package com.arb.monitor.mq;

import com.arb.monitor.config.MqProperties;
import com.arb.monitor.market.DepthTick;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "arb.mq", name = "enabled", havingValue = "true")
public class RabbitTickGateway implements TickGateway {

  private final RabbitTemplate rabbitTemplate;
  private final MqProperties mqProperties;

  public RabbitTickGateway(RabbitTemplate rabbitTemplate, MqProperties mqProperties) {
    this.rabbitTemplate = rabbitTemplate;
    this.mqProperties = mqProperties;
  }

  @Override
  public void publish(DepthTick tick) {
    rabbitTemplate.convertAndSend("", mqProperties.getQueue(), tick);
  }
}
