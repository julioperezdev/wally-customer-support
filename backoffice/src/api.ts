export type RunSummary = {
  runId: string;
  datasetVersion: string;
  agentId: string;
  agentVersion: string;
  provider: string;
  modelId: string;
  startedAt: string;
  completedAt: string;
  durationMs: number;
  totalScenarios: number;
  passedScenarios: number;
  failedScenarios: number;
  passRate: number;
  averageScore: number;
  failureReasons: Record<string, number>;
  totalTokens: number | null;
  providerLatencyMs: number | null;
  estimatedCostUsd: number | null;
};

export type RunPage = {
  items: RunSummary[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
};

export type ScenarioResult = {
  scenarioId: string;
  passed: boolean;
  score: number;
  reasons: string[];
  executionMetadata: {
    providerLatencyMs: number | null;
    inputTokens: number | null;
    outputTokens: number | null;
    totalTokens: number | null;
    estimatedCostUsd: number | null;
  } | null;
};

export type RunDetail = RunSummary & {
  suiteResult: {
    scenarioResults: ScenarioResult[];
  };
};

export type AgentRegistryVersion = {
  agentId: string;
  version: number;
  name: string;
  purpose: string;
  state: string;
  modelProvider: string;
  modelId: string;
  temperature: number;
  topP: number;
  systemPromptVersion: string;
  systemPromptHash: string;
  inputSchemaVersion: string;
  outputSchemaVersion: string;
  allowedTools: string[];
  knowledgeSources: string[];
  memoryPolicy: string;
  responsePolicy: string;
  timeoutMs: number;
  maxSteps: number;
  maxInputTokens: number;
  maxOutputTokens: number;
  budgetLimitUsd: number;
  fallbackAgentId: string | null;
  evaluationSuiteVersion: string;
  createdAt: string;
  approvedAt: string | null;
};

export type AgentRegistryActivation = {
  agentId: string;
  agentVersion: number;
  environment: string;
  channel: string;
  useCase: string;
  reason: string;
  rolloutPercentage: number;
  enabled: boolean;
  killSwitch: boolean;
  previousVersion: number | null;
  activatedAt: string;
};

export type AgentRegistryAgent = {
  agentId: string;
  versions: AgentRegistryVersion[];
  activations: AgentRegistryActivation[];
};

export type AgentRegistryMutation = {
  status: string;
  reason: string;
  agentId: string;
  version: number | null;
  state: string | null;
  createdAt: string | null;
  changedAt: string | null;
};

export type AgentVersionDraftInput = {
  version?: number | null;
  name: string;
  purpose: string;
  modelProvider: string;
  modelId: string;
  temperature: number;
  topP: number;
  systemPromptVersion: string;
  systemPromptHash: string;
  inputSchemaVersion: string;
  outputSchemaVersion: string;
  allowedTools: string[];
  knowledgeSources: string[];
  memoryPolicy: string;
  responsePolicy: string;
  timeoutMs: number;
  maxSteps: number;
  maxInputTokens: number;
  maxOutputTokens: number;
  budgetLimitUsd: number;
  fallbackAgentId?: string | null;
  evaluationSuiteVersion: string;
};

export type BackofficeAgentNode = {
  agentId: string;
  version: number;
  name: string;
  purpose: string;
  state: string;
  status: string;
  modelProvider: string;
  modelId: string;
  createdAt: string;
  ageDays: number;
  enabled: boolean;
  killSwitch: boolean;
  rolloutPercentage: number;
  executionCount: number;
  successCount: number;
  failureCount: number;
  successRate: number;
  averageLatencyMs: number | null;
  totalTokens: number | null;
  estimatedCostUsd: number | null;
  fallbackAgentId: string | null;
  allowedTools: string[];
  knowledgeSources: string[];
  metricsSource: string;
};

export type BackofficeAgentEdge = {
  source: string;
  relation: string;
  target: string;
};

export type BackofficeUseCaseMap = {
  environment: string;
  channel: string | null;
  useCase: string;
  agents: BackofficeAgentNode[];
  edges: BackofficeAgentEdge[];
};

export type BackofficeAgentMap = {
  generatedAt: string;
  filters: {
    environment: string;
    channel: string | null;
    useCase: string | null;
    agentId: string | null;
  };
  useCases: BackofficeUseCaseMap[];
  evidenceRunsScanned: number;
  evidenceTruncated: boolean;
};

export type BackofficeAgentMapSimulation = {
  environment: string;
  channel: string;
  useCase: string;
  disabledAgentId: string;
  disabledVersion: number | null;
  changed: boolean;
  outcome: "FALLBACK_AGENT" | "HUMAN_REQUIRED" | "NO_CHANGE" | string;
  reason: string;
  route: Array<{
    kind: string;
    agentId: string | null;
    version: number | null;
    reason: string;
  }>;
};

export type AgentActivationPreflightCheck = {
  code: string;
  status: "PASS" | "WARN" | "FAIL";
  message: string;
};

export type AgentActivationPreflight = {
  status: "READY" | "BLOCKED" | "DENIED";
  canActivate: boolean;
  agentId: string;
  agentVersion: number;
  environment: string;
  channel: string;
  useCase: string;
  checks: AgentActivationPreflightCheck[];
};

export type AgentActivationRequest = {
  agentId: string;
  agentVersion: number;
  environment: string;
  channel: string;
  useCase: string;
  reason: string;
  rolloutPercentage: number;
  enabled: boolean;
  approvalReference: string;
  operationalApprovalReference: string;
};

export type AgentActivationActionRequest = {
  agentId: string;
  environment: string;
  channel: string;
  useCase: string;
  approvalReference: string;
  operationalApprovalReference: string;
};

export type AgentActivationMutation = {
  status: "ACTIVATED" | "KILL_SWITCHED" | "ROLLED_BACK" | "ALREADY_PROCESSED" | "DENIED" | "DISABLED" | "INVALID" | "FAILED" | string;
  reason: string;
  agentId: string;
  agentVersion: number | null;
  environment: string;
  channel: string;
  useCase: string;
  activatedAt: string | null;
};

export type Comparison = {
  baselineRunId: string;
  candidateRunId: string;
  datasetVersion: string;
  baseline: RunSummary;
  candidate: RunSummary;
  metricDelta: {
    passedScenariosDelta: number;
    failedScenariosDelta: number;
    passRateDelta: number;
    averageScoreDelta: number;
    durationMsDelta: number;
    totalTokensDelta: number | null;
    providerLatencyMsDelta: number | null;
    estimatedCostUsdDelta: number | null;
  };
  scenarios: Array<{
    scenarioId: string;
    baselinePassed: boolean | null;
    candidatePassed: boolean | null;
    baselineScore: number | null;
    candidateScore: number | null;
    scoreDelta: number | null;
  }>;
};

export type BackofficeCatalogVariant = {
  id: string;
  sku: string;
  size: string;
  color: string;
  price: number;
  currency: string;
  stock: number;
  active: boolean;
};

export type BackofficeCatalogProduct = {
  id: string;
  name: string;
  description: string;
  productType: string | null;
  imageObjectKey: string | null;
  active: boolean;
  demo: boolean;
  variants: BackofficeCatalogVariant[];
};

export type BackofficeCatalogPage = {
  items: BackofficeCatalogProduct[];
  page: number;
  size: number;
  hasNext: boolean;
};

export type BackofficeHumanFollowUp = {
  id: string;
  conversationId: string;
  channel: string;
  reason: string;
  priority: string;
  status: string;
  dueAt: string;
  assignedTo: string | null;
  contextPreview: string[];
};

export type BackofficeStockAdjustment = {
  sku: string;
  previousStock: number;
  delta: number;
  newStock: number;
  reason: string;
  actorKey: string;
  idempotencyKey: string;
};

export type BackofficeOrderItem = {
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  currency: string;
  lineTotal: number;
};

export type BackofficeOrder = {
  id: string;
  customerReference: string | null;
  status: string;
  currency: string;
  total: number;
  paymentProvider: string;
  paymentPreferenceId: string | null;
  paymentUrl: string | null;
  externalPaymentId: string | null;
  createdAt: string;
  updatedAt: string;
  items: BackofficeOrderItem[];
};

export type BackofficeOrderPage = {
  items: BackofficeOrder[];
  page: number;
  size: number;
  hasNext: boolean;
};

export type FeatureFlagDefinition = {
  key: string;
  enabled: boolean;
  killSwitch: boolean;
  environments: string[];
  channels: string[];
  useCases: string[];
  agentIds: string[];
  agentVersions: number[];
};

export type FeatureFlagSnapshot = {
  environment: string;
  effectiveVersion: string;
  loadedAt: string;
  lastSuccessfulRefreshAt: string | null;
  stale: boolean;
  flags: FeatureFlagDefinition[];
  audit: Array<{
    operation: string;
    actor: string;
    version: string;
    timestamp: string;
    result: string;
    reason: string;
  }>;
};

export class ControlPlaneError extends Error {
  constructor(public readonly status: number, public readonly code: string) {
    super(`${code} (${status})`);
    this.name = "ControlPlaneError";
  }
}

export function createControlPlaneClient(
  baseUrl: string,
  token: string,
  registryBaseUrl = "/internal/agent-registry",
  agentMapBaseUrl = "/internal/backoffice/agent-map",
  featureFlagsBaseUrl = "/internal/backoffice/feature-flags"
) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");
  const normalizedRegistryBaseUrl = registryBaseUrl.replace(/\/+$/, "");
  const normalizedAgentMapBaseUrl = agentMapBaseUrl.replace(/\/+$/, "");
  const normalizedFeatureFlagsBaseUrl = featureFlagsBaseUrl.replace(/\/+$/, "");

  async function request<T>(path: string): Promise<T> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (token.trim()) {
      headers.Authorization = `Bearer ${token.trim()}`;
    }
    const response = await fetch(`${normalizedBaseUrl}${path}`, { headers });
    if (!response.ok) {
      let code = "CONTROL_PLANE_ERROR";
      try {
        code = ((await response.json()) as { code?: string }).code ?? code;
      } catch {
        // The status is enough when the server has no JSON error envelope.
      }
      throw new ControlPlaneError(response.status, code);
    }
    return (await response.json()) as T;
  }

  return {
    searchRuns(filters: { agentId?: string; provider?: string; page?: number; size?: number }) {
      const params = new URLSearchParams();
      if (filters.agentId?.trim()) params.set("agentId", filters.agentId.trim());
      if (filters.provider?.trim()) params.set("provider", filters.provider.trim());
      params.set("page", String(filters.page ?? 0));
      params.set("size", String(filters.size ?? 20));
      return request<RunPage>(`/runs?${params.toString()}`);
    },
    getRun(runId: string) {
      return request<RunDetail>(`/runs/${encodeURIComponent(runId)}`);
    },
    compare(baselineRunId: string, candidateRunId: string) {
      const params = new URLSearchParams({ baselineRunId, candidateRunId });
      return request<Comparison>(`/comparisons?${params.toString()}`);
    },
    listAgents(filters: {
      agentId?: string;
      environment?: string;
      channel?: string;
      useCase?: string;
      limit?: number;
    }) {
      const params = new URLSearchParams();
      if (filters.agentId?.trim()) params.set("agentId", filters.agentId.trim());
      if (filters.environment?.trim()) params.set("environment", filters.environment.trim());
      if (filters.channel?.trim()) params.set("channel", filters.channel.trim());
      if (filters.useCase?.trim()) params.set("useCase", filters.useCase.trim());
      params.set("limit", String(filters.limit ?? 50));
      return requestFrom<AgentRegistryAgent[]>(normalizedRegistryBaseUrl, `/agents?${params.toString()}`);
    },
    createAgentDraft(agentId: string, definition: AgentVersionDraftInput, idempotencyKey: string) {
      return requestFrom<AgentRegistryMutation>(
        normalizedRegistryBaseUrl,
        `/agents/${encodeURIComponent(agentId.trim())}/versions`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": idempotencyKey
          },
          body: JSON.stringify(definition)
        });
    },
    cloneAgentVersion(agentId: string, version: number, idempotencyKey: string) {
      return requestFrom<AgentRegistryMutation>(
        normalizedRegistryBaseUrl,
        `/agents/${encodeURIComponent(agentId.trim())}/versions/${version}/clone`,
        {
          method: "POST",
          headers: { "Idempotency-Key": idempotencyKey }
        });
    },
    transitionAgentVersion(
      agentId: string,
      version: number,
      request: {
        targetState: "CANDIDATE" | "EVALUATED" | "APPROVED";
        reason: string;
        approvalReference?: string;
        operationalApprovalReference?: string;
      },
      idempotencyKey: string) {
      return requestFrom<AgentRegistryMutation>(
        normalizedRegistryBaseUrl,
        `/agents/${encodeURIComponent(agentId.trim())}/versions/${version}/lifecycle`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": idempotencyKey
          },
          body: JSON.stringify(request)
        });
    },
    preflightActivation(request: AgentActivationRequest) {
      return requestFrom<AgentActivationPreflight>(
        normalizedRegistryBaseUrl,
        "/activations/preflight",
        {
          method: "POST",
          headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
            ...(token.trim() ? { Authorization: `Bearer ${token.trim()}` } : {})
          },
          body: JSON.stringify(request)
        },
        [HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.FORBIDDEN]);
    },
    activateAgent(request: AgentActivationRequest, idempotencyKey: string) {
      return requestFrom<AgentActivationMutation>(normalizedRegistryBaseUrl, "/activations", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey
        },
        body: JSON.stringify(request)
      });
    },
    killSwitchAgent(request: AgentActivationActionRequest, idempotencyKey: string) {
      return requestFrom<AgentActivationMutation>(normalizedRegistryBaseUrl, "/activations/kill-switch", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey
        },
        body: JSON.stringify(request)
      });
    },
    rollbackAgent(request: AgentActivationActionRequest, idempotencyKey: string) {
      return requestFrom<AgentActivationMutation>(normalizedRegistryBaseUrl, "/activations/rollback", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey
        },
        body: JSON.stringify(request)
      });
    },
    getAgentMap(filters: {
      environment?: string;
      channel?: string;
      useCase?: string;
      agentId?: string;
    } = {}) {
      const params = new URLSearchParams();
      params.set("environment", filters.environment?.trim() || "prod");
      if (filters.channel?.trim()) params.set("channel", filters.channel.trim());
      if (filters.useCase?.trim()) params.set("useCase", filters.useCase.trim());
      if (filters.agentId?.trim()) params.set("agentId", filters.agentId.trim());
      return requestFrom<BackofficeAgentMap>(
        normalizedAgentMapBaseUrl,
        `?${params.toString()}`);
    },
    simulateAgentMap(request: {
      environment: string;
      channel: string;
      useCase: string;
      disabledAgentId: string;
      disabledVersion?: number | null;
    }) {
      return requestFrom<BackofficeAgentMapSimulation>(
        normalizedAgentMapBaseUrl,
        "/simulations",
        {
          method: "POST",
          headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
            ...(token.trim() ? { Authorization: `Bearer ${token.trim()}` } : {})
          },
          body: JSON.stringify(request)
      });
    },
    getFeatureFlags() {
      return requestFrom<FeatureFlagSnapshot>(normalizedFeatureFlagsBaseUrl, "");
    },
    publishFeatureFlags(document: {
      schemaVersion: string;
      version: string;
      flags: FeatureFlagDefinition[];
    }) {
      return requestFrom<FeatureFlagSnapshot>(normalizedFeatureFlagsBaseUrl, "/publish", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(document)
      });
    },
    rollbackFeatureFlags() {
      return requestFrom<FeatureFlagSnapshot>(normalizedFeatureFlagsBaseUrl, "/rollback", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: "{}"
      });
    }
  };

  async function requestFrom<T>(
    root: string,
    path: string,
    init?: RequestInit,
    acceptedStatuses: number[] = []
  ): Promise<T> {
    const headers: Record<string, string> = {
      Accept: "application/json",
      ...(init?.headers as Record<string, string> | undefined)
    };
    if (token.trim()) {
      headers.Authorization = `Bearer ${token.trim()}`;
    }
    const response = await fetch(`${root}${path}`, { ...init, headers });
    if (!response.ok && !acceptedStatuses.includes(response.status)) {
      let code = "CONTROL_PLANE_ERROR";
      try {
        code = ((await response.json()) as { code?: string }).code ?? code;
      } catch {
        // The status is enough when the server has no JSON error envelope.
      }
      throw new ControlPlaneError(response.status, code);
    }
    return (await response.json()) as T;
  }
}

const HttpStatus = {
  FORBIDDEN: 403,
  UNPROCESSABLE_ENTITY: 422
} as const;

export function createBackofficeClient(baseUrl: string, token: string) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");

  async function request<T>(path: string): Promise<T> {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (token.trim()) {
      headers.Authorization = `Bearer ${token.trim()}`;
    }
    const response = await fetch(`${normalizedBaseUrl}${path}`, { headers });
    if (!response.ok) {
      let code = "BACKOFFICE_ERROR";
      try {
        code = ((await response.json()) as { code?: string }).code ?? code;
      } catch {
        // The status is enough when the server has no JSON error envelope.
      }
      throw new ControlPlaneError(response.status, code);
    }
    return (await response.json()) as T;
  }

  return {
    searchCatalog(filters: {
      name?: string;
      sku?: string;
      size?: string;
      color?: string;
      productType?: string;
      page?: number;
      limit?: number;
    }) {
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(filters)) {
        if (value !== undefined && String(value).trim()) params.set(key, String(value).trim());
      }
      return request<BackofficeCatalogPage>(`/catalog?${params.toString()}`);
    },
    listHumanFollowUps(limit = 50) {
      return request<BackofficeHumanFollowUp[]>(`/human-follow-ups?limit=${encodeURIComponent(String(limit))}`);
    },
    changeHumanFollowUp(id: string, operation: "claim" | "release" | "resolve", actor: string) {
      return requestFrom<BackofficeHumanFollowUp>(
        normalizedBaseUrl,
        `/human-follow-ups/${encodeURIComponent(id)}/${operation}`,
        {
          method: "POST",
          headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
            "X-WCS-Actor-Key": actor
          }
        });
    },
    adjustStock(sku: string, delta: number, reason: string, actorKey: string) {
      return requestFrom<BackofficeStockAdjustment>(
        normalizedBaseUrl,
        `/catalog/variants/${encodeURIComponent(sku)}/stock`,
        {
          method: "POST",
          headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
            "X-WCS-Actor-Key": actorKey,
            "Idempotency-Key": crypto.randomUUID()
          },
          body: JSON.stringify({ delta, reason })
        });
    },
    listOrders(status = "", page = 0, limit = 20) {
      const params = new URLSearchParams({
        page: String(page),
        limit: String(limit)
      });
      if (status.trim()) params.set("status", status.trim());
      return request<BackofficeOrderPage>(`/orders?${params.toString()}`);
    },
    createOrder(
      body: { customerReference?: string; items: Array<{ sku: string; quantity: number }> },
      idempotencyKey: string = crypto.randomUUID()) {
      return requestFrom<BackofficeOrder>(normalizedBaseUrl, "/orders", {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey
        },
        body: JSON.stringify(body)
      });
    }
  };

  async function requestFrom<T>(root: string, path: string, init: RequestInit): Promise<T> {
    const headers: Record<string, string> = {
      Accept: "application/json",
      ...(init.headers as Record<string, string> | undefined)
    };
    if (token.trim()) headers.Authorization = `Bearer ${token.trim()}`;
    const response = await fetch(`${root}${path}`, { ...init, headers });
    if (!response.ok) {
      let code = "BACKOFFICE_ERROR";
      try {
        code = ((await response.json()) as { code?: string }).code ?? code;
      } catch {
        // The status is enough when the server has no JSON error envelope.
      }
      throw new ControlPlaneError(response.status, code);
    }
    return (await response.json()) as T;
  }
}
