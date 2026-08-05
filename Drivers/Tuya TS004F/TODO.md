# Consolidated TODO — Tuya Scene Switch TS004F

Cross-checked against open GitHub issues in `kkossev/Hubitat` on 2026-08-04. This file tracks
forum/GitHub-issue-derived device-support requests and user reports — **not** the same as
`BUGS.md` in this folder, which is a separate static code-review bug work-list; keep both, they
cover different material (see `AGENTS.md`).

Topic: https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver-w-healthstatus/92823

Status legend:

- `OPEN` — requested or reported and not implemented/resolved.
- `NEEDS_EVIDENCE` — requires a textual fingerprint, logs, or hardware confirmation.
- `RESOLVED` — implemented or confirmed working later in the topic.
- `DEVICE_LIMITATION` — cannot be implemented using known device/firmware/hub behavior.

## 1. `[ ]` `OPEN` — TS0601 `_TZE200_nojsjtj2` SOS button not working

GitHub issues: https://github.com/kkossev/Hubitat/issues/50,
https://github.com/kkossev/Hubitat/issues/71 (duplicate reports, same device).

**User-visible problem:** device pairs but does not send usable button-press events.

Known data:

- `TS004F.groovy:206` already has a fingerprint (`model:'TS0601', manufacturer:'_TZE200_nojsjtj2'`),
  added while testing, explicitly commented `// NOT WORKING!` and referenced in the header changelog
  (v2.8.4: "testing TS0601 _TZE200_nojsjtj2 SOS button (not working for now)").
- `isSOSbutton()` (line 280) already includes this manufacturer string.

Implementation direction: needs a live debug-log capture of what the device actually sends on button
press (if anything) before further guessing — this looks like it may need Tuya EF00 DP handling
rather than the standard IAS/On-Off path the rest of `isSOSbutton()` devices use.

## 2. `[ ]` `OPEN` — Add TS0041 `_TZ3001_2kjvanir`

GitHub issue: https://github.com/kkossev/Hubitat/issues/83.

No fingerprint for this manufacturer string found anywhere in the driver (verified via repo-wide
search). Needs the textual fingerprint (model/manufacturer/inClusters/outClusters) before a
fingerprint line can be added — model is reportedly `TS0041` (single button), which this driver
already broadly supports for other manufacturers.

## 3. `DEVICE_LIMITATION` — TS0601 `_TZE284_2baujqot` SOS button on Hubitat C-7

GitHub issue: https://github.com/kkossev/Hubitat/issues/146. Forum thread:
https://community.hubitat.com/t/assistance-with-new-button/163745.

Reporter's device paired incorrectly to a Hubitat C-7 hub and never sent recognizable Zigbee
messages, even with this driver installed. kkossev determined the device "will not work when paired
directly to HE" on that hub generation — 2026-era Tuya buttons of this type reportedly need a
Zigbee-3.0-compliant coordinator (C-8 or later), not a driver-side fix. No further action possible
without a newer hub to test against.
