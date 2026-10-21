# EchoChat Android

Native Android application built with Kotlin, Jetpack Compose, Room, coroutines and a self-hosted PHP/MySQL REST API. Persian RTL and English LTR are supported.

## Implemented

- Email/password registration and login through the PHP API.
- HTTPS server configuration stored in app-private preferences.
- Public chat rooms, username lookup, private two-person conversations and message history.
- Local Room cache, retry of queued outgoing messages and polling-based sync while the app is open.
- Server-backed public-room creation.
- Server-synced text posts, likes, bookmarks, comments and 24-hour text stories.
- Explore feed, profile/settings, language and theme controls.
- No Firebase SDK, Firebase services, Google Services Gradle plugin, or `google-services.json` requirement.

## Server setup

Follow [../server/README.md](../server/README.md). The API URL must point to the folder containing `index.php`, e.g. `https://yourdomain.com/echochat-api`. The `/health` route should return `ok: true` and `database: connected`.

Re-import `../server/database/schema.sql` on an existing database when upgrading so the auth rate-limit table is created. Existing tables are preserved by `CREATE TABLE IF NOT EXISTS`.

## Build

Use Android Studio with JDK 21. Run `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleRelease` from this folder. CI produces a debug APK for installation/testing and an unsigned release APK unless a protected upload keystore is configured.

Never commit upload keystores, passwords, tokens or database configuration. A release signed with a debug key is not suitable for store distribution.

## Release status

This is still an MVP, not a production-ready release. Current social posts and stories are text-only. Media upload, server-backed profile editing, password reset/email verification, login and registration abuse protection beyond the implemented server-side throttles, moderation/reporting, account export/deletion, push notifications, real-time WebSockets, read/delivery receipts, presence, end-to-end encryption and Echo AI via a separately configured PHP provider are not complete. Validate privacy isolation on two accounts/devices and perform server security/load testing before public distribution.
