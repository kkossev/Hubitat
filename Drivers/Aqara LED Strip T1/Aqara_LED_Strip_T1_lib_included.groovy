/**
 *  Aqara E1 Thermostat - Device Driver for Hubitat Elevation
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
static String timeStamp() {"2023/11/06 11:59 PM"}

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
    
    if (fullInit || settings?.logEnable == null) device.updateSetting("logEnable", true)
    if (fullInit || settings?.txtEnable == null) device.updateSetting("txtEnable", true)
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
    
    handleTemperatureEvent(safeToDouble(par) as float)
    
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
 * ver. 1.0.1  2023-11-06 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 25
 * // library marker kkossev.xiaomiLib, line 26
 *                                   TODO:  // library marker kkossev.xiaomiLib, line 27
*/ // library marker kkossev.xiaomiLib, line 28


def xiaomiLibVersion()   {"1.0.1"} // library marker kkossev.xiaomiLib, line 31
def xiaomiLibStamp() {"2023/11/06 9:01 PM"} // library marker kkossev.xiaomiLib, line 32

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
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 73
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 74
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 75
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 76
            if (DEVICE_TYPE in  ["AqaraCube"]) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 77
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 78
            break // library marker kkossev.xiaomiLib, line 79
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 80
            log.info "unknown attribute - resetting?" // library marker kkossev.xiaomiLib, line 81
            break // library marker kkossev.xiaomiLib, line 82
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 83
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 84
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 85
            break // library marker kkossev.xiaomiLib, line 86
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 87
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 88
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 89
            break // library marker kkossev.xiaomiLib, line 90
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 91
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 92
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 93
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 94
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 95
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 96
            } // library marker kkossev.xiaomiLib, line 97
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 98
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 99
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 100
            } // library marker kkossev.xiaomiLib, line 101
            break // library marker kkossev.xiaomiLib, line 102
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 103
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 104
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 105
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 106
            break // library marker kkossev.xiaomiLib, line 107
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 108
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 109
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 110
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 111
            break // library marker kkossev.xiaomiLib, line 112
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 113
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 114
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 115
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 116
            break // library marker kkossev.xiaomiLib, line 117
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 118
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 119
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 120
            break // library marker kkossev.xiaomiLib, line 121
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 122
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 123
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 124
            break // library marker kkossev.xiaomiLib, line 125
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 126
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 127
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 128
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 129
                sendZigbeeCommands(refreshAqaraCube()) // library marker kkossev.xiaomiLib, line 130
            } // library marker kkossev.xiaomiLib, line 131
            break // library marker kkossev.xiaomiLib, line 132
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1  // library marker kkossev.xiaomiLib, line 133
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 134
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 135
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 136
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 137
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 138
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 139
                } // library marker kkossev.xiaomiLib, line 140
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 141
            } // library marker kkossev.xiaomiLib, line 142
            break // library marker kkossev.xiaomiLib, line 143
        default: // library marker kkossev.xiaomiLib, line 144
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 145
            break // library marker kkossev.xiaomiLib, line 146
    } // library marker kkossev.xiaomiLib, line 147
} // library marker kkossev.xiaomiLib, line 148

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 150
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 151
        switch (tag) { // library marker kkossev.xiaomiLib, line 152
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 153
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 154
                break // library marker kkossev.xiaomiLib, line 155
            case 0x03: // library marker kkossev.xiaomiLib, line 156
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 157
                break // library marker kkossev.xiaomiLib, line 158
            case 0x05: // library marker kkossev.xiaomiLib, line 159
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 160
                break // library marker kkossev.xiaomiLib, line 161
            case 0x06: // library marker kkossev.xiaomiLib, line 162
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 163
                break // library marker kkossev.xiaomiLib, line 164
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 165
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 167
                device.updateDataValue("aqaraVersion", swBuild) // library marker kkossev.xiaomiLib, line 168
                break // library marker kkossev.xiaomiLib, line 169
            case 0x0a: // library marker kkossev.xiaomiLib, line 170
                String nwk = intToHexStr(value as Integer,2) // library marker kkossev.xiaomiLib, line 171
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 172
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 173
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 174
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 175
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 176
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 177
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 178
                } // library marker kkossev.xiaomiLib, line 179
                break // library marker kkossev.xiaomiLib, line 180
            case 0x0b: // library marker kkossev.xiaomiLib, line 181
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 182
                break // library marker kkossev.xiaomiLib, line 183
            case 0x64: // library marker kkossev.xiaomiLib, line 184
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value/100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 185
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 186
                break // library marker kkossev.xiaomiLib, line 187
            case 0x65: // library marker kkossev.xiaomiLib, line 188
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 189
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value/100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 190
                break // library marker kkossev.xiaomiLib, line 191
            case 0x66: // library marker kkossev.xiaomiLib, line 192
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 193
                else if (isAqaraTVOC()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 194
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" }  // library marker kkossev.xiaomiLib, line 195
                break // library marker kkossev.xiaomiLib, line 196
            case 0x67: // library marker kkossev.xiaomiLib, line 197
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }     // library marker kkossev.xiaomiLib, line 198
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC:  // library marker kkossev.xiaomiLib, line 199
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 200
                break // library marker kkossev.xiaomiLib, line 201
            case 0x69: // library marker kkossev.xiaomiLib, line 202
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 203
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 204
                break // library marker kkossev.xiaomiLib, line 205
            case 0x6a: // library marker kkossev.xiaomiLib, line 206
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 207
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 208
                break // library marker kkossev.xiaomiLib, line 209
            case 0x6b: // library marker kkossev.xiaomiLib, line 210
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 212
                break // library marker kkossev.xiaomiLib, line 213
            case 0x95: // library marker kkossev.xiaomiLib, line 214
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 215
                break // library marker kkossev.xiaomiLib, line 216
            case 0x96: // library marker kkossev.xiaomiLib, line 217
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 218
                break // library marker kkossev.xiaomiLib, line 219
            case 0x97: // library marker kkossev.xiaomiLib, line 220
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 221
                break // library marker kkossev.xiaomiLib, line 222
            case 0x98: // library marker kkossev.xiaomiLib, line 223
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 224
                break // library marker kkossev.xiaomiLib, line 225
            case 0x9b: // library marker kkossev.xiaomiLib, line 226
                if (isAqaraCube()) {  // library marker kkossev.xiaomiLib, line 227
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})"  // library marker kkossev.xiaomiLib, line 228
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 229
                } // library marker kkossev.xiaomiLib, line 230
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 231
                break // library marker kkossev.xiaomiLib, line 232
            default: // library marker kkossev.xiaomiLib, line 233
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 234
        } // library marker kkossev.xiaomiLib, line 235
    } // library marker kkossev.xiaomiLib, line 236
} // library marker kkossev.xiaomiLib, line 237


/** // library marker kkossev.xiaomiLib, line 240
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 241
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 242
 */ // library marker kkossev.xiaomiLib, line 243
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 244
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 245
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 246
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 247
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 248
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 249
    } // library marker kkossev.xiaomiLib, line 250
    return bigInt // library marker kkossev.xiaomiLib, line 251
} // library marker kkossev.xiaomiLib, line 252

/** // library marker kkossev.xiaomiLib, line 254
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 255
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 256
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 257
 */ // library marker kkossev.xiaomiLib, line 258
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 259
    final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 260
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 261
    new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 262
        while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 263
            int tag = stream.read() // library marker kkossev.xiaomiLib, line 264
            int dataType = stream.read() // library marker kkossev.xiaomiLib, line 265
            Object value // library marker kkossev.xiaomiLib, line 266
            if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 267
                int length = stream.read() // library marker kkossev.xiaomiLib, line 268
                byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 269
                stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 270
                value = new String(byteArr) // library marker kkossev.xiaomiLib, line 271
            } else { // library marker kkossev.xiaomiLib, line 272
                int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 273
                value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 274
            } // library marker kkossev.xiaomiLib, line 275
            results[tag] = value // library marker kkossev.xiaomiLib, line 276
        } // library marker kkossev.xiaomiLib, line 277
    } // library marker kkossev.xiaomiLib, line 278
    return results // library marker kkossev.xiaomiLib, line 279
} // library marker kkossev.xiaomiLib, line 280


def refreshXiaomi() { // library marker kkossev.xiaomiLib, line 283
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 284
    if (cmds == []) { cmds = ["delay 299"] } // library marker kkossev.xiaomiLib, line 285
    return cmds // library marker kkossev.xiaomiLib, line 286
} // library marker kkossev.xiaomiLib, line 287

def configureXiaomi() { // library marker kkossev.xiaomiLib, line 289
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 290
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 291
    if (cmds == []) { cmds = ["delay 299"] }    // no ,  // library marker kkossev.xiaomiLib, line 292
    return cmds     // library marker kkossev.xiaomiLib, line 293
} // library marker kkossev.xiaomiLib, line 294

def initializeXiaomi() // library marker kkossev.xiaomiLib, line 296
{ // library marker kkossev.xiaomiLib, line 297
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 298
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 299
    if (cmds == []) { cmds = ["delay 299",] } // library marker kkossev.xiaomiLib, line 300
    return cmds         // library marker kkossev.xiaomiLib, line 301
} // library marker kkossev.xiaomiLib, line 302

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 304
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 305
} // library marker kkossev.xiaomiLib, line 306

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 308
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 309
} // library marker kkossev.xiaomiLib, line 310


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
 * ver. 1.0.0  2023-11-06 kkossev  - added rgbLib // library marker kkossev.rgbLib, line 26
 * // library marker kkossev.rgbLib, line 27
 *                                   TODO:  // library marker kkossev.rgbLib, line 28
*/ // library marker kkossev.rgbLib, line 29

def thermostatLibVersion()   {"1.0.0"} // library marker kkossev.rgbLib, line 31
def thermostatLibStamp() {"2023/11/06 11:59 PM"} // library marker kkossev.rgbLib, line 32

//import groovy.transform.Field // library marker kkossev.rgbLib, line 34
import hubitat.helper.ColorUtils // library marker kkossev.rgbLib, line 35

metadata { // library marker kkossev.rgbLib, line 37
    capability "Actuator" // library marker kkossev.rgbLib, line 38
    capability "Color Control" // library marker kkossev.rgbLib, line 39
    capability "ColorMode" // library marker kkossev.rgbLib, line 40
    capability "Color Temperature" // library marker kkossev.rgbLib, line 41
    capability "Refresh" // library marker kkossev.rgbLib, line 42
    capability "Switch" // library marker kkossev.rgbLib, line 43
    capability "Switch Level" // library marker kkossev.rgbLib, line 44
    capability "Light" // library marker kkossev.rgbLib, line 45
    capability "ChangeLevel" // library marker kkossev.rgbLib, line 46

    if (_DEBUG) { command "testT", [[name: "testT", type: "STRING", description: "testT", defaultValue : ""]]  } // library marker kkossev.rgbLib, line 48

    fingerprint profileId:"0104", endpointId:"01", inClusters:"0005,0004,0003,0000,0300,0008,0006,FCC0", outClusters:"0019,000A", model:"lumi.light.acn132", manufacturer:"Aqara" // library marker kkossev.rgbLib, line 50

    preferences { // library marker kkossev.rgbLib, line 52
    } // library marker kkossev.rgbLib, line 53
} // library marker kkossev.rgbLib, line 54



private getMAX_WHITE_SATURATION() { 70 } // library marker kkossev.rgbLib, line 58
private getWHITE_HUE() { 8 } // library marker kkossev.rgbLib, line 59
private getMIN_COLOR_TEMP() { 2700 } // library marker kkossev.rgbLib, line 60
private getMAX_COLOR_TEMP() { 6500 } // library marker kkossev.rgbLib, line 61


/* // library marker kkossev.rgbLib, line 64
 * ----------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 65
 * Level Control Cluster            0x0008 // library marker kkossev.rgbLib, line 66
 * ----------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 67
*/ // library marker kkossev.rgbLib, line 68
void parseLevelControlClusterBulb(final Map descMap) { // library marker kkossev.rgbLib, line 69
    logDebug "parseLevelControlClusterBulb: 0x${descMap.value}" // library marker kkossev.rgbLib, line 70
    if (descMap.attrId == "0000") { // library marker kkossev.rgbLib, line 71
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.rgbLib, line 72
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 73
        // Aqara LED Strip T1 sends the level in the range 0..255 // library marker kkossev.rgbLib, line 74
        def scaledValue = ((rawValue as double) / 2.55F + 0.5) as int // library marker kkossev.rgbLib, line 75
        sendLevelControlEvent(scaledValue) // library marker kkossev.rgbLib, line 76
    } // library marker kkossev.rgbLib, line 77
    else { // library marker kkossev.rgbLib, line 78
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.rgbLib, line 79
    } // library marker kkossev.rgbLib, line 80
} // library marker kkossev.rgbLib, line 81

/* // library marker kkossev.rgbLib, line 83
 * ----------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 84
 * ColorControl Cluster            0x0300 // library marker kkossev.rgbLib, line 85
 * ----------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 86
*/ // library marker kkossev.rgbLib, line 87
void parseColorControlClusterBulb(final Map descMap, description) { // library marker kkossev.rgbLib, line 88
    if (descMap.attrId != null) { // library marker kkossev.rgbLib, line 89
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseColorControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.rgbLib, line 90
        processColorControlCluster(descMap, description) // library marker kkossev.rgbLib, line 91
    } // library marker kkossev.rgbLib, line 92
    else { // library marker kkossev.rgbLib, line 93
        logWarn "unprocessed ColorControl attribute ${descMap.attrId}" // library marker kkossev.rgbLib, line 94
    } // library marker kkossev.rgbLib, line 95
} // library marker kkossev.rgbLib, line 96


void processColorControlCluster(final Map descMap, description) { // library marker kkossev.rgbLib, line 99
    def map = [:] // library marker kkossev.rgbLib, line 100

    if (description instanceof String)  { // library marker kkossev.rgbLib, line 102
        map = stringToMap(description) // library marker kkossev.rgbLib, line 103
    } // library marker kkossev.rgbLib, line 104

    logTrace "Map - $map" // library marker kkossev.rgbLib, line 106
    def raw = map["read attr - raw"] // library marker kkossev.rgbLib, line 107

    if(raw) { // library marker kkossev.rgbLib, line 109
        def clusterId = map.cluster // library marker kkossev.rgbLib, line 110
        def attrList = raw.substring(12) // library marker kkossev.rgbLib, line 111

        parsed = parseAttributeList(clusterId, attrList) // library marker kkossev.rgbLib, line 113

        if(state.colorChanged || (state.colorXReported && state.colorYReported)) { // library marker kkossev.rgbLib, line 115
            state.colorChanged = false; // library marker kkossev.rgbLib, line 116
            state.colorXReported = false; // library marker kkossev.rgbLib, line 117
            state.colorYReported = false; // library marker kkossev.rgbLib, line 118
            logTrace "Color Change: xy ($state.colorX, $state.colorY)" // library marker kkossev.rgbLib, line 119
            def rgb = colorXy2Rgb(state.colorX, state.colorY) // library marker kkossev.rgbLib, line 120
            logTrace "Color Change: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.rgbLib, line 121
            updateColor(rgb)        // sends a bunch of events! // library marker kkossev.rgbLib, line 122
        } // library marker kkossev.rgbLib, line 123
    } // library marker kkossev.rgbLib, line 124
    else { // library marker kkossev.rgbLib, line 125
        logDebug "Sending color event based on pending values" // library marker kkossev.rgbLib, line 126
        if (state.pendingColorUpdate) { // library marker kkossev.rgbLib, line 127
            parsed = true // library marker kkossev.rgbLib, line 128
            def rgb = colorXy2Rgb(state.colorX, state.colorY) // library marker kkossev.rgbLib, line 129
            updateColor(rgb)            // sends a bunch of events! // library marker kkossev.rgbLib, line 130
            state.pendingColorUpdate = false // library marker kkossev.rgbLib, line 131
        } // library marker kkossev.rgbLib, line 132
    } // library marker kkossev.rgbLib, line 133
} // library marker kkossev.rgbLib, line 134

def parseHex4le(hex) { // library marker kkossev.rgbLib, line 136
    Integer.parseInt(hex.substring(2, 4) + hex.substring(0, 2), 16) // library marker kkossev.rgbLib, line 137
} // library marker kkossev.rgbLib, line 138

def parseColorAttribute(id, value) { // library marker kkossev.rgbLib, line 140
    def parsed = false // library marker kkossev.rgbLib, line 141

    if(id == 0x03) { // library marker kkossev.rgbLib, line 143
        // currentColorX // library marker kkossev.rgbLib, line 144
        value = parseHex4le(value) // library marker kkossev.rgbLib, line 145
        logTrace "Parsed ColorX: $value" // library marker kkossev.rgbLib, line 146
        value /= 65536 // library marker kkossev.rgbLib, line 147
        parsed = true // library marker kkossev.rgbLib, line 148
        state.colorXReported = true; // library marker kkossev.rgbLib, line 149
        state.colorChanged |= value != colorX // library marker kkossev.rgbLib, line 150
        state.colorX = value // library marker kkossev.rgbLib, line 151
    } // library marker kkossev.rgbLib, line 152
    else if(id == 0x04) { // library marker kkossev.rgbLib, line 153
        // currentColorY // library marker kkossev.rgbLib, line 154
        value = parseHex4le(value) // library marker kkossev.rgbLib, line 155
        logTrace "Parsed ColorY: $value" // library marker kkossev.rgbLib, line 156
        value /= 65536 // library marker kkossev.rgbLib, line 157
        parsed = true // library marker kkossev.rgbLib, line 158
        state.colorYReported = true; // library marker kkossev.rgbLib, line 159
        state.colorChanged |= value != colorY // library marker kkossev.rgbLib, line 160
        state.colorY = value // library marker kkossev.rgbLib, line 161
    } // library marker kkossev.rgbLib, line 162
    else { // library marker kkossev.rgbLib, line 163
        logDebug "Not parsing Color cluster attribute $id: $value" // library marker kkossev.rgbLib, line 164
    } // library marker kkossev.rgbLib, line 165

    parsed // library marker kkossev.rgbLib, line 167
} // library marker kkossev.rgbLib, line 168




def parseAttributeList(cluster, list) { // library marker kkossev.rgbLib, line 173
    logTrace "Cluster: $cluster, AttrList: $list" // library marker kkossev.rgbLib, line 174
    def parsed = true // library marker kkossev.rgbLib, line 175

    while(list.length()) { // library marker kkossev.rgbLib, line 177
        def attrId = parseHex4le(list.substring(0, 4)) // library marker kkossev.rgbLib, line 178
        def attrType = Integer.parseInt(list.substring(4, 6), 16) // library marker kkossev.rgbLib, line 179
        def attrShift = 0 // library marker kkossev.rgbLib, line 180

        if(!attrType) { // library marker kkossev.rgbLib, line 182
            attrType = Integer.parseInt(list.substring(6, 8), 16) // library marker kkossev.rgbLib, line 183
            attrShift = 1 // library marker kkossev.rgbLib, line 184
        } // library marker kkossev.rgbLib, line 185

        def attrLen = DataType.getLength(attrType) // library marker kkossev.rgbLib, line 187
        def attrValue = list.substring(6 + 2*attrShift, 6 + 2*(attrShift+attrLen)) // library marker kkossev.rgbLib, line 188

        logTrace "Attr - Id: $attrId($attrLen), Type: $attrType, Value: $attrValue" // library marker kkossev.rgbLib, line 190

        if(cluster == 300) { // library marker kkossev.rgbLib, line 192
            parsed &= parseColorAttribute(attrId, attrValue) // library marker kkossev.rgbLib, line 193
        } // library marker kkossev.rgbLib, line 194
        else { // library marker kkossev.rgbLib, line 195
            log.info "Not parsing cluster $cluster attribute: $list" // library marker kkossev.rgbLib, line 196
            parsed = false; // library marker kkossev.rgbLib, line 197
        } // library marker kkossev.rgbLib, line 198

        list = list.substring(6 + 2*(attrShift+attrLen)) // library marker kkossev.rgbLib, line 200
    } // library marker kkossev.rgbLib, line 201

    parsed // library marker kkossev.rgbLib, line 203
} // library marker kkossev.rgbLib, line 204



/* // library marker kkossev.rgbLib, line 208
def sendColorControlEvent( rawValue ) { // library marker kkossev.rgbLib, line 209
    logWarn "TODO: sendColorControlEvent ($rawValue)" // library marker kkossev.rgbLib, line 210
    return // library marker kkossev.rgbLib, line 211

    def value = rawValue as int // library marker kkossev.rgbLib, line 213
    if (value <0) value = 0 // library marker kkossev.rgbLib, line 214
    if (value >100) value = 100 // library marker kkossev.rgbLib, line 215
    def map = [:]  // library marker kkossev.rgbLib, line 216

    def isDigital = state.states["isDigital"] // library marker kkossev.rgbLib, line 218
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.rgbLib, line 219

    map.name = "level" // library marker kkossev.rgbLib, line 221
    map.value = value // library marker kkossev.rgbLib, line 222
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.rgbLib, line 223
    if (isRefresh == true) { // library marker kkossev.rgbLib, line 224
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.rgbLib, line 225
        map.isStateChange = true // library marker kkossev.rgbLib, line 226
    } // library marker kkossev.rgbLib, line 227
    else { // library marker kkossev.rgbLib, line 228
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.rgbLib, line 229
    } // library marker kkossev.rgbLib, line 230
    logInfo "${map.descriptionText}" // library marker kkossev.rgbLib, line 231
    sendEvent(map) // library marker kkossev.rgbLib, line 232
    clearIsDigital() // library marker kkossev.rgbLib, line 233
} // library marker kkossev.rgbLib, line 234
*/ // library marker kkossev.rgbLib, line 235

// called from parseXiaomiClusterLib in xiaomiLib.groovy (xiaomi cluster 0xFCC0 ) // library marker kkossev.rgbLib, line 237
// // library marker kkossev.rgbLib, line 238
void parseXiaomiClusterRgbLib(final Map descMap) { // library marker kkossev.rgbLib, line 239
    //logWarn "parseXiaomiClusterRgbLib: received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.rgbLib, line 240
    final Integer raw // library marker kkossev.rgbLib, line 241
    final String  value // library marker kkossev.rgbLib, line 242
    switch (descMap.attrInt as Integer) { // library marker kkossev.rgbLib, line 243
        case 0x040a:    // E1 battery - read only // library marker kkossev.rgbLib, line 244
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 245
            thermostatEvent("battery", raw, raw) // library marker kkossev.rgbLib, line 246
            break // library marker kkossev.rgbLib, line 247
        case 0x00F7 :   // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes // library marker kkossev.rgbLib, line 248
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.rgbLib, line 249
            parseXiaomiClusterThermostatTags(tags) // library marker kkossev.rgbLib, line 250
            break // library marker kkossev.rgbLib, line 251
        case 0x0271:    // result['system_mode'] = {1: 'heat', 0: 'off'}[value]; (heating state) - rw // library marker kkossev.rgbLib, line 252
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 253
            value = SystemModeOpts.options[raw as int] // library marker kkossev.rgbLib, line 254
            thermostatEvent("system_mode", value, raw) // library marker kkossev.rgbLib, line 255
            break; // library marker kkossev.rgbLib, line 256
        case 0x0272:    // result['preset'] = {2: 'away', 1: 'auto', 0: 'manual'}[value]; - rw  ['manual', 'auto', 'holiday'] // library marker kkossev.rgbLib, line 257
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 258
            value = PresetOpts.options[raw as int] // library marker kkossev.rgbLib, line 259
            thermostatEvent("preset", value, raw) // library marker kkossev.rgbLib, line 260
            break; // library marker kkossev.rgbLib, line 261
        case 0x0273:    // result['window_detection'] = {1: 'ON', 0: 'OFF'}[value]; - rw // library marker kkossev.rgbLib, line 262
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 263
            value = WindowDetectionOpts.options[raw as int] // library marker kkossev.rgbLib, line 264
            thermostatEvent("window_detection", value, raw) // library marker kkossev.rgbLib, line 265
            break; // library marker kkossev.rgbLib, line 266
        case 0x0274:    // result['valve_detection'] = {1: 'ON', 0: 'OFF'}[value]; -rw  // library marker kkossev.rgbLib, line 267
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 268
            value = ValveDetectionOpts.options[raw as int] // library marker kkossev.rgbLib, line 269
            thermostatEvent("valve_detection", value, raw) // library marker kkossev.rgbLib, line 270
            break; // library marker kkossev.rgbLib, line 271
        case 0x0275:    // result['valve_alarm'] = {1: true, 0: false}[value]; - read only! // library marker kkossev.rgbLib, line 272
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 273
            value = ValveAlarmOpts.options[raw as int] // library marker kkossev.rgbLib, line 274
            thermostatEvent("valve_alarm", value, raw) // library marker kkossev.rgbLib, line 275
            break; // library marker kkossev.rgbLib, line 276
        case 0x0277:    // result['child_lock'] = {1: 'LOCK', 0: 'UNLOCK'}[value]; - rw // library marker kkossev.rgbLib, line 277
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 278
            value = ChildLockOpts.options[raw as int] // library marker kkossev.rgbLib, line 279
            thermostatEvent("child_lock", value, raw) // library marker kkossev.rgbLib, line 280
            break; // library marker kkossev.rgbLib, line 281
        case 0x0279:    // result['away_preset_temperature'] = (value / 100).toFixed(1); - rw // library marker kkossev.rgbLib, line 282
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 283
            value = raw / 100 // library marker kkossev.rgbLib, line 284
            thermostatEvent("away_preset_temperature", value, raw) // library marker kkossev.rgbLib, line 285
            break; // library marker kkossev.rgbLib, line 286
        case 0x027a:    // result['window_open'] = {1: true, 0: false}[value]; - read only // library marker kkossev.rgbLib, line 287
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 288
            value = WindowOpenOpts.options[raw as int] // library marker kkossev.rgbLib, line 289
            thermostatEvent("window_open", value, raw) // library marker kkossev.rgbLib, line 290
            break; // library marker kkossev.rgbLib, line 291
        case 0x027b:    // result['calibrated'] = {1: true, 0: false}[value]; - read only // library marker kkossev.rgbLib, line 292
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 293
            value = CalibratedOpts.options[raw as int] // library marker kkossev.rgbLib, line 294
            thermostatEvent("calibrated", value, raw) // library marker kkossev.rgbLib, line 295
            break; // library marker kkossev.rgbLib, line 296
        case 0x0276:    // unknown // library marker kkossev.rgbLib, line 297
        case 0x027c:    // unknown // library marker kkossev.rgbLib, line 298
        case 0x027d:    // unknown // library marker kkossev.rgbLib, line 299
        case 0x0280:    // unknown // library marker kkossev.rgbLib, line 300
        case 0xfff2:    // unknown // library marker kkossev.rgbLib, line 301
        case 0x00ff:    // unknown // library marker kkossev.rgbLib, line 302
        case 0x00f7:    // unknown // library marker kkossev.rgbLib, line 303
        case 0xfff2:    // unknown // library marker kkossev.rgbLib, line 304
        case 0x00FF: // library marker kkossev.rgbLib, line 305
            try { // library marker kkossev.rgbLib, line 306
                raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 307
                logDebug "Aqara E1 TRV unknown attribute ${descMap.attrInt} value raw = ${raw}" // library marker kkossev.rgbLib, line 308
            } // library marker kkossev.rgbLib, line 309
            catch (e) { // library marker kkossev.rgbLib, line 310
                logWarn "exception caught while processing Aqara E1 TRV unknown attribute ${descMap.attrInt} descMap.value = ${descMap.value}" // library marker kkossev.rgbLib, line 311
            } // library marker kkossev.rgbLib, line 312
            break; // library marker kkossev.rgbLib, line 313
        case 0x027e:    // result['sensor'] = {1: 'external', 0: 'internal'}[value]; - read only? // library marker kkossev.rgbLib, line 314
            raw = hexStrToUnsignedInt(descMap.value) // library marker kkossev.rgbLib, line 315
            value = SensorOpts.options[raw as int] // library marker kkossev.rgbLib, line 316
            thermostatEvent("sensor", value, raw) // library marker kkossev.rgbLib, line 317
            break; // library marker kkossev.rgbLib, line 318
        default: // library marker kkossev.rgbLib, line 319
            logWarn "parseXiaomiClusterRgbLib: received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.rgbLib, line 320
            break // library marker kkossev.rgbLib, line 321
    } // library marker kkossev.rgbLib, line 322
} // library marker kkossev.rgbLib, line 323


/* // library marker kkossev.rgbLib, line 326
dev:42212023-11-06 10:33:21.660debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x74=2 // library marker kkossev.rgbLib, line 327
dev:42212023-11-06 10:33:21.655debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x72=10 // library marker kkossev.rgbLib, line 328
dev:42212023-11-06 10:33:21.651debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x71=1 // library marker kkossev.rgbLib, line 329
dev:42212023-11-06 10:33:21.644debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x6E=2 // library marker kkossev.rgbLib, line 330
dev:42212023-11-06 10:33:21.638debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x6D=2 // library marker kkossev.rgbLib, line 331
dev:42212023-11-06 10:33:21.631debugAqara T1 LED lumi.light.acn132 descMap = [raw:D8A001FCC026F700410F6D20026E200271200172200A742002, dni:D8A0, endpoint:01, cluster:FCC0, size:26, attrId:00F7, encoding:41, command:0A, value:6D20026E200271200172200A742002, clusterInt:64704, attrInt:247] // library marker kkossev.rgbLib, line 332
dev:42212023-11-06 10:33:21.619debugAqara T1 LED lumi.light.acn132 parse: read attr - raw: D8A001FCC026F700410F6D20026E200271200172200A742002, dni: D8A0, endpoint: 01, cluster: FCC0, size: 26, attrId: 00F7, encoding: 41, command: 0A, value: 0F6D20026E200271200172200A742002 // library marker kkossev.rgbLib, line 333
dev:42212023-11-06 10:33:21.453debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x6C=0 // library marker kkossev.rgbLib, line 334
dev:42212023-11-06 10:33:21.446debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x73=1245482987 // library marker kkossev.rgbLib, line 335
dev:42212023-11-06 10:33:21.441debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x69=0 // library marker kkossev.rgbLib, line 336
dev:42212023-11-06 10:33:21.436debugAqara T1 LED lumi.light.acn132 xiaomi decode MOTION SENSITIVITY tag: 0x6A=425986700 // library marker kkossev.rgbLib, line 337
dev:42212023-11-06 10:33:21.431debugAqara T1 LED lumi.light.acn132 xiaomi decode tag: 0x66 presure is 153 // library marker kkossev.rgbLib, line 338
dev:42212023-11-06 10:33:21.426debugAqara T1 LED lumi.light.acn132 xiaomi decode tag: 0x65 humidity is 0.05 (raw 5) // library marker kkossev.rgbLib, line 339
dev:42212023-11-06 10:33:21.421debugAqara T1 LED lumi.light.acn132 xiaomi decode tag: 0x64 temperature is 0.01 (raw 1) // library marker kkossev.rgbLib, line 340
dev:42212023-11-06 10:33:21.416debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x9A=0 // library marker kkossev.rgbLib, line 341
dev:42212023-11-06 10:33:21.411debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x11=513 // library marker kkossev.rgbLib, line 342
dev:42212023-11-06 10:33:21.406debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x0D=6683 // library marker kkossev.rgbLib, line 343
dev:42212023-11-06 10:33:21.401debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x0C=10 // library marker kkossev.rgbLib, line 344
dev:42212023-11-06 10:33:21.396debugAqara T1 LED lumi.light.acn132 xiaomi decode tag: 0x0B light level is 0 // library marker kkossev.rgbLib, line 345
dev:42212023-11-06 10:33:21.391debugAqara T1 LED lumi.light.acn132 xiaomi decode tag: 0x0A Parent NWK is 0000 // library marker kkossev.rgbLib, line 346
dev:42212023-11-06 10:33:21.386debugAqara T1 LED lumi.light.acn132 xiaomi decode unknown tag: 0x09=1536 // library marker kkossev.rgbLib, line 347
dev:42212023-11-06 10:33:21.380debugAqara T1 LED lumi.light.acn132 xiaomi decode tag: 0x05 RSSI is 17 // library marker kkossev.rgbLib, line 348
dev:42212023-11-06 10:33:21.375debugAqara T1 LED lumi.light.acn132 xiaomi decode tag: 0x03 device temperature is 36° // library marker kkossev.rgbLib, line 349
dev:42212023-11-06 10:33:21.323debugAqara T1 LED lumi.light.acn132 descMap = [raw:D8A001FCC088F700414003282405211100092100060A2100000B20000C200A0D231B1A00001123010200009A2000642001652005662199006A238C0A64196920007323EB8F3C4A6C2000, dni:D8A0, endpoint:01, cluster:FCC0, size:88, attrId:00F7, encoding:41, command:0A, value:03282405211100092100060A2100000B20000C200A0D231B1A00001123010200009A2000642001652005662199006A238C0A64196920007323EB8F3C4A6C2000, clusterInt:64704, attrInt:247] // library marker kkossev.rgbLib, line 350
dev:42212023-11-06 10:33:21.306debugAqara T1 LED lumi.light.acn132 parse: read attr - raw: D8A001FCC088F700414003282405211100092100060A2100000B20000C200A0D231B1A00001123010200009A2000642001652005662199006A238C0A64196920007323EB8F3C4A6C2000, dni: D8A0, endpoint: 01, cluster: FCC0, size: 88, attrId: 00F7, encoding: 41, command: 0A, value: 4003282405211100092100060A2100000B20000C200A0D231B1A00001123010200009A2000642001652005662199006A238C0A64196920007323EB8F3C4A6C2000 // library marker kkossev.rgbLib, line 351
*/ // library marker kkossev.rgbLib, line 352

// called from parseXiaomiClusterRgbLib  // library marker kkossev.rgbLib, line 354
void parseXiaomiClusterRgbTags(final Map<Integer, Object> tags) {       // TODO: check https://github.com/sprut/Hub/issues/2420  // library marker kkossev.rgbLib, line 355
    tags.each { final Integer tag, final Object value -> // library marker kkossev.rgbLib, line 356
        switch (tag) { // library marker kkossev.rgbLib, line 357
            case 0x01:    // battery voltage // library marker kkossev.rgbLib, line 358
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})" // library marker kkossev.rgbLib, line 359
                break // library marker kkossev.rgbLib, line 360
            case 0x03: // library marker kkossev.rgbLib, line 361
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device internal chip temperature is ${value}&deg; (ignore it!)" // library marker kkossev.rgbLib, line 362
                break // library marker kkossev.rgbLib, line 363
            case 0x05: // library marker kkossev.rgbLib, line 364
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.rgbLib, line 365
                break // library marker kkossev.rgbLib, line 366
            case 0x06: // library marker kkossev.rgbLib, line 367
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.rgbLib, line 368
                break // library marker kkossev.rgbLib, line 369
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.rgbLib, line 370
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.rgbLib, line 371
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.rgbLib, line 372
                device.updateDataValue("aqaraVersion", swBuild) // library marker kkossev.rgbLib, line 373
                break // library marker kkossev.rgbLib, line 374
            case 0x0a: // library marker kkossev.rgbLib, line 375
                String nwk = intToHexStr(value as Integer,2) // library marker kkossev.rgbLib, line 376
                if (state.health == null) { state.health = [:] } // library marker kkossev.rgbLib, line 377
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.rgbLib, line 378
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.rgbLib, line 379
                if (oldNWK != nwk ) { // library marker kkossev.rgbLib, line 380
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.rgbLib, line 381
                    state.health['parentNWK']  = nwk // library marker kkossev.rgbLib, line 382
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.rgbLib, line 383
                } // library marker kkossev.rgbLib, line 384
                break // library marker kkossev.rgbLib, line 385
            case 0x0d: // library marker kkossev.rgbLib, line 386
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 387
                break             // library marker kkossev.rgbLib, line 388
            case 0x11: // library marker kkossev.rgbLib, line 389
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 390
                break             // library marker kkossev.rgbLib, line 391
            case 0x64: // library marker kkossev.rgbLib, line 392
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value/100} (raw ${value})"    // Aqara TVOC // library marker kkossev.rgbLib, line 393
                break // library marker kkossev.rgbLib, line 394
            case 0x65: // library marker kkossev.rgbLib, line 395
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 396
                break // library marker kkossev.rgbLib, line 397
            case 0x66: // library marker kkossev.rgbLib, line 398
                logDebug "xiaomi decode E1 thermostat temperature tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 399
                handleTemperatureEvent(value/100.0) // library marker kkossev.rgbLib, line 400
                break // library marker kkossev.rgbLib, line 401
            case 0x67: // library marker kkossev.rgbLib, line 402
                logDebug "xiaomi decode E1 thermostat heatingSetpoint tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 403
                break // library marker kkossev.rgbLib, line 404
            case 0x68: // library marker kkossev.rgbLib, line 405
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 406
                break // library marker kkossev.rgbLib, line 407
            case 0x69: // library marker kkossev.rgbLib, line 408
                logDebug "xiaomi decode E1 thermostat battery tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 409
                break // library marker kkossev.rgbLib, line 410
            case 0x6a: // library marker kkossev.rgbLib, line 411
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 412
                break // library marker kkossev.rgbLib, line 413
            default: // library marker kkossev.rgbLib, line 414
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.rgbLib, line 415
        } // library marker kkossev.rgbLib, line 416
    } // library marker kkossev.rgbLib, line 417
} // library marker kkossev.rgbLib, line 418


// all the code below is borrowed from Ivar Holand's "IKEA Tradfri RGBW Light HE v2" driver // library marker kkossev.rgbLib, line 421
// ----------------------------------------------------------------------------------------- // library marker kkossev.rgbLib, line 422


def updateColor(rgb) { // library marker kkossev.rgbLib, line 425
    logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.rgbLib, line 426
    def hsv = colorRgb2Hsv(rgb.red, rgb.green, rgb.blue) // library marker kkossev.rgbLib, line 427
    hsv.hue = Math.round(hsv.hue * 100).intValue() // library marker kkossev.rgbLib, line 428
    hsv.saturation = Math.round(hsv.saturation * 100).intValue() // library marker kkossev.rgbLib, line 429
    hsv.level = Math.round(hsv.level * 100).intValue() // library marker kkossev.rgbLib, line 430
    logTrace "updateColor: HSV ($hsv.hue, $hsv.saturation, $hsv.level)" // library marker kkossev.rgbLib, line 431

    rgb.red = Math.round(rgb.red * 255).intValue() // library marker kkossev.rgbLib, line 433
    rgb.green = Math.round(rgb.green * 255).intValue() // library marker kkossev.rgbLib, line 434
    rgb.blue = Math.round(rgb.blue * 255).intValue() // library marker kkossev.rgbLib, line 435
    logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.rgbLib, line 436

    def color = ColorUtils.rgbToHEX([rgb.red, rgb.green, rgb.blue]) // library marker kkossev.rgbLib, line 438
    logTrace "updateColor: $color" // library marker kkossev.rgbLib, line 439

    //sendEvent(name: "color", value: color, data: [ hue: hsv.hue, saturation: hsv.saturation, red: rgb.red, green: rgb.green, blue: rgb.blue, hex: color], displayed: false) // library marker kkossev.rgbLib, line 441
    sendColorEvent([name: "color", value: color, data: [ hue: hsv.hue, saturation: hsv.saturation, red: rgb.red, green: rgb.green, blue: rgb.blue, hex: color], displayed: false]) // library marker kkossev.rgbLib, line 442
    sendHueEvent([name: "hue", value: hsv.hue, displayed: false]) // library marker kkossev.rgbLib, line 443
    sendSaturationEvent([name: "saturation", value: hsv.saturation, displayed: false]) // library marker kkossev.rgbLib, line 444
    if (hsv.hue == WHITE_HUE) { // library marker kkossev.rgbLib, line 445
        def percent = (1 - ((hsv.saturation / 100) * (100 / MAX_WHITE_SATURATION))) // library marker kkossev.rgbLib, line 446
        def amount = (MAX_COLOR_TEMP - MIN_COLOR_TEMP) * percent // library marker kkossev.rgbLib, line 447
        def val = Math.round(MIN_COLOR_TEMP + amount) // library marker kkossev.rgbLib, line 448
        sendColorTemperatureEvent([name: "colorTemperature", value: val]) // library marker kkossev.rgbLib, line 449
        sendColorModeEvent([name: "colorMode", value: "CT"]) // library marker kkossev.rgbLib, line 450
        sendColorNameEvent([setGenericTempName(val)]) // library marker kkossev.rgbLib, line 451
    }  // library marker kkossev.rgbLib, line 452
    else { // library marker kkossev.rgbLib, line 453
        sendColorModeEvent([name: "colorMode", value: "RGB"]) // library marker kkossev.rgbLib, line 454
        sendColorNameEvent(setGenericName(hsv.hue)) // library marker kkossev.rgbLib, line 455
    } // library marker kkossev.rgbLib, line 456
} // library marker kkossev.rgbLib, line 457

void sendColorEvent(map) { // library marker kkossev.rgbLib, line 459
    if (map.value == device.currentValue(map.name)) { // library marker kkossev.rgbLib, line 460
        logDebug "sendColorEvent: ${map.name} is already ${map.value}" // library marker kkossev.rgbLib, line 461
        return // library marker kkossev.rgbLib, line 462
    } // library marker kkossev.rgbLib, line 463
    // get the time of the last event named "color" and compare it to the current time // library marker kkossev.rgbLib, line 464
 //   def lastColorEvent = device.currentState("color",true).date.time // library marker kkossev.rgbLib, line 465
 //   if ((now() - lastColorEvent) < 1000) { // library marker kkossev.rgbLib, line 466
       // logDebug "sendColorEvent: delaying ${map.name} event because the last color event was less than 1 second ago ${(now() - lastColorEvent)}" // library marker kkossev.rgbLib, line 467
        runInMillis(500, "sendDelayedColorEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 468
        return // library marker kkossev.rgbLib, line 469
//    } // library marker kkossev.rgbLib, line 470
    //unschedule("sendDelayedColorEvent") // cancel any pending delayed events // library marker kkossev.rgbLib, line 471
    //logDebug "sendColorEvent: lastColorEvent = ${lastColorEvent}, now = ${now()}, diff = ${(now() - lastColorEvent)}" // library marker kkossev.rgbLib, line 472
    //sendEvent(map) // library marker kkossev.rgbLib, line 473
} // library marker kkossev.rgbLib, line 474
private void sendDelayedColorEvent(Map map) { // library marker kkossev.rgbLib, line 475
    sendEvent(map) // library marker kkossev.rgbLib, line 476
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 477
} // library marker kkossev.rgbLib, line 478

void sendHueEvent(map) { // library marker kkossev.rgbLib, line 480
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 481
    runInMillis(500, "sendDelayedHueEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 482
} // library marker kkossev.rgbLib, line 483
private void sendDelayedHueEvent(Map map) { // library marker kkossev.rgbLib, line 484
    sendEvent(map) // library marker kkossev.rgbLib, line 485
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 486
} // library marker kkossev.rgbLib, line 487

void sendSaturationEvent(map) { // library marker kkossev.rgbLib, line 489
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 490
    runInMillis(500, "sendDelayedSaturationEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 491
} // library marker kkossev.rgbLib, line 492
private void sendDelayedSaturationEvent(Map map) { // library marker kkossev.rgbLib, line 493
    sendEvent(map) // library marker kkossev.rgbLib, line 494
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 495
} // library marker kkossev.rgbLib, line 496

void sendColorModeEvent(map) { // library marker kkossev.rgbLib, line 498
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 499
    runInMillis(500, "sendDelayedColorModeEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 500
} // library marker kkossev.rgbLib, line 501
private void sendDelayedColorModeEvent(Map map) { // library marker kkossev.rgbLib, line 502
    sendEvent(map) // library marker kkossev.rgbLib, line 503
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 504
} // library marker kkossev.rgbLib, line 505

void sendColorNameEvent(map) { // library marker kkossev.rgbLib, line 507
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 508
    runInMillis(500, "sendDelayedColorNameEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 509
} // library marker kkossev.rgbLib, line 510
private void sendDelayedColorNameEvent(Map map) { // library marker kkossev.rgbLib, line 511
    sendEvent(map) // library marker kkossev.rgbLib, line 512
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 513
} // library marker kkossev.rgbLib, line 514

void sendColorTemperatureEvent(map) { // library marker kkossev.rgbLib, line 516
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.rgbLib, line 517
    runInMillis(500, "sendDelayedColorTemperatureEvent",  [overwrite: true, data: map]) // library marker kkossev.rgbLib, line 518
} // library marker kkossev.rgbLib, line 519
private void sendDelayedColorTemperatureEvent(Map map) { // library marker kkossev.rgbLib, line 520
    sendEvent(map) // library marker kkossev.rgbLib, line 521
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.rgbLib, line 522
} // library marker kkossev.rgbLib, line 523


def sendZigbeeCommandsDelayed() { // library marker kkossev.rgbLib, line 526
    List cmds = state.cmds // library marker kkossev.rgbLib, line 527
    if (cmds != null) { // library marker kkossev.rgbLib, line 528
        state.cmds = [] // library marker kkossev.rgbLib, line 529
        sendZigbeeCommands(cmds) // library marker kkossev.rgbLib, line 530
    } // library marker kkossev.rgbLib, line 531
} // library marker kkossev.rgbLib, line 532

def setLevelBulb(value, rate=null) { // library marker kkossev.rgbLib, line 534
    logDebug "setLevelBulb: $value, $rate" // library marker kkossev.rgbLib, line 535

    state.pendingLevelChange = value // library marker kkossev.rgbLib, line 537

    if (rate == null) { // library marker kkossev.rgbLib, line 539
        state.cmds += zigbee.setLevel(value) // library marker kkossev.rgbLib, line 540
    } else { // library marker kkossev.rgbLib, line 541
        state.cmds += zigbee.setLevel(value, rate) // library marker kkossev.rgbLib, line 542
    } // library marker kkossev.rgbLib, line 543

    unschedule(sendZigbeeCommandsDelayed) // library marker kkossev.rgbLib, line 545
    runInMillis(100, sendZigbeeCommandsDelayed) // library marker kkossev.rgbLib, line 546
} // library marker kkossev.rgbLib, line 547


def setColorTemperature(value, level=null, rate=null) { // library marker kkossev.rgbLib, line 550
    logDebug "Set color temperature $value" // library marker kkossev.rgbLib, line 551

    def sat = MAX_WHITE_SATURATION - (((value - MIN_COLOR_TEMP) / (MAX_COLOR_TEMP - MIN_COLOR_TEMP)) * MAX_WHITE_SATURATION) // library marker kkossev.rgbLib, line 553
    setColor([ // library marker kkossev.rgbLib, line 554
            hue: WHITE_HUE, // library marker kkossev.rgbLib, line 555
            saturation: sat, // library marker kkossev.rgbLib, line 556
            level: level, // library marker kkossev.rgbLib, line 557
            rate: rate // library marker kkossev.rgbLib, line 558
    ]) // library marker kkossev.rgbLib, line 559
} // library marker kkossev.rgbLib, line 560

def setColor(value) { // library marker kkossev.rgbLib, line 562
    logDebug "setColor($value)" // library marker kkossev.rgbLib, line 563
    def rgb = colorHsv2Rgb(value.hue / 100, value.saturation / 100) // library marker kkossev.rgbLib, line 564

    logTrace "setColor: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.rgbLib, line 566
    def xy = colorRgb2Xy(rgb.red, rgb.green, rgb.blue); // library marker kkossev.rgbLib, line 567
    logTrace "setColor: xy ($xy.x, $xy.y)" // library marker kkossev.rgbLib, line 568

    def intX = Math.round(xy.x*65536).intValue() // 0..65279 // library marker kkossev.rgbLib, line 570
    def intY = Math.round(xy.y*65536).intValue() // 0..65279 // library marker kkossev.rgbLib, line 571

    logTrace "setColor: xy ($intX, $intY)" // library marker kkossev.rgbLib, line 573

    state.colorX = xy.x // library marker kkossev.rgbLib, line 575
    state.colorY = xy.y // library marker kkossev.rgbLib, line 576

    def strX = DataType.pack(intX, DataType.UINT16, true); // library marker kkossev.rgbLib, line 578
    def strY = DataType.pack(intY, DataType.UINT16, true); // library marker kkossev.rgbLib, line 579

    List cmds = [] // library marker kkossev.rgbLib, line 581

    def level = value.level // library marker kkossev.rgbLib, line 583
    def rate = value.rate // library marker kkossev.rgbLib, line 584

    if (level != null && rate != null) { // library marker kkossev.rgbLib, line 586
        state.pendingLevelChange = level // library marker kkossev.rgbLib, line 587
        cmds += zigbee.setLevel(level, rate) // library marker kkossev.rgbLib, line 588
    } else if (level != null) { // library marker kkossev.rgbLib, line 589
        state.pendingLevelChange = level // library marker kkossev.rgbLib, line 590
        cmds += zigbee.setLevel(level) // library marker kkossev.rgbLib, line 591
    } // library marker kkossev.rgbLib, line 592

    state.pendingColorUpdate = true // library marker kkossev.rgbLib, line 594

    cmds += zigbee.command(0x0300, 0x07, strX, strY, "0a00") // library marker kkossev.rgbLib, line 596
    if (state.cmds == null) { state.cmds = [] }    // library marker kkossev.rgbLib, line 597
    state.cmds += cmds // library marker kkossev.rgbLib, line 598

    logTrace "zigbee command: $cmds" // library marker kkossev.rgbLib, line 600

    unschedule(sendZigbeeCommandsDelayed) // library marker kkossev.rgbLib, line 602
    runInMillis(100, sendZigbeeCommandsDelayed) // library marker kkossev.rgbLib, line 603
} // library marker kkossev.rgbLib, line 604


def setHue(hue) { // library marker kkossev.rgbLib, line 607
    logDebug "setHue: $hue" // library marker kkossev.rgbLib, line 608
    setColor([ hue: hue, saturation: device.currentValue("saturation") ]) // library marker kkossev.rgbLib, line 609
} // library marker kkossev.rgbLib, line 610

def setSaturation(saturation) { // library marker kkossev.rgbLib, line 612
    logDebug "setSaturation: $saturation" // library marker kkossev.rgbLib, line 613
    setColor([ hue: device.currentValue("hue"), saturation: saturation ]) // library marker kkossev.rgbLib, line 614
} // library marker kkossev.rgbLib, line 615

def setGenericTempName(temp){ // library marker kkossev.rgbLib, line 617
    if (!temp) return // library marker kkossev.rgbLib, line 618
    String genericName // library marker kkossev.rgbLib, line 619
    int value = temp.toInteger() // library marker kkossev.rgbLib, line 620
    if (value <= 2000) genericName = "Sodium" // library marker kkossev.rgbLib, line 621
    else if (value <= 2100) genericName = "Starlight" // library marker kkossev.rgbLib, line 622
    else if (value < 2400) genericName = "Sunrise" // library marker kkossev.rgbLib, line 623
    else if (value < 2800) genericName = "Incandescent" // library marker kkossev.rgbLib, line 624
    else if (value < 3300) genericName = "Soft White" // library marker kkossev.rgbLib, line 625
    else if (value < 3500) genericName = "Warm White" // library marker kkossev.rgbLib, line 626
    else if (value < 4150) genericName = "Moonlight" // library marker kkossev.rgbLib, line 627
    else if (value <= 5000) genericName = "Horizon" // library marker kkossev.rgbLib, line 628
    else if (value < 5500) genericName = "Daylight" // library marker kkossev.rgbLib, line 629
    else if (value < 6000) genericName = "Electronic" // library marker kkossev.rgbLib, line 630
    else if (value <= 6500) genericName = "Skylight" // library marker kkossev.rgbLib, line 631
    else if (value < 20000) genericName = "Polar" // library marker kkossev.rgbLib, line 632
    String descriptionText = "${device.getDisplayName()} color is ${genericName}" // library marker kkossev.rgbLib, line 633
    return createEvent(name: "colorName", value: genericName ,descriptionText: descriptionText) // library marker kkossev.rgbLib, line 634
} // library marker kkossev.rgbLib, line 635

def setGenericName(hue){ // library marker kkossev.rgbLib, line 637
    String colorName // library marker kkossev.rgbLib, line 638
    hue = hue.toInteger() // library marker kkossev.rgbLib, line 639
    hue = (hue * 3.6) // library marker kkossev.rgbLib, line 640
    switch (hue.toInteger()){ // library marker kkossev.rgbLib, line 641
        case 0..15: colorName = "Red" // library marker kkossev.rgbLib, line 642
            break // library marker kkossev.rgbLib, line 643
        case 16..45: colorName = "Orange" // library marker kkossev.rgbLib, line 644
            break // library marker kkossev.rgbLib, line 645
        case 46..75: colorName = "Yellow" // library marker kkossev.rgbLib, line 646
            break // library marker kkossev.rgbLib, line 647
        case 76..105: colorName = "Chartreuse" // library marker kkossev.rgbLib, line 648
            break // library marker kkossev.rgbLib, line 649
        case 106..135: colorName = "Green" // library marker kkossev.rgbLib, line 650
            break // library marker kkossev.rgbLib, line 651
        case 136..165: colorName = "Spring" // library marker kkossev.rgbLib, line 652
            break // library marker kkossev.rgbLib, line 653
        case 166..195: colorName = "Cyan" // library marker kkossev.rgbLib, line 654
            break // library marker kkossev.rgbLib, line 655
        case 196..225: colorName = "Azure" // library marker kkossev.rgbLib, line 656
            break // library marker kkossev.rgbLib, line 657
        case 226..255: colorName = "Blue" // library marker kkossev.rgbLib, line 658
            break // library marker kkossev.rgbLib, line 659
        case 256..285: colorName = "Violet" // library marker kkossev.rgbLib, line 660
            break // library marker kkossev.rgbLib, line 661
        case 286..315: colorName = "Magenta" // library marker kkossev.rgbLib, line 662
            break // library marker kkossev.rgbLib, line 663
        case 316..345: colorName = "Rose" // library marker kkossev.rgbLib, line 664
            break // library marker kkossev.rgbLib, line 665
        case 346..360: colorName = "Red" // library marker kkossev.rgbLib, line 666
            break // library marker kkossev.rgbLib, line 667
    } // library marker kkossev.rgbLib, line 668
    String descriptionText = "${device.getDisplayName()} color is ${colorName}" // library marker kkossev.rgbLib, line 669
    return createEvent(name: "colorName", value: colorName ,descriptionText: descriptionText) // library marker kkossev.rgbLib, line 670
} // library marker kkossev.rgbLib, line 671


def startLevelChange(direction) { // library marker kkossev.rgbLib, line 674
    def dir = direction == "up"? 0 : 1 // library marker kkossev.rgbLib, line 675
    def rate = 100 // library marker kkossev.rgbLib, line 676

    if (levelChangeRate != null) { // library marker kkossev.rgbLib, line 678
        rate = levelChangeRate // library marker kkossev.rgbLib, line 679
    } // library marker kkossev.rgbLib, line 680

    return zigbee.command(0x0008, 0x01, "0x${iTo8bitHex(dir)} 0x${iTo8bitHex(rate)}") // library marker kkossev.rgbLib, line 682
} // library marker kkossev.rgbLib, line 683

def stopLevelChange() { // library marker kkossev.rgbLib, line 685
    return zigbee.command(0x0008, 0x03, "") + zigbee.levelRefresh() // library marker kkossev.rgbLib, line 686
} // library marker kkossev.rgbLib, line 687


// Color Management functions // library marker kkossev.rgbLib, line 690

def min(first, ... rest) { // library marker kkossev.rgbLib, line 692
    def min = first; // library marker kkossev.rgbLib, line 693
    for(next in rest) { // library marker kkossev.rgbLib, line 694
        if(next < min) min = next // library marker kkossev.rgbLib, line 695
    } // library marker kkossev.rgbLib, line 696

    min // library marker kkossev.rgbLib, line 698
} // library marker kkossev.rgbLib, line 699

def max(first, ... rest) { // library marker kkossev.rgbLib, line 701
    def max = first; // library marker kkossev.rgbLib, line 702
    for(next in rest) { // library marker kkossev.rgbLib, line 703
        if(next > max) max = next // library marker kkossev.rgbLib, line 704
    } // library marker kkossev.rgbLib, line 705

    max // library marker kkossev.rgbLib, line 707
} // library marker kkossev.rgbLib, line 708

def colorGammaAdjust(component) { // library marker kkossev.rgbLib, line 710
    return (component > 0.04045) ? Math.pow((component + 0.055) / (1.0 + 0.055), 2.4) : (component / 12.92) // library marker kkossev.rgbLib, line 711
} // library marker kkossev.rgbLib, line 712

def colorGammaRevert(component) { // library marker kkossev.rgbLib, line 714
    return (component <= 0.0031308) ? 12.92 * component : (1.0 + 0.055) * Math.pow(component, (1.0 / 2.4)) - 0.055; // library marker kkossev.rgbLib, line 715
} // library marker kkossev.rgbLib, line 716

def colorXy2Rgb(x = 255, y = 255) { // library marker kkossev.rgbLib, line 718

    logTrace "< Color xy: ($x, $y)" // library marker kkossev.rgbLib, line 720

    def Y = 1; // library marker kkossev.rgbLib, line 722
    def X = (Y / y) * x; // library marker kkossev.rgbLib, line 723
    def Z = (Y / y) * (1.0 - x - y); // library marker kkossev.rgbLib, line 724

    logTrace "< Color XYZ: ($X, $Y, $Z)" // library marker kkossev.rgbLib, line 726

    // sRGB, Reference White D65 // library marker kkossev.rgbLib, line 728
    def M = [ // library marker kkossev.rgbLib, line 729
            [  3.2410032, -1.5373990, -0.4986159 ], // library marker kkossev.rgbLib, line 730
            [ -0.9692243,  1.8759300,  0.0415542 ], // library marker kkossev.rgbLib, line 731
            [  0.0556394, -0.2040112,  1.0571490 ] // library marker kkossev.rgbLib, line 732
    ] // library marker kkossev.rgbLib, line 733

    def r = X * M[0][0] + Y * M[0][1] + Z * M[0][2] // library marker kkossev.rgbLib, line 735
    def g = X * M[1][0] + Y * M[1][1] + Z * M[1][2] // library marker kkossev.rgbLib, line 736
    def b = X * M[2][0] + Y * M[2][1] + Z * M[2][2] // library marker kkossev.rgbLib, line 737

    def max = max(r, g, b) // library marker kkossev.rgbLib, line 739
    r = colorGammaRevert(r / max) // library marker kkossev.rgbLib, line 740
    g = colorGammaRevert(g / max) // library marker kkossev.rgbLib, line 741
    b = colorGammaRevert(b / max) // library marker kkossev.rgbLib, line 742

    logTrace "< Color RGB: ($r, $g, $b)" // library marker kkossev.rgbLib, line 744

    [red: r, green: g, blue: b] // library marker kkossev.rgbLib, line 746
} // library marker kkossev.rgbLib, line 747

def colorRgb2Xy(r, g, b) { // library marker kkossev.rgbLib, line 749

    logTrace "> Color RGB: ($r, $g, $b)" // library marker kkossev.rgbLib, line 751

    r = colorGammaAdjust(r) // library marker kkossev.rgbLib, line 753
    g = colorGammaAdjust(g) // library marker kkossev.rgbLib, line 754
    b = colorGammaAdjust(b) // library marker kkossev.rgbLib, line 755

    // sRGB, Reference White D65 // library marker kkossev.rgbLib, line 757
    // D65    0.31271    0.32902 // library marker kkossev.rgbLib, line 758
    //  R  0.64000 0.33000 // library marker kkossev.rgbLib, line 759
    //  G  0.30000 0.60000 // library marker kkossev.rgbLib, line 760
    //  B  0.15000 0.06000 // library marker kkossev.rgbLib, line 761
    def M = [ // library marker kkossev.rgbLib, line 762
            [  0.4123866,  0.3575915,  0.1804505 ], // library marker kkossev.rgbLib, line 763
            [  0.2126368,  0.7151830,  0.0721802 ], // library marker kkossev.rgbLib, line 764
            [  0.0193306,  0.1191972,  0.9503726 ] // library marker kkossev.rgbLib, line 765
    ] // library marker kkossev.rgbLib, line 766

    def X = r * M[0][0] + g * M[0][1] + b * M[0][2] // library marker kkossev.rgbLib, line 768
    def Y = r * M[1][0] + g * M[1][1] + b * M[1][2] // library marker kkossev.rgbLib, line 769
    def Z = r * M[2][0] + g * M[2][1] + b * M[2][2] // library marker kkossev.rgbLib, line 770

    logTrace "> Color XYZ: ($X, $Y, $Z)" // library marker kkossev.rgbLib, line 772

    def x = X / (X + Y + Z) // library marker kkossev.rgbLib, line 774
    def y = Y / (X + Y + Z) // library marker kkossev.rgbLib, line 775

    logTrace "> Color xy: ($x, $y)" // library marker kkossev.rgbLib, line 777

    [x: x, y: y] // library marker kkossev.rgbLib, line 779
} // library marker kkossev.rgbLib, line 780

def colorHsv2Rgb(h, s) { // library marker kkossev.rgbLib, line 782
    logTrace "< Color HSV: ($h, $s, 1)" // library marker kkossev.rgbLib, line 783

    def r // library marker kkossev.rgbLib, line 785
    def g // library marker kkossev.rgbLib, line 786
    def b // library marker kkossev.rgbLib, line 787

    if (s == 0) { // library marker kkossev.rgbLib, line 789
        r = 1 // library marker kkossev.rgbLib, line 790
        g = 1 // library marker kkossev.rgbLib, line 791
        b = 1 // library marker kkossev.rgbLib, line 792
    } // library marker kkossev.rgbLib, line 793
    else { // library marker kkossev.rgbLib, line 794
        def region = (6 * h).intValue() // library marker kkossev.rgbLib, line 795
        def remainder = 6 * h - region // library marker kkossev.rgbLib, line 796

        def p = 1 - s // library marker kkossev.rgbLib, line 798
        def q = 1 - s * remainder // library marker kkossev.rgbLib, line 799
        def t = 1 - s * (1 - remainder) // library marker kkossev.rgbLib, line 800

        if(region == 0) { // library marker kkossev.rgbLib, line 802
            r = 1 // library marker kkossev.rgbLib, line 803
            g = t // library marker kkossev.rgbLib, line 804
            b = p // library marker kkossev.rgbLib, line 805
        } // library marker kkossev.rgbLib, line 806
        else if(region == 1) { // library marker kkossev.rgbLib, line 807
            r = q // library marker kkossev.rgbLib, line 808
            g = 1 // library marker kkossev.rgbLib, line 809
            b = p // library marker kkossev.rgbLib, line 810
        } // library marker kkossev.rgbLib, line 811
        else if(region == 2) { // library marker kkossev.rgbLib, line 812
            r = p // library marker kkossev.rgbLib, line 813
            g = 1 // library marker kkossev.rgbLib, line 814
            b = t // library marker kkossev.rgbLib, line 815
        } // library marker kkossev.rgbLib, line 816
        else if(region == 3) { // library marker kkossev.rgbLib, line 817
            r = p // library marker kkossev.rgbLib, line 818
            g = q // library marker kkossev.rgbLib, line 819
            b = 1 // library marker kkossev.rgbLib, line 820
        } // library marker kkossev.rgbLib, line 821
        else if(region == 4) { // library marker kkossev.rgbLib, line 822
            r = t // library marker kkossev.rgbLib, line 823
            g = p // library marker kkossev.rgbLib, line 824
            b = 1 // library marker kkossev.rgbLib, line 825
        } // library marker kkossev.rgbLib, line 826
        else { // library marker kkossev.rgbLib, line 827
            r = 1 // library marker kkossev.rgbLib, line 828
            g = p // library marker kkossev.rgbLib, line 829
            b = q // library marker kkossev.rgbLib, line 830
        } // library marker kkossev.rgbLib, line 831
    } // library marker kkossev.rgbLib, line 832

    logTrace "< Color RGB: ($r, $g, $b)" // library marker kkossev.rgbLib, line 834

    [red: r, green: g, blue: b] // library marker kkossev.rgbLib, line 836
} // library marker kkossev.rgbLib, line 837


def colorRgb2Hsv(r, g, b) // library marker kkossev.rgbLib, line 840
{ // library marker kkossev.rgbLib, line 841
    logTrace "> Color RGB: ($r, $g, $b)" // library marker kkossev.rgbLib, line 842

    def min = min(r, g, b) // library marker kkossev.rgbLib, line 844
    def max = max(r, g, b) // library marker kkossev.rgbLib, line 845
    def delta = max - min // library marker kkossev.rgbLib, line 846

    def h // library marker kkossev.rgbLib, line 848
    def s // library marker kkossev.rgbLib, line 849
    def v = max // library marker kkossev.rgbLib, line 850

    if (delta == 0) { // library marker kkossev.rgbLib, line 852
        h = 0 // library marker kkossev.rgbLib, line 853
        s = 0 // library marker kkossev.rgbLib, line 854
    } // library marker kkossev.rgbLib, line 855
    else { // library marker kkossev.rgbLib, line 856
        s = delta / max // library marker kkossev.rgbLib, line 857
        if (r == max) h = ( g - b ) / delta            // between yellow & magenta // library marker kkossev.rgbLib, line 858
        else if(g == max) h = 2 + ( b - r ) / delta    // between cyan & yellow // library marker kkossev.rgbLib, line 859
        else h = 4 + ( r - g ) / delta                // between magenta & cyan // library marker kkossev.rgbLib, line 860
        h /= 6 // library marker kkossev.rgbLib, line 861

        if(h < 0) h += 1 // library marker kkossev.rgbLib, line 863
    } // library marker kkossev.rgbLib, line 864

    logTrace "> Color HSV: ($h, $s, $v)" // library marker kkossev.rgbLib, line 866

    return [ hue: h, saturation: s, level: v ] // library marker kkossev.rgbLib, line 868
} // library marker kkossev.rgbLib, line 869

def iTo8bitHex(value) { // library marker kkossev.rgbLib, line 871
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.rgbLib, line 872
} // library marker kkossev.rgbLib, line 873

def logTrace(msg) { // library marker kkossev.rgbLib, line 875
    if(traceEnable) log.trace msg // library marker kkossev.rgbLib, line 876
} // library marker kkossev.rgbLib, line 877


// ----------- end of Ivar Holand's "IKEA Tradfri RGBW Light HE v2" driver code ------------ // library marker kkossev.rgbLib, line 880


// // library marker kkossev.rgbLib, line 883
// called from updated() in the main code ... // library marker kkossev.rgbLib, line 884
void updatedBulb() { // library marker kkossev.rgbLib, line 885
    logDebug "updatedBulb()..." // library marker kkossev.rgbLib, line 886
} // library marker kkossev.rgbLib, line 887

def colorControlRefresh() { // library marker kkossev.rgbLib, line 889
    def commands = [] // library marker kkossev.rgbLib, line 890
    commands += zigbee.readAttribute(0x0300, 0x03) // currentColorX // library marker kkossev.rgbLib, line 891
    commands += zigbee.readAttribute(0x0300, 0x04) // currentColorY // library marker kkossev.rgbLib, line 892
    commands // library marker kkossev.rgbLib, line 893
} // library marker kkossev.rgbLib, line 894

def colorControlConfig(min, max, step) { // library marker kkossev.rgbLib, line 896
    def commands = [] // library marker kkossev.rgbLib, line 897
    commands += zigbee.configureReporting(0x0300, 0x03, DataType.UINT16, min, max, step) // currentColorX // library marker kkossev.rgbLib, line 898
    commands += zigbee.configureReporting(0x0300, 0x04, DataType.UINT16, min, max, step) // currentColorY // library marker kkossev.rgbLib, line 899
    commands // library marker kkossev.rgbLib, line 900
} // library marker kkossev.rgbLib, line 901

def refreshBulb() { // library marker kkossev.rgbLib, line 903
    List<String> cmds = [] // library marker kkossev.rgbLib, line 904
    state.colorChanged = false // library marker kkossev.rgbLib, line 905
    state.colorXReported = false // library marker kkossev.rgbLib, line 906
    state.colorYReported = false     // library marker kkossev.rgbLib, line 907
    state.cmds = [] // library marker kkossev.rgbLib, line 908
    cmds = zigbee.onOffRefresh() + zigbee.levelRefresh() + colorControlRefresh() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig() + colorControlConfig(0, 300, 1) // library marker kkossev.rgbLib, line 909
    if (cmds == []) { cmds = ["delay 299"] } // library marker kkossev.rgbLib, line 910
    logDebug "refreshBulb: ${cmds} " // library marker kkossev.rgbLib, line 911
    return cmds // library marker kkossev.rgbLib, line 912
} // library marker kkossev.rgbLib, line 913

def configureBulb() { // library marker kkossev.rgbLib, line 915
    List<String> cmds = [] // library marker kkossev.rgbLib, line 916
    logDebug "configureBulb() : ${cmds}" // library marker kkossev.rgbLib, line 917
    cmds = refreshBulb() // library marker kkossev.rgbLib, line 918
    if (cmds == []) { cmds = ["delay 299"] }    // no ,  // library marker kkossev.rgbLib, line 919
    return cmds     // library marker kkossev.rgbLib, line 920
} // library marker kkossev.rgbLib, line 921

def initializeBulb() // library marker kkossev.rgbLib, line 923
{ // library marker kkossev.rgbLib, line 924
    List<String> cmds = [] // library marker kkossev.rgbLib, line 925
    logDebug "initializeBulb() : ${cmds}" // library marker kkossev.rgbLib, line 926
    if (cmds == []) { cmds = ["delay 299",] } // library marker kkossev.rgbLib, line 927
    return cmds         // library marker kkossev.rgbLib, line 928
} // library marker kkossev.rgbLib, line 929


void initVarsBulb(boolean fullInit=false) { // library marker kkossev.rgbLib, line 932
    state.colorChanged = false // library marker kkossev.rgbLib, line 933
    state.colorXReported = false // library marker kkossev.rgbLib, line 934
    state.colorYReported = false // library marker kkossev.rgbLib, line 935
    state.cmds = [] // library marker kkossev.rgbLib, line 936
    logDebug "initVarsBulb(${fullInit})" // library marker kkossev.rgbLib, line 937
} // library marker kkossev.rgbLib, line 938


void initEventsBulb(boolean fullInit=false) { // library marker kkossev.rgbLib, line 941
    logDebug "initEventsBulb(${fullInit})" // library marker kkossev.rgbLib, line 942
    if((device.currentState("saturation")?.value == null)) { // library marker kkossev.rgbLib, line 943
        sendEvent(name: "saturation", value: 0); // library marker kkossev.rgbLib, line 944
    } // library marker kkossev.rgbLib, line 945
    if((device.currentState("hue")?.value == null)) { // library marker kkossev.rgbLib, line 946
        sendEvent(name: "hue", value: 0); // library marker kkossev.rgbLib, line 947
    } // library marker kkossev.rgbLib, line 948
    if ((device.currentState("level")?.value == null) || (device.currentState("level")?.value == 0)) { // library marker kkossev.rgbLib, line 949
        sendEvent(name: "level", value: 100) // library marker kkossev.rgbLib, line 950
    }     // library marker kkossev.rgbLib, line 951
} // library marker kkossev.rgbLib, line 952
/* // library marker kkossev.rgbLib, line 953
================================================================================================ // library marker kkossev.rgbLib, line 954
Node Descriptor // library marker kkossev.rgbLib, line 955
================================================================================================ // library marker kkossev.rgbLib, line 956
▸ Logical Type                              = Zigbee Router // library marker kkossev.rgbLib, line 957
▸ Complex Descriptor Available              = No // library marker kkossev.rgbLib, line 958
▸ User Descriptor Available                 = No // library marker kkossev.rgbLib, line 959
▸ Frequency Band                            = 2400 - 2483.5 MHz // library marker kkossev.rgbLib, line 960
▸ Alternate PAN Coordinator                 = No // library marker kkossev.rgbLib, line 961
▸ Device Type                               = Full Function Device (FFD) // library marker kkossev.rgbLib, line 962
▸ Mains Power Source                        = Yes // library marker kkossev.rgbLib, line 963
▸ Receiver On When Idle                     = Yes (always on) // library marker kkossev.rgbLib, line 964
▸ Security Capability                       = No // library marker kkossev.rgbLib, line 965
▸ Allocate Address                          = Yes // library marker kkossev.rgbLib, line 966
▸ Manufacturer Code                         = 0x115F = XIAOMI // library marker kkossev.rgbLib, line 967
▸ Maximum Buffer Size                       = 82 bytes // library marker kkossev.rgbLib, line 968
▸ Maximum Incoming Transfer Size            = 82 bytes // library marker kkossev.rgbLib, line 969
▸ Primary Trust Center                      = No // library marker kkossev.rgbLib, line 970
▸ Backup Trust Center                       = No // library marker kkossev.rgbLib, line 971
▸ Primary Binding Table Cache               = Yes // library marker kkossev.rgbLib, line 972
▸ Backup Binding Table Cache                = No // library marker kkossev.rgbLib, line 973
▸ Primary Discovery Cache                   = Yes // library marker kkossev.rgbLib, line 974
▸ Backup Discovery Cache                    = Yes // library marker kkossev.rgbLib, line 975
▸ Network Manager                           = Yes // library marker kkossev.rgbLib, line 976
▸ Maximum Outgoing Transfer Size            = 82 bytes // library marker kkossev.rgbLib, line 977
▸ Extended Active Endpoint List Available   = No // library marker kkossev.rgbLib, line 978
▸ Extended Simple Descriptor List Available = No // library marker kkossev.rgbLib, line 979
================================================================================================ // library marker kkossev.rgbLib, line 980
Power Descriptor // library marker kkossev.rgbLib, line 981
================================================================================================ // library marker kkossev.rgbLib, line 982
▸ Current Power Mode         = Same as "Receiver On When Idle" from "Node Descriptor" section above // library marker kkossev.rgbLib, line 983
▸ Available Power Sources    = [Constant (mains) power] // library marker kkossev.rgbLib, line 984
▸ Current Power Sources      = [Constant (mains) power] // library marker kkossev.rgbLib, line 985
▸ Current Power Source Level = 100% // library marker kkossev.rgbLib, line 986
================================================================================================ // library marker kkossev.rgbLib, line 987
Endpoint 0x01 | Out Clusters: 0x000A (Time Cluster), 0x0019 (OTA Upgrade Cluster) // library marker kkossev.rgbLib, line 988
================================================================================================ // library marker kkossev.rgbLib, line 989
Endpoint 0x01 | In Cluster: 0x0000 (Basic Cluster) // library marker kkossev.rgbLib, line 990
================================================================================================ // library marker kkossev.rgbLib, line 991
▸ 0x0000 | ZCL Version          | req | r-- | uint8  | 03                | -- // library marker kkossev.rgbLib, line 992
▸ 0x0001 | Application Version  | opt | r-- | uint8  | 1B                | -- // library marker kkossev.rgbLib, line 993
▸ 0x0002 | Stack Version        | opt | r-- | uint8  | 1B                | -- // library marker kkossev.rgbLib, line 994
▸ 0x0003 | HW Version           | opt | r-- | uint8  | 01                | -- // library marker kkossev.rgbLib, line 995
▸ 0x0004 | Manufacturer Name    | opt | r-- | string | Aqara             | -- // library marker kkossev.rgbLib, line 996
▸ 0x0005 | Model Identifier     | opt | r-- | string | lumi.light.acn132 | -- // library marker kkossev.rgbLib, line 997
▸ 0x0006 | Date Code            | req | r-- | string | 20230606          | -- // library marker kkossev.rgbLib, line 998
▸ 0x0007 | Power Source         | opt | r-- | enum8  | 04 = DC source    | -- // library marker kkossev.rgbLib, line 999
▸ 0x000A | Product Code         | opt | r-- | octstr | --                | -- // library marker kkossev.rgbLib, line 1000
▸ 0x000D | Serial Number        | opt | r-- | string | --                | -- // library marker kkossev.rgbLib, line 1001
▸ 0x0010 | Location Description | opt | rw- | string | é»è®¤æ¿é´     | -- // library marker kkossev.rgbLib, line 1002
▸ 0xF000 | --                   | --  | r-- | uint16 | 0000              | -- // library marker kkossev.rgbLib, line 1003
▸ 0xFFFD | Cluster Revision     | req | r-- | uint16 | 0002              | -- // library marker kkossev.rgbLib, line 1004
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1005
▸ No commands found // library marker kkossev.rgbLib, line 1006
================================================================================================ // library marker kkossev.rgbLib, line 1007
Endpoint 0x01 | In Cluster: 0x0003 (Identify Cluster) // library marker kkossev.rgbLib, line 1008
================================================================================================ // library marker kkossev.rgbLib, line 1009
▸ 0x0000 | Identify Time    | req | rw- | uint16 | 0000 = 0 seconds | -- // library marker kkossev.rgbLib, line 1010
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0001             | -- // library marker kkossev.rgbLib, line 1011
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1012
▸ 0x00 | Identify       | req // library marker kkossev.rgbLib, line 1013
▸ 0x01 | Identify Query | req // library marker kkossev.rgbLib, line 1014
================================================================================================ // library marker kkossev.rgbLib, line 1015
Endpoint 0x01 | In Cluster: 0x0004 (Groups Cluster) // library marker kkossev.rgbLib, line 1016
================================================================================================ // library marker kkossev.rgbLib, line 1017
▸ 0x0000 | Name Support     | req | r-- | map8   | 00   | -- // library marker kkossev.rgbLib, line 1018
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0002 | -- // library marker kkossev.rgbLib, line 1019
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1020
▸ 0x00 | Add Group                | req // library marker kkossev.rgbLib, line 1021
▸ 0x01 | View Group               | req // library marker kkossev.rgbLib, line 1022
▸ 0x02 | Get Group Membership     | req // library marker kkossev.rgbLib, line 1023
▸ 0x03 | Remove Group             | req // library marker kkossev.rgbLib, line 1024
▸ 0x04 | Remove All Groups        | req // library marker kkossev.rgbLib, line 1025
▸ 0x05 | Add Group If Identifying | req // library marker kkossev.rgbLib, line 1026
================================================================================================ // library marker kkossev.rgbLib, line 1027
Endpoint 0x01 | In Cluster: 0x0005 (Scenes Cluster) // library marker kkossev.rgbLib, line 1028
================================================================================================ // library marker kkossev.rgbLib, line 1029
▸ 0x0000 | Scene Count      | req | r-- | uint8  | 00         | -- // library marker kkossev.rgbLib, line 1030
▸ 0x0001 | Current Scene    | req | r-- | uint8  | 00         | -- // library marker kkossev.rgbLib, line 1031
▸ 0x0002 | Current Group    | req | r-- | uint16 | 0000       | -- // library marker kkossev.rgbLib, line 1032
▸ 0x0003 | Scene Valid      | req | r-- | bool   | 00 = False | -- // library marker kkossev.rgbLib, line 1033
▸ 0x0004 | Name Support     | req | r-- | map8   | 00         | -- // library marker kkossev.rgbLib, line 1034
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0002       | -- // library marker kkossev.rgbLib, line 1035
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1036
▸ 0x00 | Add Scene            | req // library marker kkossev.rgbLib, line 1037
▸ 0x01 | View Scene           | req // library marker kkossev.rgbLib, line 1038
▸ 0x02 | Remove Scene         | req // library marker kkossev.rgbLib, line 1039
▸ 0x03 | Remove All Scenes    | req // library marker kkossev.rgbLib, line 1040
▸ 0x04 | Store Scene          | req // library marker kkossev.rgbLib, line 1041
▸ 0x05 | Recall Scene         | req // library marker kkossev.rgbLib, line 1042
▸ 0x06 | Get Scene Membership | req // library marker kkossev.rgbLib, line 1043
================================================================================================ // library marker kkossev.rgbLib, line 1044
Endpoint 0x01 | In Cluster: 0x0006 (On/Off Cluster) // library marker kkossev.rgbLib, line 1045
================================================================================================ // library marker kkossev.rgbLib, line 1046
▸ 0x0000 | On Off           | req | r-p | bool   | 01 = On  | 0..300 // library marker kkossev.rgbLib, line 1047
▸ 0x00F5 | --               | --  | r-- | uint32 | 00D8A053 | --     // library marker kkossev.rgbLib, line 1048
▸ 0xFFFD | Cluster Revision | req | r-- | uint16 | 0002     | --     // library marker kkossev.rgbLib, line 1049
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1050
▸ 0x00 | Off    | req // library marker kkossev.rgbLib, line 1051
▸ 0x01 | On     | req // library marker kkossev.rgbLib, line 1052
▸ 0x02 | Toggle | req // library marker kkossev.rgbLib, line 1053
================================================================================================ // library marker kkossev.rgbLib, line 1054
Endpoint 0x01 | In Cluster: 0x0008 (Level Control Cluster) // library marker kkossev.rgbLib, line 1055
================================================================================================ // library marker kkossev.rgbLib, line 1056
▸ 0x0000 | Current Level          | req | r-p | uint8  | 0C = 4%          | 1..3600 // library marker kkossev.rgbLib, line 1057
▸ 0x0001 | Remaining Time         | opt | r-- | uint16 | 0000 = 0 seconds | --      // library marker kkossev.rgbLib, line 1058
▸ 0x0002 | --                     | --  | r-- | uint8  | 01               | --      // library marker kkossev.rgbLib, line 1059
▸ 0x0003 | --                     | --  | r-- | uint8  | FE               | --      // library marker kkossev.rgbLib, line 1060
▸ 0x000F | --                     | --  | rw- | map8   | 00               | --      // library marker kkossev.rgbLib, line 1061
▸ 0x0010 | On Off Transition Time | opt | rw- | uint16 | 000F = 1 seconds | --      // library marker kkossev.rgbLib, line 1062
▸ 0x0011 | On Level               | opt | rw- | uint8  | 0C = 4%          | --      // library marker kkossev.rgbLib, line 1063
▸ 0x0012 | On Transition Time     | opt | rw- | uint16 | 000F = 1 seconds | --      // library marker kkossev.rgbLib, line 1064
▸ 0x0013 | Off Transition Time    | opt | rw- | uint16 | 000F = 1 seconds | --      // library marker kkossev.rgbLib, line 1065
▸ 0x00F5 | --                     | --  | r-- | uint32 | 00D8A074         | --      // library marker kkossev.rgbLib, line 1066
▸ 0xFFFD | Cluster Revision       | req | r-- | uint16 | 0002             | --      // library marker kkossev.rgbLib, line 1067
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1068
▸ 0x00 | Move To Level             | req // library marker kkossev.rgbLib, line 1069
▸ 0x01 | Move                      | req // library marker kkossev.rgbLib, line 1070
▸ 0x02 | Step                      | req // library marker kkossev.rgbLib, line 1071
▸ 0x03 | Stop                      | req // library marker kkossev.rgbLib, line 1072
▸ 0x04 | Move To Level With On/Off | req // library marker kkossev.rgbLib, line 1073
▸ 0x05 | Move With On/Off          | req // library marker kkossev.rgbLib, line 1074
▸ 0x06 | Step With On/Off          | req // library marker kkossev.rgbLib, line 1075
▸ 0x07 | Stop                      | req // library marker kkossev.rgbLib, line 1076
================================================================================================ // library marker kkossev.rgbLib, line 1077
Endpoint 0x01 | In Cluster: 0x0300 (Color Control Cluster) // library marker kkossev.rgbLib, line 1078
================================================================================================ // library marker kkossev.rgbLib, line 1079
▸ 0x0002 | Remaining Time                   | opt | r-- | uint16 | 0000     | --     // library marker kkossev.rgbLib, line 1080
▸ 0x0003 | CurrentX                         | req | r-p | uint16 | 4A3C     | 0..300 // library marker kkossev.rgbLib, line 1081
▸ 0x0004 | CurrentY                         | req | r-p | uint16 | 8FEB     | 0..300 // library marker kkossev.rgbLib, line 1082
▸ 0x0007 | Color Temperature Mireds         | req | r-p | uint16 | 0099     | --     // library marker kkossev.rgbLib, line 1083
▸ 0x0008 | Color Mode                       | req | r-- | enum8  | 01       | --     // library marker kkossev.rgbLib, line 1084
▸ 0x000F | --                               | --  | rw- | map8   | 00       | --     // library marker kkossev.rgbLib, line 1085
▸ 0x0010 | Number Of Primaries              | req | r-- | uint8  | 00       | --     // library marker kkossev.rgbLib, line 1086
▸ 0x00F5 | --                               | --  | r-- | uint32 | 00D8A06A | --     // library marker kkossev.rgbLib, line 1087
▸ 0x4001 | Enhanced Color Mode              | req | r-- | enum8  | 01       | --     // library marker kkossev.rgbLib, line 1088
▸ 0x400A | Color Capabilities               | req | r-- | map16  | 0018     | --     // library marker kkossev.rgbLib, line 1089
▸ 0x400B | Color Temp Physical Min Mireds   | req | r-- | uint16 | 0099     | --     // library marker kkossev.rgbLib, line 1090
▸ 0x400C | Color Temp Physical Max Mireds   | req | r-- | uint16 | 0172     | --     // library marker kkossev.rgbLib, line 1091
▸ 0x400D | --                               | --  | r-- | uint16 | 0099     | --     // library marker kkossev.rgbLib, line 1092
▸ 0x4010 | StartUp Color Temperature Mireds | opt | rw- | uint16 | 00FA     | --     // library marker kkossev.rgbLib, line 1093
▸ 0xFFFD | Cluster Revision                 | req | r-- | uint16 | 0002     | --     // library marker kkossev.rgbLib, line 1094
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1095
▸ 0x07 | Move to Color             | req // library marker kkossev.rgbLib, line 1096
▸ 0x08 | Move Color                | req // library marker kkossev.rgbLib, line 1097
▸ 0x09 | Step Color                | req // library marker kkossev.rgbLib, line 1098
▸ 0x0A | Move to Color Temperature | req // library marker kkossev.rgbLib, line 1099
▸ 0x47 | Stop Move Step            | req // library marker kkossev.rgbLib, line 1100
▸ 0x4B | Move Color Temperature    | req // library marker kkossev.rgbLib, line 1101
▸ 0x4C | Step Color Temperature    | req // library marker kkossev.rgbLib, line 1102
================================================================================================ // library marker kkossev.rgbLib, line 1103
Endpoint 0x01 | In Cluster: 0xFCC0 (Unknown Cluster) // library marker kkossev.rgbLib, line 1104
================================================================================================ // library marker kkossev.rgbLib, line 1105
▸ No attributes found // library marker kkossev.rgbLib, line 1106
------------------------------------------------------------------------------------------------ // library marker kkossev.rgbLib, line 1107
▸ No commands found // library marker kkossev.rgbLib, line 1108
================================================================================================ // library marker kkossev.rgbLib, line 1109

*/ // library marker kkossev.rgbLib, line 1111

def testT(par) { // library marker kkossev.rgbLib, line 1113
    logWarn "testT(${par})" // library marker kkossev.rgbLib, line 1114
} // library marker kkossev.rgbLib, line 1115


// ~~~~~ end include (143) kkossev.rgbLib ~~~~~
