import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "../api";

export default function SpreadRawPage() {
  const [data, setData] = useState<unknown>(null);
  const [err, setErr] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [autoRefreshSec, setAutoRefreshSec] = useState<number>(3);
  const timerRef = useRef<number | null>(null);

  const pretty = useMemo(() => {
    try {
      return JSON.stringify(data, null, 2);
    } catch {
      return String(data ?? "");
    }
  }, [data]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    setErr("");
    try {
      const d = await api<unknown>("/api/spreads");
      setData(d);
    } catch (e) {
      setErr(e instanceof Error ? e.message : "请求失败");
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (timerRef.current) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    if (autoRefreshSec <= 0) return;
    timerRef.current = window.setInterval(() => void refresh(), autoRefreshSec * 1000);
    return () => {
      if (timerRef.current) window.clearInterval(timerRef.current);
      timerRef.current = null;
    };
  }, [autoRefreshSec, refresh]);

  async function copyJson() {
    try {
      await navigator.clipboard.writeText(pretty);
    } catch {
      setErr("复制失败，请检查浏览器权限");
    }
  }

  return (
    <div className="panel">
      <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", justifyContent: "space-between" }}>
        <h2 style={{ margin: 0 }}>Spreads 原始数据</h2>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          <label style={{ display: "inline-flex", gap: 6, alignItems: "center", fontSize: 12 }}>
            自动刷新
            <select
              value={autoRefreshSec}
              onChange={(e) => setAutoRefreshSec(Number(e.target.value))}
              title="0=关闭"
            >
              <option value={0}>关闭</option>
              <option value={1}>1秒</option>
              <option value={3}>3秒</option>
              <option value={5}>5秒</option>
              <option value={10}>10秒</option>
            </select>
          </label>
          <button type="button" className="primary" onClick={() => void refresh()} disabled={refreshing}>
            {refreshing ? "刷新中…" : "刷新"}
          </button>
          <button type="button" onClick={() => void copyJson()} disabled={!pretty}>
            复制 JSON
          </button>
        </div>
      </div>

      {err && <p style={{ marginTop: 12, color: "var(--a-red)" }}>{err}</p>}

      <pre
        className="analysis-raw-json"
        style={{
          marginTop: 12,
          maxHeight: "70vh",
          overflow: "auto",
          padding: 12,
          border: "1px solid rgba(0,0,0,0.2)",
          background: "#0b0b0b",
          color: "#eaeaea",
          borderRadius: 8,
          fontSize: 12,
          lineHeight: 1.35,
        }}
      >
        {pretty || "（暂无数据）"}
      </pre>
    </div>
  );
}

