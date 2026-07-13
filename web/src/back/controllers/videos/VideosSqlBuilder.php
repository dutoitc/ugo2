<?php
declare(strict_types=1);

namespace Web\Controllers\Videos;

final class VideosSqlBuilder
{
    /**
     * @return array{whereSql:string,args:array<int,mixed>}
     */
    public static function buildWhere(VideosListQuery $q): array
    {
        $where = [];
        $args  = [];

        // q LIKE
        if ($q->q !== null) {
            $where[] = '(v.video_title LIKE ? OR v.slug LIKE ?)';
            $like = '%' . $q->q . '%';
            $args[] = $like;
            $args[] = $like;
        }

        // from / to
        if ($q->fromIso !== null) {
            $where[] = 'v.video_published_at >= ?';
            $args[] = $q->fromIso;
        }

        if ($q->toIso !== null) {
            $where[] = 'v.video_published_at < ?';
            $args[] = $q->toIso;
        }

        // platform
        if ($q->platform !== null) {
            $map = [
                'YOUTUBE'   => '(v.views_yt IS NOT NULL AND v.views_yt > 0)',
                'FACEBOOK'  => '(v.views_fb IS NOT NULL AND v.views_fb > 0)',
                'INSTAGRAM' => '(v.views_ig IS NOT NULL AND v.views_ig > 0)',
                'TIKTOK'    => '(v.views_tt IS NOT NULL AND v.views_tt > 0)',
            ];

            if (isset($map[$q->platform])) {
                $where[] = $map[$q->platform];
            }
        }

        // format
        if ($q->format !== null) {
            $where[] = 'EXISTS (
                SELECT 1 FROM source_video sv
                WHERE sv.video_id = v.video_id
                  AND sv.platform_format = ?
            )';
            $args[] = $q->format;
        }

        $whereSql = '';
        if (!empty($where)) {
            $whereSql = 'WHERE ' . implode(' AND ', $where);
        }

        return [
            'whereSql' => $whereSql,
            'args'     => $args
        ];
    }

    /**
     * @return array{sql:string,args:array<int,mixed>}
     */
    public static function sqlCount(VideosListQuery $q): array
    {
        $built = self::buildWhere($q);
        $sql = "SELECT COUNT(*) FROM mv_video_rollup v {$built['whereSql']}";

        return [
            'sql'  => $sql,
            'args' => $built['args']
        ];
    }

    /**
     * @return array{sql:string,args:array<int,mixed>}
     */
    public static function sqlPage(VideosListQuery $q): array
    {
        $built = self::buildWhere($q);
        $orderBy = Sorts::sql($q->sort);

        $sql = "
          SELECT
            v.video_id, v.slug, v.video_title, v.video_published_at, v.canonical_length_seconds,
            v.views_native_sum, v.likes_sum, v.comments_sum, v.shares_sum,
            v.total_watch_seconds_sum, v.avg_watch_ratio_est, v.watch_equivalent_sum, v.engagement_rate_sum,
            v.views_yt, v.views_fb, v.views_ig, v.views_tt,
            ls.last_snapshot_at
          FROM mv_video_rollup v
          LEFT JOIN v_video_last_snapshot ls ON ls.video_id = v.video_id
          {$built['whereSql']}
          ORDER BY {$orderBy}
          LIMIT ? OFFSET ?
        ";

        $args = $built['args'];
        $args[] = $q->paginator->size;
        $args[] = $q->paginator->offset();

        return [
            'sql'  => $sql,
            'args' => $args
        ];
    }

    /**
     * @return array{sql:string}
     */
    public static function sqlSum(VideosListQuery $q): array
    {
        $built = self::buildWhere($q);
        $sql = "
            SELECT
                COALESCE(SUM(views_yt),0) AS sum_yt,
                COALESCE(SUM(views_fb),0) AS sum_fb,
                COALESCE(SUM(views_ig),0) AS sum_ig,
                COALESCE(SUM(views_tt),0) AS sum_tt
            FROM mv_video_rollup v
            {$built['whereSql']}
        ";
        return ['sql'=>$sql, 'args'=>$built['args']];
    }
}
