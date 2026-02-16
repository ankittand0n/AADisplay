# AADisplay

[![AADisplay](https://img.shields.io/badge/AADisplay-Project-blue?logo=github)](https://github.com/ankittand0n/AADisplay)
[![GitHub Release](https://img.shields.io/github/v/release/ankittand0n/AADisplay)](https://github.com/ankittand0n/AADisplay/releases)
![Xposed Module](https://img.shields.io/badge/Xposed-Module-blue)
[![License](https://img.shields.io/github/license/ankittand0n/AADisplay)](https://github.com/ankittand0n/AADisplay/blob/main/LICENSE)
![Android SDK min 31](https://img.shields.io/badge/Android%20SDK-%3E%3D%2031-brightgreen?logo=android)
![Android SDK target 36](https://img.shields.io/badge/Android%20SDK-target%2036-brightgreen?logo=android)

Run any Android app on your car's head unit via Android Auto. Uses a VirtualDisplay-based approach to mirror a full Android experience onto the AA projection screen.

Requires Android 12+ (API 31+) and [LSPosed](https://github.com/LSPosed/LSPosed). Some ROMs may have compatibility issues — know how to recover your device before proceeding.

> **Fork of [Nitsuya/AADisplay](https://github.com/Nitsuya/AADisplay)** with bug fixes, DPI auto-calculation, power button interception, split-screen toggle, floating controller toggle, and other improvements.

-----

## Features
- **Virtual Display** — Creates a secondary Android display rendered on your car's head unit via Android Auto
- **Full App Support** — Run any installed app (navigation, music, messaging, etc.) on the car screen
- **Custom Facet Bar** — Back, Home, and Recent Tasks buttons on the AA sidebar
- **Split Screen Toggle** — Choose between full takeover mode or AA's native widget/split-screen layout
- **Floating Controller** — On-screen overlay on your phone for mirror view, power toggle, and display management
- **DPI Auto-Calculation** — Automatically determines optimal DPI based on display resolution
- **Power Button Interception** — Short-press power button toggles virtual display on/off instead of locking phone
- **Corner Radius Control** — Force square corners on projection windows for full-screen apps
- **Steering Wheel Buttons** — Intercepts media/voice buttons for navigation control
- **Phenotype Flag Override** — Inject custom AA configuration properties
- **Root Shell Commands** — Execute custom commands on virtual display creation/destruction
- **Auto-Launch** — Automatically opens configured app when AA connects
- **Recent Tasks** — View and switch between apps running on the virtual display
- **Display Mirroring** — Mirror the virtual display content to your phone screen with touch pass-through
- **Signature Bypass** — Automatically bypasses AA's install source verification

## Setup
1. Install [LSPosed](https://github.com/LSPosed/LSPosed) (requires Magisk or KernelSU)
2. Enable the AADisplay module in LSPosed, checking **System Framework** and **Android Auto** scopes
3. Install a launcher app for the virtual display (e.g., [Square Home](https://play.google.com/store/apps/details?id=com.ss.squarehome2)) and set its package name in module settings
4. Optionally configure DPI, auto-open package, and other settings
5. Connect to Android Auto — the virtual display will launch automatically

## Settings
| Setting | Description |
|---------|-------------|
| **Auto Open** | Automatically launch the configured app when AA connects |
| **Launcher Package** | App to launch on virtual display creation (e.g., navigation app) |
| **Home Package** | Launcher for the Home button (must differ from your phone's launcher) |
| **AA DPI** | Override DPI for AA rendering (0 = auto) |
| **Virtual Display DPI** | Override DPI for the virtual display (0 = auto-calculated) |
| **Delay Destroy Time** | Seconds to keep virtual display alive after AA disconnects |
| **Screen Off Replace Lock** | Power button turns off display instead of locking phone |
| **Prevent AA Split Screen** | Force full-screen takeover (vertical rail layout) |
| **Show Floating Controller** | Show/hide the floating overlay buttons on your phone |
| **Force Right Angle** | Force square corners on AA projection windows |
| **IME Display Policy** | Show keyboard on virtual display or main screen |
| **Voice Assistant** | Custom shell command for voice assistant activation |
| **Pre/Post Shell Commands** | Shell commands to run before/after virtual display lifecycle |
| **AA Properties** | Override Android Auto Phenotype flags |
| **GMS Car Properties** | Override GMS Car content provider flags |

## Disclaimer
- Use this module at your own risk, including but not limited to device damage or driving accidents.
- The developer is not responsible for any derivative projects.
- This project is open source — Issues and PRs are welcome, but do not submit features for illegal purposes.
- The developer may **stop updating** or **remove the project** at any time.

## Acknowledgements

### Original Project
[Nitsuya/AADisplay](https://github.com/Nitsuya/AADisplay) — Original author and creator

### Libraries
[AOSP](https://source.android.com/) · [YAMF](https://github.com/duzhaokun123/YAMF) · [DexKit](https://github.com/LuckyPray/DexKit) · [Hide-My-Applist](https://github.com/Dr-TSNG/Hide-My-Applist) · [HiddenApi](https://github.com/RikkaW/HiddenApi) · [LSPosed](https://github.com/LSPosed/LSPosed) · [Xposed](https://forum.xda-developers.com/xposed) · [Material](https://material.io/) · [QAuxiliary](https://github.com/cinit/QAuxiliary) · [ViewBindingUtil](https://github.com/matsudamper/ViewBindingUtil)

