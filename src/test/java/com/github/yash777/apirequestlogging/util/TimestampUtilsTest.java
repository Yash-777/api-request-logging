package com.github.yash777.apirequestlogging.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TimestampUtils}.
 */
public class TimestampUtilsTest {

    // Pattern: d/M/yyyy, h:mm:ss am/pm  (e.g. "6/4/2026, 2:54:43 pm")
    private static final java.util.regex.Pattern TIMESTAMP_PATTERN =
            java.util.regex.Pattern.compile(
                    "^\\d{1,2}/\\d{1,2}/\\d{4}, \\d{1,2}:\\d{2}:\\d{2} (am|pm)$");

    @Test
    @DisplayName("getCurrentTimestamp() returns a non-null, non-empty string")
    void nonNull() {
        String ts = TimestampUtils.getCurrentTimestamp();
        assertNotNull(ts);
        assertFalse(ts.isEmpty());
    }

    @Test
    @DisplayName("getCurrentTimestamp() matches expected format pattern")
    void formatPattern() {
        String ts = TimestampUtils.getCurrentTimestamp();
        assertTrue(TIMESTAMP_PATTERN.matcher(ts).matches(),
                "Timestamp '" + ts + "' did not match expected pattern d/M/yyyy, h:mm:ss am/pm");
    }

    @Test
    @DisplayName("getCurrentTimestamp() is lowercase (am/pm not AM/PM)")
    void isLowercase() {
        String ts = TimestampUtils.getCurrentTimestamp();
        assertEquals(ts, ts.toLowerCase(),
                "Timestamp should be all lowercase");
    }

    @Test
    @DisplayName("getCurrentTimestampByZone() returns correct format for UTC")
    void utcZone() {
        String ts = TimestampUtils.getCurrentTimestampByZone("UTC");
        assertTrue(TIMESTAMP_PATTERN.matcher(ts).matches(),
                "UTC timestamp '" + ts + "' did not match pattern");
    }

    @Test
    @DisplayName("getCurrentTimestampByZone() returns correct format for Asia/Kolkata")
    void kolkataZone() {
        String ts = TimestampUtils.getCurrentTimestampByZone("Asia/Kolkata");
        assertTrue(TIMESTAMP_PATTERN.matcher(ts).matches(),
                "Kolkata timestamp '" + ts + "' did not match pattern");
    }

    @Test
    @DisplayName("getCurrentTimestampByZone() throws for unknown zone id")
    void unknownZoneThrows() {
        assertThrows(java.time.zone.ZoneRulesException.class,
                () -> TimestampUtils.getCurrentTimestampByZone("NotAReal/Zone"));
    }

    @Test
    @DisplayName("getCurrentTimestamp() returns a time within 5 seconds of now")
    void timestampIsRecent() {
        // Parse the result back and check it is close to the current time
        String ts = TimestampUtils.getCurrentTimestamp();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d/M/yyyy, h:mm:ss a")
                .withLocale(java.util.Locale.ENGLISH);
        java.time.LocalDateTime parsed = java.time.LocalDateTime.parse(
                ts.toUpperCase().replace("AM", "AM").replace("PM", "PM"), fmt);
        long secondsDiff = Math.abs(
                java.time.Duration.between(parsed, java.time.LocalDateTime.now()).getSeconds());
        assertTrue(secondsDiff < 5,
                "Timestamp should be within 5 seconds of now, diff was: " + secondsDiff + "s");
    }
}
