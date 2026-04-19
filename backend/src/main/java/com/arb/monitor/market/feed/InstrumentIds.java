package com.arb.monitor.market.feed;

import java.util.Locale;

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

  /**
   * Bitget / MEXC 等紧凑符号 BTCUSDT、大小写不敏感（含 vanryusdt）→ BTC_USDT。
   */
  public static String fromBitgetInstId(String compact) {
    if (compact == null || compact.length() < 5) return compact;
    String c = compact.toUpperCase(Locale.ROOT);
    if (c.endsWith("USDT") && c.length() > 4) {
      return c.substring(0, c.length() - 4) + "_USDT";
    }
    return compact;
  }

  /**
   * Gate WS 等：字段可能为 {@code s}、{@code currency_pair}；带下划线或紧凑形式均归一为 {@code BASE_USDT}。
   */
  public static String toInternalSpotSymbol(String raw) {
    if (raw == null || raw.isBlank()) return "";
    String t = raw.trim();
    if (t.contains("_")) {
      return t.toUpperCase(Locale.ROOT);
    }
    return fromBitgetInstId(t);
  }
}
