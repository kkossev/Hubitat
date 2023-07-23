/**
 *  Aqara TVOC Air Quality Monitor - driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 * 	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * 	in compliance with the License. You may obtain a copy of the License at:
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * 	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * 	for the specific language governing permissions and limitations under the License.
 *
 * This driver is inspired by @w35l3y work on Tuya device driver (Edge project).
 * For a big portions of code all credits go to Jonathan Bradshaw.
 *
 * ver. 2.0.0  2023-05-08 kkossev  - Initial test version (VINDSTYRKA driver)
 * ver. 2.0.1  2023-05-27 kkossev  - another test version (Aqara TVOC Air Monitor driver)
 * ver. 2.0.2  2023-05-29 kkossev  - Just another test version (Aqara E1 thermostat driver) (not ready yet!); added 'Advanced Options'; Xiaomi cluster decoding; added temperatureScale and tVocUnit'preferences; temperature rounding bug fix
 * ver. 2.0.3  2023-06-10 kkossev  - Tuya Zigbee Fingerbot
 * ver. 2.0.4  2023-06-29 kkossev  - Tuya Zigbee Switch; Tuya Zigbee Button Dimmer; Tuya Zigbee Dimmer; Tuya Zigbee Light Sensor; 
 * ver. 2.0.5  2023-07-02 kkossev  - Tuya Zigbee Button Dimmer: added Debounce option; added VoltageToPercent option for battery; added reverseButton option; healthStatus bug fix; added  Zigbee Groups' command; added switch moode (dimmer/scene) for TS004F
 * ver. 2.0.6  2023-07-09 kkossev  - Tuya Zigbee Light Sensor: added min/max reporting time; illuminance threshold; added lastRx checkInTime, batteryTime, battCtr; added illuminanceCoeff; checkDriverVersion() bug fix;
 * ver. 2.1.0  2023-07-15 kkossev  - Libraries first introduction for the Aqara Cube T1 Pro driver; Fingerbot driver; Aqara devices: store NWK in states; aqaraVersion bug fix;
 * ver. 2.1.1  2023-07-16 kkossev  - Aqara Cube T1 Pro fixes and improvements; implemented configure() and loadAllDefaults commands;
 * ver. 2.1.2  2023-07-23 kkossev  - (dev. branch) VYNDSTIRKA library; Switch library; Fingerbot library; IR Blaster Library; fixed the exponential (3E+1) temperature representation bug;
 *
 *                                   TODO: Aqara TVOC: implement battery level/percentage 
 *                                   TODO: check  catchall: 0000 0006 00 00 0040 00 E51C 00 00 0000 00 00 01FDFF040101190000      (device object UNKNOWN_CLUSTER (0x0006) error: 0xFD)
 *                                   TODO: add timeout for auto-clearing of the Info event and rtt event after 1 minute
 *                                   TODO: add pingSuccess and pingFailure in health stats
 *                                   TODO: add clearStatistics toggle in Preferences
 *                                   TODO: skip thresholds checking for T,H,I ... on maxReportingTime
 *                                   TODO: measure PTT for on/off commands
 *                                   TODO: calculate and store the average ping RTT
 *                                   TODO: Fingerbot: add Momentary capability
 *                                   TODO: Fingerbot: touch button (on top) enable/disable option
 *                                   TODO: implement Get Device Info command
 *                                   TODO: continue the work on the 'device' capability (this project main goal!)
 *                                   TODO: state timesamps in human readable form
 *                                   TODO: parse the details of the configuration respose - cluster, min, max, delta ...
 *                                   TODO: battery min/max voltage preferences
 *                                   TODO: Configure: add custom Notes
 */

static String version() { "2.1.2" }
static String timeStamp() {"2023/07/23 9:24 AM"}

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

/*
 *    To switch between driver types :
 *        1. Copy this code
 *        2. From HE 'Drivers Code' select 'New Driver'
 *        3. Paste the copied code
 *        4. Comment out the previous device type and un-comment the new device type in these lines that define : 
 *            deviceType 
 *            DEVICE_TYPE
 *            #include libraries
 *            name (in the metadata definition section)
 *        5. Save 
 */
//deviceType = "Device"
//@Field static final String DEVICE_TYPE = "Device"
//#include kkossev.zigbeeScenes
deviceType = "AirQuality"
@Field static final String DEVICE_TYPE = "AirQuality"

//deviceType = "Fingerbot"
//@Field static final String DEVICE_TYPE = "Fingerbot"
//#include kkossev.tuyaFingerbotLib
//deviceType = "Thermostat"
//@Field static final String DEVICE_TYPE = "Thermostat"
//deviceType = "Switch"
//@Field static final String DEVICE_TYPE = "Switch"
//#include kkossev.tuyaZigbeeSwitchLib
//deviceType = "Dimmer"
//@Field static final String DEVICE_TYPE = "Dimmer"
//deviceType = "ButtonDimmer"
//@Field static final String DEVICE_TYPE = "ButtonDimmer"
//deviceType = "LightSensor"
//@Field static final String DEVICE_TYPE = "LightSensor"
//deviceType = "Bulb"
//@Field static final String DEVICE_TYPE = "Bulb"
//deviceType = "Relay"
//@Field static final String DEVICE_TYPE = "Relay"
//deviceType = "Plug"
//@Field static final String DEVICE_TYPE = "Plug"
//deviceType = "MotionSensor"
//@Field static final String DEVICE_TYPE = "MotionSensor"
//deviceType = "THSensor"
//@Field static final String DEVICE_TYPE = "THSensor"
//deviceType = "AqaraCube"
//@Field static final String DEVICE_TYPE = "AqaraCube"
//#include kkossev.aqaraCubeT1ProLib
//deviceType = "IRBlaster"
//@Field static final String DEVICE_TYPE = "IRBlaster"
//#include kkossev.irBlasterLib

@Field static final Boolean _THREE_STATE = true

metadata {
    definition (
        //name: 'Tuya Zigbee Device',
        //name: 'VINDSTYRKA Air Quality Monitor',
        name: 'Aqara TVOC Air Quality Monitor',
        //name: 'Tuya Zigbee Fingerbot',
        //name: 'Aqara E1 Thermostat',
        //name: 'Tuya Zigbee Switch',
        //name: 'Tuya Zigbee Dimmer',
        //name: 'Tuya Zigbee Button Dimmer',
        //name: 'Tuya Zigbee Light Sensor',
        //name: 'Tuya Zigbee Bulb',
        //name: 'Tuya Zigbee Relay',
        //name: 'Tuya Zigbee Plug V2',
        //name: 'Aqara Cube T1 Pro',
        //name: 'Tuya Zigbee IR Blaster',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Device%20Driver/Tuya%20Zigbee%20Device.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Device%20Driver/VINDSTYRKA%20Air%20Quality%20Monitor.groovy',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20TVOC%20Air%20Quality%20Monitor/Aqara%20TVOC%20Air%20Quality%20Monitor.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Fingerbot/Tuya%20Zigbee%20Fingerbot.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20E1%20Thermostat/Aqara%20E1%20Thermostat.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Plug/Tuya%20Zigbee%20Plug%20V2.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Switch/Tuya%20Zigbee%20Switch.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Dimmer/Tuya%20Zigbee%20Dimmer.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20TS004F/Tuya%20Zigbee%20Button%20Dimmer.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Light%20Sensor/Tuya%20Zigbee%20Light%20Sensor.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20Cube%20T1%20Pro/Aqara_Cube_T1_Pro_lib_included.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya_Zigbee_IR_Blaster/Tuya_Zigbee_IR_Blaster_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        if (_DEBUG) {
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]]
            command 'parseTest', [[name: "parseTest", type: "STRING", description: "parseTest", defaultValue : ""]]
            command "tuyaTest", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"]
            ]
        }
        
        // common capabilities for all device types
        capability 'Configuration'
        capability 'Refresh'
        capability 'Health Check'
        
        // common attributes for all device types
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        attribute "rtt", "number" 
        attribute "Info", "string"

        // common commands for all device types
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability!
        command "configure", [[name:"normally it is not needed to configure anything", type: "ENUM",   constraints: ["--- select ---"]+ConfigureOpts.keySet() as List<String>]]
        
        // deviceType specific capabilities, commands and attributes         
        if (deviceType in ["Device"]) {
            if (_DEBUG) {
                command "getAllProperties",       [[name: "Get All Properties"]]
            }
        }
        if (_DEBUG || (deviceType in ["Dimmer", "ButtonDimmer", "Switch", "Valve"])) {
            command "zigbeeGroups", [
                [name:"command", type: "ENUM",   constraints: ZigbeeGroupsOpts.options.values() as List<String>],
                [name:"value",   type: "STRING", description: "Group number", constraints: ["STRING"]]
            ]
        }        
        if (deviceType in  ["Device", "THSensor", "MotionSensor", "LightSensor", "AirQuality", "Thermostat", "AqaraCube"]) {
            capability "Sensor"
        }
        if (deviceType in  ["Device", "Switch", "Relay", "Plug", "Outlet", "Thermostat", "Fingerbot", "Dimmer", "Bulb", "IRBlaster"]) {
            capability "Actuator"
        }
        if (deviceType in  ["Device", "THSensor", "LightSensor", "MotionSensor",/* "AirQuality",*/ "Thermostat", "Fingerbot", "ButtonDimmer", "AqaraCube", "IRBlaster"]) {
            capability "Battery"
            attribute "batteryVoltage", "number"
        }
        if (deviceType in  ["Thermostat"]) {
            capability "ThermostatHeatingSetpoint"
        }
        if (deviceType in  ["Plug", "Outlet"]) {
            capability "Outlet"
        }        
        if (deviceType in  ["Device", "Switch", "Plug", "Outlet", "Dimmer", "Fingerbot"]) {
            capability "Switch"
            if (_THREE_STATE == true) {
                attribute "switch", "enum", SwitchThreeStateOpts.options.values() as List<String>
            }
        }        
        if (deviceType in ["Dimmer", "ButtonDimmer"]) {
            capability "SwitchLevel"
        }
        if (deviceType in  ["Button", "ButtonDimmer", "AqaraCube"]) {
            capability "PushableButton"
            capability "DoubleTapableButton"
            capability "HoldableButton"
   	        capability "ReleasableButton"
        }
        if (deviceType in ["ButtonDimmer"]) {
            attribute "switchMode", "enum", SwitchModeOpts.options.values() as List<String> // ["dimmer", "scene"] 
            command "switchMode", [[name: "mode*", type: "ENUM", constraints: ["--- select ---"] + SwitchModeOpts.options.values() as List<String>, description: "Select device mode"]]
        }
        if (deviceType in  ["Device", "THSensor", "AirQuality", "Thermostat"]) {
            capability "TemperatureMeasurement"
        }
        if (deviceType in  ["Device", "THSensor", "AirQuality"]) {
            capability "RelativeHumidityMeasurement"            
        }
        if (deviceType in  ["Device", "LightSensor"]) {
            capability "IlluminanceMeasurement"
        }
        if (deviceType in  ["AirQuality"]) {
            capability "AirQuality"            // Attributes: airQualityIndex - NUMBER, range:0..500
            // airQuality attribites moved to airQualityLib
        }
        if (deviceType in  ["Fingerbot"]) {
            // Fingerbot attributes moved to tuyaFingerbotLib
        }
        if (deviceType in  ["AqaraCube"]) {
            // defined in aqaraCubeT1ProLib //attribute "mode", "enum", AqaraCubeModeOpts.options.values() as List<String>
        }

        // trap for Hubitat F2 bug
        fingerprint profileId:"0104", endpointId:"F2", inClusters:"", outClusters:"", model:"unknown", manufacturer:"unknown", deviceJoinName: "Zigbee device affected by Hubitat F2 bug" 
        if (deviceType in  ["Thermostat"]) {
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0,000A,0201", outClusters:"0003,FCC0,0201", model:"lumi.airrtc.agl001", manufacturer:"LUMI", deviceJoinName: "Aqara E1 Thermostat"     // model: 'SRTS-A01'
        }
        if (deviceType in  ["LightSensor"]) {
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0001,0500", outClusters:"0019,000A", model:"TS0222", manufacturer:"_TYZB01_4mdqxxnn", deviceJoinName: "Tuya Illuminance Sensor TS0222"
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_khx7nnka", deviceJoinName: "Tuya Illuminance Sensor TS0601"
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_yi4jtqq1", deviceJoinName: "Tuya Illuminance Sensor TS0601"
        }
        if (deviceType in  ["ButtonDimmer"]) {
         	fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0004,0006,1000", outClusters:"0019,000A,0003,0004,0005,0006,0008,1000", model:"TS004F", manufacturer:"_TZ3000_xxxxxxxx", deviceJoinName: "Tuya Scene Switch TS004F"
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0004,0006,1000,0000", outClusters:"0003,0004,0005,0006,0008,1000,0019,000A", model:"TS004F", manufacturer:"_TZ3000_xxxxxxxx", deviceJoinName: "Tuya Smart Knob TS004F" //KK        
  	        fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0004,0006,1000,E001", outClusters:"0019,000A,0003,0004,0006,0008,1000", model: "TS004F", manufacturer: "_TZ3000_xxxxxxxx", deviceJoinName: "MOES Smart Button (ZT-SY-SR-MS)" // MOES ZigBee IP55 Waterproof Smart Button Scene Switch & Wireless Remote Dimmer (ZT-SY-SR-MS)
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0044", manufacturer:"_TZ3000_xxxxxxxx", deviceJoinName: "Zemismart Wireless Scene Switch"          
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0006", outClusters: "0019", model: "TS0044", manufacturer: "_TZ3000_xxxxxxxx", deviceJoinName: "Zemismart 4 Button Remote (ESW-0ZAA-EU)"                      // needs debouncing
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters: "0019,000A", model: "TS0044", manufacturer: "_TZ3000_xxxxxxxx", deviceJoinName: "Moes 4 button controller"    // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver/92823/75?u=kkossev
        }

    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: \
             '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: \
             '<i>Turns on debug logging for 24 hours.</i>'
        if (deviceType in  ["Fingerbot"]) {
            // Fingerbot settings moved 
        }
        if (advancedOptions == true || advancedOptions == false) { // groovy ...
            if (device.hasCapability("TemperatureMeasurement") || device.hasCapability("RelativeHumidityMeasurement") || device.hasCapability("IlluminanceMeasurement")) {
                input name: "minReportingTime", type: "number", title: "<b>Minimum time between reports</b>", description: "<i>Minimum reporting interval, seconds (1..300)</i>", range: "1..300", defaultValue: DEFAULT_MIN_REPORTING_TIME
                input name: "maxReportingTime", type: "number", title: "<b>Maximum time between reports</b>", description: "<i>Maximum reporting interval, seconds (120..10000)</i>", range: "120..10000", defaultValue: DEFAULT_MAX_REPORTING_TIME
            }
            if (device.hasCapability("IlluminanceMeasurement")) {
                input name: "illuminanceThreshold", type: "number", title: "<b>Illuminance Reporting Threshold</b>", description: "<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>", range: "1..255", defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD
                input name: "illuminanceCoeff", type: "decimal", title: "<b>Illuminance Correction Coefficient</b>", description: "<i>Illuminance correction coefficient, range (0.10..10.00)</i>", range: "0.10..10.00", defaultValue: 1.00
                
            }
        }
        
        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: "<i>These advanced options should be already automatically set in an optimal way for your device...</i>", defaultValue: false
        if (advancedOptions == true || advancedOptions == true) {
            input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: \
                 '<i>Method to check device online/offline status.</i>'
            //if (healthCheckMethod != null && safeToInt(healthCheckMethod.value) != 0) {
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: \
                     '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>'
            //}
            if (device.hasCapability("Battery")) {
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>'
                
            }
            if ((deviceType in  ["Switch", "Plug", "Dimmer"]) && _THREE_STATE == true) {
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>What\'s wrong with the three-state concept?</i>', defaultValue: false
            }
            if (deviceType in  ["Button", "ButtonDimmer"]) {
                input name: "reverseButton", type: "bool", title: "<b>Reverse button order</b>", defaultValue: true, description: '<i>Switches button order </i>'
                input name: 'debounce', type: 'enum', title: '<b>Debouncing</b>', options: DebounceOpts.options, defaultValue: DebounceOpts.defaultValue, required: true, description: '<i>Debouncing options.</i>'
            }
        }
    }
}

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver
@Field static final Integer REFRESH_TIMER = 5000             // refresh time in miliseconds
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events 
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored
@Field static final String  UNKNOWN = "UNKNOWN"
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod
    defaultValue: 1,
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
]
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval
    defaultValue: 240,
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours']
]
@Field static final Map SwitchThreeStateOpts = [
    defaultValue: 0,
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure']
]
@Field static final Map DebounceOpts = [
    defaultValue: 1000,
    options     : [0: 'disabled', 800: '0.8 seconds', 1000: '1.0 seconds', 1200: '1.2 seconds', 1500: '1.5 seconds', 2000: '2.0 seconds',]
]
@Field static final Map ZigbeeGroupsOptsDebug = [
    defaultValue: 0,
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying']
]
@Field static final Map ZigbeeGroupsOpts = [
    defaultValue: 0,
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups']
]
/*
@Field static final Map ConfigureOpts = [
    defaultValue: 0,
    options     : [0: 'LOAD ALL DEFAULTS', 1: 'Configure the device only', 2: 'Delete All Preferences', 3: 'Delete All Current States', 4:'Delete All Scheduled Jobs',\
                   5:'Delete All State Variables',  6:'Delete All Child Devices']
]
*/
@Field static final Map SwitchModeOpts = [
    defaultValue: 1,
    options     : [0: 'dimmer', 1: 'scene']
]


def isChattyDeviceReport(description)  {return false /*(description?.contains("cluster: FC7E")) */}
def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] }
def isAqaraTVOC()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] }
def isAqaraTRV()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] }
def isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] }
def isFingerbot()  { (device?.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3210_dse8ogfy'] }
def isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] }

/**
 * Parse Zigbee message
 * @param description Zigbee message in hex format
 */
void parse(final String description) {
    checkDriverVersion()
    if (!isChattyDeviceReport(description)) { logDebug "parse: ${description}" }
    if (state.stats != null) state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 else state.stats=[:]
    unschedule('deviceCommandTimeout')
    setHealthStatusOnline()
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {
        return
    }        
    final Map descMap = zigbee.parseDescriptionAsMap(description)
    
    if (descMap.profileId == '0000') {
        parseZdoClusters(descMap)
        return
    }
    if (descMap.isClusterSpecific == false) {
        parseGeneralCommandResponse(descMap)
        return
    }
    if (!isChattyDeviceReport(description)) {logDebug "descMap = ${descMap}"}
    //
    final String clusterName = clusterLookup(descMap.clusterInt)
    final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : ''
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute }

    switch (descMap.clusterInt as Integer) {
        case zigbee.BASIC_CLUSTER:                          // 0x0000
            parseBasicCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) }
            break
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001
            parsePowerCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) }
            break
        case zigbee.GROUPS_CLUSTER:
            parseGroupsCluster(descMap)
            descMap.remove('additionalAttrs')?.each {final Map map -> parseGroupsCluster(descMap + map) }
            break
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006
            parseOnOffCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) }
            break
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008
            parseLevelControlCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) }
            break
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro
            parseAnalogInputCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) }
            break
        case 0x0012 :                                       // Aqara Cube - Multistate Input
            parseMultistateInputCluster(descMap)
            break
        case 0x0201 :                                       // Aqara E1 TRV 
            parseThermostatCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) }
            break
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400
            parseIlluminanceCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) }
            break
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402
            parseTemperatureCluster(descMap)
            break
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405
            parseHumidityCluster(descMap)
            break
        case 0x042A :                                       // pm2.5
            parsePm25Cluster(descMap)
            break
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER:
            parseElectricalMeasureCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) }
            break
        case zigbee.METERING_CLUSTER:
            parseMeteringCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) }
            break
        case 0xEF00 :                                       // Tuya famous cluster
            parseTuyaCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) }
            break
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf
            parseAirQualityIndexCluster(descMap)
            break
        case XIAOMI_CLUSTER_ID :                            // 0xFCC0 Xiaomi cluster
            parseXiaomiCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) }
            break
        default:
            if (settings.logEnable) {
                logWarn "zigbee received unknown message cluster:${descMap.clusterInt as Integer} (${descMap})"
            }
            break
    }

}

/**
 * ZDO (Zigbee Data Object) Clusters Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseZdoClusters(final Map descMap) {
    final Integer clusterId = descMap.clusterInt as Integer
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})"
    final String statusHex = ((List)descMap.data)[1]
    final Integer statusCode = hexStrToUnsignedInt(statusHex)
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}"
    if (statusCode > 0x00) {
        logWarn "zigbee received device object ${clusterName} error: ${statusName}"
    } 
    else {
        logDebug "zigbee received device object ${clusterName} success: ${descMap.data}"
    }
}

/**
 * Zigbee General Command Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseGeneralCommandResponse(final Map descMap) {
    final int commandId = hexStrToUnsignedInt(descMap.command)
    switch (commandId) {
        case 0x01: // read attribute response
            parseReadAttributeResponse(descMap)
            break
        case 0x04: // write attribute response
            parseWriteAttributeResponse(descMap)
            break
        case 0x07: // configure reporting response
            parseConfigureResponse(descMap)
            break
        case 0x09: // read reporting configuration response
            parseReadReportingConfigResponse(descMap)
            break
        case 0x0B: // default command response
            parseDefaultCommandResponse(descMap)
            break
        default:
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})"
            final String clusterName = clusterLookup(descMap.clusterInt)
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data
            final int statusCode = hexStrToUnsignedInt(status)
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}"
            if (statusCode > 0x00) {
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}"
            } else if (settings.logEnable) {
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}"
            }
            break
    }
}

/**
 * Zigbee Read Attribute Response Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseReadAttributeResponse(final Map descMap) {
    final List<String> data = descMap.data as List<String>
    final String attribute = data[1] + data[0]
    final int statusCode = hexStrToUnsignedInt(data[2])
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}"
    if (statusCode > 0x00) {
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}"
    }
    else {
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}"
    }
}

/**
 * Zigbee Write Attribute Response Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseWriteAttributeResponse(final Map descMap) {
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data
    final int statusCode = hexStrToUnsignedInt(data)
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}"
    if (statusCode > 0x00) {
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}"
    }
    else {
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}"
    }
}

/**
 * Zigbee Configure Reporting Response Parsing
 * @param descMap Zigbee message in parsed map format
 */

void parseConfigureResponse(final Map descMap) {
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ...
    final String status = ((List)descMap.data).first()
    final int statusCode = hexStrToUnsignedInt(status)
    if (statusCode == 0x00 && settings.enableReporting != false) {
        state.reportingEnabled = true
    }
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}"
    if (statusCode > 0x00) {
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}"
    } else {
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}"
    }
}

/**
 * Zigbee Default Command Response Parsing
 * @param descMap Zigbee message in parsed map format
 */
void parseDefaultCommandResponse(final Map descMap) {
    final List<String> data = descMap.data as List<String>
    final String commandId = data[0]
    final int statusCode = hexStrToUnsignedInt(data[1])
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}"
    if (statusCode > 0x00) {
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}"
    } else {
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}"
    }
}


// Zigbee Attribute IDs
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602
@Field static final int AC_FREQUENCY_ID = 0x0300
@Field static final int AC_POWER_DIVISOR_ID = 0x0605
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600
@Field static final int ACTIVE_POWER_ID = 0x050B
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000
@Field static final int FIRMWARE_VERSION_ID = 0x4000
@Field static final int PING_ATTR_ID = 0x01
@Field static final int POWER_ON_OFF_ID = 0x0000
@Field static final int POWER_RESTORE_ID = 0x4003
@Field static final int RMS_CURRENT_ID = 0x0508
@Field static final int RMS_VOLTAGE_ID = 0x0505

@Field static final Map<Integer, String> ZigbeeStatusEnum = [
    0x00: 'Success',
    0x01: 'Failure',
    0x02: 'Not Authorized',
    0x80: 'Malformed Command',
    0x81: 'Unsupported COMMAND',
    0x85: 'Invalid Field',
    0x86: 'Unsupported Attribute',
    0x87: 'Invalid Value',
    0x88: 'Read Only',
    0x89: 'Insufficient Space',
    0x8A: 'Duplicate Exists',
    0x8B: 'Not Found',
    0x8C: 'Unreportable Attribute',
    0x8D: 'Invalid Data Type',
    0x8E: 'Invalid Selector',
    0x94: 'Time out',
    0x9A: 'Notification Pending',
    0xC3: 'Unsupported Cluster'
]

@Field static final Map<Integer, String> ZdoClusterEnum = [
    0x0013: 'Device announce',
    0x8004: 'Simple Descriptor Response',
    0x8005: 'Active Endpoints Response',
    0x801D: 'Extended Simple Descriptor Response',
    0x801E: 'Extended Active Endpoint Response',
    0x8021: 'Bind Response',
    0x8022: 'Unbind Response',
    0x8023: 'Bind Register Response',
]

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [
    0x00: 'Read Attributes',
    0x01: 'Read Attributes Response',
    0x02: 'Write Attributes',
    0x03: 'Write Attributes Undivided',
    0x04: 'Write Attributes Response',
    0x05: 'Write Attributes No Response',
    0x06: 'Configure Reporting',
    0x07: 'Configure Reporting Response',
    0x08: 'Read Reporting Configuration',
    0x09: 'Read Reporting Configuration Response',
    0x0A: 'Report Attributes',
    0x0B: 'Default Response',
    0x0C: 'Discover Attributes',
    0x0D: 'Discover Attributes Response',
    0x0E: 'Read Attributes Structured',
    0x0F: 'Write Attributes Structured',
    0x10: 'Write Attributes Structured Response',
    0x11: 'Discover Commands Received',
    0x12: 'Discover Commands Received Response',
    0x13: 'Discover Commands Generated',
    0x14: 'Discover Commands Generated Response',
    0x15: 'Discover Attributes Extended',
    0x16: 'Discover Attributes Extended Response'
]


/*
 * -----------------------------------------------------------------------------
 * Xiaomi cluster 0xFCC0 parser.
 * -----------------------------------------------------------------------------
 */
@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0

// Zigbee Attributes
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144
@Field static final int MODEL_ATTR_ID = 0x05
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143
@Field static final int PRESENCE_ATTR_ID = 0x0142
@Field static final int REGION_EVENT_ATTR_ID = 0x0151
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154
@Field static final int SET_REGION_ATTR_ID = 0x0150
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ]

// Xiaomi Tags
@Field static final int DIRECTION_MODE_TAG_ID = 0x67
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66
@Field static final int SWBUILD_TAG_ID = 0x08
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66
@Field static final int PRESENCE_TAG_ID = 0x65


void parseXiaomiCluster(final Map descMap) {
    if (settings.logEnable) {
        //log.trace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    }

    switch (descMap.attrInt as Integer) {
        case 0x0009:                      // Aqara Cube T1 Pro
            if (DEVICE_TYPE in  ["AqaraCube"]) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" }
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" }
            break
        case 0x00FC:                      // FP1
            log.info "unknown attribute - resetting?"
            break
        case PRESENCE_ATTR_ID:            // 0x0142 FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            parseXiaomiClusterPresence(value)
            break
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            parseXiaomiClusterPresenceAction(value)
            break
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1
            // Region events can be sent fast and furious so buffer them
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1])
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3])
            if (settings.logEnable) {
                log.debug "xiaomi: region ${regionId} action is ${value}"
            }
            if (device.currentValue("region${regionId}") != null) {
                RegionUpdateBuffer.get(device.id).put(regionId, value)
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions')
            }
            break
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})"
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum'])
            break
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})"
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum'])
            break
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1
            final Integer value = hexStrToUnsignedInt(descMap.value)
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})"
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum'])
            break
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) }
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" }
            break
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5)
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) }
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" }
            break
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
            parseXiaomiClusterTags(tags)
            if (isAqaraCube()) {
                sendZigbeeCommands(refreshAqaraCube())
            }
            break
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1 
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value)
            if (rawData.size() == 24 && settings.enableDistanceDirection) {
                final int degrees = rawData[19]
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff)
                if (settings.logEnable) {
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm"
                }
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ])
            }
            break
        case 0x0271:    // result['system_mode'] = {1: 'heat', 0: 'off'}[value];
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "system_mode raw = ${value}"
            break;
        case 0x0272:    // result['preset'] = {2: 'away', 1: 'auto', 0: 'manual'}[value];
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "preset raw = ${value}"
            break;
        case 0x0273:    // result['window_detection'] = {1: 'ON', 0: 'OFF'}[value];
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "window_detection raw = ${value}"
            break;
        case 0x0274:    // result['valve_detection'] = {1: 'ON', 0: 'OFF'}[value];
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "valve_detection raw = ${value}"
            break;
        case 0x0275:    // result['valve_alarm'] = {1: true, 0: false}[value];
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "valve_alarm raw = ${value}"
            break;
        case 0x0277:    // result['child_lock'] = {1: 'LOCK', 0: 'UNLOCK'}[value];
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "child_lock raw = ${value}"
            break;
        case 0x0279:    // result['away_preset_temperature'] = (value / 100).toFixed(1);
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "away_preset_temperature raw = ${value}"
            break;
        case 0x027a:    // result['window_open'] = {1: true, 0: false}[value];
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "window_open raw = ${value}"
            break;
        case 0x027b:    // result['calibrated'] = {1: true, 0: false}[value];
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "calibrated raw = ${value}"
            break;
        case 0x0276:    // unknown
        case 0x027c:    // unknown
        case 0x027d:    // unknown
        case 0x0280:    // unknown
        case 0xfff2:    // unknown
        case 0x00ff:    // unknown
        case 0x00f7:    // unknown
        case 0xfff2:    // unknown
            try {
                final Integer value = hexStrToUnsignedInt(descMap.value)
                logDebug "Aqara E1 TRV unknown attribute ${descMap.attrInt} value raw = ${value}"
            }
            catch (e) {
                logWarn "exception caught while processing Aqara E1 TRV unknown attribute ${descMap.attrInt} descMap.value = ${descMap.value}"
            }
            break;
        case 0x027e:    // result['sensor'] = {1: 'external', 0: 'internal'}[value];
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "sensor raw = ${value}"
            break;
        case 0x040a:    // E1 battery
            final Integer value = hexStrToUnsignedInt(descMap.value)
            logInfo "battery raw = ${value}"
            break
        case 0x00FF:
            // unknown
            break
        default:
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}


void parseXiaomiClusterTags(final Map<Integer, Object> tags) {
    tags.each { final Integer tag, final Object value ->
        switch (tag) {
            case 0x01:    // battery voltage
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})"
                break
            case 0x03:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;"
                break
            case 0x05:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}"
                break
            case 0x06:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}"
                break
            case 0x08:            // SWBUILD_TAG_ID:
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0')
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})"
                device.updateDataValue("aqaraVersion", swBuild)
                break
            case 0x0a:
                String nwk = intToHexStr(value as Integer,2)
                if (state.health == null) { state.health = [:] }
                String oldNWK = state.health['parentNWK'] ?: 'n/a'
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>"
                if (oldNWK != nwk ) {
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}"
                    state.health['parentNWK']  = nwk
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1
                }
                break
            case 0x0b:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}"
                break
            case 0x64:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value/100} (raw ${value})"    // Aqara TVOC
                // TODO - also smoke gas/density if UINT !
                break
            case 0x65:
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value/100} (raw ${value})" }    // Aqara TVOC
                break
            case 0x66:
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }
                else if (isAqaraTVOC()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb)
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } 
                break
            case 0x67:
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }    
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: 
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1]
                break
            case 0x69:
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }
                break
            case 0x6a:
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" }
                break
            case 0x6b:
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" }
                break
            case 0x95:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}"
                break
            case 0x96:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}"
                break
            case 0x97:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}"
                break
            case 0x98:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}"
                break
            case 0x9b:
                if (isAqaraCube()) { 
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" 
                    sendAqaraCubeOperationModeEvent(value as int)
                }
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" }
                break
            default:
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
        }
    }
}

/**
 *  Reads a specified number of little-endian bytes from a given
 *  ByteArrayInputStream and returns a BigInteger.
 */
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) {
    final byte[] byteArr = new byte[length]
    stream.read(byteArr, 0, length)
    BigInteger bigInt = BigInteger.ZERO
    for (int i = byteArr.length - 1; i >= 0; i--) {
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i)))
    }
    return bigInt
}

/**
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and
 *  returns a map of decoded tag number and value pairs where the value is either a
 *  BigInteger for fixed values or a String for variable length.
 */
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) {
    final Map<Integer, Object> results = [:]
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString)
    new ByteArrayInputStream(bytes).withCloseable { final stream ->
        while (stream.available() > 2) {
            int tag = stream.read()
            int dataType = stream.read()
            Object value
            if (DataType.isDiscrete(dataType)) {
                int length = stream.read()
                byte[] byteArr = new byte[length]
                stream.read(byteArr, 0, length)
                value = new String(byteArr)
            } else {
                int length = DataType.getLength(dataType)
                value = readBigIntegerBytes(stream, length)
            }
            results[tag] = value
        }
    }
    return results
}

/*
 * -----------------------------------------------------------------------------
 * Standard clusters reporting handlers
 * -----------------------------------------------------------------------------
*/
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']]

/**
 * Zigbee Basic Cluster Parsing  0x0000
 * @param descMap Zigbee message in parsed map format
 */
void parseBasicCluster(final Map descMap) {
    def now = new Date().getTime()
    state.lastRx["checkInTime"] = now
    switch (descMap.attrInt as Integer) {
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism
            logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})"
            if (state.lastTx == null) state.lastTx = [:]
            def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: '0').toInteger()
            if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) {
                sendRttEvent()
            }
            break
        case 0x0004:
            logDebug "received device manufacturer ${descMap?.value}"
            break
        case 0x0005:
            logDebug "received device model ${descMap?.value}"
            break
        case 0x0007:
            def powerSourceReported = powerSourceOpts.options[descMap?.value as int]
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})"
            //powerSourceEvent( powerSourceReported )
            break
        case 0xFFDF:
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})"
            break
        case 0xFFE2:
            logDebug "Tuya check-in (AppVersion=${descMap?.value})"
            break
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] :
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}"
            break
        case 0xFFFE:
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}"
            break
        case FIRMWARE_VERSION_ID:    // 0x4000
            final String version = descMap.value ?: 'unknown'
            log.info "device firmware version is ${version}"
            updateDataValue('softwareBuild', version)
            break
        default:
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

/*
 * -----------------------------------------------------------------------------
 * power cluster            0x0001
 * -----------------------------------------------------------------------------
*/
void parsePowerCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    if (descMap.attrId in ["0020", "0021"]) {
        state.lastRx["batteryTime"] = new Date().getTime()
        state.stats["battCtr"] = (state.stats["battCtr"] ?: 0 ) + 1
    }

    final long rawValue = hexStrToUnsignedInt(descMap.value)
    if (descMap.attrId == "0020") {
        sendBatteryVoltageEvent(rawValue)
        if ((settings.voltageToPercent ?: false) == true) {
            sendBatteryVoltageEvent(rawValue, convertToPercent=true)
        }
    }
    else if (descMap.attrId == "0021") {
        sendBatteryPercentageEvent(rawValue * 2)    
    }
    else {
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

def sendBatteryVoltageEvent(rawValue, Boolean convertToPercent=false) {
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V"
    def result = [:]
    def volts = rawValue / 10
    if (!(rawValue == 0 || rawValue == 255)) {
        def minVolts = 2.2
        def maxVolts = 3.2
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        if (roundedPct <= 0) roundedPct = 1
        if (roundedPct >100) roundedPct = 100
        if (convertToPercent == true) {
            result.value = Math.min(100, roundedPct)
            result.name = 'battery'
            result.unit  = '%'
            result.descriptionText = "battery is ${roundedPct} %"
        }
        else {
            result.value = volts
            result.name = 'batteryVoltage'
            result.unit  = 'V'
            result.descriptionText = "battery is ${volts} Volts"
        }
        result.type = 'physical'
        result.isStateChange = true
        logInfo "${result.descriptionText}"
        sendEvent(result)
    }
    else {
        logWarn "ignoring BatteryResult(${rawValue})"
    }    
}

def sendBatteryPercentageEvent( batteryPercent, isDigital=false ) {
    def map = [:]
    map.name = 'battery'
    map.timeStamp = now()
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int)
    map.unit  = '%'
    map.type = isDigital ? 'digital' : 'physical'    
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}"
    map.isStateChange = true
    // 
    def latestBatteryEvent = device.latestState('battery', skipCache=true)
    def latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now()
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}"
    def timeDiff = ((now() - latestBatteryEventTime) / 1000) as int
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) {
        // send it now!
        sendDelayedBatteryPercentageEvent(map)
    }
    else {
        def delayedTime = (settings?.batteryDelay as int) - timeDiff
        map.delayed = delayedTime
        map.descriptionText += " [delayed ${map.delayed} seconds]"
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds"
        runIn( delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map])
    }
}

private void sendDelayedBatteryPercentageEvent(Map map) {
    logInfo "${map.descriptionText}"
    //map.each {log.trace "$it"}
    sendEvent(map)
}

private void sendDelayedBatteryVoltageEvent(Map map) {
    logInfo "${map.descriptionText}"
    //map.each {log.trace "$it"}
    sendEvent(map)
}

/*
 * -----------------------------------------------------------------------------
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts
 * -----------------------------------------------------------------------------
*/


void parseGroupsCluster(final Map descMap) {
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]]
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}"
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:]    
    switch (descMap.command as Integer) {
        case 0x00: // Add group    0x0001 – 0xfff7
            final List<String> data = descMap.data as List<String>
            final int statusCode = hexStrToUnsignedInt(data[0])
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}"
            final String groupId = data[2] + data[1]
            final int groupIdInt = hexStrToUnsignedInt(groupId)
            if (statusCode > 0x00) {
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>"
            }
            else {
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}"
                // add the group to state.zigbeeGroups['groups'] if not exist
                int groupCount = state.zigbeeGroups['groups'].size()
                for (int i=0; i<groupCount; i++ ) {
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) {
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist"
                        return
                    }
                }
                state.zigbeeGroups['groups'].add(groupIdInt)
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt,4)})"
                state.zigbeeGroups['groups'].sort()
            }
            break
        case 0x01: // View group
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group.
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})"
            final List<String> data = descMap.data as List<String>
            final int statusCode = hexStrToUnsignedInt(data[0])
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}"
            final String groupId = data[2] + data[1]
            final int groupIdInt = hexStrToUnsignedInt(groupId)
            if (statusCode > 0x00) {
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}"
            }
            else {
                logInfo "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}"
            }
            break
        case 0x02: // Get group membership
            final List<String> data = descMap.data as List<String>
            final int capacity = hexStrToUnsignedInt(data[0])
            final int groupCount = hexStrToUnsignedInt(data[1])
            final Set<String> groups = []
            for (int i = 0; i < groupCount; i++) {
                int pos = (i * 2) + 2
                String group = data[pos + 1] + data[pos]
                groups.add(hexStrToUnsignedInt(group))
            }
            state.zigbeeGroups['groups'] = groups
            state.zigbeeGroups['capacity'] = capacity
            logInfo "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}"
            break
        case 0x03: // Remove group
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})"
            final List<String> data = descMap.data as List<String>
            final int statusCode = hexStrToUnsignedInt(data[0])
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}"
            final String groupId = data[2] + data[1]
            final int groupIdInt = hexStrToUnsignedInt(groupId)
            if (statusCode > 0x00) {
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}"
            }
            else {
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}"
            }
            // remove it from the states, even if status code was 'Not Found'
            def index = state.zigbeeGroups['groups'].indexOf(groupIdInt)
            if (index >= 0) {
                state.zigbeeGroups['groups'].remove(index)
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed"
            }
            break
        case 0x04: //Remove all groups
            logInfo "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}"
            logWarn "not implemented!"
            break
        case 0x05: // Add group if identifying
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). 
            logInfo "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}"
            logWarn "not implemented!"
            break
        default:
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})"
            break
    }
}

List<String> addGroupMembership(groupNr) {
    List<String> cmds = []
    final Integer group = safeToInt(groupNr)
    if (group < 1 || group > 0xFFF7) {
        logWarn "addGroupMembership: invalid group ${groupNr}"
        return
    }
    final String groupHex = DataType.pack(group, DataType.UINT16, true)
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00")
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}"
    return cmds
}

List<String> viewGroupMembership(groupNr) {
    List<String> cmds = []
    final Integer group = safeToInt(groupNr)
    final String groupHex = DataType.pack(group, DataType.UINT16, true)
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00")
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}"
    return cmds
}

List<String> getGroupMembership(dummy) {
    List<String> cmds = []
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, "00")
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}"
    return cmds
}

List<String> removeGroupMembership(groupNr) {
    List<String> cmds = []
    final Integer group = safeToInt(groupNr)
    if (group < 1 || group > 0xFFF7) {
        logWarn "removeGroupMembership: invalid group ${groupNr}"
        return
    }
    final String groupHex = DataType.pack(group, DataType.UINT16, true)
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00")
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}"
    return cmds
}

List<String> removeAllGroups(groupNr) {
    List<String> cmds = []
    final Integer group = safeToInt(groupNr)
    final String groupHex = DataType.pack(group, DataType.UINT16, true)
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00")
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}"
    return cmds
}

List<String> notImplementedGroups(groupNr) {
    List<String> cmds = []
    final Integer group = safeToInt(groupNr)
    final String groupHex = DataType.pack(group, DataType.UINT16, true)
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}"
    return cmds
}

@Field static final Map GroupCommandsMap = [
    "--- select ---"           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'GroupCommandsHelp'],
    "Add group"                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'],
    "View group"               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'],
    "Get group membership"     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'],
    "Remove group"             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'],
    "Remove all groups"        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'],
    "Add group if identifying" : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups']
]
/*
@Field static final Map ZigbeeGroupsOpts = [
    defaultValue: 0,
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying']
]
*/

def zigbeeGroups( command=null, par=null )
{
    logInfo "executing command \'${command}\', parameter ${par}"
    ArrayList<String> cmds = []
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:]
    if (state.zigbeeGroups['groups'] == null) state.zigbeeGroups['groups'] = []
    def value
    Boolean validated = false
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) {
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}"
        return
    }
    value = GroupCommandsMap[command]?.type == "number" ? safeToInt(par, -1) : 0
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) validated = true
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) {
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} "
        return
    }
    //
    def func
   // try {
        func = GroupCommandsMap[command]?.function
        def type = GroupCommandsMap[command]?.type
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!!
        cmds = "$func"(value)
 //   }
//    catch (e) {
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)"
//        return
//    }

    logDebug "executed <b>$func</b>(<b>$value</b>)"
    sendZigbeeCommands( cmds )
}

def GroupCommandsHelp( val ) {
    logWarn "GroupCommands: select one of the commands in this list!"             
}

/*
 * -----------------------------------------------------------------------------
 * on/off cluster            0x0006
 * -----------------------------------------------------------------------------
*/

void parseOnOffCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.command in ["FC", "FD"]) {
        processTS004Fcommand(descMap)
    }
    else if (descMap.attrId == "0000") {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        final long rawValue = hexStrToUnsignedInt(descMap.value)
        sendSwitchEvent(rawValue)
    }
    else if (descMap.attrId == "8004") {
        processTS004Fmode(descMap)
    }
    else if (descMap.attrId in ["4000", "4001", "4002", "4004", "8000", "8001", "8002", "8003"]) {
        parseOnOffAttributes(descMap)
    }
    else {
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}"
    }
}

def clearIsDigital()        { state.states["isDigital"] = false }
def switchDebouncingClear() { state.states["debounce"]  = false }
def isRefreshRequestClear() { state.states["isRefresh"] = false }

def off() {
    if ((settings?.alwaysOn ?: false) == true) {
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!"
        return
    }
    if (state.states == null) { state.states = [:] }
    state.states["isDigital"] = true
    logDebug "Switching ${device.displayName} Off"
    def cmds = zigbee.off()
    /*
    if (device.getDataValue("model") == "HY0105") {
        cmds += zigbee.command(0x0006, 0x00, "", [destEndpoint: 0x02])
    }
        else if (state.model == "TS0601") {
            if (isDinRail() || isRTXCircuitBreaker()) {
                cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "00")
            }
            else {
                cmds = zigbee.command(0xEF00, 0x0, "00010101000100")
            }
        }
        else if (isHEProblematic()) {
            cmds = ["he cmd 0x${device.deviceNetworkId}  0x01 0x0006 0 {}","delay 200"]
            logWarn "isHEProblematic() : sending off() : ${cmds}"
        }
        else if (device.endpointId == "F2") {
            cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0 {}","delay 200"]
        }
*/
    if (_THREE_STATE == true && settings?.threeStateEnable == true) {
        if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) {
            runIn(1, 'refresh',  [overwrite: true])
        }
        def value = SwitchThreeStateOpts.options[2]    // 'switching_on'
        def descriptionText = "${value} (2)"
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true)
        logInfo "${descriptionText}"
    }
    else {
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}"
    }
    
    
    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    sendZigbeeCommands(cmds)
}

def on() {
    if (state.states == null) { state.states = [:] }
    state.states["isDigital"] = true
    logDebug "Switching ${device.displayName} On"
    def cmds = zigbee.on()
/*
    if (device.getDataValue("model") == "HY0105") {
        cmds += zigbee.command(0x0006, 0x01, "", [destEndpoint: 0x02])
    }    
    else if (state.model == "TS0601") {
        if (isDinRail() || isRTXCircuitBreaker()) {
            cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "01")
        }
        else {
            cmds = zigbee.command(0xEF00, 0x0, "00010101000101")
        }
    }
    else if (isHEProblematic()) {
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"]
        logWarn "isHEProblematic() : sending off() : ${cmds}"
    }
    else if (device.endpointId == "F2") {
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"]
    }
*/
    if (_THREE_STATE == true && settings?.threeStateEnable == true) {
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on' ) {
            runIn(1, 'refresh',  [overwrite: true])
        }
        def value = SwitchThreeStateOpts.options[3]    // 'switching_on'
        def descriptionText = "${value} (3)"
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true)
        logInfo "${descriptionText}"
    }
    else {
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}"
    }
    
    
    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    sendZigbeeCommands(cmds)
}

def sendSwitchEvent( switchValue ) {
    def value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown'
    def map = [:] 
    boolean bWasChange = false
    boolean debounce   = state.states["debounce"] ?: false
    def lastSwitch = state.states["lastSwitch"] ?: "unknown"
    if (debounce == true && value == lastSwitch) {    // some devices send only catchall events, some only readattr reports, but some will fire both...
        logDebug "Ignored duplicated switch event ${value}"
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])
        return null
    }
    else {
        //log.trace "value=${value}  lastSwitch=${state.states['lastSwitch']}"
    }
    def isDigital = state.states["isDigital"]
    map.type = isDigital == true ? "digital" : "physical"
    if (lastSwitch != value ) {
        bWasChange = true
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>"
        state.states["debounce"]   = true
        state.states["lastSwitch"] = value
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])        
    }
    else {
        state.states["debounce"] = true
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])     
    }
        
    map.name = "switch"
    map.value = value
    boolean isRefresh = state.states["isRefresh"] ?: false
    if (isRefresh == true) {
        map.descriptionText = "${device.displayName} is ${value} [Refresh]"
        map.isStateChange = true
    }
    else {
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]"
    }
    logInfo "${map.descriptionText}"
    sendEvent(map)
    clearIsDigital()
}

@Field static final Map powerOnBehaviourOptions = [   
    '0': 'switch off',
    '1': 'switch on',
    '2': 'switch last state'
]

@Field static final Map switchTypeOptions = [   
    '0': 'toggle',
    '1': 'state',
    '2': 'momentary'
]


boolean isTuyaE00xCluster( String description )
{
    if(description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) {
        return false 
    }
    // try to parse ...
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..."
    def descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
        logDebug "TuyaE00xCluster Desc Map: ${descMap}"
    }
    catch ( e ) {
        logDebug "<b>exception</b> caught while parsing description:  ${description}"
        logDebug "TuyaE00xCluster Desc Map: ${descMap}"
        // cluster E001 is the one that is generating exceptions...
        return true
    }

    if (descMap.cluster == "E000" && descMap.attrId in ["D001", "D002", "D003"]) {
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}"
    }
    else if (descMap.cluster == "E001" && descMap.attrId == "D010") {
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" }
    }
    else if (descMap.cluster == "E001" && descMap.attrId == "D030") {
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" }
    }
    else {
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap"
        return false 
    }
    return true    // processed
}

// return true if further processing in the main parse method should be cancelled !
boolean otherTuyaOddities( String description ) {
  /*
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) {
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 
        return true
    }
*/
    def descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch ( e ) {
        if (logEnable) log.warn "${device.displayName} exception caught while parsing <b>otherTuyaOddities</b> descMap:  ${descMap}"
        return true
    }
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"}        
    if (descMap.attrId == null ) {
        logDebug "otherTuyaOddities: descMap = ${descMap}"
        if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping"
        return false
    }
    boolean bWasAtLeastOneAttributeProcessed = false
    boolean bWasThereAnyStandardAttribite = false
    // attribute report received
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]]
    descMap.additionalAttrs.each {
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status]
        //log.trace "Tuya oddity: filling in attrData ${attrData}"
    }
    attrData.each {
        //log.trace "each it=${it}"
        def map = [:]
        if (it.status == "86") {
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}"
            // TODO - skip parsing?
        }
        switch (it.cluster) {
            case "0000" :
                if (it.attrId in ["FFE0", "FFE1", "FFE2", "FFE4"]) {
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})"
                    bWasAtLeastOneAttributeProcessed = true
                }
                else if (it.attrId in ["FFFE", "FFDF"]) {
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})"
                    bWasAtLeastOneAttributeProcessed = true
                }
                else {
                    logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping"
                    bWasThereAnyStandardAttribite = true
                }
                break
            default :
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping"
                break
        } // switch
    } // for each attribute
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite
}

private boolean isCircuitBreaker()      { device.getDataValue("manufacturer") in ["_TZ3000_ky0fq4ho"] }
private boolean isRTXCircuitBreaker()   { device.getDataValue("manufacturer") in ["_TZE200_abatw3kj"] }

def parseOnOffAttributes( it ) {
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}"
    def mode
    def attrName
    if (it.value == null) {
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}"
        return
    }
    def value = zigbee.convertHexToInt(it.value)
    switch (it.attrId) {
        case "4000" :    // non-Tuya GlobalSceneControl (bool), read-only
            attrName = "Global Scene Control"
            mode = value == 0 ? "off" : value == 1 ? "on" : null
            break
        case "4001" :    // non-Tuya OnTime (UINT16), read-only
            attrName = "On Time"
            mode = value
            break
        case "4002" :    // non-Tuya OffWaitTime (UINT16), read-only
            attrName = "Off Wait Time"
            mode = value
            break
        case "4003" :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 
            attrName = "Power On State"
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : "UNKNOWN"
            break
        case "8000" :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]]
            attrName = "Child Lock"
            mode = value == 0 ? "off" : "on"
            break
        case "8001" :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]]
            attrName = "LED mode"
            if (isCircuitBreaker()) {
                mode = value == 0 ? "Always Green" : value == 1 ? "Red when On; Green when Off" : value == 2 ? "Green when On; Red when Off" : value == 3 ? "Always Red" : null
            }
            else {
                mode = value == 0 ? "Disabled"  : value == 1 ? "Lit when On" : value == 2 ? "Lit when Off" : value == 3 ? "Freeze": null
            }
            break
        case "8002" :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]]
            attrName = "Power On State"
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : null
            break
        case "8003" : //  Over current alarm
            attrName = "Over current alarm"
            mode = value == 0 ? "Over Current OK" : value == 1 ? "Over Current Alarm" : null
            break
        default :
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}"
            return
    }
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" }
}


/*
 * -----------------------------------------------------------------------------
 * TS004F Button/Dimmer         cluster 0x0006
 * -----------------------------------------------------------------------------
*/

def needsDebouncing() { (((settings.debounce  ?: 0) as int) != 0) && (device.getDataValue("model") == "TS004F" || (device.getDataValue("manufacturer") in ["_TZ3000_abci1hiu", "_TZ3000_vp6clf9d"]))}
    
void processTS004Fcommand(final Map descMap) {
    logDebug "processTS004Fcommand: descMap: $descMap"
    def buttonNumber = 0
    def buttonState = "unknown"
    Boolean reverseButton = settings.reverseButton ?: false
    // when TS004F initialized in Scene switch mode!
    if (descMap.clusterInt == 0x0006 && descMap.command == "FD") {
        if (descMap.sourceEndpoint == "03") {
     	    buttonNumber = reverseButton==true ? 3 : 1
        }
        else if (descMap.sourceEndpoint == "04") {
      	    buttonNumber = reverseButton==true  ? 4 : 2
        }
        else if (descMap.sourceEndpoint == "02") {
            buttonNumber = reverseButton==true  ? 2 : 3
        }
        else if (descMap.sourceEndpoint == "01") {
       	    buttonNumber = reverseButton==true  ? 1 : 4
        }
	    else if (descMap.sourceEndpoint == "05") {    // LoraTap TS0046
   	        buttonNumber = reverseButton==true  ? 5 : 5
        }
        else if (descMap.sourceEndpoint == "06") {
       	    buttonNumber = reverseButton==true  ? 6 : 6
        }            
        if (descMap.data[0] == "00") {
            buttonState = "pushed"
        }
        else if (descMap.data[0] == "01") {
            buttonState = "doubleTapped"
        }
        else if (descMap.data[0] == "02") {
            buttonState = "held"
        }
        else {
            logWarn "unknown data in event from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint} data[0] = ${descMap.data[0]}"
            return
        } 
    } // if command == "FD"}
    else if (descMap.clusterInt == 0x0006 && descMap.command == "FC") {
        // Smart knob
        if (descMap.data[0] == "00") {            // Rotate one click right
            buttonNumber = 2
        }
        else if (descMap.data[0] == "01") {       // Rotate one click left
            buttonNumber = 3
        }
        buttonState = "pushed"
    }
    else {
        logWarn "processTS004Fcommand: unprocessed command"
        return
    }
    if (buttonNumber != 0 ) {
        if (needsDebouncing()) {
            if ((state.states["lastButtonNumber"] ?: 0) == buttonNumber ) {    // debouncing timer still active!
                logWarn "ignored event for button ${state.states['lastButtonNumber']} - still in the debouncing time period!"
                runInMillis((settings.debounce ?: DebounceOpts.defaultValue) as int, buttonDebounce, [overwrite: true])    // restart the debouncing timer again
                logDebug "restarted debouncing timer ${settings.debounce ?: DebounceOpts.defaultValue}ms for button ${buttonNumber} (lastButtonNumber=${state.states['lastButtonNumber']})"
                return
            }
        }
        state.states["lastButtonNumber"] = buttonNumber
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  lastButtonNumber=${state.states['lastButtonNumber']}"
    }
    if (buttonState != "unknown" && buttonNumber != 0) {
        def descriptionText = "button $buttonNumber was $buttonState"
	    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
        logInfo "${descriptionText}"
		sendEvent(event)
        if (needsDebouncing()) {
            runInMillis((settings.debounce ?: DebounceOpts.defaultValue) as int, buttonDebounce, [overwrite: true])
        }
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  buttonState=${buttonState}"
    }
}

void processTS004Fmode(final Map descMap) {
    if (descMap.value == "00") {
        sendEvent(name: "switchMode", value: "dimmer", isStateChange: true) 
        logInfo "mode is <b>dimmer</b>"
    }
    else if (descMap.value == "01") {
        sendEvent(name: "switchMode", value: "scene", isStateChange: true)
        logInfo "mode is <b>scene</b>"
    }
    else {
        logWarn "TS004F unknown attrId ${descMap.attrId} value ${descMap.value}"
    }
}


def buttonDebounce(/*button*/) {
    logDebug "debouncing timer (${settings.debounce}) for button ${state.states['lastButtonNumber']} expired."
    state.states["lastButtonNumber"] = 0
}

def sendButtonEvent(buttonNumber, buttonState, isDigital=false) {
    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital==true ? 'digital' : 'physical']
    if (txtEnable) {log.info "${device.displayName} $event.descriptionText"}
    sendEvent(event)
}

def switchToSceneMode()
{
    logInfo "switching TS004F into Scene mode"
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01))
}

def switchToDimmerMode()
{
    logInfo "switching TS004F into Dimmer mode"
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x00))
}

def switchMode( mode ) {
    if (mode == "dimmer") {
        switchToDimmerMode()
    }
    else if (mode == "scene") {
        switchToSceneMode()
    }
}


def push(buttonNumber) {
    sendButtonEvent(buttonNumber, "pushed", isDigital=true)
}

def doubleTap(buttonNumber) {
    sendButtonEvent(buttonNumber, "doubleTapped", isDigital=true)
}

def hold(buttonNumber) {
    sendButtonEvent(buttonNumber, "held", isDigital=true)
}

def release(buttonNumber) {
    sendButtonEvent(buttonNumber, "released", isDigital=true)
}

void sendNumberOfButtonsEvent(numberOfButtons) {
    sendEvent(name: "numberOfButtons", value: numberOfButtons, isStateChange: true, type: "digital")
}

void sendSupportedButtonValuesEvent(supportedValues) {
    sendEvent(name: "supportedButtonValues", value: JsonOutput.toJson(supportedValues), isStateChange: true, type: "digital")
}


/*
 * -----------------------------------------------------------------------------
 * Level Control Cluster            0x0008
 * -----------------------------------------------------------------------------
*/
void parseLevelControlCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
    final long rawValue = hexStrToUnsignedInt(descMap.value)
    if (descMap.attrId == "0000" && descMap.command == "FD") {
        processTS004Fcommand(descMap)
    }
    else if (descMap.attrId == "0000") {
        sendLevelControlEvent(rawValue)
    }
    else {
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}"
    }
}


def sendLevelControlEvent( rawValue ) {
    def value = rawValue as int
    if (value <0) value = 0
    if (value >100) value = 100
    def map = [:] 
    
    def isDigital = state.states["isDigital"]
    map.type = isDigital == true ? "digital" : "physical"
        
    map.name = "level"
    map.value = value
    boolean isRefresh = state.states["isRefresh"] ?: false
    if (isRefresh == true) {
        map.descriptionText = "${device.displayName} is ${value} [Refresh]"
        map.isStateChange = true
    }
    else {
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]"
    }
    logInfo "${map.descriptionText}"
    sendEvent(map)
    clearIsDigital()
}

/**
 * Get the level transition rate
 * @param level desired target level (0-100)
 * @param transitionTime transition time in seconds (optional)
 * @return transition rate in 1/10ths of a second
 */
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) {
    int rate = 0
    final Boolean isOn = device.currentValue('switch') == 'on'
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0
    if (!isOn) {
        currentLevel = 0
    }
    // Check if 'transitionTime' has a value
    if (transitionTime > 0) {
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer
        rate = transitionTime * 10
    } else {
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) {
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer
            rate = settings.levelUpTransition.toInteger()
        }
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) {
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer
            rate = settings.levelDownTransition.toInteger()
        }
    }
    logDebug "using level transition rate ${rate}"
    return rate
}

// Command option that enable changes when off
@Field static final String PRE_STAGING_OPTION = '01 01'

/**
 * Constrain a value to a range
 * @param value value to constrain
 * @param min minimum value (default 0)
 * @param max maximum value (default 100)
 * @param nullValue value to return if value is null (default 0)
 */
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) {
    if (min == null || max == null) {
        return value
    }
    return value != null ? max.min(value.max(min)) : nullValue
}

/**
 * Constrain a value to a range
 * @param value value to constrain
 * @param min minimum value (default 0)
 * @param max maximum value (default 100)
 * @param nullValue value to return if value is null (default 0)
 */
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) {
    if (min == null || max == null) {
        return value as Integer
    }
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue
}

// Delay before reading attribute (when using polling)
@Field static final int POLL_DELAY_MS = 1000

/**
 * If the device is polling, delay the execution of the provided commands
 * @param delayMs delay in milliseconds
 * @param commands commands to execute
 * @return list of commands to be sent to the device
 */
private List<String> ifPolling(final int delayMs = 0, final Closure commands) {
    if (state.reportingEnabled == false) {
        final int value = Math.max(delayMs, POLL_DELAY_MS)
        return ["delay ${value}"] + (commands() as List<String>) as List<String>
    }
    return []
}

def intTo16bitUnsignedHex(value) {
    def hexStr = zigbee.convertToHexString(value.toInteger(),4)
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2))
}

def intTo8bitUnsignedHex(value) {
    return zigbee.convertToHexString(value.toInteger(), 2)
}

/**
 * Send 'switchLevel' attribute event
 * @param isOn true if light is on, false otherwise
 * @param level brightness level (0-254)
 */
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) {
    List<String> cmds = []
    final Integer level = constrain(value)
    final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8)
    final String hexRate = DataType.pack(rate, DataType.UINT16, true)
    final int levelCommand = levelPreset ? 0x00 : 0x04
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) {
        // If light is off, first go to level 0 then to desired level
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}")
    }
    // Payload: Level | Transition Time | Options Mask | Options Override
    // Options: Bit 0x01 enables pre-staging level
    /*
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") +
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) }
    */
    int duration = 10            // TODO !!!
    String endpointId = "01"     // TODO !!!
     cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",]

    return cmds
}


/**
 * Set Level Command
 * @param value level percent (0-100)
 * @param transitionTime transition time in seconds
 * @return List of zigbee commands
 */
void /*List<String>*/ setLevel(final Object value, final Object transitionTime = null) {
    logInfo "setLevel (${value}, ${transitionTime})"
    final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer)
    scheduleCommandTimeoutCheck()
    /*return*/ sendZigbeeCommands ( setLevelPrivate(value, rate))
}

/*
 * -----------------------------------------------------------------------------
 * Illuminance    cluster 0x0400
 * -----------------------------------------------------------------------------
*/
void parseIlluminanceCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    final long value = hexStrToUnsignedInt(descMap.value)
    def lux = value > 0 ? Math.round(Math.pow(10,(value/10000))) : 0
    handleIlluminanceEvent(lux)
}

void handleIlluminanceEvent( illuminance, Boolean isDigital=false ) {
    def eventMap = [:]
    if (state.stats != null) state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 else state.stats=[:]
    eventMap.name = "illuminance"
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float)))
    eventMap.value  = illumCorrected
    eventMap.type = isDigital ? "digital" : "physical"
    eventMap.unit = "lx"
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now()))/1000)
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    Integer lastIllum = device.currentValue("illuminance") ?: 0
    Integer delta = Math.abs(lastIllum- illumCorrected)
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) {
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})"
        return
    }
    if (timeElapsed >= minTime) {
		logInfo "${eventMap.descriptionText}"
		unschedule("sendDelayedIllumEvent")		//get rid of stale queued reports
        state.lastRx['illumTime'] = now()
        sendEvent(eventMap)
	}		
    else {         // queue the event
    	eventMap.type = "delayed"
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap])
    }
}

private void sendDelayedIllumEvent(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
    sendEvent(eventMap)
}

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high']


/*
 * -----------------------------------------------------------------------------
 * temperature
 * -----------------------------------------------------------------------------
*/
void parseTemperatureCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    final long value = hexStrToUnsignedInt(descMap.value)
    handleTemperatureEvent(value/100.0F as Float)
}

void handleTemperatureEvent( Float temperature, Boolean isDigital=false ) {
    def eventMap = [:]
    if (state.stats != null) state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 else state.stats=[:]
    eventMap.name = "temperature"
    def Scale = location.temperatureScale
    if (Scale == "F") {
        temperature = (temperature * 1.8) + 32
        eventMap.unit = "\u00B0"+"F"
    }
    else {
        eventMap.unit = "\u00B0"+"C"
    }
    def tempCorrected = (temperature + safeToDouble(settings?.temperatureOffset ?: 0)) as Float
    eventMap.value  =  (Math.round(tempCorrected * 10) / 10.0) as Float
    eventMap.type = isDigital == true ? "digital" : "physical"
    //eventMap.isStateChange = true
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now()))/1000)
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer
    if (timeElapsed >= minTime) {
		logInfo "${eventMap.descriptionText}"
		unschedule("sendDelayedTempEvent")		//get rid of stale queued reports
        state.lastRx['tempTime'] = now()
        sendEvent(eventMap)
	}		
    else {         // queue the event
    	eventMap.type = "delayed"
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap])
    }
}

private void sendDelayedTempEvent(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
    sendEvent(eventMap)
}

/*
 * -----------------------------------------------------------------------------
 * humidity
 * -----------------------------------------------------------------------------
*/
void parseHumidityCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    final long value = hexStrToUnsignedInt(descMap.value)
    handleHumidityEvent(value/100.0F as Float)
}

void handleHumidityEvent( Float humidity, Boolean isDigital=false ) {
    def eventMap = [:]
    if (state.stats != null) state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 else state.stats=[:]
    double humidityAsDouble = safeToDouble(humidity) + safeToDouble(settings?.humidityOffset ?: 0)
    if (humidityAsDouble <= 0.0 || humidityAsDouble > 100.0) {
        logWarn "ignored invalid humidity ${humidity} (${humidityAsDouble})"
        return
    }
    eventMap.value = Math.round(humidityAsDouble)
    eventMap.name = "humidity"
    eventMap.unit = "% RH"
    eventMap.type = isDigital == true ? "digital" : "physical"
    //eventMap.isStateChange = true
    eventMap.descriptionText = "${eventMap.name} is ${humidityAsDouble.round(1)} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now()))/1000)
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer    
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule("sendDelayedHumidityEvent")
        state.lastRx['humiTime'] = now()
        sendEvent(eventMap)
    }
    else {
    	eventMap.type = "delayed"
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap])
    }
}

private void sendDelayedHumidityEvent(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
	sendEvent(eventMap)
}

/*
 * -----------------------------------------------------------------------------
 * pm2.5
 * -----------------------------------------------------------------------------
*/
void parsePm25Cluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
	Float floatValue = Float.intBitsToFloat(value.intValue())
    //logDebug "pm25 float value = ${floatValue}"
    handlePm25Event(floatValue as Integer)
}

void handlePm25Event( Integer pm25, Boolean isDigital=false ) {
    def eventMap = [:]
    if (state.stats != null) state.stats['pm25Ctr'] = (state.stats['pm25Ctr'] ?: 0) + 1 else state.stats=[:]
    double pm25AsDouble = safeToDouble(pm25) + safeToDouble(settings?.pm25Offset ?: 0)
    if (pm25AsDouble <= 0.0 || pm25AsDouble > 999.0) {
        logWarn "ignored invalid pm25 ${pm25} (${pm25AsDouble})"
        return
    }
    eventMap.value = Math.round(pm25AsDouble)
    eventMap.name = "pm25"
    eventMap.unit = "\u03BCg/m3"    //"mg/m3"
    eventMap.type = isDigital == true ? "digital" : "physical"
    eventMap.isStateChange = true
    eventMap.descriptionText = "${eventMap.name} is ${pm25AsDouble.round()} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['pm25Time'] ?: now()))/1000)
    Integer minTime = settings?.minReportingTimePm25 ?: DEFAULT_MIN_REPORTING_TIME
    Integer timeRamaining = (minTime - timeElapsed) as Integer    
    if (timeElapsed >= minTime) {
        logInfo "${eventMap.descriptionText}"
        unschedule("sendDelayedPm25Event")
        state.lastRx['pm25Time'] = now()
        sendEvent(eventMap)
    }
    else {
    	eventMap.type = "delayed"
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}"
        runIn(timeRamaining, 'sendDelayedPm25Event',  [overwrite: true, data: eventMap])
    }
}

private void sendDelayedPm25Event(Map eventMap) {
    logInfo "${eventMap.descriptionText} (${eventMap.type})"
    state.lastRx['pm25Time'] = now()     // TODO - -(minReportingTimeHumidity * 2000)
	sendEvent(eventMap)
}

/*
 * -----------------------------------------------------------------------------
 * Analog Input Cluster 0x000C
 * -----------------------------------------------------------------------------
*/
void parseAnalogInputCluster(final Map descMap) {
    if (DEVICE_TYPE in ["AirQuality"]) {
        parseAirQualityIndexCluster(descMap)
    }
    else if (DEVICE_TYPE in ["AqaraCube"]) {
        parseAqaraCubeAnalogInputCluster(descMap)
    }
    else {
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}"
    }
}


/*
 * -----------------------------------------------------------------------------
 * Multistate Input Cluster 0x0012
 * -----------------------------------------------------------------------------
*/

void parseMultistateInputCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
	Float floatValue = Float.intBitsToFloat(value.intValue())
    if (DEVICE_TYPE in  ["AqaraCube"]) {
        parseMultistateInputClusterAqaraCube(descMap)
    }
    else {
        handleMultistateInputEvent(value as Integer)
    }
}

void handleMultistateInputEvent( Integer value, Boolean isDigital=false ) {
    def eventMap = [:]
    eventMap.value = value
    eventMap.name = "multistateInput"
    eventMap.unit = ""
    eventMap.type = isDigital == true ? "digital" : "physical"
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    sendEvent(eventMap)
    logInfo "${eventMap.descriptionText}"
}


/*
 * -----------------------------------------------------------------------------
 * thermostat cluster 0x0201
 * -----------------------------------------------------------------------------
*/

void parseThermostatCluster(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    if (settings.logEnable) {
        log.trace "zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    }

    switch (descMap.attrInt as Integer) {
        case 0x000:                      // temperature
            logInfo "temperature = ${value/100.0} (raw ${value})"
            handleTemperatureEvent(value/100.0)
            break
        case 0x0011:                      // cooling setpoint
            logInfo "cooling setpoint = ${value/100.0} (raw ${value})"
            break
        case 0x0012:                      // heating setpoint
            logInfo "heating setpoint = ${value/100.0} (raw ${value})"
            handleHeatingSetpointEvent(value/100.0)
            break
        case 0x001c:                      // mode
            logInfo "mode = ${value} (raw ${value})"
            break
        case 0x001e:                      // thermostatRunMode
            logInfo "thermostatRunMode = ${value} (raw ${value})"
            break
        case 0x0020:                      // battery
            logInfo "battery = ${value} (raw ${value})"
            break
        case 0x0023:                      // thermostatHoldMode
            logInfo "thermostatHoldMode = ${value} (raw ${value})"
            break
        case 0x0029:                      // thermostatOperatingState
            logInfo "thermostatOperatingState = ${value} (raw ${value})"
            break
        case 0xfff2:    // unknown
            logDebug "Aqara E1 TRV unknown attribute ${descMap.attrInt} value raw = ${value}"
            break;
        default:
            log.warn "zigbee received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

def handleHeatingSetpointEvent( temperature ) {
    setHeatingSetpoint(temperature)
}

//  ThermostatHeatingSetpoint command
//  sends TuyaCommand and checks after 4 seconds
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface)
def setHeatingSetpoint( temperature ) {
    def previousSetpoint = device.currentState('heatingSetpoint')?.value ?: 0
    double tempDouble
    logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})"
    if (true) {
        logDebug "0.5 C correction of the heating setpoint${temperature}"
        tempDouble = safeToDouble(temperature)
        tempDouble = Math.round(tempDouble * 2) / 2.0
    }
    else {
        if (temperature != (temperature as int)) {
            if ((temperature as double) > (previousSetpoint as double)) {
                temperature = (temperature + 0.5 ) as int
            }
            else {
                temperature = temperature as int
            }
        logDebug "corrected heating setpoint ${temperature}"
        }
        tempDouble = temperature
    }
    def maxTemp = settings?.maxThermostatTemp ?: 50
    def minTemp = settings?.minThermostatTemp ?: 5
    if (tempDouble > maxTemp ) tempDouble = maxTemp
    if (tempDouble < minTemp) tempDouble = minTemp
    tempDouble = tempDouble.round(1)
    Map eventMap = [name: "heatingSetpoint",  value: tempDouble, unit: "\u00B0"+"C"]
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}"
    sendHeatingSetpointEvent(eventMap)
    eventMap = [name: "thermostatSetpoint", value: tempDouble, unit: "\u00B0"+"C"]
    eventMap.descriptionText = null
    sendHeatingSetpointEvent(eventMap)
    updateDataValue("lastRunningMode", "heat")
    // 
    zigbee.writeAttribute(0x0201, 0x12, 0x29, (tempDouble * 100) as int)
}

private void sendHeatingSetpointEvent(Map eventMap) {
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" }
	sendEvent(eventMap)
}

// -------------------------------------------------------------------------------------------------------------------------


/*
 * -----------------------------------------------------------------------------
 * Tuya cluster EF00 specific code
 * -----------------------------------------------------------------------------
*/
private static getCLUSTER_TUYA()       { 0xEF00 }
private static getSETDATA()            { 0x00 }
private static getSETTIME()            { 0x24 }

// Tuya Commands
private static getTUYA_REQUEST()       { 0x00 }
private static getTUYA_REPORTING()     { 0x01 }
private static getTUYA_QUERY()         { 0x02 }
private static getTUYA_STATUS_SEARCH() { 0x06 }
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 }

// tuya DP type
private static getDP_TYPE_RAW()        { "01" }    // [ bytes ]
private static getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ]
private static getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ]
private static getDP_TYPE_STRING()     { "03" }    // [ N byte string ]
private static getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ]
private static getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits


void parseTuyaCluster(final Map descMap) {
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}"
        def offset = 0
        try {
            offset = location.getTimeZone().getOffset(new Date().getTime())
            //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}"
        }
        catch(e) {
            logWarn "cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero"
        }
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8))
        logDebug "sending time data : ${cmds}"
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) }
        //if (state.txCounter != null) state.txCounter = state.txCounter + 1
    }
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response
        String clusterCmd = descMap?.data[0]
        def status = descMap?.data[1]            
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}"
        if (status != "00") {
            logWarn "ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                
        }
    } 
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02" || descMap?.command == "05" || descMap?.command == "06"))
    {
        def dataLen = descMap?.data.size()
        //log.warn "dataLen=${dataLen}"
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command
        if (dataLen <= 5) {
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})"
            return
        }
        for (int i = 0; i < (dataLen-4); ) {
            def dp = zigbee.convertHexToInt(descMap?.data[2+i])          // "dp" field describes the action/message of a command frame
            def dp_id = zigbee.convertHexToInt(descMap?.data[3+i])       // "dp_identifier" is device dependant
            def fncmd_len = zigbee.convertHexToInt(descMap?.data[5+i]) 
            def fncmd = getTuyaAttributeValue(descMap?.data, i)          //
            logDebug "dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})"
            processTuyaDP( descMap, dp, dp_id, fncmd)
            i = i + fncmd_len + 4;
        }
    }
    else {
        logWarn "unprocessed Tuya command ${descMap?.command}"
    }
}

void processTuyaDP( descMap, dp, dp_id, fncmd) {
    switch (dp) {
        case 0x01 : // on/off
            if (DEVICE_TYPE in  ["LightSensor"]) {
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})"
            }
            else {
                sendSwitchEvent(fncmd)
            }
            break
/*
Switch1 		    1
Mode			    101
Degree of declining	code: 102
Duration 		    103
Switch Reverse		104
Battery Power		105
Increase		    106
Tact Switch 		107
Click 			    108
Custom Program		109
Producion Test		110
Sports Statistics	111
Custom Timing		112
*/
        case 0x02 :
            if (DEVICE_TYPE in  ["LightSensor"]) {
                handleIlluminanceEvent(fncmd)
            }
            else {
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            }
            break
        case 0x04 : // battery
            sendBatteryPercentageEvent(fncmd)
            break
        
        case 0x65 : // (101)
            if (isFingerbot()) {
                def value = FingerbotModeOpts.options[fncmd as int]
                def descriptionText = "Fingerbot mode is ${value} (${fncmd})"
                sendEvent(name: "fingerbotMode", value: value, descriptionText: descriptionText)
                logInfo "${descriptionText}"
            }
            else {
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            }
            break
        case 0x66 : // (102)
            if (isFingerbot()) {
                def value = fncmd as int
                def descriptionText = "Fingerbot Down Position is ${value} %"
                sendEvent(name: "dnPosition", value: value, descriptionText: descriptionText)
                logInfo "${descriptionText}"
            }
            else {
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            }
            break
        case 0x67 : // (103)
            if (isFingerbot()) {
                def value = fncmd as int
                def descriptionText = "Fingerbot push time (duration) is ${value} seconds"
                sendEvent(name: "pushTime", value: value, descriptionText: descriptionText)
                logInfo "${descriptionText}"
            }
            else {
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            }
            break
        case 0x68 : // (104)
            if (isFingerbot()) {
                def value = FingerbotDirectionOpts.options[fncmd as int]
                def descriptionText = "Fingerbot switch direction is ${value} (${fncmd})"
                sendEvent(name: "direction", value: value, descriptionText: descriptionText)
                logInfo "${descriptionText}"
            }
            else {
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            }
            break
        case 0x69 : // (105)
            if (isFingerbot()) {
                //logInfo "Fingerbot Battery Power is ${fncmd}"
                sendBatteryPercentageEvent(fncmd) 
            }
            else {
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            }
            break
        case 0x6A : // (106)
            if (isFingerbot()) {
                def value = fncmd as int
                def descriptionText = "Fingerbot Up Position is ${value} %"
                sendEvent(name: "upPosition", value: value, descriptionText: descriptionText)
                logInfo "${descriptionText}"
            }
            else {
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            }
            break
        case 0x6B : // (107)
            logInfo "Fingerbot Tact Switch is ${fncmd}"
            break
        case 0x6C : // (108)
            logInfo "Fingerbot Click is ${fncmd}"
            break
        case 0x6D : // (109)
            logInfo "Fingerbot Custom Program is ${fncmd}"
            break
        case 0x6E : // (110)
            logInfo "Fingerbot Producion Test is ${fncmd}"
            break
        case 0x6F : // (111)
            logInfo "Fingerbot Sports Statistics is ${fncmd}"
            break
        case 0x70 : // (112)
            logInfo "Fingerbot Custom Timing is ${fncmd}"
            break
        default :
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            break            
    }
}



private int getTuyaAttributeValue(ArrayList _data, index) {
    int retValue = 0

    if (_data.size() >= 6) {
        int dataLength = _data[5+index] as Integer
        int power = 1;
        for (i in dataLength..1) {
            retValue = retValue + power * zigbee.convertHexToInt(_data[index+i+5])
            power = power * 256
        }
    }
    return retValue
}


private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    def ep = safeToInt(state.destinationEP)
    if (ep==null || ep==0) ep = 1
    def tuyaCmd = isFingerbot() ? 0x04 : SETDATA
    
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}"
    return cmds
}

private getPACKET_ID() {
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) 
}

def tuyaTest( dpCommand, dpValue, dpTypeString ) {
    ArrayList<String> cmds = []
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue

    if (settings?.logEnable) log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}"

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) )
}

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C }
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 }

def tuyaBlackMagic() {
    def ep = safeToInt(state.destinationEP ?: 01)
    if (ep==null || ep==0) ep = 1
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay=200)
}

void aqaraBlackMagic() {
    List<String> cmds = []
    if (isAqaraTVOC() || isAqaraTRV()) {
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",]
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}"
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}"
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)    // TODO: check - battery voltage
        if (isAqaraTVOC()) {
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay=200)    // TVOC only
        }
        sendZigbeeCommands( cmds )
        logDebug "sent aqaraBlackMagic()"
    }
    else {
        logDebug "aqaraBlackMagic() was SKIPPED"
    }
}


/**
 * initializes the device
 * Invoked from configure()
 * @return zigbee commands
 */
def initializeDevice() {
    ArrayList<String> cmds = []
    logInfo 'initializeDevice...'
    
    // start with the device-specific initialization first.
    if (DEVICE_TYPE in  ["AirQuality"]) { return initializeDeviceAirQuality() }
    if (DEVICE_TYPE in  ["IRBlaster"])  { return initializeDeviceIrBlaster() }
  
 
    // not specific device type - do some generic initializations
    if (DEVICE_TYPE in  ["THSensor"]) {
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity
    }
    //
    if (cmds == []) {
        cmds = ["delay 299",]
    }
    return cmds
}


/**
 * configures the device
 * Invoked from updated()
 * @return zigbee commands
 */
def configureDevice() {
    ArrayList<String> cmds = []
    logInfo 'configureDevice...'

    if (DEVICE_TYPE in  ["AirQuality"]) { cmds += configureDeviceAirQuality() }
    if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += configureDeviceFingerbot() }
    if (DEVICE_TYPE in  ["AqaraCube"])  { cmds += configureDeviceAqaraCube() }
    if (DEVICE_TYPE in  ["Switch"])     { cmds += configureDeviceSwitch() }
    if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += configureIrBlaster() }
        
    if (cmds == []) {
        cmds = ["delay 299",]
    }
    sendZigbeeCommands(cmds)  
}

/*
 * -----------------------------------------------------------------------------
 * Hubitat default handlers methods
 * -----------------------------------------------------------------------------
*/

def refresh() {
    logInfo "refresh()..."
    checkDriverVersion()
    List<String> cmds = []
    if (state.states == null) state.states = [:]
    state.states["isRefresh"] = true
    
    // device type specific refresh handlers
    if (DEVICE_TYPE in  ["AqaraCube"])       { cmds += refreshAqaraCube() }
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += refreshFingerbot() }
    else if (DEVICE_TYPE in  ["AirQuality"]) { cmds += refreshAirQuality() }
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += refreshSwitch() }
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += refreshIrBlaster() }
    else {
        // generic refresh handling, based on teh device capabilities 
        if (device.hasCapability("Battery")) {
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)         // battery voltage
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)         // battery percentage 
        }
        if (DEVICE_TYPE in  ["Plug", "Dimmer"]) {
    	    cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay=200)
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership
        }
        if (DEVICE_TYPE in  ["Dimmer"]) {
    	    cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay=200)        
        }
        if (DEVICE_TYPE in  ["THSensor", "AirQuality"]) {
    	    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay=200)        
    	    cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay=200)        
        }
        if (DEVICE_TYPE in  ["Thermostat"]) {
            // TODO - Aqara E1 specific refresh commands only 1
    	    //cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)         // battery voltage (E1 does not send percentage)
    	    //cmds += zigbee.readAttribute(0x0201, 0x0000, [:], delay=100)         // local temperature
    	    //cmds += zigbee.readAttribute(0x0201, 0x0011, [:], delay=100)         // cooling setpoint
    	    //cmds += zigbee.readAttribute(0x0201, 0x0012, [:], delay=100)         // heating setpoint
    	    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0011, 0x0012, 0x001C], [:], delay=100)         // local temperature, cooling setpoint, heating setpoint, system mode (enum8 )
    	    //cmds += zigbee.readAttribute(0x0201, 0x0015, [:], delay=100)         // min heat setpoint limit - Unsupported Attribute
    	    //cmds += zigbee.readAttribute(0x0201, 0x0016, [:], delay=100)         // max heat setpoint limit = Unsupported Attribute
    	    //cmds += zigbee.readAttribute(0x0201, 0x0017, [:], delay=100)         // min cool setpoint limit - Unsupported Attribute
    	    //cmds += zigbee.readAttribute(0x0201, 0x0018, [:], delay=100)         // max cool setpoint limit - Unsupported Attribute
    	    //cmds += zigbee.readAttribute(0x0201, 0x0019, [:], delay=100)         // min setpoint dead band ?- Unsupported Attribute
    	    //cmds += zigbee.readAttribute(0x0201, 0x001C, [:], delay=100)         // system mode (enum8 )
    	    //cmds += zigbee.readAttribute(0x0201, 0x001E, [:], delay=100)         // Unsupported Attribute
    	    //cmds += zigbee.readAttribute(0x0201, 0x0020, [:], delay=100)         // Unsupported Attribute
    	    //cmds += zigbee.readAttribute(0x0201, 0x0023, [:], delay=100)         // hold temperature (enum) on/off  - Unsupported Attribute
    	    //cmds += zigbee.readAttribute(0x0201, 0x0029, [:], delay=100)         // thermostat running mode  - Unsupported Attribute
    	    cmds += zigbee.readAttribute(0x0202, 0x0000, [:], delay=100) 
        }
    }

    runInMillis( REFRESH_TIMER, clearRefreshRequest, [overwrite: true])                 // 3 seconds
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }
    else {
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}"
    }
}

def clearRefreshRequest() { state.states["isRefresh"] = false }

void sendInfoEvent(String info=null) {
    if (info == null) {
        logDebug "clearing the Info event"
        device.deleteCurrentState("$it")
    }
    else {
        logInfo "${info}"
        sendEvent(name: "Info", value: info, isDigital: true)    
    }
}

def ping() {
    if (!(isAqaraTVOC())) {
        logInfo 'ping...'
        scheduleCommandTimeoutCheck()
        if (state.lastTx == nill ) state.lastTx = [:] 
        state.lastTx["pingTime"] = new Date().getTime()
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) )
    }
    else {
        // Aqara TVOC is sleepy or does not respond to the ping.
        logInfo "ping() command is not available for this sleepy device."
        sendRttEvent("n/a")
    }
}

/**
 * sends 'rtt'event (after a ping() command)
 * @param null: calculate the RTT in ms
 *        value: send the text instead ('timeout', 'n/a', etc..)
 * @return none
 */
void sendRttEvent( String value=null) {
    def now = new Date().getTime()
    if (state.lastTx == null ) state.lastTx = [:]
    def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: now).toInteger()
    def descriptionText = "Round-trip time is ${timeRunning} ms"
    if (value == null) {
        logInfo "${descriptionText}"
        sendEvent(name: "rtt", value: timeRunning, descriptionText: descriptionText, unit: "ms", isDigital: true)    
    }
    else {
        descriptionText = "Round-trip time : ${value}"
        logInfo "${descriptionText}"
        sendEvent(name: "rtt", value: value, descriptionText: descriptionText, isDigital: true)    
    }
}

/**
 * Lookup the cluster name from the cluster ID
 * @param cluster cluster ID
 * @return cluster name if known, otherwise "private cluster"
 */
private String clusterLookup(final Object cluster) {
    if (cluster != null) {
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}"
    }
    else {
        logWarn "cluster is NULL!"
        return "NULL"
    }
}

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) {
    runIn(delay, 'deviceCommandTimeout')
}

void deviceCommandTimeout() {
    logWarn 'no response received (sleepy device or offline?)'
    sendRttEvent("timeout")
}

/**
 * Schedule a device health check
 * @param intervalMins interval in minutes
 */
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) {
    if (healthMethod == 1 || healthMethod == 2)  {
        String cron = getCron( intervalMins*60 )
        schedule(cron, 'deviceHealthCheck')
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes"
    }
    else {
        logWarn "deviceHealthCheck is not scheduled!"
        unschedule('deviceHealthCheck')
    }
}

private void unScheduleDeviceHealthCheck() {
    unschedule('deviceHealthCheck')
    device.deleteCurrentState('healthStatus')
    logWarn "device health check is disabled!"
    
}

// called when any event was received from the Zigbee device in the parse() method.
void setHealthStatusOnline() {
    if(state.health == null) { state.health = [:] }
    state.health['checkCtr3']  = 0
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) {   
        sendHealthStatusEvent('online')
        logInfo "is now online!"
    }
}


def deviceHealthCheck() {
    if (state.health == null) { state.health = [:] }
    def ctr = state.health['checkCtr3'] ?: 0
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) {
        if ((device.currentValue("healthStatus") ?: 'unknown') != 'offline' ) {
            logWarn "not present!"
            sendHealthStatusEvent('offline')
        }
    }
    else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})"
    }
    state.health['checkCtr3'] = ctr + 1
}

void sendHealthStatusEvent(value) {
    def descriptionText = "healthStatus changed to ${value}"
    sendEvent(name: "healthStatus", value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true)
    if (value == 'online') {
        logInfo "${descriptionText}"
    }
    else {
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" }
    }
}



/**
 * Scheduled job for polling device specific attribute(s)
 */
void autoPoll() {
    logDebug "autoPoll()..."
    checkDriverVersion()
    List<String> cmds = []
    if (state.states == null) state.states = [:]
    //state.states["isRefresh"] = true
    
    if (DEVICE_TYPE in  ["AirQuality"]) {
	    cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay=200)      // tVOC   !! mfcode="0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value;
    }
    
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }    
}


/**
 * Invoked by Hubitat when the driver configuration is updated
 */
void updated() {
    logInfo 'updated...'
    logInfo"driver version ${driverVersionAndTimeStamp()}"
    unschedule()

    if (settings.logEnable) {
        logDebug settings
        runIn(86400, logsOff)
    }

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling']
        // schedule the periodic timer
        final int interval = (settings.healthCheckInterval as Integer) ?: 0
        if (interval > 0) {
            //log.trace "healthMethod=${healthMethod} interval=${interval}"
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method"
            scheduleDeviceHealthCheck(interval, healthMethod)
        }
    }
    else {
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod
        log.info "Health Check is disabled!"
    }
    if (DEVICE_TYPE in ["AirQuality"])  { updatedAirQuality() }
    if (DEVICE_TYPE in ["IRBlaster"])   { updatedIrBlaster() }
        
    configureDevice()    // sends Zigbee commands
    
    sendInfoEvent("updated")
}

/**
 * Disable logging (for debugging)
 */
void logsOff() {
    logInfo "debug logging disabled..."
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

@Field static final Map ConfigureOpts = [
    "Configure the device only"  : [key:2, function: 'configure'],
    "           --            "  : [key:3, function: 'configureHelp'],
    "Delete All Preferences"     : [key:4, function: 'deleteAllSettings'],
    "Delete All Current States"  : [key:5, function: 'deleteAllCurrentStates'],
    "Delete All Scheduled Jobs"  : [key:6, function: 'deleteAllScheduledJobs'],
    "Delete All State Variables" : [key:7, function: 'deleteAllStates'],
    "Delete All Child Devices"   : [key:8, function: 'deleteAllChildDevices'],
    "           -             "  : [key:1, function: 'configureHelp'],
    "*** LOAD ALL DEFAULTS ***"  : [key:0, function: 'loadAllDefaults']
]

def configure(command) {
    ArrayList<String> cmds = []
    logInfo "configure(${command})..."
    
    Boolean validated = false
    if (!(command in (ConfigureOpts.keySet() as List))) {
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}"
        return
    }
    //
    def func
   // try {
        func = ConfigureOpts[command]?.function
        cmds = "$func"()
 //   }
//    catch (e) {
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)"
//        return
//    }

    logInfo "executed '${func}'"
}

def configureHelp( val ) {
    logWarn "configureHelp: select one of the commands in this list!"             
}

def loadAllDefaults() {
    logWarn "loadAllDefaults() !!!"
    deleteAllSettings()
    deleteAllCurrentStates()
    deleteAllScheduledJobs()
    deleteAllStates()
    deleteAllChildDevices()
    initialize()
    configure()
    sendInfoEvent("All Defaults Loaded!")
}

/**
 * Send configuration parameters to the device
 * Invoked when device is first installed and when the user updates the configuration
 * @return sends zigbee commands
 */
def configure() {
    ArrayList<String> cmds = []
    logInfo 'configure...'
    logDebug settings
    cmds += tuyaBlackMagic()
    if (isAqaraTVOC() || isAqaraTRV()) {
        aqaraBlackMagic()
    }
    cmds += initializeDevice()
    cmds += configureDevice()
    sendZigbeeCommands(cmds)
    sendInfoEvent("sent device configuration")
}

/**
 * Invoked by Hubitat when driver is installed
 */
void installed() {
    logInfo 'installed...'
    // populate some default values for attributes
    sendEvent(name: 'healthStatus', value: 'unknown')
    sendEvent(name: 'powerSource', value: 'unknown')
    sendInfoEvent("installed")
    runIn(3, 'updated')
}

/**
 * Invoked when initialize button is clicked
 */
void initialize() {
    logInfo 'initialize...'
    initializeVars(fullInit = true)
}


/*
 *-----------------------------------------------------------------------------
 * kkossev drivers commonly used functions
 *-----------------------------------------------------------------------------
*/

static Integer safeToInt(val, Integer defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

static Double safeToDouble(val, Double defaultVal=0.0) {
	return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    logDebug "sendZigbeeCommands(cmd=$cmd)"
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            if (state.stats != null) state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 else state.stats=[:]
    }
    if (state.lastTx != null) state.lastTx['cmdTime'] = now() else state.lastTx=[:]
    sendHubCommand(allActions)
}

static def driverVersionAndTimeStamp() {version() + ' ' + timeStamp() + ((_DEBUG) ? " (debug version!)" : " ")}

def getDeviceInfo() {
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>"
}

def getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())]
    return state.destinationEP ?: device.endpointId ?: "01"
}

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logDebug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}")
        state.driverVersion = driverVersionAndTimeStamp()
        initializeVars(fullInit = false)
    }
    else {
        // no driver version change
    }
}


/**
 * called from TODO
 * 
 */

def deleteAllStatesAndJobs() {
    state.clear()    // clear all states
    unschedule()
    device.deleteCurrentState('*')
    device.deleteCurrentState('')

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}"
}


/**
 * called from TODO
 * 
 */
def resetStats() {
    state.stats = [:]
    state.states = [:]
    state.lastRx = [:]
    state.lastTx = [:]
    state.health = [:]
    state.zigbeeGroups = [:] 
    state.stats["rxCtr"] = 0
    state.stats["txCtr"] = 0
    state.states["isDigital"] = false
    state.states["isRefresh"] = false
    state.health["offlineCtr"] = 0
    state.health["checkCtr3"] = 0
}

/**
 * called from TODO
 * 
 */
void initializeVars( boolean fullInit = false ) {
    logDebug "InitializeVars()... fullInit = ${fullInit}"
    if (fullInit == true ) {
        state.clear()
        unschedule()
        resetStats()
        //setDeviceNameAndProfile()
        //state.comment = 'Works with Tuya Zigbee Devices'
        logInfo "all states and scheduled jobs cleared!"
        state.driverVersion = driverVersionAndTimeStamp()
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}"
        state.deviceType = DEVICE_TYPE
        sendInfoEvent("Initialized")
    }
    
    if (state.stats == null)  { state.stats  = [:] }
    if (state.states == null) { state.states = [:] }
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.health == null) { state.health = [:] }
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] }
    
    if (fullInit || settings?.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit || settings?.advancedOptions == null) device.updateSetting("advancedOptions", [value:false, type:"bool"])
    if (fullInit || settings?.healthCheckMethod == null) device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.healthCheckInterval == null) device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum'])
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown')
    if (fullInit || settings?.threeStateEnable == null) device.updateSetting("threeStateEnable", false)
    if (fullInit || settings?.debounce == null) device.updateSetting('debounce', [value: DebounceOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.voltageToPercent == null) device.updateSetting("voltageToPercent", false)
    if (fullInit || settings?.reverseButton == null) device.updateSetting("reverseButton", true)
    if (device.hasCapability("IlluminanceMeasurement")) {
        if (fullInit || settings?.minReportingTime == null) device.updateSetting("minReportingTime", [value:DEFAULT_MIN_REPORTING_TIME, type:"number"])
        if (fullInit || settings?.maxReportingTime == null) device.updateSetting("maxReportingTime", [value:DEFAULT_MAX_REPORTING_TIME, type:"number"])
    }
    if (device.hasCapability("IlluminanceMeasurement")) {
        if (fullInit || settings?.illuminanceThreshold == null) device.updateSetting("illuminanceThreshold", [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:"number"])
        if (fullInit || settings?.illuminanceCoeff == null) device.updateSetting("illuminanceCoeff", [value:1.00, type:"decimal"])
    }
    // device specific initialization should be at the end
    if (DEVICE_TYPE in ["AirQuality"]) { initVarsAirQuality(fullInit) }
    if (DEVICE_TYPE in ["Fingerbot"])  { initVarsFingerbot(fullInit) }
    if (DEVICE_TYPE in ["AqaraCube"])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) }
    if (DEVICE_TYPE in ["Switch"])     { initVarsSwitch(fullInit); initEventsSwitch(fullInit) }            // none
    if (DEVICE_TYPE in ["IRBlaster"])  { initVarsIrBlaster(fullInit); initEventsIrBlaster(fullInit) }      // none

    //updateTuyaVersion()
    
    def mm = device.getDataValue("model")
    if ( mm != null) {
        logDebug " model = ${mm}"
    }
    else {
        logWarn " Model not found, please re-pair the device!"
    }
    def ep = device.getEndpointId()
    if ( ep  != null) {
        //state.destinationEP = ep
        logDebug " destinationEP = ${ep}"
    }
    else {
        logWarn " Destination End Point not found, please re-pair the device!"
        //state.destinationEP = "01"    // fallback
    }    
}


/**
 * called from TODO
 * 
 */
def setDestinationEP() {
    def ep = device.getEndpointId()
    if (ep != null && ep != 'F2') {
        state.destinationEP = ep
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}"
    }
    else {
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!"
        state.destinationEP = "01"    // fallback EP
    }      
}


def logDebug(msg) {
    if (settings.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

def logInfo(msg) {
    if (settings.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

def logWarn(msg) {
    if (settings.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

void getAllProperties() {
    log.trace 'Properties:'    
    device.properties.each { it->
        log.debug it
    }
    log.trace 'Settings:'    
    settings.each { it->
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev
    }    
    log.trace 'Done'    
}

// delete all Preferences
void deleteAllSettings() {
    settings.each { it->
        log.debug "deleting ${it.key}"
        //this.removeSetting("${it.key}")
        device.removeSetting("${it.key}")
    }
    logInfo  "All settings (preferences) DELETED"
}

// delete all attributes
void deleteAllCurrentStates() {
    device.properties.supportedAttributes.each { it->
        log.debug "deleting $it"
        device.deleteCurrentState("$it")
    }
    logInfo "All current states (attributes) DELETED"
}

// delete all State Variables
void deleteAllStates() {
    state.each { it->
        log.debug "deleting state ${it.key}"
    }
    state.clear()
    logInfo "All States DELETED"
}

void deleteAllScheduledJobs() {
    unschedule()
    logInfo "All scheduled jobs DELETED"
}

void deleteAllChildDevices() {
    logWarn "deleteAllChildDevices : not implemented!"
}

def parseTest(par) {
//read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A
    log.warn "parseTest(${par})"
    parse(par)
}

def testJob() {
    log.warn "test job executed"
}

/**
 * Calculates and returns the cron expression
 * @param timeInSeconds interval in seconds
 */
def getCron( timeInSeconds ) {
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping')
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours
    final Random rnd = new Random()
    def minutes = (timeInSeconds / 60 ) as int
    def hours = (minutes / 60 ) as int
    if (hours > 23) { hours = 23 }
    String cron
    if (timeInSeconds < 60) {
        cron = "*/$timeInSeconds * * * * ? *"
    }
    else {
        if (minutes < 60) {
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *"  
        }
        else {
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"                   
        }
    }
    return cron
}

def test(par) {
    ArrayList<String> cmds = []
    log.warn "test... ${par}"
    
    handleTemperatureEvent(safeToDouble(par) as float)
    
   // sendZigbeeCommands(cmds)    
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////


// ~~~~~ start include (134) kkossev.airQualityLib ~~~~~
library ( // library marker kkossev.airQualityLib, line 1
    base: "driver", // library marker kkossev.airQualityLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.airQualityLib, line 3
    category: "zigbee", // library marker kkossev.airQualityLib, line 4
    description: "Air Quality Library", // library marker kkossev.airQualityLib, line 5
    name: "airQualityLib", // library marker kkossev.airQualityLib, line 6
    namespace: "kkossev", // library marker kkossev.airQualityLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/main/libraries/airQualityLib.groovy", // library marker kkossev.airQualityLib, line 8
    version: "1.0.0", // library marker kkossev.airQualityLib, line 9
    documentationLink: "" // library marker kkossev.airQualityLib, line 10
) // library marker kkossev.airQualityLib, line 11
/* // library marker kkossev.airQualityLib, line 12
 * airQualityLib -Air Quality Library // library marker kkossev.airQualityLib, line 13
 * // library marker kkossev.airQualityLib, line 14
 * ver. 1.0.0  2023-07-23 kkossev  - Libraries introduction for the VINDSTIRKA driver; // library marker kkossev.airQualityLib, line 15
 * // library marker kkossev.airQualityLib, line 16
 *                                   TODO:  // library marker kkossev.airQualityLib, line 17
*/ // library marker kkossev.airQualityLib, line 18

def airQualityLibVersion()   {"1.0.0"} // library marker kkossev.airQualityLib, line 20
def airQualityimeStamp() {"2023/07/23 9:02 AM"} // library marker kkossev.airQualityLib, line 21

metadata { // library marker kkossev.airQualityLib, line 23
    attribute "pm25", "number" // library marker kkossev.airQualityLib, line 24
    attribute "airQualityLevel", "enum", ["Good","Moderate","Unhealthy for Sensitive Groups","Unhealthy","Very Unhealthy","Hazardous"]    // https://www.airnow.gov/aqi/aqi-basics/  // library marker kkossev.airQualityLib, line 25

    if (isAqaraTVOC()) { // library marker kkossev.airQualityLib, line 27
            capability "Battery" // library marker kkossev.airQualityLib, line 28
            attribute "batteryVoltage", "number" // library marker kkossev.airQualityLib, line 29
    } // library marker kkossev.airQualityLib, line 30

    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0004,0402,0405,FC57,FC7C,042A,FC7E", outClusters:"0003,0019,0020,0202", model:"VINDSTYRKA", manufacturer:"IKEA of Sweden", deviceJoinName: "VINDSTYRKA Air Quality Monitor E2112"  // library marker kkossev.airQualityLib, line 32
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0019", model:"lumi.airmonitor.acn01", manufacturer:"LUMI", deviceJoinName: "Aqara TVOC Air Quality Monitor"  // library marker kkossev.airQualityLib, line 33

    preferences { // library marker kkossev.airQualityLib, line 35
        if (isVINDSTYRKA()) { // library marker kkossev.airQualityLib, line 36
            input name: 'airQualityIndexCheckInterval', type: 'enum', title: '<b>Air Quality Index check interval</b>', options: AirQualityIndexCheckIntervalOpts.options, defaultValue: AirQualityIndexCheckIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub retreives the Air Quality Index.</i>' // library marker kkossev.airQualityLib, line 37
        } // library marker kkossev.airQualityLib, line 38
        else  if (isAqaraTRV()) { // library marker kkossev.airQualityLib, line 39
            input name: 'temperatureScale', type: 'enum', title: '<b>Temperaure Scale on the Screen</b>', options: TemperatureScaleOpts.options, defaultValue: TemperatureScaleOpts.defaultValue, required: true, description: '<i>Changes the temperature scale (Celsius, Fahrenheit) on the screen.</i>' // library marker kkossev.airQualityLib, line 40
            input name: 'tVocUnut', type: 'enum', title: '<b>tVOC unit on the Screen</b>', options: TvocUnitOpts.options, defaultValue: TvocUnitOpts.defaultValue, required: true, description: '<i>Changes the tVOC unit (mg/m³, ppb) on the screen.</i>' // library marker kkossev.airQualityLib, line 41
        } // library marker kkossev.airQualityLib, line 42
    } // library marker kkossev.airQualityLib, line 43
} // library marker kkossev.airQualityLib, line 44

@Field static final Map AirQualityIndexCheckIntervalOpts = [        // used by airQualityIndexCheckInterval // library marker kkossev.airQualityLib, line 46
    defaultValue: 60, // library marker kkossev.airQualityLib, line 47
    options     : [0: 'Disabled', 10: 'Every 10 seconds', 30: 'Every 30 seconds', 60: 'Every 1 minute', 300: 'Every 5 minutes', 900: 'Every 15 minutes', 3600: 'Every 1 hour'] // library marker kkossev.airQualityLib, line 48
] // library marker kkossev.airQualityLib, line 49

@Field static final Map TemperatureScaleOpts = [            // bit 7 // library marker kkossev.airQualityLib, line 51
    defaultValue: 0, // library marker kkossev.airQualityLib, line 52
    options     : [0: 'Celsius', 1: 'Fahrenheit'] // library marker kkossev.airQualityLib, line 53
] // library marker kkossev.airQualityLib, line 54

@Field static final Map TvocUnitOpts = [                    // bit 0 // library marker kkossev.airQualityLib, line 56
    defaultValue: 1, // library marker kkossev.airQualityLib, line 57
    options     : [0: 'mg/m³', 1: 'ppb'] // library marker kkossev.airQualityLib, line 58
] // library marker kkossev.airQualityLib, line 59


/* // library marker kkossev.airQualityLib, line 62
 * ----------------------------------------------------------------------------- // library marker kkossev.airQualityLib, line 63
 * airQualityIndex // library marker kkossev.airQualityLib, line 64
 * ----------------------------------------------------------------------------- // library marker kkossev.airQualityLib, line 65
*/ // library marker kkossev.airQualityLib, line 66
void parseAirQualityIndexCluster(final Map descMap) { // library marker kkossev.airQualityLib, line 67
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.airQualityLib, line 68
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.airQualityLib, line 69
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.airQualityLib, line 70
	Float floatValue = Float.intBitsToFloat(value.intValue())     // library marker kkossev.airQualityLib, line 71
    handleAirQualityIndexEvent(floatValue as Integer) // library marker kkossev.airQualityLib, line 72
} // library marker kkossev.airQualityLib, line 73

void handleAirQualityIndexEvent( Integer tVoc, Boolean isDigital=false ) { // library marker kkossev.airQualityLib, line 75
    def eventMap = [:] // library marker kkossev.airQualityLib, line 76
    if (state.stats != null) state.stats['tVocCtr'] = (state.stats['tVocCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.airQualityLib, line 77
    Integer tVocCorrected= safeToDouble(tVoc) + safeToDouble(settings?.tVocOffset ?: 0) // library marker kkossev.airQualityLib, line 78
    if (tVocCorrected < 0 || tVocCorrected > 999) { // library marker kkossev.airQualityLib, line 79
        logWarn "ignored invalid tVoc ${tVoc} (${tVocCorrected})" // library marker kkossev.airQualityLib, line 80
        return // library marker kkossev.airQualityLib, line 81
    } // library marker kkossev.airQualityLib, line 82
    if (safeToInt((device.currentState("airQualityIndex")?.value ?: -1)) == tVocCorrected) { // library marker kkossev.airQualityLib, line 83
        logDebug "ignored duplicated tVoc ${tVoc} (${tVocCorrected})" // library marker kkossev.airQualityLib, line 84
        return // library marker kkossev.airQualityLib, line 85
    } // library marker kkossev.airQualityLib, line 86
    eventMap.value = tVocCorrected as Integer // library marker kkossev.airQualityLib, line 87
    eventMap.name = "airQualityIndex" // library marker kkossev.airQualityLib, line 88
    eventMap.unit = "" // library marker kkossev.airQualityLib, line 89
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.airQualityLib, line 90
    eventMap.descriptionText = "${eventMap.name} is ${tVocCorrected} ${eventMap.unit}" // library marker kkossev.airQualityLib, line 91
    Integer timeElapsed = ((now() - (state.lastRx['tVocTime'] ?: now() -10000 ))/1000) as Integer // library marker kkossev.airQualityLib, line 92
    Integer minTime = settings?.minReportingTimetVoc ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.airQualityLib, line 93
    Integer timeRamaining = (minTime - timeElapsed) as Integer     // library marker kkossev.airQualityLib, line 94
    if (timeElapsed >= minTime) { // library marker kkossev.airQualityLib, line 95
        logInfo "${eventMap.descriptionText}" // library marker kkossev.airQualityLib, line 96
        unschedule("sendDelayedtVocEvent") // library marker kkossev.airQualityLib, line 97
        state.lastRx['tVocTime'] = now() // library marker kkossev.airQualityLib, line 98
        sendEvent(eventMap) // library marker kkossev.airQualityLib, line 99
        sendAirQualityLevelEvent(airQualityIndexToLevel(safeToInt(eventMap.value))) // library marker kkossev.airQualityLib, line 100
    } // library marker kkossev.airQualityLib, line 101
    else { // library marker kkossev.airQualityLib, line 102
    	eventMap.type = "delayed" // library marker kkossev.airQualityLib, line 103
        //logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.airQualityLib, line 104
        runIn(timeRamaining, 'sendDelayedtVocEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.airQualityLib, line 105
    } // library marker kkossev.airQualityLib, line 106
} // library marker kkossev.airQualityLib, line 107

private void sendDelayedtVocEvent(Map eventMap) { // library marker kkossev.airQualityLib, line 109
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.airQualityLib, line 110
    state.lastRx['tVocTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.airQualityLib, line 111
	sendEvent(eventMap) // library marker kkossev.airQualityLib, line 112
    sendAirQualityLevelEvent(airQualityIndexToLevel(safeToInt(eventMap.value))) // library marker kkossev.airQualityLib, line 113
} // library marker kkossev.airQualityLib, line 114

// https://github.com/zigpy/zigpy/discussions/691  // library marker kkossev.airQualityLib, line 116
String airQualityIndexToLevel(final Integer index) // library marker kkossev.airQualityLib, line 117
{ // library marker kkossev.airQualityLib, line 118
    String level // library marker kkossev.airQualityLib, line 119
    if (index <0 )        { level = 'unknown' } // library marker kkossev.airQualityLib, line 120
    else if (index < 50)  { level = 'Good' } // library marker kkossev.airQualityLib, line 121
    else if (index < 100) { level = 'Moderate' } // library marker kkossev.airQualityLib, line 122
    else if (index < 150) { level = 'Unhealthy for Sensitive Groups' } // library marker kkossev.airQualityLib, line 123
    else if (index < 200) { level = 'Unhealthy' } // library marker kkossev.airQualityLib, line 124
    else if (index < 300) { level = 'Very Unhealthy' } // library marker kkossev.airQualityLib, line 125
    else if (index < 501) { level = 'Hazardous' } // library marker kkossev.airQualityLib, line 126
    else                  { level = 'Hazardous Out of Range' } // library marker kkossev.airQualityLib, line 127

    return level // library marker kkossev.airQualityLib, line 129
} // library marker kkossev.airQualityLib, line 130

private void sendAirQualityLevelEvent(String level) { // library marker kkossev.airQualityLib, line 132
    if (level == null || level == '') { return } // library marker kkossev.airQualityLib, line 133
    def descriptionText = "Air Quality Level is ${level}" // library marker kkossev.airQualityLib, line 134
    logInfo "${descriptionText}" // library marker kkossev.airQualityLib, line 135
    sendEvent(name: "airQualityLevel", value: level, descriptionText: descriptionText, unit: "", isDigital: true)         // library marker kkossev.airQualityLib, line 136
} // library marker kkossev.airQualityLib, line 137

/** // library marker kkossev.airQualityLib, line 139
 * Schedule a  Air Quality Index check // library marker kkossev.airQualityLib, line 140
 * @param intervalMins interval in seconds // library marker kkossev.airQualityLib, line 141
 */ // library marker kkossev.airQualityLib, line 142
private void scheduleAirQualityIndexCheck(final int intervalSecs) { // library marker kkossev.airQualityLib, line 143
    String cron = getCron( intervalSecs ) // library marker kkossev.airQualityLib, line 144
    schedule(cron, 'autoPoll') // library marker kkossev.airQualityLib, line 145
} // library marker kkossev.airQualityLib, line 146

private void unScheduleAirQualityIndexCheck() { // library marker kkossev.airQualityLib, line 148
    unschedule('autoPoll') // library marker kkossev.airQualityLib, line 149
} // library marker kkossev.airQualityLib, line 150

def configureDeviceAirQuality() { // library marker kkossev.airQualityLib, line 152
    ArrayList<String> cmds = [] // library marker kkossev.airQualityLib, line 153
    if (isAqaraTVOC()) { // library marker kkossev.airQualityLib, line 154
        logDebug 'configureDeviceAirQuality() AqaraTVOC' // library marker kkossev.airQualityLib, line 155
        // https://forum.phoscon.de/t/aqara-tvoc-zhaairquality-data/1160/21 // library marker kkossev.airQualityLib, line 156
        final int tScale = (settings.temperatureScale as Integer) ?: TemperatureScaleOpts.defaultValue // library marker kkossev.airQualityLib, line 157
        final int tUnit =  (settings.tVocUnut as Integer) ?: TvocUnitOpts.defaultValue // library marker kkossev.airQualityLib, line 158
        logDebug "setting temperatureScale to ${TemperatureScaleOpts.options[tScale]} (${tScale})" // library marker kkossev.airQualityLib, line 159
        int cfg = tUnit // library marker kkossev.airQualityLib, line 160
        cfg |= (tScale << 4) // library marker kkossev.airQualityLib, line 161
        cmds += zigbee.writeAttribute(0xFCC0, 0x0114, DataType.UINT8, cfg, [mfgCode: 0x115F], delay=200) // library marker kkossev.airQualityLib, line 162
        cmds += zigbee.readAttribute(0xFCC0, 0x0114, [mfgCode: 0x115F], delay=200)     // library marker kkossev.airQualityLib, line 163
    } // library marker kkossev.airQualityLib, line 164
    else if (isVINDSTYRKA()) { // library marker kkossev.airQualityLib, line 165
        logDebug 'configureDeviceAirQuality() VINDSTYRKA (nothig to configure)' // library marker kkossev.airQualityLib, line 166
    } // library marker kkossev.airQualityLib, line 167
    else { // library marker kkossev.airQualityLib, line 168
        logWarn "configureDeviceAirQuality: unsupported device?" // library marker kkossev.airQualityLib, line 169
    } // library marker kkossev.airQualityLib, line 170
    return cmds // library marker kkossev.airQualityLib, line 171
} // library marker kkossev.airQualityLib, line 172


def initializeDeviceAirQuality() { // library marker kkossev.airQualityLib, line 175
    ArrayList<String> cmds = [] // library marker kkossev.airQualityLib, line 176
    if (isAqaraTVOC()) { // library marker kkossev.airQualityLib, line 177
        logDebug 'initializeDeviceAirQuality() AqaraTVOC' // library marker kkossev.airQualityLib, line 178
	    return zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020)+ // library marker kkossev.airQualityLib, line 179
			zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000)+ // library marker kkossev.airQualityLib, line 180
			zigbee.readAttribute(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0x0000) + // library marker kkossev.airQualityLib, line 181
			zigbee.readAttribute(ANALOG_INPUT_BASIC_CLUSTER, ANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE) + // library marker kkossev.airQualityLib, line 182
			zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0x0000, DataType.UINT16, 30, 300, 1*100) + // library marker kkossev.airQualityLib, line 183
			zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000, DataType.INT16, 30, 300, 0x1) + // library marker kkossev.airQualityLib, line 184
			zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020, DataType.UINT8, 30, 21600, 0x1) +  // library marker kkossev.airQualityLib, line 185
			zigbee.configureReporting(ANALOG_INPUT_BASIC_CLUSTER, ANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE, DataType.FLOAT4, 10, 3600, 5)     // library marker kkossev.airQualityLib, line 186
    }  // library marker kkossev.airQualityLib, line 187
    else if (isVINDSTYRKA()) { // library marker kkossev.airQualityLib, line 188
        logDebug 'initializeDeviceAirQuality() VINDSTYRKA' // library marker kkossev.airQualityLib, line 189
        // Ikea VINDSTYRKA : bind clusters 402, 405, 42A (PM2.5) // library marker kkossev.airQualityLib, line 190
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                 // 402 - temperature // library marker kkossev.airQualityLib, line 191
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)    // 405 - humidity // library marker kkossev.airQualityLib, line 192
        cmds += zigbee.configureReporting(0x042a, 0, 0x39, 30, 60, 1)    // 405 - pm2.5 // library marker kkossev.airQualityLib, line 193
        //cmds += zigbee.configureReporting(0xfc7e, 0, 0x39, 10, 60, 50)     // provides a measurement in the range of 0-500 that correlates with the tVOC trend display on the unit itself. // library marker kkossev.airQualityLib, line 194
        cmds += ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0xfc7e {${device.zigbeeId}} {}", "delay 251", ] // library marker kkossev.airQualityLib, line 195

    } // library marker kkossev.airQualityLib, line 197
    else { // library marker kkossev.airQualityLib, line 198
        logWarn "initializeDeviceAirQuality: unsupported device?" // library marker kkossev.airQualityLib, line 199
    } // library marker kkossev.airQualityLib, line 200
    return cmds // library marker kkossev.airQualityLib, line 201
} // library marker kkossev.airQualityLib, line 202

void updatedAirQuality() { // library marker kkossev.airQualityLib, line 204
    if (!(isAqaraTVOC())) { // library marker kkossev.airQualityLib, line 205
        final int intervalAirQuality = (settings.airQualityIndexCheckInterval as Integer) ?: 0 // library marker kkossev.airQualityLib, line 206
        if (intervalAirQuality > 0) { // library marker kkossev.airQualityLib, line 207
            logInfo "updatedAirQuality: scheduling Air Quality Index check every ${intervalAirQuality} seconds" // library marker kkossev.airQualityLib, line 208
            scheduleAirQualityIndexCheck(intervalAirQuality) // library marker kkossev.airQualityLib, line 209
        } // library marker kkossev.airQualityLib, line 210
        else { // library marker kkossev.airQualityLib, line 211
            unScheduleAirQualityIndexCheck() // library marker kkossev.airQualityLib, line 212
            logInfo "updatedAirQuality: Air Quality Index polling is disabled!" // library marker kkossev.airQualityLib, line 213
        } // library marker kkossev.airQualityLib, line 214

    } // library marker kkossev.airQualityLib, line 216
    else { // library marker kkossev.airQualityLib, line 217
        logDebug "updatedAirQuality: skipping airQuality polling" // library marker kkossev.airQualityLib, line 218
    } // library marker kkossev.airQualityLib, line 219
} // library marker kkossev.airQualityLib, line 220

def refreshAirQuality() { // library marker kkossev.airQualityLib, line 222
    List<String> cmds = [] // library marker kkossev.airQualityLib, line 223
    if (isAqaraTVOC()) { // library marker kkossev.airQualityLib, line 224
            // TODO - check what is available for VINDSTYRKA // library marker kkossev.airQualityLib, line 225
	        cmds += zigbee.readAttribute(0x042a, 0x0000, [:], delay=200)                    // pm2.5    attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; 3:Tolerance // library marker kkossev.airQualityLib, line 226
	        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay=200)      // tVOC   !! mfcode="0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.airQualityLib, line 227
    } // library marker kkossev.airQualityLib, line 228
        else if (false) { // library marker kkossev.airQualityLib, line 229
            // TODO - check what is available for Aqara  // library marker kkossev.airQualityLib, line 230
        } // library marker kkossev.airQualityLib, line 231
        else { // library marker kkossev.airQualityLib, line 232
            // TODO - unknown AirQuaility sensor - try all ?? // library marker kkossev.airQualityLib, line 233
        } // library marker kkossev.airQualityLib, line 234

    logDebug "refreshAirQuality() : ${cmds}" // library marker kkossev.airQualityLib, line 236
    return cmds // library marker kkossev.airQualityLib, line 237
} // library marker kkossev.airQualityLib, line 238

def initVarsAirQuality(boolean fullInit=false) { // library marker kkossev.airQualityLib, line 240
    logDebug "initVarsAirQuality(${fullInit})" // library marker kkossev.airQualityLib, line 241
    if (fullInit || settings?.airQualityIndexCheckInterval == null) device.updateSetting('airQualityIndexCheckInterval', [value: AirQualityIndexCheckIntervalOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.airQualityLib, line 242
    if (fullInit || settings?.TemperatureScaleOpts == null) device.updateSetting('temperatureScale', [value: TemperatureScaleOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.airQualityLib, line 243
    if (fullInit || settings?.tVocUnut == null) device.updateSetting('tVocUnut', [value: TvocUnitOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.airQualityLib, line 244

} // library marker kkossev.airQualityLib, line 246

void initEventsAirQuality(boolean fullInit=false) { // library marker kkossev.airQualityLib, line 248
//    sendNumberOfButtonsEvent(6) // library marker kkossev.airQualityLib, line 249
//    def supportedValues = ["pushed", "double", "held", "released", "tested"] // library marker kkossev.airQualityLib, line 250
//    sendSupportedButtonValuesEvent(supportedValues) // library marker kkossev.airQualityLib, line 251
} // library marker kkossev.airQualityLib, line 252

// ~~~~~ end include (134) kkossev.airQualityLib ~~~~~
