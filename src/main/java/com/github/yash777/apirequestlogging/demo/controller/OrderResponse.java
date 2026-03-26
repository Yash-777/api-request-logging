package com.github.yash777.apirequestlogging.demo.controller;

/**
 * Response body for {@code POST /api/orders} and {@code GET /api/orders/{id}}.
 */
public class OrderResponse {

    private String orderId;
    private String status;
    private String customerId;
    private String itemName;
    private double amount;
    private String transactionId;
    private String requestId;

    // ── Constructors ──────────────────────────────────────────────────────

    public OrderResponse() {}

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    @Override
    public String toString() {
        return "OrderResponse{orderId='" + orderId + "', status='" + status
                + "', txnId='" + transactionId + "'}";
    }
}
