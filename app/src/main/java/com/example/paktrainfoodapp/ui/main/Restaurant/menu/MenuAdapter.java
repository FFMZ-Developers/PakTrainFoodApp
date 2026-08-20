package com.example.paktrainfoodapp.ui.main.Restaurant.menu;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;
import java.util.List;
import java.util.Map;

public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.VH> {

    private final List<MenuItem> items;
    private final Context ctx;
    private final OnItemActionListener listener;

    public interface OnItemActionListener {
        void onEdit(MenuItem item);
        void onDelete(MenuItem item);
        void onAvailabilityChanged(MenuItem item, boolean available);
    }

    public MenuAdapter(Context ctx, List<MenuItem> items, OnItemActionListener listener) {
        this.ctx = ctx;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.restaurent_item_menu, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MenuItem it = items.get(position);

        holder.tvName.setText(it.getName());
        holder.tvDesc.setText(it.getDescription());
        holder.tvTime.setText(it.getTime() != null ? it.getTime() : "");

        // ✅ FIX: Price display logic from Variations Map
        if (it.getVariations() != null && !it.getVariations().isEmpty()) {
            // Map ki pehli value ko price ke taur par dikha rahe hain
            Map.Entry<String, Double> entry = it.getVariations().entrySet().iterator().next();
            holder.tvPrice.setText("Rs. " + entry.getValue().intValue());
        } else {
            holder.tvPrice.setText("Rs. 0");
        }

        // URL Loading Logic
        Glide.with(ctx)
                .load(it.getImageUrl())
                .placeholder(R.drawable.ic_food_placeholder)
                .error(R.drawable.ic_food_placeholder)
                .centerCrop()
                .into(holder.iv);

        // Click Listeners
        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(it);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(it);
        });

        // Availability toggle. The listener is detached while we set the
        // initial state, otherwise recycling a row would fire a spurious
        // "changed" callback and write to Firestore on every scroll.
        holder.switchAvailable.setOnCheckedChangeListener(null);
        holder.switchAvailable.setChecked(it.isAvailable());
        holder.txtAvailableLabel.setText(it.isAvailable() ? "Available" : "Unavailable");

        holder.switchAvailable.setOnCheckedChangeListener((buttonView, isChecked) -> {

            it.setAvailable(isChecked);

            holder.txtAvailableLabel.setText(isChecked ? "Available" : "Unavailable");

            if (listener != null) listener.onAvailabilityChanged(it, isChecked);
        });

        // Dim the whole row when the item is hidden from passengers
        holder.itemView.setAlpha(it.isAvailable() ? 1f : 0.55f);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        TextView tvName, tvDesc, tvPrice, tvTime;
        ImageButton btnEdit, btnDelete;
        androidx.appcompat.widget.SwitchCompat switchAvailable;
        TextView txtAvailableLabel;

        VH(@NonNull View v) {
            super(v);
            iv = v.findViewById(R.id.item_image);
            tvName = v.findViewById(R.id.item_name);
            tvDesc = v.findViewById(R.id.item_desc);
            tvPrice = v.findViewById(R.id.item_price);
            tvTime = v.findViewById(R.id.item_time);
            btnEdit = v.findViewById(R.id.btn_edit);
            btnDelete = v.findViewById(R.id.btn_delete);
            switchAvailable = v.findViewById(R.id.switch_available);
            txtAvailableLabel = v.findViewById(R.id.txt_available_label);
        }
    }
}

//


