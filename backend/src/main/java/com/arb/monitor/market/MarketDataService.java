package com.arb.monitor.market;

import org.springframework.stereotype.Service;

@Service
public class MarketDataService {

  private final AlignStore alignStore;
  private final VolatilityEngine volatilityEngine;

  public MarketDataService(AlignStore alignStore, VolatilityEngine volatilityEngine) {
    this.alignStore = alignStore;
    this.volatilityEngine = volatilityEngine;
  }

  public void onTick(DepthTick t) {
    alignStore.upsert(t);
    volatilityEngine.onTick(t);
  }

  public AlignStore alignStore() {
    return alignStore;
  }

  public VolatilityEngine volatilityEngine() {
    return volatilityEngine;
  }
}
