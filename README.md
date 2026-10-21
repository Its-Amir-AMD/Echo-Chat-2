# EchoChat

EchoChat is a native Android messenger built with Kotlin, Jetpack Compose, Room and a self-hosted PHP/MySQL API.

## Current branch

The PHP/MySQL backend supports email/password registration and login, server-backed public rooms, username lookup, private two-person conversations, message history, and a text-based social feed with posts, likes, bookmarks, comments and 24-hour text stories. Development improvements are being prepared in the separate feat/echochat-ux-rebuild branch; main is not changed by that work.

## Main folders

- Android source: echochat-fixed-source/
- PHP REST API and deployment notes: server/
- MySQL schema: server/database/schema.sql
- Android API client: echochat-fixed-source/app/src/main/java/com/example/data/remote/PhpApiClient.kt

## Try it

1. Deploy the PHP API to a hosting account with PHP 8.1+, PDO MySQL and HTTPS.
2. Import the SQL schema and set private database credentials outside the public web root.
3. Check the API health endpoint at your server URL plus /health.
4. Install the Android app and enter the base URL of the folder containing index.php.
5. Register two different accounts on separate devices to test public and private messaging.

See server/README.md for step-by-step setup and security notes.

## Build

Use Android Studio with JDK 21. From the echochat-fixed-source folder, run the Gradle tasks testDebugUnitTest, lintDebug, and assembleDebug. GitHub Actions also runs these checks and uploads a debug APK.

## Limitations

This is an MVP, not yet a production-ready release. Email verification and password reset, rate limiting, moderation, account deletion/privacy workflows, push notifications, WebSockets, end-to-end encryption, media uploads, read/delivery receipts and online presence are not included. Media uploads, server-backed profile editing, and Echo AI synchronization are not implemented by this API yet. The Explore feed uses text posts and text stories at this stage. Test on two accounts/devices and review the security/rate-limit requirements before publishing.
