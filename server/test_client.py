import asyncio
import websockets
import numpy as np
import time

async def test_client():
    uri = "ws://localhost:8000/ws/stream"
    
    # Generate dummy audio: 1 second of 440Hz sine wave at 44.1kHz, 16-bit
    sample_rate = 44100
    duration = 1.0
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)
    # 440Hz sine wave
    audio_data = (np.sin(2 * np.pi * 440 * t) * 32767).astype(np.int16)
    audio_bytes = audio_data.tobytes()
    
    chunk_size = 4096 # bytes
    
    print(f"Connecting to {uri}...")
    try:
        async with websockets.connect(uri) as websocket:
            print("Connected!")
            
            start_time = time.time()
            
            # Send in chunks
            for i in range(0, len(audio_bytes), chunk_size):
                chunk = audio_bytes[i:i+chunk_size]
                await websocket.send(chunk)
                
                # Receive response
                response = await websocket.recv()
                print(f"Sent {len(chunk)} bytes, Received {len(response)} bytes")
                
            total_time = time.time() - start_time
            print(f"Completed in {total_time:.2f}s")
            
    except Exception as e:
        print(f"Connection failed: {e}")
        print("Make sure the server is running: 'uvicorn main:app --host 0.0.0.0 --port 8000'")

if __name__ == "__main__":
    asyncio.run(test_client())
