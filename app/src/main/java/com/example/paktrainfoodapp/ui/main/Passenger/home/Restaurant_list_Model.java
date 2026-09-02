package com.example.paktrainfoodapp.ui.main.Passenger.home;

public class Restaurant_list_Model {

    // ⚡ FIXED: Added imageUrl along with legacy variables to support smooth transition
    private String uid;
    private String restaurantName;
    private String city;
    private String imageBase64;
    private String imageUrl;
    private boolean favorite;

    // Module 7 - reliability, used to rank the list (higher first) and to
    // skip restaurants that have been auto-paused for repeated strikes.
    private int reliabilityScore = 100;
    private boolean paused = false;

    // Required empty constructor for Firebase Firestore parsing layers
    public Restaurant_list_Model() { }

    // Legacy Constructor (Agar kahin purana code call kar raha ho to crash nahi hoga)
    public Restaurant_list_Model(String uid, String restaurantName, String city, String imageBase64) {
        this.uid = uid;
        this.restaurantName = restaurantName;
        this.city = city;
        this.imageBase64 = imageBase64;
        this.imageUrl = imageBase64; // Fallback configuration logic
    }

    // ================= GETTERS & SETTERS =================

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    // ⚡ THE METHOD: This fixes the compilation crash inside the fragment layer
    public String getImageUrl() {
        // Safe validation: agar imageUrl empty ho to fallback to base64
        if (imageUrl == null || imageUrl.isEmpty()) {
            return imageBase64;
        }
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public int getReliabilityScore() {
        return reliabilityScore;
    }

    public void setReliabilityScore(int reliabilityScore) {
        this.reliabilityScore = reliabilityScore;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    // Module: aggregate rating - maintained server-side by
    // onReviewWritten.js, read here rather than computed client-side from
    // every individual review.
    private double averageRating = 0;
    private int reviewCount = 0;

    public double getAverageRating() { return averageRating; }
    public void setAverageRating(double averageRating) { this.averageRating = averageRating; }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
}

//

