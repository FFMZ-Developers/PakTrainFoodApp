package com.example.paktrainfoodapp.ui.shared.profile;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One "Personal Info" screen for all three roles.
 *
 * The three roles differ only in where their profile document lives and what
 * the name field is called, so those two things are derived from the role
 * instead of being three separate near-identical fragments.
 */
public class PersonalInfoFragment extends Fragment {

    public static final String ROLE_PASSENGER = "PASSENGER";
    public static final String ROLE_RESTAURANT = "RESTAURANT";
    public static final String ROLE_DELIVERY = "DELIVERY";

    private static final String ARG_ROLE = "personal_info_role";

    public static PersonalInfoFragment newInstance(String role) {

        PersonalInfoFragment fragment = new PersonalInfoFragment();

        Bundle args = new Bundle();
        args.putString(ARG_ROLE, role);
        fragment.setArguments(args);

        return fragment;
    }

    private TextInputEditText editName, editPhone;
    private TextView editEmail, txtTitle, txtNameLabel;
    private Button btnSave;

    // Restaurant-only: opening hours. Built programmatically (this layout
    // is shared across all 3 roles) and only inserted when role() ==
    // ROLE_RESTAURANT.
    private TextView txtOpeningTime, txtClosingTime;
    private String openingTime = "10:00";
    private String closingTime = "22:00";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String role() {
        return getArguments() != null
                ? getArguments().getString(ARG_ROLE, ROLE_PASSENGER)
                : ROLE_PASSENGER;
    }

    /** Restaurants store their display name under a different field. */
    private String nameField() {
        return ROLE_RESTAURANT.equals(role()) ? "restaurantName" : "name";
    }

    private String nameLabel() {

        switch (role()) {
            case ROLE_RESTAURANT: return "Restaurant Name";
            case ROLE_DELIVERY:   return "Rider Name";
            default:              return "Full Name";
        }
    }

    /** Passengers live under Register; the approved roles under VerifiedRegister. */
    private DocumentReference docRef() {

        String uid = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;

        if (uid == null) return null;

        switch (role()) {

            case ROLE_RESTAURANT:
                return db.collection("Users").document("Restaurant")
                        .collection("VerifiedRegister").document(uid);

            case ROLE_DELIVERY:
                return db.collection("Users").document("Delivery")
                        .collection("VerifiedRegister").document(uid);

            default:
                return db.collection("Users").document("Passenger")
                        .collection("Register").document(uid);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_personal_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        editName = view.findViewById(R.id.edit_personal_name);
        editPhone = view.findViewById(R.id.edit_personal_phone);
        editEmail = view.findViewById(R.id.edit_personal_email);
        btnSave = view.findViewById(R.id.btn_save_personal_info);
        txtNameLabel = view.findViewById(R.id.txt_name_label);

        if (txtNameLabel != null) txtNameLabel.setText(nameLabel());

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (isAdded()) getParentFragmentManager().popBackStack();
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (ROLE_RESTAURANT.equals(role())) {
            addOpeningHoursUi();
        }

        loadData();

        btnSave.setOnClickListener(v -> saveData());
    }

    private void addOpeningHoursUi() {

        ViewGroup container = (ViewGroup) btnSave.getParent();
        int saveButtonIndex = container.indexOfChild(btnSave);

        float density = getResources().getDisplayMetrics().density;

        TextView label = new TextView(requireContext());
        label.setText("Opening Hours");
        label.setTextColor(0xFF757575);
        label.setTextSize(13);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.bottomMargin = (int) (6 * density);
        label.setLayoutParams(labelParams);
        container.addView(label, saveButtonIndex++);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = (int) (24 * density);
        row.setLayoutParams(rowParams);

        txtOpeningTime = new TextView(requireContext());
        txtOpeningTime.setTextSize(15);
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txtOpeningTime.setLayoutParams(openParams);
        txtOpeningTime.setText("Opens: " + to12Hour(openingTime));
        txtOpeningTime.setOnClickListener(v -> pickTime(true));
        row.addView(txtOpeningTime);

        txtClosingTime = new TextView(requireContext());
        txtClosingTime.setTextSize(15);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        txtClosingTime.setLayoutParams(closeParams);
        txtClosingTime.setText("Closes: " + to12Hour(closingTime));
        txtClosingTime.setOnClickListener(v -> pickTime(false));
        row.addView(txtClosingTime);

        container.addView(row, saveButtonIndex);
    }

    private void pickTime(boolean isOpening) {

        String current = isOpening ? openingTime : closingTime;
        String[] parts = current.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        new TimePickerDialog(requireContext(), (view, selectedHour, selectedMinute) -> {

            String formatted = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute);

            if (isOpening) {
                openingTime = formatted;
                txtOpeningTime.setText("Opens: " + to12Hour(formatted));
            } else {
                closingTime = formatted;
                txtClosingTime.setText("Closes: " + to12Hour(formatted));
            }

        }, hour, minute, false).show();
    }

    private String to12Hour(String hhmm) {

        try {
            String[] parts = hhmm.split(":");
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            return new java.text.SimpleDateFormat("hh:mm a", Locale.US).format(cal.getTime());
        } catch (Exception e) {
            return hhmm;
        }
    }

    private void loadData() {

        DocumentReference ref = docRef();
        if (ref == null) return;

        ref.get()
                .addOnSuccessListener(snapshot -> {

                    if (!isAdded() || !snapshot.exists()) return;

                    String name = snapshot.getString(nameField());
                    String phone = snapshot.getString("phone");
                    String email = snapshot.getString("email");

                    editName.setText(name != null ? name : "");
                    editPhone.setText(phone != null ? phone : "");
                    editEmail.setText(email != null ? email : "");

                    if (ROLE_RESTAURANT.equals(role()) && txtOpeningTime != null) {

                        String savedOpening = snapshot.getString("openingTime");
                        String savedClosing = snapshot.getString("closingTime");

                        if (!TextUtils.isEmpty(savedOpening)) {
                            openingTime = savedOpening;
                            txtOpeningTime.setText("Opens: " + to12Hour(openingTime));
                        }

                        if (!TextUtils.isEmpty(savedClosing)) {
                            closingTime = savedClosing;
                            txtClosingTime.setText("Closes: " + to12Hour(closingTime));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "Failed to load: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveData() {

        DocumentReference ref = docRef();
        if (ref == null) return;

        String name = editName.getText() != null ? editName.getText().toString().trim() : "";
        String phone = editPhone.getText() != null ? editPhone.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), nameLabel() + " cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(requireContext(), "Phone cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put(nameField(), name);
        updates.put("phone", phone);

        if (ROLE_RESTAURANT.equals(role()) && txtOpeningTime != null) {
            updates.put("openingTime", openingTime);
            updates.put("closingTime", closingTime);
        }

        ref.update(updates)
                .addOnSuccessListener(unused -> {

                    if (!isAdded()) return;

                    btnSave.setEnabled(true);

                    Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();

                    getParentFragmentManager().popBackStack();
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    btnSave.setEnabled(true);

                    Toast.makeText(requireContext(),
                            "Update failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}
