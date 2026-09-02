package com.example.paktrainfoodapp.ui.main.Delivery.order;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Delivery.DeliveryBoyModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;


import java.util.ArrayList;

public class Order_Complete_Fragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout layoutNoOrders;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private ArrayList<DeliveryBoyModel> orderList;
    private CompleteAdapter adapter;

    private String riderId;

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

        riderId = (auth.getCurrentUser() != null)
                ? auth.getCurrentUser().getUid()
                : "";

        orderList = new ArrayList<>();
        adapter = new CompleteAdapter(orderList);
        recyclerView.setAdapter(adapter);

        loadOrders();

        return view;
    }

    // ================= LOAD ORDERS =================
    private void loadOrders() {

        if (riderId == null || riderId.isEmpty()) return;

        db.collection("Orders")
                .whereEqualTo("acceptedBy", riderId)
                .addSnapshotListener((query, e) -> {

                    if (e != null || query == null) return;

                    orderList.clear();

                    for (QueryDocumentSnapshot doc : query) {

                        String status = doc.getString("orderStatus");
                        Double price = doc.getDouble("totalPrice");

                        // ONLY THESE STATUS FROM YOUR FLOW
                        // Completed means finished - pick_up moved to the
                        // Accept tab where the remaining work lives.
                        if (!"completed".equals(status)) {
                            continue;
                        }

                        DeliveryBoyModel model = new DeliveryBoyModel(
                                doc.getId(),
                                price != null ? price : 0.0,
                                doc.getReference().getPath()
                        );

                        model.setStatus(status);

                        // ✅ FIX: orderNumber was never populated here at
                        // all - this tab always fell back to the short
                        // document-id display, never the real Order #0001
                        // format every other tab shows.
                        model.setOrderNumber(doc.getLong("orderNumber"));
                        model.setRestaurantName(doc.getString("restaurantName"));

                        orderList.add(model);
                    }

                    adapter.notifyDataSetChanged();

                    boolean empty = orderList.isEmpty();
                    recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                    layoutNoOrders.setVisibility(empty ? View.VISIBLE : View.GONE);
                });
    }

    // ================= ADAPTER =================
    private class CompleteAdapter extends RecyclerView.Adapter<CompleteAdapter.VH> {

        private final ArrayList<DeliveryBoyModel> list;

        CompleteAdapter(ArrayList<DeliveryBoyModel> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.passanger_order_item_simple, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {

            DeliveryBoyModel model = list.get(position);

            h.txtOrderId.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils.format(model.getOrderNumber(), model.getOrderId()));

            if (h.txtRestaurantName != null) {
                String restName = model.getRestaurantName();
                if (restName != null && !restName.isEmpty()) {
                    h.txtRestaurantName.setVisibility(View.VISIBLE);
                    h.txtRestaurantName.setText(restName);
                } else {
                    h.txtRestaurantName.setVisibility(View.GONE);
                }
            }

            // Raw countdown removed - only the arrival estimate is shown.
            if (h.txtTime != null) h.txtTime.setVisibility(View.GONE);
            h.txtPrice.setText("Rs " + model.getTotalPrice());
            h.timeRow.setVisibility(View.GONE);

            // Every order in this tab is already "completed" (see the
            // query filter above) - no ETA means anything for a delivered
            // order, so it's hidden rather than showing the layout's
            // leftover "Estimated Arrival: --" placeholder.
            if (h.txtEtaArrival != null) h.txtEtaArrival.setVisibility(View.GONE);

            String status = model.getStatus();

            if (h.txtStatusBadge != null) {
                com.example.paktrainfoodapp.utils.StatusBadge.apply(h.txtStatusBadge, status);
            }

            // ✅ FIX: cards here had no click handler for in-progress
            // orders, so once the rider picked the food up they lost the
            // tracking map entirely - exactly when they most need it,
            // since that's the leg where they're driving to the station.
            // And completed orders opened nothing at all - now they open
            // the same screen, which shows the simplified completed
            // summary (order id, status, delivered time, items, chat)
            // instead of the live map once the order is actually done.
            h.itemView.setOnClickListener(v -> {

                if (!isAdded()) return;

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_container,
                                com.example.paktrainfoodapp.ui.main.Restaurant.order
                                        .OrderDetailFragment.newInstance(model.getOrderId()))
                        .addToBackStack(null)
                        .commit();
            });

            // RESET
            h.btnAction.setVisibility(View.GONE);
            h.btnAction.setEnabled(true);
            h.btnAction.setAlpha(1f);

            // ================= STATUS UI =================
            // This tab only ever holds "completed" orders (see the query
            // filter above) - the top-right status pill already says
            // "Delivered", so there's nothing actionable left to show.

            if ("pick_up".equals(status)) {

                h.timeRow.setVisibility(View.VISIBLE);
                h.btnAction.setVisibility(View.VISIBLE);
                h.btnAction.setText("Hand Over to Passenger");

                h.btnAction.setOnClickListener(v -> {

                    new AlertDialog.Builder(requireContext())
                            .setTitle("Confirm Delivery")
                            .setMessage("Kya aap order handover kar chuke hain?")
                            .setPositiveButton("YES", (dialog, which) -> {

                                updateStatus(model, "completed");

                                Toast.makeText(getContext(),
                                        "Order Delivered",
                                        Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("NO", null)
                            .show();
                });
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends RecyclerView.ViewHolder {

            TextView txtOrderId, txtPrice, txtTime, txtRestaurantName, txtStatusBadge, txtEtaArrival;
            Button btnAction;
            LinearLayout timeRow;

            VH(@NonNull View itemView) {
                super(itemView);

                txtOrderId = itemView.findViewById(R.id.txtOrderId);
                txtRestaurantName = itemView.findViewById(R.id.txtRestaurantName);
                txtPrice = itemView.findViewById(R.id.txtTotalPrice);
                txtStatusBadge = itemView.findViewById(R.id.txtStatusBadge);
                txtEtaArrival = itemView.findViewById(R.id.txtEtaArrival);
                btnAction = itemView.findViewById(R.id.btnReady);
                timeRow = itemView.findViewById(R.id.timeRow);
                txtTime=itemView.findViewById(R.id.txtTimer);
            }
        }
    }

    // ================= UPDATE STATUS =================
    private void updateStatus(DeliveryBoyModel model, String status) {

        db.collection("Orders")
                .document(model.getOrderId())
                .update("orderStatus", status);
    }
}