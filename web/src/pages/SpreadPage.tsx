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
const POSITIVE_SPREAD_ONLY_KEY = "spread_positive_only_v1";

function loadPositiveOnly(): boolean {
  try {
    return localStorage.getItem(POSITIVE_SPREAD_ONLY_KEY) === "1";
  } catch {
    return false;
  }
}

const SPREAD_MIN_FILTER_KEY = "spread_min_filter_v1";
const PAGE_SIZE_KEY = "spread_page_size_v1";
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

const AUTO_REFRESH_SEC_KEY = "spread_auto_refresh_sec_v1";
const AUTO_REFRESH_OPTIONS: { sec: number; label: string }[] = [
  { sec: 0, label: "关闭" },
  { sec: 1, label: "1秒" },
  { sec: 3, label: "3秒" },
  { sec: 5, label: "5秒" },
  { sec: 10, label: "10秒" },
  { sec: 30, label: "30秒" },
];

function loadAutoRefreshSec(): number {
  try {
    const n = Number(localStorage.getItem(AUTO_REFRESH_SEC_KEY));
    if (AUTO_REFRESH_OPTIONS.some((o) => o.sec === n)) return n;
  } catch {
    /* */
  }
  return 5;
}

function loadPageSize(): number {
  try {
    const n = Number(localStorage.getItem(PAGE_SIZE_KEY));
    if (PAGE_SIZE_OPTIONS.includes(n as (typeof PAGE_SIZE_OPTIONS)[number])) return n;
  } catch {
    /* */
  }
  return 10;
}

/** 价差筛选下限候选：0.1%～3.0%，步长 0.1%（另含「全部」表示仅 >0） */
const SPREAD_MIN_THRESHOLDS_PCT = Array.from({ length: 30 }, (_, i) =>
  ((i + 1) / 10).toFixed(1),
);

const SPREAD_MIN_OPTIONS: { value: string; label: string }[] = [
  { value: "all", label: "全部" },
  ...SPREAD_MIN_THRESHOLDS_PCT.map((v) => ({ value: v, label: `> ${v}%` })),
];

function loadSpreadMinFilter(): { enabled: boolean; threshold: string } {
  try {
    const raw = localStorage.getItem(SPREAD_MIN_FILTER_KEY);
    if (raw) {
      const o = JSON.parse(raw) as { enabled?: boolean; threshold?: string };
      const threshold =
        typeof o.threshold === "string" &&
        SPREAD_MIN_OPTIONS.some((x) => x.value === o.threshold)
          ? o.threshold
          : "all";
      return { enabled: !!o.enabled, threshold };
    }
  } catch {
    /* */
  }
  return { enabled: false, threshold: "all" };
}

function persistSpreadMinFilter(enabled: boolean, threshold: string) {
  try {
    localStorage.setItem(SPREAD_MIN_FILTER_KEY, JSON.stringify({ enabled, threshold }));
  } catch {
    /* */
  }
}

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
  /** 与设置页一致：spread_pct_desc = 价差越大越靠前 */
  const [spreadSort, setSpreadSort] = useState<string>("spread_pct_desc");
  /** 仅展示价差% &gt; 0（理论上有跨所套利空间的方向） */
  const [positiveSpreadOnly, setPositiveSpreadOnly] = useState<boolean>(loadPositiveOnly);
  const [spreadMinState, setSpreadMinState] = useState(() => loadSpreadMinFilter());
  const spreadMinEnabled = spreadMinState.enabled;
  const spreadMinThreshold = spreadMinState.threshold;

  const [pageSize, setPageSize] = useState<number>(() => loadPageSize());
  const [page, setPage] = useState(1);
  const [autoRefreshSec, setAutoRefreshSec] = useState<number>(() => loadAutoRefreshSec());
  const platKey = useMemo(() => JSON.stringify(plat), [plat]);

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
    api<{ spread_sort?: string }>("/api/settings")
      .then((d) => {
        if (d.spread_sort === "spread_pct_asc" || d.spread_sort === "spread_pct_desc") {
          setSpreadSort(d.spread_sort);
        }
      })
      .catch(() => {});
  }, []);

  const manualRefresh = useCallback(async () => {
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
  }, []);

  /** 关闭 = 不连 SSE、不定時拉取；非关闭 = 服务端推送 + 按秒定时 HTTP 拉取 */
  useEffect(() => {
    if (autoRefreshSec <= 0) return;
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
  }, [autoRefreshSec]);

  useEffect(() => {
    if (autoRefreshSec <= 0) return;
    const id = window.setInterval(() => {
      void manualRefresh();
    }, autoRefreshSec * 1000);
    return () => window.clearInterval(id);
  }, [autoRefreshSec, manualRefresh]);

  useEffect(() => {
    if (autoRefreshSec <= 0) void manualRefresh();
  }, [autoRefreshSec, manualRefresh]);

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
      if (positiveSpreadOnly && (Number(r.spreadPct) || 0) <= 0) return false;
      if (spreadMinEnabled) {
        const pct = Number(r.spreadPct) || 0;
        const min = spreadMinThreshold === "all" ? 0 : Number(spreadMinThreshold);
        if (pct <= min) return false;
      }
      return true;
    });

    const bySpread = (a: Row, b: Row) => {
      const va = Number(a.spreadPct) || 0;
      const vb = Number(b.spreadPct) || 0;
      return spreadSort === "spread_pct_asc" ? va - vb : vb - va;
    };

    list = [...list].sort((a, b) => {
      if (favFirst) {
        const fa = fav.has(a.pair_key) ? 1 : 0;
        const fb = fav.has(b.pair_key) ? 1 : 0;
        if (fa !== fb) return fb - fa;
      }
      return bySpread(a, b);
    });

    return list;
  }, [
    rows,
    plat,
    category,
    favFirst,
    fav,
    symbolQuery,
    spreadSort,
    positiveSpreadOnly,
    spreadMinEnabled,
    spreadMinThreshold,
  ]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize) || 1);

  useEffect(() => {
    setPage(1);
  }, [
    symbolQuery,
    category,
    spreadSort,
    positiveSpreadOnly,
    spreadMinEnabled,
    spreadMinThreshold,
    favFirst,
    platKey,
  ]);

  useEffect(() => {
    setPage((p) => Math.min(p, totalPages));
  }, [totalPages]);

  useEffect(() => {
    setPage(1);
  }, [pageSize]);

  const pageRows = useMemo(() => {
    const safePage = Math.min(page, totalPages);
    const start = (safePage - 1) * pageSize;
    return filtered.slice(start, start + pageSize);
  }, [filtered, page, pageSize, totalPages]);

  /** 条形图按「一档单价」相对缩放（原为一档名义 USDT 深度） */
  const maxAskBuyPx = useMemo(
    () => Math.max(1, ...pageRows.map((r) => Number(r.ask1Buy) || 0)),
    [pageRows],
  );
  const maxBidSellPx = useMemo(
    () => Math.max(1, ...pageRows.map((r) => Number(r.bid1Sell) || 0)),
    [pageRows],
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
          <label className="analysis-toggle" title="隐藏价差% ≤ 0 的行（卖一价不高于买一价的方向）">
            <input
              type="checkbox"
              checked={positiveSpreadOnly}
              onChange={(e) => {
                const v = e.target.checked;
                setPositiveSpreadOnly(v);
                try {
                  localStorage.setItem(POSITIVE_SPREAD_ONLY_KEY, v ? "1" : "0");
                } catch {
                  /* */
                }
              }}
            />
            仅正价差
          </label>
        </div>
        <div className="analysis-toolbar-group analysis-toolbar-group--spread-min">
          <label
            className="analysis-toggle"
            title="开启后只保留价差%大于所选下限；下限定在 0.1%～3%（步长 0.1%），「全部」为仅 >0"
          >
            <input
              type="checkbox"
              checked={spreadMinEnabled}
              onChange={(e) => {
                const v = e.target.checked;
                setSpreadMinState((s) => {
                  const n = { ...s, enabled: v };
                  persistSpreadMinFilter(n.enabled, n.threshold);
                  return n;
                });
              }}
            />
            价差筛选(大于)
          </label>
          <select
            className="analysis-select"
            value={spreadMinThreshold}
            disabled={!spreadMinEnabled}
            aria-label="价差下限"
            onChange={(e) => {
              const threshold = e.target.value;
              setSpreadMinState((s) => {
                const n = { ...s, threshold };
                persistSpreadMinFilter(n.enabled, n.threshold);
                return n;
              });
            }}
          >
            {SPREAD_MIN_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
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
        <div className="analysis-toolbar-group analysis-toolbar-group--refresh" style={{ marginLeft: "auto" }}>
          <label className="analysis-toolbar-label" htmlFor="spread-auto-refresh">
            自动刷新
          </label>
          <select
            id="spread-auto-refresh"
            className="analysis-select"
            value={autoRefreshSec}
            title="关闭：停止实时推送与定时拉取，仅可手动刷新；非关闭：SSE 推送 + 按秒请求 /api/spreads"
            onChange={(e) => {
              const sec = Number(e.target.value);
              setAutoRefreshSec(sec);
              try {
                localStorage.setItem(AUTO_REFRESH_SEC_KEY, String(sec));
              } catch {
                /* */
              }
            }}
          >
            {AUTO_REFRESH_OPTIONS.map((o) => (
              <option key={o.sec} value={o.sec}>
                {o.label}
              </option>
            ))}
          </select>
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
            {pageRows.map((r) => {
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

      <div className="analysis-pagination" role="navigation" aria-label="表格分页">
        <span className="analysis-pagination-total">共 {filtered.length} 条（过滤后）</span>
        <label className="analysis-pagination-pagesize">
          每页
          <select
            className="analysis-select analysis-select--compact"
            value={pageSize}
            onChange={(e) => {
              const n = Number(e.target.value);
              setPageSize(n);
              try {
                localStorage.setItem(PAGE_SIZE_KEY, String(n));
              } catch {
                /* */
              }
            }}
          >
            {PAGE_SIZE_OPTIONS.map((n) => (
              <option key={n} value={n}>
                {n} 条
              </option>
            ))}
          </select>
        </label>
        <div className="analysis-pagination-nav">
          <button
            type="button"
            className="analysis-page-btn"
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            上一页
          </button>
          <span className="analysis-pagination-indicator">
            第 {Math.min(page, totalPages)} / {totalPages} 页
          </span>
          <button
            type="button"
            className="analysis-page-btn"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  );
}
