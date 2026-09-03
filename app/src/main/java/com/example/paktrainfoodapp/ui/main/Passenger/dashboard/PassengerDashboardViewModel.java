package com.example.paktrainfoodapp.ui.main.Passenger.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.paktrainfoodapp.ui.shared.orders.MyOrdersAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Holds the passenger dashboard state.
 *
 * Keeping the Firestore listeners here (rather than in the Fragment) means the
 * numbers survive configuration changes and the Fragment only has to observe
 * and render - the M-V-VM split.
 */
public class PassengerDashboardViewModel extends ViewModel {

    private final MutableLiveData<Integer> totalOrders = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> activeOrders = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> completedOrders = new MutableLiveData<>(0);
    private final MutableLiveData<Double> walletBalance = new MutableLiveData<>(0.0);

    // Module: real "Recent Orders" for the dashboard card - same pattern
    // as the restaurant dashboard's own recentActiveOrders.
    private final MutableLiveData<java.util.List<DocumentSnapshot>> recentOrders
            = new MutableLiveData<>(new java.util.ArrayList<>());

    public LiveData<java.util.List<DocumentSnapshot>> getRecentOrders() { return recentOrders; }

    private ListenerRegistration ordersRegistration;
    private ListenerRegistration walletRegistration;

    private boolean started = false;

    public LiveData<Integer> getTotalOrders() { return totalOrders; }
    public LiveData<Integer> getActiveOrders() { return activeOrders; }
    public LiveData<Integer> getCompletedOrders() { return completedOrders; }
    public LiveData<Double> getWalletBalance() { return walletBalance; }

    public void start() {

        if (started) return;

        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        started = true;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        listenOrders(uid);
        listenWallet(uid);
    }

    private void listenOrders(String uid) {

        ordersRegistration = FirebaseFirestore.getInstance()
                .collection("Orders")
                .whereEqualTo("passengerUid", uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null) return;

                    int total = 0, active = 0, completed = 0;

                    java.util.List<DocumentSnapshot> recent = new java.util.ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        total++;

                        // Reuse the same status grouping the orders list uses,
                        // so the dashboard can never disagree with My Orders.
                        String bucket = MyOrdersAdapter.bucketOf(
                                doc.getString("orderStatus"));

                        if ("completed".equals(bucket)) {
                            completed++;
                        } else if ("ongoing".equals(bucket) || "pending".equals(bucket)) {
                            active++;
                            recent.add(doc);
                        }
                    }

                    totalOrders.postValue(total);
                    activeOrders.postValue(active);
                    completedOrders.postValue(completed);

                    recent.sort((a, b) -> {
                        Long ta = a.getLong("timestamp");
                        Long tb = b.getLong("timestamp");
                        long va = ta != null ? ta : 0L;
                        long vb = tb != null ? tb : 0L;
                        return Long.compare(vb, va); // newest first
                    });

                    if (recent.size() > 5) recent = recent.subList(0, 5);

                    recentOrders.postValue(recent);
                });
    }

    private void listenWallet(String uid) {

        walletRegistration = com.example.paktrainfoodapp.data.WalletPaths
                .wallet(com.example.paktrainfoodapp.data.WalletPaths.ROLE_PASSENGER, uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null || !snapshot.exists()) {
                        walletBalance.postValue(0.0);
                        return;
                    }

                    Double available = snapshot.getDouble("availableBalance");

                    walletBalance.postValue(available == null ? 0.0 : available);
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        if (ordersRegistration != null) ordersRegistration.remove();
        if (walletRegistration != null) walletRegistration.remove();
    }
}
