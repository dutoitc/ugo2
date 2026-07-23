<?php
declare(strict_types=1);

namespace Web\Services;

use PDO;
use Web\Db;
use Web\Lib\SensitiveData;

final class HealthStateService
{
    private PDO $pdo;

    public function __construct(private Db $db, private array $config = [])
    {
        $this->pdo = $db->pdo();
        $this->ensureSchema();
    }

    public function recordPlatformSuccess(
        string $platform,
        ?string $snapshotAt,
        ?int $durationMs,
        ?int $items,
        ?string $tokenExpiresAt
    ): void {
        $platform = strtoupper(trim($platform));
        if ($platform === '') return;

        $expiresAt = $this->toMysqlUtc($tokenExpiresAt);
        $tokenStatus = $expiresAt !== null && strtotime($expiresAt.' UTC') <= time()
            ? 'EXPIRED'
            : 'OK';

        $sql = "
            INSERT INTO platform_health
              (platform, last_success_at, last_snapshot_at, last_duration_ms, last_items,
               token_status, token_expires_at, last_error, last_error_at)
            VALUES (?, UTC_TIMESTAMP(3), ?, ?, ?, ?, ?, NULL, NULL)
            ON DUPLICATE KEY UPDATE
              last_success_at = VALUES(last_success_at),
              last_snapshot_at = COALESCE(VALUES(last_snapshot_at), last_snapshot_at),
              last_duration_ms = VALUES(last_duration_ms),
              last_items = VALUES(last_items),
              token_status = VALUES(token_status),
              token_expires_at = VALUES(token_expires_at),
              last_error = NULL,
              last_error_at = NULL
        ";
        $st = $this->pdo->prepare($sql);
        $st->execute([
            $platform,
            $this->toMysqlUtc($snapshotAt),
            $durationMs,
            $items,
            $tokenStatus,
            $expiresAt,
        ]);
    }

    public function recordPlatformError(
        string $platform,
        string $message,
        ?int $durationMs,
        ?string $tokenExpiresAt,
        bool $tokenLikelyExpired
    ): void {
        $platform = strtoupper(trim($platform));
        if ($platform === '') return;

        $status = $tokenLikelyExpired ? 'EXPIRED' : 'ERROR';
        $st = $this->pdo->prepare("
            INSERT INTO platform_health
              (platform, last_error_at, last_error, last_duration_ms, token_status, token_expires_at)
            VALUES (?, UTC_TIMESTAMP(3), ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              last_error_at = VALUES(last_error_at),
              last_error = VALUES(last_error),
              last_duration_ms = VALUES(last_duration_ms),
              token_status = VALUES(token_status),
              token_expires_at = COALESCE(VALUES(token_expires_at), token_expires_at)
        ");
        $st->execute([
            $platform,
            SensitiveData::redact($message),
            $durationMs,
            $status,
            $this->toMysqlUtc($tokenExpiresAt),
        ]);
    }

    public function recordBatch(array $event): void
    {
        $runId = trim((string)($event['run_id'] ?? ''));
        if ($runId === '') return;

        $status = strtoupper(trim((string)($event['status'] ?? 'RUNNING')));
        $startedAt = $this->toMysqlUtc($event['started_at'] ?? null) ?? gmdate('Y-m-d H:i:s').'.000';
        $finishedAt = $this->toMysqlUtc($event['finished_at'] ?? null);
        $durationMs = isset($event['duration_ms']) ? max(0, (int)$event['duration_ms']) : null;
        $items = isset($event['items']) ? max(0, (int)$event['items']) : null;
        $error = isset($event['message']) ? SensitiveData::redact((string)$event['message']) : null;

        $st = $this->pdo->prepare("
            INSERT INTO batch_run
              (run_id, started_at, finished_at, duration_ms, status, items, error)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              finished_at = VALUES(finished_at),
              duration_ms = VALUES(duration_ms),
              status = VALUES(status),
              items = VALUES(items),
              error = VALUES(error)
        ");
        $st->execute([$runId, $startedAt, $finishedAt, $durationMs, $status, $items, $error]);
    }

    public function snapshot(): array
    {
        $warningHours = max(1, (int)($this->config['staleWarningHours'] ?? 26));
        $criticalHours = max($warningHours + 1, (int)($this->config['staleCriticalHours'] ?? 72));
        $tokenWarningDays = max(1, (int)($this->config['tokenWarningDays'] ?? 14));

        $latestByPlatform = [];
        $rows = $this->pdo->query("
            SELECT sv.platform,
                   MAX(ms.snapshot_at) AS last_snapshot_at,
                   COUNT(DISTINCT sv.id) AS source_count
            FROM source_video sv
            LEFT JOIN metric_snapshot ms ON ms.source_video_id = sv.id
            WHERE sv.is_active = 1
            GROUP BY sv.platform
            ORDER BY sv.platform
        ")->fetchAll(PDO::FETCH_ASSOC) ?: [];
        foreach ($rows as $row) {
            $latestByPlatform[(string)$row['platform']] = $row;
        }

        $healthRows = [];
        foreach ($this->pdo->query("SELECT * FROM platform_health ORDER BY platform")->fetchAll(PDO::FETCH_ASSOC) ?: [] as $row) {
            $healthRows[(string)$row['platform']] = $row;
        }

        $platforms = [];
        $alerts = [];
        $names = array_values(array_unique(array_merge(array_keys($latestByPlatform), array_keys($healthRows))));
        sort($names);

        foreach ($names as $platform) {
            $db = $latestByPlatform[$platform] ?? [];
            $health = $healthRows[$platform] ?? [];
            $sourceCount = isset($db['source_count']) ? (int)$db['source_count'] : 0;
            $lastSnapshot = $db['last_snapshot_at'] ?? $health['last_snapshot_at'] ?? null;
            $snapshotAgeHours = $this->ageHours($lastSnapshot);
            $lastSuccess = $health['last_success_at'] ?? null;
            $successAgeHours = $this->ageHours($lastSuccess);
            $tokenExpiresAt = $health['token_expires_at'] ?? null;
            $tokenStatus = strtoupper((string)($health['token_status'] ?? 'UNKNOWN'));
            $lastError = SensitiveData::redact(isset($health['last_error']) ? (string)$health['last_error'] : null);
            $status = 'OK';
            $messages = [];

            if ($tokenExpiresAt !== null) {
                $expiresTimestamp = strtotime((string)$tokenExpiresAt.' UTC');
                if ($expiresTimestamp !== false) {
                    $seconds = $expiresTimestamp - time();
                    if ($seconds <= 0) {
                        $status = 'ERROR';
                        $tokenStatus = 'EXPIRED';
                        $messages[] = sprintf('Token %s expiré depuis le %s.', $platform, $tokenExpiresAt);
                    } elseif ($seconds <= $tokenWarningDays * 86400) {
                        $status = 'WARNING';
                        $tokenStatus = 'WARNING';
                        $messages[] = sprintf('Token %s expire le %s.', $platform, $tokenExpiresAt);
                    }
                }
            }

            if ($tokenStatus === 'EXPIRED') {
                $status = 'ERROR';
                $errorSince = $health['last_error_at'] ?? null;
                $messages[] = $errorSince !== null
                    ? sprintf('Token %s expiré ou refusé depuis le %s.', $platform, $errorSince)
                    : sprintf('Token %s expiré ou refusé par la plateforme.', $platform);
            } elseif ($tokenStatus === 'ERROR') {
                $status = 'ERROR';
                $messages[] = sprintf('%s : %s', $platform, $lastError ?? 'erreur de collecte');
            } elseif ($lastError !== null) {
                if ($status === 'OK') $status = 'WARNING';
                $messages[] = sprintf('%s : %s', $platform, $lastError);
            }

            if ($sourceCount > 0 && $lastSnapshot === null) {
                $status = 'ERROR';
                $messages[] = sprintf('%s possède %d source(s) active(s), mais aucun snapshot.', $platform, $sourceCount);
            }

            // La fraîcheur opérationnelle repose sur le dernier succès du collecteur.
            // Un snapshot peut rester ancien volontairement si aucune métrique utile ne change.
            if ($lastSuccess === null) {
                if ($sourceCount > 0 && $status === 'OK') $status = 'WARNING';
                if ($sourceCount > 0) $messages[] = sprintf('%s : aucun succès de collecte rapporté.', $platform);
            } elseif ($successAgeHours !== null && $successAgeHours >= $criticalHours) {
                $status = 'ERROR';
                $messages[] = sprintf('%s : aucun succès de collecte depuis le %s.', $platform, $lastSuccess);
            } elseif ($successAgeHours !== null && $successAgeHours >= $warningHours && $status === 'OK') {
                $status = 'WARNING';
                $messages[] = sprintf('%s : dernière collecte réussie le %s.', $platform, $lastSuccess);
            }

            if ($snapshotAgeHours !== null
                && $snapshotAgeHours >= $criticalHours
                && $successAgeHours !== null
                && $successAgeHours < $warningHours
                && $status === 'OK') {
                $status = 'WARNING';
                $messages[] = sprintf(
                    '%s : collecte récente, mais aucune métrique utile n’a changé depuis le %s.',
                    $platform,
                    $lastSnapshot
                );
            }

            $messages = array_values(array_unique($messages));
            foreach ($messages as $message) $alerts[] = $message;

            $platforms[] = [
                'platform' => $platform,
                'status' => $status,
                'token_status' => $tokenStatus,
                'token_expires_at' => $tokenExpiresAt,
                'last_success_at' => $lastSuccess,
                'success_age_hours' => $successAgeHours,
                'last_snapshot_at' => $lastSnapshot,
                'snapshot_age_hours' => $snapshotAgeHours,
                'last_duration_ms' => isset($health['last_duration_ms']) ? (int)$health['last_duration_ms'] : null,
                'last_items' => isset($health['last_items']) ? (int)$health['last_items'] : null,
                'last_error_at' => $health['last_error_at'] ?? null,
                'last_error' => $lastError,
                'source_count' => $sourceCount,
                'message' => $messages[0] ?? null,
            ];
        }

        $batch = $this->pdo->query("
            SELECT run_id, started_at, finished_at, duration_ms, status, items, error
            FROM batch_run
            ORDER BY started_at DESC
            LIMIT 1
        ")->fetch(PDO::FETCH_ASSOC) ?: null;
        if (is_array($batch) && isset($batch['error'])) {
            $batch['error'] = SensitiveData::redact((string)$batch['error']);
        }

        $refresh = $this->pdo->query("
            SELECT name, dirty_since, last_started_at, last_success_at,
                   last_duration_ms, last_status, last_error
            FROM refresh_job_state
            WHERE name = 'materialized_views'
        ")->fetch(PDO::FETCH_ASSOC) ?: null;
        if (is_array($refresh) && isset($refresh['last_error'])) {
            $refresh['last_error'] = SensitiveData::redact((string)$refresh['last_error']);
        }

        return [
            'ok' => true,
            'service' => 'ugo2-api',
            'now_utc' => gmdate('c'),
            'alerts' => array_values(array_unique($alerts)),
            'platforms' => $platforms,
            'last_batch' => $batch,
            'refresh' => $refresh,
        ];
    }

    private function ensureSchema(): void
    {
        $this->pdo->exec("
            CREATE TABLE IF NOT EXISTS platform_health (
              platform VARCHAR(20) NOT NULL PRIMARY KEY,
              last_success_at DATETIME(3) NULL,
              last_snapshot_at DATETIME(3) NULL,
              last_error_at DATETIME(3) NULL,
              last_error VARCHAR(1000) NULL,
              last_duration_ms BIGINT UNSIGNED NULL,
              last_items INT UNSIGNED NULL,
              token_status VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
              token_expires_at DATETIME(3) NULL,
              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        ");
        $this->pdo->exec("
            CREATE TABLE IF NOT EXISTS batch_run (
              run_id VARCHAR(64) NOT NULL PRIMARY KEY,
              started_at DATETIME(3) NOT NULL,
              finished_at DATETIME(3) NULL,
              duration_ms BIGINT UNSIGNED NULL,
              status VARCHAR(16) NOT NULL,
              items INT UNSIGNED NULL,
              error VARCHAR(1000) NULL,
              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              KEY idx_batch_run_started (started_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        ");
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
        $this->pdo->exec("INSERT IGNORE INTO refresh_job_state(name, last_status) VALUES ('materialized_views', 'NEVER')");
    }

    private function ageHours(mixed $value): ?float
    {
        if ($value === null || $value === '') return null;
        $ts = strtotime((string)$value.' UTC');
        if ($ts === false) return null;
        return round(max(0, time() - $ts) / 3600, 1);
    }

    private function toMysqlUtc(mixed $value): ?string
    {
        if ($value === null || $value === '') return null;
        try {
            $dt = new \DateTimeImmutable((string)$value);
            return $dt->setTimezone(new \DateTimeZone('UTC'))->format('Y-m-d H:i:s.v');
        } catch (\Throwable) {
            return null;
        }
    }
}
