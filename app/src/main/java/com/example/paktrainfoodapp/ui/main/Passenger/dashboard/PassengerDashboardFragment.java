package com.example.paktrainfoodapp.ui.main.Passenger.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader;
import com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment;
import com.example.paktrainfoodapp.ui.main.notification.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;

public class PassengerDashboardFragment extends Fragment {

    private TextView txtGreeting, txtUserName;
    private TextView txtTotalOrders, txtActiveOrders, txtCompletedOrders, txtWalletBalance;
    private TextView txtTopBadge;

    private PassengerDashboardViewModel viewModel;
    private NotificationRepository notificationRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_passenger_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtGreeting = view.findViewById(R.id.txtGreeting);
        txtUserName = view.findViewById(R.id.txtUserName);
        txtTotalOrders = view.findViewById(R.id.txtTotalOrders);
        txtActiveOrders = view.findViewById(R.id.txtActiveOrders);
        txtCompletedOrders = view.findViewById(R.id.txtCompletedOrders);
        txtWalletBalance = view.findViewById(R.id.txtWalletBalance);
        txtTopBadge = view.findViewById(R.id.txtTopBadge);

        txtGreeting.setText(greetingForTimeOfDay());

        loadUserName();

        viewModel = new ViewModelProvider(this).get(PassengerDashboardViewModel.class);

        viewModel.getTotalOrders().observe(getViewLifecycleOwner(),
                v -> txtTotalOrders.setText(String.valueOf(v)));

        viewModel.getActiveOrders().observe(getViewLifecycleOwner(),
                v -> txtActiveOrders.setText(String.valueOf(v)));

        viewModel.getCompletedOrders().observe(getViewLifecycleOwner(),
                v -> txtCompletedOrders.setText(String.valueOf(v)));

        viewModel.getWalletBalance().observe(getViewLifecycleOwner(),
                v -> txtWalletBalance.setText("Rs " + (v == null ? 0 : (int) v.doubleValue())));

        viewModel.start();

        setupNotificationBadge(view);

        // Every stat card opens the screen it summarises.
        view.findViewById(R.id.cardTotalOrders).setOnClickListener(v -> openMyOrders());
        view.findViewById(R.id.cardActiveOrders).setOnClickListener(v -> openMyOrders());
        view.findViewById(R.id.cardCompletedOrders).setOnClickListener(v -> openMyOrders());
        view.findViewById(R.id.cardWallet).setOnClickListener(v -> openDetail(com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.newInstance(com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.ROLE_PASSENGER)));

        view.findViewById(R.id.actionOrderFood).setOnClickListener(v -> goToOrderFood());
        view.findViewById(R.id.actionMyOrders).setOnClickListener(v -> openMyOrders());
        view.findViewById(R.id.actionProfile).setOnClickListener(v -> goToProfile());
    }

    private String greetingForTimeOfDay() {

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private void loadUserName() {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("Users")
                .document("Passenger")
                .collection("Register")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!isAdded() || !snapshot.exists()) return;

                    String name = snapshot.getString("name");

                    if (name != null && !name.isEmpty()) {
                        txtUserName.setText(name);
                    }
                });
    }

    private void setupNotificationBadge(View view) {

        view.findViewById(R.id.btnTopNotification)
                .setOnClickListener(v -> openNotifications());

        notificationRepository = new NotificationRepository();

        notificationRepository.listenUnreadCount(
                NotificationRepository.ROLE_PASSENGER,
                new NotificationRepository.BadgeCallback() {

                    @Override
                    public void onCountChanged(int count) {

                        if (!isAdded() || txtTopBadge == null) return;

                        requireActivity().runOnUiThread(() -> {

                            if (count > 0) {
                                txtTopBadge.setVisibility(View.VISIBLE);
                                txtTopBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                            } else {
                                txtTopBadge.setVisibility(View.GONE);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        // Badge is non-critical - leave it hidden on error.
                    }
                });
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    private Passenger_Fragment_Loader loader() {

        Fragment parent = getParentFragment();

        return parent instanceof Passenger_Fragment_Loader
                ? (Passenger_Fragment_Loader) parent
                : null;
    }

    private void openDetail(Fragment fragment) {

        Passenger_Fragment_Loader loader = loader();

        if (loader != null) loader.openProfileDetail(fragment);
    }

    private void openMyOrders() {
        openDetail(new MyOrdersFragment());
    }

    private void openNotifications() {

        Passenger_Fragment_Loader loader = loader();

        if (loader != null) loader.showNotifications();
    }

    private void goToOrderFood() {

        Passenger_Fragment_Loader loader = loader();

        if (loader != null) loader.showJourneyScreen();
    }

    private void goToProfile() {

        Passenger_Fragment_Loader loader = loader();

        if (loader != null) loader.showProfileScreen();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (notificationRepository != null) {
            notificationRepository.removeListener();
        }
    }
}
