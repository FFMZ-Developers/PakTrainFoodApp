package com.example.paktrainfoodapp.ui.main.Delivery.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Live figures for the rider dashboard.
 *
 * A rider "owns" an order once they accept it, which the app records in the
 * order's acceptedBy field - that is what every count here is based on.
 */
public class DeliveryDashboardViewModel extends ViewModel {

    private final MutableLiveData<Integer> totalDeliveries = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> activeDeliveries = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> completedDeliveries = new MutableLiveData<>(0);
    private final MutableLiveData<Double> earnings = new MutableLiveData<>(0.0);
    private final MutableLiveData<String> riderName = new MutableLiveData<>("Rider");
    private final MutableLiveData<Boolean> online = new MutableLiveData<>(false);

    private ListenerRegistration ordersReg, walletReg, profileReg;

    private com.google.firebase.database.DatabaseReference onlineRef;
    private com.google.firebase.database.ValueEventListener onlineListener;

    private boolean started = false;

    public LiveData<Integer> getTotalDeliveries() { return totalDeliveries; }
    public LiveData<Integer> getActiveDeliveries() { return activeDeliveries; }
    public LiveData<Integer> getCompletedDeliveries() { return completedDeliveries; }
    public LiveData<Double> getEarnings() { return earnings; }
    public LiveData<String> getRiderName() { return riderName; }
    public LiveData<Boolean> getOnline() { return online; }

    public void start() {

        if (started || FirebaseAuth.getInstance().getCurrentUser() == null) return;

        started = true;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        ordersReg = db.collection("Orders")
                .whereEqualTo("acceptedBy", uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    int total = 0, active = 0, completed = 0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        total++;

                        String status = doc.getString("orderStatus");

                        if (status != null && status.equalsIgnoreCase("completed")) {
                            completed++;
                        } else {
                            active++;
                        }
                    }

                    totalDeliveries.postValue(total);
                    activeDeliveries.postValue(active);
                    completedDeliveries.postValue(completed);
                });

        walletReg = com.example.paktrainfoodapp.data.WalletPaths
                .wallet(com.example.paktrainfoodapp.data.WalletPaths.ROLE_DELIVERY, uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null || !snapshot.exists()) {
                        earnings.postValue(0.0);
                        return;
                    }

                    Double available = snapshot.getDouble("availableBalance");

                    earnings.postValue(available == null ? 0.0 : available);
                });

        profileReg = db.collection("Users").document("Delivery")
                .collection("VerifiedRegister").document(uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null || !snapshot.exists()) return;

                    String name = snapshot.getString("name");

                    if (name != null && !name.isEmpty()) riderName.postValue(name);
                });

        // Online/offline is written to the Realtime Database by the dashboard's
        // switch, so it has to be read from there rather than Firestore.
        //
        // ✅ FIX: same explicit-database-URL fix as DeliveryDashboardFragment.java
        // - no URL here meant this could read from a different RTDB
        // instance than the dashboard actually wrote to.
        onlineRef = com.google.firebase.database.FirebaseDatabase.getInstance(
                        "https://paktrainfoodservice-default-rtdb.firebaseio.com/")
                .getReference("DeliveryRiders")
                .child(uid)
                .child("online");

        onlineListener = new com.google.firebase.database.ValueEventListener() {

            @Override
            public void onDataChange(@androidx.annotation.NonNull
                                     com.google.firebase.database.DataSnapshot snapshot) {

                Boolean value = snapshot.getValue(Boolean.class);

                online.postValue(value != null && value);
            }

            @Override
            public void onCancelled(@androidx.annotation.NonNull
                                    com.google.firebase.database.DatabaseError error) { }
        };

        onlineRef.addValueEventListener(onlineListener);
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        if (ordersReg != null) ordersReg.remove();
        if (walletReg != null) walletReg.remove();
        if (profileReg != null) profileReg.remove();

        if (onlineRef != null && onlineListener != null) {
            onlineRef.removeEventListener(onlineListener);
        }
    }
}
