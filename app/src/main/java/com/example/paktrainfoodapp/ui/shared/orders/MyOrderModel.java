package com.example.paktrainfoodapp.ui.shared.orders;

public class MyOrderModel {

    private String orderId;
    private String status;
    private String restaurantName;
    private String mealStation;
    private String dateText;
    private double totalPrice;

    public MyOrderModel() { }

    public MyOrderModel(String orderId,
                        String status,
                        String restaurantName,
                        String mealStation,
                        String dateText,
                        double totalPrice) {

        this.orderId = orderId;
        this.status = status;
        this.restaurantName = restaurantName;
        this.mealStation = mealStation;
        this.dateText = dateText;
        this.totalPrice = totalPrice;
    }

    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getRestaurantName() { return restaurantName; }
    public String getMealStation() { return mealStation; }
    public String getDateText() { return dateText; }
    public double getTotalPrice() { return totalPrice; }

    // Module: human-readable sequential order number.
    private Long orderNumber;
    public Long getOrderNumber() { return orderNumber; }
    public void setOrderNumber(Long orderNumber) { this.orderNumber = orderNumber; }
}
