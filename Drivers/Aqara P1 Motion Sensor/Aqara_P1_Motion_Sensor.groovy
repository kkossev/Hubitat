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
 * ver. 1.2.3 2022-12-26 kkossev  - added internalTemperature option (disabled by default); added homeKitCompatibility option to enable/disable battery 100% workaround for FP1 (HomeKit); Approach distance bug fix; battery 0% bug fix; pollPresence after hub reboot bug fix;
 *             RTCGQ13LM battery fix; added RTCGQ15LM and RTCGQ01LM; added GZCGQ01LM and GZCGQ11LM illuminance sensors for tests; refactored setDeviceName(); min. Motion Retrigger Interval limited to 2 seconds.
 * ver. 1.2.4 2023-01-26 kkossev  - renamed homeKitCompatibility option to sendBatteryEventsForDCdevices; aqaraModel bug fix
 * ver. 1.2.5 2023-01-30 kkossev  - bug fixes for 'lumi.sen_ill.mgl01' light sensor'; setting device name bug fix;
 * ver. 1.3.0 2023-03-06 kkossev  - regions reports decoding; on SetMotion(inactive) a Reset presence command is sent to FP1; FP1 fingerprint is temporary commented out for tests; added aqaraVersion'; Hub model (C-7 C-8) decoding
 * ver. 1.3.1 2023-03-15 kkossev  - added RTCGQ01LM lumi.sensor_motion battery % and voltage; removed sendBatteryEventsForDCdevices option; removed lastBattery;
 * ver. 1.4.0 2023-03-17 kkossev  - *** breaking change *** replaced presence => roomState [unoccupied,occupied]; replaced presence_type => roomActivity ; added capability 'Health Check'; added 'Works with ...'; added ping() and RTT
 * ver. 1.4.1 2023-04-21 kkossev  - exception prevented when application string is enormously long; italic font bug fix; lumi.sen_ill.agl01 initialization and bug fixes; light sensor delta = 5 lux; removed MCCGQ14LM
 * ver. 1.4.2 2023-05-21 kkossev  - lumi.sen_ill.agl01 initialization fixes; removed the E1 contact sensor driver code; trace logs cleanup; added reporting time configuration for the Lux sensors; Lux sensors preferences are NOT reset to defaults when paired again; removed powerSource manipulation; periodic job renamed to deviceHealthCheck()
 * ver. 1.5.0 2024-02-29 kkossev  - Groovy Lint
 * ver. 1.6.0 2024-06-29 kkossev  - added state.health 'parentNWK' and 'nwkCtr'; added attribute parentNWK;
 * ver. 1.6.1 2024-07-22 kkossev  - bugfix: illuminanceThreshold and illuminanceMinReportingTime not working for lumi.sen_ill.mgl01 (GZCGQ01LM)
 * ver. 1.7.0 2024-08-15 kkossev  - added lumi.sensor_occupy.agl1 - Aqara FP1E; capability 'Refresh'; added spammy reports filtering for FP1E
 * ver. 1.7.1 2024-11-19 kkossev  - added motionSensitivity for FP1E; added targetDistance for FP1E; added detectionRange for FP1E
 * ver. 1.7.2 2024-11-28 kkossev  - HE platfrom 2.4.0.x compatibility fixes;
 * ver. 1.7.3 2025-01-16 kkossev  - first ping() throwing exception bug fix tnx@user2428 
 * ver. 1.7.4 2025-05-24 kkossev  - HE platfrom version 2.4.1.x decimal preferences range patch/workaround.
 * ver. 1.7.5 2025-09-15 bbholthome  - light sensor GZCGQ01LM maximum illuminance capped to 65500 lux
 * 
 *                                 TODO: 
 *
 */

static String version() { "1.7.5" }
static String timeStamp() {"2025/09/15 12:35 PM"}

import hubitat.device.HubAction
import hubitat.device.Protocol
import groovy.transform.Field
import hubitat.zigbee.zcl.DataType
import hubitat.helper.HexUtils
import java.util.concurrent.ConcurrentHashMap

@Field static final Boolean _DEBUG = false
@Field static final Boolean deviceSimulation = false
@Field static final Boolean _REGIONS = false
@Field static final String COMMENT_WORKS_WITH = 'Works with Aqara P1, FP1, FP1E, Aqara/Xiaomi/Mija other motion and illuminance sensors'

@Field static final Map<Integer, Map> DynamicSettingsMap = new ConcurrentHashMap<>().withDefault {
    new ConcurrentHashMap<String, String>()
}

metadata {
    definition (name: "Aqara P1 Motion Sensor", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20P1%20Motion%20Sensor/Aqara_P1_Motion_Sensor.groovy", singleThreaded: true ) {
        capability "Sensor"
		capability "Motion Sensor"
		capability "Illuminance Measurement"
		capability "TemperatureMeasurement"        
		capability "Battery"
        capability "PowerSource"
        capability "Health Check"
        capability "Refresh"
        //capability "SignalStrength"    //lqi - NUMBER; rssi - NUMBER (not supported yet)
        
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute "batteryVoltage", "string"
        attribute "rtt", "number" 
        attribute "parentNWK", "string"
        attribute "roomState", "enum", [
            "unoccupied",
            "occupied"
        ]
        attribute "roomActivity", "enum", [
            "enter",
            "leave",
            "enter (right)",
            "leave (left)",
            "enter (left)",
            "leave (right)",
            "towards",
            "away",
            "idle", // FP1E
            "large movement", // FP1E
            "small movement" // FP1E
        ]
        attribute "targetDistance", "number"    // FP1E
        attribute "detectionRange", "decimal"   // FP1E
        attribute "motionSensitivity", "enum", ["low", "medium", "high"]   // FP1E
       
        if (_REGIONS) {
            attribute "region_last_enter", "number"
            attribute "region_last_leave", "number"
            attribute "region_last_occupied", "number"
            attribute "region_last_unoccupied", "number"
        }
        
        command "configure", [[name: "Initialize the device after switching drivers. Will load device default values!" ]]
        command "setMotion", [[name: "Force motion active/inactive (when testing automations)", type: "ENUM", constraints: ["active", "inactive"], description: "Use for tests"]]
        command "ping",      [[name: "Check device online status and measure the Round-Trip Time (ms)"]]

        if (_DEBUG) {
            command "test", [[name: "Cluster", type: "STRING", description: "Zigbee Cluster (Hex)", defaultValue : "FCC0"]]
            command "initialize", [[name: "Manually initialize the device after switching drivers. ***** Will load device default values! *****" ]]
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
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0003,0001", outClusters:"0003", model:"lumi.sen_ill.mgl01", manufacturer:"LUMI",   deviceJoinName: aqaraModels['GZCGQ01LM'].deviceJoinName                        // Mi Light Detection Sensor GZCGQ01LM
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0003,0001", outClusters:"0003", model:"lumi.sen_ill.mgl01", manufacturer: "XIAOMI", deviceJoinName: "Mi Light Detection Sensor GZCGQ01LM" 
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0003,0001", outClusters:"0003", model:"lumi.sen_ill.agl01", manufacturer:"LUMI",   deviceJoinName:  aqaraModels['GZCGQ11LM'].deviceJoinName                       // tests only : "Aqara T1 light intensity sensor GZCGQ11LM"    
        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,FCC0", outClusters:"0003,0019", model:"lumi.sensor_occupy.agl1", manufacturer:"aqara", controllerType: "ZGB", deviceJoinName: "Aqara FP1E Human Presence Detector RTCZCGQ13LM"        // RTCZCGQ13LM ( FP1E )
    }

    preferences {
        input (name: "txtEnable", type: "bool", title: "<b>Description text logging</b>", description: "<i>Show motion activity in HE log page. Recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "logEnable", type: "bool", title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. Recommended value is <b>false</b></i>", defaultValue: true)
        input (name: 'helpInfo',  type: 'hidden', title: "Information on Pairing and Configuration", description: "Pair the P1 and FP1/FP1E devices two times (without deleting), very close to the HE hub. For the battery-powered sensors, press shortly the pairing button on the device at the same time when clicking on Save Preferences")
        if (device) {
            if (!(isFP1() || isFP1E()) && !isLightSensor()) {
                input (name: "motionResetTimer", type: "number", title: "<b>Motion Reset Timer</b>", description: "<i>After motion is detected, wait ${motionResetTimer} second(s) until resetting to inactive state. Default = 30 seconds</i>", range: "0..7200", defaultValue: 30)
            }    
            if (isRTCGQ13LM() || isP1() || isT1()) {
                input (name: "motionRetriggerInterval", type: "number", title: "<b>Motion Retrigger Interval</b>", description: "<i>Motion Retrigger Interval, seconds (2..200)</i>", range: "2..202", defaultValue: 30)
            }
            if (isRTCGQ13LM() || isP1() || isFP1() || isFP1E()) {
                input (name: "motionSensitivity", type: "enum", title: "<b>Motion Sensitivity</b>", description: "<i>Sensor motion sensitivity</i>", defaultValue: 0, options: getSensitivityOptions())
            }
            if (isP1()) {
                input (name: "motionLED",  type: "enum", title: "<b>Enable/Disable LED</b>",  description: "<i>Enable/disable LED blinking on motion detection</i>", defaultValue: -1, options: ["0":"Disabled", "1":"Enabled" ])
            }
            if (isFP1()) {
                input (name: "approachDistance", type: "enum", title: "<b>Approach distance</b>", description: "<i>Approach distance</i>", defaultValue: "1", options: approachDistanceOptions)
                input (name: "monitoringMode", type: "enum", title: "<b>Monitoring mode</b>", description: "<i>monitoring mode</i>", defaultValue: 0, options: monitoringModeOptions)
            }
            if (isFP1E()) {
                input (name: "filterSpam", type: "bool", title: "<b>Filter FP1E Distance Reports</b>", description: "<i>Filter the FP1E distance reports, if not really used in automations. Recommended value is <b>true</b></i>", defaultValue: true)
                input (name: 'detectionRange', type: 'decimal', title: '<b>Detection Range</b>', description: '<i>Maximum detection distance, range (0.10..6.00)</i>', range: '0..6', defaultValue: 6.00)
            }
            if (isLightSensor()) {
                input (name: "illuminanceMinReportingTime", type: "number", title: "<b>Minimum time between Illuminance Reports</b>", description: "<i>illuminance minimum reporting interval, seconds (4..300)</i>", range: "4..300", defaultValue: DEFAULT_ILLUMINANCE_MIN_TIME)
                input (name: "illuminanceMaxReportingTime", type: "number", title: "<b>Maximum time between Illuminance Reports</b>", description: "<i>illuminance maximum reporting interval, seconds (120..10000)</i>", range: "120..10000", defaultValue: DEFAULT_ILLUMINANCE_MAX_TIME)
                input (name: "illuminanceThreshold", type: "number", title: "<b>Illuminance Reporting Threshold</b>", description: "<i>illuminance reporting threshold, value (1..255)<br>Bigger values will result in less frequent reporting</i>", range: "1..255", defaultValue: 1)
                input (name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.1..10.0)</i>', range: '0..10', defaultValue: 1.00)
            }
            input (name: "internalTemperature", type: "bool", title: "<b>Internal Temperature</b>", description: "<i>The internal temperature sensor is not very accurate, requires an offset and does not update frequently.<br>Recommended value is <b>false</b></i>", defaultValue: false)
            if (internalTemperature == true) {
                input (name: "tempOffset", type: "decimal", title: "<b>Temperature offset</b>", description: "<i>Select how many degrees to adjust the temperature.</i>", range: "-100..100", defaultValue: 0)
            }            
        }
    }
}

@Field static final int COMMAND_TIMEOUT = 10                // Command timeout before setting healthState to offline
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3
@Field static final Integer DEFAULT_POLLING_INTERVAL = 3600
@Field static final Integer DEFAULT_ILLUMINANCE_MIN_TIME = 5
@Field static final Integer DEFAULT_ILLUMINANCE_MAX_TIME = 300
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 1

@Field static final Map aqaraModels = [
    'RTCZCGQ11LM': [
        model: "lumi.motion.ac01", manufacturer: "aqara", deviceJoinName: "Aqara FP1 Human Presence Detector RTCZCGQ11LM",
        capabilities: ["motionSensor":true, "temperatureMeasurement":true, "battery":true, "powerSource":true, "signalStrength":true],
        attributes: ["roomState", "roomActivity"],
        preferences: [
            "motionSensitivity": [ min: 1, scale: 0, max: 3, step: 1, type: 'number', options:  [ "1":"low", "2":"medium", "3":"high" ] ],
            "approachDistance":true, "monitoringMode":true
        ],
        motionRetriggerInterval: [ min: 2, scale: 0, max: 200, step: 1, type: 'number' ],    // TODO - check!
    ],
    'RTCZCGQ13LM': [    // FP1E https://github.com/niceboygithub/AqaraGateway/blob/dedad6e56d02b6f5d1dac364d9d9a20ec12c5ff8/custom_components/aqara_gateway/core/utils.py#L491
        // https://github.com/Koenkk/zigbee-herdsman-converters/blob/5fbee666c8edbf198aa91a37f8008f51b4f1b467/src/devices/lumi.ts#L1895
        model: "lumi.sensor_occupy.agl1", manufacturer: "aqara", deviceJoinName: "Aqara FP1E Human Presence Detector RTCZCGQ13LM",
        capabilities: ["motionSensor":true, "temperatureMeasurement":true, "battery":true, "powerSource":true, "signalStrength":true],
        attributes: ["roomState", "roomActivity"],
        preferences: [
            "motionSensitivity": [ min: 1, scale: 0, max: 3, step: 1, type: 'number', options:  [ "1":"low", "2":"medium", "3":"high" ] ],
            "approachDistance":true, "monitoringMode":true
        ],
        motionRetriggerInterval: [ min: 2, scale: 0, max: 200, step: 1, type: 'number' ],    // TODO - check!
    ],    
    'RTCGQ14LM': [
        model: "lumi.motion.ac02", manufacturer: "LUMI", deviceJoinName: "Aqara P1 Motion Sensor RTCGQ14LM",
        motionRetriggerInterval: [ min: 2, scale: 0, max: 200, step: 1, type: 'number' ],
        motionSensitivity: [ min: 1, scale: 0, max: 3, step: 1, type: 'number', options:  [ "1":"low", "2":"medium", "3":"high" ] ]
    ],
    'RTCGQ13LM': [
        model: "lumi.motion.agl04", manufacturer: "LUMI", deviceJoinName: "Aqara High Precision Motion Sensor RTCGQ13LM",
        motionRetriggerInterval: [ min: 2, scale: 0, max: 200, step: 1, type: 'number' ],
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
    'RTCGQ01LM': [
        model: "lumi.sensor_motion", manufacturer: "LUMI", deviceJoinName: "Xiaomi Motion Sensor RTCGQ01LM"
    ],
    'GZCGQ01LM': [
        model: "lumi.sen_ill.mgl01", manufacturer: "LUMI", deviceJoinName: "Mi Light Detection Sensor GZCGQ01LM"    // aka vendor: 'Xiaomi', model: 'YTC4043GL'
        // also model: "lumi.sen_ill.mgl01", manufacturer: "XIAOMI", deviceJoinName: "Mi Light Detection Sensor GZCGQ01LM" 
    ],
    // experimental
    'GZCGQ11LM': [
        model: "lumi.sen_ill.agl01", manufacturer: "LUMI", deviceJoinName: "Aqara T1 light intensity sensor GZCGQ11LM"
    ]
]

def isRTCGQ13LM() { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.motion.agl04']) }     // Aqara Precision motion sensor
def isP1()        { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.motion.ac02'] ) }     // Aqara P1 motion sensor (LED control)
def isFP1()       { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.motion.ac01'] ) }     // Aqara FP1 Presence sensor (microwave radar)
def isFP1E()      { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.sensor_occupy.agl1'] ) }     // Aqara FP1E Presence sensor
def isT1()        { if (deviceSimulation) return false else return (device.getDataValue('model') in ['lumi.motion.agl02'] ) }    // Aqara T1 motion sensor
def isLightSensorXiaomi() { return (device.getDataValue('model') in ['lumi.sen_ill.mgl01'] ) } // Mi Light Detection Sensor;
def isLightSensorAqara()  { return (device.getDataValue('model') in ['lumi.sen_ill.agl01'] ) } // T1 light intensity sensor
def isLightSensor() { return (isLightSensorXiaomi() || isLightSensorAqara()) }

private P1_LED_MODE_VALUE(mode) { mode == "Disabled" ? 0 : mode == "Enabled" ? 1 : null }
private P1_LED_MODE_NAME(value) { value == 0 ? "Disabled" : value== 1 ? "Enabled" : null }
@Field static final Map sensitivityOptions =          [ "1":"low", "2":"medium", "3":"high" ]
@Field static final Map fp1RoomStateEventOptions =        [ "0":"unoccupied", "1":"occupied" ]
@Field static final Map fp1RoomActivityEventTypeOptions = [ "0":"enter", "1":"leave" , "2":"enter (right)" , "3":"leave (left)" , "4":"enter (left)" , "5":"leave (right)" , "6":"towards", "7":"away" ]
@Field static final Map fp1ERoomActivityEventTypeOptions = [ "0":"0 - unknown", "1":"1 - unknown" , "2":"idle" , "3":"large movement" , "4":"small movement" , "5":"5 - unknown" ]
@Field static final Map approachDistanceOptions =         [ "0":"far", "1":"medium", "2":"near" ]
@Field static final Map monitoringModeOptions =           [ "0":"undirected", "1":"left_right" ]

def getSensitivityOptions() { aqaraModels[device.getDataValue('aqaraModel')]?.preferences?.motionSensitivity?.options ?: sensitivityOptions }

void parse(String description) {
    checkDriverVersion()
    if (state.rxCounter != null) state.rxCounter = state.rxCounter + 1 ; else state.rxCounter = 1
    setHealthStatusOnline()

    def descMap = [:]
    
    if (description.contains("cluster: 0000") && description.contains("attrId: FF02")) {
        //log.trace "parsing Xiaomi cluster 0xFF02"
        parseAqaraAttributeFF02( description )
        return 
    }
    
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch ( e ) {
        logWarn "parse: exception ${e} caught while parsing description: ${description} (descMap:  ${descMap})"
        return 
    }
    if (isSpammyReport(descMap)) { return }
    if (logEnable) {log.debug "${device.displayName} parse: descMap: {$descMap} parse: description is ${description}"}
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
                if (isLightSensorAqara() || isLightSensorXiaomi()) {
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
                if (true) {    // TODO: check if this is a ping() response
                    sendRttEvent()
                }
                else {
                    logDebug "Applicaiton version is ${it.value}"
                }
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

// Aqara FP1E spammy distance reports filter
boolean isSpammyReport(Map descMap) {
    if (settings?.filterSpam != true) { return false }
    if (descMap.cluster == "FCC0" && descMap.attrId in ['015F']) {
        return true
    }
    return false
}

void parseAqaraAttributeFF01(String description) {
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    parseBatteryFF01( valueHex )    
}

void parseAqaraAttributeFF02(String description) {
    def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    parseBatteryFF02( valueHex )    
}

void parseAqaraClusterFCC0(String description, Map descMap, Map it) {
    String valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
    int value = safeToInt(it.value)
    switch (it.attrId) {
        case "0005" :
            logDebug "(parseAqaraClusterFCC0) device ${it.value} button was pressed (driver version ${driverVersionAndTimeStamp()})"
            break
        case "0064" :
            logWarn "<b>received unknown report: ${P1_LED_MODE_NAME(value)}</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0065" :
            if (isFP1() || isFP1E()) { // FP1    'unoccupied':'occupied'
                logDebug "(attr 0x065) roomState (presence) is  ${fp1RoomStateEventOptions[value.toString()]} (${value})"
                roomStateEvent( fp1RoomStateEventOptions[value.toString()] )
            }
            else {     // illuminance only? for RTCGQ12LM RTCGQ14LM
                illuminanceEventLux( value )
                logDebug "<b>received illuminance only report: ${P1_LED_MODE_NAME(value)}</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            }
            break
        case "0069" : // (105) PIR sensitivity RTCGQ13LM; distance for RTCZCGQ11LM; detection (retrigger) interval for RTCGQ14LM
            if (isRTCGQ13LM()) { 
                // sensitivity
                device.updateSetting( "motionSensitivity",  [value:value.toString(), type:"enum"] )
                logDebug "<b>received PIR sensitivity report: ${sensitivityOptions[value.toString()]}</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            }
            else if (isP1()) {
                // retrigger interval
                device.updateSetting( "motionRetriggerInterval",  [value:value.toString(), type:"number"] )
                logDebug "<b>received motion retrigger interval report: ${value} s</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            }
            else if (isFP1() || isFP1E()) { // FP1
                logDebug "(0x69) <b>received approach_distance report: ${value} s</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
                device.updateSetting( "approachDistance",  [value:value.toString(), type:"enum"] )
            }
            else {
                logWarn "Received unknown device report: cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value} status=${it.status} data=${descMap.data}"
            }
            break
        case "00F7" :   // Aqara FP1E : 00F7_States (115F): {16=1, 18=0, 3=19, 5=1, 101=0, 8=278, 10=56426, 12=20} [STRUCT2]
            decodeAqaraStruct(description)
            break
        case "00FC" :   // Aqara FP1E : 00FC_Unknown (115F): false [BOOLEAN]
            logWarn "received unknown 00FC report:  (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0102" : // Retrigger interval (duration)
            value = Integer.parseInt(it.value, 16)
            device.updateSetting( "motionRetriggerInterval",  [value:value.toString(), type:"number"] )
            logDebug "<b>received motion retrigger interval report: ${value} s</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0106" : // PIR sensitivity RTCGQ13LM RTCGQ14LM RTCZCGQ11LM
        case "010C" : // (268) PIR sensitivity RTCGQ13LM RTCGQ14LM (P1) RTCZCGQ11LM; TODO: check if applicable for FP1 ? // FP1E 010C_SensorSensitivity (115F): 3 [UNSIGNED_8_BIT_INTEGER]
            device.updateSetting( "motionSensitivity",  [value:value.toString(), type:"enum"] )
            sendEvent(name: "motionSensitivity", value: sensitivityOptions[value.toString()], type: "physical")
            logDebug "(0x010C) <b>received motion sensitivity report: ${sensitivityOptions[value.toString()]}</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0112" : // Aqara P1 PIR motion Illuminance
            if (!isRTCGQ13LM()) { // filter for High Preceision sensor - no illuminance sensor!
                def rawValue = Integer.parseInt((valueHex[(2)..(3)] + valueHex[(0)..(1)]),16)
                illuminanceEventLux( rawValue )
                handleMotion( true )    // TODO !!
            }
            break
        case "0142" : // (322) FP1 RTCZCGQ11LM presence (roomState) // FP1E: 0142_SensorPresense (115F): 1 [UNSIGNED_8_BIT_INTEGER]
            logDebug "(attr. 0x0142) roomState (presence) is  ${fp1RoomStateEventOptions[value.toString()]} (${value})"
            roomStateEvent( fp1RoomStateEventOptions[value.toString()] )
            break
        case "0143" : // (323) FP1 RTCZCGQ11LM presence_event {0: 'enter', 1: 'leave', 2: 'left_enter', 3: 'right_leave', 4: 'right_enter', 5: 'left_leave', 6: 'approach', 7: 'away'}[value];  // FP1E: 0143_SensorPresenseEvent (115F): [UNSIGNED_8_BIT_INTEGER]
            presenceTypeEvent( fp1RoomActivityEventTypeOptions[value.toString()] )
            break
        case "0144" : // (324) FP1 RTCZCGQ11LM monitoring_mode
            device.updateSetting( "monitoringMode",  [value:value.toString(), type:"enum"] )    // monitoring_mode = {0: 'undirected', 1: 'left_right'}[value]
            logDebug "<b>received monitoring_mode report: ${monitoringModeOptions[value.toString()]}</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0146" : // (326) FP1 RTCZCGQ11LM approach_distance 
            device.updateSetting( "approachDistance",  [value:value.toString(), type:"enum"] )
            logDebug "(0x0146) <b>received approach_distance report: ${approachDistanceOptions[value.toString()]}</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0150" : // (336) FP1 set region event
            logDebug "(0x0150) <b>received set region report: (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0151" : // (337) FP1 region event
            Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1])
            value = HexUtils.hexStringToInt(descMap.value[2..3])        
            logDebug "(0x0151) <b>received region report:  regionId=${regionId} value=${value} (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            sendRegionEvent( regionId, value)
            break
        case "0152" : // (338) LED configuration
            device.updateSetting( "motionLED",  [value:value.toString(), type:"enum"] )
            logDebug "${device.displayName} <b>received LED configuration report: ${P1_LED_MODE_NAME(value)}</b> (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"    //P1_LED_MODE_VALUE
            break
        case "0153" : // (339) FP1 set exit region event
            logDebug "(0x0153) <b>received set exit region report: (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0154" : // (340) FP1 set interference region event
            logDebug "(0x0154) <b>received set interference region report: (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case '0155' : // (341) FP1E 0155_SensorReportPosition (115F): 0 [UNSIGNED_8_BIT_INTEGER]
            logDebug "(0x0155) <b>received FP1E SensorReportPosition report: (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0156" : // (342) FP1 set edge region event
            logDebug "(0x0156) <b>received set edge region report: (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "0157" : // (343) FP1 reset presence event // FP1E 0157_SensorResetMotion (115F): [UNSIGNED_8_BIT_INTEGER]
            logWarn "(0x0157) <b>received reset presence/motion report: (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        case "015B" :   // 015B_Custom: 600 [UNSIGNED_32_BIT_INTEGER] detection range
            value = Integer.parseInt(it.value, 16)
            logDebug "(0x015B) received detection range report: ${value} (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            detectionRangeEvent( value )
            break
        case "015F" :   // 015F_Custom: 15 [UNSIGNED_32_BIT_INTEGER] FP1E 'target_distance'
            value = Integer.parseInt(it.value, 16)
            logDebug "(0x015F) received FP1E target_distance report: ${value} (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            targetDistanceEvent( value )
            break
        case '0160' :   // FP1E frequently sent report ?? presence_event [ "0":"0 - unknown", "1":"1 - unknown" , "2":"idle" , "3":"large movement" , "4":"small movement" , "5":"5 - unknown" ]
            logDebug "(0x0160) received report: ${fp1ERoomActivityEventTypeOptions[value.toString()]} (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            presenceTypeEvent( fp1ERoomActivityEventTypeOptions[value.toString()] )
            break

        case '0006' :   // 0006_Unknown (115F): ByteArray [value=D3 45 B9 CD AC 60 DB 02 FB C5 C0 E9 41 14 B0 CC] [OCTET_STRING]
        case '0007' :   // 0007_Unknown (115F): ByteArray [value=00 00 00 00 2A FD 50 0F] [OCTET_STRING]
        case '00DF' :   // 00DF_Unknown (115F): {228=null} [STRUCT2]
        case '00E4' :   // 00E4_Unknown (115F): [UNSIGNED_32_BIT_INTEGER]
        case '00FA' :   // 00FA_Unknown (115F): false [BOOLEAN]
        case '00FE' :   // 00FE_SerialNumber (115F): 777S00072190 [CHARACTER_STRING]
        case '00FF' :   // 00FF_Unknown (115F): ByteArray [value=] [OCTET_STRING]
        case '0133' :   // 0133_Unknown (115F): 1 [UNSIGNED_8_BIT_INTEGER]
        case '0134' :   // 0134_Unknown (115F): [OCTET_STRING]
        case '0159' :   // 0159_Unknown (115F): 1 [UNSIGNED_8_BIT_INTEGER]
        case "015A" :   // FP1E RTCZCGQ13LM unknown   015A_Custom: 600 [UNSIGNED_32_BIT_INTEGER]
        case "015C" :   // 015C_Custom: 1 [UNSIGNED_8_BIT_INTEGER]
        case "015D" :   // 015D_Custom: 1 [UNSIGNED_8_BIT_INTEGER]
        case "015E" :   // 015E_Custom: 1 [UNSIGNED_8_BIT_INTEGER]
        case '0705' :   // 0705_Unknown (115F): 0 [UNSIGNED_8_BIT_INTEGER]
        case 'FFF2' :   // FFF2_Unknown (115F): 0006_AqaraCommand [FCC0_ManufacturerSpecific: mc=115F, null -> null, TID=--, Input, const1=62207, length1=134, const2=null, seq=null, integrity=null, action=null, const3=null, length2=null, raw=null] [AQARA_FFF2]
        case 'FFFD' :   // FFFD_Unknown (115F): 1 [UNSIGNED_16_BIT_INTEGER]
            logDebug "<b>received FP1E RTCZCGQ13LM unknown report: (cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value})"
            break
        default :
            logDebug "Unprocessed <b>FCC0</b> attribute report: cluster=0x${it.cluster} attrId=0x${it.attrId} value=0x${it.value} status=${it.status} data=${descMap.data}"
        break
    }
}

// Set of Region Actions
@Field static final Map<Integer, String> REGION_ACTIONS = [
    1: 'enter',
    2: 'leave',
    4: 'occupied',
    8: 'unoccupied'
]

def sendRegionEvent( regionId, value) {
    String regionEventName = "region_last_" + REGION_ACTIONS.get(value)
    def event = [
        name: regionEventName,
        value: regionId.toString(),
        //data: [buttonNumber: regionId], 
        descriptionText: "region $regionId state is ${REGION_ACTIONS.get(value)}",
        type:'physical'
    ]
    logInfo "${event.descriptionText}"
    sendEvent(event)

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
                        logDebug "on/off is ${rawValue}"
                        break
                    case 0x9b :    // consumer connected
                        logDebug "consumer connected is ${rawValue}"
                        break
                    case 0x64 :    // curtain lift or smoke/gas density; also battery percentage for Aqara curtain motor 
                        logDebug "lift % or gas density is ${rawValue}"
                        break
                    case 0x65 :    // (101) FP1 roomState (presence)
                        if (isFP1()) { // FP1 'unoccupied':'occupied'
                            logDebug "(0x65) roomState (presence) is  ${fp1RoomStateEventOptions[rawValue.toString()]} (${rawValue})"
                            roomStateEvent( fp1RoomStateEventOptions[rawValue.toString()] )  
                        }
                        else if (isFP1E()) {
                            logDebug "FP1E tag #65 is ${rawValue}"
                        }
                        else {
                            logDebug "on/off EP 2 or battery percentage is ${rawValue}"
                        }
                        break
                    case 0x66 :    // (102)    FP1 
                        if (isFP1()) {
                            if (/* FP1 firmware version  < 50) */ false ) {
                                logWarn "RTCZCGQ11LM tag 0x66 (${rawValue} )"
                                presenceTypeEvent( fp1RoomActivityEventTypeOptions[rawValue.toString()] )
                            }
                            else {
                                device.updateSetting( "motionSensitivity",  [value:rawValue.toString(), type:"enum"] )
                                logDebug "(tag 0x66) sensitivity is <b>${sensitivityOptions[rawValue.toString()]}</b> (${rawValue})"
                            }
                        }
                        break
                    case 0x67 : // (103) FP1 monitoring_mode
                        if (isFP1() || isFP1E()) {
                            logDebug "monitoring_mode is <b> ${monitoringModeOptions[rawValue.toString()]}</b> (${rawValue})"
                            device.updateSetting( "monitoringMode",  [value:rawValue.toString(), type:"enum"] )
                        }
                        else {
                            logDebug "tag 0x67 value is ${rawValue}"    // sent by T1 sensor
                        }
                        break
                    case 0x69 : // (105) 
                        if (isFP1() || isFP1E()) { // FP1
                            device.updateSetting( "approachDistance",  [value:rawValue.toString(), type:"enum"] )    // {0: 'far', 1: 'medium', 2: 'near'}
                            logDebug "approach_distance is <b>${approachDistanceOptions[rawValue.toString()]}</b> (${rawValue})"
                        }
                        else if (isRTCGQ13LM()) {
                            // payload.motion_sensitivity = {1: 'low', 2: 'medium', 3: 'high'}[value];
                            device.updateSetting( "motionSensitivity",  [value:rawValue.toString(), type:"enum"] )
                            logDebug "(tag 0x69) sensitivity is <b>${sensitivityOptions[rawValue.toString()]}</b> (${rawValue})"
                        }
                        else if (isP1()) {
                            device.updateSetting( "motionRetriggerInterval",  [value:rawValue.toString(), type:"number"] )
                            logDebug "motion retrigger interval is ${rawValue} s."
                        }
                        else {
                            logWarn "unknown device ${device.getDataValue('model')} tag=${valueHex[(i+0)..(i+1)]} dataType 0x${valueHex[(i+2)..(i+3)]} rawValue=${rawValue}"
                        }
                        break
                    case 0x6A :    // sensitivity
                        if (isFP1() || isFP1E()) {
                            logDebug "(0x6A) FP1 unknown parameter, value: ${rawValue}"
                        }
                        else {
                            device.updateSetting( "motionSensitivity",  [value:rawValue.toString(), type:"enum"] )
                            logDebug "(tag 0x6A) sensitivity is <b>${sensitivityOptions[rawValue.toString()]}</b> (${rawValue})"
                        }
                        break
                    case 0x6B :    // LED
                        if (isFP1() || isFP1E()) {
                            logDebug "(0x06B) FP1 unknown parameter, value: ${rawValue}"
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
                        String nwk = intToHexStr(rawValue as Integer, 2)
                        if (state.health == null) { state.health = [:] }
                        String oldNWK = state.health['parentNWK'] ?: 'n/a'
                        logDebug "<b>Parent NWK is ${nwk}</b>"
                        if (oldNWK != nwk || device.currentState('parentNWK')?.value != nwk) {
                            String descriptionText = "parentNWK changed from ${oldNWK} to ${nwk}"
                            state.health['parentNWK']  = nwk
                            state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1
                            sendEvent(name: "parentNWK", value: nwk, descriptionText: descriptionText, type: "digital")
                            logWarn "${descriptionText}"
                        }
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

private parseBatteryFF02( valueHex ) {
	def MsgLength = valueHex.size()
   	for (int i = 0; i < (MsgLength-3); i+=2) {
		if (valueHex[i..(i+1)] == "21") { // Search for byte preceeding battery voltage bytes
			rawValue = Integer.parseInt((valueHex[(i+4)..(i+5)] + valueHex[(i+2)..(i+3)] ),16)
			break
		}
	}
    if (rawValue == 0) {
        return
    }
	def rawVolts = rawValue / 1000
    
    voltageAndBatteryEvents(rawVolts)
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
    
    voltageAndBatteryEvents(rawVolts)
}

def voltageAndBatteryEvents( rawVolts, isDigital=false  )
{
	def minVolts = 2.5
	def maxVolts = 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText  = "Battery level is ${roundedPct} %"
	def descText2 = "Battery voltage is ${rawVolts} V"
    if (txtEnable) log.info "${device.displayName} ${descText}"
    sendEvent(name: 'batteryVoltage', value: rawVolts, unit: "V", type: "physical", descriptionText: descText2, isStateChange: true )
    sendEvent(name: 'battery', value: roundedPct, unit: "%", type:  isDigital == true ? "digital" : "physical", descriptionText: descText, isStateChange: true )    
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
            if (descMap?.data?.size() <3) {    // Mi Light Detection Sensor GZCGQ01LM : raw:catchall: 0104 0003 01 FF 0040 00 0508 01 00 0000 01 00 , profileId:0104, clusterId:0003, clusterInt:3, sourceEndpoint:01, destinationEndpoint:FF, options:0040, messageType:00, dni:0508, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:01, direction:00, data:[]]
                logDebug "received Read attribute response: cluster ${descMap.clusterId} Attributte ${attrId}, data size ${descMap?.data?.size()}"
                return
            }
        
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
            logDebug "Received Write Attribute Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            break
        case "07" : // Configure Reporting Response
            logInfo "Received Configure Reporting Response for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
            // Status: Unreportable Attribute (0x8c)
            break
        case "09" : // Command: Read Reporting Configuration Response (0x09)
            def status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00)
            def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000)
            if (status == 0) {
                def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10)
                def min = zigbee.convertHexToInt(descMap.data[6])*256 + zigbee.convertHexToInt(descMap.data[5])
                def max = zigbee.convertHexToInt(descMap.data[8])*256 + zigbee.convertHexToInt(descMap.data[7])
                def delta = 0
                if (descMap.data.size() >= 9 ) {
                    delta = zigbee.convertHexToInt(descMap.data[9])
                }
                logInfo "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribite:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}"
            }
            else {
                logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribite:${descMap.data[3]+descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0]=="00" ? 'Success' : '<b>Failure</b>'})"
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
                        logDebug "Received ZCL Default Response to Command ${descMap.data[0]} for cluster:${descMap.clusterId} , data=${descMap.data} (Status: ${descMap.data[1]=="00" ? 'Success' : '<b>Failure</b>'})"
                        break
                }
            }
            break
        default :
            logDebug "Unprocessed global command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}"
            break
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

void illuminanceEvent( rawLux ) {
    if (rawLux == 0xFFFF) {
        logWarn "ignored rawLux reading ${rawLux}"
        return
    }
	def lux = rawLux > 0 ? Math.round(Math.pow(10,(rawLux/10000))) : 0
    illuminanceEventLux( lux as Integer )
}

void illuminanceEventLux( Integer lux ) {
    if (lux == 0xFFFF) {
        logWarn "ignored lux reading ${lux}"
        return
    }
    if ( lux > 0xFFDC ) lux = 0xFFDC    // maximum value is 0xFFDC !
    handleIlluminanceEvent(lux)
}

void handleIlluminanceEvent(int illuminance, boolean isDigital=false) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.stats == null) { state.stats = [:] }
    state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 
    Map eventMap = [:]
    eventMap.name = 'illuminance'
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float)))
    eventMap.value  = illumCorrected
    eventMap.type = isDigital ? 'digital' : 'physical'
    eventMap.unit = 'lx'
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000)
    Integer minTime = settings?.illuminanceMinReportingTime ?: DEFAULT_ILLUMINANCE_MIN_TIME  // defined in commonLib
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    Integer lastIllum = device.currentValue('illuminance') ?: 0
    Integer delta = Math.abs(lastIllum - illumCorrected)
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) {
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})"
        return
    }
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports
        state.lastRx['illumTime'] = now()
        sendEvent(eventMap)
    }
    else {         // queue the event
        eventMap.type = 'delayed'
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap])
    }
}

/* groovylint-disable-next-line UnusedPrivateMethod */
private void sendDelayedIllumEvent(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
    sendEvent(eventMap)
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

def roomStateEvent( String status, isDigital=false ) {
    if (status != null) {
        def type = isDigital == true ? "digital" : "physical"
        sendEvent("name": "roomState", "value": status, "type": type)                    // isStateChange" true removed ver 1.2.0
        if (settings?.txtEnable) log.info "${device.displayName} roomState (presence) is <b>${status}</b>"
        if (status == "occupied") {
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
        sendEvent("name": "roomActivity", "value": presenceTypeEvent, "type": type)                // isStateChange" true removed ver 1.2.0
        if (settings?.txtEnable) log.info "${device.displayName} presence type is <b>${presenceTypeEvent}</b>"
        if (presenceTypeEvent in ["enter", "left_enter", "right_enter"] ) {
            handleMotion(true, isDigital=true)
        }
        else if (presenceTypeEvent in ["leave", "left_leave", "right_leave" ]) {
            handleMotion(false, isDigital=true)
        }        
    }
}

import java.math.RoundingMode

def targetDistanceEvent( Integer distance ) {
    if (distance != null) {
        BigDecimal distanceConverted = distance / 100 as BigDecimal 
        distanceConverted = distanceConverted.setScale(2, RoundingMode.HALF_UP)
        sendEvent("name": "targetDistance", "value": distanceConverted, "type": "physical")
        if (settings?.txtEnable) log.info "${device.displayName} target distance is ${distanceConverted} m"
    }
}

def detectionRangeEvent( Integer range ) {
    if (range != null) {
        BigDecimal rangeConverted = range / 100 as BigDecimal
        rangeConverted = rangeConverted.setScale(2, RoundingMode.HALF_UP)
        sendEvent("name": "detectionRange", "value": rangeConverted, "type": "physical")
        if (settings?.txtEnable) log.info "${device.displayName} detection range is ${rangeConverted} m"
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

def powerSourceEvent() {
    def value = (isFP1() || isFP1E()) ? "dc" : "battery"
    def descriptionText = (isFP1() || isFP1E()) ? "powerSource is dc/mains" : "powerSource is battery"
    sendEvent(name : "powerSource",	value : value, descriptionText: descriptionText, type: "digital")
    logInfo "${descriptionText}"
}

// called when any event was received from the Zigbee device in parse() method..
def setHealthStatusOnline() {
    if ((state.rxCounter != null) && state.rxCounter <= 2) {
        return                    // do not count the first device announcement or binding ack packet as an online presence!
    }
    sendHealthStatusEvent("online")
    state.notPresentCounter = 0
    unschedule('deviceCommandTimeout')
}

def pollPresence() { deviceHealthCheck() }

// check for device offline every 60 minutes
def deviceHealthCheck() {
    if (logEnable) log.debug "${device.displayName} deviceHealthCheck()"
    
    if (state.notPresentCounter != null) {
        state.notPresentCounter = state.notPresentCounter + 1
        if (state.notPresentCounter >= PRESENCE_COUNT_THRESHOLD) {
            sendHealthStatusEvent("offline")
            if (!(device.currentValue('motion', true) in ['inactive', '?'])) {
                if (!isLightSensor()) {
                    handleMotion(false, isDigital=true)
                    logWarn "forced motion to <b>inactive</b>"
                }
            }
        }
    }
    else {
        state.notPresentCounter = 0  
    }
    
    runIn( DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])
}

void ping() {
    logInfo 'ping...'
    scheduleCommandTimeoutCheck()
    state.pingTime = new Date().getTime()
    sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) )
}

void sendRttEvent() {
    def now = new Date().getTime()
    def timeRunning = now.toInteger() - state.pingTime?.toInteger() ?: now.toInteger()
    logInfo "Round Trip Time is ${timeRunning} (ms)"    
    sendEvent(name: "rtt", value: timeRunning, unit: "ms", type: "digital", descriptionText: "Round Trip Time is ${timeRunning} ms")    
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    runIn(delay, 'deviceCommandTimeout')
}

private void scheduleDeviceHealthCheck(int intervalMins) {
    Random rnd = new Random()
    schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping')
}

void deviceCommandTimeout() {
    if (isFP1() || isFP1E()) {
        logWarn 'no response received (device offline?)'
        sendHealthStatusEvent("offline")
        //resetState()
    }
    else {
        logDebug 'no response received (sleepy device)'
    }
}

void sendHealthStatusEvent(String value) {
    if (device.currentValue('healthStatus') != value) {
        String descriptionText = "healthStatus changed to $value"
        sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} ${descriptionText}", type: "digital")
        if (value != 'online') {
            log.warn "${device.displayName} ${descriptionText}"
        }
        else {
            log.info "${device.displayName} ${descriptionText}"
        }
    }
}

void resetPresence() {
    logInfo 'reset presence'
    //resetRegions()
    sendZigbeeCommands(zigbee.writeAttribute(0xFCC0, 0x0157, DataType.UINT8, 0x01, [mfgCode: 0x115F], 0))
}

void setWatchdogTimer() {
    boolean watchdogEnabled = (settings.stateResetInterval as Integer) > 0
    if (watchdogEnabled) {
        int seconds = (settings.stateResetInterval as int) * 60 * 60
        runIn(seconds, 'resetState')
    }
}

void refresh() {
    logInfo 'refresh...'
    if (isFP1E()) {
        sendZigbeeCommands(zigbee.readAttribute(0xFCC0, 0x015B, [mfgCode: 0x115F], 0))  // detection range
    }
    else {
        logDebug 'no refresh required'
    }
}


static String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() }

void checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "Hubitat hub model is ${getModel()}. Updating the settings from driver version ${state.driverVersion} to ${driverVersionAndTimeStamp()}"
        state.comment = COMMENT_WORKS_WITH
        if (state.lastBattery != null) state.remove("lastBattery")
        initializeVars( fullInit = false ) 
        state.motionStarted = now()
        if(device.getDataValue('aqaraModel') == null) {
            setDeviceName()
        }
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

void logsOff(){
    if (settings?.logEnable) log.info "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}


// called when preferences are saved
void updated() {
    logDebug "updated()..."
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
    if (settings?.filterSpam == true) {
        if (device.currentValue('targetDistance') != null) {    
            device.deleteCurrentState("targetDistance")
        }
    }

    // restart the healthCheck timer
    runIn( DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])

    /*
    log.warn "updated(): before: DynamicSettingsMap dynamicCommands = ${DynamicSettingsMap.get(device.id).get('dynamicCommands')}"
    if (settings?.testCommands == true) {
        DynamicSettingsMap.get(device.id).put('dynamicCommands', 'true')
    }
    else {
        DynamicSettingsMap.get(device.id).put('dynamicCommands', 'false')
    }
    log.warn "updated(): after: DynamicSettingsMap dynamicCommands = ${DynamicSettingsMap.get(device.id).get('dynamicCommands')}"
   */
    
    def value = 0
    if (isP1()) {
        if (settings?.motionLED != null ) {
            value = safeToInt( motionLED )
            if (settings?.logEnable) log.debug "${device.displayName} setting motionLED to ${motionLED}"
            cmds += zigbee.writeAttribute(0xFCC0, 0x0152, 0x20, value, [mfgCode: 0x115F], delay=200)
        }
    }
    if (isRTCGQ13LM() || isP1() || isFP1() || isFP1E()) {
        if (settings?.motionSensitivity != null && settings?.motionSensitivity != 0) {
            value = safeToInt( motionSensitivity )
            if (settings?.logEnable) log.debug "${device.displayName} setting motionSensitivity to ${sensitivityOptions[value.toString()]} (${value})"
            cmds += zigbee.writeAttribute(0xFCC0, 0x010C, 0x20, value, [mfgCode: 0x115F], delay=200)
            cmds += zigbee.readAttribute(0xFCC0, 0x010C, [mfgCode: 0x115F], delay=200)    // read it back
            if (isFP1E()) {
                log.trace "detectionRange = ${settings?.detectionRange}"
                value = (settings?.detectionRange * 100 as Integer) ?: 600
                log.trace "value = ${value}"
                if (settings?.logEnable) log.debug "${device.displayName} setting detectionRange to ${value}"
                cmds += zigbee.writeAttribute(0xFCC0, 0x015B, 0x23, value, [mfgCode: 0x115F], delay=201)    // detection range
                cmds += zigbee.readAttribute(0xFCC0, 0x015B, [mfgCode: 0x115F], delay=201)    // read it back
            }
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
        device.deleteCurrentState("battery")
    }
    //
    if (isLightSensor()) {
        cmds += configureIlluminance()
    }
    if (cmds != null && cmds != []) {
        sendZigbeeCommands( cmds )     
    }
    else {
        logDebug "no preferences configuration commands to send!"
    }
}    

// called from  initializeVars( fullInit = true)
void setDeviceName() {
    String deviceName
    def currentModelMap = null
    aqaraModels.each { k, v -> 
        //log.trace "${k}:${v}" 
        if (v.model ==  device.getDataValue('model') /*&& v.manufacturer == device.getDataValue('manufacturer')*/) {
            currentModelMap = k
            //log.trace "found ${k}"
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
    if (deviceName != NULL) {
        device.setName(deviceName)
        logInfo "device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} <b>aqaraModel ${device.getDataValue('aqaraModel')}</b> deviceName was set to ${deviceName}"
    }
    else {
        logWarn "device model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} <b>aqaraModel ${device.getDataValue('aqaraModel')}</b> was not found!"
    }
}

void initializeVars(boolean fullInit = false) {
    if (logEnable==true) { log.info "${device.displayName} InitializeVars... fullInit = ${fullInit} (driver version ${driverVersionAndTimeStamp()})" }
    if (fullInit == true) {
        state.clear()
        setDeviceName()
        state.driverVersion = driverVersionAndTimeStamp()
    }
    if (fullInit == true || state.health == null) { state.health = [:] }
    if (fullInit == true || state.rxCounter == null) { state.rxCounter = 0 }
    if (fullInit == true || state.txCounter == null) { state.txCounter = 0 }
    if (fullInit == true || state.notPresentCounter == null) { state.notPresentCounter = 0 }
    if (fullInit == true || state.motionStarted == null) { state.motionStarted = now() }
    
    if (fullInit == true || settings?.logEnable == null) { device.updateSetting("logEnable", true) }
    if (fullInit == true || settings?.txtEnable == null) { device.updateSetting("txtEnable", true) }
    if (fullInit == true || settings?.internalTemperature == null) { device.updateSetting("internalTemperature", false) }
    if (fullInit == true || settings?.motionResetTimer == null) { device.updateSetting("motionResetTimer", 30) }
    
    if (isLightSensor()) {
        if (fullInit == true || settings?.illuminanceMinReportingTime == null) { device.updateSetting("illuminanceMinReportingTime", [value: DEFAULT_ILLUMINANCE_MIN_TIME , type:"number"]) }
        if (fullInit == true || settings?.illuminanceMaxReportingTime == null) { device.updateSetting("illuminanceMaxReportingTime", [value: DEFAULT_ILLUMINANCE_MAX_TIME , type:"number"]) }
        if (fullInit == true || settings?.illuminanceThreshold == null) { device.updateSetting("illuminanceThreshold", [value: DEFAULT_ILLUMINANCE_THRESHOLD , type:"number"]) }
        if (fullInit == true || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) }
    }
    
    if (isFP1() || isFP1E()) {
        device.updateSetting("motionResetTimer", [value: 0 , type:"number"])    // no auto reset for FP1
    }
    if (isFP1E()) {
        if (fullInit == true || settings?.filterSpam == null) { device.updateSetting("filterSpam", true) }
        if (fullInit == true || settings?.detectionRange == null) { device.updateSetting('detectionRange', [value:6.00, type:'decimal']) }
    }
    if (fullInit == true || settings.tempOffset == null) { device.updateSetting("tempOffset", 0) }
    if (fullInit == true ) { powerSourceEvent() }
    if (fullInit == true ) { sendEvent(name: "parentNWK", value: "unknown", descriptionText: "parentNWK is unknown", type: "digital") }
    
    updateAqaraVersion()
}

void installed() {
    log.info "${device.displayName} installed() model ${device.getDataValue('model')} manufacturer ${device.getDataValue('manufacturer')} driver version ${driverVersionAndTimeStamp()}"
    sendHealthStatusEvent("unknown")
    aqaraBlackMagic()
}

void configure(boolean fullInit = false) {
    log.info "${device.displayName} configure...fullInit = ${fullInit} (driver version ${driverVersionAndTimeStamp()})"
    unschedule()
    initializeVars(fullInit)
    runIn( DEFAULT_POLLING_INTERVAL, "deviceHealthCheck", [overwrite: true, misfire: "ignore"])
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
void setMotion(final String mode) {
    switch (mode) {
        case "active" : 
            handleMotion(true, isDigital=true)
            if (isFP1() || isFP1E()) {
                roomStateEvent("occupied", isDigital=true)
                presenceTypeEvent("enter", isDigital=true)
            }
            break
        case "inactive" :
            handleMotion(false, isDigital=true)
            if (isFP1() || isFP1E()) {
                roomStateEvent("unoccupied", isDigital=true)
                presenceTypeEvent("leave", isDigital=true)
                resetPresence()
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

void aqaraReadAttributes() {
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
    else if (isFP1() || isFP1E()) {  // Aqara presence detector FP1 
        cmds += zigbee.readAttribute(0xFCC0, [0x010C, 0x0142, 0x0144, 0x0146], [mfgCode: 0x115F], delay=200)
    }
    else if (isLightSensorAqara()) {
        cmds += zigbee.readAttribute(0x0400, 0x0000, [mfgCode: 0x115F], delay=200)
        cmds += zigbee.readAttribute(0x0400, 0x0000, [mfgCode: 0x126E], delay=200)    // added 05/14/2023 - try both Aqara and Xiaomi codes
    }
    else if (isLightSensorXiaomi()) {
        cmds += zigbee.readAttribute(0x0400, 0x0000, [mfgCode: 0x126E], delay=201)
        cmds += zigbee.readAttribute(0x0400, 0x0000, [mfgCode: 0x115F], delay=202)
        cmds += zigbee.readAttribute(0x0400, 0x0000, [:], delay=203)
    }
    else {
        logWarn "skipped unknown device ${device.getDataValue('manufacturer')} ${device.getDataValue('model')}"
    }    
    sendZigbeeCommands( cmds )       
}

void aqaraBlackMagic() {
    List<String> cmds = []

    if (isP1()) {
        cmds += zigbee.readAttribute(0x0000, [0x0004, 0x0005], [:], delay=200)
    }
    else if (isFP1() /*|| isFP1E()*/) {
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
    else if (isFP1E()) {
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 50",]
//        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 FF 00 41 10 02 32 71 76 20 79 16 48 28 87 18 12 21 55 72 36}  {0x0104}", "delay 50",]      // FP1 write attr 0xFF 16 bytes
//        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 50 01 41 07 01 01 ff ff 00 00 ff}  {0x0104}", "delay 50",]                                 // FP1 write attr 0x0150 8 bytes
//        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 50 01 41 03 06 55 35}  {0x0104}", "delay 50",]                                             // FP1 (seq:5) write attr 0x0150 4 bytes
//        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 50 01 41 07 01 02 ff ff 00 00 ff}  {0x0104}", "delay 50",]                                 // FP1 (seq:6) write attr 0x0150 8 bytes
//        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 50 01 41 03 06 55 35}  {0x0104}", "delay 50",]                                             // FP1 (seq:7) write attr 0x0150 4 bytes
//        cmds += zigbee.writeAttribute(0xFCC0, 0x0155, 0x20, 0x01, [mfgCode: 0x115F], delay=50)                                                                                                // FP1 (seq 8) write attr 0x0155 : 1 byte 01
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 f2 ff 41 aa 74 02 44 00 9c 03 20}  {0x0104}", "delay 50",]                                 // FP1 (seq:9) write attr 0xfff2 8 bytes
        cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 f2 ff 41 aa 74 02 44 01 9b 01 20}  {0x0104}", "delay 50",]                                 // FP1 (seq:10) write attr 0xfff2 8 bytes
        //cmds += activeEndpoints()         
        logDebug "aqaraBlackMagic() for FP1"
    }
    else if (isLightSensorXiaomi() || isLightSensorAqara()) {
        cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}", "delay 50",]
        cmds += zigbee.configureReporting(0x0001, 0x0020, 0x20, 3600, 3600, null, [:], delay=208)
        cmds += zigbee.reportingConfiguration(0x0001, 0x0020, [:], 201)
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=202)
		cmds += ["zdo bind ${device.deviceNetworkId} 0x01 0x01 0x0400 {${device.zigbeeId}} {}", "delay 50",]
        cmds += configureIlluminance()
	    cmds += zigbee.readAttribute(0x0400, 0x0000, [:], delay=207)
    }
    else {
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",]
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)    // TODO: check - battery voltage
        cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay=200)    // motion sensitivity, including FP1E
    }
    //cmds += activeEndpoints()
    sendZigbeeCommands( cmds )
}

List<String> activeEndpoints() {
    List<String> cmds = []
    cmds += ["he raw ${device.deviceNetworkId} 0 0 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"] //get all the endpoints...
    String endpointIdTemp = endpointId == null ? "01" : endpointId
    cmds += ["he raw ${device.deviceNetworkId} 0 0 0x0004 {00 ${zigbee.swapOctets(device.deviceNetworkId)} $endpointIdTemp} {0x0000}"]
    return cmds    
}

// credits @thebearmay
String getModel(){
    try{
        String model = getHubVersion() // requires >=2.2.8.141
    } catch (ignore){
        try{
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res ->
                model = res.data.device.modelName
            return model
            }        
        } catch(ignore_again) {
            return ""
        }
    }
}

// credits @thebearmay
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 )
    String model = getModel()            // <modelName>Rev C-7</modelName>
    String[] tokens = model.split('-')
    String revision = tokens.last()
    return (Integer.parseInt(revision) >= minLevel)
}

void updateAqaraVersion() {
    def application = device.getDataValue("application") 
    if (application != null) {
        def str = "0.0.0_" + String.format("%04d", zigbee.convertHexToInt(application.substring(0, Math.min(application.length(), 2))));
        if (device.getDataValue("aqaraVersion") != str) {
            device.updateDataValue("aqaraVersion", str)
            logInfo "aqaraVersion set to $str"
        }
    }
}

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

List<String> configureIlluminance() {
    List<String> cmds = []
    int secondsMinLux = settings.illuminanceMinReportingTime ?: DEFAULT_ILLUMINANCE_MIN_TIME
    int secondsMaxLux = settings.illuminanceMaxReportingTime ?: DEFAULT_ILLUMINANCE_MAX_TIME
    int variance = settings.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD
    logDebug "configureIlluminance: min=${secondsMinLux} max=${secondsMaxLux} delta=${variance}"
    cmds += zigbee.configureReporting(0x0400, 0x0000, DataType.UINT16, secondsMinLux as int, secondsMaxLux as int, variance as int, [:], delay=201)
    cmds += zigbee.reportingConfiguration(0x0400, 0x0000, [:], 203)
    return cmds 
}

void test(String description ) {
        List<String> cmds = []
        cmds = zigbee.writeAttribute(0xFCC0, 0x015B, 0x23, 420, [mfgCode: 0x115F], delay=200)
        log.trace "test() ${cmds}"
        sendZigbeeCommands( cmds )

}
