package com.example.paktrainfoodapp.ui.main.Restaurant.menu;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.List;

public class resturent_MenuFragment extends Fragment implements AddEditItemDialogFragment.Listener {

    private Button btnAddItem;
    private RecyclerView rvMenu;
    private TextView tvEmptyState;
    private ProgressBar progressBar;

    private MenuAdapter adapter;
    private final List<MenuItem> items = new ArrayList<>();

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String currentRestaurantId = "";

    // ✅ FIX: Listener registration variable to prevent memory leaks and crashes
    private ListenerRegistration menuListener;

    public resturent_MenuFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_resturent__menu, container, false);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentRestaurantId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        btnAddItem = v.findViewById(R.id.btnAddItem);
        rvMenu = v.findViewById(R.id.rvMenuItems);
        tvEmptyState = v.findViewById(R.id.tvEmpty_State);
        progressBar = v.findViewById(R.id.progressBar);

        rvMenu.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MenuAdapter(requireContext(), items, new MenuAdapter.OnItemActionListener() {
            @Override
            public void onEdit(MenuItem item) { openEditDialog(item); }
            @Override
            public void onDelete(MenuItem item) { confirmDelete(item); }

            @Override
            public void onAvailabilityChanged(MenuItem item, boolean available) {
                setItemAvailability(item, available);
            }
        });
        rvMenu.setAdapter(adapter);

        btnAddItem.setOnClickListener(view -> openAddDialog());

        loadMenuItems();
        return v;
    }

    private void loadMenuItems() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // ✅ FIX: Assign listener to the variable instead of just calling it
        menuListener = db.collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(currentRestaurantId)
                .collection("MenuItems")
                .addSnapshotListener((snapshots, e) -> {
                    // ✅ CRASH FIX: Safety check before updating UI
                    if (!isAdded() || getContext() == null) return;

                    if (progressBar != null) progressBar.setVisibility(View.GONE);

                    if (e != null) return;

                    if (snapshots != null) {
                        items.clear();
                        for (QueryDocumentSnapshot d : snapshots) {
                            MenuItem item = d.toObject(MenuItem.class);
                            item.setId(d.getId());
                            items.add(item);
                        }
                        adapter.notifyDataSetChanged();
                        tvEmptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    // ✅ FIX: Stop listening when Fragment is destroyed
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (menuListener != null) {
            menuListener.remove();
        }
    }

    /**
     * Marks a menu item available/unavailable. Unlike deleting, this keeps the
     * item so it can be switched back on later - passengers simply stop seeing
     * it while it is off.
     */
    private void setItemAvailability(MenuItem item, boolean available) {

        if (item.getId() == null) return;

        db.collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(currentRestaurantId)
                .collection("MenuItems").document(item.getId())
                .update("available", available)
                .addOnSuccessListener(unused -> {

                    if (!isAdded()) return;

                    Toast.makeText(requireContext(),
                            item.getName() + (available ? " is now available" : " hidden from menu"),
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    Toast.makeText(requireContext(),
                            "Could not update: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmDelete(MenuItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete \"" + item.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteItem(item))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteItem(MenuItem item) {
        db.collection("Users").document("Restaurant")
                .collection("VerifiedRegister").document(currentRestaurantId)
                .collection("MenuItems").document(item.getId())
                .delete();
    }

    private void openAddDialog() {
        AddEditItemDialogFragment dlg = AddEditItemDialogFragment.newInstance(currentRestaurantId, null, this);
        dlg.show(getParentFragmentManager(), "AddItem");
    }

    private void openEditDialog(MenuItem item) {
        AddEditItemDialogFragment dlg = AddEditItemDialogFragment.newInstance(currentRestaurantId, item, this);
        dlg.show(getParentFragmentManager(), "EditItem");
    }

    @Override
    public void onSaved() {
        // UI automatically updates due to snapshotListener
    }
}

//

