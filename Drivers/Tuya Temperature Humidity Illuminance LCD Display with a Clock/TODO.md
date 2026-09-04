# Consolidated TODO — Tuya Temperature Humidity Illuminance LCD Display with a Clock

Analysis date: 2026-07-11 (forum backlog, posts through 693); bug backlog merged 2026-08-04;
incremental forum audit through post 713 on 2026-09-04; supplemental topic 156210 audited through
post 7 on 2026-08-29
Forum cutoff: post 713, 2026-09-04
Topic: https://community.hubitat.com/t/-/88093  
Coverage: complete Discourse stream through post 713 — 697 visible posts; highest visible post
number 713 at audit time
Supplemental topic: https://community.hubitat.com/t/-/156210 — complete stream through post 7;
7 visible posts, highest post number 7 at audit time

This is the single consolidated backlog for this driver. It combines the forum-derived
device-support/feature backlog with the reviewed bug list. **`BUGS.md` was merged into this file
and deleted on 2026-08-04 — do not recreate it; add new findings here instead.** Resolved bugs were
removed from the list; their full history lives in `CHANGELOG.md` and `AGENTS.md`'s referenced git
history. Deleted forum posts account for the difference between the visible-post count and highest
post number.

Status legend (forum backlog):

- `OPEN` — requested or reported and not implemented/resolved.
- `NEEDS_EVIDENCE` — requires a textual fingerprint, logs, or hardware confirmation.
- `BUGS` — overlaps the bug backlog in Part 2 below.
- `RESOLVED` — implemented or confirmed working later in the topic.
- `OUT_OF_SCOPE` — belongs to another device class or project.
- `DEVICE_LIMITATION` — cannot be implemented using known device/firmware behavior.

Status legend (bug backlog, Part 2):

- `[ ]` open. `[?]` needs verification/user decision first. `[x]` items are removed once fixed and
  confirmed — see `CHANGELOG.md` instead.

## Part 1 — Forum-derived backlog

### 1. `[?]` `OPEN` — TS0601 `_TZE284_hdml1aav` five-in-one soil sensor — fix applied, pending hub confirmation

**Requested outcome:** expose all reported measurements — soil moisture, temperature, humidity,
illuminance, and fertility — without causing the sensor to stop reporting.

Evidence:

- Initial report and experimental driver: https://community.hubitat.com/t/-/88093/687
- Maintainer identified the device as TS0601 `_TZE284_hdml1aav` and noted probable firmware/DP
  differences: https://community.hubitat.com/t/-/88093/688
- Unresolved follow-up: https://community.hubitat.com/t/-/88093/689

Identity and DP mapping (2026-08-04, confirmed via the poster's own screenshots — including a raw
"Tuya Dp Log" from their ChatGPT-generated driver — plus `zigbee-herdsman-converters`):

- Model `TS0601`, manufacturer `_TZE284_hdml1aav` (z2m also lists `_TZE2841000000_hdml1aav`, an
  alternate manufacturer-ID encoding for the same device — added too).
- z2m model `ZS-300TF` (vendor Excellux, "Soil fertility sensor"). Confirmed DP map: DP 3 =
  soil_moisture (raw), DP 5 = temperature (÷10), DP 15 = battery (raw), DP 101 = humidity (raw),
  DP 102 = illuminance (raw), DP 103 = report_period (raw, writable), DP 104-107 = four calibration
  offsets (soil/humidity/illuminance raw, temperature ÷10), DP 110 = soil_warning (raw, meaning
  unclear), DP 111 = water_warning (enum none/alarm), DP 112 = soil_fertility (raw, µS/cm),
  DP 113-115 = fertility calibration/thresholds (writable), DP 116 = soil_fertility_warning (enum
  none/low/high). Every DP with an observed value in the raw log matched this table exactly.
- Forcing `TS0601_Soil_NEO` only worked by accident for temperature/humidity/illuminance — its DP 3
  means *humidity* for that group, not soil moisture, which is why fertility/moisture were missing.

Fix applied (2026-08-04): new model group `TS0601_Soil_5IN1`, fingerprint + `Models` entries for
both manufacturer IDs, and DP routing for the 5 core measurements plus the two warning enums:
- `soilMoisture` (existing `number` attribute, reused — already used by `TS0601_Soil_Coolo`)
- New `soilFertilityValue` (`number`, µS/cm) — **could not reuse** the existing `soilFertility`
  attribute, already declared `enum` (6 qualitative values) for `TS0601_Soil_NEO`; a real type
  conflict, not just a naming choice.
- New `waterWarning` (`enum`: none/alarm) and `soilFertilityWarning` (`enum`: none/low/high).
- Temperature/humidity/illuminance/battery reuse existing generic event functions.

Deliberately deferred (no forum evidence anyone needs them, and this session's convention is to
not guess at untested DP *writes*):
- `report_period` (DP 103) — logged only; not read back or written. Could be the fix for "stops
  reporting after several minutes" if it's a device-side sampling interval, but unconfirmed.
- The four calibration offsets (DP 104-107) and three fertility-threshold DPs (113-115) — not
  implemented at all (no preferences, no read handling beyond generic default).
- `soil_warning` (DP 110) — logged only; z2m doesn't clarify its meaning either.

**Reporting-dropout root cause still open** — this is a pure Tuya EF00 DP device (no ZCL reporting
cluster involved), so this driver's `isConfigurableSleepyDevice()` mechanism doesn't apply here and
wouldn't be the fix. Likely either device-side power-saving behavior or a bug specific to the
poster's own unofficial driver — not confidently diagnosed. Needs the poster to test with the
corrected mapping and report whether dropouts persist.

Verification needed from the poster: confirm all 5 core measurements plus both warnings report
plausible values, and monitor whether reporting still stops after several minutes.

### 2. `[?]` `OPEN` — Jost's `_TZE204_rbbx5mfq` three-DP T/H/illuminance sensor — fix applied, pending hub confirmation

**Requested outcome:** correctly expose illuminance, temperature, and humidity and, if supported by
the firmware, reduce the sensor's excessive Zigbee reporting frequency.

Evidence:

- Report, screenshots, and DP observations: https://community.hubitat.com/t/-/88093/691
- Maintainer confirmed it is an unknown device and accepted it for a future update:
  https://community.hubitat.com/t/-/88093/692
- Reporter produced a private modification but reporting-rate configuration remains unresolved:
  https://community.hubitat.com/t/-/88093/693

Identity and DP mapping (2026-08-04, confirmed via Device Data screenshot + `zigbee-herdsman-converters`):

- Manufacturer `_TZE204_rbbx5mfq`, model `TS0601`. z2m model
  `TS0601_illuminance_temperature_humidity_sensor_2`: DP 2 = illuminance (raw, no division), DP 6 =
  temperature (÷10), DP 7 = humidity (÷10) — matches the reporter's trace log exactly.
- Root cause confirmed by code review, not just inferred: the generic/`UNKNOWN` fallback's DP `0x02`
  case treats DP 2 as **humidity** (correct for most Tuya EF00 models, wrong here — DP 2 is
  illuminance for this device), which is why displayed humidity tracked the illuminance value and
  spiked past 100% under bright light. DP `0x06`/`0x07` were *unconditionally* log-only for every
  model group (no `temperatureEvent()`/`humidityEvent()` call at all), so this device's real
  temperature/humidity were silently discarded regardless of group.

Fix applied (2026-08-04): new model group `TS0601_Illum_TH`, fingerprint + `Models` entry for
`_TZE204_rbbx5mfq`, and `TS0601_Illum_TH`-gated branches added to DP `0x02`/`0x06`/`0x07` routing
them to illuminance/temperature/humidity respectively. Also corrected the *sibling* manufacturer
`_TZE200_rbbx5mfq` (added speculatively in v1.9.0 as `TS0601_Tuya`, unverified) — z2m lists it under
the identical device definition, so it was moved to `TS0601_Illum_TH` too; no separate fingerprint
was added for it since one already existed.

Still open:

- **Cluster list unconfirmed** — the reporter's Device Data screenshot didn't show
  `inClusters`/`outClusters`; the fingerprint uses this driver's standard TS0601-EF00 template as a
  best guess (`0004,0005,EF00,0000` / `0019,000A`), marked `// not tested !`. Wrong clusters only
  block automatic driver *selection*, not the DP fix itself.
- **Reporting-rate reduction** (post #693) — whether report frequency is controlled by a writable
  DP is still unconfirmed; event throttling in this driver doesn't reduce Zigbee network traffic.
- **Related, unconfirmed suspicion found in passing**: `_TZE200_vzqtvljm`, added in the same v1.9.0
  batch with the same "(Illuminance + TH)" note, also maps to `TS0601_Tuya`. z2m lists it under a
  *different* device (`TS0601_illuminance_temperature_humidity_sensor_1`, legacy converter: DP 3 =
  battery, DP 7 = illuminance raw, DP 8 = temperature ÷10, DP 9 = humidity **raw, not ÷10**) — a
  third, distinct DP layout from both `TS0601_Tuya` and the new `TS0601_Illum_TH` group. Not touched
  — no forum report or user complaint for this manufacturer, so left alone pending actual evidence
  rather than fixed speculatively.

Verification needed from the poster: confirm illuminance/temperature/humidity all report plausible
values with no "invalid humidity" warnings, including under bright light.

### 3. [ ] `OPEN` / `BUGS` — Suppress false 100% humidity from `_TZ3210_ncw88jfq`

GitHub issue: https://github.com/kkossev/Hubitat/issues/130.

**User-visible problem:** three TS0201 `_TZ3210_ncw88jfq` sensors reportedly send an hourly humidity
sample in hundredths while ordinary samples use tenths. The driver interprets the hourly sample as
greater than 100% and clamps it to 100%, generating a false humidity event.

Evidence:

- Reproduction from three sensors with raw logs: https://community.hubitat.com/t/-/88093/663
- Maintainer's mixed-scaling/firmware diagnosis: https://community.hubitat.com/t/-/88093/664
- Reporter chose ignoring invalid reports rather than guessing their scaling:
  https://community.hubitat.com/t/-/88093/665
- Follow-up question about zero/negative values: https://community.hubitat.com/t/-/88093/666

Known data:

- Model: `TS0201`
- Manufacturer: `_TZ3210_ncw88jfq`
- Reported examples include raw `221`–`234`, interpreted as 221%–234%, followed shortly by the
  plausible 22.1%–23.4% reading.

Implementation direction:

- Cross-reference the invalid-humidity handling findings in Part 2 below; do not create a competing
  global fix.
- Scope any workaround to `_TZ3210_ncw88jfq` unless logs prove other manufacturers behave the same.
- Ignore values above 100% for this device instead of clamping them to 100%.
- Treat negative values as invalid. Confirm whether a real 0% report must remain valid.
- Verify with live logs covering both the hourly packet and normal humidity-change packets.

### 4. [ ] `OPEN` — Avoid the external-probe child `Refresh` warning

**User-visible problem:** `_TZE284_hodyryli` external-probe support works, but refreshing its child
logs `componentRefresh() called but device is not DS18B20 model (TS0601_ZTH03PRO)`.

Evidence:

- Both temperatures confirmed working and warning reproduced:
  https://community.hubitat.com/t/-/88093/685

Known data:

- Model: `TS0601`
- Manufacturer: `_TZE284_hodyryli`
- Model group: `TS0601_ZTH03PRO`
- Probe child device: `<device.id>-probe` since 2.2.1 (older children keep `<parent-dni>-probe`)
- Current `componentRefresh()` handles only the `DS18B20` group.

Implementation direction:

- Add explicit `TS0601_ZTH03PRO` handling, likely by querying all Tuya DPs from the parent, if the
  device responds to that query.
- If the sleepy firmware cannot refresh on demand, make child refresh a documented no-op without a
  misleading warning.
- Verify that refreshing either parent or child does not disturb normal probe reports.

### `RESOLVED` — `_TZ3000_utwgoauk` ("SNZB-02" Tuya clone) pairing and apparent network dropout

**User-visible problem:** poster calinatl reports a small USB/battery-powered temperature/humidity
sensor "keeps falling off the network," paired with this driver in Auto Detect mode.

Evidence:

- Report with two screenshots (Hubitat Device Data panel + product photo):
  https://community.hubitat.com/t/-/88093/694

Known data (from the Device Data screenshot — authoritative, read from the paired device):

- Manufacturer: `_TZ3000_utwgoauk`, reports **model `SNZB-02`** (not `TS0201`) — a cheap clone
  spoofing the popular genuine Sonoff model string.
- Real clusters: `inClusters:'0000,0003,0001,0020,0402,0405'`, `outClusters:'0019'` — notably
  includes Poll Control (`0020`), absent from this driver's pre-existing fingerprint for this
  manufacturer.

Fix applied (2026-08-04, pending poster confirmation):

- Added a second, corrected fingerprint for `_TZ3000_utwgoauk` matching the real signature
  (`model:'SNZB-02'`), alongside the existing one rather than replacing it.
- Reclassified `Models['_TZ3000_utwgoauk']` from `TS0201` to `Zigbee NON-Tuya` — genuine Sonoff
  SNZB-02D/02P (which also carry cluster `0020`) are the only devices in this exact cluster
  configuration mapped to a group that runs the sleepy-device Configure Reporting state machine;
  plain `TS0201` never attempts any reporting configuration at all. This is the primary hypothesis
  for the reported dropouts, inferred from cluster-pattern analogy — **not confirmed by testing this
  specific device.**

Follow-up (2026-08-06, post #704): poster removed and re-paired the device on the corrected driver;
HE did **not** auto-select any driver at all (Type stayed "Device"), and the join log shows
`inClusters:"0000,0003,0001,0020,0402,0405"` — clusters `0001`/`0003` swapped versus the fingerprint
added on 2026-08-04 (`0000,0001,0003,0020,0402,0405`). Hubitat's built-in-driver-selection fingerprint
match is order-sensitive on `inClusters`, so the existing fingerprint silently never matched this
unit. Added a **third** fingerprint with the exact order from this log, kept the other two as-is
(other units of the same clone may report yet another order). This explains why the driver was never
selected in the first place — the sleepy-device reporting-config hypothesis from 2026-08-04 is still
unverified since the driver was never actually running on this device.

Resolution: after the exact-order fingerprint was added, the device auto-selected this driver and
reported normally for more than 72 hours ([#706](https://community.hubitat.com/t/-/88093/706)–
[#708](https://community.hubitat.com/t/-/88093/708)). The later offline event was not a driver or
Zigbee failure: the USB cable had been knocked loose. It rejoined automatically as soon as power
was restored ([#709](https://community.hubitat.com/t/-/88093/709)–
[#711](https://community.hubitat.com/t/-/88093/711)). The screenshot in #708 shows ordinary
temperature, humidity, battery-voltage, and online health-check logs, not an error. No new driver
task remains from this report.

### 6. `[?]` `OPEN` — HaiHao HZ-SL03 soil sensor: humidity not reporting, battery misread

GitHub issue: https://github.com/kkossev/Hubitat/issues/149.

**Requested outcome:** humidity should report a value (currently stays `null`); battery percentage
shouldn't read a stale/low value on fresh batteries.

Known data (from the reporter, no textual Zigbee fingerprint available yet — HE Maker API doesn't
expose it):

- Model `HZ-SL03`, manufacturer "Shenzhen HaiHao Electronic Co., LTD" — same company as the already
  supported Haozee SGS01/HZ-SL05 family (`TS0601_Haozee` group).
- Temperature, illuminance, and battery report; humidity never does — consistent with this being an
  unrecognized manufacturer falling into `UNKNOWN`/a mismatched model group whose humidity DP isn't
  the generic `0x02`.
- Battery briefly read 33% (state 0/Low) on fresh batteries, then jumped to 100% after `initialize()`
  — possibly a stale-DP-on-first-report issue rather than a scaling bug; compare against the
  known `TS0601_Soil_NEO`/`Coolo` battery-state pattern (0/1/2 → 33/66/100%) once a fingerprint exists.

Implementation direction:

- Request the textual fingerprint (Hubitat Device Data page: model/manufacturer/inClusters/
  outClusters), since screenshots aren't sufficient (see the `NEEDS_EVIDENCE` convention below).
- Once known, map to the closest existing soil/Haozee model group (likely `TS0601_Soil` or a
  `TS0601_Haozee` variant) rather than guessing a new group without a confirmed DP map.

## Reports needing more evidence

### 7. [ ] `NEEDS_EVIDENCE` — TS0201 battery value remains stale after replacement

Evidence: https://community.hubitat.com/t/-/88093/659 through
https://community.hubitat.com/t/-/88093/661

The debug log shows the device continuing to transmit raw battery value `70` (35%), so the available
evidence points to device firmware/reporting behavior rather than a demonstrated driver conversion
error.

Request before opening a code change:

- Textual fingerprint and device data.
- Raw battery reports before and after battery replacement.
- Results after waking, re-pairing without deletion, and waiting for the documented periodic report.
- Measured battery voltage, if available.

### 8. [ ] `NEEDS_EVIDENCE` — Previously working unidentified temperature sensor stopped reporting

Evidence: https://community.hubitat.com/t/-/88093/667

The identifying data exists only in screenshots and no follow-up was posted. Request the complete
fingerprint as text, current driver version/model group, pairing logs, and ordinary receive logs.

### 9. [ ] `NEEDS_EVIDENCE` — Validate `_TZE284_rqcuwlsa` ZDO responder experiment

Evidence:

- Original disconnect report: https://community.hubitat.com/t/-/88093/636
- ZDO hypothesis: https://community.hubitat.com/t/-/88093/637
- Experimental responder announcement: https://community.hubitat.com/t/-/88093/640
- Later report working with `Respond to ZDO requests` disabled:
  https://community.hubitat.com/t/-/88093/662

Do not enable the responder by default or remove it based on this thread alone. Capture controlled
pairing/rejoin tests from multiple devices with the option both enabled and disabled. ZDO handling is
normally a platform responsibility, and the available evidence does not show that the custom
responses caused the later success.

### 10. [ ] `NEEDS_EVIDENCE` — eMylo TS0201-compatible sensor fingerprint

Posts: https://community.hubitat.com/t/-/162681/3 and
https://community.hubitat.com/t/-/162681/4 (calinatl, reviewed 2026-08-15).

The sensor randomly dropped while using the Tuya Multi Sensor 4 In 1 driver. It was identified as
belonging to this driver and manually assigned to Model Group `TS0201`, but the exact fingerprint is
missing from the source and no result was posted after the requested re-pair. The Device Data exists
only in a forum screenshot. Request textual model/manufacturer/cluster data and confirmation that the
manual `TS0201` selection works before adding a fingerprint. **VERIFY ON DEVICE**.

### 11. [ ] `OPEN` — Expose core temperature-reporting controls without Advanced options — HUB-141

**Requested outcome:** make **Temperature Sensitivity** and **Maximum reporting time** visible for
the model groups that already support them, without requiring users to enable **Advanced options**.
This is a preference-discoverability change, not a change to temperature readings or reporting
semantics.

Evidence:

- A community reply recommended this driver for control over reporting:
  https://community.hubitat.com/t/-/156210/5
- The reporter said that changing the temperature reporting was not obvious and wanted an example
  configuration of reporting on a 1 °C change and at least once per hour:
  https://community.hubitat.com/t/-/156210/7
- Supplemental topic 156210 was reviewed completely through post 7 on 2026-08-29: 7 visible posts;
  highest visible post number 7.

Evidence boundary:

- The thread identifies the product as SNZB-02LD, and post 7 reports Hubitat C8 platform 2.5.1.152.
- Post 7 does not provide the device's textual Hubitat manufacturer/model fingerprint. Do not
  transfer one from another topic, poster, or similar-looking device.
- Adding an SNZB-02LD fingerprint is separate work tracked in HUB-86; this item changes the existing
  preference presentation only.

Current code behavior (driver 2.2.0, timestamp 2026/08/06 12:22 AM):

- `advancedOptions` defaults to `false`.
- All dynamic `configParams` controls are rendered only inside `if (advancedOptions == true)`.
- `temperatureSensitivity` and `maxReportingTimeTemp` are therefore hidden until Advanced options
  is enabled, even for model groups allowed by their existing `limit` lists.
- `updated()` already uses these settings when it builds the cluster `0x0402` Configure Reporting
  command; the requested change does not require a new reporting mechanism.

Implementation direction:

- Move only `temperatureSensitivity` and `maxReportingTimeTemp` outside Advanced options, while
  preserving each control's current model-group `limit`, name, default, range, unit, and saved value.
- Keep `minReportingTimeTemp` and the remaining expert controls under Advanced options unless a
  separate request expands the scope.
- Preserve reporting configuration behavior, temperature offset/rounding/filtering behavior, and
  all unsupported-model exclusions.

Verification:

- With Advanced options disabled, each control appears for every model group allowed by that
  control's existing `limit`, and does not appear when that `limit` excludes the group.
- Saving equivalent values sends the same temperature-reporting configuration as before the UI
  relocation, and existing saved values survive the change.
- Verify the reporting response on a supported sleepy Zigbee device. SNZB-02LD-specific behavior
  remains **VERIFY ON DEVICE** until its exact fingerprint and reporting response are confirmed.

### 12. `[?]` `OPEN` — DS18B20 / probe child devices lose their identity — fix applied, pending hub confirmation — HUB-137

**User-visible problem:** `@pauljneil2` reports that after power-cycling an MHCOZY four-relay board
(`TS000F` `_TZ3218_ya5d6wth`, model group `DS18B20`) the custom labels on his relay children were
sometimes replaced by default ones and his automations stopped working. Intermittent: in a second
reproduction on 2026-08-22 the labels survived and only the two children he had manually deleted
came back.

Evidence:

- Forum topic: https://community.hubitat.com/t/how-to-use-mhcozy-4-relay-w-temp/163128
- Logs through `2026-08-22 02:53:34` confirm recreation of the manually deleted Relay 2 and Relay 3
  only — that part is expected self-repair, not the defect.
- The parent and child DNIs before and after the power cycle were **not** captured, so the
  short-address change itself is not proven by the supplied log.

Maintainer decision: all four relay children are part of the device contract for
`_TZ3218_ya5d6wth` and are created automatically. Manually deleting one is unsupported and it will
be recreated. No relay-selection preference is added.

Fix applied in 2.2.1 (see `CHANGELOG.md`): child DNIs are now built from the immutable
`device.id`; existing children keep their old DNI and are resolved by suffix instead; Initialize,
a transient `UNKNOWN` model group, and the legacy-child migration no longer delete children.

Remaining verification — **VERIFY ON DEVICE**:

- Upgrade an existing four-relay installation: no child is deleted or recreated, numeric child ids,
  custom labels, rooms and **In Use By** are unchanged, no duplicates appear.
- Power cycle the board, ideally forcing a rejoin that changes the parent short DNI: labels survive
  and On/Off/Refresh from each child still address the right relay endpoint.
- Press **Initialize** and confirm the children survive.
- Regression: single-relay `_TZ3218_7fiyo3kv` keeps exactly one working child, and the
  `TS0601_ZTH03PRO` probe child survives the same operations.
- Paul's confirmation on the physical DC four-relay board is still outstanding.
### 13. [ ] `OPEN` / `NEEDS_EVIDENCE` — Add TS0601 `_TZE284_qf5mzewi` temperature/humidity sensor support — HUB-145

**Requested outcome:** recognize the device and report temperature and humidity instead of leaving it
in model group `UNKNOWN` with no readings.

Evidence:

- User report, exact identity, driver version, and state variables:
  https://community.hubitat.com/t/-/88093/713
- Topic 88093 was audited through post 713 on 2026-09-04.

Known data:

- Model: `TS0601`
- Manufacturer: `_TZE284_qf5mzewi`
- Reported driver: 2.2.1, timestamp 2026/08/29 7:28 PM
- `Model Group: UNKNOWN`; the reporter tried every manually selectable model group without success.
- Receive activity is present (`rxCtr: 22`), but the post contains no raw Tuya EF00 DP payloads or
  textual cluster lists.

Implementation direction:

- Obtain debug/trace logs containing the exact Tuya EF00 datapoints and the complete textual Hubitat
  fingerprint, including `inClusters` and `outClusters`.
- Search current Zigbee2MQTT and ZHA support for the exact manufacturer; do not borrow datapoints
  from similar-looking products or different fingerprints.
- Reuse an existing model group only if its DP semantics match exactly; otherwise add a dedicated
  group and route only confirmed datapoints.
- Preserve all existing model-group behavior and debug-log unsupported DPs.

Verification — **VERIFY ON DEVICE**:

- The exact identity is recognized instead of `UNKNOWN`.
- Confirmed temperature and humidity DPs create plausible events with correct scaling and units.
- Automatic driver selection is verified after adding the complete observed fingerprint.
- The reporting user confirms readings from the development build before this item is completed.

## Already resolved, declined, or outside this backlog

### `RESOLVED` — `_TZE204_m9dzckna` SNT857Z temperature sensor

GitHub issue: https://github.com/kkossev/Hubitat/issues/69 (still open on GitHub; candidate to close).
Forum thread: https://community.hubitat.com/t/rule-for-temperature-monitoring-and-swithching/160162.
User couldn't get a TS0601 temperature sensor working with Hubitat's built-in drivers ("Tuya TS0601
devices will not function with the generic built-in drivers, as they use Tuya-specific Zigbee
commands"). Resolved by selecting this driver with **Model Group** forced to `TS0601_Tuya` in
preferences — no fingerprint/code change needed, the existing generic EF00 fallback already covers
this device's temperature/humidity DPs.

### `RESOLVED` — HOBEIAN ZG-303Z soil tester `NumberFormatException` when forced to `TS0201_TH`

GitHub issue: https://github.com/kkossev/Hubitat/issues/67. Forum thread (separate from the main
88093 thread): https://community.hubitat.com/t/driver-for-tuya-soil-tester-sensor/156528. Device
support itself (`_TZE284_33bwcga2`, native group `TS0601_Soil_II`) was already added in v1.9.0
(2025-08-31) — the only unresolved part was user Rxich's `NumberFormatException:
For input string: "Celsius"` after manually setting Model Group to `TS0201_TH`. Same root cause as
the `temperatureUnit` default-seeding bug fixed 2026-08-04 (see `CHANGELOG.md` `[2.2.0]`) — resolved
as a side effect, not verified by the reporter. Issue #67 remains open on GitHub; consider closing
or commenting once verified.

### `RESOLVED` — Configurable temperature/humidity decimal places

Requested at https://community.hubitat.com/t/-/88093/695 (show `37` instead of `37.4`). Added
**Temperature Decimal Places** (0/1/2, default 1) and **Humidity Decimal Places** (0/1, default 0)
preferences on 2026-08-04 — see `CHANGELOG.md` `[Unreleased]`.
The requester confirmed both preferences work at https://community.hubitat.com/t/-/88093/712.

### `RESOLVED` — `_TZE284_hodyryli` external probe support

Requested at https://community.hubitat.com/t/-/88093/672, added in v2.1.0 at
https://community.hubitat.com/t/-/88093/673, corrected in the later v2.1.1 build, and confirmed working
at https://community.hubitat.com/t/-/88093/685. Only the child-refresh warning remains open as item 4.

### `RESOLVED` — `_TZE284_9ern5sfh` / RSH-HS03 support

Reported at https://community.hubitat.com/t/-/88093/675, added as `TS0601_Tuya_3` at
https://community.hubitat.com/t/-/88093/676, and both devices were confirmed working after re-pairing
at https://community.hubitat.com/t/-/88093/682.

### `RESOLVED` — DS18B20 child-switch support

Requested at https://community.hubitat.com/t/-/88093/626 and implemented in v2.0.0 at
https://community.hubitat.com/t/-/88093/627. Later releases added four-relay support for
`_TZ3218_ya5d6wth`.

### `RESOLVED` — COOLO CS-201Z soil sensors

Version 2.1.2 added `_TZE200_npj9bug3` and `_TZE200_wrmhp6b3` to the new
`TS0601_Soil_Coolo` group: https://community.hubitat.com/t/-/88093/686

### `DEVICE_LIMITATION` — On-demand illuminance refresh for contact/illuminance devices

The sleepy EF00 contact sensor reports illuminance only with contact activity. No known EF00 command
can request a current reading, and standard ZHA reporting configuration is not supported. See
https://community.hubitat.com/t/-/88093/73.

### `DEVICE_LIMITATION` — TS0201 LCZ030 temperature reporting granularity and cadence

The LCZ030 graph in [#696](https://community.hubitat.com/t/-/88093/696) shows long flat periods and
roughly 2 °F steps while adjacent sensors report smaller changes. The follow-up research found no
confirmed writable reporting delta or interval; Configure Reporting is ignored or rejected and the
approximately 1 °C threshold is firmware-controlled
([#697](https://community.hubitat.com/t/-/88093/697)). Periodic endpoint-2 polling is only a
hardware-verification experiment and may reduce battery life. The reporter replaced the sensor, so
there is no open implementation request unless another owner volunteers hardware for that test.

### `OUT_OF_SCOPE` — Use LCD sensor buttons as thermostat/scene controls

These buttons are local pairing or Celsius/Fahrenheit controls and do not report as Zigbee button
inputs. See the request and explanation at https://community.hubitat.com/t/-/88093/39 and
https://community.hubitat.com/t/-/88093/40.

### `OUT_OF_SCOPE` — Wi-Fi/Bluetooth variants sold under similar listings

Devices that do not contain a Zigbee radio cannot use this driver. Misleading marketplace listings
and Tuya Cloud alternatives are discussed at https://community.hubitat.com/t/-/88093/46 and
https://community.hubitat.com/t/-/88093/60.

---

## Part 2 — Bug backlog

Merged from `BUGS.md` on 2026-08-04 (deleted; do not recreate). Read `AGENTS.md` first — it explains
the model-group architecture, the DP map, and driver conventions. Ground rules carried over: fix one
item at a time in the order the user picks; the driver is uploaded to the dev hub and tested after
each fix; do not bump `VERSION`/`TIME_STAMP` or add header changelog lines unless told to; do not
change `ASK USER` items without an explicit answer. As of this merge, items A1–A2, B1–B8, C1–C7,
C10, C12 from the original list are fixed and confirmed (see `CHANGELOG.md`); only the three below
remain open.

### `[?]` `alarmTempPar` / `alarmHumidityPar` are dead preferences — **ASK USER**

`configParams` entries 13/14 have fully commented-out `limit` lists (never shown to any model group)
and are never read in `updated()` or `processTuyaDP()`. Their `initializeVars()` defaults also use
option **labels** (e.g. `'Below min temp'`) instead of keys — harmless while the preference stays
hidden, but the same landmine class as the fixed A2 bug if the `limit` list is ever re-enabled
without also fixing the defaults.

These are disabled LCZ030/Haozee alarm experiments — ask the user whether to delete them outright or
leave them as-is with an explanatory comment; do not re-enable the `limit` lists as a "fix."

### `[ ]` `isPendingConfig()` never settles for non-sleepy devices

For every model group *except* the `isConfigurableSleepyDevice()` set (`Zigbee NON-Tuya`,
`TS0201_TH`), `tempCfgOK`/`humiCfgOK` never flip to `true`, so `isPendingConfig()` stays `true`
forever and every received packet calls `ConfigurationStateMachine()`, which immediately returns
after a JSON parse for non-configurable devices. Cheap per call, but wasteful across the packet
volume of ~25 supported model groups.

Fix direction: guard the `ConfigurationStateMachine()` call site with `isConfigurableSleepyDevice()`,
or set both `...CfgOK = true` in `resetStats()` for non-sleepy groups.

### `[ ]` Release hygiene — `readme.md` revision history is stale

`readme.md`'s revision history ends at 1.8.1 and the supported-models table lacks everything added
since. Update at the next release point, not as a standalone commit.

- Specific gap carried over from the driver's own header TODO: update the GitHub wiki/documentation
  for TS000F `_TZ3218_7fiyo3kv` (MHCOZY switch with temp sensor, `DS18B20` group, added v1.6.2) —
  never documented externally.

The `packageManifest.json` **2.0.1** vs driver **2.2.0** gap is **deliberate, not a defect** — see
root `PUBLISHING.md` §*HPM manifest version policy*. kkossev raises the HPM manifest rarely, only
for major fixes, and tells users to update manually or run HPM **Repair**. Never file or "fix" a
manifest lag as a bug.

