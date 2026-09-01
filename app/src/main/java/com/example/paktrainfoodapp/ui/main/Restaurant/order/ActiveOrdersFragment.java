package com.example.paktrainfoodapp.ui.main.Restaurant.order;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Restaurant.menu.MenuItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ActiveOrdersFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout layoutNoOrders;

    private ArrayList<MenuItem> orderList;
    private OrdersAdapter adapter;

    private FirebaseFirestore firestore;
    private String restaurantUid;

    private ListenerRegistration orderListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_restaurant_orders_accept_pending_complete,
                container,
                false
        );

        recyclerView = view.findViewById(R.id.recyclerOrders);
        layoutNoOrders = view.findViewById(R.id.layoutNoOrders);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        orderList = new ArrayList<>();
        adapter = new OrdersAdapter(orderList);
        recyclerView.setAdapter(adapter);

        firestore = FirebaseFirestore.getInstance();

        restaurantUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!restaurantUid.isEmpty()) {
            loadOrders();
        }
    }

    private void loadOrders() {

        if (orderListener != null) {
            orderListener.remove();
        }

        orderListener = firestore.collection("Orders")
                .whereEqualTo("restaurantId", restaurantUid)
                .whereEqualTo("orderStatus", "Active")
                // Module 4 - only orders the train-ETA threshold has
                // already surfaced (see onOrderEtaThresholdReached.js).
                // Orders placed far in advance stay hidden here until then.
                .whereEqualTo("visibleToRestaurant", true)
                .addSnapshotListener((query, e) -> {

                    if (e != null || query == null || !isAdded()) return;

                    orderList.clear();

                    for (QueryDocumentSnapshot doc : query) {

                        MenuItem item = new MenuItem();
                        item.setId(doc.getId());
                        item.setOrderNumber(doc.getLong("orderNumber"));
                        item.setPassengerUid(doc.getString("passengerUid"));
                        item.setDocPath(doc.getReference().getPath());

                        Long trainEta = doc.getLong("trainEtaEndTime");
                        item.setTrainEtaEndTime(trainEta != null ? trainEta : 0L);

                        Double totalPrice = doc.getDouble("totalPrice");
                        if (totalPrice != null) {
                            Map<String, Double> map = new HashMap<>();
                            map.put("Total", totalPrice);
                            item.setVariations(map);
                        }

                        orderList.add(item);
                    }

                    adapter.notifyDataSetChanged();

                    boolean empty = orderList.isEmpty();
                    recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                    layoutNoOrders.setVisibility(empty ? View.VISIBLE : View.GONE);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (orderListener != null) {
            orderListener.remove();
        }
    }

    // ================= ADAPTER =================

    private class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.ViewHolder> {

        private final ArrayList<MenuItem> items;

        OrdersAdapter(ArrayList<MenuItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.passanger_order_item_simple, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {

            MenuItem m = items.get(position);

            h.txtOrderId.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils.format(m.getOrderNumber(), m.getId()));

            double total = 0;
            if (m.getVariations() != null && !m.getVariations().isEmpty()) {
                total = m.getVariations().values().iterator().next();
            }

            h.txtTotalPrice.setText("Total: Rs " + total);

            // Module 2 - "Estimated Arrival" (auto-updates via the Firestore
            // snapshot listener whenever trainEtaEndTime changes - no need
            // to open the order detail screen for this to refresh).
            bindEtaArrival(h.txtEtaArrival, m.getTrainEtaEndTime());

            // ACTIVE TAB UI
            h.btnAccept.setVisibility(View.VISIBLE);
            h.timeRow.setVisibility(View.GONE);

            // Module 3 - Reject button (reuses the delete icon slot, which
            // was previously unused on this tab). Rejecting a still-pending
            // order releases the Stripe hold on the passenger's card - see
            // onOrderPaymentReversal.js. The passenger is never charged for
            // an order the restaurant declined.
            h.btnDelete.setVisibility(View.VISIBLE);
            h.btnDelete.setOnClickListener(v -> {

                // Module: restaurant must give a reason when rejecting -
                // passed straight through to the passenger's notification
                // (onOrderPaymentReversal.js) instead of a generic message.
                android.widget.EditText input = new android.widget.EditText(requireContext());
                input.setHint("e.g. Kitchen is closed, item out of stock...");
                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                input.setPadding(pad, pad, pad, pad);

                new AlertDialog.Builder(requireContext())
                        .setTitle("Reject Order")
                        .setMessage("Order reject karne ki wajah likhein - passenger ko yehi wajah dikhai jayegi.")
                        .setView(input)
                        .setPositiveButton("Reject Order", (d, w) -> {

                            String reason = input.getText() != null
                                    ? input.getText().toString().trim() : "";

                            if (reason.isEmpty()) {
                                reason = "the restaurant is currently unable to accept new orders";
                            }

                            updateOrderStatus(m, "Rejected", reason);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            // ACCEPT CLICK
            h.btnAccept.setOnClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle("Accept Order")
                        .setMessage("Kya aap is order ko accept karna chahte ho? Accept karte hi passenger ka payment charge ho jayega.")
                        .setPositiveButton("Yes", (d, w) -> updateOrderStatus(m, "Accepted", null))
                        .setNegativeButton("No", null)
                        .show();
            });

            // ITEM CLICK → DETAIL
            h.itemView.setOnClickListener(v -> {
                Fragment detailFragment = OrderDetailFragment.newInstance(m.getId());

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_container, detailFragment)
                        .addToBackStack("order_detail")
                        .commit();
            });
        }

        private void updateOrderStatus(MenuItem m, String status, String rejectionReason) {

            DocumentReference globalRef = firestore.document(m.getDocPath());

            WriteBatch batch = firestore.batch();

            batch.update(globalRef, "orderStatus", status);

            if (rejectionReason != null) {
                batch.update(globalRef, "rejectionReason", rejectionReason);
            }

            batch.commit().addOnSuccessListener(a -> {
                Toast.makeText(requireContext(),
                        "Order " + status,
                        Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /**
         * Module 2 - shows the trainEtaEndTime (millis) as a clock time,
         * e.g. "Estimated Arrival: 10:00 PM". Shows "Calculating..." until
         * the value has been computed at least once (order placement writes
         * an initial estimate immediately, so this is rarely empty for long
         * - see OrderNowFragment.placeOrder()).
         */
        private void bindEtaArrival(TextView txtEtaArrival, long trainEtaEndTimeMillis) {

            if (trainEtaEndTimeMillis <= 0) {
                txtEtaArrival.setText("Estimated Arrival: Calculating...");
                return;
            }

            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());

            txtEtaArrival.setText("Estimated Arrival: " + fmt.format(new java.util.Date(trainEtaEndTimeMillis)));
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            TextView txtOrderId, txtTotalPrice, txtEtaArrival;
            ImageView btnAccept, btnDelete;
            LinearLayout timeRow;

            ViewHolder(@NonNull View itemView) {
                super(itemView);

                txtOrderId = itemView.findViewById(R.id.txtOrderId);
                txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);
                txtEtaArrival = itemView.findViewById(R.id.txtEtaArrival);
                btnAccept = itemView.findViewById(R.id.btnAccept);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                timeRow = itemView.findViewById(R.id.timeRow);
            }
        }
    }
}





















//package com.example.paktrainfoodapp.ui.main.Restaurant;
//
//import android.app.AlertDialog;
//import android.os.Bundle;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.fragment.app.Fragment;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.example.paktrainfoodapp.R;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.firestore.DocumentReference;
//import com.google.firebase.firestore.FirebaseFirestore;
//import com.google.firebase.firestore.ListenerRegistration;
//import com.google.firebase.firestore.QueryDocumentSnapshot;
//import com.google.firebase.firestore.WriteBatch;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.Map;
//
//public class ActiveOrdersFragment extends Fragment {
//
//    private RecyclerView recyclerView;
//    private LinearLayout layoutNoOrders;
//
//    private ArrayList<MenuItem> orderList;
//    private OrdersAdapter adapter;
//
//    private FirebaseFirestore firestore;
//    private String restaurantUid;
//
//    private ListenerRegistration orderListener;
//
//    @Override
//    public View onCreateView(@NonNull LayoutInflater inflater,
//                             ViewGroup container,
//                             Bundle savedInstanceState) {
//
//        View view = inflater.inflate(
//                R.layout.fragment_restaurant_orders_accept_pending_complete,
//                container,
//                false
//        );
//
//        recyclerView = view.findViewById(R.id.recyclerOrders);
//        layoutNoOrders = view.findViewById(R.id.layoutNoOrders);
//
//        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
//
//        orderList = new ArrayList<>();
//        adapter = new OrdersAdapter(orderList);
//        recyclerView.setAdapter(adapter);
//
//        firestore = FirebaseFirestore.getInstance();
//
//        restaurantUid = FirebaseAuth.getInstance().getCurrentUser() != null
//                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
//                : "";
//
//        return view;
//    }
//
//    @Override
//    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
//        super.onViewCreated(view, savedInstanceState);
//
//        if (!restaurantUid.isEmpty()) {
//            loadOrders();
//        }
//    }
//
//    private void loadOrders() {
//
//        if (orderListener != null) {
//            orderListener.remove();
//        }
//
//        orderListener = firestore.collection("Orders")
//                .whereEqualTo("restaurantUid", restaurantUid)
//                .whereEqualTo("orderStatus", "Active")
//                .addSnapshotListener((query, e) -> {
//
//                    if (e != null || query == null || !isAdded()) return;
//
//                    orderList.clear();
//
//                    for (QueryDocumentSnapshot doc : query) {
//
//                        MenuItem item = new MenuItem();
//                        item.setId(doc.getId());
//                        item.setPassengerUid(doc.getString("passengerUid"));
//                        item.setDocPath(doc.getReference().getPath());
//
//                        Double totalPrice = doc.getDouble("totalPrice");
//                        if (totalPrice != null) {
//                            Map<String, Double> map = new HashMap<>();
//                            map.put("Total", totalPrice);
//                            item.setVariations(map);
//                        }
//
//                        orderList.add(item);
//                    }
//
//                    adapter.notifyDataSetChanged();
//
//                    boolean empty = orderList.isEmpty();
//                    recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
//                    layoutNoOrders.setVisibility(empty ? View.VISIBLE : View.GONE);
//                });
//    }
//
//    @Override
//    public void onDestroyView() {
//        super.onDestroyView();
//        if (orderListener != null) {
//            orderListener.remove();
//        }
//    }
//
//    // ================= ADAPTER =================
//
//    private class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.ViewHolder> {
//
//        private final ArrayList<MenuItem> items;
//
//        OrdersAdapter(ArrayList<MenuItem> items) {
//            this.items = items;
//        }
//
//        @NonNull
//        @Override
//        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//            View v = LayoutInflater.from(parent.getContext())
//                    .inflate(R.layout.passanger_order_item_simple, parent, false);
//            return new ViewHolder(v);
//        }
//
//        @Override
//        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
//
//            MenuItem m = items.get(position);
//
//            h.txtOrderId.setText("#" + m.getId());
//
//            double total = 0;
//            if (m.getVariations() != null && !m.getVariations().isEmpty()) {
//                total = m.getVariations().values().iterator().next();
//            }
//
//            h.txtTotalPrice.setText("Total: Rs " + total);
//
//            // ACTIVE TAB UI
//            h.btnAccept.setVisibility(View.VISIBLE);
//            h.timeRow.setVisibility(View.GONE);
//
//            // ACCEPT CLICK
//            h.btnAccept.setOnClickListener(v -> {
//                new AlertDialog.Builder(requireContext())
//                        .setTitle("Accept Order")
//                        .setMessage("Kya aap is order ko accept karna chahte ho?")
//                        .setPositiveButton("Yes", (d, w) -> updateOrderStatus(m, "Accepted"))
//                        .setNegativeButton("No", null)
//                        .show();
//            });
//
//            // ITEM CLICK → DETAIL
//            h.itemView.setOnClickListener(v -> {
//                Fragment detailFragment = OrderDetailFragment.newInstance(m.getId());
//
//                requireActivity().getSupportFragmentManager()
//                        .beginTransaction()
//                        .replace(R.id.main_container, detailFragment)
//                        .addToBackStack("order_detail")
//                        .commit();
//            });
//        }
//
//        private void updateOrderStatus(MenuItem m, String status) {
//
//            DocumentReference globalRef = firestore.document(m.getDocPath());
//
//            DocumentReference passRef = firestore.collection("Users")
//                    .document("Passenger")
//                    .collection("OrderNow")
//                    .document(m.getPassengerUid())
//                    .collection("Orders")
//                    .document(m.getId());
//
//            WriteBatch batch = firestore.batch();
//
//            batch.update(globalRef, "orderStatus", status);
//            batch.update(passRef, "orderStatus", status);
//
//            batch.commit().addOnSuccessListener(a -> {
//                Toast.makeText(requireContext(),
//                        "Order " + status,
//                        Toast.LENGTH_SHORT).show();
//            });
//        }
//
//        @Override
//        public int getItemCount() {
//            return items.size();
//        }
//
//        class ViewHolder extends RecyclerView.ViewHolder {
//
//            TextView txtOrderId, txtTotalPrice;
//            ImageView btnAccept;
//            LinearLayout timeRow;
//
//            ViewHolder(@NonNull View itemView) {
//                super(itemView);
//
//                txtOrderId = itemView.findViewById(R.id.txtOrderId);
//                txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);
//                btnAccept = itemView.findViewById(R.id.btnAccept);
//                timeRow = itemView.findViewById(R.id.timeRow);
//            }
//        }
//    }
//}
//
//
//
//
//
//
//
//
//
//
