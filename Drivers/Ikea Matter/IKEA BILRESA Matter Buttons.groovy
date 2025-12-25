/*
 * IKEA BILRESA Matter Dual Button (events-based)
 *
 * Last edited: 2025/12/25 7:34 PM
 *
 * - No wildcard subscribe
 * - Subscribes to:
 *   * Switch (0x003B) events (all) on EP1 & EP2
 *   * Battery (0x002F) attr 0x000C on EP0
 *
 * Uses Matter Switch events (evtInt 1..6) for:
 *   - pushed       (MultiPressComplete count==1)
 *   - doubleTapped (MultiPressComplete count==2)
 *   - held         (LongPress event)
 */

import hubitat.device.HubAction
import hubitat.device.Protocol
import groovy.transform.Field

@Field static final Integer EP_BTN_1 = 0x01
@Field static final Integer EP_BTN_2 = 0x02
@Field static final Integer EP_BAT   = 0x00

@Field static final Integer CLUS_SWITCH   = 0x003B
@Field static final Integer CLUS_BAT      = 0x002F
@Field static final Integer ATTR_BAT_PCT  = 0x000C   // cluster 0x002F attr 0x000C -> raw 0..200

// Switch event IDs (Matter spec)
@Field static final Integer EVT_INITIAL_PRESS     = 1
@Field static final Integer EVT_LONG_PRESS        = 2
@Field static final Integer EVT_SHORT_RELEASE     = 3
@Field static final Integer EVT_LONG_RELEASE      = 4
@Field static final Integer EVT_MULTI_ONGOING     = 5
@Field static final Integer EVT_MULTI_COMPLETE    = 6

metadata {
    definition(name: "IKEA BILRESA Matter Buttons", namespace: "community", author: "kkossev + ChatGPT") {

        capability "Initialize"
        capability "Refresh"
        capability "Battery"

        capability "PushableButton"
        capability "HoldableButton"
        capability "DoubleTapableButton"
        capability "ReleasableButton"

        attribute "lastEvent", "string"

        fingerprint endpointId:"01", inClusters:"0003,001D,003B", outClusters:"", model:"BILRESA dual button", manufacturer:"IKEA of Sweden", controllerType:"MAT"
        fingerprint endpointId:"02", inClusters:"0003,001D,003B", outClusters:"", model:"BILRESA dual button", manufacturer:"IKEA of Sweden", controllerType:"MAT"
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
    paths.add(matter.attributePath(EP_BAT, CLUS_BAT, ATTR_BAT_PCT))

    // You *can* read switch state attributes too if you want, but not needed
    // paths.add(matter.attributePath(EP_BTN_1, CLUS_SWITCH, 0x0001))
    // paths.add(matter.attributePath(EP_BTN_2, CLUS_SWITCH, 0x0001))

    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private void subscribeToPaths() {
    List<Map<String,String>> paths = []

    // Battery attribute
    paths.add(matter.attributePath(EP_BAT, CLUS_BAT, ATTR_BAT_PCT))

    //paths.add(matter.attributePath(EP_BTN_1, CLUS_SWITCH, -1))
    //paths.add(matter.attributePath(EP_BTN_2, CLUS_SWITCH, -1))
    paths.add(matter.attributePath(-1, CLUS_SWITCH, -1))

    // Switch events (all IDs) for both endpoints
    //paths.add(matter.eventPath(EP_BTN_1, CLUS_SWITCH, -1))
    //paths.add(matter.eventPath(EP_BTN_2, CLUS_SWITCH, -1))
    paths.add(matter.eventPath(-1, CLUS_SWITCH, -1))

    String cmd = matter.cleanSubscribe(1, 0xFFFF, paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))

    if (txtEnable) log.info "${device.displayName} Subscribed to switch events (EP1/EP2) + battery (EP0/0x002F/0x000C)"
}

/* ---------- parsing ---------- */

void parse(String description) {
    Map msg = matter.parseDescriptionAsMap(description)
    logDebug "parse(String): ${description} -> ${msg}"
    if (!msg) return

    // --- KLIPPBOK-style battery decode from read-attr (EP0 / 0x002F / 0x000C) ---
    // These messages look like:
    //  read attr - endpoint: 00, cluster: 002F, attrId: 000C, value: 04C8
    //  -> [endpoint:00, cluster:002F, attrId:000C, value:C8, clusterInt:47, attrInt:12]
    Integer epHex   = safeHexToInt(msg.endpoint)
    Integer clusHex = safeHexToInt(msg.cluster)
    Integer attrId  = safeHexToInt(msg.attrId)
    String  value   = msg.value?.toString()
    String  cb      = (msg.callbackType ?: "").toString()

    if (cb == "" && epHex != null && clusHex != null && attrId != null &&
        epHex == EP_BAT && clusHex == CLUS_BAT && attrId == ATTR_BAT_PCT) {

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
    if (cluster == CLUS_SWITCH && evt != null && (ep == EP_BTN_1 || ep == EP_BTN_2)) {
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
    Integer buttonNumber = (ep == EP_BTN_1) ? 1 : 2
    Integer count        = extractMultiPressCount(msg) ?: 1

    switch (evt) {
        case EVT_INITIAL_PRESS:
            // evt 1 – InitialPress; usually followed by LongPress or ShortRelease/MultiPress*
            if (logEnable) { log.debug "EVT_INITIAL_PRESS"}
            break

        case EVT_LONG_PRESS:
            // evt 2 – LongPress
            sendButtonEvent("held", buttonNumber)
            break

        case EVT_SHORT_RELEASE:
            // 3 – ShortRelease
            // If you want a release after any short press, enable this:
            sendButtonEvent("released", buttonNumber)
        	break

        case EVT_LONG_RELEASE:
            // 4 – LongRelease
            sendButtonEvent("released", buttonNumber)
        	break

        case EVT_MULTI_ONGOING:
            // evt 5 – MultiPressOngoing; we’ll wait for MultiPressComplete
            if (logEnable) { log.debug "EVT_MULTI_ONGOING"}
            break

        case EVT_MULTI_COMPLETE:
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
    if (!(values instanceof Map)) return null

    def entry = values[1] ?: values[0]
    if (!(entry instanceof Map)) return null

    return safeHexToInt(entry.value)
}

/* ---------- attribute handler ---------- */

private void handleAttributeReport(Map msg) {
    Integer ep      = safeInt(msg.endpointInt)
    Integer cluster = safeInt(msg.clusterInt)
    Integer attr    = safeInt(msg.attrInt)
    def valueObj    = msg.value

    // Battery: EP0, cluster 0x002F, attr 0x000C, raw 0..200
    if (ep == EP_BAT && cluster == CLUS_BAT && attr == ATTR_BAT_PCT) {
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
    if (cluster == CLUS_SWITCH && attr == 0x0001 && (ep == EP_BTN_1 || ep == EP_BTN_2)) {
        Integer v = safeInt(valueObj)
        if (v == null) return

        Integer buttonNumber = (ep == EP_BTN_1) ? 1 : 2

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
    if (txtEnable) log.info "${device.displayName} button ${buttonNumber} ${type}"
    sendEvent(name: type, value: buttonNumber, isStateChange: true)
    sendLastEvent("button ${buttonNumber} ${type}")
}

private void sendLastEvent(String text) {
    sendEvent(name: "lastEvent", value: text, isStateChange: true)
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
