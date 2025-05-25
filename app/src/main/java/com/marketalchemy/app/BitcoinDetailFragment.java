package com.marketalchemy.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class BitcoinDetailFragment extends Fragment {

    private ImageView imageBack;
    private TextView textPrice;
    private TextView textPriceChange;
    private ImageView imageChart;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bitcoin_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        imageBack = view.findViewById(R.id.imageBack);
        textPrice = view.findViewById(R.id.textPrice);
        textPriceChange = view.findViewById(R.id.textPriceChange);
        imageChart = view.findViewById(R.id.imageChart);
        
        imageBack.setOnClickListener(v -> navigateBack());
        
        updatePriceData();
    }
    
    private void updatePriceData() {
        textPrice.setText("$29,341.58");
        textPriceChange.setText("+3.42% (24h)");
        textPriceChange.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
    }
    
    private void navigateBack() {
        getParentFragmentManager().popBackStack();
    }
    
    public static void navigate(Fragment currentFragment) {
        FragmentTransaction transaction = currentFragment.getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, new BitcoinDetailFragment());
        transaction.addToBackStack(null);
        transaction.commit();
    }
} 