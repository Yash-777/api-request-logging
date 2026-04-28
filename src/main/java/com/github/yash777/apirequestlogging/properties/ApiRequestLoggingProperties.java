package com.github.yash777.apirequestlogging.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import com.github.yash777.apirequestlogging.aop.HandlerFormat;

import java.util.Arrays;
import java.util.Collections;
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
    
    /**
     * Comma-separated list of header names to omit entirely from the log.
     *
     * <p>Matching is case-insensitive.  Headers in this list are dropped before
     * the log entry is built — they never appear in the output, not even as
     * masked values.  Use this for noisy headers that add no diagnostic value
     * (e.g. {@code user-agent}, {@code postman-token}, {@code cache-control}).</p>
     *
     * <p>Default: empty — all headers are logged (subject to masking rules).</p>
     *
     * <pre>
     * api.request.logging.skip-headers=user-agent,postman-token,cache-control,postman-token,accept-encoding
     * </pre>
     *
     * <p>Difference from {@code mask.fields}:</p>
     * <table border="1" cellpadding="4">
     *   <tr><th>Property</th><th>Effect</th><th>Header appears in log?</th></tr>
     *   <tr><td>{@code skip-headers}</td><td>header dropped entirely</td><td>No</td></tr>
     *   <tr><td>{@code mask.fields}</td><td>value replaced with {@code ***MASKED***}</td><td>Yes (masked)</td></tr>
     * </table>
     */
    private List<String> skipHeaders = Collections.emptyList();

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

    public List<String> getSkipHeaders()              { return skipHeaders; }
    public void setSkipHeaders(List<String> v)        { this.skipHeaders = v; }
    
    /** @return maximum body characters stored before truncation */
    public int getMaxBodyLength() { return maxBodyLength; }

    /** @param maxBodyLength truncation threshold; {@code -1} = unlimited */
    public void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }
    
    
    // ── Nested sections ────────────────────────────────────────────────

    @NestedConfigurationProperty
    private AopProperties aop = new AopProperties();

    @NestedConfigurationProperty
    private RestTemplateProperties restTemplate = new RestTemplateProperties();

    @NestedConfigurationProperty
    private LoggerProperties logger = new LoggerProperties();

    @NestedConfigurationProperty
    private ExceptionProperties exception = new ExceptionProperties();

    @NestedConfigurationProperty
    private MultipartProperties multipart = new MultipartProperties();

    @NestedConfigurationProperty
    private MaskProperties mask = new MaskProperties();

    // ══════════════════════════════════════════════════════════════════
    //  NESTED — AOP
    // ══════════════════════════════════════════════════════════════════

    /**
     * AOP settings for automatic controller-handler logging.
     *
     * <p>The aspect emits a single log line per controller invocation, of the form:</p>
     * <pre>
     *   [CONTROLLER-AOP] handler=[OrderController#createOrder] timeTaken=[12 ms]
     * </pre>
     *
     * <p>The verbosity of the {@code handler=[...]} field is controlled by a
     * single {@link HandlerFormat} value rather than multiple booleans, since the
     * realistic options form a clear progression (simple → qualified → full).
     * The two remaining toggles ({@link #includeArgs}, {@link #includeReturnType})
     * are orthogonal and append additional fields when enabled.</p>
     *
     * <h4>Required dependency (consumer pom.xml)</h4>
     * <pre>{@code
     * <dependency>
     *     <groupId>org.springframework.boot</groupId>
     *     <artifactId>spring-boot-starter-aop</artifactId>
     * </dependency>
     * }</pre>
     * <p>If {@code spring-aop} is absent from the classpath the aspect is silently skipped
     * ({@code @ConditionalOnClass(ProceedingJoinPoint.class)}).</p>
     */
    public static class AopProperties {
        
        /**
         * Master switch — enable AOP-based automatic capture of the controller
         * handler. Requires {@code spring-boot-starter-aop} on the classpath.
         * Default: {@code true}.
         */
        private boolean controllerHandlerEnabled = true;
        
        /**
         * Verbosity of the {@code handler=[...]} field. See {@link HandlerFormat}
         * for the four supported levels. Default: {@link HandlerFormat#SIMPLE}.
         */
        private HandlerFormat handlerFormat = HandlerFormat.SIMPLE;
        
        /**
         * When {@code true}, appends an {@code args=[...]} field containing each
         * runtime argument's {@code toString()} representation.
         *
         * <p><strong>Privacy warning:</strong> request bodies frequently contain
         * PII (emails, addresses, payment data). Keep this {@code false} in
         * production unless you have explicit field-level masking elsewhere.</p>
         *
         * <p>Default: {@code false}.</p>
         */
        private boolean includeArgs = false;
        
        /**
         * When {@code true}, appends a {@code returnType=[...]} field with the
         * declared return type of the controller method. The type is shown with
         * or without its package depending on {@link #handlerFormat}
         * ({@link HandlerFormat#FULL} or {@link HandlerFormat#QUALIFIED} use
         * fully-qualified names).
         *
         * <p>Default: {@code false}.</p>
         */
        private boolean includeReturnType = false;
        
        /**
         * If a controller method takes longer than this many milliseconds, the
         * log line is emitted at {@code WARN} instead of {@code INFO}.
         *
         * <p>Set to {@code -1} (the default) to disable — every successful call
         * is logged at {@code INFO} regardless of duration. Failed calls are
         * always logged at {@code ERROR}.</p>
         */
        private long slowThresholdMs = -1L;
        
        // ── Getters / setters ──────────────────────────────────────────
        
        public boolean       isControllerHandlerEnabled()                { return controllerHandlerEnabled; }
        public void          setControllerHandlerEnabled(boolean v)      { this.controllerHandlerEnabled = v; }
        
        public HandlerFormat getHandlerFormat()                          { return handlerFormat; }
        public void          setHandlerFormat(HandlerFormat v)           { this.handlerFormat = (v == null ? HandlerFormat.SIMPLE : v); }
        
        public boolean       isIncludeArgs()                             { return includeArgs; }
        public void          setIncludeArgs(boolean v)                   { this.includeArgs = v; }
        
        public boolean       isIncludeReturnType()                       { return includeReturnType; }
        public void          setIncludeReturnType(boolean v)             { this.includeReturnType = v; }
        
        public long          getSlowThresholdMs()                        { return slowThresholdMs; }
        public void          setSlowThresholdMs(long v)                  { this.slowThresholdMs = v; }
    }

    // ══════════════════════════════════════════════════════════════════
    //  NESTED — RestTemplateProperties
    // ══════════════════════════════════════════════════════════════════

    /**
     * Settings for automatic {@link org.springframework.web.client.RestTemplate}
     * call capture.
     *
     * <p>When {@link #isAutoCaptureEnabled()} is {@code true}, a
     * {@code BeanPostProcessor} injects a logging interceptor into every
     * {@code RestTemplate} bean registered in the Spring context.</p>
     * 
     * <h3>Quick setup</h3>
     * <pre>
     * # Enable auto-injection of the logging interceptor
     * api.request.logging.rest-template.auto-capture-enabled=true
     *
     * # Skip credential-bearing endpoints (ends-with mode — host-agnostic)
     * api.request.logging.rest-template.skip.urls=/platform/oauth/oauth2/token,/internal/health
     * api.request.logging.rest-template.skip.urls-match-endswith=true
     *
     * # OR skip by full URL (exact equality)
     * api.request.logging.rest-template.skip.urls=http://localhost:8080/platform/oauth/oauth2/token
     * api.request.logging.rest-template.skip.urls-match-full=true
     * </pre>
     *
     * <p><strong>Limitation:</strong> only Spring-managed {@code RestTemplate}
     * beans are intercepted. Instances created with {@code new RestTemplate()}
     * inside a method body are invisible to Spring and cannot be auto-captured.</p>
     */
    public static class RestTemplateProperties {

        /**
         * Auto-inject a logging interceptor into all Spring-managed
         * {@link org.springframework.web.client.RestTemplate} beans.
         * Default: {@code false} — opt-in to preserve v1.0.x behaviour.
         */
        private boolean autoCaptureEnabled = false;

        /** Capture RestTemplate request body. Default: {@code true}. */
        private boolean logRequestBody = true;

        /** Capture RestTemplate response body. Default: {@code true}. */
        private boolean logResponseBody = true;

        /**
         * Maximum characters stored from RestTemplate request/response bodies.
         * Set to {@code -1} for unlimited. Default: {@code 4096}.
         */
        private int maxBodyLength = 4096;

        /**
         * URL skip configuration — endpoints that should never be logged
         * (e.g. OAuth2 token URLs whose bodies contain credentials).
         */
        @org.springframework.boot.context.properties.NestedConfigurationProperty
        private SkipProperties skip = new SkipProperties();
        
        // ── Getters / Setters ─────────────────────────────────────────
        
        public boolean isAutoCaptureEnabled()            { return autoCaptureEnabled; }
        public void setAutoCaptureEnabled(boolean v)     { this.autoCaptureEnabled = v; }
        public boolean isLogRequestBody()                { return logRequestBody; }
        public void setLogRequestBody(boolean v)         { this.logRequestBody = v; }
        public boolean isLogResponseBody()               { return logResponseBody; }
        public void setLogResponseBody(boolean v)        { this.logResponseBody = v; }
        public int getMaxBodyLength()                    { return maxBodyLength; }
        public void setMaxBodyLength(int v)              { this.maxBodyLength = v; }
        public SkipProperties getSkip()                  { return skip; }
        public void setSkip(SkipProperties v)            { this.skip = v; }

        // ── Nested: SkipProperties ────────────────────────────────────

        /**
         * URL skip rules for {@link com.github.yash777.apirequestlogging.resttemplate.RestTemplateLoggingInterceptor}.
         *
         * <h3>ends-with mode (host-agnostic)</h3>
         * <p>Use a URL path suffix so the same pattern matches any host:</p>
         * <pre>
         * api.request.logging.rest-template.skip.urls=/platform/oauth/oauth2/token
         * api.request.logging.rest-template.skip.urls-match-endswith=true
         * </pre>
         * <p>Matches:
         * {@code http://localhost:8080/platform/oauth/oauth2/token} ✅<br>
         * {@code https://prod.example.com/platform/oauth/oauth2/token} ✅</p>
         *
         * <h3>full URL mode (exact equality)</h3>
         * <pre>
         * api.request.logging.rest-template.skip.urls=http://localhost:8080/platform/oauth/oauth2/token
         * api.request.logging.rest-template.skip.urls-match-full=true
         * </pre>
         * <p>Matches only the exact URL including scheme, host, port, and path.</p>
         *
         * <h3>Both modes together</h3>
         * <p>Both flags can be {@code true} simultaneously — a URL is skipped
         * when <em>either</em> condition matches.</p>
         *
         * <h3>Multiple URLs</h3>
         * <pre>
         * api.request.logging.rest-template.skip.urls=\
         *     /platform/oauth/oauth2/token,\
         *     /internal/health,\
         *     /actuator/info
         * </pre>
         */
        public static class SkipProperties {

            /**
             * Comma-separated list of URL patterns to skip.
             *
             * <p>Each entry is matched against the full request URL using the
             * mode(s) selected by {@link #isUrlsMatchEndswith()} and/or
             * {@link #isUrlsMatchFull()}.  Entries may be path suffixes
             * (for ends-with mode) or full URLs (for full-match mode).</p>
             *
             * <p>Default: empty — no URLs are skipped.</p>
             *
             * <pre>
             * # Path-suffix entries (use with urls-match-endswith=true):
             * api.request.logging.rest-template.skip.urls=\
             *     /platform/oauth/oauth2/token,/internal/health
             *
             * # Full URL entries (use with urls-match-full=true):
             * api.request.logging.rest-template.skip.urls=\
             *     http://localhost:8080/platform/oauth/oauth2/token
             * </pre>
             */
            private java.util.List<String> urls = java.util.Collections.emptyList();

            /**
             * When {@code true}, a URL is skipped if the full request URL
             * <em>ends with</em> any entry in {@link #getUrls()}.
             *
             * <p>This is the recommended mode when the skip list contains path
             * suffixes — the match is host-agnostic, so the same pattern works
             * in local dev, staging, and production without changing the property.</p>
             *
             * <p>Default: {@code false}.</p>
             *
             * <pre>
             * api.request.logging.rest-template.skip.urls-match-endswith=true
             * </pre>
             */
            private boolean urlsMatchEndswith = false;

            /**
             * When {@code true}, a URL is skipped if the full request URL
             * <em>equals</em> any entry in {@link #getUrls()} (case-sensitive,
             * query-string inclusive).
             *
             * <p>Use this mode when you need to skip an exact URL and want to
             * be sure that similar paths on different hosts are not skipped.</p>
             *
             * <p>Default: {@code false}.</p>
             *
             * <pre>
             * api.request.logging.rest-template.skip.urls-match-full=true
             * </pre>
             */
            private boolean urlsMatchFull = false;

            // ── Getters / Setters ─────────────────────────────────────

            public java.util.List<String> getUrls()          { return urls; }
            public void setUrls(java.util.List<String> v)    { this.urls = v; }
            public boolean isUrlsMatchEndswith()              { return urlsMatchEndswith; }
            public void setUrlsMatchEndswith(boolean v)       { this.urlsMatchEndswith = v; }
            public boolean isUrlsMatchFull()                  { return urlsMatchFull; }
            public void setUrlsMatchFull(boolean v)           { this.urlsMatchFull = v; }
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    //  NESTED — Logger
    // ══════════════════════════════════════════════════════════════════

    /**
     * Controls where log output is written.
     *
     * <h4>Logback / Log4j2 integration</h4>
     * <pre>
     * # application.properties
     * logging.level.api.request.logging=INFO
     * </pre>
     * <pre>
     * &lt;!-- logback.xml — route to a dedicated file --&gt;
     * &lt;logger name="api.request.logging" level="INFO" additivity="false"&gt;
     *     &lt;appender-ref ref="REQUEST_LOG_FILE"/&gt;
     * &lt;/logger&gt;
     * </pre>
     */
    public static class LoggerProperties {
        /**
         * Route log output to an SLF4J logger (default {@code true}).
         * Replaces the v1.0.x {@code System.out.println} output.
         */
        private boolean enabled = true;

        /**
         * Logger name used for SLF4J output. Configure its level and appenders
         * in {@code logback.xml} / {@code log4j2.xml}. Default: {@code api.request.logging}.
         */
        private String name = "api.request.logging";

        /**
         * Also print to {@code System.out} (legacy behaviour from v1.0.x).
         * Default: {@code false}.
         */
        private boolean sysoutEnabled = false;

        public boolean isEnabled()              { return enabled; }
        public void setEnabled(boolean v)       { this.enabled = v; }
        public String getName()                 { return name; }
        public void setName(String v)           { this.name = v; }
        public boolean isSysoutEnabled()        { return sysoutEnabled; }
        public void setSysoutEnabled(boolean v) { this.sysoutEnabled = v; }
    }

    // ══════════════════════════════════════════════════════════════════
    //  NESTED — Exception
    // ══════════════════════════════════════════════════════════════════

    /**
     * Controls how exception stack traces are stored in the log.
     *
     * <h4>Sample output</h4>
     * <pre>
     *   errorIndicator:     ERROR:NullPointerException
     *   exceptionStacktrace: java.lang.NullPointerException: null
     *                          at com.example.UserController.listUsers(UserController.java:25)
     *                          at ...
     *                          ... (truncated to 5 lines)
     * </pre>
     */
    public static class ExceptionProperties {
        /**
         * Maximum stack-trace lines stored when an exception is logged.
         * Full traces from Spring/Hibernate are 60–100 lines; 5 lines is enough
         * to identify the exception class, message, and application frames.
         * Default: {@code 5}.
         */
        private int maxLines = 5;

        public int getMaxLines()    { return maxLines; }
        public void setMaxLines(int v) { this.maxLines = v; }
    }

    // ══════════════════════════════════════════════════════════════════
    //  NESTED — Multipart
    // ══════════════════════════════════════════════════════════════════

    /**
     * Controls buffering behaviour for multipart and binary request bodies.
     *
     * <p>Buffering a large file upload into heap (via {@code ContentCachingRequestWrapper})
     * for every concurrent request creates GC pressure and risks OOM errors.
     * When {@link #isSkipBinary()} is {@code true}, the caching wrapper is not applied
     * to requests whose {@code Content-Type} matches any of the skip patterns.</p>
     *
     * <p>When skipped, the log shows:</p>
     * <pre>
     *   requestBody: [binary/multipart content skipped — Content-Type: image/png]
     * </pre>
     */
    public static class MultipartProperties {
        /**
         * Skip buffering binary and multipart request bodies. Default: {@code true}.
         */
        private boolean skipBinary = true;

        /**
         * {@code Content-Type} prefixes that trigger the skip.
         * Default: {@code image/, audio/, video/, multipart/, application/octet-stream}.
         */
        private List<String> skipContentTypes = Arrays.asList(
                "image/", "audio/", "video/", "multipart/", "application/octet-stream");

        /**
         * Whitelist of {@code Content-Type} prefixes whose bodies ARE captured.
         * When non-empty, only bodies matching this list are buffered (overrides skipBinary).
         * Example: {@code application/json,application/xml,text/}
         * Default: empty (whitelist not active).
         */
        private List<String> captureOnlyContentTypes = Arrays.asList();

        public boolean isSkipBinary()                    { return skipBinary; }
        public void setSkipBinary(boolean v)             { this.skipBinary = v; }
        public List<String> getSkipContentTypes()        { return skipContentTypes; }
        public void setSkipContentTypes(List<String> v)  { this.skipContentTypes = v; }
        public List<String> getCaptureOnlyContentTypes() { return captureOnlyContentTypes; }
        public void setCaptureOnlyContentTypes(List<String> v) { this.captureOnlyContentTypes = v; }
    }

    // ══════════════════════════════════════════════════════════════════
    //  NESTED — Mask
    // ══════════════════════════════════════════════════════════════════

    /**
     * Secret masking applied to request/response bodies and headers before logging.
     *
     * <h4>Sample output</h4>
     * <pre>
     *   headers:      {"authorization":"***MASKED***","content-type":"application/json"}
     *   requestBody:  {"username":"john","password":"***MASKED***","amount":500}
     * </pre>
     *
     * <h4>How it works</h4>
     * <p>Field values matching any name in {@link #getFields()} are replaced with
     * {@link #getReplacement()} using a case-insensitive regex applied to the stored
     * JSON string.  The masking happens inside {@code RequestLogCollector} after
     * JSON serialisation and before storage in the log map.</p>
     *
     * <p><strong>Note:</strong> regex-based masking works reliably for flat JSON.
     * Deeply nested structures may require a Jackson-based approach in a future version.</p>
     */
    public static class MaskProperties {
        /** Enable secret masking. Default: {@code false}. */
        private boolean enabled = false;

        /**
         * Field names whose values are masked. Case-insensitive.
         * Default covers the most common sensitive field names.
         */
        private List<String> fields = Arrays.asList(
                "password", "token", "secret", "authorization",
                "x-api-key", "api-key", "access-token", "refresh-token",
                "client-secret", "passwd", "credential");

        /** Value substituted for masked fields. Default: {@code ***MASKED***}. */
        private String replacement = "***MASKED***";

        public boolean isEnabled()              { return enabled; }
        public void setEnabled(boolean v)       { this.enabled = v; }
        public List<String> getFields()         { return fields; }
        public void setFields(List<String> v)   { this.fields = v; }
        public String getReplacement()          { return replacement; }
        public void setReplacement(String v)    { this.replacement = v; }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ROOT GETTERS / SETTERS
    // ══════════════════════════════════════════════════════════════════

    public AopProperties getAop()                       { return aop; }
    public void setAop(AopProperties v)                 { this.aop = v; }
    public RestTemplateProperties getRestTemplate()     { return restTemplate; }
    public void setRestTemplate(RestTemplateProperties v) { this.restTemplate = v; }
    public LoggerProperties getLogger()                 { return logger; }
    public void setLogger(LoggerProperties v)           { this.logger = v; }
    public ExceptionProperties getException()           { return exception; }
    public void setException(ExceptionProperties v)     { this.exception = v; }
    public MultipartProperties getMultipart()           { return multipart; }
    public void setMultipart(MultipartProperties v)     { this.multipart = v; }
    public MaskProperties getMask()                     { return mask; }
    public void setMask(MaskProperties v)               { this.mask = v; }
}
