package com.example.paktrainfoodapp.ui.main.Passenger;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.FirebaseDatabase;

public class LocationService extends Service {

    private static final String TAG = "LocationService";

    private static final String CHANNEL_ID = "LocationTrackingChannel";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private String orderId = "";
    private String passengerUid = "";
    private String station = "";

    // ✅ YOUR REALTIME DATABASE URL
    private static final String DB_URL =
            "https://paktrainfoodservice-default-rtdb.firebaseio.com/";

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

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

        startLocationUpdates();

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
                        10000 // 10 seconds
                )
                        .setMinUpdateIntervalMillis(00000)
                        .setMinUpdateDistanceMeters(5)
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
    // NOTIFICATION CHANNEL
    // =========================================================

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Location Tracking",
                            NotificationManager.IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {

                manager.createNotificationChannel(channel);
            }
        }
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroy() {

        super.onDestroy();

        if (fusedLocationClient != null
                && locationCallback != null) {

            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        Log.d(TAG, "Service Destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }
}

//

