package com.voicechanger.xposed

import android.media.AudioRecord
import com.voicechanger.audio.VoiceProcessor
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class VoiceChangerHook : IXposedHookLoadPackage {

    private val voiceProcessor = VoiceProcessor()
    
    // We need shared preferences or a file to know which persona is selected
    // For now, hardcoding to Female/Male based on file check or efficient IPC
    // Ideally, use XSharedPreferences
    
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Target specific VoIP apps or System Framework
        if (lpparam.packageName != "com.whatsapp" && 
            lpparam.packageName != "org.telegram.messenger" &&
            lpparam.packageName != "org.telegram.messenger.web") {
            return
        }

        XposedBridge.log("VoiceChanger: Hooking into ${lpparam.packageName}")

        // Hook AudioRecord.read(byte[], int, int)
        XposedHelpers.findAndHookMethod(
            AudioRecord::class.java,
            "read",
            ByteArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val bytesRead = param.result as Int
                    if (bytesRead > 0) {
                        val buffer = param.args[0] as ByteArray
                        // Process logic here
                        // Note: Real-time processing in Java/Kotlin might fail strict timing
                        // Ideally checking if processing is enabled via XSharedPreferences
                        
                        // For proof of concept, verify we can touch the buffer
                        // To implement full DSP, we need to convert byte[] to short[], process, and convert back
                        // Or update VoiceProcessor to handle bytes
                    }
                }
            }
        )
        
        // Hook AudioRecord.read(short[], int, int) - Preferred for quality
        XposedHelpers.findAndHookMethod(
            AudioRecord::class.java,
            "read",
            ShortArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val shortsRead = param.result as Int
                    if (shortsRead > 0) {
                        val buffer = param.args[0] as ShortArray
                        val offset = param.args[1] as Int
                        
                        // Apply Voice Processing IN-PLACE
                        // We must be extremely fast here
                        
                        // NOTE: In a real Xposed module, we read the desired persona 
                        // from XSharedPreferences. For now, default to "Female" for testing.
                        voiceProcessor.setPersona("Female") 
                        
                        val processed = voiceProcessor.processAudio(buffer.copyOfRange(offset, offset + shortsRead))
                        
                        // Copy back modified audio to the original buffer
                        for (i in 0 until shortsRead) {
                            if (i < processed.size) {
                                buffer[offset + i] = processed[i]
                            }
                        }
                    }
                }
            }
        )
    }
}
