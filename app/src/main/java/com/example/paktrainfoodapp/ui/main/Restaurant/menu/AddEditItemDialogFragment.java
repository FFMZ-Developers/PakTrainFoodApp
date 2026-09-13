package com.example.paktrainfoodapp.ui.main.Restaurant.menu;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.fragment.app.DialogFragment;
import com.bumptech.glide.Glide;
import com.example.paktrainfoodapp.R;
import com.example.paktrainfoodapp.utils.PrefManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class AddEditItemDialogFragment extends DialogFragment {

    public interface Listener { void onSaved(); }

    private String restId;
    private com.example.paktrainfoodapp.ui.main.Restaurant.menu.MenuItem editingItem;
    private Listener listener;

    private TextInputEditText etName, etDesc, etTime, etCategory;
    private LinearLayout layoutVariationsContainer;
    private ImageView ivImage;
    private Button btnSave, btnCancel;
    private ProgressBar progressBar;
    private Uri imageUri;

    private static final String IMGBB_API_KEY = "48a067c9d290ecbca102dca44184ae22";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ActivityResultLauncher<String> imagePickerLauncher;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static AddEditItemDialogFragment newInstance(String restId, com.example.paktrainfoodapp.ui.main.Restaurant.menu.MenuItem item, Listener listener) {
        AddEditItemDialogFragment f = new AddEditItemDialogFragment();
        f.restId = restId;
        f.editingItem = item;
        f.listener = listener;
        return f;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_restaurant_add_edit_item_dialog, container, false);

        etName = v.findViewById(R.id.et_item_name);
        etDesc = v.findViewById(R.id.et_item_desc);
        etTime = v.findViewById(R.id.et_item_time);
        etCategory = v.findViewById(R.id.et_item_category);
        layoutVariationsContainer = v.findViewById(R.id.layout_variations_container);
        ivImage = v.findViewById(R.id.imgPreview);
        btnSave = v.findViewById(R.id.btn_save_item);
        btnCancel = v.findViewById(R.id.btn_cancel_item);
        progressBar = v.findViewById(R.id.progress_Bar);

        v.findViewById(R.id.btnChooseImage).setOnClickListener(v1 -> imagePickerLauncher.launch("image/*"));
        v.findViewById(R.id.btn_add_variation).setOnClickListener(v1 -> addVariationRow(null, null));
        btnSave.setOnClickListener(v1 -> onSaveClicked());
        btnCancel.setOnClickListener(v1 -> dismiss());

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) { imageUri = uri; Glide.with(this).load(uri).into(ivImage); }
        });

        if (editingItem != null) {
            etName.setText(editingItem.getName());
            etDesc.setText(editingItem.getDescription());
            etTime.setText(editingItem.getTime());
            etCategory.setText(editingItem.getCategory());
            Glide.with(this).load(editingItem.getImageUrl()).into(ivImage);

            if (editingItem.getVariations() != null) {
                for (Map.Entry<String, Double> entry : editingItem.getVariations().entrySet()) {
                    addVariationRow(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        return v;
    }

    private void addVariationRow(String name, String price) {
        View rowView = getLayoutInflater().inflate(R.layout.item_variation_row, null);
        if (name != null) ((EditText) rowView.findViewById(R.id.et_var_name)).setText(name);
        if (price != null) ((EditText) rowView.findViewById(R.id.et_var_price)).setText(price);
        rowView.findViewById(R.id.btn_remove_variation).setOnClickListener(v -> layoutVariationsContainer.removeView(rowView));
        layoutVariationsContainer.addView(rowView);
    }

    private void onSaveClicked() {
        String name = etName.getText().toString().trim();
        Map<String, Double> variations = new HashMap<>();

        for (int i = 0; i < layoutVariationsContainer.getChildCount(); i++) {
            View row = layoutVariationsContainer.getChildAt(i);
            String n = ((EditText) row.findViewById(R.id.et_var_name)).getText().toString();
            String p = ((EditText) row.findViewById(R.id.et_var_price)).getText().toString();
            if (!n.isEmpty() && !p.isEmpty()) variations.put(n, Double.parseDouble(p));
        }

        if (name.isEmpty() || variations.isEmpty()) {
            Toast.makeText(getContext(), "Name and at least one variation required", Toast.LENGTH_SHORT).show();
            return;
        }

        processAndSaveItem(name, etDesc.getText().toString(), etTime.getText().toString(), etCategory.getText().toString(), variations);
    }

    private void processAndSaveItem(String name, String desc, String time, String cat, Map<String, Double> vars) {
        progressBar.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            String imageUrl = (editingItem != null && imageUri == null) ? editingItem.getImageUrl() : uploadImage();
            mainHandler.post(() -> {
                if (imageUrl == null && imageUri != null) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Image Upload Failed", Toast.LENGTH_SHORT).show();
                } else {
                    saveToFirestore(name, desc, time, cat, vars, imageUrl);
                }
            });
        });
    }

    private String uploadImage() {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), imageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            URL url = new URL("https://api.imgbb.com/1/upload?key=" + IMGBB_API_KEY);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.getOutputStream().write(("image=" + URLEncoder.encode(base64, "UTF-8")).getBytes());
            JSONObject json = new JSONObject(new Scanner(conn.getInputStream()).useDelimiter("\\A").next());
            return json.getJSONObject("data").getString("url");
        } catch (Exception e) { return null; }
    }

    private void saveToFirestore(String name, String desc, String time, String cat, Map<String, Double> vars, String url) {
        PrefManager pref = new PrefManager(requireContext());
        db.collection("Users").document("Restaurant").collection("VerifiedRegister").document(restId)
                .get().addOnSuccessListener(doc -> {
                    String restName = doc.getString("restaurantName");
                    String city = doc.getString("city");
                    if (pref.getUserCity() != null && !pref.getUserCity().isEmpty()) city = pref.getUserCity();

                    // ✅ UPDATED: Naye Constructor ke mutabiq object banaya gaya
                    com.example.paktrainfoodapp.ui.main.Restaurant.menu.MenuItem item = new com.example.paktrainfoodapp.ui.main.Restaurant.menu.MenuItem(
                            name,
                            desc,
                            url,
                            vars, // Map pass kiya
                            time,
                            cat,
                            restId,
                            restName,
                            city
                    );

                    if (editingItem != null) item.setId(editingItem.getId());
                    saveToCollection(item);
                });
    }

    private void saveToCollection(MenuItem item) {
        var docRef = db.collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(restId).collection("MenuItems");

        if (editingItem != null) {
            // Update existing
            docRef.document(item.getId()).set(item).addOnSuccessListener(a -> finishSave());
        } else {
            // Add new
            docRef.add(item).addOnSuccessListener(ref -> {
                item.setId(ref.getId()); // ID set karna
                ref.set(item).addOnSuccessListener(a -> finishSave()); // ID update ke sath final save
            });
        }
    }

    private void finishSave() {
        progressBar.setVisibility(View.GONE);
        if (listener != null) listener.onSaved();
        dismiss();
    }
}
//


