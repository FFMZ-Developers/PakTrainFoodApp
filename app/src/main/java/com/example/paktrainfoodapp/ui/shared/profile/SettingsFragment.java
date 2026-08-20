package com.example.paktrainfoodapp.ui.shared.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.utils.ThemeManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {

    private static final String PREF_NAME = "PakTrainSettings";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";

    private RadioGroup radioGroupTheme;
    private SwitchCompat switchNotifications;
    private LinearLayout layoutChangePassword, layoutClearFavorites;
    private TextView txtAppVersion;

    /**
     * Guards against the programmatic "restore saved choice" call below
     * triggering the listener and immediately re-applying the theme.
     */
    private boolean isInitialising = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        radioGroupTheme = view.findViewById(R.id.radioGroupTheme);
        switchNotifications = view.findViewById(R.id.switchNotifications);
        layoutChangePassword = view.findViewById(R.id.layout_change_password);
        layoutClearFavorites = view.findViewById(R.id.layout_clear_favorites);
        txtAppVersion = view.findViewById(R.id.txtAppVersion);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (isAdded()) getParentFragmentManager().popBackStack();
        });

        setupTheme();
        setupNotificationToggle();

        layoutChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        layoutClearFavorites.setOnClickListener(v -> confirmClearFavorites());

        showAppVersion();
    }

    // =========================================================
    // THEME
    // =========================================================

    private void setupTheme() {

        int saved = ThemeManager.getSavedMode(requireContext());

        if (saved == ThemeManager.MODE_LIGHT) {
            radioGroupTheme.check(R.id.radioLight);
        } else if (saved == ThemeManager.MODE_DARK) {
            radioGroupTheme.check(R.id.radioDark);
        } else {
            radioGroupTheme.check(R.id.radioSystem);
        }

        isInitialising = false;

        radioGroupTheme.setOnCheckedChangeListener((group, checkedId) -> {

            if (isInitialising) return;

            int mode;

            if (checkedId == R.id.radioLight) {
                mode = ThemeManager.MODE_LIGHT;
            } else if (checkedId == R.id.radioDark) {
                mode = ThemeManager.MODE_DARK;
            } else {
                mode = ThemeManager.MODE_SYSTEM;
            }

            // This recreates the Activity so the whole app repaints instantly.
            ThemeManager.setMode(requireContext(), mode);
        });
    }

    // =========================================================
    // NOTIFICATIONS TOGGLE
    // =========================================================

    private void setupNotificationToggle() {

        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        switchNotifications.setChecked(prefs.getBoolean(KEY_NOTIFICATIONS, true));

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {

            prefs.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply();

            Toast.makeText(requireContext(),
                    isChecked ? "Notifications enabled" : "Notifications disabled",
                    Toast.LENGTH_SHORT).show();
        });
    }

    // =========================================================
    // CHANGE PASSWORD
    // =========================================================

    private void showChangePasswordDialog() {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null || user.getEmail() == null) {
            Toast.makeText(requireContext(), "You must be logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null);

        TextInputEditText editOld = dialogView.findViewById(R.id.edit_old_password);
        TextInputEditText editNew = dialogView.findViewById(R.id.edit_new_password);
        TextInputEditText editConfirm = dialogView.findViewById(R.id.edit_confirm_password);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Update", null) // set below so it doesn't auto-dismiss
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

            String oldPass = editOld.getText() != null ? editOld.getText().toString() : "";
            String newPass = editNew.getText() != null ? editNew.getText().toString() : "";
            String confirmPass = editConfirm.getText() != null ? editConfirm.getText().toString() : "";

            if (TextUtils.isEmpty(oldPass)) {
                editOld.setError("Enter your current password");
                return;
            }

            if (newPass.length() < 6) {
                editNew.setError("At least 6 characters");
                return;
            }

            if (!newPass.equals(confirmPass)) {
                editConfirm.setError("Passwords do not match");
                return;
            }

            if (newPass.equals(oldPass)) {
                editNew.setError("New password must be different");
                return;
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

            // Firebase requires a recent login before changing a password, so
            // re-authenticate with the current password first.
            AuthCredential credential =
                    EmailAuthProvider.getCredential(user.getEmail(), oldPass);

            user.reauthenticate(credential)
                    .addOnSuccessListener(unused ->
                            user.updatePassword(newPass)
                                    .addOnSuccessListener(ignored -> {

                                        if (!isAdded()) return;

                                        dialog.dismiss();

                                        Toast.makeText(requireContext(),
                                                "Password updated successfully",
                                                Toast.LENGTH_SHORT).show();
                                    })
                                    .addOnFailureListener(e -> {

                                        if (!isAdded()) return;

                                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);

                                        Toast.makeText(requireContext(),
                                                "Update failed: " + e.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                    }))
                    .addOnFailureListener(e -> {

                        if (!isAdded()) return;

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);

                        editOld.setError("Current password is incorrect");
                    });
        });
    }

    // =========================================================
    // CLEAR FAVORITES
    // =========================================================

    private void confirmClearFavorites() {

        new AlertDialog.Builder(requireContext())
                .setTitle("Clear saved favorites?")
                .setMessage("This will remove all restaurants you marked as favorite on this device.")
                .setPositiveButton("Clear", (d, w) -> {

                    requireContext()
                            .getSharedPreferences("PakTrainFavorites", Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();

                    Toast.makeText(requireContext(), "Favorites cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAppVersion() {

        try {

            String version = requireContext()
                    .getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0)
                    .versionName;

            txtAppVersion.setText(version);

        } catch (Exception e) {
            txtAppVersion.setText("1.0");
        }
    }
}
