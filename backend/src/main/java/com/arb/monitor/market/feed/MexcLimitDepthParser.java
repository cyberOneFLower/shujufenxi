package com.arb.monitor.market.feed;

import com.arb.monitor.market.DepthTick;
import com.mxc.push.common.protobuf.PublicLimitDepthV3ApiItem;
import com.mxc.push.common.protobuf.PublicLimitDepthsV3Api;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
import java.util.ArrayList;
import java.util.List;

/** MEXC {@code spot@public.limit.depth.v3.api.pb@SYMBOL@5} Protobuf → {@link DepthTick} */
public final class MexcLimitDepthParser {

  private MexcLimitDepthParser() {}

  public static DepthTick fromPushWrapper(String exchange, String internalSymbol, PushDataV3ApiWrapper w) {
    if (!w.hasPublicLimitDepths()) return null;
    PublicLimitDepthsV3Api d = w.getPublicLimitDepths();
    List<PublicLimitDepthV3ApiItem> bids = d.getBidsList();
    List<PublicLimitDepthV3ApiItem> asks = d.getAsksList();
    if (bids.isEmpty() || asks.isEmpty()) return null;
    long ts = w.getSendTime() != 0L ? w.getSendTime() : (w.getCreateTime() != 0L ? w.getCreateTime() : System.currentTimeMillis());
    PublicLimitDepthV3ApiItem b0 = bids.get(0);
    PublicLimitDepthV3ApiItem a0 = asks.get(0);
    double bid1 = Double.parseDouble(b0.getPrice());
    double bid1Qty = Double.parseDouble(b0.getQuantity());
    double ask1 = Double.parseDouble(a0.getPrice());
    double ask1Qty = Double.parseDouble(a0.getQuantity());
    List<DepthTick.Level> bids5 = toLevels(bids, 5);
    List<DepthTick.Level> asks5 = toLevels(asks, 5);
    return new DepthTick(exchange, internalSymbol, ts, bid1, bid1Qty, ask1, ask1Qty, bids5, asks5);
  }

  private static List<DepthTick.Level> toLevels(List<PublicLimitDepthV3ApiItem> side, int max) {
    List<DepthTick.Level> lv = new ArrayList<>();
    for (int i = 0; i < Math.min(max, side.size()); i++) {
      PublicLimitDepthV3ApiItem it = side.get(i);
      lv.add(new DepthTick.Level(Double.parseDouble(it.getPrice()), Double.parseDouble(it.getQuantity())));
    }
    return lv;
  }
}
