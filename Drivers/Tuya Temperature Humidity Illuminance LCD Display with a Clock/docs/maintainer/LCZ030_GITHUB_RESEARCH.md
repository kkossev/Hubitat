# LCZ030 / `_TZ3000_qaaysllp` research and driver audit

> Analysis document only — no driver code has been changed. This review was written on 2026-08-04 against `Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy` version 2.1.2 and Zigbee2MQTT/zigbee-herdsman-converters commit `9d8862b8698f998b15b238e038708f086c64189d` (release 26.91.0).

## Scope

This document covers the Neo/Tuya LCD temperature, humidity and illuminance sensor identified by:

- Zigbee model: `TS0201`
- Zigbee manufacturer: `_TZ3000_qaaysllp`
- Zigbee2MQTT model: `LCZ030`
- Common product aliases: Neo NAS-TH02B, NAS-TH01 and LCZ030
- Hubitat driver model group: `TS0201_LCZ030`

It consolidates:

- Hubitat community post 696 and its three screenshots;
- the current Hubitat driver implementation;
- current and historical Zigbee2MQTT code, issues, pull requests and discussions;
- ZHA, Homey, SmartThings and Domoticz implementations, plus exact-device SmartThings Community feedback;
- all evidence found about temperature/humidity report intervals, deltas and sensitivity.

## Executive conclusion

There is no confirmed way to configure temperature or humidity reporting sensitivity on the exact `_TZ3000_qaaysllp` LCZ030.

The strongest available evidence shows that standard ZCL Configure Reporting requests for temperature, humidity, illuminance and battery fail or are ignored. The device appears to apply a firmware-controlled temperature threshold of approximately 1.0–1.1 °C. Humidity usually updates more often. Zigbee temperature reports correlate with changes on the physical LCD, which points to device firmware rather than Hubitat or Zigbee2MQTT filtering.

One SmartThings tester reported quick status changes after a community driver explicitly configured hidden endpoint 2. That report confirms functional endpoint-2 integration, but it included no Configure Reporting response, readback, selected delta, reporting period or long-term event history. It therefore does not establish that the firmware applies configurable reporting values.

The current Hubitat driver correctly sends the Tuya Basic-cluster activation sequence, but has three LCZ030-specific problems:

1. `updated()` sends EF00 datapoints to a device that does not advertise or use EF00 for these settings.
2. The driver does not parse or write the LCZ030 alarm attributes on cluster `0xE002`.
3. `refresh()` reads temperature and humidity using the default endpoint 1, while the device sends those measurements from an unadvertised endpoint 2.

The driver's minimum-reporting-time preferences are only Hubitat-side event throttles for this model. They cannot make the physical device send more reports.

## Device topology and activation

The device advertises only endpoint 1 during its interview:

| Endpoint | Input clusters | Output clusters |
|---|---|---|
| 1 | Basic `0x0000`, Power Configuration `0x0001`, Illuminance `0x0400`, Tuya alarm cluster `0xE002` | OTA `0x0019`, Time `0x000A` |

Temperature and humidity are subsequently emitted from an unadvertised endpoint 2:

| Endpoint | Cluster | Attribute | Data type and scale |
|---|---|---|---|
| 2 | Temperature Measurement `0x0402` | MeasuredValue `0x0000` | signed INT16, hundredths of °C |
| 2 | Relative Humidity `0x0405` | MeasuredValue `0x0000` | UINT16, hundredths of %RH |

The device must first receive the Tuya activation or “magic packet”: a Basic-cluster read containing attributes `0x0004`, `0x0000`, `0x0001`, `0x0005`, `0x0007` and `0xFFFE`. Historical reports said a Tuya gateway was required, but Zigbee2MQTT demonstrated that the Basic-cluster read reproduces the activation without a Tuya gateway.

Sources:

- [Zigbee2MQTT issue #9054 — activation investigation](https://github.com/Koenkk/zigbee2mqtt/issues/9054#issuecomment-1007877123)
- [Zigbee2MQTT PR #3644 — accepted activation and binding fix](https://github.com/Koenkk/zigbee-herdsman-converters/pull/3644)
- [ZHA issue #862 — discovery of hidden endpoint 2](https://github.com/zigpy/zha-device-handlers/issues/862#issuecomment-862521087)
- [ZHA issue #862 — confirmed manual Basic-attribute activation](https://github.com/zigpy/zha-device-handlers/issues/862#issuecomment-1424948456)

## Comparison of home-automation implementations

### Zigbee2MQTT — Confirmed

The current converter:

1. calls `tuya.configureMagicPacket()`;
2. binds Basic, Power Configuration, Temperature Measurement, Relative Humidity, Illuminance Measurement and `manuSpecificTuya2`;
3. parses standard temperature and humidity reports;
4. parses and writes the alarm settings on `0xE002`;
5. exposes no reporting interval, reportable-change or sensitivity control.

Binding establishes the destination for reports. It does not set minimum interval, maximum interval or reportable change.

Source: [current LCZ030 converter](https://github.com/Koenkk/zigbee-herdsman-converters/blob/9d8862b8698f998b15b238e038708f086c64189d/src/devices/tuya.ts#L14061-L14092)

### ZHA / zha-device-handlers — Confirmed structure, no sensitivity control

The upstream ZHA quirk creates a virtual endpoint 2 containing standard temperature and humidity clusters. It defines the manufacturer-specific alarm cluster but does not configure temperature/humidity reporting intervals or deltas.

The upstream quirk currently defines maximum humidity as `0xD00C`. Device reports, Zigbee2MQTT and Domoticz use `0xD00D`; `0xD00D` is therefore the better-supported value.

Source: [ZHA TS0201 quirk](https://github.com/zigpy/zha-device-handlers/blob/dev/zhaquirks/tuya/ts0201.py)

### Homey — Confirmed code, no temperature/humidity reporting configuration

The Homey driver binds endpoint 2 and registers listeners for temperature and humidity. Its only explicit Configure Reporting request is for battery percentage. It has no temperature or humidity delta preference.

An issue from multiple Homey users records `UNSUP_GENERAL_COMMAND` during setup and missing endpoint 2 after re-pairing. This is additional evidence that the device does not behave like a normal fully-described ZCL sensor.

Sources:

- [Homey LCZ030 driver](https://github.com/JohanBendz/com.tuya.zigbee/blob/SDK3/drivers/lcdtemphumidluxsensor/device.js)
- [Homey issue #240](https://github.com/JohanBendz/com.tuya.zigbee/issues/240)

### SmartThings community driver — Integration reported working; reporting controls unverified

One SmartThings Edge community driver special-cases `_TZ3000_qaaysllp` and sends endpoint-2 Configure Reporting commands. It offers:

- temperature maximum interval: 5–240 minutes;
- temperature reportable change: 0.1–6 °C;
- humidity maximum interval: 5–240 minutes;
- humidity reportable change: 1–10 %RH.

However, the code does not validate the Configure Reporting response or read the configuration back. SmartThings Community feedback confirms that the revised driver could make an exact LCZ030 report measurements after explicit endpoint-2 configuration, but no post or device log confirms that LCZ030 accepts or applies the selected delta and maximum-interval values. This is evidence that the standard ZCL approach was attempted, not evidence that its reporting controls work.

Sources:

- [SmartThings endpoint-2 configuration commands](https://github.com/Mariano-Github/Edge-Drivers-Beta/blob/bdf24246fd24563e6e35ea2cb8b2f14f9c8584ed/zigbee-temp-humidity-child-thermostat-edge/zigbee-driver/src/init.lua#L101-L203)
- [SmartThings reporting preferences](https://github.com/Mariano-Github/Edge-Drivers-Beta/blob/bdf24246fd24563e6e35ea2cb8b2f14f9c8584ed/zigbee-temp-humidity-child-thermostat-edge/zigbee-driver/profiles/temp-humidity-illumin-battery.yml#L87-L122)

#### SmartThings Edge code audit — Confirmed transmission; acceptance unverified

The current implementation was inspected at repository commit `bdf24246fd24563e6e35ea2cb8b2f14f9c8584ed`. It is not a dedicated LCZ030 subdriver. The exact fingerprint is embedded in the generic `Zigbee Temp Sensor and Child Thermostat Mc` package under `zigbee-temp-humidity-child-thermostat-edge/zigbee-driver` and selects the `temp-humid-illumin-battery` profile.

Sources: [driver directory](https://github.com/Mariano-Github/Edge-Drivers-Beta/tree/bdf24246fd24563e6e35ea2cb8b2f14f9c8584ed/zigbee-temp-humidity-child-thermostat-edge/zigbee-driver), [exact fingerprint](https://github.com/Mariano-Github/Edge-Drivers-Beta/blob/bdf24246fd24563e6e35ea2cb8b2f14f9c8584ed/zigbee-temp-humidity-child-thermostat-edge/zigbee-driver/fingerprints.yml#L183-L187).

During initialization, the driver sends a Basic-cluster read for attributes `0x0004`, `0x0000`, `0x0001`, `0x0005`, `0x0007` and `0xFFFE`. This is the same activation pattern called “Tuya black magic” in the Hubitat code and closely matches Zigbee2MQTT's accepted activation fix. Source: [SmartThings initialization](https://github.com/Mariano-Github/Edge-Drivers-Beta/blob/bdf24246fd24563e6e35ea2cb8b2f14f9c8584ed/zigbee-temp-humidity-child-thermostat-edge/zigbee-driver/src/init.lua#L781-L790).

For `_TZ3000_qaaysllp`, `do_configure()` explicitly binds and configures the hidden endpoint-2 temperature and humidity clusters:

| Measurement | Endpoint | Minimum interval | Maximum interval | Reportable change |
|---|---:|---:|---:|---:|
| Temperature | 2 | fixed 30 s | preference × 60; default 5 min | preference × 100; default 0.1 °C → raw 10 |
| Humidity | 2 | fixed 60 s | preference × 60; default 10 min | preference × 100; default 1% RH → raw 100 |

The same generic configuration pass also attempts endpoint-1 illuminance reporting and standard battery reporting. Generic attribute handlers parse incoming temperature, humidity and illuminance reports regardless of the source endpoint.

The audit found these limitations and defects:

1. **No success validation.** The driver does not handle or store the Configure Reporting response, issue Read Reporting Configuration, or compare returned values with the requested values. Its registered Zigbee handlers cover measurement attributes, not reporting-configuration responses.
2. **Provisioning is declared unconditionally.** `do_configure()` marks the device `PROVISIONED` immediately after transmitting the commands, without waiting for an acknowledgement or readback. Source: [unconditional provisioning transition](https://github.com/Mariano-Github/Edge-Drivers-Beta/blob/bdf24246fd24563e6e35ea2cb8b2f14f9c8584ed/zigbee-temp-humidity-child-thermostat-edge/zigbee-driver/src/init.lua#L189-L203).
3. **The successful forum test does not isolate Configure Reporting.** The activation read and endpoint-2 bindings are sufficient to enable the device's native firmware reports. The tester's success can therefore be explained even if the following Configure Reporting commands failed or were ignored.
4. **Preference writes are not wake-safe.** Changed settings are transmitted immediately. There is no driver-level sleepy-device queue, retry on the next packet, acknowledgement check or readback.
5. **Only one changed preference category is processed per save.** `do_preferences()` iterates preferences using unordered `pairs()` and executes `break` after handling the first recognized category. Changing temperature and humidity settings in the same save sends only whichever category is encountered first. Changing both settings within one category is covered because that command uses both current values. Source: [SmartThings preference handler](https://github.com/Mariano-Github/Edge-Drivers-Beta/blob/bdf24246fd24563e6e35ea2cb8b2f14f9c8584ed/zigbee-temp-humidity-child-thermostat-edge/zigbee-driver/src/init.lua#L206-L255).
6. **“Report Interval” means ZCL maximum interval.** It is not a guaranteed sampling or periodic-reporting interval; the label can overstate what the command requests even on a compliant device.
7. **No fallback polling or alarm support.** There is no LCZ030-specific endpoint-2 read/poll path and no parser or writer for the device's `0xE002` alarm attributes.

The SmartThings code therefore confirms the correct activation sequence, hidden endpoint and binding destination. It does not prove that the LCZ030 firmware accepts smaller deltas or shorter maximum intervals.

Another SmartThings implementation labels its LCZ030 profile `pending`, sends the Tuya magic packet and uses generic standard clusters. That repository also contains no confirmation that configurable deltas work.

Source: [pending SmartThings LCZ030 definition](https://github.com/wonjj6768/smartthings-zigbee-edge-drivers/blob/main/source/src/devices/zcl/sensors.lua#L200-L207)

#### Exact-device SmartThings Community feedback — Reported

The SmartThings forum contains three discussion sequences for the exact `(TS0201, _TZ3000_qaaysllp)` fingerprint:

- In 2022, a fingerprint-only driver exposed illuminance but temperature and humidity did not update. The device appeared with an error indicator and “Unable to know the status of the device.” The driver author then stated that the available driver could not handle the non-standard measurement path. Sources: [initial request and endpoint-1 fingerprint](https://community.smartthings.com/t/post-requests-for-zigbee-edge-drivers-here-community-created/262081/694), [failed temperature/humidity test](https://community.smartthings.com/t/post-requests-for-zigbee-edge-drivers-here-community-created/262081/699), [driver-author response](https://community.smartthings.com/t/post-requests-for-zigbee-edge-drivers-here-community-created/262081/700).
- In October 2023, another user supplied the same endpoint-1 clusters: `0000,0001,0400,E002`. The driver author described the device as non-standard because the advertised descriptor lacks temperature and humidity clusters and initially recommended replacing it for direct SmartThings use. Sources: [repeated exact-device request](https://community.smartthings.com/t/edge-driver-mc-zigbee-switch-mc-zigbee-switch-power-mc-zigbee-multi-switch-and-child-mc/236953/1207), [initial assessment](https://community.smartthings.com/t/edge-driver-mc-zigbee-switch-mc-zigbee-switch-power-mc-zigbee-multi-switch-and-child-mc/236953/1208).
- In December 2023, the driver author identified the hidden endpoint 2 and produced a driver version that configured its temperature and humidity clusters during pairing. The instructions required installing the updated driver first, deleting the device and then pairing it again rather than merely switching drivers. Sources: [endpoint-2 diagnosis](https://community.smartthings.com/t/edge-driver-mc-zigbee-drivers-for-motion-open-close-moisture-smoke-co-sensors-and-others-devices/231689/4148), [endpoint-2 test driver and re-pair instructions](https://community.smartthings.com/t/edge-driver-mc-zigbee-drivers-for-motion-open-close-moisture-smoke-co-sensors-and-others-devices/231689/4149).

The tester then reported working temperature, humidity, illuminance and battery values and said that status changes appeared to update quickly despite the device's reputation for infrequent reports. The attached screenshot was downloaded and inspected: SmartThings showed `62.1 °F`, `41.0%`, `54 lx` and `80%` battery, while the physical LCD showed approximately `62 °F`, `41%` and `54 lx`. The tester also reported that rechargeable AA batteries made the LCD battery icon inaccurate, while the SmartThings battery value better matched expectations. Source: [positive report and inspected screenshot](https://community.smartthings.com/t/edge-driver-mc-zigbee-drivers-for-motion-open-close-moisture-smoke-co-sensors-and-others-devices/231689/4150).

This evidence is **Reported**, not **Confirmed**: it comes from one short-term tester and one measurement snapshot. There is no raw Zigbee event history, Configure Reporting response, Read Reporting Configuration result, selected delta/interval or comparison proving that the configured reporting preferences changed device behavior. It strengthens the evidence for endpoint-2 setup during pairing but leaves configurable sensitivity and reporting periods **Implemented unverified**.

### Domoticz / Zigbee4Domoticz — Confirmed polling workaround

The certified device definition deliberately has an empty `ConfigureReporting` object. It enables polling and specifies:

- temperature polling every 300 seconds;
- humidity polling every 300 seconds;
- battery polling every 3600 seconds.

This is the only concrete implementation found that attempts to compensate for sparse LCZ030 reporting. It polls measurements rather than changing the device's reportable-change threshold.

Source: [Zigbee4Domoticz certified LCZ030 definition](https://github.com/zigbeefordomoticz/z4d-certified-devices/blob/main/z4d_certified_devices/Certified/Tuya/TS0201-_TZ3000_qaaysllp.json)

## GitHub issues and discussions about reporting frequency

### Zigbee2MQTT issue #9054 — strongest evidence

The original support investigation contains several independent observations:

- A user measured temperature reports only after approximately ±1.1 °C changes and could not program a smaller interval or delta.
- Another user observed temperature updates in roughly 1 °C steps while humidity updated more frequently.
- Temperature changes sent over Zigbee correlated with changes on the LCD, suggesting the threshold is in device firmware.
- A contributor explicitly tried `reporting.temperature()`, `reporting.humidity()`, illuminance reporting and battery reporting; the report-configuration calls “seem to fail.”
- The accepted fix was the Basic-cluster activation read plus binding, not Configure Reporting.

Sources:

- [approximately 1.1 °C steps and failed configuration attempts](https://github.com/Koenkk/zigbee2mqtt/issues/9054#issuecomment-944504811)
- [approximately 1 °C temperature steps; humidity more frequent](https://github.com/Koenkk/zigbee2mqtt/issues/9054#issuecomment-948344408)
- [temperature report follows LCD change](https://github.com/Koenkk/zigbee2mqtt/issues/9054#issuecomment-948344999)
- [Configure Reporting experiment](https://github.com/Koenkk/zigbee2mqtt/issues/9054#issuecomment-1007877123)

### Zigbee2MQTT issue #12423 — unresolved polling request

The reporter described rapid large changes being sent immediately, slow changes drifting by more than 1 °C, and a short reset-button press causing a fresh update. They requested periodic reads as a workaround. The issue received no technical response and was closed as stale.

Source: [LCZ030 updates very irregularly](https://github.com/Koenkk/zigbee2mqtt/issues/12423)

### ZHA issue #862 — similar behavior on another stack

A SmartThings user in the ZHA investigation reported humidity roughly every three hours and no changed temperature event for seven hours. This does not prove an exact threshold, but it shows that sparse temperature reports are not specific to Zigbee2MQTT or Hubitat.

Source: [SmartThings reporting observation](https://github.com/zigpy/zha-device-handlers/issues/862#issuecomment-1245942909)

### False-positive search results: other Tuya LCD sensors

GitHub searches return TS0601 sensors such as `_TZE200_vvmbj46n` and `_TZE200_locansqn` that expose Tuya datapoints named `temperature_report_interval`, `humidity_report_interval`, `temperature_sensitivity` and `humidity_sensitivity`.

Those are different EF00/MCU devices. Their datapoints are model-dependent and must not be copied to the TS0201 LCZ030. The current Hubitat driver's incorrect EF00 writes for LCZ030 are an example of this exact cross-model mistake.

Source: [example discussion for a different TS0601 device](https://github.com/Koenkk/zigbee2mqtt/discussions/18827)

## Forum screenshot analysis

The three screenshots attached to Hubitat community post 696 were downloaded and inspected.

- The temperature graph shows rapid reports during a large environmental change at approximately 09:33–09:41.
- Later reports are sparse, at approximately 10:36, 10:53, 11:44, 12:36 and 13:37.
- The visible changes are roughly 1 °C / 1.8–2 °F steps, with near-hourly reports in the quieter period.
- This pattern matches the GitHub reports: large changes are emitted quickly, while gradual changes remain flat until the firmware threshold or a periodic heartbeat is reached.
- The third screenshot shows the Hubitat driver-type selector. It does not show a device-side reporting sensitivity setting.

The screenshots alone cannot prove whether every hourly point was an unchanged heartbeat, a rounded value change or a manual refresh. Raw Zigbee logs are required for that distinction.

## Hubitat driver audit

Target file: `Tuya_Temperature_Humidity_Illuminance_LCD_Display_with_a_Clock.groovy`, version 2.1.2.

### Correct: fingerprint and model-family routing

The metadata fingerprint correctly describes endpoint 1 with clusters `0000,0001,0400,E002` and maps `_TZ3000_qaaysllp` to `TS0201_LCZ030`.

Locations:

- metadata fingerprint containing `_TZ3000_qaaysllp`;
- `deviceModelNames` entry mapping `_TZ3000_qaaysllp` to `TS0201_LCZ030`.

### Correct: Tuya activation sequence

`tuyaBlackMagic()` reads Basic attributes including `0xFFFE`. This matches the operation that activates temperature and humidity reporting in Zigbee2MQTT and in the successful ZHA user tests.

The additional write of Basic attribute `0xFFDE` value `0x13` is the repository's established Tuya pairing sequence and should remain.

Location: `tuyaBlackMagic()`.

### B1 — Confirmed wrong transport for LCZ030 settings

**Location:** `updated()`, model-group branches containing `TS0201_LCZ030`.

**Problem:** The driver sends:

- EF00 DP `0x09` for the display unit;
- EF00 DP `0x0A` for maximum temperature alarm;
- EF00 DP `0x0B` for minimum temperature alarm.

LCZ030 does not advertise EF00. Its alarm settings use ZCL attributes on cluster `0xE002`.

**Additional scaling problem:** The driver multiplies temperature alarm values by ten before sending them. LCZ030 E002 alarm attributes use whole °C values and signed INT16 (`0x29`), so 39 °C should be written as integer 39, not 390.

**Expected fix:** Remove LCZ030 from these EF00 branches. If alarm writes are supported, implement them through `0xE002` with the correct attribute IDs and types.

**Confidence:** High — confirmed by the fingerprint, Zigbee2MQTT code, PR #3649 and captured E002 reports.

### B2 — Missing E002 alarm parsing and writing

**Problem:** The driver recognizes the E002 cluster in the fingerprint but contains no LCZ030 parser for its alarm configuration or status attributes.

Supported attribute map:

| Attribute | Meaning | Type/value |
|---|---|---|
| `0xD00A` | maximum temperature alarm | INT16, whole °C |
| `0xD00B` | minimum temperature alarm | INT16, whole °C |
| `0xD00D` | maximum humidity alarm | INT16, whole %RH |
| `0xD00E` | minimum humidity alarm | INT16, whole %RH |
| `0xD006` | temperature alarm status | enum: 0 below minimum, 1 above maximum, 2 off |
| `0xD00F` | humidity alarm status | enum: 0 below minimum, 1 above maximum, 2 off |
| `0xD010` | unknown | UINT8; observed value 0 |

Do not use ZHA's current `0xD00C` value for maximum humidity without device verification; exact-device logs and the other implementations use `0xD00D`.

**Confidence:** High for the map; actual usefulness of alarm writes should still be verified on the physical device.

### B3 — Refresh reads the wrong endpoint

**Location:** `refresh()`.

**Problem:** `zigbee.readAttribute(0x0402, ...)` and `zigbee.readAttribute(0x0405, ...)` use the device's default endpoint, endpoint 1. LCZ030 sends these clusters from hidden endpoint 2.

**Likely result:** Refresh does not obtain new LCZ030 temperature/humidity values.

**Candidate approach:** Use explicit endpoint-2 raw reads, matching the existing `pollTS0222()` pattern:

```text
he rattr 0xNNNN 0x02 0x0402 0x0000 {}
he rattr 0xNNNN 0x02 0x0405 0x0000 {}
```

**VERIFY ON DEVICE:** Some evidence says direct reads can return unsupported-attribute status, while Domoticz relies on polling. Test the response before scheduling polling or changing `refresh()`.

### C1 — Minimum reporting time is locally enforced and potentially misleading

**Locations:** `configParams`, `temperatureEvent()` and `humidityEvent()`.

The preferences named “Minimum time between temperature reports” and “Minimum time between humidity reports” are displayed for all model groups. For LCZ030 they are not sent to the device. They only delay or coalesce Hubitat events after a packet has arrived.

This cannot improve a sparse LCZ030 graph. Increasing the values can only suppress or delay packets that were already received.

The temperature/humidity sensitivity preferences correctly exclude `TS0201_LCZ030`; the available evidence supports keeping them excluded.

## Device-verification plan

No driver change should claim support for configurable sensitivity without these hardware checks.

### 1. Confirm activation

1. Remove power long enough to clear the device's volatile state.
2. Pair or re-pair without deleting the Hubitat device.
3. Capture the Basic-cluster reads sent by `tuyaBlackMagic()`.
4. Confirm that reports subsequently arrive from endpoint 2, clusters `0x0402` and `0x0405`.

**Pass:** Endpoint-2 temperature and humidity reports begin after the Basic read containing `0xFFFE`.

### 2. Test endpoint-2 polling

While the sleepy device is awake, send explicit endpoint-2 reads for `0x0402/0x0000` and `0x0405/0x0000`.

**Pass:** A Read Attributes Response returns the current measurement.

**Fail:** Status `0x86` (unsupported attribute), `0x82` (unsupported general command), timeout or no response.

If the test passes reliably, a five-minute polling option modeled on Domoticz is technically justified. It should remain optional because frequent polling costs battery power.

### 3. Test standard Configure Reporting only as an experiment

Send Configure Reporting to endpoint 2 with a clearly distinguishable configuration, for example:

- temperature: minimum 30 s, maximum 900 s, reportable change 25 (0.25 °C);
- humidity: minimum 60 s, maximum 900 s, reportable change 100 (1 %RH).

Then issue Read Reporting Configuration and record the exact status and returned values.

**Pass:** Status success, readback matches and physical reports follow the configured delta/interval.

**Fail:** Error status, no readback, or reports continue at the firmware's approximately 1 °C threshold.

Do not infer success merely because the command was transmitted.

### 4. Test E002 alarm writes

Read all known E002 attributes, change one alarm threshold by one whole unit, read it back and observe both the LCD and alarm status.

**Pass:** Readback and LCD/alarm behavior match the written value.

## Recommended implementation order

1. Remove LCZ030 from the incorrect EF00 DP `09`, `0A` and `0B` branches.
2. Verify endpoint-2 measurement reads on the physical device.
3. If reads work, correct `refresh()` for LCZ030; consider optional five-minute polling only if requested.
4. Add E002 alarm parsing.
5. Add E002 alarm writes only after readback testing.
6. Do not expose device-side sensitivity or reporting-interval preferences unless Configure Reporting succeeds and readback proves that the firmware applies them.

Each change should be made and hub-tested separately. No version or timestamp bump is required until the maintainer declares a release point.

## Final status

- Device identification: **Confirmed**
- Hidden endpoint 2 for temperature/humidity: **Confirmed**
- Basic `0xFFFE` activation: **Confirmed**
- Firmware-controlled approximately 1 °C temperature step: **Reported by multiple users; consistent with forum screenshots**
- Configurable LCZ030 temperature/humidity delta: **Unsupported or ignored in available exact-device tests**
- SmartThings endpoint-2 integration: **Reported working by one exact-device tester**
- Standard Configure Reporting in the SmartThings community driver: **Implemented unverified; no response/readback or selected-setting proof, and provisioning is marked complete after transmission only**
- SmartThings multi-category preference update: **Confirmed code bug; only one changed measurement category is sent per save**
- Five-minute endpoint-2 polling: **Implemented by Domoticz; verify on Hubitat hardware**
- Hubitat EF00 configuration for LCZ030: **Confirmed bug**
- Hubitat E002 support: **Missing**
- Hubitat endpoint-2 refresh: **Likely bug; verify on device**
