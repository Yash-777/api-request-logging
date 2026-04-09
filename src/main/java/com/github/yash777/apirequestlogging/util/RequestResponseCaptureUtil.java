package com.github.yash777.apirequestlogging.util;

import java.nio.charset.StandardCharsets;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.filter.ApiLoggingFilter;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;

/**
 * <h2>RequestResponseCaptureUtil</h2>
 *
 * <p>Stateless utility that encapsulates all request-body, response-body,
 * redirect-path, and body-type capture logic for
 * {@link ApiLoggingFilter}.</p>
 *
 * <p>Extracted from {@code ApiLoggingFilter} so the filter class stays
 * focused on orchestration while this class owns every byte-level capture
 * strategy.  All methods are {@code static} — no instantiation required.</p>
 *
 * <h3>Design rules</h3>
 * <ul>
 *   <li><strong>Never throw</strong> — every method catches all exceptions
 *       internally and falls back to a descriptive label.  A logging failure
 *       must never break the application request.</li>
 *   <li><strong>No side-effects beyond logging</strong> — methods read streams
 *       and write to {@link RequestLogCollector}; they never mutate the request
 *       or response.</li>
 *   <li><strong>Strategy order</strong> — each capture method tries strategies
 *       from cheapest to most expensive, returning as soon as one succeeds.</li>
 * </ul>
 *
 * @author Yash
 * @since 1.1.0
 * @see ApiLoggingFilter
 */
public final class RequestResponseCaptureUtil {

    private RequestResponseCaptureUtil() {}

    // ══════════════════════════════════════════════════════════════════
    //  BODY TYPE
    // ══════════════════════════════════════════════════════════════════

    /**
     * Logs the semantic body-type category of the request under the key
     * {@code "requestBodyType"} in the {@link RequestLogCollector#INCOMING_KEY} block.
     *
     * <h3>Categories</h3>
     * <table border="1" cellpadding="4">
     *   <tr><th>Value</th><th>Content-Type examples</th></tr>
     *   <tr><td>{@code "raw"}</td>
     *       <td>{@code application/json}, {@code text/*}, {@code application/xml},
     *           {@code application/graphql}, absent</td></tr>
     *   <tr><td>{@code "form-data"}</td>
     *       <td>{@code application/x-www-form-urlencoded}, {@code multipart/form-data}</td></tr>
     *   <tr><td>{@code "binary"}</td>
     *       <td>{@code application/octet-stream}, {@code application/pdf},
     *           {@code image/*}, {@code audio/*}, {@code video/*}</td></tr>
     * </table>
     *
     * <h3>Log output</h3>
     * <pre>
     * ── INCOMING
     *    requestBodyType: raw          ← POST application/json
     *    requestBodyType: form-data    ← multipart file upload
     *    requestBodyType: binary       ← image/png or application/pdf
     * </pre>
     *
     * @param request   the current HTTP request; must not be {@code null}
     * @param collector the request-scoped log accumulator
     */
    public static void logRequestBodyType(HttpServletRequest request,
                                          RequestLogCollector collector) {
        String bodyType = detectBodyType(request.getContentType());
        collector.addLog(RequestLogCollector.INCOMING_KEY, "requestBodyType", bodyType);
    }

    /**
     * Pure mapping function — derives a body-type label from a raw
     * {@code Content-Type} header value.
     *
     * <p>Extracted as a {@code package-private} static method so it can be
     * unit-tested directly without a servlet container or Spring context.</p>
     *
     * @param contentType the raw {@code Content-Type} header value,
     *                    e.g. {@code "application/json; charset=UTF-8"};
     *                    may be {@code null}
     * @return {@code "binary"}, {@code "form-data"}, or {@code "raw"} (default)
     */
    static String detectBodyType(String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return "raw";
        }
        String ct = contentType.toLowerCase();

        // Binary: application/octet-stream, application/pdf, image/*, audio/*, video/*
        if (ct.startsWith("image/")
                || ct.startsWith("audio/")
                || ct.startsWith("video/")
                || ct.startsWith("application/octet-stream")
                || ct.startsWith("application/pdf")) {
            return "binary";
        }

        // Form-data: multipart/form-data, application/x-www-form-urlencoded
        if (ct.startsWith("multipart/form-data")
                || ct.startsWith("application/x-www-form-urlencoded")) {
            return "form-data";
        }

        // Raw (default): application/json, text/*, application/xml, etc.
        return "raw";
    }

    // ══════════════════════════════════════════════════════════════════
    //  REQUEST BODY CAPTURE
    // ══════════════════════════════════════════════════════════════════

    /**
     * Captures the request body using three strategies in order of preference,
     * and writes the result under {@code "requestBody"} in the
     * {@link RequestLogCollector#INCOMING_KEY} block.
     *
     * <p>Never throws — all exceptions are caught and a descriptive label is
     * logged instead so the application request is never affected.</p>
     *
     * <h4>Strategy 1 — {@link ContentCachingRequestWrapper} cache (normal path)</h4>
     * <p>When the filter chain ran fully, Jackson / the controller read the
     * {@code InputStream} and the wrapper cached every byte internally.
     * {@code getContentAsByteArray()} returns the full body immediately.</p>
     *
     * <h4>Strategy 2 — forced stream read (early-return / short-circuit path)</h4>
     * <p>When a consumer filter called {@code sendError()} and returned without
     * invoking the rest of the chain, the {@code InputStream} was never consumed
     * so the wrapper cache is empty.  We force-read the stream through the wrapper
     * to populate its internal buffer, then call {@code getContentAsByteArray()}.
     * This is safe because no downstream code will try to read the stream again —
     * the chain did not proceed.</p>
     *
     * <h4>Strategy 3 — descriptive label (binary / form-data)</h4>
     * <p>When both byte-level strategies yield nothing — either the body is
     * genuinely absent (GET, DELETE) or the content type is binary/multipart
     * and buffering was skipped — a human-readable label is logged instead of
     * silence, making it clear the body was intentionally not captured.</p>
     *
     * @param request       the current HTTP request (may or may not be wrapped)
     * @param chainInvoked  {@code true} when {@code chain.doFilter()} was called;
     *                      {@code false} when a consumer filter short-circuited
     * @param responseStatus HTTP status at the time of capture
     * @param collector     the request-scoped log accumulator
     * @param properties    externalized configuration (drives truncation limit)
     */
    public static void captureRequestBody(HttpServletRequest      request,
                                          boolean                 chainInvoked,
                                          int                     responseStatus,
                                          RequestLogCollector     collector,
                                          ApiRequestLoggingProperties properties) {
        try {
            String bodyType = detectBodyType(request.getContentType());

            // ── Strategy 1: ContentCachingRequestWrapper cache ────────────
            if (request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrapped =
                        (ContentCachingRequestWrapper) request;
                byte[] cached = wrapped.getContentAsByteArray();

                if (cached.length > 0) {
                    collector.addLog(RequestLogCollector.INCOMING_KEY,
                            "requestBody",
                            truncate(new String(cached, StandardCharsets.UTF_8), properties));
                    return;
                }

                // ── Strategy 2: force-read stream (short-circuit / error path) ──
                // Only for text-based types — never force-read binary/multipart.
                if (!chainInvoked || responseStatus >= 400) {
                    if (!bodyType.equals("binary") && !bodyType.equals("form-data")) {
                        String forced = readBodyForcefully(wrapped);
                        if (!forced.isEmpty()) {
                            collector.addLog(RequestLogCollector.INCOMING_KEY,
                                    "requestBody",
                                    truncate(forced, properties));
                            return;
                        }
                    }
                }
            }

            // ── Strategy 3: descriptive label for binary / form-data ──────
            if (bodyType.equals("binary") || bodyType.equals("form-data")) {
                collector.addLog(RequestLogCollector.INCOMING_KEY,
                        "requestBody",
                        "[" + bodyType + " content — body not captured]");
            }
            // Raw / text with genuinely empty body (GET, DELETE) → no entry logged.

        } catch (Exception e) {
            // Never propagate — log a safe marker instead
            collector.addLog(RequestLogCollector.INCOMING_KEY,
                    "requestBody",
                    "[capture error: " + e.getClass().getSimpleName() + "]");
        }
    }

    /**
     * Forces the {@link ContentCachingRequestWrapper} to read and cache the
     * remaining bytes in the underlying {@code InputStream}.
     *
     * <p>Under normal operation the controller / Jackson reads the stream first,
     * which triggers the wrapper's internal caching.  When a filter short-circuits
     * the chain before the controller runs, the stream is still unread.  Calling
     * {@code getInputStream().read(...)} here triggers the wrapper's byte-copying
     * logic, after which {@code getContentAsByteArray()} returns the full body.</p>
     *
     * @param request a non-null {@link ContentCachingRequestWrapper}
     * @return the body as a UTF-8 string, or an empty string on any error
     */
    private static String readBodyForcefully(ContentCachingRequestWrapper request) {
        try {
            ServletInputStream is = request.getInputStream();
            // Read the stream in chunks to trigger the wrapper's internal caching.
            byte[] buf = new byte[1024];
            //noinspection StatementWithEmptyBody
            while (is.read(buf) != -1) { /* drain */ }
            byte[] cached = request.getContentAsByteArray();
            return cached.length > 0
                    ? new String(cached, StandardCharsets.UTF_8)
                    : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  RESPONSE BODY CAPTURE
    // ══════════════════════════════════════════════════════════════════

    /**
     * Captures the response body or error message and writes it to the
     * {@link RequestLogCollector#INCOMING_KEY} block.
     *
     * <p>Uses {@link RequestLogCollector#LOG_RESPONSE} for 2xx/3xx responses
     * and {@link RequestLogCollector#LOG_RESPONSE_ERROR} for 4xx/5xx or
     * short-circuited responses.</p>
     *
     * <p>Never throws — all exceptions are caught internally.</p>
     *
     * <h4>Error capture attempts (when {@code !chainInvoked || status >= 400})</h4>
     * <ol>
     *   <li><strong>Body buffer</strong> — bytes written by {@code @ExceptionHandler}
     *       or an error controller (e.g. a JSON error document).</li>
     *   <li><strong>Custom request attribute</strong> — consumer filters may set
     *       {@code request.setAttribute("apilog.errorMessage", msg)} for reliable
     *       cross-container error capture without depending on Tomcat internals.</li>
     *   <li><strong>{@code javax.servlet.error.message}</strong> — set by Tomcat
     *       during the error-dispatch cycle.  Usually {@code null} in the original
     *       request dispatch, but checked as a best-effort fallback.</li>
     *   <li><strong>HTTP reason phrase</strong> — last resort when no message is
     *       available anywhere (e.g. bare {@code response.sendError(401)}).</li>
     * </ol>
     *
     * @param cr            the response wrapper; must not be {@code null}
     * @param request       the current HTTP request (used for attribute lookups)
     * @param status        HTTP status code at time of capture
     * @param chainInvoked  {@code true} when {@code chain.doFilter()} was called
     * @param collector     the request-scoped log accumulator
     * @param properties    externalized configuration (drives truncation limit)
     */
    public static void captureResponseBody(ContentCachingResponseWrapper cr,
                                           HttpServletRequest            request,
                                           int                           status,
                                           boolean                       chainInvoked,
                                           RequestLogCollector           collector,
                                           ApiRequestLoggingProperties   properties) {
        try {
            byte[] resBytes = cr.getContentAsByteArray();

            if (!chainInvoked || status >= 400) {

                // Attempt 1: body buffer (from @ExceptionHandler / error controller)
                if (resBytes.length > 0) {
                    collector.addLog(RequestLogCollector.INCOMING_KEY,
                            RequestLogCollector.LOG_RESPONSE_ERROR,
                            truncate(new String(resBytes, StandardCharsets.UTF_8), properties));
                    return;
                }

                // Attempt 2: custom attribute — most reliable cross-container approach.
                // Consumer filter sets: request.setAttribute("apilog.errorMessage", msg)
                Object customMsg = request.getAttribute("apilog.errorMessage");
                if (customMsg != null && !customMsg.toString().isEmpty()) {
                    collector.addLog(RequestLogCollector.INCOMING_KEY,
                            RequestLogCollector.LOG_RESPONSE_ERROR,
                            customMsg.toString());
                    return;
                }

                // Attempt 3: javax.servlet.error.message (Tomcat error-dispatch only)
                Object servletMsg = request.getAttribute("javax.servlet.error.message");
                if (servletMsg != null && !servletMsg.toString().isEmpty()) {
                    collector.addLog(RequestLogCollector.INCOMING_KEY,
                            RequestLogCollector.LOG_RESPONSE_ERROR,
                            servletMsg.toString());
                    return;
                }

                // Attempt 4: HTTP reason phrase — last resort
                collector.addLog(RequestLogCollector.INCOMING_KEY,
                        RequestLogCollector.LOG_RESPONSE_ERROR,
                        "HTTP " + status + " — " + getReasonPhrase(status));

            } else {
                // Normal 2xx / 3xx
                String body = resBytes.length > 0
                        ? new String(resBytes, StandardCharsets.UTF_8)
                        : "(empty)";
                collector.addLog(RequestLogCollector.INCOMING_KEY,
                        RequestLogCollector.LOG_RESPONSE,
                        truncate(body, properties));
            }

        } catch (Exception e) {
            collector.addLog(RequestLogCollector.INCOMING_KEY,
                    RequestLogCollector.LOG_RESPONSE,
                    "[capture error: " + e.getClass().getSimpleName() + "]");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  REDIRECT PATH CAPTURE
    // ══════════════════════════════════════════════════════════════════

    /**
     * Captures the redirect target URL from the {@code Location} response header
     * and writes it under {@code "redirectPath"} in the
     * {@link RequestLogCollector#INCOMING_KEY} block.
     *
     * <p>Called unconditionally in the {@code finally} block — the {@code Location}
     * header is only present on 3xx redirect responses, so for all other responses
     * this method is a no-op (the header is {@code null} and nothing is logged).</p>
     *
     * <h3>Log output (3xx responses only)</h3>
     * <pre>
     * ── INCOMING
     *    responseStatus: 302
     *    redirectPath:   /login
     * </pre>
     *
     * <p>Never throws — any exception is silently swallowed to avoid masking
     * the original request outcome.</p>
     *
     * @param response  the current HTTP response
     * @param collector the request-scoped log accumulator
     */
    public static void captureRedirectPath(HttpServletResponse response,
                                           RequestLogCollector collector) {
        try {
            String location = response.getHeader("Location");
            if (location != null && !location.isEmpty()) {
                collector.addLog(RequestLogCollector.INCOMING_KEY,
                        "redirectPath", location);
            }
        } catch (Exception e) {
            // Never propagate
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Truncates {@code body} to
     * {@link ApiRequestLoggingProperties#getMaxBodyLength()} characters
     * and appends a {@code [TRUNCATED at N chars]} suffix when cut.
     *
     * <p>Returns the original string unchanged when {@code maxBodyLength}
     * is {@code -1} (unlimited) or the body fits within the limit.</p>
     *
     * @param body       the raw body string; may be {@code null}
     * @param properties externalized configuration providing the limit
     * @return body, possibly truncated
     */
    static String truncate(String body, ApiRequestLoggingProperties properties) {
        int max = properties.getMaxBodyLength();
        if (max < 0 || body == null || body.length() <= max) return body;
        return body.substring(0, max) + " [TRUNCATED at " + max + " chars]";
    }

    /**
     * Returns a standard HTTP reason phrase for common status codes.
     * Used as a last-resort fallback in {@link #captureResponseBody} when
     * no error message is available from any source.
     *
     * @param status HTTP status code
     * @return short reason phrase, or {@code "(no message)"} for unknown codes
     */
    static String getReasonPhrase(int status) {
        switch (status) {
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 422: return "Unprocessable Entity";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default:  return "(no message)";
        }
    }
}