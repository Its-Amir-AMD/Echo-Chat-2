<?php
declare(strict_types=1);

/*
 * Copy this file to config.php beside the public/ directory on the server.
 * If deploying public/ as a flat folder under public_html/, put config.php in
 * the hosting account's private home directory outside the web root.
 * Never commit real database passwords or private credentials.
 */
return [
    'db_host' => 'localhost',
    'db_name' => 'echochat',
    'db_user' => 'echochat_user',
    'db_password' => 'CHANGE_ME_TO_A_LONG_RANDOM_PASSWORD',
    'token_ttl_seconds' => 2592000,
    // Replace with a long random secret; the database password is a fallback.
    'rate_limit_hmac_key' => 'CHANGE_ME_TO_A_RANDOM_64_CHARACTER_SECRET',
    // Optional OpenAI-compatible endpoint. Leave empty to disable Echo AI until configured.
    'ai_endpoint' => '',
    'ai_api_key' => '',
    'ai_model' => 'gpt-4o-mini',
    // Optional browser origins. Android native requests do not depend on CORS.
    // Example: ['https://yourdomain.com']
    'allowed_origins' => [],
];
