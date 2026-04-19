package com.arb.monitor.market;

import com.arb.monitor.config.DepthRowColorProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DepthRowColorService {

  private final DepthRowColorProperties p;

  public DepthRowColorService(DepthRowColorProperties p) {
    this.p = p;
  }

  /** 与 {@link com.arb.monitor.market.SpreadEngine.SpreadRow#depthMinUsd()} 同一数值。 */
  public String colorFor(double depthMinUsd) {
    if (depthMinUsd >= p.getRedMin()) return "red";
    if (depthMinUsd >= p.getYellowMin()) return "yellow";
    if (depthMinUsd >= p.getBlueMin()) return "blue";
    return "white";
  }

  /** 供前端展示图例，与 {@link #colorFor(double)} 一致。 */
  public Map<String, Object> bandsForApi() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("redMin", p.getRedMin());
    m.put("yellowMin", p.getYellowMin());
    m.put("blueMin", p.getBlueMin());
    return m;
  }
}
