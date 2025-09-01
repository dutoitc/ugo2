<?php
declare(strict_types=1);

namespace Web;

final class Config {
  public static function load(): array {
    $file = __DIR__ . '/config/config.php';
    if (!is_file($file)) {
      http_response_code(500);
      echo json_encode(['error'=>'missing_config','message'=>'Copy src/back/config/config.php.tmpl -> config.php']);
      exit;
    }
    /** @var array $cfg */
    $cfg = require $file;
    return $cfg;
  }
}
