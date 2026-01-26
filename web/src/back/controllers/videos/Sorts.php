<?php
declare(strict_types=1);

namespace Web\Controllers\Videos;


final class Sorts
{
    // REST → SQL
    private const MAP = [
        'views_desc'      => 'v.views_native_sum DESC, v.video_published_at DESC',
        'views_asc'      => 'v.views_native_sum ASC, v.video_published_at ASC',
        'published_desc'  => 'v.video_published_at DESC, v.video_id DESC',
        'published_asc'   => 'v.video_published_at ASC,  v.video_id ASC',
        'engagement_desc' => '(v.engagement_rate_sum IS NULL) ASC, v.engagement_rate_sum DESC, v.views_native_sum DESC',
        'engagement_asc' => '(v.engagement_rate_sum IS NULL) ASC, v.engagement_rate_sum ASC, v.views_native_sum ASC',
        'watch_eq_desc'   => '(v.watch_equivalent_sum IS NULL) ASC, v.watch_equivalent_sum DESC, v.views_native_sum DESC',
        'watch_eq_asc'   => '(v.watch_equivalent_sum IS NULL) ASC, v.watch_equivalent_sum ASC, v.views_native_sum ASC',
        'title_asc'       => 'v.video_title ASC,  v.video_id DESC',
        'title_desc'      => 'v.video_title DESC, v.video_id DESC',
    ];

    public static function normalize(?string $raw): string
    {
        $k = strtolower(trim($raw ?? 'views_desc'));
        return array_key_exists($k, self::MAP) ? $k : 'views_desc';
    }

    public static function sql(string $norm): string
    {
        return self::MAP[$norm];
    }

}