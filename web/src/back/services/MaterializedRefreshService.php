<?php
declare(strict_types=1);

namespace Web\Services;

use PDO;
use Web\Db;
use Web\Controllers\Videos\VideosRepository;

final class MaterializedRefreshService
{
    private const JOB = 'materialized_views';
    private const LOCK = 'ugo2:materialized_views';

    private PDO $pdo;

    public function __construct(private Db $db)
    {
        $this->pdo = $db->pdo();
        $this->ensureSchema();
    }

    public function markDirty(): void
    {
        $st = $this->pdo->prepare("
            INSERT INTO refresh_job_state(name, dirty_since, last_status)
            VALUES (?, UTC_TIMESTAMP(3), 'DIRTY')
            ON DUPLICATE KEY UPDATE
              dirty_since = VALUES(dirty_since),
              last_status = CASE WHEN last_status = 'RUNNING' THEN last_status ELSE 'DIRTY' END
        ");
        $st->execute([self::JOB]);
    }

    /**
     * @return array{refreshed:bool,locked:bool,duration_ms:?int,status:string}
     */
    public function refreshIfDirty(bool $force = false, bool $withPercentiles = false): array
    {
        $lock = $this->pdo->query("SELECT GET_LOCK('".self::LOCK."', 0)")->fetchColumn();
        if ((int)$lock !== 1) {
            return ['refreshed'=>false, 'locked'=>false, 'duration_ms'=>null, 'status'=>'LOCKED'];
        }

        $started = microtime(true);
        $refreshStartedAt = (string)$this->pdo->query("SELECT UTC_TIMESTAMP(3)")->fetchColumn();
        try {
            $st = $this->pdo->prepare("SELECT dirty_since FROM refresh_job_state WHERE name = ?");
            $st->execute([self::JOB]);
            $dirtySince = $st->fetchColumn();
            if (!$force && ($dirtySince === false || $dirtySince === null)) {
                return ['refreshed'=>false, 'locked'=>true, 'duration_ms'=>0, 'status'=>'CLEAN'];
            }

            $this->pdo->prepare("
                UPDATE refresh_job_state
                SET last_started_at = ?, last_status = 'RUNNING', last_error = NULL
                WHERE name = ?
            ")->execute([$refreshStartedAt, self::JOB]);

            $repo = new VideosRepository($this->pdo);
            $repo->internalRefreshMaterializedViews(0);
            $repo->internalRefreshVideoTimeSeries($withPercentiles);

            $durationMs = (int)round((microtime(true) - $started) * 1000);
            // Si une ingestion a marqué le job pendant le refresh, son dirty_since
            // est postérieur au début du refresh : on le conserve pour l'exécution suivante.
            $this->pdo->prepare("
                UPDATE refresh_job_state
                SET last_success_at = UTC_TIMESTAMP(3),
                    last_duration_ms = ?,
                    last_status = CASE
                        WHEN dirty_since IS NOT NULL AND dirty_since > ? THEN 'DIRTY'
                        ELSE 'SUCCESS'
                    END,
                    dirty_since = CASE
                        WHEN dirty_since IS NOT NULL AND dirty_since > ? THEN dirty_since
                        ELSE NULL
                    END,
                    last_error = NULL
                WHERE name = ?
            ")->execute([$durationMs, $refreshStartedAt, $refreshStartedAt, self::JOB]);

            error_log(sprintf('[refresh] materialized views refreshed in %d ms', $durationMs));
            return ['refreshed'=>true, 'locked'=>true, 'duration_ms'=>$durationMs, 'status'=>'SUCCESS'];
        } catch (\Throwable $e) {
            $durationMs = (int)round((microtime(true) - $started) * 1000);
            $this->pdo->prepare("
                UPDATE refresh_job_state
                SET last_duration_ms = ?, last_status = 'ERROR', last_error = ?
                WHERE name = ?
            ")->execute([$durationMs, substr($e->getMessage(), 0, 1000), self::JOB]);
            throw $e;
        } finally {
            $this->pdo->query("SELECT RELEASE_LOCK('".self::LOCK."')");
        }
    }

    private function ensureSchema(): void
    {
        $this->pdo->exec("
            CREATE TABLE IF NOT EXISTS refresh_job_state (
              name VARCHAR(64) NOT NULL PRIMARY KEY,
              dirty_since DATETIME(3) NULL,
              last_started_at DATETIME(3) NULL,
              last_success_at DATETIME(3) NULL,
              last_duration_ms BIGINT UNSIGNED NULL,
              last_status VARCHAR(16) NOT NULL DEFAULT 'NEVER',
              last_error VARCHAR(1000) NULL,
              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        ");
        $this->pdo->exec("INSERT IGNORE INTO refresh_job_state(name, last_status) VALUES ('".self::JOB."', 'NEVER')");
    }
}
