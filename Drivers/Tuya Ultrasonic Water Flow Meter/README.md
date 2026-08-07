# Tuya Ultrasonic Water Flow Meter

Hubitat Elevation driver for Tuya TS0601 Zigbee ultrasonic water meters, in both
the meter-with-valve and the meter-only variant.

These are the battery-powered ultrasonic meters sold as the "214C" and "213E"
families. They report total, monthly, daily and reverse water consumption, water
temperature, battery voltage and a fault status, and the valve models can be
opened and closed from Hubitat.

Current development source version: 3.4.0

> **Status: not yet released, and not yet tested on real hardware.**
> The maintainer does not own one of these meters. The datapoint decoding was
> derived from raw Zigbee logs posted by users in the
> [community thread](https://community.hubitat.com/t/tuya-smart-zigbee-ultrasonic-water-meters/142433)
> and cross-checked against the Zigbee2MQTT converters, then verified offline by
> replaying those captured frames. It has never run against a live meter.
> Feedback in the thread is what will get it released.

## Before you buy: this is a meter, not a flow sensor

This matters more than anything else in this document.

These devices are **consumption meters**, designed for utility billing. They are
battery powered and sleep as much as possible to make the battery last. The
device wakes on a fixed schedule and reports how much water has passed through it
since the last report. The schedule is built into the meter's firmware and can
only be one of eight values: 1, 2, 3, 4, 6, 8, 12 or 24 hours.

That means:

- You will see consumption totals, but only as often as the report period allows.
- The `rate` and `instantaneousFlowRate` attributes are a snapshot taken at the
  moment the meter woke up. They are **not** a live flow reading.
- **This device cannot be used for leak detection.** If you want to know that
  water is flowing right now, you need a mains-powered flow sensor, or a
  vibration sensor strapped to the pipe.

Do not expect values to appear immediately after pairing. If the meter is set to
its 12-hour default, the first real consumption report may be half a day away.

## Features

- Total water consumption, plus monthly, daily and reverse-flow totals.
- Selectable volume unit: cubic meters (default) or liters.
- Instantaneous flow rate, through the standard `LiquidFlowRate` capability.
- Water temperature, through the standard `TemperatureMeasurement` capability.
- Battery percentage and battery voltage.
- Valve open/close on meter models that have a valve, through the standard
  `Valve` capability.
- Meter identification number, as reported by the device.
- Decoded fault status (empty pipe, transducer, cover, magnetism, reverse flow
  and others).
- Configurable report period.
- Device health monitoring and `healthStatus`.
- Ping round-trip-time reporting through the `rtt` attribute.

## Installation

**This driver is not in Hubitat Package Manager yet**, and there is no
single-file bundle to paste into the Drivers Code editor yet either. Until it is
released, it can only be installed by importing the shared libraries alongside
it.

1. On the hub, go to **Developer tools -> Libraries code** and add these two
   libraries from the development branch:

   ```text
   https://raw.githubusercontent.com/kkossev/Hubitat/development/Libraries/commonLib.groovy
   https://raw.githubusercontent.com/kkossev/Hubitat/development/Libraries/deviceProfileLib.groovy
   ```

2. Go to **Developer tools -> Drivers code**, click **New driver -> Import**, and
   import:

   ```text
   https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Ultrasonic%20Water%20Flow%20Meter/Tuya%20Ultrasonic%20Water%20Flow%20Meter.groovy
   ```

3. Save the driver, then pair the meter.

If the meter was already paired to a different driver, open its device page,
select `Tuya Ultrasonic Water Flow Meter` as the device type, and click **Save
Device**. Do not delete the device as a first step.

If Hubitat picked a completely unrelated driver when you paired the meter, that
is the symptom this release fixes: an unrecognized TS0601 meter can end up on the
Tuya siren driver and log errors about melodies and alarms.

## Supported devices

| Manufacturer | Model | Valve | Notes |
|---|---|---|---|
| `_TZE200_vuwtqx0t` | `TS0601` | yes | "214C" ultrasonic water meter with valve |
| `_TZE284_vuwtqx0t` | `TS0601` | yes | Same meter, alternate manufacturer ID encoding |
| `_TZE284_ajlu4cud` | `TS0601` | no | Meter only, no valve or auto-clean |
| `_TZE200_zlwr0raf` | `TS0601` | assumed | "213E" - **unconfirmed**, see below |

To find out which one you have, open the device page and look at the
**Data** section at the bottom for `manufacturer` and `model`.

`_TZE200_zlwr0raf` has been listed since the driver's first draft in 2024 on the
basis of a product listing alone. Nobody has ever posted a log from one, so it is
currently assumed to behave like the 214C. If you own one, a debug log posted in
the community thread would settle it.

If your meter is not in this table, post its `manufacturer` and `model` values
plus a debug log in the community thread. See
[Identify a Zigbee Device](https://github.com/kkossev/Hubitat/wiki/Hubitat-How-To-:-Identify-a-Zigbee-Device).

## Capabilities and attributes

### Standard capabilities

- `Sensor`
- `Actuator`
- `Battery`
- `TemperatureMeasurement`
- `LiquidFlowRate`
- `Valve`
- `PowerSource`
- `Refresh`
- `Configuration`
- `Health Check`

### Custom attributes

| Attribute | Description |
|---|---|
| `waterConsumed` | Total water consumption |
| `dailyConsumption` | Consumption since the daily rollover |
| `monthConsumption` | Consumption since the monthly rollover |
| `reverseWaterConsumed` | Total water measured flowing backwards |
| `instantaneousFlowRate` | Flow rate in liters per hour, as the meter reports it |
| `batteryVoltage` | Battery voltage in volts |
| `meterId` | The meter's identification number |
| `faults` | Active fault list, or `no_alarm` |
| `reportPeriod` | The meter's current report period |
| `autoClean` | Auto-clean setting, valve models only |
| `monthAndDailyFrozenSet` | Raw rollover setting reported by the meter |
| `healthStatus` | `unknown`, `online`, or `offline` |
| `rtt` | Ping round-trip time in milliseconds |

The standard `battery`, `temperature`, `valve` and `rate` attributes are provided
by their respective capabilities.

## Volume units

The meter always sends volumes in liters. The **Water Volume Unit** preference
chooses what the driver publishes:

- **m3 (cubic meters)** - the default, and what the meter's own LCD shows.
  A reading of 324 liters is published as `0.324`.
- **L (liters)** - the raw value, published as `324`.

The setting applies to `waterConsumed`, `dailyConsumption`, `monthConsumption`
and `reverseWaterConsumed`. Changing it affects new reports only; values already
recorded in the event history keep the unit they were logged with, so pick one
before you start building charts or dashboards on top of it.

`instantaneousFlowRate` is always in liters per hour, and `rate` is always in
liters per minute, regardless of this setting.

## Report period

The **Report Period** preference sets how often the meter wakes up and reports:
1, 2, 3, 4, 6, 8, 12 or 24 hours. Choose a value and click **Save Preferences**.

The eight options are fixed in the meter's firmware; the driver cannot offer a
shorter interval. A shorter period gives more frequent updates at the cost of
battery life.

The setting is only written when the meter is awake enough to accept it. If it
does not appear to take effect, wait for the next report and check whether the
preference has changed back to the meter's own value.

## Valve control

On the meter models that include a valve, the **Open** and **Close** buttons
control it, and the `valve` attribute reflects the meter's reported state.

On the meter-only models the buttons are still shown, because Hubitat capabilities
are fixed for the whole driver, but pressing them does nothing except write a
warning to the log.

**Auto Clean** is a periodic self-cleaning cycle for the valve. Its exact behavior
is not documented by Tuya and has not been confirmed on a real device.

## Battery

These meters use a non-rechargeable 3.6 V lithium cell (ER14505 size). It is
**not** recharged by water flow or by anything else - when it is depleted, it is
replaced.

The meter reports voltage, not a percentage. The driver derives the percentage
from the voltage over a 2.5 V to 3.7 V range, so a healthy new cell reads close to
100% and the percentage falls as the cell drains. Treat it as an indication
rather than a precise gauge, and use the `batteryVoltage` attribute if you want
the number the meter actually sent.

## Preferences

- **Enable descriptionText logging** - information logging.
- **Enable debug logging** - detailed logging, switches itself off after 24 hours.
- **Water Volume Unit** - cubic meters or liters.
- **Polling Interval** - disabled by default, and it should normally stay that
  way. These meters sleep between reports and will not answer a poll, so polling
  wastes battery without producing data.
- **Report Period** - how often the meter wakes and reports.
- **Auto Clean** - valve models only.
- **Device Profile** - only needed if the meter was not recognized automatically.

## Commands

- `refresh` - ask the meter for all of its datapoints. It will only answer if it
  happens to be awake, so a refresh often produces nothing. This is normal.
- `open` / `close` - operate the valve, on models that have one.
- `ping` - measure round-trip time to the device and update the `rtt` attribute.
- `configure` - a drop-down of administrative actions, for troubleshooting only:
  - `Configure the device` - re-run the driver's setup.
  - `*** LOAD ALL DEFAULTS ***` - reset every preference and state for this
    device back to the driver defaults.
  - `Reset Statistics` - clear the driver's internal counters.
  - The `Delete All ...` entries wipe preferences, current states, scheduled jobs
    or state variables. Use them only if you are asked to.

## Troubleshooting

### No values appear after pairing

This is expected. The meter reports on its own schedule, which defaults to every
12 hours. Check the `reportPeriod` attribute, and if you want faster feedback,
set a shorter report period and wait for one cycle.

### Refresh does nothing

Also expected. A sleeping meter cannot answer. Refresh only works in the brief
window when the meter is awake.

### The log shows unrelated messages about alarms or melodies

The meter is on the wrong driver. Hubitat matched it to the Tuya siren driver at
pairing time. Open the device page, select `Tuya Ultrasonic Water Flow Meter` as
the device type, and click **Save Device**.

### The log shows "NOT PROCESSED" warnings

The meter is sending a datapoint this driver does not recognize. Enable debug
logging and post the full log line, including the `descMap.data` part, in the
community thread. Those raw bytes are exactly what is needed to add support.

### Consumption values look wrong by a factor of 1000

Check the **Water Volume Unit** preference. If the totals look 1000 times too
large you are reading liters as if they were cubic meters, or the other way
round.

## Known limitations

- The driver has not been tested on real hardware. The volume scale factor in
  particular is inferred, because every log captured so far came from a meter
  with zero consumption.
- The daily and monthly consumption datapoints carry a 4-byte timestamp that is
  not decoded. Only the consumption value from those datapoints is published.
- `monthAndDailyFrozenSet` is published as a raw number because its meaning is
  not documented.
- The `_TZE200_zlwr0raf` "213E" fingerprint is unconfirmed.
- The auto-clean and report-period write paths have never been exercised on a
  real meter.
- The driver is not yet available through Hubitat Package Manager.
- Development branch behavior may change before the first release.

## Project documentation

- [Changelog](CHANGELOG.md)
- [Open work and verification backlog](TODO.md)
- [Agent and contribution rules](AGENTS.md)
- [Hubitat Community thread](https://community.hubitat.com/t/tuya-smart-zigbee-ultrasonic-water-meters/142433)
- [Driver source](Tuya%20Ultrasonic%20Water%20Flow%20Meter.groovy)

## Credits

The datapoint names, the valve and auto-clean handling, and the battery voltage
range in this driver were established by
[@ed.net](https://community.hubitat.com/t/-/142433/12) in the community thread.
The raw Zigbee capture that made support for the meter-only variant possible was
posted by [@jw970065](https://community.hubitat.com/t/-/142433/25). The
[Zigbee2MQTT](https://github.com/Koenkk/zigbee-herdsman-converters) converters
`TS0601_water_meter` and `TS0601_water_valve` were used as the reference
implementation.

## License

This project is licensed under the Apache License, Version 2.0.
