package com.marketalchemy.app.utils;

import android.app.Activity;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {
    private static final String THEME_PREFS = "ThemePrefs";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";

    public static void applyTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(THEME_PREFS, Activity.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DARK_MODE, true).apply();
        
        // Always use dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    // Initialize dark mode on app start
    public static void initDarkMode() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    public static boolean isDarkModeEnabled() {
        // Always return true as only dark mode is supported
        return true;
    }
} 