package com.voicechanger

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voicechanger.audio.AudioRouter

class CallAudioService : Service() {
    
    private val audioRouter = AudioRouter()
    private var currentPersona = "neutral"
    
    companion object {
        const val CHANNEL_ID = "CallAudioServiceChannel"
        const val ACTION_START = "com.voicechanger.START"
        const val ACTION_STOP = "com.voicechanger.STOP"
        const val ACTION_SET_PERSONA = "com.voicechanger.SET_PERSONA"
        const val EXTRA_PERSONA = "persona"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d("CallAudioService", "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentPersona = intent.getStringExtra(EXTRA_PERSONA) ?: "neutral"
                startForegroundService()
                audioRouter.start(currentPersona)
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
            ACTION_SET_PERSONA -> {
                currentPersona = intent.getStringExtra(EXTRA_PERSONA) ?: "neutral"
                audioRouter.setPersona(currentPersona)
            }
        }
        
        return START_STICKY
    }
    
    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(1, notification)
        Log.d("CallAudioService", "Foreground service started")
    }
    
    private fun stopForegroundService() {
        audioRouter.stop()
        stopForeground(true)
        stopSelf()
        Log.d("CallAudioService", "Foreground service stopped")
    }
    
    private fun createNotification(): Notification {
        val stopIntent = Intent(this, CallAudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Changer Active")
            .setContentText("Voice: $currentPersona")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .build()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call Audio Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Runs voice changer during calls"
        }
        
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        audioRouter.stop()
        super.onDestroy()
        Log.d("CallAudioService", "Service destroyed")
    }
}
