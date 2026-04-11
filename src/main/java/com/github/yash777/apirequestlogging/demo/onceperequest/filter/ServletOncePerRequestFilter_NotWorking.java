package com.github.yash777.apirequestlogging.demo.onceperequest.filter;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * <h2>ServletOncePerRequestFilter — raw {@code javax.servlet.Filter}</h2>
 *
 * <p>Demonstrates the behaviour of a <strong>plain Servlet filter</strong>
 * when a request goes through forwarding or including cycles.</p>
 *
 * <h3>Key difference from Spring's {@link org.springframework.web.filter.OncePerRequestFilter}</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th></th><th>Servlet Filter</th><th>Spring OncePerRequestFilter</th></tr>
 *   <tr><td>Initial REQUEST</td><td>✅ runs</td><td>✅ runs</td></tr>
 *   <tr><td>FORWARD dispatch</td><td>✅ runs AGAIN</td><td>❌ skipped</td></tr>
 *   <tr><td>INCLUDE dispatch</td><td>✅ runs AGAIN</td><td>❌ skipped</td></tr>
 *   <tr><td>ERROR dispatch</td><td>✅ runs AGAIN</td><td>❌ skipped</td></tr>
 * </table>
 *
 * <p>Watch the console when hitting {@code /demo/forward/a} — this filter
 * prints twice: once for the original REQUEST, once for the FORWARD dispatch.</p>
 *
 * <h3>Activation</h3>
 * <pre>
 * internal.app.non-consumer.once-per-request=true
 * </pre>
 *
 *
@WebFilter is not picked up by Spring Boot auto-scanning unless you add @ServletComponentScan to the main application class. Without it, @WebFilter annotated classes are simply ignored — no registration, no execution, no error.
Spring Boot's component scan only picks up @Component, @Service, @Controller, @Configuration etc. @WebFilter is a plain Servlet annotation that requires either:

@ServletComponentScan on the main class, or
A manual FilterRegistrationBean

The @WebFilter approach also does not respect Spring's @ConditionalOnProperty because it is processed by the embedded Tomcat directly, not by Spring's bean factory.

 * @author Yash
 * @since 1.1.0
 */
@Order(10)
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "once-per-request",
    havingValue = "true"
)
@WebFilter(urlPatterns = {
    "/demo/forward/*",
    "/demo/include/*"
})
public class ServletOncePerRequestFilter_NotWorking implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ServletOncePerRequestFilter_NotWorking.class);

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("[ServletOncePerRequestFilter] Registered — will run on EVERY dispatch type");
    }

    /**
     * Runs on EVERY dispatch cycle — REQUEST, FORWARD, INCLUDE, ERROR.
     *
     * <p>The dispatch type is logged so you can see in the console that this
     * filter executes twice when forwarding: once for the original REQUEST
     * and once for the FORWARD.</p>
     */
    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;

        // javax.servlet.DispatcherType: REQUEST, FORWARD, INCLUDE, ERROR, ASYNC
        String dispatchType = httpReq.getDispatcherType().name();
        String uri          = httpReq.getRequestURI();

        log.info("[ServletOncePerRequestFilter] ▶ dispatch={} uri={}", dispatchType, uri);

        chain.doFilter(request, response);

        log.info("[ServletOncePerRequestFilter] ◀ dispatch={} uri={} DONE", dispatchType, uri);
    }

    @Override
    public void destroy() {}
}