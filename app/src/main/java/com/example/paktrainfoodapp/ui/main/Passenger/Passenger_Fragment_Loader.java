package com.example.paktrainfoodapp.ui.main.Passenger;

import android.graphics.Typeface;
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
import com.example.paktrainfoodapp.ui.main.Passenger.home.CartFragment;
import com.example.paktrainfoodapp.ui.main.Passenger.home.HomeFragment;
import com.example.paktrainfoodapp.ui.main.Passenger.home.Passanger_ItemDetailsFragment;
import com.example.paktrainfoodapp.ui.main.Passenger.home.Passanger_Resturent_list_Fragment;
import com.example.paktrainfoodapp.ui.main.Passenger.home.Resturent_Menu_Fragment;
import com.example.paktrainfoodapp.ui.main.Passenger.order.OrderFragment;
import com.example.paktrainfoodapp.ui.main.Passenger.order.passanger_orderDetailFragment;
import com.example.paktrainfoodapp.ui.main.Passenger.profile.CommonIssues;
import com.example.paktrainfoodapp.ui.main.Passenger.profile.LiveChatFragment;
import com.example.paktrainfoodapp.ui.main.Passenger.profile.ProfileFragment;
import com.example.paktrainfoodapp.ui.main.Passenger.profile.StaticPage;
import com.example.paktrainfoodapp.ui.main.Passenger.profile.passenger_helpandsupport;
import com.example.paktrainfoodapp.ui.main.notification.NotificationFragment;
import com.example.paktrainfoodapp.ui.main.notification.NotificationRepository;

public class Passenger_Fragment_Loader extends Fragment {

    private LinearLayout btnJourney, btnOrder, btnDashboard, btnCart, btnProfile;

    // Main Bottom Navigation Fragments.
    // NOTE: not final - after the app process is recreated (e.g. Android killed it
    // in the background) these get re-linked to the fragments the FragmentManager
    // already restored, instead of silently pointing at fresh, never-added
    // instances. That mismatch was the cause of the occasional blank/white screen.
    private HomeFragment homeFragment;
    private NotificationFragment notificationFragment;
    private com.example.paktrainfoodapp.ui.main.Passenger.dashboard.PassengerDashboardFragment dashboardFragment;
    private OrderFragment orderFragment;
    private CartFragment cartFragment;
    private ProfileFragment profileFragment;
    private passenger_helpandsupport helpSupportFragment;
    private TextView txtNotificationBadge;

    private NotificationRepository notificationRepository;

    private Fragment activeFragment;

    // 🔥 Last opened dashboard screen save hoga
    // Example:
    // Restaurant List
    // Station Menu
    // Item Detail
    private Fragment lastDashboardFragment;
    private boolean openOrderAfterLoad = false;
    public void requestOpenOrders() {

        openOrderAfterLoad = true;

    }

    public Passenger_Fragment_Loader() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_passenger_load_fragment,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        btnJourney = view.findViewById(R.id.btnJourney);
        // The badge moved from the bottom nav onto each screen's own top bar,
        // so there is no view to bind here any more.
        txtNotificationBadge = null;

        notificationRepository = new NotificationRepository();
        btnOrder = view.findViewById(R.id.btn_order);
        btnDashboard = view.findViewById(R.id.btn_dashboard);
        btnCart = view.findViewById(R.id.btn_cart);
        btnProfile = view.findViewById(R.id.btn_profile);

        // ================= INITIAL FRAGMENTS =================

        FragmentManager childFm = getChildFragmentManager();

        if (savedInstanceState == null) {

            homeFragment = new HomeFragment();
            notificationFragment = new NotificationFragment();
            dashboardFragment = new com.example.paktrainfoodapp.ui.main.Passenger.dashboard.PassengerDashboardFragment();
            orderFragment = new OrderFragment();
            cartFragment = new CartFragment();
            profileFragment = new ProfileFragment();
            helpSupportFragment = new passenger_helpandsupport();

            FragmentTransaction ft = childFm.beginTransaction();

            ft.add(R.id.fragment_holder, homeFragment, "HOME");

            ft.add(R.id.fragment_holder, notificationFragment, "NOTIFICATION")
                    .hide(notificationFragment);

            ft.add(R.id.fragment_holder, dashboardFragment, "DASHBOARD")
                    .hide(dashboardFragment);

            ft.add(R.id.fragment_holder, orderFragment, "ORDER")
                    .hide(orderFragment);

            ft.add(R.id.fragment_holder, cartFragment, "CART")
                    .hide(cartFragment);

            ft.add(R.id.fragment_holder, profileFragment, "PROFILE")
                    .hide(profileFragment);

            ft.add(R.id.fragment_holder,
                            helpSupportFragment,
                            "HELP_SUPPORT")
                    .hide(helpSupportFragment);

            ft.commit();

            activeFragment = homeFragment;

            // 🔥 Initial dashboard screen
            lastDashboardFragment = homeFragment;

        } else {

            // 🔥 FIX: process was recreated - re-link to the fragments the
            // FragmentManager already restored instead of creating orphans.
            homeFragment = (HomeFragment) childFm.findFragmentByTag("HOME");
            notificationFragment = (NotificationFragment) childFm.findFragmentByTag("NOTIFICATION");
            dashboardFragment = (com.example.paktrainfoodapp.ui.main.Passenger.dashboard.PassengerDashboardFragment)
                    childFm.findFragmentByTag("DASHBOARD");
            orderFragment = (OrderFragment) childFm.findFragmentByTag("ORDER");
            cartFragment = (CartFragment) childFm.findFragmentByTag("CART");
            profileFragment = (ProfileFragment) childFm.findFragmentByTag("PROFILE");
            helpSupportFragment = (passenger_helpandsupport) childFm.findFragmentByTag("HELP_SUPPORT");

            if (homeFragment == null) {
                // Extremely defensive fallback - should not normally happen,
                // but guarantees we never crash or show a permanently blank screen.
                homeFragment = new HomeFragment();
            }

            Fragment visible = getVisibleFragment();

            activeFragment = (visible != null) ? visible : homeFragment;
            lastDashboardFragment = homeFragment;
        }

        // ✅ FIX: this used to unconditionally reset the highlighted tab to
        // Dashboard every time this view was created - including when it was
        // RE-created after popping back from a detail/tracking screen (those
        // replace MainActivity's container, so returning rebuilds this whole
        // shell). The result was that backing out of an order always looked
        // like it had thrown you back to the Dashboard. Now the highlight
        // follows whichever tab is actually showing.
        selectNavButton(navButtonFor(activeFragment));

        // ================= DASHBOARD =================

        btnDashboard.setOnClickListener(v -> {

            selectNavButton(btnDashboard);

            showFragment(dashboardFragment);
        });

        // ================= ORDER FOOD (journey) =================

        btnJourney.setOnClickListener(v -> {

            selectNavButton(btnJourney);

            showFragment(homeFragment);

            lastDashboardFragment = homeFragment;

        });

        // ================= ORDER =================

        btnOrder.setOnClickListener(v -> {

            selectNavButton(btnOrder);

            showFragment(orderFragment);
        });

        // ================= CART =================

        btnCart.setOnClickListener(v -> {

            selectNavButton(btnCart);

            showFragment(cartFragment);
        });

        // ================= PROFILE =================

        btnProfile.setOnClickListener(v -> {

            selectNavButton(btnProfile);

            showFragment(profileFragment);
        });

        if (openOrderAfterLoad) {

            openOrderAfterLoad = false;

            view.post(() -> {

                navigateToOrders();

            });

        }
        startBadgeListener();
        startNotificationBadge();

    }
    public void navigateToOrders() {

        if (!isAdded()) {

            openOrderAfterLoad = true;
            return;

        }

        showFragment(orderFragment);

        selectNavButton(btnOrder);

    }
    /**
     * Module: opens the journey/home screen (from -> to -> meal station,
     * i.e. where a passenger picks a restaurant). Used when an order is
     * rejected - that order is finished, so sending them back to the
     * orders list would just show a dead order; what they actually need is
     * to start choosing again.
     */
    public void navigateToHome() {

        if (!isAdded()) return;

        showFragment(homeFragment);

        selectNavButton(btnJourney);
    }

    public void navigateToOrders(int tabIndex) {

        orderFragment.setSelectedTab(tabIndex);

        navigateToOrders();

    }
    public void navigateToOrders(int tabIndex, String orderId) {

        orderFragment.setSelectedTab(tabIndex);

        orderFragment.setPendingOrderId(orderId);

        navigateToOrders();

    }
    public void openOrderDetail(String orderId) {

        passanger_orderDetailFragment fragment =
                new passanger_orderDetailFragment();

        Bundle bundle = new Bundle();
        bundle.putString("orderId", orderId);

        fragment.setArguments(bundle);

        openRestaurantMenu(fragment);

    }
    private void startBadgeListener() {

        notificationRepository.listenUnreadCount(
                "PASSENGER",
                new NotificationRepository.BadgeCallback() {

                    @Override
                    public void onCountChanged(int count) {

                        if (getActivity() == null)
                            return;

                        getActivity().runOnUiThread(() -> {

                            if (count <= 0) {

                                if (txtNotificationBadge != null) txtNotificationBadge.setVisibility(View.GONE);

                            } else {

                                if (txtNotificationBadge != null) txtNotificationBadge.setVisibility(View.VISIBLE);

                                if (count > 99) {

                                    if (txtNotificationBadge != null) txtNotificationBadge.setText("99+");

                                } else {

                                    if (txtNotificationBadge != null) txtNotificationBadge.setText(
                                            String.valueOf(count));

                                }

                            }

                        });

                    }

                    @Override
                    public void onFailure(Exception e) {

                    }

                });

    }
    private void startNotificationBadge() {

        notificationRepository.listenUnreadCount(

                NotificationRepository.ROLE_PASSENGER,

                new NotificationRepository.BadgeCallback() {

                    @Override
                    public void onCountChanged(int count) {

                        if (!isAdded())
                            return;

                        requireActivity().runOnUiThread(() -> {

                            if (count <= 0) {

                                if (txtNotificationBadge != null) txtNotificationBadge.setVisibility(View.GONE);

                            } else {

                                if (txtNotificationBadge != null) txtNotificationBadge.setVisibility(View.VISIBLE);

                                if (count > 99) {

                                    if (txtNotificationBadge != null) txtNotificationBadge.setText("99+");

                                } else {

                                    if (txtNotificationBadge != null) txtNotificationBadge.setText(
                                            String.valueOf(count));

                                }

                            }

                        });

                    }

                    @Override
                    public void onFailure(Exception e) {

                    }

                });

    }
    // =========================================================
    // SHOW FRAGMENT
    // =========================================================

    public void showFragment(Fragment fragment) {

        openScreen(fragment, false);

        saveDashboardFragment(fragment);

    }

//    public void openSubFragment(Fragment fragment) {
//
//        FragmentTransaction transaction =
//                getChildFragmentManager().beginTransaction();
//
//        transaction.hide(activeFragment);
//
//        if (!fragment.isAdded()) {
//
//            transaction.add(R.id.fragment_holder, fragment);
//
//        } else {
//
//            transaction.show(fragment);
//
//        }
//
//        transaction.addToBackStack(null);
//
//        transaction.commit();
//
//        activeFragment = fragment;
//
//        updateBottomNav(fragment);
//
//    }

    public void openSubFragment(Fragment fragment) {
        if (fragment == activeFragment) {
            return;
        }

        FragmentManager manager = getChildFragmentManager();

        FragmentTransaction transaction =
                manager.beginTransaction();

        if (activeFragment != null) {

            transaction.hide(activeFragment);

        }

        if (!fragment.isAdded()) {

            transaction.add(
                    R.id.fragment_holder,
                    fragment
            );

        } else {

            transaction.show(fragment);

        }

        transaction.addToBackStack(
                activeFragment == null
                        ? ""
                        : activeFragment.getClass().getName()
        );

        transaction.commit();

        activeFragment = fragment;

        updateBottomNav(fragment);

    }

    private void saveDashboardFragment(Fragment fragment) {

        if (fragment instanceof HomeFragment
                || fragment instanceof Passanger_Resturent_list_Fragment
                || fragment instanceof Resturent_Menu_Fragment
                || fragment instanceof Passanger_ItemDetailsFragment) {

            lastDashboardFragment = fragment;

        }

    }

    private void openScreen(
            Fragment fragment,
            boolean addToBackStack) {
        if (fragment == activeFragment) {
            return;
        }
        if (!isAdded()) {
            return;
        }

        FragmentManager manager = getChildFragmentManager();

        if (manager.isStateSaved()) {
            return;
        }
        FragmentTransaction transaction =
                manager.beginTransaction();

        // Hide all visible fragments
        for (Fragment frag : manager.getFragments()) {

            if (frag != null &&
                    frag.isAdded() &&
                    frag.isVisible()) {

                transaction.hide(frag);

            }

        }

        if (!fragment.isAdded()) {

            transaction.add(
                    R.id.fragment_holder,
                    fragment
            );

        } else {

            transaction.show(fragment);

        }

        if (addToBackStack) {

            transaction.addToBackStack(null);

        }

        transaction.commit();

        activeFragment = fragment;

        updateBottomNav(fragment);

    }
    // =========================================================
    // RESTAURANT LIST
    // =========================================================

    public void openRestaurantList(Fragment fragment) {

        openDetailFragment(fragment);

        saveDashboardFragment(fragment);

    }

    // =========================================================
    // RESTAURANT MENU / ITEM DETAILS
    // =========================================================

    public void openRestaurantMenu(Fragment fragment) {

        openDetailFragment(fragment);

        saveDashboardFragment(fragment);

    }

    // =========================================================
    // GENERIC DETAIL SCREEN (e.g. Personal Info from Profile)
    // =========================================================

    public void openProfileDetail(Fragment fragment) {

        openDetailFragment(fragment);

    }

    // =========================================================
    // NAV HELPERS - used by the dashboard's shortcuts so it can
    // switch bottom-nav tabs instead of stacking detail screens.
    // =========================================================

    /** Opens the notifications list (its bottom-nav slot was removed). */
    public void showNotifications() {

        showFragment(notificationFragment);
    }

    /** Jumps to the journey / from-to selection screen. */
    public void showJourneyScreen() {

        selectNavButton(btnJourney);

        showFragment(homeFragment);

        lastDashboardFragment = homeFragment;
    }

    public void showProfileScreen() {

        selectNavButton(btnProfile);

        showFragment(profileFragment);
    }
    // =========================================================
    // TEMP FRAGMENT
    // =========================================================


// =========================================================
// UPDATE BOTTOM NAV BY CURRENT FRAGMENT
// =========================================================

    public void updateBottomNav(Fragment fragment) {

        // Journey slot: the from/to screen and everything reached while
        // ordering food from it.
        if (fragment instanceof HomeFragment
                || fragment instanceof Passanger_Resturent_list_Fragment
                || fragment instanceof Resturent_Menu_Fragment
                || fragment instanceof Passanger_ItemDetailsFragment) {

            selectNavButton(btnJourney);

        } else if (fragment instanceof com.example.paktrainfoodapp.ui.main
                .Passenger.dashboard.PassengerDashboardFragment) {

            selectNavButton(btnDashboard);

        } else if (fragment instanceof OrderFragment) {

            selectNavButton(btnOrder);

        } else if (fragment instanceof CartFragment) {

            selectNavButton(btnCart);

        } else if (fragment instanceof ProfileFragment
                || fragment instanceof passenger_helpandsupport
                || fragment instanceof CommonIssues
                || fragment instanceof StaticPage
                || fragment instanceof LiveChatFragment
                // Screens opened from the profile list keep Profile highlighted
                || fragment instanceof com.example.paktrainfoodapp.ui.shared.profile.PersonalInfoFragment
                || fragment instanceof com.example.paktrainfoodapp.ui.shared.profile.SettingsFragment
                || fragment instanceof com.example.paktrainfoodapp.ui.shared.profile.AddressFragment
                || fragment instanceof com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment
                || fragment instanceof com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment) {

            selectNavButton(btnProfile);

        } else if (fragment instanceof NotificationFragment) {

            // Notifications no longer own a nav slot, so leave the current
            // highlight alone rather than lighting up an unrelated tab.
            return;
        }
    }
    // =========================================================
    // BOTTOM NAVIGATION COLORS
    // =========================================================

    private void selectNavButton(LinearLayout selectedBtn) {

        LinearLayout[] buttons = {
                btnJourney,
                btnOrder,
                btnDashboard,
                btnCart,
                btnProfile
        };

        // Reset all buttons
        for (LinearLayout btn : buttons) {

            if (btn == null) continue;

            ImageView icon = null;
            TextView text = null;

            if (btn.getId() == R.id.btnJourney) {

                icon = btn.findViewById(R.id.icon_journey);
                text = btn.findViewById(R.id.text_journey);

            } else if (btn.getId() == R.id.btn_order) {

                icon = btn.findViewById(R.id.icon_order);
                text = btn.findViewById(R.id.text_order);

            } else if (btn.getId() == R.id.btn_dashboard) {

                icon = btn.findViewById(R.id.icon_dashboard);
                text = btn.findViewById(R.id.text_dashboard);

            } else if (btn.getId() == R.id.btn_cart) {

                icon = btn.findViewById(R.id.icon_cart);
                text = btn.findViewById(R.id.text_cart);

            } else if (btn.getId() == R.id.btn_profile) {

                icon = btn.findViewById(R.id.icon_profile);
                text = btn.findViewById(R.id.text_profile);
            }

            if (icon != null) {
                icon.setColorFilter(getResources().getColor(R.color.gray));
            }

            if (text != null) {
                text.setTextColor(getResources().getColor(R.color.gray));
                text.setTypeface(null, Typeface.NORMAL);
            }
        }

        if (selectedBtn == null) return;

        ImageView selectedIcon = null;
        TextView selectedText = null;

        if (selectedBtn.getId() == R.id.btnJourney) {

            selectedIcon = selectedBtn.findViewById(R.id.icon_journey);
            selectedText = selectedBtn.findViewById(R.id.text_journey);

        } else if (selectedBtn.getId() == R.id.btn_order) {

            selectedIcon = selectedBtn.findViewById(R.id.icon_order);
            selectedText = selectedBtn.findViewById(R.id.text_order);

        } else if (selectedBtn.getId() == R.id.btn_dashboard) {

            selectedIcon = selectedBtn.findViewById(R.id.icon_dashboard);
            selectedText = selectedBtn.findViewById(R.id.text_dashboard);

        } else if (selectedBtn.getId() == R.id.btn_cart) {

            selectedIcon = selectedBtn.findViewById(R.id.icon_cart);
            selectedText = selectedBtn.findViewById(R.id.text_cart);

        } else if (selectedBtn.getId() == R.id.btn_profile) {

            selectedIcon = selectedBtn.findViewById(R.id.icon_profile);
            selectedText = selectedBtn.findViewById(R.id.text_profile);
        }

        if (selectedIcon != null) {
            selectedIcon.setColorFilter(getResources().getColor(R.color.green));
        }

        if (selectedText != null) {
            selectedText.setTextColor(getResources().getColor(R.color.green));
            selectedText.setTypeface(null, Typeface.BOLD);
        }
    }
    // =========================================================
    // GETTERS
    // =========================================================

    public Fragment getActiveFragment() {
        return activeFragment;
    }

    public Fragment getHomeFragment() {
        return homeFragment;
    }
    public void openHelpSupport() {

        if (helpSupportFragment == null) {
            helpSupportFragment = new passenger_helpandsupport();
        }
        openSubFragment(helpSupportFragment);

    }
    public void openCommonFragment(Fragment fragment) {

        openDetailFragment(fragment);

    }

    public void openProfileScreen(Fragment fragment) {

        openDetailFragment(fragment);

    }

//    public boolean handleBackPressed() {
//
//        FragmentManager fm = getChildFragmentManager();
//
//        // Agar back stack mein screens hain
//        if (fm.getBackStackEntryCount() > 0) {
//
//            fm.popBackStack();
//
//            fm.executePendingTransactions();
//            // Hidden fragment find karo
//            Fragment previous = null;
//
//            for (Fragment fragment : fm.getFragments()) {
//
//                if (fragment != null
//                        && fragment.isAdded()
//                        && fragment.isHidden()) {
//
//                    previous = fragment;
//
//                }
//
//            }
//            // Visible fragment find karo
//            Fragment current = null;
//
//            for (Fragment fragment : fm.getFragments()) {
//
//                if (fragment != null
//                        && fragment.isAdded()
//                        && fragment.isVisible()) {
//
//                    current = fragment;
//                    break;
//
//                }
//            }
//
//            if (current != null) {
//
//                activeFragment = current;
//
//                updateBottomNav(current);
//
//            }
//
//            return true;
//        }
//
//        // Agar main tabs mein hain
//        if (!(activeFragment instanceof HomeFragment)) {
//
//            showFragment(homeFragment);
//
//            return true;
//
//        }
//
//        // Home par hain -> Activity close hogi
//        return false;
//    }

    //openDetailFragment method
    private void openDetailFragment(Fragment fragment) {

        if (!isAdded()) return;

        FragmentManager manager = getChildFragmentManager();

        if (manager.isStateSaved()) return;

        FragmentTransaction transaction = manager.beginTransaction();

        // Pehle visible fragment hide karo
        if (activeFragment != null && activeFragment.isAdded()) {
            transaction.hide(activeFragment);
        }

        // Agar pehli dafa open ho raha hai
        if (!fragment.isAdded()) {

            transaction.add(R.id.fragment_holder, fragment);

        } else {

            transaction.show(fragment);

        }

        transaction.addToBackStack(null);

        transaction.commit();

        activeFragment = fragment;

        updateBottomNav(fragment);
    }


    private Fragment getVisibleFragment() {

        for (Fragment fragment : getChildFragmentManager().getFragments()) {

            if (fragment != null
                    && fragment.isAdded()
                    && fragment.isVisible()) {

                return fragment;

            }

        }

        return null;
    }




    public boolean handleBackPressed() {

        FragmentManager fm = getChildFragmentManager();

        // Agar sub screen open hai
        if (fm.getBackStackEntryCount() > 0) {

            fm.popBackStack();

            fm.executePendingTransactions();

            Fragment current = getVisibleFragment();

            if (current == null) {

                current = homeFragment;

                showFragment(homeFragment);

            } else {

                activeFragment = current;

                updateBottomNav(current);

            }


            return true;

        }

        // Agar Home par nahi ho
        if (!(activeFragment instanceof HomeFragment)) {

            showFragment(homeFragment);

            return true;

        }

        // Home par ho to Activity close hogi
        return false;
    }
    @Override
    public void onDestroyView() {

        super.onDestroyView();

        if (notificationRepository != null) {

            notificationRepository.removeListener();


        }
    }



    /** Which bottom-nav button corresponds to a given content fragment. */
    private LinearLayout navButtonFor(Fragment f) {

        if (f == orderFragment) return btnOrder;
        if (f == cartFragment) return btnCart;
        if (f == profileFragment) return btnProfile;
        if (f == homeFragment) return btnJourney;

        return btnDashboard;
    }
}

