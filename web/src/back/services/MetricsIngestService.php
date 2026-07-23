<?php
declare(strict_types=1);

namespace Web\Services;

use Web\Db;
use Web\Domain\MetricsSnapshot;
use Web\Repositories\SourceVideoRepository;
use Web\Repositories\MetricsSnapshotRepository;
use Web\Lib\SensitiveData;

final class MetricsIngestService
{
    private \PDO $pdo;
    private SourceVideoRepository $sources;
    private MetricsSnapshotRepository $metrics;
    private int $minDeltaAbs;
    private float $minDeltaRel;
    private bool $dailyGuard;

    public function __construct(private Db $db, array $config = [])
    {
        $this->pdo = $db->pdo();
        $this->sources = new SourceVideoRepository($db);
        $this->metrics = new MetricsSnapshotRepository($db);
        $this->minDeltaAbs = max(0, (int)($config['minDeltaAbs'] ?? 10));
        $this->minDeltaRel = max(0.0, (float)($config['minDeltaRel'] ?? 0.01));
        $this->dailyGuard = (bool)($config['dailyGuard'] ?? true);
    }

    /**
     * @param array<int,array<string,mixed>> $snapshots
     * @return array{ok:int,ko:int,stored:int,skipped:int,monotonic_corrections:int,items:array<int,array<string,mixed>>}
     */
    public function ingestBatch(array $snapshots): array
    {
        $nowIsoMsUtc = substr(
            (new \DateTimeImmutable('now', new \DateTimeZone('UTC')))->format('Y-m-d H:i:s.u'),
            0,
            23
        );

        $ok = 0;
        $ko = 0;
        $stored = 0;
        $skipped = 0;
        $monotonicCorrections = 0;
        $items = [];

        $this->pdo->beginTransaction();
        try {
            foreach ($snapshots as $i => $s) {
                try {
                    $dto = MetricsSnapshot::fromArray($s, $nowIsoMsUtc);

                    $sourceId = $dto->source_video_id;
                    if ($sourceId === null) {
                        $sourceId = $this->sources->findIdByPlatformAndVideoId(
                            $dto->platform,
                            (string)$dto->platform_video_id
                        );
                        if ($sourceId === null) {
                            $sourceId = $this->sources->createMinimal(
                                $dto->platform,
                                $dto->platform_format,
                                (string)$dto->platform_video_id
                            );
                        } elseif ($dto->platform_format !== null) {
                            $this->sources->setPlatformFormatIfNull($sourceId, $dto->platform_format);
                        }
                    }

                    $decision = $this->metrics->storeIfUseful(
                        $sourceId,
                        $dto,
                        $this->minDeltaAbs,
                        $this->minDeltaRel,
                        $this->dailyGuard
                    );

                    $ok++;
                    $monotonicCorrections += $decision['monotonic_corrections'];
                    if ($decision['stored']) $stored++; else $skipped++;
                    $items[] = [
                        'i' => $i,
                        'status' => $decision['stored'] ? 'stored' : 'skipped',
                        'reason' => $decision['reason'],
                        'source_video_id' => $sourceId,
                        'snapshot_at' => $dto->snapshot_atIso,
                        'views_delta' => $decision['views_delta'],
                        'monotonic_corrections' => $decision['monotonic_corrections'],
                    ];
                } catch (\Throwable $e) {
                    $ko++;
                    $items[] = ['i'=>$i, 'status'=>'error', 'message'=>SensitiveData::redact($e->getMessage())];
                }
            }
            $this->pdo->commit();
        } catch (\Throwable $e) {
            $this->pdo->rollBack();
            throw $e;
        }

        return [
            'ok' => $ok,
            'ko' => $ko,
            'stored' => $stored,
            'skipped' => $skipped,
            'monotonic_corrections' => $monotonicCorrections,
            'items' => $items,
        ];
    }
}
