package com.github.yash777.apirequestlogging.demo.service;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;
import com.github.yash777.apirequestlogging.demo.controller.OrderRequest;
import com.github.yash777.apirequestlogging.demo.controller.OrderResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * <h2>OrderService</h2>
 *
 * <p>Demo service that simulates the full order-creation flow:
 * validate → reserve inventory → charge payment → confirm order.</p>
 *
 * <p>This service demonstrates the <strong>chain-logging pattern</strong>:
 * multiple downstream calls within a single HTTP request are each logged
 * under their own timestamped key in {@link RequestLogCollector}, producing
 * a complete chronological trace of everything that happened during the
 * request.</p>
 *
 * <h3>Sample console output for one POST /api/orders call</h3>
 * <pre>
 * =========== Request Logs [req-id: my-id-001] ===========
 *
 * ── INCOMING
 *    requestId:            my-id-001
 *    threadName:           http-nio-8080-exec-2
 *    url:                  /api/orders
 *    httpMethod:           POST
 *    timestamp:            25/3/2026, 10:32:15 am
 *    headers:              {"content-type":"application/json","x-request-id":"my-id-001"}
 *    requestBody:          {"customerId":"C-101","itemName":"Laptop","amount":999.99}
 *    responseStatus:       200
 *    responseBody:         {"orderId":"ORD-...","status":"CONFIRMED",...}
 *    requestProcessedTime: 0h 0m 0s 87ms
 *
 * ── InventoryService/reserve [10:32:15.031]
 *    itemName:  Laptop
 *    reserved:  true
 *
 * ── PaymentGateway/charge [10:32:15.042]
 *    request:   {"orderId":"ORD-...","amount":999.99,"currency":"INR"}
 *    response:  {"txnId":"TXN-...","status":"SUCCESS","orderId":"ORD-...","amount":999.99}
 *
 * ════════════════════════════════════════════════════════════
 * </pre>
 *
 * <p>This bean is active only when
 * {@code api.request.logging.enabled=true} is set.</p>
 *
 * @author Yash
 * @since 1.0.0
 * @see PaymentService
 * @see RequestLogCollector
 */
@Service
@ConditionalOnDemoEnvironment             // Apply your combined 3 conditions here
public class OrderService {

    /**
     * CGLIB proxy of the request-scoped {@link RequestLogCollector}.
     * Safe to hold in a singleton — the proxy resolves the real bean per thread.
     */
    private final RequestLogCollector collector;

    /** Payment service — injected to simulate a downstream charge call. */
    private final PaymentService paymentService;

    /**
     * Constructor injection — all dependencies are explicit and {@code final}.
     *
     * @param collector      CGLIB proxy of {@link RequestLogCollector}
     * @param paymentService downstream payment service
     */
    @Autowired
    public OrderService(RequestLogCollector collector, PaymentService paymentService) {
        this.collector      = collector;
        this.paymentService = paymentService;
    }

    /**
     * Creates a new order through the full Order → Inventory → Payment pipeline.
     *
     * <ol>
     *   <li>Generate an {@code orderId}</li>
     *   <li>Log a simulated inventory reservation call</li>
     *   <li>Delegate to {@link PaymentService#charge(PaymentRequest)} which logs
     *       its own gateway call</li>
     *   <li>Build and return the {@link OrderResponse}</li>
     * </ol>
     *
     * <p>All three log entries (INCOMING + InventoryService + PaymentGateway)
     * will appear in the single console block printed by
     * {@link com.github.yash777.apirequestlogging.filter.ApiLoggingFilter}
     * at the end of the request.</p>
     *
     * @param request validated order request from the controller
     * @return confirmed order response including transaction ID and request ID
     */
    public OrderResponse createOrder(OrderRequest request) {

        // ── Step 1: Generate order ID ─────────────────────────────────────
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // ── Step 2: Log a simulated inventory reservation ─────────────────
        // buildRetryKey stamps the current time → "InventoryService/reserve [HH:mm:ss.SSS]"
        // If you had retries each call would produce a distinct key.
        String invKey = collector.buildRetryKey("InventoryService/reserve");
        collector.addLog(invKey, "itemName", request.getItemName());
        // Simulate 10 ms inventory check
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        collector.addLog(invKey, "reserved", "true");

        // ── Step 3: Charge payment (PaymentService logs its own gateway call) ──
        PaymentRequest payReq = new PaymentRequest(orderId, request.getAmount());
        PaymentResponse payRes = paymentService.charge(payReq);

        // ── Step 4: Build response ────────────────────────────────────────
        OrderResponse response = new OrderResponse();
        response.setOrderId(orderId);
        response.setStatus("CONFIRMED");
        response.setCustomerId(request.getCustomerId());
        response.setItemName(request.getItemName());
        response.setAmount(request.getAmount());
        response.setTransactionId(payRes.getTxnId());
        // Echo the correlation ID so the caller can reference it
        response.setRequestId(RequestLogCollector.currentRequestId());

        return response;
    }

    /**
     * Fetches order details by ID (simulated — returns synthetic data).
     *
     * <p>Demonstrates GET request logging; no downstream calls are made so
     * only the INCOMING block appears in the log.</p>
     *
     * @param orderId the order identifier from the path variable
     * @return synthetic order details
     */
    public OrderResponse getOrder(String orderId) {

        // Log a simulated DB lookup
        String dbKey = collector.buildRetryKey("OrderDB/findById");
        collector.addLog(dbKey, "query", "SELECT * FROM orders WHERE id = '" + orderId + "'");
        // Simulate 5 ms DB round-trip
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}
        collector.addLog(dbKey, "rowsFound", "1");

        OrderResponse response = new OrderResponse();
        response.setOrderId(orderId);
        response.setStatus("CONFIRMED");
        response.setCustomerId("C-101");
        response.setItemName("Laptop");
        response.setAmount(999.99);
        response.setTransactionId("TXN-DEMO1234");
        response.setRequestId(RequestLogCollector.currentRequestId());
        return response;
    }
}
