/*
 * IKEA KLIPPBOK Matter Water Leak Sensor
 *
 * Last edited: 2026/05/14 5:35 PM
 */

import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(name: "IKEA KLIPPBOK Matter Water Leak Sensor w/ healthStatus", namespace: "community", author: "kkossev + ChatGPT + Claude", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Ikea%20Matter/IKEA%20KLIPPBOK%20Matter%20Water%20Leak%20Sensor.groovy") {

        capability "Sensor"
        capability "WaterSensor"
        capability "Battery"
        capability "Refresh"
        capability "Initialize"
        capability "HealthCheck"

        attribute "healthStatus", "enum", ["online", "offline"]
        attribute "rtt", "number"

        fingerprint endpointId: "01",
                inClusters: "0003,001D,0045,0080",
                outClusters: "",
                model: "KLIPPBOK water leak sensor",
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
    // Water/leak state (cluster 0x0045 attr 0x0000 => boolean)
    paths.add(matter.attributePath(0x01, 0x0045, 0x0000))
    // Battery (endpoint 0, Power Source cluster 0x002F, attr 0x000C)
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C))
    // Software version string (Basic Information cluster 0x0028, attr 0x000A)
    paths.add(matter.attributePath(0x00, 0x0028, 0x000A))

    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private void subscribeToAttributes() {
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0045, 0x0000))
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C))

    String cmd = matter.cleanSubscribe(1, 600, paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))

    logInfo "subscribing to water state (EP1/0x0045) + battery (EP0/0x002F/0x000C)"
}

void parse(String description) {
    logDebug "parse(String) called - ignored (newParse:true mode only)"
}

// parse(Map) - newParse:true format only
// Attribute report : [callbackType:Report, endpointInt:1, clusterInt:69, attrInt:0, data:[0:BOOL:false], value:false]
// Battery report   : [callbackType:Report, endpointInt:0, clusterInt:47, attrInt:12, value:200]
void parse(Map msg) {
    logDebug "parse(Map) received: ${msg}"
    handleLiveness(msg)

    Integer ep     = msg.endpointInt
    Integer clus   = msg.clusterInt
    Integer attrId = msg.attrInt

    if (ep == null || clus == null || attrId == null) return

    // Water state: EP1 cluster 0x0045 attr 0x0000 (BooleanState; false=dry, true=wet)
    if (ep == 0x01 && clus == 0x0045 && attrId == 0x0000) {
        Integer v = safeInt(msg.value)
        if (v != null) {
            String wet = (v != 0) ? "wet" : "dry"
            sendEvent(name: "water", value: wet, descriptionText: txtEnable ? "Water sensor is ${wet}" : null)
            logInfo "Water sensor is ${wet}"
        }
        return
    }

    // Battery: EP0 cluster 0x002F attr 0x000C (raw 0..200)
    if (ep == 0x00 && clus == 0x002F && attrId == 0x000C) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            Integer pct = Math.round(raw / 2.0f)
            pct = Math.max(0, Math.min(100, pct))
            sendEvent(name: "battery", value: pct, unit: "%")
            logInfo "Battery is ${pct}%"
        }
        return
    }

    // Software version string (Basic Information cluster 0x0028, attr 0x000A)
    if (clus == 0x0028 && attrId == 0x000A) {
        String ver = msg.value?.toString() ?: ""
        device.updateDataValue("softwareVersion", ver)
        logInfo "softwareVersion=${ver}"
        return
    }

    logDebug "parse(Map): unhandled msg: ${msg}"
}

private Integer safeInt(def v) {
    if (v == null) return null
    if (v instanceof Boolean) return v ? 1 : 0
    try { return Integer.parseInt(v.toString(), 10) } catch (Exception ignored) { return null }
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
