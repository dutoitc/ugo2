package ch.mno.ugo2.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IsoDurationsTest {
    @Test
    void parseSeconds_ok() {
        assertEquals(65, IsoDurations.toSeconds("PT1M5S"));
        assertEquals(3600, IsoDurations.toSeconds("PT1H"));
        assertNull(IsoDurations.toSeconds(""));
        assertNull(IsoDurations.toSeconds("oops"));
    }
}
