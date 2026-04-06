package com.arb.monitor.repo;

import com.arb.monitor.domain.VolatilityBlacklist;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolatilityBlacklistRepository extends JpaRepository<VolatilityBlacklist, Long> {
  List<VolatilityBlacklist> findAllByOrderByIdDesc();
}
