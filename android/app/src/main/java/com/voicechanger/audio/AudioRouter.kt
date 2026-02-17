package com.voicechanger.audio

import android.media.*
import android.util.Log
import kotlinx.coroutines.*

/**
 * Routes audio from microphone through voice processor and back to call.
 * Creates a virtual audio loopback.
 */
class AudioRouter {
    
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val voiceProcessor = VoiceProcessor()
    
    private var isRunning = false
    private var processingJob: Job? = null
    
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, channelConfig, audioFormat
    ) * 2
    
    fun start(persona: String = "neutral") {
        if (isRunning) return
        
        voiceProcessor.setPersona(persona)
        
        // Initialize AudioRecord (microphone input)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        
        // Initialize AudioTrack (output to call)
        audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
        
        audioRecord?.startRecording()
        audioTrack?.play()
        
        isRunning = true
        
        // Start processing loop
        processingJob = CoroutineScope(Dispatchers.IO).launch {
            processAudioLoop()
        }
        
        Log.d("AudioRouter", "Audio routing started with persona: $persona")
    }
    
    fun stop() {
        isRunning = false
        processingJob?.cancel()
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        Log.d("AudioRouter", "Audio routing stopped")
    }
    
    fun setPersona(persona: String) {
        voiceProcessor.setPersona(persona)
        Log.d("AudioRouter", "Persona changed to: $persona")
    }
    
    private suspend fun processAudioLoop() {
        val buffer = ShortArray(bufferSize / 2)
        
        while (isRunning) {
            // Read from microphone
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            
            if (read > 0) {
                // Process audio (apply voice change)
                val processed = voiceProcessor.processAudio(buffer.copyOf(read))
                
                // Write to call output
                audioTrack?.write(processed, 0, processed.size)
            }
            
            yield() // Allow cancellation
        }
    }
}
