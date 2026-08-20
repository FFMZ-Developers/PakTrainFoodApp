package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.CartManager;
import com.example.paktrainfoodapp.R;

import java.util.ArrayList;

public class OrderSummaryAdapter
        extends RecyclerView.Adapter<OrderSummaryAdapter.ViewHolder> {

    private final ArrayList<CartItem> list;

    public OrderSummaryAdapter(ArrayList<CartItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(
                        R.layout.item_order_summary,
                        parent,
                        false
                );

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position) {

        CartItem item = list.get(position);

        holder.tvItemName.setText(item.getName());

        if (item.getRestaurantName() != null && !item.getRestaurantName().isEmpty()) {
            holder.tvItemRestaurant.setVisibility(View.VISIBLE);
            holder.tvItemRestaurant.setText(item.getRestaurantName());
        } else {
            holder.tvItemRestaurant.setVisibility(View.GONE);
        }

        holder.tvItemQty.setText(String.valueOf(item.getQuantity()));

        holder.tvItemPrice.setText(
                "Rs. " +
                        (int) item.getTotal()
        );

        Glide.with(holder.itemView.getContext())
                .load(item.getImageUrl())
                .placeholder(R.drawable.ic_food_placeholder)
                .error(R.drawable.ic_food_placeholder)
                .centerCrop()
                .into(holder.imgCartItem);

        holder.btnIncreaseQty.setOnClickListener(v ->
                CartManager.increaseQuantity(item.getKey()));

        holder.btnDecreaseQty.setOnClickListener(v ->
                CartManager.decreaseQuantity(item.getKey()));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder
            extends RecyclerView.ViewHolder {

        ImageView imgCartItem;
        TextView tvItemName;
        TextView tvItemRestaurant;
        TextView tvItemPrice;
        TextView tvItemQty;
        ImageView btnIncreaseQty;
        ImageView btnDecreaseQty;

        public ViewHolder(
                @NonNull View itemView) {

            super(itemView);

            imgCartItem = itemView.findViewById(R.id.imgCartItem);
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvItemRestaurant = itemView.findViewById(R.id.tvItemRestaurant);
            tvItemPrice = itemView.findViewById(R.id.tvItemPrice);
            tvItemQty = itemView.findViewById(R.id.tvItemQty);
            btnIncreaseQty = itemView.findViewById(R.id.btnIncreaseQty);
            btnDecreaseQty = itemView.findViewById(R.id.btnDecreaseQty);
        }
    }
}
