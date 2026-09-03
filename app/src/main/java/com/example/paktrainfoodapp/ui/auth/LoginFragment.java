package com.example.paktrainfoodapp.ui.auth;

import android.app.ProgressDialog; // 🔥 ProgressDialog Import Kiya
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.paktrainfoodapp.ui.main.MainActivity;
import com.example.paktrainfoodapp.utils.GoogleSignInHelper;
import com.example.paktrainfoodapp.utils.PrefManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginFragment extends Fragment {

    private TextInputEditText edtEmail, edtPassword;
    private Button btnLogin;
    private TextView txtForgotPassword, txtGoRegister;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private String selectedRole;

    private ProgressDialog progressDialog; // 🔄 Loading Spiner

    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> googleLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Must be registered before the fragment reaches STARTED.
        googleLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts
                        .StartActivityForResult(),
                result -> handleGoogleResult(result.getData()));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_auth_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        edtEmail = view.findViewById(R.id.edit_text_email);
        edtPassword = view.findViewById(R.id.edit_text_password);
        btnLogin = view.findViewById(R.id.button_login);
        txtForgotPassword = view.findViewById(R.id.text_forgot_password);
        txtGoRegister = view.findViewById(R.id.text_register);
        TextView title = view.findViewById(R.id.textViewTitle);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // ⚙️ Progress Dialog Initialization
        progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("Logging in... Please wait.");
        progressDialog.setCancelable(false); // User screen par click karke isko band na kar sake

        selectedRole = getArguments() != null
                ? getArguments().getString(AuthActivity.USER_ROLE_KEY, "PASSENGER")
                : "PASSENGER";

        // Set dynamic title
        switch (selectedRole) {
            case "PASSENGER":
                title.setText("Passenger Login");
                break;
            case "RESTAURANT":
                title.setText("Restaurant Login");
                break;
            case "DELIVERY":
                title.setText("Delivery Login");
                break;
        }

        View btnGoogle = view.findViewById(R.id.button_google_login);

        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(v -> startGoogleSignIn());
        }

        btnLogin.setOnClickListener(v -> doLogin());
        txtForgotPassword.setOnClickListener(v -> sendResetPassword());
        txtGoRegister.setOnClickListener(v -> openRegisterFragment());
    }

    // ---------------- LOGIN FUNCTION ---------------- //
    private void doLogin() {
        String email = edtEmail.getText() != null ? edtEmail.getText().toString().trim() : "";
        String password = edtPassword.getText() != null ? edtPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(getContext(), "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔄 Loader Show Karein
        progressDialog.show();

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        auth.getCurrentUser().reload().addOnCompleteListener(reloadTask -> {

                            if (!auth.getCurrentUser().isEmailVerified()) {

                                progressDialog.dismiss();

                                // ✅ FIX: signOut() used to run HERE, before the
                                // dialog - which meant the dialog's "Resend
                                // Email" button had no signed-in user to resend
                                // for, and just told people to log in first...
                                // from the login screen they were already on.
                                // The sign-out now happens when the dialog is
                                // dismissed, so Resend actually works, and the
                                // user still ends up signed out either way.
                                if (isAdded()) {
                                    AuthDialogs.showNotVerified(requireContext(), email,
                                            () -> FirebaseAuth.getInstance().signOut());
                                } else {
                                    FirebaseAuth.getInstance().signOut();
                                }

                                return;
                            }

                            String uid = auth.getCurrentUser().getUid();

                            PrefManager pref = new PrefManager(requireContext());
                            pref.setLogin(true);
                            pref.setUserRole(selectedRole);
                            pref.setUserEmail(email);

                            checkAccountNotUsedByOtherRole(uid, email, () -> checkUserRegistration(uid, email));
                        });

                    } else {

                        progressDialog.dismiss();

                        // \u2705 FIX: this dumped Firebase's raw exception text at
                        // the user ("There is no user record corresponding to
                        // this identifier..."). Now the two cases people
                        // actually hit are told apart and explained plainly.
                        if (isAdded()) {

                            Exception ex = task.getException();

                            if (ex instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {

                                // \u2705 FIX: this used to treat "no such account"
                                // and "account disabled by admin" identically -
                                // both are FirebaseAuthInvalidUserException, but
                                // Firebase distinguishes them by error code, and
                                // showing "please register first" to someone
                                // whose account genuinely exists (just disabled)
                                // is actively misleading.
                                String code = ((com.google.firebase.auth.FirebaseAuthInvalidUserException) ex)
                                        .getErrorCode();

                                if ("ERROR_USER_DISABLED".equals(code)) {
                                    AuthDialogs.showAccountDisabled(requireContext());
                                } else {
                                    AuthDialogs.showNotRegistered(requireContext());
                                }

                            } else {
                                AuthDialogs.showWrongCredentials(requireContext());
                            }
                        }
                    }
                });
    }

    /**
     * ✅ FIX: one email must only ever belong to ONE role. Previously,
     * logging in with an email already registered as (say) Passenger but
     * with "Restaurant" selected would find no Restaurant doc for this uid
     * and just fall into the Restaurant registration form - silently
     * creating a second role under the same account. This checks the OTHER
     * two role collections first and blocks with a clear message if the
     * account already belongs to one of them.
     */
    private void checkAccountNotUsedByOtherRole(String uid, String email, Runnable onClear) {

        java.util.List<String[]> others = new java.util.ArrayList<>();
        // {roleLabel, collectionName, subCollectionName}
        if (!selectedRole.equals("PASSENGER")) others.add(new String[]{"Passenger", "Passenger", "Register"});
        if (!selectedRole.equals("RESTAURANT")) others.add(new String[]{"Restaurant", "Restaurant", "VerifiedRegister"});
        if (!selectedRole.equals("DELIVERY")) others.add(new String[]{"Delivery", "Delivery", "VerifiedRegister"});

        checkOthersSequentially(uid, others, 0, onClear);
    }

    private void checkOthersSequentially(String uid, java.util.List<String[]> others, int index, Runnable onClear) {

        if (index >= others.size()) {
            onClear.run();
            return;
        }

        String[] entry = others.get(index);

        db.collection("Users").document(entry[1])
                .collection(entry[2])
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (doc.exists()) {

                        progressDialog.dismiss();
                        FirebaseAuth.getInstance().signOut();

                        if (isAdded()) AuthDialogs.showWrongRole(requireContext(), entry[0]);

                        return;
                    }

                    checkOthersSequentially(uid, others, index + 1, onClear);
                })
                .addOnFailureListener(e -> {
                    // Non-fatal - if this check itself fails, don't block a
                    // legitimate login over it; fall through to the normal path.
                    checkOthersSequentially(uid, others, index + 1, onClear);
                });
    }


    // ---------------- CHECK REGISTRATION ---------------- //
    private void checkUserRegistration(String uid, String email) {

        String roleDoc;

        switch (selectedRole) {
            case "PASSENGER":
                roleDoc = "Passenger";
                break;

            case "RESTAURANT":
                roleDoc = "Restaurant";
                break;

            case "DELIVERY":
                roleDoc = "Delivery";
                break;

            default:
                roleDoc = "Passenger";
        }

        // Restaurant/Delivery data only ever lives at VerifiedRegister/{uid} -
        // there is no separate "Register" pre-step for those two roles (only
        // Passenger uses that collection). Checking "Register" for them
        // always came back empty, which made the wizard reopen on every
        // single login no matter what verificationStatus they were
        // actually at - this branch goes straight to the real location.
        if (selectedRole.equals("RESTAURANT") || selectedRole.equals("DELIVERY")) {

            db.collection("Users")
                    .document(roleDoc)
                    .collection("VerifiedRegister")
                    .document(uid)
                    .get()
                    .addOnSuccessListener(verifiedDoc -> handleUserCheck(verifiedDoc, uid, email, roleDoc))
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

            return;
        }

        // 🔍 Sab se pehle Register collection check karo
        db.collection("Users")
                .document(roleDoc)
                .collection("Register")
                .document(uid)
                .get()
                .addOnSuccessListener(registerDoc -> {

                    // ❌ Register data hi nahi mila
                    if (!registerDoc.exists()) {

                        progressDialog.dismiss();

                        if (selectedRole.equals("RESTAURANT")) {
                            openRestaurantRegisterForm(uid, email);
                        }
                        else if (selectedRole.equals("DELIVERY")) {
                            openDeliveryRegisterForm(uid, email);
                        }
                        else {
                            Toast.makeText(
                                    getContext(),
                                    "Please register first.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }

                        return;
                    }

                    // ✅ Passenger ka kaam yahin khatam
                    if (selectedRole.equals("PASSENGER")) {
                        handleUserCheck(registerDoc, uid, email, roleDoc);
                        return;
                    }

                    // 🔍 Restaurant / Delivery ke liye VerifiedRegister check karo
                    db.collection("Users")
                            .document(roleDoc)
                            .collection("VerifiedRegister")
                            .document(uid)
                            .get()
                            .addOnSuccessListener(verifiedDoc -> {
                                handleUserCheck(
                                        verifiedDoc,
                                        uid,
                                        email,
                                        roleDoc
                                );
                            })
                            .addOnFailureListener(e -> {
                                progressDialog.dismiss();
                                Toast.makeText(
                                        getContext(),
                                        e.getMessage(),
                                        Toast.LENGTH_SHORT
                                ).show();
                            });

                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();

                    Toast.makeText(
                            getContext(),
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }
    private void handleUserCheck(DocumentSnapshot doc, String uid, String email, String roleDoc) {
        PrefManager pref = new PrefManager(requireContext());

        if (doc.exists()) {

            // A blocked account is stopped here, before ever reaching
            // MainActivity - unlike a rejection (which still lets the
            // applicant log in to see why and resubmit), a block is meant
            // to prevent login outright.
            Boolean isBlocked = doc.getBoolean("isBlocked");

            if (isBlocked != null && isBlocked) {

                progressDialog.dismiss();

                FirebaseAuth.getInstance().signOut();

                Toast.makeText(
                        getContext(),
                        "This account has been blocked. Please contact support.",
                        Toast.LENGTH_LONG
                ).show();

                return;
            }

            // Restaurant/Delivery accounts that aren't verified yet still get
            // signed in - MainActivity checks verificationStatus itself and
            // routes to the Pending/Rejected screen instead of the
            // dashboard. Blocking here (the old behaviour) meant those
            // screens could never actually be reached, since sign-out
            // happened before MainActivity ever loaded.

            // 🎉 Agar PASSENGER hai, ya approved RESTAURANT hai, to hi yeh niche wala purana code chalay ga:
            String name = doc.getString("name"); // ya jo bhi field restaurantName hai
            pref.setRegistered(true, email);
            if (name != null) pref.setUserName(name);
            pref.setUserRole(roleDoc.toUpperCase());

            // 🔓 Success! Main activity par jane se pehle loader band
            progressDialog.dismiss();
            goToMainActivity();
        } else {
            // 🔓 Agar Mazeed form khulna hai tab bhi loading band kar dein
            progressDialog.dismiss();

            // Open additional registration form for Restaurant or Delivery
            if (selectedRole.equals("RESTAURANT")) {
                openRestaurantRegisterForm(uid, email);
            } else if (selectedRole.equals("DELIVERY")) {
                openDeliveryRegisterForm(uid, email);
            } else {
                if (isAdded()) AuthDialogs.showNotRegistered(requireContext());
            }
        }
    }
    // ---------------- PASSWORD RESET ---------------- //
    private void sendResetPassword() {
        String email = edtEmail.getText() != null ? edtEmail.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(getContext(), "Enter your email first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Optional: Yahan reset password ke liye bhi progress lagana chahein to laga sakte hain
        progressDialog.setMessage("Sending reset email...");
        progressDialog.show();

        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressDialog.dismiss();
                    progressDialog.setMessage("Logging in... Please wait."); // Reset original message
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "Password reset email sent", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(),
                                "Error: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---------------- NAVIGATION ---------------- //
    private void openRegisterFragment() {
        Fragment fragment = new RegisterFragment();
        Bundle b = new Bundle();
        b.putString(AuthActivity.USER_ROLE_KEY, selectedRole);
        fragment.setArguments(b);

        if (getActivity() instanceof AuthActivity) {
            ((AuthActivity) getActivity()).loadFragment(fragment, true);
        }
    }

    private void goToMainActivity() {
        PrefManager pref = new PrefManager(requireContext());
        pref.setLogin(true);
        pref.setRegistered(true, pref.getUserEmail());

        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.putExtra(AuthActivity.USER_ROLE_KEY, selectedRole);
        startActivity(intent);
        requireActivity().finish();
    }

    private void openRestaurantRegisterForm(String uid, String email) {

        Intent intent = new Intent(getActivity(),
                com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.class);

        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_ROLE, "RESTAURANT");
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_UID, uid);
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_EMAIL, email);

        startActivity(intent);

        if (getActivity() != null) requireActivity().finish();
    }

    private void openDeliveryRegisterForm(String uid, String email) {

        Intent intent = new Intent(getActivity(),
                com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.class);

        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_ROLE, "DELIVERY");
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_UID, uid);
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_EMAIL, email);

        startActivity(intent);

        if (getActivity() != null) requireActivity().finish();
    }

    // ---------------- GOOGLE SIGN-IN ---------------- //

    private void startGoogleSignIn() {

        if (!GoogleSignInHelper.isConfigured(requireContext())) {

            Toast.makeText(getContext(),
                    "Google login is not enabled yet. Please use email and password.",
                    Toast.LENGTH_LONG).show();

            return;
        }

        // Sign out first so the account picker always appears instead of
        // silently reusing the last account.
        GoogleSignInHelper.buildClient(requireContext())
                .signOut()
                .addOnCompleteListener(t ->
                        googleLauncher.launch(
                                GoogleSignInHelper.buildClient(requireContext())
                                        .getSignInIntent()));
    }

    private void handleGoogleResult(android.content.Intent data) {

        if (data == null) return;

        progressDialog.setMessage("Signing in with Google...");
        progressDialog.show();

        try {

            com.google.firebase.auth.AuthCredential credential =
                    GoogleSignInHelper.credentialFrom(data);

            auth.signInWithCredential(credential)
                    .addOnSuccessListener(result -> {

                        if (!isAdded()) return;

                        String uid = auth.getCurrentUser().getUid();
                        String email = auth.getCurrentUser().getEmail();

                        PrefManager pref = new PrefManager(requireContext());
                        pref.setLogin(true);
                        pref.setUserRole(selectedRole);
                        pref.setUserEmail(email);

                        // A Google account is already verified by Google, so the
                        // email-verification gate is skipped; everything else
                        // (role lookup, admin approval) stays exactly the same.
                        checkAccountNotUsedByOtherRole(uid, email, () -> checkUserRegistration(uid, email));
                    })
                    .addOnFailureListener(e -> {

                        progressDialog.dismiss();

                        if (isAdded()) {
                            Toast.makeText(getContext(),
                                    "Google login failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });

        } catch (com.google.android.gms.common.api.ApiException e) {

            progressDialog.dismiss();

            Toast.makeText(getContext(),
                    "Google sign-in cancelled",
                    Toast.LENGTH_SHORT).show();
        }
    }
}

