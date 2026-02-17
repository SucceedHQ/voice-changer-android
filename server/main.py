import asyncio
import logging
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from inference import VoiceChanger
import audio_utils

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("VoiceChangerServer")

app = FastAPI(title="High-Fidelity AI Voice Changer API")

# Allow CORS for development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize Voice Changer with default models
# In a real scenario, you'd load models here
voice_changer = VoiceChanger()

@app.get("/")
async def root():
    return {"message": "Voice Changer Server is Running"}

@app.websocket("/ws/stream")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    logger.info("Client connected")
    
    # Initial handshake to set parameters (optional, e.g., target voice)
    # Protocol: 
    # 1. Client sends config JSON (optional)
    # 2. Client sends binary audio chunks
    # 3. Server sends back processed binary audio chunks

    try:
        current_voice = "female" # Default

        while True:
            # Receive message
            message = await websocket.receive()
            
            if "text" in message:
                # Config message
                import json
                try:
                    config = json.loads(message["text"])
                    if "voice" in config:
                        current_voice = config["voice"].lower()
                        logger.info(f"Switched voice to: {current_voice}")
                except Exception as e:
                    logger.error(f"Failed to parse config: {e}")
                    
            elif "bytes" in message:
                # Audio message
                data = message["bytes"]
                
                # Process audio
                audio_np = audio_utils.bytes_to_numpy(data)
                
                # Inference with current_voice
                processed_audio_np = await voice_changer.convert_voice(audio_np, target_voice=current_voice)
                
                processed_bytes = audio_utils.numpy_to_bytes(processed_audio_np)
                
                await websocket.send_bytes(processed_bytes)

    except WebSocketDisconnect:
        logger.info("Client disconnected")
    except Exception as e:
        logger.error(f"Error in websocket connection: {e}")
        await websocket.close()
