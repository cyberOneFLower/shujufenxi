package com.arb.monitor.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arb")
public record ArbProperties(
    long spreadPushMs,
    long volatilityPushMs,
    List<String> exchanges) {}
