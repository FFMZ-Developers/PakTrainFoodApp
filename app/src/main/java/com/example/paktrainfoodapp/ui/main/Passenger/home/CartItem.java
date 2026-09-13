package com.example.paktrainfoodapp.ui.main.Passenger.home;


import java.io.Serializable;

public class CartItem implements Serializable{

    private String itemId;
    private String restaurantId;
    private String restaurantName;
    private String name;
    private double price;
    private int quantity;
    private String size;
    private String imageUrl;
    private String description;
    private String mealStation;
    private String trainId;
    private String routeId;
    private String fromStation;
    private String toStation;
    private String trainName;

    public CartItem(String itemId, String restaurantId, String restaurantName,
                    String name, double price, int quantity, String size,
                    String imageUrl, String description,
                    String mealStation, String trainId, String routeId,
                    String fromStation, String toStation, String trainName) {

        this.itemId = itemId;
        this.restaurantId = restaurantId;
        this.restaurantName = restaurantName;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.size = size;
        this.imageUrl = imageUrl;
        this.description = description;
        this.mealStation = mealStation;
        this.trainId = trainId;
        this.routeId = routeId;
        this.fromStation = fromStation;
        this.toStation = toStation;
        this.trainName = trainName;
    }

    public String getKey() {
        return restaurantId + "_" + itemId + "_" + size;
    }

    public double getTotal() {
        return price * quantity;
    }

    // getters + setter
    public String getItemId() { return itemId; }
    public String getRestaurantId() { return restaurantId; }
    public String getRestaurantName() { return restaurantName; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public String getSize() { return size; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public String getMealStation() { return mealStation; }
    public String getTrainId() { return trainId; }
    public String getRouteId() { return routeId; }
    public String getFromStation() { return fromStation; }
    public String getToStation() { return toStation; }
    public String getTrainName() { return trainName; }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}//