/*
 * IKEA ALPSTUGA Matter Air Quality Monitor
 * 
 * Last edited: 2026/05/14 5:24 PM
 *
 */

import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(name: "IKEA ALPSTUGA Matter w/ healthStatus", namespace: "community", author: "kkossev + ChatGPT + Claude", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Ikea%20Matter/IKEA%20ALPSTUGA%20Matter%20AQ%2BTemp%2BHum%2BPM2.5%2BCO2.groovy") {
        capability "Sensor"
        capability "Switch"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "AirQuality"
        capability "CarbonDioxideMeasurement"
        capability "Refresh"
        capability "Initialize"
        capability "HealthCheck"

        attribute "airQuality", "string"
        attribute "pm25", "number"
        attribute "healthStatus", "enum", ["online", "offline"]
        attribute "rtt", "number"
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
    paths.add(matter.attributePath(0x01, 0x0006, 0x0000)) // OnOff
    paths.add(matter.attributePath(0x01, 0x005B, 0x0000)) // AirQuality
    paths.add(matter.attributePath(0x01, 0x0402, 0x0000)) // Temperature
    paths.add(matter.attributePath(0x01, 0x0405, 0x0000)) // Humidity
    paths.add(matter.attributePath(0x01, 0x042A, 0x0000)) // PM2.5
    paths.add(matter.attributePath(0x01, 0x040D, 0x0000)) // CO2 MeasuredValue
    paths.add(matter.attributePath(0x00, 0x0028, 0x000A)) // Software version string

    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private void subscribeToAttributes() {
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0006, 0x0000))
    paths.add(matter.attributePath(0x01, 0x005B, 0x0000))
    paths.add(matter.attributePath(0x01, 0x0402, 0x0000))
    paths.add(matter.attributePath(0x01, 0x0405, 0x0000))
    paths.add(matter.attributePath(0x01, 0x042A, 0x0000))
    paths.add(matter.attributePath(0x01, 0x040D, 0x0000))

    String cmd = matter.cleanSubscribe(1, 600, paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))

    logInfo "subscribing to on/off + air quality + temp + humidity + PM2.5 + CO2 (EP1)"
}

void parse(String description) {
    logDebug "parse(String) called - ignored (newParse:true mode only)"
}

// parse(Map) - newParse:true format only
// Attribute report : [callbackType:Report, endpointInt:1, clusterInt:1026, attrInt:0, value:2343]
// Float report     : [callbackType:Report, endpointInt:1, clusterInt:1066, attrInt:0, value:5.0]
void parse(Map msg) {
    logDebug "parse(Map) received: ${msg}"
    handleLiveness(msg)

    Integer ep     = msg.endpointInt
    Integer clus   = msg.clusterInt
    Integer attrId = msg.attrInt

    if (ep == null || clus == null || attrId == null) return

    // Software version string (Basic Information cluster 0x0028, attr 0x000A) — EP0
    if (clus == 0x0028 && attrId == 0x000A) {
        String ver = msg.value?.toString() ?: ""
        device.updateDataValue("softwareVersion", ver)
        logInfo "softwareVersion=${ver}"
        return
    }

    // All sensor attributes are on EP1
    if (ep != 0x01) {
        logDebug "Ignoring endpointInt=${ep} cluster=${clus}"
        return
    }

    // On/Off: cluster 0x0006 attr 0x0000
    if (clus == 0x0006 && attrId == 0x0000) {
        Integer v = safeInt(msg.value)
        if (v != null) {
            String sw = (v != 0) ? "on" : "off"
            sendEvent(name: "switch", value: sw, descriptionText: txtEnable ? "Switch is ${sw}" : null)
            logInfo "Switch is ${sw}"
        }
        return
    }

    // Air Quality: cluster 0x005B attr 0x0000
    if (clus == 0x005B && attrId == 0x0000) {
        Integer aq = safeInt(msg.value)
        if (aq != null) {
            String aqText = airQualityToText(aq)
            sendEvent(name: "airQuality", value: aqText, descriptionText: txtEnable ? "Air quality is ${aqText}" : null)
            sendEvent(name: "airQualityIndex", value: aq)
            logInfo "Air quality is ${aqText} (index: ${aq})"
        }
        return
    }

    // Temperature: cluster 0x0402 attr 0x0000 (0.01 °C, signed)
    if (clus == 0x0402 && attrId == 0x0000) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            BigDecimal c = ((short) raw) / 100.0
            BigDecimal cRounded = c.setScale(1, BigDecimal.ROUND_HALF_UP)
            def t = convertTemperatureIfNeeded(cRounded, "C", 1)
            String unit = (location.temperatureScale == "F") ? "°F" : "°C"
            sendEvent(name: "temperature", value: t, unit: unit, descriptionText: txtEnable ? "Temperature is ${t} ${unit}" : null)
            logInfo "Temperature is ${t} ${unit}"
        }
        return
    }

    // Humidity: cluster 0x0405 attr 0x0000 (0.01 %)
    if (clus == 0x0405 && attrId == 0x0000) {
        Integer raw = safeInt(msg.value)
        if (raw != null) {
            BigDecimal rh = (raw / 100.0).setScale(1, BigDecimal.ROUND_HALF_UP)
            sendEvent(name: "humidity", value: rh, unit: "%", descriptionText: txtEnable ? "Humidity is ${rh}%" : null)
            logInfo "Humidity is ${rh}%"
        }
        return
    }

    // PM2.5: cluster 0x042A attr 0x0000 (IEEE 754 float32)
    if (clus == 0x042A && attrId == 0x0000) {
        Float pm = safeFloat(msg.value)
        if (pm != null) {
            Integer pmInt = Math.round(pm)
            sendEvent(name: "pm25", value: pmInt, unit: "µg/m³", descriptionText: txtEnable ? "PM2.5 is ${pmInt} µg/m³" : null)
            logInfo "PM2.5 is ${pmInt} µg/m³"
        }
        return
    }

    // CO2: cluster 0x040D attr 0x0000 (IEEE 754 float32, ppm)
    if (clus == 0x040D && attrId == 0x0000) {
        Float co2f = safeFloat(msg.value)
        if (co2f != null) {
            Integer co2 = Math.round(co2f)
            sendEvent(name: "carbonDioxide", value: co2, unit: "ppm", descriptionText: txtEnable ? "CO₂ is ${co2} ppm" : null)
            logInfo "CO₂ is ${co2} ppm"
        }
        return
    }

    logDebug "parse(Map): unhandled msg: ${msg}"
}

private Integer safeInt(def v) {
    if (v == null) return null
    if (v instanceof Boolean) return v ? 1 : 0
    try { return Integer.parseInt(v.toString(), 10) } catch (Exception ignored) { return null }
}

// For IEEE 754 float32 clusters (PM2.5, CO2):
// In newParse:true mode Hubitat delivers float attributes as Java Float; fall back to intBitsToFloat for raw integer delivery.
private Float safeFloat(def v) {
    if (v == null) return null
    if (v instanceof Float) return v
    if (v instanceof Double) return v.floatValue()
    if (v instanceof Number) return Float.intBitsToFloat(v.intValue())
    try { return Float.intBitsToFloat(Integer.parseInt(v.toString(), 10)) } catch (Exception ignored) { return null }
}

void on() {
    String cmd = matter.on()
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

void off() {
    String cmd = matter.off()
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private static String airQualityToText(Integer v) {
    switch (v) {
        case 0: return "unknown"
        case 1: return "good"
        case 2: return "fair"
        case 3: return "moderate"
        case 4: return "poor"
        case 5: return "very poor"
        case 6: return "extremely poor"
        default: return "unknown"
    }
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
