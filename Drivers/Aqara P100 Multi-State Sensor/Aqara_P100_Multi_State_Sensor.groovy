/**
 *  Aqara P100 Multi-State Sensor driver for Hubitat
 *
 *  https://community.hubitat.com/t/PLACEHOLDER
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *  Credits:
 *      Hubitat, SmartThings, ZHA, Zigbee2MQTT, deCONZ and all other home automation communities for all the shared information.
 *      Dan Gibson (@absent42) - Zigbee2MQTT P100 external converter (https://github.com/absent42/Aqara-P100-Sensor)
 *
 * ver. 0.1.0 2026-04-18 kkossev  - initial version; dedicated P100 driver based on Aqara P1 Motion Sensor driver template
 * ver. 0.1.1 2026-04-19 kkossev  - updated inClusters list; bugfixes
 *
 *                                 TODO: refine fingerprint inClusters/outClusters after real device pairing data
 *                                 TODO: verify aqaraBlackMagic() init sequence with real hardware
 *                                 TODO: test mode switching (object <-> door_window) behavior
 *
 */

static String version() { "0.1.1" }
static String timeStamp() {"2026/04/19 7:52 AM"}

import hubitat.device.HubAction
import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.helper.HexUtils
import java.math.RoundingMode

@Field static final Boolean _DEBUG = false
@Field static final String COMMENT_WORKS_WITH = 'Works with Aqara P100 Multi-State Sensor (DWZTCGQ11LM)'
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60
@Field static final int CLUSTER_AQARA_FCC0 = 0xFCC0
@Field static final int MFG_AQARA = 0x115F
@Field static final int COMMAND_TIMEOUT = 10
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3
@Field static final Integer DEFAULT_POLLING_INTERVAL = 3600

// ===== P100 Attribute IDs on cluster 0xFCC0 =====
@Field static final int ATTR_DEVICE_MODE           = 0x0116    // UINT8:  door_window=3, object=5
@Field static final int ATTR_SENSITIVITY            = 0x010C    // UINT8:  1-10
@Field static final int ATTR_VIBRATION_DETECTION    = 0x0107    // BOOLEAN: 0=OFF, 1=ON
@Field static final int ATTR_FALL_DETECTION         = 0x01D8    // BOOLEAN: 0=OFF, 1=ON
@Field static final int ATTR_DOOR_WINDOW_TYPE       = 0x01EB    // UINT8:  casement=1, hopper=2, composite=3, hinged=4
@Field static final int ATTR_REPORT_INTERVAL        = 0x01EC    // UINT32: 5-300 seconds
@Field static final int ATTR_MOVEMENT_DETECTION     = 0x01ED    // BOOLEAN: 0=OFF, 1=ON
@Field static final int ATTR_DEVICE_POSTURE         = 0x01EE    // UINT8:  normal=1, abnormal=2
@Field static final int ATTR_TRIPLE_TAP_DETECTION   = 0x01EF    // BOOLEAN: 0=OFF, 1=ON
@Field static final int ATTR_ORIENTATION_DETECTION  = 0x01F0    // BOOLEAN: 0=OFF, 1=ON
@Field static final int ATTR_ORIENTATION            = 0x01F1    // UINT8:  face_up=1, face_down=2, vertical=3, tilt=4

// ===== Action event cluster 0x0101 (closuresDoorLock), attribute 0x0055 =====
@Field static final int CLUSTER_DOOR_LOCK           = 0x0101
@Field static final int ATTR_ACTION                 = 0x0055

// ===== Lookup maps =====
@Field static final Map<String, Integer> deviceModeMap = ["door_window": 3, "object": 5]
@Field static final Map<Integer, String> deviceModeReverseMap = [3: "door_window", 5: "object"]

@Field static final Map<String, Integer> doorWindowTypeMap = [
    "casement_window": 1, "hopper_window": 2, "composite_window": 3, "hinged_door": 4
]
@Field static final Map<Integer, String> doorWindowTypeReverseMap = [
    1: "casement_window", 2: "hopper_window", 3: "composite_window", 4: "hinged_door"
]

@Field static final Map<Integer, String> orientationMap = [
    1: "face_up", 2: "face_down", 3: "vertical", 4: "tilt"
]

@Field static final Map<Integer, String> actionMap = [
    0: "triple_tap", 1: "movement", 2: "vibration", 3: "orientation", 4: "fall"
]

@Field static final Map<Integer, String> devicePostureMap = [
    1: "normal", 2: "abnormal"
]

// ===== Virtual parameters (local-only, not sent to device) =====
@Field static final List<String> VIRTUAL_PARAMS = []

metadata {
    definition (name: "Aqara P100 Multi-State Sensor", namespace: "kkossev", author: "Krassimir Kossev", importUrl:
        "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20P100%20Multi-State%20Sensor/Aqara_P100_Multi_State_Sensor.groovy", singleThreaded: true ) {
        capability "Sensor"
        capability "ContactSensor"          // contact: open/closed (door_window mode)
        capability "AccelerationSensor"     // acceleration: active/inactive (vibration events)
        capability "Battery"
        capability "PowerSource"
        capability "Health Check"
        capability "Refresh"
        capability "PushableButton"         // triple-tap as button 1 push

        attribute '_status_', 'string'
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute 'batteryVoltage', 'string'
        attribute 'rtt', 'number'
        attribute 'deviceMode', 'enum', ['object', 'door_window']
        attribute 'orientation', 'enum', ['face_up', 'face_down', 'vertical', 'tilt']
        attribute 'lastAction', 'enum', ['triple_tap', 'movement', 'vibration', 'orientation', 'fall']
        attribute 'devicePosture', 'enum', ['normal', 'abnormal']

        command "configure", [[name: "Initialize the device after switching drivers. Will load device default values!" ]]
        command "ping",      [[name: "Check device online status and measure the Round-Trip Time (ms). May not work for battery-powered devices."]]
        command "refresh",   [[name: "Refreshes all parameters and states from the device.<br>Make sure to wake up the device to receive all updates.<br>Do not use frequently on battery-powered devices."]]

        if (_DEBUG) {
            command "test", [[name: "Test", type: "STRING", description: "Debug test command"]]
            command "initialize", [[name: "Manually initialize the device"]]
        }

        // Fingerprint from zigbee2mqtt issue #31503 (devId:0x0402, epList:[1,2])
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0006,000C,0101,FCC0", outClusters:"000A,0019", model:"lumi.vibration.agl002", manufacturer:"LUMI", deviceJoinName: "Aqara P100 Multi-State Sensor DWZTCGQ11LM"
    }

    preferences {
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "Show sensor activity in HE log page. Recommended value is <b>enabled</b>", defaultValue: true)
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "Debug information, useful for troubleshooting. Recommended value is <b>disabled</b>", defaultValue: true)
        if (device) {
            input (name: "deviceMode", type: "enum", title: "<b>Device Mode</b>", description: "Operating mode: <b>object</b> (movement/vibration/tilt/tap/fall) or <b>door_window</b> (contact open/close)", defaultValue: "object",
                options: ["object":"Object mode", "door_window":"Door/Window mode"])
            input (name: "motionSensitivity", type: "number", title: "<b>Detection Sensitivity</b>", description: "Detection sensitivity (1 = low, 10 = high)", range: "1..10", defaultValue: 5)
            input (name: "reportInterval", type: "number", title: "<b>Report Interval</b>", description: "How often the device reports state (5-300 seconds)", range: "5..300", defaultValue: 60)
            // Door/window mode preference
            if (settings?.deviceMode == "door_window") {
                input (name: "doorWindowType", type: "enum", title: "<b>Door/Window Type</b>", description: "Type of door or window being monitored", defaultValue: "hinged_door",
                    options: ["casement_window":"Casement Window", "hopper_window":"Hopper Window", "composite_window":"Composite Window", "hinged_door":"Hinged Door"])
            }
            // Object mode preferences
            if (settings?.deviceMode == null || settings?.deviceMode == "object") {
                input (name: "movementDetection", type: "bool", title: "<b>Movement Detection</b>", description: "Enable movement event detection", defaultValue: true)
                input (name: "vibrationDetection", type: "bool", title: "<b>Vibration Detection</b>", description: "Enable vibration event detection", defaultValue: true)
                input (name: "orientationDetection", type: "bool", title: "<b>Orientation Detection</b>", description: "Enable orientation event detection", defaultValue: true)
                input (name: "fallDetection", type: "bool", title: "<b>Fall Detection</b>", description: "Enable fall event detection", defaultValue: true)
                input (name: "tripleTapDetection", type: "bool", title: "<b>Triple-Tap Detection</b>", description: "Enable triple-tap event detection", defaultValue: true)
            }
        }
    }
}

// ==============================================================================================
// PARAMETER STORAGE AND CHANGE DETECTION
// ==============================================================================================

Boolean isVirtualParam(String paramName) {
    return paramName in VIRTUAL_PARAMS
}

void storeParamValue(String paramName, Object value, String type, Boolean isLocal = false) {
    if (state.params == null) { state.params = [] }
    def existing = state.params.find { it.n == paramName }
    if (existing?.v == value && existing?.t == type && existing?.l == isLocal) { return }
    state.params.removeAll { it.n == paramName }
    state.params << [n: paramName, t: type, v: value, l: isLocal]
    if (logEnable) { log.debug "${device.displayName} stored parameter: ${paramName} = ${value} (${type})${isLocal ? ' [local]' : ''}" }
}

Object getStoredParamValue(String paramName) {
    if (state.params == null) { return null }
    def param = state.params.find { it.n == paramName }
    return param?.v
}

Boolean hasParamChanged(String paramName, Object newValue) {
    def storedValue = getStoredParamValue(paramName)
    if (storedValue == null) { return newValue != null }
    if (newValue == null) { return false }
    def normalizedNew = normalizeParamValue(newValue)
    def normalizedStored = normalizeParamValue(storedValue)
    return normalizedNew != normalizedStored
}

private Object normalizeParamValue(Object value) {
    if (value == null) return null
    if (value instanceof String) {
        if (value.isInteger()) { return value.toInteger() }
        if (value.isDouble()) { return value.toDouble() }
        if (value.toLowerCase() in ['true', 'false']) { return value.toLowerCase() == 'true' }
    }
    return value
}

void initializeParamStorage() {
    if (state.params == null) {
        state.params = []
        logDebug "Initialized parameter storage"
    }
    if (state.driverVersion != driverVersionAndTimeStamp()) {
        logInfo "Driver version changed from ${state.driverVersion} to ${driverVersionAndTimeStamp()}"
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

void clearParamStorage() {
    state.params = []
    logInfo "Cleared all stored parameters"
}

// ==============================================================================================
// HELPER: get current device mode from state/settings
// ==============================================================================================

String getDeviceMode() {
    // Check state first (from device report), fall back to setting
    if (device?.currentValue('deviceMode') != null) {
        return device.currentValue('deviceMode')
    }
    return settings?.deviceMode ?: "object"
}

// ==============================================================================================
// PARSE
// ==============================================================================================

void parse(String description) {
    checkDriverVersion()
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1 ; else state.rxCounter = 1
    setHealthStatusOnline()

    def descMap = [:]

    // Aqara legacy TLV battery reports (cluster 0x0000)
    if (description.contains("cluster: 0000")) {
        if (description.contains("attrId: FF01")) {
            parseAqaraAttributeFF01(description)
            return
        }
        else if (description.contains("attrId: FF02")) {
            parseAqaraAttributeFF02(description)
            return
        }
    }

    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch (e) {
        logWarn "parse: exception ${e} caught while parsing description: ${description}"
        return
    }

    if (logEnable) { log.debug "${device.displayName} parse: descMap: {$descMap}" }

    if (descMap.attrId != null) {
        // Attribute report
        List attrData = [[cluster: descMap.cluster, attrId: descMap.attrId, value: descMap.value, status: descMap.status]]
        descMap.additionalAttrs.each {
            attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status]
        }
        attrData.each {
            if (it.status == "86") {
                logWarn "unsupported cluster ${it.cluster} attribute ${it.attrId}"
            }
            // ---- Cluster 0x0006: genOnOff → contact sensor ----
            else if (it.cluster == "0006" && it.attrId == "0000") {
                parseContactEvent(Integer.parseInt(it.value, 16))
            }
            // ---- Cluster 0x0101: closuresDoorLock, attr 0x0055 → action events ----
            else if (it.cluster == "0101" && it.attrId == "0055") {
                parseActionEvent(Integer.parseInt(it.value, 16))
            }
            // ---- Cluster 0x0001: battery voltage ----
            else if (it.cluster == "0001" && it.attrId == "0020") {
                if (it.value != "00") {
                    voltageAndBatteryEvents(Integer.parseInt(it.value, 16) / 10.0)
                }
            }
            // ---- Cluster 0x0000: basic cluster ----
            else if (it.cluster == "0000" && it.attrId == "0001") {
                sendRttEvent()
            }
            else if (it.cluster == "0000" && it.attrId == "0004") {
                logInfo "(parse) device model is ${it.value}"
            }
            else if (it.cluster == "0000" && it.attrId == "0005") {
                logDebug "(parse attr 5) device ${it.value} button was pressed"
                sendInfoEvent("Button was pressed. The device will stay awake for 15 minutes")
            }
            // ---- Cluster FCC0: Aqara manufacturer-specific ----
            else if (descMap.cluster == "FCC0") {
                parseAqaraClusterFCC0(description, descMap, it)
            }
            // ---- Cluster 0x0000 legacy Aqara TLV ----
            else if (descMap.cluster == "0000" && it.attrId == "FF01") {
                parseAqaraAttributeFF01(description)
            }
            else {
                if (logEnable) log.debug "${device.displayName} Unprocessed attribute report: cluster=${it.cluster} attrId=${it.attrId} value=${it.value} status=${it.status} data=${descMap.data}"
            }
        }
    }
    else if (descMap.profileId == "0000") {
        parseZDOcommand(descMap)
    }
    else if (descMap.clusterId != null && descMap.profileId == "0104") {
        parseZHAcommand(descMap)
    }
    else {
        logWarn "Unprocessed unknown command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}

// ==============================================================================================
// PARSE: Aqara Cluster FCC0
// ==============================================================================================

void parseAqaraClusterFCC0(String description, Map descMap, Map it) {
    String valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    int value = safeToInt(it.value)

    switch (it.attrId) {
        case "0005":
            logDebug "(parseAqaraClusterFCC0) device ${it.value} button was pressed (driver version ${driverVersionAndTimeStamp()})"
            sendInfoEvent("Button was pressed. The device will stay awake for 15 minutes")
            break

        case "00F7":    // Aqara TLV structure (battery, etc.)
            decodeAqaraStruct(description)
            break

        case "00FC":    // LUMI leave report
            log.warn "${device.displayName} received LUMI LEAVE report: (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break

        // ===== P100 Settings =====

        case "0116":    // device_mode: door_window=3, object=5
            def modeName = deviceModeReverseMap[value]
            if (modeName) {
                sendEvent(name: "deviceMode", value: modeName, type: "physical", descriptionText: "${device.displayName} device mode is ${modeName}")
                device.updateSetting("deviceMode", [value: modeName, type: "enum"])
                storeParamValue('deviceMode', modeName, 'enum', false)
                logInfo "device mode: ${modeName}"
            } else {
                logWarn "unknown device_mode value: ${value}"
            }
            break

        case "010C":    // sensitivity (1-10)
            value = Integer.parseInt(it.value, 16)
            device.updateSetting("motionSensitivity", [value: value.toString(), type: "number"])
            storeParamValue('motionSensitivity', value, 'number', false)
            logDebug "sensitivity: ${value}"
            break

        case "0107":    // vibration_detection (boolean)
            def enabled = (value != 0)
            device.updateSetting("vibrationDetection", [value: enabled, type: "bool"])
            storeParamValue('vibrationDetection', enabled, 'bool', false)
            logDebug "vibration detection: ${enabled ? 'ON' : 'OFF'}"
            break

        case "01D8":    // fall_detection (boolean)
            def enabled = (value != 0)
            device.updateSetting("fallDetection", [value: enabled, type: "bool"])
            storeParamValue('fallDetection', enabled, 'bool', false)
            logDebug "fall detection: ${enabled ? 'ON' : 'OFF'}"
            break

        case "01EB":    // door_window_type
            def typeName = doorWindowTypeReverseMap[value]
            if (typeName) {
                device.updateSetting("doorWindowType", [value: typeName, type: "enum"])
                storeParamValue('doorWindowType', typeName, 'enum', false)
                logDebug "door/window type: ${typeName}"
            } else {
                logWarn "unknown door_window_type value: ${value}"
            }
            break

        case "01EC":    // report_interval (UINT32, seconds)
            value = Integer.parseInt(it.value, 16)
            device.updateSetting("reportInterval", [value: value.toString(), type: "number"])
            storeParamValue('reportInterval', value, 'number', false)
            logDebug "report interval: ${value} seconds"
            break

        case "01ED":    // movement_detection (boolean)
            def enabled = (value != 0)
            device.updateSetting("movementDetection", [value: enabled, type: "bool"])
            storeParamValue('movementDetection', enabled, 'bool', false)
            logDebug "movement detection: ${enabled ? 'ON' : 'OFF'}"
            break

        case "01EE":    // device_posture: normal=1, abnormal=2
            def posture = devicePostureMap[value]
            if (posture) {
                sendEvent(name: "devicePosture", value: posture, type: "physical", descriptionText: "${device.displayName} device posture is ${posture}")
                logInfo "device posture: ${posture}"
                if (posture == "abnormal") {
                    sendInfoEvent("Device posture is abnormal — sensor may be incorrectly installed or needs calibration")
                }
            } else {
                logWarn "unknown device_posture value: ${value}"
            }
            break

        case "01EF":    // triple_tap_detection (boolean)
            def enabled = (value != 0)
            device.updateSetting("tripleTapDetection", [value: enabled, type: "bool"])
            storeParamValue('tripleTapDetection', enabled, 'bool', false)
            logDebug "triple-tap detection: ${enabled ? 'ON' : 'OFF'}"
            break

        case "01F0":    // orientation_detection (boolean)
            def enabled = (value != 0)
            device.updateSetting("orientationDetection", [value: enabled, type: "bool"])
            storeParamValue('orientationDetection', enabled, 'bool', false)
            logDebug "orientation detection: ${enabled ? 'ON' : 'OFF'}"
            break

        case "01F1":    // orientation: face_up=1, face_down=2, vertical=3, tilt=4
            def orientName = orientationMap[value]
            if (orientName) {
                sendEvent(name: "orientation", value: orientName, type: "physical", descriptionText: "${device.displayName} orientation is ${orientName}")
                logInfo "orientation: ${orientName}"
            } else {
                logWarn "unknown orientation value: ${value}"
            }
            break

        default:
            logDebug "Unprocessed FCC0 attribute report: cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value} status=${it.status} data=${descMap.data}"
            break
    }
}

// ==============================================================================================
// PARSE: Contact Event (cluster 0x0006, genOnOff)
// ==============================================================================================

void parseContactEvent(int onOffValue) {
    // P100 uses inverted logic: onOff=0 means closed, onOff=1 means open
    if (getDeviceMode() != "door_window") {
        logDebug "ignored contact event in object mode (onOff=${onOffValue})"
        return
    }
    String contactState = (onOffValue == 0) ? "closed" : "open"
    String descText = "contact is ${contactState}"
    logInfo descText
    sendEvent(name: "contact", value: contactState, type: "physical", descriptionText: "${device.displayName} ${descText}")
}

// ==============================================================================================
// PARSE: Action Event (cluster 0x0101, closuresDoorLock, attr 0x0055)
// ==============================================================================================

void parseActionEvent(int actionValue) {
    String actionName = actionMap[actionValue]
    if (actionName == null) {
        logWarn "unknown action event value: ${actionValue}"
        return
    }

    // Only process action events in object mode
    if (getDeviceMode() != "object") {
        logDebug "ignored action event '${actionName}' in door_window mode"
        return
    }

    logInfo "action: ${actionName}"
    sendEvent(name: "lastAction", value: actionName, type: "physical", descriptionText: "${device.displayName} action: ${actionName}", isStateChange: true)

    switch (actionName) {
        case "triple_tap":
            // Fire PushableButton event (button 1)
            sendEvent(name: "pushed", value: 1, type: "physical", descriptionText: "${device.displayName} button 1 was pushed (triple-tap)", isStateChange: true)
            logInfo "button 1 pushed (triple-tap)"
            break
        case "vibration":
            // Set acceleration active, schedule reset
            sendEvent(name: "acceleration", value: "active", type: "physical", descriptionText: "${device.displayName} acceleration active (vibration)")
            runIn(3, "resetAcceleration", [overwrite: true])
            break
        case "movement":
        case "fall":
            // Also trigger acceleration briefly for movement/fall
            sendEvent(name: "acceleration", value: "active", type: "physical", descriptionText: "${device.displayName} acceleration active (${actionName})")
            runIn(3, "resetAcceleration", [overwrite: true])
            break
        case "orientation":
            // Orientation change — no acceleration trigger, just the lastAction and orientation attribute update (handled by attr 0x01F1)
            break
    }
}

void resetAcceleration() {
    sendEvent(name: "acceleration", value: "inactive", type: "digital", descriptionText: "${device.displayName} acceleration inactive (reset)")
    if (txtEnable) log.info "${device.displayName} acceleration inactive (reset)"
}

// ==============================================================================================
// PARSE: Aqara TLV structure (attr 0x00F7)
// ==============================================================================================

def decodeAqaraStruct(String description) {
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    def MsgLength = valueHex.size()

    if (logEnable) log.debug "${device.displayName} decodeAqaraStruct 00F7 : len = ${MsgLength} valueHex = ${valueHex}"
    for (int i = 2; i < (MsgLength-3); ) {
        def dataType = Integer.parseInt(valueHex[(i+2)..(i+3)], 16)
        def tag = Integer.parseInt(valueHex[(i+0)..(i+1)], 16)
        def rawValue = 0

        switch (dataType) {
            case 0x08:  // 8 bit data
            case 0x10:  // 1 byte boolean
            case 0x18:  // 8-bit bitmap
            case 0x20:  // 1 byte unsigned int
            case 0x28:  // 1 byte signed int
            case 0x30:  // 8-bit enumeration
                rawValue = Integer.parseInt(valueHex[(i+4)..(i+5)], 16)
                switch (tag) {
                    case 0x03:      // device temperature
                        logDebug "tag 0x03: device temperature is ${rawValue} °C"
                        break
                    case 0x18:      // battery percentage
                        sendBatteryEvent(rawValue)
                        logDebug "tag 0x18: battery percentage is ${rawValue}%"
                        break
                    case 0x64:      // on/off (presence-related)
                        logDebug "tag 0x64: on/off is ${rawValue}"
                        break
                    case 0x9b:      // consumer connected
                        logDebug "tag 0x9b: consumer connected is ${rawValue}"
                        break
                    default:
                        logDebug "unknown tag=0x${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                        break
                }
                i = i + (1 + 1 + 1) * 2
                break

            case 0x21:  // 2 bytes 16bit UINT
                rawValue = Integer.parseInt((valueHex[(i+6)..(i+7)] + valueHex[(i+4)..(i+5)]), 16)
                switch (tag) {
                    case 0x01:      // battery level (mV)
                        logDebug "tag 0x01: battery level is ${rawValue} mV"
                        voltageAndBatteryEvents(rawValue / 1000)
                        break
                    case 0x05:      // RSSI
                        logDebug "tag 0x05: RSSI is ${rawValue}"
                        break
                    case 0x0A:      // Parent NWK
                        logDebug "tag 0x0A: Parent NWK is ${valueHex[(i+6)..(i+7)] + valueHex[(i+4)..(i+5)]}"
                        if (state.health == null) { state.health = [:] }
                        String nwk = intToHexStr(rawValue as Integer, 2)
                        String oldNWK = state.health['parentNWK'] ?: 'n/a'
                        if (oldNWK != nwk) {
                            state.health['parentNWK'] = nwk
                            state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1
                            logWarn "parentNWK changed from ${oldNWK} to ${nwk}"
                        }
                        break
                    case 0x0B:      // light level
                        logDebug "tag 0x0B: lightlevel is ${rawValue}"
                        break
                    case 0x17:      // battery voltage in mV
                        def voltage = rawValue / 1000.0
                        sendVoltageEvent(voltage)
                        logDebug "tag 0x17: battery voltage is ${voltage}V (${rawValue} mV)"
                        break
                    default:
                        logDebug "unknown tag=0x${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                        break
                }
                i = i + (1 + 1 + 2) * 2
                break

            case 0x0B:  // 32-bit data
            case 0x1B:  // 32-bit bitmap
            case 0x23:  // Unsigned 32-bit integer
            case 0x2B:  // Signed 32-bit integer
                switch (tag) {
                    case 0x0D:      // firmware version
                        rawValue = Integer.parseInt((valueHex[(i+10)..(i+11)] + valueHex[(i+8)..(i+9)] + valueHex[(i+6)..(i+7)] + valueHex[(i+4)..(i+5)]), 16)
                        def major = (rawValue >> 24) & 0xFF
                        def minor = (rawValue >> 16) & 0xFF
                        def patch = rawValue & 0xFFFF
                        String firmwareVersion = "${major}.${minor}.${patch}"
                        logDebug "tag 0x0D: firmware version is ${firmwareVersion} (raw=${rawValue})"
                        break
                    default:
                        rawValue = Integer.parseInt((valueHex[(i+10)..(i+11)] + valueHex[(i+8)..(i+9)] + valueHex[(i+6)..(i+7)] + valueHex[(i+4)..(i+5)]), 16)
                        logDebug "unknown tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                        break
                }
                i = i + (1 + 1 + 4) * 2
                break

            case 0x24:  // 5 bytes 40 bits Zcl40BitUint
                switch (tag) {
                    case 0x06:
                        logDebug "tag 0x06: device LQI is ${valueHex[(i+4)..(i+14)]}"
                        break
                    default:
                        logDebug "unknown tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} TODO rawValue"
                        break
                }
                i = i + (1 + 1 + 5) * 2
                break

            case 0x0C:  // 40-bit data
            case 0x1C:  // 40-bit bitmap
            case 0x2C:  // Signed 40-bit integer
                logDebug "unknown 40 bit data tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]}"
                i = i + (1 + 1 + 5) * 2
                break

            case 0x0D:  // 48-bit data
            case 0x1D:  // 48-bit bitmap
            case 0x25:  // Unsigned 48-bit integer
            case 0x2D:  // Signed 48-bit integer
                logDebug "unknown 48 bit data tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]}"
                i = i + (1 + 1 + 6) * 2
                break

            default:
                logWarn "unknown dataType 0x${valueHex[(i+2)..(i+3)]} at index ${i}"
                i = i + 1*2
                break
        }
    }
}

// ==============================================================================================
// PARSE: Legacy Aqara Battery TLV (FF01 / FF02)
// ==============================================================================================

void parseAqaraAttributeFF01(String description) {
    logDebug "(parseAqaraAttributeFF01) description: ${description}"
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    parseBatteryFF01(valueHex)
}

void parseAqaraAttributeFF02(String description) {
    logDebug "(parseAqaraAttributeFF02) description: ${description}"
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    parseBatteryFF02(valueHex)
}

private parseBatteryFF01(String valueHex) {
    if (!valueHex) return
    if (!(valueHex ==~ /(?i)^[0-9a-f]+$/)) {
        StringBuilder sb = new StringBuilder(valueHex.length() * 2)
        for (char ch : valueHex.toCharArray()) {
            sb.append(String.format("%02X", ((int) ch) & 0xFF))
        }
        valueHex = sb.toString()
    }
    Integer rawmV = null
    int L = valueHex.length()
    for (int i = 0; i <= L - 8; i += 2) {
        String key  = valueHex.substring(i, i+2)
        String type = valueHex.substring(i+2, i+4)
        if (key.equalsIgnoreCase("01") && type.equalsIgnoreCase("21")) {
            String lo = valueHex.substring(i+4, i+6)
            String hi = valueHex.substring(i+6, i+8)
            rawmV = Integer.parseInt(hi + lo, 16)
            break
        }
    }
    if (rawmV == null || rawmV <= 0) return
    BigDecimal volts = rawmV / 1000.0G
    voltageAndBatteryEvents(volts)
}

private parseBatteryFF02(String valueHex) {
    if (!valueHex) return
    if (!(valueHex ==~ /(?i)^[0-9a-f]+$/)) {
        StringBuilder sb = new StringBuilder(valueHex.length() * 2)
        for (char ch : valueHex.toCharArray()) {
            sb.append(String.format("%02X", ((int) ch) & 0xFF))
        }
        valueHex = sb.toString()
    }
    Integer rawmV = null
    int L = valueHex.length()
    for (int i = 0; i <= L - 8; i += 2) {
        String key  = valueHex.substring(i, i+2)
        String type = valueHex.substring(i+2, i+4)
        if (key.equalsIgnoreCase("01") && type.equalsIgnoreCase("21")) {
            String lo = valueHex.substring(i+4, i+6)
            String hi = valueHex.substring(i+6, i+8)
            rawmV = Integer.parseInt(hi + lo, 16)
            break
        }
    }
    if (rawmV == null || rawmV <= 0) return
    BigDecimal volts = rawmV / 1000.0G
    voltageAndBatteryEvents(volts)
}

// ==============================================================================================
// BATTERY EVENTS
// ==============================================================================================

def voltageAndBatteryEvents(rawVolts, isDigital=false) {
    def minVolts = 2.5
    def maxVolts = 3.0
    def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
    def roundedPct = Math.min(100, Math.max(0, Math.round(pct * 100)))
    def descText  = "Battery level is ${roundedPct}%"
    def descText2 = "Battery voltage is ${rawVolts}V"
    if (txtEnable) log.info "${device.displayName} ${descText}"
    sendEvent(name: 'batteryVoltage', value: rawVolts, unit: "V", type: "physical", descriptionText: descText2, isStateChange: true)
    sendEvent(name: 'battery', value: roundedPct, unit: "%", type: isDigital == true ? "digital" : "physical", descriptionText: descText, isStateChange: true)
}

def sendVoltageEvent(rawVolts) {
    def descText = "Battery voltage is ${rawVolts}V"
    if (txtEnable) log.info "${device.displayName} ${descText}"
    sendEvent(name: 'batteryVoltage', value: rawVolts, unit: "V", type: "physical", descriptionText: descText, isStateChange: true)
}

def sendBatteryEvent(roundedPct, isDigital=false) {
    def descText = "Battery level is ${roundedPct}%"
    if (txtEnable) log.info "${device.displayName} ${descText}"
    sendEvent(name: 'battery', value: roundedPct, unit: "%", type: isDigital == true ? "digital" : "physical", descriptionText: descText, isStateChange: true)
}

// ==============================================================================================
// PARSE: ZDO Commands
// ==============================================================================================

def parseZDOcommand(Map descMap) {
    switch (descMap.clusterId) {
        case "0006":
            if (logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7]+descMap.data[6]})"
            break
        case "0013":    // device announcement
            if (logEnable) log.info "${device.displayName} Received device announcement"
            aqaraBlackMagic()
            break
        case "8004":    // simple descriptor response
            if (logEnable) log.info "${device.displayName} Received simple descriptor response, data=${descMap.data}"
            parseSimpleDescriptorResponse(descMap)
            break
        case "8005":    // endpoint response
            if (logEnable) log.info "${device.displayName} Received endpoint response: endpointCount=${descMap.data[4]} endpointList=${descMap.data[5]}"
            break
        case "8021":    // bind response
            if (logEnable) log.info "${device.displayName} Received bind response, Status: ${descMap.data[1]=="00" ? 'Success' : 'Failure'}"
            break
        case "8022":    // unbind response
            if (logEnable) log.info "${device.displayName} Received unbind response, Status: ${descMap.data[1]=="00" ? 'Success' : 'Failure'}"
            break
        case "8034":    // leave response
            if (logEnable) log.info "${device.displayName} Received leave response"
            break
        case "8038":    // Management Network Update Notify
            if (logEnable) log.info "${device.displayName} Received Management Network Update Notify"
            break
        default:
            if (logEnable) log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} data=${descMap.data}"
    }
}

// ==============================================================================================
// PARSE: ZHA Commands
// ==============================================================================================

def parseZHAcommand(Map descMap) {
    switch (descMap.command) {
        case "01":  // read attribute response
            if (descMap?.data?.size() < 3) {
                logDebug "received Read attribute response: cluster ${descMap.clusterId}, data size ${descMap?.data?.size()}"
                return
            }
            def status = descMap.data[2]
            def attrId = descMap.data[1] + descMap.data[0]
            if (status == "86") {
                logWarn "UNSUPPORTED Read attribute response: cluster ${descMap.clusterId} Attribute ${attrId} status code ${status}"
            } else {
                logDebug "Read attribute response: cluster ${descMap.clusterId} Attribute ${attrId} status code ${status}"
            }
            break
        case "04":  // write attribute response
            logDebug "Received Write Attribute Response for cluster:${descMap.clusterId}, Status: ${descMap.data[0]=="00" ? 'Success' : 'Failure'}"
            break
        case "07":  // Configure Reporting Response
            logInfo "Received Configure Reporting Response for cluster:${descMap.clusterId}, Status: ${descMap.data[0]=="00" ? 'Success' : 'Failure'}"
            break
        case "09":  // Read Reporting Configuration Response
            def status = zigbee.convertHexToInt(descMap.data[0])
            if (status == 0) {
                def min = zigbee.convertHexToInt(descMap.data[6])*256 + zigbee.convertHexToInt(descMap.data[5])
                def max = zigbee.convertHexToInt(descMap.data[8])*256 + zigbee.convertHexToInt(descMap.data[7])
                logInfo "Reporting Configuration Response: cluster:${descMap.clusterId} min=${min} max=${max}"
            } else {
                logWarn "Reporting Configuration Response failed: cluster:${descMap.clusterId} status=${status}"
            }
            break
        case "0B":  // ZCL Default Response
            def status = descMap.data[1]
            if (status != "00") {
                logDebug "Received ZCL Default Response: cluster:${descMap.clusterId} command=${descMap.data[0]} Status: Failure"
            }
            break
        default:
            logDebug "Unprocessed global command: cluster=${descMap.clusterId} command=${descMap.command} data=${descMap.data}"
            break
    }
}

def parseSimpleDescriptorResponse(Map descMap) {
    log.info "${device.displayName} Received simple descriptor response, data=${descMap.data}"
    def inputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[11])
    def inputClusterList = ""
    for (int i in 1..inputClusterCount) {
        inputClusterList += descMap.data[13+(i-1)*2] + descMap.data[12+(i-1)*2] + ","
    }
    if (inputClusterList.length() > 0) {
        inputClusterList = inputClusterList.substring(0, inputClusterList.length() - 1)
    }
    log.info "${device.displayName} Input Cluster Count: ${inputClusterCount} Input Cluster List: ${inputClusterList}"
    if (getDataValue("inClusters") != inputClusterList) {
        logWarn "inClusters=${getDataValue('inClusters')} differs from inputClusterList:${inputClusterList} - will be updated!"
        updateDataValue("inClusters", inputClusterList)
    }

    def outputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[12+inputClusterCount*2])
    def outputClusterList = ""
    for (int i in 1..outputClusterCount) {
        outputClusterList += descMap.data[14+inputClusterCount*2+(i-1)*2] + descMap.data[13+inputClusterCount*2+(i-1)*2] + ","
    }
    if (outputClusterList.length() > 0) {
        outputClusterList = outputClusterList.substring(0, outputClusterList.length() - 1)
    }
    log.info "${device.displayName} Output Cluster Count: ${outputClusterCount} Output Cluster List: ${outputClusterList}"
    if (getDataValue("outClusters") != outputClusterList) {
        logWarn "outClusters=${getDataValue('outClusters')} differs from outputClusterList:${outputClusterList} - will be updated!"
        updateDataValue("outClusters", outputClusterList)
    }
}

// ==============================================================================================
// CONFIGURATION: updated / configure / initialize
// ==============================================================================================

void updated() {
    logDebug "updated()..."
    checkDriverVersion()
    ArrayList<String> cmds = []

    if (txtEnable) log.info "${device.displayName} Updating ${device.getName()} model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} (driver version ${driverVersionAndTimeStamp()})"
    if (txtEnable) log.info "${device.displayName} Debug logging is ${logEnable}; Description text logging is ${txtEnable}"
    if (logEnable == true) {
        runIn(86400, "logsOff", [overwrite: true, misfire: "ignore"])
        logInfo "Debug logging will be turned off after 24 hours"
    } else {
        unschedule(logsOff)
    }

    // Restart health check timer
    runIn(DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])

    // ===== P100 Settings =====

    // Device mode
    if (hasParamChanged('deviceMode', settings?.deviceMode)) {
        def modeValue = deviceModeMap[settings.deviceMode]
        if (modeValue != null) {
            logDebug "setting deviceMode to ${settings.deviceMode} (${modeValue})"
            cmds += zigbee.writeAttribute(0xFCC0, ATTR_DEVICE_MODE, DataType.UINT8, modeValue, [mfgCode: MFG_AQARA], delay=200)
        }
    }

    // Sensitivity
    if (hasParamChanged('motionSensitivity', settings?.motionSensitivity)) {
        def val = safeToInt(settings.motionSensitivity)
        if (val >= 1 && val <= 10) {
            logDebug "setting sensitivity to ${val}"
            cmds += zigbee.writeAttribute(0xFCC0, ATTR_SENSITIVITY, DataType.UINT8, val, [mfgCode: MFG_AQARA], delay=200)
        }
    }

    // Report interval
    if (hasParamChanged('reportInterval', settings?.reportInterval)) {
        def val = safeToInt(settings.reportInterval)
        if (val >= 5 && val <= 300) {
            logDebug "setting reportInterval to ${val} seconds"
            cmds += zigbee.writeAttribute(0xFCC0, ATTR_REPORT_INTERVAL, DataType.UINT32, val, [mfgCode: MFG_AQARA], delay=200)
        }
    }

    // Door/window type (only in door_window mode)
    if (settings?.deviceMode == "door_window" && hasParamChanged('doorWindowType', settings?.doorWindowType)) {
        def typeValue = doorWindowTypeMap[settings.doorWindowType]
        if (typeValue != null) {
            logDebug "setting doorWindowType to ${settings.doorWindowType} (${typeValue})"
            cmds += zigbee.writeAttribute(0xFCC0, ATTR_DOOR_WINDOW_TYPE, DataType.UINT8, typeValue, [mfgCode: MFG_AQARA], delay=200)
        }
    }

    // Object mode detection toggles
    if (settings?.deviceMode == "object") {
        if (hasParamChanged('movementDetection', settings?.movementDetection)) {
            logDebug "setting movementDetection to ${settings.movementDetection}"
            cmds += zigbee.writeAttribute(0xFCC0, ATTR_MOVEMENT_DETECTION, DataType.BOOLEAN, settings.movementDetection ? 1 : 0, [mfgCode: MFG_AQARA], delay=200)
        }
        if (hasParamChanged('vibrationDetection', settings?.vibrationDetection)) {
            logDebug "setting vibrationDetection to ${settings.vibrationDetection}"
            cmds += zigbee.writeAttribute(0xFCC0, ATTR_VIBRATION_DETECTION, DataType.BOOLEAN, settings.vibrationDetection ? 1 : 0, [mfgCode: MFG_AQARA], delay=200)
        }
        if (hasParamChanged('orientationDetection', settings?.orientationDetection)) {
            logDebug "setting orientationDetection to ${settings.orientationDetection}"
            cmds += zigbee.writeAttribute(0xFCC0, ATTR_ORIENTATION_DETECTION, DataType.BOOLEAN, settings.orientationDetection ? 1 : 0, [mfgCode: MFG_AQARA], delay=200)
        }
        if (hasParamChanged('fallDetection', settings?.fallDetection)) {
            logDebug "setting fallDetection to ${settings.fallDetection}"
            cmds += zigbee.writeAttribute(0xFCC0, ATTR_FALL_DETECTION, DataType.BOOLEAN, settings.fallDetection ? 1 : 0, [mfgCode: MFG_AQARA], delay=200)
        }
        if (hasParamChanged('tripleTapDetection', settings?.tripleTapDetection)) {
            logDebug "setting tripleTapDetection to ${settings.tripleTapDetection}"
            cmds += zigbee.writeAttribute(0xFCC0, ATTR_TRIPLE_TAP_DETECTION, DataType.BOOLEAN, settings.tripleTapDetection ? 1 : 0, [mfgCode: MFG_AQARA], delay=200)
        }
    }

    if (cmds != null && cmds != []) {
        sendZigbeeCommands(cmds)
    } else {
        logInfo "no preferences were changed that require configuration commands to be sent."
    }
}

void refresh() {
    logInfo 'refresh...'
    List<String> cmds = []
    // Read all P100-specific attributes from cluster FCC0
    cmds += zigbee.readAttribute(0xFCC0, [ATTR_DEVICE_MODE, ATTR_SENSITIVITY, ATTR_REPORT_INTERVAL], [mfgCode: MFG_AQARA], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, [ATTR_DOOR_WINDOW_TYPE, ATTR_MOVEMENT_DETECTION, ATTR_VIBRATION_DETECTION], [mfgCode: MFG_AQARA], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, [ATTR_ORIENTATION_DETECTION, ATTR_FALL_DETECTION, ATTR_TRIPLE_TAP_DETECTION], [mfgCode: MFG_AQARA], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, [ATTR_ORIENTATION, ATTR_DEVICE_POSTURE], [mfgCode: MFG_AQARA], delay=200)
    if (cmds != []) {
        sendZigbeeCommands(cmds)
    }
}

void aqaraReadAttributes() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0xFCC0, [ATTR_DEVICE_MODE, ATTR_SENSITIVITY, ATTR_VIBRATION_DETECTION, ATTR_ORIENTATION, ATTR_DEVICE_POSTURE], [mfgCode: MFG_AQARA], delay=200)
    sendZigbeeCommands(cmds)
}

void aqaraBlackMagic() {
    List<String> cmds = []
    // Standard Lumi initialization sequence
    cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",]
    // Bind manufacturer-specific cluster
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
    // Bind genOnOff cluster (for contact events in door_window mode)
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}"
    // Bind closuresDoorLock cluster (for action events in object mode)
    cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0101 {${device.zigbeeId}} {}"
    // Read basic attributes
    cmds += zigbee.readAttribute(0x0000, [0x0004, 0x0005], [:], delay=200)
    // Read initial P100 settings
    cmds += zigbee.readAttribute(0xFCC0, [ATTR_DEVICE_MODE, ATTR_SENSITIVITY], [mfgCode: MFG_AQARA], delay=200)
    logDebug "aqaraBlackMagic() for P100"
    sendZigbeeCommands(cmds)
}

void configure(boolean fullInit = false) {
    log.info "${device.displayName} configure...fullInit = ${fullInit} (driver version ${driverVersionAndTimeStamp()})"
    unschedule()
    initializeVars(fullInit)
    runIn(DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])
    logWarn "<b>if no more logs, please pair the device again to HE!</b>"
    runIn(30, "aqaraReadAttributes", [overwrite: true])
}

def initialize() {
    log.info "${device.displayName} Initialize... (driver version ${driverVersionAndTimeStamp()})"
    configure(fullInit = true)
}

void initializeVars(boolean fullInit = false) {
    if (logEnable) log.info "${device.displayName} InitializeVars... fullInit = ${fullInit} (driver version ${driverVersionAndTimeStamp()})"
    if (fullInit == true) {
        state.clear()
        setDeviceName()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    initializeParamStorage()
    if (fullInit == true || state.health == null) { state.health = [:] }
    if (fullInit == true || state.rxCounter == null) { state.rxCounter = 0 }
    if (fullInit == true || state.txCounter == null) { state.txCounter = 0 }
    if (fullInit == true || state.notPresentCounter == null) { state.notPresentCounter = 0 }
    if (fullInit == true || settings?.logEnable == null) { device.updateSetting("logEnable", true) }
    if (fullInit == true || settings?.txtEnable == null) { device.updateSetting("txtEnable", true) }
    if (fullInit == true || settings?.deviceMode == null) { device.updateSetting("deviceMode", "object") }
    if (fullInit == true || settings?.motionSensitivity == null) { device.updateSetting("motionSensitivity", [value: 5, type: "number"]) }
    if (fullInit == true || settings?.reportInterval == null) { device.updateSetting("reportInterval", [value: 60, type: "number"]) }
    if (fullInit == true || settings?.movementDetection == null) { device.updateSetting("movementDetection", true) }
    if (fullInit == true || settings?.vibrationDetection == null) { device.updateSetting("vibrationDetection", true) }
    if (fullInit == true || settings?.orientationDetection == null) { device.updateSetting("orientationDetection", true) }
    if (fullInit == true || settings?.fallDetection == null) { device.updateSetting("fallDetection", true) }
    if (fullInit == true || settings?.tripleTapDetection == null) { device.updateSetting("tripleTapDetection", true) }
    if (fullInit == true || settings?.doorWindowType == null) { device.updateSetting("doorWindowType", "hinged_door") }
    if (fullInit == true) {
        powerSourceEvent()
        // Initialize PushableButton
        sendEvent(name: "numberOfButtons", value: 1, type: "digital")
    }
    updateAqaraVersion()
}

void installed() {
    log.info "${device.displayName} installed() model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} driver version ${driverVersionAndTimeStamp()}"
    sendHealthStatusEvent("unknown")
    sendEvent(name: "numberOfButtons", value: 1, type: "digital")
    aqaraBlackMagic()
}

// ==============================================================================================
// DEVICE NAME AND VERSION
// ==============================================================================================

void setDeviceName() {
    def deviceName = "Aqara P100 Multi-State Sensor DWZTCGQ11LM"
    if (device.getDataValue('model') == 'lumi.vibration.agl002') {
        updateDataValue("aqaraModel", "DWZTCGQ11LM")
        device.setName(deviceName)
        logInfo "device name set to ${deviceName}"
    } else {
        logWarn "unknown model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')}"
        updateDataValue("aqaraModel", "DWZTCGQ11LM")
    }
}

static String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() }

void checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "Updating the settings from driver version ${state.driverVersion} to ${driverVersionAndTimeStamp()}"
        state.comment = COMMENT_WORKS_WITH
        initializeVars(fullInit = false)
        if (device.getDataValue('aqaraModel') == null) {
            setDeviceName()
        }
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

void updateAqaraVersion() {
    def application = device.getDataValue("application")
    if (application != null) {
        if (application ==~ /(?i)^[0-9a-f]+$/) {
            def str = "0.0.0_" + String.format("%04d", zigbee.convertHexToInt(application.substring(0, Math.min(application.length(), 2))))
            if (device.getDataValue("aqaraVersion") != str) {
                device.updateDataValue("aqaraVersion", str)
                logInfo "aqaraVersion set to $str"
            }
        }
        else {
            logWarn "application data ${application} is not a valid hex string"
        }
    }
}

// ==============================================================================================
// POWER SOURCE
// ==============================================================================================

def powerSourceEvent() {
    sendEvent(name: "powerSource", value: "battery", descriptionText: "powerSource is battery", type: "digital")
    logInfo "powerSource is battery"
}

// ==============================================================================================
// HEALTH CHECK
// ==============================================================================================

def setHealthStatusOnline() {
    if ((state.rxCounter != null) && state.rxCounter <= 2) { return }
    sendHealthStatusEvent("online")
    state.notPresentCounter = 0
    unschedule('deviceCommandTimeout')
}

def pollPresence() { deviceHealthCheck() }

def deviceHealthCheck() {
    if (logEnable) log.debug "${device.displayName} deviceHealthCheck()"
    if (state.notPresentCounter != null) {
        state.notPresentCounter = state.notPresentCounter + 1
        if (state.notPresentCounter >= PRESENCE_COUNT_THRESHOLD) {
            sendHealthStatusEvent("offline")
        }
    } else {
        state.notPresentCounter = 0
    }
    runIn(DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])
}

void ping() {
    logInfo 'ping...'
    scheduleCommandTimeoutCheck()
    state.pingTime = new Date().getTime()
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0))
}

void sendRttEvent() {
    def now = new Date().getTime()
    def timeRunning = now.toInteger() - state.pingTime?.toInteger() ?: now.toInteger()
    logInfo "Round Trip Time is ${timeRunning} (ms)"
    sendEvent(name: "rtt", value: timeRunning, unit: "ms", type: "digital", descriptionText: "Round Trip Time is ${timeRunning} ms")
}

void sendHealthStatusEvent(String value) {
    if (device.currentValue('healthStatus') != value) {
        String descriptionText = "healthStatus changed to $value"
        sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} ${descriptionText}", type: "digital")
        if (value != 'online') {
            log.warn "${device.displayName} ${descriptionText}"
        } else {
            log.info "${device.displayName} ${descriptionText}"
        }
    }
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    runIn(delay, 'deviceCommandTimeout')
}

void deviceCommandTimeout() {
    logInfo 'no ping response received (sleepy device)'
}

// ==============================================================================================
// INFO EVENT
// ==============================================================================================

public void clearInfoEvent()  { sendInfoEvent('clear') }

public void sendInfoEvent(String info=null) {
    if (info == null || info == 'clear') {
        logDebug 'clearing the Status event'
        sendEvent(name: '_status_', value: 'clear', type: 'digital')
    } else {
        logInfo "${info}"
        sendEvent(name: '_status_', value: info, type: 'digital')
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')
    }
}

// ==============================================================================================
// LOGGING
// ==============================================================================================

void logDebug(final String msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

void logInfo(final String msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

void logWarn(final String msg) {
    if (settings?.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

void logsOff() {
    if (settings?.logEnable) log.info "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// ==============================================================================================
// UTILITIES
// ==============================================================================================

Integer safeToInt(val, Integer defaultVal=0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

void sendZigbeeCommands(List<String> cmds) {
    if (logEnable) { log.debug "${device.displayName} <b>sending</b> ZigbeeCommands : ${cmds}" }
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    if (state.txCounter != null) state.txCounter = state.txCounter + 1
}

String intToHexStr(Integer value, Integer minBytes=1) {
    return HexUtils.integerToHexString(value, minBytes)
}

// credits @thebearmay
String getModel() {
    try {
        String model = getHubVersion()
    } catch (ignore) {
        try {
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res ->
                model = res.data.device.modelName
                return model
            }
        } catch (ignore_again) {
            return ""
        }
    }
}

// credits @thebearmay
boolean isCompatible(Integer minLevel) {
    String model = getModel()
    String[] tokens = model.split('-')
    String revision = tokens.last()
    return (Integer.parseInt(revision) >= minLevel)
}

void test(String description) {
    log.trace "test(${description})"
}
