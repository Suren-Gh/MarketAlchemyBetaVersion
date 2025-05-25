package com.marketalchemy.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.app.AlertDialog;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import java.util.Currency;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import com.marketalchemy.app.api.BybitApiClient;
import com.marketalchemy.app.api.BybitUpdateClient;
import com.marketalchemy.app.api.CoinGeckoApiClient;
import com.marketalchemy.app.api.CoinGeckoUpdateClient;
import android.util.TypedValue;
import com.marketalchemy.app.model.VirtualPortfolio;
import com.marketalchemy.app.model.Investment;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public class CryptoFragment extends Fragment {

    private TextView tvPrice, tvChange, tvVirtualBalance, tvPortfolioValue;
    private ProgressBar progressBar;
    private Button buyButton, sellButton;
    private Button addMoneyButton;
    private Button setMoneyButton;
    private EditText quantityInput;
    private Spinner spinnerCrypto;
    
    // List of supported cryptocurrencies
    private final String[] supportedCryptos = {"BTC", "ETH", "SOL", "BNB", "XRP", "ADA"};
    private final String[] cryptoNames = {"Bitcoin", "Ethereum", "Solana", "Binance Coin", "Ripple", "Cardano"};
    private String currentCryptoId = "BTC"; // Default to Bitcoin
    
    private String getCurrentCryptoId() {
        return currentCryptoId;
    }

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private Handler mainHandler;
    
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);

    // Virtual portfolio for consistent portfolio tracking
    private VirtualPortfolio portfolio;
    // Shared preferences for transaction history
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "MarketAlchemyPrefs";
    private static final String KEY_TRANSACTION_HISTORY = "transaction_history";
    
    private CoinGeckoUpdateClient updateClient;
    
    // Handler for periodic updates
    private Handler updateHandler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_crypto, container, false);
        
        // Initialize the main handler for UI thread operations
        mainHandler = new Handler(Looper.getMainLooper());
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize SharedPreferences and portfolio
        prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        portfolio = new VirtualPortfolio(requireContext());
        
        // Initialize the currency format explicitly to ensure $ symbol
        currencyFormat.setCurrency(Currency.getInstance("USD"));
        
        // Initialize UI components
        progressBar = view.findViewById(R.id.progressBar);
        tvPrice = view.findViewById(R.id.tvBitcoinPrice);
        tvChange = view.findViewById(R.id.tvBitcoinChange);
        
        // Initialize portfolio views
        tvVirtualBalance = view.findViewById(R.id.tvVirtualBalance);
        tvPortfolioValue = view.findViewById(R.id.tvPortfolioValue);
        
        // Initialize trading controls
        quantityInput = view.findViewById(R.id.quantityInput);
        buyButton = view.findViewById(R.id.buyButton);
        sellButton = view.findViewById(R.id.sellButton);
        addMoneyButton = view.findViewById(R.id.addMoneyButton);
        setMoneyButton = view.findViewById(R.id.setMoneyButton);
        
        // Initialize cryptocurrency spinner
        spinnerCrypto = view.findViewById(R.id.spinnerCrypto);
        setupCryptoSpinner();
        
        // Set up button click listeners
        buyButton.setOnClickListener(v -> handleBuy());
        sellButton.setOnClickListener(v -> handleSell());
        addMoneyButton.setOnClickListener(v -> handleAddMoney());
        setMoneyButton.setOnClickListener(v -> handleSetMoney());
        
        // Make sure loading indicator is visible
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor("#FFD700")));
        progressBar.setVisibility(View.VISIBLE);
        
        // Initialize CoinGecko update client
        updateClient = CoinGeckoUpdateClient.getInstance();
        
        // Set up price update listener for Bitcoin
        setupPeriodicUpdates();
        
        // Initial UI title is set via the spinner selection
        
        // Set crypto theme colors
        int cryptoGreen = Color.parseColor("#4CAF50");
        buyButton.setBackgroundTintList(ColorStateList.valueOf(cryptoGreen));
        addMoneyButton.setBackgroundTintList(ColorStateList.valueOf(cryptoGreen));
        
        // Initialize portfolio display
        updatePortfolioDisplay();
        
        Log.d("CryptoFragment", "Connected to Bybit API for real-time price updates");
        showToast("Connected to live crypto market", Toast.LENGTH_SHORT);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        fetchCryptoData();
        updatePortfolioDisplay();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Stop periodic updates when fragment is paused
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up resources
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
    
    /**
     * Safely show a toast message from any thread
     * @param message Message to show
     * @param duration Toast duration
     */
    private void showToast(final String message, final int duration) {
        if (getActivity() == null || !isAdded()) {
            return;
        }
        
        // Make sure toast is shown on UI thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // We're on the main thread, show toast directly
            try {
                Toast.makeText(requireContext(), message, duration).show();
            } catch (Exception e) {
                Log.e("CryptoFragment", "Error showing toast: " + e.getMessage());
            }
        } else {
            // We're on a background thread, post to main thread
            mainHandler.post(() -> {
                try {
                    if (getActivity() != null && isAdded()) {
                        Toast.makeText(requireContext(), message, duration).show();
                    }
                } catch (Exception e) {
                    Log.e("CryptoFragment", "Error showing toast on main thread: " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * Updates the portfolio display with the current values from VirtualPortfolio
     */
    private void updatePortfolioDisplay() {
        // Get current values from portfolio
        double balance = portfolio.getBalance();
        double totalValue = portfolio.getTotalPortfolioValue();
        double investmentsValue = portfolio.getInvestmentsValue();
        
        // Update UI
        tvVirtualBalance.setText(currencyFormat.format(balance));
        tvPortfolioValue.setText("Portfolio Value: " + currencyFormat.format(totalValue));
        
        // Update holdings for current crypto if we own any
        if (portfolio.hasInvestment(currentCryptoId)) {
            Investment investment = portfolio.getInvestment(currentCryptoId);
            if (investment != null) {
                double quantity = investment.getQuantity();
                String quantityStr = String.format(Locale.US, "Holdings: %.8f %s", quantity, currentCryptoId);
                showToast(quantityStr, Toast.LENGTH_SHORT);
            }
        }
    }
    
    private void setupPeriodicUpdates() {
        // Setup real-time price updates using the Bybit update client
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                fetchCryptoData();
                updatePortfolioDisplay(); // Update portfolio values
                updateHandler.postDelayed(this, 1000); // Update every second
            }
        };
        updateHandler.post(updateRunnable);
    }
    
    private void setupCryptoSpinner() {
        // Create an adapter for the spinner with crypto names
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_spinner_item, cryptoNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCrypto.setAdapter(adapter);
        
        // Set listener for when user selects a different cryptocurrency
        spinnerCrypto.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Update the current crypto ID
                currentCryptoId = supportedCryptos[position];
                
                // Update the crypto title to show name and symbol
                TextView titleView = getView().findViewById(R.id.tvBitcoinTitle);
                if (titleView != null) {
                    titleView.setText(cryptoNames[position] + " (" + supportedCryptos[position] + ")");
                }
                
                // Set up price update listener for the selected cryptocurrency using CoinGeckoUpdateClient
                String symbol = supportedCryptos[position];
                updateClient.trackCoin(symbol, new CoinGeckoUpdateClient.PriceUpdateListener() {
                    @Override
                    public void onPriceUpdate(String symbol, double price, double change) {
                        if (isAdded() && getContext() != null) {
                            updatePriceDisplay(price, change);
                        }
                    }
                });
                
                // Fetch new data for the selected cryptocurrency
                fetchCryptoData();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        
        // Set default selection to Bitcoin (first in the list)
        spinnerCrypto.setSelection(0);
    }
    
    private void handleBuy() {
        try {
            String quantityStr = quantityInput.getText().toString();
            if (!quantityStr.isEmpty()) {
                double quantity = Double.parseDouble(quantityStr);
                if (quantity > 0) {
                    // Get current price from tvPrice
                    String priceStr = tvPrice.getText().toString().replaceAll("[^0-9.]", "");
                    double currentPrice = Double.parseDouble(priceStr);
                    double totalCost = quantity * currentPrice;
                    
                    // Try to buy using the portfolio
                    String cryptoId = getCurrentCryptoId();
                    boolean success = portfolio.buyCrypto(cryptoId, quantity);
                    
                    if (success) {
                        // Save transaction to history
                        String transaction = String.format(Locale.US, 
                            "BUY %.8f %s @ $%.2f = $%.2f", 
                            quantity, cryptoId, currentPrice, totalCost);
                        saveTransaction(transaction);
                        
                        // Update UI
                        updatePortfolioDisplay();
                        
                        // Show success message
                        String successMessage = String.format(Locale.US,
                            "Successfully bought %.8f %s\nPrice: $%.2f\nTotal: $%.2f",
                            quantity, cryptoId, currentPrice, totalCost);
                        showToast(successMessage, Toast.LENGTH_LONG);
                        
                        // Clear input field
                        quantityInput.setText("");
                    } else {
                        showToast(
                            "Insufficient funds. You need $" + String.format(Locale.US, "%.2f", totalCost),
                            Toast.LENGTH_SHORT);
                    }
                } else {
                    showToast(
                        "Please enter a positive quantity",
                        Toast.LENGTH_SHORT);
                }
            } else {
                showToast(
                    "Please enter a quantity",
                    Toast.LENGTH_SHORT);
            }
        } catch (NumberFormatException e) {
            showToast(
                "Invalid quantity",
                Toast.LENGTH_SHORT);
        }
    }
    
    private void saveTransaction(String transaction) {
        String history = prefs.getString(KEY_TRANSACTION_HISTORY, "");
        history = transaction + "\n" + history;
        prefs.edit().putString(KEY_TRANSACTION_HISTORY, history).apply();
    }
    
    private void handleSell() {
        try {
            String quantityStr = quantityInput.getText().toString();
            if (!quantityStr.isEmpty()) {
                double quantity = Double.parseDouble(quantityStr);
                if (quantity > 0) {
                    // Get current price from tvPrice
                    String priceStr = tvPrice.getText().toString().replaceAll("[^0-9.]", "");
                    double currentPrice = Double.parseDouble(priceStr);
                    double totalValue = quantity * currentPrice;
                    
                    String cryptoId = getCurrentCryptoId();
                    
                    // Check if the user has this crypto investment
                    if (portfolio.hasInvestment(cryptoId)) {
                        // Try to sell using the portfolio
                        boolean success = portfolio.sellCrypto(cryptoId, quantity);
                        
                        if (success) {
                            // Save transaction to history
                            String transaction = String.format(Locale.US, 
                                "SELL %.8f %s @ $%.2f = $%.2f", 
                                quantity, cryptoId, currentPrice, totalValue);
                            saveTransaction(transaction);
                            
                            // Update UI
                            updatePortfolioDisplay();
                            
                            // Show success message
                            String successMessage = String.format(Locale.US,
                                "Successfully sold %.8f %s\nPrice: $%.2f\nTotal: $%.2f",
                                quantity, cryptoId, currentPrice, totalValue);
                            showToast(successMessage, Toast.LENGTH_LONG);
                            
                            // Clear input field
                            quantityInput.setText("");
                        } else {
                            showToast("Insufficient " + cryptoId + " holdings", Toast.LENGTH_SHORT);
                        }
                    } else {
                        showToast("You don't own any " + cryptoId + " to sell", Toast.LENGTH_SHORT);
                    }
                } else {
                    showToast(
                        "Please enter a positive quantity",
                        Toast.LENGTH_SHORT);
                }
            } else {
                showToast(
                    "Please enter a quantity",
                    Toast.LENGTH_SHORT);
                }
            } catch (NumberFormatException e) {
                showToast(
                    "Invalid quantity",
                    Toast.LENGTH_SHORT);
            }
        }
    
    private void handleAddMoney() {
        // Create an EditText for the user to input the amount
        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter amount to add");
        
        // Create padding for the input
        int padding = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16, 
            getResources().getDisplayMetrics()
        );
        
        // Set layout parameters for the EditText
        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = padding;
        params.rightMargin = padding;
        input.setLayoutParams(params);
        container.addView(input);

        // Build the dialog
        new AlertDialog.Builder(requireContext())
            .setTitle("Add Money to Portfolio")
            .setMessage("Enter the amount you want to add to your portfolio")
            .setView(container)
            .setPositiveButton("Add", (dialog, which) -> {
                String amountStr = input.getText().toString();
                if (!amountStr.isEmpty()) {
                    try {
                        double amount = Double.parseDouble(amountStr);
                        if (amount > 0) {
                            // Add the money to the portfolio (true means it's an addition)
                            portfolio.setBalance(amount, true);
                            
                            // Update the UI
                            updatePortfolioDisplay();
                            
                            // Show success message
                            showToast(
                                "Successfully added $" + String.format(Locale.US, "%.2f", amount) + " to your portfolio", 
                                Toast.LENGTH_SHORT);
                        } else {
                            showToast(
                                "Please enter a positive amount", 
                                Toast.LENGTH_SHORT);
                        }
                    } catch (NumberFormatException e) {
                        showToast(
                            "Invalid amount", 
                            Toast.LENGTH_SHORT);
                    }
                } else {
                    showToast(
                        "Please enter an amount", 
                        Toast.LENGTH_SHORT);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void handleSetMoney() {
        // Create an EditText for the user to input the amount
        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter amount to set");
        
        // Create padding for the input
        int padding = (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 16, 
            getResources().getDisplayMetrics()
        );
        
        // Set layout parameters for the EditText
        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = padding;
        params.rightMargin = padding;
        input.setLayoutParams(params);
        container.addView(input);

        // Build the dialog
        new AlertDialog.Builder(requireContext())
            .setTitle("Set Money in Portfolio")
            .setMessage("Enter the amount you want to set in your portfolio")
            .setView(container)
            .setPositiveButton("Set", (dialog, which) -> {
                String amountStr = input.getText().toString();
                if (!amountStr.isEmpty()) {
                    try {
                        double amount = Double.parseDouble(amountStr);
                        if (amount > 0) {
                            // Set the money in the portfolio (false means it's setting a new value, not adding)
                            portfolio.setBalance(amount, false);
                            
                            // Update the UI
                            updatePortfolioDisplay();
                            
                            // Show success message
                            showToast(
                                "Successfully set $" + String.format(Locale.US, "%.2f", amount) + " in your portfolio", 
                                Toast.LENGTH_SHORT);
                        } else {
                            showToast(
                                "Please enter a positive amount", 
                                Toast.LENGTH_SHORT);
                        }
                    } catch (NumberFormatException e) {
                        showToast(
                            "Invalid amount", 
                            Toast.LENGTH_SHORT);
                    }
                } else {
                    showToast(
                        "Please enter an amount", 
                        Toast.LENGTH_SHORT);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void fetchCryptoData() {
        showLoading(true);
        
        // Get cryptocurrency data asynchronously
        String currentCrypto = getCurrentCryptoId(); // Default is "BTC"
        
        CoinGeckoApiClient.getInstance().getCurrentPriceAsync(currentCrypto, new CoinGeckoApiClient.PriceCallback() {
            @Override
            public void onPrice(double price) {
                if (!isAdded() || getContext() == null) {
                    return; // Fragment not attached, avoid crashes
                }
                
                // For now, we'll assume a 0% change since CoinGeckoApiClient.PriceCallback
                // doesn't provide the change percentage in this simple price call
                updatePriceDisplay(price, 0.0);
                showLoading(false);
            }
            
            @Override
            public void onError(Exception e) {
                if (!isAdded() || getContext() == null) {
                    return; // Fragment not attached, avoid crashes
                }
                
                Log.e("CryptoFragment", "Error fetching data: " + e.getMessage());
                showLoading(false);
                
                // Show error toast
                showToast("Error fetching price data. Please try again.", Toast.LENGTH_SHORT);
            }
        });
    }
    
    private void updatePriceDisplay(double price, double change) {
        // Check if fragment is attached to context to prevent crashes
        if (!isAdded() || getContext() == null) {
            Log.d("CryptoFragment", "updatePriceDisplay: Fragment not attached to context");
            return;
        }
        
        try {
            String formattedPrice = currencyFormat.format(price);
            animateTextChange(tvPrice, formattedPrice);
            
            String changeText = String.format(Locale.US, "%.2f%%", change);
            
            // Set crypto-themed colors for price changes
            int cryptoGreen = Color.parseColor("#4CAF50");
            int cryptoRed = Color.parseColor("#F44336");
            
            if (change >= 0) {
                tvChange.setTextColor(cryptoGreen);
                tvChange.setText("+" + changeText); // Add plus sign for positive changes
            } else {
                tvChange.setTextColor(cryptoRed);
                tvChange.setText(changeText);
            }
            
            showLoading(false);
        } catch (Exception e) {
            Log.e("CryptoFragment", "Error updating price display: " + e.getMessage());
        }
    }
    
    private void animateTextChange(final TextView textView, final String newText) {
        ValueAnimator fadeOut = ValueAnimator.ofFloat(1.0f, 0.5f);
            fadeOut.setDuration(150);
        fadeOut.addUpdateListener(animator -> textView.setAlpha((float) animator.getAnimatedValue()));
        fadeOut.addListener(new AnimatorListenerAdapter() {
                @Override
            public void onAnimationEnd(Animator animation) {
                    textView.setText(newText);
                ValueAnimator fadeIn = ValueAnimator.ofFloat(0.5f, 1.0f);
                    fadeIn.setDuration(150);
                fadeIn.addUpdateListener(animator -> textView.setAlpha((float) animator.getAnimatedValue()));
                fadeIn.start();
            }
        });
        fadeOut.start();
    }
    
    private void showLoading(boolean show) {
        // Check if fragment is attached to context to prevent crashes
        if (!isAdded() || getContext() == null) {
            return;
        }
        
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
    
    @Override
    public void onDestroy() {
        executorService.shutdown();
        // No need to explicitly untrack coins with CoinGeckoUpdateClient
        // It will be handled when the instance is garbage collected
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
        super.onDestroy();
    }
} 