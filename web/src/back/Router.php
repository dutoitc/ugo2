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
        // Chargement config + services de base
        $cfg        = Config::load();
        $this->db   = new Db($cfg['db'] ?? []);
        $this->auth = new Auth($cfg['ingestKeys'] ?? [], $this->db);


        // Déclaration des routes
        $this->routes = [
            // Health
            ['GET',  '/api/v1/health',                ['Web\\Controllers\\HealthController', 'health']],

            // Ingestion SOURCES
            ['POST', '/api/v1/sources:filterMissing', ['Web\\Controllers\\SourcesIngestController', 'filterMissing']],
            ['POST', '/api/v1/sources:batchUpsert',   ['Web\\Controllers\\SourcesIngestController', 'batchUpsert']],

            // Others
            ['POST', '/api/v1/reconcile:run',         ['Web\\Controllers\\ReconcileController', 'run']],
            ['POST', '/api/v1/overrides:apply',       ['Web\\Controllers\\OverridesController', 'apply']],

            // Ingestion METRICS
            ['POST', '/api/v1/metrics:batchUpsert',   ['Web\\Controllers\\MetricsIngestController', 'batchUpsert']],

            // Lecture
            ['GET',  '/api/v1/videos',                ['Web\\Controllers\\VideosController', 'list']],
            ['GET',  '/api/v1/video',                 ['Web\\Controllers\\VideosController', 'get']],
            ['GET',  '/api/v1/aggregates/presenters', ['Web\\Controllers\\AggregatesController', 'presenters']],
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
        // CORS + pré-vol
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

        // Petit log (utile en dév)
        $ct = $_SERVER['CONTENT_TYPE']  ?? '';
        $cl = $_SERVER['CONTENT_LENGTH'] ?? '0';
        error_log(sprintf('[API] %s %s (CT=%s, CL=%s)', $method, $path, $ct, $cl));

        try {
            foreach ($this->routes as [$m, $r, $handler]) {
                if ($m === $method && $r === $path) {
                    [$class, $fn] = $handler;

                    // Méthode statique qui retourne un array → sérialisation ici
                    if (method_exists($class, $fn)) {
                        $ref = new \ReflectionMethod($class, $fn);
                        if ($ref->isStatic()) {
                            $out = $class::$fn($this->db);
                            if (is_array($out)) {
                                Http::json($out, 200);
                            } else {
                                Http::json(['ok' => true], 200);
                            }
                            return;
                        }
                    }

                    // Sinon instance (le contrôleur écrit la réponse JSON lui-même)
                    $controller = new $class($this->db, $this->auth);
                    if (!method_exists($controller, $fn)) {
                        Http::json(['error' => 'not_implemented'], 501);
                        return;
                    }
                    $controller->$fn();
                    return;
                }
            }

            // 405 si chemin existe mais mauvaise méthode
            $allowed = [];
            foreach ($this->routes as [$m, $r]) {
                if ($r === $path) $allowed[] = $m;
            }
            if ($allowed) {
                header('Allow: ' . implode(',', array_unique($allowed)));
                Http::json(['error' => 'method_not_allowed'], 405);
                return;
            }

            // 404
            Http::json(['error' => 'not_found', 'path' => $path], 404);
        } catch (\Throwable $e) {
            error_log('[API] ERROR ' . $e->getMessage());
            Http::json(['error' => 'internal_error', 'message' => $e->getMessage()], 500);
        }
    }
}
