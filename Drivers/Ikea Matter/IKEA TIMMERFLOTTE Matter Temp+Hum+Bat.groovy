/*
 * IKEA TIMMERFLOTTE Matter Temp + Humidity + Battery(minimal)
 *
 * Last edited: 2025/12/24 4:49 PM
 */

import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(name: "IKEA TIMMERFLOTTE Matter Temp+Hum+Bat", namespace: "community", author: "kkossev + ChatGPT") {
        capability "Sensor"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "Battery"
        capability "Refresh"
        capability "Initialize"
    }
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    }
}

void installed() { initialize() }

void updated() {
    if (logEnable) runIn(1800, "logsOff")
    initialize()
}

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.warn "Debug logging disabled"
}

void initialize() {
    if (logEnable) log.debug "initialize()"
    subscribeToAttributes()
    refresh()
}

void refresh() {
    if (logEnable) log.debug "refresh()"
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0402, 0x0000)) // Temperature MeasuredValue
    paths.add(matter.attributePath(0x02, 0x0405, 0x0000)) // Humidity MeasuredValue
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C)) // BatteryPercentRemaining
    String cmd = matter.readAttributes(paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))
}

private void subscribeToAttributes() {
    List<Map<String,String>> paths = []
    paths.add(matter.attributePath(0x01, 0x0402, 0x0000)) // temp
    paths.add(matter.attributePath(0x02, 0x0405, 0x0000)) // humidity
    paths.add(matter.attributePath(0x00, 0x002F, 0x000C)) // BatteryPercentRemaining

    String cmd = matter.cleanSubscribe(1, 0xFFFF, paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))

    if (txtEnable) log.info "Subscribed to TIMMERFLOTTE: temperature + humidity"
}

def parse(String description) {
    Map msg = matter.parseDescriptionAsMap(description)
    if (logEnable) log.debug "parse: ${description} msg: ${msg}"
    if (!msg) return

    Integer ep     = safeHexToInt(msg.endpoint)
    Integer clus   = safeHexToInt(msg.cluster)
    Integer attrId = safeHexToInt(msg.attrId)
    String value   = msg.value?.toString()
    if (ep == null || clus == null || attrId == null) return

    // Temperature: EP01 cluster 0x0402 attr 0x0000 (0.01 °C)
    if (ep == 0x01 && clus == 0x0402 && attrId == 0x0000) {
        Integer raw = safeHexToInt(value)
        if (raw != null) {
            BigDecimal c = raw / 100.0
            BigDecimal cRounded = c.setScale(1, BigDecimal.ROUND_HALF_UP)

            def t = convertTemperatureIfNeeded(cRounded, "C", 1)
            String unit = (location.temperatureScale == "F") ? "°F" : "°C"

            String descText = "Temperature is ${t} ${unit}"
            sendEvent(name: "temperature", value: t, unit: unit, descriptionText: txtEnable ? descText : null)
            if (txtEnable) log.info descText
        }
        return
    }

    // Humidity: EP02 cluster 0x0405 attr 0x0000 (0.01 %)
    if (ep == 0x02 && clus == 0x0405 && attrId == 0x0000) {
        Integer raw = safeHexToInt(value)
        if (raw != null) {
            BigDecimal rh = (raw / 100.0).setScale(1, BigDecimal.ROUND_HALF_UP)
            String descText = "Humidity is ${rh}%"
            sendEvent(name: "humidity", value: rh, unit: "%", descriptionText: txtEnable ? descText : null)
            if (txtEnable) log.info descText
        }
        return
    }
    
    if (ep == 0x00 && clus == 0x002F && attrId == 0x000C) {
        Integer pctRaw = safeHexToInt(value)   // typically 0..200
        if (pctRaw != null) {
            Integer pct = (int)(pctRaw / 2)
            pct = Math.max(0, Math.min(100, pct))
            sendEvent(name: "battery", value: pct, unit: "%")
            if (txtEnable) log.info "Battery is ${pct}%"
        }
        return
    }
    if (logEnable) log.debug "Unhandled Matter report: ${msg}"
}

private Integer safeHexToInt(Object hex) {
    if (hex == null) return null
    String s = hex.toString().trim()
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2)
    if (s == "") return null
    try { return Integer.parseUnsignedInt(s, 16) } catch (Exception ignored) { return null }
}
