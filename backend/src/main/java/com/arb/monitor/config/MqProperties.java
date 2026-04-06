package com.arb.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RabbitMQ：行情入队削峰。enabled=false 时不创建连接，采集端直接写内存。
 */
@ConfigurationProperties(prefix = "arb.mq")
public class MqProperties {

  /** 为 true 时行情经队列再进入 MarketDataService（削峰） */
  private boolean enabled = false;

  private String host = "localhost";
  private int port = 5672;
  private String username = "guest";
  private String password = "guest";
  private String virtualHost = "/";
  /** 持久化队列名 */
  private String queue = "arb.depth.ticks";
  /**
   * 消费者预取条数：越大队列缓冲越多、单消费者吞吐越高；越小越平滑（削峰更明显）。
   */
  private int prefetch = 200;

  private int consumers = 1;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getVirtualHost() {
    return virtualHost;
  }

  public void setVirtualHost(String virtualHost) {
    this.virtualHost = virtualHost;
  }

  public String getQueue() {
    return queue;
  }

  public void setQueue(String queue) {
    this.queue = queue;
  }

  public int getPrefetch() {
    return prefetch;
  }

  public void setPrefetch(int prefetch) {
    this.prefetch = prefetch;
  }

  public int getConsumers() {
    return consumers;
  }

  public void setConsumers(int consumers) {
    this.consumers = consumers;
  }
}
