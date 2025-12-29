# LostFound App - User Manual

A campus lost-and-found reporting system with real-time TCP communication.

---

## Prerequisites

### For the Backend Server (Python)
- Python 3.8 or higher
- pip (Python package manager)

### For the Android App
- Android Studio (latest version recommended)
- JDK 11 or higher
- Android SDK (API level 28 minimum, targeting API 35)
- An Android device or emulator running Android 9.0 (Pie) or higher

---

## Setup Instructions

### Step 1: Install Python Dependencies

Open a terminal in the project root directory (`tcp/`) and run:

```bash
pip install supabase
```

The server uses the following Python packages:
- `supabase` - For database and storage operations
- `socket`, `threading`, `base64`, `uuid`, `datetime` - Standard library (no installation needed)

### Step 2: Start the Backend Server

From the project root directory, run:

```bash
python server.py
```

The server will start listening on:
- **Host:** `0.0.0.0` (accepts connections from any interface)
- **Port:** `12345`

You should see output indicating the server is running.

### Step 3: Build and Run the Android App

#### Option A: Using Android Studio (Recommended)

1. Open Android Studio
2. Select **File > Open** and navigate to `tcp/LostFound/`
3. Wait for Gradle sync to complete
4. Connect your Android device via USB or start an Android emulator
5. Click the **Run** button (green play icon) or press `Shift+F10`

Install on a connected device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Network Configuration

### Important: Server IP Address

The Android app needs to connect to the server. By default, the server runs on your local machine.

**For Emulator Testing:**
- Use `10.0.2.2` as the server IP (Android emulator's alias for host machine)

**For Physical Device Testing:**
- Use your computer's local IP address (e.g., `192.168.1.x`)
- Ensure both devices are on the same network
- Check your IP with:
  - Windows: `ipconfig`
  - macOS/Linux: `ifconfig` or `ip addr`

### Firewall Configuration

Ensure port `12345` is open on your firewall for incoming TCP connections.

---

## App Features

| Feature | Description |
|---------|-------------|
| **User Authentication** | Register and login to your account |
| **Report Creation** | Report lost or found items with photos |
| **View Reports** | Browse all lost and found reports |
| **My Reports** | Manage your own reports |
| **Real-time Chat** | Message other users about items |
| **Notifications** | Receive updates on your reports |
| **Location Sharing** | Share location for meetups |
| **Profile Management** | Update your profile information |
| **Claims System** | Claim items and manage claims |
| **Photo Viewing** | View item photos in detail |

---

## Required Permissions

The app requires the following permissions:
- **Internet** - For server communication
- **Camera** - To take photos of items
- **Storage** - To select photos from gallery


---

## Quick Start Summary

1. Install Python dependency: `pip install supabase`
2. Start server: `python server.py`
3. Open `LostFound/` in Android Studio
4. Configure server IP in the app (if needed)
5. Run the app on device/emulator
6. Register a new account and start using the app
