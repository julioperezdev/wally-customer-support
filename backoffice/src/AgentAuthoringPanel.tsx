import { useState } from "react";
import {
  AgentRegistryMutation,
  AgentVersionDraftInput,
  createControlPlaneClient
} from "./api";

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
  onRegistryChanged
}: {
  client: ControlPlaneClient;
  onRegistryChanged: () => Promise<boolean>;
}) {
  const [agentId, setAgentId] = useState("catalog-specialist");
  const [definitionJson, setDefinitionJson] = useState(DEFAULT_DEFINITION);
  const [sourceVersion, setSourceVersion] = useState("1");
  const [lifecycleAgentId, setLifecycleAgentId] = useState("catalog-specialist");
  const [lifecycleVersion, setLifecycleVersion] = useState("1");
  const [targetState, setTargetState] = useState<"CANDIDATE" | "EVALUATED" | "APPROVED">("CANDIDATE");
  const [reason, setReason] = useState("backoffice authoring");
  const [approvalReference, setApprovalReference] = useState("");
  const [operationalApprovalReference, setOperationalApprovalReference] = useState("");
  const [mutation, setMutation] = useState<AgentRegistryMutation | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function createDraft() {
    setBusy(true);
    setError(null);
    setMutation(null);
    try {
      const definition = JSON.parse(definitionJson) as AgentVersionDraftInput;
      const result = await client.createAgentDraft(agentId, definition, idempotencyKey());
      setMutation(result);
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
          <p>Versiones inmutables: se crea una nueva definición y sólo se modifica su estado controlado.</p>
        </div>
        <span className="security-note">PROTEGIDO</span>
      </div>
      <div className="success-alert">
        El panel nunca recibe prompts completos, secretos ni conversaciones. Usá referencias y SHA-256 de los artefactos externos.
      </div>
      <div className="authoring-grid">
        <div>
          <h3>Crear draft</h3>
          <div className="form-grid">
            <label>Agent ID<input value={agentId} onChange={(event) => setAgentId(event.target.value)} /></label>
            <label>Versión (opcional)<input placeholder="siguiente automática" value={readVersion(definitionJson)} onChange={(event) => setDefinitionVersion(event.target.value, definitionJson, setDefinitionJson)} /></label>
          </div>
          <label>Definición metadata JSON<textarea rows={19} value={definitionJson} onChange={(event) => setDefinitionJson(event.target.value)} /></label>
          <div className="button-row">
            <button className="primary" onClick={() => void createDraft()} disabled={busy}>Crear versión DRAFT</button>
          </div>
        </div>
        <div>
          <h3>Clonar versión</h3>
          <p className="muted">Copia metadata de una versión existente y asigna la siguiente versión como DRAFT.</p>
          <div className="form-grid">
            <label>Agent ID<input value={agentId} onChange={(event) => setAgentId(event.target.value)} /></label>
            <label>Versión origen<input inputMode="numeric" value={sourceVersion} onChange={(event) => setSourceVersion(event.target.value)} /></label>
          </div>
          <button onClick={() => void cloneVersion()} disabled={busy}>Clonar como DRAFT</button>

          <h3 className="authoring-subheading">Transicionar lifecycle</h3>
          <div className="form-grid">
            <label>Agent ID<input value={lifecycleAgentId} onChange={(event) => setLifecycleAgentId(event.target.value)} /></label>
            <label>Versión<input inputMode="numeric" value={lifecycleVersion} onChange={(event) => setLifecycleVersion(event.target.value)} /></label>
            <label>Destino<select value={targetState} onChange={(event) => setTargetState(event.target.value as typeof targetState)}><option value="CANDIDATE">CANDIDATE</option><option value="EVALUATED">EVALUATED</option><option value="APPROVED">APPROVED</option></select></label>
            <label>Motivo<input value={reason} onChange={(event) => setReason(event.target.value)} /></label>
            <label>Aprobación técnica<input value={approvalReference} onChange={(event) => setApprovalReference(event.target.value)} /></label>
            <label>Aprobación operativa<input value={operationalApprovalReference} onChange={(event) => setOperationalApprovalReference(event.target.value)} /></label>
          </div>
          <button onClick={() => void transitionLifecycle()} disabled={busy}>Aplicar transición</button>
        </div>
      </div>
      {error && <div className="alert" role="alert">Authoring: {error}</div>}
      {mutation && <div className="success-alert" role="status">{mutation.status}: {mutation.agentId} v{mutation.version ?? "—"}{mutation.state ? ` · ${mutation.state}` : ""} · {mutation.reason}</div>}
    </section>
  );
}

function readVersion(json: string): string {
  try {
    const value = (JSON.parse(json) as { version?: number | null }).version;
    return value == null ? "" : String(value);
  } catch {
    return "";
  }
}

function setDefinitionVersion(value: string, json: string, setJson: (value: string) => void) {
  try {
    const definition = JSON.parse(json) as Record<string, unknown>;
    definition.version = value.trim() ? Number.parseInt(value, 10) : null;
    setJson(JSON.stringify(definition, null, 2));
  } catch {
    // The JSON editor remains the source of truth when it is temporarily malformed.
  }
}

function idempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return `backoffice-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function userMessage(cause: unknown): string {
  if (cause instanceof Error) return cause.message;
  return "No se pudo completar la operación.";
}
