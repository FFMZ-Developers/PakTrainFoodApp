package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.paktrainfoodapp.CartManager;
import com.example.paktrainfoodapp.R;

import java.util.ArrayList;

public class CartUIHelper {

    public static void setupCartBar(View view, Fragment fragment) {

        TextView tvCartTotal =
                view.findViewById(R.id.tvCartTotal);

        Button btnOrderNowCart =
                view.findViewById(R.id.btnOrderNowCart);

        Button btnClear =
                view.findViewById(R.id.btnClear);

        updateTotal(tvCartTotal);

        CartManager.addListener(() -> {

            if (tvCartTotal != null) {

                tvCartTotal.post(() -> {

                    updateTotal(tvCartTotal);
                });
            }
        });

        if (btnClear != null) {

            btnClear.setOnClickListener(v -> {

                if (CartManager.isEmpty()) {

                    Toast.makeText(
                            fragment.getContext(),
                            "Cart is already empty",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                CartManager.clear();

                Toast.makeText(
                        fragment.getContext(),
                        "Cart cleared",
                        Toast.LENGTH_SHORT
                ).show();
            });
        }

        btnOrderNowCart.setOnClickListener(v -> {

            ArrayList<CartItem> items =
                    new ArrayList<>(CartManager.getCartItems());

            if (items.isEmpty()) {

                Toast.makeText(
                        fragment.getContext(),
                        "Cart Empty",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            OrderSummaryFragment dialog =
                    OrderSummaryFragment.newInstance(items);

            dialog.show(
                    fragment.getParentFragmentManager(),
                    "OrderSummary"
            );
        });
    }

    private static void updateTotal(TextView tvCartTotal) {

        if (tvCartTotal == null) return;

        double total =
                CartManager.getTotalPrice();

        tvCartTotal.setText(
                "Total: Rs. " + (int) total
        );
    }
}









