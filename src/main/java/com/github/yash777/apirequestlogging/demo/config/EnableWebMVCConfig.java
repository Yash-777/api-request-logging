package com.github.yash777.apirequestlogging.demo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

/**
 * <h2>EnableWebMVCConfig — Demo class to reproduce the {@code @EnableWebMvc} filter-order issue</h2>
 *
 * <p>This configuration class exists <strong>solely as a demo fixture</strong>
 * to reproduce and verify the fix for the {@code @RequestScope} proxy failure
 * that occurs when {@code @EnableWebMvc} is present in a consumer application.</p>
 *
 * <h3>What {@code @EnableWebMvc} does to the filter chain</h3>
 * <p>Placing {@code @EnableWebMvc} on any {@code @Configuration} class is a
 * signal to Spring Boot that says <em>"I am taking full control of Spring MVC —
 * disable all Boot defaults."</em>  Spring Boot honours this by backing off
 * {@link org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration}
 * entirely.</p>
 *
 * <p>One of the responsibilities of {@code WebMvcAutoConfiguration} is to
 * register {@code OrderedRequestContextFilter} at order {@code -105}.  That
 * filter calls {@code RequestContextHolder.setRequestAttributes(...)} on each
 * incoming thread, which is the prerequisite for any {@code @RequestScope} bean
 * to be resolvable.  When it is absent the resolution chain breaks:</p>
 *
 * <pre>
 * {@literal @}EnableWebMvc present
 *   → WebMvcAutoConfiguration backs off
 *       → OrderedRequestContextFilter (order -105) is NOT registered
 *           → RequestContextHolder is never populated
 *               → {@literal @}RequestScope CGLIB proxy throws:
 *                   "No thread-bound request found: Are you referring to
 *                    request attributes outside of an actual web request?"
 * </pre>
 *
 * <h3>Fix applied in the starter</h3>
 * <p>
 * {@link com.github.yash777.apirequestlogging.autoconfigure.ApiRequestLoggingAutoConfiguration}
 * now declares an {@code OrderedRequestContextFilter} bean guarded by
 * {@code @ConditionalOnMissingBean(RequestContextFilter.class)}.  This ensures
 * the filter is always registered at order {@code -105} — even when
 * {@code @EnableWebMvc} has suppressed Boot's own registration — and is a
 * no-op when Boot has already registered it.</p>
 *
 * <h3>Activation</h3>
 * <p>This class is active only when <strong>both</strong> conditions are met:</p>
 * <ol>
 *   <li>{@code @ConditionalOnDemoEnvironment} — the JVM entry point is
 *       {@code DemoApplication.main()} (not a consumer app classpath).</li>
 *   <li>{@code internal.app.non-consumer.web.mvc=true} — explicitly opted-in
 *       via {@code application.properties} to enable the MVC reproduction test.</li>
 * </ol>
 *
 * <h3>How to trigger the reproduction</h3>
 * <pre>
 * # application.properties
 * internal.app.non-consumer.web.mvc=true
 * internal.app.non-consumer.filter=true   # also enable ApiDemoFilter
 * api.request.logging.enabled=true
 * </pre>
 * <p>With these properties set, make any HTTP request.  Without the fix in
 * {@code ApiRequestLoggingAutoConfiguration} it would throw
 * {@code BeanCreationException: Scope 'request' is not active}.  With the fix
 * the request is logged normally.</p>
 *
 * @author Yash
 * @since 1.1.0
 * @see com.github.yash777.apirequestlogging.autoconfigure.ApiRequestLoggingAutoConfiguration
 * @see com.github.yash777.apirequestlogging.demo.filter.ApiDemoFilter
 */
@Configuration
@ConditionalOnDemoEnvironment
@ConditionalOnProperty(
    prefix = "internal.app.non-consumer",
    name   = "web.mvc",
    havingValue = "true"
)
@EnableWebMvc // ← intentional: reproduces the filter-order issue in consumer apps
public class EnableWebMVCConfig {
    /*
     * No beans declared here — the class is registered purely so that
     * @EnableWebMvc takes effect and WebMvcAutoConfiguration backs off.
     *
     * The fix under test is in ApiRequestLoggingAutoConfiguration:
     *
     *   @Bean
     *   @ConditionalOnMissingBean(RequestContextFilter.class)
     *   public OrderedRequestContextFilter starterRequestContextFilter() {
     *       OrderedRequestContextFilter f = new OrderedRequestContextFilter();
     *       f.setOrder(-105);
     *       return f;
     *   }
     *
     * When this class is active and the above bean is absent, every request
     * throws "No thread-bound request found".  When the bean is present the
     * filter chain is correct and @RequestScope works normally.
     */
}