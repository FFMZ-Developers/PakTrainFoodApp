package com.example.paktrainfoodapp.ui.main.Delivery.dashboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Delivery.order.DeliveryOrderFragment;

import com.example.paktrainfoodapp.ui.main.Delivery.profile.DeliveryProfileFragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class DeliveryDashboardFragment extends Fragment {

    private LinearLayout btn_delivery_menu,
            btn_delivery_order,
            btn_deliver_home,
            btn_delivery_profile;

    private ImageView icon_delivery_menu,
            icon_delivery_order,
            icon_deliver_home,
            icon_delivery_profile;

    private TextView text_delivery_menu,
            text_delivery_order,
            text_deliver_home,
            text_delivery_profile;

    // ================= LOCATION =================

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private DatabaseReference riderRef;

    private String currentTag = "home";

    private TextView txtRiderBadge;
    private com.example.paktrainfoodapp.ui.main.notification.NotificationRepository
            riderNotificationRepository;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(
                R.layout.fragment_delivery_dashboard,
                container,
                false
        );
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        // ================= INIT LOCATION =================

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(requireActivity());

        String uid = FirebaseAuth.getInstance().getUid();

        if (uid != null) {

            // Module 7 - pause-status check (same pattern as the
            // restaurant dashboard).
            checkPauseStatus(uid);

            // ✅ FIX: this used to call FirebaseDatabase.getInstance()
            // with NO explicit database URL - relying on whatever
            // "default" RTDB instance the SDK auto-resolves. The backend
            // (Cloud Functions, admin.database()) explicitly resolves to
            // "https://paktrainfoodservice-default-rtdb.firebaseio.com/"
            // (confirmed in this project's own deploy logs), and
            // LocationService.java already hardcodes that same URL for
            // passenger location writes - but this rider dashboard never
            // did, so there was a real risk of the rider's own
            // online/location data landing in a DIFFERENT database
            // instance than the one dispatchRider.js actually reads from
            // when deciding who to notify. Explicitly pinning the same
            // URL here removes that ambiguity entirely.
            riderRef = FirebaseDatabase.getInstance(
                            "https://paktrainfoodservice-default-rtdb.firebaseio.com/")
                    .getReference("DeliveryRiders")
                    .child(uid);
        }

        // ================= BIND VIEWS =================

        btn_delivery_menu =
                view.findViewById(R.id.btn_delivery_menu);

        txtRiderBadge = view.findViewById(R.id.txtRiderBadge);

        startRiderNotificationBadge();

        btn_delivery_order =
                view.findViewById(R.id.btn_delivery_order);

        btn_deliver_home =
                view.findViewById(R.id.btn_deliver_home);

        btn_delivery_profile =
                view.findViewById(R.id.btn_delivery_profile);

        icon_delivery_menu =
                view.findViewById(R.id.icon_delivery_menu);

        icon_delivery_order =
                view.findViewById(R.id.icon_delivery_order);

        icon_deliver_home =
                view.findViewById(R.id.icon_deliver_home);

        icon_delivery_profile =
                view.findViewById(R.id.icon_delivery_profile);

        text_delivery_menu =
                view.findViewById(R.id.text_delivery_menu);

        text_delivery_order =
                view.findViewById(R.id.text_delivery_order);

        text_deliver_home =
                view.findViewById(R.id.text_deliver_home);

        text_delivery_profile =
                view.findViewById(R.id.text_delivery_profile);

        // ✅ FIX: this fragment's own duplicate top header (with its own
        // "Online" switch) was removed - see fragment_delivery_dashboard.xml.
        // There is now exactly ONE toggle in the whole rider app - the one
        // in fragment_delivery_home.xml's teal header, owned by
        // DeliveryHomeFragment.java. That toggle calls setOnlineStatus()
        // below (this fragment still owns fusedLocationClient/riderRef,
        // so it's the right place for the actual RTDB write + location
        // start/stop to live), instead of this fragment forcing its own
        // separate (and previously buggy - always reset to false on every
        // load) online state.

        // ================= DEFAULT FRAGMENT =================

        openFragment(new DeliveryHomeFragment(), "home", false);

        highlightButton(
                btn_deliver_home,
                icon_deliver_home,
                text_deliver_home
        );

        // ================= CLICK LISTENERS =================

        btn_delivery_menu.setOnClickListener(v -> {

            openFragment(
                    com.example.paktrainfoodapp.ui.main.notification.NotificationFragment
                            .newInstance(com.example.paktrainfoodapp.ui.main.notification
                                    .NotificationRepository.ROLE_DELIVERY),
                    "notification", true);

            highlightButton(
                    btn_delivery_menu,
                    icon_delivery_menu,
                    text_delivery_menu
            );
        });

        btn_delivery_order.setOnClickListener(v -> {

            openFragment(new DeliveryOrderFragment(), "order", true);

            highlightButton(
                    btn_delivery_order,
                    icon_delivery_order,
                    text_delivery_order
            );
        });

        btn_deliver_home.setOnClickListener(v -> {

            openFragment(new DeliveryHomeFragment(), "home", true);

            highlightButton(
                    btn_deliver_home,
                    icon_deliver_home,
                    text_deliver_home
            );
        });

        btn_delivery_profile.setOnClickListener(v -> {

            openFragment(new DeliveryProfileFragment(), "profile", true);

            highlightButton(
                    btn_delivery_profile,
                    icon_delivery_profile,
                    text_delivery_profile
            );
        });
    }

    // ================= ONLINE STATUS =================

    /**
     * The single entry point for turning the rider online/offline now -
     * called from DeliveryHomeFragment.java's switch (the only toggle in
     * the app). Writes the real value to Realtime Database AND starts/
     * stops GPS tracking together, so they can never drift out of sync
     * (e.g. "online" in the database but no location updates actually
     * being sent).
     */
    public void setOnlineStatus(boolean online) {

        updateOnlineStatus(online);

        if (online) {
            startLocationUpdates();
        } else {
            stopLocationUpdates();
        }
    }

    private void updateOnlineStatus(boolean online) {

        if (riderRef == null) {
            android.widget.Toast.makeText(getContext(),
                    "Couldn't update status - not signed in properly.",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        HashMap<String, Object> map = new HashMap<>();

        map.put("online", online);

        // ✅ FIX: this write previously had NO success/failure handling
        // at all - if it silently failed (e.g. Realtime Database security
        // rules not actually deployed live, even though correct rules
        // exist in database.rules.json locally - `firebase deploy` only
        // pushes that file with `--only database`, a separate step from
        // `--only functions`), the toggle would visually flip but the
        // database would never actually change, and the rider would stay
        // invisible to dispatchRider.js with zero indication anything was
        // wrong. Now any failure shows a clear error instead of failing
        // silently.
        riderRef.updateChildren(map)
                .addOnSuccessListener(unused -> {
                    if (!isAdded()) return;
                    android.widget.Toast.makeText(getContext(),
                            online ? "You're online" : "You're offline",
                            android.widget.Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    android.widget.Toast.makeText(getContext(),
                            "Status update failed: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                });
    }

    // ================= START LOCATION =================

    private void startLocationUpdates() {

        LocationRequest locationRequest =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        10 * 60 * 1000
                ).build();

        // ================= TESTING =================
        // Uncomment for 5 minute updates while testing

//        LocationRequest locationRequest =
//                new LocationRequest.Builder(
//                        Priority.PRIORITY_HIGH_ACCURACY,
//                        5 * 60 * 1000
//                ).build();

        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(
                    @NonNull LocationResult locationResult
            ) {

                super.onLocationResult(locationResult);

                if (locationResult == null) return;

                double lat =
                        locationResult
                                .getLastLocation()
                                .getLatitude();

                double lng =
                        locationResult
                                .getLastLocation()
                                .getLongitude();

                saveLocation(lat, lng);
            }
        };

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    101
            );

            return;
        }

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
        );
    }

    // ================= SAVE LOCATION =================

    private void saveLocation(double lat, double lng) {

        if (riderRef == null) return;

        HashMap<String, Object> map = new HashMap<>();

        map.put("lat", lat);
        map.put("lng", lng);
        map.put("online", true);
        map.put("updatedAt", System.currentTimeMillis());

        riderRef.updateChildren(map)
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    android.widget.Toast.makeText(getContext(),
                            "Location update failed: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                });
    }

    // ================= STOP LOCATION =================

    private void stopLocationUpdates() {

        if (locationCallback != null) {

            fusedLocationClient.removeLocationUpdates(
                    locationCallback
            );
        }
    }

    // ================= OPEN FRAGMENT =================

    private void openFragment(Fragment fragment, String tag, boolean addToBackStack) {

        if (tag.equals(currentTag) && addToBackStack) {
            // Already on this tab - avoid stacking a duplicate entry
            return;
        }

        currentTag = tag;

        FragmentTransaction transaction = getChildFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                )
                .replace(R.id.fragment_loader, fragment);

        if (addToBackStack) {
            transaction.addToBackStack(tag);
        }

        transaction.commit();
    }

    /**
     * Called from the host Activity's back-press handler.
     * Returns true if it consumed the back press, false if the Activity
     * should handle it (e.g. exit / go to previous activity).
     */
    public boolean handleBackPressed() {

        FragmentManager fm = getChildFragmentManager();

        if (fm.getBackStackEntryCount() > 0) {

            fm.popBackStack();
            fm.executePendingTransactions();

            for (Fragment f : fm.getFragments()) {

                if (f != null && f.isAdded()) {

                    if (f instanceof DeliveryHomeFragment) {
                        currentTag = "home";
                        highlightButton(btn_deliver_home, icon_deliver_home, text_deliver_home);
                    } else if (f instanceof com.example.paktrainfoodapp.ui.main.notification.NotificationFragment) {
                        currentTag = "notification";
                        highlightButton(btn_delivery_menu, icon_delivery_menu, text_delivery_menu);
                    } else if (f instanceof DeliveryOrderFragment) {
                        currentTag = "order";
                        highlightButton(btn_delivery_order, icon_delivery_order, text_delivery_order);
                    } else if (f instanceof DeliveryProfileFragment) {
                        currentTag = "profile";
                        highlightButton(btn_delivery_profile, icon_delivery_profile, text_delivery_profile);
                    }
                }
            }

            return true;
        }

        return false;
    }

    // ================= HIGHLIGHT BUTTON =================

    private void highlightButton(
            LinearLayout selectedLayout,
            ImageView selectedIcon,
            TextView selectedText
    ) {

        resetButtons();

        selectedLayout.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(150)
                .start();

        selectedIcon.setColorFilter(
                getResources().getColor(R.color.green)
        );

        selectedText.setTextColor(
                getResources().getColor(R.color.green)
        );

        selectedText.setTypeface(null, Typeface.BOLD);
    }

    // ================= RESET BUTTONS =================

    private void resetButtons() {

        LinearLayout[] layouts = {
                btn_delivery_menu,
                btn_delivery_order,
                btn_deliver_home,
                btn_delivery_profile
        };

        ImageView[] icons = {
                icon_delivery_menu,
                icon_delivery_order,
                icon_deliver_home,
                icon_delivery_profile
        };

        TextView[] texts = {
                text_delivery_menu,
                text_delivery_order,
                text_deliver_home,
                text_delivery_profile
        };

        for (LinearLayout layout : layouts) {

            layout.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start();

            layout.setBackgroundResource(
                    android.R.color.transparent
            );
        }

        for (ImageView icon : icons) {

            icon.setColorFilter(
                    getResources().getColor(R.color.gray)
            );
        }

        for (TextView text : texts) {

            text.setTextColor(
                    getResources().getColor(R.color.gray)
            );

            text.setTypeface(null, Typeface.NORMAL);
        }
    }

    // ================= DESTROY =================

    @Override
    public void onDestroy() {

        super.onDestroy();

        stopLocationUpdates();
    }

    /** Lets the dashboard's "Available Orders" shortcut switch to that tab. */
    public void openOrdersTab() {

        if (btn_delivery_order != null) {
            btn_delivery_order.performClick();
        }
    }

    /** Unread-count badge on the rider's notifications tab. */
    private void startRiderNotificationBadge() {

        riderNotificationRepository =
                new com.example.paktrainfoodapp.ui.main.notification.NotificationRepository();

        riderNotificationRepository.listenUnreadCount(
                com.example.paktrainfoodapp.ui.main.notification
                        .NotificationRepository.ROLE_DELIVERY,
                new com.example.paktrainfoodapp.ui.main.notification
                        .NotificationRepository.BadgeCallback() {

                    @Override
                    public void onCountChanged(int count) {

                        if (!isAdded() || txtRiderBadge == null) return;

                        requireActivity().runOnUiThread(() -> {

                            if (count > 0) {
                                txtRiderBadge.setVisibility(View.VISIBLE);
                                txtRiderBadge.setText(
                                        count > 99 ? "99+" : String.valueOf(count));
                            } else {
                                txtRiderBadge.setVisibility(View.GONE);
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

        if (riderNotificationRepository != null) {
            riderNotificationRepository.removeListener();
        }
    }

    // Module 7 - pause-status check.
    private void checkPauseStatus(String uid) {

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("Users").document("Delivery")
                .collection("VerifiedRegister").document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!isAdded() || doc == null || !doc.exists()) return;

                    Boolean isPaused = doc.getBoolean("isPaused");

                    if (isPaused != null && isPaused) {

                        String reason = doc.getString("pausedReason");

                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("Account Paused")
                                .setMessage((reason != null ? reason : "Your account has been paused due to repeated delivery issues.")
                                        + "\n\nPlease contact support to resume accepting deliveries.")
                                .setPositiveButton("OK", null)
                                .setCancelable(true)
                                .show();
                    }
                });
    }
}








//




