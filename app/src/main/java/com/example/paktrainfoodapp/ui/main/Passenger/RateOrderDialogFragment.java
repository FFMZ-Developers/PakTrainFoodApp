package com.example.paktrainfoodapp.ui.main.Passenger;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.utils.PrefManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Module: order-complete rating popup.
 *
 * Shown once an order reaches "completed" and the passenger hasn't
 * reviewed it yet (see the check in Passenger_Fragment_Loader.java, which
 * is what actually decides WHEN to show this - this class just handles
 * the form itself). The review is written keyed by orderId, so it's a
 * genuine "one review per order" - reopening this same dialog for an
 * already-reviewed order pre-fills the existing stars/comment and
 * overwrites on submit rather than creating a duplicate.
 */
public class RateOrderDialogFragment extends DialogFragment {

    private static final String ARG_ORDER_ID = "orderId";
    private static final String ARG_RESTAURANT_ID = "restaurantId";
    private static final String ARG_RESTAURANT_NAME = "restaurantName";

    public static RateOrderDialogFragment newInstance(String orderId, String restaurantId, String restaurantName) {

        RateOrderDialogFragment f = new RateOrderDialogFragment();

        Bundle b = new Bundle();
        b.putString(ARG_ORDER_ID, orderId);
        b.putString(ARG_RESTAURANT_ID, restaurantId);
        b.putString(ARG_RESTAURANT_NAME, restaurantName);
        f.setArguments(b);

        return f;
    }

    private RatingBar ratingBar;
    private EditText editComment;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_rate_order, null);

        Bundle args = getArguments();

        String orderId = args != null ? args.getString(ARG_ORDER_ID) : null;
        String restaurantId = args != null ? args.getString(ARG_RESTAURANT_ID) : null;
        String restaurantName = args != null ? args.getString(ARG_RESTAURANT_NAME) : "the restaurant";

        TextView txtTitle = view.findViewById(R.id.txtRateTitle);
        txtTitle.setText("How was your food from " + restaurantName + "?");

        ratingBar = view.findViewById(R.id.ratingBarOrder);
        editComment = view.findViewById(R.id.editRateComment);

        Button btnSkip = view.findViewById(R.id.btnRateSkip);
        Button btnSubmit = view.findViewById(R.id.btnRateSubmit);

        setCancelable(false);

        btnSkip.setOnClickListener(v -> dismiss());

        btnSubmit.setOnClickListener(v -> submit(orderId, restaurantId));

        return new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }

    private void submit(String orderId, String restaurantId) {

        if (orderId == null || restaurantId == null) {
            dismiss();
            return;
        }

        float rating = ratingBar.getRating();

        if (rating <= 0) {
            Toast.makeText(getContext(), "Please tap a star to rate", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid == null) {
            dismiss();
            return;
        }

        PrefManager prefs = new PrefManager(requireContext());

        Map<String, Object> review = new HashMap<>();
        review.put("orderId", orderId);
        review.put("passengerUid", uid);
        review.put("passengerName", prefs.getUserName() != null ? prefs.getUserName() : "Passenger");
        review.put("passengerPhotoUrl", prefs.getUserImage());
        review.put("rating", rating);
        review.put("comment", editComment.getText() != null ? editComment.getText().toString().trim() : "");
        review.put("createdAt", System.currentTimeMillis());
        review.put("updatedAt", System.currentTimeMillis());

        FirebaseFirestore.getInstance()
                .collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(restaurantId)
                .collection("Reviews").document(orderId)
                .set(review)
                .addOnSuccessListener(unused -> {

                    // Marks this order as reviewed so it's never prompted
                    // again, independent of the review doc itself (kept on
                    // the order so the "any unrated completed orders?"
                    // check doesn't need an extra read per order).
                    FirebaseFirestore.getInstance().collection("Orders").document(orderId)
                            .update("reviewSubmitted", true);

                    if (isAdded()) {
                        Toast.makeText(getContext(), "Thanks for your feedback!", Toast.LENGTH_SHORT).show();
                        dismiss();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), "Couldn't submit: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
