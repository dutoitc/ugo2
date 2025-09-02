<?php
/**
 * Router minimaliste (API v1).
 * - Dispatch exact sur méthode + chemin normalisé.
 * - 405 si chemin existe avec autre méthode.
 * - 404 sinon.
 * - OPTIONS renvoie 204 (préflight CORS basique).
 */

declare(strict_types=1);

// Dépendances de base
require_once __DIR__ . '/Config.php';
require_once __DIR__ . '/Db.php';
require_once __DIR__ . '/Auth.php';
require_once __DIR__ . '/Util.php';

// Contrôleurs
require_once __DIR__ . '/Controllers/HealthController.php';
require_once __DIR__ . '/Controllers/SourcesIngestController.php';
require_once __DIR__ . '/Controllers/MetricsIngestController.php';
require_once __DIR__ . '/Controllers/OverridesController.php';
require_once __DIR__ . '/Controllers/VideosController.php';
require_once __DIR__ . '/Controllers/AggregatesController.php';

final class Router
{
    /** @var array<array{0:string,1:string,2:array{0:string,1:string}}>} */
    private array $routes = [];

    private Db $db;
    private Auth $auth;

    public function __construct()
    {
        $cfg = Config::load();
        $this->db   = new Db($cfg['db'] ?? []);
        $this->auth = new Auth($cfg);

        // Table de routage
        $this->routes = [
            // Health
            ['GET',  '/api/v1/health',                  ['Controllers\\HealthController', 'health']],

            // Ingestion SOURCES
            ['POST', '/api/v1/sources:batchUpsert',     ['Controllers\\SourcesIngestController', 'batchUpsert']],
            ['POST', '/api/v1/sources:filterMissing',   ['Controllers\\SourcesIngestController', 'filterMissing']],

            // Ingestion METRICS
            ['POST', '/api/v1/metrics:batchUpsert',     ['Controllers\\MetricsIngestController', 'batchUpsert']],

            // Overrides (admin)
            ['POST', '/api/v1/overrides:apply',         ['Controllers\\OverridesController', 'apply']],

            // Lecture (public/admin)
            ['GET',  '/api/v1/videos',                  ['Controllers\\VideosController', 'list']],
            ['GET',  '/api/v1/aggregates/presenters',   ['Controllers\\AggregatesController', 'presenters']],
            ['GET',  '/api/v1/aggregates/directors',    ['Controllers\\AggregatesController', 'directors']],
        ];
    }

    private static function normalizePath(string $p): string
    {
        // supprime multiple slashes et trailing slash (sauf racine)
        $p = preg_replace('#/+#', '/', $p) ?? $p;
        if ($p !== '/' && str_ends_with($p, '/')) {
            $p = rtrim($p, '/');
        }
        return $p;
    }

    public function dispatch(): void
    {
        $method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

        // Préflight CORS simple
        if ($method === 'OPTIONS') {
            header('Access-Control-Allow-Origin: *');
            header('Access-Control-Allow-Methods: GET,POST,OPTIONS');
            header('Access-Control-Allow-Headers: Content-Type, X-API-KEY, X-API-TS, X-API-NONCE, X-API-SIG, Idempotency-Key');
            http_response_code(204);
            return;
        }

        $uriPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?? '/';
        $path    = self::normalizePath($uriPath);

        foreach ($this->routes as [$m, $r, $handler]) {
            if ($m === $method && $r === $path) {
                [$class, $fn] = $handler;
                /** @var object $controller */
                $controller = new $class($this->db, $this->auth);
                $controller->$fn();
                return;
            }
        }

        // Si le chemin existe mais pas la méthode -> 405
        $allowed = [];
        foreach ($this->routes as [$m, $r, $handler]) {
            if ($r === $path) $allowed[] = $m;
        }
        if (!empty($allowed)) {
            header('Content-Type: application/json; charset=utf-8');
            header('Allow: ' . implode(',', array_unique($allowed)));
            http_response_code(405);
            echo json_encode(['ok' => false, 'error' => 'method_not_allowed'], JSON_UNESCAPED_UNICODE);
            return;
        }

        // 404
        header('Content-Type: application/json; charset=utf-8');
        http_response_code(404);
        echo json_encode(['ok' => false, 'error' => 'not_found'], JSON_UNESCAPED_UNICODE);
    }
}
