/*
 * IKEA ALPSTUGA Matter Air Quality Monitor (minimal)
 * 
 * Last edited: 2026/01/03 10:24 AM
 *
 */

import hubitat.device.HubAction
import hubitat.device.Protocol

metadata {
    definition(name: "IKEA ALPSTUGA Matter", namespace: "community", author: "kkossev + ChatGPT") {
        capability "Sensor"
        capability "Switch"
        capability "TemperatureMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "AirQuality"
        capability "CarbonDioxideMeasurement"
        capability "Refresh"
        capability "Initialize"
        
        attribute "airQuality", "string"
        attribute "pm25", "number"
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
    paths.add(matter.attributePath(0x01, 0x0006, 0x0000)) // OnOff
    paths.add(matter.attributePath(0x01, 0x005B, 0x0000)) // AirQuality
    paths.add(matter.attributePath(0x01, 0x0402, 0x0000)) // Temperature
    paths.add(matter.attributePath(0x01, 0x0405, 0x0000)) // Humidity
    paths.add(matter.attributePath(0x01, 0x042A, 0x0000)) // PM2.5
    paths.add(matter.attributePath(0x01, 0x040D, 0x0000)) // CO2 MeasuredValue

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
    paths.add(matter.attributePath(0x01, 0x040D, 0x0000)) // CO2 MeasuredValue

    String cmd = matter.cleanSubscribe(1, 0xFFFF, paths)
    sendHubCommand(new HubAction(cmd, Protocol.MATTER))

    if (txtEnable) log.info "Subscribed to ALPSTUGA: on/off + air quality + temp + humidity + PM2.5"
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

    // EP01 only (based on your logs)
    if (ep != 0x01) {
        if (logEnable) log.debug "Ignoring EP${msg.endpoint}"
        return
    }

    // On/Off: cluster 0x0006 attr 0x0000 (Hubitat shows "value:01" even if raw line says 09)
    if (clus == 0x0006 && attrId == 0x0000) {
        Integer v = safeHexToInt(value)
        if (v != null) {
            String sw = (v != 0) ? "on" : "off"
            sendEvent(name: "switch", value: sw, descriptionText: txtEnable ? "Switch is ${sw}" : null)
            if (txtEnable) log.info "Switch is ${sw}"
        }
        return
    }

    // Air Quality: cluster 0x005B attr 0x0000 (you saw "value:01" -> good)
    if (clus == 0x005B && attrId == 0x0000) {
        Integer aq = safeHexToInt(value)
        if (aq != null) {
            String aqText = airQualityToText(aq)
            sendEvent(name: "airQuality", value: aqText, descriptionText: txtEnable ? "Air quality is ${aqText}" : null)
            sendEvent(name: "airQualityIndex", value: aq)
            if (txtEnable) log.info "Air quality is ${aqText} (index: ${aq})"
        }
        return
    }

    // Temperature: cluster 0x0402 attr 0x0000 (0.01 °C)
    if (clus == 0x0402 && attrId == 0x0000) {
        Integer raw = safeHexToInt(value)   // e.g. 0x0927 => 2343 => 23.43°C
        if (raw != null) {
            BigDecimal c = ((short) raw) / 100.0
            BigDecimal cRounded = c.setScale(1, BigDecimal.ROUND_HALF_UP)

            // Convert to hub's scale if needed (F hubs will get °F)
            def t = convertTemperatureIfNeeded(cRounded, "C", 1)

            String unit = (location.temperatureScale == "F") ? "°F" : "°C"
            sendEvent(
                name: "temperature",
                value: t,
                unit: unit,
                descriptionText: txtEnable ? "Temperature is ${t} ${unit}" : null
            )
            if (txtEnable) log.info "Temperature is ${t} ${unit}"
        }
        return
    }

    // Humidity: cluster 0x0405 attr 0x0000 (0.01 %)
    if (clus == 0x0405 && attrId == 0x0000) {
        Integer raw = safeHexToInt(value)   // e.g. 0x135C => 4956 => 49.56%
        if (raw != null) {
            BigDecimal rh = (raw / 100.0).setScale(1, BigDecimal.ROUND_HALF_UP)
            sendEvent(name: "humidity", value: rh, unit: "%",
                      descriptionText: txtEnable ? "Humidity is ${rh}%" : null)
            if (txtEnable) log.info "Humidity is ${rh}%"
        }
        return
    }

    // PM2.5: cluster 0x042A attr 0x0000
    // Hubitat shows value like 40A00000 -> IEEE754 float bits (5.0)
    if (clus == 0x042A && attrId == 0x0000) {
        Integer bits = safeHexToInt(value)
        if (bits != null) {
            float pm = Float.intBitsToFloat(bits)
            Integer pmInt = Math.round(pm)
            sendEvent(name: "pm25", value: pmInt, unit: "µg/m³",
                      descriptionText: txtEnable ? "PM2.5 is ${pmInt} µg/m³" : null)
            if (txtEnable) log.info "PM2.5 is ${pmInt} µg/m³"
        }
        return
    }
    
    // CO2: cluster 0x040D attr 0x0000
    // Hubitat shows value like 44AF6000 -> IEEE754 float bits (e.g. 1403.0)
    if (clus == 0x040D && attrId == 0x0000) {
        Integer bits = safeHexToInt(value)
        if (bits != null) {
            float co2f = Float.intBitsToFloat(bits)
            Integer co2 = Math.round(co2f)   // ppm as integer
            sendEvent(name: "carbonDioxide", value: co2, unit: "ppm",
                      descriptionText: txtEnable ? "CO₂ is ${co2} ppm" : null)
            if (txtEnable) log.info "CO₂ is ${co2} ppm"
        }
        return
    }
    
    if (logEnable) log.debug "Unhandled Matter report: ${msg}"
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
    // Keep this mapping minimal and safe.
    // You observed: 1 -> "good"
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

private Integer safeHexToInt(Object hex) {
    if (hex == null) return null
    String s = hex.toString().trim()
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2)
    if (s == "") return null
    try { return Integer.parseUnsignedInt(s, 16) } catch (Exception ignored) { return null }
}
