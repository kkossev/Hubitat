# Tuya Ultrasonic Water Flow Meter — Driver Guide for AI Agents

This folder contains a **Hubitat Elevation (HE) Zigbee driver** (Groovy) for Tuya TS0601 ultrasonic
water meters — the battery-powered, sleeping consumption meters sold as the "214C"/"213E" families,
in both a meter-with-valve and a meter-only variant. Author: Krassimir Kossev (kkossev).
License: Apache 2.0.

Community thread: https://community.hubitat.com/t/tuya-smart-zigbee-ultrasonic-water-meters/142433

## Local sources of truth

- **[TODO.md](TODO.md)** — the single consolidated work-list: forum-derived device-support backlog
  (Part I), open technical questions and unverified decoding (Part II), publishing housekeeping
  (Part III). If you were asked to fix something or add a device, work from that file — do not
  re-derive the findings. Follow the workflow rules at the top of it (one item at a time, no version
  bumps unless told, delete items once the user confirms a hub test).
- **[CHANGELOG.md](CHANGELOG.md)** — what changed and why. `3.4.0` is the **current development
  version**: keep appending to that heading, do not open an `[Unreleased]` section above it, until
  the user explicitly asks for a bump.
- Repo and branch policy: root [AGENTS.md](../../AGENTS.md) §1 — `C:\work\Hubitat` **is** a git clone
  of `kkossev/Hubitat` on the `development` branch, and `development` is the single source of truth.
- Shared-library reference: [Libraries/AGENTS.md](../../Libraries/AGENTS.md).

> **No maintainer hardware.** Nobody on this side owns one of these meters. Every byte-level fact in
> this driver comes from raw datapoint captures posted on the forum
> ([post #25](https://community.hubitat.com/t/-/142433/25)) and from the Zigbee2MQTT converters.
> Do not "clean up" a decoding rule unless you can point at a capture that contradicts it.

---

## 1. Architecture — V3 (library-based)

This **is** a kkossev V3 driver, unlike some of its siblings. `Tuya Ultrasonic Water Flow Meter.groovy`
(483 lines, current version **3.4.0**) is the development file and pulls in the shared libraries:

```groovy
#include kkossev.deviceProfileLib   // C:\work\Hubitat\Libraries\deviceProfileLib.groovy  (3.5.7)
#include kkossev.commonLib          // C:\work\Hubitat\Libraries\commonLib.groovy         (4.0.5)
```

The driver defines **no `parse()`** — commonLib owns the entry point and calls back into the driver
through the `custom*` hooks. Behavior is keyed on the **device profile**, an entry in the
`@Field static final Map deviceProfilesV3` map (line 131) selected by fingerprint at pairing time and
stored in `state.deviceProfile`.

There is **no `*_lib_included.groovy` bundle yet** — see TODO item 13. Until one is built by hand,
the driver can only run via the Hubitat VS Code extension / hub "Libraries code" workflow, and it is
not installable from HPM. Per the maintainer's standing rule, **never generate or edit a
`*_lib_included.groovy` file** — the maintainer builds it at publish time.

## 2. Files in this folder

| File | Role |
|---|---|
| **`Tuya Ultrasonic Water Flow Meter.groovy`** | **The driver — edit this.** v3.4.0. |
| `CHANGELOG.md` | Version history. 3.4.0 is the open development heading. |
| `TODO.md` | Consolidated backlog and workflow rules. |
| `AGENTS.md` | This file. |
| `.hubitat/metadata.json` | Hubitat VS Code extension tooling config. Don't touch. |

Missing and tracked in TODO item 13: `packageManifest.json`, `README.md`, and the amalgamated
`*_lib_included.groovy`.

### Versioning workflow

- `version()` / `timeStamp()` are **methods** (lines 25–26), the V3 convention — not the
  `@Field VERSION` constants used by the legacy monolithic drivers.
- `checkDriverVersion()` (commonLib) re-runs `initializeVars(false)` whenever the *driver's* version
  string changes. Library bumps alone do not trigger it — so an existing paired device will not pick
  up new preferences or attributes until this driver's version changes.
- Do **not** bump the version or add header history lines after individual fixes unless asked.

## 3. Supported device families

Two profiles, because the meter-only variant genuinely lacks the valve datapoints:

| Profile | Manufacturers | Valve? | Datapoints |
|---|---|---|---|
| `TS0601_WATER_METER_VALVE` (line 132) | `_TZE200_vuwtqx0t`, `_TZE284_vuwtqx0t`, `_TZE200_zlwr0raf` | yes | all, incl. 13 / 14 / 15 |
| `TS0601_WATER_METER` (line 158) | `_TZE284_ajlu4cud` | no | 1,2,3,4,5,6,16,18,21,22,26 |

`_TZE200_zlwr0raf` (the "213E") is an **assumption** — it has been in the fingerprint list since the
2024 stub with no capture, no user report and no upstream definition. See TODO item 3.

Note the cluster lists differ: the `_TZE284_` devices report `0004,0005,EF00,0000,ED00`, the older
`_TZE200_vuwtqx0t` entry has no `ED00`. Keep them exact — Hubitat matches fingerprints on this string.

### Adding a device

1. Get the textual fingerprint from the owner's Device Data panel (manufacturer, model, in/out
   clusters) **and** a debug log showing raw `descMap.data` for its datapoints.
2. Add a `fingerprints` entry to whichever profile matches its datapoint set — or a new profile if it
   differs. Fingerprints are injected into `metadata` by the loop at lines 74–81; there are no static
   `fingerprint` lines to edit.
3. Only add datapoints you can see in the capture or in `zigbee-herdsman-converters`. Guessing at a
   **writable** DP is how you brick someone's meter setting.

## 4. Parse pipeline

commonLib `parse()` → `standardAndCustomParseCluster` → cluster 0xEF00 →
`standardParseTuyaCluster()` → per-DP loop → `standardProcessTuyaDP()` →
**`customProcessTuyaDp()`** (this driver, line 182) → `processTuyaDPfromDeviceProfile()`
(deviceProfileLib) → `processFoundItem()` → **`customProcessDeviceProfileEvent()`** (line 304).

`customProcessTuyaDp()` runs `processWaterMeterDP()` (line 206) **first**, and only falls through to
the profile engine for the datapoints it doesn't claim. It always returns `true`, so commonLib never
runs its own fallback.

### Two commonLib facts that shape this driver

1. **`standardProcessTuyaDP` is called without `dp_len` and without the chunk offset**
   ([commonLib.groovy:809](../../Libraries/commonLib.groovy#L809)), so `customProcessTuyaDp()` always
   receives `dp_len = 0`. A driver that needs raw payload bytes must re-walk `descMap.data` itself.
   That is what **`getTuyaDpPayload()`** (line 276) does — it mirrors the library's own loop
   (`i = i + len + 4`), so it works for multi-datapoint frames, and returns `null` on a truncated or
   missing chunk rather than throwing.
2. **`getTuyaAttributeValue()` cannot be trusted past 4 bytes**
   ([commonLib.groovy:844](../../Libraries/commonLib.groovy#L844)). It accumulates big-endian over
   the whole payload into an `int`; from the 5th byte the multiplier `256^4` overflows to zero. For
   the 8-byte dp2/dp3 blobs that coincidentally yields the last 4 bytes — the correct value, by
   accident — and for the 14-byte dp16 string it yields garbage. **Never rely on `fncmd` for a
   payload longer than 4 bytes here.**

## 5. Tuya DP map

Decoded from the raw frames in [post #25](https://community.hubitat.com/t/-/142433/25) and
cross-checked against `zigbee-herdsman-converters/src/devices/tuya.ts` (`TS0601_water_meter` and
`TS0601_water_valve`).

| dp | Tuya dtype | Attribute | Decode | Handled by |
|---|---|---|---|---|
| 1 | 0x02 value | `waterConsumed` | uint32 BE, liters → `volumeUnit` | driver |
| 2 | 0x00 raw, 8 B | `monthConsumption` | **last 4 bytes** uint32 BE; first 4 = undecoded date stamp | driver |
| 3 | 0x00 raw, 8 B | `dailyConsumption` | same as dp2 | driver |
| 4 | 0x04 enum | `reportPeriod` | `0..7` → 1h/2h/3h/4h/6h/8h/12h/24h — **writable** | profile |
| 5 | 0x05 bitmap, 2 B | `faults` | bit→name list, `no_alarm` when 0 | driver |
| 6 | 0x00 raw, 2 B | `monthAndDailyFrozenSet` | raw; semantics unconfirmed | profile |
| 13 | 0x01 bool | `valve` | 0=closed 1=open — written by `open()`/`close()` | profile |
| 14 | 0x01 bool | `autoClean` | 0=off 1=on — **writable** | profile |
| 15 | 0x00 raw | `UnknownDp15` | unknown, log only | profile |
| 16 | 0x03 string, 14 B | `meterId` | hex → ASCII | driver |
| 18 | 0x00 raw, 4 B | `reverseWaterConsumed` | uint32 BE, liters → `volumeUnit` | driver |
| 21 | 0x00 raw, 4 B | `instantaneousFlowRate` + `rate` | uint32 BE L/h; `rate` = ÷60 LPM | driver |
| 22 | 0x02 value | `temperature` | ÷100 °C | profile |
| 26 | 0x02 value | `batteryVoltage` + `battery` | ÷100 V; % over 2.5–3.7 V | profile |

"driver" = intercepted in `processWaterMeterDP()`; "profile" = left to the deviceProfileLib engine.

### The split rule

A datapoint belongs in the driver when the profile engine **cannot** express it:

- the payload is not a plain ≤4-byte scalar (dp2, dp3, dp16), or
- the output unit depends on a runtime preference (dp1, dp2, dp3, dp18 — `volumeUnit`), or
- one datapoint has to produce two attributes (dp21 → `instantaneousFlowRate` + `rate`), or
- the type has no case in the library's switch (dp5 — there is no `bitmap` type).

Everything else stays in `tuyaDPs`, where it gets preference sync, change detection and event
sending for free.

### Profile-item constraints you will trip over

- **`type:'number'` truncates.** `compareAndConvertNumbers()`
  ([deviceProfileLib.groovy:1046](../../Libraries/deviceProfileLib.groovy#L1046)) returns an
  `Integer`. Anything with a `scale` must be `type:'decimal'`.
- **Enums need `map:` in value→label direction** — `map:[0:'1h', 1:'2h', …]`
  ([deviceProfileLib.groovy:1166](../../Libraries/deviceProfileLib.groovy#L1166)). A key named
  `enumMap`, or a label→value mapping, is silently ignored. Both bugs were present before 3.4.0.
- **`type:'bool'` without a `map` throws** — the `bool` case subscripts `foundItem.map` unconditionally.
  Use `type:'enum'` with an explicit map instead; that is why dp13/dp14 are enums here.
- **There is no `bitmap` type.** It falls to `default`, which hardcodes `isEqual = true`, so no event
  ever fires.
- **An undeclared attribute is silently dropped.** `processFoundItem()` gates on
  `device.hasAttribute(name)` ([deviceProfileLib.groovy:1305](../../Libraries/deviceProfileLib.groovy#L1305)).
  Adding a `tuyaDPs` entry without the matching `attribute`/`capability` in `metadata` does nothing.
- **Profile `preferences` maps preference-name → dp number as a string**: `['reportPeriod':'4']`.
  `getPreferencesMapByName()` ([deviceProfileLib.groovy:137](../../Libraries/deviceProfileLib.groovy#L137))
  resolves it to the `tuyaDPs` entry; `inputIt()` then requires `rw:'rw'`, a `title:` and, for enums,
  a `map:`. The preference inputs are rendered by deviceProfileLib's own `preferences` block — the
  driver does not loop over them.

## 6. Driver-specific behavior

### Events

- All events are sent with `isStateChange: true` — every report generates an event on purpose, so an
  hourly meter produces a visible heartbeat even when the totals don't move.
- `type` is always `'physical'` for device reports; `open()`/`close()` set
  `state.states['isDigital']` before sending.
- The four volume attributes and `rate`/`instantaneousFlowRate` are sent directly by the driver's own
  `send*Event()` helpers (lines 221–272), not through `customProcessDeviceProfileEvent()`.

### Battery

`sendBatteryPercentageFromVoltage()` (line 315) derives `battery` from `batteryVoltage`, clamped to
**2.5–3.7 V** — the range for the non-rechargeable 3.6 V ER14505 lithium cell these meters use, taken
from ed.net's driver in [post #12](https://community.hubitat.com/t/-/142433/12).

`batteryLib` is deliberately **not** `#include`d: the meters expose no ZCL Power cluster, and
`sendBatteryVoltageEvent()` expects 0.1 V units and clamps to 2.2–3.2 V, which would be wrong here.

### Commands and preferences

- `open()` / `close()` (lines 329, 339) — `sendTuyaCommand('0D', DP_TYPE_BOOL, '01'|'00')`. Guarded by
  `hasValve()` (line 327), which checks whether the *active profile* declares dp13, so the meter-only
  variant warns instead of sending. The `Valve` capability is declared unconditionally, so its buttons
  appear on every device page.
- Static preferences: `txtEnable`, `logEnable`, `volumeUnit`, `pollingInterval`.
  `reportPeriod` and `autoClean` come from the profile.
- `pollingInterval` defaults to **Disabled**. These are sleeping end devices on a 1–24 h firmware
  report period; polling drains the battery and mostly goes unanswered. Don't restore the old
  5-minute default.
- `volumeUnit` (`0` = m³ default, `1` = L) is read inside `sendVolumeEvent()`; changing it does not
  rewrite historical events, only subsequent ones.

### Lifecycle

- `customInitializeVars()` (line 426) resolves the profile from the device's own `model` /
  `manufacturer` data values via `setDeviceNameAndProfile()`, falling back to
  `TS0601_WATER_METER_VALVE`. It must not hardcode a manufacturer — that was a leftover test default
  before 3.4.0.
- `customParseZdoClusters()` sends `queryAllTuyaDP()` on device announce (0x0013).
- `refresh: ['refreshQueryAllTuyaDP']` → `zigbee.command(0xEF00, 0x03)`, the Tuya "query all DPs"
  request. A sleeping meter will not answer until it next wakes.

## 7. Implementation caveats

- `logWarn` is gated by **`logEnable`**, not `txtEnable` (commonLib convention).
- `safeToInt()` / `safeToDouble()` do **decimal** parses — never feed them hex. Use
  `zigbee.convertHexToInt()`.
- Tuya EF00 payloads are **big-endian** (Aqara FCC0 payloads are little-endian — different protocol,
  different rule; see [Libraries/AGENTS.md](../../Libraries/AGENTS.md) §8).
- `lastUInt32BE()` (line 289) returns a **`long`** on purpose, so `0xFFFFFFFF` stays positive.
- `hexListToAscii()` (line 295) uses an index loop rather than `.each` — no closure capture games in
  the HE sandbox.
- The driver is `singleThreaded: true`; no concurrency guards needed.

## 8. Regression checklist

There are no automated tests in this repo. Two levels of verification are available:

**Offline (no hardware needed).** `TEST_FRAMES` (line 461) holds the eleven datapoint frames captured
from a real `_TZE284_ajlu4cud`; `test()` (line 475) replays them through `parse()`. Available only
when `_DEBUG = true`. The same frames can be exercised outside Hubitat with Groovy 4 by extracting
the helper functions from this file into a script with a stubbed `zigbee` object — that is how the
3.4.0 decoding was validated. Expected results:

| dp | expected |
|---|---|
| 5 | `faults` = `empty_pipe_alarm,transducer_alarm` (0x1800) |
| 16 | `meterId` = `00000026009162`, **no `NumberFormatException`** |
| 22 | `temperature` = 32.8 °C |
| 26 | `batteryVoltage` = 3.42 V, `battery` ≈ 77 % |
| 4 | `reportPeriod` = `12h` |

The dp16 frame is the important one — that string is what crashed the *Tuya Smart Siren Zigbee*
driver when the device was mis-paired to it.

**On-hub.** Paste into Drivers Code, Configure, Refresh, watch Live Logs. Zero `NOT PROCESSED`
warnings for dp 1,2,3,4,5,6,16,18,21,22,26. Flip `volumeUnit` and confirm the four volume attributes
change unit and scale by 1000 and nothing else moves. On a valve meter, Open/Close and confirm the
device echoes dp13 back.

## 9. Release and documentation policy

- Bump `version()` + `timeStamp()` and add a header history line **only when the user asks**.
- Every change lands in `CHANGELOG.md` under the current development heading.
- Resolved `TODO.md` items are **deleted**, not ticked — their history lives in the changelog.
- `npm-groovy-lint` is used repo-wide: keep new code lint-clean, never mass-reformat.
- A library fix in `C:\work\Hubitat\Libraries` reaches users only after each driver's
  `*_lib_included.groovy` is regenerated — the maintainer controls when that happens.
