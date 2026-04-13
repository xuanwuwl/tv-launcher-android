# Android TV Launcher - Remote Control Server
# For Android 4.4+ Set-Top Boxes

## Features
- Auto-start specified app on boot (via Accessibility Service)
- Remote control via TCP socket (port 7816)
- Heartbeat & connection keep-alive
- Foreground service for stability

## Build
```bash
# Install SDK and configure ANDROID_HOME first
./gradlew assembleDebug
```

## Install
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissions Required
- Internet (socket communication)
- Boot Completed (auto-start)
- Foreground Service (keep alive)
- Accessibility Service (launch apps without root)

## Socket Protocol
Connect to port 7816, send commands:

```
CMD_PING                           # Heartbeat check
CMD_GET_STATUS                     # Get device status
CMD_CHANGE_APP:com.package.name    # Change auto-start app
CMD_GET_APPS                       # List installed apps
CMD_REBOOT                         # Reboot device
```

Response format: `OK:message` or `ERR:error_message`
