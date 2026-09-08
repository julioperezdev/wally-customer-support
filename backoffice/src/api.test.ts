import { afterEach, describe, expect, it, vi } from "vitest";
import { ControlPlaneError, createControlPlaneClient } from "./api";

describe("control plane client", () => {
  afterEach(() => vi.restoreAllMocks());

  it("sends read-only filters and the session token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ items: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await createControlPlaneClient("/internal/agent-evaluations/", "session-token").searchRuns({
      agentId: "catalog-specialist",
      provider: "bedrock",
      page: 1,
      size: 10
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/internal/agent-evaluations/runs?agentId=catalog-specialist&provider=bedrock&page=1&size=10",
      { headers: { Accept: "application/json", Authorization: "Bearer session-token" } }
    );
  });

  it("maps the sanitized backend error envelope", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: "ACCESS_DENIED" }), { status: 403 })));

    await expect(createControlPlaneClient("/internal/agent-evaluations", "").searchRuns({})).rejects.toEqual(
      new ControlPlaneError(403, "ACCESS_DENIED")
    );
  });
});
