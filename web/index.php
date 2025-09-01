<?php
declare(strict_types=1);

require_once __DIR__ . '/src/back/Config.php';
require_once __DIR__ . '/src/back/Db.php';
require_once __DIR__ . '/src/back/Auth.php';
require_once __DIR__ . '/src/back/Router.php';
require_once __DIR__ . '/src/back/Util.php';

require_once __DIR__ . '/src/back/Controllers/HealthController.php';
require_once __DIR__ . '/src/back/Controllers/VideosController.php';
require_once __DIR__ . '/src/back/Controllers/AggregatesController.php';
require_once __DIR__ . '/src/back/Controllers/SourcesIngestController.php';
require_once __DIR__ . '/src/back/Controllers/MetricsIngestController.php';
require_once __DIR__ . '/src/back/Controllers/OverridesController.php';

use Web\Config;
use Web\Db;
use Web\Auth;
use Web\Router;
use Web\Controllers\{HealthController, VideosController, AggregatesController, SourcesIngestController, MetricsIngestController, OverridesController};

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
if (strpos($path, '/api/') !== 0) {
  http_response_code(404);
  header('Content-Type: application/json; charset=utf-8');
  echo json_encode(['error'=>'not_found']); exit;
}

header('Content-Type: application/json; charset=utf-8');
$config = Config::load();
$db     = new Db($config['db']);
$auth   = new Auth($config['ingestKeys'], $db);

$router = new Router($_SERVER['REQUEST_METHOD'] ?? 'GET', $_SERVER['REQUEST_URI'] ?? '/');

$router->get('/api/v1/health', fn() => HealthController::health($db));
$router->get('/api/v1/capabilities', fn() => HealthController::capabilities());
$router->get('/api/v1/videos', fn() => VideosController::list($db));
$router->get('/api/v1/aggregates/presenters', fn() => AggregatesController::presenters($db));
$router->get('/api/v1/aggregates/realisateurs', fn() => AggregatesController::realisateurs($db));

$router->post('/api/v1/sources:batchUpsert', fn() => $auth->guardIngest(function() use ($db, $auth) {
  return SourcesIngestController::batchUpsert($db, $auth);
}));

$router->post('/api/v1/metrics:batchUpsert', fn() => $auth->guardIngest(function() use ($db, $auth) {
  return MetricsIngestController::batchUpsert($db, $auth);
}));

$router->post('/api/v1/overrides:apply', fn() => $auth->guardIngest(function() use ($db, $auth) {
  return OverridesController::apply($db, $auth);
}));

$router->run();
