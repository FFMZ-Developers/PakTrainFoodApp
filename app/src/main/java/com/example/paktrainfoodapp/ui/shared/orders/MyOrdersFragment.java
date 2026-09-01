package com.example.paktrainfoodapp.ui.shared.orders;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.R;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full order history for the logged-in passenger, with a coloured status
 * badge on each row and chips to filter by pending / ongoing / completed /
 * cancelled.
 */
public class MyOrdersFragment extends Fragment {

    public static final String ROLE_PASSENGER = "PASSENGER";
    public static final String ROLE_RESTAURANT = "RESTAURANT";
    public static final String ROLE_DELIVERY = "DELIVERY";

    private static final String ARG_ROLE = "orders_role";

    public static MyOrdersFragment newInstance(String role) {

        MyOrdersFragment fragment = new MyOrdersFragment();

        Bundle args = new Bundle();
        args.putString(ARG_ROLE, role);
        fragment.setArguments(args);

        return fragment;
    }

    private String role() {
        return getArguments() != null
                ? getArguments().getString(ARG_ROLE, ROLE_PASSENGER)
                : ROLE_PASSENGER;
    }

    /** Each role finds "their" orders through a different field. */
    private String ownerField() {

        switch (role()) {
            case ROLE_RESTAURANT: return "restaurantId";
            case ROLE_DELIVERY:   return "acceptedBy";
            default:              return "passengerUid";
        }
    }

    private String screenTitle() {

        switch (role()) {
            case ROLE_RESTAURANT: return "Restaurant Orders";
            case ROLE_DELIVERY:   return "My Deliveries";
            default:              return "My Orders";
        }
    }


    private RecyclerView recycler;
    private ProgressBar progressBar;
    private LinearLayout layoutNoOrders;
    private TextView txtNoOrdersHint;
    private ChipGroup chipGroup;

    private final List<MyOrderModel> allOrders = new ArrayList<>();
    private final List<MyOrderModel> visibleOrders = new ArrayList<>();

    private MyOrdersAdapter adapter;
    private ListenerRegistration registration;

    private String activeFilter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_my_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recycler = view.findViewById(R.id.recyclerMyOrders);
        progressBar = view.findViewById(R.id.progressMyOrders);
        layoutNoOrders = view.findViewById(R.id.layoutNoOrders);
        txtNoOrdersHint = view.findViewById(R.id.txtNoOrdersHint);
        chipGroup = view.findViewById(R.id.chipGroupFilter);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (isAdded()) getParentFragmentManager().popBackStack();
        });

        TextView title = view.findViewById(R.id.txtOrdersTitle);
        if (title != null) title.setText(screenTitle());

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new MyOrdersAdapter(visibleOrders, order -> {
            // Detail screen isn't wired for history yet - keep it informative
            // rather than silently doing nothing on tap.
            Toast.makeText(requireContext(),
                    "Order #" + order.getOrderId(),
                    Toast.LENGTH_SHORT).show();
        });

        recycler.setAdapter(adapter);

        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {

            if (checkedId == R.id.chipPending)        activeFilter = "pending";
            else if (checkedId == R.id.chipOngoing)   activeFilter = "ongoing";
            else if (checkedId == R.id.chipCompleted) activeFilter = "completed";
            else if (checkedId == R.id.chipCancelled) activeFilter = "cancelled";
            else                                      activeFilter = "all";

            applyFilter();
        });

        listenOrders();
    }

    private void listenOrders() {

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            showEmpty("Please log in to see your orders");
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        progressBar.setVisibility(View.VISIBLE);

        // Deliberately no orderBy() here: combining a where() with an orderBy()
        // on a different field needs a composite index, which would make this
        // screen fail on a fresh Firebase project. Sorting is done in memory
        // instead, which is fine for a single passenger's order history.
        registration = FirebaseFirestore.getInstance()
                .collection("Orders")
                .whereEqualTo(ownerField(), uid)
                .addSnapshotListener((snapshot, error) -> {

                    if (!isAdded()) return;

                    progressBar.setVisibility(View.GONE);

                    if (error != null) {
                        showEmpty("Could not load orders: " + error.getMessage());
                        return;
                    }

                    if (snapshot == null) {
                        showEmpty("Your orders will appear here");
                        return;
                    }

                    allOrders.clear();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        String orderId = doc.getString("orderId");
                        if (orderId == null) orderId = doc.getId();

                        String status = doc.getString("orderStatus");
                        String restaurant = doc.getString("restaurantName");
                        String station = doc.getString("mealStation");

                        Double total = doc.getDouble("totalPrice");
                        if (total == null) total = 0.0;

                        Long timestamp = doc.getLong("timestamp");

                        String dateText = "";
                        if (timestamp != null && timestamp > 0) {
                            dateText = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                    .format(new Date(timestamp));
                        }

                        MyOrderModel mo = new MyOrderModel(
                                orderId, status, restaurant, station, dateText, total);
                        mo.setOrderNumber(doc.getLong("orderNumber"));
                        allOrders.add(mo);
                    }

                    // Newest first
                    Collections.sort(allOrders, (a, b) ->
                            b.getDateText().compareTo(a.getDateText()));

                    applyFilter();
                });
    }

    private void applyFilter() {

        visibleOrders.clear();

        for (MyOrderModel order : allOrders) {

            if (activeFilter.equals("all")
                    || MyOrdersAdapter.bucketOf(order.getStatus()).equals(activeFilter)) {

                visibleOrders.add(order);
            }
        }

        adapter.notifyDataSetChanged();

        if (visibleOrders.isEmpty()) {

            showEmpty(activeFilter.equals("all")
                    ? "Your orders will appear here"
                    : "No " + activeFilter + " orders");

        } else {

            layoutNoOrders.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
    }

    private void showEmpty(String hint) {

        if (!isAdded()) return;

        recycler.setVisibility(View.GONE);
        layoutNoOrders.setVisibility(View.VISIBLE);
        txtNoOrdersHint.setText(hint);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
