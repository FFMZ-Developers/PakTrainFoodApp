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

    private static final String PLAY_STORE_PACKAGE = "com.example.paktrainfoodapp";

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

        return v;
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
