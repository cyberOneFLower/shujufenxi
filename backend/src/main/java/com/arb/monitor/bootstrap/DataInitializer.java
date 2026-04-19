package com.arb.monitor.bootstrap;

import com.arb.monitor.config.ArbProperties;
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
  private final ArbProperties arbProperties;

  public DataInitializer(
      UserRepository users,
      UserSettingsRepository settings,
      PasswordEncoder encoder,
      ArbProperties arbProperties) {
    this.users = users;
    this.settings = settings;
    this.encoder = encoder;
    this.arbProperties = arbProperties;
  }

  @Override
  public void run(String... args) {
    if (users.count() > 0) return;
    User u = new User();
    u.setId(UUID.randomUUID().toString());
    u.setUsername("admin");
    u.setPasswordHash(encoder.encode("admin123"));
    u.setNote("默认管理员");
    u.setRole("ADMIN");
    u.setEnabled(true);
    u.setVolatilityEnabled(true);
    users.save(u);
    UserSettings s = new UserSettings();
    s.setUserId(u.getId());
    s.setMinTotalUsd(arbProperties.minTotalUsd());
    s.setSpreadSort("spread_pct_desc");
    s.setVolatilityThresholdPct(10);
    settings.save(s);
  }
}
