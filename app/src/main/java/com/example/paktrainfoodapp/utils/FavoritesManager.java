package com.example.paktrainfoodapp.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Stores the passenger's favorite restaurant UIDs locally on the device.
 * Kept simple (SharedPreferences) since favorites are a per-device preference,
 * not something that needs to sync across devices for this project.
 */
public class FavoritesManager {

    private static final String PREF_NAME = "PakTrainFavorites";
    private static final String KEY_FAVORITE_RESTAURANTS = "favorite_restaurant_ids";

    private final SharedPreferences pref;

    public FavoritesManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFavorite(String restaurantUid) {
        if (restaurantUid == null) return false;
        return getFavoriteSet().contains(restaurantUid);
    }

    /**
     * Toggles the favorite state for a restaurant and returns the new state
     * (true = now favorited, false = now un-favorited).
     */
    public boolean toggleFavorite(String restaurantUid) {

        if (restaurantUid == null) return false;

        Set<String> favorites = getFavoriteSet();

        boolean nowFavorite;

        if (favorites.contains(restaurantUid)) {
            favorites.remove(restaurantUid);
            nowFavorite = false;
        } else {
            favorites.add(restaurantUid);
            nowFavorite = true;
        }

        pref.edit().putStringSet(KEY_FAVORITE_RESTAURANTS, favorites).apply();

        return nowFavorite;
    }

    private Set<String> getFavoriteSet() {
        // Return a mutable copy - SharedPreferences string sets should never be
        // mutated in place and written back directly.
        return new HashSet<>(
                pref.getStringSet(KEY_FAVORITE_RESTAURANTS, new HashSet<>())
        );
    }
}
