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
}
