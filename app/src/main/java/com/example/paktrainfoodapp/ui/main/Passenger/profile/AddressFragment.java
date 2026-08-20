package com.example.paktrainfoodapp.ui.main.Passenger.profile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lets a passenger set their delivery address either by typing it manually,
 * by dragging the map so the fixed centre pin sits on their location, or by
 * tapping "Use My Current Location".
 */
public class AddressFragment extends Fragment {

    private static final LatLng DEFAULT_LOCATION = new LatLng(31.5204, 74.3587); // Lahore

    private TextInputEditText editAddress;
    private TextView txtSelectedLatLng;
    private Button btnSave, btnUseCurrent;

    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;

    private double selectedLat = 0;
    private double selectedLng = 0;
    private boolean hasSelection = false;

    private ActivityResultLauncher<String> locationPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        moveToCurrentLocation();
                    } else {
                        Toast.makeText(requireContext(),
                                "Location permission denied - you can still drag the map or type the address",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_address, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        editAddress = view.findViewById(R.id.edit_address);
        txtSelectedLatLng = view.findViewById(R.id.txt_selected_latlng);
        btnSave = view.findViewById(R.id.btn_save_address);
        btnUseCurrent = view.findViewById(R.id.btnUseCurrentLocation);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (isAdded()) getParentFragmentManager().popBackStack();
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.addressMap);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this::onMapReady);
        }

        btnUseCurrent.setOnClickListener(v -> requestCurrentLocation());

        btnSave.setOnClickListener(v -> saveAddress());

        loadSavedAddress();
    }

    private void onMapReady(GoogleMap map) {

        googleMap = map;

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, 12f));

        // The pin is a fixed overlay in the centre of the map, so whatever the
        // camera settles on is the chosen point.
        googleMap.setOnCameraIdleListener(() -> {

            if (googleMap == null) return;

            LatLng center = googleMap.getCameraPosition().target;

            selectedLat = center.latitude;
            selectedLng = center.longitude;
            hasSelection = true;

            updateLatLngLabel();
        });
    }

    private void requestCurrentLocation() {

        boolean granted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (granted) {
            moveToCurrentLocation();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void moveToCurrentLocation() {

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {

                    if (!isAdded()) return;

                    if (location == null) {
                        Toast.makeText(requireContext(),
                                "Could not get current location - try dragging the map",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    LatLng here = new LatLng(location.getLatitude(), location.getLongitude());

                    if (googleMap != null) {
                        // Camera-idle listener will pick up the new coordinates
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(here, 16f));
                    }

                    fillAddressFromCoordinates(here.latitude, here.longitude);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "Location error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Best-effort reverse geocoding to pre-fill the text field. If it fails
     * (no network, no geocoder backend) the passenger can still type it.
     */
    private void fillAddressFromCoordinates(double lat, double lng) {

        try {

            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());

            List<Address> results = geocoder.getFromLocation(lat, lng, 1);

            if (results != null && !results.isEmpty()) {

                Address address = results.get(0);

                StringBuilder sb = new StringBuilder();

                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(address.getAddressLine(i));
                }

                String line = sb.toString();

                if (!TextUtils.isEmpty(line)
                        && editAddress != null
                        && TextUtils.isEmpty(editAddress.getText())) {

                    editAddress.setText(line);
                }
            }

        } catch (Exception ignored) {
            // Geocoding is a convenience only - never block saving on it.
        }
    }

    private void updateLatLngLabel() {

        if (txtSelectedLatLng == null) return;

        txtSelectedLatLng.setText(
                String.format(Locale.getDefault(), "%.5f, %.5f", selectedLat, selectedLng));
    }

    private void loadSavedAddress() {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("Users")
                .document("Passenger")
                .collection("Register")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!isAdded() || !snapshot.exists()) return;

                    String saved = snapshot.getString("address");

                    if (!TextUtils.isEmpty(saved)) {
                        editAddress.setText(saved);
                    }

                    Double lat = snapshot.getDouble("addressLat");
                    Double lng = snapshot.getDouble("addressLng");

                    if (lat != null && lng != null && lat != 0 && lng != 0) {

                        selectedLat = lat;
                        selectedLng = lng;
                        hasSelection = true;

                        updateLatLngLabel();

                        if (googleMap != null) {
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(lat, lng), 16f));
                        }
                    }
                });
    }

    private void saveAddress() {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        String address = editAddress.getText() != null
                ? editAddress.getText().toString().trim() : "";

        if (TextUtils.isEmpty(address)) {
            Toast.makeText(requireContext(), "Please enter your address", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasSelection) {
            Toast.makeText(requireContext(),
                    "Please pick a point on the map or use your current location",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("address", address);
        updates.put("addressLat", selectedLat);
        updates.put("addressLng", selectedLng);

        FirebaseFirestore.getInstance()
                .collection("Users")
                .document("Passenger")
                .collection("Register")
                .document(uid)
                .set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    if (!isAdded()) return;

                    btnSave.setEnabled(true);

                    Toast.makeText(requireContext(), "Address saved", Toast.LENGTH_SHORT).show();

                    getParentFragmentManager().popBackStack();
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    btnSave.setEnabled(true);

                    Toast.makeText(requireContext(),
                            "Save failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}
