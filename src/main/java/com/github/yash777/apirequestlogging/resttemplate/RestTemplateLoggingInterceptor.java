package com.github.yash777.apirequestlogging.resttemplate;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.collector.RequestLogCollectorApi;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * <h2>RestTemplateLoggingInterceptor</h2>
 *
 * <p>{@link ClientHttpRequestInterceptor} that automatically logs every outgoing
 * {@link org.springframework.web.client.RestTemplate} request and response into
 * {@link RequestLogCollector}, producing a timestamped log section for each call.</p>
 *
 * <h3>Auto-registration</h3>
 * <p>When {@code api.request.logging.rest-template.auto-capture-enabled=true},
 * {@link RestTemplateLoggingBeanPostProcessor} injects this interceptor into every
 * Spring-managed {@code RestTemplate} bean at startup.  No manual configuration
 * is needed in the calling code.</p>
 *
 * <h3>Sample log output</h3>
 * <pre>
 * ── https://payment-service/charge [14:32:05.042]
 *    request:   {"orderId":"ORD-1","amount":500.0}
 *    response:  {"txnId":"TXN-99","status":"SUCCESS"}
 * </pre>
 *
 * <h3>URL skip list</h3>
 * <p>Some endpoints (e.g. OAuth2 token URLs) should never be logged because
 * their request or response bodies contain credentials.  Configure the skip list
 * with one of two matching modes:</p>
 *
 * <h4>Mode 1 — ends-with matching (recommended for path-only patterns)</h4>
 * <pre>
 * # application.properties
 * api.request.logging.rest-template.skip.urls=/platform/oauth/oauth2/token,/internal/health
 * api.request.logging.rest-template.skip.urls-match-endswith=true
 * </pre>
 * <p>The interceptor checks whether the full request URL <em>ends with</em> any
 * entry in the list.  This is URL-host-agnostic: the same pattern matches
 * {@code http://localhost:8080/platform/oauth/oauth2/token} and
 * {@code https://prod.example.com/platform/oauth/oauth2/token}.</p>
 *
 * <h4>Mode 2 — full URL matching</h4>
 * <pre>
 * api.request.logging.rest-template.skip.urls=http://localhost:8080/platform/oauth/oauth2/token
 * api.request.logging.rest-template.skip.urls-match-full=true
 * </pre>
 * <p>The interceptor checks whether the full request URL <em>equals</em> any
 * entry in the list (case-sensitive, query-string inclusive).</p>
 *
 * <h4>Both modes together</h4>
 * <p>Both flags can be {@code true} simultaneously — the URL is skipped if
 * <em>either</em> condition matches.</p>
 *
 * <h3>Limitation</h3>
 * <p>Only Spring-managed {@code RestTemplate} beans are intercepted.
 * Instances created with {@code new RestTemplate()} inside a method body are
 * invisible to Spring and will not be captured automatically.</p>
 *
 * @author Yash
 * @since 1.1.0
 * @see RestTemplateLoggingBeanPostProcessor
 * @see ApiRequestLoggingProperties.RestTemplateProperties
 */
public class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(RestTemplateLoggingInterceptor.class);

    private final RequestLogCollectorApi      collector;
    private final ApiRequestLoggingProperties properties;

    /**
     * @param collector  the request-scoped log accumulator (CGLIB proxy — safe in singleton)
     * @param properties externalized starter configuration
     */
    public RestTemplateLoggingInterceptor(RequestLogCollectorApi      collector,
                                          ApiRequestLoggingProperties properties) {
        this.collector  = collector;
        this.properties = properties;
    }

    // ══════════════════════════════════════════════════════════════════
    //  INTERCEPT
    // ══════════════════════════════════════════════════════════════════

    /**
     * Intercepts every outgoing {@code RestTemplate} HTTP call.
     *
     * <h4>Execution steps</h4>
     * <ol>
     *   <li>Check whether the request URL matches the configured skip list.
     *       If it does, delegate immediately without logging anything.</li>
     *   <li>Build a timestamped outer-map key:
     *       {@code "https://host/path [HH:mm:ss.SSS]"}.  Using
     *       {@link RequestLogCollector#buildRetryKey(String)} means that
     *       each retry gets its own entry rather than overwriting the
     *       previous attempt.</li>
     *   <li>Log the outgoing request body (before the call).</li>
     *   <li>Execute the real HTTP call via {@code execution.execute()}.</li>
     *   <li>Buffer the response body with {@link StreamUtils#copyToByteArray}
     *       so that both this interceptor and RestTemplate's message converters
     *       can read the body (the original stream is a one-shot InputStream).</li>
     *   <li>Log the response body (after the call).</li>
     *   <li>Return a {@link BufferedClientHttpResponse} that serves the
     *       buffered bytes to RestTemplate.</li>
     * </ol>
     *
     * @param request   the outgoing HTTP request (method, URI, headers)
     * @param body      serialised request body bytes; may be empty for GET/DELETE
     * @param execution the next element in the interceptor chain (ultimately the HTTP call)
     * @return a {@link ClientHttpResponse} whose body is backed by the buffered bytes
     * @throws IOException if the network call fails
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution)
            throws IOException {

        ApiRequestLoggingProperties.RestTemplateProperties rtProps =
                properties.getRestTemplate();

        // ── Step 1: skip-list check ───────────────────────────────────
        // Skipped URLs are passed through without any logging so that
        // credential-bearing endpoints (e.g. OAuth2 token URLs) never
        // appear in the log output.
        if (shouldSkip(request.getURI(), rtProps)) {
            log.debug("[RestTemplateLoggingInterceptor] Skipping logging for URL: {}", request.getURI());
            // Execute the real HTTP call — skip only the logging, never the call itself.
            ClientHttpResponse response = execution.execute(request, body);

            // Record a single "skipped" entry so the log block shows the URL was
            // reached but intentionally not logged (e.g. OAuth2 token endpoint).
            // Key is timestamped so retries still produce separate entries.
            String key = collector.buildRetryKey(request.getURI().toString());
            collector.addLog(key, RequestLogCollector.LOG_SKIPPED,
                    "request/response logging skipped — URL matched skip list");

            return response;
        }

        // ── Step 2: build timestamped key ─────────────────────────────
        // "https://payment-service/charge [14:32:05.042]"
        // Each call to buildRetryKey stamps the current time, so retries
        // produce separate log entries instead of overwriting each other.
        String key = collector.buildRetryKey(request.getURI().toString());

        // ── Step 3: log request body ──────────────────────────────────
        if (rtProps.isLogRequestBody() && body != null && body.length > 0) {
            String reqBody = new String(body, StandardCharsets.UTF_8);
            collector.addLog(key, RequestLogCollector.LOG_REQUEST, truncate(reqBody, rtProps));
        } else {
            collector.addLog(key, RequestLogCollector.LOG_REQUEST, "(no body)");
        }

        // ── Step 4: execute the real HTTP call ────────────────────────
        ClientHttpResponse response = execution.execute(request, body);

        // ── Step 5: buffer the response body ──────────────────────────
        // RestTemplate's InputStream is one-shot. Buffering here lets both
        // this interceptor and the message converter downstream read the body.
        byte[] responseBytes = StreamUtils.copyToByteArray(response.getBody());

        // ── Step 6: log response body ─────────────────────────────────
        if (rtProps.isLogResponseBody()) {
            String resBody = responseBytes.length > 0
                    ? new String(responseBytes, StandardCharsets.UTF_8)
                    : "(empty)";
            collector.addLog(key, RequestLogCollector.LOG_RESPONSE,
                             truncate(resBody, rtProps));
        } else {
            collector.addLog(key, RequestLogCollector.LOG_RESPONSE,
                             "(response body logging disabled)");
        }

        // ── Step 7: return buffered response to RestTemplate ──────────
        return new BufferedClientHttpResponse(response, responseBytes);
    }

    // ══════════════════════════════════════════════════════════════════
    //  URL SKIP LOGIC
    // ══════════════════════════════════════════════════════════════════

    /**
     * Determines whether the given URI should be excluded from logging.
     *
     * <p>Two independent matching modes are supported and can be combined:</p>
     *
     * <table border="1" cellpadding="4">
     *   <tr><th>Property</th><th>Match type</th><th>Example pattern</th></tr>
     *   <tr>
     *     <td>{@code skip.urls-match-endswith=true}</td>
     *     <td>URL ends-with the pattern string</td>
     *     <td>{@code /platform/oauth/oauth2/token}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code skip.urls-match-full=true}</td>
     *     <td>Full URL equals the pattern string</td>
     *     <td>{@code http://localhost:8080/platform/oauth/oauth2/token}</td>
     *   </tr>
     * </table>
     *
     * <p>If both flags are {@code true}, the URL is skipped when
     * <em>either</em> condition matches.  If neither flag is {@code true},
     * the skip list has no effect and every URL is logged.</p>
     *
     * @param uri     the URI of the outgoing request
     * @param rtProps the RestTemplate-specific property section
     * @return {@code true} if logging should be skipped for this URL
     */
    private boolean shouldSkip(URI uri,
            ApiRequestLoggingProperties.RestTemplateProperties rtProps) {

        ApiRequestLoggingProperties.RestTemplateProperties.SkipProperties skip =
                rtProps.getSkip();

        List<String> skipUrls = skip.getUrls();
        if (skipUrls == null || skipUrls.isEmpty()) {
            return false; // nothing configured — log everything
        }

        String fullUrl = uri.toString();

        for (String pattern : skipUrls) {
            if (pattern == null || pattern.trim().isEmpty()) continue;
            String trimmed = pattern.trim();

            // ends-with matching: pattern is a path suffix, host-agnostic
            if (skip.isUrlsMatchEndswith() && fullUrl.endsWith(trimmed)) {
                return true;
            }

            // full URL matching: exact equality
            if (skip.isUrlsMatchFull() && fullUrl.equals(trimmed)) {
                return true;
            }
        }

        return false;
    }

    // ══════════════════════════════════════════════════════════════════
    //  HELPER
    // ══════════════════════════════════════════════════════════════════

    /**
     * Truncates {@code body} to {@link ApiRequestLoggingProperties.RestTemplateProperties#getMaxBodyLength()}
     * characters, appending {@code [TRUNCATED at N chars]} when cut.
     * Returns the original string unchanged when {@code maxBodyLength} is
     * {@code -1} (unlimited) or the body is shorter than the limit.
     *
     * @param body    the body string to truncate
     * @param rtProps the RestTemplate property section (provides the limit)
     * @return the body, possibly truncated
     */
    private static String truncate(String body,
            ApiRequestLoggingProperties.RestTemplateProperties rtProps) {
        int max = rtProps.getMaxBodyLength();
        if (max < 0 || body == null || body.length() <= max) return body;
        return body.substring(0, max) + " [TRUNCATED at " + max + " chars]";
    }
}
