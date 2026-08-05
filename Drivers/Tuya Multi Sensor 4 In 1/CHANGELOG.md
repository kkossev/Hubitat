# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project follows Semantic Versioning where applicable.

**Scope.** This file tracks the **V3 driver** — developed as
`Tuya Multi Sensor 4 in 1 (V3).groovy` and distributed as the generated bundle
`Tuya Multi Sensor 4 In 1.groovy`. The legacy 1.x monolithic driver
(`Tuya_Multi_Sensor_4_In_1__ver_1_9_2.groovy`) is retained for users who have not migrated; its
history is summarized under *Earlier releases* below, with the full per-patch detail in `README.md`.

Several entries change **shared libraries** in `C:\work\Hubitat\Libraries\`. Those reach every
driver that embeds them, not only this one, and take effect for users only after each affected
driver's bundle is regenerated.

## [3.6.0] - 2026-08-05

This is the current development version — all work stays under this heading until a version bump
is explicitly requested, not a cut/released version with its own `[Unreleased]` above it.

### Added

- Fingerprint for the newer `_TZE200_3towulqd` firmware revision, which pairs with
  `inClusters: 0000,0003,0500,0001,0400` and no outClusters. Without it Hubitat matched no
  fingerprint and fell back to its built-in "Human Presence Sensor" driver.
- **Illuminance Interval** preference for the `TS0601_2IN1` group, exposing Tuya DP 102
  (1–720 minutes). This is the only control that reduces reporting at the source; the lux threshold
  is a change filter, not a rate limit.
- Six additional manufacturers to the `TS0601_2IN1` group, matching the upstream
  zigbee-herdsman-converters `ZG-204ZL` definition: `_TZE200_ttcovulf`, `_TZE200_gjldowol`,
  `_TZE200_jxyhl4eq`, `_TZE200_qxyh4r7g`, `_TZE200_na5qlzow` and `_TZE200_s6hzw8g2`
  (Nedis ZBSM20WT).
- Datapoint 101 as an illuminance source, used by `_TZE200_s6hzw8g2` instead of DP 12.
- New device profile `TS0601_PGST_PIR_SIREN` for the PGST Zigbee PIR+siren combo sensor
  (`_TZE284_zmgahdog`, `TS0601`). Motion-only — siren is intentionally unsupported. No public
  datapoint map exists for this device, so only DP 1 (motion, the Tuya convention default) is
  mapped; `queryAllTuyaDP` runs on refresh to help identify the remaining DPs from a real device
  log. See TODO.md I.4.

### Changed

- `TS0601_2IN1` devices now ignore the duplicated ZCL 0x0400 illuminance report and use the Tuya
  datapoint only, via a new `ignoreZclIlluminance` device-profile property. These sensors transmit
  every reading twice, roughly half a second apart, and the two channels can differ by about 1 lx
  because of the ZCL log-scale encoding.
- The Illuminance Interval description now states the factory default, that raising the value is
  what reduces reports, and that the write is only accepted while the PIR is awake.

### Fixed

- Save Preferences no longer logs a spurious "no commands to send" warning. `customUpdated()`
  appended the return value of a `void` method to its command list.
- Corrected log statements that printed `null` or referenced out-of-scope variables:
  occupancy-cluster trace, `setPar()` custom-function name, `zclWriteAttribute()` exception,
  `setDeviceNameAndProfile()` model/manufacturer, IAS read-attribute response, the health-check
  scheduling message, and the short Tuya frame warning.
- Corrected the `(DP=0x69)` label on the 4-in-1 lux calibration datapoint, which is 0x6A.
- Corrected an always-true condition in `compareAndConvertStrings()` (trace output only).
- Corrected the "Huidity Calibration" title typo.
- `customParseOccupancyCluster()` failed to publish on the hub ("current scope already contains a
  variable of the name value") because two `else if` branches each redeclared a local `value` that
  was already declared earlier in the method. Renamed the two inner locals.

### Developer notes

- Library changes in this release: `commonLib`, `deviceProfileLib`, `iasLib` and `illuminanceLib`.
  All are log-string or dead-code changes; no code path is altered.
- Removed dead `if (val > 4294967295)` guards from the 4-in-1 calibration datapoints — an `int`
  cannot exceed 2³¹−1 and the constant is off by one.
- Replaced a bare `NULL` identifier and a stray `l` statement that had only ever worked because the
  Hubitat sandbox resolves unknown identifiers to `null`.
- Cluster lists for the six new manufacturers are **unverified** — no owner has reported pairing
  information, so they use the family default. A wrong list only prevents automatic driver
  selection; the runtime profile match keys on model and manufacturer alone and is unaffected.
- The `ESRESSIF_PIR_TEMP` profile key retains its typo deliberately: renaming it would orphan
  `state.deviceProfile` on existing devices.

## [3.5.8] - 2026-08-03

### Changed

- `TS0202_MOTION_SWITCH` (Linkoze LKMSZ001) datapoint 102 now maps to an `illumState` enum
  (`dark`/`light`) instead of a fake lux value, and the profile no longer claims the
  `IlluminanceMeasurement` capability.
- The lux threshold preference default is now 10 lx in the user interface, matching the value the
  code has always enforced.
- A refresh now bypasses both the illuminance change filter and the minimum reporting interval, so
  Refresh produces an event immediately even when the value has not changed.

### Added

- **Illuminance Minimum Reporting Time** preference — a dedicated rate limit for illuminance
  events, independent of the shared minimum reporting time used by temperature and humidity.
  Shown under Advanced Options.

### Fixed

- A lux threshold of `0`, meaning no filtering, is no longer silently replaced by the default.
- The first illuminance report after pairing or a profile change is now published instead of being
  compared against a fabricated baseline of zero. With a high threshold this previously left the
  attribute permanently uninitialized.
- Refresh no longer throws for profiles that declare a refresh list but no attributes.
- `setPar()` no longer reports failure on the success path for Tuya datapoint writes.
- `TS0601_PIR_AIR` enum parameters no longer reject their highest values.
- The `localProcessTuyaDP()` fallback no longer calls methods that do not exist.
- Preference defaults declared in a device profile are now applied to the generated input.
- The duplicate-value check for illuminance no longer compares a raw datapoint value against a
  coefficient-corrected attribute.

### Developer notes

- Library versions: `illuminanceLib` 3.2.2, `deviceProfileLib` 3.5.7.
- `illuminanceInitializeVars()` now gates on the device profile capability rather than a test that
  was always true, so illuminance settings are no longer created on motion-only profiles.

## [3.5.7] - 2026-07-31

### Added

- HOBEIAN ZG-204ZX fingerprint to the `TS0601_TZE284_4IN1` profile.

## [3.5.6] - 2026-06-04

### Added

- `TS0601_TZE284_4IN1` profile for the `_TZE284_gnpflcoq` 4-in-1 mmWave radar sensor.

## [3.5.5] - 2025-10-20

### Added

- IMOU Motion Sensor ZP1, model `ZP2-EN`, manufacturer `MultIR`.

## [3.5.4] - 2025-10-03

### Added

- HOBEIAN 2-in-1 sensor, model `ZG-204ZL`, to the `TS0601_2IN1` profile group.

### Developer notes

- Note the `ZL` suffix; it is a distinct model from `ZG-204ZM`.

## [3.5.3] - 2025-09-15

### Changed

- Aligned with `commonLib` 4.0.0.

## [3.5.2] - 2025-07-14

### Fixed

- `sendDelayedBatteryEvent` exception.

## [3.5.1] - 2025-04-25

### Fixed

- Workaround for the decimal preference range change in Hubitat platform 2.4.1.x.

## [3.5.0] - 2025-04-08

### Fixed

- Urgent fix for `java.lang.CloneNotSupportedException`.

## [3.4.1] - 2025-03-29

### Added

- Custom configuration function for Espressif devices.

## [3.4.0] - 2025-03-03

### Added

- `customConfigureDevice()`.
- SNZB-03P device profile.

### Fixed

- SNZB-03 configuration bugs.

## [3.3.3] - 2025-01-29

### Changed

- Moved TS0601 `_TZE200_ppuj1vem` to the `TS0601_2IN1_MYQ_ZMS03` device profile.

## [3.3.2] - 2024-11-30

### Added

- Azoula Zigbee 4-in-1 Multi Sensor, model `HK-SENSOR-4IN1-A`, manufacturer `Sunricher`, to the
  SiHAS group.

## [3.3.1] - 2024-10-26

### Added

- TS0601 `_TZE200_f1pvdgoh` in a new `TS0601_2IN1_MYQ_ZMS03` device profile group.

## [3.3.0] - 2024-08-30

### Changed

- Main branch release.

## [3.2.3] - 2024-07-27

### Added

- Sonoff SNZB-03P.

## [3.2.2] - 2024-07-05

### Added

- Created `motionLib`.

### Fixed

- Restored the `all` attribute.

## [3.2.1] - 2024-05-31

### Added

- New `RH3040_TUYATEC` device profile group.
- SiHAS device support.

### Changed

- Aligned with `commonLib` 3.2.1.

### Developer notes

- 2-in-1 `_TZE200_3towulqd` tested.

## [3.2.0] - 2024-05-26

### Added

- First version of the V3 architecture, based on the mmWave radar driver code.
- TS0202 `_TYZB01_vwqnz1sn`.

### Removed

- Deprecated Linptech radar support.

## Earlier releases

Versions 1.0.0 (2022-04-16) through 1.9.2 (2024-06-15) were the monolithic pre-library driver.
That line is closed; the file is kept only for users who have not migrated to 3.x. The complete
per-patch history is in `README.md` and in the header of
`Tuya_Multi_Sensor_4_In_1__ver_1_9_2.groovy`. In outline:

- **1.0.x** (2022) — initial release; IAS motion sensor fingerprints for TS0202, TS0210 and RH3040;
  `setMotion` command; the first mmWave radar and human presence sensor support.
- **1.1.x – 1.2.x** (2022–2023) — `setPar()` command; `healthStatus` and the Health Check
  capability; 4-in-1 device support; illuminance event fixes.
- **1.3.x** (2023) — introduced **device profiles**; `batteryVoltage`, `tuyaVersion` and delayed
  battery events; `invertMotion`; the 4-in-1 reporting-time parameter.
- **1.4.x** (2023) — TS0225 24GHz radars; illuminance correction coefficient; the
  "Motion Sensor and Scene Switch" driver clone.
- **1.5.x – 1.6.x** (2023) — `deviceProfilesV2` refactoring with `tuyaDPs`; major preference-input
  rework with defaults reset on profile change; the `all` attribute; human-readable
  `motionStarted`; SONOFF, SiHAS and configurable-IAS profiles.
- **1.7.x – 1.8.x** (2024) — Groovy linting and assorted exception fixes.
- **1.9.x** (2024) — **all radars except Linptech deprecated**; preferences no longer sent to
  deprecated devices; final release 1.9.2.

mmWave radar support has since moved out of this driver entirely, into the separate
**Tuya Zigbee mmWave Sensor** driver.
