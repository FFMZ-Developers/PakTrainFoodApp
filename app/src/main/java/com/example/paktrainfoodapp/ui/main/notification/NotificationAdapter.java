package com.example.paktrainfoodapp.ui.main.notification;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader;

import java.util.List;

public class NotificationAdapter
        extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final Context context;
    private final List<NotificationModel> list;
    private NotificationClickListener listener;
    private final NotificationRepository repository =
            new NotificationRepository();
    public interface NotificationClickListener {

        void onOrderClick(NotificationModel model);

        // Future

//    void onWalletClick(NotificationModel model);
//
//    void onProfileClick(NotificationModel model);
//
//    void onRestaurantClick(NotificationModel model);
//
//    void onOfferClick(NotificationModel model);

    }
    public NotificationAdapter(
            Context context,
            List<NotificationModel> list,
            NotificationClickListener listener) {

        this.context = context;
        this.list = list;
        this.listener = listener;

    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(context)
                .inflate(
                        R.layout.item_notification,
                        parent,
                        false
                );

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position) {

        NotificationModel model = list.get(position);

        holder.txtTitle.setText(model.getTitle());
        holder.txtBody.setText(model.getBody());
        if (model.getCreatedAt() != null) {

            Date date = model.getCreatedAt().toDate();

            SimpleDateFormat sdf =
                    new SimpleDateFormat("dd MMM yyyy  hh:mm a", Locale.getDefault());

            holder.txtTime.setText(sdf.format(date));

        } else {

            holder.txtTime.setText("");

        }

        // Read / Unread Dot
        if (model.isRead()) {

            holder.viewUnread.setVisibility(View.GONE);
            holder.itemView.setAlpha(0.7f);

        } else {

            holder.viewUnread.setVisibility(View.VISIBLE);

            holder.itemView.setAlpha(1f);

        }

        holder.itemView.setOnClickListener(v -> {

            // Mark notification as read
            if (!model.isRead()) {

                repository.markAsRead(
                        NotificationRepository.ROLE_PASSENGER,
                        model.getDocumentId());

                // UI update immediately
                model.setRead(true);

                holder.viewUnread.setVisibility(View.GONE);

                notifyItemChanged(holder.getAdapterPosition());


            }

            // Open screen according to notification type
            if (listener != null) {

                listener.onOrderClick(model);

            }


        });

        String type = model.getType();

        if (type == null) {

            holder.imgIcon.setImageResource(R.drawable.ic_notification);

        }
        else if (type.equalsIgnoreCase("order")) {

            holder.imgIcon.setImageResource(R.drawable.ic_order);

        }
//        else if (type.equalsIgnoreCase("wallet")) {
//
//            holder.imgIcon.setImageResource(R.drawable.ic_wallet);
//
//        }
//        else if (type.equalsIgnoreCase("offer")) {
//
//            holder.imgIcon.setImageResource(R.drawable.ic_offer);
//
//        }
//        else if (type.equalsIgnoreCase("restaurant")) {
//
//            holder.imgIcon.setImageResource(R.drawable.ic_restaurant);
//
//        }
        else {

            holder.imgIcon.setImageResource(R.drawable.ic_notification);

        }
    }

    @Override
    public int getItemCount() {

        return list.size();

    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imgIcon;

        TextView txtTitle;
        TextView txtBody;
        TextView txtTime;

        View viewUnread;

        ViewHolder(@NonNull View itemView) {

            super(itemView);

            imgIcon = itemView.findViewById(R.id.imgIcon);

            txtTitle = itemView.findViewById(R.id.txtTitle);

            txtBody = itemView.findViewById(R.id.txtBody);

            txtTime = itemView.findViewById(R.id.txtTime);

            viewUnread = itemView.findViewById(R.id.viewUnread);

        }

    }

}