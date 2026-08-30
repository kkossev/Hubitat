# AGENTS.md — Tuya Zigbee mmWave Sensor (Hubitat driver)

> Working guide for AI agents. Written 2026-07-03 against driver **v4.2.4**.
>
> Repo-wide rules live in the repository-root [AGENTS.md](../../AGENTS.md), §2 *Golden rules*; where
> this folder guide and the root file conflict, the root file wins.
>
> Companion document:
> - `TODO.md` — open user requests harvested from forum thread 137410 (all 508 posts, analyzed 2026-07-11); feature requests + unresolved user reports, verified against v4.2.4 / JSON 4.1.5
>
> **Read this file fully before editing anything. The #1 way to break this project is to edit the wrong file** (see "Golden rules").

## What this is

A Hubitat Elevation Zigbee driver (Groovy) for ~31 families of Tuya/Sonoff/OWON mmWave presence radars. Author: Krassimir Kossev (kkossev). Community thread: https://community.hubitat.com/t/137410. Wiki: https://github.com/kkossev/Hubitat/wiki/Tuya-Zigbee-mmWave-Sensor. Public repo: `kkossev/Hubitat`, branch `development`.

**Core design:** the Groovy code is a generic *interpreter*; nearly all per-device behavior lives in a JSON "device profile" database (`deviceProfilesV4_mmWave.json`). Each profile defines fingerprints (model+manufacturer → profile matching), Tuya datapoints (`tuyaDPs`) or ZCL attributes (`attributes`), preferences, commands, refresh lists, and spammy-DP filters. Adding support for a new device usually means **editing JSON only, no Groovy**.

## File inventory and roles

| File | Role | Edit? |
|---|---|---|
| `Tuya_Zigbee_mmWave_Sensor.groovy` | **Source of truth** for driver code. 823 lines. Ends with 5 `#include kkossev.*` statements resolved against `C:\work\Hubitat\Libraries\` | ✅ Yes — this is the file you edit |
| `Tuya_Zigbee_mmWave_Sensor_lib_included.groovy` | **Generated build artifact**: driver + all 5 libraries pasted inline (~5,200 lines). This is what `importUrl` and `manifest.json` point at — it's what end users install | ⚠️ Never hand-edit logic. Only regenerate (see below) |
| `Tuya_Zigbee_mmWave_Sensor_lib_included - v.3.5.2.groovy` | Archived old V3-era release | ❌ Never touch |
| `deviceProfilesV4_mmWave.json` | The standard profile database. Own version field (currently **4.1.5**, independent of driver version). 31 profiles | ✅ Yes — for device support changes |
| `customDeviceProfiles/*.json` | Example per-device *custom* profile files users load via `loadUserCustomProfilesFromLocalStorage`. `SNZB-06P24.json` is the reference for ZCL-attribute-based (non-Tuya-DP) profiles | ✅ As needed |
| `manifest.json` | Hubitat Package Manager manifest. **Stale — still says 3.5.1** with V3-era release notes; the author has not maintained it for the 4.x branch | ⚠️ Only update if asked |
| `.hubitat/metadata.json` | Hubitat VSCode-extension mapping: source file ↔ hub driver id 2862 | ❌ Tool-managed |

**Shared libraries** (in `C:\work\Hubitat\Libraries\`, included via `#include`):

| Library | Owns | Notes |
|---|---|---|
| `commonLib.groovy` | `parse()` entry, cluster routing, install/updated/initialize lifecycle, health check, Tuya EF00 cluster parsing (`standardParseTuyaCluster` → `standardProcessTuyaDP`) | Shared by many other kkossev drivers — changes here have wide blast radius |
| `deviceProfileLibV4.groovy` | Everything profile-shaped: JSON load/cache, fingerprint generation, DP↔attribute translation (`processTuyaDPfromDeviceProfile` → `processFoundItem`), `setPar`/`sendCommand`, dynamic preferences, GitHub download. Currently **v4.1.2 (2026-07-03)** | ⚠️ `deviceProfileLib.groovy` (no V4) is the **legacy V3 library with similarly-named methods** — this driver does NOT use it. Always check the filename when grepping |
| `motionLib.groovy` | `motion` semantics: `handleMotion()`, reset timer, invert option | |
| `batteryLib.groovy` | `battery`/`batteryVoltage` from ZCL 0x0001 or Tuya DP | |
| `illuminanceLib.groovy` | `illuminance` with threshold/debounce: `handleIlluminanceEvent()` | |

## Golden rules

1. **Edit `Tuya_Zigbee_mmWave_Sensor.groovy` (and/or the library files), never the `_lib_included` bundle.** The bundle is generated, and only when the maintainer explicitly asks for it — never as a finishing step after a code change. **Never assemble it by hand.** Pasting library bodies after the driver source leaves the source’s `#include` lines in place on top of the inlined libraries, so every library is included twice and the driver will not compile. The Hubitat hub does the assembly. Normally the maintainer drives it: the driver and all five libraries it includes must be on a hub and current, then `GET /driver/downloadFull/<driverId>` returns the expanded source with the `#include` lines stripped and `// library marker kkossev.<lib>, line N` suffixes added. Save the result with **CRLF** endings; the repo is `core.autocrlf=true` while the endpoint returns LF.

   An agent may push the sources to the hub and run that download itself **only when the maintainer explicitly asks for it in that session** — root §2 rule 5 otherwise forbids uploading to the hub. Before downloading, verify every library read-back matches the repo: the hub expands from *its own* copies, so a stale library is silently baked in.
2. **Check bundle sync before and after any code change.** Compare `static String version()` (near line 42 of each file) — they must match. If you change the source or a library, the bundle must be regenerated or explicitly reported as pending regeneration. (Last verified in sync at 4.2.4 on 2026-07-03.)
3. **Version bumps happen only when the user says so** (release point — never after an individual bug fix; the repository-root [AGENTS.md](../../AGENTS.md), §2 *Golden rules*, rule 4). At that point bump three things in the source driver: `version()` string, `timeStamp()` string, and a new `* ver. X.Y.Z  date kkossev - description` line in the header comment block. The JSON database has its *own* `version`/`timestamp` fields at the bottom of the file — **those are independent of the driver version and are bumped whenever the JSON is edited**. They do not trigger anything by themselves; the driver records them in `state.profilesV4` and reports them in the load confirmation (`deviceProfileLibV4` ~lines 2113, 2251), so they are how you and users tell which database is actually loaded.
4. **JSON changes don't reach users until the file is (a) pushed to GitHub `development` branch and/or (b) uploaded to the hub's local File Manager storage** — the driver loads `http://<hubIP>:8080/local/deviceProfilesV4_mmWave.json` at runtime, falling back to a one-time GitHub download. Editing the file on disk here does nothing by itself.
5. **New fingerprints require a two-step activation on the hub:** running `loadStandardProfilesFromGitHub`/`...FromLocalStorage` refreshes DP-processing immediately, but the `metadata { fingerprint }` block only re-evaluates on driver-code re-save or hub reboot. Mention this in any change note involving fingerprints.
6. **JSON must survive Groovy's `JsonSlurper`.** A known unresolved production bug (author TODO, header line ~37) is an intermittent parse exception. Be strict: no trailing commas (note: `customDeviceProfiles/SNZB-06P24.json` currently *has* a trailing comma at the end of its `attributes` array — a latent hazard), keys double-quoted, validate with a parser before committing.
7. The driver is `singleThreaded: true`, but the profile caches are `@Field static` — **shared JVM-wide across all devices using this driver**. A bad load from one device affects all of them. Cooldown (`g_loadProfilesCooldown`, 30 s) is also global.

## Architecture in one paragraph

Incoming Zigbee messages hit `parse()` in commonLib; Tuya cluster `EF00` payloads are split into datapoints and each DP is looked up in the matched profile's `tuyaDPs` array (`processTuyaDPfromDeviceProfile` in deviceProfileLibV4). The found entry drives value scaling (`scale`), enum mapping (`map`), unit, and event emission; attribute names `motion` / `illuminance` are intercepted by the driver's `customProcessDeviceProfileEvent()` (in the driver file) and routed to motionLib/illuminanceLib instead of a plain `sendEvent`. The reverse direction (`setPar`, preference saves) uses the *same* JSON entries to validate, scale, and encode Tuya DP writes or ZCL attribute writes. Profiles are matched to devices by exact `model` + `manufacturer` string match.

Profile loading is three-tiered: hub local storage (primary) → automatic one-time GitHub download that saves into local storage (fallback) → optional per-device custom JSON overlay keyed by DNI. Caches: `g_deviceProfilesV4` (full DB), `g_deviceFingerprintsV4` (lightweight index used to *generate* `fingerprint` metadata at class-load time — empty on cold boot ⇒ zero fingerprints declared, the known "cold-start problem"), `g_currentProfilesV4` / `g_customProfilesV4` (per-DNI).

## Recipes

### Add support for a new manufacturer of an existing device type
1. Identify the closest existing profile (grep the JSON for a similar device; profile keys look like `TS0601_TUYA_RADAR`, `TS0225_LINPTECH_RADAR`, …).
2. Add `{ "model": "TS0601", "manufacturer": "_TZExxx_xxxxxxxx", "deviceJoinName": "..." }` to that profile's `fingerprints` array. Cross-check DP behavior against Zigbee2MQTT (`zigbee-herdsman-converters/src/devices/tuya.ts`) — several profiles link the exact converter in their `comments`.
3. Bump JSON `version` + `timestamp` (bottom of file). No Groovy change, no driver version bump needed.
4. If DP semantics differ (e.g. inverted motion map — see `TS0601_TUYA_RADAR_2` vs `TS0601_TUYA_RADAR`), create a new profile key instead of overloading an existing one.

### Add a whole new device profile
Copy the most similar profile block as a template. Required keys: `description`, `device` (powerSource, `ignoreIAS`/`isIAS` flags), `capabilities`, `preferences` (prefName → DP number or `"0xCCCC:0xAAAA"`), `defaultFingerprint` (supplies profileId/endpoint/clusters that individual fingerprints inherit), `fingerprints`, `tuyaDPs` **or** `attributes` (ZCL-based, see `customDeviceProfiles/SNZB-06P24.json`), `refresh`, and optionally `spammyDPsToIgnore` (dropped entirely when user enables `ignoreDistance`) / `spammyDPsToNotTrace` (processed but not logged). If the profile introduces a brand-new attribute name, it must ALSO be declared as an `attribute` in the driver metadata block (driver source, ~lines 83–121) — attribute declarations are static Groovy, not generated from JSON.

### Change driver/library code
1. Edit the source `.groovy` (or library in `Libraries\`). The driver file itself is mostly `custom*()` hooks called by commonLib — keep device-specific glue here, generic logic in the libraries.
2. Bump `version()`, `timeStamp()`, header changelog (rule 3). Library files carry their own `version:` field in the `library()` header and their own changelog.
3. Regenerate the `_lib_included` bundle (rule 1) — or state clearly that it's pending.
4. Test path: the Hubitat VSCode extension pushes the *source* file to hub driver id 2862 (the hub resolves `#include` against libraries installed on the hub — libraries changed locally must be pushed to the hub too).

### Debug profile-loading problems
Check device State Variables: `state.profilesV4` (`lastJSONSource`, `version`, `loadProfilesCtr`, `cooldownSkipsCtr`, `lastReadFileError`) and `state.gitHubV4` (`httpGetLastStatus`, `lastDownloadError`, `lastException`). Enable `_DEBUG = true` (driver line ~45) to expose the `test`/`cacheTest` commands (`cacheTest 'Info'` dumps cache stats; `'Initialize'` forces reload; `'Clear'` wipes). The three user-facing recovery commands are `loadStandardProfilesFromGitHub`, `loadStandardProfilesFromLocalStorage`, `loadUserCustomProfilesFromLocalStorage(filename)`.

## Known pitfalls (verified, not hypothetical)

- **Stale bundle** — see rule 2. Verify `version()` in both files matches before assuming they're in sync.
- **Stale `manifest.json`** (3.5.1 vs driver 4.2.4) — HPM release notes were abandoned mid-V3.
- **V3 vs V4 library confusion** — `deviceProfileLib.groovy` (legacy) and `deviceProfileLibV4.groovy` coexist in `Libraries\` with near-identical method names. This driver uses V4 only.
- **Cold-start fingerprint gap** — after hub reboot/first install the static caches are empty when `metadata{}` evaluates, so zero fingerprints are declared and newly-pairing devices won't auto-match the driver until profiles load *and* the driver is re-saved. Acknowledged, unfixed.
- **String-dispatched dynamic calls** — JSON `commands` values and `customSet<Name>`/`preProc` hooks are resolved by name at runtime (`"${func}"(val)`); typos fail silently as MissingMethodException in logs, never at compile time.
- **`customProcessDeviceProfileEvent` name contract** — the driver's switch on `'motion'` / `'illuminance'` / `'illuminance_lux'` must match JSON `tuyaDPs[].name` exactly, or the DP silently falls through to plain `sendEvent` (skipping debounce/invert logic).

## v4.2.4 delta

1. Fixed `updateIndicatorLight()` call typo in `customParseZdoClusters()` (driver file) — restores the black-square-radar LED-state resync on device announce.
2. deviceProfileLibV4 → v4.1.2: standardized the custom-profile filename state key to `customJSONFilename` (removed stale `customFilename` duplicate on revert to standard profiles).
3. Fixed `clearProfilesCache()` not resetting the cooldown flag, which made reload fail right after an explicit GitHub download.

Open TODOs (driver header, lines ~34–39): wiki info page; show profile key + name in Preferences; handle ZDO cluster 8032 after reboot; root-cause the intermittent `JsonSlurper` parse exception; don't attempt profile load before device metadata exists (freshly-paired device).
