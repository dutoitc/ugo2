<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Db;
use Web\Auth;
use Web\Lib\Http;
use PDO;

final class SourcesIngestController
{
    public function __construct(private Db $db, private Auth $auth) {}

    public function filterMissing(): void
    {
        [$code, $in] = Http::readJson();
        if ($code !== 200) { Http::json($in, $code); return; }

        $platform    = $in['platform']    ?? null;
        $externalIds = $in['externalIds'] ?? null;

        if (!is_string($platform) || !is_array($externalIds) || !$externalIds) {
            Http::json(['error'=>'bad_request','detail'=>'externalIds[] manquant'], 400);
            return;
        }

        $placeholders = implode(',', array_fill(0, count($externalIds), '?'));
        $sql = "SELECT external_id FROM sources WHERE platform = ? AND external_id IN ($placeholders)";
        $stmt = $this->db->pdo()->prepare($sql);
        $stmt->execute(array_merge([$platform], array_values($externalIds)));
        $found = array_column($stmt->fetchAll(PDO::FETCH_ASSOC), 'external_id');

        $missing = array_values(array_diff($externalIds, $found));
        Http::json(['missing'=>$missing]);
    }

    public function batchUpsert(): void
    {
        [$code, $items] = Http::readJson();
        if ($code !== 200) { Http::json($items, $code); return; }
        if (!is_array($items)) { Http::json(['error'=>'bad_request'], 400); return; }

        $upserted = 0;
        $this->db->tx(function(\PDO $pdo) use ($items, &$upserted) {
            $sql = "INSERT INTO sources (platform, platform_source_id, external_id, title, channel_id)
                    VALUES (:platform,:platform_source_id,:external_id,:title,:channel_id)
                    ON DUPLICATE KEY UPDATE
                      title = VALUES(title),
                      channel_id = VALUES(channel_id)";
            $stmt = $pdo->prepare($sql);

            foreach ($items as $it) {
                if (!is_array($it)) continue;
                $stmt->execute([
                    ':platform'           => $it['platform']            ?? 'YOUTUBE',
                    ':platform_source_id' => $it['platform_source_id']  ?? null,
                    ':external_id'        => $it['externalId']          ?? ($it['external_id'] ?? null),
                    ':title'              => $it['title']               ?? null,
                    ':channel_id'         => $it['channel_id']          ?? null,
                ]);
                $upserted++;
            }
        });

        Http::json(['upserted'=>$upserted]);
    }
}
