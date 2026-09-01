package com.example.paktrainfoodapp.ui.main.Restaurant.order;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Restaurant.menu.MenuItem;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AcceptedOrdersFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout layoutNoOrders;

    private ArrayList<MenuItem> orderList;
    private OrdersAdapter adapter;

    private FirebaseFirestore firestore;
    private String restaurantUid;

    private ListenerRegistration orderListener;

    private FusedLocationProviderClient fusedLocationClient;
    private double restaurantLat = 0.0;
    private double restaurantLng = 0.0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_restaurant_orders_accept_pending_complete,
                container,
                false
        );

        recyclerView = view.findViewById(R.id.recyclerOrders);
        layoutNoOrders = view.findViewById(R.id.layoutNoOrders);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        orderList = new ArrayList<>();
        adapter = new OrdersAdapter(orderList);
        recyclerView.setAdapter(adapter);

        firestore = FirebaseFirestore.getInstance();

        restaurantUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!restaurantUid.isEmpty()) {
            loadAcceptedOrders();
        }
    }

    // ================= LOAD ORDERS =================
    private void loadAcceptedOrders() {

        if (orderListener != null) orderListener.remove();

        orderListener = firestore.collection("Orders")
                .whereEqualTo("restaurantId", restaurantUid)
                .addSnapshotListener((query, e) -> {

                    if (e != null || query == null || !isAdded()) return;

                    orderList.clear();

                    for (QueryDocumentSnapshot doc : query) {

                        String status = doc.getString("orderStatus");
                        if (status == null) continue;

                        // ✅ FIX: "accepted_by_rider" and everything after
                        // it belongs to the DELIVERY tab
                        // (DeliveredOrdersFragment already lists exactly
                        // those four statuses). A previous fix added them
                        // here too, so from the moment a rider arrived the
                        // same order appeared in BOTH tabs at once. This
                        // tab now stops at the handover point: it covers
                        // the restaurant's own work (accept -> cook ->
                        // mark ready), and hands off to the Delivery tab
                        // once a rider takes it.
                        if (!status.equals("Accepted") &&
                                !status.equals("ready_for_delivery")) continue;

                        MenuItem item = new MenuItem();
                        item.setId(doc.getId());
                        item.setOrderNumber(doc.getLong("orderNumber"));
                        item.setPassengerUid(doc.getString("passengerUid"));
                        item.setDocPath(doc.getReference().getPath());
                        item.setStatus(status);

                        Long eta = doc.getLong("etaEndTime");
                        item.setEtaEndTime(eta != null ? eta : 0L);

                        // Module: show the same "Estimated Arrival" the
                        // Active tab shows, instead of the prep-deadline
                        // countdown that used to be here.
                        Long trainEta = doc.getLong("trainEtaEndTime");
                        item.setTrainEtaEndTime(trainEta != null ? trainEta : 0L);

                        Double price = doc.getDouble("totalPrice");
                        if (price != null) {
                            Map<String, Double> map = new HashMap<>();
                            map.put("Total", price);
                            item.setVariations(map);
                        }

                        orderList.add(item);
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
        if (orderListener != null) orderListener.remove();
    }

    // ================= LOCATION =================
    private void getRestaurantLocation(Runnable callback) {

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                restaurantLat = location.getLatitude();
                restaurantLng = location.getLongitude();
            }
            callback.run();
        });
    }

    // ================= ADAPTER =================
    private class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.ViewHolder> {

        private final ArrayList<MenuItem> items;
        private final Handler handler = new Handler(Looper.getMainLooper());

        OrdersAdapter(ArrayList<MenuItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.passanger_order_item_simple, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {

            MenuItem m = items.get(position);

            h.txtOrderId.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils.format(m.getOrderNumber(), m.getId()));

            double total = (m.getVariations() != null && !m.getVariations().isEmpty())
                    ? m.getVariations().values().iterator().next()
                    : 0;

            h.txtTotalPrice.setText("Rs " + total);

            // RESET UI
            h.btnAccept.setVisibility(View.GONE);
            h.btnDelete.setVisibility(View.GONE);

            h.timeRow.setVisibility(View.VISIBLE);
            h.btnReady.setVisibility(View.VISIBLE);
            h.btnReady.setEnabled(true);

            String status = m.getStatus();

// RESET default
            h.btnReady.setEnabled(true);
            h.btnReady.setAlpha(1f);
            h.btnReady.setVisibility(View.VISIBLE);

// STATUS UI CONTROL
            switch (status) {

                case "Accepted":
                    h.btnReady.setText("Ready For Delivery");
                    h.btnReady.setEnabled(true);
                    h.btnReady.setAlpha(1f);
                    break;

                case "ready_for_delivery":
                    // Not an action - it's a state, so it reads as a badge
                    // rather than a button nobody can press (see StatusBadge).
                    com.example.paktrainfoodapp.utils.StatusBadge.apply(h.btnReady, status);
                    h.btnReady.setEnabled(false);
                    h.btnReady.setAlpha(1f);

                    // Module: restaurant can flag "no rider is coming"
                    // once an order has been sitting unclaimed - the
                    // report goes to admin review, same as a rider's.
                    h.btnDelete.setVisibility(View.VISIBLE);
                    h.btnDelete.setOnClickListener(v -> showRestaurantReportDialog(m, "no_rider"));
                    break;
            }

            // Module: replaced the prep-deadline countdown with the same
            // "Estimated Arrival" the Active tab shows - a static clock
            // time (auto-updates via the Firestore listener whenever
            // trainEtaEndTime changes), not a per-second countdown.
            // ✅ FIX: this bound the ETA to txtTimer, but the shared item
            // layout ALSO contains txtEtaArrival with a hardcoded
            // "Estimated Arrival: --" placeholder - so the card showed the
            // estimate twice, once real and once as a dead "--" label.
            // Every other screen uses txtEtaArrival, so this one now does
            // too, and the unused txtTimer is hidden.
            bindEtaArrival(h.txtEtaArrival, m.getTrainEtaEndTime());
            h.txtTimer.setVisibility(View.GONE);

            // ================= READY CLICK =================
            h.btnReady.setOnClickListener(v -> {

                if (!"Accepted".equals(m.getStatus())) return;

                h.btnReady.setEnabled(false);
                h.btnReady.setAlpha(0.5f);

                new AlertDialog.Builder(requireContext())
                        .setTitle("Ready for Delivery")
                        .setMessage("Kya khana tayar hai?")
                        .setPositiveButton("Yes", (d, w) -> {

                            getRestaurantLocation(() -> {

                                Map<String, Object> map = new HashMap<>();
                                map.put("orderStatus", "ready_for_delivery");
                                map.put("restaurantLat", restaurantLat);
                                map.put("restaurantLng", restaurantLng);
                                map.put("readyTime", System.currentTimeMillis());

                                firestore.collection("Orders")
                                        .document(m.getId())
                                        .update(map)
                                        .addOnSuccessListener(unused -> {

                                            m.setStatus("ready_for_delivery");

                                            Toast.makeText(getContext(),
                                                    "Order Ready For Delivery",
                                                    Toast.LENGTH_SHORT).show();

                                            adapter.notifyItemChanged(h.getAdapterPosition());
                                        })
                                        .addOnFailureListener(e -> {

                                            // rollback UI
                                            h.btnReady.setEnabled(true);
                                            h.btnReady.setAlpha(1f);

                                            Toast.makeText(getContext(),
                                                    e.getMessage(),
                                                    Toast.LENGTH_LONG).show();
                                        });
                            });

                        })
                        .setNegativeButton("No", (d, w) -> {
                            h.btnReady.setEnabled(true);
                            h.btnReady.setAlpha(1f);
                        })
                        .show();
            });

            // ================= DETAIL CLICK =================
            h.itemView.setOnClickListener(v -> {

                Fragment f = OrderDetailFragment.newInstance(m.getId());

                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_container, f)
                        .addToBackStack("order_detail")
                        .commit();
            });
        }

        // ================= ESTIMATED ARRIVAL =================
        //
        // Module: same field/format as ActiveOrdersFragment's
        // bindEtaArrival() - shows a clock time, e.g.
        // "Estimated Arrival: 10:00 PM", auto-updating via the Firestore
        // snapshot listener whenever trainEtaEndTime changes. Replaces the
        // old per-second prep-deadline countdown.
        /**
         * Module: restaurant-side "report a problem". Two entry points:
         *   - "no_rider"   : order has been ready but nobody picked it up
         *   - "rider_late" : a rider accepted but is taking too long
         *
         * Both write the same delivery_failed status the rider's own
         * report does, so onDeliveryFailed.js freezes the order for admin
         * review with the full timeline attached - the admin then decides
         * the three-way split. failureReportedBy records which side
         * raised it, so the admin can see whose account it came from.
         */
        private void showRestaurantReportDialog(MenuItem m, String reportType) {

            String title = "no_rider".equals(reportType)
                    ? "No Rider Found?"
                    : "Rider Taking Too Long?";

            String hint = "no_rider".equals(reportType)
                    ? "e.g. Order ready 20 minutes ago, no rider has come"
                    : "e.g. Rider accepted 15 minutes ago but hasn't arrived";

            android.widget.EditText input = new android.widget.EditText(requireContext());
            input.setHint(hint);
            input.setMinLines(2);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            input.setPadding(pad, pad, pad, pad);

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage("Wajah likhein - admin isay dekh kar faisla karega. Order cancel ho jayega aur review mein chala jayega.")
                    .setView(input)
                    .setPositiveButton("Submit Report", null)
                    .setNegativeButton("Cancel", null)
                    .create();

            dialog.show();

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

                String reason = input.getText() != null ? input.getText().toString().trim() : "";

                if (reason.isEmpty()) {
                    input.setError("Please describe what happened");
                    return;
                }

                dialog.dismiss();

                java.util.Map<String, Object> updates = new java.util.HashMap<>();
                updates.put("orderStatus", "delivery_failed");
                updates.put("failureReason", reason);
                updates.put("failureReportedAt", System.currentTimeMillis());
                updates.put("failureReportedBy", "restaurant");
                updates.put("failureReportType", reportType);

                firestore.document(m.getDocPath())
                        .update(updates)
                        .addOnSuccessListener(unused -> {
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        "Reported - admin will review this order.",
                                        Toast.LENGTH_LONG).show();
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        "Report failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            });
        }

        private void bindEtaArrival(TextView txtTimer, long trainEtaEndTimeMillis) {

            if (trainEtaEndTimeMillis <= 0) {
                txtTimer.setText("Estimated Arrival: Calculating...");
                return;
            }

            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());

            txtTimer.setText("Estimated Arrival: " + fmt.format(new java.util.Date(trainEtaEndTimeMillis)));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            TextView txtOrderId, txtTotalPrice, txtTimer, txtEtaArrival;
            ImageView btnDelete, btnAccept;
            Button btnReady;
            LinearLayout timeRow;

            ViewHolder(@NonNull View itemView) {
                super(itemView);

                txtOrderId = itemView.findViewById(R.id.txtOrderId);
                txtTotalPrice = itemView.findViewById(R.id.txtTotalPrice);
                txtTimer = itemView.findViewById(R.id.txtTimer);
                txtEtaArrival = itemView.findViewById(R.id.txtEtaArrival);

                btnDelete = itemView.findViewById(R.id.btnDelete);
                btnAccept = itemView.findViewById(R.id.btnAccept);

                btnReady = itemView.findViewById(R.id.btnReady);
                timeRow = itemView.findViewById(R.id.timeRow);
            }
        }
    }
}












