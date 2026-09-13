package com.example.paktrainfoodapp.ui.main.Delivery.order;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Delivery.DeliveryBoyAdapter;
import com.example.paktrainfoodapp.ui.main.Delivery.DeliveryBoyModel;
import com.example.paktrainfoodapp.ui.main.Restaurant.order.OrderDetailFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.Arrays;

public class Order_Accept_Fragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout layoutNoOrders;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private ArrayList<DeliveryBoyModel> orderList;
    private DeliveryBoyAdapter adapter;

    private String riderId;

    // Module: report-a-problem evidence photo. Uses the same
    // TakePicturePreview + DocumentUploader path the verification wizard's
    // selfie step uses, so no FileProvider setup is needed for this one
    // capture.
    private android.graphics.Bitmap capturedReportPhoto;
    private android.widget.ImageView pendingReportPreview;
    private androidx.activity.result.ActivityResultLauncher<Void> reportPhotoLauncher;

    @Override
    public void onCreate(@androidx.annotation.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        reportPhotoLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview(),
                bitmap -> {

                    if (bitmap == null) return;

                    capturedReportPhoto = bitmap;

                    if (pendingReportPreview != null) {
                        pendingReportPreview.setVisibility(View.VISIBLE);
                        pendingReportPreview.setImageBitmap(bitmap);
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_delivery_order_new_accept_complete,
                container,
                false
        );

        recyclerView = view.findViewById(R.id.recyclerOrders);
        layoutNoOrders = view.findViewById(R.id.layoutNoOrders);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        orderList = new ArrayList<>();

        riderId = (auth.getCurrentUser() != null)
                ? auth.getCurrentUser().getUid()
                : "";

        adapter = new DeliveryBoyAdapter(requireContext(), orderList,
                new DeliveryBoyAdapter.OnActionClick() {

                    @Override
                    public void onItemClick(DeliveryBoyModel order, int position) {
                        openOrderDetail(order);
                    }

                    @Override
                    public void onAccept(DeliveryBoyModel order, int position) {
                        handleAction(order, position);
                    }

                    @Override
                    public void onButtonClick(DeliveryBoyModel order, int position) {
                        handleAction(order, position);
                    }

                    @Override
                    public void onReportProblem(DeliveryBoyModel order, int position) {
                        showReportProblemDialog(order, position);
                    }
                });

        recyclerView.setAdapter(adapter);

        loadAcceptedOrders();

        return view;
    }

    // ================= LOAD =================
    private void loadAcceptedOrders() {

        if (riderId == null || riderId.isEmpty()) return;

        db.collection("Orders")
                .whereEqualTo("acceptedBy", riderId)
                // ✅ FIX: pick_up added. The rider still has work to do at
                // that point (drive to the station and hand over), so it
                // belongs in the active tab - it was sitting in "Completed"
                // instead, which read as finished when it wasn't.
                .whereIn("orderStatus", Arrays.asList(
                        "accepted_by_rider",
                        "arrive_rider_at_resturent",
                        "dropped",
                        "pick_up"
                ))
                .addSnapshotListener((query, e) -> {

                    if (e != null || query == null) return;

                    orderList.clear();

                    for (QueryDocumentSnapshot doc : query) {

                        DeliveryBoyModel order =
                                new DeliveryBoyModel(
                                        doc.getId(),
                                        doc.getDouble("totalPrice") != null
                                                ? doc.getDouble("totalPrice")
                                                : 0.0,
                                        doc.getReference().getPath()
                                );

                        order.setStatus(doc.getString("orderStatus"));
                        order.setOrderNumber(doc.getLong("orderNumber"));
                        order.setRestaurantName(doc.getString("restaurantName"));

                        Long trainEta = doc.getLong("trainEtaEndTime");
                        order.setTrainEtaEndTime(trainEta != null ? trainEta : 0L);
                        orderList.add(order);
                    }

                    adapter.notifyDataSetChanged();

                    boolean empty = orderList.isEmpty();
                    recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                    layoutNoOrders.setVisibility(empty ? View.VISIBLE : View.GONE);
                });
    }

    // ================= FLOW LOGIC =================
    private void handleAction(DeliveryBoyModel order, int position) {

        String status = order.getStatus();

        // 🟢 STEP 1 → ARRIVED
        if ("accepted_by_rider".equals(status)) {

            updateStatus(order, "arrive_rider_at_resturent");
        }

        // 🟡 STEP 2 → WAIT (no action)
        else if ("arrive_rider_at_resturent".equals(status)) {

            Toast.makeText(getContext(),
                    "Wait for restaurant to drop order",
                    Toast.LENGTH_SHORT).show();
        }

        // 🔵 STEP 3 → PICKUP
        else if ("dropped".equals(status)) {

            new AlertDialog.Builder(requireContext())
                    .setTitle("Confirm Pickup")
                    .setMessage("Kya aap order pick karna chahte hain?")
                    .setPositiveButton("YES", (dialog, which) -> {

                        updateStatus(order, "pick_up");

                        // Stays in this tab now (pick_up is still active
                        // work) - just refresh the row so the button
                        // becomes "Hand Over to Passenger".
                        order.setStatus("pick_up");
                        adapter.notifyItemChanged(position);
                    })
                    .setNegativeButton("NO", null)
                    .show();
        }

        // 🔵 STEP 4 → HAND OVER (final step; moves the order to Completed)
        //
        // ✅ FIX: tapping "Hand Over to Passenger" used to complete the
        // order on a plain YES/NO confirm - a rider could mark any order
        // completed without the passenger actually being there. Now the
        // rider must enter the OTP that was sent to the passenger (see
        // onOrderPickedUp.js, which generates it and stores it on the
        // order as "deliveryOtp" the moment the rider picks up). Only a
        // correct OTP moves the order to "completed".
        else if ("pick_up".equals(status)) {

            showDeliveryOtpDialog(order, position);
        }
    }

    // ================= MODULE: DELIVERY OTP VERIFICATION =================
    private void showDeliveryOtpDialog(DeliveryBoyModel order, int position) {

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_delivery_otp, null);

        android.widget.EditText input = dialogView.findViewById(R.id.edit_delivery_otp);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Verify OTP")
                .setView(dialogView)
                .setPositiveButton("Verify", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        // Set the click listener AFTER show() so a wrong/empty OTP doesn't
        // auto-dismiss the dialog - the rider should be able to just retry.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

            String entered = input.getText() != null
                    ? input.getText().toString().trim()
                    : "";

            if (entered.isEmpty()) {
                input.setError("Enter the OTP");
                return;
            }

            verifyDeliveryOtp(order, position, entered, dialog);
        });
    }

    /**
     * Re-reads the order doc fresh (rather than trusting anything cached
     * client-side) and compares against the "deliveryOtp" field written by
     * onOrderPickedUp.js. Only on a match does the order move to
     * "completed".
     */
    private void verifyDeliveryOtp(DeliveryBoyModel order, int position,
                                   String entered, AlertDialog dialog) {

        db.collection("Orders")
                .document(order.getOrderId())
                .get()
                .addOnSuccessListener(doc -> {

                    if (!isAdded()) return;

                    String correctOtp = doc.getString("deliveryOtp");

                    if (correctOtp == null || !correctOtp.equals(entered)) {

                        Toast.makeText(getContext(),
                                "Galat OTP. Dobara koshish karein.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    dialog.dismiss();

                    updateStatus(order, "completed");

                    if (position != RecyclerView.NO_POSITION
                            && position < orderList.size()) {
                        orderList.remove(position);
                        adapter.notifyItemRemoved(position);
                    }

                    Toast.makeText(getContext(),
                            "Order Delivered",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(),
                                "OTP verify nahi ho saka: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ================= DETAIL =================
    private void openOrderDetail(DeliveryBoyModel order) {

        OrderDetailFragment fragment =
                OrderDetailFragment.newInstance(order.getOrderId());

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    // ================= MODULE 6 (FAILURE 3) =================
    //
    // Rider reports they can't complete this delivery. Backend
    // (onDeliveryFailed.js) branches on whether the food was already
    // picked up: pays the restaurant + a reduced fee to the rider +
    // refunds the rest to the passenger if so, otherwise cancels and
    // refunds the passenger in full. Either way the rider gets a strike.
    private void showReportProblemDialog(DeliveryBoyModel order, int position) {

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_report_problem, null);

        android.widget.EditText input = dialogView.findViewById(R.id.edit_report_reason);
        android.widget.ImageView imgPreview = dialogView.findViewById(R.id.img_report_photo);
        android.widget.Button btnPhoto = dialogView.findViewById(R.id.btn_take_photo);

        btnPhoto.setOnClickListener(v -> reportPhotoLauncher.launch(null));

        pendingReportPreview = imgPreview;

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Report a Problem")
                .setMessage("Wajah likhein - admin isay dekh kar payment decide karega. Photo lagana behtar hai (misaal: khana wapis laaya, passenger nahi mila).")
                .setView(dialogView)
                .setPositiveButton("Submit Report", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        // Set the click listener AFTER show() so a validation failure
        // doesn't auto-dismiss the dialog and lose what they typed.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

            String reason = input.getText() != null ? input.getText().toString().trim() : "";

            if (reason.isEmpty()) {
                input.setError("Please describe what happened");
                return;
            }

            dialog.dismiss();

            submitReport(order, position, reason);
        });
    }

    /**
     * Uploads the evidence photo (if one was taken) then flips the order
     * to "delivery_failed" - which onDeliveryFailed.js picks up, freezing
     * the order for admin review with the full timeline and both parties'
     * last known locations attached.
     */
    private void submitReport(DeliveryBoyModel order, int position, String reason) {

        Toast.makeText(getContext(), "Submitting report...", Toast.LENGTH_SHORT).show();

        if (capturedReportPhoto == null) {
            writeReportToFirestore(order, position, reason, null);
            return;
        }

        com.example.paktrainfoodapp.utils.DocumentUploader.uploadBitmap(
                "delivery", riderId, "report_" + order.getOrderId(), capturedReportPhoto,
                new com.example.paktrainfoodapp.utils.DocumentUploader.UploadCallback() {

                    @Override
                    public void onSuccess(String downloadUrl) {
                        writeReportToFirestore(order, position, reason, downloadUrl);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        // Photo upload failing shouldn't block the report
                        // itself - the reason and timeline still matter.
                        writeReportToFirestore(order, position, reason, null);
                    }
                });
    }

    private void writeReportToFirestore(DeliveryBoyModel order, int position,
                                        String reason, String photoUrl) {

        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("orderStatus", "delivery_failed");
        updates.put("failureReason", reason);
        updates.put("failureReportedAt", System.currentTimeMillis());
        updates.put("failureReportedBy", "rider");

        if (photoUrl != null) {
            updates.put("failurePhotoUrl", photoUrl);
        }

        db.collection("Orders")
                .document(order.getOrderId())
                .update(updates)
                .addOnSuccessListener(unused -> {

                    if (!isAdded()) return;

                    capturedReportPhoto = null;

                    Toast.makeText(getContext(),
                            "Reported - admin will review and process payment.",
                            Toast.LENGTH_LONG).show();

                    if (position != RecyclerView.NO_POSITION &&
                            position < orderList.size()) {
                        orderList.remove(position);
                        adapter.notifyItemRemoved(position);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ================= UPDATE =================
    private void updateStatus(DeliveryBoyModel order, String status) {

        db.collection("Orders")
                .document(order.getOrderId())
                .update("orderStatus", status);
    }
}


