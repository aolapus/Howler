# Mesh Network Connection & Alert Implementation Plan

The user reports that the mesh network is failing to establish connections and no "alarms" are heard on receiving messages. This plan aims to diagnose connection failures, improve connection reliability, and implement a high-priority alert system.

## User Review Required

> [!IMPORTANT]
> The "Alarm" will use the default alarm sound and vibration. I will also make the notification high-priority so it pops up (heads-up notification) when an emergency alert is received.

## Proposed Changes

### [Nearby Service Improvements]

#### [MODIFY] [NearbyForegroundService.kt](file:///Users/aolvaldezco/AndroidStudioProjects/EmergencyBroadcastApp2/app/src/main/java/com/example/emergencybroadcastapp/NearbyForegroundService.kt)
- Add comprehensive logging with `.addOnFailureListener` for all Nearby API calls.
- Improve connection logic to avoid potential collisions during `onEndpointFound`.
- Implement a "Loud Alert" mechanism (Sound + Vibration + High Priority Notification) in `handleIncomingMessage`.
- Update `SERVICE_ID` to be more specific to avoid conflicts with other apps (optional but good practice).
- Add checks for Bluetooth/WiFi/Location being enabled (logging warnings).

### [UI & Feedback]

#### [MODIFY] [MainActivity.kt](file:///Users/aolvaldezco/AndroidStudioProjects/EmergencyBroadcastApp2/app/src/main/java/com/example/emergencybroadcastapp/MainActivity.kt)
- Add a "Test Connection" or status indicator if possible (e.g., "Scanning for nodes...").
- Ensure it requests all necessary permissions.

## Verification Plan

### Automated Tests
- Build the project to ensure no syntax errors.

### Manual Verification
1. Deploy to two devices.
2. Observe Logcat for "MeshService" tags to see if advertising/discovery starts successfully.
3. Check if `onEndpointFound` is triggered.
4. Verify that `onConnectionResult` logs a success status.
5. Send a broadcast from one device and verify the other device:
   - Plays a sound.
   - Vibrates.
   - Shows a high-priority heads-up notification.
   - Updates the latency log.
