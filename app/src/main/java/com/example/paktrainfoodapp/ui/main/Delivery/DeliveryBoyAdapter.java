package com.example.paktrainfoodapp.ui.main.Delivery;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;

import java.util.ArrayList;

public class DeliveryBoyAdapter extends RecyclerView.Adapter<DeliveryBoyAdapter.VH> {

    public interface OnActionClick {
        void onItemClick(DeliveryBoyModel order, int position);
        void onAccept(DeliveryBoyModel order, int position);
        void onButtonClick(DeliveryBoyModel order, int position);

        // Module 6 (Failure 3) - rider reports they can't complete an
        // in-progress delivery.
        void onReportProblem(DeliveryBoyModel order, int position);
    }

    private final Context context;
    private final ArrayList<DeliveryBoyModel> list;
    private final OnActionClick listener;

    public DeliveryBoyAdapter(Context context,
                              ArrayList<DeliveryBoyModel> list,
                              OnActionClick listener) {
        this.context = context;
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.passanger_order_item_simple, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {

        DeliveryBoyModel order = list.get(position);

        h.txtOrderId.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils.format(order.getOrderNumber(), order.getOrderId()));
        h.txtTotalPrice.setText("Rs " + order.getTotalPrice());

        // ✅ FIX: "Estimated Arrival" was never populated on the rider's
        // cards - the label sat there permanently empty. Now it shows the
        // train's arrival clock time, the same value the restaurant and
        // passenger see, so the rider knows how long they actually have.
        if (h.txtEtaArrival != null) {

            if (order.getTrainEtaEndTime() > 0) {

                java.text.SimpleDateFormat fmt =
                        new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());

                h.txtEtaArrival.setVisibility(View.VISIBLE);
                h.txtEtaArrival.setText("Estimated Arrival: "
                        + fmt.format(new java.util.Date(order.getTrainEtaEndTime())));

            } else {
                h.txtEtaArrival.setVisibility(View.GONE);
            }
        }

        String status = order.getStatus();

        // ✅ FIX: the item click (which opens the tracking/detail screen)
        // used to be wired ONLY inside the "ready_for_delivery" branch
        // below - so the moment a rider accepted an order, tapping its
        // card did absolutely nothing, in every state after that
        // (accepted / arrived / dropped). Wiring it once here, before any
        // status branching, means the card is tappable in every state.
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(order, h.getAdapterPosition());
            }
        });

        // RESET
        h.btnAccept.setVisibility(View.GONE);
        h.btnReady.setVisibility(View.GONE);
        h.btnReportProblem.setVisibility(View.GONE);

        // ✅ FIX: timeRow was forced VISIBLE here while txtTimer was set
        // GONE in every single status branch below - leaving a permanently
        // empty box on every card. timeRow now only shows when it actually
        // has a button to hold (the action states below switch it on).
        h.timeRow.setVisibility(View.GONE);
        h.txtTimer.setVisibility(View.GONE);

        // ================= READY =================
        if ("ready_for_delivery".equals(status)) {

            h.btnAccept.setVisibility(View.VISIBLE);
            h.txtTimer.setVisibility(View.GONE);

            // 🟢 ICON CLICK
            h.btnAccept.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAccept(order, h.getAdapterPosition());
                }
            });
        }

        // ================= ACCEPTED =================
        else if ("accepted_by_rider".equals(status)) {

            h.timeRow.setVisibility(View.VISIBLE);
            h.btnReady.setVisibility(View.VISIBLE);
            h.btnReady.setText("ARRIVED");
            h.btnReady.setEnabled(true);
            h.btnReady.setAlpha(1f);
            h.txtTimer.setVisibility(View.GONE);
            h.btnReportProblem.setVisibility(View.VISIBLE);

        }

        // ================= ARRIVED =================
        else if ("arrive_rider_at_resturent".equals(status)) {

            h.timeRow.setVisibility(View.VISIBLE);
            h.btnReady.setVisibility(View.VISIBLE);
            h.btnReady.setText("READY FOR PICKUP");
            h.btnReady.setEnabled(false);
            h.btnReady.setAlpha(0.4f);
            h.txtTimer.setVisibility(View.GONE);
            h.btnReportProblem.setVisibility(View.VISIBLE);
        }

        // ================= DROPPED =================
        else if ("dropped".equals(status)) {

            h.timeRow.setVisibility(View.VISIBLE);
            h.btnReady.setVisibility(View.VISIBLE);
            h.btnReady.setText("PICK UP");
            h.btnReady.setEnabled(true);
            h.btnReady.setAlpha(1f);
            h.txtTimer.setVisibility(View.GONE);
            h.btnReportProblem.setVisibility(View.VISIBLE);
        }

        // pick_up now lives in the Accept tab too (it used to sit in
        // "Completed", which made an in-progress delivery look finished),
        // so the final hand-over action has to be available here.
        else if ("pick_up".equals(status)) {

            h.timeRow.setVisibility(View.VISIBLE);
            h.btnReady.setVisibility(View.VISIBLE);
            h.btnReady.setText("HAND OVER TO PASSENGER");
            h.btnReady.setEnabled(true);
            h.btnReady.setAlpha(1f);
            h.txtTimer.setVisibility(View.GONE);
            h.btnReportProblem.setVisibility(View.VISIBLE);
        }

        h.btnReportProblem.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReportProblem(order, h.getAdapterPosition());
            }
        });

        // 🔵 BUTTON CLICK (ONLY ONE LISTENER)
        h.btnReady.setOnClickListener(v -> {
            if (listener != null) {
                listener.onButtonClick(order, h.getAdapterPosition());
            }
        });
    }
    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        ImageView btnAccept, btnReportProblem;
        TextView txtOrderId, txtTotalPrice, txtTimer, txtEtaArrival;
        Button btnReady;
        View timeRow;

        VH(@NonNull View itemView) {
            super(itemView);

            txtOrderId = itemView.findViewById(R.id.txtOrderId);
            txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);

            btnReady = itemView.findViewById(R.id.btnReady);
            btnAccept = itemView.findViewById(R.id.btnAccept);

            // Module 6 (Failure 3) - reuses the delete-icon slot (same as
            // the restaurant's Reject button reuses it on a different
            // screen/adapter - no conflict, separate layouts inflate it
            // independently).
            btnReportProblem = itemView.findViewById(R.id.btnDelete);

            timeRow = itemView.findViewById(R.id.timeRow);
            txtTimer = itemView.findViewById(R.id.txtTimer);
            txtEtaArrival = itemView.findViewById(R.id.txtEtaArrival);
        }
    }
}










