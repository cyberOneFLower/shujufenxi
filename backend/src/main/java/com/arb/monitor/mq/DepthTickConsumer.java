package com.arb.monitor.mq;

import com.arb.monitor.market.DepthTick;
import com.arb.monitor.market.MarketDataService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "arb.mq", name = "enabled", havingValue = "true")
public class DepthTickConsumer {

  private final MarketDataService marketData;

  public DepthTickConsumer(MarketDataService marketData) {
    this.marketData = marketData;
  }

  @RabbitListener(queues = "${arb.mq.queue}")
  public void onTick(DepthTick tick) {
    marketData.onTick(tick);
  }
}
