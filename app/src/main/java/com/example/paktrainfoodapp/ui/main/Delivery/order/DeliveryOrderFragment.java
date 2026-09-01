package com.example.paktrainfoodapp.ui.main.Delivery.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.paktrainfoodapp.R;
import com.google.android.material.tabs.TabLayout;

public class DeliveryOrderFragment extends Fragment {

    private TabLayout tabsOrders;
    private final com.example.paktrainfoodapp.utils.OrderTabCounter tabCounter = new com.example.paktrainfoodapp.utils.OrderTabCounter();
    private ImageView headerImage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_delivery_order, container, false);

        tabsOrders = view.findViewById(R.id.tabs_delivery_Orders);

        // Tabs
        tabsOrders.addTab(tabsOrders.newTab().setText("New"));
        tabsOrders.addTab(tabsOrders.newTab().setText("Accept"));
        tabsOrders.addTab(tabsOrders.newTab().setText("Completed"));

        // Live counts next to each tab label - see OrderTabCounter.
        String counterUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (counterUid != null) {
            tabCounter.attachStatuses(tabsOrders, 1, "Accept", "acceptedBy", counterUid, "accepted_by_rider","arrive_rider_at_resturent","dropped","pick_up");
        }

        // Default: Active
        replaceChildFragment(new Order_New_Fragment());

        tabsOrders.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Fragment selected = null;
                switch (tab.getPosition()) {
                    case 0:
                        selected = new Order_New_Fragment();
                        break;
                    case 1:
                        selected = new Order_Accept_Fragment();
                        break;
                    case 2:
                        selected = new Order_Complete_Fragment();
                        break;
                }
                replaceChildFragment(selected);
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        return view;
    }

    private void replaceChildFragment(Fragment fragment) {
        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.replace(R.id.delivery_orders_tab_container, fragment);
        ft.commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        tabCounter.detachAll();
    }
}
//



