package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.CartManager;
import com.example.paktrainfoodapp.data.CartResumeHelper;
import com.example.paktrainfoodapp.data.CartStorage;

import java.util.ArrayList;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.paktrainfoodapp.R;


public class CartFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private RecyclerView rvCart;
    private TextView tvCartTotal;
    private Button btnContinueOrder;
    private LinearLayout layoutCartEmpty;
    private LinearLayout layoutCartSummary;
    private LinearLayout layoutResumeBanner;
    private TextView txtResumeMessage;
    private Button btnResumeOrder, btnStartAgain;

    private ArrayList<CartItem> cartItems;

    private androidx.activity.result.ActivityResultLauncher<String> locationPermissionLauncher;

    public CartFragment() {
        // Required empty public constructor
    }


    // TODO: Rename and change types and number of parameters
    public static CartFragment newInstance(String param1, String param2) {
        CartFragment fragment = new CartFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        locationPermissionLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        Toast.makeText(getContext(),
                                "Location granted - tap Resume again",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private final Runnable cartChangeListener = this::loadCart;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view =
                inflater.inflate(
                        R.layout.fragment_passanger_cart,
                        container,
                        false
                );

        rvCart = view.findViewById(R.id.rvCart);

        tvCartTotal =
                view.findViewById(R.id.tvCartTotal);

        btnContinueOrder =
                view.findViewById(R.id.btnContinueOrder);

        layoutCartEmpty = view.findViewById(R.id.layoutCartEmpty);
        layoutCartSummary = view.findViewById(R.id.layoutCartSummary);
        layoutResumeBanner = view.findViewById(R.id.layoutResumeBanner);
        txtResumeMessage = view.findViewById(R.id.txtResumeMessage);
        btnResumeOrder = view.findViewById(R.id.btnResumeOrder);
        btnStartAgain = view.findViewById(R.id.btnStartAgain);

        setupResumeBanner();

        rvCart.setLayoutManager(
                new LinearLayoutManager(getContext())
        );

        btnContinueOrder.setOnClickListener(v -> {

            ArrayList<CartItem> items =
                    new ArrayList<>(CartManager.getCartItems());

            if (items.isEmpty()) {

                Toast.makeText(getContext(), "Your cart is empty", Toast.LENGTH_SHORT).show();

                return;
            }

            OrderSummaryFragment dialog =
                    OrderSummaryFragment.newInstance(items);

            dialog.show(getParentFragmentManager(), "OrderSummary");
        });

        loadCart();

        CartManager.addListener(cartChangeListener);

        return view;
    }

    /**
     * A cart that was still on disk when the app reopened is only valid for a
     * short window - see CartResumeHelper. Past that the passenger is told to
     * start again instead of silently ordering food that can no longer reach
     * their meal station in time.
     */
    /**
     * Shows the resume banner whenever there is a saved cart. The time check
     * happens immediately (it's local); the station check only happens when
     * the passenger actually taps Resume, since it needs a GPS fix.
     */
    private void setupResumeBanner() {

        CartResumeHelper.Result result =
                CartResumeHelper.check(requireContext());

        if (result.decision == CartResumeHelper.Decision.NOTHING_SAVED) {
            layoutResumeBanner.setVisibility(View.GONE);
            return;
        }

        layoutResumeBanner.setVisibility(View.VISIBLE);

        btnStartAgain.setOnClickListener(v -> startAgain());

        if (result.decision == CartResumeHelper.Decision.EXPIRED_TOO_LATE) {

            txtResumeMessage.setText("Too much late - please order again");

            // Disabled, not just relabeled, so it's visually obvious this
            // saved cart can no longer be continued.
            btnResumeOrder.setEnabled(false);
            btnResumeOrder.setAlpha(0.5f);
            btnResumeOrder.setOnClickListener(null);

        } else {

            txtResumeMessage.setText("You have an unfinished order");

            btnResumeOrder.setEnabled(true);
            btnResumeOrder.setAlpha(1f);
            btnResumeOrder.setOnClickListener(v -> resumeOrder());
        }
    }

    /**
     * Re-checks the passenger's current position against the meal station
     * right when Resume is tapped (not on screen load, so no GPS permission
     * is needed just to see the cart). If the train has already reached or
     * passed the meal station the cart is invalidated instead of resumed.
     */
    private void resumeOrder() {

        if (cartItems == null || cartItems.isEmpty()) return;

        CartItem first = cartItems.get(0);

        String routeId = first.getRouteId();
        String mealStation = first.getMealStation();

        if (routeId == null || routeId.isEmpty() || mealStation == null) {
            // No route info saved with this cart - can't verify, so let it
            // through rather than punishing an older/simpler cart entry.
            openCheckout();
            return;
        }

        if (!LocationHelper.hasPermission(requireActivity())) {
            LocationHelper.requestPermission(locationPermissionLauncher);
            Toast.makeText(getContext(),
                    "Please allow location, then tap Resume again",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnResumeOrder.setEnabled(false);

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("RailwaySystem").document("main")
                .collection("Routes").document(routeId)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!isAdded()) return;

                    btnResumeOrder.setEnabled(true);

                    java.util.List<java.util.Map<String, Object>> stationMaps =
                            (java.util.List<java.util.Map<String, Object>>) doc.get("stations");

                    java.util.List<String> route = new ArrayList<>();

                    if (stationMaps != null) {
                        for (java.util.Map<String, Object> s : stationMaps) {
                            Object name = s.get("name");
                            if (name != null) route.add(name.toString());
                        }
                    }

                    LocationHelper.getCurrentLocation(
                            requireActivity(),
                            new LocationHelper.LocationCallback() {

                                @Override
                                public void onLocationReceived(android.location.Location location) {

                                    if (!isAdded()) return;

                                    String currentStation =
                                            StationValidationHelper.getNearestStation(
                                                    location.getLatitude(),
                                                    location.getLongitude());

                                    CartResumeHelper.Result stationResult =
                                            CartResumeHelper.checkStation(cartItems, route, currentStation);

                                    if (stationResult.canResume()) {

                                        openCheckout();

                                    } else {

                                        // Station already reached/passed - the saved cart
                                        // is no longer valid, force a fresh journey selection.
                                        Toast.makeText(getContext(),
                                                stationResult.message != null
                                                        ? stationResult.message
                                                        : "Your train has already reached your meal station",
                                                Toast.LENGTH_LONG).show();

                                        startAgain();
                                    }
                                }

                                @Override
                                public void onLocationFailed(String message) {

                                    if (!isAdded()) return;

                                    btnResumeOrder.setEnabled(true);

                                    // Couldn't get a GPS fix - don't trap the passenger
                                    // behind an error, just let the order continue.
                                    Toast.makeText(getContext(),
                                            "Could not verify location - continuing anyway",
                                            Toast.LENGTH_SHORT).show();

                                    openCheckout();
                                }
                            });
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    btnResumeOrder.setEnabled(true);

                    // Couldn't verify - open checkout rather than trap the
                    // passenger behind a network error.
                    openCheckout();
                });
    }

    /** Jumps straight into checkout with the resumed items - no re-selecting the journey. */
    private void openCheckout() {

        OrderSummaryFragment dialog = OrderSummaryFragment.newInstance(cartItems);

        dialog.show(getParentFragmentManager(), "OrderSummary");
    }

    /** Wipes the stale cart and sends the passenger back to journey selection. */
    private void startAgain() {

        CartManager.clear();
        CartStorage.clear(requireContext());

        layoutResumeBanner.setVisibility(View.GONE);

        Toast.makeText(requireContext(),
                "Please select your journey again",
                Toast.LENGTH_SHORT).show();

        loadCart();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        CartManager.removeListener(cartChangeListener);
    }

    private void loadCart() {

        cartItems =
                new ArrayList<>(CartManager.getCartItems());

        if (rvCart == null) return;

        rvCart.setAdapter(
                new OrderSummaryAdapter(cartItems)
        );

        boolean empty = cartItems.isEmpty();

        if (layoutCartEmpty != null) {
            layoutCartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        }

        rvCart.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (layoutCartSummary != null) {
            layoutCartSummary.setVisibility(empty ? View.GONE : View.VISIBLE);
        }

        if (tvCartTotal != null) {

            tvCartTotal.setText(
                    "Rs " + (int) CartManager.getTotalPrice()
            );
        }

    }
}