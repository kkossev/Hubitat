/**
 * Tuya-Zigbee-Device-Driver for Hubitat
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
 * ver. 2.0.0  2023-05-08 kkossev  - Initial test version
 * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) commonLib 3.0.6
 * ver. 3.2.0  2024-05-28 kkossev  - (dev. branch) commonLib 3.2.0
 *
 *                                   TODO:
 */

static String version() { "3.2.0" }
static String timeStamp() {"2024/05/28 1:34 PM"}

@Field static final Boolean _DEBUG = false














 
// can not get property 'UNKNOWN' on null object in librabry rgrbLib

deviceType = "Device"
@Field static final String DEVICE_TYPE = "Device"

metadata {
    definition (
        name: 'Tuya Zigbee Device',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Device%20Driver/Tuya%20Zigbee%20Device.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        capability 'Sensor'
        capability 'Actuator'
        capability 'MotionSensor'
        capability 'Battery'
        capability 'Switch'
        capability 'Momentary'
        capability 'TemperatureMeasurement'
        capability 'RelativeHumidityMeasurement'

        attribute 'batteryVoltage', 'number'
                    
        if (_DEBUG) {
            command 'getAllProperties',       [[name: 'Get All Properties']]
        }
    }
    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
    }
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', documentationLink: '', // library marker kkossev.commonLib, line 4
    version: '3.2.0' // library marker kkossev.commonLib, line 5
) // library marker kkossev.commonLib, line 6
/* // library marker kkossev.commonLib, line 7
  *  Common ZCL Library // library marker kkossev.commonLib, line 8
  * // library marker kkossev.commonLib, line 9
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 10
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 11
  * // library marker kkossev.commonLib, line 12
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 13
  * // library marker kkossev.commonLib, line 14
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 15
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 16
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 19
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 20
  * // library marker kkossev.commonLib, line 21
  * // library marker kkossev.commonLib, line 22
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 23
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 24
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 25
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 26
  * ver. 3.0.1  2023-12-06 kkossev  - info event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 27
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0 // library marker kkossev.commonLib, line 28
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 29
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 30
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 31
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 32
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices; // library marker kkossev.commonLib, line 33
  * ver. 3.1.0  2024-04-28 kkossev  - unnecesery unschedule() speed optimization; added syncTuyaDateTime(); tuyaBlackMagic() initialization bug fix. // library marker kkossev.commonLib, line 34
  * ver. 3.1.1  2024-05-05 kkossev  - getTuyaAttributeValue bug fix; added customCustomParseIlluminanceCluster method // library marker kkossev.commonLib, line 35
  * ver. 3.2.0  2024-05-23 kkossev  - standardParse____Cluster and customParse___Cluster methods; moved onOff methods to a new library; rename all custom handlers in the libs to statdndardParseXXX // library marker kkossev.commonLib, line 36
  * ver. 3.2.1  2024-05-27 kkossev  - (dev. branch) 4 in 1 V3 compatibility; added IAS cluster; // library marker kkossev.commonLib, line 37
  * // library marker kkossev.commonLib, line 38
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 40
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 41
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 42
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 43
  * // library marker kkossev.commonLib, line 44
*/ // library marker kkossev.commonLib, line 45

String commonLibVersion() { '3.2.1' } // library marker kkossev.commonLib, line 47
String commonLibStamp() { '2024/05/27 10:13 PM' } // library marker kkossev.commonLib, line 48

import groovy.transform.Field // library marker kkossev.commonLib, line 50
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 51
import hubitat.device.Protocol // library marker kkossev.commonLib, line 52
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 53
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 54
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 55
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 56
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 57
import java.math.BigDecimal // library marker kkossev.commonLib, line 58

metadata { // library marker kkossev.commonLib, line 60
        if (_DEBUG) { // library marker kkossev.commonLib, line 61
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 62
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 63
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 64
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 65
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 66
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 67
            ] // library marker kkossev.commonLib, line 68
        } // library marker kkossev.commonLib, line 69

        // common capabilities for all device types // library marker kkossev.commonLib, line 71
        capability 'Configuration' // library marker kkossev.commonLib, line 72
        capability 'Refresh' // library marker kkossev.commonLib, line 73
        capability 'Health Check' // library marker kkossev.commonLib, line 74

        // common attributes for all device types // library marker kkossev.commonLib, line 76
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 77
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 78
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 79

        // common commands for all device types // library marker kkossev.commonLib, line 81
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 82

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 84
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 85

    preferences { // library marker kkossev.commonLib, line 87
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 88
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 89
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 90

        if (device) { // library marker kkossev.commonLib, line 92
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 93
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 94
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 95
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 96
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 97
            } // library marker kkossev.commonLib, line 98
        } // library marker kkossev.commonLib, line 99
    } // library marker kkossev.commonLib, line 100
} // library marker kkossev.commonLib, line 101

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 103
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 104
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 105
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 106
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 107
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 108
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 109
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 110
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 111
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 112
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 113

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 115
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 116
] // library marker kkossev.commonLib, line 117
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 118
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 119
] // library marker kkossev.commonLib, line 120

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 122
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 123
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 124
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 125
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 126
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 127
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 128
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 129
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 130
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 131
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 135
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 136
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 137
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 138
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 139
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 140

/** // library marker kkossev.commonLib, line 142
 * Parse Zigbee message // library marker kkossev.commonLib, line 143
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 144
 */ // library marker kkossev.commonLib, line 145
void parse(final String description) { // library marker kkossev.commonLib, line 146
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 147
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 148
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 149
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 150

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 152
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 153
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 154
            parseIasMessage(description) // library marker kkossev.commonLib, line 155
        } // library marker kkossev.commonLib, line 156
        else { // library marker kkossev.commonLib, line 157
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 158
        } // library marker kkossev.commonLib, line 159
        return // library marker kkossev.commonLib, line 160
    } // library marker kkossev.commonLib, line 161
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 162
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 163
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 164
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 165
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 166
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 167
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 168
        return // library marker kkossev.commonLib, line 169
    } // library marker kkossev.commonLib, line 170

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 172
        return // library marker kkossev.commonLib, line 173
    } // library marker kkossev.commonLib, line 174
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 175

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 177
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 178

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 180
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 181
        return // library marker kkossev.commonLib, line 182
    } // library marker kkossev.commonLib, line 183
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 184
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 185
        return // library marker kkossev.commonLib, line 186
    } // library marker kkossev.commonLib, line 187
    // // library marker kkossev.commonLib, line 188
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 189
    // // library marker kkossev.commonLib, line 190
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 191
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 192
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 193
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 194
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 195
            } // library marker kkossev.commonLib, line 196
            break // library marker kkossev.commonLib, line 197
        default: // library marker kkossev.commonLib, line 198
            if (settings.logEnable) { // library marker kkossev.commonLib, line 199
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 200
            } // library marker kkossev.commonLib, line 201
            break // library marker kkossev.commonLib, line 202
    } // library marker kkossev.commonLib, line 203
} // library marker kkossev.commonLib, line 204

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 206
    0x0000: 'Basic',                0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x000C: 'AnalogInput', // library marker kkossev.commonLib, line 207
    0x0006: 'OnOff',                0x0008: 'LevelControl',     0x0012: 'MultistateInput',  0x0102: 'WindowCovering',   0x0201: 'Thermostat',   0x0300: 'ColorControl', // library marker kkossev.commonLib, line 208
    0x0400: 'Illuminance',          0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'ElectricalMeasure', // library marker kkossev.commonLib, line 209
    0x0B04: 'Metering',             0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',             0xFC11: 'FC11',         0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 210
    0xFCC0: 'XiaomiFCC0', // library marker kkossev.commonLib, line 211
] // library marker kkossev.commonLib, line 212

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 214
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 215
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 216
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 217
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 218
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 219
        return false // library marker kkossev.commonLib, line 220
    } // library marker kkossev.commonLib, line 221
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 222
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 223
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 224
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 225
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 226
        return true // library marker kkossev.commonLib, line 227
    } // library marker kkossev.commonLib, line 228
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 229
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 230
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 231
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 232
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 233
        return true // library marker kkossev.commonLib, line 234
    } // library marker kkossev.commonLib, line 235
    if (device?.getDataValue('model') != 'ZigUSB') {    // patch! // library marker kkossev.commonLib, line 236
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 237
    } // library marker kkossev.commonLib, line 238
    return false // library marker kkossev.commonLib, line 239
} // library marker kkossev.commonLib, line 240

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 242
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 243
} // library marker kkossev.commonLib, line 244

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 246
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 247
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 248
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 249
    } // library marker kkossev.commonLib, line 250
    return false // library marker kkossev.commonLib, line 251
} // library marker kkossev.commonLib, line 252

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 254
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 255
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 256
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 257
    } // library marker kkossev.commonLib, line 258
    return false // library marker kkossev.commonLib, line 259
} // library marker kkossev.commonLib, line 260

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 262
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 263
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 264
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 265
    } // library marker kkossev.commonLib, line 266
    return false // library marker kkossev.commonLib, line 267
} // library marker kkossev.commonLib, line 268

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 270
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 271
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 272
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 273
] // library marker kkossev.commonLib, line 274

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 276
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 277
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 278
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 279
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 280
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 281
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 282
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 283
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 284
    List<String> cmds = [] // library marker kkossev.commonLib, line 285
    switch (clusterId) { // library marker kkossev.commonLib, line 286
        case 0x0005 : // library marker kkossev.commonLib, line 287
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 288
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 289
            // send the active endpoint response // library marker kkossev.commonLib, line 290
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 291
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case 0x0006 : // library marker kkossev.commonLib, line 294
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 295
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 296
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 297
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 300
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 301
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 302
            break // library marker kkossev.commonLib, line 303
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 304
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 305
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 306
            break // library marker kkossev.commonLib, line 307
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 308
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 309
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 310
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 311
            break // library marker kkossev.commonLib, line 312
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 313
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 316
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 317
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 318
            break // library marker kkossev.commonLib, line 319
        default : // library marker kkossev.commonLib, line 320
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 321
            break // library marker kkossev.commonLib, line 322
    } // library marker kkossev.commonLib, line 323
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 324
} // library marker kkossev.commonLib, line 325

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 327
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 328
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 329
    switch (commandId) { // library marker kkossev.commonLib, line 330
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 331
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 332
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 333
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 334
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 335
        default: // library marker kkossev.commonLib, line 336
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 337
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 338
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 339
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 340
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 341
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 342
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 343
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 344
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 345
            } // library marker kkossev.commonLib, line 346
            break // library marker kkossev.commonLib, line 347
    } // library marker kkossev.commonLib, line 348
} // library marker kkossev.commonLib, line 349

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 351
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 352
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 353
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 354
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 355
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 356
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 357
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 358
    } // library marker kkossev.commonLib, line 359
    else { // library marker kkossev.commonLib, line 360
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 361
    } // library marker kkossev.commonLib, line 362
} // library marker kkossev.commonLib, line 363

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 365
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 366
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 367
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 368
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 369
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 370
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 371
    } // library marker kkossev.commonLib, line 372
    else { // library marker kkossev.commonLib, line 373
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 374
    } // library marker kkossev.commonLib, line 375
} // library marker kkossev.commonLib, line 376

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 378
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 379
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 380
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 381
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 382
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 383
        state.reportingEnabled = true // library marker kkossev.commonLib, line 384
    } // library marker kkossev.commonLib, line 385
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 386
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 387
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 388
    } else { // library marker kkossev.commonLib, line 389
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 390
    } // library marker kkossev.commonLib, line 391
} // library marker kkossev.commonLib, line 392

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 394
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 395
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 396
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 397
    if (status == 0) { // library marker kkossev.commonLib, line 398
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 399
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 400
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 401
        int delta = 0 // library marker kkossev.commonLib, line 402
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 403
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 404
        } // library marker kkossev.commonLib, line 405
        else { // library marker kkossev.commonLib, line 406
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 407
        } // library marker kkossev.commonLib, line 408
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 409
    } // library marker kkossev.commonLib, line 410
    else { // library marker kkossev.commonLib, line 411
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 412
    } // library marker kkossev.commonLib, line 413
} // library marker kkossev.commonLib, line 414

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 416
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 417
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 418
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 419
        return false // library marker kkossev.commonLib, line 420
    } // library marker kkossev.commonLib, line 421
    // execute the customHandler function // library marker kkossev.commonLib, line 422
    boolean result = false // library marker kkossev.commonLib, line 423
    try { // library marker kkossev.commonLib, line 424
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 425
    } // library marker kkossev.commonLib, line 426
    catch (e) { // library marker kkossev.commonLib, line 427
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 428
        return false // library marker kkossev.commonLib, line 429
    } // library marker kkossev.commonLib, line 430
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 431
    return result // library marker kkossev.commonLib, line 432
} // library marker kkossev.commonLib, line 433

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 435
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 436
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 437
    final String commandId = data[0] // library marker kkossev.commonLib, line 438
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 439
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 440
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 441
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 442
    } else { // library marker kkossev.commonLib, line 443
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 444
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 445
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 446
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 447
        } // library marker kkossev.commonLib, line 448
    } // library marker kkossev.commonLib, line 449
} // library marker kkossev.commonLib, line 450

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 452
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 453
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 454
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 455

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 457
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 458
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 459
] // library marker kkossev.commonLib, line 460

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 462
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 463
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 464
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 465
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 466
] // library marker kkossev.commonLib, line 467

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 469
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 470
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 471
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 472
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 473
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 474
    return avg // library marker kkossev.commonLib, line 475
} // library marker kkossev.commonLib, line 476

/* // library marker kkossev.commonLib, line 478
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 479
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 480
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 481
*/ // library marker kkossev.commonLib, line 482
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 483

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 485
void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 486
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 487
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 488
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 489
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 490
        case 0x0000: // library marker kkossev.commonLib, line 491
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 492
            break // library marker kkossev.commonLib, line 493
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 494
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 495
            if (isPing) { // library marker kkossev.commonLib, line 496
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 497
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 498
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 499
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 500
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 501
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 502
                    sendRttEvent() // library marker kkossev.commonLib, line 503
                } // library marker kkossev.commonLib, line 504
                else { // library marker kkossev.commonLib, line 505
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 506
                } // library marker kkossev.commonLib, line 507
                state.states['isPing'] = false // library marker kkossev.commonLib, line 508
            } // library marker kkossev.commonLib, line 509
            else { // library marker kkossev.commonLib, line 510
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 511
            } // library marker kkossev.commonLib, line 512
            break // library marker kkossev.commonLib, line 513
        case 0x0004: // library marker kkossev.commonLib, line 514
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 515
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 516
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 517
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 518
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 519
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 520
            } // library marker kkossev.commonLib, line 521
            break // library marker kkossev.commonLib, line 522
        case 0x0005: // library marker kkossev.commonLib, line 523
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 524
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 525
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 526
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 527
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 528
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 529
            } // library marker kkossev.commonLib, line 530
            break // library marker kkossev.commonLib, line 531
        case 0x0007: // library marker kkossev.commonLib, line 532
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 533
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 534
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 535
            break // library marker kkossev.commonLib, line 536
        case 0xFFDF: // library marker kkossev.commonLib, line 537
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 538
            break // library marker kkossev.commonLib, line 539
        case 0xFFE2: // library marker kkossev.commonLib, line 540
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 541
            break // library marker kkossev.commonLib, line 542
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 543
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 544
            break // library marker kkossev.commonLib, line 545
        case 0xFFFE: // library marker kkossev.commonLib, line 546
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 547
            break // library marker kkossev.commonLib, line 548
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 549
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 550
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 551
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 552
            break // library marker kkossev.commonLib, line 553
        default: // library marker kkossev.commonLib, line 554
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 555
            break // library marker kkossev.commonLib, line 556
    } // library marker kkossev.commonLib, line 557
} // library marker kkossev.commonLib, line 558

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 560
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 561
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 562

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 564
    Map descMap = [:] // library marker kkossev.commonLib, line 565
    try { // library marker kkossev.commonLib, line 566
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 567
    } // library marker kkossev.commonLib, line 568
    catch (e1) { // library marker kkossev.commonLib, line 569
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 570
        // try alternative custom parsing // library marker kkossev.commonLib, line 571
        descMap = [:] // library marker kkossev.commonLib, line 572
        try { // library marker kkossev.commonLib, line 573
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 574
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 575
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 576
            } // library marker kkossev.commonLib, line 577
        } // library marker kkossev.commonLib, line 578
        catch (e2) { // library marker kkossev.commonLib, line 579
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 580
            return [:] // library marker kkossev.commonLib, line 581
        } // library marker kkossev.commonLib, line 582
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 583
    } // library marker kkossev.commonLib, line 584
    return descMap // library marker kkossev.commonLib, line 585
} // library marker kkossev.commonLib, line 586

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 588
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 589
        return false // library marker kkossev.commonLib, line 590
    } // library marker kkossev.commonLib, line 591
    // try to parse ... // library marker kkossev.commonLib, line 592
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 593
    Map descMap = [:] // library marker kkossev.commonLib, line 594
    try { // library marker kkossev.commonLib, line 595
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 596
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 597
    } // library marker kkossev.commonLib, line 598
    catch (e) { // library marker kkossev.commonLib, line 599
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 600
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 601
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 602
        return true // library marker kkossev.commonLib, line 603
    } // library marker kkossev.commonLib, line 604

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 606
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 607
    } // library marker kkossev.commonLib, line 608
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 609
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 610
    } // library marker kkossev.commonLib, line 611
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 612
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 613
    } // library marker kkossev.commonLib, line 614
    else { // library marker kkossev.commonLib, line 615
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 616
        return false // library marker kkossev.commonLib, line 617
    } // library marker kkossev.commonLib, line 618
    return true    // processed // library marker kkossev.commonLib, line 619
} // library marker kkossev.commonLib, line 620

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 622
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 623
  /* // library marker kkossev.commonLib, line 624
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 625
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 626
        return true // library marker kkossev.commonLib, line 627
    } // library marker kkossev.commonLib, line 628
*/ // library marker kkossev.commonLib, line 629
    Map descMap = [:] // library marker kkossev.commonLib, line 630
    try { // library marker kkossev.commonLib, line 631
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 632
    } // library marker kkossev.commonLib, line 633
    catch (e1) { // library marker kkossev.commonLib, line 634
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 635
        // try alternative custom parsing // library marker kkossev.commonLib, line 636
        descMap = [:] // library marker kkossev.commonLib, line 637
        try { // library marker kkossev.commonLib, line 638
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 639
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 640
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 641
            } // library marker kkossev.commonLib, line 642
        } // library marker kkossev.commonLib, line 643
        catch (e2) { // library marker kkossev.commonLib, line 644
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 645
            return true // library marker kkossev.commonLib, line 646
        } // library marker kkossev.commonLib, line 647
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 648
    } // library marker kkossev.commonLib, line 649
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 650
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 651
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 652
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 653
        return false // library marker kkossev.commonLib, line 654
    } // library marker kkossev.commonLib, line 655
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 656
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 657
    // attribute report received // library marker kkossev.commonLib, line 658
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 659
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 660
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 661
    } // library marker kkossev.commonLib, line 662
    attrData.each { // library marker kkossev.commonLib, line 663
        if (it.status == '86') { // library marker kkossev.commonLib, line 664
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 665
        // TODO - skip parsing? // library marker kkossev.commonLib, line 666
        } // library marker kkossev.commonLib, line 667
        switch (it.cluster) { // library marker kkossev.commonLib, line 668
            case '0000' : // library marker kkossev.commonLib, line 669
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 670
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 671
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 672
                } // library marker kkossev.commonLib, line 673
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 674
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 675
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 676
                } // library marker kkossev.commonLib, line 677
                else { // library marker kkossev.commonLib, line 678
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 679
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 680
                } // library marker kkossev.commonLib, line 681
                break // library marker kkossev.commonLib, line 682
            default : // library marker kkossev.commonLib, line 683
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 684
                break // library marker kkossev.commonLib, line 685
        } // switch // library marker kkossev.commonLib, line 686
    } // for each attribute // library marker kkossev.commonLib, line 687
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 688
} // library marker kkossev.commonLib, line 689


String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 692
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 693
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 694
} // library marker kkossev.commonLib, line 695

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 697
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 698
} // library marker kkossev.commonLib, line 699

/* // library marker kkossev.commonLib, line 701
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 702
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 703
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 704
*/ // library marker kkossev.commonLib, line 705
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 706
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 707
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 708

// Tuya Commands // library marker kkossev.commonLib, line 710
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 711
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 712
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 713
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 714
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 715

// tuya DP type // library marker kkossev.commonLib, line 717
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 718
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 719
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 720
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 721
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 722
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 723

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 725
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 726
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 727
    long offset = 0 // library marker kkossev.commonLib, line 728
    int offsetHours = 0 // library marker kkossev.commonLib, line 729
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 730
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 731
    try { // library marker kkossev.commonLib, line 732
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 733
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 734
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 735
    } catch (e) { // library marker kkossev.commonLib, line 736
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 737
    } // library marker kkossev.commonLib, line 738
    // // library marker kkossev.commonLib, line 739
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 740
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 741
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 742
} // library marker kkossev.commonLib, line 743

// called from the main parse method when the cluster is 0xEF00 // library marker kkossev.commonLib, line 745
void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 746
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 747
        syncTuyaDateTime() // library marker kkossev.commonLib, line 748
    } // library marker kkossev.commonLib, line 749
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 750
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 751
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 752
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 753
        if (status != '00') { // library marker kkossev.commonLib, line 754
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 755
        } // library marker kkossev.commonLib, line 756
    } // library marker kkossev.commonLib, line 757
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 758
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 759
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 760
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 761
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 762
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 763
            return // library marker kkossev.commonLib, line 764
        } // library marker kkossev.commonLib, line 765
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 766
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 767
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 768
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 769
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 770
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 771
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 772
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 773
            } // library marker kkossev.commonLib, line 774
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 775
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 776
        } // library marker kkossev.commonLib, line 777
    } // library marker kkossev.commonLib, line 778
    else { // library marker kkossev.commonLib, line 779
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 780
    } // library marker kkossev.commonLib, line 781
} // library marker kkossev.commonLib, line 782

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 784
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 785
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 786
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 787
        logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 788
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 789
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 790
        } // library marker kkossev.commonLib, line 791
    } // library marker kkossev.commonLib, line 792
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 793
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) {   // library marker kkossev.commonLib, line 794
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 795
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 796
        } // library marker kkossev.commonLib, line 797
    } // library marker kkossev.commonLib, line 798
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 799
} // library marker kkossev.commonLib, line 800

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 802
    int retValue = 0 // library marker kkossev.commonLib, line 803
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 804
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 805
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 806
        int power = 1 // library marker kkossev.commonLib, line 807
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 808
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 809
            power = power * 256 // library marker kkossev.commonLib, line 810
        } // library marker kkossev.commonLib, line 811
    } // library marker kkossev.commonLib, line 812
    return retValue // library marker kkossev.commonLib, line 813
} // library marker kkossev.commonLib, line 814

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 816

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 818
    List<String> cmds = [] // library marker kkossev.commonLib, line 819
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 820
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 821
    //int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 822
    int tuyaCmd = isFingerbot() ? 0x04 : tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 823
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 824
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 825
    return cmds // library marker kkossev.commonLib, line 826
} // library marker kkossev.commonLib, line 827

private getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 829

void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 831
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 832
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 833
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 834
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 835
} // library marker kkossev.commonLib, line 836

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 838
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 839

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 841
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 842
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 843
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 844
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 845
} // library marker kkossev.commonLib, line 846

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 848
    List<String> cmds = [] // library marker kkossev.commonLib, line 849
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 850
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 851
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 852
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 853
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 854
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 855
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 856
        } // library marker kkossev.commonLib, line 857
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 858
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 859
    } // library marker kkossev.commonLib, line 860
    else { // library marker kkossev.commonLib, line 861
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 862
    } // library marker kkossev.commonLib, line 863
} // library marker kkossev.commonLib, line 864

// Invoked from configure() // library marker kkossev.commonLib, line 866
List<String> initializeDevice() { // library marker kkossev.commonLib, line 867
    List<String> cmds = [] // library marker kkossev.commonLib, line 868
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 869
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 870
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 871
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 872
    } // library marker kkossev.commonLib, line 873
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 874
    return cmds // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876

// Invoked from configure() // library marker kkossev.commonLib, line 878
List<String> configureDevice() { // library marker kkossev.commonLib, line 879
    List<String> cmds = [] // library marker kkossev.commonLib, line 880
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 881
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 882
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 883
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 884
    } // library marker kkossev.commonLib, line 885
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 886
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 887
    return cmds // library marker kkossev.commonLib, line 888
} // library marker kkossev.commonLib, line 889

/* // library marker kkossev.commonLib, line 891
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 892
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 893
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 894
*/ // library marker kkossev.commonLib, line 895

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 897
    List<String> cmds = [] // library marker kkossev.commonLib, line 898
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 899
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 900
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 901
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 902
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 903
            } // library marker kkossev.commonLib, line 904
        } // library marker kkossev.commonLib, line 905
    } // library marker kkossev.commonLib, line 906
    return cmds // library marker kkossev.commonLib, line 907
} // library marker kkossev.commonLib, line 908

void refresh() { // library marker kkossev.commonLib, line 910
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 911
    checkDriverVersion(state) // library marker kkossev.commonLib, line 912
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 913
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 914
        customCmds = customRefresh() // library marker kkossev.commonLib, line 915
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 916
    } // library marker kkossev.commonLib, line 917
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 918
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 919
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 920
    } // library marker kkossev.commonLib, line 921
    /* // library marker kkossev.commonLib, line 922
    if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 923
        cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 924
        cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 925
    } // library marker kkossev.commonLib, line 926
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 927
        cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 928
        cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 929
    } // library marker kkossev.commonLib, line 930
    */ // library marker kkossev.commonLib, line 931
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 932
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 933
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 934
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 935
    } // library marker kkossev.commonLib, line 936
    else { // library marker kkossev.commonLib, line 937
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 938
    } // library marker kkossev.commonLib, line 939
} // library marker kkossev.commonLib, line 940

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 942
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 943
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 944

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 946
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 947
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 948
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 949
    } // library marker kkossev.commonLib, line 950
    else { // library marker kkossev.commonLib, line 951
        logInfo "${info}" // library marker kkossev.commonLib, line 952
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 953
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 954
    } // library marker kkossev.commonLib, line 955
} // library marker kkossev.commonLib, line 956

public void ping() { // library marker kkossev.commonLib, line 958
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 959
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 960
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 961
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 962
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 963
    logDebug 'ping...' // library marker kkossev.commonLib, line 964
} // library marker kkossev.commonLib, line 965

def virtualPong() { // library marker kkossev.commonLib, line 967
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 968
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 969
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 970
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 971
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 972
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 973
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 974
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 975
        sendRttEvent() // library marker kkossev.commonLib, line 976
    } // library marker kkossev.commonLib, line 977
    else { // library marker kkossev.commonLib, line 978
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 979
    } // library marker kkossev.commonLib, line 980
    state.states['isPing'] = false // library marker kkossev.commonLib, line 981
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 982
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 983
} // library marker kkossev.commonLib, line 984

void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 986
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 987
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 988
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 989
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 990
    if (value == null) { // library marker kkossev.commonLib, line 991
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 992
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 993
    } // library marker kkossev.commonLib, line 994
    else { // library marker kkossev.commonLib, line 995
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 996
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 997
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 998
    } // library marker kkossev.commonLib, line 999
} // library marker kkossev.commonLib, line 1000

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1002
    if (cluster != null) { // library marker kkossev.commonLib, line 1003
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1004
    } // library marker kkossev.commonLib, line 1005
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1006
    return 'NULL' // library marker kkossev.commonLib, line 1007
} // library marker kkossev.commonLib, line 1008

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1010
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1011
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1012
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1013
} // library marker kkossev.commonLib, line 1014

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1016
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1017
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1018
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1019
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1020
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1021
    } // library marker kkossev.commonLib, line 1022
} // library marker kkossev.commonLib, line 1023

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1025
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1026
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1027
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1028
} // library marker kkossev.commonLib, line 1029

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1031
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1032
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1033
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1034
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1035
    } // library marker kkossev.commonLib, line 1036
    else { // library marker kkossev.commonLib, line 1037
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1038
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1039
    } // library marker kkossev.commonLib, line 1040
} // library marker kkossev.commonLib, line 1041

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1043
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1044
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1045
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1046
} // library marker kkossev.commonLib, line 1047

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1049
void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1050
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1051
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1052
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1053
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1054
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1055
    } // library marker kkossev.commonLib, line 1056
} // library marker kkossev.commonLib, line 1057

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1059
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1060
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1061
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1062
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1063
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1064
            logWarn 'not present!' // library marker kkossev.commonLib, line 1065
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1066
        } // library marker kkossev.commonLib, line 1067
    } // library marker kkossev.commonLib, line 1068
    else { // library marker kkossev.commonLib, line 1069
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1070
    } // library marker kkossev.commonLib, line 1071
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1072
} // library marker kkossev.commonLib, line 1073

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1075
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1076
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1077
    if (value == 'online') { // library marker kkossev.commonLib, line 1078
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1079
    } // library marker kkossev.commonLib, line 1080
    else { // library marker kkossev.commonLib, line 1081
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1082
    } // library marker kkossev.commonLib, line 1083
} // library marker kkossev.commonLib, line 1084

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1086
void updated() { // library marker kkossev.commonLib, line 1087
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1088
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1089
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1090
    unschedule() // library marker kkossev.commonLib, line 1091

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1093
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1094
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1095
    } // library marker kkossev.commonLib, line 1096
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1097
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1098
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1099
    } // library marker kkossev.commonLib, line 1100

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1102
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1103
        // schedule the periodic timer // library marker kkossev.commonLib, line 1104
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1105
        if (interval > 0) { // library marker kkossev.commonLib, line 1106
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1107
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1108
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1109
        } // library marker kkossev.commonLib, line 1110
    } // library marker kkossev.commonLib, line 1111
    else { // library marker kkossev.commonLib, line 1112
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1113
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1114
    } // library marker kkossev.commonLib, line 1115
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1116
        customUpdated() // library marker kkossev.commonLib, line 1117
    } // library marker kkossev.commonLib, line 1118

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1120
} // library marker kkossev.commonLib, line 1121

void logsOff() { // library marker kkossev.commonLib, line 1123
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1124
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1125
} // library marker kkossev.commonLib, line 1126
void traceOff() { // library marker kkossev.commonLib, line 1127
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1128
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1129
} // library marker kkossev.commonLib, line 1130

void configure(String command) { // library marker kkossev.commonLib, line 1132
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1133
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1134
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1135
        return // library marker kkossev.commonLib, line 1136
    } // library marker kkossev.commonLib, line 1137
    // // library marker kkossev.commonLib, line 1138
    String func // library marker kkossev.commonLib, line 1139
    try { // library marker kkossev.commonLib, line 1140
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1141
        "$func"() // library marker kkossev.commonLib, line 1142
    } // library marker kkossev.commonLib, line 1143
    catch (e) { // library marker kkossev.commonLib, line 1144
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1145
        return // library marker kkossev.commonLib, line 1146
    } // library marker kkossev.commonLib, line 1147
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1148
} // library marker kkossev.commonLib, line 1149

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1151
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1152
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1153
} // library marker kkossev.commonLib, line 1154

void loadAllDefaults() { // library marker kkossev.commonLib, line 1156
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1157
    deleteAllSettings() // library marker kkossev.commonLib, line 1158
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1159
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1160
    deleteAllStates() // library marker kkossev.commonLib, line 1161
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1162
    initialize() // library marker kkossev.commonLib, line 1163
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1164
    updated() // library marker kkossev.commonLib, line 1165
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1166
} // library marker kkossev.commonLib, line 1167

void configureNow() { // library marker kkossev.commonLib, line 1169
    configure() // library marker kkossev.commonLib, line 1170
} // library marker kkossev.commonLib, line 1171

/** // library marker kkossev.commonLib, line 1173
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1174
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1175
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1176
 */ // library marker kkossev.commonLib, line 1177
void configure() { // library marker kkossev.commonLib, line 1178
    List<String> cmds = [] // library marker kkossev.commonLib, line 1179
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1180
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1181
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1182
    if (isTuya()) { // library marker kkossev.commonLib, line 1183
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1184
    } // library marker kkossev.commonLib, line 1185
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1186
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1187
    } // library marker kkossev.commonLib, line 1188
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1189
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1190
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1191
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1192
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1193
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1194
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1195
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1196
    } // library marker kkossev.commonLib, line 1197
    else { // library marker kkossev.commonLib, line 1198
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1199
    } // library marker kkossev.commonLib, line 1200
} // library marker kkossev.commonLib, line 1201

 // Invoked when the device is installed or when driver is installed ? // library marker kkossev.commonLib, line 1203
void installed() { // library marker kkossev.commonLib, line 1204
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1205
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1206
    // populate some default values for attributes // library marker kkossev.commonLib, line 1207
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1208
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1209
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1210
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1211
} // library marker kkossev.commonLib, line 1212

 // Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1214
void initialize() { // library marker kkossev.commonLib, line 1215
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1216
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1217
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1218
    updateTuyaVersion() // library marker kkossev.commonLib, line 1219
    updateAqaraVersion() // library marker kkossev.commonLib, line 1220
} // library marker kkossev.commonLib, line 1221

/* // library marker kkossev.commonLib, line 1223
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1224
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1225
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1226
*/ // library marker kkossev.commonLib, line 1227

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1229
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1230
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1231
} // library marker kkossev.commonLib, line 1232

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1234
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1235
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1236
} // library marker kkossev.commonLib, line 1237

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1239
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1240
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1241
} // library marker kkossev.commonLib, line 1242

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1244
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1245
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1246
        return // library marker kkossev.commonLib, line 1247
    } // library marker kkossev.commonLib, line 1248
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1249
    cmd.each { // library marker kkossev.commonLib, line 1250
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1251
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1252
            return // library marker kkossev.commonLib, line 1253
        } // library marker kkossev.commonLib, line 1254
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1255
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1256
    } // library marker kkossev.commonLib, line 1257
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1258
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1259
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1260
} // library marker kkossev.commonLib, line 1261

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1263

String getDeviceInfo() { // library marker kkossev.commonLib, line 1265
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1266
} // library marker kkossev.commonLib, line 1267

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1269
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1270
} // library marker kkossev.commonLib, line 1271

@CompileStatic // library marker kkossev.commonLib, line 1273
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1274
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1275
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1276
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1277
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1278
        initializeVars(false) // library marker kkossev.commonLib, line 1279
        updateTuyaVersion() // library marker kkossev.commonLib, line 1280
        updateAqaraVersion() // library marker kkossev.commonLib, line 1281
    } // library marker kkossev.commonLib, line 1282
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1283
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1284
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1285
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1286
} // library marker kkossev.commonLib, line 1287

// credits @thebearmay // library marker kkossev.commonLib, line 1289
String getModel() { // library marker kkossev.commonLib, line 1290
    try { // library marker kkossev.commonLib, line 1291
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1292
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1293
    } catch (ignore) { // library marker kkossev.commonLib, line 1294
        try { // library marker kkossev.commonLib, line 1295
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1296
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1297
                return model // library marker kkossev.commonLib, line 1298
            } // library marker kkossev.commonLib, line 1299
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1300
            return '' // library marker kkossev.commonLib, line 1301
        } // library marker kkossev.commonLib, line 1302
    } // library marker kkossev.commonLib, line 1303
} // library marker kkossev.commonLib, line 1304

// credits @thebearmay // library marker kkossev.commonLib, line 1306
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1307
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1308
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1309
    String revision = tokens.last() // library marker kkossev.commonLib, line 1310
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1311
} // library marker kkossev.commonLib, line 1312

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1314
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1315
    unschedule() // library marker kkossev.commonLib, line 1316
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1317
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1318

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1320
} // library marker kkossev.commonLib, line 1321

void resetStatistics() { // library marker kkossev.commonLib, line 1323
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1324
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1325
} // library marker kkossev.commonLib, line 1326

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1328
void resetStats() { // library marker kkossev.commonLib, line 1329
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1330
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1331
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1332
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1333
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1334
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1338
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1339
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1340
        state.clear() // library marker kkossev.commonLib, line 1341
        unschedule() // library marker kkossev.commonLib, line 1342
        resetStats() // library marker kkossev.commonLib, line 1343
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1344
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1345
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1346
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1347
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1348
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1349
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1350
    } // library marker kkossev.commonLib, line 1351

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1353
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1354
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1355
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1356
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1357

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1359
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1360
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1361
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1362
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1363
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1364
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1365

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1367

    // common libraries initialization // library marker kkossev.commonLib, line 1369
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1370
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1371
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1372
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1373

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1375
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1376
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1377
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1378

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1380
    if ( mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1381
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1382
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1383
    if ( ep  != null) { // library marker kkossev.commonLib, line 1384
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1385
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1386
    } // library marker kkossev.commonLib, line 1387
    else { // library marker kkossev.commonLib, line 1388
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1389
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1390
    } // library marker kkossev.commonLib, line 1391
} // library marker kkossev.commonLib, line 1392

void setDestinationEP() { // library marker kkossev.commonLib, line 1394
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1395
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1396
        state.destinationEP = ep // library marker kkossev.commonLib, line 1397
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1398
    } // library marker kkossev.commonLib, line 1399
    else { // library marker kkossev.commonLib, line 1400
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1401
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1402
    } // library marker kkossev.commonLib, line 1403
} // library marker kkossev.commonLib, line 1404

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1406
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1407
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1408
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1409

// _DEBUG mode only // library marker kkossev.commonLib, line 1411
void getAllProperties() { // library marker kkossev.commonLib, line 1412
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1413
    device.properties.each { it -> // library marker kkossev.commonLib, line 1414
        log.debug it // library marker kkossev.commonLib, line 1415
    } // library marker kkossev.commonLib, line 1416
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1417
    settings.each { it -> // library marker kkossev.commonLib, line 1418
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1419
    } // library marker kkossev.commonLib, line 1420
    log.trace 'Done' // library marker kkossev.commonLib, line 1421
} // library marker kkossev.commonLib, line 1422

// delete all Preferences // library marker kkossev.commonLib, line 1424
void deleteAllSettings() { // library marker kkossev.commonLib, line 1425
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1426
    settings.each { it -> // library marker kkossev.commonLib, line 1427
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 1428
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1429
    } // library marker kkossev.commonLib, line 1430
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1431
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1432
} // library marker kkossev.commonLib, line 1433

// delete all attributes // library marker kkossev.commonLib, line 1435
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1436
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1437
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1438
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1439
} // library marker kkossev.commonLib, line 1440

// delete all State Variables // library marker kkossev.commonLib, line 1442
void deleteAllStates() { // library marker kkossev.commonLib, line 1443
    String stateDeleted = '' // library marker kkossev.commonLib, line 1444
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1445
    state.clear() // library marker kkossev.commonLib, line 1446
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1447
} // library marker kkossev.commonLib, line 1448

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1450
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1451
} // library marker kkossev.commonLib, line 1452

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1454
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 1455
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 1456
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 1457
    } // library marker kkossev.commonLib, line 1458
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1459
} // library marker kkossev.commonLib, line 1460

void testParse(String par) { // library marker kkossev.commonLib, line 1462
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1463
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1464
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1465
    parse(par) // library marker kkossev.commonLib, line 1466
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1467
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1468
} // library marker kkossev.commonLib, line 1469

def testJob() { // library marker kkossev.commonLib, line 1471
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1472
} // library marker kkossev.commonLib, line 1473

/** // library marker kkossev.commonLib, line 1475
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1476
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1477
 */ // library marker kkossev.commonLib, line 1478
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1479
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1480
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1481
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1482
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1483
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1484
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1485
    String cron // library marker kkossev.commonLib, line 1486
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1487
    else { // library marker kkossev.commonLib, line 1488
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1489
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1490
    } // library marker kkossev.commonLib, line 1491
    return cron // library marker kkossev.commonLib, line 1492
} // library marker kkossev.commonLib, line 1493

// credits @thebearmay // library marker kkossev.commonLib, line 1495
String formatUptime() { // library marker kkossev.commonLib, line 1496
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1497
} // library marker kkossev.commonLib, line 1498

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1500
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1501
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1502
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1503
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1504
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1505
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1506
} // library marker kkossev.commonLib, line 1507

boolean isTuya() { // library marker kkossev.commonLib, line 1509
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1510
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1511
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1512
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1513
    return (model?.startsWith('TS') && manufacturer?.startsWith('_T')) ? true : false // library marker kkossev.commonLib, line 1514
} // library marker kkossev.commonLib, line 1515

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1517
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1518
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1519
    if (application != null) { // library marker kkossev.commonLib, line 1520
        Integer ver // library marker kkossev.commonLib, line 1521
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1522
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1523
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1524
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1525
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1526
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1527
        } // library marker kkossev.commonLib, line 1528
    } // library marker kkossev.commonLib, line 1529
} // library marker kkossev.commonLib, line 1530

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1532

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1534
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1535
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1536
    if (application != null) { // library marker kkossev.commonLib, line 1537
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1538
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1539
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1540
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1541
        } // library marker kkossev.commonLib, line 1542
    } // library marker kkossev.commonLib, line 1543
} // library marker kkossev.commonLib, line 1544

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1546
    try { // library marker kkossev.commonLib, line 1547
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1548
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1549
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1550
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1551
    } catch (e) { // library marker kkossev.commonLib, line 1552
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1553
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1554
    } // library marker kkossev.commonLib, line 1555
} // library marker kkossev.commonLib, line 1556

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1558
    try { // library marker kkossev.commonLib, line 1559
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1560
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1561
        return date.getTime() // library marker kkossev.commonLib, line 1562
    } catch (e) { // library marker kkossev.commonLib, line 1563
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1564
        return now() // library marker kkossev.commonLib, line 1565
    } // library marker kkossev.commonLib, line 1566
} // library marker kkossev.commonLib, line 1567

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (167) kkossev.buttonLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.buttonLib, line 1
library( // library marker kkossev.buttonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Button Library', name: 'buttonLib', namespace: 'kkossev', // library marker kkossev.buttonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/buttonLib.groovy', documentationLink: '', // library marker kkossev.buttonLib, line 4
    version: '3.2.0' // library marker kkossev.buttonLib, line 5
) // library marker kkossev.buttonLib, line 6
/* // library marker kkossev.buttonLib, line 7
 *  Zigbee Button Library // library marker kkossev.buttonLib, line 8
 * // library marker kkossev.buttonLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.buttonLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.buttonLib, line 11
 * // library marker kkossev.buttonLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.buttonLib, line 13
 * // library marker kkossev.buttonLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.buttonLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.buttonLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.buttonLib, line 17
 * // library marker kkossev.buttonLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.buttonLib, line 19
 * ver. 3.2.0  2024-05-24 kkossev  - commonLib 3.2.0 allignment; added capability 'PushableButton' and 'Momentary' // library marker kkossev.buttonLib, line 20
 * // library marker kkossev.buttonLib, line 21
 *                                   TODO: // library marker kkossev.buttonLib, line 22
*/ // library marker kkossev.buttonLib, line 23

static String buttonLibVersion()   { '3.2.0' } // library marker kkossev.buttonLib, line 25
static String buttonLibStamp() { '2024/05/24 11:43 AM' } // library marker kkossev.buttonLib, line 26

metadata { // library marker kkossev.buttonLib, line 28
    capability 'PushableButton' // library marker kkossev.buttonLib, line 29
    capability 'Momentary' // library marker kkossev.buttonLib, line 30
    // the other capabilities must be declared in the custom driver, if applicable for the particular device! // library marker kkossev.buttonLib, line 31
    // the custom driver must allso call sendNumberOfButtonsEvent() and sendSupportedButtonValuesEvent()! // library marker kkossev.buttonLib, line 32
    // capability 'DoubleTapableButton' // library marker kkossev.buttonLib, line 33
    // capability 'HoldableButton' // library marker kkossev.buttonLib, line 34
    // capability 'ReleasableButton' // library marker kkossev.buttonLib, line 35

    // no attributes // library marker kkossev.buttonLib, line 37
    // no commands // library marker kkossev.buttonLib, line 38
    preferences { // library marker kkossev.buttonLib, line 39
        // no prefrences // library marker kkossev.buttonLib, line 40
    } // library marker kkossev.buttonLib, line 41
} // library marker kkossev.buttonLib, line 42

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.buttonLib, line 44
    if (buttonState != 'unknown' && buttonNumber != 0) { // library marker kkossev.buttonLib, line 45
        String descriptionText = "button $buttonNumber was $buttonState" // library marker kkossev.buttonLib, line 46
        if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.buttonLib, line 47
        Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.buttonLib, line 48
        logInfo "$descriptionText" // library marker kkossev.buttonLib, line 49
        sendEvent(event) // library marker kkossev.buttonLib, line 50
    } // library marker kkossev.buttonLib, line 51
    else { // library marker kkossev.buttonLib, line 52
        logWarn "sendButtonEvent: UNHANDLED event for button ${buttonNumber}, buttonState=${buttonState}" // library marker kkossev.buttonLib, line 53
    } // library marker kkossev.buttonLib, line 54
} // library marker kkossev.buttonLib, line 55

void push() {                // Momentary capability // library marker kkossev.buttonLib, line 57
    logDebug 'push momentary' // library marker kkossev.buttonLib, line 58
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.buttonLib, line 59
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.buttonLib, line 60
} // library marker kkossev.buttonLib, line 61

/* // library marker kkossev.buttonLib, line 63
void push(BigDecimal buttonNumber) {    //pushableButton capability // library marker kkossev.buttonLib, line 64
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 65
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 66
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 67
} // library marker kkossev.buttonLib, line 68
*/ // library marker kkossev.buttonLib, line 69

void push(Object bn) {    //pushableButton capability // library marker kkossev.buttonLib, line 71
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 72
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 73
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 74
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 75
} // library marker kkossev.buttonLib, line 76

void doubleTap(Object bn) { // library marker kkossev.buttonLib, line 78
    Integer buttonNumber = safeToInt(bn) // library marker kkossev.buttonLib, line 79
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.buttonLib, line 80
} // library marker kkossev.buttonLib, line 81

void hold(Object bn) { // library marker kkossev.buttonLib, line 83
    Integer buttonNumber = safeToInt(bn) // library marker kkossev.buttonLib, line 84
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.buttonLib, line 85
} // library marker kkossev.buttonLib, line 86

void release(Object bn) { // library marker kkossev.buttonLib, line 88
    Integer buttonNumber = safeToInt(bn) // library marker kkossev.buttonLib, line 89
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true) // library marker kkossev.buttonLib, line 90
} // library marker kkossev.buttonLib, line 91

// must be called from the custom driver! // library marker kkossev.buttonLib, line 93
void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.buttonLib, line 94
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 95
} // library marker kkossev.buttonLib, line 96
// must be called from the custom driver! // library marker kkossev.buttonLib, line 97
void sendSupportedButtonValuesEvent(List<String> supportedValues) { // library marker kkossev.buttonLib, line 98
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 99
} // library marker kkossev.buttonLib, line 100


// ~~~~~ end include (167) kkossev.buttonLib ~~~~~

// ~~~~~ start include (175) kkossev.ctLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.ctLib, line 1
library( // library marker kkossev.ctLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Color Temperature Library', name: 'ctLib', namespace: 'kkossev', // library marker kkossev.ctLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/ctLib.groovy', documentationLink: '', // library marker kkossev.ctLib, line 4
    version: '3.2.0' // library marker kkossev.ctLib, line 5
) // library marker kkossev.ctLib, line 6
/* // library marker kkossev.ctLib, line 7
 *  Color Temperature Library // library marker kkossev.ctLib, line 8
 * // library marker kkossev.ctLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.ctLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.ctLib, line 11
 * // library marker kkossev.ctLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.ctLib, line 13
 * // library marker kkossev.ctLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.ctLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.ctLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.ctLib, line 17
 * // library marker kkossev.ctLib, line 18
 * ver. 3.2.0  2024-05-22 kkossev  - commonLib 3.2.0 allignment // library marker kkossev.ctLib, line 19
 * // library marker kkossev.ctLib, line 20
 *                                   TODO: // library marker kkossev.ctLib, line 21
*/ // library marker kkossev.ctLib, line 22

static String ctLibVersion()   { '3.2.0' } // library marker kkossev.ctLib, line 24
static String ctLibStamp() { '2024/05/22 10:00 PM' } // library marker kkossev.ctLib, line 25

metadata { // library marker kkossev.ctLib, line 27
    capability 'Color Temperature'  // Attributes: colorName - STRING, colorTemperature - NUMBER, unit:°K; Commands:setColorTemperature(colortemperature, level, transitionTime) // library marker kkossev.ctLib, line 28
    capability 'ColorMode'          // Attributes:  colorMode - ENUM ["CT", "RGB", "EFFECTS"] // library marker kkossev.ctLib, line 29
    // no attributes // library marker kkossev.ctLib, line 30
    // no commands // library marker kkossev.ctLib, line 31
    preferences { // library marker kkossev.ctLib, line 32
        // no prefrences // library marker kkossev.ctLib, line 33
    } // library marker kkossev.ctLib, line 34
} // library marker kkossev.ctLib, line 35

import groovy.transform.Field // library marker kkossev.ctLib, line 37

private getMAX_WHITE_SATURATION() { 70 } // library marker kkossev.ctLib, line 39
private getWHITE_HUE() { 8 } // library marker kkossev.ctLib, line 40
private getMIN_COLOR_TEMP() { 2700 } // library marker kkossev.ctLib, line 41
private getMAX_COLOR_TEMP() { 6500 } // library marker kkossev.ctLib, line 42




/* // library marker kkossev.ctLib, line 47
 * ----------------------------------------------------------------------------- // library marker kkossev.ctLib, line 48
 * ColorControl Cluster            0x0300 // library marker kkossev.ctLib, line 49
 * ----------------------------------------------------------------------------- // library marker kkossev.ctLib, line 50
*/ // library marker kkossev.ctLib, line 51
void standardParseColorControlCluster(final Map descMap, description) { // library marker kkossev.ctLib, line 52
    logDebug "standardParseColorControlCluster: ${descMap}" // library marker kkossev.ctLib, line 53
    if (descMap.attrId != null) { // library marker kkossev.ctLib, line 54
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "standardParseColorControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.ctLib, line 55
        processColorControlCluster(descMap, description) // library marker kkossev.ctLib, line 56
    } // library marker kkossev.ctLib, line 57
    else { // library marker kkossev.ctLib, line 58
        logWarn "unprocessed ColorControl attribute ${descMap.attrId}" // library marker kkossev.ctLib, line 59
    } // library marker kkossev.ctLib, line 60
} // library marker kkossev.ctLib, line 61

void processColorControlCluster(final Map descMap, description) { // library marker kkossev.ctLib, line 63
    logDebug "processColorControlCluster : ${descMap}" // library marker kkossev.ctLib, line 64
    def map = [:] // library marker kkossev.ctLib, line 65
    def parsed // library marker kkossev.ctLib, line 66

    if (description instanceof String)  { // library marker kkossev.ctLib, line 68
        map = stringToMap(description) // library marker kkossev.ctLib, line 69
    } // library marker kkossev.ctLib, line 70

    logDebug "Map - $map" // library marker kkossev.ctLib, line 72
    def raw = map['read attr - raw'] // library marker kkossev.ctLib, line 73

    if (raw) { // library marker kkossev.ctLib, line 75
        def clusterId = map.cluster // library marker kkossev.ctLib, line 76
        def attrList = raw.substring(12) // library marker kkossev.ctLib, line 77

        parsed = parseAttributeList(clusterId, attrList) // library marker kkossev.ctLib, line 79

        if (state.colorChanged || (state.colorXReported && state.colorYReported)) { // library marker kkossev.ctLib, line 81
            state.colorChanged = false // library marker kkossev.ctLib, line 82
            state.colorXReported = false // library marker kkossev.ctLib, line 83
            state.colorYReported = false // library marker kkossev.ctLib, line 84
            logTrace "Color Change: xy ($state.colorX, $state.colorY)" // library marker kkossev.ctLib, line 85
            def rgb = colorXy2Rgb(state.colorX, state.colorY) // library marker kkossev.ctLib, line 86
            logTrace "Color Change: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.ctLib, line 87
            updateColor(rgb)        // sends a bunch of events! // library marker kkossev.ctLib, line 88
        } // library marker kkossev.ctLib, line 89
    } // library marker kkossev.ctLib, line 90
    else { // library marker kkossev.ctLib, line 91
        logDebug 'Sending color event based on pending values' // library marker kkossev.ctLib, line 92
        if (state.pendingColorUpdate) { // library marker kkossev.ctLib, line 93
            parsed = true // library marker kkossev.ctLib, line 94
            def rgb = colorXy2Rgb(state.colorX, state.colorY) // library marker kkossev.ctLib, line 95
            updateColor(rgb)            // sends a bunch of events! // library marker kkossev.ctLib, line 96
            state.pendingColorUpdate = false // library marker kkossev.ctLib, line 97
        } // library marker kkossev.ctLib, line 98
    } // library marker kkossev.ctLib, line 99
} // library marker kkossev.ctLib, line 100

def parseHex4le(hex) { // library marker kkossev.ctLib, line 102
    Integer.parseInt(hex.substring(2, 4) + hex.substring(0, 2), 16) // library marker kkossev.ctLib, line 103
} // library marker kkossev.ctLib, line 104

def parseColorAttribute(id, value) { // library marker kkossev.ctLib, line 106
    def parsed = false // library marker kkossev.ctLib, line 107

    if (id == 0x03) { // library marker kkossev.ctLib, line 109
        // currentColorX // library marker kkossev.ctLib, line 110
        value = parseHex4le(value) // library marker kkossev.ctLib, line 111
        logTrace "Parsed ColorX: $value" // library marker kkossev.ctLib, line 112
        value /= 65536 // library marker kkossev.ctLib, line 113
        parsed = true // library marker kkossev.ctLib, line 114
        state.colorXReported = true // library marker kkossev.ctLib, line 115
        state.colorChanged |= value != colorX // library marker kkossev.ctLib, line 116
        state.colorX = value // library marker kkossev.ctLib, line 117
    } // library marker kkossev.ctLib, line 118
    else if (id == 0x04) { // library marker kkossev.ctLib, line 119
        // currentColorY // library marker kkossev.ctLib, line 120
        value = parseHex4le(value) // library marker kkossev.ctLib, line 121
        logTrace "Parsed ColorY: $value" // library marker kkossev.ctLib, line 122
        value /= 65536 // library marker kkossev.ctLib, line 123
        parsed = true // library marker kkossev.ctLib, line 124
        state.colorYReported = true // library marker kkossev.ctLib, line 125
        state.colorChanged |= value != colorY // library marker kkossev.ctLib, line 126
        state.colorY = value // library marker kkossev.ctLib, line 127
    } // library marker kkossev.ctLib, line 128
    else {  // TODO: parse atttribute 7 (color temperature in mireds) // library marker kkossev.ctLib, line 129
        logDebug "Not parsing Color cluster attribute $id: $value" // library marker kkossev.ctLib, line 130
    } // library marker kkossev.ctLib, line 131

    parsed // library marker kkossev.ctLib, line 133
} // library marker kkossev.ctLib, line 134

def parseAttributeList(cluster, list) { // library marker kkossev.ctLib, line 136
    logTrace "Cluster: $cluster, AttrList: $list" // library marker kkossev.ctLib, line 137
    def parsed = true // library marker kkossev.ctLib, line 138

    while (list.length()) { // library marker kkossev.ctLib, line 140
        def attrId = parseHex4le(list.substring(0, 4)) // library marker kkossev.ctLib, line 141
        def attrType = Integer.parseInt(list.substring(4, 6), 16) // library marker kkossev.ctLib, line 142
        def attrShift = 0 // library marker kkossev.ctLib, line 143

        if (!attrType) { // library marker kkossev.ctLib, line 145
            attrType = Integer.parseInt(list.substring(6, 8), 16) // library marker kkossev.ctLib, line 146
            attrShift = 1 // library marker kkossev.ctLib, line 147
        } // library marker kkossev.ctLib, line 148

        def attrLen = DataType.getLength(attrType) // library marker kkossev.ctLib, line 150
        def attrValue = list.substring(6 + 2 * attrShift, 6 + 2 * (attrShift+attrLen)) // library marker kkossev.ctLib, line 151

        logTrace "Attr - Id: $attrId($attrLen), Type: $attrType, Value: $attrValue" // library marker kkossev.ctLib, line 153

        if (cluster == 300) { // library marker kkossev.ctLib, line 155
            parsed &= parseColorAttribute(attrId, attrValue) // library marker kkossev.ctLib, line 156
        } // library marker kkossev.ctLib, line 157
        else { // library marker kkossev.ctLib, line 158
            log.info "Not parsing cluster $cluster attribute: $list" // library marker kkossev.ctLib, line 159
            parsed = false // library marker kkossev.ctLib, line 160
        } // library marker kkossev.ctLib, line 161

        list = list.substring(6 + 2 * (attrShift+attrLen)) // library marker kkossev.ctLib, line 163
    } // library marker kkossev.ctLib, line 164

    parsed // library marker kkossev.ctLib, line 166
} // library marker kkossev.ctLib, line 167


def setColorTemperature(value, level=null, rate=null) { // library marker kkossev.ctLib, line 170
    logDebug "Set color temperature $value" // library marker kkossev.ctLib, line 171

    def sat = MAX_WHITE_SATURATION - (((value - MIN_COLOR_TEMP) / (MAX_COLOR_TEMP - MIN_COLOR_TEMP)) * MAX_WHITE_SATURATION) // library marker kkossev.ctLib, line 173
    setColor([ // library marker kkossev.ctLib, line 174
            hue: WHITE_HUE, // library marker kkossev.ctLib, line 175
            saturation: sat, // library marker kkossev.ctLib, line 176
            level: level, // library marker kkossev.ctLib, line 177
            rate: rate // library marker kkossev.ctLib, line 178
    ]) // library marker kkossev.ctLib, line 179
} // library marker kkossev.ctLib, line 180

def setColor(value) { // library marker kkossev.ctLib, line 182
    logDebug "setColor($value)" // library marker kkossev.ctLib, line 183
    def rgb = colorHsv2Rgb(value.hue / 100, value.saturation / 100) // library marker kkossev.ctLib, line 184

    logTrace "setColor: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.ctLib, line 186
    def xy = colorRgb2Xy(rgb.red, rgb.green, rgb.blue) // library marker kkossev.ctLib, line 187
    logTrace "setColor: xy ($xy.x, $xy.y)" // library marker kkossev.ctLib, line 188

    def intX = Math.round(xy.x * 65536).intValue() // 0..65279 // library marker kkossev.ctLib, line 190
    def intY = Math.round(xy.y * 65536).intValue() // 0..65279 // library marker kkossev.ctLib, line 191

    logTrace "setColor: xy ($intX, $intY)" // library marker kkossev.ctLib, line 193

    state.colorX = xy.x // library marker kkossev.ctLib, line 195
    state.colorY = xy.y // library marker kkossev.ctLib, line 196

    def strX = DataType.pack(intX, DataType.UINT16, true) // library marker kkossev.ctLib, line 198
    def strY = DataType.pack(intY, DataType.UINT16, true) // library marker kkossev.ctLib, line 199

    List cmds = [] // library marker kkossev.ctLib, line 201

    def level = value.level // library marker kkossev.ctLib, line 203
    def rate = value.rate // library marker kkossev.ctLib, line 204

    if (level != null && rate != null) { // library marker kkossev.ctLib, line 206
        state.pendingLevelChange = level // library marker kkossev.ctLib, line 207
        cmds += zigbee.setLevel(level, rate) // library marker kkossev.ctLib, line 208
    } else if (level != null) { // library marker kkossev.ctLib, line 209
        state.pendingLevelChange = level // library marker kkossev.ctLib, line 210
        cmds += zigbee.setLevel(level) // library marker kkossev.ctLib, line 211
    } // library marker kkossev.ctLib, line 212

    state.pendingColorUpdate = true // library marker kkossev.ctLib, line 214

    cmds += zigbee.command(0x0300, 0x07, strX, strY, '0a00') // library marker kkossev.ctLib, line 216
    if (state.cmds == null) { state.cmds = [] } // library marker kkossev.ctLib, line 217
    state.cmds += cmds // library marker kkossev.ctLib, line 218

    logTrace "zigbee command: $cmds" // library marker kkossev.ctLib, line 220

    unschedule(sendZigbeeCommandsDelayed) // library marker kkossev.ctLib, line 222
    runInMillis(100, sendZigbeeCommandsDelayed) // library marker kkossev.ctLib, line 223
} // library marker kkossev.ctLib, line 224

// all the code below is borrowed from Ivar Holand's "IKEA Tradfri RGBW Light HE v2" driver // library marker kkossev.ctLib, line 226
// ----------------------------------------------------------------------------------------- // library marker kkossev.ctLib, line 227

def updateColor(rgb) { // library marker kkossev.ctLib, line 229
    logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.ctLib, line 230
    def hsv = colorRgb2Hsv(rgb.red, rgb.green, rgb.blue) // library marker kkossev.ctLib, line 231
    hsv.hue = Math.round(hsv.hue * 100).intValue() // library marker kkossev.ctLib, line 232
    hsv.saturation = Math.round(hsv.saturation * 100).intValue() // library marker kkossev.ctLib, line 233
    hsv.level = Math.round(hsv.level * 100).intValue() // library marker kkossev.ctLib, line 234
    logTrace "updateColor: HSV ($hsv.hue, $hsv.saturation, $hsv.level)" // library marker kkossev.ctLib, line 235

    rgb.red = Math.round(rgb.red * 255).intValue() // library marker kkossev.ctLib, line 237
    rgb.green = Math.round(rgb.green * 255).intValue() // library marker kkossev.ctLib, line 238
    rgb.blue = Math.round(rgb.blue * 255).intValue() // library marker kkossev.ctLib, line 239
    logTrace "updateColor: RGB ($rgb.red, $rgb.green, $rgb.blue)" // library marker kkossev.ctLib, line 240

    def color = ColorUtils.rgbToHEX([rgb.red, rgb.green, rgb.blue]) // library marker kkossev.ctLib, line 242
    logTrace "updateColor: $color" // library marker kkossev.ctLib, line 243

    sendColorEvent([name: 'color', value: color, data: [ hue: hsv.hue, saturation: hsv.saturation, red: rgb.red, green: rgb.green, blue: rgb.blue, hex: color], displayed: false]) // library marker kkossev.ctLib, line 245
    sendHueEvent([name: 'hue', value: hsv.hue, displayed: false]) // library marker kkossev.ctLib, line 246
    sendSaturationEvent([name: 'saturation', value: hsv.saturation, displayed: false]) // library marker kkossev.ctLib, line 247
    if (hsv.hue == WHITE_HUE) { // library marker kkossev.ctLib, line 248
        def percent = (1 - ((hsv.saturation / 100) * (100 / MAX_WHITE_SATURATION))) // library marker kkossev.ctLib, line 249
        def amount = (MAX_COLOR_TEMP - MIN_COLOR_TEMP) * percent // library marker kkossev.ctLib, line 250
        def val = Math.round(MIN_COLOR_TEMP + amount) // library marker kkossev.ctLib, line 251
        sendColorTemperatureEvent([name: 'colorTemperature', value: val]) // library marker kkossev.ctLib, line 252
        sendColorModeEvent([name: 'colorMode', value: 'CT']) // library marker kkossev.ctLib, line 253
        sendColorNameEvent([setGenericTempName(val)]) // library marker kkossev.ctLib, line 254
    } // library marker kkossev.ctLib, line 255
    else { // library marker kkossev.ctLib, line 256
        sendColorModeEvent([name: 'colorMode', value: 'RGB']) // library marker kkossev.ctLib, line 257
        sendColorNameEvent(setGenericName(hsv.hue)) // library marker kkossev.ctLib, line 258
    } // library marker kkossev.ctLib, line 259
} // library marker kkossev.ctLib, line 260

void sendColorEvent(map) { // library marker kkossev.ctLib, line 262
    if (map.value == device.currentValue(map.name)) { // library marker kkossev.ctLib, line 263
        logDebug "sendColorEvent: ${map.name} is already ${map.value}" // library marker kkossev.ctLib, line 264
        return // library marker kkossev.ctLib, line 265
    } // library marker kkossev.ctLib, line 266
    // get the time of the last event named "color" and compare it to the current time // library marker kkossev.ctLib, line 267
 //   def lastColorEvent = device.currentState("color",true).date.time // library marker kkossev.ctLib, line 268
 //   if ((now() - lastColorEvent) < 1000) { // library marker kkossev.ctLib, line 269
       // logDebug "sendColorEvent: delaying ${map.name} event because the last color event was less than 1 second ago ${(now() - lastColorEvent)}" // library marker kkossev.ctLib, line 270
    runInMillis(500, 'sendDelayedColorEvent',  [overwrite: true, data: map]) // library marker kkossev.ctLib, line 271
    return // library marker kkossev.ctLib, line 272
//    } // library marker kkossev.ctLib, line 273
    //unschedule("sendDelayedColorEvent") // cancel any pending delayed events // library marker kkossev.ctLib, line 274
    //logDebug "sendColorEvent: lastColorEvent = ${lastColorEvent}, now = ${now()}, diff = ${(now() - lastColorEvent)}" // library marker kkossev.ctLib, line 275
    //sendEvent(map) // library marker kkossev.ctLib, line 276
} // library marker kkossev.ctLib, line 277
private void sendDelayedColorEvent(Map map) { // library marker kkossev.ctLib, line 278
    sendEvent(map) // library marker kkossev.ctLib, line 279
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.ctLib, line 280
} // library marker kkossev.ctLib, line 281

void sendHueEvent(map) { // library marker kkossev.ctLib, line 283
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.ctLib, line 284
    runInMillis(500, 'sendDelayedHueEvent',  [overwrite: true, data: map]) // library marker kkossev.ctLib, line 285
} // library marker kkossev.ctLib, line 286
private void sendDelayedHueEvent(Map map) { // library marker kkossev.ctLib, line 287
    sendEvent(map) // library marker kkossev.ctLib, line 288
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.ctLib, line 289
} // library marker kkossev.ctLib, line 290

void sendSaturationEvent(map) { // library marker kkossev.ctLib, line 292
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.ctLib, line 293
    runInMillis(500, 'sendDelayedSaturationEvent',  [overwrite: true, data: map]) // library marker kkossev.ctLib, line 294
} // library marker kkossev.ctLib, line 295
private void sendDelayedSaturationEvent(Map map) { // library marker kkossev.ctLib, line 296
    sendEvent(map) // library marker kkossev.ctLib, line 297
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.ctLib, line 298
} // library marker kkossev.ctLib, line 299

void sendColorModeEvent(map) { // library marker kkossev.ctLib, line 301
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.ctLib, line 302
    runInMillis(500, 'sendDelayedColorModeEvent',  [overwrite: true, data: map]) // library marker kkossev.ctLib, line 303
} // library marker kkossev.ctLib, line 304
private void sendDelayedColorModeEvent(Map map) { // library marker kkossev.ctLib, line 305
    sendEvent(map) // library marker kkossev.ctLib, line 306
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.ctLib, line 307
} // library marker kkossev.ctLib, line 308

void sendColorNameEvent(map) { // library marker kkossev.ctLib, line 310
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.ctLib, line 311
    runInMillis(500, 'sendDelayedColorNameEvent',  [overwrite: true, data: map]) // library marker kkossev.ctLib, line 312
} // library marker kkossev.ctLib, line 313
private void sendDelayedColorNameEvent(Map map) { // library marker kkossev.ctLib, line 314
    sendEvent(map) // library marker kkossev.ctLib, line 315
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.ctLib, line 316
} // library marker kkossev.ctLib, line 317

void sendColorTemperatureEvent(map) { // library marker kkossev.ctLib, line 319
    if (map.value == device.currentValue(map.name)) { return } // library marker kkossev.ctLib, line 320
    runInMillis(500, 'sendDelayedColorTemperatureEvent',  [overwrite: true, data: map]) // library marker kkossev.ctLib, line 321
} // library marker kkossev.ctLib, line 322
private void sendDelayedColorTemperatureEvent(Map map) { // library marker kkossev.ctLib, line 323
    sendEvent(map) // library marker kkossev.ctLib, line 324
    logInfo "${map.name} is now ${map.value}" // library marker kkossev.ctLib, line 325
} // library marker kkossev.ctLib, line 326

def sendZigbeeCommandsDelayed() { // library marker kkossev.ctLib, line 328
    List cmds = state.cmds // library marker kkossev.ctLib, line 329
    if (cmds != null) { // library marker kkossev.ctLib, line 330
        state.cmds = [] // library marker kkossev.ctLib, line 331
        sendZigbeeCommands(cmds) // library marker kkossev.ctLib, line 332
    } // library marker kkossev.ctLib, line 333
} // library marker kkossev.ctLib, line 334


def setHue(hue) { // library marker kkossev.ctLib, line 337
    logDebug "setHue: $hue" // library marker kkossev.ctLib, line 338
    setColor([ hue: hue, saturation: device.currentValue('saturation') ]) // library marker kkossev.ctLib, line 339
} // library marker kkossev.ctLib, line 340

def setSaturation(saturation) { // library marker kkossev.ctLib, line 342
    logDebug "setSaturation: $saturation" // library marker kkossev.ctLib, line 343
    setColor([ hue: device.currentValue('hue'), saturation: saturation ]) // library marker kkossev.ctLib, line 344
} // library marker kkossev.ctLib, line 345

def setGenericTempName(temp) { // library marker kkossev.ctLib, line 347
    if (!temp) return // library marker kkossev.ctLib, line 348
    String genericName // library marker kkossev.ctLib, line 349
    int value = temp.toInteger() // library marker kkossev.ctLib, line 350
    if (value <= 2000) genericName = 'Sodium' // library marker kkossev.ctLib, line 351
    else if (value <= 2100) genericName = 'Starlight' // library marker kkossev.ctLib, line 352
    else if (value < 2400) genericName = 'Sunrise' // library marker kkossev.ctLib, line 353
    else if (value < 2800) genericName = 'Incandescent' // library marker kkossev.ctLib, line 354
    else if (value < 3300) genericName = 'Soft White' // library marker kkossev.ctLib, line 355
    else if (value < 3500) genericName = 'Warm White' // library marker kkossev.ctLib, line 356
    else if (value < 4150) genericName = 'Moonlight' // library marker kkossev.ctLib, line 357
    else if (value <= 5000) genericName = 'Horizon' // library marker kkossev.ctLib, line 358
    else if (value < 5500) genericName = 'Daylight' // library marker kkossev.ctLib, line 359
    else if (value < 6000) genericName = 'Electronic' // library marker kkossev.ctLib, line 360
    else if (value <= 6500) genericName = 'Skylight' // library marker kkossev.ctLib, line 361
    else if (value < 20000) genericName = 'Polar' // library marker kkossev.ctLib, line 362
    String descriptionText = "${device.getDisplayName()} color is ${genericName}" // library marker kkossev.ctLib, line 363
    return createEvent(name: 'colorName', value: genericName ,descriptionText: descriptionText) // library marker kkossev.ctLib, line 364
} // library marker kkossev.ctLib, line 365

def setGenericName(hue) { // library marker kkossev.ctLib, line 367
    String colorName // library marker kkossev.ctLib, line 368
    hue = hue.toInteger() // library marker kkossev.ctLib, line 369
    hue = (hue * 3.6) // library marker kkossev.ctLib, line 370
    switch (hue.toInteger()) { // library marker kkossev.ctLib, line 371
        case 0..15: colorName = 'Red' // library marker kkossev.ctLib, line 372
            break // library marker kkossev.ctLib, line 373
        case 16..45: colorName = 'Orange' // library marker kkossev.ctLib, line 374
            break // library marker kkossev.ctLib, line 375
        case 46..75: colorName = 'Yellow' // library marker kkossev.ctLib, line 376
            break // library marker kkossev.ctLib, line 377
        case 76..105: colorName = 'Chartreuse' // library marker kkossev.ctLib, line 378
            break // library marker kkossev.ctLib, line 379
        case 106..135: colorName = 'Green' // library marker kkossev.ctLib, line 380
            break // library marker kkossev.ctLib, line 381
        case 136..165: colorName = 'Spring' // library marker kkossev.ctLib, line 382
            break // library marker kkossev.ctLib, line 383
        case 166..195: colorName = 'Cyan' // library marker kkossev.ctLib, line 384
            break // library marker kkossev.ctLib, line 385
        case 196..225: colorName = 'Azure' // library marker kkossev.ctLib, line 386
            break // library marker kkossev.ctLib, line 387
        case 226..255: colorName = 'Blue' // library marker kkossev.ctLib, line 388
            break // library marker kkossev.ctLib, line 389
        case 256..285: colorName = 'Violet' // library marker kkossev.ctLib, line 390
            break // library marker kkossev.ctLib, line 391
        case 286..315: colorName = 'Magenta' // library marker kkossev.ctLib, line 392
            break // library marker kkossev.ctLib, line 393
        case 316..345: colorName = 'Rose' // library marker kkossev.ctLib, line 394
            break // library marker kkossev.ctLib, line 395
        case 346..360: colorName = 'Red' // library marker kkossev.ctLib, line 396
            break // library marker kkossev.ctLib, line 397
    } // library marker kkossev.ctLib, line 398
    String descriptionText = "${device.getDisplayName()} color is ${colorName}" // library marker kkossev.ctLib, line 399
    return createEvent(name: 'colorName', value: colorName ,descriptionText: descriptionText) // library marker kkossev.ctLib, line 400
} // library marker kkossev.ctLib, line 401

/* // library marker kkossev.ctLib, line 403
def startLevelChange(direction) { // library marker kkossev.ctLib, line 404
    def dir = direction == 'up'? 0 : 1 // library marker kkossev.ctLib, line 405
    def rate = 100 // library marker kkossev.ctLib, line 406

    if (levelChangeRate != null) { // library marker kkossev.ctLib, line 408
        rate = levelChangeRate // library marker kkossev.ctLib, line 409
    } // library marker kkossev.ctLib, line 410

    return zigbee.command(0x0008, 0x01, "0x${iTo8bitHex(dir)} 0x${iTo8bitHex(rate)}") // library marker kkossev.ctLib, line 412
} // library marker kkossev.ctLib, line 413
*/ // library marker kkossev.ctLib, line 414
/* // library marker kkossev.ctLib, line 415
def stopLevelChange() { // library marker kkossev.ctLib, line 416
    return zigbee.command(0x0008, 0x03, '') + zigbee.levelRefresh() // library marker kkossev.ctLib, line 417
} // library marker kkossev.ctLib, line 418
*/ // library marker kkossev.ctLib, line 419

// Color Management functions // library marker kkossev.ctLib, line 421

def min(first, ... rest) { // library marker kkossev.ctLib, line 423
    def min = first // library marker kkossev.ctLib, line 424
    for (next in rest) { // library marker kkossev.ctLib, line 425
        if (next < min) min = next // library marker kkossev.ctLib, line 426
    } // library marker kkossev.ctLib, line 427

    min // library marker kkossev.ctLib, line 429
} // library marker kkossev.ctLib, line 430

def max(first, ... rest) { // library marker kkossev.ctLib, line 432
    def max = first // library marker kkossev.ctLib, line 433
    for (next in rest) { // library marker kkossev.ctLib, line 434
        if (next > max) max = next // library marker kkossev.ctLib, line 435
    } // library marker kkossev.ctLib, line 436

    max // library marker kkossev.ctLib, line 438
} // library marker kkossev.ctLib, line 439

def colorGammaAdjust(component) { // library marker kkossev.ctLib, line 441
    return (component > 0.04045) ? Math.pow((component + 0.055) / (1.0 + 0.055), 2.4) : (component / 12.92) // library marker kkossev.ctLib, line 442
} // library marker kkossev.ctLib, line 443

def colorGammaRevert(component) { // library marker kkossev.ctLib, line 445
    return (component <= 0.0031308) ? 12.92 * component : (1.0 + 0.055) * Math.pow(component, (1.0 / 2.4)) - 0.055 // library marker kkossev.ctLib, line 446
} // library marker kkossev.ctLib, line 447

def colorXy2Rgb(x = 255, y = 255) { // library marker kkossev.ctLib, line 449
    logTrace "< Color xy: ($x, $y)" // library marker kkossev.ctLib, line 450

    def Y = 1 // library marker kkossev.ctLib, line 452
    def X = (Y / y) * x // library marker kkossev.ctLib, line 453
    def Z = (Y / y) * (1.0 - x - y) // library marker kkossev.ctLib, line 454

    logTrace "< Color XYZ: ($X, $Y, $Z)" // library marker kkossev.ctLib, line 456

    // sRGB, Reference White D65 // library marker kkossev.ctLib, line 458
    def M = [ // library marker kkossev.ctLib, line 459
            [  3.2410032, -1.5373990, -0.4986159 ], // library marker kkossev.ctLib, line 460
            [ -0.9692243,  1.8759300,  0.0415542 ], // library marker kkossev.ctLib, line 461
            [  0.0556394, -0.2040112,  1.0571490 ] // library marker kkossev.ctLib, line 462
    ] // library marker kkossev.ctLib, line 463

    def r = X * M[0][0] + Y * M[0][1] + Z * M[0][2] // library marker kkossev.ctLib, line 465
    def g = X * M[1][0] + Y * M[1][1] + Z * M[1][2] // library marker kkossev.ctLib, line 466
    def b = X * M[2][0] + Y * M[2][1] + Z * M[2][2] // library marker kkossev.ctLib, line 467

    def max = max(r, g, b) // library marker kkossev.ctLib, line 469
    r = colorGammaRevert(r / max) // library marker kkossev.ctLib, line 470
    g = colorGammaRevert(g / max) // library marker kkossev.ctLib, line 471
    b = colorGammaRevert(b / max) // library marker kkossev.ctLib, line 472

    logTrace "< Color RGB: ($r, $g, $b)" // library marker kkossev.ctLib, line 474

    [red: r, green: g, blue: b] // library marker kkossev.ctLib, line 476
} // library marker kkossev.ctLib, line 477

def colorRgb2Xy(r, g, b) { // library marker kkossev.ctLib, line 479
    logTrace "> Color RGB: ($r, $g, $b)" // library marker kkossev.ctLib, line 480

    r = colorGammaAdjust(r) // library marker kkossev.ctLib, line 482
    g = colorGammaAdjust(g) // library marker kkossev.ctLib, line 483
    b = colorGammaAdjust(b) // library marker kkossev.ctLib, line 484

    // sRGB, Reference White D65 // library marker kkossev.ctLib, line 486
    // D65    0.31271    0.32902 // library marker kkossev.ctLib, line 487
    //  R  0.64000 0.33000 // library marker kkossev.ctLib, line 488
    //  G  0.30000 0.60000 // library marker kkossev.ctLib, line 489
    //  B  0.15000 0.06000 // library marker kkossev.ctLib, line 490
    def M = [ // library marker kkossev.ctLib, line 491
            [  0.4123866,  0.3575915,  0.1804505 ], // library marker kkossev.ctLib, line 492
            [  0.2126368,  0.7151830,  0.0721802 ], // library marker kkossev.ctLib, line 493
            [  0.0193306,  0.1191972,  0.9503726 ] // library marker kkossev.ctLib, line 494
    ] // library marker kkossev.ctLib, line 495

    def X = r * M[0][0] + g * M[0][1] + b * M[0][2] // library marker kkossev.ctLib, line 497
    def Y = r * M[1][0] + g * M[1][1] + b * M[1][2] // library marker kkossev.ctLib, line 498
    def Z = r * M[2][0] + g * M[2][1] + b * M[2][2] // library marker kkossev.ctLib, line 499

    logTrace "> Color XYZ: ($X, $Y, $Z)" // library marker kkossev.ctLib, line 501

    def x = X / (X + Y + Z) // library marker kkossev.ctLib, line 503
    def y = Y / (X + Y + Z) // library marker kkossev.ctLib, line 504

    logTrace "> Color xy: ($x, $y)" // library marker kkossev.ctLib, line 506

    [x: x, y: y] // library marker kkossev.ctLib, line 508
} // library marker kkossev.ctLib, line 509

def colorHsv2Rgb(h, s) { // library marker kkossev.ctLib, line 511
    logTrace "< Color HSV: ($h, $s, 1)" // library marker kkossev.ctLib, line 512

    def r // library marker kkossev.ctLib, line 514
    def g // library marker kkossev.ctLib, line 515
    def b // library marker kkossev.ctLib, line 516

    if (s == 0) { // library marker kkossev.ctLib, line 518
        r = 1 // library marker kkossev.ctLib, line 519
        g = 1 // library marker kkossev.ctLib, line 520
        b = 1 // library marker kkossev.ctLib, line 521
    } // library marker kkossev.ctLib, line 522
    else { // library marker kkossev.ctLib, line 523
        def region = (6 * h).intValue() // library marker kkossev.ctLib, line 524
        def remainder = 6 * h - region // library marker kkossev.ctLib, line 525

        def p = 1 - s // library marker kkossev.ctLib, line 527
        def q = 1 - s * remainder // library marker kkossev.ctLib, line 528
        def t = 1 - s * (1 - remainder) // library marker kkossev.ctLib, line 529

        if (region == 0) { // library marker kkossev.ctLib, line 531
            r = 1 // library marker kkossev.ctLib, line 532
            g = t // library marker kkossev.ctLib, line 533
            b = p // library marker kkossev.ctLib, line 534
        } // library marker kkossev.ctLib, line 535
        else if (region == 1) { // library marker kkossev.ctLib, line 536
            r = q // library marker kkossev.ctLib, line 537
            g = 1 // library marker kkossev.ctLib, line 538
            b = p // library marker kkossev.ctLib, line 539
        } // library marker kkossev.ctLib, line 540
        else if (region == 2) { // library marker kkossev.ctLib, line 541
            r = p // library marker kkossev.ctLib, line 542
            g = 1 // library marker kkossev.ctLib, line 543
            b = t // library marker kkossev.ctLib, line 544
        } // library marker kkossev.ctLib, line 545
        else if (region == 3) { // library marker kkossev.ctLib, line 546
            r = p // library marker kkossev.ctLib, line 547
            g = q // library marker kkossev.ctLib, line 548
            b = 1 // library marker kkossev.ctLib, line 549
        } // library marker kkossev.ctLib, line 550
        else if (region == 4) { // library marker kkossev.ctLib, line 551
            r = t // library marker kkossev.ctLib, line 552
            g = p // library marker kkossev.ctLib, line 553
            b = 1 // library marker kkossev.ctLib, line 554
        } // library marker kkossev.ctLib, line 555
        else { // library marker kkossev.ctLib, line 556
            r = 1 // library marker kkossev.ctLib, line 557
            g = p // library marker kkossev.ctLib, line 558
            b = q // library marker kkossev.ctLib, line 559
        } // library marker kkossev.ctLib, line 560
    } // library marker kkossev.ctLib, line 561

    logTrace "< Color RGB: ($r, $g, $b)" // library marker kkossev.ctLib, line 563

    [red: r, green: g, blue: b] // library marker kkossev.ctLib, line 565
} // library marker kkossev.ctLib, line 566

def colorRgb2Hsv(r, g, b) { // library marker kkossev.ctLib, line 568
    logTrace "> Color RGB: ($r, $g, $b)" // library marker kkossev.ctLib, line 569

    def min = min(r, g, b) // library marker kkossev.ctLib, line 571
    def max = max(r, g, b) // library marker kkossev.ctLib, line 572
    def delta = max - min // library marker kkossev.ctLib, line 573

    def h // library marker kkossev.ctLib, line 575
    def s // library marker kkossev.ctLib, line 576
    def v = max // library marker kkossev.ctLib, line 577

    if (delta == 0) { // library marker kkossev.ctLib, line 579
        h = 0 // library marker kkossev.ctLib, line 580
        s = 0 // library marker kkossev.ctLib, line 581
    } // library marker kkossev.ctLib, line 582
    else { // library marker kkossev.ctLib, line 583
        s = delta / max // library marker kkossev.ctLib, line 584
        if (r == max) h = ( g - b ) / delta            // between yellow & magenta // library marker kkossev.ctLib, line 585
        else if (g == max) h = 2 + ( b - r ) / delta    // between cyan & yellow // library marker kkossev.ctLib, line 586
        else h = 4 + ( r - g ) / delta                // between magenta & cyan // library marker kkossev.ctLib, line 587
        h /= 6 // library marker kkossev.ctLib, line 588

        if (h < 0) h += 1 // library marker kkossev.ctLib, line 590
    } // library marker kkossev.ctLib, line 591

    logTrace "> Color HSV: ($h, $s, $v)" // library marker kkossev.ctLib, line 593

    return [ hue: h, saturation: s, level: v ] // library marker kkossev.ctLib, line 595
} // library marker kkossev.ctLib, line 596

def iTo8bitHex(value) { // library marker kkossev.ctLib, line 598
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.ctLib, line 599
} // library marker kkossev.ctLib, line 600

// ----------- end of Ivar Holand's "IKEA Tradfri RGBW Light HE v2" driver code ------------ // library marker kkossev.ctLib, line 602


// ~~~~~ end include (175) kkossev.ctLib ~~~~~

// ~~~~~ start include (166) kkossev.energyLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.energyLib, line 1
library( // library marker kkossev.energyLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Energy Library', name: 'energyLib', namespace: 'kkossev', // library marker kkossev.energyLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/energyLib.groovy', documentationLink: '', // library marker kkossev.energyLib, line 4
    version: '3.0.0' // library marker kkossev.energyLib, line 5

) // library marker kkossev.energyLib, line 7
/* // library marker kkossev.energyLib, line 8
 *  Zigbee Energy Library // library marker kkossev.energyLib, line 9
 * // library marker kkossev.energyLib, line 10
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.energyLib, line 11
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.energyLib, line 12
 * // library marker kkossev.energyLib, line 13
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.energyLib, line 14
 * // library marker kkossev.energyLib, line 15
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.energyLib, line 16
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.energyLib, line 17
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.energyLib, line 18
 * // library marker kkossev.energyLib, line 19
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.energyLib, line 20
 * ver. 3.2.0  2024-05-24 kkossev  - CommonLib 3.2.0 allignment // library marker kkossev.energyLib, line 21
 * // library marker kkossev.energyLib, line 22
 *                                   TODO: add energyRefresh()  // library marker kkossev.energyLib, line 23
*/ // library marker kkossev.energyLib, line 24

static String energyLibVersion()   { '3.2.0' } // library marker kkossev.energyLib, line 26
static String energyLibStamp() { '2024/05/24 10:59 PM' } // library marker kkossev.energyLib, line 27

metadata { // library marker kkossev.energyLib, line 29
    capability 'PowerMeter' // library marker kkossev.energyLib, line 30
    capability 'EnergyMeter' // library marker kkossev.energyLib, line 31
    capability 'VoltageMeasurement' // library marker kkossev.energyLib, line 32
    capability 'CurrentMeter' // library marker kkossev.energyLib, line 33
    // no attributes // library marker kkossev.energyLib, line 34
    // no commands // library marker kkossev.energyLib, line 35
    preferences { // library marker kkossev.energyLib, line 36
        // no prefrences // library marker kkossev.energyLib, line 37
    } // library marker kkossev.energyLib, line 38
} // library marker kkossev.energyLib, line 39

@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.energyLib, line 41
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.energyLib, line 42
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.energyLib, line 43
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.energyLib, line 44
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.energyLib, line 45
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.energyLib, line 46
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.energyLib, line 47
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.energyLib, line 48
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.energyLib, line 49
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.energyLib, line 50
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.energyLib, line 51
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.energyLib, line 52


void sendVoltageEvent(BigDecimal voltage, boolean isDigital=false) { // library marker kkossev.energyLib, line 55
    Map map = [:] // library marker kkossev.energyLib, line 56
    map.name = 'voltage' // library marker kkossev.energyLib, line 57
    map.value = voltage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 58
    map.unit = 'V' // library marker kkossev.energyLib, line 59
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 60
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 61
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 62
    final BigDecimal lastVoltage = device.currentValue('voltage') ?: 0.0 // library marker kkossev.energyLib, line 63
    final BigDecimal  voltageThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 64
    if (Math.abs(voltage - lastVoltage) >= voltageThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 65
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 66
        sendEvent(map) // library marker kkossev.energyLib, line 67
    } // library marker kkossev.energyLib, line 68
    else { // library marker kkossev.energyLib, line 69
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastVoltage} is less than ${voltageThreshold} V)" // library marker kkossev.energyLib, line 70
    } // library marker kkossev.energyLib, line 71
} // library marker kkossev.energyLib, line 72

void sendAmperageEvent(BigDecimal amperage, boolean isDigital=false) { // library marker kkossev.energyLib, line 74
    Map map = [:] // library marker kkossev.energyLib, line 75
    map.name = 'amperage' // library marker kkossev.energyLib, line 76
    map.value = amperage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 77
    map.unit = 'A' // library marker kkossev.energyLib, line 78
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 79
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 80
    if (state.states.isRefresh  == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 81
    final BigDecimal lastAmperage = device.currentValue('amperage') ?: 0.0 // library marker kkossev.energyLib, line 82
    final BigDecimal amperageThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 83
    if (Math.abs(amperage - lastAmperage ) >= amperageThreshold || state.states.isRefresh  == true) { // library marker kkossev.energyLib, line 84
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 85
        sendEvent(map) // library marker kkossev.energyLib, line 86
    } // library marker kkossev.energyLib, line 87
    else { // library marker kkossev.energyLib, line 88
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastAmperage} is less than ${amperageThreshold} mA)" // library marker kkossev.energyLib, line 89
    } // library marker kkossev.energyLib, line 90
} // library marker kkossev.energyLib, line 91

void sendPowerEvent(BigDecimal power, boolean isDigital=false) { // library marker kkossev.energyLib, line 93
    Map map = [:] // library marker kkossev.energyLib, line 94
    map.name = 'power' // library marker kkossev.energyLib, line 95
    map.value = power.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 96
    map.unit = 'W' // library marker kkossev.energyLib, line 97
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 98
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 99
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 100
    final BigDecimal lastPower = device.currentValue('power') ?: 0.0 // library marker kkossev.energyLib, line 101
    final BigDecimal powerThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 102
    if (power  > MAX_POWER_LIMIT) { // library marker kkossev.energyLib, line 103
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (exceeds maximum power cap ${MAX_POWER_LIMIT} W)" // library marker kkossev.energyLib, line 104
        return // library marker kkossev.energyLib, line 105
    } // library marker kkossev.energyLib, line 106
    if (Math.abs(power - lastPower ) >= powerThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 107
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 108
        sendEvent(map) // library marker kkossev.energyLib, line 109
    } // library marker kkossev.energyLib, line 110
    else { // library marker kkossev.energyLib, line 111
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastPower} is less than ${powerThreshold} W)" // library marker kkossev.energyLib, line 112
    } // library marker kkossev.energyLib, line 113
} // library marker kkossev.energyLib, line 114

void sendFrequencyEvent(BigDecimal frequency, boolean isDigital=false) { // library marker kkossev.energyLib, line 116
    Map map = [:] // library marker kkossev.energyLib, line 117
    map.name = 'frequency' // library marker kkossev.energyLib, line 118
    map.value = frequency.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 119
    map.unit = 'Hz' // library marker kkossev.energyLib, line 120
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 121
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 122
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 123
    final BigDecimal lastFrequency = device.currentValue('frequency') ?: 0.0 // library marker kkossev.energyLib, line 124
    final BigDecimal frequencyThreshold = 0.1 // library marker kkossev.energyLib, line 125
    if (Math.abs(frequency - lastFrequency) >= frequencyThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 126
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 127
        sendEvent(map) // library marker kkossev.energyLib, line 128
    } // library marker kkossev.energyLib, line 129
    else { // library marker kkossev.energyLib, line 130
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${frequencyThreshold} Hz)" // library marker kkossev.energyLib, line 131
    } // library marker kkossev.energyLib, line 132
} // library marker kkossev.energyLib, line 133

void sendPowerFactorEvent(BigDecimal pf, boolean isDigital=false) { // library marker kkossev.energyLib, line 135
    Map map = [:] // library marker kkossev.energyLib, line 136
    map.name = 'powerFactor' // library marker kkossev.energyLib, line 137
    map.value = pf.setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 138
    map.unit = '%' // library marker kkossev.energyLib, line 139
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 140
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 141
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 142
    final BigDecimal lastPF = device.currentValue('powerFactor') ?: 0.0 // library marker kkossev.energyLib, line 143
    final BigDecimal powerFactorThreshold = 0.01 // library marker kkossev.energyLib, line 144
    if (Math.abs(pf - lastPF) >= powerFactorThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 145
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 146
        sendEvent(map) // library marker kkossev.energyLib, line 147
    } // library marker kkossev.energyLib, line 148
    else { // library marker kkossev.energyLib, line 149
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${powerFactorThreshold} %)" // library marker kkossev.energyLib, line 150
    } // library marker kkossev.energyLib, line 151
} // library marker kkossev.energyLib, line 152

void standardParseElectricalMeasureCluster(Map descMap) { // library marker kkossev.energyLib, line 154
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.energyLib, line 155
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.energyLib, line 156
    logDebug "standardParseElectricalMeasureCluster: (0x0B04)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}" // library marker kkossev.energyLib, line 157
} // library marker kkossev.energyLib, line 158

void standardParseMeteringCluster(Map descMap) { // library marker kkossev.energyLib, line 160
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.energyLib, line 161
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.energyLib, line 162
    logDebug "standardParseElectricalMeasureCluster: (0x0702)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}" // library marker kkossev.energyLib, line 163
} // library marker kkossev.energyLib, line 164

// ~~~~~ end include (166) kkossev.energyLib ~~~~~

// ~~~~~ start include (169) kkossev.groupsLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.groupsLib, line 1
library( // library marker kkossev.groupsLib, line 2
    base: 'driver', // library marker kkossev.groupsLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.groupsLib, line 4
    category: 'zigbee', // library marker kkossev.groupsLib, line 5
    description: 'Zigbee Groups Library', // library marker kkossev.groupsLib, line 6
    name: 'groupsLib', // library marker kkossev.groupsLib, line 7
    namespace: 'kkossev', // library marker kkossev.groupsLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/groupsLib.groovy', // library marker kkossev.groupsLib, line 9
    version: '3.0.1', // library marker kkossev.groupsLib, line 10
    documentationLink: '' // library marker kkossev.groupsLib, line 11
) // library marker kkossev.groupsLib, line 12
/* // library marker kkossev.groupsLib, line 13
 *  Zigbee Groups Library // library marker kkossev.groupsLib, line 14
 * // library marker kkossev.groupsLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.groupsLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.groupsLib, line 17
 * // library marker kkossev.groupsLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.groupsLib, line 19
 * // library marker kkossev.groupsLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.groupsLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.groupsLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.groupsLib, line 23
 * // library marker kkossev.groupsLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added groupsLib.groovy // library marker kkossev.groupsLib, line 25
 * ver. 3.0.1  2024-04-14 kkossev  - groupsInitializeVars() groupsRefresh() // library marker kkossev.groupsLib, line 26
 * // library marker kkossev.groupsLib, line 27
 *                                   TODO: // library marker kkossev.groupsLib, line 28
*/ // library marker kkossev.groupsLib, line 29

static String groupsLibVersion()   { '3.0.1' } // library marker kkossev.groupsLib, line 31
static String groupsLibStamp() { '2024/04/15 7:09 AM' } // library marker kkossev.groupsLib, line 32

metadata { // library marker kkossev.groupsLib, line 34
    // no capabilities // library marker kkossev.groupsLib, line 35
    // no attributes // library marker kkossev.groupsLib, line 36
    command 'zigbeeGroups', [ // library marker kkossev.groupsLib, line 37
        [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.groupsLib, line 38
        [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.groupsLib, line 39
    ] // library marker kkossev.groupsLib, line 40

    preferences { // library marker kkossev.groupsLib, line 42
        // no prefrences // library marker kkossev.groupsLib, line 43
    } // library marker kkossev.groupsLib, line 44
} // library marker kkossev.groupsLib, line 45

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.groupsLib, line 47
    defaultValue: 0, // library marker kkossev.groupsLib, line 48
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.groupsLib, line 49
] // library marker kkossev.groupsLib, line 50
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.groupsLib, line 51
    defaultValue: 0, // library marker kkossev.groupsLib, line 52
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.groupsLib, line 53
] // library marker kkossev.groupsLib, line 54

/* // library marker kkossev.groupsLib, line 56
 * ----------------------------------------------------------------------------- // library marker kkossev.groupsLib, line 57
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.groupsLib, line 58
 * ----------------------------------------------------------------------------- // library marker kkossev.groupsLib, line 59
*/ // library marker kkossev.groupsLib, line 60
void customParseGroupsCluster(final Map descMap) { // library marker kkossev.groupsLib, line 61
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.groupsLib, line 62
    logDebug "customParseGroupsCluster: customParseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.groupsLib, line 63
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 64
    switch (descMap.command as Integer) { // library marker kkossev.groupsLib, line 65
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.groupsLib, line 66
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 67
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 68
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 69
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 70
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 71
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 72
                logWarn "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.groupsLib, line 73
            } // library marker kkossev.groupsLib, line 74
            else { // library marker kkossev.groupsLib, line 75
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.groupsLib, line 76
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.groupsLib, line 77
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.groupsLib, line 78
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.groupsLib, line 79
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.groupsLib, line 80
                        logDebug "customParseGroupsCluster: Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.groupsLib, line 81
                        return // library marker kkossev.groupsLib, line 82
                    } // library marker kkossev.groupsLib, line 83
                } // library marker kkossev.groupsLib, line 84
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.groupsLib, line 85
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.groupsLib, line 86
                state.zigbeeGroups['groups'].sort() // library marker kkossev.groupsLib, line 87
            } // library marker kkossev.groupsLib, line 88
            break // library marker kkossev.groupsLib, line 89
        case 0x01: // View group // library marker kkossev.groupsLib, line 90
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.groupsLib, line 91
            logDebug "customParseGroupsCluster: received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 92
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 93
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 94
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 95
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 96
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 97
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 98
                logWarn "customParseGroupsCluster: zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.groupsLib, line 99
            } // library marker kkossev.groupsLib, line 100
            else { // library marker kkossev.groupsLib, line 101
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.groupsLib, line 102
            } // library marker kkossev.groupsLib, line 103
            break // library marker kkossev.groupsLib, line 104
        case 0x02: // Get group membership // library marker kkossev.groupsLib, line 105
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 106
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 107
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.groupsLib, line 108
            final Set<String> groups = [] // library marker kkossev.groupsLib, line 109
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.groupsLib, line 110
                int pos = (i * 2) + 2 // library marker kkossev.groupsLib, line 111
                String group = data[pos + 1] + data[pos] // library marker kkossev.groupsLib, line 112
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.groupsLib, line 113
            } // library marker kkossev.groupsLib, line 114
            state.zigbeeGroups['groups'] = groups // library marker kkossev.groupsLib, line 115
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.groupsLib, line 116
            logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.groupsLib, line 117
            break // library marker kkossev.groupsLib, line 118
        case 0x03: // Remove group // library marker kkossev.groupsLib, line 119
            logInfo "customParseGroupsCluster: received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 120
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 121
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 122
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 123
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 124
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 125
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 126
                logWarn "customParseGroupsCluster: zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.groupsLib, line 127
            } // library marker kkossev.groupsLib, line 128
            else { // library marker kkossev.groupsLib, line 129
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.groupsLib, line 130
            } // library marker kkossev.groupsLib, line 131
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.groupsLib, line 132
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.groupsLib, line 133
            if (index >= 0) { // library marker kkossev.groupsLib, line 134
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.groupsLib, line 135
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.groupsLib, line 136
            } // library marker kkossev.groupsLib, line 137
            break // library marker kkossev.groupsLib, line 138
        case 0x04: //Remove all groups // library marker kkossev.groupsLib, line 139
            logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.groupsLib, line 140
            logWarn 'customParseGroupsCluster: not implemented!' // library marker kkossev.groupsLib, line 141
            break // library marker kkossev.groupsLib, line 142
        case 0x05: // Add group if identifying // library marker kkossev.groupsLib, line 143
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.groupsLib, line 144
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.groupsLib, line 145
            logWarn 'customParseGroupsCluster: not implemented!' // library marker kkossev.groupsLib, line 146
            break // library marker kkossev.groupsLib, line 147
        default: // library marker kkossev.groupsLib, line 148
            logWarn "customParseGroupsCluster: received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 149
            break // library marker kkossev.groupsLib, line 150
    } // library marker kkossev.groupsLib, line 151
} // library marker kkossev.groupsLib, line 152

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 154
List<String> addGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 155
    List<String> cmds = [] // library marker kkossev.groupsLib, line 156
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 157
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.groupsLib, line 158
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.groupsLib, line 159
        return [] // library marker kkossev.groupsLib, line 160
    } // library marker kkossev.groupsLib, line 161
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 162
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 163
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 164
    return cmds // library marker kkossev.groupsLib, line 165
} // library marker kkossev.groupsLib, line 166

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 168
List<String> viewGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 169
    List<String> cmds = [] // library marker kkossev.groupsLib, line 170
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 171
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 172
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 173
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 174
    return cmds // library marker kkossev.groupsLib, line 175
} // library marker kkossev.groupsLib, line 176

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 178
List<String> getGroupMembership(dummy) { // library marker kkossev.groupsLib, line 179
    List<String> cmds = [] // library marker kkossev.groupsLib, line 180
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.groupsLib, line 181
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 182
    return cmds // library marker kkossev.groupsLib, line 183
} // library marker kkossev.groupsLib, line 184

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 186
List<String> removeGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 187
    List<String> cmds = [] // library marker kkossev.groupsLib, line 188
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 189
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.groupsLib, line 190
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.groupsLib, line 191
        return [] // library marker kkossev.groupsLib, line 192
    } // library marker kkossev.groupsLib, line 193
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 194
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 195
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 196
    return cmds // library marker kkossev.groupsLib, line 197
} // library marker kkossev.groupsLib, line 198

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 200
List<String> removeAllGroups(groupNr) { // library marker kkossev.groupsLib, line 201
    List<String> cmds = [] // library marker kkossev.groupsLib, line 202
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 203
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 204
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 205
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 206
    return cmds // library marker kkossev.groupsLib, line 207
} // library marker kkossev.groupsLib, line 208

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 210
List<String> notImplementedGroups(groupNr) { // library marker kkossev.groupsLib, line 211
    List<String> cmds = [] // library marker kkossev.groupsLib, line 212
    //final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 213
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 214
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 215
    return cmds // library marker kkossev.groupsLib, line 216
} // library marker kkossev.groupsLib, line 217

@Field static final Map GroupCommandsMap = [ // library marker kkossev.groupsLib, line 219
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.groupsLib, line 220
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.groupsLib, line 221
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.groupsLib, line 222
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.groupsLib, line 223
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.groupsLib, line 224
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.groupsLib, line 225
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.groupsLib, line 226
] // library marker kkossev.groupsLib, line 227

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 229
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.groupsLib, line 230
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.groupsLib, line 231
    List<String> cmds = [] // library marker kkossev.groupsLib, line 232
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 233
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.groupsLib, line 234
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.groupsLib, line 235
    def value // library marker kkossev.groupsLib, line 236
    Boolean validated = false // library marker kkossev.groupsLib, line 237
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.groupsLib, line 238
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.groupsLib, line 239
        return // library marker kkossev.groupsLib, line 240
    } // library marker kkossev.groupsLib, line 241
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.groupsLib, line 242
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.groupsLib, line 243
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.groupsLib, line 244
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.groupsLib, line 245
        return // library marker kkossev.groupsLib, line 246
    } // library marker kkossev.groupsLib, line 247
    // // library marker kkossev.groupsLib, line 248
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.groupsLib, line 249
    def func // library marker kkossev.groupsLib, line 250
    try { // library marker kkossev.groupsLib, line 251
        func = GroupCommandsMap[command]?.function // library marker kkossev.groupsLib, line 252
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.groupsLib, line 253
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.groupsLib, line 254
        cmds = "$func"(value) // library marker kkossev.groupsLib, line 255
    } // library marker kkossev.groupsLib, line 256
    catch (e) { // library marker kkossev.groupsLib, line 257
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.groupsLib, line 258
        return // library marker kkossev.groupsLib, line 259
    } // library marker kkossev.groupsLib, line 260

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.groupsLib, line 262
    sendZigbeeCommands(cmds) // library marker kkossev.groupsLib, line 263
} // library marker kkossev.groupsLib, line 264

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 266
void groupCommandsHelp(val) { // library marker kkossev.groupsLib, line 267
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.groupsLib, line 268
} // library marker kkossev.groupsLib, line 269

List<String> groupsRefresh() { // library marker kkossev.groupsLib, line 271
    logDebug 'groupsRefresh()' // library marker kkossev.groupsLib, line 272
    return getGroupMembership(null) // library marker kkossev.groupsLib, line 273
} // library marker kkossev.groupsLib, line 274

void groupsInitializeVars(boolean fullInit = false) { // library marker kkossev.groupsLib, line 276
    logDebug "groupsInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.groupsLib, line 277
    if (fullInit || state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 278
} // library marker kkossev.groupsLib, line 279

// ~~~~~ end include (169) kkossev.groupsLib ~~~~~

// ~~~~~ start include (173) kkossev.humidityLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.humidityLib, line 1
library( // library marker kkossev.humidityLib, line 2
    base: 'driver', // library marker kkossev.humidityLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.humidityLib, line 4
    category: 'zigbee', // library marker kkossev.humidityLib, line 5
    description: 'Zigbee Humidity Library', // library marker kkossev.humidityLib, line 6
    name: 'humidityLib', // library marker kkossev.humidityLib, line 7
    namespace: 'kkossev', // library marker kkossev.humidityLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/humidityLib.groovy', // library marker kkossev.humidityLib, line 9
    version: '3.0.0', // library marker kkossev.humidityLib, line 10
    documentationLink: '' // library marker kkossev.humidityLib, line 11
) // library marker kkossev.humidityLib, line 12
/* // library marker kkossev.humidityLib, line 13
 *  Zigbee Humidity Library // library marker kkossev.humidityLib, line 14
 * // library marker kkossev.humidityLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.humidityLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.humidityLib, line 17
 * // library marker kkossev.humidityLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.humidityLib, line 19
 * // library marker kkossev.humidityLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.humidityLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.humidityLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.humidityLib, line 23
 * // library marker kkossev.humidityLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added humidityLib.groovy // library marker kkossev.humidityLib, line 25
 * // library marker kkossev.humidityLib, line 26
 *                                   TODO: // library marker kkossev.humidityLib, line 27
*/ // library marker kkossev.humidityLib, line 28

static String humidityLibVersion()   { '3.0.0' } // library marker kkossev.humidityLib, line 30
static String humidityLibStamp() { '2024/04/06 11:49 PM' } // library marker kkossev.humidityLib, line 31

metadata { // library marker kkossev.humidityLib, line 33
    capability 'RelativeHumidityMeasurement' // library marker kkossev.humidityLib, line 34
    // no commands // library marker kkossev.humidityLib, line 35
    preferences { // library marker kkossev.humidityLib, line 36
        if (device) { // library marker kkossev.humidityLib, line 37
            if (settings?.minReportingTime == null) { // library marker kkossev.humidityLib, line 38
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.humidityLib, line 39
            } // library marker kkossev.humidityLib, line 40
            if (settings?.minReportingTime == null) { // library marker kkossev.humidityLib, line 41
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.humidityLib, line 42
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.humidityLib, line 43
                } // library marker kkossev.humidityLib, line 44
            } // library marker kkossev.humidityLib, line 45
        } // library marker kkossev.humidityLib, line 46
    } // library marker kkossev.humidityLib, line 47
} // library marker kkossev.humidityLib, line 48

void customParseHumidityCluster(final Map descMap) { // library marker kkossev.humidityLib, line 50
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.humidityLib, line 51
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.humidityLib, line 52
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.humidityLib, line 53
} // library marker kkossev.humidityLib, line 54

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.humidityLib, line 56
    Map eventMap = [:] // library marker kkossev.humidityLib, line 57
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.humidityLib, line 58
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.humidityLib, line 59
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.humidityLib, line 60
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.humidityLib, line 61
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.humidityLib, line 62
        return // library marker kkossev.humidityLib, line 63
    } // library marker kkossev.humidityLib, line 64
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.humidityLib, line 65
    eventMap.name = 'humidity' // library marker kkossev.humidityLib, line 66
    eventMap.unit = '% RH' // library marker kkossev.humidityLib, line 67
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.humidityLib, line 68
    //eventMap.isStateChange = true // library marker kkossev.humidityLib, line 69
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.humidityLib, line 70
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.humidityLib, line 71
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.humidityLib, line 72
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.humidityLib, line 73
    if (timeElapsed >= minTime) { // library marker kkossev.humidityLib, line 74
        logInfo "${eventMap.descriptionText}" // library marker kkossev.humidityLib, line 75
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.humidityLib, line 76
        state.lastRx['humiTime'] = now() // library marker kkossev.humidityLib, line 77
        sendEvent(eventMap) // library marker kkossev.humidityLib, line 78
    } // library marker kkossev.humidityLib, line 79
    else { // library marker kkossev.humidityLib, line 80
        eventMap.type = 'delayed' // library marker kkossev.humidityLib, line 81
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.humidityLib, line 82
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.humidityLib, line 83
    } // library marker kkossev.humidityLib, line 84
} // library marker kkossev.humidityLib, line 85

void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.humidityLib, line 87
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.humidityLib, line 88
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.humidityLib, line 89
    sendEvent(eventMap) // library marker kkossev.humidityLib, line 90
} // library marker kkossev.humidityLib, line 91

List<String> humidityLibInitializeDevice() { // library marker kkossev.humidityLib, line 93
    List<String> cmds = [] // library marker kkossev.humidityLib, line 94
    cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.humidityLib, line 95
    return cmds // library marker kkossev.humidityLib, line 96
} // library marker kkossev.humidityLib, line 97

// ~~~~~ end include (173) kkossev.humidityLib ~~~~~

// ~~~~~ start include (178) kkossev.iasLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.iasLib, line 1
library( // library marker kkossev.iasLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee IASLibrary', name: 'iasLib', namespace: 'kkossev', // library marker kkossev.iasLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/iasLib.groovy', documentationLink: '', // library marker kkossev.iasLib, line 4
    version: '3.2.0' // library marker kkossev.iasLib, line 5

) // library marker kkossev.iasLib, line 7
/* // library marker kkossev.iasLib, line 8
 *  Zigbee IAS Library // library marker kkossev.iasLib, line 9
 * // library marker kkossev.iasLib, line 10
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.iasLib, line 11
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.iasLib, line 12
 * // library marker kkossev.iasLib, line 13
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.iasLib, line 14
 * // library marker kkossev.iasLib, line 15
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.iasLib, line 16
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.iasLib, line 17
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.iasLib, line 18
 * // library marker kkossev.iasLib, line 19
 * ver. 3.2.0  2024-05-27 kkossev  - added iasLib.groovy // library marker kkossev.iasLib, line 20
 * // library marker kkossev.iasLib, line 21
 *                                   TODO: // library marker kkossev.iasLib, line 22
*/ // library marker kkossev.iasLib, line 23

static String iasLibVersion()   { '3.2.0' } // library marker kkossev.iasLib, line 25
static String iasLibStamp() { '2024/05/27 10:13 PM' } // library marker kkossev.iasLib, line 26

metadata { // library marker kkossev.iasLib, line 28
    // no capabilities // library marker kkossev.iasLib, line 29
    // no attributes // library marker kkossev.iasLib, line 30
    // no commands // library marker kkossev.iasLib, line 31
    preferences { // library marker kkossev.iasLib, line 32
    // no prefrences // library marker kkossev.iasLib, line 33
    } // library marker kkossev.iasLib, line 34
} // library marker kkossev.iasLib, line 35

@Field static final Map<Integer, String> IAS_ATTRIBUTES = [ // library marker kkossev.iasLib, line 37
    //  Zone Information // library marker kkossev.iasLib, line 38
    0x0000: 'zone state', // library marker kkossev.iasLib, line 39
    0x0001: 'zone type', // library marker kkossev.iasLib, line 40
    0x0002: 'zone status', // library marker kkossev.iasLib, line 41
    //  Zone Settings // library marker kkossev.iasLib, line 42
    0x0010: 'CIE addr',    // EUI64 // library marker kkossev.iasLib, line 43
    0x0011: 'Zone Id',     // uint8 // library marker kkossev.iasLib, line 44
    0x0012: 'Num zone sensitivity levels supported',     // uint8 // library marker kkossev.iasLib, line 45
    0x0013: 'Current zone sensitivity level',            // uint8 // library marker kkossev.iasLib, line 46
    0xF001: 'Current zone keep time'                     // uint8 // library marker kkossev.iasLib, line 47
] // library marker kkossev.iasLib, line 48

@Field static final Map<Integer, String> ZONE_TYPE = [ // library marker kkossev.iasLib, line 50
    0x0000: 'Standard CIE', // library marker kkossev.iasLib, line 51
    0x000D: 'Motion Sensor', // library marker kkossev.iasLib, line 52
    0x0015: 'Contact Switch', // library marker kkossev.iasLib, line 53
    0x0028: 'Fire Sensor', // library marker kkossev.iasLib, line 54
    0x002A: 'Water Sensor', // library marker kkossev.iasLib, line 55
    0x002B: 'Carbon Monoxide Sensor', // library marker kkossev.iasLib, line 56
    0x002C: 'Personal Emergency Device', // library marker kkossev.iasLib, line 57
    0x002D: 'Vibration Movement Sensor', // library marker kkossev.iasLib, line 58
    0x010F: 'Remote Control', // library marker kkossev.iasLib, line 59
    0x0115: 'Key Fob', // library marker kkossev.iasLib, line 60
    0x021D: 'Key Pad', // library marker kkossev.iasLib, line 61
    0x0225: 'Standard Warning Device', // library marker kkossev.iasLib, line 62
    0x0226: 'Glass Break Sensor', // library marker kkossev.iasLib, line 63
    0x0229: 'Security Repeater', // library marker kkossev.iasLib, line 64
    0xFFFF: 'Invalid Zone Type' // library marker kkossev.iasLib, line 65
] // library marker kkossev.iasLib, line 66

@Field static final Map<Integer, String> ZONE_STATE = [ // library marker kkossev.iasLib, line 68
    0x00: 'Not Enrolled', // library marker kkossev.iasLib, line 69
    0x01: 'Enrolled' // library marker kkossev.iasLib, line 70
] // library marker kkossev.iasLib, line 71

public void standardParseIASCluster(final Map descMap) { // library marker kkossev.iasLib, line 73
    if (descMap.cluster != '0500') { return } // not IAS cluster // library marker kkossev.iasLib, line 74
    if (descMap.attrInt == null) { return } // missing attribute // library marker kkossev.iasLib, line 75
    String zoneSetting = IAS_ATTRIBUTES[descMap.attrInt] // library marker kkossev.iasLib, line 76
    if ( IAS_ATTRIBUTES[descMap.attrInt] == null ) { // library marker kkossev.iasLib, line 77
        logWarn "standardParseIASCluster: Unknown IAS attribute ${descMap?.attrId} (value:${descMap?.value})" // library marker kkossev.iasLib, line 78
        return  // library marker kkossev.iasLib, line 79
    } // unknown IAS attribute // library marker kkossev.iasLib, line 80
    logDebug "standardParseIASCluster: Don't know how to handle IAS attribute 0x${descMap?.attrId} '${zoneSetting}' (value:${descMap?.value})!" // library marker kkossev.iasLib, line 81
    return // library marker kkossev.iasLib, line 82
/* // library marker kkossev.iasLib, line 83
    String clusterInfo = 'standardParseIASCluster:' // library marker kkossev.iasLib, line 84

    if (descMap?.cluster == '0500' && descMap?.command in ['01', '0A']) {    //IAS read attribute response // library marker kkossev.iasLib, line 86
        logDebug "${standardParseIASCluster} IAS read attribute ${descMap?.attrId} response is ${descMap?.value}" // library marker kkossev.iasLib, line 87
        if (descMap?.attrId == '0000') { // library marker kkossev.iasLib, line 88
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 89
            logInfo "${clusterInfo} IAS Zone State repot is '${ZONE_STATE[value]}' (${value})" // library marker kkossev.iasLib, line 90
            } else if (descMap?.attrId == '0001') { // library marker kkossev.iasLib, line 91
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 92
            logInfo "${clusterInfo} IAS Zone Type repot is '${ZONE_TYPE[value]}' (${value})" // library marker kkossev.iasLib, line 93
            } else if (descMap?.attrId == '0002') { // library marker kkossev.iasLib, line 94
            logDebug "${clusterInfo} IAS Zone status repoted: descMap=${descMap} value= ${Integer.parseInt(descMap?.value, 16)}" // library marker kkossev.iasLib, line 95
            handleMotion(Integer.parseInt(descMap?.value, 16) ? true : false) // library marker kkossev.iasLib, line 96
            } else if (descMap?.attrId == '0010') { // library marker kkossev.iasLib, line 97
            logDebug "${clusterInfo} IAS Zone Address received (bitmap = ${descMap?.value})" // library marker kkossev.iasLib, line 98
            } else if (descMap?.attrId == '0011') { // library marker kkossev.iasLib, line 99
            logDebug "${clusterInfo} IAS Zone ID: ${descMap.value}" // library marker kkossev.iasLib, line 100
            } else if (descMap?.attrId == '0012') { // library marker kkossev.iasLib, line 101
            logDebug "${clusterInfo} IAS Num zone sensitivity levels supported: ${descMap.value}" // library marker kkossev.iasLib, line 102
            } else if (descMap?.attrId == '0013') { // library marker kkossev.iasLib, line 103
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 104
            //logInfo "${clusterInfo} IAS Current Zone Sensitivity Level = ${sensitivityOpts.options[value]} (${value})" // library marker kkossev.iasLib, line 105
            logInfo "${clusterInfo} IAS Current Zone Sensitivity Level = (${value})" // library marker kkossev.iasLib, line 106
        // device.updateSetting('settings.sensitivity', [value:value.toString(), type:'enum']) // library marker kkossev.iasLib, line 107
        } // library marker kkossev.iasLib, line 108
            else if (descMap?.attrId == 'F001') {    // [raw:7CC50105000801F02000, dni:7CC5, endpoint:01, cluster:0500, size:08, attrId:F001, encoding:20, command:0A, value:00, clusterInt:1280, attrInt:61441] // library marker kkossev.iasLib, line 109
            int value = Integer.parseInt(descMap?.value, 16) // library marker kkossev.iasLib, line 110
            //String str   = getKeepTimeOpts().options[value] // library marker kkossev.iasLib, line 111
            //logInfo "${clusterInfo} Current IAS Zone Keep-Time =  ${str} (${value})" // library marker kkossev.iasLib, line 112
            logInfo "${clusterInfo} Current IAS Zone Keep-Time =  (${value})" // library marker kkossev.iasLib, line 113
            //device.updateSetting('keepTime', [value: value.toString(), type: 'enum']) // library marker kkossev.iasLib, line 114
            } // library marker kkossev.iasLib, line 115
            else { // library marker kkossev.iasLib, line 116
            logDebug "${clusterInfo} Zone status attribute ${descMap?.attrId}: NOT PROCESSED ${descMap}" // library marker kkossev.iasLib, line 117
            } // library marker kkossev.iasLib, line 118
        } // if IAS read attribute response // library marker kkossev.iasLib, line 119
        else if (descMap?.clusterId == '0500' && descMap?.command == '04') {    //write attribute response (IAS) // library marker kkossev.iasLib, line 120
        logDebug "${clusterInfo} AS write attribute response is ${descMap?.data[0] == '00' ? 'success' : '<b>FAILURE</b>'}" // library marker kkossev.iasLib, line 121
        } // library marker kkossev.iasLib, line 122
        else { // library marker kkossev.iasLib, line 123
        logDebug "${clusterInfo} NOT PROCESSED ${descMap}" // library marker kkossev.iasLib, line 124
        } // library marker kkossev.iasLib, line 125
*/         // library marker kkossev.iasLib, line 126
} // library marker kkossev.iasLib, line 127

// ~~~~~ end include (178) kkossev.iasLib ~~~~~

// ~~~~~ start include (168) kkossev.illuminanceLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.illuminanceLib, line 1
library( // library marker kkossev.illuminanceLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Illuminance Library', name: 'illuminanceLib', namespace: 'kkossev', // library marker kkossev.illuminanceLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy', documentationLink: '', // library marker kkossev.illuminanceLib, line 4
    version: '3.2.0' // library marker kkossev.illuminanceLib, line 5

) // library marker kkossev.illuminanceLib, line 7
/* // library marker kkossev.illuminanceLib, line 8
 *  Zigbee Illuminance Library // library marker kkossev.illuminanceLib, line 9
 * // library marker kkossev.illuminanceLib, line 10
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.illuminanceLib, line 11
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.illuminanceLib, line 12
 * // library marker kkossev.illuminanceLib, line 13
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.illuminanceLib, line 14
 * // library marker kkossev.illuminanceLib, line 15
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.illuminanceLib, line 16
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.illuminanceLib, line 17
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.illuminanceLib, line 18
 * // library marker kkossev.illuminanceLib, line 19
 * ver. 3.0.0  2024-04-06 kkossev  - added illuminanceLib.groovy // library marker kkossev.illuminanceLib, line 20
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added capability 'IlluminanceMeasurement'; added illuminanceRefresh() // library marker kkossev.illuminanceLib, line 21
 * // library marker kkossev.illuminanceLib, line 22
 *                                   TODO: illum threshold not working! // library marker kkossev.illuminanceLib, line 23
 *                                   TODO: check illuminanceInitializeVars() and illuminanceProcessTuyaDP() usage // library marker kkossev.illuminanceLib, line 24
*/ // library marker kkossev.illuminanceLib, line 25

static String illuminanceLibVersion()   { '3.2.0' } // library marker kkossev.illuminanceLib, line 27
static String illuminanceLibStamp() { '2024/05/28 1:33 PM' } // library marker kkossev.illuminanceLib, line 28

metadata { // library marker kkossev.illuminanceLib, line 30
    capability 'IlluminanceMeasurement' // library marker kkossev.illuminanceLib, line 31
    // no attributes // library marker kkossev.illuminanceLib, line 32
    // no commands // library marker kkossev.illuminanceLib, line 33
    preferences { // library marker kkossev.illuminanceLib, line 34
        // no prefrences // library marker kkossev.illuminanceLib, line 35
    } // library marker kkossev.illuminanceLib, line 36
} // library marker kkossev.illuminanceLib, line 37

@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 10 // library marker kkossev.illuminanceLib, line 39

void standardParseIlluminanceCluster(final Map descMap) { // library marker kkossev.illuminanceLib, line 41
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.illuminanceLib, line 42
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.illuminanceLib, line 43
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.illuminanceLib, line 44
    handleIlluminanceEvent(lux) // library marker kkossev.illuminanceLib, line 45
} // library marker kkossev.illuminanceLib, line 46

void handleIlluminanceEvent(int illuminance, boolean isDigital=false) { // library marker kkossev.illuminanceLib, line 48
    Map eventMap = [:] // library marker kkossev.illuminanceLib, line 49
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.illuminanceLib, line 50
    eventMap.name = 'illuminance' // library marker kkossev.illuminanceLib, line 51
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.illuminanceLib, line 52
    eventMap.value  = illumCorrected // library marker kkossev.illuminanceLib, line 53
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.illuminanceLib, line 54
    eventMap.unit = 'lx' // library marker kkossev.illuminanceLib, line 55
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.illuminanceLib, line 56
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.illuminanceLib, line 57
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.illuminanceLib, line 58
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.illuminanceLib, line 59
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.illuminanceLib, line 60
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.illuminanceLib, line 61
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.illuminanceLib, line 62
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.illuminanceLib, line 63
        return // library marker kkossev.illuminanceLib, line 64
    } // library marker kkossev.illuminanceLib, line 65
    if (timeElapsed >= minTime) { // library marker kkossev.illuminanceLib, line 66
        logInfo "${eventMap.descriptionText}" // library marker kkossev.illuminanceLib, line 67
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.illuminanceLib, line 68
        state.lastRx['illumTime'] = now() // library marker kkossev.illuminanceLib, line 69
        sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 70
    } // library marker kkossev.illuminanceLib, line 71
    else {         // queue the event // library marker kkossev.illuminanceLib, line 72
        eventMap.type = 'delayed' // library marker kkossev.illuminanceLib, line 73
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.illuminanceLib, line 74
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.illuminanceLib, line 75
    } // library marker kkossev.illuminanceLib, line 76
} // library marker kkossev.illuminanceLib, line 77

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.illuminanceLib, line 79
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.illuminanceLib, line 80
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.illuminanceLib, line 81
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.illuminanceLib, line 82
    sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 83
} // library marker kkossev.illuminanceLib, line 84

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.illuminanceLib, line 86

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.illuminanceLib, line 88
void illuminanceProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.illuminanceLib, line 89
    switch (dp) { // library marker kkossev.illuminanceLib, line 90
        case 0x01 : // on/off // library marker kkossev.illuminanceLib, line 91
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 92
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.illuminanceLib, line 93
            } // library marker kkossev.illuminanceLib, line 94
            else { // library marker kkossev.illuminanceLib, line 95
                sendSwitchEvent(fncmd) // library marker kkossev.illuminanceLib, line 96
            } // library marker kkossev.illuminanceLib, line 97
            break // library marker kkossev.illuminanceLib, line 98
        case 0x02 : // library marker kkossev.illuminanceLib, line 99
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 100
                handleIlluminanceEvent(fncmd) // library marker kkossev.illuminanceLib, line 101
            } // library marker kkossev.illuminanceLib, line 102
            else { // library marker kkossev.illuminanceLib, line 103
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 104
            } // library marker kkossev.illuminanceLib, line 105
            break // library marker kkossev.illuminanceLib, line 106
        case 0x04 : // battery // library marker kkossev.illuminanceLib, line 107
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.illuminanceLib, line 108
            break // library marker kkossev.illuminanceLib, line 109
        default : // library marker kkossev.illuminanceLib, line 110
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 111
            break // library marker kkossev.illuminanceLib, line 112
    } // library marker kkossev.illuminanceLib, line 113
} // library marker kkossev.illuminanceLib, line 114

void illuminanceInitializeVars( boolean fullInit = false ) { // library marker kkossev.illuminanceLib, line 116
    logDebug "customInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.illuminanceLib, line 117
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 118
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 119
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 120
    } // library marker kkossev.illuminanceLib, line 121
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 122
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.illuminanceLib, line 123
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.illuminanceLib, line 124
    } // library marker kkossev.illuminanceLib, line 125
} // library marker kkossev.illuminanceLib, line 126

List<String> illuminanceRefresh() { // library marker kkossev.illuminanceLib, line 128
    List<String> cmds = [] // library marker kkossev.illuminanceLib, line 129
    cmds = zigbee.readAttribute(0x0400, 0x0000, [:], delay = 200) // illuminance // library marker kkossev.illuminanceLib, line 130
    return cmds // library marker kkossev.illuminanceLib, line 131
} // library marker kkossev.illuminanceLib, line 132

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~

// ~~~~~ start include (170) kkossev.levelLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.levelLib, line 1
library( // library marker kkossev.levelLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Level Library', name: 'levelLib', namespace: 'kkossev', // library marker kkossev.levelLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/levelLib.groovy', documentationLink: '', // library marker kkossev.levelLib, line 4
    version: '3.2.0' // library marker kkossev.levelLib, line 5
) // library marker kkossev.levelLib, line 6
/* // library marker kkossev.levelLib, line 7
 *  Zigbee Level Library // library marker kkossev.levelLib, line 8
 * // library marker kkossev.levelLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.levelLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.levelLib, line 11
 * // library marker kkossev.levelLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.levelLib, line 13
 * // library marker kkossev.levelLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.levelLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.levelLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.levelLib, line 17
 * // library marker kkossev.levelLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added levelLib.groovy // library marker kkossev.levelLib, line 19
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment // library marker kkossev.levelLib, line 20
 * // library marker kkossev.levelLib, line 21
 *                                   TODO: // library marker kkossev.levelLib, line 22
*/ // library marker kkossev.levelLib, line 23

static String levelLibVersion()   { '3.2.0' } // library marker kkossev.levelLib, line 25
static String levelLibStamp() { '2024/05/28 12:33 PM' } // library marker kkossev.levelLib, line 26

metadata { // library marker kkossev.levelLib, line 28
    capability 'Switch'             // TODO - move to a new library // library marker kkossev.levelLib, line 29
    capability 'Switch Level'       // Attributes: level - NUMBER, unit:%; Commands:setLevel(level, duration) level required (NUMBER) - Level to set (0 to 100); duration optional (NUMBER) - Transition duration in seconds // library marker kkossev.levelLib, line 30
    capability 'ChangeLevel'        // Commands : startLevelChange(direction);  direction required (ENUM) - Direction for level change request; stopLevelChange() // library marker kkossev.levelLib, line 31
    // no attributes // library marker kkossev.levelLib, line 32
    // no commands // library marker kkossev.levelLib, line 33
    preferences { // library marker kkossev.levelLib, line 34
        if (device != null && DEVICE_TYPE != 'Device') {         // library marker kkossev.levelLib, line 35
            input name: 'levelUpTransition', type: 'enum', title: '<b>Dim up transition length</b>', options: TransitionOpts.options, defaultValue: TransitionOpts.defaultValue, required: true, description: '<i>Changes the speed the light dims up. Increasing the value slows down the transition.</i>' // library marker kkossev.levelLib, line 36
            input name: 'levelDownTransition', type: 'enum', title: '<b>Dim down transition length</b>', options: TransitionOpts.options, defaultValue: TransitionOpts.defaultValue, required: true, description: '<i>Changes the speed the light dims down. Increasing the value slows down the transition.</i>' // library marker kkossev.levelLib, line 37
            input name: 'levelChangeRate', type: 'enum', title: '<b>Level change rate</b>', options: LevelRateOpts.options, defaultValue: LevelRateOpts.defaultValue, required: true, description: '<i>Changes the speed that the light changes when using <b>start level change</b> until <b>stop level change</b> is sent.</i>' // library marker kkossev.levelLib, line 38
        } // library marker kkossev.levelLib, line 39
    } // library marker kkossev.levelLib, line 40
} // library marker kkossev.levelLib, line 41

import groovy.transform.Field // library marker kkossev.levelLib, line 43

@Field static final Map TransitionOpts = [ // library marker kkossev.levelLib, line 45
    defaultValue: 0x0004, // library marker kkossev.levelLib, line 46
    options: [ // library marker kkossev.levelLib, line 47
        0x0000: 'No Delay', // library marker kkossev.levelLib, line 48
        0x0002: '200ms', // library marker kkossev.levelLib, line 49
        0x0004: '400ms', // library marker kkossev.levelLib, line 50
        0x000A: '1s', // library marker kkossev.levelLib, line 51
        0x000F: '1.5s', // library marker kkossev.levelLib, line 52
        0x0014: '2s', // library marker kkossev.levelLib, line 53
        0x001E: '3s', // library marker kkossev.levelLib, line 54
        0x0028: '4s', // library marker kkossev.levelLib, line 55
        0x0032: '5s', // library marker kkossev.levelLib, line 56
        0x0064: '10s' // library marker kkossev.levelLib, line 57
    ] // library marker kkossev.levelLib, line 58
] // library marker kkossev.levelLib, line 59

@Field static final Map LevelRateOpts = [ // library marker kkossev.levelLib, line 61
    defaultValue: 0x64, // library marker kkossev.levelLib, line 62
    options: [ 0xFF: 'Device Default', 0x16: 'Very Slow', 0x32: 'Slow', 0x64: 'Medium', 0x96: 'Medium Fast', 0xC8: 'Fast' ] // library marker kkossev.levelLib, line 63
] // library marker kkossev.levelLib, line 64


/* // library marker kkossev.levelLib, line 67
 * ----------------------------------------------------------------------------- // library marker kkossev.levelLib, line 68
 * Level Control Cluster            0x0008 // library marker kkossev.levelLib, line 69
 * ----------------------------------------------------------------------------- // library marker kkossev.levelLib, line 70
*/ // library marker kkossev.levelLib, line 71
void standardParseLevelControlCluster(final Map descMap) { // library marker kkossev.levelLib, line 72
    logDebug "standardParseLevelControlCluster: 0x${descMap.value}" // library marker kkossev.levelLib, line 73
    if (descMap.attrId == '0000') { // library marker kkossev.levelLib, line 74
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "standardParseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.levelLib, line 75
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.levelLib, line 76
        // Aqara LED Strip T1 sends the level in the range 0..255 // library marker kkossev.levelLib, line 77
        int scaledValue = ((rawValue as double) / 2.55F + 0.5) as int // library marker kkossev.levelLib, line 78
        sendLevelControlEvent(scaledValue) // library marker kkossev.levelLib, line 79
    } // library marker kkossev.levelLib, line 80
    else { // library marker kkossev.levelLib, line 81
        logWarn "standardParseLevelControlCluster: unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.levelLib, line 82
    } // library marker kkossev.levelLib, line 83
} // library marker kkossev.levelLib, line 84

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.levelLib, line 86
    int value = rawValue as int // library marker kkossev.levelLib, line 87
    if (value < 0) { value = 0 } // library marker kkossev.levelLib, line 88
    if (value > 100) { value = 100 } // library marker kkossev.levelLib, line 89
    Map map = [:] // library marker kkossev.levelLib, line 90

    boolean isDigital = state.states['isDigital'] // library marker kkossev.levelLib, line 92
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.levelLib, line 93

    map.name = 'level' // library marker kkossev.levelLib, line 95
    map.value = value // library marker kkossev.levelLib, line 96
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.levelLib, line 97
    if (isRefresh == true) { // library marker kkossev.levelLib, line 98
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.levelLib, line 99
        map.isStateChange = true // library marker kkossev.levelLib, line 100
    } // library marker kkossev.levelLib, line 101
    else { // library marker kkossev.levelLib, line 102
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.levelLib, line 103
    } // library marker kkossev.levelLib, line 104
    logInfo "${map.descriptionText}" // library marker kkossev.levelLib, line 105
    sendEvent(map) // library marker kkossev.levelLib, line 106
    clearIsDigital() // library marker kkossev.levelLib, line 107
} // library marker kkossev.levelLib, line 108

/** // library marker kkossev.levelLib, line 110
 * Set Level Command // library marker kkossev.levelLib, line 111
 * @param value level percent (0-100) // library marker kkossev.levelLib, line 112
 * @param transitionTime transition time in seconds // library marker kkossev.levelLib, line 113
 * @return List of zigbee commands // library marker kkossev.levelLib, line 114
 */ // library marker kkossev.levelLib, line 115
void setLevel(final BigDecimal value, final BigDecimal transitionTime = null) { // library marker kkossev.levelLib, line 116
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.levelLib, line 117
/*     // library marker kkossev.levelLib, line 118
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.levelLib, line 119
        logDebug "calling customSetLevel: ${value}, ${transitionTime}" // library marker kkossev.levelLib, line 120
        customSetLevel(value.intValue(), transitionTime.intValue()) // library marker kkossev.levelLib, line 121
        return // library marker kkossev.levelLib, line 122
    } // library marker kkossev.levelLib, line 123
*/     // library marker kkossev.levelLib, line 124
    //if (DEVICE_TYPE in  ['Bulb']) {  // library marker kkossev.levelLib, line 125
    setLevelBulb(value.intValue(), transitionTime ? transitionTime.intValue() : null) // library marker kkossev.levelLib, line 126
    return  // library marker kkossev.levelLib, line 127
    //} // library marker kkossev.levelLib, line 128
    /* // library marker kkossev.levelLib, line 129
    final Integer rate = getLevelTransitionRate(value.intValue(), transitionTime.intValue()) // library marker kkossev.levelLib, line 130
    scheduleCommandTimeoutCheck() // library marker kkossev.levelLib, line 131
    sendZigbeeCommands(setLevelPrivate(value.intValue(), rate as int)) // library marker kkossev.levelLib, line 132
    */ // library marker kkossev.levelLib, line 133
} // library marker kkossev.levelLib, line 134

void setLevelBulb(value, rate=null) { // library marker kkossev.levelLib, line 136
    logDebug "setLevelBulb: $value, $rate" // library marker kkossev.levelLib, line 137

    state.pendingLevelChange = value // library marker kkossev.levelLib, line 139
    if (state.cmds == null) { // library marker kkossev.levelLib, line 140
        state.cmds = [] // library marker kkossev.levelLib, line 141
    } // library marker kkossev.levelLib, line 142
    if (rate == null) { // library marker kkossev.levelLib, line 143
        state.cmds += zigbee.setLevel(value) // library marker kkossev.levelLib, line 144
    } else { // library marker kkossev.levelLib, line 145
        state.cmds += zigbee.setLevel(value, rate * 10) // library marker kkossev.levelLib, line 146
    } // library marker kkossev.levelLib, line 147

    unschedule(sendLevelZigbeeCommandsDelayed) // library marker kkossev.levelLib, line 149
    runInMillis(100, sendLevelZigbeeCommandsDelayed) // library marker kkossev.levelLib, line 150
} // library marker kkossev.levelLib, line 151

void sendLevelZigbeeCommandsDelayed() { // library marker kkossev.levelLib, line 153
    List cmds = state.cmds // library marker kkossev.levelLib, line 154
    if (cmds != null) { // library marker kkossev.levelLib, line 155
        state.cmds = [] // library marker kkossev.levelLib, line 156
        sendZigbeeCommands(cmds) // library marker kkossev.levelLib, line 157
    } // library marker kkossev.levelLib, line 158
} // library marker kkossev.levelLib, line 159



/** // library marker kkossev.levelLib, line 163
 * Send 'switchLevel' attribute event // library marker kkossev.levelLib, line 164
 * @param isOn true if light is on, false otherwise // library marker kkossev.levelLib, line 165
 * @param level brightness level (0-254) // library marker kkossev.levelLib, line 166
 */ // library marker kkossev.levelLib, line 167
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.levelLib, line 168
private List<String> setLevelPrivate(final BigDecimal value, final int rate = 0, final int delay = 0, final Boolean levelPreset = false) { // library marker kkossev.levelLib, line 169
    List<String> cmds = [] // library marker kkossev.levelLib, line 170
    final Integer level = constrain(value) // library marker kkossev.levelLib, line 171
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.levelLib, line 172
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.levelLib, line 173
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.levelLib, line 174
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.levelLib, line 175
        // If light is off, first go to level 0 then to desired level // library marker kkossev.levelLib, line 176
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.levelLib, line 177
    } // library marker kkossev.levelLib, line 178
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.levelLib, line 179
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.levelLib, line 180
    /* // library marker kkossev.levelLib, line 181
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.levelLib, line 182
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.levelLib, line 183
    */ // library marker kkossev.levelLib, line 184
    int duration = 10            // TODO !!! // library marker kkossev.levelLib, line 185
    String endpointId = '01'     // TODO !!! // library marker kkossev.levelLib, line 186
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.levelLib, line 187

    return cmds // library marker kkossev.levelLib, line 189
} // library marker kkossev.levelLib, line 190


/** // library marker kkossev.levelLib, line 193
 * Get the level transition rate // library marker kkossev.levelLib, line 194
 * @param level desired target level (0-100) // library marker kkossev.levelLib, line 195
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.levelLib, line 196
 * @return transition rate in 1/10ths of a second // library marker kkossev.levelLib, line 197
 */ // library marker kkossev.levelLib, line 198
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.levelLib, line 199
    int rate = 0 // library marker kkossev.levelLib, line 200
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.levelLib, line 201
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.levelLib, line 202
    if (!isOn) { // library marker kkossev.levelLib, line 203
        currentLevel = 0 // library marker kkossev.levelLib, line 204
    } // library marker kkossev.levelLib, line 205
    // Check if 'transitionTime' has a value // library marker kkossev.levelLib, line 206
    if (transitionTime > 0) { // library marker kkossev.levelLib, line 207
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.levelLib, line 208
        rate = transitionTime * 10 // library marker kkossev.levelLib, line 209
    } else { // library marker kkossev.levelLib, line 210
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.levelLib, line 211
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.levelLib, line 212
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.levelLib, line 213
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.levelLib, line 214
        } // library marker kkossev.levelLib, line 215
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.levelLib, line 216
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.levelLib, line 217
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.levelLib, line 218
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.levelLib, line 219
        } // library marker kkossev.levelLib, line 220
    } // library marker kkossev.levelLib, line 221
    logDebug "using level transition rate ${rate}" // library marker kkossev.levelLib, line 222
    return rate // library marker kkossev.levelLib, line 223
} // library marker kkossev.levelLib, line 224

List<String> startLevelChange(String direction) { // library marker kkossev.levelLib, line 226
    if (settings.txtEnable) { log.info "startLevelChange (${direction})" } // library marker kkossev.levelLib, line 227
    String upDown = direction == 'down' ? '01' : '00' // library marker kkossev.levelLib, line 228
    String rateHex = intToHexStr(settings.levelChangeRate as Integer) // library marker kkossev.levelLib, line 229
    scheduleCommandTimeoutCheck() // library marker kkossev.levelLib, line 230
    return zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x05, [:], 0, "${upDown} ${rateHex}") // library marker kkossev.levelLib, line 231
} // library marker kkossev.levelLib, line 232


List<String> stopLevelChange() { // library marker kkossev.levelLib, line 235
    if (settings.txtEnable) { log.info 'stopLevelChange' } // library marker kkossev.levelLib, line 236
    scheduleCommandTimeoutCheck() // library marker kkossev.levelLib, line 237
    return zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x03, [:], 0) + // library marker kkossev.levelLib, line 238
        ifPolling { zigbee.levelRefresh(0) + zigbee.onOffRefresh(0) } // library marker kkossev.levelLib, line 239
} // library marker kkossev.levelLib, line 240


// Delay before reading attribute (when using polling) // library marker kkossev.levelLib, line 243
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.levelLib, line 244
// Command option that enable changes when off // library marker kkossev.levelLib, line 245
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.levelLib, line 246

/** // library marker kkossev.levelLib, line 248
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.levelLib, line 249
 * @param delayMs delay in milliseconds // library marker kkossev.levelLib, line 250
 * @param commands commands to execute // library marker kkossev.levelLib, line 251
 * @return list of commands to be sent to the device // library marker kkossev.levelLib, line 252
 */ // library marker kkossev.levelLib, line 253
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.levelLib, line 254
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.levelLib, line 255
    if (state.reportingEnabled == false) { // library marker kkossev.levelLib, line 256
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.levelLib, line 257
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.levelLib, line 258
    } // library marker kkossev.levelLib, line 259
    return [] // library marker kkossev.levelLib, line 260
} // library marker kkossev.levelLib, line 261

/** // library marker kkossev.levelLib, line 263
 * Constrain a value to a range // library marker kkossev.levelLib, line 264
 * @param value value to constrain // library marker kkossev.levelLib, line 265
 * @param min minimum value (default 0) // library marker kkossev.levelLib, line 266
 * @param max maximum value (default 100) // library marker kkossev.levelLib, line 267
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.levelLib, line 268
 */ // library marker kkossev.levelLib, line 269
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.levelLib, line 270
    if (min == null || max == null) { // library marker kkossev.levelLib, line 271
        return value // library marker kkossev.levelLib, line 272
    } // library marker kkossev.levelLib, line 273
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.levelLib, line 274
} // library marker kkossev.levelLib, line 275

/** // library marker kkossev.levelLib, line 277
 * Constrain a value to a range // library marker kkossev.levelLib, line 278
 * @param value value to constrain // library marker kkossev.levelLib, line 279
 * @param min minimum value (default 0) // library marker kkossev.levelLib, line 280
 * @param max maximum value (default 100) // library marker kkossev.levelLib, line 281
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.levelLib, line 282
 */ // library marker kkossev.levelLib, line 283
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.levelLib, line 284
    if (min == null || max == null) { // library marker kkossev.levelLib, line 285
        return value as Integer // library marker kkossev.levelLib, line 286
    } // library marker kkossev.levelLib, line 287
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.levelLib, line 288
} // library marker kkossev.levelLib, line 289

void updatedLevel() { // library marker kkossev.levelLib, line 291
    logDebug "updatedLevel: ${device.currentValue('level')}" // library marker kkossev.levelLib, line 292
} // library marker kkossev.levelLib, line 293

List<String> levelRefresh() { // library marker kkossev.levelLib, line 295
    List<String> cmds = zigbee.onOffRefresh(100) + zigbee.levelRefresh(101) // library marker kkossev.levelLib, line 296
    return cmds // library marker kkossev.levelLib, line 297
} // library marker kkossev.levelLib, line 298

// ~~~~~ end include (170) kkossev.levelLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.0' // library marker kkossev.onOffLib, line 5
) // library marker kkossev.onOffLib, line 6
/* // library marker kkossev.onOffLib, line 7
 *  Zigbee OnOff Cluster Library // library marker kkossev.onOffLib, line 8
 * // library marker kkossev.onOffLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.onOffLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.onOffLib, line 11
 * // library marker kkossev.onOffLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.onOffLib, line 13
 * // library marker kkossev.onOffLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.onOffLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.onOffLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.onOffLib, line 17
 * // library marker kkossev.onOffLib, line 18
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment // library marker kkossev.onOffLib, line 19
 * // library marker kkossev.onOffLib, line 20
 *                                   TODO: // library marker kkossev.onOffLib, line 21
*/ // library marker kkossev.onOffLib, line 22

static String onOffLibVersion()   { '3.2.0' } // library marker kkossev.onOffLib, line 24
static String onOffLibStamp() { '2024/05/28 10:44 AM' } // library marker kkossev.onOffLib, line 25

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 27

metadata { // library marker kkossev.onOffLib, line 29
    capability 'Actuator' // library marker kkossev.onOffLib, line 30
    capability 'Switch' // library marker kkossev.onOffLib, line 31
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 32
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 33
    } // library marker kkossev.onOffLib, line 34
    // no commands // library marker kkossev.onOffLib, line 35
    preferences { // library marker kkossev.onOffLib, line 36
        if (settings?.advancedOptions == true && device != null && DEVICE_TYPE != 'Device') { // library marker kkossev.onOffLib, line 37
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: '<i>Some switches and plugs send periodically the switch status as a heart-beet </i>', defaultValue: true) // library marker kkossev.onOffLib, line 38
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: '<i>Disable switching OFF for plugs that must be always On</i>', defaultValue: false) // library marker kkossev.onOffLib, line 39
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 40
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.onOffLib, line 41
            } // library marker kkossev.onOffLib, line 42
        } // library marker kkossev.onOffLib, line 43
    } // library marker kkossev.onOffLib, line 44
} // library marker kkossev.onOffLib, line 45

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 47
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 48
] // library marker kkossev.onOffLib, line 49

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 51
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 52
] // library marker kkossev.onOffLib, line 53

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 55
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 56
] // library marker kkossev.onOffLib, line 57

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 59

/* // library marker kkossev.onOffLib, line 61
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 62
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 63
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 64
*/ // library marker kkossev.onOffLib, line 65
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 66
    /* // library marker kkossev.onOffLib, line 67
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 68
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 69
    } // library marker kkossev.onOffLib, line 70
    else */ // library marker kkossev.onOffLib, line 71
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 72
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 73
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 74
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 75
    } // library marker kkossev.onOffLib, line 76
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 77
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 78
    } // library marker kkossev.onOffLib, line 79
    else { // library marker kkossev.onOffLib, line 80
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 81
        else { logDebug "standardParseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 82
    } // library marker kkossev.onOffLib, line 83
} // library marker kkossev.onOffLib, line 84

void toggle() { // library marker kkossev.onOffLib, line 86
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 87
    String state = '' // library marker kkossev.onOffLib, line 88
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 89
        state = 'on' // library marker kkossev.onOffLib, line 90
    } // library marker kkossev.onOffLib, line 91
    else { // library marker kkossev.onOffLib, line 92
        state = 'off' // library marker kkossev.onOffLib, line 93
    } // library marker kkossev.onOffLib, line 94
    descriptionText += state // library marker kkossev.onOffLib, line 95
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 96
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 97
} // library marker kkossev.onOffLib, line 98

void off() { // library marker kkossev.onOffLib, line 100
    if (this.respondsTo('customOff')) { // library marker kkossev.onOffLib, line 101
        customOff() // library marker kkossev.onOffLib, line 102
        return // library marker kkossev.onOffLib, line 103
    } // library marker kkossev.onOffLib, line 104
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.onOffLib, line 105
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.onOffLib, line 106
        return // library marker kkossev.onOffLib, line 107
    } // library marker kkossev.onOffLib, line 108
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 109
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 110
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 111
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 112
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 113
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 114
        } // library marker kkossev.onOffLib, line 115
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 116
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 117
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 118
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 119
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 120
    } // library marker kkossev.onOffLib, line 121
    /* // library marker kkossev.onOffLib, line 122
    else { // library marker kkossev.onOffLib, line 123
        if (currentState != 'off') { // library marker kkossev.onOffLib, line 124
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.onOffLib, line 125
        } // library marker kkossev.onOffLib, line 126
        else { // library marker kkossev.onOffLib, line 127
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.onOffLib, line 128
            return // library marker kkossev.onOffLib, line 129
        } // library marker kkossev.onOffLib, line 130
    } // library marker kkossev.onOffLib, line 131
    */ // library marker kkossev.onOffLib, line 132

    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 134
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 135
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 136
} // library marker kkossev.onOffLib, line 137

void on() { // library marker kkossev.onOffLib, line 139
    if (this.respondsTo('customOn')) { // library marker kkossev.onOffLib, line 140
        customOn() // library marker kkossev.onOffLib, line 141
        return // library marker kkossev.onOffLib, line 142
    } // library marker kkossev.onOffLib, line 143
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 144
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 145
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 146
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 147
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 148
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 149
        } // library marker kkossev.onOffLib, line 150
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 151
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 152
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 153
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 154
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 155
    } // library marker kkossev.onOffLib, line 156
    /* // library marker kkossev.onOffLib, line 157
    else { // library marker kkossev.onOffLib, line 158
        if (currentState != 'on') { // library marker kkossev.onOffLib, line 159
            logDebug "Switching ${device.displayName} On" // library marker kkossev.onOffLib, line 160
        } // library marker kkossev.onOffLib, line 161
        else { // library marker kkossev.onOffLib, line 162
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.onOffLib, line 163
            return // library marker kkossev.onOffLib, line 164
        } // library marker kkossev.onOffLib, line 165
    } // library marker kkossev.onOffLib, line 166
    */ // library marker kkossev.onOffLib, line 167
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 168
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 169
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 170
} // library marker kkossev.onOffLib, line 171

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 173
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 174
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 175
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 176
    } // library marker kkossev.onOffLib, line 177
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 178
    Map map = [:] // library marker kkossev.onOffLib, line 179
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 180
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 181
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.onOffLib, line 182
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 183
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 184
        return // library marker kkossev.onOffLib, line 185
    } // library marker kkossev.onOffLib, line 186
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 187
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 188
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 189
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 190
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 191
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 192
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 193
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 194
    } else { // library marker kkossev.onOffLib, line 195
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 196
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 197
    } // library marker kkossev.onOffLib, line 198
    map.name = 'switch' // library marker kkossev.onOffLib, line 199
    map.value = value // library marker kkossev.onOffLib, line 200
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 201
    if (isRefresh) { // library marker kkossev.onOffLib, line 202
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 203
        map.isStateChange = true // library marker kkossev.onOffLib, line 204
    } else { // library marker kkossev.onOffLib, line 205
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 206
    } // library marker kkossev.onOffLib, line 207
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 208
    sendEvent(map) // library marker kkossev.onOffLib, line 209
    clearIsDigital() // library marker kkossev.onOffLib, line 210
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 211
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 212
    } // library marker kkossev.onOffLib, line 213
} // library marker kkossev.onOffLib, line 214

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 216
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 217
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 218
    String mode // library marker kkossev.onOffLib, line 219
    String attrName // library marker kkossev.onOffLib, line 220
    if (it.value == null) { // library marker kkossev.onOffLib, line 221
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 222
        return // library marker kkossev.onOffLib, line 223
    } // library marker kkossev.onOffLib, line 224
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 225
    switch (it.attrId) { // library marker kkossev.onOffLib, line 226
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 227
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 228
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 229
            break // library marker kkossev.onOffLib, line 230
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 231
            attrName = 'On Time' // library marker kkossev.onOffLib, line 232
            mode = value // library marker kkossev.onOffLib, line 233
            break // library marker kkossev.onOffLib, line 234
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 235
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 236
            mode = value // library marker kkossev.onOffLib, line 237
            break // library marker kkossev.onOffLib, line 238
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 239
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 240
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 241
            break // library marker kkossev.onOffLib, line 242
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 243
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 244
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 245
            break // library marker kkossev.onOffLib, line 246
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 247
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 248
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 249
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 250
            } // library marker kkossev.onOffLib, line 251
            else { // library marker kkossev.onOffLib, line 252
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 253
            } // library marker kkossev.onOffLib, line 254
            break // library marker kkossev.onOffLib, line 255
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 256
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 257
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 258
            break // library marker kkossev.onOffLib, line 259
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 260
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 261
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 262
            break // library marker kkossev.onOffLib, line 263
        default : // library marker kkossev.onOffLib, line 264
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 265
            return // library marker kkossev.onOffLib, line 266
    } // library marker kkossev.onOffLib, line 267
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 268
} // library marker kkossev.onOffLib, line 269

List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 271
    logDebug 'onOffRefresh()' // library marker kkossev.onOffLib, line 272
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 273
    return cmds // library marker kkossev.onOffLib, line 274
} // library marker kkossev.onOffLib, line 275

void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 277
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 278
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) } // library marker kkossev.onOffLib, line 279
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 280
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 281
} // library marker kkossev.onOffLib, line 282

// ~~~~~ end include (176) kkossev.onOffLib ~~~~~

// ~~~~~ start include (177) kkossev.reportingLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.reportingLib, line 1
library( // library marker kkossev.reportingLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Reporting Config Library', name: 'reportingLib', namespace: 'kkossev', // library marker kkossev.reportingLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/reportingLib.groovy', documentationLink: '', // library marker kkossev.reportingLib, line 4
    version: '3.0.0' // library marker kkossev.reportingLib, line 5

) // library marker kkossev.reportingLib, line 7
/* // library marker kkossev.reportingLib, line 8
 *  Zigbee Reporting Config Library // library marker kkossev.reportingLib, line 9
 * // library marker kkossev.reportingLib, line 10
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.reportingLib, line 11
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.reportingLib, line 12
 * // library marker kkossev.reportingLib, line 13
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.reportingLib, line 14
 * // library marker kkossev.reportingLib, line 15
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.reportingLib, line 16
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.reportingLib, line 17
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.reportingLib, line 18
 * // library marker kkossev.reportingLib, line 19
 * ver. 3.2.0  2024-05-25 kkossev  - added reportingLib.groovy // library marker kkossev.reportingLib, line 20
 * // library marker kkossev.reportingLib, line 21
 *                                   TODO: // library marker kkossev.reportingLib, line 22
*/ // library marker kkossev.reportingLib, line 23

static String reportingLibVersion()   { '3.2.0' } // library marker kkossev.reportingLib, line 25
static String reportingLibStamp() { '2024/05/25 7:27 AM' } // library marker kkossev.reportingLib, line 26

metadata { // library marker kkossev.reportingLib, line 28
    // no capabilities // library marker kkossev.reportingLib, line 29
    // no attributes // library marker kkossev.reportingLib, line 30
    // no commands // library marker kkossev.reportingLib, line 31
    preferences { // library marker kkossev.reportingLib, line 32
        // no prefrences // library marker kkossev.reportingLib, line 33
    } // library marker kkossev.reportingLib, line 34
} // library marker kkossev.reportingLib, line 35

@Field static final String ONOFF = 'Switch' // library marker kkossev.reportingLib, line 37
@Field static final String POWER = 'Power' // library marker kkossev.reportingLib, line 38
@Field static final String INST_POWER = 'InstPower' // library marker kkossev.reportingLib, line 39
@Field static final String ENERGY = 'Energy' // library marker kkossev.reportingLib, line 40
@Field static final String VOLTAGE = 'Voltage' // library marker kkossev.reportingLib, line 41
@Field static final String AMPERAGE = 'Amperage' // library marker kkossev.reportingLib, line 42
@Field static final String FREQUENCY = 'Frequency' // library marker kkossev.reportingLib, line 43
@Field static final String POWER_FACTOR = 'PowerFactor' // library marker kkossev.reportingLib, line 44

List<String> configureReporting(String operation, String measurement,  String minTime='0', String maxTime='0', String delta='0', boolean sendNow=true ) { // library marker kkossev.reportingLib, line 46
    int intMinTime = safeToInt(minTime) // library marker kkossev.reportingLib, line 47
    int intMaxTime = safeToInt(maxTime) // library marker kkossev.reportingLib, line 48
    int intDelta = safeToInt(delta) // library marker kkossev.reportingLib, line 49
    String epString = state.destinationEP // library marker kkossev.reportingLib, line 50
    int ep = safeToInt(epString) // library marker kkossev.reportingLib, line 51
    if (ep == null || ep == 0) { // library marker kkossev.reportingLib, line 52
        ep = 1 // library marker kkossev.reportingLib, line 53
        epString = '01' // library marker kkossev.reportingLib, line 54
    } // library marker kkossev.reportingLib, line 55

    logDebug "configureReporting operation=${operation}, measurement=${measurement}, minTime=${intMinTime}, maxTime=${intMaxTime}, delta=${intDelta} )" // library marker kkossev.reportingLib, line 57

    List<String> cmds = [] // library marker kkossev.reportingLib, line 59

    switch (measurement) { // library marker kkossev.reportingLib, line 61
        case ONOFF : // library marker kkossev.reportingLib, line 62
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 63
                cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${epString} 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 251', ] // library marker kkossev.reportingLib, line 64
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 ${intMinTime} ${intMaxTime} {}", 'delay 251', ] // library marker kkossev.reportingLib, line 65
            } // library marker kkossev.reportingLib, line 66
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 67
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 65535 65535 {}", 'delay 251', ]    // disable Plug automatic reporting // library marker kkossev.reportingLib, line 68
            } // library marker kkossev.reportingLib, line 69
            cmds +=  zigbee.reportingConfiguration(0x0006, 0x0000, [destEndpoint :ep], 251)    // read it back // library marker kkossev.reportingLib, line 70
            break // library marker kkossev.reportingLib, line 71
        case ENERGY :    // default delta = 1 Wh (0.001 kWh) // library marker kkossev.reportingLib, line 72
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 73
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, intMinTime, intMaxTime, (intDelta * getEnergyDiv() as int)) // library marker kkossev.reportingLib, line 74
            } // library marker kkossev.reportingLib, line 75
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 76
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, 0xFFFF, 0xFFFF, 0x0000)    // disable energy automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 77
            } // library marker kkossev.reportingLib, line 78
            cmds += zigbee.reportingConfiguration(0x0702, 0x0000, [destEndpoint :ep], 252) // library marker kkossev.reportingLib, line 79
            break // library marker kkossev.reportingLib, line 80
        case INST_POWER :        // 0x702:0x400 // library marker kkossev.reportingLib, line 81
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 82
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int)) // library marker kkossev.reportingLib, line 83
            } // library marker kkossev.reportingLib, line 84
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 85
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, 0xFFFF, 0xFFFF, 0x0000)    // disable power automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 86
            } // library marker kkossev.reportingLib, line 87
            cmds += zigbee.reportingConfiguration(0x0702, 0x0400, [destEndpoint :ep], 253) // library marker kkossev.reportingLib, line 88
            break // library marker kkossev.reportingLib, line 89
        case POWER :        // Active power default delta = 1 // library marker kkossev.reportingLib, line 90
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 91
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int) )   // bug fixes in ver  1.6.0 - thanks @guyee // library marker kkossev.reportingLib, line 92
            } // library marker kkossev.reportingLib, line 93
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 94
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, 0xFFFF, 0xFFFF, 0x8000)    // disable power automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 95
            } // library marker kkossev.reportingLib, line 96
            cmds += zigbee.reportingConfiguration(0x0B04, 0x050B, [destEndpoint :ep], 254) // library marker kkossev.reportingLib, line 97
            break // library marker kkossev.reportingLib, line 98
        case VOLTAGE :    // RMS Voltage default delta = 1 // library marker kkossev.reportingLib, line 99
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 100
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getVoltageDiv() as int)) // library marker kkossev.reportingLib, line 101
            } // library marker kkossev.reportingLib, line 102
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 103
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable voltage automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 104
            } // library marker kkossev.reportingLib, line 105
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0505, [destEndpoint :ep], 255) // library marker kkossev.reportingLib, line 106
            break // library marker kkossev.reportingLib, line 107
        case AMPERAGE :    // RMS Current default delta = 100 mA = 0.1 A // library marker kkossev.reportingLib, line 108
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 109
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getCurrentDiv() as int)) // library marker kkossev.reportingLib, line 110
            } // library marker kkossev.reportingLib, line 111
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 112
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable amperage automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 113
            } // library marker kkossev.reportingLib, line 114
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0508, [destEndpoint :ep], 256) // library marker kkossev.reportingLib, line 115
            break // library marker kkossev.reportingLib, line 116
        case FREQUENCY :    // added 03/27/2023 // library marker kkossev.reportingLib, line 117
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 118
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getFrequencyDiv() as int)) // library marker kkossev.reportingLib, line 119
            } // library marker kkossev.reportingLib, line 120
            else if (operation == 'Disable') { // library marker kkossev.reportingLib, line 121
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable frequency automatic reporting - tested with Frient // library marker kkossev.reportingLib, line 122
            } // library marker kkossev.reportingLib, line 123
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0300, [destEndpoint :ep], 257) // library marker kkossev.reportingLib, line 124
            break // library marker kkossev.reportingLib, line 125
        case POWER_FACTOR : // added 03/27/2023 // library marker kkossev.reportingLib, line 126
            if (operation == 'Write') { // library marker kkossev.reportingLib, line 127
                cmds += zigbee.configureReporting(0x0B04, 0x0510,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getPowerFactorDiv() as int)) // library marker kkossev.reportingLib, line 128
            } // library marker kkossev.reportingLib, line 129
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0510, [destEndpoint :ep], 258) // library marker kkossev.reportingLib, line 130
            break // library marker kkossev.reportingLib, line 131
        default : // library marker kkossev.reportingLib, line 132
            break // library marker kkossev.reportingLib, line 133
    } // library marker kkossev.reportingLib, line 134
    if (cmds != null) { // library marker kkossev.reportingLib, line 135
        if (sendNow == true) { // library marker kkossev.reportingLib, line 136
            sendZigbeeCommands(cmds) // library marker kkossev.reportingLib, line 137
        } // library marker kkossev.reportingLib, line 138
        else { // library marker kkossev.reportingLib, line 139
            return cmds // library marker kkossev.reportingLib, line 140
        } // library marker kkossev.reportingLib, line 141
    } // library marker kkossev.reportingLib, line 142
} // library marker kkossev.reportingLib, line 143


// ~~~~~ end include (177) kkossev.reportingLib ~~~~~

// ~~~~~ start include (143) kkossev.rgbLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ElseBlockBraces, IfStatementBraces, Instanceof, LineLength, MethodCount, MethodParameterTypeRequired, MethodReturnTypeRequired, NoDef, ParameterReassignment, PublicMethodsBeforeNonPublicMethods, SpaceAroundOperator, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessarySetter, UnusedPrivateMethod, UnusedVariable, VariableName, VariableTypeRequired */ // library marker kkossev.rgbLib, line 1
library( // library marker kkossev.rgbLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'RGB Library', name: 'rgbLib', namespace: 'kkossev', // library marker kkossev.rgbLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/rgbLib.groovy', documentationLink: '', // library marker kkossev.rgbLib, line 4
    version: '3.2.0' // library marker kkossev.rgbLib, line 5
) // library marker kkossev.rgbLib, line 6
/* // library marker kkossev.rgbLib, line 7
 *  Zigbee Button Dimmer -Library // library marker kkossev.rgbLib, line 8
 * // library marker kkossev.rgbLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.rgbLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.rgbLib, line 11
 * // library marker kkossev.rgbLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.rgbLib, line 13
 * // library marker kkossev.rgbLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.rgbLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.rgbLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.rgbLib, line 17
 * // library marker kkossev.rgbLib, line 18
 *  Credits: Ivar Holand for 'IKEA Tradfri RGBW Light HE v2' driver code // library marker kkossev.rgbLib, line 19
 * // library marker kkossev.rgbLib, line 20
 * ver. 1.0.0  2023-11-06 kkossev  - added rgbLib; musicMode; // library marker kkossev.rgbLib, line 21
 * ver. 1.0.1  2024-04-01 kkossev  - Groovy linting (all disabled) // library marker kkossev.rgbLib, line 22
 * ver. 3.2.0  2024-05-21 kkossev  - (dev.branch) commonLib 3.2.0 allignment // library marker kkossev.rgbLib, line 23
 * // library marker kkossev.rgbLib, line 24
 *                                   TODO: // library marker kkossev.rgbLib, line 25
*/ // library marker kkossev.rgbLib, line 26

def rgbLibVersion()   { '3.2.0' } // library marker kkossev.rgbLib, line 28
def rgbLibStamp() { '2024/05/21 10:06 PM' } // library marker kkossev.rgbLib, line 29

import hubitat.helper.ColorUtils // library marker kkossev.rgbLib, line 31

metadata { // library marker kkossev.rgbLib, line 33
    capability 'Actuator' // library marker kkossev.rgbLib, line 34
    capability 'Color Control' // library marker kkossev.rgbLib, line 35
    capability 'ColorMode' // library marker kkossev.rgbLib, line 36
    capability 'Refresh' // library marker kkossev.rgbLib, line 37
    capability 'Switch' // library marker kkossev.rgbLib, line 38
    capability 'Light' // library marker kkossev.rgbLib, line 39

    preferences { // library marker kkossev.rgbLib, line 41
    } // library marker kkossev.rgbLib, line 42
} // library marker kkossev.rgbLib, line 43


// // library marker kkossev.rgbLib, line 46
// called from customUpdated() in the driver *Aqara_LED_Strip_T1.groovy* // library marker kkossev.rgbLib, line 47
void updatedRGB() { // library marker kkossev.rgbLib, line 48
    logDebug 'updatedBulb()...' // library marker kkossev.rgbLib, line 49
} // library marker kkossev.rgbLib, line 50

def colorControlRefresh() { // library marker kkossev.rgbLib, line 52
    def commands = [] // library marker kkossev.rgbLib, line 53
    commands += zigbee.readAttribute(0x0300, 0x03, [:], 200) // currentColorX // library marker kkossev.rgbLib, line 54
    commands += zigbee.readAttribute(0x0300, 0x04, [:], 201) // currentColorY // library marker kkossev.rgbLib, line 55
    commands // library marker kkossev.rgbLib, line 56
} // library marker kkossev.rgbLib, line 57

def colorControlConfig(min, max, step) { // library marker kkossev.rgbLib, line 59
    def commands = [] // library marker kkossev.rgbLib, line 60
    commands += zigbee.configureReporting(0x0300, 0x03, DataType.UINT16, min, max, step) // currentColorX // library marker kkossev.rgbLib, line 61
    commands += zigbee.configureReporting(0x0300, 0x04, DataType.UINT16, min, max, step) // currentColorY // library marker kkossev.rgbLib, line 62
    commands // library marker kkossev.rgbLib, line 63
} // library marker kkossev.rgbLib, line 64

// called from customRefresh() in the driver *Aqara_LED_Strip_T1.groovy* // library marker kkossev.rgbLib, line 66
List<String> refreshRGB() { // library marker kkossev.rgbLib, line 67
    List<String> cmds = [] // library marker kkossev.rgbLib, line 68
    state.colorChanged = false // library marker kkossev.rgbLib, line 69
    state.colorXReported = false // library marker kkossev.rgbLib, line 70
    state.colorYReported = false // library marker kkossev.rgbLib, line 71
    state.cmds = [] // library marker kkossev.rgbLib, line 72
    cmds =  zigbee.onOffRefresh(200) + zigbee.levelRefresh(201) + colorControlRefresh() // library marker kkossev.rgbLib, line 73
    cmds += zigbee.readAttribute(0x0300, [0x4001, 0x400a, 0x400b, 0x400c, 0x000f], [:], 204)    // colormode and color/capabilities // library marker kkossev.rgbLib, line 74
    cmds += zigbee.readAttribute(0x0008, [0x000f, 0x0010, 0x0011], [:], 204)                    // config/bri/execute_if_off // library marker kkossev.rgbLib, line 75
    cmds += zigbee.readAttribute(0xFCC0, [0x0515, 0x0516, 0x0517], [mfgCode:0x115F], 204)       // config/bri/min & max * startup // library marker kkossev.rgbLib, line 76
    cmds += zigbee.readAttribute(0xFCC0, [0x051B, 0x051c], [mfgCode:0x115F], 204)               // pixel count & musicMode // library marker kkossev.rgbLib, line 77
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.rgbLib, line 78
    logDebug "refreshRGB: ${cmds} " // library marker kkossev.rgbLib, line 79
    return cmds // library marker kkossev.rgbLib, line 80
} // library marker kkossev.rgbLib, line 81

// called from customConfigureDevice() in the driver *Aqara_LED_Strip_T1.groovy* // library marker kkossev.rgbLib, line 83
List<String> configureRGB() { // library marker kkossev.rgbLib, line 84
    List<String> cmds = [] // library marker kkossev.rgbLib, line 85
    logDebug "configureRGB() : ${cmds}" // library marker kkossev.rgbLib, line 86
    cmds = refreshBulb() + zigbee.onOffConfig(0, 300) + zigbee.levelConfig() + colorControlConfig(0, 300, 1) // library marker kkossev.rgbLib, line 87
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.rgbLib, line 88
    return cmds // library marker kkossev.rgbLib, line 89
} // library marker kkossev.rgbLib, line 90

def initializeRGB() { // library marker kkossev.rgbLib, line 92
    List<String> cmds = [] // library marker kkossev.rgbLib, line 93
    logDebug "initializeRGB() : ${cmds}" // library marker kkossev.rgbLib, line 94
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.rgbLib, line 95
    return cmds // library marker kkossev.rgbLib, line 96
} // library marker kkossev.rgbLib, line 97

// called from customInitializeVars in the driver *Aqara_LED_Strip_T1.groovy* // library marker kkossev.rgbLib, line 99
void initVarsRGB(boolean fullInit=false) { // library marker kkossev.rgbLib, line 100
    state.colorChanged = false // library marker kkossev.rgbLib, line 101
    state.colorXReported = false // library marker kkossev.rgbLib, line 102
    state.colorYReported = false // library marker kkossev.rgbLib, line 103
    state.colorX = 0.9999 // library marker kkossev.rgbLib, line 104
    state.colorY = 0.9999 // library marker kkossev.rgbLib, line 105
    state.cmds = [] // library marker kkossev.rgbLib, line 106
    logDebug "initVarsRGB(${fullInit})" // library marker kkossev.rgbLib, line 107
} // library marker kkossev.rgbLib, line 108

// called from customInitializeEvents in the driver *Aqara_LED_Strip_T1.groovy* // library marker kkossev.rgbLib, line 110
void initEventsBulb(boolean fullInit=false) { // library marker kkossev.rgbLib, line 111
    logDebug "initEventsBulb(${fullInit})" // library marker kkossev.rgbLib, line 112
    if ((device.currentState('saturation')?.value == null)) { // library marker kkossev.rgbLib, line 113
        sendEvent(name: 'saturation', value: 0) // library marker kkossev.rgbLib, line 114
    } // library marker kkossev.rgbLib, line 115
    if ((device.currentState('hue')?.value == null)) { // library marker kkossev.rgbLib, line 116
        sendEvent(name: 'hue', value: 0) // library marker kkossev.rgbLib, line 117
    } // library marker kkossev.rgbLib, line 118
    if ((device.currentState('level')?.value == null) || (device.currentState('level')?.value == 0)) { // library marker kkossev.rgbLib, line 119
        sendEvent(name: 'level', value: 100) // library marker kkossev.rgbLib, line 120
    } // library marker kkossev.rgbLib, line 121
} // library marker kkossev.rgbLib, line 122


def testT(par) { // library marker kkossev.rgbLib, line 125
    logWarn "testT(${par})" // library marker kkossev.rgbLib, line 126
} // library marker kkossev.rgbLib, line 127

// ~~~~~ end include (143) kkossev.rgbLib ~~~~~

// ~~~~~ start include (172) kkossev.temperatureLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.temperatureLib, line 1
library( // library marker kkossev.temperatureLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Temperature Library', name: 'temperatureLib', namespace: 'kkossev', // library marker kkossev.temperatureLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/temperatureLib.groovy', documentationLink: '', // library marker kkossev.temperatureLib, line 4
    version: '3.2.0' // library marker kkossev.temperatureLib, line 5
) // library marker kkossev.temperatureLib, line 6
/* // library marker kkossev.temperatureLib, line 7
 *  Zigbee Temperature Library // library marker kkossev.temperatureLib, line 8
 * // library marker kkossev.temperatureLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.temperatureLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.temperatureLib, line 11
 * // library marker kkossev.temperatureLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.temperatureLib, line 13
 * // library marker kkossev.temperatureLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.temperatureLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.temperatureLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.temperatureLib, line 17
 * // library marker kkossev.temperatureLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added temperatureLib.groovy // library marker kkossev.temperatureLib, line 19
 * ver. 3.0.1  2024-04-19 kkossev  - temperature rounding fix // library marker kkossev.temperatureLib, line 20
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added temperatureRefresh() // library marker kkossev.temperatureLib, line 21
 * // library marker kkossev.temperatureLib, line 22
 *                                   TODO: add temperatureOffset // library marker kkossev.temperatureLib, line 23
 *                                   TODO: unschedule('sendDelayedTempEvent') only if needed (add boolean flag to sendDelayedTempEvent()) // library marker kkossev.temperatureLib, line 24
 *                                   TODO: check for negative temperature values in standardParseTemperatureCluster() // library marker kkossev.temperatureLib, line 25
*/ // library marker kkossev.temperatureLib, line 26

static String temperatureLibVersion()   { '3.2.0' } // library marker kkossev.temperatureLib, line 28
static String temperatureLibStamp() { '2024/05/28 1:18 PM' } // library marker kkossev.temperatureLib, line 29

metadata { // library marker kkossev.temperatureLib, line 31
    capability 'TemperatureMeasurement' // library marker kkossev.temperatureLib, line 32
    // no commands // library marker kkossev.temperatureLib, line 33
    preferences { // library marker kkossev.temperatureLib, line 34
        if (device) { // library marker kkossev.temperatureLib, line 35
            if (settings?.minReportingTime == null) { // library marker kkossev.temperatureLib, line 36
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 37
            } // library marker kkossev.temperatureLib, line 38
            if (settings?.minReportingTime == null) { // library marker kkossev.temperatureLib, line 39
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.temperatureLib, line 40
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.temperatureLib, line 41
                } // library marker kkossev.temperatureLib, line 42
            } // library marker kkossev.temperatureLib, line 43
        } // library marker kkossev.temperatureLib, line 44
    } // library marker kkossev.temperatureLib, line 45
} // library marker kkossev.temperatureLib, line 46

void standardParseTemperatureCluster(final Map descMap) { // library marker kkossev.temperatureLib, line 48
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.temperatureLib, line 49
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.temperatureLib, line 50
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.temperatureLib, line 51
} // library marker kkossev.temperatureLib, line 52

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.temperatureLib, line 54
    Map eventMap = [:] // library marker kkossev.temperatureLib, line 55
    BigDecimal temperature = safeToBigDecimal(temperaturePar).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 56
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.temperatureLib, line 57
    eventMap.name = 'temperature' // library marker kkossev.temperatureLib, line 58
    if (location.temperatureScale == 'F') { // library marker kkossev.temperatureLib, line 59
        temperature = ((temperature * 1.8) + 32).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 60
        eventMap.unit = '\u00B0F' // library marker kkossev.temperatureLib, line 61
    } // library marker kkossev.temperatureLib, line 62
    else { // library marker kkossev.temperatureLib, line 63
        eventMap.unit = '\u00B0C' // library marker kkossev.temperatureLib, line 64
    } // library marker kkossev.temperatureLib, line 65
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)).setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 66
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.temperatureLib, line 67
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.temperatureLib, line 68
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.temperatureLib, line 69
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.temperatureLib, line 70
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.temperatureLib, line 71
        return // library marker kkossev.temperatureLib, line 72
    } // library marker kkossev.temperatureLib, line 73
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.temperatureLib, line 74
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.temperatureLib, line 75
    if (state.states['isRefresh'] == true) { // library marker kkossev.temperatureLib, line 76
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.temperatureLib, line 77
        eventMap.isStateChange = true // library marker kkossev.temperatureLib, line 78
    } // library marker kkossev.temperatureLib, line 79
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.temperatureLib, line 80
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.temperatureLib, line 81
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.temperatureLib, line 82
    if (timeElapsed >= minTime) { // library marker kkossev.temperatureLib, line 83
        logInfo "${eventMap.descriptionText}" // library marker kkossev.temperatureLib, line 84
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.temperatureLib, line 85
        state.lastRx['tempTime'] = now() // library marker kkossev.temperatureLib, line 86
        sendEvent(eventMap) // library marker kkossev.temperatureLib, line 87
    } // library marker kkossev.temperatureLib, line 88
    else {         // queue the event // library marker kkossev.temperatureLib, line 89
        eventMap.type = 'delayed' // library marker kkossev.temperatureLib, line 90
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.temperatureLib, line 91
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.temperatureLib, line 92
    } // library marker kkossev.temperatureLib, line 93
} // library marker kkossev.temperatureLib, line 94

void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.temperatureLib, line 96
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.temperatureLib, line 97
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.temperatureLib, line 98
    sendEvent(eventMap) // library marker kkossev.temperatureLib, line 99
} // library marker kkossev.temperatureLib, line 100

List<String> temperatureLibInitializeDevice() { // library marker kkossev.temperatureLib, line 102
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 103
    cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.temperatureLib, line 104
    return cmds // library marker kkossev.temperatureLib, line 105
} // library marker kkossev.temperatureLib, line 106

List<String> temperatureRefresh() { // library marker kkossev.temperatureLib, line 108
    List<String> cmds = [] // library marker kkossev.temperatureLib, line 109
    cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.temperatureLib, line 110
    return cmds // library marker kkossev.temperatureLib, line 111
} // library marker kkossev.temperatureLib, line 112

// ~~~~~ end include (172) kkossev.temperatureLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NestedBlockDepth, NoDouble, NoFloat, NoWildcardImports, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.deviceProfileLib, line 1
library( // library marker kkossev.deviceProfileLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Device Profile Library', name: 'deviceProfileLib', namespace: 'kkossev', // library marker kkossev.deviceProfileLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy', documentationLink: '', // library marker kkossev.deviceProfileLib, line 4
    version: '3.1.3' // library marker kkossev.deviceProfileLib, line 5
) // library marker kkossev.deviceProfileLib, line 6
/* // library marker kkossev.deviceProfileLib, line 7
 *  Device Profile Library // library marker kkossev.deviceProfileLib, line 8
 * // library marker kkossev.deviceProfileLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLib, line 11
 * // library marker kkossev.deviceProfileLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLib, line 13
 * // library marker kkossev.deviceProfileLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLib, line 17
 * // library marker kkossev.deviceProfileLib, line 18
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLib, line 19
 * ver. 3.0.0  2023-11-27 kkossev  - (dev. branch) fixes for use with commonLib; added processClusterAttributeFromDeviceProfile() method; added validateAndFixPreferences() method;  inputIt bug fix; signedInt Preproc method; // library marker kkossev.deviceProfileLib, line 20
 * ver. 3.0.1  2023-12-02 kkossev  - (dev. branch) release candidate // library marker kkossev.deviceProfileLib, line 21
 * ver. 3.0.2  2023-12-17 kkossev  - (dev. branch) inputIt moved to the preferences section; setfunction replaced by customSetFunction; Groovy Linting; // library marker kkossev.deviceProfileLib, line 22
 * ver. 3.0.4  2024-03-30 kkossev  - (dev. branch) more Groovy Linting; processClusterAttributeFromDeviceProfile exception fix; // library marker kkossev.deviceProfileLib, line 23
 * ver. 3.1.0  2024-04-03 kkossev  - (dev. branch) more Groovy Linting; deviceProfilesV3, enum pars bug fix; // library marker kkossev.deviceProfileLib, line 24
 * ver. 3.1.1  2024-04-21 kkossev  - (dev. branch) deviceProfilesV3 bug fix; tuyaDPs list of maps bug fix; resetPreferencesToDefaults bug fix; // library marker kkossev.deviceProfileLib, line 25
 * ver. 3.1.2  2024-05-05 kkossev  - (dev. branch) added isSpammyDeviceProfile() // library marker kkossev.deviceProfileLib, line 26
 * ver. 3.1.3  2024-05-21 kkossev  - skip processClusterAttributeFromDeviceProfile if cluster or attribute or value is missing // library marker kkossev.deviceProfileLib, line 27
 * ver. 3.2.0  2024-05-25 kkossev  - commonLib 3.2.0 allignment; // library marker kkossev.deviceProfileLib, line 28
 * ver. 3.2.1  2024-05-27 kkossev  - (dev. branch) Tuya Multi Sensor 4 In 1 (V3) driver allignment (customProcessDeviceProfileEvent) // library marker kkossev.deviceProfileLib, line 29
 * // library marker kkossev.deviceProfileLib, line 30
 *                                   TODO - add defaults for profileId:'0104', endpointId:'01', inClusters, outClusters, in the deviceProfilesV3 map // library marker kkossev.deviceProfileLib, line 31
 *                                   TODO - updateStateUnknownDPs !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 32
 *                                   TODO - check why the forcedProfile preference is not initialized? // library marker kkossev.deviceProfileLib, line 33
 *                                   TODO - when [refresh], send Info logs for parameters that are not events or preferences // library marker kkossev.deviceProfileLib, line 34
 *                                   TODO: refactor sendAttribute ! sendAttribute exception bug fix for virtual devices; check if String getObjectClassName(Object o) is in 2.3.3.137, can be used? // library marker kkossev.deviceProfileLib, line 35
 *                                   TODO: handle preferences of a type TEXT // library marker kkossev.deviceProfileLib, line 36
 * // library marker kkossev.deviceProfileLib, line 37
*/ // library marker kkossev.deviceProfileLib, line 38

static String deviceProfileLibVersion()   { '3.2.1' } // library marker kkossev.deviceProfileLib, line 40
static String deviceProfileLibStamp() { '2024/05/27 10:13 PM' } // library marker kkossev.deviceProfileLib, line 41
import groovy.json.* // library marker kkossev.deviceProfileLib, line 42
import groovy.transform.Field // library marker kkossev.deviceProfileLib, line 43
import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.deviceProfileLib, line 44
import hubitat.zigbee.zcl.DataType // library marker kkossev.deviceProfileLib, line 45
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.deviceProfileLib, line 46

import groovy.transform.CompileStatic // library marker kkossev.deviceProfileLib, line 48

metadata { // library marker kkossev.deviceProfileLib, line 50
    // no capabilities // library marker kkossev.deviceProfileLib, line 51
    // no attributes // library marker kkossev.deviceProfileLib, line 52
    command 'sendCommand', [ // library marker kkossev.deviceProfileLib, line 53
        [name:'command', type: 'STRING', description: 'command name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 54
        [name:'val',     type: 'STRING', description: 'command parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 55
    ] // library marker kkossev.deviceProfileLib, line 56
    command 'setPar', [ // library marker kkossev.deviceProfileLib, line 57
            [name:'par', type: 'STRING', description: 'preference parameter name', constraints: ['STRING']], // library marker kkossev.deviceProfileLib, line 58
            [name:'val', type: 'STRING', description: 'preference parameter value', constraints: ['STRING']] // library marker kkossev.deviceProfileLib, line 59
    ] // library marker kkossev.deviceProfileLib, line 60

    preferences { // library marker kkossev.deviceProfileLib, line 62
        if (device) { // library marker kkossev.deviceProfileLib, line 63
            // itterate over DEVICE.preferences map and inputIt all // library marker kkossev.deviceProfileLib, line 64
            if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 65
                (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 66
                    Map inputMap = inputIt(key) // library marker kkossev.deviceProfileLib, line 67
                    if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 68
                        input inputMap // library marker kkossev.deviceProfileLib, line 69
                    } // library marker kkossev.deviceProfileLib, line 70
                } // library marker kkossev.deviceProfileLib, line 71
            } // library marker kkossev.deviceProfileLib, line 72
            if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 73
                input(name: 'forcedProfile', type: 'enum', title: '<b>Device Profile</b>', description: '<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>',  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 74
            } // library marker kkossev.deviceProfileLib, line 75
        } // library marker kkossev.deviceProfileLib, line 76
    } // library marker kkossev.deviceProfileLib, line 77
} // library marker kkossev.deviceProfileLib, line 78

boolean is2in1() { return getDeviceProfile().contains('TS0601_2IN1') } // library marker kkossev.deviceProfileLib, line 80

String  getDeviceProfile()       { state?.deviceProfile ?: 'UNKNOWN' } // library marker kkossev.deviceProfileLib, line 82
Map     getDEVICE()              { deviceProfilesV3 != null ? deviceProfilesV3[getDeviceProfile()] : deviceProfilesV2 != null ? deviceProfilesV2[getDeviceProfile()] : [:] } // library marker kkossev.deviceProfileLib, line 83
Set     getDeviceProfiles()      { deviceProfilesV3 != null ? deviceProfilesV3?.keySet() : deviceProfilesV2 != null ?  deviceProfilesV2?.keySet() : [] } // library marker kkossev.deviceProfileLib, line 84
//List<String> getDeviceProfilesMap()   { deviceProfilesV3 != null ? deviceProfilesV3.values().description as List<String> : deviceProfilesV2.values().description as List<String> } // library marker kkossev.deviceProfileLib, line 85
List<String> getDeviceProfilesMap()   { // library marker kkossev.deviceProfileLib, line 86
    if (deviceProfilesV3 == null) { // library marker kkossev.deviceProfileLib, line 87
        if (deviceProfilesV2 == null) { return [] } // library marker kkossev.deviceProfileLib, line 88
        return deviceProfilesV2.values().description as List<String> // library marker kkossev.deviceProfileLib, line 89
    } // library marker kkossev.deviceProfileLib, line 90
    List<String> activeProfiles = [] // library marker kkossev.deviceProfileLib, line 91
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 92
        if (profileMap.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 93
            activeProfiles.add(profileName) // library marker kkossev.deviceProfileLib, line 94
        } // library marker kkossev.deviceProfileLib, line 95
    } // library marker kkossev.deviceProfileLib, line 96
    return activeProfiles // library marker kkossev.deviceProfileLib, line 97
} // library marker kkossev.deviceProfileLib, line 98

// ---------------------------------- deviceProfilesV3 helper functions -------------------------------------------- // library marker kkossev.deviceProfileLib, line 100

/** // library marker kkossev.deviceProfileLib, line 102
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 103
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 104
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 105
 */ // library marker kkossev.deviceProfileLib, line 106
String getProfileKey(final String valueStr) { // library marker kkossev.deviceProfileLib, line 107
    if (deviceProfilesV3 != null) { return deviceProfilesV3.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 108
    else if (deviceProfilesV2 != null) { return deviceProfilesV2.find { _, profileMap -> profileMap.description == valueStr }?.key } // library marker kkossev.deviceProfileLib, line 109
    else { return null } // library marker kkossev.deviceProfileLib, line 110
} // library marker kkossev.deviceProfileLib, line 111

/** // library marker kkossev.deviceProfileLib, line 113
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 114
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 115
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 116
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 117
 * @return empty map [:] if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 118
 */ // library marker kkossev.deviceProfileLib, line 119
Map getPreferencesMapByName(final String param, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 120
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 121
    if (!(param in DEVICE?.preferences)) { if (debug) { log.warn "getPreferencesMapByName: preference ${param} not defined for this device!" } ; return [:] } // library marker kkossev.deviceProfileLib, line 122
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 123
    def preference // library marker kkossev.deviceProfileLib, line 124
    try { // library marker kkossev.deviceProfileLib, line 125
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 126
        if (debug) { log.debug "getPreferencesMapByName: preference ${param} found. value is ${preference}" } // library marker kkossev.deviceProfileLib, line 127
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 128
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 129
            logDebug "getPreferencesMapByName: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 130
            return [:]     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 131
        } // library marker kkossev.deviceProfileLib, line 132
        if (safeToInt(preference, -1) > 0) {             //if (preference instanceof Number) { // library marker kkossev.deviceProfileLib, line 133
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 134
            //if (debug) log.trace "getPreferencesMapByName: param ${param} preference ${preference} is number (${dp})" // library marker kkossev.deviceProfileLib, line 135
            foundMap = DEVICE?.tuyaDPs.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 136
        } // library marker kkossev.deviceProfileLib, line 137
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 138
            //if (debug) { log.trace "${DEVICE?.attributes}" } // library marker kkossev.deviceProfileLib, line 139
            foundMap = DEVICE?.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 140
        } // library marker kkossev.deviceProfileLib, line 141
    // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 142
    } catch (e) { // library marker kkossev.deviceProfileLib, line 143
        if (debug) { log.warn "getPreferencesMapByName: exception ${e} caught when getting preference ${param} !" } // library marker kkossev.deviceProfileLib, line 144
        return [:] // library marker kkossev.deviceProfileLib, line 145
    } // library marker kkossev.deviceProfileLib, line 146
    if (debug) { log.debug "getPreferencesMapByName: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 147
    return foundMap // library marker kkossev.deviceProfileLib, line 148
} // library marker kkossev.deviceProfileLib, line 149

Map getAttributesMap(String attribName, boolean debug=false) { // library marker kkossev.deviceProfileLib, line 151
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 152
    List<Map> searchMapList = [] // library marker kkossev.deviceProfileLib, line 153
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in tuyaDPs" } // library marker kkossev.deviceProfileLib, line 154
    if (DEVICE?.tuyaDPs != null && DEVICE?.tuyaDPs != [:]) { // library marker kkossev.deviceProfileLib, line 155
        searchMapList =  DEVICE?.tuyaDPs // library marker kkossev.deviceProfileLib, line 156
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 157
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 158
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 159
            return foundMap // library marker kkossev.deviceProfileLib, line 160
        } // library marker kkossev.deviceProfileLib, line 161
    } // library marker kkossev.deviceProfileLib, line 162
    if (debug) { logDebug "getAttributesMap: searching for attribute ${attribName} in attributes" } // library marker kkossev.deviceProfileLib, line 163
    if (DEVICE?.attributes != null && DEVICE?.attributes != [:]) { // library marker kkossev.deviceProfileLib, line 164
        searchMapList  =  DEVICE?.attributes // library marker kkossev.deviceProfileLib, line 165
        foundMap = searchMapList.find { it.name == attribName } // library marker kkossev.deviceProfileLib, line 166
        if (foundMap != null) { // library marker kkossev.deviceProfileLib, line 167
            if (debug) { logDebug "getAttributesMap: foundMap = ${foundMap}" } // library marker kkossev.deviceProfileLib, line 168
            return foundMap // library marker kkossev.deviceProfileLib, line 169
        } // library marker kkossev.deviceProfileLib, line 170
    } // library marker kkossev.deviceProfileLib, line 171
    if (debug) { logDebug "getAttributesMap: attribute ${attribName} not found in tuyaDPs or attributes map! foundMap=${foundMap}" } // library marker kkossev.deviceProfileLib, line 172
    return [:] // library marker kkossev.deviceProfileLib, line 173
} // library marker kkossev.deviceProfileLib, line 174

/** // library marker kkossev.deviceProfileLib, line 176
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 177
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 178
 */ // library marker kkossev.deviceProfileLib, line 179
void resetPreferencesToDefaults(boolean debug=true) { // library marker kkossev.deviceProfileLib, line 180
    logDebug "resetPreferencesToDefaults: DEVICE=${DEVICE?.description} preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 181
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 182
    if (preferences == null || preferences.isEmpty()) { logDebug 'Preferences not found!' ; return } // library marker kkossev.deviceProfileLib, line 183
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 184
    preferences.each { parName, mapValue -> // library marker kkossev.deviceProfileLib, line 185
        if (debug) { log.trace "$parName $mapValue" } // library marker kkossev.deviceProfileLib, line 186
        if ((mapValue in [true, false]) || (mapValue in ['true', 'false'])) { // library marker kkossev.deviceProfileLib, line 187
            logDebug "Preference ${parName} is predefined -> (${mapValue})"     // what was the idea here? // library marker kkossev.deviceProfileLib, line 188
            return // continue // library marker kkossev.deviceProfileLib, line 189
        } // library marker kkossev.deviceProfileLib, line 190
        parMap = getPreferencesMapByName(parName, false)    // the individual preference map // library marker kkossev.deviceProfileLib, line 191
        if (parMap?.isEmpty()) { logDebug "Preference ${parName} not found in tuyaDPs or attributes map!";  return }    // continue // library marker kkossev.deviceProfileLib, line 192
        // at:'0x0406:0x0020', name:'fadingTime', type:'enum', dt: '0x21', rw: 'rw', min:15, max:999, defVal:'30', scale:1, unit:'seconds', map:[15:'15 seconds', 30:'30 seconds', 60:'60 seconds', 120:'120 seconds', 300:'300 seconds'], title:'<b>Fading Time</b>',   description:'<i>Radar fading time in seconds</i>'], // library marker kkossev.deviceProfileLib, line 193
        if (parMap.defVal == null) { logDebug "no default value for preference ${parName} !" ; return }     // continue // library marker kkossev.deviceProfileLib, line 194
        if (debug) { log.info "setting par ${parMap.name} defVal = ${parMap.defVal} (type:${parMap.type})" } // library marker kkossev.deviceProfileLib, line 195
        String str = parMap.name // library marker kkossev.deviceProfileLib, line 196
        device.updateSetting("$str", [value:parMap.defVal as String, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 197
    } // library marker kkossev.deviceProfileLib, line 198
    logInfo 'Preferences reset to default values' // library marker kkossev.deviceProfileLib, line 199
} // library marker kkossev.deviceProfileLib, line 200

/** // library marker kkossev.deviceProfileLib, line 202
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 203
 * // library marker kkossev.deviceProfileLib, line 204
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 205
 */ // library marker kkossev.deviceProfileLib, line 206
List<String> getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 207
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 208
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 209
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 210
        validPars = DEVICE?.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 211
    } // library marker kkossev.deviceProfileLib, line 212
    return validPars // library marker kkossev.deviceProfileLib, line 213
} // library marker kkossev.deviceProfileLib, line 214

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 216
def getScaledPreferenceValue(String preference, Map dpMap) { // library marker kkossev.deviceProfileLib, line 217
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 218
    def value = settings."${preference}" // library marker kkossev.deviceProfileLib, line 219
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 220
    def scaledValue // library marker kkossev.deviceProfileLib, line 221
    if (value == null) { // library marker kkossev.deviceProfileLib, line 222
        logDebug "getScaledPreferenceValue: preference ${preference} not found!" // library marker kkossev.deviceProfileLib, line 223
        return null // library marker kkossev.deviceProfileLib, line 224
    } // library marker kkossev.deviceProfileLib, line 225
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 226
        case 'number' : // library marker kkossev.deviceProfileLib, line 227
            scaledValue = safeToInt(value) // library marker kkossev.deviceProfileLib, line 228
            break // library marker kkossev.deviceProfileLib, line 229
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 230
            scaledValue = safeToDouble(value) // library marker kkossev.deviceProfileLib, line 231
            if (dpMap.scale != null && dpMap.scale != 1) { // library marker kkossev.deviceProfileLib, line 232
                scaledValue = Math.round(scaledValue * dpMap.scale) // library marker kkossev.deviceProfileLib, line 233
            } // library marker kkossev.deviceProfileLib, line 234
            break // library marker kkossev.deviceProfileLib, line 235
        case 'bool' : // library marker kkossev.deviceProfileLib, line 236
            scaledValue = value == 'true' ? 1 : 0 // library marker kkossev.deviceProfileLib, line 237
            break // library marker kkossev.deviceProfileLib, line 238
        case 'enum' : // library marker kkossev.deviceProfileLib, line 239
            //logWarn "getScaledPreferenceValue: <b>ENUM</b> preference ${preference} type:${dpMap.type} value = ${value} dpMap.scale=${dpMap.scale}" // library marker kkossev.deviceProfileLib, line 240
            if (dpMap.map == null) { // library marker kkossev.deviceProfileLib, line 241
                logDebug "getScaledPreferenceValue: preference ${preference} has no map defined!" // library marker kkossev.deviceProfileLib, line 242
                return null // library marker kkossev.deviceProfileLib, line 243
            } // library marker kkossev.deviceProfileLib, line 244
            scaledValue = value // library marker kkossev.deviceProfileLib, line 245
            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 246
                scaledValue = Math.round(safeToDouble(scaledValue ) * safeToInt(dpMap.scale)) // library marker kkossev.deviceProfileLib, line 247
            } // library marker kkossev.deviceProfileLib, line 248
            break // library marker kkossev.deviceProfileLib, line 249
        default : // library marker kkossev.deviceProfileLib, line 250
            logDebug "getScaledPreferenceValue: preference ${preference} has unsupported type ${dpMap.type}!" // library marker kkossev.deviceProfileLib, line 251
            return null // library marker kkossev.deviceProfileLib, line 252
    } // library marker kkossev.deviceProfileLib, line 253
    //logDebug "getScaledPreferenceValue: preference ${preference} value = ${value} scaledValue = ${scaledValue} (scale=${dpMap.scale})" // library marker kkossev.deviceProfileLib, line 254
    return scaledValue // library marker kkossev.deviceProfileLib, line 255
} // library marker kkossev.deviceProfileLib, line 256

// called from updated() method // library marker kkossev.deviceProfileLib, line 258
// TODO !!!!!!!!!! - refactor it !!!  IAS settings do not use Tuya DPs !!! // library marker kkossev.deviceProfileLib, line 259
public void updateAllPreferences() { // library marker kkossev.deviceProfileLib, line 260
    logDebug "updateAllPreferences: preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 261
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 262
        logDebug "updateAllPreferences: no preferences defined for device profile ${getDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 263
        return // library marker kkossev.deviceProfileLib, line 264
    } // library marker kkossev.deviceProfileLib, line 265
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 266
    def preferenceValue    // int or String for enums // library marker kkossev.deviceProfileLib, line 267
    // itterate over the preferences map and update the device settings // library marker kkossev.deviceProfileLib, line 268
    (DEVICE?.preferences).each { name, dp -> // library marker kkossev.deviceProfileLib, line 269
        Map foundMap = getPreferencesMapByName(name, false) // library marker kkossev.deviceProfileLib, line 270
        logDebug "updateAllPreferences: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 271
        if (foundMap != null && foundMap != [:]) { // library marker kkossev.deviceProfileLib, line 272
            // preferenceValue = getScaledPreferenceValue(name, foundMap) // library marker kkossev.deviceProfileLib, line 273
            preferenceValue = settings."${name}" // library marker kkossev.deviceProfileLib, line 274
            logTrace"preferenceValue = ${preferenceValue}" // library marker kkossev.deviceProfileLib, line 275
            if (foundMap.type == 'enum' && foundMap.scale != null && foundMap.scale != 1 && foundMap.scale != 0) { // library marker kkossev.deviceProfileLib, line 276
                // scale the value // library marker kkossev.deviceProfileLib, line 277
                preferenceValue = (safeToDouble(preferenceValue) / safeToInt(foundMap.scale)) as double // library marker kkossev.deviceProfileLib, line 278
            } // library marker kkossev.deviceProfileLib, line 279
            if (preferenceValue != null) { setPar(name, preferenceValue.toString()) } // library marker kkossev.deviceProfileLib, line 280
            else { logDebug "updateAllPreferences: preference ${name} is not set (preferenceValue was null)" ;  return } // library marker kkossev.deviceProfileLib, line 281
        } // library marker kkossev.deviceProfileLib, line 282
        else { logDebug "warning: couldn't find map for preference ${name}" ; return } // library marker kkossev.deviceProfileLib, line 283
    } // library marker kkossev.deviceProfileLib, line 284
    return // library marker kkossev.deviceProfileLib, line 285
} // library marker kkossev.deviceProfileLib, line 286

/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 288
def divideBy100(int val) { return (val as int) / 100 } // library marker kkossev.deviceProfileLib, line 289
int multiplyBy100(int val) { return (val as int) * 100 } // library marker kkossev.deviceProfileLib, line 290
int divideBy10(int val) { // library marker kkossev.deviceProfileLib, line 291
    if (val > 10) { return (val as int) / 10 } // library marker kkossev.deviceProfileLib, line 292
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 293
} // library marker kkossev.deviceProfileLib, line 294
int multiplyBy10(int val) { return (val as int) * 10 } // library marker kkossev.deviceProfileLib, line 295
int divideBy1(int val) { return (val as int) / 1 }    //tests // library marker kkossev.deviceProfileLib, line 296
int signedInt(int val) { // library marker kkossev.deviceProfileLib, line 297
    if (val > 127) { return (val as int) - 256 } // library marker kkossev.deviceProfileLib, line 298
    else { return (val as int) } // library marker kkossev.deviceProfileLib, line 299
} // library marker kkossev.deviceProfileLib, line 300

List<String> zclWriteAttribute(Map attributesMap, int scaledValue) { // library marker kkossev.deviceProfileLib, line 302
    if (attributesMap == null || attributesMap == [:]) { logWarn "attributesMap=${attributesMap}" ; return [] } // library marker kkossev.deviceProfileLib, line 303
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 304
    Map map = [:] // library marker kkossev.deviceProfileLib, line 305
    // cluster:attribute // library marker kkossev.deviceProfileLib, line 306
    try { // library marker kkossev.deviceProfileLib, line 307
        map['cluster'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[0]) as Integer // library marker kkossev.deviceProfileLib, line 308
        map['attribute'] = hubitat.helper.HexUtils.hexStringToInt((attributesMap.at).split(':')[1]) as Integer // library marker kkossev.deviceProfileLib, line 309
        map['dt']  = (attributesMap.dt != null && attributesMap.dt != '') ? hubitat.helper.HexUtils.hexStringToInt(attributesMap.dt) as Integer : null // library marker kkossev.deviceProfileLib, line 310
        map['mfgCode'] = attributesMap.mfgCode ? attributesMap.mfgCode as String : null // library marker kkossev.deviceProfileLib, line 311
    } // library marker kkossev.deviceProfileLib, line 312
    catch (e) { logWarn "setPar: Exception caught while splitting cluser and attribute <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) :  '${e}' " ; return [] } // library marker kkossev.deviceProfileLib, line 313
    // dt (data type) is obligatory when writing to a cluster... // library marker kkossev.deviceProfileLib, line 314
    if (attributesMap.rw != null && attributesMap.rw == 'rw' && map.dt == null) { // library marker kkossev.deviceProfileLib, line 315
        map.dt = attributesMap.type in ['number', 'decimal'] ? DataType.INT16 : DataType.UINT8 // library marker kkossev.deviceProfileLib, line 316
        logDebug "cluster:attribute ${attributesMap.at} is read-write, but no data type (dt) is defined! Assuming 0x${zigbee.convertToHexString(map.dt, 2)}" // library marker kkossev.deviceProfileLib, line 317
    } // library marker kkossev.deviceProfileLib, line 318
    if (map.mfgCode != null && map.mfgCode != '') { // library marker kkossev.deviceProfileLib, line 319
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, map.mfgCode, delay = 200) // library marker kkossev.deviceProfileLib, line 320
    } // library marker kkossev.deviceProfileLib, line 321
    else { // library marker kkossev.deviceProfileLib, line 322
        cmds = zigbee.writeAttribute(map.cluster as int, map.attribute as int, map.dt as int, scaledValue, [:], delay = 200) // library marker kkossev.deviceProfileLib, line 323
    } // library marker kkossev.deviceProfileLib, line 324
    return cmds // library marker kkossev.deviceProfileLib, line 325
} // library marker kkossev.deviceProfileLib, line 326

/** // library marker kkossev.deviceProfileLib, line 328
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 329
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 330
 * // library marker kkossev.deviceProfileLib, line 331
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 332
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 333
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 334
 */ // library marker kkossev.deviceProfileLib, line 335
/* groovylint-disable-next-line MethodReturnTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 336
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 337
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 338
    def value              // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 339
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 340
    def scaledValue        // // library marker kkossev.deviceProfileLib, line 341
    //logDebug "validateAndScaleParameterValue: dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 342
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 343
        case 'number' : // library marker kkossev.deviceProfileLib, line 344
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 345
            //scaledValue = value // library marker kkossev.deviceProfileLib, line 346
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 347
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 348
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 349
            } // library marker kkossev.deviceProfileLib, line 350
            else { // library marker kkossev.deviceProfileLib, line 351
                scaledValue = value // library marker kkossev.deviceProfileLib, line 352
            } // library marker kkossev.deviceProfileLib, line 353
            break // library marker kkossev.deviceProfileLib, line 354

        case 'decimal' : // library marker kkossev.deviceProfileLib, line 356
            value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 357
            // scale the value // library marker kkossev.deviceProfileLib, line 358
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 359
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 360
            } // library marker kkossev.deviceProfileLib, line 361
            else { // library marker kkossev.deviceProfileLib, line 362
                scaledValue = value // library marker kkossev.deviceProfileLib, line 363
            } // library marker kkossev.deviceProfileLib, line 364
            break // library marker kkossev.deviceProfileLib, line 365

        case 'bool' : // library marker kkossev.deviceProfileLib, line 367
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 368
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 369
            else { // library marker kkossev.deviceProfileLib, line 370
                logInfo "bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 371
                return null // library marker kkossev.deviceProfileLib, line 372
            } // library marker kkossev.deviceProfileLib, line 373
            break // library marker kkossev.deviceProfileLib, line 374
        case 'enum' : // library marker kkossev.deviceProfileLib, line 375
            // enums are always integer values // library marker kkossev.deviceProfileLib, line 376
            // check if the scaling is different than 1 in dpMap // library marker kkossev.deviceProfileLib, line 377
            logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. dpMap=${dpMap}" // library marker kkossev.deviceProfileLib, line 378
            Integer scale = safeToInt(dpMap.scale) // library marker kkossev.deviceProfileLib, line 379
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 380
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 381
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 382
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 383
            } // library marker kkossev.deviceProfileLib, line 384
            else { // library marker kkossev.deviceProfileLib, line 385
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 386
            } // library marker kkossev.deviceProfileLib, line 387
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 388
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 389
                //List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 390
                //logDebug "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 391
                // find the key for the value // library marker kkossev.deviceProfileLib, line 392
                String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 393
                logTrace "validateAndScaleParameterValue: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 394
                if (key == null) { // library marker kkossev.deviceProfileLib, line 395
                    logInfo "invalid enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 396
                    return null // library marker kkossev.deviceProfileLib, line 397
                } // library marker kkossev.deviceProfileLib, line 398
                value = scaledValue = key as Integer // library marker kkossev.deviceProfileLib, line 399
            //return null // library marker kkossev.deviceProfileLib, line 400
            } // library marker kkossev.deviceProfileLib, line 401
            break // library marker kkossev.deviceProfileLib, line 402
        default : // library marker kkossev.deviceProfileLib, line 403
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 404
            return null // library marker kkossev.deviceProfileLib, line 405
    } // library marker kkossev.deviceProfileLib, line 406
    //logTrace "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 407
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 408
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 409
        logWarn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 410
        return null // library marker kkossev.deviceProfileLib, line 411
    } // library marker kkossev.deviceProfileLib, line 412
    //logTrace "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 413
    return scaledValue // library marker kkossev.deviceProfileLib, line 414
} // library marker kkossev.deviceProfileLib, line 415

/** // library marker kkossev.deviceProfileLib, line 417
 * Sets the value of a parameter for a device. // library marker kkossev.deviceProfileLib, line 418
 * // library marker kkossev.deviceProfileLib, line 419
 * @param par The parameter name. // library marker kkossev.deviceProfileLib, line 420
 * @param val The parameter value. // library marker kkossev.deviceProfileLib, line 421
 * @return true if the parameter was successfully set, false otherwise. // library marker kkossev.deviceProfileLib, line 422
 */ // library marker kkossev.deviceProfileLib, line 423
public boolean setPar(final String parPar=null, final String val=null ) { // library marker kkossev.deviceProfileLib, line 424
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 425
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 426
    logDebug "setPar(${parPar}, ${val})" // library marker kkossev.deviceProfileLib, line 427
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 428
    if (parPar == null /*|| !(par in getValidParsPerModel())*/) { logInfo "setPar: 'parameter' must be one of these : ${getValidParsPerModel()}"; return false } // library marker kkossev.deviceProfileLib, line 429
    String par = parPar.trim() // library marker kkossev.deviceProfileLib, line 430
    Map dpMap = getPreferencesMapByName(par, false)                                   // get the map for the parameter // library marker kkossev.deviceProfileLib, line 431
    if ( dpMap == null || dpMap == [:]) { logInfo "setPar: tuyaDPs map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 432
    if (val == null) { logInfo "setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 433
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 434
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 435
    if (scaledValue == null) { logInfo "setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 436

    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 438
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 439
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 440
    if (this.respondsTo(customSetFunction)) { // library marker kkossev.deviceProfileLib, line 441
        logDebug "setPar: found customSetFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 442
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 443
        try { cmds = "$customSetFunction"(scaledValue) } // library marker kkossev.deviceProfileLib, line 444
        catch (e) { logWarn "setPar: Exception caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val})) : '${e}'" ; return false } // library marker kkossev.deviceProfileLib, line 445
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 446
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 447
            logInfo "setPar: (1) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 448
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 449
            return true // library marker kkossev.deviceProfileLib, line 450
        } // library marker kkossev.deviceProfileLib, line 451
        else { // library marker kkossev.deviceProfileLib, line 452
            logWarn "setPar: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 453
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 454
        } // library marker kkossev.deviceProfileLib, line 455
    } // library marker kkossev.deviceProfileLib, line 456
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 457
        // set a virtual attribute // library marker kkossev.deviceProfileLib, line 458
        /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 459
        def valMiscType // library marker kkossev.deviceProfileLib, line 460
        logDebug "setPar: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 461
        if (dpMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 462
            // find the key for the value // library marker kkossev.deviceProfileLib, line 463
            String key = dpMap.map.find { it.value == val }?.key // library marker kkossev.deviceProfileLib, line 464
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key}" // library marker kkossev.deviceProfileLib, line 465
            if (key == null) { // library marker kkossev.deviceProfileLib, line 466
                logInfo "setPar: invalid virtual enum parameter <b>${val}</b>. value must be one of <b>${dpMap.map}</b>" // library marker kkossev.deviceProfileLib, line 467
                return false // library marker kkossev.deviceProfileLib, line 468
            } // library marker kkossev.deviceProfileLib, line 469
            valMiscType = dpMap.map[key as int] // library marker kkossev.deviceProfileLib, line 470
            logTrace "setPar: enum parameter <b>${val}</b>. key=${key} valMiscType=${valMiscType} dpMap.map=${dpMap.map}" // library marker kkossev.deviceProfileLib, line 471
            device.updateSetting("$par", [value:key as String, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 472
        } // library marker kkossev.deviceProfileLib, line 473
        else { // library marker kkossev.deviceProfileLib, line 474
            valMiscType = val // library marker kkossev.deviceProfileLib, line 475
            device.updateSetting("$par", [value:valMiscType, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 476
        } // library marker kkossev.deviceProfileLib, line 477
        String descriptionText = "${par} set to ${valMiscType}${dpMap.unit ?: ''} [virtual]" // library marker kkossev.deviceProfileLib, line 478
        sendEvent(name:par, value:valMiscType, unit:dpMap.unit ?: '', isDigital: true) // library marker kkossev.deviceProfileLib, line 479
        logInfo descriptionText // library marker kkossev.deviceProfileLib, line 480
        return true // library marker kkossev.deviceProfileLib, line 481
    } // library marker kkossev.deviceProfileLib, line 482

    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 484
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 485

    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 487
    try { isTuyaDP = dpMap.dp instanceof Number } // library marker kkossev.deviceProfileLib, line 488
    catch (e) { logWarn"setPar: (1) exception ${e} caught while checking isNumber() preference ${preference}" ; isTuyaDP = false } // library marker kkossev.deviceProfileLib, line 489
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 490
        // Tuya DP // library marker kkossev.deviceProfileLib, line 491
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 492
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 493
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 494
            return false // library marker kkossev.deviceProfileLib, line 495
        } // library marker kkossev.deviceProfileLib, line 496
        else { // library marker kkossev.deviceProfileLib, line 497
            logInfo "setPar: (2) sending parameter <b>$par</b> (<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 498
            sendZigbeeCommands(cmds) // library marker kkossev.deviceProfileLib, line 499
            return false // library marker kkossev.deviceProfileLib, line 500
        } // library marker kkossev.deviceProfileLib, line 501
    } // library marker kkossev.deviceProfileLib, line 502
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 503
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 504
        logDebug "setPar: found at=${dpMap.at} dt=${dpMap.dt} mapMfCode=${dpMap.mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 505
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 506
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 507
            logWarn "setPar: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 508
            return false // library marker kkossev.deviceProfileLib, line 509
        } // library marker kkossev.deviceProfileLib, line 510
    } // library marker kkossev.deviceProfileLib, line 511
    else { logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" ; return false } // library marker kkossev.deviceProfileLib, line 512
    logInfo "setPar: (3) successfluly executed setPar <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 513
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 514
    return true // library marker kkossev.deviceProfileLib, line 515
} // library marker kkossev.deviceProfileLib, line 516

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 518
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 519
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 520
List<String> sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 521
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 522
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 523
    if (dpMap == null) { logWarn "sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" ; return [] } // library marker kkossev.deviceProfileLib, line 524
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 525
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 526
        logWarn "sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 527
        return [] // library marker kkossev.deviceProfileLib, line 528
    } // library marker kkossev.deviceProfileLib, line 529
    String dpType // library marker kkossev.deviceProfileLib, line 530
    if (dpMap.dt == null) { // library marker kkossev.deviceProfileLib, line 531
        dpType = dpMap.type == 'bool' ? DP_TYPE_BOOL : dpMap.type == 'enum' ? DP_TYPE_ENUM : (dpMap.type in ['value', 'number', 'decimal']) ? DP_TYPE_VALUE : null // library marker kkossev.deviceProfileLib, line 532
    } // library marker kkossev.deviceProfileLib, line 533
    else { // library marker kkossev.deviceProfileLib, line 534
        dpType = dpMap.dt // "01" - bool, "02" - enum, "03" - value // library marker kkossev.deviceProfileLib, line 535
    } // library marker kkossev.deviceProfileLib, line 536
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 537
        logWarn "sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 538
        return [] // library marker kkossev.deviceProfileLib, line 539
    } // library marker kkossev.deviceProfileLib, line 540
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 541
    String dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2) // library marker kkossev.deviceProfileLib, line 542
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 543
    if (dpMap.tuyaCmd != null ) { // library marker kkossev.deviceProfileLib, line 544
        cmds = sendTuyaCommand( dp, dpType, dpValHex, dpMap.tuyaCmd as int) // library marker kkossev.deviceProfileLib, line 545
    } // library marker kkossev.deviceProfileLib, line 546
    else { // library marker kkossev.deviceProfileLib, line 547
        cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 548
    } // library marker kkossev.deviceProfileLib, line 549
    return cmds // library marker kkossev.deviceProfileLib, line 550
} // library marker kkossev.deviceProfileLib, line 551

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 553
public boolean sendAttribute(String par=null, val=null ) { // library marker kkossev.deviceProfileLib, line 554
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 555
    //Boolean validated = false // library marker kkossev.deviceProfileLib, line 556
    logDebug "sendAttribute(${par}, ${val})" // library marker kkossev.deviceProfileLib, line 557
    if (par == null || DEVICE?.preferences == null || DEVICE?.preferences == [:]) { return false } // library marker kkossev.deviceProfileLib, line 558

    Map dpMap = getAttributesMap(par, false)                                   // get the map for the attribute // library marker kkossev.deviceProfileLib, line 560
    if (dpMap == null || dpMap.isEmpty()) { logWarn "sendAttribute: map not found for parameter <b>${par}</b>"; return false } // library marker kkossev.deviceProfileLib, line 561
    if (val == null) { logWarn "sendAttribute: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 562
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 563
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String)      // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 564
    if (scaledValue == null) { logWarn "sendAttribute: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}"; return false } // library marker kkossev.deviceProfileLib, line 565
    logDebug "sendAttribute: parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 566
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 567
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 568
    String customSetFunction = "customSet${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 569
    if (this.respondsTo(customSetFunction) /*&& !(customSetFunction in ["setHeatingSetpoint", "setCoolingSetpoint", "setThermostatMode"])*/) { // library marker kkossev.deviceProfileLib, line 570
        logDebug "sendAttribute: found customSetFunction=${customSetFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 571
        // execute the customSetFunction // library marker kkossev.deviceProfileLib, line 572
        try { // library marker kkossev.deviceProfileLib, line 573
            cmds = "$customSetFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 574
        } // library marker kkossev.deviceProfileLib, line 575
        catch (e) { // library marker kkossev.deviceProfileLib, line 576
            logWarn "sendAttribute: Exception '${e}'caught while processing <b>$customSetFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 577
            return false // library marker kkossev.deviceProfileLib, line 578
        } // library marker kkossev.deviceProfileLib, line 579
        logDebug "customSetFunction result is ${cmds}" // library marker kkossev.deviceProfileLib, line 580
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 581
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 582
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 583
            return true // library marker kkossev.deviceProfileLib, line 584
        } // library marker kkossev.deviceProfileLib, line 585
        else { // library marker kkossev.deviceProfileLib, line 586
            logWarn "sendAttribute: customSetFunction <b>$customSetFunction</b>(<b>$scaledValue</b>) returned null or empty list, continue with the default processing" // library marker kkossev.deviceProfileLib, line 587
        // continue with the default processing // library marker kkossev.deviceProfileLib, line 588
        } // library marker kkossev.deviceProfileLib, line 589
    } // library marker kkossev.deviceProfileLib, line 590
    else { // library marker kkossev.deviceProfileLib, line 591
        logDebug "sendAttribute: SKIPPED customSetFunction ${customSetFunction}, continue with the default processing" // library marker kkossev.deviceProfileLib, line 592
    } // library marker kkossev.deviceProfileLib, line 593
    // check whether this is a tuya DP or a cluster:attribute parameter or a virtual device // library marker kkossev.deviceProfileLib, line 594
    if (isVirtual()) { // library marker kkossev.deviceProfileLib, line 595
        // send a virtual attribute // library marker kkossev.deviceProfileLib, line 596
        logDebug "sendAttribute: found virtual attribute ${par} value ${val}" // library marker kkossev.deviceProfileLib, line 597
        // patch !! // library marker kkossev.deviceProfileLib, line 598
        if (par == 'heatingSetpoint') { // library marker kkossev.deviceProfileLib, line 599
            sendHeatingSetpointEvent(val) // library marker kkossev.deviceProfileLib, line 600
        } // library marker kkossev.deviceProfileLib, line 601
        else { // library marker kkossev.deviceProfileLib, line 602
            String descriptionText = "${par} is ${val} [virtual]" // library marker kkossev.deviceProfileLib, line 603
            sendEvent(name:par, value:val, isDigital: true) // library marker kkossev.deviceProfileLib, line 604
            logInfo descriptionText // library marker kkossev.deviceProfileLib, line 605
        } // library marker kkossev.deviceProfileLib, line 606
        return true // library marker kkossev.deviceProfileLib, line 607
    } // library marker kkossev.deviceProfileLib, line 608
    else { // library marker kkossev.deviceProfileLib, line 609
        logDebug "sendAttribute: not a virtual device (device.controllerType = ${device.controllerType}), continue " // library marker kkossev.deviceProfileLib, line 610
    } // library marker kkossev.deviceProfileLib, line 611
    boolean isTuyaDP // library marker kkossev.deviceProfileLib, line 612
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 613
    def preference = dpMap.dp   // TODO - remove it? // library marker kkossev.deviceProfileLib, line 614
    try { // library marker kkossev.deviceProfileLib, line 615
        isTuyaDP = dpMap.dp instanceof Number       // check if dpMap.dp is a number // library marker kkossev.deviceProfileLib, line 616
    } // library marker kkossev.deviceProfileLib, line 617
    catch (e) { // library marker kkossev.deviceProfileLib, line 618
        if (debug) { log.warn "sendAttribute: exception ${e} caught while checking isNumber() preference ${preference}" } // library marker kkossev.deviceProfileLib, line 619
        return false // library marker kkossev.deviceProfileLib, line 620
    } // library marker kkossev.deviceProfileLib, line 621
    if (dpMap.dp != null && isTuyaDP) { // library marker kkossev.deviceProfileLib, line 622
        // Tuya DP // library marker kkossev.deviceProfileLib, line 623
        cmds = sendTuyaParameter(dpMap,  par, scaledValue) // library marker kkossev.deviceProfileLib, line 624
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 625
            logWarn "sendAttribute: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 626
            return false // library marker kkossev.deviceProfileLib, line 627
        } // library marker kkossev.deviceProfileLib, line 628
        else { // library marker kkossev.deviceProfileLib, line 629
            logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 630
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 631
            return true // library marker kkossev.deviceProfileLib, line 632
        } // library marker kkossev.deviceProfileLib, line 633
    } // library marker kkossev.deviceProfileLib, line 634
    /* groovylint-disable-next-line EmptyIfStatement */ // library marker kkossev.deviceProfileLib, line 635
    else if (dpMap.at != null && dpMap.at == 'virtual') { // library marker kkossev.deviceProfileLib, line 636
    // send a virtual attribute // library marker kkossev.deviceProfileLib, line 637
    } // library marker kkossev.deviceProfileLib, line 638
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 639
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 640
        cmds = zclWriteAttribute(dpMap, scaledValue) // library marker kkossev.deviceProfileLib, line 641
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 642
            logWarn "sendAttribute: failed to write cluster:attribute ${dpMap.at} value ${scaledValue}" // library marker kkossev.deviceProfileLib, line 643
            return false // library marker kkossev.deviceProfileLib, line 644
        } // library marker kkossev.deviceProfileLib, line 645
    } // library marker kkossev.deviceProfileLib, line 646
    else { // library marker kkossev.deviceProfileLib, line 647
        logWarn "sendAttribute: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 648
        return false // library marker kkossev.deviceProfileLib, line 649
    } // library marker kkossev.deviceProfileLib, line 650
    logDebug "sendAttribute: successfluly executed sendAttribute <b>$customSetFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 651
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 652
    return true // library marker kkossev.deviceProfileLib, line 653
} // library marker kkossev.deviceProfileLib, line 654

/** // library marker kkossev.deviceProfileLib, line 656
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 657
 * @param command - The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 658
 * @param val     - The value to send with the command, can be null. // library marker kkossev.deviceProfileLib, line 659
 * @return true on success, false otherwise. // library marker kkossev.deviceProfileLib, line 660
 */ // library marker kkossev.deviceProfileLib, line 661
public boolean sendCommand(final String command_orig=null, final String val_orig=null) { // library marker kkossev.deviceProfileLib, line 662
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 663
    final String command = command_orig?.trim() // library marker kkossev.deviceProfileLib, line 664
    final String val = val_orig?.trim() // library marker kkossev.deviceProfileLib, line 665
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 666
    Map supportedCommandsMap = DEVICE?.commands as Map // library marker kkossev.deviceProfileLib, line 667
    if (supportedCommandsMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 668
        logInfo "sendCommand: no commands defined for device profile ${getDeviceProfile()} !" // library marker kkossev.deviceProfileLib, line 669
        return false // library marker kkossev.deviceProfileLib, line 670
    } // library marker kkossev.deviceProfileLib, line 671
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 672
    List supportedCommandsList =  DEVICE?.commands?.keySet() as List // library marker kkossev.deviceProfileLib, line 673
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 674
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 675
        logInfo "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE?.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 676
        return false // library marker kkossev.deviceProfileLib, line 677
    } // library marker kkossev.deviceProfileLib, line 678
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 679
    def func, funcResult // library marker kkossev.deviceProfileLib, line 680
    try { // library marker kkossev.deviceProfileLib, line 681
        func = DEVICE?.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 682
        if (val != null) { // library marker kkossev.deviceProfileLib, line 683
            funcResult = "${func}"(val) // library marker kkossev.deviceProfileLib, line 684
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 685
        } // library marker kkossev.deviceProfileLib, line 686
        else { // library marker kkossev.deviceProfileLib, line 687
            funcResult = "${func}"() // library marker kkossev.deviceProfileLib, line 688
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 689
        } // library marker kkossev.deviceProfileLib, line 690
    } // library marker kkossev.deviceProfileLib, line 691
    catch (e) { // library marker kkossev.deviceProfileLib, line 692
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 693
        return false // library marker kkossev.deviceProfileLib, line 694
    } // library marker kkossev.deviceProfileLib, line 695
    // funcResult is expected to be list of commands to be sent to the device, but can also return boolean or null // library marker kkossev.deviceProfileLib, line 696
    // check if the result is a list of commands // library marker kkossev.deviceProfileLib, line 697
    /* groovylint-disable-next-line Instanceof */ // library marker kkossev.deviceProfileLib, line 698
    if (funcResult instanceof List) { // library marker kkossev.deviceProfileLib, line 699
        cmds = funcResult // library marker kkossev.deviceProfileLib, line 700
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 701
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 702
        } // library marker kkossev.deviceProfileLib, line 703
    } else { // library marker kkossev.deviceProfileLib, line 704
        logDebug "sendCommand: <b>$func</b>(${val}) returned <b>${funcResult}</b> instead of a list of commands!" // library marker kkossev.deviceProfileLib, line 705
        return false // library marker kkossev.deviceProfileLib, line 706
    } // library marker kkossev.deviceProfileLib, line 707
    cmds = funcResult // library marker kkossev.deviceProfileLib, line 708
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 709
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 710
    } // library marker kkossev.deviceProfileLib, line 711
    return true // library marker kkossev.deviceProfileLib, line 712
} // library marker kkossev.deviceProfileLib, line 713

/** // library marker kkossev.deviceProfileLib, line 715
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 716
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 717
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 718
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 719
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 720
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 721
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 722
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 723
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 724
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 725
 */ // library marker kkossev.deviceProfileLib, line 726
Map inputIt(String paramPar, boolean debug = false) { // library marker kkossev.deviceProfileLib, line 727
    String param = paramPar.trim() // library marker kkossev.deviceProfileLib, line 728
    Map input = [:] // library marker kkossev.deviceProfileLib, line 729
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 730
    if (!(param in DEVICE?.preferences)) { // library marker kkossev.deviceProfileLib, line 731
        if (debug) { log.warn "inputIt: preference ${param} not defined for this device!" } // library marker kkossev.deviceProfileLib, line 732
        return [:] // library marker kkossev.deviceProfileLib, line 733
    } // library marker kkossev.deviceProfileLib, line 734
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 735
    def preference // library marker kkossev.deviceProfileLib, line 736
    try { // library marker kkossev.deviceProfileLib, line 737
        preference = DEVICE?.preferences["$param"] // library marker kkossev.deviceProfileLib, line 738
    } // library marker kkossev.deviceProfileLib, line 739
    catch (e) { // library marker kkossev.deviceProfileLib, line 740
        if (debug) { log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 741
        return [:] // library marker kkossev.deviceProfileLib, line 742
    } // library marker kkossev.deviceProfileLib, line 743
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 744
    try { // library marker kkossev.deviceProfileLib, line 745
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 746
            if (debug) { log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" } // library marker kkossev.deviceProfileLib, line 747
            return [:] // library marker kkossev.deviceProfileLib, line 748
        } // library marker kkossev.deviceProfileLib, line 749
    } // library marker kkossev.deviceProfileLib, line 750
    catch (e) { // library marker kkossev.deviceProfileLib, line 751
        if (debug) { log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 752
        return [:] // library marker kkossev.deviceProfileLib, line 753
    } // library marker kkossev.deviceProfileLib, line 754

    try { // library marker kkossev.deviceProfileLib, line 756
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 757
    } // library marker kkossev.deviceProfileLib, line 758
    catch (e) { // library marker kkossev.deviceProfileLib, line 759
        if (debug) { log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" } // library marker kkossev.deviceProfileLib, line 760
        return [:] // library marker kkossev.deviceProfileLib, line 761
    } // library marker kkossev.deviceProfileLib, line 762

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 764
    foundMap = getPreferencesMapByName(param) // library marker kkossev.deviceProfileLib, line 765
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 766
    if (foundMap?.isEmpty()) { // library marker kkossev.deviceProfileLib, line 767
        if (debug) { log.warn "inputIt: map not found for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 768
        return [:] // library marker kkossev.deviceProfileLib, line 769
    } // library marker kkossev.deviceProfileLib, line 770
    if (foundMap.rw != 'rw') { // library marker kkossev.deviceProfileLib, line 771
        if (debug) { log.warn "inputIt: param '${param}' is read only!" } // library marker kkossev.deviceProfileLib, line 772
        return [:] // library marker kkossev.deviceProfileLib, line 773
    } // library marker kkossev.deviceProfileLib, line 774
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 775
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 776
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 777
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 778
    if (input.type in ['number', 'decimal']) { // library marker kkossev.deviceProfileLib, line 779
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 780
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 781
        } // library marker kkossev.deviceProfileLib, line 782
        if (input.range != null && input.description != null) { // library marker kkossev.deviceProfileLib, line 783
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 784
            if (foundMap.unit != null && foundMap.unit != '') { // library marker kkossev.deviceProfileLib, line 785
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 786
            } // library marker kkossev.deviceProfileLib, line 787
        } // library marker kkossev.deviceProfileLib, line 788
    } // library marker kkossev.deviceProfileLib, line 789
    /* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.deviceProfileLib, line 790
    else if (input.type == 'enum') { // library marker kkossev.deviceProfileLib, line 791
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 792
    }/* // library marker kkossev.deviceProfileLib, line 793
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 794
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 795
    }*/ // library marker kkossev.deviceProfileLib, line 796
    else { // library marker kkossev.deviceProfileLib, line 797
        if (debug) { log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" } // library marker kkossev.deviceProfileLib, line 798
        return [:] // library marker kkossev.deviceProfileLib, line 799
    } // library marker kkossev.deviceProfileLib, line 800
    if (input.defVal != null) { // library marker kkossev.deviceProfileLib, line 801
        input.defVal = foundMap.defVal // library marker kkossev.deviceProfileLib, line 802
    } // library marker kkossev.deviceProfileLib, line 803
    return input // library marker kkossev.deviceProfileLib, line 804
} // library marker kkossev.deviceProfileLib, line 805

/** // library marker kkossev.deviceProfileLib, line 807
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 808
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 809
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 810
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 811
 */ // library marker kkossev.deviceProfileLib, line 812
List<String> getDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 813
    String deviceName = UNKNOWN, deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 814
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 815
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 816
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 817
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 818
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 819
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 820
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV3[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 821
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 822
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 823
            } // library marker kkossev.deviceProfileLib, line 824
        } // library marker kkossev.deviceProfileLib, line 825
    } // library marker kkossev.deviceProfileLib, line 826
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 827
        logWarn "getDeviceNameAndProfile: <b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 828
    } // library marker kkossev.deviceProfileLib, line 829
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 830
} // library marker kkossev.deviceProfileLib, line 831

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 833
void setDeviceNameAndProfile(String model=null, String manufacturer=null) { // library marker kkossev.deviceProfileLib, line 834
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 835
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 836
        logInfo "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 837
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 838
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 839
    } // library marker kkossev.deviceProfileLib, line 840
    String dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 841
    String dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 842
    if (deviceName != NULL && deviceName != UNKNOWN) { // library marker kkossev.deviceProfileLib, line 843
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 844
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 845
        device.updateSetting('forcedProfile', [value:deviceProfilesV3[deviceProfile]?.description, type:'enum']) // library marker kkossev.deviceProfileLib, line 846
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 847
    } else { // library marker kkossev.deviceProfileLib, line 848
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 849
    } // library marker kkossev.deviceProfileLib, line 850
} // library marker kkossev.deviceProfileLib, line 851

// TODO! // library marker kkossev.deviceProfileLib, line 853
List<String> refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 854
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 855
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 856
    logDebug "refreshDeviceProfile() : ${cmds} (TODO!)" // library marker kkossev.deviceProfileLib, line 857
    return cmds // library marker kkossev.deviceProfileLib, line 858
} // library marker kkossev.deviceProfileLib, line 859

// TODO ! // library marker kkossev.deviceProfileLib, line 861
List<String> configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 862
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 863
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 864
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.deviceProfileLib, line 865
    return cmds // library marker kkossev.deviceProfileLib, line 866
} // library marker kkossev.deviceProfileLib, line 867

// TODO // library marker kkossev.deviceProfileLib, line 869
List<String> initializeDeviceProfile() { // library marker kkossev.deviceProfileLib, line 870
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 871
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 872
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.deviceProfileLib, line 873
    return cmds // library marker kkossev.deviceProfileLib, line 874
} // library marker kkossev.deviceProfileLib, line 875

public void deviceProfileInitializeVars(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 877
    logDebug "deviceProfileInitializeVars(${fullInit})" // library marker kkossev.deviceProfileLib, line 878
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 879
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 880
    } // library marker kkossev.deviceProfileLib, line 881
} // library marker kkossev.deviceProfileLib, line 882

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 884
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 885
} // library marker kkossev.deviceProfileLib, line 886

///////////////////////////// Tuya DPs ///////////////////////////////// // library marker kkossev.deviceProfileLib, line 888

// // library marker kkossev.deviceProfileLib, line 890
// called from parse() // library marker kkossev.deviceProfileLib, line 891
// returns: true  - do not process this message if the spammy DP is defined in the spammyDPsToIgnore element of the active Device Profule // library marker kkossev.deviceProfileLib, line 892
//          false - the processing can continue // library marker kkossev.deviceProfileLib, line 893
// // library marker kkossev.deviceProfileLib, line 894
public boolean isSpammyDPsToIgnore(Map descMap) { // library marker kkossev.deviceProfileLib, line 895
    //log.trace "isSpammyDPsToIgnore: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 896
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 897
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 898
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 899
    int dp =  zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 900
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToIgnore as List // library marker kkossev.deviceProfileLib, line 901
    return (spammyList != null && (dp in spammyList) && ((settings?.ignoreDistance ?: false) == true)) // library marker kkossev.deviceProfileLib, line 902
} // library marker kkossev.deviceProfileLib, line 903

// // library marker kkossev.deviceProfileLib, line 905
// called from processTuyaDP(), processTuyaDPfromDeviceProfile(), isChattyDeviceReport() // library marker kkossev.deviceProfileLib, line 906
// returns: true  - do not generate Debug log messages if the chatty DP is defined in the spammyDPsToNotTrace element of the active Device Profule // library marker kkossev.deviceProfileLib, line 907
//          false - debug logs can be generated // library marker kkossev.deviceProfileLib, line 908
// // library marker kkossev.deviceProfileLib, line 909
public boolean isSpammyDPsToNotTrace(Map descMap) { // library marker kkossev.deviceProfileLib, line 910
    //log.trace "isSpammyDPsToNotTrace: ${state.deviceProfile == 'TS0225_LINPTECH_RADAR'} ${descMap.cluster == 'E002'} ${descMap.attrId == 'E00A'} ${settings?.ignoreDistance == true}" // library marker kkossev.deviceProfileLib, line 911
    if (state.deviceProfile == 'TS0225_LINPTECH_RADAR' && descMap.cluster == 'E002' && descMap.attrId == 'E00A' && settings?.ignoreDistance == true) { return true } // library marker kkossev.deviceProfileLib, line 912
    if (!(descMap?.clusterId == 'EF00' && (descMap?.command in ['01', '02']))) { return false } // library marker kkossev.deviceProfileLib, line 913
    if (descMap?.data?.size <= 2) { return false } // library marker kkossev.deviceProfileLib, line 914
    int dp = zigbee.convertHexToInt(descMap.data[2]) // library marker kkossev.deviceProfileLib, line 915
    List spammyList = deviceProfilesV3[getDeviceProfile()]?.spammyDPsToNotTrace as List // library marker kkossev.deviceProfileLib, line 916
    return (spammyList != null && (dp in spammyList)) // library marker kkossev.deviceProfileLib, line 917
} // library marker kkossev.deviceProfileLib, line 918

// all DPs are spammy - sent periodically! // library marker kkossev.deviceProfileLib, line 920
public boolean isSpammyDeviceProfile() { // library marker kkossev.deviceProfileLib, line 921
    if (deviceProfilesV3 == null || deviceProfilesV3[getDeviceProfile()] == null) { return false } // library marker kkossev.deviceProfileLib, line 922
    Boolean isSpammy = deviceProfilesV3[getDeviceProfile()]?.device?.isSpammy ?: false // library marker kkossev.deviceProfileLib, line 923
    return isSpammy // library marker kkossev.deviceProfileLib, line 924
} // library marker kkossev.deviceProfileLib, line 925

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 927
List<Object> compareAndConvertStrings(final Map foundItem, String tuyaValue, String hubitatValue) { // library marker kkossev.deviceProfileLib, line 928
    String convertedValue = tuyaValue // library marker kkossev.deviceProfileLib, line 929
    boolean isEqual    = ((tuyaValue  as String) == (hubitatValue as String))      // because the events(attributes) are always strings // library marker kkossev.deviceProfileLib, line 930
    if (foundItem?.scale != null || foundItem?.scale != 0 || foundItem?.scale != 1) { // library marker kkossev.deviceProfileLib, line 931
        logTrace "compareAndConvertStrings: scaling: foundItem.scale=${foundItem.scale} tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 932
    } // library marker kkossev.deviceProfileLib, line 933
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 934
} // library marker kkossev.deviceProfileLib, line 935

List<Object> compareAndConvertNumbers(final Map foundItem, int tuyaValue, int hubitatValue) { // library marker kkossev.deviceProfileLib, line 937
    Integer convertedValue // library marker kkossev.deviceProfileLib, line 938
    boolean isEqual // library marker kkossev.deviceProfileLib, line 939
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) {    // compare as integer // library marker kkossev.deviceProfileLib, line 940
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 941
    } // library marker kkossev.deviceProfileLib, line 942
    else { // library marker kkossev.deviceProfileLib, line 943
        convertedValue  = ((tuyaValue as double) / (foundItem.scale as double)) as int // library marker kkossev.deviceProfileLib, line 944
    } // library marker kkossev.deviceProfileLib, line 945
    isEqual = ((convertedValue as int) == (hubitatValue as int)) // library marker kkossev.deviceProfileLib, line 946
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 947
} // library marker kkossev.deviceProfileLib, line 948

List<Object> compareAndConvertDecimals(final Map foundItem, double tuyaValue, double hubitatValue) { // library marker kkossev.deviceProfileLib, line 950
    Double convertedValue // library marker kkossev.deviceProfileLib, line 951
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 952
        convertedValue = tuyaValue as double // library marker kkossev.deviceProfileLib, line 953
    } // library marker kkossev.deviceProfileLib, line 954
    else { // library marker kkossev.deviceProfileLib, line 955
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 956
    } // library marker kkossev.deviceProfileLib, line 957
    isEqual = Math.abs((convertedValue as double) - (hubitatValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 958
    logTrace  "compareAndConvertDecimals: tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 959
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 960
} // library marker kkossev.deviceProfileLib, line 961

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 963
List<Object> compareAndConvertEnumKeys(final Map foundItem, int tuyaValue, hubitatValue) { // library marker kkossev.deviceProfileLib, line 964
    //logTrace "compareAndConvertEnumKeys: tuyaValue=${tuyaValue} hubitatValue=${hubitatValue}" // library marker kkossev.deviceProfileLib, line 965
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 966
    def convertedValue // library marker kkossev.deviceProfileLib, line 967
    if (foundItem?.scale == null || foundItem?.scale == 0 || foundItem?.scale == 1) { // library marker kkossev.deviceProfileLib, line 968
        convertedValue = tuyaValue as int // library marker kkossev.deviceProfileLib, line 969
        isEqual = ((convertedValue as int) == (safeToInt(hubitatValue))) // library marker kkossev.deviceProfileLib, line 970
    } // library marker kkossev.deviceProfileLib, line 971
    else {  // scaled value - divide by scale // library marker kkossev.deviceProfileLib, line 972
        double hubitatSafeValue = safeToDouble(hubitatValue, -1.0) // library marker kkossev.deviceProfileLib, line 973
        convertedValue = (tuyaValue as double) / (foundItem.scale as double) // library marker kkossev.deviceProfileLib, line 974
        if (hubitatSafeValue == -1.0) { // library marker kkossev.deviceProfileLib, line 975
            isEqual = false // library marker kkossev.deviceProfileLib, line 976
        } // library marker kkossev.deviceProfileLib, line 977
        else { // compare as double (float) // library marker kkossev.deviceProfileLib, line 978
            isEqual = Math.abs((convertedValue as double) - (hubitatSafeValue as double)) < 0.001 // library marker kkossev.deviceProfileLib, line 979
        } // library marker kkossev.deviceProfileLib, line 980
    } // library marker kkossev.deviceProfileLib, line 981
    //logTrace  "compareAndConvertEnumKeys:  tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} convertedValue=${convertedValue} to hubitatValue=${hubitatValue} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 982
    return [isEqual, convertedValue] // library marker kkossev.deviceProfileLib, line 983
} // library marker kkossev.deviceProfileLib, line 984

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.deviceProfileLib, line 986
List<Object> compareAndConvertTuyaToHubitatPreferenceValue(final Map foundItem, fncmd, preference) { // library marker kkossev.deviceProfileLib, line 987
    if (foundItem == null || fncmd == null || preference == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 988
    if (foundItem?.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 989
    boolean isEqual // library marker kkossev.deviceProfileLib, line 990
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 991
    def tuyaValueScaled     // could be integer or float // library marker kkossev.deviceProfileLib, line 992
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 993
    def preferenceValue = settings[foundItem.name] // library marker kkossev.deviceProfileLib, line 994
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 995
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 996
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 997
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: bool: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 998
            break // library marker kkossev.deviceProfileLib, line 999
        case 'enum' :       // [0:"inactive", 1:"active"]   map:['75': '0.75 meters', '150': '1.50 meters', '225': '2.25 meters'] // library marker kkossev.deviceProfileLib, line 1000
            Integer scale = (foundItem.scale ?: 0 ) as int // library marker kkossev.deviceProfileLib, line 1001
            if (scale != null && scale != 0 && scale != 1) { // library marker kkossev.deviceProfileLib, line 1002
                preferenceValue = preferenceValue.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1003
                /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1004
                preference = preference.toString().replace('[', '').replace(']', '') // library marker kkossev.deviceProfileLib, line 1005
                logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: scale=${scale} fncmd=${fncmd} preference=${preference} preferenceValue=${preferenceValue} safeToDouble(fncmd)=${safeToDouble(fncmd)} safeToDouble(preference)=${safeToDouble(preference)}" // library marker kkossev.deviceProfileLib, line 1006
                (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1007
            } // library marker kkossev.deviceProfileLib, line 1008
            else { // library marker kkossev.deviceProfileLib, line 1009
                (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1010
            } // library marker kkossev.deviceProfileLib, line 1011
            logTrace "compareAndConvertTuyaToHubitatPreferenceValue: enum: preference = ${preference} <b>type=${foundItem.type}</b>  foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> preferenceValue=${preferenceValue} tuyaValueScaled=${tuyaValueScaled} fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1012
            break // library marker kkossev.deviceProfileLib, line 1013
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1014
        case 'number' : // library marker kkossev.deviceProfileLib, line 1015
            (isEqual, tuyaValueScaled) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(preference)) // library marker kkossev.deviceProfileLib, line 1016
            logTrace "tuyaValue=${tuyaValue} tuyaValueScaled=${tuyaValueScaled} preferenceValue = ${preference} isEqual=${isEqual}" // library marker kkossev.deviceProfileLib, line 1017
            break // library marker kkossev.deviceProfileLib, line 1018
       case 'decimal' : // library marker kkossev.deviceProfileLib, line 1019
            (isEqual, tuyaValueScaled) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(preference)) // library marker kkossev.deviceProfileLib, line 1020
            logTrace "comparing as float tuyaValue=${tuyaValue} foundItem.scale=${foundItem.scale} tuyaValueScaled=${tuyaValueScaled} to preferenceValue = ${preference}" // library marker kkossev.deviceProfileLib, line 1021
            break // library marker kkossev.deviceProfileLib, line 1022
        default : // library marker kkossev.deviceProfileLib, line 1023
            logDebug 'compareAndConvertTuyaToHubitatPreferenceValue: unsupported type %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1024
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1025
    } // library marker kkossev.deviceProfileLib, line 1026
    if (isEqual == false) { // library marker kkossev.deviceProfileLib, line 1027
        logDebug "compareAndConvertTuyaToHubitatPreferenceValue: preference = ${preference} <b>type=${foundItem.type}</b> foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> tuyaValueScaled=${tuyaValueScaled} (scale=${foundItem.scale}) fncmd=${fncmd}" // library marker kkossev.deviceProfileLib, line 1028
    } // library marker kkossev.deviceProfileLib, line 1029
    // // library marker kkossev.deviceProfileLib, line 1030
    return [isEqual, tuyaValueScaled] // library marker kkossev.deviceProfileLib, line 1031
} // library marker kkossev.deviceProfileLib, line 1032

// // library marker kkossev.deviceProfileLib, line 1034
// called from process TuyaDP from DeviceProfile() // library marker kkossev.deviceProfileLib, line 1035
// compares the value of the DP foundItem against a Preference with the same name // library marker kkossev.deviceProfileLib, line 1036
// returns: (two results!) // library marker kkossev.deviceProfileLib, line 1037
//    isEqual : true  - if the Tuya DP value equals to the DP calculated value (no need to update the preference) // library marker kkossev.deviceProfileLib, line 1038
//            : true  - if a preference with the same name does not exist (no preference value to update) // library marker kkossev.deviceProfileLib, line 1039
//    isEqual : false - the reported DP value is different than the corresponding preference (the preference needs to be updated!) // library marker kkossev.deviceProfileLib, line 1040
// // library marker kkossev.deviceProfileLib, line 1041
//    hubitatEventValue - the converted DP value, scaled (divided by the scale factor) to match the corresponding preference type value // library marker kkossev.deviceProfileLib, line 1042
// // library marker kkossev.deviceProfileLib, line 1043
//  TODO: refactor! // library marker kkossev.deviceProfileLib, line 1044
// // library marker kkossev.deviceProfileLib, line 1045
/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1046
List<Object> compareAndConvertTuyaToHubitatEventValue(Map foundItem, int fncmd, boolean doNotTrace=false) { // library marker kkossev.deviceProfileLib, line 1047
    if (foundItem == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1048
    if (foundItem.type == null) { return [true, 'none'] } // library marker kkossev.deviceProfileLib, line 1049
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1050
    def hubitatEventValue   // could be integer or float or string // library marker kkossev.deviceProfileLib, line 1051
    boolean isEqual // library marker kkossev.deviceProfileLib, line 1052
    switch (foundItem.type) { // library marker kkossev.deviceProfileLib, line 1053
        case 'bool' :       // [0:"OFF", 1:"ON"] // library marker kkossev.deviceProfileLib, line 1054
            (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1055
            break // library marker kkossev.deviceProfileLib, line 1056
        case 'enum' :       // [0:"inactive", 1:"active"]  foundItem.map=[75:0.75 meters, 150:1.50 meters, 225:2.25 meters, 300:3.00 meters, 375:3.75 meters, 450:4.50 meters] // library marker kkossev.deviceProfileLib, line 1057
            logTrace "compareAndConvertTuyaToHubitatEventValue: enum: foundItem.scale=${foundItem.scale}, fncmd=${fncmd}, device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))} map=${foundItem.map}" // library marker kkossev.deviceProfileLib, line 1058
            Object latestEvent = device.currentState(foundItem.name) // library marker kkossev.deviceProfileLib, line 1059
            String dataType = latestEvent?.dataType // library marker kkossev.deviceProfileLib, line 1060
            logTrace "latestEvent is dataType is ${dataType}" // library marker kkossev.deviceProfileLib, line 1061
            // if the attribute is of a type enum, the value is a string. Compare the string values! // library marker kkossev.deviceProfileLib, line 1062
            if (dataType == 'ENUM') { // library marker kkossev.deviceProfileLib, line 1063
                (isEqual, hubitatEventValue) = compareAndConvertStrings(foundItem, foundItem.map[fncmd as int] ?: 'unknown', device.currentValue(foundItem.name) ?: 'unknown') // library marker kkossev.deviceProfileLib, line 1064
            } // library marker kkossev.deviceProfileLib, line 1065
            else { // library marker kkossev.deviceProfileLib, line 1066
                (isEqual, hubitatEventValue) = compareAndConvertEnumKeys(foundItem, fncmd, device.currentValue(foundItem.name)) // library marker kkossev.deviceProfileLib, line 1067
            } // library marker kkossev.deviceProfileLib, line 1068
            logTrace "compareAndConvertTuyaToHubitatEventValue: after compareAndConvertStrings: isEqual=${isEqual} hubitatEventValue=${hubitatEventValue}" // library marker kkossev.deviceProfileLib, line 1069
            break // library marker kkossev.deviceProfileLib, line 1070
        case 'value' :      // depends on foundItem.scale // library marker kkossev.deviceProfileLib, line 1071
        case 'number' : // library marker kkossev.deviceProfileLib, line 1072
            //logTrace "compareAndConvertTuyaToHubitatEventValue: foundItem.scale=${foundItem.scale} fncmd=${fncmd} device.currentValue(${foundItem.name})=${(device.currentValue(foundItem.name))}" // library marker kkossev.deviceProfileLib, line 1073
            (isEqual, hubitatEventValue) = compareAndConvertNumbers(foundItem, safeToInt(fncmd), safeToInt(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1074
            break // library marker kkossev.deviceProfileLib, line 1075
        case 'decimal' : // library marker kkossev.deviceProfileLib, line 1076
            (isEqual, hubitatEventValue) = compareAndConvertDecimals(foundItem, safeToDouble(fncmd), safeToDouble(device.currentValue(foundItem.name))) // library marker kkossev.deviceProfileLib, line 1077
            break // library marker kkossev.deviceProfileLib, line 1078
        default : // library marker kkossev.deviceProfileLib, line 1079
            logDebug 'compareAndConvertTuyaToHubitatEventValue: unsupported dpType %{foundItem.type}' // library marker kkossev.deviceProfileLib, line 1080
            return [true, 'none']   // fallback - assume equal // library marker kkossev.deviceProfileLib, line 1081
    } // library marker kkossev.deviceProfileLib, line 1082
    //if (!doNotTrace)  log.trace "foundItem=${foundItem.name} <b>isEqual=${isEqual}</b> attrValue=${attrValue} fncmd=${fncmd}  foundItem.scale=${foundItem.scale } valueScaled=${valueScaled} " // library marker kkossev.deviceProfileLib, line 1083
    return [isEqual, hubitatEventValue] // library marker kkossev.deviceProfileLib, line 1084
} // library marker kkossev.deviceProfileLib, line 1085

public Integer preProc(final Map foundItem, int fncmd_orig) { // library marker kkossev.deviceProfileLib, line 1087
    Integer fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1088
    if (foundItem == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1089
    if (foundItem.preProc == null) { return fncmd } // library marker kkossev.deviceProfileLib, line 1090
    String preProcFunction = foundItem.preProc // library marker kkossev.deviceProfileLib, line 1091
    //logDebug "preProc: foundItem.preProc = ${preProcFunction}" // library marker kkossev.deviceProfileLib, line 1092
    // check if preProc method exists // library marker kkossev.deviceProfileLib, line 1093
    if (!this.respondsTo(preProcFunction)) { // library marker kkossev.deviceProfileLib, line 1094
        logDebug "preProc: function <b>${preProcFunction}</b> not found" // library marker kkossev.deviceProfileLib, line 1095
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1096
    } // library marker kkossev.deviceProfileLib, line 1097
    // execute the preProc function // library marker kkossev.deviceProfileLib, line 1098
    try { // library marker kkossev.deviceProfileLib, line 1099
        fncmd = "$preProcFunction"(fncmd_orig) // library marker kkossev.deviceProfileLib, line 1100
    } // library marker kkossev.deviceProfileLib, line 1101
    catch (e) { // library marker kkossev.deviceProfileLib, line 1102
        logWarn "preProc: Exception '${e}' caught while processing <b>$preProcFunction</b>(<b>$fncmd_orig</b>) (val=${fncmd}))" // library marker kkossev.deviceProfileLib, line 1103
        return fncmd_orig // library marker kkossev.deviceProfileLib, line 1104
    } // library marker kkossev.deviceProfileLib, line 1105
    //logDebug "setFunction result is ${fncmd}" // library marker kkossev.deviceProfileLib, line 1106
    return fncmd // library marker kkossev.deviceProfileLib, line 1107
} // library marker kkossev.deviceProfileLib, line 1108

// TODO: refactor! // library marker kkossev.deviceProfileLib, line 1110
// called from custom drivers (customParseE002Cluster customParseFC11Cluster customParseOccupancyCluster ...) // library marker kkossev.deviceProfileLib, line 1111
// returns true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1112
public boolean processClusterAttributeFromDeviceProfile(final Map descMap) { // library marker kkossev.deviceProfileLib, line 1113
    logTrace "processClusterAttributeFromDeviceProfile: descMap = ${descMap}" // library marker kkossev.deviceProfileLib, line 1114
    if (state.deviceProfile == null)  { logTrace '<b>state.deviceProfile is missing!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1115
    if (descMap == null || descMap == [:] || descMap.cluster == null || descMap.attrId == null || descMap.value == null) { logTrace '<b>descMap is missing cluster, attribute or value!<b>'; return false } // library marker kkossev.deviceProfileLib, line 1116

    List<Map> attribMap = deviceProfilesV3[state.deviceProfile]?.attributes // library marker kkossev.deviceProfileLib, line 1118
    if (attribMap == null || attribMap.isEmpty()) { return false }    // no any attributes are defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1119

    String clusterAttribute = "0x${descMap.cluster}:0x${descMap.attrId}" // library marker kkossev.deviceProfileLib, line 1121
    int value // library marker kkossev.deviceProfileLib, line 1122
    try { // library marker kkossev.deviceProfileLib, line 1123
        value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.deviceProfileLib, line 1124
    } // library marker kkossev.deviceProfileLib, line 1125
    catch (e) { // library marker kkossev.deviceProfileLib, line 1126
        logWarn "processClusterAttributeFromDeviceProfile: exception ${e} caught while converting hex value ${descMap.value} to integer" // library marker kkossev.deviceProfileLib, line 1127
        return false // library marker kkossev.deviceProfileLib, line 1128
    } // library marker kkossev.deviceProfileLib, line 1129
    Map foundItem = attribMap.find { it['at'] == clusterAttribute } // library marker kkossev.deviceProfileLib, line 1130
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1131
        // clusterAttribute was not found into the attributes list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1132
        // updateStateUnknownclusterAttribute(descMap) // library marker kkossev.deviceProfileLib, line 1133
        // continue processing the descMap report in the old code ... // library marker kkossev.deviceProfileLib, line 1134
        logTrace "processClusterAttributeFromDeviceProfile: clusterAttribute ${clusterAttribute} was not found in the attributes list for this deviceProfile ${DEVICE?.description}" // library marker kkossev.deviceProfileLib, line 1135
        return false // library marker kkossev.deviceProfileLib, line 1136
    } // library marker kkossev.deviceProfileLib, line 1137
    return processFoundItem(descMap, foundItem, value, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1138
} // library marker kkossev.deviceProfileLib, line 1139

/** // library marker kkossev.deviceProfileLib, line 1141
 * Called from standardProcessTuyaDP method in commonLib // library marker kkossev.deviceProfileLib, line 1142
 * // library marker kkossev.deviceProfileLib, line 1143
 * Processes a Tuya DP (Data Point) received from the device, based on the device profile and its defined Tuya DPs. // library marker kkossev.deviceProfileLib, line 1144
 * If a preference exists for the DP, it updates the preference value and sends an event if the DP is declared as an attribute. // library marker kkossev.deviceProfileLib, line 1145
 * If no preference exists for the DP, it logs the DP value as an info message. // library marker kkossev.deviceProfileLib, line 1146
 * If the DP is spammy (not needed for anything), it does not perform any further processing. // library marker kkossev.deviceProfileLib, line 1147
 * // library marker kkossev.deviceProfileLib, line 1148
 * @return true if the DP was processed successfully, false otherwise. // library marker kkossev.deviceProfileLib, line 1149
 */ // library marker kkossev.deviceProfileLib, line 1150
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.deviceProfileLib, line 1151
public boolean processTuyaDPfromDeviceProfile(final Map descMap, final int dp, final int dp_id, final int fncmd_orig, final int dp_len) { // library marker kkossev.deviceProfileLib, line 1152
    int fncmd = fncmd_orig // library marker kkossev.deviceProfileLib, line 1153
    if (state.deviceProfile == null)  { return false } // library marker kkossev.deviceProfileLib, line 1154
    if (isSpammyDPsToIgnore(descMap)) { return true  }       // do not perform any further processing, if this is a spammy report that is not needed for anyhting (such as the LED status) // library marker kkossev.deviceProfileLib, line 1155

    List<Map> tuyaDPsMap = deviceProfilesV3[state.deviceProfile]?.tuyaDPs // library marker kkossev.deviceProfileLib, line 1157
    if (tuyaDPsMap == null || tuyaDPsMap == [:]) { return false }    // no any Tuya DPs defined in the Device Profile // library marker kkossev.deviceProfileLib, line 1158

    Map foundItem = tuyaDPsMap.find { it['dp'] == (dp as int) } // library marker kkossev.deviceProfileLib, line 1160
    if (foundItem == null || foundItem == [:]) { // library marker kkossev.deviceProfileLib, line 1161
        // DP was not found into the tuyaDPs list for this particular deviceProfile // library marker kkossev.deviceProfileLib, line 1162
//      updateStateUnknownDPs(descMap, dp, dp_id, fncmd, dp_len)    // TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.deviceProfileLib, line 1163
        // continue processing the DP report in the old code ... // library marker kkossev.deviceProfileLib, line 1164
        return false // library marker kkossev.deviceProfileLib, line 1165
    } // library marker kkossev.deviceProfileLib, line 1166
    return processFoundItem(descMap, foundItem, fncmd, isSpammyDPsToNotTrace(descMap)) // library marker kkossev.deviceProfileLib, line 1167
} // library marker kkossev.deviceProfileLib, line 1168

/* // library marker kkossev.deviceProfileLib, line 1170
 * deviceProfile DP processor : updates the preference value and calls a custom handler or sends an event if the DP is declared as an attribute in the device profile // library marker kkossev.deviceProfileLib, line 1171
 */ // library marker kkossev.deviceProfileLib, line 1172
private boolean processFoundItem(final Map descMap, final Map foundItem, int value, boolean doNotTrace = false) { // library marker kkossev.deviceProfileLib, line 1173
    if (foundItem == null) { return false } // library marker kkossev.deviceProfileLib, line 1174
    // added 10/31/2023 - preProc the attribute value if needed // library marker kkossev.deviceProfileLib, line 1175
    if (foundItem.preProc != null) { // library marker kkossev.deviceProfileLib, line 1176
        /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1177
        Integer preProcValue = preProc(foundItem, value) // library marker kkossev.deviceProfileLib, line 1178
        if (preProcValue == null) { logDebug "processFoundItem: preProc returned null for ${foundItem.name} value ${value} -> further processing is skipped!" ; return true } // library marker kkossev.deviceProfileLib, line 1179
        if (preProcValue != value) { // library marker kkossev.deviceProfileLib, line 1180
            logDebug "processFoundItem: <b>preProc</b> changed ${foundItem.name} value to ${preProcValue}" // library marker kkossev.deviceProfileLib, line 1181
            /* groovylint-disable-next-line ParameterReassignment */ // library marker kkossev.deviceProfileLib, line 1182
            value = preProcValue as int // library marker kkossev.deviceProfileLib, line 1183
        } // library marker kkossev.deviceProfileLib, line 1184
    } // library marker kkossev.deviceProfileLib, line 1185
    else { logTrace "processFoundItem: no preProc for ${foundItem.name}" } // library marker kkossev.deviceProfileLib, line 1186

    String name = foundItem.name                                   // preference name as in the attributes map // library marker kkossev.deviceProfileLib, line 1188
    String existingPrefValue = settings[foundItem.name] ?: 'none'  // existing preference value // library marker kkossev.deviceProfileLib, line 1189
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1190
    def preferenceValue = null   // preference value // library marker kkossev.deviceProfileLib, line 1191
    //log.trace "settings=${settings}" // library marker kkossev.deviceProfileLib, line 1192
    boolean preferenceExists = DEVICE?.preferences?.containsKey(foundItem.name)         // check if there is an existing preference for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1193
    //log.trace "preferenceExists=${preferenceExists}" // library marker kkossev.deviceProfileLib, line 1194
    boolean isAttribute = device.hasAttribute(foundItem.name)    // check if there is such a attribute for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1195
    boolean isEqual = false // library marker kkossev.deviceProfileLib, line 1196
    boolean wasChanged = false // library marker kkossev.deviceProfileLib, line 1197
    if (!doNotTrace) { logTrace "processFoundItem: name=${foundItem.name}, isAttribute=${isAttribute}, preferenceExists=${preferenceExists}, existingPrefValue=${existingPrefValue} (type ${foundItem.type}, rw=${foundItem.rw}) value is ${value} (description: ${foundItem.description})" } // library marker kkossev.deviceProfileLib, line 1198
    // check if the clusterAttribute has the same value as the last one, or the value has changed // library marker kkossev.deviceProfileLib, line 1199
    // the previous value may be stored in an attribute, as a preference, as both attribute and preference or not stored anywhere ... // library marker kkossev.deviceProfileLib, line 1200
    String unitText     = foundItem.unit != null ? "$foundItem.unit" : '' // library marker kkossev.deviceProfileLib, line 1201
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1202
    def valueScaled    // can be number or decimal or string // library marker kkossev.deviceProfileLib, line 1203
    String descText = descText  = "${name} is ${value} ${unitText}"    // the default description text for log events // library marker kkossev.deviceProfileLib, line 1204

    // TODO - check if clusterAttribute is in the list of the received state.attributes - then we have something to compare ! // library marker kkossev.deviceProfileLib, line 1206
    if (!isAttribute && !preferenceExists) {                    // if the previous value of this clusterAttribute is not stored anywhere - just seend an Info log if Debug is enabled // library marker kkossev.deviceProfileLib, line 1207
        if (!doNotTrace) {                                      // only if the clusterAttribute is not in the spammy list // library marker kkossev.deviceProfileLib, line 1208
            logTrace "processFoundItem: no preference or attribute for ${name} - just log the value, if not equal to the last one..." // library marker kkossev.deviceProfileLib, line 1209
            // TODO - scaledValue ????? TODO! // library marker kkossev.deviceProfileLib, line 1210
            descText  = "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1211
            if (settings.logEnable) { logInfo "${descText }" }  // only when Debug is enabled! // library marker kkossev.deviceProfileLib, line 1212
        } // library marker kkossev.deviceProfileLib, line 1213
        return true         // no more processing is needed, as this clusterAttribute is NOT a preference and NOT an attribute // library marker kkossev.deviceProfileLib, line 1214
    } // library marker kkossev.deviceProfileLib, line 1215

    // first, check if there is a preference defined in the deviceProfileV3 to be updated // library marker kkossev.deviceProfileLib, line 1217
    if (preferenceExists && !doNotTrace) {  // do not even try to automatically update the preference if it is in the spammy list! - added 04/23/2024 // library marker kkossev.deviceProfileLib, line 1218
        // preference exists and its's value is extracted // library marker kkossev.deviceProfileLib, line 1219
        (isEqual, preferenceValue)  = compareAndConvertTuyaToHubitatPreferenceValue(foundItem, value, existingPrefValue) // library marker kkossev.deviceProfileLib, line 1220
        logTrace "processFoundItem: preference '${name}' exists with existingPrefValue ${existingPrefValue} (type ${foundItem.type}) -> <b>isEqual=${isEqual} preferenceValue=${preferenceValue}</b>" // library marker kkossev.deviceProfileLib, line 1221
        if (isEqual == true) {              // the preference is not changed - do nothing // library marker kkossev.deviceProfileLib, line 1222
            //log.trace "doNotTrace=${doNotTrace} isSpammyDeviceProfile=${isSpammyDeviceProfile()}" // library marker kkossev.deviceProfileLib, line 1223
            if (!(doNotTrace || isSpammyDeviceProfile())) {                                 // the clusterAttribute value is the same as the preference value - no need to update the preference // library marker kkossev.deviceProfileLib, line 1224
                logDebug "processFoundItem: no change: preference '${name}' existingPrefValue ${existingPrefValue} equals scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1225
            } // library marker kkossev.deviceProfileLib, line 1226
        } // library marker kkossev.deviceProfileLib, line 1227
        else {      // the preferences has changed - update it! // library marker kkossev.deviceProfileLib, line 1228
            String scaledPreferenceValue = preferenceValue // library marker kkossev.deviceProfileLib, line 1229
            if (foundItem.type == 'enum' && foundItem.scale != null && foundItem.scale != 0 && foundItem.scale != 1) { // library marker kkossev.deviceProfileLib, line 1230
                scaledPreferenceValue = ((preferenceValue * safeToInt(foundItem.scale)) as int).toString() // library marker kkossev.deviceProfileLib, line 1231
            } // library marker kkossev.deviceProfileLib, line 1232
            logDebug "processFoundItem: preference '${name}' value ${existingPrefValue} <b>differs</b> from the new scaled value ${preferenceValue} (clusterAttribute raw value ${value})" // library marker kkossev.deviceProfileLib, line 1233
            if (settings.logEnable) { logInfo "updating the preference '${name}' from ${existingPrefValue} to ${preferenceValue} (scaledPreferenceValue=${scaledPreferenceValue}, type=${foundItem.type})" } // library marker kkossev.deviceProfileLib, line 1234
            try { // library marker kkossev.deviceProfileLib, line 1235
                device.updateSetting("${name}", [value:scaledPreferenceValue, type:foundItem.type]) // library marker kkossev.deviceProfileLib, line 1236
                wasChanged = true // library marker kkossev.deviceProfileLib, line 1237
            } // library marker kkossev.deviceProfileLib, line 1238
            catch (e) { // library marker kkossev.deviceProfileLib, line 1239
                logWarn "exception ${e} caught while updating preference ${name} to ${preferenceValue}, type ${foundItem.type}" // library marker kkossev.deviceProfileLib, line 1240
            } // library marker kkossev.deviceProfileLib, line 1241
        } // library marker kkossev.deviceProfileLib, line 1242
    } // library marker kkossev.deviceProfileLib, line 1243
    else {    // no preference exists for this clusterAttribute // library marker kkossev.deviceProfileLib, line 1244
        // if not in the spammy list - log it! // library marker kkossev.deviceProfileLib, line 1245
        unitText = foundItem.unit != null ? "$foundItem.unit" : ''      // TODO - check if unitText must be declared here or outside the if block // library marker kkossev.deviceProfileLib, line 1246
        //logInfo "${name} is ${value} ${unitText}" // library marker kkossev.deviceProfileLib, line 1247
    } // library marker kkossev.deviceProfileLib, line 1248

    // second, send an event if this is declared as an attribute! // library marker kkossev.deviceProfileLib, line 1250
    if (isAttribute) {                                         // this clusterAttribute has an attribute that must be sent in an Event // library marker kkossev.deviceProfileLib, line 1251
        (isEqual, valueScaled) = compareAndConvertTuyaToHubitatEventValue(foundItem, value, doNotTrace) // library marker kkossev.deviceProfileLib, line 1252
        if (isEqual == false) { logTrace "attribute '${name}' exists (type ${foundItem.type}), value ${value} -> <b>isEqual=${isEqual} valueScaled=${valueScaled}</b> wasChanged=${wasChanged}" } // library marker kkossev.deviceProfileLib, line 1253
        descText  = "${name} is ${valueScaled} ${unitText}" // library marker kkossev.deviceProfileLib, line 1254
        if (settings?.logEnable == true) { descText += " (raw:${value})" } // library marker kkossev.deviceProfileLib, line 1255
        if (state.states != null && state.states['isRefresh'] == true) { descText += ' [refresh]' } // library marker kkossev.deviceProfileLib, line 1256
        if (isEqual && !wasChanged) {                        // this DP report has the same value as the last one - just send a debug log and move along! // library marker kkossev.deviceProfileLib, line 1257
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1258
                if (settings.logEnable) { logDebug "${descText } (no change)" } // library marker kkossev.deviceProfileLib, line 1259
            } // library marker kkossev.deviceProfileLib, line 1260
            // patch for inverted motion sensor 2-in-1 // library marker kkossev.deviceProfileLib, line 1261
            if (name == 'motion' && is2in1()) {                 // TODO - remove the patch !! // library marker kkossev.deviceProfileLib, line 1262
                logDebug 'patch for inverted motion sensor 2-in-1' // library marker kkossev.deviceProfileLib, line 1263
            // continue ... // library marker kkossev.deviceProfileLib, line 1264
            } // library marker kkossev.deviceProfileLib, line 1265
            else { // library marker kkossev.deviceProfileLib, line 1266
                if (state.states != null && state.states['isRefresh'] == true) { // library marker kkossev.deviceProfileLib, line 1267
                    logTrace 'isRefresh = true - continue and send an event, although there was no change...' // library marker kkossev.deviceProfileLib, line 1268
                } // library marker kkossev.deviceProfileLib, line 1269
                else { // library marker kkossev.deviceProfileLib, line 1270
                    return true       // we are done (if there was potentially a preference, it should be already set to the same value) // library marker kkossev.deviceProfileLib, line 1271
                } // library marker kkossev.deviceProfileLib, line 1272
            } // library marker kkossev.deviceProfileLib, line 1273
        } // library marker kkossev.deviceProfileLib, line 1274

        // clusterAttribute value (value) is not equal to the attribute last value or was changed- we must send an update event! // library marker kkossev.deviceProfileLib, line 1276
        int divider = safeToInt(foundItem.scale ?: 1) ?: 1 // library marker kkossev.deviceProfileLib, line 1277
        float valueCorrected = value / divider // library marker kkossev.deviceProfileLib, line 1278
        if (!doNotTrace) { logTrace "value=${value} foundItem.scale=${foundItem.scale}  divider=${divider} valueCorrected=${valueCorrected}" } // library marker kkossev.deviceProfileLib, line 1279
        // process the events in the device specific driver.. // library marker kkossev.deviceProfileLib, line 1280
        if (this.respondsTo('customProcessDeviceProfileEvent')) { // library marker kkossev.deviceProfileLib, line 1281
            customProcessDeviceProfileEvent(descMap, name, valueScaled, unitText, descText)             // used in Zigbee_TRV // library marker kkossev.deviceProfileLib, line 1282
        } // library marker kkossev.deviceProfileLib, line 1283
        else { // library marker kkossev.deviceProfileLib, line 1284
            // no custom handler - send the event as usual // library marker kkossev.deviceProfileLib, line 1285
            sendEvent(name : name, value : valueScaled, unit:unitText, descriptionText: descText, type: 'physical', isStateChange: true)    // attribute value is changed - send an event ! // library marker kkossev.deviceProfileLib, line 1286
            if (!doNotTrace) { // library marker kkossev.deviceProfileLib, line 1287
                logTrace "event ${name} sent w/ value ${valueScaled}" // library marker kkossev.deviceProfileLib, line 1288
                logInfo "${descText}"   // TODO - send info log only if the value has changed?   // TODO - check whether Info log will be sent also for spammy clusterAttribute ? // library marker kkossev.deviceProfileLib, line 1289
            } // library marker kkossev.deviceProfileLib, line 1290
        } // library marker kkossev.deviceProfileLib, line 1291
    } // library marker kkossev.deviceProfileLib, line 1292
    return true     // all processing was done here! // library marker kkossev.deviceProfileLib, line 1293
} // library marker kkossev.deviceProfileLib, line 1294

// not used ? (except for debugging)? TODO // library marker kkossev.deviceProfileLib, line 1296
public boolean validateAndFixPreferences(boolean debug=false) { // library marker kkossev.deviceProfileLib, line 1297
    //debug = true // library marker kkossev.deviceProfileLib, line 1298
    if (debug) { logTrace "validateAndFixPreferences: preferences=${DEVICE?.preferences}" } // library marker kkossev.deviceProfileLib, line 1299
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { logDebug "validateAndFixPreferences: no preferences defined for device profile ${getDeviceProfile()}" ; return false } // library marker kkossev.deviceProfileLib, line 1300
    int validationFailures = 0, validationFixes = 0, total = 0 // library marker kkossev.deviceProfileLib, line 1301
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.deviceProfileLib, line 1302
    def oldSettingValue, newValue // library marker kkossev.deviceProfileLib, line 1303
    String settingType = '' // library marker kkossev.deviceProfileLib, line 1304
    DEVICE?.preferences.each { // library marker kkossev.deviceProfileLib, line 1305
        Map foundMap = getPreferencesMapByName(it.key) // library marker kkossev.deviceProfileLib, line 1306
        if (foundMap == null || foundMap == [:]) { logDebug "validateAndFixPreferences: map not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1307
        settingType = device.getSettingType(it.key) ; oldSettingValue = device.getSetting(it.key) // library marker kkossev.deviceProfileLib, line 1308
        if (settingType == null) { logDebug "validateAndFixPreferences: settingType not found for preference ${it.key}" ; return false } // library marker kkossev.deviceProfileLib, line 1309
        if (debug) { logTrace "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) oldSettingValue = ${oldSettingValue} mapType = ${foundMap.type} settingType=${settingType}" } // library marker kkossev.deviceProfileLib, line 1310
        if (foundMap.type != settingType) { // library marker kkossev.deviceProfileLib, line 1311
            logDebug "validateAndFixPreferences: preference ${it.key} (dp=${it.value}) new mapType = ${foundMap.type} <b>differs</b> from the old settingType=${settingType} (oldSettingValue = ${oldSettingValue}) " // library marker kkossev.deviceProfileLib, line 1312
            validationFailures ++ // library marker kkossev.deviceProfileLib, line 1313
            // remove the setting and create a new one using the foundMap.type // library marker kkossev.deviceProfileLib, line 1314
            try { // library marker kkossev.deviceProfileLib, line 1315
                device.removeSetting(it.key) ; logDebug "validateAndFixPreferences: removing setting ${it.key}" // library marker kkossev.deviceProfileLib, line 1316
            } catch (e) { // library marker kkossev.deviceProfileLib, line 1317
                logWarn "validateAndFixPreferences: exception ${e} caught while removing setting ${it.key}" ; return false // library marker kkossev.deviceProfileLib, line 1318
            } // library marker kkossev.deviceProfileLib, line 1319
            // first, try to use the old setting value // library marker kkossev.deviceProfileLib, line 1320
            try { // library marker kkossev.deviceProfileLib, line 1321
                // correct the oldSettingValue type // library marker kkossev.deviceProfileLib, line 1322
                if (foundMap.type == 'decimal')     { newValue = oldSettingValue.toDouble() } // library marker kkossev.deviceProfileLib, line 1323
                else if (foundMap.type == 'number') { newValue = oldSettingValue.toInteger() } // library marker kkossev.deviceProfileLib, line 1324
                else if (foundMap.type == 'bool')   { newValue = oldSettingValue == 'true' ? 1 : 0 } // library marker kkossev.deviceProfileLib, line 1325
                else if (foundMap.type == 'enum') { // library marker kkossev.deviceProfileLib, line 1326
                    // check if the old settingValue was 'true' or 'false' and convert it to 1 or 0 // library marker kkossev.deviceProfileLib, line 1327
                    if (oldSettingValue == 'true' || oldSettingValue == 'false' || oldSettingValue == true || oldSettingValue == false) { // library marker kkossev.deviceProfileLib, line 1328
                        newValue = (oldSettingValue == 'true' || oldSettingValue == true) ? '1' : '0' // library marker kkossev.deviceProfileLib, line 1329
                    } // library marker kkossev.deviceProfileLib, line 1330
                    // check if there are any period chars in the foundMap.map string keys as String and format the settingValue as string with 2 decimals // library marker kkossev.deviceProfileLib, line 1331
                    else if (foundMap.map.keySet().toString().any { it.contains('.') }) { // library marker kkossev.deviceProfileLib, line 1332
                        newValue = String.format('%.2f', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1333
                    } else { // library marker kkossev.deviceProfileLib, line 1334
                        // format the settingValue as a string of the integer value // library marker kkossev.deviceProfileLib, line 1335
                        newValue = String.format('%d', oldSettingValue) // library marker kkossev.deviceProfileLib, line 1336
                    } // library marker kkossev.deviceProfileLib, line 1337
                } // library marker kkossev.deviceProfileLib, line 1338
                device.updateSetting(it.key, [value:newValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1339
                logDebug "validateAndFixPreferences: removed and updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1340
                validationFixes ++ // library marker kkossev.deviceProfileLib, line 1341
            } // library marker kkossev.deviceProfileLib, line 1342
            catch (e) { // library marker kkossev.deviceProfileLib, line 1343
                logWarn "validateAndFixPreferences: exception '${e}' caught while creating setting ${it.key} with type ${foundMap.type} to new type ${foundMap.type} with the old value ${oldSettingValue} to new value ${newValue}" // library marker kkossev.deviceProfileLib, line 1344
                // change the settingValue to the foundMap default value // library marker kkossev.deviceProfileLib, line 1345
                try { // library marker kkossev.deviceProfileLib, line 1346
                    settingValue = foundMap.defVal // library marker kkossev.deviceProfileLib, line 1347
                    device.updateSetting(it.key, [value:settingValue, type:foundMap.type]) // library marker kkossev.deviceProfileLib, line 1348
                    logDebug "validateAndFixPreferences: updated setting ${it.key} from old type ${settingType} to new type ${foundMap.type} with <b>default</b> value ${newValue} " // library marker kkossev.deviceProfileLib, line 1349
                    validationFixes ++ // library marker kkossev.deviceProfileLib, line 1350
                } catch (e2) { // library marker kkossev.deviceProfileLib, line 1351
                    logWarn "<b>validateAndFixPreferences: exception '${e2}' caught while setting default value ... Giving up!</b>" ; return false // library marker kkossev.deviceProfileLib, line 1352
                } // library marker kkossev.deviceProfileLib, line 1353
            } // library marker kkossev.deviceProfileLib, line 1354
        } // library marker kkossev.deviceProfileLib, line 1355
        total ++ // library marker kkossev.deviceProfileLib, line 1356
    } // library marker kkossev.deviceProfileLib, line 1357
    logDebug "validateAndFixPreferences: total = ${total} validationFailures = ${validationFailures} validationFixes = ${validationFixes}" // library marker kkossev.deviceProfileLib, line 1358
    return true // library marker kkossev.deviceProfileLib, line 1359
} // library marker kkossev.deviceProfileLib, line 1360

// command for debugging // library marker kkossev.deviceProfileLib, line 1362
public void printFingerprints() { // library marker kkossev.deviceProfileLib, line 1363
    deviceProfilesV3.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 1364
        profileMap.fingerprints?.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 1365
            logInfo "${fingerprint}" // library marker kkossev.deviceProfileLib, line 1366
        } // library marker kkossev.deviceProfileLib, line 1367
    } // library marker kkossev.deviceProfileLib, line 1368
} // library marker kkossev.deviceProfileLib, line 1369

// command for debugging // library marker kkossev.deviceProfileLib, line 1371
public void printPreferences() { // library marker kkossev.deviceProfileLib, line 1372
    logDebug "printPreferences: DEVICE?.preferences=${DEVICE?.preferences}" // library marker kkossev.deviceProfileLib, line 1373
    if (DEVICE != null && DEVICE?.preferences != null && DEVICE?.preferences != [:] && DEVICE?.device?.isDepricated != true) { // library marker kkossev.deviceProfileLib, line 1374
        (DEVICE?.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 1375
            Map inputMap = inputIt(key, true)   // debug = true // library marker kkossev.deviceProfileLib, line 1376
            if (inputMap != null && inputMap != [:]) { // library marker kkossev.deviceProfileLib, line 1377
                log.trace inputMap // library marker kkossev.deviceProfileLib, line 1378
            } // library marker kkossev.deviceProfileLib, line 1379
        } // library marker kkossev.deviceProfileLib, line 1380
    } // library marker kkossev.deviceProfileLib, line 1381
} // library marker kkossev.deviceProfileLib, line 1382

// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~
