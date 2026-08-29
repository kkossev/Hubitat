# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project follows Semantic Versioning where applicable.

## [2.1.0] - 2026-08-29

This stays the working section for version 2.1.0 until a bump is explicitly requested.

### Added

- Added Tuya multi-channel meter support via a new `getTuyaMultiChannelProfile()` profile map:
  SPM02 (3-channel packed), SPM02V2 (3-channel scalar), and SDM02T (2-channel packed,
  `_TZE284_x8diwkqb`).
- Added per-channel `Generic Component Metering Switch` child devices for the multi-channel meter
  profiles, exposing channel power and energy.
- Added `deviceLocating` and `updateFrequency` preferences for multi-channel meters that support
  them.

### Fixed

- Fixed Tuya datapoint routing so multi-channel meter reports no longer fall through to unrelated
  generic branches.
- Fixed the ping RTT calculation.
- Fixed power factor decoding for the multi-channel meter profiles (DP 50/108/117) to read the raw
  value as signed instead of unsigned; the unsigned read previously produced garbage readings such
  as `4294967274 %` for any negative power factor.
- Fixed a `BigDecimal` scientific-notation formatting bug (for example `6E+1 Hz` instead of
  `60.0 Hz`, `0E+1 kWh` instead of `0.0 kWh`) affecting frequency, energy, and producedEnergy values
  across all `getTuyaMultiChannelProfile()` device parsers, caused by dividing a raw `long` by the
  Groovy `BigDecimal` literal `100.0`.
- `autoPollingEnabled` now defaults to off for every `getTuyaMultiChannelProfile()` device
  (previously this only happened inconsistently, via a `state.model == 'TS0601'` check in
  `initializeVars()` that a pre-existing saved preference value could bypass), since these devices
  push Tuya EF00 reports actively and do not need generic polling.

### Changed

- Demoted the per-channel voltage/amperage/producedEnergy/powerFactor "not exposed by Generic
  Component Metering Switch" logs from `info` to `debug`; they were logged unconditionally on every
  poll with no change-detection threshold.

### Developer notes

- Target device for this round of fixes: `_TZE284_x8diwkqb` (SDM02T), reported and tested by
  @justcliff on the Hubitat community forum. Tracked as TODO.md item 14 / Jira HUB-140, still marked
  VERIFY ON DEVICE.
- A negative total-power / total-power-factor reading observed on the reporter's device was traced
  to a reversed CT clamp on that installation, not a decoding defect — the per-channel packed
  voltage/current/power decoded correctly and matched a V×I×PF sanity check.
- New helper `isTuyaMultiChannelMeter()` is `getTuyaMultiChannelProfile() != null`; a fix scoped to
  "all multi-channel meter devices" should gate on that helper rather than one manufacturer id.

## [2.0.4] - 2026-08-07

### Fixed

- Minor bug fixes.

## [2.0.3] - 2026-04-20

### Fixed

- Fixed breaker threshold boolean variables that were declared as `int` in
  `processTuyaThresholdsCommands()`.
- AC frequency polling (`0x0300`) is now automatically disabled after the first
  unsupported-attribute response.

## [2.0.2] - 2025-08-19

### Fixed

- Attempted a fix for the Frient Monitor 2 (EMIZB-141).

## [2.0.1] - 2025-04-12

### Added

- Added Shelly 1PM (Gen 4) support.
- Added Frient Monitor 2 (EMIZB-141) support.

## [2.0.0] - 2024-11-30

### Added

- Added RTT measurement to the `ping()` command.
- Added TS011F `_TZ3000_ww6drja5`.

### Changed

- Improved compatibility with Hubitat Elevation platform version 2.4.0.x.
- Set the Third Reality 3RSP02028BZ energy divisor to 3600000.

## [1.9.8] - 2024-11-22

### Added

- Added TS011F fingerprints: `_TZ3000_xzhnra8x`, `_TZ3000_rdfh8cfs`, `_TZ3000_fgwhjm9j`,
  `_TZ3000_waho4jtj`.

## [1.9.7] - 2024-07-05

### Added

- Added SPM01 TS0601 support for `_TZE200_qhlxve78` and `_TZE200_bcusnqt8`.

## [1.9.6] - 2024-06-04

### Added

- Added the `setEnergy()` command.

## [1.9.5] - 2024-05-16

### Added

- Added Third Reality 3RSPE01044BZ Smart Plug E2 (European socket) support.

## [1.9.4] - 2024-04-14

### Added

- Added, for testing, TS0601 Zemismart Real-time Smart Energy Monitors: `_TZE204_ves1ycwx`,
  `_TZE200_ves1ycwx`, `_TZE200_v9hkz2yn`.

### Developer notes

- These three-channel meters are supported on the first channel only.

## [1.9.3] - 2024-04-07

### Fixed

- Fixed `setEnergyPrice()`.

## [1.9.2] - 2024-03-30

### Added

- Added TS0001 Tuya switch modules with power monitoring: `_TZ3000_mkhkxx1p`, `_TZ3000_tgddllx4`,
  `_TZ3000_x3ewpzyr`, `_TZ3000_qnejhcsu`, `_TZ3000_xkap8wtb`.

### Developer notes

- Additional Groovy linting pass.

## [1.9.1] - 2024-03-02

### Fixed

- Fixed a current-event bug.
- Fixed raw-value bugs in debug logs.

### Changed

- Reduced debug logging.

## [1.9.0] - 2024-01-18

### Added

- Added TS0601 `_TZE204_81yrt3lo` bidirectional energy meter with an 80 A current clamp (PJ-1203A).
- Added TS0601 `_TZE200_rks0sgb7` (one channel only).

### Developer notes

- Groovy linting pass.

## [1.8.0] - 2023-09-03

### Added

- Added TS011F `_TZ3000_qystbcjg` Somgoms ZigBee MCB Circuit Breaker DIN Rail.
- Added TS0601 `_TZE200_bcusnqt8` (partially matches the RTX Circuit Breaker profile).

## [1.7.8] - 2023-07-29

### Added

- Added TS0001 `_TZ3000_kqvb5akv`.
- Added the `setSwitchType` configuration command.

### Fixed

- Fixed a bug where a report containing multiple `0x0006` attributes was not processed correctly.

### Changed

- Suppressed some warning logs.

## [1.7.7] - 2023-06-10

### Added

- Added `isTuyaE00xCluster` processing of known attributes.
- Added Tongou SY2 over/under voltage/current/power protection parameter decoding and encoding.

### Changed

- Moved TS011F non-power-reporting plugs to the Zemismart driver.

## [1.7.6] - 2023-05-13

### Added

- Added partial support for TS0601 `_TZE200_abatw3kj` RTX Circuit Breaker 4x25A ZCB25-4P.
- Added Tongou SY2 `_TZ3000_cayepv1a`.
- Added threshold decoding for the RTX circuit breaker.

## [1.7.5] - 2023-05-12

### Added

- Added Tongou TO-Q-SY1-JZT DIN Rail Switch TS011F `_TZ3000_qeuvnohg`.
- Added toggles for enabling frequency, power factor, and temperature reporting (where supported).
- Added `_TZE200_fsb6zw01`.

## [1.7.4] - 2023-04-22

### Added

- Added TS011F Tuya Circuit Breaker 2P: `_TZ3000_1hwjutgo`, `_TZ3000_lnggrqqi`.
- Added TS0601 `_TZE200_hkdl5fmv` circuit breaker with energy, power, voltage, and amperage
  reporting.

### Changed

- Polling and `energyMode` are now set automatically depending on the device type.

### Fixed

- Fixed an automatic reporting configuration thresholds bug.

## [1.7.3] - 2023-03-28

### Added

- Added frequency polling.

### Fixed

- Fixed the Third Reality amperage divisor.
- Improved logging for disabled attributes.
- `hourlyEnergy` is no longer sent when energy reporting is disabled or the device health is
  offline.

### Changed

- Completely removed the presence capability.

### Developer notes

- Added a dummy `ping()`.

## [1.7.2] - 2023-02-16

### Added

- Added Third Reality 3RSP02028BZ metering plug support.
- Added `powerOnState` handling for non-Tuya plugs.

### Fixed

- IntelliJ lint pass plus bug fixes.

## [1.7.1] - 2023-02-02

### Added

- Added the `Health Check` capability.

## [1.7.0] - 2023-01-29

### Added

- Added the `healthStatus` attribute.

## [1.6.6] - 2023-01-20

### Added

- Added SONOFF Z111PL0H-1JX to the `isHEProblematic()` list.
- Added `_TZ3000_7dndcnnb` Overload Protection Switch 25A.

### Changed

- Commented out the Zigbee-3.0-incompatible `_TZ3000_r6buo8ba` and `_TZ3000_okaz9tjs` fingerprints.

## [1.6.5] - 2022-12-19

### Added

- Added `_TZE204_cjbofhxw` Smart Meter with Current Transformer.
- Added HIKING TOMZN DDS238-2 `_TZE200_bkkmqmyo`.
- Added MatSee `_TZE200_eaac7dkw`.

### Fixed

- Fixed a bug in the HTML representation of the power attribute.

## [1.6.4] - 2022-11-26

### Added

- Added Frient Energy Monitor (ZHEMI101) support.
- Added `pulseConfiguration` and `energyMeterMode`.
- Added `rejoinCounter`.

### Fixed

- Fixed `isRefreshRequest`.
- Fixed a null Zigbee commands bug.

### Changed

- Removed the fixed `destEndpoint`.

### Developer notes

- Additional `_TZ3000_okaz9tjs` tests.

## [1.6.3] - 2022-11-08

### Added

- Added OSRAM 'Plug 01' support.
- Added 'Develco Products A/S' as a Frient manufacturer.
- Added last-hour energy to the HTML attribute.
- Added `_TZ3000_zloso4jk`.
- Added SiHAS products.
- Added frequency and power factor reporting.
- Added `fixOtherTuyaOddities()` for `_TZ3000_okaz9tjs`.
- Added `extendedTuyaMagic`.

### Fixed

- Fixed power events when switching on/off.
- Fixed the 'not present' status bug when polling is disabled.
- Set the maximum power cap to 13 kW.

### Changed

- Removed `lastPresenceState`.

## [1.6.2] - 2022-09-28

### Added

- Added non-Tuya plug fingerprints.
- Added processing for power Instantaneous Demand.

### Fixed

- Corrected SmartThings outlet power and voltage.
- Corrected frient A/S SPLZB-131 voltage.
- Added automatic negative-energy correction.
- Ignored false zero automatic reports for power, amperage, and voltage.

### Changed

- Removed hardcoded EPVA dividers.
- Removed `lastAmperage` and `lastVoltage`.

### Developer notes

- Added a warning for `_TZ3000_okaz9tjs`.

## [1.6.1] - 2022-09-19

### Added

- Added the `html` attribute.
- Added `energyDuration` and `hourlyEnergy`.

### Fixed

- Fixed an `autoPoll` bug.

### Changed

- `energy` and `energyCosts` are now reset on the Initialize button.
- `energyCost` and `hourlyEnergy` types changed to `NUMBER`.

## [1.6.0] - 2022-09-12

### Added

- Added individual thresholds for power, amperage, and voltage.
- Added the `autoReportingEnabled` switch (default off).
- Added the `resetEnergy` command.
- Added the `energyPrice` preference and the `setEnergyPrice` command.
- Added `energyCost` calculation and event.

### Fixed

- Fixed automatic reporting configuration bugs.

### Changed

- Removed the `Health Check` and `Polling` capabilities (ping and poll buttons).
- Disabled attribute states are now deleted.

## [1.5.2] - 2022-09-09

### Added

- Added `_TZ3000_cehuw1lw` and `_TZ3000_typdpbpg`.

## [1.5.1] - 2022-06-12

### Fixed

- Fixed the child lock.

## [1.5.0] - 2022-06-05

### Added

- Added over-current alarm (`0x8003`) handling.
- Added a 'Freeze' LED mode that keeps the backlight at its current state.
- Added parsing for 'other Tuya oddities'.

### Fixed

- Fixed a bug where all settings were reset back to defaults on hub reboot.

## [1.4.6] - 2022-06-04

### Added

- Added `_TZ3000_gjnozsaz`.
- Added on/off switches for power, amperage, voltage, and energy reporting (logs and events).
- Added the device display name to all log lines.

## [1.4.5] - 2022-05-24

### Added

- Added `_TZ3000_5f43h46b` XUELILI 16A UK, `_TZ3000_r6buo8ba`, `_TZ3000_ksw8qtmt` NOUS A1Z,
  `_TZ3000_1h2x4akh` Ajax/Zignito, `_TZ3000_ky0fq4ho` DIN Relay.
- Added `childLock`, `ledMode`, and `powerOnState` configuration commands.

### Fixed

- Possible GreenPower cluster `0xF2` fix.

### Changed

- `importURL` now points to the development branch.

## [1.4.4] - 2022-05-08

### Added

- Added new fingerprints.
- Added an explicit `[overwrite: true]` option for `runIn` timers.

### Fixed

- Fixed a settings-reset bug.

## [1.4.3] - 2022-02-15

### Added

- Added 'Tuya RC-RCBO Circuit Breaker' support.

## [1.4.2] - 2022-02-20

### Fixed

- Fixed a missing `Switch` capability bug.

## [1.4.1] - 2022-01-27

### Added

- Added XH-002P Outlet TS011F fingerprint (no power monitoring).

## [1.4.0] - 2022-01-23

### Added

- Added a driver version check.

### Changed

- Debug/trace logging cleanup.
- Switch and energy events are now excluded from polling.
- Default debug logging is now off; optimizations are now on by default.
- Initialized the switch and energy automatic reporting mode.

### Fixed

- Fixed a switch digital/physical bug.

## [1.3.2] - 2022-01-12

### Fixed

- Fixed a Tuya cluster command bug (HIKING TOMZN TS0601).

## [1.3.1] - 2022-01-02

### Fixed

- Minor bug fixes.

## [1.3.0] - 2022-01-01

### Added

- Added 'HIKING TOMZN DDS238-2 TS0601' support.

## [1.2.1] - 2021-12-29

### Added

- Added the `alwaysOn` option.

## [1.2.0] - 2021-12-29

### Changed

- Major refactoring and optimizations.

## [1.1.2] - 2021-12-24

### Added

- Added the Tuya / Neo NAS-WR01 fingerprint.

### Fixed

- Corrected a fingerprint `inClusters` value.

## [1.1.1] - 2021-11-25

### Added

- Added the Tuya Outlet TS011F fingerprint.

## [1.1.0] - 2021-11-12

### Added

- Added the `PresenceSensor` capability.

### Changed

- Automatic polling can now be switched off.

## [1.0.1] - 2021-11-10

### Added

- Added the `pollingInterval` preference.

### Fixed

- Fixed the `amperage` attribute name.

## [1.0.0] - 2021-11-09

### Added

- Initial release: reads power, energy, voltage, and amperage once every 60 seconds.
