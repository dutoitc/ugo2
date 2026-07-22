<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Auth;
use Web\Config;
use Web\Db;
use Web\Lib\Http;
use Web\Lib\SensitiveData;
use Web\Services\HealthStateService;

final class HealthController
{
    public function __construct(private Db $db, private Auth $auth) {}

    public function health(): void
    {
        try {
            $cfg = Config::load();
            $state = new HealthStateService($this->db, $cfg['health'] ?? []);
            Http::json($state->snapshot(), 200);
        } catch (\Throwable $e) {
            error_log('[health] '.SensitiveData::throwable($e));
            Http::json([
                'ok' => false,
                'service' => 'ugo2-api',
                'error' => 'health_unavailable',
            ], 503);
        }
    }

    public function report(): void
    {
        if (method_exists($this->auth, 'requireIngestAuth')) {
            $this->auth->requireIngestAuth();
        } elseif (method_exists($this->auth, 'requireValidRequest')) {
            $this->auth->requireValidRequest();
        }

        $event = Http::readJson();
        $type = strtolower(trim((string)($event['type'] ?? '')));
        $cfg = Config::load();
        $state = new HealthStateService($this->db, $cfg['health'] ?? []);

        if ($type === 'platform') {
            $platform = strtoupper(trim((string)($event['platform'] ?? '')));
            $status = strtoupper(trim((string)($event['status'] ?? 'ERROR')));
            if ($platform === '') {
                Http::json(['error'=>'bad_request', 'message'=>'platform is required'], 400);
                return;
            }

            if ($status === 'SUCCESS') {
                $state->recordPlatformSuccess(
                    $platform,
                    isset($event['snapshot_at']) ? (string)$event['snapshot_at'] : null,
                    isset($event['duration_ms']) ? (int)$event['duration_ms'] : null,
                    isset($event['items']) ? (int)$event['items'] : null,
                    isset($event['token_expires_at']) ? (string)$event['token_expires_at'] : null
                );
            } else {
                $state->recordPlatformError(
                    $platform,
                    (string)($event['message'] ?? 'Erreur inconnue'),
                    isset($event['duration_ms']) ? (int)$event['duration_ms'] : null,
                    isset($event['token_expires_at']) ? (string)$event['token_expires_at'] : null,
                    (bool)($event['token_likely_expired'] ?? false)
                );
            }
            Http::json(['ok'=>true], 200);
            return;
        }

        if ($type === 'batch') {
            $state->recordBatch($event);
            Http::json(['ok'=>true], 200);
            return;
        }

        Http::json(['error'=>'bad_request', 'message'=>'type must be platform or batch'], 400);
    }
}
