package com.github.yash777.apirequestlogging.demo.controller;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <h2>RedirectDemoController — verifies {@code redirectPath} capture in the log</h2>
 *
 * <p>Provides four redirect endpoints, each using a different redirect mechanism.
 * {@link com.github.yash777.apirequestlogging.filter.RequestResponseCaptureUtil#captureRedirectPath}
 * reads the {@code Location} response header in {@code ApiLoggingFilter}'s
 * {@code finally} block and writes it under {@code "redirectPath"} in the
 * {@code INCOMING} log block.</p>
 *
 * <h3>Expected log output for any redirect endpoint</h3>
 * <pre>
 * ── INCOMING
 *    url:              /demo/redirect/spring
 *    httpMethod:       GET
 *    responseStatus:   302
 *    redirectPath:     /api/payments/status/{txnId}        ← captured from Location header
 *    requestProcessedTime: 0h 0m 0s 2ms
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
@RestController
@RequestMapping("/demo/redirect")
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "redirect",
    havingValue = "true"
)
public class RedirectDemoController {

    private static final Logger log = LoggerFactory.getLogger(RedirectDemoController.class);

    // ── 1. Spring ResponseEntity redirect (302 Found) ─────────────────────

    /**
     * Redirects using Spring's {@link ResponseEntity} with a {@code Location}
     * header — the most explicit and testable approach.
     *
     * <p>The {@code Location} header is set directly on the response, so
     * {@code captureRedirectPath()} finds it immediately in the {@code finally}
     * block without any special handling.</p>
     *
     * <pre>
     * curl -v http://localhost:8080/demo/redirect/spring
     * # Response: HTTP/1.1 302  Location: /api/payments/status/{txnId}
     * # Log:      redirectPath: /api/payments/status/{txnId}
     * </pre>
     */
    @GetMapping("/spring")
    public ResponseEntity<Void> redirectViaResponseEntity() {
        log.info("[RedirectDemoController] /spring → redirect to /api/payments/status/{txnId}");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, "/api/payments/status/1");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);   // 302
    }

    // ── 2. Spring "redirect:" view name (302 Found) ───────────────────────

    /**
     * Redirects using Spring MVC's {@code "redirect:"} prefix on the return value.
     *
     * <p>Spring resolves this string through {@code UrlBasedViewResolver} and
     * writes a {@code 302} response with a {@code Location} header — identical
     * to the {@link ResponseEntity} approach at the HTTP level.</p>
     *
     * <pre>
     * curl -v http://localhost:8080/demo/redirect/view
     * # Response: HTTP/1.1 302  Location: /api/payments/status/{txnId}
     * # Log:      redirectPath: /api/payments/status/{txnId}
     * </pre>
     */
    @GetMapping("/view")
    public String redirectViaViewName() {
        log.info("[RedirectDemoController] /view → redirect: /api/payments/status/{txnId}");
        return "redirect:/api/payments/status/{txnId}";
    }

    // ── 3. HttpServletResponse.sendRedirect (302 Found) ───────────────────

    /**
     * Redirects using the low-level {@link HttpServletResponse#sendRedirect}
     * call — the same mechanism a consumer filter or legacy servlet code uses.
     *
     * <p>{@code sendRedirect()} sets the {@code Location} header and commits the
     * response immediately.  {@code captureRedirectPath()} still reads the header
     * from the {@link org.springframework.web.util.ContentCachingResponseWrapper}
     * in the {@code finally} block because the wrapper buffers header writes.</p>
     *
     * <pre>
     * curl -v http://localhost:8080/demo/redirect/servlet
     * # Response: HTTP/1.1 302  Location: http://localhost:8080/api/payments/status/{txnId}
     * # Log:      redirectPath: http://localhost:8080/api/payments/status/{txnId}
     * </pre>
     *
     * @param response the raw servlet response
     * @throws IOException if {@code sendRedirect} fails
     */
    @GetMapping("/servlet")
    public void redirectViaServletResponse(HttpServletResponse response) throws IOException {
        log.info("[RedirectDemoController] /servlet → sendRedirect /api/payments/status/{txnId}");
        response.sendRedirect("/api/payments/status/1");
    }

    // ── 4. Permanent redirect (301 Moved Permanently) ─────────────────────

    /**
     * Returns a permanent {@code 301 Moved Permanently} redirect.
     *
     * <p>Useful for verifying that {@code captureRedirectPath()} works for
     * {@code 301} as well as {@code 302} — the fix reads the {@code Location}
     * header unconditionally and logs it whenever non-null.</p>
     *
     * <pre>
     * curl -v http://localhost:8080/demo/redirect/permanent
     * # Response: HTTP/1.1 301  Location: /api/payments/status/{txnId}
     * # Log:      redirectPath: /api/payments/status/{txnId}
     * </pre>
     */
    @GetMapping("/permanent")
    public ResponseEntity<Void> redirectPermanent() {
        log.info("[RedirectDemoController] /permanent → 301 to /api/payments/status/{txnId}");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, "/api/payments/status/1");
        return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);  // 301
    }

    // ── 5. External URL redirect ───────────────────────────────────────────

    /**
     * Redirects to an external URL — demonstrates that {@code redirectPath}
     * captures the full absolute URL including scheme and host.
     *
     * <pre>
     * curl -v "http://localhost:8080/demo/redirect/external?to=https://example.com"
     * # Response: HTTP/1.1 302  Location: https://example.com
     * # Log:      redirectPath: https://example.com
     * </pre>
     *
     * @param to the target URL passed as a query parameter
     */
    @GetMapping("/external")
    public ResponseEntity<Void> redirectExternal(@PathVariable(required = false) String to) {
        String target = (to != null && !to.isEmpty()) ? to : "https://example.com";
        log.info("[RedirectDemoController] /external → redirect to {}", target);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, target);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}