package ch.mno.ugo2.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public final class PollingPolicy {
    private PollingPolicy(){}

    // Réglages : J0–J3 intensif (toutes 2h), J4–J7 (toutes 6h), puis 1/jour
    private static final Duration INTENSIVE = Duration.ofHours(2);
    private static final Duration OFTEN     = Duration.ofHours(6);
    private static final Duration DAILY     = Duration.ofDays(1);

    public static boolean shouldPoll(Instant publishedAt, Instant lastSnapshotAt, Instant nowUtc) {
        if (publishedAt == null) return lastOlderThan(lastSnapshotAt, DAILY, nowUtc);

        long ageDays = Duration.between(publishedAt, nowUtc).toDays();

        Duration minInterval = ageDays <= 3 ? INTENSIVE : (ageDays <= 7 ? OFTEN : DAILY);
        return lastOlderThan(lastSnapshotAt, minInterval, nowUtc);
    }

    private static boolean lastOlderThan(Instant last, Duration minInterval, Instant now) {
        if (last == null) return true;
        return last.plus(minInterval).isBefore(now);
    }

    public static Duration nextDelay(Instant publishedAt, Instant lastSnapshotAt, Instant nowUtc) {
        long ageDays = (publishedAt == null) ? 8 : Duration.between(publishedAt, nowUtc).toDays();
        Duration minInterval = ageDays <= 3 ? INTENSIVE : (ageDays <= 7 ? OFTEN : DAILY);
        if (lastSnapshotAt == null) return Duration.ZERO;
        var next = lastSnapshotAt.plus(minInterval);
        return next.isAfter(nowUtc) ? Duration.between(nowUtc, next) : Duration.ZERO;
    }
}
