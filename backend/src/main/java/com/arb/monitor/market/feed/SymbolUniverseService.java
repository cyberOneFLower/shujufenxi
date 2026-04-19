package com.arb.monitor.market.feed;

import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.config.LiveFeedProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 从各所公共 REST 拉取 USDT 现货交易对：{@link LiveFeedProperties#getUniverseMode()} 为 {@code union}
 * 时取并集（每所单独订阅本所存在的交易对，小币种更全）；为 {@code intersection} 时取多所交集（与旧版一致）。
 */
@Service
public class SymbolUniverseService {

  private static final Logger log = LoggerFactory.getLogger(SymbolUniverseService.class);
  private static final String UA = "ArbMonitor-Symbols/1.0";

  private final LiveFeedProperties live;
  private final ArbProperties arb;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

  private volatile List<String> symbols = List.of("BTC_USDT", "ETH_USDT");

  /** union 模式下各所实际订阅用的交易对（已应用 max-symbols 截断） */
  private volatile Map<String, Set<String>> symbolsByExchange = Map.of();

  public SymbolUniverseService(
      LiveFeedProperties live, ArbProperties arb, ObjectMapper mapper) {
    this.live = live;
    this.arb = arb;
    this.mapper = mapper;
  }

  @PostConstruct
  public void init() {
    refresh();
  }

  /** 当前监控的交易对全集（排序后；union 为并集，intersection 为交集），用于模拟盘等 */
  public List<String> getSymbols() {
    return symbols;
  }

  /**
   * 某所 WebSocket 应订阅的交易对：union 模式下仅含该所真实存在的交易对；intersection 下与 {@link
   * #getSymbols()} 相同。
   */
  public List<String> symbolsForExchange(String exchange) {
    if (!"api".equalsIgnoreCase(live.getSymbolSource())) {
      return List.copyOf(live.getSymbols());
    }
    if ("intersection".equalsIgnoreCase(live.getUniverseMode())) {
      return symbols;
    }
    String ex = exchange.toLowerCase(Locale.ROOT);
    Set<String> set = symbolsByExchange.get(ex);
    if (set == null || set.isEmpty()) {
      return List.of();
    }
    List<String> list = new ArrayList<>(set);
    Collections.sort(list);
    return list;
  }

  /** 重新拉取（启动或手动触发） */
  public synchronized void refresh() {
    if (!"api".equalsIgnoreCase(live.getSymbolSource())) {
      symbols = List.copyOf(live.getSymbols());
      symbolsByExchange = Map.of();
      log.info("Symbol universe: using config list, size={}", symbols.size());
      return;
    }
    try {
      Map<String, Set<String>> raw = fetchRawByEnabledExchanges();
      if (raw.isEmpty()) {
        log.warn("Symbol universe: no exchange symbols fetched, fallback BTC/ETH");
        symbols = List.of("BTC_USDT", "ETH_USDT");
        symbolsByExchange = Map.of();
        return;
      }

      if ("intersection".equalsIgnoreCase(live.getUniverseMode())) {
        refreshIntersection(raw);
      } else {
        refreshUnion(raw);
      }
    } catch (Exception e) {
      log.warn("Symbol universe: refresh failed, using fallback: {}", e.toString());
      symbols = List.of("BTC_USDT", "ETH_USDT");
      symbolsByExchange = Map.of();
    }
  }

  private void refreshIntersection(Map<String, Set<String>> raw) {
    List<Set<String>> sets = new ArrayList<>(raw.values());
    Set<String> inter = new TreeSet<>(sets.get(0));
    for (int i = 1; i < sets.size(); i++) {
      inter.retainAll(sets.get(i));
    }
    if (inter.isEmpty()) {
      log.warn("Symbol universe: empty intersection, fallback BTC/ETH");
      symbols = List.of("BTC_USDT", "ETH_USDT");
      symbolsByExchange = Map.of();
      return;
    }
    applyMaxAndFinalize(inter, null);
    log.info(
        "Symbol universe [intersection]: exchanges={} -> size={}",
        raw.keySet(),
        symbols.size());
  }

  private void refreshUnion(Map<String, Set<String>> raw) {
    Set<String> union = new TreeSet<>();
    for (Set<String> s : raw.values()) {
      union.addAll(s);
    }
    if (union.isEmpty()) {
      log.warn("Symbol universe: empty union, fallback BTC/ETH");
      symbols = List.of("BTC_USDT", "ETH_USDT");
      symbolsByExchange = Map.of();
      return;
    }
    int distinct = union.size();
    Map<String, Integer> perEx = new LinkedHashMap<>();
    raw.forEach((k, v) -> perEx.put(k, v.size()));
    applyMaxAndFinalize(union, raw);
    log.info(
        "Symbol universe [union]: perExSizes={}, unionDistinct={}, afterMax={}",
        perEx,
        distinct,
        symbols.size());
  }

  /** 对全集做 maxSymbols 截断，并在 union 模式下为各所生成截断后的订阅集合 */
  private void applyMaxAndFinalize(Set<String> universe, Map<String, Set<String>> rawByEx) {
    List<String> list = new ArrayList<>(universe);
    Collections.sort(list);
    int max = live.getMaxSymbols();
    if (max > 0 && list.size() > max) {
      list = new ArrayList<>(list.subList(0, max));
    }
    Set<String> allowed = new HashSet<>(list);
    symbols = List.copyOf(list);

    if (rawByEx == null) {
      symbolsByExchange = Map.of();
      return;
    }
    Map<String, Set<String>> out = new LinkedHashMap<>();
    for (Map.Entry<String, Set<String>> e : rawByEx.entrySet()) {
      Set<String> fil = new HashSet<>();
      for (String s : e.getValue()) {
        if (allowed.contains(s)) {
          fil.add(s);
        }
      }
      out.put(e.getKey(), fil);
    }
    symbolsByExchange = Collections.unmodifiableMap(out);
  }

  private Map<String, Set<String>> fetchRawByEnabledExchanges() throws Exception {
    Map<String, Set<String>> raw = new LinkedHashMap<>();
    if (exchangeEnabled("okx")) {
      raw.put("okx", fetchOkxUsdtSpot());
    }
    if (exchangeEnabled("gate")) {
      raw.put("gate", fetchGateUsdtSpot());
    }
    if (exchangeEnabled("bitget")) {
      raw.put("bitget", fetchBitgetUsdtSpot());
    }
    if (exchangeEnabled("mexc")) {
      raw.put("mexc", fetchMexcUsdtSpot());
    }
    if (exchangeEnabled("binance")) {
      raw.put("binance", fetchBinanceUsdtSpot());
    }
    return raw;
  }

  private boolean exchangeEnabled(String name) {
    try {
      List<String> ex = arb.exchanges();
      if (ex == null || ex.isEmpty()) {
        return true;
      }
      return ex.stream().anyMatch(name::equalsIgnoreCase);
    } catch (Exception e) {
      return true;
    }
  }

  private Set<String> fetchOkxUsdtSpot() throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder(
                URI.create("https://www.okx.com/api/v5/public/instruments?instType=SPOT"))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", UA)
            .GET()
            .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("OKX HTTP " + res.statusCode());
    }
    JsonNode root = mapper.readTree(res.body());
    Set<String> set = new HashSet<>();
    for (JsonNode n : root.path("data")) {
      if (!"SPOT".equals(n.path("instType").asText())) continue;
      if (!"USDT".equals(n.path("quoteCcy").asText())) continue;
      if (!"live".equals(n.path("state").asText())) continue;
      String instId = n.path("instId").asText();
      set.add(InstrumentIds.fromOkxInstId(instId));
    }
    return set;
  }

  private Set<String> fetchGateUsdtSpot() throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("https://api.gateio.ws/api/v4/spot/currency_pairs"))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", UA)
            .GET()
            .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("Gate HTTP " + res.statusCode());
    }
    JsonNode arr = mapper.readTree(res.body());
    Set<String> set = new HashSet<>();
    for (JsonNode n : arr) {
      if (!"USDT".equals(n.path("quote").asText())) continue;
      if (!"tradable".equals(n.path("trade_status").asText())) continue;
      set.add(n.path("id").asText());
    }
    return set;
  }

  private Set<String> fetchBitgetUsdtSpot() throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("https://api.bitget.com/api/v2/spot/public/symbols"))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", UA)
            .GET()
            .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("Bitget HTTP " + res.statusCode());
    }
    JsonNode root = mapper.readTree(res.body());
    Set<String> set = new HashSet<>();
    for (JsonNode n : root.path("data")) {
      if (!"USDT".equals(n.path("quoteCoin").asText())) continue;
      if (!"online".equals(n.path("status").asText())) continue;
      String sym = n.path("symbol").asText();
      set.add(InstrumentIds.fromBitgetInstId(sym));
    }
    return set;
  }

  private Set<String> fetchMexcUsdtSpot() throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("https://api.mexc.com/api/v3/exchangeInfo"))
            .timeout(Duration.ofSeconds(90))
            .header("User-Agent", UA)
            .GET()
            .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("MEXC HTTP " + res.statusCode());
    }
    JsonNode root = mapper.readTree(res.body());
    Set<String> set = new HashSet<>();
    for (JsonNode n : root.path("symbols")) {
      if (!"USDT".equals(n.path("quoteAsset").asText())) continue;
      if (!n.path("isSpotTradingAllowed").asBoolean(false)) continue;
      String sym = n.path("symbol").asText();
      set.add(InstrumentIds.fromBitgetInstId(sym));
    }
    return set;
  }

  /** Binance 现货 USDT 交易对 */
  private Set<String> fetchBinanceUsdtSpot() throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("https://api.binance.com/api/v3/exchangeInfo"))
            .timeout(Duration.ofSeconds(90))
            .header("User-Agent", UA)
            .GET()
            .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() != 200) {
      throw new IllegalStateException("Binance HTTP " + res.statusCode());
    }
    JsonNode root = mapper.readTree(res.body());
    Set<String> set = new HashSet<>();
    for (JsonNode n : root.path("symbols")) {
      if (!"USDT".equals(n.path("quoteAsset").asText())) continue;
      if (!"TRADING".equals(n.path("status").asText())) continue;
      String sym = n.path("symbol").asText();
      set.add(InstrumentIds.fromBitgetInstId(sym));
    }
    return set;
  }
}
