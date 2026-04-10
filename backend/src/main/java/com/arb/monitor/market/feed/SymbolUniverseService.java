package com.arb.monitor.market.feed;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 从四所公共 REST 拉取 USDT 现货交易对，取交集后作为统一监控列表（与 {@link LiveFeedProperties#getSymbolSource()} api
 * 配合）。
 */
@Service
public class SymbolUniverseService {

  private static final Logger log = LoggerFactory.getLogger(SymbolUniverseService.class);
  private static final String UA = "ArbMonitor-Symbols/1.0";

  private final LiveFeedProperties live;
  private final ObjectMapper mapper;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

  private volatile List<String> symbols =
      List.of("BTC_USDT", "ETH_USDT");

  public SymbolUniverseService(LiveFeedProperties live, ObjectMapper mapper) {
    this.live = live;
    this.mapper = mapper;
  }

  @PostConstruct
  public void init() {
    refresh();
  }

  /** 当前用于订阅/模拟的交易对（已排序、大写 BASE_USDT） */
  public List<String> getSymbols() {
    return symbols;
  }

  /** 重新拉取四所交集（启动或手动触发） */
  public synchronized void refresh() {
    if (!"api".equalsIgnoreCase(live.getSymbolSource())) {
      symbols = List.copyOf(live.getSymbols());
      log.info("Symbol universe: using config list, size={}", symbols.size());
      return;
    }
    try {
      Set<String> okx = fetchOkxUsdtSpot();
      Set<String> gate = fetchGateUsdtSpot();
      Set<String> bitget = fetchBitgetUsdtSpot();
      Set<String> mexc = fetchMexcUsdtSpot();

      Set<String> inter = new TreeSet<>(okx);
      inter.retainAll(gate);
      inter.retainAll(bitget);
      inter.retainAll(mexc);

      if (inter.isEmpty()) {
        log.warn("Symbol universe: empty intersection, fallback BTC/ETH");
        symbols = List.of("BTC_USDT", "ETH_USDT");
        return;
      }

      int max = live.getMaxSymbols();
      List<String> list = new ArrayList<>(inter);
      if (max > 0 && list.size() > max) {
        list = list.subList(0, max);
      }
      symbols = List.copyOf(list);
      log.info(
          "Symbol universe: okx={} gate={} bitget={} mexc={} -> intersection={} (using {})",
          okx.size(),
          gate.size(),
          bitget.size(),
          mexc.size(),
          inter.size(),
          symbols.size());
    } catch (Exception e) {
      log.warn("Symbol universe: refresh failed, using fallback: {}", e.toString());
      symbols = List.of("BTC_USDT", "ETH_USDT");
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
}
