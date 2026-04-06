import { useEffect, useRef, useState } from "react";
import { api, getToken } from "../api";

type Item = {
  exchange: string;
  symbol: string;
  changePct: number | null;
  key_id: string;
  alert: boolean;
};

function playAlert() {
  const ctx = new AudioContext();
  const o = ctx.createOscillator();
  const g = ctx.createGain();
  o.connect(g);
  g.connect(ctx.destination);
  o.type = "square";
  o.frequency.value = 880;
  g.gain.value = 0.08;
  o.start();
  setTimeout(() => {
    o.stop();
    ctx.close();
  }, 200);
}

export default function VolatilityPage({ volEnabled }: { volEnabled: boolean }) {
  const [items, setItems] = useState<Item[]>([]);
  const [threshold, setThreshold] = useState(10);
  const prevAlert = useRef(false);

  useEffect(() => {
    const es = new EventSource(`/api/stream/volatility?token=${encodeURIComponent(getToken() || "")}`);
    es.onmessage = (ev) => {
      try {
        const data = JSON.parse(ev.data) as { items?: Item[]; threshold?: number };
        const list = Array.isArray(data.items) ? data.items : [];
        setItems(list);
        setThreshold(typeof data.threshold === "number" ? data.threshold : 10);
        const anyAlert = list.some((x) => x.alert);
        if (anyAlert && !prevAlert.current) {
          for (let i = 0; i < 3; i++) setTimeout(() => playAlert(), i * 250);
        }
        prevAlert.current = anyAlert;
      } catch {
        /* */
      }
    };
    return () => es.close();
  }, []);

  async function hide(it: Item) {
    await api("/api/blacklist/volatility", {
      method: "POST",
      body: JSON.stringify({ symbol: it.symbol, exchange: it.exchange, key_id: it.key_id }),
    });
  }

  if (!volEnabled) {
    return (
      <div className="panel">
        <h2>暴涨暴跌</h2>
        <p style={{ color: "var(--muted)" }}>当前账号未开通此模块（由管理员在数据库中设置 volatility_enabled）。</p>
      </div>
    );
  }

  return (
    <div>
      <div className="panel">
        <h2 style={{ marginTop: 0 }}>5 分钟滚动涨跌幅</h2>
        <p style={{ color: "var(--muted)", fontSize: 13 }}>
          约 3 秒刷新；绝对值 ≥ {threshold}% 时触发蜂鸣（可在设置中调整阈值）。
        </p>
      </div>
      <div className="panel">
        <table>
          <thead>
            <tr>
              <th>交易所</th>
              <th>币种</th>
              <th>涨跌幅%</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {items.map((it) => (
              <tr key={it.key_id}>
                <td>{it.exchange}</td>
                <td>{it.symbol}</td>
                <td style={{ color: it.alert ? "var(--red)" : undefined, fontWeight: it.alert ? 600 : undefined }}>
                  {it.changePct !== null ? it.changePct.toFixed(4) : "—"}
                </td>
                <td>
                  <button type="button" className="ghost" onClick={() => hide(it)}>
                    屏蔽
                  </button>
                </td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td colSpan={4} style={{ color: "var(--muted)" }}>
                  暂无样本（采集运行一段时间后出现）
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
