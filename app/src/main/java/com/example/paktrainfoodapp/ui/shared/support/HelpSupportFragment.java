package com.example.paktrainfoodapp.ui.shared.support;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;

/**
 * Module: Help & Support - one shared screen for Restaurant and Rider
 * (Passenger has its own equivalent support entry point already). Kept
 * simple on purpose: a handful of common questions, a way to rate the app,
 * and a way to reach support - not a full ticketing system.
 */
public class HelpSupportFragment extends Fragment {

    private static final String PLAY_STORE_PACKAGE = "com.fahad.paktrainfoodservice";

    private static final String ARG_ROLE = "help_role";

    public static final String ROLE_RESTAURANT = "RESTAURANT";
    public static final String ROLE_DELIVERY = "DELIVERY";
    public static final String ROLE_PASSENGER = "PASSENGER";

    public static HelpSupportFragment newInstance(String role) {

        HelpSupportFragment f = new HelpSupportFragment();

        Bundle b = new Bundle();
        b.putString(ARG_ROLE, role);
        f.setArguments(b);

        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_help_support, container, false);

        ImageView btnBack = v.findViewById(R.id.btnHelpBack);
        btnBack.setOnClickListener(view -> {

            if (!isAdded()) return;

            // ✅ FIX: this popped requireActivity()'s FragmentManager, but
            // this screen is added via the ROLE SHELL's own child manager
            // (getParentFragmentManager() in the profile screens' wireRow)
            // - so the pop hit the wrong back stack and the arrow did
            // nothing at all. Popping the manager this fragment actually
            // belongs to makes it work from every entry point.
            getParentFragmentManager().popBackStack();
        });

        // Rate the app - the same Play Store listing every "share" flow
        // links to (see ShareUtils).
        LinearLayout rowRate = v.findViewById(R.id.rowRateApp);
        rowRate.setOnClickListener(view -> openPlayStore());

        LinearLayout rowContact = v.findViewById(R.id.rowContactSupport);
        rowContact.setOnClickListener(view -> {

            try {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:supportpaktrainfood@gmail.com"));
                intent.putExtra(Intent.EXTRA_SUBJECT, "PakTrainFood Support Request");
                startActivity(intent);
            } catch (Exception ignored) {
                // No mail app - the FAQ below still covers the common
                // cases, so this isn't a dead end even if it fails.
            }
        });

        buildFaq(v);

        return v;
    }

    /**
     * ✅ FIX: the passenger side used to have its own, much longer
     * support screen (passenger_helpandsupport) with a large list of
     * unrelated options. It now opens this SAME simple screen every
     * other role uses - only the FAQ questions below actually change per
     * role, everything else (Rate app, Contact support) is identical.
     */
    private void buildFaq(View v) {

        LinearLayout container = v.findViewById(R.id.layoutFaqContent);

        if (container == null) return;

        container.removeAllViews();

        String role = getArguments() != null ? getArguments().getString(ARG_ROLE) : null;

        String[][] faqs = ROLE_PASSENGER.equals(role) ? passengerFaqs() : partnerFaqs();

        for (int i = 0; i < faqs.length; i++) {
            addFaqRow(container, faqs[i][0], faqs[i][1], i < faqs.length - 1);
        }
    }

    private String[][] partnerFaqs() {
        return new String[][]{
                {"When do I get paid?",
                        "Your earnings move to your Available balance once an order is completed, and are transferred to your bank automatically every 24 hours once Stripe is connected (see your Wallet screen)."},
                {"How do I set up payments?",
                        "Go to Wallet \u2192 Setup Payments, and follow Stripe's onboarding form."},
                {"An order has a problem - what do I do?",
                        "Open the order and use \"Report a Problem\" - our team reviews every report and resolves payment fairly for everyone involved."}
        };
    }

    private String[][] passengerFaqs() {
        return new String[][]{
                {"How do I track my order?",
                        "Open the order from Active/Accepted/Delivered in your Orders tab - you'll see the live map once a rider is on the way."},
                {"Can I cancel an order?",
                        "Yes, from the Active tab, as long as the restaurant hasn't accepted it yet. Once accepted, contact support to cancel."},
                {"My order didn't arrive - what now?",
                        "Use \"Report a Problem\" on the order, or contact support below - we review every report and refund fairly where it's due."},
                {"How do refunds work?",
                        "A cancelled or disputed order is refunded straight back to the card you paid with - check your Wallet's history for the receipt."}
        };
    }

    private void addFaqRow(LinearLayout container, String question, String answer, boolean addSpacing) {

        TextView txtQ = new TextView(requireContext());
        txtQ.setText(question);
        txtQ.setTextSize(14);
        txtQ.setTypeface(txtQ.getTypeface(), android.graphics.Typeface.BOLD);
        container.addView(txtQ);

        TextView txtA = new TextView(requireContext());
        txtA.setText(answer);
        txtA.setTextSize(13);
        txtA.setTextColor(0xFF666666);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 4;
        lp.bottomMargin = addSpacing ? 28 : 0;
        txtA.setLayoutParams(lp);
        container.addView(txtA);
    }

    private void openPlayStore() {

        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + PLAY_STORE_PACKAGE)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + PLAY_STORE_PACKAGE)));
        }
    }
}
