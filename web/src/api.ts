const TOKEN_KEY = "arb_token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(t: string | null): void {
  if (t) localStorage.setItem(TOKEN_KEY, t);
  else localStorage.removeItem(TOKEN_KEY);
}

export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...(init?.headers ?? {}),
  };
  if (token) (headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
  const r = await fetch(path, { ...init, headers });
  if (!r.ok) {
    const err = await r.json().catch(() => ({}));
    const body = err as { error?: string; message?: string; path?: string };
    let msg = body.error || body.message || r.statusText;
    if (r.status === 404) {
      msg =
        (msg || "Not Found") +
        " — 若刚加过后端接口，请在 backend 目录重新执行 mvn spring-boot:run 重启服务。";
    }
    throw new Error(msg);
  }
  return r.json() as Promise<T>;
}
