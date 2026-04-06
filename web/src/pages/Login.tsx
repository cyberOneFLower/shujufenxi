import { useState } from "react";
import { api, setToken } from "../api";

export default function Login({ onLoggedIn }: { onLoggedIn: () => void }) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin123");
  const [error, setError] = useState("");

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    try {
      const r = await api<{ token: string }>("/api/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      setToken(r.token);
      onLoggedIn();
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败");
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
          <h2>进入控制台</h2>
          <p className="hint">
            默认账号 <code>admin</code> / <code>admin123</code>
            <br />
            首次启动后端时自动创建
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
            {error && <p className="error">{error}</p>}
            <button type="submit" className="primary">
              打开应用
            </button>
          </form>
        </div>
      </div>

      <footer className="login-footer">套利监控 · 本地部署</footer>
    </div>
  );
}
