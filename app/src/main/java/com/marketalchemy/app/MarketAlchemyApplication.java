package com.marketalchemy.app;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;

public class MarketAlchemyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Force dark mode for the entire app
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }
} 