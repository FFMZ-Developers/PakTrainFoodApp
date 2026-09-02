package com.example.paktrainfoodapp.ui.main.Passenger.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;

public class CommonIssues extends Fragment {

    private TextView txtIssueTitle, txtIssueDescription;
    private Button btnContactSupport;

    public CommonIssues() {
        // Required empty constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_common_issues, container, false);

        txtIssueTitle = view.findViewById(R.id.txtIssueTitle);
        txtIssueDescription = view.findViewById(R.id.txtIssueDescription);
        btnContactSupport = view.findViewById(R.id.btnContactSupport);

        if (getArguments() != null) {

            String title = getArguments().getString("title", "Issue");
            String description = getArguments().getString("description", "");

            txtIssueTitle.setText(title);
            txtIssueDescription.setText(description);
        }

        btnContactSupport.setOnClickListener(v -> {

            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:supportpaktrainfood@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Support Request");
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(Intent.createChooser(intent, "Contact Support"));
            }

        });

        return view;
    }
}