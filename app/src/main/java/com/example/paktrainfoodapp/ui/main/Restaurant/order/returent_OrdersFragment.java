package com.example.paktrainfoodapp.ui.main.Restaurant.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.paktrainfoodapp.R;
import com.google.android.material.tabs.TabLayout;

public class returent_OrdersFragment extends Fragment {

    private TabLayout tabsOrders;
    private final com.example.paktrainfoodapp.utils.OrderTabCounter tabCounter = new com.example.paktrainfoodapp.utils.OrderTabCounter();
    // Track active fragment to prevent redundant reloads
    private Fragment activeFragment = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_returent__orders, container, false);

        tabsOrders = view.findViewById(R.id.tabsOrders);

        // 🔹 Create Tabs
        tabsOrders.addTab(tabsOrders.newTab().setText("Active"));
        tabsOrders.addTab(tabsOrders.newTab().setText("Accepted"));
        tabsOrders.addTab(tabsOrders.newTab().setText("Delivered"));
        tabsOrders.addTab(tabsOrders.newTab().setText("Completed"));

        // Live counts next to each tab label - see OrderTabCounter.
        String counterUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (counterUid != null) {
            tabCounter.attachStatuses(tabsOrders, 0, "Active", "restaurantId", counterUid, "Active");
            tabCounter.attachStatuses(tabsOrders, 1, "Accepted", "restaurantId", counterUid, "Accepted", "ready_for_delivery");
            tabCounter.attachStatuses(tabsOrders, 2, "Delivered", "restaurantId", counterUid, "accepted_by_rider","arrive_rider_at_resturent","dropped","pick_up");
            tabCounter.attachStatuses(tabsOrders, 4, "Cancelled", "restaurantId", counterUid, "Cancelled", "Rejected");
        }

        // 🔹 Default Load
        loadTabFragment(0);

        tabsOrders.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadTabFragment(tab.getPosition());
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        return view;
    }

    private void loadTabFragment(int position) {
        Fragment selected;
        switch (position) {
            case 1: selected = new AcceptedOrdersFragment(); break;
            case 2: selected = new DeliveredOrdersFragment(); break;
            case 3: selected = new CompletedOrdersFragment(); break;
            case 0:
            default: selected = new ActiveOrdersFragment(); break;
        }

        // ✅ FIX: Check if the same tab is being clicked again to avoid glitch
        if (activeFragment != null && activeFragment.getClass().equals(selected.getClass())) {
            return;
        }

        replaceChildFragment(selected);
        activeFragment = selected;
    }

    private void replaceChildFragment(Fragment fragment) {
        getChildFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.orders_tab_container, fragment)
                .commitAllowingStateLoss(); // Use this to avoid state loss crash
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        tabCounter.detachAll();
    }
}


//