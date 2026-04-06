package com.arb.monitor.market;

public final class DepthColorUtil {

  private DepthColorUtil() {}

  public static String depthColorUsd(double usd) {
    if (usd >= 1000) return "red";
    if (usd >= 500) return "yellow";
    if (usd >= 100) return "blue";
    return "white";
  }
}
