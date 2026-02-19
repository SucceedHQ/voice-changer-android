package com.voicechanger.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.*

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
    
    // Adaptive Buffer Sizing
    // We want a buffer that is large enough to prevent underruns but small enough for low latency.
    private val minRunBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
    private val trackBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)
    
    // Staging buffer size (larger to prevent underruns)
    private val bufferSize = max(minRunBufferSize, max(trackBufferSize, 4096))
    
    // Noise Gate State
    private var gateOpen = false
    private var gateTimer = 0
    private val HOLD_TIME_FRAMES = 25 // ~250ms at 10ms chunks (increased for short words)
    
    // Reverb State (Simple Feedback Delay Network)
    // 4 delay lines for a basic reverb
    private val delayLines = Array(4) { FloatArray(2000) } // Max ~50ms
    private val delayIndices = IntArray(4)
    private val delayLens = intArrayOf(1103, 1373, 1597, 1993) // Primes
    private val feedback = 0.4f
    
    @SuppressLint("MissingPermission")
    fun start(persona: String = "neutral") {
        if (isRunning) return
        
        try {
            voiceProcessor.setPersona(persona)
            
            // 1. Setup AudioRecord (Microphone)
            // Use UNPROCESSED to get raw audio if possible, avoiding system AGC/NS which might fight our effects
            // recursive simple backoff if UNPROCESSED is not supported could be added, but MIC is standard fallback
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
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
                
            val audioFormatObj = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(audioFormat)
                .setChannelMask(channelConfigOut)
                .build()
            
            // Adaptive buffer sizing for AudioTrack
            // AudioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) would be better
            // but requires Context to be passed around more cleanly. 
            // We use a safe default of max(min, 2048).
            val outBuffSize = max(trackBufferSize, 2048)
            
            audioTrack = AudioTrack(
                audioAttributes,
                audioFormatObj,
                outBuffSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e("AudioRouter", "AudioTrack init failed")
                audioRecord?.release()
                return
            }
            
            // 3. Maximize Volume
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.95).toInt(), 0)
            
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
        // Reduced chunk size for better responsiveness for short words
        val chunkSize = 2048 
        val buffer = ShortArray(chunkSize)
        val audioRecord = this.audioRecord ?: return
        val audioTrack = this.audioTrack ?: return
        
        val noiseGateThreshold = 0.002f // Lowered to catch "yea", "ok"
        // Gain (Software Amp)
        val gain = 1.5f // Reduced from 3.0f to prevent clipping
        
        // Reverb wet/dry
        val revMix = 0.15f
        
        while (isRunning) {
            try {
                if (!isRunning) break
                val read = audioRecord.read(buffer, 0, chunkSize) 
                
                if (read > 0) {
                    // Convert to float for analysis to check RMS
                    var rms = 0.0f
                    // We only check a subset to save CPU? No, check all for accuracy.
                    // Or simple peak check? RMS is better.
                    // Optimization: Check every 4th sample
                    for (i in 0 until read step 4) {
                        val s = buffer[i] / 32768f
                        rms += s * s
                    }
                    rms = sqrt(rms / (read / 4))
                    
                    // Noise Gate with Hysteresis
                    if (rms > noiseGateThreshold) {
                        gateOpen = true
                        gateTimer = HOLD_TIME_FRAMES
                    } else {
                        if (gateTimer > 0) {
                            gateTimer--
                        } else {
                            gateOpen = false
                        }
                    }
                    
                    if (gateOpen) {
                        // Copy purely for processing safety
                        val inputBuffer = buffer.copyOf(read)
                        
                        // Apply DSP (VoiceProcessor)
                        val processed = voiceProcessor.processAudio(inputBuffer)
                        
                        // Apply Effects & Gain & Reverb
                        // We do this in-place or new buffer? Processed is new buffer.
                        // Let's reuse 'processed' for output to save allocation if possible, 
                        // but VoiceProcessor creates new.
                        
                        // To avoid allocating 'outBuffer' every loop, we could have a member buffer
                        // But size varies. Let's just alloc for now, GC handles short lived objects well.
                        val outBuffer = ShortArray(processed.size)
                        
                        for (i in processed.indices) {
                            val samp = processed[i] / 32768f
                            
                            // Reverb (Simple FDN)
                            var wet = 0.0f
                            // Unroll loop for slight speedup?
                            for (j in 0 until 4) {
                                val idx = delayIndices[j]
                                val dOut = delayLines[j][idx]
                                wet += dOut
                                // Update delay line
                                val dIn = samp + dOut * feedback
                                delayLines[j][idx] = dIn
                                delayIndices[j] = (idx + 1) % delayLens[j]
                            }
                            wet *= 0.25f // Average
                            
                            // Mix
                            val mixed = samp * (1 - revMix) + wet * revMix
                            
                            // Gain 
                            val amplified = mixed * gain
                            
                            // Hard Clip / Saturation
                            outBuffer[i] = (amplified.coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
                        }
                        
                        audioTrack.write(outBuffer, 0, outBuffer.size)
                    } else {
                        // Silence
                        // Write zeros to keep timing? 
                        // If we don't write, the AudioTrack buffer empties and might underrun when we resume.
                        // Better to write silence.
                        val silence = ShortArray(read) // Zeroed by default
                        audioTrack.write(silence, 0, read)
                    }
                }
                // Yield to allow other coroutines to run (UI updates etc)
                yield() 
            } catch (e: Exception) {
                Log.e("AudioRouter", "Loop error: ${e.message}")
                if (!isRunning) break
            }
        }
    }
}
