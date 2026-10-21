# EchoChat Android

Native Android application built with Kotlin, Jetpack Compose, Room, coroutines and a self-hosted PHP/MySQL REST API. Persian (RTL) and English (LTR) UI are supported.

## Implemented in this branch

- Email/password registration and login through the PHP API.
- HTTPS server configuration from inside the app; the configured URL is stored privately on the device.
- Public chat rooms, exact username lookup and member-protected private conversations.
- Local Room caching and retry of queued outgoing messages.
- Message history refresh through polling while the app is open.
- Persian/English UI, theme preferences, local explore/feed/profile UI.

## Server setup

Follow the steps in ../server/README.md. The app defaults to `https://chat.userdesk.ir/chat` and calls the API routes directly (it does not embed the website in a WebView). The URL must use HTTPS and point to the folder containing `index.php`. Its health route at URL + `/health` should return `ok: true` and `database: connected`. Do not use the HTTP URL for accounts or messaging because credentials and bearer tokens would be sent without transport encryption.

The SQL schema seeds three public rooms: general, technology and help. You can customize their names and descriptions in the rooms table after installation.

## Build on Windows

Use Android Studio and JDK 21. From this folder, run Gradle tasks testDebugUnitTest, lintDebug, and assembleDebug. The debug APK is written to app/build/outputs/apk/debug/app-debug.apk.

GitHub Actions runs unit tests, lint and a debug build when branches or pull requests are updated.

## Scope and release readiness

This branch is a working MVP, not a claim of production readiness. Password reset/email verification, login throttling, server moderation and abuse controls, account deletion/privacy flows, data retention, push notifications, WebSockets, end-to-end encryption, media uploads, delivery/read receipts, presence and load testing still need implementation. The Explore feed, stories, comments, profile editing and Echo AI are not synchronized by the PHP API. Before public release, test registration, logout, privacy isolation, public rooms and private conversations with at least two separate accounts/devices, review the PHP host setup, and add rate limiting and automated API tests.

Firebase remains an optional dependency for the existing Echo AI integration only; normal account login and messaging use PHP/MySQL. Do not put database passwords, session tokens, signing keys or private hosting config into this repository.
