package com.github.yash777.apirequestlogging.resttemplate;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.properties.ApiRequestLoggingProperties;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@link ClientHttpRequestInterceptor} that automatically logs every outgoing
 * {@code RestTemplate} request and response into {@link RequestLogCollector}.
 *
 * <h3>Auto-registration</h3>
 * <p>When {@code api.request.logging.rest-template.auto-capture-enabled=true},
 * a {@link RestTemplateLoggingBeanPostProcessor} injects this interceptor into every
 * {@code RestTemplate} bean registered in the Spring ApplicationContext.</p>
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Only Spring-managed {@code RestTemplate} beans are intercepted. Instances created
 *       with {@code new RestTemplate()} inside a method body are invisible to Spring.</li>
 *   <li>For {@code WebClient} (reactive), use an {@code ExchangeFilterFunction} instead.</li>
 * </ul>
 *
 * <h3>Sample output</h3>
 * <pre>
 * ── https://payment-service/charge [14:32:05.042]
 *    request:   {"orderId":"ORD-1","amount":500.0}
 *    response:  {"txnId":"TXN-99","status":"SUCCESS"}
 * </pre>
 *
 * @author Yash
 * @since 1.1.0
 */
public class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {

    private final RequestLogCollector collector;
    private final ApiRequestLoggingProperties properties;

    public RestTemplateLoggingInterceptor(RequestLogCollector collector,
                                          ApiRequestLoggingProperties properties) {
        this.collector  = collector;
        this.properties = properties;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution)
            throws IOException {

        ApiRequestLoggingProperties.RestTemplateProperties rtProps =
                properties.getRestTemplate();

        // Build a timestamped key: "https://host/path [HH:mm:ss.SSS]"
        String key = collector.buildRetryKey(request.getURI().toString());

        // Log outgoing request body BEFORE the call
        if (rtProps.isLogRequestBody() && body != null && body.length > 0) {
            String reqBody = new String(body, StandardCharsets.UTF_8);
            collector.addLog(key, RequestLogCollector.LOG_REQUEST, truncate(reqBody, rtProps));
        } else {
            collector.addLog(key, RequestLogCollector.LOG_REQUEST, "(no body)");
        }

        // Execute the actual HTTP call
        ClientHttpResponse response = execution.execute(request, body);

        // Buffer the response body so both the logger and RestTemplate can read it
        byte[] responseBytes = StreamUtils.copyToByteArray(response.getBody());

        // Log the response body AFTER the call
        if (rtProps.isLogResponseBody()) {
            String resBody = responseBytes.length > 0
                    ? new String(responseBytes, StandardCharsets.UTF_8)
                    : "(empty)";
            collector.addLog(key, RequestLogCollector.LOG_RESPONSE,
                             truncate(resBody, rtProps));
        } else {
            collector.addLog(key, RequestLogCollector.LOG_RESPONSE,
                             "(response body logging disabled)");
        }

        // Return a wrapper that serves the buffered bytes to RestTemplate
        return new BufferedClientHttpResponse(response, responseBytes);
    }

    private static String truncate(String body,
            ApiRequestLoggingProperties.RestTemplateProperties props) {
        int max = props.getMaxBodyLength();
        if (max < 0 || body == null || body.length() <= max) return body;
        return body.substring(0, max) + " [TRUNCATED at " + max + " chars]";
    }
}
