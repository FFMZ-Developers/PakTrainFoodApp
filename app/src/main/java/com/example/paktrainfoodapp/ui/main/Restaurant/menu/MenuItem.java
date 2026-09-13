package com.example.paktrainfoodapp.ui.main.Restaurant.menu;

import java.util.Map;

public class MenuItem {
    private String id;
    private String restaurantId;
    private String restaurantName;
    private String restaurantCity;
    private String imageUrl;
    private String category;
    private String name;
    private String description;
    private String time;
    private String status;

    // ✅ CHANGED: Replaced 'double price' with Map for Variations (e.g., {"Half": 250, "Full": 450})
    private Map<String, Double> variations;

    private String passengerUid;
    private String orderStatus;
    private Map<String, Object> meta;
    private String docPath;
    // MenuItem class mein ye add kar dein:
    private long etaEndTime;

    // Module 2 - separate field for the LIVE TRAIN ETA (time the train is
    // expected to reach the meal station). Deliberately a different field
    // from `etaEndTime` above, which is already used elsewhere (Accepted
    // tab's "Ready for Delivery" prep-deadline countdown) - reusing the same
    // field would have overwritten that timer with the train's ETA instead.
    private long trainEtaEndTime;

    /**
     * Whether passengers can currently order this item. Defaults to true so
     * every item created before this field existed stays orderable.
     */
    private boolean available = true;


    public MenuItem() {}

    // Constructor updated to accept Map instead of double price
    public MenuItem(String name, String desc, String imageUrl, Map<String, Double> variations, String time,
                    String category, String restId, String restaurantName, String restaurantCity) {
        this.name = name;
        this.description = desc;
        this.imageUrl = imageUrl;
        this.variations = variations;
        this.time = time;
        this.category = category;
        this.restaurantId = restId;
        this.restaurantName = restaurantName;
        this.restaurantCity = restaurantCity;
        this.orderStatus = "Available";
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }

    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }

    public String getRestaurantCity() { return restaurantCity; }
    public void setRestaurantCity(String restaurantCity) { this.restaurantCity = restaurantCity; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    // ✅ NEW: Getter/Setter for Variations
    public Map<String, Double> getVariations() { return variations; }
    public void setVariations(Map<String, Double> variations) { this.variations = variations; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public String getPassengerUid() { return passengerUid; }
    public void setPassengerUid(String passengerUid) { this.passengerUid = passengerUid; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }

    public String getDocPath() { return docPath; }
    public void setDocPath(String docPath) { this.docPath = docPath; }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public long getEtaEndTime() { return etaEndTime; }
    public void setEtaEndTime(long etaEndTime) { this.etaEndTime = etaEndTime; }

    public long getTrainEtaEndTime() { return trainEtaEndTime; }
    public void setTrainEtaEndTime(long trainEtaEndTime) { this.trainEtaEndTime = trainEtaEndTime; }


    public boolean isAvailable() { return available; }

    public void setAvailable(boolean available) { this.available = available; }

    // Module: human-readable sequential order number (see OrderNumberUtils).
    private Long orderNumber;
    public Long getOrderNumber() { return orderNumber; }
    public void setOrderNumber(Long orderNumber) { this.orderNumber = orderNumber; }
}








