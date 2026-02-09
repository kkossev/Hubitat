/*
 * IKEA BILRESA Matter Dual Button (events-based). Supports both dual button and scroll wheel models.
 *
 * Last edited: 2026/01/04 11:41 AM
 *
 * WARNING:
 * This driver runs on pure magic, optimism, and several offerings to the Hubitat gods.
 *
 * Magic activation spell (do NOT remove):
 *   $^$%#$*(*(&&$#
 */

import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(name: "IKEA BILRESA Matter Buttons", namespace: "community", author: "kkossev + ChatGPT") {

        capability "Initialize"
        capability "Refresh"
        capability "Battery"

        capability "PushableButton"
        capability "HoldableButton"
        capability "DoubleTapableButton"
        capability "ReleasableButton"

        attribute "supportedButtonValues", "enum", ["pushed", "held", "doubleTapped", "released"]
        attribute "numberOfButtons", "number"

        fingerprint endpointId:"01", inClusters:"0003,001D,003B", outClusters:"", model:"BILRESA dual button", manufacturer:"IKEA of Sweden", controllerType:"MAT"
        fingerprint endpointId:"01", inClusters:"0003,001D,003B", outClusters:"", model:"BILRESA scroll wheel", manufacturer:"IKEA of Sweden", controllerType:"MAT"
    }

    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging",          defaultValue: false
    }
}

void parse(String description) {
    Map msg = matter.parseDescriptionAsMap(description)
    Boolean isEvent = msg.evtId != null || msg.evtInt != null
    if (isEvent) {
        logDebug "parse(String) received <b>event</b> description: ${description} -> passing it to parse(Map)"
        parse(msg)
    }
    else {
        // for everything else, we are using only the new parse(Map) method! :) 
        logDebug "parse(String) received and <i>ignored</i> description: ${description} -> msg: ${msg}"
    }
    return
}

// New parse(Map) method to handle events (and attribute reports) when Device Data.newParse	is set to true
// example : [callbackType:Report, endpointInt:2, clusterInt:59, attrInt:1, data:[1:UINT:0], value:0] 
// example : [endpoint:01, cluster:003B, evtId:0006, clusterInt:59, evtInt:6, values:[0:[type:04, isContextSpecific:true, value:01], 1:[type:04, isContextSpecific:true, value:01]]]

void parse(Map msg) {
    logDebug "<b>newParse(Map)</b> received  Map: ${msg}"

    boolean isEvent = msg.evtId != null || msg.evtInt != null

    // 1) Battery reports (EP0)  Example : [callbackType:Report, endpointInt:0, clusterInt:47, attrInt:12, data:[12:UINT:200], value:200]
    if (msg.endpointInt == 0x00 && msg.clusterInt == 0x002F && msg.attrInt == 0x000C) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            Integer pct = Math.round(raw / 2.0f)
            pct = Math.max(0, Math.min(100, pct))
            sendEvent(name: "battery", value: pct, unit: "%", type: "physical")
            logInfo "Battery is ${pct}%"
        }
        return
    }

    // 3) Switch events
    // Example :  [endpoint:02, cluster:003B, evtId:0004, clusterInt:59, evtInt:4, values:[0:[type:04, isContextSpecific:true, value:01]]]
    if (isEvent && msg.clusterInt == 0x003B) {
        handleSwitchEvent(msg)
            return
    }

    // 3) Switch attribute reports - ignore them explicitely! 
    // Example :  [callbackType:Report, endpointInt:1, clusterInt:59, attrInt:1, data:[1:UINT:1], value:1] 
    if (msg.clusterInt == 0x003B && !isEvent) {
        logDebug "newParse(Map): <i>ignoring</i> switch attribute report ep=${msg.endpointInt} cluster=${msg.clusterInt} attr=${msg.attrInt} value=${msg.value}"
        return
    }

    // ignore everything else
    logDebug "newParse(Map): unhandled msg: ${msg}"
}


private void handleSwitchReport(Integer ep, Integer clus, Integer attrId, def valueObj) {
    logDebug "handleSwitchReport: ep=${ep} cluster=${clus} attr=${attrId} value=${valueObj}"
    // Button press state: cluster 0x003B attr 0x0001
    if (clus == 0x003B && attrId == 0x0001) {
        Integer v = safeInt(valueObj)
        if (v != null) {
            if (v == 0) {       // released
                sendButtonEventFiltered("released", ep)
            }
            else if (v == 1) {  // pressed
                sendButtonEventFiltered("pushed", ep)
            }
            else {
                logDebug "handleSwitchReport: Unhandled button state value: ${v} for ep=${ep}"
            }
        }
        return
    }
    logDebug "handleSwitchReport: unhandled switch report ep=${ep} cluster=${clus} attr=${attrId} value=${valueObj}"
}


// Handle switch events from cluster 0x003B
// Example :  Map: [endpoint:01, cluster:003B, evtId:0001, clusterInt:59, evtInt:1, values:[0:[type:04, isContextSpecific:true, value:01]]]
private void handleSwitchEvent(Map msg) {
    // Ignore noisy events that arrive shortly after we (re)subscribed.
    def lastInit = state.lastInitializeTime
    if (lastInit != null) {
        long age = now() - (lastInit as long)
        long uptime = location.hub.uptime ?: 0L // in seconds
        long thresholdTime = uptime < 60 ? 30000 : 10000
        if (age >= 0 && age < thresholdTime) {
            logDebug "Ignored switch event (ep=${msg.endpoint} evt=${msg.evtInt}) ${age}ms after initialize/subscribe.  hub uptime=${uptime}"
            return
        }
    }
    Integer buttonNumber = safeInt(msg.endpoint)
    Integer count        = extractMultiPressCount(msg) ?: 1
    logDebug "handleSwitchEvent: buttonNumber=${buttonNumber} count=${count}}"
    switch (msg.evtInt) {
        case 1:     // evt 1 – InitialPress; usually followed by LongPress or ShortRelease/MultiPress*
            state.lastButtonNumber = buttonNumber
            state.lastAction = "initialPress"
            state.buttonInitialPressTime = now()
            if (logEnable) { log.debug "EVT_INITIAL_PRESS buttonNumber=${buttonNumber} buttonInitialPressTime=${state.buttonInitialPressTime}" }
            if (isWheelModel() && isWheelEndpoint(buttonNumber)) {
                logDebug "Initial press for wheel ep=${buttonNumber} (sending 'pushed' event)"
                sendButtonEventFiltered("pushed", buttonNumber)
            }
            break

        case 2:     // evt 2 – LongPress
            logDebug "EVT_LONG_PRESS buttonNumber=${buttonNumber}"            
            sendButtonEventFiltered("held", buttonNumber)
            break

        case 3: // 3 – ShortRelease
            logDebug "EVT_SHORT_RELEASE buttonNumber=${buttonNumber}"
            if (isWheelModel() && isWheelEndpoint(buttonNumber)) {
                logDebug "Short-release for wheel ep=${buttonNumber} (logged, continuing)"
            }
            sendButtonEventFiltered("released", buttonNumber)
            break

        case 4:     // 4 – LongRelease
            logDebug "EVT_LONG_RELEASE buttonNumber=${buttonNumber}"
            sendButtonEventFiltered("released", buttonNumber)
            break

        case 5:     // evt 5 – MultiPressOngoing; we’ll wait for MultiPressComplete
            logDebug "EVT_MULTI_ONGOING buttonNumber=${buttonNumber}"
            if (isWheelModel() && isWheelEndpoint(buttonNumber)) {
                logDebug "Multi ongoing for wheel ep=${buttonNumber} (logged, continuing)"
            }
            break

        case 6:     // evt 6 – MultiPressComplete; this includes press count
            logDebug "EVT_MULTI_COMPLETE buttonNumber=${buttonNumber} count=${count}"
            if (isWheelModel() && isWheelEndpoint(buttonNumber)) {
                logDebug "Multi complete for wheel ep=${buttonNumber} count=${count}"
                if (count > 3) {
                    sendButtonEventFiltered("pushed", buttonNumber)
                }
                return
            }

            if (count == 1) {
                sendButtonEventFiltered("pushed", buttonNumber)
            }
            else if (count == 2) {
                sendButtonEventFiltered("doubleTapped", buttonNumber)
            }
            else {
                // triple+ → treat as pushed (or add multiTapped custom attr?)
                sendButtonEventFiltered("pushed", buttonNumber)
            }
            break

        default:
            logDebug "Unhandled switch event evt=${msg.evtInt} ep=${msg.endpoint} count=${count} msg=${msg}"
            break
    }
}

/**
 * For MultiPress* events:
 *   values[0].value -> newPosition (01 == pressed)
 *   values[1].value -> totalNumberOfPresses (01, 02, ...)
 */
private Integer extractMultiPressCount(Map msg) {
    def values = msg.values
    if (values == null) return null

    List<Integer> counts = []

    values.each { k, v ->
        if (v instanceof Map && v.value != null) {
            Integer n = safeHexToInt(v.value) ?: safeInt(v.value)
            if (n != null) counts << n
        }
    }
    if (!counts) return null

    // prefer the highest parsed count (totalNumberOfPresses)
    return counts.max()
}

void installed() { initialize() }

void updated() {
    logInfo "updated..."
    if (logEnable) runIn(7200, "logsOff")
    initialize()
}

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.warn "${device.displayName} Debug logging disabled"
}

void initialize() {
    logInfo "initialize..."
    if (getDataValue("newParse") != "true") { device.updateDataValue("newParse", "true") }
    logInfo "model=${device.getDataValue('model') ?: device.model} endpoints=${endpointCount()} newParse=${getDataValue("newParse")} uptime=${location.hub.uptime}"
    configureButtons()
    subscribeToPaths()
    refresh()
}

private void configureButtons() {
    Integer count = endpointCount()
    sendEvent(name: "numberOfButtons", value: count, isStateChange: true)
    def vals = ["pushed", "held", "doubleTapped", "released"]
    sendEvent(name: "supportedButtonValues", value: vals.toString(), isStateChange: true)
}

// Return number of endpoints/buttons for this device model (2 or 9)
private Integer endpointCount() {
    String model = (device.getDataValue("model") ?: device.model ?: "").toString().toLowerCase().trim()
    if (model.contains("scroll")) return 9
    return 2
}

// Wheel helpers: preserved so callers can detect wheel models/endpoints.
private boolean isWheelModel() {
    String model = (device.getDataValue("model") ?: device.model ?: "").toString().toLowerCase()
    return model.contains("scroll")
}

private boolean isWheelEndpoint(Integer ep) {
    if (ep == null) return false
    return [1,2,4,5,7,8].contains(ep)
}

/* ---------- subscriptions & refresh ---------- */

void refresh() {
    logDebug "refresh()"

    List<Map<String,String>> paths = []

    // Battery percent (raw 0..200)
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C))

    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private void subscribeToPaths() {
    List<Map<String,String>> paths = []

    // Battery attribute
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C))

    // Subscribe per-endpoint for switch attributes & events (EP1..EPN)
    Integer epCount = endpointCount()
    
    // 0x003B attr 0x0001 = PresentValue(CurrentState)
    // Subscribing to this attribute seems to 'unlock' or keep events flowing.
    // Probably, other Matter switches also require any attribute subscription to activate event streams?
    for (int ep = 1; ep <= epCount; ep++) {
        paths.add(matter.attributePath(ep, 0x003B, 1))      // Switch cluster attribute 0x0001 (current position) seems to be enough
    }
    
    // matter events are always enabled    
    for (int ep = 1; ep <= epCount; ep++) {
        paths.add(matter.eventPath(ep, 0x003B, -1))         // We need to subscribe for ALL events from the switch cluster 
    }

    String cmd = matter.cleanSubscribe(1, 0xFFFF, paths)
    logDebug "subscribeToPaths cmd=${cmd}"
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
    // Record the time we sent the subscription so we can ignore noisy events that immediately follow subscription/initialize.
    state.lastInitializeTime = now()
    logInfo "subscribed to switch events (EP1..EP${epCount}) + battery (EP0/0x002F/0x000C)"
}

private void sendButtonEventFiltered(String type, Integer buttonNumber) {
    // Filter 'released' events: only allow if previous action for the same
    // button was 'held'. Otherwise ignore the release (single press).
    if (type == "released") {
        def lastNum = state.lastButtonNumber
        def lastAct = state.lastAction
        if (lastNum != buttonNumber || lastAct != "held") {
            logDebug "Ignored release for button ${buttonNumber} (previous=${lastAct} button=${lastNum})"
            return
        }
    }

    if (txtEnable) log.info "${device.displayName} button ${buttonNumber} ${type}"
    sendEvent(name: type, value: buttonNumber, isStateChange: true, type: "physical")

    // Persist last button event parameters for future filtering
    state.lastButtonNumber = buttonNumber
    state.lastAction = type
    state.lastButtonTime = now()
}

/* ---------- dashboard commands ---------- */

void push(buttonNumber) {
    Integer btn = safeInt(buttonNumber)
    if (btn == null) return
    String descriptionText = "${device.displayName} button ${btn} was pushed"
    if (txtEnable) log.info descriptionText
    sendEvent(name: "pushed", value: btn, descriptionText: descriptionText, isStateChange: true, type: "digital")
}

void hold(buttonNumber) {
    Integer btn = safeInt(buttonNumber)
    if (btn == null) return
    String descriptionText = "${device.displayName} button ${btn} was held"
    if (txtEnable) log.info descriptionText
    sendEvent(name: "held", value: btn, descriptionText: descriptionText, isStateChange: true, type: "digital")
}

void doubleTap(buttonNumber) {
    Integer btn = safeInt(buttonNumber)
    if (btn == null) return
    String descriptionText = "${device.displayName} button ${btn} was doubleTapped"
    if (txtEnable) log.info descriptionText
    sendEvent(name: "doubleTapped", value: btn, descriptionText: descriptionText, isStateChange: true, type: "digital")
}

void release(buttonNumber) {
    Integer btn = safeInt(buttonNumber)
    if (btn == null) return
    String descriptionText = "${device.displayName} button ${btn} was released"
    if (txtEnable) log.info descriptionText
    sendEvent(name: "released", value: btn, descriptionText: descriptionText, isStateChange: true, type: "digital")
}

/* ---------- helpers ---------- */

private Integer safeInt(def v) {
    try {
        if (v == null) return null
        return Integer.parseInt(v.toString(), 10)
    } catch (Exception ignored) {
        return null
    }
}

private Integer safeHexToInt(Object hex) {
    if (hex == null) return null
    String s = hex.toString().trim()
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2)
    if (!s) return null
    try { return Integer.parseUnsignedInt(s, 16) } catch (Exception ignored) { return null }
}

private void logDebug(String msg) {
    if (logEnable) log.debug "${device.displayName} ${msg}"
}

private void logInfo(String msg) {
    if (txtEnable) { log.info "${device.displayName} ${msg}" }
}
