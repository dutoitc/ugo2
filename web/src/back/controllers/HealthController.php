<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Db;
use Web\Auth;
use Web\Lib\Http;

final class HealthController
{
    public function __construct(private Db $db, private Auth $auth) {}

    public function health(): void
    {
        Http::json(['ok'=>true,'service'=>'ugo2-api','version'=>'router-1']);
    }
}
