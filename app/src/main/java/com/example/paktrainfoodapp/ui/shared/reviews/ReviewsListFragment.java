package com.example.paktrainfoodapp.ui.shared.reviews;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;

/**
 * Shows every rating + comment a restaurant or a rider has ever received,
 * with the average at the top - opened by tapping the "Rating" card on
 * either dashboard (see resturent_DashboardFragment / DeliveryHomeFragment).
 * Same underlying data (Users/{Restaurant|Delivery}/VerifiedRegister/
 * {targetId}/Reviews) and the same review row layout the passenger-facing
 * restaurant page already uses, just read-only here.
 */
public class ReviewsListFragment extends Fragment {

    private static final String ARG_ROOT = "root";
    private static final String ARG_TARGET_ID = "targetId";
    private static final String ARG_TITLE = "title";

    public static final String ROOT_RESTAURANT = "Restaurant";
    public static final String ROOT_DELIVERY = "Delivery";

    public static ReviewsListFragment newInstance(String root, String targetId, String title) {

        ReviewsListFragment f = new ReviewsListFragment();

        Bundle b = new Bundle();
        b.putString(ARG_ROOT, root);
        b.putString(ARG_TARGET_ID, targetId);
        b.putString(ARG_TITLE, title);
        f.setArguments(b);

        return f;
    }

    private LinearLayout layoutReviewsList;
    private TextView txtAverageRating, txtReviewCount, txtReviewsTitle;
    private RatingBar ratingBarAverage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reviews_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutReviewsList = view.findViewById(R.id.layoutReviewsList);
        txtAverageRating = view.findViewById(R.id.txtAverageRating);
        txtReviewCount = view.findViewById(R.id.txtReviewCount);
        ratingBarAverage = view.findViewById(R.id.ratingBarAverage);
        txtReviewsTitle = view.findViewById(R.id.txtReviewsTitle);

        ImageView btnBack = view.findViewById(R.id.btnReviewsBack);
        btnBack.setOnClickListener(v -> {
            // getParentFragmentManager() rather than the Activity's - this
            // screen gets opened from two different places (the
            // restaurant dashboard replaces into the Activity's own
            // container, the rider dashboard replaces into its own child
            // container), so popping needs to target whichever manager
            // actually added this fragment, not always the Activity's.
            if (isAdded()) getParentFragmentManager().popBackStack();
        });

        Bundle args = getArguments();
        String root = args != null ? args.getString(ARG_ROOT) : ROOT_RESTAURANT;
        String targetId = args != null ? args.getString(ARG_TARGET_ID) : null;
        String title = args != null ? args.getString(ARG_TITLE) : null;

        if (txtReviewsTitle != null && title != null) {
            txtReviewsTitle.setText(title);
        }

        loadReviews(root, targetId);
    }

    private void loadReviews(String root, String targetId) {

        if (targetId == null || layoutReviewsList == null || !isAdded()) return;

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("Users").document(root)
                .collection("VerifiedRegister").document(targetId)
                .collection("Reviews")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {

                    if (!isAdded() || layoutReviewsList == null) return;

                    layoutReviewsList.removeAllViews();

                    if (error != null || snap == null || snap.isEmpty()) {

                        txtAverageRating.setText("\u2014");
                        ratingBarAverage.setRating(0f);
                        txtReviewCount.setText("No ratings yet");

                        TextView empty = new TextView(requireContext());
                        empty.setText("No reviews yet.");
                        empty.setTextColor(0xFF888888);
                        empty.setTextSize(13);
                        layoutReviewsList.addView(empty);
                        return;
                    }

                    double total = 0;
                    int count = 0;

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {

                        Double rating = doc.getDouble("rating");
                        if (rating != null) {
                            total += rating;
                            count++;
                        }

                        bindReviewRow(doc);
                    }

                    float average = count > 0 ? (float) (total / count) : 0f;

                    txtAverageRating.setText(String.format(java.util.Locale.getDefault(), "%.1f", average));
                    ratingBarAverage.setRating(average);
                    txtReviewCount.setText(count + (count == 1 ? " rating" : " ratings"));
                });
    }

    private void bindReviewRow(com.google.firebase.firestore.DocumentSnapshot doc) {

        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.passanger_tem_review, layoutReviewsList, false);

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

        Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.edit_info)
                .error(R.drawable.edit_info)
                .circleCrop()
                .into(imgUser);

        if (createdAt != null && createdAt > 0) {

            String when = new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                    .format(new java.util.Date(createdAt));

            tvComment.setText(tvComment.getText() + "\n" + when);
        }

        layoutReviewsList.addView(row);

        View divider = new View(requireContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(0xFFE0E0E0);
        layoutReviewsList.addView(divider);
    }
}
