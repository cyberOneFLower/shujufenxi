package com.arb.monitor.market.feed;

import com.arb.monitor.config.LiveFeedProperties;
import com.arb.monitor.market.DepthTick;
import com.arb.monitor.mq.TickGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 四所真实行情：OKX/Gate/Bitget 使用公共 WSS；MEXC 默认使用官方 Protobuf 限价深度流（与 REST 二选一）。
 *
 * @see <a href="https://www.okx.com/docs-v5/en/#order-book-trading-market-data-ws-order-book-channel">OKX books5</a>
 * @see <a href="https://www.gate.com/docs/developers/apiv4/ws/en/">Gate spot.order_book</a>
 * @see <a href="https://www.bitget.com/api-doc/common/websocket-intro">Bitget WSS</a>
 * @see <a href="https://www.mexc.com/api-docs/spot-v3/websocket-market-streams">MEXC limit depth (protobuf)</a>
 */
@Service
@DependsOn("symbolUniverseService")
@ConditionalOnProperty(
    prefix = "arb.live",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LiveFeedsService {

  private static final Logger log = LoggerFactory.getLogger(LiveFeedsService.class);
  private static final String UA_LIVE = "ArbMonitor-Live/1.0";
  private static final String MEXC_APP_PING = "{\"method\":\"PING\"}";

  private final LiveFeedProperties live;
  private final SymbolUniverseService symbolUniverse;
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

  private final ExecutorService workers =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "live-feed");
            t.setDaemon(true);
            return t;
          });
  private final ScheduledExecutorService mexcKeepAlive =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "mexc-ws-ping");
            t.setDaemon(true);
            return t;
          });
  /** MEXC 多分片连接，用于应用层 PING */
  private final CopyOnWriteArrayList<WebSocket> mexcShardSockets = new CopyOnWriteArrayList<>();

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  public LiveFeedsService(
      LiveFeedProperties live,
      SymbolUniverseService symbolUniverse,
      TickGateway tickGateway,
      ObjectMapper mapper) {
    this.live = live;
    this.symbolUniverse = symbolUniverse;
    this.tickGateway = tickGateway;
    this.mapper = mapper;
  }

  private List<String> feedSymbols() {
    if ("config".equalsIgnoreCase(live.getSymbolSource())) {
      return List.copyOf(live.getSymbols());
    }
    return symbolUniverse.getSymbols();
  }

  private static <T> List<List<T>> partition(List<T> list, int size) {
    if (list.isEmpty()) {
      return List.of();
    }
    int step = Math.max(1, size);
    List<List<T>> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i += step) {
      out.add(List.copyOf(list.subList(i, Math.min(list.size(), i + step))));
    }
    return out;
  }

  @jakarta.annotation.PostConstruct
  public void start() {
    workers.submit(this::runOkxLoop);
    workers.submit(this::runGateLoop);
    workers.submit(this::runBitgetLoop);
    if (live.isMexcWebsocketEnabled()) {
      int shard = Math.max(1, live.getMexcShardSize());
      for (List<String> part : partition(feedSymbols(), shard)) {
        final List<String> shardSyms = List.copyOf(part);
        workers.submit(() -> runMexcShardLoop(shardSyms));
      }
      mexcKeepAlive.scheduleAtFixedRate(this::pingMexcApp, 25, 25, TimeUnit.SECONDS);
    }
    log.info(
        "Live feeds started (OKX/Gate/Bitget WS + MEXC {}). symbolSource={} count={}",
        live.isMexcWebsocketEnabled() ? "WS protobuf" : "REST",
        live.getSymbolSource(),
        feedSymbols().size());
  }

  @PreDestroy
  public void shutdown() {
    stopped.set(true);
    mexcKeepAlive.shutdownNow();
    for (WebSocket w : mexcShardSockets) {
      if (w != null) {
        try {
          w.sendClose(WebSocket.NORMAL_CLOSURE, "");
        } catch (Exception ignored) {
        }
      }
    }
    mexcShardSockets.clear();
    workers.shutdownNow();
  }

  private void pingMexcApp() {
    if (stopped.get()) return;
    for (WebSocket w : mexcShardSockets) {
      if (w == null) continue;
      try {
        w.sendText(MEXC_APP_PING, true);
      } catch (Exception e) {
        log.debug("[mexc] ping: {}", e.toString());
      }
    }
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
        List<String> payloads = buildOkxSubscribeBatched(feedSymbols());
        http.newWebSocketBuilder()
            .buildAsync(URI.create(live.getOkxWsUrl()), new OkxListener(payloads, closed))
            .get(30, TimeUnit.SECONDS);
        closed.await();
      } catch (Exception e) {
        if (!stopped.get()) log.warn("[okx] feed error: {}", e.toString());
      }
      sleepReconnect();
    }
  }

  private List<String> buildOkxSubscribeBatched(List<String> symbols) {
    int chunk = Math.max(1, live.getSubscribeChunkSize());
    List<String> out = new ArrayList<>();
    for (int i = 0; i < symbols.size(); i += chunk) {
      List<String> part = symbols.subList(i, Math.min(symbols.size(), i + chunk));
      out.add(buildOkxSubscribe(part));
    }
    return out;
  }

  private String buildOkxSubscribe(List<String> syms) {
    ArrayNode args = mapper.createArrayNode();
    for (String sym : syms) {
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
    private final List<String> subscribePayloads;
    private final CountDownLatch closed;
    private final StringBuilder buf = new StringBuilder();

    OkxListener(List<String> subscribePayloads, CountDownLatch closed) {
      this.subscribePayloads = subscribePayloads;
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      for (String p : subscribePayloads) {
        webSocket.sendText(p, true);
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
            .buildAsync(
                URI.create(live.getGateWsUrl()), new GateListener(feedSymbols(), closed))
            .get(30, TimeUnit.SECONDS);
        closed.await();
      } catch (Exception e) {
        if (!stopped.get()) log.warn("[gate] feed error: {}", e.toString());
      }
      sleepReconnect();
    }
  }

  private final class GateListener implements WebSocket.Listener {
    private final List<String> symbols;
    private final CountDownLatch closed;
    private final StringBuilder buf = new StringBuilder();

    GateListener(List<String> symbols, CountDownLatch closed) {
      this.symbols = symbols;
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      long t = Instant.now().getEpochSecond();
      for (String sym : symbols) {
        ObjectNode req = mapper.createObjectNode();
        req.put("time", t);
        req.put("channel", "spot.order_book");
        req.put("event", "subscribe");
        ArrayNode payload = mapper.createArrayNode();
        payload.add(InstrumentIds.gatePair(sym));
        payload.add("5");
        payload.add(live.getGateOrderBookInterval());
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
        List<String> payloads = buildBitgetSubscribeBatched(feedSymbols());
        http.newWebSocketBuilder()
            .buildAsync(URI.create(live.getBitgetWsUrl()), new BitgetListener(payloads, closed))
            .get(30, TimeUnit.SECONDS);
        closed.await();
      } catch (Exception e) {
        if (!stopped.get()) log.warn("[bitget] feed error: {}", e.toString());
      }
      sleepReconnect();
    }
  }

  private List<String> buildBitgetSubscribeBatched(List<String> symbols) {
    int chunk = Math.max(1, live.getSubscribeChunkSize());
    List<String> out = new ArrayList<>();
    for (int i = 0; i < symbols.size(); i += chunk) {
      List<String> part = symbols.subList(i, Math.min(symbols.size(), i + chunk));
      ObjectNode sub = mapper.createObjectNode();
      sub.put("op", "subscribe");
      ArrayNode args = mapper.createArrayNode();
      for (String sym : part) {
        ObjectNode o = mapper.createObjectNode();
        o.put("instType", "SPOT");
        o.put("channel", "books5");
        o.put("instId", InstrumentIds.compact(sym));
        args.add(o);
      }
      sub.set("args", args);
      out.add(sub.toString());
    }
    return out;
  }

  private final class BitgetListener implements WebSocket.Listener {
    private final List<String> subscribePayloads;
    private final CountDownLatch closed;
    private final StringBuilder buf = new StringBuilder();

    BitgetListener(List<String> subscribePayloads, CountDownLatch closed) {
      this.subscribePayloads = subscribePayloads;
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      for (String p : subscribePayloads) {
        webSocket.sendText(p, true);
      }
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

  private void runMexcShardLoop(List<String> shardSyms) {
    while (!stopped.get() && !Thread.currentThread().isInterrupted()) {
      CountDownLatch closed = new CountDownLatch(1);
      try {
        String sub = buildMexcSubscribe(shardSyms);
        http.newWebSocketBuilder()
            .buildAsync(URI.create(live.getMexcWsUrl()), new MexcListener(sub, closed))
            .get(30, TimeUnit.SECONDS);
        closed.await();
      } catch (Exception e) {
        if (!stopped.get()) log.warn("[mexc] feed error: {}", e.toString());
      }
      sleepReconnect();
    }
  }

  private String buildMexcSubscribe(List<String> syms) {
    ArrayNode params = mapper.createArrayNode();
    for (String sym : syms) {
      String c = InstrumentIds.compact(sym);
      params.add("spot@public.limit.depth.v3.api.pb@" + c + "@5");
    }
    ObjectNode root = mapper.createObjectNode();
    root.put("method", "SUBSCRIPTION");
    root.set("params", params);
    return root.toString();
  }

  private final class MexcListener implements WebSocket.Listener {
    private final String subscribeMsg;
    private final CountDownLatch closed;
    private final StringBuilder textBuf = new StringBuilder();
    private final ByteArrayOutputStream binBuf = new ByteArrayOutputStream(768);

    MexcListener(String subscribeMsg, CountDownLatch closed) {
      this.subscribeMsg = subscribeMsg;
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      mexcShardSockets.add(webSocket);
      webSocket.sendText(subscribeMsg, true);
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      textBuf.append(data);
      if (last) {
        textBuf.setLength(0);
        webSocket.request(1);
      } else {
        webSocket.request(1);
      }
      return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer message, boolean last) {
      int n = message.remaining();
      if (n > 0) {
        byte[] chunk = new byte[n];
        message.get(chunk);
        binBuf.write(chunk, 0, chunk.length);
      }
      if (last) {
        byte[] full = binBuf.toByteArray();
        binBuf.reset();
        try {
          PushDataV3ApiWrapper w = PushDataV3ApiWrapper.parseFrom(full);
          if (w.hasPublicLimitDepths()) {
            String compactSym = w.getSymbol();
            if (compactSym == null || compactSym.length() < 7) {
              webSocket.request(1);
              return null;
            }
            String internal = InstrumentIds.fromBitgetInstId(compactSym);
            DepthTick t = MexcLimitDepthParser.fromPushWrapper("mexc", internal, w);
            if (t != null) {
              tickGateway.publish(t);
            }
          }
        } catch (Exception e) {
          log.debug("[mexc] protobuf: {}", e.toString());
        }
        webSocket.request(1);
      } else {
        webSocket.request(1);
      }
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      mexcShardSockets.remove(webSocket);
      closed.countDown();
      return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }
  }

  /** MEXC：仅当关闭 WebSocket 时使用 REST 深度轮询 */
  @Scheduled(fixedDelayString = "${arb.live.mexc-poll-ms:400}")
  public void pollMexcDepth() {
    if (stopped.get() || live.isMexcWebsocketEnabled()) return;
    for (String sym : feedSymbols()) {
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
