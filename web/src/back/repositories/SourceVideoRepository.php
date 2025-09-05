<?php
declare(strict_types=1);

namespace Web\Repositories;

use Web\Db;

final class SourceVideoRepository
{
    public function __construct(private Db $db) {}

    private function pdo(): \PDO
    {
        if (method_exists($this->db, 'pdo')) return $this->db->pdo();
        if (method_exists($this->db, 'getPdo')) return $this->db->getPdo();
        throw new \RuntimeException('Db must expose PDO via pdo() or getPdo()');
    }

    public function findIdByPlatformAndVideoId(string $platform, string $platformVideoId): ?int
    {
        $sql = 'SELECT id FROM source_video WHERE platform = ? AND platform_video_id = ?';
        $st = $this->pdo()->prepare($sql);
        $st->execute([$platform, $platformVideoId]);
        $id = $st->fetchColumn();
        return $id !== false ? (int)$id : null;
    }

    public function setPlatformFormatIfNull(int $id, ?string $platformFormat): void
    {
        if (!$platformFormat) return;
        $sql = 'UPDATE source_video SET platform_format = ? WHERE id = ? AND platform_format IS NULL';
        $st = $this->pdo()->prepare($sql);
        $st->execute([$platformFormat, $id]);
    }

    public function createMinimal(string $platform, ?string $platformFormat, string $platformVideoId): int
    {
        $sql = "INSERT INTO source_video
            (video_id, platform, platform_format, platform_channel_id, platform_video_id, title, description, url, etag, published_at, duration_seconds, is_active, created_at, updated_at)
            VALUES (NULL, ?, ?, NULL, ?, NULL, NULL, NULL, NULL, NULL, NULL, 1, NOW(), NOW())";
        $st = $this->pdo()->prepare($sql);
        $st->execute([$platform, $platformFormat, $platformVideoId]);
        return (int)$this->pdo()->lastInsertId();
    }
}
