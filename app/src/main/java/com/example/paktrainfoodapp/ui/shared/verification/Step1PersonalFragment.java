package com.example.paktrainfoodapp.ui.shared.verification;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.google.android.material.textfield.TextInputEditText;

public class Step1PersonalFragment extends Fragment {

    private TextInputEditText editName, editPhone;
    private ImageView imgCnicFront, imgCnicBack;
    private TextView txtCnicFrontHint, txtCnicBackHint;

    private VerificationViewModel viewModel;

    private ActivityResultLauncher<String> cnicFrontPicker;
    private ActivityResultLauncher<String> cnicBackPicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cnicFrontPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    viewModel.setCnicFrontUri(uri);
                    Glide.with(this).load(uri).into(imgCnicFront);
                    txtCnicFrontHint.setVisibility(View.GONE);
                });

        cnicBackPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    viewModel.setCnicBackUri(uri);
                    Glide.with(this).load(uri).into(imgCnicBack);
                    txtCnicBackHint.setVisibility(View.GONE);
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_verification_step1, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        editName = view.findViewById(R.id.edit_full_name);
        editPhone = view.findViewById(R.id.edit_phone);
        imgCnicFront = view.findViewById(R.id.img_cnic_front);
        imgCnicBack = view.findViewById(R.id.img_cnic_back);
        txtCnicFrontHint = view.findViewById(R.id.txt_cnic_front_hint);
        txtCnicBackHint = view.findViewById(R.id.txt_cnic_back_hint);

        viewModel = new ViewModelProvider(requireActivity()).get(VerificationViewModel.class);

        // Restore anything already entered (coming back from step 2, or a resubmit prefill)
        if (viewModel.getFullName() != null) editName.setText(viewModel.getFullName());
        if (viewModel.getPhone() != null) editPhone.setText(viewModel.getPhone());

        if (viewModel.getCnicFrontUri() != null) {
            Glide.with(this).load(viewModel.getCnicFrontUri()).into(imgCnicFront);
            txtCnicFrontHint.setVisibility(View.GONE);
        } else if (!TextUtils.isEmpty(viewModel.getExistingCnicFrontUrl())) {
            Glide.with(this).load(viewModel.getExistingCnicFrontUrl()).into(imgCnicFront);
            txtCnicFrontHint.setVisibility(View.GONE);
        }

        if (viewModel.getCnicBackUri() != null) {
            Glide.with(this).load(viewModel.getCnicBackUri()).into(imgCnicBack);
            txtCnicBackHint.setVisibility(View.GONE);
        } else if (!TextUtils.isEmpty(viewModel.getExistingCnicBackUrl())) {
            Glide.with(this).load(viewModel.getExistingCnicBackUrl()).into(imgCnicBack);
            txtCnicBackHint.setVisibility(View.GONE);
        }

        view.findViewById(R.id.card_cnic_front).setOnClickListener(v -> cnicFrontPicker.launch("image/*"));
        view.findViewById(R.id.card_cnic_back).setOnClickListener(v -> cnicBackPicker.launch("image/*"));

        view.findViewById(R.id.btn_step1_next).setOnClickListener(v -> validateAndContinue());
    }

    private void validateAndContinue() {

        String name = editName.getText() != null ? editName.getText().toString().trim() : "";
        String phone = editPhone.getText() != null ? editPhone.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            editName.setError("Enter your full name");
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            editPhone.setError("Enter your phone number");
            return;
        }

        boolean hasFront = viewModel.getCnicFrontUri() != null || !TextUtils.isEmpty(viewModel.getExistingCnicFrontUrl());
        boolean hasBack = viewModel.getCnicBackUri() != null || !TextUtils.isEmpty(viewModel.getExistingCnicBackUrl());

        if (!hasFront || !hasBack) {
            Toast.makeText(requireContext(), "Please add both sides of your CNIC", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.setFullName(name);
        viewModel.setPhone(phone);

        if (requireActivity() instanceof VerificationWizardActivity) {

            String stepTitle = VerificationViewModel.ROLE_RESTAURANT.equals(viewModel.getRole())
                    ? "Restaurant Details" : "Vehicle Details";

            ((VerificationWizardActivity) requireActivity())
                    .showStep(new Step2RoleDetailsFragment(), 2, stepTitle);
        }
    }
}
