import { useCallback, useEffect, useMemo, useState } from "react";
import { api, getToken } from "../api";

type Row = {
  symbol: string;
  label: string;
  exchangeBuy: string;
  exchangeSell: string;
  spreadPct: number;
  buyLegTotalUsd: number;
  sellLegTotalUsd: number;
  depthMinUsd: number;
  pair_key: string;
  depthColor: "red" | "yellow" | "blue" | "white";
  ask1Buy?: number;
  bid1Buy?: number;
  bid1Sell?: number;
  ask1Sell?: number;
};

const PLATFORMS = ["bitget", "okx", "gate", "mexc"] as const;

type ColKey =
  | "fav"
  | "status"
  | "pair"
  | "buyEx"
  | "sellEx"
  | "spread"
  | "pxBuy"
  | "pxSell"
  | "volBuy"
  | "volSell"
  | "ops";

const COL_LABELS: Record<ColKey, string> = {
  fav: "收藏",
  status: "状态",
  pair: "币种",
  buyEx: "买入平台",
  sellEx: "卖出平台",
  spread: "价差%",
  pxBuy: "买入价格",
  pxSell: "卖出价格",
  volBuy: "买一档单价 USDT",
  volSell: "卖一档单价 USDT",
  ops: "操作",
};

const FAV_KEY = "spread_fav_pair_keys";
const COLS_KEY = "spread_visible_cols_v1";

function loadSet(key: string): Set<string> {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return new Set();
    const a = JSON.parse(raw) as string[];
    return new Set(Array.isArray(a) ? a : []);
  } catch {
    return new Set();
  }
}

function saveSet(key: string, s: Set<string>) {
  localStorage.setItem(key, JSON.stringify([...s]));
}

function loadCols(): Record<ColKey, boolean> {
  const def: Record<ColKey, boolean> = {
    fav: true,
    status: true,
    pair: true,
    buyEx: true,
    sellEx: true,
    spread: true,
    pxBuy: true,
    pxSell: true,
    volBuy: true,
    volSell: true,
    ops: true,
  };
  try {
    const raw = localStorage.getItem(COLS_KEY);
    if (!raw) return def;
    const o = JSON.parse(raw) as Partial<Record<ColKey, boolean>>;
    for (const k of Object.keys(def) as ColKey[]) {
      if (typeof o[k] === "boolean") def[k] = o[k]!;
    }
    return def;
  } catch {
    return def;
  }
}

function fmtPrice(p: number | undefined): string {
  if (p == null || !Number.isFinite(p)) return "—";
  if (p >= 1000) return p.toFixed(2);
  if (p >= 1) return p.toFixed(4);
  return p.toFixed(8);
}

function ExBadge({ ex }: { ex: string }) {
  const key = ex.toLowerCase();
  const colors: Record<string, string> = {
    bitget: "#00c853",
    okx: "#303030",
    gate: "#2962ff",
    mexc: "#c99400",
  };
  const bg = colors[key] || "#6b7280";
  const letter = ex.slice(0, 1).toUpperCase();
  return (
    <span className="ex-badge" style={{ background: bg }} title={ex}>
      {letter}
    </span>
  );
}

export default function SpreadPage() {
  const [rows, setRows] = useState<Row[]>([]);
  const [minUsd, setMinUsd] = useState(100);
  const [err, setErr] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [fav, setFav] = useState<Set<string>>(() => loadSet(FAV_KEY));
  const [plat, setPlat] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(PLATFORMS.map((p) => [p, true])),
  );
  const [cols, setCols] = useState<Record<ColKey, boolean>>(loadCols);
  const [showColMenu, setShowColMenu] = useState(false);
  const [favFirst, setFavFirst] = useState(true);
  const [category, setCategory] = useState<"all" | "majors">("all");
  /** 按交易对关键字过滤（不区分大小写） */
  const [symbolQuery, setSymbolQuery] = useState("");

  const persistCols = useCallback((next: Record<ColKey, boolean>) => {
    setCols(next);
    localStorage.setItem(COLS_KEY, JSON.stringify(next));
  }, []);

  const toggleFav = (pairKey: string) => {
    setFav((prev) => {
      const n = new Set(prev);
      if (n.has(pairKey)) n.delete(pairKey);
      else n.add(pairKey);
      saveSet(FAV_KEY, n);
      return n;
    });
  };

  useEffect(() => {
    const es = new EventSource(`/api/stream/spreads?token=${encodeURIComponent(getToken() || "")}`);
    es.onmessage = (ev) => {
      try {
        const data = JSON.parse(ev.data) as { rows?: Row[]; minUsd?: number; min_usd?: number };
        setRows(Array.isArray(data.rows) ? data.rows : []);
        const m = data.minUsd ?? data.min_usd;
        setMinUsd(typeof m === "number" && !Number.isNaN(m) ? m : 100);
        setErr("");
      } catch {
        /* */
      }
    };
    es.onerror = () => setErr("连接中断，请手动刷新或重载页面");
    return () => es.close();
  }, []);

  const manualRefresh = async () => {
    setRefreshing(true);
    setErr("");
    try {
      const data = await api<{ rows?: Row[]; minUsd?: number }>("/api/spreads");
      setRows(Array.isArray(data.rows) ? data.rows : []);
      if (typeof data.minUsd === "number") setMinUsd(data.minUsd);
    } catch (e) {
      setErr(e instanceof Error ? e.message : "刷新失败");
    } finally {
      setRefreshing(false);
    }
  };

  const filtered = useMemo(() => {
    const q = symbolQuery.trim().toLowerCase();
    let list = rows.filter((r) => {
      const b = r.exchangeBuy?.toLowerCase() ?? "";
      const s = r.exchangeSell?.toLowerCase() ?? "";
      if (!plat[b] || !plat[s]) return false;
      if (q) {
        const sym = r.symbol?.toLowerCase() ?? "";
        if (!sym.includes(q)) return false;
      }
      if (category === "majors") {
        const sym = r.symbol?.toUpperCase() ?? "";
        if (!sym.includes("BTC") && !sym.includes("ETH")) return false;
      }
      return true;
    });

    if (favFirst) {
      list = [...list].sort((a, b) => {
        const fa = fav.has(a.pair_key) ? 1 : 0;
        const fb = fav.has(b.pair_key) ? 1 : 0;
        if (fa !== fb) return fb - fa;
        return (Number(b.spreadPct) || 0) - (Number(a.spreadPct) || 0);
      });
    }
    return list;
  }, [rows, plat, category, favFirst, fav, symbolQuery]);

  /** 条形图按「一档单价」相对缩放（原为一档名义 USDT 深度） */
  const maxAskBuyPx = useMemo(
    () => Math.max(1, ...filtered.map((r) => Number(r.ask1Buy) || 0)),
    [filtered],
  );
  const maxBidSellPx = useMemo(
    () => Math.max(1, ...filtered.map((r) => Number(r.bid1Sell) || 0)),
    [filtered],
  );

  async function hideRow(r: Row) {
    try {
      await api("/api/blacklist/spread", {
        method: "POST",
        body: JSON.stringify({ symbol: r.symbol, pair_key: r.pair_key }),
      });
      setRows((prev) => prev.filter((x) => x.pair_key !== r.pair_key));
    } catch (e) {
      setErr(e instanceof Error ? e.message : "操作失败");
    }
  }

  const visibleCount = useMemo(() => Object.values(cols).filter(Boolean).length, [cols]);

  const togglePlat = (p: string) => {
    setPlat((prev) => ({ ...prev, [p]: !prev[p] }));
  };

  return (
    <div className="analysis-page">
      <div className="analysis-title-row">
        <h1>数据分析</h1>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          <select
            className="analysis-menu-btn"
            value={category}
            onChange={(e) => setCategory(e.target.value as "all" | "majors")}
            title="类目"
          >
            <option value="all">全部币种</option>
            <option value="majors">主流 (BTC/ETH)</option>
          </select>
          <button type="button" className="analysis-menu-btn" onClick={() => setShowColMenu(true)}>
            显示菜单
          </button>
        </div>
      </div>

      <div className="analysis-toolbar">
        <div className="analysis-toolbar-group">
          <label className="analysis-toggle">
            <input
              type="checkbox"
              checked={favFirst}
              onChange={(e) => setFavFirst(e.target.checked)}
            />
            收藏优先
          </label>
        </div>
        <div className="analysis-toolbar-group">
          <label className="analysis-toolbar-label" htmlFor="symbol-search">
            搜索币种
          </label>
          <input
            id="symbol-search"
            type="search"
            className="analysis-search-input"
            placeholder="如 ETH、PEPE、BTC_USDT"
            value={symbolQuery}
            onChange={(e) => setSymbolQuery(e.target.value)}
            autoComplete="off"
          />
        </div>
        <div className="analysis-toolbar-group">
          <span className="analysis-toolbar-label">过滤平台</span>
          <div className="analysis-platforms">
            {PLATFORMS.map((p) => (
              <label key={p}>
                <input type="checkbox" checked={plat[p] !== false} onChange={() => togglePlat(p)} />
                {p.charAt(0).toUpperCase() + p.slice(1)}
              </label>
            ))}
          </div>
        </div>
        <div className="analysis-toolbar-group" style={{ marginLeft: "auto" }}>
          <button
            type="button"
            className="analysis-btn-primary"
            disabled={refreshing}
            onClick={() => void manualRefresh()}
          >
            {refreshing ? "刷新中…" : "手动刷新"}
          </button>
          <button
            type="button"
            className="analysis-btn-primary analysis-btn-secondary"
            onClick={() => setShowColMenu((v) => !v)}
          >
            隐藏列
          </button>
        </div>
      </div>

      {showColMenu && (
        <div className="analysis-columns-panel">
          <span>显示列（勾选为显示）</span>
          {(Object.keys(COL_LABELS) as ColKey[]).map((k) => (
            <label key={k}>
              <input
                type="checkbox"
                checked={cols[k]}
                onChange={() => persistCols({ ...cols, [k]: !cols[k] })}
              />
              {COL_LABELS[k]}
            </label>
          ))}
        </div>
      )}

      {err && <div className="analysis-error">{err}</div>}

      <p style={{ margin: "0.4rem 1rem 0.35rem", fontSize: 12, color: "var(--a-muted)" }}>
        深度阈值 ${minUsd.toFixed(0)}（设置页可改）· 颜色规则：≥1000 红 · 500–999 黄 · 100–499 蓝 · &lt;100 白
      </p>

      <div className="analysis-table-scroller">
        <table className="analysis-table">
          <thead>
            <tr>
              {cols.fav && <th style={{ width: 36 }}> </th>}
              {cols.status && <th style={{ width: 48 }}>状态</th>}
              {cols.pair && <th style={{ width: 130 }}>币种</th>}
              {cols.buyEx && <th style={{ width: 72 }}>买入平台</th>}
              {cols.sellEx && <th style={{ width: 72 }}>卖出平台</th>}
              {cols.spread && <th style={{ width: 72 }}>价差%</th>}
              {cols.pxBuy && <th style={{ width: 88 }}>买入价格</th>}
              {cols.pxSell && <th style={{ width: 88 }}>卖出价格</th>}
              {cols.volBuy && <th>{COL_LABELS.volBuy}</th>}
              {cols.volSell && <th>{COL_LABELS.volSell}</th>}
              {cols.ops && <th style={{ width: 100 }}>操作</th>}
            </tr>
          </thead>
          <tbody>
            {filtered.map((r) => {
              const askBuy = Number(r.ask1Buy) || 0;
              const bidSell = Number(r.bid1Sell) || 0;
              const wBuy = Math.min(100, (askBuy / maxAskBuyPx) * 100);
              const wSell = Math.min(100, (bidSell / maxBidSellPx) * 100);
              const warnSell = r.depthColor === "yellow";
              return (
                <tr key={r.pair_key}>
                  {cols.fav && (
                    <td>
                      <button
                        type="button"
                        className={`analysis-star ${fav.has(r.pair_key) ? "is-on" : ""}`}
                        onClick={() => toggleFav(r.pair_key)}
                        title="收藏"
                        aria-label="收藏"
                      >
                        ★
                      </button>
                    </td>
                  )}
                  {cols.status && (
                    <td>
                      <span className="analysis-status">实时</span>
                    </td>
                  )}
                  {cols.pair && (
                    <td className="analysis-pair-cell">
                      <span className="analysis-pair-row">
                        <span className="analysis-pair-symbol">{r.symbol}</span>
                        <span className="ex-badge-wrap">
                          <ExBadge ex={r.exchangeBuy} />
                          <ExBadge ex={r.exchangeSell} />
                        </span>
                      </span>
                    </td>
                  )}
                  {cols.buyEx && <td>{r.exchangeBuy}</td>}
                  {cols.sellEx && <td>{r.exchangeSell}</td>}
                  {cols.spread && (
                    <td>
                      <span className="analysis-spread">{(Number(r.spreadPct) || 0).toFixed(4)}</span>
                    </td>
                  )}
                  {cols.pxBuy && <td className="analysis-num">{fmtPrice(r.ask1Buy)}</td>}
                  {cols.pxSell && <td className="analysis-num">{fmtPrice(r.bid1Sell)}</td>}
                  {cols.volBuy && (
                    <td className="analysis-bar-cell">
                      <div className="analysis-bar-track">
                        <div
                          className="analysis-bar-fill analysis-bar-fill--buy"
                          style={{ width: `${wBuy}%` }}
                        >
                          {fmtPrice(r.ask1Buy)}
                        </div>
                      </div>
                    </td>
                  )}
                  {cols.volSell && (
                    <td className="analysis-bar-cell">
                      <div className="analysis-bar-track">
                        <div
                          className={`analysis-bar-fill analysis-bar-fill--sell ${warnSell ? "is-warn" : ""}`}
                          style={{ width: `${wSell}%` }}
                        >
                          {fmtPrice(r.bid1Sell)}
                        </div>
                      </div>
                    </td>
                  )}
                  {cols.ops && (
                    <td className="analysis-actions">
                      <button type="button" onClick={() => void hideRow(r)}>
                        过滤
                      </button>
                      <button type="button" onClick={() => void hideRow(r)}>
                        移除
                      </button>
                    </td>
                  )}
                </tr>
              );
            })}
            {filtered.length === 0 && (
              <tr>
                <td className="analysis-empty" colSpan={Math.max(1, visibleCount)}>
                  暂无数据（调整平台筛选、类目或等待行情对齐）
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
