package ch.mno.ugo2.util;

import java.util.Locale;

public final class TeaserHeuristics {
    private TeaserHeuristics(){}
    private static final String[] KEYWORDS = {
        "teaser","extrait","trailer","bande-annonce","short","extract","sneak peek"
    };
    public static boolean isTeaser(String title, String description, Integer durationSec) {
        String t = (title == null ? "" : title).toLowerCase(Locale.ROOT);
        String d = (description == null ? "" : description).toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) {
            if (t.contains(k) || d.contains(k)) return true;
        }
        if (durationSec != null && durationSec <= 45) return true;
        return false;
    }
}
