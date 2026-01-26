<?php
declare(strict_types=1);

namespace Web\Services;

use PDO;

final class TrendService
{
    public function __construct(private PDO $pdo) {}

    /**
     * Retourne une pente (vues / heure)
     */
    public function computeSlope(int $videoId, int $limitSnapshots = 20): float
    {
        $sql = "
            SELECT snapshot_at, views_native
            FROM v_metric_snapshot_enriched
            WHERE video_id = ?
              AND views_native IS NOT NULL
            ORDER BY snapshot_at DESC
            LIMIT ?
        ";

        $st = $this->pdo->prepare($sql);
        $st->execute([$videoId, $limitSnapshots]);
        $rows = $st->fetchAll(PDO::FETCH_ASSOC);

        if (count($rows) < 2) {
            return 0.0;
        }

        // oldest → newest
        $rows = array_reverse($rows);

        // Convertir snapshot_at (string|float) en epoch pour le premier
        $first = $rows[0]['snapshot_at'];
        if (!is_numeric($first)) {
            $first = strtotime((string)$first);
        }
        if ($first === false || $first === null) {
            return 0.0;
        }
        $t0 = (float)$first;

        $x = [];
        $y = [];

        foreach ($rows as $r) {
            $ts = $r['snapshot_at'];
            if (!is_numeric($ts)) {
                $ts = strtotime((string)$ts);
            }
            if ($ts === false || $ts === null) {
                continue;
            }

            $ts = (float)$ts;

            $x[] = ($ts - $t0) / 3600.0; // heures depuis t0
            $y[] = (float)$r['views_native'];
        }

        if (count($x) < 2) {
            return 0.0;
        }

        return $this->leastSquaresSlope($x, $y);
    }

    /**
     * Régression linéaire simple: slope = covariance(x,y) / variance(x)
     */
    private function leastSquaresSlope(array $x, array $y): float
    {
        $n = count($x);
        if ($n < 2) {
            return 0.0;
        }

        $meanX = array_sum($x) / $n;
        $meanY = array_sum($y) / $n;

        $num = 0.0;
        $den = 0.0;

        for ($i = 0; $i < $n; $i++) {
            $dx = $x[$i] - $meanX;
            $dy = $y[$i] - $meanY;
            $num += $dx * $dy;
            $den += $dx * $dx;
        }

        if ($den == 0.0) {
            return 0.0;
        }

        return $num / $den; // vues / heure
    }

    /**
     * Convertit la pente en étoiles ★
     */
    public function slopeToStars(float $slope): int
    {

        if ($slope >= 32) {
            return 3;
        }
        if ($slope >= 8) {
            return 2;
        }
        if ($slope >= 2) {
            return 1;
        }
        return 0;
    }

    public function computeTrend(int $videoId): array
    {
        $slope = $this->computeSlope($videoId);
        $stars = $this->slopeToStars($slope);

        return [
            'slope' => $slope,
            'stars' => $stars,
        ];
    }
}
