package com.github.yash777.apirequestlogging.demo.onceperequest.forward;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <h2>ControllerA — initiates an internal {@code RequestDispatcher.forward()}</h2>
 *
 * <p>Demonstrates <strong>server-side request forwarding</strong>: the client
 * sends one HTTP request to {@code /demo/forward/a} and receives the response
 * from {@code /demo/forward/b} — all within a single request/response cycle,
 * invisible to the client.</p>
 *
 * <h3>Dispatch flow</h3>
 * <pre>
 * Client GET /demo/forward/a
 *   │
 *   ├─► FIRST EXECUTION (REQUEST dispatch)
 *   │     SpringOncePerRequestFilter.doFilterInternal() → runs ✅
 *   │     ServletOncePerRequestFilter.doFilter()        → runs ✅
 *   │     ControllerA.forward()
 *   │       └─► RequestDispatcher.forward(request, response)
 *   │
 *   └─► SECOND EXECUTION (FORWARD dispatch — same request object, same thread)
 *         SpringOncePerRequestFilter → SKIPPED (already filtered) ❌
 *         ServletOncePerRequestFilter.doFilter()        → runs AGAIN ✅
 *         ControllerB.handle()
 *           └─► writes response body
 * </pre>
 *
 * <h3>Key point — same request object</h3>
 * <p>The <em>same</em> {@link HttpServletRequest} object is passed to
 * {@code ControllerB}.  Attributes set in {@code ControllerA} are visible in
 * {@code ControllerB} via {@code request.getAttribute(...)}.  The URL seen by
 * the client remains {@code /demo/forward/a}.</p>
 *
 * <h3>ApiLoggingFilter behaviour</h3>
 * <p>{@code ApiLoggingFilter} runs once (it extends {@link org.springframework.web.filter.OncePerRequestFilter}).
 * The log shows the original URI {@code /demo/forward/a} and the response
 * body written by {@code ControllerB}.</p>
 *
 * <h3>Activation</h3>
 * <pre>
 * internal.app.non-consumer.once-per-request=true
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 * @see ControllerB
 */
@RestController
@RequestMapping("/demo/forward")
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "once-per-request",
    havingValue = "true"
)
public class ControllerA {

    private static final Logger log = LoggerFactory.getLogger(ControllerA.class);

    /**
     * Receives the original client request, stamps a request attribute, then
     * forwards internally to {@link ControllerB}.
     *
     * <p>After {@code forward()} returns, this method must NOT write anything to
     * the response — the response has already been committed by {@code ControllerB}.</p>
     *
     * @param request  used to obtain the {@link RequestDispatcher} and set attributes
     * @param response passed through to the dispatcher
     * @throws ServletException propagated from {@code forward()}
     * @throws IOException      propagated from {@code forward()}
     */
    @GetMapping("/a")
    public void forward(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        log.info("[ControllerA] Received REQUEST dispatch — setting attribute and forwarding to /demo/forward/b");

        // Attributes survive the forward — ControllerB can read these
        request.setAttribute("forwardedFrom", "ControllerA");
        request.setAttribute("originalUri",   request.getRequestURI());

        // Server-side forward — client URL stays /demo/forward/a
        // Same request and response objects are reused in ControllerB
        RequestDispatcher dispatcher = request.getRequestDispatcher("/demo/forward/b");
        dispatcher.forward(request, response);

        // Execution returns here after ControllerB completes, but the response
        // is already committed — do NOT write anything after this point.
        log.info("[ControllerA] Returned from forward — response already committed by ControllerB");
    }
}