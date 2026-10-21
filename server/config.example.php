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
    // Optional OpenAI-compatible chat-completions provider for Echo AI.
    // Keep this API key in the private server config, never in the Android app.
    'ai_api_key' => '',
    'ai_endpoint' => 'https://api.openai.com/v1/chat/completions',
    'ai_model' => 'gpt-4o-mini',
    // Optional browser origins. Android native requests do not depend on CORS.
    // Example: ['https://yourdomain.com']
    'allowed_origins' => [],
];
