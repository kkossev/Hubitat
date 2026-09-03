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

## [3.6.1] - 2026-08-23

This is the current development version — all work stays under this heading until a version bump
is explicitly requested, not a cut/released version with its own `[Unreleased]` above it.

Most of this entry is **shared-library** work: `commonLib` 4.1.1, `onOffLib` 3.2.4, `batteryLib`
3.2.4, `humidityLib` 3.3.1 and `temperatureLib` 3.3.2. Those reach every driver that embeds them.

### Added

- Zbeacon MS01 Motion Sensor, model `MS01`, manufacturer `zbeacon`.

### Fixed

- **Refresh now forces temperature and humidity events.** Pressing Refresh re-read the device, but
  an unchanged value was swallowed by the delta filter and no event was sent. `humidityLib` had no
  `isRefresh` bypass at all, and `temperatureLib` bypassed only the delta filter, so a Refresh
  within 10 s of the last temperature event was still queued behind the minimum reporting interval.
  All three sensor libraries now use the same two-gate bypass already proven in `illuminanceLib`:
  `isRefresh` skips both the delta filter and the minimum interval, and the event carries a
  `[refresh]` suffix with `isStateChange`. Confirmed on a SiHAS `USM-300Z`, where one Refresh
  published illuminance, temperature and humidity immediately.
- **Battery percentage no longer reads one step low on non-Tuya devices.** The ZCL half-percent
  attribute 0x0021 was converted with `(rawValue / 2) as int`, which truncates: raw 1 (0.5 %) became
  `0 %` and raw 3 (1.5 %) became `1 %`. Now rounded, matching zigbee-herdsman-converters. Tuya
  devices report 0–100 directly and are unaffected.
- **Two unquoted `respondsTo()` arguments that could throw `NullPointerException`.** A bare
  identifier resolves to `null` in the Hubitat sandbox, and `respondsTo(null)` throws — verified on
  hardware, see below. `commonLib.standardProcessTuyaDP()` would have thrown on every Tuya EF00
  datapoint in drivers that embed `commonLib` without `deviceProfileLib`, and `onOffLib.on()` /
  `off()` would have thrown for on/off-capable drivers embedding `onOffLib` without it. This driver
  was never affected — its `customProcessTuyaDp()` returns before the line is reached.
- **`TS0202_4IN1` no longer advertises a `reportingTime4in1` command that cannot execute.** The
  profile's `commands` map named a method that does not exist, so invoking it threw
  `MissingMethodException` (caught and logged). Removed; the Reporting Interval remains fully
  settable through the dp:102 preference of the same name.

### Notes

- The Hubitat sandbox's handling of unknown bare identifiers was settled by testing on the hub: an
  unknown identifier resolves to **`null`** — not to its own name as a String, and with no
  `MissingPropertyException`. `respondsTo(bareName)` happens to work when the named method exists,
  but throws `NullPointerException` when it does not. Details in `Libraries\BUGS.md`.
- **Known issue introduced by the Refresh change:** `isRefresh` stays true for the whole refresh
  window, so a device that answers one cluster twice — an unsolicited report plus the read response
  — now publishes two events instead of one. Seen with humidity on the SiHAS `USM-300Z`. Not yet
  decided whether to accept it or gate the bypass to the first event per attribute.

---

## [3.6.0] - 2026-08-05

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
- Four diagnostic attributes — `unknown_3`, `unknown_80`, `unknown_101` and `unknown_102` — carrying
  the PGST datapoints that are received but not yet decoded. They are declared for the whole driver
  but only ever populated by the `TS0601_PGST_PIR_SIREN` profile, so no other device shows them.

### Changed

- `TS0601_PGST_PIR_SIREN` extended from the first real device log (`@calinatl`, 2026-08-05).
  DP 1 = motion is now **confirmed**, not a guess. DP 9 (sensitivity, 0–2) and DP 10 (keep time,
  0–3) are mapped as writable preferences on the strength of the Tuya convention and the values the
  device reported; DP 3, 80, 101 and 102 are mapped read-only to the `unknown_*` attributes. DP 80
  is in `spammyDPsToNotTrace` because the device repeats it, unchanged, every 3.4 seconds.
  The siren datapoint is still unidentified — DP 102 (boolean) is the leading candidate.
- **Reset Motion to Inactive** now defaults to *on* for `TS0601_PGST_PIR_SIREN` only. No motion
  inactive report was seen in that device's first log, so without the software reset the sensor
  would stay active indefinitely. Every other profile keeps the `false` default.
- The **Motion Reset Timer** description now states that the value must be set *longer* than the
  sensor's own Keep Time. A shorter software timeout resets motion while the sensor still considers
  it active, and the sensor's real "inactive" report is then discarded as a duplicate. The default
  is unchanged at 60 seconds, which matches the most common hardware keep time in this driver.
- The **Reset Motion to Inactive** description now says *for which* sensors `false` is the right
  value — those that report motion inactive on their own — rather than recommending it flatly.
- The administrative commands list moved off the **Configure** command onto a new **Device
  Utilities** command. `Configure` is once again a plain button that only ever configures the
  device; *** LOAD ALL DEFAULTS *** and the *Delete All …* actions now live under Device Utilities.
  Running Device Utilities without choosing anything prints the list of available commands instead
  of doing something.

  The reason is not cosmetic. `configure` was declared twice — as the Configuration capability's
  no-argument command and as an ENUM-parameter command — so which of the two methods ran depended on
  whether the platform happened to supply an argument. That implicit dispatch is what allowed a
  single metadata attribute to turn an ordinary click into a full wipe (see below).

  Anywhere that used to say "select *** LOAD ALL DEFAULTS *** from the Configure dropdown" now means
  the **Device Utilities** dropdown.
- The **Configure** button now carries a warning that it cannot configure battery-powered "sleepy"
  devices, and that the way to configure one is to pair it again without deleting it first. The
  **Ping** icon changed from a satellite dish to antenna bars, which reads as a reachability check.
- **Send Command** and **Set Par** now document themselves: their help text says the valid names
  come from the active device profile, and that leaving the name empty lists them in the log. Set
  Par additionally notes that leaving the value empty reports the allowed range for that parameter,
  and that the value is written to the device rather than only stored. All of that behaviour already
  existed but nothing mentioned it.
- The **Device Profile** preference now explains what a profile is and that it is normally matched
  automatically, and carries the step that was missing: after forcing a profile by hand you have to
  pair the device again, without deleting it, or the new configuration never reaches a battery-
  powered sensor. Its title is prefixed with ⚠️.
- New **Load All Defaults** button — a one-click shortcut for the Device Utilities entry everyone is
  told to run after switching drivers. It is destructive and has no confirmation step, so its help
  text spells out that preferences, states, scheduled jobs and child devices are all deleted.
- Do **not** add `defaultValue` to an ENUM command parameter. It was tried on the **Configure**
  command to preselect *** LOAD ALL DEFAULTS ***, and the platform behaves in the worst possible
  way: the dropdown still displays `- No selection -`, but pressing Run with nothing selected
  submits the `defaultValue` anyway. On a C-8 Pro running 2.5.1.143 that silently wiped a device's
  settings, states and child devices. The attribute has been removed and a warning comment left in
  its place.
- The two blank separator rows are gone from the **Configure** command list. Selecting one had never
  worked: `configure()` invokes the mapped function with no arguments, while `configureHelp()`
  requires one, so it only ever threw into the surrounding try/catch.

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
- The **Reset Motion to Inactive** preference is visible again for `SONOFF_MOTION_IAS`, which had it
  in the legacy 1.x driver but lost it in V3, and is now also offered for `RH3040_TUYATEC`, which
  never had it. Both are IAS PIRs with a roughly 60-second hardware re-trigger period, and they were
  the only two motion profiles that hid the toggle. The default is unchanged — off — so nothing
  happens until an owner turns it on.
- The **Motion Reset Timer** input was gated on `settings?.motionReset?.value`, reading a `.value`
  property that a Groovy `Boolean` does not have — the same setting is read as plain
  `settings?.motionReset` by the code that actually schedules the reset. Had that expression ever
  resolved to null, the timer input would have disappeared from the page while the software reset
  kept running off the stored value, with no way to see or change it. Both places now read the
  setting the same way.
- `NullPointerException: Cannot get property 'isDepricated' on null object` in
  `localProcessTuyaDP()` when datapoint 0x65 arrives on a device whose profile did not resolve.
  `DEVICE?.device.isDepricated` guarded only the first dereference; `DEVICE` itself is `null`
  whenever `state.deviceProfile` is `UNKNOWN`, and the datapoint was then dropped entirely.
- `customParseOccupancyCluster()` failed to publish on the hub ("current scope already contains a
  variable of the name value") because two `else if` branches each redeclared a local `value` that
  was already declared earlier in the method. Renamed the two inner locals.

### Developer notes

- Library changes in this release: `commonLib`, `deviceProfileLib`, `iasLib`, `illuminanceLib` and
  `motionLib` (3.2.3) and `commonLib` (**4.1.0** — the minor bump reflects the `configure` /
  `deviceUtilities` command split, which changes the device page of every driver that embeds it).
  The rest are log-string changes except the `motionLib` preference-visibility fix above. Both
  libraries also reach the **Tuya Zigbee mmWave Sensor** driver, whose bundle needs regenerating for
  any of it to take effect there.
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
