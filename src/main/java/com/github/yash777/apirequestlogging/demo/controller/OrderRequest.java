package com.github.yash777.apirequestlogging.demo.controller;

/**
 * Incoming payload for {@code POST /api/orders}.
 *
 * <pre>{@code
 * curl -X POST http://localhost:8080/api/orders \
 *      -H "Content-Type: application/json" \
 *      -H "X-Request-ID: my-id-001" \
 *      -d '{"customerId":"C-101","itemName":"Laptop","amount":999.99}'
 * }</pre>
 */
public class OrderRequest {

    /** Customer identifier. Example: {@code "C-101"} */
    private String customerId;

    /** Name of the item being ordered. Example: {@code "Laptop"} */
    private String itemName;

    /** Amount to charge in the consumer's currency. Example: {@code 999.99} */
    private double amount;

    // ── Constructors ──────────────────────────────────────────────────────

    public OrderRequest() {}

    public OrderRequest(String customerId, String itemName, double amount) {
        this.customerId = customerId;
        this.itemName   = itemName;
        this.amount     = amount;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    @Override
    public String toString() {
        return "OrderRequest{customerId='" + customerId + "', itemName='" + itemName
                + "', amount=" + amount + "}";
    }
}
