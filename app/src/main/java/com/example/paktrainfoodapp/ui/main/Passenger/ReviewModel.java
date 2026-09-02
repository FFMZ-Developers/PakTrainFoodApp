package com.example.paktrainfoodapp.ui.main.Passenger;

/**
 * A single passenger review of a restaurant - one per completed order
 * (keyed by orderId as the Firestore document id, so re-submitting for
 * the same order edits rather than duplicates).
 */
public class ReviewModel {

    private String reviewId;
    private String orderId;
    private String passengerUid;
    private String passengerName;
    private String passengerPhotoUrl;
    private float rating;
    private String comment;
    private long createdAt;
    private long updatedAt;

    public ReviewModel() {}

    public String getReviewId() { return reviewId; }
    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getPassengerUid() { return passengerUid; }
    public void setPassengerUid(String passengerUid) { this.passengerUid = passengerUid; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public String getPassengerPhotoUrl() { return passengerPhotoUrl; }
    public void setPassengerPhotoUrl(String passengerPhotoUrl) { this.passengerPhotoUrl = passengerPhotoUrl; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
