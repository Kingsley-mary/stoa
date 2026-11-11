package com.signal.reader.ui.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jsoup.Jsoup
import java.util.Locale

enum class AudioPlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED
}

data class AudioPlaybackState(
    val status: AudioPlaybackStatus = AudioPlaybackStatus.IDLE,
    val speed: Float = 1.0f,
    val currentSentence: String = "",
    val articleId: Long? = null,
    val articleTitle: String = "",
    val sourceName: String = "",
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0
) {
    val progressFraction: Float
        get() = if (totalChunks <= 0) 0f else ((currentChunkIndex + 1).toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
}

class ArticleAudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingPlayAction: (() -> Unit)? = null

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private var articleTextQueue: List<String> = emptyList()
    private var currentChunkIndex = 0

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_playbackState.value.status == AudioPlaybackStatus.PLAYING) {
                    pause()
                }
            }
        }
    }

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                val defaultLocale = Locale.getDefault()
                tts?.language = defaultLocale

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                try {
                    val allVoices = tts?.voices ?: emptySet()
                    val langVoices = allVoices.filter { voice ->
                        voice.locale.language.equals(defaultLocale.language, ignoreCase = true) ||
                            voice.locale.language.equals("en", ignoreCase = true)
                    }

                    val bestVoice = langVoices.maxByOrNull { voice ->
                        var score = 0
                        if (voice.locale.language.equals(defaultLocale.language, ignoreCase = true)) score += 40
                        if (voice.locale.country.equals(defaultLocale.country, ignoreCase = true)) score += 20

                        if (voice.quality == Voice.QUALITY_VERY_HIGH) score += 100
                        else if (voice.quality == Voice.QUALITY_HIGH) score += 60
                        else if (voice.quality == Voice.QUALITY_NORMAL) score += 20

                        if (voice.latency == Voice.LATENCY_VERY_LOW) score += 20
                        if (!voice.isNetworkConnectionRequired) score += 15

                        val name = voice.name.lowercase()
                        if (name.contains("natural") || name.contains("neural") || name.contains("wavenet")) score += 80
                        if (name.contains("enhanced") || name.contains("premium")) score += 40
                        score
                    }

                    if (bestVoice != null) {
                        tts?.voice = bestVoice
                    }
                } catch (_: Exception) {}

                tts?.setPitch(1.0f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _playbackState.value = _playbackState.value.copy(
                            status = AudioPlaybackStatus.PLAYING
                        )
                    }

                    override fun onDone(utteranceId: String?) {
                        currentChunkIndex++
                        if (currentChunkIndex < articleTextQueue.size && _playbackState.value.status == AudioPlaybackStatus.PLAYING) {
                            speakCurrentChunk()
                        } else {
                            abandonAudioFocus()
                            _playbackState.value = _playbackState.value.copy(
                                status = AudioPlaybackStatus.IDLE,
                                currentChunkIndex = 0
                            )
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        abandonAudioFocus()
                        _playbackState.value = _playbackState.value.copy(
                            status = AudioPlaybackStatus.IDLE
                        )
                    }
                })

                pendingPlayAction?.invoke()
                pendingPlayAction = null
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            focusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun play(
        title: String,
        bodyText: String,
        articleId: Long? = null,
        sourceName: String = ""
    ) {
        if (!isInitialized || tts == null) {
            pendingPlayAction = { play(title, bodyText, articleId, sourceName) }
            if (tts == null) initTts()
            return
        }

        requestAudioFocus()

        val cleanTitle = cleanHtmlForSpeech(title)
        val cleanBody = cleanHtmlForSpeech(bodyText)

        val fullText = "$cleanTitle. $cleanBody"
        articleTextQueue = fullText
            .split(Regex("(?<=[.!?])\\s+|\n+"))
            .map { it.trim() }
            .filter { it.length > 2 }

        currentChunkIndex = 0
        _playbackState.value = _playbackState.value.copy(
            articleId = articleId,
            articleTitle = title,
            sourceName = sourceName,
            currentChunkIndex = 0,
            totalChunks = articleTextQueue.size
        )

        tts?.setSpeechRate(_playbackState.value.speed)
        speakCurrentChunk()
    }

    private fun cleanHtmlForSpeech(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            doc.select("script, style, noscript, nav, footer, header").remove()
            doc.text()
                .replace(Regex("&[a-zA-Z0-9#]+;"), " ")
                .replace(Regex("https?://\\S+"), "link")
                .trim()
        } catch (_: Exception) {
            html.replace(Regex("<[^>]*>"), " ").trim()
        }
    }

    private fun speakCurrentChunk() {
        if (currentChunkIndex in articleTextQueue.indices) {
            val chunk = articleTextQueue[currentChunkIndex]
            _playbackState.value = _playbackState.value.copy(
                status = AudioPlaybackStatus.PLAYING,
                currentSentence = chunk,
                currentChunkIndex = currentChunkIndex,
                totalChunks = articleTextQueue.size
            )
            tts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "chunk_$currentChunkIndex")
        } else {
            abandonAudioFocus()
            _playbackState.value = _playbackState.value.copy(status = AudioPlaybackStatus.IDLE)
        }
    }

    fun pause() {
        tts?.stop()
        abandonAudioFocus()
        _playbackState.value = _playbackState.value.copy(status = AudioPlaybackStatus.PAUSED)
    }

    fun resume() {
        if (_playbackState.value.status == AudioPlaybackStatus.PAUSED) {
            requestAudioFocus()
            tts?.setSpeechRate(_playbackState.value.speed)
            speakCurrentChunk()
        }
    }

    fun skipNext() {
        if (articleTextQueue.isEmpty()) return
        if (currentChunkIndex < articleTextQueue.lastIndex) {
            currentChunkIndex++
            speakCurrentChunk()
        } else {
            stop()
        }
    }

    fun skipPrevious() {
        if (articleTextQueue.isEmpty()) return
        currentChunkIndex = (currentChunkIndex - 1).coerceAtLeast(0)
        speakCurrentChunk()
    }

    fun togglePlayPause(
        title: String,
        bodyText: String,
        articleId: Long? = null,
        sourceName: String = ""
    ) {
        val currentState = _playbackState.value
        val isDifferentArticle = articleId != null && currentState.articleId != null && articleId != currentState.articleId
        if (isDifferentArticle) {
            play(title, bodyText, articleId, sourceName)
            return
        }
        when (currentState.status) {
            AudioPlaybackStatus.PLAYING -> pause()
            AudioPlaybackStatus.PAUSED -> resume()
            AudioPlaybackStatus.IDLE -> play(title, bodyText, articleId, sourceName)
        }
    }

    fun cycleSpeed() {
        val nextSpeed = when (_playbackState.value.speed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 1.75f
            else -> 1.0f
        }
        _playbackState.value = _playbackState.value.copy(speed = nextSpeed)
        tts?.setSpeechRate(nextSpeed)
        if (_playbackState.value.status == AudioPlaybackStatus.PLAYING) {
            speakCurrentChunk()
        }
    }

    fun stop() {
        tts?.stop()
        abandonAudioFocus()
        articleTextQueue = emptyList()
        currentChunkIndex = 0
        _playbackState.value = _playbackState.value.copy(
            status = AudioPlaybackStatus.IDLE,
            currentSentence = "",
            articleId = null,
            articleTitle = "",
            sourceName = "",
            currentChunkIndex = 0,
            totalChunks = 0
        )
    }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
