package com.example.paktrainfoodapp.ui.main.Restaurant.profile;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;

import java.util.ArrayList;

public class WalletHistoryAdapter
        extends RecyclerView.Adapter<WalletHistoryAdapter.ViewHolder> {

    Context context;
    ArrayList<WalletHistory> list;

    public WalletHistoryAdapter(
            Context context,
            ArrayList<WalletHistory> list) {

        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(
                        R.layout.item_wallet_history,
                        parent,
                        false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position) {

        WalletHistory model = list.get(position);

        holder.type.setText(model.getType());
        holder.amount.setText("Rs " + model.getAmount());
        holder.date.setText(model.getDate());
        holder.orderId.setText(
                // Payout rows carry sentinel ids like "ADMIN_PAYOUT" rather
                // than a real order, so those are shown as-is.
                model.getOrderId() != null && model.getOrderId().matches("[A-Z_0-9]{6,}")
                        ? model.getOrderId().replace("_", " ")
                        : com.example.paktrainfoodapp.utils.OrderNumberUtils
                        .format(model.getOrderNumber(), model.getOrderId()));

        String type = model.getType();

        if (type == null)
            type = "";

        switch (type) {

            case "Pending":
            case "Payment Held":
            case "Refund Pending":

                holder.type.setTextColor(
                        Color.parseColor("#FF9800"));

                holder.amount.setTextColor(
                        Color.parseColor("#FF9800"));

                break;

            case "Available":
            case "Payment Sent":
            case "Refund Completed":
            case "Auto Payout":
            case "Paid by Admin":
            case "Dispute Resolution":

                holder.type.setTextColor(
                        Color.parseColor("#4CAF50"));

                holder.amount.setTextColor(
                        Color.parseColor("#4CAF50"));

                break;

            case "Withdraw":
            case "Refund Failed":

                holder.type.setTextColor(
                        Color.RED);

                holder.amount.setTextColor(
                        Color.RED);

                break;

            default:

                holder.type.setTextColor(
                        Color.parseColor("#4CAF50"));

                holder.amount.setTextColor(
                        Color.parseColor("#4CAF50"));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        TextView type;
        TextView amount;
        TextView date;
        TextView orderId;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            type = itemView.findViewById(R.id.txtType);
            amount = itemView.findViewById(R.id.txtAmount);
            date = itemView.findViewById(R.id.txtDate);
            orderId = itemView.findViewById(R.id.txtOrderId);
        }
    }
}