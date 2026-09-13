package com.example.paktrainfoodapp.ui.main.Restaurant.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Restaurant.restaurant_LoadFragment;

public class resturent_DashboardFragment extends Fragment {

    private LinearLayout btnProfile, btnMenu, btnDelivery, btnHelpSupport;
    private LinearLayout layoutRecentOrders;
    private TextView tvOrdersCount, tvRevenue, tvMenuCount, tvRating, tvDashboardTitle,
            tvDashboardGreeting, tvNoRecentOrders, tvViewAllOrders;

    private RestaurantDashboardViewModel viewModel;

    public resturent_DashboardFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_resturent__dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnProfile = view.findViewById(R.id.btnProfile);
        btnMenu = view.findViewById(R.id.btnMenu);
        btnDelivery = view.findViewById(R.id.btnDelivery);
        btnHelpSupport = view.findViewById(R.id.btnHelpSupport);
        layoutRecentOrders = view.findViewById(R.id.layoutRecentOrders);
        tvNoRecentOrders = view.findViewById(R.id.tvNoRecentOrders);
        tvViewAllOrders = view.findViewById(R.id.tvViewAllOrders);
        tvDashboardGreeting = view.findViewById(R.id.tvDashboardGreeting);

        // Click listeners calling the parent's navigation method
        tvOrdersCount = view.findViewById(R.id.tvOrdersCount);
        tvRevenue = view.findViewById(R.id.tvRevenue);
        tvMenuCount = view.findViewById(R.id.tvMenuCount);
        tvRating = view.findViewById(R.id.tvRating);
        tvDashboardTitle = view.findViewById(R.id.tvDashboardTitle);

        btnMenu.setOnClickListener(v -> navigateTo("menu"));
        btnDelivery.setOnClickListener(v -> navigateTo("delivery"));
        btnProfile.setOnClickListener(v -> navigateTo("profile"));
        tvViewAllOrders.setOnClickListener(v -> navigateTo("order"));

        if (btnHelpSupport != null) {
            btnHelpSupport.setOnClickListener(v -> {
                if (isAdded()) {
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.main_container,
                                    new com.example.paktrainfoodapp.ui.shared.support.HelpSupportFragment())
                            .addToBackStack(null)
                            .commit();
                }
            });
        }

        // A time-based greeting reads as a small, human touch rather than
        // a static "Welcome Back!" that never changes.
        if (tvDashboardGreeting != null) {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            String greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
            tvDashboardGreeting.setText(greeting);
        }

        // Module 7 - warn the restaurant if their account has been
        // auto-paused for repeated reliability strikes (see
        // reliabilityHelper.js). Plain-language reason, same pattern as
        // Module 1's rejection flow.
        checkPauseStatus();

        // Stat cards open the screen they summarise
        if (tvOrdersCount != null) {
            ((View) tvOrdersCount.getParent()).setOnClickListener(v -> navigateTo("order"));
        }

        if (tvMenuCount != null) {
            ((View) tvMenuCount.getParent()).setOnClickListener(v -> navigateTo("menu"));
        }

        if (tvRating != null) {
            ((View) tvRating.getParent()).setOnClickListener(v -> openReviews());
        }

        viewModel = new ViewModelProvider(this).get(RestaurantDashboardViewModel.class);

        viewModel.getTotalOrders().observe(getViewLifecycleOwner(), v -> {
            if (tvOrdersCount != null) tvOrdersCount.setText(String.valueOf(v));
        });

        viewModel.getRevenue().observe(getViewLifecycleOwner(), v -> {
            if (tvRevenue != null) tvRevenue.setText("Rs. " + (v == null ? 0 : (int) v.doubleValue()));
        });

        viewModel.getMenuCount().observe(getViewLifecycleOwner(), v -> {
            if (tvMenuCount != null) tvMenuCount.setText(String.valueOf(v));
        });

        viewModel.getRestaurantName().observe(getViewLifecycleOwner(), name -> {
            if (tvDashboardTitle != null) tvDashboardTitle.setText(name);
        });

        viewModel.getRecentActiveOrders().observe(getViewLifecycleOwner(), this::bindRecentOrders);

        // ✅ FIX: this was hardcoded to "—" with a comment saying ratings
        // weren't collected anywhere - they are now (see
        // RateOrderDialogFragment), so this computes the real average
        // from this restaurant's own Reviews subcollection.
        loadAverageRating();

        viewModel.start();
    }

    /** Averages this restaurant's own Reviews subcollection for the
     *  dashboard's Rating stat card. */
    private void loadAverageRating() {

        if (tvRating == null) return;

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(uid)
                .collection("Reviews")
                .addSnapshotListener((snap, e) -> {

                    if (!isAdded() || tvRating == null) return;

                    if (e != null || snap == null || snap.isEmpty()) {
                        tvRating.setText("\u2014");
                        return;
                    }

                    double total = 0;
                    int count = 0;

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        Double rating = doc.getDouble("rating");
                        if (rating != null) {
                            total += rating;
                            count++;
                        }
                    }

                    tvRating.setText(count > 0
                            ? String.format(java.util.Locale.getDefault(), "%.1f", total / count)
                            : "\u2014");
                });
    }

    private void openReviews() {

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null || !isAdded()) return;

        String uid = auth.getCurrentUser().getUid();
        String name = tvDashboardTitle != null ? tvDashboardTitle.getText().toString() : "Ratings & Reviews";

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container,
                        com.example.paktrainfoodapp.ui.shared.reviews.ReviewsListFragment.newInstance(
                                com.example.paktrainfoodapp.ui.shared.reviews.ReviewsListFragment.ROOT_RESTAURANT,
                                uid, name))
                .addToBackStack(null)
                .commit();
    }

    /**
     * Builds each "Recent Orders" row programmatically - simple enough not
     * to need its own layout resource, and each row opens straight into
     * that order's own live detail/tracking screen.
     */
    private void bindRecentOrders(java.util.List<com.google.firebase.firestore.DocumentSnapshot> orders) {

        if (!isAdded() || layoutRecentOrders == null) return;

        layoutRecentOrders.removeAllViews();

        boolean empty = orders == null || orders.isEmpty();

        if (tvNoRecentOrders != null) tvNoRecentOrders.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (empty) return;

        float density = getResources().getDisplayMetrics().density;

        for (com.google.firebase.firestore.DocumentSnapshot doc : orders) {

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, (int) (8 * density), 0, (int) (8 * density));
            row.setClickable(true);
            row.setFocusable(true);

            android.util.TypedValue outValue = new android.util.TypedValue();
            requireContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, outValue, true);
            row.setBackgroundResource(outValue.resourceId);

            TextView txtTitle = new TextView(requireContext());
            txtTitle.setTextColor(0xFF000000);
            txtTitle.setTextSize(14);
            txtTitle.setText(com.example.paktrainfoodapp.utils.OrderNumberUtils
                    .format(doc.getLong("orderNumber"), doc.getId()));

            TextView txtStatus = new TextView(requireContext());
            txtStatus.setTextSize(13);
            txtStatus.setTextColor(0xFF00695C);
            txtStatus.setPadding(0, (int) (2 * density), 0, 0);
            txtStatus.setText(com.example.paktrainfoodapp.utils.StatusBadge.label(doc.getString("orderStatus")));

            row.addView(txtTitle);
            row.addView(txtStatus);

            row.setOnClickListener(v -> {
                if (!isAdded()) return;
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_container,
                                com.example.paktrainfoodapp.ui.main.Restaurant.order
                                        .OrderDetailFragment.newInstance(doc.getId()))
                        .addToBackStack(null)
                        .commit();
            });

            layoutRecentOrders.addView(row);

            View divider = new View(requireContext());
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density)));
            divider.setBackgroundColor(0xFFE0E0E0);
            layoutRecentOrders.addView(divider);
        }
    }

    private void navigateTo(String target) {
        if (getParentFragment() instanceof restaurant_LoadFragment) {
            ((restaurant_LoadFragment) getParentFragment()).navigateFromDashboard(target);
        }
    }

    // Module 7 - pause-status check.
    private void checkPauseStatus() {

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!isAdded() || doc == null || !doc.exists()) return;

                    Boolean isPaused = doc.getBoolean("isPaused");

                    if (isPaused != null && isPaused) {

                        String reason = doc.getString("pausedReason");

                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Account Paused")
                                .setMessage((reason != null ? reason : "Your account has been paused due to repeated order issues.")
                                        + "\n\nPlease contact support to resume taking orders.")
                                .setPositiveButton("OK", null)
                                .setCancelable(true)
                                .show();
                    }
                });
    }
}

//

