# Open User Requests — Tuya Multi Sensor 4 In 1

Improvement requests harvested from all 1,161 posts available in the community thread
([RELEASE] Tuya Zigbee Multi-Sensor 4 In 1 (PIR motion sensors) w/ healthStatus) on
2026-07-11. This also consolidates the unresolved TODOs formerly listed at the bottom of
`README.md`. Post links are `https://community.hubitat.com/t/-/92441/<post#>` unless
another topic id is shown.

This list complements [BUGS.md](BUGS.md) (confirmed defects in v3.5.6). Items here are
**feature requests, device-support work, and unresolved user reports**, not reviewed bugs.
Analyze one item at a time; do not bump `version()`/`timeStamp()` until a release point,
and mark `[x]` only after confirmation on a real device/hub.

The current driver is for PIR sensors. The old mmWave profiles are deprecated and mmWave-only
requests belong in the separate **Tuya Zigbee mmWave Sensor** driver. The active
`TS0601_TZE284_4IN1` PIR+mmWave combination profile remains in scope.

---

## 1. Calibration and measurement usability

### 1.1 `[ ]` Temperature, humidity, and illuminance calibration offsets
Add per-device ± offsets for temperature and humidity and a correction/scale control for lux.
The original 4-in-1 request asked for all three; temperature/humidity offset support is also
still called out twice in the old README TODOs.
- Post: [#11](https://community.hubitat.com/t/-/92441/11) (nclark)
- Existing infrastructure: `temperatureLib` already handles `temperatureOffset`; the driver
  exposes an illuminance correction coefficient for some profiles. Verify which profiles are
  missing preferences before adding duplicate controls.

### 1.2 `[ ]` MYQ ZMS03 illuminance is about 10× too low
`TS0601_2IN1_MYQ_ZMS03` reports values roughly one tenth of two comparison sensors and reports
zero in dim but non-dark conditions. Determine whether this is a device limitation or whether
the profile needs a manufacturer-specific scale/correction default.
- Post: [#1138](https://community.hubitat.com/t/-/92441/1138) (ilkeraktuna)
- Needs: simultaneous raw DP values and reference-lux readings from more than one unit/model.

### 1.3 `[ ]` Reject physically invalid humidity reports
Ignore values above 100% (and decide how to log/count them) instead of emitting invalid events.
- Source: former README TODO.

### 1.4 `[ ]` Human-readable long motion-reset durations
Format messages such as `Motion reset to inactive after 43648s` as days/hours/minutes/seconds.
- Source: former README TODO.

## 2. Battery, power source, and sleepy-device behavior

### 2.1 `[ ]` Queue configuration and refresh commands until sleepy devices wake
Battery-powered sensors can miss preference writes because `updated()`/`refresh()` transmit
while the device sleeps. Store pending commands in state and flush them on the next check-in.
- Forum context: [#46](https://community.hubitat.com/t/-/92441/46),
  [#52](https://community.hubitat.com/t/-/92441/52)
- Source: former README TODO: “if isSleepy - store in state.cmds”.
- Take care to expire/de-duplicate queued writes and not replay stale profile commands.

### 2.2 `[ ]` SONOFF motion sensor reports voltage but not battery percentage
Investigate reporting/binding for `SONOFF_MOTION_IAS`; users currently may need to re-pair near
the hub before two-hour battery reporting starts.
- Posts: [#1116](https://community.hubitat.com/t/-/92441/1116)–
  [#1121](https://community.hubitat.com/t/-/92441/1121)
- Source: former README TODO: “check why only voltage is reported for SONOFF_MOTION_IAS”.

### 2.3 `[ ]` Complete battery reporting for TS0202, TS0601 2-in-1, and Fantem 4-in-1
Research model-specific battery encodings and the Fantem `battery1` behavior (currently seen as
100% or 0%). Do not fold in the confirmed low-percentage mapping defects already tracked as
BUGS A9/A10.
- Source: former README TODO.

### 2.4 `[ ]` TS0601 3-in-1 Battery/USB power-source transitions
Process the reported 0–4 power-source DP values and emit correct Hubitat `powerSource` changes.
- Source: former README TODO.

### 2.5 `[ ]` TUYATEC `53o41joc` IAS battery initialization/refresh
Test the required bindings and add refresh commands; battery was not reported after pairing.
- Source: former README TODO.

## 3. PIR configuration and device controls

### 3.1 `[ ]` LED disable support per compatible PIR profile
A user recently asked how to disable the Tuya 4-in-1 LED. The 4-in-1 has historically supported
an LED setting, while kkossev reported no known method for some 3-in-1 firmware. Audit which
current V3 profiles expose the control, repair missing profile metadata if necessary, and clearly
document models where firmware offers no LED DP/attribute.
- Posts: [#372](https://community.hubitat.com/t/-/92441/372),
  [#685](https://community.hubitat.com/t/-/92441/685)?[#687](https://community.hubitat.com/t/-/92441/687),
  [#1168](https://community.hubitat.com/t/-/92441/1168)

### 3.2 `[ ]` Missing sensitivity and retrigger/keep-time controls for IAS profiles
Research and expose supported sensitivity and keep-time attributes for remaining
`TS0202_MOTION_IAS` models. Early work confirmed these controls on `_TZ3000_msl6wxk9`, but the
old README still records a gap for the broader profile group.
- Posts: [#31](https://community.hubitat.com/t/-/92441/31),
  [#46](https://community.hubitat.com/t/-/92441/46)
- Do not duplicate BUGS B4 (`reportingTime4in1` advertises a command that cannot execute).

### 3.3 `[ ]` TS0601 2-in-1 `illuminance_interval` (DP 102)
Add profile metadata, decoding, and configuration for the illuminance reporting interval where
firmware supports it.
- Source: former README `TOOD` entry.

### 3.4 `[ ]` Publish practical `setPar` examples
Document how to change reporting time and other supported parameters from Hubitat automations,
including value units/ranges and a sleepy-device wake-up warning.
- Related thread: [t/115793 #12](https://community.hubitat.com/t/-/115793/12)

## 4. Refresh, binding, and profile maintenance

### 4.1 `[ ]` Review profile-specific binding commands in `configure()`
Audit standard cluster bindings and reporting configuration instead of relying on broad or empty
configuration maps. The immediate open case is the Sunricher/Azoula 4-in-1 profile, whose 0x0406
binding is commented out and whose illuminance reporting was unresolved at the end of the crawl.
- Posts: [#1171](https://community.hubitat.com/t/-/92441/1171)–
  [#1174](https://community.hubitat.com/t/-/92441/1174)
- Source: former README TODO: “check the bindings commands in configure()”.
- First verify whether the dedicated `SiHAS Multipurpose Sensor` driver resolves the report; the
  device itself may simply have stopped transmitting lux.

### 4.2 `[ ]` Complete refresh behavior for Tuya and Fantem profiles
Evaluate the old proposed refresh commands:
- Fantem 4-in-1: `zigbee.command(0xEF00, 0x07, '00')` (“Tuya Magic”).
- Other Tuya profiles: `zigbee.command(0xEF00, 0x03)`.

Only send commands to profiles proven to support them, and coordinate with BUGS A1/B2 so refresh
does not throw and can force humidity/illuminance events correctly.

### 4.3 `[ ]` Clear obsolete preferences safely when the device profile changes
Remove settings that belong only to the previous profile without destroying user preferences
shared by both profiles. Validate the existing profile-change/default-reset behavior first.
- Source: former README TODO.

### 4.4 `[ ]` Restore/maintain raw Tuya DP diagnostics in state
Add a bounded `state.tuyaDps` diagnostic view (as in related Tuya drivers) to help identify new
firmware mappings without allowing unbounded state growth.
- Source: former README TODO.

## 5. Missing fingerprints / new device support

### 5.1 `[ ]` TS0601 `_TZE200_agumlajc` Ikuü combination wall sensor
Confirmed absent from the v3.5.6 `deviceProfilesV3` fingerprints.
Add and test a profile for the wall-powered Ikuü sensor (PIR, lux, temperature, humidity, plus a
manual faceplate switch). Determine whether the switch needs Button/Switch capabilities or a
separate child device before implementation.
- Post: [#1077](https://community.hubitat.com/t/-/92441/1077) (Wealy)
- Reported fingerprint: `model:'TS0601', manufacturer:'_TZE200_agumlajc', endpointId:'01',
  application:'41'` (the post does not include the full in/out-cluster lists).
- Source: former README TODO.

### 5.2 `[ ]` Revisit short-range TS0202/MOES motion detection report
Determine whether the “must be ridiculously close” behavior is configurable sensitivity,
incorrect profile selection, placement, or device firmware—not automatically a driver defect.
- Related thread: [t/109917 #8](https://community.hubitat.com/t/-/109917/8)
- Source: former README TODO.

### Fingerprint audit notes (2026-07-11)

The full forum-thread manufacturer-ID audit found these additional IDs absent from this driver's
current source, but they should **not** be added here:

- `_TZE200_pay2byax`, `_TZ3000_26fmupbb`, `_TZ3000_oxslv1c9`, and `_TZ3000_yfekcy3n` are contact
  sensors; the first three were explicitly moved to the dedicated Tuya Zigbee Contact Sensor++
  driver ([#306](https://community.hubitat.com/t/-/92441/306)-
  [#365](https://community.hubitat.com/t/-/92441/365)); `_TZ3000_yfekcy3n` reports model `TS0203`
  ([#720](https://community.hubitat.com/t/-/92441/720)).
- `_TZ3218_t9ynfz4x`, `_TZE204_muvkrjr5`, `_TZE204_ztqnh5cg`, `_TZE204_7gclukjs`,
  `_TZE200_kb5noeto`, and `_TZE204_gkfbdvyx` are mmWave or PIR+mmWave presence devices reported
  against the old/deprecated radar portion of this driver; route them to the dedicated mmWave
  driver. See [#906](https://community.hubitat.com/t/-/92441/906),
  [#958](https://community.hubitat.com/t/-/92441/958),
  [#1054](https://community.hubitat.com/t/-/92441/1054),
  [#1126](https://community.hubitat.com/t/-/92441/1126), and
  [#1131](https://community.hubitat.com/t/-/92441/1131).

No other clear PIR/multi-sensor manufacturer ID posted in the thread was absent from the current
v3.5.6 source. Re-run this audit when new forum posts arrive; model `TS0601` alone is insufficient
to choose a profile.

## 6. Optional platform features and documentation

### 6.1 `[ ]` Zigbee OTA firmware-check command for eligible standard devices
Add Hubitat's `zigbee.updateFirmware()` command only for profiles where standard Zigbee OTA is
appropriate (for example Third Reality). Do **not** enable it indiscriminately for Tuya devices;
kkossev has explicitly warned that Tuya OTA outside its ecosystem can brick devices.
- Eligible-device request: [#1169](https://community.hubitat.com/t/-/92441/1169) (Third Reality)
- Tuya request explicitly declined: [#1161](https://community.hubitat.com/t/-/92441/1161)-
  [#1162](https://community.hubitat.com/t/-/92441/1162) (`_TZE200_3towulqd`)

### 6.2 `[ ]` Document per-profile capabilities and limitations
Expand README/wiki tables to distinguish independently reported lux from lux sent only near a
motion event, configurable vs fixed cooldown, LED availability, battery support, mains/battery
power, reporting-interval value semantics, and devices intentionally delegated to dedicated
drivers.
- Recent reporting/keep-time documentation request: [#1166](https://community.hubitat.com/t/-/92441/1166)

---

## Already covered elsewhere or out of scope (do not duplicate)

- Confirmed implementation defects in v3.5.6 → [BUGS.md](BUGS.md), including refresh NPE/event
  forcing, lux threshold zero, dead scene-switch events, battery percentage mappings, and
  `reportingTime4in1`.
- `TS0601_2IN1` keep-time decoding uses the wrong option map → BUGS/implementation analysis should
  be completed before treating the former README note as a separate feature.
- General common-library TODOs (offline counters, default responses, ZDO counters, library version
  reporting) affect many drivers and belong in the shared library backlog, not this device TODO.
- Deprecated mmWave profiles and mmWave-only devices → separate **Tuya Zigbee mmWave Sensor**
  driver/thread.
- The request to suppress illuminance events ([#506](https://community.hubitat.com/t/-/92441/506)-
  [#510](https://community.hubitat.com/t/-/92441/510)) concerned a now-deprecated mmWave profile;
  retain it in the dedicated mmWave backlog if it is still relevant.
- The health-check report in [#809](https://community.hubitat.com/t/-/92441/809)-
  [#813](https://community.hubitat.com/t/-/92441/813) also concerned the old mmWave support and the
  shared `commonLib`; re-test it in the dedicated driver before opening a PIR-driver item.
- Battery voltage requested in [#397](https://community.hubitat.com/t/-/92441/397) was added in
  v1.3.0; the reporting-interval request in [#401](https://community.hubitat.com/t/-/92441/401)
  was added in v1.3.2 (its current broken command path is BUGS B4); human-readable `motionStarted`
  from [#411](https://community.hubitat.com/t/-/92441/411) was added in v1.6.0.
- Aqara FP2 is Wi-Fi/HomeKit, not Zigbee and not supportable by this driver.
- False motion caused by rechargeable 16340 cells was resolved as a power-supply/device issue
  ([#11](https://community.hubitat.com/t/-/92441/11)–
  [#23](https://community.hubitat.com/t/-/92441/23)).
- The 2025 delayed-battery exception was fixed in v3.5.2
  ([#1128](https://community.hubitat.com/t/-/92441/1128)–
  [#1130](https://community.hubitat.com/t/-/92441/1130)).
