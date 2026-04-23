package com.github.yash777.apirequestlogging.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.yash777.apirequestlogging.masking.SecretMaskingUtil;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;
import com.github.yash777.apirequestlogging.util.TimestampUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
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
 * <h3>Using from your services — recommended pattern with try/catch/finally</h3>
 * <p>Always log inside a {@code try/catch/finally} block so that the response
 * (or {@code null} on failure) is always recorded even when the gateway throws:</p>
 * <pre>{@code
 * @Service
 * public class PaymentService {
 *
 *     @Autowired
 *     private RequestLogCollector collector;   // CGLIB proxy — safe in singleton
 *
 *     public PaymentResponse charge(PaymentRequest request) {
 *         // buildRetryKey stamps the current time → each retry gets its own entry
 *         String key = collector.buildRetryKey("PaymentGateway/charge");
 *
 *         collector.addLog(key, RequestLogCollector.LOG_REQUEST,  request);  // before call
 *
 *         PaymentResponse res = null;
 *         try {
 *             res = gateway.post(request);                                    // actual HTTP call
 *         } catch (Exception e) {
 *             // Pass the Throwable directly — addLog() automatically truncates
 *             // the stack trace to the first 5 lines so logs stay readable.
 *             collector.addLog(key, RequestLogCollector.LOG_EXCEPTION, e);
 *         } finally {
 *             collector.addLog(key, RequestLogCollector.LOG_RESPONSE, res);  // null-safe
 *         }
 *         return res;
 *     }
 * }
 * }</pre>
 *
 * <h4>Sample console output for the pattern above (success path)</h4>
 * <pre>
 * ── PaymentGateway/charge [14:32:05.001]
 *    request:   {"orderId":"ORD-1","amount":500.0,"currency":"INR"}
 *    response:  {"txnId":"TXN-99","status":"SUCCESS","orderId":"ORD-1","amount":500.0}
 * </pre>
 *
 * <h4>Sample console output (failure path)</h4>
 * <pre>
 * ── PaymentGateway/charge [14:32:05.001]
 *    request:    {"orderId":"ORD-1","amount":500.0,"currency":"INR"}
 *    exception:  java.net.ConnectException: Connection refused
 *                  at sun.nio.ch.SocketChannelImpl.checkConnect(SocketChannelImpl.java:...)
 *                  at sun.nio.ch.SocketChannelImpl.finishConnect(SocketChannelImpl.java:...)
 *                  at org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor...
 *                  at org.apache.http.impl.nio.reactor.AbstractIOReactor.execute(...)
 *                  at org.apache.http.impl.nio.reactor.BaseIOReactor.execute(...)
 *                  ... (truncated to 5 lines)
 *    response:   null
 * </pre>
 *
 * <h3>v1.1.0 changes</h3>
 * <ul>
 *   <li>Log output routed to SLF4J logger ({@code api.request.logging}) at INFO level.
 *       {@code System.out} is retained as an opt-in fallback via property
 *       {@code api.request.logging.logger.sysout-enabled=true}.</li>
 *   <li>Secret masking applied when {@code api.request.logging.mask.enabled=true}.</li>
 *   <li>New inner-key constants: {@link #LOG_REQUEST}, {@link #LOG_RESPONSE},
 *       {@link #LOG_EXCEPTION}, {@link #LOG_ERROR_INDICATOR},
 *       {@link #LOG_CONTROLLER_HANDLER}.</li>
 *   <li>Exception stack traces truncated to
 *       {@code api.request.logging.exception.max-lines} (default 5).</li>
 * </ul>
 * 
 * @author Yash
 * @since 1.0.0 (SLF4J + masking added in 1.1.0)
 * @see com.github.yash777.apirequestlogging.filter.ApiLoggingFilter
 * @see com.github.yash777.apirequestlogging.filter.RequestBodyCachingFilter
 */
@Component
@Scope(
    value     = WebApplicationContext.SCOPE_REQUEST,
    proxyMode = ScopedProxyMode.INTERFACES   // mandatory — lets singletons hold a proxy reference ← was TARGET_CLASS
)
@ConditionalOnProperty(
    prefix = "api.request.logging",
    name   = "enabled",
    havingValue = "true"
)
public class RequestLogCollector implements RequestLogCollectorApi {

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

    /**
     * Maximum number of stack-trace lines captured when an {@link Exception} is
     * passed to {@link #addLog} with the inner-key {@link #LOG_EXCEPTION}.
     *
     * <p>Full stack traces from frameworks like Spring, Hibernate, or Apache HttpClient
     * can easily exceed 60–100 lines, flooding the log with noise.  Keeping the first
     * {@value} lines is enough to identify the root-cause exception class, message,
     * and the first few frames of the call stack — the frames most relevant to
     * application code.</p>
     *
     * <p>Configurable via the property {@code api.request.logging.exception.max-lines}.
     * Set to a smaller value (e.g. {@code 3}) for very compact logs, or increase
     * it if your application has deep call chains that need more context.</p>
     */
    private static final int EXCEPTION_MAX_LINES = 5;

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

    
    // ── Per-instance state ────────────────────────────────────────────────
    //
    // mapLog  — NOT static. Each @RequestScope instance has its own map.
    //           Outer key: INCOMING_KEY or "ServiceName [HH:mm:ss.SSS]"
    //           Inner key: field name   ("request", "response", "exception", …)
    //           LinkedHashMap preserves insertion order → chronological output.
    //
    private final Map<String, Map<String, String>> mapLog = new LinkedHashMap<>();
    private String requestId;

    /** Properties injected at construction — drives header resolution. */
    private final ApiRequestLoggingProperties properties;

    /** SLF4J logger — name is driven by {@code api.request.logging.logger.name}. */
    private Logger log;
    
    /**
     * Constructor injection — Spring resolves and fully constructs the
     * {@link ApiRequestLoggingProperties} bean before calling this constructor.
     *
     * @param properties externalized configuration
     */
    public RequestLogCollector(ApiRequestLoggingProperties properties) {
        this.properties = properties;
        this.log = LoggerFactory.getLogger(properties.getLogger().getName());
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
        addLog(INCOMING_KEY, "timestamp",  TimestampUtils.getCurrentTimestamp());
        String paths = " ➤ ContextPath["+request.getContextPath()+"] — ServletPath["+request.getServletPath()+"]";
        addLog(INCOMING_KEY, "url",        request.getRequestURI() + paths);
        addLog(INCOMING_KEY, "httpMethod", request.getMethod());

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
     * <h4>Exception handling — automatic stack-trace truncation</h4>
     * <p>When {@code innerKey} equals {@link #LOG_EXCEPTION} <strong>and</strong>
     * {@code value} is a {@link Throwable}, the full stack trace is captured via
     * {@link PrintWriter}/{@link StringWriter} and then truncated to the first
     * {@value #EXCEPTION_MAX_LINES} lines before storage.  A
     * {@code "... (truncated to N lines)"} suffix is appended so the log clearly
     * indicates that more frames were omitted.</p>
     *
     * <p>This prevents framework stack traces (Spring, Hibernate, Apache HttpClient)
     * from producing 60–100 line log entries for a single failed call.</p>
     *
     * <h4>Recommended usage pattern (try/catch/finally)</h4>
     * <pre>{@code
     * String key = collector.buildRetryKey("PaymentGateway/charge");
     *
     * collector.addLog(key, RequestLogCollector.LOG_REQUEST, request);   // before call
     *
     * PaymentResponse res = null;
     * try {
     *     res = gateway.post(request);
     * } catch (Exception e) {
     *     collector.addLog(key, RequestLogCollector.LOG_EXCEPTION, e);   // truncated trace
     * } finally {
     *     collector.addLog(key, RequestLogCollector.LOG_RESPONSE, res);  // null if threw
     * }
     * }</pre>
     *
     * <h4>Inner-key constants</h4>
     * <p>Prefer the typed constants over raw string literals to avoid typos:</p>
     * <table border="1" cellpadding="4">
     *   <tr><th>Constant</th><th>Value</th><th>When to use</th></tr>
     *   <tr>
     *     <td>{@link #LOG_REQUEST}</td><td>{@code "request"}</td>
     *     <td>Outgoing request payload — log before the HTTP call</td>
     *   </tr>
     *   <tr>
     *     <td>{@link #LOG_RESPONSE}</td><td>{@code "response"}</td>
     *     <td>Incoming response payload — log in {@code finally} block</td>
     *   </tr>
     *   <tr>
     *     <td>{@link #LOG_EXCEPTION}</td><td>{@code "exception"}</td>
     *     <td>Caught {@link Throwable} — log in {@code catch} block;
     *         stack trace auto-truncated to {@value #EXCEPTION_MAX_LINES} lines</td>
     *   </tr>
     * </table>
     *
     * @param key      outer map key — typically a service name or endpoint URL;
     *                 use {@link #buildRetryKey(String)} to get a timestamped key
     * @param innerKey field name inside the entry; use {@link #LOG_REQUEST},
     *                 {@link #LOG_RESPONSE}, or {@link #LOG_EXCEPTION} for standard
     *                 fields; must not be {@code null}
     * @param value    the value to log — accepts {@code null} (stored as {@code null}
     *                 and printed explicitly), {@link String} (stored as-is),
     *                 {@link Throwable} with {@link #LOG_EXCEPTION} key (stack trace
     *                 truncated to {@value #EXCEPTION_MAX_LINES} lines), or any
     *                 Jackson-serialisable object (converted to compact JSON)
     */
    public void addLog(String key, String innerKey, Object value) {
        if (key == null || innerKey == null) return;
        try {
            // ── Exception handling — truncate stack trace ─────────────────
            // When the caller uses the LOG_EXCEPTION inner-key and passes a
            // Throwable, capture the full stack trace as a string and keep
            // only the first EXCEPTION_MAX_LINES lines.
            //
            // Why not store the full trace?
            //   A typical Spring + HttpClient stack trace can be 80–120 lines.
            //   Multiplied by concurrent requests this floods System.out and
            //   makes the structured log block unreadable.
            //   The first 5 lines always contain: exception class + message +
            //   the application frames closest to the throw site — exactly
            //   what is needed to diagnose the failure.
            //
            // Why read by innerKey and not instanceof Throwable?
            //   The caller may intentionally pass a Throwable as a "response"
            //   field for diagnostic purposes.  Checking the key makes the
            //   truncation opt-in and explicit.
            if (LOG_EXCEPTION.equals(innerKey) && value instanceof Throwable) {
                value = truncateStackTrace((Throwable) value, EXCEPTION_MAX_LINES);
            }

            String serialised = toJson(value);

            // Apply secret masking to string values
            if (properties.getMask().isEnabled() && serialised != null) {
                serialised = SecretMaskingUtil.mask(
                        serialised,
                        properties.getMask().getFields(),
                        properties.getMask().getReplacement());
            }
            
            mapLog.computeIfAbsent(key, k -> new LinkedHashMap<>())
                  .put(innerKey, serialised);
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
        addLog(key, LOG_REQUEST,  request);
        addLog(key, LOG_RESPONSE, response);
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
     *    request:    {"amount":500}
     *    exception:  java.net.ConnectException: Connection refused
     *                  at sun.nio.ch.SocketChannelImpl.checkConnect(...)
     *                  ... (truncated to 5 lines)
     *    response:   null
     *
     * ── PaymentGateway/charge [14:32:07.244]   ← attempt 2  (2 s backoff)
     *    request:    {"amount":500}
     *    response:   {"status":"SUCCESS","txnId":"TXN-99"}
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

    // ══════════════════════════════════════════════════════════════════
    //  OUTPUT
    // ══════════════════════════════════════════════════════════════════

    /**
     * Prints the complete request log. Called by {@code ApiLoggingFilter} in its
     * {@code finally} block after all fields (body, timing, 3rd-party calls) are collected.
     *
     * <p>Output is routed to the SLF4J logger named by
     * {@code api.request.logging.logger.name} at INFO level.
     * Set {@code api.request.logging.logger.sysout-enabled=true} to also
     * print to {@code System.out} (v1.0.x legacy behaviour).</p>
     */
    public void printLogs() {
        String output = buildLogString();
        if (properties.getLogger().isEnabled()) {
            log.info("{}", output);
        }
        if (properties.getLogger().isSysoutEnabled()) {
            System.out.println(output);
        }
    }

    private String buildLogString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=========== Request Logs [req-id: ").append(requestId).append("] ===========");
        if (mapLog.isEmpty()) {
            sb.append("\n  (no entries)");
        } else {
            mapLog.forEach((callKey, details) -> {
                sb.append("\n── ").append(callKey);
                details.forEach((k, v) -> {
                    if (v != null)
                        sb.append("\n   ").append(k).append(": ").append(v.replaceAll("\\s+", " "));
                });
                sb.append("\n");
            });
        }
        sb.append("════════════════════════════════════════════════════════\n");
        return sb.toString();
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
     * Captures the full stack trace of {@code throwable} and returns only
     * the first {@code maxLines} lines, with a truncation notice appended.
     *
     * <h4>Why PrintWriter + StringWriter?</h4>
     * <p>{@link Throwable#printStackTrace(PrintWriter)} is the standard JDK API
     * for writing a full stack trace to a {@link java.io.Writer}.
     * {@link StringWriter} captures those bytes in-memory without any I/O.
     * This is Java 8 compatible — no {@code Stream} or {@code String.lines()}
     * (Java 11+) are needed.</p>
     *
     * <h4>Output format</h4>
     * <pre>
     *   java.net.ConnectException: Connection refused
     *     at sun.nio.ch.SocketChannelImpl.checkConnect(SocketChannelImpl.java:...)
     *     at sun.nio.ch.SocketChannelImpl.finishConnect(SocketChannelImpl.java:...)
     *     at org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor...
     *     at org.apache.http.impl.nio.reactor.AbstractIOReactor.execute(...)
     *     at org.apache.http.impl.nio.reactor.BaseIOReactor.execute(...)
     *     ... (truncated to 5 lines)
     * </pre>
     *
     * @param throwable the exception whose stack trace should be captured;
     *                  must not be {@code null}
     * @param maxLines  maximum number of lines to keep from the full stack trace
     * @return a {@link String} containing at most {@code maxLines} lines of the
     *         stack trace followed by a {@code "... (truncated to N lines)"} suffix
     *         when the trace was cut; the full trace if it fits within the limit
     */
    private static String truncateStackTrace(Throwable throwable, int maxLines) {
        // Capture the full stack trace into a String using PrintWriter/StringWriter.
        // This is Java 8 compatible — Throwable.printStackTrace(PrintWriter) exists
        // since Java 1.1.  We do NOT use String.lines() (Java 11+) or streams.
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String fullTrace = sw.toString();

        // Split on newlines.  Stack trace lines use "\r\n" on Windows and "\n"
        // on Linux/macOS.  The regex handles both.
        String[] lines = fullTrace.split("\\r?\\n");

        if (lines.length <= maxLines) {
            // Trace fits within the limit — return as-is (no truncation notice needed)
            return fullTrace.trim();
        }

        // Keep the first maxLines lines and append a truncation notice so the
        // log reader knows the trace was intentionally cut.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("  ... (truncated to ").append(maxLines).append(" lines)");
        return sb.toString();
    }

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

    // ══════════════════════════════════════════════════════════════════
    //  REQUEST HEADERS CAPTURE
    // ══════════════════════════════════════════════════════════════════
    /**
     * Serialises all non-blank request headers to a compact JSON object,
     * applying two independent filters before the value is written:
     *
     * <ol>
     *   <li><strong>Skip list</strong> ({@code api.request.logging.skip-headers}) —
     *       headers whose name appears in this list are dropped entirely.
     *       They do not appear in the output even as masked values.
     *       Matching is case-insensitive.</li>
     *   <li><strong>Mask list</strong> ({@code api.request.logging.mask.fields}) —
     *       headers whose name appears in this list have their value replaced with
     *       {@link ApiRequestLoggingProperties.MaskProperties#getReplacement()}
     *       (default {@code ***MASKED***}).  The header name is still logged.</li>
     * </ol>
     *
     * <h3>Processing order</h3>
     * <pre>
     * for each header name:
     *   1. skip?  → drop entirely          (skip-headers list)
     *   2. mask?  → replace value          (mask.fields list, when masking enabled)
     *   3. else   → log name + value as-is
     * </pre>
     *
     * <h3>Example</h3>
     * <p>Given headers: {@code content-type, user-agent, authorization, host}
     * with {@code skip-headers=user-agent} and {@code mask.fields=authorization}:</p>
     * <pre>
     * {"content-type":"application/json","authorization":"***MASKED***","host":"localhost:8080"}
     * </pre>
     * <p>{@code user-agent} is absent entirely; {@code authorization} is present
     * but its value is hidden.</p>
     *
     * @param request the current {@link HttpServletRequest}; safe to pass {@code null}
     * @return compact JSON object string, or {@code "{}"} when request is null or
     *         on serialisation failure
     */
    private String headersAsJson(HttpServletRequest request) {
        if (request == null) return "{}";

        ApiRequestLoggingProperties.MaskProperties maskProps = properties.getMask();
        List<String> skipHeaders = properties.getSkipHeaders();   // never null — default emptyList()

        Map<String, String> map = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            for (String name : Collections.list(names)) {

                // ── Filter 1: skip-headers — drop entirely ────────────────
                if (isSkippedHeader(name, skipHeaders)) {
                    continue;
                }

                String value = request.getHeader(name);
                if (isBlank(value)) {
                    continue;   // never log blank values
                }

                // ── Filter 2: mask — replace value ────────────────────────
                if (maskProps.isEnabled()
                        && SecretMaskingUtil.shouldMaskHeader(name, maskProps.getFields())) {
                    map.put(name, maskProps.getReplacement());
                } else {
                    map.put(name, value.trim());
                }
            }
        }

        try   { return MAPPER.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }

    /**
     * Returns {@code true} when {@code headerName} should be omitted from the log
     * because it appears in the configured skip list.
     *
     * <p>Matching is case-insensitive: {@code "User-Agent"}, {@code "user-agent"},
     * and {@code "USER-AGENT"} all match a skip entry of {@code "user-agent"}.</p>
     *
     * @param headerName  the header name to check; may be {@code null}
     * @param skipHeaders the configured skip list; may be {@code null} or empty
     * @return {@code true} if the header should be dropped from the log
     */
    private static boolean isSkippedHeader(String headerName, List<String> skipHeaders) {
        if (headerName == null || skipHeaders == null || skipHeaders.isEmpty()) {
            return false;
        }
        for (String skip : skipHeaders) {
            if (skip.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
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