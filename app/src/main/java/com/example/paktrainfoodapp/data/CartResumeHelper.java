package com.example.paktrainfoodapp.data;

import android.content.Context;

import com.example.paktrainfoodapp.ui.main.Passenger.home.CartItem;
import com.example.paktrainfoodapp.ui.main.Passenger.home.StationValidationHelper;

import java.util.List;

/**
 * Decides what should happen to a cart that was left behind when the app was
 * closed.
 *
 * Two things can invalidate it:
 *   1. Too much time has passed (the food would no longer arrive in time)
 *   2. The train has already reached or passed the chosen meal station
 *
 * Either way the passenger has to start a fresh journey selection; otherwise
 * they are dropped straight back into the order screen with their items.
 */
public class CartResumeHelper {

    /** A saved cart is only offered for resume within this window. */
    public static final long MAX_AGE_MILLIS = 60 * 60 * 1000L; // 1 hour

    public enum Decision {
        /** Nothing saved - normal fresh start. */
        NOTHING_SAVED,
        /** Saved cart is still valid - jump back into the order flow. */
        RESUME,
        /** Older than the allowed window - must start over. */
        EXPIRED_TOO_LATE,
        /** Train already at/past the meal station - must start over. */
        STATION_PASSED
    }

    public static class Result {

        public final Decision decision;
        public final List<CartItem> items;
        public final String message;

        Result(Decision decision, List<CartItem> items, String message) {
            this.decision = decision;
            this.items = items;
            this.message = message;
        }

        public boolean canResume() {
            return decision == Decision.RESUME;
        }
    }

    /**
     * Time-only check. Use this when the current location isn't known yet -
     * the station check can be layered on afterwards via
     * {@link #checkStation(List, List, String)}.
     */
    public static Result check(Context context) {

        List<CartItem> saved = CartStorage.load(context);

        if (saved.isEmpty()) {
            return new Result(Decision.NOTHING_SAVED, saved, null);
        }

        long age = CartStorage.ageMillis(context);

        if (age < 0 || age > MAX_AGE_MILLIS) {

            return new Result(
                    Decision.EXPIRED_TOO_LATE,
                    saved,
                    "Too much late - please order again");
        }

        return new Result(Decision.RESUME, saved, null);
    }

    /**
     * Second-stage check once the train's current station is known.
     *
     * @param route          ordered station list for the saved train
     * @param items          the saved cart items
     * @param currentStation nearest station to the passenger right now
     */
    public static Result checkStation(List<CartItem> items,
                                      List<String> route,
                                      String currentStation) {

        if (items == null || items.isEmpty()) {
            return new Result(Decision.NOTHING_SAVED, items, null);
        }

        String mealStation = items.get(0).getMealStation();

        // Without a route or a known position we can't prove it's invalid,
        // so we let the passenger continue rather than wrongly wiping a
        // legitimate cart.
        if (route == null || route.isEmpty()
                || currentStation == null || mealStation == null) {

            return new Result(Decision.RESUME, items, null);
        }

        boolean crossed = StationValidationHelper.isMealStationCrossed(
                route, currentStation, mealStation);

        if (crossed) {

            return new Result(
                    Decision.STATION_PASSED,
                    items,
                    "Your train has already reached " + mealStation
                            + " - please start a new order");
        }

        return new Result(Decision.RESUME, items, null);
    }

    /** Called when the passenger accepts that the cart can't be resumed. */
    public static void discard(Context context) {
        CartStorage.clear(context);
    }
}
