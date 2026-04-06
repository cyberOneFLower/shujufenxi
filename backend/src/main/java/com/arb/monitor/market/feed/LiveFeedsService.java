package com.arb.monitor.market.feed;

import com.arb.monitor.config.LiveFeedProperties;
import com.arb.monitor.market.DepthTick;
import com.arb.monitor.mq.TickGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 四所真实行情：OKX/Gate/Bitget 使用文档中的公共 WSS；MEXC 使用 REST 深度（Spot v3 WS 多为 Protobuf，见官方说明）。
 *
 * @see <a href="https://www.okx.com/docs-v5/en/#order-book-trading-market-data-ws-order-book-channel">OKX books5</a>
 * @see <a href="https://www.gate.com/docs/developers/apiv4/ws/en/">Gate spot.order_book</a>
 * @see <a href="https://www.bitget.com/api-doc/common/websocket-intro">Bitget WSS</a>
 * @see <a href="https://www.mexc.com/api-docs/spot-v3/websocket-market-streams">MEXC（REST 兜底）</a>
 */
@Service
@ConditionalOnProperty(prefix = "arb.live", name = "enabled", havingValue = "true")
public class LiveFeedsService {

  private static final Logger log = LoggerFactory.getLogger(LiveFeedsService.class);
  private static final String UA_LIVE = "ArbMonitor-Live/1.0";

  private final LiveFeedProperties live;
  private final TickGateway tickGateway;
  private final ObjectMapper mapper;
  /** WebSocket 握手与 HTTP/2 行为解耦，避免与 REST 轮询共用一个 client 的内部状态 */
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  /** MEXC REST：独立连接、显式超时与 HTTP/2，便于复用与失败快速返回 */
  private final HttpClient mexcHttp =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_2)
          .connectTimeout(Duration.ofSeconds(10))
          .build();

  private final ExecutorService workers = Executors.newFixedThreadPool(3);
  private final AtomicBoolean stopped = new AtomicBoolean(false);

  public LiveFeedsService(
      LiveFeedProperties live, TickGateway tickGateway, ObjectMapper mapper) {
    this.live = live;
    this.tickGateway = tickGateway;
    this.mapper = mapper;
  }

  @jakarta.annotation.PostConstruct
  public void start() {
    workers.submit(this::runOkxLoop);
    workers.submit(this::runGateLoop);
    workers.submit(this::runBitgetLoop);
    log.info(
        "Live feeds started (OKX/Gate/Bitget WS + MEXC REST). symbols={}",
        live.getSymbols());
  }

  @PreDestroy
  public void shutdown() {
    stopped.set(true);
    workers.shutdownNow();
  }

  private void sleepReconnect() {
    try {
      Thread.sleep(live.getReconnectBaseMs());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void runOkxLoop() {
    while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
      CountDownLatch closed = new CountDownLatch(1);
      try {
        String sub = buildOkxSubscribe();
        http.newWebSocketBuilder()
            .buildAsync(URI.create(live.getOkxWsUrl()), new OkxListener(sub, closed))
            .get(30, TimeUnit.SECONDS);
        closed.await();
      } catch (Exception e) {
        if (!stopped.get()) log.warn("[okx] feed error: {}", e.toString());
      }
      sleepReconnect();
    }
  }

  private String buildOkxSubscribe() {
    ArrayNode args = mapper.createArrayNode();
    for (String sym : live.getSymbols()) {
      ObjectNode o = mapper.createObjectNode();
      o.put("channel", "books5");
      o.put("instId", InstrumentIds.okxInstId(sym));
      args.add(o);
    }
    ObjectNode root = mapper.createObjectNode();
    root.put("op", "subscribe");
    root.set("args", args);
    return root.toString();
  }

  private final class OkxListener implements WebSocket.Listener {
    private final String subscribeMsg;
    private final CountDownLatch closed;
    private final StringBuilder buf = new StringBuilder();

    OkxListener(String subscribeMsg, CountDownLatch closed) {
      this.subscribeMsg = subscribeMsg;
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.sendText(subscribeMsg, true);
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buf.append(data);
      if (last) {
        String text = buf.toString();
        buf.setLength(0);
        try {
          JsonNode root = mapper.readTree(text);
          if (root.has("event") && "subscribe".equals(root.get("event").asText())) {
            webSocket.request(1);
            return null;
          }
          if (root.has("event") && "error".equals(root.get("event").asText())) {
            log.warn("[okx] ws error: {}", text);
            webSocket.request(1);
            return null;
          }
          JsonNode arg = root.path("arg");
          if (!"books5".equals(arg.path("channel").asText())) {
            webSocket.request(1);
            return null;
          }
          DepthTick t =
              DepthBookParser.fromOkxBooks5(
                  "okx", InstrumentIds.fromOkxInstId(arg.path("instId").asText()), root);
          if (t != null) {
            tickGateway.publish(t);
          }
        } catch (Exception e) {
          log.debug("[okx] parse: {}", e.toString());
        }
        webSocket.request(1);
      } else {
        webSocket.request(1);
      }
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      closed.countDown();
      return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }
  }

  private void runGateLoop() {
    while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
      CountDownLatch closed = new CountDownLatch(1);
      try {
        http.newWebSocketBuilder()
            .buildAsync(URI.create(live.getGateWsUrl()), new GateListener(closed))
            .get(30, TimeUnit.SECONDS);
        closed.await();
      } catch (Exception e) {
        if (!stopped.get()) log.warn("[gate] feed error: {}", e.toString());
      }
      sleepReconnect();
    }
  }

  private final class GateListener implements WebSocket.Listener {
    private final CountDownLatch closed;
    private final StringBuilder buf = new StringBuilder();

    GateListener(CountDownLatch closed) {
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      long t = Instant.now().getEpochSecond();
      for (String sym : live.getSymbols()) {
        ObjectNode req = mapper.createObjectNode();
        req.put("time", t);
        req.put("channel", "spot.order_book");
        req.put("event", "subscribe");
        ArrayNode payload = mapper.createArrayNode();
        payload.add(InstrumentIds.gatePair(sym));
        payload.add("5");
        payload.add("100ms");
        req.set("payload", payload);
        webSocket.sendText(req.toString(), true);
      }
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buf.append(data);
      if (last) {
        String text = buf.toString();
        buf.setLength(0);
        try {
          JsonNode root = mapper.readTree(text);
          if (!"update".equals(root.path("event").asText())) {
            webSocket.request(1);
            return null;
          }
          DepthTick tick = DepthBookParser.fromGateOrderBook("gate", root);
          if (tick != null) {
            tickGateway.publish(tick);
          }
        } catch (Exception e) {
          log.debug("[gate] parse: {}", e.toString());
        }
        webSocket.request(1);
      } else {
        webSocket.request(1);
      }
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      closed.countDown();
      return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }
  }

  private void runBitgetLoop() {
    while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
      CountDownLatch closed = new CountDownLatch(1);
      try {
        ObjectNode sub = mapper.createObjectNode();
        sub.put("op", "subscribe");
        ArrayNode args = mapper.createArrayNode();
        for (String sym : live.getSymbols()) {
          ObjectNode o = mapper.createObjectNode();
          o.put("instType", "SPOT");
          o.put("channel", "books5");
          o.put("instId", InstrumentIds.compact(sym));
          args.add(o);
        }
        sub.set("args", args);
        String subStr = sub.toString();
        http.newWebSocketBuilder()
            .buildAsync(URI.create(live.getBitgetWsUrl()), new BitgetListener(subStr, closed))
            .get(30, TimeUnit.SECONDS);
        closed.await();
      } catch (Exception e) {
        if (!stopped.get()) log.warn("[bitget] feed error: {}", e.toString());
      }
      sleepReconnect();
    }
  }

  private final class BitgetListener implements WebSocket.Listener {
    private final String subscribeMsg;
    private final CountDownLatch closed;
    private final StringBuilder buf = new StringBuilder();

    BitgetListener(String subscribeMsg, CountDownLatch closed) {
      this.subscribeMsg = subscribeMsg;
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.sendText(subscribeMsg, true);
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buf.append(data);
      if (last) {
        String text = buf.toString();
        buf.setLength(0);
        if ("pong".equalsIgnoreCase(text.trim())) {
          webSocket.request(1);
          return null;
        }
        try {
          JsonNode root = mapper.readTree(text);
          if ("subscribe".equals(root.path("event").asText())) {
            webSocket.request(1);
            return null;
          }
          JsonNode arg = root.path("arg");
          if (!"books5".equals(arg.path("channel").asText())) {
            webSocket.request(1);
            return null;
          }
          String internal = InstrumentIds.fromBitgetInstId(arg.path("instId").asText(""));
          DepthTick t = DepthBookParser.fromBitgetBooks("bitget", internal, root);
          if (t != null) {
            tickGateway.publish(t);
          }
        } catch (Exception e) {
          log.debug("[bitget] parse: {}", e.toString());
        }
        webSocket.request(1);
      } else {
        webSocket.request(1);
      }
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      closed.countDown();
      return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }
  }

  /** MEXC：REST 深度（避免 Spot v3 WS Protobuf） */
  @Scheduled(fixedDelayString = "${arb.live.mexc-poll-ms:400}")
  public void pollMexcDepth() {
    if (stopped.get()) return;
    for (String sym : live.getSymbols()) {
      try {
        String c = InstrumentIds.compact(sym);
        URI uri = URI.create("https://api.mexc.com/api/v3/depth?symbol=" + c + "&limit=5");
        HttpRequest req =
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", UA_LIVE)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> res = mexcHttp.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) continue;
        JsonNode root = mapper.readTree(res.body());
        DepthTick t = DepthBookParser.fromMexcRest("mexc", sym, root);
        if (t != null) {
          tickGateway.publish(t);
        }
      } catch (Exception e) {
        log.debug("[mexc] rest: {}", e.toString());
      }
    }
  }
}
