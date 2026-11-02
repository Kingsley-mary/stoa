package com.signal.reader.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    object UpToDate : UpdateState
    data class Available(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String
    ) : UpdateState
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val currentVersionCode: Int by lazy {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (_: Exception) {
            5
        }
    }

    val currentVersionName: String by lazy {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.4"
        } catch (_: Exception) {
            "1.4"
        }
    }

    // Default GitHub Releases API endpoint
    var updateManifestUrl: String = "https://api.github.com/repos/Kingsley-mary/stoa/releases/latest"

    suspend fun checkForUpdates(customUrl: String? = null, isSilent: Boolean = false): UpdateState = withContext(Dispatchers.IO) {
        val targetUrl = customUrl?.ifBlank { null } ?: updateManifestUrl
        if (!isSilent) {
            _updateState.value = UpdateState.Checking
        }

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Stoa-Reader/$currentVersionName (Android)")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Server responded with code ${response.code}"
                if (!isSilent) _updateState.value = UpdateState.Error(err)
                return@withContext UpdateState.Error(err)
            }

            val bodyString = response.body?.string().orEmpty()
            if (bodyString.isBlank()) {
                val err = "Empty response from update server"
                if (!isSilent) _updateState.value = UpdateState.Error(err)
                return@withContext UpdateState.Error(err)
            }

            val json = JSONObject(bodyString)

            // Detect format: GitHub Releases API vs standard version.json
            val (remoteVersionCode, remoteVersionName, apkUrl, changelog) = if (json.has("tag_name")) {
                // GitHub Releases JSON format
                val tag = json.getString("tag_name").removePrefix("v").trim()
                val body = json.optString("body", "Bug fixes and improvements")
                val parsedCode = Regex("""(?:versionCode|code)[:=\s]+(\d+)""", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                var downloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                Quadruple(parsedCode, tag, downloadUrl, body)
            } else {
                // Custom / Stoa version.json format
                val code = json.optInt("versionCode", 0)
                val name = json.optString("versionName", "1.0")
                val url = json.optString("apkUrl", "")
                val notes = json.optString("changelog", "Bug fixes and performance improvements.")
                Quadruple(code, name, url, notes)
            }

            if (apkUrl.isBlank()) {
                val err = "No APK package found in update release."
                if (!isSilent) _updateState.value = UpdateState.Error(err)
                return@withContext UpdateState.Error(err)
            }

            val isNewer = if (remoteVersionCode > 0) {
                remoteVersionCode > currentVersionCode
            } else {
                isVersionStringNewer(remoteVersionName, currentVersionName)
            }

            val result = if (isNewer) {
                UpdateState.Available(
                    versionCode = remoteVersionCode,
                    versionName = remoteVersionName,
                    apkUrl = apkUrl,
                    changelog = changelog
                )
            } else {
                UpdateState.UpToDate
            }

            if (isNewer || !isSilent) {
                _updateState.value = result
            }
            result
        } catch (e: CancellationException) {
            if (!isSilent) _updateState.value = UpdateState.Idle
            throw e
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Unable to check for updates."
            val state = UpdateState.Error(err)
            if (!isSilent) {
                _updateState.value = state
            }
            state
        }
    }

    suspend fun downloadAndInstall(available: UpdateState.Available) = withContext(Dispatchers.IO) {
        try {
            _updateState.value = UpdateState.Downloading(0, 0, 0)

            val request = Request.Builder()
                .url(available.apkUrl)
                .header("User-Agent", "Stoa-Reader/$currentVersionName (Android)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _updateState.value = UpdateState.Error("Download failed with HTTP ${response.code}")
                return@withContext
            }

            val body = response.body ?: throw IllegalStateException("Download body was null")
            val totalBytes = body.contentLength()

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val cleanVersionName = available.versionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val targetApk = File(updatesDir, "stoa-update-$cleanVersionName.apk")

            body.byteStream().use { input ->
                FileOutputStream(targetApk).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalDownloaded = 0L
                    var lastPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((totalDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        if (percent != lastPercent) {
                            lastPercent = percent
                            _updateState.value = UpdateState.Downloading(
                                progressPercent = percent,
                                downloadedBytes = totalDownloaded,
                                totalBytes = totalBytes
                            )
                        }
                    }
                    output.flush()
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(targetApk)
            withContext(Dispatchers.Main) {
                installApk(context, targetApk)
            }
        } catch (e: CancellationException) {
            _updateState.value = UpdateState.Idle
            throw e
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Download failed.")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                _updateState.value = UpdateState.Error("APK file not found.")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Couldn't start package installer: ${e.localizedMessage}")
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    companion object {
        internal fun isVersionStringNewer(remote: String, current: String): Boolean {
            val rParts = remote.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
            val cParts = current.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
