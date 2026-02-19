package com.voicechanger

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    
    private var isServiceRunning = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF9D50BB),
                    background = Color(0xFF0A0A0C)
                )
            ) {
                VoiceChangerScreen()
            }
        }
    }
    
    @Composable
    fun VoiceChangerScreen() {
        var isActive by remember { mutableStateOf(isServiceRunning) }
        var selectedPersona by remember { mutableStateOf("Neutral") }
        
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 48.dp)
                ) {
                    Text(
                        text = "CALL VOICE",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "CHANGER",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "For WhatsApp & Telegram Calls",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                
                // Persona Selection
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select Voice Profile",
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text(text = "Male Variations", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        listOf("MV1", "MV2", "MV3", "MV4").forEach { p ->
                            PersonaSmallButton(p, selectedPersona.equals(p, ignoreCase = true)) {
                                selectedPersona = p
                                if (isActive) updatePersona(p)
                            }
                        }
                    }

                    Text(text = "Female Variations", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        listOf("FV1", "FV2", "FV3", "FV4").forEach { p ->
                            PersonaSmallButton(p, selectedPersona.equals(p, ignoreCase = true)) {
                                selectedPersona = p
                                if (isActive) updatePersona(p)
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        PersonaButton("Neutral", selectedPersona == "Neutral") {
                            selectedPersona = "Neutral"
                            if (isActive) updatePersona("Neutral")
                        }
                    }
                }
                
                // Control Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 48.dp)
                ) {
                    Button(
                        onClick = {
                            isActive = !isActive
                            if (isActive) {
                                startService(selectedPersona)
                            } else {
                                stopService()
                            }
                        },
                        modifier = Modifier
                            .size(100.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isActive) Color(0xFFFF4B2B) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (isActive) "ON" else "OFF",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (isActive) "Voice changer is active" else "Tap to activate",
                        color = if (isActive) Color(0xFF00F2FE) else Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Test Button
                    Button(
                        onClick = { 
                            if (!isActive) {
                                // Simple test: Start service briefly or use a separate test method
                                // For now, just a toast instruction
                                Toast.makeText(this@MainActivity.applicationContext, "Activate specific voice to test loopback", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity.applicationContext, "Speak now! You should hear yourself.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                    ) {
                        Text("Test Output (Hear Yourself)", color = Color.White)
                    }
                }
            }
        }
    }
    
    @Composable
    fun PersonaSmallButton(text: String, selected: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF1A1A1C),
                contentColor = Color.White
            ),
            modifier = Modifier.height(36.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Text(text, fontSize = 12.sp)
        }
    }

    @Composable
    fun PersonaButton(text: String, selected: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF1A1A1C),
                contentColor = Color.White // Force white text
            ),
            modifier = Modifier.height(48.dp)
        ) {
            Text(text)
        }
    }
    
    private fun startService(persona: String) {
        try {
            val intent = Intent(this, CallAudioService::class.java).apply {
                action = CallAudioService.ACTION_START
                putExtra(CallAudioService.EXTRA_PERSONA, persona)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isServiceRunning = true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_LONG).show()
            isServiceRunning = false
        }
    }
    
    private fun stopService() {
        try {
            val intent = Intent(this, CallAudioService::class.java).apply {
                action = CallAudioService.ACTION_STOP
            }
            startService(intent)
            isServiceRunning = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updatePersona(persona: String) {
        try {
            val intent = Intent(this, CallAudioService::class.java).apply {
                action = CallAudioService.ACTION_SET_PERSONA
                putExtra(CallAudioService.EXTRA_PERSONA, persona)
            }
            startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
