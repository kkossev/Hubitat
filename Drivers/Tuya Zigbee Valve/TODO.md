# Open User Requests — Tuya Zigbee Valve

Improvement requests harvested from all 465 posts of the community thread
([RELEASE] Tuya Zigbee Valve driver (w/ healthStatus)) on 2026-07-11, reviewed and
confirmed by the user. Also includes the four former driver-header TODOs (moved here from
`Tuya Zigbee Valve.groovy` on 2026-07-11 → items 2.1, 2.3, 6.3, 6.4).
Post links are `https://community.hubitat.com/t/-/92788/<post#>` unless another topic id is shown.

Four related threads were also analyzed in full on 2026-07-11 (→ items 4.6, 5.5, 5.6, plus
notes merged into 5.2 and 6.1):
- t/164667 "Giex Zigbee Sprinkler Valve" (12 posts)
- t/162398 "Sonoff zigbee sprinklers on pre-order sale" (71 posts)
- t/163259 "Measuring zigbee valve" (25 posts — no driver items; kkossev pointed the OP to
  the separate community "Tuya Water Meter & Valve" driver by mryaflle)
- t/152365 "Sonoff Sprinkler Valve Running Tuya Zigbee Valve Driver" (5 posts — resolved
  stale-HPM case, folded into 6.1)

This list complements [BUGS.md](BUGS.md) (confirmed defects in v1.8.0). Items here are
**feature requests and unresolved user reports**, not reviewed bugs — each needs its own
analysis before implementation. Follow the same ground rules as BUGS.md: one item at a
time, no version/timeStamp bumps until a release point, mark `[x]` only after the user
confirms on the dev hub.

---

## 1. Dual-valve (TZE284 / GiEX GX-03) usability

### 1.1 `[ ]` Component child devices for `TS0601_TZE284_VALVE` dual valves
Users can only reach valve 2 via RM "custom action" → `setValve2('open')`, and
dashboards / virtual switches don't track its state.
- Posts: [#439](https://community.hubitat.com/t/-/92788/439), [#441](https://community.hubitat.com/t/-/92788/441) (leepearson1977); [#442](https://community.hubitat.com/t/-/92788/442)–[#444](https://community.hubitat.com/t/-/92788/444) (lotussteve); [#445](https://community.hubitat.com/t/-/92788/445), [#450](https://community.hubitat.com/t/-/92788/450) (Jann)
- Approach: reuse the SWV-ZF2 child infrastructure added in 1.8.0 (`createZF2ChildDevices()`,
  `component*()` methods, `Tuya Zigbee Valve Component Child.groovy`). TZE284 children would
  delegate to Tuya DP 1/2 instead of genOnOff endpoints.

### 1.2 `[ ]` Verify valve 1 / valve 2 mapping on the GiEX GX-03
Jann reports the parent open/close drives the *right* (2nd) valve and `setValve2` did
nothing — possible DP 1 / DP 2 swap on this model.
- Posts: [#445](https://community.hubitat.com/t/-/92788/445), [#450](https://community.hubitat.com/t/-/92788/450) (Jann)
- Needs: debug logs from a GX-03 while operating each valve manually.

## 2. Time / timestamp handling

### 2.1 `[ ]` Sonoff SWV `irrigationStartTime`/`irrigationEndTime` timezone offset
Reported off by −4 h (user in EDT); the uint32 epoch value is converted without applying
the hub timezone. Was also a driver-header TODO ("off by 3 hours!") — moved here 2026-07-11.
- Post: [#457](https://community.hubitat.com/t/-/92788/457) (dbowles1975, 2026-06-17)
- Code: `parseSonoffCluster()` cases `500D`/`500E` (~lines 1360–1390).

### 2.2 `[ ]` GiEX / Tuya EF00 start/end time strings shown in device time
Device sends UTC+8 ("Beijing time") or uptime-relative strings; users see 8–13 h offsets,
and dstutz notes the values lag one irrigation cycle. GX02 devices reset their clock to
00:00 on battery insert.
- Posts: [#131](https://community.hubitat.com/t/-/92788/131)–[#142](https://community.hubitat.com/t/-/92788/142) (Ken_Fraleigh, Hatallica); [#172](https://community.hubitat.com/t/-/92788/172) (rngarcia); [#406](https://community.hubitat.com/t/-/92788/406) (dstutz); [#458](https://community.hubitat.com/t/-/92788/458) (jwjr)
- Options: convert/annotate hub-side, or synthesize the timestamps from the hub clock on
  valve open/close events instead of trusting the device strings (DPs 0x65/0x66/0x72).

### 2.3 `[ ]` "Time left before auto-close" countdown attribute
Driver-side countdown for models that don't report `timerTimeLeft`. Requested by `@rgr`;
was a driver-header TODO ("add a timer to the driver that shows how much time is left
before the valve closes") — moved here 2026-07-11.

## 3. Sonoff SWV

### 3.1 `[ ]` Command to reset `waterConsumed`
User wants to zero the seasonal total.
- Post: [#455](https://community.hubitat.com/t/-/92788/455) (an39511, 2026-05-14)
- Note: the device reports daily volume (FC11 0x500F); a reset likely has to be a
  driver-side baseline/offset kept in state, not a device command.

### 3.2 `[ ]` Configure periodic battery reporting
Battery only updates on refresh. `configure()` binds genPowerCfg for Sonoff but the
reporting configuration is literally `// TODO - configure battery reporting` (~line 1880).
- Post: [#424](https://community.hubitat.com/t/-/92788/424) (ilkeraktuna)

### 3.3 `[ ]` **VERIFY ON DEVICE** — physical button open fires `valve open` event
Physical open produced only irrigation attribute events, so users' auto-off rules didn't
trigger. The 1.6.4 digital workaround plus the 1.7.x 500D/500E inference probably fixed
this — confirm on a real SWV before closing.
- Posts: [#363](https://community.hubitat.com/t/-/92788/363) (calinatl), [#408](https://community.hubitat.com/t/-/92788/408) (chris18), bookmarked in [#409](https://community.hubitat.com/t/-/92788/409)

### 3.4 `[ ]` `irrigationVolume` reset / `irrigationDuration: disabled` inconsistency
`configure()` sends `irrigationDuration: disabled` when `autoOffTimer == 0`; kkossev noted
in-thread this "may be a bug, maybe not". One of three identical valves reset
`irrigationVolume` on every `on()` until re-paired.
- Posts: [#416](https://community.hubitat.com/t/-/92788/416)–[#421](https://community.hubitat.com/t/-/92788/421) (ilkeraktuna)

## 4. Missing fingerprints (all confirmed absent from v1.8.0)

All of these work when the user forces the profile manually; adding the fingerprint makes
detection automatic. One line in `deviceProfilesV2` `fingerprints` (+ `manufacturers`) each.

### 4.1 `[ ]` TS0049 `_TZ3000_cjfmu5he` → `TS0049_IRRIGATION_VALVE`
Promised in-thread ("will add this model in the next update, suppressing the warnings").
- Posts: [#305](https://community.hubitat.com/t/-/92788/305)–[#308](https://community.hubitat.com/t/-/92788/308) (jw970065)
- inClusters: `0003,0004,0005,0001,0006,E001,0000` (differs from the existing TS0049 entry — verify DP handling too).

### 4.2 `[ ]` TS0049 `_TZ3210_ru41azca` → `TS0049_IRRIGATION_VALVE`
- Posts: [#380](https://community.hubitat.com/t/-/92788/380)–[#387](https://community.hubitat.com/t/-/92788/387) (Pat-C)

### 4.3 `[ ]` TS0601 `_TZE204_rzrrjkz2` → `TS0601_VALVE_ONOFF`
NEO sprinkler timer; basic on/off confirmed working when forced.
- Posts: [#309](https://community.hubitat.com/t/-/92788/309) (Arktronic), [#389](https://community.hubitat.com/t/-/92788/389) (user6851)

### 4.4 `[ ]` TS0601 `_TZE284_zm8zpwas` → `TS0601_VALVE_ONOFF`
Novato ZPV-01; confirmed working when forced.
- Post: [#429](https://community.hubitat.com/t/-/92788/429) (ilkeraktuna)

### 4.5 `[ ]` TS011F `_TZ3000_tvuarksa` → `TS011F_VALVE_ONOFF`
SM-AZ713; confirmed working when forced. Fix together with BUGS C2 (the TS011F profile's
`model` field wrongly says `TS0011`).
- Posts: [#238](https://community.hubitat.com/t/-/92788/238) (Bruce123), [#343](https://community.hubitat.com/t/-/92788/343) (mboisson)

### 4.6 `[ ]` SONOFF SWV-ZF2E / SWV-ZF2U model fingerprints
The `SONOFF_SWV_ZF2_DOUBLE_VALVE` profile has a single fingerprint with `model:'SWV-ZF2'`
(line ~514), but the real devices report regional model strings: Bagpuss has a **SWV-ZF2E**
([t/92788 #464](https://community.hubitat.com/t/-/92788/464), fingerprint sent via DM) and
brianwilson a **SWV-ZF2U** ("Hydro DUO", [t/162398 #69](https://community.hubitat.com/t/-/162398/69),
currently running the inbuilt Generic Zigbee Multi-Endpoint Switch driver). The exact-match
model+manufacturer detection in `setDeviceNameAndProfile()` will not match these — add the
model strings to the profile `model`/`fingerprints` once the real fingerprints are confirmed.
See also SONOFF_SWV_ZF2_SUPPORT_TODO.md.

## 5. Older, still-unresolved functional requests

### 5.1 `[ ]` LIDL/Parkside: open with custom duration
Device auto-closes after its default 5 min; zigbee2mqtt supports `{"on":true,"ontime":N}`.
`setIrrigationTimer()` currently excludes LIDL (line ~2299).
- Post: [#251](https://community.hubitat.com/t/-/92788/251) (banas.sylwester)

### 5.2 `[ ]` GiEX capacity mode never works
`setIrrigationMode` sends DP 01 enum but users never saw the mode change take effect;
goal is auto-close after N litres. Needs zigbee2mqtt converter comparison + device logs.
- Posts: [#162](https://community.hubitat.com/t/-/92788/162)–[#171](https://community.hubitat.com/t/-/92788/171) (JmikeyB, rngarcia), [#204](https://community.hubitat.com/t/-/92788/204) (user5467)
- Also reported on the GiEX **GX-02** (built-in flow meter): "Auto Off timer and Irrigation
  Capacity shut off don't work" — [t/164667 #1](https://community.hubitat.com/t/-/164667/1) (hb-instruments, 2026-06-19)

### 5.3 `[ ]` TS0011 `_TYZB01_rifa0wlb`: power-on behaviour via alternative method
Device has no E001 cluster; support was promised via a different mechanism (likely genOnOff
attribute 0x4003 StartUpOnOff). BUGS B6 stops the wrong E001 write; the feature itself is
still missing.
- Posts: [#255](https://community.hubitat.com/t/-/92788/255)–[#259](https://community.hubitat.com/t/-/92788/259) (sheytanov.simeon)

### 5.4 `[ ]` GiEX DP 02 valve-state text is misleading ("disabled" means closed)
The GiEX DP 02 on/off value is mapped through `timerStateOptions` (line ~942), so a closed
valve logs/reports "Water Valve State is **disabled**" — a user found this confusing when
auditing why a sprinkler opened. Use open/closed wording for the valve-state part and keep
the timer-state labels for the actual timer attribute.
- Post: [t/164667 #1](https://community.hubitat.com/t/-/164667/1) (hb-instruments)

### 5.5 `[ ]` "Open for N minutes" one-shot command
User wants b-hyve-style operation: manually open a valve *and* give the run time at the
point of operation (one action, e.g. from a dashboard or RM). Today this takes two steps
(`setIrrigationTimer` then `open`). A convenience command (e.g. `openFor(duration)`) would
cover it for the models with auto-off support.
- Post: [t/162398 #54](https://community.hubitat.com/t/-/162398/54) (bobbo)

### 5.6 `[ ]` Dashboard state-inversion preference
Preference to flip the open/closed presentation for normally-closed irrigation valves next
to normally-open main valves on a dashboard. Arguably a dashboard-template problem —
candidate to decline politely.
- Post: [#462](https://community.hubitat.com/t/-/92788/462) (dnickel)

## 6. Housekeeping raised by users

### 6.1 `[ ]` HPM update channel is stale
HPM reports "up to date" at 1.6.1 while the driver is far ahead. Same root cause as BUGS C1
(manifest version lag + broken beta manifest); becomes part of the v1.8.0 release task.
Keeps generating support cases: user4286 debugged a "state always closed" issue that was
just an old driver HPM refused to update, and kkossev has had to tell users to run HPM
'Repair' manually.
- Posts: [#437](https://community.hubitat.com/t/-/92788/437) (user3357); [t/152365 #1](https://community.hubitat.com/t/-/152365/1)–[#4](https://community.hubitat.com/t/-/152365/4) (user4286, oldcomputerwiz); [t/162398 #52](https://community.hubitat.com/t/-/162398/52) (kkossev)

### 6.2 `[ ]` TS011F physical open/close not reported at all — document as limitation
Device firmware never reports state changes from the physical button; the only workaround
is periodic polling, which kkossev prefers to avoid. Probably "won't fix" — document it in
README/top post instead.
- Posts: [#412](https://community.hubitat.com/t/-/92788/412)–[#427](https://community.hubitat.com/t/-/92788/427) (bobbles)

### 6.3 `[ ]` Document the attributes (per valve model) in GitHub
Add per-model attribute documentation to the GitHub repo, plus links between the HE forum
top post and the GitHub pages. (Was a driver-header TODO — moved here 2026-07-11.)

### 6.4 `[ ]` Clear the old states on update; add rejoinCtr
Stale `state` entries from older driver versions should be cleaned up in
`updated()`/`checkDriverVersion()`, and the rejoin counter handling revisited.
(Was a driver-header TODO — moved here 2026-07-11. Note `state.stats.rejoinCtr` already
exists and is bumped on device announce — clarify with the user what "add rejoinCtr"
should cover, e.g. surviving `resetStats()` or being exposed as an attribute.)

---

## Already covered elsewhere (do not duplicate)

- FrankEver `setValveOpenThreshold` never transmits → BUGS **B1**
- `isBatteryPowered()` under-counts profiles → BUGS **B7**
- Sonoff dotted firmware-version parse → BUGS **C12**
- ZF2 parent `open()`/`close()` drives endpoint 1 only → BUGS **B5**
- SWV-ZF2 dual-valve support ([#461](https://community.hubitat.com/t/-/92788/461), [#464](https://community.hubitat.com/t/-/92788/464)) → the active v1.8.0 work; the remaining
  gap (ZF2E/ZF2U model-string fingerprints) is item **4.6**
- GiEX valves dropping off the Zigbee network every ~2 weeks ([t/164667 #9](https://community.hubitat.com/t/-/164667/9), chrisbvt) → mesh/pairing issue, not addressable in the driver
- Tuya ultrasonic water meter + valve ("214C", [t/163259](https://community.hubitat.com/t/-/163259/1)) → out of scope; kkossev referred the user to the separate community "Tuya Water Meter & Valve" driver (mryaflle)
