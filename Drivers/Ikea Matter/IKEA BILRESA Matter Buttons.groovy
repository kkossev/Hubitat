/*
 * IKEA BILRESA Matter Dual Button (attributes and events-based)
 *
 * Last edited: 2025/12/25 10:19 PM
 *
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

        fingerprint endpointId:"01", inClusters:"0003,001D,003B", outClusters:"", model:"BILRESA dual button", manufacturer:"IKEA of Sweden", controllerType:"MAT"
    }

    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging",          defaultValue: false
    }
}

void installed() { initialize() }

void updated() {
    if (logEnable) runIn(7200, "logsOff")
    initialize()
}

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.warn "Debug logging disabled"
}

void initialize() {
    logDebug "initialize()"
    configureButtons()
    subscribeToPaths()
    refresh()
}

private void configureButtons() {
    sendEvent(name: "numberOfButtons", value: 2, isStateChange: true)
    sendEvent(name: "supportedButtonValues",
              value: ["pushed", "held", "doubleTapped", "released"].toString(),
              isStateChange: true)
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

    // Subscribe per-endpoint for switch attributes (EP1 & EP2)
    paths.add(matter.attributePath(0x01, 0x003B, -1))
    paths.add(matter.attributePath(0x02, 0x003B, -1))

    // Subscribe per-endpoint for switch events (EP1 & EP2)
    paths.add(matter.eventPath(0x01, 0x003B, -1))
    paths.add(matter.eventPath(0x02, 0x003B, -1))

    String cmd = matter.cleanSubscribe(1, 0xFFFF, paths)
    logDebug "subscribeToPaths cmd=${cmd}"
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
    // Record the time we sent the subscription so we can ignore noisy
    // events that immediately follow subscription/initialize.
    state.lastInitializeTime = now()

    if (txtEnable) log.info "${device.displayName} Subscribed to switch events (EP1/EP2) + battery (EP0/0x002F/0x000C)"
}

/* ---------- parsing ---------- */

void parse(String description) {
    Map msg = matter.parseDescriptionAsMap(description)
    logDebug "parse(String): description: ${description} -> msg: ${msg}"
    if (!msg) return

    Integer epHex   = safeHexToInt(msg.endpoint)
    Integer clusHex = safeHexToInt(msg.cluster)
    Integer attrId  = safeHexToInt(msg.attrId)
    String  value   = msg.value?.toString()
    String  cb      = (msg.callbackType ?: "").toString()

    if (cb == "" && epHex != null && clusHex != null && attrId != null &&
        epHex == 0x00 && clusHex == 0x002F && attrId == 0x000C) {

        Integer raw = safeHexToInt(value)   // typically 0x00..0xC8
        if (raw != null) {
            Integer pct = Math.round(raw / 2.0f)
            pct = Math.max(0, Math.min(100, pct))
            sendEvent(name: "battery", value: pct, unit: "%")
            if (txtEnable) log.info "${device.displayName} Battery is ${pct}%"
        }
        return
    }

    // Everything else (switch events, Report-based attrs) goes through the Map parser
    parse(msg)
}

void parse(Map msg) {
    logDebug "parse(Map): ${msg}"

    Integer cluster = safeInt(msg.clusterInt)
    if (cluster == null) cluster = safeHexToInt(msg.cluster)

    // endpoint can be endpointInt (reports) or endpoint (events)
    Integer ep = safeInt(msg.endpointInt)
    if (ep == null) ep = safeHexToInt(msg.endpoint)

    // 1) Switch events (multi-press, long-press)
    Integer evt = safeInt(msg.evtInt)
    if (cluster == 0x003B && evt != null && (ep == 0x01 || ep == 0x02)) {
        handleSwitchEvent(ep, evt, msg)
        return
    }

    // 2) Attribute reports (battery, possibly others)
    String cb = (msg.callbackType ?: "").toString()
    if (cb == "Report") {
        handleAttributeReport(msg)
        return
    }

    // ignore everything else
}

/* ---------- event handlers ---------- */

private void handleSwitchEvent(Integer ep, Integer evt, Map msg) {
    // Ignore noisy events that arrive shortly after we (re)subscribed.
    def lastInit = state.lastInitializeTime
    if (lastInit != null) {
        long age = now() - (lastInit as long)
        if (age >= 0 && age < 10000) {
            logDebug "Ignored switch event (ep=${ep} evt=${evt}) ${age}ms after initialize/subscribe"
            return
        }
    }
    Integer buttonNumber = (ep == 0x01) ? 1 : 2
    Integer count        = extractMultiPressCount(msg) ?: 1
    switch (evt) {
        case 1:
            // evt 1 – InitialPress; usually followed by LongPress or ShortRelease/MultiPress*
            if (logEnable) { log.debug "EVT_INITIAL_PRESS"}
            break

        case 2:
            // evt 2 – LongPress
            sendButtonEvent("held", buttonNumber)
            break

        case 3:
            // 3 – ShortRelease
            // If you want a release after any short press, enable this:
            sendButtonEvent("released", buttonNumber)
            break

        case 4:
            // 4 – LongRelease
            sendButtonEvent("released", buttonNumber)
            break

        case 5:
            // evt 5 – MultiPressOngoing; we’ll wait for MultiPressComplete
            if (logEnable) { log.debug "EVT_MULTI_ONGOING"}
            break

        case 6:
            // evt 6 – MultiPressComplete; this includes press count
            if (logEnable) { log.debug "EVT_MULTI_COMPLETE count=${count}"}
            if (count == 1) {
                sendButtonEvent("pushed", buttonNumber)
            }
            else if (count == 2) {
                sendButtonEvent("doubleTapped", buttonNumber)
            }
            else {
                // triple+ → treat as pushed (or add tripleTapped custom attr if you want)
                sendButtonEvent("pushed", buttonNumber)
            }
            break

        default:
            logDebug "Unhandled switch event evt=${evt} ep=${ep} count=${count} msg=${msg}"
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

    // values may be a Map with numeric keys or a List; handle both.
    List<Integer> counts = []

    if (values instanceof Map) {
        values.each { k, v ->
            if (v instanceof Map && v.value != null) {
                Integer n = safeHexToInt(v.value) ?: safeInt(v.value)
                if (n != null) counts << n
            }
        }
    } else if (values instanceof List) {
        values.each { v ->
            if (v instanceof Map && v.value != null) {
                Integer n = safeHexToInt(v.value) ?: safeInt(v.value)
                if (n != null) counts << n
            }
        }
    }

    if (!counts) return null

    // prefer the highest parsed count (totalNumberOfPresses)
    return counts.max()
}

/* ---------- attribute handler ---------- */

private void handleAttributeReport(Map msg) {
    Integer ep      = safeInt(msg.endpointInt)
    Integer cluster = safeInt(msg.clusterInt)
    Integer attr    = safeInt(msg.attrInt)
    def valueObj    = msg.value

    // Battery: EP0, cluster 0x002F, attr 0x000C, raw 0..200
    if (ep == 0x00 && cluster == 0x002F && attr == 0x000C) {
        Integer raw = safeInt(valueObj)
        if (raw != null) {
            Integer pct = Math.round(raw / 2.0f)
            pct = Math.max(0, Math.min(100, pct))
            sendEvent(name: "battery", value: pct, unit: "%")
            if (txtEnable) log.info "${device.displayName} Battery is ${pct}%"
        }
        return
    }

    // We ignore Switch attribute 0x0001 here; events already give us better info
    // (and avoid duplicate 'pushed' events).
    // Fallback: handle simple press from Switch attribute if events don’t fire
    if (cluster == 0x003B && attr == 0x0001 && (ep == 0x01 || ep == 0x02)) {
        Integer v = safeInt(valueObj)
        if (v == null) return

        Integer buttonNumber = (ep == 0x01) ? 1 : 2

        if (v == 1) {
            // rising edge = pressed
            sendButtonEvent("pushed", buttonNumber)
        } else if (v == 0) {
            // optional release
            sendButtonEvent("released", buttonNumber)
        }
        return
    }
}

/* ---------- helpers ---------- */

private void sendButtonEvent(String type, Integer buttonNumber) {
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
    sendEvent(name: type, value: buttonNumber, isStateChange: true)

    // Persist last button event parameters for future filtering
    state.lastButtonNumber = buttonNumber
    state.lastAction = type
    state.lastButtonTime = now()
}


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
