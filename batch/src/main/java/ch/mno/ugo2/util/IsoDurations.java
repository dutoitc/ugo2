package ch.mno.ugo2.util;

import java.time.Duration;

public final class IsoDurations {
    private IsoDurations(){}
    public static Integer toSeconds(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Math.toIntExact(Duration.parse(iso).getSeconds());
        } catch (Exception e) {
            return null;
        }
    }
}
