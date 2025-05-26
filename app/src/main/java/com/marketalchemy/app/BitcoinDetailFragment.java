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

    private static final String ARG_CRYPTO_SYMBOL = "crypto_symbol";
    private static final String ARG_CRYPTO_NAME = "crypto_name";
    
    private String cryptoSymbol = "BTC"; // Default to Bitcoin
    private String cryptoName = "Bitcoin"; // Default to Bitcoin
    
    private ImageView imageBack;
    private TextView textPrice;
    private TextView textPriceChange;
    private ImageView imageChart;
    private TextView textCryptoTitle;

    /**
     * Creates a new instance of BitcoinDetailFragment with the given crypto symbol and name
     * @param cryptoSymbol The symbol of the cryptocurrency (e.g., "BTC", "ETH")
     * @param cryptoName The name of the cryptocurrency (e.g., "Bitcoin", "Ethereum")
     * @return A new instance of BitcoinDetailFragment
     */
    public static BitcoinDetailFragment newInstance(String cryptoSymbol, String cryptoName) {
        BitcoinDetailFragment fragment = new BitcoinDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CRYPTO_SYMBOL, cryptoSymbol);
        args.putString(ARG_CRYPTO_NAME, cryptoName);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            cryptoSymbol = getArguments().getString(ARG_CRYPTO_SYMBOL, "BTC");
            cryptoName = getArguments().getString(ARG_CRYPTO_NAME, "Bitcoin");
        }
    }

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
        textCryptoTitle = view.findViewById(R.id.textCryptoTitle);
        TextView textAboutTitle = view.findViewById(R.id.textAboutTitle);
        
        // Set the crypto title with the provided symbol and name
        textCryptoTitle.setText(String.format("%s (%s)", cryptoName, cryptoSymbol));
        
        // Set the About section title dynamically
        if (textAboutTitle != null) {
            textAboutTitle.setText(String.format("About %s", cryptoName));
        }
        
        imageBack.setOnClickListener(v -> navigateBack());
        
        updatePriceData();
    }
    
    private void updatePriceData() {
        // In a real app, this would fetch the price for the specific crypto
        // For now, we'll use dummy data
        textPrice.setText("$29,341.58");
        textPriceChange.setText("+3.42% (24h)");
        textPriceChange.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
    }
    
    private void navigateBack() {
        getParentFragmentManager().popBackStack();
    }
    
    /**
     * Navigate to the Bitcoin detail fragment with the specified crypto symbol and name
     * @param currentFragment The current fragment
     * @param cryptoSymbol The symbol of the cryptocurrency (e.g., "BTC", "ETH")
     * @param cryptoName The name of the cryptocurrency (e.g., "Bitcoin", "Ethereum")
     */
    public static void navigate(Fragment currentFragment, String cryptoSymbol, String cryptoName) {
        FragmentTransaction transaction = currentFragment.getParentFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, newInstance(cryptoSymbol, cryptoName));
        transaction.addToBackStack(null);
        transaction.commit();
    }
}