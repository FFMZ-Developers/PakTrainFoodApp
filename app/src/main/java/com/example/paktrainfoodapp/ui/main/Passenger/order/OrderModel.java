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

    public void setTrainEtaEndTime(long trainEtaEndTime) {
        this.trainEtaEndTime = trainEtaEndTime;
    }

    // Module: human-readable sequential order number (see OrderNumberUtils).
    private Long orderNumber;
    public Long getOrderNumber() { return orderNumber; }
    public void setOrderNumber(Long orderNumber) { this.orderNumber = orderNumber; }


    // Module: which restaurant this order is with - shown on the order
    // card so it doesn't have to be looked up separately.
    private String restaurantName;
    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }

    // Module: shown on the order card (station + when it was placed),
    // matching the richer card design.
    private String mealStation;
    private long timestamp;

    public String getMealStation() { return mealStation; }
    public void setMealStation(String mealStation) { this.mealStation = mealStation; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}//