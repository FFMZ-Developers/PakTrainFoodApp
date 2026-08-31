package com.example.paktrainfoodapp.ui.main;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.text.TextUtils;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.ui.main.Delivery.dashboard.DeliveryDashboardFragment;
import com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader;
import com.example.paktrainfoodapp.ui.main.Restaurant.restaurant_LoadFragment;
import com.example.paktrainfoodapp.utils.PrefManager;
import com.example.paktrainfoodapp.utils.LocationEnforcementWatcher;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.messaging.FirebaseMessaging;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final String USER_ROLE_KEY = "USER_ROLE_KEY";
    private PrefManager prefManager;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private Passenger_Fragment_Loader passengerLoader;

    // Module 2 - checks every 5s (while app is foregrounded) that device
    // location is on and that background order-tracking is actually alive.
    private LocationEnforcementWatcher locationEnforcementWatcher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createNotificationChannel();
// Android 13+ Notification Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101
                );
            }
        }

        FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                );


        prefManager = new PrefManager(this);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Module 2 - app-open watchdog (location-off popup + tracking
        // service self-heal). Started/stopped in onResume/onPause below.
        locationEnforcementWatcher = new LocationEnforcementWatcher(this);

        // 🔹 100% FIXED BACK PRESS HANDLING FOR INNER FRAGMENTS
        // 🔹 100% PERFECT UNIVERSAL BACK PRESS HANDLING
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        Fragment fragment =
                                getSupportFragmentManager()
                                        .findFragmentById(R.id.main_container);

                        if (fragment instanceof Passenger_Fragment_Loader) {

                            Passenger_Fragment_Loader loader =
                                    (Passenger_Fragment_Loader) fragment;

                            if (loader.handleBackPressed()) {

                                return;

                            }

                        } else if (fragment instanceof restaurant_LoadFragment) {

                            restaurant_LoadFragment loader =
                                    (restaurant_LoadFragment) fragment;

                            if (loader.handleBackPressed()) {

                                return;

                            }

                        } else if (fragment instanceof DeliveryDashboardFragment) {

                            DeliveryDashboardFragment loader =
                                    (DeliveryDashboardFragment) fragment;

                            if (loader.handleBackPressed()) {

                                return;

                            }

                        }

                        setEnabled(false);

                        getOnBackPressedDispatcher().onBackPressed();

                    }

                });
        String userRole = getIntent().getStringExtra(USER_ROLE_KEY);
        if (userRole == null || userRole.isEmpty()) {
            userRole = prefManager.getUserRole();
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {

                    if (!task.isSuccessful()) {
                        Log.e("FCM", "Token generate nahi hua");
                        return;
                    }

                    String token = task.getResult();

                    Log.d("FCM_TOKEN", token);

                    Map<String, Object> map = new HashMap<>();
                    map.put("fcmToken", token);

                    db.collection("Users")
                            .document("Notification")
                            .collection("FCMTokens")
                            .document(uid)
                            .set(map)
                            .addOnSuccessListener(unused ->
                                    Log.d("FCM", "Token Save Successfully"))
                            .addOnFailureListener(e ->
                                    Log.e("FCM", e.getMessage()));
                });


        // Module: restriction check - runs regardless of which path below
        // loads the role's screen (some take a fast-path using cached
        // local prefs that skip a fresh Firestore read entirely), so a
        // restriction set by admin after this device's last full sign-in
        // still gets caught the next time the app opens.
        checkAccountRestriction(userRole, uid);

        switch (userRole) {
            case "RESTAURANT":
                handleRestaurantRole(uid);
                break;

            case "PASSENGER":
                passengerLoader = new Passenger_Fragment_Loader();

                String screen =
                        getIntent().getStringExtra("screen");

                if ("orders".equals(screen)) {

                    passengerLoader.requestOpenOrders();

                }

                loadFragment(passengerLoader);
                break;

            case "DELIVERY":
                handleDeliveryRole(uid);
                break;

            default:
                Toast.makeText(this, "Unknown role", Toast.LENGTH_SHORT).show();
                break;
        }
        handleNotificationIntent(getIntent());
    }

    /**
     * Shows a dismissible warning if an admin has restricted this account -
     * they can still browse/view existing orders and their wallet, but
     * key action screens (placing an order, accepting an order) check this
     * same flag themselves before allowing it. A restricted account is
     * NOT signed out or blocked from opening the app at all - that's what
     * a full "disable" (a real Firebase Auth disable, enforced by Firebase
     * itself at sign-in) is for.
     */
    private void checkAccountRestriction(String role, String uid) {

        com.google.firebase.firestore.DocumentReference ref;

        switch (role) {
            case "RESTAURANT":
                ref = db.collection("Users").document("Restaurant").collection("VerifiedRegister").document(uid);
                break;
            case "DELIVERY":
                ref = db.collection("Users").document("Delivery").collection("VerifiedRegister").document(uid);
                break;
            default:
                ref = db.collection("Users").document("Passenger").collection("Register").document(uid);
                break;
        }

        ref.get().addOnSuccessListener(doc -> {

            if (doc == null || !doc.exists()) return;

            Boolean restricted = doc.getBoolean("isRestricted");

            if (restricted != null && restricted) {

                String reason = doc.getString("restrictionReason");

                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Account Restricted")
                        .setMessage("Your account has a restriction from PakTrainFood"
                                + (reason != null && !reason.isEmpty() ? ":\n\n" + reason : ".")
                                + "\n\nYou can still view your existing orders and wallet, but can't place or accept new orders until this is resolved.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    //jab app open ho then notificaton pr clik krny pr call hoga
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);

        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {

        if (intent == null) return;

        String screen = intent.getStringExtra("screen");

        if (screen == null) return;

        // Module: auto-payout receipt notifications deep-link here -
        // works for any role since it replaces the top-level container
        // directly (same pattern OrderDetailFragment uses), rather than
        // needing each role's own nested navigation system to know about it.
        // Module: chat deep-link - tapping a chat notification opens that
        // exact conversation, WhatsApp-style, rather than dumping the user
        // on a generic screen and making them navigate back to it.
        if ("chat".equals(screen)) {

            String chatOrderId = intent.getStringExtra("orderId");
            String chatType = intent.getStringExtra("chatType");
            String senderName = intent.getStringExtra("senderName");

            if (chatOrderId != null && chatType != null) {

                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_container,
                                com.example.paktrainfoodapp.ui.shared.chat.OrderChatFragment
                                        .newInstance(chatOrderId, chatType,
                                                senderName != null ? senderName : "Chat", null))
                        .addToBackStack("chat")
                        .commit();
            }

            return;
        }

        // ✅ FIX: every notification OTHER than chat used to navigate
        // straight to its target screen the instant it was tapped - no
        // way to actually read the message first, and every message type
        // behaved identically regardless of what it was about. Now they
        // all open NotificationDetailFragment - the message shown in
        // full, with whatever action button actually fits (View Wallet
        // for a payment message, View Order for an order-lifecycle one,
        // Browse Restaurants for a rejected-order one) - and THAT button
        // is what performs the navigation, via navigateToNotificationTarget()
        // below (which is the exact same logic this used to run directly).
        String notificationId = intent.getStringExtra("notificationId");
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        String orderId = intent.getStringExtra("orderId");
        String orderNumber = intent.getStringExtra("orderNumber");

        if (screen == null && notificationId == null) return;

        com.example.paktrainfoodapp.ui.main.notification.NotificationDetailFragment detail =
                !TextUtils.isEmpty(notificationId)
                        ? com.example.paktrainfoodapp.ui.main.notification.NotificationDetailFragment
                                .newInstanceFromId(notificationId)
                        : com.example.paktrainfoodapp.ui.main.notification.NotificationDetailFragment
                                .newInstanceFromExtras(title, body, screen, orderId, orderNumber);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container, detail)
                .addToBackStack("notification_detail")
                .commit();
    }

    /**
     * The actual navigation for a notification's action button -
     * unchanged from what handleNotificationIntent() used to do directly;
     * it just runs a step later now, after the user has seen the message
     * and deliberately tapped through.
     */
    public void navigateToNotificationTarget(String screen, String orderId) {

        if ("wallet".equals(screen)) {

            String uiRole;

            switch (prefManager.getUserRole() != null ? prefManager.getUserRole() : "") {
                case "RESTAURANT": uiRole = com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.ROLE_RESTAURANT; break;
                case "DELIVERY":   uiRole = com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.ROLE_DELIVERY; break;
                default:           uiRole = com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.ROLE_PASSENGER; break;
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.main_container,
                            com.example.paktrainfoodapp.ui.shared.wallet.WalletFragment.newInstance(uiRole))
                    .addToBackStack("wallet")
                    .commit();

            return;
        }

        // A rejected order is over - send the passenger straight back to
        // picking a journey/restaurant rather than to a dead order.
        if ("home".equals(screen)) {

            if (passengerLoader != null) passengerLoader.navigateToHome();

            return;
        }

        if ("orders".equals(screen)) {

            // If we know exactly which order this is about, go straight to
            // its own detail/live-tracking screen (works for all 3 roles) -
            // far more useful than just switching to a generic tab.
            if (!TextUtils.isEmpty(orderId)) {

                String role = prefManager.getUserRole();

                Fragment target = "PASSENGER".equalsIgnoreCase(role)
                        ? buildPassengerDetail(orderId)
                        : com.example.paktrainfoodapp.ui.main.Restaurant.order.OrderDetailFragment
                                .newInstance(orderId);

                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_container, target)
                        .addToBackStack("order_detail")
                        .commit();

                return;
            }

            if (passengerLoader != null) {
                passengerLoader.navigateToOrders(0);
            }
        }
    }

    private Fragment buildPassengerDetail(String orderId) {

        com.example.paktrainfoodapp.ui.main.Passenger.order.passanger_orderDetailFragment f =
                new com.example.paktrainfoodapp.ui.main.Passenger.order.passanger_orderDetailFragment();

        Bundle args = new Bundle();
        args.putString("orderId", orderId);
        f.setArguments(args);

        return f;
    }
    private void handleRestaurantRole(String uid) {
        if (prefManager.isRegistered() && prefManager.isRestaurantVerified()) {
            loadFragment(new restaurant_LoadFragment());
            return;
        }

        db.collection("Users")
                .document("Restaurant")
                .collection("VerifiedRegister")
                .document(uid)
                .get()
                .addOnSuccessListener(this::handleRestaurantDocument)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    startVerificationWizard("RESTAURANT", uid, auth.getCurrentUser().getEmail());
                });
    }

    private void handleRestaurantDocument(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            prefManager.setRegistered(false, auth.getCurrentUser().getEmail());
            startVerificationWizard("RESTAURANT", auth.getCurrentUser().getUid(), auth.getCurrentUser().getEmail());
            return;
        }

        Boolean isVerified = doc.getBoolean("isVerified");
        String email = doc.getString("email");
        String city = doc.getString("city");
        String status = doc.getString("verificationStatus");

        if (isVerified != null && isVerified) {
            prefManager.setRegistered(true, email);
            prefManager.setIsRestaurantVerified(true);
            prefManager.setUserCity(city);
            loadFragment(new restaurant_LoadFragment());
        } else if ("rejected".equals(status)) {
            prefManager.setRegistered(true, email);
            loadFragment(com.example.paktrainfoodapp.ui.shared.verification.RejectedFragment.newInstance(
                    "RESTAURANT", auth.getCurrentUser().getUid(), email, doc.getString("rejectionReason")));
        } else {
            prefManager.setRegistered(true, email);
            loadFragment(new com.example.paktrainfoodapp.ui.shared.verification.PendingReviewFragment());
        }
    }

    private void handleDeliveryRole(String uid) {
        if (prefManager.isRegistered() && prefManager.isDeliveryVerified()) {
            loadFragment(new DeliveryDashboardFragment());
            return;
        }

        db.collection("Users")
                .document("Delivery")
                .collection("VerifiedRegister")
                .document(uid)
                .get()
                .addOnSuccessListener(this::handleDeliveryDocument)
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    startVerificationWizard("DELIVERY", uid, auth.getCurrentUser().getEmail());
                });
    }

    private void handleDeliveryDocument(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            prefManager.setRegistered(false, auth.getCurrentUser().getEmail());
            startVerificationWizard("DELIVERY", auth.getCurrentUser().getUid(), auth.getCurrentUser().getEmail());
            return;
        }

        Boolean isVerified = doc.getBoolean("isVerified");
        String email = doc.getString("email");
        String city = doc.getString("city");
        String status = doc.getString("verificationStatus");

        if (isVerified != null && isVerified) {
            prefManager.setRegistered(true, email);
            prefManager.setIsDeliveryVerified(true);
            prefManager.setUserCity(city);
            loadFragment(new DeliveryDashboardFragment());
        } else if ("rejected".equals(status)) {
            prefManager.setRegistered(true, email);
            loadFragment(com.example.paktrainfoodapp.ui.shared.verification.RejectedFragment.newInstance(
                    "DELIVERY", auth.getCurrentUser().getUid(), email, doc.getString("rejectionReason")));
        } else {
            prefManager.setRegistered(true, email);
            loadFragment(new com.example.paktrainfoodapp.ui.shared.verification.PendingReviewFragment());
        }
    }

    /** Launches the multi-step verification wizard for a brand-new applicant. */
    private void startVerificationWizard(String role, String uid, String email) {

        Intent intent = new Intent(this,
                com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.class);

        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_ROLE, role);
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_UID, uid);
        intent.putExtra(com.example.paktrainfoodapp.ui.shared.verification.VerificationWizardActivity.EXTRA_EMAIL, email);

        startActivity(intent);
        finish();
    }

    private void loadFragment(Fragment fragment) {
        if (isFinishing()) return;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container, fragment)
                .commitAllowingStateLoss();
    }
    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    "channel_id",
                    "General Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );

            NotificationManager manager = getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    // =========================================================
    // Module 2 - start/stop the app-open watchdog with the activity's
    // foreground lifecycle. Only needs to run while something is actually
    // on screen; LocationService has its own independent background
    // watchdog for while the app isn't open at all.
    // =========================================================

    @Override
    protected void onResume() {
        super.onResume();

        if (locationEnforcementWatcher != null) {
            locationEnforcementWatcher.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (locationEnforcementWatcher != null) {
            locationEnforcementWatcher.stop();
        }
    }

}





