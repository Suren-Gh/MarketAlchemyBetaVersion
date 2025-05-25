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
 * Client for interacting with CoinGecko API with built-in caching to handle rate limits
 */
public class CoinGeckoApiClient {
    
    private static final String TAG = "CoinGeckoApiClient";
    private static final String BASE_URL = "https://api.coingecko.com/api/v3";
    
    // Cache times
    private static final long CACHE_TIME_MS = 2000; // Cache for 2 seconds (real-time updates)
    private static final long CACHE_EXPIRY_MS = 60 * 1000; // 60 seconds
    
    // Singleton instance
    private static CoinGeckoApiClient instance;
    
    // OkHttp client with timeouts
    private final OkHttpClient client;
    
    // In-memory cache
    private final Map<String, CachedResponse> cache;
    
    // Map of CoinGecko IDs to symbols
    private final Map<String, String> idToSymbol;
    private final Map<String, String> symbolToId;
    
    // Price cache to avoid network calls
    private final Map<String, Double> priceCache;
    
    // Executor service for background tasks
    private final ExecutorService executorService;
    
    /**
     * Private constructor for singleton pattern
     */
    private CoinGeckoApiClient() {
        // Initialize OkHttp client with timeouts
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        // Initialize cache
        cache = new ConcurrentHashMap<>();
        
        // Initialize ID to symbol maps
        idToSymbol = new HashMap<>();
        symbolToId = new HashMap<>();
        
        // Initialize price cache
        priceCache = new HashMap<>();
        
        // Executor service for background tasks
        executorService = Executors.newFixedThreadPool(2);
        
        // Add common cryptocurrencies
        addCryptoMapping("bitcoin", "BTC");
        addCryptoMapping("ethereum", "ETH");
        addCryptoMapping("ripple", "XRP");
        addCryptoMapping("litecoin", "LTC");
        addCryptoMapping("bitcoin-cash", "BCH");
        addCryptoMapping("cardano", "ADA");
        addCryptoMapping("polkadot", "DOT");
        addCryptoMapping("stellar", "XLM");
        addCryptoMapping("chainlink", "LINK");
        addCryptoMapping("binancecoin", "BNB");
    }
    
    /**
     * Get singleton instance
     * @return CoinGeckoApiClient instance
     */
    public static synchronized CoinGeckoApiClient getInstance() {
        if (instance == null) {
            instance = new CoinGeckoApiClient();
        }
        return instance;
    }
    
    /**
     * Add a mapping from CoinGecko ID to symbol
     * @param id CoinGecko ID
     * @param symbol Cryptocurrency symbol
     */
    private void addCryptoMapping(String id, String symbol) {
        idToSymbol.put(id.toLowerCase(), symbol.toUpperCase());
        symbolToId.put(symbol.toUpperCase(), id.toLowerCase());
    }
    
    /**
     * Get CoinGecko ID from symbol
     * @param symbol Cryptocurrency symbol (e.g., BTC)
     * @return CoinGecko ID (e.g., bitcoin)
     */
    public String getIdFromSymbol(String symbol) {
        // Try to get ID from symbol
        String id = symbolToId.get(symbol.toUpperCase());
        if (id != null) {
            return id;
        }
        
        // If not found, assume it's already an ID
        return symbol.toLowerCase();
    }
    
    /**
     * Get cached price for a cryptocurrency
     * @param coinId Coin ID or symbol
     * @return Cached price in USD, or null if not cached
     */
    public Double getCachedPrice(String coinId) {
        // Convert symbol to ID if needed
        String id = getIdFromSymbol(coinId);
        return priceCache.get(id);
    }
    
    /**
     * Get symbol from CoinGecko ID
     * @param id CoinGecko ID (e.g., bitcoin)
     * @return Cryptocurrency symbol (e.g., BTC)
     */
    public String getSymbolFromId(String id) {
        return idToSymbol.getOrDefault(id.toLowerCase(), id.toUpperCase());
    }
    
    /**
     * Get current price for a cryptocurrency
     * @param coinId Coin ID or symbol
     * @return Current price in USD
     */
    public double getCurrentPrice(String coinId) {
        // This is a synchronous method that might be called from the main thread
        // We should avoid network operations on the main thread
        
        // First check our cache
        String id = getIdFromSymbol(coinId);
        Double cachedPrice = priceCache.get(id);
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
            // Build URL
            String url = String.format("%s/simple/price?ids=%s&vs_currencies=usd", BASE_URL, id);
            
            // Get response
            JSONObject response = getJsonObjectFromUrl(url);
            
            // Parse response
            if (response.has(id)) {
                JSONObject coin = response.getJSONObject(id);
                if (coin.has("usd")) {
                    double price = coin.getDouble("usd");
                    priceCache.put(id, price); // Update cache
                    return price;
                }
            }
            
            return 0.0; // Default price if not found
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error fetching price: " + e.getMessage());
            return 0.0; // Default price on error
        }
    }
    
    /**
     * Get current price for a cryptocurrency asynchronously
     * @param coinId Coin ID or symbol
     * @param callback Callback to receive price
     */
    public void getCurrentPriceAsync(String coinId, PriceCallback callback) {
        // Execute on background thread
        executorService.execute(() -> {
            try {
                // Convert symbol to ID if needed
                String id = getIdFromSymbol(coinId);
                
                // Build URL
                String url = String.format("%s/simple/price?ids=%s&vs_currencies=usd", BASE_URL, id);
                
                // Get response
                JSONObject response = getJsonObjectFromUrl(url);
                
                // Parse response
                final double price;
                if (response.has(id)) {
                    JSONObject coin = response.getJSONObject(id);
                    if (coin.has("usd")) {
                        price = coin.getDouble("usd");
                        priceCache.put(id, price); // Update cache
                    } else {
                        price = 0.0;
                    }
                } else {
                    price = 0.0;
                }
                
                // Return result on main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onPrice(price);
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
     * Callback interface for async price fetching
     */
    public interface PriceCallback {
        void onPrice(double price);
        void onError(Exception e);
    }
    
    /**
     * Get market data for multiple cryptocurrencies
     * @param ids List of CoinGecko IDs or symbols
     * @return Map of ID to market data
     * @throws IOException if API request fails
     * @throws JSONException if parsing response fails
     */
    public Map<String, CryptoMarketData> getMarketData(List<String> ids) throws IOException, JSONException {
        // Build ID parameter
        StringBuilder idParam = new StringBuilder();
        for (String id : ids) {
            // Convert symbol to ID if needed
            String coinId = symbolToId.getOrDefault(id.toUpperCase(), id.toLowerCase());
            idParam.append(coinId).append(",");
        }
        
        // Remove trailing comma
        if (idParam.length() > 0) {
            idParam.setLength(idParam.length() - 1);
        }
        
        // Build URL
        String url = BASE_URL + "/coins/markets?vs_currency=usd&ids=" + idParam + "&order=market_cap_desc&per_page=100&page=1&sparkline=false&price_change_percentage=24h";
        
        // Get response (cached if available)
        JSONArray response = getJsonArrayFromUrl(url);
        
        // Parse response
        Map<String, CryptoMarketData> result = new HashMap<>();
        for (int i = 0; i < response.length(); i++) {
            JSONObject item = response.getJSONObject(i);
            String id = item.getString("id");
            
            CryptoMarketData data = new CryptoMarketData();
            data.id = id;
            data.symbol = item.getString("symbol").toUpperCase();
            data.name = item.getString("name");
            data.currentPrice = item.getDouble("current_price");
            data.priceChangePercentage24h = item.getDouble("price_change_percentage_24h");
            data.marketCap = item.getLong("market_cap");
            data.totalVolume = item.getLong("total_volume");
            data.imageUrl = item.getString("image");
            
            result.put(id, data);
        }
        
        return result;
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
     * Get JSON array from URL with caching
     * @param url URL to fetch
     * @return JSONArray response
     * @throws IOException if request fails
     * @throws JSONException if parsing fails
     */
    private JSONArray getJsonArrayFromUrl(String url) throws IOException, JSONException {
        String responseStr = getFromUrl(url);
        return new JSONArray(responseStr);
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
     * Class to hold cached response with expiration
     */
    private static class CachedResponse {
        final String response;
        final long timestamp;
        
        CachedResponse(String response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TIME_MS;
        }
    }
    
    /**
     * Class to hold cryptocurrency market data
     */
    public static class CryptoMarketData {
        public String id;
        public String symbol;
        public String name;
        public double currentPrice;
        public double priceChangePercentage24h;
        public long marketCap;
        public long totalVolume;
        public String imageUrl;
    }
}
