package com.github.yash777.apirequestlogging;

import com.github.yash777.apirequestlogging.filter.ApiLoggingFilter;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;
import com.github.yash777.apirequestlogging.util.TimestampUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h2>ApiRequestLoggingTest</h2>
 *
 * <p>Unit tests for the core utility methods of the
 * <strong>api-request-logging-spring-boot-starter</strong>.</p>
 *
 * <p>These tests run without starting a Spring context (no {@code @SpringBootTest})
 * so they execute in milliseconds and can be run offline.</p>
 *
 * @author Yash
 * @since 1.0.0
 */
class ApiRequestLoggingTest {

    // ═════════════════════════════════════════════════════════════════════
    //  ApiLoggingFilter.formatElapsed — millisecond input
    // ═════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("formatElapsed: sub-second (312 ms)")
    void formatElapsed_subSecond() {
        assertEquals("0h 0m 0s 312ms", ApiLoggingFilter.formatElapsed(312L));
    }

    @Test
    @DisplayName("formatElapsed: exactly 1 second (1000 ms)")
    void formatElapsed_oneSecond() {
        assertEquals("0h 0m 1s 0ms", ApiLoggingFilter.formatElapsed(1_000L));
    }

    @Test
    @DisplayName("formatElapsed: 1 s 500 ms")
    void formatElapsed_oneSecondFiveHundredMs() {
        assertEquals("0h 0m 1s 500ms", ApiLoggingFilter.formatElapsed(1_500L));
    }

    @Test
    @DisplayName("formatElapsed: 1 min 5 s 312 ms")
    void formatElapsed_minuteRange() {
        assertEquals("0h 1m 5s 312ms", ApiLoggingFilter.formatElapsed(65_312L));
    }

    @Test
    @DisplayName("formatElapsed: 1 h 2 min 3 s 500 ms")
    void formatElapsed_hourRange() {
        assertEquals("1h 2m 3s 500ms", ApiLoggingFilter.formatElapsed(3_723_500L));
    }

    @Test
    @DisplayName("formatElapsed: zero")
    void formatElapsed_zero() {
        assertEquals("0h 0m 0s 0ms", ApiLoggingFilter.formatElapsed(0L));
    }

    @Test
    @DisplayName("formatElapsed: exactly one hour (3 600 000 ms)")
    void formatElapsed_exactOneHour() {
        assertEquals("1h 0m 0s 0ms", ApiLoggingFilter.formatElapsed(3_600_000L));
    }

    // ═════════════════════════════════════════════════════════════════════
    //  TimestampUtils
    // ═════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getCurrentTimestamp: matches pattern d/M/yyyy, h:mm:ss am/pm")
    void getCurrentTimestamp_matchesPattern() {
        String ts = TimestampUtils.getCurrentTimestamp();
        assertNotNull(ts, "Timestamp must not be null");
        // Pattern: one-or-two-digit day / one-or-two-digit month / 4-digit year, h:mm:ss am|pm
        assertTrue(ts.matches("\\d{1,2}/\\d{1,2}/\\d{4}, \\d{1,2}:\\d{2}:\\d{2} [ap]m"),
                "Timestamp '" + ts + "' does not match expected pattern");
    }

    @Test
    @DisplayName("getCurrentTimestamp: is lowercase (no AM/PM)")
    void getCurrentTimestamp_isLowercase() {
        String ts = TimestampUtils.getCurrentTimestamp();
        assertEquals(ts, ts.toLowerCase(),
                "Timestamp must be all lowercase — got: " + ts);
    }

    @Test
    @DisplayName("getCurrentTimestampByZone: UTC matches pattern")
    void getCurrentTimestampByZone_utc() {
        String ts = TimestampUtils.getCurrentTimestampByZone("UTC");
        assertNotNull(ts);
        assertTrue(ts.matches("\\d{1,2}/\\d{1,2}/\\d{4}, \\d{1,2}:\\d{2}:\\d{2} [ap]m"),
                "UTC timestamp '" + ts + "' does not match expected pattern");
    }

    @Test
    @DisplayName("getCurrentTimestampByZone: Asia/Kolkata (IST) matches pattern")
    void getCurrentTimestampByZone_ist() {
        String ts = TimestampUtils.getCurrentTimestampByZone("Asia/Kolkata");
        assertNotNull(ts);
        assertTrue(ts.matches("\\d{1,2}/\\d{1,2}/\\d{4}, \\d{1,2}:\\d{2}:\\d{2} [ap]m"),
                "IST timestamp '" + ts + "' does not match expected pattern");
    }

    @Test
    @DisplayName("getCurrentTimestampByZone: invalid zone throws ZoneRulesException")
    void getCurrentTimestampByZone_invalidZone_throws() {
        assertThrows(java.time.zone.ZoneRulesException.class,
                () -> TimestampUtils.getCurrentTimestampByZone("Invalid/Zone"),
                "Expected ZoneRulesException for invalid zone ID");
    }

    // ═════════════════════════════════════════════════════════════════════
    //  ApiRequestLoggingProperties — defaults
    // ═════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Properties: default enabled=false (starter is off by default)")
    void properties_defaultDisabled() {
        ApiRequestLoggingProperties props = new ApiRequestLoggingProperties();
        assertFalse(props.isEnabled(),
                "Starter must be disabled by default to avoid unexpected logging");
    }

    @Test
    @DisplayName("Properties: default request-id-headers contains X-Request-ID")
    void properties_defaultRequestIdHeader() {
        ApiRequestLoggingProperties props = new ApiRequestLoggingProperties();
        assertTrue(props.getRequestIdHeaders().contains("X-Request-ID"),
                "Default header list must include X-Request-ID");
    }

    @Test
    @DisplayName("Properties: default exclude-paths contains /actuator")
    void properties_defaultExcludesActuator() {
        ApiRequestLoggingProperties props = new ApiRequestLoggingProperties();
        assertTrue(props.getExcludePaths().contains("/actuator"),
                "Default exclude-paths must include /actuator");
    }

    @Test
    @DisplayName("Properties: default max-body-length is 4096")
    void properties_defaultMaxBodyLength() {
        ApiRequestLoggingProperties props = new ApiRequestLoggingProperties();
        assertEquals(4096, props.getMaxBodyLength());
    }

    @Test
    @DisplayName("Properties: default log-request-body is true")
    void properties_defaultLogRequestBody() {
        ApiRequestLoggingProperties props = new ApiRequestLoggingProperties();
        assertTrue(props.isLogRequestBody());
    }

    @Test
    @DisplayName("Properties: setters update values")
    void properties_settersWork() {
        ApiRequestLoggingProperties props = new ApiRequestLoggingProperties();
        props.setEnabled(true);
        props.setMaxBodyLength(1024);
        props.setRequestIdHeaders(Arrays.asList("X-Correlation-ID", "traceparent"));

        assertTrue(props.isEnabled());
        assertEquals(1024, props.getMaxBodyLength());
        assertEquals("X-Correlation-ID", props.getRequestIdHeaders().get(0));
    }
}
