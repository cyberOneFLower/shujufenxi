package com.arb.monitor.service;

import com.arb.monitor.domain.User;
import com.arb.monitor.market.AlignStore;
import com.arb.monitor.market.DepthRowColorService;
import com.arb.monitor.market.MarketDataService;
import com.arb.monitor.market.SpreadEngine;
import com.arb.monitor.market.SpreadEngine.FiveAvg;
import com.arb.monitor.market.SpreadEngine.SpreadRow;
import com.arb.monitor.market.VolatilityEngine;
import com.arb.monitor.market.VolatilityEngine.ExchangeSymbol;
import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.repo.SpreadBlacklistRepository;
import com.arb.monitor.repo.UserRepository;
import com.arb.monitor.repo.UserSettingsRepository;
import com.arb.monitor.repo.VolatilityBlacklistRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.arb.monitor.domain.UserSettings;
import com.arb.monitor.domain.SpreadBlacklist;
import com.arb.monitor.domain.VolatilityBlacklist;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PayloadService {

  private final UserRepository userRepository;
  private final UserSettingsRepository settingsRepository;
  private final SpreadBlacklistRepository spreadBlacklistRepository;
  private final VolatilityBlacklistRepository volatilityBlacklistRepository;
  private final MarketDataService marketData;
  private final ArbProperties arbProperties;
  private final DepthRowColorService depthRowColorService;
  private final ObjectMapper mapper = new ObjectMapper();

  public PayloadService(
      UserRepository userRepository,
      UserSettingsRepository settingsRepository,
      SpreadBlacklistRepository spreadBlacklistRepository,
      VolatilityBlacklistRepository volatilityBlacklistRepository,
      MarketDataService marketData,
      ArbProperties arbProperties,
      DepthRowColorService depthRowColorService) {
    this.userRepository = userRepository;
    this.settingsRepository = settingsRepository;
    this.spreadBlacklistRepository = spreadBlacklistRepository;
    this.volatilityBlacklistRepository = volatilityBlacklistRepository;
    this.marketData = marketData;
    this.arbProperties = arbProperties;
    this.depthRowColorService = depthRowColorService;
  }

  private static String pairKey(String symbol, String buy, String sell) {
    return symbol + "|" + buy + "|" + sell;
  }

  public Map<String, Object> buildSpreadPayload(String userId) {
    UserSettings s =
        settingsRepository.findById(userId).orElse(null);
    double minUsd = s != null ? s.getMinTotalUsd() : arbProperties.minTotalUsd();
    String sort = s != null ? s.getSpreadSort() : "spread_pct_desc";

    Set<String> blocked = new HashSet<>();
    for (SpreadBlacklist b : spreadBlacklistRepository.findAll()) {
      blocked.add(b.getPairKey());
    }

    AlignStore align = marketData.alignStore();
    List<Map<String, Object>> rows = new ArrayList<>();

    for (String sym : align.allSymbols()) {
      var ticks = align.aligned(sym);
      if (ticks == null) continue;
      List<SpreadRow> spreadRows = SpreadEngine.computeSpreadRows(sym, ticks);
      Map<String, FiveAvg> five = SpreadEngine.fiveLevelSummary(ticks);
      Map<String, Map<String, Double>> fiveOut = new LinkedHashMap<>();
      for (var e : five.entrySet()) {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("bidAvg", e.getValue().bidAvg());
        m.put("askAvg", e.getValue().askAvg());
        fiveOut.put(e.getKey(), m);
      }
      for (SpreadRow r : spreadRows) {
        String pk = pairKey(r.symbol(), r.exchangeBuy(), r.exchangeSell());
        if (blocked.contains(pk)) continue;
        if (r.depthMinUsd() < minUsd) continue;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("symbol", r.symbol());
        row.put("label", r.label());
        row.put("exchangeBuy", r.exchangeBuy());
        row.put("exchangeSell", r.exchangeSell());
        row.put("spreadPct", r.spreadPct());
        row.put("buyTotalUsdt", r.buyTotalUsdt());
        row.put("sellTotalUsdt", r.sellTotalUsdt());
        row.put("bid1Buy", r.bid1Buy());
        row.put("ask1Buy", r.ask1Buy());
        row.put("bid1Sell", r.bid1Sell());
        row.put("ask1Sell", r.ask1Sell());
        row.put("pair_key", pk);
        row.put("depthColor", depthRowColorService.colorFor(r.depthMinUsd()));
        row.put("five", fiveOut);
        rows.add(row);
      }
    }

    Comparator<Map<String, Object>> cmp =
        Comparator.comparingDouble(m -> ((Number) m.get("spreadPct")).doubleValue());
    if ("spread_pct_asc".equals(sort)) rows.sort(cmp);
    else rows.sort(cmp.reversed());

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ts", System.currentTimeMillis());
    out.put("rows", rows);
    out.put("minUsd", minUsd);
    out.put("depthColorBands", depthRowColorService.bandsForApi());
    return out;
  }

  public Map<String, Object> buildVolPayload(String userId) {
    User u = userRepository.findById(userId).orElseThrow();
    UserSettings s = settingsRepository.findById(userId).orElse(null);
    double threshold = s != null ? s.getVolatilityThresholdPct() : 10;

    Set<String> blocked = new HashSet<>();
    for (VolatilityBlacklist b : volatilityBlacklistRepository.findAll()) {
      blocked.add(b.getKeyId());
    }

    VolatilityEngine vol = marketData.volatilityEngine();
    List<Map<String, Object>> items = new ArrayList<>();

    if (u.isVolatilityEnabled()) {
      for (ExchangeSymbol es : vol.listKeys()) {
        String keyId;
        try {
          keyId = mapper.writeValueAsString(List.of(es.exchange(), es.symbol()));
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
        if (blocked.contains(keyId)) continue;
        Double changePct = vol.changePct(es.exchange(), es.symbol());
        if (changePct == null) continue;
        Map<String, Object> it = new LinkedHashMap<>();
        it.put("exchange", es.exchange());
        it.put("symbol", es.symbol());
        it.put("changePct", changePct);
        it.put("key_id", keyId);
        it.put("alert", Math.abs(changePct) >= threshold);
        items.add(it);
      }
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ts", System.currentTimeMillis());
    out.put("threshold", threshold);
    out.put("items", items);
    return out;
  }
}
