<?php
declare(strict_types=1);

namespace Web\Domain;

final class MetricsSnapshot
{
    public string $platform;                  // YOUTUBE | FACEBOOK | INSTAGRAM | TIKTOK
    public ?string $platform_format;          // VIDEO | SHORT | REEL | null
    public ?int $source_video_id;             // soit ça…
    public ?string $platform_video_id;        // …soit (platform, platform_video_id)

    public string $snapshot_atUtc;            // 'YYYY-MM-DD HH:MM:SS.mmm' UTC (DATETIME(3))

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
    public static function fromArray(array $s, ?string $defaultNowUtcMillis = null): self
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

        $self->snapshot_atUtc = self::toUtcMillisString($s['snapshot_at'] ?? null, $defaultNowUtcMillis);

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

   /**
    * Accepte :
    *  - string ISO-8601 ("2025-09-05T06:15:00Z" ou "2025-09-05 06:15:00")
    *  - entier/float epoch en secondes ou millisecondes
    * Retourne des millisecondes epoch (string, UTC) ou null si invalide.
    */
   private static function toUtcMillisString($value): ?string
   {
       if ($value === null || $value === '') return null;

       // 1) Numérique → epoch
       if (is_int($value) || is_float($value) || (is_string($value) && ctype_digit($value))) {
           $num = (int)$value;
           if ($num < 0) return null;
           // Heuristique : >= 10^12 => millisecondes, sinon secondes
           $ms = ($num >= 100000000000) ? $num : $num * 1000;
           return (string)$ms;
       }

       // 2) Chaîne date ISO → epoch
       if (is_string($value)) {
           $ts = strtotime($value);
           if ($ts === false) return null;
           return (string)($ts * 1000);
       }

       return null;
   }
}
