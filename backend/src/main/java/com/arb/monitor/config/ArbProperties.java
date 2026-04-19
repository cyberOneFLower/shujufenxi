package com.arb.monitor.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arb")
public record ArbProperties(
    long spreadPushMs,
    long volatilityPushMs,
    List<String> exchanges,
    /** 价差表深度门槛：买/卖两腿名义 USDT 的较小值须 ≥ 此值（用户设置可覆盖；无用户设置时用此默认） */
    double minTotalUsd) {}
