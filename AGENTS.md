# AGENTS.md — repo-wide guide for AI agents

Working guide for AI coding agents in the **`kkossev/Hubitat`** repository. It holds the rules that
apply everywhere, so the per-folder guides only have to describe what is unique to their own driver.

---

## Principles

Everything below is detail. If a specific rule ever seems not to cover your situation, these five
do. They exist because this repository has properties most codebases don't: the code runs on other
people's hardware, there are no automated tests, and a regression reaches real homes.

**1. Understand before you change.** Read the whole driver, not the fragment around the symptom —
most defects here depend on something far away (a `case` label 900 lines up, a helper whose name
misdescribes what it matches, a datapoint that means something different on another model). State
the interpretation you are acting on. If two readings are possible, say so and ask; do not pick one
silently and build on it.

**2. Make the smallest change that solves the stated problem — and build it the simple way.** Only
what was asked: no speculative abstraction, no "while I was in there", no refactor bundled into a
fix. A tightly-scoped diff is what makes the user's hardware test conclusive — if three things
changed, a failure tells you nothing about which one broke it. Then solve it plainly: the simplest
construct that works beats a general mechanism you might need later. Elaboration is not free here —
methods have a hard 64 KB compile limit, the static profile map is already near its cap, and every
preference or attribute you add is permanent public surface you can never rename (§7, §10).

**3. Leave everything else exactly as it was.** Match the surrounding style even where it is not the
style you would choose. Preserve public names, established idioms, and deliberate oddities; in this
codebase renaming things breaks paired devices and scheduled jobs, and "obviously dead" code is
often load-bearing. Remove only what your own change made dead.

**4. Decide what "done" looks like before you start.** A change is not finished because it compiles.
State the concrete check — which device, which command, which log line or attribute value — so the
hub test has a real pass/fail. Verification here is a person with real hardware, so give them
something specific to look for.

**5. When the answer requires a device, stop and say so.** Plenty of questions here cannot be
settled by reading code: what a specific manufacturer's firmware actually emits, whether a report
arrives at all. Guessing produces confident, wrong code in a driver other people install. Label it
**VERIFY ON DEVICE** or **ASK USER** and hand it back.

> Principles 1–4 are adapted from the four principles in
> [multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills) (MIT),
> which are in turn derived from Andrej Karpathy's public observations on LLM coding — not authored
> or endorsed by him.

---

## 0. Scope and precedence

Applies to everything in this repository — `Drivers\`, `Libraries\`, `Apps\`, `Documents\`.

When sources disagree:

1. **The folder's own `AGENTS.md`** wins on driver-specific facts (architecture, DP maps, quirks,
   deliberate exceptions to the rules below).
2. **This file** wins on repo-wide conventions.
3. **[CONTRIBUTING.md](CONTRIBUTING.md) and [PUBLISHING.md](PUBLISHING.md)** win on branch policy,
   release procedure and package-catalog rules. Those two are the authoritative, user-facing docs —
   this file deliberately does not restate them.

**Before editing anything:** check whether the folder you are working in has its own `AGENTS.md`
and read it, along with the complete target `.groovy` file. Never analyse from a partial read —
most bugs in this codebase are context-dependent (a `case` label 900 lines away, a helper whose
name lies about what it matches). Most folders have **no** guide; there, this file plus the
driver's own header comment is what you have, so ask more and assume less.

> **Note on the working documents.** This guide refers throughout to per-folder `BUGS.md`,
> `TODO.md` and `DEVICES.md`. Those are the maintainer's working notes — they are kept in the local
> working copy and are **not published in this repository**, so most of them will not be present in
> a clone. Where a rule below says "work from the folder's `BUGS.md`", it applies when that file
> exists; otherwise derive the finding yourself and raise it rather than assuming none exists.
> §9 documents their formats so that anything newly written matches the established conventions.

Not every folder has a guide — 37 of the 54 driver folders have none. There, this file plus the
driver's own header comment is what you have; be correspondingly more careful and ask more.

---

## 1. What this repository is

Hubitat Elevation drivers and apps written in Groovy, by **Krassimir Kossev (kkossev)**,
Apache License 2.0, distributed through Hubitat Package Manager (HPM).

- GitHub: `https://github.com/kkossev/Hubitat` — wiki at `https://github.com/kkossev/Hubitat/wiki`
- **The maintainer's working copy is a normal git clone**, checked out on the **`development`**
  branch — git commands work there.
- Branch semantics: `development` is the **primary branch and single source of truth**; `main` is
  only a compatibility entry point for old links and existing HPM registrations. See
  [CONTRIBUTING.md](CONTRIBUTING.md).
- Driver namespace is `kkossev` (exception: the IKEA Matter drivers use `community`).
- Each driver has its own Hubitat community forum thread, linked from its folder guide and from the
  `.groovy` header.

**Nested git repositories** exist inside three driver folders — `Drivers\Ikea Matter`,
`Drivers\Tuya Zigbee Valve`, `Drivers\Tuya ZigBee Vibration Sensor`. A `git` command run from one of
those folders operates on the nested repo, not on `kkossev/Hubitat`. Check `git rev-parse
--show-toplevel` before assuming.

---

## 2. Golden rules

1. **Read first.** The folder guide, its `BUGS.md` / `TODO.md` if present, and the whole driver file.
2. **Edit the right file.** V3/V4 drivers exist as a small `#include` source *and* a generated
   `*_lib_included.groovy` bundle — never hand-edit the bundle's logic (see §4). Archive and
   snapshot copies (`… ver 2.1.5.groovy`, `… v. 1.7.1.groovy`, `Archives\`) are **excluded from all
   analyses and edits**.
3. **One bug / one item at a time, in the order the user chooses.** Never batch fixes on your own
   initiative, even when a `BUGS.md` suggests batches — those are advisory.
4. **Do not bump `version()` / `timeStamp()` / the `Last edited:` header, and do not add header
   history or changelog lines, after an individual fix.** The user names the release point
   explicitly. The one thing that *is* bumped whenever it changes is the mmWave driver's JSON
   profile database `version`/`timestamp` — it versions the data, not the driver.
5. **Never upload or publish to the hub.** The user pushes the driver from the Hubitat VS Code
   extension and reports the result. Your job ends at the file edit — then wait. Mark a `BUGS.md` /
   `TODO.md` item `[x]` only after the user confirms the hub test.
6. **`ASK USER` and `VERIFY ON DEVICE` items are blocked** until the user answers. When code
   contradicts its own comments or changelog, that is an ASK USER, not a unilateral fix.
7. **Keep changes minimal and local.** No mass-reformatting, no opportunistic refactors, no
   re-enabling of deliberately disabled code, no unrelated fixes folded into a device-support change.
8. **Analysis tasks produce documents, not edits.**

---

## 3. Repository layout

```
<repository root>\
├─ AGENTS.md, CONTRIBUTING.md, PUBLISHING.md, README.md, repository.json
├─ Apps\<App Name>\
├─ Documents\                 external Zigbee spec links
├─ Drivers\<Driver Name>\
│  └─ docs\maintainer\        driver-specific maintainer research and analysis
├─ Libraries\                 22 shared Groovy libraries (V3/V4 drivers only)
└─ docs\maintainer\           repository-wide maintainer research and analysis
```

### The `docs\maintainer` structure

`docs\maintainer` folders hold research, investigations, plans and other maintainer-facing material
that does not belong among a driver's runtime or distribution files. Repository-wide documents live
under the root `docs\maintainer`; driver-specific documents live inside the applicable driver folder:

```
Drivers\
└─ <Driver Name>\
   └─ docs\
      └─ maintainer\
         └─ <driver-specific maintainer documents>.md
```

- Not every driver has a `docs\maintainer` folder; create one only when a driver-specific document
  belongs there. Never place driver-specific documents under `docs\maintainer\Drivers\` at the
  repository root.
- The canonical per-folder files (`AGENTS.md`, `BUGS.md`, `TODO.md`, `DEVICES.md`, README and release
  files) remain beside the driver under `Drivers\<Driver Name>\`.
- Some maintainer documents are published on GitHub and some are intentionally local-only. Do not
  infer publication status from the file's location.
- Publication is **per document**, not per directory. Maintainer documents are ignored by default.
  A reviewed document intended for GitHub must be explicitly unignored with a narrow `.gitignore`
  exception; never expose a whole `docs\maintainer` tree to publish one file.
- Before publishing, apply §9's GitHub-facing rules: English, and no absolute local paths, private
  addresses, device ids or personal data.

### The per-folder document set

| File | Role |
|---|---|
| `<Driver>.groovy` | The driver. For V3/V4 this is the `#include` **source**. |
| `<Driver>_lib_included.groovy` | **Generated bundle** (V3/V4 only) — what HPM installs. Never hand-edit its logic. |
| `AGENTS.md` | This folder's agent guide: architecture, devices, parse flow, quirks. |
| `BUGS.md` | Reviewed bug work-list (§9). |
| `TODO.md` | Open user requests harvested from the forum (§9). |
| `CHANGELOG.md` | Keep a Changelog history (§9). |
| `DEVICES.md` | Device / links database behind the README table (§9). |
| `README.md` | User-facing. Casing varies across folders (`README.md` / `README.MD` / `readme.md`). |
| `packageManifest.json` | HPM manifest. Two folders use `manifest.json` instead. |
| `.hubitat\metadata.json` | Hubitat VS Code extension mapping (local file ↔ hub code id). **Tool-managed — never hand-edit.** |
| `.groovylintrc.json` | npm-groovy-lint / CodeNarc config. |
| `<Driver>.code-workspace` | Adds `../../Libraries` to the workspace so `#include` targets are editable side by side. |
| `scratchPad.txt`, `Archives\`, `Images\` | Scratch / reference only. |

### Two traps

- **Driver files with no `.groovy` extension.** `Drivers\Tuya Zigbee Metering Plug\Tuya Zigbee
  Metering Plug` (3065 lines) is a real driver that any `*.groovy` glob will miss. Others exist in
  the sibling repos. **Never rename them** — `importUrl` and HPM point at the exact path.
- **Agent documents are deliberately untracked.** `.gitignore` lists each `AGENTS.md`, `BUGS.md`,
  `*_PLAN.md`, `DEVICES.md` etc. **by exact path**. A newly created or renamed document is **not**
  auto-ignored — append its path to `.gitignore` in the same change.

---

## 4. The two driver architectures

Every driver is one of these. The folder guide says which; without a guide, look for `#include`
lines near the top of the file.

### V3 / V4 — libraries + generated bundle

- A small **development source** (e.g. `Tuya_Zigbee_Switch.groovy`) ending in
  `#include kkossev.commonLib`, `#include kkossev.deviceProfileLib`, …
- A large **amalgamated bundle** `<Driver>_lib_included.groovy` with every library body pasted
  inline, delimited by `// ~~~~~ start include (NNN) kkossev.<lib> ~~~~~` /
  `// ~~~~~ end include (NNN) kkossev.<lib> ~~~~~` and with each library line suffixed
  `// library marker kkossev.<lib>, line N`. **This is what HPM installs and most users run.**
- Behaviour is data-driven: a `deviceProfilesV3` map (V3) or a JSON profile database (V4) supplies
  fingerprints, datapoints, preferences and commands; device-specific glue lives in `custom*()`
  hooks called by `commonLib`.
- **There is no build script.** Regeneration is manual: copy the source, paste each library verbatim
  between its markers, preserving the marker lines. Always state whether the bundle was regenerated
  or is pending.
- Verify sync before and after any change by comparing `version()` in both files.

### Legacy monolithic

One self-contained file. No libraries, no `#include`, no `deviceProfilesV3`, no `custom*` hook
contract. **Do not apply V3 conventions here, and do not convert a legacy driver to V3 as part of a
focused fix.** Some legacy drivers carry a `deviceProfilesV2`-style map that supplies fingerprints
only and does *not* drive parsing — read the folder guide before assuming otherwise.

### Where device support gets added

This is the most common wrong-file mistake. It depends on the architecture:

| Architecture | New device support goes in |
|---|---|
| **V4** (mmWave) | the JSON profile database **only** — normally no Groovy change at all |
| **V3** | the driver's `deviceProfilesV3` map — not `custom*()` code |
| **Legacy monolithic** | a `metadata` fingerprint plus hand-coded parse/command branches |

For V3 and V4, **prefer the data-driven path**: if the profile engine can express the behaviour,
put it in the profile rather than writing `custom*()` code beside it. Reach for Groovy only when
the device genuinely does something the engine cannot describe.

**Adding a device touches more than one place — miss one and it fails silently:**

1. the fingerprint (profile `fingerprints` array, or `metadata` for legacy drivers);
2. the datapoint / attribute entries that decode it;
3. any preference the profile exposes;
4. **a brand-new attribute name must ALSO be declared as an `attribute` in the driver's
   `metadata` block** — attribute declarations are static Groovy and are never generated from
   profile data, so the events will be emitted and silently dropped if you skip this;
5. the folder's `README.md` / `DEVICES.md` device table, at a release point.

If DP semantics differ from an existing profile, create a **new** profile key rather than
overloading the existing one.

### Shared-library impact radius

`Libraries\commonLib.groovy` is embedded in ~28 drivers; `deviceProfileLib` in ~17.

- Fix the library master in `Libraries\` first — it is the single source of truth.
- **A library fix reaches users only after each affected driver's bundle is regenerated.** The user
  decides when and for which drivers. Never hand-edit one bundle and call it done.
- Every library finding must state **which drivers it reaches**.
- Bundles lag: shipped drivers embed library versions spanning several minor releases.
- The ~30 `custom*` hook names that `commonLib` resolves via `respondsTo()` are **a contract with 28
  drivers** — never rename them.

---

## 5. Versions and releases

### Where the version lives

| Form | Used by |
|---|---|
| `static String version()` / `static String timeStamp()` | most drivers (timestamp format `yyyy/MM/dd h:mm a`) |
| `@Field static final String VERSION` / `TIME_STAMP` | some legacy drivers |
| `<name>LibVersion()` / `<name>LibStamp()` **plus** the `version:` field in the `library()` header | `Libraries\*` — keep all three in sync |
| `* Last edited: <date>` header line only | the IKEA Matter drivers |

`checkDriverVersion()` runs on every `parse()` and on `updated()`; when the version+timestamp string
changes it re-runs `initializeVars(fullInit = false)`, so new settings get defaulted automatically
after an upgrade. **A library version bump alone does not trigger it** — only the driver's own
version string does.

### At a release point (and only then — the user says when)

1. Bump the version and timestamp.
2. Add one header-history line:
   `* ver. x.y.z  YYYY-MM-DD kkossev  - description`
   Third-party contributions credit the contributor in the author column; dev-branch entries are
   tagged `(dev. branch)`.
   **Keep it short — one line, users are the audience.** Name only what a user would notice: new
   devices/model groups, new preferences/attributes/commands, and visible behavior fixes, plus a
   bare `bug fixes` for the rest. No root-cause explanations, no method or variable names, no
   forum/issue links, no continuation lines. All of that detail belongs in `CHANGELOG.md` only —
   point there instead (`... (details in CHANGELOG.md)`). Shorten an over-long existing line when
   you touch it.
3. Follow **[PUBLISHING.md](PUBLISHING.md)** for README, `packageManifest.json` and the catalog.

`packageManifest.json` versions lag the driver routinely across this repo — that is normal between
releases. Only touch a manifest at a release the user has declared.

### importUrl

Always the **`development`** branch raw URL, spaces `%20`-encoded. For V3/V4 drivers it points at
the `*_lib_included.groovy` bundle; for monolithic drivers at the single file. Several guides note
this explicitly as deliberate — **do not "fix" an importUrl to `main`.**

---

## 6. Verification — there are no automated tests

Nothing in this repository has a test harness. The authoritative checks are:

1. **The hub compiles the driver on Save** (Hubitat "Drivers code" editor, or upload via the
   Hubitat VS Code extension). This is the only real compile check.
2. **Live device behaviour** on the user's development hub, with Live Logs and debug logging on.

**The user runs both.** One change at a time; wait for the confirmation before the next.

**Before diagnosing anything, confirm what is actually running on the hub.** Do not infer that a
change is deployed from timing alone. Check the device's `state.driverVersion` / timestamp, or the
live logs. Diagnosing new local code against an older build still on the hub wastes a whole cycle —
and for V3 drivers, remember the hub may be running the bundle while you edited the dev source.

Local pre-checks an agent may run:

- `npm-groovy-lint` / CodeNarc, configured per driver folder in `.groovylintrc.json`
  (`"extends": "recommended"` with a standard disable list). Per-file exceptions go in the line-1
  `/* groovylint-disable … */` pragma. **Keep new code lint-clean; never mass-reformat old code.**
- A local Groovy CLI for pure language-semantics questions (operator precedence, cast behaviour,
  `switch` scoping). Useful, but it is *not* the Hubitat sandbox — see §7. In particular a local
  syntax check **cannot** catch sandbox restrictions, which fail at runtime rather than at paste
  time.

### Scope your tooling to one folder

The repository is 152 `.groovy` files / ~17 MB, and 29 of the `*_lib_included.groovy` bundles are
over 150 KB.

- **Lint the one driver folder you are working in, never the repository root.** There is no root
  `.groovylintrc.json`; the config is per-folder by design.
- **When searching for a symbol, search the dev sources and `Libraries\`, not the bundles.** Every
  library symbol is duplicated into ~29 bundles, so an unscoped grep buries the real definition
  under copies. Read the bundle only when the bundle itself is the thing in question.

---

## 7. Hubitat platform and Groovy sandbox semantics

The single source of truth for these; per-folder guides list only which of them a given driver
relies on. Target runtime is Groovy 2.4.21 inside Hubitat's sandbox.

**Undefined method calls throw.** Calling a method that does not exist, or an existing one with the
wrong arity, raises `MissingMethodException` and aborts the handler. This is a real crash source
here — latent calls to methods that no longer exist have survived in error-only branches for
years, because nothing exercises them until something else goes wrong. It is separate from how the
sandbox treats undefined *identifiers*, which is documented per-driver where it matters and in
`Libraries\AGENTS.md`.

### Sandbox restrictions

- **Reflection is blocked. `getClass()` and `.class` are NOT allowed** — and they fail at
  **runtime, not at paste time**, so neither the hub's compile-on-Save nor a local syntax check
  will catch them. To identify a value's type (e.g. probing an unknown `descMap.value` shape) use
  an `instanceof` chain. In a `catch` block use `e.message`, **never** `e.class.simpleName` — that
  makes the error handler itself throw, precisely when you need the diagnostic.
- The hub **rejects script-level `private static final`** fields ("Modifier 'private' not allowed
  here"). Use `@Field`, or a plain method returning the constant.

### Execution budget

- A method has roughly **20 seconds** before the platform intervenes. Break long work into stages
  with `runIn()` / `runInMillis()` rather than looping or sleeping.
- Blocking HTTP holds a hub thread for the whole call. Prefer `asynchttpGet` for anything that
  talks to the network on a schedule.

### Concurrency and shared state

- Every driver declares `singleThreaded: true` → **no concurrency guards needed in handlers.**
- Plain `state` only. **`atomicState` is not used anywhere in this repository.**
- **`@Field static` maps are shared JVM-wide across every device using that driver.** Never mutate
  `deviceProfilesV3` at runtime — doing so caused the `CloneNotSupportedException` fixed in
  3.5.0/3.5.1. V4's profile caches are global for the same reason and carry their own load lock.
- **Store IDs in `state`, never device objects** — a live object in `state` does not survive
  serialisation. Same for GStrings: write `"...".toString()` when the value goes into `state`.
- Release what you created: schedules and subscriptions are cleaned up in `uninstalled()`.

### Size limits

- Methods larger than ~64 KB fail to compile ("Method too large"). This is why parsing is
  decomposed into many small handlers — keep new `switch` blocks small.
- The static device-profile map is near its own limit; that is why 3.6.2 introduced
  `deviceProfilesV3defaults` and trimmed `defaultFingerprint` entries. **Don't re-inflate it.**

### Numbers and bytes

- `safeToInt(val, default)` / `safeToDouble(val, default)` are **decimal** parsers that return the
  default, **never `null`**. Never feed them hex attribute strings — use `hexStrToUnsignedInt` or
  `zigbee.convertHexToInt`. (Mixing these up is a recurring bug class here.)
- **Endianness is per-protocol.** Tuya EF00 datapoint values are decoded **big-endian**
  (MSB-first walk). Aqara `0xFCC0` payloads and ZCL write payloads are **little-endian** — the
  byte-swap patterns (`data[1] + data[0]`, `hex[2..3] + hex[0..1]`, `zigbee.swapOctets`) are
  intentional. Check the direction before touching either.
- Signed ZCL types (`0x28`, `0x29`) decoded as unsigned produce nonsense negatives (e.g. −10 °C
  reported as +246) — a recurring bug class.

### Events, health and lifecycle

- Event `type` is `'physical'` (device-originated), `'digital'` (driver-derived or commanded), or
  `'delayed'` (re-queued by a rate limiter). Older code sometimes uses `isDigital: true` instead —
  cosmetic, tracked as cleanup.
- `state.states['isDigital']` is armed by commands (~5 s) and `state.states['isRefresh']` by
  `refresh()` (~6 s); events carry `[digital]` / `[refresh]` suffixes and force `isStateChange`
  accordingly.
- Measurement libraries apply a **delta + minimum-reporting-time filter**: a report changing less
  than the delta is dropped; one arriving too soon is *queued* via
  `runIn(…, 'sendDelayedXxxEvent', [data: eventMap])` and emitted with `type: 'delayed'`.
- Health check: `healthCheckMethod` 0 = disabled, 1 = activity, 2 = periodic ping. Any received
  packet marks the device online; 3 missed intervals mark it offline. `ping()` reads Basic attribute
  `0x01` and emits `rtt`.

### Zigbee device behaviour

- **Sleepy battery devices only accept configuration right after they wake.** Never tell a user to
  press *Configure* on a battery-powered sleepy device. The house pattern is to queue commands and
  flush them on the next incoming packet. Some devices must be **re-paired** with the driver already
  installed, without deleting the device first.
- **`tuyaBlackMagic()`** — Basic-cluster reads plus a write of `0x13` to attribute `0xFFDE` — is the
  required Tuya pairing wake-up ritual. It was removed once and deliberately restored. Leave it.
- **Tuya EF00 command `0x24` is a time-sync request the driver must answer** (local + UTC). Tuya
  devices re-request time on every check-in; keep this working.
- `isTuyaE00xCluster()` swallowing `0xE000` / `0xE001` messages is a workaround for platform parse
  exceptions — keep it.
- `zigbee.getEvent()` can throw on some Tuya frames; the surrounding try/catch is deliberate.
- **Tuya DP meanings are model-dependent.** Never assume a datapoint number carries the same meaning
  in another profile or manufacturer — always guard new handling with a model/family helper.
- Aqara private cluster `0xFCC0` reads and writes always need `[mfgCode: 0x115F]`.

---

## 8. Code conventions

### Standard `.groovy` header, in order

1. Line 1: `/* groovylint-disable <alphabetical rule list> */`
2. Block comment: title, community thread URL, Apache 2.0 boilerplate, credits, then the version
   history (`* ver. x.y.z  YYYY-MM-DD kkossev  - description`, long histories elided with
   `* ..............`), optionally followed by the author's live `TODO:` list.
3. `static String version()` / `static String timeStamp()`.
4. `@Field static final Boolean _DEBUG = false` and `DEFAULT_DEBUG_LOGGING`. **`_DEBUG` gates the
   test/debug commands — keep it `false` in anything released.**
5. Imports, then `#include kkossev.<lib>` lines (V3/V4 only), then `DEVICE_TYPE`.
6. `metadata { definition(name:, namespace: 'kkossev', author: 'Krassimir Kossev', importUrl: …,
   singleThreaded: true) { … } }`.

Libraries use a `library(base:, author:, category:, description:, name:, namespace:, importUrl:,
documentationLink:, version:)` block instead of `metadata`.

### Logging

- Helpers: `logDebug` / `logInfo` / `logWarn` / `logTrace` / `logError`, gated by the `logEnable` /
  `txtEnable` / `traceEnable` preferences.
- **`logWarn` is gated by `logEnable` (debug), not `txtEnable`** — warnings are invisible when debug
  logging is off. Long-standing repo-wide behaviour; changing it changes log volume for every user.
- Debug logging auto-disables after 24 h, trace after 30 min.
- **Never pass a closure to a log helper** (`logInfo { … }` logs garbage).
- Older files still use raw `if (settings?.logEnable) log.debug …`. **Match the style of the
  function you are editing** rather than converting it.

### Idioms to preserve, not "fix"

- **`fn(argName = value)`** — `initializeVars(fullInit = false)`, `sendEvent(…, isDigital = true)`,
  `runIn(delay = 200, …)`. These are *not* named parameters; they are assignment expressions that
  create a script-binding variable and pass the value positionally. They work. Match the style, do
  not rely on it in new code, and do not mass-refactor it.
- **`?.someBoolean?.value == true`** reads the private `value` field of `java.lang.Boolean`. It
  works in the sandbox. Preserve existing uses; write plain `== true` in new code.
- **Deliberate typos are load-bearing.** `setAccelarationInactive`, `clearFefreshRequest`,
  `isWierdTS0041`, `healthStatusCountTreshold`, `hasIlliminance`, `depricated`, `Huidity`,
  `isFankEver`, `timeRamaining` and friends appear as public command names, map keys, and strings
  passed to `runIn`/`schedule`. Renaming them breaks scheduled jobs, and renaming a profile key
  breaks devices already paired to it. **Don't.**
- Model and family detection always goes through **`is*()` helper predicates**. Never scatter inline
  `device.getDataValue('manufacturer') == …` comparisons — when adding a device, check every
  relevant helper, not just the fingerprint list.

### Reading the code before changing it

- **Verify every claim against the current code — never against the changelog, the header history,
  or a comment.** Those record what was true when written, not what is true now. When code and its
  own comment disagree, that is an ASK USER, not a licence to pick one.
- **A driver's commands and attributes are not all in the driver file.** For V3/V4 drivers the
  `#include`d libraries declare commands and attributes too, so grep `Libraries\` as well or you
  will conclude a driver has fewer than it does.
- **Reuse the existing abstraction before adding a new one.** Search the driver and its libraries
  for an existing helper first — in particular never call `log.debug` / `log.info` / `log.warn`
  directly where the `logDebug` / `logInfo` / `logWarn` helper exists. When reviewing a change,
  check explicitly for newly introduced direct `log.*` calls.

### Adding device support

- Copy the closest donor fingerprint, change **only** the model/manufacturer identity, append
  `// not tested!` to that line, and record which donor it came from.
- Datapoint numbers and attribute ids are cross-checked against zigbee-herdsman-converters
  (`src/devices/tuya.ts`, `sonoff.ts`) — many profiles already cite the exact converter.
- **Do not add capabilities or attributes merely to mirror Zigbee2MQTT.**
- If DP semantics differ from an existing profile, create a **new** profile key rather than
  overloading the existing one.
- Route unsupported DPs away from conflicting handlers, then debug-log and ignore them.
- Incomplete evidence → mark the work as awaiting device information rather than guessing behaviour
  into a publicly consumed driver ([CONTRIBUTING.md](CONTRIBUTING.md)).

---

## 9. Document conventions

All GitHub-facing files, including Markdown, are written in **English**.

Do not create parallel work-list files. Use the folder's existing `BUGS.md` / `TODO.md` and their
canonical item ids.

Before any Jira write in the `HUB` project, retrieve `HUB-126`, read its complete current
description, and follow it. Do not rely on a cached copy. If it cannot be retrieved, do not write
to Jira and report the blocker.

Forum findings follow a two-track rule:

- A clear, concrete software change is recorded in both the applicable driver's canonical
  `TODO.md` and a normal Jira `HUB` work item. Link the two with the canonical local item ID/path in
  Jira and a bare Jira key in `TODO.md`.
- A finding that does not yet define a concrete software change is recorded only as a Jira
  `Forum Watch` item. This includes uncertain evidence and resolved, declined, out-of-scope, or
  device-limitation findings. Promote it to dual tracking only after a concrete change is clear.
- Repository-wide Forum crawl/baseline Markdown files are migration archives, not canonical work
  lists. Do not add new work to them after Jira Forum Watch migration, and never remove or replace
  a driver's canonical `TODO.md` as part of that migration.

Rules that apply to every document here:

- **Prefer stable symbols over line numbers.** A "lines 542–688" map is wrong within one release.
  Cite the function, the method, or a quoted line and let the reader search for it. Where a
  document must pin line numbers (`BUGS.md` does), it states the exact version they refer to and
  says to re-locate by the quoted code.
- **Documentation-only edits need no version bump or timestamp change** — the §2 rule 4 embargo is
  about driver code.
- **Every support claim carries an evidence label**, never a bare `?`, `check` or `TODO` in a
  table: *Confirmed* (tested on a named hub/device combination), *Reported* (a user says so),
  *Implemented unverified*, *Unsupported*, *Unknown*, *Historical*. "It should work" is
  *Implemented unverified*, not *Confirmed*.
- **Never publish absolute local paths, private (RFC1918) hub addresses, device ids, or personal
  data** — including inside screenshots. This applies to anything that could become public.
- **Repository documents use bare Jira keys only** (for example, `HUB-6`). Never include an
  issue-tracker site URL or hostname in repository files, commit messages, or any other content
  that could become public.

### `BUGS.md` — reviewed bug work-list

- Header: the target file, its **exact version**, and "line numbers refer to this version"; a
  pointer to the folder's `AGENTS.md`; explicit exclusion of archive copies.
- Ground-rules block: one bug at a time in the user's order; each fix hub-tested and confirmed
  before the next; `[x]` only after confirmation; no version bumps or history lines after individual
  fixes.
- Status legend: `[ ]` open, `[x]` fixed (user-confirmed), `[?]` needs verification first.
- Severity sections: **A** = runtime exceptions, **B** = wrong behaviour, **C** = minor /
  inconsistency / dead code. Items numbered `A1…`, `B1…`, `C1…`.
- Per item: **Location** (file, function, line, quoted line) · **Problem** · **Failure scenario**
  (concrete input → wrong output) · **Fix** · **Impact radius** (library findings only) ·
  **Verification** · **Confidence**.
- Inline labels: **ASK USER** (the author's deliberate design or an open decision) and
  **VERIFY ON DEVICE** (depends on real payloads/hardware). Neither may be changed unconfirmed.
- Ends with an advisory fix order — the user picks the actual order.

### `TODO.md` — open user requests

- Provenance paragraph: every thread analysed, with topic id, title, post count and analysis date.
- Post-link shorthand, and the driver version the list was audited against.
- Its relationship to `BUGS.md` stated explicitly (feature requests and unresolved reports, not
  reviewed defects) and a closing "already covered elsewhere — do not duplicate" section.
- Items: `### N.M `[ ]` Title` under themed `## N.` sections, with `- Posts:` (deep links plus
  usernames), `- Approach:` / `- Code:` (`file:line`), `- Needs:`, `- Verification:`.
- Driver-header `TODO:` lists migrate here.
- Same ground rules as `BUGS.md`.

### `CHANGELOG.md`

Keep a Changelog: newest version first, dated version headings, the standard `Added` / `Changed` /
`Fixed` categories, user-facing summaries first and one `Developer notes` block per version. Record
notable completed changes only — open work belongs in `TODO.md` and full history in git. Never
paste a complete diff.

**Never head the top section `## [Unreleased]`.** Per §2 rule 4 / §5, a version number is the
*current development bucket* until the user explicitly bumps it — it is never "cut" or "released"
just because it has a dated heading. So the top section's heading is always `## [current version] -
YYYY-MM-DD`, matching whatever `version()` / `VERSION` / the header's latest `ver.` line says right
now, with a one-line note that this stays the working section until a bump is requested. New
work goes into that section's existing `Added`/`Changed`/`Fixed` lists; only start a new heading
above it once the user has actually bumped the version.

### `DEVICES.md`

Products and Links tables joined by `Key`. The manufacturer and profile columns are **synced from
the driver source** and are never hand-edited. The columns kkossev maintains by hand must **never**
be created, modified or deleted by an agent. The README "Supported models" section is generated
between the `<!-- BEGIN SUPPORTED-MODELS -->` / `<!-- END SUPPORTED-MODELS -->` markers.

### `*_PLAN.md`, `*_OPTIMIZATION*.md`

Open with a "planning document only — no code has been changed yet" blockquote naming the versions
it was written against, then an execution-rules blockquote (one step at a time, hub-test each, no
version bumps unless requested, regenerate the bundle or report it pending), a shared-library
warning where relevant, and numbered steps each carrying **Problem** · **Change** · **Files**
(`file:line`) · **Expected gain** · **Risk** · **Verify**.

---

## 10. Hard boundaries

- **Never upload or publish to the hub**, and never POST driver code to hub HTTP endpoints.
- Never re-enable code that is deliberately disabled or commented out; never delete "dead" reference
  maps casually. Each folder guide lists its own intentionally-disabled areas.
- Never rename: extension-less driver files, public attribute or command names (including the
  misspelled ones), profile keys, or the `custom*` hook names.
- Never hand-edit `.hubitat\metadata.json`.
- Never edit the hand-maintained columns of `DEVICES.md`.
- Never fold unrelated fixes into a device-support change.
- Never change `main`-branch content beyond its compatibility role
  ([CONTRIBUTING.md](CONTRIBUTING.md)).
- **Never commit secrets** — hub tokens, API keys, Maker API URLs, credentials — and never write
  them into a log line. Use a placeholder and tell the user what to substitute.

### Always stop and ask — these need the user, not a judgement call

- Any change to a **public attribute or command** (rename, removal, or changed semantics): existing
  user rules and dashboards depend on them.
- Any change spanning **multiple drivers**, or a shared library whose blast radius is ~28 drivers.
- Anything whose correctness can only be settled by **real hardware** — mark it VERIFY ON DEVICE
  and stop.
- Anything the folder guide flags as the author's **active experiment** or deliberately disabled.

---

## 11. Related repositories outside this tree

Some Hubitat work by the same author lives in **separate repositories**, with their own namespaces,
manifests and branches. Nothing in this guide applies to them unless that repository's own guide
says so — in particular the V3/V4 architecture (§4) is specific to this repository.

| Repository | Notes |
|---|---|
| `kkossev/Hubitat---Matter-Advanced-Bridge` | Matter bridge parent driver + its own `Libraries\` and `Components\` child drivers; shipped as an HPM bundle. Not the Zigbee V3 architecture. |
| `kkossev/hubitat-muxa-fork` | namespace `muxa`; its `drivers\` folder holds many unrelated drivers. |
| `kkossev/hubitat-matt-hammond-fork` | namespace `matthammonddotorg`. |

If a task names a driver that is not under `Drivers\` or `Libraries\` here, confirm which repository
it belongs to before editing anything.

---

## 12. Quick reference

| Question | Answer |
|---|---|
| Which file do I edit? | V3/V4 → the `#include` dev source (and the library master). Legacy → the single driver file. **Never** the `*_lib_included.groovy` bundle's logic, never an `Archives\` or `… ver X.Y.Z.groovy` copy. |
| Where does new device support go? | V4 → JSON profile DB. V3 → `deviceProfilesV3`. Legacy → fingerprint + hand-coded parse. New attribute name → **also** declare it in `metadata`. §4 |
| Do I bump the version? | No — only at a release point the user names. Documentation-only edits never. §2 rule 4, §5 |
| How is it tested? | The hub compiles on Save; the user runs the device test. There are no automated tests. §6 |
| Can I upload it to the hub? | No. Ever. The user does that. §2 rule 5 |
| What will the sandbox reject? | `getClass()` / `.class`, script-level `private static final`, methods over ~64 KB — the first two fail at **runtime**. §7 |
| Big-endian or little? | Tuya EF00 → big. Aqara `0xFCC0` and ZCL writes → little. §7 |
| Where do I search? | The one driver folder, plus `Libraries\`. Never the repo root, never the bundles. §6 |
| When do I stop and ask? | Public API changes, multi-driver or shared-library changes, anything needing hardware, anything flagged ASK USER / an active experiment. §10 |
