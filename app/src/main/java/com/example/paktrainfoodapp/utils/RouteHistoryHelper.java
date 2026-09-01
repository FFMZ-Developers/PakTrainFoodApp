package com.example.paktrainfoodapp.utils;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Read-side counterpart of functions/utils/routeHistoryHelper.js.
 *
 * The backend (onOrderCompleted trigger) is the ONLY thing that writes to
 * the RouteHistory collection - every completed order saves the actual
 * travel time it took between every pair of stations it passed through,
 * keeping only the last 5 samples per pair and their average (see the
 * backend file for the full explanation).
 *
 * The app only ever reads from here, to blend a "this segment usually takes
 * ~X minutes" figure into the live GPS-based ETA (see EtaCalculator).
 *
 * Doc id format MUST match the backend exactly: "{fromStation}__{toStation}"
 * (double underscore, exact station name strings - same ones used as
 * document ids under RailwaySystem/main/Stations).
 */
public class RouteHistoryHelper {

    public interface OnAverageReady {
        /** minutes is null if no history exists yet for this segment. */
        void onResult(@Nullable Double averageMinutes, int sampleCount);
    }

    private static String segmentDocId(String fromStation, String toStation) {
        return fromStation.trim() + "__" + toStation.trim();
    }

    public static void getAverageMinutes(String fromStation, String toStation, OnAverageReady callback) {

        if (fromStation == null || toStation == null
                || fromStation.trim().isEmpty() || toStation.trim().isEmpty()
                || fromStation.equalsIgnoreCase(toStation)) {

            callback.onResult(null, 0);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("RouteHistory")
                .document(segmentDocId(fromStation, toStation))
                .get()
                .addOnSuccessListener(doc -> {

                    if (doc == null || !doc.exists()) {
                        callback.onResult(null, 0);
                        return;
                    }

                    Double avg = doc.getDouble("averageMinutes");
                    Long count = doc.getLong("sampleCount");

                    callback.onResult(avg, count != null ? count.intValue() : 0);
                })
                .addOnFailureListener(e -> callback.onResult(null, 0));
    }
}
