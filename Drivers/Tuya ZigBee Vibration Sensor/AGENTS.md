# Tuya ZigBee Vibration Sensor — Local Agent Guide

This folder contains a legacy, monolithic Hubitat Elevation Zigbee driver written in Groovy for
Tuya vibration sensors, Third Reality vibration/tilt sensors, and Samsung/Samjin multisensor
variants.

- Author: Krassimir Kossev
- Community thread: https://community.hubitat.com/t/tuya-vibration-sensor/75269
- Release thread: https://community.hubitat.com/t/release-tuya-zigbee-vibration-sensor/138208

The repository-root `AGENTS.md` governs repository-wide policy. This file records only the facts
and exceptions specific to this driver.

## Local sources of truth

- `Tuya ZigBee Vibration Sensor.groovy` is the production driver and public API.
- `TODO.md` is this folder's consolidated device-support backlog, bug work-list, and TS0210
  research/verification record. Use its canonical IDs and status labels; do not create separate
  bug or TS0210 work-list files for this driver.

## Driver shape and helpers

This is a legacy monolithic driver:

- One self-contained Groovy file; no `#include kkossev.*` libraries.
- No `deviceProfilesV3` map, V3 `custom*` hooks, or `deviceProfileLib`.
- Do not convert it to V3, shared libraries, or a broad architecture during a focused fix.
- Use the existing helper predicates rather than scattering model/manufacturer comparisons.

Important helper predicates include:

- `isTuya()`
- `isTuyaTiltXyzAxisSensor()`
- `isTuyaVibrationDoorSensor()`
- `isTuyaZG102ZM()`
- `isTuyaVibrationSensorTZ32101000000()`
- `supportsIasSensitivity()`
- `supportsTuyaSensitivity()`
- `supportsNumericSensitivity()`

Reuse the existing event, Tuya, and transport paths when extending behavior:

- vibration: `sendVibrationEvent()`, AccelerationSensor, optional ShockSensor;
- battery percentage: `sendBatteryPercentageEvent()`, `lastBattery`;
- sensitivity: existing `sensitivity` or `tuyaSensitivity` paths;
- EF00 refresh: `queryAllTuyaDP()`;
- writes: `tuyaSetValueDp()`, `tuyaSetEnumDp()`;
- transport: `sendZigbeeCommands()`.

## Supported device families

| Family | Identity examples | Main path |
|---|---|---|
| Tuya TS0210 IAS vibration | `_TYZB01_*`, `_TZ3000_*` | IAS Zone cluster 0x0500 |
| Third Reality vibration | model `3RVS01031Z`, manufacturer `Third Reality, Inc` | IAS plus private cluster 0xFFF1 |
| Third Reality garage tilt | model `3RDTS01056Z`, manufacturer `Third Reality, Inc` | IAS contact/battery path plus private cluster 0xFF01 |
| Samsung/Samjin multisensor | model `multi`, manufacturer `Samjin` | IAS and private cluster 0xFC02 |
| Tuya TS0601 vibration/contact | `_TZE200_kzm5w4iz` | EF00 DP 0x0A vibration, DP 0x03 battery, DP 0x01 contact log-only |
| Tuya ZG-102ZM | `_TZE200_jfw0a4aa`, `_TZE200_wzk0x7fq` | EF00 DP 0x01 vibration, DP 0x04 battery, DP 0x06 sensitivity |
| Tuya ZG-103Z / tilt XYZ | `_TZE200_iba1ckek`, `_TZE200_hggxgsjj`, `_TZE200_yjryxpot`, `_TZE200_afycb3cg`, and the two `_TZ321...` variants | EF00 vibration, tilt, acceleration, and Tuya sensitivity |

Out of scope unless explicitly requested:

- HOBEIAN ZG-228Z alarm/siren.
- Excellux ZG-102MV contact/vibration multisensor.
- Excellux ZG-104PLV PIR multisensor.
- Senoro.Win v2 window sensor.
- Upstream haptic-feedback false positives.

### Adding support for a new device

For every real pairing of a new model/manufacturer, capture before writing any code:

- exact case-sensitive model and manufacturer;
- complete Hubitat fingerprint, profile, endpoint, device, input and output clusters;
- application, stack, and hardware versions when available;
- raw IAS and EF00 descriptions;
- behavior before and after Configure, Refresh, and Save Preferences;
- sensitivity readback/write while the sleepy device is awake;
- EF00 DP number, datatype, length, raw bytes, and decoded value.

When exact pairing data isn't available yet, a fingerprint may reuse a donor device's known-good
behavior as a starting point (see `TODO.md` open device-support items for current donor
mappings); replace the donor fields and remove any `// not tested!` marker only once the
complete real fingerprint is confirmed.

Detection rules:

- `isTuya()` returns true when the model starts with `T` and manufacturer starts with `_T`; it
  also returns true when `device` is null during pairing context.
- `supportsIasSensitivity()` selects Tuya IAS non-tilt devices, excluding the EF00-only
  `_TZE200_kzm5w4iz` device.
- `supportsTuyaSensitivity()` selects the ZG-103Z tilt XYZ family.
- `supportsNumericSensitivity()` selects ZG-102ZM.
- New profiles must match exact model/manufacturer pairs; do not broaden a manufacturer-only match
  without evidence.

### Critical DP-swap rule

The long-ID manufacturer `_TZ32101000000_5oy7cysk` intentionally differs from the rest of the
ZG-103Z family:

- DP 0x68 is vibration.
- DP 0x69 is sensitivity.

Other tilt XYZ devices use DP 0x68 for sensitivity and DP 0x69 for battery. Preserve these
branches exactly.

## Parse pipeline

`parse(String description)` is the single entry point:

1. `checkDriverVersion(state)`, RX statistics, command-timeout cancellation, and health-online
   handling.
2. `zigbee.getEvent(description)` is attempted first. Recognized standard events return early.
3. IAS enrollment requests schedule `sendEnrollResponse()`.
4. Zone-status messages go through `zigbee.parseZoneStatus()` and `parseIasMessage()`.
5. Catchall/read-attribute descriptions go through `zigbee.parseDescriptionAsMap()` and
   cluster-specific routing.

Important routes:

| Cluster / data | Handler | Behavior |
|---|---|---|
| IAS 0x0500 zone status or attr 0x0002 | `parseIasMessage()` | Vibration; non-garage IAS tamper and battery status; garage contact and battery status |
| IAS 0x0500 attr 0x0011 | log only | IAS Zone ID |
| IAS 0x0500 attr 0x0012 | log only | Number of sensitivity levels supported; null/status failures are ignored |
| IAS 0x0500 attr 0x0013 | sensitivity event and setting sync | Numeric IAS sensitivity 0..50 |
| Power Configuration 0x0001 attr 0x0020 | `parseBatteryVoltage()` | Voltage-to-percent conversion |
| Basic 0x0000 attr 0x0001 | `handlePingResponse()` | Ping RTT or unsolicited Tuya check-in |
| Third Reality 0xFFF1 | `handleThreeAxisTR()` | Three-axis values |
| Samsung 0xFC02 | `handleThreeAxisSamsung()` | Vibration and three-axis values |
| Tuya EF00 commands 0x01/0x02/0x06 | DP loop and `processTuyaDP()` | Tuya datapoint processing |

The EF00 parser walks multi-DP records using the DP type, advertised length, and big-endian value.
Its malformed-frame guards are intentional and must not be weakened.

## Current Tuya DP map

| DP | Current meaning |
|---|---|
| 0x01 | Contact for `_TZE200_kzm5w4iz`; vibration for applicable families; ignored for the long-ID DP-swap variant |
| 0x03 | Battery percentage for `_TZE200_kzm5w4iz` |
| 0x04 | Battery percentage for ZG-102ZM and the long-ID DP-swap variant |
| 0x06 | Numeric sensitivity for ZG-102ZM |
| 0x07 | Tilt detected/clear |
| 0x0A | Vibration for `_TZE200_kzm5w4iz` |
| 0x65/0x66/0x67 | Tuya X/Y/Z acceleration; Z triggers the three-axis event |
| 0x68 | Tuya sensitivity for most tilt XYZ devices; vibration for the long-ID DP-swap variant |
| 0x69 | Battery for most tilt XYZ devices; sensitivity for the long-ID DP-swap variant |

Unused or unsupported DPs should be debug-logged and ignored unless a new public capability is
explicitly requested.

## Driver-specific behavior

### Events

- Vibration flows through `handleVibration()` and `getVibrationResult()`, emitting
  `acceleration: active/inactive`.
- Most devices send only active notifications; inactivity is reset through
  `resetToVibrationInactive()`.
- Repeated active IAS notifications re-arm the reset timer with `overwrite: true`.
- Shock simulation emits `shock: detected/clear` only when `shockSensor` is enabled.
- IAS non-garage devices emit `tamper: detected/clear` and `batteryStatus: replace/normal` only
  when those values change. The first observed value initializes the state; repeated identical
  values only produce debug logging.
- The Third Reality garage path preserves contact and battery-status behavior and does not use the
  generic tamper branch.
- Tilt emits the custom `tilt: detected/clear` event and has no driver-side auto-reset.
- Three-axis emits JSON containing `x`, `y`, `z`, `psi`, `phi`, and `theta`, controlled by the
  `threeAxis` preference.
- Battery events use Zigbee event, voltage-conversion, and Tuya percentage-DP paths; all update
  `lastBattery`.
- Ping uses Basic attr 0x01 and emits `rtt`.

### Commands and preferences

Commands:

- `configure` calls `configureReporting()`.
- `refresh` reads battery and family-specific attributes and queries Tuya DPs.
- `ping` checks Basic attr 0x01 and records RTT.
- `setAccelarationInactive` is a misspelled public legacy command and must be preserved.

Preferences:

- `txtEnable`, `logEnable`.
- IAS `sensitivity` numeric 0..50 for `supportsIasSensitivity()`.
- `tuyaSensitivity` enum for ZG-103Z tilt XYZ.
- ZG-102ZM numeric `sensitivity` range 1..50.
- `vibrationReset` and `threeAxis`.
- Advanced options: `shockSensor`, `healthCheckMethod`, `healthCheckInterval`,
  `batteryReportingHours`.
- Third Reality garage options include open delay and calibration.

Hidden preferences can still receive defaults from `initializeVars()`; check model guards before
changing preference behavior.

### Lifecycle and health

- `installed()` initializes state, configures health checks, updates Tuya version, and refreshes.
- `configure()` calls `configureReporting()`.
- `updated()` clears schedules, re-arms an active vibration reset, handles logs-off and health
  scheduling, manages shock/three-axis state, then configures reporting.
- This driver exposes health method 0 as disabled and method 1 as activity check. Legacy method 2
  is migrated to activity checking; there is no active periodic ping-polling implementation.

## Implementation caveats

- Undefined bare identifiers such as `_DEBUG`, `UNKNOWN`, `DEFAULT_DEBUG_LOGGING`, `voltsmin`,
  `voltsmax`, and the short-frame `fncmd_len` log variable resolve harmlessly in the current
  Hubitat sandbox but produce null or misleading output. Treat them as cleanup items, not assumed
  runtime crashes.
- Large IAS reference maps and state fields may be documentation/dead code; do not delete them
  casually.
- `JsonOutput` is imported mid-file; this is legal Groovy. Preserve it unless performing a
  dedicated cleanup.
- `checkDriverVersion()` is `@CompileStatic`; the rest of the driver is dynamic. Do not add static
  compilation broadly.
- Battery event state-change behavior is deliberate and should not be changed without user
  approval.

## Driver-specific risk boundaries

- Never casually change the long-ID ZG-103Z DP routing.
- Re-read the complete `processTuyaDP()` switch after any DP change; several branches differ only
  by model guard.
- Keep all bug, device-support, TS0210 status, and verification records in `TODO.md`.
- Do not remove DONE, REJECTED, or otherwise fixed/implemented items from `TODO.md` on your own
  initiative. Clean up or delete resolved TODO items only when the developer explicitly asks for
  it in that session.

## Regression checklist

Run this for every bug fix or device-support change:

- TS0210 IAS vibration, enrollment, sensitivity, tamper, battery, reset, Configure, and Refresh.
- `_TZE200_kzm5w4iz` contact event (parent `contact` attribute), DP 3 battery, DP 10 vibration.
- ZG-102ZM DP 1/4/6 behavior and sensitivity 1..50.
- ZG-103Z tilt, axes, Tuya sensitivity, and long-ID DP swap.
- Third Reality IAS/private-cluster vibration and axes.
- Samsung/Samjin vibration and axes.
- ShockSensor on/off.
- Save Preferences while acceleration is active.
- Battery and `lastBattery`.
- Health status and ping.
- Logging behavior and malformed-frame handling.

## Release and documentation policy

After user-confirmed hub testing:

- Update the relevant status in `TODO.md`, including test date, tester, observed reports, and
  limitations.
- Do not recreate separate bug or TS0210 work-list files.
- On explicit release approval only, update the driver version/timestamp/history and synchronize
  `packageManifest.json` release version, date, and notes.
- Keep the public acceleration, shock, sensitivity, batteryStatus, tamper, and command names
  compatible.
