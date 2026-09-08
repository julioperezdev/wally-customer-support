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

export class ControlPlaneError extends Error {
  constructor(public readonly status: number, public readonly code: string) {
    super(`${code} (${status})`);
    this.name = "ControlPlaneError";
  }
}

export function createControlPlaneClient(baseUrl: string, token: string) {
  const normalizedBaseUrl = baseUrl.replace(/\/+$/, "");

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
    }
  };
}
