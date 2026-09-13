package com.example.paktrainfoodapp.ui.main.Restaurant.profile;

public class WalletHistory {

    private String type;
    private String amount;
    private String date;
    private String orderId;
    private Long orderNumber;

    public WalletHistory() {}

    public WalletHistory(String type, String amount, String date, String orderId) {
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.orderId = orderId;
    }

    public WalletHistory(String type, String amount, String date, String orderId, Long orderNumber) {
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
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

    public Long getOrderNumber() {
        return orderNumber;
    }
}