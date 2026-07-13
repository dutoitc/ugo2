<?php
declare(strict_types=1);

namespace Web\Repositories;

use Web\Db;
use Web\Domain\MetricsSnapshot;

final class MetricsSnapshotRepository
{
    private const ALL_FIELDS = [
        'views_native', 'avg_watch_seconds', 'total_watch_seconds', 'video_length_seconds',
        'reach', 'unique_viewers', 'likes', 'comments', 'shares',
        'reactions_total', 'reactions_like', 'reactions_love', 'reactions_wow',
        'reactions_haha', 'reactions_sad', 'reactions_angry'
    ];

    private const IMMEDIATE_USEFUL_FIELDS = [
        'likes', 'comments', 'shares',
        'reactions_total', 'reactions_like', 'reactions_love', 'reactions_wow',
        'reactions_haha', 'reactions_sad', 'reactions_angry',
        'video_length_seconds'
    ];

    public function __construct(private Db $db) {}

    private function pdo(): \PDO
    {
        return $this->db->pdo();
    }

    /**
     * Stocke uniquement un point utile et protège les compteurs cumulés contre les régressions.
     *
     * @return array{stored:bool,reason:string,monotonic_corrections:int,views_delta:?int}
     */
    public function storeIfUseful(
        int $sourceId,
        MetricsSnapshot $m,
        int $minDeltaAbs,
        float $minDeltaRel,
        bool $dailyGuard
    ): array {
        $previous = $this->latestForUpdate($sourceId);
        if ($previous === null) {
            $this->upsert($sourceId, $m);
            return ['stored'=>true, 'reason'=>'first_snapshot', 'monotonic_corrections'=>0, 'views_delta'=>null];
        }

        if ($m->snapshot_atIso < (string)$previous['snapshot_at']) {
            return ['stored'=>false, 'reason'=>'out_of_order', 'monotonic_corrections'=>0, 'views_delta'=>null];
        }

        $corrections = $this->mergeUnknownAndEnforceMonotonicity($sourceId, $m, $previous);
        $previousViews = $this->toNullableInt($previous['views_native'] ?? null);
        $currentViews = $m->views_native;
        $viewsDelta = ($previousViews !== null && $currentViews !== null)
            ? max(0, $currentViews - $previousViews)
            : null;

        if ((string)$previous['snapshot_at'] === $m->snapshot_atIso) {
            $this->upsert($sourceId, $m);
            return [
                'stored'=>true,
                'reason'=>'same_timestamp_update',
                'monotonic_corrections'=>$corrections,
                'views_delta'=>$viewsDelta
            ];
        }

        $viewChangedEnough = $this->isSignificantIncrease(
            $previousViews,
            $currentViews,
            max(0, $minDeltaAbs),
            max(0.0, $minDeltaRel)
        );
        $usefulChanged = $this->hasUsefulMetricChange($m, $previous);
        $dailyPoint = $dailyGuard
            && substr((string)$previous['snapshot_at'], 0, 10) !== substr($m->snapshot_atIso, 0, 10);

        if (!$viewChangedEnough && !$usefulChanged && !$dailyPoint) {
            return [
                'stored'=>false,
                'reason'=>'below_threshold',
                'monotonic_corrections'=>$corrections,
                'views_delta'=>$viewsDelta
            ];
        }

        $this->upsert($sourceId, $m);
        $reason = $usefulChanged ? 'useful_metric_changed' : ($viewChangedEnough ? 'views_delta' : 'daily_guard');
        return [
            'stored'=>true,
            'reason'=>$reason,
            'monotonic_corrections'=>$corrections,
            'views_delta'=>$viewsDelta
        ];
    }

    private function latestForUpdate(int $sourceId): ?array
    {
        $st = $this->pdo()->prepare("
            SELECT snapshot_at,
                   views_native, avg_watch_seconds, total_watch_seconds, video_length_seconds,
                   reach, unique_viewers, likes, comments, shares,
                   reactions_total, reactions_like, reactions_love, reactions_wow,
                   reactions_haha, reactions_sad, reactions_angry
            FROM metric_snapshot
            WHERE source_video_id = ?
            ORDER BY snapshot_at DESC, id DESC
            LIMIT 1
            FOR UPDATE
        ");
        $st->execute([$sourceId]);
        $row = $st->fetch(\PDO::FETCH_ASSOC);
        return $row === false ? null : $row;
    }

    private function mergeUnknownAndEnforceMonotonicity(int $sourceId, MetricsSnapshot $m, array $previous): int
    {
        $corrections = 0;
        foreach (self::ALL_FIELDS as $field) {
            $old = $this->toNullableInt($previous[$field] ?? null);
            if ($m->$field === null && $old !== null) {
                $m->$field = $old;
            }
        }

        foreach (['views_native', 'total_watch_seconds'] as $field) {
            $old = $this->toNullableInt($previous[$field] ?? null);
            $current = $m->$field;
            if ($old !== null && $current !== null && $current < $old) {
                error_log(sprintf(
                    '[metrics] monotonic correction platform=%s source_video_id=%d field=%s old=%d received=%d',
                    $m->platform,
                    $sourceId,
                    $field,
                    $old,
                    $current
                ));
                $m->$field = $old;
                $corrections++;
            }
        }
        return $corrections;
    }

    private function hasUsefulMetricChange(MetricsSnapshot $m, array $previous): bool
    {
        foreach (self::IMMEDIATE_USEFUL_FIELDS as $field) {
            $old = $this->toNullableInt($previous[$field] ?? null);
            if ($m->$field !== $old) return true;
        }
        return false;
    }

    private function isSignificantIncrease(?int $old, ?int $current, int $minAbs, float $minRel): bool
    {
        if ($current === null) return false;
        if ($old === null) return true;
        $delta = max(0, $current - $old);
        if ($delta <= 0) return false;
        if ($delta >= $minAbs) return true;
        return $old > 0 && ($delta / $old) >= $minRel;
    }

    private function toNullableInt(mixed $value): ?int
    {
        return $value === null || $value === '' ? null : (int)$value;
    }

    private function upsert(int $sourceId, MetricsSnapshot $m): void
    {
        $sql = "
        INSERT INTO metric_snapshot
          (source_video_id, snapshot_at,
           views_native, avg_watch_seconds, total_watch_seconds, video_length_seconds,
           reach, unique_viewers,
           likes, comments, shares,
           reactions_total, reactions_like, reactions_love, reactions_wow, reactions_haha, reactions_sad, reactions_angry)
        VALUES
          (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        ON DUPLICATE KEY UPDATE
           views_native=VALUES(views_native),
           avg_watch_seconds=VALUES(avg_watch_seconds),
           total_watch_seconds=VALUES(total_watch_seconds),
           video_length_seconds=VALUES(video_length_seconds),
           reach=VALUES(reach),
           unique_viewers=VALUES(unique_viewers),
           likes=VALUES(likes),
           comments=VALUES(comments),
           shares=VALUES(shares),
           reactions_total=VALUES(reactions_total),
           reactions_like=VALUES(reactions_like),
           reactions_love=VALUES(reactions_love),
           reactions_wow=VALUES(reactions_wow),
           reactions_haha=VALUES(reactions_haha),
           reactions_sad=VALUES(reactions_sad),
           reactions_angry=VALUES(reactions_angry)
        ";
        $st = $this->pdo()->prepare($sql);
        $st->execute([
            $sourceId,
            $m->snapshot_atIso,
            $m->views_native,
            $m->avg_watch_seconds,
            $m->total_watch_seconds,
            $m->video_length_seconds,
            $m->reach,
            $m->unique_viewers,
            $m->likes,
            $m->comments,
            $m->shares,
            $m->reactions_total,
            $m->reactions_like,
            $m->reactions_love,
            $m->reactions_wow,
            $m->reactions_haha,
            $m->reactions_sad,
            $m->reactions_angry,
        ]);
    }
}
