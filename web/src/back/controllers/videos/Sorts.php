<?php
declare(strict_types=1);

namespace Web\Controllers\Videos;

final class Sorts
{
    private const MAP = [
        'views_desc'      => 'COALESCE(v.views_native_sum, 0) DESC, v.video_published_at DESC, v.video_id DESC',
        'views_asc'       => 'COALESCE(v.views_native_sum, 0) ASC, v.video_published_at ASC, v.video_id ASC',
        'youtube_desc'    => 'COALESCE(v.views_yt, 0) DESC, v.video_published_at DESC, v.video_id DESC',
        'youtube_asc'     => 'COALESCE(v.views_yt, 0) ASC, v.video_published_at ASC, v.video_id ASC',
        'facebook_desc'   => 'COALESCE(v.views_fb, 0) DESC, v.video_published_at DESC, v.video_id DESC',
        'facebook_asc'    => 'COALESCE(v.views_fb, 0) ASC, v.video_published_at ASC, v.video_id ASC',
        'instagram_desc'  => 'COALESCE(v.views_ig, 0) DESC, v.video_published_at DESC, v.video_id DESC',
        'instagram_asc'   => 'COALESCE(v.views_ig, 0) ASC, v.video_published_at ASC, v.video_id ASC',
        'tiktok_desc'     => 'COALESCE(v.views_tt, 0) DESC, v.video_published_at DESC, v.video_id DESC',
        'tiktok_asc'      => 'COALESCE(v.views_tt, 0) ASC, v.video_published_at ASC, v.video_id ASC',
        'published_desc'  => 'v.video_published_at DESC, v.video_id DESC',
        'published_asc'   => 'v.video_published_at ASC, v.video_id ASC',
        'engagement_desc' => '(v.engagement_rate_sum IS NULL) ASC, v.engagement_rate_sum DESC, v.views_native_sum DESC, v.video_id DESC',
        'engagement_asc'  => '(v.engagement_rate_sum IS NULL) ASC, v.engagement_rate_sum ASC, v.views_native_sum ASC, v.video_id ASC',
        'watch_eq_desc'   => '(v.watch_equivalent_sum IS NULL) ASC, v.watch_equivalent_sum DESC, v.views_native_sum DESC, v.video_id DESC',
        'watch_eq_asc'    => '(v.watch_equivalent_sum IS NULL) ASC, v.watch_equivalent_sum ASC, v.views_native_sum ASC, v.video_id ASC',
        'title_asc'       => 'v.video_title ASC, v.video_id ASC',
        'title_desc'      => 'v.video_title DESC, v.video_id DESC',
    ];

    public static function normalize(?string $raw): string
    {
        $key = strtolower(trim($raw ?? 'views_desc'));
        return array_key_exists($key, self::MAP) ? $key : 'views_desc';
    }

    public static function sql(string $normalized): string
    {
        return self::MAP[$normalized];
    }
}
