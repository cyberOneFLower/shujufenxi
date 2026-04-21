import { NavLink, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { useEffect, useMemo, useRef, useState } from "react";
import { api, getToken, setToken } from "./api";
import Login from "./pages/Login";
import SpreadPage from "./pages/SpreadPage";
import VolatilityPage from "./pages/VolatilityPage";
import BlacklistPage from "./pages/BlacklistPage";
import SettingsPage from "./pages/SettingsPage";
import LatencyPage from "./pages/LatencyPage";
import AdminUsersPage from "./pages/AdminUsersPage";
import ProfilePage from "./pages/ProfilePage";
import SpreadRawPage from "./pages/SpreadRawPage";

export type Me = { id: string; username: string; note: string; volatility_enabled: boolean; role?: string };

function LoggedInShell({ me }: { me: Me }) {
  const { pathname } = useLocation();
  const nav = useNavigate();
  const fullWidthMain = pathname === "/";
  const isAdmin = useMemo(() => String(me.role || "").toUpperCase() === "ADMIN", [me.role]);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    function onDocMouseDown(e: MouseEvent) {
      if (!menuRef.current) return;
      const target = e.target as Node | null;
      if (target && menuRef.current.contains(target)) return;
      setMenuOpen(false);
    }
    if (!menuOpen) return;
    document.addEventListener("mousedown", onDocMouseDown);
    return () => document.removeEventListener("mousedown", onDocMouseDown);
  }, [menuOpen]);

  async function logout() {
    try {
      await api("/api/logout", { method: "POST" });
    } catch {
      // ignore: token 可能已过期/后端重启
    } finally {
      setToken(null);
      window.location.reload();
    }
  }

  return (
    <div className="layout">
      <header className="nt-header">
        <span className="nt-brand">套利监控</span>
        <nav>
          <NavLink to="/" end className={({ isActive }) => (isActive ? "active" : "")}>
            数据分析
          </NavLink>
          <NavLink to="/spreads/raw" className={({ isActive }) => (isActive ? "active" : "")}>
            原始Spreads
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
          {isAdmin && (
            <NavLink to="/admin/users" className={({ isActive }) => (isActive ? "active" : "")}>
              管理员
            </NavLink>
          )}
        </nav>
        <div className="nt-header-right">
          <div className="user-menu" ref={menuRef}>
            <button
              type="button"
              className="badge badge--link user-menu-trigger"
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((v) => !v)}
              title="打开用户菜单"
            >
              {isAdmin ? "ADMIN" : me.username}
              <span className="user-menu-caret" aria-hidden>
                ▾
              </span>
            </button>
            {menuOpen && (
              <div className="user-menu-pop" role="menu" aria-label="用户菜单">
                <button
                  type="button"
                  className="user-menu-item"
                  role="menuitem"
                  onClick={() => {
                    setMenuOpen(false);
                    nav("/profile");
                  }}
                >
                  个人资料
                </button>
                {isAdmin && (
                  <button
                    type="button"
                    className="user-menu-item"
                    role="menuitem"
                    onClick={() => {
                      setMenuOpen(false);
                      nav("/admin/users");
                    }}
                  >
                    用户管理
                  </button>
                )}
                <div className="user-menu-sep" role="separator" />
                <button type="button" className="user-menu-item user-menu-item--danger" role="menuitem" onClick={logout}>
                  登出
                </button>
              </div>
            )}
          </div>
        </div>
      </header>
      <main className={fullWidthMain ? "main--full" : undefined}>
        <Routes>
          <Route path="/" element={<SpreadPage />} />
          <Route path="/spreads/raw" element={<SpreadRawPage />} />
          <Route path="/vol" element={<VolatilityPage volEnabled={me.volatility_enabled} />} />
          <Route path="/blacklist" element={<BlacklistPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/latency" element={<LatencyPage />} />
          <Route path="/profile" element={<ProfilePage me={me} />} />
          <Route path="/admin/users" element={isAdmin ? <AdminUsersPage /> : <Navigate to="/" replace />} />
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
