package com.example.paktrainfoodapp.ui.main.Passenger;

import java.io.Serializable;
import java.util.Map;

// ✅ Serializable implement kiya taake fragment mein pass ho sake
public class MenuitemModel implements Serializable {

    private String id;
    private String name;
    private String description;
    private String imageUrl;
    private String time;
    private String category;
    private Map<String, Double> variations;
    private String restaurantId;
    private String restaurantName;
    private String restaurantCity;
    private String restaurantUid;
    private String orderStatus = "active";
    private double price;
    private int quantity;
    // ✅ Firebase ke liye Empty Constructor
    public MenuitemModel() {}

    // 🔹 Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Map<String, Double> getVariations() { return variations; }
    public void setVariations(Map<String, Double> variations) { this.variations = variations; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }

    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }

    public String getRestaurantCity() { return restaurantCity; }
    public void setRestaurantCity(String restaurantCity) { this.restaurantCity = restaurantCity; }

    public String getRestaurantUid() { return restaurantUid; }
    public void setRestaurantUid(String restaurantUid) { this.restaurantUid = restaurantUid; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}




//
  //publick void


