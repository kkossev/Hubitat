# Consolidated TODO — Tuya Zigbee Smoke Detector

Cross-checked against open GitHub issues in `kkossev/Hubitat` on 2026-08-04. This file tracks
forum/GitHub-issue-derived device-support requests and user reports — **not** the same as
`BUGS.md` in this folder, which is a separate static code-review bug work-list; keep both, they
cover different material (see `AGENTS.md`).

Topic: https://community.hubitat.com/t/release-tuya-zigbee-smoke-detector/104159

Status legend:

- `OPEN` — requested or reported and not implemented/resolved.
- `NEEDS_EVIDENCE` — requires a textual fingerprint, logs, or hardware confirmation.
- `RESOLVED` — implemented or confirmed working later in the topic.

## 1. `[ ]` `OPEN` — Add event filtering

GitHub issue: https://github.com/kkossev/Hubitat/issues/55. Forum thread:
https://community.hubitat.com/t/release-tuya-zigbee-smoke-detector/104159/97.

This is the same underlying problem as `BUGS.md` item **C9** ("`isStateChange: true` on every
battery/tamper/value event floods the event DB on each 4-hour check-in") — see that file for the
full analysis and the `ASK USER` design question (smoke/gas alarms deliberately always fire;
battery/tamper/value don't need to). Resolve both together, not as separate fixes.

## 2. `[ ]` `OPEN` — Add Moes/Heiman HS-720ES Carbon Monoxide Alarm

GitHub issue: https://github.com/kkossev/Hubitat/issues/135. Forum thread:
https://community.hubitat.com/t/moes-zigbee-carbon-monoxide-alarm-hs-720es/161764.

Known data: model `TS0601`, manufacturer `_TZE284_rjxqso4a`. No fingerprint for this manufacturer
found anywhere in the repo (verified). kkossev told the reporter in-thread "your particular device
is not in it yet. The self-test feature will not work with it" and provided a separate one-off
custom driver outside this repo as a stopgap.

Implementation direction: needs the DP map for this specific CO alarm (likely similar to the
existing gas-sensor DP handling, but CO alarms commonly report ppm rather than a simple
alarm/no-alarm enum — verify against `zigbee-herdsman-converters` before assuming the gas-sensor
template applies as-is).
