package com.example.paktrainfoodapp.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Stores and applies the user's light/dark theme choice.
 *
 * The app theme already extends Theme.Material3.DayNight, so switching the
 * AppCompat night mode is enough for the whole UI to follow - no per-screen
 * work needed.
 */
public class ThemeManager {

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private static final String PREF_NAME = "PakTrainSettings";
    private static final String KEY_THEME_MODE = "theme_mode";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static int getSavedMode(Context context) {
        return prefs(context).getInt(KEY_THEME_MODE, MODE_SYSTEM);
    }

    /**
     * Saves the choice and applies it immediately. Activities currently on
     * screen are recreated by AppCompat automatically.
     */
    public static void setMode(Context context, int mode) {

        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply();

        applyMode(mode);
    }

    /**
     * Call once on app start (from Application.onCreate) so the saved choice
     * survives a restart instead of flashing back to the system default.
     */
    public static void applySavedMode(Context context) {
        applyMode(getSavedMode(context));
    }

    private static void applyMode(int mode) {

        switch (mode) {

            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;

            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;

            default:
                AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
