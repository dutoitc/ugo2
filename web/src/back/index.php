<?php
declare(strict_types=1);

require_once __DIR__ . '/Config.php';
require_once __DIR__ . '/Db.php';
require_once __DIR__ . '/lib/Http.php';
require_once __DIR__ . '/lib/SensitiveData.php';
require_once __DIR__ . '/Auth.php';
require_once __DIR__ . '/Router.php';
require_once __DIR__ . '/Util.php';

// Helpers & contrôleurs
require_once __DIR__ . '/controllers/sql/MaterializedViewsSql.php';
require_once __DIR__ . '/controllers/videos/Paginator.php';
require_once __DIR__ . '/controllers/videos/Sorts.php';
require_once __DIR__ . '/controllers/videos/VideosListQuery.php';
require_once __DIR__ . '/controllers/videos/VideosListRequestFactory.php';
require_once __DIR__ . '/controllers/videos/VideosRepository.php';
require_once __DIR__ . '/controllers/videos/VideosSqlBuilder.php';
require_once __DIR__ . '/controllers/HealthController.php';
require_once __DIR__ . '/controllers/SourcesIngestController.php';
require_once __DIR__ . '/controllers/MetricsIngestController.php';
require_once __DIR__ . '/controllers/VideosController.php';
require_once __DIR__ . '/controllers/VideoTimeseriesController.php';
require_once __DIR__ . '/controllers/ReconcileController.php';
require_once __DIR__ . '/controllers/OverridesController.php';
require_once __DIR__ . '/services/MetricsIngestService.php';
require_once __DIR__ . '/services/HealthStateService.php';
require_once __DIR__ . '/services/MaterializedRefreshService.php';
require_once __DIR__ . '/services/TrendService.php';
require_once __DIR__ . '/repositories/MetricsSnapshotRepository.php';
require_once __DIR__ . '/repositories/SourceVideoRepository.php';
require_once __DIR__ . '/domain/MetricsSnapshot.php';


$router = new \Web\Router();
$router->dispatch();
