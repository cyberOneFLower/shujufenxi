package com.arb.monitor.repo;

import com.arb.monitor.domain.ArbMigration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArbMigrationRepository extends JpaRepository<ArbMigration, String> {}
