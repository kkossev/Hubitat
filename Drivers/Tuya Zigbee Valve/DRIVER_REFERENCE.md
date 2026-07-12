# Tuya Zigbee Valve — Driver Reference

A Hubitat Elevation Zigbee driver for Tuya, Sonoff, GiEX, Saswell, Lidl/Parkside, FrankEver, TS0049 and related water valves and irrigation timers. It auto-detects the connected model, exposes a common `valve`/`switch` interface (for Alexa, Google Home and HomeKit), and adds model-specific irrigation features such as auto-off timers, capacity/duration modes, water metering and multi-valve control. The SONOFF SWV-ZF2 dual valve additionally creates two component child devices (requires the companion *Tuya Zigbee Valve Component Child* driver). Author: Krassimir Kossev (kkossev), Apache 2.0.

## Commands

| Command | Description |
|---|---|
| **Open / Close** | Open or close the valve, using each model's own command format. On the classic Sonoff SWV, Open also arms the device's built-in auto-close timer from the auto-off preference; on SWV-ZN/ZF2 the configured duration is sent to the device first; Saswell and GiEX re-send the configured duration a few seconds after opening. On the SWV-ZF2 these control the first channel; the summary `valve` state reports open if either channel is open. |
| **On / Off** | Alias of Open/Close via the `switch` capability, for voice-assistant and dashboard compatibility. |
| **Refresh** | Polls the device for its current valve/switch state, battery and metering values, using a model-specific set of queries. On the classic Sonoff SWV the valve-state poll is deliberately skipped while the valve is open, because it would cancel the device's auto-close countdown (a firmware bug workaround). |
| **Configure** | Re-applies the Zigbee configuration and default settings, and creates the two SWV-ZF2 component children. It runs automatically at pairing, on **initialize**, and every time preferences are saved — it rarely needs to be pressed manually. |
| **Ping** | Sends a quick test message to the device and reports the response time in the `rtt` attribute. If no reply arrives within 6 seconds, `rtt` is set to `timeout`. |
| **initialize** | ⚠ Destructive full reset — wipes all settings, states, scheduled jobs and child devices, then re-initializes with defaults. Intended for use after switching from another driver; on a configured SWV-ZF2 the component children are deleted and recreated. |
| **setIrrigationTimer** | Sets the auto-off (irrigation duration) timer; value is in seconds for most models, minutes for SWV-ZN/ZF2 (0–719) and TZE284. Zero disables auto-off. The value is also stored into the *Auto off timer* preference and sent to the device one second later. |
| **setIrrigationCapacity** | Sets the auto-off water volume in liters (0–999), for Saswell and GiEX only. The value is stored into the *Irrigation Capacity* preference and sent to the device one second later; it takes effect when the irrigation mode is `capacity`. |
| **setIrrigationMode** | Selects whether irrigation stops by `duration` or by `capacity` (Saswell and GiEX). The mode is sent to the device immediately. |
| **setValveOpenThreshold** | Sets the valve open threshold in percent (0–100), for the FrankEver FK_V02 only. The value is rounded to the nearest 10%, saved to the matching preference and attribute, and sent to the device. |
| **setValve2** | Opens or closes the second valve of GiEX/TZE284 double valves and the SWV-ZF2. The command acts on the second valve channel directly. |
| **updateZigbeeFirmware** | Requests an over-the-air firmware update, for supported non-Tuya devices (e.g. Sonoff). The command is refused with a warning on Tuya devices, which do not support firmware updates through the hub. |

*Child device (SWV-ZF2 channels):* **setManualIrrigationDuration** (1–719 min) and **setManualIrrigationAmount** (0–10000, unit from child preference) configure that channel's manual-irrigation defaults through the parent.

## Current States

Which attributes appear depends on the connected model — only the ones the device actually reports are populated. Events triggered by a driver command are tagged `digital`; device-originated reports are tagged `physical`.

- **valve** — the main valve state, `open` or `closed` (plus transient `opening`/`closing` when the experimental three-state option is on, reverting to `unknown` on command timeout). On the SWV-ZF2 this is a summary: `open` if either channel is open, `closed` only when both are closed.
- **switch** — an `on`/`off` mirror of `valve`, kept in sync for Alexa / Google Home / HomeKit and dashboard compatibility.
- **battery** — battery charge in percent, decoded from each model's own battery report (the TS0049 reports only three levels, shown as 33/66/100%). Forced to 0 when a battery-powered device is marked offline.
- **batteryVoltage** — battery voltage in volts, for devices that report it.
- **powerSource** — `dc` or `battery` per the detected device model; set to `unknown` while the device is offline.
- **healthStatus** — `online`/`offline` presence indicator: any received message marks the device online, three missed 3-hourly health checks mark it offline.
- **rtt** — round-trip time of the last Ping in milliseconds, or `timeout` if no reply arrived within 6 seconds.
- **lqi** / **rssi** — Zigbee link quality and signal strength, reported by Sonoff valves.
- **rate** — current water flow in m³/h (classic Sonoff SWV).
- **timerState** — irrigation timer state (`disabled`, `active (on)`, `enabled (off)`) reported by GiEX and Saswell timers.
- **timerTimeLeft** — remaining irrigation time: in seconds for Saswell, in minutes for TZE284 (first valve) and TS0049.
- **timerTimeLeft2** — remaining countdown for the second TZE284 valve, in minutes.
- **lastValveOpenDuration** — how long the (last) irrigation ran, in seconds (Saswell, TZE284 and Sonoff models). Reset to a `***` placeholder when a Sonoff valve is commanded open.
- **lastValveOpenDuration2** — the same for the second TZE284 valve, in seconds.
- **weatherDelay** — Saswell rain-delay setting: `disabled`, `24h`, `48h` or `72h`.
- **irrigationStartTime** / **irrigationEndTime** — when the last irrigation started/ended: a text value from GiEX or a formatted date/time from Sonoff. Reset to a `****-**-**` placeholder when a Sonoff valve is commanded open.
- **lastIrrigationDuration** — GiEX last-watering duration as a text value, e.g. `00:01:10,0`.
- **waterConsumed** — accumulated water usage in liters: GiEX total or Sonoff SWV daily volume.
- **irrigationVolume** — real-time volume of the current Sonoff irrigation, in liters.
- **irrigationDuration** — the configured auto-off duration as shown to the user (`disabled` when 0), updated by `setIrrigationTimer`; the FrankEver FK_V02 also reports it from the device, in seconds.
- **irrigationCapacity** — the configured auto-off volume target in liters, echoed by the `setIrrigationCapacity` command (GiEX/Saswell).
- **valveStatus** — valve fault/abnormal state: Sonoff models report `normal`, water `shortage`, `leakage`, `fail-safe` and their combinations; TZE284 models report `manual`, `auto`, `idle` and fault states. Pre-set to `clear` when a non-ZF2 Sonoff valve is commanded open after a fault.
- **valveStatus2** — the same fault/state report for the second TZE284 valve.
- **valveOpenThreshold** — the configured FrankEver partial-opening threshold, in percent.
- **valveOpenPercentage** — the actual valve opening the FrankEver device currently reports, in percent.
- **workState** — classic Sonoff SWV working indicator, `idle` or `working`.
- **valve1** — the state of SWV-ZF2 channel 1 (`open`/`closed`/`unknown`), mirrored to the first component child device.
- **valve2** — the state of the second valve channel: SWV-ZF2 channel 2 (mirrored to the second child) or the TZE284 second valve.
- **waterMode** — whether irrigation is limited by `duration` or `capacity`, reported by GiEX and TS0049.
- **sonoffAutoShutOff** — the Sonoff lack-of-water auto shut-off timeout in minutes (firmware 1.0.4 or later).
- **manualIrrigationDuration** / **manualIrrigationMode** / **manualIrrigationAmountUnit** / **manualIrrigationAmount** / **manualFailSafe** — the five manual-irrigation default settings read back from Sonoff ZN/ZF2 valves (duration in minutes, mode `duration`/`capacity`, unit `US gallon`/`liter`, amount, fail-safe minutes). On SWV-ZN models they appear on the parent and also sync the matching unit/fail-safe preferences; on the SWV-ZF2 they are mirrored to both component children instead.

## Preferences

- **Description text logging / Debug logging** — control info-level and troubleshooting log output (debug should be off in normal use). Debug logging turns itself off automatically 24 hours after it is enabled, so it can be left on temporarily while diagnosing an issue.
- **Power-On Behaviour** — valve state after a power cycle: closed, open, or last state (hidden on models that don't support it). The selected value is sent to the device when preferences are saved, and defaults to *last state*.
- **Auto off timer (Irrigation Duration)** — auto-close delay in seconds (most models) or minutes (SWV-ZN/ZF2, TZE284); zero disables it. Shown for Saswell, GiEX, Sonoff, FrankEver and TZE284; the value is transmitted to the device as part of every open command (and, with *auto-send* on, re-sent when a GiEX device reports a state change). Defaults to 0 (no auto-off).
- **Irrigation Capacity** — auto-off volume in liters (0–999), for Saswell and GiEX. It only takes effect when the irrigation mode is set to *capacity*, and defaults to 99 liters.
- **Manual irrigation amount unit / fail-safe** — default amount unit (US gallon or liter) and safety timeout for the SWV-ZN manual-irrigation commands. Both are required fields (defaults *liter* and 0 minutes, 0–719); on *Save Preferences* they are sent to the device only when their value actually changed.
- **Valve Open Threshold** — FrankEver open percentage in 10% steps (note: adjusting it emits an event during preference rendering). Values are rounded to the nearest 10% when saved, and the preference defaults to 100% (fully open).
- **Advanced Options** — reveals: **Device Profile** override (default Auto-detect), **auto-send timeout on every command** (GiEX), experimental **three-state events**, **Sonoff Auto Shut Off** minutes (needs firmware ≥ 1.0.4), and **Ignore Duplicate Packets** (skips repeated identical Sonoff ZN status reports during irrigation, reducing log noise). These are marked as normally driver-managed, and the sub-options each appear only for the matching device family (default profile Auto-detect, auto-send on for GiEX / off otherwise, three-state off, Sonoff auto shut-off 30 min, duplicate-packet filtering on).

---

## Advanced Information

### State Variables

- **state.deviceProfile** — the device profile assigned at the last detection (e.g. `TS0601_GIEX_VALVE`). It is set during automatic model detection and used as a fallback when no separately-detected profile is stored.
- **state.detectedDeviceProfile** — the profile matched from the physical model and manufacturer of the device. This is the preferred source for the active profile, taking precedence over `state.deviceProfile` unless a profile is forced in the preferences.
- **state.activeDeviceProfile** — the profile currently in effect (the forced profile if set, otherwise the detected one). It drives every model-specific decision in the driver.
- **state.comment** — a static human-readable note listing the device families the driver supports. It is written once during a full initialization and is purely informational.
- **state.driverVersion** — the driver version and build timestamp last applied, used to trigger settings migration on upgrade. When it no longer matches the running code, the driver runs a non-destructive re-initialization (filling in any newly added settings and states without wiping existing ones) and reschedules the health check.
- **state.manualIrrigationSettings** — a cache of the last-known Sonoff ZN/ZF2 manual-irrigation defaults (mode, duration, amount unit, amount, fail-safe) as read from the device. It is refreshed whenever the device reports them and is used to fill in unchanged fields when only one parameter is written, so a partial update never clobbers the others.
- **state.stats** — a map of lifetime counters:
  - **RxCtr** — number of Zigbee messages received from the device. It is incremented for every inbound message the driver processes.
  - **TxCtr** — number of Zigbee commands sent to the device.
  - **rejoinCtr** — number of times the device has re-announced itself on the Zigbee network, so a rising value indicates an unstable connection.
- **state.states** — a map of transient runtime flags:
  - **isDigital** — true while a command-initiated (digital) valve change is expected, so the resulting event is tagged `digital` rather than `physical`. It is armed by Open/Close and auto-cleared 3 seconds later.
  - **isRefresh** — true briefly after a Refresh, so a polled report is not misread as a real valve change on Sonoff models. It is armed by Refresh and cleared after 5 seconds.
  - **debounce** — guards against duplicate switch events within a 300 ms window. It is set when a switch event is processed and cleared by a short timer, collapsing rapid duplicate reports into one.
  - **lastSwitch** — the last switch value processed, used for debouncing. It lets the driver recognize and drop a repeated identical report.
  - **lastBattery** — the last battery percentage physically reported by the device (battery-powered models, initialized to 100). It is updated only on real (physical) battery reports, so driver-generated battery events — such as the forced 0% when the device goes offline — do not overwrite the last genuine reading.
  - **notPresentCtr** — consecutive missed health checks; three misses mark the device offline (`healthStatus: offline`, `powerSource: unknown`, battery forced to 0 on battery models). It is reset to 0 whenever any message is received from the device and incremented on each 3-hourly health-check run.
  - *(Sonoff ZN keys, dynamically named)* — cached data used to filter out duplicate irrigation status reports and to convert the device's internal timestamps to real dates and times.
- **state.lastRx** — receive-side timing:
  - **parseTime** — the timestamp of the most recently received message. It records when the device was last heard from and underlies the presence/health tracking.
- **state.lastTx** — transmit-side timing:
  - **pingTime** — the timestamp of the last Ping, used to compute the round-trip time. The difference between this and the ping response is reported in the `rtt` attribute.
