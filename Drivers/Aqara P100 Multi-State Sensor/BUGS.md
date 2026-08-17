# Aqara P100 Multi-State Sensor driver — reviewed bug work-list

Target file: `Aqara_P100_Multi_State_Sensor.groovy`, **v0.1.6 (2026-05-06)**, 1380 lines.
Line numbers refer to that exact version — re-locate by the quoted code if the file has moved on.
Read [AGENTS.md](AGENTS.md) in this folder before touching anything.

Findings are from a static deep-review (2026-08-17). No changes have been applied yet.
**One bug at a time, in the order the user (kkossev) chooses** — upload to the dev hub and let the
user test after each fix; mark an item `[x]` only after he confirms it works.
**Do NOT bump `version()`/`timeStamp()` and do NOT add header history lines after individual
fixes — the user will explicitly say when.** Items marked **ASK USER** or **VERIFY ON DEVICE**
must not be "fixed" without confirmation — they depend on real device behaviour or on the author's
deliberate design decisions around the P100 registration/time-sync experiments (v0.1.3–v0.1.6).

Severity: **A** = runtime exception / crash of `parse()` or a scheduled job, **B** = wrong behaviour,
**C** = minor, inconsistency, dead code.

Status legend: `[ ]` open · `[x]` fixed (user-confirmed) · `[?]` needs verification first.

> **Note on the P1 lineage.** This driver was forked from `Aqara_P1_Motion_Sensor.groovy`, so some
> items below are the same defect that exists in the P1's own `BUGS.md` (A1 ≡ P1 A2, A6 ≡ P1 C-class,
> C5 ≡ P1 C15). Others look similar but are **not** — in particular this driver's `safeHexToInt()` is
> a correct hex parser, so **P1's B1 does not apply here.** Do not copy P1 fixes across blindly.

---

## A — Runtime exceptions

- [ ] **A1. `sendRttEvent()` NPE when `state.pingTime` is null**
  - Location: `sendRttEvent()`, line 1255:
    `def timeRunning = now.toInteger() - state.pingTime?.toInteger() ?: now.toInteger()`.
  - Problem: the elvis binds to the *result of the subtraction*, so it never protects against a null
    `state.pingTime` — `Integer - null` throws NPE before the elvis is ever evaluated. Identical to
    the P1 driver's A2.
  - Failure scenario: `sendRttEvent()` is called from `parse()` for **any** Basic cluster attr
    `0x0001` report (line 269), not only for a reply to `ping()`. So: an unsolicited
    ApplicationVersion report during pairing, or the first such report after `state.clear()` in
    `initializeVars(true)` — `state.pingTime` is null and the whole message is aborted.
  - Fix: guard first, then compute —
    `if (state.pingTime == null) { logDebug 'Basic attr 0x0001 received without a pending ping — ignored'; return }`.
    Also clear `state.pingTime` after use (see B8).
  - Secondary note, same line: `new Date().getTime()` is a Long around 1.75e12; `.toInteger()`
    silently truncates to the low 32 bits. Both operands truncate the same way, so the result is
    normally correct, but it is wrong by 2^32 when the two timestamps straddle a 32-bit boundary
    (roughly every 49.7 days). Prefer subtracting the Longs and narrowing afterwards.
  - Verification: with debug logging on, a fresh install must not throw on the first Basic `0x0001`
    report; `ping()` must still produce a sane `rtt`.
  - Confidence: high.

- [ ] **A2. `safeHexToInt()` throws `NumberFormatException` on 8-hex-character values ≥ 0x80000000**
  - Location: `safeHexToInt()`, line 1346–1353; the length guard is `s.length() > 8`, then
    `return Integer.parseInt(s, 16)`.
  - Problem: `Integer.parseInt("FFFFFFFF", 16)` overflows and throws. The guard rejects *longer*
    strings but lets a full-width 32-bit value through.
  - Failure scenario: `Integer value = safeHexToInt(it.value)` runs at the **top of
    `parseAqaraClusterFCC0()`** (line 312) for *every* FCC0 attribute, before the switch. So one
    oversized value aborts the entire report, including attributes that would otherwise have been
    handled in the same frame. The only UINT32 attribute in the map is `0x01EC`
    (`ATTR_REPORT_INTERVAL`), whose legitimate range is 5–300 — but an uninitialised or
    firmware-glitched readback of `FFFFFFFF` is exactly the case that would hit this.
  - Fix: parse as `Long` and narrow, returning the default on overflow:
    `long l = Long.parseLong(s, 16); return (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) ? defaultValue : (l as Integer)`.
    Wrapping the body in a try/catch returning `defaultValue` also works and is closer to the house
    style of `safeToInt`.
  - Verification: `safeHexToInt("FFFFFFFF")` must return the default rather than throw. On-device:
    an FCC0 report is a normal-path check, no special setup needed.
  - Confidence: high on the logic; low frequency in practice.

- [ ] **A3. `parseSimpleDescriptorResponse()` has no status guard, and `1..0` iterates backwards —
      NPE, or the device's `inClusters` data value overwritten with garbage**
  - Location: `parseSimpleDescriptorResponse()`, lines 902–930. Two distinct defects:
    - Line 904: `hexStringToInt(descMap.data[11])` with no check of the response status at
      `data[1]`. A ZDO Simple_Desc_rsp with a failure status (e.g. `NOT_ACTIVE`, `DEVICE_NOT_FOUND`)
      is only 4 bytes, so `data[11]` is null → NPE.
    - Lines 906 and 920: `for (int i in 1..inputClusterCount)`. In Groovy `1..0` is a **descending**
      range `[1, 0]`, not an empty one. With a cluster count of 0 the loop runs twice, reads
      `data[13]`, `data[12]`, then `data[11]`, `data[10]`, and builds a bogus cluster list — which
      lines 915 / 929 then write into the device's `inClusters` / `outClusters` data values,
      **destroying the real values**.
  - Failure scenario: any Simple_Desc_rsp that is not a full success for endpoint 1. Reachable
    because the hub issues Simple_Desc_req during pairing and on "Get Info", and the response is
    delivered to the driver via ZDO `0x8004`. The driver itself never requests one, which is why
    this has probably not been seen yet.
  - Fix: return early unless `descMap.data[1] == "00"` and `descMap.data.size() >= 12`, and guard both
    loops with `if (inputClusterCount > 0)` / `if (outputClusterCount > 0)`. Also only call
    `updateDataValue()` when the parsed list is non-empty.
  - Verification: on-hub, press *Get Info* on the device page — the `inClusters` data value must still
    read `0101,000C,0006,0003,0000,FCC0` afterwards.
  - Confidence: high on both defects; medium on how often the failing response actually arrives.

- [ ] **A4. `replyToTimeClusterRead()` NPEs when the hub has no timezone configured**
  - Location: lines 943–944: `location.timeZone.rawOffset.intdiv(1000)` and
    `location.timeZone.inDaylightTime(new Date())`, with no null check on `location.timeZone`.
  - Failure scenario: on a hub whose location/timezone has not been set, `location.timeZone` is null.
    Because `sendTimeSync()` is scheduled from `parse()` after **every** FCC0 `0x00DF` diagnostic
    heartbeat (line 451), this throws repeatedly inside a scheduled job — and the P100's time reply,
    which the author suspects prevents the device leaving the network, never goes out.
  - Fix: `def tz = location.timeZone; int tzOffsetSec = tz ? tz.rawOffset.intdiv(1000) : 0` and the
    same for DST, with a `logWarn` when it is null so the cause is visible in the log.
  - Verification: `logInfo "Sending Time cluster reply: …"` must still appear after a heartbeat.
    The null case cannot be reproduced on a properly configured hub — a code read is sufficient.
  - Confidence: high on the logic; medium that any real user hits it.

- [ ] **A5. Unused `valueHex` local in `parseAqaraClusterFCC0()` — dead, and NPE-prone**
  - Location: line 311:
    `String valueHex = description.split(",").find { it.split(":")[0].trim() == "value" }?.split(":")[1].trim()`.
  - Problem: the `?.` guards the `.split(":")` call but **not** the following `[1]` index, so if the
    description has no `value:` segment this is `null[1]` → NPE. And the variable is **never read** —
    every case in the switch uses `it.value` or `value` instead. (Grep-verified: `valueHex` inside
    this function has exactly one occurrence, its own declaration.)
  - Fix: delete the line. That removes the hazard and the dead local in one edit.
  - Verification: the file still compiles on the hub; FCC0 reports parse unchanged.
  - Confidence: high (the variable being unused is certain; the NPE is latent).

- [ ] **A6. `decodeAqaraStruct()` can throw `StringIndexOutOfBoundsException` on a truncated
      trailing TLV**
  - Location: `decodeAqaraStruct()`, line 569. The loop guard is `i < (MsgLength-3)`, but the 32-bit
    branch (dataTypes `0x0B`/`0x1B`/`0x23`/`0x2B`) reads up to `valueHex[i+11]`, and the `0x24` LQI log
    slices up to `[i+14]`. A payload whose last triplet is cut short satisfies the guard and then
    indexes past the end of the string.
  - Failure scenario: a malformed or truncated `0x00F7` heartbeat. The whole report is lost, including
    the battery voltage it carries.
  - Fix: either wrap the body in a try/catch that logs the payload and returns, or check the
    remaining length per data type before slicing. This is an inherited house pattern (the P1 driver
    is identical), so **coordinate with kkossev before changing the loop shape** — a fix here is a
    candidate for the shared pattern across several Aqara drivers.
  - Verification: needs a captured malformed payload; otherwise code-read only.
  - Confidence: high on the arithmetic; low on real-world frequency.

---

## B — Functional / logic bugs

- [ ] **B1. `powerSource` is never populated in any released build**
  - Location: `powerSourceEvent()` (line 1215) is called from exactly one place —
    `initializeVars()` line 1154, inside `if (fullInit == true)`.
  - Problem: `initializeVars(true)` is reachable only from `configure(fullInit = true)`, which is
    called only by `initialize()` (line 1125) — and the `initialize` **command is declared inside
    `if (_DEBUG)`** (line 119). `_DEBUG` is `false` in every release. The `configure` command passes
    `fullInit = false`.
  - Failure scenario: the driver declares `capability "PowerSource"`, so every user has a
    `powerSource` attribute that stays empty forever. Any rule or dashboard tile that reads it sees
    null.
  - Fix: call `powerSourceEvent()` unconditionally in `initializeVars()` (it is a constant `battery`
    for this device, so there is nothing to preserve), or from `installed()`. Simplest is to move
    line 1154 out of the `if (fullInit == true)` block.
  - Verification: on-hub, press *Configure* — `powerSource` must read `battery` on the device page.
  - Confidence: high (pure reachability, grep-verified).

- [ ] **B2. `reportInterval` accepts 1–4 seconds in the UI but silently writes nothing**
  - Location: preference declaration line 134 — `range: "1..300"`, description "How often the device
    reports state (1-300 seconds)"; guard in `updated()` line 1016 — `if (val >= 5 && val <= 300)`.
    The `ATTR_REPORT_INTERVAL` constant comment (line 56) says "UINT32: 5-300 seconds".
  - Failure scenario: user sets 2 s and saves. `hasParamChanged` is true, the range guard fails, no
    command is sent, and **nothing is logged** — `updated()` then reports "no preferences were
    changed that require configuration commands to be sent", which is actively misleading. The
    preference keeps the invalid value, so it retries and fails again on every subsequent save.
  - Fix: change the preference to `range: "5..300"` and the description to "5-300 seconds" (the
    constant comment and the device's real limit are the authority). Optionally add a `logWarn` on
    the out-of-range branch.
  - Verification: the preference page must reject 4 and accept 5.
  - Confidence: high.

- [ ] **B3. ASK USER — `configure()` never sends the P100 registration handshake**
  - Location: `configure()` lines 1109–1123. It calls `unschedule()`, `initializeVars(fullInit)`,
    schedules the health check, 4× `sendTimeSync`, and `aqaraReadAttributes` at +30 s — but **not
    `aqaraBlackMagic()`**. `aqaraBlackMagic()` is called only from `installed()` (line 1163) and from
    ZDO `0x0013` device announcement (line 806).
  - Concern: the FCC0/`0x00FF` registration write inside `aqaraBlackMagic()` is the mechanism the
    driver's own comment suspects of preventing the device leaving the network after ~24 h. A user who
    switches an already-paired device to this driver presses *Configure* and never gets that write —
    the handshake only happens if the device happens to rejoin and send a `0x0013`.
  - Note both sides: the P1 driver's `configure()` behaves the same way, so this may simply be
    inherited from the template rather than a decision. But the P100 depends on the write in a way the
    P1 does not.
  - Action: **ask kkossev** whether `configure()` should call `aqaraBlackMagic()`. Do not add it
    unilaterally — it changes what *Configure* does on every user's device, and for a sleepy device
    a write sent while asleep is wasted anyway.
  - Confidence: high that the gap exists; intent unknown.

- [ ] **B4. ASK USER — FCC0 `0x0018` still emits the device-reported battery percentage, contradicting
      the v0.1.6 "voltage only" decision**
  - Location: two paths disagree.
    - `parseAqaraClusterFCC0()` case `"0018"`, line 337–339: `sendBatteryEvent(value)` — sends the
      device's own percentage straight through.
    - `decodeAqaraStruct()` TLV tag `0x18`, lines 591–592: `sendBatteryEvent(rawValue)` is
      **commented out**, with the note "(ignored; using voltage-based calculation)".
    - Header history v0.1.6: "battery level is derived from voltage only".
  - Failure scenario: the device reports both. The `battery` attribute then alternates between the
    firmware's percentage and the driver's 2.5–3.0 V linear derivation, which for Aqara devices
    typically disagree by a wide margin — and because both send `isStateChange: true`, every
    disagreement is a logged event. Users see the battery level jumping.
  - Action: **ask kkossev** which policy is intended. Per root AGENTS.md §2 rule 6, code contradicting
    its own changelog is an ASK USER, not a unilateral fix. If "voltage only" stands, comment out or
    log-and-ignore the `0x0018` case to match tag `0x18`.
  - Confidence: high that the two paths contradict each other and the changelog.

- [ ] **B5. The health check is never scheduled at install time**
  - Location: `installed()` lines 1159–1163 calls only `sendHealthStatusEvent("unknown")` and
    `aqaraBlackMagic()`. `deviceHealthCheck` is first scheduled in `updated()` (line 990) and
    `configure()` (line 1113).
  - Failure scenario: a freshly paired device that the user never saves preferences on has no
    health-check job, so `healthStatus` stays `unknown` forever and the device is never marked
    `offline` when it dies. The `Health Check` capability is declared, so this is visible surface.
  - Fix: add `runIn(DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])`
    to `installed()`, or call `initializeVars(true)` there (which is the more thorough fix and would
    also resolve B1 — but changes more, so keep them separate).
  - Verification: pair a device, do not save preferences, and check *Scheduled Jobs* on the device
    page for `deviceHealthCheck`.
  - Confidence: high.

- [ ] **B6. Mode-mismatched events are dropped at `logDebug` level only**
  - Location: `parseContactEvent()` line 509 and `parseActionEvent()` line 531 — each returns with a
    `logDebug` when `getDeviceMode()` disagrees with the event's mode.
  - Failure scenario: `getDeviceMode()` falls back to `"object"` whenever the `deviceMode` attribute
    is null and the preference is unset — i.e. on a fresh install before FCC0 `0x0116` has been read
    back. A device physically configured for door/window use has its `contact` reports **silently
    discarded**, and with debug logging off there is no trace at all. From the user's point of view
    the driver simply does not report the door.
  - Fix: make the mismatch visible — `logWarn` (which is gated by `logEnable`, so consider
    `sendInfoEvent` for a one-shot user-facing hint) plus a `refresh()`-style read of `0x0116` so the
    driver learns the real mode instead of guessing. A minimal fix is just to raise the log level.
  - Verification: put a device in door_window mode, remove the `deviceMode` state, open the door —
    the log must say why the event was ignored.
  - Confidence: high on the mechanism; the practical impact depends on how reliably `0x0116` is read
    at pairing (`aqaraBlackMagic()` does read it first, which mitigates it).

- [ ] **B7. `updated()` clears the other mode's attributes before the mode write is acknowledged**
  - Location: `updated()` line 996: `clearStaleModeAttributes(settings.deviceMode)` runs immediately,
    *then* the `0x0116` write is queued. Compare `parse()` case `"0116"` (lines 346–348), which calls
    it only after the device confirms the change.
  - Failure scenario: on a sleepy device the write is very likely to be lost. The user changes the
    mode preference, the driver deletes `contact` (or `lastAction`/`orientation`/`acceleration`), the
    write never lands, and the device carries on in the old mode — but its attributes are gone and
    the preference now disagrees with the hardware.
  - Fix: remove the call from `updated()` and rely on the `parse()` case `"0116"` path, which is
    already correct and already handles the previous-mode comparison.
  - Verification: change the mode preference while the device is asleep — the existing attributes
    must survive until the device actually reports the new mode.
  - Confidence: high on the ordering; medium on how often the write is lost.

- [ ] **B8. `state.pingTime` is never cleared, so a late Basic report publishes a bogus `rtt`**
  - Location: `ping()` line 1249 sets `state.pingTime`; nothing ever clears it —
    `deviceCommandTimeout()` (line 1276) only logs, and `sendRttEvent()` does not reset it.
  - Failure scenario: `ping()` on a sleepy P100 usually times out. Minutes or hours later the device
    wakes and sends an unsolicited Basic `0x0001` report; `parse()` calls `sendRttEvent()`, which
    computes `now - pingTime` and publishes an `rtt` of, say, 3600000 ms as if it were a round-trip
    time.
  - Fix: `state.remove('pingTime')` (or set null) both in `deviceCommandTimeout()` and at the end of
    `sendRttEvent()`. Combines naturally with A1 — the null guard added there makes the cleared state
    the correct "no ping pending" signal.
  - Verification: `ping()` a sleeping device, wait for the timeout, then wake it — no `rtt` event
    should be emitted.
  - Confidence: high.

- [ ] **B9. VERIFY ON DEVICE — cluster `0x000C` (analogInput) is in the fingerprint but has no handler**
  - Location: the fingerprint (line 124) declares `inClusters:"0101,000C,0006,0003,0000,FCC0"`. There
    is no `0x000C` branch anywhere in `parse()`, so any such report lands in the final
    "Unprocessed attribute report" `logDebug`.
  - Concern: on older `lumi.vibration.*` devices (e.g. `lumi.vibration.aq1`) attribute `0x000C:0x0055`
    (present value) carries **vibration strength** and **tilt angle** — data this driver currently
    exposes nothing equivalent to. If the P100 reports it, there is a real feature sitting unused;
    if it does not, the cluster is vestigial in Aqara's endpoint declaration.
  - Action: capture a debug log while shaking and tilting the device and search for
    `cluster=000C`. Only then decide whether to add handling. Do **not** add speculative parsing or
    a new attribute — a new attribute name is permanent public surface (root AGENTS.md §10).
  - Confidence: high that it is unhandled; unknown whether the device uses it.

---

## C — Minor issues, inconsistencies, dead code

- [ ] **C1. Dead functions** (all grep-verified as having exactly one occurrence — their own
  definition — in the file):
  `parseAqaraAttributeFF02` (710), `parseBatteryFF02` (742), `sendVoltageEvent` (784),
  `pollPresence` (1231), `clearParamStorage` (205), `isVirtualParam` (156), `safeToDouble` (1332),
  `isCompatible` (1359), `getModel` (1355 — called only by `isCompatible`).
  `parseBatteryFF02` is a **byte-identical duplicate** of `parseBatteryFF01`. Remove, or annotate the
  ones deliberately kept as template scaffolding. See also C14 — `parseAqaraAttributeFF02` is dead
  only because nothing routes FF02 to it.

- [ ] **C2. `getModel()` is broken as well as dead**
  - Location: `getModel()` line 1355, `isCompatible()` line 1371. The success path `try { String model = getHubVersion() }` has **no
    `return`**, so the method falls through and returns null even when it works. In the `catch`,
    `model = res.data.device.modelName` assigns to an identifier that is out of scope (the `try`
    block's `model` is block-scoped), and the `return model` inside the closure returns from the
    closure, not the method. `isCompatible()` would then call `.split('-')` on null.
  - Both functions are unreferenced, so nothing throws today. Delete both, or fix `getModel()` to
    `return getHubVersion()` if kkossev wants to keep the helper for future use.
  - Confidence: high.

- [ ] **C3. Dead constants and an empty list** — declared but never referenced (the call sites use
  literals instead): `CLUSTER_AQARA_FCC0` (42, code writes `0xFCC0`), `CLUSTER_DOOR_LOCK` (64),
  `ATTR_ACTION` (65), `ATTR_BATTERY_VOLTAGE` (49), `ATTR_BATTERY_PCT` (50) — the last two are shadowed
  by the string literals `"0017"` / `"0018"` in the switch. `VIRTUAL_PARAMS` (91) is an empty list
  with a single dead reader (`isVirtualParam`, C1). Either use the constants or drop them; the
  half-and-half state is the trap, because a reader assumes `ATTR_BATTERY_VOLTAGE` is authoritative.

- [ ] **C4. Unused imports** — `hubitat.device.HubAction` (32) and `java.math.RoundingMode` (37).
  `hubitat.device.Protocol` (33) is also nominally unused, since every call site writes
  `hubitat.device.Protocol.ZIGBEE` fully qualified — harmless, leave it if it matches the P1 header.

- [ ] **C5. `orientation` can emit `"unknown"`, which is not in its declared enum**
  - Location: `orientationMap` (line 78) maps key `0` to `"unknown"`; the attribute is declared
    `attribute 'orientation', 'enum', ['face_up', 'face_down', 'vertical', 'tilt']` (line 109). Case
    `"01F1"` (line 431) treats any non-null map hit as valid, so a device report of 0 emits an
    out-of-enum value.
  - Fix: either add `'unknown'` to the declared enum (public surface change — ask), or skip the event
    for value 0 and just `logDebug`. Same class as the P1 driver's C15.
  - Note the inconsistency beside it: unknown *orientation* logs at `logDebug` while unknown
    *device_mode*, *door_window_type* and *device_posture* log at `logWarn`.

- [ ] **C6. `decodeAqaraStruct()` does not sign-extend ZCL signed types**
  - Location: lines 580–585 lump dataType `0x28` (signed 8-bit) in with the unsigned types; there is
    no `0x29` (signed 16-bit) case at all, so it falls to the "unknown dataType" branch.
  - Impact here is **log-only**: the one signed consumer is tag `0x03` (device temperature, line 590),
    which this driver only `logDebug`s — it does not emit an event, unlike the P1 driver. So a
    sub-zero chip temperature logs as ~+246 °C and nothing else happens.
  - Fix: `if (dataType == 0x28 && rawValue > 127) { rawValue -= 256 }`.

- [ ] **C7. Off-by-one in the LQI log slice** — line 668:
  `valueHex[(i+4)..(i+14)]` is **11** hex characters for a 5-byte (10-character) value, so the log
  includes the first nibble of the next tag. Should be `(i+4)..(i+13)`. Log-only.

- [ ] **C8. Firmware-version decode overlaps its own bit fields** — lines 651–653:
  `minor = (rawValue >> 16) & 0xFF` and `patch = rawValue & 0xFFFF` share a byte, so `minor` is also
  the high half of `patch`. Log-only (no `aqaraVersion` is derived from it — that comes from the
  `application` data value in `updateAqaraVersion()`). Worth cross-checking against
  zigbee-herdsman-converters before changing, since the correct Aqara encoding is not obvious.

- [ ] **C9. Raw `log.info` where the `logInfo` helper exists** — `parseSimpleDescriptorResponse()`
  lines 903, 912, 926. These three are unguarded and unconditional, so they log even with both
  logging preferences off. Convert to `logInfo`.
  **Leave the other raw `log.*` calls alone** — `configure` (1110), `initialize` (1126),
  `installed` (1160), `sendHealthStatusEvent` (1265/1267) and the LUMI-LEAVE `log.warn` (330) are
  deliberate house style, matched in the P1 driver.

- [x] **C10. The community-thread URL was a literal placeholder** — line 4 now points at the real
  release thread (<https://community.hubitat.com/t/release-aqara-multi-state-sensor-p100-zigbee-driver-c8-only/163540>),
  matched by `communityLink` in `packageManifest.json` and by the README. Fixed 2026-08-17.

- [x] **C11. Release hygiene** — `packageManifest.json` was created on 2026-08-17 (version `0.1.6`)
  and the package was added to the repository catalog `repository.json`. The folder still has no
  `.groovylintrc.json` or `.code-workspace`; neither is needed (there are no `#include` targets).
  **Never file or "fix" a manifest version lag from here on** — kkossev raises the HPM manifest
  deliberately and rarely, and a manifest trailing the driver version is the intended state
  (see [PUBLISHING.md](../../PUBLISHING.md), *HPM manifest version policy*).

- [ ] **C12. `deviceMode` naming collision** — it is simultaneously a preference (line 131), an
  attribute (line 108), and a getter-shaped method `String getDeviceMode()` (line 214). Groovy
  resolves a bare `deviceMode` to the method. Nothing is broken today because every call site is
  explicit, but it is a live trap for anyone editing nearby. Document rather than rename —
  both the preference and the attribute are public surface.

- [ ] **C13. `parseZHAcommand` case `"09"` is unreachable and unguarded** — lines 873–882 handle a
  Read Reporting Configuration Response, but the driver never sends a read-reporting-config command
  (grep-verified: no `readReportingConfiguration` and no `he rattr`-style config read anywhere). If
  it is ever wired up, note there is no size check before `descMap.data[8]`. Keep as scaffolding or
  remove; do not add the guard to dead code without also adding the caller.

- [ ] **C14. Cluster `0x0000` attribute `FF02` has no route** — `parse()` handles `FF01` (line 287)
  but not `FF02`, so an FF02 report falls to the final "Unprocessed attribute report" `logDebug`
  even though `parseAqaraAttributeFF02()` exists to handle it (C1). Either wire it up or delete the
  pair. Low priority — the P100 reports its TLV on FCC0 `0x00F7`, so FF01/FF02 are legacy-Xiaomi
  paths that may never fire on this device.

- [ ] **C15. Misleading comment in `parseContactEvent()`** — line 508 says "P100 uses inverted logic:
  onOff=0 means closed, onOff=1 means open", and the code maps `0 → closed`. That is the **standard**
  Zigbee/Z2M mapping, not an inverted one. The code is right; the word "inverted" is wrong and will
  make the next reader "fix" working code. Reword the comment only.

- [ ] **C16. `configure()` cancels the debug-logging auto-off timer** — `unschedule()` at line 1111
  clears every scheduled job, including the 24-hour `logsOff` armed by `updated()` (line 983).
  After a *Configure*, debug logging stays on indefinitely. Fix: re-arm `logsOff` in `configure()`
  when `logEnable` is true, or unschedule selectively.

- [ ] **C17. `parse()` mixes `it.cluster` and `descMap.cluster` in one routing chain** — lines
  253–290: the early branches test `it.cluster`, then the FCC0 and FF01 branches test
  `descMap.cluster`. Equivalent today, because every `attrData` entry copies `descMap.cluster`
  verbatim (lines 245–247), so this is cosmetic — but it is fragile if the fan-out ever carries a
  per-entry cluster. Normalise to `it.cluster`.

- [ ] **C18. Basic `0x0004` is logged as "device model"** — `parse()` line 272:
  `logInfo "(parse) device model is ${it.value}"`. ZCL Basic attribute `0x0004` is
  **ManufacturerName**; `0x0005` is ModelIdentifier. Log text only, but it will mislead anyone
  debugging a fingerprint from the logs. Reword to "manufacturer name".
  Related oddity, not a defect: `aqaraBlackMagic()` reads `[0x0004, 0x0005]` together, and the
  `0x0005` **response** is then reported by the `0x0005` handler as "Button was pressed" — so every
  initialization logs a phantom button press. That handler is the correct Aqara convention (Aqara
  devices genuinely signal a button press with a `0x0005` report), so leave it; just be aware of the
  false positive when reading an init log.

---

## Suggested fix order (advisory only — kkossev picks the actual order)

1. **Batch 1 — safe, self-contained, no behaviour redesign:**
   A1 (+B8, they share a line), A2, A5, B2, C4, C5, C7, C9, C15, C16.
2. **Batch 2 — needs care, touches control flow:**
   A3, A4, B1, B5, B6, B7, C1, C2, C3, C6, C8, C13, C14, C17, C18.
3. **Batch 3 — gated on kkossev's answer or on a real device log:**
   B3, B4, B9, A6 (shared house pattern — coordinate across the Aqara drivers), C12.

General rules: keep each fix minimal, keep the kkossev logging style, and **never touch the
deliberately disabled blocks** — the `zdo bind` lines and the `runIn(342, "sendTimeSync")` in
`aqaraBlackMagic()`/the `00DF` case, the commented-out `sendBatteryEvent` at TLV tag `0x18`, the
ignored `01F3` attribute, and the log-only ZDO `0002`/`0036` cases (see [AGENTS.md](AGENTS.md) §10).
Above all, do not rewrite the `he raw` FCC0/`0x00FF` registration write in "cleaner" Groovy — the
explicit octet-string length byte is the whole point of it.
