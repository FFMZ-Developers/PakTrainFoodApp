package com.example.paktrainfoodapp.ui.shared.verification;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.utils.DocumentUploader;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Final step: capture a live selfie, upload any newly-picked documents,
 * then write the complete profile document with verificationStatus set to
 * "pending" - this is what makes the applicant show up in the admin
 * panel's review queue.
 *
 * Upload chain: CNIC front -> CNIC back -> (restaurant: license | rider:
 * driving license -> bike photo) -> selfie -> Firestore write. Anything
 * not re-picked on a resubmit keeps its existing URL instead of
 * re-uploading.
 */
public class Step4SelfieFragment extends Fragment {

    private ImageView imgSelfie;
    private Button btnTakeSelfie, btnSubmit;
    private ProgressBar progressSubmit;

    private VerificationViewModel viewModel;
    private Bitmap capturedSelfie;

    private ActivityResultLauncher<Void> cameraLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TakePicturePreview returns a Bitmap directly - no FileProvider/
        // manifest setup needed, and preview resolution is plenty for a
        // face-match selfie.
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    if (bitmap == null) return;
                    capturedSelfie = bitmap;
                    imgSelfie.setImageBitmap(bitmap);
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_verification_step4, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imgSelfie = view.findViewById(R.id.img_selfie);
        btnTakeSelfie = view.findViewById(R.id.btn_take_selfie);
        btnSubmit = view.findViewById(R.id.btn_submit);
        progressSubmit = view.findViewById(R.id.progress_submit);

        viewModel = new ViewModelProvider(requireActivity()).get(VerificationViewModel.class);

        btnTakeSelfie.setOnClickListener(v -> cameraLauncher.launch(null));

        btnSubmit.setOnClickListener(v -> validateAndSubmit());
    }

    private void validateAndSubmit() {

        boolean hasSelfie = capturedSelfie != null || viewModel.getExistingSelfieUrl() != null;

        if (!hasSelfie) {
            Toast.makeText(requireContext(), "Please take a live selfie first", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        uploadCnicFront();
    }

    private void uploadCnicFront() {

        if (viewModel.getCnicFrontUri() == null) {
            uploadCnicBack(viewModel.getExistingCnicFrontUrl());
            return;
        }

        DocumentUploader.upload(requireContext(), roleFolder(), viewModel.getUid(), "cnic_front",
                viewModel.getCnicFrontUri(), new DocumentUploader.UploadCallback() {

                    @Override
                    public void onSuccess(String downloadUrl) {
                        uploadCnicBack(downloadUrl);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        onUploadFailed("CNIC front", e);
                    }
                });
    }

    private void uploadCnicBack(String cnicFrontUrl) {

        if (viewModel.getCnicBackUri() == null) {
            uploadRoleDocuments(cnicFrontUrl, viewModel.getExistingCnicBackUrl());
            return;
        }

        DocumentUploader.upload(requireContext(), roleFolder(), viewModel.getUid(), "cnic_back",
                viewModel.getCnicBackUri(), new DocumentUploader.UploadCallback() {

                    @Override
                    public void onSuccess(String downloadUrl) {
                        uploadRoleDocuments(cnicFrontUrl, downloadUrl);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        onUploadFailed("CNIC back", e);
                    }
                });
    }

    /** Branches to the restaurant's license upload or the rider's driving-license/bike uploads. */
    private void uploadRoleDocuments(String cnicFrontUrl, String cnicBackUrl) {

        if (VerificationViewModel.ROLE_RESTAURANT.equals(viewModel.getRole())) {
            uploadLicense(cnicFrontUrl, cnicBackUrl);
        } else {
            uploadDrivingLicense(cnicFrontUrl, cnicBackUrl);
        }
    }

    private void uploadLicense(String cnicFrontUrl, String cnicBackUrl) {

        if (viewModel.getLicenseImageUri() == null) {
            uploadSelfie(cnicFrontUrl, cnicBackUrl, viewModel.getExistingLicenseImageUrl(), null, null);
            return;
        }

        DocumentUploader.upload(requireContext(), "restaurant", viewModel.getUid(), "license",
                viewModel.getLicenseImageUri(), new DocumentUploader.UploadCallback() {

                    @Override
                    public void onSuccess(String downloadUrl) {
                        uploadSelfie(cnicFrontUrl, cnicBackUrl, downloadUrl, null, null);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        onUploadFailed("license document", e);
                    }
                });
    }

    private void uploadDrivingLicense(String cnicFrontUrl, String cnicBackUrl) {

        if (viewModel.getDrivingLicenseImageUri() == null) {
            uploadBike(cnicFrontUrl, cnicBackUrl, viewModel.getExistingDrivingLicenseImageUrl());
            return;
        }

        DocumentUploader.upload(requireContext(), "delivery", viewModel.getUid(), "driving_license",
                viewModel.getDrivingLicenseImageUri(), new DocumentUploader.UploadCallback() {

                    @Override
                    public void onSuccess(String downloadUrl) {
                        uploadBike(cnicFrontUrl, cnicBackUrl, downloadUrl);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        onUploadFailed("driving license", e);
                    }
                });
    }

    private void uploadBike(String cnicFrontUrl, String cnicBackUrl, String drivingLicenseUrl) {

        if (viewModel.getBikeImageUri() == null) {
            uploadSelfie(cnicFrontUrl, cnicBackUrl, null, drivingLicenseUrl, viewModel.getExistingBikeImageUrl());
            return;
        }

        DocumentUploader.upload(requireContext(), "delivery", viewModel.getUid(), "bike",
                viewModel.getBikeImageUri(), new DocumentUploader.UploadCallback() {

                    @Override
                    public void onSuccess(String downloadUrl) {
                        uploadSelfie(cnicFrontUrl, cnicBackUrl, null, drivingLicenseUrl, downloadUrl);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        onUploadFailed("bike photo", e);
                    }
                });
    }

    private void uploadSelfie(String cnicFrontUrl, String cnicBackUrl,
                              String licenseUrl, String drivingLicenseUrl, String bikeUrl) {

        if (capturedSelfie == null) {
            saveToFirestore(cnicFrontUrl, cnicBackUrl, licenseUrl, drivingLicenseUrl, bikeUrl, viewModel.getExistingSelfieUrl());
            return;
        }

        DocumentUploader.uploadBitmap(roleFolder(), viewModel.getUid(), "selfie", capturedSelfie,
                new DocumentUploader.UploadCallback() {

                    @Override
                    public void onSuccess(String downloadUrl) {
                        saveToFirestore(cnicFrontUrl, cnicBackUrl, licenseUrl, drivingLicenseUrl, bikeUrl, downloadUrl);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        onUploadFailed("selfie", e);
                    }
                });
    }

    private String roleFolder() {
        return VerificationViewModel.ROLE_RESTAURANT.equals(viewModel.getRole()) ? "restaurant" : "delivery";
    }

    private void saveToFirestore(String cnicFrontUrl, String cnicBackUrl,
                                 String licenseUrl, String drivingLicenseUrl, String bikeUrl,
                                 String selfieUrl) {

        boolean isRestaurant = VerificationViewModel.ROLE_RESTAURANT.equals(viewModel.getRole());

        Map<String, Object> data = new HashMap<>();

        data.put("uid", viewModel.getUid());
        data.put("email", viewModel.getEmail());
        data.put("phone", viewModel.getPhone());
        data.put("role", viewModel.getRole());

        data.put("cnicFrontUrl", cnicFrontUrl);
        data.put("cnicBackUrl", cnicBackUrl);
        data.put("selfieUrl", selfieUrl);

        data.put("bankName", viewModel.getBankName());
        data.put("bankAccountHolder", viewModel.getBankAccountHolder());
        data.put("bankAccountNumber", viewModel.getBankAccountNumber());

        // verificationStatus is the source of truth for the admin queue and
        // the login gate; isVerified is kept in sync for backward
        // compatibility with the existing login/dashboard checks.
        // The admin panel's pending/active lists filter by this legacy
        // "status" field (Pending/Approved/Rejected), not verificationStatus -
        // both must be kept in sync on every submit, including a resubmit
        // after a rejection, or the account becomes invisible in the admin
        // panel (still tagged "Rejected" there even though it was resubmitted).
        data.put("status", "Pending");
        data.put("verificationStatus", "pending");
        data.put("isVerified", false);
        data.put("rejectionReason", null);
        data.put("submittedAt", FieldValue.serverTimestamp());

        if (isRestaurant) {

            data.put("name", viewModel.getFullName());
            data.put("ownerName", viewModel.getFullName());
            data.put("restaurantName", viewModel.getRestaurantName());
            data.put("address", viewModel.getRestaurantAddress());
            data.put("restaurantAddress", viewModel.getRestaurantAddress());
            data.put("city", viewModel.getRestaurantCity());
            data.put("cityNormalized", com.example.paktrainfoodapp.utils.CityNameUtils.normalize(viewModel.getRestaurantCity()));
            if (viewModel.getRestaurantLat() != null && viewModel.getRestaurantLng() != null) {
                data.put("restaurantLat", viewModel.getRestaurantLat());
                data.put("restaurantLng", viewModel.getRestaurantLng());
            }
            data.put("openingTime", viewModel.getOpeningTime() != null ? viewModel.getOpeningTime() : "10:00");
            data.put("closingTime", viewModel.getClosingTime() != null ? viewModel.getClosingTime() : "22:00");
            data.put("licenseNo", viewModel.getLicenseNumber());
            data.put("foodAuthorityLicenseNumber", viewModel.getLicenseNumber());
            data.put("licenseImageUrl", licenseUrl);

        } else {

            data.put("name", viewModel.getFullName());
            data.put("address", viewModel.getRiderAddress());
            data.put("city", viewModel.getRiderCity());
            data.put("cityNormalized", com.example.paktrainfoodapp.utils.CityNameUtils.normalize(viewModel.getRiderCity()));
            data.put("drivingLicenseNumber", viewModel.getDrivingLicenseNumber());
            data.put("drivingLicenseImageUrl", drivingLicenseUrl);
            data.put("vehicleType", "Bike");
            data.put("vehicleRegistrationNumber", viewModel.getVehicleRegistrationNumber());
            data.put("bikeImageUrl", bikeUrl);
        }

        String collectionRoot = isRestaurant ? "Restaurant" : "Delivery";

        FirebaseFirestore.getInstance()
                .collection("Users").document(collectionRoot)
                .collection("VerifiedRegister").document(viewModel.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    if (!isAdded()) return;

                    setLoading(false);

                    if (requireActivity() instanceof VerificationWizardActivity) {
                        ((VerificationWizardActivity) requireActivity())
                                .showFinalScreen(new PendingReviewFragment());
                    }
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    setLoading(false);

                    Toast.makeText(requireContext(),
                            "Could not submit: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void onUploadFailed(String what, Exception e) {

        if (!isAdded()) return;

        setLoading(false);

        Toast.makeText(requireContext(),
                "Failed to upload " + what + ": " + e.getMessage(),
                Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {

        progressSubmit.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!loading);
        btnTakeSelfie.setEnabled(!loading);
    }
}
