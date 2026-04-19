import { useEffect, useMemo, useState } from "react";
import { api } from "../api";

type AdminUser = {
  id: string;
  username: string;
  note?: string;
  role?: string;
  enabled?: boolean;
  volatility_enabled?: boolean;
  created_at?: string | null;
};

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [err, setErr] = useState("");
  const [q, setQ] = useState("");
  const [loading, setLoading] = useState(false);

  const filtered = useMemo(() => {
    const s = q.trim().toLowerCase();
    if (!s) return users;
    return users.filter((u) => {
      const a = (u.username || "").toLowerCase();
      const b = (u.note || "").toLowerCase();
      return a.includes(s) || b.includes(s);
    });
  }, [users, q]);

  async function refresh() {
    setLoading(true);
    setErr("");
    try {
      const r = await api<{ users: AdminUser[] }>("/api/admin/users");
      setUsers(Array.isArray(r.users) ? r.users : []);
    } catch (e) {
      setErr(e instanceof Error ? e.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  async function createUser() {
    const username = window.prompt("新用户名（必填）")?.trim() || "";
    if (!username) return;
    const password = window.prompt("初始密码（必填）")?.trim() || "";
    if (!password) return;
    const note = window.prompt("备注（可选）")?.trim() || "";
    const vol = window.confirm("是否启用「暴涨暴跌」模块？\n确定=启用，取消=停用");
    try {
      await api("/api/admin/users", {
        method: "POST",
        body: JSON.stringify({
          username,
          password,
          note,
          role: "USER",
          enabled: true,
          volatility_enabled: vol,
        }),
      });
      await refresh();
    } catch (e) {
      setErr(e instanceof Error ? e.message : "创建失败");
    }
  }

  async function toggleEnabled(u: AdminUser) {
    const next = !(u.enabled ?? true);
    const ok = window.confirm(`${next ? "启用" : "停用"}用户：${u.username}？`);
    if (!ok) return;
    try {
      await api(`/api/admin/users/${encodeURIComponent(u.id)}`, {
        method: "PATCH",
        body: JSON.stringify({ enabled: next }),
      });
      await refresh();
    } catch (e) {
      setErr(e instanceof Error ? e.message : "操作失败");
    }
  }

  async function toggleVol(u: AdminUser) {
    const next = !(u.volatility_enabled ?? true);
    try {
      await api(`/api/admin/users/${encodeURIComponent(u.id)}`, {
        method: "PATCH",
        body: JSON.stringify({ volatility_enabled: next }),
      });
      await refresh();
    } catch (e) {
      setErr(e instanceof Error ? e.message : "操作失败");
    }
  }

  async function resetPassword(u: AdminUser) {
    const password = window.prompt(`为 ${u.username} 设置新密码（必填）`)?.trim() || "";
    if (!password) return;
    try {
      await api(`/api/admin/users/${encodeURIComponent(u.id)}/reset-password`, {
        method: "POST",
        body: JSON.stringify({ password }),
      });
      window.alert("已重置密码，并已踢下线该用户的已登录会话。");
    } catch (e) {
      setErr(e instanceof Error ? e.message : "重置失败");
    }
  }

  async function revokeTokens(u: AdminUser) {
    const ok = window.confirm(`踢下线 ${u.username} 的所有会话？`);
    if (!ok) return;
    try {
      await api(`/api/admin/users/${encodeURIComponent(u.id)}/revoke-tokens`, {
        method: "POST",
      });
      window.alert("已踢下线。");
    } catch (e) {
      setErr(e instanceof Error ? e.message : "操作失败");
    }
  }

  async function deleteUser(u: AdminUser) {
    const ok = window.confirm(`删除用户：${u.username}？\n此操作不可恢复。`);
    if (!ok) return;
    try {
      await api(`/api/admin/users/${encodeURIComponent(u.id)}`, {
        method: "DELETE",
      });
      await refresh();
    } catch (e) {
      setErr(e instanceof Error ? e.message : "删除失败");
    }
  }

  return (
    <div className="panel">
      <h2>用户管理</h2>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem", alignItems: "center" }}>
        <input
          type="search"
          placeholder="搜索用户名/备注"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          style={{
            minWidth: 220,
            padding: "0.45rem 0.65rem",
            border: "1px solid var(--border)",
            borderRadius: 8,
          }}
        />
        <button type="button" className="analysis-btn-primary" onClick={() => void createUser()}>
          新增用户
        </button>
        <button
          type="button"
          className="analysis-btn-primary analysis-btn-secondary"
          disabled={loading}
          onClick={() => void refresh()}
        >
          {loading ? "刷新中…" : "刷新"}
        </button>
        <span style={{ marginLeft: "auto", color: "var(--muted)", fontSize: 12 }}>
          共 {filtered.length} / {users.length}
        </span>
      </div>

      {err && <div className="analysis-error">{err}</div>}

      <div style={{ marginTop: 12, overflow: "auto", border: "1px solid var(--border)", borderRadius: 12 }}>
        <table>
          <thead>
            <tr>
              <th style={{ width: 180 }}>用户名</th>
              <th style={{ width: 90 }}>角色</th>
              <th style={{ width: 90 }}>状态</th>
              <th style={{ width: 130 }}>暴涨暴跌</th>
              <th>备注</th>
              <th style={{ width: 220 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((u) => {
              const enabled = u.enabled ?? true;
              const role = String(u.role || "USER").toUpperCase();
              const vol = u.volatility_enabled ?? true;
              return (
                <tr key={u.id}>
                  <td style={{ fontWeight: 700 }}>{u.username}</td>
                  <td>{role}</td>
                  <td>{enabled ? "启用" : "停用"}</td>
                  <td>
                    <button type="button" className="analysis-menu-btn" onClick={() => void toggleVol(u)}>
                      {vol ? "已启用" : "已停用"}
                    </button>
                  </td>
                  <td>{u.note || ""}</td>
                  <td style={{ whiteSpace: "nowrap" }}>
                    <button type="button" className="analysis-menu-btn" onClick={() => void toggleEnabled(u)}>
                      {enabled ? "停用" : "启用"}
                    </button>{" "}
                    <button type="button" className="analysis-menu-btn" onClick={() => void resetPassword(u)}>
                      重置密码
                    </button>{" "}
                    <button type="button" className="analysis-menu-btn" onClick={() => void revokeTokens(u)}>
                      踢下线
                    </button>{" "}
                    {role !== "ADMIN" && (
                      <button type="button" className="analysis-menu-btn" onClick={() => void deleteUser(u)}>
                        删除
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
            {filtered.length === 0 && (
              <tr>
                <td colSpan={6} style={{ padding: "1rem", color: "var(--muted)" }}>
                  暂无用户
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

