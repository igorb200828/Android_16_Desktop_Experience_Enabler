# Android 16 Desktop Experience Enabler

## What does it do?

Android 16 Desktop Experience Enabler is a module for LSPosed that enables the "Enable desktop experience features toggle" in developer options that enables Desktop Mode on External Display. It works ONLY on Android 16 QPR1 Beta 1, with devices that support external displays output.

## Target Applications

This module modifies core system behavior and thus primarily targets system components involved in window and display management. The changes will affect how the entire system handles desktop mode. Key components influenced include:

*   **System UI:** `com.android.systemui` (for taskbar, window decorations, etc.)
*   **Android Framework:** `android` (for underlying window and display logic)
*   **Settings:** `com.android.settings` (for any developer options or settings related to desktop mode)
*   **Shell:** `com.android.shell`
*   **Launchers:** e.g., `com.google.android.apps.nexuslauncher` (for how they interact with different display states)

**Important:** 

This module is intended to modify system-wide behavior. The Desktop Mode on external display has many bugs but once it is properly set up the Desktop Mode should work as expected.


## How to install

### Prerequisites

To use this module you must have one of the following (latest versions):
- [Magisk](https://github.com/topjohnwu/Magisk) with Zygisk enabled
    - IMPORTANT: DO NOT add apps that you want to spoof to Magisk's denyList as that will break the module.
- [KernelSU](https://github.com/tiann/KernelSU) with [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext) module installed
- [APatch](https://github.com/bmax121/APatch) with [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext) module installed

You must also have [LSPosed](https://github.com/mywalkb/LSPosed_mod) installed

### Installation

- Download the latest APK of Android 16 Desktop Experience Enabler Theme from the [releases section](https://github.com/igorb200828/) and install it like any normal APK.
- Now open the LSPosed Manager and go to "Modules".
- "Android 16 Desktop Experience Enabler" should now appear in that list.
- Click on "Android 16 Desktop Experience Enabler" and enable the module by flipping the switch at the top that says "Enable module".
- The recommended apps will be already selected.
- Force close the settings app, open the Developer Options and toggle Enable desktop experience features under Window Management. Click Reboot Now and after the reboot when you plug in the external display the new desktop mode should be active.
