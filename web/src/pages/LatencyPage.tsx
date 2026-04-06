import { startTransition, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "../api";

type Row = {
  exchange: string;
  ok: boolean;
  avgMs: number | null;
  minMs: number | null;
  maxMs: number | null;
  error: string | null;
  endpoint?: string;
};

type Payload = { rounds: number; ts: number; results: Row[] };

function isAbortError(e: unknown): boolean {
  return (
    e instanceof DOMException && e.name === "AbortError"
  ) || (e instanceof Error && e.name === "AbortError");
}

export default function LatencyPage() {
  const [rounds, setRounds] = useState(3);
  const [loading, setLoading] = useState(false);
  const [elapsedMs, setElapsedMs] = useState(0);
  const [data, setData] = useState<Payload | null>(null);
  const [err, setErr] = useState("");

  const abortRef = useRef<AbortController | null>(null);
  /** 防止先发起的请求在后发起的请求之后结束，误把 loading 关掉 */
  const runGenRef = useRef(0);

  useEffect(() => {
    if (!loading) {
      setElapsedMs(0);
      return;
    }
    const t0 = performance.now();
    const id = window.setInterval(() => {
      setElapsedMs(Math.round(performance.now() - t0));
    }, 100);
    return () => window.clearInterval(id);
  }, [loading]);

  useEffect(
    () => () => {
      abortRef.current?.abort();
    },
    [],
  );

  const run = useCallback(async () => {
    abortRef.current?.abort();
    const myGen = ++runGenRef.current;
    const ac = new AbortController();
    abortRef.current = ac;

    setLoading(true);
    setErr("");
    try {
      const r = await api<Payload>(`/api/latency?rounds=${encodeURIComponent(String(rounds))}`, {
        signal: ac.signal,
        cache: "no-store",
      });
      startTransition(() => {
        setData(r);
      });
    } catch (e) {
      if (isAbortError(e)) return;
      setErr(e instanceof Error ? e.message : "请求失败");
      setData(null);
    } finally {
      if (runGenRef.current !== myGen) return;
      if (abortRef.current === ac) {
        abortRef.current = null;
      }
      setLoading(false);
    }
  }, [rounds]);

  const stop = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  const sortedResults = useMemo(() => {
    if (!data?.results?.length) return [];
    return [...data.results].sort((a, b) => {
      if (a.ok !== b.ok) return a.ok ? -1 : 1;
      const av = a.avgMs;
      const bv = b.avgMs;
      if (av == null && bv == null) return 0;
      if (av == null) return 1;
      if (bv == null) return -1;
      return av - bv;
    });
  }, [data]);

  const bestExchange = useMemo(() => {
    const first = sortedResults.find((r) => r.ok && r.avgMs != null);
    return first?.exchange ?? null;
  }, [sortedResults]);

  const onRoundsChange = (raw: string) => {
    const n = Number.parseInt(raw, 10);
    if (Number.isNaN(n)) {
      setRounds(1);
      return;
    }
    setRounds(Math.min(10, Math.max(1, n)));
  };

  return (
    <div>
      <div className="panel">
        <h2 style={{ marginTop: 0 }}>交易所 API 延时</h2>
        <p style={{ color: "var(--muted)", fontSize: 14, lineHeight: 1.6 }}>
          对各所<strong>公共 REST</strong>发起 GET（无需 API Key），测量本机到服务器的往返时间（ms）。结果受本地网络、DNS、防火墙、代理、地区政策等影响；
          若只有部分所成功、部分报 SSL/超时/连接失败，多为<strong>网络环境</strong>对特定域名限制，可换热点或检查系统代理。与 WebSocket/下单延时<strong>不是同一指标</strong>。
        </p>
        <form
          className="field"
          style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem", alignItems: "flex-end" }}
          onSubmit={(e) => {
            e.preventDefault();
            if (!loading) void run();
          }}
        >
          <div>
            <label htmlFor="latency-rounds">每所连续请求次数（1–10）</label>
            <input
              id="latency-rounds"
              type="number"
              min={1}
              max={10}
              value={rounds}
              onChange={(e) => onRoundsChange(e.target.value)}
            />
          </div>
          <button type="submit" className="primary" disabled={loading} aria-busy={loading}>
            {loading ? "测试中…" : "开始测试"}
          </button>
          {loading && (
            <button type="button" className="ghost" onClick={stop}>
              取消
            </button>
          )}
        </form>
        {loading && (
          <p style={{ marginTop: "0.75rem", fontSize: 13, color: "var(--muted)" }} aria-live="polite">
            已用时 {(elapsedMs / 1000).toFixed(1)}s（各所并行测速，耗时会随轮数增加）
          </p>
        )}
        {err && <p className="error" style={{ marginTop: "0.75rem" }}>{err}</p>}
      </div>

      {data && (
        <div className="panel" style={{ overflowX: "auto" }}>
          <p style={{ marginTop: 0, fontSize: 13, color: "var(--muted)" }}>
            采样轮数 {data.rounds} · 时间 {new Date(data.ts).toLocaleString()}
            {bestExchange ? (
              <>
                {" "}
                · 当前最快（平均）<strong>{bestExchange}</strong>
              </>
            ) : null}
          </p>
          <table>
            <thead>
              <tr>
                <th>交易所</th>
                <th>平均 ms</th>
                <th>最小</th>
                <th>最大</th>
                <th>状态</th>
                <th>测速 URL</th>
              </tr>
            </thead>
            <tbody>
              {sortedResults.map((r) => {
                const isBest = bestExchange != null && r.exchange === bestExchange && r.ok;
                return (
                  <tr
                    key={r.exchange}
                    style={
                      isBest
                        ? { background: "rgba(34, 197, 94, 0.09)" }
                        : undefined
                    }
                  >
                    <td style={{ fontWeight: 700 }}>{r.exchange}</td>
                    <td className={r.ok ? "row-blue" : ""}>{r.avgMs != null ? r.avgMs : "—"}</td>
                    <td>{r.minMs != null ? r.minMs : "—"}</td>
                    <td>{r.maxMs != null ? r.maxMs : "—"}</td>
                    <td className={r.ok ? "row-white" : "row-red"}>{r.ok ? "成功" : r.error || "失败"}</td>
                    <td style={{ fontSize: 11, color: "var(--muted)", maxWidth: 360 }} title={r.endpoint}>
                      {r.endpoint ? (
                        <code style={{ wordBreak: "break-all" }}>{r.endpoint}</code>
                      ) : (
                        "—"
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
