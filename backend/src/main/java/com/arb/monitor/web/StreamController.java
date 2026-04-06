package com.arb.monitor.web;

import com.arb.monitor.auth.TokenService;
import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.domain.User;
import com.arb.monitor.repo.UserRepository;
import com.arb.monitor.service.PayloadService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamController {

  private final TokenService tokens;
  private final UserRepository users;
  private final PayloadService payloadService;
  private final ArbProperties props;

  public StreamController(
      TokenService tokens, UserRepository users, PayloadService payloadService, ArbProperties props) {
    this.tokens = tokens;
    this.users = users;
    this.payloadService = payloadService;
    this.props = props;
  }

  private User resolveUser(HttpServletRequest request, String queryToken) {
    String t = AuthController.bearer(request.getHeader("Authorization"));
    if (t == null) t = queryToken;
    if (t == null) return null;
    String uid = tokens.getUserId(t);
    if (uid == null) return null;
    return users.findById(uid).orElse(null);
  }

  @GetMapping(value = "/api/stream/spreads", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> streamSpreads(
      HttpServletRequest request, @RequestParam(required = false) String token) {
    User u = resolveUser(request, token);
    if (u == null) return ResponseEntity.status(401).build();
    SseEmitter emitter = new SseEmitter(0L);
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    Runnable send =
        () -> {
          try {
            var payload = payloadService.buildSpreadPayload(u.getId());
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
          } catch (IOException e) {
            emitter.completeWithError(e);
          }
        };
    send.run();
    ScheduledFuture<?> f =
        exec.scheduleAtFixedRate(send, props.spreadPushMs(), props.spreadPushMs(), TimeUnit.MILLISECONDS);
    Runnable cleanup =
        () -> {
          f.cancel(false);
          exec.shutdown();
        };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());
    return ResponseEntity.ok(emitter);
  }

  @GetMapping(value = "/api/stream/volatility", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> streamVol(
      HttpServletRequest request, @RequestParam(required = false) String token) {
    User u = resolveUser(request, token);
    if (u == null) return ResponseEntity.status(401).build();
    SseEmitter emitter = new SseEmitter(0L);
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    Runnable send =
        () -> {
          try {
            var payload = payloadService.buildVolPayload(u.getId());
            emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
          } catch (Exception e) {
            emitter.completeWithError(e);
          }
        };
    send.run();
    ScheduledFuture<?> f =
        exec.scheduleAtFixedRate(send, props.volatilityPushMs(), props.volatilityPushMs(), TimeUnit.MILLISECONDS);
    Runnable cleanup =
        () -> {
          f.cancel(false);
          exec.shutdown();
        };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(e -> cleanup.run());
    return ResponseEntity.ok(emitter);
  }
}
