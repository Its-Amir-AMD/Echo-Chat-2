# EchoChat

EchoChat is a native Android social messenger built with Kotlin, Jetpack Compose and Room, backed by a self-hosted PHP 8.1+ / MySQL REST API.

## Current features

- Email/password registration, login, token validation and logout through PHP/MySQL.
- Public chat rooms, private one-to-one conversations, message history and local Room caching.
- Server-synced text posts, likes, bookmarks, comments and 24-hour text stories.
- Explore feed, profile/settings UI, Persian RTL and English LTR, light/dark themes.
- HTTPS-only remote API configuration, input validation and database prepared statements.
- Firebase has been removed from the Android build and runtime. No Firebase project or `google-services.json` is required.

## Repository layout

- Android source: `echochat-fixed-source/`
- PHP API: `server/public/index.php`
- MySQL schema: `server/database/schema.sql`
- Android HTTP client: `echochat-fixed-source/app/src/main/java/com/example/data/remote/PhpApiClient.kt`

## Configure the backend

Follow [server/README.md](server/README.md). Import `server/database/schema.sql`, configure a private `config.php` outside the public web root, deploy the contents of `server/public/` over HTTPS, and verify the `/health` route. In the app, set the base URL to the folder containing `index.php`.

When upgrading an existing installation, re-import `server/database/schema.sql` so that newly added tables are created. The schema uses `CREATE TABLE IF NOT EXISTS` for existing tables.

## Build Android

Use Android Studio with JDK 21. From `echochat-fixed-source/`, run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

On Windows, use `gradlew.bat` with the same tasks. GitHub Actions runs tests, lint, a debug APK build and an unsigned release APK build. The release APK is unsigned unless a private upload keystore is supplied using the environment variables `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, and optionally `KEY_ALIAS`.

**Do not publish the unsigned artifact.** For Google Play or an update to an existing installation, sign the release using the app owner's protected upload key and verify the application ID and version.

## Known release blockers

This remains an MVP and must not be described as a fully production-ready social platform yet. Current server-synced social content is text-only. Media uploads, server-backed profile editing, password reset delivery, email verification, abuse reporting/moderation, account deletion and export, push notifications, WebSockets, online presence, delivery/read receipts, end-to-end encryption, load testing, and an independent security review remain to be implemented or validated. Firebase-based AI was removed rather than left as a hidden dependency; an AI feature needs a separately configured PHP/provider integration before it can be advertised as working.

Before public release, test registration, logout, rate limiting, room messaging, private-conversation privacy, and social-feed sync using two independent accounts and devices. Keep database credentials and signing keys out of Git.
