# 🎯 Focus Sessions — Android App

A feature-complete, modern Android application built with **Kotlin 2.2**, **Jetpack Compose**, **Hilt**, **Room**, **DataStore**, **Media3/ExoPlayer**, **WorkManager**, and **Firebase (Auth & Cloud Firestore Sync)**.

---

## 🏗️ Architecture Overview

```text
com.focussessions.app/
├── auth/
│   ├── AuthManager.kt        → Credential Manager & Google Sign-In with Firebase Auth
│   └── UserProfile.kt        → User profile state & Firestore mapping
│
├── calendar/
│   ├── AnalyticsEngine.kt    → Daily, weekly, and monthly productivity calculations
│   ├── CalendarManager.kt   → Calendar navigation, month grids, and heatmap data merging
│   └── ProductivityCalculator.kt → Automated productivity insights and streak calculator
│
├── connectivity/
│   └── NetworkMonitor.kt     → Real-time network state monitoring
│
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── dao/          → TaskDao, FocusSessionDao, FocusHistoryDao, DailyProgressDao, CalendarDao, AnalyticsDao
│   │   │   ├── entities/     → TaskEntity, FocusSessionEntity, FocusHistoryEntity, DailyProgressEntity, CalendarDayEntity
│   │   │   └── AppDatabase.kt → Room DB instance
│   │   └── datastore/
│   │       └── UserPreferencesDataStore.kt
│   └── repository/
│       └── Repositories.kt   → Repositories backing local Room DB and DataStore
│
├── domain/
│   └── model/
│       └── Models.kt         → Domain data models
│
├── service/
│   ├── TimerService.kt       → Foreground Service (lifecycle-safe focus countdown)
│   └── MusicService.kt       → Media3 MediaSessionService + local music & system ringtone player
│
├── stopwatch/
│   ├── StopwatchEngine.kt    → Drift-free 16ms precision stopwatch
│   ├── StopwatchService.kt   → Foreground service with high-priority alarm notification
│   └── StopwatchState.kt     → Stopwatch UI state model
│
├── sync/
│   ├── ConflictResolver.kt   → Last-Write-Wins (LWW) conflict resolution algorithm
│   ├── FirestoreSyncService.kt → Cloud Firestore read/write operations
│   ├── SyncManager.kt        → Offline sync queue, auto-sync triggers, and full sync
│   ├── SyncQueue.kt          → Room `sync_queue` entity & DAO
│   └── SyncWorker.kt         → WorkManager periodic background synchronization
│
├── timer/
│   ├── CycleState.kt         → Real-time focus cycle progress & time calculations
│   ├── SessionManager.kt     → Multi-cycle Pomodoro state machine with minute-by-minute progress
│   └── TimerEngine.kt        → Drift-free SystemClock countdown engine
│
├── ui/
│   ├── components/           → Modular Compose components (Timer, AppLogo, Tasks, Music, Calendar)
│   ├── screens/              → Navigation screens (Focus, Analytics, Calendar, Profile, Login, Settings)
│   └── theme/                → App typography, colors, and Material 3 theme
│
└── viewmodel/                → FocusViewModel, AuthViewModel, AnalyticsViewModel, CalendarViewModel, StopwatchViewModel
```

---

## 🌟 Key Features

### ⏱️ Focus Timer & Cycle Manager
- **Drift-Free Timer:** Uses `SystemClock.elapsedRealtime` to prevent timer drift during long runs or sleep states.
- **Custom Arc Ring:** Fluid Jetpack Compose `Canvas` ring with pulse glow, tick marks, and animated sweep angle.
- **Session Phases:** Automatically cycles between `FOCUS`, `SHORT_BREAK`, and `LONG_BREAK`.
- **Live Minute-by-Minute Tracking:** Focus time and break time update live every minute across all app features (`FocusScreen`, `AnalyticsScreen`, `CalendarScreen`, `ProfileScreen`) while a session is running.
- **Foreground Service:** Active countdown runs inside `TimerService` with a sticky notification and WakeLock protection.

### 🟩 365-Day Coding Platform Style Streak Heatmap
- **Contribution Heatmap Grid:** A 53-week × 7-day contribution grid (GitHub/LeetCode style) displaying focused and non-focused days across the last 365 days.
- **5-Level Color Scale:**
  - `0m` (Non-focused day): Dark slate square (`#161B22`) with a crisp border (`#21262D`).
  - `1m - 20m`: Dark forest green (`#0E4429`).
  - `21m - 45m`: Medium emerald green (`#006D32`).
  - `46m - 90m`: Bright mint green (`#26A641`).
  - `91m+`: Vibrant glowing neon green (`#39D353`).
- **Interactive Tapping:** Tapping any square on the heatmap highlights the cell with a white outline and displays exact session list and total focus time for that date.
- **Cloud & Local Storage:** Heatmap metrics (`calendar_days`) persist in Room DB and sync live to Firebase Firestore.

### 🔔 System Alarm & Ringtone Completion Music
- **Ringtone & Alarm Integration:** Supports device system ringtones, alarms, and local MP3 tracks via `RingtoneManager` and `Media3/ExoPlayer`.
- **Default Sound Fallback:** Built-in "Default Alarm Ring ⏰" ensures the timer always rings even on fresh devices without local music files.
- **High-Priority Alarm Notifications:** Custom notification channels (`stopwatch_complete_channel` / `focus_timer_channel`) with alarm audio attributes and vibration patterns so the device rings loudly even when screen is locked or app is in background.

### 🔄 Multi-Device Cloud Sync & Offline-First Storage
- **Offline-First:** All user data (sessions, tasks, daily progress, calendar days, preferences) is saved instantly to local Room database tables.
- **Firebase Firestore Sync:** Automatically queues offline changes in `SyncQueue` and uploads to Firebase Cloud Firestore (`users/{uid}/...`) when connected.
- **Multi-Device Synchronization:** Sign in with Google on multiple devices to automatically merge and synchronize focus history, streaks, calendar heatmaps, and tasks via Last-Write-Wins (`ConflictResolver`).
- **Automatic Sync on Launch:** Performs a `fullSync()` on login or app launch to fetch and merge cloud data seamlessly.

### 👤 Live Profile Metrics
- **Dynamic Stats Display:** Profile screen computes and displays live total focus time ("2h 15m") and active streak ("3d") directly from Room DB and `AnalyticsEngine`.
- **Firebase Profile Sync:** Profile stats automatically sync to Firestore profile documents (`users/{uid}/profile/data`).

### 🎨 Custom App Branding & Vector Logo
- **Adaptive Icon:** Adaptive launcher icon (`ic_launcher` / `ic_launcher_round`) with midnight blue squircle background, cyan-to-purple gradient arc, indicator node, and clock hands.
- **Compose Logo Components:** Composable vectors (`AppLogoIcon`, `AppLogoHeader`) with gradient `"time"` text typography.

---

## 📦 Tech Stack & Dependencies

| Component | Library / Version | Purpose |
|---|---|---|
| **Language** | Kotlin `2.2.10` | Core language |
| **UI Toolkit** | Jetpack Compose BOM `2024.08.00` | Declarative UI |
| **Design System** | Material 3 | Modern Android styling |
| **Dependency Injection** | Hilt `2.60.1` | App-wide dependency injection |
| **Local Database** | Room `2.8.5` | SQLite persistence with KSP |
| **Preferences** | DataStore Preferences `1.1.1` | Key-value settings persistence |
| **Background Work** | WorkManager `2.9.1` | Periodic cloud synchronization |
| **Media Player** | Media3 / ExoPlayer `1.4.0` | Audio & ringtone playback |
| **Authentication** | Credential Manager `1.3.0` + Firebase Auth | One-Tap Google Sign-In |
| **Cloud Storage** | Firebase Firestore `33.1.2` | Multi-device cloud sync |
| **Testing** | JUnit4, Kotlinx Coroutines Test, Room Test, Robolectric | Comprehensive unit testing suite |

---

## 🛠️ Build & Setup Instructions

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer
- Android SDK 35 (`compileSdk` / `targetSdk`)
- JDK 17
- Device or Emulator running API 26 (Android 8.0) or higher

### Steps

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/vivek-1728/FocusSessions.git
   cd FocusSessions
   ```

2. **Firebase Setup:**
   - Download your `google-services.json` file from the [Firebase Console](https://console.firebase.google.com/).
   - Place `google-services.json` inside the `app/` folder (`app/google-services.json`).
   - Register your debug SHA-1 fingerprint in Firebase Console (**Project Settings** → **Your Apps** → **Android app**):
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

3. **Build the Debug APK:**
   ```bash
   ./gradlew :app:assembleDebug
   ```
   *Output path:* `app/build/outputs/apk/debug/app-debug.apk`

4. **Run Local Unit Tests:**
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

---

## 🧪 Testing

The codebase includes 107+ unit tests covering data serialization, timer engines, analytics calculations, and conflict resolution:

```bash
./gradlew :app:testDebugUnitTest
```

Key test coverage includes:
- `ConflictResolverTest`: Validates Last-Write-Wins resolution logic and map merging.
- `CycleStateTest`: Validates timer progress fractions and display time formatting.
- `ModelsAndEntitiesTest`: Validates Firestore map conversions and Room entity defaults.
- `StopwatchStateTest`: Validates millisecond-precision stopwatch display logic.
- `AnalyticsEngineTest`: Validates daily/weekly/monthly productivity analytics algorithms.
- `TwoDeviceSyncTest`: Validates cross-device cloud sync convergence for sessions, tasks, and streaks.

---

## 📄 License

MIT License — feel free to use and adapt for personal or commercial projects.
