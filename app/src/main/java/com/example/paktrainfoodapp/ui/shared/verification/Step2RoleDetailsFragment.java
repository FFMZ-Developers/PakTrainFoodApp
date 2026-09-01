package com.example.paktrainfoodapp.ui.shared.verification;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.data.AppConfig;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class Step2RoleDetailsFragment extends Fragment {

    private VerificationViewModel viewModel;

    // Restaurant fields
    private TextInputEditText editRestaurantName, editRestaurantAddress, editLicenseNumber;
    private Spinner spinnerCity;
    private ImageView imgLicense;
    private TextView txtLicenseHint;
    private TextView txtOpeningTime, txtClosingTime;
    private String openingTime = "10:00";
    private String closingTime = "22:00";

    // Module: restaurant's real coordinates - required for the rider's
    // turn-by-turn route (DirectionsHelper). Before this, a restaurant
    // only had a free-text address, which can't be routed to.
    private TextView txtRestaurantLocation;
    private Double restaurantLat = null;
    private Double restaurantLng = null;
    private ActivityResultLauncher<String> licensePicker;

    // Rider fields
    private TextInputEditText editRiderAddress, editDrivingLicenseNumber, editVehicleNumber;
    private Spinner spinnerRiderCity;
    private ImageView imgDrivingLicense, imgBike;
    private TextView txtDrivingLicenseHint, txtBikeHint;
    private ActivityResultLauncher<String> drivingLicensePicker;
    private ActivityResultLauncher<String> bikePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        licensePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    viewModel.setLicenseImageUri(uri);
                    Glide.with(this).load(uri).into(imgLicense);
                    txtLicenseHint.setVisibility(View.GONE);
                });

        drivingLicensePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    viewModel.setDrivingLicenseImageUri(uri);
                    Glide.with(this).load(uri).into(imgDrivingLicense);
                    txtDrivingLicenseHint.setVisibility(View.GONE);
                });

        bikePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    viewModel.setBikeImageUri(uri);
                    Glide.with(this).load(uri).into(imgBike);
                    txtBikeHint.setVisibility(View.GONE);
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_verification_step2, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(VerificationViewModel.class);

        LinearLayout groupRestaurant = view.findViewById(R.id.group_restaurant);
        LinearLayout groupRider = view.findViewById(R.id.group_rider);

        boolean isRestaurant = VerificationViewModel.ROLE_RESTAURANT.equals(viewModel.getRole());

        groupRestaurant.setVisibility(isRestaurant ? View.VISIBLE : View.GONE);
        groupRider.setVisibility(isRestaurant ? View.GONE : View.VISIBLE);

        // City list is admin-configurable (Settings/orderConfig.cities), same
        // list reused for both roles rather than two separate hardcoded arrays.
        List<String> cities = AppConfig.get().getCities();

        ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, cities);
        cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        if (isRestaurant) {

            editRestaurantName = view.findViewById(R.id.edit_restaurant_name);
            editRestaurantAddress = view.findViewById(R.id.edit_restaurant_address);
            editLicenseNumber = view.findViewById(R.id.edit_license_number);
            spinnerCity = view.findViewById(R.id.spinner_city);
            imgLicense = view.findViewById(R.id.img_license);
            txtLicenseHint = view.findViewById(R.id.txt_license_hint);
            txtOpeningTime = view.findViewById(R.id.txt_wizard_opening_time);
            txtClosingTime = view.findViewById(R.id.txt_wizard_closing_time);

            if (viewModel.getOpeningTime() != null) openingTime = viewModel.getOpeningTime();
            if (viewModel.getClosingTime() != null) closingTime = viewModel.getClosingTime();
            txtOpeningTime.setText("Opens: " + to12Hour(openingTime));
            txtClosingTime.setText("Closes: " + to12Hour(closingTime));

            txtOpeningTime.setOnClickListener(v -> pickTime(true));
            txtClosingTime.setOnClickListener(v -> pickTime(false));

            txtRestaurantLocation = view.findViewById(R.id.txt_restaurant_location);

            if (viewModel.getRestaurantLat() != null && viewModel.getRestaurantLng() != null) {
                restaurantLat = viewModel.getRestaurantLat();
                restaurantLng = viewModel.getRestaurantLng();
                showCapturedLocation();
            }

            view.findViewById(R.id.btn_capture_location)
                    .setOnClickListener(v -> captureCurrentLocation());

            spinnerCity.setAdapter(cityAdapter);

            if (viewModel.getRestaurantName() != null) editRestaurantName.setText(viewModel.getRestaurantName());
            if (viewModel.getRestaurantAddress() != null) editRestaurantAddress.setText(viewModel.getRestaurantAddress());
            if (viewModel.getLicenseNumber() != null) editLicenseNumber.setText(viewModel.getLicenseNumber());

            if (viewModel.getRestaurantCity() != null) {
                int index = cities.indexOf(viewModel.getRestaurantCity());
                if (index >= 0) spinnerCity.setSelection(index);
            }

            if (viewModel.getLicenseImageUri() != null) {
                Glide.with(this).load(viewModel.getLicenseImageUri()).into(imgLicense);
                txtLicenseHint.setVisibility(View.GONE);
            } else if (!TextUtils.isEmpty(viewModel.getExistingLicenseImageUrl())) {
                Glide.with(this).load(viewModel.getExistingLicenseImageUrl()).into(imgLicense);
                txtLicenseHint.setVisibility(View.GONE);
            }

            view.findViewById(R.id.card_license).setOnClickListener(v -> licensePicker.launch("image/*"));

        } else {

            editRiderAddress = view.findViewById(R.id.edit_rider_address);
            spinnerRiderCity = view.findViewById(R.id.spinner_rider_city);
            editDrivingLicenseNumber = view.findViewById(R.id.edit_driving_license_number);
            imgDrivingLicense = view.findViewById(R.id.img_driving_license);
            txtDrivingLicenseHint = view.findViewById(R.id.txt_driving_license_hint);
            editVehicleNumber = view.findViewById(R.id.edit_vehicle_number);
            imgBike = view.findViewById(R.id.img_bike);
            txtBikeHint = view.findViewById(R.id.txt_bike_hint);

            spinnerRiderCity.setAdapter(cityAdapter);

            if (viewModel.getRiderAddress() != null) editRiderAddress.setText(viewModel.getRiderAddress());
            if (viewModel.getDrivingLicenseNumber() != null) editDrivingLicenseNumber.setText(viewModel.getDrivingLicenseNumber());
            if (viewModel.getVehicleRegistrationNumber() != null) editVehicleNumber.setText(viewModel.getVehicleRegistrationNumber());

            if (viewModel.getRiderCity() != null) {
                int index = cities.indexOf(viewModel.getRiderCity());
                if (index >= 0) spinnerRiderCity.setSelection(index);
            }

            if (viewModel.getDrivingLicenseImageUri() != null) {
                Glide.with(this).load(viewModel.getDrivingLicenseImageUri()).into(imgDrivingLicense);
                txtDrivingLicenseHint.setVisibility(View.GONE);
            } else if (!TextUtils.isEmpty(viewModel.getExistingDrivingLicenseImageUrl())) {
                Glide.with(this).load(viewModel.getExistingDrivingLicenseImageUrl()).into(imgDrivingLicense);
                txtDrivingLicenseHint.setVisibility(View.GONE);
            }

            if (viewModel.getBikeImageUri() != null) {
                Glide.with(this).load(viewModel.getBikeImageUri()).into(imgBike);
                txtBikeHint.setVisibility(View.GONE);
            } else if (!TextUtils.isEmpty(viewModel.getExistingBikeImageUrl())) {
                Glide.with(this).load(viewModel.getExistingBikeImageUrl()).into(imgBike);
                txtBikeHint.setVisibility(View.GONE);
            }

            view.findViewById(R.id.card_driving_license).setOnClickListener(v -> drivingLicensePicker.launch("image/*"));
            view.findViewById(R.id.card_bike).setOnClickListener(v -> bikePicker.launch("image/*"));
        }

        view.findViewById(R.id.btn_step2_next).setOnClickListener(v -> validateAndContinue(isRestaurant));
    }

    private void validateAndContinue(boolean isRestaurant) {

        if (isRestaurant) {

            String name = editRestaurantName.getText() != null ? editRestaurantName.getText().toString().trim() : "";
            String address = editRestaurantAddress.getText() != null ? editRestaurantAddress.getText().toString().trim() : "";
            String license = editLicenseNumber.getText() != null ? editLicenseNumber.getText().toString().trim() : "";

            if (TextUtils.isEmpty(name)) {
                editRestaurantName.setError("Enter your restaurant name");
                return;
            }

            if (TextUtils.isEmpty(address)) {
                editRestaurantAddress.setError("Enter your restaurant address");
                return;
            }

            if (TextUtils.isEmpty(license)) {
                editLicenseNumber.setError("Enter your food authority license number");
                return;
            }

            boolean hasLicensePhoto = viewModel.getLicenseImageUri() != null
                    || !TextUtils.isEmpty(viewModel.getExistingLicenseImageUrl());

            if (!hasLicensePhoto) {
                Toast.makeText(requireContext(), "Please add a photo of your license document", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.setRestaurantName(name);
            viewModel.setRestaurantAddress(address);
            viewModel.setLicenseNumber(license);
            viewModel.setRestaurantCity(spinnerCity.getSelectedItem().toString());
            viewModel.setRestaurantLat(restaurantLat);
            viewModel.setRestaurantLng(restaurantLng);
            viewModel.setOpeningTime(openingTime);
            viewModel.setClosingTime(closingTime);

        } else {

            String address = editRiderAddress.getText() != null ? editRiderAddress.getText().toString().trim() : "";
            String drivingLicenseNumber = editDrivingLicenseNumber.getText() != null ? editDrivingLicenseNumber.getText().toString().trim() : "";
            String vehicleNumber = editVehicleNumber.getText() != null ? editVehicleNumber.getText().toString().trim() : "";

            if (TextUtils.isEmpty(address)) {
                editRiderAddress.setError("Enter your address");
                return;
            }

            if (TextUtils.isEmpty(drivingLicenseNumber)) {
                editDrivingLicenseNumber.setError("Enter your driving license number");
                return;
            }

            if (TextUtils.isEmpty(vehicleNumber)) {
                editVehicleNumber.setError("Enter your bike registration number");
                return;
            }

            boolean hasDrivingLicensePhoto = viewModel.getDrivingLicenseImageUri() != null
                    || !TextUtils.isEmpty(viewModel.getExistingDrivingLicenseImageUrl());

            if (!hasDrivingLicensePhoto) {
                Toast.makeText(requireContext(), "Please add a photo of your driving license", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean hasBikePhoto = viewModel.getBikeImageUri() != null
                    || !TextUtils.isEmpty(viewModel.getExistingBikeImageUrl());

            if (!hasBikePhoto) {
                Toast.makeText(requireContext(), "Please add a photo of your bike with the number plate visible", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.setRiderAddress(address);
            viewModel.setRiderCity(spinnerRiderCity.getSelectedItem().toString());
            viewModel.setDrivingLicenseNumber(drivingLicenseNumber);
            viewModel.setVehicleRegistrationNumber(vehicleNumber);
        }

        if (requireActivity() instanceof VerificationWizardActivity) {
            ((VerificationWizardActivity) requireActivity())
                    .showStep(new Step3BankFragment(), 3, "Bank Details");
        }
    }

    /**
     * Captures the restaurant's real coordinates from the device's GPS -
     * the assumption being the owner registers while physically at the
     * restaurant. These coordinates are what the rider's route is
     * calculated to (see DirectionsHelper / RiderTrackingFragment); a
     * free-text address alone can't be routed to.
     */
    private void captureCurrentLocation() {

        if (!isAdded()) return;

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{ android.Manifest.permission.ACCESS_FINE_LOCATION }, 4021);
            return;
        }

        txtRestaurantLocation.setText("Getting your location...");

        com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(requireActivity())
                .getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {

                    if (!isAdded()) return;

                    if (location == null) {
                        txtRestaurantLocation.setText("Couldn't get location - please try again outdoors");
                        return;
                    }

                    restaurantLat = location.getLatitude();
                    restaurantLng = location.getLongitude();

                    showCapturedLocation();
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    txtRestaurantLocation.setText("Location failed: " + e.getMessage());
                });
    }

    private void showCapturedLocation() {

        if (txtRestaurantLocation == null || restaurantLat == null || restaurantLng == null) return;

        txtRestaurantLocation.setText(String.format(java.util.Locale.US,
                "📍 Location set (%.5f, %.5f)", restaurantLat, restaurantLng));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 4021
                && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            captureCurrentLocation();
        }
    }

    private void pickTime(boolean isOpening) {

        String current = isOpening ? openingTime : closingTime;
        String[] parts = current.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);

        new TimePickerDialog(requireContext(), (view, selectedHour, selectedMinute) -> {

            String formatted = String.format(java.util.Locale.US, "%02d:%02d", selectedHour, selectedMinute);

            if (isOpening) {
                openingTime = formatted;
                txtOpeningTime.setText("Opens: " + to12Hour(formatted));
            } else {
                closingTime = formatted;
                txtClosingTime.setText("Closes: " + to12Hour(formatted));
            }

        }, hour, minute, false).show();
    }

    private String to12Hour(String hhmm) {

        try {
            String[] parts = hhmm.split(":");
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            cal.set(java.util.Calendar.MINUTE, Integer.parseInt(parts[1]));
            return new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(cal.getTime());
        } catch (Exception e) {
            return hhmm;
        }
    }
}
