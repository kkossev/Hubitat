# Changelog

All notable changes to the **Aqara P100 Multi-State Sensor** driver for Hubitat are documented in
this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). This driver is
pre-1.0 and versions are not semantic yet — the `0.1.x` series is the initial bring-up of a new
device.

> **0.1.6 is the current development bucket, not a cut release.** New work goes into the section
> below until kkossev explicitly asks for a version bump; only then does a new heading get added
> above it. This file therefore never carries an `[Unreleased]` heading.

## [0.1.6] - 2026-05-06

### Changed

- Battery level is now derived from the reported battery **voltage** only, using a linear 2.5–3.0 V
  curve, instead of trusting the percentage the device reports for itself.

### Fixed

- Several bug fixes across parsing and configuration. Thanks to `@user1974` for the reports and
  testing.

### Developer notes

- `decodeAqaraStruct()` TLV tag `0x18` (battery percentage) is now commented out in favour of the
  voltage-based calculation. Note that FCC0 attribute `0x0018` still emits the device-reported
  percentage directly — the two paths disagree, tracked as `BUGS.md` **B4**.

## [0.1.5] - 2026-05-03

### Changed

- A forced Time cluster (`0x000A`) reply is now sent shortly after **every** FCC0 attribute `0x00DF`
  diagnostic heartbeat, keeping the device's internal clock in sync without waiting for it to ask.

### Developer notes

- Implemented as `runIn(1, "sendTimeSync", [overwrite: true])` in the `00DF` case. A second,
  longer-delay reply (`runIn(342, …)`) was written and then deliberately commented out — the P100 is
  asleep at that point.

## [0.1.4] - 2026-05-01

### Added

- Forced **Time cluster (`0x000A`) response**. Hubitat's Zigbee coordinator does not answer
  device-originated Time reads, so the driver now hand-builds and sends the ZCL Read Attributes
  Response itself (UTC time, timezone offset, DST offset).
- ZDO handlers for `End_Device_Timeout_Req` (`0x0036`), `Node_Desc_req` (`0x0002`) and
  `Mgmt_Rtg_rsp` (`0x8032`) — all log-only, since the Zigbee stack answers these itself.
- FCC0 attribute `0x00FF` handler for the device registration-response report, so the Lumi
  handshake can be confirmed in the log.

### Changed

- Extended `aqaraBlackMagic()` to follow the Aqara E1 hub's initialization sequence more closely:
  fake coordinator node-descriptor response, read device mode first, then the 16-byte FCC0/`0x00FF`
  registration write, then the basic and settings reads.

### Developer notes

- The registration write uses `he raw` rather than `zigbee.writeAttribute` / `he wattr`, because
  `he wattr` does not prepend the ZCL octet-string length byte (`0x10` = 16 bytes) for type `0x41`.
  The payload was captured verbatim from an Aqara E1 hub Wireshark trace.
- The `zdo bind` lines were left commented out on purpose: the registration write makes the device
  unicast its reports to the hub directly, and the E1 hub sends no binds at all.
- Working theory behind both this and 0.1.5: without the registration write and the time replies,
  the device's internal 24-hour watchdog makes it leave the Zigbee network. **Suspected, not
  confirmed.**

## [0.1.3] - 2026-04-29

### Added

- `preventDeviceReset()` — the driver now detects the Aqara factory-reset probe on genBasic
  attribute `0xFFF0` (payload starting `AA10054187`) and writes back the abort response
  `AA1005414701011001`, so holding the button no longer resets the device out of the network.
  Mirrors Zigbee2MQTT's `lumiPreventReset`.

## [0.1.2] - 2026-04-28

### Fixed

- Bug fixes.

## [0.1.1] - 2026-04-19

### Fixed

- Corrected the device fingerprint (model `lumi.vibration.agl002`, manufacturer `Aqara`).
- Assorted bug fixes. Thanks to `@rad1` for testing and feedback.

## [0.1.0] - 2026-04-18

### Added

- Initial version: a dedicated driver for the **Aqara P100 Multi-State Sensor (DWZTCGQ11LM)**, built
  on the *Aqara P1 Motion Sensor* driver as a template.
- Two operating modes selected by FCC0 attribute `0x0116`: **object** mode (movement, vibration,
  orientation, triple-tap and fall events) and **door_window** mode (contact open/closed).
- Attributes `contact`, `acceleration`, `lastAction`, `orientation`, `devicePosture`, `deviceMode`,
  `battery`, `batteryVoltage`, `powerSource`, `healthStatus`, `rtt`, `_status_`.
- Preferences for device mode, detection sensitivity (1–10), report interval, door/window type, and
  per-event-type detection toggles (movement, vibration, orientation, fall, triple-tap).
- Commands `configure`, `ping`, `refresh`; health-check monitoring with online/offline detection.

### Developer notes

- Device semantics were derived from `@absent42`'s Zigbee2MQTT external converter
  (<https://github.com/absent42/Aqara-P100-Sensor>).
- Preference writes go through the `state.params` change-detection layer: `updated()` sends an FCC0
  write only when the value actually changed, and the value is recorded as confirmed only when the
  device echoes it back in `parse()`. This keeps a sleepy device from being flooded with writes.
- Legacy monolithic architecture — no `#include` libraries, no `deviceProfilesV3`. See `AGENTS.md`.
