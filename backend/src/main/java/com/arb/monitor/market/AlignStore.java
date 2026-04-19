package com.arb.monitor.market;

import com.arb.monitor.config.LiveFeedProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AlignStore {

  private final Map<String, Map<String, DepthTick>> bySymbol = new HashMap<>();
  private final LiveFeedProperties live;

  public AlignStore(LiveFeedProperties live) {
    this.live = live;
  }

  public synchronized void upsert(DepthTick t) {
    bySymbol.computeIfAbsent(t.symbol(), k -> new HashMap<>()).put(t.exchange(), t);
  }

  /**
   * 各所在 {@link #upsert} 中仅保留最新一条；本方法在「短时间窗」内取齐各所后交给价差引擎。
   *
   * <p>令 tMax = 各所最新 ts 的最大值。若 {@code alignWindowMs > 0}，仅保留满足 {@code tMax - ts <=
   * alignWindowMs} 的所；若 {@code alignWindowMs <= 0}，不按时戳裁剪，所有已缓存的所最新深度一起参与。
   *
   * <p>若最终不足 2 所则返回 null。
   */
  public synchronized List<DepthTick> aligned(String symbol) {
    Map<String, DepthTick> m = bySymbol.get(symbol);
    if (m == null || m.size() < 2) return null;
    List<DepthTick> all = new ArrayList<>(m.values());
    long win = live.getAlignWindowMs();
    if (win <= 0) {
      return all;
    }
    long tMax = all.stream().mapToLong(DepthTick::ts).max().orElse(0L);
    List<DepthTick> out = new ArrayList<>();
    for (DepthTick t : all) {
      if (tMax - t.ts() <= win) {
        out.add(t);
      }
    }
    return out.size() >= 2 ? out : null;
  }

  public synchronized Set<String> allSymbols() {
    return Set.copyOf(bySymbol.keySet());
  }
}
