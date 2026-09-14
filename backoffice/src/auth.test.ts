import { describe, expect, it, vi } from "vitest";
import {
  BackofficeAuthError,
  getBackofficeSession,
  loginBackoffice,
  logoutBackoffice,
  refreshBackofficeSession,
  createSessionRefresher
} from "./auth";

describe("backoffice API authentication", () => {
  it("sends credentials to the API and includes browser credentials", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: "AUTHENTICATED" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await loginBackoffice("operator@example.com", "not-a-real-password", "http://api.test/internal/auth");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://api.test/internal/auth/login",
      expect.objectContaining({
        method: "POST",
        credentials: "include",
        body: JSON.stringify({ username: "operator@example.com", password: "not-a-real-password" })
      })
    );
  });

  it("reads the sanitized identity without exposing tokens to the frontend", async () => {
    const session = {
      subject: "subject-1",
      username: "operator@example.com",
      groups: ["store-viewer"],
      capabilities: ["backoffice.catalog.read"]
    };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(session), { status: 200 })));

    await expect(getBackofficeSession("/internal/auth")).resolves.toEqual(session);
  });

  it("supports refresh and logout through cookies", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: "REFRESHED" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await refreshBackofficeSession("/internal/auth");
    await logoutBackoffice("/internal/auth");

    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({ credentials: "include", method: "POST" }));
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({ credentials: "include", method: "POST" }));
  });

  it("preserves a stable error code and Cognito challenge", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: "COGNITO_CHALLENGE_REQUIRED", challenge: "NEW_PASSWORD_REQUIRED" }), { status: 428 })
    ));

    await expect(loginBackoffice("operator@example.com", "secret", "/internal/auth"))
      .rejects.toEqual(expect.objectContaining<Partial<BackofficeAuthError>>({
        status: 428,
        code: "COGNITO_CHALLENGE_REQUIRED",
        challenge: "NEW_PASSWORD_REQUIRED"
      }));
  });

  it("shares a concurrent refresh request between expired panels", async () => {
    let resolveRefresh: ((value: Response) => void) | undefined;
    const refreshResponse = new Promise<Response>((resolve) => { resolveRefresh = resolve; });
    const fetchMock = vi.fn().mockReturnValue(refreshResponse);
    vi.stubGlobal("fetch", fetchMock);
    const refresh = createSessionRefresher("/internal/auth");

    const first = refresh();
    const second = refresh();
    resolveRefresh?.(new Response(JSON.stringify({ status: "REFRESHED" }), { status: 200 }));

    await expect(Promise.all([first, second])).resolves.toEqual([
      { status: "REFRESHED" },
      { status: "REFRESHED" }
    ]);
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
