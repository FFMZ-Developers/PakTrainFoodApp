package com.example.paktrainfoodapp.ui.main.Restaurant.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Lets a restaurant owner view and update their restaurant name/phone.
 * Email is shown but not editable.
 */
public class PersonalInfoFragment extends Fragment {

    private TextInputEditText editName, editPhone;
    private TextView editEmail;
    private Button btnSave;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

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

        View btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (isAdded()) {
                getParentFragmentManager().popBackStack();
            }
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        loadData();

        btnSave.setOnClickListener(v -> saveData());
    }

    private String uid() {
        return mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : null;
    }

    private void loadData() {

        String uid = uid();
        if (uid == null) return;

        db.collection("Users")
                .document("Restaurant")
                .collection("VerifiedRegister")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!isAdded() || !snapshot.exists()) return;

                    String name = snapshot.getString("restaurantName");
                    String phone = snapshot.getString("phone");
                    String email = snapshot.getString("email");

                    editName.setText(name != null ? name : "");
                    editPhone.setText(phone != null ? phone : "");
                    editEmail.setText(email != null ? email : "");
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Failed to load: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveData() {

        String uid = uid();
        if (uid == null) return;

        String name = editName.getText() != null ? editName.getText().toString().trim() : "";
        String phone = editPhone.getText() != null ? editPhone.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "Restaurant name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(requireContext(), "Phone cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("restaurantName", name);
        updates.put("phone", phone);

        db.collection("Users")
                .document("Restaurant")
                .collection("VerifiedRegister")
                .document(uid)
                .update(updates)
                .addOnSuccessListener(unused -> {

                    if (!isAdded()) return;

                    btnSave.setEnabled(true);

                    Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();

                    getParentFragmentManager().popBackStack();
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    btnSave.setEnabled(true);

                    Toast.makeText(requireContext(), "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
