import { afterEach, describe, expect, it, vi } from "vitest";
import { ControlPlaneError, createBackofficeClient, createControlPlaneClient } from "./api";

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

  it("loads the agent map and simulates a route without mutating activations", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ useCases: [], evidenceRunsScanned: 0 }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ outcome: "HUMAN_REQUIRED", route: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const client = createControlPlaneClient("/internal/agent-evaluations", "session-token");
    await client.getAgentMap({ environment: "prod", channel: "telegram", useCase: "catalog-search" });
    await client.simulateAgentMap({
      environment: "prod",
      channel: "telegram",
      useCase: "catalog-search",
      disabledAgentId: "catalog-specialist",
      disabledVersion: 1
    });

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/internal/backoffice/agent-map?environment=prod&channel=telegram&useCase=catalog-search",
      { headers: { Accept: "application/json", Authorization: "Bearer session-token" } }
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/internal/backoffice/agent-map/simulations",
      expect.objectContaining({ method: "POST", body: expect.stringContaining("catalog-specialist") })
    );
  });

  it("reads and publishes the separate business feature flag profile", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ effectiveVersion: "v1", flags: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ effectiveVersion: "v2", flags: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ effectiveVersion: "v1", flags: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = createControlPlaneClient("/internal/agent-evaluations", "session-token");
    await client.getFeatureFlags();
    await client.publishFeatureFlags({ schemaVersion: "1", version: "v2", flags: [] });
    await client.rollbackFeatureFlags();

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/internal/backoffice/feature-flags", expect.anything());
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/internal/backoffice/feature-flags/publish", expect.objectContaining({ method: "POST" }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/internal/backoffice/feature-flags/rollback", expect.objectContaining({ method: "POST" }));
  });
});

describe("backoffice client", () => {
  afterEach(() => vi.restoreAllMocks());

  it("loads catalog filters and human follow-ups with the session token", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ items: [], page: 0, size: 20, hasNext: false }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify([]), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const client = createBackofficeClient("/internal/backoffice/", "session-token");
    await client.searchCatalog({ name: "buzo", color: "negro", page: 0, limit: 20 });
    await client.listHumanFollowUps(10);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/internal/backoffice/catalog?name=buzo&color=negro&page=0&limit=20",
      { headers: { Accept: "application/json", Authorization: "Bearer session-token" } }
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/internal/backoffice/human-follow-ups?limit=10",
      { headers: { Accept: "application/json", Authorization: "Bearer session-token" } }
    );
  });

  it("sends stock and ownership mutations with their safety headers", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: "IN_PROGRESS" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ sku: "SKU-1", newStock: 4 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const client = createBackofficeClient("/internal/backoffice", "session-token");
    await client.changeHumanFollowUp("task-1", "claim", "operator-1");
    await client.adjustStock("SKU-1", 2, "recepción", "operator-1");

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/internal/backoffice/human-follow-ups/task-1/claim",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "X-WCS-Actor-Key": "operator-1" })
      })
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/internal/backoffice/catalog/variants/SKU-1/stock",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          "X-WCS-Actor-Key": "operator-1",
          "Idempotency-Key": expect.any(String)
        })
      })
    );
  });
});
