# Changelog

All notable changes to the **Matter Advanced Device** driver.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.0.0] - 2026-08-15

This stays the working section until a version bump is requested.

### Added

- First version of the driver — a general purpose utility for a directly paired Matter device,
  the Matter counterpart of the Hubitat built-in *Device* driver.
- **Read Something**: a plain English dropdown (*Firmware version*, *Battery level*,
  *On/Off state*, *Temperature*, *Thread network*, *Uptime and reboots* and more) that resolves
  the endpoint and cluster by itself, so no Matter knowledge is needed.
- **Discovery**: walks Descriptor `0x001D` — `PartsList`, `ServerList`, `DeviceTypeList`,
  `TagList` — across every endpoint, then reads Basic Information and the OTA cluster. The
  `AttributeList` of every cluster is read by *Get Info Advanced*, not by discovery.
- **Get Info**: the built-in *Device* driver's version — logs the Matter fingerprint of every
  endpoint. Sends nothing to the device.
- **Get Info Advanced**: dumps one endpoint or the whole device as a single sorted block in the
  live logs, every cluster and every attribute.
- **Picker**: endpoint / cluster / attribute dropdowns in the Preferences, rebuilt from the
  discovery results, with *Read Selected*, *Write Selected* and *Subscribe Selected*.
- **Expert commands**: `readAttribute`, `writeAttribute`, `invokeCommand`, `subscribeAttribute`.
  Every parameter accepts a name or a number.
- **utilities**: a one line command box with `?` help.
- **Firmware**: `firmwareVersion` and `firmwareVersionCode` from Basic Information `0x0028`, and
  `otaSupported` / `otaState` / `otaProgress` / `otaProvider` / `otaLastEvent` from the OTA
  Software Update Requestor cluster `0x002A`. `Watch Firmware Update` follows an update started by
  any controller on the fabric.
- **OTA probe suite** (`otaProbe`, debug builds only): six probes that establish, on real
  hardware, what Matter OTA actually allows on Hubitat. See `OTA_PROBE_PLAN.md`.
- **Housekeeping**: delete all child devices, current states, scheduled jobs and state variables.
- Health check with round trip time, and `Identify`.

### Changed

- The dump is printed one cluster at a time as the walk proceeds, instead of being accumulated in
  `state` and printed as one sorted block at the end. There is no line cap any more: the 400-line
  limit that had kept the accumulated dump inside the state size limit was silently discarding half
  the output of a 19-endpoint bridge. Each cluster now appears under its own heading as it is read,
  and the closing line reports the total.
- **Get Info** split in two: `getInfo` prints the Matter fingerprint of every endpoint, the way the
  built-in *Device* driver does and sending nothing to the device; `getInfoAdvanced` is the full
  cluster and attribute dump.
- The progress attribute is named `_status_` — the leading underscore keeps it at the top of the
  Current States list.
- `parse(String)` is a deliberate dead end: it logs that it was called and stops. Being called at
  all is the symptom worth seeing. The decoded-map path is selected by the `newParse` device data
  value, which `installed()`, `updated()` and now `configure()` all set.
- Every scalar attribute value is parsed as decimal. The platform hands scalars over as decimal
  strings and list elements as hex strings; both are now handled where they occur, not by guessing.
- `utilities nodes` lists every Matter node on the fabric with its node id, read from the hub's own
  inventory, which is where the node ids for `setprovider` come from.
- The `swapWriteBytes` preference was removed. Probe P5 established that `attributeWriteRequest`
  byte swaps the value itself on this platform, so the preference could only ever double swap and
  corrupt a multi-byte write. `cmdField` is the opposite and still pre-swapped by the caller.

### Fixed

- The command timeout is cleared only by an answer from the cluster that was written to or read
  from. Any inbound message used to clear it, so on a device that reports regularly no unanswered
  read was ever reported and the small-read-chunks fallback could never engage.
- A ping completes on its own `0x0028/0x0000` read-back rather than on any Basic Information
  message, so `rtt` cannot time an unrelated subscription report.
- The OTA provider node id is decoded numerically when the attribute is reported, instead of being
  recovered later with a regex that assumed node ids are large. Node 1 is a legal node id — it is
  what chip-tool's own OTA example uses — and the `otaProvider` attribute and `Check For Update`
  no longer contradict each other about whether a provider is configured.
- A dump only collects paths that the dump asked for. A subscription report from an unrelated
  endpoint used to be listed among the results and to keep the collector waiting; on a bridge that
  reports continuously, both happened on every discovery.
- One line per path in a dump, latest value wins. De-duplication compared the value too, so an
  attribute that changed while the dump was running produced several contradictory lines.
- The collector's chunk pacing is no longer reset by an unrelated read. Pressing *Ping* during a
  *Get Info Advanced* could let the quiet window elapse between chunks and count a cluster as silent.
- The picker's "run Get Info Advanced first" placeholder is a sentinel, not attribute `0x0000` — it
  used to be a selectable real attribute that would be read and presented as the user's choice.
- The OTA probe P4 verdict requires an Invoke callback from the OTA cluster, so running *Identify*
  during the probe window cannot be recorded as its answer.
- `configure()` sets the `newParse` device data value. Swapping an existing device's Type does not
  run `installed()`, and the documented flow ends at *Configure*, so the decoded-map path could go
  unarmed and every message would land in the dead-end `parse(String)`.
- `utilities nodes` reports a schema mismatch in the hub's Matter inventory JSON instead of an
  empty fabric. None of those key names is platform documented.
- GStrings are coerced to Strings before being stored in `state`.
- Expert commands no longer throw a NullPointerException on a device that has never been Configured.
  Swapping an existing device's Type does not run `installed()`, so the state maps may not exist yet;
  ten commands and scheduled handlers wrote into them without checking.
- One value in a dump line is cut at 200 characters, with the full length reported. Operational
  credentials are why: `NOCs`, `TrustedRootCertificates` and `Fabrics` each arrive as a base64 blob
  of a thousand characters or more, which is unreadable in a listing. A manual read of a single
  attribute is not shortened.
- The collector no longer decides whether a cluster answered by watching the printed line count.
  Replies are counted separately, so a cluster that answers without producing new lines is not
  mistaken for a silent one and the walk is not abandoned with "the device looks unreachable".
- Discovery no longer reads the OTA cluster on a device whose ServerList says it has none. Those
  reads are never answered, so they cost a full collector timeout and then raised a "no answer from
  the device" warning at the end of an otherwise clean discovery.
- `normalizeDescMap()` is gone. The platform's decoded map is used exactly as it arrives, and the
  handlers read only its `*Int` members, formatting hex at the point of display. The normalizer
  rewrote the caller's map to make both forms always available; the hex String members are the ones
  that are sometimes absent, so reading the ints is both simpler and more reliable.
- Matter 1.5 camera identities added: device type `0x0142` Camera, and cluster `0x0553`
  WebRTCTransportProvider alongside the `0x0551` CameraAvStreamManagement already known. Seen on an
  Aqara Camera Hub G350, endpoint 02.
- The sensor device type ids followed the Zigbee sequence rather than the Matter one, so a humidity
  sensor was reported as `DeviceType (0x0307)` while `0x0305` was labeled Humidity Sensor. Pressure
  is `0x0305`, Flow `0x0306`, Humidity `0x0307` and On/Off Sensor `0x0850`.

### Removed

- Dead code, none of it reachable: three collector phase constants left over from an earlier design
  (`INFO_STATE_ATTR_LIST`, `INFO_STATE_VALUES`, `INFO_STATE_SETTLE`), the `OTA_ANNOUNCEMENT_REASONS`
  name map, `getCommandName()`, and the `isRefresh` / `isSubscribing` state flags with the
  `setRefreshRequest()` / `clearRefreshRequest()` pair that maintained the first of them. `refresh()`
  no longer schedules a job to clear a flag nobody read.

### Developer notes

- Monolithic single file, deliberately. `matterLib` and `matterCommonLib` live in the Matter
  Advanced Bridge repository under the same `kkossev` namespace, so including them would either
  force a dependency on that bundle or collide with it on the hub. The name maps that are needed
  are copied in instead.
- Attribute names resolve from the **live Hubitat registry first**
  (`matter.getClusterAttributeName`, about 1895 pairs), falling back to the copied maps. This is
  the reverse of the Bridge's order, which is correct there because it keeps Hubitat-facing
  aliases such as OnOff `0x0000` → `Switch`; a diagnostic driver wants the true Matter names.
  Cluster *names* still come from the copied map — `matter.getClusterName()` is defective on
  platform 2.5.1.135 and returns the ID back as a decimal string.
- `Identify` sends a correctly byte swapped `UINT16 IdentifyTime`. The existing
  `Matter Advanced Outlet` / `Contact Sensor` / `RGBW Light` drivers send an unswapped `0101`
  there, which is 257 seconds rather than 1 — which is why their `identify` command is commented
  out as "can't make it work".
- The collector refuses to settle while read chunks are still going out (`notBefore`), and gives
  up entirely after five clusters in a row answer nothing, so an unreachable device fails in about
  a minute instead of grinding for eleven.
- Command parameter ENUM constraints are frozen when the driver is saved and cannot be rebuilt
  from discovered data, so the discovery driven dropdowns live in the Preferences instead.
- No command parameter is named `type` — Hubitat renders a driver dropdown when one is.
- Byte order is settled, and the two helpers disagree with each other: `attributeWriteRequest`
  swaps the value itself, so the caller must **not** pre-swap; `cmdField` passes bytes through
  verbatim, so the caller **must**. Probe P5 captured both on platform 2.5.1.156. The driver logs
  the exact bytes it sent and always schedules a **delayed** read back — delayed because Thread
  responses can arrive out of order.
