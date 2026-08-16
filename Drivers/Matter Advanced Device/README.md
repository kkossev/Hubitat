# Matter Advanced Device

A general purpose utility driver for a Matter device that is paired directly to a Hubitat hub —
the Matter equivalent of the built-in **Device** driver. Use it to find out what a Matter device
actually is, what it can do, what firmware it is running, and to read or change any of its
settings.

It is a diagnostic tool, not a driver you leave assigned to a device you use every day.

Current development source version: 1.0.0

> Status: **partly tested on real hardware** (2026-08-15/16) — an IKEA GRILLPLATS plug and an IKEA
> DIRIGERA bridge. Discovery, *Get Info*, *Get Info Advanced*, subscriptions and the OTA probe
> suite have all run against real devices. The write path and the picker have not. What Matter OTA
> allows on Hubitat is now established — see *Firmware updates* below.

## Features

- **A plain English menu.** Pick *Firmware version*, *Battery level*, *Temperature*, *On/Off
  state* and so on from a dropdown. You never have to know what an endpoint or a cluster is — the
  driver finds them for you.
- **Full discovery.** Walks every endpoint and names every cluster it finds.
- **Get Info.** Logs the Matter fingerprint of every endpoint, exactly like the built-in *Device*
  driver.
- **Get Info Advanced.** Dumps one endpoint, or the whole device, as a single readable block in
  the logs — every cluster and every attribute.
- **A picker for the curious.** Once the device has been discovered, the Preferences offer
  endpoint and cluster dropdowns filled in from that device, plus *Read Selected*, *Write Selected*
  and *Subscribe Selected*. Run *Get Info Advanced* once to fill in the attribute dropdown as well.
- **Expert commands.** Read, write, invoke and subscribe to anything, by name or by number.
  `OnOff`, `0x0006`, `0006` and `6` all mean the same cluster.
- **A one line command box** for people who would rather type: `read 1 OnOff OnOff`. It also lists
  every Matter node on your fabric (`nodes`), which is where the node ids for `setprovider` come from.
- **Firmware version and Matter update status**, including live progress of an update started by
  any app on the same Matter fabric.
- **Housekeeping**, the same set the built-in Device driver offers: delete all child devices,
  current states, scheduled jobs or state variables.

## Installation

Import URL:

```text
https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Matter%20Advanced%20Device/Matter_Advanced_Device.groovy
```

Then, on the device you want to inspect, change **Type** to *Matter Advanced Device* and press
**Save Device**. Do not delete and re-pair the device.

Press **Configure** once. Discovery takes a few seconds and fills in the endpoint list that
everything else relies on.

To go back, change **Type** to whatever it was before and press **Save Device** again.

## Firmware updates

**What works today:** reading the firmware version, and watching an update happen.

`Get Firmware Info` reads the version string, the hardware version and the Matter update status.
`Watch Firmware Update` follows an update that is already running — including one started by the
manufacturer's own app, or by Apple, Google or Alexa on the same Matter fabric. The `otaState` and
`otaProgress` attributes track it from *querying* through *downloading 0…100 %* to *applying*.

**What cannot work:** this driver can never send you firmware. Matter delivers the image over BDX
from an **OTA Provider** node commissioned on the fabric, and Hubitat's Groovy Matter API has no
BDX support at all. No Hubitat driver can be an OTA Provider.

**What was established** (probe suite, 2026-08-15/16, platform 2.5.1.156 — full record in
`OTA_PROBE_PLAN.md`): `Check for Firmware Update` asks the device to check its update server using
the Matter `AnnounceOTAProvider` command, and that command *is* reachable. What is missing is
anything to announce. Every plausible candidate on a 26-node fabric was examined directly — the
Hubitat hub, an IKEA DIRIGERA and three Aqara hubs — and **not one is an OTA Provider**. Announcing
a node that is not one leaves the device sitting in *querying* until it times out. So the command
tells you there is no update server and points you at the manufacturer's app, rather than
pretending.

Vendors do update these devices, constantly — over their own application protocols. A Matter device
exposes an OTA Requestor because the specification requires one, not because that is how its
manufacturer ships firmware.

`Watch Firmware Update` is the half that works: the event pipeline is hardware confirmed, so an
update started by the manufacturer's app or by another controller on the fabric shows up here.

## Commands

The commands are arranged in tiers. Tier 1 needs no Matter knowledge at all; tier 3 assumes you know
what a cluster is. Everything here is safe to press except the writes and invokes, which is why
those are locked behind a preference.

Every command logs its result. Most also put a one line answer in the `lastResult` attribute, so you
can read it on the device page without opening the logs.

### Start here

**`Read Something`** — the plain English menu, and the reason this driver exists. Pick *Firmware
version*, *Battery level*, *Temperature*, *On/Off state*, *Thread network*, *Uptime and reboots* and
so on; the driver works out which endpoint and cluster holds that value on this particular device
and reads it. If the device has not been discovered yet it tells you to press **Configure** first;
if the device genuinely has no such function it says that instead, naming the cluster it looked for.

Two entries are shortcuts rather than reads: *Endpoints and features* runs discovery, and
*Everything about this device* runs the full dump.

### Setting up

**`Configure`** — discovery, then subscribe. Walks the Descriptor cluster of every endpoint to learn
what the device is, reads Basic Information, reads the OTA cluster if the device has one, and
subscribes to the firmware version so later changes arrive on their own. Press it once after
assigning the driver. Everything in tiers 2 and 3 depends on what it learns.

**`Discover All`** — re-runs that discovery without touching the subscription. Use it after the
device changes — a bridge that gained a child, for instance.

**`Re Subscribe`** / **`Unsubscribe Matter`** — rebuild or cancel this driver's Matter subscription.
The subscription is how reports arrive unprompted; the intervals are set in the Preferences.

### Looking at the device

**`Get Info`** — the built-in *Device* driver's version: one Matter fingerprint line per endpoint,
straight from the platform. Sends nothing to the device, so it works even on an unreachable one.

**`Get Info Advanced`** *(endpoint, optional)* — the full dump: every cluster and every attribute,
printed one cluster at a time as it goes. Leave the endpoint blank for the whole device, or give one
(`1`, `0x0B`) to dump just that endpoint. A large bridge produces several hundred values and takes a
minute or two — the Aqara M3 gives 810 values across 83 clusters. Run it once before using the
picker, since it is what fills in the attribute dropdown.

**`Ping`** — round trip time to the device, reported in the `rtt` attribute, with running min, max
and average kept in `state.stats`.

**`Refresh`** — re-reads the identity and firmware information. The standard Hubitat refresh.

### Firmware

**`Get Firmware Info`** — firmware version, hardware version and the Matter update status. On a
device with no OTA cluster it says so and skips the pointless read.

**`Check For Update`** — asks the device to go and check its update server, via the Matter
`AnnounceOTAProvider` command. Read *Firmware updates* above before expecting much: if no OTA
Provider exists on your fabric, this tells you so and explains where updates actually come from.

**`Watch Firmware Update`** *(minutes, default 30)* — subscribes to the OTA state and progress
attributes and follows an update that is already running, **including one started by the
manufacturer's app or by another controller on the same fabric**. This is the half of the firmware
story that works everywhere. `otaState` and `otaProgress` track it live.

### Safe actions

**`Identify`** *(seconds, default 10)* — makes the device blink so you can tell which one it is. The
driver finds an endpoint with the Identify cluster by itself.

### The picker — tier 2

The Preferences carry endpoint, cluster and attribute dropdowns, filled in from this device's own
discovery results. They live in the Preferences rather than in the command parameters because
Hubitat freezes command dropdowns when the driver is saved and they cannot be rebuilt from
discovered data.

Choose the three, press **Save Preferences**, then:

**`Read Selected`** — reads it. **`Subscribe Selected`** — adds it to the subscription so it reports
by itself. **`Write Selected`** *(value, dataType)* — writes it.

The attribute dropdown stays empty until *Get Info Advanced* has run on that cluster, because only
that command reads the cluster's attribute list.

### Expert, free text — tier 3

Every parameter takes a name or a number, and numbers may be written any way you like: `OnOff`,
`0x0006`, `0006` and `6` all mean the same cluster. A leading zero means hexadecimal, the way Matter
IDs are normally written; a plain number without one is decimal.

**`Read Attribute`** *(endpoint, cluster, attribute)* — one attribute, or the whole cluster if you
leave the attribute blank. Endpoint blank means 0, the device itself.

**`Write Attribute`** *(endpoint, cluster, attribute, dataType, value)* — writes, then reads the
value back a few seconds later to confirm it. The delay is deliberate: a Thread device can answer an
immediate read with the old value. Leave the value blank for Boolean and Null types.

**`Invoke Command`** *(endpoint, cluster, command, fields)* — invokes a cluster command. Fields are
optional and comma separated, each one `dataType:tag:value` — for example `UINT16:0:10`.

**`Subscribe Attribute`** *(add | remove | show, endpoint, cluster, attribute)* — manages this
driver's subscription list one attribute at a time. `show` lists what is currently subscribed. The
Descriptor cluster `0x001D` is deliberately refused: subscribing to it destabilizes the Matter stack.

**`Utilities`** — a one line command box for people who would rather type than click. See below.

Writing and invoking are locked until **Advanced options** is switched on in the Preferences.

### Housekeeping

The same set the built-in *Device* driver offers, and they behave the same way.

**`Delete All Child Devices`** — removes every child device this driver created.
**`Delete All Current States`** — clears the Current States list.
**`Delete All Scheduled Jobs`** — cancels every scheduled job, including the health check.
**`Delete All States`** — erases the State Variables and leaves them erased. Discovery results go
with them, so press **Configure** again afterwards.

### The utilities box

`Utilities` takes one line of text and runs it. Type `?` or leave it blank for this list in the
logs. Names or numbers both work, exactly as in the free text commands.

| Line | What it does |
|---|---|
| `read <endpoint> <cluster> <attribute>` | One attribute, e.g. `read 1 OnOff OnOff` |
| `readall <endpoint> <cluster>` | Every attribute of that cluster |
| `write <endpoint> <cluster> <attribute> <dataType> <value>` | Needs *Advanced options* switched on |
| `invoke <endpoint> <cluster> <command> [dataType:tag:value,...]` | Needs *Advanced options* switched on |
| `subscribe add\|remove <endpoint> <cluster> <attribute>` | Add or drop one attribute from the subscription |
| `info [endpoint]`, `firmware`, `checkupdate` | The same as *Get Info Advanced*, *Get Firmware Info* and *Check For Update* |
| `setprovider <nodeId>` | Which OTA Provider node `checkupdate` announces; `setprovider 0` clears it |
| `nodes` | Every Matter node on this fabric, with its node id, read from the hub itself |
| `unsubscribe`, `subscriptions` | Cancel the subscription; list what is subscribed |
| `discover`, `endpoints` | Re-run discovery; print the endpoint summary |
| `stats`, `resetstats` | Traffic and ping counters |
| `probe [ALL\|P1..P6]` | The OTA research probes, debug builds only |

## Attributes

`vendorName`, `productName`, `nodeLabel`, `serialNumber`, `hardwareVersionString`,
`specificationVersion`, `endpointsCount`, `firmwareVersion`, `firmwareVersionCode`,
`otaSupported`, `otaState`, `otaProgress`, `otaProvider`, `otaLastEvent`, `lastResult`,
`healthStatus`, `rtt`, `_status_`.

`lastResult` is where the answer to the last command appears, so you do not have to open the logs.
`_status_` is the progress line — the leading underscore keeps it at the top of the Current States
list.

## Notes

- `firmwareVersion` is the Matter `SoftwareVersionString`, which is the authoritative one.
  `firmwareVersionCode` is the raw `SoftwareVersion` number; it is vendor defined with no standard
  packing, so it is kept unformatted. It is what an OTA update compares against.
- Numbers may be written as `0x0006`, `0006` or `6`. A leading zero means hexadecimal, the way
  Matter IDs are normally written; a plain number without one is decimal.
- The driver deliberately refuses to subscribe to the Descriptor cluster `0x001D` — doing so
  destabilises the Matter stack.

(last edited 2026-08-16)
