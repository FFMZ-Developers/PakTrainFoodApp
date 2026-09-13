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

    // Module: resolution details for cancelled/disputed orders - only
    // populated when relevant, so a normal in-progress order carries no
    // extra weight.
    private String rejectionReason;
    private String failureReason;
    private String disputeStatus;
    private Double disputeRestaurantShare, disputeRiderShare, disputePassengerRefund;
    private String disputeRestaurantReason, disputeRiderReason;
    private boolean paymentCaptured;

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getDisputeStatus() { return disputeStatus; }
    public void setDisputeStatus(String disputeStatus) { this.disputeStatus = disputeStatus; }

    public Double getDisputeRestaurantShare() { return disputeRestaurantShare; }
    public void setDisputeRestaurantShare(Double v) { this.disputeRestaurantShare = v; }

    public Double getDisputeRiderShare() { return disputeRiderShare; }
    public void setDisputeRiderShare(Double v) { this.disputeRiderShare = v; }

    public Double getDisputePassengerRefund() { return disputePassengerRefund; }
    public void setDisputePassengerRefund(Double v) { this.disputePassengerRefund = v; }

    public String getDisputeRestaurantReason() { return disputeRestaurantReason; }
    public void setDisputeRestaurantReason(String v) { this.disputeRestaurantReason = v; }

    public String getDisputeRiderReason() { return disputeRiderReason; }
    public void setDisputeRiderReason(String v) { this.disputeRiderReason = v; }

    public boolean isPaymentCaptured() { return paymentCaptured; }
    public void setPaymentCaptured(boolean paymentCaptured) { this.paymentCaptured = paymentCaptured; }
}
