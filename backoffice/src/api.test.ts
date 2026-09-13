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

  it("sends protected authoring and lifecycle commands with idempotency keys", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: "CREATED", agentId: "support", version: 2, state: "DRAFT" }), { status: 201 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: "TRANSITIONED", agentId: "support", version: 2, state: "CANDIDATE" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const client = createControlPlaneClient("/internal/agent-evaluations", "session-token");
    await client.createAgentDraft("support specialist", {
      version: null,
      name: "Support",
      purpose: "Grounded support",
      modelProvider: "bedrock",
      modelId: "model-1",
      temperature: 0,
      topP: 1,
      systemPromptVersion: "system-v1",
      systemPromptHash: "0".repeat(64),
      inputSchemaVersion: "input-v1",
      outputSchemaVersion: "output-v1",
      allowedTools: [],
      knowledgeSources: [],
      memoryPolicy: "none",
      responsePolicy: "grounded-v1",
      timeoutMs: 1000,
      maxSteps: 1,
      maxInputTokens: 100,
      maxOutputTokens: 100,
      budgetLimitUsd: 0.01,
      evaluationSuiteVersion: "eval-v1"
    }, "key-create");
    await client.transitionAgentVersion("support specialist", 2, {
      targetState: "CANDIDATE",
      reason: "ready for evaluation"
    }, "key-transition");

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/internal/agent-registry/agents/support%20specialist/versions",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Authorization: "Bearer session-token",
          "Idempotency-Key": "key-create"
        })
      })
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/internal/agent-registry/agents/support%20specialist/versions/2/lifecycle",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "Idempotency-Key": "key-transition" })
      })
    );
  });

  it("keeps blocked preflight details instead of hiding the validation response", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: "BLOCKED",
      canActivate: false,
      agentId: "catalog-specialist",
      agentVersion: 2,
      environment: "prod",
      channel: "telegram",
      useCase: "catalog-search",
      checks: [{ code: "VERSION_NOT_APPROVED", status: "FAIL", message: "La versión no está aprobada." }]
    }), { status: 422 }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await createControlPlaneClient("/internal/agent-evaluations", "session-token").preflightActivation({
      agentId: "catalog-specialist",
      agentVersion: 2,
      environment: "prod",
      channel: "telegram",
      useCase: "catalog-search",
      reason: "preflight",
      rolloutPercentage: 100,
      enabled: true,
      approvalReference: "approval-1",
      operationalApprovalReference: "ops-approval-1"
    });

    expect(result.status).toBe("BLOCKED");
    expect(result.checks[0].code).toBe("VERSION_NOT_APPROVED");
  });

  it("sends protected activation mutations with an idempotency key", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: "ACTIVATED" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: "KILL_SWITCHED" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: "ROLLED_BACK" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const client = createControlPlaneClient("/internal/agent-evaluations", "session-token");
    const activation = {
      agentId: "catalog-specialist",
      agentVersion: 2,
      environment: "prod",
      channel: "telegram",
      useCase: "catalog-search",
      reason: "approved rollout",
      rolloutPercentage: 25,
      enabled: true,
      approvalReference: "approval-1",
      operationalApprovalReference: "ops-approval-1"
    };
    const action = {
      agentId: activation.agentId,
      environment: activation.environment,
      channel: activation.channel,
      useCase: activation.useCase,
      approvalReference: activation.approvalReference,
      operationalApprovalReference: activation.operationalApprovalReference
    };

    await client.activateAgent(activation, "idem-activate");
    await client.killSwitchAgent(action, "idem-kill");
    await client.rollbackAgent(action, "idem-rollback");

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/internal/agent-registry/activations", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({
        Authorization: "Bearer session-token",
        "Idempotency-Key": "idem-activate",
        "Content-Type": "application/json"
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/internal/agent-registry/activations/kill-switch", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({ "Idempotency-Key": "idem-kill" })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/internal/agent-registry/activations/rollback", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({ "Idempotency-Key": "idem-rollback" })
    }));
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

  it("lists orders and sends an idempotency key for assisted sales", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ items: [], page: 1, size: 10, hasNext: false }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "order-1", status: "PENDING_PAYMENT" }), { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);

    const client = createBackofficeClient("/internal/backoffice", "session-token");
    await client.listOrders("PAID", 1, 10);
    await client.createOrder({ items: [{ sku: "SKU-1", quantity: 1 }] }, "order-request-1");

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/internal/backoffice/orders?page=1&limit=10&status=PAID",
      { headers: { Accept: "application/json", Authorization: "Bearer session-token" } }
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/internal/backoffice/orders",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Authorization: "Bearer session-token",
          "Idempotency-Key": "order-request-1"
        })
      })
    );
  });
});
