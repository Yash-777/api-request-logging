package com.github.yash777.apirequestlogging.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ApiRequestLoggingProperties} and its nested sections.
 *
 * <p>No Spring context required — tests verify default values and
 * setter/getter behavior directly on the POJO.</p>
 */
public class ApiRequestLoggingPropertiesTest {

    private ApiRequestLoggingProperties props;

    @BeforeEach
    void setUp() {
        props = new ApiRequestLoggingProperties();
    }

    // ── Root defaults ─────────────────────────────────────────────────

    @Test
    @DisplayName("enabled defaults to false")
    void enabledDefaultFalse() {
        assertFalse(props.isEnabled());
    }

    @Test
    @DisplayName("request-id-headers defaults to [X-Request-ID]")
    void requestIdHeadersDefault() {
        assertEquals(Arrays.asList("X-Request-ID"), props.getRequestIdHeaders());
    }

    @Test
    @DisplayName("logRequestBody defaults to true")
    void logRequestBodyDefault() {
        assertTrue(props.isLogRequestBody());
    }

    @Test
    @DisplayName("logResponseBody defaults to true")
    void logResponseBodyDefault() {
        assertTrue(props.isLogResponseBody());
    }

    @Test
    @DisplayName("logHeaders defaults to true")
    void logHeadersDefault() {
        assertTrue(props.isLogHeaders());
    }

    @Test
    @DisplayName("maxBodyLength defaults to 4096")
    void maxBodyLengthDefault() {
        assertEquals(4096, props.getMaxBodyLength());
    }

    @Test
    @DisplayName("excludePaths contains /actuator and /swagger-ui by default")
    void excludePathsDefault() {
        List<String> paths = props.getExcludePaths();
        assertTrue(paths.contains("/actuator"));
        assertTrue(paths.contains("/swagger-ui"));
    }

    @Test
    @DisplayName("excludeExtensions contains .js and .css by default")
    void excludeExtensionsDefault() {
        List<String> exts = props.getExcludeExtensions();
        assertTrue(exts.contains(".js"));
        assertTrue(exts.contains(".css"));
    }

    // ── Setters ───────────────────────────────────────────────────────

    @Test
    @DisplayName("setEnabled(true) is reflected by isEnabled()")
    void setEnabled() {
        props.setEnabled(true);
        assertTrue(props.isEnabled());
    }

    @Test
    @DisplayName("setMaxBodyLength(-1) allows unlimited capture")
    void setMaxBodyLengthUnlimited() {
        props.setMaxBodyLength(-1);
        assertEquals(-1, props.getMaxBodyLength());
    }

    @Test
    @DisplayName("setRequestIdHeaders replaces the default list")
    void setRequestIdHeaders() {
        props.setRequestIdHeaders(Arrays.asList("X-Correlation-ID", "traceparent"));
        assertEquals(2, props.getRequestIdHeaders().size());
        assertTrue(props.getRequestIdHeaders().contains("X-Correlation-ID"));
    }

    // ── Nested: AopProperties ─────────────────────────────────────────

    @Nested
    @DisplayName("AopProperties")
    class AopPropertiesTest {

        @Test
        @DisplayName("controllerHandlerEnabled defaults to true")
        void defaultTrue() {
            assertTrue(props.getAop().isControllerHandlerEnabled());
        }

        @Test
        @DisplayName("setControllerHandlerEnabled(false) disables it")
        void disable() {
            props.getAop().setControllerHandlerEnabled(false);
            assertFalse(props.getAop().isControllerHandlerEnabled());
        }
    }

    // ── Nested: RestTemplateProperties ───────────────────────────────

    @Nested
    @DisplayName("RestTemplateProperties")
    class RestTemplatePropertiesTest {

        @Test
        @DisplayName("autoCaptureEnabled defaults to false")
        void defaultFalse() {
            assertFalse(props.getRestTemplate().isAutoCaptureEnabled());
        }

        @Test
        @DisplayName("logRequestBody defaults to true")
        void logRequestBodyDefault() {
            assertTrue(props.getRestTemplate().isLogRequestBody());
        }

        @Test
        @DisplayName("maxBodyLength defaults to 4096")
        void maxBodyLengthDefault() {
            assertEquals(4096, props.getRestTemplate().getMaxBodyLength());
        }

        @Test
        @DisplayName("setAutoCaptureEnabled(true) enables auto-capture")
        void enable() {
            props.getRestTemplate().setAutoCaptureEnabled(true);
            assertTrue(props.getRestTemplate().isAutoCaptureEnabled());
        }
    }

    // ── Nested: LoggerProperties ──────────────────────────────────────

    @Nested
    @DisplayName("LoggerProperties")
    class LoggerPropertiesTest {

        @Test
        @DisplayName("logger.enabled defaults to true")
        void defaultEnabled() {
            assertTrue(props.getLogger().isEnabled());
        }

        @Test
        @DisplayName("logger.name defaults to api.request.logging")
        void defaultName() {
            assertEquals("api.request.logging", props.getLogger().getName());
        }

        @Test
        @DisplayName("sysoutEnabled defaults to false")
        void sysoutDefaultFalse() {
            assertFalse(props.getLogger().isSysoutEnabled());
        }

        @Test
        @DisplayName("setSysoutEnabled(true) enables System.out fallback")
        void enableSysout() {
            props.getLogger().setSysoutEnabled(true);
            assertTrue(props.getLogger().isSysoutEnabled());
        }
    }

    // ── Nested: ExceptionProperties ───────────────────────────────────

    @Nested
    @DisplayName("ExceptionProperties")
    class ExceptionPropertiesTest {

        @Test
        @DisplayName("maxLines defaults to 5")
        void defaultMaxLines() {
            assertEquals(5, props.getException().getMaxLines());
        }

        @Test
        @DisplayName("setMaxLines(10) is reflected")
        void setMaxLines() {
            props.getException().setMaxLines(10);
            assertEquals(10, props.getException().getMaxLines());
        }
    }

    // ── Nested: MultipartProperties ───────────────────────────────────

    @Nested
    @DisplayName("MultipartProperties")
    class MultipartPropertiesTest {

        @Test
        @DisplayName("skipBinary defaults to true")
        void defaultSkipBinary() {
            assertTrue(props.getMultipart().isSkipBinary());
        }

        @Test
        @DisplayName("skipContentTypes includes image/ and multipart/ by default")
        void defaultSkipTypes() {
            List<String> types = props.getMultipart().getSkipContentTypes();
            assertTrue(types.contains("image/"));
            assertTrue(types.contains("multipart/"));
            assertTrue(types.contains("video/"));
            assertTrue(types.contains("audio/"));
        }

        @Test
        @DisplayName("captureOnlyContentTypes is empty by default (whitelist inactive)")
        void defaultCaptureOnlyEmpty() {
            assertTrue(props.getMultipart().getCaptureOnlyContentTypes().isEmpty());
        }
    }

    // ── Nested: MaskProperties ────────────────────────────────────────

    @Nested
    @DisplayName("MaskProperties")
    class MaskPropertiesTest {

        @Test
        @DisplayName("mask.enabled defaults to false")
        void defaultDisabled() {
            assertFalse(props.getMask().isEnabled());
        }

        @Test
        @DisplayName("mask.fields contains common sensitive names by default")
        void defaultFields() {
            List<String> fields = props.getMask().getFields();
            assertTrue(fields.contains("password"));
            assertTrue(fields.contains("token"));
            assertTrue(fields.contains("secret"));
            assertTrue(fields.contains("authorization"));
        }

        @Test
        @DisplayName("mask.replacement defaults to ***MASKED***")
        void defaultReplacement() {
            assertEquals("***MASKED***", props.getMask().getReplacement());
        }

        @Test
        @DisplayName("setEnabled(true) activates masking")
        void enable() {
            props.getMask().setEnabled(true);
            assertTrue(props.getMask().isEnabled());
        }
    }
}
