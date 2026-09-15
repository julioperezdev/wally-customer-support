import { useMemo, useState } from "react";
import {
  AgentFilterOptions,
  AgentRegistryAgent,
  AgentRegistryMutation,
  AgentVersionDraftInput,
  createControlPlaneClient
} from "./api";
import { draftFromVersion, versionsForAgent } from "./agent-registry";

type ControlPlaneClient = ReturnType<typeof createControlPlaneClient>;

const DEFAULT_DEFINITION = `{
  "version": null,
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
  "evaluationSuiteVersion": "catalog-response-v1"
}`;

export function AgentAuthoringPanel({
  client,
  onRegistryChanged,
  canWrite,
  agents,
  filterOptions
}: {
  client: ControlPlaneClient;
  onRegistryChanged: () => Promise<boolean>;
  canWrite: boolean;
  agents: AgentRegistryAgent[] | null;
  filterOptions: AgentFilterOptions;
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
  const [mutation, setMutation] = useState<AgentRegistryMutation | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const availableVersions = useMemo(
    () => versionsForAgent(agents, agentId),
    [agents, agentId]);

  function selectAgent(value: string) {
    setAgentId(value);
    setLifecycleAgentId(value);
    setEditingVersion(null);
    const firstVersion = versionsForAgent(agents, value)[0];
    setSourceVersion(firstVersion ? String(firstVersion.version) : "");
    setLifecycleVersion(firstVersion ? String(firstVersion.version) : "");
    setError(null);
  }

  function loadVersionForEditing() {
    const versionNumber = Number.parseInt(sourceVersion, 10);
    const version = availableVersions.find((candidate) => candidate.version === versionNumber);
    if (!version) {
      setError("Seleccioná una versión existente para cargarla como nueva DRAFT.");
      return;
    }
    setDefinitionJson(JSON.stringify(draftFromVersion(version), null, 2));
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
          ...(operationalApprovalReference.trim() ? { operationalApprovalReference } : {})
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
          <p>Creá una nueva versión inmutable, editá su metadata y promovela con un workflow auditable.</p>
        </div>
        <span className="security-note">PROTEGIDO</span>
      </div>
      <div className="success-alert">
        Los prompts, schemas y secretos son artefactos externos: el registry sólo guarda referencias, versiones y hashes SHA-256.
      </div>
      {!canWrite && <div className="warning-alert">Tu usuario no tiene <code>agent-registry.write</code>; el authoring está en modo lectura.</div>}
      <div className="authoring-grid">
        <div>
          <h3>Crear o editar como nueva DRAFT</h3>
          <p className="muted">Para editar una versión publicada, cargala abajo, cambiá la metadata y guardá una nueva versión. La original nunca se modifica.</p>
          {editingVersion !== null && <div className="info-alert">Editando una copia de <strong>{agentId} v{editingVersion}</strong>. Guardar creará la siguiente versión DRAFT.</div>}
          <div className="form-grid">
            <label>Agent ID<input list="agent-authoring-agent-ids" value={agentId} onChange={(event) => selectAgent(event.target.value)} /></label>
            <label>Versión nueva<input value="automática" readOnly /></label>
          </div>
          <datalist id="agent-authoring-agent-ids">{filterOptions.agentIds.map((option) => <option key={option} value={option} />)}</datalist>
          <label>Definición metadata JSON<textarea rows={19} value={definitionJson} onChange={(event) => setDefinitionJson(event.target.value)} /></label>
          <div className="button-row">
            <button className="primary" onClick={() => void createDraft()} disabled={busy || !canWrite}>Guardar nueva DRAFT</button>
          </div>
        </div>
        <div>
          <h3>Cargar una versión para editar</h3>
          <p className="muted">La carga sólo copia metadata sanitizada al editor. No trae prompts completos ni conversaciones.</p>
          <div className="form-grid">
            <label>Agente<select value={agentId} onChange={(event) => selectAgent(event.target.value)}><option value="">Seleccionar</option>{filterOptions.agentIds.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
            <label>Versión origen<select value={sourceVersion} onChange={(event) => setSourceVersion(event.target.value)}><option value="">Seleccionar</option>{availableVersions.map((version) => <option key={version.version} value={version.version}>v{version.version} · {version.state}</option>)}</select></label>
          </div>
          <div className="button-row">
            <button onClick={loadVersionForEditing} disabled={busy || !agentId || !sourceVersion}>Cargar para editar</button>
            <button onClick={() => void cloneVersion()} disabled={busy || !canWrite || !agentId || !sourceVersion}>Clonar sin cambios</button>
          </div>

          <h3 className="authoring-subheading">Transicionar lifecycle</h3>
          <div className="form-grid">
            <label>Agent ID<input list="agent-lifecycle-agent-ids" value={lifecycleAgentId} onChange={(event) => setLifecycleAgentId(event.target.value)} /></label>
            <label>Versión<input inputMode="numeric" value={lifecycleVersion} onChange={(event) => setLifecycleVersion(event.target.value)} placeholder="versión creada" /></label>
            <label>Destino<select value={targetState} onChange={(event) => setTargetState(event.target.value as typeof targetState)}><option value="CANDIDATE">CANDIDATE</option><option value="EVALUATED">EVALUATED</option><option value="APPROVED">APPROVED</option><option value="ACTIVE">ACTIVE</option><option value="RETIRED">RETIRED</option></select></label>
            <label>Motivo<input value={reason} onChange={(event) => setReason(event.target.value)} /></label>
            <label>Aprobación técnica<input value={approvalReference} onChange={(event) => setApprovalReference(event.target.value)} /></label>
            <label>Aprobación operativa<input value={operationalApprovalReference} onChange={(event) => setOperationalApprovalReference(event.target.value)} /></label>
          </div>
          <datalist id="agent-lifecycle-agent-ids">{filterOptions.agentIds.map((option) => <option key={option} value={option} />)}</datalist>
          <button onClick={() => void transitionLifecycle()} disabled={busy || !canWrite}>Aplicar transición</button>
          <p className="muted">Las transiciones de publicación requieren autorización y referencias de aprobación cuando corresponda.</p>
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
