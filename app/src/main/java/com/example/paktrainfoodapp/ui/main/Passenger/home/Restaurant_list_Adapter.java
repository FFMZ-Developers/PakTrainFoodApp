package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;

import java.util.ArrayList;
import java.util.List;

public class Restaurant_list_Adapter extends RecyclerView.Adapter<Restaurant_list_Adapter.ViewHolder> {

    private List<Restaurant_list_Model> list;
    private OnItemClickListener listener;

    // Interface for click events (like favorite and navigation triggers)
    public interface OnItemClickListener {
        void onFavoriteClick(int position);
        void onItemClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public Restaurant_list_Adapter(ArrayList<Restaurant_list_Model> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.passanger_resturent_layout, parent, false);
        return new ViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Restaurant_list_Model model = list.get(position);

        holder.tvName.setText(model.getRestaurantName());
        holder.tvInfo.setText(model.getCity());

        // ⚡ FIXED: Dropped local bitmap rendering loops in favor of asynchronous cloud stream engine (Glide)
        String imageUrl = model.getImageUrl(); // Resolves through our safe dynamic fallback getter layer

        Glide.with(holder.itemView.getContext())
                .load(imageUrl)
                .placeholder(R.drawable.station3) // Standard temporary vector template asset
                .error(R.drawable.station3)       // Fallback system asset mapping on processing drops
                .centerCrop()                     // Image stretch parameters balanced smoothly inside layout item
                .into(holder.imgRestaurant);

        // Heart icon reflects saved favorite state
        if (holder.btnFavorite != null) {

            holder.btnFavorite.setImageResource(
                    model.isFavorite()
                            ? R.drawable.ic_heart_filled
                            : R.drawable.ic_heart_outline
            );
        }
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    // ViewHolder class with localized memory references
    public static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imgRestaurant, btnFavorite;
        TextView tvName, tvInfo, tvRating, tvAd;

        public ViewHolder(@NonNull View itemView, OnItemClickListener listener) {
            super(itemView);

            imgRestaurant = itemView.findViewById(R.id.img_restaurant);
            btnFavorite = itemView.findViewById(R.id.btn_favorite);
            tvName = itemView.findViewById(R.id.tv_restaurant_name);
            tvInfo = itemView.findViewById(R.id.tv_restaurant_info);
            tvRating = itemView.findViewById(R.id.tv_rating);
            tvAd = itemView.findViewById(R.id.tv_ad);

            // Favorite click event handler
            if (btnFavorite != null) {
                btnFavorite.setOnClickListener(v -> {
                    if (listener != null) {
                        int position = getAdapterPosition();
                        // Guard clause ensures click indices never fire during animation frames shifts
                        if (position != RecyclerView.NO_POSITION) {
                            listener.onFavoriteClick(position);
                        }
                    }
                });
            }

            // Whole restaurant item layout routing event handler
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onItemClick(position);
                    }
                }
            });
        }
    }
}




//