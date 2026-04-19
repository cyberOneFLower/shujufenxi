import { useState } from "react";
import { api, setToken } from "../api";

export default function Login({ onLoggedIn }: { onLoggedIn: () => void }) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin123");
  const [note, setNote] = useState("");
  const [mode, setMode] = useState<"login" | "register">("login");
  const [error, setError] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      const path = mode === "register" ? "/api/register" : "/api/login";
      const r = await api<{ token: string }>(path, {
        method: "POST",
        body: JSON.stringify(mode === "register" ? { username, password, note } : { username, password }),
      });
      setToken(r.token);
      onLoggedIn();
    } catch (err) {
      setError(err instanceof Error ? err.message : mode === "register" ? "注册失败" : "登录失败");
    }
  }

  return (
    <div className="login-page">
      <div className="login-topbar">
        <span className="login-brand">套利监控</span>
        <span className="login-beta">监控面板</span>
      </div>

      <div className="login-hero-wrap">
        <section className="login-hero" aria-labelledby="login-hero-title">
          <h1 id="login-hero-title">
            从
            <br />
            0 到<span className="hl">清晰</span>
          </h1>
          <p className="lead">
            任何人都能先看懂价差再动手。从你熟悉的交易所开始，看哪些机会站得住脚——数据对齐、少折腾。
          </p>
          <ul className="login-features">
            <li>同秒对齐的深度与价差推送</li>
            <li>暴涨暴跌与过滤列表，一手掌握</li>
            <li>本地部署，密钥与数据留在你手中</li>
          </ul>
        </section>

        <div className="login-card">
          <h2>{mode === "register" ? "创建账号" : "进入控制台"}</h2>
          <p className="hint">
            {mode === "register" ? (
              <>
                若提示“已关闭自助注册”，请用 <code>admin</code> 进入后在管理员页创建用户
              </>
            ) : (
              <>
                默认账号 <code>admin</code> / <code>admin123</code>
                <br />
                首次启动后端时自动创建
              </>
            )}
          </p>
          <form onSubmit={submit}>
            <div className="field">
              <label>用户名</label>
              <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
            </div>
            <div className="field">
              <label>密码</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </div>
            {mode === "register" && (
              <div className="field">
                <label>备注（可选）</label>
                <input value={note} onChange={(e) => setNote(e.target.value)} autoComplete="off" />
              </div>
            )}
            {error && <p className="error">{error}</p>}
            <button type="submit" className="primary">
              {mode === "register" ? "注册并登录" : "登录"}
            </button>
            <button
              type="button"
              className="ghost"
              style={{ marginLeft: 10 }}
              onClick={() => {
                setError("");
                setMode((m) => (m === "login" ? "register" : "login"));
              }}
            >
              {mode === "login" ? "去注册" : "去登录"}
            </button>
          </form>
        </div>
      </div>

      <footer className="login-footer">套利监控 · 本地部署</footer>
    </div>
  );
}
