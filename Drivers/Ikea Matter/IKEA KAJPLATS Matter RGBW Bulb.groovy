/*
 * IKEA KAJPLATS Matter RGBW Bulb (On/Off + Dimming + Color + Color Temperature)
 *
 * Device endpoints:
 *   EP0  : Basic Information (0x0028), General Diagnostics (0x0033)
 *   EP1  : Extended Color Light — OnOff (0x0006), LevelControl (0x0008), ColorControl (0x0300)
 *
 * Last edited: 2026/05/14
 */

import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.matter.DataType

metadata {
    definition(name: "IKEA KAJPLATS Matter RGBW Bulb w/ healthStatus", namespace: "community", author: "kkossev + ChatGPT + Claude", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Ikea%20Matter/IKEA%20KAJPLATS%20Matter%20RGBW%20Bulb.groovy") {

        capability "Switch"
        capability "SwitchLevel"
        capability "ColorControl"
        capability "ColorTemperature"
        capability "Refresh"
        capability "Initialize"
        capability "HealthCheck"

        attribute "healthStatus", "enum", ["online", "offline"]
        attribute "rtt",          "number"

        fingerprint endpointId: "01",
                inClusters: "0003,0004,0006,0008,001D,0300",
                outClusters: "",
                model: "KAJPLATS E14 CWS globe 806lm",
                manufacturer: "IKEA of Sweden",
                controllerType: "MAT"
    }

    preferences {
        input name: "txtEnable",         type: "bool", title: "Enable descriptionText logging",                         defaultValue: true
        input name: "logEnable",         type: "bool", title: "Enable debug logging",                                  defaultValue: false
        input name: "enableHealthCheck", type: "bool", title: "Enable health check (ping every 5 min)",                defaultValue: true
        input name: "enableAutoReInit",  type: "bool", title: "Auto re-initialize after 2 consecutive ping failures",  defaultValue: true
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
    paths.add(matter.attributePath(0x01, 0x0006, 0x0000)) // OnOff
    paths.add(matter.attributePath(0x01, 0x0008, 0x0000)) // CurrentLevel
    paths.add(matter.attributePath(0x01, 0x0300, 0x0000)) // CurrentHue
    paths.add(matter.attributePath(0x01, 0x0300, 0x0001)) // CurrentSaturation
    paths.add(matter.attributePath(0x01, 0x0300, 0x0007)) // ColorTemperatureMireds
    paths.add(matter.attributePath(0x01, 0x0300, 0x0008)) // ColorMode
    paths.add(matter.attributePath(0x00, 0x0028, 0x000A)) // SoftwareVersionString
    sendHubCommand(new HubAction(matter.readAttributes(paths), Protocol.MATTER))
}

private void subscribeToAttributes() {
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0006, 0x0000)) // OnOff
    paths.add(matter.attributePath(0x01, 0x0008, 0x0000)) // CurrentLevel
    paths.add(matter.attributePath(0x01, 0x0300, 0x0000)) // CurrentHue
    paths.add(matter.attributePath(0x01, 0x0300, 0x0001)) // CurrentSaturation
    paths.add(matter.attributePath(0x01, 0x0300, 0x0007)) // ColorTemperatureMireds
    paths.add(matter.attributePath(0x01, 0x0300, 0x0008)) // ColorMode
    sendHubCommand(new HubAction(matter.cleanSubscribe(1, 600, paths), Protocol.MATTER))
    logInfo "subscribing to switch/level/hue/saturation/colorTemp/colorMode (EP1)"
}

/* ---------- switch / level / color commands ---------- */

void on() {
    logDebug "turning on"
    sendHubCommand(new HubAction(matter.on(), Protocol.MATTER))
}

void off() {
    logDebug "turning off"
    sendHubCommand(new HubAction(matter.off(), Protocol.MATTER))
}

void setLevel(BigDecimal level, BigDecimal rate = null) {
    logDebug "setting level to ${level}%${rate != null ? " (rate=${rate}s)" : ""}"
    Integer levelScaled = int100To254(level as Integer)
    Integer durationTenths = (rate == null) ? 0 : (rate * 10) as Integer
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8,  0x00, HexUtils.integerToHexString(levelScaled, 1)),
        matter.cmdField(DataType.UINT16, 0x01, zigbee.swapOctets(HexUtils.integerToHexString(durationTenths, 2)))
    ]
    // MoveToLevelWithOnOff (0x04) so the bulb turns on when dimmed up
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0008, 0x04, cmdFields), Protocol.MATTER))
}

void setColorTemperature(BigDecimal colorTemperature, BigDecimal level = null, BigDecimal rate = null) {
    logDebug "setting color temperature to ${colorTemperature}K${level != null ? " level=${level}%" : ""}${rate != null ? " rate=${rate}s" : ""}"
    Integer mireds = ctToMired(colorTemperature as Integer)
    Integer durationTenths = (rate == null) ? 0 : (rate * 10) as Integer
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT16, 0x00, zigbee.swapOctets(HexUtils.integerToHexString(mireds, 2))),
        matter.cmdField(DataType.UINT16, 0x01, zigbee.swapOctets(HexUtils.integerToHexString(durationTenths, 2)))
    ]
    // MoveToColorTemperature (0x0A)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0300, 0x0A, cmdFields), Protocol.MATTER))
    if (level != null) { setLevel(level, rate) }
}

void setColor(Map colormap) {
    logDebug "setting color hue=${colormap.hue} saturation=${colormap.saturation}${colormap.level != null ? " level=${colormap.level}%" : ""}"
    Integer hueScaled = int100To254(colormap.hue as Integer)
    Integer satScaled  = int100To254(colormap.saturation as Integer)
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8,  0x00, HexUtils.integerToHexString(hueScaled, 1)),
        matter.cmdField(DataType.UINT8,  0x01, HexUtils.integerToHexString(satScaled, 1)),
        matter.cmdField(DataType.UINT16, 0x02, zigbee.swapOctets(HexUtils.integerToHexString(1, 2)))  // transition 0.1 s
    ]
    // MoveToHueAndSaturation (0x06)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0300, 0x06, cmdFields), Protocol.MATTER))
    if (colormap.level != null) { setLevel(colormap.level as BigDecimal, null) }
}

void setHue(BigDecimal hue) {
    logDebug "setting hue to ${hue}"
    Integer hueScaled = int100To254(hue as Integer)
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8,  0x00, HexUtils.integerToHexString(hueScaled, 1)),
        matter.cmdField(DataType.UINT8,  0x01, "00"),   // direction = Shortest
        matter.cmdField(DataType.UINT16, 0x02, zigbee.swapOctets(HexUtils.integerToHexString(1, 2)))
    ]
    // MoveToHue (0x00)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0300, 0x00, cmdFields), Protocol.MATTER))
}

void setSaturation(BigDecimal saturation) {
    logDebug "setting saturation to ${saturation}"
    Integer satScaled = int100To254(saturation as Integer)
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8,  0x00, HexUtils.integerToHexString(satScaled, 1)),
        matter.cmdField(DataType.UINT16, 0x01, zigbee.swapOctets(HexUtils.integerToHexString(1, 2)))
    ]
    // MoveToSaturation (0x03)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0300, 0x03, cmdFields), Protocol.MATTER))
}

/* ---------- parse ---------- */

void parse(String description) {
    logDebug "parse(String) called - ignored (newParse:true mode only)"
}

// parse(Map) - newParse:true format only
// OnOff       : [callbackType:Report, endpointInt:1, clusterInt:6,   attrInt:0,  value:true]
// Level       : [callbackType:Report, endpointInt:1, clusterInt:8,   attrInt:0,  value:127]
// Hue         : [callbackType:Report, endpointInt:1, clusterInt:768, attrInt:0,  value:127]
// Saturation  : [callbackType:Report, endpointInt:1, clusterInt:768, attrInt:1,  value:254]
// ColorTemp   : [callbackType:Report, endpointInt:1, clusterInt:768, attrInt:7,  value:263]
// ColorMode   : [callbackType:Report, endpointInt:1, clusterInt:768, attrInt:8,  value:2]
void parse(Map msg) {
    logDebug "parse(Map) received: ${msg}"
    handleLiveness(msg)

    Integer ep     = msg.endpointInt
    Integer clus   = msg.clusterInt
    Integer attrId = msg.attrInt

    if (ep == null || clus == null || attrId == null) return

    // Software version (Basic Information cluster 0x0028, attr 0x000A) — EP0
    if (clus == 0x0028 && attrId == 0x000A) {
        String ver = msg.value?.toString() ?: ""
        device.updateDataValue("softwareVersion", ver)
        logInfo "softwareVersion=${ver}"
        return
    }

    // All functional attributes on EP1
    if (ep != 0x01) {
        logDebug "Ignoring endpointInt=${ep} cluster=0x${Integer.toHexString(clus)}"
        return
    }

    // OnOff: cluster 0x0006 attr 0x0000
    if (clus == 0x0006 && attrId == 0x0000) {
        Integer v = safeInt(msg.value)
        if (v != null) {
            String sw = (v != 0) ? "on" : "off"
            sendEvent(name: "switch", value: sw, descriptionText: "${device.displayName} switch is ${sw}", type: "physical")
            logInfo "Switch is ${sw}"
        }
        return
    }

    // CurrentLevel: cluster 0x0008 attr 0x0000 (0-254 -> 0-100)
    if (clus == 0x0008 && attrId == 0x0000) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            Integer lvl = int254To100(raw)
            sendEvent(name: "level", value: lvl, unit: "%", descriptionText: "${device.displayName} level is ${lvl}%", type: "physical")
            logInfo "Level is ${lvl}%"
        }
        return
    }

    // --- Color Control cluster 0x0300 ---

    // CurrentHue: attr 0x0000 (0-254 -> 0-100)
    if (clus == 0x0300 && attrId == 0x0000) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            Integer h = int254To100(raw)
            sendEvent(name: "hue", value: h, descriptionText: "${device.displayName} hue is ${h}", type: "physical")
            logInfo "Hue is ${h}"
            if ((device.currentValue("colorMode") ?: "") != "CT") { updateColorName() }
        }
        return
    }

    // CurrentSaturation: attr 0x0001 (0-254 -> 0-100)
    if (clus == 0x0300 && attrId == 0x0001) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            Integer s = int254To100(raw)
            sendEvent(name: "saturation", value: s, descriptionText: "${device.displayName} saturation is ${s}", type: "physical")
            logInfo "Saturation is ${s}"
            if ((device.currentValue("colorMode") ?: "") != "CT") { updateColorName() }
        }
        return
    }

    // ColorTemperatureMireds: attr 0x0007 (mireds -> Kelvin)
    if (clus == 0x0300 && attrId == 0x0007) {
        Integer raw = safeInt(msg.value)
        if (raw != null && raw > 0) {
            Integer ct = miredToKelvin(raw)
            sendEvent(name: "colorTemperature", value: ct, unit: "K", descriptionText: "${device.displayName} colorTemperature is ${ct}K", type: "physical")
            logInfo "Color temperature is ${ct}K"
            if ((device.currentValue("colorMode") ?: "") == "CT") { updateColorName() }
        }
        return
    }

    // ColorMode: attr 0x0008 (0=RGB, 1=XY, 2=CT)
    if (clus == 0x0300 && attrId == 0x0008) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            String cm = (raw == 0) ? "RGB" : (raw == 2) ? "CT" : "RGB"
            sendEvent(name: "colorMode", value: cm, descriptionText: "${device.displayName} colorMode is ${cm}", type: "physical")
            logInfo "Color mode is ${cm}"
            updateColorName()
        }
        return
    }

    logDebug "parse(Map): unhandled msg: ${msg}"
}

/* ---------- color name helper ---------- */

private void updateColorName() {
    String cm = device.currentValue("colorMode") ?: ""
    String colorName
    if (cm == "CT") {
        Integer ct = (device.currentValue("colorTemperature") ?: 0) as Integer
        if (ct > 0) { colorName = convertTemperatureToGenericColorName(ct) }
    } else {
        Integer h = (device.currentValue("hue") ?: 0) as Integer
        Integer s = (device.currentValue("saturation") ?: 0) as Integer
        colorName = convertHueToGenericColorName(h, s)
    }
    if (colorName) {
        sendEvent(name: "colorName", value: colorName, descriptionText: "${device.displayName} color is ${colorName}")
        logInfo "Color name is ${colorName}"
    }
}

/* ---------- helpers ---------- */

private Integer safeInt(def v) {
    if (v == null) return null
    if (v instanceof Boolean) return v ? 1 : 0
    try { return Integer.parseInt(v.toString(), 10) } catch (Exception ignored) { return null }
}

// 0-100 (Hubitat) <-> 0-254 (Matter)
private Integer int100To254(Integer v) { return (int) Math.round(Math.max(0, Math.min(v ?: 0, 100)) * 2.54) }
private Integer int254To100(Integer v) {
    if (v == null || v <= 0) return 0
    Integer pct = (int) Math.round(v / 2.54)
    return Math.max(1, Math.min(pct, 100))   // raw>0 always maps to at least 1%
}

// Color temperature conversion
private Integer ctToMired(Integer kelvin) { return (int) Math.round(1000000.0 / kelvin) }
private Integer miredToKelvin(Integer mireds) { return (int) Math.round(1000000.0 / mireds) }

/* ---------- health check ---------- */

private void handleLiveness(Map msg) {
    unschedule("autoReInit")
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
    state.pingConsecutiveFails = 0
    if (device.currentValue("healthStatus") == "offline") {
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

private void logDebug(String msg) { if (logEnable)  { log.debug "${device.displayName} ${msg}" } }
private void logInfo(String msg)  { if (txtEnable)  { log.info  "${device.displayName} ${msg}" } }
private void logWarn(String msg)  { log.warn "${device.displayName} ${msg}" }
