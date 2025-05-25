package com.marketalchemy.app.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Client for Bybit API - Provides real-time cryptocurrency price data
 */
public class BybitApiClient {
    private static final String TAG = "BybitApiClient";
    private static final String BASE_URL = "https://api.bybit.com";
    private static final long CACHE_TIME_MS = 1000; // Cache for 1 second (real-time updates)
    
    // Singleton instance
    private static BybitApiClient instance;
    
    // OkHttp client
    private final OkHttpClient client;
    
    // Cache for responses
    private final Map<String, CachedResponse> cache;
    
    // Price cache to avoid network calls
    private final Map<String, Double> priceCache;
    
    // 24h Change cache
    private final Map<String, Double> changeCache;
    
    // Symbol mappings
    private final Map<String, String> idToSymbol;
    private final Map<String, String> symbolToId;
    
    // Supported cryptos
    private final List<String> supportedCryptos;
    
    // Executor service for background tasks
    private final ExecutorService executorService;
    
    /**
     * Private constructor for singleton pattern
     */
    private BybitApiClient() {
        // Initialize OkHttp client with timeouts
        client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
        
        // Initialize cache
        cache = new ConcurrentHashMap<>();
        
        // Initialize price cache
        priceCache = new ConcurrentHashMap<>();
        
        // Initialize change cache
        changeCache = new ConcurrentHashMap<>();
        
        // Initialize symbol mappings
        idToSymbol = new HashMap<>();
        symbolToId = new HashMap<>();
        
        // Initialize supported cryptos
        supportedCryptos = new ArrayList<>();
        
        // Initialize executor service
        executorService = Executors.newFixedThreadPool(2);
        
        // Add supported cryptocurrencies
        addCryptoMapping("bitcoin", "BTC");
        addCryptoMapping("ethereum", "ETH");
        addCryptoMapping("solana", "SOL");
        addCryptoMapping("binance-coin", "BNB");
        addCryptoMapping("ripple", "XRP");
        addCryptoMapping("cardano", "ADA");
        
        // Add to supported list
        supportedCryptos.add("BTC");
        supportedCryptos.add("ETH");
        supportedCryptos.add("SOL");
        supportedCryptos.add("BNB");
        supportedCryptos.add("XRP");
        supportedCryptos.add("ADA");
    }
    
    /**
     * Add mapping between ID and symbol
     * @param id Cryptocurrency ID
     * @param symbol Cryptocurrency symbol
     */
    private void addCryptoMapping(String id, String symbol) {
        idToSymbol.put(id.toLowerCase(), symbol.toUpperCase());
        symbolToId.put(symbol.toUpperCase(), id.toLowerCase());
    }
    
    /**
     * Get singleton instance
     * @return BybitApiClient instance
     */
    public static synchronized BybitApiClient getInstance() {
        if (instance == null) {
            instance = new BybitApiClient();
        }
        return instance;
    }
    
    /**
     * Get symbol from ID
     * @param id Cryptocurrency ID
     * @return Symbol (e.g., BTC)
     */
    public String getSymbolFromId(String id) {
        return idToSymbol.getOrDefault(id.toLowerCase(), id.toUpperCase());
    }
    
    /**
     * Get ID from symbol
     * @param symbol Cryptocurrency symbol
     * @return ID (e.g., bitcoin)
     */
    public String getIdFromSymbol(String symbol) {
        String id = symbolToId.get(symbol.toUpperCase());
        if (id != null) {
            return id;
        }
        
        // If not found, assume it's already an ID
        return symbol.toLowerCase();
    }
    
    /**
     * Get supported cryptocurrencies
     * @return List of supported cryptocurrency symbols
     */
    public List<String> getSupportedCryptos() {
        return new ArrayList<>(supportedCryptos);
    }
    
    /**
     * Get cached price for a cryptocurrency
     * @param cryptoId Coin ID or symbol
     * @return Cached price in USD, or null if not cached
     */
    public Double getCachedPrice(String cryptoId) {
        // Convert symbol to ID if needed
        String symbol = getSymbolFromId(cryptoId);
        return priceCache.get(symbol.toUpperCase());
    }
    
    /**
     * Get cached 24h price change percentage for a cryptocurrency
     * @param cryptoId Coin ID or symbol
     * @return Cached 24h price change percentage, or null if not cached
     */
    public Double getCachedChange(String cryptoId) {
        // Convert symbol to ID if needed
        String symbol = getSymbolFromId(cryptoId);
        return changeCache.get(symbol.toUpperCase());
    }
    
    /**
     * Get current price for a cryptocurrency
     * @param cryptoId Coin ID or symbol
     * @return Current price in USD
     */
    public double getCurrentPrice(String cryptoId) {
        // This is a synchronous method that might be called from the main thread
        // We should avoid network operations on the main thread
        
        // Convert to symbol format for Bybit API
        String symbol = getSymbolFromId(cryptoId).toUpperCase() + "USDT";
        
        // First check our cache
        Double cachedPrice = priceCache.get(symbol);
        if (cachedPrice != null) {
            return cachedPrice;
        }
        
        // If we don't have a cached price, we need to get it from the network
        // But we should never do this on the main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "getCurrentPrice called on main thread, returning 0 to avoid NetworkOnMainThreadException");
            return 0.0; // Default price to avoid crash
        }
        
        try {
            // Build URL - use Bybit's ticker endpoint
            String url = String.format("%s/v5/market/tickers?category=spot&symbol=%s", BASE_URL, symbol);
            
            // Get response
            JSONObject response = getJsonObjectFromUrl(url);
            
            // Parse response
            if (response.has("result") && response.getJSONObject("result").has("list")) {
                JSONArray list = response.getJSONObject("result").getJSONArray("list");
                if (list.length() > 0) {
                    JSONObject ticker = list.getJSONObject(0);
                    if (ticker.has("lastPrice")) {
                        double price = ticker.getDouble("lastPrice");
                        priceCache.put(symbol, price); // Update cache
                        
                        // Also update change cache if available
                        if (ticker.has("price24hPcnt")) {
                            double change = ticker.getDouble("price24hPcnt") * 100;
                            changeCache.put(symbol, change);
                        }
                        
                        return price;
                    }
                }
            }
            
            return 0.0; // Default price if not found
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching price: " + e.getMessage());
            return 0.0; // Default price on error
        }
    }
    
    /**
     * Get current price for a cryptocurrency asynchronously
     * @param cryptoId Coin ID or symbol
     * @param callback Callback to receive price
     */
    public void getCurrentPriceAsync(String cryptoId, PriceCallback callback) {
        // Convert to symbol format for Bybit API
        final String symbol = getSymbolFromId(cryptoId).toUpperCase() + "USDT";
        
        // Execute on background thread
        executorService.execute(() -> {
            try {
                // Build URL - use Bybit's ticker endpoint
                String url = String.format("%s/v5/market/tickers?category=spot&symbol=%s", BASE_URL, symbol);
                
                // Get response
                JSONObject response = getJsonObjectFromUrl(url);
                
                // Parse response
                final double price;
                final double change;
                
                if (response.has("result") && response.getJSONObject("result").has("list")) {
                    JSONArray list = response.getJSONObject("result").getJSONArray("list");
                    if (list.length() > 0) {
                        JSONObject ticker = list.getJSONObject(0);
                        if (ticker.has("lastPrice")) {
                            price = ticker.getDouble("lastPrice");
                            priceCache.put(symbol, price); // Update cache
                            
                            // Also update change cache if available
                            if (ticker.has("price24hPcnt")) {
                                change = ticker.getDouble("price24hPcnt") * 100;
                                changeCache.put(symbol, change);
                            } else {
                                change = 0.0;
                            }
                        } else {
                            price = 0.0;
                            change = 0.0;
                        }
                    } else {
                        price = 0.0;
                        change = 0.0;
                    }
                } else {
                    price = 0.0;
                    change = 0.0;
                }
                
                // Return result on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onPrice(price, change);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching price async: " + e.getMessage());
                
                // Return error on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onError(e);
                });
            }
        });
    }
    
    /**
     * Get market data for multiple cryptocurrencies asynchronously
     * @param symbols List of cryptocurrency symbols
     * @param callback Callback to receive market data
     */
    public void getMarketDataAsync(List<String> symbols, MarketDataCallback callback) {
        // Execute on background thread
        executorService.execute(() -> {
            try {
                Map<String, CryptoMarketData> marketDataMap = new HashMap<>();
                
                for (String symbol : symbols) {
                    // Convert to symbol format for Bybit API
                    final String apiSymbol = getSymbolFromId(symbol).toUpperCase() + "USDT";
                    
                    // Build URL - use Bybit's ticker endpoint
                    String url = String.format("%s/v5/market/tickers?category=spot&symbol=%s", BASE_URL, apiSymbol);
                    
                    // Get response
                    JSONObject response = getJsonObjectFromUrl(url);
                    
                    // Parse response
                    if (response.has("result") && response.getJSONObject("result").has("list")) {
                        JSONArray list = response.getJSONObject("result").getJSONArray("list");
                        if (list.length() > 0) {
                            JSONObject ticker = list.getJSONObject(0);
                            
                            CryptoMarketData marketData = new CryptoMarketData();
                            marketData.symbol = getSymbolFromId(symbol);
                            marketData.id = getIdFromSymbol(symbol);
                            
                            if (ticker.has("lastPrice")) {
                                marketData.currentPrice = ticker.getDouble("lastPrice");
                                priceCache.put(apiSymbol, marketData.currentPrice);
                            }
                            
                            if (ticker.has("price24hPcnt")) {
                                marketData.priceChangePercentage24h = ticker.getDouble("price24hPcnt") * 100;
                                changeCache.put(apiSymbol, marketData.priceChangePercentage24h);
                            }
                            
                            if (ticker.has("highPrice24h")) {
                                marketData.high24h = ticker.getDouble("highPrice24h");
                            }
                            
                            if (ticker.has("lowPrice24h")) {
                                marketData.low24h = ticker.getDouble("lowPrice24h");
                            }
                            
                            if (ticker.has("volume24h")) {
                                marketData.volume24h = ticker.getDouble("volume24h");
                            }
                            
                            marketDataMap.put(marketData.symbol, marketData);
                        }
                    }
                }
                
                // Return result on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onMarketData(marketDataMap);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error fetching market data async: " + e.getMessage());
                
                // Return error on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onError(e);
                });
            }
        });
    }
    
    /**
     * Get market data for all supported cryptocurrencies
     * @return Map of cryptocurrency symbol to market data
     * @throws IOException if API request fails
     * @throws JSONException if parsing response fails
     */
    public Map<String, CryptoMarketData> getMarketData(List<String> symbols) throws IOException, JSONException {
        Map<String, CryptoMarketData> marketDataMap = new HashMap<>();
        
        for (String symbol : symbols) {
            // Convert to symbol format for Bybit API
            final String apiSymbol = getSymbolFromId(symbol).toUpperCase() + "USDT";
            
            // Build URL - use Bybit's ticker endpoint
            String url = String.format("%s/v5/market/tickers?category=spot&symbol=%s", BASE_URL, apiSymbol);
            
            // Get response
            JSONObject response = getJsonObjectFromUrl(url);
            
            // Parse response
            if (response.has("result") && response.getJSONObject("result").has("list")) {
                JSONArray list = response.getJSONObject("result").getJSONArray("list");
                if (list.length() > 0) {
                    JSONObject ticker = list.getJSONObject(0);
                    
                    CryptoMarketData marketData = new CryptoMarketData();
                    marketData.symbol = getSymbolFromId(symbol);
                    marketData.id = getIdFromSymbol(symbol);
                    
                    if (ticker.has("lastPrice")) {
                        marketData.currentPrice = ticker.getDouble("lastPrice");
                        priceCache.put(apiSymbol, marketData.currentPrice);
                    }
                    
                    if (ticker.has("price24hPcnt")) {
                        marketData.priceChangePercentage24h = ticker.getDouble("price24hPcnt") * 100;
                        changeCache.put(apiSymbol, marketData.priceChangePercentage24h);
                    }
                    
                    if (ticker.has("highPrice24h")) {
                        marketData.high24h = ticker.getDouble("highPrice24h");
                    }
                    
                    if (ticker.has("lowPrice24h")) {
                        marketData.low24h = ticker.getDouble("lowPrice24h");
                    }
                    
                    if (ticker.has("volume24h")) {
                        marketData.volume24h = ticker.getDouble("volume24h");
                    }
                    
                    marketDataMap.put(marketData.symbol, marketData);
                }
            }
        }
        
        return marketDataMap;
    }
    
    /**
     * Get JSON object from URL with caching
     * @param url URL to fetch
     * @return JSONObject response
     * @throws IOException if request fails
     * @throws JSONException if parsing fails
     */
    private JSONObject getJsonObjectFromUrl(String url) throws IOException, JSONException {
        String responseStr = getFromUrl(url);
        return new JSONObject(responseStr);
    }
    
    /**
     * Get string response from URL with caching
     * @param url URL to fetch
     * @return String response
     * @throws IOException if request fails
     */
    private String getFromUrl(String url) throws IOException {
        // Check cache first
        CachedResponse cachedResponse = cache.get(url);
        if (cachedResponse != null && !cachedResponse.isExpired()) {
            return cachedResponse.response;
        }
        
        // Build request
        Request request = new Request.Builder()
                .url(url)
                .build();
        
        // Execute request
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response);
            }
            
            String responseStr = response.body().string();
            
            // Cache response
            cache.put(url, new CachedResponse(responseStr));
            
            return responseStr;
        }
    }
    
    /**
     * Callback interface for async price fetching
     */
    public interface PriceCallback {
        void onPrice(double price, double change);
        void onError(Exception e);
    }
    
    /**
     * Callback interface for async market data fetching
     */
    public interface MarketDataCallback {
        void onMarketData(Map<String, CryptoMarketData> marketData);
        void onError(Exception e);
    }
    
    /**
     * Data class for crypto market data
     */
    public static class CryptoMarketData {
        public String id;
        public String symbol;
        public double currentPrice;
        public double priceChangePercentage24h;
        public double high24h;
        public double low24h;
        public double volume24h;
    }
    
    /**
     * Private class for cached responses
     */
    private class CachedResponse {
        private final String response;
        private final long timestamp;
        
        CachedResponse(String response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TIME_MS;
        }
    }
}
