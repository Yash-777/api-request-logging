package com.github.yash777.apirequestlogging.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * <h2>ApiRequestLoggingProperties</h2>
 *
 * <p>Externalized configuration for the <strong>api-request-logging-spring-boot-starter</strong>.
 * All properties share the prefix {@code api.request.logging}.</p>
 *
 * <h3>Quick-start (application.properties / application.yml)</h3>
 * <pre>
 * # ── Minimal — enable the starter with all defaults ──────────────────────
 * api.request.logging.enabled=true
 *
 * # ── Full configuration ───────────────────────────────────────────────────
 * api.request.logging.enabled=true
 * api.request.logging.request-id-headers=X-Request-ID,request_id,X-Correlation-ID
 * api.request.logging.exclude-paths=/actuator,/swagger-ui,/v3/api-docs,/health
 * api.request.logging.exclude-extensions=.js,.css,.html,.png,.jpg,.ico
 * api.request.logging.log-request-body=true
 * api.request.logging.log-response-body=true
 * api.request.logging.log-headers=true
 * api.request.logging.max-body-length=4096
 * </pre>
 *
 * <h3>YAML equivalent</h3>
 * <pre>
 * api:
 *   request:
 *     logging:
 *       enabled: true
 *       request-id-headers:
 *         - X-Request-ID
 *         - request_id
 *         - X-Correlation-ID
 *       exclude-paths:
 *         - /actuator
 *         - /swagger-ui
 *       log-request-body: true
 *       log-response-body: true
 *       log-headers: true
 *       max-body-length: 4096
 * </pre>
 *
 * <h3>Property reference</h3>
 * <table border="1" cellpadding="4">
 *   <tr>
 *     <th>Property</th><th>Type</th><th>Default</th><th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>enabled</td><td>boolean</td><td>false</td>
 *     <td>Master switch — set to {@code true} to activate all logging filters.</td>
 *   </tr>
 *   <tr>
 *     <td>request-id-headers</td><td>List&lt;String&gt;</td>
 *     <td>X-Request-ID</td>
 *     <td>
 *       Ordered list of HTTP header names to check for an incoming correlation /
 *       idempotency ID.  The first non-blank value found wins.  If none match a
 *       random UUID is generated.  Common alternatives: {@code request_id},
 *       {@code X-Correlation-ID}, {@code X-B3-TraceId}.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>exclude-paths</td><td>List&lt;String&gt;</td>
 *     <td>/actuator, /swagger-ui, /v3/api-docs, /v2/api-docs,
 *         /swagger-resources, /js/, /css/, /images/</td>
 *     <td>URI prefixes that are silently skipped by both filters.</td>
 *   </tr>
 *   <tr>
 *     <td>exclude-extensions</td><td>List&lt;String&gt;</td>
 *     <td>.js, .css, .html, .scss, .png, .jpg, .gif, .ico, .wsdl</td>
 *     <td>File extensions that are silently skipped.</td>
 *   </tr>
 *   <tr>
 *     <td>log-request-body</td><td>boolean</td><td>true</td>
 *     <td>Whether to capture and log the HTTP request body.</td>
 *   </tr>
 *   <tr>
 *     <td>log-response-body</td><td>boolean</td><td>true</td>
 *     <td>Whether to capture and log the HTTP response body.</td>
 *   </tr>
 *   <tr>
 *     <td>log-headers</td><td>boolean</td><td>true</td>
 *     <td>Whether to capture and log all request headers as JSON.</td>
 *   </tr>
 *   <tr>
 *     <td>max-body-length</td><td>int</td><td>4096</td>
 *     <td>
 *       Maximum characters to store from request/response bodies.
 *       Bodies exceeding this limit are truncated with a {@code [TRUNCATED]} suffix.
 *       Set to {@code -1} for unlimited (caution: large payloads consume heap).
 *     </td>
 *   </tr>
 * </table>
 *
 * @author Yash
 * @since 1.0.0
 * @see com.github.yash777.apirequestlogging.autoconfigure.ApiRequestLoggingAutoConfiguration
 */
@ConfigurationProperties(prefix = "api.request.logging")
public class ApiRequestLoggingProperties {

    // ── Master switch ─────────────────────────────────────────────────────

    /**
     * Master switch for the logging starter.
     *
     * <p>When {@code false} (default), none of the filters or beans defined
     * by this starter are registered — zero overhead on the consumer application.</p>
     *
     * <p>Set to {@code true} in {@code application.properties} to activate:</p>
     * <pre>api.request.logging.enabled=true</pre>
     */
    private boolean enabled = false;

    // ── Correlation ID ────────────────────────────────────────────────────

    /**
     * Ordered list of HTTP header names to check for an incoming correlation /
     * idempotency ID.
     *
     * <p>Resolution order: the first non-blank header value found in this list
     * is used as the {@code requestId}.  If no header matches, a random UUID
     * ({@link java.util.UUID#randomUUID()}) is generated.</p>
     *
     * <p>Default: {@code ["X-Request-ID"]} — the de-facto standard used by
     * AWS API Gateway, NGINX, Postman, and most REST clients.</p>
     *
     * <p>Common alternatives you may add:</p>
     * <ul>
     *   <li>{@code request_id} — used by some internal services</li>
     *   <li>{@code X-Correlation-ID} — alternative correlation standard</li>
     *   <li>{@code X-B3-TraceId} — Zipkin/Brave trace header</li>
     *   <li>{@code traceparent} — W3C Trace Context standard (OpenTelemetry)</li>
     * </ul>
     *
     * <p>Property example (comma-separated):</p>
     * <pre>api.request.logging.request-id-headers=X-Request-ID,request_id,X-Correlation-ID</pre>
     */
    private List<String> requestIdHeaders = Arrays.asList("X-Request-ID");

    // ── Path / extension exclusions ───────────────────────────────────────

    /**
     * URI prefixes that are silently skipped by both filters.
     *
     * <p>Requests whose {@link javax.servlet.http.HttpServletRequest#getServletPath()}
     * starts with any entry in this list are excluded.  Exclusions prevent
     * unnecessary body-caching overhead on non-API paths such as Actuator health
     * checks, Swagger UI, and static resources.</p>
     *
     * <p>Defaults exclude common framework paths; add your own as needed:</p>
     * <pre>
     * api.request.logging.exclude-paths=/actuator,/swagger-ui,/internal/metrics
     * </pre>
     */
    private List<String> excludePaths = Arrays.asList(
            "/actuator", "/swagger-ui", "/v3/api-docs",
            "/v2/api-docs", "/swagger-resources",
            "/js/", "/css/", "/images/"
    );

    /**
     * File-extension suffixes that are silently skipped by both filters.
     *
     * <p>Requests whose path (lowercased) ends with any listed extension are
     * excluded — typically static assets that carry no useful API payload.</p>
     *
     * <pre>
     * api.request.logging.exclude-extensions=.js,.css,.html,.png,.ico
     * </pre>
     */
    private List<String> excludeExtensions = Arrays.asList(
            ".js", ".css", ".html", ".scss",
            ".png", ".jpg", ".gif", ".ico", ".wsdl"
    );

    // ── Body / header capture toggles ─────────────────────────────────────

    /**
     * Whether to capture and include the HTTP request body in the log.
     *
     * <p>Default: {@code true}.  Set to {@code false} for endpoints that receive
     * large binary payloads (file uploads) to avoid heap pressure.</p>
     *
     * <pre>api.request.logging.log-request-body=false</pre>
     */
    private boolean logRequestBody = true;

    /**
     * Whether to capture and include the HTTP response body in the log.
     *
     * <p>Default: {@code true}.  Set to {@code false} if response bodies are
     * large or contain sensitive data that must not appear in logs.</p>
     *
     * <pre>api.request.logging.log-response-body=true</pre>
     */
    private boolean logResponseBody = true;

    /**
     * Whether to capture and include all HTTP request headers in the log.
     *
     * <p>Default: {@code true}.  Consider disabling if headers carry sensitive
     * tokens that should not be persisted (use a custom mask filter instead).</p>
     *
     * <pre>api.request.logging.log-headers=true</pre>
     */
    private boolean logHeaders = true;

    // ── Body truncation ───────────────────────────────────────────────────

    /**
     * Maximum number of characters stored from request / response bodies.
     *
     * <p>Bodies longer than this limit are truncated and a
     * {@code [TRUNCATED at N chars]} suffix is appended so the log clearly
     * indicates data was cut.  Default: {@code 4096} characters.</p>
     *
     * <p>Set to {@code -1} for unlimited storage (use with caution — very large
     * JSON payloads consume heap proportional to concurrent request count).</p>
     *
     * <pre>api.request.logging.max-body-length=8192</pre>
     */
    private int maxBodyLength = 4096;

    // ═════════════════════════════════════════════════════════════════════
    //  Getters / Setters — required by Spring's @ConfigurationProperties
    //  binding mechanism.  No Lombok to keep the library dependency-free.
    // ═════════════════════════════════════════════════════════════════════

    /** @return {@code true} when the logging starter is active */
    public boolean isEnabled() { return enabled; }

    /** @param enabled master on/off switch */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** @return ordered list of header names used to resolve a correlation ID */
    public List<String> getRequestIdHeaders() { return requestIdHeaders; }

    /** @param requestIdHeaders ordered list of header names */
    public void setRequestIdHeaders(List<String> requestIdHeaders) {
        this.requestIdHeaders = requestIdHeaders;
    }

    /** @return URI prefixes excluded from logging */
    public List<String> getExcludePaths() { return excludePaths; }

    /** @param excludePaths URI prefixes to skip */
    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    /** @return file extensions excluded from logging */
    public List<String> getExcludeExtensions() { return excludeExtensions; }

    /** @param excludeExtensions extensions to skip */
    public void setExcludeExtensions(List<String> excludeExtensions) {
        this.excludeExtensions = excludeExtensions;
    }

    /** @return whether request body logging is enabled */
    public boolean isLogRequestBody() { return logRequestBody; }

    /** @param logRequestBody enable/disable request body capture */
    public void setLogRequestBody(boolean logRequestBody) {
        this.logRequestBody = logRequestBody;
    }

    /** @return whether response body logging is enabled */
    public boolean isLogResponseBody() { return logResponseBody; }

    /** @param logResponseBody enable/disable response body capture */
    public void setLogResponseBody(boolean logResponseBody) {
        this.logResponseBody = logResponseBody;
    }

    /** @return whether header logging is enabled */
    public boolean isLogHeaders() { return logHeaders; }

    /** @param logHeaders enable/disable header capture */
    public void setLogHeaders(boolean logHeaders) {
        this.logHeaders = logHeaders;
    }

    /** @return maximum body characters stored before truncation */
    public int getMaxBodyLength() { return maxBodyLength; }

    /** @param maxBodyLength truncation threshold; {@code -1} = unlimited */
    public void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }
}
