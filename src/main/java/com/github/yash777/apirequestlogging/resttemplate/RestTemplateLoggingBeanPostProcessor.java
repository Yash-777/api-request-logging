package com.github.yash777.apirequestlogging.resttemplate;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.collector.RequestLogCollectorApi;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link BeanPostProcessor} that automatically injects a
 * {@link RestTemplateLoggingInterceptor} into every Spring-managed
 * {@link RestTemplate} bean after it has been initialised.
 *
 * <h3>Activation</h3>
 * <p>Active only when both of these properties are {@code true}:</p>
 * <pre>
 * api.request.logging.enabled=true
 * api.request.logging.rest-template.auto-capture-enabled=true
 * </pre>
 *
 * <h3>Limitation</h3>
 * <p>Only Spring-managed {@code RestTemplate} beans are intercepted.
 * {@code RestTemplate} instances created with {@code new RestTemplate()} inside a
 * method body are invisible to Spring and will not be captured automatically.
 * Those must be logged manually via {@link RequestLogCollector#addLog}.</p>
 *
 * @author Yash
 * @since 1.1.0
 */
@Component
@ConditionalOnProperty(
    prefix = "api.request.logging",
    name   = "enabled",
    havingValue = "true"
)
public class RestTemplateLoggingBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(RestTemplateLoggingBeanPostProcessor.class);

    private final RequestLogCollectorApi collector;
    private final ApiRequestLoggingProperties properties;

    public RestTemplateLoggingBeanPostProcessor(RequestLogCollectorApi      collector,
                                                ApiRequestLoggingProperties properties) {
        this.collector  = collector;
        this.properties = properties;
    }

    /**
     * After each bean is fully initialised, check if it is a {@link RestTemplate}.
     * If so — and if auto-capture is enabled — inject the logging interceptor,
     * wrapping the request factory with {@code BufferingClientHttpRequestFactory}
     * so the response body can be read by both the interceptor and RestTemplate.
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {

        if (!properties.getRestTemplate().isAutoCaptureEnabled()) {
            return bean;
        }

        if (bean instanceof RestTemplate) {
            RestTemplate restTemplate = (RestTemplate) bean;

            // Wrap the existing factory in a buffering factory so the response
            // body can be read more than once (interceptor + RestTemplate deserialiser).
            if (!(restTemplate.getRequestFactory()
                    instanceof org.springframework.http.client.BufferingClientHttpRequestFactory)) {
                restTemplate.setRequestFactory(
                    new org.springframework.http.client.BufferingClientHttpRequestFactory(
                            restTemplate.getRequestFactory()));
            }

            // Add our interceptor (avoid duplicates on context refresh)
            List<org.springframework.http.client.ClientHttpRequestInterceptor> interceptors =
                    new ArrayList<>(restTemplate.getInterceptors());
            boolean alreadyAdded = interceptors.stream()
                    .anyMatch(i -> i instanceof RestTemplateLoggingInterceptor);
            if (!alreadyAdded) {
                interceptors.add(new RestTemplateLoggingInterceptor(collector, properties));
                restTemplate.setInterceptors(interceptors);
                log.debug("[api-request-logging] Injected RestTemplateLoggingInterceptor "
                        + "into RestTemplate bean '{}'", beanName);
            }
        }
        return bean;
    }
}
