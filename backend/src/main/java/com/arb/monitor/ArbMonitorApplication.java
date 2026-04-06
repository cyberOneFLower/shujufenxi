package com.arb.monitor;

import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.config.LiveFeedProperties;
import com.arb.monitor.config.MqProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = RabbitAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties({ArbProperties.class, LiveFeedProperties.class, MqProperties.class})
public class ArbMonitorApplication {

  public static void main(String[] args) {
    SpringApplication.run(ArbMonitorApplication.class, args);
  }
}
