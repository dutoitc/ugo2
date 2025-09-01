<?php
declare(strict_types=1);
namespace Web;

final class Util {
  public static function jsonInput(): array {
    $raw = file_get_contents('php://input') ?: '';
    $data = json_decode($raw, true);
    if (!is_array($data)) { http_response_code(400); echo json_encode(['error'=>'invalid_json']); exit; }
    return $data;
  }
  public static function qp(string $name, $default = null): ?string {
    return isset($_GET[$name]) ? trim((string)$_GET[$name]) : $default;
  }
  public static function paging(): array {
    $page = max(1, (int)($_GET['page'] ?? 1));
    $size = (int)($_GET['pageSize'] ?? 50);
    $size = ($size > 200) ? 200 : max(1, $size);
    $offset = ($page - 1) * $size;
    return [$page, $size, $offset];
  }
}
