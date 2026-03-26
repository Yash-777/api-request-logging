package com.github.yash777.apirequestlogging.demo.service;

/**
 * Payload sent by {@link OrderService} to {@link PaymentService}
 * to charge for a confirmed order.
 */
public class PaymentRequest {

    private String orderId;
    private double amount;
    private String currency;

    public PaymentRequest() {}

    public PaymentRequest(String orderId, double amount) {
        this.orderId  = orderId;
        this.amount   = amount;
        this.currency = "INR";
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
