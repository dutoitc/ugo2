<?php
declare(strict_types=1);

namespace Web\Controllers\Videos;

use Web\Services\TrendService;
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
        //
        // COUNT
        //
        $count = VideosSqlBuilder::sqlCount($q);
        $stC = $this->pdo->prepare($count['sql']);
        $stC->execute($count['args']);
        $total = (int)$stC->fetchColumn();

        //
        // PAGE
        //
        $page = VideosSqlBuilder::sqlPage($q);
        $stP = $this->pdo->prepare($page['sql']);
        $stP->execute($page['args']);

        $items = [];
        while (true) {
            $row = $stP->fetch(PDO::FETCH_ASSOC);
            if ($row === false) {
                break;
            }
            $items[] = $this->mapItem($row);
        }

        //
        // SUM
        //
        $sumSql = VideosSqlBuilder::sqlSum();
        $stS = $this->pdo->prepare($sumSql['sql']);
        $stS->execute();
        $sumRow = $stS->fetch(PDO::FETCH_ASSOC);

        if (!$sumRow) {
            $sumRow = ['sum_yt'=>0,'sum_fb'=>0,'sum_ig'=>0,'sum_tt'=>0];
        }

        $sum = [
            'youtube'   => (int)$sumRow['sum_yt'],
            'facebook'  => (int)$sumRow['sum_fb'],
            'instagram' => (int)$sumRow['sum_ig'],
            'tiktok'    => (int)$sumRow['sum_tt'],
        ];

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
}
