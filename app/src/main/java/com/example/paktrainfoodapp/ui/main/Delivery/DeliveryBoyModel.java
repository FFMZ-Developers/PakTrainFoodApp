package com.example.paktrainfoodapp.ui.main.Delivery;

public class DeliveryBoyModel {

    private String orderId;
    private double totalPrice;
    private String docPath;
    private String status;
    private String passengerUid;
    public DeliveryBoyModel(String id, double v, String status, String path) {}

    public DeliveryBoyModel(String orderId, double totalPrice, String docPath) {
        this.orderId = orderId;
        this.totalPrice = totalPrice;
        this.docPath = docPath;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getDocPath() { return docPath; }
    public void setDocPath(String docPath) { this.docPath = docPath; }

    public String getStatus() { return status; }

    // Live train ETA (millis) - same field the restaurant/passenger
    // screens show, so the rider knows how long they actually have.
    private long trainEtaEndTime;
    public long getTrainEtaEndTime() { return trainEtaEndTime; }
    public void setTrainEtaEndTime(long trainEtaEndTime) { this.trainEtaEndTime = trainEtaEndTime; }
    public void setStatus(String status) { this.status = status; }

    public String getPassengerUid() { return passengerUid; }
    public void setPassengerUid(String passengerUid) { this.passengerUid = passengerUid; }

    // Module: human-readable sequential order number (see OrderNumberUtils).
    private Long orderNumber;
    public Long getOrderNumber() { return orderNumber; }
    public void setOrderNumber(Long orderNumber) { this.orderNumber = orderNumber; }


    // Module: which restaurant this order is with - shown on the order
    // card so it doesn't have to be looked up separately.
    private String restaurantName;
    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }
}





