package com.github.yash777.apirequestlogging.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;
import com.github.yash777.apirequestlogging.util.TimestampUtils;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <h2>RequestLogCollector</h2>
 *
 * <p>A <strong>Spring-managed, request-scoped</strong> log accumulator.
 * One fresh instance is created per HTTP request; it survives for the entire
 * request/response lifecycle and is destroyed (see {@link #cleanup()}) when
 * the request ends.</p>
 *
 * <p>This bean is active only when
 * {@code api.request.logging.enabled=true} is set.</p>
 *
 * <h3>What it collects</h3>
 * <ul>
 *   <li>Incoming request metadata — URL, method, headers, query params, timestamp</li>
 *   <li>Correlation / idempotency ID — {@code requestId} resolved from configurable headers</li>
 *   <li>Thread name — for JVM-thread-dump correlation</li>
 *   <li>Total request processing time — measured by {@link com.github.yash777.apirequestlogging.filter.ApiLoggingFilter}</li>
 *   <li>Outgoing 3rd-party calls — request + response per attempt, with timestamps for retry visibility</li>
 *   <li>Final HTTP response — status code + body</li>
 * </ul>
 *
 * <h3>Scope and proxy — why {@code proxyMode = TARGET_CLASS} is mandatory</h3>
 * <p>{@link com.github.yash777.apirequestlogging.filter.ApiLoggingFilter} is a
 * <em>singleton</em> bean created once at startup.
 * {@code RequestLogCollector} is <em>request-scoped</em> — there is no instance
 * at startup.  Spring solves this by injecting a lightweight
 * <strong>CGLIB proxy</strong> into the singleton field.  On every method call
 * the proxy asks {@code RequestContextHolder} for the real per-request instance:</p>
 * <pre>
 *   singleton field  →  CGLIB proxy
 *                            ↓  (each method call)
 *                       RequestContextHolder.currentRequestAttributes()
 *                            ↓
 *                       real RequestLogCollector for THIS thread/request
 * </pre>
 * <p>This only works after {@code RequestContextFilter} (order {@code -105})
 * has populated {@code RequestContextHolder}.  The logging filters run at
 * {@code -104} / {@code -103} — just after.</p>
 *
 * <h3>ThreadLocal — ambient request-ID without injection</h3>
 * <p>{@link #CURRENT_REQUEST_ID} mirrors the {@code requestId} in a
 * {@link ThreadLocal} so it is readable from any class on the same thread
 * without injecting this bean:</p>
 * <pre>
 *   String id = RequestLogCollector.currentRequestId();
 *   // or:
 *   MDC.put("requestId", RequestLogCollector.currentRequestId());
 * </pre>
 * <p><strong>ThreadLocal leak prevention:</strong> the value is removed in
 * {@link #cleanup()} ({@code @PreDestroy}).  Tomcat reuses threads; without
 * explicit removal, request B would see request A's ID.</p>
 *
 * <h3>Using from your services</h3>
 * <pre>{@code
 * @Service
 * public class PaymentService {
 *
 *     @Autowired
 *     private RequestLogCollector collector;   // proxy — safe in singleton
 *
 *     public PaymentResponse charge(PaymentRequest req) {
 *         String key = collector.buildRetryKey("PaymentGateway/charge");
 *         collector.addLog(key, "request", req);
 *         PaymentResponse res = gateway.post(req);
 *         collector.addLog(key, "response", res);
 *         return res;
 *     }
 * }
 * }</pre>
 *
 * @author Yash
 * @since 1.0.0
 * @see com.github.yash777.apirequestlogging.filter.ApiLoggingFilter
 * @see com.github.yash777.apirequestlogging.filter.RequestBodyCachingFilter
 */
@Component
@Scope(
    value     = WebApplicationContext.SCOPE_REQUEST,
    proxyMode = ScopedProxyMode.TARGET_CLASS   // mandatory — lets singletons hold a proxy reference
)
@ConditionalOnProperty(
    prefix = "api.request.logging",
    name   = "enabled",
    havingValue = "true"
)
public class RequestLogCollector {

    // ── Shared, thread-safe static infrastructure ─────────────────────────
    //
    // ObjectMapper  — thread-safe after construction (Jackson docs guarantee this).
    //                 One instance for the JVM lifetime; not recreated per request.
    //
    // TS_FMT        — DateTimeFormatter is immutable and thread-safe (Java 8+).
    //                 Pattern "HH:mm:ss.SSS" → "14:32:05.001" — ms precision is
    //                 enough to distinguish retries that happen seconds apart.
    //
    private static final ObjectMapper      MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // ── ThreadLocal — ambient request-ID without any injection ────────────
    //
    // Lifecycle: set in addRequestMeta() → read via currentRequestId()
    //            cleared in cleanup() (@PreDestroy) to prevent thread-pool reuse leaks.
    //
    private static final ThreadLocal<String> CURRENT_REQUEST_ID = new ThreadLocal<>();

    /**
     * Returns the correlation/idempotency ID for the request currently
     * executing on this thread.  Safe to call from any class without injecting
     * {@code RequestLogCollector}.
     *
     * <pre>{@code
     * // In any @Service, @Component, or utility class:
     * String id = RequestLogCollector.currentRequestId();
     *
     * // Typical MDC usage (Logback / Log4j2):
     * MDC.put("requestId", RequestLogCollector.currentRequestId());
     * }</pre>
     *
     * @return the requestId, or {@code null} if called outside a request thread
     */
    public static String currentRequestId() {
        return CURRENT_REQUEST_ID.get();
    }

    // ── Constants ─────────────────────────────────────────────────────────

    /**
     * Outer-map key used for the main incoming-request entry.
     * All fields logged by {@link #addRequestMeta(HttpServletRequest)} and by
     * {@link com.github.yash777.apirequestlogging.filter.ApiLoggingFilter}'s
     * finally block are stored under this key.
     */
    public static final String INCOMING_KEY = "INCOMING";

    // ── Per-instance state ────────────────────────────────────────────────
    //
    // mapLog  — NOT static. Each @RequestScope instance has its own map.
    //           Outer key: INCOMING_KEY or "ServiceName [HH:mm:ss.SSS]"
    //           Inner key: field name   ("request", "response", "error", …)
    //           LinkedHashMap preserves insertion order → chronological output.
    //
    private final Map<String, Map<String, String>> mapLog = new LinkedHashMap<>();
    private String requestId;

    /** Properties injected at construction — drives header resolution. */
    private final ApiRequestLoggingProperties properties;

    /**
     * Constructor injection — Spring resolves and fully constructs the
     * {@link ApiRequestLoggingProperties} bean before calling this constructor.
     *
     * @param properties externalized configuration
     */
    public RequestLogCollector(ApiRequestLoggingProperties properties) {
        this.properties = properties;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  REQUEST METADATA
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Captures all metadata for the incoming HTTP request and stores it under
     * {@link #INCOMING_KEY}.  Called by
     * {@link com.github.yash777.apirequestlogging.filter.ApiLoggingFilter}
     * <em>before</em> {@code chain.doFilter()} so every field is always present
     * even when the controller throws an unchecked exception.
     *
     * <h4>Fields captured</h4>
     * <table border="1" cellpadding="4">
     *   <tr><th>Field</th><th>Source</th><th>Notes</th></tr>
     *   <tr><td>requestId</td>
     *       <td>First non-blank match from
     *           {@link ApiRequestLoggingProperties#getRequestIdHeaders()}, or UUID</td>
     *       <td>Also stored as request attribute and in {@link ThreadLocal}</td></tr>
     *   <tr><td>threadName</td>
     *       <td>{@code Thread.currentThread().getName()}</td>
     *       <td>e.g. {@code http-nio-8080-exec-3}</td></tr>
     *   <tr><td>url</td>
     *       <td>{@code request.getRequestURI()}</td><td></td></tr>
     *   <tr><td>httpMethod</td>
     *       <td>{@code request.getMethod()}</td><td></td></tr>
     *   <tr><td>timestamp</td>
     *       <td>{@link TimestampUtils#getCurrentTimestamp()}</td>
     *       <td>e.g. "23/3/2026, 2:54:43 pm"</td></tr>
     *   <tr><td>headers</td>
     *       <td>All non-blank headers as JSON</td>
     *       <td>Only when {@link ApiRequestLoggingProperties#isLogHeaders()} is true</td></tr>
     *   <tr><td>queryParams</td>
     *       <td>{@code request.getQueryString()}</td>
     *       <td>Omitted when URL has no query string</td></tr>
     * </table>
     *
     * <h4>requestId resolution order</h4>
     * <ol>
     *   <li>Iterate {@link ApiRequestLoggingProperties#getRequestIdHeaders()} in order</li>
     *   <li>Use the first header whose value is non-blank</li>
     *   <li>Fall back to {@link UUID#randomUUID()} if no header matches</li>
     * </ol>
     *
     * @param request the current {@link HttpServletRequest}; must not be {@code null}
     */
    public void addRequestMeta(HttpServletRequest request) {

        // ── Resolve correlation / idempotency ID from configured headers ──
        //
        // api.request.logging.request-id-headers=X-Request-ID,request_id,X-Correlation-ID
        //
        // The first non-blank value from the ordered list wins.
        // "X-Request-ID" is the de-facto standard (AWS API Gateway, NGINX, Postman).
        // Common alternatives: request_id, X-Correlation-ID, X-B3-TraceId, traceparent.
        //
        List<String> headerNames = properties.getRequestIdHeaders();
        String incomingId = null;
        for (String headerName : headerNames) {
            String value = request.getHeader(headerName);
            if (!isBlank(value)) {
                incomingId = value.trim();
                break;
            }
        }
        this.requestId = (incomingId != null) ? incomingId : UUID.randomUUID().toString();

        // Store on the request attribute map so any controller or @Service can
        // read it without injecting RequestLogCollector:
        //   String id = (String) request.getAttribute("requestId");
        request.setAttribute("requestId", this.requestId);

        // Store in ThreadLocal for zero-injection access from anywhere on this thread:
        //   String id = RequestLogCollector.currentRequestId();
        CURRENT_REQUEST_ID.set(this.requestId);

        // ── Log all fields ────────────────────────────────────────────────
        addLog(INCOMING_KEY, "requestId",  this.requestId);
        addLog(INCOMING_KEY, "threadName", Thread.currentThread().getName());
        addLog(INCOMING_KEY, "url",        request.getRequestURI());
        addLog(INCOMING_KEY, "httpMethod", request.getMethod());
        addLog(INCOMING_KEY, "timestamp",  TimestampUtils.getCurrentTimestamp());

        if (properties.isLogHeaders()) {
            addLog(INCOMING_KEY, "headers", headersAsJson(request));
        }

        // getQueryString() returns null when no "?" is in the URL.
        // Guard prevents "queryParams: null" polluting POST request logs.
        if (request.getQueryString() != null) {
            addLog(INCOMING_KEY, "queryParams", request.getQueryString());
        }
    }

    /**
     * Returns the correlation ID resolved in {@link #addRequestMeta}.
     * Useful to echo back to the caller in a response header or include in
     * an audit record.
     *
     * @return requestId string; {@code null} if {@link #addRequestMeta} was not yet called
     */
    public String getRequestId() {
        return requestId;
    }


    // ═════════════════════════════════════════════════════════════════════
    //  CORE LOGGING API
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Adds or updates one field inside the log entry identified by {@code key}.
     *
     * <p>If the outer key does not yet exist a new inner {@link LinkedHashMap}
     * is created automatically ({@code computeIfAbsent}).  If it already exists
     * the new field is appended to the same inner map, preserving insertion order.</p>
     *
     * <pre>{@code
     * // Typical 3rd-party call pattern:
     * String key = collector.buildRetryKey("PaymentService/charge");
     *
     * collector.addLog(key, "request",  chargeRequestPayload);   // before HTTP call
     * ChargeResponse res = restTemplate.postForObject(...);
     * collector.addLog(key, "response", res);                    // after HTTP call
     * }</pre>
     *
     * @param key      outer map key — typically a service name or endpoint URL
     * @param innerKey field name inside the entry; must not be {@code null}
     * @param value    the value — {@code null}, {@link String}, or any
     *                 Jackson-serialisable object
     */
    public void addLog(String key, String innerKey, Object value) {
        if (key == null || innerKey == null) return;
        try {
            mapLog.computeIfAbsent(key, k -> new LinkedHashMap<>())
                  .put(innerKey, toJson(value));
        } catch (Exception e) {
            System.err.println("[RequestLogCollector] addLog error  key=" + key
                    + "  innerKey=" + innerKey + " : " + e.getMessage());
        }
    }

    /**
     * Convenience shorthand — logs both request and response in one call.
     * Use when both objects are available simultaneously (no retry involved).
     *
     * <p>For retry scenarios use {@link #addLog} individually with
     * {@link #buildRetryKey(String)} so each attempt gets its own map entry.</p>
     *
     * <pre>{@code
     * collector.addRequestResponseLog("InventoryService/check", requestObj, responseObj);
     * }</pre>
     *
     * @param key      outer map key (service name / URL)
     * @param request  outgoing request object (serialised to JSON)
     * @param response incoming response object (serialised to JSON)
     */
    public void addRequestResponseLog(String key, Object request, Object response) {
        if (key == null) return;
        addLog(key, "request",  request);
        addLog(key, "response", response);
    }

    /**
     * Builds a <strong>retry-aware</strong> outer-map key:
     * {@code label + " [HH:mm:ss.SSS]"}.
     *
     * <p>Why timestamp the key?  Without it, every retry overwrites the
     * previous attempt's fields — only the last attempt survives.  A
     * timestamped key creates a separate inner map per attempt:</p>
     *
     * <pre>
     * ── PaymentGateway/charge [14:32:05.001]   ← attempt 1
     *    request:   {"amount":500}
     *    error:     Connection timed out
     *
     * ── PaymentGateway/charge [14:32:07.244]   ← attempt 2  (2 s backoff)
     *    request:   {"amount":500}
     *    response:  {"status":"SUCCESS","txnId":"TXN-99"}
     * </pre>
     *
     * @param label human-readable service / endpoint label
     * @return label with appended timestamp, e.g.
     *         {@code "PaymentGateway/charge [14:32:05.001]"}
     */
    public String buildRetryKey(String label) {
        return label + " [" + LocalTime.now().format(TS_FMT) + "]";
    }

    /**
     * Returns the raw log map for programmatic use (audit DB writes,
     * debug REST endpoints, test assertions, etc.).
     *
     * @return the raw log map; do not mutate the returned map
     */
    public Map<String, Map<String, String>> getLogs() {
        return mapLog;
    }

    /**
     * Prints the complete collected log to {@code System.out}.
     * Called by {@link com.github.yash777.apirequestlogging.filter.ApiLoggingFilter}
     * in its {@code finally} block once the full request/response cycle has
     * completed and all 3rd-party calls are recorded.
     *
     * <h4>Sample output</h4>
     * <pre>
     * =========== Request Logs [req-id: f3a1b2c4-...] ===========
     *
     * ── INCOMING
     *    requestId:            f3a1b2c4-d5e6-7890-abcd-ef1234567890
     *    threadName:           http-nio-8080-exec-3
     *    url:                  /api/orders/create
     *    httpMethod:           POST
     *    timestamp:            23/3/2026, 2:54:43 pm
     *    headers:              {"content-type":"application/json","host":"..."}
     *    requestBody:          {"customerId":"C-101","amount":500}
     *    responseStatus:       200
     *    responseBody:         {"orderId":"ORD-1","status":"CONFIRMED"}
     *    requestProcessedTime: 0h 0m 0s 312ms
     *
     * ── PaymentGateway/charge [14:32:05.001]
     *    request:   {"amount":500,"orderId":"ORD-1"}
     *    response:  {"status":"SUCCESS","txnId":"TXN-99"}
     *
     * ════════════════════════════════════════════════════════════
     * </pre>
     */
    public void printLogs() {
        System.out.println("\n=========== Request Logs [req-id: " + requestId + "] ===========");
        if (mapLog.isEmpty()) {
            System.out.println("  (no entries)");
        } else {
            mapLog.forEach((callKey, details) -> {
                System.out.println("── " + callKey);
                details.forEach((k, v) -> {
                    if (v != null)
                        System.out.println("   " + k + ": " + v.replaceAll("\\s+", " "));
                });
                System.out.println();
            });
        }
        System.out.println("════════════════════════════════════════════════════════\n");
    }


    // ═════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Invoked by Spring when the HTTP request ends and this bean is destroyed.
     *
     * <p><strong>Why {@link ThreadLocal#remove()} is critical:</strong><br>
     * Tomcat uses a thread pool.  The same thread that served request A will
     * later serve request B.  If the ThreadLocal is not removed here, request B
     * will silently read request A's {@code requestId} — a subtle, hard-to-reproduce
     * data-leak bug.</p>
     */
    @PreDestroy
    public void cleanup() {
        CURRENT_REQUEST_ID.remove();
    }


    // ═════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Null-safe blank check.
     *
     * <p>Uses {@link String#trim()} for Java 8 compatibility.
     * {@code trim()} removes ASCII whitespace (≤ U+0020); for most header
     * values this is sufficient.</p>
     *
     * @param s the string to test
     * @return {@code true} if {@code s} is {@code null}, empty, or whitespace-only
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Converts all non-blank request headers to a compact JSON object string.
     *
     * <p>Headers with null or blank values are silently excluded.
     * Output example:</p>
     * <pre>{"content-type":"application/json","host":"localhost:8080","x-request-id":"abc-123"}</pre>
     *
     * @param request the current {@link HttpServletRequest}
     * @return JSON object string, or {@code "{}"} on failure
     */
    private static String headersAsJson(HttpServletRequest request) {
        if (request == null) return "{}";
        Map<String, String> map = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            for (String name : Collections.list(names)) {
                String value = request.getHeader(name);
                if (!isBlank(value)) {
                    map.put(name, value.trim());
                }
            }
        }
        try   { return MAPPER.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }

    /**
     * Serialises a value for storage in {@link #mapLog}.
     * <ul>
     *   <li>{@code null}   → stored as {@code null} (field skipped in printLogs)</li>
     *   <li>{@link String} → stored as-is (prevents double JSON-encoding)</li>
     *   <li>any other type → Jackson {@code writeValueAsString}; {@code toString()} fallback</li>
     * </ul>
     *
     * @param value any value
     * @return JSON string representation, or {@code null}
     */
    private static String toJson(Object value) {
        if (value == null)           return null;
        if (value instanceof String) return (String) value;
        try   { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { return value.toString(); }
    }
}
