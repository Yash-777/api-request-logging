package com.github.yash777.apirequestlogging.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;

/**
 * Unit tests for {@link RequestBodyCachingFilter}.
 *
 * <p>Uses Spring's {@link MockHttpServletRequest} / {@link MockHttpServletResponse}
 * — no running Tomcat required.</p>
 *
 * <p><strong>Note:</strong> the multipart/binary skip feature
 * ({@code api.request.logging.multipart.*}) is introduced in v1.1.0.
 * These tests cover v1.0.1 behaviour: every request body is always wrapped
 * in {@link ContentCachingRequestWrapper} regardless of Content-Type.</p>
 */
public class RequestBodyCachingFilterTest {
    private ApiRequestLoggingProperties properties;
    private RequestBodyCachingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new ApiRequestLoggingProperties();
        filter = new RequestBodyCachingFilter();
    }

    // ── Request wrapping ──────────────────────────────────────────────

    @Test
    @DisplayName("JSON POST request is wrapped in ContentCachingRequestWrapper")
    void jsonRequestIsWrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType("application/json");
        request.setContent("{\"item\":\"Laptop\"}".getBytes());

        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(req));

        assertInstanceOf(ContentCachingRequestWrapper.class, seen.get(),
                "JSON request must be wrapped in ContentCachingRequestWrapper");
    }

    @Test
    @DisplayName("GET request with no body is wrapped")
    void getRequestIsWrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/123");

        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(req));

        assertInstanceOf(ContentCachingRequestWrapper.class, seen.get(),
                "GET request should be wrapped even with no body");
    }

    // ── No Content-Type — default to wrapping ─────────────────────────

    @Test
    @DisplayName("request with no Content-Type is wrapped")
    void noContentTypeIsWrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/data");

        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(req));

        assertInstanceOf(ContentCachingRequestWrapper.class, seen.get(),
                "Request with no Content-Type should still be wrapped");
    }

    // ── Response wrapping ─────────────────────────────────────────────

    @Test
    @DisplayName("response is always wrapped in ContentCachingResponseWrapper")
    void responseIsAlwaysWrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");

        AtomicReference<ServletResponse> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> seen.set(res));

        assertInstanceOf(ContentCachingResponseWrapper.class, seen.get(),
                "Response should always be wrapped in ContentCachingResponseWrapper");
    }

    @Test
    @DisplayName("response body written in chain is flushed back to client after filter")
    void responseBodyFlushedAfterChain() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Simulate a controller writing the response body
        FilterChain chain = (req, res) -> res.getWriter().write("{\"status\":\"ok\"}");

        filter.doFilter(request, response, chain);

        assertFalse(response.getContentAsString().isEmpty(),
                "Response body should be flushed from ContentCachingResponseWrapper to client");
    }

    // ── Double-wrap prevention ────────────────────────────────────────

    @Test
    @DisplayName("already-wrapped request is not double-wrapped")
    void noDoubleWrapRequest() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/api/orders");
        raw.setContentType("application/json");
        ContentCachingRequestWrapper alreadyWrapped = new ContentCachingRequestWrapper(raw);

        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        filter.doFilter(alreadyWrapped, new MockHttpServletResponse(),
                (req, res) -> seen.set(req));

        assertSame(alreadyWrapped, seen.get(),
                "Already-wrapped request must not be wrapped a second time");
    }

    @Test
    @DisplayName("already-wrapped response is not double-wrapped")
    void noDoubleWrapResponse() throws Exception {
        MockHttpServletRequest raw      = new MockHttpServletRequest("GET", "/api");
        MockHttpServletResponse rawRes  = new MockHttpServletResponse();
        ContentCachingResponseWrapper alreadyWrapped = new ContentCachingResponseWrapper(rawRes);

        AtomicReference<ServletResponse> seen = new AtomicReference<>();
        filter.doFilter(raw, alreadyWrapped, (req, res) -> seen.set(res));

        assertSame(alreadyWrapped, seen.get(),
                "Already-wrapped response must not be wrapped a second time");
    }

    // ── Double-wrap prevention ────────────────────────────────────────

    @Test
    @DisplayName("already-wrapped request is not double-wrapped")
    void noDoubleWrap() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/api");
        raw.setContentType("application/json");
        ContentCachingRequestWrapper alreadyWrapped = new ContentCachingRequestWrapper(raw);

        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        filter.doFilter(alreadyWrapped, new MockHttpServletResponse(),
                (req, res) -> seen.set(req));

        assertSame(alreadyWrapped, seen.get(),
                "Already-wrapped request should not be double-wrapped");
    }
    
    // ── Response always wrapped ───────────────────────────────────────

    @Test
    @DisplayName("response is always wrapped in ContentCachingResponseWrapper")
    void responseAlwaysWrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");

        AtomicReference<ServletResponse> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(res);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertInstanceOf(ContentCachingResponseWrapper.class, seen.get(),
                "Response should always be wrapped regardless of content type");
    }

    // ── skip-binary=false — always wrap ──────────────────────────────

    @Test
    @DisplayName("image/png is wrapped when skip-binary=false")
    void skipBinaryFalseAlwaysWraps() throws Exception {
        properties.getMultipart().setSkipBinary(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload");
        request.setContentType("image/png");
        request.setContent(new byte[]{1, 2, 3});

        AtomicReference<ServletRequest> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(req);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertInstanceOf(ContentCachingRequestWrapper.class, seen.get(),
                "When skip-binary=false, even image/png should be wrapped");
    }
    
}