package com.example.paktrainfoodapp.ui.main.notification;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;

import java.util.ArrayList;
import java.util.List;

public class NotificationFragment extends Fragment {

    /**
     * Which role's notifications to show. Defaults to PASSENGER so existing
     * callers keep working; restaurant and delivery pass their own role via
     * {@link #newInstance(String)}.
     */
    private static final String ARG_ROLE = "notification_role";

    public static NotificationFragment newInstance(String role) {

        NotificationFragment fragment = new NotificationFragment();

        Bundle args = new Bundle();
        args.putString(ARG_ROLE, role);
        fragment.setArguments(args);

        return fragment;
    }

    private String role() {

        return getArguments() != null
                ? getArguments().getString(ARG_ROLE, NotificationRepository.ROLE_PASSENGER)
                : NotificationRepository.ROLE_PASSENGER;
    }


    private RecyclerView recyclerNotifications;
    private ProgressBar progressBar;
    private LinearLayout layoutEmpty;
    private Toolbar toolbar;

    private NotificationAdapter adapter;
    private final List<NotificationModel> notificationList = new ArrayList<>();


    private NotificationRepository repository;

    public NotificationFragment() {
        super(R.layout.fragment_notification);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        recyclerNotifications = view.findViewById(R.id.recyclerNotifications);
        progressBar = view.findViewById(R.id.progressBar);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        toolbar = view.findViewById(R.id.toolbarNotification);

        adapter = new NotificationAdapter(

                requireContext(),
                notificationList,

                new NotificationAdapter.NotificationClickListener() {

                    @Override
                    public void onOrderClick(NotificationModel model) {

                        openNotificationDetail(model);

                    }

//            @Override
//            public void onWalletClick(NotificationModel model) {
//
//            }
//
//            @Override
//            public void onProfileClick(NotificationModel model) {
//
//            }
//
//            @Override
//            public void onRestaurantClick(NotificationModel model) {
//
//            }
//
//            @Override
//            public void onOfferClick(NotificationModel model) {
//
//            }

                }

        );
        recyclerNotifications.setLayoutManager(
                new LinearLayoutManager(requireContext()));

        recyclerNotifications.setAdapter(adapter);

        repository = new NotificationRepository();



        toolbar.setOnMenuItemClickListener(item -> {

            if (item.getItemId() == R.id.action_clear_all) {

                confirmClearAll();

                return true;
            }

            return false;
        });

        setupSwipeToDelete();

        startRealtimeNotifications();

    }

    private void confirmClearAll() {

        if (notificationList.isEmpty()) {

            Toast.makeText(requireContext(), "No notifications to clear", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Clear all notifications?")
                .setMessage("This will delete all your notifications. This cannot be undone.")
                .setPositiveButton("Clear All", (dialog, which) -> {

                    repository.deleteAllNotifications(
                            role(),
                            new NotificationRepository.SimpleCallback() {

                                @Override
                                public void onSuccess() {

                                    if (!isAdded()) return;

                                    Toast.makeText(requireContext(),
                                            "All notifications cleared",
                                            Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure(Exception e) {

                                    if (!isAdded()) return;

                                    Toast.makeText(requireContext(),
                                            "Failed to clear: " + e.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Lets the user swipe a notification left or right to delete it individually.
     */
    private void setupSwipeToDelete() {

        ItemTouchHelper.SimpleCallback callback =
                new ItemTouchHelper.SimpleCallback(
                        0,
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

                        int position = viewHolder.getAdapterPosition();

                        if (position < 0 || position >= notificationList.size()) {
                            adapter.notifyDataSetChanged();
                            return;
                        }

                        NotificationModel removed = notificationList.get(position);

                        notificationList.remove(position);
                        adapter.notifyItemRemoved(position);
                        updateUI();

                        repository.deleteNotification(
                                role(),
                                removed.getDocumentId(),
                                new NotificationRepository.SimpleCallback() {

                                    @Override
                                    public void onSuccess() {
                                        // Realtime listener will keep list in sync;
                                        // nothing further needed here.
                                    }

                                    @Override
                                    public void onFailure(Exception e) {

                                        if (!isAdded()) return;

                                        Toast.makeText(requireContext(),
                                                "Failed to delete: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerNotifications);
    }

    // ✅ FIX: this used to jump straight to the order (openOrder()) - no
    // way to actually read the message first, and every notification
    // behaved the same regardless of type. Now every tap - from this
    // in-app list or from the system tray - opens the same expanded
    // detail screen (NotificationDetailFragment), which then offers
    // whatever action button actually fits the message.
    private void openNotificationDetail(NotificationModel model) {

        if (!isAdded() || model.getDocumentId() == null) return;

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_container,
                        NotificationDetailFragment.newInstanceFromId(model.getDocumentId()))
                .addToBackStack("notification_detail")
                .commit();
    }

    private void openOrder(NotificationModel model) {

        if (!(getParentFragment() instanceof com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader))
            return;

        com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader loader =
                (com.example.paktrainfoodapp.ui.main.Passenger.Passenger_Fragment_Loader) getParentFragment();

        String status = model.getStatus();

        int tab = 0;

        if ("Active".equalsIgnoreCase(status)) {

            tab = 0;

        } else if ("Accepted".equalsIgnoreCase(status)) {

            tab = 1;

        } else if ("Delivered".equalsIgnoreCase(status)) {

            tab = 2;

        } else if ("Completed".equalsIgnoreCase(status)) {

            tab = 3;

        }

        loader.navigateToOrders(tab);

        loader.openOrderDetail(model.getOrderId());

    }

    private void startRealtimeNotifications() {

        progressBar.setVisibility(View.VISIBLE);

        repository.listenNotifications(
                role(),
                new NotificationRepository.NotificationRealtimeCallback() {

                    @Override
                    public void onChanged(List<NotificationModel> list) {

                        notificationList.clear();

                        notificationList.addAll(list);

                        adapter.notifyDataSetChanged();

                        updateUI();

                    }

                    @Override
                    public void onFailure(Exception e) {

                        progressBar.setVisibility(View.GONE);

                        Toast.makeText(
                                requireContext(),
                                e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();

                    }

                });

    }
    @Override
    public void onDestroyView() {

        super.onDestroyView();

        if (repository != null) {
            repository.removeListener();
        }
    }


    private void updateUI() {

        if (notificationList.isEmpty()) {

            layoutEmpty.setVisibility(View.VISIBLE);
            recyclerNotifications.setVisibility(View.GONE);

        } else {

            layoutEmpty.setVisibility(View.GONE);
            recyclerNotifications.setVisibility(View.VISIBLE);

        }

        progressBar.setVisibility(View.GONE);
    }

}