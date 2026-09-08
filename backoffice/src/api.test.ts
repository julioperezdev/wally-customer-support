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

  it("queries the registry through its own read-only endpoint", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([]), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await createControlPlaneClient(
      "/internal/agent-evaluations",
      "session-token",
      "/internal/agent-registry/"
    ).listAgents({ environment: "prod", channel: "telegram", useCase: "catalog-search", limit: 10 });

    expect(fetchMock).toHaveBeenCalledWith(
      "/internal/agent-registry/agents?environment=prod&channel=telegram&useCase=catalog-search&limit=10",
      { headers: { Accept: "application/json", Authorization: "Bearer session-token" } }
    );
  });

  it("runs a non-mutating activation preflight through the read scope", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: "READY",
      canActivate: true,
      checks: []
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await createControlPlaneClient("/internal/agent-evaluations", "session-token").preflightActivation({
      agentId: "catalog-specialist",
      agentVersion: 1,
      environment: "prod",
      channel: "telegram",
      useCase: "catalog-search",
      reason: "preflight",
      rolloutPercentage: 100,
      enabled: true,
      approvalReference: "approval-1",
      operationalApprovalReference: "ops-approval-1"
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/internal/agent-registry/activations/preflight",
      {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          Authorization: "Bearer session-token"
        },
        body: expect.any(String)
      }
    );
  });
});
