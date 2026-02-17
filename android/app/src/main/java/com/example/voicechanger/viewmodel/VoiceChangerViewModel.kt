package com.example.voicechanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicechanger.audio.AudioPlayer
import com.example.voicechanger.audio.AudioRecorder
import com.example.voicechanger.network.VoiceWebSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VoiceChangerViewModel(application: Application) : AndroidViewModel(application) {

    private val audioRecorder = AudioRecorder { data ->
        // Direct conversion on-device for Zero-Setup Real-time
        val processed = audioProcessor.process(data)
        audioPlayer.playAudio(processed)
        updateWaveform(processed)

        // Still send to server/supabase if needed for high-fidelity or storage
        if (isConnected.value) {
            voiceWebSocket.sendAudio(data)
        }
    }

    private val audioPlayer = AudioPlayer()
    private val audioProcessor = OnDeviceAudioProcessor()
    private val voiceWebSocket = VoiceWebSocket()

    // UI State
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectionStatus = MutableStateFlow("On-Device (Ready)")
    val connectionStatus: StateFlow<String> = _connectionStatus
    
    // Waveform data for visualization (simplified)
    private val _waveformData = MutableStateFlow(listOf<Float>())
    val waveformData: StateFlow<List<Float>> = _waveformData

    init {
        connectToServer()
        
        // Listen to WebSocket connection state
        viewModelScope.launch {
            voiceWebSocket.connectionState.collect { status ->
                _connectionStatus.value = status
                _isConnected.value = status == "Connected"
            }
        }

        // Setup Audio Player callback
        voiceWebSocket.onAudioDataReceived = { data ->
            audioPlayer.playAudio(data)
            // Update waveform for visualization (simple aptitude)
            updateWaveform(data)
        }
    }

    private fun connectToServer() {
        voiceWebSocket.connect()
        // Start Audio Player (it waits for data)
        audioPlayer.start()
    }

    fun setPersona(persona: String) {
        audioProcessor.setPersona(persona)
        val config = "{\"voice\": \"$persona\"}"
        voiceWebSocket.sendConfig(config)
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        _isRecording.value = true
        audioRecorder.startRecording()
    }

    private fun stopRecording() {
        _isRecording.value = false
        audioRecorder.stopRecording()
    }
    
    private fun updateWaveform(data: ByteArray) {
        // Simple visualization logic: take avg amplitude of chunk
        // This is not real FFT, just for visual feedback
        if (data.isNotEmpty()) {
            val amplitude = data.map { Math.abs(it.toInt()) }.average().toFloat() / 128f
            val currentList = _waveformData.value.toMutableList()
            if (currentList.size > 50) currentList.removeAt(0)
            currentList.add(amplitude)
            _waveformData.value = currentList
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.stop()
        voiceWebSocket.close()
    }
}
