# BlueToothMultiScreenSync

[![Android](https://img.shields.io/badge/Platform-Android-green)](https://developer.android.com/) 
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7f52ff)](https://kotlinlang.org/) 
[![Build](https://img.shields.io/badge/Build-Pending-yellow)](#)
[![Status](https://img.shields.io/badge/Status-Experimental-orange)](#)
[![Bluetooth](https://img.shields.io/badge/Protocol-Bluetooth-blue)](#)



**BlueToothMultiScreenSync** is an experimental Android project that turns multiple smartphones into a **continuous, shared canvas** using **Bluetooth Classic** for communication.  
When you drag an image across the screen of one device, it **seamlessly moves onto the neighboring phone**, with proper scaling, cropping, and alignment — no matter the devices’ resolutions, pixel densities, or aspect ratios.

The goal is to simulate an **extended display** setup (like dual monitors) without cables or Wi-Fi, using only **Bluetooth pairing**.  
The app is designed for **real-time interaction** (smooth dragging) and a **perfectly aligned visual experience** even if devices are physically different.


### Multimedia Assets Consideration

For this demonstration, the images used are pre-loaded and statically present in memory on all devices.

This approach has allowed me to focus entirely on the complex challenges of real-time coordinate synchronization via Bluetooth Classic and adaptive rendering on a virtual canvas, independent of screen specificities.

While extending this to dynamic image streaming or other large media would necessitate exploring higher-bandwidth protocols like Wi-Fi Direct, the goal of this project is to prove the feasibility of a fluid and perfectly aligned multi-screen experience by relying solely on Bluetooth capabilities for interaction management.


---

## Core Objectives

- **Real-time synchronization** of 2D object coordinates between devices over Bluetooth.
- **Consistent visual scaling**: the object’s size stays the same in physical space across all devices.
- **Edge crossing without gaps**: the image continues exactly where it left off.
- **Device-agnostic layout**: works regardless of pixel density (DPI) or resolution.
- **Extendable beyond two devices** (linear chain or grid configurations).


---

## Core Architecture

### 1. **Virtual Canvas System**
- The **Master device** defines the total virtual area (width × height).
- All positions are expressed in **virtual coordinates**, independent of any specific screen resolution.
- Each device is assigned a **viewport** (position and size) within the virtual canvas.

### 2. **Bluetooth Communication**
- **Master → Slaves**: Sends object coordinates and possibly velocity or interaction states.
- **Slaves**: Convert virtual coordinates to local screen coordinates based on their viewport definition.
- Protocol is kept minimal to reduce latency (single position packet per update).

### 3. **Coordinate Mapping**
- **Scaling**: Adjust object size based on master’s definition while keeping proportions identical.
- **Translation**: Position object correctly within the local screen's viewport.
- **Clipping**: If a device’s screen is smaller than the part of the virtual canvas it represents, the extra content is simply not shown.


---

## Features & Technical Approach

### 🔹 Virtual Plane & Viewports
- All devices share the same **virtual plane** measured in **Virtual Units (VU)**.
- The virtual plane acts as the *absolute coordinate system* for all rendering.
- Each device owns a **viewport** within this plane:
  - **offsetX / offsetY** → top-left corner in VU
  - **widthVU / heightVU** → viewport size in VU
- Conversion formula for rendering:
  ```scale = widthPixels / widthVU
  px = (xVU - offsetX) * scale
  py = (yVU - offsetY) * scale
  ```
  - This keeps **scale uniform** across devices while avoiding distortion.

### 🔹 Master / Slave Roles
- **Master device**:
- Defines the virtual plane dimensions.
- Assigns viewports to all devices.
- Tracks object positions in VU.
- Sends updates via Bluetooth.
- **Slave devices**:
- Receive their viewport assignment.
- Map VU → px for local rendering.
- Only draw the visible section of the image.

### 🔹 Drag & Sync
- Touch input is processed **only on the Master** by default.
- Master sends updates in `{xVU, yVU, wVU, hVU}` format.
- Slaves crop & render in real-time.

---

## Data Flow Overview

1. **Pairing Phase**
 - Devices connect over Bluetooth Classic SPP (RFCOMM).
 - Master is selected; others are Slaves.
2. **Initialization**
 - Master defines:
   - Virtual plane size (VU)
   - Each device’s viewport offsets and size (VU)
 - Sends this data to all Slaves.
3. **Interaction**
 - User drags the image on the Master.
 - Master calculates new position in VU.
 - Broadcasts coordinates to Slaves.
4. **Rendering**
 - Each device converts VU → px.
 - Draws only the visible section.
5. **Seamless Transition**
 - Image moves naturally across physical device borders.

---

## Example Configuration

- **Virtual plane**: `2000 × 1200` VU  
- **Two devices side-by-side**:
- Viewport A: `offsetX=0`, `widthVU=1000`
- Viewport B: `offsetX=1000`, `widthVU=1000`
- **Phone A**: `1080 × 2400 px`, scale = `1080 / 1000 = 1.08`
- **Phone B**: `720 × 1600 px`, scale = `0.72`
- **Image**: `200 × 200` VU at `(900, 100)` VU
- Part visible on A: `108 × 216` px
- Part visible on B: `72 × 144` px
- Alignment is perfect at the shared virtual border (`offsetX=1000`).

---

## Bluetooth Protocol

- **Transport**: Bluetooth Classic SPP (RFCOMM)
- **Message format** (JSON, small & readable):
```json
{
"x": 900.0,
"y": 100.0,
"w": 200.0,
"h": 200.0
}
```
## Future Extensions

### Multi-Device Grid
Support more than two devices, e.g., 2×2 grid configuration. Each device would:
- Know its grid position (row, column).
- Calculate its viewport based on grid cell.
- Map incoming virtual coordinates accordingly.

### Dynamic Device Joining
Allow devices to join or leave the virtual canvas dynamically. Upon joining:
- The master sends updated virtual layout and assigns the new device’s viewport.

### Alternative Communication Protocols
While Bluetooth ensures offline use, future versions may support:
- **Wi-Fi Direct** for higher bandwidth and lower latency.
- **WebSocket over LAN** for mixed-platform setups.

### Extended Interaction
Enable:
- Multiple draggable objects.
- Cross-device gestures.
- Shared state beyond just position (e.g., rotation, scale).

---

## ⚠️ Technical Considerations

 - Latency: Bluetooth Classic has ~20–50 ms typical latency → good for dragging, bad for ultra-fast animation.

 - Bezels: Large physical bezels break the visual illusion unless compensated.

 - Uniform scale: must be applied equally to X and Y to prevent distortion.

 - Portrait-only: first version fixed to portrait mode for simpler math.

 - Partial rendering: crop logic must handle cases where the image is 100% outside the viewport.

   
---

