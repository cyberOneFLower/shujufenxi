package com.arb.monitor.bootstrap;

import com.arb.monitor.domain.User;
import com.arb.monitor.domain.UserSettings;
import com.arb.monitor.repo.UserRepository;
import com.arb.monitor.repo.UserSettingsRepository;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

  private final UserRepository users;
  private final UserSettingsRepository settings;
  private final PasswordEncoder encoder;

  public DataInitializer(
      UserRepository users, UserSettingsRepository settings, PasswordEncoder encoder) {
    this.users = users;
    this.settings = settings;
    this.encoder = encoder;
  }

  @Override
  public void run(String... args) {
    if (users.count() > 0) return;
    User u = new User();
    u.setId(UUID.randomUUID().toString());
    u.setUsername("admin");
    u.setPasswordHash(encoder.encode("admin123"));
    u.setNote("默认管理员");
    u.setVolatilityEnabled(true);
    users.save(u);
    UserSettings s = new UserSettings();
    s.setUserId(u.getId());
    s.setMinTotalUsd(100);
    s.setSpreadSort("spread_pct_desc");
    s.setVolatilityThresholdPct(10);
    settings.save(s);
  }
}
