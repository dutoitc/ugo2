<?php
declare(strict_types=1);

namespace Web\Controllers\Videos;



final class Paginator
{
    public function __construct(
        public readonly int $page,
        public readonly int $size,
    ) {}

    public function offset(): int
    {
        return ($this->page - 1) * $this->size;
    }

    public static function from(?int $page, ?int $size, int $defaultSize = 20, int $max = 5000): self
    {
        $p = max(1, $page ?? 1);
        $s = $size ?? $defaultSize;

        if ($s < 1) {
            $s = $defaultSize;
        }
        if ($s > $max) {
            $s = $max;
        }

        return new self($p, $s);
    }
}
