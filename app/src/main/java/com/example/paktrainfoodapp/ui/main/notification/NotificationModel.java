package com.example.paktrainfoodapp.ui.main.notification;

import com.google.firebase.Timestamp;

public class NotificationModel {

    private String documentId;

    private String title;
    private String body;

    // Kis order ke liye notification hai
    private String orderId;

    // ACCEPTED, PREPARING, READY, DELIVERED
    private String status;

    // ORDER, WALLET, PROFILE, RESTAURANT, DELIVERY
    private String type;

    // OPEN_ORDER, OPEN_WALLET, OPEN_PROFILE
    private String action;

    private boolean isRead;

    private Timestamp createdAt;
    private String screen;
    private String deepLinkId;
    public NotificationModel() {
    }

    public NotificationModel(
            String title,
            String body,
            String orderId,
            String status,
            String type,
            String action,
            boolean isRead,
            Timestamp createdAt
    ) {

        this.title = title;
        this.body = body;
        this.orderId = orderId;
        this.status = status;
        this.type = type;
        this.action = action;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public String getScreen() {
        return screen;
    }

    public String getDeepLinkId() {
        return deepLinkId;
    }
    public String getBody() {
        return body;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getAction() {
        return action;
    }

    public boolean isRead() {
        return isRead;
    }
    public boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(boolean isRead) {
        this.isRead = isRead;
    }
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

}










//package com.example.paktrainfoodapp.ui.main.notification;
//
//import com.google.firebase.Timestamp;
//
//public class NotificationModel {
//
//    private String documentId;
//
//    private String title;
//
//    private String body;
//
//    private String orderId;
//
//    private String status;
//
//    private boolean isRead;
//
//    private Timestamp createdAt;
//
//    public NotificationModel() {
//        // Firestore Required
//    }
//
//    public NotificationModel(String title,
//                             String body,
//                             String orderId,
//                             String status,
//                             boolean isRead,
//                             Timestamp createdAt) {
//
//        this.title = title;
//        this.body = body;
//        this.orderId = orderId;
//        this.status = status;
//        this.isRead = isRead;
//        this.createdAt = createdAt;
//    }
//
//    public String getDocumentId() {
//        return documentId;
//    }
//
//    public void setDocumentId(String documentId) {
//        this.documentId = documentId;
//    }
//
//    public String getTitle() {
//        return title;
//    }
//
//    public String getBody() {
//        return body;
//    }
//
//    public String getOrderId() {
//        return orderId;
//    }
//
//    public String getStatus() {
//        return status;
//    }
//
//    public boolean isRead() {
//        return isRead;
//    }
//
//    public Timestamp getCreatedAt() {
//        return createdAt;
//    }
//
//    public void setRead(boolean read) {
//        isRead = read;
//    }
//}