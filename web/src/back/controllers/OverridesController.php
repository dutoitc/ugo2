<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Db;
use Web\Auth;
use Web\Util;
use PDO;

/**
 * Crée des overrides dans reconcile_override.
 * Exemple d'item JSON:
 * {
 *   "source_platform": "YOUTUBE",
 *   "source_platform_id": "abcd1234",   // -> source_video.platform_video_id
 *   "action": "LINK" | "UNLINK",
 *   "target_video_id": 42                // requis pour LINK
 * }
 */
final class OverridesController
{
    public function __construct(private Db $db, private Auth $auth) {}

    public function apply(): array
    {
        $items = Util::jsonInput();
        $applied = 0; $unknown = 0; $invalid = 0;

        $selSrc = $this->db->pdo()->prepare("
          SELECT id
          FROM source_video
          WHERE platform = ?
            AND platform_video_id = ?
        ");

        $insOv  = $this->db->pdo()->prepare("
          INSERT INTO reconcile_override (source_video_id, action, target_video_id, created_by)
          VALUES (?, ?, ?, 'api')
        ");

        $this->db->tx(function(PDO $tx) use ($items, $selSrc, $insOv, &$applied, &$unknown, &$invalid) {
            foreach ((array)$items as $it) {
                $platform = (string)($it['source_platform'] ?? '');
                $pvid     = (string)($it['source_platform_id'] ?? '');
                $action   = strtoupper((string)($it['action'] ?? ''));
                $targetId = isset($it['target_video_id']) ? (int)$it['target_video_id'] : null;

                if ($platform === '' || $pvid === '' || !in_array($action, ['LINK','UNLINK'], true)) {
                    $invalid++;
                    continue;
                }
                if ($action === 'LINK' && $targetId === null) {
                    $invalid++;
                    continue;
                }

                $selSrc->execute([$platform, $pvid]);
                $row = $selSrc->fetch(PDO::FETCH_ASSOC);
                if (!$row) { $unknown++; continue; }

                $sid = (int)$row['id'];
                $insOv->execute([$sid, $action, $targetId]);
                $applied++;
            }
        });

        return ['ok' => true, 'createdOverrides' => $applied, 'unknownSources' => $unknown, 'invalid' => $invalid];
    }
}
