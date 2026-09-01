package com.example.paktrainfoodapp.ui.shared.verification;

import android.net.Uri;

import androidx.lifecycle.ViewModel;

/**
 * Holds everything collected across the 4-step verification wizard, so each
 * step fragment only needs to read/write the fields it owns, and the final
 * step can submit the whole thing at once.
 *
 * Scoped to VerificationWizardActivity (one instance shared by all its
 * fragments), not to an individual fragment - that's what makes the data
 * survive moving between steps.
 */
public class VerificationViewModel extends ViewModel {

    public static final String ROLE_RESTAURANT = "RESTAURANT";
    public static final String ROLE_DELIVERY = "DELIVERY";

    private String role;
    private String uid;
    private String email;

    /** True when this is a re-submission after a rejection, not a first-time signup. */
    private boolean isResubmit = false;

    // ---- Step 1: personal ----
    private String fullName;
    private String phone;
    private Uri cnicFrontUri;
    private Uri cnicBackUri;
    private String existingCnicFrontUrl; // used on resubmit if the passenger doesn't re-pick it
    private String existingCnicBackUrl;

    // ---- Step 2: role details ----
    // Restaurant
    private String restaurantName;
    private String restaurantAddress;
    private String restaurantCity;
    private Double restaurantLat;
    private Double restaurantLng;
    private String openingTime;
    private String closingTime;
    private String licenseNumber;
    private Uri licenseImageUri;
    private String existingLicenseImageUrl;
    // Delivery
    private String riderAddress;
    private String riderCity;
    private String drivingLicenseNumber;
    private Uri drivingLicenseImageUri;
    private String existingDrivingLicenseImageUrl;
    private String vehicleRegistrationNumber;
    private Uri bikeImageUri;
    private String existingBikeImageUrl;

    // ---- Step 3: bank ----
    private String bankName;
    private String bankAccountHolder;
    private String bankAccountNumber;

    // ---- Step 4: selfie ----
    private Uri selfieUri;
    private String existingSelfieUrl;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isResubmit() { return isResubmit; }
    public void setResubmit(boolean resubmit) { isResubmit = resubmit; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Uri getCnicFrontUri() { return cnicFrontUri; }
    public void setCnicFrontUri(Uri cnicFrontUri) { this.cnicFrontUri = cnicFrontUri; }

    public Uri getCnicBackUri() { return cnicBackUri; }
    public void setCnicBackUri(Uri cnicBackUri) { this.cnicBackUri = cnicBackUri; }

    public String getExistingCnicFrontUrl() { return existingCnicFrontUrl; }
    public void setExistingCnicFrontUrl(String url) { this.existingCnicFrontUrl = url; }

    public String getExistingCnicBackUrl() { return existingCnicBackUrl; }
    public void setExistingCnicBackUrl(String url) { this.existingCnicBackUrl = url; }

    public String getRestaurantName() { return restaurantName; }
    public void setRestaurantName(String restaurantName) { this.restaurantName = restaurantName; }

    public String getRestaurantAddress() { return restaurantAddress; }
    public void setRestaurantAddress(String restaurantAddress) { this.restaurantAddress = restaurantAddress; }

    public String getRestaurantCity() { return restaurantCity; }
    public void setRestaurantCity(String restaurantCity) { this.restaurantCity = restaurantCity; }

    public Double getRestaurantLat() { return restaurantLat; }
    public void setRestaurantLat(Double restaurantLat) { this.restaurantLat = restaurantLat; }

    public Double getRestaurantLng() { return restaurantLng; }
    public void setRestaurantLng(Double restaurantLng) { this.restaurantLng = restaurantLng; }

    public String getOpeningTime() { return openingTime; }
    public void setOpeningTime(String openingTime) { this.openingTime = openingTime; }

    public String getClosingTime() { return closingTime; }
    public void setClosingTime(String closingTime) { this.closingTime = closingTime; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public Uri getLicenseImageUri() { return licenseImageUri; }
    public void setLicenseImageUri(Uri licenseImageUri) { this.licenseImageUri = licenseImageUri; }

    public String getExistingLicenseImageUrl() { return existingLicenseImageUrl; }
    public void setExistingLicenseImageUrl(String url) { this.existingLicenseImageUrl = url; }

    public String getRiderAddress() { return riderAddress; }
    public void setRiderAddress(String riderAddress) { this.riderAddress = riderAddress; }

    public String getRiderCity() { return riderCity; }
    public void setRiderCity(String riderCity) { this.riderCity = riderCity; }

    public String getDrivingLicenseNumber() { return drivingLicenseNumber; }
    public void setDrivingLicenseNumber(String drivingLicenseNumber) { this.drivingLicenseNumber = drivingLicenseNumber; }

    public Uri getDrivingLicenseImageUri() { return drivingLicenseImageUri; }
    public void setDrivingLicenseImageUri(Uri uri) { this.drivingLicenseImageUri = uri; }

    public String getExistingDrivingLicenseImageUrl() { return existingDrivingLicenseImageUrl; }
    public void setExistingDrivingLicenseImageUrl(String url) { this.existingDrivingLicenseImageUrl = url; }

    public String getVehicleRegistrationNumber() { return vehicleRegistrationNumber; }
    public void setVehicleRegistrationNumber(String v) { this.vehicleRegistrationNumber = v; }

    public Uri getBikeImageUri() { return bikeImageUri; }
    public void setBikeImageUri(Uri bikeImageUri) { this.bikeImageUri = bikeImageUri; }

    public String getExistingBikeImageUrl() { return existingBikeImageUrl; }
    public void setExistingBikeImageUrl(String url) { this.existingBikeImageUrl = url; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankAccountHolder() { return bankAccountHolder; }
    public void setBankAccountHolder(String bankAccountHolder) { this.bankAccountHolder = bankAccountHolder; }

    public String getBankAccountNumber() { return bankAccountNumber; }
    public void setBankAccountNumber(String bankAccountNumber) { this.bankAccountNumber = bankAccountNumber; }

    public Uri getSelfieUri() { return selfieUri; }
    public void setSelfieUri(Uri selfieUri) { this.selfieUri = selfieUri; }

    public String getExistingSelfieUrl() { return existingSelfieUrl; }
    public void setExistingSelfieUrl(String url) { this.existingSelfieUrl = url; }
}
