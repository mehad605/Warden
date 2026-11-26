# `receivers` Package

Android `BroadcastReceiver` entry points registered in `AndroidManifest.xml`:

- `WardenDeviceAdminReceiver.kt` — `DeviceAdminReceiver` handling Device Administrator enable/disable lifecycle toasts for Anti-Uninstall protection.

**Upgrade Invariant:** Do not rename or move `com.warden.app.receivers.WardenDeviceAdminReceiver`; Android keys Device Admin registration to this exact FQCN.
