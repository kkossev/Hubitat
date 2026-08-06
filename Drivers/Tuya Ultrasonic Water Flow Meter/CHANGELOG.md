# Changelog

All notable changes to this driver are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project follows Semantic Versioning where applicable.

**Scope.** This is a **V3-architecture driver** — `Tuya Ultrasonic Water Flow Meter.groovy` is the
development file and pulls `kkossev.commonLib` + `kkossev.deviceProfileLib` via `#include`. There is
no `*_lib_included.groovy` bundle yet; one has to be built by hand before the driver can be pasted
into the Hubitat "Drivers Code" editor or published to HPM. See `AGENTS.md` for the architecture and
`TODO.md` for the open work-list.

Community thread: https://community.hubitat.com/t/tuya-smart-zigbee-ultrasonic-water-meters/142433

## [3.4.0] - 2026-08-05

This is the current development version — all work stays under this heading until a version bump is
explicitly requested, not a cut/released version with its own `[Unreleased]` above it.

**Not yet hub-tested.** Every item below is verified only against the raw datapoint frames captured
in [post #25](https://community.hubitat.com/t/-/142433/25) and against the Zigbee2MQTT converters.
No maintainer-owned hardware exists for this device family.

### Added

- Support for **`_TZE284_ajlu4cud`** (TS0601 ultrasonic water meter, no valve), reported in
  [post #25](https://community.hubitat.com/t/-/142433/25). The device previously matched no
  water-meter driver at all, so Hubitat paired it to the *Tuya Smart Siren Zigbee* driver, which
  decoded its datapoints as siren functions ("Solar Alarm state is Alarm Sound", "confirmed melody
  1=Doorbell 1") and threw `NumberFormatException: For input string: "0E"` on the meter-ID string.
  That log turned out to contain the complete raw EF00 payload for every datapoint the meter sends,
  so the decoding below is derived from real bytes rather than inferred.
- Support for **`_TZE284_vuwtqx0t`**, the `_TZE284_`-prefixed encoding of the existing
  `_TZE200_vuwtqx0t` meter-with-valve. Both fingerprints are listed by the Zigbee2MQTT
  `TS0601_water_valve` definition.
- The single `ULTRASONIC_FLOW_METER` profile was split into two, so the meter-only variant no longer
  advertises datapoints it does not have:
  - `TS0601_WATER_METER_VALVE` — `_TZE200_vuwtqx0t`, `_TZE284_vuwtqx0t`, `_TZE200_zlwr0raf`.
    Full datapoint set including dp13 (valve), dp14 (auto clean) and dp15.
  - `TS0601_WATER_METER` — `_TZE284_ajlu4cud`. Same set minus 13/14/15.
- Standard capabilities. The driver previously declared **none at all** (the metadata block said
  "no standard capabilities"), and `processFoundItem()` gates event sending on
  `device.hasAttribute(name)`, so essentially every report was being dropped. Added `Sensor`,
  `Actuator`, `Refresh`, `Configuration`, `Battery`, `TemperatureMeasurement`, `PowerSource`,
  `Valve`, `LiquidFlowRate` and `HealthCheck`.
- `open()` / `close()` valve commands (dp13, `DP_TYPE_BOOL`), following the pattern used by the
  sibling *Tuya Zigbee Valve* driver. On a profile without dp13 they log a warning and send nothing.
- Battery percentage, derived from the dp26 voltage over a **2.5–3.7 V** range (the 3.6 V ER14505
  lithium cell these meters use). `batteryLib.sendBatteryVoltageEvent()` is deliberately not used:
  it expects 0.1 V units and clamps to 2.2–3.2 V. `batteryLib` is not `#include`d at all — the
  meters expose no ZCL Power cluster.
- `volumeUnit` preference (`m3` default / `L`) applied to `waterConsumed`, `monthConsumption`,
  `dailyConsumption` and `reverseWaterConsumed`. The datapoints always carry liters; m³ matches the
  meter's own LCD and the Zigbee2MQTT `TS0601_water_meter` converter.
- `reportPeriod` (dp4) and `autoClean` (dp14) are now profile `preferences`, so `inputIt()` renders
  them and `updateAllPreferences()` writes them to the device with no new code.
- `faults` attribute — dp5's fault bitmap decoded to a comma-separated list, mirroring the
  Zigbee2MQTT fault map (`battery_alarm`, `magnetism_alarm`, `cover_alarm`, `credit_alarm`,
  `switch_gaps_alarm`, `meter_body_alarm`, `abnormal_water_alarm`, `arrearage_alarm`,
  `overflow_alarm`, `revflow_alarm`, `over_pre_alarm`, `empty_pipe_alarm`, `transducer_alarm`).
  `no_alarm` when the bitmap is zero.
- `healthStatus` and `rtt` attributes — commonLib already sends both and they were being dropped.
- `test()` now replays the eleven datapoint frames captured from a real `_TZE284_ajlu4cud`
  (`TEST_FRAMES`), instead of calling an undefined `testFunc()` in a 100-iteration benchmark loop.
  Available only when `_DEBUG = true`.

### Changed

- Header, comments and `importUrl` no longer identify the file as the *Tuya Zigbee Chlorine Meter* —
  the driver was copy-pasted from it in 2024 and never rebranded. `importUrl` pointed at the
  Chlorine Meter's amalgamated file on GitHub.
- `PollingIntervalOpts.defaultValue` changed from `300` (every 5 minutes) to `0` (Disabled). These
  are sleepy battery end devices that transmit on their own 1h–24h report period; polling wastes
  battery and mostly goes unanswered. The 5-minute option remains selectable.
- `customInitializeVars()` no longer hardcodes `setDeviceNameAndProfile('TS0601', '_TZE200_vuwtqx0t')`
  as a leftover test default — it now resolves the profile from the joined device's own `model` /
  `manufacturer` data values, falling back to `TS0601_WATER_METER_VALVE` only if that lookup fails.
- Attribute `reverseWaterConsumption` renamed to `reverseWaterConsumed`, matching the Zigbee2MQTT
  name. Safe to rename: the driver has never been published.
- `customProcessDeviceProfileEvent()` lost its single-`default` switch statement, which did nothing.

### Fixed

- **dp26 scale was `1000`, giving 0.342 V instead of 3.42 V.** Corrected to `100`.
- **dp4 (`reportPeriod`) never worked.** It used a key named `enumMap` with the mapping inverted
  (`['1h':0, …]`); the profile library reads `foundItem.map` in value→label direction, so the entry
  was silently ignored. Now `map:[0:'1h', 1:'2h', 2:'3h', 3:'4h', 4:'6h', 5:'8h', 6:'12h', 7:'24h']`
  with `rw:'rw'`.
- **dp13 / dp14 would have thrown.** Both were `type:'bool'` with no `map`, and the library's `bool`
  case evaluates `foundItem.map[fncmd as int]` — a subscript on null. Both are now `type:'enum'`
  with an explicit map, and dp13 was renamed `state` → `valve` (the old name shadows the driver's own
  `state` map, and `bool` is not a valid Hubitat attribute type either).
- **dp5 (`warning`) never sent an event.** It was `type:'bitmap'`, which has no case in
  `compareAndConvertTuyaToHubitatEventValue()`; the `default` branch hardcodes `isEqual = true`.
  It is now decoded in the driver as the `faults` string attribute.
- **dp16 (`meterId`) was decoded as a number.** It is a 14-byte UTF-8 string (Tuya dtype 3); the
  attribute is now `string` and the payload is converted from hex to ASCII in the driver.
- **dp1 / dp2 / dp3 / dp18 / dp21 are no longer decoded by `getTuyaAttributeValue()`.** That helper
  accumulates big-endian over the whole payload into an `int`; for the 8-byte dp2/dp3 blobs the high
  bytes are multiplied by `256^4`, which overflows to zero, so it returns the last 4 bytes — the
  right answer, but by accident, and completely wrong for the 14-byte dp16 string. These datapoints
  now read their raw bytes explicitly (`getTuyaDpPayload()` / `lastUInt32BE()`).
- Attributes `warning` (`bitmap`) and `state`/`autoClean` (`bool`) used **types Hubitat does not
  accept**; `reportPeriod` was declared `enum` with no options list.

### Developer notes

- `commonLib.standardParseTuyaCluster()` calls `standardProcessTuyaDP(descMap, dp, dp_id, fncmd)`
  **without** `dp_len` and without the chunk offset, so `customProcessTuyaDp()` always receives
  `dp_len = 0`. Any driver that needs raw payload bytes must re-walk `descMap.data` itself — that is
  what `getTuyaDpPayload()` does, mirroring the library's own loop so multi-datapoint frames work.
- Decoding was verified offline with Groovy 4 by extracting the real helper functions from this file
  and replaying the captured frames: `faults` → `empty_pipe_alarm,transducer_alarm` (0x1800, correct
  for a meter sitting on a bench), `meterId` → `00000026009162`, dp22 → 32.8 °C, dp26 → 3.42 V →
  77 % battery, dp4 → `12h`. Synthetic non-zero values confirm 324 L → `0.324 m³` / `324 L` and
  1500 L/h → 25 LPM; multi-datapoint frames, truncated frames and `0xFFFFFFFF` all behave.
- The driver passes Groovy parse + semantic analysis. `npm-groovy-lint` has not been run.

## [3.3.2] - 2024-09-30

Initial stub, never published. Added to the repository on 2024-10-05 as an incidental part of an
unrelated multi-driver commit (`919542c`, "Zigbee TRVs and Thermostats"), and untouched for the
following 22 months.

### Added

- `deviceProfilesV3` skeleton with a single `ULTRASONIC_FLOW_METER` profile, fingerprints for
  `_TZE200_vuwtqx0t` (Tuya 214C) and `_TZE200_zlwr0raf` (Tuya 213E), and a first datapoint table
  derived from [zigbee2mqtt#21255](https://github.com/Koenkk/zigbee2mqtt/issues/21255).
- Standard V3 plumbing: `customProcessTuyaDp`, `customRefresh`, `customUpdated`,
  `customInitializeVars`, `customParseZdoClusters`, polling scheduler.

### Known limitations of this version

Header, comments and `importUrl` still identified the file as the *Tuya Zigbee Chlorine Meter* it was
copied from; no capabilities were declared, so no events reached the device page; and several
datapoint definitions were unusable (see the Fixed section of 3.4.0).
