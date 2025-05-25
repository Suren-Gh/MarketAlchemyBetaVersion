package com.marketalchemy.app;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.marketalchemy.app.api.BybitApiClient;
import com.marketalchemy.app.api.BybitUpdateClient;
import com.marketalchemy.app.model.Investment;
import com.marketalchemy.app.model.VirtualPortfolio;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import org.json.JSONException;

public class HomeFragment extends Fragment {

    private TextView tvUsername;
    private TextView tvPortfolioValue;
    private TextView tvProfitLoss;
    private TextView tvBitcoinHoldings;
    private TextView tvBitcoinValue;
    private TextView tvBitcoinPrice;
    private TextView tvWalletBalance;
    private ProgressBar progressBar;
    private LinearLayout cryptoAssetsContainer;
    private ImageView ivNotifications;
    private FirebaseAuth mAuth;
    private VirtualPortfolio portfolio;
    
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final BybitUpdateClient updateClient = BybitUpdateClient.getInstance();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize portfolio
        portfolio = new VirtualPortfolio(requireContext());
        
        // Initialize views
        tvUsername = view.findViewById(R.id.tvUsername);
        tvPortfolioValue = view.findViewById(R.id.tvPortfolioValue);
        tvProfitLoss = view.findViewById(R.id.tvProfitLoss);
        tvBitcoinPrice = view.findViewById(R.id.tvBitcoinPrice);
        progressBar = view.findViewById(R.id.progressBar);
        ivNotifications = view.findViewById(R.id.ivNotifications);
        
        // Initialize new UI elements
        tvBitcoinHoldings = view.findViewById(R.id.tvBitcoinHoldings);
        tvBitcoinValue = view.findViewById(R.id.tvBitcoinValue);
        tvWalletBalance = view.findViewById(R.id.tvWalletBalance);
        cryptoAssetsContainer = view.findViewById(R.id.cryptoAssetsContainer);
        
        // Set crypto-themed colors
        int cryptoGreen = Color.parseColor("#4CAF50");
        tvPortfolioValue.setTextColor(cryptoGreen);
        
        // Set click listener for profile button
        ivNotifications.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to profile fragment
                if (getActivity() != null) {
                    ((MainActivity) getActivity()).navigateToProfile();
                }
            }
        });
        
        // Set up Bybit update client to track Bitcoin prices
        updateClient.trackSymbol("BTC", new BybitUpdateClient.PriceUpdateListener() {
            @Override
            public void onPriceUpdate(String symbol, double price, double change) {
                updateHandler.post(() -> updateBitcoinDisplay(price));
            }
        });
        
        // Update UI with user and portfolio data
        updateUserInfo();
        updatePortfolioInfo();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Update portfolio data when returning to this fragment
        updatePortfolioInfo();
        
        // Start Bitcoin price updates
        startPriceUpdates();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Stop price updates when fragment is not visible
        stopPriceUpdates();
    }
    
    private void startPriceUpdates() {
        // Set up regular updates for crypto prices (every 1 second)
        updateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // Update all supported cryptocurrencies
                    updateCryptoHoldings();
                    updatePortfolioInfo(); // Refresh all values
                    
                    // Schedule next update
                    updateHandler.postDelayed(this, 1000); // Real-time 1-second updates
                } catch (Exception e) {
                    Log.e("HomeFragment", "Error updating prices: " + e.getMessage());
                }
            }
        }, 500); // Initial delay of 0.5 second
    }
    
    private void stopPriceUpdates() {
        // Remove all pending updates
        updateHandler.removeCallbacksAndMessages(null);
    }
    
    private void updateUserInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                // Extract username if available
                if (displayName.contains("(") && displayName.contains(")")) {
                    String username = displayName.substring(
                            displayName.indexOf("(") + 1, 
                            displayName.indexOf(")")
                    );
                    tvUsername.setText("@" + username);
                } else {
                    tvUsername.setText(displayName);
                }
            }
        }
    }
    
    private void updatePortfolioInfo() {
        // Show loading state
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        
        // Get balance (this is safe to do on main thread)
        double walletBalance = portfolio.getBalance();
        
        // Update wallet balance immediately (this doesn't need network calls)
        tvWalletBalance.setText(currencyFormat.format(walletBalance));
        
        // Use cached values for immediate display
        double cachedTotalValue = portfolio.getTotalPortfolioValue(); // Uses cached values
        tvPortfolioValue.setText(currencyFormat.format(cachedTotalValue));
        
        // Then get accurate values asynchronously
        portfolio.getTotalPortfolioValueAsync(totalValue -> {
            if (!isAdded() || getContext() == null) {
                return; // Fragment not attached, avoid crashes
            }
            
            // Update portfolio value with fresh data
            tvPortfolioValue.setText(currencyFormat.format(totalValue));
            
            // Update profit/loss information if available
            if (tvProfitLoss != null) {
                double profitLoss = portfolio.getTotalProfitLoss();
                double profitLossPercent = 0;
                if (totalValue > walletBalance) {
                    profitLossPercent = (profitLoss / (totalValue - walletBalance)) * 100;
                }
                
                String profitLossText = String.format("%s (%.1f%%)", 
                        currencyFormat.format(profitLoss), 
                        profitLossPercent);
                
                tvProfitLoss.setText(profitLossText);
                
                // Set crypto-themed colors
                int cryptoGreen = Color.parseColor("#4CAF50");
                int cryptoRed = Color.parseColor("#F44336");
                
                // Set color based on profit/loss
                if (profitLoss >= 0) {
                    tvProfitLoss.setTextColor(cryptoGreen);
                } else {
                    tvProfitLoss.setTextColor(cryptoRed);
                }
            }
            
            // Update Bitcoin price asynchronously
            updateBitcoinPrice();
            
            // Hide loading state
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }
    
    private void updateBitcoinPrice() {
        if (!isAdded() || getContext() == null) {
            return; // Fragment not attached, avoid crashes
        }
        
        // Then get fresh data asynchronously
        BybitApiClient.getInstance().getCurrentPriceAsync("BTC", new BybitApiClient.PriceCallback() {
            @Override
            public void onPrice(double price, double change) {
                if (!isAdded() || getContext() == null) {
                    return; // Fragment not attached, avoid crashes
                }
                
                if (tvBitcoinPrice != null) {
                    tvBitcoinPrice.setText(currencyFormat.format(price));
                }
                
                // Update Bitcoin holdings with new price
                updateBitcoinDisplay(price);
            }
            
            @Override
            public void onError(Exception e) {
                Log.e("HomeFragment", "Error getting Bitcoin price: " + e.getMessage());
            }
        });
        
        // Update all crypto holdings display
        updateCryptoHoldings();
    }
    
    /**
     * Updates the Bitcoin display with the current price
     */
    private void updateBitcoinDisplay(double price) {
        // Get Bitcoin investment if it exists
        Investment bitcoinInvestment = portfolio.getInvestment("BTC");
        if (bitcoinInvestment != null) {
            double quantity = bitcoinInvestment.getQuantity();
            double value = quantity * price;
            
            // Format the BTC amount with 8 decimal places (standard for BTC)
            String formattedBtc = String.format(Locale.US, "%.8f BTC", quantity);
            tvBitcoinHoldings.setText(formattedBtc);
            
            // Format the value with currency symbol
            tvBitcoinValue.setText(currencyFormat.format(value));
        } else {
            // No Bitcoin holdings
            tvBitcoinHoldings.setText("0.00000000 BTC");
            tvBitcoinValue.setText(currencyFormat.format(0));
        }
    }
    
    /**
     * Updates the crypto holdings display in the UI
     */
    private void updateCryptoHoldings() {
        // Clear existing views
        if (cryptoAssetsContainer != null) {
            cryptoAssetsContainer.removeAllViews();
        }
        
        // Get all investments
        List<Investment> investments = portfolio.getInvestments();
        
        // Define crypto names mapping
        Map<String, String> cryptoNames = new HashMap<>();
        cryptoNames.put("BTC", "Bitcoin");
        cryptoNames.put("ETH", "Ethereum");
        cryptoNames.put("SOL", "Solana");
        cryptoNames.put("BNB", "Binance Coin");
        cryptoNames.put("XRP", "Ripple");
        cryptoNames.put("ADA", "Cardano");
        
        try {
            // For each investment, create a card and add it to the container
            for (Investment investment : investments) {
                String cryptoId = investment.getCryptoId();
                double quantity = investment.getQuantity();
                
                // Skip if quantity is too small
                if (quantity < 0.00000001) continue;
                
                // Get crypto name and symbol
                String cryptoName = cryptoNames.getOrDefault(cryptoId, cryptoId);
                
                // Get current price asynchronously
                BybitApiClient.getInstance().getCurrentPriceAsync(cryptoId, new BybitApiClient.PriceCallback() {
                    @Override
                    public void onPrice(double price, double change) {
                        if (!isAdded() || getContext() == null) return;
                        
                        double value = quantity * price;
                        
                        // Create crypto holding card
                        View cardView = createCryptoCard(cryptoName, cryptoId, quantity, price, value);
                        
                        // Add to UI on main thread
                        updateHandler.post(() -> {
                            if (cryptoAssetsContainer != null && isAdded()) {
                                cryptoAssetsContainer.addView(cardView);
                            }
                        });
                    }
                    
                    @Override
                    public void onError(Exception e) {
                        Log.e("HomeFragment", "Error getting price for " + cryptoId + ": " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            // Handle error
            Log.e("HomeFragment", "Error updating crypto holdings: " + e.getMessage());
        }
    }
    
    /**
     * Creates a card view for a crypto holding
     */
    private View createCryptoCard(String cryptoName, String symbol, double quantity, double price, double value) {
        // Create card layout
        CardView cardView = new CardView(requireContext());
        cardView.setCardBackgroundColor(Color.parseColor("#1E1E1E"));
        cardView.setRadius(16);
        cardView.setCardElevation(4);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(16, 8, 16, 8);
        cardView.setLayoutParams(cardParams);
        
        // Create content layout
        LinearLayout contentLayout = new LinearLayout(requireContext());
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(24, 16, 24, 16);
        
        // Crypto name and symbol
        TextView tvCryptoName = new TextView(requireContext());
        tvCryptoName.setText(cryptoName + " (" + symbol + ")");
        tvCryptoName.setTextSize(18);
        tvCryptoName.setTextColor(Color.WHITE);
        contentLayout.addView(tvCryptoName);
        
        // Holdings layout (quantity and value)
        LinearLayout holdingsLayout = new LinearLayout(requireContext());
        holdingsLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        // Quantity
        TextView tvQuantity = new TextView(requireContext());
        tvQuantity.setText(String.format(Locale.US, "%.8f %s", quantity, symbol.toUpperCase()));
        tvQuantity.setTextSize(16);
        tvQuantity.setTextColor(Color.parseColor("#4CAF50"));
        LinearLayout.LayoutParams quantityParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvQuantity.setLayoutParams(quantityParams);
        holdingsLayout.addView(tvQuantity);
        
        // Value
        TextView tvValue = new TextView(requireContext());
        tvValue.setText(currencyFormat.format(value));
        tvValue.setTextSize(16);
        tvValue.setTextColor(Color.WHITE);
        tvValue.setGravity(android.view.Gravity.END);
        holdingsLayout.addView(tvValue);
        
        contentLayout.addView(holdingsLayout);
        
        // Price
        TextView tvPrice = new TextView(requireContext());
        tvPrice.setText("Price: " + currencyFormat.format(price));
        tvPrice.setTextSize(14);
        tvPrice.setTextColor(Color.LTGRAY);
        contentLayout.addView(tvPrice);
        
        cardView.addView(contentLayout);
        return cardView;
    }
} 