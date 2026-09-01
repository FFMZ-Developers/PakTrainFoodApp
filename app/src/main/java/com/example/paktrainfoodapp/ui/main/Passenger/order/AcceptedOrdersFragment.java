package com.example.paktrainfoodapp.ui.main.Passenger.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.utils.Refreshable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;

public class AcceptedOrdersFragment extends Fragment implements Refreshable {

    private RecyclerView recyclerView;
    private LinearLayout layoutNoOrders;

    private ArrayList<OrderModel> orderList;
    private OrdersAdapter adapter;

    private FirebaseFirestore firestore;
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

        loadOrders();

        return view;
    }

    private void loadOrders() {

        Query ordersQuery = firestore.collection("Orders")
                .whereEqualTo("passengerUid", uid);

        if (listenerRegistration != null)
            listenerRegistration.remove();

        listenerRegistration = ordersQuery.addSnapshotListener((snap, e) -> {

            if (e != null || snap == null) return;

            orderList.clear();

            for (DocumentSnapshot doc : snap.getDocuments()) {

                String status = doc.getString("orderStatus");

                // ONLY ALLOWED STATUSES
                if (!"Accepted".equalsIgnoreCase(status)
                        && !"ready_for_delivery".equalsIgnoreCase(status)) {
                    continue;
                }

                String orderId = doc.getId();

                Double totalPrice = doc.getDouble("totalPrice");
                double price = totalPrice != null ? totalPrice : 0;

                OrderModel model = new OrderModel(orderId, price, status);
                model.setOrderNumber(doc.getLong("orderNumber"));
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
    private  class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.OrderViewHolder> {

        private final ArrayList<OrderModel> items;
        private final Fragment fragment;

        OrdersAdapter(ArrayList<OrderModel> items,
                      FirebaseFirestore firestore,
                      String uid,
                      Fragment fragment) {

            this.items = items;
            this.fragment = fragment;
        }

        @NonNull
        @Override
        public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.passanger_order_item_simple, parent, false);

            return new OrderViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {

            OrderModel order = items.get(position);

            holder.txtOrderId.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils.format(order.getOrderNumber(), order.getOrderId()));

            // Raw countdown removed - only the arrival estimate is shown.
            if (holder.txtTimer != null) holder.txtTimer.setVisibility(View.GONE);
            holder.txtTotalPrice.setText("Total: Rs " + order.getTotalPrice());

            // SHOW TIME ROW ALWAYS
            holder.timeRow.setVisibility(View.VISIBLE);
            holder.btnReady.setEnabled(false);
            holder.btnReady.setAlpha(0.6f);

            String status = order.getStatus();

            if ("Accepted".equalsIgnoreCase(status)) {
                holder.btnReady.setText("Preparing...");
                holder.btnReady.setBackgroundColor(0xFFB0BEC5); // light gray
            }
            else if ("ready_for_delivery".equalsIgnoreCase(status)) {
                holder.btnReady.setText("Waiting for Rider Accept Order...");
                holder.btnReady.setBackgroundColor(0xFF90A4AE); // slightly darker gray
            }

            // CLICK ITEM -> ONLY TOAST
            // ✅ FIX: this only popped a Toast with the order number - the
            // passenger couldn't actually open their order from this tab.
            // Now it opens the full detail screen (which offers live
            // tracking once a rider is assigned).
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


         class OrderViewHolder extends RecyclerView.ViewHolder {

            TextView txtOrderId, txtTotalPrice, txtTimer;
            LinearLayout timeRow;
            Button btnReady;

            OrderViewHolder(@NonNull View itemView) {
                super(itemView);

                txtOrderId = itemView.findViewById(R.id.txtOrderId);
            txtTimer = itemView.findViewById(R.id.txtTimer);
                txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);

                timeRow = itemView.findViewById(R.id.timeRow);
                btnReady = itemView.findViewById(R.id.btnReady);
            }
        }
    }

    @Override
    public void refreshData() {

        recyclerView.setVisibility(View.GONE);

        orderList.clear();
        adapter.notifyDataSetChanged();

        loadOrders();

        recyclerView.postDelayed(() -> recyclerView.setVisibility(View.VISIBLE), 1000);
    }
}