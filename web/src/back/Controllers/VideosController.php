<?php
declare(strict_types=1);
namespace Web\Controllers;
use Web\Db;
use Web\Util;

final class VideosController {
  public static function list(Db $db): array {
    [$page,$size,$offset] = Util::paging();
    $q    = Util::qp('q','');
    $from = Util::qp('from', null);
    $to   = Util::qp('to', null);

    $where = [];
    $params = [];
    if ($q !== '') { $where[] = "(LOWER(v.canonical_title) LIKE ? OR LOWER(sv.title) LIKE ?)"; $params[]="%".strtolower($q)."%"; $params[]="%".strtolower($q)."%"; }
    if ($from) { $where[] = "v.official_published_at >= ?"; $params[] = $from; }
    if ($to)   { $where[] = "v.official_published_at <= ?"; $params[] = $to; }

    $wsql = $where ? ("WHERE " . implode(" AND ", $where)) : "";

    $stmtC = $db->pdo()->prepare("
      SELECT COUNT(DISTINCT v.id) AS c
      FROM video v
      LEFT JOIN source_video sv ON sv.video_id = v.id
      $wsql
    ");
    $stmtC->execute($params);
    $total = (int)$stmtC->fetch()['c'];

    $stmt = $db->pdo()->prepare("
      SELECT v.id, v.canonical_title, v.official_published_at,
             COALESCE(SUM(CASE WHEN sv.platform <> 'WORDPRESS' THEN ms.views_3s ELSE 0 END),0) AS total_views_3s,
             JSON_ARRAYAGG(JSON_OBJECT(
               'platform', sv.platform,
               'platform_source_id', sv.platform_source_id,
               'title', sv.title,
               'permalink', sv.permalink_url,
               'is_teaser', sv.is_teaser,
               'latest_views_3s', COALESCE(ms.views_3s,0)
             )) AS sources
      FROM video v
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
      GROUP BY v.id
      ORDER BY v.official_published_at DESC
      LIMIT $size OFFSET $offset
    ");
    $stmt->execute($params);
    $rows = $stmt->fetchAll();

    return ['page'=>$page,'pageSize'=>$size,'total'=>$total,'items'=>$rows];
  }
}
