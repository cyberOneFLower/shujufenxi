package com.arb.monitor.web;

import com.arb.monitor.auth.TokenService;
import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.domain.SpreadBlacklist;
import com.arb.monitor.domain.User;
import com.arb.monitor.domain.UserSettings;
import com.arb.monitor.domain.VolatilityBlacklist;
import com.arb.monitor.repo.SpreadBlacklistRepository;
import com.arb.monitor.repo.UserRepository;
import com.arb.monitor.repo.UserSettingsRepository;
import com.arb.monitor.repo.VolatilityBlacklistRepository;
import com.arb.monitor.service.ExchangeLatencyService;
import com.arb.monitor.service.PayloadService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ApiController {

  private final TokenService tokens;
  private final UserRepository users;
  private final UserSettingsRepository settingsRepo;
  private final SpreadBlacklistRepository spreadBl;
  private final VolatilityBlacklistRepository volBl;
  private final PayloadService payloadService;
  private final ArbProperties arbProperties;
  private final ExchangeLatencyService exchangeLatencyService;

  public ApiController(
      TokenService tokens,
      UserRepository users,
      UserSettingsRepository settingsRepo,
      SpreadBlacklistRepository spreadBl,
      VolatilityBlacklistRepository volBl,
      PayloadService payloadService,
      ArbProperties arbProperties,
      ExchangeLatencyService exchangeLatencyService) {
    this.tokens = tokens;
    this.users = users;
    this.settingsRepo = settingsRepo;
    this.spreadBl = spreadBl;
    this.volBl = volBl;
    this.payloadService = payloadService;
    this.arbProperties = arbProperties;
    this.exchangeLatencyService = exchangeLatencyService;
  }

  private User requireUser(String auth) {
    String t = AuthController.bearer(auth);
    if (t == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    String uid = tokens.getUserId(t);
    if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    return users.findById(uid).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录"));
  }

  @GetMapping("/api/settings")
  public Map<String, Object> getSettings(@RequestHeader(value = "Authorization", required = false) String auth) {
    User u = requireUser(auth);
    UserSettings s =
        settingsRepo.findById(u.getId()).orElse(null);
    if (s == null) {
      Map<String, Object> def = new LinkedHashMap<>();
      def.put("min_total_usd", 100);
      def.put("spread_sort", "spread_pct_desc");
      def.put("volatility_threshold_pct", 10);
      return def;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("min_total_usd", s.getMinTotalUsd());
    out.put("spread_sort", s.getSpreadSort());
    out.put("volatility_threshold_pct", s.getVolatilityThresholdPct());
    return out;
  }

  @PutMapping("/api/settings")
  public Map<String, Object> putSettings(
      @RequestHeader(value = "Authorization", required = false) String auth, @RequestBody Map<String, Object> body) {
    User u = requireUser(auth);
    UserSettings s = settingsRepo.findById(u.getId()).orElseGet(UserSettings::new);
    if (s.getUserId() == null) {
      s.setUserId(u.getId());
      s.setMinTotalUsd(100);
      s.setSpreadSort("spread_pct_desc");
      s.setVolatilityThresholdPct(10);
    }
    if (body.containsKey("min_total_usd")) s.setMinTotalUsd(toDouble(body.get("min_total_usd")));
    if (body.containsKey("spread_sort")) s.setSpreadSort(String.valueOf(body.get("spread_sort")));
    if (body.containsKey("volatility_threshold_pct"))
      s.setVolatilityThresholdPct(toDouble(body.get("volatility_threshold_pct")));
    settingsRepo.save(s);
    Map<String, Object> ok = new LinkedHashMap<>();
    ok.put("ok", true);
    return ok;
  }

  private static double toDouble(Object o) {
    if (o instanceof Number n) return n.doubleValue();
    return Double.parseDouble(String.valueOf(o));
  }

  @GetMapping("/api/blacklist/spread")
  public List<Map<String, Object>> spreadBlacklist(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    requireUser(auth);
    return spreadBl.findAllByOrderByIdDesc().stream().map(this::spreadRow).collect(Collectors.toList());
  }

  private Map<String, Object> spreadRow(SpreadBlacklist b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", b.getId());
    m.put("symbol", b.getSymbol());
    m.put("pair_key", b.getPairKey());
    m.put("created_at", instantString(b.getCreatedAt()));
    return m;
  }

  private static String instantString(Instant i) {
    return i == null ? null : i.toString();
  }

  @PostMapping("/api/blacklist/spread")
  public Map<String, Object> addSpreadBl(
      @RequestHeader(value = "Authorization", required = false) String auth, @RequestBody Map<String, String> body) {
    requireUser(auth);
    String symbol = body.get("symbol");
    String pairKey = body.get("pair_key");
    if (symbol == null || pairKey == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 symbol 或 pair_key");
    SpreadBlacklist e = new SpreadBlacklist();
    e.setSymbol(symbol);
    e.setPairKey(pairKey);
    try {
      spreadBl.save(e);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已存在");
    }
    Map<String, Object> ok = new LinkedHashMap<>();
    ok.put("ok", true);
    return ok;
  }

  @DeleteMapping("/api/blacklist/spread/{id}")
  public Map<String, Object> delSpreadBl(
      @RequestHeader(value = "Authorization", required = false) String auth, @PathVariable long id) {
    requireUser(auth);
    spreadBl.deleteById(id);
    Map<String, Object> ok = new LinkedHashMap<>();
    ok.put("ok", true);
    return ok;
  }

  @GetMapping("/api/blacklist/volatility")
  public List<Map<String, Object>> volBlacklist(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    requireUser(auth);
    return volBl.findAllByOrderByIdDesc().stream().map(this::volRow).collect(Collectors.toList());
  }

  private Map<String, Object> volRow(VolatilityBlacklist b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", b.getId());
    m.put("symbol", b.getSymbol());
    m.put("exchange", b.getExchange());
    m.put("key_id", b.getKeyId());
    m.put("created_at", instantString(b.getCreatedAt()));
    return m;
  }

  @PostMapping("/api/blacklist/volatility")
  public Map<String, Object> addVolBl(
      @RequestHeader(value = "Authorization", required = false) String auth, @RequestBody Map<String, String> body) {
    requireUser(auth);
    String symbol = body.get("symbol");
    String exchange = body.get("exchange");
    String keyId = body.get("key_id");
    if (symbol == null || exchange == null || keyId == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少字段");
    VolatilityBlacklist e = new VolatilityBlacklist();
    e.setSymbol(symbol);
    e.setExchange(exchange);
    e.setKeyId(keyId);
    try {
      volBl.save(e);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已存在");
    }
    Map<String, Object> ok = new LinkedHashMap<>();
    ok.put("ok", true);
    return ok;
  }

  @DeleteMapping("/api/blacklist/volatility/{id}")
  public Map<String, Object> delVolBl(
      @RequestHeader(value = "Authorization", required = false) String auth, @PathVariable long id) {
    requireUser(auth);
    volBl.deleteById(id);
    Map<String, Object> ok = new LinkedHashMap<>();
    ok.put("ok", true);
    return ok;
  }

  @GetMapping("/api/spreads")
  public Map<String, Object> spreads(@RequestHeader(value = "Authorization", required = false) String auth) {
    User u = requireUser(auth);
    return payloadService.buildSpreadPayload(u.getId());
  }

  @GetMapping("/api/volatility")
  public Map<String, Object> volatility(@RequestHeader(value = "Authorization", required = false) String auth) {
    User u = requireUser(auth);
    return payloadService.buildVolPayload(u.getId());
  }

  @GetMapping("/api/health")
  public Map<String, Object> health() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("ok", true);
    m.put("exchanges", arbProperties.exchanges());
    return m;
  }

  /**
   * 测试本机到各交易所<strong>公共 REST</strong>的往返延时（无需 API Key）。<br>
   * {@code rounds}：每个所连续请求次数（1–10），取成功样本的 min/avg/max ms。
   */
  @GetMapping("/api/latency")
  public Map<String, Object> latency(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestParam(value = "rounds", defaultValue = "3") int rounds) {
    requireUser(auth);
    return exchangeLatencyService.measure(arbProperties.exchanges(), rounds);
  }
}
