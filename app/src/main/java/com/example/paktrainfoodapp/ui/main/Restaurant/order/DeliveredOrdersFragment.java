package com.example.paktrainfoodapp.ui.main.Restaurant.order;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Restaurant.menu.MenuItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class DeliveredOrdersFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout layoutNoOrders;

    private ArrayList<MenuItem> orderList;
    private OrdersAdapter adapter;

    private FirebaseFirestore firestore;
    private String restaurantUid;

    private ListenerRegistration listener;

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

    // ================= LOAD ORDERS =================
    private void loadOrders() {

        if (listener != null) listener.remove();

        listener = firestore.collection("Orders")
                .whereEqualTo("restaurantId", restaurantUid)
                .addSnapshotListener((query, e) -> {

                    if (e != null || query == null || !isAdded()) return;

                    orderList.clear();

                    for (QueryDocumentSnapshot doc : query) {

                        String status = doc.getString("orderStatus");
                        if (status == null) continue;

                        // ONLY THESE STATUSES
                        if (!status.equals("accepted_by_rider") &&
                                !status.equals("arrive_rider_at_resturent") &&
                                !status.equals("dropped") &&
                                !status.equals("pick_up")) {
                            continue;
                        }

                        MenuItem item = new MenuItem();
                        item.setId(doc.getId());
                        item.setOrderNumber(doc.getLong("orderNumber"));
                        item.setStatus(status);

                        Long time = doc.getLong("etaEndTime");
                        item.setEtaEndTime(time != null ? time : 0L);

                        Double price = doc.getDouble("totalPrice");
                        if (price != null) {
                            Map<String, Double> map = new HashMap<>();
                            map.put("Total", price);
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

            double total = (m.getVariations() != null && !m.getVariations().isEmpty())
                    ? m.getVariations().values().iterator().next()
                    : 0;

            h.txtTotalPrice.setText("Rs " + total);

            String status = m.getStatus();

            // RESET
            h.btnReady.setEnabled(true);
            h.btnReady.setAlpha(1f);
            h.btnReady.setVisibility(View.VISIBLE);
             h.timeRow.setVisibility(View.VISIBLE);

            // Only the arrival estimate is meaningful here - the raw
            // txtTimer placeholder was showing through empty.
            bindEtaArrival(h.txtEtaArrival, m.getTrainEtaEndTime());
            h.txtTimer.setVisibility(View.GONE);

            // ✅ FIX: cards in this tab had no click handler at all, so
            // once an order moved here (rider assigned onwards) the
            // restaurant had no way to open the live tracking map and see
            // where their rider actually was. Same destination the
            // Accepted tab uses.
            h.itemView.setOnClickListener(v -> {

                if (!isAdded()) return;

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_container, OrderDetailFragment.newInstance(m.getId()))
                        .addToBackStack(null)
                        .commit();
            });

            // ================= STATUS LOGIC =================

            switch (status) {

                case "accepted_by_rider":
                    com.example.paktrainfoodapp.utils.StatusBadge.apply(h.btnReady, status);
                    h.btnReady.setEnabled(false);
                    h.btnReady.setAlpha(1f);
                    break;

                case "arrive_rider_at_resturent":
                    h.btnReady.setText("Dropped");
                    h.btnReady.setEnabled(true);
                    h.btnReady.setAlpha(1f);
                    break;

                case "dropped":
                    h.btnReady.setText("Waiting for Pickup");
                    h.btnReady.setEnabled(false);
                    h.btnReady.setAlpha(0.5f);
                    break;

                case "pick_up":
                    // Was hidden entirely here, which - now that Completed
                    // no longer carries pick_up - left the order invisible
                    // to the restaurant everywhere.
                    com.example.paktrainfoodapp.utils.StatusBadge.apply(h.btnReady, status);
                    h.btnReady.setEnabled(false);
                    h.btnReady.setAlpha(1f);
                    break;
            }

            // ================= CLICK =================
            h.btnReady.setOnClickListener(v -> {

                String currentStatus = m.getStatus();

                Map<String, Object> map = new HashMap<>();

                // CASE 1: rider arrived → drop order
                if ("arrive_rider_at_resturent".equals(currentStatus)) {

                    map.put("orderStatus", "dropped");

                    firestore.collection("Orders")
                            .document(m.getId())
                            .update(map)
                            .addOnSuccessListener(unused -> {

                                m.setStatus("dropped");
                                notifyItemChanged(h.getAdapterPosition());

                                Toast.makeText(getContext(),
                                        "Order Dropped",
                                        Toast.LENGTH_SHORT).show();
                            });

                }

                // CASE 2: already dropped → optional action or disable
                else if ("dropped".equals(currentStatus)) {

                    Toast.makeText(getContext(),
                            "Already Dropped - Waiting for Pickup",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            TextView txtOrderId, txtTotalPrice, txtTimer, txtEtaArrival;
            Button btnReady;
LinearLayout timeRow;
            ViewHolder(@NonNull View itemView) {
                super(itemView);

                txtOrderId = itemView.findViewById(R.id.txtOrderId);
                txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);
                btnReady = itemView.findViewById(R.id.btnReady);
                timeRow = itemView.findViewById(R.id.timeRow);
                txtTimer = itemView.findViewById(R.id.txtTimer);
                txtEtaArrival = itemView.findViewById(R.id.txtEtaArrival);
            }
        }
    }

    private void bindEtaArrival(TextView view, long trainEtaEndTimeMillis) {

        if (view == null) return;

        if (trainEtaEndTimeMillis <= 0) {
            view.setText("Estimated Arrival: Calculating...");
            return;
        }

        view.setText("Estimated Arrival: " + new java.text.SimpleDateFormat(
                "hh:mm a", java.util.Locale.getDefault())
                .format(new java.util.Date(trainEtaEndTimeMillis)));
    }
}