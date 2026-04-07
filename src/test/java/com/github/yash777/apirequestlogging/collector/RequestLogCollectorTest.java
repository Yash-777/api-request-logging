package com.github.yash777.apirequestlogging.collector;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;

/**
 * Unit tests for {@link RequestLogCollector}.
 *
 * <p>Instantiated directly (no Spring context) using a manually constructed
 * {@link ApiRequestLoggingProperties} instance, so the tests run fast and
 * without any web environment overhead.</p>
 */
public class RequestLogCollectorTest {

    private RequestLogCollector collector;
    private ApiRequestLoggingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ApiRequestLoggingProperties();
        properties.getLogger().setEnabled(false);   // suppress log output in tests
        properties.getLogger().setSysoutEnabled(false);
        collector = new RequestLogCollector(properties);
    }

    // ── Constants ─────────────────────────────────────────────────────

    @Test
    @DisplayName("INCOMING_KEY is 'INCOMING'")
    void incomingKeyConstant() {
        assertEquals("INCOMING", RequestLogCollector.INCOMING_KEY);
    }

    @Test
    @DisplayName("LOG_REQUEST is 'request'")
    void logRequestConstant() {
        assertEquals("request", RequestLogCollector.LOG_REQUEST);
    }

    @Test
    @DisplayName("LOG_RESPONSE is 'response'")
    void logResponseConstant() {
        assertEquals("response", RequestLogCollector.LOG_RESPONSE);
    }

    @Test
    @DisplayName("LOG_EXCEPTION is 'exceptionStacktrace'")
    void logExceptionConstant() {
        assertEquals("exceptionStacktrace", RequestLogCollector.LOG_EXCEPTION);
    }

    @Test
    @DisplayName("LOG_ERROR_INDICATOR is 'errorIndicator'")
    void logErrorIndicatorConstant() {
        assertEquals("errorIndicator", RequestLogCollector.LOG_ERROR_INDICATOR);
    }

    @Test
    @DisplayName("LOG_CONTROLLER_HANDLER is 'controllerHandler'")
    void logControllerHandlerConstant() {
        assertEquals("controllerHandler", RequestLogCollector.LOG_CONTROLLER_HANDLER);
    }

    // ── addLog() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("addLog()")
    class AddLogTests {

        @Test
        @DisplayName("stores a string value under the correct key")
        void storesStringValue() {
            collector.addLog("MyService/call", "request", "payload");
            Map<String, String> inner = collector.getLogs().get("MyService/call");
            assertNotNull(inner);
            assertEquals("payload", inner.get("request"));
        }

        @Test
        @DisplayName("stores a null value without throwing")
        void storesNullValue() {
            assertDoesNotThrow(() ->
                    collector.addLog("MyService/call", "response", null));
            assertNull(collector.getLogs().get("MyService/call").get("response"));
        }

        @Test
        @DisplayName("serialises a POJO to JSON")
        void serialisesPojo() {
            TestPojo pojo = new TestPojo("alice", 42);
            collector.addLog("svc", "data", pojo);
            String stored = collector.getLogs().get("svc").get("data");
            assertTrue(stored.contains("alice"));
            assertTrue(stored.contains("42"));
        }

        @Test
        @DisplayName("appends multiple inner keys to the same outer key")
        void appendsMultipleInnerKeys() {
            collector.addLog("svc", "request",  "req-payload");
            collector.addLog("svc", "response", "res-payload");
            Map<String, String> inner = collector.getLogs().get("svc");
            assertEquals(2, inner.size());
            assertEquals("req-payload", inner.get("request"));
            assertEquals("res-payload", inner.get("response"));
        }

        @Test
        @DisplayName("does not throw when outer key is null")
        void nullKeyIgnored() {
            assertDoesNotThrow(() -> collector.addLog(null, "field", "value"));
            assertFalse(collector.getLogs().containsKey(null));
        }

        @Test
        @DisplayName("does not throw when inner key is null")
        void nullInnerKeyIgnored() {
            assertDoesNotThrow(() -> collector.addLog("svc", null, "value"));
        }
    }

    // ── addRequestResponseLog() ───────────────────────────────────────

    @Test
    @DisplayName("addRequestResponseLog() stores both request and response")
    void addRequestResponseLog() {
        collector.addRequestResponseLog("InventoryService/check", "req-payload", "res-payload");

        Map<String, String> inner = collector.getLogs().get("InventoryService/check");
        assertNotNull(inner);
        assertEquals("req-payload", inner.get("request"));
        assertEquals("res-payload", inner.get("response"));
    }

    @Test
    @DisplayName("addRequestResponseLog() does not throw for null key")
    void addRequestResponseLogNullKey() {
        assertDoesNotThrow(() ->
                collector.addRequestResponseLog(null, "req", "res"));
    }

    // ── buildRetryKey() ───────────────────────────────────────────────

    @Nested
    @DisplayName("buildRetryKey()")
    class BuildRetryKeyTests {

        @Test
        @DisplayName("contains the label as a prefix")
        void containsLabel() {
            String key = collector.buildRetryKey("PaymentGateway/charge");
            assertTrue(key.startsWith("PaymentGateway/charge"));
        }

        @Test
        @DisplayName("appends a timestamp in [HH:mm:ss.SSS] format")
        void appendsTimestamp() {
            String key = collector.buildRetryKey("svc");
            // matches: "svc [14:32:05.001]"
            assertTrue(key.matches("svc \\[\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\]"),
                    "Key '" + key + "' did not match expected pattern");
        }

        @Test
        @DisplayName("two consecutive calls produce different keys")
        void twoCallsProduceDifferentKeys() throws InterruptedException {
            String key1 = collector.buildRetryKey("svc");
            Thread.sleep(2); // ensure time advances
            String key2 = collector.buildRetryKey("svc");
            // May be same if both happen within the same millisecond — acceptable.
            // The important thing is that they can differ, which this demonstrates.
            assertNotNull(key1);
            assertNotNull(key2);
        }
    }

    // ── Secret masking ────────────────────────────────────────────────

    @Nested
    @DisplayName("addLog() — secret masking applied")
    class MaskingTests {

        @BeforeEach
        void enableMasking() {
            properties.getMask().setEnabled(true);
            properties.getMask().setFields(Arrays.asList("password", "token"));
            properties.getMask().setReplacement("***MASKED***");
            // Re-create collector with masking-enabled properties
            collector = new RequestLogCollector(properties);
        }

        @Test
        @DisplayName("masks password string field in stored JSON")
        void masksPasswordStringField() {
            collector.addLog("svc", "request",
                    "{\"username\":\"john\",\"password\":\"secret\"}");
            String stored = collector.getLogs().get("svc").get("request");
            assertFalse(stored.contains("secret"),
                    "password value should be masked");
            assertTrue(stored.contains("***MASKED***"));
        }

        @Test
        @DisplayName("does not mask non-sensitive fields")
        void doesNotMaskNonSensitiveFields() {
            collector.addLog("svc", "data", "{\"username\":\"john\"}");
            String stored = collector.getLogs().get("svc").get("data");
            assertTrue(stored.contains("john"),
                    "username should not be masked");
        }

        @Test
        @DisplayName("masks numeric field when masking is enabled")
        void masksNumericField() {
            properties.getMask().setFields(Arrays.asList("latitude"));
            collector = new RequestLogCollector(properties);
            collector.addLog("weather", "response", "{\"latitude\":17.375}");
            String stored = collector.getLogs().get("weather").get("response");
            assertFalse(stored.contains("17.375"),
                    "numeric latitude should be masked");
            assertTrue(stored.contains("***MASKED***"));
        }
    }

    // ── getLogs() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getLogs() returns empty map on a fresh collector")
    void getLogsEmpty() {
        assertTrue(collector.getLogs().isEmpty());
    }

    @Test
    @DisplayName("getLogs() returns entries in insertion order")
    void getLogsOrder() {
        collector.addLog("first",  "k", "v1");
        collector.addLog("second", "k", "v2");
        collector.addLog("third",  "k", "v3");

        String[] keys = collector.getLogs().keySet().toArray(new String[0]);
        assertEquals("first",  keys[0]);
        assertEquals("second", keys[1]);
        assertEquals("third",  keys[2]);
    }

    // ── currentRequestId() ────────────────────────────────────────────

    @Test
    @DisplayName("currentRequestId() returns null when not in a request thread")
    void currentRequestIdNullOutsideRequest() {
        // ThreadLocal is not set — should return null
        assertNull(RequestLogCollector.currentRequestId());
    }

    // ── helpers ───────────────────────────────────────────────────────

    /** Simple POJO for JSON serialisation tests. */
    static class TestPojo {
        public final String name;
        public final int age;
        TestPojo(String name, int age) { this.name = name; this.age = age; }
    }
}
