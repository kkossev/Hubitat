/**
 *  Aqara Motion and Presence sensor driver for Hubitat
 *
 *  https://community.hubitat.com/t/aqara-p1-motion-sensor/92987/46?u=kkossev
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
 * 
 * ver. 1.0.0 2022-06-24 kkossev  - first test version
 * ver. 1.1.0 2022-06-30 kkossev  - decodeAqaraStruct; added temperatureEvent;  RTCGQ13LM; RTCZCGQ11LM (FP1) parsing
 * ver. 1.1.1 2022-07-01 kkossev  - no any commands are sent immediately after pairing!
 * ver. 1.1.2 2022-07-04 kkossev  - PowerSource presence polling; FP1 pars
 * ver. 1.1.3 2022-07-04 kkossev  - FP1 approachDistance and monitoringMode parameters update
 * ver. 1.1.4 2022-07-08 kkossev  - aqaraReadAttributes()
 * ver. 1.1.5 2022-07-09 kkossev  - when going offline the battery level is set to 0 (zero); when back online, the last known battery level is restored; when switching offline, motion is reset to 'inactive'; added digital and physical events type
 * ver. 1.1.6 2022-07-12 kkossev  - aqaraBlackMagic; 
 * ver. 1.1.7 2022-07-23 kkossev  - added MCCGQ14LM for tests
 * ver. 1.2.0 2022-07-29 kkossev  - FP1 first successful initializaiton :
 *            attr. 0142 presence bug fix; debug logs improvements; monitoring_mode bug fix; LED is null bug fix ;motionRetriggerInterval bugfix for FP1; motion sensitivity bug fix for FP1; temperature exception bug; 
 *            monitoring_mode bug fix; approachDistance bug fix; setMotion command for tests/tuning of automations; added motion active/inactive simulation for FP1
 * ver. 1.2.1 2022-08-10 kkossev  - code / traces cleanup; change device name on initialize(); 
 * ver. 1.2.2 2022-08-21 kkossev  - added motionRetriggerInterval for T1 model; filter illuminance parsing for RTCGQ13LM
 * ver. 1.2.3 2022-12-11 kkossev  - (dev. branch ) added internalTemperature option (disabled by default); added homeKitCompatibility option to enable/disable battery 100% workaround for FP1 (HomeKit); Approach distance bug fix; battery 0% bug fix; pollPresence after hub reboot bug fix;
 *             RTCGQ13LM battery fix; added RTCGQ15LM and RTCGQ01LM; added GZCGQ01LM and GZCGQ11LM illuminance sensors for tests; refactored setDeviceName()
 *
 *
*/

def version() { "1.2.3" }
def timeStamp() {"2022/12/12 10:15 PM"}

import hubitat.device.HubAction
import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.helper.HexUtils


@Field static final Boolean debug = false
@Field static final Boolean deviceSimulation = false

metadata {
    definition (name: "Aqara P1 Motion Sensor", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20P1%20Motion%20Sensor/Aqara_P1_Motion_Sensor.groovy", singleThreaded: true ) {
        capability "Sensor"
		capability "Motion Sensor"
		capability "Illuminance Measurement"
		capability "TemperatureMeasurement"        
		capability "Battery"
        capability "PowerSource"
        capability "SignalStrength"    //lqi - NUMBER; rssi - NUMBER
        
        attribute "batteryVoltage", "string"
        attribute "presence", "enum", [
            "present",
            "not present"
        ]
        attribute "presence_type", "enum", [
            "enter",
            "leave",
            "left_enter",
            "right_leave",
            "right_enter",
            "left_leave",
            "approach",
            "away"
        ]
        
        command "configure", [[name: "Initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****" ]]
        command "setMotion", [[name: "Force motion active/inactive (when testing automations)", type: "ENUM", constraints: ["--- Select ---", "active", "inactive"], description: "Force motion active/inactive (for tests)"]]
        
        if (debug) {
            command "test", [[name: "Cluster", type: "STRING", description: "Zigbee Cluster (Hex)", defaultValue : "0001"]]
            command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****" ]]
            command "aqaraReadAttributes"
            command "activeEndpoints"
        }
        
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0", outClusters:"0003,0019,FCC0", model:"lumi.motion.ac02",  manufacturer:"LUMI",  deviceJoinName: "Aqara P1 Motion Sensor RTCGQ14LM"                     // Aqara P1 presence sensor RTCGQ14LM {manufacturerCode: 0x115f}
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0406,0003,0001", outClusters:"0003,0019",      model:"lumi.motion.agl04", manufacturer:"LUMI",  deviceJoinName: "Aqara High Precision Motion Sensor RTCGQ13LM"         // Aqara precision motion sensor
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,FCC0",      outClusters:"0003,0019",      model:"lumi.motion.ac01",  manufacturer:"aqara", deviceJoinName: "Aqara FP1 Human Presence Detector RTCZCGQ11LM"        // RTCZCGQ11LM ( FP1 )
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0406,0003,0001", outClusters:"0003,0019",      model:"lumi.motion.agl02", manufacturer:"LUMI",  deviceJoinName: "Aqara T1 Motion Sensor RTCGQ12LM"                     // https://zigbee.blakadder.com/Aqara_RTCGQ12LM.html RTCGQ12LM T1 motion sensor 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0", outClusters:"0003,0019",     model:"lumi.motion.acn001", manufacturer:"LUMI",  deviceJoinName: "Aqara E1 Motion Sensor RTCGQ15LM"                     // https://zigbee.blakadder.com/Aqara_RTCGQ12LM.html RTCGQ12LM T1 motion sensor 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,FFFF,0406,0400,0500,0001,0003", outClusters:"0000,0019", model:"lumi.sensor_motion.aq2", manufacturer:"LUMI", deviceJoinName: "Aqara Motion Sensor RTCGQ11LM"          // https://zigbee.blakadder.com/Aqara_RTCGQ11LM.html
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,FFFF,0019", outClusters:"0000,0004,0003,0006,0008,0005,0019", model:"lumi.sensor_motion", manufacturer:"LUMI", deviceJoinName: "Xiaomi/Mijia Motion Sensor RTCGQ01LM"   // https://zigbee.blakadder.com/Xiaomi_RTCGQ01LM.html
        // experimental
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0500,FCC0", outClusters:"0003,0019", model:"lumi.magnet.acn001", manufacturer:"LUMI",  deviceJoinName: aqaraModels['MCCGQ14LM'].deviceJoinName               // tests only : "Aqara Contact Sensor MCCGQ14LM"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0003,0001", outClusters:"0003", model:"lumi.sen_ill.mgl01", manufacturer:"LUMI",   deviceJoinName: aqaraModels['GZCGQ01LM'].deviceJoinName                        // tests only : "Mi Light Detection Sensor GZCGQ01LM"
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0003,0001", outClusters:"0003", model:"lumi.sen_ill.agl01", manufacturer:"LUMI",   deviceJoinName:  aqaraModels['GZCGQ11LM'].deviceJoinName                       // tests only : "Aqara T1 light intensity sensor GZCGQ11LM"    
        
    }

    preferences {
        if (logEnable == true || logEnable == false) { // Groovy ... :) 
            input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "Debug information, useful for troubleshooting. Recommended value is <b>false</b>", defaultValue: true)
            input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "Show motion activity in HE log page. Recommended value is <b>true</b>", defaultValue: true)
            input (title: "<b>Information on Pairing and Configuration:</b>", description: "Pair the P1 and FP1 devices at least 2 times, very close to the HE hub. For the battery-powered sensors, press shortly the pairing button on the device at the same time when clicking on Save Preferences", type: "paragraph", element: "paragraph")        
            if (!isFP1()) {
                input (name: "motionResetTimer", type: "number", title: "<b>Motion Reset Timer</b>", description: "After motion is detected, wait ___ second(s) until resetting to inactive state. Default = 30 seconds", range: "0..7200", defaultValue: 30)
            }    
            if (isRTCGQ13LM() || isP1() || isT1()) {
                input (name: "motionRetriggerInterval", type: "number", title: "<b>Motion Retrigger Interval</b>", description: "Motion Retrigger Interval, seconds (1..200)", range: "1..202", defaultValue: 30)
            }
            if (isRTCGQ13LM() || isP1() || isFP1()) {
                input (name: "motionSensitivity", type: "enum", title: "<b>Motion Sensitivity</b>", description: "Sensor motion sensitivity", defaultValue: 0, options: getSensitivityOptions())
            }
            if (isP1()) {
                input (name: "motionLED",  type: "enum", title: "<b>Enable/Disable LED</b>",  description: "Enable/disable LED blinking on motion detection", defaultValue: -1, options: ["0":"Disabled", "1":"Enabled" ])
            }
            if (isFP1()) {
                // "Approaching induction" distance : far, medium, near            // https://www.reddit.com/r/Aqara/comments/scht7o/aqara_presence_detector_fp1_rtczcgq11lm/
                input (name: "approachDistance", type: "enum", title: "<b>Approach distance</b>", description: "Approach distance", defaultValue: "1", options: approachDistanceOptions)
                // Monitoring Mode: "Undirected monitoring" - Monitors all motions within the sensing range; "Left and right monitoring" - Monitors motions on the lefy and right sides within
                input (name: "monitoringMode", type: "enum", title: "<b>Monitoring mode</b>", description: "monitoring mode", defaultValue: 0, options: monitoringModeOptions)
            }
            input (name: "internalTemperature", type: "bool", title: "<b>Internal Temperature</b>", description: "The internal temperature sensor is not very accurate, requires an offset and does not update frequently.<br>Recommended value is <b>false</b>", defaultValue: false)
            if (internalTemperature == true) {
                input (name: "tempOffset", type: "decimal", title: "<b>Temperature offset</b>", description: "Select how many degrees to adjust the temperature.", range: "-100..100", defaultValue: 0)
            }
            //if (isFP1()) {
            if (aqaraModels[device.getDataValue('aqaraModel')]?.preferences?.homeKitCompatibility) {
                input (name: "homeKitCompatibility",  type: "bool", title: "<b>HomeKit Compatibility</b>",  description: "Enable/disable HomeKit Compatibility", defaultValue: false)
            }
        }
    }
}

@Field static final Integer presenceCountTreshold = 3
@Field static final Integer defaultPollingInterval = 3600

@Field static final Map aqaraModels = [
    'RTCZCGQ11LM': [
        model: "lumi.motion.ac01", manufacturer: "aqara", deviceJoinName: "Aqara FP1 Human Presence Detector RTCZCGQ11LM",
        capabilities: ["motionSensor":true, "temperatureMeasurement":true, "battery":true, "powerSource":true, "signalStrength":true],
        attributes: ["presence", "presence_type"],
        preferences: [
            "motionSensitivity": [ min: 1, scale: 0, max: 3, step: 1, type: 'number', options:  [ "1":"low", "2":"medium", "3":"high" ] ],
            "approachDistance":true, "monitoringMode":true, "homeKitCompatibility":true
        ],
        motionRetriggerInterval: [ min: 1, scale: 0, max: 200, step: 1, type: 'number' ],    // TODO - check!
    ],
    'RTCGQ14LM': [
        model: "lumi.motion.ac02", manufacturer: "LUMI", deviceJoinName: "Aqara P1 Motion Sensor RTCGQ14LM",
        motionRetriggerInterval: [ min: 1, scale: 0, max: 200, step: 1, type: 'number' ],
        motionSensitivity: [ min: 1, scale: 0, max: 3, step: 1, type: 'number', options:  [ "1":"low", "2":"medium", "3":"high" ] ]
    ],
    'RTCGQ13LM': [
        model: "lumi.motion.agl04", manufacturer: "LUMI", deviceJoinName: "Aqara High Precision Motion Sensor RTCGQ13LM",
        motionRetriggerInterval: [ min: 1, scale: 0, max: 200, step: 1, type: 'number' ],
        motionSensitivity: [ min: 1, scale: 0, max: 3, step: 1, type: 'number', options:  [ "1":"low", "2":"medium", "3":"high" ] ]
    ],
    'RTCGQ12LM': [
        model: "lumi.motion.agl02", manufacturer: "LUMI", deviceJoinName: "Aqara T1 Motion Sensor RTCGQ12LM"
    ],
    'RTCGQ15LM': [
        model: "lumi.motion.acn001", manufacturer: "LUMI", deviceJoinName: "Aqara E1 Motion Sensor RTCGQ15LM"
    ],
    'RTCGQ11LM': [
        model: "lumi.sensor_motion.aq2", manufacturer: "LUMI", deviceJoinName: "Xiaomi Motion Sensor RTCGQ11LM"
    ],
    // experimental
    'MCCGQ14LM': [
        model: "lumi.magnet.acn001", manufacturer: "LUMI", deviceJoinName: "Aqara Contact Sensor MCCGQ14LM"
    ],
    'GZCGQ01LM': [
        model: "lumi.sen_ill.mgl01", manufacturer: "LUMI", deviceJoinName: "Mi Light Detection Sensor GZCGQ01LM"
    ],
    'GZCGQ11LM': [
        model: "lumi.sen_ill.agl01", manufacturer: "LUMI", deviceJoinName: "Aqara T1 light intensity sensor GZCGQ11LM"
    ]
]




def isRTCGQ13LM() { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.motion.agl04']) }     // Aqara Precision motion sensor
def isP1()        { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.motion.ac02'] ) }     // Aqara P1 motion sensor (LED control)
def isFP1()       { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.motion.ac01'] ) }     // Aqara FP1 Presence sensor (microwave radar)
def isE1()        { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.magnet.acn001'] ) }   // Aqara E1 contact sensor
def isT1()        { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.motion.agl02'] ) }    // Aqara T1 motion sensor

private P1_LED_MODE_VALUE(mode) { mode == "Disabled" ? 0 : mode == "Enabled" ? 1 : null }
private P1_LED_MODE_NAME(value) { value == 0 ? "Disabled" : value== 1 ? "Enabled" : null }
@Field static final Map sensitivityOptions =          [ "1":"low", "2":"medium", "3":"high" ]
@Field static final Map fp1PresenceEventOptions =     [ "0":"not present", "1":"present" ]
@Field static final Map fp1PresenceEventTypeOptions = [ "0":"enter", "1":"leave" , "2":"left_enter" , "3":"right_leave" , "4":"right_enter" , "5":"left_leave" , "6":"approach", "7":"away" ]
@Field static final Map approachDistanceOptions =     [ "0":"far", "1":"medium", "2":"near" ]
@Field static final Map monitoringModeOptions =       [ "0":"undirected", "1":"left_right" ]

def getSensitivityOptions() { aqaraModels[device.getDataValue('aqaraModel')]?.preferences?.motionSensitivity?.options ?: sensitivityOptions }

def parse(String description) {
    if (logEnable == true) log.debug "${device.displayName} parse: description is $description"
    checkDriverVersion()
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1
    setPresent()

    def descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch ( e ) {
        logWarn "parse: exception caught while parsing descMap:  ${descMap}"
        return null
    }
    if (logEnable) {log.debug "${device.displayName} parse: Desc Map: $descMap"}
    if (descMap.attrId != null ) {
        // attribute report received
        List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]]
        descMap.additionalAttrs.each {
            attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status]
        }
        attrData.each {
            if (it.status == "86") {
                logWarn "unsupported cluster ${it.cluster} attribute ${it.attrId}"
            }
		    else if (it.cluster == "0400" && it.attrId == "0000") {    // lumi.sensor_motion.aq2
                def rawLux = Integer.parseInt(it.value,16)
                if (device.getDataValue('model') in ['lumi.sen_ill.mgl01', 'lumi.sen_ill.agl01']) {
                    illuminanceEvent( rawLux )
                }
                else {
                    illuminanceEventLux( rawLux )
                }
		    }                 
            else if (it.cluster == "0406" && it.attrId == "0000") {    // lumi.sensor_motion.aq2
                map = handleMotion( Integer.parseInt(it.value,16) as Boolean )
            }
            else if (it.cluster == "0000" && it.attrId == "0001") {
                if (logEnable) log.info "${device.displayName} Applicaiton version is ${it.value}"
            }
            else if (it.cluster == "0000" && it.attrId == "0004") {    // device model
                if (txtEnable) log.info "${device.displayName} (parse) device model is ${it.value}"
            }
            else if (it.cluster == "0000" && it.attrId == "0005") {    // lumi.sensor_motion.aq2 button is pressed
                if (txtEnable) log.info "${device.displayName} (parse attr 5) device ${it.value} button was pressed "
            }
            else if (it.cluster == "0001" && it.attrId == "0020") {    // contact sensor
                if (it.value != "00") {
                    voltageAndBatteryEvents( Integer.parseInt(it.value,16) / 10.0)
                }
                else {
                    logWarn "ignored value ${it.value} cluster ${it.cluster} attr ${it.attrId} for ${device.getDataValue('model')}"
                }
            }
            else if (descMap.cluster == "FCC0") {    // Aqara P1
                parseAqaraClusterFCC0( description, descMap, it )
            }
            else if (descMap.cluster == "0000" && it.attrId == "FF01") {
                parseAqaraAttributeFF01( description )
            }
            else {
                if (logEnable) log.debug "${device.displayName} Unprocessed attribute report: cluster=${it.cluster} attrId=${it.attrId} value=${it.value} status=${it.status} data=${descMap.data}"
            }
        } // for each attribute
    } // if attribute report
    else if (descMap.profileId == "0000") { //zdo
        parseZDOcommand(descMap)
    } 
    else if (descMap.clusterId != null && descMap.profileId == "0104") { // ZHA global command
        parseZHAcommand(descMap)
    } 
    else {
        logWarn "Unprocesed unknown command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}

def parseAqaraAttributeFF01 ( description ) {
    Map result = [:]
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    result = parseBatteryFF01( valueHex )    
    sendEvent( result )
}
                     
                     
def parseAqaraClusterFCC0 ( description, descMap, it  ) {
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    switch (it.attrId) {
        case "0005" :
            if (logEnable) log.info "${device.displayName} (parseAqaraClusterFCC0) device ${it.value} button was pressed (driver version ${driverVersionAndTimeStamp()})"
            break
        case "0064" :
            logWarn "<b>received unknown report: ${P1_LED_MODE_NAME(value)}</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            break
        case "0065" :
            def value = safeToInt(it.value)
            if (isFP1()) { // FP1    'not present':'present'
                if (txtEnable) log.info "${device.displayName} (attr 0x065) presence is  ${fp1PresenceEventOptions[value.toString()]} (${value})"
                presenceEvent( fp1PresenceEventOptions[value.toString()] )
            }
            else {     // illuminance only? for RTCGQ12LM RTCGQ14LM
                illuminanceEventLux( value )
                if (txtEnable) log.info "${device.displayName} <b>received illuminance only report: ${P1_LED_MODE_NAME(value)}</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            }
            break
        case "0069" : // (105) PIR sensitivity RTCGQ13LM; distance for RTCZCGQ11LM; detection (retrigger) interval for RTCGQ14LM
            if (isRTCGQ13LM()) { 
                // sensitivity
                def value = safeToInt(it.value)
                device.updateSetting( "motionSensitivity",  [value:value.toString(), type:"enum"] )
                if (txtEnable) log.info "${device.displayName} <b>received PIR sensitivity report: ${sensitivityOptions[value.toString()]}</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            }
            else if (isP1()) {
                // retrigger interval
                def value = safeToInt(it.value)
                device.updateSetting( "motionRetriggerInterval",  [value:value.toString(), type:"number"] )
                if (txtEnable) log.info "${device.displayName} <b>received motion retrigger interval report: ${value} s</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            }
            else if (isFP1()) { // FP1
                def value = safeToInt(it.value)
                if (txtEnable) log.info "${device.displayName} (0x69) <b>received approach_distance report: ${value} s</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
                device.updateSetting( "approachDistance",  [value:value.toString(), type:"enum"] )
            }
            else {
                logWarn "Received unknown device report: cluster=${it.cluster} attrId=${it.attrId} value=${it.value} status=${it.status} data=${descMap.data}"
            }
            break
        case "00F7" :
            decodeAqaraStruct(description)
            break
        case "00FC" :
            logWarn "received unknown FC report:  (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            break
        case "0102" : // Retrigger interval (duration)
            def value = Integer.parseInt(it.value, 16)
            device.updateSetting( "motionRetriggerInterval",  [value:value.toString(), type:"number"] )
            if (txtEnable) log.info "${device.displayName} <b>received motion retrigger interval report: ${value} s</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            break
        case "0106" : // PIR sensitivity RTCGQ13LM RTCGQ14LM RTCZCGQ11LM
        case "010C" : // (268) PIR sensitivity RTCGQ13LM RTCGQ14LM (P1) RTCZCGQ11LM
            def value = safeToInt(it.value)
            device.updateSetting( "motionSensitivity",  [value:value.toString(), type:"enum"] )
            logInfo "${device.displayName} (${it.attrId}) <b>received PIR sensitivity report: ${sensitivityOptions[value.toString()]}</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            break
        case "0112" : // Aqara P1 PIR motion Illuminance
            if (!isRTCGQ13LM()) { // filter for High Preceision sensor - no illuminance sensor!
                def rawValue = Integer.parseInt((valueHex[(2)..(3)] + valueHex[(0)..(1)]),16)
                illuminanceEventLux( rawValue )
                handleMotion( true )    // TODO !!
            }
            break
        case "0142" : // (322) FP1 RTCZCGQ11LM presence
            def value = safeToInt(it.value)
            if (txtEnable) log.info "${device.displayName} (attr. 0x0142) presence is  ${fp1PresenceEventOptions[value.toString()]} (${value})"
            presenceEvent( fp1PresenceEventOptions[value.toString()] )
            break
        case "0143" : // (323) FP1 RTCZCGQ11LM presence_event {0: 'enter', 1: 'leave', 2: 'left_enter', 3: 'right_leave', 4: 'right_enter', 5: 'left_leave', 6: 'approach', 7: 'away'}[value];
            def value = safeToInt(it.value)
            presenceTypeEvent( fp1PresenceEventTypeOptions[value.toString()] )
            break
        case "0144" : // (324) FP1 RTCZCGQ11LM monitoring_mode
            def value = safeToInt(it.value)
            device.updateSetting( "monitoringMode",  [value:value.toString(), type:"enum"] )    // monitoring_mode = {0: 'undirected', 1: 'left_right'}[value]
            if (txtEnable) log.info "${device.displayName} <b>received monitoring_mode report: ${monitoringModeOptions[value.toString()]}</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            break
        case "0146" : // (326) FP1 RTCZCGQ11LM approach_distance 
            def value = safeToInt(it.value)
            device.updateSetting( "approachDistance",  [value:value.toString(), type:"enum"] )
            if (txtEnable) log.info "${device.displayName} (0x0146) <b>received approach_distance report: ${approachDistanceOptions[value.toString()]}</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"
            break
        case "0152" : // LED configuration
            def value = safeToInt(it.value)
            device.updateSetting( "motionLED",  [value:value.toString(), type:"enum"] )
            if (txtEnable) log.info "${device.displayName} <b>received LED configuration report: ${P1_LED_MODE_NAME(value)}</b> (cluster=${it.cluster} attrId=${it.attrId} value=${it.value})"    //P1_LED_MODE_VALUE
            break        
        default :
            if (logEnable) log.debug "${device.displayName} Unprocessed <b>FCC0</b> attribute report: cluster=${it.cluster} attrId=${it.attrId} value=${it.value} status=${it.status} data=${descMap.data}"
        break
    }
    
}

def decodeAqaraStruct( description )
{
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
	def MsgLength = valueHex.size()
    
    if (logEnable) log.debug "decodeAqaraStruct len = ${MsgLength} valueHex = ${valueHex}"
   	for (int i = 2; i < (MsgLength-3); ) {
        def dataType = Integer.parseInt(valueHex[(i+2)..(i+3)], 16)
        def tag = Integer.parseInt(valueHex[(i+0)..(i+1)], 16)                            
        def rawValue = 0
        //
        switch (dataType) {
            case 0x08 : // 8 bit data
            case 0x10 : // 1 byte boolean
            case 0x18 : // 8-bit bitmap
            case 0x20 : // 1 byte unsigned int
            case 0x28 : // 1 byte 8 bit signed int
            case 0x30 : // 8-bit enumeration
                rawValue = Integer.parseInt(valueHex[(i+4)..(i+5)], 16)
                switch (tag) {
                    case 0x03 :    // device temperature
                        temperatureEvent( rawValue )
                        break
                    case 0x64 :    // on/off
                        if (logEnable) log.debug "on/off is ${rawValue}"
                        break
                    case 0x9b :    // consumer connected
                        if (logEnable) log.debug "consumer connected is ${rawValue}"
                        break
                    case 0x64 :    // curtain lift or smoke/gas density; also battery percentage for Aqara curtain motor 
                        if (logEnable) log.debug "lift % or gas density is ${rawValue}"
                        break
                    case 0x65 :    // (101) FP1 presence
                        if (isFP1()) { // FP1 'not present':'present'
                            if (txtEnable) log.info "${device.displayName} (0x65) presence is  ${fp1PresenceEventOptions[rawValue.toString()]} (${rawValue})"
                            presenceEvent( fp1PresenceEventOptions[rawValue.toString()] )  
                        }
                        else {
                            if (logEnable) log.debug "${device.displayName} on/off EP 2 or battery percentage is ${rawValue}"
                        }
                        break
                    case 0x66 :    // (102)    FP1 
                        if (isFP1()) {
                            if (/* FP1 firmware version  < 50) */ false ) {
                                logWarn "RTCZCGQ11LM tag 0x66 (${rawValue} )"
                                presenceTypeEvent( fp1PresenceEventTypeOptions[rawValue.toString()] )
                            }
                            else {
                                device.updateSetting( "motionSensitivity",  [value:rawValue.toString(), type:"enum"] )
                                if (txtEnable) log.info "${device.displayName} (tag 0x66) sensitivity is <b>${sensitivityOptions[rawValue.toString()]}</b> (${rawValue})"
                            }
                        }
                        break
                    case 0x67 : // (103) FP1 monitoring_mode
                        if (isFP1()) {
                            if (txtEnable) log.info "${device.displayName} monitoring_mode is <b> ${monitoringModeOptions[rawValue.toString()]}</b> (${rawValue} )"
                            device.updateSetting( "monitoringMode",  [value:rawValue.toString(), type:"enum"] )
                        }
                        else {
                            if (logEnable) log.info "${device.displayName} tag 0x67 value is ${rawValue}"    // sent by T1 sensor
                        }
                        break
                    case 0x69 : // (105) 
                        if (isFP1()) { // FP1
                            device.updateSetting( "approachDistance",  [value:rawValue.toString(), type:"enum"] )    // {0: 'far', 1: 'medium', 2: 'near'}
                            if (txtEnable) log.info "${device.displayName} approach_distance is <b>${approachDistanceOptions[rawValue.toString()]}</b> (${rawValue})"
                        }
                        else if (isRTCGQ13LM()) {
                            // payload.motion_sensitivity = {1: 'low', 2: 'medium', 3: 'high'}[value];
                            device.updateSetting( "motionSensitivity",  [value:rawValue.toString(), type:"enum"] )
                            if (txtEnable) log.info "${device.displayName} (tag 0x69) sensitivity is <b>${sensitivityOptions[rawValue.toString()]}</b> (${rawValue})"
                        }
                        else if (isP1()) {
                            device.updateSetting( "motionRetriggerInterval",  [value:rawValue.toString(), type:"number"] )
                            if (txtEnable) log.info "${device.displayName} motion retrigger interval is ${rawValue} s."
                        }
                        else {
                            logWarn "unknown device ${device.getDataValue('model')} tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                        }
                        break
                    case 0x6A :    // sensitivity
                        if (isFP1()) {
                            logDebug "(0x6A) unknown parameter, value: ${rawValue}"
                        }
                        else {
                            device.updateSetting( "motionSensitivity",  [value:rawValue.toString(), type:"enum"] )
                            if (txtEnable) log.info "${device.displayName} (tag 0x6A) sensitivity is <b>${sensitivityOptions[rawValue.toString()]}</b> (${rawValue})"
                        }
                        break
                    case 0x6B :    // LED
                        if (isFP1()) {
                            logDebug "(0x06B) unknown parameter, value: ${rawValue}"
                        }
                        else {
                            device.updateSetting( "motionLED",  [value:rawValue.toString(), type:"enum"] )
                            if (txtEnable) log.info "${device.displayName} LED is ${P1_LED_MODE_NAME(rawValue)} (${rawValue})"
                        }
                        break
                    default :
                        logDebug "unknown tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                        break
                }
                i = i + (1 + 1 + 1) * 2
                break;
            case 0x21 : // 2 bytes 16bitUINT
                rawValue = Integer.parseInt((valueHex[(i+6)..(i+7)] + valueHex[(i+4)..(i+5)]),16)
                switch (tag) {
                    case 0x01 : // battery level
                        voltageAndBatteryEvents( rawValue/1000 )
                        break
                    case 0x05 : // RSSI
                        if (logEnable) log.debug "RSSI is ${rawValue} ? db"
                        break
                    case 0x0A : // Parent NWK
                        if (logEnable) log.debug "Parent NWK is ${valueHex[(i+6)..(i+7)] + valueHex[(i+4)..(i+5)]}"
                        break
                    case 0x0B : // lightlevel 
                        if (logEnable) log.debug "lightlevel is ${rawValue}"
                        break
                    case 0x65 : // illuminance or humidity
                        if (!isRTCGQ13LM()) {    // filter for high precision sensor - no illuminance!
                            illuminanceEventLux( rawValue )
                        }
                        break
                    default :
                        logDebug "unknown tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                        break
                }
                i = i + (1 + 1 + 2) * 2
                break
            case 0x0B : // 32-bit data
            case 0x1B : // 32-bit bitmap
            case 0x23 : // Unsigned 32-bit integer
            case 0x2B : // Signed 32-bit integer
                // TODO: Zcl32BitUint tag == 0x0d  -> firmware version ?
                logDebug "unknown 32 bit data tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                i = i + (1 + 1 + 4) * 2    // TODO: check!
                break
            case 0x24 : // 5 bytes 40 bits Zcl40BitUint tag == 0x06 -> LQI (?)
                switch (tag) {
                    case 0x06 :    // LQI ?
                        if (logEnable) log.debug "device LQI is ${valueHex[(i+4)..(i+14)]}"
                        break
                    default :
                        logDebug "unknown tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} TODO rawValue"
                        break
                }
                i = i + (1 + 1 + 5) * 2
                break;
            case 0x0C : // 40-bit data
            case 0x1C : // 40-bit bitmap
            case 0x24 : // Unsigned 40-bit integer
            case 0x2C : // Signed 40-bit integer
                logDebug "unknown 40 bit data tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                i = i + (1 + 1 + 5) * 2
                break
            case 0x0D : // 48-bit data
            case 0x1D : // 48-bit bitmap
            case 0x25 : // Unsigned 48-bit integer
            case 0x2D : // Signed 48-bit integer
                // TODO: Zcl48BitUint tag == 0x9a ?
                // TODO: Zcl64BitUint tag == 0x07 ?
                logDebug "unknown 48 bit data tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                i = i + (1 + 1 + 6) * 2
                break
            // TODO: Zcl16BitInt tag == 0x64 -> temperature
            // TODO: ZclSingleFloat tag == 0x95 (consumption) tag == 0x96 (voltage) tag == 0x97 (current) tag == 0x98 (power)
            // https://github.com/SwoopX/deconz-rest-plugin/blob/1c09f60eb2001fef790450e70a142180e9494aa4/general.xml
            default : 
                logWarn "unknown dataType 0x${valueHex[(i+2)..(i+3)]} at index ${i}"
                i = i + 1*2
                break
        } // switch dataType
	} // for all tags in valueHex 
}

// called by parseAqaraAttributeFF01 (cluster "0000")
private parseBatteryFF01( valueHex ) {
	def MsgLength = valueHex.size()
   	for (int i = 0; i < (MsgLength-3); i+=2) {
		if (valueHex[i..(i+1)] == "21") { // Search for byte preceeding battery voltage bytes
			rawValue = Integer.parseInt((valueHex[(i+2)..(i+3)] + valueHex[(i+4)..(i+5)]),16)
			break
		}
	}
    if (rawValue == 0) {
        return
    }
	def rawVolts = rawValue / 100
	def minVolts = 2.5
	def maxVolts = 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText = "Battery level is ${roundedPct}% (${rawVolts} Volts)"
    if (txtEnable) log.info "${device.displayName} ${descText}"
	def result = [
		name: 'battery',
		value: roundedPct,
		unit: "%",
        type:  isDigital == true ? "digital" : "physical",
		isStateChange: true,
		descriptionText: descText
	]
    state.lastBattery = roundedPct.toString()
	return result
}

def voltageAndBatteryEvents( rawVolts, isDigital=false  )
{
	def minVolts = 2.5
	def maxVolts = 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText = "Battery level is ${roundedPct}% (${rawVolts} Volts)"
    if (txtEnable) log.info "${device.displayName} ${descText}"
    sendEvent(name: 'batteryVoltage', value: rawVolts, unit: "V", type: "physical", isStateChange: true )
    sendBatteryEvent( roundedPct, isDigital )
    state.lastBattery = roundedPct.toString()
}

def sendBatteryEvent( roundedPct, isDigital=false ) {
    def descText = "Battery level "
    descText += isDigital ? safeToInt(roundedPct)==0 ?"forced to ${roundedPct}%" : "restored to ${roundedPct}%" : " "     // TODO !!!
    sendEvent(name: 'battery', value: roundedPct, unit: "%", type:  isDigital == true ? "digital" : "physical", descriptionText: descText, isStateChange: true )    
}

def parseZDOcommand( Map descMap ) {
    switch (descMap.clusterId) {
        case "0006" :
            if (logEnable) log.info "${device.displayName} Received match descriptor request, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7]+descMap.data[6]})"
            break
        case "0013" : // device announcement
            if (logEnable) log.info "${device.displayName} Received device announcement, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2]+descMap.data[1]}, Capability Information: ${descMap.data[11]})"
            aqaraBlackMagic()
            //aqaraReadAttributes()
            break
        case "8004" : // simple descriptor response
            if (logEnable) log.info "${device.displayName} Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
            parseSimpleDescriptorResponse( descMap )
            break
        case "8005" : // endpoint response
            if (logEnable) log.info "${device.displayName} Received endpoint response: cluster: ${descMap.clusterId} (endpoint response) endpointCount = ${ descMap.data[4]}  endpointList = ${descMap.data[5]}"
            break
        case "8021" : // bind response
            if (logEnable) log.info "${device.displayName} Received bind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "8022" : //unbind request
            if (logEnable) log.info "${device.displayName} Received unbind response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "8034" : //leave response
            if (logEnable) log.info "${device.displayName} Received leave response, data=${descMap.data}"
            break
        case "8038" : // Management Network Update Notify
            if (logEnable) log.info "${device.displayName} Received Management Network Update Notify, data=${descMap.data}"
            break
        default :
            if (logEnable) log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}

def parseZHAcommand( Map descMap) {
    switch (descMap.command) {
        case "01" : //read attribute response. If there was no error, the successful attribute reading would be processed in the main parse() method.
            def status = descMap.data[2]
            def attrId = descMap.data[1] + descMap.data[0] 
            if (status == "86") {
                logWarn "<b>UNSUPPORTED/b> Read attribute response: cluster ${descMap.clusterId} Attributte ${attrId} status code ${status}"
            }
            else {
                switch (descMap.clusterId) {
                    // "lumi.sensor_motion.aq2" inClusters: "0000,FFFF,0406,0400,0500,0001,0003"
                    case "0000" :
                    case "0001" :
                    case "0003" :
                    case "0400" :
                    case "0500" :
                    case "FFFF" :
                        logWarn "<b>NOT PROCESSED</b> Read attribute response: cluster ${descMap.clusterId} Attributte ${attrId} status code ${status}"
                        break                    
                    default :
                        logWarn "<b>UNHANDLED</b> Read attribute response: cluster ${descMap.clusterId} Attributte ${attrId} status code ${status}"
                        break
                }
            }
            break
        case "04" : //write attribute response
            if (logEnable==true) log.info "${device.displayName} Received Write Attribute Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "07" : // Configure Reporting Response
            if (txtEnable==true) log.info "${device.displayName} Received Configure Reporting Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            // Status: Unreportable Attribute (0x8c)
            break
        case "09" : // Command: Read Reporting Configuration Response (0x09)
            def status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00)
            def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000)
            if (status == 0) {
                def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10)
                def min = zigbee.convertHexToInt(descMap.data[6])*256 + zigbee.convertHexToInt(descMap.data[5])
                def max = zigbee.convertHexToInt(descMap.data[8]+descMap.data[7])
                if (logEnable==true) log.info "${device.displayName} Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribite:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max}"
            }
            else {
                if (logEnable==true) log.info "${device.displayName} <b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribite:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            }
            break
        case "0B" : // ZCL Default Response
            def status = descMap.data[1]
            if (status != "00") {
                switch (descMap.clusterId) {
                    /// "lumi.sensor_motion.aq2" inClusters: "0000,FFFF,0406,0400,0500,0001,0003"
                    case "0000" :
                    case "0001" :
                    case "0003" :
                    case "0400" :
                    case "0500" :
                    case "FFFF" :
                    default :
                        if (logEnable==true) log.info "${device.displayName} Received ZCL Default Response to Command ${descMap.data[0]} for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
                        break
                }
            }
            break
        default :
            if (logEnable==true) log.debug "${device.displayName} Unprocessed global command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
    }
}


def parseSimpleDescriptorResponse(Map descMap) {
    log.info "Received simple descriptor response, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}"
    log.info "Endpoint: ${descMap.data[5]} Application Device:${descMap.data[9]}${descMap.data[8]}, Application Version:${descMap.data[10]}"
    def inputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[11])
    def inputClusterList = ""
    for (int i in 1..inputClusterCount) {
        inputClusterList += descMap.data[13+(i-1)*2] + descMap.data[12+(i-1)*2] + ","
    }
    inputClusterList = inputClusterList.substring(0, inputClusterList.length() - 1)
    log.info "Input Cluster Count: ${inputClusterCount} Input Cluster List : ${inputClusterList}"
    if (getDataValue("inClusters") != inputClusterList)  {
        logWarn "inClusters=${getDataValue('inClusters')} differs from inputClusterList:${inputClusterList} - will be updated!"
        updateDataValue("inClusters", inputClusterList)
    }
    
    def outputClusterCount = hubitat.helper.HexUtils.hexStringToInt(descMap.data[12+inputClusterCount*2])
    def outputClusterList = ""
    for (int i in 1..outputClusterCount) {
        outputClusterList += descMap.data[14+inputClusterCount*2+(i-1)*2] + descMap.data[13+inputClusterCount*2+(i-1)*2] + ","
    }
    outputClusterList = outputClusterList.substring(0, outputClusterList.length() - 1)
    log.info "Output Cluster Count: ${outputClusterCount} Output Cluster List : ${outputClusterList}"
    if (getDataValue("outClusters") != outputClusterList)  {
        logWarn "outClusters=${getDataValue('outClusters')} differs from outputClusterList:${outputClusterList} -  will be updated!"
        updateDataValue("outClusters", outputClusterList)
    }
}




def illuminanceEvent( rawLux ) {
	def lux = rawLux > 0 ? Math.round(Math.pow(10,(rawLux/10000))) : 0
    sendEvent("name": "illuminance", "value": lux, "unit": "lx", type: "physical")
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}

def illuminanceEventLux( Integer lux ) {
    if ( lux > 0xFFDC ) lux = 0    // maximum value is 0xFFDC !
    sendEvent("name": "illuminance", "value": lux, "unit": "lx", type: "physical")
    if (settings?.txtEnable) log.info "$device.displayName illuminance is ${lux} Lux"
}

def temperatureEvent( temperature ) {
    if (settings?.internalTemperature == false) {
        return
    }
    def map = [:] 
    map.name = "temperature"
    map.unit = "\u00B0"+"C"
    if ( location.temperatureScale == "F") {
        temperature = (temperature * 1.8) + 32
        map.unit = "\u00B0"+"F"
    }
    Integer tempConverted = temperature + ((settings?.tempOffset?:0) as int) 
    map.value = tempConverted
    if (settings?.txtEnable) {log.info "${device.displayName} ${map.name} is ${map.value} ${map.unit}"}
    sendEvent(map)
}

def presenceEvent( String status, isDigital=false ) {
    if (status != null) {
        def type = isDigital == true ? "digital" : "physical"
        sendEvent("name": "presence", "value": status, "type": type)                    // isStateChange" true removed ver 1.2.0
        if (settings?.txtEnable) log.info "${device.displayName} presence is <b>${status}</b>"
        if (status == "present") {
            handleMotion(true, isDigital=true)
        }
        else {
            handleMotion(false, isDigital=true)
        }
    }
}
                                              
def presenceTypeEvent( String presenceTypeEvent, isDigital=false ) {
    if (presenceTypeEvent != null) {
        def type = isDigital == true ? "digital" : "physical"
        sendEvent("name": "presence_type", "value": presenceTypeEvent, "type": type)                // isStateChange" true removed ver 1.2.0
        if (settings?.txtEnable) log.info "${device.displayName} presence type is <b>${presenceTypeEvent}</b>"
        if (presenceTypeEvent in ["enter", "left_enter", "right_enter"] ) {
            handleMotion(true, isDigital=true)
        }
        else if (presenceTypeEvent in ["leave", "left_leave", "right_leave" ]) {
            handleMotion(false, isDigital=true)
        }        
    }
}

private handleMotion( Boolean motionActive, isDigital=false ) {    
    if (motionActive) {
        def timeout = settings?.motionResetTimer == null ? 30 : motionResetTimer
        // If the sensor only sends a motion detected message, the reset to motion inactive must be  performed in the code
        if (timeout != 0) {
            runIn(timeout, "resetToMotionInactive", [overwrite: true])
        }
        if (device.currentState('motion')?.value != "active") {
            state.motionStarted = now()
        }
    }
    else {
        if (device.currentState('motion')?.value == "inactive") {
            if (logEnable) log.debug "${device.displayName} ignored motion inactive event after ${getSecondsInactive()} s."
            return [:]   // do not process a second motion inactive event!
        }
    }
	return getMotionResult(motionActive, isDigital)
}

def getMotionResult( Boolean motionActive, isDigital=false ) {
	def descriptionText = "Detected motion"
    if (!motionActive) {
		descriptionText = "Motion reset to inactive after ${getSecondsInactive()} s."
    }
    else {
        descriptionText = device.currentValue("motion") == "active" ? "Motion is active ${getSecondsInactive()}s" : "Detected motion"
    }
    if (txtEnable) log.info "${device.displayName} ${descriptionText}"
	sendEvent (
			name			: 'motion',
			value			: motionActive ? 'active' : 'inactive',
            type            : isDigital == true ? "digital" : "physical",
			descriptionText : descriptionText
	)
}

def resetToMotionInactive() {
	if (device.currentState('motion')?.value == "active") {
		def descText = "Motion reset to inactive after ${getSecondsInactive()} s."
		sendEvent(
			name : "motion",
			value : "inactive",
			isStateChange : true,
            type:  "digital",
			descriptionText : descText
		)
        if (txtEnable) log.info "${device.displayName} ${descText}"
	}
    else {
        if (txtEnable) log.debug "${device.displayName} ignored resetToMotionInactive (software timeout) after ${getSecondsInactive()} s."
    }
}

def getSecondsInactive() {
    if (state.motionStarted) {
        return Math.round((now() - state.motionStarted)/1000)
    } else {
        return motionResetTimer ?: 30
    }
}

def powerSourceEvent( state = null) {
    if (state != null && state == 'unknown' ) {
        sendEvent(name : "powerSource",	value : "unknown", descriptionText: "device is OFFLINE", type: "digital")
    }
    else if (isFP1()) {
        sendEvent(name : "powerSource",	value : "dc", descriptionText: "device is back online", type: "digital")
    }
    else {
        sendEvent(name : "powerSource",	value : "battery", descriptionText: "device is back online", type: "digital")
    }
}

// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    if ((state.rxCounter != null) && state.rxCounter <= 2) {
        return                    // do not count the first device announcement or binding ack packet as an online presence!
    }
    powerSourceEvent()
    if (device.currentValue('powerSource', true) in ['unknown', '?']) {
        if (settings?.txtEnable) log.info "${device.displayName} is present"
        if (safeToInt(device.currentValue('battery', true)) == 0 ) {
            if (state.lastBattery != null &&  safeToInt(state.lastBattery) != 0) {
                if ((!isFP1()) || (isFP1() && settings?.homeKitCompatibility == true)) {
                    sendBatteryEvent(safeToInt(state.lastBattery), isDigital=true)
                }
            }
        }
    }    
    state.notPresentCounter = 0    
}

// called every 60 minutes from pollPresence()
def checkIfNotPresent() {
    if (state.notPresentCounter != null) {
        state.notPresentCounter = state.notPresentCounter + 1
        if (state.notPresentCounter >= presenceCountTreshold) {
            if (!(device.currentValue('powerSource', true) in ['unknown'])) {
    	        powerSourceEvent("unknown")
                logWarn "is not present!"
            }
            if (!(device.currentValue('motion', true) in ['inactive', '?'])) {
                handleMotion(false, isDigital=true)
                logWarn "forced motion to <b>inactive</b>"
            }
            if ((!isFP1()) || (isFP1() && settings?.homeKitCompatibility == true)) {
                if (safeToInt(device.currentValue('battery', true)) != 0) {
                    logWarn "${device.displayName} forced battery to '<b>0 %</b>"
                    sendBatteryEvent( 0, isDigital=true )
                }
            }
        }
    }
    else {
        state.notPresentCounter = 0  
    }
}

// check for device offline every 60 minutes
def pollPresence() {
    if (logEnable) log.debug "${device.displayName} pollPresence()"
    checkIfNotPresent()
    runIn( defaultPollingInterval, "pollPresence", [overwrite: true, misfire: "ignore"])
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        if (txtEnable==true) log.info "${device.displayName} updating the settings from driver version ${state.driverVersion} to ${driverVersionAndTimeStamp()}"
        initializeVars( fullInit = false ) 
        state.motionStarted = now()
        // added 12/04/2022
        if (isFP1()) {
            if (device.currentValue('battery', true) == null && settings?.homeKitCompatibility == true) {
                sendBatteryEvent( 100, isDigital=true )
            }
            if (state.lastBattery == null || safeToInt(state.lastBattery) == 0) {
                state.lastBattery = "100"
            }
        }
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

def logsOff(){
    if (settings?.logEnable) log.info "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}


// called when preferences are saved
def updated() {
    checkDriverVersion()
    ArrayList<String> cmds = []
    
    if (settings?.txtEnable) log.info "${device.displayName} Updating ${device.getName()} model ${device.getDataValue('model')} manufacturer <b>${device.getDataValue('manufacturer')}</b> (driver version ${driverVersionAndTimeStamp()})"
    if (settings?.txtEnable) log.info "${device.displayName} Debug logging is <b>${logEnable}</b>; Description text logging is <b>${txtEnable}</b>"
    if (logEnable==true) {
        runIn(86400, "logsOff", [overwrite: true, misfire: "ignore"])    // turn off debug logging after 24 hours
        logInfo "Debug logging is will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }
    if (settings?.internalTemperature == false) {
        device.deleteCurrentState("temperature")
    }
    
    def value = 0
    if (isP1()) {
        if (settings?.motionLED != null ) {
            value = safeToInt( motionLED )
            if (settings?.logEnable) log.debug "${device.displayName} setting motionLED to ${motionLED}"
            cmds += zigbee.writeAttribute(0xFCC0, 0x0152, 0x20, value, [mfgCode: 0x115F], delay=200)
        }
    }
    if (isRTCGQ13LM() || isP1() || isFP1()) {
        if (settings?.motionSensitivity != null && settings?.motionSensitivity != 0) {
            value = safeToInt( motionSensitivity )
            if (settings?.logEnable) log.debug "${device.displayName} setting motionSensitivity to ${sensitivityOptions[value.toString()]} (${value})"
            cmds += zigbee.writeAttribute(0xFCC0, 0x010C, 0x20, value, [mfgCode: 0x115F], delay=200)
            cmds += zigbee.readAttribute(0xFCC0, 0x010C, [mfgCode: 0x115F], delay=200)    // read it back
        }
    }
    if (isRTCGQ13LM() || isP1() || isT1()) {
        if (settings?.motionRetriggerInterval != null && settings?.motionRetriggerInterval != 0) {
            value = safeToInt( motionRetriggerInterval )
            logDebug "setting motionRetriggerInterval to ${motionRetriggerInterval} (${value})"
            cmds += zigbee.writeAttribute(0xFCC0, 0x0102, 0x20, value.toInteger(), [mfgCode: 0x115F], delay=200)
            cmds += zigbee.readAttribute(0xFCC0, 0x0102, [mfgCode: 0x115F], delay=200)    // read it back
        }
    }
    //
    if (isFP1()) { // FP1
        if (settings?.approachDistance != null) {    // [0:"far", 1:"medium", 2:"near" ]
            value = safeToInt( approachDistance )
            if (settings?.logEnable) log.debug "${device.displayName} setting approachDistance to ${approachDistanceOptions[value.toString()]} (${value})"
            cmds += zigbee.writeAttribute(0xFCC0, 0x0146, 0x20, value, [mfgCode: 0x115F], delay=200)
        }
        if (settings?.monitoringMode != null) {    // [0:"undirected", 1:"left_right" ]
            value = safeToInt( monitoringMode )
            if (settings?.logEnable) log.debug "${device.displayName} setting monitoringMode to ${monitoringModeOptions[value.toString()]} (${value})"
            cmds += zigbee.writeAttribute(0xFCC0, 0x0144, 0x20, value, [mfgCode: 0x115F], delay=200)
        }
        if (settings?.homeKitCompatibility == false) {
            device.deleteCurrentState("battery")
        }
        else if (device.currentValue('battery', true) == null) {
            sendBatteryEvent( 100, isDigital=true )
        }
    }
    //
    if ( cmds != null ) {
        sendZigbeeCommands( cmds )     
    }
}    

// called from  initializeVars( fullInit = true)
void setDeviceName() {
    String deviceName
    def currentModelMap = null
    aqaraModels.each { k, v -> 
        //log.trace "${k}:${v}" 
        if (v.model ==  device.getDataValue('model') && v.manufacturer == device.getDataValue('manufacturer')) {
            currentModelMap = k
            log.trace "found ${k}"
            updateDataValue("aqaraModel", currentModelMap)
            deviceName = aqaraModels[currentModelMap].deviceJoinName
        }
    }
    if (currentModelMap == null) {
        //log.trace "not found!"
        if (device.getDataValue('manufacturer') in ['aqara', 'LUMI']) {
            deviceName = "Aqara Sensor"
            updateDataValue("aqaraModel", currentModelMap)
        }
        else {
            logWarn "unknown model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')}"
            // don't change the device name when unknown
            updateDataValue("aqaraModel", currentModelMap)
        }        
    }
    //
    device.setName(deviceName)
    logInfo "device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} <b>aqaraModel ${device.getDataValue('aqaraModel')}</b> deviceName was set to ${deviceName}"
}

void initializeVars( boolean fullInit = false ) {
    if (logEnable==true) log.info "${device.displayName} InitializeVars... fullInit = ${fullInit} (driver version ${driverVersionAndTimeStamp()})"
    if (fullInit == true ) {
        state.clear()
        setDeviceName()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    if (fullInit == true || state.rxCounter == null) state.rxCounter = 0
    if (fullInit == true || state.txCounter == null) state.txCounter = 0
    if (fullInit == true || state.notPresentCounter == null) state.notPresentCounter = 0
    if (fullInit == true || state.motionStarted == null) state.motionStarted = now()
    if (state.lastBattery == null) state.lastBattery = "100"
    
    if (fullInit == true || settings?.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit == true || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit == true || settings?.internalTemperature == null) device.updateSetting("internalTemperature", false)
    if (fullInit == true || settings?.motionResetTimer == null) device.updateSetting("motionResetTimer", 30)
    if (fullInit == true || settings?.homeKitCompatibility == null) device.updateSetting("homeKitCompatibility", false)
    
    if (isFP1()) {
        device.updateSetting("motionResetTimer", [value: 0 , type:"number"])    // no auto reset for FP1
    }
    if (fullInit == true || settings.tempOffset == null) device.updateSetting("tempOffset", 0)    
    
    if (fullInit == true ) sendEvent(name : "powerSource",	value : "?", isStateChange : true)

}

def installed() {
    log.info "${device.displayName} installed() model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} driver version ${driverVersionAndTimeStamp()}"
    aqaraBlackMagic()
}

def configure(boolean fullInit = true ) {
    log.info "${device.displayName} configure...(driver version ${driverVersionAndTimeStamp()})"
    unschedule()
    initializeVars( fullInit )
    runIn( defaultPollingInterval, "pollPresence", [overwrite: true, misfire: "ignore"])
    logWarn "<b>if no more logs, please pair the device again to HE!</b>"
    runIn( 30, "aqaraReadAttributes", [overwrite: true])
}
def initialize() {
    log.info "${device.displayName} Initialize... (driver version ${driverVersionAndTimeStamp()})"
    configure(fullInit = true)
}

Integer safeToInt(val, Integer defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

Double safeToDouble(val, Double defaultVal=0.0) {
	return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

void sendZigbeeCommands(List<String> cmds) {
    if (logEnable) {log.debug "${device.displayName} <b>sending</b> ZigbeeCommands : ${cmds}"}
	sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE))
    if (state.txCounter != null) state.txCounter = state.txCounter + 1
}

// device Web UI command
def setMotion( mode ) {
    switch (mode) {
        case "active" : 
            handleMotion(true, isDigital=true)
            if (isFP1()) {
                presenceEvent("present", isDigital=true)
                presenceTypeEvent("enter", isDigital=true)
            }
            break
        case "inactive" :
            handleMotion(false, isDigital=true)
            if (isFP1()) {
                presenceEvent("not present", isDigital=true)
                presenceTypeEvent("leave", isDigital=true)
            }
            break
        default :
            logWarn "select motion action"
            break
    }
}



String integerToHexString(BigDecimal value, Integer minBytes, boolean reverse=false) {
    return integerToHexString(value.intValue(), minBytes, reverse=reverse)
}

String integerToHexString(Integer value, Integer minBytes, boolean reverse=false) {
    if(reverse == true) {
        return HexUtils.integerToHexString(value, minBytes).split("(?<=\\G..)").reverse().join()
    } else {
        return HexUtils.integerToHexString(value, minBytes)
    }
    
}


def aqaraReadAttributes() {
    List<String> cmds = []

    if (isT1()) {             // RTCGQ12LM Aqara T1 human body movement and illuminance sensor
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)     // TODO: check - battery voltage
        cmds += zigbee.readAttribute(0xFCC0, 0x0102, [mfgCode: 0x115F], delay=200)
    }
    else if (isRTCGQ13LM()) {         // Aqara high precision motion sensor
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)    // TODO: check - battery voltage
        cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay=200)
    }
    else if (isP1()) {    // Aqara P1 human body movement and illuminance sensor
        cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C, 0x0152], [mfgCode: 0x115F], delay=200)
    }
    else if (isFP1()) {  // Aqara presence detector FP1 
        cmds += zigbee.readAttribute(0xFCC0, [0x010C, 0x0142, 0x0144, 0x0146], [mfgCode: 0x115F], delay=200)
    }
    else if (isE1()) {   // E1 contact sensor
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)    //  battery voltage
        cmds += zigbee.readAttribute(0x0002, 0x0500, [mfgCode: 0x115F], delay=200)    //  open/close IAS Zone 2
    }
    else {
        logWarn "unknown device ${device.getDataValue('manufacturer')} ${device.getDataValue('model')}"
    }    
    
    sendZigbeeCommands( cmds )       
}


def aqaraBlackMagic() {
    List<String> cmds = []

    if (isP1()) {
        cmds += zigbee.readAttribute(0x0000, [0x0004, 0x0005], [:], delay=200)
    }
    else if (isE1()) {
        logWarn "aqaraBlackMagic() for E1"
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",]
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 FF 00 41 10 89 34 86 38 41 04 19 89 90 79 74 27 27 80 18 45}  {0x0104}", "delay 200",]     // contact sensor write attr 0xFF
    }
    else if (isFP1()) {
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 50",]
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 FF 00 41 10 02 32 71 76 20 79 16 48 28 87 18 12 21 55 72 36}  {0x0104}", "delay 50",]      // FP1 write attr 0xFF 16 bytes
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 50 01 41 07 01 01 ff ff 00 00 ff}  {0x0104}", "delay 50",]                                 // FP1 write attr 0x0150 8 bytes
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 50 01 41 03 06 55 35}  {0x0104}", "delay 50",]                                             // FP1 (seq:5) write attr 0x0150 4 bytes
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 50 01 41 07 01 02 ff ff 00 00 ff}  {0x0104}", "delay 50",]                                 // FP1 (seq:6) write attr 0x0150 8 bytes
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 50 01 41 03 06 55 35}  {0x0104}", "delay 50",]                                             // FP1 (seq:7) write attr 0x0150 4 bytes
        cmds += zigbee.writeAttribute(0xFCC0, 0x0155, 0x20, 0x01, [mfgCode: 0x115F], delay=50)                                                                                                // FP1 (seq 8) write attr 0x0155 : 1 byte 01
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 f2 ff 41 aa 74 02 44 00 9c 03 20}  {0x0104}", "delay 50",]                                 // FP1 (seq:9) write attr 0xfff2 8 bytes
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 f2 ff 41 aa 74 02 44 01 9b 01 20}  {0x0104}", "delay 50",]                                 // FP1 (seq:10) write attr 0xfff2 8 bytes
        //cmds += activeEndpoints()         
        logDebug "aqaraBlackMagic() for FP1"
    }
    else {
        //logWarn "aqaraBlackMagic() = NOT E1 !!!!!!"
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",]
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)    // TODO: check - battery voltage
        cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay=200)
    }
    //cmds += activeEndpoints()
    sendZigbeeCommands( cmds )

}

def activeEndpoints() {
    List<String> cmds = []
    
    cmds += ["he raw ${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"] //get all the endpoints...
    String endpointIdTemp = endpointId == null ? "01" : endpointId
    cmds += ["he raw ${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} $endpointIdTemp} {0x0000}"]
    
    return cmds    
}

def logDebug(msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

def logInfo(msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

def logWarn(msg) {
    if (settings?.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

def test( description ) {
        List<String> cmds = []
            value = safeToInt( description )
            if (settings?.logEnable) log.debug "${device.displayName} setting approachDistance to ${approachDistanceOptions[value.toString()]} (${value})"
            cmds += zigbee.writeAttribute(0xFCC0, 0x0146, 0x20, value, [mfgCode: 0x115F], delay=200)    
}


