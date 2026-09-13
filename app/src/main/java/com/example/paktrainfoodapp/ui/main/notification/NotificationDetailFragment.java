package com.example.paktrainfoodapp.ui.main.notification;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A single notification, expanded to full detail with whatever action
 * actually makes sense for it - opened both when tapping a notification
 * from the system tray (cold-start or background) and when tapping one
 * in the in-app Alerts list, so the experience is identical either way.
 *
 * Chat notifications never reach this screen - see
 * MainActivity.handleNotificationIntent(), they open the chat directly
 * and are never persisted in the first place (sendNotification.js's
 * persist:false).
 */
public class NotificationDetailFragment extends Fragment {

    private static final String ARG_NOTIFICATION_ID = "notificationId";
    private static final String ARG_TITLE = "title";
    private static final String ARG_BODY = "body";
    private static final String ARG_SCREEN = "screen";
    private static final String ARG_ORDER_ID = "orderId";
    private static final String ARG_ORDER_NUMBER = "orderNumber";

    /**
     * Preferred entry point - loads everything fresh from Firestore by
     * the notification's own document id (used from the in-app Alerts
     * list, where the id is always known).
     */
    public static NotificationDetailFragment newInstanceFromId(String notificationId) {

        NotificationDetailFragment f = new NotificationDetailFragment();
        Bundle b = new Bundle();
        b.putString(ARG_NOTIFICATION_ID, notificationId);
        f.setArguments(b);
        return f;
    }

    /**
     * Fallback entry point - used when opened straight from the FCM data
     * payload (system tray tap) and no Firestore id was included (older
     * notifications sent before this field existed, or non-persisted
     * ones that still somehow ended up here). Renders directly from the
     * intent extras instead of a Firestore read.
     */
    public static NotificationDetailFragment newInstanceFromExtras(
            String title, String body, String screen, String orderId, String orderNumber) {

        NotificationDetailFragment f = new NotificationDetailFragment();
        Bundle b = new Bundle();
        b.putString(ARG_TITLE, title);
        b.putString(ARG_BODY, body);
        b.putString(ARG_SCREEN, screen);
        b.putString(ARG_ORDER_ID, orderId);
        b.putString(ARG_ORDER_NUMBER, orderNumber);
        f.setArguments(b);
        return f;
    }

    private TextView txtTitle, txtBody, txtTime, txtOrderChip;
    private Button btnAction;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_notification_detail, container, false);

        ImageView btnBack = v.findViewById(R.id.btnNotifDetailBack);
        btnBack.setOnClickListener(view -> {
            if (isAdded()) requireActivity().getSupportFragmentManager().popBackStack();
        });

        txtTitle = v.findViewById(R.id.txtNotifDetailTitle);
        txtBody = v.findViewById(R.id.txtNotifDetailBody);
        txtTime = v.findViewById(R.id.txtNotifDetailTime);
        txtOrderChip = v.findViewById(R.id.txtNotifDetailOrderChip);
        btnAction = v.findViewById(R.id.btnNotifDetailAction);

        Bundle args = getArguments();

        if (args != null && args.getString(ARG_NOTIFICATION_ID) != null) {
            loadFromFirestore(args.getString(ARG_NOTIFICATION_ID));
        } else if (args != null) {
            render(
                    args.getString(ARG_TITLE),
                    args.getString(ARG_BODY),
                    args.getString(ARG_SCREEN),
                    args.getString(ARG_ORDER_ID),
                    args.getString(ARG_ORDER_NUMBER),
                    null
            );
        }

        return v;
    }

    private void loadFromFirestore(String notificationId) {

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (uid == null) return;

        // The notification could be under any of the 3 role subtrees -
        // this screen doesn't know in advance which one opened it, so it
        // tries each in turn. Cheap (at most 3 reads) and simple.
        String[][] paths = {
                {"Passenger", "Register"},
                {"Restaurant", "VerifiedRegister"},
                {"Delivery", "VerifiedRegister"}
        };

        tryPath(paths, 0, uid, notificationId);
    }

    private void tryPath(String[][] paths, int index, String uid, String notificationId) {

        if (index >= paths.length || !isAdded()) return;

        FirebaseFirestore.getInstance()
                .collection("Users").document(paths[index][0])
                .collection(paths[index][1]).document(uid)
                .collection("Notifications").document(notificationId)
                .get()
                .addOnSuccessListener(doc -> {

                    if (!isAdded()) return;

                    if (!doc.exists()) {
                        tryPath(paths, index + 1, uid, notificationId);
                        return;
                    }

                    render(
                            doc.getString("title"),
                            doc.getString("body"),
                            doc.getString("screen"),
                            doc.getString("orderId"),
                            doc.getString("orderNumber"),
                           null
                    );

                    // Marking read here (rather than back in the list)
                    // means it's correctly marked even when opened cold
                    // from the system tray, which never touched the list.
                    doc.getReference().update("isRead", true);
                })
                .addOnFailureListener(e -> tryPath(paths, index + 1, uid, notificationId));
    }

    private void render(String title, String body, String screen,
                        String orderId, String orderNumber, DocumentSnapshot ignored) {

        if (!isAdded()) return;

        txtTitle.setText(!TextUtils.isEmpty(title) ? title : "Notification");
        txtBody.setText(!TextUtils.isEmpty(body) ? body : "");

        txtTime.setText(new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date()));

        if (!TextUtils.isEmpty(orderNumber)) {
            txtOrderChip.setVisibility(View.VISIBLE);
            txtOrderChip.setText("Order #" + orderNumber);
        } else {
            txtOrderChip.setVisibility(View.GONE);
        }

        setupActionButton(screen, orderId);
    }

    /**
     * The one contextual thing this screen offers beyond just reading the
     * message - a direct shortcut to wherever the message is actually
     * about, so "your payment was sent" doesn't leave you to go hunt for
     * the wallet screen yourself.
     */
    private void setupActionButton(String screen, String orderId) {

        if (screen == null) screen = "";

        switch (screen) {

            case "wallet":
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText("View Wallet");
                btnAction.setOnClickListener(v -> navigateTo("wallet", orderId));
                break;

            case "orders":
                if (!TextUtils.isEmpty(orderId)) {
                    btnAction.setVisibility(View.VISIBLE);
                    btnAction.setText("View Order");
                    btnAction.setOnClickListener(v -> navigateTo("orders", orderId));
                } else {
                    btnAction.setVisibility(View.GONE);
                }
                break;

            case "home":
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText("Browse Restaurants");
                btnAction.setOnClickListener(v -> navigateTo("home", orderId));
                break;

            default:
                btnAction.setVisibility(View.GONE);
        }
    }

    private void navigateTo(String screen, String orderId) {

        if (!isAdded()) return;

        if (!(requireActivity() instanceof com.example.paktrainfoodapp.ui.main.MainActivity)) return;

        // Reuses MainActivity's own (already role-aware, already correct)
        // deep-link routing rather than duplicating that logic here.
        ((com.example.paktrainfoodapp.ui.main.MainActivity) requireActivity())
                .navigateToNotificationTarget(screen, orderId);
    }
}
