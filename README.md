<div align="center">

# ✋ Air Transfer (AirXfer)
### *Touchless, Gesture-Controlled File Sharing for Android*

**Grab. Move. Release.**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android_9.0%2B_(API_28%2B)-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![MediaPipe](https://img.shields.io/badge/MediaPipe-On--Device_Vision-FF6F00.svg?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/mediapipe)
[![P2P Wi-Fi](https://img.shields.io/badge/Transport-Nearby_Connections_P2P-4285F4.svg?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/nearby/connections/overview)

<br/>

<img src="assets/air_transfer_preview.gif" alt="Air Transfer Gesture Demonstration" width="700" style="border-radius: 16px; box-shadow: 0 10px 40px rgba(0,0,0,0.5);" />

<p align="center">
  <em>Watch the full high-resolution product announcement film: <a href="assets/air_transfer_announcement.mp4"><b>assets/air_transfer_announcement.mp4</b></a></em>
</p>

</div>

---

## 🌟 The Vision

> **"Today's fun experiment is tomorrow's wish, and the future's essential need."**

Transferring a file in physical life is effortless: you pick up a document and hand it to the person next to you. On smartphones, however, file sharing still feels like work: open a share sheet, scroll through endless apps, wait for Bluetooth device discovery, select the right name from a list, and wait for confirmation.

**Air Transfer rethinks sharing from the physical world up:**
- 🧪 **Today:** A playful, tactile Android experiment that lets you literally grab a file out of thin air and toss it to a nearby phone.
- ⚡ **Tomorrow:** A faster, more natural interaction paradigm for mobile devices without looking at recipient lists.
- 🌐 **The Future:** A system-level Android capability. What if gestures became part of the OS itself? Reach out, grab, and release—without opening an app. Touchless interaction shouldn't require custom radar hardware or OEM lock-in; just smarter, human-centered software.

---

## 🖐️ Core Interaction Model

The interaction revolves around two physical gestures recognized in real-time by the device camera:

```
Sender:    ✋ Open Palm  ──[ Close Fist ]──>  ✊ Fist Armed ("GRAB")
                                                   │
                                            [ Spatial Transit ]
                                                   │
Receiver:  ✊ Fist Armed  ──[ Open Palm ]───>  🖐️ Open Palm ("RELEASE & CATCH")
```

| Phase | Gesture Action | System Response |
| :--- | :--- | :--- |
| **1. Select** | Multi-file picker | Files highlighted; CameraX pipeline initializes. |
| **2. Grab** | **✋ Open Palm $\rightarrow$ ✊ Close Fist** | Target file lifts with elevation shadow and glowing aura; payload armed for transmission. |
| **3. Move** | Physical proximity | Nearby devices discovered via Bluetooth Low Energy; high-bandwidth Wi-Fi Direct established automatically. |
| **4. Release** | **✊ Close Fist $\rightarrow$ 🖐️ Open Palm** | Incoming portal captures incoming byte stream; payload saved directly to public storage. |

---

## 🛠️ System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       Air Transfer UI                       │
│      Jetpack Compose Material 3 + Custom Radar Canvas       │
└──────────────────────────────┬──────────────────────────────┘
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
┌─────────────────────────────┐ ┌─────────────────────────────┐
│      CameraX Vision Pipeline│ │ NearbyTransferManager       │
│  • ImageAnalysis (30 FPS)   │ │  • Google Nearby Connections│
│  • YUV to RGB stream        │ │  • P2P Star / Wi-Fi Direct  │
└──────────────┬──────────────┘ └──────────────┬──────────────┘
               ▼                               ▼
┌─────────────────────────────┐ ┌─────────────────────────────┐
│  MediaPipe Hand Landmarker  │ │ Multi-File Payload Engine   │
│  • 21 3D landmarks          │ │  • JSON Manifest handshake  │
│  • On-device CPU/GPU runtime│ │  • Chunked byte streams     │
│  • Zero Cloud / 100% Private│ │  • Speed & progress state   │
└──────────────┬──────────────┘ └──────────────┬──────────────┘
               ▼                               ▼
┌─────────────────────────────┐ ┌─────────────────────────────┐
│   GestureStateMachine       │ │ FileRepository & MediaStore │
│  • Palm vs. Fist classifier │ │  • Download/AirTransfer     │
│  • Temporal hysteresis debnc│ │  • MediaScannerConnection   │
│  • Grab / Release event bus │ │  • Instant Gallery / Photos │
└─────────────────────────────┘ └─────────────────────────────┘
```

### Key Technical Highlights
- **On-Device Machine Learning:** Powered by Google MediaPipe Hand Landmarker (`hand_landmarker.task`). Computes 21 3D coordinate landmarks per hand with sub-millisecond inference time. No external cloud servers or internet connections are ever contacted.
- **High-Throughput P2P Radio:** Leverages Google Play Services Nearby Connections API (`P2P_STAR` strategy). Automatically negotiates local Wi-Fi Direct or hotspot channels, achieving transfer speeds up to **80 MB/s**.
- **Public MediaStore Integration:** Received files are placed in `/storage/emulated/0/Download/AirTransfer` and registered with the Android `MediaStore` (Pictures, Movies, Downloads) and `MediaScannerConnection`, ensuring instant visibility in Google Photos, Gallery, and Files apps.
- **Hardware-Agnostic:** Works on standard front and rear smartphone cameras across all major Android manufacturers without requiring specialized Soli radar, LiDAR, or OEM-specific APIs.

---

## 🚀 Getting Started

### Option 1: Direct APK Download
1. Head to [**Releases**](https://github.com/Hackerop777/Air-Transfer/releases) or download the prebuilt binary directly:
   - [**📥 Download AirTransfer-v1.0.0-preview.apk**](https://github.com/Hackerop777/Air-Transfer/releases/download/v1.0.0-preview/AirTransfer-v1.0.0-preview.apk)
2. Install the APK on two Android devices (Android 9.0+ / API 28+).
3. Ensure both devices have **Wi-Fi** and **Bluetooth** enabled (no active internet or shared Wi-Fi network is required).
4. Launch **Air Transfer** on both devices:
   - Tap **Send** on Device A, pick files, and show your hand to the camera.
   - Tap **Receive** on Device B.
   - Close your fist on Device A to **Grab**; open your palm on Device B to **Release & Catch**!

### Option 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/Hackerop777/Air-Transfer.git
cd Air-Transfer

# Build debug APK using Gradle Wrapper
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug
```

---

## 🧭 Android OS Integration Roadmap

Air Transfer was architected from day one so that its modular subsystems can migrate into the Android Open Source Project (AOSP) as a native system capability:

1. **`com.android.server.airtransfer.AirTransferService`**:
   Background system service managing low-power perception hooks and device rendezvous.
2. **SystemUI Quick Settings Tile & Ambient Gestures**:
   Always-ready spatial gestures that can be triggered directly from the lock screen or launcher without launching a standalone app.
3. **Android Share Sheet Integration**:
   Direct target in `ChooserActivity` to allow grabbing and casting content from any browser, gallery, or document viewer across physical room space.

---

## 📄 License

```text
Copyright 2026 Air Transfer Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```