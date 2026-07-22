<?php
declare(strict_types=1);

require_once __DIR__.'/../src/back/lib/SensitiveData.php';

use Web\Lib\SensitiveData;

$input = 'https://example.org/api?access_token=meta-value&key=youtube-value'
    .' Authorization: Bearer bearer-value'
    .' authorization=Basic YmFzaWMtdmFsdWU='
    .' body={"client_secret":"client-value"}'
    .' signed=https://storage.example/file?X-Amz-Signature=signed-value';
$result = SensitiveData::redact($input) ?? '';

foreach (['meta-value', 'youtube-value', 'bearer-value', 'YmFzaWMtdmFsdWU=', 'client-value', 'signed-value'] as $secret) {
    if (str_contains($result, $secret)) {
        fwrite(STDERR, "Secret not redacted: {$secret}\n");
        exit(1);
    }
}

if (SensitiveData::redact('https://user:private-value@example.org/api')
    !== 'https://user:[REDACTED]@example.org/api') {
    fwrite(STDERR, "URL password not redacted\n");
    exit(1);
}

echo "SensitiveDataTest: OK\n";
