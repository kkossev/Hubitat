# TODO â€” Tuya Zigbee Metering Plug

Forum audit performed **2026-07-11** against driver **v2.0.3** (`timeStamp 2026/04/20`).
Source topic: [Hubitat Community topic 86465](https://community.hubitat.com/t/-/86465/1).

The Discourse API crawl covered all **651 visible posts** in the topic stream, from post 1 through
post 658 (seven post numbers are deleted or otherwise unavailable). The newest visible post is
post 658, dated **2026-02-05**. Candidates were checked against the production driver, its revision
history, `README.MD`, and `BUGS.md`.

Additional topic audit performed **2026-08-26** against driver **v2.0.5** (`timeStamp 2026/08/26 09:23 PM`).
Source topic: [Hubitat Community topic 165245](https://community.hubitat.com/t/-/165245/1).
The complete topic stream contains **10 visible posts**, from post 1 through post 10; the newest
post is post 10, dated **2026-08-26**.

This file is for forum-derived feature/device work. Confirmed code defects already recorded in
`BUGS.md` are referenced rather than duplicated.

Status legend: `[ ]` **OPEN**, `[?]` **NEEDS_EVIDENCE**. Resolved, declined, out-of-scope, and
device-limitation findings are retained in the final audit section.

---

## Open backlog

### 1. [ ] Add the confirmed-compatible NOUS plug fingerprint `_TZ3210_ddigca5n`

- Reporter: @tim.ocallag, [request and initial results](https://community.hubitat.com/t/-/86465/646),
  [successful fingerprint test](https://community.hubitat.com/t/-/86465/648).
- Exact pair: model `TS011F`, manufacturer `_TZ3210_ddigca5n`.
- Confirmed fingerprint: endpoint `01`, profile `0104`, in-clusters
  `0003,0004,0005,0006,0702,0B04,E000,E001,0000`, out-clusters `0019,000A`.
- The reporter added only this fingerprint and confirmed the device then worked. The identifier is
  absent from v2.0.3, so this appears to need auto-detection only, not a new measurement profile.
- Verification: pair or re-pair one device and confirm automatic driver selection plus switch,
  power, current, voltage, and energy behavior.

### 2. [ ] Add Sercomm SZ-ESW02 / SZ-ESW02N-CZ3 support with the correct power scaling

- GitHub issue: https://github.com/kkossev/Hubitat/issues/123.
- Reporter: @Rxich, [fingerprint and inaccurate-reading report](https://community.hubitat.com/t/-/86465/653),
  [follow-up](https://community.hubitat.com/t/-/86465/655). The maintainer explicitly registered
  this as a support issue in [post 654](https://community.hubitat.com/t/-/86465/654).
- Reported Hubitat pair: model `SZ-ESW02`, manufacturer `Sercomm Corp.`, endpoint `01`, profile
  `0104`, in-clusters `0000,0003,0004,0005,0006,0702,0B05`, out-cluster `0019`; endpoint `02`
  exposes `0000,0003` in and `0003,0006` out.
- The same hardware appears as `SZ-ESW02N-CZ3` in Zigbee2MQTT and reports plausible values there.
  Hubitat custom and stock drivers reportedly show power roughly 1000Ã— too high. Treat `/1000` as
  a hypothesis until raw `0x0702:0x0400` reports are compared with a reference meter.
- Likely areas: add fingerprint/model detection, extend `isSercomm()`, and add a model-specific
  instantaneous-demand divisor without changing the existing SZ-ESW01 families.
- Verification: known resistive loads at several wattages, energy accumulation, on/off, re-pairing,
  and endpoint behavior.

### 3. [ ] Add Zemismart SPM01 variant `_TZE284_iwn0gpzz`

- GitHub issue: https://github.com/kkossev/Hubitat/issues/128.
- Reporter: @gmanor77, [request](https://community.hubitat.com/t/-/86465/656); maintainer accepted it
  in principle but deferred it in [post 657](https://community.hubitat.com/t/-/86465/657).
- Known pair: model `TS0601`, manufacturer `_TZE284_iwn0gpzz`.
- The device paired as the unrelated Tuya Zigbee Valve driver, confirming that v2.0.3 lacks its
  fingerprint. Existing `isSPM01()` recognizes only `_TZE200_bcusnqt8` and `_TZE200_qhlxve78`.
- Obtain the full fingerprint and EF00 debug reports, then compare DP IDs/scaling with the existing
  SPM01 path before adding the manufacturer to `isSPM01()`.
- Verification: voltage, current, power, frequency, energy, switch suppression, and sustained
  reporting under a known load.

### 4. [ ] Implement `dailyEnergy`

- Requested by @iEnam and accepted for the TODO in
  [post 229](https://community.hubitat.com/t/-/86465/229); the maintainer reiterated it remains
  pending in [post 403](https://community.hubitat.com/t/-/86465/403).
- v2.0.3 still has only a commented `dailyEnergy` attribute declaration. Despite the method name
  `scheduleHourlyAndDailyEnergy()`, it schedules only `hourlyEnergyEvent()`.
- Define reset/time-zone semantics explicitly (calendar day in hub local time), persist the prior
  daily baseline, and decide how resets, `setEnergy()`, hub restarts, and offline periods affect the
  first daily event.
- Keep this separate from the hourly-accounting defects already tracked as `BUGS.md` B6/B12.

### 5. [ ] Add daily/hourly energy-cost aggregation after daily energy semantics are settled

- The maintainer proposed `hourlyCost` and later `dailyCost` in
  [post 194](https://community.hubitat.com/t/-/86465/194), in the context of time-varying tariffs.
- No such attributes or events exist in v2.0.3. Implement only after item 4 and the energy reset
  accounting in `BUGS.md` B6 are resolved, otherwise boundary/reset errors will be inherited.
- Decide whether cost buckets use the rate active for each incremental energy report (preferred for
  changing tariffs) or a single rate at the bucket boundary.

### 6. [ ] Resolve EMIZB-141 pulse configuration/scaling instead of relying on the 1000-pulse workaround

- Reporter: @teet.niin. The device is identified in
  [posts 633â€“635](https://community.hubitat.com/t/-/86465/635); testing showed that a real
  400 pulses/kWh meter reads 2.5Ã— high when configured as 400 but reads correctly when incorrectly
  configured as 1000 ([post 641](https://community.hubitat.com/t/-/86465/641)). The workaround was
  accepted temporarily in [post 644](https://community.hubitat.com/t/-/86465/644).
- v2.0.3 contains an unconditional EMIZB-141 2.5Ã— multiplier as well as pulse-configuration writes.
  Determine whether Hubitat already applies the device multiplier/divisor and whether the custom
  multiplier is double-correcting values.
- Coordinate this with `BUGS.md` B9 (contradictory accepted pulse ranges); do not create a second
  independent fix.
- Verification matrix: physical meters at 400 and 1000 pulses/kWh, read-back of `0x0702:0x0300`,
  instantaneous demand, and cumulative energy against a known load.

### 7. [ ] Decide whether to replace/refactor the monolithic driver before expanding device families

- The maintainer stated that the driver needs refactoring or replacement in
  [post 403](https://community.hubitat.com/t/-/86465/403), and later described it as near the limit
  of device variants in [post 529](https://community.hubitat.com/t/-/86465/529).
- This is an architectural decision, not permission for a broad rewrite. First define the supported
  scope: single-channel plugs/meters only, or also multi-channel/multi-endpoint devices.
- Preserve existing fingerprints, model-specific divisors, energy-reset behavior, HPM migration,
  and healthStatus semantics. Add fixture-driven parsing tests from sanitized forum logs before
  moving model logic.

---

## Upstream `tuya.ts` device gap audit

Compared **2026-07-11** with upstream [`tuya.ts`](https://github.com/Koenkk/zigbee-herdsman-converters/blob/f478b1dcea7190f2972cf836d15b644b8c23c61f/src/devices/tuya.ts) at commit `f478b1dcea7190f2972cf836d15b644b8c23c61f`. Capture the complete Hubitat endpoint/profile/cluster fingerprint before adding each metadata line; upstream establishes the exact model/manufacturer pair and converter family but does not always store the descriptor.

### Add a fingerprint only — covered by an existing single-channel group

- **TS011F smart plugs** — model `TS011F`; manufacturers `_TZ3000_0zfrhq4i`, `_TZ3000_266azbg3`, `_TZ3000_2uollq9d`, `_TZ3000_4ux0ondb`, `_TZ3000_amdymr7l`, `_TZ3000_b28wrpvx`, `_TZ3000_cicwjqth`, `_TZ3000_cjrngdr3`, `_TZ3000_ko6v90pg`, `_TZ3000_ss98ec5d`, `_TZ3000_y4ona9me`, `_TZ3000_yujkchbz`, `_TZ3210_2uollq9d`, `_TZ3210_4ux0ondb`, `_TZ3210_5ct6e7ye`, `_TZ3210_cjrngdr3`, `_TZ3210_jlf1nepw`, `_TZ3210_rwmitwj4`, `_TZ3210_w0qqde0g`, `_TZ3210_zifx0xoj`. These use the existing standard TS011F ZCL measurement path; polling covers variants which reject reporting configuration. [Upstream group](https://github.com/Koenkk/zigbee-herdsman-converters/blob/f478b1dcea7190f2972cf836d15b644b8c23c61f/src/devices/tuya.ts#L10368-L10584).
- **TS0001 metering switches** — model `TS0001`; manufacturers `_TZ3000_0ghwhypc`, `_TZ3000_1adss9de`, `_TZ3000_g92baclx`, `_TZ3000_gsat0axs`, `_TZ3000_hzlsaltw`, `_TZ3000_iktiy8ue`, `_TZ3000_ikuxinvo`, `_TZ3000_jsfzkftc`, `_TZ3000_q8r0bbvy`, `_TZ3000_qaabwu5c`, `_TZ3000_qlai3277`, `_TZ3000_qorepo2x`, `_TZ3000_x8mbwtsz`, `_TZ3000_zojh9vz7`. Six siblings are already local; these use the same endpoint-1 On/Off, Electrical Measurement and Metering clusters. [Upstream group](https://github.com/Koenkk/zigbee-herdsman-converters/blob/f478b1dcea7190f2972cf836d15b644b8c23c61f/src/devices/tuya.ts#L7169-L7260).
- **TS011F DIN relays** — model `TS011F`; manufacturers `_TZ3210_vbfp8eyv`, `_TZ3000_2iiimqs9`, `_TZ3000_6l1pjfqe`, `_TZ3000_viqwamhn`. Their scaling matches the existing `_TZ3000_8bxrzyxz`, `_TZ3000_qeuvnohg`, and `_TZ3000_ky0fq4ho` paths. [Upstream groups](https://github.com/Koenkk/zigbee-herdsman-converters/blob/f478b1dcea7190f2972cf836d15b644b8c23c61f/src/devices/tuya.ts#L12853-L12934).
- **Aubess WHD02** — model `TS000F`, manufacturer `_TZ3000_xkap8wtb`; a single-endpoint ZCL metering switch with the TS0001 group's divisors. [Upstream definition](https://github.com/Koenkk/zigbee-herdsman-converters/blob/f478b1dcea7190f2972cf836d15b644b8c23c61f/src/devices/tuya.ts#L7295-L7332).

Do not duplicate `_TZ3210_ddigca5n` (item 1), `_TZ3000_3ias4w4o` (item 11 needs scaling/protection evidence), or `_TZ3000_2putqrmw` (recorded below as a Hubitat/device limitation).

### New group / requires more complex implementation

- **TS0002 two-channel metering switches** — model `TS0002`; manufacturers `_TZ3000_aaifmpuq`, `_TZ3000_huvxrx4i`, `_TZ3000_irrmjcgi`, `_TZ3000_pxfjrzyj`. Endpoint 1 owns the shared meter and endpoint 2 the second switch, so support needs component/child switch routing. [Upstream group](https://github.com/Koenkk/zigbee-herdsman-converters/blob/f478b1dcea7190f2972cf836d15b644b8c23c61f/src/devices/tuya.ts#L7263-L7294).
- **TS011F two-gang socket with USB** — model `TS011F`, manufacturer `_TZ3000_bep7ccew`. Measurements are shared while switches use endpoints 1 and 2; it needs the same child-device decision as TS0002. [Upstream definition](https://github.com/Koenkk/zigbee-herdsman-converters/blob/f478b1dcea7190f2972cf836d15b644b8c23c61f/src/devices/tuya.ts#L6610-L6624).
- **Additional TS011F threshold/breaker variants** — model `TS011F`; manufacturers `_TZ3000_303avxxt`, `_TZ3000_ibefeicf`, `_TZ3000_yi0n4xfd`, `_TZ3000_zjchz7pd`, `_TZ3000_zrm3oxsh`, `_TZ3000_zv6x8bt2`. Add them to the Tongou SY2 predicate and verify E001 thresholds, child lock, countdown/indicator behavior, and per-model temperature exclusions; a fingerprint alone is insufficient. [Upstream group](https://github.com/Koenkk/zigbee-herdsman-converters/blob/f478b1dcea7190f2972cf836d15b644b8c23c61f/src/devices/tuya.ts#L15529-L15626).
- **TS3008 non-default scaling** — model `TS011F`; manufacturers `_TZ3008_reatplte`, `_TZ3008_1a8m8wd6`. Upstream reads Electrical Measurement multipliers/divisors because fixed TS011F scaling is wrong. Cache those attributes with safe defaults, or establish verified manufacturer-specific divisors, before adding fingerprints. [Upstream note](https://github.com/Koenkk/zigbee-herdsman-converters/blob/f478b1dcea7190f2972cf836d15b644b8c23c61f/src/devices/tuya.ts#L10465-L10491).

Verification: confirm automatic driver selection, switch, power, current, voltage, cumulative energy, restart recovery, and reporting-versus-polling against a known resistive load. For multi-endpoint devices, independently test commands and events on every endpoint.

---
## Needs evidence before implementation

### 8. [?] Shelly Plug US Gen4 `S4PL-00116US`

- The maintainer noted that its fingerprint is absent in
  [post 650](https://community.hubitat.com/t/-/86465/650). The reporter paired it, but posted only
  ZDO logs rather than the Device-driver `Get Info` fingerprint
  ([posts 651â€“652](https://community.hubitat.com/t/-/86465/652)).
- Required: complete model/manufacturer, endpoints, profile, in/out clusters, application/build,
  plus reports for switch and measurements. Do not assume it matches the supported Shelly `1PM`.

### 9. [?] Zemismart SPM02 `_TZE284_dikb3dp6` (three phase)

- Reporter: @Bawt818, [fingerprint and incorrect-values report](https://community.hubitat.com/t/-/86465/628),
  [failed fingerprint-only test](https://community.hubitat.com/t/-/86465/630).
- Known pair: `TS0601` / `_TZE284_dikb3dp6`, endpoint `01`, in-clusters
  `0000,0004,0005,EF00,0000,ED00`, out-clusters `0019,000A`.
- A fingerprint alone produces null/nonsensical readings. Obtain full EF00/ED00 logs and the current
  Zigbee2MQTT converter mapping. Decide whether single-channel compatibility is acceptable or route
  the device to a dedicated multi-channel driver; the current driver cannot represent three phases.

### 10. [?] `_TZ3210_xej4kukg` intermittent failure to turn on

- Reporter: @jlv, [fingerprint and reproducible symptom](https://community.hubitat.com/t/-/86465/621).
  The same device reportedly works with Generic Zigbee Outlet; the maintainer requested a hardware
  swap test in [posts 623â€“625](https://community.hubitat.com/t/-/86465/625), but no result followed.
- Required: swap-test result from both physical units, platform version outside beta, debug/Zigbee
  logs for successful and failed commands, ACK/default-response behavior, and effect of the
  platform's advanced Zigbee option.

### 11. [?] `_TZ3000_3ias4w4o` reports ~13 kW and shuts off under ordinary loads

- Reporter: @vito90, [full fingerprint and two-device reproduction](https://community.hubitat.com/t/-/86465/609).
- Model `TS011F`; endpoint `01`; in-clusters `0003,0004,0005,0006,0702,0B04,E000,E001,0000`;
  out-clusters `0019,000A`.
- Both this driver and the stock driver behave the same, so firmware protection or a scaling
  attribute is at least as likely as a driver defect. Required: raw electrical-measurement values,
  multiplier/divisor attributes, protection attributes, actual load, and a reference-meter reading.

### 12. [?] Tongou `_TZ3000_qeuvnohg` apparent under-reporting

- Reporter: @user4286, [report and comparison graphs](https://community.hubitat.com/t/-/86465/627).
- No follow-up logs or controlled reference-load test were posted. Compare power and cumulative
  energy against a resistive reference load before changing divisors; downstream device totals are
  not a reliable calibration reference.
- Also resolve the ambiguous divisor expression separately tracked in `BUGS.md` B3 before making a
  model-family scaling change.

### 13. [?] Zemismart `SDM01-TZ0-12-ZM` three-phase, three-CT meter

- **Reported** in a private support request on 2026-08-15; the requester offered to purchase the
  120 A device and provide test information. Private correspondence and identity are not reproduced
  here.
- Required before implementation: the exact `model` / `manufacturer`, full endpoint and cluster
  fingerprint, and complete EF00/ED00 traffic while independently varying the load on each phase.
- This is related to item 9 but is not assumed to use the same datapoints or scaling. The current
  driver exposes one switch/power/current/voltage/energy channel and therefore cannot faithfully
  represent three CT channels. Decide whether to create a dedicated multi-channel/child-device
  driver after the evidence is captured; do not add a fingerprint to the existing single-channel
  profile as a speculative shortcut.

### 14. [?] Tuya SDM02T dual-channel monitor `TS0601 / _TZE284_x8diwkqb` — HUB-140

- Reporter/device owner: @justcliff, [initial device report and behavior](https://community.hubitat.com/t/-/165245/8),
  [request for Device Details](https://community.hubitat.com/t/-/165245/9), and
  [Device Details screenshot](https://community.hubitat.com/t/-/165245/10).
- **Confirmed target identity:** model `TS0601`, manufacturer `_TZE284_x8diwkqb`, endpoint `01`,
  application `4A`, firmware MT `1002-1602-0000004A`, in-clusters
  `0000,0004,0005,EF00,0000,ED00`, and out-clusters `0019,000A`. The Zigbee profile id is not
  visible in the supplied evidence and remains unknown.
- Keep this device separate from @chrisbvt's `TS0601 / _TZE284_81yrt3lo` in
  [post 7](https://community.hubitat.com/t/-/165245/7). Similar appearance and two CT inputs do
  not prove that the devices share fingerprints, firmware, or datapoints.
- **Reported:** driver v2.0.4 did not expose two independent channels and routed reports through
  unrelated generic branches, including DP 17 as forward energy, DP 102 as produced energy, and
  DP 117 as Hoch reverse active power.
- **External reference, not target-device capture:** the exact manufacturer/model pair is defined
  upstream as `SDM02V1-GT`, with packed L1/L2 measurements on DPs 6/7 and a datapoint map that
  conflicts with several current generic branches. Only `_TZE284_x8diwkqb` is in scope locally;
  upstream sibling manufacturers are not evidence of Hubitat compatibility.
- **Implemented unverified full support:** an exact-identity fingerprint (with profile id `0104`
  explicitly marked as inferred), guarded decoding for the complete upstream SDM02T datapoint map,
  parent total-meter events, L1/L2 Hubitat `Generic Component Metering Switch` children exposing
  power and energy, informational logs for unsupported per-line measurements, device-locating and
  update-frequency preferences, Tuya refresh queries, migration cleanup, and unknown-DP logging.
- **Clarified — negative total power/power factor is not a driver bug:** the 2026-08-28 log capture
  showed DP 29 (`total active power`) and DP 50 (`total power factor`) reading negative while both
  channels drew positive power. Per-channel packed voltage/current/power (DP 6/7) decoded correctly
  and matched V×I×PF sanity checks, so this is consistent with the total-power CT clamp being
  installed in reversed orientation on this specific device, not a decoding defect. No driver change
  needed for this point specifically. A separate, unrelated sign-decode bug (power factor read as
  unsigned instead of signed, producing values like `4294967274 %`) was found in the same log and
  fixed.
- **Required:** install the parent driver, initialize it, and capture pairing/configuration plus
  normal-reporting logs; verify values with known loads on CT1 only, CT2 only, and both inputs; test
  clamp swap/orientation, imported/exported energy, locating, a controlled DP 102 interval change,
  child power/energy and refresh, ignored child switch controls, unsupported-measurement info logs,
  and all unknown datapoints. Keep `[?]` until the device owner confirms these checks; this
  implementation remains **VERIFY ON DEVICE**.

---

## Already resolved, declined, limited, or out of scope

- **RESOLVED â€” session energy:** `resetEnergy` plus a Rule Machine trigger was confirmed usable in
  [posts 550â€“553](https://community.hubitat.com/t/-/86465/553). The separate `setEnergy()` request
  was implemented in v1.9.6 ([post 557](https://community.hubitat.com/t/-/86465/557)). Remaining
  reset-accounting bugs are in `BUGS.md` B6, not here.
- **RESOLVED â€” `_TZ3000_ww6drja5`:** requested in
  [post 602](https://community.hubitat.com/t/-/86465/602) and added in v2.0.0
  ([post 603](https://community.hubitat.com/t/-/86465/603)); present in v2.0.3.
- **RESOLVED â€” Third Reality energy divisor and Tongou temperature toggle:** fixed in
  [post 603](https://community.hubitat.com/t/-/86465/603) and
  [posts 502â€“508](https://community.hubitat.com/t/-/86465/508), respectively; both are present in
  current source.
- **DECLINED â€” generic user calibration coefficients:** rejected because the measurement error is
  not linear and the preference surface is already too complex
  ([post 279](https://community.hubitat.com/t/-/86465/279)). Do not reopen without model-specific,
  controlled calibration evidence.
- **DEVICE_LIMITATION â€” `_TZ3000_46t1rvdu`:** it lacks metering hardware; use a switch driver
  ([posts 394â€“395](https://community.hubitat.com/t/-/86465/395)).
- **DEVICE_LIMITATION â€” `_TZ3000_2putqrmw` and other problematic newer Tuya Zigbee 3.0 batches:**
  inability to stay connected was attributed to the device/Hubitat Zigbee combination, with no
  firmware rollback or driver fix available ([posts 490â€“495](https://community.hubitat.com/t/-/86465/495)).
- **OUT_OF_SCOPE â€” multi-endpoint double outlets** `_TZ3000_dd8wwzcy` and `_TZ3210_raqjcxo5`:
  the maintainer explicitly routed these to a dedicated Makegood double-GPO driver because this
  driver has no child-endpoint model ([post 618](https://community.hubitat.com/t/-/86465/618)).
- **OUT_OF_SCOPE â€” Aqara H2 socket:** Aqara-specific support was declined for this Tuya-oriented
  driver ([posts 604â€“605](https://community.hubitat.com/t/-/86465/605)).
- **OUT_OF_SCOPE / DEVICE LIMITATION â€” three-channel meters already listed for one-channel tests:**
  `_TZE200_ves1ycwx`, `_TZE200_v9hkz2yn`, and related variants require a dedicated child-device
  driver for correct full support ([posts 496â€“497](https://community.hubitat.com/t/-/86465/497),
  [523â€“529](https://community.hubitat.com/t/-/86465/529)). Do not describe the current first-channel
  decoding as full device support.
- **BUGS:** runtime errors, false-zero filtering, polling, energy reset accounting, health checks,
  fingerprint typos already present in source, release hygiene, and related code defects remain in
  `BUGS.md` and are intentionally not duplicated here.



