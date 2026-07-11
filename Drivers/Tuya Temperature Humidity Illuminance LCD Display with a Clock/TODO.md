# Forum-Derived TODO — Tuya Temperature Humidity Illuminance LCD Display with a Clock

Analysis date: 2026-07-11  
Forum cutoff: 2026-06-06, post 693  
Topic: https://community.hubitat.com/t/-/88093  
Coverage: complete Discourse stream — 677 visible posts; highest visible post number 693

This backlog records actionable work extracted from the complete community topic and compared with
the current v2.1.2 driver, its release history, and `BUGS.md`. Deleted forum posts account for the
difference between the visible-post count and highest post number.

Status legend:

- `OPEN` — requested or reported and not implemented/resolved.
- `NEEDS_EVIDENCE` — requires a textual fingerprint, logs, or hardware confirmation.
- `BUGS` — overlaps the confirmed static-review backlog in `BUGS.md`.
- `RESOLVED` — implemented or confirmed working later in the topic.
- `OUT_OF_SCOPE` — belongs to another device class or project.
- `DEVICE_LIMITATION` — cannot be implemented using known device/firmware behavior.

## Open work

### 1. [ ] `OPEN` — Support TS0601 `_TZE284_hdml1aav` five-in-one soil sensor

**Requested outcome:** expose all reported measurements — soil moisture, temperature, humidity,
illuminance, and fertility — without causing the sensor to stop reporting.

Evidence:

- Initial report and experimental driver: https://community.hubitat.com/t/-/88093/687
- Maintainer identified the device as TS0601 `_TZE284_hdml1aav` and noted probable firmware/DP
  differences: https://community.hubitat.com/t/-/88093/688
- Unresolved follow-up: https://community.hubitat.com/t/-/88093/689

Known data:

- Model: `TS0601`
- Manufacturer: `_TZE284_hdml1aav`
- Forcing `TS0601_Soil_NEO` exposes only temperature, humidity, and illuminance.
- A custom Zigbee2MQTT-derived driver exposed more values but reportedly stopped receiving data
  after several minutes.

Required investigation:

- Capture complete Tuya DP logs while independently changing each of the five measurements.
- Record each DP number, datatype, scaling, and raw value range.
- Compare the map with `TS0601_Soil_NEO`; create a separate model group if any meaning differs.
- Add both the metadata fingerprint and the `Models` map entry.
- Identify any initialization command responsible for reporting stopping.
- Look for writable reporting-frequency DPs and verify them on real hardware.

### 2. [ ] `NEEDS_EVIDENCE` — Add Jost's unknown three-DP T/H/illuminance sensor

**Requested outcome:** correctly expose illuminance, temperature, and humidity and, if supported by
the firmware, reduce the sensor's excessive Zigbee reporting frequency.

Evidence:

- Report, screenshots, and DP observations: https://community.hubitat.com/t/-/88093/691
- Maintainer confirmed it is an unknown device and accepted it for a future update:
  https://community.hubitat.com/t/-/88093/692
- Reporter produced a private modification but reporting-rate configuration remains unresolved:
  https://community.hubitat.com/t/-/88093/693

Observed mapping from the report:

- DP 2: illuminance
- DP 6: temperature
- DP 7: humidity
- Current generic processing applies the wrong mapping/scaling.

Evidence still required:

- Obtain the fingerprint as text: model, manufacturer, endpoint, application version, device ID,
  in-clusters, and out-clusters. The identifier is currently present only in a screenshot.
- Capture full raw Tuya messages to confirm DP datatypes, signedness, and scaling.
- Determine whether the high report rate is controlled by writable DPs. Event throttling in the
  driver does not reduce Zigbee network traffic.

Implementation direction after evidence is supplied:

- Add an exact fingerprint and `Models` entry.
- Use a distinct model group rather than altering generic DP meanings for existing devices.
- Expose only capabilities the device actually implements.

### 3. [ ] `OPEN` / `BUGS` — Suppress false 100% humidity from `_TZ3210_ncw88jfq`

**User-visible problem:** three TS0201 `_TZ3210_ncw88jfq` sensors reportedly send an hourly humidity
sample in hundredths while ordinary samples use tenths. The driver interprets the hourly sample as
greater than 100% and clamps it to 100%, generating a false humidity event.

Evidence:

- Reproduction from three sensors with raw logs: https://community.hubitat.com/t/-/88093/663
- Maintainer's mixed-scaling/firmware diagnosis: https://community.hubitat.com/t/-/88093/664
- Reporter chose ignoring invalid reports rather than guessing their scaling:
  https://community.hubitat.com/t/-/88093/665
- Follow-up question about zero/negative values: https://community.hubitat.com/t/-/88093/666

Known data:

- Model: `TS0201`
- Manufacturer: `_TZ3210_ncw88jfq`
- Reported examples include raw `221`–`234`, interpreted as 221%–234%, followed shortly by the
  plausible 22.1%–23.4% reading.

Implementation direction:

- Cross-reference the invalid-humidity handling findings in `BUGS.md`; do not create a competing
  global fix.
- Scope any workaround to `_TZ3210_ncw88jfq` unless logs prove other manufacturers behave the same.
- Ignore values above 100% for this device instead of clamping them to 100%.
- Treat negative values as invalid. Confirm whether a real 0% report must remain valid.
- Verify with live logs covering both the hourly packet and normal humidity-change packets.

### 4. [ ] `OPEN` — Avoid the external-probe child `Refresh` warning

**User-visible problem:** `_TZE284_hodyryli` external-probe support works, but refreshing its child
logs `componentRefresh() called but device is not DS18B20 model (TS0601_ZTH03PRO)`.

Evidence:

- Both temperatures confirmed working and warning reproduced:
  https://community.hubitat.com/t/-/88093/685

Known data:

- Model: `TS0601`
- Manufacturer: `_TZE284_hodyryli`
- Model group: `TS0601_ZTH03PRO`
- Probe child device: `<parent-dni>-probe`
- Current `componentRefresh()` handles only the `DS18B20` group.

Implementation direction:

- Add explicit `TS0601_ZTH03PRO` handling, likely by querying all Tuya DPs from the parent, if the
  device responds to that query.
- If the sleepy firmware cannot refresh on demand, make child refresh a documented no-op without a
  misleading warning.
- Verify that refreshing either parent or child does not disturb normal probe reports.

## Reports needing more evidence

### 5. [ ] `NEEDS_EVIDENCE` — TS0201 battery value remains stale after replacement

Evidence: https://community.hubitat.com/t/-/88093/659 through
https://community.hubitat.com/t/-/88093/661

The debug log shows the device continuing to transmit raw battery value `70` (35%), so the available
evidence points to device firmware/reporting behavior rather than a demonstrated driver conversion
error.

Request before opening a code change:

- Textual fingerprint and device data.
- Raw battery reports before and after battery replacement.
- Results after waking, re-pairing without deletion, and waiting for the documented periodic report.
- Measured battery voltage, if available.

### 6. [ ] `NEEDS_EVIDENCE` — Previously working unidentified temperature sensor stopped reporting

Evidence: https://community.hubitat.com/t/-/88093/667

The identifying data exists only in screenshots and no follow-up was posted. Request the complete
fingerprint as text, current driver version/model group, pairing logs, and ordinary receive logs.

### 7. [ ] `NEEDS_EVIDENCE` — Validate `_TZE284_rqcuwlsa` ZDO responder experiment

Evidence:

- Original disconnect report: https://community.hubitat.com/t/-/88093/636
- ZDO hypothesis: https://community.hubitat.com/t/-/88093/637
- Experimental responder announcement: https://community.hubitat.com/t/-/88093/640
- Later report working with `Respond to ZDO requests` disabled:
  https://community.hubitat.com/t/-/88093/662

Do not enable the responder by default or remove it based on this thread alone. Capture controlled
pairing/rejoin tests from multiple devices with the option both enabled and disabled. ZDO handling is
normally a platform responsibility, and the available evidence does not show that the custom
responses caused the later success.

## Already resolved, declined, or outside this backlog

### `RESOLVED` — `_TZE284_hodyryli` external probe support

Requested at https://community.hubitat.com/t/-/88093/672, added in v2.1.0 at
https://community.hubitat.com/t/-/88093/673, corrected in the later v2.1.1 build, and confirmed working
at https://community.hubitat.com/t/-/88093/685. Only the child-refresh warning remains open as item 4.

### `RESOLVED` — `_TZE284_9ern5sfh` / RSH-HS03 support

Reported at https://community.hubitat.com/t/-/88093/675, added as `TS0601_Tuya_3` at
https://community.hubitat.com/t/-/88093/676, and both devices were confirmed working after re-pairing
at https://community.hubitat.com/t/-/88093/682.

### `RESOLVED` — DS18B20 child-switch support

Requested at https://community.hubitat.com/t/-/88093/626 and implemented in v2.0.0 at
https://community.hubitat.com/t/-/88093/627. Later releases added four-relay support for
`_TZ3218_ya5d6wth`.

### `RESOLVED` — COOLO CS-201Z soil sensors

Version 2.1.2 added `_TZE200_npj9bug3` and `_TZE200_wrmhp6b3` to the new
`TS0601_Soil_Coolo` group: https://community.hubitat.com/t/-/88093/686

### `DEVICE_LIMITATION` — On-demand illuminance refresh for contact/illuminance devices

The sleepy EF00 contact sensor reports illuminance only with contact activity. No known EF00 command
can request a current reading, and standard ZHA reporting configuration is not supported. See
https://community.hubitat.com/t/-/88093/73.

### `OUT_OF_SCOPE` — Use LCD sensor buttons as thermostat/scene controls

These buttons are local pairing or Celsius/Fahrenheit controls and do not report as Zigbee button
inputs. See the request and explanation at https://community.hubitat.com/t/-/88093/39 and
https://community.hubitat.com/t/-/88093/40.

### `OUT_OF_SCOPE` — Wi-Fi/Bluetooth variants sold under similar listings

Devices that do not contain a Zigbee radio cannot use this driver. Misleading marketplace listings
and Tuya Cloud alternatives are discussed at https://community.hubitat.com/t/-/88093/46 and
https://community.hubitat.com/t/-/88093/60.

