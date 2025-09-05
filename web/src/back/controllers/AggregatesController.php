<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Auth;
use Web\Db;
use Web\Lib\Http;

final class AggregatesController
{
    public function __construct(
        private Db $db,
        private ?Auth $auth = null
    ) {}

    public function presenters(): void
    {
        Http::json(['error'=>'not_implemented','message'=>'Aggregates by presenter require schema with presenters.'], 501);
    }

    public function realisateurs(): void
    {
        Http::json(['error'=>'not_implemented','message'=>'Aggregates by director require schema with directors.'], 501);
    }
}
