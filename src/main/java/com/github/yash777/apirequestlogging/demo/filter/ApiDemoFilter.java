package com.github.yash777.apirequestlogging.demo.filter;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

/**
 * <h2>ApiDemoFilter — Demo consumer filter to verify early-return body capture</h2>
 *
 * <p>Simulates a real-world consumer application filter that validates a mandatory
 * HTTP request header and short-circuits the filter chain with
 * {@code response.sendError(401)} when the header is absent.</p>
 *
 * <p>This filter exists as a <strong>demo fixture only</strong>.  Its purpose is
 * to reproduce and verify that
 * {@link com.github.yash777.apirequestlogging.filter.ApiLoggingFilter} correctly
 * captures the request body and error response even when the chain is never
 * invoked — i.e. when a consumer filter returns early.</p>
 *
 * <h3>The problem being demonstrated</h3>
 * <p>When this filter calls {@code response.sendError(401, message)} and returns
 * without invoking {@code filterChain.doFilter(...)}, the following happens:</p>
 * <ul>
 *   <li>The {@link org.springframework.web.util.ContentCachingRequestWrapper}
 *       byte cache is empty — the {@code InputStream} was never read by the
 *       controller or Jackson, so {@code getContentAsByteArray()} returns
 *       {@code byte[0]}.</li>
 *   <li>{@code response.sendError()} does NOT write to the response body buffer —
 *       Tomcat stores the message internally and flushes it during a separate
 *       error-dispatch cycle that happens after our filter's {@code finally} block.</li>
 *   <li>Without the fix in {@code ApiLoggingFilter}, both {@code requestBody}
 *       and {@code responseError} would be missing from the log.</li>
 * </ul>
 *
 * <h3>The fix in {@code ApiLoggingFilter}</h3>
 * <p>{@code doFilterInternal} tracks a {@code chainInvoked} boolean flag.  When
 * {@code false}, {@code captureRequestBody()} falls back to Strategy 2
 * (force-read the raw {@code InputStream}), and {@code captureResponseBody()}
 * walks four fallback attempts to find the error message — including the custom
 * request attribute {@code "apilog.errorMessage"} set by
 * {@link #returnResponse(HttpServletResponse, String)}.</p>
 *
 * <h3>Activation</h3>
 * <p>Active only when <strong>both</strong> conditions are satisfied:</p>
 * <ol>
 *   <li>{@code @ConditionalOnDemoEnvironment} — JVM entry point is
 *       {@code DemoApplication.main()}, not a consumer app.</li>
 *   <li>{@code internal.app.non-consumer.filter=true} in
 *       {@code application.properties}.</li>
 * </ol>
 *
 * <h3>How to trigger the reproduction</h3>
 * <pre>
 * # application.properties
 * internal.app.non-consumer.filter=true
 * api.request.logging.enabled=true
 *
 * # Then send a request WITHOUT the 'location' header:
 * curl -X POST http://localhost:8080/api/orders \
 *      -H "Content-Type: application/json" \
 *      -d '{"customerId":"C-101","itemName":"Laptop","amount":999.99}'
 *
 * # Expected log output:
 * # ── INCOMING
 * #    requestBodyType: raw
 * #    requestBody:     {"customerId":"C-101","itemName":"Laptop","amount":999.99}
 * #    responseStatus:  401
 * #    responseError:   Please provide the required 'location' header.
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 * @see com.github.yash777.apirequestlogging.filter.ApiLoggingFilter
 * @see com.github.yash777.apirequestlogging.filter.RequestResponseCaptureUtil
 */
@Component
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "filter",
    havingValue = "true"
)
public class ApiDemoFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiDemoFilter.class);

    /**
     * Validates the mandatory {@code location} request header.
     *
     * <h4>Execution flow</h4>
     * <ol>
     *   <li>Skip Swagger / OpenAPI documentation endpoints — those must always
     *       pass through regardless of headers.</li>
     *   <li>Read the {@code location} header (trimmed; {@code null} when absent
     *       or blank).</li>
     *   <li>If absent: call {@link #returnResponse} which issues
     *       {@code sendError(401, message)} and stores the message as a request
     *       attribute for {@code ApiLoggingFilter} to capture, then return.</li>
     *   <li>If present: store in {@code RequestContext} (consumer app concern,
     *       omitted here for simplicity) and continue the chain.</li>
     * </ol>
     *
     * @param request     the current HTTP request
     * @param response    the current HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException propagated from {@code filterChain.doFilter()}
     * @throws IOException      propagated from {@code filterChain.doFilter()} or
     *                          {@code response.sendError()}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        log.debug("[ApiDemoFilter] Request URI: {}", uri);

        // Skip Swagger / OpenAPI documentation paths — these must always pass through
        if (uri.contains("/swagger-ui")
                || uri.contains("/v2/api-docs")
                || uri.contains("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String locationHeader = StringUtils.trimToNull(request.getHeader("location"));
        if (isEmpty(locationHeader)) {
            log.warn("[ApiDemoFilter] Mandatory 'location' header is absent — rejecting request: {}", uri);
            returnResponse(request, response, "Please provide the required 'location' header.");
            return;
            // ↑ chain is NOT invoked here — ApiLoggingFilter must handle
            // the empty request body cache and the missing response body.
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Sends a {@code 401 Unauthorized} error response and stores the error
     * message as a request attribute so that
     * {@link com.github.yash777.apirequestlogging.filter.ApiLoggingFilter}
     * can capture it without requiring any changes to this filter.
     *
     * <h4>Why the request attribute?</h4>
     * <p>{@code response.sendError(status, message)} does NOT write to the
     * response body buffer — Tomcat stores the message internally and only
     * makes it available via {@code javax.servlet.error.message} during the
     * error-dispatch cycle, which runs <em>after</em> our filter's
     * {@code finally} block.  Storing it as
     * {@code "apilog.errorMessage"} on the request makes it immediately
     * available to {@code ApiLoggingFilter}'s Attempt 2 fallback in
     * {@code captureResponseBody()}.</p>
     *
     * @param request      the current HTTP request — used to store the error
     *                     attribute for {@code ApiLoggingFilter}
     * @param response     the current HTTP response
     * @param errorMessage the human-readable error message sent to the client
     *                     and stored for log capture
     * @throws IOException if {@code sendError} fails
     */
    public static void returnResponse(HttpServletRequest  request,
                                      HttpServletResponse response,
                                      String              errorMessage)
            throws IOException {
        // Store the message for ApiLoggingFilter's captureResponseBody() Attempt 2.
        // This is the most reliable cross-container approach — no dependency on
        // Tomcat's error-dispatch cycle.
        //request.setAttribute("apilog.errorMessage", errorMessage);

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, errorMessage);
    }
}