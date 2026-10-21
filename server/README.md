# EchoChat PHP + MySQL API

This folder contains a self-hosted backend for Android account login, public chat rooms, username lookup, private conversations, and message history.

## Requirements

- PHP 8.1+ with PDO MySQL enabled.
- MySQL 8+ (MariaDB 10.5+ may also work).
- A domain/hosting account with HTTPS enabled.
- Apache URL rewriting enabled for the supplied .htaccess file.

## Deploy to shared hosting

1. Create a MySQL database and a database user in the hosting control panel. Grant the user access only to this database.
2. Import the SQL file at server/database/schema.sql using phpMyAdmin or the host's SQL importer.
3. Copy server/config.example.php to config.php and enter the actual database host, database name, username, and password. Never commit real credentials.
4. Upload the contents of server/public/ into a web folder such as public_html/echochat-api/. Keep config.php outside the public web root. If you upload the public folder's contents into public_html/echochat-api/, place config.php in the hosting account's private home directory (usually one level above public_html). The API checks that private location. If you instead preserve the original server/public directory layout, keep config.php alongside server/public, outside the public web root. Do not leave real config credentials anywhere directly downloadable from the website.
5. Confirm .htaccess rewriting is enabled and install a valid HTTPS certificate.
6. Open https://YOUR_DOMAIN/echochat-api/health. The response should contain ok: true and database: connected.
7. In EchoChat, enter https://YOUR_DOMAIN/echochat-api in the server URL field and tap Connect.

The base URL must point to the folder containing index.php. Do not add /index.php to it.

## API routes

- GET /health — check the API and database connection.
- POST /auth/register — create an account and access token.
- POST /auth/login — sign in with email and password.
- GET /auth/me — validate the current session.
- POST /auth/logout — revoke the current token.
- GET /users?username=handle — exact username lookup.
- GET /rooms — list public rooms.
- POST /rooms — create a public room for the signed-in account.
- GET /posts — list the latest social posts with per-user likes/bookmarks.
- POST /posts — publish a text post.
- POST /posts/like — toggle the signed-in user's like on a post.
- POST /posts/bookmark — toggle a saved-post bookmark.
- POST /posts/delete — delete a post owned by the signed-in user.
- GET /comments?post_id=... — list comments for a post.
- POST /comments — publish a comment.
- GET /stories — list unexpired stories and per-user view state.
- POST /stories — publish a text story (expires after 24 hours).
- POST /stories/view — mark an active story as viewed.
- GET /conversations — list the signed-in user's private conversations.
- POST /conversations — create or return a private conversation for a username.
- GET /messages?room_id=general — recent messages for a public room.
- GET /messages?conversation_id=dm_... — recent messages in a private conversation.
- POST /messages — send a message. channel_id is a room id or conversation id.

Success responses use an ok/data JSON envelope. Errors use ok: false and an error message.

## Security and MVP boundaries

Passwords are stored with PHP password_hash, SQL uses prepared statements, and bearer tokens are random values whose SHA-256 hashes are stored in MySQL. Tokens expire and logout revokes them. Private message reads and writes require membership. Email addresses are not returned by username search.

Serve this API only over HTTPS. Keep configuration credentials outside the public web root, use a least-privilege database user, keep backups, and don't publish database credentials or access tokens.

This is an MVP, not yet a public-release-ready service. Before a broad release, add and test login throttling, email verification and password-reset delivery, abuse reporting/moderation, privacy and account-deletion workflows, data retention, monitoring, and independent security testing. Messages and social-feed data are refreshed by polling while the app is open; push notifications, WebSockets, end-to-end encryption, media uploads, delivery/read receipts, online presence, server-backed profile editing, and Echo AI synchronization are not included.
