package com.meditation.timer

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

class MetronomeManager(
    private val audioManager: AudioManager,
    private val onStopped: (() -> Unit)? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private var clickTrack: AudioTrack? = null
    private var accentTrack: AudioTrack? = null
    private var bpm = 90
    private var beatsPerBar = 4
    private var beatIndex = 0
    private var running = false
    private var volume = 1.0f
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            playTick()
            val intervalMs = (60_000.0 / max(1, bpm)).toLong()
            handler.postDelayed(this, intervalMs)
        }
    }

    fun configure(bpm: Int, beatsPerBar: Int) {
        this.bpm = bpm
        this.beatsPerBar = beatsPerBar
    }

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        recreateClickTracksIfNeeded()
    }

    fun start() {
        if (running) return
        if (!requestFocus()) return
        recreateClickTracksIfNeeded()
        running = true
        beatIndex = 0
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tickRunnable)
        abandonFocus()
        onStopped?.invoke()
    }

    fun isRunning(): Boolean = running

    fun release() {
        stop()
        clickTrack?.release()
        accentTrack?.release()
        clickTrack = null
        accentTrack = null
    }

    fun onAudioFocusLost() {
        stop()
    }

    private fun ensureClickTracks() {
        if (clickTrack != null && accentTrack != null) return
        val sampleRate = 44100
        clickTrack = buildClickTrack(sampleRate, volume * 0.6f)
        accentTrack = buildClickTrack(sampleRate, volume * 1.0f)
    }

    private fun recreateClickTracksIfNeeded() {
        clickTrack?.release()
        accentTrack?.release()
        clickTrack = null
        accentTrack = null
        if (running) {
            ensureClickTracks()
        }
    }

    private fun playTick() {
        ensureClickTracks()
        val isAccent = beatIndex % max(1, beatsPerBar) == 0
        val track = if (isAccent) accentTrack else clickTrack
        playTrack(track)
        beatIndex = (beatIndex + 1) % max(1, beatsPerBar)
    }

    private fun playTrack(track: AudioTrack?) {
        if (track == null) return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
            track.flush()
        }
        track.setPlaybackHeadPosition(0)
        track.play()
    }

    private fun buildClickTrack(sampleRate: Int, amplitude: Float): AudioTrack {
        val durationMs = 18
        val numSamples = (sampleRate * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        val random = Random(1337)
        val decayRate = 6.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val envelope = (1.0 - t).pow(decayRate)
            val noise = (random.nextDouble() * 2.0 - 1.0)
            val value = (noise * envelope * amplitude * Short.MAX_VALUE).toInt()
            buffer[i] = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buffer.size * 2)
            .build()
        track.write(buffer, 0, buffer.size)
        return track
    }

    private fun requestFocus(): Boolean {
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change <= AudioManager.AUDIOFOCUS_LOSS) {
                onAudioFocusLost()
            }
        }
        focusChangeListener = listener
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        val request = audioFocusRequest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (request != null) {
                audioManager.abandonAudioFocusRequest(request)
            }
        } else {
            @Suppress("DEPRECATION")
            focusChangeListener?.let { audioManager.abandonAudioFocus(it) }
        }
        audioFocusRequest = null
        focusChangeListener = null
    }
}
