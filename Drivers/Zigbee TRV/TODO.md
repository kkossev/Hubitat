# Consolidated TODO — Zigbee TRV folder

Cross-checked against open GitHub issues in `kkossev/Hubitat` on 2026-08-04. This file tracks
forum/GitHub-issue-derived device-support requests and user reports — **not** the same as
`BUGS.md` in this folder, which is a separate static code-review bug work-list; keep both, they
cover different material. This folder holds multiple drivers (Fibaro TRV FGT-001, Namron Zigbee
Thermostat, Sonoff Zigbee TRV, Tuya Zigbee Thermostat, Zigbee TRVs and Thermostats (Misc)) — see
`AGENTS.md` for the layout. Items below are scoped to the specific driver named in each entry.

The Tuya thermostat forum topic 128916 was fully reviewed through post 178 on 2026-08-15.

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

## 2. `[ ]` `NEEDS_EVIDENCE` — MOES ZHT-S03 battery and power source

Forum thread: https://community.hubitat.com/t/-/128916/170.

Driver: `Tuya Zigbee Thermostat.groovy`, profile `MOES_ZHT_S03_THERMOSTAT`.

The newly added profile's hysteresis behavior was confirmed working, but the reporter said battery
never reports and Hubitat still shows `powerSource` as `ac`. Obtain a complete debug capture after a
fresh battery insertion/check-in and verify the device's actual battery DP before changing the
profile. **VERIFY ON DEVICE** — do not infer a DP from another TS0601 thermostat family.

## 3. `[ ]` `NEEDS_EVIDENCE` — ENGO EONE-BATW support

Forum thread: https://community.hubitat.com/t/-/128916/171.

The public thread does not contain enough fingerprint/DP evidence to add a safe profile. Request
the textual manufacturer/model data, Tuya DP debug logs for every control, and the modified driver
profile discussed later in the thread.

## 4. `[ ]` `NEEDS_EVIDENCE` — `_TZE204_cvub6xbb` Vancoo/Beok thermostat profile

Forum thread: https://community.hubitat.com/t/-/128916/175.

The device pairs against a nearby profile but exposes mismatched behavior. Capture its complete
fingerprint and DP traffic for mode, setpoint, local temperature, operating state, and preferences;
create a separate profile if its DP semantics differ. **VERIFY ON DEVICE**.
