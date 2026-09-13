package com.example.paktrainfoodapp.ui.main.Delivery.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment;
import com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment;

import java.util.Calendar;

/**
 * Rider dashboard. Previously this screen was an empty placeholder; it now
 * shows live delivery counts, earnings and the current online status.
 */
public class DeliveryHomeFragment extends Fragment {

    private TextView txtGreeting, txtName, txtStatus;
    private TextView txtTotal, txtActive, txtCompleted, txtEarnings;
    private TextView txtRiderRating, txtRiderRatingCount;
    private Switch switchOnline;

    // Set while we're pushing the OBSERVED (already-saved) value into the
    // switch, so that update doesn't itself get treated as "the user
    // tapped the toggle" and re-written straight back to the database.
    private boolean suppressToggleCallback = false;

    private DeliveryDashboardViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_delivery_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtGreeting = view.findViewById(R.id.txtRiderGreeting);
        txtName = view.findViewById(R.id.txtRiderName);
        txtStatus = view.findViewById(R.id.txtRiderStatus);
        switchOnline = view.findViewById(R.id.switchOnline);
        txtTotal = view.findViewById(R.id.txtRiderTotalDeliveries);
        txtActive = view.findViewById(R.id.txtRiderActive);
        txtCompleted = view.findViewById(R.id.txtRiderCompleted);
        txtEarnings = view.findViewById(R.id.txtRiderEarnings);
        txtRiderRating = view.findViewById(R.id.txtRiderRating);
        txtRiderRatingCount = view.findViewById(R.id.txtRiderRatingCount);

        txtGreeting.setText(greetingForTimeOfDay());

        viewModel = new ViewModelProvider(this).get(DeliveryDashboardViewModel.class);

        viewModel.getRiderName().observe(getViewLifecycleOwner(), txtName::setText);

        viewModel.getTotalDeliveries().observe(getViewLifecycleOwner(),
                v -> txtTotal.setText(String.valueOf(v)));

        viewModel.getActiveDeliveries().observe(getViewLifecycleOwner(),
                v -> txtActive.setText(String.valueOf(v)));

        viewModel.getCompletedDeliveries().observe(getViewLifecycleOwner(),
                v -> txtCompleted.setText(String.valueOf(v)));

        viewModel.getEarnings().observe(getViewLifecycleOwner(),
                v -> txtEarnings.setText("Rs " + (v == null ? 0 : (int) v.doubleValue())));

        viewModel.getOnline().observe(getViewLifecycleOwner(), isOnline -> {

            boolean on = isOnline != null && isOnline;

            txtStatus.setText(on ? "Online" : "Offline");

            txtStatus.setBackgroundResource(
                    on ? R.drawable.bg_badge_green : R.drawable.bg_badge_grey);

            txtStatus.setTextColor(on ? 0xFF2E7D32 : 0xFF616161);

            // ✅ FIX: sync the switch to the REAL saved value (not
            // hardcoded to off) - this is what actually fixes "toggle
            // resets to off every time I reopen the app". suppressToggleCallback
            // stops this sync itself from being mistaken for a user tap
            // and re-triggering a write.
            suppressToggleCallback = true;
            switchOnline.setChecked(on);
            suppressToggleCallback = false;
        });

        switchOnline.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {

            if (suppressToggleCallback) return;

            if (getParentFragment() instanceof DeliveryDashboardFragment) {
                ((DeliveryDashboardFragment) getParentFragment()).setOnlineStatus(isChecked);
            }
        });

        viewModel.start();

        // Stat cards and shortcuts open the screen they summarise
        view.findViewById(R.id.cardRiderTotal).setOnClickListener(v -> openMyDeliveries());
        view.findViewById(R.id.cardRiderActive).setOnClickListener(v -> openMyDeliveries());
        view.findViewById(R.id.cardRiderCompleted).setOnClickListener(v -> openMyDeliveries());
        view.findViewById(R.id.cardRiderEarnings).setOnClickListener(v -> openWallet());
        view.findViewById(R.id.cardRiderRating).setOnClickListener(v -> openReviews());

        loadAverageRating();

        view.findViewById(R.id.actionMyDeliveries).setOnClickListener(v -> openMyDeliveries());
        view.findViewById(R.id.actionRiderWallet).setOnClickListener(v -> openWallet());

        view.findViewById(R.id.actionNewOrders).setOnClickListener(v -> {

            if (getParentFragment() instanceof DeliveryDashboardFragment) {
                ((DeliveryDashboardFragment) getParentFragment()).openOrdersTab();
            }
        });
    }

    private String greetingForTimeOfDay() {

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private void openMyDeliveries() {

        openDetail(MyOrdersFragment.newInstance(MyOrdersFragment.ROLE_DELIVERY));
    }

    private void openWallet() {

        openDetail(WalletFragment.newInstance(WalletFragment.ROLE_DELIVERY));
    }

    /** Averages this rider's own Reviews subcollection for the dashboard's
     *  Rating card. */
    private void loadAverageRating() {

        if (txtRiderRating == null) return;

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("Users").document("Delivery")
                .collection("VerifiedRegister").document(uid)
                .collection("Reviews")
                .addSnapshotListener((snap, e) -> {

                    if (!isAdded() || txtRiderRating == null) return;

                    if (e != null || snap == null || snap.isEmpty()) {
                        txtRiderRating.setText("\u2014");
                        if (txtRiderRatingCount != null) txtRiderRatingCount.setText("Your Rating");
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

                    txtRiderRating.setText(count > 0
                            ? String.format(java.util.Locale.getDefault(), "%.1f", total / count)
                            : "\u2014");

                    if (txtRiderRatingCount != null) {
                        txtRiderRatingCount.setText(count + (count == 1 ? " rating" : " ratings"));
                    }
                });
    }

    private void openReviews() {

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null || !isAdded()) return;

        String uid = auth.getCurrentUser().getUid();
        String name = txtName != null ? txtName.getText().toString() : "Ratings & Reviews";

        openDetail(com.example.paktrainfoodapp.ui.shared.reviews.ReviewsListFragment.newInstance(
                com.example.paktrainfoodapp.ui.shared.reviews.ReviewsListFragment.ROOT_DELIVERY,
                uid, name));
    }

    private void openDetail(Fragment fragment) {

        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_loader, fragment)
                .addToBackStack(null)
                .commit();
    }
}
