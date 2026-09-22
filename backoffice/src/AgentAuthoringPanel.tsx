import { useMemo, useState } from "react";
import {
  AgentFilterOptions,
  AgentRegistryAgent,
  AgentRegistryMutation,
  AgentVersionDraftInput,
  RunSummary,
  createControlPlaneClient
} from "./api";
import { draftFromVersion, versionsForAgent } from "./agent-registry";

type ControlPlaneClient = ReturnType<typeof createControlPlaneClient>;

const DEFAULT_DEFINITION = `{
  "version": null,
  "semanticVersion": "1.0.0",
  "name": "Catalog specialist candidate",
  "purpose": "Search products, variants, price and stock through validated WCS tools",
  "modelProvider": "bedrock",
  "modelId": "openai.gpt-oss-20b-1:0",
  "temperature": 0,
  "topP": 1,
  "systemPromptVersion": "system-v1",
  "systemPromptHash": "7b791651bc2e74d37ced276deb5fa48f425a382997db3fe67c83bc7f7a855811",
  "inputSchemaVersion": "catalog-input-v1",
  "outputSchemaVersion": "catalog-output-v1",
  "allowedTools": ["catalog.search", "catalog.stock"],
  "knowledgeSources": [],
  "memoryPolicy": "conversation-summary-v1",
  "responsePolicy": "grounded-customer-support-v1",
  "timeoutMs": 10000,
  "maxSteps": 2,
  "maxInputTokens": 2000,
  "maxOutputTokens": 1000,
  "budgetLimitUsd": 0.05,
  "fallbackAgentId": null,
  "evaluationSuiteVersion": "catalog-response-v1",
  "invocationConfiguration": {
    "systemPrompt": "",
    "userPromptTemplate": "",
    "inputSchemaJson": "{}",
    "outputSchemaJson": "{}",
    "reasoningEffort": null,
    "structuredToolCalling": false,
    "pricingVersion": "aws-bedrock-us-east-1-standard-2026-09",
    "inputPriceUsdPerMillionTokens": 0.0721,
    "outputPriceUsdPerMillionTokens": 0.309
  }
}`;

export function AgentAuthoringPanel({
  client,
  onRegistryChanged,
  canWrite,
  agents,
  filterOptions,
  evaluationRuns
}: {
  client: ControlPlaneClient;
  onRegistryChanged: () => Promise<boolean>;
  canWrite: boolean;
  agents: AgentRegistryAgent[] | null;
  filterOptions: AgentFilterOptions;
  evaluationRuns: RunSummary[];
}) {
  const [agentId, setAgentId] = useState("catalog-specialist");
  const [definitionJson, setDefinitionJson] = useState(DEFAULT_DEFINITION);
  const [sourceVersion, setSourceVersion] = useState("1");
  const [editingVersion, setEditingVersion] = useState<number | null>(null);
  const [lifecycleAgentId, setLifecycleAgentId] = useState("catalog-specialist");
  const [lifecycleVersion, setLifecycleVersion] = useState("1");
  const [targetState, setTargetState] = useState<"CANDIDATE" | "EVALUATED" | "APPROVED" | "ACTIVE" | "RETIRED">("CANDIDATE");
  const [reason, setReason] = useState("backoffice authoring");
  const [approvalReference, setApprovalReference] = useState("");
  const [operationalApprovalReference, setOperationalApprovalReference] = useState("");
  const [baselineEvaluationRunId, setBaselineEvaluationRunId] = useState("");
  const [candidateEvaluationRunId, setCandidateEvaluationRunId] = useState("");
  const [mutation, setMutation] = useState<AgentRegistryMutation | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const availableVersions = useMemo(
    () => versionsForAgent(agents, agentId),
    [agents, agentId]);
  const lifecycleVersions = useMemo(
    () => versionsForAgent(agents, lifecycleAgentId),
    [agents, lifecycleAgentId]);
  const selectedLifecycleVersion = lifecycleVersions.find((version) => version.version === Number(lifecycleVersion));
  const activeBaselineRuns = evaluationRuns.filter((run) => run.agentId === lifecycleAgentId
    && lifecycleVersions.some((version) => version.state === "ACTIVE" && String(version.version) === run.agentVersion));
  const selectedBaselineRun = activeBaselineRuns.find((run) => run.runId === baselineEvaluationRunId);
  const eligibleCandidateRuns = evaluationRuns.filter((run) => run.agentId === lifecycleAgentId
    && run.agentVersion === lifecycleVersion
    && selectedLifecycleVersion?.evaluationSuiteVersion === run.datasetVersion
    && (!selectedBaselineRun || selectedBaselineRun.datasetVersion === run.datasetVersion));
  const requiresEvaluationEvidence = targetState === "EVALUATED" || targetState === "APPROVED";

  function selectAgent(value: string) {
    setAgentId(value);
    setLifecycleAgentId(value);
    setEditingVersion(null);
    const firstVersion = versionsForAgent(agents, value)[0];
    setSourceVersion(firstVersion ? String(firstVersion.version) : "");
    setLifecycleVersion(firstVersion ? String(firstVersion.version) : "");
    setBaselineEvaluationRunId("");
    setCandidateEvaluationRunId("");
    setError(null);
  }

  function loadVersionForEditing() {
    const versionNumber = Number.parseInt(sourceVersion, 10);
    const version = availableVersions.find((candidate) => candidate.version === versionNumber);
    if (!version) {
      setError("Seleccioná una versión existente para cargarla como nueva DRAFT.");
      return;
    }
    setDefinitionJson(JSON.stringify(draftFromVersion(version, availableVersions), null, 2));
    setEditingVersion(version.version);
    setLifecycleAgentId(version.agentId);
    setLifecycleVersion("");
    setMutation(null);
    setError(null);
  }

  async function createDraft() {
    setBusy(true);
    setError(null);
    setMutation(null);
    try {
      const definition = JSON.parse(definitionJson) as AgentVersionDraftInput;
      const result = await client.createAgentDraft(agentId, {
        ...definition,
        version: null
      }, idempotencyKey());
      setMutation(result);
      setEditingVersion(null);
      if (result.version) setLifecycleVersion(String(result.version));
      await onRegistryChanged();
    } catch (cause) {
      setError(cause instanceof SyntaxError ? "La definición no es JSON válido." : userMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function cloneVersion() {
    const version = Number.parseInt(sourceVersion, 10);
    if (!agentId.trim() || !Number.isInteger(version)) {
      setError("Indicá un agentId y una versión de origen válida.");
      return;
    }
    setBusy(true);
    setError(null);
    setMutation(null);
    try {
      const result = await client.cloneAgentVersion(agentId, version, idempotencyKey());
      setMutation(result);
      setEditingVersion(null);
      if (result.version) setLifecycleVersion(String(result.version));
      await onRegistryChanged();
    } catch (cause) {
      setError(userMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function transitionLifecycle() {
    const version = Number.parseInt(lifecycleVersion, 10);
    if (!lifecycleAgentId.trim() || !Number.isInteger(version) || !reason.trim()) {
      setError("Indicá agentId, versión y motivo para la transición.");
      return;
    }
    if (requiresEvaluationEvidence && (!baselineEvaluationRunId || !candidateEvaluationRunId)) {
      setError("Elegí un run del agente activo y otro de esta versión, con el mismo dataset, para documentar la evaluación comparable.");
      return;
    }
    setBusy(true);
    setError(null);
    setMutation(null);
    try {
      const result = await client.transitionAgentVersion(
        lifecycleAgentId,
        version,
        {
          targetState,
          reason,
          ...(approvalReference.trim() ? { approvalReference } : {}),
          ...(operationalApprovalReference.trim() ? { operationalApprovalReference } : {}),
          ...(requiresEvaluationEvidence ? { baselineEvaluationRunId, candidateEvaluationRunId } : {})
        },
        idempotencyKey());
      setMutation(result);
      await onRegistryChanged();
    } catch (cause) {
      setError(userMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <div className="section-heading">
        <div>
          <h2>Authoring y lifecycle de agentes</h2>
          <p>Creá versiones inmutables de prompts, modelo, parámetros, schemas y tools; publicá sólo tras evaluación y aprobación.</p>
        </div>
        <span className="security-note">PROTEGIDO</span>
      </div>
      <div className="success-alert">
        El registro SQL guarda prompts, modelo, límites y pricing versionados; los hash SHA-256 se calculan en backend. Secretos y herramientas ejecutables no se almacenan aquí.
      </div>
      {!canWrite && <div className="warning-alert">Tu usuario no tiene <code>agent-registry.write</code>; el authoring está en modo lectura.</div>}
      <div className="authoring-grid">
        <div>
          <h3>Crear o editar como nueva DRAFT</h3>
          <p className="muted">Para editar una versión publicada, cargala abajo, cambiá prompts, modelo, límites o schemas y guardá una nueva versión. La original nunca se modifica.</p>
          {editingVersion !== null && <div className="info-alert">Editando una copia de <strong>{agentId} v{editingVersion}</strong>. Guardar creará la siguiente versión DRAFT.</div>}
          <div className="form-grid">
            <label>Agent ID<input list="agent-authoring-agent-ids" value={agentId} onChange={(event) => selectAgent(event.target.value)} /></label>
            <label>SemVer<input value="se define en la definición JSON" readOnly /></label>
          </div>
          <datalist id="agent-authoring-agent-ids">{filterOptions.agentIds.map((option) => <option key={option} value={option} />)}</datalist>
          <label>Definición metadata JSON<textarea rows={19} value={definitionJson} onChange={(event) => setDefinitionJson(event.target.value)} /></label>
          <div className="button-row">
            <button className="primary" onClick={() => void createDraft()} disabled={busy || !canWrite}>Guardar nueva DRAFT</button>
          </div>
        </div>
        <div>
          <h3>Cargar una versión para editar</h3>
          <p className="muted">La carga copia al editor la definición versionada, incluidos prompts y schemas. Nunca incluye conversaciones ni secretos.</p>
          <div className="form-grid">
            <label>Agente<select value={agentId} onChange={(event) => selectAgent(event.target.value)}><option value="">Seleccionar</option>{filterOptions.agentIds.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
            <label>Versión origen<select value={sourceVersion} onChange={(event) => setSourceVersion(event.target.value)}><option value="">Seleccionar</option>{availableVersions.map((version) => <option key={version.version} value={version.version}>{version.semanticVersion} · SQL #{version.version} · {version.state}</option>)}</select></label>
          </div>
          <div className="button-row">
            <button onClick={loadVersionForEditing} disabled={busy || !agentId || !sourceVersion}>Cargar para editar</button>
            <button onClick={() => void cloneVersion()} disabled={busy || !canWrite || !agentId || !sourceVersion}>Clonar sin cambios</button>
          </div>

          <h3 className="authoring-subheading">Transicionar lifecycle</h3>
          <div className="form-grid">
            <label>Agent ID<select value={lifecycleAgentId} onChange={(event) => { setLifecycleAgentId(event.target.value); setBaselineEvaluationRunId(""); setCandidateEvaluationRunId(""); }}><option value="">Seleccionar</option>{filterOptions.agentIds.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
            <label>Versión<select value={lifecycleVersion} onChange={(event) => { setLifecycleVersion(event.target.value); setCandidateEvaluationRunId(""); }}><option value="">Seleccionar</option>{lifecycleVersions.map((version) => <option key={version.version} value={version.version}>v{version.version} · {version.semanticVersion} · {version.state}</option>)}</select></label>
            <label>Destino<select value={targetState} onChange={(event) => setTargetState(event.target.value as typeof targetState)}><option value="CANDIDATE">CANDIDATE</option><option value="EVALUATED">EVALUATED</option><option value="APPROVED">APPROVED</option><option value="ACTIVE">ACTIVE</option><option value="RETIRED">RETIRED</option></select></label>
            <label>Motivo<input value={reason} onChange={(event) => setReason(event.target.value)} /></label>
            <label>Aprobación técnica<input value={approvalReference} onChange={(event) => setApprovalReference(event.target.value)} /></label>
            <label>Aprobación operativa<input value={operationalApprovalReference} onChange={(event) => setOperationalApprovalReference(event.target.value)} /></label>
          </div>
          {requiresEvaluationEvidence && <>
            <div className="info-alert">La promoción necesita comparar la candidata con una ejecución de la versión activa, usando el mismo dataset. La API valida también que la cobertura de escenarios coincida. Consultá la tabla Comparar antes de la aprobación humana; el resultado no aprueba automáticamente.</div>
            <div className="form-grid">
              <label>Run baseline (versión activa)<select aria-label="Run baseline activo" value={baselineEvaluationRunId} onChange={(event) => { setBaselineEvaluationRunId(event.target.value); setCandidateEvaluationRunId(""); }}><option value="">Seleccionar run del activo</option>{activeBaselineRuns.map((run) => <option key={run.runId} value={run.runId}>{run.agentVersion} · {run.datasetVersion} · {new Date(run.completedAt).toLocaleString("es-AR")} · {run.passRate.toLocaleString("es-AR", { style: "percent" })}</option>)}</select></label>
              <label>Run candidata (esta versión)<select aria-label="Run candidato" value={candidateEvaluationRunId} onChange={(event) => setCandidateEvaluationRunId(event.target.value)}><option value="">Seleccionar run de candidata</option>{eligibleCandidateRuns.map((run) => <option key={run.runId} value={run.runId}>{run.agentVersion} · {run.datasetVersion} · {new Date(run.completedAt).toLocaleString("es-AR")} · {run.passRate.toLocaleString("es-AR", { style: "percent" })}</option>)}</select></label>
            </div>
            {evaluationRuns.length === 0 && <p className="muted">No hay runs cargados en esta vista. Actualizá los runs después de evaluar la versión activa y la candidata.</p>}
          </>}
          <button onClick={() => void transitionLifecycle()} disabled={busy || !canWrite}>Aplicar transición</button>
          <p className="muted">La evaluación queda enlazada a la auditoría. La publicación sigue requiriendo aprobación humana y autorización.</p>
        </div>
      </div>
      {error && <div className="alert" role="alert">Authoring: {error}</div>}
      {mutation && <div className="success-alert" role="status">{mutation.status}: {mutation.agentId} v{mutation.version ?? "—"}{mutation.state ? ` · ${mutation.state}` : ""} · {mutation.reason}</div>}
    </section>
  );
}

function idempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return `backoffice-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function userMessage(cause: unknown): string {
  if (cause instanceof Error) return cause.message;
  return "No se pudo completar la operación.";
}
