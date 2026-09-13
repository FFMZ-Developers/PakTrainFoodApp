package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.CartManager;
import com.example.paktrainfoodapp.R;

import java.util.ArrayList;

public class OrderSummaryFragment extends DialogFragment {

    private TextView tvItemsTotal, tvDeliveryFee, tvAdminFee, tvTotal;
    private RecyclerView rvItems;

    private double subtotal = 0;
    private double deliveryFee = 0;
    private double adminFee = 0;
    private double totalAmount = 0;

    private ArrayList<CartItem> cartItems;
    private OrderSummaryAdapter adapter;

    // ✅ FIX: this dialog never listened for CartManager changes at all -
    // tapping +/- correctly updated the underlying cart data (confirmed:
    // CartManager.increaseQuantity/decreaseQuantity both call
    // notifyChange()), but nothing here ever refreshed the RecyclerView or
    // recalculated the totals, so the screen looked completely
    // unresponsive. Now this listens the same way the cart screen itself
    // does, and detaches in onDestroyView to avoid leaking a callback
    // into a dead fragment.
    private final Runnable cartChangeListener = this::onCartChanged;

    public static OrderSummaryFragment newInstance(ArrayList<CartItem> items) {
        OrderSummaryFragment fragment = new OrderSummaryFragment();

        Bundle bundle = new Bundle();
        bundle.putSerializable("cartItems", items);

        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            cartItems = (ArrayList<CartItem>) getArguments().getSerializable("cartItems");
        }

        if (cartItems == null) {
            cartItems = new ArrayList<>();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_order_summary, container, false);

        tvItemsTotal = view.findViewById(R.id.tvItemsTotal);
        tvDeliveryFee = view.findViewById(R.id.tvDeliveryFee);
        tvAdminFee = view.findViewById(R.id.tvAdminFee);
        tvTotal = view.findViewById(R.id.tvTotal);

        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        rvItems = view.findViewById(R.id.rvItems);
        rvItems.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new OrderSummaryAdapter(cartItems);
        rvItems.setAdapter(adapter);

        CartManager.addListener(cartChangeListener);

        calculateBill();

        btnCancel.setOnClickListener(v -> dismiss());

        btnConfirm.setOnClickListener(v -> {

            if (cartItems.isEmpty()) {
                dismiss();
                return;
            }

            CartItem first = cartItems.get(0);

            OrderNowFragment dialog =
                    OrderNowFragment.newInstance(
                            first.getName(),
                            totalAmount, // ✅ ONLY ONE TOTAL
                            "Multiple Items",
                            first.getRestaurantName(),
                            first.getRestaurantId(),
                            first.getImageUrl(),
                            first.getMealStation(),
                            first.getTrainId(),
                            first.getRouteId(),
                            first.getFromStation(),
                            first.getToStation(),
                            cartItems,
                            subtotal,
                            deliveryFee,
                            adminFee
                    );

            dialog.show(getParentFragmentManager(), "OrderNow");

            dismiss();
        });

        return view;
    }

    private void calculateBill() {

        subtotal = CartManager.getTotalPrice();

        deliveryFee = 150;
        adminFee = subtotal * 0.05;

        totalAmount = subtotal + deliveryFee + adminFee;

        tvItemsTotal.setText("Items Total: Rs " + (int) subtotal);
        tvDeliveryFee.setText("Delivery Fee: Rs " + (int) deliveryFee);
        tvAdminFee.setText("Admin Fee: Rs " + (int) adminFee);
        tvTotal.setText("Total Amount: Rs " + (int) totalAmount);
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();

        if (dialog != null && dialog.getWindow() != null) {

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);

            dialog.getWindow().setLayout(width, height);
        }
    }

    /**
     * Fired by CartManager whenever a quantity actually changes - redraws
     * the list AND recalculates the bill, since a quantity change moves
     * both.
     */
    private void onCartChanged() {

        if (!isAdded() || adapter == null) return;

        adapter.notifyDataSetChanged();
        calculateBill();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        CartManager.removeListener(cartChangeListener);
    }
}




//package com.example.paktrainfoodapp.ui.main.Passenger;
//
//import android.app.Dialog;
//import android.os.Bundle;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.Window;
//
//import android.widget.Button;
//import android.widget.TextView;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.fragment.app.DialogFragment;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.example.paktrainfoodapp.CartManager;
//import com.example.paktrainfoodapp.R;
//
//import java.util.ArrayList;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//public class OrderSummaryFragment extends DialogFragment {
//
//    private TextView tvItemsTotal;
//    private TextView tvDeliveryFee;
//    private TextView tvAdminFee;
//    private TextView tvTotal;
//
//    private double subtotal;
//    private double deliveryFee;
//    private double adminFee;
//    private double total;
//    private RecyclerView rvItems;
//
//    private ArrayList<CartItem> cartItems;
//
//    public static OrderSummaryFragment newInstance(
//            ArrayList<CartItem> items
//    ) {
//
//        OrderSummaryFragment fragment =
//                new OrderSummaryFragment();
//
//        Bundle bundle = new Bundle();
//
//        bundle.putSerializable(
//                "cartItems",
//                items
//        );
//
//        fragment.setArguments(bundle);
//
//        return fragment;
//    }
//
//    @Override
//    public Dialog onCreateDialog(Bundle savedInstanceState) {
//
//        Dialog dialog = super.onCreateDialog(savedInstanceState);
//        if (getArguments() != null) {
//
//            cartItems =
//                    (ArrayList<CartItem>)
//                            getArguments().getSerializable("cartItems");
//        }
//
//        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
//
//        return dialog;
//    }
//
//    @Nullable
//    @Override
//    public View onCreateView(
//            @NonNull LayoutInflater inflater,
//            @Nullable ViewGroup container,
//            @Nullable Bundle savedInstanceState) {
//
//        View view = inflater.inflate(
//                R.layout.fragment_order_summary,
//                container,
//                false
//        );
//
//        tvItemsTotal = view.findViewById(R.id.tvItemsTotal);
//        tvDeliveryFee = view.findViewById(R.id.tvDeliveryFee);
//        tvAdminFee = view.findViewById(R.id.tvAdminFee);
//        tvTotal = view.findViewById(R.id.tvTotal);
//
//        Button btnCancel = view.findViewById(R.id.btnCancel);
//        Button btnConfirm = view.findViewById(R.id.btnConfirm);
//        rvItems =
//                view.findViewById(R.id.rvItems);
//
//        rvItems.setLayoutManager(
//                new LinearLayoutManager(getContext())
//        );
//
//        rvItems.setAdapter(
//                new OrderSummaryAdapter(cartItems)
//        );
//        calculateBill();
//
//        btnCancel.setOnClickListener(v -> dismiss());
//
//        btnConfirm.setOnClickListener(v -> {
//
//            if (cartItems == null || cartItems.isEmpty()) {
//                dismiss();
//                return;
//            }
//
//            CartItem first = cartItems.get(0);
//
//            OrderNowFragment dialog =
//                    OrderNowFragment.newInstance(
//                            first.getName(),
//                            total,
//                            "Multiple Items",
//                            first.getRestaurantName(),
//                            first.getRestaurantId(),
//                            first.getImageUrl(),
//                            first.getMealStation(),
//                            first.getTrainId(),
//                            first.getRouteId(),
//                            first.getFromStation(),
//                            first.getToStation(),
//                            cartItems,
//                            subtotal, deliveryFee, adminFee
//                    );
//
//            dialog.show(
//                    getParentFragmentManager(),
//                    "OrderNow"
//            );
//
//            dismiss();
//        });
//
//        return view;
//    }
//
//    private void calculateBill() {
//
//        subtotal = CartManager.getTotalPrice();
//
//        deliveryFee = 150;
//
//        adminFee = subtotal * 0.05;
//
//        total =
//                subtotal +
//                        deliveryFee +
//                        adminFee;
//
//        tvItemsTotal.setText(
//                "Items Total: Rs. " + (int) subtotal
//        );
//
//        tvDeliveryFee.setText(
//                "Delivery Fee: Rs. " + (int) deliveryFee
//        );
//
//        tvAdminFee.setText(
//                "Admin Fee: Rs. " + (int) adminFee
//        );
//
//        tvTotal.setText(
//                "Total: Rs. " + (int) total
//        );
//    }
//    @Override
//    public void onStart() {
//        super.onStart();
//
//        Dialog dialog = getDialog();
//
//        if (dialog != null && dialog.getWindow() != null) {
//
//            int width = (int) (getResources()
//                    .getDisplayMetrics()
//                    .widthPixels * 0.95);
//
//            int height = (int) (getResources()
//                    .getDisplayMetrics()
//                    .heightPixels * 0.85);
//
//            dialog.getWindow().setLayout(
//                    width,
//                    height
//            );
//        }
//    }
//}