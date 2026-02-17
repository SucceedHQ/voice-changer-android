package com.example.voicechanger.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class AudioPlayer {
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    private val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    // Larger buffer for track to avoid underruns on the OS side
    private val bufferSize = maxOf(minBufferSize, 16384) 

    private var audioTrack: AudioTrack? = null
    private var playerJob: Job? = null
    private var isPlaying = false

    // Jitter Buffer: Queue to hold incoming packets
    // Simple implementation: Wait for X packets before starting to play
    private val packetQueue = ConcurrentLinkedQueue<ByteArray>()
    private val jitterBufferThreshold = 5 // Number of packets to buffer before starting playback
    private var isBuffering = true

    fun start() {
        if (isPlaying) return
        
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
            
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(audioFormat)
            .setChannelMask(channelConfig)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            
        audioTrack?.play()
        isPlaying = true
        isBuffering = true
        packetQueue.clear()

        playerJob = CoroutineScope(Dispatchers.Default).launch {
            processQueue()
        }
    }

    private suspend fun processQueue() {
        while (isActive && isPlaying) {
            if (isBuffering) {
                if (packetQueue.size >= jitterBufferThreshold) {
                    isBuffering = false
                    Log.d("AudioPlayer", "Jitter Buffer full, starting playback")
                } else {
                    delay(10) // Wait for buffer to fill
                    continue
                }
            }

            val data = packetQueue.poll()
            if (data != null) {
                audioTrack?.write(data, 0, data.size)
            } else {
                // Buffer underrun encountered
                // Option 1: Pause and re-buffer (stops crackling but interrupts stream)
                // Option 2: Write silence (keeps stream alive but gap in audio)
                // We'll go with re-buffering for quality
                isBuffering = true
                Log.d("AudioPlayer", "Buffer Underrun! Re-buffering...")
            }
        }
    }

    fun playAudio(data: ByteArray) {
        if (!isPlaying) return
        packetQueue.offer(data)
    }

    fun stop() {
        isPlaying = false
        playerJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping player", e)
        }
        audioTrack = null
        packetQueue.clear()
    }
}
