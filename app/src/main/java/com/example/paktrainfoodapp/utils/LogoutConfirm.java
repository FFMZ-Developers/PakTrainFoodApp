package com.example.paktrainfoodapp.utils;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

/**
 * Confirmation step before signing out.
 *
 * Logging out is easy to trigger by accident (the button usually sits at
 * the bottom of a profile screen people are scrolling through) and it's
 * annoying to recover from - you have to type your password again, and on
 * this app a rider also drops offline and stops receiving orders. A single
 * "are you sure" tap is a cheap guard against that.
 */
public class LogoutConfirm {

    public interface OnConfirmed {
        void onConfirmed();
    }

    public static void show(Context context, OnConfirmed onConfirmed) {

        if (context == null || onConfirmed == null) return;

        new AlertDialog.Builder(context)
                .setTitle("Log Out?")
                .setMessage("Kya aap logout karna chahte hain?\n\n"
                        + "Aapko dobara login karna hoga.")
                .setPositiveButton("Yes, Log Out", (d, w) -> onConfirmed.onConfirmed())
                .setNegativeButton("Cancel", null)
                .show();
    }
}
