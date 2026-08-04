# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project follows Semantic Versioning where applicable.

**Scope.** This is a **single-file legacy driver** —
`Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy` is the whole driver, no
libraries and no separate generated bundle to keep in sync. See `BUGS.md` and `AGENTS.md` in this
folder for the model-group architecture and a maintainer's bug-fix work list.

## [Unreleased]

Changes for the next release will be documented here.

### Added

- TS0222 `illuminanceSensitivity` preference is now written to the device
  (`zigbee.writeAttribute(0x0400, 0xF001, ...)`), not just read back from it. The data type
  (`UINT8`) is a best guess — `0xF001` is not a documented ZCL attribute and has no equivalent in
  `zigbee-herdsman-converters` — pending confirmation on a real device.

### Fixed

- `_TZ3000_qaaysllp` (`TS0201_LCZ030`): stopped sending Tuya EF00 datapoints `09`/`0A`/`0B`
  (temperature scale, max/min temperature alarm) to this device, which advertises no EF00 cluster;
  the corresponding Minimum/Maximum Temperature Alarm preferences no longer appear for this model
  group either.
- ZDO Simple Descriptor Response (`0x8004`) no longer throws `MissingMethodException` — removed a
  call to an undefined `parseSimpleDescriptorResponse()` method that aborted `parse()` for that
  message.
- `_TYST11_pisltm67` (AUBESS light sensor) now correctly auto-detects as `TS0601_AUBESS` instead of
  falling through to `UNKNOWN`, where its illuminance-status datapoint was misparsed as a
  temperature event and its lux datapoint as humidity.
- The Read Attribute Response "unsupported Attribute" warning log now shows the actual cluster ID
  instead of an undefined variable that silently resolved to blank/`null`.
- Round-trip-time (`rtt`) calculation now uses `long` arithmetic (a new `safeToLong()` helper)
  instead of truncating epoch milliseconds to `int`, which only produced a correct result by
  coincidence and would misbehave if a ping straddled a 2^32 ms boundary.
- `_TZE284_myd45weu`: removed an incorrect "Soil Sensor II" (`ED00`-cluster) fingerprint. Verified
  against `zigbee-herdsman-converters` upstream: this manufacturer only ever maps to the plain
  `TS0601_soil` device family (temperature datapoint used raw, i.e. divide-by-1), never
  `soil_2`/`soil_3` (`divideBy10`) — matching this driver's existing `Models` mapping, which was
  already correct.
- Datapoint `0x65` (101) no longer sends a spurious `illuminance` event for every model group — now
  scoped to `TS0601_Contact` (`_TZE200_pay2byax`, `_TZE200_nvups4nh`), the only group that actually
  uses it. Soil/DS18B20/TS0201_TH devices reporting this DP for their own purposes (temperature
  alarm state, work mode, C/F scale) no longer also get a bogus illuminance reading.
- `temperatureUnit` preference default is now seeded with the option key (`'0'`) instead of the
  label `'Celsius'`, fixing a `NumberFormatException` in `updated()` on any `TS0201_TH` device that
  never manually set the preference — which silently aborted all reporting configuration (temp,
  humidity, battery) for that device. The read is also now defensive (`safeToInt`) against devices
  already carrying the bad value.
- `initialize()` no longer runs the full preference/state wipe twice — it called
  `initializeVars(fullInit = true)` itself and then again via `installed()`.
- Corrected a stale comment ("turn off debug logging after 30 minutes") that didn't match the
  actual 24-hour (`86400` s) timeout, or the info log beneath it.
- Typos: `fingrprint` → `fingerprint` (fingerprint comments), `addedadd` → `added` (2.0.0 header
  line), `ThidReality` → `ThirdReality` (ThirdReality `deviceJoinName`s), `unparesed` →
  `unparsed` (log line), and the Humidity Alarm preference description, which incorrectly read
  "Temperature Alarm".

### Changed

- `getBatteryVoltageResult()` renamed to `sendBatteryVoltageEvent()` and changed from
  `private Map` (which never actually returned a value on either code path) to `private void`; its
  debug log now fires only for valid readings, after the `0`/`255` validity check instead of before
  it.

### Removed

- Duplicate, byte-identical `_TZE284_myd45weu` "Soil Sensor II" fingerprint line.
- Dead `// TODO - write attribute 0xF001, cluster 0x400` stub, mistakenly gated on
  `TS0601_Haozee` — which has no `0x0400` cluster and could never have used it.

## [2.1.2] - 2026-04-22

### Added

- COOLO CS-201Z (`_TZE200_npj9bug3`, `_TZE200_wrmhp6b3`) into a new `TS0601_Soil_Coolo` group.
- `soilMoisture` attribute.

## [2.1.1] - 2026-04-19

### Added

- Four switch child devices for `_TZ3218_ya5d6wth`.
- TS0601 `_TZE284_9ern5sfh` into a new `TS0601_Tuya_3` group.

### Fixed

- Assorted bug fixes.

## [2.1.0] - 2026-03-31

### Added

- TS0601 `_TZE284_hodyryli` (Tuya Temperature Humidity Sensor with External Probe) into a new
  `TS0601_ZTH03PRO` group, using a child device for the probe.

## [2.0.2] - 2026-02-01

### Fixed

- Null preference values causing `GroovyCastException` in `updated()` reporting configuration
  (safe defaults for sleepy devices and Haozee).

## [2.0.1] - 2025-12-22

### Added

- `respondToZdoRequests` preference (default: disabled).
- TS0222 `_TZ3000_hy6ncvmw` illuminance-only sensor.

### Fixed

- `temperatureSensitivity` preference being reset to zero.

## [2.0.0] - 2025-11-30

### Added

- Child switch device support for DS18B20-group devices (relay control via DP 1) and cluster
  `0x0006` (On/Off) parsing for DS18B20 relay-state reporting.
- NEO NAS-STH02B2 electrical conductivity/fertility/temperature/humidity sensor,
  TS0601 `_TZE284_rqcuwlsa`.
- `soilEC` and `soilFertility` attributes (`soilFertility` values: `normal`, `lower`, `low`,
  `middle`, `high`, `higher`).
- ZDO `0x0000` Network Address Response and `0x0002` Node Descriptor Response handlers, in an
  attempt to fix `_TZE284_rqcuwlsa` device disconnections; rate-limited to once per 10 seconds.

## Earlier releases

Versions 1.0.0 (2022-01-02) through 1.9.3 (2025-11-10) added the bulk of this driver's device
support. The full per-patch detail is in the version header of
`Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy`. In outline:

- **1.0.x** (2022) — initial release; core temperature/humidity/illuminance parsing; Fahrenheit
  scale support; `TS0201_LCZ030`, `TS0601_Contact` and `TS0601_AUBESS` model groups; Tuya command
  refactoring; temperature/humidity offsets; configured parameters re-sent on re-pairing.
- **1.1.x – 1.2.x** (2022–2023) — `_info_` attribute; delayed reporting configuration for sleepy
  devices on wake; multi-datapoint-per-command parsing; `TS0601_Soil` group.
- **1.3.x** (2023) — `healthStatus` and the Health Check capability; `ping()`/`rtt`
  round-trip-time; `resetStats` command; per-metric (T/H/I/battery) stat counters; `TS0601_Tuya_2`
  group.
- **1.4.x – 1.5.x** (2023–2024) — Groovy lint pass; `healthStatus` periodic-job scheduling fix;
  battery-reporting-period fix for non-Tuya devices.
- **1.6.x** (2024) — ThirdReality 3RTHS0224Z/3RTHS24BZ; DS18B20 temperature-only group; `TS0222`
  Tuya command `0x06` processing; `TS0222_Soil` group; `TS0601_Soil_II` group.
- **1.7.x – 1.8.x** (2024–2025) — `temperatureOffset`/`humidityOffset` moved outside
  `configParams`; `queryAllTuyaDPs()` on Refresh; HE platform 2.4.0.x compatibility patch;
  Temperature Unit preference for `TS0201_TH`; Ink-display T/H sensor (`_TZE204_s139roas`) support;
  TS0210 (`_TZ3000_1o6x1bl0`).
- **1.9.x** (2025) — a single release added 27 new device fingerprints across `TS0601` and
  `TS0201` variants; temperature/humidity offset bug fix; invalid humidity values corrected to 0%
  or 100% instead of ignored; DS18B20 humidity processing (`0x67` DP).
