# Aqara P100 Multi-State Sensor driver for Hubitat

A Hubitat Elevation driver for the **Aqara P100 Multi-State Sensor (DWZTCGQ11LM)** — a small
battery-powered Zigbee sensor that can be used either as a **motion/vibration sensor for objects**
or as a **door/window contact sensor**, switched by a preference.

The P100 is not supported by any Hubitat inbuilt driver: it reports its state through the Aqara
manufacturer-specific cluster `0xFCC0` and through a repurposed door-lock cluster, neither of which
the generic Zigbee drivers understand.

> **Status: work in progress (v0.1.6).** Please report problems in the community thread, with debug
> logs attached.

The recommended way to install the driver is from Hubitat Package Manager
([HPM](https://community.hubitat.com/t/beta-hubitat-package-manager/38016)) — search for
"Aqara P100 Multi-State Sensor" or by the tag "Zigbee". If you have already installed the driver
manually, run a **Match Up** in HPM first, then Update.

You can also install or update it manually from the raw `development` link below.

Driver code (development branch):
[Aqara_P100_Multi_State_Sensor.groovy](https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20P100%20Multi-State%20Sensor/Aqara_P100_Multi_State_Sensor.groovy)

Community thread: <https://community.hubitat.com/t/release-aqara-multi-state-sensor-p100-zigbee-driver-c8-only/163540>

---

## Supported models

| Device | Details |
|---|---|
| **Aqara Multi-State Sensor P100** | Model: `lumi.vibration.agl002`<br>Manufacturer: `Aqara`<br>Marketing name: DWZTCGQ11LM<br>Features: contact, vibration, movement, orientation, triple-tap, fall, device posture, battery<br>Battery: internal coin cell<br>Driver status: **work-in-progress** |

Support evidence, per the repository documentation convention:

| Feature | Evidence |
|---|---|
| Pairing and fingerprint | **Reported** — corrected in v0.1.1 from real pairing data, tested by `@rad1` |
| Object-mode action events, orientation, posture | **Implemented unverified** |
| Door/window contact reporting | **Implemented unverified** |
| Battery reporting (voltage-derived) | **Reported** — reworked in v0.1.6 after feedback from `@user1974` |
| Preference writes (sensitivity, report interval, detection toggles) | **Implemented unverified** |
| Staying joined to the network long-term | **Unknown** — see *Known limitations* |

---

## The two operating modes

The P100 works in exactly one of two modes at a time, set by the **Device Mode** preference. The
preference page changes shape depending on which mode is selected, and the driver clears the other
mode stale attributes when you switch.

### Object mode (default)

Attach the sensor to a moving object — a drawer, a bike, a washing machine, a safe. It reports
discrete events rather than a continuous state:

| Attribute | Values | Notes |
|---|---|---|
| `lastAction` | `movement`, `vibration`, `orientation`, `triple_tap`, `fall` | the event that just occurred |
| `acceleration` | `active` / `inactive` | pulsed `active` for movement, vibration and fall, then automatically reset to `inactive` after 3 seconds |
| `orientation` | `face_up`, `face_down`, `vertical`, `tilt` | current physical orientation |
| `devicePosture` | `unknown`, `normal`, `abnormal` | `abnormal` usually means the sensor is mounted incorrectly or needs calibration |

`acceleration` is what you want for most rules — it is a standard Hubitat capability, so it works
with any app that accepts an acceleration sensor. Use `lastAction` when you need to distinguish
*which* kind of event happened.

### Door/window mode

Mount the two-part sensor on a door or window and it behaves as a plain contact sensor:

| Attribute | Values |
|---|---|
| `contact` | `open` / `closed` |

The **Door/Window Type** preference (casement window, hopper window, composite window, hinged door)
tells the device firmware what it is mounted on, which affects how it interprets its motion data.

---

## Attributes

| Attribute | Description |
|---|---|
| `contact` | open / closed — door_window mode only |
| `acceleration` | active / inactive — object mode only |
| `lastAction` | the last detected event — object mode only |
| `orientation` | current orientation — object mode only |
| `devicePosture` | installation sanity check reported by the device |
| `deviceMode` | the mode the device itself reports it is in |
| `battery` | battery percentage, derived from voltage |
| `batteryVoltage` | raw battery voltage |
| `powerSource` | `battery` |
| `healthStatus` | `online` / `offline` / `unknown` |
| `rtt` | round-trip time in ms, from the `ping` command |
| `_status_` | transient status messages, auto-cleared after 60 seconds |

## Commands

| Command | Description |
|---|---|
| `configure` | Re-initializes the driver after switching drivers, restarts the health check and re-reads the device settings. |
| `refresh` | Re-reads all settings and state from the device. **Wake the device first** (short button press) or the reads will be lost. |
| `ping` | Checks the device is reachable and measures the round-trip time. Usually times out on this device — it is a sleepy battery sensor and is asleep most of the time. |

## Preferences

| Preference | Range | Default | Shown in |
|---|---|---|---|
| Description text logging | on/off | on | always |
| Debug logging | on/off | on | always — auto-disables after 24 hours |
| Device Mode | object / door_window | object | always |
| Detection Sensitivity | 1 (low) to 10 (high) | 5 | always |
| Report Interval | seconds | 60 | always |
| Door/Window Type | casement / hopper / composite / hinged door | hinged door | door_window mode |
| Movement Detection | on/off | on | object mode |
| Vibration Detection | on/off | on | object mode |
| Orientation Detection | on/off | on | object mode |
| Fall Detection | on/off | on | object mode |
| Triple-Tap Detection | on/off | on | object mode |

---

## Installation and pairing

1. Add the driver: **Developer tools -> Drivers code -> New Driver -> Import**, paste the raw
   `development` link above, then **Save**.
2. Put the P100 into pairing mode (hold its button until the LED blinks) and pair it from
   **Devices -> Add device -> Zigbee**.
3. Pair it **close to the hub** if possible, and pair it a second time afterwards — Aqara sensors are
   notoriously reluctant to complete their initialization on the first attempt. Pairing again with
   the driver already installed does not create a duplicate device.
4. Wait for the driver to report the device mode in the log before relying on events.

### A note on changing preferences

The P100 is a **sleepy** battery device: it only listens for a few moments after it wakes up. If you
change a preference and nothing happens, press the device button briefly at the same moment as you
click **Save Preferences**.

You do not need to keep retrying by hand. The driver remembers which settings the device has actually
confirmed, and automatically re-sends anything that did not land the next time you save.

---

## Known limitations

- **Long-term network stability is unverified.** The driver performs an Aqara registration
  handshake and answers the device time-synchronization requests, because the device is suspected of
  leaving the Zigbee network after about 24 hours without them. This is a working theory, not a
  confirmed diagnosis. If your P100 drops off the network, please report it with logs.
- Vibration **strength** and **tilt angle** are not reported. Older Aqara vibration sensors expose
  these on Zigbee cluster `0x000C`; whether the P100 does too is not yet known.
- `ping` normally times out. That is expected for a sleepy sensor and does not mean the device is
  offline — use the `healthStatus` attribute instead.
- The battery percentage is calculated from voltage over a 2.5 to 3.0 V range, so it is an estimate.

---

## Revision history

* ver. 0.1.0 2026-04-18 - initial version; dedicated P100 driver based on the Aqara P1 Motion Sensor driver template
* ver. 0.1.1 2026-04-19 - corrected fingerprint; bug fixes (thanks to @rad1 for testing and feedback)
* ver. 0.1.2 2026-04-28 - bug fixes
* ver. 0.1.3 2026-04-29 - preventDeviceReset implementation
* ver. 0.1.4 2026-05-01 - more aqaraBlackMagic(); added Time cluster (0x000A) forced response; added ZDO handlers for End_Device_Timeout_Req (0x0036), Node_Desc_req (0x0002), Mgmt_Rtg_rsp (0x8032); added FCC0 attr 0x00FF handler for the device registration-response report
* ver. 0.1.5 2026-05-03 - a forced Time cluster reply is sent after every FCC0 attr 0x00DF diagnostic heartbeat
* ver. 0.1.6 2026-05-06 - bug fixes (tnx @user1974); battery level is derived from voltage only

A fuller, categorized history is in [CHANGELOG.md](CHANGELOG.md).

---

## Credits

* Hubitat, SmartThings, ZHA, Zigbee2MQTT, deCONZ and the other home automation communities for the
  shared information.
* Dan Gibson (`@absent42`) — Zigbee2MQTT P100 external converter,
  <https://github.com/absent42/Aqara-P100-Sensor>
* `@rad1` and `@user1974` — testing and feedback.

Licensed under the Apache License, Version 2.0.
