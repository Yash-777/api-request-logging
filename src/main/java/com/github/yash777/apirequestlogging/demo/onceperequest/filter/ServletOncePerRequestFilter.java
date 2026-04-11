package com.github.yash777.apirequestlogging.demo.onceperequest.filter;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
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
 *   <tr><td>FORWARD dispatch</td><td>✅ runs AGAIN</td><td>❌ skipped (flag set)</td></tr>
 *   <tr><td>INCLUDE dispatch</td><td>✅ runs AGAIN</td><td>❌ skipped (flag set)</td></tr>
 *   <tr><td>ERROR dispatch</td><td>✅ runs AGAIN</td><td>❌ skipped (flag set)</td></tr>
 * </table>
 *
 * <h3>Why NOT {@code @WebFilter}</h3>
 * <p>{@code @WebFilter} is a plain Servlet annotation processed by the embedded
 * Tomcat container — not by Spring's bean factory.  It is silently ignored
 * unless {@code @ServletComponentScan} is present on the main class AND it does
 * not honour Spring's {@code @ConditionalOnProperty}.  The correct approach for
 * a Spring Boot application is to register via {@link FilterRegistrationBean}
 * inside an inner {@link Configuration} class, which gives full control over
 * dispatch types, URL patterns, and Spring conditions.</p>
 *
 * <h3>Activation</h3>
 * <pre>
 * internal.app.non-consumer.once-per-request=true
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 */
public class ServletOncePerRequestFilter implements Filter {

    private static final Logger log =
            LoggerFactory.getLogger(ServletOncePerRequestFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("[ServletOncePerRequestFilter] Registered — runs on EVERY dispatch type "
               + "(REQUEST, FORWARD, INCLUDE, ERROR)");
    }

    /**
     * Runs on <strong>every</strong> dispatch cycle — REQUEST, FORWARD, INCLUDE, ERROR.
     *
     * <p>The dispatch type is logged so you can see in the console that this filter
     * executes <em>twice</em> when forwarding: once for the original REQUEST and
     * once for the FORWARD dispatch.  Compare with
     * {@link SpringOncePerRequestFilter} which prints only once for the same flow.</p>
     */
    @Override
    public void doFilter(ServletRequest  request,
                         ServletResponse response,
                         FilterChain     chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        String dispatchType = httpReq.getDispatcherType().name();
        String uri          = httpReq.getRequestURI();

        log.info("[ServletOncePerRequestFilter] ▶ dispatch={} uri={}", dispatchType, uri);

        chain.doFilter(request, response);

        log.info("[ServletOncePerRequestFilter] ◀ dispatch={} uri={} DONE", dispatchType, uri);
    }

    @Override
    public void destroy() {}


    // ══════════════════════════════════════════════════════════════════
    //  Registration — inner @Configuration class
    //  Registers the filter with explicit dispatch types so it also
    //  runs on FORWARD and INCLUDE dispatches, not just REQUEST.
    // ══════════════════════════════════════════════════════════════════

    /**
     * Registers {@link ServletOncePerRequestFilter} via {@link FilterRegistrationBean}
     * so Spring Boot controls the lifecycle and all Spring conditions are honoured.
     *
     * <h4>Why explicit {@code DispatcherType}s?</h4>
     * <p>By default {@link FilterRegistrationBean} only registers a filter for
     * {@code REQUEST} dispatches.  To prove that a plain {@code javax.servlet.Filter}
     * runs on FORWARD and INCLUDE too, we must explicitly add those dispatch types
     * here — otherwise Tomcat skips the filter on those dispatches and the
     * comparison with {@link SpringOncePerRequestFilter} is incomplete.</p>
     */
    @Configuration
    @ConditionalOnDemoEnvironment
    @ConditionalOnProperty(
        prefix   = "internal.app.non-consumer",
        name     = "once-per-request",
        havingValue = "true"
    )
    static class Registration {

        /**
         * Registers the filter at order {@code 10} for the demo forward and
         * include paths, covering all four dispatch types.
         *
         * @return configured {@link FilterRegistrationBean}
         */
        @Bean
        public FilterRegistrationBean<ServletOncePerRequestFilter>
                servletOncePerRequestFilterReg() {

            FilterRegistrationBean<ServletOncePerRequestFilter> bean =
                    new FilterRegistrationBean<>(new ServletOncePerRequestFilter());

            bean.setOrder(10);
            bean.addUrlPatterns("/demo/forward/*", "/demo/include/*");
            bean.setName("servletOncePerRequestFilter");

            // ── Critical: add FORWARD and INCLUDE dispatch types ──────────
            // Without these, Tomcat only invokes the filter on REQUEST dispatch.
            // Adding them here proves the filter runs on every dispatch type
            // while SpringOncePerRequestFilter still runs only once.
            bean.setDispatcherTypes(
                javax.servlet.DispatcherType.REQUEST,
                javax.servlet.DispatcherType.FORWARD,
                javax.servlet.DispatcherType.INCLUDE,
                javax.servlet.DispatcherType.ERROR
            );

            return bean;
        }
    }
}