package com.example.paktrainfoodapp.ui.main.Passenger.home;


import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import java.util.HashMap;
import java.util.Map;

public class StationValidationHelper {

    private static final Map<String, LatLng> stationMap =
            new HashMap<>();

    public static void clearCache(){

        stationMap.clear();

    }
    public static void addStation(
            String stationName,
            double lat,
            double lng){

        stationMap.put(
                stationName,
                new LatLng(lat,lng)
        );

    }

    public static String getNearestStation(
            double currentLat,
            double currentLng){

        return getNearestStation(currentLat, currentLng, stationMap.keySet());
    }

    /**
     * ✅ FIX: the version above searches EVERY station in the whole
     * railway system - fine for trains with several stops along a route,
     * but for a DIRECT/express train (e.g. Rawalpindi -> Lahore with no
     * intermediate stops), a passenger physically mid-journey (nowhere
     * near either endpoint) would resolve to whichever RANDOM station
     * elsewhere in the country happens to be geographically closest -
     * which isn't even on this route. canOrder() then couldn't find that
     * station in the route list, treated it as "already crossed", and
     * blocked ordering with a false "you've crossed the meal station"
     * toast - even mid-journey, nowhere near arriving.
     *
     * This overload only searches among the given route's OWN stations,
     * so it always resolves to a station that's actually on this train's
     * route - correctly picking the nearer of the two endpoints for a
     * direct/express train, instead of an unrelated station elsewhere.
     */
    public static String getNearestStation(
            double currentLat,
            double currentLng,
            java.util.Collection<String> allowedStations){

        String nearest = null;

        float minDistance = Float.MAX_VALUE;

        for (String stationName : allowedStations) {

            LatLng point = stationMap.get(stationName);

            if (point == null) continue;

            float[] result = new float[1];

            Location.distanceBetween(
                    currentLat,
                    currentLng,
                    point.latitude,
                    point.longitude,
                    result
            );

            if (result[0] < minDistance) {

                minDistance = result[0];

                nearest = stationName;

            }

        }

        return nearest;

    }
    public static boolean canOrder(
            java.util.List<String> route,
            String currentStation,
            String mealStation) {

        if (route == null || route.isEmpty()) {
            return false;
        }

        int currentIndex = route.indexOf(currentStation);
        int mealIndex = route.indexOf(mealStation);

        if (currentIndex == -1 || mealIndex == -1) {
            return false;
        }

        // Order sirf meal station se pehle allow hoga
        return currentIndex < mealIndex;
    }
    public static boolean isMealStationCrossed(
            java.util.List<String> route,
            String currentStation,
            String mealStation) {

        int currentIndex = route.indexOf(currentStation);
        int mealIndex = route.indexOf(mealStation);

        if (currentIndex == -1 || mealIndex == -1) {
            return true;
        }

        return currentIndex >= mealIndex;
    }
    public static boolean shouldClearCart(
            java.util.List<String> route,
            String currentStation,
            String mealStation) {

        return isMealStationCrossed(
                route,
                currentStation,
                mealStation
        );
    }

}
