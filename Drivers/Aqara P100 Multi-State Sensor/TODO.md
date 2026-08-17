# Consolidated TODO — Aqara P100 Multi-State Sensor

## Provenance

The release thread — <https://community.hubitat.com/t/release-aqara-multi-state-sensor-p100-zigbee-driver-c8-only/163540>
— was created on 2026-08-17 and **has not been analysed yet**. So this list is **not** forum-derived,
unlike the `TODO.md` files of the other drivers in this repository. Nothing has been harvested from
the thread; run the forum-thread analysis against it to populate this file properly.

The items below were instead derived from the driver code itself during the static review of
2026-08-17, audited against **v0.1.6 (2026-05-06)**. They are the open *questions and feature gaps*
the code raises — not defects.

The driver header carries **no author `TODO:` block**, so there was nothing to migrate here either.

### Relationship to BUGS.md

[BUGS.md](BUGS.md) is the reviewed static-code-review defect list (A/B/C severity, exact locations,
suggested fixes). **This file is not that.** It holds open questions, unverified device behaviour and
feature gaps — things that need evidence or a decision before any code could sensibly be written.
Where an item here overlaps a BUGS.md entry, the BUGS.md id is quoted; do not duplicate the work.
Read [AGENTS.md](AGENTS.md) before acting on anything here.

### Ground rules

Same as `BUGS.md`: one item at a time, in the order kkossev chooses; each change uploaded to the dev
hub and confirmed by kkossev before the next; mark `[x]` only after he confirms. **Do not bump
`version()`/`timeStamp()` or add header history lines after an individual item** — kkossev says when.

### Status legend

- `OPEN` — a concrete change is defined and could be implemented.
- `NEEDS_EVIDENCE` — requires a device log, hardware confirmation, or a decision from kkossev first.
- `RESOLVED` — done or confirmed unnecessary.

---

## 1. Device behaviour to confirm

### 1.1 `[ ]` `NEEDS_EVIDENCE` — Does the P100 actually leave the network without the registration write and time replies?

This is the single biggest open question about the driver, and the justification for its two most
unusual pieces of code.

The driver comments state the theory explicitly, and label it unconfirmed:

- `aqaraBlackMagic()`: *"It is SUSPECTED, that WITHOUT this write the device will leave the Zigbee
  network after ~24 hours (NOT confirmed yet!)"* — referring to the 16-byte FCC0/`0x00FF` Lumi
  registration write, captured verbatim from an Aqara E1 hub Wireshark trace.
- `replyToTimeClusterRead()`: *"Probably, without this reply the device internal 24-hour watchdog
  timer causes it to leave the network?"*

Versions 0.1.4 and 0.1.5 were both spent on this theory (the E1-hub init sequence, then a forced Time
reply after every `0x00DF` heartbeat).

**Needs:** a device left paired for several days with debug logging on, and a report of whether it
stays joined. Ideally also one run with the registration write removed, to establish the negative —
but that risks losing the pairing, so it is kkossev's call whether that experiment is worth it.

**Why it matters before anything else:** if the theory is wrong, a good deal of the driver
(`sendTimeSync` scheduling on every heartbeat, the four post-`configure()` time pushes) is
unnecessary complexity that could be simplified. If it is right, it needs to be documented as
load-bearing so no future agent removes it. Either way the answer changes what the driver should look
like — hence this being item 1.1.

**Related:** `BUGS.md` A4 (the time reply throws when the hub has no timezone set — worth fixing
*before* running this experiment, so a null timezone cannot silently invalidate the result) and
`BUGS.md` B3 (`configure()` does not send the registration write at all).

### 1.2 `[ ]` `NEEDS_EVIDENCE` — Does the P100 report vibration strength / tilt angle on cluster `0x000C`?

Tracked in `BUGS.md` as **B9 / VERIFY ON DEVICE**; recorded here too because if the answer is yes it
is a **feature**, not a bug fix.

The fingerprint declares `0x000C` (analogInput) in its `inClusters`, but the driver has no handler for
it, so any such report lands in the generic "Unprocessed attribute report" debug log. On older
`lumi.vibration.*` devices, `0x000C:0x0055` (present value) carries vibration strength and tilt angle
— neither of which this driver exposes in any form.

**Needs:** a debug log captured while shaking and tilting the device, searched for `cluster=000C`.

**Then, only if it fires:** decide whether to expose it. A new attribute name is permanent public
surface (root [AGENTS.md](../../AGENTS.md) §10), so this needs kkossev's decision on naming, and a
check of what Zigbee2MQTT calls the same values, before any code. Do not add speculative parsing.

### 1.3 `[ ]` `NEEDS_EVIDENCE` — Which battery does the P100 use, and is the 2.5–3.0 V curve right?

`voltageAndBatteryEvents()` uses the repository default linear range of **2.5–3.0 V**. The P1 driver
uses the same range for coin cells but **2.85–3.0 V** for the FP300's 2×CR2450, so the range is
known to be device-specific in practice.

The v0.1.6 decision to derive battery percentage from voltage only (rather than trusting the device
percentage) makes the curve the sole determinant of what users see, so it matters more here than it
would otherwise.

**Needs:** the battery type from the device or its manual, plus a `batteryVoltage` reading from a
device with a known-fresh cell and, ideally, one near end of life.

**Related:** `BUGS.md` B4 — the FCC0 `0x0018` path still emits the device's own percentage, so today
the two methods fight. Settle B4 first; there is no point tuning a curve whose output is being
overwritten.

---

## 2. Release readiness

### 2.1 `[x]` `RESOLVED` — Community thread created and wired up

Done 2026-08-17. `BUGS.md` C10. All three places now carry the real URL: line 4 of the driver, the
README, and `communityLink` in `packageManifest.json`.

**Follow-up:** the thread has not been analysed for user requests yet — that is what would normally
populate this file. Note the thread title says **"C8 only"**; that constraint is not stated anywhere
in the README or the driver, and probably should be.

### 2.2 `[x]` `RESOLVED` — HPM manifest created and package added to the catalog

Done 2026-08-17 at kkossev's request. `packageManifest.json` version `0.1.6`, dated 2026-08-17,
package id `7e8d3951-8550-470b-84a8-21e9d07acdec`, driver id
`0cf0b9b7-cc5e-4a60-8d15-6bea294acbe8`. The catalog entry was appended to
`development/repository.json` (now 38 packages).

**Remaining, and it needs kkossev:** [PUBLISHING.md](../../PUBLISHING.md) *Updating the package
catalog* steps 5–7 require `main/repository.json` to be byte-for-byte identical to the
`development` copy. The two were identical before this change; `development` is now one package
ahead. Syncing it is a `main`-branch operation and was deliberately not done unilaterally.

Note also that the manifest has **no `communityLink`** — the field was omitted rather than filled
with the placeholder URL. Add it when 2.1 is done.

---

## Already covered elsewhere — do not duplicate

- **All static-review defects** — the 6 A-items, 9 B-items and 18 C-items live in
  [BUGS.md](BUGS.md). Do not restate any of them here as TODO items.
- **Architecture, parse flow, the FCC0 attribute map, the TLV tag map, and the list of deliberately
  disabled code** — all in [AGENTS.md](AGENTS.md). If you are about to write a description of how the
  driver works into this file, it belongs there instead.
- **Version history** — [CHANGELOG.md](CHANGELOG.md) and the driver header. Completed work is
  recorded there, not here.
- **Repository-wide conventions** (release procedure, branch policy, the sandbox restrictions) — root
  [AGENTS.md](../../AGENTS.md), [CONTRIBUTING.md](../../CONTRIBUTING.md) and
  [PUBLISHING.md](../../PUBLISHING.md).
