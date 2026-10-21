# EchoChat Android

Native Android client built with Kotlin, Jetpack Compose, Room and coroutines. It supports Persian (RTL) and English (LTR).

**Backend policy:** account, chat and social data use the self-hosted PHP/MySQL API. Firebase SDKs, Firebase authentication, Firestore synchronization and Google Sign-In are not part of this app.

## Current implementation

- Email/password registration and login through the PHP API.
- HTTPS server configuration in the app with an app-private stored session token.
- Public chat rooms, exact username lookup and member-protected private conversations.
- Room cache and retry of outgoing messages not yet acknowledged by the API.
- Message history refresh through polling while the app is open.
- Server-backed public-room creation and text social posts, likes, bookmarks, comments and 24-hour text stories.
- Explore feed with search and category filters; saved posts, profile/settings, theme and language preferences.
- Optional server-backed Echo AI. The private PHP server configuration supplies the AI-provider key; no provider key is embedded in the APK.

## PHP setup

Follow [../server/README.md](../server/README.md). The API URL entered in the app must point to the folder containing `index.php`, for example `https://yourdomain.com/echochat-api`. The `/health` route should report that the database is connected.

Import `../server/database/schema.sql` before testing. Register two accounts on separate devices or app installs to validate that public rooms and private conversations synchronize and that private data stays private.

To enable Echo AI, configure `ai_api_key`, `ai_endpoint` and `ai_model` in the private server `config.php`. Keep credentials off the Android device and out of Git.

## Build on Windows

Use Android Studio with JDK 21. Open this directory as the project, then run:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also builds the debug APK and uploads it as an artifact.

## Release readiness

This is an MVP, not a production-ready release. Password reset/email verification, login throttling, moderation and abuse controls, account deletion/privacy workflows, retention and monitoring, push notifications, WebSockets, end-to-end encryption, media uploads, delivery/read receipts, presence and load/security testing still need implementation. Posts and stories are text-only. Echo AI works only when a compatible provider is configured on the PHP server.

A release APK must be built and tested against the deployed API and signed with the owner's private release key. Never commit signing keys, session tokens, database credentials or provider secrets.
