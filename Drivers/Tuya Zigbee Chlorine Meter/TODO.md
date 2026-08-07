# Consolidated TODO — Tuya Zigbee Chlorine Meter

Cross-checked against open GitHub issues in `kkossev/Hubitat` on 2026-08-04.

Topic: https://community.hubitat.com/t/tuya-chlorine-meter-driver/158250

Status legend:

- `OPEN` — requested or reported and not implemented/resolved.
- `NEEDS_EVIDENCE` — requires a textual fingerprint, logs, or hardware confirmation.
- `RESOLVED` — implemented or confirmed working later in the topic.

## 1. `[ ]` `OPEN` — pH reading scale factor appears wrong

GitHub issue: https://github.com/kkossev/Hubitat/issues/65. Forum thread:
https://community.hubitat.com/t/tuya-chlorine-meter-driver/158250/3.

**User-visible problem:** reported pH values don't look right (reporter suspects the wrong divisor).

Diagnosis (2026-08-04, from reading `Tuya Zigbee Chlorine Meter.groovy:135`): the live `ph` reading
(dp:10) uses `scale:100`, but its own `phMmax`/`phMmin` limit attributes (dp:106/107, lines 142-143)
use `scale:10` — an internal inconsistency between the live value and the min/max bounds it's
compared against. The CHANGELOG only documents a `freeChlorine` divide-by-10 fix (v3.3.2); no entry
mentions a pH scale fix, so this looks genuinely unaddressed.

Implementation direction: confirm the correct scale for dp:10 against a raw Tuya DP log or
`zigbee-herdsman-converters`, then make `ph`/`phMmax`/`phMmin` consistent (likely all `scale:10`,
matching the limits, but verify — don't guess which side is wrong without a real reading to check
against a known pH test strip value).
