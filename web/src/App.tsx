import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useEffect, useState } from "react";
import { api, getToken } from "./api";
import Login from "./pages/Login";
import SpreadPage from "./pages/SpreadPage";
import VolatilityPage from "./pages/VolatilityPage";
import BlacklistPage from "./pages/BlacklistPage";
import SettingsPage from "./pages/SettingsPage";
import LatencyPage from "./pages/LatencyPage";

type Me = { id: string; username: string; note: string; volatility_enabled: boolean };

function LoggedInShell({ me }: { me: Me }) {
  const { pathname } = useLocation();
  const fullWidthMain = pathname === "/";
  return (
    <div className="layout">
      <header className="nt-header">
        <span className="nt-brand">套利监控</span>
        <nav>
          <NavLink to="/" end className={({ isActive }) => (isActive ? "active" : "")}>
            数据分析
          </NavLink>
          <NavLink to="/vol" className={({ isActive }) => (isActive ? "active" : "")}>
            暴涨暴跌
          </NavLink>
          <NavLink to="/blacklist" className={({ isActive }) => (isActive ? "active" : "")}>
            过滤列表
          </NavLink>
          <NavLink to="/settings" className={({ isActive }) => (isActive ? "active" : "")}>
            设置
          </NavLink>
          <NavLink to="/latency" className={({ isActive }) => (isActive ? "active" : "")}>
            API 延时
          </NavLink>
        </nav>
        <span className="badge">{me.username}</span>
      </header>
      <main className={fullWidthMain ? "main--full" : undefined}>
        <Routes>
          <Route path="/" element={<SpreadPage />} />
          <Route path="/vol" element={<VolatilityPage volEnabled={me.volatility_enabled} />} />
          <Route path="/blacklist" element={<BlacklistPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/latency" element={<LatencyPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}

function initialMe(): Me | null | false {
  if (typeof window === "undefined") return null;
  return getToken() ? null : false;
}

export default function App() {
  const [me, setMe] = useState<Me | null | false>(initialMe);

  useEffect(() => {
    const t = getToken();
    if (!t) {
      setMe(false);
      return;
    }
    api<Me>("/api/me")
      .then(setMe)
      .catch(() => setMe(false));
  }, []);

  if (me === null) {
    return (
      <div className="app-loading">
        <div className="pulse" aria-hidden />
        <p>加载中…</p>
      </div>
    );
  }

  if (me === false) {
    return (
      <Routes>
        <Route path="*" element={<Login onLoggedIn={() => window.location.reload()} />} />
      </Routes>
    );
  }

  return <LoggedInShell me={me} />;
}
