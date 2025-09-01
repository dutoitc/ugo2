<?php
declare(strict_types=1);

namespace Web;

use PDO;

final class Db {
  private PDO $pdo;

  public function __construct(array $cfg) {
    $dsn  = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4', $cfg['host'], $cfg['port'], $cfg['name']);
    $this->pdo = new PDO($dsn, $cfg['user'], $cfg['pass'], [
      PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
      PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
      PDO::ATTR_EMULATE_PREPARES => false,
    ]);
  }
  public function pdo(): PDO { return $this->pdo; }

  public function tx(callable $fn) {
    try {
      $this->pdo->beginTransaction();
      $res = $fn($this->pdo);
      $this->pdo->commit();
      return $res;
    } catch (\Throwable $e) {
      if ($this->pdo->inTransaction()) $this->pdo->rollBack();
      throw $e;
    }
  }
}
