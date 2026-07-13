<?php
declare(strict_types=1);

namespace Web\Controllers\Videos;

use Web\Services\TrendService;
use Web\Controllers\Sql\MaterializedViewsSql;
use PDO;

final class VideosRepository
{

    private TrendService $trend;

    public function __construct(
            private PDO $pdo
    ) {
        $this->trend = new TrendService($pdo);
    }

    /**
     * @return array{
     *     total:int,
     *     sum:array<string,int>,
     *     items:list<array<string,mixed>>
     * }
     */
    public function findList(VideosListQuery $q): array
    {
        $ts0 = microtime(true); // DBG

        //
        // COUNT
        //
        $count = VideosSqlBuilder::sqlCount($q);
        $stC = $this->pdo->prepare($count['sql']);
        $stC->execute($count['args']);
        $total = (int)$stC->fetchColumn();
//         error_log("DBG1 - " . round((microtime(true) - $ts0)*1000,1) . " ms");
//         error_log("SQL COUNT: ".$count['sql']." | ".json_encode($count['args']));


        //
        // PAGE
        //
        $page = VideosSqlBuilder::sqlPage($q);
        $stP = $this->pdo->prepare($page['sql']);
        $stP->execute($page['args']);
//         error_log("DBG2 - " . round((microtime(true) - $ts0)*1000,1) . " ms");
//         error_log("SQL PAGE: ".$page['sql']." | ".json_encode($page['args']));


        $items = [];
        while (true) {
            $row = $stP->fetch(PDO::FETCH_ASSOC);
            if ($row === false) {
                break;
            }
            $items[] = $this->mapItem($row);
        }
//         error_log("DBG3 - " . round((microtime(true) - $ts0)*1000,1) . " ms");



        //
        // SUM
        //
        $sumSql = VideosSqlBuilder::sqlSum($q);
        $stS = $this->pdo->prepare($sumSql['sql']);
        $stS->execute($sumSql['args']);
        $sumRow = $stS->fetch(PDO::FETCH_ASSOC);
//         error_log("DBG4 - " . round((microtime(true) - $ts0)*1000,1) . " ms");
//         error_log("SQL SUM: ".$sumSql['sql']);

        if (!$sumRow) {
            $sumRow = ['sum_yt'=>0,'sum_fb'=>0,'sum_ig'=>0,'sum_tt'=>0];
        }

        $sum = [
            'youtube'   => (int)$sumRow['sum_yt'],
            'facebook'  => (int)$sumRow['sum_fb'],
            'instagram' => (int)$sumRow['sum_ig'],
            'tiktok'    => (int)$sumRow['sum_tt'],
        ];

//         error_log("DBG5 - " . round((microtime(true) - $ts0)*1000,1) . " ms");

        return [
            'total' => $total,
            'sum'   => $sum,
            'items' => $items,
        ];
    }

    /**
     * @param array<string,mixed> $row
     * @return array<string,mixed>
     */
    private function mapItem(array $row): array
    {
        $trend = $this->trend->computeTrend((int)$row['video_id']);

        return [
            'id'                   => (int)$row['video_id'],
            'slug'                 => $row['slug'],
            'title'                => $row['video_title'],
            'published_at'         => $row['video_published_at'],

            'length_seconds'       => $row['canonical_length_seconds'] !== null
                                        ? (int)$row['canonical_length_seconds']
                                        : null,

            'views_native_sum'     => $row['views_native_sum'] !== null ? (int)$row['views_native_sum'] : null,
            'likes_sum'            => $row['likes_sum']        !== null ? (int)$row['likes_sum']        : null,
            'comments_sum'         => $row['comments_sum']     !== null ? (int)$row['comments_sum']     : null,
            'shares_sum'           => $row['shares_sum']       !== null ? (int)$row['shares_sum']       : null,

            'total_watch_seconds_sum' => $row['total_watch_seconds_sum'] !== null
                                            ? (int)$row['total_watch_seconds_sum']
                                            : null,

            'avg_watch_ratio_est'  => $row['avg_watch_ratio_est'] !== null
                                        ? (float)$row['avg_watch_ratio_est']
                                        : null,

            'watch_equivalent_sum' => $row['watch_equivalent_sum'] !== null
                                        ? (float)$row['watch_equivalent_sum']
                                        : null,

            'engagement_rate_sum'  => $row['engagement_rate_sum'] !== null
                                        ? (float)$row['engagement_rate_sum']
                                        : null,

            'by_platform' => [
                'YOUTUBE'   => $row['views_yt'] !== null ? (int)$row['views_yt'] : 0,
                'FACEBOOK'  => $row['views_fb'] !== null ? (int)$row['views_fb'] : 0,
                'INSTAGRAM' => $row['views_ig'] !== null ? (int)$row['views_ig'] : 0,
                'TIKTOK'    => $row['views_tt'] !== null ? (int)$row['views_tt'] : 0,
            ],

            'last_snapshot_at'     => $row['last_snapshot_at'],

            'trend' => [
                'slope' => $trend['slope'],
                'stars' => $trend['stars'],
            ],
        ];
    }


    public function internalRefreshMaterializedViews(int $minIntervalSeconds = 120): bool
    {
        $now = new \DateTimeImmutable();

        $st = $this->pdo->prepare("
            SELECT last_refresh
            FROM mv_refresh_control
            WHERE name = 'video_rollup'
            FOR UPDATE
        ");
        $st->execute();
        $row = $st->fetch(\PDO::FETCH_ASSOC);

        $last = ($row && $row['last_refresh'])
            ? new \DateTimeImmutable($row['last_refresh'])
            : null;

        if ($last && ($now->getTimestamp() - $last->getTimestamp()) < $minIntervalSeconds) {
            return false;
        }

        $this->pdo->exec("DELETE FROM mv_video_rollup");

        $sql = "
        INSERT INTO mv_video_rollup (
            video_id, slug, video_title, video_published_at, canonical_length_seconds,
            views_native_sum, likes_sum, comments_sum, shares_sum, total_watch_seconds_sum,
            views_yt, views_fb, views_ig, views_tt
        )
        SELECT
          v.id,
          v.slug,
          v.title,
          v.published_at,
          v.duration_seconds,

          COALESCE(SUM(e.views_native_fallback),0),
          COALESCE(SUM(e.likes),0),
          COALESCE(SUM(e.comments),0),
          COALESCE(SUM(e.shares),0),
          COALESCE(SUM(e.total_watch_seconds),0),

          COALESCE(SUM(CASE WHEN e.platform='YOUTUBE' THEN e.views_native_fallback ELSE 0 END),0),
          COALESCE(SUM(CASE WHEN e.platform='FACEBOOK' THEN e.views_native_fallback ELSE 0 END),0),
          COALESCE(SUM(CASE WHEN e.platform='INSTAGRAM' THEN e.views_native_fallback ELSE 0 END),0),
          COALESCE(SUM(CASE WHEN e.platform='TIKTOK' THEN e.views_native_fallback ELSE 0 END),0)
        FROM video v
        LEFT JOIN source_video sv ON sv.video_id = v.id AND sv.is_active = 1
        LEFT JOIN v_source_latest_enriched e ON e.source_video_id = sv.id
        GROUP BY v.id, v.slug, v.title, v.published_at, v.duration_seconds
        ";

        $this->pdo->exec($sql);

        $upd = $this->pdo->prepare("
            REPLACE INTO mv_refresh_control(name, last_refresh)
            VALUES ('video_rollup', NOW())
        ");
        $upd->execute();

        return true;
    }


   public function internalRefreshVideoTimeSeries(bool $withPercentiles = false): void
   {
       $this->pdo->exec(MaterializedViewsSql::ENSURE_TABLES);

       foreach (explode(';', MaterializedViewsSql::ENSURE_INDEXES) as $sql) {
           $sql = trim($sql);
           if (!$sql) {continue;}
           try {
               $this->pdo->exec($sql);
           } catch (\PDOException $e) {
               if (($e->errorInfo[1] ?? null) != 1061) {
                   throw $e;
               }
           }
       }

       $this->pdo->exec(MaterializedViewsSql::REFRESH_ALIGNED_HOUR_RAW);
       $this->pdo->exec(MaterializedViewsSql::REFRESH_ALIGNED_HOUR_DENSE);

       if ($withPercentiles) {
           $this->pdo->exec(MaterializedViewsSql::REFRESH_PERCENTILES_HOUR);
       }
   }





    public function refreshMaterializedViews(int $minIntervalSeconds = 120): bool
    {
        $ret = $this->internalRefreshMaterializedViews($minIntervalSeconds);
        $this->internalRefreshVideoTimeSeries(false);
        return $ret;
    }



    public function refreshVideoTimeSeries(bool $withPercentiles = true): bool
    {
        $this->internalRefreshVideoTimeSeries($withPercentiles);
        return true;
    }



}
