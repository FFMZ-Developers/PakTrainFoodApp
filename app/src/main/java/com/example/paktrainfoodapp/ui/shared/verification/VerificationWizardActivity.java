package com.example.paktrainfoodapp.ui.shared.verification;

import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.example.paktrainfoodapp.R;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Hosts the 4-step restaurant/rider verification wizard:
 *   1. Personal details (name, phone, CNIC)
 *   2. Role details (restaurant info, or vehicle info for a rider)
 *   3. Bank details
 *   4. Live selfie + submit
 *
 * Launched two ways:
 *  - First-time signup: EXTRA_ROLE, EXTRA_UID, EXTRA_EMAIL set, EXTRA_RESUBMIT
 *    false/absent - wizard starts empty.
 *  - Resubmission after a rejection: EXTRA_RESUBMIT true - wizard pre-loads
 *    the applicant's previously-submitted data from Firestore so they only
 *    need to fix what was flagged, matching the "same account, not a
 *    duplicate" resubmit design.
 */
public class VerificationWizardActivity extends AppCompatActivity {

    public static final String EXTRA_ROLE = "role";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_EMAIL = "email";
    public static final String EXTRA_RESUBMIT = "resubmit";

    private TextView txtStepLabel;
    private ProgressBar progressStep;

    private VerificationViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification_wizard);

        txtStepLabel = findViewById(R.id.txtStepLabel);
        progressStep = findViewById(R.id.progressStep);

        viewModel = new ViewModelProvider(this).get(VerificationViewModel.class);

        String role = getIntent().getStringExtra(EXTRA_ROLE);
        String uid = getIntent().getStringExtra(EXTRA_UID);
        String email = getIntent().getStringExtra(EXTRA_EMAIL);
        boolean resubmit = getIntent().getBooleanExtra(EXTRA_RESUBMIT, false);

        viewModel.setRole(role);
        viewModel.setUid(uid);
        viewModel.setEmail(email);
        viewModel.setResubmit(resubmit);

        if (savedInstanceState == null) {

            if (resubmit) {
                prefillFromExistingDocument();
            } else {
                showStep(new Step1PersonalFragment(), 1, "Personal Details");
            }
        }
    }

    /** Loads the applicant's previously-submitted data before showing step 1, for a resubmit. */
    private void prefillFromExistingDocument() {

        String collectionRoot = VerificationViewModel.ROLE_RESTAURANT.equals(viewModel.getRole())
                ? "Restaurant" : "Delivery";

        FirebaseFirestore.getInstance()
                .collection("Users").document(collectionRoot)
                .collection("VerifiedRegister").document(viewModel.getUid())
                .get()
                .addOnSuccessListener(this::applyExistingDocument)
                .addOnFailureListener(e ->
                        showStep(new Step1PersonalFragment(), 1, "Personal Details"));
    }

    private void applyExistingDocument(DocumentSnapshot doc) {

        if (doc != null && doc.exists()) {

            viewModel.setFullName(firstNonNull(doc.getString("ownerName"), doc.getString("name")));
            viewModel.setPhone(doc.getString("phone"));
            viewModel.setExistingCnicFrontUrl(doc.getString("cnicFrontUrl"));
            viewModel.setExistingCnicBackUrl(doc.getString("cnicBackUrl"));

            viewModel.setRestaurantName(doc.getString("restaurantName"));
            viewModel.setRestaurantAddress(firstNonNull(doc.getString("restaurantAddress"), doc.getString("address")));
            viewModel.setRestaurantCity(doc.getString("city"));
            viewModel.setRestaurantLat(doc.getDouble("restaurantLat"));
            viewModel.setRestaurantLng(doc.getDouble("restaurantLng"));
            viewModel.setOpeningTime(doc.getString("openingTime"));
            viewModel.setClosingTime(doc.getString("closingTime"));
            viewModel.setLicenseNumber(firstNonNull(doc.getString("foodAuthorityLicenseNumber"), doc.getString("licenseNo")));
            viewModel.setExistingLicenseImageUrl(doc.getString("licenseImageUrl"));

            viewModel.setRiderAddress(doc.getString("address"));
            viewModel.setRiderCity(doc.getString("city"));
            viewModel.setDrivingLicenseNumber(doc.getString("drivingLicenseNumber"));
            viewModel.setExistingDrivingLicenseImageUrl(doc.getString("drivingLicenseImageUrl"));
            viewModel.setVehicleRegistrationNumber(doc.getString("vehicleRegistrationNumber"));
            viewModel.setExistingBikeImageUrl(doc.getString("bikeImageUrl"));

            viewModel.setBankName(doc.getString("bankName"));
            viewModel.setBankAccountHolder(doc.getString("bankAccountHolder"));
            viewModel.setBankAccountNumber(doc.getString("bankAccountNumber"));

            viewModel.setExistingSelfieUrl(doc.getString("selfieUrl"));
        }

        if (!isFinishing()) {
            showStep(new Step1PersonalFragment(), 1, "Personal Details");
        }
    }

    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    /** Called by each step fragment to advance to the next one. */
    public void showStep(Fragment fragment, int stepNumber, String stepTitle) {

        progressStep.setProgress(stepNumber);
        txtStepLabel.setText("Step " + stepNumber + " of 4 \u2014 " + stepTitle);

        FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.wizard_container, fragment);

        if (stepNumber > 1) {
            transaction.addToBackStack(null);
        }

        transaction.commit();
    }

    /**
     * Shows a terminal screen (Pending) that has its own self-contained
     * header, instead of the numbered step chrome - used right after a
     * successful submit, so the wizard-hosted view looks identical to how
     * MainActivity shows the same fragment standalone on a later login.
     * Without this, the fragment appeared with no title at all whenever it
     * was shown outside the wizard (its own layout has no header of its
     * own to fall back on if the wizard's chrome isn't hidden either).
     */
    public void showFinalScreen(Fragment fragment) {

        findViewById(R.id.wizard_top_bar).setVisibility(android.view.View.GONE);
        findViewById(R.id.wizard_step_progress).setVisibility(android.view.View.GONE);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.wizard_container, fragment)
                .commit();
    }

    public VerificationViewModel getViewModel() {
        return viewModel;
    }
}
