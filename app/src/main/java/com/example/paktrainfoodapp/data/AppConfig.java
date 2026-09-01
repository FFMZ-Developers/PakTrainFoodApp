package com.example.paktrainfoodapp.data;

import android.content.Context;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the admin-configurable thresholds used across the order pipeline
 * (rider search radius, ETA/dispatch timing, reliability strike limits,
 * etc.) from Firestore's Settings/orderConfig document.
 *
 * These values must never be hardcoded elsewhere in the app - every module
 * that needs one of these numbers should call this class instead, so an
 * admin can tune them from the admin panel without shipping a new release.
 * See functions/settings/defaultOrderConfig.js on the backend for the
 * matching default values and field names - the two must stay in sync.
 *
 * Usage: call AppConfig.init(context) once at app start (already wired
 * into MyApplication), then read values anywhere with
 * AppConfig.get().getOrderDispatchEtaThresholdMinutes() etc. A real-time
 * Firestore listener keeps the cached values current, so an admin change
 * takes effect without the app needing to be restarted.
 */
public class AppConfig {

    private static AppConfig instance;

    // --- Defaults mirror functions/settings/defaultOrderConfig.js.
    // These are ONLY used until the first real Firestore snapshot arrives,
    // or if a field is missing from the document for any reason - never
    // relied on as the "real" values in normal operation. ---

    private List<Integer> riderSearchRadiiKm = defaultRadii();
    private int riderSearchStepDelaySeconds = 5;
    private int riderSearchTimeoutSeconds = 240;

    private int orderDispatchEtaThresholdMinutes = 60;
    private int riderTransitBufferMinutes = 25;

    private double fallbackTrainSpeedKmph = 70;

    private int restaurantReliabilityStrikeLimit = 3;
    private int restaurantReliabilityWindowDays = 30;
    private int riderReliabilityStrikeLimit = 3;
    private int riderReliabilityWindowDays = 30;
    private int reliabilityStartingScore = 100;
    private int reliabilityStrikePenalty = 15;
    private int reliabilityCompletionBonus = 2;

    private int riderAttemptedDeliveryFeePercent = 40;

    private int journeyStallMinutesBeforeCancel = 12;

    // Sensible fallback list until the first real Firestore snapshot arrives -
    // mirrors functions/defaultOrderConfig.js's default "cities" array.
    private List<String> cities = defaultCities();

    private ListenerRegistration listenerRegistration;
    private boolean firstSnapshotReceived = false;

    private AppConfig() { }

    public static synchronized void init(Context context) {

        if (instance != null) return; // already initialised

        instance = new AppConfig();
        instance.startListening(context.getApplicationContext());
    }

    public static AppConfig get() {

        if (instance == null) {
            // Defensive fallback: if something reads config before init()
            // ran (shouldn't happen once wired into MyApplication), hand
            // back a defaults-only instance rather than crashing.
            instance = new AppConfig();
        }

        return instance;
    }

    private static List<Integer> defaultRadii() {
        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        list.add(5);
        return list;
    }

    private static List<String> defaultCities() {
        List<String> list = new ArrayList<>();
        String[] defaults = {
                "Karachi", "Lahore", "Islamabad", "Rawalpindi", "Faisalabad",
                "Multan", "Peshawar", "Quetta", "Sialkot", "Gujranwala",
                "Hyderabad", "Bahawalpur", "Sargodha", "Sukkur", "Larkana",
                "Sheikhupura", "Rahim Yar Khan", "Jhang", "Dera Ghazi Khan", "Gujrat",
                "Sahiwal", "Mardan", "Kasur", "Okara", "Mingora",
                "Nawabshah", "Chiniot", "Kotri", "Hafizabad", "Mandi Bahauddin",
                "Jhelum", "Khanewal", "Muzaffargarh", "Vehari", "Abbottabad",
                "Muridke", "Kohat", "Sadiqabad", "Burewala", "Jacobabad"
        };
        for (String city : defaults) list.add(city);
        return list;
    }

    private void startListening(Context appContext) {

        listenerRegistration = FirebaseFirestore.getInstance()
                .collection("Settings")
                .document("orderConfig")
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null || !snapshot.exists()) {
                        // Keep whatever values are already cached (defaults,
                        // or the last good snapshot) - never null out config
                        // just because of a transient read error.
                        return;
                    }

                    applySnapshot(snapshot);
                    firstSnapshotReceived = true;
                });
    }

    private void applySnapshot(DocumentSnapshot snapshot) {

        riderSearchRadiiKm = readIntList(snapshot, "riderSearchRadiiKm", riderSearchRadiiKm);
        riderSearchStepDelaySeconds = readInt(snapshot, "riderSearchStepDelaySeconds", riderSearchStepDelaySeconds);
        riderSearchTimeoutSeconds = readInt(snapshot, "riderSearchTimeoutSeconds", riderSearchTimeoutSeconds);

        orderDispatchEtaThresholdMinutes = readInt(snapshot, "orderDispatchEtaThresholdMinutes", orderDispatchEtaThresholdMinutes);
        riderTransitBufferMinutes = readInt(snapshot, "riderTransitBufferMinutes", riderTransitBufferMinutes);

        fallbackTrainSpeedKmph = readDouble(snapshot, "fallbackTrainSpeedKmph", fallbackTrainSpeedKmph);

        restaurantReliabilityStrikeLimit = readInt(snapshot, "restaurantReliabilityStrikeLimit", restaurantReliabilityStrikeLimit);
        restaurantReliabilityWindowDays = readInt(snapshot, "restaurantReliabilityWindowDays", restaurantReliabilityWindowDays);
        riderReliabilityStrikeLimit = readInt(snapshot, "riderReliabilityStrikeLimit", riderReliabilityStrikeLimit);
        riderReliabilityWindowDays = readInt(snapshot, "riderReliabilityWindowDays", riderReliabilityWindowDays);
        reliabilityStartingScore = readInt(snapshot, "reliabilityStartingScore", reliabilityStartingScore);
        reliabilityStrikePenalty = readInt(snapshot, "reliabilityStrikePenalty", reliabilityStrikePenalty);
        reliabilityCompletionBonus = readInt(snapshot, "reliabilityCompletionBonus", reliabilityCompletionBonus);

        riderAttemptedDeliveryFeePercent = readInt(snapshot, "riderAttemptedDeliveryFeePercent", riderAttemptedDeliveryFeePercent);

        journeyStallMinutesBeforeCancel = readInt(snapshot, "journeyStallMinutesBeforeCancel", journeyStallMinutesBeforeCancel);

        cities = readStringList(snapshot, "cities", cities);
    }

    // ---- small typed-read helpers, all tolerant of a missing/odd field ----

    private static int readInt(DocumentSnapshot s, String field, int fallback) {
        Long v = s.getLong(field);
        return v != null ? v.intValue() : fallback;
    }

    private static double readDouble(DocumentSnapshot s, String field, double fallback) {
        Double v = s.getDouble(field);
        return v != null ? v : fallback;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> readIntList(DocumentSnapshot s, String field, List<Integer> fallback) {

        Object raw = s.get(field);

        if (!(raw instanceof List)) return fallback;

        List<Integer> result = new ArrayList<>();

        for (Object item : (List<Object>) raw) {
            if (item instanceof Number) {
                result.add(((Number) item).intValue());
            }
        }

        return result.isEmpty() ? fallback : result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readStringList(DocumentSnapshot s, String field, List<String> fallback) {

        Object raw = s.get(field);

        if (!(raw instanceof List)) return fallback;

        List<String> result = new ArrayList<>();

        for (Object item : (List<Object>) raw) {
            if (item instanceof String && !((String) item).trim().isEmpty()) {
                result.add((String) item);
            }
        }

        return result.isEmpty() ? fallback : result;
    }

    // ---------------------------- getters ----------------------------

    public List<String> getCities() { return cities; }

    public List<Integer> getRiderSearchRadiiKm() { return riderSearchRadiiKm; }
    public int getRiderSearchStepDelaySeconds() { return riderSearchStepDelaySeconds; }
    public int getRiderSearchTimeoutSeconds() { return riderSearchTimeoutSeconds; }

    public int getOrderDispatchEtaThresholdMinutes() { return orderDispatchEtaThresholdMinutes; }
    public int getRiderTransitBufferMinutes() { return riderTransitBufferMinutes; }

    public double getFallbackTrainSpeedKmph() { return fallbackTrainSpeedKmph; }

    public int getRestaurantReliabilityStrikeLimit() { return restaurantReliabilityStrikeLimit; }
    public int getRestaurantReliabilityWindowDays() { return restaurantReliabilityWindowDays; }
    public int getRiderReliabilityStrikeLimit() { return riderReliabilityStrikeLimit; }
    public int getRiderReliabilityWindowDays() { return riderReliabilityWindowDays; }
    public int getReliabilityStartingScore() { return reliabilityStartingScore; }
    public int getReliabilityStrikePenalty() { return reliabilityStrikePenalty; }
    public int getReliabilityCompletionBonus() { return reliabilityCompletionBonus; }

    public int getRiderAttemptedDeliveryFeePercent() { return riderAttemptedDeliveryFeePercent; }

    public int getJourneyStallMinutesBeforeCancel() { return journeyStallMinutesBeforeCancel; }

    /** True once at least one real snapshot has been applied (vs still on hardcoded defaults). */
    public boolean isLoaded() { return firstSnapshotReceived; }

    /** Call from Application.onTerminate() equivalents if ever needed; not required for normal app lifetime. */
    public void stopListening() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }
}
