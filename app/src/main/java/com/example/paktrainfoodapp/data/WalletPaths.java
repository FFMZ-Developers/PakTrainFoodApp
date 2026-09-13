package com.example.paktrainfoodapp.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Single place that knows where wallets live, so the app and the Cloud
 * Functions can never drift apart.
 *
 *   Wallets/{Role}/Accounts/{uid}          -> balances + bank details
 *   Wallets/{Role}/Accounts/{uid}/history  -> transactions
 *
 * Role is one of Passenger / Restaurant / Delivery / Admin, matching how
 * Users is organised.
 */
public class WalletPaths {

    public static final String ROLE_PASSENGER = "Passenger";
    public static final String ROLE_RESTAURANT = "Restaurant";
    public static final String ROLE_DELIVERY = "Delivery";
    public static final String ROLE_ADMIN = "Admin";

    public static DocumentReference wallet(String role, String uid) {

        return FirebaseFirestore.getInstance()
                .collection("Wallets")
                .document(role)
                .collection("Accounts")
                .document(uid);
    }

    public static CollectionReference history(String role, String uid) {
        return wallet(role, uid).collection("history");
    }

    /** Maps the UI role constants onto the Firestore role folder names. */
    public static String roleFolder(String uiRole) {

        if (uiRole == null) return ROLE_PASSENGER;

        switch (uiRole.toUpperCase()) {
            case "RESTAURANT": return ROLE_RESTAURANT;
            case "DELIVERY":   return ROLE_DELIVERY;
            case "ADMIN":      return ROLE_ADMIN;
            default:           return ROLE_PASSENGER;
        }
    }
}
