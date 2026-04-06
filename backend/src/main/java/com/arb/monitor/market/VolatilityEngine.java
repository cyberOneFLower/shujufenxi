package com.arb.monitor.market;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class VolatilityEngine {

  private static final long WINDOW_MS = 5 * 60 * 1000;

  private final Map<String, Deque<VolSample>> history = new HashMap<>();
  private final ObjectMapper mapper = new ObjectMapper();

  public record VolSample(long t, double mid) {}

  public record ExchangeSymbol(String exchange, String symbol) {}

  private String key(String exchange, String symbol) {
    try {
      return mapper.writeValueAsString(List.of(exchange, symbol));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public synchronized void onTick(DepthTick t) {
    String k = key(t.exchange(), t.symbol());
    double mid = (t.bid1() + t.ask1()) / 2;
    long now = t.ts();
    Deque<VolSample> arr = history.computeIfAbsent(k, x -> new ArrayDeque<>());
    arr.addLast(new VolSample(now, mid));
    long cutoff = now - WINDOW_MS;
    while (!arr.isEmpty() && arr.peekFirst().t() < cutoff) arr.removeFirst();
  }

  public synchronized Double changePct(String exchange, String symbol) {
    Deque<VolSample> arr = history.get(key(exchange, symbol));
    if (arr == null || arr.size() < 2) return null;
    VolSample last = arr.peekLast();
    long targetT = last.t() - WINDOW_MS;
    double baseline = arr.peekFirst().mid();
    for (VolSample s : arr) {
      if (s.t() >= targetT) {
        baseline = s.mid();
        break;
      }
      baseline = s.mid();
    }
    if (baseline <= 0) return null;
    return ((last.mid() - baseline) / baseline) * 100;
  }

  public synchronized List<ExchangeSymbol> listKeys() {
    List<ExchangeSymbol> out = new ArrayList<>();
    for (String k : history.keySet()) {
      try {
        @SuppressWarnings("unchecked")
        List<Object> pair = mapper.readValue(k, List.class);
        out.add(new ExchangeSymbol(String.valueOf(pair.get(0)), String.valueOf(pair.get(1))));
      } catch (JsonProcessingException ignored) {
        // skip
      }
    }
    return out;
  }
}
