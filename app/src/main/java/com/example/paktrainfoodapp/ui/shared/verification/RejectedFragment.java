package com.example.paktrainfoodapp.ui.shared.verification;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.Splash;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Shown when an admin has rejected a restaurant/rider's verification
 * submission. Displays the reason the admin typed, and lets the applicant
 * resubmit on the SAME account (not a new signup) - this is what prevents
 * the duplicate-account confusion the rejection-reason design was meant to
 * solve.
 */
public class RejectedFragment extends Fragment {

    private static final String ARG_ROLE = "role";
    private static final String ARG_UID = "uid";
    private static final String ARG_EMAIL = "email";
    private static final String ARG_REASON = "reason";

    public static RejectedFragment newInstance(String role, String uid, String email, String reason) {

        RejectedFragment fragment = new RejectedFragment();

        Bundle args = new Bundle();
        args.putString(ARG_ROLE, role);
        args.putString(ARG_UID, uid);
        args.putString(ARG_EMAIL, email);
        args.putString(ARG_REASON, reason);
        fragment.setArguments(args);

        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_verification_rejected, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView txtReason = view.findViewById(R.id.txt_rejection_reason);

        Bundle args = getArguments();
        String reason = args != null ? args.getString(ARG_REASON) : null;

        txtReason.setText(TextUtils.isEmpty(reason) ? "No reason provided." : reason);

        view.findViewById(R.id.btn_edit_resubmit).setOnClickListener(v -> {

            if (args == null) return;

            Intent intent = new Intent(requireActivity(), VerificationWizardActivity.class);
            intent.putExtra(VerificationWizardActivity.EXTRA_ROLE, args.getString(ARG_ROLE));
            intent.putExtra(VerificationWizardActivity.EXTRA_UID, args.getString(ARG_UID));
            intent.putExtra(VerificationWizardActivity.EXTRA_EMAIL, args.getString(ARG_EMAIL));
            intent.putExtra(VerificationWizardActivity.EXTRA_RESUBMIT, true);

            startActivity(intent);
            requireActivity().finish();
        });

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            // Guarded by a confirmation - see LogoutConfirm.
            com.example.paktrainfoodapp.utils.LogoutConfirm.show(requireContext(), () -> {

            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(requireActivity(), Splash.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

            requireActivity().finish();
            });
        });
    }
}
