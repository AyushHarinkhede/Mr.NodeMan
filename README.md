<p align="center">
  <br/>
  <img src="./app_logo.png" alt="Mr.NodeMan Logo" width="160" height="160" style="border-radius: 36px; box-shadow: 0 16px 40px rgba(124, 111, 237, 0.35), 0 0 50px rgba(45, 212, 168, 0.25); border: 2px solid rgba(124, 111, 237, 0.4); display: block; margin: 0 auto;" />
</p>

<h1 align="center" style="font-size: 38px; font-weight: 900; letter-spacing: -0.04em; color: #EEEEF5; margin-top: 18px; margin-bottom: 8px;">
  Mr.NodeMan
</h1>

<p align="center" style="font-size: 16px; line-height: 1.6; max-width: 680px; margin: 0 auto 20px auto; color: #A0A0B8;">
  <strong>Financial Operating System & Work Ledger</strong><br/>
  Seamlessly unified for <strong>Daily Shift Workers</strong>, <strong>Freelancers</strong>, and <strong>Enterprise Contractors</strong>.
</p>

<!-- Documentation release notes: Production v3.2.0 — Vector SVG iconography & adaptive habit engine -->
<p align="center">
  <a href="https://github.com/AyushHarinkhede/Mr.NodeMan"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20PWA-7C6FED?style=for-the-badge&logo=android&logoColor=white" alt="Platform" /></a>
  <a href="https://github.com/AyushHarinkhede/Mr.NodeMan"><img src="https://img.shields.io/badge/Theme-Emerald%20%26%20Violet%20OLED-07070A?style=for-the-badge&logo=palette&logoColor=2DD4A8" alt="Theme" /></a>
  <a href="https://github.com/AyushHarinkhede/Mr.NodeMan"><img src="https://img.shields.io/badge/Storage-100%25%20Offline%20Vault-2DD4A8?style=for-the-badge&logo=shield&logoColor=black" alt="Storage" /></a>
  <a href="https://github.com/AyushHarinkhede/Mr.NodeMan/releases"><img src="https://img.shields.io/badge/Build-v3.2.0%20Passing-36DFAF?style=for-the-badge&logo=githubactions&logoColor=white" alt="Build" /></a>
</p>

<br/>

---

### 🎨 Dark OLED & Glassmorphism Design System

Mr.NodeMan is crafted around high-contrast OLED black aesthetics, precision financial telemetry, and subtle frosted accents:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  CORE DESIGN PALETTE                                                        │
├──────────────────────┬─────────────┬────────────────────────────────────────┤
│  Token               │  Hex Code   │  Role & Semantic Function              │
├──────────────────────┼─────────────┼────────────────────────────────────────┤
│  Deep OLED Canvas    │  #07070A    │  Zero-power black substrate            │
│  Surface Elevated    │  #0E0E13    │  Glassmorphic card container           │
│  Surface Hover       │  #16161E    │  Interactive controls & buttons        │
│  Accent Emerald      │  #2DD4A8    │  Net Earnings, Payouts, Present (P)    │
│  Electric Violet     │  #7C6FED    │  Primary branding, Sliders & Highlights│
│  Amber Warmth        │  #FFB930    │  Overtime (OT), Pending Dues, Half-Day │
│  Crimson / Coral     │  #F87171    │  Deductions, Sunday badges, Absent (A) │
│  Pure Text Slate     │  #EEEEF5    │  Primary high-contrast legibility      │
└──────────────────────┴─────────────┴────────────────────────────────────────┘
```

---

### ⚡ Unified Workspace Tri-Mode Architecture

Users can switch perspective dynamically across three distinct operational modes directly from the Home bar or More screen:

| Mode | Target User | Key Capabilities Included |
| :--- | :--- | :--- |
| **Hybrid** | Dual Earners & Multi-income Pros | Combined ledger uniting client invoices, shift work, overtime payouts, and unified cash flow analysis. |
| **Shift** | Daily Employees & Shift Workers | Punch-in/out roster, attendance calendar (P/HD/PL/SL/WO/HL/A), OT multipliers (1.5x/2.0x), PF calculations, and automatic salary slip slips. |
| **Gig** | Freelancers & Contractors | Hourly billable time tracking, client management directory, GST invoicing, milestone badges, and pending payment tracking. |

---

### 📅 Advanced Attendance Calendar & Roster Suite

* **Smart Sunday Distinction**: Sundays are cleanly accented in red while Saturdays and weekdays remain in standard contrast.
* **Instant Month / Year Pickers**: Tap directly on the header to jump to any year or month in 1 click.
* **Streamlined Metric Cards**: Live tracking of **Present Days**, **Half-Days & Leaves**, **Overtime Hours**, and **Estimated Payout**.
* **Swipe-Driven Navigation**: Smooth horizontal gestures for rapid month-by-month navigation.
* **Granular Attendance Codes**:
  * `P` — Present (Full Shift)
  * `HD` — Half Day (Logged hours & rate)
  * `PL` / `SL` / `CL` — Paid, Sick & Casual Leaves
  * `WO` / `HL` — Week Off & Official Holidays
  * `A` — Absent

---

### ☁️ Privacy-First Storage & Real Google Cloud Sync

* **100% Offline by Default**: Financial entries, wages, and profiles reside in private local sandbox storage.
* **Integrated Cloud Vault**: Directly accessible inside the Worker ID Profile card. Back up and restore anytime via real Google Identity Services (OAuth 2.0) & Google Drive v3 REST API.
* **Selective JSON Backups**: Choose whether to export **Hybrid (Full Workspace)**, **Shift (Worker Only)**, or **Gig (Freelancer Only)** snapshots with custom filenames and timestamps.
* **CSV & Formatted PDF Exports**: Export client statements or printable financial invoices on demand.

---

### 🛠️ Native Android Architecture

```
Mr.NodeMan/
├── app/                                       # Native Android Studio Application Shell
│   ├── src/main/
│   │   ├── java/com/mrnodeman/app/
│   │   │   ├── MainActivity.java              # WebView acceleration, notch insets & bridges
│   │   │   ├── NotificationAlarmReceiver.java # Offline exact alarm broadcaster
│   │   │   ├── NotificationActionReceiver.java# Interactive shade tap handlers
│   │   │   ├── NotificationScheduler.java     # Android AlarmManager exact scheduler
│   │   │   └── BootReceiver.java              # Auto-rearms alarms upon device boot
│   │   ├── res/
│   │   │   ├── raw/nodeman_notification.wav   # Signature 528Hz audio chime
│   │   │   └── drawable/                      # Optimized vector resources
│   │   └── assets/index.html                  # Bundled offline core application
├── index.html                                 # Core PWA application engine
├── app_logo.png                               # Official branding asset
├── manifest.json                              # Progressive Web App manifest
└── build.gradle                               # Gradle build configuration
```

---

### 🚀 Quick Start & Build Instructions

#### Prerequisites
- Android Studio / Android SDK (API Level 24+ supported, targeting Android 14 / API 34)
- Java Development Kit (JDK 17+)
- Modern browser (for PWA mode)

#### Build APK
```powershell
# Clone the repository
git clone https://github.com/AyushHarinkhede/Mr.NodeMan.git

# Navigate to project root
cd Mr.NodeMan

# Compile and package debug APK
.\gradlew.bat assembleDebug
```
The output APK is generated at `app/build/outputs/apk/debug/app-debug.apk` and mirrored to root `app-debug.apk`.

---

<p align="center">
  <br/>
  Designed &amp; Engineered with precision by <strong>Ayush Harinkhede</strong>.<br/>
  <sub>Mr.NodeMan • Next-Gen Work Ledger &amp; Freelance Operating System</sub>
</p>
