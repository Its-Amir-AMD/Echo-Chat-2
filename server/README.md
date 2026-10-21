# EchoChat PHP + MySQL API

Self-hosted REST API for EchoChat Android. The backend uses PHP 8.1+, PDO MySQL and MySQL 8+ (MariaDB 10.5+ may work). No Firebase services are required.

## Shared-host deployment

1. Create a MySQL database and a least-privilege user in the hosting panel.
2. Import `server/database/schema.sql` with phpMyAdmin or the SQL importer.
3. Copy `server/config.example.php` to `config.php` outside the public web root. Set the actual database credentials and replace the example `rate_limit_hmac_key` with a long random secret.
4. Upload the contents of `server/public/` into a folder such as `public_html/echochat-api/`. Keep private config outside the publicly downloadable directory.
5. Enable Apache rewrite rules and HTTPS.
6. Open `https://YOUR_DOMAIN/echochat-api/health`; expect `ok: true` and `database: connected`.
7. Enter the base URL of the folder containing `index.php` into the Android app.

When updating an existing database, re-import `schema.sql`. Its `CREATE TABLE IF NOT EXISTS` statements preserve existing tables and create the new rate-limit table. Do not commit real configuration or credentials.

## API routes

- `GET /health` — service and database health.
- `POST /auth/register` — create an email/password account.
- `POST /auth/login` — authenticate and issue a bearer token.
- `GET /auth/me` — validate the current session.
- `POST /auth/logout` — revoke the current token.
- `GET /users?username=handle` — exact username lookup.
- `GET /rooms`, `POST /rooms` — list/create public rooms.
- `GET /posts`, `POST /posts` — text posts.
- `POST /posts/like`, `POST /posts/bookmark`, `POST /posts/delete` — post interactions.
- `GET /comments?post_id=...`, `POST /comments` — comments.
- `GET /stories`, `POST /stories`, `POST /stories/view` — text stories and view state.
- `GET /conversations`, `POST /conversations` — private one-to-one conversations.
- `GET /messages?room_id=...`, `GET /messages?conversation_id=...`, `POST /messages` — message history and sending.

Responses follow an `ok/data` JSON envelope. Errors use `ok: false` and an error message.

## Current protections

- Passwords use PHP `password_hash` / `password_verify`.
- SQL requests use PDO prepared statements.
- Bearer tokens are random values; only SHA-256 hashes are stored server-side. Tokens expire and logout revokes them.
- Private-conversation reads and sends require conversation membership.
- Login failures are rate-limited by a one-way hash of email and IP; registration attempts are throttled by email/IP. Raw IP addresses and email addresses are not written to the rate-limit table.
- The Android client requires HTTPS for non-localhost APIs.

## Remaining production work

This is not yet a production-ready general-purpose social platform. Email verification and password-reset delivery, moderation and abuse reporting, account deletion/export, privacy policy and retention workflows, media uploads/storage, server-backed profile editing, push notifications, WebSockets, read/delivery receipts, online presence, end-to-end encryption, detailed audit logging, load testing and independent security review remain to be implemented or tested. Social feed data is text-only. Firebase-based AI has been removed; AI requires a separately secured PHP/provider integration.
