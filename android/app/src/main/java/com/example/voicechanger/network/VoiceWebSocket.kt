package com.example.voicechanger.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

class VoiceWebSocket(
    private val serverUrl: String = "ws://10.0.2.2:8000/ws/stream" // 10.0.2.2 is localhost for Emulator
) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    
    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    // Callback for received audio data
    var onAudioDataReceived: ((ByteArray) -> Unit)? = null

    fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("VoiceWebSocket", "Connected")
                _connectionState.value = "Connected"
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Receive processed audio
                onAudioDataReceived?.invoke(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("VoiceWebSocket", "Closing: $reason")
                _connectionState.value = "Disconnected"
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("VoiceWebSocket", "Error: ${t.message}")
                _connectionState.value = "Error: ${t.message}"
            }
        })
    }

    fun sendAudio(data: ByteArray) {
        webSocket?.send(data.toByteString())
    }

    fun sendConfig(json: String) {
        webSocket?.send(json)
    }

    fun close() {
        webSocket?.close(1000, "User disconnected")
    }
}
