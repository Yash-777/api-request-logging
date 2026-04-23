package com.github.yash777.apirequestlogging.filter;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.collector.RequestLogCollectorApi;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;
import com.github.yash777.apirequestlogging.util.RequestResponseCaptureUtil;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StopWatch;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <h2>ApiLoggingFilter</h2>
 *
 * <p>The entry/exit logging gate for every HTTP request.  Integrates with
 * {@link RequestLogCollector} (request-scoped) to build a complete structured
 * log for each request, including all third-party calls made during processing.</p>
 *
 * <p>This bean and its inner {@link FilterOrderConfig} are active only when
 * {@code api.request.logging.enabled=true} is set.</p>
 *
 * <h3>Filter chain execution order</h3>
 * <pre>
 *  Order  -105   RequestContextFilter         ★ populates RequestContextHolder
 *                                               (@RequestScope becomes usable)
 *  Order  -104   RequestBodyCachingFilter        wraps req + res in caching wrappers
 *  Order  -103   ApiLoggingFilter  ◄             this class
 *  Order  -100   Spring Security (if present)
 *                DispatcherServlet → @Controller → @Service
 * </pre>
 *
 * <h3>Request timing — Spring {@code StopWatch}</h3>
 * <p>{@link StopWatch} is created as a <em>local variable</em> inside
 * {@link #doFilterInternal}, which makes it <strong>thread-safe by design</strong>:
 * each request thread owns its own {@link StopWatch} instance — no sharing,
 * no synchronisation needed.</p>
 *
 * <p>Timing covers the full request-processing window:</p>
 * <pre>
 *   StopWatch starts ──► chain.doFilter() (controller + services + 3rd-party calls)
 *                    ◄── StopWatch stops in finally block
 * </pre>
 *
 * <p>The elapsed time is formatted as {@code "0h 0m 0s 312ms"} via
 * {@link #formatElapsed(long)} and stored under the
 * {@link RequestLogCollector#INCOMING_KEY} entry as {@code requestProcessedTime}.</p>
 *
 * <h3>Why {@code @Configuration} not {@code @Component}</h3>
 * <p>The inner {@link FilterOrderConfig} class declares {@code @Bean} methods.
 * {@code @Configuration} triggers CGLIB processing on the outer class, ensuring
 * Spring's singleton semantics apply to those beans.  {@code @Component} would
 * not apply CGLIB and the {@code @Bean} methods would create new instances on
 * every call.</p>
 *
 * <h3>StopWatch — version compatibility note</h3>
 * <p>{@link StopWatch#getTotalTimeMillis()} is used (not {@code getTotalTimeNanos()})
 * for compatibility with Spring Framework 4.x / Spring Boot 2.x.
 * {@code getTotalTimeNanos()} was added in Spring 5.2 (Spring Boot 2.3+).
 * If you are on Spring Boot 2.3+ you may switch to {@code getTotalTimeNanos()}
 * and pass it directly to {@link #formatElapsed(long)} for sub-millisecond precision.</p>
 *
 * @author Yash
 * @since 1.0.0
 * @see RequestLogCollector
 * @see RequestBodyCachingFilter
 */
@Configuration
@ConditionalOnProperty(
    prefix = "api.request.logging",
    name   = "enabled",
    havingValue = "true"
)
public class ApiLoggingFilter extends OncePerRequestFilter {

    /**
     * CGLIB proxy of {@link RequestLogCollector} injected at startup.
     *
     * <p>This field holds the proxy, <em>not</em> a real instance.
     * The proxy resolves the real per-request bean from
     * {@code RequestContextHolder} on every method call.  The field is
     * {@code final} — written once at construction and never mutated.</p>
     */
    private final RequestLogCollectorApi collector;

    /** Externalized configuration — drives exclusion lists and body-capture toggles. */
    private final ApiRequestLoggingProperties properties;

    /**
     * Constructor injection — preferred over {@code @Autowired} field injection
     * because it makes dependencies explicit, {@code final}, and verifiable at
     * compile time.
     *
     * @param collector  CGLIB proxy of the request-scoped {@link RequestLogCollector}
     * @param properties externalized configuration
     */
    public ApiLoggingFilter(RequestLogCollectorApi collector,
                            ApiRequestLoggingProperties properties) {
        this.collector  = collector;
        this.properties = properties;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  PATH / EXTENSION EXCLUSIONS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Determines whether this filter should be skipped for the given request.
     *
     * <p>Exclusion rules applied in order:</p>
     * <ol>
     *   <li>Skip HTTP {@code OPTIONS} pre-flight requests (CORS)</li>
     *   <li>Skip paths whose prefix matches
     *       {@link ApiRequestLoggingProperties#getExcludePaths()}</li>
     *   <li>Skip static assets whose extension matches
     *       {@link ApiRequestLoggingProperties#getExcludeExtensions()}</li>
     * </ol>
     *
     * <p>Using {@link HttpServletRequest#getServletPath()} (not {@code getRequestURI()})
     * for more accurate matching when the application is deployed under a context path.</p>
     *
     * <p><strong>Why skip excluded paths?</strong><br>
     * Logging Actuator health checks, Swagger UI, and static assets is noisy
     * and wastes heap.  More importantly, accessing the request-scoped
     * {@link RequestLogCollector} outside an active web request context throws
     * {@code IllegalStateException: No thread-bound request found}.</p>
     *
     * @param request the current request
     * @return {@code true} to skip this filter; {@code false} to proceed
     */
    @Override
    public boolean shouldNotFilter(HttpServletRequest request) {

        // 1. Skip CORS pre-flight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getServletPath();

        // 2. Skip explicitly excluded path prefixes
        List<String> excludedPaths = properties.getExcludePaths();
        if (excludedPaths != null && excludedPaths.stream().anyMatch(path::startsWith)) {
            return true;
        }

        // 3. Skip static file extensions
        String lowerPath = path.toLowerCase();
        List<String> excludedExts = properties.getExcludeExtensions();
        if (excludedExts != null) {
            return excludedExts.stream().anyMatch(lowerPath::endsWith);
        }

        return false;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  CORE FILTER LOGIC
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Core filter logic — executed exactly once per request (guaranteed by
     * {@link OncePerRequestFilter}).
     *
     * <h4>Execution steps</h4>
     * <ol>
     *   <li><strong>Capture metadata</strong> — URL, method, headers, requestId,
     *       threadName, timestamp — recorded before the chain runs (always present
     *       even if the controller throws).</li>
     *   <li><strong>Start StopWatch</strong> — begins timing immediately before
     *       {@code chain.doFilter()}.</li>
     *   <li><strong>Run chain</strong> — hands control to the next filter, then
     *       the controller, then any {@code @Service} calls.</li>
     *   <li><strong>finally block</strong> — stop StopWatch, read cached request
     *       body (conditional), read cached response body + status (conditional),
     *       store elapsed time, print.</li>
     * </ol>
     *
     * <h4>StopWatch locality = thread safety</h4>
     * <p>{@code StopWatch sw} is a local variable.  Java's memory model guarantees
     * that local variables exist on the current thread's stack frame — they are
     * never shared.  Each concurrent request gets its own {@code StopWatch}.</p>
     *
     * <h4>StopWatch version compatibility</h4>
     * <p>This implementation calls {@link StopWatch#getTotalTimeMillis()} which
     * is available since Spring Framework 4.0 (Spring Boot 1.x and above).
     * The value is passed to {@link #formatElapsed(long)} which expects
     * milliseconds (NOT nanoseconds).  If you upgrade to Spring Boot 2.3+ and
     * wish to use {@code getTotalTimeNanos()}, convert to millis first or adjust
     * the {@link #formatElapsed} method signature accordingly.</p>
     *
     * @param request  the current {@link HttpServletRequest}
     *                 (wrapped by {@link RequestBodyCachingFilter})
     * @param response the current {@link HttpServletResponse}
     *                 (wrapped by {@link RequestBodyCachingFilter})
     * @param chain    the remaining filter chain
     * @throws ServletException propagated from downstream filters/servlet
     * @throws IOException      propagated from downstream filters/servlet
     */
    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        // ── STEP 1: Capture request metadata ─────────────────────────────
        // URL, method, headers, requestId (UUID or configured header), threadName.
        // Executed BEFORE chain.doFilter() — always recorded even on exception.
        collector.addRequestMeta(request);

        // ── STEP 2: Start timing ──────────────────────────────────────────
        //
        // StopWatch is a LOCAL variable — a new instance per call to this method.
        // Each request runs on its own thread; local variables live on the
        // thread's stack frame, so concurrent requests never share this object.
        //
        // StopWatch is from org.springframework.util — no external dependency.
        // It is NOT thread-safe for shared use, but here it is never shared.
        //
        StopWatch sw = new StopWatch("ApiLoggingFilter");
        sw.start("request-processing");

        // Track whether chain.doFilter() was actually called.
        // When a consumer filter short-circuits (sendError + return),
        // chainInvoked stays false — we still flush what we can in finally.
        boolean chainInvoked = false;
        
        try {
            // ── STEP 3: Delegate to the rest of the chain ─────────────────
            // Blocks until the controller returns (or throws).
            // Any @Service that injects RequestLogCollector reaches the SAME
            // real instance via the proxy (same thread = same RequestAttributes).
            chain.doFilter(request, response);

            chainInvoked = true;
        } finally {

            // ── STEP 4: Stop timing ───────────────────────────────────────
            // stop() is in finally to guarantee it is called even when the
            // controller throws a RuntimeException or Error.
            sw.stop();

            int responseStatus = response.getStatus();
            
            // ── STEP 5: Request body type + body ──────────────────────────
            if (properties.isLogRequestBody()) {
                RequestResponseCaptureUtil.logRequestBodyType(request, collector);
                RequestResponseCaptureUtil.captureRequestBody(
                        request, chainInvoked, responseStatus, collector, properties);
            }

            // ── STEP 6: Redirect path (3xx only — no-op for all other responses) ──
            // Reads the Location response header; logs "redirectPath" when present.
            RequestResponseCaptureUtil.captureRedirectPath(response, collector);
            
            // ── STEP 7: Response status + body / error ────────────────────
            // Read BEFORE RequestBodyCachingFilter's finally calls copyBodyToResponse()
            // — that flushes and clears the buffer.
            // Finally-block unwind order:
            //   this filter (-103) unwinds FIRST  → reads cached bytes here
            //   RequestBodyCachingFilter (-104) unwinds AFTER → flushes to socket
            if (response instanceof ContentCachingResponseWrapper) {
                ContentCachingResponseWrapper cr =
                        (ContentCachingResponseWrapper) response;
                collector.addLog(RequestLogCollector.INCOMING_KEY,
                        "responseStatus", String.valueOf(responseStatus));
 
                if (properties.isLogResponseBody()) {
                    RequestResponseCaptureUtil.captureResponseBody(
                            cr, request, responseStatus, chainInvoked, collector, properties);
                }
            } else {
                // Response not wrapped (edge case) — still record the status
                collector.addLog(RequestLogCollector.INCOMING_KEY,
                        "responseStatus", String.valueOf(responseStatus));
            }

            // ── STEP 8: Store formatted elapsed time ──────────────────────
            //
            // sw.getTotalTimeMillis() — returns elapsed milliseconds.
            // Available since Spring Framework 4.0; safe for Spring Boot 2.5+.
            //
            // Spring Boot 2.3+ note: getTotalTimeNanos() is available from
            // Spring 5.2 onward.  To use nanoseconds precision:
            //   long totalNanos = sw.getTotalTimeNanos();
            //   formatElapsed() would need to accept nanos and convert.
            // For maximum compatibility we use millis here.
            //
            long totalMillis = sw.getTotalTimeMillis();
            collector.addLog(RequestLogCollector.INCOMING_KEY,
                    "requestProcessedTime", formatElapsed(totalMillis));

            // ── STEP 9: Print/Flush ────────────────────────────────────
            // All fields are now present.  One coherent chronological block.
            collector.printLogs();
            
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    //  TIME FORMATTING
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Formats a millisecond duration as a human-readable string.
     *
     * <p>Examples:</p>
     * <pre>
     *   312L           →  "0h 0m 0s 312ms"
     *   1_500L         →  "0h 0m 1s 500ms"
     *   65_312L        →  "0h 1m 5s 312ms"
     *   3_723_500L     →  "1h 2m 3s 500ms"
     * </pre>
     *
     * <p><strong>Thread safety:</strong> this is a pure static method with no
     * shared mutable state — safe under any concurrency.</p>
     *
     * <p><strong>Spring Boot version note:</strong> this method accepts
     * <em>milliseconds</em> (from {@link StopWatch#getTotalTimeMillis()}).
     * If you switch to {@code getTotalTimeNanos()} (Spring Boot 2.3+), convert
     * to millis first: {@code TimeUnit.NANOSECONDS.toMillis(totalNanos)}.</p>
     *
     * @param totalMillis total elapsed time in <strong>milliseconds</strong>
     * @return formatted string, e.g. {@code "0h 0m 0s 312ms"}
     */
    public static String formatElapsed(long totalMillis) {
        long hours   = TimeUnit.MILLISECONDS.toHours(totalMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis)  % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(totalMillis)  % 60;
        long millis  = totalMillis % 1_000;

        // String.format — not String.formatted() (Java 15+) — for Java 8 compat.
        return String.format("%dh %dm %ds %dms", hours, minutes, seconds, millis);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  INNER CLASS — filter registration and order
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Registers both filters with explicit, safe execution order via
     * {@link FilterRegistrationBean}.
     *
     * <h4>Why {@code FilterRegistrationBean} instead of {@code @Order}</h4>
     * <p>Spring Boot auto-registers every {@code @Component}/{@code @Configuration}
     * that implements {@link javax.servlet.Filter}.  If {@code @Order} is also
     * present on the class, Boot applies <em>both</em> the annotation order
     * and the {@code FilterRegistrationBean} order — the filter runs twice per
     * request.  We keep {@code @Order} off the filter classes entirely and control
     * order exclusively here.</p>
     *
     * <h4>Why inner class</h4>
     * <p>Both classes are tightly coupled — {@code ApiLoggingFilter} cannot
     * function without being registered at the correct order, and
     * {@code FilterOrderConfig} exists solely to register it.  Co-location
     * avoids an additional top-level class without architectural compromise.</p>
     */
    @Configuration
    @ConditionalOnProperty(
        prefix = "api.request.logging",
        name   = "enabled",
        havingValue = "true"
    )
    static class FilterOrderConfig {

        private final RequestBodyCachingFilter cachingFilter;
        private final ApiLoggingFilter         loggingFilter;

        /**
         * Constructor injection — Spring fully resolves both filter beans before
         * constructing this config, ensuring correct initialisation order.
         */
        FilterOrderConfig(RequestBodyCachingFilter cachingFilter,
                          ApiLoggingFilter loggingFilter) {
            this.cachingFilter = cachingFilter;
            this.loggingFilter = loggingFilter;
        }

        /**
         * Registers {@link RequestBodyCachingFilter} at order {@code -104}.
         *
         * <p>Order {@code -104} is one position after {@code RequestContextFilter}
         * ({@code -105}), so {@code @RequestScope} beans are resolvable, and one
         * position before Spring Security ({@code -100}), so the
         * {@link ContentCachingRequestWrapper} is in place before Security reads
         * the body.</p>
         *
         * @return configured {@link FilterRegistrationBean}
         */
        @Bean
        public FilterRegistrationBean<RequestBodyCachingFilter> cachingFilterReg() {
            FilterRegistrationBean<RequestBodyCachingFilter> bean =
                    new FilterRegistrationBean<>(cachingFilter);
            bean.setOrder(-104);   // after RequestContextFilter (-105), before Security (-100)
            bean.addUrlPatterns("/*");
            bean.setName("requestBodyCachingFilter");
            return bean;
        }

        /**
         * Registers {@link ApiLoggingFilter} at order {@code -103}.
         *
         * <p>Runs immediately after {@link RequestBodyCachingFilter} ({@code -104})
         * so the {@link ContentCachingRequestWrapper} and
         * {@link ContentCachingResponseWrapper} are guaranteed to be in place.</p>
         *
         * @return configured {@link FilterRegistrationBean}
         */
        @Bean
        public FilterRegistrationBean<ApiLoggingFilter> loggingFilterReg() {
            FilterRegistrationBean<ApiLoggingFilter> bean =
                    new FilterRegistrationBean<>(loggingFilter);
            bean.setOrder(-103);   // after RequestBodyCachingFilter (-104)
            bean.addUrlPatterns("/*");
            bean.setName("apiLoggingFilter");
            return bean;
        }
    }
}
