# EchoChat PHP + MySQL API

This is the self-hosted backend for EchoChat Android. It implements accounts, public rooms, private conversations, message history and the text-based social feed. Android client authentication and sync use this API; Firebase is not required.

## Requirements

- PHP 8.1+ with PDO MySQL.
- MySQL 8+ (MariaDB 10.5+ may also work).
- HTTPS hosting.
- Apache URL rewriting enabled for the supplied `.htaccess`.
- PHP cURL extension only if Echo AI is enabled.

## Deploy to shared hosting

1. Create a MySQL database and a least-privilege database user.
2. Import `server/database/schema.sql` using phpMyAdmin or the host SQL importer. Re-importing is safe for existing tables because the schema uses `CREATE TABLE IF NOT EXISTS`; it adds the login-attempt and AI-request tables.
3. Copy `server/config.example.php` to a private `config.php` outside the public web root. Enter the database credentials there.
4. Upload the contents of `server/public/` to a folder such as `public_html/echochat-api/`. Keep the private config file outside directly downloadable folders.
5. Enable URL rewriting and install a valid HTTPS certificate.
6. Open `https://YOUR_DOMAIN/echochat-api/health`; a healthy response reports `ok: true` and `database: connected`.
7. In EchoChat, set the server URL to the folder containing `index.php`, then register and sign in.

Never commit real database credentials, bearer tokens or provider keys.

## API routes

- `GET /health` — API/database health check.
- `POST /auth/register`, `POST /auth/login` — create an account or sign in.
- `GET /auth/me`, `POST /auth/logout` — validate or revoke a session.
- `GET /users?username=handle` — exact username lookup.
- `GET /rooms`, `POST /rooms` — public rooms.
- `GET /posts`, `POST /posts` — text feed.
- `POST /posts/like`, `POST /posts/bookmark`, `POST /posts/delete` — post interactions.
- `GET /comments?post_id=...`, `POST /comments` — comments.
- `GET /stories`, `POST /stories`, `POST /stories/view` — text stories that expire after 24 hours.
- `GET /conversations`, `POST /conversations` — private one-to-one conversations.
- `GET /messages?room_id=...` or `?conversation_id=...`, `POST /messages` — message history and sending.
- `POST /ai/respond` — authenticated Echo AI request (optional provider configuration required).

Success responses use an `ok/data` JSON envelope; errors use `ok: false` with an error message.

## Optional Echo AI

The `/ai/respond` route forwards authenticated prompts to an OpenAI-compatible chat-completions endpoint. Configure the following in the private server `config.php`:

- `ai_api_key` — provider secret key.
- `ai_endpoint` — HTTPS chat-completions endpoint.
- `ai_model` — model ID supported by the provider.

The example config defaults the endpoint and model but leaves the key empty. The server returns a clear configuration error until the key is supplied. Provider credentials remain on the server and are never packaged into the Android APK. The request is sent only over HTTPS with certificate verification enabled.

## Security and MVP limits

Passwords use PHP `password_hash`; SQL uses prepared statements; bearer tokens are random values whose SHA-256 hashes are stored in MySQL; tokens expire and logout revokes them. Private-message reads and writes require conversation membership. API credentials must remain outside the public web root, and the API should be served over HTTPS only.

The API includes basic server-side limits of 8 failed attempts per email and 40 per source IP in a 15-minute window, plus 5 Echo AI requests per user per minute. These are lightweight safeguards, not a replacement for a WAF, host-level throttling, monitoring or a security audit. Email verification, password reset delivery, moderation and abuse controls, account deletion/privacy workflows, data retention, monitoring, push notifications, WebSockets, end-to-end encryption, media uploads, read/delivery receipts, online presence, profile editing and independent security testing remain unfinished. Messages and feed data use polling while the app is open; text posts and text stories are synchronized but media isn't uploaded yet.
