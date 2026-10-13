# 🎯 Focus Sessions — Android App

A feature-complete, modern Android application built with **Kotlin 2.2**, **Jetpack Compose**, **Hilt**, **Room**, **DataStore**, **Media3/ExoPlayer**, **WorkManager**, and **Firebase (Auth & Firestore Sync)**.

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
│   ├── CalendarManager.kt   → Calendar navigation and date selection
│   └── ProductivityCalculator.kt → Automated productivity insights and streak calculator
│
├── connectivity/
│   └── NetworkMonitor.kt     → Real-time network state monitoring
│
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── dao/          → TaskDao, FocusSessionDao, FocusHistoryDao, AnalyticsDao
│   │   │   ├── entities/     → TaskEntity, FocusSessionEntity, FocusHistoryEntity, CalendarDayEntity
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
│   └── MusicService.kt       → Media3 MediaSessionService + local music player
│
├── stopwatch/
│   ├── StopwatchEngine.kt    → Drift-free 16ms precision stopwatch
│   ├── StopwatchService.kt   → Foreground service for continuous stopwatch operation
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
│   ├── SessionManager.kt     → Multi-cycle Pomodoro state machine
│   └── TimerEngine.kt        → Drift-free SystemClock countdown engine
│
├── ui/
│   ├── components/           → Modular Compose components (Timer, Tasks, Music, Calendar)
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
- **Foreground Service:** Active countdown runs inside `TimerService` with a sticky notification and WakeLock protection.

### 🔄 Multi-Device Cloud Sync & Offline-First Storage
- **Offline-First:** All user data (sessions, tasks, preferences, stopwatch records) is saved instantly to local Room database tables.
- **Firebase Firestore Sync:** Automatically queues offline changes in `SyncQueue` and uploads to Firebase Cloud Firestore (`users/{uid}/...`) when connected.
- **Multi-Device Synchronization:** Sign in with Google on multiple devices to automatically merge and synchronize focus history, streaks, and tasks via Last-Write-Wins (`ConflictResolver`).
- **Periodic Background Sync:** `WorkManager` runs background synchronization every 15 minutes to ensure consistency.

### 🔑 Google Authentication
- **Credential Manager Integration:** Seamless One-Tap Google Sign-In using Android `CredentialManager` API.
- **Legacy Fallback:** Automatically falls back to Google Sign-In bottom sheet if Credential Manager is unavailable.

### 📊 Analytics & Calendar Insights
- **Productivity Breakdown:** Analyzes focus habits across daily, weekly, and monthly views.
- **Streak & Consistency Counter:** Calculates active focus day streaks and monthly consistency percentages.
- **Time-of-Day Insights:** Identifies most productive days of the week and peak focus hours.

### 🎵 Media3 Local Music Player
- **Local Audio Scan:** Scans device storage using `MediaStore` for local audio tracks.
- **ExoPlayer Playback:** Integrated `Media3` player with mini-player controls, track selection dialog, and auto-play during focus sessions.

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
| **Media Player** | Media3 / ExoPlayer `1.4.0` | Audio playback |
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
   git clone https://github.com/your-username/FocusSessions.git
   cd FocusSessions
   ```

2. **Firebase Setup:**
   - Download your `google-services.json` file from the [Firebase Console](https://console.firebase.google.com/).
   - Place `google-services.json` inside the `app/` folder (`app/google-services.json`).
   - Register your debug SHA-1 fingerprint in Firebase Console (**Project Settings** → **Your Apps** → **Android app**):
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

3. **Build the Project:**
   ```bash
   ./gradlew :app:assembleDebug
   ```

4. **Run Local Unit Tests:**
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

---

## 🧪 Testing

The codebase includes unit tests covering data serialization, timer engines, analytics calculations, and conflict resolution:

```bash
./gradlew :app:testDebugUnitTest
```

Key test coverage includes:
- `ConflictResolverTest`: Validates Last-Write-Wins resolution logic and map merging.
- `CycleStateTest`: Validates timer progress fractions and display time formatting.
- `ModelsAndEntitiesTest`: Validates Firestore map conversions and Room entity defaults.
- `StopwatchStateTest`: Validates millisecond-precision stopwatch display logic.
- `AnalyticsEngineTest`: Validates daily/weekly/monthly productivity analytics algorithms.

---

## 📄 License

MIT License — feel free to use and adapt for personal or commercial projects.
