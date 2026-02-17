# VoIP Call Voice Changer for Android

Real-time voice transformation for WhatsApp and Telegram calls.

## Features
- 🎙️ Real-time voice changing (Male/Female/Neutral)
- 📱 Works with WhatsApp & Telegram calls  
- ⚡ Low latency (<100ms)
- 🔊 Human-sounding audio quality
- 🎯 Simple ON/OFF toggle interface

## Installation

### Download APK
Get the latest APK from the [Releases](../../releases) page.

### Build from Source
Requires:
- **Java 17** (NOT Java 25)
- Android SDK

```bash
# Set Java 17
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.x.x.x-hotspot

# Build
cd android
.\gradlew.bat assembleRelease
```

APK will be at: `android/app/build/outputs/apk/release/app-release-unsigned.apk`

## Usage
1. Install the APK on your Android phone
2. Open "Call Voice Changer"
3. Grant microphone permission
4. Select voice (Male/Female/Neutral)
5. Tap **ON**
6. Make a WhatsApp/Telegram call - your voice will be changed!

## Tech Stack
- Kotlin 2.0.20
- Jetpack Compose
- Real-time audio processing (pitch + formant shifting)
- Background service for seamless operation

## License
MIT
