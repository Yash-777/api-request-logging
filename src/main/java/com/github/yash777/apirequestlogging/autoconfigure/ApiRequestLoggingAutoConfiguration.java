package com.github.yash777.apirequestlogging.autoconfigure;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.filter.ApiLoggingFilter;
import com.github.yash777.apirequestlogging.filter.RequestBodyCachingFilter;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * <h2>ApiRequestLoggingAutoConfiguration</h2>
 *
 * <p>Spring Boot Auto-Configuration entry point for the
 * <strong>api-request-logging-spring-boot-starter</strong>.</p>
 *
 * <p>This class is referenced from
 * {@code META-INF/spring.factories} (Spring Boot 2.x) and
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * (Spring Boot 3.x) so that Spring Boot's auto-configuration mechanism picks it
 * up automatically from the classpath — no {@code @Import} needed in consumer
 * applications.</p>
 *
 * <h3>Activation</h3>
 * <p>All beans are <strong>off by default</strong> and are activated only when:</p>
 * <pre>
 * api.request.logging.enabled=true
 * </pre>
 * <p>is present in {@code application.properties} / {@code application.yml}.
 * When the property is absent or {@code false}, this starter has
 * <strong>zero runtime overhead</strong> — no filters, no beans, no body caching.</p>
 *
 * <h3>Beans registered</h3>
 * <ul>
 *   <li>{@link ApiRequestLoggingProperties} — externalized configuration (always bound)</li>
 *   <li>{@link RequestLogCollector} — request-scoped log accumulator (conditional)</li>
 *   <li>{@link RequestBodyCachingFilter} — caches req/res bodies for re-reading (conditional)</li>
 *   <li>{@link ApiLoggingFilter} — captures and prints the structured log (conditional)</li>
 * </ul>
 *
 * <h3>Usage in consumer project (pom.xml)</h3>
 * <pre>{@code
 * <dependency>
 *     <groupId>com.github.yash777</groupId>
 *     <artifactId>api-request-logging-spring-boot-starter</artifactId>
 *     <version>1.0.0</version>
 * </dependency>
 * }</pre>
 *
 * <p>Then in {@code application.properties}:</p>
 * <pre>
 * api.request.logging.enabled=true
 * api.request.logging.request-id-headers=X-Request-ID,request_id
 * api.request.logging.exclude-paths=/actuator,/health
 * </pre>
 *
 * @author Yash
 * @since 1.0.0
 * @see ApiRequestLoggingProperties
 * @see RequestLogCollector
 * @see RequestBodyCachingFilter
 * @see ApiLoggingFilter
 */
@Configuration
@ConditionalOnWebApplication   // only active in a Servlet-based web application
@EnableConfigurationProperties(ApiRequestLoggingProperties.class)
@ConditionalOnProperty(
    prefix      = "api.request.logging",
    name        = "enabled",
    havingValue = "true",
    matchIfMissing = false     // starter is OFF unless explicitly enabled
)
@ComponentScan(basePackages = "com.github.yash777.apirequestlogging")
public class ApiRequestLoggingAutoConfiguration {

    /*
     * All bean definitions live in the individual @Component / @Configuration
     * classes (RequestLogCollector, RequestBodyCachingFilter, ApiLoggingFilter).
     *
     * This class's sole responsibilities are:
     *   1. Act as the @Configuration root found by spring.factories / AutoConfiguration.imports
     *   2. @EnableConfigurationProperties — binds ApiRequestLoggingProperties
     *   3. @ComponentScan — registers the filter and collector beans
     *   4. @ConditionalOnWebApplication — ensures we only activate in a web context
     *   5. @ConditionalOnProperty — master on/off switch
     */
}
