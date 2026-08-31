# Bootstrap de infraestructura

El bootstrap de WCS no crea automáticamente un bucket nuevo de estado ni
adopta recursos de otra cuenta. El stack `prod` referencia inicialmente el
bucket remoto observado en `tesis-dev` mediante una key propia.

Antes de inicializar el backend remoto, confirmar explícitamente:

1. cuenta y rol AWS activos;
2. región objetivo;
3. bucket de estado y soporte de `use_lockfile`;
4. permisos de lectura/escritura del estado;
5. que `wally-customer-support/environments/prod/terraform.tfstate` no exista
   o sea el estado correcto de este repositorio.

Si el bucket no pertenece a la cuenta/organización objetivo, crear un bootstrap
aislado y aprobado para esa cuenta antes de continuar. No copiar el state ni
los `tfvars` de `tesis-dev`.
