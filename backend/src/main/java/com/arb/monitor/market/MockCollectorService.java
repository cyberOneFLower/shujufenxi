package com.arb.monitor.market;

import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.mq.TickGateway;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "arb.live", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockCollectorService {

  private final ArbProperties props;
  private final TickGateway tickGateway;
  private int tick = 0;

  public MockCollectorService(ArbProperties props, TickGateway tickGateway) {
    this.props = props;
    this.tickGateway = tickGateway;
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
    List<String> symbols = List.of("BTC_USDT", "ETH_USDT");
    for (String symbol : symbols) {
      for (String exchange : props.exchanges()) {
        double jitter = (Math.sin(tick + exchange.length() + symbol.length()) * 2) / 100;
        double base = 95000 * (1 + jitter);
        double bid1 = base - 5;
        double ask1 = base + 5;
        DepthTick t =
            new DepthTick(
                exchange,
                symbol,
                sec,
                bid1,
                0.15,
                ask1,
                0.12,
                mkLevels(bid1, 2, 5),
                mkLevels(ask1, -2, 5));
        tickGateway.publish(t);
      }
    }
  }
}
