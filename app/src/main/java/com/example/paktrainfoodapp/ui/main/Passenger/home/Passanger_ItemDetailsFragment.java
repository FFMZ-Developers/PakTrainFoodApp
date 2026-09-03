package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.CartManager;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Passenger.MenuitemModel;

import java.util.Map;

public class Passanger_ItemDetailsFragment extends Fragment {

    private MenuitemModel item;

    private String restaurantId, restaurantName, mealStation, trainId, routeId, fromStation, toStation, trainName;

    private TextView tvPrice, tvName, tvDesc, tvQuantity;
    private LinearLayout layoutSizeButtons, layoutReviews;
    private ImageView imgItem;
    private Button btnAddToCart;
    private TextView btnMinus, btnPlus;

    private int quantity = 1;
    private double basePrice = 0;
    private String selectedSize = "";

    public static Passanger_ItemDetailsFragment newInstance(
            MenuitemModel item, String restaurantId, String restaurantName,
            String mealStation, String trainId, String routeId,
            String from, String to, String trainName) {

        Passanger_ItemDetailsFragment fragment = new Passanger_ItemDetailsFragment();
        Bundle args = new Bundle();
        args.putSerializable("item_data", item);
        args.putString("restaurantId", restaurantId);
        args.putString("restaurantName", restaurantName);
        args.putString("mealStation", mealStation);
        args.putString("trainId", trainId);
        args.putString("routeId", routeId);
        args.putString("from", from);
        args.putString("to", to);
        args.putString("trainName", trainName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {

            item = (MenuitemModel) getArguments().getSerializable("item_data");
            restaurantId = getArguments().getString("restaurantId");
            restaurantName = getArguments().getString("restaurantName");
            mealStation = getArguments().getString("mealStation");
            trainId = getArguments().getString("trainId");
            routeId = getArguments().getString("routeId");
            fromStation = getArguments().getString("from");
            toStation = getArguments().getString("to");
            trainName = getArguments().getString("trainName");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_passanger__item_details, container, false);

        imgItem = v.findViewById(R.id.imgItem);
        tvName = v.findViewById(R.id.tvName);
        tvDesc = v.findViewById(R.id.tvDesc);
        tvPrice = v.findViewById(R.id.tvPrice);
        layoutSizeButtons = v.findViewById(R.id.layoutSizeButtons);
        layoutReviews = v.findViewById(R.id.layoutReviews);
        btnAddToCart = v.findViewById(R.id.btnAddToCart);
        tvQuantity = v.findViewById(R.id.tvQuantity);
        btnMinus = v.findViewById(R.id.btnMinus);
        btnPlus = v.findViewById(R.id.btnPlus);

        // Module: real reviews - this was previously never populated at
        // all, an empty container with a "Customer Reviews" label above
        // it and nothing underneath. Reviews are restaurant-level (one per
        // completed order, from RateOrderDialogFragment), shown here since
        // this is where a passenger is actually deciding what to order.
        loadReviews();

        if (item != null) {

            tvName.setText(item.getName());
            tvDesc.setText(item.getDescription());

            Glide.with(this)
                    .load(item.getImageUrl())
                    .placeholder(R.drawable.ic_food_placeholder)
                    .into(imgItem);

            if (item.getVariations() != null && !item.getVariations().isEmpty()) {

                Map.Entry<String, Double> first =
                        item.getVariations().entrySet().iterator().next();

                basePrice = first.getValue();
                selectedSize = first.getKey();

                tvPrice.setText("Rs. " + (int) basePrice);

                boolean firstBtn = true;

                for (Map.Entry<String, Double> entry : item.getVariations().entrySet()) {

                    Button btn = (Button) inflater.inflate(
                            R.layout.passanger_item_size_button,
                            layoutSizeButtons,
                            false
                    );

                    btn.setText(entry.getKey());

                    if (firstBtn) {
                        btn.setBackgroundTintList(
                                ContextCompat.getColorStateList(requireContext(), R.color.green)
                        );
                        btn.setTextColor(Color.WHITE);
                        firstBtn = false;
                    }

                    btn.setOnClickListener(view -> {

                        basePrice = entry.getValue();
                        selectedSize = entry.getKey();
                        quantity = 1;
                        tvQuantity.setText("1");

                        updatePrice();

                        for (int i = 0; i < layoutSizeButtons.getChildCount(); i++) {

                            Button b = (Button) layoutSizeButtons.getChildAt(i);

                            b.setBackgroundTintList(
                                    ContextCompat.getColorStateList(requireContext(), R.color.gray)
                            );

                            b.setTextColor(Color.BLACK);
                        }

                        btn.setBackgroundTintList(
                                ContextCompat.getColorStateList(requireContext(), R.color.green)
                        );

                        btn.setTextColor(Color.WHITE);
                    });

                    layoutSizeButtons.addView(btn);
                }
            }
        }

        btnPlus.setOnClickListener(v1 -> {
            quantity++;
            tvQuantity.setText(String.valueOf(quantity));
            updatePrice();
        });

        btnMinus.setOnClickListener(v1 -> {
            if (quantity > 1) {
                quantity--;
                tvQuantity.setText(String.valueOf(quantity));
                updatePrice();
            }
        });

        btnAddToCart.setOnClickListener(view -> {

            if (item == null) return;

            CartItem cartItem = new CartItem(
                    item.getId(),
                    restaurantId,
                    restaurantName,
                    item.getName(),
                    basePrice,
                    quantity,
                    selectedSize,
                    item.getImageUrl(),
                    item.getDescription(),
                    mealStation,
                    trainId,
                    routeId,
                    fromStation,
                    toStation,
                    trainName
            );

            CartManager.addOrUpdate(cartItem);

            Toast.makeText(getContext(),
                    "Added to Cart",
                    Toast.LENGTH_SHORT).show();

            getParentFragmentManager().popBackStack();
        });

        return v;
    }

    private void updatePrice() {

        double finalPrice = basePrice * quantity;

        tvPrice.setText("Rs. " + (int) finalPrice);
    }

    /**
     * Loads every review for this restaurant (reviews are restaurant-level,
     * not per-dish - see RateOrderDialogFragment) and renders each one
     * using the existing passanger_tem_review.xml row layout, newest
     * first. A review belonging to the currently signed-in passenger gets
     * Edit/Delete controls; everyone else's reviews are read-only.
     */
    private void loadReviews() {

        if (restaurantId == null || layoutReviews == null || !isAdded()) return;

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(restaurantId)
                .collection("Reviews")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {

                    if (!isAdded() || layoutReviews == null) return;

                    layoutReviews.removeAllViews();

                    if (error != null || snap == null || snap.isEmpty()) {

                        TextView empty = new TextView(requireContext());
                        empty.setText("No reviews yet - be the first!");
                        empty.setTextColor(0xFF888888);
                        empty.setTextSize(13);
                        layoutReviews.addView(empty);
                        return;
                    }

                    String myUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                            ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        bindReviewRow(doc, myUid);
                    }
                });
    }

    private void bindReviewRow(com.google.firebase.firestore.DocumentSnapshot doc, String myUid) {

        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.passanger_tem_review, layoutReviews, false);

        ImageView imgUser = row.findViewById(R.id.imgUser);
        TextView tvUserName = row.findViewById(R.id.tvUserName);
        RatingBar ratingBar = row.findViewById(R.id.ratingBar);
        TextView tvComment = row.findViewById(R.id.tvComment);

        String name = doc.getString("passengerName");
        String photoUrl = doc.getString("passengerPhotoUrl");
        String comment = doc.getString("comment");
        Double rating = doc.getDouble("rating");
        Long createdAt = doc.getLong("createdAt");

        tvUserName.setText(name != null ? name : "Passenger");
        ratingBar.setRating(rating != null ? rating.floatValue() : 0f);
        tvComment.setText(comment != null && !comment.isEmpty() ? comment : "(No comment)");

        // ✅ FIX: the placeholder/error fallback here was the splash
        // screen's train photo (@drawable/hlo) - since most reviewers
        // don't have passengerPhotoUrl set, or Glide fails, every review
        // ended up showing a picture of a train instead of a person. Now
        // it falls back to the same generic profile-photo placeholder the
        // profile screens themselves already use.
        Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.edit_info)
                .error(R.drawable.edit_info)
                .circleCrop()
                .into(imgUser);

        // Timestamp - the row layout doesn't have a dedicated view for
        // this, so it's appended onto the comment area itself.
        if (createdAt != null && createdAt > 0) {

            String when = new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                    .format(new java.util.Date(createdAt));

            TextView tvWhen = new TextView(requireContext());
            tvWhen.setText(when);
            tvWhen.setTextSize(11);
            tvWhen.setTextColor(0xFF999999);
            tvWhen.setPadding(0, 4, 0, 0);

            ((LinearLayout) tvComment.getParent()).addView(tvWhen);
        }

        // Edit/Delete only for the passenger's own review.
        if (myUid != null && myUid.equals(doc.getString("passengerUid"))) {

            LinearLayout actionsRow = new LinearLayout(requireContext());
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setPadding(0, 8, 0, 0);

            TextView btnEdit = new TextView(requireContext());
            btnEdit.setText("Edit");
            btnEdit.setTextColor(0xFF00695C);
            btnEdit.setTextSize(12);
            btnEdit.setPadding(0, 0, 24, 0);
            btnEdit.setOnClickListener(v -> {

                if (!isAdded()) return;

                String orderId = doc.getString("orderId");
                if (orderId == null) return;

                com.example.paktrainfoodapp.ui.main.Passenger.RateOrderDialogFragment
                        .forRestaurant(orderId, restaurantId, restaurantName)
                        .show(getParentFragmentManager(), "edit_review");
            });

            TextView btnDelete = new TextView(requireContext());
            btnDelete.setText("Delete");
            btnDelete.setTextColor(0xFFC62828);
            btnDelete.setTextSize(12);
            btnDelete.setOnClickListener(v -> {

                if (!isAdded()) return;

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete your review?")
                        .setMessage("This can't be undone.")
                        .setPositiveButton("Delete", (d, w) -> {

                            doc.getReference().delete();

                            String orderId = doc.getString("orderId");

                            if (orderId != null) {
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("Orders").document(orderId)
                                        .update("reviewSubmitted", false);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });

            actionsRow.addView(btnEdit);
            actionsRow.addView(btnDelete);

            ((LinearLayout) tvComment.getParent()).addView(actionsRow);
        }

        layoutReviews.addView(row);
    }
}//