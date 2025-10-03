/*
 *  NOUS E10 (TS0601 / _TZE200_xpvamyfz) – Multifunctional CO2 Detector
 *
 *  Hubitat Elevation Driver (Groovy)
 *
 *  Based on a Zigbee2MQTT definition mapping of Tuya DPs:
 *    DP 1  -> air_quality (enum: 0=excellent,1=moderate,2=poor)
 *    DP 2  -> co2 (value, ppm)
 *    DP 5  -> alarm_ringtone (enum: 0=melody_1,1=melody_2,2=OFF)
 *    DP 14 -> battery (%)
 *    DP 17 -> backlight_mode (value 1..3)
 *    DP 18 -> temperature (value /10, °C)
 *    DP 19 -> humidity (%)
 *
 *  Capabilities:
 *    - CarbonDioxideMeasurement (ppm)
 *    - RelativeHumidityMeasurement (%)
 *    - TemperatureMeasurement (°C)
 *    - Battery
 *
 *  Custom Attributes:
 *    - airQuality (string: excellent/moderate/poor)
 *    - alarmRingtone (string: melody_1/melody_2/OFF)
 *    - backlightMode (number: 1..3)
 *
 *  Custom Commands:
 *    - setAlarmRingtone(String ringtone) // "melody_1", "melody_2", "OFF"
 *    - setBacklightMode(Number level)    // 1..3
 *
 *  Notes:
 *    - Tuya EF00 cluster parsing implemented locally (no common library dependency).
 *    - Time sync is optional; a lightweight Tuya time sync is included in configure().
 *    - This driver focuses on the above DPs as per the provided Z2M spec.
 *
 *  Author: ChatGPT (for Krassimir / Hubitat)
 *  Date: 2025-10-03
 */

import hubitat.zigbee.zcl.DataType

metadata {
    definition (name: "NOUS E10 Zigbee Smart CO2 Detector", namespace: "community", author: "ChatGPT", importUrl: "") {
        capability "CarbonDioxideMeasurement"
        capability "RelativeHumidityMeasurement"
        capability "TemperatureMeasurement"
        capability "Battery"
        capability "Refresh"
        capability "Configuration"

        attribute "airQuality", "STRING"
        attribute "alarmRingtone", "STRING"
        attribute "backlightMode", "NUMBER"

        command "setAlarmRingtone", [[name: "ringtone*", type: "ENUM", constraints: ["melody_1","melody_2","OFF"], description: "Alarm ringtone selection"]]
        command "setBacklightMode", [[name: "level*", type: "NUMBER", description: "Backlight level 1..3"]]

        // Basic Tuya TS0601 fingerprint
        fingerprint profileId: "0104", inClusters: "0000,0003,EF00", outClusters: "0003,0019", model: "TS0601", manufacturer: "_TZE200_xpvamyfz", deviceJoinName: "NOUS E10 CO2"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable info logging", defaultValue: true
        input name: "tempOffset", type: "decimal", title: "Temperature offset (°C)", defaultValue: 0, range: "-10..10"
        input name: "humOffset", type: "number", title: "Humidity offset (%)", defaultValue: 0, range: "-20..20"
        input name: "co2Offset", type: "number", title: "CO2 offset (ppm)", defaultValue: 0, range: "-2000..2000"
    }
}

/* ------------------------------- Lifecycle ------------------------------- */

def installed() {
    if (txtEnable) log.info "Installed: ${device.displayName}"
}

def updated() {
    if (txtEnable) log.info "Updated: ${device.displayName}"
    if (logEnable) runIn(1800, "logsOff")
}

def configure() {
    if (txtEnable) log.info "Configuring ${device.displayName}"
    // Attempt a lightweight Tuya time sync (not critical if it fails)
    sendHubCommand(new hubitat.device.HubAction(zigbee.command(0xEF00, 0x24, [:], createTuyaTimePayload()), hubitat.device.Protocol.ZIGBEE))
    refresh()
}

void logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.warn "Debug logging disabled"
}

/* --------------------------------- Parse -------------------------------- */

def parse(String description) {
    if (logEnable) log.debug "parse(): ${description}"

    if (description?.startsWith('catchall:') || description?.startsWith('read attr -') || description?.startsWith('zigbee')) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (logEnable) log.debug "descMap = ${descMap}"

        if (descMap?.clusterInt == 0xEF00) {
            parseTuyaCluster(descMap)
            return null
        }
    }
    return null
}

/* ------------------------------ Tuya Parsing ----------------------------- */
// Tuya EF00 payload format per DP frame: [DP (2B)][TYPE (1B)][LEN (2B)][DATA (N)]
private void parseTuyaCluster(Map descMap) {
    try {
        byte[] data = descMap?.data?.collect{ (int)Integer.parseInt(it,16) as byte } as byte[]
        if (!data) return
        int idx = 0
        while (idx + 4 < data.size()) {
            int dp = (data[idx] & 0xFF) << 8 | (data[idx+1] & 0xFF); idx += 2
            int dpType = data[idx++] & 0xFF
            int dpLen = (data[idx] & 0xFF) << 8 | (data[idx+1] & 0xFF); idx += 2
            if (idx + dpLen > data.size()) break
            byte[] dpData = subBytes(data, idx, dpLen); idx += dpLen
            handleTuyaDP(dp, dpType, dpLen, dpData)
        }
    } catch (e) {
        log.warn "parseTuyaCluster() exception: ${e}"
    }
}

private void handleTuyaDP(int dp, int dpType, int dpLen, byte[] dpData) {
    if (logEnable) log.debug "DP=${dp} type=${hex(dpType)} len=${dpLen} data=${bytesToHex(dpData)}"
    switch (dp) {
        case 1: // air_quality enum
            Integer v = tuyaGetEnum(dpType, dpData)
            String quality = [0:"excellent", 1:"moderate", 2:"poor"][v] ?: "unknown"
            sendEvent(name:"airQuality", value:quality, descriptionText:"Air quality ${quality}")
            break
        case 2: // CO2 value (ppm)
            Long ppm = tuyaGetValue(dpType, dpData)
            if (ppm != null) {
                int adj = ((ppm as BigDecimal) + (settings.co2Offset ?: 0)).toInteger()
                sendEvent(name:"carbonDioxide", value: adj, unit:"ppm", descriptionText:"CO2 ${adj} ppm")
            }
            break
        case 5: // alarm_ringtone enum
            Integer r = tuyaGetEnum(dpType, dpData)
            String ring = [0:"melody_1", 1:"melody_2", 2:"OFF"][r] ?: "unknown"
            sendEvent(name:"alarmRingtone", value:ring, descriptionText:"Alarm ringtone ${ring}")
            break
        case 14: // battery (%)
            Long bat = tuyaGetValue(dpType, dpData)
            if (bat != null) {
                int pct = Math.max(0, Math.min(100, bat as int))
                sendEvent(name:"battery", value:pct, unit:"%", descriptionText:"Battery ${pct}%")
            }
            break
        case 17: // backlight_mode (1..3)
            Long bl = tuyaGetValue(dpType, dpData)
            if (bl != null) {
                sendEvent(name:"backlightMode", value: bl as int, descriptionText:"Backlight mode ${bl}")
            }
            break
        case 18: // temperature (divide by 10)
            Long tv = tuyaGetValue(dpType, dpData)
            if (tv != null) {
                BigDecimal t = (tv as BigDecimal) / 10G
                BigDecimal adj = (t + (settings.tempOffset ?: 0))
                sendEvent(name:"temperature", value: (adj as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP), unit: "°C")
            }
            break
        case 19: // humidity (%)
            Long hv = tuyaGetValue(dpType, dpData)
            if (hv != null) {
                int h = ((hv as BigDecimal) + (settings.humOffset ?: 0)).toInteger()
                sendEvent(name:"humidity", value: h, unit:"%")
            }
            break
        default:
            if (logEnable) log.debug "Unhandled DP ${dp} type ${dpType} len ${dpLen}"
    }
}

/* ---------------------------- Tuya Helpers ------------------------------- */
private Long tuyaGetValue(int dpType, byte[] data) {
    // Tuya type 0x02 = 4-byte value, 0x00 = raw (use as big-endian), 0x04 = enum (1B)
    switch (dpType) {
        case 0x02: // value (4B)
            return bytesToUInt(data)
        case 0x00: // raw – attempt big-endian uint
            return bytesToUInt(data)
        case 0x04: // enum (1B)
            return (long)(data[0] & 0xFF)
        default:
            return bytesToUInt(data)
    }
}

private Integer tuyaGetEnum(int dpType, byte[] data) {
    if (dpType == 0x04 && data?.length >= 1) return (int)(data[0] & 0xFF)
    // fallback: treat as value
    Long v = tuyaGetValue(dpType, data)
    return v != null ? v.intValue() : null
}

private long bytesToUInt(byte[] data) {
    long v = 0
    data?.each { b -> v = (v << 8) | (b & 0xFF) }
    return v
}

private String bytesToHex(byte[] data) {
    data?.collect { String.format("%02X", it) }?.join("") ?: ""
}

private String hex(int val) { String.format("0x%02X", val & 0xFF) }

/* ------------------------------ Commands -------------------------------- */

def refresh() {
    if (txtEnable) log.info "Refresh requested"
    // Tuya devices usually report periodically; explicit polling often not supported.
    // Optionally, you could re-send time sync to encourage reports.
}

/**
 * setAlarmRingtone("melody_1"|"melody_2"|"OFF")
 */
void setAlarmRingtone(String ringtone) {
    Map map = ["melody_1":0, "melody_2":1, "OFF":2]
    Integer v = map[ringtone]
    if (v == null) {
        log.warn "Invalid ringtone '${ringtone}'. Valid: ${map.keySet()}"
        return
    }
    List<hubitat.device.HubAction> cmds = sendTuyaEnum(5, v as int)
    if (txtEnable) log.info "Setting alarm ringtone to ${ringtone} (${v})"
    sendHubCommand(cmds)
}

/**
 * setBacklightMode(1..3)
 */
void setBacklightMode(Number level) {
    int v = (level as int)
    v = Math.max(1, Math.min(3, v))
    List<hubitat.device.HubAction> cmds = sendTuyaValue(17, v)
    if (txtEnable) log.info "Setting backlight mode to ${v}"
    sendHubCommand(cmds)
}

/* --------------------------- Tuya Write Frames --------------------------- */
private List<hubitat.device.HubAction> sendTuyaEnum(int dp, int value) {
    return [new hubitat.device.HubAction(zigbee.command(0xEF00, 0x02, [:], tuyaPacket(dp, 0x04, [(byte)(value & 0xFF)] as byte[])), hubitat.device.Protocol.ZIGBEE)]
}

private List<hubitat.device.HubAction> sendTuyaValue(int dp, int value) {
    byte[] payload = new byte[4]
    payload[0] = (byte)((value >> 24) & 0xFF)
    payload[1] = (byte)((value >> 16) & 0xFF)
    payload[2] = (byte)((value >> 8) & 0xFF)
    payload[3] = (byte)(value & 0xFF)
    return [new hubitat.device.HubAction(zigbee.command(0xEF00, 0x02, [:], tuyaPacket(dp, 0x02, payload)), hubitat.device.Protocol.ZIGBEE)]
}

private Map createTuyaTimePayload() {
    // Command 0x24 (set time) – payload: [DP(2B)=0x0101][TYPE=0x00][LEN=0x0008][UTC 4B][TZ 4B]
    long epoch = now() / 1000L
    int tz = (TimeZone.getDefault().getRawOffset() / 1000) // seconds offset (approx; ignores DST)
    byte[] utc = intTo4B(epoch as int)
    byte[] tzb = intTo4B(tz as int)
    byte[] dp = [(byte)0x01, (byte)0x01] as byte[]
    byte type = 0x00
    byte[] len = [(byte)0x00,(byte)0x08] as byte[]
    byte[] payload = concatBytes(concatBytes(concatBytes(dp, [type] as byte[]), len), concatBytes(utc, tzb))
    return [payload: payload]
}

private Map tuyaPacket(int dp, int type, byte[] value) {
    byte[] dpb = [(byte)((dp >> 8) & 0xFF), (byte)(dp & 0xFF)] as byte[]
    byte[] typ = [(byte)(type & 0xFF)] as byte[]
    byte[] len = [(byte)((value.length >> 8) & 0xFF), (byte)(value.length & 0xFF)] as byte[]
    byte[] payload = concatBytes(concatBytes(concatBytes(dpb, typ), len), value)
    return [payload: payload]
}

private byte[] intTo4B(int v) {
    return [(byte)((v>>24)&0xFF),(byte)((v>>16)&0xFF),(byte)((v>>8)&0xFF),(byte)(v&0xFF)] as byte[]
}

private byte[] concatBytes(byte[] a, byte[] b) { ((a ?: [] as byte[]) + (b ?: [] as byte[])) as byte[] }

private byte[] subBytes(byte[] src, int start, int len) {
    if (!src) return [] as byte[]
    int s = Math.max(0, start)
    int e = Math.min(src.length, start + Math.max(0, len))
    if (s >= e) return [] as byte[]
    byte[] out = new byte[e - s]
    int j = 0
    for (int i = s; i < e; i++) { out[j++] = src[i] }
    return out
}
