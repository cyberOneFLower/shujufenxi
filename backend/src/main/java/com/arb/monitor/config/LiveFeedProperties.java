package com.arb.monitor.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 真实四所行情：OKX / Gate / Bitget 使用官方 WSS JSON；MEXC Spot v3 主推 Protobuf，此处用 REST 深度作补充（见文档说明）。
 */
@ConfigurationProperties(prefix = "arb.live")
public class LiveFeedProperties {

  /** 为 true 时启用真实行情，关闭 {@link com.arb.monitor.market.MockCollectorService} */
  private boolean enabled = false;

  private List<String> symbols = new ArrayList<>(List.of("BTC_USDT", "ETH_USDT"));

  /** MEXC REST /api/v3/depth 轮询间隔（毫秒） */
  private long mexcPollMs = 400;

  /** WebSocket 断线后重连基础延迟（毫秒） */
  private long reconnectBaseMs = 2000;

  /** 各所 WSS 地址（一般无需改） */
  private String okxWsUrl = "wss://ws.okx.com:8443/ws/v5/public";
  private String gateWsUrl = "wss://api.gateio.ws/ws/v4/";
  private String bitgetWsUrl = "wss://ws.bitget.com/v2/ws/public";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public List<String> getSymbols() {
    return symbols;
  }

  public void setSymbols(List<String> symbols) {
    this.symbols = symbols;
  }

  public long getMexcPollMs() {
    return mexcPollMs;
  }

  public void setMexcPollMs(long mexcPollMs) {
    this.mexcPollMs = mexcPollMs;
  }

  public long getReconnectBaseMs() {
    return reconnectBaseMs;
  }

  public void setReconnectBaseMs(long reconnectBaseMs) {
    this.reconnectBaseMs = reconnectBaseMs;
  }

  public String getOkxWsUrl() {
    return okxWsUrl;
  }

  public void setOkxWsUrl(String okxWsUrl) {
    this.okxWsUrl = okxWsUrl;
  }

  public String getGateWsUrl() {
    return gateWsUrl;
  }

  public void setGateWsUrl(String gateWsUrl) {
    this.gateWsUrl = gateWsUrl;
  }

  public String getBitgetWsUrl() {
    return bitgetWsUrl;
  }

  public void setBitgetWsUrl(String bitgetWsUrl) {
    this.bitgetWsUrl = bitgetWsUrl;
  }
}
