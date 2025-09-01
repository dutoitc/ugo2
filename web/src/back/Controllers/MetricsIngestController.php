<?php
declare(strict_types=1);
namespace Web\Controllers;
use Web\Db;
use Web\Util;
use PDO;

final class MetricsIngestController {
  public static function batchUpsert(Db $db, $auth): array {
    $items = Util::jsonInput();
    if (!is_array($items)) { http_response_code(400); return ['error'=>'bad_payload']; }

    $find = $db->pdo()->prepare("SELECT id FROM source_video WHERE platform=? AND platform_source_id=?");
    $ins = $db->pdo()->prepare("
      INSERT INTO metric_snapshot(source_video_id, snapshot_at, views_3s, views_platform_raw, comments, shares, reactions, saves)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON DUPLICATE KEY UPDATE
        views_3s=VALUES(views_3s),
        views_platform_raw=VALUES(views_platform_raw),
        comments=VALUES(comments),
        shares=VALUES(shares),
        reactions=VALUES(reactions),
        saves=VALUES(saves)
    ");
    $n=0; $skipped=0;

    $db->tx(function(PDO $pdo) use ($items, $find, $ins, &$n, &$skipped) {
      foreach ($items as $it) {
        $find->execute([$it['platform'] ?? '', $it['platform_source_id'] ?? '']);
        $row = $find->fetch();
        if (!$row) { $skipped++; continue; }
        $sid = (int)$row['id'];
        $ins->execute([
          $sid,
          $it['snapshot_at'] ?? null,
          (int)($it['views_3s'] ?? 0),
          (int)($it['views_platform_raw'] ?? 0),
          (int)($it['comments'] ?? 0),
          (int)($it['shares'] ?? 0),
          (int)($it['reactions'] ?? 0),
          (int)($it['saves'] ?? 0),
        ]);
        $n++;
      }
    });

    return ['ok'=>true,'upserts'=>$n,'skipped_no_source'=>$skipped];
  }
}
