import numpy as np
import io
import soundfile as sf

def bytes_to_numpy(audio_bytes: bytes, dtype=np.int16) -> np.ndarray:
    """
    Convert raw PCM bytes to a numpy array.
    Assumes 16-bit integer PCM.
    """
    # Create a numpy array from the bytes
    audio_array = np.frombuffer(audio_bytes, dtype=dtype)
    
    # Normalize to float32 range [-1.0, 1.0] for processing if needed
    # But RVC might expect specific format. For now, let's keep it simple.
    # If we need float:
    # return audio_array.astype(np.float32) / 32768.0
    
    return audio_array

def numpy_to_bytes(audio_array: np.ndarray, dtype=np.int16) -> bytes:
    """
    Convert numpy array back to raw PCM bytes.
    """
    # If float, clip and convert to int16
    if audio_array.dtype == np.float32:
        audio_array = np.clip(audio_array, -1.0, 1.0)
        audio_array = (audio_array * 32767).astype(np.int16)
        
    return audio_array.tobytes()

def resample_audio(audio: np.ndarray, original_sr: int, target_sr: int) -> np.ndarray:
    """
    Resample audio if necessary.
    """
    if original_sr == target_sr:
        return audio
        
    # Using librosa or scipy for resampling would be ideal here
    # import librosa
    # return librosa.resample(audio, orig_sr=original_sr, target_sr=target_sr)
    
    # Placeholder for simplicity without heavy dependencies for now
    return audio
