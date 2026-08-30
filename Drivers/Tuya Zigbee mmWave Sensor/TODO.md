# Open User Requests — Tuya Zigbee mmWave Sensor

Improvement requests and unresolved reports harvested from all 515 visible posts through highest
visible post 520 of the community thread ([BETA] Tuya Zigbee mmWave Sensors, topic 137410) on
2026-08-30. Deleted posts account for the difference between the visible-post count and highest
post number. Checked against driver v4.2.5 / deviceProfilesV4_mmWave.json v4.1.6 before listing —
everything already fixed in a released version (per the header changelog and the JSON changelog)
was dropped.
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
GitHub issue: https://github.com/kkossev/Hubitat/issues/45.
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

### 1.6 `[ ]` Tuya Smart Human Presence Sensor TS0601 `_TZE204_eaulras5` — dual motion/presence radar
GitHub issue: https://github.com/kkossev/Hubitat/issues/47. Forum thread:
https://community.hubitat.com/t/tuya-smart-human-presence-sensor-detect-human-motion-detector-zigbee-ts0601-tze204-eaulras5/145464.
kkossev indicated in-thread the device could be added to this driver; not present in
`deviceProfilesV4_mmWave.json` (verified absent). Reporter got some functionality working via a
manually-forced profile, with `fadingTime`/sensitivity tuning needed for stationary-person presence
— worth confirming which existing profile they used before writing a dedicated one.

### 1.7 `[ ]` Add ThirdReality R1 mmWave sensor
GitHub issue: https://github.com/kkossev/Hubitat/issues/70. Forum thread:
https://community.hubitat.com/t/re-support-for-new-third-reality-r1-mmwave-sensor/159974. Not in
`deviceProfilesV4_mmWave.json` (verified absent) — no known fingerprint or DP map captured yet;
needs a textual fingerprint and DP log before a profile can be written.

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
GitHub issue: https://github.com/kkossev/Hubitat/issues/133.
"Is there a way for the TS0225 to reset to 0 targetDistance after idle/inactive like the
FP1E?" ([#474](https://community.hubitat.com/t/-/137410/474), nckepa, 2026-02-10 — unanswered). Most radars stop sending distance when
idle, so the last value stays stale on dashboards. Could be a small generic option in
`customProcessDeviceProfileEvent()`/motion-inactive handling.

### 2.8 `[~]` `PARTIAL` — SNZB-06P24 zone-enable writes can disable all motion — HUB-129

Outgoing-write fix **Confirmed on device** on the maintainer's firmware `1.0.0` unit (2026-08-30),
together with the scope of the quirk (BITMAP16 only) and the `zoneStatus` read path (2.9). Still
open: the `setZone` / `setAllZones` commands end to end, and the reporter's firmware `1.0.4` device.

**User-visible problem:** on a SONOFF `SNZB-06P24` running firmware `1.0.4`, saving Preferences
with `zoneEnable = 255` stops all motion reports even though Refresh subsequently displays all
zones as enabled. The same outgoing write path is used by the `setZone` and `setAllZones`
commands.

Evidence:

- Exact textual fingerprint from [#514](https://community.hubitat.com/t/-/137410/514), kdb:
  `profileId:"0104", endpointId:"01", inClusters:"0000,0003,0400,0406,FC11,FC57",`
  `outClusters:"0003,0019", model:"SNZB-06P24", manufacturer:"SONOFF"`.
- [#518](https://community.hubitat.com/t/-/137410/518) records an `0xFC11:0x2016`
  BITMAP16 read response with on-wire bytes `00 FF`, which Hubitat correctly decodes as
  `0xFF00` / `65280`.
- [#519](https://community.hubitat.com/t/-/137410/519) added the then-current receive-side byte
  swap. [#520](https://community.hubitat.com/t/-/137410/520), kdb, confirms that this makes Refresh
  look correct but does not prevent Save Preferences from disabling every zone. The reporter's
  outgoing-write workaround was preliminarily working and a PR was offered, but no matching PR was
  present when checked on 2026-08-30.
- **On-device confirmation of the fix** — maintainer's unit, device `BE94`, firmware `1.0.0`
  (`firmwareMT 1286-0812-00001000`), C-8 Pro 2.5.1.176, driver 4.2.5 timestamp
  `2026/08/30 07:38 AM`, hub log 2026-08-30 08:21:44–08:21:45. Save Preferences with
  `zoneEnable = 255` produced:

  ```
  setPar: found customSetFunction=null, scaledValue=255 (val=255)
  setPar: (1) successfluly executed setPar customSetZoneEnable(255)
  sendZigbeeCommands: sent cmd=[he wattr 0xBE94 0x01 0xFC11 0x2016 0x19 {FF00} {1286}, ...]
  zigbee response write private cluster 0xFC11 attribute response: Success
  parse: descMap = [raw:BE9401FC110C162019FF00, ..., attrId:2016, encoding:19, value:00FF]
  zoneEnable is 255 (raw:255) (no change)
  ```

  Current States afterwards: `zoneEnable 255`, `zonesEnabled 1,2,3,4,5,6,7`.

Byte order — settled by the two read frames:

Hubitat reverses the ZCL payload octets when it builds `descMap.value`. The same log shows
illuminance `0x0400:0x0000` arriving as wire `FF 4F` → `descMap.value 4FFF` → 20479 → 112 lx,
which is correct, so the reversal is standard Hubitat behavior and not a device quirk.

| | logical value | logged `he wattr` | on the wire | sensor stores | read response wire | Hubitat decodes |
|---|---|---|---|---|---|---|
| before the fix ([#518](https://community.hubitat.com/t/-/137410/518)) | 255 | `{00FF}` | `FF 00` | `0xFF00` = 65280 | `00 FF` | 65280 ✗ |
| after the fix (2026-08-30 log) | 65280 | `{FF00}` | `00 FF` | `0x00FF` = 255 | `FF 00` | 255 ✓ |

Only one model fits both frames: **the sensor decodes BITMAP16 writes big-endian, but serializes
read responses little-endian like any other attribute.** A device that were big-endian in both
directions would have decoded correctly in #518 without any driver change, and one that were
little-endian in both directions would never have stored 65280. The correction therefore belongs on
the transmit side only; the earlier receive-side swap treated the symptom on the display and left
the wrong bitmap in the sensor.

Pre-fix code behavior (driver 4.2.5, timestamp `2026/08/29 07:05 PM`):

- `customUpdated()` calls `updateAllPreferences()`. The V4 profile library routed `zoneEnable`
  through generic `setPar()` / `zclWriteAttribute()` because no `customSetZoneEnable()` existed, so
  logical value `255` was passed directly to the BITMAP16 writer.
- `sonoffWriteZoneEnable()` likewise passed the logical low-byte bitmap directly; both zone commands
  call this helper.
- `customParseFC11Cluster()` swapped every four-character `0x19` value on cluster `0xFC11`, without
  a model or attribute guard. This masked the displayed readback and could affect other FC11
  BITMAP16 attributes; it did not repair the bitmap already stored by the sensor.

Implementation applied on 2026-08-30 (driver 4.2.5, timestamp `2026/08/30 07:38 AM`):

- Removed the receive-side BITMAP16 swap from `customParseFC11Cluster()`. **Confirmed correct** —
  the read frame above decodes to 255 with no parser correction.
- Added `customSetZoneEnable(int)` so Save Preferences and generic `setPar` writes use the same
  centralized helper as `setZone` / `setAllZones`. **Confirmed reached** — the log shows
  `setPar: (1) successfluly executed setPar customSetZoneEnable(255)`.
- Restricted the byte-order workaround to outgoing `0xFC11:0x2016` writes. The helper passes
  `(bitmap & 0xFF) << 8` to Hubitat's BITMAP16 writer; other FC11 attributes are unchanged.
- The JSON profile database was not changed: the functional fix does not require profile data, and
  comment cleanup would need its own data-version bump.

Open defect in the regenerated bundle — **not yet fixed**:

`Tuya_Zigbee_mmWave_Sensor_lib_included.groovy` was regenerated with the five `#include` directives
left live at lines 62–66 *on top of* the inlined library code. HEAD blanks those five lines; the
current file would include each library twice and will not compile on a hub. Only the `#include`
source driver has been pushed and verified so far. Rebuild the bundle before publishing.

Library note (cosmetic, `deviceProfileLibV4` line 698): `setPar()` logs
`found customSetFunction=${setFunction}` with an undeclared variable — the intended one is
`customSetFunction`, used correctly by `sendAttribute()` at line 841. Hubitat resolves the unknown
property to `null` instead of throwing, so the hook still runs; the log line simply reads
`found customSetFunction=null`.

Scope of the quirk — **settled 2026-08-30**, the Refresh at 08:37:42 read back
`0xFC11:0x2018` (`illuminationOffset`, INT16 `0x29`) as `raw:BE9401FC110C1820293200` →
`value:0032` → **50**, the value written earlier in the same session. The sensor therefore consumes
INT16 writes little-endian like any normal device. **The big-endian write defect is specific to the
BITMAP16 (`0x19`) attribute handler**, so the pre-swap must stay confined to `0x2016` and must not
be generalized to other `0xFC11` attributes.

Remaining verification — **VERIFY ON DEVICE**:

- `zoneStatus` (`0xFC11:0x2015`) read path: **passed**, see 2.9.
- Confirm motion still reports active/inactive after Save Preferences, then test reversible
  `setAllZones none/all` and one `setZone` operation before restoring `255`. Motion active/inactive
  is confirmed working in the 08:31–08:38 captures; the zone commands themselves are still untested.
- Repeat on kdb's firmware `1.0.4` device. The absence of unsolicited `zoneStatus` reports there
  remains **NEEDS_EVIDENCE** and is not part of this fix. Do not transfer the firmware `1.0.0`
  result to `1.0.4` by identity alone.
- Expected and not a regression: the `0x2021` write in the same Save is answered with
  `0x86 UNSUPPORTED_ATTRIBUTE` (`data:[86, 21, 20]`). Firmware `1.0.0` does not implement
  `radarSensitivity`; this is already documented in the profile comment in
  `deviceProfilesV4_mmWave.json`.

### 2.9 `[~]` `PARTIAL` — SNZB-06P24 `zoneStatus` byte order and zone mapping — HUB-129

Byte order and zone mapping **Confirmed on device** (firmware `1.0.0`, 2026-08-30 08:31–08:38, all
seven zones observed). Tests C, E and F below are still open.

`zoneStatus` (`0xFC11:0x2015`, read-only, same bit layout as `zoneEnable`) had never been observed
with a non-zero value before this session: every earlier capture read `0` with nobody present,
which is consistent with either byte order, so the receive path removed in 2.8 was unproven for
this attribute.

Results, 2026-08-30 (device `BE94`, firmware `1.0.0`, driver 4.2.5 timestamp `2026/08/30 07:38 AM`):

- **Reads need no correction.** Every occupancy value decoded into the low byte, exactly as the
  2.8 model predicts. Nothing above 255 was ever seen.

  | zone | report payload | `descMap.value` | `zonesOccupied` |
  |---|---|---|---|
  | 1 (0-1 m) | `02 00` | `0002` | `1` |
  | 2 (1-1.5 m) | `04 00` | `0004` | `2` |
  | 3 (1.5-2 m) | `08 00` | `0008` | `3` |
  | 4 (2-2.5 m) | `10 00` | `0010` | `4` |
  | 5 (2.5-3 m) | `20 00` | `0020` | `5` |
  | 6 (3-3.5 m) | `40 00` | `0040` | `6` |
  | 7 (3.5-4 m) | `80 00` | `0080` | `7` |
  | empty | `00 00` | `0000` | `none` |

- **Zone 1 is bit 1, not bits 0+1.** Bit 0 was never set in any of the captured reports — zone `N`
  maps to bit `N` for all of `1..7`. `sonoffZoneBitMask(1)` returns `0x03`, which still detects and
  still enables/disables zone 1 correctly because bit 1 is inside the mask, so no code change is
  needed. Worth knowing before anyone "simplifies" that mask.
- **`zoneStatus` is one-hot in practice, not a multi-zone bitmap.** Across both capture sessions
  (08:31-08:38 and 08:46-08:48, well over a hundred reports with continuous free movement) every
  non-zero value had exactly **one** bit set: only `0002`, `0004`, `0008`, `0010`, `0020`, `0040`,
  `0080` and `0000` were ever seen. No combination such as `000C` or `0030` occurred even while
  crossing zone boundaries. The sensor therefore appears to report the single zone holding the
  strongest target rather than every occupied zone, so `zonesOccupied` will in practice always name
  one zone or `none`. The driver decodes it as a bitmap, which stays correct either way — but the
  attribute description in `deviceProfilesV4_mmWave.json` ("Which detection zone is currently
  occupied") should not be reworded to promise a multi-zone list.
- Adjacent-zone ping-ponging at the 1 Hz report rate is common (for example `5 → 7 → 5 → 7` at
  08:47:17-08:47:22). Whether that is target-tracking jitter or genuine movement is unknown, since
  neither capture was a controlled walk. Anyone building automations on `zonesOccupied` will want
  to debounce it.
- **The report path differs from the read path** and both are little-endian:
  - Unsolicited reports (`command:0A`) arrive as a single multi-attribute Report Attributes frame
    that always bundles `0x2016` **and** `0x2015`, both with encoding **`0x21`** (UINT16), e.g.
    `raw:BE9401FC1114162021FF001520218000` → `zoneEnable 255` plus
    `additionalAttrs:[[attrId:2015, value:0080]]` → `zonesOccupied 7`.
  - Read Attributes Responses (`command:01`, from Refresh) use encoding **`0x19`**, e.g.
    `raw:BE9401FC110C1520190000`.
  - The profile declares `dt: "0x19"`. That is only used for writes, and `0x2015` is read-only, so
    the encoding mismatch on the report path is harmless — but do not "fix" the profile to `0x21`.
- **Unsolicited `zoneStatus` reports do arrive on firmware `1.0.0`**, roughly once per second while
  a person is present. kdb reports none on `1.0.4`; that difference is real and still
  **NEEDS_EVIDENCE**.
- Event volume is self-limiting: `deviceProfileLib` only calls `customProcessDeviceProfileEvent()`
  when the decoded value changes, so a stationary person produces `(no change)` debug lines rather
  than a 1 Hz stream of events.

Why a zero reading proves nothing, and what does:

The zone bits all live in the low byte — zone 1 is bits 0+1 (`0x03`), zones 2..7 are bits 2..7. So
**any** genuine occupancy value is asymmetric, and a byte swap moves every bit into the high byte:

| occupied zone | logical | little-endian read (`descMap.value`) | byte-swapped read | `zonesOccupied` if swapped |
|---|---|---|---|---|
| 1 (0-1 m) | `0x0003` = 3 | `0003` → 3 | `0300` → 768 | `none` |
| 4 (2-2.5 m) | `0x0010` = 16 | `0010` → 16 | `1000` → 4096 | `none` |
| 7 (3.5-4 m) | `0x0080` = 128 | `0080` → 128 | `8000` → 32768 | `none` |

`sonoffZonesToString()` only inspects bits 0..7, so a swapped value silently yields
`zonesOccupied = none` while the raw `zoneStatus` attribute holds a number above 255. **Rule of
thumb: any `zoneStatus` value greater than 255 means the read path needs a swap for `0x2015`.**

Preconditions: `zoneEnable = 255` (`zonesEnabled` shows `1,2,3,4,5,6,7`), `ignoreZoneStatus = false`,
debug logging on, room empty and `motion` settled to `inactive` before each test.

- **A. Decisive endianness test — near zone. `[x]` PASSED.** Stand still at roughly 0.5 m directly in front of the
  sensor for about 10 seconds. Capture the `parse: descMap = [raw:...attrId:2015...]` lines.
  Pass: payload `03 00`, `value:0003`, `zoneStatus 3`, `zonesOccupied 1`.
  Fail: `value:0300`, `zoneStatus 768`, `zonesOccupied none` — restore the receive-side swap,
  scoped to `0x2015` only.
- **B. Far zone. `[x]` PASSED.** Stand still at roughly 3.7 m. Pass: `value:0080`, `zoneStatus 128`,
  `zonesOccupied 7`. This also checks the bit-to-distance mapping, not just the byte order.
- **C. Walk test. `[ ]` still open** — the 2026-08-30 capture was free movement around the
  sensor, not a straight walk out, so the direction of the mapping is not yet proven. Walk slowly from 0.5 m out to 4 m. `zonesOccupied` should climb
  `1 → 2 → 3 → ... → 7`. A clean `7 → 6 → ... → 1` progression means the bit order within the byte
  is reversed, which is a different defect from endianness and would need its own fix.
- **D. Polled read versus unsolicited report. `[x]` PASSED**, see the report-path notes above. With someone present, press **Refresh** and capture
  the Read Attributes Response (`command:01`) for `0x2015`; separately capture an unsolicited report
  (`command:0A`). Record the `encoding` of each. The device is known to report `0x2016` with
  encoding `0x21` rather than the `0x19` it uses in read responses, so the two paths must be
  confirmed independently. Also record whether unsolicited `zoneStatus` reports arrive at all on
  firmware `1.0.0` — kdb reports none on `1.0.4`.
- **E. End-to-end zone gating. `[ ]` still open.** `setAllZones('none')`, then `setZone('1 on')`. Stand in zone 1:
  `zonesOccupied` shows `1` and `motion` goes active. Stand at 3 m: no motion and
  `zonesOccupied none`. Restore with `setAllZones('all')`. This is the original HUB-129 symptom, and
  it also exercises `setZone`, which takes `device.currentValue('zoneEnable')` as its base bitmap —
  that attribute must stay within `0..255` after every operation.
- **F. Log-volume regression. `[ ]` partially done** — the roughly 1 Hz cadence is confirmed;
  `ignoreZoneStatus = true` is still untested. Confirm the roughly one-per-second cadence while a person is
  present, and that setting `ignoreZoneStatus = true` suppresses the events without affecting
  `motion`.

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
enforcement mechanism for AGENTS.md golden rule 6.

---

## Already covered elsewhere (do not duplicate)

- Cold-start fingerprint gap — device pairs as "Device" after reboot/fresh install, users must
  load profiles *and* re-save the driver ([#436](https://community.hubitat.com/t/-/137410/436)–[#443](https://community.hubitat.com/t/-/137410/443) DGBQ, [#201](https://community.hubitat.com/t/-/137410/201), [#262](https://community.hubitat.com/t/-/137410/262)) →
  IMPROVEMENT_PLAN item 1 + driver-header TODOs.
- Intermittent `JsonSlurper` parse exception after reboot ([#345](https://community.hubitat.com/t/-/137410/345), [#486](https://community.hubitat.com/t/-/137410/486)–[#493](https://community.hubitat.com/t/-/137410/493)
  user5504 — worked around with the archived 3.5.2 driver) → driver-header TODO +
  IMPROVEMENT_PLAN item 3.
- Load commands don't re-evaluate `metadata{}` fingerprints (two-step activation) →
  IMPROVEMENT_PLAN item 4 / AGENTS.md golden rule 5.
- `switch` attribute enum `['manual','auto']` vs MUVKRJR5_2 OFF/ON mismatch →
  OPTIMIZATION_PLAN appendix #19 (the rename proposal itself is folded into item 1.1 here).
- HPM still serving 3.5.1 → deliberate: the author keeps 4.x manual-update/Repair-only until
  it leaves beta (thread top post, updated 2025-11-24). Becomes a release task, not a bug.
  Tracked on GitHub as issue #114 (open, intentionally deferred — not a code gap).
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
