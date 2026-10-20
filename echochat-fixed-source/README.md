# EchoChat

EchoChat is a native Android app built with Kotlin, Jetpack Compose, Material 3, Room, coroutines/Flow, Firebase Authentication and Cloud Firestore. The interface supports Persian (RTL) and English (LTR).

## Implemented in this branch

- Email/password registration with a unique username, email/password sign-in, Google sign-in, and password-reset email flow.
- An authentication gate: without Firebase configuration, the app shows setup instructions rather than presenting local demo data as a real cloud account.
- Room-backed local caching for messages and channels.
- Shared real-time public-room messages under `channels/{channelId}/messages/{messageId}`.
- Username-based account lookup through exact handle-document reads; public profile records do not publish email addresses.
- Private conversations under `conversations/{conversationId}`, with a two-member list and member-only Firestore rules for conversation metadata and messages.
- Message authorship bound to the current Firebase UID; remote message snapshots merge into Room without re-upload loops.
- Account-switch cleanup for cached private conversations and local social content, to avoid showing one account's local content to another account on the same device.
- Removal of fabricated sample users/messages/posts/stories from new installs and cleanup migrations for fixed records from previous demo builds.
- Persian/English UI, theme preferences, local explore/feed/profile UI, and Firebase AI integration hooks.

## Important scope boundary

**This is a cloud-messaging MVP, not yet a production-ready public release.** Public-room and private-conversation messages are designed to sync through Firestore, but this environment cannot verify them against your Firebase project or two real accounts/devices. Posts, comments, stories, and profile edits are still local to the device and are not shared between accounts.

Still unfinished: delivery/read receipts, online presence, cross-user typing indicators, image/file uploads, push notifications, abuse/spam controls, message pagination and retention, end-to-end encryption, privacy policy and user data export/deletion workflows, and automated Firebase Emulator integration tests. Do not advertise these as available features.

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
4. Create Cloud Firestore and publish the repository's root `firestore.rules`.
5. Configure App Check for development and release.

See [Firebase setup details](app/FIREBASE_SETUP.md). Never commit signing keystores, passwords, service-account keys, debug tokens, or other private credentials.

## CI artifacts

The GitHub Actions workflow runs unit tests, lint and a debug APK build and uploads an `EchoChat-debug-apk` artifact. A debug APK is for testing; a public store release additionally needs an owner-controlled signing key and full security, privacy, and cross-device tests.
