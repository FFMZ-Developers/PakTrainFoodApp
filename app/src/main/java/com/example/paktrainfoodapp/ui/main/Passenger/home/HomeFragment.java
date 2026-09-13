package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.Manifest;
import android.location.Location;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.paktrainfoodapp.ui.main.Passenger.home.LocationHelper;
import com.example.paktrainfoodapp.ui.main.Passenger.home.StationValidationHelper;

import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.notification.NotificationRepository;
import com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class HomeFragment extends Fragment {

    private AutoCompleteTextView actvFrom, actvTo, actvTrain, actvMealStation;
    private Button btnNext;
    private TextView tvRoutePreview;

    private FirebaseFirestore db;
    private ActivityResultLauncher<String> locationPermissionLauncher;

    private Location currentLocation;
    private final List<String> allStations = new ArrayList<>();
    private final List<String> trainNames = new ArrayList<>();

    private final Map<String, List<String>> trainRoutes = new HashMap<>();
    private final Map<String, String> trainRouteIds = new HashMap<>();

    public HomeFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_passenger_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupTopNotification(view);

        actvFrom = view.findViewById(R.id.actv_from);
        actvTo = view.findViewById(R.id.actv_to);
        actvTrain = view.findViewById(R.id.actv_train);
        actvMealStation = view.findViewById(R.id.actv_meal_station);
        btnNext = view.findViewById(R.id.btn_next_to_restaurants);
        tvRoutePreview = view.findViewById(R.id.tv_route_preview);

        locationPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        isGranted -> {

                            if (isGranted) {

                                Toast.makeText(
                                        getContext(),
                                        "Location Permission Granted",
                                        Toast.LENGTH_SHORT
                                ).show();

                            } else {

                                Toast.makeText(
                                        getContext(),
                                        "Location Permission Required",
                                        Toast.LENGTH_LONG
                                ).show();
                            }

                        });

        db = FirebaseFirestore.getInstance();

        loadAllData();

        // Dropdowns automation triggers
        actvFrom.setOnClickListener(v -> actvFrom.showDropDown());
        actvTo.setOnClickListener(v -> actvTo.showDropDown());
        actvTrain.setOnClickListener(v -> actvTrain.showDropDown());
        actvMealStation.setOnClickListener(v -> actvMealStation.showDropDown());

        actvFrom.setOnItemClickListener((parent, view1, position, id) -> filterTrains());
        actvTo.setOnItemClickListener((parent, view1, position, id) -> filterTrains());

        actvTrain.setOnItemClickListener((parent, view12, position, id) -> {
            String train = (String) parent.getItemAtPosition(position);
            showRoute(train);
        });

        btnNext.setOnClickListener(v -> {
            if (!LocationHelper.hasPermission(requireActivity())) {

                LocationHelper.requestPermission(
                        locationPermissionLauncher
                );

                return;
            }

            String from = actvFrom.getText().toString().trim();
            String to = actvTo.getText().toString().trim();
            String train = actvTrain.getText().toString().trim();
            String mealStation = actvMealStation.getText().toString().trim();

            if (TextUtils.isEmpty(from) || TextUtils.isEmpty(to)
                    || TextUtils.isEmpty(train) || TextUtils.isEmpty(mealStation)) {
                Toast.makeText(getContext(), "Select From, To, Train and Meal Station", Toast.LENGTH_SHORT).show();
                return;
            }

            String routeId = trainRouteIds.get(train);
            if (routeId == null) {
                Toast.makeText(getContext(), "Route ID missing", Toast.LENGTH_SHORT).show();
                return;
            }

            LocationHelper.getCurrentLocation(
                    requireActivity(),
                    new LocationHelper.LocationCallback() {

                        @Override
                        public void onLocationReceived(Location location) {

                            currentLocation = location;

                            List<String> route =
                                    trainRoutes.get(train);

                            if (route == null) {

                                Toast.makeText(
                                        getContext(),
                                        "Train route not found",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }

                            // ✅ FIX: search only among THIS route's own
                            // stations, not the whole railway system - see
                            // StationValidationHelper.java for why (direct/
                            // express trains with few stops were falsely
                            // blocked mid-journey otherwise).
                            String currentStation =
                                    StationValidationHelper.getNearestStation(
                                            location.getLatitude(),
                                            location.getLongitude(),
                                            route
                                    );

                            boolean canOrder =
                                    StationValidationHelper.canOrder(
                                            route,
                                            currentStation,
                                            mealStation
                                    );

                            if (!canOrder) {

                                Toast.makeText(
                                        getContext(),
                                        "You have already reached or crossed the selected meal station.",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }
                            Passanger_Resturent_list_Fragment fragment = new Passanger_Resturent_list_Fragment();
                            Bundle b = new Bundle();
                            b.putString("selectedCity", mealStation);
                            b.putString("TRAIN_NAME", train);
                            b.putString("TRAIN_ID", train);
                            b.putString("ROUTE_ID", routeId);
                            b.putString("FROM", from);
                            b.putString("TO", to);
                            fragment.setArguments(b);

                            // Loader wrapper handling
                            Fragment parentFrag = getParentFragment();
                            if (parentFrag instanceof Passenger_Fragment_Loader) {
                                ((Passenger_Fragment_Loader) parentFrag).openRestaurantList(fragment);
                            } else {
                                getParentFragmentManager()
                                        .beginTransaction()
                                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                                        .replace(getId(), fragment)
                                        .addToBackStack(null)
                                        .commit();
                            }
                        }

                        @Override
                        public void onLocationFailed(String message) {

                            Toast.makeText(
                                    getContext(),
                                    message,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });


        });
    }

    // ================= LOAD DATA WITH SAFETY =================
    private void loadAllData() {
        db.collection("RailwaySystem")
                .document("main")
                .collection("Stations")
                .get()
                .addOnSuccessListener(stations -> {
                    if (!isAdded() || getContext() == null) return; // Crash guard
                    allStations.clear();
                    for (DocumentSnapshot doc : stations.getDocuments()) {

                        allStations.add(doc.getId());

                        Double lat = doc.getDouble("lat");
                        Double lng = doc.getDouble("lng");

                        if (lat != null && lng != null) {

                            StationValidationHelper.addStation(
                                    doc.getId(),
                                    lat,
                                    lng
                            );

                        }
                    }
                    setupStationAdapter();
                });

        db.collection("RailwaySystem")
                .document("main")
                .collection("Trains")
                .get()
                .addOnSuccessListener(trains -> {
                    if (!isAdded() || getContext() == null) return; // Crash guard
                    trainNames.clear();
                    trainRouteIds.clear();
                    for (DocumentSnapshot doc : trains.getDocuments()) {
                        String name = doc.getString("name");
                        String number = doc.getString("number");
                        String routeId = doc.getString("routeId");

                        if (name == null || routeId == null)
                            continue;

                        String fullName = (number != null) ? name + " (" + number + ")" : name;
                        trainNames.add(fullName);
                        trainRouteIds.put(fullName, routeId);
                    }
                    setupTrainAdapter();
                    preloadRoutes();
                });
    }

    // ================= PRELOAD ROUTES WITH BACKGROUND LOOP PROTECTION =================
    private void preloadRoutes() {
        db.collection("RailwaySystem")
                .document("main")
                .collection("Routes")
                .get()
                .addOnSuccessListener(routes -> {
                    if (!isAdded() || getContext() == null) return; // Crash guard
                    trainRoutes.clear();
                    for (DocumentSnapshot doc : routes.getDocuments()) {
                        String routeId = doc.getId();
                        List<Map<String, Object>> stationMaps = (List<Map<String, Object>>) doc.get("stations");

                        if (stationMaps == null)
                            continue;

                        List<String> stationNames = new ArrayList<>();
                        for (Map<String, Object> stationMap : stationMaps) {
                            String stationName = (String) stationMap.get("name");
                            if (stationName != null) {
                                stationNames.add(stationName);
                            }
                        }

                        for (String train : trainRouteIds.keySet()) {
                            if (routeId.equals(trainRouteIds.get(train))) {
                                trainRoutes.put(train, stationNames);
                            }
                        }
                    }
                });
    }

    private void setupStationAdapter() {
        if (!isAdded() || getContext() == null) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                allStations
        );
        actvFrom.setAdapter(adapter);
        actvTo.setAdapter(adapter);
    }

    private void setupTrainAdapter() {
        if (!isAdded() || getContext() == null) return;
        actvTrain.setAdapter(new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                trainNames
        ));
    }

    private void filterTrains() {
        String from = actvFrom.getText().toString().trim();
        String to = actvTo.getText().toString().trim();

        if (TextUtils.isEmpty(from) || TextUtils.isEmpty(to)) {
            return;
        }

        List<String> filtered = new ArrayList<>();
        for (String train : trainRoutes.keySet()) {
            List<String> route = trainRoutes.get(train);
            if (route == null)
                continue;

            int fromIndex = route.indexOf(from);
            int toIndex = route.indexOf(to);

            if (fromIndex != -1 && toIndex != -1 && fromIndex < toIndex) {
                filtered.add(train);
            }
        }

        if (isAdded() && getContext() != null) {
            actvTrain.setAdapter(new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    filtered
            ));
        }

        actvTrain.setText("");
        actvMealStation.setText("");
        tvRoutePreview.setText("");
    }

    private void showRoute(String train) {
        List<String> route = trainRoutes.get(train);
        if (route == null || route.isEmpty())
            return;

        String from = actvFrom.getText().toString().trim();
        String to = actvTo.getText().toString().trim();

        int fromIndex = route.indexOf(from);
        int toIndex = route.indexOf(to);

        if (fromIndex == -1 || toIndex == -1 || fromIndex > toIndex) {
            return;
        }

        List<String> mealStations = new ArrayList<>(route.subList(fromIndex, toIndex + 1));

        if (isAdded() && getContext() != null) {
            actvMealStation.setAdapter(new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    mealStations
            ));
        }

        tvRoutePreview.setText("Route: " + TextUtils.join(" → ", mealStations));
    }

    // =========================================================
    // TOP-BAR NOTIFICATION BELL
    // =========================================================

    private NotificationRepository homeNotificationRepo;
    private android.widget.TextView txtTopBadge;

    private void setupTopNotification(View view) {

        View bell = view.findViewById(R.id.btnTopNotification);
        txtTopBadge = view.findViewById(R.id.txtTopBadge);

        if (bell == null) return;

        bell.setOnClickListener(v -> {

            Fragment parent = getParentFragment();

            if (parent instanceof com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader) {
                ((com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader) parent)
                        .showNotifications();
            }
        });

        homeNotificationRepo = new NotificationRepository();

        homeNotificationRepo.listenUnreadCount(
                NotificationRepository.ROLE_PASSENGER,
                new NotificationRepository.BadgeCallback() {

                    @Override
                    public void onCountChanged(int count) {

                        if (!isAdded() || txtTopBadge == null) return;

                        requireActivity().runOnUiThread(() -> {

                            if (count > 0) {
                                txtTopBadge.setVisibility(View.VISIBLE);
                                txtTopBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                            } else {
                                txtTopBadge.setVisibility(View.GONE);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Exception e) { }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (homeNotificationRepo != null) {
            homeNotificationRepo.removeListener();
        }
    }
}

