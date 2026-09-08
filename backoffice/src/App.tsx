import { useEffect, useMemo, useState } from "react";
import {
  AgentRegistryAgent,
  Comparison,
  ControlPlaneError,
  RunDetail,
  RunPage,
  RunSummary,
  createControlPlaneClient
} from "./api";

const DEFAULT_BASE_URL = import.meta.env.VITE_WCS_CONTROL_PLANE_BASE_URL ?? "/internal/agent-evaluations";
const DEFAULT_REGISTRY_BASE_URL = import.meta.env.VITE_WCS_AGENT_REGISTRY_BASE_URL ?? "/internal/agent-registry";

export function App() {
  const [baseUrl, setBaseUrl] = useState(DEFAULT_BASE_URL);
  const [token, setToken] = useState("");
  const [agentId, setAgentId] = useState("");
  const [provider, setProvider] = useState("");
  const [page, setPage] = useState<RunPage | null>(null);
  const [selectedRun, setSelectedRun] = useState<RunDetail | null>(null);
  const [comparison, setComparison] = useState<Comparison | null>(null);
  const [baselineId, setBaselineId] = useState("");
  const [candidateId, setCandidateId] = useState("");
  const [registryAgents, setRegistryAgents] = useState<AgentRegistryAgent[] | null>(null);
  const [registryAgentId, setRegistryAgentId] = useState("");
  const [registryEnvironment, setRegistryEnvironment] = useState("");
  const [registryChannel, setRegistryChannel] = useState("");
  const [registryUseCase, setRegistryUseCase] = useState("");
  const [busy, setBusy] = useState(false);
  const [registryBusy, setRegistryBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [registryError, setRegistryError] = useState<string | null>(null);

  const client = useMemo(() => createControlPlaneClient(baseUrl, token, DEFAULT_REGISTRY_BASE_URL), [baseUrl, token]);

  async function loadRuns(nextPage = 0) {
    setBusy(true);
    setError(null);
    try {
      const result = await client.searchRuns({ agentId, provider, page: nextPage });
      setPage(result);
      if (!baselineId && result.items[0]) setBaselineId(result.items[0].runId);
      if (!candidateId && result.items[1]) setCandidateId(result.items[1].runId);
    } catch (cause) {
      setError(toUserMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function openRun(runId: string) {
    setBusy(true);
    setError(null);
    try {
      setSelectedRun(await client.getRun(runId));
    } catch (cause) {
      setError(toUserMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function compareRuns() {
    if (!baselineId || !candidateId || baselineId === candidateId) {
      setError("Elegí dos runs diferentes para comparar.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      setComparison(await client.compare(baselineId, candidateId));
    } catch (cause) {
      setError(toUserMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function loadRegistry() {
    setRegistryBusy(true);
    setRegistryError(null);
    try {
      setRegistryAgents(await client.listAgents({
        agentId: registryAgentId,
        environment: registryEnvironment,
        channel: registryChannel,
        useCase: registryUseCase,
        limit: 50
      }));
    } catch (cause) {
      setRegistryError(toUserMessage(cause));
    } finally {
      setRegistryBusy(false);
    }
  }

  useEffect(() => {
    void loadRuns();
    void loadRegistry();
    // The first load is intentionally tied to the initial client only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <main className="shell">
      <header className="hero">
        <div>
          <p className="eyebrow">WALLY CUSTOMER SUPPORT · CONTROL PLANE</p>
          <h1>Evaluaciones de agentes</h1>
          <p className="lead">Consulta read-only de evidencia sanitizada. Esta pantalla no publica, activa ni ejecuta agentes.</p>
        </div>
        <span className="status-pill">READ ONLY</span>
      </header>

      <section className="card connection-card">
        <div className="section-heading">
          <div>
            <h2>Conexión</h2>
            <p>El control plane permanece cerrado si el backend no tiene JWT habilitado.</p>
          </div>
          <span className="security-note">Sin secretos en el build</span>
        </div>
        <div className="form-grid">
          <label>API base URL<input value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} /></label>
          <label>Token de sesión (memoria)<input type="password" value={token} onChange={(event) => setToken(event.target.value)} autoComplete="off" /></label>
        </div>
        <div className="button-row"><button className="primary" onClick={() => void loadRuns()} disabled={busy}>Actualizar runs</button><button onClick={() => void loadRegistry()} disabled={registryBusy}>Actualizar registry</button></div>
      </section>

      {error && <div className="alert" role="alert">{error}</div>}
      {registryError && <div className="alert" role="alert">Registry: {registryError}</div>}

      <section className="card">
        <div className="section-heading">
          <div><h2>Agentes y activaciones</h2><p>{registryAgents ? `${registryAgents.length} agentes sanitizados` : "Sin datos cargados"}</p></div>
          <span className="security-note">READ ONLY</span>
        </div>
        <div className="filters registry-filters">
          <input aria-label="Filtrar registry por agente" placeholder="agentId" value={registryAgentId} onChange={(event) => setRegistryAgentId(event.target.value)} />
          <input aria-label="Filtrar registry por ambiente" placeholder="environment" value={registryEnvironment} onChange={(event) => setRegistryEnvironment(event.target.value)} />
          <input aria-label="Filtrar registry por canal" placeholder="channel" value={registryChannel} onChange={(event) => setRegistryChannel(event.target.value)} />
          <input aria-label="Filtrar registry por caso de uso" placeholder="useCase" value={registryUseCase} onChange={(event) => setRegistryUseCase(event.target.value)} />
          <button onClick={() => void loadRegistry()} disabled={registryBusy}>Filtrar</button>
        </div>
        <AgentRegistryView agents={registryAgents} />
      </section>

      <section className="card">
        <div className="section-heading">
          <div><h2>Runs</h2><p>{page ? `${page.totalElements} resultados sanitizados` : "Sin datos cargados"}</p></div>
          <div className="filters">
            <input aria-label="Filtrar por agente" placeholder="agentId" value={agentId} onChange={(event) => setAgentId(event.target.value)} />
            <input aria-label="Filtrar por proveedor" placeholder="provider" value={provider} onChange={(event) => setProvider(event.target.value)} />
            <button onClick={() => void loadRuns()} disabled={busy}>Filtrar</button>
          </div>
        </div>
        <RunTable page={page} onOpen={openRun} />
        {page && <div className="pagination"><button onClick={() => void loadRuns(page.pageNumber - 1)} disabled={busy || page.pageNumber === 0}>Anterior</button><span>Página {page.pageNumber + 1} de {Math.max(page.totalPages, 1)}</span><button onClick={() => void loadRuns(page.pageNumber + 1)} disabled={busy || page.pageNumber + 1 >= page.totalPages}>Siguiente</button></div>}
      </section>

      <section className="split-grid">
        <section className="card">
          <div className="section-heading"><div><h2>Comparar</h2><p>La comparación no decide promociones.</p></div></div>
          <div className="form-grid">
            <label>Baseline<select value={baselineId} onChange={(event) => setBaselineId(event.target.value)}><option value="">Seleccionar</option>{page?.items.map((run) => <option key={run.runId} value={run.runId}>{shortId(run.runId)} · {run.agentVersion}</option>)}</select></label>
            <label>Candidate<select value={candidateId} onChange={(event) => setCandidateId(event.target.value)}><option value="">Seleccionar</option>{page?.items.map((run) => <option key={run.runId} value={run.runId}>{shortId(run.runId)} · {run.agentVersion}</option>)}</select></label>
          </div>
          <button className="primary" onClick={() => void compareRuns()} disabled={busy}>Comparar runs</button>
          {comparison && <ComparisonView comparison={comparison} />}
        </section>
        <section className="card">
          <h2>Detalle</h2>
          {selectedRun ? <RunDetailView run={selectedRun} /> : <p className="muted">Seleccioná un run para ver sus métricas por escenario.</p>}
        </section>
      </section>
    </main>
  );
}

function RunTable({ page, onOpen }: { page: RunPage | null; onOpen: (id: string) => void }) {
  if (!page) return <p className="muted">El control plane no está disponible todavía.</p>;
  if (page.items.length === 0) return <p className="muted">No hay runs para los filtros seleccionados.</p>;
  return <div className="table-wrap"><table><thead><tr><th>Agente</th><th>Modelo</th><th>Resultado</th><th>Latencia</th><th>Tokens</th><th>Costo USD</th><th /></tr></thead><tbody>{page.items.map((run) => <tr key={run.runId}><td><strong>{run.agentId}</strong><small>{run.agentVersion} · {run.datasetVersion}</small></td><td>{run.provider}<small>{run.modelId}</small></td><td><span className={run.failedScenarios ? "negative" : "positive"}>{formatPercent(run.passRate)}</span><small>{run.passedScenarios}/{run.totalScenarios} escenarios</small></td><td>{formatMs(run.durationMs)}<small>provider: {formatMs(run.providerLatencyMs)}</small></td><td>{run.totalTokens ?? "—"}</td><td>{run.estimatedCostUsd == null ? "—" : run.estimatedCostUsd.toFixed(6)}</td><td><button className="link-button" onClick={() => void onOpen(run.runId)}>Ver</button></td></tr>)}</tbody></table></div>;
}

function AgentRegistryView({ agents }: { agents: AgentRegistryAgent[] | null }) {
  if (!agents) return <p className="muted">El registry no está disponible todavía.</p>;
  if (agents.length === 0) return <p className="muted">No hay agentes para los filtros seleccionados.</p>;
  return <div className="registry-list">{agents.map((agent) => <article className="registry-agent" key={agent.agentId}>
    <div className="section-heading"><div><h3>{agent.agentId}</h3><p className="muted">{agent.versions.length} versiones · {agent.activations.length} activaciones</p></div></div>
    <div className="table-wrap"><table><thead><tr><th>Versión</th><th>Lifecycle</th><th>Modelo</th><th>Prompt</th><th>Límites</th></tr></thead><tbody>{agent.versions.map((version) => <tr key={`${version.agentId}-${version.version}`}><td><strong>v{version.version}</strong><small>{version.name}</small></td><td>{version.state}<small>{version.evaluationSuiteVersion}</small></td><td>{version.modelProvider}<small>{version.modelId}</small></td><td>{version.systemPromptVersion}<small>SHA-256: {version.systemPromptHash.slice(0, 12)}…</small></td><td>{version.maxSteps} steps<small>{version.maxInputTokens}/{version.maxOutputTokens} tokens</small></td></tr>)}</tbody></table></div>
    <h4>Activaciones</h4>
    {agent.activations.length === 0 ? <p className="muted">Sin activaciones registradas.</p> : <div className="activation-list">{agent.activations.map((activation, index) => <div className="activation" key={`${activation.activatedAt}-${index}`}><span><strong>{activation.environment}</strong> · {activation.channel} · {activation.useCase}</span><span>{activation.enabled && !activation.killSwitch ? `${activation.rolloutPercentage}% activa` : "kill switch / inactiva"}<small>{activation.reason}</small></span></div>)}</div>}
  </article>)}</div>;
}

function RunDetailView({ run }: { run: RunDetail }) {
  return <div className="detail"><p><strong>{run.agentId}</strong> · {run.agentVersion}</p><p className="muted">{run.provider} / {run.modelId} · {run.datasetVersion}</p><div className="metric-row"><Metric label="Pass rate" value={formatPercent(run.passRate)} /><Metric label="Tokens" value={run.totalTokens == null ? "—" : String(run.totalTokens)} /><Metric label="Costo USD" value={run.estimatedCostUsd == null ? "—" : run.estimatedCostUsd.toFixed(6)} /></div><h3>Escenarios</h3>{run.suiteResult.scenarioResults.map((scenario) => <div className="scenario" key={scenario.scenarioId}><span>{scenario.scenarioId}</span><span className={scenario.passed ? "positive" : "negative"}>{scenario.passed ? "PASS" : "FAIL"} · {formatPercent(scenario.score)}</span></div>)}</div>;
}

function ComparisonView({ comparison }: { comparison: Comparison }) {
  const delta = comparison.metricDelta;
  return <div className="comparison"><h3>Delta candidate − baseline</h3><div className="metric-row"><Metric label="Pass rate" value={formatSignedPercent(delta.passRateDelta)} /><Metric label="Latencia" value={formatSignedMs(delta.durationMsDelta)} /><Metric label="Tokens" value={delta.totalTokensDelta == null ? "—" : formatSigned(delta.totalTokensDelta)} /><Metric label="Costo USD" value={delta.estimatedCostUsdDelta == null ? "—" : formatSigned(delta.estimatedCostUsdDelta)} /></div><p className="muted">Dataset: {comparison.datasetVersion}. La evidencia es informativa y requiere aprobación separada para cualquier promoción.</p></div>;
}

function Metric({ label, value }: { label: string; value: string }) { return <div className="metric"><span>{label}</span><strong>{value}</strong></div>; }
function shortId(value: string) { return value.slice(0, 8); }
function formatPercent(value: number) { return `${(value * 100).toFixed(1)}%`; }
function formatSignedPercent(value: number) { return `${value >= 0 ? "+" : ""}${(value * 100).toFixed(1)} pp`; }
function formatMs(value: number | null) { return value == null ? "—" : `${value} ms`; }
function formatSigned(value: number) { return `${value >= 0 ? "+" : ""}${value}`; }
function formatSignedMs(value: number) { return formatSigned(value) + " ms"; }
function toUserMessage(cause: unknown) {
  if (cause instanceof ControlPlaneError) {
    if (cause.status === 401) return "La sesión no es válida. El control plane requiere un JWT válido.";
    if (cause.status === 403) return "Acceso denegado o control plane cerrado por configuración.";
    if (cause.status === 404) return "El recurso de evaluación no existe.";
    if (cause.status === 409) return "Los runs no son comparables: verificá que usen el mismo dataset.";
    return `El control plane respondió ${cause.code}.`;
  }
  return "No se pudo conectar con el control plane. Revisá la URL y el estado del backend.";
}
