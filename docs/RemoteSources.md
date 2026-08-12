# Remote Sources

**Language:** **English** | [Türkçe](i18n/tr.md#remote-sources) | [简体中文](i18n/zh-CN.md#remote-sources) | [Español](i18n/es.md#remote-sources) | [Deutsch](i18n/de.md#remote-sources) | [Русский](i18n/ru.md#remote-sources) | [Bahasa Indonesia](i18n/id.md#remote-sources) | [हिन्दी](i18n/hi.md#remote-sources) | [العربية](i18n/ar.md#remote-sources)

## Purpose

Remote Sources retrieves authorized keybox material from an explicitly configured secure endpoint. Retrieved data remains untrusted until every local validation step succeeds.

## Connection policy

Only HTTPS endpoints are accepted. Host, port, path, timeout, refresh interval, authentication type, header name, and response size are bounded. Redirect and connection behavior is restricted by the configured client policy.

Supported authentication data is stored in the protected configuration directory. The WebUI does not include secrets in status responses. Removing a source also removes its stored configuration and loaded material.

## Signature and content validation

A source can require signed content. When signature verification is configured, an invalid or missing signature rejects the update. XML and encrypted container input must also pass format, size, keybox, certificate, and revocation checks.

A failed refresh does not turn invalid bytes into active key material. Existing verified material remains separated from the failed download, and the status reports the error for diagnosis.

## Operational guidance

Use an endpoint you control or trust. Rotate authentication data independently from key material. Review source status after changing a certificate, public key, or server policy.

[Return to the project overview](../README.md)
