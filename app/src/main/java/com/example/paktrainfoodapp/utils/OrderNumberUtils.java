package com.example.paktrainfoodapp.utils;

import java.util.Locale;

/**
 * Formats an order's human-facing reference.
 *
 * Firestore document IDs are random 20-character strings ("aB3xK9..."),
 * which are fine as database keys but unusable as something a passenger
 * reads out on the phone or a restaurant writes on a bag. Every order now
 * also carries a sequential `orderNumber` (assigned in onOrderPlaced.js),
 * and that's what gets displayed everywhere.
 *
 * Padded to four digits so the list lines up neatly - #0001, #0042,
 * #1000 - and simply keeps growing past four digits when it needs to.
 */
public class OrderNumberUtils {

    /**
     * @param orderNumber the sequential number, or null for orders placed
     *                    before sequential numbering existed
     * @param fallbackId  the Firestore document id, used only when there's
     *                    no sequential number to show
     */
    public static String format(Long orderNumber, String fallbackId) {

        if (orderNumber != null && orderNumber > 0) {
            return "Order #" + String.format(Locale.US, "%04d", orderNumber);
        }

        // Older orders have no number - show a short slice of the id
        // rather than the whole unreadable string.
        if (fallbackId == null || fallbackId.isEmpty()) return "Order";

        return "Order #" + (fallbackId.length() > 6
                ? fallbackId.substring(0, 6).toUpperCase(Locale.US)
                : fallbackId.toUpperCase(Locale.US));
    }

    /** Same value without the "Order " prefix, for tight spaces. */
    public static String formatShort(Long orderNumber, String fallbackId) {
        return format(orderNumber, fallbackId).replace("Order ", "");
    }
}
