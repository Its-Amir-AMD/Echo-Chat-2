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

function social_post_select_sql(): string {
    return 'SELECT p.id, p.author_id, p.content, p.tags, p.category, p.created_at_ms,
                u.username, u.display_name,
                (SELECT COUNT(*) FROM post_likes l WHERE l.post_id = p.id) AS likes_count,
                (SELECT COUNT(*) FROM post_comments c WHERE c.post_id = p.id) AS comments_count,
                EXISTS(SELECT 1 FROM post_likes l WHERE l.post_id = p.id AND l.user_id = ?) AS is_liked,
                EXISTS(SELECT 1 FROM post_bookmarks b WHERE b.post_id = p.id AND b.user_id = ?) AS is_bookmarked
            FROM social_posts p INNER JOIN users u ON u.id = p.author_id';
}
function social_post_payload(array $row): array {
    return [
        'id' => (string)$row['id'],
        'author_id' => (string)$row['author_id'],
        'author_name' => (string)$row['display_name'],
        'author_username' => (string)$row['username'],
        'author_role' => 'Echo member',
        'author_avatar_color' => 4281639158,
        'content' => (string)$row['content'],
        'tags' => (string)$row['tags'],
        'category' => (string)$row['category'],
        'likes_count' => (int)$row['likes_count'],
        'comments_count' => (int)$row['comments_count'],
        'shares_count' => 0,
        'is_liked' => (bool)$row['is_liked'],
        'is_bookmarked' => (bool)$row['is_bookmarked'],
        'timestamp' => (int)$row['created_at_ms'],
    ];
}
function fetch_social_post(PDO $db, string $postId, string $viewerId): array {
    $stmt = $db->prepare(social_post_select_sql() . ' WHERE p.id = ? LIMIT 1');
    $stmt->execute([$viewerId, $viewerId, $postId]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$row) fail(404, 'Post was not found.');
    return social_post_payload($row);
}
function social_comment_payload(array $row): array {
    return [
        'id' => (string)$row['id'],
        'post_id' => (string)$row['post_id'],
        'author_id' => (string)$row['author_id'],
        'author_name' => (string)$row['display_name'],
        'content' => (string)$row['content'],
        'timestamp' => (int)$row['created_at_ms'],
    ];
}


/**
 * Server-side throttling for authentication endpoints.
 * Only HMACs of identity/IP are persisted; raw identifiers are not logged here.
 */
function auth_rate_hash(array $config, string $value): string {
    $key = trim((string)($config['rate_limit_hmac_key'] ?? ''));
    if ($key === '' || str_starts_with($key, 'CHANGE_ME') || strlen($key) < 32) {
        $key = (string)($config['db_password'] ?? '');
    }
    if (strlen($key) < 32) $key = hash('sha256', $key . '|EchoChat authentication throttling');
    return hash_hmac('sha256', $value, $key);
}
function auth_rate_context(array $config, string $action, string $identity): array {
    $ip = trim((string)($_SERVER['REMOTE_ADDR'] ?? 'unknown'));
    if (!filter_var($ip, FILTER_VALIDATE_IP)) $ip = 'unknown';
    return [
        'action' => $action,
        'identity_hash' => auth_rate_hash($config, $action . ':' . strtolower(trim($identity))),
        'ip_hash' => auth_rate_hash($config, 'ip:' . $ip),
    ];
}
function enforce_auth_rate_limit(
    PDO $db,
    array $config,
    string $action,
    string $identity,
    int $identityLimit,
    int $ipLimit,
    int $windowSeconds
): array {
    $context = auth_rate_context($config, $action, $identity);
    $cutoff = gmdate('Y-m-d H:i:s', time() - $windowSeconds);
    $failureOnly = $action === 'login' ? ' AND was_successful = 0' : '';

    $identityQuery = $db->prepare(
        'SELECT COUNT(*) FROM auth_attempts WHERE action = ? AND identity_hash = ? AND attempted_at >= ?' . $failureOnly
    );
    $identityQuery->execute([$action, $context['identity_hash'], $cutoff]);
    $identityCount = (int)$identityQuery->fetchColumn();

    $ipQuery = $db->prepare(
        'SELECT COUNT(*) FROM auth_attempts WHERE action = ? AND ip_hash = ? AND attempted_at >= ?' . $failureOnly
    );
    $ipQuery->execute([$action, $context['ip_hash'], $cutoff]);
    $ipCount = (int)$ipQuery->fetchColumn();

    if ($identityCount >= $identityLimit || $ipCount >= $ipLimit) {
        header('Retry-After: ' . $windowSeconds);
        fail(429, 'Too many authentication attempts. Please try again later.');
    }
    return $context;
}
function record_auth_attempt(PDO $db, array $context, bool $successful): void {
    $stmt = $db->prepare(
        'INSERT INTO auth_attempts (action, identity_hash, ip_hash, was_successful, attempted_at)
         VALUES (?, ?, ?, ?, UTC_TIMESTAMP())'
    );
    $stmt->execute([
        $context['action'],
        $context['identity_hash'],
        $context['ip_hash'],
        $successful ? 1 : 0,
    ]);

    // Bounded, probabilistic cleanup avoids running a table-wide delete for every request.
    if (random_int(1, 50) === 1) {
        $db->exec('DELETE FROM auth_attempts WHERE attempted_at < UTC_TIMESTAMP() - INTERVAL 2 DAY');
    }
}

try {
    // Works both when public/ is the document root and when its contents are
    // deployed into a subfolder such as public_html/echochat-api/.
    $configCandidates = [
        dirname(__DIR__) . '/config.php',
        dirname(__DIR__, 2) . '/config.php',
    ];
    $configPath = '';
    foreach ($configCandidates as $candidate) {
        if (is_file($candidate)) { $configPath = $candidate; break; }
    }
    if ($configPath === '') fail(503, 'Server is not configured. Copy config.example.php to a private config.php outside the public web root.');
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
        $rateContext = enforce_auth_rate_limit($db, $config, 'register', $email, 5, 12, 3600);
        $id = new_id();
        try {
            $stmt = $db->prepare('INSERT INTO users (id, email, username, display_name, password_hash) VALUES (?, ?, ?, ?, ?)');
            $stmt->execute([$id, $email, $username, $displayName, password_hash($password, PASSWORD_DEFAULT)]);
        } catch (PDOException $e) {
            record_auth_attempt($db, $rateContext, false);
            if ($e->getCode() === '23000') fail(409, 'Email address or username is already registered.');
            throw $e;
        }
        record_auth_attempt($db, $rateContext, true);
        $user = find_user_by_id($db, $id);
        $token = issue_token($db, $id, (int)($config['token_ttl_seconds'] ?? 2592000));
        ok(['token' => $token, 'user' => $user], 201);
    }

    if ($method === 'POST' && $path === '/auth/login') {
        $email = strtolower(required_string($data, 'email', 254));
        $rateContext = enforce_auth_rate_limit($db, $config, 'login', $email, 8, 35, 900);
        $password = $data['password'] ?? '';
        if (!is_string($password) || $password === '') fail(422, 'Email and password are required.');
        $stmt = $db->prepare('SELECT id, password_hash FROM users WHERE email = ? LIMIT 1');
        $stmt->execute([$email]);
        $row = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$row || !password_verify($password, (string)$row['password_hash'])) {
            record_auth_attempt($db, $rateContext, false);
            fail(401, 'Invalid email or password.');
        }
        record_auth_attempt($db, $rateContext, true);
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


    // Echo AI is proxied by PHP to an OpenAI-compatible provider; secrets never reach Android.
    if ($method === 'POST' && $path === '/ai/reply') {
        $prompt = required_string($data, 'prompt', 6000);
        if (preg_match('//u', $prompt) !== 1) fail(422, 'Prompt contains invalid text encoding.');

        $aiEndpoint = trim((string)($config['ai_endpoint'] ?? ''));
        $aiApiKey = trim((string)($config['ai_api_key'] ?? ''));
        $aiModel = trim((string)($config['ai_model'] ?? ''));
        if ($aiEndpoint === '' || $aiApiKey === '' || $aiModel === '' ||
            str_starts_with($aiApiKey, 'CHANGE_ME')) {
            fail(503, 'Echo AI is not configured on the PHP server.');
        }
        $endpointParts = parse_url($aiEndpoint);
        if (!is_array($endpointParts) || ($endpointParts['scheme'] ?? '') !== 'https' ||
            empty($endpointParts['host']) || isset($endpointParts['user']) ||
            isset($endpointParts['pass']) || isset($endpointParts['fragment'])) {
            fail(503, 'AI provider endpoint must be a valid HTTPS URL.');
        }
        if (!function_exists('curl_init')) {
            fail(503, 'PHP cURL extension is required for Echo AI.');
        }

        $payload = [
            'model' => $aiModel,
            'messages' => [
                [
                    'role' => 'system',
                    'content' => 'You are Echo AI, a helpful assistant inside the Echo Chat app. Answer clearly and respectfully. Reply in the same language as the user unless they request another language.'
                ],
                ['role' => 'user', 'content' => $prompt],
            ],
            'temperature' => 0.7,
            'max_tokens' => 800,
        ];
        try {
            $encodedPayload = json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            fail(422, 'Could not encode the AI request.');
        }
        $handle = curl_init($aiEndpoint);
        curl_setopt_array($handle, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $encodedPayload,
            CURLOPT_HTTPHEADER => [
                'Content-Type: application/json',
                'Accept: application/json',
                'Authorization: Bearer ' . $aiApiKey,
            ],
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_CONNECTTIMEOUT => 8,
            CURLOPT_TIMEOUT => 35,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_MAXREDIRS => 0,
        ]);
        $rawResponse = curl_exec($handle);
        $curlErrno = curl_errno($handle);
        $httpStatus = (int)curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
        curl_close($handle);
        if ($rawResponse === false || $curlErrno !== 0) {
            error_log('EchoChat AI provider connection failed; cURL errno ' . $curlErrno);
            fail(502, 'AI provider could not be reached. Please try again.');
        }
        if ($httpStatus < 200 || $httpStatus >= 300) {
            error_log('EchoChat AI provider returned HTTP ' . $httpStatus);
            fail(502, 'AI provider returned an error. Please try again later.');
        }
        try {
            $providerData = json_decode($rawResponse, true, 32, JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            fail(502, 'AI provider returned an invalid response.');
        }
        $reply = $providerData['choices'][0]['message']['content'] ?? null;
        if (!is_string($reply) || trim($reply) === '') {
            fail(502, 'AI provider returned an empty response.');
        }
        ok(['reply' => mb_substr_safe(trim($reply), 12000)]);
    }

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

    // Create a public room on the server; local-only channel creation is not enough.
    if ($method === 'POST' && $path === '/rooms') {
        $name = required_string($data, 'name', 100);
        if (mb_length($name) < 2) fail(422, 'Channel name must contain at least 2 characters.');

        $description = $data['description'] ?? '';
        if (!is_string($description)) fail(422, 'Invalid field: description.');
        $description = trim($description);
        if (mb_length($description) > 255) fail(422, 'Channel description must be at most 255 characters.');

        $iconName = $data['icon_name'] ?? 'forum';
        $allowedIcons = ['forum', 'code', 'palette', 'smart_toy'];
        if (!is_string($iconName) || !in_array($iconName, $allowedIcons, true)) {
            fail(422, 'Choose a supported channel icon.');
        }

        $id = 'room_' . bin2hex(random_bytes(12));
        $stmt = $db->prepare(
            'INSERT INTO rooms (id, name, description, icon_name, is_active) VALUES (?, ?, ?, ?, 1)'
        );
        $stmt->execute([$id, $name, $description, $iconName]);

        ok(['room' => [
            'id' => $id,
            'name' => $name,
            'description' => $description,
            'icon_name' => $iconName,
            'is_direct_message' => false,
            'last_message' => '',
            'last_message_time' => 0,
            'created_at' => (int) floor(microtime(true) * 1000),
        ]], 201);
    }

    if ($method === 'GET' && $path === '/posts') {
        $stmt = $db->prepare(
            social_post_select_sql() . ' ORDER BY p.created_at_ms DESC, p.id DESC LIMIT 100'
        );
        $stmt->execute([$user['id'], $user['id']]);
        ok(['posts' => array_map('social_post_payload', $stmt->fetchAll(PDO::FETCH_ASSOC))]);
    }

    if ($method === 'POST' && $path === '/posts') {
        $content = required_string($data, 'content', 10000);
        $tags = $data['tags'] ?? '';
        if (!is_string($tags)) fail(422, 'Invalid field: tags.');
        $tags = trim($tags);
        if (mb_length($tags) > 500) fail(422, 'Tags must be at most 500 characters.');
        $category = $data['category'] ?? 'tech';
        if (!is_string($category) || !in_array($category, ['tech', 'design', 'ai'], true)) {
            fail(422, 'Choose a supported post category.');
        }
        $id = 'post_' . bin2hex(random_bytes(12));
        $now = (int)floor(microtime(true) * 1000);
        $stmt = $db->prepare(
            'INSERT INTO social_posts (id, author_id, content, tags, category, created_at_ms) VALUES (?, ?, ?, ?, ?, ?)'
        );
        $stmt->execute([$id, $user['id'], $content, $tags, $category, $now]);
        ok(['post' => fetch_social_post($db, $id, (string)$user['id'])], 201);
    }

    if ($method === 'POST' && $path === '/posts/like') {
        $postId = required_string($data, 'post_id', 64);
        $db->beginTransaction();
        try {
            $lock = $db->prepare('SELECT id FROM social_posts WHERE id = ? FOR UPDATE');
            $lock->execute([$postId]);
            if (!$lock->fetchColumn()) fail(404, 'Post was not found.');
            $check = $db->prepare('SELECT 1 FROM post_likes WHERE post_id = ? AND user_id = ? LIMIT 1');
            $check->execute([$postId, $user['id']]);
            if ($check->fetchColumn()) {
                $db->prepare('DELETE FROM post_likes WHERE post_id = ? AND user_id = ?')
                    ->execute([$postId, $user['id']]);
            } else {
                $db->prepare('INSERT INTO post_likes (post_id, user_id, created_at_ms) VALUES (?, ?, ?)')
                    ->execute([$postId, $user['id'], (int)floor(microtime(true) * 1000)]);
            }
            $db->commit();
        } catch (Throwable $e) {
            if ($db->inTransaction()) $db->rollBack();
            throw $e;
        }
        ok(['post' => fetch_social_post($db, $postId, (string)$user['id'])]);
    }

    if ($method === 'POST' && $path === '/posts/bookmark') {
        $postId = required_string($data, 'post_id', 64);
        $exists = $db->prepare('SELECT 1 FROM social_posts WHERE id = ? LIMIT 1');
        $exists->execute([$postId]);
        if (!$exists->fetchColumn()) fail(404, 'Post was not found.');
        $check = $db->prepare('SELECT 1 FROM post_bookmarks WHERE post_id = ? AND user_id = ? LIMIT 1');
        $check->execute([$postId, $user['id']]);
        if ($check->fetchColumn()) {
            $db->prepare('DELETE FROM post_bookmarks WHERE post_id = ? AND user_id = ?')
                ->execute([$postId, $user['id']]);
        } else {
            $db->prepare('INSERT INTO post_bookmarks (post_id, user_id, created_at_ms) VALUES (?, ?, ?)')
                ->execute([$postId, $user['id'], (int)floor(microtime(true) * 1000)]);
        }
        ok(['post' => fetch_social_post($db, $postId, (string)$user['id'])]);
    }

    if ($method === 'POST' && $path === '/posts/delete') {
        $postId = required_string($data, 'post_id', 64);
        $stmt = $db->prepare('SELECT author_id FROM social_posts WHERE id = ? LIMIT 1');
        $stmt->execute([$postId]);
        $authorId = $stmt->fetchColumn();
        if (!$authorId) fail(404, 'Post was not found.');
        if ((string)$authorId !== (string)$user['id']) fail(403, 'You can only delete your own posts.');
        $db->prepare('DELETE FROM social_posts WHERE id = ?')->execute([$postId]);
        ok(['deleted' => true]);
    }

    if ($method === 'GET' && $path === '/comments') {
        $postId = trim((string)($_GET['post_id'] ?? ''));
        if ($postId === '') fail(422, 'Provide a post_id query parameter.');
        $check = $db->prepare('SELECT 1 FROM social_posts WHERE id = ? LIMIT 1');
        $check->execute([$postId]);
        if (!$check->fetchColumn()) fail(404, 'Post was not found.');
        $stmt = $db->prepare(
            'SELECT c.id, c.post_id, c.author_id, c.content, c.created_at_ms, u.display_name
             FROM post_comments c INNER JOIN users u ON u.id = c.author_id
             WHERE c.post_id = ? ORDER BY c.created_at_ms ASC, c.id ASC LIMIT 100'
        );
        $stmt->execute([$postId]);
        ok(['comments' => array_map('social_comment_payload', $stmt->fetchAll(PDO::FETCH_ASSOC))]);
    }

    if ($method === 'POST' && $path === '/comments') {
        $postId = required_string($data, 'post_id', 64);
        $content = required_string($data, 'content', 3000);
        $check = $db->prepare('SELECT 1 FROM social_posts WHERE id = ? LIMIT 1');
        $check->execute([$postId]);
        if (!$check->fetchColumn()) fail(404, 'Post was not found.');
        $id = 'comment_' . bin2hex(random_bytes(12));
        $now = (int)floor(microtime(true) * 1000);
        $db->prepare(
            'INSERT INTO post_comments (id, post_id, author_id, content, created_at_ms) VALUES (?, ?, ?, ?, ?)'
        )->execute([$id, $postId, $user['id'], $content, $now]);
        $stmt = $db->prepare(
            'SELECT c.id, c.post_id, c.author_id, c.content, c.created_at_ms, u.display_name
             FROM post_comments c INNER JOIN users u ON u.id = c.author_id WHERE c.id = ? LIMIT 1'
        );
        $stmt->execute([$id]);
        ok(['comment' => social_comment_payload($stmt->fetch(PDO::FETCH_ASSOC))], 201);
    }

    if ($method === 'GET' && $path === '/stories') {
        $now = (int)floor(microtime(true) * 1000);
        $stmt = $db->prepare(
            'SELECT s.id, s.author_id, s.title, s.created_at_ms, u.display_name,
                EXISTS(SELECT 1 FROM story_views v WHERE v.story_id = s.id AND v.user_id = ?) AS is_viewed
             FROM user_stories s INNER JOIN users u ON u.id = s.author_id
             WHERE s.expires_at_ms > ? ORDER BY s.created_at_ms DESC LIMIT 100'
        );
        $stmt->execute([$user['id'], $now]);
        $stories = [];
        foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $story) {
            $stories[] = [
                'id' => (string)$story['id'],
                'author_id' => (string)$story['author_id'],
                'author_name' => (string)$story['display_name'],
                'author_avatar_color' => 4281639158,
                'story_title' => (string)$story['title'],
                'is_viewed' => (bool)$story['is_viewed'],
                'timestamp' => (int)$story['created_at_ms'],
            ];
        }
        ok(['stories' => $stories]);
    }

    if ($method === 'POST' && $path === '/stories') {
        $title = required_string($data, 'title', 500);
        $now = (int)floor(microtime(true) * 1000);
        $id = 'story_' . bin2hex(random_bytes(12));
        $db->prepare(
            'INSERT INTO user_stories (id, author_id, title, created_at_ms, expires_at_ms) VALUES (?, ?, ?, ?, ?)'
        )->execute([$id, $user['id'], $title, $now, $now + 86400000]);
        ok(['story' => [
            'id' => $id,
            'author_id' => (string)$user['id'],
            'author_name' => (string)$user['display_name'],
            'author_avatar_color' => 4281639158,
            'story_title' => $title,
            'is_viewed' => false,
            'timestamp' => $now,
        ]], 201);
    }

    if ($method === 'POST' && $path === '/stories/view') {
        $storyId = required_string($data, 'story_id', 64);
        $check = $db->prepare('SELECT 1 FROM user_stories WHERE id = ? AND expires_at_ms > ? LIMIT 1');
        $check->execute([$storyId, (int)floor(microtime(true) * 1000)]);
        if (!$check->fetchColumn()) fail(404, 'Story is no longer available.');
        $db->prepare(
            'INSERT INTO story_views (story_id, user_id, viewed_at_ms) VALUES (?, ?, ?)
             ON DUPLICATE KEY UPDATE viewed_at_ms = VALUES(viewed_at_ms)'
        )->execute([$storyId, $user['id'], (int)floor(microtime(true) * 1000)]);
        ok(['viewed' => true]);
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
