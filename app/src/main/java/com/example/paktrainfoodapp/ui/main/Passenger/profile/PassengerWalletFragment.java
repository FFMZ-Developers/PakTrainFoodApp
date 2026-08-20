package com.example.paktrainfoodapp.ui.main.Passenger.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Restaurant.profile.WalletHistory;
import com.example.paktrainfoodapp.ui.main.Restaurant.profile.WalletHistoryAdapter;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Passenger wallet: shows refunds from cancelled orders, lets the passenger
 * save a bank account, and transfer the available balance to it.
 *
 * The transfer is simulated for this project - it moves the amount out of the
 * wallet and records a "Withdrawal" history entry rather than calling a real
 * banking API.
 */
public class PassengerWalletFragment extends Fragment {

    private TextView txtAvailableBalance, txtPendingBalance;
    private TextView txtBankName, txtBankAccount, txtBankHolder, txtNoBank, txtNoHistory;
    private LinearLayout layoutBankDetails;
    private Button btnAddBank, btnWithdraw;
    private RecyclerView recyclerHistory;

    private final ArrayList<WalletHistory> historyList = new ArrayList<>();
    private WalletHistoryAdapter adapter;

    private String uid;
    private double availableBalance = 0;
    private boolean hasBankAccount = false;

    private ListenerRegistration walletRegistration;
    private ListenerRegistration historyRegistration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_passenger_wallet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtAvailableBalance = view.findViewById(R.id.txtAvailableBalance);
        txtPendingBalance = view.findViewById(R.id.txtPendingBalance);
        txtBankName = view.findViewById(R.id.txtBankName);
        txtBankAccount = view.findViewById(R.id.txtBankAccount);
        txtBankHolder = view.findViewById(R.id.txtBankHolder);
        txtNoBank = view.findViewById(R.id.txtNoBank);
        txtNoHistory = view.findViewById(R.id.txtNoHistory);
        layoutBankDetails = view.findViewById(R.id.layoutBankDetails);
        btnAddBank = view.findViewById(R.id.btnAddBank);
        btnWithdraw = view.findViewById(R.id.btnWithdraw);
        recyclerHistory = view.findViewById(R.id.recyclerHistory);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (isAdded()) getParentFragmentManager().popBackStack();
        });

        recyclerHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new WalletHistoryAdapter(getContext(), historyList);
        recyclerHistory.setAdapter(adapter);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Login required", Toast.LENGTH_SHORT).show();
            return;
        }

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        btnAddBank.setOnClickListener(v -> showAddBankDialog());
        btnWithdraw.setOnClickListener(v -> confirmWithdraw());

        listenWallet();
        listenHistory();
        loadBankAccount();
    }

    // =========================================================
    // BALANCE
    // =========================================================

    private void listenWallet() {

        walletRegistration = FirebaseFirestore.getInstance()
                .collection("Wallets")
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (!isAdded()) return;

                    if (error != null || snapshot == null || !snapshot.exists()) {
                        availableBalance = 0;
                        txtAvailableBalance.setText("Rs 0");
                        txtPendingBalance.setText("Rs 0");
                        return;
                    }

                    Double available = snapshot.getDouble("availableBalance");
                    Double pending = snapshot.getDouble("pendingBalance");

                    if (available == null) available = 0.0;
                    if (pending == null) pending = 0.0;

                    availableBalance = available;

                    txtAvailableBalance.setText("Rs " + (int) available.doubleValue());
                    txtPendingBalance.setText("Rs " + (int) pending.doubleValue());
                });
    }

    private void listenHistory() {

        historyRegistration = FirebaseFirestore.getInstance()
                .collection("Wallets")
                .document(uid)
                .collection("history")
                .addSnapshotListener((snapshot, error) -> {

                    if (!isAdded() || error != null || snapshot == null) return;

                    historyList.clear();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        String type = doc.getString("type");
                        String orderId = doc.getString("orderId");
                        String date = doc.getString("date");

                        Double amount = doc.getDouble("amount");
                        if (amount == null) amount = 0.0;

                        historyList.add(new WalletHistory(
                                type != null ? type : "Transaction",
                                String.valueOf((int) amount.doubleValue()),
                                date != null ? date : "",
                                orderId != null ? orderId : "-"));
                    }

                    adapter.notifyDataSetChanged();

                    txtNoHistory.setVisibility(historyList.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    // =========================================================
    // BANK ACCOUNT
    // =========================================================

    private void loadBankAccount() {

        FirebaseFirestore.getInstance()
                .collection("Wallets")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!isAdded() || !snapshot.exists()) {
                        showNoBank();
                        return;
                    }

                    String bankName = snapshot.getString("bankName");
                    String accountNumber = snapshot.getString("bankAccountNumber");
                    String holder = snapshot.getString("bankAccountHolder");

                    if (TextUtils.isEmpty(bankName) || TextUtils.isEmpty(accountNumber)) {
                        showNoBank();
                        return;
                    }

                    hasBankAccount = true;

                    layoutBankDetails.setVisibility(View.VISIBLE);
                    txtNoBank.setVisibility(View.GONE);

                    txtBankName.setText(bankName);
                    txtBankAccount.setText(maskAccount(accountNumber));
                    txtBankHolder.setText(holder != null ? holder : "");

                    btnAddBank.setText("Change Bank Account");
                })
                .addOnFailureListener(e -> showNoBank());
    }

    private void showNoBank() {

        if (!isAdded()) return;

        hasBankAccount = false;
        layoutBankDetails.setVisibility(View.GONE);
        txtNoBank.setVisibility(View.VISIBLE);
        btnAddBank.setText("Add Bank Account");
    }

    /** Shows only the last 4 digits so the full number isn't left on screen. */
    private String maskAccount(String accountNumber) {

        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;

        return "•••• •••• " + accountNumber.substring(accountNumber.length() - 4);
    }

    private void showAddBankDialog() {

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_bank, null);

        TextInputEditText editBank = dialogView.findViewById(R.id.edit_bank_name);
        TextInputEditText editHolder = dialogView.findViewById(R.id.edit_account_holder);
        TextInputEditText editNumber = dialogView.findViewById(R.id.edit_account_number);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(hasBankAccount ? "Change Bank Account" : "Add Bank Account")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {

            String bank = editBank.getText() != null ? editBank.getText().toString().trim() : "";
            String holder = editHolder.getText() != null ? editHolder.getText().toString().trim() : "";
            String number = editNumber.getText() != null ? editNumber.getText().toString().trim() : "";

            if (TextUtils.isEmpty(bank)) {
                editBank.setError("Enter bank name");
                return;
            }

            if (TextUtils.isEmpty(holder)) {
                editHolder.setError("Enter account holder name");
                return;
            }

            if (number.length() < 6) {
                editNumber.setError("Enter a valid account number");
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("bankName", bank);
            data.put("bankAccountHolder", holder);
            data.put("bankAccountNumber", number);

            FirebaseFirestore.getInstance()
                    .collection("Wallets")
                    .document(uid)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(unused -> {

                        if (!isAdded()) return;

                        dialog.dismiss();

                        Toast.makeText(requireContext(), "Bank account saved", Toast.LENGTH_SHORT).show();

                        loadBankAccount();
                    })
                    .addOnFailureListener(e -> {

                        if (!isAdded()) return;

                        Toast.makeText(requireContext(),
                                "Save failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        });
    }

    // =========================================================
    // WITHDRAW (simulated)
    // =========================================================

    private void confirmWithdraw() {

        if (!hasBankAccount) {
            Toast.makeText(requireContext(),
                    "Please add a bank account first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (availableBalance <= 0) {
            Toast.makeText(requireContext(), "No balance to transfer", Toast.LENGTH_SHORT).show();
            return;
        }

        final double amount = availableBalance;

        new AlertDialog.Builder(requireContext())
                .setTitle("Transfer to bank?")
                .setMessage("Rs " + (int) amount + " will be transferred to your saved bank account.")
                .setPositiveButton("Transfer", (d, w) -> performWithdraw(amount))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performWithdraw(double amount) {

        btnWithdraw.setEnabled(false);

        String date = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date());

        Map<String, Object> historyEntry = new HashMap<>();
        historyEntry.put("type", "Withdrawal");
        historyEntry.put("amount", amount);
        historyEntry.put("date", date);
        historyEntry.put("orderId", "BANK-TRANSFER");

        FirebaseFirestore.getInstance()
                .collection("Wallets")
                .document(uid)
                .collection("history")
                .add(historyEntry)
                .addOnSuccessListener(ref ->
                        FirebaseFirestore.getInstance()
                                .collection("Wallets")
                                .document(uid)
                                .update("availableBalance", FieldValue.increment(-amount))
                                .addOnSuccessListener(unused -> {

                                    if (!isAdded()) return;

                                    btnWithdraw.setEnabled(true);

                                    Toast.makeText(requireContext(),
                                            "Rs " + (int) amount + " transferred to your bank",
                                            Toast.LENGTH_LONG).show();
                                })
                                .addOnFailureListener(e -> {

                                    if (!isAdded()) return;

                                    btnWithdraw.setEnabled(true);

                                    Toast.makeText(requireContext(),
                                            "Transfer failed: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }))
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    btnWithdraw.setEnabled(true);

                    Toast.makeText(requireContext(),
                            "Transfer failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (walletRegistration != null) walletRegistration.remove();
        if (historyRegistration != null) historyRegistration.remove();
    }
}
