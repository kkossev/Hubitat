/*
 * IKEA MYGGSPRAY Matter Motion + Illuminance + Battery (minimal)
 *
 * Last edited: 2026/02/14 12:08 PM (uses newParse:true messages map format)
 */

import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(name: "IKEA MYGGSPRAY Matter Motion+Lux+Bat", namespace: "community", author: "kkossev + ChatGPT :)") {
        capability "Sensor"
        capability "MotionSensor"
        capability "IlluminanceMeasurement"
        capability "Refresh"
        capability "Initialize"
        capability "Battery"
    }
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

void installed() {
    if (logEnable) log.debug "installed()"
    initialize()
}

void updated() {
    if (logEnable) log.debug "updated()"
    if (logEnable) runIn(1800, "logsOff")
    initialize()
}

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.warn "Debug logging disabled"
}

void initialize() {
    if (logEnable) log.debug "initialize()"
    device.updateDataValue('newParse', "true")  // Force new parse logic for all messages
    subscribeToAttributes()
    refresh()
}

void refresh() {
    if (logEnable) log.debug "refresh()"
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0400, 0x0000)) // Illuminance MeasuredValue
    paths.add(matter.attributePath(0x02, 0x0406, 0x0000)) // Occupancy
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C)) // BatPercentRemaining
    
    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private void subscribeToAttributes() {
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0400, 0x0000)) // lux
    paths.add(matter.attributePath(0x02, 0x0406, 0x0000)) // motion
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C)) // BatteryPercentRemaining

    String cmd = matter.cleanSubscribe(1, 600, paths)	  // 10 minutes refresh
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))

    if (txtEnable) log.info "Subscribed to MYGGSPRAY motion + illuminance + battery (newParse:true)"
}

def parse(Map descMap) {
    if (logEnable) log.debug "parse: ${descMap}"
    if (!descMap) return

    // This driver uses the new unified parse logic that Hubitat provides for Matter devices.
    // endpointInt, clusterInt, and attrInt IDs are already parsed and provided in the descMap as integers, so we don't need to do any manual hex parsing here.
    Integer ep     = descMap.endpointInt
    Integer clus   = descMap.clusterInt
    Integer attrId = descMap.attrInt
    Integer value  = descMap.value as Integer

    if (ep == null || clus == null || attrId == null) return

    // Illuminance Measurement: cluster 0x0400 attr 0x0000 (MeasuredValue)
    if (ep == 0x01 && clus == 0x0400 && attrId == 0x0000) {
        if (value != null) {
            Integer lux = value / 100.0
            String descText = "Illuminance is ${lux} lx"
            sendEvent(name: "illuminance", value: lux, unit: "lx", descriptionText: txtEnable ? descText : null)
            if (txtEnable) { log.info descText }
        }
        return
    }

    // Occupancy Sensing: cluster 0x0406 attr 0x0000 (Occupancy)
    if (ep == 0x02 && clus == 0x0406 && attrId == 0x0000) {
        if (value != null) {
            String motion = ((value & 0x01) != 0) ? "active" : "inactive"
            String descText = "Motion is ${motion}" 
            sendEvent(name: "motion", value: motion, descriptionText: txtEnable ? descText : null)
            if (txtEnable) { log.info descText }
        }
        return
    }

    // Power Source (Battery): cluster 0x002F attr 0x000C
    if (ep == 0x00 && clus == 0x002F && attrId == 0x000C) {
        value = (value) / 2
        if (value != null) {
            value = Math.max(0, Math.min(100, value))
            sendEvent(name: "battery", value: value, unit: "%")
            if (txtEnable) log.info "Battery is ${value}%"
        }
        return
    }
    logDebug "Unhandled Matter report: ${descMap}"
}

def parse(String description) {
    if (logEnable) log.warn "old parse method (deprecated): ${description}"
    return
}
