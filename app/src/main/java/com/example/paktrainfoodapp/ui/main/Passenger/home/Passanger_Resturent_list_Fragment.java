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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class Passanger_Resturent_list_Fragment extends Fragment {

    private RecyclerView rv;
    private ArrayList<Restaurant_list_Model> list;
    private Restaurant_list_Adapter adapter;
    private FirebaseFirestore db;

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
        String fetchCity = cleanCityName(selectedCity);
        tvTopTitle.setText("Restaurants in " + fetchCity);

        Log.d("CITY_DEBUG", "Original = " + selectedCity + " | Fetch = " + fetchCity);

        // ================= LOAD RESTAURANTS =================
        loadRestaurants(fetchCity);

        // ================= CLICK HANDLING =================
        adapter.setOnItemClickListener(new Restaurant_list_Adapter.OnItemClickListener() {
            @Override
            public void onFavoriteClick(int position) {}

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

    private void loadRestaurants(String city) {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("Users")
                .document("Restaurant")
                .collection("VerifiedRegister")
                .whereEqualTo("city", city)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded() || getContext() == null) return;

                    progressBar.setVisibility(View.GONE);
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Toast.makeText(getContext(), "Error loading restaurants", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (task.getResult().isEmpty()) {
                        Toast.makeText(getContext(), "No restaurants found in " + city, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    list.clear();
                    int totalDocs = task.getResult().size();
                    final int[] loadedCount = {0};

                    for (DocumentSnapshot doc : task.getResult()) {
                        String uid = doc.getId();
                        String restaurantName = doc.getString("restaurantName");
                        String cityName = doc.getString("city");

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

                                        list.add(new Restaurant_list_Model(uid, restaurantName, cityName, imageUrl));
                                        if (loadedCount[0] == totalDocs) adapter.notifyDataSetChanged();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    loadedCount[0]++;
                                    if (loadedCount[0] == totalDocs) adapter.notifyDataSetChanged();
                                });
                    }
                });
    }
}



//



