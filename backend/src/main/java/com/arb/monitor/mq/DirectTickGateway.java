package com.arb.monitor.mq;

import com.arb.monitor.market.DepthTick;
import com.arb.monitor.market.MarketDataService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "arb.mq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DirectTickGateway implements TickGateway {

  private final MarketDataService marketData;

  public DirectTickGateway(MarketDataService marketData) {
    this.marketData = marketData;
  }

  @Override
  public void publish(DepthTick tick) {
    marketData.onTick(tick);
  }
}
