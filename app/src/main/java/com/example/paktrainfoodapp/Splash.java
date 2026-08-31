package com.example.paktrainfoodapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.paktrainfoodapp.ui.main.FirebaseSeeder;
import com.example.paktrainfoodapp.ui.auth.AuthActivity;
import com.example.paktrainfoodapp.ui.main.MainActivity;
import com.example.paktrainfoodapp.utils.PrefManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;

public class Splash extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 2000;

    @Override
    protected void onCreate(Bundle BundleSavedInstance) {
        super.onCreate(BundleSavedInstance);

        // 📱 Fullscreen Setting
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_splash);

        // 🔥 Optimized Firestore Cache Configuration (Crash-Safe & Faster)
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            // Naye Firebase SDK ke mutabiq Persistent Cache architecture
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED) // Train routes data k liye unlimited size
                            .build())
                    .build();

            db.setFirestoreSettings(settings);
        } catch (IllegalStateException e) {
            // Agar Firestore instance pehle hi initialize ho chuka ho, to error catch ho jaye ga aur app crash nahi hogi
            e.printStackTrace();
        }

        FirebaseSeeder seeder = new FirebaseSeeder();
        seeder.seedAll();
        // 🚂 Background worker for station coordinates (Saves stations data offline)
        SaveStationLocations.saveAllStations();

        // ⏱️ High-Speed Navigation Handling
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            PrefManager pref = new PrefManager(Splash.this);
            FirebaseAuth auth = FirebaseAuth.getInstance();

            // Double security: Firebase Session + SharedPreferences Role check
            if (auth.getCurrentUser() != null && pref.isLoggedIn() && !pref.getUserRole().isEmpty()) {

                Intent intent = new Intent(Splash.this, MainActivity.class);
                intent.putExtra("USER_ROLE_KEY", pref.getUserRole());
                startActivity(intent);
                finish();

            } else {
                // Not logged in or Session Expired
                // Agar Firebase session na ho to clear SharedPrefs safely
                if (auth.getCurrentUser() == null) {
                    pref.setLogin(false);
                }

                startActivity(new Intent(Splash.this, AuthActivity.class));
                finish();
            }
        }, SPLASH_DELAY_MS);
    }

}

//
