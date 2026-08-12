# Region Properties

**Language:** **English** | [Türkçe](i18n/tr.md#region-properties) | [简体中文](i18n/zh-CN.md#region-properties) | [Español](i18n/es.md#region-properties) | [Deutsch](i18n/de.md#region-properties) | [Русский](i18n/ru.md#region-properties) | [Bahasa Indonesia](i18n/id.md#region-properties) | [हिन्दी](i18n/hi.md#region-properties) | [العربية](i18n/ar.md#region-properties)

## Purpose

Region Properties provides an optional bounded China region view for applications and vendor components that read a small set of Android properties.

## Applied view

The module uses fixed property names for hardware country, SIM country, operator country, hardware level, and a radio compatibility marker. Values are fixed in the early boot script rather than accepted as arbitrary user input.

The feature runs before Zygote when Spoof Engine is enabled. Automatic Boot Properties policy can still defer changes on a sensitive vendor family or when another property owner is active.

## Limits and recovery

This control does not change the real SIM country, radio registration, modem firmware, sales region stored in secure hardware, or carrier account. Applications may combine properties with other evidence.

If a vendor service behaves differently, disable Region Properties and reboot. Use the general Boot Properties mode to disable all CleveresTricky early property changes when a broader diagnostic baseline is needed.

[Return to the project overview](../README.md)
