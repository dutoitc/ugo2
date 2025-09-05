<?php
declare(strict_types=1);

namespace Web\Domain;

final class MetricsSnapshot
{
    public string $platform;                  // YOUTUBE | FACEBOOK | INSTAGRAM | TIKTOK
    public ?string $platform_format;          // VIDEO | SHORT | REEL | null
    public ?int $source_video_id;             // soit ça…
    public ?string $platform_video_id;        // …soit (platform, platform_video_id)

    public string $snapshot_atIso;            // 'YYYY-MM-DD HH:MM:SS.mmm' UTC (DATETIME(3))

    // bruts
    public ?int $views_native;
    public ?int $avg_watch_seconds;
    public ?int $total_watch_seconds;
    public ?int $video_length_seconds;

    public ?int $reach;
    public ?int $unique_viewers;

    public ?int $likes;
    public ?int $comments;
    public ?int $shares;

    public ?int $reactions_total;
    public ?int $reactions_like;
    public ?int $reactions_love;
    public ?int $reactions_wow;
    public ?int $reactions_haha;
    public ?int $reactions_sad;
    public ?int $reactions_angry;

    /**
     * Normalise et valide un item.
     * Applique le mapping legacy: legacy_views_3s -> views_native pour FACEBOOK/VIDEO.
     */
    public static function fromArray(array $s, ?string $defaultNowIsoMs  = null): self
    {
        $self = new self();

        $self->platform = strtoupper(trim((string)($s['platform'] ?? '')));
        if ($self->platform === '') {
            throw new \InvalidArgumentException('platform is required');
        }

        $fmt = $s['platform_format'] ?? null;
        $self->platform_format = $fmt ? strtoupper(trim((string)$fmt)) : null;
        if ($self->platform_format !== null && !in_array($self->platform_format, ['VIDEO','SHORT','REEL'], true)) {
            $self->platform_format = null; // on ignore les valeurs non supportées
        }

        $self->source_video_id   = isset($s['source_video_id']) ? self::toUIntOrNull($s['source_video_id']) : null;
        $self->platform_video_id = isset($s['platform_video_id']) ? trim((string)$s['platform_video_id']) : null;

        if ($self->source_video_id === null && ($self->platform_video_id === null || $self->platform_video_id === '')) {
            throw new \InvalidArgumentException('Provide source_video_id or (platform, platform_video_id)');
        }

        $self->snapshot_atIso = self::toUtcIsoMillis($s['snapshot_at'] ?? null, $defaultNowIsoMs);


        // mapping legacy
        $viewsNative = self::toUIntOrNull($s['views_native'] ?? null);
        $legacy3s    = self::toUIntOrNull($s['legacy_views_3s'] ?? null);
        if ($viewsNative === null && $legacy3s !== null && $self->platform === 'FACEBOOK' && ($self->platform_format === 'VIDEO' || $self->platform_format === null)) {
            $viewsNative = $legacy3s;
        }
        $self->views_native        = $viewsNative;
        $self->avg_watch_seconds   = self::toUIntOrNull($s['avg_watch_seconds'] ?? null);
        $self->total_watch_seconds = self::toUIntOrNull($s['total_watch_seconds'] ?? null);
        $self->video_length_seconds= self::toUIntOrNull($s['video_length_seconds'] ?? null);

        $self->reach          = self::toUIntOrNull($s['reach'] ?? null);
        $self->unique_viewers = self::toUIntOrNull($s['unique_viewers'] ?? null);

        $self->likes    = self::toUIntOrNull($s['likes'] ?? null);
        $self->comments = self::toUIntOrNull($s['comments'] ?? null);
        $self->shares   = self::toUIntOrNull($s['shares'] ?? null);

        $self->reactions_total = self::toUIntOrNull($s['reactions_total'] ?? null);
        $self->reactions_like  = self::toUIntOrNull($s['reactions_like'] ?? null);
        $self->reactions_love  = self::toUIntOrNull($s['reactions_love'] ?? null);
        $self->reactions_wow   = self::toUIntOrNull($s['reactions_wow'] ?? null);
        $self->reactions_haha  = self::toUIntOrNull($s['reactions_haha'] ?? null);
        $self->reactions_sad   = self::toUIntOrNull($s['reactions_sad'] ?? null);
        $self->reactions_angry = self::toUIntOrNull($s['reactions_angry'] ?? null);

        return $self;
    }

    private static function toUIntOrNull(mixed $v): ?int
    {
        if ($v === null || $v === '') return null;
        if (!is_numeric($v)) throw new \InvalidArgumentException('numeric expected');
        $n = (int)$v;
        return $n < 0 ? 0 : $n;
    }

   /** Retourne 'YYYY-MM-DD HH:MM:SS.mmm' (UTC) */
   private static function toUtcIsoMillis(mixed $v, ?string $fallbackIsoMs = null): string
   {
       if ($v === null || $v === '') {
           return $fallbackIsoMs ?? gmdate('Y-m-d H:i:s').'.000';
       }

       // Numérique → epoch (s ou ms)
       if (is_int($v) || is_float($v) || (is_string($v) && ctype_digit($v))) {
           $num = (int)$v;
           $sec = ($num >= 100000000000) ? intdiv($num, 1000) : $num;
           $ms  = ($num >= 100000000000) ? ($num % 1000)       : 0;
           $dt  = (new \DateTimeImmutable('@'.$sec))->setTimezone(new \DateTimeZone('UTC'));
           return $dt->format('Y-m-d H:i:s').'.'.str_pad((string)$ms, 3, '0', STR_PAD_LEFT);
       }

       // ISO → UTC
       $dt = (new \DateTimeImmutable((string)$v))->setTimezone(new \DateTimeZone('UTC'));
       $ms = (int) floor(((int)$dt->format('u')) / 1000);
       return $dt->format('Y-m-d H:i:s').'.'.str_pad((string)$ms, 3, '0', STR_PAD_LEFT);
   }
}
