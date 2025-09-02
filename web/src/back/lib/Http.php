<?php
declare(strict_types=1);

namespace Web\Lib;

final class Http
{
     private static ?string $RAW = null;

        public static function rawBody(): string
        {
            if (self::$RAW !== null) return self::$RAW;
            // lit UNE fois puis cache
            self::$RAW = file_get_contents('php://input') ?: '';
            return self::$RAW;
        }

        public static function readJson(): array
        {
            $raw = self::rawBody();
            $data = json_decode($raw, true);
            if (json_last_error() !== JSON_ERROR_NONE || !is_array($data)) {
                header('Content-Type: application/json; charset=utf-8');
                http_response_code(400);
                echo json_encode(['error' => 'invalid_json', 'details' => json_last_error_msg()], JSON_UNESCAPED_UNICODE);
                exit;
            }
            return $data;
        }


    public static function json($payload, int $code=200): void
    {
        header('Content-Type: application/json; charset=utf-8');
        http_response_code($code);
        echo json_encode($payload, JSON_UNESCAPED_UNICODE);
    }


}
