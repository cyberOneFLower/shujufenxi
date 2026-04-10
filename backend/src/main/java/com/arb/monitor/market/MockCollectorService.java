package com.arb.monitor.market;

import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.config.LiveFeedProperties;
import com.arb.monitor.market.feed.SymbolUniverseService;
import com.arb.monitor.mq.TickGateway;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "arb.live", name = "enabled", havingValue = "false")
public class MockCollectorService {

  private static final int MOCK_SYMBOL_CAP = 60;

  private final ArbProperties props;
  private final LiveFeedProperties live;
  private final SymbolUniverseService symbolUniverse;
  private final TickGateway tickGateway;
  private int tick = 0;

  public MockCollectorService(
      ArbProperties props,
      LiveFeedProperties live,
      SymbolUniverseService symbolUniverse,
      TickGateway tickGateway) {
    this.props = props;
    this.live = live;
    this.symbolUniverse = symbolUniverse;
    this.tickGateway = tickGateway;
  }

  private List<String> symbolsForMock() {
    List<String> syms;
    if ("config".equalsIgnoreCase(live.getSymbolSource())) {
      syms =
          live.getSymbols().isEmpty()
              ? List.of("BTC_USDT", "ETH_USDT")
              : List.copyOf(live.getSymbols());
    } else {
      syms = symbolUniverse.getSymbols();
    }
    if (syms.isEmpty()) {
      syms = List.of("BTC_USDT", "ETH_USDT");
    }
    if (syms.size() > MOCK_SYMBOL_CAP) {
      return syms.subList(0, MOCK_SYMBOL_CAP);
    }
    return syms;
  }

  private static List<DepthTick.Level> mkLevels(double base, double spread, int n) {
    List<DepthTick.Level> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(new DepthTick.Level(base - spread * i, 0.5 + i * 0.1));
    }
    return out;
  }

  @Scheduled(fixedRate = 800)
  public void emit() {
    tick++;
    long sec = (System.currentTimeMillis() / 1000) * 1000;
    List<String> symbols = symbolsForMock();
    for (String symbol : symbols) {
      for (String exchange : props.exchanges()) {
        double jitter = (Math.sin(tick + exchange.length() + symbol.length()) * 2) / 100;
        double base =
            symbol.startsWith("BTC")
                ? 95000 * (1 + jitter)
                : symbol.startsWith("ETH")
                    ? 2500 * (1 + jitter)
                    : 100 * (1 + jitter);
        double spread = symbol.startsWith("BTC") ? 5 : symbol.startsWith("ETH") ? 0.5 : 0.01;
        double bid1 = base - spread;
        double ask1 = base + spread;
        DepthTick t =
            new DepthTick(
                exchange,
                symbol,
                sec,
                bid1,
                0.15,
                ask1,
                0.12,
                mkLevels(bid1, spread * 0.4, 5),
                mkLevels(ask1, -spread * 0.4, 5));
        tickGateway.publish(t);
      }
    }
  }
}
