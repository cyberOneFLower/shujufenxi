package com.arb.monitor.repo;

import com.arb.monitor.domain.SpreadBlacklist;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpreadBlacklistRepository extends JpaRepository<SpreadBlacklist, Long> {
  List<SpreadBlacklist> findAllByOrderByIdDesc();
}
