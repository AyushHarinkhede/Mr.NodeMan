# <p align="center"><img src="./app_logo.png" alt="Mr.NodeMan Logo" width="96" height="96" style="border-radius: 24px; box-shadow: 0 10px 30px rgba(54, 223, 175, 0.35);"/></p>

<h1 align="center" style="color: #EEEEF5; font-weight: 800; letter-spacing: -0.03em;">
  Mr.NodeMan
</h1>

<p align="center" style="color: #74748A; font-size: 16px;">
  <strong>Next-Gen Work Ledger & Freelance Financial Operating System</strong><br/>
  Track Daily Attendance, Overtime, Salary Payouts, Client Billing & Smart Offline Automation
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20PWA-7C6FED?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Theme-Dark%20OLED%20Glassmorphism-07070A?style=for-the-badge&logo=color-palette&logoColor=2DD4A8" alt="Theme" />
  <img src="https://img.shields.io/badge/Engine-Offline--First%20Core-2DD4A8?style=for-the-badge" alt="Engine" />
  <img src="https://img.shields.io/badge/Build-Passing-36DFAF?style=for-the-badge" alt="Build" />
</p>

---

## 🎨 Design System & Visual Palette

Mr.NodeMan features a modern **OLED Obsidian Glass** aesthetic inspired by minimalist financial terminals and cyberpunk ergonomics:

| Color Token | Hex Code | Visual Meaning |
| :--- | :--- | :--- |
| **Deep Space Canvas** | `#07070A` | True deep-black OLED battery-saving backdrop |
| **Subtle Card Glass** | `#0E0E13` / `#131318` | High-definition elevated surfaces with soft ambient aura |
| **Neon Mint (Accent)** | `#2DD4A8` / `#36DFAF` | Financial positive, Present attendance, Success metrics |
| **Electric Violet** | `#7C6FED` | Core branding, primary actions, calendar highlights |
| **Warm Amber** | `#FFB930` / `#F59E0B` | Pending milestones, overtime tags, half-day indicators |
| **Soft Coral / Rose** | `#F87171` / `#EC4899` | Absentee alerts, Sick Leave empathy banners |

---

## ⚡ Key Highlights & Core Capabilities

### 1. 🏢 Dual Architecture: Freelance + Workplace Roster
* **Office & Shift Mode**: Complete worker ledger with check-in/out timestamps, automatic rate calculations, shift roster, half-day tracking, and overtime multipliers.
* **Freelance Client Ledger**: Billable hourly & fixed-rate projects, client dues ledger, invoice generation, and pending milestone alerts.
* **Dual Operation**: Seamlessly switch between or combine freelance billing and regular shift earnings in a single unified dashboard.

### 2. 📅 Ultra-Clean Attendance Calendar & Tools
* **Single-Line Header**: Dynamically formatted Month & Year inline display preventing uneven text drops.
* **Interactive Year & Month Pickers**: Quick popover selectors to switch years or jump across months in one tap.
* **Unified Tools Bar**: Quick access bar prioritizing **Calculator** and **Calendar** directly on home view.
* **Granular Status Classification**:
  * `P` — Present
  * `HD` — Half Day (with custom duration)
  * `PL` — Paid Leave
  * `SL` — Sick Leave
  * `WO` — Week Off
  * `HL` — Official Holiday
  * `A` — Unplanned Absent

---

## 🔔 Intelligent Notification Suite & Native Integration

Mr.NodeMan features a zero-battery-drain, offline-first Android native notification suite (`AlarmManager` + `BroadcastReceiver`):

### ☀️ Morning Shift Check-In (09:00 AM)
* Interactive notification shade with **3 direct action buttons**:
  * **`Present 🎉`**: Records Present with an energetic *"Yeepee! 🎉 Present Marked!"* celebration chime and crisp haptic feedback.
  * **`Absent ❌`**: Triggers a smart sub-menu notification prompt asking the absence reason.
  * **`Week Off 🌴`**: Immediately logs scheduled Week Off without opening the app.

### 📋 Interactive Absent Sub-Menu Flow
When selecting **Absent**, the notification updates in-place to offer specific leave reasons:
1. **`Sick Leave (SL) 💊`**
2. **`Paid Leave (PL) 🏖️`**
3. **`Absent (A) ❌`**

### 🩺 Personalized & Empathetic Sick Leave Health Care
Whenever Sick Leave (`SL`) is marked (either via notification or in-app buttons):
* Sends a **dedicated recovery notification** addressing the user by their **registered name** (*"Take Full Rest, [User Name]! 🩺💖"*).
* Utilizes a rotating empathy engine with **6 unique compassionate messages** encouraging hydration, stress-free rest, and medical care.

### 🎂 Milestones & Life Reminders
* **Advance & Day-Of Birthday Wishes**: Timely birthday greetings honoring the worker's date of birth (DOB).
* **Workplace Tenure Celebrations**: Exact 1-year work anniversary celebrations and 1st-of-the-month tenure milestones.
* **Stats Screen Sync**: Continuous work streak badge and tenure details displayed directly in Worker Statistics.

### 🍱 Machine Learning Lunch Break Sync
* **Interactive Lunch Reminders**: *"Haan, Kar Liya 🍱"* vs *"Abhi Nahi ⏳"*.
* **Time Learning Algorithm**: Automatically tracks habits and calculates a personalized moving average window for subsequent daily reminders.

---

## 🏗️ Project Architecture

```
Mr.NodeMan/
├── app/                                # Native Android Shell
│   ├── src/main/
│   │   ├── java/com/mrnodeman/app/
│   │   │   ├── MainActivity.java       # WebView bridge & High-refresh rate controller
│   │   │   ├── NotificationAlarmReceiver.java   # Offline alarm triggers & greetings
│   │   │   ├── NotificationActionReceiver.java  # 1-Tap interactive shade actions
│   │   │   ├── NotificationScheduler.java      # AlarmManager exact scheduling
│   │   │   └── BootReceiver.java       # Re-arm alarms upon device restart
│   │   ├── res/
│   │   │   ├── raw/nodeman_notification.wav     # Custom signature audio chime
│   │   │   └── drawable/               # High-contrast action icons
│   │   └── assets/index.html           # Bundled standalone offline app
├── index.html                          # Core PWA application codebase
├── app_logo.png                        # Official branding logo
├── manifest.json                       # Progressive Web App configuration
└── build.gradle                        # Android build scripts
```

---

## 🚀 Building & Running

### Requirements
* **Android SDK** (API Level 24+ supported, targeted to Android 14 / API 34)
* **Java Development Kit (JDK 17+)**
* Modern Web Browser (for PWA mode)

### Android Compilation
```powershell
# Clone the repository
git clone https://github.com/AyushHarinkhede/Mr.NodeMan.git

# Enter workspace
cd Mr.NodeMan

# Build debug APK
./gradlew.bat assembleDebug
```
The output APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Privacy & Offline Assurance
* **100% Offline Capable**: Your financial earnings, client agreements, and attendance records stay encrypted in native local device storage.
* **Incognito Support**: Built-in incognito mode prevents clipboard leakage, denies media queries, and leaves zero forensic trace.

---

<p align="center" style="color: #74748A; font-size: 13px;">
  Crafted with care by <strong>Ayush Harinkhede</strong> for modern workers, freelancers, and craftsmen everywhere. ⚡
</p>
