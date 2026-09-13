package com.example.paktrainfoodapp.ui.main.Passenger.order;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader;
import com.example.paktrainfoodapp.utils.Refreshable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;

public class ActiveOrdersFragment extends Fragment implements Refreshable {

    private RecyclerView recyclerView;
    private LinearLayout layoutNoOrders;

    private ArrayList<OrderModel> orderList;
    private OrdersAdapter adapter;

    private FirebaseFirestore firestore;
    private ProgressBar progressBar;

    private String uid;

    private ListenerRegistration listenerRegistration;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_passanger_orders_accept_pending_complete,
                container,
                false
        );

        recyclerView = view.findViewById(R.id.recyclerOrders);
        layoutNoOrders = view.findViewById(R.id.layoutNoOrders);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        orderList = new ArrayList<>();
        firestore = FirebaseFirestore.getInstance();

        uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        adapter = new OrdersAdapter(orderList, firestore, uid, this);
        recyclerView.setAdapter(adapter);

        if (uid == null) {

            recyclerView.setVisibility(View.GONE);
            layoutNoOrders.setVisibility(View.VISIBLE);

            return view;
        }

        listenOrdersRealtime();

        return view;
    }

    private void listenOrdersRealtime() {

        Query ordersQuery = firestore.collection("Orders")
                .whereEqualTo("passengerUid", uid);

        if (listenerRegistration != null)
            listenerRegistration.remove();

        listenerRegistration = ordersQuery.addSnapshotListener((snap, e) -> {

            if (e != null || snap == null) return;

            orderList.clear();

            for (DocumentSnapshot doc : snap.getDocuments()) {

                String status = doc.getString("orderStatus");

                // ✅ FIX: this tab used to also pull in "Cancelled" orders
                // (styled red, with the raw Firestore doc id instead of a
                // formatted order number) - a cancelled order isn't
                // "active" any more, so it now has its own tab
                // (see CancelledOrdersFragment) and this one only ever
                // shows orders that are actually still pending.
                if ("Active".equalsIgnoreCase(status)) {

                    String orderId = doc.getId();

                    double totalPrice =
                            doc.getDouble("totalPrice") != null
                                    ? doc.getDouble("totalPrice")
                                    : 0;

                    Long trainEta = doc.getLong("trainEtaEndTime");
                    long trainEtaEndTime = trainEta != null ? trainEta : 0L;

                    OrderModel model = new OrderModel(orderId, totalPrice, status, trainEtaEndTime);
                    model.setOrderNumber(doc.getLong("orderNumber"));
                    model.setRestaurantName(doc.getString("restaurantName"));
                    model.setMealStation(doc.getString("mealStation"));
                    Long ts = doc.getLong("timestamp");
                    model.setTimestamp(ts != null ? ts : 0L);
                    orderList.add(model);
                }
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

        if (listenerRegistration != null) {

            listenerRegistration.remove();

            listenerRegistration = null;
        }
    }

    // ================= ADAPTER =================

    private static class OrdersAdapter
            extends RecyclerView.Adapter<OrdersAdapter.OrderViewHolder> {

        private final ArrayList<OrderModel> items;
        private final FirebaseFirestore firestore;
        private final String uid;
        private final Fragment fragment;

        OrdersAdapter(ArrayList<OrderModel> items,
                      FirebaseFirestore firestore,
                      String uid,
                      Fragment fragment) {

            this.items = items;
            this.firestore = firestore;
            this.uid = uid;
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent,
                int viewType
        ) {

            View v = LayoutInflater.from(parent.getContext())
                    .inflate(
                            R.layout.passanger_order_item_simple,
                            parent,
                            false
                    );

            return new OrderViewHolder(v);
        }

        @Override
        public void onBindViewHolder(
                @NonNull OrderViewHolder holder,
                int position
        ) {

            OrderModel order = items.get(position);

            // ================= ORDER ID =================
            // Every order reaching this adapter is "Active" now (see the
            // fragment's query filter), so the cancelled-specific styling
            // that used to live here is gone - it's all handled by
            // CancelledOrdersFragment instead.

            holder.itemView.setBackgroundColor(Color.WHITE);

            holder.txtOrderId.setText(
                    com.example.paktrainfoodapp.utils.OrderNumberUtils.format(order.getOrderNumber(), order.getOrderId())
            );

            holder.txtOrderId.setTextColor(Color.BLACK);

            holder.btnDelete.setVisibility(View.VISIBLE);

            if (holder.txtRestaurantName != null) {
                String restName = order.getRestaurantName();
                if (restName != null && !restName.isEmpty()) {
                    holder.txtRestaurantName.setVisibility(View.VISIBLE);
                    holder.txtRestaurantName.setText(restName);
                } else {
                    holder.txtRestaurantName.setVisibility(View.GONE);
                }
            }

            if (holder.txtStation != null) {
                String st = order.getMealStation();
                if (st != null && !st.isEmpty()) {
                    holder.txtStation.setVisibility(View.VISIBLE);
                    holder.txtStation.setText("Station: " + st);
                } else {
                    holder.txtStation.setVisibility(View.GONE);
                }
            }

            if (holder.txtOrderDate != null) {
                if (order.getTimestamp() > 0) {
                    holder.txtOrderDate.setText(new java.text.SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                            .format(new java.util.Date(order.getTimestamp())));
                } else {
                    holder.txtOrderDate.setText("");
                }
            }

            holder.txtTotalPrice.setText(
                    "Total: Rs " + order.getTotalPrice()
            );

            // Status pill - top-right corner, same for every role/tab.
            if (holder.txtStatusBadge != null) {
                com.example.paktrainfoodapp.utils.StatusBadge.apply(holder.txtStatusBadge, order.getStatus());
            }

            // Module: bind "Estimated Arrival" - the layout already has
            // this label, it just was never populated with data before.
            if (order.getTrainEtaEndTime() <= 0) {
                holder.txtEtaArrival.setVisibility(View.GONE);
            } else {
                holder.txtEtaArrival.setVisibility(View.VISIBLE);
                java.text.SimpleDateFormat fmt =
                        new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
                holder.txtEtaArrival.setText(
                        "Estimated Arrival: " + fmt.format(new java.util.Date(order.getTrainEtaEndTime())));
            }

            // ================= OPEN DETAIL =================

            holder.itemView.setOnClickListener(v -> {

                int pos = holder.getAdapterPosition();

                if (pos == RecyclerView.NO_POSITION) return;

                OrderModel selected = items.get(pos);

                passanger_orderDetailFragment detailFragment =
                        new passanger_orderDetailFragment();

                Bundle bundle = new Bundle();

                bundle.putString(
                        "orderId",
                        selected.getOrderId()
                );

                detailFragment.setArguments(bundle);

                if (fragment.getParentFragment() instanceof OrderFragment) {

                    OrderFragment orderFragment =
                            (OrderFragment) fragment.getParentFragment();

                    if (orderFragment.getParentFragment()
                            instanceof Passenger_Fragment_Loader) {

                        Passenger_Fragment_Loader loader =
                                (Passenger_Fragment_Loader)
                                        orderFragment.getParentFragment();

                        loader.openOrderDetail(selected.getOrderId());

                    }

                }
            });

            // ================= DELETE =================

            holder.btnDelete.setOnClickListener(v -> {

                new AlertDialog.Builder(fragment.requireContext())
                        .setTitle("Cancel Order")
                        .setMessage("Cancel this order?")
                        .setPositiveButton("Yes", (d, w) -> {

                            int pos = holder.getAdapterPosition();

                            if (pos == RecyclerView.NO_POSITION)
                                return;

                            OrderModel selected = items.get(pos);

                            String orderId =
                                    selected.getOrderId();

                            firestore.collection("Orders")
                                    .document(orderId)
                                    .delete();

                            items.remove(pos);

                            notifyItemRemoved(pos);

                            Toast.makeText(
                                    fragment.getContext(),
                                    "Order Deleted",
                                    Toast.LENGTH_SHORT
                            ).show();

                        })
                        .setNegativeButton("No", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        // ================= VIEW HOLDER =================

        static class OrderViewHolder
                extends RecyclerView.ViewHolder {

            TextView txtOrderId, txtTotalPrice, txtEtaArrival, txtRestaurantName, txtStation, txtOrderDate, txtStatusBadge;

            android.widget.ImageView btnDelete;

            OrderViewHolder(@NonNull View itemView) {

                super(itemView);

                txtOrderId =
                        itemView.findViewById(R.id.txtOrderId);

                txtRestaurantName =
                        itemView.findViewById(R.id.txtRestaurantName);
                txtStation = itemView.findViewById(R.id.txtStation);
                txtOrderDate = itemView.findViewById(R.id.txtOrderDate);

                txtTotalPrice =
                        itemView.findViewById(R.id.txtTotalPrice);

                txtEtaArrival =
                        itemView.findViewById(R.id.txtEtaArrival);

                txtStatusBadge =
                        itemView.findViewById(R.id.txtStatusBadge);

                btnDelete =
                        itemView.findViewById(R.id.btnDelete);
            }
        }
    }

    @Override
    public void refreshData() {

        orderList.clear();

        adapter.notifyDataSetChanged();

        listenOrdersRealtime();
    }
}























