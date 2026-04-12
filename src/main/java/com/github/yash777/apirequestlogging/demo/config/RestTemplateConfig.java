package com.github.yash777.apirequestlogging.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

/**
 * <h2>RestTemplateConfig</h2>
 *
 * <p>Spring {@link Configuration} class that manually registers a
 * {@link RestTemplate} as a Spring-managed bean for the built-in live demo
 * of the <strong>api-request-logging-spring-boot-starter</strong>.</p>
 *
 * <p>Active only when {@code DemoApplication.main()} is the JVM entry point
 * (guarded by {@link ConditionalOnDemoEnvironment}). Consumer applications
 * that add this starter as a Maven dependency never trigger
 * {@code DemoApplication.main()}, so this configuration class — and the
 * {@link RestTemplate} bean it declares — are never registered in consumer
 * contexts.</p>
 *
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 * <h3>Why RestTemplate is NOT a Spring Bean by default</h3>
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 *
 * <p>{@link RestTemplate} is a <em>client-side utility class</em> — deliberately
 * designed after the <strong>Template Method pattern</strong> (like
 * {@code JdbcTemplate} or {@code KafkaTemplate}). Spring auto-configuration
 * intentionally <strong>does not</strong> register a {@code RestTemplate} bean
 * because:</p>
 *
 * <ul>
 *   <li><strong>No single sensible default</strong> — there is no default
 *       base URL, no agreed timeout, no default auth or SSL policy. Every
 *       application has its own requirements.</li>
 *   <li><strong>Multiple configurations may be needed</strong> — one bean might
 *       use a 5 s timeout for a fast internal service while another needs 60 s
 *       for a slow external partner. Auto-registering a singleton would force
 *       both to share one configuration.</li>
 *   <li><strong>Interceptor isolation</strong> — different beans can carry
 *       different {@code ClientHttpRequestInterceptor} chains (e.g. auth,
 *       logging, retry) without interfering with each other.</li>
 *   <li><strong>Template-pattern philosophy</strong> — Spring's "XxxTemplate"
 *       helpers are helper objects that <em>you</em> configure and hand to
 *       Spring, not components that Spring discovers automatically.</li>
 * </ul>
 *
 * <p>Because of this, you must <strong>explicitly declare a
 * {@code @Bean}</strong> — exactly as {@link #demoRestTemplate()} does below —
 * for Spring to manage the instance, inject it into other components via
 * {@code @Autowired} / constructor injection, and allow post-processors
 * (such as {@code RestTemplateLoggingBeanPostProcessor}) to intercept it.</p>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │  new RestTemplate()        → invisible to Spring, never     │
 * │  (inside a method body)      intercepted, not injectable    │
 * │                                                             │
 * │  @Bean RestTemplate …      → managed by Spring, eligible    │
 * │  (declared here)             for DI and post-processing     │
 * └─────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 * <h3>Deprecation Notice</h3>
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 *
 * <p>{@link RestTemplate} was deprecated in
 * <strong>Spring Framework 6.1 / Spring Boot 3.2</strong> (released
 * November 2023). It will not be removed in the short term but receives
 * only maintenance fixes going forward. Prefer the fluent, non-blocking
 * {@code RestClient} (synchronous) or {@code WebClient} (reactive) for
 * new code:</p>
 *
 * <pre>
 * // RestTemplate (legacy — Spring Boot &lt; 3.2)
 * ResponseEntity&lt;String&gt; r = restTemplate.getForEntity(url, String.class);
 *
 * // RestClient (preferred — Spring Boot 3.2+)
 * String body = restClient.get().uri(url).retrieve().body(String.class);
 * </pre>
 *
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 * <h3>RestTemplate auto-capture demo</h3>
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 *
 * <p>Because {@link #demoRestTemplate()} is declared as a Spring {@code @Bean},
 * the starter's {@code RestTemplateLoggingBeanPostProcessor} detects it at
 * startup and — when the property below is {@code true} — automatically wraps
 * it with a {@code BufferingClientHttpRequestFactory} and injects a
 * {@code RestTemplateLoggingInterceptor}:</p>
 *
 * <pre>
 * api.request.logging.rest-template.auto-capture-enabled=true
 * </pre>
 *
 * <p>After that, every outbound call made through {@code demoRestTemplate} is
 * captured and appears in the structured log block alongside the INCOMING
 * section:</p>
 *
 * <pre>
 * ── INCOMING
 *    controllerHandler:    WeatherController#getCurrent
 *    url:                  /api/weather/current
 *    httpMethod:           GET
 *    responseStatus:       200
 *    requestProcessedTime: 0h 0m 0s 312ms
 *
 * ── https://api.open-meteo.com/v1/forecast?... [14:32:05.042]
 *    request:   (no body)
 *    response:  {"current":{"temperature_2m":28.4},...}
 * </pre>
 *
 * <h3>Logger routing</h3>
 * <p>Log output is emitted under the logger name configured by
 * {@code ApiRequestLoggingProperties.LoggerProperties#getName()}, which
 * defaults to {@code api.request.logging}. Control its level via
 * {@code application.properties}:</p>
 *
 * <pre>
 * logging.level.api.request.logging=INFO
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 * @see com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment
 * @see com.github.yash777.apirequestlogging.resttemplate.RestTemplateLoggingBeanPostProcessor
 * @see <a href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/RestTemplate.html">
 *      RestTemplate Javadoc</a>
 * @see <a href="https://docs.spring.io/spring-framework/reference/integration/rest-clients.html">
 *      Spring REST Clients reference (RestClient / RestTemplate)</a>
 */
@Configuration
@ConditionalOnDemoEnvironment
public class RestTemplateConfig {

    // ══════════════════════════════════════════════════════════════════
    //  BEANS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Registers a Spring-managed {@link RestTemplate} bean named
     * {@code demoRestTemplate} in the application context.
     *
     * <!-- Why @Bean and not new RestTemplate() elsewhere? -->
     * <h4>Why declare it as a {@code @Bean}?</h4>
     *
     * <p>{@link RestTemplate} ships as an ordinary Java class — it carries
     * <strong>no</strong> {@code @Component}, {@code @Service}, or any other
     * Spring stereotype annotation. Spring therefore never picks it up during
     * component scan. Declaring it here with {@code @Bean}:</p>
     *
     * <ol>
     *   <li><strong>Makes it injectable</strong> — any {@code @Service} or
     *       {@code @Component} can receive it via {@code @Autowired} or
     *       constructor injection.</li>
     *   <li><strong>Enables post-processing</strong> — Spring's
     *       {@code BeanPostProcessor} infrastructure (including
     *       {@code RestTemplateLoggingBeanPostProcessor}) can wrap or modify
     *       it before the first caller uses it.</li>
     *   <li><strong>Enforces singleton scope</strong> — the same, fully
     *       configured instance (with interceptors, timeouts, etc.) is shared
     *       across the application, avoiding accidental duplication.</li>
     * </ol>
     *
     * <p>By contrast, an instance created with {@code new RestTemplate()} inside
     * a method body is invisible to Spring — it is never post-processed, never
     * intercepted by the logging starter, and cannot be injected elsewhere.</p>
     *
     * <!-- Deprecation note on the method -->
     * <p><strong>Note:</strong> {@link RestTemplate} itself is deprecated as of
     * Spring Framework 6.1 (Spring Boot 3.2). This bean exists solely to
     * demonstrate the starter's auto-capture capability on a familiar API.
     * For new outbound HTTP calls, prefer {@code RestClient.Builder} or
     * {@code WebClient.Builder}.</p>
     *
     * @return a new, plain {@link RestTemplate} instance ready for Spring
     *         to post-process and inject
     * @deprecated {@link RestTemplate} is deprecated since Spring Framework 6.1
     *             / Spring Boot 3.2. Use {@code RestClient} or {@code WebClient}
     *             for new development.
     */
    @Bean
    public RestTemplate demoRestTemplate() {
        return new RestTemplate();
    }
}