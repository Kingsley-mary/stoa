package com.signal.reader.ui.reader

import android.content.Context
import android.media.AudioAttributes
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
    val currentSentence: String = ""
)

class ArticleAudioPlayer(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private var articleTextQueue: List<String> = emptyList()
    private var currentChunkIndex = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                val defaultLocale = Locale.getDefault()
                tts?.language = defaultLocale

                // Configure high-quality audio attributes
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                // Select the highest quality natural voice available
                try {
                    val allVoices = tts?.voices ?: emptySet()
                    val langVoices = allVoices.filter { voice ->
                        voice.locale.language.equals(defaultLocale.language, ignoreCase = true) ||
                        voice.locale.language.equals("en", ignoreCase = true)
                    }

                    // Score voices prioritizing high quality, natural/neural voices
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
                            _playbackState.value = _playbackState.value.copy(
                                status = AudioPlaybackStatus.IDLE
                            )
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        _playbackState.value = _playbackState.value.copy(
                            status = AudioPlaybackStatus.IDLE
                        )
                    }
                })
            }
        }
    }

    fun play(title: String, bodyText: String) {
        if (!isInitialized || tts == null) return

        // Clean HTML tags and decode entities for natural speech
        val cleanTitle = cleanHtmlForSpeech(title)
        val cleanBody = cleanHtmlForSpeech(bodyText)

        // Chunk text into natural sentences (splitting on sentence terminators)
        val fullText = "$cleanTitle. $cleanBody"
        articleTextQueue = fullText
            .split(Regex("(?<=[.!?])\\s+|\n+"))
            .map { it.trim() }
            .filter { it.length > 2 }

        currentChunkIndex = 0

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
                currentSentence = chunk
            )
            tts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "chunk_$currentChunkIndex")
        } else {
            _playbackState.value = _playbackState.value.copy(status = AudioPlaybackStatus.IDLE)
        }
    }

    fun pause() {
        tts?.stop()
        _playbackState.value = _playbackState.value.copy(status = AudioPlaybackStatus.PAUSED)
    }

    fun resume() {
        if (_playbackState.value.status == AudioPlaybackStatus.PAUSED) {
            tts?.setSpeechRate(_playbackState.value.speed)
            speakCurrentChunk()
        }
    }

    fun togglePlayPause(title: String, bodyText: String) {
        when (_playbackState.value.status) {
            AudioPlaybackStatus.PLAYING -> pause()
            AudioPlaybackStatus.PAUSED -> resume()
            AudioPlaybackStatus.IDLE -> play(title, bodyText)
        }
    }

    fun cycleSpeed() {
        val nextSpeed = when (_playbackState.value.speed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            else -> 1.0f
        }
        _playbackState.value = _playbackState.value.copy(speed = nextSpeed)
        tts?.setSpeechRate(nextSpeed)
    }

    fun stop() {
        tts?.stop()
        articleTextQueue = emptyList()
        currentChunkIndex = 0
        _playbackState.value = _playbackState.value.copy(
            status = AudioPlaybackStatus.IDLE,
            currentSentence = ""
        )
    }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
