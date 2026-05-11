/*
 * IKEA BILRESA Matter Dual Button (events-based). Supports both dual button and scroll wheel models.
 *
 * Last edited: 2026/05/11 11:42 PM
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
    logDebug "parse(String) called - ignored (newParse:true mode only)"
}

// parse(Map) - newParse:true format only
// Attribute report  : [callbackType:Report, endpointInt:0, clusterInt:47, attrInt:12, value:200]
// Switch event      : [callbackType:Event,  endpointInt:1, clusterInt:59, evtId:1,    value:[0:1]]
// MultiPressComplete: [callbackType:Event,  endpointInt:1, clusterInt:59, evtId:6,    value:[0:1, 1:2]]

void parse(Map msg) {
    logDebug "parse(Map) received: ${msg}"

    boolean isEvent = msg.evtId != null

    // Battery report (EP0)  Example: [callbackType:Report, endpointInt:0, clusterInt:47, attrInt:12, value:200]
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

    // SubscriptionResult: signals end of post-subscribe event burst — safe to accept events now
    // Example: [callbackType:SubscriptionResult, subscriptionId:3743154004]
    if (msg.callbackType == "SubscriptionResult") {
        clearInitPending()
        return
    }

    // Switch event  Example: [callbackType:Event, endpointInt:2, clusterInt:59, evtId:4, value:[0:1]]
    if (isEvent && msg.clusterInt == 0x003B) {
        handleSwitchEvent(msg)
            return
    }

    // Switch attribute reports - ignore explicitly
    if (msg.clusterInt == 0x003B && !isEvent) {
        logDebug "newParse(Map): ignoring switch attribute report ep=${msg.endpointInt} cluster=${msg.clusterInt} attr=${msg.attrInt} value=${msg.value}"
        return
    }

    // ignore everything else
    logDebug "newParse(Map): unhandled msg: ${msg}"
}



// Handle switch events from cluster 0x003B (newParse:true format)
// Example: [callbackType:Event, endpointInt:1, clusterInt:59, evtId:1, value:[0:1]]
private void handleSwitchEvent(Map msg) {
    // Ignore noisy buffered events that arrive in the burst right after subscribing.
    // state.initPending is cleared when SubscriptionResult arrives (or after 5s fallback).
    if (state.initPending) {
        logDebug "Ignored switch event (ep=${msg.endpointInt} evtId=${msg.evtId}) - subscription still pending"
        return
    }
    Integer buttonNumber = msg.endpointInt as Integer
    logDebug "handleSwitchEvent: buttonNumber=${buttonNumber} evtId=${msg.evtId}"
    switch (msg.evtId) {
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

        case 6:     // evt 6 – MultiPressComplete; value:[0:previousPosition, 1:totalNumberOfPresses]
            Integer count =  safeInt(msg.value[1])
            logDebug "EVT_MULTI_COMPLETE buttonNumber=${buttonNumber} count=${count}"
            if (count == null) {
                logDebug "Invalid MultiPressComplete event value: ${msg.value}"
                break
            }
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
            logDebug "Unhandled switch event evtId=${msg.evtId} ep=${msg.endpointInt} msg=${msg}"
            break
    }
}

void clearInitPending() {
    unschedule("clearInitPending")
    if (state.initPending) {
        state.initPending = false
        logInfo "subscription confirmed - accepting button events"
    }
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

    String cmd = matter.cleanSubscribe(1, 600, paths)
    logDebug "subscribeToPaths cmd=${cmd}"
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
    // Block events until SubscriptionResult confirms the burst is done; 120s fallback covers slow reconnects after hub reboot.
    state.initPending = true
    runIn(120, "clearInitPending")
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
