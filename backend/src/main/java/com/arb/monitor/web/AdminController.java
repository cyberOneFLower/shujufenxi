package com.arb.monitor.web;

import com.arb.monitor.auth.TokenService;
import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.domain.User;
import com.arb.monitor.domain.UserSettings;
import com.arb.monitor.repo.UserRepository;
import com.arb.monitor.repo.UserSettingsRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminController {

  private final UserRepository users;
  private final UserSettingsRepository settings;
  private final PasswordEncoder encoder;
  private final TokenService tokens;
  private final ArbProperties arbProperties;

  public AdminController(
      UserRepository users,
      UserSettingsRepository settings,
      PasswordEncoder encoder,
      TokenService tokens,
      ArbProperties arbProperties) {
    this.users = users;
    this.settings = settings;
    this.encoder = encoder;
    this.tokens = tokens;
    this.arbProperties = arbProperties;
  }

  private User requireAdmin(String auth) {
    String token = AuthController.bearer(auth);
    if (token == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    String uid = tokens.getUserId(token);
    if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    User me = users.findById(uid).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录"));
    if (!me.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用");
    if (!"ADMIN".equalsIgnoreCase(me.getRole())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限");
    }
    return me;
  }

  private static Map<String, Object> toUserOut(User u) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", u.getId());
    m.put("username", u.getUsername());
    m.put("note", u.getNote());
    m.put("role", u.getRole());
    m.put("enabled", u.isEnabled());
    m.put("volatility_enabled", u.isVolatilityEnabled());
    Instant createdAt = u.getCreatedAt();
    m.put("created_at", createdAt != null ? createdAt.toString() : null);
    return m;
  }

  @GetMapping("/api/admin/users")
  public Map<String, Object> listUsers(@RequestHeader(value = "Authorization", required = false) String auth) {
    requireAdmin(auth);
    List<Map<String, Object>> out = users.findAll().stream().map(AdminController::toUserOut).toList();
    return Map.of("users", out);
  }

  @PostMapping("/api/admin/users")
  public Map<String, Object> createUser(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestBody Map<String, Object> body) {
    requireAdmin(auth);
    String username = String.valueOf(body.getOrDefault("username", "")).trim();
    String password = String.valueOf(body.getOrDefault("password", "")).trim();
    String note = String.valueOf(body.getOrDefault("note", "")).trim();
    String role = String.valueOf(body.getOrDefault("role", "USER")).trim();
    boolean enabled = Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", "true")));
    boolean volEnabled = Boolean.parseBoolean(String.valueOf(body.getOrDefault("volatility_enabled", "true")));

    if (username.isEmpty() || password.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少用户名或密码");
    }
    if (users.findByUsername(username).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
    }
    if (!"ADMIN".equalsIgnoreCase(role)) role = "USER";

    User u = new User();
    u.setId(UUID.randomUUID().toString());
    u.setUsername(username);
    u.setPasswordHash(encoder.encode(password));
    u.setNote(note);
    u.setRole(role.toUpperCase());
    u.setEnabled(enabled);
    u.setVolatilityEnabled(volEnabled);
    users.save(u);

    // ensure settings row exists
    UserSettings s = new UserSettings();
    s.setUserId(u.getId());
    s.setMinTotalUsd(arbProperties.minTotalUsd());
    settings.save(s);

    return Map.of("user", toUserOut(u));
  }

  @PatchMapping("/api/admin/users/{id}")
  public Map<String, Object> patchUser(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody Map<String, Object> body) {
    User admin = requireAdmin(auth);
    User u = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    if (u.getId().equals(admin.getId())) {
      // prevent locking yourself out
      if (body.containsKey("enabled") && Boolean.FALSE.equals(body.get("enabled"))) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能停用当前管理员账号");
      }
      if (body.containsKey("role") && !"ADMIN".equalsIgnoreCase(String.valueOf(body.get("role")))) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能降级当前管理员账号");
      }
    }

    if (body.containsKey("note")) u.setNote(String.valueOf(body.get("note")));
    if (body.containsKey("volatility_enabled")) {
      u.setVolatilityEnabled(Boolean.parseBoolean(String.valueOf(body.get("volatility_enabled"))));
    }
    if (body.containsKey("enabled")) {
      boolean en = Boolean.parseBoolean(String.valueOf(body.get("enabled")));
      u.setEnabled(en);
      if (!en) tokens.revokeTokensByUserId(u.getId());
    }
    if (body.containsKey("role")) {
      String role = String.valueOf(body.get("role")).trim();
      u.setRole("ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "USER");
    }
    users.save(u);
    return Map.of("user", toUserOut(u));
  }

  @PostMapping("/api/admin/users/{id}/reset-password")
  public Map<String, Object> resetPassword(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody Map<String, Object> body) {
    requireAdmin(auth);
    User u = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    String password = String.valueOf(body.getOrDefault("password", "")).trim();
    if (password.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少新密码");
    u.setPasswordHash(encoder.encode(password));
    users.save(u);
    tokens.revokeTokensByUserId(u.getId());
    return Map.of("ok", true);
  }

  @PostMapping("/api/admin/users/{id}/revoke-tokens")
  public Map<String, Object> revokeTokens(
      @RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
    requireAdmin(auth);
    int n = tokens.revokeTokensByUserId(id);
    return Map.of("revoked", n);
  }

  @DeleteMapping("/api/admin/users/{id}")
  public Map<String, Object> deleteUser(
      @RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
    User admin = requireAdmin(auth);
    if (admin.getId().equals(id)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除当前管理员账号");
    }
    if (!users.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
    tokens.revokeTokensByUserId(id);
    settings.deleteById(id);
    users.deleteById(id);
    return Map.of("ok", true);
  }
}

