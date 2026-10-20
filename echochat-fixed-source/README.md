# EchoChat

EchoChat is a native Android app built with Kotlin, Jetpack Compose, Material 3, Room, coroutines/Flow, Firebase Authentication and Cloud Firestore. The interface supports Persian (RTL) and English (LTR).

## Implemented in this branch

- Email/password registration and sign-in, Google sign-in, and password-reset email flow.
- An authentication gate: the app does not present the local demo experience as a real cloud account when Firebase is missing.
- Room-backed local caching for messages and channels.
- Shared real-time **public room** channel metadata and messages through top-level Firestore `channels/{channelId}/messages/{messageId}` documents.
- Message authorship bound to the current Firebase UID when sending.
- Remote message snapshots replace stale Room copies; outgoing/incoming presentation is computed per signed-in account.
- Removal of fabricated sample users, messages, posts, and stories from new installs, plus migration cleanup for the previously shipped fixed demo records.
- Persian/English UI, theme preferences, profile UI, local explore/feed UI, local Room data persistence, and Firebase AI integration hooks.

## Important scope boundary

**This branch is an improved cloud-chat MVP, not a complete production messenger yet.** Only public rooms and their messages are synchronized through Firestore. Direct messages are not yet backed by membership-protected cloud conversations. Posts, comments, stories, and profile edits are still local to the device. Delivery/read receipts, online presence, typing state across users, file uploads, push notifications, abuse controls, privacy/export/delete workflows, and end-to-end encryption are not implemented.

Do not advertise those unfinished features as live server functionality. Public-room messages are visible to all authenticated users. The app requires a real Firebase project to pass the login gate.

## Requirements

- Android Studio and a compatible Android SDK.
- JDK 21.
- Network access to download Gradle and Maven dependencies.
- A Firebase project configured as described in [FIREBASE_SETUP.md](app/FIREBASE_SETUP.md).

## Build on Windows

From the `echochat-fixed-source` folder:

```bat
gradlew.bat testDebugUnitTest
gradlew.bat lintDebug
gradlew.bat assembleDebug
```

The debug APK is created at:

```
app/build/outputs/apk/debug/app-debug.apk
```

On macOS/Linux, run the same tasks through `./gradlew`.

## Firebase quick setup

1. Register Android application ID `com.aistudio.echochat.vpkzqr`.
2. Add `google-services.json` to `app/google-services.json`.
3. Enable Email/Password and Google providers in Firebase Authentication.
4. Create Cloud Firestore and publish the rules in the repository's root `firestore.rules`.
5. Configure App Check for development and release.

See [Firebase setup details](app/FIREBASE_SETUP.md). Never commit signing keystores, passwords, service-account keys, debug tokens, or other private credentials.

## CI artifacts

The GitHub Actions workflow checks the project and uploads an `EchoChat-debug-apk` artifact for configured push/PR runs. A debug APK is for testing; a public store release additionally needs an owner-controlled signing key and complete security, privacy, and cross-device tests.
