# Tuya Ultrasonic Water Flow Meter — Consolidated TODO and Work-list

Analysis date: 2026-08-05  
Topic: https://community.hubitat.com/t/tuya-smart-zigbee-ultrasonic-water-meters/142433  
Forum coverage: complete thread, posts 1–25 (the whole thread as of 2026-08-05)

This is the single consolidated backlog for this driver. Part I is the forum-derived device-support
backlog, Part II the open technical questions and unverified decoding, Part III repo housekeeping.
Resolved items are removed from this file — their history lives in `CHANGELOG.md`.

**Workflow rules**

- One item at a time. State the item number before editing, and wait for the go-ahead.
- Mark an item done only after the user confirms a hub test, then delete it from this file.
- No version bumps or changelog history lines unless explicitly asked.
- **The maintainer owns no hardware from this device family.** Everything in this driver is derived
  from raw datapoint captures posted on the forum and from the Zigbee2MQTT converters. Any item
  below that says "confirm on device" genuinely cannot be closed without a user's hub.

Status legend:

- `[ ]` open · `[?]` needs verification or a user decision first
- `OPEN` — requested/reported, not implemented
- `NEEDS_EVIDENCE` — needs a fingerprint, raw logs or hardware confirmation
- `INFO` — no code change expected; capture it in the README instead
- `DEVICE_LIMITATION` — cannot be implemented given known device/firmware behavior

---

## Part I — Forum-derived backlog

### 1. `[?]` `OPEN` — `_TZE284_ajlu4cud` support — fix applied in 3.4.0, pending hub confirmation

**Requested outcome:** jw970065 could not connect the meter to the Tuya app and paired it to Hubitat
instead; it matched no water-meter driver, so Hubitat gave it the *Tuya Smart Siren Zigbee* driver.

Evidence:

- Purchase note, logs promised: https://community.hubitat.com/t/-/142433/24
- Device data + full debug log: https://community.hubitat.com/t/-/142433/25

Fix applied (3.4.0): new `TS0601_WATER_METER` profile (no valve), fingerprint
`_TZE284_ajlu4cud` / `TS0601` with `inClusters:'0004,0005,EF00,0000,ED00'`, and payload-level
decoding for dp 1/2/3/5/16/18/21. Decoding verified offline against the exact frames in post #25.

Verification needed from jw970065:

- Zero `NOT PROCESSED` warnings for dp 1, 2, 3, 4, 5, 6, 16, 18, 21, 22, 26.
- No `NumberFormatException` on the dp16 meter-ID frame — that is the one that crashed the siren
  driver, and it is the single most important regression check.
- `meterId` = `00000026009162`, `temperature` ≈ 32.8 °C, `batteryVoltage` = 3.42 V, `battery` ≈ 77 %,
  `reportPeriod` = `12h`.
- `faults` should read `empty_pipe_alarm,transducer_alarm` while the meter is out of a pipe, and
  should clear to `no_alarm` once installed and filled — **this second half is unconfirmed**, see
  item 7.

### 2. `[ ]` `OPEN` — Report period is a preference here, but users are looking for a command

ilkeraktuna asked how to set the report interval to 1 h, and specifically whether the device button
could do it (https://community.hubitat.com/t/-/142433/21). ed.net answered that his driver exposes it
in the **Commands** tab (https://community.hubitat.com/t/-/142433/22), and that the eight intervals
are fixed in firmware, not chosen by the driver (https://community.hubitat.com/t/-/142433/20).

This driver exposes `reportPeriod` as a **preference** (dp4, `rw`), written by
`updateAllPreferences()` on Save Preferences. Functionally equivalent, but users arriving from
ed.net's driver will look in the Commands tab and not find it.

Decision needed: add a `setReportPeriod` command for parity, or leave it as a preference and cover it
in the README. Preference-only is the V3-architecture-consistent choice; a command is the
discoverable one.

### 3. `[ ]` `NEEDS_EVIDENCE` — `_TZE200_zlwr0raf` (Tuya 213E) has never been confirmed

Present in the fingerprint list since the 2024 stub, sourced only from an AliExpress listing
(https://www.aliexpress.com/item/1005007308058989.html). No datapoint capture, no user report, no
Zigbee2MQTT or ZHA definition. It currently sits in `TS0601_WATER_METER_VALVE` on the assumption that
it behaves like the 214C — which may be wrong in either direction (it may have no valve, or a
different datapoint set entirely).

Needs a device-data screenshot plus a debug log from an owner before it can be trusted. Until then
the assumption is documented in a comment next to the fingerprint.

### 4. `[ ]` `INFO` — Set user expectations: this is a water meter, not a flow meter

ed.net's explanation (https://community.hubitat.com/t/-/142433/20) is the single most useful thing in
the thread for new owners: the device is a battery-powered consumption meter designed to sleep as
much as possible, the report period is fixed in firmware at 1/2/3/4/6/8/12/24 h, and it will **not**
tell you that water is flowing right now. ilkeraktuna ended up adding a vibration sensor on the pipe
for that (https://community.hubitat.com/t/-/142433/21).

`instantaneousFlowRate` and `rate` exist and are decoded, but they only update on the meter's own
report schedule — they are not a leak-detection mechanism. Say so in the README when it is written
(item 12), so nobody buys one expecting sub-minute flow detection.

### 5. `[ ]` `INFO` `DEVICE_LIMITATION` — Battery replacement, not recharging

ilkeraktuna asked how the internal lithium cell is recharged
(https://community.hubitat.com/t/-/142433/14); ed.net had not opened one and planned to simply
replace it (https://community.hubitat.com/t/-/142433/18). These meters use a non-rechargeable 3.6 V
ER14505 lithium cell, which is also the basis for this driver's 2.5–3.7 V battery-percentage range.
Nothing to implement — worth one line in the README.

---

## Part II — Open technical questions and unverified decoding

### 6. `[ ]` `NEEDS_EVIDENCE` — Liters vs m³ scaling is not fully settled

dp1 (total consumption) and dp21 (flow rate) read **zero in every capture anyone has posted**, so the
raw evidence cannot confirm the scale factor. The interpretation used here — raw value is liters,
divided by 1000 for m³ — comes from the Zigbee2MQTT `TS0601_water_meter` converter and from ed.net's
report that the meter's own values are in m³.

Note that Zigbee2MQTT is itself inconsistent about this: `TS0601_water_valve` exposes dp1 with
`tuya.valueConverter.raw` and labels it **L**, while `TS0601_water_meter` uses `divideBy1000` and
labels it **m³**. The `volumeUnit` preference covers both readings, but the underlying factor is
still an assumption.

Ask jw970065 (or any owner) to report `waterConsumed` alongside the figure on the meter's LCD once
water has actually run through it.

### 7. `[ ]` `NEEDS_EVIDENCE` — Fault bitmap only ever observed as 0x1800

The one captured dp5 value is `0x1800` = `empty_pipe_alarm` + `transducer_alarm`, which is exactly
what an uninstalled meter on a bench should report — a good sign, but it exercises only 2 of the 13
mapped bits. The bit→name table is copied verbatim from Zigbee2MQTT and is otherwise unverified.
Confirm the value clears to `no_alarm` on an installed, filled meter.

### 8. `[ ]` `OPEN` — The 4-byte frozen-date stamp in dp2 / dp3 is not decoded

Both consumption blobs are 8 bytes: the last 4 are the value (big-endian uint32 liters, decoded), the
first 4 are a date stamp that neither this driver nor Zigbee2MQTT decodes. ed.net described the same
thing as "a proprietary string format that I haven't decoded"
(https://community.hubitat.com/t/-/142433/18).

Observed samples, both from a meter with zero consumption:

| dp | payload | first 4 bytes |
|---|---|---|
| 2 (month) | `15 06 15 06 00 00 00 00` | `15 06 15 06` |
| 3 (daily) | `07 07 07 07 00 00 00 00` | `07 07 07 07` |

`0x15 = 21`, `0x06 = 6` reads plausibly as a day/month pair, but with only one zero-consumption
sample from one device it is a guess. Needs several captures taken on known dates.

### 9. `[ ]` `OPEN` — dp6 (`monthAndDailyFrozenSet`) semantics unknown

Two raw bytes, observed as `01 00`. The name comes from ed.net's datapoint list; the working theory
is "the day of month and the hour at which the monthly/daily totals roll over", which would tie it to
item 8. Currently surfaced as a plain number with no interpretation. Zigbee2MQTT does not map it at
all.

### 10. `[ ]` `OPEN` — dp15 unknown (meters with a valve only)

Listed by ed.net as an undecoded raw blob; not present in the `_TZE284_ajlu4cud` capture and not
mapped by Zigbee2MQTT. Currently declared as `UnknownDp15` with no attribute, so it is logged at
debug level only. Needs a capture from a `_TZE200_vuwtqx0t` owner.

### 11. `[?]` `OPEN` — `rate` unit diverges from the sibling Tuya Zigbee Valve driver

This driver sends `rate` in **LPM**, per the Hubitat `LiquidFlowRate` capability documentation
(dp21 is L/h, divided by 60). The *Tuya Zigbee Valve* driver in this repo sends the same `rate`
attribute in **m³/h** (`Tuya Zigbee Valve.groovy:1708`, from the SONOFF SWV ZCL Flow Measurement
cluster).

Same attribute name, two units, two of the maintainer's own drivers. The native reading is also
published unconverted as `instantaneousFlowRate` (L/h), so switching `rate` to m³/h here is a
one-line change if consistency is preferred over the capability spec. Maintainer decision.

### 12. `[ ]` `OPEN` — Untested write paths

No maintainer hardware exists, so none of the outgoing commands have ever run against a real meter:

- `open()` / `close()` — `sendTuyaCommand('0D', DP_TYPE_BOOL, '01'|'00')`. Confirm the meter echoes
  dp13 back and the `valve` attribute follows.
- `reportPeriod` write — enum DP through `updateAllPreferences()` → `setPar()` → `sendTuyaCommand`.
- `autoClean` write — same path, dp14. Its exact effect is unclear even in Zigbee2MQTT; the 2024 stub
  carried the comment "AUTOCLEAN SW - ?".
- On `_TZE284_ajlu4cud`, confirm `open()`/`close()` log a warning and send nothing (`hasValve()`
  returns false for that profile).

---

## Part III — Repo and publishing housekeeping

### 13. `[ ]` `OPEN` — The driver has never been published

Missing, in rough dependency order:

- **`Tuya_Ultrasonic_Water_Flow_Meter_lib_included.groovy`** — the amalgamated bundle. Without it the
  driver cannot be pasted into the Hubitat Drivers Code editor or installed by anyone. Built by hand
  by the maintainer at publish time; `importUrl` in the driver already points at where it will live.
- **`packageManifest.json`** — HPM manifest; the driver is not in HPM.
- **`README.md`** — supported-models table (both variants, valve vs no-valve), the datapoint
  reference, and the expectation-setting from items 4 and 5.
- The driver is **not listed in the repo root `README.md`** driver catalog.

### 14. `[ ]` `OPEN` — Cross-references to update once this driver ships

- `Drivers/Tuya Zigbee Valve/TODO.md:236` records the 214C ultrasonic meter as *out of scope* for the
  valve driver and refers users to a separate community driver. Update it to point here.
- kkossev has been pointing thread users at ed.net's community driver
  (https://community.hubitat.com/t/-/142433/16). Worth a courtesy note to ed.net in the thread — his
  post #12 driver is where the dp13/dp14/dp16 datapoint names and the 2.5–3.7 V battery range in this
  driver came from.

### 15. `[ ]` `OPEN` — Lint has not been run

`npm-groovy-lint` is not installed in the current environment (`npx` refused to fetch
`npm-groovy-lint@18.0.0`). The file passes Groovy 4 parse + semantic analysis, but the repo-wide lint
pass still has to be run before release.
