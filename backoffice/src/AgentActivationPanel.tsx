import { useState } from "react";
import {
  AgentActivationActionRequest,
  AgentActivationMutation,
  AgentActivationPreflight,
  AgentActivationRequest,
  ControlPlaneError,
  createControlPlaneClient
} from "./api";

type ControlPlaneClient = ReturnType<typeof createControlPlaneClient>;

type ActivationDraft = {
  agentId: string;
  version: string;
  environment: string;
  channel: string;
  useCase: string;
  reason: string;
  rollout: string;
  approvalReference: string;
  operationalApprovalReference: string;
};

const INITIAL_DRAFT: ActivationDraft = {
  agentId: "catalog-specialist",
  version: "1",
  environment: "prod",
  channel: "telegram",
  useCase: "catalog-search",
  reason: "controlled activation",
  rollout: "100",
  approvalReference: "",
  operationalApprovalReference: ""
};

export function AgentActivationPanel({
  client,
  onRegistryChanged
}: {
  client: ControlPlaneClient;
  onRegistryChanged: () => Promise<boolean>;
}) {
  const [draft, setDraft] = useState<ActivationDraft>(INITIAL_DRAFT);
  const [preflight, setPreflight] = useState<AgentActivationPreflight | null>(null);
  const [preflightFingerprint, setPreflightFingerprint] = useState("");
  const [preflightBusy, setPreflightBusy] = useState(false);
  const [mutationBusy, setMutationBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [mutation, setMutation] = useState<AgentActivationMutation | null>(null);

  function updateDraft(field: keyof ActivationDraft, value: string) {
    setDraft((current) => ({ ...current, [field]: value }));
    setPreflightFingerprint("");
    setError(null);
    setMessage(null);
    setMutation(null);
  }

  function activationRequest(): AgentActivationRequest | null {
    const agentVersion = Number.parseInt(draft.version, 10);
    const rolloutPercentage = Number.parseInt(draft.rollout, 10);
    if (!draft.agentId.trim() || !draft.environment.trim() || !draft.channel.trim() || !draft.useCase.trim()) {
      setError("Agente, ambiente, canal y caso de uso son obligatorios.");
      return null;
    }
    if (!Number.isInteger(agentVersion) || agentVersion < 1) {
      setError("La versión debe ser un número entero positivo.");
      return null;
    }
    if (!Number.isInteger(rolloutPercentage) || rolloutPercentage < 0 || rolloutPercentage > 100) {
      setError("El rollout debe ser un porcentaje entre 0 y 100.");
      return null;
    }
    if (!draft.reason.trim() || !draft.approvalReference.trim() || !draft.operationalApprovalReference.trim()) {
      setError("Motivo y las dos referencias de aprobación son obligatorios.");
      return null;
    }
    return {
      agentId: draft.agentId.trim(),
      agentVersion,
      environment: draft.environment.trim(),
      channel: draft.channel.trim(),
      useCase: draft.useCase.trim(),
      reason: draft.reason.trim(),
      rolloutPercentage,
      enabled: true,
      approvalReference: draft.approvalReference.trim(),
      operationalApprovalReference: draft.operationalApprovalReference.trim()
    };
  }

  function actionRequest(): AgentActivationActionRequest | null {
    if (!draft.agentId.trim() || !draft.environment.trim() || !draft.channel.trim() || !draft.useCase.trim()) {
      setError("Agente, ambiente, canal y caso de uso son obligatorios.");
      return null;
    }
    if (!draft.approvalReference.trim() || !draft.operationalApprovalReference.trim()) {
      setError("Kill switch y rollback requieren las dos referencias de aprobación.");
      return null;
    }
    return {
      agentId: draft.agentId.trim(),
      environment: draft.environment.trim(),
      channel: draft.channel.trim(),
      useCase: draft.useCase.trim(),
      approvalReference: draft.approvalReference.trim(),
      operationalApprovalReference: draft.operationalApprovalReference.trim()
    };
  }

  function fingerprint(request: AgentActivationRequest) {
    return JSON.stringify(request);
  }

  async function runPreflight() {
    const request = activationRequest();
    if (!request) return;
    setPreflightBusy(true);
    setError(null);
    setMessage(null);
    setMutation(null);
    try {
      setPreflight(await client.preflightActivation(request));
      setPreflightFingerprint(fingerprint(request));
    } catch (cause) {
      setPreflight(null);
      setPreflightFingerprint("");
      setError(toActivationMessage(cause));
    } finally {
      setPreflightBusy(false);
    }
  }

  async function activate() {
    const request = activationRequest();
    if (!request) return;
    if (!preflight || preflightFingerprint !== fingerprint(request) || !preflight.canActivate) {
      setError("Ejecutá nuevamente el preflight y activá sólo cuando figure READY.");
      return;
    }
    await mutate("activate", () => client.activateAgent(request, newIdempotencyKey()));
  }

  async function killSwitch() {
    const request = actionRequest();
    if (!request) return;
    await mutate("kill switch", () => client.killSwitchAgent(request, newIdempotencyKey()));
  }

  async function rollback() {
    const request = actionRequest();
    if (!request) return;
    await mutate("rollback", () => client.rollbackAgent(request, newIdempotencyKey()));
  }

  async function mutate(operation: string, action: () => Promise<AgentActivationMutation>) {
    setMutationBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await action();
      setMutation(result);
      if (result.status === "ACTIVATED" || result.status === "KILL_SWITCHED" || result.status === "ROLLED_BACK") {
        const registryRefreshed = await onRegistryChanged();
        setMessage(registryRefreshed
          ? `${operation} ejecutado. Se actualizó el registry read-only.`
          : `${operation} ejecutado, pero no se pudo actualizar el registry automáticamente.`);
      } else {
        setError(`${operation} no aplicado: ${result.reason}.`);
      }
    } catch (cause) {
      setError(toActivationMessage(cause));
    } finally {
      setMutationBusy(false);
    }
  }

  const currentRequest = buildFingerprintCandidate(draft);
  const preflightIsCurrent = preflight !== null && preflightFingerprint === currentRequest;
  const activationReady = preflightIsCurrent && preflight.canActivate;

  return <section className="card activation-console">
    <div className="section-heading">
      <div><h2>Control de activaciones</h2><p>Promoción, rollback y kill switch de una versión aprobada.</p></div>
      <span className="warning status-label">MUTACIÓN CONTROLADA</span>
    </div>
    <div className="warning-alert" role="note">
      Las acciones escriben en el registry sólo si el backend está habilitado, el JWT tiene <code>agent-registry.write</code> y existen ambas aprobaciones. La pantalla no muestra prompts, secretos ni el contenido de las aprobaciones.
    </div>
    <div className="form-grid">
      <label>Agente<input value={draft.agentId} onChange={(event) => updateDraft("agentId", event.target.value)} /></label>
      <label>Versión<input inputMode="numeric" value={draft.version} onChange={(event) => updateDraft("version", event.target.value)} /></label>
      <label>Ambiente<input value={draft.environment} onChange={(event) => updateDraft("environment", event.target.value)} /></label>
      <label>Canal<input value={draft.channel} onChange={(event) => updateDraft("channel", event.target.value)} /></label>
      <label>Caso de uso<input value={draft.useCase} onChange={(event) => updateDraft("useCase", event.target.value)} /></label>
      <label>Rollout %<input inputMode="numeric" value={draft.rollout} onChange={(event) => updateDraft("rollout", event.target.value)} /></label>
      <label>Motivo<input value={draft.reason} onChange={(event) => updateDraft("reason", event.target.value)} /></label>
      <label>Aprobación técnica<input value={draft.approvalReference} onChange={(event) => updateDraft("approvalReference", event.target.value)} autoComplete="off" /></label>
      <label>Aprobación operativa<input value={draft.operationalApprovalReference} onChange={(event) => updateDraft("operationalApprovalReference", event.target.value)} autoComplete="off" /></label>
    </div>
    <div className="button-row">
      <button onClick={() => void runPreflight()} disabled={preflightBusy || mutationBusy}>{preflightBusy ? "Validando..." : "Ejecutar preflight"}</button>
      <button className="primary" onClick={() => void activate()} disabled={preflightBusy || mutationBusy || !activationReady}>Activar versión</button>
      <button onClick={() => void rollback()} disabled={preflightBusy || mutationBusy}>Rollback</button>
      <button className="negative-button" onClick={() => void killSwitch()} disabled={preflightBusy || mutationBusy}>Kill switch</button>
    </div>
    {!preflightIsCurrent && <p className="muted">La activación queda bloqueada hasta ejecutar un preflight con estos mismos datos.</p>}
    {error && <div className="alert" role="alert">Activaciones: {error}</div>}
    {message && <div className="success-alert" role="status" aria-live="polite">Activaciones: {message}</div>}
    {preflight && <PreflightSummary result={preflight} current={preflightIsCurrent} />}
    {mutation && <MutationSummary result={mutation} />}
  </section>;
}

function PreflightSummary({ result, current }: { result: AgentActivationPreflight; current: boolean }) {
  return <div className="preflight-result">
    <div className="section-heading"><div><h3>Preflight {result.agentId} · v{result.agentVersion}</h3><p className="muted">{result.environment} · {result.channel} · {result.useCase}</p></div><span className={result.canActivate && current ? "positive status-label" : "negative status-label"}>{current ? result.status : "STALE"}</span></div>
    <div className="preflight-checks">{result.checks.map((check) => <div className="preflight-check" key={check.code}><span className={check.status === "PASS" ? "positive" : check.status === "WARN" ? "warning" : "negative"}>{check.status}</span><span><strong>{check.code}</strong><small>{check.message}</small></span></div>)}</div>
  </div>;
}

function MutationSummary({ result }: { result: AgentActivationMutation }) {
  return <div className="mutation-result" role="status"><div className="section-heading"><div><h3>Resultado: {result.status}</h3><p className="muted">{result.reason}</p></div><span className={result.status === "ACTIVATED" || result.status === "KILL_SWITCHED" || result.status === "ROLLED_BACK" ? "positive status-label" : "negative status-label"}>{result.status}</span></div><p className="muted">{result.agentId} · {result.agentVersion == null ? "versión no informada" : `v${result.agentVersion}`} · {result.environment} · {result.channel} · {result.useCase}</p></div>;
}

function buildFingerprintCandidate(draft: ActivationDraft) {
  const agentVersion = Number.parseInt(draft.version, 10);
  const rolloutPercentage = Number.parseInt(draft.rollout, 10);
  if (!Number.isInteger(agentVersion) || !Number.isInteger(rolloutPercentage)) return "";
  return JSON.stringify({
    agentId: draft.agentId.trim(),
    agentVersion,
    environment: draft.environment.trim(),
    channel: draft.channel.trim(),
    useCase: draft.useCase.trim(),
    reason: draft.reason.trim(),
    rolloutPercentage,
    enabled: true,
    approvalReference: draft.approvalReference.trim(),
    operationalApprovalReference: draft.operationalApprovalReference.trim()
  });
}

function newIdempotencyKey() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return `wcs-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function toActivationMessage(cause: unknown) {
  if (cause instanceof ControlPlaneError) {
    if (cause.status === 401) return "La sesión no es válida. Ingresá un JWT autorizado.";
    if (cause.status === 403) return "La mutación fue rechazada: falta autorización, el backend está cerrado o no se puede operar ese ambiente.";
    if (cause.status === 409) return "La orden ya fue procesada. Usá una nueva acción sólo si corresponde.";
    if (cause.status === 422) return "La solicitud no supera las validaciones del registry.";
    return `El control plane respondió ${cause.code}.`;
  }
  return "No se pudo ejecutar la acción de activación. Revisá la URL y el estado del backend.";
}
