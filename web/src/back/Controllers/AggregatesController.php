<?php
declare(strict_types=1);
namespace Web\Controllers;
use Web\Db;
use Web\Util;

final class AggregatesController {
  private static function agg(Db $db, string $role): array {
    $from = Util::qp('from', null);
    $to   = Util::qp('to', null);
    $where = [];
    $params = [];
    if ($from) { $where[] = "v.official_published_at >= ?"; $params[] = $from; }
    if ($to)   { $where[] = "v.official_published_at <= ?"; $params[] = $to; }
    $wsql = $where ? ("WHERE " . implode(" AND ", $where)) : "";

    $link = ($role === 'presentateur') ? 'video_presentateur' : 'video_realisateur';

    $sql = "
      SELECT p.id as person_id, p.full_name,
             COALESCE(SUM(CASE WHEN sv.platform <> 'WORDPRESS' THEN ms.views_3s ELSE 0 END),0) AS total_views_3s
      FROM person p
      JOIN $link vp ON vp.person_id = p.id
      JOIN video v  ON v.id = vp.video_id
      LEFT JOIN source_video sv ON sv.video_id = v.id
      LEFT JOIN (
        SELECT x.source_video_id, x.views_3s
        FROM metric_snapshot x
        INNER JOIN (
          SELECT source_video_id, MAX(snapshot_at) AS last_snap
          FROM metric_snapshot GROUP BY source_video_id
        ) m ON m.source_video_id=x.source_video_id AND m.last_snap=x.snapshot_at
      ) ms ON ms.source_video_id = sv.id
      $wsql
      GROUP BY p.id, p.full_name
      ORDER BY total_views_3s DESC, p.full_name ASC
      LIMIT 1000
    ";
    $stmt = $db->pdo()->prepare($sql);
    $stmt->execute($params);
    return $stmt->fetchAll();
  }

  public static function presenters(Db $db): array   { return self::agg($db, 'presentateur'); }
  public static function realisateurs(Db $db): array { return self::agg($db, 'realisateur'); }
}
