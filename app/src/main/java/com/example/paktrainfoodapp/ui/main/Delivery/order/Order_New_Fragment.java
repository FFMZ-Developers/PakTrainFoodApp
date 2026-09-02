package com.example.paktrainfoodapp.ui.main.Delivery.order;

import android.os.Bundle;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Delivery.DeliveryBoyAdapter;
import com.example.paktrainfoodapp.ui.main.Delivery.DeliveryBoyModel;
import com.example.paktrainfoodapp.ui.main.Restaurant.order.OrderDetailFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class Order_New_Fragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout layoutNoOrders;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private ArrayList<DeliveryBoyModel> orderList;
    private DeliveryBoyAdapter adapter;

    private String riderId;

    // Kept so the available-orders listener can be detached the moment
    // this rider accepts something (single-active-order rule) and
    // re-attached once they're free again.
    private ListenerRegistration availableOrdersRegistration;

    /** Rider's own city; null means "show everything" (see listenOrdersForCity). */
    private String riderCityFilter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_delivery_order_new_accept_complete,
                container,
                false
        );

        recyclerView = view.findViewById(R.id.recyclerOrders);
        layoutNoOrders = view.findViewById(R.id.layoutNoOrders);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        orderList = new ArrayList<>();

        riderId = (auth.getCurrentUser() != null)
                ? auth.getCurrentUser().getUid()
                : "";

        // ✅ ADAPTER FIXED CALLBACK
        adapter = new DeliveryBoyAdapter(requireContext(), orderList,
                new DeliveryBoyAdapter.OnActionClick() {

                    @Override
                    public void onItemClick(DeliveryBoyModel order, int position) {
                        openDetail(order);
                    }

                    @Override
                    public void onAccept(DeliveryBoyModel order, int position) {
                        showAcceptDialog(order, position);
                    }

                    @Override
                    public void onButtonClick(DeliveryBoyModel order, int position) {
                        handleButton(order, position);
                    }

                    @Override
                    public void onReportProblem(DeliveryBoyModel order, int position) {
                        // Not applicable here - this screen only shows
                        // orders not yet accepted (Module 6 Failure 3's
                        // "report a problem" only makes sense once a rider
                        // has actually accepted a delivery - see
                        // Order_Accept_Fragment for that implementation).
                    }
                });

        recyclerView.setAdapter(adapter);

        loadNearbyOrders();

        return view;
    }

    // ================= POPUP =================
    private void showAcceptDialog(DeliveryBoyModel order, int position) {

        new AlertDialog.Builder(requireContext())
                .setTitle("Accept Order")
                .setMessage("Kya aap ye order accept karna chahte hain?")
                .setPositiveButton("YES", (dialog, which) -> acceptOrder(order, position))
                .setNegativeButton("NO", null)
                .show();
    }

    // ================= ACCEPT ORDER =================
    //
    // Module 5 - "whichever rider taps Accept first gets the order" is
    // enforced HERE with a Firestore transaction: read riderAssigned
    // inside the transaction, only proceed if it's still false, and set it
    // true in the same atomic write. If two riders tap Accept within
    // milliseconds of each other, Firestore guarantees only one
    // transaction commits - the loser gets a clean "already taken" message
    // instead of silently double-assigning the order.
    private void acceptOrder(DeliveryBoyModel order, int position) {

        DocumentReference orderRef = db.collection("Orders").document(order.getOrderId());

        db.runTransaction(transaction -> {

            DocumentSnapshot snapshot = transaction.get(orderRef);

            Boolean alreadyAssigned = snapshot.getBoolean("riderAssigned");

            if (alreadyAssigned != null && alreadyAssigned) {
                throw new FirebaseFirestoreException(
                        "Order already taken by another rider",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            String currentStatus = snapshot.getString("orderStatus");

            if (!"ready_for_delivery".equals(currentStatus)) {
                throw new FirebaseFirestoreException(
                        "Order is no longer available",
                        FirebaseFirestoreException.Code.ABORTED);
            }

            transaction.update(orderRef,
                    "orderStatus", "accepted_by_rider",
                    "acceptedBy", riderId,
                    "riderAssigned", true,
                    "riderAssignedAt", System.currentTimeMillis()
            );

            return null;

        }).addOnSuccessListener(unused -> {

            Toast.makeText(getContext(),
                    "Order Accepted",
                    Toast.LENGTH_SHORT).show();

            if (position != RecyclerView.NO_POSITION &&
                    position < orderList.size()) {

                orderList.remove(position);
                adapter.notifyItemRemoved(position);
            }

        }).addOnFailureListener(e ->

                Toast.makeText(getContext(),
                        "Too late - " + e.getMessage(),
                        Toast.LENGTH_LONG).show()
        );
    }

    // ================= DETAIL OPEN =================
    private void openDetail(DeliveryBoyModel order) {

        OrderDetailFragment fragment =
                OrderDetailFragment.newInstance(order.getOrderId());

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container, fragment)
                .addToBackStack("order_detail")
                .commit();
    }

    // ================= LOAD ORDERS =================
    private void loadNearbyOrders() {

        if (riderId == null || riderId.isEmpty()) return;

        // ✅ Single-active-order rule: if this rider already has an order
        // in progress (accepted but not yet completed/cancelled), the
        // "Available Orders" list stays EMPTY - they shouldn't be able to
        // pick up a second delivery while still holding one. As soon as
        // that order finishes (or gets cancelled), this listener fires
        // again and available orders reappear automatically.
        db.collection("Orders")
                .whereEqualTo("acceptedBy", riderId)
                .whereIn("orderStatus", java.util.Arrays.asList(
                        "accepted_by_rider",
                        "arrive_rider_at_resturent",
                        "dropped",
                        "pick_up"
                ))
                .addSnapshotListener((activeQuery, activeErr) -> {

                    if (!isAdded()) return;

                    boolean hasActiveOrder = activeErr == null
                            && activeQuery != null
                            && !activeQuery.isEmpty();

                    if (hasActiveOrder) {

                        if (availableOrdersRegistration != null) {
                            availableOrdersRegistration.remove();
                            availableOrdersRegistration = null;
                        }

                        orderList.clear();
                        adapter.notifyDataSetChanged();

                        recyclerView.setVisibility(View.GONE);
                        layoutNoOrders.setVisibility(View.VISIBLE);

                        // Without this, a rider holding a leftover order
                        // from an earlier test just sees a blank list with
                        // no explanation and assumes dispatch is broken.
                        android.util.Log.d("Order_New_Fragment",
                                "Hiding available orders - rider still has "
                                        + activeQuery.size() + " order(s) in progress");

                        Toast.makeText(getContext(),
                                "Finish your current delivery to see new orders.",
                                Toast.LENGTH_LONG).show();

                        return;
                    }

                    if (availableOrdersRegistration == null) {
                        startAvailableOrdersListener();
                    }
                });
    }

    /**
     * ✅ FIX: this used to query by whereArrayContains("notifiedRiderIds",
     * riderId) - that array only ever contains whoever happened to be
     * online at the exact moment dispatchRider.js ran its search
     * (which also times out after a few minutes). A rider who wasn't
     * online yet at that moment - or who comes online later - would
     * NEVER see this order, even though it's still sitting unassigned.
     *
     * Now this queries by the restaurant's city directly (written onto
     * the order by dispatchRider.js) - completely independent of when
     * any particular rider happened to go online. Any rider, whenever
     * they open this screen, sees every still-unassigned
     * "ready_for_delivery" order in their own city.
     */
    private void startAvailableOrdersListener() {

        db.collection("Users").document("Delivery")
                .collection("VerifiedRegister").document(riderId)
                .get()
                .addOnSuccessListener(riderDoc -> {

                    if (!isAdded()) return;

                    String riderCity = riderDoc.exists() ? riderDoc.getString("cityNormalized") : null;

                    if (riderCity != null && !riderCity.isEmpty()) {
                        listenOrdersForCity(riderCity);
                    } else {
                        // No city on file for this rider (older account) -
                        // fall back to showing every unassigned order
                        // rather than silently showing nothing.
                        listenAllUnassignedOrders();
                    }
                })
                .addOnFailureListener(e -> listenAllUnassignedOrders());
    }

    /**
     * ✅ FIX: this used to be a Firestore composite query
     * (orderStatus == ready_for_delivery AND restaurantCityNormalized == X).
     * Two ways that silently showed nothing:
     *
     *   1. It needs a composite index. Without one deployed, the listener
     *      just errors and the list stays empty.
     *   2. It matches on restaurantCityNormalized, which only exists on
     *      orders whose restaurant registered AFTER that field was added.
     *      For any older restaurant the field is absent, so the equality
     *      filter excludes the order entirely - no error, just nothing.
     *
     * Now it queries on orderStatus alone (single field, always indexed,
     * always present) and does the city match in the app. An order whose
     * restaurant has no city recorded is SHOWN rather than hidden - during
     * testing, over-showing is far better than an unexplained empty list.
     */
    private void listenOrdersForCity(String riderCity) {

        this.riderCityFilter = riderCity;

        availableOrdersRegistration = db.collection("Orders")
                .whereEqualTo("orderStatus", "ready_for_delivery")
                .addSnapshotListener((query, e) -> bindOrderResults(query, e));
    }

    private void listenAllUnassignedOrders() {

        this.riderCityFilter = null;

        availableOrdersRegistration = db.collection("Orders")
                .whereEqualTo("orderStatus", "ready_for_delivery")
                .addSnapshotListener((query, e) -> bindOrderResults(query, e));
    }

    private void bindOrderResults(QuerySnapshot query, FirebaseFirestoreException e) {

        if (!isAdded()) return;

        // ✅ FIX: this used to `return` silently on any error - so a
        // missing Firestore composite index (which is exactly what the
        // orderStatus + restaurantCityNormalized query needs) produced a
        // permanently empty list with zero indication anything was wrong.
        // Now the failure is surfaced, and the query falls back to the
        // simpler single-field version so orders still appear.
        if (e != null) {

            android.util.Log.e("Order_New_Fragment", "Available-orders query failed", e);

            Toast.makeText(getContext(),
                    "Couldn't load orders by city - showing all instead.",
                    Toast.LENGTH_LONG).show();

            if (availableOrdersRegistration != null) {
                availableOrdersRegistration.remove();
                availableOrdersRegistration = null;
            }

            listenAllUnassignedOrders();
            return;
        }

        if (query == null) return;

        orderList.clear();

        for (QueryDocumentSnapshot doc : query) {

            // Already grabbed by another rider (their Accept
            // transaction won the race) - don't show it here.
            Boolean riderAssigned = doc.getBoolean("riderAssigned");
            if (riderAssigned != null && riderAssigned) continue;

            // City match done here rather than in the query - see
            // listenOrdersForCity() for why. An order with no recorded
            // restaurant city is shown rather than skipped.
            if (riderCityFilter != null) {

                String orderCity = doc.getString("restaurantCityNormalized");

                if (orderCity != null && !orderCity.isEmpty()
                        && !orderCity.equalsIgnoreCase(riderCityFilter)) {
                    continue;
                }
            }

            DeliveryBoyModel order =
                    new DeliveryBoyModel(
                            doc.getId(),
                            doc.getDouble("totalPrice") != null
                                    ? doc.getDouble("totalPrice")
                                    : 0.0,
                            doc.getReference().getPath()
                    );

            order.setStatus(doc.getString("orderStatus"));
                        order.setOrderNumber(doc.getLong("orderNumber"));
                        order.setRestaurantName(doc.getString("restaurantName"));

                        Long trainEta = doc.getLong("trainEtaEndTime");
                        order.setTrainEtaEndTime(trainEta != null ? trainEta : 0L);
            orderList.add(order);
        }

        adapter.notifyDataSetChanged();

        boolean empty = orderList.isEmpty();

        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutNoOrders.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
    private void handleButton(DeliveryBoyModel order, int position) {

        String status = order.getStatus();

        if ("accepted_by_rider".equals(status)) {

            updateStatus(order, "arrive_rider_at_resturent");
            Toast.makeText(getContext(), "Arrived marked", Toast.LENGTH_SHORT).show();

        } else if ("arrive_rider_at_resturent".equals(status)) {

            Toast.makeText(getContext(), "Wait for restaurant", Toast.LENGTH_SHORT).show();

        } else if ("dropped".equals(status)) {

            new AlertDialog.Builder(requireContext())
                    .setTitle("Pickup Order")
                    .setMessage("Confirm pickup?")
                    .setPositiveButton("YES", (d, w) -> {
                        updateStatus(order, "pick_up");
                        orderList.remove(position);
                        adapter.notifyItemRemoved(position);
                    })
                    .setNegativeButton("NO", null)
                    .show();
        }
    }

    // ✅ FIX: this used to be an empty stub, so tapping "Arrived" / "Pickup"
    // on this tab updated nothing in Firestore and silently did nothing -
    // mirrors Order_Accept_Fragment's updateStatus.
    private void updateStatus(DeliveryBoyModel order, String status) {

        db.collection("Orders")
                .document(order.getOrderId())
                .update("orderStatus", status);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (availableOrdersRegistration != null) {
            availableOrdersRegistration.remove();
            availableOrdersRegistration = null;
        }
    }
}






