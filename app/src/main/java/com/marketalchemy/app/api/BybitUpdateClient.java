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
 * Client for receiving real-time price updates from Bybit
 * Uses polling at 1-second intervals for high-frequency updates
 */
public class BybitUpdateClient {
    
    private static final String TAG = "BybitUpdateClient";
    
    // Update interval in milliseconds (1 second for real-time market data)
    private static final long UPDATE_INTERVAL_MS = 1000;
    
    // Singleton instance
    private static BybitUpdateClient instance;
    
    // Bybit API client
    private final BybitApiClient apiClient;
    
    // Handler for main thread
    private final Handler mainHandler;
    
    // Executor for background tasks
    private ExecutorService executorService;
    
    // List of symbols to track
    private final List<String> trackedSymbols;
    
    // Map of symbol to listeners
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
    private BybitUpdateClient() {
        // Initialize fields
        apiClient = BybitApiClient.getInstance();
        mainHandler = new Handler(Looper.getMainLooper());
        executorService = Executors.newSingleThreadExecutor();
        trackedSymbols = new ArrayList<>();
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
     * @return BybitUpdateClient instance
     */
    public static synchronized BybitUpdateClient getInstance() {
        if (instance == null) {
            instance = new BybitUpdateClient();
        }
        return instance;
    }
    
    /**
     * Start tracking a cryptocurrency for price updates
     * @param symbol Cryptocurrency symbol
     * @param listener Listener to receive updates
     */
    public void trackSymbol(String symbol, PriceUpdateListener listener) {
        // Add to tracked symbols if not already tracking
        if (!trackedSymbols.contains(symbol)) {
            trackedSymbols.add(symbol);
        }
        
        // Add listener
        List<PriceUpdateListener> symbolListeners = listeners.getOrDefault(symbol, new ArrayList<>());
        if (!symbolListeners.contains(listener)) {
            symbolListeners.add(listener);
        }
        listeners.put(symbol, symbolListeners);
        
        // Start updates if not already running
        if (!isRunning) {
            startUpdates();
        }
    }
    
    /**
     * Stop tracking a cryptocurrency for a specific listener
     * @param symbol Cryptocurrency symbol
     * @param listener Listener to remove
     */
    public void untrackSymbol(String symbol, PriceUpdateListener listener) {
        // Remove listener
        List<PriceUpdateListener> symbolListeners = listeners.getOrDefault(symbol, new ArrayList<>());
        symbolListeners.remove(listener);
        
        // If no more listeners for this symbol, remove from tracked symbols
        if (symbolListeners.isEmpty()) {
            listeners.remove(symbol);
            trackedSymbols.remove(symbol);
        } else {
            listeners.put(symbol, symbolListeners);
        }
        
        // If no more symbols to track, stop updates
        if (trackedSymbols.isEmpty()) {
            stopUpdates();
        }
    }
    
    /**
     * Stop tracking all cryptocurrencies and remove all listeners
     */
    public void untrackAllSymbols() {
        // Clear all listeners and tracked symbols
        listeners.clear();
        trackedSymbols.clear();
        
        // Stop updates since there are no symbols to track
        stopUpdates();
        
        Log.d(TAG, "Untracked all symbols and stopped updates");
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
     * Fetch updates for all tracked symbols
     */
    private void fetchUpdates() {
        if (trackedSymbols.isEmpty()) {
            return;
        }
        
        // Make sure executor is available
        ensureExecutorRunning();
        
        try {
            executorService.execute(() -> {
                try {
                    // Get market data for all tracked symbols
                    apiClient.getMarketDataAsync(trackedSymbols, new BybitApiClient.MarketDataCallback() {
                        @Override
                        public void onMarketData(Map<String, BybitApiClient.CryptoMarketData> marketData) {
                            // Update listeners on main thread
                            for (String symbol : trackedSymbols) {
                                // Get market data for this symbol
                                BybitApiClient.CryptoMarketData data = marketData.get(symbol);
                                if (data != null) {
                                    // Cache prices
                                    priceCache.put(symbol, data.currentPrice);
                                    changeCache.put(symbol, data.priceChangePercentage24h);
                                    
                                    // Notify listeners
                                    List<PriceUpdateListener> symbolListeners = listeners.getOrDefault(symbol, new ArrayList<>());
                                    for (PriceUpdateListener listener : symbolListeners) {
                                        try {
                                            listener.onPriceUpdate(data.symbol, data.currentPrice, data.priceChangePercentage24h);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error notifying listener: " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                        
                        @Override
                        public void onError(Exception e) {
                            Log.e(TAG, "Error fetching market data: " + e.getMessage());
                        }
                    });
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
     * Get the last known price for a cryptocurrency
     * @param symbol Cryptocurrency symbol
     * @return Last known price, or null if not available
     */
    public Double getLastPrice(String symbol) {
        return priceCache.get(symbol);
    }
    
    /**
     * Get the last known 24h change for a cryptocurrency
     * @param symbol Cryptocurrency symbol
     * @return Last known 24h change, or null if not available
     */
    public Double getLastChange(String symbol) {
        return changeCache.get(symbol);
    }
    
    /**
     * Interface for price update listeners
     */
    public interface PriceUpdateListener {
        void onPriceUpdate(String symbol, double price, double change);
    }
}
