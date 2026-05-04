# Iron Gate - App Blocker

A parental control Android app that blocks selected applications using an overlay. When a lock session is active, blocked apps cannot be opened.

## Features

- **App Blocking**: Select apps to block during lock sessions
- **Overlay Blocking**: Shows a non-dismissible screen when blocked apps are opened
- **Device Admin**: Prevents the app from being disabled while lock is active
- **Permanent Protection**: Survives device reboots
- **Foreground Service**: Runs continuously to maintain protection

## Permissions Required

1. **Device Admin** - To prevent disabling during active lock
2. **Accessibility Service** - To detect and block app launches with overlay
3. **Usage Stats** - To monitor app usage
4. **Foreground Service** - To keep protection running
5. **Boot Completed** - To restart protection after reboot

## How to Use

### First Setup
1. Install the APK on your Android device
2. Enable "Install from unknown sources" in settings
3. Open the app

### Grant Permissions
1. Tap to enable **Device Admin** when prompted
2. Enable **Accessibility Service** in settings
3. Allow **Usage access** when prompted

### Using the App
1. Go to "Apps" tab to select apps to block
2. Return to home screen
3. Enter PIN and tap "Lock" to start a lock session
4. Blocked apps will show a warning overlay when opened
5. Enter PIN and tap "Unlock" to end the session

## Default PIN

The default PIN is `1234`. Change it in Settings.

## Technical Details

- **Min Android Version**: Android 10 (API 29)
- **Target Android Version**: Android 14 (API 34)
- **Language**: Kotlin with Jetpack Compose

## Security Notes

- The lock cannot be bypassed while active
- Device Admin prevents disabling the app without unlocking first
- Boot completed receiver ensures protection resumes after restart