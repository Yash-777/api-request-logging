package com.github.yash777.apirequestlogging.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ApiLoggingFilter#formatElapsed(long)}.
 *
 * <p>This is a pure static method — no Spring context required.</p>
 */
public class ApiLoggingFilterFormatElapsedTest {

    @Test
    @DisplayName("formats zero milliseconds")
    void zero() {
        assertEquals("0h 0m 0s 0ms", ApiLoggingFilter.formatElapsed(0L));
    }

    @Test
    @DisplayName("formats sub-second duration")
    void subSecond() {
        assertEquals("0h 0m 0s 312ms", ApiLoggingFilter.formatElapsed(312L));
    }

    @Test
    @DisplayName("formats exactly one second")
    void oneSecond() {
        assertEquals("0h 0m 1s 0ms", ApiLoggingFilter.formatElapsed(1_000L));
    }

    @Test
    @DisplayName("formats seconds and millis")
    void secondsAndMillis() {
        assertEquals("0h 0m 1s 500ms", ApiLoggingFilter.formatElapsed(1_500L));
    }

    @Test
    @DisplayName("formats exactly one minute")
    void oneMinute() {
        assertEquals("0h 1m 0s 0ms", ApiLoggingFilter.formatElapsed(60_000L));
    }

    @Test
    @DisplayName("formats minutes and seconds")
    void minutesAndSeconds() {
        assertEquals("0h 1m 5s 312ms", ApiLoggingFilter.formatElapsed(65_312L));
    }

    @Test
    @DisplayName("formats exactly one hour")
    void oneHour() {
        assertEquals("1h 0m 0s 0ms", ApiLoggingFilter.formatElapsed(3_600_000L));
    }

    @Test
    @DisplayName("formats hours, minutes, seconds, millis")
    void fullDuration() {
        assertEquals("1h 2m 3s 500ms", ApiLoggingFilter.formatElapsed(3_723_500L));
    }

    @Test
    @DisplayName("formats typical fast API response (< 100ms)")
    void typicalFastResponse() {
        assertEquals("0h 0m 0s 87ms", ApiLoggingFilter.formatElapsed(87L));
    }

    @Test
    @DisplayName("formats typical slow API response (> 2s)")
    void typicalSlowResponse() {
        assertEquals("0h 0m 2s 309ms", ApiLoggingFilter.formatElapsed(2_309L));
    }
}
