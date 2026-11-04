package com.signal.reader.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        val totalBytes: Long,
        val apkUrl: String = ""
    ) : UpdateState
    data class ReadyToInstall(val apkFile: File, val apkUrl: String = "") : UpdateState
    data class Error(val message: String, val apkUrl: String = "") : UpdateState
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

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startDownloadAndInstall(available: UpdateState.Available) {
        managerScope.launch {
            performDownloadAndInstall(available)
        }
    }

    suspend fun downloadAndInstall(available: UpdateState.Available) = withContext(Dispatchers.IO + NonCancellable) {
        performDownloadAndInstall(available)
    }

    private suspend fun performDownloadAndInstall(available: UpdateState.Available) {
        try {
            _updateState.value = UpdateState.Downloading(0, 0, 0, available.apkUrl)

            val request = Request.Builder()
                .url(available.apkUrl)
                .header("User-Agent", "Stoa-Reader/$currentVersionName (Android)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _updateState.value = UpdateState.Error("Download failed with HTTP ${response.code}", available.apkUrl)
                return
            }

            val body = response.body ?: throw IllegalStateException("Download body was null")
            val totalBytes = body.contentLength()

            // Prefer external cache directory so Android PackageInstaller can access the FileProvider content URI without sandbox permission denials
            val updatesDir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
            val cleanVersionName = available.versionName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val targetApk = File(updatesDir, "stoa-update-$cleanVersionName.apk")

            // Clean up any stale partial downloads or older versions
            try {
                updatesDir.listFiles()?.forEach { file ->
                    if (file.name != targetApk.name && file.isFile) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {}

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
                                totalBytes = totalBytes,
                                apkUrl = available.apkUrl
                            )
                        }
                    }
                    output.flush()
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(targetApk, available.apkUrl)
            withContext(Dispatchers.Main) {
                installApk(context, targetApk, available.apkUrl)
            }
        } catch (e: CancellationException) {
            _updateState.value = UpdateState.Idle
            throw e
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Download failed.", available.apkUrl)
        }
    }

    fun installApk(context: Context, apkFile: File, apkUrl: String = "") {
        try {
            if (!apkFile.exists()) {
                _updateState.value = UpdateState.Error("APK file not found.", apkUrl)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                _updateState.value = UpdateState.ReadyToInstall(apkFile, apkUrl)
                try {
                    Toast.makeText(context, "Please enable unknown sources to install update", Toast.LENGTH_LONG).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                } catch (_: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                }
                return
            }

            apkFile.setReadable(true, false)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }

            // Explicitly grant read URI permission to all potential installer packages
            val knownInstallers = listOf("com.google.android.packageinstaller", "com.android.packageinstaller")
            for (pkg in knownInstallers) {
                try {
                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
            try {
                val resInfoList = context.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    context.grantUriPermission(resolveInfo.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (_: Exception) {}

            _updateState.value = UpdateState.ReadyToInstall(apkFile, apkUrl)
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Couldn't start package installer: ${e.localizedMessage}", apkUrl)
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
