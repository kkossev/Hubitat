# Aqara P100 Multi-State Sensor — Driver Guide for AI Agents

This folder contains a **Hubitat Elevation (HE) Zigbee driver** (Groovy) for the **Aqara P100
Multi-State Sensor (DWZTCGQ11LM)** — a single-model driver for a battery sensor that works in two
mutually exclusive modes: **object** (movement / vibration / orientation / triple-tap / fall) and
**door_window** (contact open / closed).
Author: Krassimir Kossev (kkossev). License: Apache 2.0.

Community thread: <https://community.hubitat.com/t/release-aqara-multi-state-sensor-p100-zigbee-driver-c8-only/163540>
(recorded at line 4 of the driver and as `communityLink` in `packageManifest.json`)
Current version: **0.1.6 (2026-05-06)** — see `version()` / `timeStamp()` at lines 29–30.

**Bug work:** a reviewed list of known bugs with exact locations, fixes and verification steps is in
`BUGS.md` in this folder (written 2026-08-17 against v0.1.6). If you were asked to fix bugs in this
driver, work from that list — do not re-derive the findings. Mark items `[x]` there only after
kkossev confirms the hub test. Items marked **ASK USER** / **VERIFY ON DEVICE** must not be changed
without confirmation.

> **`BUGS.md` and `TODO.md` are maintainer working notes kept only in the local working copy.** They
> are listed by exact path in the repository `.gitignore`, so they are **not** published and will not
> be present in a clone — exactly as for the other driver folders (root
> [AGENTS.md](../../AGENTS.md) §0). References to them below are deliberate and are not broken links;
> if the files are absent, derive the finding yourself and raise it rather than assuming none exists.

> **ARCHITECTURE WARNING:** this driver is the **legacy monolithic architecture**: one
> self-contained 1380-line file. There are **no** `#include kkossev.*` libraries, **no**
> `deviceProfilesV3` map, **no** `custom*()` hook contract, and **no** amalgamated
> `*_lib_included.groovy` bundle to keep in sync. Everything — parsing, events, health check,
> preferences — lives in `Aqara_P100_Multi_State_Sensor.groovy`. Do not apply V3/V4 conventions here
> and do not convert this driver to V3 as part of a focused fix (root [AGENTS.md](../../AGENTS.md) §4).

> **This driver was forked from `Drivers\Aqara P1 Motion Sensor\`** (see the v0.1.0 header line), so
> many P1 idioms, helper names and even a few of P1's bugs recur verbatim. When something looks odd,
> compare with the P1 driver before assuming it is P100-specific — but note that the P100 has **no
> `is*()` model predicates** at all, because it supports exactly one model.

---

## 1. Files in this folder

| File | Role |
|---|---|
| **`Aqara_P100_Multi_State_Sensor.groovy`** | **The production driver** (v0.1.6). The only `.groovy` file, and the only one you ever edit. `importUrl` points at the `development` branch copy of this exact path. |
| `AGENTS.md` | This guide. |
| `BUGS.md` | Reviewed bug work-list (root AGENTS.md §9). **Local-only** — `.gitignore`d, not published. |
| `CHANGELOG.md` | Keep a Changelog history, reconstructed from the driver's header history. |
| `README.md` | User-facing. |
| `TODO.md` | Open questions and unverified device behaviour. **Local-only** — `.gitignore`d, not published. The release thread is not yet analysed. |
| `packageManifest.json` | HPM manifest. Version `0.1.6`, first HPM release dated 2026-08-17. **Only touch it at an HPM release kkossev has declared** — a manifest version trailing the driver is the normal state (root [PUBLISHING.md](../../PUBLISHING.md)). |
| `.hubitat\metadata.json` | Hubitat VS Code extension mapping (local file ↔ hub code id 5154). **Tool-managed — never hand-edit.** |

**Absent by design:** no `.groovylintrc.json`, no `.code-workspace` (nothing to add to the workspace
— there are no `#include` targets), no `Archives\`, no snapshot copies. Do not treat these as defects.

**HPM catalog registration.** The package is registered in the repository catalog
`repository.json` as package id `7e8d3951-8550-470b-84a8-21e9d07acdec`, driver id
`0cf0b9b7-cc5e-4a60-8d15-6bea294acbe8`. **Never change either UUID** (root
[PUBLISHING.md](../../PUBLISHING.md), *Publishing a driver or app* step 10). The manifest `location`
and the driver's own `importUrl` are identical and both point at `development` — keep them in sync.

### Editing workflow

- Version constants are `static String version()` / `static String timeStamp()` (lines 29–30).
  **Bump them, and add the `* ver. x.y.z  YYYY-MM-DD kkossev  - description` header history line,
  only when kkossev says so** — never after an individual bug fix (root AGENTS.md §2 rule 4).
- `checkDriverVersion()` (line 1183) runs on **every** `parse()` and on `updated()`; when the
  version+timestamp string changes it sets `state.comment`, re-runs `initializeVars(fullInit=false)`
  and re-derives the device name, so new settings get defaulted automatically after an upgrade.
- **Never upload to the hub.** kkossev pushes from the Hubitat VS Code extension and reports the
  result. There are no automated tests — the hub's compile-on-Save and Live Logs are the only checks.
- At an HPM release point (and only then): bump `packageManifest.json`'s `version`, `dateReleased`
  and `releaseNotes` per [PUBLISHING.md](../../PUBLISHING.md), then re-sync `repository.json` to the
  `main` branch. A merge to `development` publishes the code but **does not** publish an HPM update.
- Repo and branch policy: root [AGENTS.md](../../AGENTS.md) §1 and [CONTRIBUTING.md](../../CONTRIBUTING.md).
  `development` is the single source of truth.

---

## 2. Supported device

One model only. There is no profile map and no model-predicate layer.

| Zigbee model | Manufacturer | Marketing name | Notes |
|---|---|---|---|
| `lumi.vibration.agl002` | `Aqara` | Aqara P100 Multi-State Sensor **DWZTCGQ11LM** | Battery powered, sleepy end device. Fingerprint declares endpoint `01`, `inClusters:"0101,000C,0006,0003,0000,FCC0"`, `outClusters:"000A,0019"`; the real device reports `devId:0x0402, epList:[1,2]`. |

- `setDeviceName()` (line 1169) hard-codes the display name and writes `aqaraModel = "DWZTCGQ11LM"`
  into the device data. Note it writes that data value **even on the unknown-model branch** — a
  deliberate simplification, but it means `aqaraModel` is not evidence that the model matched.
- `updateAqaraVersion()` (line 1195) derives an `aqaraVersion` data value (`0.0.0_NNNN`) from the
  first byte of the `application` data value.
- **The manufacturer string is `Aqara`, not `LUMI`.** The v0.1.1 header line "corrected fingerprint"
  refers to this. Do not "fix" it to `LUMI`.
- `_DEBUG` (line 39, `false` in releases) gates three extra commands: `test`, `initialize`,
  `sendTimeSync`. **Keep it `false`.** Note the side effect documented in BUGS.md B1: with `_DEBUG`
  false, `initialize()` is unreachable, and it is the only path to `initializeVars(fullInit = true)`.

**Credits:** the datapoint/attribute semantics come from `@absent42`'s Zigbee2MQTT external
converter (<https://github.com/absent42/Aqara-P100-Sensor>), cited in the driver header. Testing
credits: `@rad1` (v0.1.1), `@user1974` (v0.1.6).

---

## 3. The two-mode design — read this first

This is the central concept of the driver, and the thing most likely to be broken by a careless edit.

The device operates in exactly one of two modes, selected by **FCC0 attribute `0x0116`**
(`object` = 5, `door_window` = 3):

| Mode | Attributes it produces | Attributes it must not produce |
|---|---|---|
| `object` | `lastAction`, `orientation`, `acceleration`, `devicePosture` | `contact` |
| `door_window` | `contact` | `lastAction`, `orientation`, `acceleration` |

Three pieces of machinery enforce it:

- **`getDeviceMode()` (line 214)** — the single source of truth. It prefers the **`deviceMode`
  attribute** (what the device last reported) and only falls back to the **`deviceMode` preference**,
  then to `"object"`. Always call this helper; never read the preference directly for a routing
  decision.
- **`parseContactEvent()` (line 507) and `parseActionEvent()` (line 523)** each begin with a mode
  guard and `return` with a `logDebug` if the event belongs to the other mode. See BUGS.md B6 — the
  drop is invisible at default log levels.
- **`clearStaleModeAttributes(newMode)` (line 465)** — deletes the other mode's current states and
  unschedules `resetAcceleration`. Called from `parse()` case `"0116"` when the reported mode
  actually changed, and from `updated()` *before* the write is acknowledged (BUGS.md B7).

> **Naming trap.** `deviceMode` is simultaneously a **preference**, an **attribute**, and — via
> `String getDeviceMode()` — a getter-shaped **method**. Groovy will happily resolve a bare
> `deviceMode` to the method. Always be explicit: `settings?.deviceMode`,
> `device.currentValue('deviceMode')`, or `getDeviceMode()`.

---

## 4. Message flow (parse pipeline)

`parse(String description)` (line 226) is the single entry point:

1. `checkDriverVersion()`; increment `state.rxCounter`; `setHealthStatusOnline()` — the first 2
   packets after pairing deliberately do **not** count as "online" (line 1224).
2. `zigbee.parseDescriptionAsMap(description)` inside a try/catch — a parse exception is logged and
   the message dropped.
3. If `descMap.attrId != null`: build `attrData` from the primary attribute plus every entry in
   `descMap.additionalAttrs`, then run each through the routing chain below. `status == "86"` is
   filtered out first as "unsupported".
4. Otherwise route by `profileId`: `"0000"` → `parseZDOcommand()`, `"0104"` + `clusterId` →
   `parseZHAcommand()`.

### Attribute routing chain

| Cluster / attr | Handler | Meaning |
|---|---|---|
| `0x0006:0000` | `parseContactEvent(int)` | genOnOff → `contact`. `0` = closed, `1` = open (door_window mode only) |
| `0x0101:0055` | `parseActionEvent(int)` | closuresDoorLock → the action enum (object mode only) |
| `0x0001:0020` | `voltageAndBatteryEvents(raw/10.0)` | battery voltage in 0.1 V units; `"00"` is ignored |
| `0x0000:0001` | `sendRttEvent()` | treated as a ping response — **unconditionally**, see BUGS.md A1/B8 |
| `0x0000:0004` | `logInfo` | ZCL ManufacturerName — but the log text says "device model" (BUGS.md C18) |
| `0x0000:0005` | `sendInfoEvent` | ZCL ModelIdentifier, but Aqara repurposes a `0x0005` report as **button pressed** → "The device will stay awake for 15 minutes". `aqaraBlackMagic()` reads `0x0004`+`0x0005` together, so its own read echoes back as a fake button press. |
| `0x0000:FFF0` | `preventDeviceReset(value)` | Aqara factory-reset probe — see §6 |
| `0xFCC0:*` | `parseAqaraClusterFCC0()` | Aqara manufacturer cluster — the heart of the driver (§5) |
| `0x0000:FF01` | `parseAqaraAttributeFF01()` → `parseBatteryFF01()` | legacy Xiaomi battery TLV |
| anything else | `logDebug` "Unprocessed attribute report" | |

> **`0x000C` (analogInput) is in the fingerprint's `inClusters` but has no handler.** On older
> `lumi.vibration.*` devices, `0x000C:0x0055` carries vibration strength and tilt angle. Whether the
> P100 uses it at all is unknown — tracked as BUGS.md **B9 / VERIFY ON DEVICE**. Do not add
> speculative handling without a real device log.

> **`0x0000:FF02` has no route** even though `parseAqaraAttributeFF02()` / `parseBatteryFF02()` exist
> (they are byte-identical duplicates of the FF01 pair). It falls through to "Unprocessed"
> (BUGS.md C14).

### ZDO handling (`parseZDOcommand`, line 800)

All cases are log-only **except** `0x0013`:

- `0x0013` **device announcement → calls `aqaraBlackMagic()`**. This is the driver's main
  re-initialization trigger — it fires on rejoin, re-pair, and battery reinsertion. It is *not*
  triggered by pressing *Configure* (BUGS.md B3).
- `0x0002` Node_Desc_req and `0x0036` End_Device_Timeout_Req are **log-only on purpose** — the
  comments state the Zigbee stack answers these itself. Unlike the P1 driver, this driver does
  **not** hand-craft a faked Aqara-hub node descriptor. Do not port P1's version in.
- `0x8004` → `parseSimpleDescriptorResponse()`, which rewrites the `inClusters` / `outClusters`
  device data values when they differ. **This function is unsafe — see BUGS.md A3** (no status
  guard, and Groovy's `1..0` iterates downward, so a zero cluster count writes garbage into the
  device's cluster data).
- `0x0006`, `0x8005`, `0x8021`, `0x8022`, `0x8032`, `0x8034`, `0x8038` — log only.

### ZHA global-command handling (`parseZHAcommand`, line 846)

| `descMap.command` | Handling |
|---|---|
| `"00"` Read Attributes **from the device** | `clusterId == "000A"` → `replyToTimeClusterRead()`; anything else is logged. **This is the critical Time-cluster path (§6).** |
| `"01"` Read Attribute Response | logs cluster / attr / status; guards short payloads |
| `"04"` Write Attribute Response | decodes `UNSUPPORTED_ATTRIBUTE` / `UNSUPPORTED_CLUSTER` |
| `"07"` Configure Reporting Response | log only |
| `"09"` Read Reporting Config Response | log only; **unreachable** — the driver never reads a reporting config (BUGS.md C13) |
| `"0B"` ZCL Default Response | logged only on failure |

---

## 5. Aqara FCC0 cluster attribute map (mfgCode 0x115F)

`parseAqaraClusterFCC0()` (line 310) is a switch on the **upper-cased** `attrId`. Unlike the P1
driver, there are **no duplicate case labels** here — keep it that way.

Two locals are computed up front:
- `Integer value = safeHexToInt(it.value)` (line 312) — a **hex** parser, correctly. This driver does
  *not* have the P1's `safeToInt`-on-hex bug. It does have an overflow hazard (BUGS.md A2).
- `String valueHex = …` (line 311) — **never read; delete it** (BUGS.md A5).

| attrId | `ATTR_*` constant | Type | Meaning / action |
|---|---|---|---|
| `0005` | — | — | button pressed → `sendInfoEvent` |
| `0017` | `ATTR_BATTERY_VOLTAGE` | UINT16 | battery mV → `voltageAndBatteryEvents(value/1000.0)` |
| `0018` | `ATTR_BATTERY_PCT` | UINT8 | battery % → `sendBatteryEvent(value)`. **Contradicts the v0.1.6 "battery from voltage only" decision — BUGS.md B4 / ASK USER** |
| `00DF` | — | TLV | periodic diagnostic heartbeat, ignored — **but schedules `sendTimeSync` at +1 s** (v0.1.5) |
| `00E6` | — | ? | unknown/observed P100 status, log only |
| `00F7` | — | TLV | Xiaomi struct → `decodeAqaraStruct()` (§5.1) |
| `00FC` | — | — | "LUMI LEAVE" report → `log.warn` only |
| `00FF` | — | octet str | **registration-response** report — the device's acknowledgement of the §6 handshake write |
| `0107` | `ATTR_VIBRATION_DETECTION` | BOOLEAN | vibration detection on/off |
| `010C` | `ATTR_SENSITIVITY` | UINT8 | detection sensitivity 1–10 |
| `0116` | `ATTR_DEVICE_MODE` | UINT8 | **device mode** (3 = door_window, 5 = object) — drives §3 |
| `01D8` | `ATTR_FALL_DETECTION` | BOOLEAN | fall detection on/off |
| `01EB` | `ATTR_DOOR_WINDOW_TYPE` | UINT8 | 1 casement / 2 hopper / 3 composite / 4 hinged door |
| `01EC` | `ATTR_REPORT_INTERVAL` | UINT32 | report interval, seconds |
| `01ED` | `ATTR_MOVEMENT_DETECTION` | BOOLEAN | movement detection on/off |
| `01EE` | `ATTR_DEVICE_POSTURE` | UINT8 | 0 unknown / 1 normal / 2 abnormal → `devicePosture` (+ `_status_` hint when abnormal) |
| `01EF` | `ATTR_TRIPLE_TAP_DETECTION` | BOOLEAN | triple-tap detection on/off |
| `01F0` | `ATTR_ORIENTATION_DETECTION` | BOOLEAN | orientation detection on/off |
| `01F1` | `ATTR_ORIENTATION` | UINT8 | 1 face_up / 2 face_down / 3 vertical / 4 tilt → `orientation` |
| `01F3` | — | BOOLEAN | **deliberately ignored** — "fires true on every detection but never resets"; Z2M does not expose it either |

**The readback contract.** Every *settings* attribute above (`0107`, `010C`, `0116`, `01D8`, `01EB`,
`01EC`, `01ED`, `01EF`, `01F0`) does the same three things: `sendEvent` where an attribute exists,
`device.updateSetting(...)` to sync the preference page, and `storeParamValue(...)` to record the
confirmed value. **`storeParamValue()` is called only from here — never from `updated()`.** That is
the whole point of the design (§7).

### 5.1 `decodeAqaraStruct` — the 0x00F7 TLV

`decodeAqaraStruct()` (line 569) walks `tag / ZCL-dataType / value` triplets, advancing by the data
type's width. Handled tags:

| tag | type | meaning |
|---|---|---|
| `0x01` | uint16 | battery mV → `voltageAndBatteryEvents(rawValue / 1000)` |
| `0x03` | uint8 | internal device temperature — **log only**, no event (unlike P1) |
| `0x05` | uint16 | RSSI (log only) |
| `0x06` | uint40 | device LQI (log only) |
| `0x0A` | uint16 | **parent NWK** — tracked in `state.health.parentNWK` + `nwkCtr`, warns on change |
| `0x0B` | uint16 | light level (log only) |
| `0x0D` | uint32 | firmware version (log only; the decode overlaps its own bit fields — BUGS.md C8) |
| `0x17` | uint16 | battery voltage mV → `voltageAndBatteryEvents()` |
| `0x18` | uint8 | battery % — **`sendBatteryEvent` is deliberately commented out**, "using voltage-based calculation" (v0.1.6). Compare FCC0 `0x0018`, which is *not* — BUGS.md B4 |
| `0x64` | uint8 | on/off (log only) |
| `0x9B` | uint8 | consumer connected (log only) |

An unrecognised data type advances the cursor by only **one byte** and logs a warning, which
desynchronises the walk. That is the inherited house pattern; it terminates, it just gets noisy.

---

## 6. P100-specific survival rituals — do not "simplify" these

This is the part with no P1 equivalent, and the reason the driver exists as a separate file. All
three mechanisms were reverse-engineered from an Aqara E1 hub Wireshark trace across v0.1.3–v0.1.5.

### The Lumi registration handshake (`aqaraBlackMagic()`, line 1082)

```
1. he raw … 0x8002 {40 00 00 00 00 40 8f 5f 11 …}   fake coordinator node-descriptor response
2. read  FCC0:0x0116 (device mode)                   matches the E1 hub's exact ordering
3. he raw … 0xFCC0 {04 5F 11 00 02 FF 00 41 10 …}    THE registration write — see below
4. read  0x0000:[0x0004, 0x0005]
5. read  FCC0:[0x0116, 0x010C]
```

Step 3 writes a **16-byte octet string to FCC0 attribute `0x00FF`**, payload captured verbatim from
the E1 hub. Three things about it are load-bearing:

- **It uses `he raw`, not `zigbee.writeAttribute` / `he wattr`, on purpose.** The in-code comment is
  explicit: `he wattr` does not auto-prepend the octet-string length byte (`0x10` = 16) for type
  `0x41`. Rewriting this call in "cleaner" Groovy silently breaks the payload.
- **It is SUSPECTED — not confirmed — that without this write the device leaves the Zigbee network
  after ~24 hours.** The driver says so in a comment. Treat it as the driver's most important line
  until someone proves otherwise.
- The device answers with a Write Attributes Success and then a **FCC0 `0x00FF` report**, which the
  parse switch logs as "registration handshake completed successfully".

**The `zdo bind` lines immediately below step 3 are commented out deliberately** — the comment
explains that the registration write makes the device unicast directly to the hub, and that the E1
hub sends no binds at all. **Do not re-enable them** (root AGENTS.md §10).

Called from: `installed()` and ZDO `0x0013` only. **Not** from `configure()` (BUGS.md B3).

### Time cluster replies (`replyToTimeClusterRead()` line 937, `sendTimeSync()` line 959)

Hubitat's coordinator does **not** answer device-originated reads on cluster `0x000A`
(confirmed in Wireshark, per the comment). The driver hand-builds the ZCL Read Attributes Response
as a raw frame — `18 00 01` header, then attrs `0x0000` (type `0xE2` UTCTime), `0x0002` (timezone,
`0x2B` INT32) and `0x0005` (DST offset, `0x2B` INT32), all little-endian via `toLEHex32()`.
Epoch conversion uses `ZIGBEE_EPOCH_OFFSET = 946684800` (Zigbee epoch = 2000-01-01 UTC).

It is invoked from three directions:

1. **Reactively** — `parseZHAcommand` case `"00"` when `clusterId == "000A"`.
2. **Proactively after `configure()`** — four times, at +3 / +6 / +9 / +12 s, because the P100 always
   requests time right after the registration handshake and the reactive path may be too late.
3. **After every `0x00DF` diagnostic heartbeat** — `runIn(1, "sendTimeSync")`, added in v0.1.5.
   The `runIn(342, …)` line beside it is **commented out on purpose** ("P100 sleeps at this time") —
   leave it commented.

The working theory in the code comment: without the reply, the device's internal 24-hour watchdog
makes it leave the network. Same suspicion as the registration write, same treatment.

### Factory-reset abort (`preventDeviceReset()`, line 484)

Mirrors Z2M's `lumiPreventReset`. When the button is held for a factory reset, the device sends a
probe on genBasic `0x0000:FFF0` starting `AA10054187`; the hub must write
`AA1005414701011001` (type `0x41`, `mfgCode 0x115F`) back to abort it. Non-matching payloads are
logged and ignored. Added in v0.1.3.

---

## 7. Parameter change detection

Carried over from the P1/FP300 template and central to how `updated()` behaves.

- **`state.params`** is a list of `[n: name, t: type, v: value, l: isLocal]` entries
  (`storeParamValue()` line 160, `getStoredParamValue()` line 169).
- **`updated()` (line 975) writes an FCC0 attribute only when `hasParamChanged(name, settings.value)`
  is true** — comparison goes through `normalizeParamValue()` (line 184), which coerces numeric and
  boolean strings so `"5"` and `5` compare equal.
- **The confirmed value is stored only when the device echoes it back in `parse()`** (§5). So a write
  that never lands (very likely on a sleepy device) is automatically retried on the next Save
  Preferences. **Preserve this pattern when adding a parameter** — do not "helpfully" call
  `storeParamValue()` from `updated()`.
- `VIRTUAL_PARAMS` (line 91) and `isVirtualParam()` (line 156) came along from the template but are
  **unused here** — the P100 has no local-only offsets. `clearParamStorage()` (line 205) is likewise
  unreferenced (BUGS.md C1/C3).
- `initializeParamStorage()` (line 194) initialises `state.params` **and** maintains
  `state.driverVersion` using `driverVersionAndTimeStamp()`. Note this is the *same* format
  `checkDriverVersion()` uses, so the P1's `state.driverVersion` format war (P1 BUGS.md B6) does
  **not** exist here. Do not "fix" it.

---

## 8. Event pipeline

- **`contact`** — `parseContactEvent()`: `onOff == 0` → `closed`, else `open`. This is the standard
  Zigbee mapping and matches Z2M; the code comment calling it "inverted logic" is wrong
  (BUGS.md C15).
- **`lastAction`** — `parseActionEvent()` maps `actionMap`: `0 triple_tap`, `1 movement`,
  `2 vibration`, `3 orientation`, `4 fall`. Always sent with `isStateChange: true` so repeats of the
  same action still fire rules.
- **`acceleration` is derived, not reported.** `movement`, `vibration` and `fall` each pulse
  `acceleration: active` (`physical`) and schedule `resetAcceleration()` at **+3 s**, which sends
  `inactive` as `digital`. `triple_tap` and `orientation` deliberately do **not** pulse it.
- **`orientation`** — from FCC0 `0x01F1`. `orientationMap` also maps `0` to `"unknown"`, which is not
  in the declared enum (BUGS.md C5).
- **`devicePosture`** — from FCC0 `0x01EE`; `abnormal` additionally raises a `_status_` hint about
  incorrect installation.
- **Battery** — `voltageAndBatteryEvents(volts)` (line 772) computes percent linearly over
  **2.5–3.0 V**, clamped 0–100, and emits both `batteryVoltage` and `battery`, both with
  `isStateChange: true`. Reached from four places: cluster `0x0001:0020` (÷10), FCC0 `0x0017` (÷1000),
  TLV tags `0x01` and `0x17`, and the legacy FF01 TLV. `sendBatteryEvent()` (line 790) sends percent
  directly and is used only by FCC0 `0x0018`.
- **`powerSource`** — `powerSourceEvent()` (line 1215) hard-codes `battery`, but is only reachable
  when `fullInit == true`, which is unreachable in a released build. **The attribute is never
  populated** (BUGS.md B1).
- **`_status_`** — `sendInfoEvent(msg)` is transient user feedback, auto-cleared after
  `INFO_AUTO_CLEAR_PERIOD` (60 s).
- Event `type` is `'physical'` for device-originated and `'digital'` for driver-derived. This driver
  has no rate-limiting / delta filters and no `'delayed'` events.

### Health check

- `deviceHealthCheck()` (line 1233) self-reschedules every `DEFAULT_POLLING_INTERVAL` (3600 s) and
  sends `healthStatus: offline` after `PRESENCE_COUNT_THRESHOLD` (3) silent intervals. Any received
  packet resets the counter via `setHealthStatusOnline()` — except the first two after pairing.
- It is scheduled from `updated()` and `configure()` only, **not** from `installed()`
  (BUGS.md B5).
- `ping()` (line 1246) reads Basic attr `0x01`, stores `state.pingTime`, and arms
  `deviceCommandTimeout()` at `COMMAND_TIMEOUT` (10 s), which just logs "sleepy device".
  `sendRttEvent()` (line 1253) emits `rtt`. Both have defects — BUGS.md A1 and B8.

---

## 9. Commands & preferences

**User commands:** `configure` (unschedules everything, `initializeVars(false)`, restarts the health
check, 4× `sendTimeSync`, `aqaraReadAttributes` at +30 s), `ping`, `refresh` (reads the 11 FCC0
settings/state attributes in 4 batched reads — note it does **not** read battery).
`_DEBUG`-only: `test`, `initialize`, `sendTimeSync`.

> `configure()`'s command description promises "Will load device default values!", but the command
> passes `fullInit = false`, so `state` is *not* cleared and defaults are only filled where null.
> Only `initialize()` does a true full init — and it is `_DEBUG`-gated.

**Preferences** (line 127 onward) are **conditional on `settings?.deviceMode`**, so the preference
page changes shape after the mode is known:

| Preference | Type / range | Default | Mode |
|---|---|---|---|
| `txtEnable` | bool | true | always |
| `logEnable` | bool | true | always |
| `deviceMode` | enum `object` / `door_window` | `object` | always |
| `motionSensitivity` | number `1..10` | 5 | always |
| `reportInterval` | number `1..300` | 60 | always — **but `updated()` only writes 5..300**, BUGS.md B2 |
| `doorWindowType` | enum, 4 values | `hinged_door` | `door_window` only |
| `movementDetection` | bool | true | `object` only |
| `vibrationDetection` | bool | true | `object` only |
| `orientationDetection` | bool | true | `object` only |
| `fallDetection` | bool | true | `object` only |
| `tripleTapDetection` | bool | true | `object` only |

Defaults appear twice — as `defaultValue:` in the `input` and again in `initializeVars()`
(line 1130). **They currently agree; keep them in sync if you change either.**

`motionSensitivity` keeps the P1 template's name even though this device has no motion sensor. It is
public preference surface now — **do not rename it** (root AGENTS.md §10).

All FCC0 writes carry `[mfgCode: MFG_AQARA]` (`0x115F`) and `delay = 200`.

> **Sleepy device.** The P100 accepts configuration only shortly after it wakes. Never tell a user to
> just press *Configure* — the house guidance is to press the device's pairing button at the same
> time as *Save Preferences*, or to re-pair with the driver already installed.

---

## 10. Known quirks (verify before "fixing")

1. **`safeHexToInt()` is a hex parser and is used correctly.** This driver does *not* inherit the
   P1's `safeToInt`-on-hex-string bug (P1 BUGS.md B1). It has a different problem — overflow on
   8-character values (BUGS.md A2). Do not port the P1 fix here.
2. **`sendRttEvent()` carries the P1's elvis-precedence bug verbatim** (line 1255) — BUGS.md A1.
3. **`parseSimpleDescriptorResponse()` can corrupt device data** (BUGS.md A3). If you ever see
   garbage in the device's `inClusters`/`outClusters` data values, this is why.
4. **`state.driverVersion` uses one consistent format** here — unlike the P1. Leave it.
5. **`unschedule()` in `configure()`** (line 1111) also cancels the 24-hour `logsOff` timer without
   rescheduling it (BUGS.md C16).
6. The header history's `TODO:` list is empty — there is no author TODO block to migrate to
   `TODO.md`.
7. The community-thread URL was a literal `PLACEHOLDER` until 2026-08-17; it is now the real
   release thread (BUGS.md C10, closed).

### Intentionally disabled or ignored — do not re-enable

| Location | What | Why |
|---|---|---|
| `aqaraBlackMagic()` | three `zdo bind` lines | the registration write replaces them; the E1 hub sends no binds |
| FCC0 `00DF` case | `runIn(342, "sendTimeSync", [overwrite: false])` | v0.1.5 — "P100 sleeps at this time" |
| `decodeAqaraStruct` tag `0x18` | `sendBatteryEvent(rawValue)` | v0.1.6 — battery is derived from voltage only |
| FCC0 `01F3` case | the whole attribute | fires true on every detection and never resets; Z2M does not expose it |
| `parseZDOcommand` `0002` / `0036` | log-only, no response built | the Zigbee stack answers these; do not port P1's faked node descriptor here |

---

## 11. Conventions

- `singleThreaded: true` in the definition — **no concurrency guards needed** in handlers.
- Logging: `logDebug` / `logInfo` / `logWarn` (lines 1301–1317), gated by `logEnable` / `txtEnable`.
  **`logWarn` is gated by `logEnable` (debug), not `txtEnable`** — repo-wide behaviour, do not
  change. Debug logging auto-disables after 24 h (`logsOff`). There is **no** `logTrace` helper here.
- Some raw `log.*` calls are deliberate house style (`configure`, `initialize`, `installed`,
  `sendHealthStatusEvent`, the LUMI-LEAVE warning) — **match the style of the function you are
  editing** and do not mass-convert them. The three in `parseSimpleDescriptorResponse` are
  genuine slips (BUGS.md C9).
- Every FCC0 read/write carries `[mfgCode: 0x115F]` / `[mfgCode: MFG_AQARA]`.
- **Multi-byte Aqara payloads are little-endian.** The byte-swap patterns
  (`valueHex[(i+6)..(i+7)] + valueHex[(i+4)..(i+5)]`, `toLEHex32()`) are intentional.
- `Integer.parseInt(x, 16)` / `safeHexToInt()` for hex; `safeToInt()` is **decimal** and is used only
  on preference values. Never mix them (root AGENTS.md §7).
- `fn(argName = value)` (`delay = 200`, `initializeVars(fullInit = false)`, `configure(fullInit = true)`)
  is the repo's assignment-expression idiom, **not** named parameters. It works. Match it locally,
  don't rely on it in new code, don't mass-refactor it.
- Reflection is blocked in the Hubitat sandbox: **no `getClass()` / `.class`**, and they fail at
  *runtime*, not at paste time. In a `catch` use `e.message`.
- **No automated tests.** Verification is: kkossev pastes into HE "Drivers code", saves (the only
  real compile check), then pairs or hits `configure` and watches Live Logs with debug logging on.
