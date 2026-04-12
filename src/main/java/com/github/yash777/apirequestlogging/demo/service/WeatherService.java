package com.github.yash777.apirequestlogging.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;

/**
 * <h2>WeatherService</h2>
 *
 * <p>Demo {@link Service} that exercises the
 * <strong>api-request-logging-spring-boot-starter</strong>'s outbound
 * {@link RestTemplate} capture feature by calling the free
 * <a href="https://open-meteo.com/">Open-Meteo</a> weather API.</p>
 *
 * <p>Active only when {@code DemoApplication.main()} is the JVM entry point
 * (guarded by {@link ConditionalOnDemoEnvironment}). Consumer applications
 * that depend on this starter as a Maven artifact never activate this
 * service.</p>
 *
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 * <h3>RestTemplate — why it is injected here, not created with {@code new}</h3>
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 *
 * <p>{@link RestTemplate} is <strong>not a Spring bean by default</strong>. It
 * is a plain utility class (like {@code JdbcTemplate}) with no stereotype
 * annotations, so Spring's component scan never registers it automatically.
 * To use it as a managed, injectable object it must be declared explicitly:</p>
 *
 * <pre>
 * // ✅  Declared as @Bean in RestTemplateConfig — Spring-managed,
 * //     injectable, and eligible for post-processing.
 * &#64;Bean
 * public RestTemplate demoRestTemplate() { return new RestTemplate(); }
 *
 * // ❌  Created with new inside a method — invisible to Spring,
 * //     never intercepted, never injectable elsewhere.
 * RestTemplate rt = new RestTemplate();
 * </pre>
 *
 * <p>Because {@code demoRestTemplate} is a Spring-managed bean, the starter's
 * {@code RestTemplateLoggingBeanPostProcessor} detects it at startup and —
 * when {@code api.request.logging.rest-template.auto-capture-enabled=true} —
 * automatically wraps it with a {@code BufferingClientHttpRequestFactory}
 * and injects a {@code RestTemplateLoggingInterceptor} before the first
 * call is made.</p>
 *
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 * <h3>Deprecation context</h3>
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 *
 * <p>{@link RestTemplate} was deprecated in
 * <strong>Spring Framework 6.1 / Spring Boot 3.2</strong> in favour of
 * the fluent {@code RestClient} (synchronous) and {@code WebClient}
 * (reactive). This service intentionally retains {@code RestTemplate} to
 * showcase the starter's auto-capture behaviour on the older, widely-used
 * API. A {@code RestClient}-based equivalent is provided in
 * {@code WeatherRestClientService}.</p>
 *
 * <pre>
 * // Legacy — RestTemplate (Spring Boot &lt; 3.2)
 * ResponseEntity&lt;Object&gt; r =
 *     restTemplate.getForEntity(WEATHER_URL, Object.class);
 *
 * // Modern — RestClient (Spring Boot 3.2+)
 * Object body = restClient.get()
 *     .uri(WEATHER_URL)
 *     .retrieve()
 *     .body(Object.class);
 * </pre>
 *
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 * <h3>Logger routing</h3>
 * <!-- ═══════════════════════════════════════════════════════════════ -->
 *
 * <p>The SLF4J logger is created with the name returned by
 * {@link ApiRequestLoggingProperties.LoggerProperties#getName()}, which
 * defaults to {@code api.request.logging}. This keeps all demo output
 * under a single, configurable logger name:</p>
 *
 * <pre>
 * # application.properties
 * logging.level.api.request.logging=INFO
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 * @see com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment
 * @see com.github.yash777.apirequestlogging.demo.config.RestTemplateConfig
 * @see com.github.yash777.apirequestlogging.resttemplate.RestTemplateLoggingBeanPostProcessor
 * @see <a href="https://docs.spring.io/spring-framework/reference/integration/rest-clients.html">
 *      Spring REST Clients reference (RestClient / RestTemplate)</a>
 * @see <a href="https://open-meteo.com/en/docs">Open-Meteo API documentation</a>
 */
@Service
@ConditionalOnDemoEnvironment
public class WeatherService {

    // ══════════════════════════════════════════════════════════════════
    //  LOGGER
    // ══════════════════════════════════════════════════════════════════

    /**
     * SLF4J logger whose name is driven by
     * {@link ApiRequestLoggingProperties.LoggerProperties#getName()}.
     *
     * <p>Defaults to {@code api.request.logging}. All log statements in
     * this service appear under that name so they can be controlled from
     * a single {@code logging.level.*} entry without touching this
     * class.</p>
     */
    private final Logger log;

    // ══════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ══════════════════════════════════════════════════════════════════

    /**
     * Constructor injection — Spring fully constructs and binds
     * {@link ApiRequestLoggingProperties} before invoking this constructor,
     * guaranteeing that {@code properties.getLogger().getName()} is
     * non-null and populated at the time the logger is created.
     *
     * @param properties externalized starter configuration; must not be
     *                   {@code null}
     */
    public WeatherService(ApiRequestLoggingProperties properties) {
        this.log = LoggerFactory.getLogger(properties.getLogger().getName());
    }

    // ══════════════════════════════════════════════════════════════════
    //  INJECTED FIELDS
    // ══════════════════════════════════════════════════════════════════

    /**
     * The Spring-managed {@link RestTemplate} bean declared in
     * {@link com.github.yash777.apirequestlogging.demo.config.RestTemplateConfig#demoRestTemplate()}.
     *
     * <!-- Why @Lazy? -->
     * <h4>Why {@code @Lazy}?</h4>
     *
     * <p>{@code @Lazy} instructs Spring to defer the injection of this
     * field until it is <em>first accessed</em> at runtime, rather than
     * resolving it eagerly during context startup. This ordering
     * guarantee matters here because:</p>
     *
     * <ol>
     *   <li>The starter's {@code RestTemplateLoggingBeanPostProcessor}
     *       runs during the <em>post-processing phase</em> of context
     *       refresh, after all {@code @Bean} methods have been called but
     *       before any application code executes.</li>
     *   <li>Without {@code @Lazy}, Spring could inject the
     *       {@code RestTemplate} reference <em>before</em> the
     *       post-processor has had a chance to wrap it with the
     *       {@code RestTemplateLoggingInterceptor}, resulting in a bean
     *       that silently skips logging.</li>
     *   <li>With {@code @Lazy}, the proxy is resolved only when
     *       {@link #getWeather()} is first called — at which point the
     *       post-processor has already finished and the interceptor is
     *       in place.</li>
     * </ol>
     *
     * <p>In short: {@code @Lazy} ensures the interceptor is always
     * attached before the first outbound HTTP call is made.</p>
     *
     * <!-- Why @Autowired (field injection) instead of constructor injection? -->
     * <h4>Field injection vs. constructor injection</h4>
     *
     * <p>Constructor injection is generally preferred for mandatory
     * dependencies (easier to test, guarantees non-null). Field injection
     * with {@code @Autowired @Lazy} is used here solely because
     * {@code @Lazy} on a constructor parameter does not carry the same
     * proxy semantics as {@code @Lazy} on a field — the lazy-proxy trick
     * only works reliably with field or setter injection.</p>
     *
     * @deprecated {@link RestTemplate} is deprecated since Spring
     *             Framework 6.1 / Spring Boot 3.2. Prefer
     *             {@code RestClient} for new code.
     */
    @Autowired
    @Lazy
    private RestTemplate restTemplate;

    // ══════════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Open-Meteo free weather API endpoint pre-configured for
     * <strong>Hyderabad, India</strong> (latitude 17.38 °N,
     * longitude 78.47 °E).
     *
     * <p>Returns the current {@code temperature_2m} (air temperature
     * at 2 m above ground level) without any authentication or API key.
     * Suitable for local demo and integration testing.</p>
     *
     * <p>Example response fragment:</p>
     * <pre>
     * {
     *   "latitude":  17.4,
     *   "longitude": 78.5,
     *   "current": {
     *     "time":           "2026-04-11T09:00",
     *     "temperature_2m": 28.4
     *   }
     * }
     * </pre>
     *
     * @see <a href="https://api.open-meteo.com/v1/forecast?latitude=17.38&amp;longitude=78.47&amp;current=temperature_2m">
     *      Live endpoint (Hyderabad)</a>
     */
    static final String WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=17.38&longitude=78.47&current=temperature_2m";

    // ══════════════════════════════════════════════════════════════════
    //  DEMO METHODS
    // ══════════════════════════════════════════════════════════════════

    /**
     * Makes a synchronous GET request to the Open-Meteo weather API via
     * the injected {@link RestTemplate} and returns the raw response.
     *
     * <!-- How RestTemplate executes the call -->
     * <h4>What {@code RestTemplate.getForEntity} does</h4>
     *
     * <ol>
     *   <li>Opens an HTTP connection to {@link #WEATHER_URL} using the
     *       underlying {@code ClientHttpRequestFactory}
     *       ({@code SimpleClientHttpRequestFactory} by default, or
     *       {@code BufferingClientHttpRequestFactory} after the
     *       post-processor wraps it).</li>
     *   <li>Passes the request through each registered
     *       {@code ClientHttpRequestInterceptor} in order (the
     *       {@code RestTemplateLoggingInterceptor} is one of these).</li>
     *   <li>Deserializes the response body into the requested type
     *       ({@code Object.class} here — produces a {@code Map} when
     *       the content type is {@code application/json}).</li>
     *   <li>Returns a {@link ResponseEntity} containing the status code,
     *       headers, and deserialized body.</li>
     * </ol>
     *
     * <!-- Auto-capture -->
     * <h4>Automatic logging by the starter</h4>
     *
     * <p>When {@code api.request.logging.rest-template.auto-capture-enabled=true}
     * the entire outbound request and response are captured by the injected
     * {@code RestTemplateLoggingInterceptor} — no manual
     * {@code collector.addLog(...)} call is needed inside this method.
     * The captured entry appears in the structured log block:</p>
     *
     * <pre>
     * ── https://api.open-meteo.com/v1/forecast?... [14:32:05.042]
     *    httpMethod: GET
     *    request:    (no body)
     *    response:   {"latitude":17.4,"longitude":78.5,"current":{...}}
     *    elapsed:    312 ms
     * </pre>
     *
     * @return a {@link ResponseEntity} whose body is a {@code Map}
     *         deserialized from the Open-Meteo JSON response;
     *         never {@code null}
     */
    public ResponseEntity<Object> getWeather() {
        log.info("[WeatherService] Calling Open-Meteo weather API → {}", WEATHER_URL);
        ResponseEntity<Object> response = restTemplate.getForEntity(WEATHER_URL, Object.class);
        log.info("[WeatherService] Weather API response: HTTP {}", response.getStatusCode());
        return response;
    }
    
    /**
     * Fetches the current weather conditions for the given geographic coordinates
     * from the <a href="https://open-meteo.com/">Open-Meteo</a> public forecast API.
     *
     * <p>The request is dispatched as an HTTP {@code GET} to:
     * <pre>
     * https://api.open-meteo.com/v1/forecast
     *     ?latitude={lat}
     *     &longitude={lon}
     *     &current_weather=true
     * </pre>
     *
     * <p>URI template variables ({@code {lat}}, {@code {lon}}) are resolved by
     * {@link RestTemplate} using the varargs overload of
     * {@link RestTemplate#exchange(String, HttpMethod, HttpEntity, Class, Object...)},
     * which avoids manual string concatenation and ensures proper URL encoding.
     *
     * <p><b>Response type — {@code Object.class}:</b><br>
     * The response body is deserialized into a raw {@link Object} (backed by a
     * {@link java.util.LinkedHashMap} at runtime when Jackson is on the classpath).
     * This is intentional for exploratory or loosely-typed use cases where the
     * full API schema is not yet modelled as a dedicated DTO.
     * Callers may cast the body to {@code Map<String, Object>} to traverse the
     * response structure:
     * <pre>{@code
     * Map<String, Object> body = (Map<String, Object>) response.getBody();
     * Map<String, Object> current = (Map<String, Object>) body.get("current_weather");
     * Double temperature = (Double) current.get("temperature");
     * }</pre>
     * For type-safe access, consider replacing {@code Object.class} with a
     * dedicated response DTO (e.g. {@code WeatherResponse.class}).
     *
     * <p><b>Why {@link ResponseEntity} is returned (not just the body):</b><br>
     * Returning {@link ResponseEntity} gives the caller full access to:
     * <ul>
     *   <li>HTTP status code — {@code response.getStatusCode()}</li>
     *   <li>Response headers — {@code response.getHeaders()}</li>
     *   <li>Deserialized body — {@code response.getBody()}</li>
     * </ul>
     *
     * <p><b>Example response JSON from Open-Meteo:</b>
     * <pre>{@code
     * {
     *   "latitude": 17.385044,
     *   "longitude": 78.486671,
     *   "current_weather": {
     *     "temperature": 32.4,
     *     "windspeed": 14.2,
     *     "weathercode": 0,
     *     "time": "2026-04-12T10:00"
     *   }
     * }
     * }</pre>
     *
     * <p><b>Error handling:</b><br>
     * Non-2xx HTTP responses cause {@link RestTemplate} to throw a subclass of
     * {@link org.springframework.web.client.RestClientException}:
     * <ul>
     *   <li>{@link org.springframework.web.client.HttpClientErrorException} — 4xx errors</li>
     *   <li>{@link org.springframework.web.client.HttpServerErrorException} — 5xx errors</li>
     *   <li>{@link org.springframework.web.client.ResourceAccessException} — network/timeout failure</li>
     * </ul>
     *
     * @param lat geographic latitude in decimal degrees  (e.g. {@code 17.385} for Hyderabad)
     * @param lon geographic longitude in decimal degrees (e.g. {@code 78.486} for Hyderabad)
     * @return a {@link ResponseEntity} wrapping the raw deserialized response body as
     *         {@link Object} (typically a {@link java.util.LinkedHashMap} at runtime),
     *         along with the HTTP status and response headers; never {@code null}
     *
     * @throws org.springframework.web.client.HttpClientErrorException if the API returns 4xx
     * @throws org.springframework.web.client.HttpServerErrorException if the API returns 5xx
     * @throws org.springframework.web.client.ResourceAccessException  on network/timeout failure
     *
     * @see RestTemplate#exchange(String, HttpMethod, HttpEntity, Class, Object...)
     * @see ResponseEntity
     * @see <a href="https://open-meteo.com/en/docs">Open-Meteo API Docs</a>
     */
    public ResponseEntity<Object> getWeather(double lat, double lon) {
        String url = "https://api.open-meteo.com/v1/forecast"
                   + "?latitude={lat}&longitude={lon}&current_weather=true";

        ResponseEntity<Object> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,               // no request body / headers needed — public API
                Object.class,       // raw deserialization → LinkedHashMap at runtime
                lat,                // {lat}  URI variable
                lon                 // {lon}  URI variable
        );

        return response;
    }
}