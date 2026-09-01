package com.example.paktrainfoodapp.ui.main.Passenger.order;

public class OrderModel {
    private String orderId;
    private double totalPrice;
    private String status;
    private long trainEtaEndTime;
    public OrderModel() {}

    public OrderModel(String orderId, double totalPrice) {
        this.orderId = orderId;
        this.totalPrice = totalPrice;
    }
    public OrderModel(String orderId,
                      double totalPrice,
                      String status) {

        this.orderId = orderId;
        this.totalPrice = totalPrice;
        this.status = status;
    }

    public OrderModel(String orderId,
                      double totalPrice,
                      String status,
                      long trainEtaEndTime) {

        this.orderId = orderId;
        this.totalPrice = totalPrice;
        this.status = status;
        this.trainEtaEndTime = trainEtaEndTime;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }
    public double getTotalPrice() {
        return totalPrice;
    }

    public long getTrainEtaEndTime() {
        return trainEtaEndTime;
    }

    // Module: human-readable sequential order number (see OrderNumberUtils).
    private Long orderNumber;
    public Long getOrderNumber() { return orderNumber; }
    public void setOrderNumber(Long orderNumber) { this.orderNumber = orderNumber; }
}//