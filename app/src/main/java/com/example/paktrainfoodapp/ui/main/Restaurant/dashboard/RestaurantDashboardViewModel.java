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

    // Module: real "Recent Orders" for the dashboard card - previously a
    // single hardcoded fake row that never reflected anything real.
    private final MutableLiveData<java.util.List<com.google.firebase.firestore.DocumentSnapshot>> recentActiveOrders
            = new MutableLiveData<>(new java.util.ArrayList<>());

    public LiveData<java.util.List<com.google.firebase.firestore.DocumentSnapshot>> getRecentActiveOrders() {
        return recentActiveOrders;
    }

    private ListenerRegistration ordersReg, menuReg, profileReg, walletReg;

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

        // Orders + total-orders-count + recent-active-orders, all from ONE
        // listener - avoids a second query (and the composite index it
        // would need) for something this listener is already reading
        // anyway. Revenue itself no longer comes from here (see below).
        ordersReg = db.collection("Orders")
                .whereEqualTo("restaurantId", uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    int count = 0;

                    java.util.List<DocumentSnapshot> active = new java.util.ArrayList<>();

                    java.util.Set<String> activeStatuses = new java.util.HashSet<>(java.util.Arrays.asList(
                            "Active", "Accepted", "ready_for_delivery",
                            "accepted_by_rider", "arrive_rider_at_resturent",
                            "dropped", "pick_up"
                    ));

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        count++;

                        String status = doc.getString("orderStatus");

                        if (status != null && activeStatuses.contains(status)) {
                            active.add(doc);
                        }
                    }

                    totalOrders.postValue(count);

                    active.sort((a, b) -> {
                        Long ta = a.getLong("timestamp");
                        Long tb = b.getLong("timestamp");
                        long va = ta != null ? ta : 0L;
                        long vb = tb != null ? tb : 0L;
                        return Long.compare(vb, va); // newest first
                    });

                    if (active.size() > 5) active = active.subList(0, 5);

                    recentActiveOrders.postValue(active);
                });

        // ✅ FIX: "Revenue" used to be re-derived here by independently
        // summing every completed order's subtotal, forever - which
        // drifts away from the wallet's real availableBalance the moment
        // a payout happens (onOrderCompleted.js moves each completed
        // order's subtotal from pendingBalance into availableBalance;
        // autoPayoutWallets.js later empties availableBalance out to the
        // restaurant's bank). The dashboard now shows the SAME number the
        // wallet screen does, live, so the two can never disagree.
        walletReg = com.example.paktrainfoodapp.data.WalletPaths
                .history(com.example.paktrainfoodapp.data.WalletPaths.ROLE_RESTAURANT, uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    double total = 0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        String type = doc.getString("type");

                        if (!"Available".equals(type) && !"Payout".equals(type)) continue;

                        Double amount = doc.getDouble("amount");

                        if (amount != null) total += amount;
                    }

                    revenue.postValue(total);
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
        if (walletReg != null) walletReg.remove();
    }
}
