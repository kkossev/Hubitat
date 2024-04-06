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
 * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) comminLib 3.6
 *
 *                                   TODO:
 */

static String version() { "3.0.6" }
static String timeStamp() {"2024/04/06 10:53 PM"}

@Field static final Boolean _DEBUG = true







//#include kkossev.rgbLib
//#include kkossev.deviceProfileLib // can not get property 'UNKNOWN' on null object in librabry rgrbLib

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
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.0.6', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups code to dedicated lib; moved level methids to dedicated lib + setLevel bug fix; // library marker kkossev.commonLib, line 38
  * // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 40
  *                                   TODO: add custom* handlers for the new drivers! // library marker kkossev.commonLib, line 41
  *                                   TODO: remove the automatic capabilities selectionm for the new drivers! // library marker kkossev.commonLib, line 42
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 43
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 44
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 45
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 46
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 47
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 48
 * // library marker kkossev.commonLib, line 49
*/ // library marker kkossev.commonLib, line 50

String commonLibVersion() { '3.0.6' } // library marker kkossev.commonLib, line 52
String commonLibStamp() { '2024/04/06 10:44 PM' } // library marker kkossev.commonLib, line 53

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

        if (deviceType in  ['THSensor', 'MotionSensor']) { // library marker kkossev.commonLib, line 91
            capability 'Sensor' // library marker kkossev.commonLib, line 92
        } // library marker kkossev.commonLib, line 93
        if (deviceType in  ['MotionSensor']) { // library marker kkossev.commonLib, line 94
            capability 'MotionSensor' // library marker kkossev.commonLib, line 95
        } // library marker kkossev.commonLib, line 96
        if (deviceType in  ['Switch', 'Relay', 'Outlet', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 97
            capability 'Actuator' // library marker kkossev.commonLib, line 98
        } // library marker kkossev.commonLib, line 99
        if (deviceType in  ['THSensor', 'MotionSensor', 'Thermostat']) { // library marker kkossev.commonLib, line 100
            capability 'Battery' // library marker kkossev.commonLib, line 101
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 102
        } // library marker kkossev.commonLib, line 103
        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 104
            capability 'Switch' // library marker kkossev.commonLib, line 105
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 106
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 107
            } // library marker kkossev.commonLib, line 108
        } // library marker kkossev.commonLib, line 109
        if (deviceType in ['Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 110
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 111
        } // library marker kkossev.commonLib, line 112
        if (deviceType in  ['THSensor']) { // library marker kkossev.commonLib, line 113
            capability 'TemperatureMeasurement' // library marker kkossev.commonLib, line 114
        } // library marker kkossev.commonLib, line 115
        if (deviceType in  ['THSensor']) { // library marker kkossev.commonLib, line 116
            capability 'RelativeHumidityMeasurement' // library marker kkossev.commonLib, line 117
        } // library marker kkossev.commonLib, line 118

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 120
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 121

    preferences { // library marker kkossev.commonLib, line 123
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 124
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 125
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 126

        if (device) { // library marker kkossev.commonLib, line 128
            if ((device.hasCapability('TemperatureMeasurement') || device.hasCapability('RelativeHumidityMeasurement') /*|| device.hasCapability('IlluminanceMeasurement')*/)) { // library marker kkossev.commonLib, line 129
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 130
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.commonLib, line 131
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 132
                } // library marker kkossev.commonLib, line 133
            } // library marker kkossev.commonLib, line 134
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 135
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 136
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 137
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 138
                if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 139
                    input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 140
                } // library marker kkossev.commonLib, line 141
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 142
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 143
                } // library marker kkossev.commonLib, line 144
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 145
            } // library marker kkossev.commonLib, line 146
        } // library marker kkossev.commonLib, line 147
    } // library marker kkossev.commonLib, line 148
} // library marker kkossev.commonLib, line 149

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 151
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 152
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 153
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 154
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 155
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 156
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 157
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 158
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 159
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 160
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 161

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 163
    defaultValue: 1, // library marker kkossev.commonLib, line 164
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 165
] // library marker kkossev.commonLib, line 166
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 167
    defaultValue: 240, // library marker kkossev.commonLib, line 168
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 169
] // library marker kkossev.commonLib, line 170
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 171
    defaultValue: 0, // library marker kkossev.commonLib, line 172
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 173
] // library marker kkossev.commonLib, line 174

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 176
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 177
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 178
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 179
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 180
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 181
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 182
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 183
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 184
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 185
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 186
] // library marker kkossev.commonLib, line 187

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 189
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 190
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 191
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 192
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 193
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 194
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 195
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 196
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 197

/** // library marker kkossev.commonLib, line 199
 * Parse Zigbee message // library marker kkossev.commonLib, line 200
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 201
 */ // library marker kkossev.commonLib, line 202
void parse(final String description) { // library marker kkossev.commonLib, line 203
    checkDriverVersion() // library marker kkossev.commonLib, line 204
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 205
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 206
    setHealthStatusOnline() // library marker kkossev.commonLib, line 207

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 209
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 210
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 211
            parseIasMessage(description) // library marker kkossev.commonLib, line 212
        } // library marker kkossev.commonLib, line 213
        else { // library marker kkossev.commonLib, line 214
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 215
        } // library marker kkossev.commonLib, line 216
        return // library marker kkossev.commonLib, line 217
    } // library marker kkossev.commonLib, line 218
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 219
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 220
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 221
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 222
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 223
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 224
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 225
        return // library marker kkossev.commonLib, line 226
    } // library marker kkossev.commonLib, line 227
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 228
        return // library marker kkossev.commonLib, line 229
    } // library marker kkossev.commonLib, line 230
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 231

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 233
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 234
        return // library marker kkossev.commonLib, line 235
    } // library marker kkossev.commonLib, line 236
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 237
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 238
        return // library marker kkossev.commonLib, line 239
    } // library marker kkossev.commonLib, line 240
    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 241
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 242
    // // library marker kkossev.commonLib, line 243
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 244
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 245
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 246

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 248
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 249
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 250
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 251
            break // library marker kkossev.commonLib, line 252
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 253
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 254
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 255
            break // library marker kkossev.commonLib, line 256
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 257
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 258
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 259
            break // library marker kkossev.commonLib, line 260
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 261
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 262
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 263
            break // library marker kkossev.commonLib, line 264
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 265
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 266
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 267
            break // library marker kkossev.commonLib, line 268
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 269
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 270
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 271
            break // library marker kkossev.commonLib, line 272
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 273
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 274
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 275
            break // library marker kkossev.commonLib, line 276
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 277
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 278
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 279
            break // library marker kkossev.commonLib, line 280
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 281
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 282
            break // library marker kkossev.commonLib, line 283
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 284
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 285
            break // library marker kkossev.commonLib, line 286
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 287
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 288
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 289
            break // library marker kkossev.commonLib, line 290
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 291
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 292
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 293
            break // library marker kkossev.commonLib, line 294
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 295
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 296
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 297
            break // library marker kkossev.commonLib, line 298
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 299
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 300
            break // library marker kkossev.commonLib, line 301
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 302
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 303
            break // library marker kkossev.commonLib, line 304
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 305
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 306
            break // library marker kkossev.commonLib, line 307
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 308
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 309
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 310
            break // library marker kkossev.commonLib, line 311
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 312
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 313
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        case 0xE002 : // library marker kkossev.commonLib, line 316
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 317
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 318
            break // library marker kkossev.commonLib, line 319
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 320
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 321
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 322
            break // library marker kkossev.commonLib, line 323
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 324
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 325
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 328
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 329
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 330
            break // library marker kkossev.commonLib, line 331
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 332
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 333
            break // library marker kkossev.commonLib, line 334
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 335
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 336
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 337
            break // library marker kkossev.commonLib, line 338
        default: // library marker kkossev.commonLib, line 339
            if (settings.logEnable) { // library marker kkossev.commonLib, line 340
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 341
            } // library marker kkossev.commonLib, line 342
            break // library marker kkossev.commonLib, line 343
    } // library marker kkossev.commonLib, line 344
} // library marker kkossev.commonLib, line 345

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 347
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 348
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 349
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 350
    } // library marker kkossev.commonLib, line 351
    return false // library marker kkossev.commonLib, line 352
} // library marker kkossev.commonLib, line 353

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 355
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 356
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 357
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 358
    } // library marker kkossev.commonLib, line 359
    return false // library marker kkossev.commonLib, line 360
} // library marker kkossev.commonLib, line 361

/** // library marker kkossev.commonLib, line 363
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 364
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 365
 */ // library marker kkossev.commonLib, line 366
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 367
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 368
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 369
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 370
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 371
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 372
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 373
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 374
    } // library marker kkossev.commonLib, line 375
    else { // library marker kkossev.commonLib, line 376
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 377
    } // library marker kkossev.commonLib, line 378
} // library marker kkossev.commonLib, line 379

/** // library marker kkossev.commonLib, line 381
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 382
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 383
 */ // library marker kkossev.commonLib, line 384
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 385
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 386
    switch (commandId) { // library marker kkossev.commonLib, line 387
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 388
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 389
            break // library marker kkossev.commonLib, line 390
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 391
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 392
            break // library marker kkossev.commonLib, line 393
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 394
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 395
            break // library marker kkossev.commonLib, line 396
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 397
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 398
            break // library marker kkossev.commonLib, line 399
        case 0x0B: // default command response // library marker kkossev.commonLib, line 400
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 401
            break // library marker kkossev.commonLib, line 402
        default: // library marker kkossev.commonLib, line 403
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 404
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 405
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 406
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 407
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 408
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 409
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 410
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 411
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 412
            } // library marker kkossev.commonLib, line 413
            break // library marker kkossev.commonLib, line 414
    } // library marker kkossev.commonLib, line 415
} // library marker kkossev.commonLib, line 416

/** // library marker kkossev.commonLib, line 418
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 419
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 420
 */ // library marker kkossev.commonLib, line 421
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 422
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 423
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 424
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 425
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 426
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 427
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 428
    } // library marker kkossev.commonLib, line 429
    else { // library marker kkossev.commonLib, line 430
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 431
    } // library marker kkossev.commonLib, line 432
} // library marker kkossev.commonLib, line 433

/** // library marker kkossev.commonLib, line 435
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 436
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 437
 */ // library marker kkossev.commonLib, line 438
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 439
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 440
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 441
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 442
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 443
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 444
    } // library marker kkossev.commonLib, line 445
    else { // library marker kkossev.commonLib, line 446
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 447
    } // library marker kkossev.commonLib, line 448
} // library marker kkossev.commonLib, line 449

/** // library marker kkossev.commonLib, line 451
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 452
 */ // library marker kkossev.commonLib, line 453
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 454
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 455
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 456
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 457
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 458
        state.reportingEnabled = true // library marker kkossev.commonLib, line 459
    } // library marker kkossev.commonLib, line 460
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 461
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 462
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 463
    } else { // library marker kkossev.commonLib, line 464
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 465
    } // library marker kkossev.commonLib, line 466
} // library marker kkossev.commonLib, line 467

/** // library marker kkossev.commonLib, line 469
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 470
 */ // library marker kkossev.commonLib, line 471
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 472
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 473
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 474
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 475
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 476
    if (status == 0) { // library marker kkossev.commonLib, line 477
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 478
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 479
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 480
        int delta = 0 // library marker kkossev.commonLib, line 481
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 482
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 483
        } // library marker kkossev.commonLib, line 484
        else { // library marker kkossev.commonLib, line 485
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 486
        } // library marker kkossev.commonLib, line 487
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 488
    } // library marker kkossev.commonLib, line 489
    else { // library marker kkossev.commonLib, line 490
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 491
    } // library marker kkossev.commonLib, line 492
} // library marker kkossev.commonLib, line 493

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 495
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 496
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 497
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 498
        return false // library marker kkossev.commonLib, line 499
    } // library marker kkossev.commonLib, line 500
    // execute the customHandler function // library marker kkossev.commonLib, line 501
    boolean result = false // library marker kkossev.commonLib, line 502
    try { // library marker kkossev.commonLib, line 503
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 504
    } // library marker kkossev.commonLib, line 505
    catch (e) { // library marker kkossev.commonLib, line 506
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 507
        return false // library marker kkossev.commonLib, line 508
    } // library marker kkossev.commonLib, line 509
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 510
    return result // library marker kkossev.commonLib, line 511
} // library marker kkossev.commonLib, line 512

/** // library marker kkossev.commonLib, line 514
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 515
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 516
 */ // library marker kkossev.commonLib, line 517
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 518
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 519
    final String commandId = data[0] // library marker kkossev.commonLib, line 520
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 521
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 522
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 523
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 524
    } else { // library marker kkossev.commonLib, line 525
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 526
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 527
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 528
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 529
        } // library marker kkossev.commonLib, line 530
    } // library marker kkossev.commonLib, line 531
} // library marker kkossev.commonLib, line 532

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 534
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 535
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 536
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 537
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 538
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 539
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 540
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 541
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 542
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 543
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 544
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 545
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 546
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 547
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 548
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 549

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 551
    0x00: 'Success', // library marker kkossev.commonLib, line 552
    0x01: 'Failure', // library marker kkossev.commonLib, line 553
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 554
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 555
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 556
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 557
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 558
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 559
    0x88: 'Read Only', // library marker kkossev.commonLib, line 560
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 561
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 562
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 563
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 564
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 565
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 566
    0x94: 'Time out', // library marker kkossev.commonLib, line 567
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 568
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 569
] // library marker kkossev.commonLib, line 570

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 572
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 573
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 574
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 575
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 576
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 577
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 578
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 579
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 580
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 581
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 582
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 583
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 584
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 585
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 586
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 587
] // library marker kkossev.commonLib, line 588

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 590
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 591
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 592
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 593
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 594
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 595
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 596
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 597
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 598
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 599
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 600
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 601
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 602
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 603
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 604
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 605
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 606
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 607
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 608
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 609
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 610
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 611
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 612
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 613
] // library marker kkossev.commonLib, line 614

void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 616
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' } // library marker kkossev.commonLib, line 617
} // library marker kkossev.commonLib, line 618

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 620
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 621
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 622
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 623
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 624
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 625
    return avg // library marker kkossev.commonLib, line 626
} // library marker kkossev.commonLib, line 627

/* // library marker kkossev.commonLib, line 629
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 630
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 631
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 632
*/ // library marker kkossev.commonLib, line 633
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 634

/** // library marker kkossev.commonLib, line 636
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 637
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 638
 */ // library marker kkossev.commonLib, line 639
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 640
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 641
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 642
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 643
        case 0x0000: // library marker kkossev.commonLib, line 644
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 645
            break // library marker kkossev.commonLib, line 646
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 647
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 648
            if (isPing) { // library marker kkossev.commonLib, line 649
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 650
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 651
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 652
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 653
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 654
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 655
                    sendRttEvent() // library marker kkossev.commonLib, line 656
                } // library marker kkossev.commonLib, line 657
                else { // library marker kkossev.commonLib, line 658
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 659
                } // library marker kkossev.commonLib, line 660
                state.states['isPing'] = false // library marker kkossev.commonLib, line 661
            } // library marker kkossev.commonLib, line 662
            else { // library marker kkossev.commonLib, line 663
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 664
            } // library marker kkossev.commonLib, line 665
            break // library marker kkossev.commonLib, line 666
        case 0x0004: // library marker kkossev.commonLib, line 667
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 668
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 669
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 670
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 671
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 672
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 673
            } // library marker kkossev.commonLib, line 674
            break // library marker kkossev.commonLib, line 675
        case 0x0005: // library marker kkossev.commonLib, line 676
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 677
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 678
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 679
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 680
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 681
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 682
            } // library marker kkossev.commonLib, line 683
            break // library marker kkossev.commonLib, line 684
        case 0x0007: // library marker kkossev.commonLib, line 685
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 686
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 687
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 688
            break // library marker kkossev.commonLib, line 689
        case 0xFFDF: // library marker kkossev.commonLib, line 690
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 691
            break // library marker kkossev.commonLib, line 692
        case 0xFFE2: // library marker kkossev.commonLib, line 693
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 694
            break // library marker kkossev.commonLib, line 695
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 696
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 697
            break // library marker kkossev.commonLib, line 698
        case 0xFFFE: // library marker kkossev.commonLib, line 699
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 700
            break // library marker kkossev.commonLib, line 701
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 702
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 703
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 704
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 705
            break // library marker kkossev.commonLib, line 706
        default: // library marker kkossev.commonLib, line 707
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 708
            break // library marker kkossev.commonLib, line 709
    } // library marker kkossev.commonLib, line 710
} // library marker kkossev.commonLib, line 711

/* // library marker kkossev.commonLib, line 713
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 714
 * power cluster            0x0001 // library marker kkossev.commonLib, line 715
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 716
*/ // library marker kkossev.commonLib, line 717
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 718
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 719
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 720
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 721
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 722
    } // library marker kkossev.commonLib, line 723

    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 725
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 726
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 727
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 728
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 729
        } // library marker kkossev.commonLib, line 730
    } // library marker kkossev.commonLib, line 731
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 732
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 733
    } // library marker kkossev.commonLib, line 734
    else { // library marker kkossev.commonLib, line 735
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 736
    } // library marker kkossev.commonLib, line 737
} // library marker kkossev.commonLib, line 738

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.commonLib, line 740
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 741
    Map result = [:] // library marker kkossev.commonLib, line 742
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.commonLib, line 743
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.commonLib, line 744
        BigDecimal minVolts = 2.2 // library marker kkossev.commonLib, line 745
        BigDecimal maxVolts = 3.2 // library marker kkossev.commonLib, line 746
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 747
        int roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 748
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 749
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 750
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 751
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 752
            result.name = 'battery' // library marker kkossev.commonLib, line 753
            result.unit  = '%' // library marker kkossev.commonLib, line 754
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 755
        } // library marker kkossev.commonLib, line 756
        else { // library marker kkossev.commonLib, line 757
            result.value = volts // library marker kkossev.commonLib, line 758
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 759
            result.unit  = 'V' // library marker kkossev.commonLib, line 760
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 761
        } // library marker kkossev.commonLib, line 762
        result.type = 'physical' // library marker kkossev.commonLib, line 763
        result.isStateChange = true // library marker kkossev.commonLib, line 764
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 765
        sendEvent(result) // library marker kkossev.commonLib, line 766
    } // library marker kkossev.commonLib, line 767
    else { // library marker kkossev.commonLib, line 768
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 769
    } // library marker kkossev.commonLib, line 770
} // library marker kkossev.commonLib, line 771

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.commonLib, line 773
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 774
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 775
        return // library marker kkossev.commonLib, line 776
    } // library marker kkossev.commonLib, line 777
    Map map = [:] // library marker kkossev.commonLib, line 778
    map.name = 'battery' // library marker kkossev.commonLib, line 779
    map.timeStamp = now() // library marker kkossev.commonLib, line 780
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 781
    map.unit  = '%' // library marker kkossev.commonLib, line 782
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 783
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 784
    map.isStateChange = true // library marker kkossev.commonLib, line 785
    // // library marker kkossev.commonLib, line 786
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.commonLib, line 787
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 788
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 789
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 790
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 791
        // send it now! // library marker kkossev.commonLib, line 792
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 793
    } // library marker kkossev.commonLib, line 794
    else { // library marker kkossev.commonLib, line 795
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 796
        map.delayed = delayedTime // library marker kkossev.commonLib, line 797
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 798
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 799
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 800
    } // library marker kkossev.commonLib, line 801
} // library marker kkossev.commonLib, line 802

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 804
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 805
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 806
    sendEvent(map) // library marker kkossev.commonLib, line 807
} // library marker kkossev.commonLib, line 808

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 810
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 811
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 812
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 813
    sendEvent(map) // library marker kkossev.commonLib, line 814
} // library marker kkossev.commonLib, line 815

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 817
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' } // library marker kkossev.commonLib, line 818

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 820
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 821
} // library marker kkossev.commonLib, line 822

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 824
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 825
} // library marker kkossev.commonLib, line 826

/* // library marker kkossev.commonLib, line 828
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 829
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 830
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 831
*/ // library marker kkossev.commonLib, line 832

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 834
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 835
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 836
    } // library marker kkossev.commonLib, line 837
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 838
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 839
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 840
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 841
    } // library marker kkossev.commonLib, line 842
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 843
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 844
    } // library marker kkossev.commonLib, line 845
    else { // library marker kkossev.commonLib, line 846
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 847
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 848
    } // library marker kkossev.commonLib, line 849
} // library marker kkossev.commonLib, line 850

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 852
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 853
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 854

void toggle() { // library marker kkossev.commonLib, line 856
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 857
    String state = '' // library marker kkossev.commonLib, line 858
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 859
        state = 'on' // library marker kkossev.commonLib, line 860
    } // library marker kkossev.commonLib, line 861
    else { // library marker kkossev.commonLib, line 862
        state = 'off' // library marker kkossev.commonLib, line 863
    } // library marker kkossev.commonLib, line 864
    descriptionText += state // library marker kkossev.commonLib, line 865
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 866
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 867
} // library marker kkossev.commonLib, line 868

void off() { // library marker kkossev.commonLib, line 870
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 871
        customOff() // library marker kkossev.commonLib, line 872
        return // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 875
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 876
        return // library marker kkossev.commonLib, line 877
    } // library marker kkossev.commonLib, line 878
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 879
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 880
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 881
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 882
        if (currentState == 'off') { // library marker kkossev.commonLib, line 883
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 884
        } // library marker kkossev.commonLib, line 885
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 886
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 887
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 888
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 889
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 890
    } // library marker kkossev.commonLib, line 891
    /* // library marker kkossev.commonLib, line 892
    else { // library marker kkossev.commonLib, line 893
        if (currentState != 'off') { // library marker kkossev.commonLib, line 894
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 895
        } // library marker kkossev.commonLib, line 896
        else { // library marker kkossev.commonLib, line 897
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 898
            return // library marker kkossev.commonLib, line 899
        } // library marker kkossev.commonLib, line 900
    } // library marker kkossev.commonLib, line 901
    */ // library marker kkossev.commonLib, line 902

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 904
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 905
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 906
} // library marker kkossev.commonLib, line 907

void on() { // library marker kkossev.commonLib, line 909
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 910
        customOn() // library marker kkossev.commonLib, line 911
        return // library marker kkossev.commonLib, line 912
    } // library marker kkossev.commonLib, line 913
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 914
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 915
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 916
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 917
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 918
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 919
        } // library marker kkossev.commonLib, line 920
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 921
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 922
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 923
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 924
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 925
    } // library marker kkossev.commonLib, line 926
    /* // library marker kkossev.commonLib, line 927
    else { // library marker kkossev.commonLib, line 928
        if (currentState != 'on') { // library marker kkossev.commonLib, line 929
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 930
        } // library marker kkossev.commonLib, line 931
        else { // library marker kkossev.commonLib, line 932
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 933
            return // library marker kkossev.commonLib, line 934
        } // library marker kkossev.commonLib, line 935
    } // library marker kkossev.commonLib, line 936
    */ // library marker kkossev.commonLib, line 937
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 938
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 939
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 940
} // library marker kkossev.commonLib, line 941

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 943
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 944
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 945
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 946
    } // library marker kkossev.commonLib, line 947
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 948
    Map map = [:] // library marker kkossev.commonLib, line 949
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 950
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 951
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 952
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 953
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 954
        return // library marker kkossev.commonLib, line 955
    } // library marker kkossev.commonLib, line 956
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 957
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 958
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 959
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 960
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 961
        state.states['debounce'] = true // library marker kkossev.commonLib, line 962
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 963
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 964
    } else { // library marker kkossev.commonLib, line 965
        state.states['debounce'] = true // library marker kkossev.commonLib, line 966
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 967
    } // library marker kkossev.commonLib, line 968
    map.name = 'switch' // library marker kkossev.commonLib, line 969
    map.value = value // library marker kkossev.commonLib, line 970
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 971
    if (isRefresh) { // library marker kkossev.commonLib, line 972
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 973
        map.isStateChange = true // library marker kkossev.commonLib, line 974
    } else { // library marker kkossev.commonLib, line 975
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 976
    } // library marker kkossev.commonLib, line 977
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 978
    sendEvent(map) // library marker kkossev.commonLib, line 979
    clearIsDigital() // library marker kkossev.commonLib, line 980
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 981
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 982
    } // library marker kkossev.commonLib, line 983
} // library marker kkossev.commonLib, line 984

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 986
    '0': 'switch off', // library marker kkossev.commonLib, line 987
    '1': 'switch on', // library marker kkossev.commonLib, line 988
    '2': 'switch last state' // library marker kkossev.commonLib, line 989
] // library marker kkossev.commonLib, line 990

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 992
    '0': 'toggle', // library marker kkossev.commonLib, line 993
    '1': 'state', // library marker kkossev.commonLib, line 994
    '2': 'momentary' // library marker kkossev.commonLib, line 995
] // library marker kkossev.commonLib, line 996

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 998
    Map descMap = [:] // library marker kkossev.commonLib, line 999
    try { // library marker kkossev.commonLib, line 1000
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1001
    } // library marker kkossev.commonLib, line 1002
    catch (e1) { // library marker kkossev.commonLib, line 1003
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1004
        // try alternative custom parsing // library marker kkossev.commonLib, line 1005
        descMap = [:] // library marker kkossev.commonLib, line 1006
        try { // library marker kkossev.commonLib, line 1007
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1008
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1009
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1010
            } // library marker kkossev.commonLib, line 1011
        } // library marker kkossev.commonLib, line 1012
        catch (e2) { // library marker kkossev.commonLib, line 1013
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1014
            return [:] // library marker kkossev.commonLib, line 1015
        } // library marker kkossev.commonLib, line 1016
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1017
    } // library marker kkossev.commonLib, line 1018
    return descMap // library marker kkossev.commonLib, line 1019
} // library marker kkossev.commonLib, line 1020

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1022
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1023
        return false // library marker kkossev.commonLib, line 1024
    } // library marker kkossev.commonLib, line 1025
    // try to parse ... // library marker kkossev.commonLib, line 1026
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1027
    Map descMap = [:] // library marker kkossev.commonLib, line 1028
    try { // library marker kkossev.commonLib, line 1029
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1030
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1031
    } // library marker kkossev.commonLib, line 1032
    catch (e) { // library marker kkossev.commonLib, line 1033
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1034
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1035
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1036
        return true // library marker kkossev.commonLib, line 1037
    } // library marker kkossev.commonLib, line 1038

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1040
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1041
    } // library marker kkossev.commonLib, line 1042
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1043
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1044
    } // library marker kkossev.commonLib, line 1045
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1046
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1047
    } // library marker kkossev.commonLib, line 1048
    else { // library marker kkossev.commonLib, line 1049
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1050
        return false // library marker kkossev.commonLib, line 1051
    } // library marker kkossev.commonLib, line 1052
    return true    // processed // library marker kkossev.commonLib, line 1053
} // library marker kkossev.commonLib, line 1054

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1056
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 1057
  /* // library marker kkossev.commonLib, line 1058
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1059
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1060
        return true // library marker kkossev.commonLib, line 1061
    } // library marker kkossev.commonLib, line 1062
*/ // library marker kkossev.commonLib, line 1063
    Map descMap = [:] // library marker kkossev.commonLib, line 1064
    try { // library marker kkossev.commonLib, line 1065
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1066
    } // library marker kkossev.commonLib, line 1067
    catch (e1) { // library marker kkossev.commonLib, line 1068
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1069
        // try alternative custom parsing // library marker kkossev.commonLib, line 1070
        descMap = [:] // library marker kkossev.commonLib, line 1071
        try { // library marker kkossev.commonLib, line 1072
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1073
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1074
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1075
            } // library marker kkossev.commonLib, line 1076
        } // library marker kkossev.commonLib, line 1077
        catch (e2) { // library marker kkossev.commonLib, line 1078
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1079
            return true // library marker kkossev.commonLib, line 1080
        } // library marker kkossev.commonLib, line 1081
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1082
    } // library marker kkossev.commonLib, line 1083
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1084
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1085
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1086
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1087
        return false // library marker kkossev.commonLib, line 1088
    } // library marker kkossev.commonLib, line 1089
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1090
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1091
    // attribute report received // library marker kkossev.commonLib, line 1092
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1093
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1094
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1095
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1096
    } // library marker kkossev.commonLib, line 1097
    attrData.each { // library marker kkossev.commonLib, line 1098
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1099
        //def map = [:] // library marker kkossev.commonLib, line 1100
        if (it.status == '86') { // library marker kkossev.commonLib, line 1101
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1102
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1103
        } // library marker kkossev.commonLib, line 1104
        switch (it.cluster) { // library marker kkossev.commonLib, line 1105
            case '0000' : // library marker kkossev.commonLib, line 1106
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1107
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1108
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1109
                } // library marker kkossev.commonLib, line 1110
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1111
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1112
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1113
                } // library marker kkossev.commonLib, line 1114
                else { // library marker kkossev.commonLib, line 1115
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1116
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1117
                } // library marker kkossev.commonLib, line 1118
                break // library marker kkossev.commonLib, line 1119
            default : // library marker kkossev.commonLib, line 1120
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1121
                break // library marker kkossev.commonLib, line 1122
        } // switch // library marker kkossev.commonLib, line 1123
    } // for each attribute // library marker kkossev.commonLib, line 1124
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1125
} // library marker kkossev.commonLib, line 1126

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1128

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1130
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1131
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1132
    def mode // library marker kkossev.commonLib, line 1133
    String attrName // library marker kkossev.commonLib, line 1134
    if (it.value == null) { // library marker kkossev.commonLib, line 1135
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1136
        return // library marker kkossev.commonLib, line 1137
    } // library marker kkossev.commonLib, line 1138
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1139
    switch (it.attrId) { // library marker kkossev.commonLib, line 1140
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1141
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1142
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1143
            break // library marker kkossev.commonLib, line 1144
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1145
            attrName = 'On Time' // library marker kkossev.commonLib, line 1146
            mode = value // library marker kkossev.commonLib, line 1147
            break // library marker kkossev.commonLib, line 1148
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1149
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1150
            mode = value // library marker kkossev.commonLib, line 1151
            break // library marker kkossev.commonLib, line 1152
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1153
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1154
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1155
            break // library marker kkossev.commonLib, line 1156
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1157
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1158
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1159
            break // library marker kkossev.commonLib, line 1160
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1161
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1162
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1163
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1164
            } // library marker kkossev.commonLib, line 1165
            else { // library marker kkossev.commonLib, line 1166
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1167
            } // library marker kkossev.commonLib, line 1168
            break // library marker kkossev.commonLib, line 1169
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1170
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1171
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1172
            break // library marker kkossev.commonLib, line 1173
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1174
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1175
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1176
            break // library marker kkossev.commonLib, line 1177
        default : // library marker kkossev.commonLib, line 1178
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1179
            return // library marker kkossev.commonLib, line 1180
    } // library marker kkossev.commonLib, line 1181
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1182
} // library marker kkossev.commonLib, line 1183

void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1185
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1186
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1187
    } // library marker kkossev.commonLib, line 1188
    else if (this.respondsTo('levelLibParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1189
        levelLibParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1190
    } // library marker kkossev.commonLib, line 1191
    else { // library marker kkossev.commonLib, line 1192
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1193
    } // library marker kkossev.commonLib, line 1194
} // library marker kkossev.commonLib, line 1195

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1197
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1198
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1199
} // library marker kkossev.commonLib, line 1200

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1202
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1203
} // library marker kkossev.commonLib, line 1204

void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1206
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1207
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1208
    } // library marker kkossev.commonLib, line 1209
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1210
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1211
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1212
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1213
    } // library marker kkossev.commonLib, line 1214
    else { // library marker kkossev.commonLib, line 1215
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1216
    } // library marker kkossev.commonLib, line 1217
} // library marker kkossev.commonLib, line 1218

void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1220
    if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 1221
} // library marker kkossev.commonLib, line 1222

/* // library marker kkossev.commonLib, line 1224
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1225
 * temperature // library marker kkossev.commonLib, line 1226
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1227
*/ // library marker kkossev.commonLib, line 1228
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1229
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1230
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1231
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1232
} // library marker kkossev.commonLib, line 1233

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1235
    Map eventMap = [:] // library marker kkossev.commonLib, line 1236
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1237
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1238
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1239
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1240
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1241
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1242
    } // library marker kkossev.commonLib, line 1243
    else { // library marker kkossev.commonLib, line 1244
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1245
    } // library marker kkossev.commonLib, line 1246
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1247
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1248
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1249
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.commonLib, line 1250
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.commonLib, line 1251
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1252
        return // library marker kkossev.commonLib, line 1253
    } // library marker kkossev.commonLib, line 1254
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1255
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1256
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1257
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1258
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1259
    } // library marker kkossev.commonLib, line 1260
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1261
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1262
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1263
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1264
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1265
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1266
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1267
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1268
    } // library marker kkossev.commonLib, line 1269
    else {         // queue the event // library marker kkossev.commonLib, line 1270
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1271
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1272
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1273
    } // library marker kkossev.commonLib, line 1274
} // library marker kkossev.commonLib, line 1275

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1277
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1278
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1279
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1280
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1281
} // library marker kkossev.commonLib, line 1282

/* // library marker kkossev.commonLib, line 1284
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1285
 * humidity // library marker kkossev.commonLib, line 1286
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1287
*/ // library marker kkossev.commonLib, line 1288
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1289
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1290
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1291
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1292
} // library marker kkossev.commonLib, line 1293

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1295
    Map eventMap = [:] // library marker kkossev.commonLib, line 1296
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.commonLib, line 1297
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1298
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1299
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.commonLib, line 1300
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.commonLib, line 1301
        return // library marker kkossev.commonLib, line 1302
    } // library marker kkossev.commonLib, line 1303
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1304
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1305
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1306
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1307
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1308
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1309
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1310
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1311
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1312
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1313
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1314
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1315
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1316
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1317
    } // library marker kkossev.commonLib, line 1318
    else { // library marker kkossev.commonLib, line 1319
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1320
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1321
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1322
    } // library marker kkossev.commonLib, line 1323
} // library marker kkossev.commonLib, line 1324

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1326
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1327
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1328
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1329
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1330
} // library marker kkossev.commonLib, line 1331

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1333
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1334
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1338
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1339
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1340
} // library marker kkossev.commonLib, line 1341

// pm2.5 // library marker kkossev.commonLib, line 1343
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1344
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1345
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1346
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1347
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1348
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1349
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1350
    } // library marker kkossev.commonLib, line 1351
    else { // library marker kkossev.commonLib, line 1352
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1353
    } // library marker kkossev.commonLib, line 1354
} // library marker kkossev.commonLib, line 1355

// Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1357
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1358
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1359
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1360
    } // library marker kkossev.commonLib, line 1361
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1362
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1363
    } // library marker kkossev.commonLib, line 1364
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1365
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1366
    } // library marker kkossev.commonLib, line 1367
    else { // library marker kkossev.commonLib, line 1368
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1369
    } // library marker kkossev.commonLib, line 1370
} // library marker kkossev.commonLib, line 1371

// Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1373
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1374
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1375
} // library marker kkossev.commonLib, line 1376

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1378
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1379
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1380
} // library marker kkossev.commonLib, line 1381

// thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1383
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1384
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1385
} // library marker kkossev.commonLib, line 1386

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1388
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1389
} // library marker kkossev.commonLib, line 1390

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1392
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars // library marker kkossev.commonLib, line 1393
} // library marker kkossev.commonLib, line 1394

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1396
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars // library marker kkossev.commonLib, line 1397
} // library marker kkossev.commonLib, line 1398

/* // library marker kkossev.commonLib, line 1400
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1401
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1402
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1403
*/ // library marker kkossev.commonLib, line 1404
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1405
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1406
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1407

// Tuya Commands // library marker kkossev.commonLib, line 1409
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1410
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1411
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1412
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1413
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1414

// tuya DP type // library marker kkossev.commonLib, line 1416
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1417
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1418
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1419
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1420
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1421
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1422

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1424
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1425
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 1426
        Long offset = 0 // library marker kkossev.commonLib, line 1427
        try { // library marker kkossev.commonLib, line 1428
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1429
        } // library marker kkossev.commonLib, line 1430
        catch (e) { // library marker kkossev.commonLib, line 1431
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 1432
        } // library marker kkossev.commonLib, line 1433
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1434
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 1435
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 1436
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 1437
    } // library marker kkossev.commonLib, line 1438
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1439
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1440
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1441
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1442
        if (status != '00') { // library marker kkossev.commonLib, line 1443
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1444
        } // library marker kkossev.commonLib, line 1445
    } // library marker kkossev.commonLib, line 1446
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1447
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1448
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1449
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1450
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1451
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1452
            return // library marker kkossev.commonLib, line 1453
        } // library marker kkossev.commonLib, line 1454
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1455
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1456
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1457
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1458
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1459
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1460
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1461
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1462
        } // library marker kkossev.commonLib, line 1463
    } // library marker kkossev.commonLib, line 1464
    else { // library marker kkossev.commonLib, line 1465
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 1466
    } // library marker kkossev.commonLib, line 1467
} // library marker kkossev.commonLib, line 1468

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1470
    log.trace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1471
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1472
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1473
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1474
            return // library marker kkossev.commonLib, line 1475
        } // library marker kkossev.commonLib, line 1476
    } // library marker kkossev.commonLib, line 1477
    // check if the method  method exists // library marker kkossev.commonLib, line 1478
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1479
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1480
            return // library marker kkossev.commonLib, line 1481
        } // library marker kkossev.commonLib, line 1482
    } // library marker kkossev.commonLib, line 1483
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1484
} // library marker kkossev.commonLib, line 1485

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1487
    int retValue = 0 // library marker kkossev.commonLib, line 1488
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1489
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1490
        int power = 1 // library marker kkossev.commonLib, line 1491
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 1492
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 1493
            power = power * 256 // library marker kkossev.commonLib, line 1494
        } // library marker kkossev.commonLib, line 1495
    } // library marker kkossev.commonLib, line 1496
    return retValue // library marker kkossev.commonLib, line 1497
} // library marker kkossev.commonLib, line 1498

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 1500
    List<String> cmds = [] // library marker kkossev.commonLib, line 1501
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 1502
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1503
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 1504
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 1505
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 1506
    return cmds // library marker kkossev.commonLib, line 1507
} // library marker kkossev.commonLib, line 1508

private getPACKET_ID() { // library marker kkossev.commonLib, line 1510
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 1511
} // library marker kkossev.commonLib, line 1512

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1514
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 1515
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 1516
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 1517
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 1518
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 1519
} // library marker kkossev.commonLib, line 1520

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 1522
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 1523

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 1525
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 1526
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1527
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 1528
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 1529
} // library marker kkossev.commonLib, line 1530

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 1532
    List<String> cmds = [] // library marker kkossev.commonLib, line 1533
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1534
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 1535
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1536
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1537
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 1538
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 1539
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 1540
        } // library marker kkossev.commonLib, line 1541
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1542
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 1543
    } // library marker kkossev.commonLib, line 1544
    else { // library marker kkossev.commonLib, line 1545
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 1546
    } // library marker kkossev.commonLib, line 1547
} // library marker kkossev.commonLib, line 1548

/** // library marker kkossev.commonLib, line 1550
 * initializes the device // library marker kkossev.commonLib, line 1551
 * Invoked from configure() // library marker kkossev.commonLib, line 1552
 * @return zigbee commands // library marker kkossev.commonLib, line 1553
 */ // library marker kkossev.commonLib, line 1554
List<String> initializeDevice() { // library marker kkossev.commonLib, line 1555
    List<String> cmds = [] // library marker kkossev.commonLib, line 1556
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 1557

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 1559
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 1560
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 1561
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1562
    } // library marker kkossev.commonLib, line 1563
    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 1564
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1565
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 1566
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 1567
    } // library marker kkossev.commonLib, line 1568
    // // library marker kkossev.commonLib, line 1569
    return cmds // library marker kkossev.commonLib, line 1570
} // library marker kkossev.commonLib, line 1571

/** // library marker kkossev.commonLib, line 1573
 * configures the device // library marker kkossev.commonLib, line 1574
 * Invoked from configure() // library marker kkossev.commonLib, line 1575
 * @return zigbee commands // library marker kkossev.commonLib, line 1576
 */ // library marker kkossev.commonLib, line 1577
List<String> configureDevice() { // library marker kkossev.commonLib, line 1578
    List<String> cmds = [] // library marker kkossev.commonLib, line 1579
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 1580

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 1582
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 1583
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1584
    } // library marker kkossev.commonLib, line 1585
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 1586
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 1587
    return cmds // library marker kkossev.commonLib, line 1588
} // library marker kkossev.commonLib, line 1589

/* // library marker kkossev.commonLib, line 1591
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1592
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 1593
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1594
*/ // library marker kkossev.commonLib, line 1595

void refresh() { // library marker kkossev.commonLib, line 1597
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1598
    checkDriverVersion() // library marker kkossev.commonLib, line 1599
    List<String> cmds = [] // library marker kkossev.commonLib, line 1600
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1601

    // device type specific refresh handlers // library marker kkossev.commonLib, line 1603
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 1604
        List<String> customCmds = customRefresh() // library marker kkossev.commonLib, line 1605
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1606
    } // library marker kkossev.commonLib, line 1607
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 1608
    else { // library marker kkossev.commonLib, line 1609
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 1610
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 1611
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 1612
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 1613
        } // library marker kkossev.commonLib, line 1614
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 1615
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1616
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1617
        } // library marker kkossev.commonLib, line 1618
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1619
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1620
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1621
        } // library marker kkossev.commonLib, line 1622
    } // library marker kkossev.commonLib, line 1623

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1625
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1626
    } // library marker kkossev.commonLib, line 1627
    else { // library marker kkossev.commonLib, line 1628
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1629
    } // library marker kkossev.commonLib, line 1630
} // library marker kkossev.commonLib, line 1631

void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 1633
void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1634

void clearInfoEvent() { // library marker kkossev.commonLib, line 1636
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 1637
} // library marker kkossev.commonLib, line 1638

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1640
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1641
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1642
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 1643
    } // library marker kkossev.commonLib, line 1644
    else { // library marker kkossev.commonLib, line 1645
        logInfo "${info}" // library marker kkossev.commonLib, line 1646
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 1647
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1648
    } // library marker kkossev.commonLib, line 1649
} // library marker kkossev.commonLib, line 1650

void ping() { // library marker kkossev.commonLib, line 1652
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1653
    state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1654
    //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 1655
    state.states['isPing'] = true // library marker kkossev.commonLib, line 1656
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1657
    if (isVirtual()) { // library marker kkossev.commonLib, line 1658
        runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 1659
    } // library marker kkossev.commonLib, line 1660
    else { // library marker kkossev.commonLib, line 1661
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 1662
    } // library marker kkossev.commonLib, line 1663
    logDebug 'ping...' // library marker kkossev.commonLib, line 1664
} // library marker kkossev.commonLib, line 1665

def virtualPong() { // library marker kkossev.commonLib, line 1667
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1668
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1669
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1670
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1671
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1672
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1673
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1674
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1675
        sendRttEvent() // library marker kkossev.commonLib, line 1676
    } // library marker kkossev.commonLib, line 1677
    else { // library marker kkossev.commonLib, line 1678
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1679
    } // library marker kkossev.commonLib, line 1680
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1681
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1682
} // library marker kkossev.commonLib, line 1683

/** // library marker kkossev.commonLib, line 1685
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 1686
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 1687
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 1688
 * @return none // library marker kkossev.commonLib, line 1689
 */ // library marker kkossev.commonLib, line 1690
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1691
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1692
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1693
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1694
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1695
    if (value == null) { // library marker kkossev.commonLib, line 1696
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1697
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1698
    } // library marker kkossev.commonLib, line 1699
    else { // library marker kkossev.commonLib, line 1700
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1701
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1702
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1703
    } // library marker kkossev.commonLib, line 1704
} // library marker kkossev.commonLib, line 1705

/** // library marker kkossev.commonLib, line 1707
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 1708
 * @param cluster cluster ID // library marker kkossev.commonLib, line 1709
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 1710
 */ // library marker kkossev.commonLib, line 1711
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1712
    if (cluster != null) { // library marker kkossev.commonLib, line 1713
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1714
    } // library marker kkossev.commonLib, line 1715
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1716
    return 'NULL' // library marker kkossev.commonLib, line 1717
} // library marker kkossev.commonLib, line 1718

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1720
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1721
} // library marker kkossev.commonLib, line 1722

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1724
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1725
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1726
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1727
} // library marker kkossev.commonLib, line 1728

/** // library marker kkossev.commonLib, line 1730
 * Schedule a device health check // library marker kkossev.commonLib, line 1731
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 1732
 */ // library marker kkossev.commonLib, line 1733
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1734
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1735
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1736
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1737
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1738
    } // library marker kkossev.commonLib, line 1739
    else { // library marker kkossev.commonLib, line 1740
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1741
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1742
    } // library marker kkossev.commonLib, line 1743
} // library marker kkossev.commonLib, line 1744

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1746
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1747
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1748
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1749
} // library marker kkossev.commonLib, line 1750

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1752
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 1753
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1754
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1755
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1756
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1757
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1758
    } // library marker kkossev.commonLib, line 1759
} // library marker kkossev.commonLib, line 1760

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1762
    checkDriverVersion() // library marker kkossev.commonLib, line 1763
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1764
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1765
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1766
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1767
            logWarn 'not present!' // library marker kkossev.commonLib, line 1768
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1769
        } // library marker kkossev.commonLib, line 1770
    } // library marker kkossev.commonLib, line 1771
    else { // library marker kkossev.commonLib, line 1772
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1773
    } // library marker kkossev.commonLib, line 1774
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1775
} // library marker kkossev.commonLib, line 1776

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1778
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1779
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1780
    if (value == 'online') { // library marker kkossev.commonLib, line 1781
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1782
    } // library marker kkossev.commonLib, line 1783
    else { // library marker kkossev.commonLib, line 1784
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1785
    } // library marker kkossev.commonLib, line 1786
} // library marker kkossev.commonLib, line 1787

/** // library marker kkossev.commonLib, line 1789
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 1790
 */ // library marker kkossev.commonLib, line 1791
void autoPoll() { // library marker kkossev.commonLib, line 1792
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 1793
    checkDriverVersion() // library marker kkossev.commonLib, line 1794
    List<String> cmds = [] // library marker kkossev.commonLib, line 1795
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 1796
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 1797
    } // library marker kkossev.commonLib, line 1798

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1800
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1801
    } // library marker kkossev.commonLib, line 1802
} // library marker kkossev.commonLib, line 1803

/** // library marker kkossev.commonLib, line 1805
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1806
 */ // library marker kkossev.commonLib, line 1807
void updated() { // library marker kkossev.commonLib, line 1808
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1809
    checkDriverVersion() // library marker kkossev.commonLib, line 1810
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1811
    unschedule() // library marker kkossev.commonLib, line 1812

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1814
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1815
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1816
    } // library marker kkossev.commonLib, line 1817
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1818
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1819
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1820
    } // library marker kkossev.commonLib, line 1821

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1823
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1824
        // schedule the periodic timer // library marker kkossev.commonLib, line 1825
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1826
        if (interval > 0) { // library marker kkossev.commonLib, line 1827
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1828
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1829
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1830
        } // library marker kkossev.commonLib, line 1831
    } // library marker kkossev.commonLib, line 1832
    else { // library marker kkossev.commonLib, line 1833
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1834
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1835
    } // library marker kkossev.commonLib, line 1836
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1837
        customUpdated() // library marker kkossev.commonLib, line 1838
    } // library marker kkossev.commonLib, line 1839

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1841
} // library marker kkossev.commonLib, line 1842

/** // library marker kkossev.commonLib, line 1844
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 1845
 */ // library marker kkossev.commonLib, line 1846
void logsOff() { // library marker kkossev.commonLib, line 1847
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1848
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1849
} // library marker kkossev.commonLib, line 1850
void traceOff() { // library marker kkossev.commonLib, line 1851
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1852
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1853
} // library marker kkossev.commonLib, line 1854

void configure(String command) { // library marker kkossev.commonLib, line 1856
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1857
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1858
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1859
        return // library marker kkossev.commonLib, line 1860
    } // library marker kkossev.commonLib, line 1861
    // // library marker kkossev.commonLib, line 1862
    String func // library marker kkossev.commonLib, line 1863
    try { // library marker kkossev.commonLib, line 1864
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1865
        "$func"() // library marker kkossev.commonLib, line 1866
    } // library marker kkossev.commonLib, line 1867
    catch (e) { // library marker kkossev.commonLib, line 1868
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1869
        return // library marker kkossev.commonLib, line 1870
    } // library marker kkossev.commonLib, line 1871
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1872
} // library marker kkossev.commonLib, line 1873

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1875
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1876
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1877
} // library marker kkossev.commonLib, line 1878

void loadAllDefaults() { // library marker kkossev.commonLib, line 1880
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1881
    deleteAllSettings() // library marker kkossev.commonLib, line 1882
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1883
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1884
    deleteAllStates() // library marker kkossev.commonLib, line 1885
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1886
    initialize() // library marker kkossev.commonLib, line 1887
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1888
    updated() // library marker kkossev.commonLib, line 1889
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1890
} // library marker kkossev.commonLib, line 1891

void configureNow() { // library marker kkossev.commonLib, line 1893
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 1894
} // library marker kkossev.commonLib, line 1895

/** // library marker kkossev.commonLib, line 1897
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1898
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1899
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1900
 */ // library marker kkossev.commonLib, line 1901
List<String> configure() { // library marker kkossev.commonLib, line 1902
    List<String> cmds = [] // library marker kkossev.commonLib, line 1903
    logInfo 'configure...' // library marker kkossev.commonLib, line 1904
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1905
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1906
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1907
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1908
    } // library marker kkossev.commonLib, line 1909
    cmds += initializeDevice() // library marker kkossev.commonLib, line 1910
    cmds += configureDevice() // library marker kkossev.commonLib, line 1911
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1912
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1913
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 1914
    //return cmds // library marker kkossev.commonLib, line 1915
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1916
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1917
    } // library marker kkossev.commonLib, line 1918
    else { // library marker kkossev.commonLib, line 1919
        logDebug "no configure() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1920
    } // library marker kkossev.commonLib, line 1921
} // library marker kkossev.commonLib, line 1922

/** // library marker kkossev.commonLib, line 1924
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 1925
 */ // library marker kkossev.commonLib, line 1926
void installed() { // library marker kkossev.commonLib, line 1927
    logInfo 'installed...' // library marker kkossev.commonLib, line 1928
    // populate some default values for attributes // library marker kkossev.commonLib, line 1929
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1930
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1931
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1932
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1933
} // library marker kkossev.commonLib, line 1934

/** // library marker kkossev.commonLib, line 1936
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1937
 */ // library marker kkossev.commonLib, line 1938
void initialize() { // library marker kkossev.commonLib, line 1939
    logInfo 'initialize...' // library marker kkossev.commonLib, line 1940
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1941
    updateTuyaVersion() // library marker kkossev.commonLib, line 1942
    updateAqaraVersion() // library marker kkossev.commonLib, line 1943
} // library marker kkossev.commonLib, line 1944

/* // library marker kkossev.commonLib, line 1946
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1947
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1948
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1949
*/ // library marker kkossev.commonLib, line 1950

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1952
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1953
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1954
} // library marker kkossev.commonLib, line 1955

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1957
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1958
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1959
} // library marker kkossev.commonLib, line 1960

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1962
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1963
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1964
} // library marker kkossev.commonLib, line 1965

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1967
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 1968
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 1969
        return // library marker kkossev.commonLib, line 1970
    } // library marker kkossev.commonLib, line 1971
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 1972
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1973
    cmd.each { // library marker kkossev.commonLib, line 1974
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1975
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1976
    } // library marker kkossev.commonLib, line 1977
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1978
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1979
} // library marker kkossev.commonLib, line 1980

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1982

String getDeviceInfo() { // library marker kkossev.commonLib, line 1984
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1985
} // library marker kkossev.commonLib, line 1986

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1988
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1989
} // library marker kkossev.commonLib, line 1990

void checkDriverVersion() { // library marker kkossev.commonLib, line 1992
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1993
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1994
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1995
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1996
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 1997
        updateTuyaVersion() // library marker kkossev.commonLib, line 1998
        updateAqaraVersion() // library marker kkossev.commonLib, line 1999
    } // library marker kkossev.commonLib, line 2000
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2001
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2002
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2003
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 2004
} // library marker kkossev.commonLib, line 2005

// credits @thebearmay // library marker kkossev.commonLib, line 2007
String getModel() { // library marker kkossev.commonLib, line 2008
    try { // library marker kkossev.commonLib, line 2009
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2010
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2011
    } catch (ignore) { // library marker kkossev.commonLib, line 2012
        try { // library marker kkossev.commonLib, line 2013
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2014
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2015
                return model // library marker kkossev.commonLib, line 2016
            } // library marker kkossev.commonLib, line 2017
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2018
            return '' // library marker kkossev.commonLib, line 2019
        } // library marker kkossev.commonLib, line 2020
    } // library marker kkossev.commonLib, line 2021
} // library marker kkossev.commonLib, line 2022

// credits @thebearmay // library marker kkossev.commonLib, line 2024
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2025
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2026
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2027
    String revision = tokens.last() // library marker kkossev.commonLib, line 2028
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2029
} // library marker kkossev.commonLib, line 2030

/** // library marker kkossev.commonLib, line 2032
 * called from TODO // library marker kkossev.commonLib, line 2033
 */ // library marker kkossev.commonLib, line 2034

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2036
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2037
    unschedule() // library marker kkossev.commonLib, line 2038
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2039
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2040

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2042
} // library marker kkossev.commonLib, line 2043

void resetStatistics() { // library marker kkossev.commonLib, line 2045
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2046
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2047
} // library marker kkossev.commonLib, line 2048

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2050
void resetStats() { // library marker kkossev.commonLib, line 2051
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2052
    state.stats = [:] // library marker kkossev.commonLib, line 2053
    state.states = [:] // library marker kkossev.commonLib, line 2054
    state.lastRx = [:] // library marker kkossev.commonLib, line 2055
    state.lastTx = [:] // library marker kkossev.commonLib, line 2056
    state.health = [:] // library marker kkossev.commonLib, line 2057
    if (this.respondsTo('groupsLibVersion')) { // library marker kkossev.commonLib, line 2058
        state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2059
    } // library marker kkossev.commonLib, line 2060
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2061
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2062
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2063
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2064
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2065
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2066
} // library marker kkossev.commonLib, line 2067

/** // library marker kkossev.commonLib, line 2069
 * called from TODO // library marker kkossev.commonLib, line 2070
 */ // library marker kkossev.commonLib, line 2071
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2072
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2073
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2074
        state.clear() // library marker kkossev.commonLib, line 2075
        unschedule() // library marker kkossev.commonLib, line 2076
        resetStats() // library marker kkossev.commonLib, line 2077
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2078
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2079
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2080
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2081
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2082
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2083
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2084
    } // library marker kkossev.commonLib, line 2085

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2087
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2088
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2089
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2090
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2091

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2093
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2094
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2095
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2096
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2097
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2098
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2099
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2100
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2101
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 2102

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2104
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2105
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2106
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2107

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2109
    if ( mm != null) { // library marker kkossev.commonLib, line 2110
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2111
    } // library marker kkossev.commonLib, line 2112
    else { // library marker kkossev.commonLib, line 2113
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2114
    } // library marker kkossev.commonLib, line 2115
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2116
    if ( ep  != null) { // library marker kkossev.commonLib, line 2117
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2118
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2119
    } // library marker kkossev.commonLib, line 2120
    else { // library marker kkossev.commonLib, line 2121
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2122
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2123
    } // library marker kkossev.commonLib, line 2124
} // library marker kkossev.commonLib, line 2125

/** // library marker kkossev.commonLib, line 2127
 * called from TODO // library marker kkossev.commonLib, line 2128
 */ // library marker kkossev.commonLib, line 2129
void setDestinationEP() { // library marker kkossev.commonLib, line 2130
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2131
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2132
        state.destinationEP = ep // library marker kkossev.commonLib, line 2133
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2134
    } // library marker kkossev.commonLib, line 2135
    else { // library marker kkossev.commonLib, line 2136
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2137
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2138
    } // library marker kkossev.commonLib, line 2139
} // library marker kkossev.commonLib, line 2140

void logDebug(final String msg) { // library marker kkossev.commonLib, line 2142
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2143
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2144
    } // library marker kkossev.commonLib, line 2145
} // library marker kkossev.commonLib, line 2146

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2148
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2149
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2150
    } // library marker kkossev.commonLib, line 2151
} // library marker kkossev.commonLib, line 2152

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2154
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2155
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2156
    } // library marker kkossev.commonLib, line 2157
} // library marker kkossev.commonLib, line 2158

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2160
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2161
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2162
    } // library marker kkossev.commonLib, line 2163
} // library marker kkossev.commonLib, line 2164

// _DEBUG mode only // library marker kkossev.commonLib, line 2166
void getAllProperties() { // library marker kkossev.commonLib, line 2167
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2168
    device.properties.each { it -> // library marker kkossev.commonLib, line 2169
        log.debug it // library marker kkossev.commonLib, line 2170
    } // library marker kkossev.commonLib, line 2171
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2172
    settings.each { it -> // library marker kkossev.commonLib, line 2173
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2174
    } // library marker kkossev.commonLib, line 2175
    log.trace 'Done' // library marker kkossev.commonLib, line 2176
} // library marker kkossev.commonLib, line 2177

// delete all Preferences // library marker kkossev.commonLib, line 2179
void deleteAllSettings() { // library marker kkossev.commonLib, line 2180
    settings.each { it -> // library marker kkossev.commonLib, line 2181
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2182
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2183
    } // library marker kkossev.commonLib, line 2184
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2185
} // library marker kkossev.commonLib, line 2186

// delete all attributes // library marker kkossev.commonLib, line 2188
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2189
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2190
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2191
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2192
    } // library marker kkossev.commonLib, line 2193
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2194
} // library marker kkossev.commonLib, line 2195

// delete all State Variables // library marker kkossev.commonLib, line 2197
void deleteAllStates() { // library marker kkossev.commonLib, line 2198
    state.each { it -> // library marker kkossev.commonLib, line 2199
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2200
    } // library marker kkossev.commonLib, line 2201
    state.clear() // library marker kkossev.commonLib, line 2202
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2203
} // library marker kkossev.commonLib, line 2204

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2206
    unschedule() // library marker kkossev.commonLib, line 2207
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2208
} // library marker kkossev.commonLib, line 2209

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2211
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2212
} // library marker kkossev.commonLib, line 2213

void parseTest(String par) { // library marker kkossev.commonLib, line 2215
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2216
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2217
    parse(par) // library marker kkossev.commonLib, line 2218
} // library marker kkossev.commonLib, line 2219

def testJob() { // library marker kkossev.commonLib, line 2221
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2222
} // library marker kkossev.commonLib, line 2223

/** // library marker kkossev.commonLib, line 2225
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2226
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2227
 */ // library marker kkossev.commonLib, line 2228
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2229
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2230
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2231
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2232
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2233
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2234
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2235
    String cron // library marker kkossev.commonLib, line 2236
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2237
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2238
    } // library marker kkossev.commonLib, line 2239
    else { // library marker kkossev.commonLib, line 2240
        if (minutes < 60) { // library marker kkossev.commonLib, line 2241
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2242
        } // library marker kkossev.commonLib, line 2243
        else { // library marker kkossev.commonLib, line 2244
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2245
        } // library marker kkossev.commonLib, line 2246
    } // library marker kkossev.commonLib, line 2247
    return cron // library marker kkossev.commonLib, line 2248
} // library marker kkossev.commonLib, line 2249

// credits @thebearmay // library marker kkossev.commonLib, line 2251
String formatUptime() { // library marker kkossev.commonLib, line 2252
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2253
} // library marker kkossev.commonLib, line 2254

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2256
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2257
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2258
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2259
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2260
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2261
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2262
} // library marker kkossev.commonLib, line 2263

boolean isTuya() { // library marker kkossev.commonLib, line 2265
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2266
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2267
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2268
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2269
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2270
} // library marker kkossev.commonLib, line 2271

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2273
    if (!isTuya()) { // library marker kkossev.commonLib, line 2274
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2275
        return // library marker kkossev.commonLib, line 2276
    } // library marker kkossev.commonLib, line 2277
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2278
    if (application != null) { // library marker kkossev.commonLib, line 2279
        Integer ver // library marker kkossev.commonLib, line 2280
        try { // library marker kkossev.commonLib, line 2281
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2282
        } // library marker kkossev.commonLib, line 2283
        catch (e) { // library marker kkossev.commonLib, line 2284
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2285
            return // library marker kkossev.commonLib, line 2286
        } // library marker kkossev.commonLib, line 2287
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2288
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2289
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2290
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2291
        } // library marker kkossev.commonLib, line 2292
    } // library marker kkossev.commonLib, line 2293
} // library marker kkossev.commonLib, line 2294

boolean isAqara() { // library marker kkossev.commonLib, line 2296
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2297
} // library marker kkossev.commonLib, line 2298

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2300
    if (!isAqara()) { // library marker kkossev.commonLib, line 2301
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2302
        return // library marker kkossev.commonLib, line 2303
    } // library marker kkossev.commonLib, line 2304
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2305
    if (application != null) { // library marker kkossev.commonLib, line 2306
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2307
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2308
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2309
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2310
        } // library marker kkossev.commonLib, line 2311
    } // library marker kkossev.commonLib, line 2312
} // library marker kkossev.commonLib, line 2313

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2315
    try { // library marker kkossev.commonLib, line 2316
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2317
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2318
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2319
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2320
    } catch (e) { // library marker kkossev.commonLib, line 2321
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2322
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2323
    } // library marker kkossev.commonLib, line 2324
} // library marker kkossev.commonLib, line 2325

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2327
    try { // library marker kkossev.commonLib, line 2328
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2329
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2330
        return date.getTime() // library marker kkossev.commonLib, line 2331
    } catch (e) { // library marker kkossev.commonLib, line 2332
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2333
        return now() // library marker kkossev.commonLib, line 2334
    } // library marker kkossev.commonLib, line 2335
} // library marker kkossev.commonLib, line 2336

void test(String par) { // library marker kkossev.commonLib, line 2338
    List<String> cmds = [] // library marker kkossev.commonLib, line 2339
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2340

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2342
    //parse(par) // library marker kkossev.commonLib, line 2343

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2345
} // library marker kkossev.commonLib, line 2346

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

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
    version: '3.0.0', // library marker kkossev.groupsLib, line 10
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
 * // library marker kkossev.groupsLib, line 26
 *                                   TODO: // library marker kkossev.groupsLib, line 27
*/ // library marker kkossev.groupsLib, line 28

static String groupsLibVersion()   { '3.0.0' } // library marker kkossev.groupsLib, line 30
static String groupsLibStamp() { '2024/04/06 3:56 PM' } // library marker kkossev.groupsLib, line 31

metadata { // library marker kkossev.groupsLib, line 33
    // no capabilities // library marker kkossev.groupsLib, line 34
    // no attributes // library marker kkossev.groupsLib, line 35
    command 'zigbeeGroups', [ // library marker kkossev.groupsLib, line 36
        [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.groupsLib, line 37
        [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.groupsLib, line 38
    ] // library marker kkossev.groupsLib, line 39

    preferences { // library marker kkossev.groupsLib, line 41
        // no prefrences // library marker kkossev.groupsLib, line 42
    } // library marker kkossev.groupsLib, line 43
} // library marker kkossev.groupsLib, line 44

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.groupsLib, line 46
    defaultValue: 0, // library marker kkossev.groupsLib, line 47
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.groupsLib, line 48
] // library marker kkossev.groupsLib, line 49
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.groupsLib, line 50
    defaultValue: 0, // library marker kkossev.groupsLib, line 51
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.groupsLib, line 52
] // library marker kkossev.groupsLib, line 53

/* // library marker kkossev.groupsLib, line 55
 * ----------------------------------------------------------------------------- // library marker kkossev.groupsLib, line 56
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.groupsLib, line 57
 * ----------------------------------------------------------------------------- // library marker kkossev.groupsLib, line 58
*/ // library marker kkossev.groupsLib, line 59
void customParseGroupsCluster(final Map descMap) { // library marker kkossev.groupsLib, line 60
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.groupsLib, line 61
    logDebug "customParseGroupsCluster: customParseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.groupsLib, line 62
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 63
    switch (descMap.command as Integer) { // library marker kkossev.groupsLib, line 64
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.groupsLib, line 65
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 66
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 67
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 68
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 69
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 70
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 71
                logWarn "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.groupsLib, line 72
            } // library marker kkossev.groupsLib, line 73
            else { // library marker kkossev.groupsLib, line 74
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.groupsLib, line 75
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.groupsLib, line 76
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.groupsLib, line 77
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.groupsLib, line 78
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.groupsLib, line 79
                        logDebug "customParseGroupsCluster: Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.groupsLib, line 80
                        return // library marker kkossev.groupsLib, line 81
                    } // library marker kkossev.groupsLib, line 82
                } // library marker kkossev.groupsLib, line 83
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.groupsLib, line 84
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.groupsLib, line 85
                state.zigbeeGroups['groups'].sort() // library marker kkossev.groupsLib, line 86
            } // library marker kkossev.groupsLib, line 87
            break // library marker kkossev.groupsLib, line 88
        case 0x01: // View group // library marker kkossev.groupsLib, line 89
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.groupsLib, line 90
            logDebug "customParseGroupsCluster: received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 91
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 92
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 93
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 94
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 95
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 96
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 97
                logWarn "customParseGroupsCluster: zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.groupsLib, line 98
            } // library marker kkossev.groupsLib, line 99
            else { // library marker kkossev.groupsLib, line 100
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.groupsLib, line 101
            } // library marker kkossev.groupsLib, line 102
            break // library marker kkossev.groupsLib, line 103
        case 0x02: // Get group membership // library marker kkossev.groupsLib, line 104
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 105
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 106
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.groupsLib, line 107
            final Set<String> groups = [] // library marker kkossev.groupsLib, line 108
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.groupsLib, line 109
                int pos = (i * 2) + 2 // library marker kkossev.groupsLib, line 110
                String group = data[pos + 1] + data[pos] // library marker kkossev.groupsLib, line 111
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.groupsLib, line 112
            } // library marker kkossev.groupsLib, line 113
            state.zigbeeGroups['groups'] = groups // library marker kkossev.groupsLib, line 114
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.groupsLib, line 115
            logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.groupsLib, line 116
            break // library marker kkossev.groupsLib, line 117
        case 0x03: // Remove group // library marker kkossev.groupsLib, line 118
            logInfo "customParseGroupsCluster: received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 119
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 120
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 121
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 122
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 123
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 124
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 125
                logWarn "customParseGroupsCluster: zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.groupsLib, line 126
            } // library marker kkossev.groupsLib, line 127
            else { // library marker kkossev.groupsLib, line 128
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.groupsLib, line 129
            } // library marker kkossev.groupsLib, line 130
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.groupsLib, line 131
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.groupsLib, line 132
            if (index >= 0) { // library marker kkossev.groupsLib, line 133
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.groupsLib, line 134
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.groupsLib, line 135
            } // library marker kkossev.groupsLib, line 136
            break // library marker kkossev.groupsLib, line 137
        case 0x04: //Remove all groups // library marker kkossev.groupsLib, line 138
            logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.groupsLib, line 139
            logWarn 'customParseGroupsCluster: not implemented!' // library marker kkossev.groupsLib, line 140
            break // library marker kkossev.groupsLib, line 141
        case 0x05: // Add group if identifying // library marker kkossev.groupsLib, line 142
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.groupsLib, line 143
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.groupsLib, line 144
            logWarn 'customParseGroupsCluster: not implemented!' // library marker kkossev.groupsLib, line 145
            break // library marker kkossev.groupsLib, line 146
        default: // library marker kkossev.groupsLib, line 147
            logWarn "customParseGroupsCluster: received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 148
            break // library marker kkossev.groupsLib, line 149
    } // library marker kkossev.groupsLib, line 150
} // library marker kkossev.groupsLib, line 151

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 153
List<String> addGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 154
    List<String> cmds = [] // library marker kkossev.groupsLib, line 155
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 156
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.groupsLib, line 157
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.groupsLib, line 158
        return [] // library marker kkossev.groupsLib, line 159
    } // library marker kkossev.groupsLib, line 160
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 161
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 162
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 163
    return cmds // library marker kkossev.groupsLib, line 164
} // library marker kkossev.groupsLib, line 165

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 167
List<String> viewGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 168
    List<String> cmds = [] // library marker kkossev.groupsLib, line 169
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 170
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 171
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 172
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 173
    return cmds // library marker kkossev.groupsLib, line 174
} // library marker kkossev.groupsLib, line 175

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 177
List<String> getGroupMembership(dummy) { // library marker kkossev.groupsLib, line 178
    List<String> cmds = [] // library marker kkossev.groupsLib, line 179
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.groupsLib, line 180
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 181
    return cmds // library marker kkossev.groupsLib, line 182
} // library marker kkossev.groupsLib, line 183

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 185
List<String> removeGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 186
    List<String> cmds = [] // library marker kkossev.groupsLib, line 187
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 188
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.groupsLib, line 189
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.groupsLib, line 190
        return [] // library marker kkossev.groupsLib, line 191
    } // library marker kkossev.groupsLib, line 192
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 193
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 194
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 195
    return cmds // library marker kkossev.groupsLib, line 196
} // library marker kkossev.groupsLib, line 197

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 199
List<String> removeAllGroups(groupNr) { // library marker kkossev.groupsLib, line 200
    List<String> cmds = [] // library marker kkossev.groupsLib, line 201
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 202
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 203
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 204
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 205
    return cmds // library marker kkossev.groupsLib, line 206
} // library marker kkossev.groupsLib, line 207

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 209
List<String> notImplementedGroups(groupNr) { // library marker kkossev.groupsLib, line 210
    List<String> cmds = [] // library marker kkossev.groupsLib, line 211
    //final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 212
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 213
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 214
    return cmds // library marker kkossev.groupsLib, line 215
} // library marker kkossev.groupsLib, line 216

@Field static final Map GroupCommandsMap = [ // library marker kkossev.groupsLib, line 218
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.groupsLib, line 219
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.groupsLib, line 220
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.groupsLib, line 221
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.groupsLib, line 222
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.groupsLib, line 223
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.groupsLib, line 224
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.groupsLib, line 225
] // library marker kkossev.groupsLib, line 226

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 228
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.groupsLib, line 229
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.groupsLib, line 230
    List<String> cmds = [] // library marker kkossev.groupsLib, line 231
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 232
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.groupsLib, line 233
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.groupsLib, line 234
    def value // library marker kkossev.groupsLib, line 235
    Boolean validated = false // library marker kkossev.groupsLib, line 236
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.groupsLib, line 237
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.groupsLib, line 238
        return // library marker kkossev.groupsLib, line 239
    } // library marker kkossev.groupsLib, line 240
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.groupsLib, line 241
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.groupsLib, line 242
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.groupsLib, line 243
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.groupsLib, line 244
        return // library marker kkossev.groupsLib, line 245
    } // library marker kkossev.groupsLib, line 246
    // // library marker kkossev.groupsLib, line 247
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.groupsLib, line 248
    def func // library marker kkossev.groupsLib, line 249
    try { // library marker kkossev.groupsLib, line 250
        func = GroupCommandsMap[command]?.function // library marker kkossev.groupsLib, line 251
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.groupsLib, line 252
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.groupsLib, line 253
        cmds = "$func"(value) // library marker kkossev.groupsLib, line 254
    } // library marker kkossev.groupsLib, line 255
    catch (e) { // library marker kkossev.groupsLib, line 256
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.groupsLib, line 257
        return // library marker kkossev.groupsLib, line 258
    } // library marker kkossev.groupsLib, line 259

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.groupsLib, line 261
    sendZigbeeCommands(cmds) // library marker kkossev.groupsLib, line 262
} // library marker kkossev.groupsLib, line 263

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 265
void groupCommandsHelp(val) { // library marker kkossev.groupsLib, line 266
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.groupsLib, line 267
} // library marker kkossev.groupsLib, line 268

List<String> customRefresh() { // library marker kkossev.groupsLib, line 270
    logDebug 'customRefresh()' // library marker kkossev.groupsLib, line 271
    return getGroupMembership(null) // library marker kkossev.groupsLib, line 272
} // library marker kkossev.groupsLib, line 273

void customInitializeVars( boolean fullInit = false ) { // library marker kkossev.groupsLib, line 275
    logDebug "customInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.groupsLib, line 276
    if (fullInit || state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 277
} // library marker kkossev.groupsLib, line 278

// ~~~~~ end include (169) kkossev.groupsLib ~~~~~

// ~~~~~ start include (167) kkossev.buttonLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.buttonLib, line 1
library( // library marker kkossev.buttonLib, line 2
    base: 'driver', // library marker kkossev.buttonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.buttonLib, line 4
    category: 'zigbee', // library marker kkossev.buttonLib, line 5
    description: 'Zigbee Button Library', // library marker kkossev.buttonLib, line 6
    name: 'buttonLib', // library marker kkossev.buttonLib, line 7
    namespace: 'kkossev', // library marker kkossev.buttonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/buttonLib.groovy', // library marker kkossev.buttonLib, line 9
    version: '3.0.0', // library marker kkossev.buttonLib, line 10
    documentationLink: '' // library marker kkossev.buttonLib, line 11
) // library marker kkossev.buttonLib, line 12
/* // library marker kkossev.buttonLib, line 13
 *  Zigbee Button Library // library marker kkossev.buttonLib, line 14
 * // library marker kkossev.buttonLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.buttonLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.buttonLib, line 17
 * // library marker kkossev.buttonLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.buttonLib, line 19
 * // library marker kkossev.buttonLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.buttonLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.buttonLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.buttonLib, line 23
 * // library marker kkossev.buttonLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.buttonLib, line 25
 * // library marker kkossev.buttonLib, line 26
 *                                   TODO: // library marker kkossev.buttonLib, line 27
*/ // library marker kkossev.buttonLib, line 28

static String buttonLibVersion()   { '3.0.0' } // library marker kkossev.buttonLib, line 30
static String buttonLibStamp() { '2024/04/06 1:02 PM' } // library marker kkossev.buttonLib, line 31

/* // library marker kkossev.buttonLib, line 33
metadata { // library marker kkossev.buttonLib, line 34
    // no capabilities // library marker kkossev.buttonLib, line 35
    // no attributes // library marker kkossev.buttonLib, line 36
    // no commands // library marker kkossev.buttonLib, line 37
    preferences { // library marker kkossev.buttonLib, line 38
        // no prefrences // library marker kkossev.buttonLib, line 39
    } // library marker kkossev.buttonLib, line 40
} // library marker kkossev.buttonLib, line 41
*/ // library marker kkossev.buttonLib, line 42

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

void push(BigDecimal buttonNumber) {    //pushableButton capability // library marker kkossev.buttonLib, line 63
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 64
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 65
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 66
} // library marker kkossev.buttonLib, line 67

void doubleTap(BigDecimal buttonNumber) { // library marker kkossev.buttonLib, line 69
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.buttonLib, line 70
} // library marker kkossev.buttonLib, line 71

void hold(BigDecimal buttonNumber) { // library marker kkossev.buttonLib, line 73
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.buttonLib, line 74
} // library marker kkossev.buttonLib, line 75

void release(BigDecimal buttonNumber) { // library marker kkossev.buttonLib, line 77
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true) // library marker kkossev.buttonLib, line 78
} // library marker kkossev.buttonLib, line 79

void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.buttonLib, line 81
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 82
} // library marker kkossev.buttonLib, line 83

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.buttonLib, line 85
void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.buttonLib, line 86
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 87
} // library marker kkossev.buttonLib, line 88


// ~~~~~ end include (167) kkossev.buttonLib ~~~~~

// ~~~~~ start include (166) kkossev.energyLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.energyLib, line 1
library( // library marker kkossev.energyLib, line 2
    base: 'driver', // library marker kkossev.energyLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.energyLib, line 4
    category: 'zigbee', // library marker kkossev.energyLib, line 5
    description: 'Zigbee Energy Library', // library marker kkossev.energyLib, line 6
    name: 'energyLib', // library marker kkossev.energyLib, line 7
    namespace: 'kkossev', // library marker kkossev.energyLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/energyLib.groovy', // library marker kkossev.energyLib, line 9
    version: '3.0.0', // library marker kkossev.energyLib, line 10
    documentationLink: '' // library marker kkossev.energyLib, line 11
) // library marker kkossev.energyLib, line 12
/* // library marker kkossev.energyLib, line 13
 *  Zigbee Energy Library // library marker kkossev.energyLib, line 14
 * // library marker kkossev.energyLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.energyLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.energyLib, line 17
 * // library marker kkossev.energyLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.energyLib, line 19
 * // library marker kkossev.energyLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.energyLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.energyLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.energyLib, line 23
 * // library marker kkossev.energyLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.energyLib, line 25
 * // library marker kkossev.energyLib, line 26
 *                                   TODO: // library marker kkossev.energyLib, line 27
*/ // library marker kkossev.energyLib, line 28

static String energyLibVersion()   { '3.0.0' } // library marker kkossev.energyLib, line 30
static String energyLibStamp() { '2024/04/06 10:48 AM' } // library marker kkossev.energyLib, line 31

//import groovy.json.* // library marker kkossev.energyLib, line 33
//import groovy.transform.Field // library marker kkossev.energyLib, line 34
//import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.energyLib, line 35
//import hubitat.zigbee.zcl.DataType // library marker kkossev.energyLib, line 36
//import java.util.concurrent.ConcurrentHashMap // library marker kkossev.energyLib, line 37

//import groovy.transform.CompileStatic // library marker kkossev.energyLib, line 39

metadata { // library marker kkossev.energyLib, line 41
    // no capabilities // library marker kkossev.energyLib, line 42
    // no attributes // library marker kkossev.energyLib, line 43
    // no commands // library marker kkossev.energyLib, line 44
    preferences { // library marker kkossev.energyLib, line 45
        // no prefrences // library marker kkossev.energyLib, line 46
    } // library marker kkossev.energyLib, line 47
} // library marker kkossev.energyLib, line 48

void sendVoltageEvent(BigDecimal voltage, boolean isDigital=false) { // library marker kkossev.energyLib, line 50
    Map map = [:] // library marker kkossev.energyLib, line 51
    map.name = 'voltage' // library marker kkossev.energyLib, line 52
    map.value = voltage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 53
    map.unit = 'V' // library marker kkossev.energyLib, line 54
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 55
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 56
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 57
    final BigDecimal lastVoltage = device.currentValue('voltage') ?: 0.0 // library marker kkossev.energyLib, line 58
    final BigDecimal  voltageThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 59
    if (Math.abs(voltage - lastVoltage) >= voltageThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 60
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 61
        sendEvent(map) // library marker kkossev.energyLib, line 62
    } // library marker kkossev.energyLib, line 63
    else { // library marker kkossev.energyLib, line 64
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastVoltage} is less than ${voltageThreshold} V)" // library marker kkossev.energyLib, line 65
    } // library marker kkossev.energyLib, line 66
} // library marker kkossev.energyLib, line 67

void sendAmperageEvent(BigDecimal amperage, boolean isDigital=false) { // library marker kkossev.energyLib, line 69
    Map map = [:] // library marker kkossev.energyLib, line 70
    map.name = 'amperage' // library marker kkossev.energyLib, line 71
    map.value = amperage.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 72
    map.unit = 'A' // library marker kkossev.energyLib, line 73
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 74
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 75
    if (state.states.isRefresh  == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 76
    final BigDecimal lastAmperage = device.currentValue('amperage') ?: 0.0 // library marker kkossev.energyLib, line 77
    final BigDecimal amperageThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 78
    if (Math.abs(amperage - lastAmperage ) >= amperageThreshold || state.states.isRefresh  == true) { // library marker kkossev.energyLib, line 79
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 80
        sendEvent(map) // library marker kkossev.energyLib, line 81
    } // library marker kkossev.energyLib, line 82
    else { // library marker kkossev.energyLib, line 83
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastAmperage} is less than ${amperageThreshold} mA)" // library marker kkossev.energyLib, line 84
    } // library marker kkossev.energyLib, line 85
} // library marker kkossev.energyLib, line 86

void sendPowerEvent(BigDecimal power, boolean isDigital=false) { // library marker kkossev.energyLib, line 88
    Map map = [:] // library marker kkossev.energyLib, line 89
    map.name = 'power' // library marker kkossev.energyLib, line 90
    map.value = power.setScale(DEFAULT_PRECISION, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 91
    map.unit = 'W' // library marker kkossev.energyLib, line 92
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 93
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 94
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 95
    final BigDecimal lastPower = device.currentValue('power') ?: 0.0 // library marker kkossev.energyLib, line 96
    final BigDecimal powerThreshold = DEFAULT_DELTA // library marker kkossev.energyLib, line 97
    if (power  > MAX_POWER_LIMIT) { // library marker kkossev.energyLib, line 98
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (exceeds maximum power cap ${MAX_POWER_LIMIT} W)" // library marker kkossev.energyLib, line 99
        return // library marker kkossev.energyLib, line 100
    } // library marker kkossev.energyLib, line 101
    if (Math.abs(power - lastPower ) >= powerThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 102
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 103
        sendEvent(map) // library marker kkossev.energyLib, line 104
    } // library marker kkossev.energyLib, line 105
    else { // library marker kkossev.energyLib, line 106
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastPower} is less than ${powerThreshold} W)" // library marker kkossev.energyLib, line 107
    } // library marker kkossev.energyLib, line 108
} // library marker kkossev.energyLib, line 109

void sendFrequencyEvent(BigDecimal frequency, boolean isDigital=false) { // library marker kkossev.energyLib, line 111
    Map map = [:] // library marker kkossev.energyLib, line 112
    map.name = 'frequency' // library marker kkossev.energyLib, line 113
    map.value = frequency.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 114
    map.unit = 'Hz' // library marker kkossev.energyLib, line 115
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 116
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 117
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 118
    final BigDecimal lastFrequency = device.currentValue('frequency') ?: 0.0 // library marker kkossev.energyLib, line 119
    final BigDecimal frequencyThreshold = 0.1 // library marker kkossev.energyLib, line 120
    if (Math.abs(frequency - lastFrequency) >= frequencyThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 121
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 122
        sendEvent(map) // library marker kkossev.energyLib, line 123
    } // library marker kkossev.energyLib, line 124
    else { // library marker kkossev.energyLib, line 125
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${frequencyThreshold} Hz)" // library marker kkossev.energyLib, line 126
    } // library marker kkossev.energyLib, line 127
} // library marker kkossev.energyLib, line 128

void sendPowerFactorEvent(BigDecimal pf, boolean isDigital=false) { // library marker kkossev.energyLib, line 130
    Map map = [:] // library marker kkossev.energyLib, line 131
    map.name = 'powerFactor' // library marker kkossev.energyLib, line 132
    map.value = pf.setScale(2, BigDecimal.ROUND_HALF_UP) // library marker kkossev.energyLib, line 133
    map.unit = '%' // library marker kkossev.energyLib, line 134
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.energyLib, line 135
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.energyLib, line 136
    if (state.states.isRefresh == true) { map.descriptionText += ' (refresh)' } // library marker kkossev.energyLib, line 137
    final BigDecimal lastPF = device.currentValue('powerFactor') ?: 0.0 // library marker kkossev.energyLib, line 138
    final BigDecimal powerFactorThreshold = 0.01 // library marker kkossev.energyLib, line 139
    if (Math.abs(pf - lastPF) >= powerFactorThreshold || state.states.isRefresh == true) { // library marker kkossev.energyLib, line 140
        logInfo "${map.descriptionText}" // library marker kkossev.energyLib, line 141
        sendEvent(map) // library marker kkossev.energyLib, line 142
    } // library marker kkossev.energyLib, line 143
    else { // library marker kkossev.energyLib, line 144
        logDebug "ignored ${map.name} ${map.value} ${map.unit} (change from ${lastFrequency} is less than ${powerFactorThreshold} %)" // library marker kkossev.energyLib, line 145
    } // library marker kkossev.energyLib, line 146
} // library marker kkossev.energyLib, line 147

List<String> configureReporting(String operation, String measurement,  String minTime='0', String maxTime='0', String delta='0', boolean sendNow=true ) { // library marker kkossev.energyLib, line 149
    int intMinTime = safeToInt(minTime) // library marker kkossev.energyLib, line 150
    int intMaxTime = safeToInt(maxTime) // library marker kkossev.energyLib, line 151
    int intDelta = safeToInt(delta) // library marker kkossev.energyLib, line 152
    String epString = state.destinationEP // library marker kkossev.energyLib, line 153
    int ep = safeToInt(epString) // library marker kkossev.energyLib, line 154
    if (ep == null || ep == 0) { // library marker kkossev.energyLib, line 155
        ep = 1 // library marker kkossev.energyLib, line 156
        epString = '01' // library marker kkossev.energyLib, line 157
    } // library marker kkossev.energyLib, line 158

    logDebug "configureReporting operation=${operation}, measurement=${measurement}, minTime=${intMinTime}, maxTime=${intMaxTime}, delta=${intDelta} )" // library marker kkossev.energyLib, line 160

    List<String> cmds = [] // library marker kkossev.energyLib, line 162

    switch (measurement) { // library marker kkossev.energyLib, line 164
        case ONOFF : // library marker kkossev.energyLib, line 165
            if (operation == 'Write') { // library marker kkossev.energyLib, line 166
                cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${epString} 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 251', ] // library marker kkossev.energyLib, line 167
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 ${intMinTime} ${intMaxTime} {}", 'delay 251', ] // library marker kkossev.energyLib, line 168
            } // library marker kkossev.energyLib, line 169
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 170
                cmds += ["he cr 0x${device.deviceNetworkId} 0x${epString} 6 0 16 65535 65535 {}", 'delay 251', ]    // disable Plug automatic reporting // library marker kkossev.energyLib, line 171
            } // library marker kkossev.energyLib, line 172
            cmds +=  zigbee.reportingConfiguration(0x0006, 0x0000, [destEndpoint :ep], 251)    // read it back // library marker kkossev.energyLib, line 173
            break // library marker kkossev.energyLib, line 174
        case ENERGY :    // default delta = 1 Wh (0.001 kWh) // library marker kkossev.energyLib, line 175
            if (operation == 'Write') { // library marker kkossev.energyLib, line 176
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, intMinTime, intMaxTime, (intDelta * getEnergyDiv() as int)) // library marker kkossev.energyLib, line 177
            } // library marker kkossev.energyLib, line 178
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 179
                cmds += zigbee.configureReporting(0x0702, 0x0000,  DataType.UINT48, 0xFFFF, 0xFFFF, 0x0000)    // disable energy automatic reporting - tested with Frient // library marker kkossev.energyLib, line 180
            } // library marker kkossev.energyLib, line 181
            cmds += zigbee.reportingConfiguration(0x0702, 0x0000, [destEndpoint :ep], 252) // library marker kkossev.energyLib, line 182
            break // library marker kkossev.energyLib, line 183
        case INST_POWER :        // 0x702:0x400 // library marker kkossev.energyLib, line 184
            if (operation == 'Write') { // library marker kkossev.energyLib, line 185
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int)) // library marker kkossev.energyLib, line 186
            } // library marker kkossev.energyLib, line 187
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 188
                cmds += zigbee.configureReporting(0x0702, 0x0400,  DataType.INT16, 0xFFFF, 0xFFFF, 0x0000)    // disable power automatic reporting - tested with Frient // library marker kkossev.energyLib, line 189
            } // library marker kkossev.energyLib, line 190
            cmds += zigbee.reportingConfiguration(0x0702, 0x0400, [destEndpoint :ep], 253) // library marker kkossev.energyLib, line 191
            break // library marker kkossev.energyLib, line 192
        case POWER :        // Active power default delta = 1 // library marker kkossev.energyLib, line 193
            if (operation == 'Write') { // library marker kkossev.energyLib, line 194
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, intMinTime, intMaxTime, (intDelta * getPowerDiv() as int) )   // bug fixes in ver  1.6.0 - thanks @guyee // library marker kkossev.energyLib, line 195
            } // library marker kkossev.energyLib, line 196
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 197
                cmds += zigbee.configureReporting(0x0B04, 0x050B,  DataType.INT16, 0xFFFF, 0xFFFF, 0x8000)    // disable power automatic reporting - tested with Frient // library marker kkossev.energyLib, line 198
            } // library marker kkossev.energyLib, line 199
            cmds += zigbee.reportingConfiguration(0x0B04, 0x050B, [destEndpoint :ep], 254) // library marker kkossev.energyLib, line 200
            break // library marker kkossev.energyLib, line 201
        case VOLTAGE :    // RMS Voltage default delta = 1 // library marker kkossev.energyLib, line 202
            if (operation == 'Write') { // library marker kkossev.energyLib, line 203
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getVoltageDiv() as int)) // library marker kkossev.energyLib, line 204
            } // library marker kkossev.energyLib, line 205
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 206
                cmds += zigbee.configureReporting(0x0B04, 0x0505,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable voltage automatic reporting - tested with Frient // library marker kkossev.energyLib, line 207
            } // library marker kkossev.energyLib, line 208
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0505, [destEndpoint :ep], 255) // library marker kkossev.energyLib, line 209
            break // library marker kkossev.energyLib, line 210
        case AMPERAGE :    // RMS Current default delta = 100 mA = 0.1 A // library marker kkossev.energyLib, line 211
            if (operation == 'Write') { // library marker kkossev.energyLib, line 212
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getCurrentDiv() as int)) // library marker kkossev.energyLib, line 213
            } // library marker kkossev.energyLib, line 214
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 215
                cmds += zigbee.configureReporting(0x0B04, 0x0508,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable amperage automatic reporting - tested with Frient // library marker kkossev.energyLib, line 216
            } // library marker kkossev.energyLib, line 217
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0508, [destEndpoint :ep], 256) // library marker kkossev.energyLib, line 218
            break // library marker kkossev.energyLib, line 219
        case FREQUENCY :    // added 03/27/2023 // library marker kkossev.energyLib, line 220
            if (operation == 'Write') { // library marker kkossev.energyLib, line 221
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getFrequencyDiv() as int)) // library marker kkossev.energyLib, line 222
            } // library marker kkossev.energyLib, line 223
            else if (operation == 'Disable') { // library marker kkossev.energyLib, line 224
                cmds += zigbee.configureReporting(0x0B04, 0x0300,  DataType.UINT16, 0xFFFF, 0xFFFF, 0xFFFF)    // disable frequency automatic reporting - tested with Frient // library marker kkossev.energyLib, line 225
            } // library marker kkossev.energyLib, line 226
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0300, [destEndpoint :ep], 257) // library marker kkossev.energyLib, line 227
            break // library marker kkossev.energyLib, line 228
        case POWER_FACTOR : // added 03/27/2023 // library marker kkossev.energyLib, line 229
            if (operation == 'Write') { // library marker kkossev.energyLib, line 230
                cmds += zigbee.configureReporting(0x0B04, 0x0510,  DataType.UINT16, intMinTime, intMaxTime, (intDelta * getPowerFactorDiv() as int)) // library marker kkossev.energyLib, line 231
            } // library marker kkossev.energyLib, line 232
            cmds += zigbee.reportingConfiguration(0x0B04, 0x0510, [destEndpoint :ep], 258) // library marker kkossev.energyLib, line 233
            break // library marker kkossev.energyLib, line 234
        default : // library marker kkossev.energyLib, line 235
            break // library marker kkossev.energyLib, line 236
    } // library marker kkossev.energyLib, line 237
    if (cmds != null) { // library marker kkossev.energyLib, line 238
        if (sendNow == true) { // library marker kkossev.energyLib, line 239
            sendZigbeeCommands(cmds) // library marker kkossev.energyLib, line 240
        } // library marker kkossev.energyLib, line 241
        else { // library marker kkossev.energyLib, line 242
            return cmds // library marker kkossev.energyLib, line 243
        } // library marker kkossev.energyLib, line 244
    } // library marker kkossev.energyLib, line 245
} // library marker kkossev.energyLib, line 246

// ~~~~~ end include (166) kkossev.energyLib ~~~~~

// ~~~~~ start include (168) kkossev.illuminanceLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.illuminanceLib, line 1
library( // library marker kkossev.illuminanceLib, line 2
    base: 'driver', // library marker kkossev.illuminanceLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.illuminanceLib, line 4
    category: 'zigbee', // library marker kkossev.illuminanceLib, line 5
    description: 'Zigbee Illuminance Library', // library marker kkossev.illuminanceLib, line 6
    name: 'illuminanceLib', // library marker kkossev.illuminanceLib, line 7
    namespace: 'kkossev', // library marker kkossev.illuminanceLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/illuminanceLib.groovy', // library marker kkossev.illuminanceLib, line 9
    version: '3.0.0', // library marker kkossev.illuminanceLib, line 10
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
 *                                   TODO: // library marker kkossev.illuminanceLib, line 27
*/ // library marker kkossev.illuminanceLib, line 28

static String illuminanceLibVersion()   { '3.0.0' } // library marker kkossev.illuminanceLib, line 30
static String illuminanceLibStamp() { '2024/04/06 2:40 PM' } // library marker kkossev.illuminanceLib, line 31

metadata { // library marker kkossev.illuminanceLib, line 33
    // no capabilities // library marker kkossev.illuminanceLib, line 34
    // no attributes // library marker kkossev.illuminanceLib, line 35
    // no commands // library marker kkossev.illuminanceLib, line 36
    preferences { // library marker kkossev.illuminanceLib, line 37
        // no prefrences // library marker kkossev.illuminanceLib, line 38
    } // library marker kkossev.illuminanceLib, line 39
} // library marker kkossev.illuminanceLib, line 40

// ~~~~~ end include (168) kkossev.illuminanceLib ~~~~~

// ~~~~~ start include (170) kkossev.levelLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.levelLib, line 1
library( // library marker kkossev.levelLib, line 2
    base: 'driver', // library marker kkossev.levelLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.levelLib, line 4
    category: 'zigbee', // library marker kkossev.levelLib, line 5
    description: 'Zigbee Level Library', // library marker kkossev.levelLib, line 6
    name: 'levelLib', // library marker kkossev.levelLib, line 7
    namespace: 'kkossev', // library marker kkossev.levelLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/levelLib.groovy', // library marker kkossev.levelLib, line 9
    version: '3.0.0', // library marker kkossev.levelLib, line 10
    documentationLink: '' // library marker kkossev.levelLib, line 11
) // library marker kkossev.levelLib, line 12
/* // library marker kkossev.levelLib, line 13
 *  Zigbee Level Library // library marker kkossev.levelLib, line 14
 * // library marker kkossev.levelLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.levelLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.levelLib, line 17
 * // library marker kkossev.levelLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.levelLib, line 19
 * // library marker kkossev.levelLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.levelLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.levelLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.levelLib, line 23
 * // library marker kkossev.levelLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added levelLib.groovy // library marker kkossev.levelLib, line 25
 * // library marker kkossev.levelLib, line 26
 *                                   TODO: // library marker kkossev.levelLib, line 27
*/ // library marker kkossev.levelLib, line 28

static String levelLibVersion()   { '3.0.0' } // library marker kkossev.levelLib, line 30
static String levelLibStamp() { '2024/04/06 9:07 PM' } // library marker kkossev.levelLib, line 31

metadata { // library marker kkossev.levelLib, line 33
    // no capabilities // library marker kkossev.levelLib, line 34
    // no attributes // library marker kkossev.levelLib, line 35
    // no commands // library marker kkossev.levelLib, line 36
    preferences { // library marker kkossev.levelLib, line 37
        // no prefrences // library marker kkossev.levelLib, line 38
    } // library marker kkossev.levelLib, line 39
} // library marker kkossev.levelLib, line 40

import groovy.transform.Field // library marker kkossev.levelLib, line 42

void levelLibParseLevelControlCluster(final Map descMap) { // library marker kkossev.levelLib, line 44
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.levelLib, line 45
        customParseLevelControlCluster(descMap) // library marker kkossev.levelLib, line 46
    } // library marker kkossev.levelLib, line 47
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.levelLib, line 48
        parseLevelControlClusterBulb(descMap) // library marker kkossev.levelLib, line 49
    } // library marker kkossev.levelLib, line 50
    else if (descMap.attrId == '0000') { // library marker kkossev.levelLib, line 51
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.levelLib, line 52
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.levelLib, line 53
        sendLevelControlEvent(rawValue) // library marker kkossev.levelLib, line 54
    } // library marker kkossev.levelLib, line 55
    else { // library marker kkossev.levelLib, line 56
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.levelLib, line 57
    } // library marker kkossev.levelLib, line 58
} // library marker kkossev.levelLib, line 59

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.levelLib, line 61
    int value = rawValue as int // library marker kkossev.levelLib, line 62
    if (value < 0) { value = 0 } // library marker kkossev.levelLib, line 63
    if (value > 100) { value = 100 } // library marker kkossev.levelLib, line 64
    Map map = [:] // library marker kkossev.levelLib, line 65

    boolean isDigital = state.states['isDigital'] // library marker kkossev.levelLib, line 67
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.levelLib, line 68

    map.name = 'level' // library marker kkossev.levelLib, line 70
    map.value = value // library marker kkossev.levelLib, line 71
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.levelLib, line 72
    if (isRefresh == true) { // library marker kkossev.levelLib, line 73
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.levelLib, line 74
        map.isStateChange = true // library marker kkossev.levelLib, line 75
    } // library marker kkossev.levelLib, line 76
    else { // library marker kkossev.levelLib, line 77
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.levelLib, line 78
    } // library marker kkossev.levelLib, line 79
    logInfo "${map.descriptionText}" // library marker kkossev.levelLib, line 80
    sendEvent(map) // library marker kkossev.levelLib, line 81
    clearIsDigital() // library marker kkossev.levelLib, line 82
} // library marker kkossev.levelLib, line 83

/** // library marker kkossev.levelLib, line 85
 * Send 'switchLevel' attribute event // library marker kkossev.levelLib, line 86
 * @param isOn true if light is on, false otherwise // library marker kkossev.levelLib, line 87
 * @param level brightness level (0-254) // library marker kkossev.levelLib, line 88
 */ // library marker kkossev.levelLib, line 89
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.levelLib, line 90
private List<String> setLevelPrivate(final BigDecimal value, final int rate = 0, final int delay = 0, final Boolean levelPreset = false) { // library marker kkossev.levelLib, line 91
    List<String> cmds = [] // library marker kkossev.levelLib, line 92
    final Integer level = constrain(value) // library marker kkossev.levelLib, line 93
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.levelLib, line 94
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.levelLib, line 95
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.levelLib, line 96
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.levelLib, line 97
        // If light is off, first go to level 0 then to desired level // library marker kkossev.levelLib, line 98
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.levelLib, line 99
    } // library marker kkossev.levelLib, line 100
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.levelLib, line 101
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.levelLib, line 102
    /* // library marker kkossev.levelLib, line 103
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.levelLib, line 104
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.levelLib, line 105
    */ // library marker kkossev.levelLib, line 106
    int duration = 10            // TODO !!! // library marker kkossev.levelLib, line 107
    String endpointId = '01'     // TODO !!! // library marker kkossev.levelLib, line 108
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.levelLib, line 109

    return cmds // library marker kkossev.levelLib, line 111
} // library marker kkossev.levelLib, line 112

/** // library marker kkossev.levelLib, line 114
 * Set Level Command // library marker kkossev.levelLib, line 115
 * @param value level percent (0-100) // library marker kkossev.levelLib, line 116
 * @param transitionTime transition time in seconds // library marker kkossev.levelLib, line 117
 * @return List of zigbee commands // library marker kkossev.levelLib, line 118
 */ // library marker kkossev.levelLib, line 119
void setLevel(final BigDecimal value, final BigDecimal transitionTime = null) { // library marker kkossev.levelLib, line 120
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.levelLib, line 121
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.levelLib, line 122
        customSetLevel(value.intValue(), transitionTime.intValue()) // library marker kkossev.levelLib, line 123
        return // library marker kkossev.levelLib, line 124
    } // library marker kkossev.levelLib, line 125
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value.intValue(), transitionTime.intValue()); return } // library marker kkossev.levelLib, line 126
    final Integer rate = getLevelTransitionRate(value.intValue(), transitionTime.intValue()) // library marker kkossev.levelLib, line 127
    scheduleCommandTimeoutCheck() // library marker kkossev.levelLib, line 128
    sendZigbeeCommands(setLevelPrivate(value.intValue(), rate as int)) // library marker kkossev.levelLib, line 129
} // library marker kkossev.levelLib, line 130

/** // library marker kkossev.levelLib, line 132
 * Get the level transition rate // library marker kkossev.levelLib, line 133
 * @param level desired target level (0-100) // library marker kkossev.levelLib, line 134
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.levelLib, line 135
 * @return transition rate in 1/10ths of a second // library marker kkossev.levelLib, line 136
 */ // library marker kkossev.levelLib, line 137
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.levelLib, line 138
    int rate = 0 // library marker kkossev.levelLib, line 139
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.levelLib, line 140
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.levelLib, line 141
    if (!isOn) { // library marker kkossev.levelLib, line 142
        currentLevel = 0 // library marker kkossev.levelLib, line 143
    } // library marker kkossev.levelLib, line 144
    // Check if 'transitionTime' has a value // library marker kkossev.levelLib, line 145
    if (transitionTime > 0) { // library marker kkossev.levelLib, line 146
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.levelLib, line 147
        rate = transitionTime * 10 // library marker kkossev.levelLib, line 148
    } else { // library marker kkossev.levelLib, line 149
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.levelLib, line 150
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.levelLib, line 151
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.levelLib, line 152
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.levelLib, line 153
        } // library marker kkossev.levelLib, line 154
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.levelLib, line 155
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.levelLib, line 156
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.levelLib, line 157
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.levelLib, line 158
        } // library marker kkossev.levelLib, line 159
    } // library marker kkossev.levelLib, line 160
    logDebug "using level transition rate ${rate}" // library marker kkossev.levelLib, line 161
    return rate // library marker kkossev.levelLib, line 162
} // library marker kkossev.levelLib, line 163

// Delay before reading attribute (when using polling) // library marker kkossev.levelLib, line 165
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.levelLib, line 166
// Command option that enable changes when off // library marker kkossev.levelLib, line 167
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.levelLib, line 168

/** // library marker kkossev.levelLib, line 170
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.levelLib, line 171
 * @param delayMs delay in milliseconds // library marker kkossev.levelLib, line 172
 * @param commands commands to execute // library marker kkossev.levelLib, line 173
 * @return list of commands to be sent to the device // library marker kkossev.levelLib, line 174
 */ // library marker kkossev.levelLib, line 175
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.levelLib, line 176
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.levelLib, line 177
    if (state.reportingEnabled == false) { // library marker kkossev.levelLib, line 178
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.levelLib, line 179
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.levelLib, line 180
    } // library marker kkossev.levelLib, line 181
    return [] // library marker kkossev.levelLib, line 182
} // library marker kkossev.levelLib, line 183

/** // library marker kkossev.levelLib, line 185
 * Constrain a value to a range // library marker kkossev.levelLib, line 186
 * @param value value to constrain // library marker kkossev.levelLib, line 187
 * @param min minimum value (default 0) // library marker kkossev.levelLib, line 188
 * @param max maximum value (default 100) // library marker kkossev.levelLib, line 189
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.levelLib, line 190
 */ // library marker kkossev.levelLib, line 191
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.levelLib, line 192
    if (min == null || max == null) { // library marker kkossev.levelLib, line 193
        return value // library marker kkossev.levelLib, line 194
    } // library marker kkossev.levelLib, line 195
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.levelLib, line 196
} // library marker kkossev.levelLib, line 197

/** // library marker kkossev.levelLib, line 199
 * Constrain a value to a range // library marker kkossev.levelLib, line 200
 * @param value value to constrain // library marker kkossev.levelLib, line 201
 * @param min minimum value (default 0) // library marker kkossev.levelLib, line 202
 * @param max maximum value (default 100) // library marker kkossev.levelLib, line 203
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.levelLib, line 204
 */ // library marker kkossev.levelLib, line 205
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.levelLib, line 206
    if (min == null || max == null) { // library marker kkossev.levelLib, line 207
        return value as Integer // library marker kkossev.levelLib, line 208
    } // library marker kkossev.levelLib, line 209
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.levelLib, line 210
} // library marker kkossev.levelLib, line 211

// ~~~~~ end include (170) kkossev.levelLib ~~~~~
