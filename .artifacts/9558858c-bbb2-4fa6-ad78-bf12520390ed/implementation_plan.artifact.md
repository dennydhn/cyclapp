# Fix GPS Tracking, Duration Freeze, and Altitude Display Issues

## Problem Description
1. **Duration Freeze & Jump**: The ride duration timer currently updates only inside `onLocation()`. When GPS signal momentarily drops or stutters (even outdoors), location updates stop arriving, causing the duration counter to freeze completely. When GPS reconnects, `onLocation` fires again and the duration jumps forward by the elapsed time.
2. **Missing Altitude (`--`)**: When GPS updates are lost or when a location fix lacks altitude (`hasAltitude() == false`), altitude drops to `null`, displaying `--` on the dashboard.

## Proposed Changes

### [RideEngine.kt](file:///C:/Personal/work/Freewheel/cyclapp/app/src/main/java/com/example/cyclapp/ride/RideEngine.kt)
- Add a periodic 1-second coroutine ticker (`timerJob`) when recording (`state == State.RECORDING`) to continuously update metrics (`durationSeconds`, etc.) in real-time, independent of GPS event frequency.
- Track `latestAltitude` so that if a location update lacks an altitude reading (`hasAltitude() == false`), the last known valid altitude is preserved instead of reverting to `null`.
- Properly start, pause, resume, and cancel `timerJob` on ride start, pause, resume, finish, and auto-pause.

### [LocationTracker.kt](file:///C:/Personal/work/Freewheel/cyclapp/app/src/main/java/com/example/cyclapp/location/LocationTracker.kt)
- Ensure location request parameters are robust for continuous outdoor tracking.

## Verification Plan

### Automated Tests
- Build the app with `gradle_build` (`app:assembleDebug`) to ensure compilation succeeds.

### Manual Verification
- Deploy the app to a connected device/emulator and verify:
  1. Duration timer increments smoothly every second during recording without freezing.
  2. Altitude displays valid values and does not revert to `--` during temporary GPS fluctuations.
