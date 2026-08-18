package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Passenger.MenuitemModel;
import com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class Resturent_Menu_Fragment extends Fragment {

    private ImageView imgRestaurant;
    private TextView txtRestaurantName, txtRestaurantInfo;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;

    private final List<MenuitemModel> itemList = new ArrayList<>();
    private Resturent_Menu_Adapter adapter;
    private FirebaseFirestore db;

    private String restaurantId = "";
    private String restaurantName = "";
    private String restaurantImageUrl = "";
    private String restaurantInfo = "";
    private String mealStation = "";

    private String trainId = "";
    private String routeId = "";
    private String fromStation = "";
    private String toStation = "";
    private String trainName = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_passanger_station__menu_, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imgRestaurant = view.findViewById(R.id.img_restaurant);
        txtRestaurantName = view.findViewById(R.id.tv_restaurant_name);
        txtRestaurantInfo = view.findViewById(R.id.tv_restaurant_info);
        recyclerView = view.findViewById(R.id.recyclerMenu);
        progressBar = view.findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new Resturent_Menu_Adapter(itemList);
        recyclerView.setAdapter(adapter);


        db = FirebaseFirestore.getInstance();

        // ================= ARGUMENTS =================
        Bundle args = getArguments();
        if (args != null) {

            restaurantId = args.getString("RESTAURANT_UID", "");
            restaurantName = args.getString("RESTAURANT_NAME", "");
            restaurantImageUrl = args.getString("RESTAURANT_IMAGE_URL",
                    args.getString("RESTAURANT_IMAGE", ""));
            restaurantInfo = args.getString("RESTAURANT_INFO", "Fast Food");

            mealStation = args.getString("MEAL_STATION", "");

            trainId = args.getString("TRAIN_ID", "");
            routeId = args.getString("ROUTE_ID", "");
            fromStation = args.getString("FROM", "");
            toStation = args.getString("TO", "");
            trainName = args.getString("TRAIN_NAME", "");
        }

        txtRestaurantName.setText(restaurantName);
        txtRestaurantInfo.setText(restaurantInfo);

        Glide.with(this)
                .load(restaurantImageUrl)
                .placeholder(R.drawable.station3)
                .error(R.drawable.station3)
                .centerCrop()
                .into(imgRestaurant);

        loadMenuItemsByRestaurantId(restaurantId);

        // ================= ITEM CLICK =================
        adapter.setOnMenuItemClickListener(new Resturent_Menu_Adapter.OnMenuItemClickListener() {

            @Override
            public void onAddToCart(MenuitemModel item) {

                if (getParentFragment() instanceof Passenger_Fragment_Loader) {

                    Passenger_Fragment_Loader loader =
                            (Passenger_Fragment_Loader) getParentFragment();

                    Passanger_ItemDetailsFragment detailsFragment =
                            Passanger_ItemDetailsFragment.newInstance(
                                    item,
                                    restaurantId,
                                    restaurantName,
                                    mealStation,
                                    trainId,
                                    routeId,
                                    fromStation,
                                    toStation,
                                    trainName
                            );

                    loader.getChildFragmentManager()
                            .beginTransaction()
                            .hide(loader.getActiveFragment())
                            .add(R.id.fragment_holder, detailsFragment)
                            .addToBackStack(null)
                            .commit();
                }
            }




            @Override
            public void onBuyNow(MenuitemModel item) {
                if (!isAdded()) return;

                double price = 0.0;
                if (item.getVariations() != null && !item.getVariations().isEmpty()) {
                    price = item.getVariations().values().iterator().next();
                }

                // 1. CartItem banayein
                ArrayList<CartItem> cartItems = new ArrayList<>();
                cartItems.add(new CartItem(
                        item.getId(), restaurantId, restaurantName, item.getName(),
                        price, 1, "", item.getImageUrl(), item.getDescription(),
                        mealStation, trainId, routeId, fromStation, toStation, trainName
                ));

                // 2. Sirf OrderSummaryFragment open karein
                OrderSummaryFragment dialog = OrderSummaryFragment.newInstance(cartItems);
                dialog.show(getParentFragmentManager(), "OrderSummary");
            }

            @Override
            public void onDeleteOrder(MenuitemModel item) {}
        });

        CartUIHelper.setupCartBar(view, this);
    }

    // ================= LOAD MENU =================
    private void loadMenuItemsByRestaurantId(String restaurantId) {

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        itemList.clear();

        db.collection("Users")
                .document("Restaurant")
                .collection("VerifiedRegister")
                .document(restaurantId)
                .collection("MenuItems")
                .get()
                .addOnSuccessListener(menuItems -> {

                    if (!isAdded() || getContext() == null) return;

                    for (QueryDocumentSnapshot doc : menuItems) {

                        MenuitemModel item = doc.toObject(MenuitemModel.class);

                        item.setId(doc.getId());
                        item.setRestaurantId(restaurantId);
                        item.setRestaurantName(restaurantName);

                        itemList.add(item);
                    }

                    adapter.notifyDataSetChanged();

                    if (progressBar != null)
                        progressBar.setVisibility(View.GONE);
                });
    }
}



















//package com.example.paktrainfoodapp.ui.main.Passenger;
//
//import android.os.Bundle;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.*;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.fragment.app.Fragment;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.bumptech.glide.Glide;
//import com.example.paktrainfoodapp.R;
//import com.example.paktrainfoodapp.CartManager;
//import com.google.firebase.firestore.FirebaseFirestore;
//import com.google.firebase.firestore.QueryDocumentSnapshot;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class Station_Menu_Fragment extends Fragment {
//
//    private ImageView imgRestaurant;
//    private TextView txtRestaurantName, txtRestaurantInfo;
//    private RecyclerView recyclerView;
//    private ProgressBar progressBar;
//
//    private final List<MenuitemModel> itemList = new ArrayList<>();
//    private MenuAdapter adapter;
//    private FirebaseFirestore db;
//
//    private String restaurantId = "";
//    private String restaurantName = "";
//    private String restaurantImageUrl = "";
//    private String restaurantInfo = "";
//    private String mealStation = "";
//
//    private String trainId = "";
//    private String routeId = "";
//    private String fromStation = "";
//    private String toStation = "";
//    private String trainName = "";
//
//    @Nullable
//    @Override
//    public View onCreateView(@NonNull LayoutInflater inflater,
//                             @Nullable ViewGroup container,
//                             @Nullable Bundle savedInstanceState) {
//
//        return inflater.inflate(R.layout.fragment_passanger_station__menu_, container, false);
//    }
//
//    @Override
//    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
//        super.onViewCreated(view, savedInstanceState);
//
//        imgRestaurant = view.findViewById(R.id.img_restaurant);
//        txtRestaurantName = view.findViewById(R.id.tv_restaurant_name);
//        txtRestaurantInfo = view.findViewById(R.id.tv_restaurant_info);
//        recyclerView = view.findViewById(R.id.recyclerMenu);
//        progressBar = view.findViewById(R.id.progressBar);
//
//        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
//        adapter = new MenuAdapter(itemList);
//        recyclerView.setAdapter(adapter);
//
//        db = FirebaseFirestore.getInstance();
//
//        // ================= ARGUMENTS =================
//        Bundle args = getArguments();
//        if (args != null) {
//
//            restaurantId = args.getString("RESTAURANT_UID", "");
//            restaurantName = args.getString("RESTAURANT_NAME", "");
//            restaurantImageUrl = args.getString("RESTAURANT_IMAGE_URL",
//                    args.getString("RESTAURANT_IMAGE", ""));
//            restaurantInfo = args.getString("RESTAURANT_INFO", "Fast Food");
//
//            mealStation = args.getString("MEAL_STATION", "");
//
//            trainId = args.getString("TRAIN_ID", "");
//            routeId = args.getString("ROUTE_ID", "");
//            fromStation = args.getString("FROM", "");
//            toStation = args.getString("TO", "");
//            trainName = args.getString("TRAIN_NAME", "");
//        }
//
//        txtRestaurantName.setText(restaurantName);
//        txtRestaurantInfo.setText(restaurantInfo);
//
//        Glide.with(this)
//                .load(restaurantImageUrl)
//                .placeholder(R.drawable.station3)
//                .error(R.drawable.station3)
//                .centerCrop()
//                .into(imgRestaurant);
//
//        loadMenuItemsByRestaurantId(restaurantId);
//
//        // ================= ITEM CLICK =================
//        adapter.setOnMenuItemClickListener(new MenuAdapter.OnMenuItemClickListener() {
//
//            @Override
//            public void onAddToCart(MenuitemModel item) {
//
//                if (getParentFragment() instanceof Passenger_Fragment_Loader) {
//
//                    Passenger_Fragment_Loader loader =
//                            (Passenger_Fragment_Loader) getParentFragment();
//
//                    Passanger_ItemDetailsFragment detailsFragment =
//                            Passanger_ItemDetailsFragment.newInstance(
//                                    item,
//                                    restaurantId,
//                                    restaurantName,
//                                    mealStation,
//                                    trainId,
//                                    routeId,
//                                    fromStation,
//                                    toStation,
//                                    trainName
//                            );
//
//                    loader.getChildFragmentManager()
//                            .beginTransaction()
//                            .hide(loader.getActiveFragment())
//                            .add(R.id.fragment_holder, detailsFragment)
//                            .addToBackStack(null)
//                            .commit();
//                }
//            }
//
//            @Override
//            public void onBuyNow(MenuitemModel item) {
//
//                if (!isAdded()) return;
//
//                double price = 0.0;
//
//                if (item.getVariations() != null &&
//                        !item.getVariations().isEmpty()) {
//
//                    price = item.getVariations()
//                            .values()
//                            .iterator()
//                            .next();
//                }
//
//                // 🔥 FIX: cartItems ADD KARO (IMPORTANT)
//                ArrayList<CartItem> cartItems = new ArrayList<>();
//
//                CartItem tempItem = new CartItem(
//                        item.getId(),
//                        restaurantId,
//                        restaurantName,
//                        item.getName(),
//                        price,
//                        1,
//                        "",
//                        item.getImageUrl(),
//                        item.getDescription(),
//                        mealStation,
//                        trainId,
//                        routeId,
//                        fromStation,
//                        toStation,
//                        trainName
//                );
//
//                cartItems.add(tempItem);
//
//                OrderNowFragment dialog = OrderNowFragment.newInstance(
//                        item.getName(),
//                        price,
//                        item.getDescription(),
//                        restaurantName,
//                        restaurantId,
//                        item.getImageUrl(),
//                        mealStation,
//                        trainId,
//                        routeId,
//                        fromStation,
//                        toStation,
//                        cartItems   // 🔥 FIXED HERE
//                );
//
//                dialog.show(getParentFragmentManager(), "OrderNowDialog");
//            }
//
//            @Override
//            public void onDeleteOrder(MenuitemModel item) {}
//        });
//
//        CartUIHelper.setupCartBar(view, this);
//    }
//
//    // ================= LOAD MENU =================
//    private void loadMenuItemsByRestaurantId(String restaurantId) {
//
//        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
//
//        itemList.clear();
//
//        db.collection("Users")
//                .document("Restaurant")
//                .collection("VerifiedRegister")
//                .document(restaurantId)
//                .collection("MenuItems")
//                .get()
//                .addOnSuccessListener(menuItems -> {
//
//                    if (!isAdded() || getContext() == null) return;
//
//                    for (QueryDocumentSnapshot doc : menuItems) {
//
//                        MenuitemModel item = doc.toObject(MenuitemModel.class);
//
//                        item.setId(doc.getId());
//                        item.setRestaurantId(restaurantId);
//                        item.setRestaurantName(restaurantName);
//
//                        itemList.add(item);
//                    }
//
//                    adapter.notifyDataSetChanged();
//
//                    if (progressBar != null)
//                        progressBar.setVisibility(View.GONE);
//                });
//    }
//}
//
//
