package com.example.paktrainfoodapp.ui.main.Restaurant.order;

import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Passenger.MenuitemModel;
import com.example.paktrainfoodapp.ui.main.Passenger.OrderItemsAdapter;
import com.example.paktrainfoodapp.utils.EtaCalculator;
import com.example.paktrainfoodapp.utils.MapIconUtils;
import com.example.paktrainfoodapp.utils.RouteHistoryHelper;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.firebase.database.*;
import com.google.firebase.firestore.*;
import com.google.firebase.firestore.Query;

import java.text.NumberFormat;
import java.util.*;

/**
 * Module 2 - live order tracking map.
 *
 * FIXES in this version:
 *
 *  1. Every station along the route (not just the meal station) now gets
 *     its own labeled marker - a small station icon with its name baked
 *     right above it, so the whole journey is identifiable on the map at a
 *     glance (see MapIconUtils.labeledVectorToBitmapDescriptor).
 *
 *  2. Proper app bar: "Live Order Tracking" title + a working back button
 *     that pops this fragment off the Activity's fragment manager. (The
 *     actual "back always goes to Dashboard" bug was in
 *     restaurant_LoadFragment.java re-opening its default tab every time
 *     its view was recreated - fixed there; this screen's back button just
 *     needs to pop normally now that that's fixed.)
 *
 *  3. Field name fix: this screen used to write the live train ETA to
 *     Orders/{orderId}.etaEndTime - but that field is ALREADY used
 *     elsewhere (AcceptedOrdersFragment's "Ready for Delivery" prep-deadline
 *     countdown timer). Writing to it here was silently corrupting that
 *     countdown. Now writes to a separate field, trainEtaEndTime.
 *
 *  4. Current-station text no longer carries a confusing "(estimated)"
 *     suffix - it's always the actual nearest station to the best position
 *     we currently have (real GPS once available, the boarding station
 *     before that), and updates continuously as the train moves station to
 *     station. Only the ETA number keeps a caveat label, since unlike the
 *     station name, the exact minute figure really is a rougher guess
 *     before live GPS data arrives.
 */
public class OrderDetailFragment extends Fragment implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FirebaseFirestore firestore;
    private DatabaseReference realtimeDb;
    private ValueEventListener locationListener;
    private static final String DB_URL = "https://paktrainfoodservice-default-rtdb.firebaseio.com/";

    private String orderId;
    private final List<String> stations = new ArrayList<>();
    private final List<LatLng> routePoints = new ArrayList<>();

    // ✅ FIX: parallel to routePoints (same index = same station), built by
    // ONLY including stations whose coordinates were actually found. The
    // old code indexed into `stations` directly using routePoints' index -
    // if even ONE station in the middle of the route had no lat/lng in
    // Firestore, routePoints ended up shorter than `stations` and every
    // index after that point pointed at the WRONG station name. That's why
    // "current station" could pick the wrong one - this list guarantees
    // routeStationNames.get(i) is always the correct name for routePoints.get(i).
    private final List<String> routeStationNames = new ArrayList<>();
    private final List<Double> cumulativeDistanceKm = new ArrayList<>();

    private Marker trainMarker;
    private Marker mealMarker;
    private final List<Marker> routeStationMarkers = new ArrayList<>();
    private Polyline polyline;
    private boolean mapReady = false;
    private LatLng currentPos;

    // Module: rider live-tracking layer (see listenRiderLocation()).
    private Marker riderMarker;
    private LatLng riderPos;
    private LatLng restaurantPos;
    private LatLng mealStationPos;
    // Two separate legs, drawn in different colours like Google Maps'
    // multi-stop preview: rider -> restaurant (pickup leg) and
    // restaurant -> station (delivery leg).
    private com.google.android.gms.maps.model.Polyline legToRestaurantPolyline;
    private com.google.android.gms.maps.model.Polyline legToStationPolyline;
    private String legToRestaurantDuration;
    private String legToStationDuration;
    private String legToRestaurantDistance;
    private String legToStationDistance;
    private com.google.firebase.database.DatabaseReference riderLocationRef;
    private ValueEventListener riderLocationListener;
    private boolean riderHasPickedUp = false;
    private TextView txtRiderEta;
    private Button btnNavigate;
    private ImageView btnChat, btnCall;
    private TextView txtContactWith;
    private View layoutContact;

    // Counterparty details for this order, resolved once the order loads.
    private String restaurantNameForChat, restaurantPhoneForChat;
    private String passengerNameForChat, passengerPhoneForChat;
    private String riderPhoneForChat;
    private long lastRouteRefreshAt = 0;
    private boolean staticDataLoaded = false;
    private boolean lastKnownPickedUp = false;
    private String trackedRiderId;
    private ListenerRegistration orderRegistration;

    /** Directions API calls cost money - don't redraw on every GPS tick. */
    private static final long RIDER_ROUTE_REFRESH_INTERVAL_MS = 20 * 1000L;
    private String mealStationName;
    private long lastSavedMinutes = -1;

    private boolean hasReceivedLiveLocation = false;

    private final EtaCalculator etaCalculator = new EtaCalculator();
    private int lastNearestStationIndex = -1;

    private TextView txtEta, txtTrain, txtTotalPrice,
            txtSeat, txtCoach, txtTicket, txtPhone, txtMealStation, txtCurrentStation;

    private RecyclerView recyclerItems;
    private OrderItemsAdapter adapter;
    private final List<MenuitemModel> itemList = new ArrayList<>();

    // Module: order id / status pill (always shown) + the completed-only
    // summary (completed time + read-only chat). See updateTerminalUi().
    private TextView txtDetailOrderId, txtDetailStatusBadge, txtCompletedTime;
    private View mapContainer, layoutLiveTrackingCard, layoutCompletedTime, layoutCompletedChat;
    // The two conversations are kept visually SEPARATE, never merged -
    // a rider was in both, but they're different chats about different
    // phases of the order, so mixing them into one timeline just reads
    // as confusing. Restaurant thread block:
    private View layoutChatRestaurant, dividerChatThreads;
    private TextView txtChatRestaurantTitle, txtChatRestaurantEmpty;
    private RecyclerView recyclerChatRestaurant;
    // Passenger thread block:
    private View layoutChatPassenger;
    private TextView txtChatPassengerTitle, txtChatPassengerEmpty;
    private RecyclerView recyclerChatPassenger;
    private String currentOrderStatus;

    public static OrderDetailFragment newInstance(String orderId) {
        OrderDetailFragment f = new OrderDetailFragment();
        Bundle b = new Bundle();
        b.putString("orderId", orderId);
        f.setArguments(b);
        return f;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_order_detail, container, false);

        // ✅ FIX: every one of these is tied to the VIEW, not to the
        // fragment instance. Opening the chat screen replaces this
        // fragment (keeping the instance on the back stack), so coming
        // back re-runs onCreateView with fresh views while the old flags
        // were still set - staticDataLoaded stayed true, so the entire
        // "train / ticket / total / route / ETA / navigation" block was
        // skipped and the screen came back showing only the map. Resetting
        // here makes a returning view rebuild exactly like a first open.
        staticDataLoaded = false;
        mapReady = false;
        hasReceivedLiveLocation = false;
        lastKnownPickedUp = false;
        trackedRiderId = null;
        lastRouteRefreshAt = 0;
        lastNearestStationIndex = -1;
        lastSavedMinutes = -1;
        currentOrderStatus = null;
        completedChatAdapterRestaurant = null;
        completedChatAdapterPassenger = null;
        completedChatMessagesRestaurant.clear();
        completedChatMessagesPassenger.clear();

        trainMarker = null;
        mealMarker = null;
        riderMarker = null;
        polyline = null;
        legToRestaurantPolyline = null;
        legToStationPolyline = null;
        legToRestaurantDuration = null;
        legToStationDuration = null;
        legToRestaurantDistance = null;
        legToStationDistance = null;
        routeStationMarkers.clear();
        routePoints.clear();
        routeStationNames.clear();
        cumulativeDistanceKm.clear();
        stations.clear();

        txtEta = v.findViewById(R.id.txtEta);
        txtRiderEta = v.findViewById(R.id.txtRiderEta);

        layoutContact = v.findViewById(R.id.layoutContact);
        btnChat = v.findViewById(R.id.btnChat);
        btnCall = v.findViewById(R.id.btnCall);
        txtContactWith = v.findViewById(R.id.txtContactWith);

        btnNavigate = v.findViewById(R.id.btnNavigate);
        if (btnNavigate != null) {
            btnNavigate.setOnClickListener(view -> startNavigation());
        }
        txtTrain = v.findViewById(R.id.txtTrainName);
        txtSeat = v.findViewById(R.id.txtSeatNumber);
        txtCoach = v.findViewById(R.id.txtCoachNumber);
        txtTicket = v.findViewById(R.id.txtTicketNumber);
        txtPhone = v.findViewById(R.id.txtPhone);
        txtMealStation = v.findViewById(R.id.txtMealStation);
        txtCurrentStation = v.findViewById(R.id.txtCurrentStation);
        recyclerItems = v.findViewById(R.id.recyclerItems);
        txtTotalPrice = v.findViewById(R.id.txtTotalPrice);

        txtDetailOrderId = v.findViewById(R.id.txtDetailOrderId);
        txtDetailStatusBadge = v.findViewById(R.id.txtDetailStatusBadge);
        mapContainer = v.findViewById(R.id.mapContainer);
        layoutLiveTrackingCard = v.findViewById(R.id.layoutLiveTrackingCard);
        layoutCompletedTime = v.findViewById(R.id.layoutCompletedTime);
        txtCompletedTime = v.findViewById(R.id.txtCompletedTime);
        layoutCompletedChat = v.findViewById(R.id.layoutCompletedChat);
        layoutChatRestaurant = v.findViewById(R.id.layoutChatRestaurant);
        dividerChatThreads = v.findViewById(R.id.dividerChatThreads);
        txtChatRestaurantTitle = v.findViewById(R.id.txtChatRestaurantTitle);
        txtChatRestaurantEmpty = v.findViewById(R.id.txtChatRestaurantEmpty);
        recyclerChatRestaurant = v.findViewById(R.id.recyclerChatRestaurant);
        layoutChatPassenger = v.findViewById(R.id.layoutChatPassenger);
        txtChatPassengerTitle = v.findViewById(R.id.txtChatPassengerTitle);
        txtChatPassengerEmpty = v.findViewById(R.id.txtChatPassengerEmpty);
        recyclerChatPassenger = v.findViewById(R.id.recyclerChatPassenger);
        if (recyclerChatRestaurant != null) {
            recyclerChatRestaurant.setLayoutManager(new LinearLayoutManager(getContext()));
        }
        if (recyclerChatPassenger != null) {
            recyclerChatPassenger.setLayoutManager(new LinearLayoutManager(getContext()));
        }

        ImageView btnBack = v.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(view -> {
                if (isAdded()) {
                    requireActivity().getSupportFragmentManager().popBackStack();
                }
            });
        }

        recyclerItems.setLayoutManager(new LinearLayoutManager(getContext()));

        firestore = FirebaseFirestore.getInstance();
        realtimeDb = FirebaseDatabase.getInstance(DB_URL).getReference();

        if (getArguments() != null) orderId = getArguments().getString("orderId");

        SupportMapFragment map = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (map != null) map.getMapAsync(this);

        return v;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mapReady = true;
        mMap.setPadding(0, 24, 0, 24);
        mMap.setOnPolylineClickListener(this::onRiderLegClicked);
        loadOrder();
    }

    private void loadOrder() {
        if (orderId == null) return;

        // ✅ FIX: this was a one-time .get(). While this screen stayed open,
        // nothing on it reacted to the order actually progressing - so when
        // the rider tapped "Picked Up", the route never flipped from
        // "heading to restaurant" to "heading to station", the Chat/Call
        // buttons never switched from the restaurant to the passenger, and
        // a rider assigned after the screen opened never appeared at all.
        // A live listener makes the whole screen track in real time, for
        // all three roles.
        orderRegistration = firestore.collection("Orders").document(orderId)
                .addSnapshotListener((doc, error) -> {

            if (!isAdded() || error != null || doc == null || !doc.exists()) return;

            // Status-dependent parts run on EVERY update; the heavy
            // one-time work (items list, route geometry, GPS listener) is
            // guarded below so it isn't rebuilt on each snapshot.
            String status = doc.getString("orderStatus");
            String assignedRiderId = doc.getString("acceptedBy");

            currentOrderStatus = status;
            riderHasPickedUp = "pick_up".equals(status) || "completed".equals(status);

            restaurantNameForChat = doc.getString("restaurantName");
            restaurantPhoneForChat = doc.getString("restaurantPhone");
            passengerNameForChat = doc.getString("passengerName");
            passengerPhoneForChat = doc.getString("phone");
            riderPhoneForChat = doc.getString("riderPhone");

            setupContactButtons(status, assignedRiderId);

            // Order id / status pill (top of screen) + swaps the whole
            // screen between "live tracking" and "completed summary" -
            // see updateTerminalUi().
            updateTerminalUi(status, doc.getLong("orderNumber"), doc.getLong("completedAt"));

            if (assignedRiderId != null && !assignedRiderId.isEmpty()
                    && !assignedRiderId.equals(trackedRiderId)) {

                trackedRiderId = assignedRiderId;
                listenRiderLocation(assignedRiderId);
            }

            // Destination just flipped - bypass the refresh throttle so the
            // new leg is drawn immediately instead of up to 20s later.
            if (riderHasPickedUp != lastKnownPickedUp) {
                lastKnownPickedUp = riderHasPickedUp;
                lastRouteRefreshAt = 0;
                refreshRiderRoute();
            }

            // ✅ FIX: Start Navigation's visibility used to be set ONLY
            // inside updateRiderEtaText() - i.e. only after the Directions
            // API successfully returned a route. If that call was slow,
            // failed, or never fired (no network, quota, etc.) the button
            // just silently never appeared, even for the rider it's meant
            // for. The button itself doesn't need the drawn route at all -
            // it only needs a destination point - so its visibility is now
            // decided independently, right here, every time the order
            // updates.
            updateNavigationButtonVisibility();

            if (staticDataLoaded) return;
            staticDataLoaded = true;

            List<Map<String, Object>> cartItems = (List<Map<String, Object>>) doc.get("cartItems");

            itemList.clear();
            double total = 0;

            if (cartItems != null) {
                for (Map<String, Object> m : cartItems) {

                    MenuitemModel item = new MenuitemModel();
                    item.setName(String.valueOf(m.get("name")));
                    item.setDescription(String.valueOf(m.get("description")));
                    item.setRestaurantName(String.valueOf(m.get("restaurantName")));
                    item.setImageUrl(String.valueOf(m.get("imageUrl")));

                    Object priceObj = m.get("price");
                    double price = 0;
                    if (priceObj != null) {
                        price = Double.parseDouble(priceObj.toString());
                        item.setPrice(price);
                    }

                    Object quantityObj = m.get("quantity");
                    int quantity = 1;
                    if (quantityObj != null) {
                        quantity = Integer.parseInt(quantityObj.toString());
                        item.setQuantity(quantity);
                    }

                    total += price * quantity;
                    itemList.add(item);
                }
            }

            adapter = new OrderItemsAdapter(itemList);
            recyclerItems.setAdapter(adapter);

            txtTotalPrice.setText(formatPrice(total));
            txtTrain.setText("Train: " + doc.getString("trainName"));
            txtSeat.setText("Seat: " + doc.getString("seatNumber"));
            txtCoach.setText("Coach: " + doc.getString("coachNumber"));
            txtTicket.setText("Ticket: " + doc.getString("ticketNumber"));
            txtPhone.setText("Phone: " + doc.getString("phone"));

            mealStationName = doc.getString("mealStation");
            txtMealStation.setText("Meal Station: " + mealStationName);

            txtCurrentStation.setText("Current: Loading...");
            txtEta.setText("ETA: Calculating...");

            String fromStationName = doc.getString("fromStation");

            loadRoute(doc.getString("routeId"), fromStationName, mealStationName);
            listenLocation();

            // Restaurant coordinates are copied onto the order by
            // dispatchRider.js, so no extra lookup is needed here.
            Double rLat = doc.getDouble("restaurantLat");
            Double rLng = doc.getDouble("restaurantLng");

            if (rLat != null && rLng != null) {
                restaurantPos = new LatLng(rLat, rLng);
                placeRestaurantMarker();
                updateNavigationButtonVisibility();
            }
        });
    }

    private String formatPrice(double amount) {
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        return "Total: Rs " + nf.format(Math.round(amount));
    }

    /**
     * ✅ FIX: route documents describe the TRAIN's whole journey (e.g. from
     * its actual origin station), not just this passenger's own
     * boarding-to-meal-station stretch. Without trimming to where THIS
     * passenger actually boarded, index 0 of the route was the train's
     * absolute origin (e.g. "Karachi Cantt" for a long-distance train) -
     * which corrupted the current-station guess (nearest-station search
     * included stations the passenger never even passed through) AND the
     * ETA (remaining-distance calc started from the train's origin instead
     * of from where the passenger boarded).
     *
     * Now: walk the full route, find fromStation's index, and only keep
     * from there onward (up to and including mealStation).
     */
    private void loadRoute(String routeId, String fromStation, String mealStation) {

        if (routeId == null) return;

        firestore.collection("RailwaySystem").document("main").collection("Routes").document(routeId).get()
                .addOnSuccessListener(doc -> {

                    List<Map<String, Object>> list = (List<Map<String, Object>>) doc.get("stations");
                    if (list == null || list.isEmpty()) return;

                    List<String> fullOrderedNames = new ArrayList<>();
                    for (Map<String, Object> m : list) {
                        fullOrderedNames.add(String.valueOf(m.get("name")));
                    }

                    int boardIndex = -1;

                    if (fromStation != null) {
                        String target = fromStation.trim();
                        for (int i = 0; i < fullOrderedNames.size(); i++) {
                            if (fullOrderedNames.get(i).trim().equalsIgnoreCase(target)) {
                                boardIndex = i;
                                break;
                            }
                        }
                    }

                    List<String> relevant = boardIndex >= 0
                            ? fullOrderedNames.subList(boardIndex, fullOrderedNames.size())
                            : fullOrderedNames; // fromStation not found in route - fall back to everything rather than showing nothing

                    stations.clear();

                    for (String name : relevant) {
                        stations.add(name);
                        if (mealStation != null && name.trim().equalsIgnoreCase(mealStation.trim())) break;
                    }

                    loadCoords(list);
                });
    }

    private void loadCoords(List<Map<String, Object>> allStations) {

        Map<String, LatLng> coordMap = new HashMap<>();
        int totalToFetch = allStations.size();

        if (totalToFetch == 0) return;

        final int[] completedCount = {0};

        for (Map<String, Object> m : allStations) {

            String name = String.valueOf(m.get("name"));

            firestore.collection("RailwaySystem").document("main")
                    .collection("Stations").document(name).get()
                    .addOnCompleteListener(task -> {

                        if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {

                            Double lat = task.getResult().getDouble("lat");
                            Double lng = task.getResult().getDouble("lng");

                            if (lat != null && lng != null) {
                                coordMap.put(name, new LatLng(lat, lng));
                            }
                        }

                        completedCount[0]++;

                        if (completedCount[0] >= totalToFetch) {
                            buildRouteFromCoordMap(coordMap);
                        }
                    });
        }
    }

    private void buildRouteFromCoordMap(Map<String, LatLng> coordMap) {

        if (!isAdded() || mMap == null) return;

        routePoints.clear();
        routeStationNames.clear();

        for (String s : stations) {
            LatLng point = coordMap.get(s);
            if (point != null) {
                routePoints.add(point);
                routeStationNames.add(s);
            }
        }

        if (routePoints.isEmpty()) {
            txtCurrentStation.setText("Current: Unavailable");
            txtEta.setText("ETA: Unavailable");
            return;
        }

        // The trimmed route ends AT the meal station, so its last point is
        // exactly where the rider has to deliver - reused as the rider
        // route's destination after pickup (see refreshRiderRoute()).
        mealStationPos = routePoints.get(routePoints.size() - 1);

        // Rider route may have been waiting on this coordinate.
        refreshRiderRoute();
        updateNavigationButtonVisibility();

        buildCumulativeDistances();
        etaCalculator.setRoute(routePoints);
        drawRoute();
        drawStationMarkers(coordMap);

        if (currentPos == null) {
            currentPos = routePoints.get(0);
            placeOrUpdateTrainMarker(currentPos, false);
        }

        zoomToFitRoute();
        updateCurrentStation();
        updateETA();
    }

    /**
     * Requested feature: label EVERY station on the route (not just the
     * meal station) with its own small icon + name, so the whole journey is
     * identifiable on the map.
     */
    private void drawStationMarkers(Map<String, LatLng> coordMap) {

        for (Marker m : routeStationMarkers) m.remove();
        routeStationMarkers.clear();

        if (mealMarker != null) {
            mealMarker.remove();
            mealMarker = null;
        }

        int lastIndex = stations.size() - 1;

        for (int i = 0; i < stations.size(); i++) {

            String name = stations.get(i);
            LatLng pos = coordMap.get(name);

            if (pos == null) continue;

            if (i == lastIndex) {

                // Meal station keeps its distinct, larger icon.
                mealMarker = mMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title("Meal Station: " + name)
                        .icon(MapIconUtils.labeledVectorToBitmapDescriptor(
                                requireContext(), R.drawable.ic_station_marker, 40, name))
                        .anchor(0.5f, 1f));

            } else {

                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title(name)
                        .icon(MapIconUtils.labeledVectorToBitmapDescriptor(
                                requireContext(), R.drawable.ic_route_station_marker, 26, name))
                        .anchor(0.5f, 1f)
                        .zIndex(0.5f));

                routeStationMarkers.add(marker);
            }
        }
    }

    private void buildCumulativeDistances() {

        cumulativeDistanceKm.clear();

        if (routePoints.isEmpty()) return;

        cumulativeDistanceKm.add(0.0);

        for (int i = 1; i < routePoints.size(); i++) {

            float[] d = new float[1];
            Location.distanceBetween(
                    routePoints.get(i - 1).latitude, routePoints.get(i - 1).longitude,
                    routePoints.get(i).latitude, routePoints.get(i).longitude, d);

            cumulativeDistanceKm.add(cumulativeDistanceKm.get(i - 1) + (d[0] / 1000.0));
        }
    }

    private void drawRoute() {
        if (!mapReady || routePoints.isEmpty()) return;
        if (polyline != null) polyline.remove();
        polyline = mMap.addPolyline(new PolylineOptions().addAll(routePoints).width(10).color(Color.parseColor("#1565C0")));
    }

    private void zoomToFitRoute() {

        if (!mapReady || routePoints.isEmpty()) return;

        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (LatLng p : routePoints) builder.include(p);
        if (currentPos != null) builder.include(currentPos);

        try {
            LatLngBounds bounds = builder.build();
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140));
        } catch (IllegalStateException e) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(routePoints.get(0), 13f));
        }
    }

    private void placeOrUpdateTrainMarker(LatLng pos, boolean animate) {

        if (!mapReady || pos == null) return;

        if (trainMarker == null) {

            trainMarker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Train")
                    .icon(MapIconUtils.vectorToBitmapDescriptor(
                            requireContext(), R.drawable.ic_train_marker, 48))
                    .anchor(0.5f, 0.5f)
                    .zIndex(1f));

        } else if (animate) {

            MapIconUtils.animateMarkerTo(trainMarker, pos, 1000L);

        } else {

            trainMarker.setPosition(pos);
        }
    }

    private void listenLocation() {

        if (locationListener != null) {
            realtimeDb.child("OrderLocations").child(orderId).child("latest").removeEventListener(locationListener);
        }

        locationListener = realtimeDb.child("OrderLocations").child(orderId).child("latest")
                .addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {

                if (!snap.exists()) return;

                Double lat = snap.child("lat").getValue(Double.class);
                Double lng = snap.child("lng").getValue(Double.class);
                Long timestamp = snap.child("timestamp").getValue(Long.class);

                if (lat == null || lng == null) return;

                boolean firstLiveFix = !hasReceivedLiveLocation;
                hasReceivedLiveLocation = true;

                currentPos = new LatLng(lat, lng);

                etaCalculator.recordGpsSample(lat, lng,
                        timestamp != null ? timestamp : System.currentTimeMillis());

                placeOrUpdateTrainMarker(currentPos, true);

                if (firstLiveFix) {
                    zoomToMyPosition();
                }

                updateCurrentStation();
                updateETA();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // =========================================================
    // Module: RIDER LIVE TRACKING
    //
    // Once a rider is assigned, everyone looking at this order (the rider
    // themselves, the restaurant, and the passenger) sees the rider's
    // live position, the real driving route, and Google's own travel-time
    // estimate. The route's destination flips at pickup:
    //
    //   before pickup  ->  rider heading to the RESTAURANT
    //   after pickup   ->  rider heading to the MEAL STATION
    //
    // Route/duration come from DirectionsHelper (real Google Directions
    // API), so the "X mins" shown is exactly what Google Maps would say
    // for that trip - not a straight-line guess.
    // =========================================================

    private void listenRiderLocation(String riderId) {

        if (riderId == null || riderId.isEmpty()) return;

        if (riderLocationListener != null && riderLocationRef != null) {
            riderLocationRef.removeEventListener(riderLocationListener);
        }

        riderLocationRef = com.google.firebase.database.FirebaseDatabase
                .getInstance("https://paktrainfoodservice-default-rtdb.firebaseio.com/")
                .getReference("DeliveryRiders").child(riderId);

        riderLocationListener = new ValueEventListener() {

            @Override
            public void onDataChange(@NonNull DataSnapshot snap) {

                if (!isAdded() || !snap.exists()) return;

                Double lat = snap.child("lat").getValue(Double.class);
                Double lng = snap.child("lng").getValue(Double.class);

                if (lat == null || lng == null) return;

                riderPos = new LatLng(lat, lng);

                placeOrUpdateRiderMarker(riderPos);

                refreshRiderRoute();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        riderLocationRef.addValueEventListener(riderLocationListener);
    }

    private void placeOrUpdateRiderMarker(LatLng pos) {

        if (!mapReady || mMap == null || pos == null) return;

        boolean firstRiderFix = (riderMarker == null);

        if (riderMarker == null) {

            riderMarker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Rider")
                    .icon(MapIconUtils.circularBitmapDescriptor(
                            requireContext(), R.drawable.logo5, 40))
                    .anchor(0.5f, 0.5f));

        } else {
            MapIconUtils.animateMarkerTo(riderMarker, pos, 1000L);
        }

        if (firstRiderFix && "DELIVERY".equalsIgnoreCase(myRole())) {
            zoomToMyPosition();
        }
    }

    /**
     * Redraws the rider's road route + updates the "arrives in X" text.
     * Throttled - Directions API calls cost money and the rider's GPS can
     * tick far more often than the route meaningfully changes.
     */
    /**
     * Draws the rider's journey the way Google Maps' own multi-stop route
     * preview does: two coloured legs with a time on each.
     *
     *   BEFORE PICKUP - both legs are shown:
     *      blue   rider -> restaurant   (go collect the food)
     *      orange restaurant -> station (then deliver it)
     *
     *   AFTER PICKUP - only the delivery leg remains, since the pickup
     *   leg is done.
     *
     * Real road geometry and real drive times come from the Directions
     * API (DirectionsHelper), so these match what Google Maps would say.
     * Turn-by-turn itself is handed off to the Google Maps app via
     * startNavigation() - that voice-guided "Head north" experience is
     * Google's own navigation, not something redrawn here.
     */
    private void refreshRiderRoute() {

        if (!isAdded() || !mapReady || riderPos == null) return;
        if (isTerminalStatus(currentOrderStatus)) return;

        long now = System.currentTimeMillis();

        if (now - lastRouteRefreshAt < RIDER_ROUTE_REFRESH_INTERVAL_MS) return;

        lastRouteRefreshAt = now;

        if (riderHasPickedUp) {

            // Pickup leg is finished - clear it and show only the run to
            // the station.
            if (legToRestaurantPolyline != null) {
                legToRestaurantPolyline.remove();
                legToRestaurantPolyline = null;
            }

            legToRestaurantDuration = null;
            legToRestaurantDistance = null;

            drawLeg(riderPos, mealStationPos, 0xFFEF6C00, false);

        } else {

            drawLeg(riderPos, restaurantPos, 0xFF1565C0, true);
            drawLeg(restaurantPos, mealStationPos, 0xFFEF6C00, false);
        }
    }

    /**
     * @param toRestaurant true for the pickup leg, false for the delivery
     *                     leg - decides which polyline/duration slot the
     *                     result is stored in.
     */
    private void drawLeg(LatLng from, LatLng to, int colour, boolean toRestaurant) {

        if (from == null || to == null) return;

        com.example.paktrainfoodapp.utils.DirectionsHelper.getRoute(
                requireContext(), from, to, "driving",
                new com.example.paktrainfoodapp.utils.DirectionsHelper.Callback() {

                    @Override
                    public void onRouteReady(com.example.paktrainfoodapp.utils.DirectionsHelper.RouteResult result) {

                        if (!isAdded() || mMap == null) return;

                        com.google.android.gms.maps.model.Polyline existing =
                                toRestaurant ? legToRestaurantPolyline : legToStationPolyline;

                        if (existing != null) existing.remove();

                        // Module: real road route from Google Directions -
                        // clickable, so tapping either leg on the map shows
                        // that leg's own time/distance (see
                        // setOnPolylineClickListener() in onMapReady()).
                        com.google.android.gms.maps.model.Polyline drawn =
                                mMap.addPolyline(new PolylineOptions()
                                        .addAll(result.points)
                                        .width(14f)
                                        .color(colour)
                                        .zIndex(2f)
                                        .clickable(true)
                                        .startCap(new RoundCap())
                                        .endCap(new RoundCap())
                                        .jointType(JointType.ROUND));

                        if (toRestaurant) {
                            legToRestaurantPolyline = drawn;
                            legToRestaurantDuration = result.durationText;
                            legToRestaurantDistance = result.distanceText;
                        } else {
                            legToStationPolyline = drawn;
                            legToStationDuration = result.durationText;
                            legToStationDistance = result.distanceText;
                        }

                        updateRiderEtaText();
                    }

                    @Override
                    public void onFailed(String reason) {

                        if (!isAdded() || txtRiderEta == null) return;

                        txtRiderEta.setVisibility(View.VISIBLE);
                        txtRiderEta.setText("Route unavailable: " + reason);
                    }
                });
    }

    private void updateRiderEtaText() {

        if (txtRiderEta == null) return;

        StringBuilder sb = new StringBuilder();

        if (!riderHasPickedUp && legToRestaurantDuration != null) {
            sb.append("\uD83D\uDEF5 To restaurant: ").append(legToRestaurantDuration);
            if (legToRestaurantDistance != null) {
                sb.append(" \u2022 ").append(legToRestaurantDistance);
            }
        }

        if (legToStationDuration != null) {
            if (sb.length() > 0) sb.append("   \u2192   ");
            sb.append("\uD83D\uDCE6 To station: ").append(legToStationDuration);
            if (legToStationDistance != null) {
                sb.append(" \u2022 ").append(legToStationDistance);
            }
        }

        if (sb.length() == 0) return;

        txtRiderEta.setVisibility(View.VISIBLE);
        txtRiderEta.setText(sb.toString());
    }

    /**
     * Tapping either coloured leg on the map shows a small toast with
     * that specific leg's time and distance - "un k km ya m bhi aur time
     * har route jis pr click kre us ka" - so the numbers aren't only
     * readable when both legs happen to fit on one line above the map.
     */
    private void onRiderLegClicked(com.google.android.gms.maps.model.Polyline polyline) {

        if (!isAdded()) return;

        String label;
        String duration;
        String distance;

        if (polyline.equals(legToRestaurantPolyline)) {
            label = "Rider \u2192 Restaurant";
            duration = legToRestaurantDuration;
            distance = legToRestaurantDistance;
        } else if (polyline.equals(legToStationPolyline)) {
            label = riderHasPickedUp ? "Rider \u2192 Meal Station" : "Restaurant \u2192 Meal Station";
            duration = legToStationDuration;
            distance = legToStationDistance;
        } else {
            return;
        }

        StringBuilder sb = new StringBuilder(label).append(": ");
        if (duration != null) sb.append(duration);
        if (distance != null) sb.append(" \u2022 ").append(distance);

        Toast.makeText(getContext(), sb.toString(), Toast.LENGTH_SHORT).show();
    }

    /**
     * Decides whether "Start Navigation" is shown - deliberately kept
     * independent of the Directions API result (see updateRiderEtaText()).
     * The button only launches the external Google Maps app with a
     * destination point, so all it actually needs is that destination
     * (restaurantPos before pickup, mealStationPos after) plus being the
     * rider - it doesn't need the in-app polyline/duration to have loaded
     * first. Called every time the order snapshot, restaurantPos or
     * mealStationPos changes, so it can never get stuck hidden just
     * because a Directions call was slow or failed.
     */
    private void updateNavigationButtonVisibility() {

        if (btnNavigate == null) return;

        if (isTerminalStatus(currentOrderStatus)) {
            btnNavigate.setVisibility(View.GONE);
            return;
        }

        LatLng target = riderHasPickedUp ? mealStationPos : restaurantPos;

        boolean show = "DELIVERY".equalsIgnoreCase(myRole()) && target != null;

        btnNavigate.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Hands off to the Google Maps app for real turn-by-turn navigation -
     * the voice-guided view with the thick blue line, "Head north",
     * re-centre button and live re-routing. That experience is Google's
     * own navigation stack; an embedded MapView can draw a route but
     * can't provide guidance, so launching Maps is what actually gets the
     * rider that screen.
     */
    private void startNavigation() {

        LatLng target = riderHasPickedUp ? mealStationPos : restaurantPos;

        if (target == null) {
            Toast.makeText(getContext(), "Destination not available yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // "b" = two-wheeler/bike routing, matching how a delivery rider
        // actually travels (same mode as the "Two-wheeler" tab in Maps).
        android.net.Uri uri = android.net.Uri.parse(
                "google.navigation:q=" + target.latitude + "," + target.longitude + "&mode=b");

        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, uri);
        intent.setPackage("com.google.android.apps.maps");

        if (intent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(intent);
            return;
        }

        // Google Maps app not installed - fall back to the browser version
        // rather than doing nothing.
        android.content.Intent web = new android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                        + target.latitude + "," + target.longitude + "&travelmode=driving"));

        try {
            startActivity(web);
        } catch (Exception e) {
            Toast.makeText(getContext(), "No maps app available", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Decides which conversation this order's Chat/Call buttons point at,
     * based on how far along the order is:
     *
     *   rider assigned -> pickup : rider <-> RESTAURANT
     *                              (coordinating collection)
     *   after pickup             : rider <-> PASSENGER
     *                              (coordinating handover at the station)
     *
     * The restaurant thread deliberately disappears at pickup - their part
     * is finished - and both threads disappear once the order reaches a
     * terminal state, so a completed order has no live contact options.
     * Nothing is deleted: the admin panel can still read every thread.
     */
    /** Whoever is signed in - decides zoom target and whether navigation is offered. */
    private String myRole() {
        return new com.example.paktrainfoodapp.utils.PrefManager(requireContext()).getUserRole();
    }

    /**
     * Centres the map on whoever is LOOKING at it - a rider wants to see
     * themselves, a passenger wants to see the train they're on, and a
     * restaurant wants to see its own shop. Previously everyone got the
     * same whole-route fit, which on a long route left your own position
     * as an unfindable dot.
     */
    private void zoomToMyPosition() {

        if (!mapReady || mMap == null) return;

        String role = myRole();
        LatLng target = null;

        if ("DELIVERY".equalsIgnoreCase(role)) {
            target = riderPos;
        } else if ("RESTAURANT".equalsIgnoreCase(role)) {
            target = restaurantPos;
        } else {
            target = currentPos; // passenger: the train's live position
        }

        if (target == null) return;

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 14f));
    }

    /**
     * ✅ FIX: this used to assume the viewer was ALWAYS the rider - it
     * offered "Chat with Restaurant" / "Chat with Passenger" purely based
     * on pickup status, with no idea who was actually looking at the
     * screen. That meant:
     *   - a PASSENGER before pickup saw "Chat with Restaurant" (nothing
     *     to do with them - that's the rider's conversation)
     *   - a PASSENGER after pickup saw "Chat with Passenger" - an offer
     *     to chat with themselves
     *   - a RESTAURANT after pickup still saw a chat option, even though
     *     their part of the order is finished
     *
     * Now the target and the visible window both depend on who's
     * actually viewing:
     *
     *   RIDER      : restaurant before pickup, passenger after pickup
     *                (unchanged - this was already correct for riders)
     *   RESTAURANT : always the rider, but only until pickup (their job
     *                is done once the food leaves the building)
     *   PASSENGER  : always the rider, but only from pickup onward (per
     *                the original spec - nothing to coordinate before
     *                the food is actually on its way)
     */
    private void setupContactButtons(String status, String assignedRiderId) {

        if (layoutContact == null) return;

        boolean riderAssigned = assignedRiderId != null && !assignedRiderId.isEmpty();

        if (!riderAssigned || isTerminalStatus(status)) {
            layoutContact.setVisibility(View.GONE);
            return;
        }

        String role = myRole();
        boolean show;
        String chatType;
        String otherName;
        String otherPhone;

        if ("RESTAURANT".equalsIgnoreCase(role)) {

            show = !riderHasPickedUp;
            chatType = com.example.paktrainfoodapp.ui.shared.chat.OrderChatFragment.TYPE_RESTAURANT;
            otherName = "PakTrain Rider";
            otherPhone = riderPhoneForChat;

        } else if ("PASSENGER".equalsIgnoreCase(role) || role == null || role.isEmpty()) {

            show = riderHasPickedUp;
            chatType = com.example.paktrainfoodapp.ui.shared.chat.OrderChatFragment.TYPE_PASSENGER;
            otherName = "PakTrain Rider";
            otherPhone = riderPhoneForChat;

        } else {

            // Rider: unchanged behaviour - restaurant before pickup,
            // passenger after.
            show = true;
            chatType = riderHasPickedUp
                    ? com.example.paktrainfoodapp.ui.shared.chat.OrderChatFragment.TYPE_PASSENGER
                    : com.example.paktrainfoodapp.ui.shared.chat.OrderChatFragment.TYPE_RESTAURANT;
            otherName = riderHasPickedUp
                    ? (passengerNameForChat != null ? passengerNameForChat : "Passenger")
                    : (restaurantNameForChat != null ? restaurantNameForChat : "Restaurant");
            otherPhone = riderHasPickedUp ? passengerPhoneForChat : restaurantPhoneForChat;
        }

        if (!show) {
            layoutContact.setVisibility(View.GONE);
            return;
        }

        layoutContact.setVisibility(View.VISIBLE);

        final String finalChatType = chatType;
        final String finalOtherName = otherName;
        final String finalOtherPhone = otherPhone;

        // The counterparty's name sits beside the icons rather than
        // inside a button, so the actions stay compact.
        if (txtContactWith != null) {
            txtContactWith.setText(finalOtherName);
        }

        btnChat.setOnClickListener(v -> {

            if (!isAdded()) return;

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container,
                            com.example.paktrainfoodapp.ui.shared.chat.OrderChatFragment
                                    .newInstance(orderId, finalChatType, finalOtherName, finalOtherPhone))
                    .addToBackStack(null)
                    .commit();
        });

        btnCall.setOnClickListener(v -> {

            if (finalOtherPhone == null || finalOtherPhone.trim().isEmpty()) {
                Toast.makeText(getContext(), "No phone number on file", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                startActivity(new android.content.Intent(
                        android.content.Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:" + finalOtherPhone.trim())));
            } catch (Exception e) {
                Toast.makeText(getContext(), "Couldn't open dialer", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * A single definition of "this order is finished" - used everywhere
     * the screen needs to decide between live tracking and a closed
     * order (contact buttons, navigation button, map, completed summary).
     * Keeping one definition means every one of those places agrees.
     */
    private boolean isTerminalStatus(String status) {
        return status == null
                || status.equals("completed")
                || status.equals("Cancelled")
                || status.equals("Rejected")
                || status.equals("delivery_failed")
                || status.equals("disputed");
    }

    /**
     * Module: order id + status pill at the top of the screen (every
     * status), and the switch between "live tracking" (map + ETA card)
     * and "completed summary" (delivered time + read-only chat) once the
     * order reaches a terminal state.
     *
     * ✅ FIX: a completed order used to keep showing the live map, the
     * rider's ETA text and the Start Navigation button, and had no order
     * id, status or delivered time on screen at all. Now, once the order
     * is done, the map and every live-tracking control disappear (nothing
     * left to track), and a clean summary - order id, status, train,
     * delivery station, coach, seat, delivered time, items, total price
     * and the chat history - is all that's left.
     */
    private void updateTerminalUi(String status, Long orderNumber, Long completedAtMillis) {

        if (!isAdded()) return;

        if (txtDetailOrderId != null) {
            txtDetailOrderId.setText(
                    com.example.paktrainfoodapp.utils.OrderNumberUtils.format(orderNumber, orderId));
        }

        if (txtDetailStatusBadge != null) {
            com.example.paktrainfoodapp.utils.StatusBadge.apply(txtDetailStatusBadge, status);
        }

        boolean terminal = isTerminalStatus(status);
        boolean completed = "completed".equals(status);

        if (mapContainer != null) mapContainer.setVisibility(terminal ? View.GONE : View.VISIBLE);
        if (layoutLiveTrackingCard != null) {
            layoutLiveTrackingCard.setVisibility(terminal ? View.GONE : View.VISIBLE);
        }

        if (layoutCompletedTime != null) {
            layoutCompletedTime.setVisibility(completed ? View.VISIBLE : View.GONE);
        }

        if (completed && txtCompletedTime != null) {
            if (completedAtMillis != null && completedAtMillis > 0) {
                txtCompletedTime.setText("Delivered at: " + new java.text.SimpleDateFormat(
                        "dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                        .format(new java.util.Date(completedAtMillis)));
            } else {
                txtCompletedTime.setText("Delivered");
            }
        }

        if (layoutCompletedChat != null) {
            layoutCompletedChat.setVisibility(completed ? View.VISIBLE : View.GONE);
        }

        TextView txtDetailTitle = getView() != null ? getView().findViewById(R.id.txtDetailTitle) : null;
        if (txtDetailTitle != null) {
            txtDetailTitle.setText(terminal ? "Order Details" : "Live Order Tracking");
        }

        if (completed) {
            setupCompletedChatSections();
            loadCompletedChatHistory();
        }
    }

    private final List<CompletedChatMessage> completedChatMessagesRestaurant = new ArrayList<>();
    private final List<CompletedChatMessage> completedChatMessagesPassenger = new ArrayList<>();
    private CompletedChatAdapter completedChatAdapterRestaurant;
    private CompletedChatAdapter completedChatAdapterPassenger;

    /**
     * Decides which of the two thread blocks this viewer should even see,
     * and what to label them - a restaurant/passenger only ever had ONE
     * conversation (with the rider), so they see a single block titled
     * "Chat with Rider". A rider was in both, so they see both blocks,
     * clearly separated (never merged into one timeline) and labelled by
     * who each one was with.
     */
    private void setupCompletedChatSections() {

        String role = myRole();
        boolean isRestaurant = "RESTAURANT".equalsIgnoreCase(role);
        boolean isPassenger = "PASSENGER".equalsIgnoreCase(role) || role == null || role.isEmpty();
        boolean isRider = !isRestaurant && !isPassenger;

        if (layoutChatRestaurant != null) {
            layoutChatRestaurant.setVisibility(isPassenger ? View.GONE : View.VISIBLE);
        }
        if (txtChatRestaurantTitle != null) {
            txtChatRestaurantTitle.setText(isRestaurant ? "Chat with Rider" : "Chat with Restaurant");
        }

        if (layoutChatPassenger != null) {
            layoutChatPassenger.setVisibility(isRestaurant ? View.GONE : View.VISIBLE);
        }
        if (txtChatPassengerTitle != null) {
            txtChatPassengerTitle.setText(isPassenger ? "Chat with Rider" : "Chat with Passenger");
        }

        // Divider between the two blocks only makes sense when both are
        // actually showing (the rider's view).
        if (dividerChatThreads != null) {
            dividerChatThreads.setVisibility(isRider ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Read-only transcript for a finished order - no send box, this is
     * just a record of what was said. The two conversations are fetched
     * and rendered completely independently (never merged) - see
     * setupCompletedChatSections() for who sees which one. A completed
     * order's chat can't change any more, so a single fetch (not a live
     * listener) is enough, and every message that was ever sent is kept
     * in the list - the card only ever grows to fit them (wrap_content,
     * nested scrolling off) inside the screen's own outer scroll, so nothing
     * is cut off; you just scroll the page to read the rest.
     */
    private void loadCompletedChatHistory() {

        if (orderId == null) return;

        String role = myRole();
        boolean isRestaurant = "RESTAURANT".equalsIgnoreCase(role);
        boolean isPassenger = "PASSENGER".equalsIgnoreCase(role) || role == null || role.isEmpty();

        if (!isPassenger && recyclerChatRestaurant != null) {
            fetchChatThread("chats_restaurant", recyclerChatRestaurant, txtChatRestaurantEmpty,
                    completedChatMessagesRestaurant, true);
        }

        if (!isRestaurant && recyclerChatPassenger != null) {
            fetchChatThread("chats_passenger", recyclerChatPassenger, txtChatPassengerEmpty,
                    completedChatMessagesPassenger, false);
        }
    }

    private void fetchChatThread(String threadCollection, RecyclerView recycler, TextView emptyLabel,
                                 List<CompletedChatMessage> targetList, boolean isRestaurantThread) {

        firestore.collection("Orders").document(orderId)
                .collection(threadCollection)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {

                    if (!isAdded()) return;

                    targetList.clear();

                    if (task.isSuccessful() && task.getResult() != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult()) {

                            CompletedChatMessage m = new CompletedChatMessage();
                            m.senderName = doc.getString("senderName");
                            m.text = doc.getString("text");
                            Long ts = doc.getLong("timestamp");
                            m.timestamp = ts != null ? ts : 0L;

                            targetList.add(m);
                        }
                    }

                    CompletedChatAdapter adapter = isRestaurantThread
                            ? completedChatAdapterRestaurant : completedChatAdapterPassenger;

                    if (adapter == null) {
                        adapter = new CompletedChatAdapter(targetList);
                        recycler.setAdapter(adapter);
                        if (isRestaurantThread) completedChatAdapterRestaurant = adapter;
                        else completedChatAdapterPassenger = adapter;
                    } else {
                        adapter.notifyDataSetChanged();
                    }

                    if (emptyLabel != null) {
                        emptyLabel.setVisibility(targetList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private static class CompletedChatMessage {
        String senderName, text;
        long timestamp;
    }

    private static class CompletedChatAdapter
            extends RecyclerView.Adapter<CompletedChatAdapter.VH> {

        private final List<CompletedChatMessage> items;

        CompletedChatAdapter(List<CompletedChatMessage> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_theirs, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {

            CompletedChatMessage m = items.get(position);

            h.txtMessage.setText(m.text);
            h.txtTime.setText(new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    .format(new java.util.Date(m.timestamp)));

            if (h.txtSender != null) {
                h.txtSender.setText(m.senderName != null ? m.senderName : "");
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {

            TextView txtMessage, txtTime, txtSender;

            VH(@NonNull View itemView) {
                super(itemView);
                txtMessage = itemView.findViewById(R.id.txtChatMessage);
                txtTime = itemView.findViewById(R.id.txtChatTime);
                txtSender = itemView.findViewById(R.id.txtChatSender);
            }
        }
    }

    private void updateCurrentStation() {

        if (currentPos == null || routePoints.isEmpty()) return;

        String current = "In Transit";
        float min = Float.MAX_VALUE;
        int nearestIndex = 0;

        for (int i = 0; i < routePoints.size(); i++) {
            float[] d = new float[1];
            Location.distanceBetween(currentPos.latitude, currentPos.longitude,
                    routePoints.get(i).latitude, routePoints.get(i).longitude, d);
            // ✅ FIX: use routeStationNames (aligned with routePoints), not
            // `stations` (which may be longer if some station had no
            // coordinates) - this was the actual cause of the wrong
            // current-station being picked.
            if (d[0] < min) { min = d[0]; current = routeStationNames.get(i); nearestIndex = i; }
        }

        // Always the real nearest station to the best position we have -
        // no hardcoded default. Updates every time a new GPS fix moves the
        // nearest match to the next station along the route.
        txtCurrentStation.setText("Current: " + current);

        if (nearestIndex != lastNearestStationIndex) {
            lastNearestStationIndex = nearestIndex;
            refreshHistoricalAverage(nearestIndex);
        }
    }

    private void refreshHistoricalAverage(int nearestIndex) {

        if (mealStationName == null || routeStationNames.isEmpty() || cumulativeDistanceKm.isEmpty()) return;

        int lastIndex = routeStationNames.size() - 1;

        if (nearestIndex >= lastIndex) {
            etaCalculator.setHistoricalAverage(0.0, 0.0);
            return;
        }

        String nearestStationName = routeStationNames.get(nearestIndex);
        double segmentDistanceKm = cumulativeDistanceKm.get(lastIndex) - cumulativeDistanceKm.get(nearestIndex);

        RouteHistoryHelper.getAverageMinutes(nearestStationName, mealStationName, (averageMinutes, sampleCount) -> {

            if (!isAdded()) return;

            etaCalculator.setHistoricalAverage(averageMinutes, segmentDistanceKm);
            updateETA();
        });
    }

    private void updateETA() {

        if (currentPos == null || routePoints.isEmpty()) return;

        EtaCalculator.Result result = etaCalculator.computeEtaMinutes(currentPos);

        if (result == null) return;

        int totalMins = result.etaMinutes;

        long trainEtaEndTime = System.currentTimeMillis() + (totalMins * 60 * 1000L);

        int hours = totalMins / 60;
        int minutes = totalMins % 60;

        String suffix;

        if (!hasReceivedLiveLocation) {
            suffix = " (estimated)";
        } else if (result.usedLiveSpeed) {
            suffix = "";
        } else if (result.usedHistory) {
            suffix = " (usual timing)";
        } else {
            suffix = " (approx)";
        }

        txtEta.setText("ETA: ~" + hours + " hr " + minutes + " min" + suffix);

        // ✅ FIX: write on EVERY recompute (not just once live GPS has
        // arrived) so the restaurant's order-list "Estimated Arrival" and
        // the passenger's own order-list always show the EXACT SAME number
        // as this screen - no separate/stale calculation sitting in
        // Firestore. Before this fix, the list only updated once real GPS
        // data existed, so it could sit showing the rough order-placement
        // guess indefinitely while this screen already had a better number.
        if (Math.abs(totalMins - lastSavedMinutes) >= 1) {
            lastSavedMinutes = totalMins;
            firestore.collection("Orders").document(orderId).update("trainEtaEndTime", trainEtaEndTime);
        }
    }

    private void placeRestaurantMarker() {

        if (!mapReady || mMap == null || restaurantPos == null) return;

        mMap.addMarker(new MarkerOptions()
                .position(restaurantPos)
                .title("Restaurant")
                .icon(MapIconUtils.vectorToBitmapDescriptor(
                        requireContext(), R.drawable.ic_restaurant_marker, 44))
                .anchor(0.5f, 0.5f));
    }

    @Override public void onDestroyView() {
        if (locationListener != null) {
            realtimeDb.child("OrderLocations").child(orderId).child("latest").removeEventListener(locationListener);
        }
        if (orderRegistration != null) orderRegistration.remove();
        if (riderLocationRef != null && riderLocationListener != null) {
            riderLocationRef.removeEventListener(riderLocationListener);
        }
        super.onDestroyView();
    }
}
