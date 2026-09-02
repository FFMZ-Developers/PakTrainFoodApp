package com.example.paktrainfoodapp.ui.main.Passenger.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.paktrainfoodapp.Splash;
import com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.shared.profile.AddressFragment;
import com.example.paktrainfoodapp.ui.shared.profile.SettingsFragment;
import com.example.paktrainfoodapp.utils.ProfileImageUploader;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private ImageView profileImage;
    private ImageView btnEditProfile;

    private TextView txtName;
    private TextView txtEmail;
    private TextView btnLogout;

    private LinearLayout layoutHelpSupport;
    private LinearLayout layoutShareApp;
    private LinearLayout layoutAccountInfo;
    private LinearLayout layoutWallet;
    private LinearLayout layoutMyOrders;
    private LinearLayout layoutAddress;
    private LinearLayout layoutSettings;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ActivityResultLauncher<String> galleryLauncher;

    public ProfileFragment() {
        // Required empty constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_passanger_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        // Initialize Views

        profileImage = view.findViewById(R.id.img_profile);
        btnEditProfile = view.findViewById(R.id.btn_edit_profile);

        txtName = view.findViewById(R.id.txt_name);
        txtEmail = view.findViewById(R.id.txt_email);
        btnLogout = view.findViewById(R.id.btn_logout);

        layoutHelpSupport = view.findViewById(R.id.layoutHelpSupport);
        layoutShareApp = view.findViewById(R.id.layoutShareApp);
        layoutAccountInfo = view.findViewById(R.id.layout_account_info);
        layoutWallet = view.findViewById(R.id.layout_wallet);
        layoutMyOrders = view.findViewById(R.id.layout_my_orders);
        layoutAddress = view.findViewById(R.id.layout_address);
        layoutSettings = view.findViewById(R.id.layout_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Gallery Launcher

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {

                    if (uri != null && mAuth.getCurrentUser() != null) {

                        // Show immediately for instant feedback
                        Glide.with(requireActivity())
                                .load(uri)
                                .circleCrop()
                                .into(profileImage);

                        String uid = mAuth.getCurrentUser().getUid();

                        Toast.makeText(requireContext(),
                                "Uploading photo...",
                                Toast.LENGTH_SHORT).show();

                        ProfileImageUploader.upload(
                                requireContext(),
                                "passenger",
                                uid,
                                uri,
                                new ProfileImageUploader.UploadCallback() {

                                    @Override
                                    public void onSuccess(String downloadUrl) {

                                        if (!isAdded()) return;

                                        db.collection("Users")
                                                .document("Passenger")
                                                .collection("Register")
                                                .document(uid)
                                                .update("profileImageUrl", downloadUrl);

                                        Toast.makeText(requireContext(),
                                                "Profile Image Updated",
                                                Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onFailure(Exception e) {

                                        if (!isAdded()) return;

                                        Toast.makeText(requireContext(),
                                                "Upload failed: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                });

        // Change Profile Image

        btnEditProfile.setOnClickListener(v ->
                galleryLauncher.launch("image/*"));
        // ==========================
        // Load User Data
        // ==========================

        if (mAuth.getCurrentUser() != null) {
            loadUserData();
        } else {
            txtName.setText("Guest User");
            txtEmail.setText("");
        }

        // ==========================
        // Help & Support
        // ==========================

        layoutHelpSupport.setOnClickListener(v -> {

            Fragment parent = getParentFragment();

            if (parent instanceof Passenger_Fragment_Loader) {
                ((Passenger_Fragment_Loader) parent).openHelpSupport();
            }

        });

        // Module: share the app - WhatsApp / copy link / anything else,
        // via the system share sheet.
        if (layoutShareApp != null) {
            layoutShareApp.setOnClickListener(v ->
                    com.example.paktrainfoodapp.utils.ShareUtils.showShareOptions(requireContext()));
        }

        // ==========================
        // Account Info -> Personal Info edit screen
        // ==========================

        if (layoutAccountInfo != null) {

            layoutAccountInfo.setOnClickListener(v ->
                    openDetail(com.example.paktrainfoodapp.ui.shared.profile.PersonalInfoFragment.newInstance(com.example.paktrainfoodapp.ui.shared.profile.PersonalInfoFragment.ROLE_PASSENGER)));
        }

        if (layoutWallet != null) {
            layoutWallet.setOnClickListener(v ->
                    openDetail(com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.newInstance(com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.ROLE_PASSENGER)));
        }

        if (layoutMyOrders != null) {
            layoutMyOrders.setOnClickListener(v ->
                    openDetail(com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment
                            .newInstance(com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment.ROLE_PASSENGER)));
        }

        if (layoutAddress != null) {
            layoutAddress.setOnClickListener(v ->
                    openDetail(new AddressFragment()));
        }

        if (layoutSettings != null) {
            layoutSettings.setOnClickListener(v ->
                    openDetail(new SettingsFragment()));
        }

        // ==========================
        // Logout
        // ==========================

        btnLogout.setOnClickListener(v -> {
            // Guarded by a confirmation - see LogoutConfirm.
            com.example.paktrainfoodapp.utils.LogoutConfirm.show(requireContext(), () -> {

            mAuth.signOut();

            Intent intent = new Intent(requireActivity(), Splash.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);
            requireActivity().finish();
            });
        });

    }

    /**
     * All of these screens live inside the passenger shell, so they go through
     * the loader's detail-screen helper which handles the back stack and
     * hides/shows the bottom nav correctly.
     */
    private void openDetail(Fragment fragment) {

        Fragment parent = getParentFragment();

        if (parent instanceof Passenger_Fragment_Loader) {
            ((Passenger_Fragment_Loader) parent).openProfileDetail(fragment);
        }
    }

    // ==========================
    // Load User Data From Firebase
    // ==========================

    private void loadUserData() {

        if (mAuth.getCurrentUser() == null)
            return;

        String uid = mAuth.getCurrentUser().getUid();

        db.collection("Users")
                .document("Passenger")
                .collection("Register")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!isAdded())
                        return;

                    if (snapshot.exists()) {

                        String name = snapshot.getString("name");
                        String email = snapshot.getString("email");
                        String imageUrl = snapshot.getString("profileImageUrl");

                        txtName.setText(name != null ? name : "No Name");
                        txtEmail.setText(email != null ? email : "No Email");
                        if (imageUrl != null && !imageUrl.isEmpty()) {

                            Glide.with(requireActivity())
                                    .load(imageUrl)
                                    .placeholder(R.drawable.edit_info)
                                    .circleCrop()
                                    .into(profileImage);

                        } else {

                            profileImage.setImageResource(R.drawable.edit_info);

                        }

                    }

                })
                .addOnFailureListener(e ->

                        Toast.makeText(requireContext(),
                                "Failed to load profile",
                                Toast.LENGTH_SHORT).show()

                );

    }

}

