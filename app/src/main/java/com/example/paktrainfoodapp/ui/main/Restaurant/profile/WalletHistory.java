package com.example.paktrainfoodapp.ui.main.Restaurant.profile;

public class WalletHistory {

    private String type;
    private String amount;
    private String date;
    private String orderId;
    private Long orderNumber;

    public WalletHistory() {}

    public WalletHistory(String type, String amount, String date, String orderId) {
        this(type, amount, date, orderId, null);
    }

    public WalletHistory(String type, String amount, String date, String orderId, Long orderNumber) {
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
    }

    /** Sequential order number (e.g. 1 -> "Order #0001"), or null if not resolved yet. */
    public Long getOrderNumber() {
        return orderNumber;
    }

    public String getType() {
        return type;
    }

    public String getAmount() {
        return amount;
    }

    public String getDate() {
        return date;
    }

    public String getOrderId() {
        return orderId;
    }
}