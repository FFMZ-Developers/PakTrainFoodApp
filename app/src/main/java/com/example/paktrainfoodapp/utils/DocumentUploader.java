package com.example.paktrainfoodapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Uploads verification documents (CNIC front/back, business license, live
 * selfie, etc.) to Cloudinary using the account's "credential_preset"
 * unsigned upload preset - kept separate from "profile_preset" since these
 * are sensitive identity documents, not display pictures.
 *
 * This replaces the old ImgBB-based upload previously used in restaurant
 * registration, which had an API key hardcoded directly in the app - the
 * same category of problem fixed earlier for the Firebase/Stripe secrets.
 * No key of any kind lives in the app with an unsigned Cloudinary preset.
 */
public class DocumentUploader {

    private static final String CLOUD_NAME = "dllffrfdz";
    private static final String UPLOAD_PRESET = "credential_preset";

    private static final String UPLOAD_URL =
            "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    private static final OkHttpClient client = new OkHttpClient();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onFailure(Exception e);
    }

    /**
     * @param context  needed to read the picked/captured image bytes from its Uri
     * @param role     "restaurant" or "delivery" - just for Cloudinary folder organization
     * @param uid      current user's uid
     * @param docType  which document this is, e.g. "cnic_front", "cnic_back",
     *                 "license", "selfie" - becomes part of the stored filename
     *                 so re-uploading the same document type overwrites the old one
     */
    public static void upload(
            Context context,
            String role,
            String uid,
            String docType,
            Uri imageUri,
            UploadCallback callback) {

        if (context == null || uid == null || imageUri == null) {
            callback.onFailure(new Exception("Missing context, uid or image"));
            return;
        }

        new Thread(() -> {

            try {

                byte[] imageBytes = readBytes(context, imageUri);

                RequestBody fileBody =
                        RequestBody.create(imageBytes, MediaType.parse("image/jpeg"));

                MultipartBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", uid + "_" + docType + ".jpg", fileBody)
                        .addFormDataPart("upload_preset", UPLOAD_PRESET)
                        .addFormDataPart("public_id", "verification/" + role + "/" + uid + "/" + docType)
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

    /**
     * Same as {@link #upload}, but for a selfie captured directly as a
     * Bitmap via ActivityResultContracts.TakePicturePreview() - used instead
     * of a Uri-based camera intent so no FileProvider/manifest setup is
     * needed just for this one capture.
     */
    public static void uploadBitmap(
            String role,
            String uid,
            String docType,
            Bitmap bitmap,
            UploadCallback callback) {

        if (uid == null || bitmap == null) {
            callback.onFailure(new Exception("Missing uid or image"));
            return;
        }

        new Thread(() -> {

            try {

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream);
                byte[] imageBytes = stream.toByteArray();

                RequestBody fileBody =
                        RequestBody.create(imageBytes, MediaType.parse("image/jpeg"));

                MultipartBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", uid + "_" + docType + ".jpg", fileBody)
                        .addFormDataPart("upload_preset", UPLOAD_PRESET)
                        .addFormDataPart("public_id", "verification/" + role + "/" + uid + "/" + docType)
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
