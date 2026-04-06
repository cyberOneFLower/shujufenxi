package com.arb.monitor.service;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/**
 * 对各交易所<strong>公共 REST</strong>做一次 GET，测量往返延时（毫秒），无需 API Key。
 * 用于评估本机到各所网络质量，不代表 WebSocket 或下单延时。
 *
 * <p>若部分所失败、部分成功，多为<strong>本地网络/地区/防火墙/代理/运营商</strong>对特定域名或 TLS 的限制，
 * 与业务代码无关；可换网络（如热点）、检查代理、或在新加坡等合规地区机器上复测。
 *
 * <p>调优：批量 DNS 预解析、单次预热、专用 HttpClient 线程池、identity 编码、丢弃响应体采样、各所并行；
 * 物理 RTT 仍由线路决定。
 */
@Service
public class ExchangeLatencyService {

  private static final String UA = "ArbMonitor-LatencyTest/1.0";
  private static final Duration REQ_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
  private static final String ACCEPT_ENCODING_IDENTITY = "identity";

  private static final Map<String, String> PUBLIC_GET =
      Map.of(
          "okx", "https://www.okx.com/api/v5/public/time",
          "gate", "https://api.gateio.ws/api/v4/spot/time",
          "bitget", "https://api.bitget.com/api/v2/public/time",
          "mexc", "https://api.mexc.com/api/v3/ping");

  /** HttpClient 内部 HTTP/2 / TLS 回调，与业务线程池分离，减少互相抢线程 */
  private final ExecutorService httpClientExecutor =
      Executors.newFixedThreadPool(
          8,
          r -> {
            Thread t = new Thread(r, "jdk-http-client");
            t.setDaemon(true);
            return t;
          });

  private final ExecutorService latencyWorkers =
      Executors.newFixedThreadPool(
          16,
          r -> {
            Thread t = new Thread(r, "exchange-latency");
            t.setDaemon(true);
            return t;
          });

  private final HttpClient http =
      HttpClient.newBuilder()
          .executor(httpClientExecutor)
          .version(HttpClient.Version.HTTP_2)
          .connectTimeout(CONNECT_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  @PreDestroy
  void shutdownExecutors() {
    shutdownPool(httpClientExecutor);
    shutdownPool(latencyWorkers);
  }

  private static void shutdownPool(ExecutorService pool) {
    pool.shutdown();
    try {
      if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
        pool.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      pool.shutdownNow();
    }
  }

  public Map<String, Object> measure(List<String> exchanges, int rounds) {
    int r = Math.max(1, Math.min(rounds, 10));
    prefetchDnsForExchanges(exchanges);

    List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>(exchanges.size());
    for (String ex : exchanges) {
      String key = ex == null ? "" : ex.toLowerCase().trim();
      String url = PUBLIC_GET.get(key);
      futures.add(CompletableFuture.supplyAsync(() -> pingOne(key, url, r), latencyWorkers));
    }
    List<Map<String, Object>> results = futures.stream().map(CompletableFuture::join).toList();

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rounds", r);
    out.put("results", results);
    out.put("ts", System.currentTimeMillis());
    return out;
  }

  /** 在并发测速前集中解析主机名，利用 JVM/OS 缓存，减轻首包抖动 */
  private static void prefetchDnsForExchanges(List<String> exchanges) {
    Set<String> hosts = new LinkedHashSet<>();
    for (String ex : exchanges) {
      String key = ex == null ? "" : ex.toLowerCase().trim();
      String url = PUBLIC_GET.get(key);
      if (url == null || url.isEmpty()) continue;
      try {
        String h = URI.create(url).getHost();
        if (h != null && !h.isEmpty()) {
          hosts.add(h);
        }
      } catch (Exception ignored) {
      }
    }
    for (String host : hosts) {
      try {
        InetAddress.getByName(host);
      } catch (Exception ignored) {
      }
    }
  }

  private Map<String, Object> pingOne(String exchange, String url, int rounds) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("exchange", exchange);
    if (url == null || url.isEmpty()) {
      row.put("ok", false);
      row.put("error", "未配置该交易所的公共测速 URL");
      row.put("avgMs", null);
      row.put("minMs", null);
      row.put("maxMs", null);
      return row;
    }

    URI uri = URI.create(url);

    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(uri)
            .timeout(REQ_TIMEOUT)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .header("Accept-Encoding", ACCEPT_ENCODING_IDENTITY)
            .GET()
            .build();

    warmup(req);

    double[] samples = new double[rounds];
    int n = 0;
    String lastErr = null;
    for (int i = 0; i < rounds; i++) {
      try {
        long t0 = System.nanoTime();
        HttpResponse<Void> res =
            http.send(req, HttpResponse.BodyHandlers.discarding());
        long ns = System.nanoTime() - t0;
        double ms = ns / 1_000_000.0;
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
          samples[n++] = ms;
        } else {
          lastErr = "HTTP " + res.statusCode();
        }
      } catch (Exception e) {
        lastErr = e.getClass().getSimpleName() + ": " + e.getMessage();
      }
    }

    if (n == 0) {
      row.put("ok", false);
      row.put("error", lastErr != null ? lastErr : "无成功样本");
      row.put("avgMs", null);
      row.put("minMs", null);
      row.put("maxMs", null);
      return row;
    }

    double sum = 0;
    double min = Double.MAX_VALUE;
    double max = 0;
    for (int i = 0; i < n; i++) {
      double v = samples[i];
      sum += v;
      min = Math.min(min, v);
      max = Math.max(max, v);
    }
    double avg = sum / n;
    row.put("ok", true);
    row.put("error", null);
    row.put("avgMs", Math.round(avg * 100) / 100.0);
    row.put("minMs", Math.round(min * 100) / 100.0);
    row.put("maxMs", Math.round(max * 100) / 100.0);
    row.put("endpoint", url);
    return row;
  }

  private void warmup(HttpRequest req) {
    try {
      http.send(req, HttpResponse.BodyHandlers.discarding());
    } catch (Exception ignored) {
    }
  }
}
