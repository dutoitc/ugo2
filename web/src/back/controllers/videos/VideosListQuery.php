<?php
declare(strict_types=1);

namespace Web\Controllers\Videos;


final class VideosListQuery
{
    public function __construct(
        public readonly Paginator $paginator,
        public readonly ?string $q,
        public readonly ?string $platform, // YOUTUBE/FACEBOOK/INSTAGRAM/TIKTOK/null
        public readonly ?string $format,   // VIDEO/SHORT/REEL/null
        public readonly ?string $fromIso,
        public readonly ?string $toIso,
        public readonly string $sort       // clé normalisée
    ) {}
}
