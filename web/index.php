<?php
declare(strict_types=1);

/**
 * Front controller minimal :
 * - /api/...  -> Router PHP (API JSON)
 * - tout le reste -> front statique (src/front/index.html)
 */

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?? '/';
$path = preg_replace('#/+#', '/', $path) ?? '/';

if (str_starts_with($path, '/api/')) {
    require __DIR__ . '/src/back/Router.php';
    (new Router())->dispatch();
    exit;
}

// Sert le front (single page)
$front = __DIR__ . '/src/front/index.html';
if (is_file($front)) {
    header('Content-Type: text/html; charset=utf-8');
    readfile($front);
    exit;
}

// Fallback
http_response_code(404);
header('Content-Type: text/plain; charset=utf-8');
echo "Front not found (src/front/index.html)";
