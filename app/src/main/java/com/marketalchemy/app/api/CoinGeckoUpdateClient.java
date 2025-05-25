package com.marketalchemy.app.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Client for receiving real-time price updates from CoinGecko
 * (Uses polling to simulate real-time since CoinGecko doesn't have a WebSocket API)
 */
public class CoinGeckoUpdateClient {
    
    private static final String TAG = "CoinGeckoUpdateClient";
    
    // Update interval in milliseconds
    private static final long UPDATE_INTERVAL_MS = 1000; // Update every second
    
    // Singleton instance
    private static CoinGeckoUpdateClient instance;
    
    // CoinGecko API client
    private final CoinGeckoApiClient apiClient;
    
    // Handler for main thread
    private final Handler mainHandler;
    
    // Executor for background tasks
    private ExecutorService executorService;
    
    // List of coins to track
    private final List<String> trackedCoins;
    
    // Map of coin ID to listeners
    private final Map<String, List<PriceUpdateListener>> listeners;
    
    // Price cache
    private final Map<String, Double> priceCache;
    private final Map<String, Double> changeCache;
    
    // Running flag
    private boolean isRunning;
    
    // Update runnable
    private final Runnable updateRunnable;
    
    /**
     * Private constructor for singleton pattern
     */
    private CoinGeckoUpdateClient() {
        // Initialize fields
        apiClient = CoinGeckoApiClient.getInstance();
        mainHandler = new Handler(Looper.getMainLooper());
        executorService = Executors.newSingleThreadExecutor();
        trackedCoins = new ArrayList<>();
        listeners = new HashMap<>();
        priceCache = new HashMap<>();
        changeCache = new HashMap<>();
        isRunning = false;
        
        // Create update runnable
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                fetchUpdates();
                if (isRunning) {
                    mainHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };
    }
    
    /**
     * Get singleton instance
     * @return CoinGeckoUpdateClient instance
     */
    public static synchronized CoinGeckoUpdateClient getInstance() {
        if (instance == null) {
            instance = new CoinGeckoUpdateClient();
        }
        return instance;
    }
    
    /**
     * Start tracking a coin for price updates
     * @param coinId CoinGecko ID or symbol
     * @param listener Listener to receive updates
     */
    public void trackCoin(String coinId, PriceUpdateListener listener) {
        // Convert symbol to ID if needed
        String id = apiClient.getIdFromSymbol(coinId);
        
        // Add to tracked coins if not already tracking
        if (!trackedCoins.contains(id)) {
            trackedCoins.add(id);
        }
        
        // Add listener
        List<PriceUpdateListener> coinListeners = listeners.getOrDefault(id, new ArrayList<>());
        if (!coinListeners.contains(listener)) {
            coinListeners.add(listener);
        }
        listeners.put(id, coinListeners);
        
        // Start updates if not already running
        if (!isRunning) {
            startUpdates();
        }
    }
    
    /**
     * Stop tracking a coin for a specific listener
     * @param coinId CoinGecko ID or symbol
     * @param listener Listener to remove
     */
    public void untrackCoin(String coinId, PriceUpdateListener listener) {
        // Convert symbol to ID if needed
        String id = apiClient.getIdFromSymbol(coinId);
        
        // Remove listener
        List<PriceUpdateListener> coinListeners = listeners.getOrDefault(id, new ArrayList<>());
        coinListeners.remove(listener);
        
        // If no more listeners for this coin, remove from tracked coins
        if (coinListeners.isEmpty()) {
            listeners.remove(id);
            trackedCoins.remove(id);
        } else {
            listeners.put(id, coinListeners);
        }
        
        // If no more coins to track, stop updates
        if (trackedCoins.isEmpty()) {
            stopUpdates();
        }
    }
    
    /**
     * Start updates
     */
    private void startUpdates() {
        if (!isRunning) {
            isRunning = true;
            ensureExecutorRunning();
            mainHandler.post(updateRunnable);
        }
    }
    
    /**
     * Ensure the executor service is running
     */
    private void ensureExecutorRunning() {
        try {
            if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
                Log.d(TAG, "Recreating executor that was shut down");
                executorService = Executors.newSingleThreadExecutor();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error recreating executor: " + e.getMessage());
            executorService = Executors.newSingleThreadExecutor();
        }
    }
    
    /**
     * Stop updates
     */
    private void stopUpdates() {
        isRunning = false;
        mainHandler.removeCallbacks(updateRunnable);
    }
    
    /**
     * Fetch updates for all tracked coins
     */
    private void fetchUpdates() {
        if (trackedCoins.isEmpty()) {
            return;
        }
        
        // Make sure executor is available
        ensureExecutorRunning();
        
        try {
            executorService.execute(() -> {
                try {
                    // Get market data for all tracked coins
                    Map<String, CoinGeckoApiClient.CryptoMarketData> marketData = apiClient.getMarketData(trackedCoins);
                    
                    // Update listeners on main thread
                    mainHandler.post(() -> {
                        try {
                            for (String coinId : trackedCoins) {
                                // Get market data for this coin
                                CoinGeckoApiClient.CryptoMarketData data = marketData.get(coinId);
                                if (data != null) {
                                    // Cache prices
                                    Double oldPrice = priceCache.getOrDefault(coinId, data.currentPrice);
                                    priceCache.put(coinId, data.currentPrice);
                                    changeCache.put(coinId, data.priceChangePercentage24h);
                                    
                                    // Notify listeners
                                    List<PriceUpdateListener> coinListeners = listeners.getOrDefault(coinId, new ArrayList<>());
                                    for (PriceUpdateListener listener : coinListeners) {
                                        try {
                                            listener.onPriceUpdate(data.symbol, data.currentPrice, data.priceChangePercentage24h);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error notifying listener: " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing updates on UI thread: " + e.getMessage());
                        }
                    });
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Error fetching updates: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error in update task: " + e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Executor rejected task: " + e.getMessage());
            // Recreate executor for future tasks
            ensureExecutorRunning();
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling update task: " + e.getMessage());
        }
    }
    
    /**
     * Get the last known price for a coin
     * @param coinId CoinGecko ID or symbol
     * @return Last known price or null if not cached
     */
    public Double getLastPrice(String coinId) {
        // Convert symbol to ID if needed
        String id = apiClient.getIdFromSymbol(coinId);
        return priceCache.get(id);
    }
    
    /**
     * Get the last known 24h change for a coin
     * @param coinId CoinGecko ID or symbol
     * @return Last known 24h change or null if not cached
     */
    public Double getLastChange(String coinId) {
        // Convert symbol to ID if needed
        String id = apiClient.getIdFromSymbol(coinId);
        return changeCache.get(id);
    }
    
    /**
     * Interface for price update listeners
     */
    public interface PriceUpdateListener {
        /**
         * Called when a price update is received
         * @param symbol Cryptocurrency symbol (e.g., BTC)
         * @param price Current price in USD
         * @param change 24h price change percentage
         */
        void onPriceUpdate(String symbol, double price, double change);
    }
}
