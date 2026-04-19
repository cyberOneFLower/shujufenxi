package com.arb.monitor.web;

import com.arb.monitor.auth.TokenService;
import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.config.AuthProperties;
import com.arb.monitor.domain.User;
import com.arb.monitor.domain.UserSettings;
import com.arb.monitor.repo.UserRepository;
import com.arb.monitor.repo.UserSettingsRepository;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {

  private final UserRepository users;
  private final UserSettingsRepository settings;
  private final PasswordEncoder encoder;
  private final TokenService tokens;
  private final AuthProperties authProps;
  private final ArbProperties arbProperties;

  public AuthController(
      UserRepository users,
      UserSettingsRepository settings,
      PasswordEncoder encoder,
      TokenService tokens,
      AuthProperties authProps,
      ArbProperties arbProperties) {
    this.users = users;
    this.settings = settings;
    this.encoder = encoder;
    this.tokens = tokens;
    this.authProps = authProps;
    this.arbProperties = arbProperties;
  }

  @PostMapping("/api/login")
  public Map<String, Object> login(@RequestBody Map<String, String> body) {
    String username = body.get("username");
    String password = body.get("password");
    if (username == null || password == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少用户名或密码");
    }
    User u =
        users
            .findByUsername(username)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
    if (!u.isEnabled()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用");
    }
    if (!encoder.matches(password, u.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }
    String token = tokens.createToken(u.getId());
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", u.getId());
    user.put("username", u.getUsername());
    user.put("note", u.getNote());
    user.put("volatility_enabled", u.isVolatilityEnabled());
    user.put("role", u.getRole());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("token", token);
    out.put("user", user);
    return out;
  }

  @GetMapping("/api/me")
  public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String auth) {
    String token = bearer(auth);
    if (token == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    String uid = tokens.getUserId(token);
    if (uid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    User u = users.findById(uid).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录"));
    if (!u.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已停用");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", u.getId());
    out.put("username", u.getUsername());
    out.put("note", u.getNote());
    out.put("volatility_enabled", u.isVolatilityEnabled());
    out.put("role", u.getRole());
    return out;
  }

  @PostMapping("/api/logout")
  public Map<String, Object> logout(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    String token = bearer(auth);
    if (token != null) {
      tokens.revokeToken(token);
    }
    return Map.of("ok", true);
  }

  @PostMapping("/api/register")
  public Map<String, Object> register(@RequestBody Map<String, String> body) {
    if (!authProps.isAllowSelfRegister()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "已关闭自助注册，请联系管理员创建账号");
    }
    String username = body.get("username");
    String password = body.get("password");
    String note = body.getOrDefault("note", "");
    if (username == null || password == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少用户名或密码");
    }
    username = username.trim();
    password = password.trim();
    if (username.isEmpty() || password.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名或密码不能为空");
    }
    if (users.findByUsername(username).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
    }

    User u = new User();
    u.setId(UUID.randomUUID().toString());
    u.setUsername(username);
    u.setPasswordHash(encoder.encode(password));
    u.setNote(note == null ? "" : note.trim());
    u.setRole("USER");
    u.setEnabled(true);
    u.setVolatilityEnabled(true);
    users.save(u);

    UserSettings s = new UserSettings();
    s.setUserId(u.getId());
    s.setMinTotalUsd(arbProperties.minTotalUsd());
    settings.save(s);

    String token = tokens.createToken(u.getId());
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", u.getId());
    user.put("username", u.getUsername());
    user.put("note", u.getNote());
    user.put("volatility_enabled", u.isVolatilityEnabled());
    user.put("role", u.getRole());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("token", token);
    out.put("user", user);
    return out;
  }

  static String bearer(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) return null;
    return authorization.substring(7);
  }
}
