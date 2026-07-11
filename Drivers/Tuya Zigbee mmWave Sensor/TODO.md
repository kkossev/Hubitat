# Open User Requests — Tuya Zigbee mmWave Sensor

Improvement requests harvested from all 508 posts of the community thread
([BETA] Tuya Zigbee mmWave Sensors, topic 137410) on 2026-07-11. Checked against driver
v4.2.4 / deviceProfilesV4_mmWave.json v4.1.5 before listing — everything already fixed in a
released version (per the header changelog and the JSON changelog) was dropped.
Post links are `https://community.hubitat.com/t/-/137410/<post#>`.

This list complements the two existing planning documents:
[IMPROVEMENT_PLAN](Tuya_Zigbee_mmWave_Sensor_IMPROVEMENT_PLAN.md) (profile-system robustness)
and [OPTIMIZATION_PLAN](Tuya_Zigbee_mmWave_Sensor_OPTIMIZATION_PLAN.md) (hot-path performance +
bug appendix). Items here are **user-facing feature requests and unresolved user reports** from
the forum — each needs its own analysis before implementation. Ground rules: one item at a
time, no `version()`/`timeStamp()` bumps during analysis, mark `[x]` only after the user
confirms on the dev hub. *This list has not yet been reviewed/confirmed by the user.*

---

## 1. Missing device support (fingerprints / profiles)

### 1.1 `[ ]` Promote the NEO NAS-PS10B2 custom profile into the standard JSON
TS0601 `_TZE204_1youk3hj` / `_TZE284_1youk3hj` (Haozee/NEO presence sensor with built-in
relay). A working custom profile exists ([customDeviceProfiles/deviceProfilesV4_mmWave_NAS_PS10B2.json](customDeviceProfiles/deviceProfilesV4_mmWave_NAS_PS10B2.json))
and the driver already declares its `switch`/`switchOnTime`/`switchState` attributes (added
4.2.1), but neither manufacturer string is in `deviceProfilesV4_mmWave.json` (verified absent).
Known open sub-issues from the thread testing:
- sensitivity semantics are **reversed** (0 = most sensitive, 7 = least) — fix the preference
  descriptions so users aren't misled ([#430](https://community.hubitat.com/t/-/137410/430) andrea.veroni, [#431](https://community.hubitat.com/t/-/137410/431) njanda)
- the `_TZE284` variant reports no usable illuminance (lux is a 4-step enum threshold for the
  relay, not a measurement — [#414](https://community.hubitat.com/t/-/137410/414)–[#416](https://community.hubitat.com/t/-/137410/416)); consider dropping `IlluminanceMeasurement`
- kkossev's own in-thread proposal: rename the confusing `switch` (work-mode) attribute to
  something like `workMode`/`operationMode` ([#427](https://community.hubitat.com/t/-/137410/427)) — see also OPTIMIZATION_PLAN appendix #19
  (the `switch` enum `['manual','auto']` clashes with MUVKRJR5_2's OFF/ON usage)
- Posts: [#371](https://community.hubitat.com/t/-/137410/371)–[#431](https://community.hubitat.com/t/-/137410/431) (andrea.veroni, njanda)

### 1.2 `[ ]` iHseno battery radar TS0601 `_TZE284_debczeci` — finish and promote the profile
kkossev posted a custom profile in-thread ([#446](https://community.hubitat.com/t/-/137410/446), covers `_TZE204_debczeci`,
`_TZE284_debczeci`, `_TZE284_1lvln0x6`) and promised to move it into the standard JSON once
confirmed working ([#451](https://community.hubitat.com/t/-/137410/451)). Testing stalled: motion works, but `radarSensitivity` and
`fadingTime` show `0` and have no effect ([#452](https://community.hubitat.com/t/-/137410/452)–[#456](https://community.hubitat.com/t/-/137410/456), petteri.joutseno) — note the
custom JSON declares those enum DPs with `"dt": "0x10"` (BOOL) for 3-value enums, which is a
plausible cause (should likely be ENUM8 `0x30`). Not in the standard JSON nor in
`customDeviceProfiles/` (verified absent). Another user ([#458](https://community.hubitat.com/t/-/137410/458) Jann) intends to buy this model.

### 1.3 `[ ]` Zemismart ZPS-Z1 TS0601 `_TZE284_ft7qqpx3` — unanswered request
`fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000,ED00",
outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE284_ft7qqpx3"`. The request
([#502](https://community.hubitat.com/t/-/137410/502), antti202, 2026-06-09) was never answered; the user ended up generating their own
standalone driver with ChatGPT ([#504](https://community.hubitat.com/t/-/137410/504), pastebin link in post). Not in the JSON (verified).

### 1.4 `[ ]` HOBEIAN-type TS0601 `_TZE200_vuqzj1ej` — promised, never added
inClusters `0000,0003,0500,EF00,0402,0405,0001,0400` (matches the ZG-204ZV cluster set).
kkossev received full pairing logs and replied "I will notify you when an update is available
in the coming days" ([#426](https://community.hubitat.com/t/-/137410/426), 2025-10-20) — the manufacturer string is in neither the standard
JSON nor the custom HOBEIAN JSON (verified absent). Same user also reported open issues while
running it on the HOBEIAN custom profile: `powerSource` shows battery though USB-powered,
humidity not usable by other apps, detection distance not settable ([#424](https://community.hubitat.com/t/-/137410/424), rfg81).

### 1.5 `[ ]` HOBEIAN CK-BL702-MWS-01(7016) family — profile + `humanMotionState` enum extension
- Mains 24 GHz variant (lpakula, [#311](https://community.hubitat.com/t/-/137410/311)–[#328](https://community.hubitat.com/t/-/137410/328)): works when forced to
  `TS0225_2AAELWXK_RADAR`, but 3 DPs are unmapped and the extra `humanMotionState` values
  4 = "moving away" / 5 = "moving towards" display as `null` — the driver attribute enum
  ([Tuya_Zigbee_mmWave_Sensor.groovy:92](Tuya_Zigbee_mmWave_Sensor.groovy#L92)) does not include them. lpakula posted his DP map
  in [#314](https://community.hubitat.com/t/-/137410/314) (screenshot).
- Battery motion-only variant (model `CK-BL702-MWS-01(7016)`, manufacturer `ZG-204ZE`):
  popcornhax posted a complete working custom profile in [#475](https://community.hubitat.com/t/-/137410/475) (2026-02-21) — candidate
  for direct inclusion in the standard JSON or `customDeviceProfiles/`.
- A third HOBEIAN variant was shown by mgk65 in [#315](https://community.hubitat.com/t/-/137410/315) (fingerprint only in screenshot —
  needs re-request as text).

## 2. Device-behavior fixes in existing profiles

### 2.1 `[ ]` YA4FT0W4: constrain distance preferences to the allowed 0.75 m steps
The device silently rejects min/max distances that aren't multiples of 0.75 m, so preferences
"revert". kkossev promised: "in a future update I may try to present the minimum/maximum
distances as drop-downs in the Preferences section, so that only 'allowed' values are accepted"
([#244](https://community.hubitat.com/t/-/137410/244), 2024-10-19) — never shipped. The JSON already carries `"step": 75` on those DPs but
the UI input does not enforce it. Related: `staticDetectionSensitivity` reverts to 0 unless
set via `setPar` ([#243](https://community.hubitat.com/t/-/137410/243)–[#246](https://community.hubitat.com/t/-/137410/246), [#306](https://community.hubitat.com/t/-/137410/306) bdydrp still seeing it in 2025-06).

### 2.2 `[ ]` Re-send configured parameters after device power-cycle
YA4FT0W4 (and likely other Tuya radars) reset `radarSensitivity`/`staticDetectionSensitivity`
to 0 on power loss. kkossev's own proposed solution: "intercept the device reboot event and
automatically send all the configured parameters in the driver code" ([#270](https://community.hubitat.com/t/-/137410/270), 2025-01-09) —
never implemented. The device-announce hook (`customParseZdoClusters`) and
`updateAllPreferences()` both already exist; wiring them (per-profile opt-in flag) would cover
it. Posts: [#269](https://community.hubitat.com/t/-/137410/269)–[#272](https://community.hubitat.com/t/-/137410/272) (DGBQ, worked around with a webCoRE piston).

### 2.3 `[ ]` 'Save Preferences' applies only some parameters on several devices
Multiple devices (NAS-PS10B2, YA4FT0W4, 7GCLUKJS) accept parameters one-at-a-time via
`setPar` but drop some when `updateAllPreferences()` sends them in a burst. 4.2.1 added the
per-profile `tuyaDelay` setting, but users still reported the problem afterwards
([#425](https://community.hubitat.com/t/-/137410/425), [#428](https://community.hubitat.com/t/-/137410/428) njanda; [#430](https://community.hubitat.com/t/-/137410/430) andrea.veroni: "the only effective way to be sure that a
parameter is actually set on the device is the setPar function"). Candidate approach:
sequence the writes and verify each DP echo before sending the next (andrea.veroni's
suggestion in [#397](https://community.hubitat.com/t/-/137410/397)).

### 2.4 `[ ]` **VERIFY ON DEVICE** — UXLLNYWP `ledIndicator` preference has no effect
Reported at the time the device was added ("the LED indicator is not functional, even if I
turned it off" — [#259](https://community.hubitat.com/t/-/137410/259), Televisi, 2024-11-30) and never followed up. Profile maps it to
dp 104 ([deviceProfilesV4_mmWave.json:502](deviceProfilesV4_mmWave.json#L502)); check the DP number and `dt` against a current
Z2M converter before assuming a device limitation.

### 2.5 `[ ]` **VERIFY ON DEVICE** — TUYA_RADAR_2 (`_TZE284_iadro9bf`): fadingTime rejected, no inactive report
User set fadingTime 30 → device kept reporting 60; the device also never sends motion
inactive itself (only the driver's timeout resets it). kkossev asked for a 120 s test; thread
went silent. Posts: [#366](https://community.hubitat.com/t/-/137410/366)–[#367](https://community.hubitat.com/t/-/137410/367) (rfg81, 2025-09).

### 2.6 `[ ]` ZG-204ZV: illuminance missing on some units — unanswered report
Beezer ([#482](https://community.hubitat.com/t/-/137410/482), 2026-03-27): 1 of 3 identical ZG-204ZV sensors reports illuminance, the other
2 don't (offset preference works). No reply in-thread. Needs debug logs / battery-reinsert
DP dump; possibly a firmware variant using a different illuminance DP or the ZCL 0x0400
cluster path.

### 2.7 `[ ]` Reset `distance` to 0 when motion goes inactive (FP1E-style)
"Is there a way for the TS0225 to reset to 0 targetDistance after idle/inactive like the
FP1E?" ([#474](https://community.hubitat.com/t/-/137410/474), nckepa, 2026-02-10 — unanswered). Most radars stop sending distance when
idle, so the last value stays stale on dashboards. Could be a small generic option in
`customProcessDeviceProfileEvent()`/motion-inactive handling.

## 3. Generic driver features

### 3.1 `[ ]` Driver-side `occupiedTime` for radars without native support
mark1 ([#98](https://community.hubitat.com/t/-/137410/98), 2024-07-03): expose an elapsed-occupancy timer (like the LINPTECH/Black-Square
native `occupiedTime`) for any radar, driver-computed from motion active→inactive.
`state.motionStarted` already exists (motionLib); this is mostly an event-emission question.
kkossev acknowledged the related battery request (shipped 3.2.4) but this part was never
answered.

### 3.2 `[ ]` Firmware-version-dependent preference visibility — probably "document instead"
Linptech `ledIndicator` needs device FW ≥ 1.0.6; on older FW the preference shows but does
nothing. kkossev: "Currently it is not possible to automatically enable or disable a specific
'Preference' based on the device firmware version... I will think about adding such a
possibility in the future" ([#35](https://community.hubitat.com/t/-/137410/35), 2024-05-08); a clarification note was added to the
preference text instead. Candidate **won't fix** — keep the descriptive-text approach,
document in the wiki.

### 3.3 `[ ]` HOBEIAN temperature/humidity: custom attributes vs real capabilities
4.0.2 deliberately exposed `temperature`/`humidity` as plain custom attributes (driver
[Tuya_Zigbee_mmWave_Sensor.groovy:93](Tuya_Zigbee_mmWave_Sensor.groovy#L93)/[119](Tuya_Zigbee_mmWave_Sensor.groovy#L119)), so apps that select devices by capability can't
use them ("it doesn't show as a humidity device for another app I'm trying to use" —
[#424](https://community.hubitat.com/t/-/137410/424), rfg81). Decide: per-profile capability opt-in, or document the limitation (the
companion 4-in-1 driver covers the full T/H use case).

## 4. Profile-loading UX (thread evidence for the existing plans)

### 4.1 `[ ]` "❌ Failed to download standard profiles from GitHub" shown although download+save succeeded
Rxich ([#503](https://community.hubitat.com/t/-/137410/503), [#511](https://community.hubitat.com/t/-/137410/511)): logs show `Successfully downloaded … Successfully uploaded …` followed
by the ❌ failure info event, repeatedly; still reproducible after the 4.2.3 SSL fix ("device
details still say failed, yet I see the new JSON downloaded at the exact second I clicked").
The message in `loadStandardProfilesFromGitHub()` ([deviceProfileLibV4.groovy:2331](../../Libraries/deviceProfileLibV4.groovy#L2331)) fires
whenever the in-memory load fails — even when the failure is parse/cooldown, not download —
so the wording misdiagnoses the problem for the user. Fold into IMPROVEMENT_PLAN items 5/6
(diagnostics wiring): report *which* stage failed (download / save / parse / cooldown-skip).

### 4.2 `[ ]` **VERIFY ON DEVICE** — empty Device Profiles drop-down ("No available options")
kejin ([#499](https://community.hubitat.com/t/-/137410/499), [#512](https://community.hubitat.com/t/-/137410/512)): profiles load successfully per the logs but the Preferences
drop-down stays empty. kkossev shipped a fix in 4.2.4 and asked for confirmation
([#513](https://community.hubitat.com/t/-/137410/513), 2026-07-08) — no user reply yet. Keep open until confirmed.

### 4.3 `[ ]` **VERIFY ON DEVICE** — custom-JSON profile lost after driver code re-save
andrea.veroni ([#399](https://community.hubitat.com/t/-/137410/399), 2025-10-14): editing/saving the driver code wiped the static caches
and the device needed a manual re-run of `loadUserCustomProfilesFromLocalStorage`. kkossev's
stated fix — "I will add the ensureProfilesLoaded() also at the beginning of the
customUpdated() method" ([#400](https://community.hubitat.com/t/-/137410/400)) — is **not** in the current `customUpdated()`
([Tuya_Zigbee_mmWave_Sensor.groovy:279](Tuya_Zigbee_mmWave_Sensor.groovy#L279), verified). The persistence keys
(`state.profilesV4.lastJSONSource` / `customJSONFilename`) added later may cover the scenario
via the lazy `ensureProfilesLoaded()` on the next Zigbee message — reproduce on the dev hub
(re-save driver code, then immediately Save Preferences on a custom-profile device) before
closing or fixing.

### 4.4 `[ ]` Validate the published JSON before pushing (process)
Two production incidents came from malformed published JSON: a missing comma broke every
hub's GitHub reload until fixed ([#494](https://community.hubitat.com/t/-/137410/494)–[#495](https://community.hubitat.com/t/-/137410/495), sigfreund, 2026-04), and a duplicated
`defVal` key went unnoticed because Groovy's parser tolerates it ([#379](https://community.hubitat.com/t/-/137410/379)). Add a
pre-push validation step (even a local `JsonSlurper` parse script / CI check) — this is the
enforcement mechanism for CLAUDE.md golden rule 6.

---

## Already covered elsewhere (do not duplicate)

- Cold-start fingerprint gap — device pairs as "Device" after reboot/fresh install, users must
  load profiles *and* re-save the driver ([#436](https://community.hubitat.com/t/-/137410/436)–[#443](https://community.hubitat.com/t/-/137410/443) DGBQ, [#201](https://community.hubitat.com/t/-/137410/201), [#262](https://community.hubitat.com/t/-/137410/262)) →
  IMPROVEMENT_PLAN item 1 + driver-header TODOs.
- Intermittent `JsonSlurper` parse exception after reboot ([#345](https://community.hubitat.com/t/-/137410/345), [#486](https://community.hubitat.com/t/-/137410/486)–[#493](https://community.hubitat.com/t/-/137410/493)
  user5504 — worked around with the archived 3.5.2 driver) → driver-header TODO +
  IMPROVEMENT_PLAN item 3.
- Load commands don't re-evaluate `metadata{}` fingerprints (two-step activation) →
  IMPROVEMENT_PLAN item 4 / CLAUDE.md golden rule 5.
- `switch` attribute enum `['manual','auto']` vs MUVKRJR5_2 OFF/ON mismatch →
  OPTIMIZATION_PLAN appendix #19 (the rename proposal itself is folded into item 1.1 here).
- HPM still serving 3.5.1 → deliberate: the author keeps 4.x manual-update/Repair-only until
  it leaves beta (thread top post, updated 2025-11-24). Becomes a release task, not a bug.
- humanMotionState missing `large` value, duplicated lux preferences → fixed 3.3.3.
- `_TZE204_qasjif9e` inverted motion after V4 migration → fixed in JSON 4.0.8 ([#409](https://community.hubitat.com/t/-/137410/409)).
- YA4FT0W4 missing `preProc` in V4 JSON ([#459](https://community.hubitat.com/t/-/137410/459)–[#461](https://community.hubitat.com/t/-/137410/461) lukaszbet) → merged (verified
  present, JSON v4.1.5, incl. the GKFBDVYX split).
- `_TZE204_aai5grix` fingerprint ([#479](https://community.hubitat.com/t/-/137410/479)) → added in JSON v4.1.2 (verified).
- HOBEIAN ZG-204ZM / ZG-204ZK model fingerprints ([#436](https://community.hubitat.com/t/-/137410/436), forum cross-thread) → present in
  JSON v4.1.4/4.1.5 (verified).
- Illuminance minimum reporting time preference ([#91](https://community.hubitat.com/t/-/137410/91)) → implemented as `minReportingTime`
  in illuminanceLib (verified).
- Spammy distance reports, mesh drop-offs, C-7/C-8 pairing woes, device hardware quirks
  (fadingTime not honored by firmware, sensors stuck active) → device/platform limitations;
  the driver already provides `distanceReporting`/`ignoreDistance`/spammy-DP filters.
