<?php
declare(strict_types=1);

namespace Web\Services;

use Web\Db;
use Web\Domain\MetricsSnapshot;
use Web\Repositories\SourceVideoRepository;
use Web\Repositories\MetricsSnapshotRepository;

final class MetricsIngestService
{
    private \PDO $pdo;
    private SourceVideoRepository $sources;
    private MetricsSnapshotRepository $metrics;

    public function __construct(private Db $db)
    {
        if (method_exists($db, 'pdo')) $this->pdo = $db->pdo();
        elseif (method_exists($db, 'getPdo')) $this->pdo = $db->getPdo();
        else throw new \RuntimeException('Db must expose PDO via pdo() or getPdo()');

        $this->sources = new SourceVideoRepository($db);
        $this->metrics = new MetricsSnapshotRepository($db);
    }

    /**
     * @param array<int,array<string,mixed>> $snapshots
     * @return array{ok:int,ko:int,items:array<int,array<string,mixed>>}
     */
    public function ingestBatch(array $snapshots): array
    {
        $nowIsoMsUtc = substr(
                (new \DateTimeImmutable('now', new \DateTimeZone('UTC')))->format('Y-m-d H:i:s.u'),
                0, 23
            );

        $ok=0; $ko=0; $items=[];
        $this->pdo->beginTransaction();
        try {
            foreach ($snapshots as $i => $s) {
                try {
                    $dto = MetricsSnapshot::fromArray($s, $nowIsoMsUtc);

                    $sourceId = $dto->source_video_id;
                    if ($sourceId === null) {
                        $sourceId = $this->sources->findIdByPlatformAndVideoId($dto->platform, (string)$dto->platform_video_id);
                        if ($sourceId === null) {
                            // création minimale (active)
                            $sourceId = $this->sources->createMinimal($dto->platform, $dto->platform_format, (string)$dto->platform_video_id);
                        } else {
                            // si format fourni, on le renseigne si NULL
                            $this->sources->setPlatformFormatIfNull($sourceId, $dto->platform_format);
                        }
                    }

                    $this->metrics->upsert($sourceId, $dto);

                    $ok++;
                    $items[] = ['i'=>$i,'status'=>'ok','source_video_id'=>$sourceId,'snapshot_at'=>$dto->snapshot_atIso];
                } catch (\Throwable $e) {
                    $ko++;
                    $items[] = ['i'=>$i,'status'=>'error','message'=>$e->getMessage()];
                }
            }
            $this->pdo->commit();
        } catch (\Throwable $e) {
            $this->pdo->rollBack();
            throw $e;
        }

        return compact('ok','ko','items');
    }
}
