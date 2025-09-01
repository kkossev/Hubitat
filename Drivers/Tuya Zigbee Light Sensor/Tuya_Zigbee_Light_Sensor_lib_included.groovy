/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, ParameterName, PublicMethodsBeforeNonPublicMethods */
/**
 *  Tuya Zigbee Light Sensor - Driver for Hubitat Elevation
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
 * ver. 2.0.6  2023-07-09 kkossev  - Tuya Zigbee Light Sensor: added min/max reporting time; illuminance threshold; added lastRx checkInTime, batteryTime, battCtr; added illuminanceCoeff; checkDriverVersion() bug fix;
 * ver. 3.0.6  2024-04-06 kkossev  - commonLib 3.06
 *
 *                                   TODO:  */

static String version() { '3.0.6' }
static String timeStamp() { '2024/04/06 2:55 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field

@Field static final String DEVICE_TYPE = 'LightSensor'
deviceType = 'LightSensor'





metadata {
    definition(
        name: 'Tuya Zigbee Light Sensor',
        importUrl: 'https://github.com/kkossev/Hubitat/blob/development/Drivers/Tuya%20Zigbee%20Light%20Sensor/Tuya_Zigbee_Light_Sensor_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
            capability 'Sensor'
            capability 'IlluminanceMeasurement'
            capability 'Battery'

            attribute 'batteryVoltage', 'number'

            fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0400,0001,0500', outClusters:'0019,000A', model:'TS0222', manufacturer:'_TYZB01_4mdqxxnn', deviceJoinName: 'Tuya Illuminance Sensor TS0222'
            fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_khx7nnka', deviceJoinName: 'Tuya Illuminance Sensor TS0601'
            fingerprint profileId:'0104', endpointId:'01', inClusters:'0004,0005,EF00,0000', outClusters:'0019,000A', model:'TS0601', manufacturer:'_TZE200_yi4jtqq1', deviceJoinName: 'Tuya Illuminance Sensor TS0601'
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        if (device) {
            if (device.hasCapability('IlluminanceMeasurement')) {
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME
                input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME
            }
            if (device.hasCapability('IlluminanceMeasurement')) {
                input name: 'illuminanceThreshold', type: 'number', title: '<b>Illuminance Reporting Threshold</b>', description: '<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00
            }
        }
    }
}

// everything is handled in the libraries ...


// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.1.1', // library marker kkossev.commonLib, line 10
    documentationLink: '' // library marker kkossev.commonLib, line 11
) // library marker kkossev.commonLib, line 12
/* // library marker kkossev.commonLib, line 13
  *  Common ZCL Library // library marker kkossev.commonLib, line 14
  * // library marker kkossev.commonLib, line 15
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 16
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 19
  * // library marker kkossev.commonLib, line 20
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 21
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 22
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 23
  * // library marker kkossev.commonLib, line 24
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 25
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 26
  * // library marker kkossev.commonLib, line 27
  * // library marker kkossev.commonLib, line 28
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 29
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 30
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 31
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 32
  * ver. 3.0.1  2023-12-06 kkossev  - nfo event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 33
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0 // library marker kkossev.commonLib, line 34
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 35
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 36
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 37
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 38
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices; // library marker kkossev.commonLib, line 39
  * ver. 3.1.0  2024-04-28 kkossev  - unnecesery unschedule() speed optimization; added syncTuyaDateTime(); tuyaBlackMagic() initialization bug fix. // library marker kkossev.commonLib, line 40
  * ver. 3.1.1  2024-05-05 kkossev  - getTuyaAttributeValue bug fix; added customCustomParseIlluminanceCluster method // library marker kkossev.commonLib, line 41
  * // library marker kkossev.commonLib, line 42
  *                                   TODO: rename all custom handlers in the libs to statdndardParseXXX !! TODO // library marker kkossev.commonLib, line 43
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 44
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 45
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 46
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 47
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 48
  * // library marker kkossev.commonLib, line 49
*/ // library marker kkossev.commonLib, line 50

String commonLibVersion() { '3.1.1' } // library marker kkossev.commonLib, line 52
String commonLibStamp() { '2024/05/05 8:24 PM' } // library marker kkossev.commonLib, line 53

import groovy.transform.Field // library marker kkossev.commonLib, line 55
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 56
import hubitat.device.Protocol // library marker kkossev.commonLib, line 57
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 58
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 59
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 60
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 61
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 62
import java.math.BigDecimal // library marker kkossev.commonLib, line 63

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 65

metadata { // library marker kkossev.commonLib, line 67
        if (_DEBUG) { // library marker kkossev.commonLib, line 68
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 69
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 70
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 71
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 72
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 73
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 74
            ] // library marker kkossev.commonLib, line 75
        } // library marker kkossev.commonLib, line 76

        // common capabilities for all device types // library marker kkossev.commonLib, line 78
        capability 'Configuration' // library marker kkossev.commonLib, line 79
        capability 'Refresh' // library marker kkossev.commonLib, line 80
        capability 'Health Check' // library marker kkossev.commonLib, line 81

        // common attributes for all device types // library marker kkossev.commonLib, line 83
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 84
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 85
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 86

        // common commands for all device types // library marker kkossev.commonLib, line 88
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 89

        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 91
            capability 'Switch' // library marker kkossev.commonLib, line 92
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 93
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 94
            } // library marker kkossev.commonLib, line 95
        } // library marker kkossev.commonLib, line 96

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 98
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 99

    preferences { // library marker kkossev.commonLib, line 101
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 102
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 103
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 104

        if (device) { // library marker kkossev.commonLib, line 106
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 107
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 108
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 109
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 110
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 111
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 112
                } // library marker kkossev.commonLib, line 113
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 114
            } // library marker kkossev.commonLib, line 115
        } // library marker kkossev.commonLib, line 116
    } // library marker kkossev.commonLib, line 117
} // library marker kkossev.commonLib, line 118

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 120
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 121
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 122
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 123
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 124
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 125
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 126
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 127
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 128
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 129
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 130

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 132
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 133
] // library marker kkossev.commonLib, line 134
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 135
    defaultValue: 240, options: [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 136
] // library marker kkossev.commonLib, line 137
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 138
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 139
] // library marker kkossev.commonLib, line 140

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 142
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 143
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 144
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 145
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 146
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 147
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 148
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 149
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 150
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 151
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 152
] // library marker kkossev.commonLib, line 153

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 155
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 156
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 157
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 158
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 159
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 160

/** // library marker kkossev.commonLib, line 162
 * Parse Zigbee message // library marker kkossev.commonLib, line 163
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 164
 */ // library marker kkossev.commonLib, line 165
void parse(final String description) { // library marker kkossev.commonLib, line 166
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 167
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 168
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 169
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 170

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 172
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 173
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 174
            parseIasMessage(description) // library marker kkossev.commonLib, line 175
        } // library marker kkossev.commonLib, line 176
        else { // library marker kkossev.commonLib, line 177
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 178
        } // library marker kkossev.commonLib, line 179
        return // library marker kkossev.commonLib, line 180
    } // library marker kkossev.commonLib, line 181
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 182
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 183
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 184
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 185
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 186
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 187
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 188
        return // library marker kkossev.commonLib, line 189
    } // library marker kkossev.commonLib, line 190

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 195

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" }   // library marker kkossev.commonLib, line 197
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 198

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 200
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 201
        return // library marker kkossev.commonLib, line 202
    } // library marker kkossev.commonLib, line 203
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 204
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 205
        return // library marker kkossev.commonLib, line 206
    } // library marker kkossev.commonLib, line 207
    // // library marker kkossev.commonLib, line 208
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 209
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 210
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 211

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 213
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 214
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 215
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 216
            break // library marker kkossev.commonLib, line 217
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 218
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 219
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 220
            break // library marker kkossev.commonLib, line 221
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 222
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 223
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 224
            break // library marker kkossev.commonLib, line 225
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 226
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 227
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 228
            break // library marker kkossev.commonLib, line 229
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 230
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 231
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 232
            break // library marker kkossev.commonLib, line 233
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 234
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 235
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 236
            break // library marker kkossev.commonLib, line 237
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 238
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 239
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 240
            break // library marker kkossev.commonLib, line 241
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 242
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 243
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 244
            break // library marker kkossev.commonLib, line 245
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 246
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 247
            break // library marker kkossev.commonLib, line 248
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 249
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 250
            break // library marker kkossev.commonLib, line 251
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 252
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 253
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 254
            break // library marker kkossev.commonLib, line 255
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 256
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 257
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 258
            break // library marker kkossev.commonLib, line 259
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 260
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 261
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 262
            break // library marker kkossev.commonLib, line 263
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 264
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 265
            break // library marker kkossev.commonLib, line 266
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 267
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 268
            break // library marker kkossev.commonLib, line 269
        case 0x0406 : //OCCUPANCY_CLUSTER                   // Sonoff SNZB-06 // library marker kkossev.commonLib, line 270
            parseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 271
            break // library marker kkossev.commonLib, line 272
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 273
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 274
            break // library marker kkossev.commonLib, line 275
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 276
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 277
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 278
            break // library marker kkossev.commonLib, line 279
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 280
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 281
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 282
            break // library marker kkossev.commonLib, line 283
        case 0xE002 : // library marker kkossev.commonLib, line 284
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 285
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 286
            break // library marker kkossev.commonLib, line 287
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 288
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 289
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 290
            break // library marker kkossev.commonLib, line 291
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 292
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 293
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 294
            break // library marker kkossev.commonLib, line 295
        case 0xFC11 :                                       // Sonoff // library marker kkossev.commonLib, line 296
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 297
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 300
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 301
            break // library marker kkossev.commonLib, line 302
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 303
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 304
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        default: // library marker kkossev.commonLib, line 307
            if (settings.logEnable) { // library marker kkossev.commonLib, line 308
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 309
            } // library marker kkossev.commonLib, line 310
            break // library marker kkossev.commonLib, line 311
    } // library marker kkossev.commonLib, line 312
} // library marker kkossev.commonLib, line 313

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 315
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 316
} // library marker kkossev.commonLib, line 317

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 319
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 320
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 321
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 322
    } // library marker kkossev.commonLib, line 323
    return false // library marker kkossev.commonLib, line 324
} // library marker kkossev.commonLib, line 325

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 327
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 328
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 329
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 330
    } // library marker kkossev.commonLib, line 331
    return false // library marker kkossev.commonLib, line 332
} // library marker kkossev.commonLib, line 333

boolean isSpammyTuyaRadar() { // library marker kkossev.commonLib, line 335
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 336
    if (this.respondsTo('isSpammyDeviceProfile'())) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 337
        return isSpammyDeviceProfile() // library marker kkossev.commonLib, line 338
    } // library marker kkossev.commonLib, line 339
    return false // library marker kkossev.commonLib, line 340
} // library marker kkossev.commonLib, line 341

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 343
    0x0002: 'Node Descriptor Request', 0x0005: 'Active Endpoints Request', 0x0006: 'Match Descriptor Request', 0x0022: 'Unbind Request', 0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 344
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 345
    0x8021: 'Bind Response', 0x8022: 'Unbind Response', 0x8023: 'Bind Register Response', 0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 346
] // library marker kkossev.commonLib, line 347

/** // library marker kkossev.commonLib, line 349
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 350
 */ // library marker kkossev.commonLib, line 351
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 352
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 353
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 354
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 355
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 356
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 357
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 358
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 359
    switch (clusterId) { // library marker kkossev.commonLib, line 360
        case 0x0005 : // library marker kkossev.commonLib, line 361
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 362
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 363
            break // library marker kkossev.commonLib, line 364
        case 0x0006 : // library marker kkossev.commonLib, line 365
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 366
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 367
            break // library marker kkossev.commonLib, line 368
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 369
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 370
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 371
            break // library marker kkossev.commonLib, line 372
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 373
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 374
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 375
            break // library marker kkossev.commonLib, line 376
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 377
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 378
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 379
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 380
            break // library marker kkossev.commonLib, line 381
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 382
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 383
            break // library marker kkossev.commonLib, line 384
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 385
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 386
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 387
            break // library marker kkossev.commonLib, line 388
        default : // library marker kkossev.commonLib, line 389
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 390
            break // library marker kkossev.commonLib, line 391
    } // library marker kkossev.commonLib, line 392
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 393
} // library marker kkossev.commonLib, line 394

/** // library marker kkossev.commonLib, line 396
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 397
 */ // library marker kkossev.commonLib, line 398
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 399
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 400
    switch (commandId) { // library marker kkossev.commonLib, line 401
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 402
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 403
            break // library marker kkossev.commonLib, line 404
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 405
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 406
            break // library marker kkossev.commonLib, line 407
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 408
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 409
            break // library marker kkossev.commonLib, line 410
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 411
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 412
            break // library marker kkossev.commonLib, line 413
        case 0x0B: // default command response // library marker kkossev.commonLib, line 414
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 415
            break // library marker kkossev.commonLib, line 416
        default: // library marker kkossev.commonLib, line 417
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 418
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 419
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 420
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 421
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 422
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 423
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 424
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 425
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 426
            } // library marker kkossev.commonLib, line 427
            break // library marker kkossev.commonLib, line 428
    } // library marker kkossev.commonLib, line 429
} // library marker kkossev.commonLib, line 430

/** // library marker kkossev.commonLib, line 432
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 433
 */ // library marker kkossev.commonLib, line 434
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 435
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 436
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 437
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 438
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 439
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 440
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 441
    } // library marker kkossev.commonLib, line 442
    else { // library marker kkossev.commonLib, line 443
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 444
    } // library marker kkossev.commonLib, line 445
} // library marker kkossev.commonLib, line 446

/** // library marker kkossev.commonLib, line 448
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 449
 */ // library marker kkossev.commonLib, line 450
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 451
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 452
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 453
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 454
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 455
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 456
    } // library marker kkossev.commonLib, line 457
    else { // library marker kkossev.commonLib, line 458
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 459
    } // library marker kkossev.commonLib, line 460
} // library marker kkossev.commonLib, line 461

/** // library marker kkossev.commonLib, line 463
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 464
 */ // library marker kkossev.commonLib, line 465
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 466
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 467
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 468
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 469
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 470
        state.reportingEnabled = true // library marker kkossev.commonLib, line 471
    } // library marker kkossev.commonLib, line 472
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 473
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 474
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 475
    } else { // library marker kkossev.commonLib, line 476
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 477
    } // library marker kkossev.commonLib, line 478
} // library marker kkossev.commonLib, line 479

/** // library marker kkossev.commonLib, line 481
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 482
 */ // library marker kkossev.commonLib, line 483
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 484
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 485
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 486
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 487
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 488
    if (status == 0) { // library marker kkossev.commonLib, line 489
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 490
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 491
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 492
        int delta = 0 // library marker kkossev.commonLib, line 493
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 494
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 495
        } // library marker kkossev.commonLib, line 496
        else { // library marker kkossev.commonLib, line 497
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 498
        } // library marker kkossev.commonLib, line 499
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 500
    } // library marker kkossev.commonLib, line 501
    else { // library marker kkossev.commonLib, line 502
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 503
    } // library marker kkossev.commonLib, line 504
} // library marker kkossev.commonLib, line 505

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 507
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 508
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 509
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 510
        return false // library marker kkossev.commonLib, line 511
    } // library marker kkossev.commonLib, line 512
    // execute the customHandler function // library marker kkossev.commonLib, line 513
    boolean result = false // library marker kkossev.commonLib, line 514
    try { // library marker kkossev.commonLib, line 515
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 516
    } // library marker kkossev.commonLib, line 517
    catch (e) { // library marker kkossev.commonLib, line 518
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 519
        return false // library marker kkossev.commonLib, line 520
    } // library marker kkossev.commonLib, line 521
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 522
    return result // library marker kkossev.commonLib, line 523
} // library marker kkossev.commonLib, line 524

/** // library marker kkossev.commonLib, line 526
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 527
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 528
 */ // library marker kkossev.commonLib, line 529
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 530
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 531
    final String commandId = data[0] // library marker kkossev.commonLib, line 532
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 533
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 534
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 535
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 536
    } else { // library marker kkossev.commonLib, line 537
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 538
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 539
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 540
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 541
        } // library marker kkossev.commonLib, line 542
    } // library marker kkossev.commonLib, line 543
} // library marker kkossev.commonLib, line 544

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 546
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 547
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 548
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 549

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 551
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 552
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 553
] // library marker kkossev.commonLib, line 554

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 556
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 557
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 558
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 559
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 560
] // library marker kkossev.commonLib, line 561

void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 563
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' } // library marker kkossev.commonLib, line 564
} // library marker kkossev.commonLib, line 565

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 567
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 568
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 569
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 570
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 571
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 572
    return avg // library marker kkossev.commonLib, line 573
} // library marker kkossev.commonLib, line 574

/* // library marker kkossev.commonLib, line 576
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 577
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 578
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 579
*/ // library marker kkossev.commonLib, line 580
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 581

/** // library marker kkossev.commonLib, line 583
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 584
 */ // library marker kkossev.commonLib, line 585
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 586
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 587
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 588
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 589
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 590
        case 0x0000: // library marker kkossev.commonLib, line 591
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 592
            break // library marker kkossev.commonLib, line 593
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 594
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 595
            if (isPing) { // library marker kkossev.commonLib, line 596
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 597
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 598
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 599
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 600
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 601
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 602
                    sendRttEvent() // library marker kkossev.commonLib, line 603
                } // library marker kkossev.commonLib, line 604
                else { // library marker kkossev.commonLib, line 605
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 606
                } // library marker kkossev.commonLib, line 607
                state.states['isPing'] = false // library marker kkossev.commonLib, line 608
            } // library marker kkossev.commonLib, line 609
            else { // library marker kkossev.commonLib, line 610
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 611
            } // library marker kkossev.commonLib, line 612
            break // library marker kkossev.commonLib, line 613
        case 0x0004: // library marker kkossev.commonLib, line 614
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 615
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 616
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 617
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 618
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 619
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 620
            } // library marker kkossev.commonLib, line 621
            break // library marker kkossev.commonLib, line 622
        case 0x0005: // library marker kkossev.commonLib, line 623
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 624
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 625
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 626
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 627
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 628
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 629
            } // library marker kkossev.commonLib, line 630
            break // library marker kkossev.commonLib, line 631
        case 0x0007: // library marker kkossev.commonLib, line 632
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 633
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 634
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 635
            break // library marker kkossev.commonLib, line 636
        case 0xFFDF: // library marker kkossev.commonLib, line 637
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 638
            break // library marker kkossev.commonLib, line 639
        case 0xFFE2: // library marker kkossev.commonLib, line 640
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 641
            break // library marker kkossev.commonLib, line 642
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 643
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 644
            break // library marker kkossev.commonLib, line 645
        case 0xFFFE: // library marker kkossev.commonLib, line 646
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 647
            break // library marker kkossev.commonLib, line 648
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 649
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 650
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 651
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 652
            break // library marker kkossev.commonLib, line 653
        default: // library marker kkossev.commonLib, line 654
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 655
            break // library marker kkossev.commonLib, line 656
    } // library marker kkossev.commonLib, line 657
} // library marker kkossev.commonLib, line 658

// power cluster            0x0001 // library marker kkossev.commonLib, line 660
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 661
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 662
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 663
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 664
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 665
    } // library marker kkossev.commonLib, line 666
    if (this.respondsTo('customParsePowerCluster')) { // library marker kkossev.commonLib, line 667
        customParsePowerCluster(descMap) // library marker kkossev.commonLib, line 668
    } // library marker kkossev.commonLib, line 669
    else { // library marker kkossev.commonLib, line 670
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 671
    } // library marker kkossev.commonLib, line 672
} // library marker kkossev.commonLib, line 673

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 675
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' } // library marker kkossev.commonLib, line 676

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 678
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 679
} // library marker kkossev.commonLib, line 680

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 682
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 683
} // library marker kkossev.commonLib, line 684

/* // library marker kkossev.commonLib, line 686
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 687
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 688
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 689
*/ // library marker kkossev.commonLib, line 690

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 692
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 693
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 694
    } // library marker kkossev.commonLib, line 695
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 696
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 697
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 698
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 699
    } // library marker kkossev.commonLib, line 700
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 701
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 702
    } // library marker kkossev.commonLib, line 703
    else { // library marker kkossev.commonLib, line 704
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 705
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 706
    } // library marker kkossev.commonLib, line 707
} // library marker kkossev.commonLib, line 708

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 710
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 711
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 712

void toggle() { // library marker kkossev.commonLib, line 714
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 715
    String state = '' // library marker kkossev.commonLib, line 716
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 717
        state = 'on' // library marker kkossev.commonLib, line 718
    } // library marker kkossev.commonLib, line 719
    else { // library marker kkossev.commonLib, line 720
        state = 'off' // library marker kkossev.commonLib, line 721
    } // library marker kkossev.commonLib, line 722
    descriptionText += state // library marker kkossev.commonLib, line 723
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 724
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 725
} // library marker kkossev.commonLib, line 726

void off() { // library marker kkossev.commonLib, line 728
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 729
        customOff() // library marker kkossev.commonLib, line 730
        return // library marker kkossev.commonLib, line 731
    } // library marker kkossev.commonLib, line 732
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 733
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 734
        return // library marker kkossev.commonLib, line 735
    } // library marker kkossev.commonLib, line 736
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 737
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 738
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 739
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 740
        if (currentState == 'off') { // library marker kkossev.commonLib, line 741
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 742
        } // library marker kkossev.commonLib, line 743
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 744
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 745
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 746
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 747
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 748
    } // library marker kkossev.commonLib, line 749
    /* // library marker kkossev.commonLib, line 750
    else { // library marker kkossev.commonLib, line 751
        if (currentState != 'off') { // library marker kkossev.commonLib, line 752
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 753
        } // library marker kkossev.commonLib, line 754
        else { // library marker kkossev.commonLib, line 755
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 756
            return // library marker kkossev.commonLib, line 757
        } // library marker kkossev.commonLib, line 758
    } // library marker kkossev.commonLib, line 759
    */ // library marker kkossev.commonLib, line 760

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 762
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 763
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 764
} // library marker kkossev.commonLib, line 765

void on() { // library marker kkossev.commonLib, line 767
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 768
        customOn() // library marker kkossev.commonLib, line 769
        return // library marker kkossev.commonLib, line 770
    } // library marker kkossev.commonLib, line 771
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 772
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 773
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 774
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 775
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 776
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 777
        } // library marker kkossev.commonLib, line 778
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 779
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 780
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 781
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 782
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 783
    } // library marker kkossev.commonLib, line 784
    /* // library marker kkossev.commonLib, line 785
    else { // library marker kkossev.commonLib, line 786
        if (currentState != 'on') { // library marker kkossev.commonLib, line 787
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 788
        } // library marker kkossev.commonLib, line 789
        else { // library marker kkossev.commonLib, line 790
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 791
            return // library marker kkossev.commonLib, line 792
        } // library marker kkossev.commonLib, line 793
    } // library marker kkossev.commonLib, line 794
    */ // library marker kkossev.commonLib, line 795
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 796
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 797
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 798
} // library marker kkossev.commonLib, line 799

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 801
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 802
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 803
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 804
    } // library marker kkossev.commonLib, line 805
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 806
    Map map = [:] // library marker kkossev.commonLib, line 807
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 808
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 809
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 810
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 811
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 812
        return // library marker kkossev.commonLib, line 813
    } // library marker kkossev.commonLib, line 814
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 815
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 816
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 817
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 818
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 819
        state.states['debounce'] = true // library marker kkossev.commonLib, line 820
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 821
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 822
    } else { // library marker kkossev.commonLib, line 823
        state.states['debounce'] = true // library marker kkossev.commonLib, line 824
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 825
    } // library marker kkossev.commonLib, line 826
    map.name = 'switch' // library marker kkossev.commonLib, line 827
    map.value = value // library marker kkossev.commonLib, line 828
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 829
    if (isRefresh) { // library marker kkossev.commonLib, line 830
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 831
        map.isStateChange = true // library marker kkossev.commonLib, line 832
    } else { // library marker kkossev.commonLib, line 833
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 834
    } // library marker kkossev.commonLib, line 835
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 836
    sendEvent(map) // library marker kkossev.commonLib, line 837
    clearIsDigital() // library marker kkossev.commonLib, line 838
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 839
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 840
    } // library marker kkossev.commonLib, line 841
} // library marker kkossev.commonLib, line 842

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 844
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.commonLib, line 845
] // library marker kkossev.commonLib, line 846

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 848
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.commonLib, line 849
] // library marker kkossev.commonLib, line 850

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 852
    Map descMap = [:] // library marker kkossev.commonLib, line 853
    try { // library marker kkossev.commonLib, line 854
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 855
    } // library marker kkossev.commonLib, line 856
    catch (e1) { // library marker kkossev.commonLib, line 857
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 858
        // try alternative custom parsing // library marker kkossev.commonLib, line 859
        descMap = [:] // library marker kkossev.commonLib, line 860
        try { // library marker kkossev.commonLib, line 861
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 862
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 863
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 864
            } // library marker kkossev.commonLib, line 865
        } // library marker kkossev.commonLib, line 866
        catch (e2) { // library marker kkossev.commonLib, line 867
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 868
            return [:] // library marker kkossev.commonLib, line 869
        } // library marker kkossev.commonLib, line 870
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 871
    } // library marker kkossev.commonLib, line 872
    return descMap // library marker kkossev.commonLib, line 873
} // library marker kkossev.commonLib, line 874

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 876
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 877
        return false // library marker kkossev.commonLib, line 878
    } // library marker kkossev.commonLib, line 879
    // try to parse ... // library marker kkossev.commonLib, line 880
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 881
    Map descMap = [:] // library marker kkossev.commonLib, line 882
    try { // library marker kkossev.commonLib, line 883
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 884
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 885
    } // library marker kkossev.commonLib, line 886
    catch (e) { // library marker kkossev.commonLib, line 887
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 888
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 889
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 890
        return true // library marker kkossev.commonLib, line 891
    } // library marker kkossev.commonLib, line 892

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 894
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 895
    } // library marker kkossev.commonLib, line 896
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 897
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 898
    } // library marker kkossev.commonLib, line 899
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 900
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 901
    } // library marker kkossev.commonLib, line 902
    else { // library marker kkossev.commonLib, line 903
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 904
        return false // library marker kkossev.commonLib, line 905
    } // library marker kkossev.commonLib, line 906
    return true    // processed // library marker kkossev.commonLib, line 907
} // library marker kkossev.commonLib, line 908

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 910
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 911
  /* // library marker kkossev.commonLib, line 912
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 913
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 914
        return true // library marker kkossev.commonLib, line 915
    } // library marker kkossev.commonLib, line 916
*/ // library marker kkossev.commonLib, line 917
    Map descMap = [:] // library marker kkossev.commonLib, line 918
    try { // library marker kkossev.commonLib, line 919
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 920
    } // library marker kkossev.commonLib, line 921
    catch (e1) { // library marker kkossev.commonLib, line 922
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 923
        // try alternative custom parsing // library marker kkossev.commonLib, line 924
        descMap = [:] // library marker kkossev.commonLib, line 925
        try { // library marker kkossev.commonLib, line 926
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 927
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 928
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 929
            } // library marker kkossev.commonLib, line 930
        } // library marker kkossev.commonLib, line 931
        catch (e2) { // library marker kkossev.commonLib, line 932
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 933
            return true // library marker kkossev.commonLib, line 934
        } // library marker kkossev.commonLib, line 935
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 936
    } // library marker kkossev.commonLib, line 937
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 938
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 939
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 940
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 941
        return false // library marker kkossev.commonLib, line 942
    } // library marker kkossev.commonLib, line 943
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 944
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 945
    // attribute report received // library marker kkossev.commonLib, line 946
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 947
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 948
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 949
    } // library marker kkossev.commonLib, line 950
    attrData.each { // library marker kkossev.commonLib, line 951
        if (it.status == '86') { // library marker kkossev.commonLib, line 952
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 953
        // TODO - skip parsing? // library marker kkossev.commonLib, line 954
        } // library marker kkossev.commonLib, line 955
        switch (it.cluster) { // library marker kkossev.commonLib, line 956
            case '0000' : // library marker kkossev.commonLib, line 957
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 958
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 959
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 960
                } // library marker kkossev.commonLib, line 961
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 962
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 963
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 964
                } // library marker kkossev.commonLib, line 965
                else { // library marker kkossev.commonLib, line 966
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 967
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 968
                } // library marker kkossev.commonLib, line 969
                break // library marker kkossev.commonLib, line 970
            default : // library marker kkossev.commonLib, line 971
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 972
                break // library marker kkossev.commonLib, line 973
        } // switch // library marker kkossev.commonLib, line 974
    } // for each attribute // library marker kkossev.commonLib, line 975
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 976
} // library marker kkossev.commonLib, line 977

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 979

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 981
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 982
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 983
    def mode // library marker kkossev.commonLib, line 984
    String attrName // library marker kkossev.commonLib, line 985
    if (it.value == null) { // library marker kkossev.commonLib, line 986
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 987
        return // library marker kkossev.commonLib, line 988
    } // library marker kkossev.commonLib, line 989
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 990
    switch (it.attrId) { // library marker kkossev.commonLib, line 991
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 992
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 993
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 994
            break // library marker kkossev.commonLib, line 995
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 996
            attrName = 'On Time' // library marker kkossev.commonLib, line 997
            mode = value // library marker kkossev.commonLib, line 998
            break // library marker kkossev.commonLib, line 999
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1000
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1001
            mode = value // library marker kkossev.commonLib, line 1002
            break // library marker kkossev.commonLib, line 1003
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1004
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1005
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1006
            break // library marker kkossev.commonLib, line 1007
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1008
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1009
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1010
            break // library marker kkossev.commonLib, line 1011
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1012
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1013
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1014
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1015
            } // library marker kkossev.commonLib, line 1016
            else { // library marker kkossev.commonLib, line 1017
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1018
            } // library marker kkossev.commonLib, line 1019
            break // library marker kkossev.commonLib, line 1020
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1021
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1022
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1023
            break // library marker kkossev.commonLib, line 1024
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1025
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1026
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1027
            break // library marker kkossev.commonLib, line 1028
        default : // library marker kkossev.commonLib, line 1029
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1030
            return // library marker kkossev.commonLib, line 1031
    } // library marker kkossev.commonLib, line 1032
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1033
} // library marker kkossev.commonLib, line 1034

void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1036
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1037
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1038
    } // library marker kkossev.commonLib, line 1039
    else if (this.respondsTo('levelLibParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1040
        levelLibParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1041
    } // library marker kkossev.commonLib, line 1042
    else { // library marker kkossev.commonLib, line 1043
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1044
    } // library marker kkossev.commonLib, line 1045
} // library marker kkossev.commonLib, line 1046

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1048
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1049
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1050
} // library marker kkossev.commonLib, line 1051

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1053
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1054
} // library marker kkossev.commonLib, line 1055

void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1057
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1058
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1059
    } // library marker kkossev.commonLib, line 1060
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1061
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1062
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1063
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1064
    } // library marker kkossev.commonLib, line 1065
    else { // library marker kkossev.commonLib, line 1066
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1067
    } // library marker kkossev.commonLib, line 1068
} // library marker kkossev.commonLib, line 1069

void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1071
    if (this.respondsTo('customCustomParseIlluminanceCluster')) { customCustomParseIlluminanceCluster(descMap) } // library marker kkossev.commonLib, line 1072
    else if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } // library marker kkossev.commonLib, line 1073
    else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 1074
} // library marker kkossev.commonLib, line 1075

// Temperature Measurement Cluster 0x0402 // library marker kkossev.commonLib, line 1077
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1078
    if (this.respondsTo('customParseTemperatureCluster')) { // library marker kkossev.commonLib, line 1079
        customParseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 1080
    } // library marker kkossev.commonLib, line 1081
    else { // library marker kkossev.commonLib, line 1082
        logWarn "unprocessed Temperature attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1083
    } // library marker kkossev.commonLib, line 1084
} // library marker kkossev.commonLib, line 1085

// Humidity Measurement Cluster 0x0405 // library marker kkossev.commonLib, line 1087
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1088
    if (this.respondsTo('customParseHumidityCluster')) { // library marker kkossev.commonLib, line 1089
        customParseHumidityCluster(descMap) // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091
    else { // library marker kkossev.commonLib, line 1092
        logWarn "unprocessed Humidity attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1093
    } // library marker kkossev.commonLib, line 1094
} // library marker kkossev.commonLib, line 1095

// Occupancy Sensing Cluster 0x0406 // library marker kkossev.commonLib, line 1097
void parseOccupancyCluster(final Map descMap) { // library marker kkossev.commonLib, line 1098
    if (this.respondsTo('customParseOccupancyCluster')) { // library marker kkossev.commonLib, line 1099
        customParseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 1100
    } // library marker kkossev.commonLib, line 1101
    else { // library marker kkossev.commonLib, line 1102
        logWarn "unprocessed Occupancy attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1103
    } // library marker kkossev.commonLib, line 1104
} // library marker kkossev.commonLib, line 1105

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1107
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1108
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1109
} // library marker kkossev.commonLib, line 1110

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1112
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1113
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1114
} // library marker kkossev.commonLib, line 1115

// pm2.5 // library marker kkossev.commonLib, line 1117
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1118
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1119
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1120
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1121
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1122
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1123
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1124
    } // library marker kkossev.commonLib, line 1125
    else { // library marker kkossev.commonLib, line 1126
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1127
    } // library marker kkossev.commonLib, line 1128
} // library marker kkossev.commonLib, line 1129

// Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1131
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1132
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1133
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1136
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1137
    } // library marker kkossev.commonLib, line 1138
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1139
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1140
    } // library marker kkossev.commonLib, line 1141
    else { // library marker kkossev.commonLib, line 1142
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1143
    } // library marker kkossev.commonLib, line 1144
} // library marker kkossev.commonLib, line 1145

// Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1147
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1148
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1149
} // library marker kkossev.commonLib, line 1150

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1152
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1153
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1154
} // library marker kkossev.commonLib, line 1155

// thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1157
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1158
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1159
} // library marker kkossev.commonLib, line 1160

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1162
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1163
} // library marker kkossev.commonLib, line 1164

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1166
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars // library marker kkossev.commonLib, line 1167
} // library marker kkossev.commonLib, line 1168

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1170
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars // library marker kkossev.commonLib, line 1171
} // library marker kkossev.commonLib, line 1172

/* // library marker kkossev.commonLib, line 1174
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1175
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1176
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1177
*/ // library marker kkossev.commonLib, line 1178
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1179
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1180
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1181

// Tuya Commands // library marker kkossev.commonLib, line 1183
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1184
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1185
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1186
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1187
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1188

// tuya DP type // library marker kkossev.commonLib, line 1190
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1191
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1192
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1193
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1194
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1195
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1196

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 1198
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 1199
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 1200
    long offset = 0 // library marker kkossev.commonLib, line 1201
    int offsetHours = 0 // library marker kkossev.commonLib, line 1202
    Calendar cal = Calendar.getInstance();    //it return same time as new Date() // library marker kkossev.commonLib, line 1203
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 1204
    try { // library marker kkossev.commonLib, line 1205
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1206
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 1207
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 1208
    } catch (e) { // library marker kkossev.commonLib, line 1209
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 1210
    } // library marker kkossev.commonLib, line 1211
    // // library marker kkossev.commonLib, line 1212
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000),8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1213
    String dateTimeNow = unix2formattedDate(now()) // library marker kkossev.commonLib, line 1214
    logDebug "sending time data : ${dateTimeNow} (${cmds})" // library marker kkossev.commonLib, line 1215
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1216
    logInfo "Tuya device time synchronized to ${dateTimeNow}" // library marker kkossev.commonLib, line 1217
} // library marker kkossev.commonLib, line 1218


void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1221
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1222
        syncTuyaDateTime() // library marker kkossev.commonLib, line 1223
    } // library marker kkossev.commonLib, line 1224
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1225
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1226
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1227
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1228
        if (status != '00') { // library marker kkossev.commonLib, line 1229
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1230
        } // library marker kkossev.commonLib, line 1231
    } // library marker kkossev.commonLib, line 1232
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1233
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1234
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1235
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1236
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1237
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1238
            return // library marker kkossev.commonLib, line 1239
        } // library marker kkossev.commonLib, line 1240
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1241
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1242
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1243
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1244
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1245
            if (!isChattyDeviceReport(descMap) && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 1246
                logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1247
            } // library marker kkossev.commonLib, line 1248
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1249
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1250
        } // library marker kkossev.commonLib, line 1251
    } // library marker kkossev.commonLib, line 1252
    else { // library marker kkossev.commonLib, line 1253
        if (this.respondsTo('customParseTuyaCluster')) { // library marker kkossev.commonLib, line 1254
            customParseTuyaCluster(descMap) // library marker kkossev.commonLib, line 1255
        } // library marker kkossev.commonLib, line 1256
        else { // library marker kkossev.commonLib, line 1257
            logWarn "unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 1258
        } // library marker kkossev.commonLib, line 1259
    } // library marker kkossev.commonLib, line 1260
} // library marker kkossev.commonLib, line 1261

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1263
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1264
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1265
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1266
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1267
            return // library marker kkossev.commonLib, line 1268
        } // library marker kkossev.commonLib, line 1269
    } // library marker kkossev.commonLib, line 1270
    // check if the method  method exists // library marker kkossev.commonLib, line 1271
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1272
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1273
            return // library marker kkossev.commonLib, line 1274
        } // library marker kkossev.commonLib, line 1275
    } // library marker kkossev.commonLib, line 1276
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1277
} // library marker kkossev.commonLib, line 1278

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1280
    int retValue = 0 // library marker kkossev.commonLib, line 1281
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1282
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1283
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 1284
        int power = 1 // library marker kkossev.commonLib, line 1285
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 1286
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 1287
            power = power * 256 // library marker kkossev.commonLib, line 1288
        } // library marker kkossev.commonLib, line 1289
    } // library marker kkossev.commonLib, line 1290
    return retValue // library marker kkossev.commonLib, line 1291
} // library marker kkossev.commonLib, line 1292

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 1294

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 1296
    List<String> cmds = [] // library marker kkossev.commonLib, line 1297
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 1298
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1299
    int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 1300
    //tuyaCmd = 0x04  // !!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.commonLib, line 1301
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 1302
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 1303
    return cmds // library marker kkossev.commonLib, line 1304
} // library marker kkossev.commonLib, line 1305

private getPACKET_ID() { // library marker kkossev.commonLib, line 1307
    /* // library marker kkossev.commonLib, line 1308
    int packetId = state.packetId ?: 0 // library marker kkossev.commonLib, line 1309
    state.packetId = packetId + 1 // library marker kkossev.commonLib, line 1310
    return zigbee.convertToHexString(packetId, 4) // library marker kkossev.commonLib, line 1311
    */ // library marker kkossev.commonLib, line 1312
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 1313
} // library marker kkossev.commonLib, line 1314

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1316
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 1317
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 1318
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 1319
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 1320
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 1321
} // library marker kkossev.commonLib, line 1322

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 1324
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 1325

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 1327
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 1328
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1329
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 1330
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 1331
} // library marker kkossev.commonLib, line 1332

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 1334
    List<String> cmds = [] // library marker kkossev.commonLib, line 1335
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1336
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 1337
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1338
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1339
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 1340
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 1341
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 1342
        } // library marker kkossev.commonLib, line 1343
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1344
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 1345
    } // library marker kkossev.commonLib, line 1346
    else { // library marker kkossev.commonLib, line 1347
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 1348
    } // library marker kkossev.commonLib, line 1349
} // library marker kkossev.commonLib, line 1350

/** // library marker kkossev.commonLib, line 1352
 * initializes the device // library marker kkossev.commonLib, line 1353
 * Invoked from configure() // library marker kkossev.commonLib, line 1354
 * @return zigbee commands // library marker kkossev.commonLib, line 1355
 */ // library marker kkossev.commonLib, line 1356
List<String> initializeDevice() { // library marker kkossev.commonLib, line 1357
    List<String> cmds = [] // library marker kkossev.commonLib, line 1358
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 1359
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 1360
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 1361
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1362
    } // library marker kkossev.commonLib, line 1363
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 1364
    return cmds // library marker kkossev.commonLib, line 1365
} // library marker kkossev.commonLib, line 1366

/** // library marker kkossev.commonLib, line 1368
 * configures the device // library marker kkossev.commonLib, line 1369
 * Invoked from configure() // library marker kkossev.commonLib, line 1370
 * @return zigbee commands // library marker kkossev.commonLib, line 1371
 */ // library marker kkossev.commonLib, line 1372
List<String> configureDevice() { // library marker kkossev.commonLib, line 1373
    List<String> cmds = [] // library marker kkossev.commonLib, line 1374
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 1375
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 1376
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 1377
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1378
    } // library marker kkossev.commonLib, line 1379
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 1380
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 1381
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 1382
    return cmds // library marker kkossev.commonLib, line 1383
} // library marker kkossev.commonLib, line 1384

/* // library marker kkossev.commonLib, line 1386
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1387
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 1388
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1389
*/ // library marker kkossev.commonLib, line 1390

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 1392
    List<String> cmds = [] // library marker kkossev.commonLib, line 1393
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 1394
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 1395
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 1396
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 1397
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1398
            } // library marker kkossev.commonLib, line 1399
        } // library marker kkossev.commonLib, line 1400
    } // library marker kkossev.commonLib, line 1401
    return cmds // library marker kkossev.commonLib, line 1402
} // library marker kkossev.commonLib, line 1403

void refresh() { // library marker kkossev.commonLib, line 1405
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 1406
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1407
    List<String> cmds = [] // library marker kkossev.commonLib, line 1408
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1409

    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 1411
    if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1412

    if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 1414
    else { // library marker kkossev.commonLib, line 1415
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 1416
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1417
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1418
        } // library marker kkossev.commonLib, line 1419
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1420
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1421
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1422
        } // library marker kkossev.commonLib, line 1423
    } // library marker kkossev.commonLib, line 1424

    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1426
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 1427
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1428
    } // library marker kkossev.commonLib, line 1429
    else { // library marker kkossev.commonLib, line 1430
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1431
    } // library marker kkossev.commonLib, line 1432
} // library marker kkossev.commonLib, line 1433

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 1435
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1436

public void clearInfoEvent() { // library marker kkossev.commonLib, line 1438
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 1439
} // library marker kkossev.commonLib, line 1440

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1442
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1443
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1444
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 1445
    } // library marker kkossev.commonLib, line 1446
    else { // library marker kkossev.commonLib, line 1447
        logInfo "${info}" // library marker kkossev.commonLib, line 1448
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 1449
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1450
    } // library marker kkossev.commonLib, line 1451
} // library marker kkossev.commonLib, line 1452

public void ping() { // library marker kkossev.commonLib, line 1454
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1455
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 1456
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1457
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 1458
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 1459
    logDebug 'ping...' // library marker kkossev.commonLib, line 1460
} // library marker kkossev.commonLib, line 1461

def virtualPong() { // library marker kkossev.commonLib, line 1463
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1464
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1465
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1466
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1467
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1468
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1469
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1470
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1471
        sendRttEvent() // library marker kkossev.commonLib, line 1472
    } // library marker kkossev.commonLib, line 1473
    else { // library marker kkossev.commonLib, line 1474
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1475
    } // library marker kkossev.commonLib, line 1476
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1477
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1478
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1479
} // library marker kkossev.commonLib, line 1480

/** // library marker kkossev.commonLib, line 1482
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 1483
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 1484
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 1485
 * @return none // library marker kkossev.commonLib, line 1486
 */ // library marker kkossev.commonLib, line 1487
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1488
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1489
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1490
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1491
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1492
    if (value == null) { // library marker kkossev.commonLib, line 1493
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1494
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1495
    } // library marker kkossev.commonLib, line 1496
    else { // library marker kkossev.commonLib, line 1497
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1498
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1499
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1500
    } // library marker kkossev.commonLib, line 1501
} // library marker kkossev.commonLib, line 1502

/** // library marker kkossev.commonLib, line 1504
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 1505
 * @param cluster cluster ID // library marker kkossev.commonLib, line 1506
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 1507
 */ // library marker kkossev.commonLib, line 1508
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1509
    if (cluster != null) { // library marker kkossev.commonLib, line 1510
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1511
    } // library marker kkossev.commonLib, line 1512
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1513
    return 'NULL' // library marker kkossev.commonLib, line 1514
} // library marker kkossev.commonLib, line 1515

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1517
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1518
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1519
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1520
} // library marker kkossev.commonLib, line 1521

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1523
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :(  // library marker kkossev.commonLib, line 1524
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1525
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1526
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1527
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1528
    } // library marker kkossev.commonLib, line 1529
} // library marker kkossev.commonLib, line 1530

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1532
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1533
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1534
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1535
} // library marker kkossev.commonLib, line 1536

/** // library marker kkossev.commonLib, line 1538
 * Schedule a device health check // library marker kkossev.commonLib, line 1539
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 1540
 */ // library marker kkossev.commonLib, line 1541
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1542
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1543
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1544
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1545
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1546
    } // library marker kkossev.commonLib, line 1547
    else { // library marker kkossev.commonLib, line 1548
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1549
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1550
    } // library marker kkossev.commonLib, line 1551
} // library marker kkossev.commonLib, line 1552

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1554
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1555
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1556
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1557
} // library marker kkossev.commonLib, line 1558

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1560

void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1562
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1563
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1564
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1565
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1566
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1567
    } // library marker kkossev.commonLib, line 1568
} // library marker kkossev.commonLib, line 1569

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1571
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1572
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1573
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1574
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1575
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1576
            logWarn 'not present!' // library marker kkossev.commonLib, line 1577
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1578
        } // library marker kkossev.commonLib, line 1579
    } // library marker kkossev.commonLib, line 1580
    else { // library marker kkossev.commonLib, line 1581
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1582
    } // library marker kkossev.commonLib, line 1583
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1584
} // library marker kkossev.commonLib, line 1585

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1587
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1588
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1589
    if (value == 'online') { // library marker kkossev.commonLib, line 1590
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1591
    } // library marker kkossev.commonLib, line 1592
    else { // library marker kkossev.commonLib, line 1593
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1594
    } // library marker kkossev.commonLib, line 1595
} // library marker kkossev.commonLib, line 1596

/** // library marker kkossev.commonLib, line 1598
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 1599
 */ // library marker kkossev.commonLib, line 1600
void autoPoll() { // library marker kkossev.commonLib, line 1601
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 1602
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1603
    List<String> cmds = [] // library marker kkossev.commonLib, line 1604
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 1605
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 1606
    } // library marker kkossev.commonLib, line 1607

    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1609
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1610
    } // library marker kkossev.commonLib, line 1611
} // library marker kkossev.commonLib, line 1612

/** // library marker kkossev.commonLib, line 1614
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1615
 */ // library marker kkossev.commonLib, line 1616
void updated() { // library marker kkossev.commonLib, line 1617
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1618
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1619
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1620
    unschedule() // library marker kkossev.commonLib, line 1621

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1623
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1624
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1625
    } // library marker kkossev.commonLib, line 1626
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1627
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1628
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1629
    } // library marker kkossev.commonLib, line 1630

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1632
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1633
        // schedule the periodic timer // library marker kkossev.commonLib, line 1634
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1635
        if (interval > 0) { // library marker kkossev.commonLib, line 1636
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1637
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1638
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1639
        } // library marker kkossev.commonLib, line 1640
    } // library marker kkossev.commonLib, line 1641
    else { // library marker kkossev.commonLib, line 1642
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1643
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1644
    } // library marker kkossev.commonLib, line 1645
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1646
        customUpdated() // library marker kkossev.commonLib, line 1647
    } // library marker kkossev.commonLib, line 1648

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1650
} // library marker kkossev.commonLib, line 1651

/** // library marker kkossev.commonLib, line 1653
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 1654
 */ // library marker kkossev.commonLib, line 1655
void logsOff() { // library marker kkossev.commonLib, line 1656
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1657
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1658
} // library marker kkossev.commonLib, line 1659
void traceOff() { // library marker kkossev.commonLib, line 1660
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1661
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1662
} // library marker kkossev.commonLib, line 1663

void configure(String command) { // library marker kkossev.commonLib, line 1665
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1666
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1667
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1668
        return // library marker kkossev.commonLib, line 1669
    } // library marker kkossev.commonLib, line 1670
    // // library marker kkossev.commonLib, line 1671
    String func // library marker kkossev.commonLib, line 1672
    try { // library marker kkossev.commonLib, line 1673
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1674
        "$func"() // library marker kkossev.commonLib, line 1675
    } // library marker kkossev.commonLib, line 1676
    catch (e) { // library marker kkossev.commonLib, line 1677
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1678
        return // library marker kkossev.commonLib, line 1679
    } // library marker kkossev.commonLib, line 1680
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1681
} // library marker kkossev.commonLib, line 1682

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1684
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1685
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1686
} // library marker kkossev.commonLib, line 1687

void loadAllDefaults() { // library marker kkossev.commonLib, line 1689
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1690
    deleteAllSettings() // library marker kkossev.commonLib, line 1691
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1692
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1693
    deleteAllStates() // library marker kkossev.commonLib, line 1694
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1695
    initialize() // library marker kkossev.commonLib, line 1696
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1697
    updated() // library marker kkossev.commonLib, line 1698
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1699
} // library marker kkossev.commonLib, line 1700

void configureNow() { // library marker kkossev.commonLib, line 1702
    configure() // library marker kkossev.commonLib, line 1703
} // library marker kkossev.commonLib, line 1704

/** // library marker kkossev.commonLib, line 1706
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1707
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1708
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1709
 */ // library marker kkossev.commonLib, line 1710
void configure() { // library marker kkossev.commonLib, line 1711
    List<String> cmds = [] // library marker kkossev.commonLib, line 1712
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1713
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1714
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1715
    if (isTuya()) { // library marker kkossev.commonLib, line 1716
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1717
    } // library marker kkossev.commonLib, line 1718
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1719
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1720
    } // library marker kkossev.commonLib, line 1721
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1722
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1723
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1724
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1725
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1726
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1727
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1728
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1729
    } // library marker kkossev.commonLib, line 1730
    else { // library marker kkossev.commonLib, line 1731
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1732
    } // library marker kkossev.commonLib, line 1733
} // library marker kkossev.commonLib, line 1734

/** // library marker kkossev.commonLib, line 1736
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 1737
 */ // library marker kkossev.commonLib, line 1738
void installed() { // library marker kkossev.commonLib, line 1739
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1740
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1741
    // populate some default values for attributes // library marker kkossev.commonLib, line 1742
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1743
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1744
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1745
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1746
} // library marker kkossev.commonLib, line 1747

/** // library marker kkossev.commonLib, line 1749
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1750
 */ // library marker kkossev.commonLib, line 1751
void initialize() { // library marker kkossev.commonLib, line 1752
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1753
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1754
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1755
    updateTuyaVersion() // library marker kkossev.commonLib, line 1756
    updateAqaraVersion() // library marker kkossev.commonLib, line 1757
} // library marker kkossev.commonLib, line 1758

/* // library marker kkossev.commonLib, line 1760
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1761
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1762
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1763
*/ // library marker kkossev.commonLib, line 1764

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1766
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1767
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1768
} // library marker kkossev.commonLib, line 1769

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1771
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1772
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1773
} // library marker kkossev.commonLib, line 1774

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1776
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1777
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1778
} // library marker kkossev.commonLib, line 1779

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1781
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1782
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1783
        return // library marker kkossev.commonLib, line 1784
    } // library marker kkossev.commonLib, line 1785
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1786
    cmd.each { // library marker kkossev.commonLib, line 1787
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1788
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1789
            return // library marker kkossev.commonLib, line 1790
        } // library marker kkossev.commonLib, line 1791
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1792
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1793
    } // library marker kkossev.commonLib, line 1794
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1795
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1796
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1797
} // library marker kkossev.commonLib, line 1798

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1800

String getDeviceInfo() { // library marker kkossev.commonLib, line 1802
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1803
} // library marker kkossev.commonLib, line 1804

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1806
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1807
} // library marker kkossev.commonLib, line 1808

@CompileStatic // library marker kkossev.commonLib, line 1810
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1811
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1812
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1813
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1814
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1815
        initializeVars(false) // library marker kkossev.commonLib, line 1816
        updateTuyaVersion() // library marker kkossev.commonLib, line 1817
        updateAqaraVersion() // library marker kkossev.commonLib, line 1818
    } // library marker kkossev.commonLib, line 1819
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1820
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1821
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1822
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1823
} // library marker kkossev.commonLib, line 1824


// credits @thebearmay // library marker kkossev.commonLib, line 1827
String getModel() { // library marker kkossev.commonLib, line 1828
    try { // library marker kkossev.commonLib, line 1829
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1830
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1831
    } catch (ignore) { // library marker kkossev.commonLib, line 1832
        try { // library marker kkossev.commonLib, line 1833
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1834
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1835
                return model // library marker kkossev.commonLib, line 1836
            } // library marker kkossev.commonLib, line 1837
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1838
            return '' // library marker kkossev.commonLib, line 1839
        } // library marker kkossev.commonLib, line 1840
    } // library marker kkossev.commonLib, line 1841
} // library marker kkossev.commonLib, line 1842

// credits @thebearmay // library marker kkossev.commonLib, line 1844
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1845
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1846
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1847
    String revision = tokens.last() // library marker kkossev.commonLib, line 1848
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1849
} // library marker kkossev.commonLib, line 1850

/** // library marker kkossev.commonLib, line 1852
 * called from TODO // library marker kkossev.commonLib, line 1853
 */ // library marker kkossev.commonLib, line 1854

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1856
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1857
    unschedule() // library marker kkossev.commonLib, line 1858
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1859
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1860

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1862
} // library marker kkossev.commonLib, line 1863

void resetStatistics() { // library marker kkossev.commonLib, line 1865
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1866
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1867
} // library marker kkossev.commonLib, line 1868

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1870
void resetStats() { // library marker kkossev.commonLib, line 1871
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1872
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1873
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1874
    state.stats['rxCtr'] = 0 ; state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1875
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1876
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1877
} // library marker kkossev.commonLib, line 1878

/** // library marker kkossev.commonLib, line 1880
 * called from TODO // library marker kkossev.commonLib, line 1881
 */ // library marker kkossev.commonLib, line 1882
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1883
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1884
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1885
        state.clear() // library marker kkossev.commonLib, line 1886
        unschedule() // library marker kkossev.commonLib, line 1887
        resetStats() // library marker kkossev.commonLib, line 1888
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1889
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1890
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1891
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1892
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1893
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1894
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1895
    } // library marker kkossev.commonLib, line 1896

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1898
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1899
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1900
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1901
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1902

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1904
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1905
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1906
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 1907
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1908
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1909
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1910
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1911
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1912
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 1913

    // common libraries initialization - TODO !!!!!!!!!!!!! // library marker kkossev.commonLib, line 1915
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1916
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1917
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1918

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1920
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1921
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1922
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1923
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 1924

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1926
    if ( mm != null) { // library marker kkossev.commonLib, line 1927
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 1928
    } // library marker kkossev.commonLib, line 1929
    else { // library marker kkossev.commonLib, line 1930
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 1931
    } // library marker kkossev.commonLib, line 1932
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1933
    if ( ep  != null) { // library marker kkossev.commonLib, line 1934
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1935
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1936
    } // library marker kkossev.commonLib, line 1937
    else { // library marker kkossev.commonLib, line 1938
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1939
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1940
    } // library marker kkossev.commonLib, line 1941
} // library marker kkossev.commonLib, line 1942

/** // library marker kkossev.commonLib, line 1944
 * called from TODO // library marker kkossev.commonLib, line 1945
 */ // library marker kkossev.commonLib, line 1946
void setDestinationEP() { // library marker kkossev.commonLib, line 1947
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1948
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1949
        state.destinationEP = ep // library marker kkossev.commonLib, line 1950
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1951
    } // library marker kkossev.commonLib, line 1952
    else { // library marker kkossev.commonLib, line 1953
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1954
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1955
    } // library marker kkossev.commonLib, line 1956
} // library marker kkossev.commonLib, line 1957

void logDebug(final String msg) { // library marker kkossev.commonLib, line 1959
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1960
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 1961
    } // library marker kkossev.commonLib, line 1962
} // library marker kkossev.commonLib, line 1963

void logInfo(final String msg) { // library marker kkossev.commonLib, line 1965
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 1966
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 1967
    } // library marker kkossev.commonLib, line 1968
} // library marker kkossev.commonLib, line 1969

void logWarn(final String msg) { // library marker kkossev.commonLib, line 1971
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1972
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 1973
    } // library marker kkossev.commonLib, line 1974
} // library marker kkossev.commonLib, line 1975

void logTrace(final String msg) { // library marker kkossev.commonLib, line 1977
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 1978
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 1979
    } // library marker kkossev.commonLib, line 1980
} // library marker kkossev.commonLib, line 1981

// _DEBUG mode only // library marker kkossev.commonLib, line 1983
void getAllProperties() { // library marker kkossev.commonLib, line 1984
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1985
    device.properties.each { it -> // library marker kkossev.commonLib, line 1986
        log.debug it // library marker kkossev.commonLib, line 1987
    } // library marker kkossev.commonLib, line 1988
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1989
    settings.each { it -> // library marker kkossev.commonLib, line 1990
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1991
    } // library marker kkossev.commonLib, line 1992
    log.trace 'Done' // library marker kkossev.commonLib, line 1993
} // library marker kkossev.commonLib, line 1994

// delete all Preferences // library marker kkossev.commonLib, line 1996
void deleteAllSettings() { // library marker kkossev.commonLib, line 1997
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1998
    settings.each { it -> // library marker kkossev.commonLib, line 1999
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 2000
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2001
    } // library marker kkossev.commonLib, line 2002
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 2003
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2004
} // library marker kkossev.commonLib, line 2005

// delete all attributes // library marker kkossev.commonLib, line 2007
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2008
    String attributesDeleted = '' // library marker kkossev.commonLib, line 2009
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 2010
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2011
} // library marker kkossev.commonLib, line 2012

// delete all State Variables // library marker kkossev.commonLib, line 2014
void deleteAllStates() { // library marker kkossev.commonLib, line 2015
    String stateDeleted = '' // library marker kkossev.commonLib, line 2016
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 2017
    state.clear() // library marker kkossev.commonLib, line 2018
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2019
} // library marker kkossev.commonLib, line 2020

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2022
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2023
} // library marker kkossev.commonLib, line 2024

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2026
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 2027
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 2028
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 2029
    } // library marker kkossev.commonLib, line 2030
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 2031
} // library marker kkossev.commonLib, line 2032

void parseTest(String par) { // library marker kkossev.commonLib, line 2034
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2035
    log.warn "parseTest - <b>START</b> (${par})" // library marker kkossev.commonLib, line 2036
    parse(par) // library marker kkossev.commonLib, line 2037
    log.warn "parseTest -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 2038
} // library marker kkossev.commonLib, line 2039

def testJob() { // library marker kkossev.commonLib, line 2041
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2042
} // library marker kkossev.commonLib, line 2043

/** // library marker kkossev.commonLib, line 2045
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2046
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2047
 */ // library marker kkossev.commonLib, line 2048
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2049
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2050
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2051
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2052
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2053
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2054
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2055
    String cron // library marker kkossev.commonLib, line 2056
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2057
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2058
    } // library marker kkossev.commonLib, line 2059
    else { // library marker kkossev.commonLib, line 2060
        if (minutes < 60) { // library marker kkossev.commonLib, line 2061
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2062
        } // library marker kkossev.commonLib, line 2063
        else { // library marker kkossev.commonLib, line 2064
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2065
        } // library marker kkossev.commonLib, line 2066
    } // library marker kkossev.commonLib, line 2067
    return cron // library marker kkossev.commonLib, line 2068
} // library marker kkossev.commonLib, line 2069

// credits @thebearmay // library marker kkossev.commonLib, line 2071
String formatUptime() { // library marker kkossev.commonLib, line 2072
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2073
} // library marker kkossev.commonLib, line 2074

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2076
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2077
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2078
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2079
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2080
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2081
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2082
} // library marker kkossev.commonLib, line 2083

boolean isTuya() { // library marker kkossev.commonLib, line 2085
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2086
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2087
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2088
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2089
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2090
} // library marker kkossev.commonLib, line 2091

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2093
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 2094
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2095
    if (application != null) { // library marker kkossev.commonLib, line 2096
        Integer ver // library marker kkossev.commonLib, line 2097
        try { // library marker kkossev.commonLib, line 2098
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2099
        } // library marker kkossev.commonLib, line 2100
        catch (e) { // library marker kkossev.commonLib, line 2101
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2102
            return // library marker kkossev.commonLib, line 2103
        } // library marker kkossev.commonLib, line 2104
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2105
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2106
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2107
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2108
        } // library marker kkossev.commonLib, line 2109
    } // library marker kkossev.commonLib, line 2110
} // library marker kkossev.commonLib, line 2111

boolean isAqara() { // library marker kkossev.commonLib, line 2113
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2114
} // library marker kkossev.commonLib, line 2115

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2117
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 2118
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2119
    if (application != null) { // library marker kkossev.commonLib, line 2120
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2121
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2122
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2123
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2124
        } // library marker kkossev.commonLib, line 2125
    } // library marker kkossev.commonLib, line 2126
} // library marker kkossev.commonLib, line 2127

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2129
    try { // library marker kkossev.commonLib, line 2130
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2131
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2132
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2133
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2134
    } catch (e) { // library marker kkossev.commonLib, line 2135
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2136
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2137
    } // library marker kkossev.commonLib, line 2138
} // library marker kkossev.commonLib, line 2139

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2141
    try { // library marker kkossev.commonLib, line 2142
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2143
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2144
        return date.getTime() // library marker kkossev.commonLib, line 2145
    } catch (e) { // library marker kkossev.commonLib, line 2146
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2147
        return now() // library marker kkossev.commonLib, line 2148
    } // library marker kkossev.commonLib, line 2149
} // library marker kkossev.commonLib, line 2150


// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (168) kkossev.illuminanceLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.illuminanceLib, line 1
library( // library marker kkossev.illuminanceLib, line 2
    base: 'driver', // library marker kkossev.illuminanceLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.illuminanceLib, line 4
    category: 'zigbee', // library marker kkossev.illuminanceLib, line 5
    description: 'Zigbee Illuminance Library', // library marker kkossev.illuminanceLib, line 6
    name: 'illuminanceLib', // library marker kkossev.illuminanceLib, line 7
    namespace: 'kkossev', // library marker kkossev.illuminanceLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy', // library marker kkossev.illuminanceLib, line 9
    version: '3.0.1', // library marker kkossev.illuminanceLib, line 10
    documentationLink: '' // library marker kkossev.illuminanceLib, line 11
) // library marker kkossev.illuminanceLib, line 12
/* // library marker kkossev.illuminanceLib, line 13
 *  Zigbee Illuminance Library // library marker kkossev.illuminanceLib, line 14
 * // library marker kkossev.illuminanceLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.illuminanceLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.illuminanceLib, line 17
 * // library marker kkossev.illuminanceLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.illuminanceLib, line 19
 * // library marker kkossev.illuminanceLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.illuminanceLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.illuminanceLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.illuminanceLib, line 23
 * // library marker kkossev.illuminanceLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added illuminanceLib.groovy // library marker kkossev.illuminanceLib, line 25
 * // library marker kkossev.illuminanceLib, line 26
 *                                   TODO: illum threshold not working! // library marker kkossev.illuminanceLib, line 27
 *                                   TODO: check illuminanceInitializeVars() and illuminanceProcessTuyaDP() usage // library marker kkossev.illuminanceLib, line 28
*/ // library marker kkossev.illuminanceLib, line 29

static String illuminanceLibVersion()   { '3.0.1' } // library marker kkossev.illuminanceLib, line 31
static String illuminanceLibStamp() { '2024/04/26 8:06 AM' } // library marker kkossev.illuminanceLib, line 32

metadata { // library marker kkossev.illuminanceLib, line 34
    // no capabilities // library marker kkossev.illuminanceLib, line 35
    // no attributes // library marker kkossev.illuminanceLib, line 36
    // no commands // library marker kkossev.illuminanceLib, line 37
    preferences { // library marker kkossev.illuminanceLib, line 38
        // no prefrences // library marker kkossev.illuminanceLib, line 39
    } // library marker kkossev.illuminanceLib, line 40
} // library marker kkossev.illuminanceLib, line 41

@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 10 // library marker kkossev.illuminanceLib, line 43

void customParseIlluminanceCluster(final Map descMap) { // library marker kkossev.illuminanceLib, line 45
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.illuminanceLib, line 46
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.illuminanceLib, line 47
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.illuminanceLib, line 48
    handleIlluminanceEvent(lux) // library marker kkossev.illuminanceLib, line 49
} // library marker kkossev.illuminanceLib, line 50

void handleIlluminanceEvent(int illuminance, boolean isDigital=false) { // library marker kkossev.illuminanceLib, line 52
    Map eventMap = [:] // library marker kkossev.illuminanceLib, line 53
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.illuminanceLib, line 54
    eventMap.name = 'illuminance' // library marker kkossev.illuminanceLib, line 55
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.illuminanceLib, line 56
    eventMap.value  = illumCorrected // library marker kkossev.illuminanceLib, line 57
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.illuminanceLib, line 58
    eventMap.unit = 'lx' // library marker kkossev.illuminanceLib, line 59
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.illuminanceLib, line 60
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.illuminanceLib, line 61
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.illuminanceLib, line 62
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.illuminanceLib, line 63
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.illuminanceLib, line 64
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.illuminanceLib, line 65
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.illuminanceLib, line 66
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.illuminanceLib, line 67
        return // library marker kkossev.illuminanceLib, line 68
    } // library marker kkossev.illuminanceLib, line 69
    if (timeElapsed >= minTime) { // library marker kkossev.illuminanceLib, line 70
        logInfo "${eventMap.descriptionText}" // library marker kkossev.illuminanceLib, line 71
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.illuminanceLib, line 72
        state.lastRx['illumTime'] = now() // library marker kkossev.illuminanceLib, line 73
        sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 74
    } // library marker kkossev.illuminanceLib, line 75
    else {         // queue the event // library marker kkossev.illuminanceLib, line 76
        eventMap.type = 'delayed' // library marker kkossev.illuminanceLib, line 77
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.illuminanceLib, line 78
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.illuminanceLib, line 79
    } // library marker kkossev.illuminanceLib, line 80
} // library marker kkossev.illuminanceLib, line 81

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.illuminanceLib, line 83
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.illuminanceLib, line 84
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.illuminanceLib, line 85
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.illuminanceLib, line 86
    sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 87
} // library marker kkossev.illuminanceLib, line 88

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.illuminanceLib, line 90

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.illuminanceLib, line 92
void illuminanceProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.illuminanceLib, line 93
    switch (dp) { // library marker kkossev.illuminanceLib, line 94
        case 0x01 : // on/off // library marker kkossev.illuminanceLib, line 95
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 96
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.illuminanceLib, line 97
            } // library marker kkossev.illuminanceLib, line 98
            else { // library marker kkossev.illuminanceLib, line 99
                sendSwitchEvent(fncmd) // library marker kkossev.illuminanceLib, line 100
            } // library marker kkossev.illuminanceLib, line 101
            break // library marker kkossev.illuminanceLib, line 102
        case 0x02 : // library marker kkossev.illuminanceLib, line 103
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 104
                handleIlluminanceEvent(fncmd) // library marker kkossev.illuminanceLib, line 105
            } // library marker kkossev.illuminanceLib, line 106
            else { // library marker kkossev.illuminanceLib, line 107
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 108
            } // library marker kkossev.illuminanceLib, line 109
            break // library marker kkossev.illuminanceLib, line 110
        case 0x04 : // battery // library marker kkossev.illuminanceLib, line 111
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.illuminanceLib, line 112
            break // library marker kkossev.illuminanceLib, line 113
        default : // library marker kkossev.illuminanceLib, line 114
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 115
            break // library marker kkossev.illuminanceLib, line 116
    } // library marker kkossev.illuminanceLib, line 117
} // library marker kkossev.illuminanceLib, line 118

void illuminanceInitializeVars( boolean fullInit = false ) { // library marker kkossev.illuminanceLib, line 120
    logDebug "customInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.illuminanceLib, line 121
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 122
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 123
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 124
    } // library marker kkossev.illuminanceLib, line 125
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 126
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.illuminanceLib, line 127
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.illuminanceLib, line 128
    } // library marker kkossev.illuminanceLib, line 129
} // library marker kkossev.illuminanceLib, line 130

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter */ // library marker kkossev.xiaomiLib, line 1
library( // library marker kkossev.xiaomiLib, line 2
    base: 'driver', // library marker kkossev.xiaomiLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.xiaomiLib, line 4
    category: 'zigbee', // library marker kkossev.xiaomiLib, line 5
    description: 'Xiaomi Library', // library marker kkossev.xiaomiLib, line 6
    name: 'xiaomiLib', // library marker kkossev.xiaomiLib, line 7
    namespace: 'kkossev', // library marker kkossev.xiaomiLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', // library marker kkossev.xiaomiLib, line 9
    version: '1.0.2', // library marker kkossev.xiaomiLib, line 10
    documentationLink: '' // library marker kkossev.xiaomiLib, line 11
) // library marker kkossev.xiaomiLib, line 12
/* // library marker kkossev.xiaomiLib, line 13
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 14
 * // library marker kkossev.xiaomiLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 17
 * // library marker kkossev.xiaomiLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 19
 * // library marker kkossev.xiaomiLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 23
 * // library marker kkossev.xiaomiLib, line 24
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 25
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 26
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) Groovy linting; aqaraCube specific code; // library marker kkossev.xiaomiLib, line 27
 * // library marker kkossev.xiaomiLib, line 28
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 29
*/ // library marker kkossev.xiaomiLib, line 30

/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 32
static String xiaomiLibVersion()   { '1.0.2' } // library marker kkossev.xiaomiLib, line 33
/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 34
static String xiaomiLibStamp() { '2024/04/06 12:14 PM' } // library marker kkossev.xiaomiLib, line 35

boolean isAqaraTVOC_Lib()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 37
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.xiaomiLib, line 38

// no metadata for this library! // library marker kkossev.xiaomiLib, line 40

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 42

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 44
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 45
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 46
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 47
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 48
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 49
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 50
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 52
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 53
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 54
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 55
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 56
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 57
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 58
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 59

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 61
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 62
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 63
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 64
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 65
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 66
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 67

// called from parseXiaomiCluster() in the main code ... // library marker kkossev.xiaomiLib, line 69
// // library marker kkossev.xiaomiLib, line 70
void parseXiaomiClusterLib(final Map descMap) { // library marker kkossev.xiaomiLib, line 71
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 72
        logTrace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 73
    } // library marker kkossev.xiaomiLib, line 74
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 75
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 76
        return // library marker kkossev.xiaomiLib, line 77
    } // library marker kkossev.xiaomiLib, line 78
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 79
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 80
        return // library marker kkossev.xiaomiLib, line 81
    } // library marker kkossev.xiaomiLib, line 82
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 83
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 84
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 85
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 86
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 87
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 90
            log.info 'unknown attribute - resetting?' // library marker kkossev.xiaomiLib, line 91
            break // library marker kkossev.xiaomiLib, line 92
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 93
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 94
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 95
            break // library marker kkossev.xiaomiLib, line 96
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 97
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 98
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 99
            break // library marker kkossev.xiaomiLib, line 100
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 101
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 102
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 103
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 104
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 105
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 106
            } // library marker kkossev.xiaomiLib, line 107
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 108
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 109
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 110
            } // library marker kkossev.xiaomiLib, line 111
            break // library marker kkossev.xiaomiLib, line 112
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 113
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 114
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 115
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 116
            break // library marker kkossev.xiaomiLib, line 117
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 118
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 119
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 120
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 121
            break // library marker kkossev.xiaomiLib, line 122
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 123
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 124
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 125
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 126
            break // library marker kkossev.xiaomiLib, line 127
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 128
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 129
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 130
            break // library marker kkossev.xiaomiLib, line 131
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 132
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 133
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 134
            break // library marker kkossev.xiaomiLib, line 135
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 136
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 137
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 138
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 139
                sendZigbeeCommands(customRefresh()) // library marker kkossev.xiaomiLib, line 140
            } // library marker kkossev.xiaomiLib, line 141
            break // library marker kkossev.xiaomiLib, line 142
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1 // library marker kkossev.xiaomiLib, line 143
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 144
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 145
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 146
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 147
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 148
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 149
                } // library marker kkossev.xiaomiLib, line 150
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 151
            } // library marker kkossev.xiaomiLib, line 152
            break // library marker kkossev.xiaomiLib, line 153
        default: // library marker kkossev.xiaomiLib, line 154
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 155
            break // library marker kkossev.xiaomiLib, line 156
    } // library marker kkossev.xiaomiLib, line 157
} // library marker kkossev.xiaomiLib, line 158

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 160
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 161
        switch (tag) { // library marker kkossev.xiaomiLib, line 162
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 163
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 164
                break // library marker kkossev.xiaomiLib, line 165
            case 0x03: // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 167
                break // library marker kkossev.xiaomiLib, line 168
            case 0x05: // library marker kkossev.xiaomiLib, line 169
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 170
                break // library marker kkossev.xiaomiLib, line 171
            case 0x06: // library marker kkossev.xiaomiLib, line 172
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 173
                break // library marker kkossev.xiaomiLib, line 174
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 175
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 176
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 177
                device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 178
                break // library marker kkossev.xiaomiLib, line 179
            case 0x0a: // library marker kkossev.xiaomiLib, line 180
                String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 181
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 182
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 183
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 184
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 185
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 186
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 187
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 188
                } // library marker kkossev.xiaomiLib, line 189
                break // library marker kkossev.xiaomiLib, line 190
            case 0x0b: // library marker kkossev.xiaomiLib, line 191
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 192
                break // library marker kkossev.xiaomiLib, line 193
            case 0x64: // library marker kkossev.xiaomiLib, line 194
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 195
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 196
                break // library marker kkossev.xiaomiLib, line 197
            case 0x65: // library marker kkossev.xiaomiLib, line 198
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 199
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 200
                break // library marker kkossev.xiaomiLib, line 201
            case 0x66: // library marker kkossev.xiaomiLib, line 202
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 203
                else if (isAqaraTVOC_Lib()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 204
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 205
                break // library marker kkossev.xiaomiLib, line 206
            case 0x67: // library marker kkossev.xiaomiLib, line 207
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 208
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 209
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 210
                break // library marker kkossev.xiaomiLib, line 211
            case 0x69: // library marker kkossev.xiaomiLib, line 212
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 213
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 214
                break // library marker kkossev.xiaomiLib, line 215
            case 0x6a: // library marker kkossev.xiaomiLib, line 216
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 217
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 218
                break // library marker kkossev.xiaomiLib, line 219
            case 0x6b: // library marker kkossev.xiaomiLib, line 220
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 221
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 222
                break // library marker kkossev.xiaomiLib, line 223
            case 0x95: // library marker kkossev.xiaomiLib, line 224
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 225
                break // library marker kkossev.xiaomiLib, line 226
            case 0x96: // library marker kkossev.xiaomiLib, line 227
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 228
                break // library marker kkossev.xiaomiLib, line 229
            case 0x97: // library marker kkossev.xiaomiLib, line 230
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 231
                break // library marker kkossev.xiaomiLib, line 232
            case 0x98: // library marker kkossev.xiaomiLib, line 233
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 234
                break // library marker kkossev.xiaomiLib, line 235
            case 0x9b: // library marker kkossev.xiaomiLib, line 236
                if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 237
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 238
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 239
                } // library marker kkossev.xiaomiLib, line 240
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 241
                break // library marker kkossev.xiaomiLib, line 242
            default: // library marker kkossev.xiaomiLib, line 243
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 244
        } // library marker kkossev.xiaomiLib, line 245
    } // library marker kkossev.xiaomiLib, line 246
} // library marker kkossev.xiaomiLib, line 247

/** // library marker kkossev.xiaomiLib, line 249
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 250
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 251
 */ // library marker kkossev.xiaomiLib, line 252
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 253
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 254
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 255
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 256
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 257
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 258
    } // library marker kkossev.xiaomiLib, line 259
    return bigInt // library marker kkossev.xiaomiLib, line 260
} // library marker kkossev.xiaomiLib, line 261

/** // library marker kkossev.xiaomiLib, line 263
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 264
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 265
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 266
 */ // library marker kkossev.xiaomiLib, line 267
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 268
    final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 269
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 270
    new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 271
        while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 272
            int tag = stream.read() // library marker kkossev.xiaomiLib, line 273
            int dataType = stream.read() // library marker kkossev.xiaomiLib, line 274
            Object value // library marker kkossev.xiaomiLib, line 275
            if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 276
                int length = stream.read() // library marker kkossev.xiaomiLib, line 277
                byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 278
                stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 279
                value = new String(byteArr) // library marker kkossev.xiaomiLib, line 280
            } else { // library marker kkossev.xiaomiLib, line 281
                int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 282
                value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 283
            } // library marker kkossev.xiaomiLib, line 284
            results[tag] = value // library marker kkossev.xiaomiLib, line 285
        } // library marker kkossev.xiaomiLib, line 286
    } // library marker kkossev.xiaomiLib, line 287
    return results // library marker kkossev.xiaomiLib, line 288
} // library marker kkossev.xiaomiLib, line 289

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 291
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 292
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 293
    return cmds // library marker kkossev.xiaomiLib, line 294
} // library marker kkossev.xiaomiLib, line 295

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 297
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 298
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 299
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 300
    return cmds // library marker kkossev.xiaomiLib, line 301
} // library marker kkossev.xiaomiLib, line 302

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 304
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 305
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 306
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 307
    return cmds // library marker kkossev.xiaomiLib, line 308
} // library marker kkossev.xiaomiLib, line 309

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 311
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 312
} // library marker kkossev.xiaomiLib, line 313

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 315
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 316
} // library marker kkossev.xiaomiLib, line 317

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~
