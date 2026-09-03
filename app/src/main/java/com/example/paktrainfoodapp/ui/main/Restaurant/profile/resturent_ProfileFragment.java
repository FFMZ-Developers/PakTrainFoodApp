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

        View layoutUpdateVerification = view.findViewById(R.id.layout_update_verification);
        if (layoutUpdateVerification != null) {
            layoutUpdateVerification.setOnClickListener(v -> launchVerificationUpdate());
        }

        layoutWallet.setOnClickListener(v -> {

            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_holder, com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.newInstance(com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.ROLE_RESTAURANT))
                    .addToBackStack(null)
                    .commit();

        });

        wireRow(view, R.id.layout_settings,
                () -> new com.example.paktrainfoodapp.ui.shared.profile.SettingsFragment());

        wireRow(view, R.id.layout_my_orders,
                () -> com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment.newInstance(
                        com.example.paktrainfoodapp.ui.shared.orders.MyOrdersFragment.ROLE_RESTAURANT));

        // ✅ FIX: this used to open resturent_MenuFragment directly via
        // wireRow() (a plain fragment replace) - the exact same screen the
        // bottom nav's "Menu" tab opens, but without going through that
        // tab's own click listener, so the bottom nav kept showing
        // "Profile" highlighted even though Manage Menu was now on
        // screen. Routing through the host's navigateFromDashboard() -
        // the same path the dashboard's own "Manage Menu" shortcut uses -
        // makes the highlight follow the screen correctly.
        View layoutManageMenu = view.findViewById(R.id.layout_manage_menu);
        if (layoutManageMenu != null) {
            layoutManageMenu.setOnClickListener(v -> navigateToTab("menu"));
        }

        // \u2705 FIX: this row existed in the layout but had never been wired
        // to anything at all - tapping "Help & Support" did nothing.
        wireRow(view, R.id.layout_help_support,
                () -> com.example.paktrainfoodapp.ui.shared.support.HelpSupportFragment
                        .newInstance(com.example.paktrainfoodapp.ui.shared.support.HelpSupportFragment.ROLE_RESTAURANT));

        // Module: share the app - WhatsApp / copy link / anything else,
        // via the system share sheet.
        View layoutShare = view.findViewById(R.id.layout_share_app);
        if (layoutShare != null) {
            layoutShare.setOnClickListener(v ->
                    com.example.paktrainfoodapp.utils.ShareUtils.showShareOptions(requireContext()));
        }

        // App Version row: shows the real installed versionName and opens
        // a small info dialog with the same version on tap - same as the
        // rider profile's row.
        TextView txtAppVersion = view.findViewById(R.id.txt_app_version);
        View layoutAppVersion = view.findViewById(R.id.layout_app_version);
        String appVersion = resolveAppVersion();
        if (txtAppVersion != null) txtAppVersion.setText(appVersion);
        if (layoutAppVersion != null) {
            layoutAppVersion.setOnClickListener(v ->
                    new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("App Version")
                            .setMessage("Pak Train Food\n\nVersion " + appVersion)
                            .setPositiveButton("OK", null)
                            .show());
        }

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
            btnLogout.setOnClickListener(v ->
                    // Guarded by a confirmation - see LogoutConfirm.
                    com.example.paktrainfoodapp.utils.LogoutConfirm.show(
                            requireContext(), this::performLogout));
        }
    }



    private void performLogout() {        // 1. Firebase sign out
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

    /** Reads the real app version from PackageManager, same source SettingsFragment/rider profile use. */
    private String resolveAppVersion() {
        try {
            return requireContext()
                    .getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "1.0";
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
                            // ✅ FIX: fell back to the LICENSE document photo
                            // (a photo of a piece of paper) rather than the
                            // owner's actual face from the verification
                            // selfie step. selfieUrl is the correct default
                            // for a profile picture nobody has manually
                            // changed yet.
                            imageUrl = snapshot.getString("selfieUrl");
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
    /**
     * \u2705 FIX: this took a single, already-constructed Fragment INSTANCE.
     * That instance was created once when the profile screen loaded, so
     * navigating away and coming back tried to re-add the very same
     * (already-used) fragment - which silently fails, making the row look
     * completely dead on every tap after the first. It now takes a
     * factory and builds a fresh instance per tap.
     */
    private void wireRow(View parent, int rowId, FragmentFactory factory) {

        View row = parent.findViewById(rowId);

        if (row == null) return;

        row.setOnClickListener(v ->
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_holder, factory.create())
                        .addToBackStack(null)
                        .commit());
    }

    interface FragmentFactory {
        androidx.fragment.app.Fragment create();
    }

    /** Routes through the host's own bottom-nav click path (same as
     *  resturent_DashboardFragment.navigateTo()) so the highlighted tab
     *  always matches whatever screen is actually showing. */
    private void navigateToTab(String target) {
        if (getParentFragment() instanceof com.example.paktrainfoodapp.ui.main.Restaurant.restaurant_LoadFragment) {
            ((com.example.paktrainfoodapp.ui.main.Restaurant.restaurant_LoadFragment) getParentFragment())
                    .navigateFromDashboard(target);
        }
    }

    /**
     * Module: re-verification on sensitive-field edit. Launches the SAME
     * wizard used for signup/resubmission-after-rejection, with
     * EXTRA_RESUBMIT=true so it pre-fills the restaurant's current data
     * (name, city, opening hours, existing CNIC/license, bank details -
     * see VerificationWizardActivity.applyExistingDocument()). Submitting
     * it (Step4SelfieFragment) always sets verificationStatus back to
     * "pending" - so if the restaurant changes CNIC or license here, their
     * account goes back into the admin review queue automatically, using
     * infrastructure that already existed rather than a new mechanism.
     *
     * Non-sensitive fields (name, phone, opening hours, bank details) have
     * their own direct-update screens (PersonalInfoFragment, Wallet) that
     * do NOT touch verificationStatus - editing those never requires
     * re-verification.
     */
    private void launchVerificationUpdate() {

        com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null || !isAdded()) return;

        String uid = auth.getCurrentUser().getUid();
        String email = auth.getCurrentUser().getEmail();

        Intent intent = new Intent(requireContext(),
                com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.class);

        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_ROLE, "RESTAURANT");
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_UID, uid);
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_EMAIL, email);
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_RESUBMIT, true);

        startActivity(intent);
    }
}