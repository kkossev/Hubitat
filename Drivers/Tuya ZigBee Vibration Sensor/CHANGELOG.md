# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project follows Semantic Versioning where applicable.

## [Unreleased]

Changes for the next release will be documented here.

## [1.4.7] - 2026-08-02

### Added

- Exposed contact state for the `_TZE200_kzm5w4iz` TS0601 door/vibration sensor via the
  existing parent `contact` attribute (DP 0x01), reusing the change-only event pattern already
  used for the Third Reality garage-door contact.

### Fixed

- Corrected ping RTT calculation to preserve epoch timestamps before subtraction.
- Cleared stale ping state after command timeouts so late Basic reports are treated as check-ins.
- Hardened diagnostics for truncated Tuya EF00 frames and undefined debug/device-info values.
- Restored hub model reporting from the successful `getHubVersion()` path.
- Excluded the EF00-only `_TZE200_kzm5w4iz` device from IAS sensitivity handling.
- Replaced undefined battery voltage limits with explicit 2.5–3.0 V constants.
- Removed unused internal health-check state initialization without changing health-status behavior.
- Labeled the timer-driven vibration inactivity reset as a `digital` event instead of
  `physical`, since a hub-timeout-based reset is not a physical device action.

### Developer notes

- Standardized driver-derived RTT and health events on `type: 'digital'` and routed vibration
  diagnostics through the gated debug logger.
- Completed focused internal naming, comment, and diagnostic cleanup without changing public
  commands, attributes, or device-profile routing.
- Reviewed and retained randomized cron-based health-check scheduling to spread checks across
  devices.

## [1.4.6] - 2026-07-31

### Added

- Enabled the `TamperAlert` capability for IAS devices.
- Added IAS battery-low status reporting.
- Added IAS sensitivity diagnostic readback.

### Changed

- Expanded IAS sensitivity from enum values `0..6` to numeric values `0..50`.
- IAS sensitivity readback now synchronizes the preference as a numeric value.
- The existing Third Reality garage-door contact and battery handling remains
  unchanged.

### Fixed

- Re-armed the vibration inactivity timer when repeated active IAS
  notifications are received.
- Prevented duplicate acceleration and shock events during repeated active IAS
  notifications.
- Added change-only tamper event generation.
- Added change-only generic battery-status event generation.
- Preserved the existing garage-door battery event semantics while renaming
  the helper to `sendBatteryStatusEvent()`.

### Developer notes

- Non-garage IAS devices report `tamper: detected` and `tamper: clear` from
  the IAS tamper status bit.
- Non-garage IAS devices report `batteryStatus: replace` and
  `batteryStatus: normal` from the IAS battery-low status bit.
- IAS attribute `0x0500:0x0012` is read for diagnostics and does not modify
  user preferences.
- The public `sensitivity` preference and attribute name remain unchanged.
- Existing stored values such as `3` remain compatible.
- ZG-102ZM numeric sensitivity remains limited to `1..50`.
- ZG-103Z `tuyaSensitivity` remains an enum.
- Repeated active IAS messages use `overwrite: true` when scheduling the
  vibration reset.

## [1.4.5] - 2026-07-18

### Added

- Added custom contact attribute support for the Third Reality garage-door
  tilt sensor.

### Developer notes

- Target device: Third Reality model `3RDTS01056Z`.

## [1.4.4] - 2026-07-18

### Added

- Added HOBEIAN ZG-102ZM vibration sensor support.

### Fixed

- Included additional bug fixes.

### Developer notes

- Added fingerprints `_TZE200_jfw0a4aa` and `_TZE200_wzk0x7fq`.

## [1.4.3] - 2026-03-22

### Fixed

- Fixed handling for the `_TZ32101000000_5oy7cysk` variant.

### Developer notes

- The fix targeted the long-ID ZG-103Z family variant.

## [1.4.2] - 2026-02-04

### Added

- Added an alternative TS0210 variant.
- Added additional ZG-103Z variants.
- Added the Tuya sensitivity setting for supported models.

### Developer notes

- Added fingerprints `_TZ32101000000_5oy7cysk`, `_TZE200_hggxgsjj`,
  `_TZE200_yjryxpot`, and `_TZE200_afycb3cg`.

## [1.4.1] - 2025-08-30

### Added

- Added TS0210 support for the `_TZ3210100000_5oy7cysk` variant.

### Developer notes

- This variant was added for development and testing.

## [1.4.0] - 2025-03-01

### Added

- Added the `ShockSensor` capability.
- Added the `shockSensor` preference.

### Changed

- The `shockSensor` preference is enabled by default.

## [1.3.1] - 2025-02-19

### Added

- Added TS0210 support for additional Tuya fingerprints.

### Developer notes

- Added fingerprints `_TZ3000_lqpt3mvr`, `_TZ3000_lzdjjfss`, and
  `_TYZB01_geigpsy4`.

## [1.3.0] - 2025-01-28

### Added

- Added the Tuya cluster parser.
- Added TS0601 contact and vibration sensor support.
- Added TS0601 ZG-103Z tilt and XYZ axis sensor support.
- Added the `queryAllTuyaDP()` function.

### Fixed

- Fixed missing `overwrite: true` scheduling behavior.

### Developer notes

- Added EF00 datapoint routing for `_TZE200_kzm5w4iz` and
  `_TZE200_iba1ckek`.

## [1.2.2] - 2024-06-03

### Changed

- Hid the sensitivity preference for non-Tuya models.
- Hid the Three Axis preference for Tuya models.

## [1.2.1] - 2024-05-22

### Added

- Added the `lastBattery` attribute.
- Added the `setAccelarationInactive` command.

### Changed

- Scheduled jobs are now deleted when Save Preferences is used.

## [1.2.0] - 2024-05-20

### Added

- Added the `healthStatus` attribute.
- Added the `ping` command.
- Added Third Reality vibration sensor support.
- Added the Three Axis capability and preference.
- Added Samsung multisensor support.
- Added the logs-off scheduler.
- Added the sensitivity attribute.

### Fixed

- Included bug fixes for existing device and lifecycle handling.

## [1.1.0] - 2023-03-07

### Added

- Added the driver import URL.
- Added support for the `_TYZB01_cc3jzhlj` TS0210 variant.

### Changed

- IAS enrollment responses are sent with a one-second delay.
- IAS is initialized during Configure.

## [1.0.8] - 2022-11-08

### Added

- Added TS0210 support for `_TZ3000_bmfw9ykl`.

## [1.0.7] - 2022-05-12

### Added

- Added TS0210 support for the `_TYZB01_pbgpvhgx` Smart Vibration Sensor
  HS1VS.

## [1.0.6] - 2022-03-03

### Added

- Added vibration sensitivity support.

## [1.0.5] - 2022-03-03

### Added

- Added battery reporting.

## [1.0.4] - 2022-03-02

### Fixed

- Fixed the misspelled acceleration event handling.

## [1.0.3] - 2022-02-28

### Added

- Initial release of the Tuya ZigBee Vibration Sensor driver.

[Unreleased]: https://github.com/kkossev/Hubitat/compare/v1.4.6...HEAD
[1.4.6]: https://github.com/kkossev/Hubitat/releases/tag/v1.4.6
[1.4.5]: https://github.com/kkossev/Hubitat/releases/tag/v1.4.5
[1.4.4]: https://github.com/kkossev/Hubitat/releases/tag/v1.4.4
[1.4.3]: https://github.com/kkossev/Hubitat/releases/tag/v1.4.3
[1.4.2]: https://github.com/kkossev/Hubitat/releases/tag/v1.4.2
[1.4.1]: https://github.com/kkossev/Hubitat/releases/tag/v1.4.1
[1.4.0]: https://github.com/kkossev/Hubitat/releases/tag/v1.4.0
[1.3.1]: https://github.com/kkossev/Hubitat/releases/tag/v1.3.1
[1.3.0]: https://github.com/kkossev/Hubitat/releases/tag/v1.3.0
[1.2.2]: https://github.com/kkossev/Hubitat/releases/tag/v1.2.2
[1.2.1]: https://github.com/kkossev/Hubitat/releases/tag/v1.2.1
[1.2.0]: https://github.com/kkossev/Hubitat/releases/tag/v1.2.0
[1.1.0]: https://github.com/kkossev/Hubitat/releases/tag/v1.1.0
[1.0.8]: https://github.com/kkossev/Hubitat/releases/tag/v1.0.8
[1.0.7]: https://github.com/kkossev/Hubitat/releases/tag/v1.0.7
[1.0.6]: https://github.com/kkossev/Hubitat/releases/tag/v1.0.6
[1.0.5]: https://github.com/kkossev/Hubitat/releases/tag/v1.0.5
[1.0.4]: https://github.com/kkossev/Hubitat/releases/tag/v1.0.4
[1.0.3]: https://github.com/kkossev/Hubitat/releases/tag/v1.0.3
