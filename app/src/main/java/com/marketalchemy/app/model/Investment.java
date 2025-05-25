package com.marketalchemy.app.model;

import java.util.Date;

/**
 * Class representing a single cryptocurrency investment
 */
public class Investment {
    private String cryptoId;
    private double quantity;
    private double purchasePrice;
    private Date lastUpdated;
    
    public Investment(String cryptoId, double quantity, double purchasePrice, Date lastUpdated) {
        this.cryptoId = cryptoId;
        this.quantity = quantity;
        this.purchasePrice = purchasePrice;
        this.lastUpdated = lastUpdated;
    }
    
    // For Gson deserialization
    public Investment() {
    }
    
    public String getCryptoId() {
        return cryptoId;
    }
    
    public void setCryptoId(String cryptoId) {
        this.cryptoId = cryptoId;
    }
    
    public double getQuantity() {
        return quantity;
    }
    
    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }
    
    public double getPurchasePrice() {
        return purchasePrice;
    }
    
    public void setPurchasePrice(double purchasePrice) {
        this.purchasePrice = purchasePrice;
    }
    
    public Date getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    /**
     * Calculate current value based on current price
     * @param currentPrice Current price of the cryptocurrency
     * @return Current value of the investment
     */
    public double getCurrentValue(double currentPrice) {
        return quantity * currentPrice;
    }
    
    /**
     * Calculate profit/loss based on current price
     * @param currentPrice Current price of the cryptocurrency
     * @return Profit/loss value (positive for profit, negative for loss)
     */
    public double getProfitLoss(double currentPrice) {
        double currentValue = getCurrentValue(currentPrice);
        double investedValue = quantity * purchasePrice;
        return currentValue - investedValue;
    }
    
    /**
     * Calculate profit/loss percentage based on current price
     * @param currentPrice Current price of the cryptocurrency
     * @return Profit/loss percentage (positive for profit, negative for loss)
     */
    public double getProfitLossPercentage(double currentPrice) {
        double profitLoss = getProfitLoss(currentPrice);
        double investedValue = quantity * purchasePrice;
        if (investedValue == 0) {
            return 0;
        }
        return (profitLoss / investedValue) * 100;
    }
}
