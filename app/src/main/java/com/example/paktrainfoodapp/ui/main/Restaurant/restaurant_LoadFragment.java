package com.example.paktrainfoodapp.ui.main.Restaurant;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Restaurant.dashboard.resturent_DashboardFragment;
import com.example.paktrainfoodapp.ui.main.Restaurant.menu.resturent_MenuFragment;
import com.example.paktrainfoodapp.ui.main.Restaurant.order.returent_OrdersFragment;
import com.example.paktrainfoodapp.ui.main.Restaurant.profile.resturent_ProfileFragment;

//
public class restaurant_LoadFragment extends Fragment {

    private LinearLayout btnMenu, btnOrder, btnDashboard, btnDelivery, btnProfile;
    private ImageView iconMenu, iconOrder, iconDashboard, iconDelivery, iconProfile;
    private TextView textMenu, textOrder, textDashboard, textDelivery, textProfile;

    private String currentTag = "dashboard";

    private TextView txtRestaurantBadge;
    private com.example.paktrainfoodapp.ui.main.notification.NotificationRepository
            notificationRepository;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_restaurant__load, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind Views
        btnMenu = view.findViewById(R.id.btn_menu);
        btnOrder = view.findViewById(R.id.btn_order);
        btnDashboard = view.findViewById(R.id.btn_dashboard);
        btnDelivery = view.findViewById(R.id.btn_delivery);
        txtRestaurantBadge = view.findViewById(R.id.txtRestaurantBadge);
        startNotificationBadge();
        btnProfile = view.findViewById(R.id.btn_profile);

        iconMenu = view.findViewById(R.id.icon_menu);
        iconOrder = view.findViewById(R.id.icon_order);
        iconDashboard = view.findViewById(R.id.icon_dashboard);
        iconDelivery = view.findViewById(R.id.icon_delivery);
        iconProfile = view.findViewById(R.id.icon_profile);

        textMenu = view.findViewById(R.id.text_menu);
        textOrder = view.findViewById(R.id.text_order);
        textDashboard = view.findViewById(R.id.text_dashboard);
        textDelivery = view.findViewById(R.id.text_delivery);
        textProfile = view.findViewById(R.id.text_profile);

        // Default fragment open (Dashboard) - first screen, not added to back stack
        openFragment(new resturent_DashboardFragment(), "dashboard", false);
        highlightButton(btnDashboard, iconDashboard, textDashboard);

        // Click listeners
        btnMenu.setOnClickListener(v -> {
            openFragment(new resturent_MenuFragment(), "menu", true);
            highlightButton(btnMenu, iconMenu, textMenu);
        });

        btnOrder.setOnClickListener(v -> {
            openFragment(new returent_OrdersFragment(), "order", true);
            highlightButton(btnOrder, iconOrder, textOrder);
        });

        btnDashboard.setOnClickListener(v -> {
            openFragment(new resturent_DashboardFragment(), "dashboard", true);
            highlightButton(btnDashboard, iconDashboard, textDashboard);
        });

        // This slot now shows the restaurant's notifications; the shared
        // NotificationFragment is reused with the RESTAURANT role.
        btnDelivery.setOnClickListener(v -> {
            openFragment(
                    com.example.paktrainfoodapp.ui.main.notification.NotificationFragment
                            .newInstance(com.example.paktrainfoodapp.ui.main.notification
                                    .NotificationRepository.ROLE_RESTAURANT),
                    "notifications", true);
            highlightButton(btnDelivery, iconDelivery, textDelivery);
        });

        btnProfile.setOnClickListener(v -> {
            openFragment(new resturent_ProfileFragment(), "profile", true);
            highlightButton(btnProfile, iconProfile, textProfile);
        });
    }

    /**
     * @param tag             identifies which tab this is, used to avoid pushing
     *                        a duplicate back-stack entry when the same tab is tapped again
     * @param addToBackStack  false only for the very first screen shown
     */
    private void openFragment(Fragment fragment, String tag, boolean addToBackStack) {

        if (tag.equals(currentTag) && addToBackStack) {
            // Already on this tab - avoid stacking a duplicate entry
            return;
        }

        currentTag = tag;

        FragmentTransaction transaction = getChildFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_holder, fragment);

        if (addToBackStack) {
            transaction.addToBackStack(tag);
        }

        transaction.commit();
    }

    /**
     * Called from the host Activity's back-press handler.
     * Returns true if it consumed the back press (popped an inner screen),
     * false if there's nothing left to pop and the Activity should handle it.
     */
    public boolean handleBackPressed() {

        FragmentManager fm = getChildFragmentManager();

        if (fm.getBackStackEntryCount() > 0) {

            fm.popBackStack();
            fm.executePendingTransactions();

            // Re-sync currentTag + bottom nav highlight with whatever is now showing
            java.util.List<Fragment> fragments = fm.getFragments();

            for (Fragment f : fragments) {

                if (f != null && f.isAdded()) {

                    if (f instanceof resturent_DashboardFragment) {
                        currentTag = "dashboard";
                        highlightButton(btnDashboard, iconDashboard, textDashboard);
                    } else if (f instanceof resturent_MenuFragment) {
                        currentTag = "menu";
                        highlightButton(btnMenu, iconMenu, textMenu);
                    } else if (f instanceof returent_OrdersFragment) {
                        currentTag = "order";
                        highlightButton(btnOrder, iconOrder, textOrder);
                    } else if (f instanceof com.example.paktrainfoodapp.ui.main.notification.NotificationFragment) {
                        currentTag = "notifications";
                        highlightButton(btnDelivery, iconDelivery, textDelivery);
                    } else if (f instanceof resturent_ProfileFragment) {
                        currentTag = "profile";
                        highlightButton(btnProfile, iconProfile, textProfile);
                    }
                }
            }

            return true;
        }

        return false;
    }

    private void highlightButton(LinearLayout selectedLayout, ImageView selectedIcon, TextView selectedText) {
        // Reset all
        resetButtons();

        // Highlight selected
        selectedLayout.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start();
        selectedLayout.setBackgroundResource(R.drawable.selected_circle_bg);
        selectedIcon.setColorFilter(getResources().getColor(R.color.green));
        selectedText.setTextColor(getResources().getColor(R.color.green));
        selectedText.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    private void resetButtons() {
        LinearLayout[] layouts = {btnMenu, btnOrder, btnDashboard, btnDelivery, btnProfile};
        ImageView[] icons = {iconMenu, iconOrder, iconDashboard, iconDelivery, iconProfile};
        TextView[] texts = {textMenu, textOrder, textDashboard, textDelivery, textProfile};

        for (LinearLayout layout : layouts) {
            layout.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            layout.setBackgroundResource(android.R.color.transparent);
        }

        for (ImageView icon : icons) {
            icon.setColorFilter(getResources().getColor(R.color.gray));
        }

        for (TextView text : texts) {
            text.setTextColor(getResources().getColor(R.color.gray));
            text.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }
    // Eh method restaurant_LoadFragment class de vich rakho
    public void navigateFromDashboard(String target) {
        switch (target) {
            case "menu":
                btnMenu.performClick(); // btnMenu da click trigger karo
                break;
            case "delivery":
                btnDelivery.performClick();
                break;
            case "profile":
                btnProfile.performClick();
                break;
            case "order":
                btnOrder.performClick();
                break;
        }
    }

    /** Keeps the unread count on the notifications tab up to date. */
    private void startNotificationBadge() {

        notificationRepository =
                new com.example.paktrainfoodapp.ui.main.notification.NotificationRepository();

        notificationRepository.listenUnreadCount(
                com.example.paktrainfoodapp.ui.main.notification
                        .NotificationRepository.ROLE_RESTAURANT,
                new com.example.paktrainfoodapp.ui.main.notification
                        .NotificationRepository.BadgeCallback() {

                    @Override
                    public void onCountChanged(int count) {

                        if (!isAdded() || txtRestaurantBadge == null) return;

                        requireActivity().runOnUiThread(() -> {

                            if (count > 0) {
                                txtRestaurantBadge.setVisibility(View.VISIBLE);
                                txtRestaurantBadge.setText(
                                        count > 99 ? "99+" : String.valueOf(count));
                            } else {
                                txtRestaurantBadge.setVisibility(View.GONE);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Exception e) { }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (notificationRepository != null) {
            notificationRepository.removeListener();
        }
    }
}
