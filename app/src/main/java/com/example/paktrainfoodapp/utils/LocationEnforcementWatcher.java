package com.example.paktrainfoodapp.utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.location.LocationManagerCompat;

import com.example.paktrainfoodapp.ui.main.Passenger.LocationService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Arrays;
import java.util.List;

/**
 * Module 2 - "app kholte hi sab theek karo" watchdog.
 *
 * Attach this to MainActivity (start in onResume, stop in onPause). While
 * the app is in the foreground it checks every 5 seconds:
 *
 *   1. Is device location (GPS) switched on at all? If not, show a popup
 *      asking the user to turn it on - repeats every 5 seconds until it is,
 *      then auto-dismisses.
 *
 *   2. Does the current passenger have an order that's still "in transit"
 *      (i.e. not yet completed)? If so, the background LocationService
 *      should be alive and tracking. If it got killed (low memory, battery
 *      optimisation, app swiped away, etc.), restart it automatically and
 *      let the user know with a short toast - matches the requirement that
 *      payment/tracking-critical background work must self-heal rather than
 *      silently stop.
 *
 * This is deliberately foreground-only (a Handler loop, not a background
 * job) - LocationService itself already runs its own 5-second GPS-on/off
 * watchdog independently in the background (see LocationService.java), so
 * tracking recovers even while the app isn't open. This class is the extra
 * layer for "whenever the user opens the app, make sure everything is
 * actually still working".
 */
public class LocationEnforcementWatcher {

    private static final long CHECK_INTERVAL_MS = 5000L; // 5 seconds

    private static final List<String> ACTIVE_ORDER_STATUSES = Arrays.asList(
            "Active",
            "accepted_by_rider",
            "arrive_rider_at_resturent",
            "dropped",
            "pick_up"
    );

    private final AppCompatActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable loopRunnable;

    private AlertDialog locationOffDialog;
    private boolean checkingActiveOrder = false;
    private long lastServiceRestartAttemptMs = 0L;

    public LocationEnforcementWatcher(AppCompatActivity activity) {
        this.activity = activity;
    }

    public void start() {

        if (loopRunnable != null) return; // already running

        loopRunnable = new Runnable() {
            @Override
            public void run() {

                checkLocationEnabled();
                checkActiveOrderTracking();

                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };

        handler.post(loopRunnable);
    }

    public void stop() {

        if (loopRunnable != null) {
            handler.removeCallbacks(loopRunnable);
            loopRunnable = null;
        }

        dismissLocationOffDialog();
    }

    // =========================================================
    // 1. Device location on/off
    // =========================================================

    private void checkLocationEnabled() {

        LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);

        boolean enabled = lm != null && LocationManagerCompat.isLocationEnabled(lm);

        if (!enabled) {
            showLocationOffDialog();
        } else {
            dismissLocationOffDialog();
        }
    }

    private void showLocationOffDialog() {

        if (activity.isFinishing() || activity.isDestroyed()) return;

        if (locationOffDialog != null && locationOffDialog.isShowing()) {
            return; // already showing - no need to stack dialogs every 5 sec
        }

        locationOffDialog = new AlertDialog.Builder(activity)
                .setTitle("Location On Karein")
                .setMessage("Order tracking aur bar-waqt updates ke liye apni device ki location on karna zaroori hai.")
                .setCancelable(false)
                .setPositiveButton("Location Settings", (dialog, which) -> {

                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    activity.startActivity(intent);
                })
                .create();

        locationOffDialog.show();
    }

    private void dismissLocationOffDialog() {

        if (locationOffDialog != null && locationOffDialog.isShowing()) {
            locationOffDialog.dismiss();
        }

        locationOffDialog = null;
    }

    // =========================================================
    // 2. Active order -> background tracking service must be alive
    // =========================================================

    private void checkActiveOrderTracking() {

        if (checkingActiveOrder) return; // previous check still in flight, skip this tick

        if (LocationService.isRunning) return; // already alive, nothing to do

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return;

        checkingActiveOrder = true;

        FirebaseFirestore.getInstance()
                .collection("Orders")
                .whereEqualTo("passengerUid", uid)
                .whereIn("orderStatus", ACTIVE_ORDER_STATUSES)
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {

                    checkingActiveOrder = false;

                    if (query == null || query.isEmpty()) return;

                    for (QueryDocumentSnapshot doc : query) {
                        restartTrackingService(
                                doc.getId(),
                                uid,
                                doc.getString("mealStation")
                        );
                        break;
                    }
                })
                .addOnFailureListener(e -> checkingActiveOrder = false);
    }

    private void restartTrackingService(String orderId, String passengerUid, String station) {

        long now = System.currentTimeMillis();

        // Don't hammer startForegroundService() every 5 seconds if it keeps
        // failing for some reason (e.g. permission revoked mid-journey) -
        // back off to at most one restart attempt per cycle, and let the
        // toast tell the user what's happening each time.
        if (now - lastServiceRestartAttemptMs < CHECK_INTERVAL_MS) return;

        lastServiceRestartAttemptMs = now;

        Intent serviceIntent = new Intent(activity, LocationService.class);
        serviceIntent.putExtra("orderId", orderId);
        serviceIntent.putExtra("passengerUid", passengerUid);
        serviceIntent.putExtra("station", station);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(serviceIntent);
        } else {
            activity.startService(serviceIntent);
        }

        Toast.makeText(activity,
                "Order tracking dobara shuru ho rahi hai...",
                Toast.LENGTH_SHORT).show();
    }
}
