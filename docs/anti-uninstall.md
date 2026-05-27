# Anti-Uninstall Protection

Warden uses Android's **Device Administrator** API to prevent itself from being silently uninstalled, force-stopped, or having its data cleared — all common ways to bypass a blocker.

---

## Table of Contents

- [The Problem](#the-problem)
- [How Device Administrator Works](#how-device-administrator-works)
- [Warden's Implementation](#wardens-implementation)
- [Accessibility-Based Deactivation Guard](#accessibility-based-deactivation-guard)
- [Activation Grace Window](#activation-grace-window)
- [Limitations](#limitations)
- [Enabling & Disabling](#enabling--disabling)

---

## The Problem

Without protection, any blocker can be defeated in seconds:

1. Open `Settings → Apps → [App Name]`
2. Tap **Force Stop** — kills the running service immediately
3. Tap **Uninstall** — removes the app entirely

This is the most common way impulsive users circumvent focus apps. Warden closes this loophole at two layers.

---

## How Device Administrator Works

Android's Device Policy Manager allows apps registered as **Device Administrators** to gain special system privileges. One side effect of this registration is that **the app cannot be uninstalled** through normal means as long as Device Admin is active. Attempting to uninstall a Device Admin app prompts the user to deactivate admin first — adding a significant friction step.

Device Admin does **not** require root or any special manufacturer unlock. It is a standard Android API available from API 8+, used widely by MDM (Mobile Device Management) and parental control apps.

---

## Warden's Implementation

### Receiver

`WardenDeviceAdminReceiver` extends `DeviceAdminReceiver` and handles the activation/deactivation lifecycle:

```kotlin
class WardenDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Anti-Uninstall Protection Activated", Toast.LENGTH_SHORT).show()
    }
    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Anti-Uninstall Protection Deactivated", Toast.LENGTH_SHORT).show()
    }
}
```

### Policy Declaration

`res/xml/device_admin_rules.xml` declares the Device Admin policy. No elevated policies (e.g., password enforcement, remote wipe) are requested — Warden only needs Device Admin status for the uninstall-prevention side effect:

```xml
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <!-- No specific policies needed — registration alone prevents uninstallation -->
    </uses-policies>
</device-admin>
```

### Manifest Registration

```xml
<receiver
    android:name=".receivers.WardenDeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_rules" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

### Activation Flow

When the user enables Anti-Uninstall in the Warden settings:

1. `deviceAdminActivationRequestedAt` is set to `System.currentTimeMillis()` in DataStore
2. Warden launches the system's Device Admin consent screen via:
   ```kotlin
   Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
   ```
3. The user grants Device Admin in the system prompt
4. `WardenDeviceAdminReceiver.onEnabled()` fires — protection is now active

---

## Accessibility-Based Deactivation Guard

Making uninstallation require a Device Admin deactivation step is only half the protection. The user could still:
1. Open `Settings → Security → Device Administrators`
2. Tap Warden → Deactivate → Confirm
3. Uninstall normally

To block this flow, Warden's accessibility service monitors every screen for the deactivation UI:

```
Screen text contains "warden"
      AND
Screen class/text contains "device admin" or "deactivate"
      AND
Anti-uninstall is enabled
      AND
Not within the 3-minute activation grace window
      →
pressHome() immediately
```

The full check runs in `KeywordBlocker.checkIfUserGettingFreaky()` on every accessibility event, before the keyword scan. See [How It Works](how-it-works.md#self-protection-blocking-settings-access) for the full logic.

### Force Stop / Clear Data Guard

The same self-protection logic also blocks:
- `Settings → Apps → Warden → Force Stop`
- `Settings → Apps → Warden → Clear Data`
- `Settings → Apps → Warden → Uninstall` (even without Device Admin)

Any screen displaying "warden" alongside "force stop", "uninstall", or "clear data" triggers an immediate home press.

---

## Activation Grace Window

When Warden itself triggers the Device Admin activation prompt, the system shows a screen containing the word "Warden". Without a grace window, the self-protection logic would immediately close that screen.

To prevent this, a **3-minute grace window** is active from the moment activation is requested:

```kotlin
val isActivating = System.currentTimeMillis() - deviceAdminActivationRequestedAt < 180_000
if (isDeviceAdminScreen && antiUninstallEnabled && hasDeactivateAction && !isActivating) {
    pressHome()
}
```

During this window, Device Admin screens are not blocked. After 3 minutes, protection is fully in effect.

---

## Limitations

| Limitation | Detail |
|---|---|
| **Safe Mode bypass** | A user can boot into Safe Mode to temporarily disable Warden's accessibility service, allowing them to deactivate the Device Admin and uninstall. |
| **ADB bypass** | A user can run `adb shell settings delete secure enabled_accessibility_services` from a computer to kill the accessibility service, allowing them to deactivate the Device Admin. |
| **Factory reset** | A factory reset removes all apps including Warden. This is intentional behavior by Android design. |
| **Root** | A rooted device can uninstall any app regardless of Device Admin status. |

Warden is designed to stop impulsive in-the-moment bypasses — not to be a cryptographically unbreakable lock.

---

## Enabling & Disabling

**To enable:**
1. Open Warden → Keyword Blocker
2. Scroll to **Anti-Uninstall Protection**
3. Toggle on → grant Device Admin in the system prompt

**To disable normally (if you need to uninstall Warden):**
1. Open Warden → Keyword Blocker
2. Toggle Anti-Uninstall off → enter your password
3. Confirm Device Admin deactivation in the system prompt
4. Warden can now be uninstalled normally via Settings

**If you are locked out (forgot password or Settings is blocked):**

Use Safe Mode or ADB from a computer. Full step-by-step instructions are in the [Emergency Uninstall section of the README](../README.md#emergency-uninstall).

**Example ADB Emergency Removal Flow:**
```bash
# Step 1: Kill Accessibility Service to unblock Settings
adb shell settings delete secure enabled_accessibility_services

# Step 2: Manually deactivate Device Admin on the phone's Security settings

# Step 3: Uninstall
adb shell pm uninstall com.warden.app
```

