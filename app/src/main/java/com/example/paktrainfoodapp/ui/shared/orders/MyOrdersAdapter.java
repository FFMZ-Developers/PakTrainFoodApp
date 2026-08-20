package com.example.paktrainfoodapp.ui.shared.orders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;

import java.util.List;

/**
 * Shows every order a passenger has ever placed, with a coloured status badge.
 * Raw backend status values (e.g. "accepted_by_rider", "WFR") are mapped to
 * friendly labels here so the passenger sees plain language.
 */
public class MyOrdersAdapter
        extends RecyclerView.Adapter<MyOrdersAdapter.ViewHolder> {

    public interface OnOrderClickListener {
        void onOrderClick(MyOrderModel order);
    }

    private final List<MyOrderModel> list;
    private final OnOrderClickListener listener;

    public MyOrdersAdapter(List<MyOrderModel> list, OnOrderClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_order, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        MyOrderModel order = list.get(position);

        holder.txtOrderId.setText("#" + order.getOrderId());

        holder.txtTotalPrice.setText("Rs " + (int) order.getTotalPrice());

        if (order.getRestaurantName() != null && !order.getRestaurantName().isEmpty()) {
            holder.txtRestaurant.setVisibility(View.VISIBLE);
            holder.txtRestaurant.setText(order.getRestaurantName());
        } else {
            holder.txtRestaurant.setVisibility(View.GONE);
        }

        if (order.getMealStation() != null && !order.getMealStation().isEmpty()) {
            holder.txtStation.setVisibility(View.VISIBLE);
            holder.txtStation.setText("Station: " + order.getMealStation());
        } else {
            holder.txtStation.setVisibility(View.GONE);
        }

        if (order.getDateText() != null && !order.getDateText().isEmpty()) {
            holder.txtOrderDate.setVisibility(View.VISIBLE);
            holder.txtOrderDate.setText(order.getDateText());
        } else {
            holder.txtOrderDate.setVisibility(View.GONE);
        }

        holder.txtStatusBadge.setText(friendlyLabel(order.getStatus()));

        holder.txtStatusBadge.setBackgroundResource(badgeBackground(order.getStatus()));

        holder.txtStatusBadge.setTextColor(badgeTextColor(order.getStatus()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOrderClick(order);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    // =====================================================
    // STATUS MAPPING
    // =====================================================

    /**
     * Groups every raw backend status into one of:
     * pending / ongoing / completed / cancelled
     */
    public static String bucketOf(String rawStatus) {

        if (rawStatus == null) return "pending";

        String s = rawStatus.toLowerCase();

        switch (s) {
            case "active":
                return "pending";

            case "cancelled":
            case "canceled":
            case "rejected":
                return "cancelled";

            case "completed":
                return "completed";

            case "accepted":
            case "wfr":
            case "ready_for_delivery":
            case "accepted_by_rider":
            case "arrive_rider_at_resturent":
            case "pick_up":
            case "dropped":
                return "ongoing";

            default:
                return "ongoing";
        }
    }

    private static String friendlyLabel(String rawStatus) {

        if (rawStatus == null) return "Pending";

        switch (rawStatus.toLowerCase()) {
            case "active":                     return "Pending";
            case "accepted":                   return "Preparing";
            case "wfr":
            case "ready_for_delivery":         return "Ready";
            case "accepted_by_rider":          return "Rider Assigned";
            case "arrive_rider_at_resturent":  return "Rider at Restaurant";
            case "pick_up":                    return "On the Way";
            case "dropped":                    return "Delivered";
            case "completed":                  return "Completed";
            case "cancelled":
            case "canceled":                   return "Cancelled";
            case "rejected":                   return "Rejected";
            default:                           return rawStatus;
        }
    }

    private static int badgeBackground(String rawStatus) {

        switch (bucketOf(rawStatus)) {
            case "completed": return R.drawable.bg_badge_green;
            case "cancelled": return R.drawable.bg_badge_red;
            case "pending":   return R.drawable.bg_badge_orange;
            case "ongoing":   return R.drawable.bg_badge_blue;
            default:          return R.drawable.bg_badge_grey;
        }
    }

    private static int badgeTextColor(String rawStatus) {

        switch (bucketOf(rawStatus)) {
            case "completed": return 0xFF2E7D32; // green
            case "cancelled": return 0xFFC62828; // red
            case "pending":   return 0xFFEF6C00; // orange
            case "ongoing":   return 0xFF1565C0; // blue
            default:          return 0xFF616161; // grey
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView txtOrderId, txtStatusBadge, txtRestaurant,
                txtStation, txtOrderDate, txtTotalPrice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            txtOrderId = itemView.findViewById(R.id.txtOrderId);
            txtStatusBadge = itemView.findViewById(R.id.txtStatusBadge);
            txtRestaurant = itemView.findViewById(R.id.txtRestaurant);
            txtStation = itemView.findViewById(R.id.txtStation);
            txtOrderDate = itemView.findViewById(R.id.txtOrderDate);
            txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);
        }
    }
}
