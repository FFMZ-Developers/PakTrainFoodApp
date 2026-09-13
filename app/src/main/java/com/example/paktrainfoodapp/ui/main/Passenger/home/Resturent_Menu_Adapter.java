package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Passenger.MenuitemModel;

import java.util.List;

public class Resturent_Menu_Adapter extends RecyclerView.Adapter<Resturent_Menu_Adapter.ViewHolder> {

    private final List<MenuitemModel> itemList;
    private OnMenuItemClickListener listener;
    private boolean showButtons = true;
    private boolean showDeleteButton = false;

    public Resturent_Menu_Adapter(List<MenuitemModel> itemList) {
        this.itemList = itemList;
    }

    public void setShowButtons(boolean show) {
        this.showButtons = show;
        notifyDataSetChanged();
    }

    public void setShowDeleteButton(boolean showDeleteButton) {
        this.showDeleteButton = showDeleteButton;
        notifyDataSetChanged();
    }

    public interface OnMenuItemClickListener {
        void onAddToCart(MenuitemModel item);
        void onBuyNow(MenuitemModel item);
        void onDeleteOrder(MenuitemModel item);
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.passanger_item_menu, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MenuitemModel item = itemList.get(position);

        holder.txtName.setText(item.getName());
        holder.txtDesc.setText(item.getDescription());
        holder.txtRestName.setText("By " + item.getRestaurantName());

        // Price display logic from Variations Map
        if (item.getVariations() != null && !item.getVariations().isEmpty()) {
            // Map ki pehli entry (size/price) ko display karein
            double price = item.getVariations().values().iterator().next();
            holder.txtPrice.setText("Rs. " + (int) price);
        } else {
            holder.txtPrice.setText("Rs. 0");
        }

        // Glide Image Loading
        String imgUrl = item.getImageUrl();
        if (imgUrl != null && !imgUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(imgUrl)
                    .placeholder(R.drawable.ic_food_placeholder)
                    .error(R.drawable.ic_food_placeholder)
                    .into(holder.imgFood);
        } else {
            holder.imgFood.setImageResource(R.drawable.ic_food_placeholder);
        }

        // Button Visibility Logic
        if (showDeleteButton) {
            holder.btnDeleteOrder.setVisibility(View.VISIBLE);
            holder.btnAddCart.setVisibility(View.GONE);
            holder.btnBuyNow.setVisibility(View.GONE);
        } else {
            holder.btnDeleteOrder.setVisibility(View.GONE);
            holder.btnAddCart.setVisibility(showButtons ? View.VISIBLE : View.GONE);
            // "Buy Now" button ko permanently GONE kar diya
            holder.btnBuyNow.setVisibility(View.GONE);
        }

        // Button Clicks
        holder.btnAddCart.setOnClickListener(v -> {
            if (listener != null) listener.onAddToCart(item);
        });

        holder.btnDeleteOrder.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteOrder(item);
        });
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgFood;
        TextView txtName, txtDesc, txtPrice, txtRestName;
        Button btnAddCart, btnBuyNow, btnDeleteOrder;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgFood = itemView.findViewById(R.id.imgFood);
            txtName = itemView.findViewById(R.id.txtName);
            txtDesc = itemView.findViewById(R.id.txtDesc);
            txtPrice = itemView.findViewById(R.id.txtPrice);
            txtRestName = itemView.findViewById(R.id.txtRestName);
            btnAddCart = itemView.findViewById(R.id.btnAddCart);
            btnBuyNow = itemView.findViewById(R.id.btnBuyNow);
            btnDeleteOrder = itemView.findViewById(R.id.btnDeleteOrder);
        }
    }
}


//

