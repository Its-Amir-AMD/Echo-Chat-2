<?php
declare(strict_types=1);

/*
 * Copy this file to ../config.php (one directory above public/) on the server.
 * Never commit real database passwords or private credentials.
 */
return [
    'db_host' => 'localhost',
    'db_name' => 'echochat',
    'db_user' => 'echochat_user',
    'db_password' => 'CHANGE_ME_TO_A_LONG_RANDOM_PASSWORD',
    'token_ttl_seconds' => 2592000,
    // Optional browser origins. Android native requests do not depend on CORS.
    // Example: ['https://yourdomain.com']
    'allowed_origins' => [],
];
