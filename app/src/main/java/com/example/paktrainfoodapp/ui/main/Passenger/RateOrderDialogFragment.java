package com.example.paktrainfoodapp.ui.main.Passenger;

import android.app.Dialog;
import android.content.DialogInterface;
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
 * reviewed it yet (see the checks in Passenger_Fragment_Loader.java,
 * which is what actually decides WHEN to show this - this class just
 * handles the form itself). The review is written keyed by orderId, so
 * it's a genuine "one review per order" - reopening this same dialog for
 * an already-reviewed order pre-fills nothing and just overwrites on
 * submit rather than creating a duplicate.
 *
 * ✅ FIX: this used to only ever rate the restaurant. Same dialog now
 * rates either the restaurant OR the rider - which one is decided by
 * TARGET_TYPE - so the rider gets rated too (Passenger_Fragment_Loader
 * chains this dialog twice: restaurant first, then rider).
 */
public class RateOrderDialogFragment extends DialogFragment {

    private static final String ARG_ORDER_ID = "orderId";
    private static final String ARG_TARGET_TYPE = "targetType";
    private static final String ARG_TARGET_ID = "targetId";
    private static final String ARG_TARGET_NAME = "targetName";

    public static final String TARGET_RESTAURANT = "restaurant";
    public static final String TARGET_RIDER = "rider";

    public static RateOrderDialogFragment forRestaurant(String orderId, String restaurantId, String restaurantName) {
        return newInstance(orderId, TARGET_RESTAURANT, restaurantId, restaurantName);
    }

    public static RateOrderDialogFragment forRider(String orderId, String riderId, String riderName) {
        return newInstance(orderId, TARGET_RIDER, riderId, riderName);
    }

    private static RateOrderDialogFragment newInstance(String orderId, String targetType, String targetId, String targetName) {

        RateOrderDialogFragment f = new RateOrderDialogFragment();

        Bundle b = new Bundle();
        b.putString(ARG_ORDER_ID, orderId);
        b.putString(ARG_TARGET_TYPE, targetType);
        b.putString(ARG_TARGET_ID, targetId);
        b.putString(ARG_TARGET_NAME, targetName);
        f.setArguments(b);

        return f;
    }

    private RatingBar ratingBar;
    private EditText editComment;

    /** Fires once the dialog closes, however it closed (submit or skip) -
     *  set by Passenger_Fragment_Loader so it can chain the rider dialog
     *  right after the restaurant one. */
    private Runnable onClosedCallback;

    public void setOnClosedCallback(Runnable callback) {
        this.onClosedCallback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_rate_order, null);

        Bundle args = getArguments();

        String orderId = args != null ? args.getString(ARG_ORDER_ID) : null;
        String targetType = args != null ? args.getString(ARG_TARGET_TYPE) : TARGET_RESTAURANT;
        String targetId = args != null ? args.getString(ARG_TARGET_ID) : null;
        String targetName = args != null ? args.getString(ARG_TARGET_NAME) : null;

        boolean isRider = TARGET_RIDER.equals(targetType);

        TextView txtTitle = view.findViewById(R.id.txtRateTitle);
        txtTitle.setText(isRider
                ? "How was your delivery from " + (targetName != null ? targetName : "your rider") + "?"
                : "How was your food from " + (targetName != null ? targetName : "the restaurant") + "?");

        ratingBar = view.findViewById(R.id.ratingBarOrder);
        editComment = view.findViewById(R.id.editRateComment);

        Button btnSkip = view.findViewById(R.id.btnRateSkip);
        Button btnSubmit = view.findViewById(R.id.btnRateSubmit);

        setCancelable(false);

        btnSkip.setOnClickListener(v -> dismiss());

        btnSubmit.setOnClickListener(v -> submit(orderId, targetType, targetId));

        return new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }

    private void submit(String orderId, String targetType, String targetId) {

        if (orderId == null || targetId == null) {
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
        boolean isRider = TARGET_RIDER.equals(targetType);

        Map<String, Object> review = new HashMap<>();
        review.put("orderId", orderId);
        review.put("passengerUid", uid);
        review.put("passengerName", prefs.getUserName() != null ? prefs.getUserName() : "Passenger");
        review.put("passengerPhotoUrl", prefs.getUserImage());
        review.put("rating", rating);
        review.put("comment", editComment.getText() != null ? editComment.getText().toString().trim() : "");
        review.put("createdAt", System.currentTimeMillis());
        review.put("updatedAt", System.currentTimeMillis());

        String rootCollection = isRider ? "Delivery" : "Restaurant";
        String reviewedFlag = isRider ? "riderReviewSubmitted" : "reviewSubmitted";

        FirebaseFirestore.getInstance()
                .collection("Users").document(rootCollection)
                .collection("VerifiedRegister").document(targetId)
                .collection("Reviews").document(orderId)
                .set(review)
                .addOnSuccessListener(unused -> {

                    // Marks this order as reviewed (for this target) so
                    // it's never prompted again for that target,
                    // independent of the review doc itself (kept on the
                    // order so the "any unrated completed orders?" check
                    // doesn't need an extra read per order).
                    FirebaseFirestore.getInstance().collection("Orders").document(orderId)
                            .update(reviewedFlag, true);

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

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (onClosedCallback != null) onClosedCallback.run();
    }
}
