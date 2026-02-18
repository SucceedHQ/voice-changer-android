package com.voicechanger.audio

import android.media.AudioFormat
import android.media.AudioRecord
import kotlin.math.*

/**
 * Real-time voice processor for VoIP calls (Non-Root).
 * Applies PSOLA-based pitch shifting, formant correction, and spectral effects.
 */
class VoiceProcessor {
    
    // DSP Parameters
    private var pitchShift: Float = 1.0f
    private var formantShift: Float = 1.0f
    
    // Effects
    private var lfoFreq: Float = 5.0f // Hz
    private var lfoDepth: Float = 0.0f
    private var lfoPhase: Float = 0.0f
    
    // Audio Context
    private val sampleRate = 44100
    
    // Filter Chain
    private val filters = ArrayList<BiQuadFilter>()
    
    // Pitch Detection State (Simple Autocorrelation / AMDF)
    private val minPeriod = sampleRate / 400 // Max 400Hz
    private val maxPeriod = sampleRate / 60 // Min 60Hz
    
    // Internal Buffers for Overlap-Add
    private var overflowBuffer = FloatArray(0)
    
    // Limiter State
    private var envelope = 0.0f
    
    fun setPersona(persona: String) {
        filters.clear()
        
        when (persona.lowercase()) {
            "male" -> {
                // Deeper tone, removing robotic highs
                pitchShift = 0.82f 
                formantShift = 0.92f
                lfoDepth = 0.005f // Subtle variation
                
                // 1. Low Shelf Cut (Reduce mud)
                filters.add(BiQuadFilter.lowShelf(200f, -3.0f, sampleRate))
                // 2. Peaking Boost (Chest resonance)
                filters.add(BiQuadFilter.peaking(250f, 2.0f, 1.0f, sampleRate))
                // 3. Low Pass (Remove robotic artifacts)
                filters.add(BiQuadFilter.lowPass(3800f, 0.707f, sampleRate))
            }
            "female" -> {
                // Higher pitch, distinct feminine timbre
                pitchShift = 1.55f 
                formantShift = 1.15f
                lfoDepth = 0.01f // Slight vibrato
                
                // 1. High Pass (Reduce proximity effect)
                filters.add(BiQuadFilter.highPass(150f, 0.707f, sampleRate))
                // 2. Nasality / Frontal placement
                filters.add(BiQuadFilter.peaking(2200f, 3.0f, 1.5f, sampleRate))
                // 3. Air / Breathiness
                filters.add(BiQuadFilter.highShelf(6000f, 3.0f, sampleRate))
            }
            "child" -> {
                pitchShift = 1.65f 
                formantShift = 1.35f
                lfoDepth = 0.0f
                filters.add(BiQuadFilter.peaking(3000f, 4.0f, 2.0f, sampleRate))
            }
            else -> {
                // Neutral
                pitchShift = 1.0f
                formantShift = 1.0f
                lfoDepth = 0.0f
            }
        }
    }
    
    fun processAudio(inputShorts: ShortArray): ShortArray {
        // Convert to Float for processing
        val input = FloatArray(inputShorts.size) { inputShorts[it] / 32768f }
        
        // 1. Apply LFO to pitch
        var currentPitch = pitchShift
        if (lfoDepth > 0) {
            val lfoVal = sin(lfoPhase) * lfoDepth
            currentPitch *= (1.0f + lfoVal)
            lfoPhase += (2f * PI.toFloat() * lfoFreq) / sampleRate * input.size
            if (lfoPhase > 2 * PI) lfoPhase -= (2f * PI.toFloat())
        }

        // 2. PSOLA Pitch Shifting
        val pitched = if (abs(currentPitch - 1.0f) > 0.01f) {
             applyPSOLA(input, currentPitch)
        } else {
            input
        }
        
        // 3. Apply Filter Chain
        var processed = pitched
        for (filter in filters) {
            processed = filter.process(processed)
        }
        
        // 4. Limiter (Soft Knee)
        for (i in processed.indices) {
            val absVal = abs(processed[i])
            // Simple attack/release envelope
            val attack = 0.1f // Fast attack
            val release = 0.01f // Slower release
            if (absVal > envelope) {
                envelope = attack * absVal + (1 - attack) * envelope
            } else {
                envelope = release * absVal + (1 - release) * envelope
            }
            
            // Gain reduction if loudness exceeds threshold
            if (envelope > 0.95f) {
                processed[i] *= (0.95f / envelope)
            }
        }
        
        // Convert back to Short
        return ShortArray(processed.size) {
            (processed[it].coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
        }
    }
    
    /**
     * Pitch Shifting using Resampling + OLA (Time-Scale Modification).
     * 1. Resample signal to shift pitch (duration changes inversely).
     * 2. Use OLA to time-stretch back to original duration (preserving pitch).
     */
    private fun applyPSOLA(input: FloatArray, ratio: Float): FloatArray {
        // 1. Resample: Changes Pitch by 'ratio', Duration by '1/ratio'
        // If ratio > 1 (Pitch Up), we play faster (Duration Down).
        // To get Pitch Up, we resample with step < 1.0? 
        // No, to Pitch Shift UP (higher freq), we iterate input SLOWER? 
        // Wait, if I play a tape fast (Pitch UP), I cover more input samples per seconds.
        // If I have 1 sec of audio, and I play it in 0.5 sec, Pitch is 2x.
        // My resampleLinear uses `newSize = size * factor`.
        // If pitchShift = 2.0. We want a signal that sounds 2x higher.
        // This effectively means playing it at 2x speed? 
        // No, 2x speed means duration is 0.5x.
        // resampling with factor=0.5 (decimation) makes it shorter?
        
        // Let's stick to standard def:
        // We want Pitch * ratio.
        // We create a resampled buffer where the waveform is compressed/stretched.
        // Compressed waveform = Higher Pitch. 
        // To compress waveform, we take input[0], input[2], input[4]... 
        // This is decimation (factor < 1).
        // But wait, if I take every 2nd sample, the array is smaller. 
        // Audio engine plays it at fixed 44100.
        // If array is half size, it plays in half time. Pitch is 2x. 
        // Correct.
        
        // So for Pitch Ratio X:
        // We want new size = size / X.
        // Resample factor = 1/X.
        
        val resampleFactor = 1.0f / ratio
        val resampled = resampleLinear(input, resampleFactor)
        
        // 2. Now 'resampled' has correct Pitch, but wrong Duration (size / ratio).
        // We need to restore Duration to match original 'input.size'.
        // So we need to time-stretch by 'ratio'.
        // e.g. if Pitch=2.0, resampled is 0.5 length. We stretch by 2.0 to get 1.0 length.
        
        return applySOLA(resampled, ratio)
    }
    
    // Time-Stretch using OLA (Overlap-Add)
    private fun applySOLA(input: FloatArray, stretchFactor: Float): FloatArray {
         val outputTargetSize = (input.size * stretchFactor).toInt()
         val output = FloatArray(outputTargetSize)
         
         val grainSize = 512 
         val overlap = grainSize / 2
         
         val synthesisHop = overlap // Fixed output advance
         val analysisHop = (synthesisHop / stretchFactor).toInt() // Input varies
         
         var outPtr = 0
         var inPtr = 0
         
         // Hanning window
         val window = FloatArray(grainSize) { i ->
             0.5f * (1f - cos(2f * PI.toFloat() * i / (grainSize - 1)))
         }
         
         while (outPtr + grainSize < output.size && inPtr + grainSize < input.size) {
             // Add grain
             for (i in 0 until grainSize) {
                 output[outPtr + i] += input[inPtr + i] * window[i]
             }
             
             outPtr += synthesisHop
             inPtr += analysisHop
         }
         
         return output
    }

    private fun detectPitchPeriod(buffer: FloatArray): Int {
        // AMDF (Average Magnitude Difference Function)
        var bestPeriod = 0
        var minDiff = Float.MAX_VALUE
        
        for (p in minPeriod..maxPeriod) {
            var diff = 0.0f
            // Check limited samples for performance
            val checkLen = min(buffer.size - p, 200) 
            for (i in 0 until checkLen) {
                diff += abs(buffer[i] - buffer[i + p])
            }
            if (diff < minDiff) {
                minDiff = diff
                bestPeriod = p
            }
        }
        return bestPeriod
    }
    
    private fun resampleLinear(input: FloatArray, factor: Float): FloatArray {
        val newSize = (input.size * factor).toInt()
        val output = FloatArray(newSize)
        for (i in 0 until newSize) {
            val pos = i / factor
            val i0 = pos.toInt()
            val i1 = min(i0 + 1, input.size - 1)
            val frac = pos - i0
            if (i0 < input.size) {
                output[i] = input[i0] * (1 - frac) + input[i1] * frac
            }
        }
        return output
    }
    
    // --- Nested BiQuad Filter Class ---
    class BiQuadFilter(
        private var b0: Float, private var b1: Float, private var b2: Float,
        private var a1: Float, private var a2: Float
    ) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f
        
        fun process(input: FloatArray): FloatArray {
            val output = FloatArray(input.size)
            for (i in input.indices) {
                val x = input[i]
                val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                
                // Shift states
                x2 = x1
                x1 = x
                y2 = y1
                y1 = y
                
                output[i] = y
            }
            return output
        }
        
        companion object {
            fun peaking(freq: Float, dbGain: Float, q: Float, sampleRate: Int): BiQuadFilter {
                val a = 10f.pow(dbGain / 40f)
                val w0 = 2f * PI.toFloat() * freq / sampleRate
                val alpha = sin(w0) / (2f * q)
                val a0 = 1f + alpha / a
                return BiQuadFilter(
                    (1f + alpha * a) / a0, (-2f * cos(w0)) / a0, (1f - alpha * a) / a0,
                    (-2f * cos(w0)) / a0, (1f - alpha / a) / a0
                )
            }
            
            fun lowShelf(freq: Float, dbGain: Float, sampleRate: Int): BiQuadFilter {
                val a = 10f.pow(dbGain / 40f)
                val w0 = 2f * PI.toFloat() * freq / sampleRate
                val alpha = sin(w0) / 2f * sqrt((a + 1 / a) * (1 / 0.707f - 1) + 2)
                val cosw = cos(w0)
                val a0 = (a + 1) + (a - 1) * cosw + 2 * sqrt(a) * alpha
                 return BiQuadFilter(
                    (a * ((a + 1) - (a - 1) * cosw + 2 * sqrt(a) * alpha)) / a0,
                    (2 * a * ((a - 1) - (a + 1) * cosw)) / a0,
                    (a * ((a + 1) - (a - 1) * cosw - 2 * sqrt(a) * alpha)) / a0,
                    (-2 * ((a - 1) + (a + 1) * cosw)) / a0,
                    ((a + 1) + (a - 1) * cosw - 2 * sqrt(a) * alpha) / a0
                )
            }
            
            fun highShelf(freq: Float, dbGain: Float, sampleRate: Int): BiQuadFilter {
                val a = 10f.pow(dbGain / 40f)
                val w0 = 2f * PI.toFloat() * freq / sampleRate
                val alpha = sin(w0) / 2f * sqrt((a + 1 / a) * (1 / 0.707f - 1) + 2)
                val cosw = cos(w0)
                val a0 = (a + 1) - (a - 1) * cosw + 2 * sqrt(a) * alpha
                return BiQuadFilter(
                    (a * ((a + 1) + (a - 1) * cosw + 2 * sqrt(a) * alpha)) / a0,
                    (-2 * a * ((a - 1) + (a + 1) * cosw)) / a0,
                    (a * ((a + 1) + (a - 1) * cosw - 2 * sqrt(a) * alpha)) / a0,
                    (2 * ((a - 1) - (a + 1) * cosw)) / a0,
                    ((a + 1) - (a - 1) * cosw - 2 * sqrt(a) * alpha) / a0
                )
            }
            
            fun lowPass(freq: Float, q: Float, sampleRate: Int): BiQuadFilter {
                 val w0 = 2f * PI.toFloat() * freq / sampleRate
                 val alpha = sin(w0) / (2f * q)
                 val cosw = cos(w0)
                 val a0 = 1f + alpha
                 return BiQuadFilter(
                     ((1f - cosw) / 2f) / a0, ((1f - cosw)) / a0, ((1f - cosw) / 2f) / a0,
                     (-2f * cosw) / a0, (1f - alpha) / a0
                 )
            }

            fun highPass(freq: Float, q: Float, sampleRate: Int): BiQuadFilter {
                val w0 = 2f * PI.toFloat() * freq / sampleRate
                val alpha = sin(w0) / (2f * q)
                val cosw = cos(w0)
                val a0 = 1f + alpha
                return BiQuadFilter(
                    ((1f + cosw) / 2f) / a0, -((1f + cosw)) / a0, ((1f + cosw) / 2f) / a0,
                    (-2f * cosw) / a0, (1f - alpha) / a0
                )
            }
        }
    }
}
