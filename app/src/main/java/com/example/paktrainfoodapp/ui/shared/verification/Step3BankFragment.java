package com.example.paktrainfoodapp.ui.shared.verification;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.paktrainfoodapp.R;
import com.google.android.material.textfield.TextInputEditText;

public class Step3BankFragment extends Fragment {

    private TextInputEditText editBankName, editAccountHolder, editAccountNumber;
    private VerificationViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_verification_step3, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        editBankName = view.findViewById(R.id.edit_bank_name);
        editAccountHolder = view.findViewById(R.id.edit_account_holder);
        editAccountNumber = view.findViewById(R.id.edit_account_number);

        viewModel = new ViewModelProvider(requireActivity()).get(VerificationViewModel.class);

        if (viewModel.getBankName() != null) editBankName.setText(viewModel.getBankName());
        if (viewModel.getBankAccountHolder() != null) editAccountHolder.setText(viewModel.getBankAccountHolder());
        if (viewModel.getBankAccountNumber() != null) editAccountNumber.setText(viewModel.getBankAccountNumber());

        view.findViewById(R.id.btn_step3_next).setOnClickListener(v -> validateAndContinue());
    }

    private void validateAndContinue() {

        String bank = editBankName.getText() != null ? editBankName.getText().toString().trim() : "";
        String holder = editAccountHolder.getText() != null ? editAccountHolder.getText().toString().trim() : "";
        String number = editAccountNumber.getText() != null ? editAccountNumber.getText().toString().trim() : "";

        if (TextUtils.isEmpty(bank)) {
            editBankName.setError("Enter your bank name");
            return;
        }

        if (TextUtils.isEmpty(holder)) {
            editAccountHolder.setError("Enter the account holder name");
            return;
        }

        if (number.length() < 6) {
            editAccountNumber.setError("Enter a valid account number");
            return;
        }

        viewModel.setBankName(bank);
        viewModel.setBankAccountHolder(holder);
        viewModel.setBankAccountNumber(number);

        if (requireActivity() instanceof VerificationWizardActivity) {
            ((VerificationWizardActivity) requireActivity())
                    .showStep(new Step4SelfieFragment(), 4, "Selfie & Submit");
        }
    }
}
