package com.arb.monitor.auth;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

  private final ConcurrentHashMap<String, String> tokenToUserId = new ConcurrentHashMap<>();
  private final SecureRandom random = new SecureRandom();

  public String createToken(String userId) {
    byte[] b = new byte[24];
    random.nextBytes(b);
    String t = HexFormat.of().formatHex(b);
    tokenToUserId.put(t, userId);
    return t;
  }

  public String getUserId(String token) {
    if (token == null || token.isBlank()) return null;
    return tokenToUserId.get(token);
  }

  public void revokeToken(String token) {
    if (token == null || token.isBlank()) return;
    tokenToUserId.remove(token);
  }

  public int revokeTokensByUserId(String userId) {
    if (userId == null || userId.isBlank()) return 0;
    int removed = 0;
    for (var e : tokenToUserId.entrySet()) {
      if (userId.equals(e.getValue())) {
        if (tokenToUserId.remove(e.getKey(), e.getValue())) removed++;
      }
    }
    return removed;
  }
}
