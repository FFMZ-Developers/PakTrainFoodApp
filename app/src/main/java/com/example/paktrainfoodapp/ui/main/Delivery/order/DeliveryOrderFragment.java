package com.example.paktrainfoodapp.ui.main.Delivery.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;
import com.google.android.material.tabs.TabLayout;

public class DeliveryOrderFragment extends Fragment {

    private TabLayout tabsOrders;
    private final com.example.paktrainfoodapp.utils.OrderTabCounter tabCounter = new com.example.paktrainfoodapp.utils.OrderTabCounter();
    private ImageView headerImage;

    // Track the active tab fragment so re-tapping the same tab doesn't
    // reload it - same guard used in Restaurant's returent_OrdersFragment.
    private Fragment activeFragment = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_delivery_order, container, false);

        tabsOrders = view.findViewById(R.id.tabs_delivery_Orders);
        headerImage = view.findViewById(R.id.headerImage);

        // Tabs
        tabsOrders.addTab(tabsOrders.newTab().setText("New"));
        tabsOrders.addTab(tabsOrders.newTab().setText("Accept"));
        tabsOrders.addTab(tabsOrders.newTab().setText("Completed"));

        // Live counts next to each tab label - see OrderTabCounter.
        // "Completed" deliberately gets no count (that list only grows -
        // same convention Restaurant/Passenger's order tabs follow).
        String counterUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (counterUid != null) {
            tabCounter.attachStatuses(tabsOrders, 1, "Accept", "acceptedBy", counterUid, "accepted_by_rider","arrive_rider_at_resturent","dropped","pick_up");
        }

        // Default: New
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
            case 1:
                selected = new Order_Accept_Fragment();
                break;
            case 2:
                selected = new Order_Complete_Fragment();
                break;
            case 0:
            default:
                selected = new Order_New_Fragment();
                break;
        }

        if (activeFragment != null && activeFragment.getClass().equals(selected.getClass())) {
            return;
        }

        replaceChildFragment(selected);
        activeFragment = selected;
    }

    private void replaceChildFragment(Fragment fragment) {

        if (!isAdded() || getActivity() == null) return;

        try {
            getChildFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.delivery_orders_tab_container, fragment)
                    .commitAllowingStateLoss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        tabCounter.detachAll();
    }
}
//



