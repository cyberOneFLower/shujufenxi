package com.arb.monitor.web;

import com.arb.monitor.auth.TokenService;
import com.arb.monitor.domain.User;
import com.arb.monitor.repo.UserRepository;
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
  private final PasswordEncoder encoder;
  private final TokenService tokens;

  public AuthController(UserRepository users, PasswordEncoder encoder, TokenService tokens) {
    this.users = users;
    this.encoder = encoder;
    this.tokens = tokens;
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
    if (!encoder.matches(password, u.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }
    String token = tokens.createToken(u.getId());
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", u.getId());
    user.put("username", u.getUsername());
    user.put("note", u.getNote());
    user.put("volatility_enabled", u.isVolatilityEnabled());
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
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", u.getId());
    out.put("username", u.getUsername());
    out.put("note", u.getNote());
    out.put("volatility_enabled", u.isVolatilityEnabled());
    return out;
  }

  static String bearer(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) return null;
    return authorization.substring(7);
  }
}
