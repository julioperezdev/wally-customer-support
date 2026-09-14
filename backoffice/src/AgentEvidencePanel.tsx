import { useState } from "react";
import { AgentExecutionTrace, AgentFilterOptions, AgentRegistryAuditEvent, createControlPlaneClient } from "./api";

type ControlPlaneClient = ReturnType<typeof createControlPlaneClient>;

export function AgentEvidencePanel({ client, canRead, filterOptions }: { client: ControlPlaneClient; canRead: boolean; filterOptions: AgentFilterOptions }) {
  const [agentId, setAgentId] = useState("");
  const [useCase, setUseCase] = useState("");
  const [audit, setAudit] = useState<AgentRegistryAuditEvent[] | null>(null);
  const [executions, setExecutions] = useState<AgentExecutionTrace[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function loadEvidence() {
    setBusy(true);
    setError(null);
    try {
      const [nextAudit, nextExecutions] = await Promise.all([
        client.listAgentAudit(agentId),
        client.listAgentExecutions(agentId, useCase)
      ]);
      setAudit(nextAudit);
      setExecutions(nextExecutions);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo cargar la evidencia del control plane.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <div className="section-heading">
        <div>
          <h2>Auditoría y trazas de ejecución</h2>
          <p>Quién cambió una versión y cómo se resolvieron las ejecuciones reales, sin guardar mensajes ni PII.</p>
        </div>
        <span className="security-note">READ ONLY</span>
      </div>
      <div className="filters registry-filters">
        <label>Agente<select aria-label="Filtrar evidencia por agente" value={agentId} onChange={(event) => setAgentId(event.target.value)}><option value="">Todos</option>{filterOptions.agentIds.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
        <label>Caso de uso<select aria-label="Filtrar trazas por caso de uso" value={useCase} onChange={(event) => setUseCase(event.target.value)}><option value="">Todos</option>{filterOptions.useCases.map((option) => <option key={option} value={option}>{option}</option>)}</select></label>
        <button onClick={() => void loadEvidence()} disabled={busy || !canRead}>{busy ? "Cargando…" : "Actualizar evidencia"}</button>
      </div>
      {!canRead && <div className="warning-alert">Tu usuario no tiene <code>agent-registry.read</code>; la evidencia está restringida.</div>}
      {error && <div className="alert" role="alert">Evidencia: {error}</div>}
      <div className="evidence-grid">
        <div>
          <h3>Auditoría de cambios</h3>
          {!audit ? <p className="muted">Sin datos cargados.</p> : audit.length === 0 ? <p className="muted">No hay eventos para el filtro.</p> : (
            <div className="table-wrap"><table className="responsive-table"><thead><tr><th>Operación</th><th>Agente</th><th>Estado</th><th>Actor</th><th>Motivo</th><th>Hora</th></tr></thead><tbody>
              {audit.map((event, index) => <tr key={`${event.occurredAt}-${index}`}><td data-label="Operación">{event.operation}</td><td data-label="Agente">{event.agentId} {event.agentVersion ? `v${event.agentVersion}` : ""}</td><td data-label="Estado">{event.previousState ?? "—"} → {event.resultingState ?? "—"}</td><td data-label="Actor">{event.actorId}</td><td data-label="Motivo">{event.reason}</td><td data-label="Hora">{formatDate(event.occurredAt)}</td></tr>)}
            </tbody></table></div>
          )}
        </div>
        <div>
          <h3>Ejecuciones productivas</h3>
          {!executions ? <p className="muted">Sin datos cargados.</p> : executions.length === 0 ? <p className="muted">No hay trazas para el filtro.</p> : (
            <div className="table-wrap"><table className="responsive-table"><thead><tr><th>Agente</th><th>Ruta</th><th>Resultado</th><th>Modelo</th><th>Latencia</th><th>Actor pseudónimo</th></tr></thead><tbody>
              {executions.map((trace) => <tr key={trace.traceId}><td data-label="Agente">{trace.agentId ?? "fallback"} {trace.agentVersion ? `v${trace.agentVersion}` : ""}</td><td data-label="Ruta">{trace.channel} · {trace.useCase}</td><td data-label="Resultado">{trace.outcome}<small>{trace.resolutionStatus}</small></td><td data-label="Modelo">{trace.provider ?? "—"}<small>{trace.modelId ?? "—"}</small></td><td data-label="Latencia">{formatMs(trace.durationMs)}<small>{trace.errorType ?? "sin error"}</small></td><td data-label="Actor pseudónimo">{trace.actorKey ? `${trace.actorKey.slice(0, 16)}…` : "—"}</td></tr>)}
            </tbody></table></div>
          )}
        </div>
      </div>
    </section>
  );
}

function formatDate(value: string) {
  return new Date(value).toLocaleString("es-AR", { dateStyle: "short", timeStyle: "short" });
}

function formatMs(value: number) {
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s`;
}
