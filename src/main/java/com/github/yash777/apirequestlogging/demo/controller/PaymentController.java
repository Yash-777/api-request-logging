package com.github.yash777.apirequestlogging.demo.controller;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;
import com.github.yash777.apirequestlogging.demo.service.PaymentRequest;
import com.github.yash777.apirequestlogging.demo.service.PaymentResponse;
import com.github.yash777.apirequestlogging.demo.service.PaymentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>PaymentController</h2>
 *
 * <p>Demo REST controller that exposes direct payment endpoints.
 * Demonstrates that {@link RequestLogCollector} works independently
 * of {@link OrderService} — any service/controller can use it
 * to record third-party call logs.</p>
 *
 * <h3>Endpoints</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr>
 *     <td>POST</td><td>/api/payments/charge</td>
 *     <td>Direct payment charge — single gateway call</td>
 *   </tr>
 *   <tr>
 *     <td>GET</td><td>/api/payments/status/{txnId}</td>
 *     <td>Check payment transaction status</td>
 *   </tr>
 * </table>
 *
 * <h3>cURL examples</h3>
 * <pre>
 * # Charge directly (2 log sections: INCOMING + PaymentGateway/charge)
 * curl -X POST http://localhost:8080/api/payments/charge \
 *      -H "Content-Type: application/json" \
 *      -H "X-Request-ID: pay-trace-001" \
 *      -d '{"orderId":"ORD-99","amount":250.00}'
 *
 * # Check status (2 log sections: INCOMING + PaymentGateway/status)
 * curl http://localhost:8080/api/payments/status/TXN-ABCD1234 \
 *      -H "X-Request-ID: pay-trace-002"
 * </pre>
 *
 * <p>This controller is active only when
 * {@code api.request.logging.enabled=true} is set.</p>
 *
 * @author Yash
 * @since 1.0.0
 * @see PaymentService
 * @see OrderController
 */
@RestController
@RequestMapping("/api/payments")
@ConditionalOnDemoEnvironment             // Apply your combined conditions here
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Charges a payment directly, bypassing order orchestration.
     *
     * <p>Demonstrates a minimal two-section log:
     * INCOMING (from the filter) + PaymentGateway/charge (from {@link PaymentService}).</p>
     *
     * @param request payment details including {@code orderId} and {@code amount}
     * @return {@code 200 OK} with the {@link PaymentResponse} including transaction ID
     */
    @PostMapping("/charge")
    public ResponseEntity<PaymentResponse> charge(@RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.charge(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Checks the status of an existing payment transaction.
     *
     * <p>The correlation ID is available on the thread via
     * {@link RequestLogCollector#currentRequestId()} — useful to echo
     * back in the response header for client-side tracing.</p>
     *
     * @param txnId path variable from {@code /api/payments/status/{txnId}}
     * @return {@code 200 OK} with the current payment status
     */
    @GetMapping("/status/{txnId}")
    public ResponseEntity<PaymentResponse> checkStatus(@PathVariable String txnId) {
        PaymentResponse response = paymentService.checkStatus(txnId);
        return ResponseEntity.ok(response);
    }
}
