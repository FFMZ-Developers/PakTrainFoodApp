package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.net.Uri;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.CartManager;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Passenger.LocationService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class OrderNowFragment extends DialogFragment {

    private ImageView imgFood;
    private TextView txtName, txtPrice, txtDesc, txtRest;
    private EditText edtTicket, edtCoach, edtSeat, edtTrain, edtPhone;
    private Button btnOrderNow, btnCancel;

    private String passengerUid;
    private PaymentSheet paymentSheet;
    private String clientSecret;

    private double subtotalVal = 0;
    private double deliveryFeeVal = 0;
    private double adminFeeVal = 0;
    private double totalAmount = 0;

    private static final String ARG_NAME = "itemName";
    private static final String ARG_PRICE = "itemPrice";
    private static final String ARG_DESC = "itemDesc";
    private static final String ARG_REST = "itemRest";
    private static final String ARG_IMAGE = "itemImage";
    private static final String ARG_CART_ITEMS = "cartItems";
    private String currentOrderId = "";
    private String currentStation = "";

    private androidx.activity.result.ActivityResultLauncher<String> backgroundLocationLauncher;
    private androidx.activity.result.ActivityResultLauncher<String> foregroundLocationLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Must be registered before the fragment reaches STARTED state.
        // Ask for the normal location permission inside the app first; only
        // send the user to system Settings if they permanently denied it.
        foregroundLocationLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                granted -> {

                    if (granted) {
                        continueAfterForegroundPermission();
                    } else if (!shouldShowRequestPermissionRationale(
                            Manifest.permission.ACCESS_FINE_LOCATION)) {
                        // "Don't ask again" - Settings is the only way now
                        showLocationPermissionDialog();
                    } else {
                        Toast.makeText(getContext(),
                                "Location permission is needed to place an order",
                                Toast.LENGTH_SHORT).show();
                    }
                });

        backgroundLocationLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                granted -> {

                    if (granted) {

                        startPaymentFlow();

                    } else {

                        btnOrderNow.setEnabled(true);
                        btnOrderNow.setText("Order Now");

                        Toast.makeText(getContext(),
                                "Background location is required to place an order",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

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
            ArrayList<CartItem> cartItems,
            double sub,
            double del,
            double adm
    ) {

        OrderNowFragment fragment = new OrderNowFragment();
        Bundle args = new Bundle();

        args.putString(ARG_NAME, name);
        args.putDouble(ARG_PRICE, price);
        args.putString(ARG_DESC, desc);
        args.putString(ARG_REST, rest);
        args.putString(ARG_IMAGE, image);

        args.putSerializable(ARG_CART_ITEMS, cartItems);

        args.putDouble("subtotal", sub);
        args.putDouble("deliveryFee", del);
        args.putDouble("adminFee", adm);

        args.putDouble("total", sub + del + adm);

        args.putString("restaurantId", restUid);

        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
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

        paymentSheet = new PaymentSheet(this, this::onPaymentResult);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(getContext(), "Login Required", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        passengerUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Bundle args = getArguments();
        if (args != null) {

            txtName.setText(args.getString(ARG_NAME, ""));
            txtPrice.setText("Rs. " + args.getDouble(ARG_PRICE, 0));
            txtDesc.setText(args.getString(ARG_DESC, ""));
            txtRest.setText(args.getString(ARG_REST, ""));

            subtotalVal = args.getDouble("subtotal", 0);
            deliveryFeeVal = args.getDouble("deliveryFee", 0);
            adminFeeVal = args.getDouble("adminFee", 0);
            totalAmount = args.getDouble("total", 0);

            String image = args.getString(ARG_IMAGE, "");
            if (!TextUtils.isEmpty(image)) {
                Glide.with(this)
                        .load(image)
                        .placeholder(R.drawable.ic_food_placeholder)
                        .into(imgFood);
            }
        }

        btnCancel.setOnClickListener(v -> dismiss());

        btnOrderNow.setOnClickListener(v -> {
            String ticket = edtTicket.getText().toString().trim();
            String coach = edtCoach.getText().toString().trim();
            String seat = edtSeat.getText().toString().trim();
            String train = edtTrain.getText().toString().trim();
            String phone = edtPhone.getText().toString().trim();

            if (TextUtils.isEmpty(ticket) ||
                    TextUtils.isEmpty(coach) ||
                    TextUtils.isEmpty(seat) ||
                    TextUtils.isEmpty(train) ||
                    TextUtils.isEmpty(phone)) {

                Toast.makeText(
                        getContext(),
                        "Please fill all fields",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }
//            if (!hasLocationPermission() || !isLocationEnabled()) {
//                Toast.makeText(getContext(), "Permission/Location Error", Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            startPaymentFlow();
            if (!hasLocationPermission()) {
                foregroundLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                return;
            }

            continueAfterForegroundPermission();
        });
    }

    private void continueAfterForegroundPermission() {

        if (!isLocationEnabled()) {
            showLocationDisabledDialog();
            return;
        }

        requestBackgroundLocationThenProceed();
    }

    /**
     * On Android 10+ (Q), continuous location updates while the app is in the
     * background require ACCESS_BACKGROUND_LOCATION separately from
     * ACCESS_FINE_LOCATION - just declaring it in the manifest is not enough.
     * This is now REQUIRED before an order can be placed, so the rider is
     * guaranteed to be able to track the passenger for the whole delivery.
     */
    private void requestBackgroundLocationThenProceed() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startPaymentFlow();
            return;
        }

        boolean hasBackground = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (hasBackground) {
            startPaymentFlow();
            return;
        }

        // Android 11+ never shows the background-location dialog twice. If the
        // system won't show it again, app Settings is the only way left to
        // grant it - this is the ONLY case that leaves the app, because there
        // is no other route to "Allow all the time" on those versions.
        boolean canAskAgain = shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle("Background Location Required")
                .setMessage(
                        "So your rider can find you for the whole delivery, please "
                                + "choose \"Allow all the time\" on the next screen.\n\n"
                                + "This is required to place an order.")
                .setCancelable(false);

        if (canAskAgain || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {

            builder.setPositiveButton("Allow", (dialog, which) ->
                    backgroundLocationLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION));

        } else {

            builder.setPositiveButton("Open Settings", (dialog, which) -> {

                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package",
                                requireContext().getPackageName(), null));

                startActivity(intent);
            });
        }

        builder.setNegativeButton("Cancel", (dialog, which) -> {

            btnOrderNow.setEnabled(true);
            btnOrderNow.setText("Order Now");
        });

        builder.show();
    }

    private void startPaymentFlow() {

        btnOrderNow.setEnabled(false);
        btnOrderNow.setText("Processing...");

        if (getArguments() == null) return;

        totalAmount = getArguments().getDouble("total", 0);

        HashMap<String, Object> data = new HashMap<>();
        data.put("amount", totalAmount);

        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("createPaymentIntent")
                .call(data)
                .addOnSuccessListener(result -> {

                    Map<String, Object> response = (Map<String, Object>) result.getData();
                    clientSecret = (String) response.get("clientSecret");

                    if (clientSecret != null) {

                        PaymentSheet.Configuration config =
                                new PaymentSheet.Configuration("Train Food App");

                        paymentSheet.presentWithPaymentIntent(clientSecret, config);

                    } else {
                        Toast.makeText(getContext(), "Payment error", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {

                    btnOrderNow.setEnabled(true);
                    btnOrderNow.setText("Order Now");

                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void onPaymentResult(PaymentSheetResult result) {

        btnOrderNow.setEnabled(true);
        btnOrderNow.setText("Order Now");

        if (result instanceof PaymentSheetResult.Completed) {

            saveOrderToFirestore();
//            showSuccessDialog();

        } else if (result instanceof PaymentSheetResult.Failed) {

            Toast.makeText(getContext(), "Payment Failed", Toast.LENGTH_SHORT).show();

        } else {

            Toast.makeText(getContext(), "Payment Cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveOrderToFirestore() {
        String ticketNumber = edtTicket.getText().toString().trim();
        String coachNumber = edtCoach.getText().toString().trim();
        String seatNumber = edtSeat.getText().toString().trim();
        String phone = edtPhone.getText().toString().trim();
        String trainNumber = edtTrain.getText().toString().trim();


        FirebaseFirestore db = FirebaseFirestore.getInstance();

        currentOrderId = db.collection("Orders")
                .document()
                .getId();

        String orderId = currentOrderId;

        Map<String, Object> orderData = new HashMap<>();

        // Basic Order Info

        orderData.put("orderId", orderId);
        orderData.put("passengerUid", passengerUid);
        orderData.put("orderStatus", "Active");
        orderData.put("timestamp", System.currentTimeMillis());

        // Passenger Details

        orderData.put("ticketNumber", ticketNumber);
        orderData.put("coachNumber", coachNumber);
        orderData.put("seatNumber", seatNumber);
        orderData.put("phone", phone);
        orderData.put("trainId", trainNumber);

        // Restaurant

        orderData.put("restaurantId",
                getArguments().getString("restaurantId"));

        orderData.put("restaurantName",
                txtRest.getText().toString());

        // Cart Items

        ArrayList<CartItem> cartItems =
                (ArrayList<CartItem>)
                        getArguments().getSerializable(ARG_CART_ITEMS);

        ArrayList<Map<String, Object>> itemsList =
                new ArrayList<>();

        if (cartItems != null && !cartItems.isEmpty()) {

            CartItem first = cartItems.get(0);
            currentStation = first.getMealStation();

            // Order Level Fields

            orderData.put("mealStation",
                    first.getMealStation());

            orderData.put("fromStation",
                    first.getFromStation());

            orderData.put("toStation",
                    first.getToStation());

            orderData.put("routeId",
                    first.getRouteId());

            orderData.put("trainName",
                    first.getTrainName());

            orderData.put("totalItems",
                    cartItems.size());

            // Only Item Data

            for (CartItem item : cartItems) {

                Map<String, Object> map =
                        new HashMap<>();

                map.put("itemId",
                        item.getItemId());

                map.put("name",
                        item.getName());

                map.put("description",
                        item.getDescription());

                map.put("imageUrl",
                        item.getImageUrl());

                map.put("price",
                        item.getPrice());

                map.put("quantity",
                        item.getQuantity());

                map.put("size",
                        item.getSize());

                map.put("restaurantId",
                        item.getRestaurantId());

                map.put("restaurantName",
                        item.getRestaurantName());

                itemsList.add(map);
            }
        }

        orderData.put("cartItems", itemsList);

        // Pricing

        orderData.put("subtotal", subtotalVal);
        orderData.put("deliveryFee", deliveryFeeVal);
        orderData.put("adminFee", adminFeeVal);
        orderData.put("totalPrice", totalAmount);

        // Save Order

        db.collection("Orders")
                .document(orderId)
                .set(orderData)
                .addOnSuccessListener(unused -> {

                    if (!isAdded() || getActivity() == null)
                        return;

                    Context context = getActivity().getApplicationContext();

                    Intent serviceIntent =
                            new Intent(context, LocationService.class);

                    serviceIntent.putExtra("orderId", currentOrderId);
                    serviceIntent.putExtra("passengerUid", passengerUid);
                    serviceIntent.putExtra("station", currentStation);

                    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                        context.startForegroundService(serviceIntent);

                    } else {

                        context.startService(serviceIntent);

                    }

                    CartManager.clear();

                    Toast.makeText(
                            getActivity(),
                            "Order Placed Successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    showSuccessDialog();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            getContext(),
                            e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void showSuccessDialog() {

        if (!isAdded())
            return;

        new AlertDialog.Builder(requireActivity())
                .setTitle("Success")
                .setMessage("Order placed successfully")
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> dismissAllowingStateLoss())
                .show();
    }

    private void showLocationDisabledDialog() {

        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Turn On Location")
                .setMessage(
                        "Your device location is currently turned off. Please turn on Location to continue placing your order and sharing your live location."
                )
                .setCancelable(false)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Turn On Location", (dialog, which) -> {

                    Intent intent = new Intent(
                            Settings.ACTION_LOCATION_SOURCE_SETTINGS
                    );

                    startActivity(intent);
                })
                .show();
    }
    private void showLocationPermissionDialog() {

        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Location Permission Required")
                .setMessage(
                        "Location permission is required to place your order and share your live location with the delivery rider. Please allow location permission from App Settings."
                )
                .setCancelable(false)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open Settings", (dialog, which) -> {

                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    );

                    Uri uri = Uri.fromParts(
                            "package",
                            requireContext().getPackageName(),
                            null
                    );

                    intent.setData(uri);

                    startActivity(intent);
                })
                .show();
    }


    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95);

            dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}

















