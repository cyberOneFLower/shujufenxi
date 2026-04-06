package com.arb.monitor.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AlignStore {

  private final Map<String, Map<String, DepthTick>> bySymbol = new HashMap<>();

  public synchronized void upsert(DepthTick t) {
    bySymbol.computeIfAbsent(t.symbol(), k -> new HashMap<>()).put(t.exchange(), t);
  }

  public synchronized List<DepthTick> aligned(String symbol) {
    Map<String, DepthTick> m = bySymbol.get(symbol);
    if (m == null || m.size() < 2) return null;
    Map<Long, List<DepthTick>> bySec = new HashMap<>();
    for (DepthTick t : m.values()) {
      long s = t.ts() / 1000;
      bySec.computeIfAbsent(s, k -> new ArrayList<>()).add(t);
    }
    List<DepthTick> best = null;
    for (List<DepthTick> arr : bySec.values()) {
      if (arr.size() >= 2 && (best == null || arr.size() > best.size())) best = arr;
    }
    return best;
  }

  public synchronized Set<String> allSymbols() {
    return Set.copyOf(bySymbol.keySet());
  }
}
