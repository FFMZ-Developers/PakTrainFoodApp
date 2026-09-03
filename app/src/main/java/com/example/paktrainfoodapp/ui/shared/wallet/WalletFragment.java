package com.example.paktrainfoodapp.ui.shared.wallet;

import android.content.Intent;
import android.net.Uri;
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
import com.example.paktrainfoodapp.data.WalletPaths;
import com.example.paktrainfoodapp.ui.main.Restaurant.profile.WalletHistory;
import com.example.paktrainfoodapp.ui.main.Restaurant.profile.WalletHistoryAdapter;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;



public class WalletFragment extends Fragment {

    public static final String ROLE_PASSENGER = "PASSENGER";
    public static final String ROLE_RESTAURANT = "RESTAURANT";
    public static final String ROLE_DELIVERY = "DELIVERY";

    private static final String ARG_ROLE = "wallet_role";

    public static WalletFragment newInstance(String role) {

        WalletFragment fragment = new WalletFragment();

        Bundle args = new Bundle();
        args.putString(ARG_ROLE, role);
        fragment.setArguments(args);

        return fragment;
    }

    private TextView txtWalletTitle, txtWalletHint;
    private TextView txtAvailableBalance, txtPendingBalance;

    private LinearLayout layoutBankDetails;
    private View cardBankAccount;
    private Button btnAddBank;
    private TextView txtNoHistory;
    // Module: self-service Stripe Connect - restaurant/rider does their
    // own bank onboarding directly with Stripe (not through the admin),
    // shown only for those two roles.
    private TextView txtStripeStatus, txtStripeInTransit;
    private Button btnConnectStripe, btnCheckStripeStatus;
    private String stripeAccountId = null;
    private boolean stripeOnboardingComplete = false;
    private RecyclerView recyclerHistory;

    private final ArrayList<WalletHistory> historyList = new ArrayList<>();
    private WalletHistoryAdapter adapter;

    private String uid;
    private double availableBalance = 0;


    private ListenerRegistration walletRegistration;
    private ListenerRegistration historyRegistration;

    private String role() {
        return getArguments() != null
                ? getArguments().getString(ARG_ROLE, ROLE_PASSENGER)
                : ROLE_PASSENGER;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_wallet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        txtWalletTitle = view.findViewById(R.id.txtWalletTitle);
        txtWalletHint = view.findViewById(R.id.txtWalletHint);
        txtAvailableBalance = view.findViewById(R.id.txtAvailableBalance);
        txtPendingBalance = view.findViewById(R.id.txtPendingBalance);
        txtNoHistory = view.findViewById(R.id.txtNoHistory);
        layoutBankDetails = view.findViewById(R.id.layoutBankDetails);

        // Module: "Withdraw"/"Transfer to bank" removed - a rider/
        // restaurant's payout is handled by the admin (Payments panel),
        // not self-service from here. Only "Change Bank Account" remains,
        // so admin always pays out to the correct, current account.
        View btnWithdrawLegacy = view.findViewById(R.id.btnWithdraw);
        if (btnWithdrawLegacy != null) btnWithdrawLegacy.setVisibility(View.GONE);

        recyclerHistory = view.findViewById(R.id.recyclerHistory);

        applyRoleWording();

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (isAdded()) getParentFragmentManager().popBackStack();
        });

        recyclerHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        // ✅ FIX: this RecyclerView lives inside the screen's outer
        // ScrollView (see fragment_wallet.xml) - without this, the two
        // scrolling views fight over touch events and only the first
        // couple of history items ever appeared scrollable. Disabling the
        // RecyclerView's own nested scrolling lets the outer ScrollView
        // smoothly scroll through the ENTIRE history list, no matter how
        // long it gets.
        recyclerHistory.setNestedScrollingEnabled(false);
        adapter = new WalletHistoryAdapter(getContext(), historyList);
        recyclerHistory.setAdapter(adapter);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Login required", Toast.LENGTH_SHORT).show();
            return;
        }

        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();



        // ✅ FIX: manual "Add/Change Bank Account" entry removed entirely.
        // Restaurant/Rider now connect their real bank via Stripe Connect
        // (below) - the money that actually moves comes from there, so a
        // manually-typed account number that Stripe never sees was pure
        // decoration. Passengers never had a payout bank to begin with
        // (they only ever pay by card and get refunded to that same card).

        View txtBankAccountTitle = view.findViewById(R.id.txtBankAccountTitle);
        cardBankAccount = view.findViewById(R.id.cardBankAccount);

        if (txtBankAccountTitle != null) txtBankAccountTitle.setVisibility(View.GONE);
        if (cardBankAccount != null) cardBankAccount.setVisibility(View.GONE);

        layoutBankDetails.setVisibility(View.GONE);


        if (ROLE_PASSENGER.equals(role())) {

            // Module - a passenger doesn't have a spendable "balance" -
            // their money either sits held on their card, or gets
            // refunded straight back to it. This screen is purely a
            // receipt/history log for them now.
            View balanceCards = view.findViewById(R.id.layoutBalanceCards);
            if (balanceCards != null) balanceCards.setVisibility(View.GONE);

        } else if (ROLE_RESTAURANT.equals(role()) || ROLE_DELIVERY.equals(role())) {
            addStripeConnectUi(view);
        }

        listenWallet();
        listenHistory();
    }

    // =========================================================
    // Module: self-service Stripe Connect.
    //
    // Restaurant/rider does their own bank onboarding directly with
    // Stripe - the admin never sees or handles their bank details. Built
    // programmatically (this layout is shared across all 3 roles) and
    // inserted right below the bank-details section, since it's the same
    // "how do I get paid" concept, one step further.
    // =========================================================

    private void addStripeConnectUi(@NonNull View root) {

        // ✅ FIX: the Stripe section used to be inserted INSIDE the old
        // "Bank Account" card (layoutBankDetails.getParent()) - once that
        // whole card got hidden (View.GONE) to remove the confusing
        // leftover "Add Bank Account" button, everything nested inside it
        // - including this Stripe section - vanished too, since GONE
        // hides an entire subtree regardless of the children's own
        // visibility. Inserting as a SIBLING of the (hidden) card instead
        // - in the same parent it already sits in - keeps the Stripe
        // section fully independent and always visible.
        ViewGroup container = (ViewGroup) cardBankAccount.getParent();
        int insertIndex = container.indexOfChild(cardBankAccount) + 1;

        if (container != null) {

            // Right after the balance/hint block, where the old "Bank
            // Account" section used to visually sit.
            insertIndex = Math.min(2, container.getChildCount());

        } else {

            container = (ViewGroup) layoutBankDetails.getParent();
            insertIndex = container.indexOfChild(layoutBankDetails) + 1;
        }

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (12 * density);

        LinearLayout section = new LinearLayout(requireContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = (int) (16 * density);
        section.setLayoutParams(sectionParams);
        section.setBackgroundResource(R.drawable.edittext_bg);

        TextView label = new TextView(requireContext());
        label.setText("Payout Method (Stripe)");
        label.setTextColor(0xFF757575);
        label.setTextSize(13);
        section.addView(label);

        txtStripeStatus = new TextView(requireContext());
        txtStripeStatus.setTextSize(15);
        txtStripeStatus.setPadding(0, (int) (6 * density), 0, (int) (10 * density));
        txtStripeStatus.setText("Checking status...");
        section.addView(txtStripeStatus);

        LinearLayout buttonRow = new LinearLayout(requireContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        btnConnectStripe = new Button(requireContext());
        btnConnectStripe.setText("Setup Payments");
        btnConnectStripe.setAllCaps(false);
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        connectParams.rightMargin = (int) (6 * density);
        btnConnectStripe.setLayoutParams(connectParams);
        btnConnectStripe.setOnClickListener(v -> startStripeOnboarding());
        buttonRow.addView(btnConnectStripe);

        btnCheckStripeStatus = new Button(requireContext());
        btnCheckStripeStatus.setText("Check Status");
        btnCheckStripeStatus.setAllCaps(false);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        checkParams.leftMargin = (int) (6 * density);
        btnCheckStripeStatus.setLayoutParams(checkParams);
        btnCheckStripeStatus.setVisibility(View.GONE);
        btnCheckStripeStatus.setOnClickListener(v -> checkStripeStatus());
        buttonRow.addView(btnCheckStripeStatus);

        section.addView(buttonRow);

        txtStripeInTransit = new TextView(requireContext());
        txtStripeInTransit.setTextSize(13);
        txtStripeInTransit.setTextColor(0xFF1565C0);
        txtStripeInTransit.setPadding(0, (int) (10 * density), 0, 0);
        txtStripeInTransit.setVisibility(View.GONE);
        section.addView(txtStripeInTransit);

        container.addView(section, insertIndex);

        loadStripeStatus();
    }

    /** Reads stripeAccountId/stripeOnboardingComplete from the profile doc (VerifiedRegister - the same source loadBankAccount() reads). */
    private void loadStripeStatus() {

        DocumentReferenceForRole().get()
                .addOnSuccessListener(snap -> {

                    if (!isAdded() || txtStripeStatus == null) return;

                    if (snap.exists()) {
                        stripeAccountId = snap.getString("stripeAccountId");
                        Boolean complete = snap.getBoolean("stripeOnboardingComplete");
                        stripeOnboardingComplete = complete != null && complete;
                    }

                    updateStripeStatusUi();
                })
                .addOnFailureListener(e -> {
                    if (isAdded() && txtStripeStatus != null) {
                        txtStripeStatus.setText("Could not check Stripe status.");
                    }
                });
    }

    private void updateStripeStatusUi() {

        if (txtStripeStatus == null) return;

        boolean hasAccount = stripeAccountId != null && stripeAccountId.startsWith("acct_");

        if (hasAccount && stripeOnboardingComplete) {
            txtStripeStatus.setText("✅ Connected - payments go straight to your bank.");
            btnConnectStripe.setText("Reconnect");
            btnCheckStripeStatus.setVisibility(View.GONE);

            // Module - shows "In Transit to Bank: Rs X" whenever Stripe
            // is holding money for this account that hasn't reached the
            // real bank yet (Stripe sweeps it automatically on its own
            // schedule - this is a read-only status, not an action).
            loadConnectedAccountBalance();

        } else if (hasAccount) {
            txtStripeStatus.setText("⏳ Setup started - finish it to receive payments.");
            btnConnectStripe.setText("Continue Setup");
            btnCheckStripeStatus.setVisibility(View.VISIBLE);
        } else {
            txtStripeStatus.setText("⚠️ Not set up yet - tap below to receive payments directly to your bank.");
            btnConnectStripe.setText("Setup Payments");
            btnCheckStripeStatus.setVisibility(View.GONE);
        }
    }

    /**
     * Reads the CONNECTED account's own Stripe balance (not the admin's) -
     * shows an "In Transit to Bank" line whenever Stripe is holding money
     * for this restaurant/rider that hasn't reached their real bank yet.
     * Purely informational - Stripe moves it on its own schedule, this
     * screen doesn't trigger anything.
     */
    private void loadConnectedAccountBalance() {

        if (!isAdded() || stripeAccountId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("stripeAccountId", stripeAccountId);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("getConnectedAccountBalance")
                .call(data)
                .addOnSuccessListener(result -> {

                    if (!isAdded() || txtStripeInTransit == null) return;

                    Map<?, ?> response = (Map<?, ?>) result.getData();
                    if (response == null) return;

                    Object pendingObj = response.get("pendingUsd");
                    Object availableObj = response.get("availableUsd");

                    double pendingUsd = pendingObj instanceof Number ? ((Number) pendingObj).doubleValue() : 0;
                    double availableUsd = availableObj instanceof Number ? ((Number) availableObj).doubleValue() : 0;

                    if (pendingUsd > 0 || availableUsd > 0) {

                        txtStripeInTransit.setVisibility(View.VISIBLE);

                        StringBuilder sb = new StringBuilder();

                        if (availableUsd > 0) {
                            sb.append(String.format(java.util.Locale.US,
                                    "💵 In Stripe, being sent to your bank: $%.2f", availableUsd));
                        }

                        if (pendingUsd > 0) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(String.format(java.util.Locale.US,
                                    "⏳ Still settling in Stripe: $%.2f", pendingUsd));
                        }

                        txtStripeInTransit.setText(sb.toString());

                    } else {
                        txtStripeInTransit.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    if (txtStripeInTransit != null) txtStripeInTransit.setVisibility(View.GONE);
                });
    }

    /**
     * Opens a URL with a forced app chooser (Intent.createChooser) instead
     * of plain implicit ACTION_VIEW - avoids Android's default-app
     * resolution picking a broken/non-exported handler on some devices
     * (see the FIX note where this is called). Falls back to a copyable
     * dialog if even that fails.
     */
    private void openOnboardingUrl(String url) {

        if (!isAdded()) return;

        Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // ✅ FIX: check BEFORE launching whether anything can actually
        // handle a web link at all. On some devices/emulators there's no
        // browser installed - in that case Intent.createChooser() still
        // "succeeds" (it launches Android's own chooser component), but
        // the chooser itself then shows "No apps can perform this
        // action." with no way forward - no exception is thrown back to
        // this code, so the try/catch fallback never fired. Checking
        // resolution up front means we go straight to the copyable-link
        // dialog instead of showing that dead end.
        boolean canHandle = !requireContext().getPackageManager()
                .queryIntentActivities(viewIntent, 0).isEmpty();

        if (!canHandle) {
            showOnboardingUrlFallback(url);
            return;
        }

        try {

            Intent chooser = Intent.createChooser(viewIntent, "Open with");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(chooser);

        } catch (Exception e) {

            // Covers SecurityException (broken non-exported handler on
            // some devices) and ActivityNotFoundException - either way,
            // don't crash, just let the user copy the link and open it
            // manually.
            showOnboardingUrlFallback(url);
        }
    }

    private void showOnboardingUrlFallback(String url) {

        if (!isAdded()) return;

        TextView linkView = new TextView(requireContext());
        linkView.setText(url);
        linkView.setTextIsSelectable(true);
        linkView.setPadding((int) (16 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density));

        new AlertDialog.Builder(requireContext())
                .setTitle("Couldn't open automatically")
                .setMessage("Copy this link and open it in your browser to finish setting up payments:")
                .setView(linkView)
                .setPositiveButton("Copy Link", (d, w) -> {

                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) requireContext()
                                    .getSystemService(android.content.Context.CLIPBOARD_SERVICE);

                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Stripe Onboarding Link", url));
                        Toast.makeText(requireContext(), "Link copied", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void startStripeOnboarding() {

        if (!isAdded()) return;

        String email = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(requireContext(), "No email on file - can't set up payments.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnConnectStripe.setEnabled(false);
        txtStripeStatus.setText("Setting up...");

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("uid", uid);
        data.put("type", ROLE_RESTAURANT.equals(role()) ? "restaurant" : "rider");

        FirebaseFunctions.getInstance()
                .getHttpsCallable("createConnectedAccount")
                .call(data)
                .addOnSuccessListener(result -> {

                    if (!isAdded()) return;

                    btnConnectStripe.setEnabled(true);

                    Map<?, ?> response = (Map<?, ?>) result.getData();
                    Object onboardingUrl = response != null ? response.get("onboardingUrl") : null;

                    if (onboardingUrl == null) {
                        txtStripeStatus.setText("Setup failed - please try again.");
                        return;
                    }

                    // Opens Stripe's own secure onboarding page - bank
                    // details are entered there directly with Stripe, never
                    // seen by this app or the admin.
                    // ✅ FIX: plain implicit ACTION_VIEW crashed with a
                    // SecurityException on some devices - a third-party
                    // payment app (seen: "OPay Pakistan") registers itself
                    // as a handler for connect.stripe.com links but its
                    // target Activity isn't exported, so Android's default-
                    // app resolution picks it and then can't actually
                    // launch it. Forcing Intent.createChooser() bypasses
                    // that default-app resolution (always shows the
                    // picker), and everything is wrapped in a try/catch so
                    // a genuinely broken device state shows the link
                    // instead of crashing the app.
                    openOnboardingUrl(onboardingUrl.toString());

                    txtStripeStatus.setText("Complete the form that just opened, then come back and tap \"Check Status\".");
                    btnCheckStripeStatus.setVisibility(View.VISIBLE);

                    loadStripeStatus();
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    btnConnectStripe.setEnabled(true);
                    txtStripeStatus.setText("Setup failed: " + e.getMessage());
                });
    }

    private void checkStripeStatus() {

        if (!isAdded() || stripeAccountId == null) return;

        btnCheckStripeStatus.setEnabled(false);
        txtStripeStatus.setText("Checking...");

        Map<String, Object> data = new HashMap<>();
        data.put("stripeAccountId", stripeAccountId);
        data.put("uid", uid);
        data.put("type", ROLE_RESTAURANT.equals(role()) ? "restaurant" : "rider");

        FirebaseFunctions.getInstance()
                .getHttpsCallable("checkStripeAccountStatus")
                .call(data)
                .addOnSuccessListener(result -> {

                    if (!isAdded()) return;

                    btnCheckStripeStatus.setEnabled(true);

                    Map<?, ?> response = (Map<?, ?>) result.getData();
                    Object isComplete = response != null ? response.get("isComplete") : null;

                    stripeOnboardingComplete = Boolean.TRUE.equals(isComplete);

                    updateStripeStatusUi();

                    Toast.makeText(requireContext(),
                            stripeOnboardingComplete
                                    ? "You're all set - payments will come straight to your bank."
                                    : "Not finished yet - complete the Stripe form first.",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    btnCheckStripeStatus.setEnabled(true);
                    txtStripeStatus.setText("Check failed: " + e.getMessage());
                });
    }

    /** Only the copy differs per role - the data model is identical. */
    private void applyRoleWording() {

        switch (role()) {

            case ROLE_RESTAURANT:
                txtWalletTitle.setText("Restaurant Wallet");
                txtWalletHint.setText(
                        "Earnings from completed orders appear here, then transfer to your bank.");
                break;

            case ROLE_DELIVERY:
                txtWalletTitle.setText("Rider Wallet");
                txtWalletHint.setText(
                        "Your delivery earnings appear here, then transfer to your bank.");
                break;

            default:
                txtWalletTitle.setText("My Wallet");
                txtWalletHint.setText(
                        "Refunds from cancelled orders appear here, then transfer to your bank.");
        }
    }

    // =========================================================
    // BALANCE + HISTORY
    // =========================================================

    private void listenWallet() {

        walletRegistration = WalletPaths.wallet(WalletPaths.roleFolder(role()), uid)
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

        // Newest transaction first - the most recent one is what anyone
        // opening this screen is actually looking for.
        historyRegistration = WalletPaths.history(WalletPaths.roleFolder(role()), uid)
                .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {

                    if (!isAdded()) return;

                    // ✅ FIX: this used to swallow every error silently, so an
                    // empty history was indistinguishable from a failed read -
                    // there was no way to tell whether the transactions genuinely
                    // weren't there or the query had been rejected/failed.
                    if (error != null) {

                        android.util.Log.e("WalletFragment",
                                "History listener failed for " + WalletPaths.roleFolder(role())
                                        + "/" + uid, error);

                        txtNoHistory.setVisibility(View.VISIBLE);
                        txtNoHistory.setText("Couldn't load transactions: " + error.getMessage());
                        return;
                    }

                    if (snapshot == null) return;

                    android.util.Log.d("WalletFragment",
                            "History snapshot: " + snapshot.size() + " entries at Wallets/"
                                    + WalletPaths.roleFolder(role()) + "/Accounts/" + uid + "/history");

                    java.util.List<WalletHistory> freshList = new java.util.ArrayList<>();
                    java.util.List<com.google.android.gms.tasks.Task<DocumentSnapshot>> orderLookups =
                            new java.util.ArrayList<>();
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        Double amount = doc.getDouble("amount");
                        if (amount == null) amount = 0.0;

                        String type = doc.getString("type");
                        String orderId = doc.getString("orderId");
                        String date = doc.getString("date");

                        WalletHistory entry = new WalletHistory(
                                type != null ? type : "Transaction",
                                String.valueOf((int) amount.doubleValue()),
                                date != null ? date : "",
                                orderId != null ? orderId : "-");

                        freshList.add(entry);

                        // orderId here is the Firestore document id of the order, not
                        // the sequential number a person can read - look that up so
                        // the row can show "Order #0001" instead of the raw id.
                        orderLookups.add(orderId != null
                                ? db.collection("Orders").document(orderId).get()
                                : com.google.android.gms.tasks.Tasks.forResult(null));
                    }

                    com.google.android.gms.tasks.Tasks.whenAllComplete(orderLookups)
                            .addOnCompleteListener(ignored -> {

                                if (!isAdded()) return;

                                for (int i = 0; i < freshList.size(); i++) {

                                    com.google.android.gms.tasks.Task<DocumentSnapshot> task =
                                            orderLookups.get(i);

                                    if (!task.isSuccessful() || task.getResult() == null) continue;

                                    Long orderNumber = task.getResult().getLong("orderNumber");
                                    if (orderNumber == null) continue;

                                    WalletHistory old = freshList.get(i);
                                    freshList.set(i, new WalletHistory(
                                            old.getType(),
                                            old.getAmount(),
                                            old.getDate(),
                                            old.getOrderId(),
                                            orderNumber));
                                }

                                historyList.clear();
                                historyList.addAll(freshList);
                                adapter.notifyDataSetChanged();

                                txtNoHistory.setVisibility(
                                        historyList.isEmpty() ? View.VISIBLE : View.GONE);
                            });
                });
    }



    /**
     * Passengers don't go through the verification wizard, so they only
     * ever have a Wallets-doc bank entry (from this screen's Add Bank
     * dialog) - restaurants/riders' real source of truth is VerifiedRegister.
     */
    private DocumentReference DocumentReferenceForRole() {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        switch (role()) {

            case ROLE_RESTAURANT:
                return db.collection("Users").document("Restaurant")
                        .collection("VerifiedRegister").document(uid);

            case ROLE_DELIVERY:
                return db.collection("Users").document("Delivery")
                        .collection("VerifiedRegister").document(uid);

            default:
                return WalletPaths.wallet(WalletPaths.roleFolder(role()), uid);
        }
    }





    // =========================================================
    // ✅ FIX: self-service "Withdraw"/"Transfer to bank" removed - see
    // the comment above where btnWithdraw is hidden in onViewCreated.
    // =========================================================

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (walletRegistration != null) walletRegistration.remove();
        if (historyRegistration != null) historyRegistration.remove();
    }
}
