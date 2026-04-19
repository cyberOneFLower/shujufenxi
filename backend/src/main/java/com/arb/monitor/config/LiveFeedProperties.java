package com.arb.monitor.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 真实四所行情：OKX / Gate / Bitget 使用官方 WSS；MEXC 默认用官方 Protobuf 深度流（与 REST 二选一）。
 */
@ConfigurationProperties(prefix = "arb.live")
public class LiveFeedProperties {

  /** 为 true（默认）时启用真实行情；显式 false 时使用 {@link com.arb.monitor.market.MockCollectorService} */
  private boolean enabled = true;

  /**
   * api：从各所公共接口拉取 USDT 现货列表（见 {@link com.arb.monitor.market.feed.SymbolUniverseService}）；config：仅使用下方
   * {@link #symbols}
   */
  private String symbolSource = "api";

  /**
   * 仅 symbolSource=api 时生效：{@code intersection} 仅保留 arb.exchanges 里各所均有的交易对；{@code union}
   * 为各所可交易 USDT 现货的并集（每所只订阅本所实际存在的交易对，小币种更全，负载更高）。
   */
  private String universeMode = "union";

  /** 仅 symbolSource=api 时生效；0 表示不截断（使用交集全部交易对） */
  private int maxSymbols = 0;

  /** OKX/Bitget 单条 subscribe 内分批条数，避免报文过大 */
  private int subscribeChunkSize = 60;

  /** MEXC 单连接最多订阅数（官方约 30） */
  private int mexcShardSize = 30;

  private List<String> symbols = new ArrayList<>(List.of("BTC_USDT", "ETH_USDT"));

  /**
   * true：MEXC 使用 {@code wss://wbs-api.mexc.com/ws} + limit depth Protobuf（推荐，延迟最低）。
   * false：使用 REST /api/v3/depth 轮询（{@link #mexcPollMs}）。
   */
  private boolean mexcWebsocketEnabled = true;

  /** MEXC Spot WebSocket 基址 */
  private String mexcWsUrl = "wss://wbs-api.mexc.com/ws";

  /** Gate {@code spot.order_book} 推送间隔：20ms 比 100ms 更接近盘口（负载更高） */
  private String gateOrderBookInterval = "20ms";

  /** MEXC REST /api/v3/depth 轮询间隔（毫秒），仅 mexcWebsocketEnabled=false 时生效 */
  private long mexcPollMs = 400;

  /** WebSocket 断线后重连基础延迟（毫秒） */
  private long reconnectBaseMs = 2000;

  /**
   * 价差对齐窗口（毫秒）：{@link com.arb.monitor.market.AlignStore} 对每所仅保留最新深度后，只参与 ts ≥
   * (各所中最新的 ts − 本值) 的所；避免「按整秒取最大簇」漏所。≤0 表示不按时戳裁剪（各所最新一条全部一起算）。
   */
  private long alignWindowMs = 0;

  /** 各所 WSS 地址（一般无需改） */
  private String okxWsUrl = "wss://ws.okx.com:8443/ws/v5/public";
  private String gateWsUrl = "wss://api.gateio.ws/ws/v4/";
  private String bitgetWsUrl = "wss://ws.bitget.com/v2/ws/public";
  private String binanceWsUrl = "wss://stream.binance.com:9443/ws";

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

  public String getSymbolSource() {
    return symbolSource;
  }

  public void setSymbolSource(String symbolSource) {
    this.symbolSource = symbolSource;
  }

  public String getUniverseMode() {
    return universeMode;
  }

  public void setUniverseMode(String universeMode) {
    this.universeMode = universeMode;
  }

  public int getMaxSymbols() {
    return maxSymbols;
  }

  public void setMaxSymbols(int maxSymbols) {
    this.maxSymbols = maxSymbols;
  }

  public int getSubscribeChunkSize() {
    return subscribeChunkSize;
  }

  public void setSubscribeChunkSize(int subscribeChunkSize) {
    this.subscribeChunkSize = subscribeChunkSize;
  }

  public int getMexcShardSize() {
    return mexcShardSize;
  }

  public void setMexcShardSize(int mexcShardSize) {
    this.mexcShardSize = mexcShardSize;
  }

  public boolean isMexcWebsocketEnabled() {
    return mexcWebsocketEnabled;
  }

  public void setMexcWebsocketEnabled(boolean mexcWebsocketEnabled) {
    this.mexcWebsocketEnabled = mexcWebsocketEnabled;
  }

  public String getMexcWsUrl() {
    return mexcWsUrl;
  }

  public void setMexcWsUrl(String mexcWsUrl) {
    this.mexcWsUrl = mexcWsUrl;
  }

  public String getGateOrderBookInterval() {
    return gateOrderBookInterval;
  }

  public void setGateOrderBookInterval(String gateOrderBookInterval) {
    this.gateOrderBookInterval = gateOrderBookInterval;
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

  public long getAlignWindowMs() {
    return alignWindowMs;
  }

  public void setAlignWindowMs(long alignWindowMs) {
    this.alignWindowMs = alignWindowMs;
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

  public String getBinanceWsUrl() {
    return binanceWsUrl;
  }

  public void setBinanceWsUrl(String binanceWsUrl) {
    this.binanceWsUrl = binanceWsUrl;
  }
}
