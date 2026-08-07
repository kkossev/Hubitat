# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/2.0.0/),
and this project follows Semantic Versioning where applicable.

## [3.5.1] - Unreleased (dev branch)

### Changed

- Aligned with commonLib 4.1.0.

### Fixed

- `push(buttonNumber)` no longer throws `MissingMethodException`: `customPush()` now accepts
  an optional button-number argument instead of only the no-argument signature.
- `customUpdated()` no longer appends the `void` return of `updateAllPreferences()` to the
  command list, which used to log a spurious "no commands to send! it=null" warning on every
  Save Preferences.
- `logEnable` now defaults consistently to `false` on Initialize / Load All Defaults, via a
  newly-defined `DEFAULT_DEBUG_LOGGING` field (aligned with the preference input's default).
- `customConfigureDevice()`'s `delta` variable is now explicitly declared, removing an
  undeclared-binding assignment.
- `parseSimpleDescriptorResponse()`'s input-cluster loop no longer iterates backwards on a
  0-input-cluster response (debug-command-only `activeEndpoints` path).
- `toggle()` now checks structurally for a Tuya `switch` DataPoint instead of string-matching
  the model against `"TS0601"`, so DP-controlled EF00 devices (including the generic
  `model:'Tuya'` test fingerprint) are toggled correctly instead of receiving a no-op ZCL Toggle.
- `testDetachRelayMode()` no longer declares an unused `cmds` list.

## [3.5.0] - 2025-09-15

### Changed

- Aligned with commonLib 4.0.0.

## [3.4.0] - 2025-04-08

### Fixed

- Urgent fix for `java.lang.CloneNotSupportedException` under HE platform update 2.4.1.155.

## [3.3.2] - 2025-03-29

### Added

- Added the `updateFirmware()` command.
- Added the `toggle()` command.
- Added the `delayedPowerOnState` and `delayedPowerOnTime` preferences.

### Fixed

- Fixed the ZCL Default Response sent during ZBMINIR2 Detach Relay Mode.

## [3.3.1] - 2025-03-13

### Added

- Added the `activeEndpoints()` command (test mode only).
- Added the `PushableButton` capability for ZBMINIR2.

### Changed

- ZBMINIR2 Detach Relay Mode now sends a ZCL Default Response.

## [3.3.0] - 2025-03-10

### Added

- Added health check via device ping.
- Added SONOFF ZBMINIR2 support.
- Added SONOFF ZBMINI-L support under a new `SWITCH_SONOFF_GENERIC` profile.

## [3.2.2] - 2024-06-29

### Added

- Added on/off control for the `SWITCH_GENERIC_EF00_TUYA` `switch` DataPoint.

## [3.2.1] - 2024-06-04

### Changed

- Aligned with commonLib 3.2.1.

### Fixed

- ZBMicro now performs a `refresh()` after saving preferences.

## [3.1.1] - 2024-05-15

### Added

- Added SONOFF ZBMicro support.

### Changed

- Aligned with commonLib 3.1.1.
- Groovy linting cleanup.

## [3.0.7] - 2024-04-18

### Changed

- Aligned with commonLib 3.0.7 and groupsLib.

## [3.0.3] - 2024-02-24

### Changed

- Aligned with commonLib 3.0.3.

## [3.0.2] - 2023-12-12

### Added

- Added SONOFF ZBMINIL2 support.

## [3.0.1] - 2023-11-25

### Added

- Added LEDVANCE Plug 03 support.
- Added TS0101 `_TZ3000_pnzfdr9y` SilverCrest Outdoor Plug (Lidl HG06619) support.
- Added OnOff cluster (0x0006) reporting configuration for all devices.

## [3.0.0] - 2023-11-24

### Changed

- Migrated to commonLib.

### Added

- Added the AlwaysOn option.
- Added an option to ignore duplicated on/off events.

## [2.1.3] - 2023-08-12

### Added

- Added ping OK/Fail/Min/Max rolling average counters.
- Added `clearStatistics()`.
- Added `updateTuyaVersion()` and `updateAqaraVersion()`.
- Added HE hub model and platform version reporting.

### Changed

- Improved `ping()`.

## [2.1.2] - 2023-07-23

### Added

- Added the Switch library.

## [2.0.4] - 2023-06-29

### Added

- Initial release of the Tuya Zigbee Switch driver.

[Unreleased]: https://github.com/kkossev/Hubitat/compare/v3.5.0...HEAD
[3.5.1]: https://github.com/kkossev/Hubitat/compare/v3.5.0...HEAD
[3.5.0]: https://github.com/kkossev/Hubitat/releases/tag/v3.5.0
[3.4.0]: https://github.com/kkossev/Hubitat/releases/tag/v3.4.0
[3.3.2]: https://github.com/kkossev/Hubitat/releases/tag/v3.3.2
[3.3.1]: https://github.com/kkossev/Hubitat/releases/tag/v3.3.1
[3.3.0]: https://github.com/kkossev/Hubitat/releases/tag/v3.3.0
[3.2.2]: https://github.com/kkossev/Hubitat/releases/tag/v3.2.2
[3.2.1]: https://github.com/kkossev/Hubitat/releases/tag/v3.2.1
[3.1.1]: https://github.com/kkossev/Hubitat/releases/tag/v3.1.1
[3.0.7]: https://github.com/kkossev/Hubitat/releases/tag/v3.0.7
[3.0.3]: https://github.com/kkossev/Hubitat/releases/tag/v3.0.3
[3.0.2]: https://github.com/kkossev/Hubitat/releases/tag/v3.0.2
[3.0.1]: https://github.com/kkossev/Hubitat/releases/tag/v3.0.1
[3.0.0]: https://github.com/kkossev/Hubitat/releases/tag/v3.0.0
[2.1.3]: https://github.com/kkossev/Hubitat/releases/tag/v2.1.3
[2.1.2]: https://github.com/kkossev/Hubitat/releases/tag/v2.1.2
[2.0.4]: https://github.com/kkossev/Hubitat/releases/tag/v2.0.4
