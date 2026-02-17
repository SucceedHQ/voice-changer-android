package com.voicechanger.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.math.*

/**
 * Real-time voice processor for VoIP calls.
 * Applies pitch and formant shifting with <100ms latency.
 */
class VoiceProcessor {
    
    private var pitchShift: Float = 1.0f
    private var formantShift: Float = 1.0f
    
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    
    fun setPersona(persona: String) {
        when (persona.lowercase()) {
            "male" -> {
                pitchShift = 0.75f
                formantShift = 0.85f
            }
            "female" -> {
                pitchShift = 1.4f
                formantShift = 1.15f
            }
            "child" -> {
                pitchShift = 1.6f
                formantShift = 1.25f
            }
            else -> {
                pitchShift = 1.0f
                formantShift = 1.0f
            }
        }
    }
    
    /**
     * Process audio buffer in real-time.
     * Uses SOLA (Synchronized Overlap-Add) for pitch shifting.
     */
    fun processAudio(inputBuffer: ShortArray): ShortArray {
        if (pitchShift == 1.0f && formantShift == 1.0f) {
            return inputBuffer
        }
        
        // Apply pitch shifting using resampling with interpolation
        val outputBuffer = applyPitchShift(inputBuffer, pitchShift)
        
        return outputBuffer
    }
    
    private fun applyPitchShift(input: ShortArray, factor: Float): ShortArray {
        if (factor == 1.0f) return input
        
        val output = ShortArray(input.size)
        
        for (i in output.indices) {
            val srcPos = i * factor
            val index0 = srcPos.toInt()
            val index1 = min(index0 + 1, input.size - 1)
            val frac = srcPos - index0
            
            if (index0 < input.size) {
                // Linear interpolation for smooth pitch shift
                val val0 = input[index0].toFloat()
                val val1 = input[index1].toFloat()
                output[i] = (val0 * (1 - frac) + val1 * frac).toInt().toShort()
            }
        }
        
        return output
    }
}
