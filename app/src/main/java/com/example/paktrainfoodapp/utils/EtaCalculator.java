package com.example.paktrainfoodapp.utils;

import android.location.Location;

import com.example.paktrainfoodapp.data.AppConfig;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Module 2 - ETA engine.
 *
 * Combines three signals, in priority order:
 *
 *   1. LIVE GPS SPEED - a rolling average of the last few GPS fixes gives a
 *      smoothed current speed, used with the remaining distance along the
 *      route polyline (not a straight line) to the meal station.
 *
 *   2. HISTORICAL AVERAGE - the last-5-orders average travel time for the
 *      exact "current nearest station -> meal station" segment (see
 *      RouteHistoryHelper / functions/utils/routeHistoryHelper.js). Blended
 *      in for stability - live GPS speed is noisy (train stops at stations,
 *      slows on curves, etc.), the historical figure smooths that out.
 *
 *   3. FALLBACK CONFIGURED SPEED - AppConfig.get().getFallbackTrainSpeedKmph()
 *      (Module 0 setting, admin-tunable). Used when we don't have enough
 *      GPS samples yet (e.g. just after boarding) and no history exists
 *      for this segment either.
 *
 * Usage: create one instance per order-tracking screen, feed it GPS fixes
 * and (optionally) a historical average, then call computeEtaMinutes()
 * whenever you want a fresh number - typically on every GPS update.
 */
public class EtaCalculator {

    private static final int MAX_GPS_SAMPLES = 8;
    private static final float MIN_RELIABLE_SPEED_KMPH = 4f; // below this, treat as "stopped" and don't trust it

    private static class Fix {
        final double lat, lng;
        final long timeMs;
        Fix(double lat, double lng, long timeMs) { this.lat = lat; this.lng = lng; this.timeMs = timeMs; }
    }

    private final Deque<Fix> recentFixes = new ArrayDeque<>();

    // Ordered polyline points from boarding station to meal station.
    private List<LatLng> routePoints;

    // Historical average (minutes) for "current nearest station -> meal station".
    // Set externally by the fragment whenever the nearest station changes.
    private Double historicalMinutesForRemainingSegment = null;

    // Total distance (km) that historicalMinutesForRemainingSegment covers -
    // needed so we can scale it if the current position is partway between
    // two stations rather than exactly at one.
    private double historicalSegmentDistanceKm = 0;

    public void setRoute(List<LatLng> orderedRoutePoints) {
        this.routePoints = orderedRoutePoints;
    }

    public void setHistoricalAverage(Double minutes, double segmentDistanceKm) {
        this.historicalMinutesForRemainingSegment = minutes;
        this.historicalSegmentDistanceKm = segmentDistanceKm;
    }

    /** Feed a new GPS fix in. Call this every time a new location arrives. */
    public void recordGpsSample(double lat, double lng, long timeMs) {

        recentFixes.addLast(new Fix(lat, lng, timeMs));

        while (recentFixes.size() > MAX_GPS_SAMPLES) {
            recentFixes.removeFirst();
        }
    }

    public static class Result {
        public final int etaMinutes;
        public final boolean usedLiveSpeed;
        public final boolean usedHistory;

        Result(int etaMinutes, boolean usedLiveSpeed, boolean usedHistory) {
            this.etaMinutes = etaMinutes;
            this.usedLiveSpeed = usedLiveSpeed;
            this.usedHistory = usedHistory;
        }
    }

    /**
     * @param currentPos latest known position (train / passenger)
     * @return null if we don't have enough info yet to say anything (no
     *         route, or route has no points)
     */
    public Result computeEtaMinutes(LatLng currentPos) {

        if (currentPos == null || routePoints == null || routePoints.isEmpty()) {
            return null;
        }

        double remainingKm = remainingDistanceAlongRouteKm(currentPos);

        Float smoothedSpeedKmph = smoothedSpeedKmph();

        boolean liveReliable = smoothedSpeedKmph != null && smoothedSpeedKmph >= MIN_RELIABLE_SPEED_KMPH;

        double liveEtaMinutes = liveReliable
                ? (remainingKm / smoothedSpeedKmph) * 60.0
                : -1;

        Double historicalEtaMinutes = null;

        if (historicalMinutesForRemainingSegment != null && historicalSegmentDistanceKm > 0) {
            // Scale the historical full-segment average down to however much
            // of that segment is actually still remaining right now.
            double fraction = Math.min(1.0, remainingKm / historicalSegmentDistanceKm);
            historicalEtaMinutes = historicalMinutesForRemainingSegment * fraction;
        }

        double fallbackEtaMinutes =
                (remainingKm / AppConfig.get().getFallbackTrainSpeedKmph()) * 60.0;

        double finalMinutes;
        boolean usedLive = false;
        boolean usedHistory = false;

        if (liveReliable && historicalEtaMinutes != null) {

            // Blend: live GPS speed is more "right now", but noisy - lean on
            // it more (65%) while letting history (35%) smooth out sudden
            // swings (a station stop, a slow curve, etc.).
            finalMinutes = (liveEtaMinutes * 0.65) + (historicalEtaMinutes * 0.35);
            usedLive = true;
            usedHistory = true;

        } else if (liveReliable) {

            finalMinutes = liveEtaMinutes;
            usedLive = true;

        } else if (historicalEtaMinutes != null) {

            // No trustworthy live speed right now (just boarded, or train
            // stopped at a station) - history is a much better guess than
            // the flat fallback speed.
            finalMinutes = historicalEtaMinutes;
            usedHistory = true;

        } else {

            finalMinutes = fallbackEtaMinutes;
        }

        int minutes = Math.max(0, (int) Math.round(finalMinutes));

        return new Result(minutes, usedLive, usedHistory);
    }

    // =========================================================
    // Rolling GPS speed
    // =========================================================

    private Float smoothedSpeedKmph() {

        if (recentFixes.size() < 2) return null;

        Fix[] fixes = recentFixes.toArray(new Fix[0]);

        double totalDistanceMeters = 0;
        long totalTimeMs = 0;

        for (int i = 1; i < fixes.length; i++) {

            Fix a = fixes[i - 1];
            Fix b = fixes[i];

            long dt = b.timeMs - a.timeMs;
            if (dt <= 0) continue;

            float[] d = new float[1];
            Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, d);

            totalDistanceMeters += d[0];
            totalTimeMs += dt;
        }

        if (totalTimeMs <= 0) return null;

        double metersPerMs = totalDistanceMeters / totalTimeMs;
        double kmph = metersPerMs * 3600000.0 / 1000.0;

        return (float) kmph;
    }

    // =========================================================
    // Remaining distance along the route polyline (not straight-line)
    // =========================================================

    /**
     * Finds the route segment closest to currentPos, then adds up: distance
     * from currentPos to the end of that segment + every remaining segment's
     * full length. Much closer to the real remaining distance than a
     * straight line to the meal station, especially on a winding track.
     */
    private double remainingDistanceAlongRouteKm(LatLng currentPos) {

        if (routePoints.size() == 1) {
            return distanceKm(currentPos, routePoints.get(0));
        }

        int bestSegmentEndIndex = 1;
        double bestDistanceToSegmentMeters = Double.MAX_VALUE;

        for (int i = 0; i < routePoints.size() - 1; i++) {

            double d = distancePointToSegmentMeters(
                    currentPos, routePoints.get(i), routePoints.get(i + 1));

            if (d < bestDistanceToSegmentMeters) {
                bestDistanceToSegmentMeters = d;
                bestSegmentEndIndex = i + 1;
            }
        }

        double remainingKm = distanceKm(currentPos, routePoints.get(bestSegmentEndIndex));

        for (int i = bestSegmentEndIndex; i < routePoints.size() - 1; i++) {
            remainingKm += distanceKm(routePoints.get(i), routePoints.get(i + 1));
        }

        return remainingKm;
    }

    private static double distanceKm(LatLng a, LatLng b) {
        float[] d = new float[1];
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, d);
        return d[0] / 1000.0;
    }

    /** Approximate point-to-segment distance in meters (good enough at rail scale, avoids heavy projection math). */
    private static double distancePointToSegmentMeters(LatLng p, LatLng segA, LatLng segB) {

        float[] toA = new float[1];
        float[] toB = new float[1];

        Location.distanceBetween(p.latitude, p.longitude, segA.latitude, segA.longitude, toA);
        Location.distanceBetween(p.latitude, p.longitude, segB.latitude, segB.longitude, toB);

        return Math.min(toA[0], toB[0]);
    }
}
