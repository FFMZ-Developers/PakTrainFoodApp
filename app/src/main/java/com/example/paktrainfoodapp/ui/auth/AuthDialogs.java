package com.example.paktrainfoodapp.ui.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;

/**
 * The dialogs around sign-up / sign-in.
 *
 * These were all Toasts before, which is the wrong tool here: a Toast
 * disappears in a couple of seconds and can't hold a button. "We emailed
 * you a link, go check it (and look in spam)" is exactly the kind of
 * message a user needs to be able to read at their own pace and act on -
 * so it gets a dialog instead.
 *
 * Registration (showVerificationSent) is deliberately bare-bones: just
 * "check your Gmail", the address, the spam/junk note, and a single OK.
 * Login-time (showNotVerified) additionally offers "Resend Email", since
 * that's the point someone is most likely to actually need it.
 */
public class AuthDialogs {

    public interface OnDismissed {
        void onDismissed();
    }

    // ---- Resend throttling ----
    //
    // Firebase itself will start rejecting rapid repeat sends, and an
    // unlimited "Resend" button invites people to hammer it when an email
    // is just slow. Two per 24 hours is enough for a genuinely lost email
    // without turning into a way to spam someone's inbox. Tracked locally
    // (per-device, per-email) because there's no server-side counter for
    // this and adding one for a purely cosmetic guard isn't worth a
    // Cloud Function.
    private static final String PREFS = "PakTrainAuthPrefs";
    private static final int MAX_RESENDS_PER_DAY = 2;
    private static final long WINDOW_MS = TimeUnit.HOURS.toMillis(24);

    private static String countKey(String email) { return "resend_count_" + email; }
    private static String windowKey(String email) { return "resend_window_start_" + email; }

    /** How many resends are still allowed for this email right now. */
    private static int resendsLeft(Context context, String email) {

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        long windowStart = prefs.getLong(windowKey(email), 0);
        long now = System.currentTimeMillis();

        // Window expired - the allowance resets.
        if (now - windowStart > WINDOW_MS) return MAX_RESENDS_PER_DAY;

        return Math.max(0, MAX_RESENDS_PER_DAY - prefs.getInt(countKey(email), 0));
    }

    private static void recordResend(Context context, String email) {

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        long windowStart = prefs.getLong(windowKey(email), 0);
        long now = System.currentTimeMillis();

        if (now - windowStart > WINDOW_MS) {
            // Start a fresh 24h window with this as the first send.
            prefs.edit()
                    .putLong(windowKey(email), now)
                    .putInt(countKey(email), 1)
                    .apply();
        } else {
            prefs.edit()
                    .putInt(countKey(email), prefs.getInt(countKey(email), 0) + 1)
                    .apply();
        }
    }

    /**
     * Sends another verification email, respecting the 2-per-24h cap.
     * Requires a currently signed-in FirebaseUser - which is the case
     * right after registration (before the app signs them out) and any
     * time a login attempt succeeded but the email wasn't verified yet.
     */
    private static void resendVerification(Context context, String email) {

        if (resendsLeft(context, email) <= 0) {
            Toast.makeText(context,
                    "You've reached the limit of " + MAX_RESENDS_PER_DAY
                            + " resends per day. Please check your spam folder or try again tomorrow.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Toast.makeText(context,
                    "Please log in with your email and password first, then tap Resend.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        user.sendEmailVerification()
                .addOnSuccessListener(unused -> {

                    recordResend(context, email);

                    int left = resendsLeft(context, email);

                    Toast.makeText(context,
                            "Verification email sent again. " + left + " resend(s) left today.",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context,
                                "Couldn't resend: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    /**
     * Shown right after registration succeeds. Deliberately blocks moving
     * on until the user acknowledges it - previously registration dropped
     * straight to the login screen with only a brief Toast, so people
     * regularly never realised an email had been sent at all, tried to log
     * in immediately, and got an unexplained "not verified" error.
     *
     * Kept deliberately simple: just "check your Gmail" + the address +
     * the spam/junk reminder, with a single OK button. No "Open Email
     * App" and no "Resend" here - those live on the login-time
     * showNotVerified() dialog instead.
     */
    public static void showVerificationSent(Context context, String email, OnDismissed onDismissed) {

        TextView message = buildMessageView(context,
                "Check your Gmail:<br><b>" + email + "</b>"
                        + "<br><br><b><font color='#C62828'>NOTE: If the mail doesn't arrive, check your Spam / Junk folder.</font></b>");

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Verify Your Email")
                .setView(message)
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    if (onDismissed != null) onDismissed.onDismissed();
                })
                .create();

        dialog.show();
    }

    /**
     * Login attempt with correct credentials but an unverified email.
     *
     * @param onDismissed run when the dialog closes - the caller uses this
     *                    to sign the user out. Signing out BEFORE showing
     *                    this dialog would break the Resend button, since
     *                    Firebase needs a signed-in user to resend for.
     */
    public static void showNotVerified(Context context, String email, OnDismissed onDismissed) {

        TextView message = buildMessageView(context,
                "Your email <b>" + email + "</b> hasn't been verified yet."
                        + "<br><br>Open the verification link we sent you, then try logging in again."
                        + "<br><br><b><font color='#C62828'>IMPORTANT: CHECK YOUR SPAM / JUNK FOLDER</font></b>"
                        + " - that's where it usually is.");

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Email Not Verified")
                .setView(message)
                .setNeutralButton("Resend Email", null)
                .setPositiveButton("OK", (d, w) -> {
                    if (onDismissed != null) onDismissed.onDismissed();
                })
                .create();

        dialog.setOnCancelListener(d -> {
            if (onDismissed != null) onDismissed.onDismissed();
        });

        dialog.show();

        // Listener set AFTER show() so tapping "Resend" doesn't dismiss
        // the dialog - the user should be able to resend and still read
        // the message before tapping OK.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                resendVerification(context, email));
    }

    /** No account exists for the email that was entered. */
    public static void showNotRegistered(Context context) {

        new AlertDialog.Builder(context)
                .setTitle("No Account Found")
                .setMessage("We couldn't find an account with that email.\n\n"
                        + "Please register first, then verify your email before logging in.")
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * The admin has disabled this account (showWrongCredentials is NOT
     * what fires here - Firebase Auth itself rejects the sign-in attempt
     * with a distinct error code before any password mismatch).
     */
    public static void showAccountDisabled(Context context) {

        new AlertDialog.Builder(context)
                .setTitle("Account Disabled")
                .setMessage("Your account has been disabled by an administrator.\n\n"
                        + "If you believe this is a mistake, please contact support.")
                .setPositiveButton("OK", null)
                .show();
    }

    /** Wrong password, or an otherwise rejected sign-in. */
    public static void showWrongCredentials(Context context) {

        new AlertDialog.Builder(context)
                .setTitle("Incorrect Details")
                .setMessage("The email or password you entered is incorrect.\n\n"
                        + "Please check both and try again.")
                .setPositiveButton("Try Again", null)
                .show();
    }
    /** Signed-in account belongs to a different role than the one selected
     *  on the login screen - stops here rather than silently starting a
     *  fresh registration for the selected role under the same account. */
    public static void showWrongRole(Context context, String actualRole) {

        new AlertDialog.Builder(context)
                .setTitle("Wrong Role Selected")
                .setMessage("Your account was created for the " + actualRole
                        + " role.\n\nPlease select the correct role to log in.")
                .setPositiveButton("OK", null)
                .show();
    }
    private static TextView buildMessageView(Context context, String html) {

        TextView tv = new TextView(context);

        int pad = (int) (22 * context.getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad / 2, pad, 0);

        tv.setTextSize(15);
        tv.setLineSpacing(0, 1.15f);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY));

        return tv;
    }
}
