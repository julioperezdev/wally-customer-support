import { useEffect, useMemo, useState } from "react";
import {
  BackofficeAgentMap,
  BackofficeAgentMapSimulation,
  AgentRegistryAgent,
  AgentActivationPreflight,
  BackofficeCatalogProduct,
  BackofficeCatalogPage,
  BackofficeHumanFollowUp,
  FeatureFlagSnapshot,
  Comparison,
  ControlPlaneError,
  RunDetail,
  RunPage,
  RunSummary,
  createBackofficeClient,
  createControlPlaneClient
} from "./api";

const DEFAULT_BASE_URL = import.meta.env.VITE_WCS_CONTROL_PLANE_BASE_URL ?? "/internal/agent-evaluations";
const DEFAULT_REGISTRY_BASE_URL = import.meta.env.VITE_WCS_AGENT_REGISTRY_BASE_URL ?? "/internal/agent-registry";
const DEFAULT_BACKOFFICE_BASE_URL = import.meta.env.VITE_WCS_BACKOFFICE_BASE_URL ?? "/internal/backoffice";
const DEFAULT_AGENT_MAP_BASE_URL = import.meta.env.VITE_WCS_AGENT_MAP_BASE_URL ?? "/internal/backoffice/agent-map";

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
  const [agentMap, setAgentMap] = useState<BackofficeAgentMap | null>(null);
  const [agentMapError, setAgentMapError] = useState<string | null>(null);
  const [agentMapBusy, setAgentMapBusy] = useState(false);
  const [mapEnvironment, setMapEnvironment] = useState("prod");
  const [mapChannel, setMapChannel] = useState("telegram");
  const [mapUseCase, setMapUseCase] = useState("");
  const [mapAgentId, setMapAgentId] = useState("");
  const [simulation, setSimulation] = useState<BackofficeAgentMapSimulation | null>(null);
  const [simulationAgentId, setSimulationAgentId] = useState("");
  const [simulationVersion, setSimulationVersion] = useState("");
  const [preflight, setPreflight] = useState<AgentActivationPreflight | null>(null);
  const [preflightBusy, setPreflightBusy] = useState(false);
  const [preflightError, setPreflightError] = useState<string | null>(null);
  const [preflightAgentId, setPreflightAgentId] = useState("catalog-specialist");
  const [preflightVersion, setPreflightVersion] = useState("1");
  const [preflightEnvironment, setPreflightEnvironment] = useState("prod");
  const [preflightChannel, setPreflightChannel] = useState("telegram");
  const [preflightUseCase, setPreflightUseCase] = useState("catalog-search");
  const [preflightReason, setPreflightReason] = useState("controlled preflight");
  const [preflightApproval, setPreflightApproval] = useState("");
  const [preflightOperationalApproval, setPreflightOperationalApproval] = useState("");
  const [preflightRollout, setPreflightRollout] = useState("100");
  const [catalogPage, setCatalogPage] = useState<BackofficeCatalogPage | null>(null);
  const [catalogName, setCatalogName] = useState("");
  const [catalogType, setCatalogType] = useState("");
  const [catalogColor, setCatalogColor] = useState("");
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [followUps, setFollowUps] = useState<BackofficeHumanFollowUp[] | null>(null);
  const [followUpError, setFollowUpError] = useState<string | null>(null);
  const [storeBusy, setStoreBusy] = useState(false);
  const [storeActor, setStoreActor] = useState("local-operator");
  const [featureFlags, setFeatureFlags] = useState<FeatureFlagSnapshot | null>(null);
  const [featureFlagsError, setFeatureFlagsError] = useState<string | null>(null);
  const [featureFlagsBusy, setFeatureFlagsBusy] = useState(false);
  const [featureFlagsJson, setFeatureFlagsJson] = useState(`{
  "schemaVersion": "1",
  "version": "backoffice-${new Date().toISOString().slice(0, 10)}",
  "flags": []
}`);

  const client = useMemo(() => createControlPlaneClient(baseUrl, token, DEFAULT_REGISTRY_BASE_URL, DEFAULT_AGENT_MAP_BASE_URL), [baseUrl, token]);
  const backofficeClient = useMemo(() => createBackofficeClient(DEFAULT_BACKOFFICE_BASE_URL, token), [token]);

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

  async function runPreflight() {
    const version = Number.parseInt(preflightVersion, 10);
    const rollout = Number.parseInt(preflightRollout, 10);
    if (!Number.isInteger(version) || !Number.isInteger(rollout)) {
      setPreflightError("La versión y el rollout deben ser números.");
      return;
    }
    setPreflightBusy(true);
    setPreflightError(null);
    try {
      setPreflight(await client.preflightActivation({
        agentId: preflightAgentId,
        agentVersion: version,
        environment: preflightEnvironment,
        channel: preflightChannel,
        useCase: preflightUseCase,
        reason: preflightReason,
        rolloutPercentage: rollout,
        enabled: true,
        approvalReference: preflightApproval,
        operationalApprovalReference: preflightOperationalApproval
      }));
    } catch (cause) {
      setPreflightError(toUserMessage(cause));
    } finally {
      setPreflightBusy(false);
    }
  }

  async function loadAgentMap() {
    setAgentMapBusy(true);
    setAgentMapError(null);
    try {
      const result = await client.getAgentMap({
        environment: mapEnvironment,
        channel: mapChannel,
        useCase: mapUseCase,
        agentId: mapAgentId
      });
      setAgentMap(result);
      const firstAgent = result.useCases[0]?.agents[0];
      if (firstAgent && !simulationAgentId) setSimulationAgentId(firstAgent.agentId);
      if (firstAgent && !simulationVersion) setSimulationVersion(String(firstAgent.version));
    } catch (cause) {
      setAgentMapError(toUserMessage(cause));
    } finally {
      setAgentMapBusy(false);
    }
  }

  async function simulateAgentMap() {
    if (!mapUseCase.trim() || !simulationAgentId.trim()) {
      setAgentMapError("Para simular indicá el caso de uso y el agentId.");
      return;
    }
    const version = simulationVersion.trim() ? Number.parseInt(simulationVersion, 10) : null;
    if (simulationVersion.trim() && !Number.isInteger(version)) {
      setAgentMapError("La versión a simular debe ser un número.");
      return;
    }
    setAgentMapBusy(true);
    setAgentMapError(null);
    try {
      setSimulation(await client.simulateAgentMap({
        environment: mapEnvironment,
        channel: mapChannel,
        useCase: mapUseCase,
        disabledAgentId: simulationAgentId,
        disabledVersion: version
      }));
    } catch (cause) {
      setAgentMapError(toUserMessage(cause));
    } finally {
      setAgentMapBusy(false);
    }
  }

  async function loadStore() {
    setStoreBusy(true);
    setCatalogError(null);
    setFollowUpError(null);
    try {
      const [catalog, humanFollowUps] = await Promise.all([
        backofficeClient.searchCatalog({
          name: catalogName,
          productType: catalogType,
          color: catalogColor,
          page: 0,
          limit: 20
        }),
        backofficeClient.listHumanFollowUps(50)
      ]);
      setCatalogPage(catalog);
      setFollowUps(humanFollowUps);
    } catch (cause) {
      const message = toUserMessage(cause);
      setCatalogError(message);
      setFollowUpError(message);
    } finally {
      setStoreBusy(false);
    }
  }

  async function adjustStock(sku: string, delta: number) {
    if (!Number.isInteger(delta) || delta === 0) return;
    setStoreBusy(true);
    try {
      await backofficeClient.adjustStock(sku, delta, "backoffice MVP", storeActor);
      await loadStore();
    } catch (cause) {
      setCatalogError(toUserMessage(cause));
    } finally {
      setStoreBusy(false);
    }
  }

  async function changeFollowUp(id: string, operation: "claim" | "release" | "resolve") {
    setStoreBusy(true);
    try {
      await backofficeClient.changeHumanFollowUp(id, operation, storeActor);
      await loadStore();
    } catch (cause) {
      setFollowUpError(toUserMessage(cause));
    } finally {
      setStoreBusy(false);
    }
  }

  async function loadFeatureFlags() {
    setFeatureFlagsBusy(true);
    setFeatureFlagsError(null);
    try {
      setFeatureFlags(await client.getFeatureFlags());
    } catch (cause) {
      setFeatureFlagsError(toUserMessage(cause));
    } finally {
      setFeatureFlagsBusy(false);
    }
  }

  async function publishFeatureFlags() {
    setFeatureFlagsBusy(true);
    setFeatureFlagsError(null);
    try {
      const document = JSON.parse(featureFlagsJson) as { schemaVersion: string; version: string; flags: unknown[] };
      setFeatureFlags(await client.publishFeatureFlags(document as Parameters<typeof client.publishFeatureFlags>[0]));
    } catch (cause) {
      setFeatureFlagsError(cause instanceof SyntaxError ? "El documento no es JSON válido." : toUserMessage(cause));
    } finally {
      setFeatureFlagsBusy(false);
    }
  }

  async function rollbackFeatureFlags() {
    setFeatureFlagsBusy(true);
    setFeatureFlagsError(null);
    try {
      setFeatureFlags(await client.rollbackFeatureFlags());
    } catch (cause) {
      setFeatureFlagsError(toUserMessage(cause));
    } finally {
      setFeatureFlagsBusy(false);
    }
  }

  useEffect(() => {
    void loadRuns();
    void loadRegistry();
    void loadAgentMap();
    void loadStore();
    void loadFeatureFlags();
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
            <p>El backend requiere un token temporal de preview o un JWT autorizado; el panel nunca persiste el token.</p>
          </div>
          <span className="security-note">Sin secretos en el build</span>
        </div>
        <div className="form-grid">
          <label>API base URL<input value={baseUrl} onChange={(event) => setBaseUrl(event.target.value)} /></label>
          <label>Token de sesión (memoria)<input type="password" value={token} onChange={(event) => setToken(event.target.value)} autoComplete="off" /></label>
        </div>
        <div className="button-row"><button className="primary" onClick={() => void loadRuns()} disabled={busy}>Actualizar runs</button><button onClick={() => void loadRegistry()} disabled={registryBusy}>Actualizar registry</button></div>
      </section>

      <section className="card">
        <div className="section-heading">
          <div><h2>Feature flags de negocio</h2><p>Perfil AppConfig separado del runtime técnico. Publicar requiere el scope de escritura y no reinicia App Runner.</p></div>
          <span className={featureFlags?.stale ? "negative status-label" : "security-note"}>{featureFlags?.stale ? "STALE" : "HOT RELOAD"}</span>
        </div>
        {featureFlagsError && <div className="alert" role="alert">Feature flags: {featureFlagsError}</div>}
        <div className="button-row"><button onClick={() => void loadFeatureFlags()} disabled={featureFlagsBusy}>Actualizar snapshot</button><button onClick={() => void rollbackFeatureFlags()} disabled={featureFlagsBusy}>Rollback última versión</button></div>
        {featureFlags && <div className="metric-row"><Metric label="Versión efectiva" value={featureFlags.effectiveVersion} /><Metric label="Flags" value={String(featureFlags.flags.length)} /><Metric label="Auditoría" value={String(featureFlags.audit.length)} /></div>}
        <label>Documento de publicación (sin secretos)<textarea rows={9} value={featureFlagsJson} onChange={(event) => setFeatureFlagsJson(event.target.value)} /></label>
        <button className="primary" onClick={() => void publishFeatureFlags()} disabled={featureFlagsBusy}>Publicar nueva versión</button>
        {featureFlags && <FeatureFlagView snapshot={featureFlags} />}
      </section>

      <section className="card">
        <div className="section-heading">
          <div><h2>Operación de tienda</h2><p>Catálogo y solicitudes humanas con contexto mínimo sanitizado.</p></div>
          <span className="security-note">CONTROLADO</span>
        </div>
        <div className="store-toolbar">
          <input aria-label="Filtrar catálogo por nombre" placeholder="Nombre de producto" value={catalogName} onChange={(event) => setCatalogName(event.target.value)} />
          <input aria-label="Filtrar catálogo por tipo" placeholder="Tipo: remera, buzo..." value={catalogType} onChange={(event) => setCatalogType(event.target.value)} />
          <input aria-label="Filtrar catálogo por color" placeholder="Color" value={catalogColor} onChange={(event) => setCatalogColor(event.target.value)} />
          <input aria-label="Actor de operación" placeholder="Actor" value={storeActor} onChange={(event) => setStoreActor(event.target.value)} />
          <button className="primary" onClick={() => void loadStore()} disabled={storeBusy}>Actualizar tienda</button>
        </div>
        {catalogError && <div className="alert" role="alert">Catálogo: {catalogError}</div>}
        {followUpError && !catalogError && <div className="alert" role="alert">Atención humana: {followUpError}</div>}
        <div className="store-grid">
          <div>
            <h3>Catálogo</h3>
          <CatalogView page={catalogPage} onAdjustStock={adjustStock} disabled={storeBusy} />
          </div>
          <div>
            <h3>Solicitudes humanas</h3>
            <HumanFollowUpView followUps={followUps} onAction={changeFollowUp} disabled={storeBusy} />
          </div>
        </div>
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
          <div><h2>Mapa de casos de uso</h2><p>Ruta read-only: caso de uso → agentes → tools/Knowledge Base → fallback o humano.</p></div>
          <span className="security-note">SIN MUTACIÓN</span>
        </div>
        <div className="filters registry-filters">
          <input aria-label="Ambiente del mapa" placeholder="environment" value={mapEnvironment} onChange={(event) => setMapEnvironment(event.target.value)} />
          <input aria-label="Canal del mapa" placeholder="channel" value={mapChannel} onChange={(event) => setMapChannel(event.target.value)} />
          <input aria-label="Caso de uso del mapa" placeholder="useCase (opcional)" value={mapUseCase} onChange={(event) => setMapUseCase(event.target.value)} />
          <input aria-label="Agente del mapa" placeholder="agentId (opcional)" value={mapAgentId} onChange={(event) => setMapAgentId(event.target.value)} />
          <button onClick={() => void loadAgentMap()} disabled={agentMapBusy}>Actualizar mapa</button>
        </div>
        {agentMapError && <div className="alert" role="alert">Mapa: {agentMapError}</div>}
        <AgentMapView map={agentMap} simulation={simulation} simulationAgentId={simulationAgentId} simulationVersion={simulationVersion} onAgentChange={setSimulationAgentId} onVersionChange={setSimulationVersion} onSimulate={() => void simulateAgentMap()} disabled={agentMapBusy} />
      </section>

      <section className="card">
        <div className="section-heading">
          <div><h2>Preflight de activación</h2><p>Valida una solicitud sin persistir ni activar nada.</p></div>
          <span className="security-note">SIN MUTACIÓN</span>
        </div>
        <div className="form-grid">
          <label>Agente<input value={preflightAgentId} onChange={(event) => setPreflightAgentId(event.target.value)} /></label>
          <label>Versión<input inputMode="numeric" value={preflightVersion} onChange={(event) => setPreflightVersion(event.target.value)} /></label>
          <label>Ambiente<input value={preflightEnvironment} onChange={(event) => setPreflightEnvironment(event.target.value)} /></label>
          <label>Canal<input value={preflightChannel} onChange={(event) => setPreflightChannel(event.target.value)} /></label>
          <label>Caso de uso<input value={preflightUseCase} onChange={(event) => setPreflightUseCase(event.target.value)} /></label>
          <label>Rollout %<input inputMode="numeric" value={preflightRollout} onChange={(event) => setPreflightRollout(event.target.value)} /></label>
          <label>Motivo<input value={preflightReason} onChange={(event) => setPreflightReason(event.target.value)} /></label>
          <label>Aprobación técnica<input value={preflightApproval} onChange={(event) => setPreflightApproval(event.target.value)} /></label>
          <label>Aprobación operativa<input value={preflightOperationalApproval} onChange={(event) => setPreflightOperationalApproval(event.target.value)} /></label>
        </div>
        <button className="primary" onClick={() => void runPreflight()} disabled={preflightBusy}>Ejecutar preflight</button>
        {preflightError && <div className="alert" role="alert">Preflight: {preflightError}</div>}
        {preflight && <PreflightView result={preflight} />}
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

function CatalogView({ page, onAdjustStock, disabled }: { page: BackofficeCatalogPage | null; onAdjustStock: (sku: string, delta: number) => Promise<void>; disabled: boolean }) {
  if (!page) return <p className="muted">El backoffice operativo no está disponible todavía.</p>;
  if (page.items.length === 0) return <p className="muted">No hay productos para los filtros seleccionados.</p>;
  return <div className="store-list">{page.items.map((product) => <article className="store-item" key={product.id}>
    <div className="section-heading"><div><h4>{product.name}</h4><p className="muted">{product.productType ?? "producto"} · {product.active ? "activo" : "inactivo"}</p></div><span className="security-note">{product.variants.length} variantes</span></div>
    <div className="table-wrap"><table><thead><tr><th>SKU</th><th>Variante</th><th>Precio</th><th>Stock</th><th>Operar</th></tr></thead><tbody>{product.variants.map((variant) => <CatalogVariantRow key={variant.id} variant={variant} onAdjustStock={onAdjustStock} disabled={disabled} />)}</tbody></table></div>
  </article>)}</div>;
}

function CatalogVariantRow({ variant, onAdjustStock, disabled }: { variant: BackofficeCatalogProduct["variants"][number]; onAdjustStock: (sku: string, delta: number) => Promise<void>; disabled: boolean }) {
  const [delta, setDelta] = useState("1");
  return <tr><td><strong>{variant.sku}</strong></td><td>{variant.color} · {variant.size}</td><td>{variant.price.toLocaleString("es-AR")} {variant.currency}</td><td className={variant.stock > 0 ? "positive" : "negative"}>{variant.stock > 0 ? `${variant.stock} disponibles` : "Sin stock"}</td><td><div className="inline-action"><input aria-label={`Ajuste de stock ${variant.sku}`} inputMode="numeric" value={delta} onChange={(event) => setDelta(event.target.value)} /><button disabled={disabled} onClick={() => void onAdjustStock(variant.sku, Number.parseInt(delta, 10))}>Aplicar</button></div></td></tr>;
}

function HumanFollowUpView({ followUps, onAction, disabled }: { followUps: BackofficeHumanFollowUp[] | null; onAction: (id: string, operation: "claim" | "release" | "resolve") => Promise<void>; disabled: boolean }) {
  if (!followUps) return <p className="muted">La cola de atención humana no está disponible todavía.</p>;
  if (followUps.length === 0) return <p className="muted">No hay solicitudes abiertas.</p>;
  return <div className="store-list">{followUps.map((followUp) => <article className="store-item" key={followUp.id}>
    <div className="section-heading"><div><h4>{followUp.reason}</h4><p className="muted">{followUp.channel} · {followUp.status} · vence {new Date(followUp.dueAt).toLocaleString("es-AR")}</p></div><span className={followUp.priority === "HIGH" ? "negative status-label" : "warning status-label"}>{followUp.priority}</span></div>
    <div className="context-preview">{followUp.contextPreview.length === 0 ? <p className="muted">Sin contexto reciente.</p> : followUp.contextPreview.map((message, index) => <p key={`${followUp.id}-${index}`}>{message}</p>)}</div>
    <div className="button-row"><button disabled={disabled || followUp.status !== "OPEN"} onClick={() => void onAction(followUp.id, "claim")}>Tomar</button><button disabled={disabled || followUp.status !== "IN_PROGRESS"} onClick={() => void onAction(followUp.id, "release")}>Devolver</button><button className="primary" disabled={disabled || followUp.status !== "IN_PROGRESS"} onClick={() => void onAction(followUp.id, "resolve")}>Resolver</button></div>
  </article>)}</div>;
}

function FeatureFlagView({ snapshot }: { snapshot: FeatureFlagSnapshot }) {
  return <div className="store-list">
    <p className="muted">Versión efectiva: {snapshot.effectiveVersion} · último refresh: {new Date(snapshot.loadedAt).toLocaleString("es-AR")}</p>
    {snapshot.flags.length === 0 ? <p className="muted">No hay flags publicados.</p> : <div className="table-wrap"><table><thead><tr><th>Key</th><th>Estado</th><th>Alcance</th><th>Agentes/versiones</th></tr></thead><tbody>{snapshot.flags.map((flag) => <tr key={flag.key}><td><strong>{flag.key}</strong></td><td className={flag.enabled && !flag.killSwitch ? "positive" : "negative"}>{flag.killSwitch ? "KILL SWITCH" : flag.enabled ? "ACTIVA" : "INACTIVA"}</td><td>{flag.environments.join(", ") || "todos"} · {flag.channels.join(", ") || "todos"}</td><td>{flag.agentIds.join(", ") || "todos"}{flag.agentVersions.length ? ` · v${flag.agentVersions.join(", v")}` : ""}</td></tr>)}</tbody></table></div>}
    <details><summary>Auditoría reciente</summary><div className="relation-list">{snapshot.audit.slice().reverse().slice(0, 10).map((entry, index) => <span key={`${entry.timestamp}-${index}`}>{entry.operation} · {entry.result} · {entry.version} · {entry.actor}</span>)}</div></details>
  </div>;
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

function AgentMapView({
  map,
  simulation,
  simulationAgentId,
  simulationVersion,
  onAgentChange,
  onVersionChange,
  onSimulate,
  disabled
}: {
  map: BackofficeAgentMap | null;
  simulation: BackofficeAgentMapSimulation | null;
  simulationAgentId: string;
  simulationVersion: string;
  onAgentChange: (value: string) => void;
  onVersionChange: (value: string) => void;
  onSimulate: () => void;
  disabled: boolean;
}) {
  if (!map) return <p className="muted">El mapa no está disponible todavía.</p>;
  return <div className="agent-map">
    <div className="map-meta">
      <span>{map.useCases.length} casos de uso</span>
      <span>{map.evidenceRunsScanned} runs de evidencia agregados</span>
      {map.evidenceTruncated && <span className="warning">Evidencia limitada a la primera página</span>}
    </div>
    {map.useCases.length === 0 ? <p className="muted">No hay activaciones para los filtros seleccionados.</p> : map.useCases.map((useCase) => <article className="use-case-map" key={`${useCase.environment}-${useCase.channel}-${useCase.useCase}`}>
      <div className="section-heading"><div><h3>{useCase.useCase}</h3><p className="muted">{useCase.environment} · {useCase.channel ?? "todos los canales"}</p></div><span className="security-note">{useCase.agents.length} agentes</span></div>
      <div className="agent-map-grid">{useCase.agents.map((agent) => <div className="agent-node" key={`${agent.agentId}-${agent.version}`}>
        <div className="section-heading"><div><h4>{agent.agentId} · v{agent.version}</h4><p className="muted">{agent.name}</p></div><span className={agent.status === "ACTIVE" ? "positive status-label" : "warning status-label"}>{agent.status}</span></div>
        <p className="muted">{agent.modelProvider} / {agent.modelId}</p>
        <div className="metric-row"><Metric label="Ejecuciones" value={String(agent.executionCount)} /><Metric label="Éxito" value={formatPercent(agent.successRate)} /><Metric label="Latencia" value={formatMs(agent.averageLatencyMs)} /></div>
        <div className="node-details"><span>Edad: {agent.ageDays} días</span><span>Tokens: {agent.totalTokens ?? "—"}</span><span>Costo: {agent.estimatedCostUsd == null ? "—" : agent.estimatedCostUsd.toFixed(6)} USD</span><span>Fallback: {agent.fallbackAgentId ?? "humano"}</span></div>
        <div className="node-tags">{agent.allowedTools.map((tool) => <span key={`tool-${tool}`}>tool:{tool}</span>)}{agent.knowledgeSources.map((source) => <span key={`kb-${source}`}>kb:{source}</span>)}</div>
      </div>)}</div>
      <details><summary>Relaciones del flujo</summary><div className="relation-list">{useCase.edges.map((edge, index) => <span key={`${edge.source}-${edge.relation}-${edge.target}-${index}`}>{edge.source} <strong>{edge.relation}</strong> {edge.target}</span>)}</div></details>
    </article>)}
    <div className="simulation-box">
      <div className="section-heading"><div><h3>Simular desactivación</h3><p className="muted">No escribe activaciones ni cambia feature flags.</p></div><span className="security-note">PREVIEW</span></div>
      <div className="form-grid"><label>Agente<input value={simulationAgentId} onChange={(event) => onAgentChange(event.target.value)} /></label><label>Versión (opcional)<input inputMode="numeric" value={simulationVersion} onChange={(event) => onVersionChange(event.target.value)} /></label></div>
      <button className="primary" onClick={onSimulate} disabled={disabled}>Simular ruta</button>
      {simulation && <div className="simulation-result"><div className="section-heading"><strong>{simulation.outcome}</strong><span className={simulation.changed ? "warning status-label" : "positive status-label"}>{simulation.changed ? "CAMBIARÍA" : "SIN CAMBIOS"}</span></div><p>{simulation.reason}</p><div className="relation-list">{simulation.route.map((step, index) => <span key={`${step.kind}-${index}`}>{step.kind}: {step.agentId ?? "humano"}{step.version ? ` v${step.version}` : ""} · {step.reason}</span>)}</div></div>}
    </div>
  </div>;
}

function PreflightView({ result }: { result: AgentActivationPreflight }) {
  return <div className="preflight-result">
    <div className="section-heading"><div><h3>{result.agentId} · v{result.agentVersion}</h3><p className="muted">{result.environment} · {result.channel} · {result.useCase}</p></div><span className={result.canActivate ? "positive status-label" : "negative status-label"}>{result.status}</span></div>
    <div className="preflight-checks">{result.checks.map((check) => <div className="preflight-check" key={check.code}><span className={check.status === "PASS" ? "positive" : check.status === "WARN" ? "warning" : "negative"}>{check.status}</span><span><strong>{check.code}</strong><small>{check.message}</small></span></div>)}</div>
  </div>;
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
