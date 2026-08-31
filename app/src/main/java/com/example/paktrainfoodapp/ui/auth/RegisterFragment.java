package com.example.paktrainfoodapp.ui.auth;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class RegisterFragment extends Fragment {

    // 🔥 APNI IMGBB API KEY YAHAN PASTE KAREIN
    private static final String IMGBB_API_KEY = "48a067c9d290ecbca102dca44184ae22";

    private CircleImageView profileImage;
    private TextInputEditText edtName, edtEmail, edtPhone, edtPassword, edtConfirmPassword;
    private Button btnChooseImage, btnRegister;
    private TextView txtGoLogin;

    private Uri imageUri = null;
    private String imageBase64 = null;
    private String profileImageUrl = ""; // To store ImgBB online link

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseFunctions functions; // 🔥 Stripe Cloud Functions call karne ke liye
    private ProgressDialog progressDialog; // 🔄 Loading Spinner
    private RequestQueue requestQueue;

    private String userRole = "PASSENGER"; // default

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    imageUri = uri;
                    Glide.with(this).load(uri).into(profileImage);

                    // Image select hote hi background mein convert karwa letay hain
                    try {
                        imageBase64 = convertImageToBase64(uri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_auth_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        profileImage = view.findViewById(R.id.profile_image);
        edtName = view.findViewById(R.id.edit_text_name);
        edtEmail = view.findViewById(R.id.edit_text_email);
        edtPhone = view.findViewById(R.id.edit_text_phone);
        edtPassword = view.findViewById(R.id.edit_text_password);
        edtConfirmPassword = view.findViewById(R.id.edit_text_confirm_password);
        btnChooseImage = view.findViewById(R.id.button_choose_image);
        btnRegister = view.findViewById(R.id.button_register);
        txtGoLogin = view.findViewById(R.id.text_login);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance(); // 🔥 Initialize Cloud Functions
        requestQueue = Volley.newRequestQueue(requireContext());

        // ⚙️ Progress Dialog Initialization
        progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("Creating account & uploading profile... Please wait.");
        progressDialog.setCancelable(false);

        if (getArguments() != null && getArguments().containsKey(AuthActivity.USER_ROLE_KEY)) {
            userRole = getArguments().getString(AuthActivity.USER_ROLE_KEY);
        }

        btnChooseImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnRegister.setOnClickListener(v -> doRegister());
        txtGoLogin.setOnClickListener(v -> goToLogin());
    }

    private void goToLogin() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            Bundle bundle = new Bundle();
            bundle.putString(AuthActivity.USER_ROLE_KEY, userRole);
            LoginFragment fragment = new LoginFragment();
            fragment.setArguments(bundle);
            if (getActivity() instanceof AuthActivity) {
                ((AuthActivity) getActivity()).loadFragment(fragment, true);
            }
        }
    }

    private void doRegister() {
        String name = edtName.getText() != null ? edtName.getText().toString().trim() : "";
        String email = edtEmail.getText() != null ? edtEmail.getText().toString().trim() : "";
        String phone = edtPhone.getText() != null ? edtPhone.getText().toString().trim() : "";
        String password = edtPassword.getText() != null ? edtPassword.getText().toString().trim() : "";
        String confirmPassword = edtConfirmPassword.getText() != null ? edtConfirmPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(phone) ||
                TextUtils.isEmpty(password) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(getContext(), "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(getContext(), "Invalid email", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(getContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (imageUri == null || imageBase64 == null) {
            Toast.makeText(getContext(), "Please select profile image", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔄 Sab valid hai, ab Progress Dialog show karein aur sequence start karein
        progressDialog.show();
        uploadProfileImageToImgBB(name, email, phone, password);
    }

    private String convertImageToBase64(Uri imageUri) throws IOException {
        InputStream inputStream = requireContext().getContentResolver().openInputStream(imageUri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // 🔄 Quality 50% rakhi hai taake profile image instantly upload ho
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    // 🚀 STEP 1: Upload Profile Image to ImgBB First
    private void uploadProfileImageToImgBB(String name, String email, String phone, String password) {
        String url = "https://api.imgbb.com/1/upload?key=" + IMGBB_API_KEY;

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                response -> {
                    try {
                        JSONObject jsonObject = new JSONObject(response);
                        profileImageUrl = jsonObject.getJSONObject("data").getString("url");

                        // Image upload ho gayi, ab Firebase Auth user
                        createFirebaseUser(name, email, phone, password);
                    } catch (Exception e) {
                        progressDialog.dismiss();
                        Toast.makeText(getContext(), "Image Parsing Error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    progressDialog.dismiss();
                    Toast.makeText(getContext(), "Image Upload Failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("image", imageBase64);
                return params;
            }
        };

        requestQueue.add(stringRequest);
    }

    // 🚀 STEP 2: Authenticate User and Save Small URL Link in Firestore
    private void createFirebaseUser(String name, String email, String phone, String password) {

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {

                    if (task.isSuccessful()) {

                        if (mAuth.getCurrentUser() == null) {
                            progressDialog.dismiss();
                            Toast.makeText(getContext(),
                                    "User creation failed",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        mAuth.getCurrentUser().sendEmailVerification()
                                .addOnCompleteListener(verifyTask -> {

                                    if (!verifyTask.isSuccessful()) {
                                        progressDialog.dismiss();
                                        Toast.makeText(getContext(),
                                                "Failed to send verification email",
                                                Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    String uid = mAuth.getCurrentUser().getUid();

                                    Map<String, Object> user = new HashMap<>();
                                    user.put("uid", uid);
                                    user.put("name", name);
                                    user.put("email", email);
                                    user.put("phone", phone);
                                    user.put("profileImageUrl", profileImageUrl);
                                    user.put("role", userRole);

                                    String parentCollection;

                                    if (userRole.equals("PASSENGER")) {
                                        parentCollection = "Passenger";
                                    } else if (userRole.equals("RESTAURANT")) {
                                        parentCollection = "Restaurant";
                                    } else {
                                        parentCollection = "Delivery";
                                    }

                                    db.collection("Users")
                                            .document(parentCollection)
                                            .collection("Register")
                                            .document(uid)
                                            .set(user)
                                            .addOnSuccessListener(unused -> {

                                                progressDialog.dismiss();

                                                // \u2705 FIX: this used to be a brief Toast that
                                                // vanished while the app moved straight on to the
                                                // login screen - people never saw it, tried to log
                                                // in immediately, and hit an unexplained "not
                                                // verified" error. Now it's a blocking dialog with
                                                // a direct "Open Email App" button and a prominent
                                                // spam-folder warning.
                                                // The next step only runs once the user has
                                                // actually acknowledged the dialog, so the login
                                                // screen (or Stripe onboarding) never appears
                                                // over the top of the message they still need
                                                // to read.
                                                Runnable continueAfterDialog = () -> {

                                                    if (!isAdded()) return;

                                                    // 🔥 Sirf Restaurant aur Delivery (Rider) ke liye
                                                    // Stripe Connected Account banayenge.
                                                    // Passenger ko iski zaroorat nahi (refund original
                                                    // payment method pe seedha wapas jata hai).
                                                    if (userRole.equals("RESTAURANT") || userRole.equals("DELIVERY")) {
                                                        createStripeConnectedAccount(uid, email, userRole);
                                                    } else {
                                                        mAuth.signOut();
                                                        goToLogin();
                                                    }
                                                };

                                                if (isAdded()) {

                                                    AuthDialogs.showVerificationSent(
                                                            requireContext(), email,
                                                            continueAfterDialog::run);

                                                } else {
                                                    continueAfterDialog.run();
                                                }
                                            })
                                            .addOnFailureListener(e -> {

                                                progressDialog.dismiss();

                                                Toast.makeText(
                                                        getContext(),
                                                        "Firestore Error: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            });

                                });

                    } else {

                        progressDialog.dismiss();

                        Toast.makeText(
                                getContext(),
                                "Auth failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
    }

    // 🚀 STEP 3: Restaurant/Rider ke liye Stripe Test Connected Account banana
    // aur onboarding link browser mein open karna (sandbox mode)
    private void createStripeConnectedAccount(String uid, String email, String role) {

        progressDialog.setMessage("Setting up payment account... Please wait.");
        progressDialog.show();

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("uid", uid);
        data.put("type", role.equals("RESTAURANT") ? "restaurant" : "rider");

        functions
                .getHttpsCallable("createConnectedAccount")
                .call(data)
                .addOnSuccessListener(result -> {

                    progressDialog.dismiss();

                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultData = (Map<String, Object>) result.getData();

                    Boolean success = resultData != null && resultData.get("success") != null
                            ? (Boolean) resultData.get("success")
                            : false;

                    String onboardingUrl = resultData != null
                            ? (String) resultData.get("onboardingUrl")
                            : null;

                    if (success != null && success && onboardingUrl != null) {

                        Toast.makeText(getContext(),
                                "Account created. Please complete Stripe onboarding.",
                                Toast.LENGTH_LONG).show();

                        // Onboarding link browser mein open karein (sandbox test details fill karne ke liye)
                        try {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(onboardingUrl));
                            startActivity(browserIntent);
                        } catch (Exception e) {
                            Toast.makeText(getContext(),
                                    "Could not open onboarding link. Please complete it later from your profile.",
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(getContext(),
                                "Payment account setup incomplete. You can complete it later from your profile.",
                                Toast.LENGTH_LONG).show();
                    }

                    mAuth.signOut();
                    goToLogin();
                })
                .addOnFailureListener(e -> {

                    progressDialog.dismiss();

                    // 🔥 Stripe account creation fail ho jaye tab bhi registration
                    // process rukna nahi chahiye. User baad mein profile se
                    // payment account setup retry kar sakta hai.
                    Toast.makeText(getContext(),
                            "Payment account setup failed: " + e.getMessage() +
                                    "\nYou can set it up later from your profile.",
                            Toast.LENGTH_LONG).show();

                    mAuth.signOut();
                    goToLogin();
                });
    }
}