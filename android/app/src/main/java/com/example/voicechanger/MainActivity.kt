package com.example.voicechanger

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.voicechanger.viewmodel.VoiceChangerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: VoiceChangerViewModel by viewModels()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission Granted
            } else {
                // Permission Denied
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFBB86FC),
                    secondary = Color(0xFF03DAC5),
                    background = Color(0xFF121212)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceChangerScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun VoiceChangerScreen(viewModel: VoiceChangerViewModel) {
    val isRecording by viewModel.isRecording.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val waveformData by viewModel.waveformData.collectAsState()
    
    var selectedPersona by remember { mutableStateOf("Female") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header
        Text(
            text = "AI Voice Changer",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp)
        )

        // Status
        StatusIndicator(connectionStatus)

        // Waveform Visualization
        WaveformView(waveformData, modifier = Modifier.height(150.dp).fillMaxWidth())

        // Persona Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PersonaButton("Male", selectedPersona == "Male") { 
                selectedPersona = "Male"
                viewModel.setPersona("Male")
            }
            PersonaButton("Female", selectedPersona == "Female") { 
                selectedPersona = "Female"
                viewModel.setPersona("Female")
            }
        }

        // Record Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { viewModel.toggleRecording() },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(80.dp)
                ) { }
                Text(
                    text = if (isRecording) "STOP" else "REC",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { /* Share functionality */ },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Share to WhatsApp/Telegram")
            }
        }
    }
}

@Composable
fun StatusIndicator(status: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (status == "Connected") Color.Green else Color.Red,
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = status, color = Color.Gray)
    }
}

@Composable
fun PersonaButton(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.DarkGray
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text = name, color = if (isSelected) Color.Black else Color.White)
    }
}

@Composable
fun WaveformView(data: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val widthPerPoint = size.width / 50f
        val path = Path()
        val centerY = size.height / 2f
        
        path.moveTo(0f, centerY)
        
        data.forEachIndexed { index, amplitude ->
            val x = index * widthPerPoint
            val y = centerY - (amplitude * 50f) // Scale amplitude
            path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = Color.Cyan,
            style = Stroke(width = 3f)
        )
    }
}
