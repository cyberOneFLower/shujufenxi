package com.arb.monitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

  @Id private String id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  private String note = "";

  /** USER / ADMIN */
  @Column
  private String role;

  @Column
  private Boolean enabled;

  @Column(name = "volatility_enabled", nullable = false)
  private boolean volatilityEnabled = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public boolean isVolatilityEnabled() {
    return volatilityEnabled;
  }

  public void setVolatilityEnabled(boolean volatilityEnabled) {
    this.volatilityEnabled = volatilityEnabled;
  }

  public String getRole() {
    return role == null || role.isBlank() ? "USER" : role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public boolean isEnabled() {
    return enabled == null ? true : enabled.booleanValue();
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
