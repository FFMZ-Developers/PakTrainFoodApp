package com.example.paktrainfoodapp.ui.main.Restaurant.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Real dashboard figures for a restaurant. Previously these four cards were
 * hard-coded placeholders in the layout and never populated.
 */
public class RestaurantDashboardViewModel extends ViewModel {

    private final MutableLiveData<Integer> totalOrders = new MutableLiveData<>(0);
    private final MutableLiveData<Double> revenue = new MutableLiveData<>(0.0);
    private final MutableLiveData<Integer> menuCount = new MutableLiveData<>(0);
    private final MutableLiveData<String> restaurantName = new MutableLiveData<>("Restaurant");

    private ListenerRegistration ordersReg, menuReg, profileReg;

    private boolean started = false;

    public LiveData<Integer> getTotalOrders() { return totalOrders; }
    public LiveData<Double> getRevenue() { return revenue; }
    public LiveData<Integer> getMenuCount() { return menuCount; }
    public LiveData<String> getRestaurantName() { return restaurantName; }

    public void start() {

        if (started || FirebaseAuth.getInstance().getCurrentUser() == null) return;

        started = true;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Orders + revenue
        ordersReg = db.collection("Orders")
                .whereEqualTo("restaurantId", uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    int count = 0;
                    double earned = 0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        count++;

                        // Only completed orders count as revenue; pending or
                        // cancelled ones would overstate earnings.
                        String status = doc.getString("orderStatus");

                        if (status != null && status.equalsIgnoreCase("completed")) {

                            Double subtotal = doc.getDouble("subtotal");

                            if (subtotal == null) subtotal = doc.getDouble("totalPrice");

                            if (subtotal != null) earned += subtotal;
                        }
                    }

                    totalOrders.postValue(count);
                    revenue.postValue(earned);
                });

        // Menu item count
        menuReg = db.collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(uid)
                .collection("MenuItems")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    menuCount.postValue(snapshot.size());
                });

        // Display name
        profileReg = db.collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null || !snapshot.exists()) return;

                    String name = snapshot.getString("restaurantName");

                    if (name != null && !name.isEmpty()) restaurantName.postValue(name);
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        if (ordersReg != null) ordersReg.remove();
        if (menuReg != null) menuReg.remove();
        if (profileReg != null) profileReg.remove();
    }
}
