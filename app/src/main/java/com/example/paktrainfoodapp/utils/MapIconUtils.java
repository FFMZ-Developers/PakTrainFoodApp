package com.example.paktrainfoodapp.utils;

import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

/**
 * Small helpers for the order-tracking map (Module 2):
 *
 *  - vectorToBitmapDescriptor: Google Maps markers can't take a vector
 *    drawable resource id directly (only bitmaps), so this rasterizes a
 *    vector drawable (e.g. a train icon, a station pin) onto a bitmap once
 *    and hands back a BitmapDescriptor to use as a marker icon.
 *
 *  - animateMarkerTo: moves a marker smoothly from its current position to
 *    a new one over ~1 second instead of jumping instantly, so the train
 *    icon appears to glide along the track on every location update.
 */
public class MapIconUtils {

    public static BitmapDescriptor vectorToBitmapDescriptor(
            Context context, @DrawableRes int drawableResId, int sizeDp) {

        Drawable drawable = ContextCompat.getDrawable(context, drawableResId);

        if (drawable == null) {
            return BitmapDescriptorFactory.defaultMarker();
        }

        float density = context.getResources().getDisplayMetrics().density;
        int sizePx = Math.round(sizeDp * density);

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    /**
     * Same as vectorToBitmapDescriptor, but bakes the station's name as a
     * small text label directly above the icon into the same bitmap - so
     * every station along the route can be identified on the map at a
     * glance, without needing to tap each marker to open an info window.
     */
    public static BitmapDescriptor labeledVectorToBitmapDescriptor(
            Context context, @DrawableRes int drawableResId, int iconSizeDp, String label) {

        Drawable drawable = ContextCompat.getDrawable(context, drawableResId);

        if (drawable == null) {
            return BitmapDescriptorFactory.defaultMarker();
        }

        float density = context.getResources().getDisplayMetrics().density;
        int iconSizePx = Math.round(iconSizeDp * density);

        android.graphics.Paint textPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(android.graphics.Color.parseColor("#212121"));
        textPaint.setTextSize(11 * density);
        textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        android.graphics.Paint bgPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(android.graphics.Color.parseColor("#F0FFFFFF"));

        String safeLabel = label == null ? "" : label;

        int textWidth = (int) Math.ceil(textPaint.measureText(safeLabel)) + (int) (12 * density);
        int labelHeight = (int) (16 * density);

        int bitmapWidth = Math.max(iconSizePx, textWidth);
        int bitmapHeight = iconSizePx + labelHeight + (int) (2 * density);

        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Label pill background, drawn above the icon.
        android.graphics.RectF labelRect = new android.graphics.RectF(
                (bitmapWidth - textWidth) / 2f, 0, (bitmapWidth + textWidth) / 2f, labelHeight);
        canvas.drawRoundRect(labelRect, 8 * density, 8 * density, bgPaint);

        canvas.drawText(
                safeLabel,
                bitmapWidth / 2f,
                labelHeight - (4 * density),
                textPaint);

        // Icon, centered below the label.
        int iconLeft = (bitmapWidth - iconSizePx) / 2;
        int iconTop = labelHeight + (int) (2 * density);

        drawable.setBounds(iconLeft, iconTop, iconLeft + iconSizePx, iconTop + iconSizePx);
        drawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private static final TypeEvaluator<LatLng> LATLNG_EVALUATOR = (fraction, startValue, endValue) -> {
        double lat = startValue.latitude + (endValue.latitude - startValue.latitude) * fraction;
        double lng = startValue.longitude + (endValue.longitude - startValue.longitude) * fraction;
        return new LatLng(lat, lng);
    };

    /**
     * Glides the marker from its current position to {@code newPosition}
     * over durationMs, instead of an instant jump. Safe to call repeatedly
     * on every location update - each call just animates from wherever the
     * marker currently is.
     */
    public static void animateMarkerTo(Marker marker, LatLng newPosition, long durationMs) {

        if (marker == null || newPosition == null) return;

        LatLng startPosition = marker.getPosition();

        if (startPosition == null
                || (startPosition.latitude == newPosition.latitude
                    && startPosition.longitude == newPosition.longitude)) {
            marker.setPosition(newPosition);
            return;
        }

        ValueAnimator animator = ValueAnimator.ofObject(LATLNG_EVALUATOR, startPosition, newPosition);
        animator.setDuration(durationMs);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            try {
                marker.setPosition((LatLng) animation.getAnimatedValue());
            } catch (Exception ignored) {
                // Marker/map may have been torn down mid-animation (fragment
                // destroyed) - safe to just stop updating it.
            }
        });
        animator.start();
    }
}
