package com.arb.monitor.market.feed;

import com.arb.monitor.market.DepthTick;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/** 从各所 JSON 解析买盘卖盘，构造 {@link DepthTick} */
public final class DepthBookParser {

  private DepthBookParser() {}

  public static DepthTick fromOkxBooks5(
      String exchange, String internalSymbol, JsonNode root) {
    JsonNode arg = root.path("arg");
    String instId = arg.path("instId").asText("");
    String sym = instId.isEmpty() ? internalSymbol : InstrumentIds.fromOkxInstId(instId);
    JsonNode data0 = root.path("data").path(0);
    long ts = Long.parseLong(data0.path("ts").asText("0"));
    if (ts == 0) ts = System.currentTimeMillis();
    List<List<String>> bids = readSide(data0.path("bids"));
    List<List<String>> asks = readSide(data0.path("asks"));
    return build(exchange, sym, ts, bids, asks);
  }

  public static DepthTick fromGateOrderBook(String exchange, JsonNode root) {
    JsonNode res = root.path("result");
    String sym = res.path("s").asText("");
    if (sym.isEmpty()) {
      sym = res.path("currency_pair").asText("");
    }
    sym = InstrumentIds.toInternalSpotSymbol(sym);
    if (sym.isEmpty()) {
      return null;
    }
    long ts = res.path("t").asLong(0);
    if (ts == 0) ts = System.currentTimeMillis();
    List<List<String>> bids = readSide(res.path("bids"));
    List<List<String>> asks = readSide(res.path("asks"));
    return build(exchange, sym, ts, bids, asks);
  }

  public static DepthTick fromBitgetBooks(String exchange, String internalSymbol, JsonNode root) {
    JsonNode arg = root.path("arg");
    String instId = arg.path("instId").asText("");
    String sym = instId.isEmpty() ? internalSymbol : InstrumentIds.fromBitgetInstId(instId);
    JsonNode data0 = root.path("data").path(0);
    long ts = 0;
    if (data0.hasNonNull("ts")) {
      ts = Long.parseLong(data0.get("ts").asText("0"));
    }
    if (ts == 0) ts = System.currentTimeMillis();
    List<List<String>> bids = readSide(data0.path("bids"));
    List<List<String>> asks = readSide(data0.path("asks"));
    return build(exchange, sym, ts, bids, asks);
  }

  /** MEXC GET /api/v3/depth JSON */
  public static DepthTick fromMexcRest(String exchange, String internalSymbol, JsonNode root) {
    long ts = System.currentTimeMillis();
    List<List<String>> bids = readSide(root.path("bids"));
    List<List<String>> asks = readSide(root.path("asks"));
    return build(exchange, internalSymbol, ts, bids, asks);
  }

  /**
   * Binance bookTicker.
   *
   * <p>Example fields: e=bookTicker, E=eventTime, s=BTCUSDT, b=bestBidPrice, B=bestBidQty,
   * a=bestAskPrice, A=bestAskQty.
   */
  public static DepthTick fromBinanceBookTicker(String exchange, JsonNode root) {
    String compact = root.path("s").asText("");
    String sym = InstrumentIds.fromBitgetInstId(compact);
    long ts = root.path("E").asLong(0);
    if (ts == 0) ts = System.currentTimeMillis();
    String bidPx = root.path("b").asText("");
    String bidQty = root.path("B").asText("");
    String askPx = root.path("a").asText("");
    String askQty = root.path("A").asText("");
    if (bidPx.isEmpty() || askPx.isEmpty() || bidQty.isEmpty() || askQty.isEmpty()) return null;
    List<List<String>> bids = List.of(List.of(bidPx, bidQty));
    List<List<String>> asks = List.of(List.of(askPx, askQty));
    return build(exchange, sym, ts, bids, asks);
  }

  private static List<List<String>> readSide(JsonNode arr) {
    List<List<String>> out = new ArrayList<>();
    if (!arr.isArray()) return out;
    for (JsonNode row : arr) {
      if (!row.isArray() || row.size() < 2) continue;
      List<String> pair = new ArrayList<>(2);
      pair.add(row.get(0).asText());
      pair.add(row.get(1).asText());
      out.add(pair);
    }
    return out;
  }

  private static DepthTick build(
      String exchange,
      String symbol,
      long ts,
      List<List<String>> bids,
      List<List<String>> asks) {
    if (bids.isEmpty() || asks.isEmpty()) {
      return null;
    }
    double bid1 = Double.parseDouble(bids.get(0).get(0));
    double bid1Qty = Double.parseDouble(bids.get(0).get(1));
    double ask1 = Double.parseDouble(asks.get(0).get(0));
    double ask1Qty = Double.parseDouble(asks.get(0).get(1));
    List<DepthTick.Level> bids5 = toLevels(bids, 5);
    List<DepthTick.Level> asks5 = toLevels(asks, 5);
    return new DepthTick(exchange, symbol, ts, bid1, bid1Qty, ask1, ask1Qty, bids5, asks5);
  }

  private static List<DepthTick.Level> toLevels(List<List<String>> side, int max) {
    List<DepthTick.Level> lv = new ArrayList<>();
    for (int i = 0; i < Math.min(max, side.size()); i++) {
      List<String> row = side.get(i);
      if (row.size() < 2) continue;
      lv.add(new DepthTick.Level(Double.parseDouble(row.get(0)), Double.parseDouble(row.get(1))));
    }
    return lv;
  }
}
