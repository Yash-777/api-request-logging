package com.github.yash777.apirequestlogging.demo.onceperequest.filter;

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
 * <h2>SpringOncePerRequestFilter — extends {@link OncePerRequestFilter}</h2>
 *
 * <p>Demonstrates that Spring's {@link OncePerRequestFilter} runs
 * <strong>exactly once per logical HTTP request</strong>, regardless of how many
 * internal FORWARD or INCLUDE dispatches occur.</p>
 *
 * <h3>How OncePerRequestFilter prevents double-execution</h3>
 * <p>It stores a flag in the request attribute named
 * {@code "SpringOncePerRequestFilter.FILTERED"} before delegating to
 * {@link #doFilterInternal}.  On any subsequent dispatch (FORWARD, INCLUDE) the
 * flag is already present, so the filter skips execution and calls
 * {@code chain.doFilter()} directly.</p>
 *
 * <h3>Activation</h3>
 * <pre>
 * internal.app.non-consumer.once-per-request=true
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 */
@Component
@Order(11)
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "once-per-request",
    havingValue = "true"
)
public class SpringOncePerRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SpringOncePerRequestFilter.class);

    /**
     * Called ONLY ONCE per logical request — never on FORWARD or INCLUDE.
     *
     * <p>Even when {@code ControllerA} forwards to {@code ControllerB}, this
     * method is invoked only for the initial REQUEST dispatch. Compare this with
     * {@link ServletOncePerRequestFilter} which runs twice for the same flow.</p>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String dispatchType = request.getDispatcherType().name();
        String uri          = request.getRequestURI();

        log.info("[SpringOncePerRequestFilter] ▶ dispatch={} uri={} (runs once only)", dispatchType, uri);

        chain.doFilter(request, response);

        log.info("[SpringOncePerRequestFilter] ◀ dispatch={} uri={} DONE", dispatchType, uri);
    }

    /**
     * Also apply this filter to FORWARD and INCLUDE dispatches so we can
     * prove it still skips execution for those types.
     *
     * <p>Overriding to {@code false} means the filter is registered for all
     * dispatch types — but {@link OncePerRequestFilter}'s own guard ensures
     * {@link #doFilterInternal} is called only once.</p>
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}