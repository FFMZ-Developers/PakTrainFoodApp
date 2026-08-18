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

        String nearest = null;

        float minDistance = Float.MAX_VALUE;

        for(Map.Entry<String,LatLng> entry : stationMap.entrySet()){

            LatLng point = entry.getValue();

            float[] result = new float[1];

            Location.distanceBetween(

                    currentLat,
                    currentLng,

                    point.latitude,
                    point.longitude,

                    result

            );

            if(result[0] < minDistance){

                minDistance = result[0];

                nearest = entry.getKey();

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
