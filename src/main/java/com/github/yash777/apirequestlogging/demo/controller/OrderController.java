package com.github.yash777.apirequestlogging.demo.controller;

import com.github.yash777.apirequestlogging.collector.RequestLogCollector;
import com.github.yash777.apirequestlogging.demo.condition.ConditionalOnDemoEnvironment;
import com.github.yash777.apirequestlogging.demo.service.OrderService;

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
 * <h2>OrderController</h2>
 *
 * <p>Demo REST controller that exposes order management endpoints.
 * Used to demonstrate the full request/response logging lifecycle
 * provided by the <strong>api-request-logging-spring-boot-starter</strong>.</p>
 *
 * <h3>Endpoints</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr>
 *     <td>POST</td><td>/api/orders</td>
 *     <td>Create a new order — triggers Order → Inventory → Payment chain</td>
 *   </tr>
 *   <tr>
 *     <td>GET</td><td>/api/orders/{orderId}</td>
 *     <td>Fetch order details by ID</td>
 *   </tr>
 * </table>
 *
 * <h3>cURL examples</h3>
 * <pre>
 * # Create an order (full chain — 3 log sections in output)
 * curl -X POST http://localhost:8080/api/orders \
 *      -H "Content-Type: application/json" \
 *      -H "X-Request-ID: order-trace-001" \
 *      -d '{"customerId":"C-101","itemName":"Laptop","amount":999.99}'
 *
 * # Get order by ID (single INCOMING + DB lookup log)
 * curl http://localhost:8080/api/orders/ORD-ABC12345 \
 *      -H "X-Request-ID: order-trace-002"
 * </pre>
 *
 * <p>This controller is active only when
 * {@code api.request.logging.enabled=true} is set.</p>
 *
 * @author Yash
 * @since 1.0.0
 * @see OrderService
 * @see PaymentController
 */
@RestController
@RequestMapping("/api/orders")
//@ConditionalOnProperty(
//    prefix      = "api.request.logging",
//    name        = "enabled",
//    havingValue = "true"
//)
////Guard 1 — class must be present (always true inside this project itself)
//@ConditionalOnClass(name = "com.github.yash777.apirequestlogging.demo.DemoApplication")
////Guard 2 — property only set by pom.xml jvmArguments, never by a consumer
//@ConditionalOnProperty(
// prefix      = "api.request.logging",
// name        = "live-demo",
// havingValue = "true",
// matchIfMissing = false
//)

@AutoConfigureAfter(com.github.yash777.apirequestlogging.demo.DemoApplication.class) // Crucial: Wait for Main class beans
@ConditionalOnDemoEnvironment             // Apply your combined 3 conditions here
public class OrderController {

    private final OrderService orderService;

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order by running the full
     * Order → Inventory → Payment pipeline.
     *
     * <p>The request body is logged by {@link com.github.yash777.apirequestlogging.filter.ApiLoggingFilter}
     * automatically — no extra code needed in the controller.</p>
     *
     * <p>The correlation ID from the {@code X-Request-ID} header (or a generated
     * UUID) is available via {@link RequestLogCollector#currentRequestId()} and
     * is echoed back in the response body as {@code requestId}.</p>
     *
     * @param request incoming order details (deserialized from JSON request body)
     * @return {@code 200 OK} with the confirmed {@link OrderResponse}
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetches order details for the given {@code orderId}.
     *
     * <p>Demonstrates GET request logging — the INCOMING block captures
     * the URL path, method, headers, and timing even though there is no
     * request body to log.</p>
     *
     * @param orderId path variable from {@code /api/orders/{orderId}}
     * @return {@code 200 OK} with the order details
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        OrderResponse response = orderService.getOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
