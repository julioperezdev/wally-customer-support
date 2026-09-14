import { useState } from "react";
import { AgentExecutionTrace, AgentRegistryAuditEvent, createControlPlaneClient } from "./api";

type ControlPlaneClient = ReturnType<typeof createControlPlaneClient>;

export function AgentEvidencePanel({ client }: { client: ControlPlaneClient }) {
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
        <input aria-label="Filtrar evidencia por agente" placeholder="agentId opcional" value={agentId} onChange={(event) => setAgentId(event.target.value)} />
        <input aria-label="Filtrar trazas por caso de uso" placeholder="useCase opcional" value={useCase} onChange={(event) => setUseCase(event.target.value)} />
        <button onClick={() => void loadEvidence()} disabled={busy}>{busy ? "Cargando…" : "Actualizar evidencia"}</button>
      </div>
      {error && <div className="alert" role="alert">Evidencia: {error}</div>}
      <div className="evidence-grid">
        <div>
          <h3>Auditoría de cambios</h3>
          {!audit ? <p className="muted">Sin datos cargados.</p> : audit.length === 0 ? <p className="muted">No hay eventos para el filtro.</p> : (
            <div className="table-wrap"><table><thead><tr><th>Operación</th><th>Agente</th><th>Estado</th><th>Actor</th><th>Motivo</th><th>Hora</th></tr></thead><tbody>
              {audit.map((event, index) => <tr key={`${event.occurredAt}-${index}`}><td>{event.operation}</td><td>{event.agentId} {event.agentVersion ? `v${event.agentVersion}` : ""}</td><td>{event.previousState ?? "—"} → {event.resultingState ?? "—"}</td><td>{event.actorId}</td><td>{event.reason}</td><td>{formatDate(event.occurredAt)}</td></tr>)}
            </tbody></table></div>
          )}
        </div>
        <div>
          <h3>Ejecuciones productivas</h3>
          {!executions ? <p className="muted">Sin datos cargados.</p> : executions.length === 0 ? <p className="muted">No hay trazas para el filtro.</p> : (
            <div className="table-wrap"><table><thead><tr><th>Agente</th><th>Ruta</th><th>Resultado</th><th>Modelo</th><th>Latencia</th><th>Actor pseudónimo</th></tr></thead><tbody>
              {executions.map((trace) => <tr key={trace.traceId}><td>{trace.agentId ?? "fallback"} {trace.agentVersion ? `v${trace.agentVersion}` : ""}</td><td>{trace.channel} · {trace.useCase}</td><td>{trace.outcome}<small>{trace.resolutionStatus}</small></td><td>{trace.provider ?? "—"}<small>{trace.modelId ?? "—"}</small></td><td>{formatMs(trace.durationMs)}<small>{trace.errorType ?? "sin error"}</small></td><td>{trace.actorKey ? `${trace.actorKey.slice(0, 16)}…` : "—"}</td></tr>)}
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
