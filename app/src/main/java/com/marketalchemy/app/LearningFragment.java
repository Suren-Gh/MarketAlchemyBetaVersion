package com.marketalchemy.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import java.util.Random;

public class LearningFragment extends Fragment {

    private EditText searchInput;
    private CardView cardCryptoBasics, cardTradingStrategies, cardRiskManagement;
    private CardView cardBitcoinIntro, cardMarketCycles, cardTechnicalAnalysis, cardPortfolio;
    private CardView cardVideo1, cardVideo2, cardVideo3;
    private TextView textDailyTip;
    private Button buttonNextTip;
    
    private String[] dailyTips = {
        "Always set stop-loss orders to protect your capital from significant losses.",
        "Never invest more than you can afford to lose.",
        "Dollar-cost averaging can help reduce the impact of market volatility.",
        "Maintain a trading journal to track your decisions and learn from them.",
        "Technical analysis works best when combined with fundamental analysis.",
        "Market sentiment often drives short-term price movements.",
        "Focus on risk management before profit potential.",
        "Avoid FOMO (Fear of Missing Out) - it leads to poor trading decisions.",
        "Bitcoin's volatility can create both risks and opportunities for traders."
    };
    
    private Random random = new Random();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_learning, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        
        setupClickListeners();
        
        showRandomTip();
    }
    
    private void initializeViews(View view) {
        searchInput = view.findViewById(R.id.editSearch);
        
        cardCryptoBasics = view.findViewById(R.id.cardCryptoBasics);
        cardTradingStrategies = view.findViewById(R.id.cardTradingStrategies);
        cardRiskManagement = view.findViewById(R.id.cardRiskManagement);
        
        cardBitcoinIntro = view.findViewById(R.id.cardBitcoinIntro);
        cardMarketCycles = view.findViewById(R.id.cardMarketCycles);
        cardTechnicalAnalysis = view.findViewById(R.id.cardTechnicalAnalysis);
        cardPortfolio = view.findViewById(R.id.cardPortfolio);
        
        cardVideo1 = view.findViewById(R.id.cardVideo1);
        cardVideo2 = view.findViewById(R.id.cardVideo2);
        cardVideo3 = view.findViewById(R.id.cardVideo3);
        
        textDailyTip = view.findViewById(R.id.textDailyTip);
        buttonNextTip = view.findViewById(R.id.buttonNextTip);
    }
    
    private void setupClickListeners() {
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.getText().toString());
                return true;
            }
            return false;
        });
        
        cardCryptoBasics.setOnClickListener(v -> openCourse("Cryptocurrency Basics"));
        cardTradingStrategies.setOnClickListener(v -> openCourse("Trading Strategies"));
        cardRiskManagement.setOnClickListener(v -> openCourse("Risk Management"));
        
        cardBitcoinIntro.setOnClickListener(v -> openBitcoinArticle());
        cardMarketCycles.setOnClickListener(v -> openArticle("Understanding Market Cycles"));
        cardTechnicalAnalysis.setOnClickListener(v -> openArticle("Technical Analysis Fundamentals"));
        cardPortfolio.setOnClickListener(v -> openArticle("Creating a Diversified Portfolio"));
        
        cardVideo1.setOnClickListener(v -> playVideo("Introduction to Cryptocurrency Trading"));
        cardVideo2.setOnClickListener(v -> playVideo("Reading Candlestick Charts"));
        cardVideo3.setOnClickListener(v -> playVideo("Advanced Trading Strategies"));
        
        buttonNextTip.setOnClickListener(v -> showRandomTip());
    }
    
    private void performSearch(String query) {
        Toast.makeText(getContext(), "Searching for: " + query, Toast.LENGTH_SHORT).show();
    }
    
    private void openCourse(String courseTitle) {
        Toast.makeText(getContext(), "Opening course: " + courseTitle, Toast.LENGTH_SHORT).show();
    }
    
    private void openBitcoinArticle() {
        // Pass Bitcoin as the default cryptocurrency for the learning section
        BitcoinDetailFragment.navigate(this, "BTC", "Bitcoin");
    }
    
    private void openArticle(String articleTitle) {
        Toast.makeText(getContext(), "Opening article: " + articleTitle, Toast.LENGTH_SHORT).show();
    }
    
    private void playVideo(String videoTitle) {
        Toast.makeText(getContext(), "Playing video: " + videoTitle, Toast.LENGTH_SHORT).show();
    }
    
    private void showRandomTip() {
        int randomIndex = random.nextInt(dailyTips.length);
        textDailyTip.setText(dailyTips[randomIndex]);
    }
} 