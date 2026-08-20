package com.example.paktrainfoodapp.ui.main.Restaurant.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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

public class resturent_ProfileFragment extends Fragment {

    private ImageView profileImage;
    private ImageView btnEditProfile; // Pencil button reference
    private TextView txtName, txtEmail;
    private TextView btnLogout; // Type badal kar TextView kar diya list row click handle karne ke liye
    private static final String TAG = "ProfileFragment";
    private LinearLayout layoutWallet;
    private LinearLayout layoutAccountInfo;
    // Gallery result click callback trigger
    private ActivityResultLauncher<String> galleryLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_resturent__profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        profileImage = view.findViewById(R.id.img_profile);
        btnEditProfile = view.findViewById(R.id.btn_edit_profile); // Initialized pencil button
        txtName = view.findViewById(R.id.txt_name);
        txtEmail = view.findViewById(R.id.txt_email);
        btnLogout = view.findViewById(R.id.btn_logout); // Reference map
        layoutWallet = view.findViewById(R.id.layout_wallet);
        layoutAccountInfo = view.findViewById(R.id.layout_account_info);

        layoutWallet.setOnClickListener(v -> {

            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_holder, com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.newInstance(com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.ROLE_RESTAURANT))
                    .addToBackStack(null)
                    .commit();

        });

        wireRow(view, R.id.layout_settings,
                new com.example.paktrainfoodapp.ui.shared.profile.SettingsFragment());

        wireRow(view, R.id.layout_my_orders,
                com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment.newInstance(
                        com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment.ROLE_RESTAURANT));

        if (layoutAccountInfo != null) {

            layoutAccountInfo.setOnClickListener(v ->
                    getParentFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_holder, com.example.paktrainfoodapp.ui.shared.profile.PersonalInfoFragment.newInstance(com.example.paktrainfoodapp.ui.shared.profile.PersonalInfoFragment.ROLE_RESTAURANT))
                            .addToBackStack("personal_info")
                            .commit());
        }

        // 🖼️ Gallery pick handling aur circle crop preview setup
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

                            if (FirebaseAuth.getInstance().getCurrentUser() != null) {

                                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

                                Toast.makeText(getContext(), "Uploading photo...", Toast.LENGTH_SHORT).show();

                                ProfileImageUploader.upload(
                                        requireContext(),
                                        "restaurant",
                                        uid,
                                        uri,
                                        new ProfileImageUploader.UploadCallback() {

                                            @Override
                                            public void onSuccess(String downloadUrl) {

                                                if (!isAdded()) return;

                                                FirebaseFirestore.getInstance()
                                                        .collection("Users")
                                                        .document("Restaurant")
                                                        .collection("VerifiedRegister")
                                                        .document(uid)
                                                        .update("profileImageUrl", downloadUrl);

                                                Toast.makeText(getContext(), "Restaurant Image Updated", Toast.LENGTH_SHORT).show();
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

        // Pencil click par phone gallery open karne ka trigger
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        }

        loadUserData();

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> performLogout());
        }
    }



    private void performLogout() {
        // 1. Firebase sign out
        FirebaseAuth.getInstance().signOut();

        // 2. Data clear
        PrefManager prefManager = new PrefManager(requireContext());
        prefManager.setLogin(false);
        prefManager.clear();

        // 3. Navigation with safe Context
        Intent intent = new Intent(requireContext(), Splash.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        // 4. Activity finish safely
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void loadUserData() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("Users")
                .document("Restaurant")
                .collection("VerifiedRegister")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded() || getContext() == null) return;

                    if (snapshot.exists()) {
                        if (txtName != null) txtName.setText(snapshot.getString("restaurantName"));
                        if (txtEmail != null) txtEmail.setText(snapshot.getString("email"));

                        String imageUrl = snapshot.getString("profileImageUrl");
                        if ((imageUrl == null || imageUrl.isEmpty())) {
                            imageUrl = snapshot.getString("licenseImageUrl");
                        }
                        if (imageUrl != null && !imageUrl.isEmpty() && profileImage != null) {
                            Glide.with(requireContext())
                                    .load(imageUrl)
                                    .circleCrop()
                                    .into(profileImage);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error: ", e);
                });
    }

    /**
     * Opens a shared screen from a profile row. All these screens are the same
     * ones the passenger uses - only the role argument differs.
     */
    private void wireRow(View parent, int rowId, androidx.fragment.app.Fragment target) {

        View row = parent.findViewById(rowId);

        if (row == null) return;

        row.setOnClickListener(v ->
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_holder, target)
                        .addToBackStack(null)
                        .commit());
    }
}