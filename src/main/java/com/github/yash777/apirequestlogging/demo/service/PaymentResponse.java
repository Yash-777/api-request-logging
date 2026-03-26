package com.github.yash777.apirequestlogging.demo.service;

/**
 * Response from {@link PaymentService} after processing a charge.
 */
public class PaymentResponse {

    private String txnId;
    private String status;
    private String orderId;
    private double amount;

    public PaymentResponse() {}

    public PaymentResponse(String txnId, String status, String orderId, double amount) {
        this.txnId   = txnId;
        this.status  = status;
        this.orderId = orderId;
        this.amount  = amount;
    }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}
