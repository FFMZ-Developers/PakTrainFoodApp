package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader;
import com.example.paktrainfoodapp.utils.FavoritesManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class Passanger_Resturent_list_Fragment extends Fragment {

    private RecyclerView rv;
    private ArrayList<Restaurant_list_Model> list;
    private Restaurant_list_Adapter adapter;
    private FirebaseFirestore db;
    private FavoritesManager favoritesManager;

    /** Master list as loaded from Firestore; `list` is the filtered view of it. */
    private final ArrayList<Restaurant_list_Model> allRestaurants = new ArrayList<>();
    private boolean showFavoritesOnly = false;
    private android.widget.ImageView btnFilterFavorites;

    private String searchQuery = "";
    private android.widget.EditText etSearch;
    private View layoutSearch;

    private TextView tvTopTitle;
    private ProgressBar progressBar;

    private String selectedCity;
    private String trainName;
    private String routeId;
    private String fromStation;
    private String toStation;

    public Passanger_Resturent_list_Fragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_passanger__resturent_list_, container, false);

        rv = view.findViewById(R.id.rv_restaurants);
        tvTopTitle = view.findViewById(R.id.tv_top_title);
        progressBar = view.findViewById(R.id.progressBar1);

        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        list = new ArrayList<>();
        adapter = new Restaurant_list_Adapter(list);
        rv.setAdapter(adapter);

        db = FirebaseFirestore.getInstance();
        favoritesManager = new FavoritesManager(requireContext());

        setupSearch(view);

        btnFilterFavorites = view.findViewById(R.id.btn_filter_favorites);

        if (btnFilterFavorites != null) {

            btnFilterFavorites.setOnClickListener(v -> {

                showFavoritesOnly = !showFavoritesOnly;

                btnFilterFavorites.setImageResource(
                        showFavoritesOnly
                                ? R.drawable.ic_heart_filled
                                : R.drawable.ic_heart_outline);

                applyFilter();

                Toast.makeText(getContext(),
                        showFavoritesOnly ? "Showing favorites only" : "Showing all restaurants",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // ================= GET ARGUMENTS =================
        if (getArguments() != null) {
            selectedCity = getArguments().getString("selectedCity");
            trainName = getArguments().getString("TRAIN_NAME");
            routeId = getArguments().getString("ROUTE_ID");
            fromStation = getArguments().getString("FROM");
            toStation = getArguments().getString("TO");
        }

        if (selectedCity == null) selectedCity = "Unknown";

        // ================= CLEAN CITY NAME =================
        //
        // ✅ FIX: switched from a fragile .replace("Jn","").replace("Cantt","")
        // string edit to CityNameUtils.normalize() (word-boundary regex,
        // case-insensitive, whitespace-collapsed) - the old version broke
        // silently for some cities (reported: Mandi Bahauddin) whenever the
        // station name's suffix wasn't an EXACT case/spacing match, since
        // Firestore's whereEqualTo needs an exact string. Restaurant
        // registration now saves the SAME normalized value as
        // "cityNormalized" (see Step4SelfieFragment.java) - both sides are
        // guaranteed to agree now.
        String fetchCity = com.example.paktrainfoodapp.utils.CityNameUtils.normalize(selectedCity);
        tvTopTitle.setText("Restaurants in " + cleanCityName(selectedCity));

        Log.d("CITY_DEBUG", "Original = " + selectedCity + " | Fetch (normalized) = " + fetchCity);

        // ================= LOAD RESTAURANTS =================
        loadRestaurants(fetchCity);

        // ================= CLICK HANDLING =================
        adapter.setOnItemClickListener(new Restaurant_list_Adapter.OnItemClickListener() {
            @Override
            public void onFavoriteClick(int position) {

                if (position < 0 || position >= list.size()) return;

                Restaurant_list_Model model = list.get(position);

                boolean nowFavorite =
                        favoritesManager.toggleFavorite(model.getUid());

                model.setFavorite(nowFavorite);

                // Keep the master copy in sync so the filter stays correct
                for (Restaurant_list_Model m : allRestaurants) {
                    if (m.getUid() != null && m.getUid().equals(model.getUid())) {
                        m.setFavorite(nowFavorite);
                    }
                }

                if (showFavoritesOnly && !nowFavorite) {
                    applyFilter();
                } else {
                    adapter.notifyItemChanged(position);
                }

                Toast.makeText(
                        getContext(),
                        nowFavorite
                                ? model.getRestaurantName() + " added to favorites"
                                : model.getRestaurantName() + " removed from favorites",
                        Toast.LENGTH_SHORT
                ).show();
            }

            @Override
            public void onItemClick(int position) {
                if (position < 0 || position >= list.size()) return;

                Restaurant_list_Model model = list.get(position);
                Resturent_Menu_Fragment fragment = new Resturent_Menu_Fragment();
                Bundle bundle = new Bundle();

                bundle.putString("RESTAURANT_UID", model.getUid());
                bundle.putString("RESTAURANT_NAME", model.getRestaurantName());
                bundle.putString("RESTAURANT_IMAGE", model.getImageUrl());
                bundle.putString("RESTAURANT_INFO", "Verified Restaurant"); // Added info field

                // Route Info
                bundle.putString("MEAL_STATION", selectedCity);
                bundle.putString("TRAIN_NAME", trainName);
                bundle.putString("ROUTE_ID", routeId);
                bundle.putString("FROM", fromStation);
                bundle.putString("TO", toStation);

                fragment.setArguments(bundle);

                // Parent Loader validation logic
                Fragment parentFrag = getParentFragment();
                if (parentFrag instanceof Passenger_Fragment_Loader) {
                    Passenger_Fragment_Loader loader =
                            (Passenger_Fragment_Loader) parentFrag;

                    loader.openRestaurantMenu(fragment);

//                    loader.showTempFragment(fragment);
                }
            }
        });

        return view;
    }

    private String cleanCityName(String city) {
        if (city == null) return "";
        return city.trim()
                .replace("Jn", "")
                .replace("Cantt", "")
                .trim();
    }

    private void loadRestaurants(String normalizedCity) {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("Users")
                .document("Restaurant")
                .collection("VerifiedRegister")
                .whereEqualTo("cityNormalized", normalizedCity)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded() || getContext() == null) return;

                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                        bindRestaurantResults(task.getResult());
                        return;
                    }

                    // ✅ Backward-compat fallback: restaurants registered
                    // BEFORE this fix don't have "cityNormalized" yet (it's
                    // a new field). Rather than making them invisible until
                    // every restaurant happens to re-save their profile,
                    // fall back to the legacy exact-match "city" query for
                    // anyone the normalized query didn't find.
                    db.collection("Users")
                            .document("Restaurant")
                            .collection("VerifiedRegister")
                            .whereEqualTo("city", cleanCityName(selectedCity))
                            .get()
                            .addOnCompleteListener(legacyTask -> {

                                if (!isAdded() || getContext() == null) return;

                                progressBar.setVisibility(View.GONE);

                                if (!legacyTask.isSuccessful() || legacyTask.getResult() == null) {
                                    Toast.makeText(getContext(), "Error loading restaurants", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                if (legacyTask.getResult().isEmpty()) {
                                    Toast.makeText(getContext(), "No restaurants found in " + normalizedCity, Toast.LENGTH_SHORT).show();
                                    list.clear();
                                    allRestaurants.clear();
                                    adapter.notifyDataSetChanged();
                                    return;
                                }

                                bindRestaurantResults(legacyTask.getResult());
                            });
                });
    }

    private void bindRestaurantResults(com.google.firebase.firestore.QuerySnapshot result) {

                    progressBar.setVisibility(View.GONE);

                    list.clear();
                    allRestaurants.clear();
                    int totalDocs = result.size();
                    final int[] loadedCount = {0};

                    for (DocumentSnapshot doc : result) {
                        String uid = doc.getId();
                        String restaurantName = doc.getString("restaurantName");
                        String cityName = doc.getString("city");

                        // Module 7 - skip auto-paused restaurants entirely
                        // (repeated reliability strikes - reliabilityHelper.js).
                        Boolean isPaused = doc.getBoolean("isPaused");
                        if (isPaused != null && isPaused) {
                            loadedCount[0]++;
                            if (loadedCount[0] == totalDocs) applyFilter();
                            continue;
                        }

                        Long reliabilityScoreLong = doc.getLong("reliabilityScore");
                        int reliabilityScore = reliabilityScoreLong != null
                                ? reliabilityScoreLong.intValue() : 100;

                        // Module: aggregate rating - maintained server-side
                        // (onReviewWritten.js), already sitting right on this
                        // same doc, so no extra read is needed for it.
                        Double avgRatingD = doc.getDouble("averageRating");
                        final double avgRating = avgRatingD != null ? avgRatingD : 0;

                        Long reviewCountL = doc.getLong("reviewCount");
                        final int reviewCount = reviewCountL != null ? reviewCountL.intValue() : 0;

                        db.collection("Users")
                                .document("Restaurant")
                                .collection("Register")
                                .document(uid)
                                .get()
                                .addOnSuccessListener(imageDoc -> {
                                    loadedCount[0]++;
                                    if (isAdded() && getContext() != null) {
                                        String imageUrl = imageDoc.exists() ?
                                                (TextUtils.isEmpty(imageDoc.getString("profileImageUrl")) ?
                                                        imageDoc.getString("imageUrl") : imageDoc.getString("profileImageUrl")) : null;

                                        Restaurant_list_Model model =
                                                new Restaurant_list_Model(uid, restaurantName, cityName, imageUrl);

                                        model.setFavorite(favoritesManager.isFavorite(uid));
                                        model.setReliabilityScore(reliabilityScore);
                                        model.setAverageRating(avgRating);
                                        model.setReviewCount(reviewCount);

                                        allRestaurants.add(model);
                                        if (loadedCount[0] == totalDocs) applyFilter();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    loadedCount[0]++;
                                    if (loadedCount[0] == totalDocs) applyFilter();
                                });
                    }
    }

    /**
     * Rebuilds the visible list from the master copy, honouring the
     * favourites-only toggle.
     */
    private void applyFilter() {

        list.clear();

        String q = searchQuery.trim().toLowerCase();

        for (Restaurant_list_Model m : allRestaurants) {

            if (showFavoritesOnly && !m.isFavorite()) continue;

            if (!q.isEmpty()) {

                String name = m.getRestaurantName() == null
                        ? "" : m.getRestaurantName().toLowerCase();

                String city = m.getCity() == null
                        ? "" : m.getCity().toLowerCase();

                if (!name.contains(q) && !city.contains(q)) continue;
            }

            list.add(m);
        }

        // Module 7 - higher-reliability restaurants shown first.
        list.sort((a, b) -> Integer.compare(b.getReliabilityScore(), a.getReliabilityScore()));

        adapter.notifyDataSetChanged();
    }

    /**
     * The search field starts hidden so a long station title can never push it
     * off the app bar; the icon reveals it.
     */
    private void setupSearch(View view) {

        layoutSearch = view.findViewById(R.id.layout_search);
        etSearch = view.findViewById(R.id.et_search);

        View btnToggle = view.findViewById(R.id.btn_toggle_search);
        View btnClear = view.findViewById(R.id.btn_clear_search);

        if (btnToggle == null || layoutSearch == null || etSearch == null) return;

        btnToggle.setOnClickListener(v -> {

            boolean visible = layoutSearch.getVisibility() == View.VISIBLE;

            if (visible) {

                layoutSearch.setVisibility(View.GONE);
                etSearch.setText("");
                hideKeyboard();

            } else {

                layoutSearch.setVisibility(View.VISIBLE);
                etSearch.requestFocus();

                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) requireContext()
                                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);

                if (imm != null) {
                    imm.showSoftInput(etSearch,
                            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> etSearch.setText(""));
        }

        etSearch.addTextChangedListener(new android.text.TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {

                searchQuery = s.toString();

                applyFilter();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) { }
        });
    }

    private void hideKeyboard() {

        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) requireContext()
                        .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);

        if (imm != null && etSearch != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
    }
}



//



