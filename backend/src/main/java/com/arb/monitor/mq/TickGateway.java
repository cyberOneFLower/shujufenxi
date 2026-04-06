package com.arb.monitor.mq;

import com.arb.monitor.market.DepthTick;

/** 行情入口：直连内存或发往 RabbitMQ 队列。 */
public interface TickGateway {

  void publish(DepthTick tick);
}
