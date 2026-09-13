package com.example.paktrainfoodapp.ui.main.Passenger.order;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;

import java.util.ArrayList;

public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.ViewHolder> {

    private final ArrayList<OrderModel> items;
    private final String userRole;
    private final OrderActionListener listener;

    // CALLBACK INTERFACE
    public interface OrderActionListener {
        void onDeleteClick(OrderModel model, int position);
        void onAcceptClick(OrderModel model, int position);
        void onReadyClick(OrderModel model, int position);
        void onItemClick(OrderModel model);
    }

    public OrdersAdapter(ArrayList<OrderModel> items, String userRole, OrderActionListener listener) {
        this.items = items;
        this.userRole = userRole;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.passanger_order_item_simple, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {

        OrderModel m = items.get(position);

        if (m == null) return;

        h.txtOrderId.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils.format(m.getOrderNumber(), m.getOrderId()));
        h.txtTotalPrice.setText("Total: Rs " + m.getTotalPrice());

        // RESET UI (VERY IMPORTANT)
        h.btnDelete.setVisibility(View.GONE);
        h.btnAccept.setVisibility(View.GONE);
        h.timeRow.setVisibility(View.GONE);
        h.btnReady.setVisibility(View.GONE);
        h.btnReady.setEnabled(true);

        String status = m.getStatus();

        // ================= RESTAURANT LOGIC =================
        if ("Restaurant".equals(userRole)) {

            if ("Active".equals(status)) {

                h.btnAccept.setVisibility(View.VISIBLE);
                h.btnDelete.setVisibility(View.VISIBLE);

                h.btnReady.setVisibility(View.GONE);

            } else if ("Accepted".equals(status)) {

                h.timeRow.setVisibility(View.VISIBLE);
                h.btnReady.setVisibility(View.VISIBLE);
                h.btnReady.setText("Ready for Delivery");
                h.btnReady.setEnabled(true);

            } else if ("WFR".equals(status)) {

                h.timeRow.setVisibility(View.VISIBLE);
                h.btnReady.setVisibility(View.VISIBLE);
                h.btnReady.setText("Sent to Rider");
                h.btnReady.setEnabled(false);
            }
        }

        // ================= RIDER LOGIC =================
        else if ("Rider".equals(userRole)) {

            if ("WFR".equals(status)) {

                h.timeRow.setVisibility(View.VISIBLE);
                h.btnReady.setVisibility(View.VISIBLE);
                h.btnReady.setText("Accept Delivery");
                h.btnReady.setEnabled(true);
            }
        }

        // ================= CLICK LISTENERS =================

        h.btnDelete.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onDeleteClick(m, pos);
            }
        });

        h.btnAccept.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onAcceptClick(m, pos);
            }
        });

        h.btnReady.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onReadyClick(m, pos);
            }
        });

        h.itemView.setOnClickListener(v -> listener.onItemClick(m));
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    // ================= VIEW HOLDER =================

    class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtOrderId, txtTotalPrice, txtTimer;
        ImageView btnDelete, btnAccept;
        Button btnReady;
        LinearLayout timeRow;

        ViewHolder(@NonNull View itemView) {
            super(itemView);

            txtOrderId = itemView.findViewById(R.id.txtOrderId);
            txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);

            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnAccept = itemView.findViewById(R.id.btnAccept);

            btnReady = itemView.findViewById(R.id.btnReady);
            timeRow = itemView.findViewById(R.id.timeRow);

            txtTimer = itemView.findViewById(R.id.txtTimer);
        }
    }
}





//package com.example.paktrainfoodapp.ui.main.Passenger;
//
//import android.os.Bundle;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.ImageView;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AlertDialog;
//import androidx.fragment.app.Fragment;
//import androidx.fragment.app.FragmentManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.example.paktrainfoodapp.R;
//import com.google.firebase.firestore.FirebaseFirestore;
//
//import java.util.ArrayList;
//
//public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.OrderViewHolder> {
//
//    private final ArrayList<OrderModel> items;
//    private final FirebaseFirestore firestore;
//    private final String uid;
//    private final Fragment fragment;
//
//    public OrdersAdapter(ArrayList<OrderModel> items,
//                         FirebaseFirestore firestore,
//                         String uid,
//                         Fragment fragment) {
//
//        this.items = items;
//        this.firestore = firestore;
//        this.uid = uid;
//        this.fragment = fragment;
//    }
//
//    @NonNull
//    @Override
//    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//
//        View view = LayoutInflater.from(parent.getContext())
//                .inflate(R.layout.passanger_order_item_simple, parent, false);
//
//        return new OrderViewHolder(view);
//    }
//
//    @Override
//    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
//
//        OrderModel order = items.get(position);
//
//        holder.txtOrderId.setText("#" + order.getOrderId());
//        holder.txtTotalPrice.setText("Total: Rs " + order.getTotalPrice());
//
//        // ================= CLICK TO OPEN DETAIL =================
//        holder.itemView.setOnClickListener(v -> {
//
//            int pos = holder.getAdapterPosition();
//            if (pos == RecyclerView.NO_POSITION) return;
//
//            OrderModel selectedOrder = items.get(pos);
//
//            passanger_orderDetailFragment detailFragment =
//                    new passanger_orderDetailFragment();
//
//            Bundle bundle = new Bundle();
//            bundle.putString("orderId", selectedOrder.getOrderId());
//            detailFragment.setArguments(bundle);
//
//            fragment.requireActivity()
//                    .getSupportFragmentManager()
//                    .beginTransaction()
//                    .setReorderingAllowed(true)
//                    .replace(R.id.fragment_holder, detailFragment)
//                    .addToBackStack("order_detail")
//                    .commit();
//        });
//
//        // ================= DELETE ORDER =================
//        holder.btnDelete.setOnClickListener(v -> {
//
//            new AlertDialog.Builder(fragment.requireContext())
//                    .setTitle("Delete Order")
//                    .setMessage("Are you sure you want to delete this order?")
//                    .setPositiveButton("Yes", (dialog, which) -> {
//
//                        int pos = holder.getAdapterPosition();
//                        if (pos == RecyclerView.NO_POSITION) return;
//
//                        OrderModel selectedOrder = items.get(pos);
//                        String orderId = selectedOrder.getOrderId();
//
//                        firestore.collection("Users")
//                                .document("Passenger")
//                                .collection("OrderNow")
//                                .document(uid)
//                                .collection("Orders")
//                                .document(orderId)
//                                .delete();
//
//                        firestore.collection("Orders")
//                                .document(orderId)
//                                .delete();
//
//                        items.remove(pos);
//                        notifyItemRemoved(pos);
//
//                        Toast.makeText(fragment.getContext(),
//                                "Order Deleted",
//                                Toast.LENGTH_SHORT).show();
//                    })
//                    .setNegativeButton("No", null)
//                    .show();
//        });
//    }
//
//    @Override
//    public int getItemCount() {
//        return items.size();
//    }
//
//    // ================= VIEW HOLDER =================
//    static class OrderViewHolder extends RecyclerView.ViewHolder {
//
//        TextView txtOrderId, txtTotalPrice;
//        ImageView btnDelete;
//
//        public OrderViewHolder(@NonNull View itemView) {
//            super(itemView);
//
//            txtOrderId = itemView.findViewById(R.id.txtOrderId);
//            txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);
//            btnDelete = itemView.findViewById(R.id.btnDelete);
//        }
//    }
//}
//
//
//
//
