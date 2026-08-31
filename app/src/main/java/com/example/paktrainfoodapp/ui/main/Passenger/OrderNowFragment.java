package com.example.paktrainfoodapp.ui.main.Passenger;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.CartManager;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.data.AppConfig;

// ✅ FIX: CartItem ab "Passenger.home" package mein hai, is file ka package
// "Passenger" hai — is liye explicit import zaroori hai. Yehi ek line missing
// thi jis ki wajah se "cannot find symbol: class CartItem" error aa raha tha.
import com.example.paktrainfoodapp.ui.main.Passenger.home.CartItem;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;

public class OrderNowFragment extends DialogFragment {

    private static final String ARG_NAME = "itemName";
    private static final String ARG_PRICE = "itemPrice";
    private static final String ARG_DESC = "itemDesc";
    private static final String ARG_REST = "itemRest";
    private static final String ARG_REST_UID = "restaurantUid";
    private static final String ARG_IMAGE = "itemImage";
    private static final String ARG_MEAL_STATION = "mealStation";
    private static final String ARG_TRAIN_ID = "trainId";
    private static final String ARG_ROUTE_ID = "routeId";
    private static final String ARG_FROM = "from";
    private static final String ARG_TO = "to";
    private static final String ARG_CART_ITEMS = "cartItems";

    private ImageView imgFood;
    private TextView txtName, txtPrice, txtDesc, txtRest;

    private EditText edtTicket, edtCoach, edtSeat, edtTrain, edtPhone;
    private Button btnOrderNow, btnCancel;

    private String passengerUid = "";
    private String orderId = "";
    private String mealStation = "";

    public static OrderNowFragment newInstance(
            String name,
            double price,
            String desc,
            String rest,
            String restUid,
            String image,
            String mealStation,
            String trainId,
            String routeId,
            String from,
            String to,
            ArrayList<CartItem> cartItems
    ) {

        OrderNowFragment fragment = new OrderNowFragment();
        Bundle args = new Bundle();

        args.putString(ARG_NAME, name);
        args.putDouble(ARG_PRICE, price);
        args.putString(ARG_DESC, desc);
        args.putString(ARG_REST, rest);
        args.putString(ARG_REST_UID, restUid);
        args.putString(ARG_IMAGE, image);
        args.putString(ARG_MEAL_STATION, mealStation);
        args.putString(ARG_TRAIN_ID, trainId);
        args.putString(ARG_ROUTE_ID, routeId);
        args.putString(ARG_FROM, from);
        args.putString(ARG_TO, to);

        args.putSerializable(ARG_CART_ITEMS, cartItems);

        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_passanger_order_now_form, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imgFood = view.findViewById(R.id.imgFood);
        txtName = view.findViewById(R.id.txtName);
        txtPrice = view.findViewById(R.id.txtPrice);
        txtDesc = view.findViewById(R.id.txtDesc);
        txtRest = view.findViewById(R.id.txtRest);

        edtTicket = view.findViewById(R.id.edtTicket);
        edtCoach = view.findViewById(R.id.edtCoach);
        edtSeat = view.findViewById(R.id.edtSeat);
        edtTrain = view.findViewById(R.id.edtTrain);
        edtPhone = view.findViewById(R.id.edtPhone);

        btnOrderNow = view.findViewById(R.id.btnOrderNow);
        btnCancel = view.findViewById(R.id.btnCancel);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "Login Required", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        passengerUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Bundle args = getArguments();
        if (args == null) return;

        @SuppressWarnings("unchecked")
        ArrayList<CartItem> cartItems =
                (ArrayList<CartItem>) args.getSerializable(ARG_CART_ITEMS);

        String name = args.getString(ARG_NAME, "");
        double price = args.getDouble(ARG_PRICE, 0);
        String desc = args.getString(ARG_DESC, "");
        String rest = args.getString(ARG_REST, "");
        String restUid = args.getString(ARG_REST_UID, "");
        String image = args.getString(ARG_IMAGE, "");

        mealStation = args.getString(ARG_MEAL_STATION, "");

        String trainId = args.getString(ARG_TRAIN_ID, "");
        String routeId = args.getString(ARG_ROUTE_ID, "");
        String from = args.getString(ARG_FROM, "");
        String to = args.getString(ARG_TO, "");

        txtName.setText(name);
        txtPrice.setText("Rs. " + price);
        txtDesc.setText(desc);
        txtRest.setText(rest);

        if (!TextUtils.isEmpty(image)) {
            Glide.with(this)
                    .load(image)
                    .placeholder(R.drawable.ic_food_placeholder)
                    .into(imgFood);
        }

        btnCancel.setOnClickListener(v -> dismiss());

        btnOrderNow.setOnClickListener(v -> {

            if (!hasLocationPermission()) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        }, 1001);
                return;
            }

            if (!isLocationEnabled()) {
                showLocationDialog();
                return;
            }

            placeOrder(name, price, desc, rest, restUid, image,
                    trainId, routeId, from, to, cartItems);
        });
    }

    private void placeOrder(
            String name, double price, String desc,
            String rest, String restUid, String image,
            String trainId, String routeId, String from, String to,
            ArrayList<CartItem> cartItems
    ) {

        String ticket = edtTicket.getText().toString().trim();
        String coach = edtCoach.getText().toString().trim();
        String seat = edtSeat.getText().toString().trim();
        String trainName = edtTrain.getText().toString().trim();
        String phone = edtPhone.getText().toString().trim();

        if (TextUtils.isEmpty(ticket) || TextUtils.isEmpty(coach)
                || TextUtils.isEmpty(seat) || TextUtils.isEmpty(trainName)
                || TextUtils.isEmpty(phone)) {
            Toast.makeText(getContext(), "Fill All Fields", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        orderId = db.collection("Orders").document().getId();

        HashMap<String, Object> order = new HashMap<>();
        double restaurantTotal = 0;
        ArrayList<HashMap<String, Object>> list = new ArrayList<>();

        if (cartItems != null) {
            for (CartItem c : cartItems) {
                // DEBUG CHECK
                Log.d("REST_CHECK",
                        "cartRest = " + c.getRestaurantId()
                                + " currentRest = " + restUid);
                if (c.getRestaurantId() != null
                        && c.getRestaurantId().equals(restUid)) {
                    HashMap<String, Object> m = new HashMap<>();
                    m.put("itemId", c.getItemId());
                    m.put("name", c.getName());
                    m.put("price", c.getPrice());
                    m.put("quantity", c.getQuantity());
                    m.put("size", c.getSize());
                    m.put("restaurantId", c.getRestaurantId());
                    m.put("restaurantName", c.getRestaurantName());
                    m.put("imageUrl", c.getImageUrl());
                    m.put("description", c.getDescription());
                    list.add(m);

                    restaurantTotal +=
                            c.getPrice() * c.getQuantity();
                }
            }
        }

        order.put("cartItems", list);
        order.put("totalItems", list.size());
        order.put("totalPrice", restaurantTotal);

        order.put("orderId", orderId);
        order.put("restaurantName", rest);
        order.put("restaurantUid", restUid);
        order.put("passengerUid", passengerUid);
        order.put("trainName", trainName);
        order.put("trainId", trainId);
        order.put("routeId", routeId);
        order.put("fromStation", from);
        order.put("toStation", to);
        order.put("mealStation", mealStation);
        order.put("ticketNumber", ticket);
        order.put("coachNumber", coach);
        order.put("seatNumber", seat);
        order.put("phone", phone);
        order.put("orderStatus", "Active");
        order.put("timestamp", System.currentTimeMillis());

        btnOrderNow.setEnabled(false);

        db.collection("Orders")
                .document(orderId)
                .set(order)
                .addOnSuccessListener(unused -> {

                    // ==============================
                    // START LOCATION SERVICE
                    // ==============================

                    Intent serviceIntent =
                            new Intent(requireContext(), LocationService.class);

                    serviceIntent.putExtra("orderId", orderId);
                    serviceIntent.putExtra("passengerUid", passengerUid);
                    serviceIntent.putExtra("station", mealStation);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(serviceIntent);
                    } else {
                        requireContext().startService(serviceIntent);
                    }

                    Toast.makeText(getContext(),
                            "Order Placed Successfully",
                            Toast.LENGTH_LONG).show();

                    // ==============================
                    // Module 2 - INITIAL ETA ESTIMATE
                    //
                    // So the restaurant's order list shows "Estimated
                    // Arrival" immediately, without needing anyone to open
                    // the live-tracking detail screen first. This is a
                    // rough straight-line-distance guess using the admin-
                    // configured fallback speed; OrderDetailFragment (once
                    // opened) and the backend location trigger both refine
                    // it further with real GPS data as the journey goes on.
                    // ==============================

                    saveInitialTrainEtaEstimate(from, mealStation);

                    CartManager.clear();
                    dismiss();
                })
                .addOnFailureListener(e -> {
                    btnOrderNow.setEnabled(true);
                    Toast.makeText(getContext(),
                            "Order Failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Module 2 - fetches the boarding station's and meal station's
     * coordinates and writes a rough initial "trainEtaEndTime" estimate
     * (straight-line distance / admin-configured fallback speed) onto the
     * just-created order, so the restaurant's order list has something
     * meaningful to show right away. Best-effort: if either station's
     * coordinates are missing, this silently does nothing rather than
     * failing the whole order (the order itself already succeeded).
     */
    private void saveInitialTrainEtaEstimate(String fromStation, String mealStationName) {

        if (orderId == null || orderId.isEmpty()
                || fromStation == null || fromStation.isEmpty()
                || mealStationName == null || mealStationName.isEmpty()) {
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        DocumentReference fromRef = db.collection("RailwaySystem").document("main")
                .collection("Stations").document(fromStation);

        DocumentReference mealRef = db.collection("RailwaySystem").document("main")
                .collection("Stations").document(mealStationName);

        fromRef.get().addOnSuccessListener(fromDoc -> {

            if (!fromDoc.exists()) return;

            Double fromLat = fromDoc.getDouble("lat");
            Double fromLng = fromDoc.getDouble("lng");

            if (fromLat == null || fromLng == null) return;

            mealRef.get().addOnSuccessListener(mealDoc -> {

                if (!mealDoc.exists()) return;

                Double mealLat = mealDoc.getDouble("lat");
                Double mealLng = mealDoc.getDouble("lng");

                if (mealLat == null || mealLng == null) return;

                float[] distanceMeters = new float[1];
                android.location.Location.distanceBetween(
                        fromLat, fromLng, mealLat, mealLng, distanceMeters);

                double distanceKm = distanceMeters[0] / 1000.0;
                double speedKmph = AppConfig.get().getFallbackTrainSpeedKmph();

                if (speedKmph <= 0) return;

                double minutes = (distanceKm / speedKmph) * 60.0;
                long trainEtaEndTime = System.currentTimeMillis() + Math.round(minutes) * 60_000L;

                db.collection("Orders").document(orderId)
                        .update("trainEtaEndTime", trainEtaEndTime);
            });
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) requireActivity()
                .getSystemService(Context.LOCATION_SERVICE);

        return lm != null &&
                (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private void showLocationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Enable Location")
                .setMessage("Location required for delivery tracking")
                .setPositiveButton("Open Settings", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null && getActivity() != null) {

            DisplayMetrics dm = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

            dialog.getWindow().setLayout(
                    (int) (dm.widthPixels * 0.95),
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }
}
