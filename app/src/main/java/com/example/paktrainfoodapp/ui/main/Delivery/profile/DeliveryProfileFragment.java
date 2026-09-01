package com.example.paktrainfoodapp.ui.main.Delivery.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.Splash;
import com.example.paktrainfoodapp.utils.PrefManager;
import com.example.paktrainfoodapp.utils.ProfileImageUploader;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class DeliveryProfileFragment extends Fragment {

    private ImageView profileImage;
    private ImageView btnEditProfile;
    private TextView txtName,riderWallet, txtEmail;
    private TextView btnLogout; // Badla hua type Card List ke mutabiq
    private LinearLayout layoutAccountInfo;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private PrefManager prefManager;

    private ActivityResultLauncher<String> galleryLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_delivery_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        profileImage = view.findViewById(R.id.img_delivery_profile);
        btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        txtName = view.findViewById(R.id.txt_delivery_name);
        txtEmail = view.findViewById(R.id.txt_delivery_email);
        btnLogout = view.findViewById(R.id.btn_delivery_logout); // Matching ID text reference
        riderWallet  = view.findViewById(R.id.riderWallet);
        layoutAccountInfo = view.findViewById(R.id.layout_account_info);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefManager = new PrefManager(requireContext());

        wireRow(view, R.id.layout_settings,
                new com.example.paktrainfoodapp.ui.shared.profile.SettingsFragment());

        wireRow(view, R.id.layout_my_orders,
                com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment.newInstance(
                        com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment.ROLE_DELIVERY));

        if (layoutAccountInfo != null) {

            layoutAccountInfo.setOnClickListener(v ->
                    getParentFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_loader, com.example.paktrainfoodapp.ui.shared.profile.PersonalInfoFragment.newInstance(com.example.paktrainfoodapp.ui.shared.profile.PersonalInfoFragment.ROLE_DELIVERY))
                            .addToBackStack("personal_info")
                            .commit());
        }

        View layoutUpdateVerification = view.findViewById(R.id.layout_update_verification);
        if (layoutUpdateVerification != null) {
            layoutUpdateVerification.setOnClickListener(v -> launchVerificationUpdate());
        }

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null && isAdded() && getActivity() != null) {
                            if (profileImage != null) {
                                Glide.with(requireActivity())
                                        .load(uri)
                                        .circleCrop()
                                        .into(profileImage);
                            }

                            if (mAuth.getCurrentUser() != null) {

                                String uid = mAuth.getCurrentUser().getUid();

                                Toast.makeText(getContext(), "Uploading photo...", Toast.LENGTH_SHORT).show();

                                ProfileImageUploader.upload(
                                        requireContext(),
                                        "delivery",
                                        uid,
                                        uri,
                                        new ProfileImageUploader.UploadCallback() {

                                            @Override
                                            public void onSuccess(String downloadUrl) {

                                                if (!isAdded()) return;

                                                db.collection("Users")
                                                        .document("Delivery")
                                                        .collection("VerifiedRegister")
                                                        .document(uid)
                                                        .update("profileImageUrl", downloadUrl);

                                                Toast.makeText(getContext(), "Delivery Rider Image Updated", Toast.LENGTH_SHORT).show();
                                            }

                                            @Override
                                            public void onFailure(Exception e) {

                                                if (!isAdded()) return;

                                                Toast.makeText(getContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }
                        }
                    }
                }
        );

        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        }

        if (mAuth.getCurrentUser() != null) {
            loadUserData();
        } else {
            if (txtName != null) txtName.setText("Guest");
            if (txtEmail != null) txtEmail.setText("");
        }

        //rider wallet open
        riderWallet.setOnClickListener(v -> {

            getParentFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.fragment_loader,
                            com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.newInstance(com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.ROLE_DELIVERY)
                    )
                    .addToBackStack("profile")
                    .commit();
        });

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                // Guarded by a confirmation - see LogoutConfirm.
                com.example.paktrainfoodapp.utils.LogoutConfirm.show(requireContext(), () -> {
                if (mAuth != null) {
                    mAuth.signOut();
                }
                if (prefManager != null) {
                    prefManager.setLogin(false);
                }
                if (getActivity() != null) {
                    getActivity().finish();
                }
                startActivity(new Intent(getContext(), Splash.class));
                });
            });
        }
    }

    private void loadUserData() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("Users")
                .document("Delivery")
                .collection("VerifiedRegister")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (isAdded() && snapshot.exists()) {
                        String deliveryBoyName = snapshot.getString("name");
                        String email = snapshot.getString("email");
                        String imageUrl = snapshot.getString("profileImageUrl");
                        if (imageUrl == null || imageUrl.isEmpty()) {
                            // Fallback for accounts registered before profileImageUrl existed
                            imageUrl = snapshot.getString("ownerCnicUrlfront");
                        }

                        if (txtName != null) txtName.setText(deliveryBoyName != null ? deliveryBoyName : "No Name");
                        if (txtEmail != null) txtEmail.setText(email != null ? email : "No Email");

                        if (imageUrl != null && !imageUrl.isEmpty() && profileImage != null) {
                            Glide.with(this)
                                    .load(imageUrl)
                                    .placeholder(R.drawable.edit_info)
                                    .error(R.drawable.edit_info)
                                    .circleCrop()
                                    .into(profileImage);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        if (txtName != null) txtName.setText("Error loading data");
                        if (txtEmail != null) txtEmail.setText("");
                        Toast.makeText(getContext(), "Failed to load profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Opens a shared screen from a profile row (see restaurant equivalent). */
    private void wireRow(View parent, int rowId, androidx.fragment.app.Fragment target) {

        View row = parent.findViewById(rowId);

        if (row == null) return;

        row.setOnClickListener(v ->
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_loader, target)
                        .addToBackStack(null)
                        .commit());
    }

    /** Module: re-verification on sensitive-field edit (see restaurant equivalent for full explanation). */
    private void launchVerificationUpdate() {

        if (mAuth.getCurrentUser() == null || !isAdded()) return;

        String uid = mAuth.getCurrentUser().getUid();
        String email = mAuth.getCurrentUser().getEmail();

        Intent intent = new Intent(requireContext(),
                com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.class);

        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_ROLE, "DELIVERY");
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_UID, uid);
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_EMAIL, email);
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_RESUBMIT, true);

        startActivity(intent);
    }
}