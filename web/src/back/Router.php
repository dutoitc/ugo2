<?php
declare(strict_types=1);

namespace Web;

use Web\Lib\Http;

final class Router
{
    private Db $db;
    private Auth $auth;

    /** @var array<array{0:string,1:string,2:array{0:string,1:string}}>} */
    private array $routes = [];

    public function __construct()
    {
        $cfg        = Config::load();
        $this->db   = new Db($cfg['db'] ?? []);
        $this->auth = new Auth($cfg);

        $this->routes = [
            // Health
            ['GET',  '/api/v1/health',                ['Web\\Controllers\\HealthController', 'health']],

            // Ingestion SOURCES
            ['POST', '/api/v1/sources:filterMissing', ['Web\\Controllers\\SourcesIngestController', 'filterMissing']],
            ['POST', '/api/v1/sources:batchUpsert',   ['Web\\Controllers\\SourcesIngestController', 'batchUpsert']],

            // Ingestion METRICS (écrit dans metric_snapshot)
            ['POST', '/api/v1/metrics:batchUpsert',   ['Web\\Controllers\\MetricsIngestController', 'batchUpsert']],

            // Lecture (tes contrôleurs statiques qui retournent un array)
            ['GET',  '/api/v1/videos',                ['Web\\Controllers\\VideosController', 'list']],
            ['GET',  '/api/v1/aggregates/presenters', ['Web\\Controllers\\AggregatesController', 'presenters']],
            // "directors" côté API → méthode "realisateurs" côté code
            ['GET',  '/api/v1/aggregates/directors',  ['Web\\Controllers\\AggregatesController', 'realisateurs']],
        ];
    }

    private static function normalize(string $path): string
    {
        $p = preg_replace('#/+#', '/', $path) ?? $path;
        if ($p !== '/' && str_ends_with($p, '/')) $p = rtrim($p, '/');
        return $p;
    }

    public function dispatch(): void
    {
        $method = $_SERVER['REQUEST_METHOD'] ?? 'GET';

        if ($method === 'OPTIONS') {
            header('Access-Control-Allow-Origin: *');
            header('Access-Control-Allow-Methods: GET,POST,OPTIONS');
            header('Access-Control-Allow-Headers: Content-Type, X-API-KEY, X-API-TS, X-API-NONCE, X-API-SIG, Idempotency-Key');
            http_response_code(204);
            return;
        }

        $uriPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?? '/';
        $path    = self::normalize($uriPath);

        foreach ($this->routes as [$m, $r, $handler]) {
            if ($m === $method && $r === $path) {
                [$class, $fn] = $handler;

                // Si la méthode est statique et retourne un array -> on sérialise ici.
                if (method_exists($class, $fn)) {
                    $ref = new \ReflectionMethod($class, $fn);
                    if ($ref->isStatic()) {
                        $out = $class::$fn($this->db);
                        if (is_array($out)) {
                            Http::json($out, 200);
                        } else {
                            Http::json(['ok'=>true], 200);
                        }
                        return;
                    }
                }

                // Sinon : instance qui gère elle-même la réponse (echo/json)
                $controller = new $class($this->db, $this->auth);
                $controller->$fn();
                return;
            }
        }

        // 405 si chemin existe avec autre méthode
        $allowed = [];
        foreach ($this->routes as [$m, $r]) if ($r === $path) $allowed[] = $m;
        if ($allowed) {
            header('Content-Type: application/json; charset=utf-8');
            header('Allow: ' . implode(',', array_unique($allowed)));
            http_response_code(405);
            echo json_encode(['error' => 'method_not_allowed'], JSON_UNESCAPED_UNICODE);
            return;
        }

        header('Content-Type: application/json; charset=utf-8');
        http_response_code(404);
        echo json_encode(['error' => 'not_found ' . $path . ' / ' . $uriPath . ' / ' . $path], JSON_UNESCAPED_UNICODE);
    }
}
