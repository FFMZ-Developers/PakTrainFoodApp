package com.example.paktrainfoodapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.paktrainfoodapp.ui.main.Passenger.home.CartItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Saves the passenger's cart to disk so it survives the app being closed.
 *
 * Along with the items we store when the cart was last saved, because a food
 * order is only valid for a short window - see {@link CartResumeHelper}.
 */
public class CartStorage {

    private static final String PREF_NAME = "PakTrainCart";
    private static final String KEY_ITEMS = "cart_items";
    private static final String KEY_SAVED_AT = "cart_saved_at";

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void save(Context context, List<CartItem> items) {

        if (context == null) return;

        try {

            JSONArray array = new JSONArray();

            for (CartItem item : items) {

                JSONObject o = new JSONObject();

                o.put("itemId", safe(item.getItemId()));
                o.put("restaurantId", safe(item.getRestaurantId()));
                o.put("restaurantName", safe(item.getRestaurantName()));
                o.put("name", safe(item.getName()));
                o.put("price", item.getPrice());
                o.put("quantity", item.getQuantity());
                o.put("size", safe(item.getSize()));
                o.put("imageUrl", safe(item.getImageUrl()));
                o.put("description", safe(item.getDescription()));
                o.put("mealStation", safe(item.getMealStation()));
                o.put("trainId", safe(item.getTrainId()));
                o.put("routeId", safe(item.getRouteId()));
                o.put("fromStation", safe(item.getFromStation()));
                o.put("toStation", safe(item.getToStation()));
                o.put("trainName", safe(item.getTrainName()));

                array.put(o);
            }

            SharedPreferences.Editor editor = prefs(context).edit();

            editor.putString(KEY_ITEMS, array.toString());

            // Only (re)stamp the time when the cart actually has something in
            // it, so an empty cart never looks like a stale pending order.
            if (items.isEmpty()) {
                editor.remove(KEY_SAVED_AT);
            } else if (!prefs(context).contains(KEY_SAVED_AT)) {
                editor.putLong(KEY_SAVED_AT, System.currentTimeMillis());
            }

            editor.apply();

        } catch (Exception ignored) {
            // Persistence is best-effort; never crash the cart over it.
        }
    }

    public static List<CartItem> load(Context context) {

        List<CartItem> items = new ArrayList<>();

        if (context == null) return items;

        String json = prefs(context).getString(KEY_ITEMS, null);

        if (json == null || json.isEmpty()) return items;

        try {

            JSONArray array = new JSONArray(json);

            for (int i = 0; i < array.length(); i++) {

                JSONObject o = array.getJSONObject(i);

                items.add(new CartItem(
                        o.optString("itemId"),
                        o.optString("restaurantId"),
                        o.optString("restaurantName"),
                        o.optString("name"),
                        o.optDouble("price", 0),
                        o.optInt("quantity", 1),
                        o.optString("size"),
                        o.optString("imageUrl"),
                        o.optString("description"),
                        o.optString("mealStation"),
                        o.optString("trainId"),
                        o.optString("routeId"),
                        o.optString("fromStation"),
                        o.optString("toStation"),
                        o.optString("trainName")
                ));
            }

        } catch (Exception ignored) {
            // Corrupt data - treat as an empty cart rather than crashing.
        }

        return items;
    }

    /** Milliseconds since the cart was first filled, or -1 if nothing saved. */
    public static long ageMillis(Context context) {

        long savedAt = prefs(context).getLong(KEY_SAVED_AT, -1);

        if (savedAt <= 0) return -1;

        return System.currentTimeMillis() - savedAt;
    }

    public static void clear(Context context) {

        if (context == null) return;

        prefs(context).edit()
                .remove(KEY_ITEMS)
                .remove(KEY_SAVED_AT)
                .apply();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
