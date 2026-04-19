package com.arb.monitor.bootstrap;

import com.arb.monitor.config.ArbProperties;
import com.arb.monitor.domain.ArbMigration;
import com.arb.monitor.domain.UserSettings;
import com.arb.monitor.repo.ArbMigrationRepository;
import com.arb.monitor.repo.UserSettingsRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 一次性：将仍为历史默认 100 USDT 的深度阈值改为当前 {@code arb.min-total-usd}。 执行后写入 {@link
 * ArbMigration} 标记，避免重启后把用户手动改回的 100 再次覆盖。
 */
@Component
@Order(Integer.MAX_VALUE - 10)
public class LegacyMinTotalUsdMigration implements CommandLineRunner {

  /** 旧版 {@link UserSettings} 与代码中的写死默认值 */
  private static final double LEGACY_DEFAULT_MIN_TOTAL_USD = 100.0;

  private final UserSettingsRepository settingsRepository;
  private final ArbMigrationRepository migrationRepository;
  private final ArbProperties arbProperties;

  public LegacyMinTotalUsdMigration(
      UserSettingsRepository settingsRepository,
      ArbMigrationRepository migrationRepository,
      ArbProperties arbProperties) {
    this.settingsRepository = settingsRepository;
    this.migrationRepository = migrationRepository;
    this.arbProperties = arbProperties;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (migrationRepository.existsById(ArbMigration.MIN_TOTAL_USD_LEGACY_V1)) {
      return;
    }
    double target = arbProperties.minTotalUsd();
    if (Double.compare(LEGACY_DEFAULT_MIN_TOTAL_USD, target) == 0) {
      migrationRepository.save(new ArbMigration(ArbMigration.MIN_TOTAL_USD_LEGACY_V1));
      return;
    }
    for (UserSettings s : settingsRepository.findAll()) {
      if (Double.compare(s.getMinTotalUsd(), LEGACY_DEFAULT_MIN_TOTAL_USD) == 0) {
        s.setMinTotalUsd(target);
        settingsRepository.save(s);
      }
    }
    migrationRepository.save(new ArbMigration(ArbMigration.MIN_TOTAL_USD_LEGACY_V1));
  }
}
