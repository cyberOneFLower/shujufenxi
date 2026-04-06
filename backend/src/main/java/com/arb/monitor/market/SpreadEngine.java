package com.arb.monitor.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpreadEngine {

  private SpreadEngine() {}

  public record FiveAvg(double bidAvg, double askAvg) {}

  public record SpreadRow(
      String symbol,
      String label,
      String exchangeBuy,
      String exchangeSell,
      double spreadPct,
      double buyLegTotalUsd,
      double sellLegTotalUsd,
      double depthMinUsd,
      double bid1Buy,
      double ask1Buy,
      double bid1Sell,
      double ask1Sell) {}

  private static double avg5(List<DepthTick.Level> side) {
    if (side == null || side.isEmpty()) return 0;
    double pq = 0, q = 0;
    for (DepthTick.Level x : side) {
      pq += x.p() * x.q();
      q += x.q();
    }
    return q > 0 ? pq / q : 0;
  }

  public static Map<String, FiveAvg> fiveLevelSummary(List<DepthTick> ticks) {
    Map<String, FiveAvg> five = new HashMap<>();
    for (DepthTick t : ticks) {
      five.put(
          t.exchange(),
          new FiveAvg(avg5(t.bids5()), avg5(t.asks5())));
    }
    return five;
  }

  public static List<SpreadRow> computeSpreadRows(String symbol, List<DepthTick> ticks) {
    List<SpreadRow> rows = new ArrayList<>();
    for (int i = 0; i < ticks.size(); i++) {
      for (int j = 0; j < ticks.size(); j++) {
        if (i == j) continue;
        DepthTick buy = ticks.get(i);
        DepthTick sell = ticks.get(j);
        double ask = buy.ask1();
        double bid = sell.bid1();
        if (ask <= 0 || bid <= 0) continue;
        double spreadPct = ((bid - ask) / ask) * 100;
        double buyLeg = buy.ask1() * buy.ask1Qty();
        double sellLeg = sell.bid1() * sell.bid1Qty();
        double depthMin = Math.min(buyLeg, sellLeg);
        rows.add(
            new SpreadRow(
                symbol,
                buy.exchange() + "买 → " + sell.exchange() + "卖",
                buy.exchange(),
                sell.exchange(),
                spreadPct,
                buyLeg,
                sellLeg,
                depthMin,
                buy.bid1(),
                buy.ask1(),
                sell.bid1(),
                sell.ask1()));
      }
    }
    rows.sort(Comparator.comparingDouble(SpreadRow::spreadPct).reversed());
    return rows;
  }
}
