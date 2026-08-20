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
import com.example.paktrainfoodapp.ui.main.Delivery.DeliveryRegisterFragment;
import com.example.paktrainfoodapp.ui.main.Restaurant.restaurant_registers;
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

                                FirebaseAuth.getInstance().signOut();

                                Toast.makeText(
                                        getContext(),
                                        "Please verify your email first. Check your inbox.",
                                        Toast.LENGTH_LONG
                                ).show();

                                return;
                            }

                            String uid = auth.getCurrentUser().getUid();

                            PrefManager pref = new PrefManager(requireContext());
                            pref.setLogin(true);
                            pref.setUserRole(selectedRole);
                            pref.setUserEmail(email);

                            checkUserRegistration(uid, email);
                        });

                    } else {

                        progressDialog.dismiss();

                        Toast.makeText(
                                getContext(),
                                "Login failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
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
            // 🚨 SECURITY GATE: Pehle check karo kya yeh Restaurant hai aur iska status false to nahi?
            if (selectedRole.equals("RESTAURANT")
                    || selectedRole.equals("DELIVERY")) {

                Boolean isVerified = doc.getBoolean("isVerified");

                if (isVerified == null || !isVerified) {

                    progressDialog.dismiss();

                    FirebaseAuth.getInstance().signOut();

                    Toast.makeText(
                            getContext(),
                            "Your profile is waiting for Admin Approval.",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }
            }

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
                Toast.makeText(getContext(), "User not found. Please register first.", Toast.LENGTH_SHORT).show();
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
        restaurant_registers fragment = new restaurant_registers();
        Bundle args = new Bundle();
        args.putString("uid", uid);
        args.putString("email", email);
        fragment.setArguments(args);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded() && getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).loadFragment(fragment, true);
            }
        }, 300);
    }

    private void openDeliveryRegisterForm(String uid, String email) {
        DeliveryRegisterFragment fragment = new DeliveryRegisterFragment();
        Bundle args = new Bundle();
        args.putString("uid", uid);
        args.putString("email", email);
        fragment.setArguments(args);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded() && getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).loadFragment(fragment, true);
            }
        }, 300);
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
                        checkUserRegistration(uid, email);
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

