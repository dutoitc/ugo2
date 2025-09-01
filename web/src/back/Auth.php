<?php
declare(strict_types=1);

namespace Web;

final class Auth {
  /** @var array<string,string> keyId => secret */
  private array $keys;
  private Db $db;

  public function __construct(array $keys, Db $db) { 
    $this->keys = $keys; 
    $this->db = $db;
  }

  public function guardIngest(callable $handler) {
    $keyId = $_SERVER['HTTP_X_API_KEY'] ?? '';
    $ts    = $_SERVER['HTTP_X_API_TS'] ?? '';
    $nonce = $_SERVER['HTTP_X_API_NONCE'] ?? '';
    $sig   = $_SERVER['HTTP_X_API_SIG'] ?? '';
    $idem  = $_SERVER['HTTP_IDEMPOTENCY_KEY'] ?? '';

    if (!$keyId || !$ts || !$nonce || !$sig) {
      http_response_code(401);
      echo json_encode(['error'=>'auth_missing_headers']); return;
    }
    $secret = $this->keys[$keyId] ?? null;
    if (!$secret) { http_response_code(401); echo json_encode(['error'=>'unknown_key']); return; }

    $now = time();
    if (abs($now - (int)$ts) > 300) { // 5 min
      http_response_code(401); echo json_encode(['error'=>'ts_out_of_range']); return;
    }

    $method = $_SERVER['REQUEST_METHOD'] ?? 'POST';
    $path   = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
    $body   = file_get_contents('php://input') ?: '';
    $bodyHash = hash('sha256', $body);

    $toSign = $keyId . "\n" . $ts . "\n" . $nonce . "\n" . $method . "\n" . $path . "\n" . $bodyHash;
    $calc   = base64_encode(hash_hmac('sha256', $toSign, $secret, true));
    if (!hash_equals($calc, $sig)) {
      http_response_code(401); echo json_encode(['error'=>'bad_signature']); return;
    }

    // Idempotency replay if present
    if ($idem) {
      $stmt = $this->db->pdo()->prepare("SELECT response_code, response_body FROM api_idempotency WHERE idem_key=?");
      $stmt->execute([$idem]);
      if ($row = $stmt->fetch()) {
        http_response_code((int)$row['response_code']);
        header('X-Idempotent-Replay: 1');
        echo $row['response_body']; return;
      }
    }

    ob_start();
    $code = 200; $payload = null;
    try {
      $payload = $handler();
    } catch (\Throwable $e) {
      $code = 500;
      $payload = ['error'=>'server_error','message'=>$e->getMessage()];
    }
    $bodyOut = is_string($payload) ? $payload : json_encode($payload ?? []);
    http_response_code($code);

    if (!empty($idem)) {
      try {
        $ins = $this->db->pdo()->prepare("INSERT INTO api_idempotency(idem_key,route,request_hash,response_code,response_body) VALUES(?,?,?,?,?)");
        $ins->execute([$idem, $path, $bodyHash, $code, $bodyOut]);
      } catch (\Throwable $e) { /* ignore */ }
    }

    echo $bodyOut;
  }
}
