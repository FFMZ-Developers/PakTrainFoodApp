package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

public class LocationHelper {

    public interface LocationCallback {
        void onLocationReceived(Location location);

        void onLocationFailed(String message);
    }

    public static boolean hasPermission(Activity activity) {

        return ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermission(
            ActivityResultLauncher<String> launcher
    ) {

        launcher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
        );
    }

    public static void getCurrentLocation(
            Activity activity,
            LocationCallback callback
    ) {

        if (!hasPermission(activity)) {

            callback.onLocationFailed("Location Permission Required");

            return;
        }

        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(activity);

        try {

            client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    new CancellationTokenSource().getToken()
            ).addOnSuccessListener(location -> {

                if (location != null) {

                    callback.onLocationReceived(location);

                } else {

                    callback.onLocationFailed("Location not found");
                }

            }).addOnFailureListener(e -> {

                callback.onLocationFailed(e.getMessage());

            });

        } catch (SecurityException e) {

            callback.onLocationFailed(e.getMessage());
        }
    }
}