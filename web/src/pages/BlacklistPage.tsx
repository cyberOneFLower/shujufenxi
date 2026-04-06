import { useEffect, useState } from "react";
import { api } from "../api";

type S = { id: number; symbol: string; pair_key: string; created_at: string };
type V = { id: number; symbol: string; exchange: string; key_id: string; created_at: string };

export default function BlacklistPage() {
  const [spread, setSpread] = useState<S[]>([]);
  const [vol, setVol] = useState<V[]>([]);

  async function load() {
    const [s, v] = await Promise.all([
      api<S[]>("/api/blacklist/spread"),
      api<V[]>("/api/blacklist/volatility"),
    ]);
    setSpread(s);
    setVol(v);
  }

  useEffect(() => {
    load().catch(() => {});
  }, []);

  return (
    <div>
      <div className="panel">
        <h2 style={{ marginTop: 0 }}>过滤列表</h2>
        <p style={{ color: "var(--muted)", fontSize: 13 }}>在此恢复误屏蔽的条目。</p>
        <button type="button" className="primary" onClick={() => load()}>
          刷新
        </button>
      </div>
      <div className="panel">
        <h3>价差屏蔽</h3>
        <table>
          <thead>
            <tr>
              <th>币种</th>
              <th>键</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {spread.map((x) => (
              <tr key={x.id}>
                <td>{x.symbol}</td>
                <td style={{ fontSize: 12 }}>{x.pair_key}</td>
                <td>
                  <button
                    type="button"
                    className="ghost"
                    onClick={async () => {
                      await api(`/api/blacklist/spread/${x.id}`, { method: "DELETE" });
                      load();
                    }}
                  >
                    恢复
                  </button>
                </td>
              </tr>
            ))}
            {spread.length === 0 && (
              <tr>
                <td colSpan={3} style={{ color: "var(--muted)" }}>
                  无
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      <div className="panel">
        <h3>暴涨暴跌屏蔽</h3>
        <table>
          <thead>
            <tr>
              <th>交易所</th>
              <th>币种</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {vol.map((x) => (
              <tr key={x.id}>
                <td>{x.exchange}</td>
                <td>{x.symbol}</td>
                <td>
                  <button
                    type="button"
                    className="ghost"
                    onClick={async () => {
                      await api(`/api/blacklist/volatility/${x.id}`, { method: "DELETE" });
                      load();
                    }}
                  >
                    恢复
                  </button>
                </td>
              </tr>
            ))}
            {vol.length === 0 && (
              <tr>
                <td colSpan={3} style={{ color: "var(--muted)" }}>
                  无
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
