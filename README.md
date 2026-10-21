# EchoChat

EchoChat is a native Android social messenger built with Kotlin, Jetpack Compose and Room, backed by a self-hosted PHP 8.1+ REST API and MySQL. **The application has no Firebase SDK or Firebase runtime dependency.**

## Features in the current MVP

- Email/password registration, login, session validation and logout through PHP.
- Public rooms, username lookup, private one-to-one conversations and server-backed message history.
- Local Room cache and retry of queued outgoing messages.
- Server-synchronized text posts, likes, bookmarks, comments and 24-hour text stories.
- Explore feed with search and category filters, plus profile, saved posts, Persian/English UI and light/dark themes.
- Optional Echo AI routed through an authenticated PHP endpoint. Provider credentials stay on the server and are not bundled into the APK.

## Repository layout

- Android project: `echochat-fixed-source/`
- PHP REST API: `server/public/`
- MySQL schema: `server/database/schema.sql`
- Server configuration template: `server/config.example.php`
- Android PHP API client: `echochat-fixed-source/app/src/main/java/com/example/data/remote/PhpApiClient.kt`

## Configure the PHP backend

1. Deploy the contents of `server/public/` to HTTPS hosting with PHP 8.1+, PDO MySQL and the PHP cURL extension (needed only if Echo AI is enabled).
2. Create a MySQL database and import `server/database/schema.sql`.
3. Copy `server/config.example.php` to a private `config.php` outside the public web root and set the database credentials.
4. Check `https://YOUR_DOMAIN/echochat-api/health`; it should report `ok: true` and `database: connected`.
5. In the app, enter the base URL of the folder containing `index.php`, connect, then register accounts.

To enable Echo AI, set `ai_api_key`, `ai_endpoint` and `ai_model` in the private server config to an OpenAI-compatible chat-completions service. Keep those credentials private. If the AI provider is not configured, the endpoint returns an error rather than a fabricated response.

See [server/README.md](server/README.md) for deployment instructions and API routes.

## Build and CI

Use Android Studio and JDK 21. From `echochat-fixed-source/`, run:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The Android GitHub Actions workflow runs unit tests, lint, and debug plus release APK builds, then uploads both as workflow artifacts. A release APK is install-ready only when the repository Actions signing secrets are configured; otherwise the release artifact is unsigned.

## Release limitations

This remains an MVP and must not be advertised as a complete production messenger yet. Password reset delivery, email verification, email verification, password reset delivery, moderation and abuse reporting, account deletion/privacy workflows, push notifications, WebSockets, end-to-end encryption, media upload, delivery/read receipts, online presence and independent security testing remain to be completed. The social feed currently synchronizes text posts and text stories only. Echo AI requires server-side provider configuration.

Before a public release, run two-account/two-device tests against the deployed HTTPS API, review the PHP host configuration and extend the basic rate limits for the expected traffic, and create a release-signed APK using a private signing key.
