package com.arb.monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class WebConfig {

  @Bean
  public CorsFilter corsFilter() {
    CorsConfiguration c = new CorsConfiguration();
    c.addAllowedOriginPattern("*");
    c.addAllowedHeader("*");
    c.addAllowedMethod("*");
    c.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return new CorsFilter(source);
  }
}
