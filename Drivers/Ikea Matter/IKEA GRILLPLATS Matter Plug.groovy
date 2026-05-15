/*
 * IKEA GRILLPLATS Matter Plug (On/Off + Power + Energy + Voltage + Current)
 *
 * Device endpoints:
 *   EP0  : Basic Information (0x0028), General Diagnostics (0x0033)
 *   EP1  : On/Off Plug-in Unit — OnOff cluster (0x0006)
 *   EP2  : Electrical Sensor  — Power Measurement (0x0090) + Energy Measurement (0x0091)
 *
 * Last edited: 2026/05/15 10:51 AM
 */

import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(name: "IKEA GRILLPLATS Matter Plug w/ healthStatus", namespace: "community", author: "kkossev + ChatGPT + Claude", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Ikea%20Matter/IKEA%20GRILLPLATS%20Matter%20Plug.groovy") {

        capability "Switch"
        capability "Outlet"
        capability "PowerMeter"
        capability "EnergyMeter"
        capability "VoltageMeasurement"
        capability "CurrentMeter"
        capability "Refresh"
        capability "Initialize"
        capability "HealthCheck"

        attribute "frequency",    "number"   // Hz
        attribute "powerFactor",  "number"   // %
        attribute "healthStatus", "enum",   ["online", "offline"]
        attribute "rtt",          "number"

        fingerprint endpointId: "01",
                inClusters: "0003,0004,0006,001D",
                outClusters: "",
                model: "GRILLPLATS Plug",
                manufacturer: "IKEA of Sweden",
                controllerType: "MAT"
    }

    preferences {
        input name: "txtEnable",         type: "bool", title: "Enable descriptionText logging",                              defaultValue: true
        input name: "logEnable",         type: "bool", title: "Enable debug logging",                                       defaultValue: false
        input name: "enableHealthCheck", type: "bool", title: "Enable health check (ping every 5 min)",                     defaultValue: true
        input name: "enableAutoReInit",  type: "bool", title: "Auto re-initialize after 2 consecutive ping failures",       defaultValue: true
    }
}

void installed() {
    state.stats = [initializeCounter: 0, pingFailCounter: 0, autoReInitCounter: 0]
    initialize()
}

void updated() {
    logInfo "updated..."
    if (logEnable) runIn(7200, "logsOff")
    initialize()
}

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    logWarn "Debug logging disabled"
}

void initialize() {
    if (state.stats == null) { state.stats = [initializeCounter: 0, pingFailCounter: 0, autoReInitCounter: 0] }
    state.stats.initializeCounter = (state.stats.initializeCounter ?: 0) + 1
    unschedule("deviceHealthCheck")
    unschedule("pingTimeout")
    unschedule("autoReInit")
    state.pingStart = null
    state.pingConsecutiveFails = 0
    if (getDataValue("newParse") != "true") { device.updateDataValue("newParse", "true") }
    logInfo "initialize... (initializeCounter=${state.stats.initializeCounter})"
    logInfo "model=${device.getDataValue('model') ?: device.model} newParse=${getDataValue("newParse")} uptime=${location.hub.uptime}"
    subscribeToAttributes()
    refresh()
    if (enableHealthCheck != false) { runEvery5Minutes("deviceHealthCheck") }
}

void refresh() {
    logDebug "refresh()"
    List<Map<String,String>> paths = []
    // EP1 — Switch
    paths.add(matter.attributePath(0x01, 0x0006, 0x0000)) // OnOff
    // EP2 — Electrical Power Measurement (cluster 0x0090)
    paths.add(matter.attributePath(0x02, 0x0090, 0x0008)) // ActivePower    (mW)
    paths.add(matter.attributePath(0x02, 0x0090, 0x000B)) // RMSVoltage     (mV)
    paths.add(matter.attributePath(0x02, 0x0090, 0x000C)) // RMSCurrent     (mA)
    paths.add(matter.attributePath(0x02, 0x0090, 0x000E)) // Frequency      (mHz)
    paths.add(matter.attributePath(0x02, 0x0090, 0x0011)) // PowerFactor    (×100 %)
    // EP2 — Electrical Energy Measurement (cluster 0x0091)
    paths.add(matter.attributePath(0x02, 0x0091, 0x0001)) // CumulativeEnergyImported (struct; tag 0 = mWh)
    // EP0 — Basic Information
    paths.add(matter.attributePath(0x00, 0x0028, 0x000A)) // SoftwareVersionString

    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private void subscribeToAttributes() {
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0006, 0x0000)) // OnOff
    paths.add(matter.attributePath(0x02, 0x0090, 0x0008)) // ActivePower
    paths.add(matter.attributePath(0x02, 0x0090, 0x000B)) // RMSVoltage
    paths.add(matter.attributePath(0x02, 0x0090, 0x000C)) // RMSCurrent
    paths.add(matter.attributePath(0x02, 0x0090, 0x000E)) // Frequency
    paths.add(matter.attributePath(0x02, 0x0090, 0x0011)) // PowerFactor
    paths.add(matter.attributePath(0x02, 0x0091, 0x0001)) // CumulativeEnergyImported

    String cmd = matter.cleanSubscribe(1, 600, paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))

    logInfo "subscribing to on/off (EP1) + power/voltage/current/freq/pf (EP2/0x0090) + energy (EP2/0x0091)"
}

/* ---------- switch commands ---------- */

void on() {
    String cmd = matter.on()
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

void off() {
    String cmd = matter.off()
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

/* ---------- parse ---------- */

void parse(String description) {
    logDebug "parse(String) called - ignored (newParse:true mode only)"
}

// parse(Map) - newParse:true format only
// OnOff report    : [callbackType:Report, endpointInt:1, clusterInt:6,   attrInt:0,  value:true]
// Power report    : [callbackType:Report, endpointInt:2, clusterInt:144, attrInt:8,  value:12500]
// Energy report   : [callbackType:Report, endpointInt:2, clusterInt:145, attrInt:1,  value:[0:334000, 4:1060350679]]
void parse(Map msg) {
    logDebug "parse(Map) received: ${msg}"
    handleLiveness(msg)

    Integer ep     = msg.endpointInt
    Integer clus   = msg.clusterInt
    Integer attrId = msg.attrInt

    if (ep == null || clus == null || attrId == null) return

    // Software version string (Basic Information cluster 0x0028, attr 0x000A) — EP0
    if (clus == 0x0028 && attrId == 0x000A) {
        String ver = msg.value?.toString() ?: ""
        device.updateDataValue("softwareVersion", ver)
        logInfo "softwareVersion=${ver}"
        return
    }

    // OnOff: EP1 cluster 0x0006 attr 0x0000
    if (ep == 0x01 && clus == 0x0006 && attrId == 0x0000) {
        Integer v = safeInt(msg.value)
        if (v != null) {
            String sw = (v != 0) ? "on" : "off"
            sendEvent(name: "switch", value: sw, descriptionText: txtEnable ? "Switch is ${sw}" : null, type: "physical")
            logInfo "Switch is ${sw}"
        }
        return
    }

    // All remaining attributes are on EP2
    if (ep != 0x02) {
        logDebug "Ignoring endpointInt=${ep} cluster=${clus}"
        return
    }

    // --- Electrical Power Measurement (cluster 0x0090) ---

    // ActivePower: attr 0x0008 (mW → W)
    if (clus == 0x0090 && attrId == 0x0008) {
        Long raw = safeLong(msg.value)
        if (raw != null) {
            BigDecimal w = (raw / 1000.0).setScale(1, BigDecimal.ROUND_HALF_UP)
            sendEvent(name: "power", value: w, unit: "W", descriptionText: txtEnable ? "Power is ${w} W" : null, isStateChange: true)
            logInfo "Power is ${w} W"
        }
        return
    }

    // RMSVoltage: attr 0x000B (mV → V)
    if (clus == 0x0090 && attrId == 0x000B) {
        Long raw = safeLong(msg.value)
        if (raw != null) {
            BigDecimal v = (raw / 1000.0).setScale(1, BigDecimal.ROUND_HALF_UP)
            sendEvent(name: "voltage", value: v, unit: "V", descriptionText: txtEnable ? "Voltage is ${v} V" : null, isStateChange: true)
            logInfo "Voltage is ${v} V"
        }
        return
    }

    // RMSCurrent: attr 0x000C (mA → A)
    if (clus == 0x0090 && attrId == 0x000C) {
        Long raw = safeLong(msg.value)
        if (raw != null) {
            BigDecimal a = (raw / 1000.0).setScale(3, BigDecimal.ROUND_HALF_UP)
            sendEvent(name: "amperage", value: a, unit: "A", descriptionText: txtEnable ? "Current is ${a} A" : null)
            logInfo "Current is ${a} A"
        }
        return
    }

    // Frequency: attr 0x000E (Hz — device reports directly in Hz, not mHz)
    if (clus == 0x0090 && attrId == 0x000E) {
        Long raw = safeLong(msg.value)
        if (raw != null) {
            BigDecimal hz = new BigDecimal(raw).setScale(1, BigDecimal.ROUND_HALF_UP)
            sendEvent(name: "frequency", value: hz, unit: "Hz", descriptionText: txtEnable ? "Frequency is ${hz} Hz" : null)
            logInfo "Frequency is ${hz} Hz"
        }
        return
    }

    // PowerFactor: attr 0x0011 (×100 % → %)
    if (clus == 0x0090 && attrId == 0x0011) {
        Long raw = safeLong(msg.value)
        if (raw != null) {
            BigDecimal pf = (raw / 100.0).setScale(1, BigDecimal.ROUND_HALF_UP)
            sendEvent(name: "powerFactor", value: pf, unit: "%", descriptionText: txtEnable ? "Power factor is ${pf}%" : null)
            logInfo "Power factor is ${pf}%"
        }
        return
    }

    // --- Electrical Energy Measurement (cluster 0x0091) ---

    // CumulativeEnergyImported: attr 0x0001 (struct; tag 0 = mWh -> kWh)
    // data:[1:STRUCT:[4:UINT:1067348117, 0:INT:361000]] — struct keys are strings like "0:INT", "4:UINT"
    if (clus == 0x0091 && attrId == 0x0001) {
        def structData = msg.data?.values()?.find { it instanceof Map }
        Long mwh = structData ? safeLong(structData.find { k, v -> k.toString().startsWith('0:') }?.value) : null
        if (mwh != null) {
            BigDecimal kwh = (mwh / 1000000.0).setScale(3, BigDecimal.ROUND_HALF_UP)
            sendEvent(name: "energy", value: kwh, unit: "kWh", descriptionText: txtEnable ? "Energy is ${kwh} kWh" : null, isStateChange: true)
            logInfo "Energy is ${kwh} kWh"
        } else {
            logWarn "CumulativeEnergyImported: could not parse from data=${msg.data}"
        }
        return
    }

    logDebug "parse(Map): unhandled msg: ${msg}"
}

/* ---------- helpers ---------- */

private Integer safeInt(def v) {
    if (v == null) return null
    if (v instanceof Boolean) return v ? 1 : 0
    try { return Integer.parseInt(v.toString(), 10) } catch (Exception ignored) { return null }
}

private Long safeLong(def v) {
    if (v == null) return null
    if (v instanceof Boolean) return v ? 1L : 0L
    try { return Long.parseLong(v.toString(), 10) } catch (Exception ignored) { return null }
}

/* ---------- health check ---------- */

private void handleLiveness(Map msg) {
    // Cancel pending auto-reinit — any Matter message means the device is alive
    unschedule("autoReInit")

    // If a ping is in flight, handle the response (explicit or implicit)
    if (state.pingStart != null) {
        unschedule("pingTimeout")
        Long rtt = now() - (state.pingStart as Long)
        if (msg.clusterInt == 0x0028 && msg.attrInt == 0x0000) {
            sendEvent(name: "rtt", value: rtt, unit: "ms", type: "digital", descriptionText: "Ping round-trip time: ${rtt} ms")
            logInfo "Ping RTT: ${rtt} ms"
        } else {
            logDebug "Implicit ping success (msg arrived while ping in-flight), RTT: ${rtt} ms"
        }
        state.pingStart = null
    }

    // Reset consecutive fail counter on any activity
    state.pingConsecutiveFails = 0

    // If device was offline, mark it back online
    if (device.currentValue("healthStatus") != "online") {
        sendEvent(name: "healthStatus", value: "online", descriptionText: "${device.displayName} is online", type: "digital")
        logInfo "Device is back online"
    }
}

void ping() {
    deviceHealthCheck()
}

void deviceHealthCheck() {
    if (enableHealthCheck == false) { return }
    logDebug "deviceHealthCheck() - sending DataModelRevision read"
    state.pingStart = now()
    List<Map<String,String>> paths = [matter.attributePath(0x00, 0x0028, 0x0000)]
    sendHubCommand(new HubAction(matter.readAttributes(paths), Protocol.MATTER))
    runIn(30, "pingTimeout")
    // Voltage staleness check: if no voltage report in the last 1 hour, request a fresh read
    def lastVoltage = device.currentState("voltage")
    if (lastVoltage == null || (now() - lastVoltage.date.time) > 1 * 3600 * 1000L) {
        logWarn "No voltage report in >1h — requesting voltage attribute read"
        sendHubCommand(new HubAction(matter.readAttributes([matter.attributePath(0x02, 0x0090, 0x000B)]), Protocol.MATTER))
    } else {
        logDebug "Voltage report is recent (last: ${lastVoltage.date})"
    }
    // Power staleness check: if no power report in the last 1 hour, request a fresh read
    def lastPower = device.currentState("power")
    if (lastPower == null || (now() - lastPower.date.time) > 1 * 3600 * 1000L) {
        logWarn "No power report in >1h — requesting power attribute read"
        sendHubCommand(new HubAction(matter.readAttributes([matter.attributePath(0x02, 0x0090, 0x0008)]), Protocol.MATTER))
    } else {
        logDebug "Power report is recent (last: ${lastPower.date})"
    }
    // Energy staleness check: if no energy report in the last 1 hour, request a fresh read
    def lastEnergy = device.currentState("energy")
    if (lastEnergy == null || (now() - lastEnergy.date.time) > 1 * 3600 * 1000L) {
        logWarn "No energy report in >1h — requesting energy attribute read"
        sendHubCommand(new HubAction(matter.readAttributes([matter.attributePath(0x02, 0x0091, 0x0001)]), Protocol.MATTER))
    } else {
        logDebug "Energy report is recent (last: ${lastEnergy.date})"
    }
}

void pingTimeout() {
    state.pingStart = null
    state.pingConsecutiveFails = (state.pingConsecutiveFails ?: 0) + 1
    if (state.stats == null) { state.stats = [:] }
    state.stats.pingFailCounter = (state.stats.pingFailCounter ?: 0) + 1
    sendEvent(name: "rtt", value: -1, unit: "ms", type: "digital", descriptionText: "Ping timeout (consecutiveFails=${state.pingConsecutiveFails})")
    logWarn "Ping timeout! consecutiveFails=${state.pingConsecutiveFails} (total pingFails=${state.stats.pingFailCounter})"
    if (state.pingConsecutiveFails >= 2) {
        sendEvent(name: "healthStatus", value: "offline", descriptionText: "${device.displayName} is offline", type: "digital")
        logWarn "Device is OFFLINE after ${state.pingConsecutiveFails} consecutive ping failures"
        if (enableAutoReInit != false) {
            logWarn "Auto re-init scheduled in 30 seconds"
            runIn(30, "autoReInit")
        }
    }
}

void autoReInit() {
    if (state.stats == null) { state.stats = [:] }
    state.stats.autoReInitCounter = (state.stats.autoReInitCounter ?: 0) + 1
    logWarn "Auto re-initializing after failed health checks (autoReInitCounter=${state.stats.autoReInitCounter})"
    initialize()
}

// Logging helpers — prefix all messages with device display name
private void logDebug(String msg) {
    if (logEnable) { log.debug "${device.displayName} ${msg}" }
}

private void logInfo(String msg) {
    if (txtEnable) { log.info "${device.displayName} ${msg}" }
}

private void logWarn(String msg) {
    log.warn "${device.displayName} ${msg}"
}
