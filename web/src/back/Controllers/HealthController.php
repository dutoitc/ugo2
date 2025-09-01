<?php
declare(strict_types=1);
namespace Web\Controllers;
use Web\Db;

final class HealthController {
  public static function health(Db $db): array {
    $db->pdo()->query('SELECT 1');
    return ['ok'=>true, 'version'=>'v1'];
  }
  public static function capabilities(): array {
    return [
      'schemaVersion' => 1,
      'features' => ['ingest.bulk','idempotency','totals.excl.wp'],
      'limits' => ['maxBatch'=>1000]
    ];
  }
}
