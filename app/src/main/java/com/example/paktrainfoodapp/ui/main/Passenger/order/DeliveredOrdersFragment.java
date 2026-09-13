package com.example.paktrainfoodapp.ui.main.Passenger.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.utils.Refreshable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;

public class DeliveredOrdersFragment extends Fragment implements Refreshable {

    private RecyclerView recyclerView;
    private LinearLayout layoutNoOrders;

    private ArrayList<OrderModel> orderList;
    private OrdersAdapter adapter;

    private FirebaseFirestore firestore;

    private ListenerRegistration listenerRegistration;
    private String uid;

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

        adapter = new OrdersAdapter(orderList);
        recyclerView.setAdapter(adapter);
        if (uid == null) {
            Toast.makeText(getContext(),
                    "User not logged in",
                    Toast.LENGTH_SHORT).show();
            return view;
        }
        loadDeliveredOrders();

        return view;
    }

    private void loadDeliveredOrders() {

        Query ordersQuery = firestore.collection("Orders")
                .whereEqualTo("passengerUid", uid);

        if (listenerRegistration != null)
            listenerRegistration.remove();

        listenerRegistration = ordersQuery.addSnapshotListener((snap, e) -> {

            if (e != null || snap == null) return;

            orderList.clear();

            for (DocumentSnapshot doc : snap.getDocuments()) {

                String status = doc.getString("orderStatus");

                // ONLY THESE STATUSES
                // pick_up belongs here - the rider has the food and is
                // actively delivering it (see CompletedOrdersFragment).
                if (!"accepted_by_rider".equalsIgnoreCase(status)
                        && !"arrive_rider_at_resturent".equalsIgnoreCase(status)
                        && !"dropped".equalsIgnoreCase(status)
                        && !"pick_up".equalsIgnoreCase(status)) {
                    continue;
                }

                String orderId = doc.getId();

                Double totalPrice = doc.getDouble("totalPrice");
                double price = totalPrice != null ? totalPrice : 0;

                OrderModel model = new OrderModel(orderId, price, status);
                model.setOrderNumber(doc.getLong("orderNumber"));
                    model.setRestaurantName(doc.getString("restaurantName"));
                    model.setMealStation(doc.getString("mealStation"));
                    Long ts = doc.getLong("timestamp");
                    model.setTimestamp(ts != null ? ts : 0L);

                // ✅ FIX: this tab never populated trainEtaEndTime at all -
                // the ETA line stuck on the layout's placeholder text
                // ("Estimated Arrival: --") permanently once an order left
                // the Active tab.
                Long trainEta = doc.getLong("trainEtaEndTime");
                model.setTrainEtaEndTime(trainEta != null ? trainEta : 0L);

                orderList.add(model);
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
    private class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.VH> {

        private final ArrayList<OrderModel> items;

        OrdersAdapter(ArrayList<OrderModel> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.passanger_order_item_simple, parent, false);

            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {

            OrderModel order = items.get(position);

            holder.txtOrderId.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils.format(order.getOrderNumber(), order.getOrderId()));

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

            // Raw countdown removed - only the arrival estimate is shown.
            if (holder.txtTimer != null) holder.txtTimer.setVisibility(View.GONE);
            holder.txtTotalPrice.setText("Total: Rs " + order.getTotalPrice());

            // Same "Estimated Arrival" binding the Active tab uses.
            if (holder.txtEtaArrival != null) {
                if (order.getTrainEtaEndTime() <= 0) {
                    holder.txtEtaArrival.setVisibility(View.GONE);
                } else {
                    holder.txtEtaArrival.setVisibility(View.VISIBLE);
                    java.text.SimpleDateFormat fmt =
                            new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());
                    holder.txtEtaArrival.setText(
                            "Estimated Arrival: " + fmt.format(new java.util.Date(order.getTrainEtaEndTime())));
                }
            }

            holder.timeRow.setVisibility(View.VISIBLE);
            holder.btnReady.setEnabled(false);
            holder.btnReady.setAlpha(0.6f);

            String status = order.getStatus();

            if (holder.txtStatusBadge != null) {
                com.example.paktrainfoodapp.utils.StatusBadge.apply(holder.txtStatusBadge, status);
            }

            if ("accepted_by_rider".equalsIgnoreCase(status)) {
                holder.btnReady.setText("Accepted by Rider..");
            }

            else if ("arrive_rider_at_resturent".equalsIgnoreCase(status)) {
                holder.btnReady.setText("Rider Arrived at Restaurant");
            }

            else if ("dropped".equalsIgnoreCase(status)) {
                holder.btnReady.setText("Restaurant Dropped Order to Rider");
            }

            // click only toast
            // ✅ FIX: this only ever popped a Toast with the raw order id -
            // that black pill in the middle of the screen. The passenger
            // had no way to open their own order at all once it left the
            // Active tab: no details, no live map, no rider tracking.
            // Now it opens the same detail screen the Active tab does,
            // which itself offers "Track Live on Map" once a rider is
            // assigned.
            holder.itemView.setOnClickListener(v -> {

                if (!isAdded()) return;

                passanger_orderDetailFragment detail = new passanger_orderDetailFragment();

                Bundle args = new Bundle();
                args.putString("orderId", order.getOrderId());
                detail.setArguments(args);

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_container, detail)
                        .addToBackStack(null)
                        .commit();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

         class VH extends RecyclerView.ViewHolder {

            TextView txtOrderId, txtTotalPrice, txtTimer, txtEtaArrival, txtRestaurantName, txtStation, txtOrderDate, txtStatusBadge;
            LinearLayout timeRow;
            Button btnReady;

            VH(@NonNull View itemView) {
                super(itemView);

                txtOrderId = itemView.findViewById(R.id.txtOrderId);
                txtRestaurantName = itemView.findViewById(R.id.txtRestaurantName);
                txtStation = itemView.findViewById(R.id.txtStation);
                txtOrderDate = itemView.findViewById(R.id.txtOrderDate);
            txtTimer = itemView.findViewById(R.id.txtTimer);
                txtEtaArrival = itemView.findViewById(R.id.txtEtaArrival);
                txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);
                txtStatusBadge = itemView.findViewById(R.id.txtStatusBadge);

                timeRow = itemView.findViewById(R.id.timeRow);
                btnReady = itemView.findViewById(R.id.btnReady);
            }
        }
    }

    @Override
    public void refreshData() {
        orderList.clear();
        adapter.notifyDataSetChanged();
        loadDeliveredOrders();
    }
}