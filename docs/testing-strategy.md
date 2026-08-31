# Estrategia de testing — WCS

## Capas

| Capa | Cubre | Evidencia |
|---|---|---|
| Unit | Parser, HMAC, deduplicación, ventana, contexto, mapeos | Reporte JUnit |
| Application | Orquestación y políticas con mocks | Reporte JUnit |
| Contract | Payloads de Meta y respuestas de clientes | Fixtures versionados |
| Integration | PostgreSQL, Flyway, ownership e idempotencia | Testcontainers |
| E2E mock | Inbound → LLM mock → outbound | Log de correlación |
| E2E Meta | Número de prueba y webhook HTTPS | Evidencia sanitizada |
| Operational | Health, readiness, retry, rollback y alertas | Runbook + CI |

## Casos iniciales

- `TC-001`: challenge válido.
- `TC-002`: challenge inválido.
- `TC-003`: firma válida.
- `TC-004`: firma ausente.
- `TC-005`: firma inválida.
- `TC-006`: payload de texto válido.
- `TC-007`: evento de estado ignorado.
- `TC-008`: mensaje duplicado.
- `TC-009`: contexto limitado enviado al LLM.
- `TC-010`: respuesta vacía rechazada.
- `TC-011`: fallo transitorio reintentado tres veces como máximo.
- `TC-012`: respuesta fuera de ventana.
- `TC-013`: ciclo completo con mocks.
- `TC-014`: migraciones en PostgreSQL limpio.
- `TC-015`: endpoint interno no disponible en producción.

## Gate de aceptación

No alcanza con que compile. Cada issue debe declarar qué casos prueba, qué riesgo cubren y dónde queda la evidencia.

## Evidencia de WCS-13

- `mvn clean test`: tests unitarios de HMAC, parser, controller, servicio de aplicación y adapter Meta.
- `WallyCustomerSupportApplicationTest`: arranque Spring Boot con JPA, migración Flyway V1 y adapters mock usando H2 compatible con PostgreSQL.
- La prueba de integración PostgreSQL real se ejecuta con `docker compose up -d postgres`; Testcontainers se incorpora en la siguiente iteración de integración.
