<?php
declare(strict_types=1);
namespace Web\Controllers;
use Web\Db;
use Web\Util;
use PDO;

final class OverridesController {
  public static function apply(Db $db, $auth): array {
    $items = Util::jsonInput();
    $n=0; $unknown=0;
    $sel = $db->pdo()->prepare("SELECT id FROM source_video WHERE platform=? AND platform_source_id=?");
    $upd = $db->pdo()->prepare("UPDATE source_video SET video_id=?, is_teaser=IFNULL(?, is_teaser), locked=IFNULL(?,locked) WHERE id=?");
    $insAudit = $db->pdo()->prepare("INSERT INTO reconcile_override(source_video_id, action, target_video_id, created_by) VALUES(?, ?, ?, 'api')");

    $db->tx(function(PDO $pdo) use ($items, $sel, $upd, $insAudit, &$n, &$unknown) {
      foreach ($items as $it) {
        $sel->execute([$it['source_platform'] ?? '', $it['source_platform_id'] ?? '']);
        $row = $sel->fetch();
        if (!$row) { $unknown++; continue; }
        $sid = (int)$row['id'];
        $action = strtoupper((string)($it['action'] ?? ''));
        $target = isset($it['target_video_id']) ? (int)$it['target_video_id'] : null;
        $lock   = isset($it['lock']) ? (int)$it['lock'] : null;

        $newVid = null; $teaser = null;
        if ($action === 'LINK')   { $newVid = $target; }
        if ($action === 'UNLINK') { $newVid = null; }
        if ($action === 'TEASER') { $teaser = 1; }
        if ($action === 'MAIN')   { $teaser = 0; }
        $upd->execute([$newVid, $teaser, $lock, $sid]);
        $insAudit->execute([$sid, $action, $target]);
        $n++;
      }
    });
    return ['ok'=>true,'applied'=>$n,'unknownSources'=>$unknown];
  }
}
