package com.arb.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 价差表档位键（与 depthMinUsd = min(买入总价,卖出总价) 比较，单位 USDT）；前端用对应字体色展示。
 * 规则：≥redMin 红；[yellowMin, redMin) 土黄；[blueMin, yellowMin) 蓝；&lt;blueMin 白。须 redMin &gt; yellowMin
 * &gt; blueMin &gt; 0。
 */
@ConfigurationProperties(prefix = "arb.depth-row-color")
public class DepthRowColorProperties {

  private double redMin = 1500;
  private double yellowMin = 1000;
  private double blueMin = 100;

  public double getRedMin() {
    return redMin;
  }

  public void setRedMin(double redMin) {
    this.redMin = redMin;
  }

  public double getYellowMin() {
    return yellowMin;
  }

  public void setYellowMin(double yellowMin) {
    this.yellowMin = yellowMin;
  }

  public double getBlueMin() {
    return blueMin;
  }

  public void setBlueMin(double blueMin) {
    this.blueMin = blueMin;
  }
}
