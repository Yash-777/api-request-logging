package com.github.yash777.apirequestlogging.demo.service;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.collector.RequestLogCollectorApi;
import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * <h2>PaymentService</h2>
 *
 * <p>Demo service that simulates charging a payment gateway.
 * Demonstrates how to use {@link RequestLogCollector} inside a {@code @Service}
 * to capture outgoing third-party call request/response pairs with
 * retry-aware timestamped keys.</p>
 *
 * <h3>How RequestLogCollector works inside a @Service</h3>
 * <p>{@link RequestLogCollector} is request-scoped with a CGLIB proxy.
 * When this singleton {@code @Service} calls any method on the injected
 * {@code collector}, the proxy transparently delegates to the real
 * per-request instance bound to the current thread's
 * {@code RequestAttributes}.</p>
 *
 * <pre>
 *   this singleton  →  CGLIB proxy  →  real RequestLogCollector (per-request)
 * </pre>
 *
 * <h3>Retry-aware logging pattern</h3>
 * <pre>{@code
 * String key = collector.buildRetryKey("PaymentGateway/charge");
 * // key => "PaymentGateway/charge [14:32:05.001]"
 *
 * collector.addLog(key, "request", paymentRequest);
 * PaymentResponse res = gateway.post(...);         // real HTTP call here
 * collector.addLog(key, "response", res);
 * }</pre>
 * <p>Each retry call to {@link RequestLogCollector#buildRetryKey(String)}
 * produces a new timestamp-suffixed key, so every attempt gets its own
 * inner log map — none overwrite the previous.</p>
 *
 * <p>This bean is active only when
 * {@code api.request.logging.enabled=true} is set.</p>
 *
 * @author Yash
 * @since 1.0.0
 * @see RequestLogCollector#buildRetryKey(String)
 * @see RequestLogCollector#addLog(String, String, Object)
 */
@Service
@ConditionalOnDemoEnvironment             // Apply your combined conditions here
public class PaymentService {

    /**
     * CGLIB proxy of the request-scoped {@link RequestLogCollector}.
     * Safe to hold in a singleton — proxy resolves the real bean per-thread.
     */
    private final RequestLogCollectorApi collector;

    /**
     * Constructor injection — makes the dependency explicit and {@code final}.
     *
     * @param collector CGLIB proxy of {@link RequestLogCollector}
     */
    @Autowired
    public PaymentService(RequestLogCollectorApi collector) {
        this.collector = collector;
    }

    /**
     * Simulates a payment gateway charge call and logs the full
     * request/response pair under a timestamped retry-aware key.
     *
     * <p>In a real implementation, replace the simulated response with an
     * actual {@code RestTemplate} or {@code WebClient} HTTP call to your
     * payment provider.</p>
     *
     * <h4>Log output produced (visible in console)</h4>
     * <pre>
     * ── PaymentGateway/charge [14:32:05.042]
     *    request:   {"orderId":"ORD-abc","amount":999.99,"currency":"INR"}
     *    response:  {"txnId":"TXN-xyz","status":"SUCCESS","orderId":"ORD-abc","amount":999.99}
     * </pre>
     *
     * @param request the payment details to charge
     * @return simulated {@link PaymentResponse}
     */
    public PaymentResponse charge(PaymentRequest request) {

        // ── Build a retry-aware log key ───────────────────────────────────
        // Each call gets a unique "[HH:mm:ss.SSS]" suffix so retries are
        // kept as separate entries — not overwritten in the same map.
        String key = collector.buildRetryKey("PaymentGateway/charge");

        // ── Log the outgoing request BEFORE the call ──────────────────────
        collector.addLog(key, RequestLogCollector.LOG_REQUEST, request);

        // ── Simulate payment gateway call (replace with real HTTP call) ───
        PaymentResponse response = simulateGatewayCall(request);

        // ── Log the response AFTER the call ──────────────────────────────
        collector.addLog(key, RequestLogCollector.LOG_RESPONSE, response);

        return response;
    }

    /**
     * Simulates a payment status check and logs the request/response pair.
     *
     * <p>Demonstrates the convenience method
     * {@link RequestLogCollector#addRequestResponseLog(String, Object, Object)}
     * when both objects are available at the same point (no retry scenario).</p>
     *
     * @param txnId the transaction ID to check
     * @return simulated status response
     */
    public PaymentResponse checkStatus(String txnId) {

        String key = collector.buildRetryKey("PaymentGateway/status");

        // Outgoing "request" — just the txnId in this case
        collector.addLog(key, "txnId", txnId);

        // Simulate status lookup
        PaymentResponse response = new PaymentResponse(
                txnId, "CAPTURED", "ORD-UNKNOWN", 0.0);

        collector.addLog(key, RequestLogCollector.LOG_RESPONSE, response);

        return response;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Simulates a payment gateway HTTP response.
     * In production replace this with {@code RestTemplate.postForObject()} or
     * a {@code WebClient} reactive call to your actual gateway endpoint.
     *
     * @param req payment details
     * @return synthetic success response
     */
    private PaymentResponse simulateGatewayCall(PaymentRequest req) {
        // Simulate ~20 ms gateway latency
        try { Thread.sleep(20); } catch (InterruptedException ignored) {}

        String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new PaymentResponse(txnId, "SUCCESS", req.getOrderId(), req.getAmount());
    }
}
