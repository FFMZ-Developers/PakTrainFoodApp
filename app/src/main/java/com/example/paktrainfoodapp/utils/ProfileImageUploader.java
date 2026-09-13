package com.example.paktrainfoodapp.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Uploads a picked profile picture to Cloudinary (unsigned upload preset)
 * and returns the permanent secure_url, so the image survives app restarts
 * instead of only being shown temporarily from the local picked Uri.
 *
 * Uses "profile_preset" (unsigned) already configured on the Cloudinary
 * account - no API key/secret is ever stored in the app.
 */
public class ProfileImageUploader {

    private static final String CLOUD_NAME = "dllffrfdz";
    private static final String UPLOAD_PRESET = "profile_preset";

    private static final String UPLOAD_URL =
            "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    private static final OkHttpClient client = new OkHttpClient();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onFailure(Exception e);
    }

    /**
     * @param context  needed to read the picked image bytes from its Uri
     * @param role     used as a folder inside Cloudinary, e.g. "passenger",
     *                 "restaurant", "delivery" - just for organization
     * @param uid      current user's uid - used as the public_id so
     *                 re-uploading overwrites the previous picture
     * @param imageUri local content:// Uri picked from the gallery
     */
    public static void upload(
            Context context,
            String role,
            String uid,
            Uri imageUri,
            UploadCallback callback) {

        if (context == null || uid == null || imageUri == null) {
            callback.onFailure(new Exception("Missing context, uid or image"));
            return;
        }

        // Network + file I/O off the main thread; callback hops back to
        // the main thread so callers can safely touch views inside it.
        new Thread(() -> {

            try {

                byte[] imageBytes = readBytes(context, imageUri);

                RequestBody fileBody =
                        RequestBody.create(imageBytes, MediaType.parse("image/jpeg"));

                MultipartBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", uid + ".jpg", fileBody)
                        .addFormDataPart("upload_preset", UPLOAD_PRESET)
                        .addFormDataPart("public_id", role + "/" + uid)
                        .build();

                Request request = new Request.Builder()
                        .url(UPLOAD_URL)
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {

                    String responseBody =
                            response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {

                        throw new IOException(
                                "Cloudinary upload failed ("
                                        + response.code() + "): " + responseBody);
                    }

                    JSONObject json = new JSONObject(responseBody);
                    String secureUrl = json.getString("secure_url");

                    mainHandler.post(() -> callback.onSuccess(secureUrl));
                }

            } catch (Exception e) {

                mainHandler.post(() -> callback.onFailure(e));
            }

        }).start();
    }

    private static byte[] readBytes(Context context, Uri uri) throws IOException {

        try (InputStream inputStream =
                     context.getContentResolver().openInputStream(uri)) {

            if (inputStream == null) {
                throw new IOException("Could not open image stream");
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }

            return buffer.toByteArray();
        }
    }
}
