package com.github.yash777.apirequestlogging.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

/**
 * <h2>DemoApplication</h2>
 *
 * <p>Sample Spring Boot application that demonstrates the
 * <strong>api-request-logging-spring-boot-starter</strong> in action.</p>
 *
 * <h3>Running the demo</h3>
 * <pre>
 * mvn spring-boot:run
 * </pre>
 *
 * <h3>Try the endpoints</h3>
 * <pre>
 * # Place an order (triggers OrderService → PaymentService chain)
 * curl -X POST http://localhost:8080/api/orders \
 *      -H "Content-Type: application/json" \
 *      -H "X-Request-ID: my-correlation-id-001" \
 *      -d '{"customerId":"C-101","itemName":"Laptop","amount":999.99}'
 *
 * # Get an order by ID
 * curl http://localhost:8080/api/orders/ORD-12345 \
 *      -H "X-Request-ID: my-correlation-id-002"
 *
 * # Charge payment directly
 * curl -X POST http://localhost:8080/api/payments/charge \
 *      -H "Content-Type: application/json" \
 *      -H "X-Request-ID: my-correlation-id-003" \
 *      -d '{"orderId":"ORD-99","amount":250.00}'
 * </pre>
 *
 * <h3>What to observe in the console</h3>
 * <p>For each request you will see a structured log block like:</p>
 * <pre>
 * =========== Request Logs [req-id: my-correlation-id-001] ===========
 *
 * ── INCOMING
 *    requestId:            my-correlation-id-001
 *    threadName:           http-nio-8080-exec-1
 *    url:                  /api/orders
 *    httpMethod:           POST
 *    timestamp:            25/3/2026, 10:32:15 am
 *    headers:              {"content-type":"application/json","x-request-id":"..."}
 *    requestBody:          {"customerId":"C-101","itemName":"Laptop","amount":999.99}
 *    responseStatus:       200
 *    responseBody:         {"orderId":"ORD-...","status":"CONFIRMED"}
 *    requestProcessedTime: 0h 0m 0s 87ms
 *
 * ── PaymentService/charge [10:32:15.042]
 *    request:   {"orderId":"ORD-...","amount":999.99}
 *    response:  {"txnId":"TXN-...","status":"SUCCESS"}
 *
 * ════════════════════════════════════════════════════════════
 * </pre>
 *
 * @author Yash
 * @since 1.0.0
 */
@SpringBootApplication
public class DemoApplication {
	private static boolean nonConsumer = false;
	public static boolean getNonConsumer() {
		return nonConsumer;
	}
    public static void main(String[] args) {
        // SETTING THE FLAG MANUALLY: This is processed BEFORE Spring starts scanning Controllers/Services
        System.setProperty("internal.demo.bean.active", "true");
        System.setProperty("internal.app.non-consumer", "true");
        
        nonConsumer = true;
        
        SpringApplication.run(DemoApplication.class, args);
        
//        new SpringApplicationBuilder(DemoApplication.class)
//        .properties("internal.app.non-consumer=true") // Official way
//        .run(args);
    }
    
//    /**
//     * Internal dummy bean required to activate the demo filters.
//     */
//    @Bean(name = "dummyBean")
//    public String dummyBean() {
//        return "Active";
//    }
}
