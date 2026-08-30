# 功能历史与先前工作

本文档记录 CleveresTricky 部分主要功能的公开开发历史，用于提供清晰的时间线和归属信息。本文档本身不主张其他项目复制了源代码。

## 设备身份与 Attestation

- **#79 — 应用级配置与 `ATTESTATION_ID_*` 处理（2026-02-01）**
  https://github.com/tryigit/CleveresTricky/pull/79
- **#139 — 随机设备身份（2026-02-05）**
  https://github.com/tryigit/CleveresTricky/pull/139
- **#871 — 应用级 Dual-SIM/设备身份控制（2026-08-09）**
  https://github.com/tryigit/CleveresTricky/pull/871
  包括 IMEI、IMEI2、MEID、IMSI、ICCID、电话号码和 Serial，以及应用/配置文件范围和运行时生命周期处理。

## Keybox / Attestation

- **#77 — 多 Keybox 管理与轮换（2026-02-01）**
  https://github.com/tryigit/CleveresTricky/pull/77
- **#79 — Keybox 验证与 Attestation 身份工作**
  https://github.com/tryigit/CleveresTricky/pull/79

## Native / Rust 架构

- **#876 — Rust/Native interceptor 架构与生命周期（2026-08-09）**
  https://github.com/tryigit/CleveresTricky/pull/876

## 其他模块功能

项目还逐步加入了 profile/template 管理、应用范围控制、runtime hook 生命周期、身份隔离/隐藏、RKP/DRM 相关处理、WebUI 管理以及 StrongBox/Attestation 集成。

- #376 — https://github.com/tryigit/CleveresTricky/pull/376
- #476 — https://github.com/tryigit/CleveresTricky/pull/476
- #618 — https://github.com/tryigit/CleveresTricky/pull/618
- #908 — https://github.com/tryigit/CleveresTricky/pull/908
- #909 — https://github.com/tryigit/CleveresTricky/pull/909
- #910 — https://github.com/tryigit/CleveresTricky/pull/910
- #952 — https://github.com/tryigit/CleveresTricky/pull/952
- **#1132 — StrongBox → TEE 重定向与 Attestation 安全级别统一**
  https://github.com/tryigit/CleveresTricky/pull/1132
  此更改后来被 revert，因此当前 `master` 不包含它。

## 历史说明

以上链接均直接指向 GitHub 的公开开发记录。不同项目具有相似功能本身并不能证明源代码复制或许可证违规。
