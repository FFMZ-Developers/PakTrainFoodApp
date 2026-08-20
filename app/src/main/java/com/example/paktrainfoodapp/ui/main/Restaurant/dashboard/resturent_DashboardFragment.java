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

    private LinearLayout btnProfile, btnMenu, btnDelivery;
    private TextView tvOrdersCount, tvRevenue, tvMenuCount, tvRating, tvDashboardTitle;

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

        // Click listeners calling the parent's navigation method
        tvOrdersCount = view.findViewById(R.id.tvOrdersCount);
        tvRevenue = view.findViewById(R.id.tvRevenue);
        tvMenuCount = view.findViewById(R.id.tvMenuCount);
        tvRating = view.findViewById(R.id.tvRating);
        tvDashboardTitle = view.findViewById(R.id.tvDashboardTitle);

        btnMenu.setOnClickListener(v -> navigateTo("menu"));
        btnDelivery.setOnClickListener(v -> navigateTo("delivery"));
        btnProfile.setOnClickListener(v -> navigateTo("profile"));

        // Stat cards open the screen they summarise
        if (tvOrdersCount != null) {
            ((View) tvOrdersCount.getParent()).setOnClickListener(v -> navigateTo("order"));
        }

        if (tvMenuCount != null) {
            ((View) tvMenuCount.getParent()).setOnClickListener(v -> navigateTo("menu"));
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

        // Ratings aren't collected anywhere yet, so show a dash rather than a
        // fake 4.8 that would mislead during a demo.
        if (tvRating != null) tvRating.setText("—");

        viewModel.start();
    }

    private void navigateTo(String target) {
        if (getParentFragment() instanceof restaurant_LoadFragment) {
            ((restaurant_LoadFragment) getParentFragment()).navigateFromDashboard(target);
        }
    }
}

//

