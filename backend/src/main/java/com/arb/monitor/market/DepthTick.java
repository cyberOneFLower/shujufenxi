package com.arb.monitor.market;

import java.util.List;

public record DepthTick(
    String exchange,
    String symbol,
    long ts,
    double bid1,
    double bid1Qty,
    double ask1,
    double ask1Qty,
    List<Level> bids5,
    List<Level> asks5) {

  public record Level(double p, double q) {}
}
