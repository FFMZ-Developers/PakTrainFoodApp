package com.example.paktrainfoodapp.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Module: "Share the app" - one place used from all three profile
 * screens (Restaurant, Rider, Passenger), rather than three separate
 * copies of the same intent-building code.
 */
public class ShareUtils {

    private static final String PLAY_STORE_PACKAGE = "com.fahad.paktrainfoodservice";

    private static String playStoreLink() {
        return "https://play.google.com/store/apps/details?id=" + PLAY_STORE_PACKAGE;
    }

    private static String shareMessage() {
        return "Order food to be delivered right to your train seat with PakTrainFood! "
                + "Download it here: " + playStoreLink();
    }

    /** The system share sheet - shows WhatsApp alongside every other app
        that can handle shared text, plus the OS's own "Copy" action. */
    public static void shareGeneric(Context context) {

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareMessage());

        context.startActivity(Intent.createChooser(intent, "Share PakTrainFood"));
    }

    /** Goes straight to WhatsApp specifically, for the common case where
        that's exactly where someone wants to share to. */
    public static void shareToWhatsApp(Context context) {

        try {

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.setPackage("com.whatsapp");
            intent.putExtra(Intent.EXTRA_TEXT, shareMessage());

            context.startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(context, "WhatsApp isn't installed", Toast.LENGTH_SHORT).show();
        }
    }

    /** Copies just the link, for pasting anywhere manually. */
    public static void copyLink(Context context) {

        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("PakTrainFood Link", playStoreLink()));
            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The single entry point every profile screen calls - a small chooser
     * offering WhatsApp / Copy Link / everything else, rather than each
     * screen deciding on its own which option to expose.
     */
    public static void showShareOptions(Context context) {

        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Share PakTrainFood")
                .setItems(new String[]{"WhatsApp", "Copy Link", "More Options..."}, (dialog, which) -> {

                    switch (which) {
                        case 0: shareToWhatsApp(context); break;
                        case 1: copyLink(context); break;
                        default: shareGeneric(context); break;
                    }
                })
                .show();
    }
}
