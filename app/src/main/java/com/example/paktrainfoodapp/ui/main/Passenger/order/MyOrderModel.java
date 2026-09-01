package com.example.paktrainfoodapp.ui.main.Passenger.order;

public class MyOrderModel {

    private String orderId;
    private String status;
    private String restaurantName;
    private String mealStation;
    private String dateText;
    private double totalPrice;

    // Module 2 - the live train ETA to the meal station (millis, epoch time).
    // 0 means "not computed yet".
    private long trainEtaEndTime;

    public MyOrderModel() { }

    public MyOrderModel(String orderId,
                        String status,
                        String restaurantName,
                        String mealStation,
                        String dateText,
                        double totalPrice,
                        long trainEtaEndTime) {

        this.orderId = orderId;
        this.status = status;
        this.restaurantName = restaurantName;
        this.mealStation = mealStation;
        this.dateText = dateText;
        this.totalPrice = totalPrice;
        this.trainEtaEndTime = trainEtaEndTime;
    }

    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getRestaurantName() { return restaurantName; }
    public String getMealStation() { return mealStation; }
    public String getDateText() { return dateText; }
    public double getTotalPrice() { return totalPrice; }
    public long getTrainEtaEndTime() { return trainEtaEndTime; }

    // Module: human-readable sequential order number (see OrderNumberUtils).
    private Long orderNumber;
    public Long getOrderNumber() { return orderNumber; }
    public void setOrderNumber(Long orderNumber) { this.orderNumber = orderNumber; }
}
