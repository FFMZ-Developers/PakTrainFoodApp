package com.example.paktrainfoodapp.ui.main.Passenger.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import android.widget.LinearLayout;

import com.example.paktrainfoodapp.ui.main.Passenger.MenuitemModel;
import com.example.paktrainfoodapp.ui.main.Passenger.OrderItemsAdapter;
import com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader;
import com.example.paktrainfoodapp.ui.main.Passenger.home.Resturent_Menu_Fragment;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class passanger_orderDetailFragment extends Fragment {

    private android.widget.Button btnTrackLive, btnPassengerChat, btnPassengerCall;
    private View layoutPassengerContact;
    private TextView txtOrderStatus, txtOrderTrain, txtOrderStation, txtOrderSeat, txtOrderEta;
    private TextView txtOrderId,txtTotalPrice;
    private RecyclerView recyclerView;

    private TextView txtCompletedTime, txtCompletedChatEmpty;
    private View layoutCompletedChat;
    private RecyclerView recyclerCompletedChat;
    private final List<CompletedChatMessage> completedChatMessages = new ArrayList<>();
    private CompletedChatAdapter completedChatAdapter;

    private LinearLayout layoutRejectedAlternatives;
    private LinearLayout containerAlternativeRestaurants;

    private LinearLayout layoutNoRiderFound;
    private Button btnCancelNoRider;

    private FirebaseFirestore firestore;
    private String orderId;

    private OrderItemsAdapter adapter;
    private List<MenuitemModel> itemList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_passanger_order_detail, container, false);

        txtOrderId = view.findViewById(R.id.txtOrderId);
        recyclerView = view.findViewById(R.id.recyclerView);
        txtTotalPrice = view.findViewById(R.id.txtTotalPrice);

        layoutRejectedAlternatives = view.findViewById(R.id.layoutRejectedAlternatives);
        containerAlternativeRestaurants = view.findViewById(R.id.containerAlternativeRestaurants);

        layoutNoRiderFound = view.findViewById(R.id.layoutNoRiderFound);
        btnTrackLive = view.findViewById(R.id.btnTrackLive);
        layoutPassengerContact = view.findViewById(R.id.layoutPassengerContact);
        btnPassengerChat = view.findViewById(R.id.btnPassengerChat);
        btnPassengerCall = view.findViewById(R.id.btnPassengerCall);
        txtOrderStatus = view.findViewById(R.id.txtOrderStatus);
        txtOrderTrain = view.findViewById(R.id.txtOrderTrain);
        txtOrderStation = view.findViewById(R.id.txtOrderStation);
        txtOrderSeat = view.findViewById(R.id.txtOrderSeat);
        txtOrderEta = view.findViewById(R.id.txtOrderEta);
        btnCancelNoRider = view.findViewById(R.id.btnCancelNoRider);

        txtCompletedTime = view.findViewById(R.id.txtCompletedTime);
        layoutCompletedChat = view.findViewById(R.id.layoutCompletedChat);
        txtCompletedChatEmpty = view.findViewById(R.id.txtCompletedChatEmpty);
        recyclerCompletedChat = view.findViewById(R.id.recyclerCompletedChat);
        if (recyclerCompletedChat != null) {
            recyclerCompletedChat.setLayoutManager(new LinearLayoutManager(getContext()));
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        firestore = FirebaseFirestore.getInstance();

        // Bottom Navigation Hide
        LinearLayout bottomNav =
                requireActivity().findViewById(R.id.bottom_nav);

        if (bottomNav != null) {
            bottomNav.setVisibility(View.GONE);
        }

        // Get Order ID
        if (getArguments() != null) {
            orderId = getArguments().getString("orderId");
        }

        // Handle Hardware Back Button


        // Load Data
        loadOrderDetails();

        return view;
    }

    // Hardware Back Button


    // Load Order Details
    private void loadOrderDetails() {

        firestore.collection("Orders")
                .document(orderId)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) return;

                    txtOrderId.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils.format(doc.getLong("orderNumber"), orderId));
                    Double totalPrice = doc.getDouble("totalPrice");

                    if (totalPrice != null) {
                        txtTotalPrice.setText("Total Price: Rs " + totalPrice);
                    } else {
                        txtTotalPrice.setText("Total Price: Rs 0");
                    }

                    List<Map<String, Object>> cartItems =
                            (List<Map<String, Object>>) doc.get("cartItems");

                    itemList.clear();

                    if (cartItems != null) {

                        for (Map<String, Object> m : cartItems) {

                            MenuitemModel item = new MenuitemModel();

                            item.setName(String.valueOf(m.get("name")));
                            item.setDescription(String.valueOf(m.get("description")));
                            item.setRestaurantName(String.valueOf(m.get("restaurantName")));
                            item.setImageUrl(String.valueOf(m.get("imageUrl")));

                            Object priceObj = m.get("price");

                            if (priceObj != null) {
                                item.setPrice(Double.parseDouble(priceObj.toString()));
                            } else {
                                item.setPrice(0);
                            }

// Quantity
                            Object quantityObj = m.get("quantity");

                            if (quantityObj != null) {
                                item.setQuantity(Integer.parseInt(quantityObj.toString()));
                            } else {
                                item.setQuantity(1);
                            }

                            itemList.add(item);
                        }
                    }

                    adapter = new OrderItemsAdapter(itemList);
                    recyclerView.setAdapter(adapter);

                    // Module 4 - "surface alternative open restaurants".
                    // Only shown when this order was actually rejected and
                    // the backend found at least one alternative at the
                    // same meal station.
                    String orderStatus = doc.getString("orderStatus");
                    Boolean hasAlternatives = doc.getBoolean("hasAlternatives");

                    if ("Rejected".equalsIgnoreCase(orderStatus)
                            && hasAlternatives != null && hasAlternatives) {

                        showAlternativeRestaurants(doc);

                    } else {

                        layoutRejectedAlternatives.setVisibility(View.GONE);
                    }

                    // Module 6 (Failure 2) - "still looking for a rider"
                    // banner with a manual cancel option.
                    Boolean riderSearchExhausted = doc.getBoolean("riderSearchExhausted");

                    if (riderSearchExhausted != null && riderSearchExhausted
                            && !"Cancelled".equalsIgnoreCase(orderStatus)
                            && !"completed".equalsIgnoreCase(orderStatus)) {

                        layoutNoRiderFound.setVisibility(View.VISIBLE);

                        btnCancelNoRider.setOnClickListener(v ->
                                new AlertDialog.Builder(requireContext())
                                        .setTitle("Cancel Order")
                                        .setMessage("Aap is order ko cancel karna chahte hain? Aapko poora refund mil jayega.")
                                        .setPositiveButton("Yes, Cancel", (d, w) ->
                                                firestore.collection("Orders").document(orderId)
                                                        .update("orderStatus", "Cancelled")
                                                        .addOnSuccessListener(unused ->
                                                                layoutNoRiderFound.setVisibility(View.GONE)))
                                        .setNegativeButton("No", null)
                                        .show());

                    } else {

                        layoutNoRiderFound.setVisibility(View.GONE);
                    }

                    // Module: live tracking for the passenger. Available
                    // from the moment a rider is assigned right up until
                    // the order is completed/cancelled - so the passenger
                    // can watch the rider approach their station the same
                    // way the rider and restaurant can. Before this there
                    // was no way in at all from the passenger's side.
                    boolean trackable = orderStatus != null
                            && (orderStatus.equals("accepted_by_rider")
                             || orderStatus.equals("arrive_rider_at_resturent")
                             || orderStatus.equals("dropped")
                             || orderStatus.equals("pick_up"));

                    // ✅ FIX: this screen only ever showed the order id,
                    // total and item list - none of the journey details the
                    // passenger actually cares about (which train, which
                    // station, seat, current status, arrival time). All of
                    // it was already on the order document, just never bound.
                    bindOrderDetails(doc, orderStatus);

                    setupPassengerContact(doc, orderStatus, trackable);

                    if (btnTrackLive != null) {

                        btnTrackLive.setVisibility(trackable ? View.VISIBLE : View.GONE);

                        btnTrackLive.setOnClickListener(v -> {

                            if (!isAdded()) return;

                            requireActivity().getSupportFragmentManager()
                                    .beginTransaction()
                                    .replace(R.id.main_container,
                                            com.example.paktrainfoodapp.ui.main.Restaurant.order
                                                    .OrderDetailFragment.newInstance(orderId))
                                    .addToBackStack(null)
                                    .commit();
                        });
                    }

                })
                .addOnFailureListener(e ->
                        txtOrderId.setText("Failed to load order"));
    }

    /**
     * Builds one button per alternative restaurant (the order's own
     * trainName/routeId/from/to/mealStation are reused so the passenger
     * lands straight on that restaurant's menu for the SAME journey,
     * without re-entering any of that).
     */
    private void showAlternativeRestaurants(com.google.firebase.firestore.DocumentSnapshot orderDoc) {

        List<Map<String, Object>> alternatives =
                (List<Map<String, Object>>) orderDoc.get("alternativeRestaurants");

        if (alternatives == null || alternatives.isEmpty()) {
            layoutRejectedAlternatives.setVisibility(View.GONE);
            return;
        }

        containerAlternativeRestaurants.removeAllViews();

        String mealStation = orderDoc.getString("mealStation");
        String trainName = orderDoc.getString("trainName");
        String routeId = orderDoc.getString("routeId");
        String fromStation = orderDoc.getString("fromStation");
        String toStation = orderDoc.getString("toStation");

        for (Map<String, Object> alt : alternatives) {

            String restId = String.valueOf(alt.get("restaurantId"));
            String restName = String.valueOf(alt.get("restaurantName"));

            Button btn = new Button(requireContext());
            btn.setText(restName);
            btn.setAllCaps(false);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0x33FFFFFF);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = (int) (4 * getResources().getDisplayMetrics().density);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> openRestaurantMenu(
                    restId, restName, mealStation, trainName, routeId, fromStation, toStation));

            containerAlternativeRestaurants.addView(btn);
        }

        layoutRejectedAlternatives.setVisibility(View.VISIBLE);
    }

    private void openRestaurantMenu(
            String restaurantId, String restaurantName, String mealStation,
            String trainName, String routeId, String fromStation, String toStation) {

        Resturent_Menu_Fragment fragment = new Resturent_Menu_Fragment();

        Bundle bundle = new Bundle();
        bundle.putString("RESTAURANT_UID", restaurantId);
        bundle.putString("RESTAURANT_NAME", restaurantName);
        bundle.putString("MEAL_STATION", mealStation);
        bundle.putString("TRAIN_NAME", trainName);
        bundle.putString("ROUTE_ID", routeId);
        bundle.putString("FROM", fromStation);
        bundle.putString("TO", toStation);
        fragment.setArguments(bundle);

        Fragment parentFrag = getParentFragment();

        if (parentFrag instanceof Passenger_Fragment_Loader) {
            ((Passenger_Fragment_Loader) parentFrag).openRestaurantMenu(fragment);
        }
    }

    // Show Bottom Navigation Again
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        LinearLayout bottomNav =
                requireActivity().findViewById(R.id.bottom_nav);

        if (bottomNav != null) {
            bottomNav.setVisibility(View.VISIBLE);
        }
    }

    private void bindOrderDetails(com.google.firebase.firestore.DocumentSnapshot doc,
                                  String orderStatus) {

        if (txtOrderStatus != null) {
            txtOrderStatus.setText("Status: " + prettyStatus(orderStatus));
        }

        if (txtOrderTrain != null) {
            String train = doc.getString("trainName");
            txtOrderTrain.setText("Train: " + (train != null ? train : "-"));
        }

        if (txtOrderStation != null) {
            String station = doc.getString("mealStation");
            txtOrderStation.setText("Delivery Station: " + (station != null ? station : "-"));
        }

        if (txtOrderSeat != null) {
            String coach = doc.getString("coachNumber");
            String seat = doc.getString("seatNumber");
            txtOrderSeat.setText("Coach " + (coach != null ? coach : "-")
                    + "  \u2022  Seat " + (seat != null ? seat : "-"));
        }

        if (txtOrderEta != null) {

            Long eta = doc.getLong("trainEtaEndTime");

            if (eta != null && eta > 0 && !"completed".equalsIgnoreCase(orderStatus)) {
                txtOrderEta.setVisibility(View.VISIBLE);
                txtOrderEta.setText("Estimated Arrival: "
                        + new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                            .format(new java.util.Date(eta)));
            } else {
                txtOrderEta.setVisibility(View.GONE);
            }
        }

        boolean completed = "completed".equalsIgnoreCase(orderStatus);

        if (txtCompletedTime != null) {
            if (completed) {
                Long completedAt = doc.getLong("completedAt");
                txtCompletedTime.setVisibility(View.VISIBLE);
                if (completedAt != null && completedAt > 0) {
                    txtCompletedTime.setText("Delivered at: " + new java.text.SimpleDateFormat(
                            "dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                            .format(new java.util.Date(completedAt)));
                } else {
                    txtCompletedTime.setText("Delivered");
                }
            } else {
                txtCompletedTime.setVisibility(View.GONE);
            }
        }

        if (layoutCompletedChat != null) {
            layoutCompletedChat.setVisibility(completed ? View.VISIBLE : View.GONE);
        }

        if (completed) {
            loadCompletedChatHistory();
        }
    }

    /**
     * Read-only transcript for a finished order - no send box, this is
     * just a record of what was said between the passenger and the rider
     * (the passenger was only ever in the "chats_passenger" thread - the
     * rider<->restaurant thread never involved them). A completed order's
     * chat can't change any more, so a single fetch is enough.
     */
    private void loadCompletedChatHistory() {

        if (orderId == null || recyclerCompletedChat == null || firestore == null) return;

        if (completedChatAdapter == null) {
            completedChatAdapter = new CompletedChatAdapter(completedChatMessages);
            recyclerCompletedChat.setAdapter(completedChatAdapter);
        }

        firestore.collection("Orders").document(orderId)
                .collection("chats_passenger")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {

                    if (!isAdded()) return;

                    completedChatMessages.clear();

                    if (task.isSuccessful() && task.getResult() != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc :
                                task.getResult()) {

                            CompletedChatMessage m = new CompletedChatMessage();
                            m.senderName = doc.getString("senderName");
                            m.text = doc.getString("text");
                            Long ts = doc.getLong("timestamp");
                            m.timestamp = ts != null ? ts : 0L;

                            completedChatMessages.add(m);
                        }
                    }

                    completedChatAdapter.notifyDataSetChanged();

                    if (txtCompletedChatEmpty != null) {
                        txtCompletedChatEmpty.setVisibility(
                                completedChatMessages.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    if (!completedChatMessages.isEmpty()) {
                        recyclerCompletedChat.scrollToPosition(completedChatMessages.size() - 1);
                    }
                });
    }

    private static class CompletedChatMessage {
        String senderName, text;
        long timestamp;
    }

    private static class CompletedChatAdapter
            extends RecyclerView.Adapter<CompletedChatAdapter.VH> {

        private final List<CompletedChatMessage> items;

        CompletedChatAdapter(List<CompletedChatMessage> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_theirs, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {

            CompletedChatMessage m = items.get(position);

            h.txtMessage.setText(m.text);
            h.txtTime.setText(new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    .format(new java.util.Date(m.timestamp)));

            if (h.txtSender != null) {
                h.txtSender.setText(m.senderName != null ? m.senderName : "");
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {

            TextView txtMessage, txtTime, txtSender;

            VH(@NonNull View itemView) {
                super(itemView);
                txtMessage = itemView.findViewById(R.id.txtChatMessage);
                txtTime = itemView.findViewById(R.id.txtChatTime);
                txtSender = itemView.findViewById(R.id.txtChatSender);
            }
        }
    }

    /** Raw status keys are internal - passengers get plain language. */
    private String prettyStatus(String status) {

        if (status == null) return "-";

        switch (status) {
            case "Active": return "Waiting for restaurant";
            case "Accepted": return "Being prepared";
            case "ready_for_delivery": return "Ready - finding a rider";
            case "accepted_by_rider": return "Rider assigned";
            case "arrive_rider_at_resturent": return "Rider at restaurant";
            case "dropped": return "Handed to rider";
            case "pick_up": return "On the way to you";
            case "completed": return "Delivered";
            case "Cancelled": return "Cancelled";
            case "Rejected": return "Not accepted";
            case "delivery_failed":
            case "disputed": return "Under review";
            default: return status;
        }
    }

    /**
     * The passenger can chat/call the rider only once the food is actually
     * with the rider and on its way - before pickup there is nothing for
     * them to coordinate, and after completion the thread closes.
     */
    private void setupPassengerContact(com.google.firebase.firestore.DocumentSnapshot doc,
                                       String orderStatus, boolean trackable) {

        if (layoutPassengerContact == null) return;

        boolean canContactRider = "pick_up".equals(orderStatus);

        if (!canContactRider) {
            layoutPassengerContact.setVisibility(View.GONE);
            return;
        }

        layoutPassengerContact.setVisibility(View.VISIBLE);

        final String riderPhone = doc.getString("riderPhone");

        btnPassengerChat.setOnClickListener(v -> {

            if (!isAdded()) return;

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container,
                            com.example.paktrainfoodapp.ui.shared.chat.OrderChatFragment
                                    .newInstance(orderId,
                                            com.example.paktrainfoodapp.ui.shared.chat
                                                    .OrderChatFragment.TYPE_PASSENGER,
                                            "PakTrain Rider", riderPhone))
                    .addToBackStack(null)
                    .commit();
        });

        btnPassengerCall.setOnClickListener(v -> {

            if (riderPhone == null || riderPhone.trim().isEmpty()) {
                Toast.makeText(getContext(), "Rider's number not available yet",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                startActivity(new android.content.Intent(
                        android.content.Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:" + riderPhone.trim())));
            } catch (Exception e) {
                Toast.makeText(getContext(), "Couldn't open dialer", Toast.LENGTH_SHORT).show();
            }
        });
    }
}



//
