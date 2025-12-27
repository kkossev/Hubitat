/*
 * IKEA MYGGBETT Matter Door/Window Sensor (minimal)
 *
 * Last edited: 2025/12/2724 10:18 PM
 */

import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(name: "IKEA MYGGBETT Matter Door/Window Sensor", namespace: "community", author: "kkossev + ChatGPT") {

        capability "Sensor"
        capability "ContactSensor"
        capability "Battery"
        capability "Refresh"
        capability "Initialize"

        fingerprint endpointId: "01",
                inClusters: "0003,001D,0045",
                outClusters: "",
                model: "MYGGBETT door/window sensor",
                manufacturer: "IKEA of Sweden",
                controllerType: "MAT"
    }

    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

void installed() { initialize() }

void updated() {
    if (logEnable) runIn(7200, "logsOff")
    initialize()
}

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    logWarn("Debug logging disabled")
}

void initialize() {
    state.lastInitializeTime = now()
    logInfo("initializing...")
    subscribeToAttributes()
    //refresh()
}

void refresh() {
    logDebug("refreshing...")
    state.lastRefreshTime = now()

    List<Map<String,String>> paths = []

    // Contact state (cluster 0x0045 attr 0x0000 => boolean)
    paths.add(matter.attributePath(0x01, 0x0045, 0x0000))

    // Battery (same as KLIPPBOK: endpoint 0, Power Source cluster 0x002F, attr 0x000C)
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C))

    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private void subscribeToAttributes() {
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0045, 0x0000))
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C))

    //String cmd = matter.cleanSubscribe(1, 0xFFFF, paths)
    String cmd = matter.cleanSubscribe(1, 180 /*14400*/, paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))

    logInfo("subscribing...")
}

def parse(String description) {
    Map msg = matter.parseDescriptionAsMap(description)
    logDebug("parse: ${description} msg: ${msg}")
    if (!msg) return

    Integer ep     = safeHexToInt(msg.endpoint)
    Integer clus   = safeHexToInt(msg.cluster)
    Integer attrId = safeHexToInt(msg.attrId)
    String value   = msg.value?.toString()

    if (ep == null || clus == null || attrId == null) return

    // Contact state: cluster 0x0045 attr 0x0000 (expected boolean 0/1)
    if (ep == 0x01 && clus == 0x0045 && attrId == 0x0000) {
        Integer v = safeHexToInt(value)
        if (v != null) {
            String contact = (v == 0) ? "open" : "closed"
            boolean isInit = state.lastInitializeTime ? (now() - state.lastInitializeTime <= 15000) : false
            boolean isRef = state.lastRefreshTime ? (now() - state.lastRefreshTime <= 15000) : false
            def sfx = (isInit ? " [initialize]" : "") + (isRef ? " [refresh]" : "")
            String prev = device.currentValue('contact')
            String txt = (prev != null && prev.toString() == contact) ? "Contact is ${contact}${sfx}" : "Contact was ${contact}${sfx}"
            // for contact attribute, isStateChange should never be forced to true to avoid apps triggering repeatedly on same contact state
            sendEvent(name: "contact", value: contact, descriptionText: txtEnable ? txt : null, type: "physical")
            def contactTime = device.currentState('contact')?.date.time
            logInfo(txt)
        }
        return
    }

    // Battery: endpoint 0, cluster 0x002F, attr 0x000C (typically 0..200)
    if (ep == 0x00 && clus == 0x002F && attrId == 0x000C) {
        Integer raw = safeHexToInt(value)
            if (raw != null) {
            Integer pct = Math.round(raw / 2.0f)
            pct = Math.max(0, Math.min(100, pct))
            boolean isInit = state.lastInitializeTime ? (now() - state.lastInitializeTime <= 15000) : false
            boolean isRef = state.lastRefreshTime ? (now() - state.lastRefreshTime <= 15000) : false
            def sfx = (isInit ? " [initialize]" : "") + (isRef ? " [refresh]" : "")
            def contactVal = device.currentValue('contact')
            def batteryVal = device.currentValue('battery')
            def batteryTime = device.currentState('battery')?.date.time
            logDebug("Battery report received; current contact state is ${contactVal} contact time = ${contactTime}")
            if (sfx == "") sfx = " (contact is ${contactVal})"
            // for battery, we can safely mark isStateChange true during init/refresh without causing issues in apps
            sendEvent(name: "battery", value: pct, unit: "%", descriptionText: txtEnable ? "Battery is ${pct}%${sfx}" : null, isStateChange: (isInit || isRef), type: "physical")
            logInfo("Battery is ${pct}%${sfx}")
        }
        return
    }

    logDebug("Unhandled Matter report: ${msg}")
}

private Integer safeHexToInt(Object hex) {
    if (hex == null) return null
    String s = hex.toString().trim()
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2)
    if (s == "") return null
    try { return Integer.parseUnsignedInt(s, 16) } catch (Exception ignored) { return null }
}

// Logging helpers — prefix all messages with device display name
private void logDebug(String msg) { 
    if (logEnable) { log.debug "${device.displayName} ${msg}" }
}

private void logInfo(String msg) {
    if (txtEnable) { log.info "${device.displayName} ${msg}" }
}

private void logInfoAlways(String msg) {
    log.info "${device.displayName} ${msg}"
}

private void logWarn(String msg) {
    log.warn "${device.displayName} ${msg}"
}
