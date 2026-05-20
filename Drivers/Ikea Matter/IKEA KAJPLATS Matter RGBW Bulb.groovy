/*
 * IKEA KAJPLATS Matter RGBW Bulb (On/Off + Dimming + Color + Color Temperature)
 *
 * Device endpoints:
 *   EP0  : Basic Information (0x0028), General Diagnostics (0x0033)
 *   EP1  : Extended Color Light — OnOff (0x0006), LevelControl (0x0008), ColorControl (0x0300)
 *
 * Last edited: 2026/05/16 11:26 AM
 */

import hubitat.device.HubAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.matter.DataType

metadata {
    definition(name: "IKEA KAJPLATS Matter RGBW Bulb w/ healthStatus", namespace: "community", author: "kkossev + ChatGPT + Claude", singleThreaded: true, importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Ikea%20Matter/IKEA%20KAJPLATS%20Matter%20RGBW%20Bulb.groovy") {

        capability "Switch"
        capability "SwitchLevel"
        capability "ChangeLevel"
        capability "ColorControl"
        capability "ColorTemperature"
        capability "Refresh"
        capability "Initialize"
        capability "HealthCheck"

        attribute "healthStatus", "enum", ["online", "offline"]
        attribute "rtt",          "number"

        command "clearStatistics"

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
        input name: "colorPreStaging",   type: "bool", title: "Enable color pre-staging",                                      defaultValue: true
        input name: "transitionTime",    type: "enum", title: "Transition time",
            options: ["ASAP":"ASAP", "500ms":"500ms", "1s":"1s", "1.5s":"1.5s", "2s":"2s", "5s":"5s"],
            defaultValue: "1s"
        input name: "levelChangeRate",    type: "enum", title: "Hold-to-dim rate (startLevelChange)",
            options: ["slow":"Slow (~10s full sweep)", "medium":"Medium (~5s full sweep)", "fast":"Fast (~2.5s full sweep)", "vfast":"Very fast (~1.5s full sweep)"],
            defaultValue: "medium"
        input name: "enableHealthCheck", type: "bool", title: "Enable health check (ping every 5 min)",                defaultValue: true
        input name: "enableAutoReInit",  type: "bool", title: "Auto re-initialize after 2 consecutive ping failures",  defaultValue: true
    }
}

void clearStatistics() {
    logInfo "Clearing statistics: ${state.stats}"
    state.stats = [initializeCounter: 0, pingFailCounter: 0, autoReInitCounter: 0]
    logInfo "Statistics cleared"
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
    sendHubCommand(new HubAction(matter.cleanSubscribe(0, 600, paths), Protocol.MATTER))
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
    Integer durationTenths = (rate == null) ? transitionTimeTenths() : (rate * 10) as Integer
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8,  0x00, HexUtils.integerToHexString(levelScaled, 1)),
        matter.cmdField(DataType.UINT16, 0x01, zigbee.swapOctets(HexUtils.integerToHexString(durationTenths, 2))),
        matter.cmdField(DataType.UINT8,  0x02, "00"),   // OptionsMask
        matter.cmdField(DataType.UINT8,  0x03, "00")    // OptionsOverride
    ]
    // MoveToLevelWithOnOff (0x04) so the bulb turns on when dimmed up
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0008, 0x04, cmdFields), Protocol.MATTER))
}

void startLevelChange(String direction) {
    logDebug "startLevelChange direction=${direction}"
    Integer moveMode = (direction == "down") ? 0x01 : 0x00
    Integer rate = levelChangeRateUnitsPerSec()
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8, 0x00, HexUtils.integerToHexString(moveMode, 1)),
        matter.cmdField(DataType.UINT8, 0x01, HexUtils.integerToHexString(rate, 1)),   // rate (level units/sec)
        matter.cmdField(DataType.UINT8, 0x02, "00"),   // OptionsMask
        matter.cmdField(DataType.UINT8, 0x03, "00")    // OptionsOverride
    ]
    // MoveWithOnOff (0x05)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0008, 0x05, cmdFields), Protocol.MATTER))
}

void stopLevelChange() {
    logDebug "stopLevelChange"
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8, 0x00, "00"),   // OptionsMask
        matter.cmdField(DataType.UINT8, 0x01, "00")    // OptionsOverride
    ]
    // Stop (0x03)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0008, 0x03, cmdFields), Protocol.MATTER))
}

void setColorTemperature(BigDecimal colorTemperature, BigDecimal level = null, BigDecimal rate = null) {
    logDebug "setting color temperature to ${colorTemperature}K${level != null ? " level=${level}%" : ""}${rate != null ? " rate=${rate}s" : ""}"
    Integer mireds = ctToMired(colorTemperature as Integer)
    Integer durationTenths = (rate == null) ? transitionTimeTenths() : (rate * 10) as Integer
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT16, 0x00, zigbee.swapOctets(HexUtils.integerToHexString(mireds, 2))),
        matter.cmdField(DataType.UINT16, 0x01, zigbee.swapOctets(HexUtils.integerToHexString(durationTenths, 2)))
    ]
    // MoveToColorTemperature (0x0A)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0300, 0x0A, cmdFields), Protocol.MATTER))
    Boolean isOff = (device.currentValue("switch") == "off")
    if (colorPreStaging != false && isOff) {
        logDebug "color pre-staging: bulb is off, skipping on/level command"
    } else if (level != null) {
        setLevel(level, rate)
    } else if (isOff) {
        on()
    }
}

void setColor(Map colormap) {
    logDebug "setting color hue=${colormap.hue} saturation=${colormap.saturation}${colormap.level != null ? " level=${colormap.level}%" : ""}"
    Integer hueScaled = int100To254(colormap.hue as Integer)
    Integer satScaled  = int100To254(colormap.saturation as Integer)
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8,  0x00, HexUtils.integerToHexString(hueScaled, 1)),
        matter.cmdField(DataType.UINT8,  0x01, HexUtils.integerToHexString(satScaled, 1)),
        matter.cmdField(DataType.UINT16, 0x02, zigbee.swapOctets(HexUtils.integerToHexString(transitionTimeTenths(), 2)))
    ]
    // MoveToHueAndSaturation (0x06)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0300, 0x06, cmdFields), Protocol.MATTER))
    Boolean isOff = (device.currentValue("switch") == "off")
    if (colorPreStaging != false && isOff) {
        logDebug "color pre-staging: bulb is off, skipping on/level command"
    } else if (colormap.level != null) {
        setLevel(colormap.level as BigDecimal, null)
    } else if (isOff) {
        on()
    }
}

void setHue(BigDecimal hue) {
    logDebug "setting hue to ${hue}"
    Integer hueScaled = int100To254(hue as Integer)
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8,  0x00, HexUtils.integerToHexString(hueScaled, 1)),
        matter.cmdField(DataType.UINT8,  0x01, "00"),   // direction = Shortest
        matter.cmdField(DataType.UINT16, 0x02, zigbee.swapOctets(HexUtils.integerToHexString(transitionTimeTenths(), 2)))
    ]
    // MoveToHue (0x00)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0300, 0x00, cmdFields), Protocol.MATTER))
    if (colorPreStaging == false && device.currentValue("switch") == "off") { on() }
}

void setSaturation(BigDecimal saturation) {
    logDebug "setting saturation to ${saturation}"
    Integer satScaled = int100To254(saturation as Integer)
    List<Map<String,String>> cmdFields = [
        matter.cmdField(DataType.UINT8,  0x00, HexUtils.integerToHexString(satScaled, 1)),
        matter.cmdField(DataType.UINT16, 0x01, zigbee.swapOctets(HexUtils.integerToHexString(transitionTimeTenths(), 2)))
    ]
    // MoveToSaturation (0x03)
    sendHubCommand(new HubAction(matter.invoke(0x01, 0x0300, 0x03, cmdFields), Protocol.MATTER))
    if (colorPreStaging == false && device.currentValue("switch") == "off") { on() }
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
            sendEvent(name: "hue", value: h, descriptionText: "${device.displayName} hue is ${h}%", type: "physical")
            logInfo "Hue is ${h}%"
            if ((device.currentValue("colorMode") ?: "") != "CT") { updateColorName() }
        }
        return
    }

    // CurrentSaturation: attr 0x0001 (0-254 -> 0-100)
    if (clus == 0x0300 && attrId == 0x0001) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            Integer s = int254To100(raw)
            sendEvent(name: "saturation", value: s, descriptionText: "${device.displayName} saturation is ${s}%", type: "physical")
            logInfo "Saturation is ${s}%"
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

private Integer levelChangeRateUnitsPerSec() {
    switch (levelChangeRate) {
        case "slow":  return 25   // ~10s full sweep
        case "fast":  return 100  // ~2.5s full sweep
        case "vfast": return 170  // ~1.5s full sweep
        default:      return 50   // medium ~5s full sweep
    }
}

private Integer transitionTimeTenths() {
    switch (transitionTime) {
        case "ASAP":  return 0
        case "500ms": return 5
        case "1.5s":  return 15
        case "2s":    return 20
        case "5s":    return 50
        default:      return 10  // 1s
    }
}

// Color temperature conversion
private Integer ctToMired(Integer kelvin) { return (int) Math.round(1000000.0 / kelvin) }
private Integer miredToKelvin(Integer mireds) { return (int) Math.round(1000000.0 / mireds) }

/* ---------- health check ---------- */

private void handleLiveness(Map msg) {
    unschedule("autoReInit")
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
