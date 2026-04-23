package com.github.yash777.apirequestlogging.collector;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * <h2>RequestLogCollectorApi</h2>
 *
 * <p>Public contract for the request-scoped log accumulator.
 * Implemented by {@link RequestLogCollector}.</p>
 *
 * <h3>Why this interface exists — fixing the DevTools {@code LinkageError}</h3>
 *
 * <p>{@link RequestLogCollector} is a request-scoped bean that must be injected
 * into singleton beans (filters, services, interceptors).  Spring bridges the
 * scope gap by wrapping it in a proxy.  There are two proxy strategies:</p>
 *
 * <table border="1" cellpadding="6">
 *   <caption>Proxy mode comparison</caption>
 *   <tr>
 *     <th>Strategy</th>
 *     <th>Annotation</th>
 *     <th>How it works</th>
 *     <th>DevTools restart (Java 17+)</th>
 *   </tr>
 *   <tr>
 *     <td><strong>CGLIB subclass</strong> (old)</td>
 *     <td>{@code proxyMode = ScopedProxyMode.TARGET_CLASS}</td>
 *     <td>Spring generates a bytecode subclass of the concrete class at runtime</td>
 *     <td>
 *       💥 {@code LinkageError: duplicate class definition}<br>
 *       The generated class is tied to the base (permanent) classloader.
 *       On restart DevTools cannot redefine it — Java 17 enforces strict
 *       duplicate-class checking via {@code ClassLoader.defineClass()}.
 *     </td>
 *   </tr>
 *   <tr>
 *     <td><strong>JDK interface proxy</strong> (new)</td>
 *     <td>{@code proxyMode = ScopedProxyMode.INTERFACES}</td>
 *     <td>Spring generates a standard {@code java.lang.reflect.Proxy}
 *         backed by this interface</td>
 *     <td>
 *       ✅ No CGLIB subclass is generated.<br>
 *       JDK proxies are created fresh each time — no classloader conflict.
 *     </td>
 *   </tr>
 * </table>
 *
 * <h3>Root cause — IDE + Java version interaction</h3>
 * <pre>
 * IntelliJ + Java 17
 *   ├─ Starter loaded as JAR from ~/.m2  → base "app" classloader (permanent)
 *   ├─ CGLIB proxy generated into the base classloader
 *   ├─ DevTools restart recreates child RestartClassLoader
 *   ├─ Spring tries to redefine the CGLIB class in the SAME base classloader
 *   └─ Java 17 strict enforcement → 💥 LinkageError: duplicate class definition
 *
 * Eclipse + Java 8
 *   ├─ m2e workspace resolution loads starter via target/classes
 *   ├─ CGLIB proxy generated into RestartClassLoader (child, discarded on restart)
 *   ├─ Java 8 used Unsafe.defineClass() — lenient, no duplicate check enforced
 *   └─ ✅ No crash (either classloader or Java leniency saves it)
 * </pre>
 *
 * <h3>Migration — what to change</h3>
 *
 * <p><strong>1. {@link RequestLogCollector} — change {@code proxyMode}:</strong></p>
 * <pre>{@code
 * // BEFORE (CGLIB — breaks on DevTools restart with Java 17+):
 * @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
 *
 * // AFTER (JDK interface proxy — safe in all environments):
 * @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.INTERFACES)
 * public class RequestLogCollector implements RequestLogCollectorApi { ... }
 * }</pre>
 *
 * <p><strong>2. Injection sites — change the declared type:</strong></p>
 * <pre>{@code
 * // BEFORE:
 * private final RequestLogCollector collector;
 *
 * // AFTER:
 * private final RequestLogCollectorApi collector;
 * }</pre>
 *
 * <p>This is a <strong>non-breaking</strong> change for consumers who already inject
 * {@code RequestLogCollector} by concrete type — Spring will still resolve the bean
 * because {@code RequestLogCollector} implements this interface.  Consumers who
 * switch to the interface type gain compile-time verification that they only use
 * the public API, not internal helpers.</p>
 *
 * <h3>ThreadLocal ambient access — static helper not in this interface</h3>
 *
 * <p>The static method {@link RequestLogCollector#currentRequestId()} reads the
 * per-thread request ID from a {@link ThreadLocal} stored on the concrete class.
 * Static methods cannot participate in interface polymorphism, so it is intentionally
 * kept on {@link RequestLogCollector} and must be called by its concrete class name:</p>
 *
 * <pre>{@code
 * // Access the current requestId without injecting anything:
 * String id = RequestLogCollector.currentRequestId();
 *
 * // Typical MDC usage (Logback / Log4j2):
 * MDC.put("requestId", RequestLogCollector.currentRequestId());
 * }</pre>
 *
 * <h3>Typical consumer pattern — {@code @Service} with try/catch/finally</h3>
 * <pre>{@code
 * @Service
 * public class PaymentService {
 *
 *     // Inject the interface — JDK proxy, safe in any singleton
 *     private final RequestLogCollectorApi collector;
 *
 *     public PaymentService(RequestLogCollectorApi collector) {
 *         this.collector = collector;
 *     }
 *
 *     public PaymentResponse charge(PaymentRequest request) {
 *     
 *         String key = collector.buildRetryKey("PaymentGateway/charge");
 *         collector.addLog(key, RequestLogCollectorApi.LOG_REQUEST, request);
 *
 *         PaymentResponse res = null;
 *         try {
 *             res = gateway.post(request);
 *         } catch (Exception e) {
 *             collector.addLog(key, RequestLogCollectorApi.LOG_EXCEPTION, e);
 *         } finally {
 *             collector.addLog(key, RequestLogCollectorApi.LOG_RESPONSE, res);
 *         }
 *         return res;
 *     }
 * }
 * }</pre>
 *
 * @author Yash
 * @since 1.2.0
 * @see RequestLogCollector
 */
public interface RequestLogCollectorApi {

    // ═════════════════════════════════════════════════════════════════════
    //  OUTER-MAP KEY CONSTANTS
    //  These are the top-level keys used in the structured log map.
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Outer-map key used for the main incoming-request block.
     *
     * <p>All fields captured by {@link #addRequestMeta(HttpServletRequest)} and
     * by {@code ApiLoggingFilter}'s finally block (response status, body, timing)
     * are stored under this key.</p>
     *
     * <p>Log output example:</p>
     * <pre>
     * ── INCOMING
     *    requestId:             req-001
     *    threadName:            http-nio-8080-exec-3
     *    url:                   /api/orders ➤ ContextPath[] — ServletPath[/api/orders]
     *    httpMethod:            POST
     *    timestamp:             22/4/2026, 10:32:15 am
     *    headers:               {"content-type":"application/json"}
     *    responseStatus:        200
     *    response:              {"orderId":"ORD-001","status":"CONFIRMED"}
     *    requestProcessedTime:  0h 0m 0s 87ms
     * </pre>
     */
    String INCOMING_KEY = "INCOMING";

    // ═════════════════════════════════════════════════════════════════════
    //  INNER-KEY CONSTANTS
    //  Pass these as the {@code innerKey} argument to {@link #addLog}.
    //  Using constants prevents typos and makes log-grepping reliable.
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Standard inner-key for the outgoing request payload of a third-party call.
     *
     * <p>Use this constant as the {@code innerKey} in
     * {@link #addLog(String, String, Object)} to record what was sent:</p>
     * <pre>{@code
     * collector.addLog(key, RequestLogCollectorApi.LOG_REQUEST, paymentRequest);
     * }</pre>
     *
     * <p>Produces the log line:</p>
     * <pre>
     *    request:  {"orderId":"ORD-1","amount":500.0}
     * </pre>
     */
    String LOG_REQUEST = "request";

    /**
     * Standard inner-key for the response payload of a successful (2xx/3xx) call.
     * 
     * <p>Written to the {@link #INCOMING_KEY} block after the filter chain
     * completes and the {@link org.springframework.web.util.ContentCachingResponseWrapper}
     * buffer has been read — before {@code copyBodyToResponse()} flushes it to
     * the socket.</p>
     * 
     * <p>Use in the {@code finally} block so it is always recorded, even when the
     * call threw an exception (in which case the value will be {@code null}):</p>
     * <pre>{@code
     * } finally {
     *     collector.addLog(key, RequestLogCollectorApi.LOG_RESPONSE, res);  // null-safe
     * }
     * }</pre>
     *
     * <h3>Log output</h3>
     * <pre>
     * ── INCOMING
     *    responseStatus:  200
     *    response:        {"orderId":"ORD-001","status":"CONFIRMED"}
     * </pre>
     *
     * <p>Populated only when
     * {@link com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties#isLogResponseBody()}
     * is {@code true} (the default).</p>
     *
     * @see com.github.yash777.apirequestlogging.util.RequestResponseCaptureUtil#captureResponseBody
     */
    String LOG_RESPONSE = "response";

    /**
     * Inner-key for the HTTP error message on 4xx/5xx or short-circuited responses.
     *
     * <p>Written to the {@link #INCOMING_KEY} block in two situations:</p>
     * <ol>
     *   <li>A consumer filter called {@code response.sendError(status, message)}
     *       and returned early — the chain was never invoked.</li>
     *   <li>The chain ran but the controller / {@code @ExceptionHandler} returned
     *       a 4xx or 5xx response without a body (e.g. {@code 401 Unauthorized}
     *       from a security filter).</li>
     * </ol>
     *
     * <p>Log output example:</p>
     * <pre>
     * ── INCOMING
     *    responseStatus:  401
     *    responseError:   Language is Required
     * </pre>
     *
     * <p>When the error response does carry a body (e.g. a JSON error document from
     * {@code @ExceptionHandler}), {@link #LOG_RESPONSE} is used instead and this
     * key is omitted.</p>
     * 
     * @see com.github.yash777.apirequestlogging.util.RequestResponseCaptureUtil#captureResponseBody
     */
    String LOG_RESPONSE_ERROR = "responseError";

    /**
     * Standard inner-key for a caught {@link Throwable} from a third-party call.
     *
     * <p>When this key is used and the {@code value} passed to
     * {@link #addLog(String, String, Object)} is a {@link Throwable}, the
     * implementation automatically captures the full stack trace and truncates it
     * to the first 5 lines (configurable via
     * {@code api.request.logging.exception.max-lines}) to keep logs readable.</p>
     *
     * <p>Always use inside a {@code catch} block:</p>
     * <pre>{@code
     * try {
     *     res = gateway.post(request);
     * } catch (Exception e) {
     *     collector.addLog(key, RequestLogCollectorApi.LOG_EXCEPTION, e);
     * }
     * }</pre>
     *
     * <p>Produces (truncated):</p>
     * <pre>
     *    exceptionStacktrace:  java.net.ConnectException: Connection refused
     *                            at sun.nio.ch.SocketChannelImpl.checkConnect(...)
     *                            at sun.nio.ch.SocketChannelImpl.finishConnect(...)
     *                            at org.apache.http.impl.nio.reactor...
     *                            ... (truncated to 5 lines)
     * </pre>
     */
    String LOG_EXCEPTION = "exceptionStacktrace";

    /**
     * Inner-key for the short error indicator added to the {@link #INCOMING_KEY} block.
     *
     * <p>Format: {@code "ERROR:ExceptionSimpleName"},
     * e.g. {@code "ERROR:NullPointerException"}.</p>
     *
     * <p>Written automatically by {@code ApiLoggingFilter} when an unchecked
     * exception escapes the controller.  Consumer code rarely needs to use this
     * key directly.</p>
     */
    String LOG_ERROR_INDICATOR = "errorIndicator";

    /**
     * Inner-key for the AOP-captured controller handler name.
     *
     * <p>Format: {@code "ControllerClass#methodName"},
     * e.g. {@code "UserController#listUsers"}.</p>
     *
     * <p>Written automatically by {@code ControllerHandlerAspect} when
     * {@code spring-boot-starter-aop} is on the classpath and
     * {@code api.request.logging.aop.controller-handler-enabled=true}.</p>
     */
    String LOG_CONTROLLER_HANDLER = "controllerHandler";

    /**
     * Inner-key written when a RestTemplate call is intentionally skipped because
     * its URL matched the configured skip list
     * ({@code api.request.logging.rest-template.skip.urls}).
     *
     * <p>The HTTP call is still executed normally — only the
     * request/response logging is suppressed.  Use this for credential-bearing
     * endpoints such as OAuth2 token URLs that must never appear in logs.</p>
     *
     * <p>Log output example:</p>
     * <pre>
     * ── https://auth-server/oauth/token [14:32:05.001]
     *    skipped: request/response logging skipped — URL matched skip list
     * </pre>
     */
    String LOG_SKIPPED = "skipped";

    // ═════════════════════════════════════════════════════════════════════
    //  REQUEST METADATA
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Captures all metadata for the incoming HTTP request and stores it under
     * {@link #INCOMING_KEY}.
     *
     * <p>Called by {@code ApiLoggingFilter} <em>before</em>
     * {@code chain.doFilter()} so every field is always present even when the
     * controller throws an unchecked exception.</p>
     *
     * <h4>Fields captured</h4>
     * <table border="1" cellpadding="4">
     *   <caption>Captured request fields</caption>
     *   <tr><th>Field</th><th>Source</th><th>Notes</th></tr>
     *   <tr>
     *     <td>{@code requestId}</td>
     *     <td>First non-blank match from
     *         {@code api.request.logging.request-id-headers}, or random UUID</td>
     *     <td>Also stored as a request attribute and in a {@link ThreadLocal}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code threadName}</td>
     *     <td>{@code Thread.currentThread().getName()}</td>
     *     <td>e.g. {@code http-nio-8080-exec-3}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code url}</td>
     *     <td>{@code request.getRequestURI()}</td>
     *     <td>Includes context path and servlet path</td>
     *   </tr>
     *   <tr>
     *     <td>{@code httpMethod}</td>
     *     <td>{@code request.getMethod()}</td>
     *     <td>e.g. {@code GET}, {@code POST}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code timestamp}</td>
     *     <td>{@code TimestampUtils.getCurrentTimestamp()}</td>
     *     <td>e.g. {@code "22/4/2026, 10:32:15 am"}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code headers}</td>
     *     <td>All non-blank headers serialised to JSON</td>
     *     <td>Only when {@code api.request.logging.log-headers=true}</td>
     *   </tr>
     *   <tr>
     *     <td>{@code queryParams}</td>
     *     <td>{@code request.getQueryString()}</td>
     *     <td>Omitted entirely when the URL has no query string</td>
     *   </tr>
     * </table>
     *
     * <h4>requestId resolution order</h4>
     * <ol>
     *   <li>Iterate {@code api.request.logging.request-id-headers} in order</li>
     *   <li>Use the first header whose value is non-blank</li>
     *   <li>Fall back to {@link java.util.UUID#randomUUID()} if no header matches</li>
     * </ol>
     *
     * @param request the current {@link HttpServletRequest}; must not be {@code null}
     */
    void addRequestMeta(HttpServletRequest request);

    /**
     * Returns the correlation/idempotency ID resolved during
     * {@link #addRequestMeta(HttpServletRequest)}.
     *
     * <p>Useful to echo back to the caller in a response header or include in
     * an audit record:</p>
     * <pre>{@code
     * response.setHeader("X-Request-ID", collector.getRequestId());
     * }</pre>
     *
     * @return the requestId string; {@code null} if
     *         {@link #addRequestMeta(HttpServletRequest)} has not yet been called
     *         for the current request
     */
    String getRequestId();

    // ═════════════════════════════════════════════════════════════════════
    //  CORE LOGGING API
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Adds or updates one field inside the log entry identified by {@code key}.
     *
     * <p>If the outer key does not yet exist a new inner map is created
     * automatically.  If it already exists the new field is appended to the same
     * inner map, preserving insertion order — so fields appear in the log in the
     * order they were added.</p>
     *
     * <h4>Value serialisation</h4>
     * <ul>
     *   <li>{@code null} — stored and printed as {@code null}</li>
     *   <li>{@link String} — stored as-is</li>
     *   <li>{@link Throwable} with {@link #LOG_EXCEPTION} key — stack trace
     *       captured and truncated to the first 5 lines
     *       (configurable via {@code api.request.logging.exception.max-lines})</li>
     *   <li>Any other object — serialised to compact JSON via Jackson
     *       ({@code ObjectMapper})</li>
     * </ul>
     *
     * <h4>Secret masking</h4>
     * <p>When {@code api.request.logging.mask.enabled=true} the serialised value
     * is passed through {@code SecretMaskingUtil} before storage, replacing any
     * configured sensitive field values with the configured replacement string
     * (default {@code "***"}).</p>
     *
     * <h4>Recommended try/catch/finally pattern</h4>
     * <pre>{@code
     * String key = collector.buildRetryKey("PaymentGateway/charge");
     *
     * collector.addLog(key, RequestLogCollectorApi.LOG_REQUEST, request);   // before call
     *
     * PaymentResponse res = null;
     * try {
     *     res = gateway.post(request);                                       // actual HTTP call
     * } catch (Exception e) {
     *     collector.addLog(key, RequestLogCollectorApi.LOG_EXCEPTION, e);    // auto-truncated
     * } finally {
     *     collector.addLog(key, RequestLogCollectorApi.LOG_RESPONSE, res);   // null if threw
     * }
     * }</pre>
     *
     * @param key      outer map key — typically a service name or endpoint URL;
     *                 use {@link #buildRetryKey(String)} for retry-aware keys;
     *                 use {@link #INCOMING_KEY} for fields in the main request block;
     *                 {@code null} is silently ignored
     * @param innerKey field name inside the entry; use the {@code LOG_*} constants
     *                 for standard fields to avoid typos; must not be {@code null}
     * @param value    the value to store — accepts {@code null}, {@link String},
     *                 {@link Throwable} (with {@link #LOG_EXCEPTION} key), or any
     *                 Jackson-serialisable object
     */
    void addLog(String key, String innerKey, Object value);

    /**
     * Convenience shorthand — records both request and response in one call.
     *
     * <p>Use when both objects are available simultaneously and no retry is
     * involved.  For retry scenarios, call {@link #addLog} individually with
     * {@link #buildRetryKey(String)} so each attempt gets its own timestamped
     * outer-map entry.</p>
     *
     * <p>Equivalent to:</p>
     * <pre>{@code
     * collector.addLog(key, RequestLogCollectorApi.LOG_REQUEST,  request);
     * collector.addLog(key, RequestLogCollectorApi.LOG_RESPONSE, response);
     * }</pre>
     *
     * <h4>Example</h4>
     * <pre>{@code
     * collector.addRequestResponseLog("InventoryService/check", requestObj, responseObj);
     * }</pre>
     *
     * <h4>Log output</h4>
     * <pre>
     * ── InventoryService/check
     *    request:   {"itemName":"Laptop","qty":1}
     *    response:  {"reserved":true}
     * </pre>
     *
     * @param key      outer map key (service name or endpoint URL);
     *                 {@code null} is silently ignored
     * @param request  outgoing request object (serialised to JSON)
     * @param response incoming response object (serialised to JSON)
     */
    void addRequestResponseLog(String key, Object request, Object response);

    /**
     * Builds a <strong>retry-aware</strong> outer-map key by appending a
     * millisecond-precision timestamp to the given label:
     * {@code label + " [HH:mm:ss.SSS]"}.
     *
     * <h4>Why timestamp the key?</h4>
     * <p>Without a timestamp, every retry attempt writes into the same inner map
     * — later attempts overwrite earlier ones and only the last attempt survives
     * in the log.  A timestamped key creates a separate inner map per attempt:</p>
     * <pre>
     * ── PaymentGateway/charge [14:32:05.001]   ← attempt 1 (failed)
     *    request:    {"amount":500}
     *    exception:  java.net.ConnectException: Connection refused
     *    response:   null
     *
     * ── PaymentGateway/charge [14:32:07.244]   ← attempt 2 (succeeded, 2 s backoff)
     *    request:    {"amount":500}
     *    response:   {"status":"SUCCESS","txnId":"TXN-99"}
     * </pre>
     *
     * <h4>Usage</h4>
     * <pre>{@code
     * String key = collector.buildRetryKey("PaymentGateway/charge");
     * // key => "PaymentGateway/charge [14:32:05.001]"
     * }</pre>
     *
     * @param label human-readable service or endpoint name;
     *              e.g. {@code "PaymentGateway/charge"} or
     *              {@code "https://api.example.com/v1/orders"}
     * @return label with an appended timestamp,
     *         e.g. {@code "PaymentGateway/charge [14:32:05.001]"}
     */
    String buildRetryKey(String label);

    /**
     * Returns the raw structured log map for this request.
     *
     * <p>The outer key is either {@link #INCOMING_KEY} (the main request block)
     * or a timestamped service label built by {@link #buildRetryKey(String)}.
     * The inner key is a field name ({@code "request"}, {@code "response"}, etc.)
     * and the value is the JSON-serialised, masked string.</p>
     *
     * <p>Typical use cases:</p>
     * <ul>
     *   <li>Persisting the full log map to an audit database</li>
     *   <li>Exposing it from a debug REST endpoint</li>
     *   <li>Asserting map contents in unit/integration tests</li>
     * </ul>
     *
     * <p><strong>Do not mutate the returned map.</strong>  The map is the live
     * internal state of this bean — mutations will corrupt the log output.</p>
     *
     * @return the live log map; never {@code null}, but may be empty if no
     *         logging methods have been called yet
     */
    Map<String, Map<String, String>> getLogs();

    // ═════════════════════════════════════════════════════════════════════
    //  OUTPUT
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Formats and emits the complete request log for the current HTTP request.
     *
     * <p>Called by {@code ApiLoggingFilter} in its {@code finally} block after
     * all fields — request metadata, response status and body, third-party call
     * logs, and request-processing time — have been collected.</p>
     *
     * <h4>Output routing</h4>
     * <ul>
     *   <li><strong>SLF4J at INFO level</strong> — always active when
     *       {@code api.request.logging.logger.enabled=true} (the default).
     *       Logger name is controlled by
     *       {@code api.request.logging.logger.name}.</li>
     *   <li><strong>{@code System.out}</strong> — opt-in legacy output via
     *       {@code api.request.logging.logger.sysout-enabled=true}.</li>
     * </ul>
     *
     * <h4>Sample output</h4>
     * <pre>
     * =========== Request Logs [req-id: req-001] ===========
     *
     * ── INCOMING
     *    requestId:             req-001
     *    url:                   /api/orders ➤ ContextPath[] — ServletPath[/api/orders]
     *    httpMethod:            POST
     *    responseStatus:        200
     *    response:              {"orderId":"ORD-001","status":"CONFIRMED"}
     *    requestProcessedTime:  0h 0m 0s 87ms
     *
     * ── PaymentGateway/charge [14:32:05.042]
     *    request:   {"orderId":"ORD-001","amount":999.99}
     *    response:  {"txnId":"TXN-xyz","status":"SUCCESS"}
     *
     * ════════════════════════════════════════════════════════
     * </pre>
     *
     * <p>Consumer code should not normally call this method directly — it is
     * invoked automatically by the filter chain.</p>
     */
    void printLogs();
}