package com.arb.monitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_settings")
public class UserSettings {

  @Id
  @Column(name = "user_id")
  private String userId;

  @Column(name = "min_total_usd", nullable = false)
  private double minTotalUsd = 10;

  @Column(name = "spread_sort", nullable = false)
  private String spreadSort = "spread_pct_desc";

  @Column(name = "volatility_threshold_pct", nullable = false)
  private double volatilityThresholdPct = 10;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public double getMinTotalUsd() {
    return minTotalUsd;
  }

  public void setMinTotalUsd(double minTotalUsd) {
    this.minTotalUsd = minTotalUsd;
  }

  public String getSpreadSort() {
    return spreadSort;
  }

  public void setSpreadSort(String spreadSort) {
    this.spreadSort = spreadSort;
  }

  public double getVolatilityThresholdPct() {
    return volatilityThresholdPct;
  }

  public void setVolatilityThresholdPct(double volatilityThresholdPct) {
    this.volatilityThresholdPct = volatilityThresholdPct;
  }
}
