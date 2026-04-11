package com.github.yash777.apirequestlogging.demo.filter;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <h2>RedirectDemoFilter — filter-level redirect to verify {@code redirectPath} capture</h2>
 *
 * <p>Intercepts requests to {@code /demo/filter-redirect/**} and issues a
 * {@code 302} redirect <strong>from the filter</strong> — before the request
 * reaches any controller.  This tests the same {@code captureRedirectPath()}
 * path as controller redirects but with {@code chainInvoked = false} in
 * {@code ApiLoggingFilter}, making it a stricter scenario.</p>
 *
 * <h3>Why this is harder to capture than a controller redirect</h3>
 * <p>When a filter redirects, {@code chain.doFilter()} is never called.
 * {@code ApiLoggingFilter}'s {@code finally} block still runs (because the
 * redirect filter executes inside the chain that {@code ApiLoggingFilter}
 * delegated to), and {@code captureRedirectPath()} reads the {@code Location}
 * header from the {@link org.springframework.web.util.ContentCachingResponseWrapper}
 * which has already been written by {@code sendRedirect()}.</p>
 *
 * <h3>Expected log output</h3>
 * <pre>
 * ── INCOMING
 *    url:                  /demo/filter-redirect/login
 *    httpMethod:           GET
 *    responseStatus:       302
 *    redirectPath:         /login            ← from Location header
 *    requestProcessedTime: 0h 0m 0s 1ms
 * </pre>
 *
 * <h3>Activation</h3>
 * <pre>
 * # application.properties
 * internal.app.non-consumer.redirect=true
 * api.request.logging.enabled=true
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 */
@Component
@Order(1)   // runs before ApiDemoFilter (order 2) — both after ApiLoggingFilter (-103)
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "redirect",
    havingValue = "true"
)
public class RedirectDemoFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RedirectDemoFilter.class);

    /** Only intercept the demo redirect path — all other requests pass through. */
    private static final String REDIRECT_PATH_PREFIX = "/demo/filter-redirect";

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        if (!uri.startsWith(REDIRECT_PATH_PREFIX)) {
            // Not a redirect-demo request — pass through untouched
            filterChain.doFilter(request, response);
            return;
        }

        // Derive redirect target from the path segment after the prefix:
        //   /demo/filter-redirect/login  → /login
        //   /demo/filter-redirect        → /api/payments/status/{txnId}  (default)
        String suffix = uri.substring(REDIRECT_PATH_PREFIX.length());
        String target = suffix.isEmpty() || suffix.equals("/")
                ? "/api/payments/status/1"
                : suffix;   // e.g. "/login", "/api/payments/status/{txnId}"

        log.info("[RedirectDemoFilter] Redirecting {} → {}", uri, target);

        // sendRedirect writes the Location header and commits the response.
        // ApiLoggingFilter's finally block will read it via captureRedirectPath().
        response.sendRedirect(target);

        // Do NOT call filterChain.doFilter() — this is an intentional
        // short-circuit, same as a security filter rejecting an unauthenticated
        // request and redirecting to /login.
    }
}