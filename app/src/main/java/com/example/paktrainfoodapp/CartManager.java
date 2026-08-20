package com.example.paktrainfoodapp;

import android.content.Context;

import com.example.paktrainfoodapp.data.CartStorage;
import com.example.paktrainfoodapp.ui.main.Passenger.home.CartItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CartManager {

    private static HashMap<String, CartItem> cartMap = new HashMap<>();
    private static List<Runnable> listeners = new ArrayList<>();

    /**
     * Application context kept so every cart change can be written to disk
     * without each caller having to pass one in. Set once from MyApplication.
     */
    private static Context appContext;

    private static boolean restored = false;

    public static void init(Context context) {

        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    /**
     * Loads the cart saved before the app was closed. Safe to call more than
     * once - it only restores on the first call.
     */
    public static void restoreIfNeeded() {

        if (restored || appContext == null) return;

        restored = true;

        for (CartItem item : CartStorage.load(appContext)) {
            cartMap.put(item.getKey(), item);
        }

        if (!cartMap.isEmpty()) {
            notifyChange();
        }
    }

    /** Replaces the whole cart, e.g. when a saved order is resumed. */
    public static void replaceAll(List<CartItem> items) {

        cartMap.clear();

        if (items != null) {
            for (CartItem item : items) {
                cartMap.put(item.getKey(), item);
            }
        }

        notifyChange();
    }

    private static void persist() {

        if (appContext != null) {
            CartStorage.save(appContext, new ArrayList<>(cartMap.values()));
        }
    }

    public static CartItem getFirstItem() {

        if (cartMap.isEmpty()) return null;

        return new ArrayList<>(cartMap.values()).get(0);
    }

    public static void addListener(Runnable r) {

        if (!listeners.contains(r)) {
            listeners.add(r);
        }
    }

    public static void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private static void notifyChange() {

        persist();

        for (Runnable r : listeners) {
            r.run();
        }
    }

    public static void addOrUpdate(CartItem newItem) {

        String key = newItem.getKey();

        if (cartMap.containsKey(key)) {

            CartItem existing = cartMap.get(key);

            existing.setQuantity(
                    existing.getQuantity() + newItem.getQuantity()
            );

        } else {
            cartMap.put(key, newItem);
        }

        notifyChange();
    }

    public static List<CartItem> getCartItems() {
        return new ArrayList<>(cartMap.values());
    }

    public static double getTotalPrice() {

        double total = 0;

        for (CartItem item : cartMap.values()) {
            total += item.getPrice() * item.getQuantity();
        }

        return total;
    }

    public static void clear() {
        cartMap.clear();
        notifyChange();
    }

    /**
     * Increases quantity for the given cart item key by 1.
     */
    public static void increaseQuantity(String key) {

        CartItem item = cartMap.get(key);

        if (item != null) {
            item.setQuantity(item.getQuantity() + 1);
            notifyChange();
        }
    }

    /**
     * Decreases quantity for the given cart item key by 1.
     * Removes the item entirely once quantity would drop to 0.
     */
    public static void decreaseQuantity(String key) {

        CartItem item = cartMap.get(key);

        if (item == null) return;

        if (item.getQuantity() <= 1) {
            cartMap.remove(key);
        } else {
            item.setQuantity(item.getQuantity() - 1);
        }

        notifyChange();
    }

    public static boolean isEmpty() {
        return cartMap.isEmpty();
    }
}



