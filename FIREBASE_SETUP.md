# 🔥 Firebase Setup Guide

Follow these steps BEFORE building the app.

---

## Step 1 — Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add project** → name it "FocusSessions"
3. Enable Google Analytics (optional)

---

## Step 2 — Register Android App

1. In your Firebase project → **Add app** → Android
2. Package name: `com.focussessions.app`
3. App nickname: Focus Sessions
4. **SHA-1 fingerprint** (required for Google Sign-In):

```bash
# Debug keystore
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

5. Download `google-services.json`
6. Place it in: `app/google-services.json`

---

## Step 3 — Enable Authentication

1. Firebase Console → **Authentication** → Get started
2. Sign-in providers → **Google** → Enable
3. Project support email → select your email → Save
4. Copy the **Web Client ID** (looks like `123456789-abc.apps.googleusercontent.com`)

---

## Step 4 — Set Web Client ID

Open `app/build.gradle.kts` and replace:

```kotlin
buildConfigField("String", "GOOGLE_WEB_CLIENT_ID",
    "\"YOUR_WEB_CLIENT_ID_HERE.apps.googleusercontent.com\"")
```

with your actual Web Client ID.

---

## Step 5 — Enable Firestore

1. Firebase Console → **Firestore Database** → Create database
2. Choose **Production mode**
3. Select a region close to your users

---

## Step 6 — Deploy Security Rules

In Firebase Console → Firestore → Rules, paste the contents of `firestore.rules`:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null
                          && request.auth.uid == userId;
    }
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

Or deploy via CLI:
```bash
npm install -g firebase-tools
firebase login
firebase deploy --only firestore:rules
```

---

## Step 7 — Enable Crashlytics (optional)

1. Firebase Console → **Crashlytics** → Get started
2. Follow the setup wizard

---

## Step 8 — Sync & Run

```
Android Studio → File → Sync Project with Gradle Files
Build → Clean Project
Run ▶
```

---

## Firestore Data Structure

```
users/
  {uid}/
    profile/
      data → { displayName, email, photoUrl, totalFocusMinutes, streak, lastSyncTime }
    focus_sessions/
      {sessionId} → { startTimestamp, endTimestamp, focusDuration, ... }
    tasks/
      {taskId} → { title, isCompleted, createdAt, updatedAt }
    settings/
      preferences → { focusDurationMinutes, dailyGoalMinutes, ... }
    stopwatch_history/
      {id} → { duration, completedAt, ... }
```

---

## Offline Behavior

The app works **fully offline**:
- All data reads/writes go to Room first
- SyncQueue buffers any changes made offline
- WorkManager syncs automatically when internet returns
- Firestore's local cache (100MB) provides read access offline
