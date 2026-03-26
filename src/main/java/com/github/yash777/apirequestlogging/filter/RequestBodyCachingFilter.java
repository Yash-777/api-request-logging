package com.github.yash777.apirequestlogging.filter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <h2>RequestBodyCachingFilter</h2>
 *
 * <p>Makes HTTP request and response bodies readable more than once by wrapping
 * the raw {@link HttpServletRequest} and {@link HttpServletResponse} in
 * Spring's caching wrappers before handing control to the next filter.</p>
 *
 * <p>This bean is active only when
 * {@code api.request.logging.enabled=true} is set.</p>
 *
 * <h3>The problem this solves</h3>
 * <p>{@link HttpServletRequest#getInputStream()} is a one-shot stream — once read
 * by the controller or Jackson it is exhausted.  A second read returns nothing.
 * The same applies to the response: bytes written to the output stream are gone.</p>
 *
 * <ul>
 *   <li>{@link ContentCachingRequestWrapper} tees every byte read from the
 *       {@code InputStream} into an internal buffer.  After the controller runs,
 *       {@code getContentAsByteArray()} returns the full body.</li>
 *   <li>{@link ContentCachingResponseWrapper} buffers every byte written to the
 *       output stream internally.  {@code copyBodyToResponse()} <strong>must</strong>
 *       be called in the {@code finally} block to actually send the bytes to the
 *       HTTP client — otherwise the client receives a 0-byte body.</li>
 * </ul>
 *
 * <h3>Execution order</h3>
 * <pre>
 *  Order  -105   RequestContextFilter        ★ populates RequestContextHolder
 *  Order  -104   RequestBodyCachingFilter ◄   this filter — wraps req/res
 *  Order  -103   ApiLoggingFilter             reads wrapped bodies
 *  Order  -100   Spring Security (if present)
 *                DispatcherServlet → @Controller
 * </pre>
 * <p>Wrapping at {@code -104} (before Spring Security at {@code -100}) means
 * Security reads from the caching wrapper, so body-reads by Security are
 * also captured.</p>
 *
 * <h3>Thread safety</h3>
 * <p>This filter is a singleton — one instance shared across all requests.
 * The {@link ContentCachingRequestWrapper} and
 * {@link ContentCachingResponseWrapper} are created as <em>local variables</em>
 * inside {@link #doFilterInternal}, so each concurrent request gets its own
 * pair.  No shared mutable state exists on the filter class itself.</p>
 *
 * <h3>Double-wrap protection</h3>
 * <p>{@code instanceof} guards prevent double-wrapping if Spring Security or
 * another upstream library has already wrapped the request/response.</p>
 *
 * @author Yash
 * @since 1.0.0
 * @see ApiLoggingFilter
 * @see org.springframework.web.util.ContentCachingRequestWrapper
 * @see org.springframework.web.util.ContentCachingResponseWrapper
 */
@Component  // no @Order — order is set in ApiLoggingFilter.FilterOrderConfig
@ConditionalOnProperty(
    prefix = "api.request.logging",
    name   = "enabled",
    havingValue = "true"
)
public class RequestBodyCachingFilter extends OncePerRequestFilter {

    /**
     * Wraps the incoming request and response in caching wrappers, runs the
     * rest of the filter chain, then flushes the buffered response to the socket.
     *
     * <h4>Finally-block flush order (important)</h4>
     * <pre>
     *   ApiLoggingFilter (order -103)        unwinds first  → reads cached bytes
     *   RequestBodyCachingFilter (order -104) unwinds after → flushes to socket
     * </pre>
     * <p>This guarantees {@link ApiLoggingFilter} can read the response body
     * before this filter flushes and clears the internal buffer.</p>
     *
     * @param request  the raw {@link HttpServletRequest}
     * @param response the raw {@link HttpServletResponse}
     * @param chain    the remaining filter chain
     * @throws ServletException propagated from downstream
     * @throws IOException      propagated from downstream
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // ── Wrap request ──────────────────────────────────────────────────
        // instanceof guard prevents double-wrapping.
        HttpServletRequest req = (request instanceof ContentCachingRequestWrapper)
                ? request
                : new ContentCachingRequestWrapper(request);

        // ── Wrap response ─────────────────────────────────────────────────
        // After this line ALL bytes written by the controller go into the
        // wrapper's internal buffer, not the socket.
        HttpServletResponse res = (response instanceof ContentCachingResponseWrapper)
                ? response
                : new ContentCachingResponseWrapper(response);

        try {
            // Controller/service runs here; bodies are buffered by the wrappers
            chain.doFilter(req, res);
        } finally {
            // ── Flush buffered response to the real socket ─────────────────
            // Without this call the HTTP client receives a 0-byte body.
            // Runs AFTER ApiLoggingFilter's finally (order -103 unwinds first),
            // so ApiLoggingFilter has already read the cached bytes before we
            // flush and clear the buffer here.
            if (res instanceof ContentCachingResponseWrapper) {
                ((ContentCachingResponseWrapper) res).copyBodyToResponse();
            }
        }
    }
}
