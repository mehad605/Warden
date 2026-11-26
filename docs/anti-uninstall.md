# Anti-Uninstall Protection

Warden pairs Android's **Device Administrator** API with an accessibility-based settings guard to deter impulsive force-stops, data clears, and uninstalls.

---

## Two-Layer Design

### 1. Device Administrator (`WardenDeviceAdminReceiver`)

`com.warden.app.receivers.WardenDeviceAdminReceiver` extends `DeviceAdminReceiver` and declares an empty `<uses-policies/>` set in `res/xml/device_admin_rules.xml`. Warden requests **no** remote-wipe or lock-screen policies — registration alone prevents normal uninstallation until admin rights are deactivated.

Activation and deactivation flows are coordinated by `AntiUninstallUiController` (`feature/security/ui/`) and `DeviceAdminController` (`core/platform/`).

### 2. Accessibility Self-Protection (`SelfProtectionEvaluator`)

Before scanning for blocked keywords, `BlockerCoordinator` delegates every accessibility window snapshot to the pure-Kotlin `SelfProtectionEvaluator` (ignoring `com.warden.app` and safe browser/social/messaging packages):

- **App Info Guard:** Any screen mentioning `"warden"` together with `"force stop"`, `"kill"`, `"uninstall"`, or `"clear data"` triggers an immediate `pressHome()`.
- **Accessibility Revocation Guard:** Any screen mentioning `"warden"` together with `"warden needs access to accessibility data"`, `"stop warden"`, or `"turn off warden"` triggers an immediate `pressHome()`.
- **Device Admin Deactivation Guard:** When `antiUninstallEnabled == true`, any Device Admin deactivation screen mentioning `"warden"` (`"deactivate"` / `"de-activate"`) triggers `pressHome()`, **unless** it falls inside the 180-second activation grace window.

---

## 180-Second Activation Grace Window

When the user taps **Enable Anti-Uninstall**, Warden records `deviceAdminActivationRequestedAt = clock.currentTimeMillis()` via `BlockerAction.RecordAdminActivationRequest` before launching `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN`.

For the next **180,000 ms (3 minutes)**, `SelfProtectionEvaluator` allows Device Admin screens so the user can complete the system confirmation without being bounced to Home.

---

## Known Limitations

Warden is designed to prevent impulsive bypasses — not to resist deliberate administrative removal:
- **Safe Mode:** Disables third-party accessibility services, allowing Device Admin deactivation and uninstall.
- **ADB:** A user with USB Debugging enabled on a computer can use Android's ADB tool to clear active Accessibility Services, deactivate Device Admin, and uninstall the app (exact commands are intentionally omitted from this repo to preserve friction).
- **Factory Reset / Root:** Standard Android OS boundaries still apply.

See the [Emergency Uninstall section of README.md](../README.md#emergency-uninstall) for exact recovery steps.
