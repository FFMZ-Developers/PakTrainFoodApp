package com.example.paktrainfoodapp.utils;

import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/**
 * Renders an order status as a coloured pill instead of a disabled button.
 *
 * A lot of these statuses aren't actions at all - "Waiting for Rider",
 * "Rider On The Way", "Delivered" are just *information*. Showing them as
 * greyed-out buttons invited people to keep tapping something that would
 * never respond. A badge reads as a state, which is what it actually is,
 * and the colour makes the list scannable at a glance:
 *
 *   amber  - waiting on someone else
 *   blue   - in motion right now
 *   green  - done / good
 *   red    - failed / cancelled
 */
public class StatusBadge {

    private static final int AMBER = 0xFFEF6C00;
    private static final int BLUE  = 0xFF1565C0;
    private static final int GREEN = 0xFF2E7D32;
    private static final int RED   = 0xFFC62828;
    private static final int GREY  = 0xFF616161;

    /** Plain-language label for a raw status key. */
    public static String label(String status) {

        if (status == null) return "Unknown";

        switch (status) {
            case "Active": return "Awaiting Restaurant";
            case "Accepted": return "Preparing";
            case "ready_for_delivery": return "Waiting for Rider";
            case "accepted_by_rider": return "Rider Assigned";
            case "arrive_rider_at_resturent": return "Rider Arrived";
            case "dropped": return "Handed to Rider";
            case "pick_up": return "On The Way";
            case "completed": return "Delivered";
            case "Cancelled": return "Cancelled";
            case "Rejected": return "Rejected";
            case "delivery_failed": return "Delivery Failed";
            case "disputed": return "Under Review";
            default: return status;
        }
    }

    private static int colour(String status) {

        if (status == null) return GREY;

        switch (status) {
            case "Active":
            case "ready_for_delivery":
                return AMBER;

            case "Accepted":
            case "accepted_by_rider":
            case "arrive_rider_at_resturent":
            case "dropped":
            case "pick_up":
                return BLUE;

            case "completed":
                return GREEN;

            case "Cancelled":
            case "Rejected":
            case "delivery_failed":
                return RED;

            case "disputed":
                return AMBER;

            default:
                return GREY;
        }
    }

    /**
     * Turns any TextView into a status pill. Uses a tinted background with
     * matching darker text (rather than a solid block of colour) so a list
     * of several badges stays calm instead of shouting.
     */
    public static void apply(TextView view, String status) {

        if (view == null) return;

        int c = colour(status);

        view.setText(label(status));
        view.setTextColor(c);
        view.setAllCaps(false);

        GradientDrawable pill = new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);

        float density = view.getResources().getDisplayMetrics().density;
        pill.setCornerRadius(20 * density);

        // 12% tint of the same hue - readable, and works on light backgrounds.
        pill.setColor((c & 0x00FFFFFF) | 0x1F000000);
        pill.setStroke((int) (1 * density), (c & 0x00FFFFFF) | 0x55000000);

        view.setBackground(pill);

        int padH = (int) (14 * density);
        int padV = (int) (6 * density);
        view.setPadding(padH, padV, padH, padV);
    }
}
