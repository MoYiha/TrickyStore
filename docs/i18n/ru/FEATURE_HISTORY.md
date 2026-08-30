# История функций и предшествующие разработки

Этот документ фиксирует историю отдельных функций CleveresTricky для прозрачности и атрибуции. Сам по себе он не является утверждением о копировании исходного кода другим проектом.

## Идентичность устройства и attestation

- **#79 — Конфигурация для приложений и обработка `ATTESTATION_ID_*` (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79
- **#139 — Случайная идентичность устройства (2026-02-05)**
  https://github.com/tryigit/CleveresTricky/pull/139
- **#871 — Управление идентичностью устройства/Dual-SIM на уровне приложения (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/871
  Включает IMEI, IMEI2, MEID, IMSI, ICCID, номер телефона и Serial, а также область приложения/профиля и runtime lifecycle.

## Keybox / attestation

- **#77 — Управление и ротация нескольких keybox (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/77
- **#79 — Проверка Keybox и работа с идентичностью attestation**
  https://github.com/tryigit/CleveresTricky/pull/79

## Native / Rust архитектура

- **#876 — Архитектура Rust/Native interceptor и lifecycle (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/876

## Другие функции модуля

Проект также развивал профили/templates, область приложения, lifecycle hooks, изоляцию/redaction идентичности, функции RKP/DRM, управление WebUI и интеграцию StrongBox/attestation.

- #376 — https://github.com/tryigit/CleveresTricky/pull/376
- #476 — https://github.com/tryigit/CleveresTricky/pull/476
- #618 — https://github.com/tryigit/CleveresTricky/pull/618
- #908 — https://github.com/tryigit/CleveresTricky/pull/908
- #909 — https://github.com/tryigit/CleveresTricky/pull/909
- #910 — https://github.com/tryigit/CleveresTricky/pull/910
- #952 — https://github.com/tryigit/CleveresTricky/pull/952
- **#1132 — Перенаправление StrongBox в TEE и согласование уровня безопасности attestation**
  https://github.com/tryigit/CleveresTricky/pull/1132
  Это изменение впоследствии было отменено и поэтому не входит в текущий `master`.

## Историческая заметка

Ссылки выше ведут непосредственно на публичные записи GitHub. Сходство функций само по себе не доказывает копирование исходного кода или нарушение лицензии.
