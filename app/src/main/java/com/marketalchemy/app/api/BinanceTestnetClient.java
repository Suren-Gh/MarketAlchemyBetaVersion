package com.marketalchemy.app.api;

import android.util.Log;
import okhttp3.*;
import okio.Buffer;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class BinanceTestnetClient {
    private static final String TAG = "BinanceTestnetClient";
    private static final String BASE_URL = "https://testnet.binance.vision";
    private static final String API_KEY = "YOUR_TESTNET_API_KEY"; // Replace with your testnet API key
    private static final String API_SECRET = "YOUR_TESTNET_API_SECRET"; // Replace with your testnet API secret
    
    private final OkHttpClient client;
    
    public BinanceTestnetClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Get current price for a symbol
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     * @return Current price as double
     */
    public double getCurrentPrice(String symbol) throws IOException {
        String url = BASE_URL + "/api/v3/ticker/price?symbol=" + symbol;
        Request request = new Request.Builder()
                .url(url)
                .build();
                
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            
            String responseData = response.body().string();
            JSONObject json = new JSONObject(responseData);
            return Double.parseDouble(json.getString("price"));
        } catch (Exception e) {
            Log.e(TAG, "Error getting price: " + e.getMessage());
            throw new IOException("Failed to get price", e);
        }
    }
    
    /**
     * Place a market buy order
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     * @param quantity Amount to buy
     * @return Order response as JSONObject
     */
    public JSONObject placeMarketBuyOrder(String symbol, double quantity) throws IOException {
        String url = BASE_URL + "/api/v3/order";
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        // Create request body
        RequestBody formBody = new FormBody.Builder()
                .add("symbol", symbol)
                .add("side", "BUY")
                .add("type", "MARKET")
                .add("quantity", String.valueOf(quantity))
                .add("timestamp", timestamp)
                .build();
                
        // Add signature
        String signature = generateSignature(formBody);
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-MBX-APIKEY", API_KEY)
                .addHeader("X-MBX-SIGNATURE", signature)
                .post(formBody)
                .build();
                
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            
            String responseData = response.body().string();
            return new JSONObject(responseData);
        } catch (Exception e) {
            Log.e(TAG, "Error placing buy order: " + e.getMessage());
            throw new IOException("Failed to place buy order", e);
        }
    }
    
    /**
     * Place a market sell order
     * @param symbol Trading pair symbol (e.g., "BTCUSDT")
     * @param quantity Amount to sell
     * @return Order response as JSONObject
     */
    public JSONObject placeMarketSellOrder(String symbol, double quantity) throws IOException {
        String url = BASE_URL + "/api/v3/order";
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        // Create request body
        RequestBody formBody = new FormBody.Builder()
                .add("symbol", symbol)
                .add("side", "SELL")
                .add("type", "MARKET")
                .add("quantity", String.valueOf(quantity))
                .add("timestamp", timestamp)
                .build();
                
        // Add signature
        String signature = generateSignature(formBody);
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-MBX-APIKEY", API_KEY)
                .addHeader("X-MBX-SIGNATURE", signature)
                .post(formBody)
                .build();
                
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            
            String responseData = response.body().string();
            return new JSONObject(responseData);
        } catch (Exception e) {
            Log.e(TAG, "Error placing sell order: " + e.getMessage());
            throw new IOException("Failed to place sell order", e);
        }
    }
    
    /**
     * Get account balance
     * @return Account balance as JSONObject
     */
    public JSONObject getAccountBalance() throws IOException {
        String url = BASE_URL + "/api/v3/account";
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        Request request = new Request.Builder()
                .url(url + "?timestamp=" + timestamp)
                .addHeader("X-MBX-APIKEY", API_KEY)
                .addHeader("X-MBX-SIGNATURE", generateSignature(url + "?timestamp=" + timestamp))
                .get()
                .build();
                
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            
            String responseData = response.body().string();
            return new JSONObject(responseData);
        } catch (Exception e) {
            Log.e(TAG, "Error getting account balance: " + e.getMessage());
            throw new IOException("Failed to get account balance", e);
        }
    }
    
    /**
     * Generate signature for authenticated requests
     * @param data Data to sign
     * @return HMAC SHA256 signature
     */
    private String generateSignature(String data) {
        try {
            javax.crypto.Mac sha256_HMAC = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secret_key = new javax.crypto.spec.SecretKeySpec(
                    API_SECRET.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            return bytesToHex(sha256_HMAC.doFinal(data.getBytes()));
        } catch (Exception e) {
            Log.e(TAG, "Error generating signature: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Generate signature for FormBody
     * @param body FormBody to sign
     * @return HMAC SHA256 signature
     */
    private String generateSignature(RequestBody body) {
        try {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return generateSignature(buffer.readUtf8());
        } catch (Exception e) {
            Log.e(TAG, "Error generating signature for FormBody: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * Convert bytes to hex string
     * @param bytes Bytes to convert
     * @return Hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
} 