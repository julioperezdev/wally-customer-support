import { useEffect, useState } from "react";
import { BackofficeOrder, BackofficeOrderPage, createBackofficeClient } from "./api";

type BackofficeClient = ReturnType<typeof createBackofficeClient>;

export function OrderPanel({ client }: { client: BackofficeClient }) {
  const [page, setPage] = useState<BackofficeOrderPage | null>(null);
  const [status, setStatus] = useState("");
  const [sku, setSku] = useState("RP-REM-NP-NEG-M");
  const [quantity, setQuantity] = useState("1");
  const [customerReference, setCustomerReference] = useState("demo-telegram");
  const [createdOrder, setCreatedOrder] = useState<BackofficeOrder | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadOrders() {
    setBusy(true);
    setError(null);
    try {
      setPage(await client.listOrders(status));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo cargar la cola de pedidos.");
    } finally {
      setBusy(false);
    }
  }

  async function createOrder() {
    const parsedQuantity = Number.parseInt(quantity, 10);
    if (!sku.trim() || !Number.isInteger(parsedQuantity) || parsedQuantity < 1) {
      setError("Indicá un SKU y una cantidad válida.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const order = await client.createOrder({
        customerReference: customerReference.trim() || undefined,
        items: [{ sku: sku.trim(), quantity: parsedQuantity }]
      });
      setCreatedOrder(order);
      await loadOrders();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo crear el pedido.");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    void loadOrders();
    // The client is stable for the current token; a manual refresh is explicit.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [client]);

  return (
    <section className="card">
      <div className="section-heading">
        <div>
          <h2>Pedidos y pagos</h2>
          <p>Venta asistida MVP: valida stock, crea un pedido idempotente y muestra el link del proveedor.</p>
        </div>
        <span className="security-note">SANDBOX / MOCK</span>
      </div>
      {error && <div className="alert" role="alert">Pedidos: {error}</div>}
      <div className="order-create-grid">
        <label>SKU<input value={sku} onChange={(event) => setSku(event.target.value)} /></label>
        <label>Cantidad<input inputMode="numeric" value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label>
        <label>Referencia del cliente<input value={customerReference} onChange={(event) => setCustomerReference(event.target.value)} /></label>
        <div className="button-row order-actions">
          <button className="primary" onClick={() => void createOrder()} disabled={busy}>Crear pedido y link</button>
          <button onClick={() => void loadOrders()} disabled={busy}>Actualizar pedidos</button>
        </div>
      </div>
      {createdOrder && <OrderResult order={createdOrder} />}
      <div className="section-heading order-list-heading">
        <h3>Pedidos recientes</h3>
        <select aria-label="Filtrar pedidos por estado" value={status} onChange={(event) => setStatus(event.target.value)}>
          <option value="">Todos los estados</option>
          <option value="PENDING_PAYMENT">Pendiente de pago</option>
          <option value="PAID">Pagado</option>
          <option value="REJECTED">Rechazado</option>
          <option value="CANCELLED">Cancelado</option>
          <option value="EXPIRED">Expirado</option>
        </select>
      </div>
      {!page ? <p className="muted">La vista de pedidos no está disponible todavía.</p> : page.items.length === 0 ?
        <p className="muted">No hay pedidos para el filtro seleccionado.</p> :
        <div className="table-wrap"><table><thead><tr><th>Pedido</th><th>Estado</th><th>Total</th><th>Pago</th><th>Creado</th></tr></thead><tbody>
          {page.items.map((order) => <tr key={order.id}>
            <td><strong>{shortId(order.id)}</strong><small>{order.customerReference ?? "sin referencia"}</small></td>
            <td className={order.status === "PAID" ? "positive" : order.status === "REJECTED" ? "negative" : "warning"}>{order.status}</td>
            <td>{formatMoney(order.total, order.currency)}</td>
            <td>{order.paymentUrl ? <a href={order.paymentUrl} target="_blank" rel="noreferrer">Abrir link</a> : "Pendiente"}<small>{order.paymentProvider}</small></td>
            <td>{new Date(order.createdAt).toLocaleString("es-AR")}</td>
          </tr>)}
        </tbody></table></div>}
    </section>
  );
}

function OrderResult({ order }: { order: BackofficeOrder }) {
  return <div className="success-alert order-result">
    Pedido <strong>{shortId(order.id)}</strong> creado · {formatMoney(order.total, order.currency)} · {order.status}.
    {order.paymentUrl && <> <a href={order.paymentUrl} target="_blank" rel="noreferrer">Abrir link de pago</a></>}
  </div>;
}

function shortId(id: string) {
  return id.slice(0, 8);
}

function formatMoney(value: number, currency: string) {
  return `${value.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ${currency}`;
}
