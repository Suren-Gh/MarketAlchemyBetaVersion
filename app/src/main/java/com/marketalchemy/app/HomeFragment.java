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
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.ProgressBar;
import android.content.res.ColorStateList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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

public class HomeFragment extends Fragment {

    private TextView tvUsername;
    private TextView tvPortfolioValue;
    private TextView tvProfitLoss;
    private TextView tvBitcoinHoldings;
    private TextView tvBitcoinValue;
    private TextView tvBitcoinPrice;
    private TextView tvWalletBalance;
    private ProgressBar progressBar;
    private TableLayout cryptoTableLayout;
    private ImageView ivNotifications;
    private FirebaseAuth mAuth;
    private VirtualPortfolio portfolio;
    
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final BybitUpdateClient updateClient = BybitUpdateClient.getInstance();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
    
    // Supported cryptocurrencies
    private final String[] supportedCryptos = {"BTC", "ETH", "SOL", "BNB", "XRP", "ADA"};
    private final String[] cryptoNames = {"Bitcoin", "Ethereum", "Solana", "Binance Coin", "Ripple", "Cardano"};

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
        cryptoTableLayout = view.findViewById(R.id.cryptoTableLayout);
        
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
        
        // Update user info
        updateUserInfo();
        
        // Update portfolio display
        updatePortfolioInfo();
        
        // Initial display for Bitcoin (will be updated with real data)
        updateBitcoinDisplay(0.0);
        
        // Setup real-time price updates
        setupPriceUpdates();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start price updates when fragment is resumed
        startPriceUpdates();
        // Update portfolio display
        updatePortfolioInfo();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Stop price updates when fragment is paused
        stopPriceUpdates();
    }
    
    /**
     * Set up price update listener for Bitcoin
     */
    private void setupPriceUpdates() {
        // Set up price update listener for Bitcoin
        updateClient.trackSymbol("BTC", new BybitUpdateClient.PriceUpdateListener() {
            @Override
            public void onPriceUpdate(String symbol, double price, double change) {
                if (isAdded() && getContext() != null) {
                    if (symbol.equals("BTC")) {
                        updateBitcoinPrice(price);
                    }
                }
            }
        });
    }
    
    /**
     * Updates the Bitcoin price display
     * @param price Current Bitcoin price
     */
    private void updateBitcoinPrice(double price) {
        if (isAdded() && getContext() != null) {
            tvBitcoinPrice.setText(currencyFormat.format(price));
            updateBitcoinDisplay(price);
        }
    }
    
    /**
     * Updates the portfolio information
     */
    private void updatePortfolioInfo() {
        double balance = portfolio.getBalance();
        double totalInvestmentValue = portfolio.getInvestmentsValue();
        double portfolioValue = balance + totalInvestmentValue;
        
        // Update the wallet balance
        tvWalletBalance.setText(currencyFormat.format(balance));
        
        // Update the total portfolio value
        tvPortfolioValue.setText(currencyFormat.format(portfolioValue));
        
        // Calculate profit/loss - For simplicity, we'll just show total portfolio value
        double initialBalance = 10000.0; // Assuming starting with $10,000
        double profitLoss = portfolioValue - initialBalance;
        
        // Format with + or - sign
        String profitLossText = String.format(Locale.US, "%s%.2f%%", 
                profitLoss >= 0 ? "+" : "", (profitLoss / initialBalance) * 100);
        
        // Set the profit/loss text
        tvProfitLoss.setText(profitLossText);
        
        // Set the color (green for profit, red for loss)
        tvProfitLoss.setTextColor(profitLoss >= 0 ? 
                Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
    }
    
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
     * Updates the crypto holdings display in the UI as a clean table
     */
    private void updateCryptoHoldings() {
        // Clear existing rows (except header)
        if (cryptoTableLayout != null) {
            // Keep header row and remove all others
            int childCount = cryptoTableLayout.getChildCount();
            if (childCount > 1) {
                cryptoTableLayout.removeViews(1, childCount - 1);
            }
        }
        
        try {
            // Create a row for each supported cryptocurrency
            for (int i = 0; i < supportedCryptos.length; i++) {
                final String symbol = supportedCryptos[i];
                final String name = cryptoNames[i];
                
                // Get investment data if it exists
                Investment investment = portfolio.getInvestment(symbol);
                final double quantity = investment != null ? investment.getQuantity() : 0.0;
                
                // Create table row
                TableRow row = new TableRow(requireContext());
                row.setLayoutParams(new TableLayout.LayoutParams(
                        TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));
                
                // Cryptocurrency name cell
                TextView nameCell = new TextView(requireContext());
                nameCell.setLayoutParams(new TableRow.LayoutParams(
                        0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
                nameCell.setText(name + " (" + symbol + ")");
                nameCell.setTextColor(Color.WHITE);
                nameCell.setTextSize(16);
                nameCell.setPadding(0, 12, 0, 12);
                row.addView(nameCell);
                
                // Holdings cell
                TextView holdingsCell = new TextView(requireContext());
                holdingsCell.setLayoutParams(new TableRow.LayoutParams(
                        0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
                holdingsCell.setText(String.format(Locale.US, "%.8f %s", quantity, symbol));
                holdingsCell.setGravity(android.view.Gravity.END);
                holdingsCell.setTextColor(quantity > 0 ? Color.parseColor("#4CAF50") : Color.WHITE);
                holdingsCell.setTextSize(16);
                holdingsCell.setPadding(0, 12, 0, 12);
                row.addView(holdingsCell);
                
                // Add row to table
                cryptoTableLayout.addView(row);
                
                // Add divider
                if (i < supportedCryptos.length - 1) {
                    View divider = new View(requireContext());
                    divider.setLayoutParams(new TableLayout.LayoutParams(
                            TableLayout.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(Color.parseColor("#333333"));
                    cryptoTableLayout.addView(divider);
                }
            }
        } catch (Exception e) {
            // Handle error
            Log.e("HomeFragment", "Error updating crypto holdings: " + e.getMessage());
        }
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
}
