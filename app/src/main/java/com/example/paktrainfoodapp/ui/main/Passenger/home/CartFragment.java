package com.example.paktrainfoodapp.ui.main.Passenger.home;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.paktrainfoodapp.CartManager;

import java.util.ArrayList;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.paktrainfoodapp.R;


public class CartFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private RecyclerView rvCart;
    private TextView tvCartTotal;
    private Button btnContinueOrder;

    private ArrayList<CartItem> cartItems;
    public CartFragment() {
        // Required empty public constructor
    }


    // TODO: Rename and change types and number of parameters
    public static CartFragment newInstance(String param1, String param2) {
        CartFragment fragment = new CartFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view =
                inflater.inflate(
                        R.layout.fragment_passanger_cart,
                        container,
                        false
                );

        rvCart = view.findViewById(R.id.rvCart);

        tvCartTotal =
                view.findViewById(R.id.tvCartTotal);

        btnContinueOrder =
                view.findViewById(R.id.btnContinueOrder);

        rvCart.setLayoutManager(
                new LinearLayoutManager(getContext())
        );

        loadCart();

        return view;
    }
    private void loadCart() {

        cartItems =
                new ArrayList<>(CartManager.getCartItems());

        rvCart.setAdapter(
                new OrderSummaryAdapter(cartItems)
        );

        tvCartTotal.setText(
                "Total : Rs "
                        + (int) CartManager.getTotalPrice()
        );

    }
}//