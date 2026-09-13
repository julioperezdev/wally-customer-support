export type BackofficeSession = {
  subject: string;
  username: string | null;
  groups: string[];
  capabilities: string[];
};

type SessionEnvelope = {
  status?: string;
};

type AuthErrorEnvelope = {
  code?: string;
  challenge?: string;
};

const DEFAULT_AUTH_BASE_URL = import.meta.env.VITE_WCS_AUTH_BASE_URL ?? "/internal/auth";

export async function loginBackoffice(
  username: string,
  password: string,
  baseUrl = DEFAULT_AUTH_BASE_URL
): Promise<SessionEnvelope> {
  return request<SessionEnvelope>(baseUrl, "/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  });
}

export async function refreshBackofficeSession(baseUrl = DEFAULT_AUTH_BASE_URL): Promise<SessionEnvelope> {
  return request<SessionEnvelope>(baseUrl, "/refresh", { method: "POST" });
}

export async function getBackofficeSession(baseUrl = DEFAULT_AUTH_BASE_URL): Promise<BackofficeSession> {
  return request<BackofficeSession>(baseUrl, "/me");
}

export async function logoutBackoffice(baseUrl = DEFAULT_AUTH_BASE_URL): Promise<void> {
  await request<void>(baseUrl, "/logout", { method: "POST" }, [204]);
}

export class BackofficeAuthError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    public readonly challenge?: string
  ) {
    super(`${code} (${status})`);
    this.name = "BackofficeAuthError";
  }
}

async function request<T>(
  baseUrl: string,
  path: string,
  init: RequestInit = {},
  acceptedStatuses: number[] = []
): Promise<T> {
  const root = baseUrl.replace(/\/+$/, "");
  const response = await fetch(`${root}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(init.headers as Record<string, string> | undefined)
    }
  });
  if (!response.ok && !acceptedStatuses.includes(response.status)) {
    let envelope: AuthErrorEnvelope = {};
    try {
      envelope = await response.json() as AuthErrorEnvelope;
    } catch {
      // The status is enough when the server has no JSON envelope.
    }
    throw new BackofficeAuthError(response.status, envelope.code ?? "BACKOFFICE_AUTH_ERROR", envelope.challenge);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}
