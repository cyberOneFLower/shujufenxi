package com.arb.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arb.auth")
public class AuthProperties {

  /** 是否开放用户自助注册（默认关闭；推荐由管理员后台创建用户） */
  private boolean allowSelfRegister = false;

  public boolean isAllowSelfRegister() {
    return allowSelfRegister;
  }

  public void setAllowSelfRegister(boolean allowSelfRegister) {
    this.allowSelfRegister = allowSelfRegister;
  }
}

