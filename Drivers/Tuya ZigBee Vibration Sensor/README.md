# Tuya ZigBee Vibration Sensor

Hubitat Elevation driver for Tuya Zigbee vibration sensors and selected
Third Reality and Samsung/Samjin vibration, tilt, and multisensor devices.

The driver is maintained as a legacy monolithic Groovy driver and supports
both standard Zigbee IAS Zone devices and Tuya EF00 datapoint devices.

Current development source version: 1.4.7

## Features

- Vibration state through the standard `AccelerationSensor` capability.
- Optional simulated `ShockSensor` events.
- IAS tamper and battery-low status reporting.
- Battery voltage and Tuya battery percentage reporting.
- Configurable vibration reset timeout.
- IAS sensitivity configuration and readback.
- Three-axis X/Y/Z acceleration reporting for supported devices.
- Device health monitoring and `healthStatus`.
- Ping round-trip-time reporting through the `rtt` attribute.
- Refresh and configuration support for supported device families.

## Installation

### Hubitat Package Manager

1. Open Hubitat Package Manager.
2. Search for `Tuya ZigBee Vibration Sensor`.
3. Install the driver.
4. Pair the supported device after installing the driver whenever possible.

If the driver was not installed before pairing and Hubitat did not
auto-select it during pairing, pair the device again without deleting it.
Do not delete the device as a first step.

The driver is available through Hubitat Package Manager as described in the
[Hubitat Community release thread](https://community.hubitat.com/t/release-tuya-zigbee-vibration-sensor/138208).

### Manual installation

Import the driver from the development branch:

```text
https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20ZigBee%20Vibration%20Sensor/Tuya%20ZigBee%20Vibration%20Sensor.groovy
```

After importing or updating the driver:

1. Open the device page.
2. Select `Tuya ZigBee Vibration Sensor` as the device type.
3. Save the device.
4. Follow the device-specific wake-up procedure when settings need to be
   written.
5. Click Refresh only when the device is awake and a refresh is needed.

Never press Configure for a battery-powered sleepy Zigbee device. When a
setting must be written, use the documented device-specific wake-up and Save
Preferences procedure instead.

## Supported device families

| Device family | Model and manufacturer examples | Main protocol |
|---|---|---|
| Tuya TS0210 IAS vibration sensors | Model `TS0210` with `_TYZB01_*` and `_TZ3000_*` manufacturer IDs | IAS Zone |
| Third Reality vibration sensor | Model `3RVS01031Z`, manufacturer `Third Reality, Inc` | IAS Zone and private cluster `0xFFF1` |
| Third Reality garage-door tilt sensor | Model `3RDTS01056Z`, manufacturer `Third Reality, Inc` | IAS Zone and private cluster `0xFF01` |
| Samsung/Samjin multisensor | Model `multi`, manufacturer `Samjin` | IAS Zone and private cluster `0xFC02` |
| Tuya TS0601 vibration/contact sensor | Manufacturer `_TZE200_kzm5w4iz` | Tuya EF00 |
| Tuya ZG-102ZM | Manufacturers `_TZE200_jfw0a4aa` and `_TZE200_wzk0x7fq` | Tuya EF00 and IAS |
| Tuya ZG-103Z tilt and XYZ sensors | `_TZE200_iba1ckek`, `_TZE200_hggxgsjj`, `_TZE200_yjryxpot`, `_TZE200_afycb3cg`, and `_TZ321..._5oy7cysk` variants | Tuya EF00 |

Some fingerprints in the driver are marked as not tested. New devices should
be added only after their exact Hubitat fingerprint has been captured.

The Third Reality garage-door tilt sensor is not automatically fingerprinted
by this driver. Assign this driver manually when its contact and acceleration
handling is required.

## Capabilities and attributes

### Standard capabilities

- `Sensor`
- `AccelerationSensor`
- `ShockSensor`
- `TamperAlert`
- `Battery`
- `Configuration`
- `Refresh`
- `Health Check`
- `ThreeAxis`

### Custom attributes

| Attribute | Description |
|---|---|
| `batteryVoltage` | Battery voltage reported by the device |
| `batteryStatus` | `normal` or `replace` |
| `healthStatus` | `unknown`, `online`, or `offline` |
| `lastBattery` | Time of the most recent battery event |
| `rtt` | Ping round-trip time in milliseconds |
| `sensitivity` | Numeric IAS or ZG-102ZM sensitivity |
| `tuyaSensitivity` | ZG-103Z sensitivity enum |
| `contact` | Contact state for supported contact devices |
| `tilt` | Tilt state for supported tilt devices |

The standard `acceleration`, `shock`, `tamper`, `battery`, and `threeAxis`
attributes are provided by their respective capabilities.

## Sensitivity

IAS sensitivity uses numeric values from `0` to `50`:

- `0` is the highest sensitivity.
- `50` is the lowest sensitivity.

ZG-102ZM numeric sensitivity remains limited to `1..50`. ZG-103Z devices use
the existing `tuyaSensitivity` enum.

For sleepy devices, wake the device immediately before clicking Save
Preferences. The driver reads back IAS sensitivity when the device reports
attribute `0x0500:0x0013`.

Third Reality vibration sensitivity is configured on the device itself,
typically through its hardware DIP switches. The driver does not expose the
IAS sensitivity preference for that device.

## Vibration and status behavior

- Repeated active IAS notifications re-arm the inactivity timer.
- The acceleration state remains active until the configured timeout expires
  after the final active notification.
- Repeated active notifications do not generate duplicate acceleration or
  shock events.
- Tamper and battery-low events are change-only.
- The first observed IAS status may initialize `tamper: clear` and
  `batteryStatus: normal`.
- The Third Reality garage-door path keeps its separate contact and battery
  handling.

## Preferences

The available preferences depend on the device family:

- Information and debug logging.
- IAS or ZG-102ZM sensitivity.
- ZG-103Z Tuya sensitivity.
- Vibration reset timeout.
- Three-axis reporting.
- Shock sensor simulation.
- Health-check method and interval.
- Periodic battery reporting.
- Third Reality garage-door open delay and calibration.

Advanced preferences are hidden until Advanced Options is enabled.

## Commands

- `configure` - Configure reporting on supported devices when the
  device-specific procedure allows it. Never use it for battery-powered
  sleepy Zigbee devices.
- `refresh` - Read battery and device-specific attributes.
- `ping` - Read the Basic cluster and report round-trip time.
- `setAccelarationInactive` - Manually reset acceleration to inactive.

`setAccelarationInactive` is intentionally misspelled for compatibility with
existing Hubitat automations.

## Troubleshooting

### The device does not report

- Confirm that the device is using this driver.
- If the driver was not installed before pairing and was not auto-selected,
  pair the device again without deleting it.
- For a battery-powered sleepy device, do not press Configure.
- Use the device-specific wake-up procedure before saving settings.
- Click Refresh only when the device is awake and a refresh is needed.
- Enable debug logging temporarily and inspect Live Logs.

### Acceleration remains active

Set an appropriate `vibrationReset` timeout or use
`setAccelarationInactive` to reset the state manually.

### Sensitivity changes do not apply

Wake the device immediately before clicking Save Preferences. Confirm the
write and readback in Live Logs and Current States.

## Known limitations

- ZCL battery percentage attribute `0x0001:0x0021` is not currently handled by
  the custom parser.
- The `_TZE200_kzm5w4iz` contact datapoint remains log-only by design.
- Some device fingerprints are untested and require real-device validation.
- Development branch behavior may change before the next package release.

## Project documentation

- [Changelog](CHANGELOG.md)
- [Open work and verification backlog](TODO.md)
- [Agent and contribution rules](AGENTS.md)
- [Hubitat Community release thread](https://community.hubitat.com/t/release-tuya-zigbee-vibration-sensor/138208)
- [Driver source](Tuya%20ZigBee%20Vibration%20Sensor.groovy)

## License

This project is licensed under the Apache License, Version 2.0.
