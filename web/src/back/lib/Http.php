<?php
declare(strict_types=1);

namespace Web\Lib;

final class Http
{
    /** @return array{0:int,1:array} */
    public static function readJson(): array
    {
        $raw = file_get_contents('php://input') ?: '';
        $ct  = $_SERVER['CONTENT_TYPE'] ?? '';
        if (stripos($ct, 'application/json') === false) {
            return [415, ['error'=>'unsupported_media_type']];
        }
        $data = json_decode($raw, true);
        if (!is_array($data)) {
            return [400, ['error'=>'bad_request','detail'=>'JSON invalide']];
        }
        return [200, $data];
    }

    public static function json($payload, int $code=200): void
    {
        header('Content-Type: application/json; charset=utf-8');
        http_response_code($code);
        echo json_encode($payload, JSON_UNESCAPED_UNICODE);
    }
}
