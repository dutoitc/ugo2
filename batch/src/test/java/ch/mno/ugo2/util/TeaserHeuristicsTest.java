package ch.mno.ugo2.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TeaserHeuristicsTest {
    @Test
    void detects_keywords_or_short_duration() {
        assertTrue(TeaserHeuristics.isTeaser("Teaser: XYZ", "", 120));
        assertTrue(TeaserHeuristics.isTeaser("", "extrait de l'émission", 120));
        assertTrue(TeaserHeuristics.isTeaser("vidéo", "", 30));
        assertFalse(TeaserHeuristics.isTeaser("Long format", "rien de spécial", 300));
    }
}
