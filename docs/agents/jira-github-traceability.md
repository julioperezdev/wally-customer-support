# Skill: trazabilidad Jira–GitHub — WCS

Owner: Tech Lead  
Status: `Accepted`  
Last reviewed: 2026-08-30  
Related Jira: `WCS-7`, `WCS-13`, `WCS-22`  
Related repository paths: `.github/`, `CONTRIBUTING.md`, `docs/agents/`  
Decision/source: integración GitHub for Atlassian validada con `WCS-13`

## Propósito

Mantener una relación automática y auditable entre cada issue Jira `WCS-*` y su trabajo técnico en GitHub: branch, commits, Pull Request, CI, deployment y evidencia.

La integración de GitHub con Jira debe estar configurada una sola vez por el administrador de Jira y el propietario de la cuenta/organización de GitHub. El skill no reemplaza esa configuración: define cómo deben trabajar los agentes y desarrolladores después de conectarla.

## Cuándo usarlo

Usar este skill para cualquier cambio de código, migración, documentación técnica versionada, configuración CI/CD o infraestructura asociada a una issue `WCS-*`.

No usar una issue genérica para ocultar trabajo. Si no existe una issue adecuada, detenerse y crear o solicitar una antes de modificar el repositorio.

## Precondiciones

1. Confirmar la key exacta de Jira, por ejemplo `WCS-13`.
2. Revisar alcance, fuera de alcance, dependencias, pruebas y evidencia de la issue.
3. Confirmar que la issue está habilitada para comenzar según el workflow del proyecto.
4. Verificar que GitHub for Atlassian tiene acceso al repositorio `wally-customer-support`.
5. No incluir secretos, tokens, credenciales, payloads completos ni PII en branches, commits, PRs o fixtures.

## Convenciones obligatorias

### Branch

La key debe aparecer al comienzo del nombre:

```text
feature/WCS-123-descripcion-corta
fix/WCS-123-descripcion-corta
docs/WCS-123-descripcion-corta
chore/WCS-123-descripcion-corta
```

Usar una branch por unidad de trabajo. La branch principal es `main` y no se trabaja directamente sobre ella.

### Commit

La key debe aparecer al comienzo del mensaje, en mayúsculas y con el formato exacto de Jira:

```text
WCS-123 add durable message outbox
WCS-124 validate Meta webhook signature
```

El mensaje debe describir un cambio verificable, en modo imperativo y sin incluir secretos ni datos reales. No usar una key de otra issue sólo para forzar una relación.

### Pull Request

La key debe aparecer en el título y en la descripción:

```text
[WCS-123] Add durable message outbox
```

La descripción mínima debe incluir objetivo, alcance, fuera de alcance, pruebas, documentación, riesgos, rollout/rollback y evidencia. El PR debe apuntar a `main` y tener la issue correcta vinculada.

## Flujo operativo

1. Confirmar la issue y su estado antes de trabajar.
2. Crear la branch desde `main` actualizado.
3. Mantener la key en todos los commits relevantes.
4. Ejecutar tests y validaciones proporcionales al riesgo.
5. Hacer push de la branch al repositorio remoto.
6. Abrir el PR con la key en el título.
7. Revisar en Jira el panel **Development** y confirmar branch, commits y PR.
8. Mover la issue a `In Review` sólo cuando el PR o la revisión documental y la evidencia estén disponibles.
9. Moverla a `Done` sólo después de aceptación, merge y deploy/smoke cuando corresponda.

## Comandos de referencia

Reemplazar `WCS-123` y el slug por la issue real:

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/WCS-123-descripcion-corta

git add <archivos-revisados>
git commit -m "WCS-123 implement agreed change"
git push --set-upstream origin feature/WCS-123-descripcion-corta
```

## Commits que abarcan varias issues

Preferir separar el trabajo. Si un commit realmente resuelve más de una issue, incluir todas las keys relevantes y mantener primero la issue principal:

```text
WCS-123 WCS-124 share message processing contract
```

No asociar issues no relacionadas sólo para aumentar la trazabilidad.

## Verificación de la relación

La relación se considera verificable cuando:

- la branch fue subida a GitHub;
- el commit remoto contiene la key exacta;
- el PR contiene la key en título o branch fuente;
- Jira muestra el desarrollo en el panel **Development**;
- la issue contiene los enlaces a PR, CI y evidencia cuando aplican.

Si no aparece en Jira, revisar en este orden:

1. la branch/commit/PR fue realmente subida al remoto;
2. la key coincide exactamente y está en mayúsculas;
3. GitHub for Atlassian tiene seleccionado este repositorio;
4. la aplicación tiene permisos de lectura sobre metadata, commits, branches y PRs;
5. actualizar Jira y esperar la sincronización/backfill;
6. registrar el hallazgo en la issue sin afirmar que quedó vinculado hasta verlo.

## Reglas de seguridad y evidencia

- El hash del commit y las URLs de GitHub/Jira sí son evidencia válida.
- Los logs de CI deben estar sanitizados.
- Nunca guardar access tokens, app secrets, passwords, firmas, números completos o conversaciones reales.
- No reescribir historia ya subida para ocultar un error; corregir con un commit explícito y documentarlo.
- La relación Jira–GitHub no sustituye code review, tests ni la Definition of Done.
