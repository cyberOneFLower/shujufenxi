package com.arb.monitor.repo;

import com.arb.monitor.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
  Optional<User> findByUsername(String username);
}
