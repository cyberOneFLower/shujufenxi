package com.arb.monitor.mq;

import com.arb.monitor.config.MqProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

/**
 * 仅在 arb.mq.enabled=true 时装配；默认不连 Rabbit，避免本地无 Broker 时启动失败。
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(prefix = "arb.mq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

  @Bean
  ConnectionFactory rabbitConnectionFactory(MqProperties p) {
    CachingConnectionFactory cf = new CachingConnectionFactory();
    cf.setHost(p.getHost());
    cf.setPort(p.getPort());
    cf.setUsername(p.getUsername());
    cf.setPassword(p.getPassword());
    cf.setVirtualHost(p.getVirtualHost());
    return cf;
  }

  @Bean
  RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
    return new RabbitAdmin(connectionFactory);
  }

  @Bean
  Queue depthTickQueue(MqProperties p) {
    return QueueBuilder.durable(p.getQueue()).build();
  }

  @Bean
  Jackson2JsonMessageConverter tickJsonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }

  @Bean
  RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
    RabbitTemplate t = new RabbitTemplate(cf);
    t.setMessageConverter(conv);
    return t;
  }

  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory cf,
      Jackson2JsonMessageConverter conv,
      MqProperties p) {
    SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
    f.setConnectionFactory(cf);
    f.setMessageConverter(conv);
    f.setPrefetchCount(Math.max(1, p.getPrefetch()));
    f.setConcurrentConsumers(Math.max(1, p.getConsumers()));
    return f;
  }
}
