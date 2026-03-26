package com.github.yash777.apirequestlogging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * <h2>ApiRequestLoggingIntegrationTest</h2>
 *
 * <p>Integration tests that start a full Spring Boot context with the
 * logging starter enabled, then fire HTTP requests via {@link MockMvc}
 * to verify that the filters, collector, and demo controllers all wire
 * together correctly.</p>
 *
 * <p>The tests use {@link TestPropertySource} to force
 * {@code api.request.logging.enabled=true} so the starter is active
 * regardless of the default in {@code application.properties}.</p>
 *
 * @author Yash
 * @since 1.0.0
 */
@SpringBootTest(
    classes = com.github.yash777.apirequestlogging.demo.DemoApplication.class
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "api.request.logging.enabled=true",
    "api.request.logging.request-id-headers=X-Request-ID,request_id",
    "api.request.logging.log-request-body=true",
    "api.request.logging.log-response-body=true",
    "api.request.logging.log-headers=true",
    "api.request.logging.max-body-length=4096"
})
class ApiRequestLoggingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ── POST /api/orders ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders: returns 200 with orderId and txnId")
    void createOrder_returns200() throws Exception {
        mockMvc.perform(
                post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-ID", "integration-test-001")
                    .content("{\"customerId\":\"C-101\",\"itemName\":\"Laptop\",\"amount\":999.99}")
        )
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.orderId").exists())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.transactionId").exists())
        .andExpect(jsonPath("$.requestId").value("integration-test-001"));
    }

    @Test
    @DisplayName("POST /api/orders: no X-Request-ID header → UUID generated")
    void createOrder_noRequestId_uuidGenerated() throws Exception {
        mockMvc.perform(
                post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customerId\":\"C-202\",\"itemName\":\"Mouse\",\"amount\":29.99}")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").exists())  // UUID auto-generated
        .andExpect(jsonPath("$.orderId").exists());
    }

    @Test
    @DisplayName("POST /api/orders: fallback header request_id is also accepted")
    void createOrder_fallbackHeader_requestId() throws Exception {
        mockMvc.perform(
                post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("request_id", "fallback-header-test-002")
                    .content("{\"customerId\":\"C-303\",\"itemName\":\"Keyboard\",\"amount\":49.99}")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value("fallback-header-test-002"));
    }

    // ── GET /api/orders/{orderId} ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{orderId}: returns 200 with order details")
    void getOrder_returns200() throws Exception {
        mockMvc.perform(
                get("/api/orders/ORD-TEST-123")
                    .header("X-Request-ID", "integration-test-003")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value("ORD-TEST-123"))
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.requestId").value("integration-test-003"));
    }

    // ── POST /api/payments/charge ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/payments/charge: returns 200 with txnId")
    void charge_returns200() throws Exception {
        mockMvc.perform(
                post("/api/payments/charge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-ID", "integration-test-004")
                    .content("{\"orderId\":\"ORD-99\",\"amount\":250.00}")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.txnId").exists())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.orderId").value("ORD-99"));
    }

    // ── GET /api/payments/status/{txnId} ─────────────────────────────────

    @Test
    @DisplayName("GET /api/payments/status/{txnId}: returns 200 with CAPTURED status")
    void checkStatus_returns200() throws Exception {
        mockMvc.perform(
                get("/api/payments/status/TXN-ABCD1234")
                    .header("X-Request-ID", "integration-test-005")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.txnId").value("TXN-ABCD1234"))
        .andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    // ── Excluded paths — filter must NOT be triggered ─────────────────────

    @Test
    @DisplayName("GET /actuator/health: excluded — no filter overhead, returns 200")
    void actuatorHealth_isExcluded() throws Exception {
        // Actuator is in the exclude-paths list; both filters skip it.
        // The request must still succeed (no IllegalStateException from collector).
        mockMvc.perform(get("/actuator/health"))
               .andExpect(status().isOk());
    }
}
