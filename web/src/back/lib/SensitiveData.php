<?php
declare(strict_types=1);

namespace Web\Lib;

final class SensitiveData
{
    private const SECRET_NAME = 'access[_-]?token|api[_-]?key|apikey|key|token|secret|client[_-]?secret|password|passwd|authorization|x-api-key|x-api-sig|signature|sig|x-amz-signature|x-goog-signature';

    public static function redact(?string $value, int $maxLength = 1000): ?string
    {
        if ($value === null || $value === '') return $value;

        $redacted = preg_replace(
            '~(\bauthorization\s*[:=]\s*)(?:(?:bearer|basic)\s+)?[A-Za-z0-9._~+/=-]+~i',
            '$1[REDACTED]',
            $value
        ) ?? $value;
        $redacted = preg_replace('~(\bbearer\s+)[A-Za-z0-9._~+/=-]+~i', '$1[REDACTED]', $redacted) ?? $redacted;
        $redacted = preg_replace(
            '~([?&](?:'.self::SECRET_NAME.')=)[^&#\s]*~i',
            '$1[REDACTED]',
            $redacted
        ) ?? $redacted;
        $redacted = preg_replace(
            '~((?:["\']?(?:'.self::SECRET_NAME.')["\']?)\s*[:=]\s*)(?!\[REDACTED\])(?:"(?:\\\\.|[^"])*"|\'(?:\\\\.|[^\'])*\'|[^\s,;&}\]]+)~i',
            '$1[REDACTED]',
            $redacted
        ) ?? $redacted;
        $redacted = preg_replace('~(https?://[^:/@\s]+:)[^@/\s]+(@)~i', '$1[REDACTED]$2', $redacted) ?? $redacted;

        if (strlen($redacted) <= $maxLength) return $redacted;
        return substr($redacted, 0, max(0, $maxLength)).'…';
    }

    public static function throwable(\Throwable $error): string
    {
        return $error::class.': '.(self::redact($error->getMessage()) ?? '');
    }
}
