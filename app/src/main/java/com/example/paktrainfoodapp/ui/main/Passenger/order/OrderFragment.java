package com.example.paktrainfoodapp.ui.main.Passenger.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.widget.Toolbar;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.utils.Refreshable;
import com.example.paktrainfoodapp.utils.RefreshHelper;
import com.google.android.material.tabs.TabLayout;

public class OrderFragment extends Fragment implements Refreshable {

    private TabLayout tabsOrders;
    private final com.example.paktrainfoodapp.utils.OrderTabCounter tabCounter = new com.example.paktrainfoodapp.utils.OrderTabCounter();
    private ImageView headerImage;
    private Toolbar toolbarOrders;
    private ProgressBar progressRefresh;
    private int selectedTab = 0;
    private String selectedOrderId;
    private String pendingOrderId = null;


    public OrderFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_passanger_order, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        tabsOrders = view.findViewById(R.id.tabsOrders);
        headerImage = view.findViewById(R.id.headerImage);
        progressRefresh = view.findViewById(R.id.progressRefresh);
        toolbarOrders = view.findViewById(R.id.toolbarOrders);

        RefreshHelper.setupRefresh(toolbarOrders, this, this);

        if (tabsOrders.getTabCount() == 0) {
            tabsOrders.addTab(tabsOrders.newTab().setText("Active"));
            tabsOrders.addTab(tabsOrders.newTab().setText("Accepted"));
            tabsOrders.addTab(tabsOrders.newTab().setText("Delivered"));
            tabsOrders.addTab(tabsOrders.newTab().setText("Completed"));

        // Live counts next to each tab label - see OrderTabCounter.
        String counterUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (counterUid != null) {

            tabCounter.attachStatuses(tabsOrders, 0, "Active", "passengerUid", counterUid, "Active");
            tabCounter.attachStatuses(tabsOrders, 1, "Accepted", "passengerUid", counterUid, "Accepted","ready_for_delivery");
            // Must mirror DeliveredOrdersFragment's own filter exactly - it does
            // NOT include pick_up (that sits in the Completed tab), so counting
            // it here made the badge show orders the list wouldn't display.
            tabCounter.attachStatuses(tabsOrders, 2, "Delivered", "passengerUid", counterUid, "accepted_by_rider","arrive_rider_at_resturent","dropped","pick_up");
        }
        }

        if (savedInstanceState == null) {
            if (savedInstanceState == null) {

                TabLayout.Tab tab = tabsOrders.getTabAt(selectedTab);

                if (tab != null) {
                    tab.select();
                }

                replaceChildFragment(getFragmentByTab(selectedTab));
            }
        }

        tabsOrders.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                replaceChildFragment(getFragmentByTab(tab.getPosition()));
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    // ================= TAB FRAGMENTS =================
    private Fragment getFragmentByTab(int position) {

        switch (position) {
            case 0:
                return new ActiveOrdersFragment();
            case 1:
                return new AcceptedOrdersFragment();
            case 2:
                return new DeliveredOrdersFragment();
            case 3:
                return new CompletedOrdersFragment();
            default:
                return new ActiveOrdersFragment();
        }
    }

    // ================= FRAGMENT REPLACE =================
    private void replaceChildFragment(Fragment fragment) {

        if (!isAdded() || getActivity() == null) return;

        try {
            getChildFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.orders_tab_container, fragment)
                    .commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //  REFRESH
    @Override
    public void refreshData() {

        Fragment current = getChildFragmentManager()
                .findFragmentById(R.id.orders_tab_container);

        if (current instanceof Refreshable) {

            // show loading in parent (SAFE)
            if (progressRefresh != null) {
                progressRefresh.setVisibility(View.VISIBLE);
            }

            // refresh child data
            ((Refreshable) current).refreshData();

            // hide loader after short delay
            tabsOrders.postDelayed(() -> {

                if (progressRefresh != null) {
                    progressRefresh.setVisibility(View.GONE);
                }

            }, 800);
        }
    }

    public void setSelectedTab(int tab) {
        selectedTab = tab;
    }
    public void setSelectedOrder(String orderId) {

        selectedOrderId = orderId;

    }
    public String getSelectedOrderId() {

        return selectedOrderId;

    }
    public void setPendingOrderId(String orderId) {

        pendingOrderId = orderId;

    }
    public void clearPendingOrderId() {

        pendingOrderId = null;

    }
    public String getPendingOrderId() {

        return pendingOrderId;

    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        tabCounter.detachAll();
    }
}



//

