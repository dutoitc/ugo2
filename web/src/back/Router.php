<?php
declare(strict_types=1);

namespace Web;

final class Router {
  private string $method;
  private string $path;
  /** @var array<string,array<string,callable>> */
  private array $routes = ['GET'=>[], 'POST'=>[]];

  public function __construct(string $method, string $uri) {
    $this->method = strtoupper($method);
    $this->path   = parse_url($uri, PHP_URL_PATH) ?: '/';
  }
  public function get(string $path, callable $h): void  { $this->routes['GET'][$path]  = $h; }
  public function post(string $path, callable $h): void { $this->routes['POST'][$path] = $h; }

  public function run(): void {
    $h = $this->routes[$this->method][$this->path] ?? null;
    if (!$h) { http_response_code(404); echo json_encode(['error'=>'not_found']); return; }
    $out = $h();
    if (is_array($out)) echo json_encode($out);
    elseif (is_string($out)) echo $out;
  }
}
