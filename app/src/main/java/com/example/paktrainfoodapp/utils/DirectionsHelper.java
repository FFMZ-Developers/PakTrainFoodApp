package com.example.paktrainfoodapp.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Google Directions API wrapper - gives a real driving route (the actual
 * road polyline, not a straight line) plus a real travel duration, the
 * same numbers Google Maps itself would show for a bike/car trip.
 *
 * Used by all three live-tracking screens:
 *   - rider:      their own position -> restaurant, then restaurant -> station
 *   - restaurant: watching the assigned rider approach
 *   - passenger:  watching the rider bring their food to the station
 *
 * The API key is read from the same manifest meta-data entry the Maps SDK
 * uses (com.google.android.geo.API_KEY), so there's only one key to manage.
 * That key needs BOTH "Maps SDK for Android" and "Directions API" enabled
 * in Google Cloud Console.
 */
public class DirectionsHelper {

    private static final String TAG = "DirectionsHelper";

    private static final OkHttpClient client = new OkHttpClient();

    public static class RouteResult {

        /** Decoded road polyline, ready to hand to a Google Map Polyline. */
        public final List<LatLng> points;

        /** Human-readable travel time, e.g. "12 mins" - straight from Google. */
        public final String durationText;

        /** Travel time in seconds, for any arithmetic (e.g. ETA maths). */
        public final int durationSeconds;

        /** Human-readable distance, e.g. "3.4 km". */
        public final String distanceText;

        RouteResult(List<LatLng> points, String durationText, int durationSeconds, String distanceText) {
            this.points = points;
            this.durationText = durationText;
            this.durationSeconds = durationSeconds;
            this.distanceText = distanceText;
        }
    }

    public interface Callback {
        void onRouteReady(RouteResult result);
        void onFailed(String reason);
    }

    /**
     * @param mode "driving" for a rider on a bike (Google has no dedicated
     *             motorbike mode in the Directions API; driving is the
     *             closest match and what delivery apps generally use).
     */
    public static void getRoute(Context context, LatLng origin, LatLng destination,
                                String mode, Callback callback) {

        if (origin == null || destination == null) {
            callback.onFailed("Missing start or end point");
            return;
        }

        String apiKey = readApiKey(context);

        if (apiKey == null) {
            callback.onFailed("Maps API key not found in manifest");
            return;
        }

        String url = "https://maps.googleapis.com/maps/api/directions/json"
                + "?origin=" + origin.latitude + "," + origin.longitude
                + "&destination=" + destination.latitude + "," + destination.longitude
                + "&mode=" + (mode == null ? "driving" : mode)
                + "&key=" + apiKey;

        Handler main = new Handler(Looper.getMainLooper());

        Executors.newSingleThreadExecutor().execute(() -> {

            try {

                Request request = new Request.Builder().url(url).build();

                try (Response response = client.newCall(request).execute()) {

                    if (response.body() == null) {
                        main.post(() -> callback.onFailed("Empty response from Directions API"));
                        return;
                    }

                    JSONObject json = new JSONObject(response.body().string());

                    String status = json.optString("status");

                    if (!"OK".equals(status)) {

                        // Surfaced rather than swallowed - "REQUEST_DENIED"
                        // here almost always means the Directions API isn't
                        // enabled for this key, which is otherwise a very
                        // confusing silent failure.
                        String message = json.optString("error_message", status);
                        Log.e(TAG, "Directions API returned " + status + ": " + message);
                        main.post(() -> callback.onFailed(message));
                        return;
                    }

                    JSONArray routes = json.getJSONArray("routes");

                    if (routes.length() == 0) {
                        main.post(() -> callback.onFailed("No route found"));
                        return;
                    }

                    JSONObject route = routes.getJSONObject(0);

                    String encoded = route.getJSONObject("overview_polyline").getString("points");

                    JSONObject leg = route.getJSONArray("legs").getJSONObject(0);

                    String durationText = leg.getJSONObject("duration").getString("text");
                    int durationSeconds = leg.getJSONObject("duration").getInt("value");
                    String distanceText = leg.getJSONObject("distance").getString("text");

                    List<LatLng> points = decodePolyline(encoded);

                    RouteResult result = new RouteResult(points, durationText, durationSeconds, distanceText);

                    main.post(() -> callback.onRouteReady(result));
                }

            } catch (IOException e) {
                main.post(() -> callback.onFailed("Network error: " + e.getMessage()));
            } catch (Exception e) {
                main.post(() -> callback.onFailed("Could not read route: " + e.getMessage()));
            }
        });
    }

    private static String readApiKey(Context context) {

        try {

            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);

            return info.metaData != null
                    ? info.metaData.getString("com.google.android.geo.API_KEY")
                    : null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Google's encoded-polyline format -> plain lat/lng list. Implemented
     * here rather than pulling in the whole maps-utils library just for
     * this one function.
     */
    public static List<LatLng> decodePolyline(String encoded) {

        List<LatLng> poly = new ArrayList<>();

        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {

            int b, shift = 0, result = 0;

            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);

            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;

            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);

            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new LatLng(lat / 1E5, lng / 1E5));
        }

        return poly;
    }
}
