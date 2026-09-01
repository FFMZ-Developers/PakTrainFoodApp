package com.example.paktrainfoodapp.ui.main.Passenger;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class LocationService extends Service {

    private static final String TAG = "LocationService";

    private static final String CHANNEL_ID = "LocationTrackingChannel";

    // Separate (higher-priority) channel for the "please turn location back
    // on" alert, so it doesn't get lost among the low-priority silent
    // "tracking active" notification.
    private static final String ALERT_CHANNEL_ID = "LocationOffAlertChannel";

    // =========================================================
    // ⚙️ TESTING CONFIG — CHANGE THIS BACK BEFORE RELEASE
    //
    // Har GPS fix ke darmiyan kitna wait karna hai. Pehle 20 minute rakha
    // gaya tha, lekin isi wajah se testing ke dauran "current station" aur
    // "ETA" bahut dair tak purani location par hi atke reh jate the (agla
    // fix milne mein 20 minute lag rahe the). Ab 10 second kar diya hai
    // taake har test cycle mein turant naya data mile aur live-update dekh
    // sako. Live production release se pehle ise wapas kam-frequent (jaise
    // 60-120 second) kar dena — 10 second production mein battery jaldi khatam
    // karega. Poora service isi ek constant ko follow karta hai.
    // =========================================================
    private static final long LOCATION_UPDATE_INTERVAL_MS = 10 * 1000L; // 10 seconds (TESTING)
    // private static final long LOCATION_UPDATE_INTERVAL_MS = 20 * 60 * 1000L; // 20 minutes (was too slow for testing)

    // How often we check whether device location (GPS) is still switched on.
    private static final long LOCATION_ENABLED_CHECK_INTERVAL_MS = 5000L; // 5 seconds

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationManager locationManager;

    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private Runnable watchdogRunnable;
    private boolean lastKnownLocationEnabled = true;

    private FirebaseFirestore firestore;
    private ListenerRegistration orderStatusListener;

    private String orderId = "";
    private String passengerUid = "";
    private String station = "";

    // ✅ YOUR REALTIME DATABASE URL
    private static final String DB_URL =
            "https://paktrainfoodservice-default-rtdb.firebaseio.com/";

    // Exposed so an app-wide watchdog (see LocationEnforcementWatcher) can
    // tell whether this service is actually alive without relying on the
    // deprecated ActivityManager#getRunningServices API.
    public static volatile boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannels();

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        firestore = FirebaseFirestore.getInstance();

        isRunning = true;

        Log.d(TAG, "Service Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {

            orderId = intent.getStringExtra("orderId");
            passengerUid = intent.getStringExtra("passengerUid");
            station = intent.getStringExtra("station");

        }

        Log.d(TAG, "Order ID : " + orderId);
        Log.d(TAG, "Passenger UID : " + passengerUid);
        Log.d(TAG, "Station : " + station);

        if (orderId == null || orderId.isEmpty()) {

            Log.e(TAG, "Order ID NULL");

            stopSelf();

            return START_NOT_STICKY;
        }

        startMyForegroundService();

        // ✅ FIX: writes a location to Realtime Database the INSTANT the
        // service starts, using whatever fix the device already has
        // cached (getLastLocation() - near-instant), instead of only
        // waiting for the next scheduled GPS request cycle. This is what
        // guarantees an entry appears under OrderLocations/{orderId}/latest
        // right after the order is placed, rather than the passenger
        // wondering why Firebase shows nothing yet.
        writeImmediateLastKnownLocation();

        startLocationUpdates();

        startLocationEnabledWatchdog();

        listenForOrderCompletion();

        // START_STICKY: agar system service ko kill kar de (low memory
        // waghera), Android usay dobara start karega. Extras (orderId etc.)
        // us waqt null aa sakte hain, isliye LocationEnforcementWatcher
        // (app-level) bhi orderId Firestore se dobara nikal ke poori Intent
        // ke saath service restart karta hai — sirf STICKY par depend nahi
        // karte.
        return START_STICKY;
    }

    // =========================================================
    // FOREGROUND SERVICE
    // =========================================================

    private void startMyForegroundService() {

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Train Food Tracking")
                        .setContentText("Passenger location sharing active")
                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                        .setOngoing(true)
                        .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                    2,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            );

        } else {

            startForeground(2, notification);
        }

        Log.d(TAG, "Foreground Service Started");
    }

    // =========================================================
    // LOCATION UPDATES
    // =========================================================

    /**
     * Fires once, immediately, when the service starts - writes whatever
     * location fix Android already has cached (usually near-instant,
     * unlike a brand-new GPS request which can take a while indoors/cold).
     * Purely a "don't leave Firebase empty while we wait" measure; the
     * regular requestLocationUpdates() call right after this keeps it
     * fresh going forward.
     */
    private void writeImmediateLastKnownLocation() {

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {

                if (location == null) {
                    Log.d(TAG, "No cached last-known location available yet");
                    return;
                }

                PassengerLocationModel model = new PassengerLocationModel(
                        orderId, passengerUid, station,
                        location.getLatitude(), location.getLongitude(),
                        System.currentTimeMillis());

                FirebaseDatabase.getInstance(DB_URL)
                        .getReference("OrderLocations")
                        .child(orderId)
                        .child("latest")
                        .setValue(model)
                        .addOnSuccessListener(unused ->
                                Log.d(TAG, "Immediate last-known location written"))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Immediate location write failed: " + e.getMessage()));
            });

        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception (immediate write): " + e.getMessage());
        }
    }

    private void startLocationUpdates() {

        // ✅ PERMISSION CHECK

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            Log.e(TAG, "Location Permission NOT Granted");

            stopSelf();

            return;
        }

        LocationRequest locationRequest =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
<<<<<<< HEAD
                        10000 // 10 seconds
                )
                        .setMinUpdateIntervalMillis(00000)
                        .setMinUpdateDistanceMeters(5)
=======
                        LOCATION_UPDATE_INTERVAL_MS
                )
                        .setMinUpdateIntervalMillis(LOCATION_UPDATE_INTERVAL_MS)
                        // TESTING: 0 = report every interval tick even if the
                        // device barely moved (e.g. testing indoors/stationary).
                        // For production, raise this back to ~5-10m so a
                        // parked/stationary phone doesn't spam writes.
                        .setMinUpdateDistanceMeters(0)
>>>>>>> origin/Fahad
                        .build();

        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult result) {

                super.onLocationResult(result);

                if (result == null) {

                    Log.e(TAG, "Location Result NULL");

                    return;
                }

                Location location = result.getLastLocation();

                if (location == null) {

                    Log.e(TAG, "Location NULL");

                    return;
                }

                double lat = location.getLatitude();
                double lng = location.getLongitude();

                Log.d(TAG, "Latitude : " + lat);
                Log.d(TAG, "Longitude : " + lng);

                PassengerLocationModel model =
                        new PassengerLocationModel(
                                orderId,
                                passengerUid,
                                station,
                                lat,
                                lng,
                                System.currentTimeMillis()
                        );

                // =================================================
                // SAVE TO FIREBASE REALTIME DATABASE
                // =================================================

                FirebaseDatabase
                        .getInstance(DB_URL)
                        .getReference("OrderLocations")
                        .child(orderId)
                        .child("latest")
                        .setValue(model)

                        .addOnSuccessListener(unused -> {

                            Log.d(TAG,
                                    "Location Saved Successfully");

                        })

                        .addOnFailureListener(e -> {

                            Log.e(TAG,
                                    "Firebase Error : "
                                            + e.getMessage());

                        });
            }
        };

        try {

            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );

            Log.d(TAG, "Location Updates Started");

        } catch (SecurityException e) {

            Log.e(TAG,
                    "Security Exception : "
                            + e.getMessage());
        }
    }

    // =========================================================
    // GPS ON/OFF WATCHDOG
    //
    // Passenger safar ke dauran kabhi bhi device ki location band kar sakta
    // hai. Har LOCATION_ENABLED_CHECK_INTERVAL_MS (5 sec) mein check karte
    // hain — agar band paayi to ek notification bhej dete hain ("location
    // on karein"), aur jab dobara ON ho jaye to location updates ko taaza
    // (re-request) kar dete hain taake tracking khud-ba-khud resume ho jaye
    // — user ko dobara order karne ya app kholne ki zaroorat nahi.
    // =========================================================

    private void startLocationEnabledWatchdog() {

        lastKnownLocationEnabled = isDeviceLocationEnabled();

        watchdogRunnable = new Runnable() {
            @Override
            public void run() {

                boolean nowEnabled = isDeviceLocationEnabled();

                if (lastKnownLocationEnabled && !nowEnabled) {

                    // Location abhi abhi OFF hui
                    Log.w(TAG, "Location turned OFF during active journey");
                    sendLocationOffAlert();

                } else if (!lastKnownLocationEnabled && nowEnabled) {

                    // Location dobara ON hui — tracking resume karo
                    Log.d(TAG, "Location turned back ON — resuming tracking");

                    restartLocationUpdates();

                    sendLocationResumedNotice();
                }

                lastKnownLocationEnabled = nowEnabled;

                watchdogHandler.postDelayed(this, LOCATION_ENABLED_CHECK_INTERVAL_MS);
            }
        };

        watchdogHandler.postDelayed(watchdogRunnable, LOCATION_ENABLED_CHECK_INTERVAL_MS);
    }

    private boolean isDeviceLocationEnabled() {

        if (locationManager == null) return true; // fail-open, don't nag incorrectly

        return LocationManagerCompat.isLocationEnabled(locationManager);
    }

    private void restartLocationUpdates() {

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        startLocationUpdates();
    }

    private void sendLocationOffAlert() {

        Intent locationSettingsIntent =
                new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        locationSettingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                locationSettingsIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification =
                new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                        .setContentTitle("Location On Karein")
                        .setContentText("Aapka order raste mein hai — live tracking jaari rakhne ke liye location dobara on karein.")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .build();

        NotificationManager manager = getSystemService(NotificationManager.class);

        if (manager != null) {
            manager.notify(3, notification);
        }
    }

    private void sendLocationResumedNotice() {

        Notification notification =
                new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                        .setContentTitle("Live Tracking Resume Ho Gayi")
                        .setContentText("Location dobara on ho gayi hai — tracking phir se shuru ho gayi.")
                        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build();

        NotificationManager manager = getSystemService(NotificationManager.class);

        // Clears the earlier "turn location on" alert and replaces it with
        // the resumed notice on the same notification id.
        if (manager != null) {
            manager.notify(3, notification);
        }
    }

    // =========================================================
    // AUTO-STOP WHEN ORDER COMPLETES
    //
    // Jaisay hi Orders/{orderId}.orderStatus "completed" (ya "cancelled" /
    // refunded) ho jaye, live tracking khud-ba-khud band ho jaani chahiye —
    // passenger ko manually kuch nahi karna. Yeh listener isi liye hai.
    // =========================================================

    private void listenForOrderCompletion() {

        if (orderId == null || orderId.isEmpty()) return;

        orderStatusListener = firestore.collection("Orders")
                .document(orderId)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null || !snapshot.exists()) return;

                    String status = snapshot.getString("orderStatus");

                    if (status == null) return;

                    boolean isTerminal =
                            status.equalsIgnoreCase("completed") ||
                            status.equalsIgnoreCase("cancelled") ||
                            status.equalsIgnoreCase("refunded") ||
                            // ✅ FIX: these three terminal states were
                            // added later (Module 3's restaurant reject,
                            // Module 6's rider-reported failure, and the
                            // admin-review "disputed" freeze) but never
                            // added here - so a passenger's GPS kept
                            // running and draining battery indefinitely on
                            // an order that was already dead.
                            status.equalsIgnoreCase("rejected") ||
                            status.equalsIgnoreCase("delivery_failed") ||
                            status.equalsIgnoreCase("disputed");

                    if (isTerminal) {

                        Log.d(TAG, "Order reached terminal status (" + status
                                + ") — stopping live tracking");

                        stopSelf();
                    }
                });
    }

    // =========================================================
    // NOTIFICATION CHANNELS
    // =========================================================

    private void createNotificationChannels() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager == null) return;

            NotificationChannel trackingChannel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Location Tracking",
                            NotificationManager.IMPORTANCE_LOW
                    );

            manager.createNotificationChannel(trackingChannel);

            NotificationChannel alertChannel =
                    new NotificationChannel(
                            ALERT_CHANNEL_ID,
                            "Location Alerts",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            manager.createNotificationChannel(alertChannel);
        }
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroy() {

        super.onDestroy();

        isRunning = false;

        if (fusedLocationClient != null
                && locationCallback != null) {

            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        if (watchdogRunnable != null) {
            watchdogHandler.removeCallbacks(watchdogRunnable);
        }

        if (orderStatusListener != null) {
            orderStatusListener.remove();
        }

        Log.d(TAG, "Service Destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }
}
