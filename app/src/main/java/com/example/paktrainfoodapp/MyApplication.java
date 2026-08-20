package com.example.paktrainfoodapp;

import android.app.Application;

import com.example.paktrainfoodapp.utils.ThemeManager;
import com.stripe.android.PaymentConfiguration;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Apply the saved light/dark choice before any Activity is created,
        // otherwise the app briefly flashes the wrong theme on cold start.
        ThemeManager.applySavedMode(this);

        // Bring back any cart the passenger left behind before the app closed.
        CartManager.init(this);
        CartManager.restoreIfNeeded();

        // Stripe yahan initialize hoga
        PaymentConfiguration.init(
                getApplicationContext(),
                "pk_test_51T2vhzDKgsKyivl6XMUfyaPEUTHOo5Nbbzh8myFJzg4CsHLpwrmwCPCHfXJS3TMF2ZTxvjgx4SO32tJ7oWSe6djY00M3AcQsWg" // Yahan apni Publishable Key dalein
        );
    }
}