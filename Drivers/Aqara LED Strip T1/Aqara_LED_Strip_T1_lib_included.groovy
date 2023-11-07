/**
 *  Aqara LED Strip T1 - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *     in compliance with the License. You may obtain a copy of the License at:
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *     on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *     for the specific language governing permissions and limitations under the License.
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
 * ver. 2.1.2  2023-07-23 kkossev  - VYNDSTIRKA library; Switch library; Fingerbot library; IR Blaster Library; fixed the exponential (3E+1) temperature representation bug;
 * ver. 2.1.3  2023-08-28 kkossev  - ping() improvements; added ping OK, Fail, Min, Max, rolling average counters; added clearStatistics(); added updateTuyaVersion() updateAqaraVersion(); added HE hub model and platform version; Tuya mmWave Radar driver; processTuyaDpFingerbot; added Momentary capability for Fingerbot
 * ver. 2.1.4  2023-09-09 kkossev  - buttonDimmerLib library; added IKEA Styrbar E2001/E2002, IKEA on/off switch E1743, IKEA remote control E1810; added Identify cluster; Ranamed 'Zigbee Button Dimmer'; bugfix - Styrbar ignore button 1; IKEA RODRET E2201  key #4 changed to key #2; added IKEA TRADFRI open/close remote E1766; added thermostatLib; added xiaomiLib
 * ver. 2.1.5  2023-11-06 kkossev  - (dev. branch) Aqara E1 thermostat; added deviceProfileLib; Aqara LED Strip T1 driver;
 *
 *                                   TODO: auto turn off Debug messages 15 seconds after installing the new device
 *                                   TODO: Aqara TVOC: implement battery level/percentage 
 *                                   TODO: check  catchall: 0000 0006 00 00 0040 00 E51C 00 00 0000 00 00 01FDFF040101190000      (device object UNKNOWN_CLUSTER (0x0006) error: 0xFD)
 *                                   TODO: skip thresholds checking for T,H,I ... on maxReportingTime
 *                                   TODO: measure PTT for on/off commands
 *                                   TODO: add rejonCtr to statistics
 *                                   TODO: implement Get Device Info command
 *                                   TODO: continue the work on the 'device' capability (this project main goal!)
 *                                   TODO: state timesamps in human readable form
 *                                   TODO: parse the details of the configuration respose - cluster, min, max, delta ...
 *                                   TODO: battery min/max voltage preferences
 *                                   TODO: Configure: add custom Notes
 */

static String version() { "2.1.5" }
static String timeStamp() {"2023/11/07 5:23 PM"}

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
//
//#include kkossev.zigbeeScenes
//deviceType = "AirQuality"
//@Field static final String DEVICE_TYPE = "AirQuality"
//
//#include kkossev.airQualityLib
//deviceType = "Fingerbot"
//@Field static final String DEVICE_TYPE = "Fingerbot"
//#include kkossev.tuyaFingerbotLib
//deviceType = "Thermostat"
//@Field static final String DEVICE_TYPE = "Thermostat"
//
//#include kkossev.thermostatLib
//#include kkossev.deviceProfileLib
//deviceType = "Switch"
//@Field static final String DEVICE_TYPE = "Switch"
//#include kkossev.tuyaZigbeeSwitchLib
//deviceType = "Dimmer"
//@Field static final String DEVICE_TYPE = "Dimmer"
//deviceType = "ButtonDimmer"
//@Field static final String DEVICE_TYPE = "ButtonDimmer"
//#include kkossev.buttonDimmerLib
//deviceType = "LightSensor"
//@Field static final String DEVICE_TYPE = "LightSensor"
deviceType = "Bulb"
@Field static final String DEVICE_TYPE = "Bulb"


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
//
//#include kkossev.aqaraCubeT1ProLib
//deviceType = "IRBlaster"
//@Field static final String DEVICE_TYPE = "IRBlaster"
//#include kkossev.irBlasterLib
//deviceType = "Radar"
//@Field static final String DEVICE_TYPE = "Radar"
//#include kkossev.tuyaRadarLib

@Field static final Boolean _THREE_STATE = true

metadata {
    definition (
        //name: 'Tuya Zigbee Device',
        //name: 'VINDSTYRKA Air Quality Monitor',
        //name: 'Aqara TVOC Air Quality Monitor',
        //name: 'Tuya Zigbee Fingerbot',
        //name: 'Aqara E1 Thermostat',
        //name: 'Tuya Zigbee Switch',
        //name: 'Tuya Zigbee Dimmer',
        //name: 'Zigbee Button Dimmer',
        //name: 'Tuya Zigbee Light Sensor',
        name: 'Aqara LED Strip T1',
        //name: 'Tuya Zigbee Relay',
        //name: 'Tuya Zigbee Plug V2',
        //name: 'Aqara Cube T1 Pro',
        //name: 'Tuya Zigbee IR Blaster',
        //name: 'Tuya mmWave Radar',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Device%20Driver/Tuya%20Zigbee%20Device.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/VINDSTYRKA%20Air%20Quality%20Monitor/VINDSTYRKA_Air_Quality_Monitor_lib_included.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20TVOC%20Air%20Quality%20Monitor/Aqara_TVOC_Air_Quality_Monitor_lib_included.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Fingerbot/Tuya_Zigbee_Fingerbot_lib_included.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20E1%20Thermostat/Aqara_E1_Thermostat_lib_included.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Plug/Tuya%20Zigbee%20Plug%20V2.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Switch/Tuya%20Zigbee%20Switch.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Dimmer/Tuya%20Zigbee%20Dimmer.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20Button%20Dimmer/Zigbee_Button_Dimmer_lib_included.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Light%20Sensor/Tuya%20Zigbee%20Light%20Sensor.groovy',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%LED%20Strip%20T1/Aqara%20LED%20Strip%20T1.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20Cube%20T1%20Pro/Aqara_Cube_T1_Pro_lib_included.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya_Zigbee_IR_Blaster/Tuya_Zigbee_IR_Blaster_lib_included.groovy',
        //importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Multi%20Sensor%204%20In%201/Tuya_mmWave_Radar.groovy',
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
        if (deviceType in  ["Device", "THSensor", "MotionSensor", "LightSensor", "AirQuality", "Thermostat", "AqaraCube", "Radar"]) {
            capability "Sensor"
        }
        if (deviceType in  ["Device", "MotionSensor", "Radar"]) {
            capability "MotionSensor"
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
        if (deviceType in  ["Device", "Switch", "Plug", "Outlet", "Dimmer", "Fingerbot", "Bulb"]) {
            capability "Switch"
            if (_THREE_STATE == true) {
                attribute "switch", "enum", SwitchThreeStateOpts.options.values() as List<String>
            }
        }        
        if (deviceType in ["Dimmer", "ButtonDimmer", "Bulb"]) {
            capability "SwitchLevel"
        }
        /*
        if (deviceType in  ["Fingerbot"]) {
            capability "PushableButton"
        }
        */
        if (deviceType in  ["Button", "ButtonDimmer", "AqaraCube"]) {
            capability "PushableButton"
            capability "DoubleTapableButton"
            capability "HoldableButton"
            capability "ReleasableButton"
        }
        if (deviceType in  ["Device", "Fingerbot"]) {
            capability "Momentary"
        }
        if (deviceType in  ["Device", "THSensor", "AirQuality", "Thermostat"]) {
            capability "TemperatureMeasurement"
        }
        if (deviceType in  ["Device", "THSensor", "AirQuality"]) {
            capability "RelativeHumidityMeasurement"            
        }
        if (deviceType in  ["Device", "LightSensor", "Radar"]) {
            capability "IlluminanceMeasurement"
        }
        if (deviceType in  ["AirQuality"]) {
            capability "AirQuality"            // Attributes: airQualityIndex - NUMBER, range:0..500
        }

        // trap for Hubitat F2 bug
        fingerprint profileId:"0104", endpointId:"F2", inClusters:"", outClusters:"", model:"unknown", manufacturer:"unknown", deviceJoinName: "Zigbee device affected by Hubitat F2 bug" 
        if (deviceType in  ["LightSensor"]) {
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0001,0500", outClusters:"0019,000A", model:"TS0222", manufacturer:"_TYZB01_4mdqxxnn", deviceJoinName: "Tuya Illuminance Sensor TS0222"
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_khx7nnka", deviceJoinName: "Tuya Illuminance Sensor TS0601"
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_yi4jtqq1", deviceJoinName: "Tuya Illuminance Sensor TS0601"
        }
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
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
            input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>'
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
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 30      // automatically clear the Info attribute after 30 seconds

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

@Field static final Map ZigbeeGroupsOptsDebug = [
    defaultValue: 0,
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying']
]
@Field static final Map ZigbeeGroupsOpts = [
    defaultValue: 0,
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups']
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
    
    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {    
        logDebug "parse: zone status: $description"
        if (true /*isHL0SS9OAradar() && _IGNORE_ZCL_REPORTS == true*/) {    // TODO!
            logDebug "ignored IAS zone status"
            return
        }
        else {
            parseIasMessage(description)    // TODO!
        }
    }
    else if (description?.startsWith('enroll request')) {
        logDebug "parse: enroll request: $description"
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */
        if (settings?.logEnable) logInfo "Sending IAS enroll response..."
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000)
        logDebug "enroll response: ${cmds}"
        sendZigbeeCommands( cmds )  
    } 
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {
        return
    }        
    final Map descMap = myParseDescriptionAsMap(description)
    
    if (descMap.profileId == '0000') {
        parseZdoClusters(descMap)
        return
    }
    if (descMap.isClusterSpecific == false) {
        parseGeneralCommandResponse(descMap)
        return
    }
    if (!isChattyDeviceReport(description)) {logDebug "parse: descMap = ${descMap} description=${description}"}
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
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003
            parseIdentityCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) }
            break
        case zigbee.GROUPS_CLUSTER:                        // 0x0004
            parseGroupsCluster(descMap)
            descMap.remove('additionalAttrs')?.each {final Map map -> parseGroupsCluster(descMap + map) }
            break
        case zigbee.SCENES_CLUSTER:                         // 0x0005
            parseScenesCluster(descMap)
            descMap.remove('additionalAttrs')?.each {final Map map -> parseScenesCluster(descMap + map) }
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
         case 0x0102 :                                      // window covering 
            parseWindowCoveringCluster(descMap)
            break       
        case 0x0201 :                                       // Aqara E1 TRV 
            parseThermostatCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) }
            break
        case 0x0300 :                                       // Aqara LED Strip T1
            parseColorControlCluster(descMap, description)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) }
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
        case 0xE002 :
            parseE002Cluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) }
            break
        case 0xEF00 :                                       // Tuya famous cluster
            parseTuyaCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) }
            break
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf
            parseAirQualityIndexCluster(descMap)
            break
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster
            parseXiaomiCluster(descMap)
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) }
            break
        default:
            if (settings.logEnable) {
                logWarn "zigbee received <b>unknown cluster:${descMap.clusterId}</b> message (${descMap})"
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
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})"
    } 
    else {
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}"
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
    0x0002: 'Node Descriptor Request',
    0x0005: 'Active Endpoints Request',
    0x0006: 'Match Descriptor Request',
    0x0022: 'Unbind Request',
    0x0013: 'Device announce',
    0x0034: 'Management Leave Request',
    0x8002: 'Node Descriptor Response',
    0x8004: 'Simple Descriptor Response',
    0x8005: 'Active Endpoints Response',
    0x801D: 'Extended Simple Descriptor Response',
    0x801E: 'Extended Active Endpoint Response',
    0x8021: 'Bind Response',
    0x8022: 'Unbind Response',
    0x8023: 'Bind Register Response',
    0x8034: 'Management Leave Response'
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
void parseXiaomiCluster(final Map descMap) {
    if (xiaomiLibVersion() != null) {
        parseXiaomiClusterLib(descMap)
    }    
    else {
        logWarn "Xiaomi cluster 0xFCC0"
    }
}


/*
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
*/


// TODO - move to xiaomiLib
// TODO - move to thermostatLib
// TODO - move to aqaraQubeLib




@Field static final int ROLLING_AVERAGE_N = 10
double approxRollingAverage (double avg, double new_sample) {
    if (avg == null || avg == 0) { avg = new_sample}
    avg -= avg / ROLLING_AVERAGE_N
    avg += new_sample / ROLLING_AVERAGE_N
    // TOSO: try Method II : New average = old average * (n-1)/n + new value /n
    return avg
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
    if (state.lastRx == null) { state.lastRx = [:] }
    if (state.lastTx == null) { state.lastTx = [:] }
    if (state.states == null) { state.states = [:] }
    if (state.stats == null) { state.stats = [:] }
    state.lastRx["checkInTime"] = now
    switch (descMap.attrInt as Integer) {
        case 0x0000:
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}"
            break
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism
            boolean isPing = state.states["isPing"] ?: false
            if (isPing) {
                def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: '0').toInteger()
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) {
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning }
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning }
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']),safeToDouble(timeRunning)) as int
                    sendRttEvent()
                }
                else {
                    logWarn "unexpected ping timeRunning=${timeRunning} "
                }
                state.states["isPing"] = false
            }
            else {
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})"
            }
            break
        case 0x0004:
            logDebug "received device manufacturer ${descMap?.value}"
            // received device manufacturer IKEA of Sweden
            def manufacturer = device.getDataValue("manufacturer")
            if ((manufacturer == null || manufacturer == "unknown") && (descMap?.value != null) ) {
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}"
                device.updateDataValue("manufacturer", descMap?.value)
            }
            break
        case 0x0005:
            logDebug "received device model ${descMap?.value}"
            // received device model Remote Control N2
            def model = device.getDataValue("model")
            if ((model == null || model == "unknown") && (descMap?.value != null) ) {
                logWarn "updating device model from ${model} to ${descMap?.value}"
                device.updateDataValue("model", descMap?.value)
            }
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
    if ((batteryPercent as int) == 255) {
        logWarn "ignoring battery report raw=${batteryPercent}"
        return
    }
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
 * Zigbee Identity Cluster 0x0003
 * -----------------------------------------------------------------------------
*/

void parseIdentityCluster(final Map descMap) {
    logDebug "unprocessed parseIdentityCluster"
}



/*
 * -----------------------------------------------------------------------------
 * Zigbee Scenes Cluster 0x005
 * -----------------------------------------------------------------------------
*/

void parseScenesCluster(final Map descMap) {
    if (DEVICE_TYPE in ["ButtonDimmer"]) {
        parseScenesClusterButtonDimmer(descMap)
    }    
    else {
        logWarn "unprocessed ScenesCluste attribute ${descMap.attrId}"
    }
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
    if (DEVICE_TYPE in ["ButtonDimmer"]) {
        parseOnOffClusterButtonDimmer(descMap)
    }    

    else if (descMap.attrId == "0000") {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        final long rawValue = hexStrToUnsignedInt(descMap.value)
        sendSwitchEvent(rawValue)
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

def toggle() {
    def descriptionText = "central button switch is "
    def state = ""
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) {
        state = "on"
    }
    else {
        state = "off"
    }
    descriptionText += state
    sendEvent(name: "switch", value: state, descriptionText: descriptionText, type: "physical", isStateChange: true)
    logInfo "${descriptionText}"
}

def off() {
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOff(); return }
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
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOn(); return }
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

Map myParseDescriptionAsMap( String description )
{
    def descMap = [:]
    try {
        descMap = zigbee.parseDescriptionAsMap(description)
    }
    catch (e1) {
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}"
        // try alternative custom parsing
        descMap = [:]
        try {
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry ->
                def pair = entry.split(':')
                [(pair.first().trim()): pair.last().trim()]
            }        
        }
        catch (e2) {
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}"
            return [:]
        }
        logDebug "alternative method parsing success: descMap=${descMap}"
    }
    return descMap
}

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
    catch (e1) {
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}"
        // try alternative custom parsing
        descMap = [:]
        try {
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry ->
                def pair = entry.split(':')
                [(pair.first().trim()): pair.last().trim()]
            }        
        }
        catch (e2) {
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}"
            return true
        }
        logDebug "alternative method parsing success: descMap=${descMap}"
    }
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"}        
    if (descMap.attrId == null ) {
        //logDebug "otherTuyaOddities: descMap = ${descMap}"
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping"
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
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping"
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

def sendButtonEvent(buttonNumber, buttonState, isDigital=false) {
    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital==true ? 'digital' : 'physical']
    if (txtEnable) {log.info "${device.displayName} $event.descriptionText"}
    sendEvent(event)
}

def push() {                // Momentary capability
    logDebug "push momentary"
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(); return }    
    logWarn "push() not implemented for ${(DEVICE_TYPE)}"
}

def push(buttonNumber) {    //pushableButton capability
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(buttonNumber); return }    
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
    if (DEVICE_TYPE in ["ButtonDimmer"]) {
        parseLevelControlClusterButtonDimmer(descMap)
    }
    else if (DEVICE_TYPE in ["Bulb"]) {
        parseLevelControlClusterBulb(descMap)
    }
    else if (descMap.attrId == "0000") {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        final long rawValue = hexStrToUnsignedInt(descMap.value)
        sendLevelControlEvent(rawValue)
    }
    else {
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}"
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
    if (DEVICE_TYPE in  ["ButtonDimmer"]) { setLevelButtonDimmer(value, transitionTime); return }
    if (DEVICE_TYPE in  ["Bulb"]) { setLevelBulb(value, transitionTime); return }
    else {
        final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer)
        scheduleCommandTimeoutCheck()
        /*return*/ sendZigbeeCommands ( setLevelPrivate(value, rate))
    }
}

/*
 * -----------------------------------------------------------------------------
 * Color Control Cluster            0x0300
 * -----------------------------------------------------------------------------
*/
void parseColorControlCluster(final Map descMap, description) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (DEVICE_TYPE in ["Bulb"]) {
        parseColorControlClusterBulb(descMap, description)
    }
    else if (descMap.attrId == "0000") {
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value
        final long rawValue = hexStrToUnsignedInt(descMap.value)
        sendLevelControlEvent(rawValue)
    }
    else {
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}"
    }
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
        unschedule("sendDelayedIllumEvent")        //get rid of stale queued reports
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
        unschedule("sendDelayedTempEvent")        //get rid of stale queued reports
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
 * Electrical Measurement Cluster 0x0702
 * -----------------------------------------------------------------------------
*/

void parseElectricalMeasureCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    if (DEVICE_TYPE in  ["Switch"]) {
        parseElectricalMeasureClusterSwitch(descMap)
    }
    else {
        logWarn "parseElectricalMeasureCluster is NOT implemented1"
    }
}

/*
 * -----------------------------------------------------------------------------
 * Metering Cluster 0x0B04
 * -----------------------------------------------------------------------------
*/

void parseMeteringCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    if (DEVICE_TYPE in  ["Switch"]) {
        parseMeteringClusterSwitch(descMap)
    }
    else {
        logWarn "parseMeteringCluster is NOT implemented1"
    }
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
 * Window Covering Cluster 0x0102
 * -----------------------------------------------------------------------------
*/

void parseWindowCoveringCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (DEVICE_TYPE in  ["ButtonDimmer"]) {
        parseWindowCoveringClusterButtonDimmer(descMap)
    }
    else {
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}"
    }
}

/*
 * -----------------------------------------------------------------------------
 * thermostat cluster 0x0201
 * -----------------------------------------------------------------------------
*/
void parseThermostatCluster(final Map descMap) {
    if (state.lastRx == null) { state.lastRx = [:] }
    if (DEVICE_TYPE in  ["Thermostat"]) {
        parseThermostatClusterThermostat(descMap)
    }
    else {
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}"
    }
}



// -------------------------------------------------------------------------------------------------------------------------

def parseE002Cluster( descMap ) {
    if (DEVICE_TYPE in ["Radar"])     { parseE002ClusterRadar(descMap) }    
    else {
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"
    }
}


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

void processTuyaDP(descMap, dp, dp_id, fncmd) {
    if (DEVICE_TYPE in ["Radar"])         { processTuyaDpRadar(descMap, dp, dp_id, fncmd); return }    
    if (DEVICE_TYPE in ["Fingerbot"])     { processTuyaDpFingerbot(descMap, dp, dp_id, fncmd); return }    
    switch (dp) {
        case 0x01 : // on/off
            if (DEVICE_TYPE in  ["LightSensor"]) {
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})"
            }
            else {
                sendSwitchEvent(fncmd)
            }
            break
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
    if (DEVICE_TYPE in  ["AirQuality"])          { return initializeDeviceAirQuality() }
    else if (DEVICE_TYPE in  ["IRBlaster"])      { return initializeDeviceIrBlaster() }
    else if (DEVICE_TYPE in  ["Radar"])          { return initializeDeviceRadar() }
    else if (DEVICE_TYPE in  ["ButtonDimmer"])   { return initializeDeviceButtonDimmer() }
  
 
    // not specific device type - do some generic initializations
    if (DEVICE_TYPE in  ["THSensor"]) {
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity
    }
    //
    if (cmds == []) {
        cmds = ["delay 299"]
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
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += configureDeviceFingerbot() }
    else if (DEVICE_TYPE in  ["AqaraCube"])  { cmds += configureDeviceAqaraCube() }
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += configureDeviceSwitch() }
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += configureDeviceIrBlaster() }
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += configureDeviceRadar() }
    else if (DEVICE_TYPE in  ["ButtonDimmer"]) { cmds += configureDeviceButtonDimmer() }
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += configureBulb() }
    if (cmds == []) { 
        cmds = ["delay 277",]
    }
    sendZigbeeCommands(cmds)  
}

/*
 * -----------------------------------------------------------------------------
 * Hubitat default handlers methods
 * -----------------------------------------------------------------------------
*/

def refresh() {
    logInfo "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}"
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
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += refreshRadar() }
    else if (DEVICE_TYPE in  ["Thermostat"]) { cmds += refreshThermostat() }
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += refreshBulb() }
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
    }

    runInMillis( REFRESH_TIMER, clearRefreshRequest, [overwrite: true])                 // 3 seconds
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }
    else {
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}"
    }
}

def clearRefreshRequest() { if (state.states == null) {state.states = [:] }; state.states["isRefresh"] = false }

void clearInfoEvent() {
    sendInfoEvent('clear')
}

void sendInfoEvent(String info=null) {
    if (info == null || info == "clear") {
        logDebug "clearing the Info event"
        sendEvent(name: "Info", value: " ", isDigital: true)
    }
    else {
        logInfo "${info}"
        sendEvent(name: "Info", value: info, isDigital: true)
        runIn(INFO_AUTO_CLEAR_PERIOD, "clearInfoEvent")            // automatically clear the Info attribute after 1 minute
    }
}

def ping() {
    if (!(isAqaraTVOC())) {
        if (state.lastTx == nill ) state.lastTx = [:] 
        state.lastTx["pingTime"] = new Date().getTime()
        if (state.states == nill ) state.states = [:] 
        state.states["isPing"] = true
        scheduleCommandTimeoutCheck()
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) )
        logDebug 'ping...'
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
    def descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats["pingsMin"]} max=${state.stats["pingsMax"]} average=${state.stats["pingsAvg"]})"
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
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1
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
    if (settings.traceEnable) {
        logDebug settings
        runIn(1800, traceOff)
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
    if (DEVICE_TYPE in ["Thermostat"])  { updatedThermostat() }
        
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
void traceOff() {
    logInfo "trace logging disabled..."
    device.updateSetting('traceEnable', [value: 'false', type: 'bool'])
}

@Field static final Map ConfigureOpts = [
    "Configure the device only"  : [key:2, function: 'configure'],
    "Reset Statistics"           : [key:9, function: 'resetStatistics'],
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
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" }
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
    updated() // calls  also   configureDevice()
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
    updateTuyaVersion()
    updateAqaraVersion()
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
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats=[:] }
    }
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] }
    sendHubCommand(allActions)
}

def driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? " (debug version!) " : " ") + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString}) "}

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
        updateTuyaVersion()
        updateAqaraVersion()
    }
    else {
        // no driver version change
    }
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


def resetStatistics() {
    runIn(1, "resetStats")
    sendInfoEvent("Statistics are reset. Refresh the web page")
}

/**
 * called from TODO
 * 
 */
def resetStats() {
    logDebug "resetStats..."
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
    
    if (fullInit || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
    if (fullInit || settings?.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit || settings?.traceEnable == null) device.updateSetting("traceEnable", false)
    if (fullInit || settings?.advancedOptions == null) device.updateSetting("advancedOptions", [value:false, type:"bool"])
    if (fullInit || settings?.healthCheckMethod == null) device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.healthCheckInterval == null) device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum'])
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown')
    if (fullInit || settings?.threeStateEnable == null) device.updateSetting("threeStateEnable", false)
    if (fullInit || settings?.voltageToPercent == null) device.updateSetting("voltageToPercent", false)
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
    if (DEVICE_TYPE in ["Fingerbot"])  { initVarsFingerbot(fullInit); initEventsFingerbot(fullInit) }
    if (DEVICE_TYPE in ["AqaraCube"])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) }
    if (DEVICE_TYPE in ["Switch"])     { initVarsSwitch(fullInit);    initEventsSwitch(fullInit) }         // none
    if (DEVICE_TYPE in ["IRBlaster"])  { initVarsIrBlaster(fullInit); initEventsIrBlaster(fullInit) }      // none
    if (DEVICE_TYPE in ["Radar"])      { initVarsRadar(fullInit);     initEventsRadar(fullInit) }          // none
    if (DEVICE_TYPE in ["ButtonDimmer"]) { initVarsButtonDimmer(fullInit);     initEventsButtonDimmer(fullInit) }
    if (DEVICE_TYPE in ["Thermostat"]) { initVarsThermostat(fullInit);     initEventsThermostat(fullInit) }
    if (DEVICE_TYPE in ["Bulb"])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) }

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

def logTrace(msg) {
    if (settings.traceEnable) {
        log.trace "${device.displayName} " + msg
    }
}



// _DEBUG mode only
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
        logDebug "deleting ${it.key}"
        device.removeSetting("${it.key}")
    }
    logInfo  "All settings (preferences) DELETED"
}

// delete all attributes
void deleteAllCurrentStates() {
    device.properties.supportedAttributes.each { it->
        logDebug "deleting $it"
        device.deleteCurrentState("$it")
    }
    logInfo "All current states (attributes) DELETED"
}

// delete all State Variables
void deleteAllStates() {
    state.each { it->
        logDebug "deleting state ${it.key}"
    }
    state.clear()
    logInfo "All States DELETED"
}

void deleteAllScheduledJobs() {
    unschedule()
    logInfo "All scheduled jobs DELETED"
}

void deleteAllChildDevices() {
    logDebug "deleteAllChildDevices : not implemented!"
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

boolean isTuya() {
    def model = device.getDataValue("model") 
    def manufacturer = device.getDataValue("manufacturer") 
    if (model?.startsWith("TS") && manufacturer?.startsWith("_TZ")) {
        return true
    }
    return false
}

void updateTuyaVersion() {
    if (!isTuya()) {
        logDebug "not Tuya"
        return
    }
    def application = device.getDataValue("application") 
    if (application != null) {
        Integer ver
        try {
            ver = zigbee.convertHexToInt(application)
        }
        catch (e) {
            logWarn "exception caught while converting application version ${application} to tuyaVersion"
            return
        }
        def str = ((ver&0xC0)>>6).toString() + "." + ((ver&0x30)>>4).toString() + "." + (ver&0x0F).toString()
        if (device.getDataValue("tuyaVersion") != str) {
            device.updateDataValue("tuyaVersion", str)
            logInfo "tuyaVersion set to $str"
        }
    }
}

boolean isAqara() {
    def model = device.getDataValue("model") 
    def manufacturer = device.getDataValue("manufacturer") 
    if (model?.startsWith("lumi")) {
        return true
    }
    return false
}

def updateAqaraVersion() {
    if (!isAqara()) {
        logDebug "not Aqara"
        return
    }    
    def application = device.getDataValue("application") 
    if (application != null) {
        def str = "0.0.0_" + String.format("%04d", zigbee.convertHexToInt(application.substring(0, Math.min(application.length(), 2))));
        if (device.getDataValue("aqaraVersion") != str) {
            device.updateDataValue("aqaraVersion", str)
            logInfo "aqaraVersion set to $str"
        }
    }
    else {
        return null
    }
}

def test(par) {
    ArrayList<String> cmds = []
    log.warn "test... ${par}"
    
    parse(par)
    
   // sendZigbeeCommands(cmds)    
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////


// ~~~~~ start include (141) kkossev.xiaomiLib ~~~~~
library ( // library marker kkossev.xiaomiLib, line 1
    base: "driver", // library marker kkossev.xiaomiLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.xiaomiLib, line 3
    category: "zigbee", // library marker kkossev.xiaomiLib, line 4
    description: "Xiaomi Library", // library marker kkossev.xiaomiLib, line 5
    name: "xiaomiLib", // library marker kkossev.xiaomiLib, line 6
    namespace: "kkossev", // library marker kkossev.xiaomiLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy", // library marker kkossev.xiaomiLib, line 8
    version: "1.0.1", // library marker kkossev.xiaomiLib, line 9
    documentationLink: "" // library marker kkossev.xiaomiLib, line 10
) // library marker kkossev.xiaomiLib, line 11
/* // library marker kkossev.xiaomiLib, line 12
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 13
 * // library marker kkossev.xiaomiLib, line 14
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 15
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 16
 * // library marker kkossev.xiaomiLib, line 17
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 18
 * // library marker kkossev.xiaomiLib, line 19
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 20
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 21
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 22
 * // library marker kkossev.xiaomiLib, line 23
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 24
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 25
 * // library marker kkossev.xiaomiLib, line 26
 *                                   TODO:  // library marker kkossev.xiaomiLib, line 27
*/ // library marker kkossev.xiaomiLib, line 28


def xiaomiLibVersion()   {"1.0.1"} // library marker kkossev.xiaomiLib, line 31
def xiaomiLibStamp() {"2023/11/07 5:23 PM"} // library marker kkossev.xiaomiLib, line 32

// no metadata for this library! // library marker kkossev.xiaomiLib, line 34

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 36

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 38
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 39
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 40
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 41
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 42
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 43
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 44
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 45
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 46
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 47
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 48
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 49
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 50
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 51
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 52
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 53

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 55
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 56
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 57
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 58
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 59
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 60
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 61

// called from parseXiaomiCluster() in the main code ... // library marker kkossev.xiaomiLib, line 63
// // library marker kkossev.xiaomiLib, line 64
void parseXiaomiClusterLib(final Map descMap) { // library marker kkossev.xiaomiLib, line 65
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 66
        //log.trace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 67
    } // library marker kkossev.xiaomiLib, line 68
    if (DEVICE_TYPE in  ["Thermostat"]) { // library marker kkossev.xiaomiLib, line 69
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 70
        return // library marker kkossev.xiaomiLib, line 71
    } // library marker kkossev.xiaomiLib, line 72
    if (DEVICE_TYPE in  ["Bulb"]) { // library marker kkossev.xiaomiLib, line 73
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 74
        return // library marker kkossev.xiaomiLib, line 75
    } // library marker kkossev.xiaomiLib, line 76
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 77
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 78
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 79
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 80
            if (DEVICE_TYPE in  ["AqaraCube"]) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 81
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 82
            break // library marker kkossev.xiaomiLib, line 83
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 84
            log.info "unknown attribute - resetting?" // library marker kkossev.xiaomiLib, line 85
            break // library marker kkossev.xiaomiLib, line 86
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 87
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 88
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 89
            break // library marker kkossev.xiaomiLib, line 90
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 91
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 92
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 93
            break // library marker kkossev.xiaomiLib, line 94
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 95
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 96
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 97
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 98
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 99
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 100
            } // library marker kkossev.xiaomiLib, line 101
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 102
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 103
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 104
            } // library marker kkossev.xiaomiLib, line 105
            break // library marker kkossev.xiaomiLib, line 106
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 107
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 108
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 109
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 110
            break // library marker kkossev.xiaomiLib, line 111
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 112
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 113
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 114
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 115
            break // library marker kkossev.xiaomiLib, line 116
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 117
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 118
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 119
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 120
            break // library marker kkossev.xiaomiLib, line 121
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 122
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 123
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 124
            break // library marker kkossev.xiaomiLib, line 125
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 126
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 127
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 128
            break // library marker kkossev.xiaomiLib, line 129
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 130
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 131
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 132
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 133
                sendZigbeeCommands(refreshAqaraCube()) // library marker kkossev.xiaomiLib, line 134
            } // library marker kkossev.xiaomiLib, line 135
            break // library marker kkossev.xiaomiLib, line 136
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1  // library marker kkossev.xiaomiLib, line 137
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 138
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 139
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 140
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 141
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 142
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 143
                } // library marker kkossev.xiaomiLib, line 144
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 145
            } // library marker kkossev.xiaomiLib, line 146
            break // library marker kkossev.xiaomiLib, line 147
        default: // library marker kkossev.xiaomiLib, line 148
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 149
            break // library marker kkossev.xiaomiLib, line 150
    } // library marker kkossev.xiaomiLib, line 151
} // library marker kkossev.xiaomiLib, line 152

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 154
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 155
        switch (tag) { // library marker kkossev.xiaomiLib, line 156
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 157
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 158
                break // library marker kkossev.xiaomiLib, line 159
            case 0x03: // library marker kkossev.xiaomiLib, line 160
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 161
                break // library marker kkossev.xiaomiLib, line 162
            case 0x05: // library marker kkossev.xiaomiLib, line 163
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 164
                break // library marker kkossev.xiaomiLib, line 165
            case 0x06: // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 167
                break // library marker kkossev.xiaomiLib, line 168
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 169
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 170
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 171
                device.updateDataValue("aqaraVersion", swBuild) // library marker kkossev.xiaomiLib, line 172
                break // library marker kkossev.xiaomiLib, line 173
            case 0x0a: // library marker kkossev.xiaomiLib, line 174
                String nwk = intToHexStr(value as Integer,2) // library marker kkossev.xiaomiLib, line 175
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 176
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 177
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 178
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 179
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 180
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 181
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 182
                } // library marker kkossev.xiaomiLib, line 183
                break // library marker kkossev.xiaomiLib, line 184
            case 0x0b: // library marker kkossev.xiaomiLib, line 185
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 186
                break // library marker kkossev.xiaomiLib, line 187
            case 0x64: // library marker kkossev.xiaomiLib, line 188
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value/100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 189
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 190
                break // library marker kkossev.xiaomiLib, line 191
            case 0x65: // library marker kkossev.xiaomiLib, line 192
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 193
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value/100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 194
                break // library marker kkossev.xiaomiLib, line 195
            case 0x66: // library marker kkossev.xiaomiLib, line 196
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 197
                else if (isAqaraTVOC()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 198
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" }  // library marker kkossev.xiaomiLib, line 199
                break // library marker kkossev.xiaomiLib, line 200
            case 0x67: // library marker kkossev.xiaomiLib, line 201
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }     // library marker kkossev.xiaomiLib, line 202
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC:  // library marker kkossev.xiaomiLib, line 203
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 204
                break // library marker kkossev.xiaomiLib, line 205
            case 0x69: // library marker kkossev.xiaomiLib, line 206
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 207
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 208
                break // library marker kkossev.xiaomiLib, line 209
            case 0x6a: // library marker kkossev.xiaomiLib, line 210
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 212
                break // library marker kkossev.xiaomiLib, line 213
            case 0x6b: // library marker kkossev.xiaomiLib, line 214
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 215
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 216
                break // library marker kkossev.xiaomiLib, line 217
            case 0x95: // library marker kkossev.xiaomiLib, line 218
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 219
                break // library marker kkossev.xiaomiLib, line 220
            case 0x96: // library marker kkossev.xiaomiLib, line 221
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 222
                break // library marker kkossev.xiaomiLib, line 223
            case 0x97: // library marker kkossev.xiaomiLib, line 224
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 225
                break // library marker kkossev.xiaomiLib, line 226
            case 0x98: // library marker kkossev.xiaomiLib, line 227
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 228
                break // library marker kkossev.xiaomiLib, line 229
            case 0x9b: // library marker kkossev.xiaomiLib, line 230
                if (isAqaraCube()) {  // library marker kkossev.xiaomiLib, line 231
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})"  // library marker kkossev.xiaomiLib, line 232
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 233
                } // library marker kkossev.xiaomiLib, line 234
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 235
                break // library marker kkossev.xiaomiLib, line 236
            default: // library marker kkossev.xiaomiLib, line 237
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 238
        } // library marker kkossev.xiaomiLib, line 239
    } // library marker kkossev.xiaomiLib, line 240
} // library marker kkossev.xiaomiLib, line 241


/** // library marker kkossev.xiaomiLib, line 244
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 245
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 246
 */ // library marker kkossev.xiaomiLib, line 247
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 248
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 249
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 250
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 251
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 252
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 253
    } // library marker kkossev.xiaomiLib, line 254
    return bigInt // library marker kkossev.xiaomiLib, line 255
} // library marker kkossev.xiaomiLib, line 256

/** // library marker kkossev.xiaomiLib, line 258
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 259
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 260
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 261
 */ // library marker kkossev.xiaomiLib, line 262
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 263
    final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 264
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 265
    new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 266
        while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 267
            int tag = stream.read() // library marker kkossev.xiaomiLib, line 268
            int dataType = stream.read() // library marker kkossev.xiaomiLib, line 269
            Object value // library marker kkossev.xiaomiLib, line 270
            if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 271
                int length = stream.read() // library marker kkossev.xiaomiLib, line 272
                byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 273
                stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 274
                value = new String(byteArr) // library marker kkossev.xiaomiLib, line 275
            } else { // library marker kkossev.xiaomiLib, line 276
                int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 277
                value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 278
            } // library marker kkossev.xiaomiLib, line 279
            results[tag] = value // library marker kkossev.xiaomiLib, line 280
        } // library marker kkossev.xiaomiLib, line 281
    } // library marker kkossev.xiaomiLib, line 282
    return results // library marker kkossev.xiaomiLib, line 283
} // library marker kkossev.xiaomiLib, line 284


def refreshXiaomi() { // library marker kkossev.xiaomiLib, line 287
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 288
    if (cmds == []) { cmds = ["delay 299"] } // library marker kkossev.xiaomiLib, line 289
    return cmds // library marker kkossev.xiaomiLib, line 290
} // library marker kkossev.xiaomiLib, line 291

def configureXiaomi() { // library marker kkossev.xiaomiLib, line 293
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 294
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 295
    if (cmds == []) { cmds = ["delay 299"] }    // no ,  // library marker kkossev.xiaomiLib, line 296
    return cmds     // library marker kkossev.xiaomiLib, line 297
} // library marker kkossev.xiaomiLib, line 298

def initializeXiaomi() // library marker kkossev.xiaomiLib, line 300
{ // library marker kkossev.xiaomiLib, line 301
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 302
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 303
    if (cmds == []) { cmds = ["delay 299",] } // library marker kkossev.xiaomiLib, line 304
    return cmds         // library marker kkossev.xiaomiLib, line 305
} // library marker kkossev.xiaomiLib, line 306

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 308
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 309
} // library marker kkossev.xiaomiLib, line 310

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 312
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 313
} // library marker kkossev.xiaomiLib, line 314


// ~~~~~ end include (141) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (143) kkossev.rgbLib ~~~~~
library ( // library marker kkossev.rgbLib, line 1
    base: "driver", // library marker kkossev.rgbLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.rgbLib, line 3
    category: "zigbee", // library marker kkossev.rgbLib, line 4
    description: "RGB Library", // library marker kkossev.rgbLib, line 5
    name: "rgbLib", // library marker kkossev.rgbLib, line 6
    namespace: "kkossev", // library marker kkossev.rgbLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/rgbLib.groovy", // library marker kkossev.rgbLib, line 8
    version: "1.0.0", // library marker kkossev.rgbLib, line 9
    documentationLink: "" // library marker kkossev.rgbLib, line 10
) // library marker kkossev.rgbLib, line 11
/* // library marker kkossev.rgbLib, line 12
 *  Zigbee Button Dimmer -Library // library marker kkossev.rgbLib, line 13
 * // library marker kkossev.rgbLib, line 14
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.rgbLib, line 15
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.rgbLib, line 16
 * // library marker kkossev.rgbLib, line 17
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.rgbLib, line 18
 * // library marker kkossev.rgbLib, line 19
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.rgbLib, line 20
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.rgbLib, line 21
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.rgbLib, line 22
 * // library marker kkossev.rgbLib, line 23
 *  Credits: Ivar Holand for 'IKEA Tradfri RGBW Light HE v2' driver code // library marker kkossev.rgbLib, line 24
 * // library marker kkossev.rgbLib, line 25
 * ver. 1.0.0  2023-11-06 kkossev  - added rgbLib; musicMode; // library marker kkossev.rgbLib, line 26
 * // library marker kkossev.rgbLib, line 27
 *                                   TODO:  // library marker kkossev.rgbLib, line 28
*/ // library marker kkossev.rgbLib, line 29

def thermostatLibVersion()   {"1.0.0"} // library marker kkossev.rgbLib, line 31
def thermostatLibStamp() {"2023/11/07 5:23 PM"} // library marker kkossev.rgbLib, line 32

import hubitat.helper.ColorUtils // library marker kkossev.rgbLib, line 34

metadata { // library marker kkossev.rgbLib, line 36
    capability "Actuator" // library marker kkossev.rgbLib, line 37
    capability "Color Control" // library marker kkossev.rgbLib, line 38
    capability "ColorMode" // library marker kkossev.rgbLib, line 39
    capability "Color Temperature" // library marker kkossev.rgbLib, line 40
    capability "Refresh" // library marker kkossev.rgbLib, line 41
    capability "Switch" // library marker kkossev.rgbLib, line 42
    capability "Switch Level" // library marker kkossev.rgbLib, line 43
    capability "Light" // library marker kkossev.rgbLib, line 44
    capability "ChangeLevel" // library marker kkossev.rgbLib, line 45

    attribute "deviceTemperature", "number" // library marker kkossev.rgbLib, line 47
    attribute "musicMode", "enum", MusicModeOpts.options.values() as List<String> // library marker kkossev.rgbLib, line 48

    command "musicMode", [[name:"Select Music Mode", type: "ENUM",   constraints: ["--- select ---"]+MusicModeOpts.options.values() as List<String>]] // library marker kkossev.rgbLib, line 50


    if (_DEBUG) { command "testT", [[name: "testT", type: "STRING", description: "testT", defaultValue : ""]]  } // library marker kkossev.rgbLib, line 53

    fingerprint profileId:"0104", endpointId:"01", inClusters:"0005,0004,0003,0000,0300,0008,0006,FCC0", outClusters:"0019,000A", model:"lumi.light.acn132", manufacturer:"Aqara" // library marker kkossev.rgbLib, line 55
    // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/7200 // library marker kkossev.rgbLib, line 56
    //https://github.com/dresden-elektronik/deconz-rest-plugin/blob/50555f9350dc1872f266ebe9a5b3620b76e99af6/devices/xiaomi/lumi_light_acn132.json#L4 // library marker kkossev.rgbLib, line 57
    preferences { // library marker kkossev.rgbLib, line 58
    } // library marker kkossev.rgbLib, line 59
} // library marker kkossev.rgbLib, line 60



private getMAX_WHITE_SATURATION() { 70 } // library marker kkossev.rgbLib, line 64
private getWHITE_HUE() { 8 } // library marker kkossev.rgbLib, line 65
private getMIN_COLOR_TEMP() { 2700 } // library marker kkossev.rgbLib, line 66
private getMAX_COLOR_TEMP() { 6500 } // library marker kkossev.rgbLib, line 67

@Field static final Map MusicModeOpts = [            // preset // library marker kkossev.rgbLib, line 69
    defaultValue: 0, // library marker kkossev.rgbLib, line 70
    options     : [0: 'off', 1: 'on'] // library marker kkossev.rgbLib, line 71
] // library marker kkossev.rgbLib, line 72

/* // library marker kkossev.rgbLib, line 74
 * ----------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 75
 * Level Control Cluster            0x0008 // library marker kkossev.rgbLib, line 76
 * ----------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 77
*/ // library marker kkossev.rgbLib, line 78
void parseLevelControlClusterBulb(final Map descMap) { // library marker kkossev.rgbLib, line 79
    logDebug "parseLevelControlClusterBulb: 0x${descMap.value}" // library marker kkossev.rgbLib, line 80
    if (descMap.attrId == "0000") { // library marker kkossev.rgbLib, line 81
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.rgbLib, line 82
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 83
        // Aqara LED Strip T1 sends the level in the range 0..255 // library marker kkossev.rgbLib, line 84
        def scaledValue = ((rawValue as double) / 2.55F + 0.5) as int // library marker kkossev.rgbLib, line 85
        sendLevelControlEvent(scaledValue) // library marker kkossev.rgbLib, line 86
    } // library marker kkossev.rgbLib, line 87
    else { // library marker kkossev.rgbLib, line 88
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.rgbLib, line 89
    } // library marker kkossev.rgbLib, line 90
} // library marker kkossev.rgbLib, line 91

/* // library marker kkossev.rgbLib, line 93
 * ----------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 94
 * ColorControl Cluster            0x0300 // library marker kkossev.rgbLib, line 95
 * ----------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 96
*/ // library marker kkossev.rgbLib, line 97
void parseColorControlClusterBulb(final Map descMap, description) { // library marker kkossev.rgbLib, line 98
    if (descMap.attrId != null) { // library marker kkossev.rgbLib, line 99
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseColorControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.rgbLib, line 100
        processColorControlCluster(descMap, description) // library marker kkossev.rgbLib, line 101
    } // library marker kkossev.rgbLib, line 102
    else { // library marker kkossev.rgbLib, line 103
        logWarn "unprocessed ColorControl attribute ${descMap.attrId}" // library marker kkossev.rgbLib, line 104
    } // library marker kkossev.rgbLib, line 105
} // library marker kkossev.rgbLib, line 106


void processColorControlCluster(final Map descMap, description) { // library marker kkossev.rgbLib, line 109
    logDebug "processColorControlCluster : ${descMap}" // library marker kkossev.rgbLib, line 110
    def map = [:] // library marker kkossev.rgbLib, line 111
    def parsed // library marker kkossev.rgbLib, line 112

    if (description instanceof String)  { // library marker kkossev.rgbLib, line 114
        map = stringToMap(description) // library marker kkossev.rgbLib, line 115
    } // library marker kkossev.rgbLib, line 116

    logDebug "Map - $map" // library marker kkossev.rgbLib, line 118
    def raw = map["read attr - raw"] // library marker kkossev.rgbLib, line 119

    if(raw) { // library marker kkossev.rgbLib, line 121
        def clusterId = map.cluster // library marker kkossev.rgbLib, line 122
        def attrList = raw.substring(12) // library marker kkossev.rgbLib, line 123

        parsed = parseAttributeList(clusterId, attrList) // library marker kkossev.rgbLib, line 125

        if(state.colorChanged || (state.colorXReported && state.colorYReported)) { // library marker kkossev.rgbLib, line 127
            state.colorChanged = false; // library marker kkossev.rgbLib, line 128
            state.colorXReported = false; // library marker kkossev.rgbLib, line 129
            state.colorYReported = false; // library marker kkossev.rgbLib, line 130
            logTrace "Color Change: xy ($state.colorX, $state.colorY)" // library marker kkossev.rgbLib, line 131
            def rgb = colorXy2Rgb(state.colorX, state.colorY) // library marker kkossev.rgbLib, line 132
            logTrace "Color Change: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.rgbLib, line 133
            updateColor(rgb)        // sends a bunch of events! // library marker kkossev.rgbLib, line 134
        } // library marker kkossev.rgbLib, line 135
    } // library marker kkossev.rgbLib, line 136
    else { // library marker kkossev.rgbLib, line 137
        logDebug "Sending color event based on pending values" // library marker kkossev.rgbLib, line 138
        if (state.pendingColorUpdate) { // library marker kkossev.rgbLib, line 139
            parsed = true // library marker kkossev.rgbLib, line 140
            def rgb = colorXy2Rgb(state.colorX, state.colorY) // library marker kkossev.rgbLib, line 141
            updateColor(rgb)            // sends a bunch of events! // library marker kkossev.rgbLib, line 142
            state.pendingColorUpdate = false // library marker kkossev.rgbLib, line 143
        } // library marker kkossev.rgbLib, line 144
    } // library marker kkossev.rgbLib, line 145
} // library marker kkossev.rgbLib, line 146

def parseHex4le(hex) { // library marker kkossev.rgbLib, line 148
    Integer.parseInt(hex.substring(2, 4) + hex.substring(0, 2), 16) // library marker kkossev.rgbLib, line 149
} // library marker kkossev.rgbLib, line 150

def parseColorAttribute(id, value) { // library marker kkossev.rgbLib, line 152
    def parsed = false // library marker kkossev.rgbLib, line 153

    if(id == 0x03) { // library marker kkossev.rgbLib, line 155
        // currentColorX // library marker kkossev.rgbLib, line 156
        value = parseHex4le(value) // library marker kkossev.rgbLib, line 157
        logTrace "Parsed ColorX: $value" // library marker kkossev.rgbLib, line 158
        value /= 65536 // library marker kkossev.rgbLib, line 159
        parsed = true // library marker kkossev.rgbLib, line 160
        state.colorXReported = true; // library marker kkossev.rgbLib, line 161
        state.colorChanged |= value != colorX // library marker kkossev.rgbLib, line 162
        state.colorX = value // library marker kkossev.rgbLib, line 163
    } // library marker kkossev.rgbLib, line 164
    else if(id == 0x04) { // library marker kkossev.rgbLib, line 165
        // currentColorY // library marker kkossev.rgbLib, line 166
        value = parseHex4le(value) // library marker kkossev.rgbLib, line 167
        logTrace "Parsed ColorY: $value" // library marker kkossev.rgbLib, line 168
        value /= 65536 // library marker kkossev.rgbLib, line 169
        parsed = true // library marker kkossev.rgbLib, line 170
        state.colorYReported = true; // library marker kkossev.rgbLib, line 171
        state.colorChanged |= value != colorY // library marker kkossev.rgbLib, line 172
        state.colorY = value // library marker kkossev.rgbLib, line 173
    } // library marker kkossev.rgbLib, line 174
    else {  // TODO: parse atttribute 7 (color temperature in mireds) // library marker kkossev.rgbLib, line 175
        logDebug "Not parsing Color cluster attribute $id: $value" // library marker kkossev.rgbLib, line 176
    } // library marker kkossev.rgbLib, line 177

    parsed // library marker kkossev.rgbLib, line 179
} // library marker kkossev.rgbLib, line 180




def parseAttributeList(cluster, list) { // library marker kkossev.rgbLib, line 185
    logTrace "Cluster: $cluster, AttrList: $list" // library marker kkossev.rgbLib, line 186
    def parsed = true // library marker kkossev.rgbLib, line 187

    while(list.length()) { // library marker kkossev.rgbLib, line 189
        def attrId = parseHex4le(list.substring(0, 4)) // library marker kkossev.rgbLib, line 190
        def attrType = Integer.parseInt(list.substring(4, 6), 16) // library marker kkossev.rgbLib, line 191
        def attrShift = 0 // library marker kkossev.rgbLib, line 192

        if(!attrType) { // library marker kkossev.rgbLib, line 194
            attrType = Integer.parseInt(list.substring(6, 8), 16) // library marker kkossev.rgbLib, line 195
            attrShift = 1 // library marker kkossev.rgbLib, line 196
        } // library marker kkossev.rgbLib, line 197

        def attrLen = DataType.getLength(attrType) // library marker kkossev.rgbLib, line 199
        def attrValue = list.substring(6 + 2*attrShift, 6 + 2*(attrShift+attrLen)) // library marker kkossev.rgbLib, line 200

        logTrace "Attr - Id: $attrId($attrLen), Type: $attrType, Value: $attrValue" // library marker kkossev.rgbLib, line 202

        if(cluster == 300) { // library marker kkossev.rgbLib, line 204
            parsed &= parseColorAttribute(attrId, attrValue) // library marker kkossev.rgbLib, line 205
        } // library marker kkossev.rgbLib, line 206
        else { // library marker kkossev.rgbLib, line 207
            log.info "Not parsing cluster $cluster attribute: $list" // library marker kkossev.rgbLib, line 208
            parsed = false; // library marker kkossev.rgbLib, line 209
        } // library marker kkossev.rgbLib, line 210

        list = list.substring(6 + 2*(attrShift+attrLen)) // library marker kkossev.rgbLib, line 212
    } // library marker kkossev.rgbLib, line 213

    parsed // library marker kkossev.rgbLib, line 215
} // library marker kkossev.rgbLib, line 216



/* // library marker kkossev.rgbLib, line 220
def sendColorControlEvent( rawValue ) { // library marker kkossev.rgbLib, line 221
    logWarn "TODO: sendColorControlEvent ($rawValue)" // library marker kkossev.rgbLib, line 222
    return // library marker kkossev.rgbLib, line 223

    def value = rawValue as int // library marker kkossev.rgbLib, line 225
    if (value <0) value = 0 // library marker kkossev.rgbLib, line 226
    if (value >100) value = 100 // library marker kkossev.rgbLib, line 227
    def map = [:]  // library marker kkossev.rgbLib, line 228

    def isDigital = state.states["isDigital"] // library marker kkossev.rgbLib, line 230
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.rgbLib, line 231

    map.name = "level" // library marker kkossev.rgbLib, line 233
    map.value = value // library marker kkossev.rgbLib, line 234
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.rgbLib, line 235
    if (isRefresh == true) { // library marker kkossev.rgbLib, line 236
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.rgbLib, line 237
        map.isStateChange = true // library marker kkossev.rgbLib, line 238
    } // library marker kkossev.rgbLib, line 239
    else { // library marker kkossev.rgbLib, line 240
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.rgbLib, line 241
    } // library marker kkossev.rgbLib, line 242
    logInfo "${map.descriptionText}" // library marker kkossev.rgbLib, line 243
    sendEvent(map) // library marker kkossev.rgbLib, line 244
    clearIsDigital() // library marker kkossev.rgbLib, line 245
} // library marker kkossev.rgbLib, line 246
*/ // library marker kkossev.rgbLib, line 247

// called from parseXiaomiClusterLib in xiaomiLib.groovy (xiaomi cluster 0xFCC0 ) // library marker kkossev.rgbLib, line 249
// // library marker kkossev.rgbLib, line 250
void parseXiaomiClusterRgbLib(final Map descMap) { // library marker kkossev.rgbLib, line 251
    //logWarn "parseXiaomiClusterRgbLib: received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.rgbLib, line 252
    final Integer raw // library marker kkossev.rgbLib, line 253
    final String  value // library marker kkossev.rgbLib, line 254
    switch (descMap.attrInt as Integer) { // library marker kkossev.rgbLib, line 255
        case 0x00EE:    // attr/swversion" // library marker kkossev.rgbLib, line 256
            raw = hexStrToUnsignedInt(descMap.value)        // val = '0.0.0_' + ('0000' + ((Attr.val & 0xFF00) >> 8).toString() + (Attr.val & 0xFF).toString()).slice(-4)" // library marker kkossev.rgbLib, line 257
            logInfo "Aqara Version is ${raw}" // library marker kkossev.rgbLib, line 258
            break // library marker kkossev.rgbLib, line 259
        case 0x00F7 :   // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes // library marker kkossev.rgbLib, line 260
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.rgbLib, line 261
            parseXiaomiClusterRgbTags(tags) // library marker kkossev.rgbLib, line 262
            break // library marker kkossev.rgbLib, line 263
        case 0x0515:    // config/bri/min                   // r/w "dt": "0x20"  // library marker kkossev.rgbLib, line 264
            raw = hexStrToUnsignedInt(descMap.value)        // .val = Math.round(Attr.val * 2.54) // library marker kkossev.rgbLib, line 265
            logInfo "Aqara min brightness is ${raw}" // library marker kkossev.rgbLib, line 266
            break // library marker kkossev.rgbLib, line 267
        case 0x0516:    // config/bri/max                   // r/w "dt": "0x20"  // library marker kkossev.rgbLib, line 268
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 269
            logInfo "Aqara max brightness is ${raw}" // library marker kkossev.rgbLib, line 270
            break // library marker kkossev.rgbLib, line 271
        case 0x0517:    // config/on/startup               // r/w "dt": "0x20"  // library marker kkossev.rgbLib, line 272
            raw = hexStrToUnsignedInt(descMap.value)       // val = [1, 255, 0][Attr.val]       // val === 1 ? 0 : Item.val === 0 ? 2 : 1"  // library marker kkossev.rgbLib, line 273
            logInfo "Aqara on startup is ${raw}" // library marker kkossev.rgbLib, line 274
            break // library marker kkossev.rgbLib, line 275
        case 0x051B:    // config/color/gradient/pixel_count                  // r/w "dt": "0x20" , Math.max(5, Math.min(Item.val, 50)) // library marker kkossev.rgbLib, line 276
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 277
            logInfo "Aqara pixel count is ${raw}" // library marker kkossev.rgbLib, line 278
            break // library marker kkossev.rgbLib, line 279
        case 0x051C:    // state/music_sync                 // r/w "dt": "0x20" , val = Attr.val === 1      // Item.val ? 1 : 0  // library marker kkossev.rgbLib, line 280
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 281
            value = MusicModeOpts.options[raw as int] // library marker kkossev.rgbLib, line 282
            aqaraEvent("musicMode", value, raw) // library marker kkossev.rgbLib, line 283
            break // library marker kkossev.rgbLib, line 284
        case 0x0509:    // state/gradient                   // r/w "dt": "0x20" , val = Attr.val === 1      // Item.val ? 1 : 0  // library marker kkossev.rgbLib, line 285
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 286
            logInfo "Aqara gradient is ${raw}" // library marker kkossev.rgbLib, line 287
            break // library marker kkossev.rgbLib, line 288
        case 0x051F:    // state/gradient/flow              // r/w "dt": "0x20" , val = Attr.val === 1      // Item.val ? 1 : 0  // library marker kkossev.rgbLib, line 289
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 290
            logInfo "Aqara gradient flow is ${raw}" // library marker kkossev.rgbLib, line 291
            break // library marker kkossev.rgbLib, line 292
        case 0x051D:    // state/gradient/flow/speed        // r/w "dt": "0x20" , val = Math.max(1, Math.min(Item.val, 10)) // library marker kkossev.rgbLib, line 293
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 294
            logInfo "Aqara gradient flow speed is ${raw}" // library marker kkossev.rgbLib, line 295
            break // library marker kkossev.rgbLib, line 296
        default: // library marker kkossev.rgbLib, line 297
            logWarn "parseXiaomiClusterRgbLib: received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.rgbLib, line 298
            break // library marker kkossev.rgbLib, line 299
    } // library marker kkossev.rgbLib, line 300
} // library marker kkossev.rgbLib, line 301

void aqaraEvent(eventName, value, raw) { // library marker kkossev.rgbLib, line 303
    sendEvent(name: eventName, value: value, type: "physical") // library marker kkossev.rgbLib, line 304
    logInfo "${eventName} is ${value} (raw ${raw})" // library marker kkossev.rgbLib, line 305
} // library marker kkossev.rgbLib, line 306

// // library marker kkossev.rgbLib, line 308
// called from parseXiaomiClusterRgbLib  // library marker kkossev.rgbLib, line 309
// // library marker kkossev.rgbLib, line 310
void parseXiaomiClusterRgbTags(final Map<Integer, Object> tags) {       // TODO: check https://github.com/sprut/Hub/issues/2420  // library marker kkossev.rgbLib, line 311
    tags.each { final Integer tag, final Object value -> // library marker kkossev.rgbLib, line 312
        switch (tag) { // library marker kkossev.rgbLib, line 313
            case 0x01:    // battery voltage // library marker kkossev.rgbLib, line 314
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})" // library marker kkossev.rgbLib, line 315
                break // library marker kkossev.rgbLib, line 316
            case 0x03: // library marker kkossev.rgbLib, line 317
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device internal chip temperature is ${value}&deg;" // library marker kkossev.rgbLib, line 318
                sendEvent(name: "deviceTemperature", value: value, unit: "C") // library marker kkossev.rgbLib, line 319
                break // library marker kkossev.rgbLib, line 320
            case 0x05: // library marker kkossev.rgbLib, line 321
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.rgbLib, line 322
                break // library marker kkossev.rgbLib, line 323
            case 0x06: // library marker kkossev.rgbLib, line 324
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.rgbLib, line 325
                break // library marker kkossev.rgbLib, line 326
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.rgbLib, line 327
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.rgbLib, line 328
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.rgbLib, line 329
                device.updateDataValue("aqaraVersion", swBuild) // library marker kkossev.rgbLib, line 330
                break // library marker kkossev.rgbLib, line 331
            case 0x0a: // library marker kkossev.rgbLib, line 332
                String nwk = intToHexStr(value as Integer,2) // library marker kkossev.rgbLib, line 333
                if (state.health == null) { state.health = [:] } // library marker kkossev.rgbLib, line 334
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.rgbLib, line 335
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.rgbLib, line 336
                if (oldNWK != nwk ) { // library marker kkossev.rgbLib, line 337
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.rgbLib, line 338
                    state.health['parentNWK']  = nwk // library marker kkossev.rgbLib, line 339
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.rgbLib, line 340
                } // library marker kkossev.rgbLib, line 341
                break // library marker kkossev.rgbLib, line 342
            default: // library marker kkossev.rgbLib, line 343
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 344
        } // library marker kkossev.rgbLib, line 345
    } // library marker kkossev.rgbLib, line 346
} // library marker kkossev.rgbLib, line 347


// all the code below is borrowed from Ivar Holand's "IKEA Tradfri RGBW Light HE v2" driver // library marker kkossev.rgbLib, line 350
// ----------------------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 351


def updateColor(rgb) { // library marker kkossev.rgbLib, line 354
    logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.rgbLib, line 355
    def hsv = colorRgb2Hsv(rgb.red, rgb.green, rgb.blue) // library marker kkossev.rgbLib, line 356
    hsv.hue = Math.round(hsv.hue * 100).intValue() // library marker kkossev.rgbLib, line 357
    hsv.saturation = Math.round(hsv.saturation * 100).intValue() // library marker kkossev.rgbLib, line 358
    hsv.level = Math.round(hsv.level * 100).intValue() // library marker kkossev.rgbLib, line 359
    logTrace "updateColor: HSV ($hsv.hue, $hsv.saturation, $hsv.level)" // library marker kkossev.rgbLib, line 360

    rgb.red = Math.round(rgb.red * 255).intValue() // library marker kkossev.rgbLib, line 362
    rgb.green = Math.round(rgb.green * 255).intValue() // library marker kkossev.rgbLib, line 363
    rgb.blue = Math.round(rgb.blue * 255).intValue() // library marker kkossev.rgbLib, line 364
    logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.rgbLib, line 365

    def color = ColorUtils.rgbToHEX([rgb.red, rgb.green, rgb.blue]) // library marker kkossev.rgbLib, line 367
    logTrace "updateColor: $color" // library marker kkossev.rgbLib, line 368

    sendColorEvent([name: "color", value: color, data: [ hue: hsv.hue, saturation: hsv.saturation, red: rgb.red, green: rgb.green, blue: rgb.blue, hex: color], displayed: false]) // library marker kkossev.rgbLib, line 370
    sendHueEvent([name: "hue", value: hsv.hue, displayed: false]) // library marker kkossev.rgbLib, line 371
    sendSaturationEvent([name: "saturation", value: hsv.saturation, displayed: false]) // library marker kkossev.rgbLib, line 372
    if (hsv.hue == WHITE_HUE) { // library marker kkossev.rgbLib, line 373
        def percent = (1 - ((hsv.saturation / 100) * (100 / MAX_WHITE_SATURATION))) // library marker kkossev.rgbLib, line 374
        def amount = (MAX_COLOR_TEMP - MIN_COLOR_TEMP) * percent // library marker kkossev.rgbLib, line 375
        def val = Math.round(MIN_COLOR_TEMP + amount) // library marker kkossev.rgbLib, line 376
        sendColorTemperatureEvent([name: "colorTemperature", value: val]) // library marker kkossev.rgbLib, line 377
        sendColorModeEvent([name: "colorMode", value: "CT"]) // library marker kkossev.rgbLib, line 378
        sendColorNameEvent([setGenericTempName(val)]) // library marker kkossev.rgbLib, line 379
    }  // library marker kkossev.rgbLib, line 380
    else { // library marker kkossev.rgbLib, line 381
        sendColorModeEvent([name: "colorMode", value: "RGB"]) // library marker kkossev.rgbLib, line 382
        sendColorNameEvent(setGenericName(hsv.hue)) // library marker kkossev.rgbLib, line 383
    } // library marker kkossev.rgbLib, line 384
} // library marker kkossev.rgbLib, line 385

void sendColorEvent(map) { // library marker kkossev.rgbLib, line 387
    if (map.value == device.currentValue(map.name)) { // library marker kkossev.rgbLib, line 388
        logDebug "sendColorEvent: ${map.name} is already ${map.value}" // library marker kkossev.rgbLib, line 389
        return // library marker kkossev.rgbLib, line 390
    } // library marker kkossev.rgbLib, line 391
    // get the time of the last event named "color" and compare it to the current time // library marker kkossev.rgbLib, line 392
 //   def lastColorEvent = device.currentState("color",true).date.time // library marker kkossev.rgbLib, line 393
 //   if ((now() - lastColorEvent) < 1000) { // library marker kkossev.rgbLib, line 394
       // logDebug "sendColorEvent: delaying ${map.name} event because the last color event was less than 1 second ago ${(now() - lastColorEvent)}" // library marker kkossev.rgbLib, line 395
        runInMillis(500, "sendDelayedColorEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 396
        return // library marker kkossev.rgbLib, line 397
//    } // library marker kkossev.rgbLib, line 398
    //unschedule("sendDelayedColorEvent") // cancel any pending delayed events // library marker kkossev.rgbLib, line 399
    //logDebug "sendColorEvent: lastColorEvent = ${lastColorEvent}, now = ${now()}, diff = ${(now() - lastColorEvent)}" // library marker kkossev.rgbLib, line 400
    //sendEvent(map) // library marker kkossev.rgbLib, line 401
} // library marker kkossev.rgbLib, line 402
private void sendDelayedColorEvent(Map map) { // library marker kkossev.rgbLib, line 403
    sendEvent(map) // library marker kkossev.rgbLib, line 404
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 405
} // library marker kkossev.rgbLib, line 406

void sendHueEvent(map) { // library marker kkossev.rgbLib, line 408
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 409
    runInMillis(500, "sendDelayedHueEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 410
} // library marker kkossev.rgbLib, line 411
private void sendDelayedHueEvent(Map map) { // library marker kkossev.rgbLib, line 412
    sendEvent(map) // library marker kkossev.rgbLib, line 413
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 414
} // library marker kkossev.rgbLib, line 415

void sendSaturationEvent(map) { // library marker kkossev.rgbLib, line 417
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 418
    runInMillis(500, "sendDelayedSaturationEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 419
} // library marker kkossev.rgbLib, line 420
private void sendDelayedSaturationEvent(Map map) { // library marker kkossev.rgbLib, line 421
    sendEvent(map) // library marker kkossev.rgbLib, line 422
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 423
} // library marker kkossev.rgbLib, line 424

void sendColorModeEvent(map) { // library marker kkossev.rgbLib, line 426
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 427
    runInMillis(500, "sendDelayedColorModeEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 428
} // library marker kkossev.rgbLib, line 429
private void sendDelayedColorModeEvent(Map map) { // library marker kkossev.rgbLib, line 430
    sendEvent(map) // library marker kkossev.rgbLib, line 431
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 432
} // library marker kkossev.rgbLib, line 433

void sendColorNameEvent(map) { // library marker kkossev.rgbLib, line 435
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 436
    runInMillis(500, "sendDelayedColorNameEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 437
} // library marker kkossev.rgbLib, line 438
private void sendDelayedColorNameEvent(Map map) { // library marker kkossev.rgbLib, line 439
    sendEvent(map) // library marker kkossev.rgbLib, line 440
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 441
} // library marker kkossev.rgbLib, line 442

void sendColorTemperatureEvent(map) { // library marker kkossev.rgbLib, line 444
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 445
    runInMillis(500, "sendDelayedColorTemperatureEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 446
} // library marker kkossev.rgbLib, line 447
private void sendDelayedColorTemperatureEvent(Map map) { // library marker kkossev.rgbLib, line 448
    sendEvent(map) // library marker kkossev.rgbLib, line 449
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 450
} // library marker kkossev.rgbLib, line 451


def sendZigbeeCommandsDelayed() { // library marker kkossev.rgbLib, line 454
    List cmds = state.cmds // library marker kkossev.rgbLib, line 455
    if (cmds != null) { // library marker kkossev.rgbLib, line 456
        state.cmds = [] // library marker kkossev.rgbLib, line 457
        sendZigbeeCommands(cmds) // library marker kkossev.rgbLib, line 458
    } // library marker kkossev.rgbLib, line 459
} // library marker kkossev.rgbLib, line 460

def setLevelBulb(value, rate=null) { // library marker kkossev.rgbLib, line 462
    logDebug "setLevelBulb: $value, $rate" // library marker kkossev.rgbLib, line 463

    state.pendingLevelChange = value // library marker kkossev.rgbLib, line 465

    if (rate == null) { // library marker kkossev.rgbLib, line 467
        state.cmds += zigbee.setLevel(value) // library marker kkossev.rgbLib, line 468
    } else { // library marker kkossev.rgbLib, line 469
        state.cmds += zigbee.setLevel(value, rate) // library marker kkossev.rgbLib, line 470
    } // library marker kkossev.rgbLib, line 471

    unschedule(sendZigbeeCommandsDelayed) // library marker kkossev.rgbLib, line 473
    runInMillis(100, sendZigbeeCommandsDelayed) // library marker kkossev.rgbLib, line 474
} // library marker kkossev.rgbLib, line 475


def setColorTemperature(value, level=null, rate=null) { // library marker kkossev.rgbLib, line 478
    logDebug "Set color temperature $value" // library marker kkossev.rgbLib, line 479

    def sat = MAX_WHITE_SATURATION - (((value - MIN_COLOR_TEMP) / (MAX_COLOR_TEMP - MIN_COLOR_TEMP)) * MAX_WHITE_SATURATION) // library marker kkossev.rgbLib, line 481
    setColor([ // library marker kkossev.rgbLib, line 482
            hue: WHITE_HUE, // library marker kkossev.rgbLib, line 483
            saturation: sat, // library marker kkossev.rgbLib, line 484
            level: level, // library marker kkossev.rgbLib, line 485
            rate: rate // library marker kkossev.rgbLib, line 486
    ]) // library marker kkossev.rgbLib, line 487
} // library marker kkossev.rgbLib, line 488

def setColor(value) { // library marker kkossev.rgbLib, line 490
    logDebug "setColor($value)" // library marker kkossev.rgbLib, line 491
    def rgb = colorHsv2Rgb(value.hue / 100, value.saturation / 100) // library marker kkossev.rgbLib, line 492

    logTrace "setColor: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.rgbLib, line 494
    def xy = colorRgb2Xy(rgb.red, rgb.green, rgb.blue); // library marker kkossev.rgbLib, line 495
    logTrace "setColor: xy ($xy.x, $xy.y)" // library marker kkossev.rgbLib, line 496

    def intX = Math.round(xy.x*65536).intValue() // 0..65279 // library marker kkossev.rgbLib, line 498
    def intY = Math.round(xy.y*65536).intValue() // 0..65279 // library marker kkossev.rgbLib, line 499

    logTrace "setColor: xy ($intX, $intY)" // library marker kkossev.rgbLib, line 501

    state.colorX = xy.x // library marker kkossev.rgbLib, line 503
    state.colorY = xy.y // library marker kkossev.rgbLib, line 504

    def strX = DataType.pack(intX, DataType.UINT16, true); // library marker kkossev.rgbLib, line 506
    def strY = DataType.pack(intY, DataType.UINT16, true); // library marker kkossev.rgbLib, line 507

    List cmds = [] // library marker kkossev.rgbLib, line 509

    def level = value.level // library marker kkossev.rgbLib, line 511
    def rate = value.rate // library marker kkossev.rgbLib, line 512

    if (level != null && rate != null) { // library marker kkossev.rgbLib, line 514
        state.pendingLevelChange = level // library marker kkossev.rgbLib, line 515
        cmds += zigbee.setLevel(level, rate) // library marker kkossev.rgbLib, line 516
    } else if (level != null) { // library marker kkossev.rgbLib, line 517
        state.pendingLevelChange = level // library marker kkossev.rgbLib, line 518
        cmds += zigbee.setLevel(level) // library marker kkossev.rgbLib, line 519
    } // library marker kkossev.rgbLib, line 520

    state.pendingColorUpdate = true // library marker kkossev.rgbLib, line 522

    cmds += zigbee.command(0x0300, 0x07, strX, strY, "0a00") // library marker kkossev.rgbLib, line 524
    if (state.cmds == null) { state.cmds = [] }    // library marker kkossev.rgbLib, line 525
    state.cmds += cmds // library marker kkossev.rgbLib, line 526

    logTrace "zigbee command: $cmds" // library marker kkossev.rgbLib, line 528

    unschedule(sendZigbeeCommandsDelayed) // library marker kkossev.rgbLib, line 530
    runInMillis(100, sendZigbeeCommandsDelayed) // library marker kkossev.rgbLib, line 531
} // library marker kkossev.rgbLib, line 532


def setHue(hue) { // library marker kkossev.rgbLib, line 535
    logDebug "setHue: $hue" // library marker kkossev.rgbLib, line 536
    setColor([ hue: hue, saturation: device.currentValue("saturation") ]) // library marker kkossev.rgbLib, line 537
} // library marker kkossev.rgbLib, line 538

def setSaturation(saturation) { // library marker kkossev.rgbLib, line 540
    logDebug "setSaturation: $saturation" // library marker kkossev.rgbLib, line 541
    setColor([ hue: device.currentValue("hue"), saturation: saturation ]) // library marker kkossev.rgbLib, line 542
} // library marker kkossev.rgbLib, line 543

def setGenericTempName(temp){ // library marker kkossev.rgbLib, line 545
    if (!temp) return // library marker kkossev.rgbLib, line 546
    String genericName // library marker kkossev.rgbLib, line 547
    int value = temp.toInteger() // library marker kkossev.rgbLib, line 548
    if (value <= 2000) genericName = "Sodium" // library marker kkossev.rgbLib, line 549
    else if (value <= 2100) genericName = "Starlight" // library marker kkossev.rgbLib, line 550
    else if (value < 2400) genericName = "Sunrise" // library marker kkossev.rgbLib, line 551
    else if (value < 2800) genericName = "Incandescent" // library marker kkossev.rgbLib, line 552
    else if (value < 3300) genericName = "Soft White" // library marker kkossev.rgbLib, line 553
    else if (value < 3500) genericName = "Warm White" // library marker kkossev.rgbLib, line 554
    else if (value < 4150) genericName = "Moonlight" // library marker kkossev.rgbLib, line 555
    else if (value <= 5000) genericName = "Horizon" // library marker kkossev.rgbLib, line 556
    else if (value < 5500) genericName = "Daylight" // library marker kkossev.rgbLib, line 557
    else if (value < 6000) genericName = "Electronic" // library marker kkossev.rgbLib, line 558
    else if (value <= 6500) genericName = "Skylight" // library marker kkossev.rgbLib, line 559
    else if (value < 20000) genericName = "Polar" // library marker kkossev.rgbLib, line 560
    String descriptionText = "${device.getDisplayName()} color is ${genericName}" // library marker kkossev.rgbLib, line 561
    return createEvent(name: "colorName", value: genericName ,descriptionText: descriptionText) // library marker kkossev.rgbLib, line 562
} // library marker kkossev.rgbLib, line 563

def setGenericName(hue){ // library marker kkossev.rgbLib, line 565
    String colorName // library marker kkossev.rgbLib, line 566
    hue = hue.toInteger() // library marker kkossev.rgbLib, line 567
    hue = (hue * 3.6) // library marker kkossev.rgbLib, line 568
    switch (hue.toInteger()){ // library marker kkossev.rgbLib, line 569
        case 0..15: colorName = "Red" // library marker kkossev.rgbLib, line 570
            break // library marker kkossev.rgbLib, line 571
        case 16..45: colorName = "Orange" // library marker kkossev.rgbLib, line 572
            break // library marker kkossev.rgbLib, line 573
        case 46..75: colorName = "Yellow" // library marker kkossev.rgbLib, line 574
            break // library marker kkossev.rgbLib, line 575
        case 76..105: colorName = "Chartreuse" // library marker kkossev.rgbLib, line 576
            break // library marker kkossev.rgbLib, line 577
        case 106..135: colorName = "Green" // library marker kkossev.rgbLib, line 578
            break // library marker kkossev.rgbLib, line 579
        case 136..165: colorName = "Spring" // library marker kkossev.rgbLib, line 580
            break // library marker kkossev.rgbLib, line 581
        case 166..195: colorName = "Cyan" // library marker kkossev.rgbLib, line 582
            break // library marker kkossev.rgbLib, line 583
        case 196..225: colorName = "Azure" // library marker kkossev.rgbLib, line 584
            break // library marker kkossev.rgbLib, line 585
        case 226..255: colorName = "Blue" // library marker kkossev.rgbLib, line 586
            break // library marker kkossev.rgbLib, line 587
        case 256..285: colorName = "Violet" // library marker kkossev.rgbLib, line 588
            break // library marker kkossev.rgbLib, line 589
        case 286..315: colorName = "Magenta" // library marker kkossev.rgbLib, line 590
            break // library marker kkossev.rgbLib, line 591
        case 316..345: colorName = "Rose" // library marker kkossev.rgbLib, line 592
            break // library marker kkossev.rgbLib, line 593
        case 346..360: colorName = "Red" // library marker kkossev.rgbLib, line 594
            break // library marker kkossev.rgbLib, line 595
    } // library marker kkossev.rgbLib, line 596
    String descriptionText = "${device.getDisplayName()} color is ${colorName}" // library marker kkossev.rgbLib, line 597
    return createEvent(name: "colorName", value: colorName ,descriptionText: descriptionText) // library marker kkossev.rgbLib, line 598
} // library marker kkossev.rgbLib, line 599


def startLevelChange(direction) { // library marker kkossev.rgbLib, line 602
    def dir = direction == "up"? 0 : 1 // library marker kkossev.rgbLib, line 603
    def rate = 100 // library marker kkossev.rgbLib, line 604

    if (levelChangeRate != null) { // library marker kkossev.rgbLib, line 606
        rate = levelChangeRate // library marker kkossev.rgbLib, line 607
    } // library marker kkossev.rgbLib, line 608

    return zigbee.command(0x0008, 0x01, "0x${iTo8bitHex(dir)} 0x${iTo8bitHex(rate)}") // library marker kkossev.rgbLib, line 610
} // library marker kkossev.rgbLib, line 611

def stopLevelChange() { // library marker kkossev.rgbLib, line 613
    return zigbee.command(0x0008, 0x03, "") + zigbee.levelRefresh() // library marker kkossev.rgbLib, line 614
} // library marker kkossev.rgbLib, line 615


// Color Management functions // library marker kkossev.rgbLib, line 618

def min(first, ... rest) { // library marker kkossev.rgbLib, line 620
    def min = first; // library marker kkossev.rgbLib, line 621
    for(next in rest) { // library marker kkossev.rgbLib, line 622
        if(next < min) min = next // library marker kkossev.rgbLib, line 623
    } // library marker kkossev.rgbLib, line 624

    min // library marker kkossev.rgbLib, line 626
} // library marker kkossev.rgbLib, line 627

def max(first, ... rest) { // library marker kkossev.rgbLib, line 629
    def max = first; // library marker kkossev.rgbLib, line 630
    for(next in rest) { // library marker kkossev.rgbLib, line 631
        if(next > max) max = next // library marker kkossev.rgbLib, line 632
    } // library marker kkossev.rgbLib, line 633

    max // library marker kkossev.rgbLib, line 635
} // library marker kkossev.rgbLib, line 636

def colorGammaAdjust(component) { // library marker kkossev.rgbLib, line 638
    return (component > 0.04045) ? Math.pow((component + 0.055) / (1.0 + 0.055), 2.4) : (component / 12.92) // library marker kkossev.rgbLib, line 639
} // library marker kkossev.rgbLib, line 640

def colorGammaRevert(component) { // library marker kkossev.rgbLib, line 642
    return (component <= 0.0031308) ? 12.92 * component : (1.0 + 0.055) * Math.pow(component, (1.0 / 2.4)) - 0.055; // library marker kkossev.rgbLib, line 643
} // library marker kkossev.rgbLib, line 644

def colorXy2Rgb(x = 255, y = 255) { // library marker kkossev.rgbLib, line 646

    logTrace "< Color xy: ($x, $y)" // library marker kkossev.rgbLib, line 648

    def Y = 1; // library marker kkossev.rgbLib, line 650
    def X = (Y / y) * x; // library marker kkossev.rgbLib, line 651
    def Z = (Y / y) * (1.0 - x - y); // library marker kkossev.rgbLib, line 652

    logTrace "< Color XYZ: ($X, $Y, $Z)" // library marker kkossev.rgbLib, line 654

    // sRGB, Reference White D65 // library marker kkossev.rgbLib, line 656
    def M = [ // library marker kkossev.rgbLib, line 657
            [  3.2410032, -1.5373990, -0.4986159 ], // library marker kkossev.rgbLib, line 658
            [ -0.9692243,  1.8759300,  0.0415542 ], // library marker kkossev.rgbLib, line 659
            [  0.0556394, -0.2040112,  1.0571490 ] // library marker kkossev.rgbLib, line 660
    ] // library marker kkossev.rgbLib, line 661

    def r = X * M[0][0] + Y * M[0][1] + Z * M[0][2] // library marker kkossev.rgbLib, line 663
    def g = X * M[1][0] + Y * M[1][1] + Z * M[1][2] // library marker kkossev.rgbLib, line 664
    def b = X * M[2][0] + Y * M[2][1] + Z * M[2][2] // library marker kkossev.rgbLib, line 665

    def max = max(r, g, b) // library marker kkossev.rgbLib, line 667
    r = colorGammaRevert(r / max) // library marker kkossev.rgbLib, line 668
    g = colorGammaRevert(g / max) // library marker kkossev.rgbLib, line 669
    b = colorGammaRevert(b / max) // library marker kkossev.rgbLib, line 670

    logTrace "< Color RGB: ($r, $g, $b)" // library marker kkossev.rgbLib, line 672

    [red: r, green: g, blue: b] // library marker kkossev.rgbLib, line 674
} // library marker kkossev.rgbLib, line 675

def colorRgb2Xy(r, g, b) { // library marker kkossev.rgbLib, line 677

    logTrace "> Color RGB: ($r, $g, $b)" // library marker kkossev.rgbLib, line 679

    r = colorGammaAdjust(r) // library marker kkossev.rgbLib, line 681
    g = colorGammaAdjust(g) // library marker kkossev.rgbLib, line 682
    b = colorGammaAdjust(b) // library marker kkossev.rgbLib, line 683

    // sRGB, Reference White D65 // library marker kkossev.rgbLib, line 685
    // D65    0.31271    0.32902 // library marker kkossev.rgbLib, line 686
    //  R  0.64000 0.33000 // library marker kkossev.rgbLib, line 687
    //  G  0.30000 0.60000 // library marker kkossev.rgbLib, line 688
    //  B  0.15000 0.06000 // library marker kkossev.rgbLib, line 689
    def M = [ // library marker kkossev.rgbLib, line 690
            [  0.4123866,  0.3575915,  0.1804505 ], // library marker kkossev.rgbLib, line 691
            [  0.2126368,  0.7151830,  0.0721802 ], // library marker kkossev.rgbLib, line 692
            [  0.0193306,  0.1191972,  0.9503726 ] // library marker kkossev.rgbLib, line 693
    ] // library marker kkossev.rgbLib, line 694

    def X = r * M[0][0] + g * M[0][1] + b * M[0][2] // library marker kkossev.rgbLib, line 696
    def Y = r * M[1][0] + g * M[1][1] + b * M[1][2] // library marker kkossev.rgbLib, line 697
    def Z = r * M[2][0] + g * M[2][1] + b * M[2][2] // library marker kkossev.rgbLib, line 698

    logTrace "> Color XYZ: ($X, $Y, $Z)" // library marker kkossev.rgbLib, line 700

    def x = X / (X + Y + Z) // library marker kkossev.rgbLib, line 702
    def y = Y / (X + Y + Z) // library marker kkossev.rgbLib, line 703

    logTrace "> Color xy: ($x, $y)" // library marker kkossev.rgbLib, line 705

    [x: x, y: y] // library marker kkossev.rgbLib, line 707
} // library marker kkossev.rgbLib, line 708

def colorHsv2Rgb(h, s) { // library marker kkossev.rgbLib, line 710
    logTrace "< Color HSV: ($h, $s, 1)" // library marker kkossev.rgbLib, line 711

    def r // library marker kkossev.rgbLib, line 713
    def g // library marker kkossev.rgbLib, line 714
    def b // library marker kkossev.rgbLib, line 715

    if (s == 0) { // library marker kkossev.rgbLib, line 717
        r = 1 // library marker kkossev.rgbLib, line 718
        g = 1 // library marker kkossev.rgbLib, line 719
        b = 1 // library marker kkossev.rgbLib, line 720
    } // library marker kkossev.rgbLib, line 721
    else { // library marker kkossev.rgbLib, line 722
        def region = (6 * h).intValue() // library marker kkossev.rgbLib, line 723
        def remainder = 6 * h - region // library marker kkossev.rgbLib, line 724

        def p = 1 - s // library marker kkossev.rgbLib, line 726
        def q = 1 - s * remainder // library marker kkossev.rgbLib, line 727
        def t = 1 - s * (1 - remainder) // library marker kkossev.rgbLib, line 728

        if(region == 0) { // library marker kkossev.rgbLib, line 730
            r = 1 // library marker kkossev.rgbLib, line 731
            g = t // library marker kkossev.rgbLib, line 732
            b = p // library marker kkossev.rgbLib, line 733
        } // library marker kkossev.rgbLib, line 734
        else if(region == 1) { // library marker kkossev.rgbLib, line 735
            r = q // library marker kkossev.rgbLib, line 736
            g = 1 // library marker kkossev.rgbLib, line 737
            b = p // library marker kkossev.rgbLib, line 738
        } // library marker kkossev.rgbLib, line 739
        else if(region == 2) { // library marker kkossev.rgbLib, line 740
            r = p // library marker kkossev.rgbLib, line 741
            g = 1 // library marker kkossev.rgbLib, line 742
            b = t // library marker kkossev.rgbLib, line 743
        } // library marker kkossev.rgbLib, line 744
        else if(region == 3) { // library marker kkossev.rgbLib, line 745
            r = p // library marker kkossev.rgbLib, line 746
            g = q // library marker kkossev.rgbLib, line 747
            b = 1 // library marker kkossev.rgbLib, line 748
        } // library marker kkossev.rgbLib, line 749
        else if(region == 4) { // library marker kkossev.rgbLib, line 750
            r = t // library marker kkossev.rgbLib, line 751
            g = p // library marker kkossev.rgbLib, line 752
            b = 1 // library marker kkossev.rgbLib, line 753
        } // library marker kkossev.rgbLib, line 754
        else { // library marker kkossev.rgbLib, line 755
            r = 1 // library marker kkossev.rgbLib, line 756
            g = p // library marker kkossev.rgbLib, line 757
            b = q // library marker kkossev.rgbLib, line 758
        } // library marker kkossev.rgbLib, line 759
    } // library marker kkossev.rgbLib, line 760

    logTrace "< Color RGB: ($r, $g, $b)" // library marker kkossev.rgbLib, line 762

    [red: r, green: g, blue: b] // library marker kkossev.rgbLib, line 764
} // library marker kkossev.rgbLib, line 765


def colorRgb2Hsv(r, g, b) // library marker kkossev.rgbLib, line 768
{ // library marker kkossev.rgbLib, line 769
    logTrace "> Color RGB: ($r, $g, $b)" // library marker kkossev.rgbLib, line 770

    def min = min(r, g, b) // library marker kkossev.rgbLib, line 772
    def max = max(r, g, b) // library marker kkossev.rgbLib, line 773
    def delta = max - min // library marker kkossev.rgbLib, line 774

    def h // library marker kkossev.rgbLib, line 776
    def s // library marker kkossev.rgbLib, line 777
    def v = max // library marker kkossev.rgbLib, line 778

    if (delta == 0) { // library marker kkossev.rgbLib, line 780
        h = 0 // library marker kkossev.rgbLib, line 781
        s = 0 // library marker kkossev.rgbLib, line 782
    } // library marker kkossev.rgbLib, line 783
    else { // library marker kkossev.rgbLib, line 784
        s = delta / max // library marker kkossev.rgbLib, line 785
        if (r == max) h = ( g - b ) / delta            // between yellow & magenta // library marker kkossev.rgbLib, line 786
        else if(g == max) h = 2 + ( b - r ) / delta    // between cyan & yellow // library marker kkossev.rgbLib, line 787
        else h = 4 + ( r - g ) / delta                // between magenta & cyan // library marker kkossev.rgbLib, line 788
        h /= 6 // library marker kkossev.rgbLib, line 789

        if(h < 0) h += 1 // library marker kkossev.rgbLib, line 791
    } // library marker kkossev.rgbLib, line 792

    logTrace "> Color HSV: ($h, $s, $v)" // library marker kkossev.rgbLib, line 794

    return [ hue: h, saturation: s, level: v ] // library marker kkossev.rgbLib, line 796
} // library marker kkossev.rgbLib, line 797

def iTo8bitHex(value) { // library marker kkossev.rgbLib, line 799
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.rgbLib, line 800
} // library marker kkossev.rgbLib, line 801


// ----------- end of Ivar Holand's "IKEA Tradfri RGBW Light HE v2" driver code ------------ // library marker kkossev.rgbLib, line 804

def musicMode(mode) { // library marker kkossev.rgbLib, line 806
    List<String> cmds = [] // library marker kkossev.rgbLib, line 807
    if (mode in MusicModeOpts.options.values()) { // library marker kkossev.rgbLib, line 808
        logDebug "sending musicMode: ${mode}" // library marker kkossev.rgbLib, line 809
        if (mode == "on") { // library marker kkossev.rgbLib, line 810
            cmds = zigbee.writeAttribute(0xFCC0, 0x051C, 0x20, 0x01, [mfgCode: 0x115F], delay=200) // library marker kkossev.rgbLib, line 811
        } // library marker kkossev.rgbLib, line 812
        else if (mode == "off") { // library marker kkossev.rgbLib, line 813
            cmds = zigbee.writeAttribute(0xFCC0, 0x051C, 0x20, 0x00, [mfgCode: 0x115F], delay=200) // library marker kkossev.rgbLib, line 814
        } // library marker kkossev.rgbLib, line 815
    } // library marker kkossev.rgbLib, line 816
    else { // library marker kkossev.rgbLib, line 817
        logWarn "musicMode: invalid mode ${mode}" // library marker kkossev.rgbLib, line 818
        return // library marker kkossev.rgbLib, line 819
    } // library marker kkossev.rgbLib, line 820
    if (cmds == []) { cmds = ["delay 299"] } // library marker kkossev.rgbLib, line 821
    sendZigbeeCommands(cmds) // library marker kkossev.rgbLib, line 822

} // library marker kkossev.rgbLib, line 824


// // library marker kkossev.rgbLib, line 827
// called from updated() in the main code ... // library marker kkossev.rgbLib, line 828
void updatedBulb() { // library marker kkossev.rgbLib, line 829
    logDebug "updatedBulb()..." // library marker kkossev.rgbLib, line 830
} // library marker kkossev.rgbLib, line 831

def colorControlRefresh() { // library marker kkossev.rgbLib, line 833
    def commands = [] // library marker kkossev.rgbLib, line 834
    commands += zigbee.readAttribute(0x0300, 0x03,[:],200) // currentColorX // library marker kkossev.rgbLib, line 835
    commands += zigbee.readAttribute(0x0300, 0x04,[:],201) // currentColorY // library marker kkossev.rgbLib, line 836
    commands // library marker kkossev.rgbLib, line 837
} // library marker kkossev.rgbLib, line 838

def colorControlConfig(min, max, step) { // library marker kkossev.rgbLib, line 840
    def commands = [] // library marker kkossev.rgbLib, line 841
    commands += zigbee.configureReporting(0x0300, 0x03, DataType.UINT16, min, max, step) // currentColorX // library marker kkossev.rgbLib, line 842
    commands += zigbee.configureReporting(0x0300, 0x04, DataType.UINT16, min, max, step) // currentColorY // library marker kkossev.rgbLib, line 843
    commands // library marker kkossev.rgbLib, line 844
} // library marker kkossev.rgbLib, line 845

def refreshBulb() { // library marker kkossev.rgbLib, line 847
    List<String> cmds = [] // library marker kkossev.rgbLib, line 848
    state.colorChanged = false // library marker kkossev.rgbLib, line 849
    state.colorXReported = false // library marker kkossev.rgbLib, line 850
    state.colorYReported = false     // library marker kkossev.rgbLib, line 851
    state.cmds = [] // library marker kkossev.rgbLib, line 852
    cmds =  zigbee.onOffRefresh(200) + zigbee.levelRefresh(201) + colorControlRefresh() // library marker kkossev.rgbLib, line 853
    cmds += zigbee.readAttribute(0x0300,[0x4001,0x400a,0x400b,0x400c,0x000f],[:],204)    // colormode and color/capabilities // library marker kkossev.rgbLib, line 854
    cmds += zigbee.readAttribute(0x0008,[0x000f,0x0010,0x0011],[:],204)                  // config/bri/execute_if_off // library marker kkossev.rgbLib, line 855
    cmds += zigbee.readAttribute(0xFCC0,[0x0515,0x0516,0x517],[mfgCode:0x115F],204)      // config/bri/min & max * startup // library marker kkossev.rgbLib, line 856
    cmds += zigbee.readAttribute(0xFCC0,[0x051B,0x051c],[mfgCode:0x115F],204)            // pixel count & musicMode // library marker kkossev.rgbLib, line 857
    if (cmds == []) { cmds = ["delay 299"] } // library marker kkossev.rgbLib, line 858
    logDebug "refreshBulb: ${cmds} " // library marker kkossev.rgbLib, line 859
    return cmds // library marker kkossev.rgbLib, line 860
} // library marker kkossev.rgbLib, line 861

def configureBulb() { // library marker kkossev.rgbLib, line 863
    List<String> cmds = [] // library marker kkossev.rgbLib, line 864
    logDebug "configureBulb() : ${cmds}" // library marker kkossev.rgbLib, line 865
    cmds = refreshBulb() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig() + colorControlConfig(0, 300, 1) // library marker kkossev.rgbLib, line 866
    if (cmds == []) { cmds = ["delay 299"] }    // no ,  // library marker kkossev.rgbLib, line 867
    return cmds     // library marker kkossev.rgbLib, line 868
} // library marker kkossev.rgbLib, line 869

def initializeBulb() // library marker kkossev.rgbLib, line 871
{ // library marker kkossev.rgbLib, line 872
    List<String> cmds = [] // library marker kkossev.rgbLib, line 873
    logDebug "initializeBulb() : ${cmds}" // library marker kkossev.rgbLib, line 874
    if (cmds == []) { cmds = ["delay 299",] } // library marker kkossev.rgbLib, line 875
    return cmds         // library marker kkossev.rgbLib, line 876
} // library marker kkossev.rgbLib, line 877


void initVarsBulb(boolean fullInit=false) { // library marker kkossev.rgbLib, line 880
    state.colorChanged = false // library marker kkossev.rgbLib, line 881
    state.colorXReported = false // library marker kkossev.rgbLib, line 882
    state.colorYReported = false // library marker kkossev.rgbLib, line 883
    state.colorX = 0.9999 // library marker kkossev.rgbLib, line 884
    state.colorY = 0.9999 // library marker kkossev.rgbLib, line 885
    state.cmds = [] // library marker kkossev.rgbLib, line 886
    //if (fullInit || settings?.temperaturePollingInterval == null) device.updateSetting('temperaturePollingInterval', [value: TemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.rgbLib, line 887

    logDebug "initVarsBulb(${fullInit})" // library marker kkossev.rgbLib, line 889
} // library marker kkossev.rgbLib, line 890


void initEventsBulb(boolean fullInit=false) { // library marker kkossev.rgbLib, line 893
    logDebug "initEventsBulb(${fullInit})" // library marker kkossev.rgbLib, line 894
    if((device.currentState("saturation")?.value == null)) { // library marker kkossev.rgbLib, line 895
        sendEvent(name: "saturation", value: 0); // library marker kkossev.rgbLib, line 896
    } // library marker kkossev.rgbLib, line 897
    if((device.currentState("hue")?.value == null)) { // library marker kkossev.rgbLib, line 898
        sendEvent(name: "hue", value: 0); // library marker kkossev.rgbLib, line 899
    } // library marker kkossev.rgbLib, line 900
    if ((device.currentState("level")?.value == null) || (device.currentState("level")?.value == 0)) { // library marker kkossev.rgbLib, line 901
        sendEvent(name: "level", value: 100) // library marker kkossev.rgbLib, line 902
    }     // library marker kkossev.rgbLib, line 903
} // library marker kkossev.rgbLib, line 904
/* // library marker kkossev.rgbLib, line 905
================================================================================================ // library marker kkossev.rgbLib, line 906
Node Descriptor // library marker kkossev.rgbLib, line 907
================================================================================================ // library marker kkossev.rgbLib, line 908
▸ Logical Type                              = Zigbee Router // library marker kkossev.rgbLib, line 909
▸ Complex Descriptor Available              = No // library marker kkossev.rgbLib, line 910
▸ User Descriptor Available                 = No // library marker kkossev.rgbLib, line 911
▸ Frequency Band                            = 2400 - 2483.5 MHz // library marker kkossev.rgbLib, line 912
▸ Alternate PAN Coordinator                 = No // library marker kkossev.rgbLib, line 913
▸ Device Type                               = Full Function Device (FFD) // library marker kkossev.rgbLib, line 914
▸ Mains Power Source                        = Yes // library marker kkossev.rgbLib, line 915
▸ Receiver On When Idle                     = Yes (always on) // library marker kkossev.rgbLib, line 916
▸ Security Capability                       = No // library marker kkossev.rgbLib, line 917
▸ Allocate Address                          = Yes // library marker kkossev.rgbLib, line 918
▸ Manufacturer Code                         = 0x115F = XIAOMI // library marker kkossev.rgbLib, line 919
▸ Maximum Buffer Size                       = 82 bytes // library marker kkossev.rgbLib, line 920
▸ Maximum Incoming Transfer Size            = 82 bytes // library marker kkossev.rgbLib, line 921
▸ Primary Trust Center                      = No // library marker kkossev.rgbLib, line 922
▸ Backup Trust Center                       = No // library marker kkossev.rgbLib, line 923
▸ Primary Binding Table Cache               = Yes // library marker kkossev.rgbLib, line 924
▸ Backup Binding Table Cache                = No // library marker kkossev.rgbLib, line 925
▸ Primary Discovery Cache                   = Yes // library marker kkossev.rgbLib, line 926
▸ Backup Discovery Cache                    = Yes // library marker kkossev.rgbLib, line 927
▸ Network Manager                           = Yes // library marker kkossev.rgbLib, line 928
▸ Maximum Outgoing Transfer Size            = 82 bytes // library marker kkossev.rgbLib, line 929
▸ Extended Active Endpoint List Available   = No // library marker kkossev.rgbLib, line 930
▸ Extended Simple Descriptor List Available = No // library marker kkossev.rgbLib, line 931
================================================================================================ // library marker kkossev.rgbLib, line 932
Power Descriptor // library marker kkossev.rgbLib, line 933
================================================================================================ // library marker kkossev.rgbLib, line 934
▸ Current Power Mode         = Same as "Receiver On When Idle" from "Node Descriptor" section above // library marker kkossev.rgbLib, line 935
▸ Available Power Sources    = [Constant (mains) power] // library marker kkossev.rgbLib, line 936
▸ Current Power Sources      = [Constant (mains) power] // library marker kkossev.rgbLib, line 937
▸ Current Power Source Level = 100% // library marker kkossev.rgbLib, line 938
================================================================================================ // library marker kkossev.rgbLib, line 939
Endpoint 0x01 | Out Clusters: 0x000A (Time Cluster), 0x0019 (OTA Upgrade Cluster) // library marker kkossev.rgbLib, line 940
================================================================================================ // library marker kkossev.rgbLib, line 941
Endpoint 0x01 | In Cluster: 0x0000 (Basic Cluster) // library marker kkossev.rgbLib, line 942
================================================================================================ // library marker kkossev.rgbLib, line 943
▸ 0x0000 | ZCL Version          | req | r-- | uint8  | 03                | -- // library marker kkossev.rgbLib, line 944
▸ 0x0001 | Application Version  | opt | r-- | uint8  | 1B                | -- // library marker kkossev.rgbLib, line 945
▸ 0x0002 | Stack Version        | opt | r-- | uint8  | 1B                | -- // library marker kkossev.rgbLib, line 946
▸ 0x0003 | HW Version           | opt | r-- | uint8  | 01                | -- // library marker kkossev.rgbLib, line 947
▸ 0x0004 | Manufacturer Name    | opt | r-- | string | Aqara             | -- // library marker kkossev.rgbLib, line 948
▸ 0x0005 | Model Identifier     | opt | r-- | string | lumi.light.acn132 | -- // library marker kkossev.rgbLib, line 949
▸ 0x0006 | Date Code            | req | r-- | string | 20230606          | -- // library marker kkossev.rgbLib, line 950
▸ 0x0007 | Power Source         | opt | r-- | enum8  | 04 = DC source    | -- // library marker kkossev.rgbLib, line 951
▸ 0x000A | Product Code         | opt | r-- | octstr | --                | -- // library marker kkossev.rgbLib, line 952
▸ 0x000D | Serial Number        | opt | r-- | string | --                | -- // library marker kkossev.rgbLib, line 953
▸ 0x0010 | Location Description | opt | rw- | string | é»è®¤æ¿é´     | -- // library marker kkossev.rgbLib, line 954
▸ 0xF000 | --                   | --  | r-- | uint16 | 0000              | -- // library marker kkossev.rgbLib, line 955
▸ 0xFFFD | Cluster Revision     | req | r-- | uint16 | 0002              | -- // library marker kkossev.rgbLib, line 956
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 957
▸ No commands found // library marker kkossev.rgbLib, line 958
================================================================================================ // library marker kkossev.rgbLib, line 959
Endpoint 0x01 | In Cluster: 0x0003 (Identify Cluster) // library marker kkossev.rgbLib, line 960
================================================================================================ // library marker kkossev.rgbLib, line 961
▸ 0x0000 | Identify Time    | req | rw- | uint16 | 0000 = 0 seconds | -- // library marker kkossev.rgbLib, line 962
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0001             | -- // library marker kkossev.rgbLib, line 963
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 964
▸ 0x00 | Identify       | req // library marker kkossev.rgbLib, line 965
▸ 0x01 | Identify Query | req // library marker kkossev.rgbLib, line 966
================================================================================================ // library marker kkossev.rgbLib, line 967
Endpoint 0x01 | In Cluster: 0x0004 (Groups Cluster) // library marker kkossev.rgbLib, line 968
================================================================================================ // library marker kkossev.rgbLib, line 969
▸ 0x0000 | Name Support     | req | r-- | map8   | 00   | -- // library marker kkossev.rgbLib, line 970
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0002 | -- // library marker kkossev.rgbLib, line 971
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 972
▸ 0x00 | Add Group                | req // library marker kkossev.rgbLib, line 973
▸ 0x01 | View Group               | req // library marker kkossev.rgbLib, line 974
▸ 0x02 | Get Group Membership     | req // library marker kkossev.rgbLib, line 975
▸ 0x03 | Remove Group             | req // library marker kkossev.rgbLib, line 976
▸ 0x04 | Remove All Groups        | req // library marker kkossev.rgbLib, line 977
▸ 0x05 | Add Group If Identifying | req // library marker kkossev.rgbLib, line 978
================================================================================================ // library marker kkossev.rgbLib, line 979
Endpoint 0x01 | In Cluster: 0x0005 (Scenes Cluster) // library marker kkossev.rgbLib, line 980
================================================================================================ // library marker kkossev.rgbLib, line 981
▸ 0x0000 | Scene Count      | req | r-- | uint8  | 00         | -- // library marker kkossev.rgbLib, line 982
▸ 0x0001 | Current Scene    | req | r-- | uint8  | 00         | -- // library marker kkossev.rgbLib, line 983
▸ 0x0002 | Current Group    | req | r-- | uint16 | 0000       | -- // library marker kkossev.rgbLib, line 984
▸ 0x0003 | Scene Valid      | req | r-- | bool   | 00 = False | -- // library marker kkossev.rgbLib, line 985
▸ 0x0004 | Name Support     | req | r-- | map8   | 00         | -- // library marker kkossev.rgbLib, line 986
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0002       | -- // library marker kkossev.rgbLib, line 987
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 988
▸ 0x00 | Add Scene            | req // library marker kkossev.rgbLib, line 989
▸ 0x01 | View Scene           | req // library marker kkossev.rgbLib, line 990
▸ 0x02 | Remove Scene         | req // library marker kkossev.rgbLib, line 991
▸ 0x03 | Remove All Scenes    | req // library marker kkossev.rgbLib, line 992
▸ 0x04 | Store Scene          | req // library marker kkossev.rgbLib, line 993
▸ 0x05 | Recall Scene         | req // library marker kkossev.rgbLib, line 994
▸ 0x06 | Get Scene Membership | req // library marker kkossev.rgbLib, line 995
================================================================================================ // library marker kkossev.rgbLib, line 996
Endpoint 0x01 | In Cluster: 0x0006 (On/Off Cluster) // library marker kkossev.rgbLib, line 997
================================================================================================ // library marker kkossev.rgbLib, line 998
▸ 0x0000 | On Off           | req | r-p | bool   | 01 = On  | 0..300 // library marker kkossev.rgbLib, line 999
▸ 0x00F5 | --               | --  | r-- | uint32 | 00D8A053 | --     // library marker kkossev.rgbLib, line 1000
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0002     | --     // library marker kkossev.rgbLib, line 1001
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1002
▸ 0x00 | Off    | req // library marker kkossev.rgbLib, line 1003
▸ 0x01 | On     | req // library marker kkossev.rgbLib, line 1004
▸ 0x02 | Toggle | req // library marker kkossev.rgbLib, line 1005
================================================================================================ // library marker kkossev.rgbLib, line 1006
Endpoint 0x01 | In Cluster: 0x0008 (Level Control Cluster) // library marker kkossev.rgbLib, line 1007
================================================================================================ // library marker kkossev.rgbLib, line 1008
▸ 0x0000 | Current Level          | req | r-p | uint8  | 0C = 4%          | 1..3600 // library marker kkossev.rgbLib, line 1009
▸ 0x0001 | Remaining Time         | opt | r-- | uint16 | 0000 = 0 seconds | --      // library marker kkossev.rgbLib, line 1010
▸ 0x0002 | --                     | --  | r-- | uint8  | 01               | --      // library marker kkossev.rgbLib, line 1011
▸ 0x0003 | --                     | --  | r-- | uint8  | FE               | --      // library marker kkossev.rgbLib, line 1012
▸ 0x000F | --                     | --  | rw- | map8   | 00               | --      // library marker kkossev.rgbLib, line 1013
▸ 0x0010 | On Off Transition Time | opt | rw- | uint16 | 000F = 1 seconds | --      // library marker kkossev.rgbLib, line 1014
▸ 0x0011 | On Level               | opt | rw- | uint8  | 0C = 4%          | --      // library marker kkossev.rgbLib, line 1015
▸ 0x0012 | On Transition Time     | opt | rw- | uint16 | 000F = 1 seconds | --      // library marker kkossev.rgbLib, line 1016
▸ 0x0013 | Off Transition Time    | opt | rw- | uint16 | 000F = 1 seconds | --      // library marker kkossev.rgbLib, line 1017
▸ 0x00F5 | --                     | --  | r-- | uint32 | 00D8A074         | --      // library marker kkossev.rgbLib, line 1018
▸ 0xFFFD | Cluster Revision       | req | r-- | uint16 | 0002             | --      // library marker kkossev.rgbLib, line 1019
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1020
▸ 0x00 | Move To Level             | req // library marker kkossev.rgbLib, line 1021
▸ 0x01 | Move                      | req // library marker kkossev.rgbLib, line 1022
▸ 0x02 | Step                      | req // library marker kkossev.rgbLib, line 1023
▸ 0x03 | Stop                      | req // library marker kkossev.rgbLib, line 1024
▸ 0x04 | Move To Level With On/Off | req // library marker kkossev.rgbLib, line 1025
▸ 0x05 | Move With On/Off          | req // library marker kkossev.rgbLib, line 1026
▸ 0x06 | Step With On/Off          | req // library marker kkossev.rgbLib, line 1027
▸ 0x07 | Stop                      | req // library marker kkossev.rgbLib, line 1028
================================================================================================ // library marker kkossev.rgbLib, line 1029
Endpoint 0x01 | In Cluster: 0x0300 (Color Control Cluster) // library marker kkossev.rgbLib, line 1030
================================================================================================ // library marker kkossev.rgbLib, line 1031
▸ 0x0002 | Remaining Time                   | opt | r-- | uint16 | 0000     | --     // library marker kkossev.rgbLib, line 1032
▸ 0x0003 | CurrentX                         | req | r-p | uint16 | 4A3C     | 0..300 // library marker kkossev.rgbLib, line 1033
▸ 0x0004 | CurrentY                         | req | r-p | uint16 | 8FEB     | 0..300 // library marker kkossev.rgbLib, line 1034
▸ 0x0007 | Color Temperature Mireds         | req | r-p | uint16 | 0099     | --     // library marker kkossev.rgbLib, line 1035
▸ 0x0008 | Color Mode                       | req | r-- | enum8  | 01       | --     // library marker kkossev.rgbLib, line 1036
▸ 0x000F | --                               | --  | rw- | map8   | 00       | --     // library marker kkossev.rgbLib, line 1037
▸ 0x0010 | Number Of Primaries              | req | r-- | uint8  | 00       | --     // library marker kkossev.rgbLib, line 1038
▸ 0x00F5 | --                               | --  | r-- | uint32 | 00D8A06A | --     // library marker kkossev.rgbLib, line 1039
▸ 0x4001 | Enhanced Color Mode              | req | r-- | enum8  | 01       | --     // library marker kkossev.rgbLib, line 1040
▸ 0x400A | Color Capabilities               | req | r-- | map16  | 0018     | --     // library marker kkossev.rgbLib, line 1041
▸ 0x400B | Color Temp Physical Min Mireds   | req | r-- | uint16 | 0099     | --     // library marker kkossev.rgbLib, line 1042
▸ 0x400C | Color Temp Physical Max Mireds   | req | r-- | uint16 | 0172     | --     // library marker kkossev.rgbLib, line 1043
▸ 0x400D | --                               | --  | r-- | uint16 | 0099     | --     // library marker kkossev.rgbLib, line 1044
▸ 0x4010 | StartUp Color Temperature Mireds | opt | rw- | uint16 | 00FA     | --     // library marker kkossev.rgbLib, line 1045
▸ 0xFFFD | Cluster Revision                 | req | r-- | uint16 | 0002     | --     // library marker kkossev.rgbLib, line 1046
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1047
▸ 0x07 | Move to Color             | req // library marker kkossev.rgbLib, line 1048
▸ 0x08 | Move Color                | req // library marker kkossev.rgbLib, line 1049
▸ 0x09 | Step Color                | req // library marker kkossev.rgbLib, line 1050
▸ 0x0A | Move to Color Temperature | req // library marker kkossev.rgbLib, line 1051
▸ 0x47 | Stop Move Step            | req // library marker kkossev.rgbLib, line 1052
▸ 0x4B | Move Color Temperature    | req // library marker kkossev.rgbLib, line 1053
▸ 0x4C | Step Color Temperature    | req // library marker kkossev.rgbLib, line 1054
================================================================================================ // library marker kkossev.rgbLib, line 1055
Endpoint 0x01 | In Cluster: 0xFCC0 (Unknown Cluster) // library marker kkossev.rgbLib, line 1056
================================================================================================ // library marker kkossev.rgbLib, line 1057
▸ No attributes found // library marker kkossev.rgbLib, line 1058
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1059
▸ No commands found // library marker kkossev.rgbLib, line 1060
================================================================================================ // library marker kkossev.rgbLib, line 1061

*/ // library marker kkossev.rgbLib, line 1063

def testT(par) { // library marker kkossev.rgbLib, line 1065
    logWarn "testT(${par})" // library marker kkossev.rgbLib, line 1066
} // library marker kkossev.rgbLib, line 1067


// ~~~~~ end include (143) kkossev.rgbLib ~~~~~
