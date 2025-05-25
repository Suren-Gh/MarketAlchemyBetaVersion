package com.marketalchemy.app.api;

import android.util.Log;
import okhttp3.*;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

public class BinanceWebSocketClient {
    private static final String TAG = "BinanceWebSocketClient";
    private static final String WS_BASE_URL = "wss://testnet.binance.vision/ws";
    
    private final OkHttpClient client;
    private Map<String, WebSocket> webSockets;
    private OnPriceUpdateListener priceUpdateListener;
    private OnKlineUpdateListener klineUpdateListener;
    
    public interface OnPriceUpdateListener {
        void onPriceUpdate(String symbol, double price, double change);
    }
    
    public interface OnKlineUpdateListener {
        void onKlineUpdate(String symbol, double open, double high, double low, double close, long timestamp);
    }
    
    public BinanceWebSocketClient() {
        client = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();
        webSockets = new HashMap<>();
    }
    
    public void connect(String symbol) {
        // Connect to trade stream
        String tradeStreamUrl = WS_BASE_URL + "/" + symbol.toLowerCase() + "@trade";
        connectToStream(symbol, tradeStreamUrl, true);
        
        // Connect to kline stream
        String klineStreamUrl = WS_BASE_URL + "/" + symbol.toLowerCase() + "@kline_1m";
        connectToStream(symbol, klineStreamUrl, false);
    }
    
    private void connectToStream(String symbol, String url, boolean isTradeStream) {
        WebSocketListener listener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket connection opened for " + symbol + " " + (isTradeStream ? "trade" : "kline") + " stream");
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    
                    if (isTradeStream) {
                        // Handle trade stream data
                        String symbol = json.getString("s");
                        double price = Double.parseDouble(json.getString("p"));
                        double change = Double.parseDouble(json.getString("P"));
                        
                        if (priceUpdateListener != null) {
                            priceUpdateListener.onPriceUpdate(symbol, price, change);
                        }
                    } else {
                        // Handle kline stream data
                        String symbol = json.getString("s");
                        JSONObject kline = json.getJSONObject("k");
                        
                        double open = Double.parseDouble(kline.getString("o"));
                        double high = Double.parseDouble(kline.getString("h"));
                        double low = Double.parseDouble(kline.getString("l"));
                        double close = Double.parseDouble(kline.getString("c"));
                        long timestamp = kline.getLong("t") / 1000; // Convert to seconds
                        
                        if (klineUpdateListener != null) {
                            klineUpdateListener.onKlineUpdate(symbol, open, high, low, close, timestamp);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing WebSocket message: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket connection failed for " + symbol + ": " + t.getMessage());
                // Attempt to reconnect after a delay
                reconnect(symbol, url, isTradeStream);
            }
        };
        
        Request request = new Request.Builder()
            .url(url)
            .build();
            
        WebSocket webSocket = client.newWebSocket(request, listener);
        webSockets.put(symbol + (isTradeStream ? "_trade" : "_kline"), webSocket);
    }
    
    private void reconnect(String symbol, String url, boolean isTradeStream) {
        try {
            Thread.sleep(5000); // Wait 5 seconds before reconnecting
            connectToStream(symbol, url, isTradeStream);
        } catch (InterruptedException e) {
            Log.e(TAG, "Reconnection interrupted: " + e.getMessage());
        }
    }
    
    public void setPriceUpdateListener(OnPriceUpdateListener listener) {
        this.priceUpdateListener = listener;
    }
    
    public void setKlineUpdateListener(OnKlineUpdateListener listener) {
        this.klineUpdateListener = listener;
    }
    
    public void disconnect() {
        for (WebSocket webSocket : webSockets.values()) {
            if (webSocket != null) {
                webSocket.close(1000, "User requested disconnect");
            }
        }
        webSockets.clear();
    }
} 