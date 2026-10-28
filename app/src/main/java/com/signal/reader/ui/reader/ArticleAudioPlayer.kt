package com.signal.reader.ui.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                tts?.language = Locale.getDefault()
                isInitialized = true
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

        // Chunk text into sentences/paragraphs
        val fullText = "$title. $bodyText"
        articleTextQueue = fullText.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        currentChunkIndex = 0

        tts?.setSpeechRate(_playbackState.value.speed)
        speakCurrentChunk()
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
