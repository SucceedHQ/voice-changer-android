package com.voicechanger.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.max

/**
 * Routes audio from microphone, processes it, and plays it loudly through the speaker.
 * This relies on "Acoustic Coupling" - the phone's mic picks up the speaker output.
 */
class AudioRouter(private val context: Context) {
    
    private val sampleRate = 44100
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val voiceProcessor = VoiceProcessor()
    
    private var isRunning = false
    private var processingJob: Job? = null
    
    // Larger buffer to prevent glitches
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
    private val bufferSize = max(minBufferSize, 8192)
    
    @SuppressLint("MissingPermission")
    fun start(persona: String = "neutral") {
        if (isRunning) return
        
        try {
            voiceProcessor.setPersona(persona)
            
            // 1. Setup AudioRecord (Microphone)
            // Use MIC instead of VOICE_COMMUNICATION to avoid conflict with VoIP app
            // However, VoIP app might still block this. 'Unprocessed' is also good.
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, 
                sampleRate,
                channelConfigIn,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRouter", "AudioRecord init failed")
                return
            }
            
            // 2. Setup AudioTrack (Speaker)
            // Use STREAM_MUSIC to play loudly through main speaker
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
                
            val audioFormatObj = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(audioFormat)
                .setChannelMask(channelConfigOut)
                .build()
                
            audioTrack = AudioTrack(
                audioAttributes,
                audioFormatObj,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e("AudioRouter", "AudioTrack init failed")
                audioRecord?.release()
                return
            }
            
            // 3. Maximize Volume (Optional, user can control)
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.9).toInt(), 0) // Set to 90%
            
            // Start
            audioRecord?.startRecording()
            audioTrack?.play()
            
            isRunning = true
            
            // Start Loop
            processingJob = CoroutineScope(Dispatchers.Default).launch {
                processAudioLoop()
            }
            
            Log.d("AudioRouter", "Speaker Relay started with persona: $persona")
            
        } catch (e: Exception) {
            Log.e("AudioRouter", "Error starting: ${e.message}")
            e.printStackTrace()
            stop()
        }
    }
    
    fun stop() {
        isRunning = false
        processingJob?.cancel()
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) { e.printStackTrace() }
        audioRecord = null
        
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) { e.printStackTrace() }
        audioTrack = null
        
        Log.d("AudioRouter", "Stopped")
    }
    
    fun setPersona(persona: String) {
        voiceProcessor.setPersona(persona)
    }
    
    private suspend fun processAudioLoop() {
        val buffer = ShortArray(bufferSize / 2)
        val audioRecord = this.audioRecord ?: return
        val audioTrack = this.audioTrack ?: return
        
        // Gain factor to make output louder (software amplification)
        val gain = 1.5f 
        
        while (isRunning) {
            try {
                val read = audioRecord.read(buffer, 0, buffer.size) 
                
                if (read > 0) {
                    val inputBuffer = buffer.copyOf(read)
                    
                    // Process
                    val processed = voiceProcessor.processAudio(inputBuffer)
                    
                    // Amplify
                    for (i in processed.indices) {
                        var amplified = processed[i] * gain
                        // Clip
                        if (amplified > 32767) amplified = 32767f
                        if (amplified < -32768) amplified = -32768f
                        processed[i] = amplified.toInt().toShort()
                    }
                    
                    // Play to Speaker
                    audioTrack.write(processed, 0, processed.size)
                }
                yield()
            } catch (e: Exception) {
                Log.e("AudioRouter", "Loop error: ${e.message}")
                if (!isRunning) break
            }
        }
    }
}
