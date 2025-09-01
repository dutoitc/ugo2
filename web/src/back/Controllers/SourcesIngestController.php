<?php
declare(strict_types=1);
namespace Web\Controllers;
use Web\Db;
use Web\Util;
use PDO;

final class SourcesIngestController {
  public static function batchUpsert(Db $db, $auth): array {
    $items = Util::jsonInput();
    if (!is_array($items)) { http_response_code(400); return ['error'=>'bad_payload']; }
    $sql = "
      INSERT INTO source_video(platform, platform_source_id, title, description, permalink_url, media_type, duration_seconds, published_at, is_teaser, video_id, locked)
      VALUES (:platform, :platform_source_id, :title, :description, :permalink_url, :media_type, :duration_seconds, :published_at, :is_teaser, :video_id, :locked)
      ON DUPLICATE KEY UPDATE
        title=VALUES(title),
        description=VALUES(description),
        permalink_url=VALUES(permalink_url),
        media_type=VALUES(media_type),
        duration_seconds=VALUES(duration_seconds),
        published_at=VALUES(published_at),
        is_teaser=IF(source_video.locked=1, source_video.is_teaser, VALUES(is_teaser)),
        video_id = IF(source_video.locked=1, source_video.video_id, VALUES(video_id))
    ";
    $stmt = $db->pdo()->prepare($sql);

    $n=0;
    $db->tx(function(PDO $pdo) use ($items, $stmt, &$n) {
      foreach ($items as $it) {
        $stmt->execute([
          ':platform' => $it['platform'] ?? null,
          ':platform_source_id' => $it['platform_source_id'] ?? null,
          ':title' => $it['title'] ?? null,
          ':description' => $it['description'] ?? null,
          ':permalink_url' => $it['permalink_url'] ?? null,
          ':media_type' => $it['media_type'] ?? null,
          ':duration_seconds' => $it['duration_seconds'] ?? null,
          ':published_at' => $it['published_at'] ?? null,
          ':is_teaser' => (int)($it['is_teaser'] ?? 0),
          ':video_id' => $it['video_id'] ?? null,
          ':locked' => (int)($it['locked'] ?? 0),
        ]);
        $n++;
      }
    });
    return ['ok'=>true,'upserts'=>$n];
  }
}
