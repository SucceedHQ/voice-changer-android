import numpy as np
import time
import logging
import torch
import os
import asyncio
import pyworld as pw
import scipy.signal as signal

# Check for RVC dependencies
try:
    # from rvc_infer import RVCInference 
    RVC_AVAILABLE = False
except ImportError:
    RVC_AVAILABLE = False

logger = logging.getLogger("VoiceChangerInference")

class VoiceChanger:
    def __init__(self, model_dir="models/"):
        self.model_dir = model_dir
        self.models = {}
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.sample_rate = 44100
        self.load_models()

    def load_models(self):
        """
        Load RVC models (.pth) and Index files (.index) from the models directory.
        """
        logger.info(f"Loading models on device: {self.device}")
        
        # Define expected models
        personas = ["male", "female"]
        
        for persona in personas:
            pth_path = os.path.join(self.model_dir, f"{persona}.pth")
            index_path = os.path.join(self.model_dir, f"{persona}.index")
            
            if os.path.exists(pth_path) and RVC_AVAILABLE:
                logger.info(f"Found RVC model for {persona}")
                # self.models[persona] = RVCInference(model_path=pth_path, index_path=index_path, device=self.device)
            else:
                logger.info(f"Using High-Quality Fallback for {persona}")
                self.models[persona] = "fallback"

    async def convert_voice(self, audio_data: np.ndarray, target_voice: str) -> np.ndarray:
        """
        Convert voice to target persona using RVC or High-Quality Fallback.
        """
        start_time = time.time()
        
        if target_voice not in self.models:
            return audio_data

        # Ensure audio is float64 for pyworld
        audio_double = audio_data.astype(np.float64)
        if np.max(np.abs(audio_double)) > 1.0:
            audio_double = audio_double / 32768.0

        if self.models[target_voice] == "fallback":
            processed_audio = await self._fallback_inference(audio_double, target_voice)
        else:
            # Real RVC Inference placeholder
            processed_audio = audio_double 
        
        # Convert back to int16
        processed_audio = (processed_audio * 32767).astype(np.int16)
        
        logger.debug(f"Inference time: {(time.time() - start_time)*1000:.2f}ms")
        return processed_audio

    async def _fallback_inference(self, x: np.ndarray, target: str) -> np.ndarray:
        """
        High-quality pitch and formant shifting using WORLD vocoder.
        """
        # x is float64 array
        # 1. Harvest F0
        _f0, t = pw.harvest(x, self.sample_rate)
        
        # 2. Extract spectral envelope and aperiodicity
        sp = pw.cheaptrick(x, _f0, t, self.sample_rate)
        ap = pw.d4c(x, _f0, t, self.sample_rate)
        
        # 3. Modify F0 and Formant (Spectrum)
        if target == "female":
            modified_f0 = _f0 * 1.5           # Raise Pitch
            # Formant shift (compression/expansion in freq domain)
            # Roughly, we want to shift the spectrum up for female
            modified_sp = self._shift_formant(sp, 1.15)
        elif target == "male":
            modified_f0 = _f0 * 0.75          # Lower Pitch
            modified_sp = self._shift_formant(sp, 0.85)
        else: # Deep Male / Child etc could be added
            modified_f0 = _f0
            modified_sp = sp

        # 4. Synthesize
        y = pw.synthesize(modified_f0, modified_sp, ap, self.sample_rate)
        return y

    def _shift_formant(self, sp: np.ndarray, factor: float) -> np.ndarray:
        """
        Shift the spectral envelope by a factor to change formant perceived age/gender.
        """
        if factor == 1.0:
            return sp
            
        new_sp = np.zeros_like(sp)
        v_len = sp.shape[1]
        
        for i in range(sp.shape[0]):
            # Linear interpolation for simple formant shifting
            x_orig = np.arange(v_len)
            x_new = x_orig / factor
            new_sp[i] = np.interp(x_orig, x_new, sp[i])
            
        return new_sp
