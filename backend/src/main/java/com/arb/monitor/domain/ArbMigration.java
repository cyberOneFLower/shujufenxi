package com.arb.monitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 应用级一次性迁移标记（避免重复执行）。 */
@Entity
@Table(name = "arb_migration")
public class ArbMigration {

  public static final String MIN_TOTAL_USD_LEGACY_V1 = "min_total_usd_legacy_v1";

  @Id
  @Column(name = "name", nullable = false, length = 64)
  private String name;

  public ArbMigration() {}

  public ArbMigration(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
