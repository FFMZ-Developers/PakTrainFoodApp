package com.example.paktrainfoodapp.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * The four things that can happen around sign-up / sign-in, each with a
 * dialog rather than a Toast.
 *
 * These were all Toasts before, which is the wrong tool here: a Toast
 * disappears in a couple of seconds and can't hold a button. "We emailed
 * you a link, go check it (and look in spam)" is exactly the kind of
 * message a user needs to be able to read at their own pace and act on -
 * so it gets a dialog with a direct "Open Email App" button.
 */
public class AuthDialogs {

    public interface OnDismissed {
        void onDismissed();
    }

    /**
     * Shown right after registration succeeds. Deliberately blocks moving
     * on until the user acknowledges it - previously registration dropped
     * straight to the login screen with only a brief Toast, so people
     * regularly never realised an email had been sent at all, tried to log
     * in immediately, and got an unexplained "not verified" error.
     */
    public static void showVerificationSent(Context context, String email, OnDismissed onDismissed) {

        TextView message = buildMessageView(context,
                "We've sent a verification link to:<br><b>" + email + "</b>"
                        + "<br><br>Open that email and tap the link, then come back and log in."
                        + "<br><br><b><font color='#C62828'>IMPORTANT: If you don't see it, CHECK YOUR SPAM / JUNK FOLDER.</font></b>"
                        + " Verification emails very often land there.");

        new AlertDialog.Builder(context)
                .setTitle("Verify Your Email")
                .setView(message)
                .setCancelable(false)
                .setPositiveButton("Open Email App", (d, w) -> {
                    openEmailApp(context);
                    if (onDismissed != null) onDismissed.onDismissed();
                })
                .setNegativeButton("I'll Do It Later", (d, w) -> {
                    if (onDismissed != null) onDismissed.onDismissed();
                })
                .show();
    }

    /** Login attempt with correct credentials but an unverified email. */
    public static void showNotVerified(Context context, String email) {

        TextView message = buildMessageView(context,
                "Your email <b>" + email + "</b> hasn't been verified yet."
                        + "<br><br>Open the verification link we sent you, then try logging in again."
                        + "<br><br><b><font color='#C62828'>IMPORTANT: CHECK YOUR SPAM / JUNK FOLDER</font></b>"
                        + " - that's where it usually is.");

        new AlertDialog.Builder(context)
                .setTitle("Email Not Verified")
                .setView(message)
                .setPositiveButton("Open Email App", (d, w) -> openEmailApp(context))
                .setNegativeButton("Close", null)
                .show();
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
     * The admin has disabled this account (AuthDialogs.showWrongCredentials
     * is NOT what fires here - Firebase Auth itself rejects the sign-in
     * attempt with a distinct error code before the app ever sees a
     * password mismatch).
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

    /**
     * Opens whatever the user reads mail in. ACTION_MAIN with the EMAIL
     * category is what actually lands in an inbox - ACTION_SENDTO (the
     * usual first instinct) opens a *compose* window instead, which isn't
     * where the verification link is.
     */
    private static void openEmailApp(Context context) {

        try {

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_EMAIL);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return;
            }

            // No mail app registered - Gmail on the web is the safest
            // fallback rather than silently doing nothing.
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://mail.google.com")));

        } catch (Exception e) {
            android.widget.Toast.makeText(context,
                    "Couldn't open an email app - please check your inbox manually",
                    android.widget.Toast.LENGTH_LONG).show();
        }
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
