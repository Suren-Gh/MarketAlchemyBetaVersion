package com.marketalchemy.app.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.marketalchemy.app.api.BybitApiClient;
import com.marketalchemy.app.api.CoinGeckoApiClient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Model class for handling virtual portfolio functionality using Bybit API
 */
public class VirtualPortfolio {
    
    private static final String TAG = "VirtualPortfolio";
    private static final String PREFS_NAME = "VirtualPortfolioPrefs";
    private static final double INITIAL_BALANCE = 10000.0; // $10,000 initial virtual fiat balance
    private static final String KEY_BALANCE = "virtualBalance";
    private static final String KEY_INVESTMENTS = "investments";
    private static final String KEY_TRANSACTION_HISTORY = "transaction_history";
    
    private double balance;
    private List<Investment> investments;
    private final SharedPreferences prefs;
    private final Gson gson;
    private final BybitApiClient bybitClient;
    private final CoinGeckoApiClient coinGeckoClient;
    
    public VirtualPortfolio(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        // Initialize API clients
        bybitClient = BybitApiClient.getInstance();
        coinGeckoClient = CoinGeckoApiClient.getInstance();
        
        // Load saved balance
        balance = prefs.getFloat(KEY_BALANCE, (float) INITIAL_BALANCE); // Default $10,000
        
        // Load saved investments
        String investmentsJson = prefs.getString(KEY_INVESTMENTS, "");
        if (investmentsJson.isEmpty()) {
            investments = new ArrayList<>();
        } else {
            try {
                Type type = new TypeToken<List<Investment>>() {}.getType();
                investments = gson.fromJson(investmentsJson, type);
            } catch (Exception e) {
                Log.e(TAG, "Error loading investments: " + e.getMessage());
                investments = new ArrayList<>();
            }
        }
    }
    
    /**
     * Set the initial or add to virtual money balance
     * @param amount Amount to set or add
     * @param isAddition True if adding to balance, false if setting new balance
     */
    public void setBalance(double amount, boolean isAddition) {
        if (isAddition) {
            balance += amount;
        } else {
            balance = amount;
        }
        saveBalance();
    }
    
    /**
     * Get current virtual money balance
     * @return Current balance
     */
    public double getBalance() {
        return balance;
    }
    
    /**
     * Buy a cryptocurrency with virtual money using Bybit API
     * @param cryptoId Cryptocurrency ID (e.g., "BTCUSDT")
     * @param quantity Amount to buy
     * @return True if purchase successful, false if insufficient funds
     */
    public boolean buyCrypto(String cryptoId, double quantity) {
        try {
            // Get current price from Bybit
            double currentPrice = bybitClient.getCurrentPrice(cryptoId);
            
            double cost = quantity * currentPrice;
            
            // Check if user has enough funds
            if (cost > balance) {
                return false;
            }
            
            // Deduct the cost from balance
            balance -= cost;
            saveBalance();
            
            // Check if user already has this crypto
            Investment existingInvestment = getInvestment(cryptoId);
            if (existingInvestment != null) {
                // Calculate new average purchase price
                double oldValue = existingInvestment.getQuantity() * existingInvestment.getPurchasePrice();
                double newValue = quantity * currentPrice;
                double totalQuantity = existingInvestment.getQuantity() + quantity;
                double newAveragePrice = (oldValue + newValue) / totalQuantity;
                
                // Update existing investment
                existingInvestment.setQuantity(totalQuantity);
                existingInvestment.setPurchasePrice(newAveragePrice);
            } else {
                // Create new investment
                Investment newInvestment = new Investment(cryptoId, quantity, currentPrice, new Date());
                investments.add(newInvestment);
            }
            
            // Save investments
            saveInvestments();
            
            // Save transaction to history
            saveTransactionHistory("BUY", cryptoId, quantity, currentPrice);
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error buying crypto: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Sell a cryptocurrency for virtual money using Bybit API
     * @param cryptoId Cryptocurrency ID (e.g., "BTCUSDT")
     * @param quantity Amount to sell
     * @return True if sale successful, false if insufficient holdings
     */
    public boolean sellCrypto(String cryptoId, double quantity) {
        try {
            // Check if user has this crypto and enough quantity
            Investment investment = getInvestment(cryptoId);
            if (investment == null || investment.getQuantity() < quantity) {
                return false;
            }
            
            // Get current price from Bybit
            double currentPrice = bybitClient.getCurrentPrice(cryptoId);
            
            // Calculate proceeds
            double proceeds = quantity * currentPrice;
            
            // Add proceeds to balance
            balance += proceeds;
            saveBalance();
            
            // Update investment
            double remainingQuantity = investment.getQuantity() - quantity;
            if (remainingQuantity > 0.00000001) { // Keep a small threshold to avoid floating point issues
                investment.setQuantity(remainingQuantity);
            } else {
                // Remove investment if quantity is effectively zero
                investments.remove(investment);
            }
            
            // Save investments
            saveInvestments();
            
            // Save transaction to history
            saveTransactionHistory("SELL", cryptoId, quantity, currentPrice);
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error selling crypto: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get total portfolio value (balance + investments)
     * @return Total portfolio value
     */
    public double getTotalPortfolioValue() {
        // This could be called from the main thread
        // Use cached investment values to avoid network operations
        return balance + getInvestmentsValueCached();
    }
    
    /**
     * Get total portfolio value asynchronously (balance + investments)
     * @param callback Callback to receive result
     */
    public void getTotalPortfolioValueAsync(PortfolioValueCallback callback) {
        getInvestmentsValueAsync(investmentValue -> {
            double total = balance + investmentValue;
            callback.onValueCalculated(total);
        });
    }
    
    /**
     * Callback for async portfolio value calculation
     */
    public interface PortfolioValueCallback {
        void onValueCalculated(double value);
    }
    
    /**
     * Get total value of all crypto investments
     * @return Total value of all crypto investments
     */
    public double getInvestmentsValue() {
        // This should not be called from the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w("VirtualPortfolio", "getInvestmentsValue called on main thread, returning cached value");
            return getInvestmentsValueCached();
        }
        
        double total = 0.0;
        for (Investment investment : investments) {
            double currentPrice = bybitClient.getCurrentPrice(investment.getCryptoId());
            total += investment.getQuantity() * currentPrice;
        }
        return total;
    }
    
    /**
     * Get total value of all investments using cached prices
     * @return Total cached value
     */
    public double getInvestmentsValueCached() {
        double total = 0.0;
        BybitApiClient apiClient = BybitApiClient.getInstance();
        
        for (Investment investment : investments) {
            // Try to get cached price first
            String coinId = investment.getCryptoId();
            Double cachedPrice = apiClient.getCachedPrice(coinId);
            double price = (cachedPrice != null) ? cachedPrice : 0.0;
            
            total += investment.getQuantity() * price;
        }
        return total;
    }
    
    /**
     * Get total value of all investments asynchronously
     * @param callback Callback to receive result
     */
    public void getInvestmentsValueAsync(InvestmentValueCallback callback) {
        // If no investments, return 0 immediately
        if (investments.isEmpty()) {
            callback.onValueCalculated(0.0);
            return;
        }
        
        BybitApiClient apiClient = BybitApiClient.getInstance();
        AtomicInteger pendingRequests = new AtomicInteger(investments.size());
        AtomicReference<Double> totalValue = new AtomicReference<>(0.0);
        
        for (Investment investment : investments) {
            String coinId = investment.getCryptoId();
            final double quantity = investment.getQuantity();
            
            // Try to get cached price first
            Double cachedPrice = apiClient.getCachedPrice(coinId);
            if (cachedPrice != null) {
                // Use cached price
                double investmentValue = quantity * cachedPrice;
                totalValue.updateAndGet(currentTotal -> currentTotal + investmentValue);
                
                // Decrease pending count
                if (pendingRequests.decrementAndGet() == 0) {
                    callback.onValueCalculated(totalValue.get());
                }
            } else {
                // Fetch price asynchronously
                apiClient.getCurrentPriceAsync(coinId, new BybitApiClient.PriceCallback() {
                    @Override
                    public void onPrice(double price, double change) {
                        double investmentValue = quantity * price;
                        totalValue.updateAndGet(currentTotal -> currentTotal + investmentValue);
                        
                        // If all requests are complete, call the callback
                        if (pendingRequests.decrementAndGet() == 0) {
                            callback.onValueCalculated(totalValue.get());
                        }
                    }
                    
                    @Override
                    public void onError(Exception e) {
                        Log.e("VirtualPortfolio", "Error getting price for " + coinId + ": " + e.getMessage());
                        
                        // Even on error, we need to count this as complete
                        if (pendingRequests.decrementAndGet() == 0) {
                            callback.onValueCalculated(totalValue.get());
                        }
                    }
                });
            }
        }
    }
    
    /**
     * Callback for async investment value calculation
     */
    public interface InvestmentValueCallback {
        void onValueCalculated(double value);
    }
    
    /**
     * Get the total profit/loss of all investments
     * @return Total profit/loss
     */
    public double getTotalProfitLoss() {
        double totalProfitLoss = 0.0;
        
        try {
            for (Investment investment : investments) {
                String cryptoId = investment.getCryptoId();
                double currentPrice = bybitClient.getCurrentPrice(cryptoId);
                double profitLoss = investment.getQuantity() * (currentPrice - investment.getPurchasePrice());
                totalProfitLoss += profitLoss;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating profit/loss: " + e.getMessage());
        }
        
        return totalProfitLoss;
    }
    
    /**
     * Check if the user has an investment in a specific cryptocurrency
     * @param cryptoId Cryptocurrency ID (e.g., "bitcoin")
     * @return True if the user has the investment, false otherwise
     */
    public boolean hasInvestment(String cryptoId) {
        return getInvestment(cryptoId) != null;
    }
    
    /**
     * Get all crypto investments
     * @return List of all investments
     */
    public List<Investment> getInvestments() {
        return investments;
    }
    
    /**
     * Get investment for a specific cryptocurrency
     * @param cryptoId Cryptocurrency ID (e.g., "bitcoin")
     * @return Investment or null if not found
     */
    public Investment getInvestment(String cryptoId) {
        for (Investment investment : investments) {
            if (investment.getCryptoId().equals(cryptoId)) {
                return investment;
            }
        }
        return null;
    }
    
    /**
     * Save transaction to history
     * @param type Transaction type (BUY/SELL)
     * @param cryptoId Cryptocurrency ID
     * @param quantity Quantity
     * @param price Price
     */
    public void saveTransactionHistory(String type, String cryptoId, double quantity, double price) {
        try {
            // Format date
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            String timestamp = sdf.format(new Date());
            
            // Get symbol from ID
            String symbol = coinGeckoClient.getSymbolFromId(cryptoId);
            
            // Calculate total value
            double totalValue = quantity * price;
            
            // Format transaction string
            String transaction = String.format(Locale.US, 
                "%s %.8f %s @ $%.2f = $%.2f", 
                type, quantity, symbol, price, totalValue);
            
            // Get existing history
            String history = prefs.getString(KEY_TRANSACTION_HISTORY, "");
            
            // Add new transaction at the beginning
            history = transaction + "\n" + history;
            
            // Save updated history
            prefs.edit().putString(KEY_TRANSACTION_HISTORY, history).apply();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving transaction history: " + e.getMessage());
        }
    }
    
    /**
     * Save balance to SharedPreferences
     */
    private void saveBalance() {
        prefs.edit().putFloat(KEY_BALANCE, (float) balance).apply();
    }
    
    /**
     * Save investments to SharedPreferences
     */
    private void saveInvestments() {
        String investmentsJson = gson.toJson(investments);
        prefs.edit().putString(KEY_INVESTMENTS, investmentsJson).apply();
    }
}
