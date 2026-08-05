# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project follows Semantic Versioning where applicable.

**Scope.** This is a **single-file legacy driver** —
`Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy` is the whole driver, no
libraries and no separate generated bundle to keep in sync. See `TODO.md` and `AGENTS.md` in this
folder for the model-group architecture and the maintainer work list.

## [2.2.0] - 2026-08-04

This is the current development version — all work stays under this heading until a version bump
is explicitly requested, not a cut/released version with its own `[Unreleased]` above it.

### Added

- **Temperature Decimal Places** and **Humidity Decimal Places** preferences (0–2 digits for
  temperature, default 1; 0–1 digits for humidity, default 0 — both matching prior behavior).
  Requested on the community thread: [post #695](https://community.hubitat.com/t/release-tuya-temperature-humidity-illuminance-lcd-display-with-a-clock-w-healthstatus/88093/695?u=kkossev).
  At 0 decimal places the reported value is a plain integer (e.g. `20`, never `20.0`), immune to any
  platform-level numeric-type coercion. `temperatureEvent()`/`humidityEvent()` now round via a
  shared `roundToDecimals()` helper (`BigDecimal.setScale(..., HALF_UP)`) instead of two hardcoded,
  ad hoc formulas; `descriptionText` for both now logs the same rounded value that is actually
  reported, instead of an unrounded one.
- Second fingerprint for `_TZ3000_utwgoauk`, matching its real signature (`model:'SNZB-02'`,
  `inClusters:'0000,0001,0003,0020,0402,0405'`) as read from a paired device's Hubitat Device Data
  panel — the existing fingerprint declared a different `model` (`TS0201`) and cluster set, so this
  device likely never matched it at pairing time. Reported on the community thread:
  [post #694](https://community.hubitat.com/t/-/88093/694).
- TS0222 `illuminanceSensitivity` preference is now written to the device
  (`zigbee.writeAttribute(0x0400, 0xF001, ...)`), not just read back from it. The data type
  (`UINT8`) is a best guess — `0xF001` is not a documented ZCL attribute and has no equivalent in
  `zigbee-herdsman-converters` — pending confirmation on a real device.
- New model group `TS0601_Illum_TH` and fingerprint for `_TZE204_rbbx5mfq` (Tuya illuminance/
  temperature/humidity sensor, DP 2/6/7). Confirmed via a Device Data screenshot plus
  `zigbee-herdsman-converters` (`TS0601_illuminance_temperature_humidity_sensor_2`: DP 2 =
  illuminance raw, DP 6 = temperature ÷10, DP 7 = humidity ÷10). Cluster list is unconfirmed (best
  guess from this driver's standard TS0601-EF00 template) — the reporter's screenshot didn't show
  it. Requested on the community thread:
  [post #691](https://community.hubitat.com/t/-/88093/691).
- New model group `TS0601_Soil_5IN1`, fingerprints for `_TZE284_hdml1aav` and
  `_TZE2841000000_hdml1aav` (Excellux ZS-300TF 5-in-1 soil tester), and three new attributes:
  `soilFertilityValue` (`number`, µS/cm — the existing `soilFertility` attribute is an incompatible
  `enum` used by `TS0601_Soil_NEO`, so it couldn't be reused), `waterWarning` and
  `soilFertilityWarning` (`enum`s). DP map confirmed via the poster's own raw "Tuya Dp Log"
  screenshot plus `zigbee-herdsman-converters` (model `ZS-300TF`): DP 3 = soil moisture, DP 5 =
  temperature ÷10, DP 15 = battery, DP 101 = humidity, DP 102 = illuminance, DP 111 = water_warning,
  DP 112 = soil_fertility, DP 116 = soil_fertility_warning — all raw except where noted. Requested
  on the community thread: [post #687](https://community.hubitat.com/t/-/88093/687).
  Deliberately **not** implemented: the writable `report_period`/calibration/threshold DPs (103-107,
  113-115) and DP 110 (`soil_warning`, meaning unclear even in z2m) — no forum evidence anyone needs
  them yet, logged only. The poster's "stops reporting after several minutes" complaint is not
  addressed by this fix — this device has no ZCL reporting cluster for
  `isConfigurableSleepyDevice()` to help with, so the dropout cause is still unknown.

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
  already carrying the bad value. Also resolves the `NumberFormatException` reported by Rxich on
  [GitHub issue #67](https://github.com/kkossev/Hubitat/issues/67) /
  [the soil-tester thread](https://community.hubitat.com/t/driver-for-tuya-soil-tester-sensor/156528/4)
  after manually forcing Model Group to `TS0201_TH` — unconfirmed by the reporter, but same root
  cause.
- `initialize()` no longer runs the full preference/state wipe twice — it called
  `initializeVars(fullInit = true)` itself and then again via `installed()`.
- Corrected a stale comment ("turn off debug logging after 30 minutes") that didn't match the
  actual 24-hour (`86400` s) timeout, or the info log beneath it.
- Typos: `fingrprint` → `fingerprint` (fingerprint comments), `addedadd` → `added` (2.0.0 header
  line), `ThidReality` → `ThirdReality` (ThirdReality `deviceJoinName`s), `unparesed` →
  `unparsed` (log line), and the Humidity Alarm preference description, which incorrectly read
  "Temperature Alarm".
- `_TZE200_ysm4dsb1` now auto-detects as `TS0601_Tuya` instead of falling through to `UNKNOWN`,
  confirmed against upstream `zigbee-herdsman-converters` (z2m model `RSH-HS06`).
- Removed a mistyped `_TZE0204_yjjdcqsq` fingerprint — no such manufacturer exists upstream; the
  real device (`_TZE204_yjjdcqsq`) was already fingerprinted and mapped to `TS0601_Tuya_2`.
- Humidity Sensitivity preference now actually takes effect for `TS0601_Tuya`/`TS0601_Tuya_2`
  devices (Tuya DP `0x14` write was previously sent only to `TS0601_Haozee`, even though the
  preference was shown for all three groups). `TS0601_Tuya_3` no longer shows the Temperature/
  Humidity Sensitivity preferences at all — checked against z2m, its only member has no confirmed
  support for either.

### Added

- A second fingerprint for the Zemismart ZXZTH (`_TZ3000_lfa05ajd`), matching the real cluster
  signature confirmed against ZHA quirks and blakadder's device database (`0402`/`0405`
  temperature/humidity clusters, model `TS0201`) — the original fingerprint declared unrelated
  illuminance/IAS-zone clusters and likely never matched real hardware. Both fingerprints are kept
  since it's unconfirmed which one (if either) the real device pairs with.
- Root cause of the `_TZE204_rbbx5mfq` issue above: the generic/`UNKNOWN` fallback's DP `0x02`
  handler assumed DP 2 is always humidity (true for most Tuya EF00 models, wrong for this device,
  where DP 2 is illuminance) — displayed "humidity" was actually tracking the illuminance reading,
  explaining both the wrong values and the "invalid humidity, clamped to 100%" warnings under
  bright light. DP `0x06`/`0x07` were unconditionally log-only for every model group (no event ever
  sent) — now gated so `TS0601_Illum_TH` routes them to real temperature/humidity events, unchanged
  for every other group.
- `_TZE200_rbbx5mfq` (added speculatively as `TS0601_Tuya` in v1.9.0, never verified): corrected to
  `TS0601_Illum_TH` — z2m lists it under the identical device definition as `_TZE204_rbbx5mfq`.

### Changed

- `getBatteryVoltageResult()` renamed to `sendBatteryVoltageEvent()` and changed from
  `private Map` (which never actually returned a value on either code path) to `private void`; its
  debug log now fires only for valid readings, after the `0`/`255` validity check instead of before
  it.
- Temperature rounding now uses `BigDecimal.HALF_UP` on the decimal value instead of the old
  `Math.round((x - 0.05) * 10) / 10` formula (an empirical fix for binary floating-point drift on
  raw doubles). At the unchanged default of 1 decimal place this is byte-identical for the vast
  majority of readings; the only observed differences are values landing almost exactly on a `.x5`
  boundary (e.g. `21.45`, rare in real sensor data), and whole-degree readings, which previously
  printed without any decimal (`20`) due to a Groovy integer-division quirk and now consistently
  show the configured decimal count (`20.0`).
- `_TZ3000_utwgoauk` moved from model group `TS0201` to `Zigbee NON-Tuya`, so it now gets the
  sleepy-device Configure Reporting handshake — a hypothesis-driven fix for reported connectivity
  dropouts, pending confirmation on real hardware (see `TODO.md` item 5). Revert if it doesn't help.

### Removed

- Duplicate, byte-identical `_TZE284_myd45weu` "Soil Sensor II" fingerprint line.
- Dead `// TODO - write attribute 0xF001, cluster 0x400` stub, mistakenly gated on
  `TS0601_Haozee` — which has no `0x0400` cluster and could never have used it.
- Dead `deviceProfilesV3` / `SONOFF_TEMP_HUMI` stub — an unreferenced draft from an abandoned V3
  migration attempt, never wired into any code path.
- Dead code with no callers/references: `switchEvent()`, `swapEndianHex()`, and the
  `@Field static String UNKNOWN` constant (the code used the literal `'UNKNOWN'` everywhere anyway).

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
