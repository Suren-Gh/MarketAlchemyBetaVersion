package com.marketalchemy.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TradingHistoryFragment extends Fragment {

    private static final String PREFS_NAME = "MarketAlchemyPrefs";
    private static final String KEY_TRANSACTION_HISTORY = "transaction_history";
    
    private ListView listView;
    private TextView emptyStateView;
    private SharedPreferences prefs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trading_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        listView = view.findViewById(R.id.listTransactions);
        emptyStateView = view.findViewById(R.id.tvEmptyState);
        
        // Get shared preferences
        prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Load and display transaction history
        loadTransactionHistory();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh transaction history when returning to this fragment
        loadTransactionHistory();
    }
    
    private void loadTransactionHistory() {
        String history = prefs.getString(KEY_TRANSACTION_HISTORY, "");
        
        if (history.isEmpty()) {
            // Show empty state
            listView.setVisibility(View.GONE);
            emptyStateView.setVisibility(View.VISIBLE);
        } else {
            // Parse transaction history and display in list
            List<Map<String, String>> transactions = parseTransactionHistory(history);
            
            if (transactions.isEmpty()) {
                listView.setVisibility(View.GONE);
                emptyStateView.setVisibility(View.VISIBLE);
            } else {
                // Show list view and hide empty state
                listView.setVisibility(View.VISIBLE);
                emptyStateView.setVisibility(View.GONE);
                
                // Create adapter
                SimpleAdapter adapter = new SimpleAdapter(
                    requireContext(),
                    transactions,
                    R.layout.item_transaction,
                    new String[]{"type", "date", "crypto", "amount", "price", "total"},
                    new int[]{R.id.tvTransactionType, R.id.tvTransactionDate, 
                             R.id.tvCryptoName, R.id.tvAmount, 
                             R.id.tvPrice, R.id.tvTotal}
                );
                
                // Set adapter
                listView.setAdapter(adapter);
            }
        }
    }
    
    private List<Map<String, String>> parseTransactionHistory(String history) {
        List<Map<String, String>> result = new ArrayList<>();
        String[] lines = history.split("\n");
        
        // Format for date in transactions
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
        
        // Regular expressions to extract transaction details
        Pattern buyPattern = Pattern.compile("BUY (\\d+\\.\\d+) (\\w+) @ \\$(\\d+\\.\\d+) = \\$(\\d+\\.\\d+)");
        Pattern sellPattern = Pattern.compile("SELL (\\d+\\.\\d+) (\\w+) @ \\$(\\d+\\.\\d+) = \\$(\\d+\\.\\d+)");
        
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            
            Map<String, String> transaction = new HashMap<>();
            
            // Set current date/time if not in the transaction string
            transaction.put("date", dateFormat.format(new Date()));
            
            Matcher buyMatcher = buyPattern.matcher(line);
            Matcher sellMatcher = sellPattern.matcher(line);
            
            if (buyMatcher.find()) {
                transaction.put("type", "BUY");
                transaction.put("amount", buyMatcher.group(1));
                transaction.put("crypto", buyMatcher.group(2));
                transaction.put("price", "$" + buyMatcher.group(3));
                transaction.put("total", "$" + buyMatcher.group(4));
                result.add(transaction);
            } else if (sellMatcher.find()) {
                transaction.put("type", "SELL");
                transaction.put("amount", sellMatcher.group(1));
                transaction.put("crypto", sellMatcher.group(2));
                transaction.put("price", "$" + sellMatcher.group(3));
                transaction.put("total", "$" + sellMatcher.group(4));
                result.add(transaction);
            }
        }
        
        return result;
    }
}
