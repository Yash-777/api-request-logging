package com.github.yash777.apirequestlogging.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;


/**
 * <h2>DemoConfiguration</h2>
 *
 * <p>Spring {@link Configuration} class for the built-in live demo of the
 * <strong>api-request-logging-spring-boot-starter</strong>.</p>
 *
 * <p>Active only when {@link DemoApplication#main(String[])} is the JVM entry point
 * (guarded by {@link ConditionalOnDemoEnvironment}).  Consumer applications that add
 * this starter as a Maven dependency never trigger {@code DemoApplication.main()},
 * so this configuration class — and the {@link RestTemplate} bean it declares — are
 * never registered in consumer contexts.</p>
 *
 * <h3>RestTemplate auto-capture demo</h3>
 * <p>This class declares a Spring-managed {@link RestTemplate} bean named
 * {@code demoRestTemplate}.  When the property below is set to {@code true},
 * the starter's {@code RestTemplateLoggingBeanPostProcessor} detects every
 * {@link RestTemplate} bean in the application context at startup and
 * automatically injects a {@code RestTemplateLoggingInterceptor} into it:</p>
 *
 * <pre>
 * api.request.logging.rest-template.auto-capture-enabled=true
 * </pre>
 *
 * <p>After that, every call made through {@code demoRestTemplate} is logged
 * automatically under a timestamped key in {@code RequestLogCollector},
 * appearing in the structured request log block alongside the INCOMING section:</p>
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
 * <p><strong>Limitation:</strong> only Spring-managed {@link RestTemplate} beans
 * (i.e. declared via {@code @Bean}) are intercepted automatically.
 * Instances created with {@code new RestTemplate()} inside a method body are
 * invisible to Spring and will not be captured unless logged manually via
 * {@link com.github.yash777.apirequestlogging.collector.RequestLogCollector#addLog}.</p>
 *
 * <h3>Logger routing</h3>
 * <p>The SLF4J logger used here is named after
 * {@link ApiRequestLoggingProperties.LoggerProperties#getName()}, which defaults to
 * {@code api.request.logging}.  You can control its level and appender in
 * {@code logback.xml} without touching this class:</p>
 *
 * <pre>
 * # application.properties
 * logging.level.api.request.logging=INFO
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 * @see com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment
 * @see com.github.yash777.apirequestlogging.resttemplate.RestTemplateLoggingBeanPostProcessor
 */
@Configuration
@ConditionalOnDemoEnvironment
public class DemoConfiguration {

    /**
     * SLF4J logger whose name is driven by
     * {@link ApiRequestLoggingProperties.LoggerProperties#getName()}.
     *
     * <p>Defaults to {@code api.request.logging}.  Configure the level in
     * {@code application.properties}:</p>
     * <pre>
     * logging.level.api.request.logging=INFO
     * </pre>
     */
    private final Logger log;

    /**
     * Constructor injection — Spring resolves and fully constructs
     * {@link ApiRequestLoggingProperties} before calling this constructor,
     * so {@code properties.getLogger().getName()} is guaranteed to be populated.
     *
     * @param properties externalized starter configuration; must not be {@code null}
     */
    public DemoConfiguration(ApiRequestLoggingProperties properties) {
        this.log = LoggerFactory.getLogger(properties.getLogger().getName());
    }

    // ══════════════════════════════════════════════════════════════════
    //  BEANS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Declares a Spring-managed {@link RestTemplate} bean for the demo application.
     *
     * <p>Because this bean is registered in the Spring {@code ApplicationContext},
     * the starter's {@code RestTemplateLoggingBeanPostProcessor} detects it at
     * startup and — when
     * {@code api.request.logging.rest-template.auto-capture-enabled=true} —
     * automatically wraps it with a {@code BufferingClientHttpRequestFactory} and
     * injects a {@code RestTemplateLoggingInterceptor}.  No manual configuration
     * is needed in the calling code.</p>
     *
     * <p>Compare this with {@code new RestTemplate()} created inside a method body:
     * that instance is invisible to Spring and is therefore never intercepted.</p>
     *
     * @return a new, plain {@link RestTemplate} instance ready for Spring to post-process
     */
    @Bean
    public RestTemplate demoRestTemplate() {
        return new RestTemplate();
    }

    // ══════════════════════════════════════════════════════════════════
    //  INJECTED FIELDS
    // ══════════════════════════════════════════════════════════════════

    /**
     * The {@link RestTemplate} bean declared by {@link #demoRestTemplate()}.
     *
     * <p>{@code @Lazy} defers injection until first use, ensuring that the
     * {@code RestTemplateLoggingBeanPostProcessor} has had a chance to
     * post-process (and optionally intercept) the bean before any call is made.</p>
     */
    @Autowired
    @Lazy
    private RestTemplate restTemplate;

    // ══════════════════════════════════════════════════════════════════
    //  DEMO METHODS
    // ══════════════════════════════════════════════════════════════════

    /**
     * URL for the Open-Meteo free weather API, pre-configured for Hyderabad, India
     * (latitude 17.38°N, longitude 78.47°E).
     *
     * <p>Returns the current {@code temperature_2m} (air temperature at 2 m above ground)
     * without any authentication or API key.  Suitable for local demo and integration
     * testing.</p>
     *
     * <p>Example response fragment:</p>
     * <pre>
     * {
     *   "latitude": 17.4,
     *   "longitude": 78.5,
     *   "current": {
     *     "time": "2026-04-06T09:00",
     *     "temperature_2m": 28.4
     *   }
     * }
     * </pre>
     */
    static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=17.38&longitude=78.47&current=temperature_2m";

    /**
     * Makes a GET request to the Open-Meteo weather API and logs the result.
     *
     * <p>When {@code api.request.logging.rest-template.auto-capture-enabled=true}
     * the entire request/response is captured automatically by the injected
     * {@code RestTemplateLoggingInterceptor} — no manual
     * {@code collector.addLog(...)} call is needed here.</p>
     *
     * <p>The response is logged via the SLF4J {@link #log} at {@code INFO} level
     * for quick local visibility during the demo run.</p>
     *
     * @return the raw response entity from the weather API
     */
    public ResponseEntity<Object> getWeather() {
        log.info("[DemoConfiguration] Calling Open-Meteo weather API: {}", WEATHER_URL);
        ResponseEntity<Object> response = restTemplate.getForEntity(WEATHER_URL, Object.class);
        log.info("[DemoConfiguration] Weather API response: HTTP {}", response.getStatusCode());
        return response;
    }
}
