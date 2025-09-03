<?php
declare(strict_types=1);
namespace Web\Controllers;

use Web\Db;
use Web\Util;

final class VideosController {

  public static function list(Db $db): array {
    [$page, $size, $offset] = Util::paging();
    $q           = Util::qp('q', '');
    $from        = Util::qp('from', null);
    $to          = Util::qp('to', null);
    $platform    = Util::qp('platform', null);       // YOUTUBE|FACEBOOK|INSTAGRAM|WORDPRESS
    $withMetrics = Util::qp('withMetrics', '0') === '1';

    $where  = [];
    $params = [];

    if ($q !== '') {
      $where[] = "(LOWER(v.canonical_title) LIKE ? OR LOWER(sv.title) LIKE ?)";
      $params[] = "%".strtolower($q)."%";
      $params[] = "%".strtolower($q)."%";
    }
    if ($from) { $where[] = "v.official_published_at >= ?"; $params[] = $from; }
    if ($to)   { $where[] = "v.official_published_at <= ?"; $params[] = $to; }
    if ($platform) { $where[] = "sv.platform = ?"; $params[] = $platform; }

    $wsql = $where ? ("WHERE " . implode(" AND ", $where)) : "";

    $pdo = $db->pdo();

    // Total distinct videos
    $stmtC = $pdo->prepare("
      SELECT COUNT(DISTINCT v.id) AS c
      FROM video v
      LEFT JOIN source_video sv ON sv.video_id = v.id
      $wsql
    ");
    $stmtC->execute($params);
    $total = (int)($stmtC->fetch()['c'] ?? 0);

    // Jointure metrics (facultative)
    $metricsJoin = $withMetrics ? "
      LEFT JOIN (
        SELECT x.source_video_id, x.views_3s
        FROM metric_snapshot x
        INNER JOIN (
          SELECT source_video_id, MAX(snapshot_at) AS last_snap
          FROM metric_snapshot GROUP BY source_video_id
        ) m ON m.source_video_id = x.source_video_id AND m.last_snap = x.snapshot_at
      ) ms ON ms.source_video_id = sv.id
    " : "";

    $totalViewsExpr = $withMetrics
      ? "COALESCE(SUM(CASE WHEN sv.platform <> 'WORDPRESS' THEN ms.views_3s ELSE 0 END),0)"
      : "0";

    // Page de résultats
    $stmt = $pdo->prepare("
      SELECT
        v.id,
        v.canonical_title,
        v.canonical_description,
        v.official_published_at,
        $totalViewsExpr AS total_views_3s,
        /* Assure un tableau vide [] si aucune source */
        COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
          'platform', sv.platform,
          'platform_source_id', sv.platform_source_id,
          'title', sv.title,
          'permalink', sv.permalink_url,
          'is_teaser', sv.is_teaser,
          'published_at', sv.published_at" . ($withMetrics ? ",
          'latest_views_3s', COALESCE(ms.views_3s,0)" : "") . "
        )), JSON_ARRAY()) AS sources
      FROM video v
      LEFT JOIN source_video sv ON sv.video_id = v.id
      $metricsJoin
      $wsql
      GROUP BY v.id
      ORDER BY v.official_published_at DESC, v.id DESC
      LIMIT $size OFFSET $offset
    ");
    $stmt->execute($params);
    $rows = $stmt->fetchAll(\PDO::FETCH_ASSOC);

    // Normalisation de la sortie:
    // - timezone locale
    // - décoder JSON_ARRAYAGG en tableau PHP (selon PDO, ça peut revenir en string)
    foreach ($rows as &$r) {
      $r['published_at_local'] = self::toLocalIso($r['official_published_at'], 'Europe/Zurich');
      if (is_string($r['sources'])) {
        $dec = json_decode($r['sources'], true);
        $r['sources'] = is_array($dec) ? $dec : [];
      } elseif (!is_array($r['sources'])) {
        $r['sources'] = [];
      }
    }

    return ['page'=>$page,'pageSize'=>$size,'total'=>$total,'items'=>$rows];
  }

  public static function get(Db $db): array {
    // 1) Extraire l’ID depuis l’URL ou ?id=
    $id = 0;
    // a) query param de secours
    $qid = Util::qp('id', null);
    if ($qid !== null) { $id = (int)$qid; }
    // b) sinon, piocher dans REQUEST_URI (ex: /api/v1/videos/123)
    if ($id === 0 && isset($_SERVER['REQUEST_URI'])) {
      if (preg_match('#/api/v1/videos/(\d+)#', $_SERVER['REQUEST_URI'], $m)) {
        $id = (int)$m[1];
      }
    }
    if ($id <= 0) {
      return ['error' => 'bad_request', 'message' => 'Missing or invalid id'];
    }

    // 2) Charger la vidéo + sources + dernier snapshot/ source
    $pdo = $db->pdo();
    $stmt = $pdo->prepare("
      SELECT
        v.id,
        v.canonical_title,
        v.canonical_description,
        v.official_published_at,
        COALESCE(SUM(CASE WHEN sv.platform <> 'WORDPRESS' THEN ms.views_3s ELSE 0 END),0) AS total_views_3s,
        COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
          'id', sv.id,
          'platform', sv.platform,
          'platform_source_id', sv.platform_source_id,
          'title', sv.title,
          'permalink', sv.permalink_url,
          'is_teaser', sv.is_teaser,
          'published_at', sv.published_at,
          'latest_views_3s', COALESCE(ms.views_3s, 0)
        )), JSON_ARRAY()) AS sources
      FROM video v
      LEFT JOIN source_video sv ON sv.video_id = v.id
      LEFT JOIN (
        SELECT x.source_video_id, x.views_3s
        FROM metric_snapshot x
        INNER JOIN (
          SELECT source_video_id, MAX(snapshot_at) AS last_snap
          FROM metric_snapshot GROUP BY source_video_id
        ) m ON m.source_video_id = x.source_video_id AND m.last_snap = x.snapshot_at
      ) ms ON ms.source_video_id = sv.id
      WHERE v.id = ?
      GROUP BY v.id
    ");
    $stmt->execute([$id]);
    $row = $stmt->fetch(\PDO::FETCH_ASSOC);

    if (!$row) {
      return ['error' => 'not_found', 'message' => 'Video not found'];
    }

    // 3) Normaliser la sortie (TZ locale + sources = tableau + agrégats par plateforme)
    $row['published_at_local'] = self::toLocalIso($row['official_published_at'] ?? null, 'Europe/Zurich');

    $sources = [];
    if (isset($row['sources']) && is_string($row['sources'])) {
      $dec = json_decode($row['sources'], true);
      $sources = is_array($dec) ? $dec : [];
    } elseif (is_array($row['sources'])) {
      $sources = $row['sources'];
    }

    // Ajouter published_at_local pour chaque source
    foreach ($sources as &$s) {
      $s['published_at_local'] = self::toLocalIso($s['published_at'] ?? null, 'Europe/Zurich');
    }
    unset($s);

    // Agrégats par plateforme
    $byPlatform = [];
    foreach ($sources as $s) {
      $p = (string)($s['platform'] ?? '');
      if ($p === '') continue;
      if (!isset($byPlatform[$p])) {
        $byPlatform[$p] = [
          'platform' => $p,
          'count' => 0,
          'teasers' => 0,
          'total_latest_views_3s' => 0,
          'first_published_at_local' => null,
          'last_published_at_local'  => null,
          'primary_permalink' => null
        ];
      }
      $byPlatform[$p]['count']++;
      if (!empty($s['is_teaser'])) $byPlatform[$p]['teasers']++;
      $byPlatform[$p]['total_latest_views_3s'] += (int)($s['latest_views_3s'] ?? 0);

      $pl = $s['published_at_local'] ?? null;
      if ($pl) {
        if ($byPlatform[$p]['first_published_at_local'] === null || $pl < $byPlatform[$p]['first_published_at_local'])
          $byPlatform[$p]['first_published_at_local'] = $pl;
        if ($byPlatform[$p]['last_published_at_local'] === null || $pl > $byPlatform[$p]['last_published_at_local'])
          $byPlatform[$p]['last_published_at_local'] = $pl;
      }
      if ($byPlatform[$p]['primary_permalink'] === null && empty($s['is_teaser'])) {
        $byPlatform[$p]['primary_permalink'] = $s['permalink'] ?? null;
      }
    }

    return [
      'id' => (int)$row['id'],
      'title' => (string)$row['canonical_title'],
      'description' => (string)($row['canonical_description'] ?? ''),
      'published_at' => $row['official_published_at'],
      'published_at_local' => $row['published_at_local'],
      'total_views_3s' => (int)($row['total_views_3s'] ?? 0),
      'sources' => $sources,
      'sources_by_platform' => array_values($byPlatform)
    ];
  }


  private static function toLocalIso(?string $utc, string $tz): ?string {
    if (!$utc) return null;
    try {
      $d = new \DateTimeImmutable($utc, new \DateTimeZone('UTC'));
      return $d->setTimezone(new \DateTimeZone($tz))->format('c');
    } catch (\Throwable $e) {
      return $utc;
    }
  }
}
