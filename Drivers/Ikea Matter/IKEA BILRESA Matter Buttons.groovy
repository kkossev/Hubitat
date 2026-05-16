/*
 * IKEA BILRESA Matter Dual Button (events-based). Supports both dual button and scroll wheel models. 
 *
 * Last edited: 2026/05/16 10:30 PM
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
    definition(name: "IKEA BILRESA Matter Buttons w/ healthStatus", namespace: "community", author: "kkossev + ChatGPT + Claude", singleThreaded: true, importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/Drivers/Ikea%20Matter/IKEA%20BILRESA%20Matter%20Buttons.groovy") {

        capability "Initialize"
        capability "Refresh"
        capability "Battery"
        capability "HealthCheck"
        capability "PushableButton"
        capability "HoldableButton"
        capability "DoubleTapableButton"
        capability "ReleasableButton"

        attribute "supportedButtonValues", "enum", ["pushed", "held", "doubleTapped", "tripleTapped", "released"]
        attribute "tripleTapped", "number"
        attribute "numberOfButtons", "number"
        attribute "healthStatus", "enum", ["online", "offline"]
        attribute "rtt", "number"

        command   "tripleTap", [[name: "buttonNumber", type: "NUMBER"]]

        fingerprint endpointId:"01", inClusters:"0003,001D,003B", outClusters:"", model:"BILRESA dual button", manufacturer:"IKEA of Sweden", controllerType:"MAT"
        fingerprint endpointId:"01", inClusters:"0003,001D,003B", outClusters:"", model:"BILRESA scroll wheel", manufacturer:"IKEA of Sweden", controllerType:"MAT"
    }

    preferences {
        input name: "txtEnable",         type: "bool", title: "Enable descriptionText logging",                                    defaultValue: true
        input name: "logEnable",         type: "bool", title: "Enable debug logging",                                        defaultValue: false
        input name: "enableHealthCheck", type: "bool", title: "Enable health check (ping every 15 min)",                   defaultValue: true
        input name: "enableAutoReInit",  type: "bool", title: "Auto re-initialize after 2 consecutive ping failures",      defaultValue: true
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
    handleLiveness(msg)

    // Ping response (explicit) or implicit ping success (any msg while ping in-flight)
    if (state.pingStart != null) {
        unschedule("pingTimeout")
        Long rtt = now() - (state.pingStart as Long)
        if (msg.clusterInt == 0x0028 && msg.attrInt == 0x0000) {
            sendEvent(name: "rtt", value: rtt, unit: "ms", type: "digital", descriptionText: "Ping round-trip time: ${rtt} ms")
            logInfo "Ping RTT: ${rtt} ms"
            state.pingStart = null
            return   // ping response fully handled
        }
        logDebug "Implicit ping success (msg arrived while ping in-flight), RTT: ${rtt} ms"
        state.pingStart = null
    }

    boolean isEvent = msg.evtId != null

    // Battery report (EP0)  Example: [callbackType:Report, endpointInt:0, clusterInt:47, attrInt:12, value:200]
    if (msg.endpointInt == 0x00 && msg.clusterInt == 0x002F && msg.attrInt == 0x000C) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            Integer pct = Math.round(raw / 2.0f)
            pct = Math.max(0, Math.min(100, pct))
            sendEvent(name: "battery", value: pct, unit: "%", descriptionText: "Battery is ${pct}%", type: "physical", isStateChange: true)
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

    // Software version string (Basic Information cluster 0x0028, attr 0x000A)
    if (msg.clusterInt == 0x0028 && msg.attrInt == 0x000A) {
        String ver = msg.value?.toString() ?: ""
        device.updateDataValue("softwareVersion", ver)
        logInfo "softwareVersion=${ver}"
        return
    }

    // ignore everything else
    logDebug "newParse(Map): unhandled msg: ${msg}"
}



// Handle switch events from cluster 0x003B (newParse:true format)
// Example: [callbackType:Event, endpointInt:1, clusterInt:59, evtId:1, value:[0:1]]
private void handleSwitchEvent(Map msg) {
    // Ignore noisy buffered events that arrive in the burst right after subscribing.
    // state.initPending is cleared when SubscriptionResult arrives (or after 120s fallback).
    if (state.initPending) {
        logDebug "Ignored switch event (ep=${msg.endpointInt} evtId=${msg.evtId}) - subscription still pending"
        return
    }

    // Deduplicate by eventSerial: the same buffered events can be re-delivered across
    // multiple subscription bursts (e.g. after autoReInit). Reject clearly stale serials.
    // A tolerance window of 20 accommodates out-of-order delivery within a single press
    // sequence (the hub delivers even/odd serials in separate streams, a few apart).
    // Genuinely stale re-delivered events are typically 100s of serials behind.
    Long serial = msg.eventSerial as Long
    if (serial != null) {
        Long lastSerial = (state.lastEventSerial ?: 0L) as Long
        if (serial < lastSerial - 20L) {
            logDebug "Stale event rejected (ep=${msg.endpointInt} evtId=${msg.evtId} eventSerial=${serial} lastSerial=${lastSerial})"
            return
        }
        if (serial < lastSerial) {
            logWarn "Out-of-order event (possibly stale from prev gesture): ep=${msg.endpointInt} evtId=${msg.evtId} serial=${serial} lastSerial=${lastSerial} delta=${serial - lastSerial}"
        }
        if (serial > lastSerial) { state.lastEventSerial = serial }
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
                state.wheelPressCount = (state.wheelPressCount ?: 0) + 1
                logDebug "Initial press for wheel ep=${buttonNumber} (sending 'pushed' event)"
                sendButtonEventFiltered("pushed", buttonNumber)
            } else if (!isWheelModel()) {
                // Dual button: cancel pending timers from the previous gesture, then start the
                // hold-simulation timer. If evtId=3 (ShortRelease) arrives within 750ms it cancels
                // this timer (short press). If 750ms elapses without evtId=3, holdSimulate() fires
                // "held" immediately (the device batches evtId=2+4 and delivers them only after release).
                unschedule("singlePressComplete")
                unschedule("holdSimulate")
                state.holdSimulateButtonNumber = buttonNumber
                runInMillis(750, "holdSimulate")
            }
            break

        case 2:     // evt 2 – LongPress
            logDebug "EVT_LONG_PRESS buttonNumber=${buttonNumber}"
            unschedule("singlePressComplete")   // Dual button: cancel pending single-tap timer
            unschedule("holdSimulate")           // Dual button: cancel hold-simulation timer (defensive)
            if (!isWheelModel() && (state.holdSimulatedForButton as Integer) == buttonNumber) {
                // Dual button: holdSimulate() already fired "held" — suppress the duplicate
                // (evtId=2 arrives batched, seconds after the hold ends)
                logDebug "EVT_LONG_PRESS: held already simulated for button ${buttonNumber}, skipping"
                state.holdSimulatedForButton = null
            } else {
                sendButtonEventFiltered("held", buttonNumber)
            }
            break

        case 3: // 3 – ShortRelease
            logDebug "EVT_SHORT_RELEASE buttonNumber=${buttonNumber}"
            if (isWheelModel() && isWheelEndpoint(buttonNumber)) {
                logDebug "Short-release for wheel ep=${buttonNumber} (logged, continuing)"
            } else if (!isWheelModel()) {
                // Dual button: ShortRelease fires only for short presses (holds fire evtId=4, never evtId=3).
                // Cancel hold-simulation timer — this is a short press, not a hold.
                // Schedule single-tap 'pushed'; cancelled by the next evtId=1 (multi-tap) or evtId=5.
                // 400 ms gives the second press of a double-tap time to arrive and cancel the timer.
                unschedule("holdSimulate")
                runInMillis(400, "singlePressComplete")
            }
            sendButtonEventFiltered("released", buttonNumber)
            break

        case 4:     // 4 – LongRelease
            logDebug "EVT_LONG_RELEASE buttonNumber=${buttonNumber}"
            sendButtonEventFiltered("released", buttonNumber)
            break

        case 5:     // evt 5 – MultiPressOngoing; value:[0:previousPosition, 1:totalNumberOfPressesSoFar]
            // The dual button never sends evtId=6 (MultiPressComplete), so we use evtId=5 as the
            // trigger: store the running count and debounce-fire after 300 ms of silence.
            // The scroll wheel does send evtId=6, so skip the debounce for that model entirely.
            Integer ongoingCount = safeInt(msg.value[1])
            logDebug "EVT_MULTI_ONGOING buttonNumber=${buttonNumber} count=${ongoingCount}"
            if (ongoingCount != null && !isWheelModel()) {
                unschedule("singlePressComplete")   // Dual button: cancel single-tap timer, it's a multi-press
                unschedule("holdSimulate")           // Dual button: cancel hold-simulation timer
                state.multiPressCount = ongoingCount
                state.multiPressButtonNumber = buttonNumber
                runInMillis(150, "multiPressComplete")
            }
            break

        case 6:     // evt 6 – MultiPressComplete; value:[0:previousPosition, 1:totalNumberOfPresses]
            if (!isWheelModel()) {
                // Dual button: evtId=6 always arrives late (stale from prev gesture; serial is adjacent
                // to the new gesture so dedup cannot filter it). Completely ignore it.
                // singlePressComplete() and multiPressComplete() handle all dual-button events.
                logDebug "EVT_MULTI_COMPLETE: ignored for dual-button model (always arrives late/stale)"
                break
            }
            // Wheel model only from here:
            unschedule("multiPressComplete")
            Integer count =  safeInt(msg.value[1])
            logDebug "EVT_MULTI_COMPLETE buttonNumber=${buttonNumber} count=${count}"
            if (count == null) {
                logDebug "Invalid MultiPressComplete event value: ${msg.value}"
                break
            }
            if (isWheelModel() && isWheelEndpoint(buttonNumber)) {
                Integer firedCount = (state.wheelPressCount ?: 0) as Integer
                Integer missed = Math.max(0, count - firedCount) as Integer
                logDebug "Multi complete for wheel ep=${buttonNumber} count=${count} firedCount=${firedCount} missed=${missed}"
                if (missed > 0) {
                    missed.times { sendButtonEventFiltered("pushed", buttonNumber) }
                }
                state.wheelPressCount = 0
                return
            }

            if (count == 1) {
                sendButtonEventFiltered("pushed", buttonNumber)
            }
            else if (count == 2) {
                sendButtonEventFiltered("doubleTapped", buttonNumber)
            }
            else if (count == 3) {
                sendButtonEventFiltered("tripleTapped", buttonNumber)
            }
            else {
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
        logInfo "accepting button events now..."
    }
}

// Fired 800 ms after evtId=1 when no evtId=5 (multi-press) or evtId=2 (hold) cancelled it.
// Handles single-tap 'pushed' for the dual button model.
// (evtId=6 is completely ignored for dual button — it always arrives ~17 s late.)
void singlePressComplete() {
    Integer button = state.lastButtonNumber as Integer
    logDebug "singlePressComplete() buttonNumber=${button}"
    if (button == null) { return }
    sendButtonEventFiltered("pushed", button)
}

// Fired 750ms after evtId=1 (InitialPress) when evtId=3 (ShortRelease) has not cancelled it first.
// Simulates 'held' immediately — the device batches evtId=2+4 and only delivers them after release.
// Sets holdSimulatedForButton so case 2 (LongPress) suppresses the duplicate when it arrives late.
void holdSimulate() {
    Integer button = state.holdSimulateButtonNumber as Integer
    logDebug "holdSimulate() buttonNumber=${button} - firing simulated held"
    if (button == null) { return }
    state.holdSimulatedForButton = button
    sendButtonEventFiltered("held", button)
}

// Fired 150 ms after evtId=5 (MultiPressOngoing) for the dual button model.
// evtId=6 is completely ignored for dual button — it always arrives ~17 s late.
// count=2 → doubleTapped; any other count is ignored (dual button does not support triple-tap).
void multiPressComplete() {
    unschedule("singlePressComplete")   // Safety: hub can deliver evtId=3 after evtId=5 (out-of-order serials)
    unschedule("holdSimulate")           // Safety: cancel hold-simulation if still pending
    Integer count  = state.multiPressCount as Integer
    Integer button = state.multiPressButtonNumber as Integer
    logDebug "multiPressComplete() buttonNumber=${button} count=${count}"
    if (count == null || button == null) { return }
    if (count == 2) {
        sendButtonEventFiltered("doubleTapped", button)
    } else {
        logDebug "multiPressComplete: count=${count} unhandled, ignoring (button=${button})"
    }
}

/* ---------- health check ---------- */

private void handleLiveness(Map msg) {
    // Cancel pending auto-reinit — any Matter message means the device is alive
    unschedule("autoReInit")

    // Reset consecutive fail counter on any activity
    state.pingConsecutiveFails = 0

    // If device is not yet online (null on first boot) or was offline, mark it online
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
    if (state.initPending) {
        logDebug "deviceHealthCheck() skipped — subscription still pending"
        return
    }
    logDebug "deviceHealthCheck() - sending DataModelRevision read"
    state.pingStart = now()
    List<Map<String,String>> paths = [matter.attributePath(0x00, 0x0028, 0x0000)]
    sendHubCommand(new HubAction(matter.readAttributes(paths), Protocol.MATTER))
    runIn(30, "pingTimeout")
    // Battery staleness check: if no battery report in the last 12 hours, request a fresh read
    def lastBat = device.currentState("battery")
    if (lastBat == null || (now() - lastBat.date.time) > 12 * 3600 * 1000L) {
        logWarn "No battery report in >12h — requesting battery attribute read"
        sendHubCommand(new HubAction(matter.readAttributes([matter.attributePath(0x00, 0x002F, 0x000C)]), Protocol.MATTER))
    } else {
        logDebug "Battery report is recent (last: ${lastBat.date})"
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
    log.warn "${device.displayName} Debug logging disabled"
}

void initialize() {
    if (state.stats == null) { state.stats = [initializeCounter: 0, pingFailCounter: 0, autoReInitCounter: 0] }
    state.stats.initializeCounter = (state.stats.initializeCounter ?: 0) + 1
    unschedule("deviceHealthCheck")
    unschedule("pingTimeout")
    unschedule("autoReInit")
    state.pingStart = null
    state.pingConsecutiveFails = 0
    logInfo "initialize... (initializeCounter=${state.stats.initializeCounter})"
    if (getDataValue("newParse") != "true") { device.updateDataValue("newParse", "true") }
    logInfo "model=${device.getDataValue('model') ?: device.model} endpoints=${endpointCount()} newParse=${getDataValue("newParse")} uptime=${location.hub.uptime}"
    configureButtons()
    subscribeToPaths()
    refresh()
    if (enableHealthCheck != false) { runEvery5Minutes("deviceHealthCheck") }
}

private void configureButtons() {
    Integer count = endpointCount()
    sendEvent(name: "numberOfButtons", value: count, isStateChange: true)
    // Wheel model supports triple-tap (evtId=6 count=3); dual button does not.
    def vals = isWheelModel()
        ? ["pushed", "held", "doubleTapped", "tripleTapped", "released"]
        : ["pushed", "held", "doubleTapped", "released"]
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

    // Software version string (Basic Information cluster)
    paths.add(matter.attributePath(0x00, 0x0028, 0x000A))

    // (0x00, 0x0033, 0x0002) = UpTime attribute in General Diagnostics cluster, useful for testing liveness and event flow during health check pings. Optional in Matter, but if supported by the device, it will be included in the refresh read and cause ping RTTs to be logged in the rtt attribute. If not supported, no harm done, just no RTT updates.
    paths.add(matter.attributePath(0x00, 0x0033, 0x0002))

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
        paths.add(matter.attributePath(ep, 0x003B, -1))      // Switch cluster attribute 0x0001 (current position) seems to be enough
    }
    
    // matter events are always enabled    
    for (int ep = 1; ep <= epCount; ep++) {
        paths.add(matter.eventPath(ep, 0x003B, -1))         // We need to subscribe for ALL events from the switch cluster 
    }

    // General Diagnostics cluster: UpTime attribute
    // EP0 / Cluster 0x0033 / Attr 0x0002 = UpTime
    // This is optional in Matter, but useful to test whether the device reports periodic changes.
    paths.add(matter.attributePath(0x00, 0x0033, 0x0002))
    paths.add(matter.attributePath(0x00, 0x0035, 0x0005))  // RSSI, if implemented

    String cmd = matter.cleanSubscribe(0, 600, paths)        // 05/2026 update: using 0-600s reporting interval
    logDebug "subscribeToPaths cmd=${cmd}"
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))

    // Block events until SubscriptionResult confirms the burst is done; 120s fallback covers slow reconnects after hub reboot.
    state.initPending = true
    runIn(120, "clearInitPending")
    logInfo "subscribing to switch events (EP1..EP${epCount}) + battery (EP0/0x002F/0x000C)"
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

void tripleTap(buttonNumber) {
    Integer btn = safeInt(buttonNumber)
    if (btn == null) return
    String descriptionText = "${device.displayName} button ${btn} was tripleTapped"
    if (txtEnable) log.info descriptionText
    sendEvent(name: "tripleTapped", value: btn, descriptionText: descriptionText, isStateChange: true, type: "digital")
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

private void logDebug(String msg) {
    if (logEnable) log.debug "${device.displayName} ${msg}"
}

private void logInfo(String msg) {
    if (txtEnable) { log.info "${device.displayName} ${msg}" }
}

private void logWarn(String msg) {
    log.warn "${device.displayName} ${msg}"
}
