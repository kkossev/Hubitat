# Consolidated TODO — Aqara E1 Thermostat

Cross-checked against open GitHub issues in `kkossev/Hubitat` on 2026-08-04. This file tracks
forum/GitHub-issue-derived device-support requests and user reports — **not** the same as
`BUGS.md` in this folder, which is a separate static code-review bug work-list; keep both, they
cover different material (see `AGENTS.md`).

Topic: https://community.hubitat.com/t/release-aqara-e1-thermostat-trv-driver/128971

Status legend:

- `OPEN` — requested or reported and not implemented/resolved.
- `NEEDS_EVIDENCE` — requires a textual fingerprint, logs, or hardware confirmation.
- `RESOLVED` — implemented or confirmed working later in the topic.

## 1. `[ ]` `OPEN` — Support for an external temperature sensor

GitHub issue: https://github.com/kkossev/Hubitat/issues/119. Forum thread:
https://community.hubitat.com/t/release-aqara-e1-thermostat-trv-driver/128971/41.

**Requested outcome:** let the TRV use a temperature reading from a separate room sensor instead of
its own built-in sensor (a common TRV complaint — the valve body reads warmer than the room due to
its position on the radiator).

Known data:

- `Aqara_E1_Thermostat.groovy:140` already defines attribute `0xFCC0:0x027E` (`sensor`, `rw:'ro'`,
  values `{0:'internal',1:'external'}`) — this only **reports** which sensor the TRV's own firmware
  is currently using (Aqara's own paired external sensor via their proprietary pairing), it does not
  let this driver inject an arbitrary external reading. Confirmed read-only in `AGENTS.md`.
- No preference, command, or child-device mechanism exists to feed in a reading from a different
  Hubitat temperature device.

Implementation direction: would need a new preference/command that lets the user pick another
Hubitat temperature sensor device, and a scheduled or subscribed handler that writes that value to
the TRV's setpoint-calculation path (if the Aqara ZCL implementation even accepts an external
temperature write — needs research against `zigbee-herdsman-converters`/deconz before assuming it's
possible over Zigbee at all, as opposed to only via Aqara's own hub).
