package com.voicechanger.audio

import android.media.AudioFormat
import android.media.AudioRecord
import kotlin.math.*

/**
 * Real-time voice processor for VoIP calls (Non-Root).
 * Uses SOLA (Synchronized Overlap-Add) for high-fidelity pitch shifting.
 */
class VoiceProcessor {
    
    // DSP Parameters
    private var pitchRatio: Float = 1.0f
    
    // Effects
    private var lfoFreq: Float = 5.0f // Hz
    private var lfoDepth: Float = 0.0f
    private var lfoPhase: Float = 0.0f
    
    // Audio Context
    private val sampleRate = 44100
    
    // Filter Chain
    private val filters = ArrayList<BiQuadFilter>()
    
    // SOLA State
    private val overlap = 120 // Search range
    private val windowSize = 1024 // Grain size
    // Buffer to hold tail of previous output for overlap
    private var outputTail = FloatArray(windowSize + overlap * 2) 
    
    // Limiter State
    private var envelope = 0.0f
    
    fun setPersona(persona: String) {
        filters.clear()
        
        when (persona.lowercase()) {
            "male" -> {
                // Natural Male: Slight drop, but not too deep. 
                // Increased from 0.85 to 0.88 to reduce "robotic" depth.
                pitchRatio = 0.88f 
                lfoDepth = 0.002f // Reduced LFO for more stability
                
                // Warmth & Clarity
                filters.add(BiQuadFilter.lowShelf(150f, 1.5f, sampleRate))
                // Clarity boost to make it sound less muffled/robotic
                filters.add(BiQuadFilter.peaking(3000f, 2.0f, 1.0f, sampleRate))
                // Cut extreme highs
                filters.add(BiQuadFilter.highShelf(7000f, -4.0f, sampleRate))
            }
            "female" -> {
                // Natural Female: Lowered from 1.45 to 1.38 to sound more mature, less childish.
                pitchRatio = 1.38f
                lfoDepth = 0.004f
                
                // Cut Mud & Add "Chest" Resonance
                filters.add(BiQuadFilter.highPass(150f, 0.7f, sampleRate))
                filters.add(BiQuadFilter.peaking(350f, 1.5f, 1.2f, sampleRate)) // Maturity boost
                
                // Air & Clarity (not too much to avoid sibilance)
                filters.add(BiQuadFilter.highShelf(4000f, 1.5f, sampleRate))
            }
            else -> {
                // Neutral
                pitchRatio = 1.0f
                lfoDepth = 0.0f
            }
        }
    }
    
    fun processAudio(inputShorts: ShortArray): ShortArray {
        // Convert to Float
        val input = FloatArray(inputShorts.size) { inputShorts[it] / 32768f }
        
        // 1. Modulate Pitch with LFO
        var currentRatio = pitchRatio
        if (lfoDepth > 0) {
            val lfoVal = sin(lfoPhase) * lfoDepth
            currentRatio *= (1.0f + lfoVal)
            lfoPhase += (2f * PI.toFloat() * lfoFreq) / sampleRate * input.size
            if (lfoPhase > 2 * PI) lfoPhase -= 2f * PI.toFloat()
        }

        // 2. High-Quality Pitch Shift (Resample + SOLA)
        val processedFloat = if (abs(currentRatio - 1.0f) > 0.01f) {
             processSOLA(input, currentRatio)
        } else {
            input
        }
        
        // 3. Apply Filters
        var filtered = processedFloat
        for (filter in filters) {
            filtered = filter.process(filtered)
        }
        
        // 4. Limiter (Soft Knee) & Output
        val output = ShortArray(filtered.size)
        for (i in filtered.indices) {
            var samp = filtered[i]
            val absVal = abs(samp)
            
            // Envelope follower
            if (absVal > envelope) {
                envelope = 0.1f * absVal + 0.9f * envelope
            } else {
                envelope = 0.001f * absVal + 0.999f * envelope
            }
            
            // Soft Limiter
            if (envelope > 0.8f) {
                samp *= (0.8f / envelope)
            }
            
            output[i] = (samp.coerceIn(-1.0f, 1.0f) * 32767).toInt().toShort()
        }
        
        return output
    }
    
    /**
     * Pitch Shift Strategy:
     * 1. Resample input by 1/ratio. (Changes pitch to target, but duration is wrong).
     * 2. Time-Stretch by ratio using SOLA. (Restores duration, preserves pitch).
     */
    private fun processSOLA(input: FloatArray, ratio: Float): FloatArray {
        // 1. Resample
        val resampleFactor = 1.0f / ratio
        val resampled = resampleLinear(input, resampleFactor)
        
        // 2. Time Shift (SOLA)
        // We want to stretch 'resampled' length by 'ratio'.
        return applySOLA(resampled, ratio)
    }
    
    /**
     * SOLA Time-Stretching
     * Expends/Contracts 'input' to match original duration.
     * Uses cross-correlation to align phases.
     */
    private fun applySOLA(input: FloatArray, stretchFactor: Float): FloatArray {
        // Output length should be roughly: input.size * stretchFactor
        // But for real-time, we matched input.size in processAudio logic context?
        // Wait, 'input' here is already resampled.
        // Original size was N. Resampled size is N/ratio.
        // We stretch by ratio. Output size is N.
        
        val targetSize = (input.size * stretchFactor).toInt()
        val output = FloatArray(targetSize)
        
        // Hop sizes
        val SynHop = (windowSize / 4) // Fixed synthesis hop
        val AnaHop = (SynHop / stretchFactor).toInt() // Analysis hop in input
        
        // Re-use tail buffer
        if (outputTail.size < windowSize) {
           outputTail = FloatArray(windowSize)
        }
        
        var inPtr = 0
        var outPtr = 0
        
        // We write to output buffer, but we must overlap with previous tail.
        // Copy tail to start of output? No, standard OLA adds to buffer.
        // For Block Processing:
        // We have 'outputTail' from previous block. 
        // We start generating frames.
        
        // Since this is stateless simple implementation for now, we reset tail if gap is large?
        // No, we need state. But for simplicity in this rewrite, we keep 'outputTail' as class member.
        
        // Temp output buffer (large enough)
        val tempOut = FloatArray(targetSize + windowSize)
        // Copy tail
        for (i in outputTail.indices) {
            if (i < tempOut.size) tempOut[i] = outputTail[i]
        }
        
        var currentOutEnd = outputTail.size // Where valid data ends
        
        while (inPtr + windowSize < input.size && outPtr < targetSize) {
            // Analaysis frame
            val frame = FloatArray(windowSize)
            for (i in 0 until windowSize) {
                frame[i] = input[inPtr + i]
            }
            
            // Search for best overlap in tempOut around outPtr
            // We want to add 'frame' at 'outPtr' but we can shift it by +/- overlap
            // to align with existing data in tempOut.
            val searchStart = max(0, outPtr - overlap)
            val searchEnd = min(currentOutEnd, outPtr + overlap)
            
            var bestOffset = 0
            var maxCorr = -1.0f
            
            // Cross-correlation search
            // Optimized: only check where we have data
            if (searchEnd > searchStart) {
                for (offset in 0 until (searchEnd - searchStart)) {
                    val checkPos = searchStart + offset
                    var corr = 0.0f
                    // Correlate overlap region
                    val compareLen = min(windowSize, currentOutEnd - checkPos)
                    for (k in 0 until compareLen step 4) { // step 4 optimization
                         corr += frame[k] * tempOut[checkPos + k]
                    }
                    if (corr > maxCorr) {
                        maxCorr = corr
                        bestOffset = checkPos - outPtr // Delta from nominal
                    }
                }
            }
            
            val actualPos = outPtr + bestOffset
            
            // OLA
            for (i in 0 until windowSize) {
                // Hanning Window
                val win = 0.5f * (1f - cos(2f * PI.toFloat() * i / (windowSize - 1)))
                if (actualPos + i < tempOut.size) {
                    // Fade in new, Fade out old? 
                    // Standard OLA: Out[i] += In[i] * Window
                    // But we need to handle valid data.
                    // Simplified: Just add. 
                    tempOut[actualPos + i] += frame[i] * win 
                    
                    // Normalize?
                    // SOLA implies we align and fade.
                    // If we just add, we might gain volume.
                    // Usually we divide by overlap factor. 
                }
            }
            
            // Advance
            inPtr += AnaHop
            outPtr += SynHop
            currentOutEnd = max(currentOutEnd, actualPos + windowSize)
        }
        
        // Save new tail
        val consumed = targetSize
        val remaining = max(0, currentOutEnd - consumed)
        for (i in 0 until remaining) {
             if (consumed + i < tempOut.size) {
                 outputTail[i] = tempOut[consumed + i]
             } else {
                 outputTail[i] = 0f
             }
        }
        // Zero rest of tail
        for (i in remaining until outputTail.size) outputTail[i] = 0f
        
        // Result
        // Better normalization: Hanning window sums to 0.5 when overlapped by 50%.
        // Since our SynHop is windowSize/4, we have ~4x overlap.
        // Sum of Hann window with 75% overlap is 2.0. So we divide by 2.0 (multiply by 0.5f).
        // However, SOLA alignment varies. We use a slightly safer factor.
        val normFactor = 1.0f / (windowSize.toFloat() / SynHop.toFloat() * 0.5f) 
        
        for (i in 0 until targetSize) {
             output[i] = tempOut[i] * normFactor
        }
        
        return output
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
    
    // --- BiQuad Filter ---
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
                x2 = x1; x1 = x; y2 = y1; y1 = y
                output[i] = y
            }
            return output
        }
        
        companion object {
            // Gentle Filters (Q=0.7)
            fun lowShelf(freq: Float, dbGain: Float, sampleRate: Int): BiQuadFilter {
                val a = 10f.pow(dbGain / 40f)
                val w0 = 2f * PI.toFloat() * freq / sampleRate
                val alpha = sin(w0) / 2f * sqrt((a + 1/a)*(1/0.7f - 1) + 2)
                val cosw = cos(w0)
                val a0 = (a+1) + (a-1)*cosw + 2*sqrt(a)*alpha
                 return BiQuadFilter(
                    (a*((a+1) - (a-1)*cosw + 2*sqrt(a)*alpha))/a0,
                    (2*a*((a-1) - (a+1)*cosw))/a0,
                    (a*((a+1) - (a-1)*cosw - 2*sqrt(a)*alpha))/a0,
                    (-2*((a-1) + (a+1)*cosw))/a0,
                    ((a+1) + (a-1)*cosw - 2*sqrt(a)*alpha)/a0
                )
            }
            fun highShelf(freq: Float, dbGain: Float, sampleRate: Int): BiQuadFilter {
                val a = 10f.pow(dbGain / 40f)
                val w0 = 2f * PI.toFloat() * freq / sampleRate
                val alpha = sin(w0) / 2f * sqrt((a + 1/a)*(1/0.7f - 1) + 2)
                val cosw = cos(w0)
                val a0 = (a+1) - (a-1)*cosw + 2*sqrt(a)*alpha
                return BiQuadFilter(
                    (a*((a+1) + (a-1)*cosw + 2*sqrt(a)*alpha))/a0,
                    (-2*a*((a-1) + (a+1)*cosw))/a0,
                    (a*((a+1) + (a-1)*cosw - 2*sqrt(a)*alpha))/a0,
                    (2*((a-1) - (a+1)*cosw))/a0,
                    ((a+1) - (a-1)*cosw - 2*sqrt(a)*alpha)/a0
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
            fun peaking(freq: Float, dbGain: Float, q: Float, sampleRate: Int): BiQuadFilter {
                val a = 10f.pow(dbGain / 40f)
                val w0 = 2f * PI.toFloat() * freq / sampleRate
                val alpha = sin(w0) / (2f * q)
                val cosw = cos(w0)
                val a0 = 1f + alpha / a
                return BiQuadFilter(
                    (1f + alpha * a) / a0, (-2f * cosw) / a0, (1f - alpha * a) / a0,
                    (-2f * cosw) / a0, (1f - alpha / a) / a0
                )
            }
        }
    }
}
