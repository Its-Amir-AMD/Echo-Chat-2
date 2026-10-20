<?php
declare(strict_types=1);

date_default_timezone_set('UTC');
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, max-age=0');
header('X-Content-Type-Options: nosniff');

function respond(int $status, array $payload): never {
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function ok(mixed $data = null, int $status = 200): never {
    respond($status, ['ok' => true, 'data' => $data]);
}
function fail(int $status, string $message): never {
    respond($status, ['ok' => false, 'error' => $message]);
}
function input(): array {
    $raw = file_get_contents('php://input');
    if ($raw === false || strlen($raw) > 32768) fail(413, 'Request body is too large.');
    if ($raw === '') return [];
    try {
        $data = json_decode($raw, true, 32, JSON_THROW_ON_ERROR);
    } catch (JsonException) {
        fail(400, 'Request body must be valid JSON.');
    }
    if (!is_array($data)) fail(400, 'Request body must be a JSON object.');
    return $data;
}
function mb_length(string $value): int {
    if (function_exists('mb_strlen')) return mb_strlen($value, 'UTF-8');
    preg_match_all('/./us', $value, $matches);
    return count($matches[0] ?? []);
}
function mb_substr_safe(string $value, int $length): string {
    if (function_exists('mb_substr')) return mb_substr($value, 0, $length, 'UTF-8');
    preg_match_all('/./us', $value, $matches);
    return implode('', array_slice($matches[0] ?? [], 0, $length));
}
function required_string(array $data, string $key, int $maxLength): string {
    $value = $data[$key] ?? null;
    if (!is_string($value)) fail(422, 'Invalid field: ' . $key . '.');
    $value = trim($value);
    if ($value === '' || mb_length($value) > $maxLength) fail(422, 'Invalid field: ' . $key . '.');
    return $value;
}
function user_public(array $row): array {
    return [
        'id' => (string)$row['id'],
        'username' => (string)$row['username'],
        'display_name' => (string)$row['display_name'],
        'email' => (string)($row['email'] ?? ''),
        'created_at_ms' => isset($row['created_at_ms'])
            ? (int)$row['created_at_ms']
            : (isset($row['created_at']) ? (int)(strtotime((string)$row['created_at']) * 1000) : 0),
    ];
}
function message_public(array $row): array {
    return [
        'id' => (string)$row['id'],
        'channel_id' => (string)($row['conversation_id'] ?? $row['room_id'] ?? ''),
        'room_id' => $row['room_id'] === null ? null : (string)$row['room_id'],
        'conversation_id' => $row['conversation_id'] === null ? null : (string)$row['conversation_id'],
        'sender_id' => (string)$row['sender_id'],
        'sender_name' => (string)$row['sender_name'],
        'content' => (string)$row['content'],
        'timestamp' => (int)$row['created_at_ms'],
    ];
}
function new_id(): string { return bin2hex(random_bytes(16)); }
function find_user_by_id(PDO $db, string $id): array {
    $stmt = $db->prepare('SELECT id, email, username, display_name, created_at FROM users WHERE id = ? LIMIT 1');
    $stmt->execute([$id]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$user) fail(401, 'Account is no longer available.');
    return user_public($user);
}
function issue_token(PDO $db, string $userId, int $ttl): string {
    $token = bin2hex(random_bytes(32));
    $expiry = gmdate('Y-m-d H:i:s', time() + max(3600, min($ttl, 7776000)));
    $stmt = $db->prepare('INSERT INTO auth_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)');
    $stmt->execute([$userId, hash('sha256', $token), $expiry]);
    return $token;
}
function auth_user(PDO $db): array {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/^Bearer ([a-f0-9]{64})$/i', trim($header), $match)) fail(401, 'Sign in to continue.');
    $hash = hash('sha256', strtolower($match[1]));
    $stmt = $db->prepare(
        'SELECT u.id, u.email, u.username, u.display_name, u.created_at
         FROM auth_tokens t INNER JOIN users u ON u.id = t.user_id
         WHERE t.token_hash = ? AND t.revoked_at IS NULL AND t.expires_at > UTC_TIMESTAMP()
         LIMIT 1'
    );
    $stmt->execute([$hash]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) fail(401, 'Session expired. Please sign in again.');
    $db->prepare('UPDATE auth_tokens SET last_used_at = UTC_TIMESTAMP() WHERE token_hash = ?')->execute([$hash]);
    return user_public($row);
}
function require_member(PDO $db, string $conversationId, string $userId): void {
    $stmt = $db->prepare('SELECT 1 FROM conversation_members WHERE conversation_id = ? AND user_id = ? LIMIT 1');
    $stmt->execute([$conversationId, $userId]);
    if (!$stmt->fetchColumn()) fail(404, 'Conversation was not found.');
}
function conversation_public(PDO $db, string $conversationId, string $userId): array {
    require_member($db, $conversationId, $userId);
    $stmt = $db->prepare(
        'SELECT u.id, u.username, u.display_name
         FROM conversation_members cm INNER JOIN users u ON u.id = cm.user_id
         WHERE cm.conversation_id = ? AND cm.user_id <> ? LIMIT 1'
    );
    $stmt->execute([$conversationId, $userId]);
    $peer = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$peer) fail(500, 'Conversation member data is invalid.');
    $stmt = $db->prepare('SELECT content, created_at_ms FROM messages WHERE conversation_id = ? ORDER BY created_at_ms DESC LIMIT 1');
    $stmt->execute([$conversationId]);
    $last = $stmt->fetch(PDO::FETCH_ASSOC) ?: null;
    $stmt = $db->prepare('SELECT created_at_ms, updated_at_ms FROM conversations WHERE id = ? LIMIT 1');
    $stmt->execute([$conversationId]);
    $meta = $stmt->fetch(PDO::FETCH_ASSOC) ?: ['created_at_ms' => 0, 'updated_at_ms' => 0];
    return [
        'id' => $conversationId,
        'peer_id' => (string)$peer['id'],
        'peer_username' => (string)$peer['username'],
        'peer_display_name' => (string)$peer['display_name'],
        'member_ids' => [$userId, (string)$peer['id']],
        'last_message' => (string)($last['content'] ?? ''),
        'last_message_time' => (int)($last['created_at_ms'] ?? $meta['updated_at_ms']),
        'created_at' => (int)$meta['created_at_ms'],
    ];
}

try {
    $configPath = dirname(__DIR__) . '/config.php';
    if (!is_file($configPath)) fail(503, 'Server is not configured. Copy config.example.php to server/config.php and enter database credentials.');
    $config = require $configPath;
    if (!is_array($config)) fail(503, 'Server configuration is invalid.');
    $dbHost = (string)($config['db_host'] ?? 'localhost');
    $dbName = (string)($config['db_name'] ?? '');
    $dbUser = (string)($config['db_user'] ?? '');
    $dbPassword = (string)($config['db_password'] ?? '');
    if ($dbName === '' || $dbUser === '') fail(503, 'Database credentials are missing.');
    $db = new PDO(
        "mysql:host={$dbHost};dbname={$dbName};charset=utf8mb4",
        $dbUser,
        $dbPassword,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]
    );

    $origin = $_SERVER['HTTP_ORIGIN'] ?? '';
    $allowedOrigins = $config['allowed_origins'] ?? [];
    if ($origin !== '' && is_array($allowedOrigins) && in_array($origin, $allowedOrigins, true)) {
        header('Access-Control-Allow-Origin: ' . $origin);
        header('Vary: Origin');
    }
    header('Access-Control-Allow-Headers: Authorization, Content-Type');
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
        http_response_code(204);
        exit;
    }

    $method = strtoupper((string)($_SERVER['REQUEST_METHOD'] ?? 'GET'));
    $requestPath = parse_url((string)($_SERVER['REQUEST_URI'] ?? '/'), PHP_URL_PATH) ?: '/';
    $scriptDir = rtrim(str_replace('\\', '/', dirname((string)($_SERVER['SCRIPT_NAME'] ?? '/index.php'))), '/');
    if ($scriptDir !== '' && $scriptDir !== '/' && str_starts_with($requestPath, $scriptDir . '/')) {
        $requestPath = substr($requestPath, strlen($scriptDir));
    }
    $path = '/' . trim($requestPath, '/');
    $data = $method === 'POST' ? input() : [];

    if ($method === 'GET' && $path === '/health') {
        $db->query('SELECT 1');
        ok(['service' => 'EchoChat PHP API', 'version' => '1.0.0', 'database' => 'connected']);
    }

    if ($method === 'POST' && $path === '/auth/register') {
        $email = strtolower(required_string($data, 'email', 254));
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) fail(422, 'Enter a valid email address.');
        $username = strtolower(required_string($data, 'username', 24));
        if (!preg_match('/^[a-z0-9_]{3,24}$/', $username)) fail(422, 'Username must contain 3–24 letters, numbers or underscores.');
        $displayName = required_string($data, 'display_name', 80);
        $password = $data['password'] ?? '';
        if (!is_string($password) || strlen($password) < 8 || strlen($password) > 200) fail(422, 'Password must be 8–200 characters.');
        $id = new_id();
        try {
            $stmt = $db->prepare('INSERT INTO users (id, email, username, display_name, password_hash) VALUES (?, ?, ?, ?, ?)');
            $stmt->execute([$id, $email, $username, $displayName, password_hash($password, PASSWORD_DEFAULT)]);
        } catch (PDOException $e) {
            if ($e->getCode() === '23000') fail(409, 'Email address or username is already registered.');
            throw $e;
        }
        $user = find_user_by_id($db, $id);
        $token = issue_token($db, $id, (int)($config['token_ttl_seconds'] ?? 2592000));
        ok(['token' => $token, 'user' => $user], 201);
    }

    if ($method === 'POST' && $path === '/auth/login') {
        $email = strtolower(required_string($data, 'email', 254));
        $password = $data['password'] ?? '';
        if (!is_string($password) || $password === '') fail(422, 'Email and password are required.');
        $stmt = $db->prepare('SELECT id, password_hash FROM users WHERE email = ? LIMIT 1');
        $stmt->execute([$email]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$row || !password_verify($password, (string)$row['password_hash'])) fail(401, 'Invalid email or password.');
        $db->prepare('UPDATE users SET last_login_at = UTC_TIMESTAMP() WHERE id = ?')->execute([$row['id']]);
        ok(['token' => issue_token($db, (string)$row['id'], (int)($config['token_ttl_seconds'] ?? 2592000)), 'user' => find_user_by_id($db, (string)$row['id'])]);
    }

    if ($method === 'POST' && $path === '/auth/logout') {
        $user = auth_user($db);
        $header = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
        preg_match('/^Bearer ([a-f0-9]{64})$/i', trim($header), $match);
        $db->prepare('UPDATE auth_tokens SET revoked_at = UTC_TIMESTAMP() WHERE token_hash = ? AND user_id = ?')
            ->execute([hash('sha256', strtolower($match[1])), $user['id']]);
        ok(['logged_out' => true]);
    }

    if ($method === 'GET' && $path === '/auth/me') {
        $user = auth_user($db);
        ok(['user' => $user]);
    }

    $user = auth_user($db);

    if ($method === 'GET' && $path === '/users') {
        $username = strtolower(trim((string)($_GET['username'] ?? '')));
        if (!preg_match('/^[a-z0-9_]{3,24}$/', $username)) fail(422, 'Provide a valid username query parameter.');
        $stmt = $db->prepare('SELECT id, username, display_name, created_at FROM users WHERE username = ? LIMIT 1');
        $stmt->execute([$username]);
        $peer = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$peer) fail(404, 'No account was found for that username.');
        ok(['user' => user_public($peer)]);
    }

    if ($method === 'GET' && $path === '/rooms') {
        $stmt = $db->query(
            'SELECT r.id, r.name, r.description, r.icon_name,
                (SELECT m.content FROM messages m WHERE m.room_id = r.id ORDER BY m.created_at_ms DESC, m.id DESC LIMIT 1) AS last_message,
                (SELECT m.created_at_ms FROM messages m WHERE m.room_id = r.id ORDER BY m.created_at_ms DESC, m.id DESC LIMIT 1) AS last_message_time
             FROM rooms r WHERE r.is_active = 1 ORDER BY r.id ASC'
        );
        $rooms = [];
        foreach ($stmt->fetchAll() as $room) {
            $rooms[] = [
                'id' => (string)$room['id'],
                'name' => (string)$room['name'],
                'description' => (string)$room['description'],
                'icon_name' => (string)$room['icon_name'],
                'is_direct_message' => false,
                'last_message' => (string)($room['last_message'] ?? ''),
                'last_message_time' => (int)($room['last_message_time'] ?? 0),
                'created_at' => 0,
            ];
        }
        ok(['rooms' => $rooms]);
    }

    if ($method === 'GET' && $path === '/conversations') {
        $stmt = $db->prepare('SELECT conversation_id FROM conversation_members WHERE user_id = ?');
        $stmt->execute([$user['id']]);
        $conversations = [];
        foreach ($stmt->fetchAll(PDO::FETCH_COLUMN) as $conversationId) {
            $conversations[] = conversation_public($db, (string)$conversationId, (string)$user['id']);
        }
        usort($conversations, static fn(array $a, array $b): int => $b['last_message_time'] <=> $a['last_message_time']);
        ok(['conversations' => $conversations]);
    }

    if ($method === 'POST' && $path === '/conversations') {
        $username = strtolower(required_string($data, 'username', 24));
        if (!preg_match('/^[a-z0-9_]{3,24}$/', $username)) fail(422, 'Provide a valid username.');
        $stmt = $db->prepare('SELECT id FROM users WHERE username = ? LIMIT 1');
        $stmt->execute([$username]);
        $peerId = $stmt->fetchColumn();
        if (!$peerId) fail(404, 'No account was found for @' . $username . '.');
        if ((string)$peerId === (string)$user['id']) fail(422, 'You cannot start a conversation with your own account.');

        $ids = [(string)$user['id'], (string)$peerId];
        sort($ids, SORT_STRING);
        $pairKey = hash('sha256', $ids[0] . ':' . $ids[1]);
        $stmt = $db->prepare('SELECT id FROM conversations WHERE pair_key = ? LIMIT 1');
        $stmt->execute([$pairKey]);
        $conversationId = $stmt->fetchColumn();
        if (!$conversationId) {
            $conversationId = 'dm_' . substr($pairKey, 0, 32);
            $now = (int)floor(microtime(true) * 1000);
            try {
                $db->beginTransaction();
                $db->prepare('INSERT INTO conversations (id, pair_key, created_at_ms, updated_at_ms) VALUES (?, ?, ?, ?)')
                    ->execute([$conversationId, $pairKey, $now, $now]);
                $member = $db->prepare('INSERT INTO conversation_members (conversation_id, user_id) VALUES (?, ?)');
                $member->execute([$conversationId, $ids[0]]);
                $member->execute([$conversationId, $ids[1]]);
                $db->commit();
            } catch (Throwable $e) {
                if ($db->inTransaction()) $db->rollBack();
                $stmt = $db->prepare('SELECT id FROM conversations WHERE pair_key = ? LIMIT 1');
                $stmt->execute([$pairKey]);
                $existingId = $stmt->fetchColumn();
                if (!$existingId) throw $e;
                $conversationId = (string)$existingId;
            }
        }
        ok(['conversation' => conversation_public($db, (string)$conversationId, (string)$user['id'])], 201);
    }

    if ($method === 'GET' && $path === '/messages') {
        $roomId = trim((string)($_GET['room_id'] ?? ''));
        $conversationId = trim((string)($_GET['conversation_id'] ?? ''));
        if (($roomId === '') === ($conversationId === '')) fail(422, 'Provide exactly one room_id or conversation_id.');
        if ($roomId !== '') {
            $stmt = $db->prepare('SELECT id FROM rooms WHERE id = ? AND is_active = 1 LIMIT 1');
            $stmt->execute([$roomId]);
            if (!$stmt->fetchColumn()) fail(404, 'Room was not found.');
            $stmt = $db->prepare('SELECT id, room_id, conversation_id, sender_id, sender_name, content, created_at_ms FROM messages WHERE room_id = ? ORDER BY created_at_ms DESC, id DESC LIMIT 100');
            $stmt->execute([$roomId]);
        } else {
            require_member($db, $conversationId, (string)$user['id']);
            $stmt = $db->prepare('SELECT id, room_id, conversation_id, sender_id, sender_name, content, created_at_ms FROM messages WHERE conversation_id = ? ORDER BY created_at_ms DESC, id DESC LIMIT 100');
            $stmt->execute([$conversationId]);
        }
        $messages = array_map('message_public', $stmt->fetchAll());
        ok(['messages' => array_reverse($messages)]);
    }

    if ($method === 'POST' && $path === '/messages') {
        $channelId = required_string($data, 'channel_id', 64);
        $clientMessageId = required_string($data, 'client_message_id', 64);
        if (!preg_match('/^[a-zA-Z0-9_-]{8,64}$/', $clientMessageId)) fail(422, 'Invalid client message id.');
        $content = required_string($data, 'content', 10000);
        if (preg_match('//u', $content) !== 1) fail(422, 'Message contains invalid text encoding.');
        $roomId = null;
        $conversationId = null;
        if (str_starts_with($channelId, 'dm_')) {
            require_member($db, $channelId, (string)$user['id']);
            $conversationId = $channelId;
        } else {
            $stmt = $db->prepare('SELECT id FROM rooms WHERE id = ? AND is_active = 1 LIMIT 1');
            $stmt->execute([$channelId]);
            if (!$stmt->fetchColumn()) fail(404, 'Room was not found.');
            $roomId = $channelId;
        }

        $stmt = $db->prepare('SELECT id, room_id, conversation_id, sender_id, sender_name, content, created_at_ms FROM messages WHERE id = ? LIMIT 1');
        $stmt->execute([$clientMessageId]);
        $existing = $stmt->fetch(PDO::FETCH_ASSOC);
        if ($existing) {
            if ((string)$existing['sender_id'] !== (string)$user['id']) fail(409, 'Message id already exists.');
            ok(['message' => message_public($existing)]);
        }

        $now = (int)floor(microtime(true) * 1000);
        $stmt = $db->prepare('INSERT INTO messages (id, room_id, conversation_id, sender_id, sender_name, content, created_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?)');
        try {
            $stmt->execute([$clientMessageId, $roomId, $conversationId, $user['id'], mb_substr_safe((string)$user['display_name'], 80), $content, $now]);
        } catch (PDOException $e) {
            if ($e->getCode() !== '23000') throw $e;
            $stmt = $db->prepare('SELECT id, room_id, conversation_id, sender_id, sender_name, content, created_at_ms FROM messages WHERE id = ? LIMIT 1');
            $stmt->execute([$clientMessageId]);
            $existing = $stmt->fetch(PDO::FETCH_ASSOC);
            if (!$existing || (string)$existing['sender_id'] !== (string)$user['id']) fail(409, 'Could not save this message.');
            ok(['message' => message_public($existing)]);
        }
        if ($conversationId !== null) {
            $db->prepare('UPDATE conversations SET updated_at_ms = ? WHERE id = ?')->execute([$now, $conversationId]);
        }
        $stmt = $db->prepare('SELECT id, room_id, conversation_id, sender_id, sender_name, content, created_at_ms FROM messages WHERE id = ?');
        $stmt->execute([$clientMessageId]);
        ok(['message' => message_public($stmt->fetch(PDO::FETCH_ASSOC))], 201);
    }

    fail(404, 'Unknown API endpoint.');
} catch (Throwable $e) {
    error_log('EchoChat API failure: ' . $e::class . ': ' . $e->getMessage());
    fail(500, 'Server error. Check the server error log for details.');
}
