<?php
declare(strict_types=1);

namespace Web\Repositories;

use Web\Db;
use Web\Domain\MetricsSnapshot;

final class MetricsSnapshotRepository
{
    public function __construct(private Db $db) {}

    private function pdo(): \PDO
    {
        if (method_exists($this->db, 'pdo')) return $this->db->pdo();
        if (method_exists($this->db, 'getPdo')) return $this->db->getPdo();
        throw new \RuntimeException('Db must expose PDO via pdo() or getPdo()');
    }

    public function upsert(int $sourceId, MetricsSnapshot $m): void
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
            $m->snapshot_atUtc,

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
