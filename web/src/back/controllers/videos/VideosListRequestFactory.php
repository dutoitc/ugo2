<?php
declare(strict_types=1);

namespace Web\Controllers\Videos;

final class VideosListRequestFactory
{
    /**
     * @param array<string,mixed> $get
     */
    public static function fromGet(array $get): VideosListQuery
    {
        $paginator = Paginator::from(
            isset($get['page']) ? (int)$get['page'] : null,
            isset($get['size']) ? (int)$get['size'] : null,
            20, // default size
            5000 // hard max
        );

        $q = isset($get['q']) ? trim((string)$get['q']) : null;
        if ($q === '') $q = null;

        // platform
        $platform = isset($get['platform']) ? strtoupper(trim((string)$get['platform'])) : null;
        $validPlatforms = ['YOUTUBE','FACEBOOK','INSTAGRAM','TIKTOK'];
        if (!in_array($platform, $validPlatforms, true)) {
            $platform = null;
        }

        // format
        $format = isset($get['format']) ? strtoupper(trim((string)$get['format'])) : null;
        $validFormats = ['VIDEO','SHORT','REEL'];
        if (!in_array($format, $validFormats, true)) {
            $format = null;
        }

        // dates (on laisse ISO libre, validation forte possible plus tard)
        $from = isset($get['from']) ? trim((string)$get['from']) : null;
        $to   = isset($get['to'])   ? trim((string)$get['to'])   : null;
        if ($from === '') {
            $from = null;
        }
        if ($to === '') {
            $to   = null;
        }

        // sort
        $sortNorm = Sorts::normalize(isset($get['sort']) ? (string)$get['sort'] : null);

        return new VideosListQuery(
            paginator: $paginator,
            q: $q,
            platform: $platform,
            format: $format,
            fromIso: $from,
            toIso: $to,
            sort: $sortNorm
        );
    }
}
