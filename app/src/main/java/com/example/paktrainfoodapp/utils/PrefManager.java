package com.example.paktrainfoodapp.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefManager {

    // SharedPreferences file name
    private static final String PREF_NAME = "PakTrainPrefs";

    // Common keys
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_IS_REGISTERED = "is_registered";

    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_IMAGE = "user_image";

    // 🔹 Restaurant related keys (ADDED)
    private static final String KEY_IS_RESTAURANT_VERIFIED = "is_restaurant_verified";
    private static final String KEY_IS_DELIVERY_VERIFIED = "is_delivery_verified";
    private static final String KEY_RESTAURANT_ID = "restaurant_id";
    private static final String KEY_RESTAURANT_NAME = "restaurant_name";
    private static final String KEY_RESTAURANT_APPROVAL_STATUS = "restaurant_approval_status";

    private static final String KEY_DELIVERY_ID = "delivery_id";
    private static final String KEY_DELIVERY_NAME = "delivery_name";
    private static final String KEY_DELIVERY_APPROVAL_STATUS = "delivery_approval_status";
    private final SharedPreferences pref;
    private final SharedPreferences.Editor editor;
    // 🔹 USER CITY (for Restaurant auto city)
    private static final String KEY_USER_CITY = "user_city";

    public void setUserCity(String city) {
        editor.putString(KEY_USER_CITY, city);
        editor.apply();
    }

    public String getUserCity() {
        return pref.getString(KEY_USER_CITY, null);
    }


    public PrefManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    // 🔹 LOGIN STATUS METHODS
    public void setLogin(boolean isLoggedIn) {
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // 🔹 USER ROLE METHODS (Passenger, Restaurant, Delivery)
    public void setUserRole(String role) {
        editor.putString(KEY_USER_ROLE, role);
        editor.apply();
    }

    public String getUserRole() {
        return pref.getString(KEY_USER_ROLE, "PASSENGER");
    }

    // 🔹 REGISTRATION STATUS
    public void setRegistered(boolean isRegistered, String email) {
        editor.putBoolean(KEY_IS_REGISTERED, isRegistered);
        if (email != null && !email.isEmpty()) {
            editor.putString(KEY_USER_EMAIL, email);
        }
        editor.apply();
    }

    public boolean isRegistered() {
        return pref.getBoolean(KEY_IS_REGISTERED, false);
    }

    // 🔹 USER EMAIL
    public void setUserEmail(String email) {
        editor.putString(KEY_USER_EMAIL, email);
        editor.apply();
    }

    public String getUserEmail() {
        return pref.getString(KEY_USER_EMAIL, null);
    }

    // 🔹 USER NAME
    public void setUserName(String name) {
        editor.putString(KEY_USER_NAME, name);
        editor.apply();
    }

    public String getUserName() {
        return pref.getString(KEY_USER_NAME, null);
    }

    // 🔹 USER IMAGE (Base64 or URL)
    public void setUserImage(String imageData) {
        editor.putString(KEY_USER_IMAGE, imageData);
        editor.apply();
    }

    public String getUserImage() {
        return pref.getString(KEY_USER_IMAGE, null);
    }

    // 🔹 RESTAURANT VERIFICATION
    public void setIsRestaurantVerified(boolean verified) {
        editor.putBoolean(KEY_IS_RESTAURANT_VERIFIED, verified);
        editor.apply();
    }

    public boolean isRestaurantVerified() {
        return pref.getBoolean(KEY_IS_RESTAURANT_VERIFIED, false);
    }



    // 🔹 ADDED: Restaurant ID
    public void setRestaurantId(String id) {
        editor.putString(KEY_RESTAURANT_ID, id);
        editor.apply();
    }

    public String getRestaurantId() {
        return pref.getString(KEY_RESTAURANT_ID, null);
    }

    // 🔹 ADDED: Restaurant Name
    public void setRestaurantName(String name) {
        editor.putString(KEY_RESTAURANT_NAME, name);
        editor.apply();
    }

    public String getRestaurantName() {
        return pref.getString(KEY_RESTAURANT_NAME, null);
    }

    // 🔹 ADDED: Restaurant Approval Status (Pending, Approved, Rejected)
    public void setRestaurantApprovalStatus(String status) {
        editor.putString(KEY_RESTAURANT_APPROVAL_STATUS, status);
        editor.apply();
    }

    public String getRestaurantApprovalStatus() {
        return pref.getString(KEY_RESTAURANT_APPROVAL_STATUS, "Pending");
    }


    //Delivery boy
    public void setIsDeliveryVerified(boolean verified) {
        editor.putBoolean(KEY_IS_DELIVERY_VERIFIED, verified);
        editor.apply();
    }

    public boolean isDeliveryVerified() {
        return pref.getBoolean(KEY_IS_DELIVERY_VERIFIED, false);
    }



    // 🔹 ADDED: Delivery ID
    public void setDeliveryId(String id) {
        editor.putString(KEY_DELIVERY_ID, id);
        editor.apply();
    }

    public String getDeliveryId() {
        return pref.getString(KEY_DELIVERY_ID, null);
    }

    // 🔹 ADDED: Delivery Name (FIXED: was overwriting KEY_DELIVERY_ID by mistake)
    public void setDeliveryName(String name) {
        editor.putString(KEY_DELIVERY_NAME, name);
        editor.apply();
    }

    public String getDeliveryName() {
        return pref.getString(KEY_DELIVERY_NAME, null);
    }

    // 🔹 ADDED: Restaurant Approval Status (Pending, Approved, Rejected)
    public void setDeliveryApprovalStatus(String status) {
        editor.putString(KEY_DELIVERY_APPROVAL_STATUS, status);
        editor.apply();
    }

    public String getDeliveryApprovalStatus() {
        return pref.getString(KEY_DELIVERY_APPROVAL_STATUS, "Pending");
    }
    // 🔹 CLEAR ALL (Logout or reset)
    public void clear() {
        editor.clear();
        editor.apply();
    }
}

//
