import { useEffect, useState } from "react";
import { api } from "../api";

type Settings = {
  min_total_usd: number;
  spread_sort: string;
  volatility_threshold_pct: number;
};

export default function SettingsPage() {
  const [s, setS] = useState<Settings | null>(null);
  const [msg, setMsg] = useState("");

  useEffect(() => {
    api<Settings>("/api/settings").then(setS).catch(() => setS(null));
  }, []);

  if (!s) return <div className="panel">加载中…</div>;

  return (
    <div className="panel">
      <h2 style={{ marginTop: 0 }}>参数</h2>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setMsg("");
          try {
            await api("/api/settings", {
              method: "PUT",
              body: JSON.stringify(s),
            });
            setMsg("已保存");
          } catch (err) {
            setMsg(err instanceof Error ? err.message : "失败");
          }
        }}
      >
        <div className="field">
          <label>深度过滤（两腿一档名义 USD 取较小值，低于则隐藏；与界面展示的成交单价无关）</label>
          <input
            type="number"
            step={1}
            value={s.min_total_usd}
            onChange={(e) => setS({ ...s, min_total_usd: Number(e.target.value) })}
          />
        </div>
        <div className="field">
          <label>价差排序</label>
          <select value={s.spread_sort} onChange={(e) => setS({ ...s, spread_sort: e.target.value })}>
            <option value="spread_pct_desc">价差% 高→低</option>
            <option value="spread_pct_asc">价差% 低→高</option>
          </select>
        </div>
        <div className="field">
          <label>暴涨暴跌报警阈值（绝对值 %）</label>
          <input
            type="number"
            step={0.1}
            value={s.volatility_threshold_pct}
            onChange={(e) => setS({ ...s, volatility_threshold_pct: Number(e.target.value) })}
          />
        </div>
        <button type="submit" className="primary">
          保存
        </button>
        {msg && <p style={{ marginTop: "0.75rem" }}>{msg}</p>}
      </form>
    </div>
  );
}
