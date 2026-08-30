# Historial de funciones y trabajos previos

Este documento registra parte del historial de desarrollo de CleveresTricky para facilitar la trazabilidad y la atribución. Por sí solo no afirma que otro proyecto haya copiado código fuente.

## Identidad del dispositivo y attestation

- **#79 — Configuración por aplicación y gestión de `ATTESTATION_ID_*` (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79
- **#139 — Identidad de dispositivo aleatoria (2026-02-05)**
  https://github.com/tryigit/CleveresTricky/pull/139
- **#871 — Controles de identidad de dispositivo/Dual-SIM por aplicación (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/871
  Incluye IMEI, IMEI2, MEID, IMSI, ICCID, número de teléfono y Serial, además de alcance por aplicación/perfil y ciclo de vida en runtime.

## Keybox / attestation

- **#77 — Gestión y rotación de múltiples keyboxes (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/77
- **#79 — Verificación de Keybox y trabajo de identidad de attestation**
  https://github.com/tryigit/CleveresTricky/pull/79

## Arquitectura Native / Rust

- **#876 — Arquitectura de interceptores Rust/Native y ciclo de vida (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/876

## Otras funciones del módulo

El proyecto también ha desarrollado perfiles/templates, alcance por aplicación, ciclo de vida de hooks, aislamiento/redacción de identidad, funciones relacionadas con RKP/DRM, gestión WebUI e integración StrongBox/attestation.

- #376 — https://github.com/tryigit/CleveresTricky/pull/376
- #476 — https://github.com/tryigit/CleveresTricky/pull/476
- #618 — https://github.com/tryigit/CleveresTricky/pull/618
- #908 — https://github.com/tryigit/CleveresTricky/pull/908
- #909 — https://github.com/tryigit/CleveresTricky/pull/909
- #910 — https://github.com/tryigit/CleveresTricky/pull/910
- #952 — https://github.com/tryigit/CleveresTricky/pull/952
- **#1132 — Redirección de StrongBox a TEE y armonización del nivel de seguridad de attestation**
  https://github.com/tryigit/CleveresTricky/pull/1132
  Este cambio fue revertido posteriormente y no forma parte del `master` actual.

## Nota histórica

Los enlaces anteriores son registros públicos directos del historial de desarrollo. La similitud funcional entre proyectos no demuestra por sí sola copia de código ni una infracción de licencia.
