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
 * ver. 3.2.0  2024-08-03 kkossev  - (dev.branch)
 * ver. 3.2.1  2025-12-20 kkossev  - (dev.branch) common Lib 4.0.3 allignment; importUrl correction
 *
 *                                   TODO:
 */

static String version() { '3.2.1' }
static String timeStamp() { '2025/12/20 4:41 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field

@Field static final String DEVICE_TYPE = 'LightSensor'
deviceType = 'LightSensor'





metadata {
    definition(
        name: 'Tuya Zigbee Light Sensor',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Light%20Sensor/Tuya_Zigbee_Light_Sensor_lib_included.groovy',
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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/commonLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-commonLib', // library marker kkossev.commonLib, line 4
    version: '4.0.3' // library marker kkossev.commonLib, line 5
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
  * .............................. // library marker kkossev.commonLib, line 24
  * ver. 3.5.2  2025-08-13 kkossev  - Status attribute renamed to _status_ // library marker kkossev.commonLib, line 25
  * ver. 4.0.0  2025-09-17 kkossev  - deviceProfileV4; HOBEIAN as Tuya device; customInitialize() hook; // library marker kkossev.commonLib, line 26
  * ver. 4.0.1  2025-10-14 kkossev  - added clusters 0xFC80 and 0xFC81 // library marker kkossev.commonLib, line 27
  * ver. 4.0.2  2025-10-18 kkossev  - added tuyaDelay in sendTuyaCommand() // library marker kkossev.commonLib, line 28
  * ver. 4.0.3  2025-10-18 kkossev  - added ignoreDuplicatedZigbeeMessages setting; DIGITAL_TIMER increased to 5000 ms // library marker kkossev.commonLib, line 29
  * // library marker kkossev.commonLib, line 30
  *                                   TODO: change the offline threshold to 2  // library marker kkossev.commonLib, line 31
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 32
  *                                   TODO: make the configure() without parameter smart - analyze the State variables and call delete states.... call ActiveAndpoints() or/amd initialize() or/and configure() // library marker kkossev.commonLib, line 33
  *                                   TODO: check - offlineCtr is not increasing? (ZBMicro); // library marker kkossev.commonLib, line 34
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 35
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 36
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 37
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 38
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 40
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 41
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 42
  * // library marker kkossev.commonLib, line 43
*/ // library marker kkossev.commonLib, line 44

String commonLibVersion() { '4.0.3' } // library marker kkossev.commonLib, line 46
String commonLibStamp() { '2025/12/06 10:51 PM' } // library marker kkossev.commonLib, line 47

import groovy.transform.Field // library marker kkossev.commonLib, line 49
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 50
import hubitat.device.Protocol // library marker kkossev.commonLib, line 51
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 52
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 53
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 54
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 55
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 56
import java.math.BigDecimal // library marker kkossev.commonLib, line 57

metadata { // library marker kkossev.commonLib, line 59
        if (_DEBUG) { // library marker kkossev.commonLib, line 60
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 61
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 62
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 63
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 64
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 65
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 66
            ] // library marker kkossev.commonLib, line 67
        } // library marker kkossev.commonLib, line 68

        // common capabilities for all device types // library marker kkossev.commonLib, line 70
        capability 'Configuration' // library marker kkossev.commonLib, line 71
        capability 'Refresh' // library marker kkossev.commonLib, line 72
        capability 'HealthCheck' // library marker kkossev.commonLib, line 73
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 74

        // common attributes for all device types // library marker kkossev.commonLib, line 76
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 77
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 78
        attribute '_status_', 'string' // library marker kkossev.commonLib, line 79

        // common commands for all device types // library marker kkossev.commonLib, line 81
        command 'configure', [[name:'?? Advanced administrative and diagnostic commands • Use only when troubleshooting or reconfiguring the device', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 82
        command 'ping', [[name:'?? Test device connectivity and measure response time • Updates the RTT attribute with round-trip time in milliseconds']] // library marker kkossev.commonLib, line 83
        command 'refresh', [[name:"?? Query the device for current state and update the attributes. • ?? Battery-powered 'sleepy' devices may not respond!"]] // library marker kkossev.commonLib, line 84

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 86
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 87

    preferences { // library marker kkossev.commonLib, line 89
        // txtEnable and logEnable moved to the custom driver settings - copy& paste there ... // library marker kkossev.commonLib, line 90
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 91
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 92

        if (device) { // library marker kkossev.commonLib, line 94
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'The advanced options should be already automatically set in an optimal way for your device...Click on the "Save and Close" button when toggling this option!', defaultValue: false // library marker kkossev.commonLib, line 95
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 96
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 97
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 98
                input name: 'ignoreDuplicatedZigbeeMessages', type: 'bool', title: '<b>Ignore Duplicated Zigbee Messages</b>', defaultValue: false, description: 'Ignore identical Zigbee attribute reports received within short time periods to reduce log spam and redundant processing' // library marker kkossev.commonLib, line 99
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 100
            } // library marker kkossev.commonLib, line 101
        } // library marker kkossev.commonLib, line 102
    } // library marker kkossev.commonLib, line 103
} // library marker kkossev.commonLib, line 104

@Field static final Integer IGNORE_DUPLICATED_ZIGBEE_MESSAGES_TIMER = 1000  // 1 second // library marker kkossev.commonLib, line 106
@Field static final Integer DIGITAL_TIMER = 5000             // command was sent by this driver // library marker kkossev.commonLib, line 107
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 108
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 109
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 110
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 111
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 112
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 113
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 114
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 115
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 116
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 117

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 119
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 120
] // library marker kkossev.commonLib, line 121
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 122
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 123
] // library marker kkossev.commonLib, line 124

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 126
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 127
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 128
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 129
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 130
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 131
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 132
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 133
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 134
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 135
    '           -             '  : [key:1, function: 'configureHelp'] // library marker kkossev.commonLib, line 136
] // library marker kkossev.commonLib, line 137

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 139

/** // library marker kkossev.commonLib, line 141
 * Parse Zigbee message // library marker kkossev.commonLib, line 142
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 143
 */ // library marker kkossev.commonLib, line 144
public void parse(final String description) { // library marker kkossev.commonLib, line 145

    Map stateCopy = state            // .clone() throws java.lang.CloneNotSupportedException in HE platform version 2.4.1.155 ! // library marker kkossev.commonLib, line 147
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 148
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 149
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 150
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 151
    setHealthStatusOnline(state)    // +2 ms // library marker kkossev.commonLib, line 152

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 154
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 155
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 156
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 157
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 158
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 159
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
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 198
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 199
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 200
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 201
            } // library marker kkossev.commonLib, line 202
            break // library marker kkossev.commonLib, line 203
        default: // library marker kkossev.commonLib, line 204
            if (settings.logEnable) { // library marker kkossev.commonLib, line 205
                logWarn "parse: zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 206
            } // library marker kkossev.commonLib, line 207
            break // library marker kkossev.commonLib, line 208
    } // library marker kkossev.commonLib, line 209
} // library marker kkossev.commonLib, line 210

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 212
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0007:'onOffConfiguration',      0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 213
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 214
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 215
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 216
    0xFC80: 'FC80',              0xFC81: 'FC81',             0xFCC0: 'XiaomiFCC0' // library marker kkossev.commonLib, line 217
] // library marker kkossev.commonLib, line 218

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 220
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 221
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 222
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 223
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 224
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 225
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 226
        return false // library marker kkossev.commonLib, line 227
    } // library marker kkossev.commonLib, line 228
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 229
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 230
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 231
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 232
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 233
        return true // library marker kkossev.commonLib, line 234
    } // library marker kkossev.commonLib, line 235
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 236
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 237
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 238
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 239
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 240
        return true // library marker kkossev.commonLib, line 241
    } // library marker kkossev.commonLib, line 242
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 243
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 244
    } // library marker kkossev.commonLib, line 245
    return false // library marker kkossev.commonLib, line 246
} // library marker kkossev.commonLib, line 247

// not used - throws exception :  error groovy.lang.MissingPropertyException: No such property: rxCtr for class: java.lang.String on line 1568 (method parse) // library marker kkossev.commonLib, line 249
private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 250
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 251
} // library marker kkossev.commonLib, line 252

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 254
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 255
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 256
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 257
    } // library marker kkossev.commonLib, line 258
    return false // library marker kkossev.commonLib, line 259
} // library marker kkossev.commonLib, line 260

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 262
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 263
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 264
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 265
    } // library marker kkossev.commonLib, line 266
    return false // library marker kkossev.commonLib, line 267
} // library marker kkossev.commonLib, line 268

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 270
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 271
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 272
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 273
] // library marker kkossev.commonLib, line 274

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 276
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 277
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
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 289
            // send the active endpoint response // library marker kkossev.commonLib, line 290
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 291
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case 0x0006 : // library marker kkossev.commonLib, line 294
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 295
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 296
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 297
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 300
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 301
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 302
            break // library marker kkossev.commonLib, line 303
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 304
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 305
            if (this.respondsTo('parseSimpleDescriptorResponse')) { parseSimpleDescriptorResponse(descMap) } // library marker kkossev.commonLib, line 306
            break // library marker kkossev.commonLib, line 307
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 308
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 309
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 310
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 311
            break // library marker kkossev.commonLib, line 312
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 313
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 316
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 317
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 318
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 319
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 320
            break // library marker kkossev.commonLib, line 321
        default : // library marker kkossev.commonLib, line 322
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 323
            break // library marker kkossev.commonLib, line 324
    } // library marker kkossev.commonLib, line 325
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 326
} // library marker kkossev.commonLib, line 327

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 329
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 330
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 331
    switch (commandId) { // library marker kkossev.commonLib, line 332
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 333
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 334
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 335
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 336
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 337
        default: // library marker kkossev.commonLib, line 338
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 339
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 340
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 341
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 342
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 343
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 344
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 345
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 346
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 347
            } // library marker kkossev.commonLib, line 348
            break // library marker kkossev.commonLib, line 349
    } // library marker kkossev.commonLib, line 350
} // library marker kkossev.commonLib, line 351

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 353
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 354
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 355
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 356
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 357
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 358
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 359
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 360
    } // library marker kkossev.commonLib, line 361
    else { // library marker kkossev.commonLib, line 362
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 363
    } // library marker kkossev.commonLib, line 364
} // library marker kkossev.commonLib, line 365

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 367
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 368
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 369
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 370
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 371
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 372
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 373
    } // library marker kkossev.commonLib, line 374
    else { // library marker kkossev.commonLib, line 375
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 376
    } // library marker kkossev.commonLib, line 377
} // library marker kkossev.commonLib, line 378

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 380
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 381
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 382
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 383
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 384
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 385
        state.reportingEnabled = true // library marker kkossev.commonLib, line 386
    } // library marker kkossev.commonLib, line 387
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 388
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 389
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 390
    } else { // library marker kkossev.commonLib, line 391
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 392
    } // library marker kkossev.commonLib, line 393
} // library marker kkossev.commonLib, line 394

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 396
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 397
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 398
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 399
    if (status == 0) { // library marker kkossev.commonLib, line 400
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 401
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 402
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 403
        int delta = 0 // library marker kkossev.commonLib, line 404
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 405
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 406
        } // library marker kkossev.commonLib, line 407
        else { // library marker kkossev.commonLib, line 408
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 409
        } // library marker kkossev.commonLib, line 410
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 411
    } // library marker kkossev.commonLib, line 412
    else { // library marker kkossev.commonLib, line 413
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 414
    } // library marker kkossev.commonLib, line 415
} // library marker kkossev.commonLib, line 416

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 418
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 419
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 420
        return false // library marker kkossev.commonLib, line 421
    } // library marker kkossev.commonLib, line 422
    // execute the customHandler function // library marker kkossev.commonLib, line 423
    Boolean result = false // library marker kkossev.commonLib, line 424
    try { // library marker kkossev.commonLib, line 425
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 426
    } // library marker kkossev.commonLib, line 427
    catch (e) { // library marker kkossev.commonLib, line 428
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 429
        return false // library marker kkossev.commonLib, line 430
    } // library marker kkossev.commonLib, line 431
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 432
    return result // library marker kkossev.commonLib, line 433
} // library marker kkossev.commonLib, line 434

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 436
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 437
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 438
    final String commandId = data[0] // library marker kkossev.commonLib, line 439
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 440
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 441
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 442
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 443
    } else { // library marker kkossev.commonLib, line 444
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 445
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 446
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 447
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 448
        } // library marker kkossev.commonLib, line 449
    } // library marker kkossev.commonLib, line 450
} // library marker kkossev.commonLib, line 451

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 453
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 454
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 455
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 456

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 458
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 459
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 460
] // library marker kkossev.commonLib, line 461

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 463
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 464
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 465
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 466
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 467
] // library marker kkossev.commonLib, line 468

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 470
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 471
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 472
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 473
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 474
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 475
    return avg // library marker kkossev.commonLib, line 476
} // library marker kkossev.commonLib, line 477

private void handlePingResponse() { // library marker kkossev.commonLib, line 479
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 480
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 481
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 482

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 484
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 485
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 486
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 487
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 488
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 489
        sendRttEvent() // library marker kkossev.commonLib, line 490
    } // library marker kkossev.commonLib, line 491
    else { // library marker kkossev.commonLib, line 492
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 493
    } // library marker kkossev.commonLib, line 494
    state.states['isPing'] = false // library marker kkossev.commonLib, line 495
} // library marker kkossev.commonLib, line 496

/* // library marker kkossev.commonLib, line 498
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 499
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 500
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 501
*/ // library marker kkossev.commonLib, line 502
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 503

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 505
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 506
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 507
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 508
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 509
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 510
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 511
        case 0x0000: // library marker kkossev.commonLib, line 512
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 513
            break // library marker kkossev.commonLib, line 514
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 515
            if (isPing) { // library marker kkossev.commonLib, line 516
                handlePingResponse() // library marker kkossev.commonLib, line 517
            } // library marker kkossev.commonLib, line 518
            else { // library marker kkossev.commonLib, line 519
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 520
            } // library marker kkossev.commonLib, line 521
            break // library marker kkossev.commonLib, line 522
        case 0x0004: // library marker kkossev.commonLib, line 523
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 524
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 525
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 526
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 527
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 528
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 529
            } // library marker kkossev.commonLib, line 530
            break // library marker kkossev.commonLib, line 531
        case 0x0005: // library marker kkossev.commonLib, line 532
            if (isPing) { // library marker kkossev.commonLib, line 533
                handlePingResponse() // library marker kkossev.commonLib, line 534
            } // library marker kkossev.commonLib, line 535
            else { // library marker kkossev.commonLib, line 536
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 537
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 538
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 539
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 540
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 541
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 542
                } // library marker kkossev.commonLib, line 543
            } // library marker kkossev.commonLib, line 544
            break // library marker kkossev.commonLib, line 545
        case 0x0007: // library marker kkossev.commonLib, line 546
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 547
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 548
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 549
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 550
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 551
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 552
            } // library marker kkossev.commonLib, line 553
            break // library marker kkossev.commonLib, line 554
        case 0xFFDF: // library marker kkossev.commonLib, line 555
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 556
            break // library marker kkossev.commonLib, line 557
        case 0xFFE2: // library marker kkossev.commonLib, line 558
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 559
            break // library marker kkossev.commonLib, line 560
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 561
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 562
            break // library marker kkossev.commonLib, line 563
        case 0xFFFE: // library marker kkossev.commonLib, line 564
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 565
            break // library marker kkossev.commonLib, line 566
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 567
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 568
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 569
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 570
            break // library marker kkossev.commonLib, line 571
        default: // library marker kkossev.commonLib, line 572
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 573
            break // library marker kkossev.commonLib, line 574
    } // library marker kkossev.commonLib, line 575
} // library marker kkossev.commonLib, line 576

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 578
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 579
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 580
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 581
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 582
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 583
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 584
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 585
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 586
        default: logDebug "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 587
    } // library marker kkossev.commonLib, line 588
} // library marker kkossev.commonLib, line 589

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 591
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 592
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 593

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 595
    Map descMap = [:] // library marker kkossev.commonLib, line 596
    try { // library marker kkossev.commonLib, line 597
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 598
    } // library marker kkossev.commonLib, line 599
    catch (e1) { // library marker kkossev.commonLib, line 600
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 601
        // try alternative custom parsing // library marker kkossev.commonLib, line 602
        descMap = [:] // library marker kkossev.commonLib, line 603
        try { // library marker kkossev.commonLib, line 604
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 605
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 606
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 607
            } // library marker kkossev.commonLib, line 608
        } // library marker kkossev.commonLib, line 609
        catch (e2) { // library marker kkossev.commonLib, line 610
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 611
            return [:] // library marker kkossev.commonLib, line 612
        } // library marker kkossev.commonLib, line 613
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 614
    } // library marker kkossev.commonLib, line 615
    return descMap // library marker kkossev.commonLib, line 616
} // library marker kkossev.commonLib, line 617

// return true if the messages is processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 619
// return false if the cluster is not a Tuya cluster // library marker kkossev.commonLib, line 620
private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 621
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 622
        return false // library marker kkossev.commonLib, line 623
    } // library marker kkossev.commonLib, line 624
    // try to parse ... // library marker kkossev.commonLib, line 625
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 626
    Map descMap = [:] // library marker kkossev.commonLib, line 627
    try { // library marker kkossev.commonLib, line 628
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 629
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 630
    } // library marker kkossev.commonLib, line 631
    catch (e) { // library marker kkossev.commonLib, line 632
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 633
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 634
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 635
        return true // library marker kkossev.commonLib, line 636
    } // library marker kkossev.commonLib, line 637

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 639
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 640
    } // library marker kkossev.commonLib, line 641
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 642
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 643
    } // library marker kkossev.commonLib, line 644
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 645
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 646
    } // library marker kkossev.commonLib, line 647
    else { // library marker kkossev.commonLib, line 648
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 649
        return false // library marker kkossev.commonLib, line 650
    } // library marker kkossev.commonLib, line 651
    return true    // processed // library marker kkossev.commonLib, line 652
} // library marker kkossev.commonLib, line 653

// return true if processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 655
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 656
  /* // library marker kkossev.commonLib, line 657
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 658
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 659
        return true // library marker kkossev.commonLib, line 660
    } // library marker kkossev.commonLib, line 661
*/ // library marker kkossev.commonLib, line 662
    Map descMap = [:] // library marker kkossev.commonLib, line 663
    try { // library marker kkossev.commonLib, line 664
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 665
    } // library marker kkossev.commonLib, line 666
    catch (e1) { // library marker kkossev.commonLib, line 667
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 668
        // try alternative custom parsing // library marker kkossev.commonLib, line 669
        descMap = [:] // library marker kkossev.commonLib, line 670
        try { // library marker kkossev.commonLib, line 671
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 672
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 673
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 674
            } // library marker kkossev.commonLib, line 675
        } // library marker kkossev.commonLib, line 676
        catch (e2) { // library marker kkossev.commonLib, line 677
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 678
            return true // library marker kkossev.commonLib, line 679
        } // library marker kkossev.commonLib, line 680
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 681
    } // library marker kkossev.commonLib, line 682
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 683
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 684
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 685
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 686
        return false // library marker kkossev.commonLib, line 687
    } // library marker kkossev.commonLib, line 688
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 689
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 690
    // attribute report received // library marker kkossev.commonLib, line 691
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 692
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 693
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 694
    } // library marker kkossev.commonLib, line 695
    attrData.each { // library marker kkossev.commonLib, line 696
        if (it.status == '86') { // library marker kkossev.commonLib, line 697
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 698
        // TODO - skip parsing? // library marker kkossev.commonLib, line 699
        } // library marker kkossev.commonLib, line 700
        switch (it.cluster) { // library marker kkossev.commonLib, line 701
            case '0000' : // library marker kkossev.commonLib, line 702
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 703
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 704
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 705
                } // library marker kkossev.commonLib, line 706
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 707
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 708
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 709
                } // library marker kkossev.commonLib, line 710
                else { // library marker kkossev.commonLib, line 711
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 712
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 713
                } // library marker kkossev.commonLib, line 714
                break // library marker kkossev.commonLib, line 715
            default : // library marker kkossev.commonLib, line 716
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 717
                break // library marker kkossev.commonLib, line 718
        } // switch // library marker kkossev.commonLib, line 719
    } // for each attribute // library marker kkossev.commonLib, line 720
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 721
} // library marker kkossev.commonLib, line 722

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 724
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 725
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 726
} // library marker kkossev.commonLib, line 727

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 729
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 730
} // library marker kkossev.commonLib, line 731

/* // library marker kkossev.commonLib, line 733
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 734
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 735
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 736
*/ // library marker kkossev.commonLib, line 737
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 738
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 739
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 740

// Tuya Commands // library marker kkossev.commonLib, line 742
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 743
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 744
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 745
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 746
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 747

// tuya DP type // library marker kkossev.commonLib, line 749
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 750
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 751
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 752
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 753
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 754
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 755

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 757
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 758
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 759
    long offset = 0 // library marker kkossev.commonLib, line 760
    int offsetHours = 0 // library marker kkossev.commonLib, line 761
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 762
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 763
    try { // library marker kkossev.commonLib, line 764
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 765
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 766
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 767
    } catch (e) { // library marker kkossev.commonLib, line 768
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 769
    } // library marker kkossev.commonLib, line 770
    // // library marker kkossev.commonLib, line 771
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 772
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 773
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 774
} // library marker kkossev.commonLib, line 775

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 777
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 778
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 779
        syncTuyaDateTime() // library marker kkossev.commonLib, line 780
    } // library marker kkossev.commonLib, line 781
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 782
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 783
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 784
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 785
        if (status != '00') { // library marker kkossev.commonLib, line 786
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 787
        } // library marker kkossev.commonLib, line 788
    } // library marker kkossev.commonLib, line 789
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 790
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 791
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 792
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 793
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 794
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 795
            return // library marker kkossev.commonLib, line 796
        } // library marker kkossev.commonLib, line 797
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 798
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 799
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 800
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 801
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 802
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 803
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 804
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 805
            } // library marker kkossev.commonLib, line 806
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 807
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 808
        } // library marker kkossev.commonLib, line 809
    } // library marker kkossev.commonLib, line 810
    else { // library marker kkossev.commonLib, line 811
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 812
    } // library marker kkossev.commonLib, line 813
} // library marker kkossev.commonLib, line 814

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 816
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 817
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 818
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 819
        //logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 820
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 821
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 822
        } // library marker kkossev.commonLib, line 823
    } // library marker kkossev.commonLib, line 824
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 825
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 826
        //logTrace 'standardProcessTuyaDP: processTuyaDPfromDeviceProfile exists, calling it...' // library marker kkossev.commonLib, line 827
        if (this.respondsTo('isInCooldown') && isInCooldown()) { // library marker kkossev.commonLib, line 828
            logDebug "standardProcessTuyaDP: device is in cooldown, skipping processing of dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 829
            return // library marker kkossev.commonLib, line 830
        } // library marker kkossev.commonLib, line 831
        if (this.respondsTo('ensureCurrentProfileLoaded')) { // library marker kkossev.commonLib, line 832
            ensureCurrentProfileLoaded() // library marker kkossev.commonLib, line 833
        } // library marker kkossev.commonLib, line 834
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 835
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 836
        } // library marker kkossev.commonLib, line 837
    } // library marker kkossev.commonLib, line 838
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 839
} // library marker kkossev.commonLib, line 840

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 842
    int retValue = 0 // library marker kkossev.commonLib, line 843
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 844
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 845
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 846
        int power = 1 // library marker kkossev.commonLib, line 847
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 848
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 849
            power = power * 256 // library marker kkossev.commonLib, line 850
        } // library marker kkossev.commonLib, line 851
    } // library marker kkossev.commonLib, line 852
    return retValue // library marker kkossev.commonLib, line 853
} // library marker kkossev.commonLib, line 854

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 856

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 858
    List<String> cmds = [] // library marker kkossev.commonLib, line 859
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 860
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 861
    int tuyaCmd // library marker kkossev.commonLib, line 862
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 863
    if (this.respondsTo('getDEVICE') && getDEVICE()?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 864
        tuyaCmd = getDEVICE().device.tuyaCmd // library marker kkossev.commonLib, line 865
    } // library marker kkossev.commonLib, line 866
    else { // library marker kkossev.commonLib, line 867
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 868
    } // library marker kkossev.commonLib, line 869
    // Get delay from device profile or use default // library marker kkossev.commonLib, line 870
    int tuyaDelay = DEVICE?.device?.tuyaDelay as Integer ?: 201 // library marker kkossev.commonLib, line 871
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = tuyaDelay, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 872
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 873
    return cmds // library marker kkossev.commonLib, line 874
} // library marker kkossev.commonLib, line 875

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 877

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 879
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 880
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 881
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 882
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 883
} // library marker kkossev.commonLib, line 884


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 887
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 888
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 889
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 890
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 891
} // library marker kkossev.commonLib, line 892

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 894
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 895
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 896
    return cmds // library marker kkossev.commonLib, line 897
} // library marker kkossev.commonLib, line 898

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 900
    List<String> cmds = [] // library marker kkossev.commonLib, line 901
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 902
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 903
    } // library marker kkossev.commonLib, line 904
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 905
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 906
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 907
        return // library marker kkossev.commonLib, line 908
    } // library marker kkossev.commonLib, line 909
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 910
} // library marker kkossev.commonLib, line 911

// Invoked from configure() // library marker kkossev.commonLib, line 913
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 914
    List<String> cmds = [] // library marker kkossev.commonLib, line 915
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 916
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 917
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 918
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 919
    } // library marker kkossev.commonLib, line 920
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 921
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 922
    return cmds // library marker kkossev.commonLib, line 923
} // library marker kkossev.commonLib, line 924

// Invoked from configure() // library marker kkossev.commonLib, line 926
public List<String> configureDevice() { // library marker kkossev.commonLib, line 927
    List<String> cmds = [] // library marker kkossev.commonLib, line 928
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 929
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 930
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 931
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 932
    } // library marker kkossev.commonLib, line 933
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 934
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 935
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 936
    return cmds // library marker kkossev.commonLib, line 937
} // library marker kkossev.commonLib, line 938

/* // library marker kkossev.commonLib, line 940
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 941
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 942
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 943
*/ // library marker kkossev.commonLib, line 944

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 946
    List<String> cmds = [] // library marker kkossev.commonLib, line 947
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 948
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 949
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 950
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 951
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 952
            } // library marker kkossev.commonLib, line 953
        } // library marker kkossev.commonLib, line 954
    } // library marker kkossev.commonLib, line 955
    return cmds // library marker kkossev.commonLib, line 956
} // library marker kkossev.commonLib, line 957

public void refresh() { // library marker kkossev.commonLib, line 959
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 960
    checkDriverVersion(state) // library marker kkossev.commonLib, line 961
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 962
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 963
        customCmds = customRefresh() // library marker kkossev.commonLib, line 964
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 965
    } // library marker kkossev.commonLib, line 966
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 967
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 968
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 969
    } // library marker kkossev.commonLib, line 970
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 971
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 972
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 973
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 974
    } // library marker kkossev.commonLib, line 975
    else { // library marker kkossev.commonLib, line 976
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 977
    } // library marker kkossev.commonLib, line 978
} // library marker kkossev.commonLib, line 979

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 981
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 982
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 983

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 985
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 986
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 987
        sendEvent(name: '_status_', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 988
    } // library marker kkossev.commonLib, line 989
    else { // library marker kkossev.commonLib, line 990
        logInfo "${info}" // library marker kkossev.commonLib, line 991
        sendEvent(name: '_status_', value: info, type: 'digital') // library marker kkossev.commonLib, line 992
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 993
    } // library marker kkossev.commonLib, line 994
} // library marker kkossev.commonLib, line 995

public void ping() { // library marker kkossev.commonLib, line 997
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 998
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 999
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1000
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 1001
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 1002
    else if (device.getDataValue('manufacturer') == 'Aqara') { // library marker kkossev.commonLib, line 1003
        logDebug 'Aqara device ping...' // library marker kkossev.commonLib, line 1004
        sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [destEndpoint: 0x01], 0) ) // library marker kkossev.commonLib, line 1005
    } // library marker kkossev.commonLib, line 1006
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 1007
    logDebug 'ping...' // library marker kkossev.commonLib, line 1008
} // library marker kkossev.commonLib, line 1009

private void virtualPong() { // library marker kkossev.commonLib, line 1011
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1012
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1013
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1014
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1015
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1016
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1017
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1018
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1019
        sendRttEvent() // library marker kkossev.commonLib, line 1020
    } // library marker kkossev.commonLib, line 1021
    else { // library marker kkossev.commonLib, line 1022
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1023
    } // library marker kkossev.commonLib, line 1024
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1025
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1026
} // library marker kkossev.commonLib, line 1027

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1029
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1030
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1031
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1032
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1033
    if (value == null) { // library marker kkossev.commonLib, line 1034
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1035
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1036
    } // library marker kkossev.commonLib, line 1037
    else { // library marker kkossev.commonLib, line 1038
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1039
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1040
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1041
    } // library marker kkossev.commonLib, line 1042
} // library marker kkossev.commonLib, line 1043

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1045
    if (cluster != null) { // library marker kkossev.commonLib, line 1046
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1047
    } // library marker kkossev.commonLib, line 1048
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1049
    return 'NULL' // library marker kkossev.commonLib, line 1050
} // library marker kkossev.commonLib, line 1051

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1053
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1054
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1055
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1056
} // library marker kkossev.commonLib, line 1057

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1059
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1060
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1061
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1062
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1063
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1064
    } // library marker kkossev.commonLib, line 1065
} // library marker kkossev.commonLib, line 1066

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1068
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1069
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1070
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1071
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1072
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1073
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1074
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1075
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1076
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1077
            } // library marker kkossev.commonLib, line 1078
        } // library marker kkossev.commonLib, line 1079
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1080
    } // library marker kkossev.commonLib, line 1081
} // library marker kkossev.commonLib, line 1082

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1084
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1085
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1086
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1087
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1088
    } // library marker kkossev.commonLib, line 1089
    else { // library marker kkossev.commonLib, line 1090
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1091
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1092
    } // library marker kkossev.commonLib, line 1093
} // library marker kkossev.commonLib, line 1094

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1096
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1097
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1098
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1099
} // library marker kkossev.commonLib, line 1100

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1102
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1103
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1104
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1105
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1106
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1107
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1108
    } // library marker kkossev.commonLib, line 1109
} // library marker kkossev.commonLib, line 1110

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1112
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1113
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1114
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1115
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1116
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1117
            logWarn 'not present!' // library marker kkossev.commonLib, line 1118
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1119
        } // library marker kkossev.commonLib, line 1120
    } // library marker kkossev.commonLib, line 1121
    else { // library marker kkossev.commonLib, line 1122
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1123
    } // library marker kkossev.commonLib, line 1124
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1125
    // added 03/06/2025 // library marker kkossev.commonLib, line 1126
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1127
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1128
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1129
    } // library marker kkossev.commonLib, line 1130
} // library marker kkossev.commonLib, line 1131

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1133
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1134
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1135
    if (value == 'online') { // library marker kkossev.commonLib, line 1136
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1137
    } // library marker kkossev.commonLib, line 1138
    else { // library marker kkossev.commonLib, line 1139
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1140
    } // library marker kkossev.commonLib, line 1141
} // library marker kkossev.commonLib, line 1142

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1144
void updated() { // library marker kkossev.commonLib, line 1145
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1146
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1147
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1148
    unschedule() // library marker kkossev.commonLib, line 1149

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1151
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1152
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1153
    } // library marker kkossev.commonLib, line 1154
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1155
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1156
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1157
    } // library marker kkossev.commonLib, line 1158

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1160
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1161
        // schedule the periodic timer // library marker kkossev.commonLib, line 1162
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1163
        if (interval > 0) { // library marker kkossev.commonLib, line 1164
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1165
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1166
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1167
        } // library marker kkossev.commonLib, line 1168
    } // library marker kkossev.commonLib, line 1169
    else { // library marker kkossev.commonLib, line 1170
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1171
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1172
    } // library marker kkossev.commonLib, line 1173
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1174
        customUpdated() // library marker kkossev.commonLib, line 1175
    } // library marker kkossev.commonLib, line 1176

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1178
} // library marker kkossev.commonLib, line 1179

private void logsOff() { // library marker kkossev.commonLib, line 1181
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1182
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1183
} // library marker kkossev.commonLib, line 1184
private void traceOff() { // library marker kkossev.commonLib, line 1185
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1186
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1187
} // library marker kkossev.commonLib, line 1188

public void configure(String command) { // library marker kkossev.commonLib, line 1190
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1191
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1192
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1193
        return // library marker kkossev.commonLib, line 1194
    } // library marker kkossev.commonLib, line 1195
    // // library marker kkossev.commonLib, line 1196
    String func // library marker kkossev.commonLib, line 1197
    try { // library marker kkossev.commonLib, line 1198
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1199
        "$func"() // library marker kkossev.commonLib, line 1200
    } // library marker kkossev.commonLib, line 1201
    catch (e) { // library marker kkossev.commonLib, line 1202
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1203
        return // library marker kkossev.commonLib, line 1204
    } // library marker kkossev.commonLib, line 1205
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1206
} // library marker kkossev.commonLib, line 1207

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1209
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1210
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1211
} // library marker kkossev.commonLib, line 1212

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1214
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1215
    deleteAllSettings() // library marker kkossev.commonLib, line 1216
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1217
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1218
    deleteAllStates() // library marker kkossev.commonLib, line 1219
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1220

    initialize() // library marker kkossev.commonLib, line 1222
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1223
    updated() // library marker kkossev.commonLib, line 1224
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1225
} // library marker kkossev.commonLib, line 1226

private void configureNow() { // library marker kkossev.commonLib, line 1228
    configure() // library marker kkossev.commonLib, line 1229
} // library marker kkossev.commonLib, line 1230

/** // library marker kkossev.commonLib, line 1232
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1233
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1234
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1235
 */ // library marker kkossev.commonLib, line 1236
void configure() { // library marker kkossev.commonLib, line 1237
    List<String> cmds = [] // library marker kkossev.commonLib, line 1238
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1239
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1240
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1241
    if (isTuya()) { // library marker kkossev.commonLib, line 1242
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1243
    } // library marker kkossev.commonLib, line 1244
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1245
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1246
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1247
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1248
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1249
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1250
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1251
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1252
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1253
    } // library marker kkossev.commonLib, line 1254
    else { // library marker kkossev.commonLib, line 1255
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1256
    } // library marker kkossev.commonLib, line 1257
} // library marker kkossev.commonLib, line 1258

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1260
void installed() { // library marker kkossev.commonLib, line 1261
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1262
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1263
    // populate some default values for attributes // library marker kkossev.commonLib, line 1264
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1265
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1266
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1267
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1268
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1269
} // library marker kkossev.commonLib, line 1270

private void queryPowerSource() { // library marker kkossev.commonLib, line 1272
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1273
} // library marker kkossev.commonLib, line 1274

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1276
private void initialize() { // library marker kkossev.commonLib, line 1277
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1278
    logDebug "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1279
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1280
        logDebug "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1281
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1282
    } // library marker kkossev.commonLib, line 1283
    if (this.respondsTo('customInitialize')) { customInitialize() }  // library marker kkossev.commonLib, line 1284
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1285
    updateTuyaVersion() // library marker kkossev.commonLib, line 1286
    updateAqaraVersion() // library marker kkossev.commonLib, line 1287
} // library marker kkossev.commonLib, line 1288

/* // library marker kkossev.commonLib, line 1290
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1291
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1292
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1293
*/ // library marker kkossev.commonLib, line 1294

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1296
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1297
} // library marker kkossev.commonLib, line 1298

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1300
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1301
} // library marker kkossev.commonLib, line 1302

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1304
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1305
} // library marker kkossev.commonLib, line 1306

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1308
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1309
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1310
        return // library marker kkossev.commonLib, line 1311
    } // library marker kkossev.commonLib, line 1312
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1313
    cmd.each { // library marker kkossev.commonLib, line 1314
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1315
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1316
            return // library marker kkossev.commonLib, line 1317
        } // library marker kkossev.commonLib, line 1318
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1319
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1320
    } // library marker kkossev.commonLib, line 1321
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1322
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1323
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1324
} // library marker kkossev.commonLib, line 1325

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1327

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1329
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1330
} // library marker kkossev.commonLib, line 1331

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1333
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1334
} // library marker kkossev.commonLib, line 1335

//@CompileStatic // library marker kkossev.commonLib, line 1337
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1338
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1339
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1340
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()} from version ${stateCopy.driverVersion ?: 'unknown'}") // library marker kkossev.commonLib, line 1341
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1342
        initializeVars(false) // library marker kkossev.commonLib, line 1343
        updateTuyaVersion() // library marker kkossev.commonLib, line 1344
        updateAqaraVersion() // library marker kkossev.commonLib, line 1345
        if (this.respondsTo('customcheckDriverVersion')) { customcheckDriverVersion(stateCopy) } // library marker kkossev.commonLib, line 1346
    } // library marker kkossev.commonLib, line 1347
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1348
} // library marker kkossev.commonLib, line 1349

// credits @thebearmay // library marker kkossev.commonLib, line 1351
String getModel() { // library marker kkossev.commonLib, line 1352
    try { // library marker kkossev.commonLib, line 1353
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1354
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1355
    } catch (ignore) { // library marker kkossev.commonLib, line 1356
        try { // library marker kkossev.commonLib, line 1357
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1358
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1359
                return model // library marker kkossev.commonLib, line 1360
            } // library marker kkossev.commonLib, line 1361
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1362
            return '' // library marker kkossev.commonLib, line 1363
        } // library marker kkossev.commonLib, line 1364
    } // library marker kkossev.commonLib, line 1365
} // library marker kkossev.commonLib, line 1366

// credits @thebearmay // library marker kkossev.commonLib, line 1368
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1369
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1370
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1371
    String revision = tokens.last() // library marker kkossev.commonLib, line 1372
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1373
} // library marker kkossev.commonLib, line 1374

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1376
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1377
    unschedule() // library marker kkossev.commonLib, line 1378
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1379
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1380

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1382
} // library marker kkossev.commonLib, line 1383

void resetStatistics() { // library marker kkossev.commonLib, line 1385
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1386
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1387
} // library marker kkossev.commonLib, line 1388

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1390
void resetStats() { // library marker kkossev.commonLib, line 1391
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1392
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1393
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1394
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1395
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1396
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1397
    if (this.respondsTo('customResetStats')) { customResetStats() } // library marker kkossev.commonLib, line 1398
    logInfo 'statistics reset!' // library marker kkossev.commonLib, line 1399
} // library marker kkossev.commonLib, line 1400

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1402
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1403
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1404
        state.clear() // library marker kkossev.commonLib, line 1405
        unschedule() // library marker kkossev.commonLib, line 1406
        resetStats() // library marker kkossev.commonLib, line 1407
        if (this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1408
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1409
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1410
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1411
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1412
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1413
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1414
    } // library marker kkossev.commonLib, line 1415

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1417
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1418
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1419
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1420
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1421

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1423
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1424
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1425
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1426
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1427
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1428
    if (fullInit || settings?.ignoreDuplicatedZigbeeMessages == null) { device.updateSetting('ignoreDuplicatedZigbeeMessages', false) } // library marker kkossev.commonLib, line 1429
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1430

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1432

    // common libraries initialization // library marker kkossev.commonLib, line 1434
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1435
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1436
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1437
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1438
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1439
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1440
    // // library marker kkossev.commonLib, line 1441
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1442
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1443
    // // library marker kkossev.commonLib, line 1444
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1445
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1446
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1447
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1448

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1450
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1451
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1452
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1453
    if ( ep  != null) { // library marker kkossev.commonLib, line 1454
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1455
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1456
    } // library marker kkossev.commonLib, line 1457
    else { // library marker kkossev.commonLib, line 1458
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1459
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1460
    } // library marker kkossev.commonLib, line 1461
} // library marker kkossev.commonLib, line 1462

// not used!? // library marker kkossev.commonLib, line 1464
void setDestinationEP() { // library marker kkossev.commonLib, line 1465
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1466
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1467
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1468
} // library marker kkossev.commonLib, line 1469

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1471
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1472
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1473
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1474
void logError(final String msg) { if (settings?.txtEnable)   { log.error "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1475

// _DEBUG mode only // library marker kkossev.commonLib, line 1477
void getAllProperties() { // library marker kkossev.commonLib, line 1478
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1479
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1480
} // library marker kkossev.commonLib, line 1481

// delete all Preferences // library marker kkossev.commonLib, line 1483
void deleteAllSettings() { // library marker kkossev.commonLib, line 1484
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1485
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1486
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1487
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1488
} // library marker kkossev.commonLib, line 1489

// delete all attributes // library marker kkossev.commonLib, line 1491
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1492
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1493
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1494
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1495
} // library marker kkossev.commonLib, line 1496

// delete all State Variables // library marker kkossev.commonLib, line 1498
void deleteAllStates() { // library marker kkossev.commonLib, line 1499
    String stateDeleted = '' // library marker kkossev.commonLib, line 1500
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1501
    state.clear() // library marker kkossev.commonLib, line 1502
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1503
} // library marker kkossev.commonLib, line 1504

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1506
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1507
} // library marker kkossev.commonLib, line 1508

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1510
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1511
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1512
} // library marker kkossev.commonLib, line 1513

void testParse(String par) { // library marker kkossev.commonLib, line 1515
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1516
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1517
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1518
    parse(par) // library marker kkossev.commonLib, line 1519
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1520
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1521
} // library marker kkossev.commonLib, line 1522

Object testJob() { // library marker kkossev.commonLib, line 1524
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1525
} // library marker kkossev.commonLib, line 1526

/** // library marker kkossev.commonLib, line 1528
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1529
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1530
 */ // library marker kkossev.commonLib, line 1531
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1532
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1533
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1534
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1535
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1536
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1537
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1538
    String cron // library marker kkossev.commonLib, line 1539
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1540
    else { // library marker kkossev.commonLib, line 1541
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1542
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1543
    } // library marker kkossev.commonLib, line 1544
    return cron // library marker kkossev.commonLib, line 1545
} // library marker kkossev.commonLib, line 1546

// credits @thebearmay // library marker kkossev.commonLib, line 1548
String formatUptime() { // library marker kkossev.commonLib, line 1549
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1550
} // library marker kkossev.commonLib, line 1551

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1553
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1554
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1555
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1556
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1557
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1558
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1559
} // library marker kkossev.commonLib, line 1560

boolean isTuya() { // library marker kkossev.commonLib, line 1562
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1563
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1564
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1565
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1566
    return ((model?.startsWith('TS') && manufacturer?.startsWith('_T')) || model == 'HOBEIAN') ? true : false // library marker kkossev.commonLib, line 1567
} // library marker kkossev.commonLib, line 1568

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1570
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1571
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1572
    if (application != null) { // library marker kkossev.commonLib, line 1573
        Integer ver // library marker kkossev.commonLib, line 1574
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1575
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1576
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1577
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1578
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1579
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1580
        } // library marker kkossev.commonLib, line 1581
    } // library marker kkossev.commonLib, line 1582
} // library marker kkossev.commonLib, line 1583

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1585

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1587
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1588
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1589
    if (application != null) { // library marker kkossev.commonLib, line 1590
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1591
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1592
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1593
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1594
        } // library marker kkossev.commonLib, line 1595
    } // library marker kkossev.commonLib, line 1596
} // library marker kkossev.commonLib, line 1597

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1599
    try { // library marker kkossev.commonLib, line 1600
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1601
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1602
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1603
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1604
    } catch (e) { // library marker kkossev.commonLib, line 1605
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1606
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1607
    } // library marker kkossev.commonLib, line 1608
} // library marker kkossev.commonLib, line 1609

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1611
    try { // library marker kkossev.commonLib, line 1612
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1613
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1614
        return date.getTime() // library marker kkossev.commonLib, line 1615
    } catch (e) { // library marker kkossev.commonLib, line 1616
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1617
        return now() // library marker kkossev.commonLib, line 1618
    } // library marker kkossev.commonLib, line 1619
} // library marker kkossev.commonLib, line 1620

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1622
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1623
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1624
    int seconds = time % 60 // library marker kkossev.commonLib, line 1625
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1626
} // library marker kkossev.commonLib, line 1627

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (168) kkossev.illuminanceLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.illuminanceLib, line 1
library( // library marker kkossev.illuminanceLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Illuminance Library', name: 'illuminanceLib', namespace: 'kkossev', // library marker kkossev.illuminanceLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy', documentationLink: '', // library marker kkossev.illuminanceLib, line 4
    version: '3.2.1' // library marker kkossev.illuminanceLib, line 5
) // library marker kkossev.illuminanceLib, line 6
/* // library marker kkossev.illuminanceLib, line 7
 *  Zigbee Illuminance Library // library marker kkossev.illuminanceLib, line 8
 * // library marker kkossev.illuminanceLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.illuminanceLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.illuminanceLib, line 11
 * // library marker kkossev.illuminanceLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.illuminanceLib, line 13
 * // library marker kkossev.illuminanceLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.illuminanceLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.illuminanceLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.illuminanceLib, line 17
 * // library marker kkossev.illuminanceLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added illuminanceLib.groovy // library marker kkossev.illuminanceLib, line 19
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0 allignment; added capability 'IlluminanceMeasurement'; added illuminanceRefresh() // library marker kkossev.illuminanceLib, line 20
 * ver. 3.2.1  2024-07-06 kkossev  - added illuminanceCoeff; added luxThreshold and illuminanceCoeff to preferences (if applicable) // library marker kkossev.illuminanceLib, line 21
 * // library marker kkossev.illuminanceLib, line 22
 *                                   TODO: illum threshold not working! // library marker kkossev.illuminanceLib, line 23
 *                                   TODO: check illuminanceInitializeVars() and illuminanceProcessTuyaDP() usage // library marker kkossev.illuminanceLib, line 24
*/ // library marker kkossev.illuminanceLib, line 25

static String illuminanceLibVersion()   { '3.2.1' } // library marker kkossev.illuminanceLib, line 27
static String illuminanceLibStamp() { '2024/07/06 1:34 PM' } // library marker kkossev.illuminanceLib, line 28

metadata { // library marker kkossev.illuminanceLib, line 30
    capability 'IlluminanceMeasurement' // library marker kkossev.illuminanceLib, line 31
    // no attributes // library marker kkossev.illuminanceLib, line 32
    // no commands // library marker kkossev.illuminanceLib, line 33
    preferences { // library marker kkossev.illuminanceLib, line 34
        if (device) { // library marker kkossev.illuminanceLib, line 35
            if ((DEVICE?.capabilities?.IlluminanceMeasurement == true) && (DEVICE?.preferences.illuminanceThreshold != false) && !(DEVICE?.device?.isDepricated == true)) { // library marker kkossev.illuminanceLib, line 36
                input('illuminanceThreshold', 'number', title: '<b>Lux threshold</b>', description: 'Minimum change in the lux which will trigger an event', range: '0..999', defaultValue: 5) // library marker kkossev.illuminanceLib, line 37
                if (advancedOptions) { // library marker kkossev.illuminanceLib, line 38
                    input('illuminanceCoeff', 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: 'Illuminance correction coefficient, range (0.10..10.00)', range: '0.10..10.00', defaultValue: 1.00) // library marker kkossev.illuminanceLib, line 39
                } // library marker kkossev.illuminanceLib, line 40
            } // library marker kkossev.illuminanceLib, line 41
            /* // library marker kkossev.illuminanceLib, line 42
            if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 43
                input 'minReportingTime', 'number', title: 'Minimum Reporting Time (sec)', description: 'Minimum time between illuminance reports', defaultValue: 60, required: false // library marker kkossev.illuminanceLib, line 44
                input 'maxReportingTime', 'number', title: 'Maximum Reporting Time (sec)', description: 'Maximum time between illuminance reports', defaultValue: 3600, required: false // library marker kkossev.illuminanceLib, line 45
            } // library marker kkossev.illuminanceLib, line 46
            */ // library marker kkossev.illuminanceLib, line 47
        } // library marker kkossev.illuminanceLib, line 48
    } // library marker kkossev.illuminanceLib, line 49
} // library marker kkossev.illuminanceLib, line 50

@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 10 // library marker kkossev.illuminanceLib, line 52

void standardParseIlluminanceCluster(final Map descMap) { // library marker kkossev.illuminanceLib, line 54
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.illuminanceLib, line 55
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.illuminanceLib, line 56
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.illuminanceLib, line 57
    handleIlluminanceEvent(lux) // library marker kkossev.illuminanceLib, line 58
} // library marker kkossev.illuminanceLib, line 59

void handleIlluminanceEvent(int illuminance, boolean isDigital=false) { // library marker kkossev.illuminanceLib, line 61
    Map eventMap = [:] // library marker kkossev.illuminanceLib, line 62
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.illuminanceLib, line 63
    eventMap.name = 'illuminance' // library marker kkossev.illuminanceLib, line 64
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.illuminanceLib, line 65
    eventMap.value  = illumCorrected // library marker kkossev.illuminanceLib, line 66
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.illuminanceLib, line 67
    eventMap.unit = 'lx' // library marker kkossev.illuminanceLib, line 68
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.illuminanceLib, line 69
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.illuminanceLib, line 70
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME  // defined in commonLib // library marker kkossev.illuminanceLib, line 71
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.illuminanceLib, line 72
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.illuminanceLib, line 73
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.illuminanceLib, line 74
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.illuminanceLib, line 75
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.illuminanceLib, line 76
        return // library marker kkossev.illuminanceLib, line 77
    } // library marker kkossev.illuminanceLib, line 78
    if (timeElapsed >= minTime) { // library marker kkossev.illuminanceLib, line 79
        logInfo "${eventMap.descriptionText}" // library marker kkossev.illuminanceLib, line 80
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.illuminanceLib, line 81
        state.lastRx['illumTime'] = now() // library marker kkossev.illuminanceLib, line 82
        sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 83
    } // library marker kkossev.illuminanceLib, line 84
    else {         // queue the event // library marker kkossev.illuminanceLib, line 85
        eventMap.type = 'delayed' // library marker kkossev.illuminanceLib, line 86
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.illuminanceLib, line 87
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.illuminanceLib, line 88
    } // library marker kkossev.illuminanceLib, line 89
} // library marker kkossev.illuminanceLib, line 90

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.illuminanceLib, line 92
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.illuminanceLib, line 93
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.illuminanceLib, line 94
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.illuminanceLib, line 95
    sendEvent(eventMap) // library marker kkossev.illuminanceLib, line 96
} // library marker kkossev.illuminanceLib, line 97

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.illuminanceLib, line 99

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.illuminanceLib, line 101
void illuminanceProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) { // library marker kkossev.illuminanceLib, line 102
    switch (dp) { // library marker kkossev.illuminanceLib, line 103
        case 0x01 : // on/off // library marker kkossev.illuminanceLib, line 104
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 105
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.illuminanceLib, line 106
            } // library marker kkossev.illuminanceLib, line 107
            else { // library marker kkossev.illuminanceLib, line 108
                sendSwitchEvent(fncmd) // library marker kkossev.illuminanceLib, line 109
            } // library marker kkossev.illuminanceLib, line 110
            break // library marker kkossev.illuminanceLib, line 111
        case 0x02 : // library marker kkossev.illuminanceLib, line 112
            if (DEVICE_TYPE in  ['LightSensor']) { // library marker kkossev.illuminanceLib, line 113
                handleIlluminanceEvent(fncmd) // library marker kkossev.illuminanceLib, line 114
            } // library marker kkossev.illuminanceLib, line 115
            else { // library marker kkossev.illuminanceLib, line 116
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 117
            } // library marker kkossev.illuminanceLib, line 118
            break // library marker kkossev.illuminanceLib, line 119
        case 0x04 : // battery // library marker kkossev.illuminanceLib, line 120
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.illuminanceLib, line 121
            break // library marker kkossev.illuminanceLib, line 122
        default : // library marker kkossev.illuminanceLib, line 123
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.illuminanceLib, line 124
            break // library marker kkossev.illuminanceLib, line 125
    } // library marker kkossev.illuminanceLib, line 126
} // library marker kkossev.illuminanceLib, line 127

void illuminanceInitializeVars( boolean fullInit = false ) { // library marker kkossev.illuminanceLib, line 129
    logDebug "customInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.illuminanceLib, line 130
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 131
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // defined in commonLib // library marker kkossev.illuminanceLib, line 132
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.illuminanceLib, line 133
    } // library marker kkossev.illuminanceLib, line 134
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.illuminanceLib, line 135
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.illuminanceLib, line 136
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.illuminanceLib, line 137
    } // library marker kkossev.illuminanceLib, line 138
} // library marker kkossev.illuminanceLib, line 139

List<String> illuminanceRefresh() { // library marker kkossev.illuminanceLib, line 141
    List<String> cmds = [] // library marker kkossev.illuminanceLib, line 142
    cmds = zigbee.readAttribute(0x0400, 0x0000, [:], delay = 200) // illuminance // library marker kkossev.illuminanceLib, line 143
    return cmds // library marker kkossev.illuminanceLib, line 144
} // library marker kkossev.illuminanceLib, line 145

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter, UnnecessaryPublicModifier */ // library marker kkossev.xiaomiLib, line 1
library( // library marker kkossev.xiaomiLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Xiaomi Library', name: 'xiaomiLib', namespace: 'kkossev', importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', documentationLink: '', // library marker kkossev.xiaomiLib, line 3
    version: '3.3.0' // library marker kkossev.xiaomiLib, line 4
) // library marker kkossev.xiaomiLib, line 5
/* // library marker kkossev.xiaomiLib, line 6
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 7
 * // library marker kkossev.xiaomiLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 9
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 10
 * // library marker kkossev.xiaomiLib, line 11
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 12
 * // library marker kkossev.xiaomiLib, line 13
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 14
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 15
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 16
 * // library marker kkossev.xiaomiLib, line 17
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 18
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 19
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) Groovy linting; aqaraCube specific code; // library marker kkossev.xiaomiLib, line 20
 * ver. 1.1.0  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.0 alignmment // library marker kkossev.xiaomiLib, line 21
 * ver. 3.2.2  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.2 alignmment // library marker kkossev.xiaomiLib, line 22
 * ver. 3.3.0  2024-06-23 kkossev  - comonLib 3.3.0 alignmment; added parseXiaomiClusterSingeTag() method // library marker kkossev.xiaomiLib, line 23
 * // library marker kkossev.xiaomiLib, line 24
 *                                   TODO: remove the DEVICE_TYPE dependencies for Bulb, Thermostat, AqaraCube, FP1, TRV_OLD // library marker kkossev.xiaomiLib, line 25
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 26
*/ // library marker kkossev.xiaomiLib, line 27

static String xiaomiLibVersion()   { '3.3.0' } // library marker kkossev.xiaomiLib, line 29
static String xiaomiLibStamp() { '2024/06/23 9:36 AM' } // library marker kkossev.xiaomiLib, line 30

boolean isAqaraTVOC_Lib()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 32
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 33
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.xiaomiLib, line 34
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.xiaomiLib, line 35
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.xiaomiLib, line 36

// no metadata for this library! // library marker kkossev.xiaomiLib, line 38

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 40

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 42
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 43
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 44
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 45
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 46
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 47
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 48
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 49
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 50
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 52
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 53
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 54
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 55
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 56
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 57

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 59
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 60
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 61
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 62
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 63
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 64
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 65

// called from parseXiaomiCluster() in the main code, if no customParse is defined // library marker kkossev.xiaomiLib, line 67
// TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 68
// TODO - refactor for Thermostat and Bulb specific code // library marker kkossev.xiaomiLib, line 69
void standardParseXiaomiFCC0Cluster(final Map descMap) { // library marker kkossev.xiaomiLib, line 70
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 71
        logTrace "standardParseXiaomiFCC0Cluster: zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 72
    } // library marker kkossev.xiaomiLib, line 73
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 74
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 75
        return // library marker kkossev.xiaomiLib, line 76
    } // library marker kkossev.xiaomiLib, line 77
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 78
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 79
        return // library marker kkossev.xiaomiLib, line 80
    } // library marker kkossev.xiaomiLib, line 81
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 82
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 83
    final String funcName = 'standardParseXiaomiFCC0Cluster' // library marker kkossev.xiaomiLib, line 84
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 85
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 86
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "standardParseXiaomiFCC0Cluster: AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 87
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 90
            logWarn "${funcName}: unknown attribute - resetting?" // library marker kkossev.xiaomiLib, line 91
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
                log.debug "${funcName}: xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 106
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
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 130
            break // library marker kkossev.xiaomiLib, line 131
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 132
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 133
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 134
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
            log.warn "${funcName}: zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 155
            break // library marker kkossev.xiaomiLib, line 156
    } // library marker kkossev.xiaomiLib, line 157
} // library marker kkossev.xiaomiLib, line 158

// cluster 0xFCC0 attribute  0x00F7 is sent as a keep-alive beakon every 55 minutes // library marker kkossev.xiaomiLib, line 160
public void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 161
    final String funcName = 'parseXiaomiClusterTags' // library marker kkossev.xiaomiLib, line 162
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 163
        parseXiaomiClusterSingeTag(tag, value) // library marker kkossev.xiaomiLib, line 164
    } // library marker kkossev.xiaomiLib, line 165
} // library marker kkossev.xiaomiLib, line 166

public void parseXiaomiClusterSingeTag(final Integer tag, final Object value) { // library marker kkossev.xiaomiLib, line 168
    final String funcName = 'parseXiaomiClusterSingeTag' // library marker kkossev.xiaomiLib, line 169
    switch (tag) { // library marker kkossev.xiaomiLib, line 170
        case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 171
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 172
            break // library marker kkossev.xiaomiLib, line 173
        case 0x03: // library marker kkossev.xiaomiLib, line 174
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 175
            break // library marker kkossev.xiaomiLib, line 176
        case 0x05: // library marker kkossev.xiaomiLib, line 177
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 178
            break // library marker kkossev.xiaomiLib, line 179
        case 0x06: // library marker kkossev.xiaomiLib, line 180
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 181
            break // library marker kkossev.xiaomiLib, line 182
        case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 183
            final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 184
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 185
            device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 186
            break // library marker kkossev.xiaomiLib, line 187
        case 0x0a: // library marker kkossev.xiaomiLib, line 188
            String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 189
            if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 190
            String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 191
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 192
            if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 193
                logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 194
                state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 195
                state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 196
            } // library marker kkossev.xiaomiLib, line 197
            break // library marker kkossev.xiaomiLib, line 198
        case 0x0b: // library marker kkossev.xiaomiLib, line 199
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 200
            break // library marker kkossev.xiaomiLib, line 201
        case 0x64: // library marker kkossev.xiaomiLib, line 202
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 203
            // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 204
            break // library marker kkossev.xiaomiLib, line 205
        case 0x65: // library marker kkossev.xiaomiLib, line 206
            if (isAqaraFP1()) { logDebug "${funcName} PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 207
            else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 208
            break // library marker kkossev.xiaomiLib, line 209
        case 0x66: // library marker kkossev.xiaomiLib, line 210
            if (isAqaraFP1()) { logDebug "${funcName} SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
            else if (isAqaraTVOC_Lib()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 212
            else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 213
            break // library marker kkossev.xiaomiLib, line 214
        case 0x67: // library marker kkossev.xiaomiLib, line 215
            if (isAqaraFP1()) { logDebug "${funcName} DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 216
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 217
            // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 218
            break // library marker kkossev.xiaomiLib, line 219
        case 0x69: // library marker kkossev.xiaomiLib, line 220
            if (isAqaraFP1()) { logDebug "${funcName} TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 221
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 222
            break // library marker kkossev.xiaomiLib, line 223
        case 0x6a: // library marker kkossev.xiaomiLib, line 224
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 225
            else              { logDebug "${funcName} MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 226
            break // library marker kkossev.xiaomiLib, line 227
        case 0x6b: // library marker kkossev.xiaomiLib, line 228
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 229
            else              { logDebug "${funcName} MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 230
            break // library marker kkossev.xiaomiLib, line 231
        case 0x95: // library marker kkossev.xiaomiLib, line 232
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 233
            break // library marker kkossev.xiaomiLib, line 234
        case 0x96: // library marker kkossev.xiaomiLib, line 235
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 236
            break // library marker kkossev.xiaomiLib, line 237
        case 0x97: // library marker kkossev.xiaomiLib, line 238
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 239
            break // library marker kkossev.xiaomiLib, line 240
        case 0x98: // library marker kkossev.xiaomiLib, line 241
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 242
            break // library marker kkossev.xiaomiLib, line 243
        case 0x9b: // library marker kkossev.xiaomiLib, line 244
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 245
                logDebug "${funcName} Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 246
                sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 247
            } // library marker kkossev.xiaomiLib, line 248
            else { logDebug "${funcName} CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 249
            break // library marker kkossev.xiaomiLib, line 250
        default: // library marker kkossev.xiaomiLib, line 251
            logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 252
    } // library marker kkossev.xiaomiLib, line 253
} // library marker kkossev.xiaomiLib, line 254

/** // library marker kkossev.xiaomiLib, line 256
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 257
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 258
 */ // library marker kkossev.xiaomiLib, line 259
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 260
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 261
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 262
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 263
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 264
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 265
    } // library marker kkossev.xiaomiLib, line 266
    return bigInt // library marker kkossev.xiaomiLib, line 267
} // library marker kkossev.xiaomiLib, line 268

/** // library marker kkossev.xiaomiLib, line 270
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 271
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 272
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 273
 */ // library marker kkossev.xiaomiLib, line 274
private Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 275
    try { // library marker kkossev.xiaomiLib, line 276
        final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 277
        final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 278
        new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 279
            while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 280
                int tag = stream.read() // library marker kkossev.xiaomiLib, line 281
                int dataType = stream.read() // library marker kkossev.xiaomiLib, line 282
                Object value // library marker kkossev.xiaomiLib, line 283
                if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 284
                    int length = stream.read() // library marker kkossev.xiaomiLib, line 285
                    byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 286
                    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 287
                    value = new String(byteArr) // library marker kkossev.xiaomiLib, line 288
                } else { // library marker kkossev.xiaomiLib, line 289
                    int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 290
                    value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 291
                } // library marker kkossev.xiaomiLib, line 292
                results[tag] = value // library marker kkossev.xiaomiLib, line 293
            } // library marker kkossev.xiaomiLib, line 294
        } // library marker kkossev.xiaomiLib, line 295
        return results // library marker kkossev.xiaomiLib, line 296
    } // library marker kkossev.xiaomiLib, line 297
    catch (e) { // library marker kkossev.xiaomiLib, line 298
        if (settings.logEnable) { "${device.displayName} decodeXiaomiTags: ${e}" } // library marker kkossev.xiaomiLib, line 299
        return [:] // library marker kkossev.xiaomiLib, line 300
    } // library marker kkossev.xiaomiLib, line 301
} // library marker kkossev.xiaomiLib, line 302

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 304
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 305
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 306
    return cmds // library marker kkossev.xiaomiLib, line 307
} // library marker kkossev.xiaomiLib, line 308

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 310
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 311
    logDebug "configureXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 312
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 313
    return cmds // library marker kkossev.xiaomiLib, line 314
} // library marker kkossev.xiaomiLib, line 315

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 317
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 318
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 319
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 320
    return cmds // library marker kkossev.xiaomiLib, line 321
} // library marker kkossev.xiaomiLib, line 322

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 324
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 325
} // library marker kkossev.xiaomiLib, line 326

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 328
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 329
} // library marker kkossev.xiaomiLib, line 330

List<String> standardAqaraBlackMagic() { // library marker kkossev.xiaomiLib, line 332
    return [] // library marker kkossev.xiaomiLib, line 333
    ///////////////////////////////////////// // library marker kkossev.xiaomiLib, line 334
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 335
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.xiaomiLib, line 336
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.xiaomiLib, line 337
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 338
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 339
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.xiaomiLib, line 340
        if (isAqaraTVOC_OLD()) { // library marker kkossev.xiaomiLib, line 341
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.xiaomiLib, line 342
        } // library marker kkossev.xiaomiLib, line 343
        logDebug 'standardAqaraBlackMagic()' // library marker kkossev.xiaomiLib, line 344
    } // library marker kkossev.xiaomiLib, line 345
    return cmds // library marker kkossev.xiaomiLib, line 346
} // library marker kkossev.xiaomiLib, line 347

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~
