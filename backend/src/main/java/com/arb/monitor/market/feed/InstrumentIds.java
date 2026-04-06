package com.arb.monitor.market.feed;

/** 内部统一符号 BTC_USDT 与各所 API 符号互转 */
public final class InstrumentIds {

  private InstrumentIds() {}

  public static String okxInstId(String internal) {
    // BTC_USDT -> BTC-USDT
    return internal.replace('_', '-');
  }

  /** Gate / 内部 均使用 BTC_USDT */
  public static String gatePair(String internal) {
    return internal;
  }

  /** Bitget / MEXC REST: BTCUSDT */
  public static String compact(String internal) {
    return internal.replace("_", "");
  }

  /** OKX 返回的 BTC-USDT */
  public static String fromOkxInstId(String instId) {
    return instId.replace('-', '_');
  }

  /** Bitget instId BTCUSDT / ETHUSDT -> BTC_USDT / ETH_USDT */
  public static String fromBitgetInstId(String compact) {
    if (compact == null || compact.length() < 7) return compact;
    if (compact.endsWith("USDT")) {
      return compact.substring(0, compact.length() - 4) + "_USDT";
    }
    return compact;
  }
}
