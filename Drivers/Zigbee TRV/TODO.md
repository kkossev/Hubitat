# Consolidated TODO — Zigbee TRV folder

Cross-checked against open GitHub issues in `kkossev/Hubitat` on 2026-08-04. This file tracks
forum/GitHub-issue-derived device-support requests and user reports — **not** the same as
`BUGS.md` in this folder, which is a separate static code-review bug work-list; keep both, they
cover different material. This folder holds multiple drivers (Fibaro TRV FGT-001, Namron Zigbee
Thermostat, Sonoff Zigbee TRV, Tuya Zigbee Thermostat, Zigbee TRVs and Thermostats (Misc)) — see
`AGENTS.md` for the layout. Items below are scoped to the specific driver named in each entry.

Status legend:

- `OPEN` — requested or reported and not implemented/resolved.
- `NEEDS_EVIDENCE` — requires a textual fingerprint, logs, or hardware confirmation.
- `RESOLVED` — implemented or confirmed working later in the topic.

## 1. `[ ]` `OPEN` — Tuya Zigbee Thermostat: temperature formatting

GitHub issue: https://github.com/kkossev/Hubitat/issues/132. Forum thread:
https://community.hubitat.com/t/release-tuya-zigbee-thermostats-and-trvs-driver/128916/160.

Driver: `Tuya Zigbee Thermostat.groovy` (source) / `Tuya_Zigbee_Thermostat_lib_included.groovy`
(amalgam), both via `thermostatLib`.

Known data: temperature values already go through a rounding/formatting step (thermostatLib,
`.setScale(1, BigDecimal.ROUND_HALF_UP)` before `sendEvent`) — cannot confirm from the code alone
whether this already addresses the reporter's specific complaint, or whether it's about a different
formatting aspect (e.g. decimal-place count, unit suffix, or a specific attribute not going through
that path). Needs the actual forum post content re-checked against current driver behavior on a
live device before deciding if this is still open.
