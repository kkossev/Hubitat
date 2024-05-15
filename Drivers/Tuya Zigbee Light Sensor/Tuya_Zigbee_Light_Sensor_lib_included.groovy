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
static String timeStamp() { '2024/04/06 1:31 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field

@Field static final String DEVICE_TYPE = 'LightSensor'
deviceType = 'LightSensor'





metadata {
    definition(
        name: 'Tuya Zigbee Light Sensor',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Light%20Sensor/Tuya%20Zigbee%20Light%20Sensor.groovy',
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

@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5

void customParseIlluminanceCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    final int value = hexStrToUnsignedInt(descMap.value)
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0
    handleIlluminanceEvent(lux)
}

void handleIlluminanceEvent(int illuminance, Boolean isDigital=false) {
    Map eventMap = [:]
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] }
    eventMap.name = 'illuminance'
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float)))
    eventMap.value  = illumCorrected
    eventMap.type = isDigital ? 'digital' : 'physical'
    eventMap.unit = 'lx'
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000)
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME
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

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high']

/* groovylint-disable-next-line UnusedMethodParameter */
void customProcessTuyaDP(final Map descMap, int dp, int dp_id, int fncmd) {
    switch (dp) {
        case 0x01 : // on/off
            if (DEVICE_TYPE in  ['LightSensor']) {
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})"
            }
            else {
                sendSwitchEvent(fncmd)
            }
            break
        case 0x02 :
            if (DEVICE_TYPE in  ['LightSensor']) {
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

void customInitializeVars( boolean fullInit = false ) {
    logDebug "customInitializeVars()... fullInit = ${fullInit}"
    if (device.hasCapability('IlluminanceMeasurement')) {
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) }
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) }
    }
    if (device.hasCapability('IlluminanceMeasurement')) {
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) }
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) }
    }
}

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
  * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; // library marker kkossev.commonLib, line 38
  * // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 40
  *                                   TODO: add custom* handlers for the new drivers! // library marker kkossev.commonLib, line 41
  *                                   TODO: remove the automatic capabilities selectionm for the new drivers! // library marker kkossev.commonLib, line 42
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 43
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 44
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 45
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 46
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 47
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 48
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 49
 * // library marker kkossev.commonLib, line 50
*/ // library marker kkossev.commonLib, line 51

String commonLibVersion() { '3.0.6' } // library marker kkossev.commonLib, line 53
String commonLibStamp() { '2024/04/06 2:33 PM' } // library marker kkossev.commonLib, line 54

import groovy.transform.Field // library marker kkossev.commonLib, line 56
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 57
import hubitat.device.Protocol // library marker kkossev.commonLib, line 58
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 59
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 60
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 61
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 62
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 63
import java.math.BigDecimal // library marker kkossev.commonLib, line 64

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 66

metadata { // library marker kkossev.commonLib, line 68
        if (_DEBUG) { // library marker kkossev.commonLib, line 69
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 70
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 71
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 72
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 73
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 74
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 75
            ] // library marker kkossev.commonLib, line 76
        } // library marker kkossev.commonLib, line 77

        // common capabilities for all device types // library marker kkossev.commonLib, line 79
        capability 'Configuration' // library marker kkossev.commonLib, line 80
        capability 'Refresh' // library marker kkossev.commonLib, line 81
        capability 'Health Check' // library marker kkossev.commonLib, line 82

        // common attributes for all device types // library marker kkossev.commonLib, line 84
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 85
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 86
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 87

        // common commands for all device types // library marker kkossev.commonLib, line 89
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 90
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 91

        // deviceType specific capabilities, commands and attributes // library marker kkossev.commonLib, line 93
        if (deviceType in ['Device']) { // library marker kkossev.commonLib, line 94
            if (_DEBUG) { // library marker kkossev.commonLib, line 95
                command 'getAllProperties',       [[name: 'Get All Properties']] // library marker kkossev.commonLib, line 96
            } // library marker kkossev.commonLib, line 97
        } // library marker kkossev.commonLib, line 98
        if (_DEBUG || (deviceType in ['Dimmer', 'Switch', 'Valve'])) { // library marker kkossev.commonLib, line 99
            command 'zigbeeGroups', [ // library marker kkossev.commonLib, line 100
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 101
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.commonLib, line 102
            ] // library marker kkossev.commonLib, line 103
        } // library marker kkossev.commonLib, line 104
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor']) { // library marker kkossev.commonLib, line 105
            capability 'Sensor' // library marker kkossev.commonLib, line 106
        } // library marker kkossev.commonLib, line 107
        if (deviceType in  ['Device', 'MotionSensor']) { // library marker kkossev.commonLib, line 108
            capability 'MotionSensor' // library marker kkossev.commonLib, line 109
        } // library marker kkossev.commonLib, line 110
        if (deviceType in  ['Device', 'Switch', 'Relay', 'Outlet', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 111
            capability 'Actuator' // library marker kkossev.commonLib, line 112
        } // library marker kkossev.commonLib, line 113
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor', 'Thermostat']) { // library marker kkossev.commonLib, line 114
            capability 'Battery' // library marker kkossev.commonLib, line 115
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 116
        } // library marker kkossev.commonLib, line 117
        if (deviceType in  ['Device', 'Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 118
            capability 'Switch' // library marker kkossev.commonLib, line 119
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 120
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 121
            } // library marker kkossev.commonLib, line 122
        } // library marker kkossev.commonLib, line 123
        if (deviceType in ['Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 124
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 125
        } // library marker kkossev.commonLib, line 126
        if (deviceType in  ['Device']) { // library marker kkossev.commonLib, line 127
            capability 'Momentary' // library marker kkossev.commonLib, line 128
        } // library marker kkossev.commonLib, line 129
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 130
            capability 'TemperatureMeasurement' // library marker kkossev.commonLib, line 131
        } // library marker kkossev.commonLib, line 132
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 133
            capability 'RelativeHumidityMeasurement' // library marker kkossev.commonLib, line 134
        } // library marker kkossev.commonLib, line 135

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 137
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 138

    preferences { // library marker kkossev.commonLib, line 140
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 141
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 142
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 143

        if (device) { // library marker kkossev.commonLib, line 145
            if ((device.hasCapability('TemperatureMeasurement') || device.hasCapability('RelativeHumidityMeasurement') /*|| device.hasCapability('IlluminanceMeasurement')*/)) { // library marker kkossev.commonLib, line 146
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 147
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.commonLib, line 148
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 149
                } // library marker kkossev.commonLib, line 150
            } // library marker kkossev.commonLib, line 151
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 152
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 153
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 154
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 155
                if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 156
                    input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 157
                } // library marker kkossev.commonLib, line 158
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 159
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 160
                } // library marker kkossev.commonLib, line 161
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 162
            } // library marker kkossev.commonLib, line 163
        } // library marker kkossev.commonLib, line 164
    } // library marker kkossev.commonLib, line 165
} // library marker kkossev.commonLib, line 166

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 168
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 169
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 170
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 171
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 172
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 173
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 174
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 175
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 176
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 177
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 178

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 180
    defaultValue: 1, // library marker kkossev.commonLib, line 181
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 182
] // library marker kkossev.commonLib, line 183
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 184
    defaultValue: 240, // library marker kkossev.commonLib, line 185
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 186
] // library marker kkossev.commonLib, line 187
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 188
    defaultValue: 0, // library marker kkossev.commonLib, line 189
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 190
] // library marker kkossev.commonLib, line 191

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 193
    defaultValue: 0, // library marker kkossev.commonLib, line 194
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 195
] // library marker kkossev.commonLib, line 196
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 197
    defaultValue: 0, // library marker kkossev.commonLib, line 198
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 199
] // library marker kkossev.commonLib, line 200

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 202
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 203
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 204
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 205
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 206
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 207
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 208
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 209
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 210
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 211
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 212
] // library marker kkossev.commonLib, line 213

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 215
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 216
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 217
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 218
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 219
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 220
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 221
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 222
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 223

/** // library marker kkossev.commonLib, line 225
 * Parse Zigbee message // library marker kkossev.commonLib, line 226
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 227
 */ // library marker kkossev.commonLib, line 228
void parse(final String description) { // library marker kkossev.commonLib, line 229
    checkDriverVersion() // library marker kkossev.commonLib, line 230
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 231
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 232
    setHealthStatusOnline() // library marker kkossev.commonLib, line 233

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 235
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 236
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 237
            parseIasMessage(description) // library marker kkossev.commonLib, line 238
        } // library marker kkossev.commonLib, line 239
        else { // library marker kkossev.commonLib, line 240
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 241
        } // library marker kkossev.commonLib, line 242
        return // library marker kkossev.commonLib, line 243
    } // library marker kkossev.commonLib, line 244
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 245
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 246
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 247
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 248
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 249
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 250
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 251
        return // library marker kkossev.commonLib, line 252
    } // library marker kkossev.commonLib, line 253
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 254
        return // library marker kkossev.commonLib, line 255
    } // library marker kkossev.commonLib, line 256
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 257

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 259
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 260
        return // library marker kkossev.commonLib, line 261
    } // library marker kkossev.commonLib, line 262
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 263
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 264
        return // library marker kkossev.commonLib, line 265
    } // library marker kkossev.commonLib, line 266
    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 267
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 268
    // // library marker kkossev.commonLib, line 269
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 270
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 271
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 272

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 274
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 275
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 276
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 277
            break // library marker kkossev.commonLib, line 278
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 279
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 280
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 281
            break // library marker kkossev.commonLib, line 282
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 283
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 284
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 285
            break // library marker kkossev.commonLib, line 286
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 287
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 288
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 289
            break // library marker kkossev.commonLib, line 290
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 291
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 292
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 293
            break // library marker kkossev.commonLib, line 294
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 295
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 296
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 297
            break // library marker kkossev.commonLib, line 298
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 299
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 300
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 301
            break // library marker kkossev.commonLib, line 302
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 303
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 304
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 307
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 308
            break // library marker kkossev.commonLib, line 309
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 310
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 311
            break // library marker kkossev.commonLib, line 312
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 313
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 314
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 315
            break // library marker kkossev.commonLib, line 316
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 317
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 318
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 319
            break // library marker kkossev.commonLib, line 320
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 321
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 322
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 323
            break // library marker kkossev.commonLib, line 324
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 325
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 328
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 329
            break // library marker kkossev.commonLib, line 330
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 331
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 332
            break // library marker kkossev.commonLib, line 333
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 334
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 335
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 336
            break // library marker kkossev.commonLib, line 337
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 338
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 339
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 340
            break // library marker kkossev.commonLib, line 341
        case 0xE002 : // library marker kkossev.commonLib, line 342
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 343
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 344
            break // library marker kkossev.commonLib, line 345
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 346
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 347
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 348
            break // library marker kkossev.commonLib, line 349
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 350
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 351
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 352
            break // library marker kkossev.commonLib, line 353
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 354
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 355
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 356
            break // library marker kkossev.commonLib, line 357
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 358
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 361
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 362
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 363
            break // library marker kkossev.commonLib, line 364
        default: // library marker kkossev.commonLib, line 365
            if (settings.logEnable) { // library marker kkossev.commonLib, line 366
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 367
            } // library marker kkossev.commonLib, line 368
            break // library marker kkossev.commonLib, line 369
    } // library marker kkossev.commonLib, line 370
} // library marker kkossev.commonLib, line 371

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 373
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 374
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 375
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 376
    } // library marker kkossev.commonLib, line 377
    return false // library marker kkossev.commonLib, line 378
} // library marker kkossev.commonLib, line 379

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 381
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 382
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 383
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 384
    } // library marker kkossev.commonLib, line 385
    return false // library marker kkossev.commonLib, line 386
} // library marker kkossev.commonLib, line 387

/** // library marker kkossev.commonLib, line 389
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 390
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 391
 */ // library marker kkossev.commonLib, line 392
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 393
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 394
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 395
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 396
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 397
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 398
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 399
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 400
    } // library marker kkossev.commonLib, line 401
    else { // library marker kkossev.commonLib, line 402
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 403
    } // library marker kkossev.commonLib, line 404
} // library marker kkossev.commonLib, line 405

/** // library marker kkossev.commonLib, line 407
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 408
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 409
 */ // library marker kkossev.commonLib, line 410
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 411
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 412
    switch (commandId) { // library marker kkossev.commonLib, line 413
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 414
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 415
            break // library marker kkossev.commonLib, line 416
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 417
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 418
            break // library marker kkossev.commonLib, line 419
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 420
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 421
            break // library marker kkossev.commonLib, line 422
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 423
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 424
            break // library marker kkossev.commonLib, line 425
        case 0x0B: // default command response // library marker kkossev.commonLib, line 426
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 427
            break // library marker kkossev.commonLib, line 428
        default: // library marker kkossev.commonLib, line 429
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 430
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 431
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 432
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 433
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 434
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 435
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 436
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 437
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 438
            } // library marker kkossev.commonLib, line 439
            break // library marker kkossev.commonLib, line 440
    } // library marker kkossev.commonLib, line 441
} // library marker kkossev.commonLib, line 442

/** // library marker kkossev.commonLib, line 444
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 445
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 446
 */ // library marker kkossev.commonLib, line 447
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 448
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 449
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 450
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 451
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 452
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 453
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 454
    } // library marker kkossev.commonLib, line 455
    else { // library marker kkossev.commonLib, line 456
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 457
    } // library marker kkossev.commonLib, line 458
} // library marker kkossev.commonLib, line 459

/** // library marker kkossev.commonLib, line 461
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 462
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 463
 */ // library marker kkossev.commonLib, line 464
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 465
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 466
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 467
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 468
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 469
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 470
    } // library marker kkossev.commonLib, line 471
    else { // library marker kkossev.commonLib, line 472
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 473
    } // library marker kkossev.commonLib, line 474
} // library marker kkossev.commonLib, line 475

/** // library marker kkossev.commonLib, line 477
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 478
 */ // library marker kkossev.commonLib, line 479
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 480
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 481
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 482
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 483
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 484
        state.reportingEnabled = true // library marker kkossev.commonLib, line 485
    } // library marker kkossev.commonLib, line 486
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 487
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 488
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 489
    } else { // library marker kkossev.commonLib, line 490
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 491
    } // library marker kkossev.commonLib, line 492
} // library marker kkossev.commonLib, line 493

/** // library marker kkossev.commonLib, line 495
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 496
 */ // library marker kkossev.commonLib, line 497
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 498
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 499
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 500
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 501
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 502
    if (status == 0) { // library marker kkossev.commonLib, line 503
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 504
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 505
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 506
        int delta = 0 // library marker kkossev.commonLib, line 507
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 508
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 509
        } // library marker kkossev.commonLib, line 510
        else { // library marker kkossev.commonLib, line 511
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 512
        } // library marker kkossev.commonLib, line 513
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 514
    } // library marker kkossev.commonLib, line 515
    else { // library marker kkossev.commonLib, line 516
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 517
    } // library marker kkossev.commonLib, line 518
} // library marker kkossev.commonLib, line 519

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 521
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 522
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 523
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 524
        return false // library marker kkossev.commonLib, line 525
    } // library marker kkossev.commonLib, line 526
    // execute the customHandler function // library marker kkossev.commonLib, line 527
    boolean result = false // library marker kkossev.commonLib, line 528
    try { // library marker kkossev.commonLib, line 529
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 530
    } // library marker kkossev.commonLib, line 531
    catch (e) { // library marker kkossev.commonLib, line 532
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 533
        return false // library marker kkossev.commonLib, line 534
    } // library marker kkossev.commonLib, line 535
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 536
    return result // library marker kkossev.commonLib, line 537
} // library marker kkossev.commonLib, line 538

/** // library marker kkossev.commonLib, line 540
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 541
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 542
 */ // library marker kkossev.commonLib, line 543
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 544
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 545
    final String commandId = data[0] // library marker kkossev.commonLib, line 546
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 547
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 548
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 549
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 550
    } else { // library marker kkossev.commonLib, line 551
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 552
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 553
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 554
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 555
        } // library marker kkossev.commonLib, line 556
    } // library marker kkossev.commonLib, line 557
} // library marker kkossev.commonLib, line 558

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 560
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 561
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 562
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 563
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 564
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 565
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 566
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 567
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 568
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 569
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 570
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 571
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 572
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 573
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 574
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 575

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 577
    0x00: 'Success', // library marker kkossev.commonLib, line 578
    0x01: 'Failure', // library marker kkossev.commonLib, line 579
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 580
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 581
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 582
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 583
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 584
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 585
    0x88: 'Read Only', // library marker kkossev.commonLib, line 586
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 587
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 588
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 589
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 590
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 591
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 592
    0x94: 'Time out', // library marker kkossev.commonLib, line 593
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 594
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 595
] // library marker kkossev.commonLib, line 596

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 598
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 599
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 600
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 601
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 602
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 603
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 604
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 605
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 606
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 607
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 608
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 609
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 610
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 611
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 612
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 613
] // library marker kkossev.commonLib, line 614

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 616
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 617
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 618
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 619
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 620
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 621
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 622
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 623
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 624
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 625
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 626
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 627
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 628
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 629
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 630
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 631
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 632
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 633
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 634
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 635
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 636
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 637
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 638
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 639
] // library marker kkossev.commonLib, line 640

/* // library marker kkossev.commonLib, line 642
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 643
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 644
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 645
 */ // library marker kkossev.commonLib, line 646
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 647
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 648
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 649
    } // library marker kkossev.commonLib, line 650
    else { // library marker kkossev.commonLib, line 651
        logWarn 'Xiaomi cluster 0xFCC0' // library marker kkossev.commonLib, line 652
    } // library marker kkossev.commonLib, line 653
} // library marker kkossev.commonLib, line 654

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 656
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 657
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 658
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 659
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 660
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 661
    return avg // library marker kkossev.commonLib, line 662
} // library marker kkossev.commonLib, line 663

/* // library marker kkossev.commonLib, line 665
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 666
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 667
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 668
*/ // library marker kkossev.commonLib, line 669
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 670

/** // library marker kkossev.commonLib, line 672
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 673
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 674
 */ // library marker kkossev.commonLib, line 675
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 676
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 677
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 678
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 679
        case 0x0000: // library marker kkossev.commonLib, line 680
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 681
            break // library marker kkossev.commonLib, line 682
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 683
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 684
            if (isPing) { // library marker kkossev.commonLib, line 685
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 686
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 687
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 688
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 689
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 690
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 691
                    sendRttEvent() // library marker kkossev.commonLib, line 692
                } // library marker kkossev.commonLib, line 693
                else { // library marker kkossev.commonLib, line 694
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 695
                } // library marker kkossev.commonLib, line 696
                state.states['isPing'] = false // library marker kkossev.commonLib, line 697
            } // library marker kkossev.commonLib, line 698
            else { // library marker kkossev.commonLib, line 699
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 700
            } // library marker kkossev.commonLib, line 701
            break // library marker kkossev.commonLib, line 702
        case 0x0004: // library marker kkossev.commonLib, line 703
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 704
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 705
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 706
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 707
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 708
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 709
            } // library marker kkossev.commonLib, line 710
            break // library marker kkossev.commonLib, line 711
        case 0x0005: // library marker kkossev.commonLib, line 712
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 713
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 714
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 715
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 716
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 717
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 718
            } // library marker kkossev.commonLib, line 719
            break // library marker kkossev.commonLib, line 720
        case 0x0007: // library marker kkossev.commonLib, line 721
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 722
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 723
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 724
            break // library marker kkossev.commonLib, line 725
        case 0xFFDF: // library marker kkossev.commonLib, line 726
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 727
            break // library marker kkossev.commonLib, line 728
        case 0xFFE2: // library marker kkossev.commonLib, line 729
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 730
            break // library marker kkossev.commonLib, line 731
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 732
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 733
            break // library marker kkossev.commonLib, line 734
        case 0xFFFE: // library marker kkossev.commonLib, line 735
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 736
            break // library marker kkossev.commonLib, line 737
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 738
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 739
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 740
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 741
            break // library marker kkossev.commonLib, line 742
        default: // library marker kkossev.commonLib, line 743
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 744
            break // library marker kkossev.commonLib, line 745
    } // library marker kkossev.commonLib, line 746
} // library marker kkossev.commonLib, line 747

/* // library marker kkossev.commonLib, line 749
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 750
 * power cluster            0x0001 // library marker kkossev.commonLib, line 751
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 752
*/ // library marker kkossev.commonLib, line 753
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 754
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 755
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 756
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 757
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 758
    } // library marker kkossev.commonLib, line 759

    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 761
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 762
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 763
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 764
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 765
        } // library marker kkossev.commonLib, line 766
    } // library marker kkossev.commonLib, line 767
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 768
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 769
    } // library marker kkossev.commonLib, line 770
    else { // library marker kkossev.commonLib, line 771
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 772
    } // library marker kkossev.commonLib, line 773
} // library marker kkossev.commonLib, line 774

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.commonLib, line 776
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 777
    Map result = [:] // library marker kkossev.commonLib, line 778
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.commonLib, line 779
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.commonLib, line 780
        BigDecimal minVolts = 2.2 // library marker kkossev.commonLib, line 781
        BigDecimal maxVolts = 3.2 // library marker kkossev.commonLib, line 782
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 783
        int roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 784
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 785
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 786
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 787
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 788
            result.name = 'battery' // library marker kkossev.commonLib, line 789
            result.unit  = '%' // library marker kkossev.commonLib, line 790
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 791
        } // library marker kkossev.commonLib, line 792
        else { // library marker kkossev.commonLib, line 793
            result.value = volts // library marker kkossev.commonLib, line 794
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 795
            result.unit  = 'V' // library marker kkossev.commonLib, line 796
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 797
        } // library marker kkossev.commonLib, line 798
        result.type = 'physical' // library marker kkossev.commonLib, line 799
        result.isStateChange = true // library marker kkossev.commonLib, line 800
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 801
        sendEvent(result) // library marker kkossev.commonLib, line 802
    } // library marker kkossev.commonLib, line 803
    else { // library marker kkossev.commonLib, line 804
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 805
    } // library marker kkossev.commonLib, line 806
} // library marker kkossev.commonLib, line 807

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.commonLib, line 809
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 810
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 811
        return // library marker kkossev.commonLib, line 812
    } // library marker kkossev.commonLib, line 813
    Map map = [:] // library marker kkossev.commonLib, line 814
    map.name = 'battery' // library marker kkossev.commonLib, line 815
    map.timeStamp = now() // library marker kkossev.commonLib, line 816
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 817
    map.unit  = '%' // library marker kkossev.commonLib, line 818
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 819
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 820
    map.isStateChange = true // library marker kkossev.commonLib, line 821
    // // library marker kkossev.commonLib, line 822
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.commonLib, line 823
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 824
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 825
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 826
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 827
        // send it now! // library marker kkossev.commonLib, line 828
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 829
    } // library marker kkossev.commonLib, line 830
    else { // library marker kkossev.commonLib, line 831
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 832
        map.delayed = delayedTime // library marker kkossev.commonLib, line 833
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 834
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 835
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 836
    } // library marker kkossev.commonLib, line 837
} // library marker kkossev.commonLib, line 838

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 840
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 841
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 842
    sendEvent(map) // library marker kkossev.commonLib, line 843
} // library marker kkossev.commonLib, line 844

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 846
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 847
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 848
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 849
    sendEvent(map) // library marker kkossev.commonLib, line 850
} // library marker kkossev.commonLib, line 851

/* // library marker kkossev.commonLib, line 853
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 854
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 855
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 856
*/ // library marker kkossev.commonLib, line 857
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 858
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 859
    logDebug 'unprocessed parseIdentityCluster' // library marker kkossev.commonLib, line 860
} // library marker kkossev.commonLib, line 861

/* // library marker kkossev.commonLib, line 863
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 864
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 865
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 866
*/ // library marker kkossev.commonLib, line 867
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 868
    if (this.respondsTo('customParseScenesCluster')) { // library marker kkossev.commonLib, line 869
        customParseScenesCluster(descMap) // library marker kkossev.commonLib, line 870
    } // library marker kkossev.commonLib, line 871
    else { // library marker kkossev.commonLib, line 872
        logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
} // library marker kkossev.commonLib, line 875

/* // library marker kkossev.commonLib, line 877
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 878
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 879
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 880
*/ // library marker kkossev.commonLib, line 881
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 882
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 883
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 884
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 885
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 886
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 887
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 888
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 889
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 890
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 891
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 892
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 893
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 894
            } // library marker kkossev.commonLib, line 895
            else { // library marker kkossev.commonLib, line 896
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 897
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 898
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 899
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 900
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 901
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 902
                        return // library marker kkossev.commonLib, line 903
                    } // library marker kkossev.commonLib, line 904
                } // library marker kkossev.commonLib, line 905
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 906
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.commonLib, line 907
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 908
            } // library marker kkossev.commonLib, line 909
            break // library marker kkossev.commonLib, line 910
        case 0x01: // View group // library marker kkossev.commonLib, line 911
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 912
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 913
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 914
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 915
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 916
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 917
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 918
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 919
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 920
            } // library marker kkossev.commonLib, line 921
            else { // library marker kkossev.commonLib, line 922
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 923
            } // library marker kkossev.commonLib, line 924
            break // library marker kkossev.commonLib, line 925
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 926
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 927
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 928
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 929
            final Set<String> groups = [] // library marker kkossev.commonLib, line 930
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 931
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 932
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 933
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 934
            } // library marker kkossev.commonLib, line 935
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 936
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 937
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 938
            break // library marker kkossev.commonLib, line 939
        case 0x03: // Remove group // library marker kkossev.commonLib, line 940
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 941
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 942
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 943
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 944
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 945
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 946
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 947
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 948
            } // library marker kkossev.commonLib, line 949
            else { // library marker kkossev.commonLib, line 950
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 951
            } // library marker kkossev.commonLib, line 952
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 953
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 954
            if (index >= 0) { // library marker kkossev.commonLib, line 955
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 956
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 957
            } // library marker kkossev.commonLib, line 958
            break // library marker kkossev.commonLib, line 959
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 960
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 961
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 962
            break // library marker kkossev.commonLib, line 963
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 964
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.commonLib, line 965
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 966
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 967
            break // library marker kkossev.commonLib, line 968
        default: // library marker kkossev.commonLib, line 969
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 970
            break // library marker kkossev.commonLib, line 971
    } // library marker kkossev.commonLib, line 972
} // library marker kkossev.commonLib, line 973

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 975
List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 976
    List<String> cmds = [] // library marker kkossev.commonLib, line 977
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 978
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 979
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 980
        return [] // library marker kkossev.commonLib, line 981
    } // library marker kkossev.commonLib, line 982
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 983
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 984
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 985
    return cmds // library marker kkossev.commonLib, line 986
} // library marker kkossev.commonLib, line 987

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 989
List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 990
    List<String> cmds = [] // library marker kkossev.commonLib, line 991
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 992
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 993
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 994
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 995
    return cmds // library marker kkossev.commonLib, line 996
} // library marker kkossev.commonLib, line 997

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 999
List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1000
    List<String> cmds = [] // library marker kkossev.commonLib, line 1001
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.commonLib, line 1002
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1003
    return cmds // library marker kkossev.commonLib, line 1004
} // library marker kkossev.commonLib, line 1005

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1007
List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1008
    List<String> cmds = [] // library marker kkossev.commonLib, line 1009
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1010
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1011
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1012
        return [] // library marker kkossev.commonLib, line 1013
    } // library marker kkossev.commonLib, line 1014
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1015
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1016
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1017
    return cmds // library marker kkossev.commonLib, line 1018
} // library marker kkossev.commonLib, line 1019

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1021
List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1022
    List<String> cmds = [] // library marker kkossev.commonLib, line 1023
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1024
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1025
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1026
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1027
    return cmds // library marker kkossev.commonLib, line 1028
} // library marker kkossev.commonLib, line 1029

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1031
List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1032
    List<String> cmds = [] // library marker kkossev.commonLib, line 1033
    //final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1034
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1035
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1036
    return cmds // library marker kkossev.commonLib, line 1037
} // library marker kkossev.commonLib, line 1038

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1040
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.commonLib, line 1041
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1042
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1043
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1044
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1045
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1046
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1047
] // library marker kkossev.commonLib, line 1048

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1050
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.commonLib, line 1051
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1052
    List<String> cmds = [] // library marker kkossev.commonLib, line 1053
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1054
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.commonLib, line 1055
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1056
    def value // library marker kkossev.commonLib, line 1057
    Boolean validated = false // library marker kkossev.commonLib, line 1058
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1059
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1060
        return // library marker kkossev.commonLib, line 1061
    } // library marker kkossev.commonLib, line 1062
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1063
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.commonLib, line 1064
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1065
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1066
        return // library marker kkossev.commonLib, line 1067
    } // library marker kkossev.commonLib, line 1068
    // // library marker kkossev.commonLib, line 1069
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1070
    def func // library marker kkossev.commonLib, line 1071
    try { // library marker kkossev.commonLib, line 1072
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1073
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1074
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1075
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1076
    } // library marker kkossev.commonLib, line 1077
    catch (e) { // library marker kkossev.commonLib, line 1078
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1079
        return // library marker kkossev.commonLib, line 1080
    } // library marker kkossev.commonLib, line 1081

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1083
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1084
} // library marker kkossev.commonLib, line 1085

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1087
void groupCommandsHelp(val) { // library marker kkossev.commonLib, line 1088
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.commonLib, line 1089
} // library marker kkossev.commonLib, line 1090

/* // library marker kkossev.commonLib, line 1092
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1093
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1094
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1095
*/ // library marker kkossev.commonLib, line 1096

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1098
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 1099
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 1100
    } // library marker kkossev.commonLib, line 1101
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1102
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1103
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1104
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1105
    } // library marker kkossev.commonLib, line 1106
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 1107
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1108
    } // library marker kkossev.commonLib, line 1109
    else { // library marker kkossev.commonLib, line 1110
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 1111
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 1112
    } // library marker kkossev.commonLib, line 1113
} // library marker kkossev.commonLib, line 1114

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 1116
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 1117
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1118

void toggle() { // library marker kkossev.commonLib, line 1120
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 1121
    String state = '' // library marker kkossev.commonLib, line 1122
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 1123
        state = 'on' // library marker kkossev.commonLib, line 1124
    } // library marker kkossev.commonLib, line 1125
    else { // library marker kkossev.commonLib, line 1126
        state = 'off' // library marker kkossev.commonLib, line 1127
    } // library marker kkossev.commonLib, line 1128
    descriptionText += state // library marker kkossev.commonLib, line 1129
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 1130
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1131
} // library marker kkossev.commonLib, line 1132

void off() { // library marker kkossev.commonLib, line 1134
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 1135
        customOff() // library marker kkossev.commonLib, line 1136
        return // library marker kkossev.commonLib, line 1137
    } // library marker kkossev.commonLib, line 1138
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1139
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1140
        return // library marker kkossev.commonLib, line 1141
    } // library marker kkossev.commonLib, line 1142
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 1143
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1144
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 1145
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1146
        if (currentState == 'off') { // library marker kkossev.commonLib, line 1147
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1148
        } // library marker kkossev.commonLib, line 1149
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1150
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1151
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1152
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1153
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1154
    } // library marker kkossev.commonLib, line 1155
    /* // library marker kkossev.commonLib, line 1156
    else { // library marker kkossev.commonLib, line 1157
        if (currentState != 'off') { // library marker kkossev.commonLib, line 1158
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1159
        } // library marker kkossev.commonLib, line 1160
        else { // library marker kkossev.commonLib, line 1161
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 1162
            return // library marker kkossev.commonLib, line 1163
        } // library marker kkossev.commonLib, line 1164
    } // library marker kkossev.commonLib, line 1165
    */ // library marker kkossev.commonLib, line 1166

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1168
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1169
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1170
} // library marker kkossev.commonLib, line 1171

void on() { // library marker kkossev.commonLib, line 1173
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 1174
        customOn() // library marker kkossev.commonLib, line 1175
        return // library marker kkossev.commonLib, line 1176
    } // library marker kkossev.commonLib, line 1177
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 1178
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1179
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 1180
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1181
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 1182
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1183
        } // library marker kkossev.commonLib, line 1184
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1185
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1186
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1187
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1188
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1189
    } // library marker kkossev.commonLib, line 1190
    /* // library marker kkossev.commonLib, line 1191
    else { // library marker kkossev.commonLib, line 1192
        if (currentState != 'on') { // library marker kkossev.commonLib, line 1193
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1194
        } // library marker kkossev.commonLib, line 1195
        else { // library marker kkossev.commonLib, line 1196
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 1197
            return // library marker kkossev.commonLib, line 1198
        } // library marker kkossev.commonLib, line 1199
    } // library marker kkossev.commonLib, line 1200
    */ // library marker kkossev.commonLib, line 1201
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1202
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1203
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1204
} // library marker kkossev.commonLib, line 1205

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 1207
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 1208
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 1209
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 1210
    } // library marker kkossev.commonLib, line 1211
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1212
    Map map = [:] // library marker kkossev.commonLib, line 1213
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 1214
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 1215
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 1216
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1217
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1218
        return // library marker kkossev.commonLib, line 1219
    } // library marker kkossev.commonLib, line 1220
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1221
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 1222
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1223
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 1224
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1225
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1226
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 1227
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1228
    } else { // library marker kkossev.commonLib, line 1229
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1230
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1231
    } // library marker kkossev.commonLib, line 1232
    map.name = 'switch' // library marker kkossev.commonLib, line 1233
    map.value = value // library marker kkossev.commonLib, line 1234
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1235
    if (isRefresh) { // library marker kkossev.commonLib, line 1236
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1237
        map.isStateChange = true // library marker kkossev.commonLib, line 1238
    } else { // library marker kkossev.commonLib, line 1239
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1240
    } // library marker kkossev.commonLib, line 1241
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1242
    sendEvent(map) // library marker kkossev.commonLib, line 1243
    clearIsDigital() // library marker kkossev.commonLib, line 1244
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 1245
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 1246
    } // library marker kkossev.commonLib, line 1247
} // library marker kkossev.commonLib, line 1248

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 1250
    '0': 'switch off', // library marker kkossev.commonLib, line 1251
    '1': 'switch on', // library marker kkossev.commonLib, line 1252
    '2': 'switch last state' // library marker kkossev.commonLib, line 1253
] // library marker kkossev.commonLib, line 1254

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 1256
    '0': 'toggle', // library marker kkossev.commonLib, line 1257
    '1': 'state', // library marker kkossev.commonLib, line 1258
    '2': 'momentary' // library marker kkossev.commonLib, line 1259
] // library marker kkossev.commonLib, line 1260

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 1262
    Map descMap = [:] // library marker kkossev.commonLib, line 1263
    try { // library marker kkossev.commonLib, line 1264
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1265
    } // library marker kkossev.commonLib, line 1266
    catch (e1) { // library marker kkossev.commonLib, line 1267
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1268
        // try alternative custom parsing // library marker kkossev.commonLib, line 1269
        descMap = [:] // library marker kkossev.commonLib, line 1270
        try { // library marker kkossev.commonLib, line 1271
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1272
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1273
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1274
            } // library marker kkossev.commonLib, line 1275
        } // library marker kkossev.commonLib, line 1276
        catch (e2) { // library marker kkossev.commonLib, line 1277
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1278
            return [:] // library marker kkossev.commonLib, line 1279
        } // library marker kkossev.commonLib, line 1280
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1281
    } // library marker kkossev.commonLib, line 1282
    return descMap // library marker kkossev.commonLib, line 1283
} // library marker kkossev.commonLib, line 1284

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1286
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1287
        return false // library marker kkossev.commonLib, line 1288
    } // library marker kkossev.commonLib, line 1289
    // try to parse ... // library marker kkossev.commonLib, line 1290
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1291
    Map descMap = [:] // library marker kkossev.commonLib, line 1292
    try { // library marker kkossev.commonLib, line 1293
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1294
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1295
    } // library marker kkossev.commonLib, line 1296
    catch (e) { // library marker kkossev.commonLib, line 1297
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1298
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1299
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1300
        return true // library marker kkossev.commonLib, line 1301
    } // library marker kkossev.commonLib, line 1302

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1304
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1305
    } // library marker kkossev.commonLib, line 1306
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1307
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1308
    } // library marker kkossev.commonLib, line 1309
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1310
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1311
    } // library marker kkossev.commonLib, line 1312
    else { // library marker kkossev.commonLib, line 1313
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1314
        return false // library marker kkossev.commonLib, line 1315
    } // library marker kkossev.commonLib, line 1316
    return true    // processed // library marker kkossev.commonLib, line 1317
} // library marker kkossev.commonLib, line 1318

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1320
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 1321
  /* // library marker kkossev.commonLib, line 1322
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1323
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1324
        return true // library marker kkossev.commonLib, line 1325
    } // library marker kkossev.commonLib, line 1326
*/ // library marker kkossev.commonLib, line 1327
    Map descMap = [:] // library marker kkossev.commonLib, line 1328
    try { // library marker kkossev.commonLib, line 1329
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1330
    } // library marker kkossev.commonLib, line 1331
    catch (e1) { // library marker kkossev.commonLib, line 1332
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1333
        // try alternative custom parsing // library marker kkossev.commonLib, line 1334
        descMap = [:] // library marker kkossev.commonLib, line 1335
        try { // library marker kkossev.commonLib, line 1336
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1337
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1338
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1339
            } // library marker kkossev.commonLib, line 1340
        } // library marker kkossev.commonLib, line 1341
        catch (e2) { // library marker kkossev.commonLib, line 1342
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1343
            return true // library marker kkossev.commonLib, line 1344
        } // library marker kkossev.commonLib, line 1345
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1346
    } // library marker kkossev.commonLib, line 1347
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1348
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1349
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1350
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1351
        return false // library marker kkossev.commonLib, line 1352
    } // library marker kkossev.commonLib, line 1353
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1354
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1355
    // attribute report received // library marker kkossev.commonLib, line 1356
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1357
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1358
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1359
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1360
    } // library marker kkossev.commonLib, line 1361
    attrData.each { // library marker kkossev.commonLib, line 1362
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1363
        //def map = [:] // library marker kkossev.commonLib, line 1364
        if (it.status == '86') { // library marker kkossev.commonLib, line 1365
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1366
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1367
        } // library marker kkossev.commonLib, line 1368
        switch (it.cluster) { // library marker kkossev.commonLib, line 1369
            case '0000' : // library marker kkossev.commonLib, line 1370
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1371
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1372
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1373
                } // library marker kkossev.commonLib, line 1374
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1375
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1376
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1377
                } // library marker kkossev.commonLib, line 1378
                else { // library marker kkossev.commonLib, line 1379
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1380
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1381
                } // library marker kkossev.commonLib, line 1382
                break // library marker kkossev.commonLib, line 1383
            default : // library marker kkossev.commonLib, line 1384
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1385
                break // library marker kkossev.commonLib, line 1386
        } // switch // library marker kkossev.commonLib, line 1387
    } // for each attribute // library marker kkossev.commonLib, line 1388
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1389
} // library marker kkossev.commonLib, line 1390

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1392

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1394
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1395
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1396
    def mode // library marker kkossev.commonLib, line 1397
    String attrName // library marker kkossev.commonLib, line 1398
    if (it.value == null) { // library marker kkossev.commonLib, line 1399
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1400
        return // library marker kkossev.commonLib, line 1401
    } // library marker kkossev.commonLib, line 1402
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1403
    switch (it.attrId) { // library marker kkossev.commonLib, line 1404
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1405
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1406
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1407
            break // library marker kkossev.commonLib, line 1408
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1409
            attrName = 'On Time' // library marker kkossev.commonLib, line 1410
            mode = value // library marker kkossev.commonLib, line 1411
            break // library marker kkossev.commonLib, line 1412
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1413
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1414
            mode = value // library marker kkossev.commonLib, line 1415
            break // library marker kkossev.commonLib, line 1416
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1417
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1418
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1419
            break // library marker kkossev.commonLib, line 1420
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1421
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1422
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1423
            break // library marker kkossev.commonLib, line 1424
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1425
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1426
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1427
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1428
            } // library marker kkossev.commonLib, line 1429
            else { // library marker kkossev.commonLib, line 1430
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1431
            } // library marker kkossev.commonLib, line 1432
            break // library marker kkossev.commonLib, line 1433
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1434
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1435
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1436
            break // library marker kkossev.commonLib, line 1437
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1438
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1439
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1440
            break // library marker kkossev.commonLib, line 1441
        default : // library marker kkossev.commonLib, line 1442
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1443
            return // library marker kkossev.commonLib, line 1444
    } // library marker kkossev.commonLib, line 1445
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1446
} // library marker kkossev.commonLib, line 1447

/* // library marker kkossev.commonLib, line 1449
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1450
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1451
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1452
*/ // library marker kkossev.commonLib, line 1453
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1454
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1455
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1456
    } // library marker kkossev.commonLib, line 1457
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1458
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1459
    } // library marker kkossev.commonLib, line 1460
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1461
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1462
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1463
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1464
    } // library marker kkossev.commonLib, line 1465
    else { // library marker kkossev.commonLib, line 1466
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1467
    } // library marker kkossev.commonLib, line 1468
} // library marker kkossev.commonLib, line 1469

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.commonLib, line 1471
    int value = rawValue as int // library marker kkossev.commonLib, line 1472
    if (value < 0) { value = 0 } // library marker kkossev.commonLib, line 1473
    if (value > 100) { value = 100 } // library marker kkossev.commonLib, line 1474
    Map map = [:] // library marker kkossev.commonLib, line 1475

    boolean isDigital = state.states['isDigital'] // library marker kkossev.commonLib, line 1477
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1478

    map.name = 'level' // library marker kkossev.commonLib, line 1480
    map.value = value // library marker kkossev.commonLib, line 1481
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1482
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1483
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1484
        map.isStateChange = true // library marker kkossev.commonLib, line 1485
    } // library marker kkossev.commonLib, line 1486
    else { // library marker kkossev.commonLib, line 1487
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1488
    } // library marker kkossev.commonLib, line 1489
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1490
    sendEvent(map) // library marker kkossev.commonLib, line 1491
    clearIsDigital() // library marker kkossev.commonLib, line 1492
} // library marker kkossev.commonLib, line 1493

/** // library marker kkossev.commonLib, line 1495
 * Get the level transition rate // library marker kkossev.commonLib, line 1496
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1497
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1498
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1499
 */ // library marker kkossev.commonLib, line 1500
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1501
    int rate = 0 // library marker kkossev.commonLib, line 1502
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1503
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1504
    if (!isOn) { // library marker kkossev.commonLib, line 1505
        currentLevel = 0 // library marker kkossev.commonLib, line 1506
    } // library marker kkossev.commonLib, line 1507
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1508
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1509
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1510
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1511
    } else { // library marker kkossev.commonLib, line 1512
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1513
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1514
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1515
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1516
        } // library marker kkossev.commonLib, line 1517
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1518
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1519
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1520
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1521
        } // library marker kkossev.commonLib, line 1522
    } // library marker kkossev.commonLib, line 1523
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1524
    return rate // library marker kkossev.commonLib, line 1525
} // library marker kkossev.commonLib, line 1526

// Command option that enable changes when off // library marker kkossev.commonLib, line 1528
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1529

/** // library marker kkossev.commonLib, line 1531
 * Constrain a value to a range // library marker kkossev.commonLib, line 1532
 * @param value value to constrain // library marker kkossev.commonLib, line 1533
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1534
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1535
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1536
 */ // library marker kkossev.commonLib, line 1537
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1538
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1539
        return value // library marker kkossev.commonLib, line 1540
    } // library marker kkossev.commonLib, line 1541
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1542
} // library marker kkossev.commonLib, line 1543

/** // library marker kkossev.commonLib, line 1545
 * Constrain a value to a range // library marker kkossev.commonLib, line 1546
 * @param value value to constrain // library marker kkossev.commonLib, line 1547
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1548
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1549
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1550
 */ // library marker kkossev.commonLib, line 1551
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1552
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1553
        return value as Integer // library marker kkossev.commonLib, line 1554
    } // library marker kkossev.commonLib, line 1555
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1556
} // library marker kkossev.commonLib, line 1557

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1559
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1560

/** // library marker kkossev.commonLib, line 1562
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1563
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1564
 * @param commands commands to execute // library marker kkossev.commonLib, line 1565
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1566
 */ // library marker kkossev.commonLib, line 1567
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1568
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1569
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1570
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1571
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1572
    } // library marker kkossev.commonLib, line 1573
    return [] // library marker kkossev.commonLib, line 1574
} // library marker kkossev.commonLib, line 1575

def intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1577
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1578
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1579
} // library marker kkossev.commonLib, line 1580

def intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1582
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1583
} // library marker kkossev.commonLib, line 1584

/** // library marker kkossev.commonLib, line 1586
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1587
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1588
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1589
 */ // library marker kkossev.commonLib, line 1590
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.commonLib, line 1591
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1592
    List<String> cmds = [] // library marker kkossev.commonLib, line 1593
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1594
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1595
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1596
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1597
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1598
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1599
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1600
    } // library marker kkossev.commonLib, line 1601
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1602
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1603
    /* // library marker kkossev.commonLib, line 1604
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1605
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1606
    */ // library marker kkossev.commonLib, line 1607
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1608
    String endpointId = '01'     // TODO !!! // library marker kkossev.commonLib, line 1609
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1610

    return cmds // library marker kkossev.commonLib, line 1612
} // library marker kkossev.commonLib, line 1613

/** // library marker kkossev.commonLib, line 1615
 * Set Level Command // library marker kkossev.commonLib, line 1616
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1617
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1618
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1619
 */ // library marker kkossev.commonLib, line 1620
void setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1621
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1622
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.commonLib, line 1623
        customSetLevel(value, transitionTime) // library marker kkossev.commonLib, line 1624
        return // library marker kkossev.commonLib, line 1625
    } // library marker kkossev.commonLib, line 1626
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1627
    final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1628
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1629
    sendZigbeeCommands(setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1630
} // library marker kkossev.commonLib, line 1631

/* // library marker kkossev.commonLib, line 1633
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1634
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1635
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1636
*/ // library marker kkossev.commonLib, line 1637
void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1638
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1639
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1640
    } // library marker kkossev.commonLib, line 1641
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1642
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1643
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1644
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1645
    } // library marker kkossev.commonLib, line 1646
    else { // library marker kkossev.commonLib, line 1647
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1648
    } // library marker kkossev.commonLib, line 1649
} // library marker kkossev.commonLib, line 1650

/* // library marker kkossev.commonLib, line 1652
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1653
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1654
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1655
*/ // library marker kkossev.commonLib, line 1656
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1657
    if (this.respondsTo('customParseIlluminanceCluster')) { // library marker kkossev.commonLib, line 1658
        customParseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 1659
    } // library marker kkossev.commonLib, line 1660
    else { // library marker kkossev.commonLib, line 1661
        logWarn "unprocessed Illuminance attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1662
    } // library marker kkossev.commonLib, line 1663
} // library marker kkossev.commonLib, line 1664


/* // library marker kkossev.commonLib, line 1667
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1668
 * temperature // library marker kkossev.commonLib, line 1669
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1670
*/ // library marker kkossev.commonLib, line 1671
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1672
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1673
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1674
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1675
} // library marker kkossev.commonLib, line 1676

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1678
    Map eventMap = [:] // library marker kkossev.commonLib, line 1679
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1680
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1681
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1682
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1683
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1684
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1685
    } // library marker kkossev.commonLib, line 1686
    else { // library marker kkossev.commonLib, line 1687
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1688
    } // library marker kkossev.commonLib, line 1689
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1690
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1691
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1692
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.commonLib, line 1693
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.commonLib, line 1694
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1695
        return // library marker kkossev.commonLib, line 1696
    } // library marker kkossev.commonLib, line 1697
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1698
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1699
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1700
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1701
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1702
    } // library marker kkossev.commonLib, line 1703
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1704
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1705
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1706
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1707
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1708
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1709
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1710
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1711
    } // library marker kkossev.commonLib, line 1712
    else {         // queue the event // library marker kkossev.commonLib, line 1713
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1714
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1715
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1716
    } // library marker kkossev.commonLib, line 1717
} // library marker kkossev.commonLib, line 1718

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1720
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1721
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1722
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1723
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1724
} // library marker kkossev.commonLib, line 1725

/* // library marker kkossev.commonLib, line 1727
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1728
 * humidity // library marker kkossev.commonLib, line 1729
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1730
*/ // library marker kkossev.commonLib, line 1731
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1732
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1733
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1734
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1735
} // library marker kkossev.commonLib, line 1736

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1738
    Map eventMap = [:] // library marker kkossev.commonLib, line 1739
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.commonLib, line 1740
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1741
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1742
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.commonLib, line 1743
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.commonLib, line 1744
        return // library marker kkossev.commonLib, line 1745
    } // library marker kkossev.commonLib, line 1746
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1747
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1748
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1749
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1750
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1751
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1752
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1753
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1754
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1755
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1756
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1757
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1758
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1759
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1760
    } // library marker kkossev.commonLib, line 1761
    else { // library marker kkossev.commonLib, line 1762
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1763
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1764
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1765
    } // library marker kkossev.commonLib, line 1766
} // library marker kkossev.commonLib, line 1767

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1769
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1770
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1771
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1772
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1773
} // library marker kkossev.commonLib, line 1774

/* // library marker kkossev.commonLib, line 1776
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1777
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1778
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1779
*/ // library marker kkossev.commonLib, line 1780

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1782
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { // library marker kkossev.commonLib, line 1783
        logWarn 'parseElectricalMeasureCluster is NOT implemented1' // library marker kkossev.commonLib, line 1784
    } // library marker kkossev.commonLib, line 1785
} // library marker kkossev.commonLib, line 1786

/* // library marker kkossev.commonLib, line 1788
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1789
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1790
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1791
*/ // library marker kkossev.commonLib, line 1792
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1793
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { // library marker kkossev.commonLib, line 1794
        logWarn 'parseMeteringCluster is NOT implemented1' // library marker kkossev.commonLib, line 1795
    } // library marker kkossev.commonLib, line 1796
} // library marker kkossev.commonLib, line 1797

/* // library marker kkossev.commonLib, line 1799
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1800
 * pm2.5 // library marker kkossev.commonLib, line 1801
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1802
*/ // library marker kkossev.commonLib, line 1803
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1804
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1805
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1806
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1807
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1808
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1809
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1810
    } // library marker kkossev.commonLib, line 1811
    else { // library marker kkossev.commonLib, line 1812
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1813
    } // library marker kkossev.commonLib, line 1814
} // library marker kkossev.commonLib, line 1815

/* // library marker kkossev.commonLib, line 1817
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1818
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1819
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1820
*/ // library marker kkossev.commonLib, line 1821
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1822
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1823
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1824
    } // library marker kkossev.commonLib, line 1825
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1826
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1827
    } // library marker kkossev.commonLib, line 1828
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1829
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1830
    } // library marker kkossev.commonLib, line 1831
    else { // library marker kkossev.commonLib, line 1832
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1833
    } // library marker kkossev.commonLib, line 1834
} // library marker kkossev.commonLib, line 1835

/* // library marker kkossev.commonLib, line 1837
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1838
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1839
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1840
*/ // library marker kkossev.commonLib, line 1841
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1842
    if (this.respondsTo('customParseMultistateInputCluster')) { // library marker kkossev.commonLib, line 1843
        customParseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 1844
    } // library marker kkossev.commonLib, line 1845
    else { // library marker kkossev.commonLib, line 1846
        logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1847
    } // library marker kkossev.commonLib, line 1848
} // library marker kkossev.commonLib, line 1849

/* // library marker kkossev.commonLib, line 1851
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1852
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1853
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1854
*/ // library marker kkossev.commonLib, line 1855
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1856
    if (this.respondsTo('customParseWindowCoveringCluster')) { // library marker kkossev.commonLib, line 1857
        customParseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 1858
    } // library marker kkossev.commonLib, line 1859
    else { // library marker kkossev.commonLib, line 1860
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1861
    } // library marker kkossev.commonLib, line 1862
} // library marker kkossev.commonLib, line 1863

/* // library marker kkossev.commonLib, line 1865
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1866
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1867
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1868
*/ // library marker kkossev.commonLib, line 1869
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1870
    if (this.respondsTo('customParseThermostatCluster')) { // library marker kkossev.commonLib, line 1871
        customParseThermostatCluster(descMap) // library marker kkossev.commonLib, line 1872
    } // library marker kkossev.commonLib, line 1873
    else { // library marker kkossev.commonLib, line 1874
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1875
    } // library marker kkossev.commonLib, line 1876
} // library marker kkossev.commonLib, line 1877

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1879

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1881
    if (this.respondsTo('customParseFC11Cluster')) { // library marker kkossev.commonLib, line 1882
        customParseFC11Cluster(descMap) // library marker kkossev.commonLib, line 1883
    } // library marker kkossev.commonLib, line 1884
    else { // library marker kkossev.commonLib, line 1885
        logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1886
    } // library marker kkossev.commonLib, line 1887
} // library marker kkossev.commonLib, line 1888

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1890
    if (this.respondsTo('customParseE002Cluster')) { // library marker kkossev.commonLib, line 1891
        customParseE002Cluster(descMap) // library marker kkossev.commonLib, line 1892
    } // library marker kkossev.commonLib, line 1893
    else { // library marker kkossev.commonLib, line 1894
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 1895
    } // library marker kkossev.commonLib, line 1896
} // library marker kkossev.commonLib, line 1897

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1899
    if (this.respondsTo('customParseEC03Cluster')) { // library marker kkossev.commonLib, line 1900
        customParseEC03Cluster(descMap) // library marker kkossev.commonLib, line 1901
    } // library marker kkossev.commonLib, line 1902
    else { // library marker kkossev.commonLib, line 1903
        logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 1904
    } // library marker kkossev.commonLib, line 1905
} // library marker kkossev.commonLib, line 1906

/* // library marker kkossev.commonLib, line 1908
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1909
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1910
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1911
*/ // library marker kkossev.commonLib, line 1912
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1913
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1914
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1915

// Tuya Commands // library marker kkossev.commonLib, line 1917
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1918
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1919
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1920
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1921
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1922

// tuya DP type // library marker kkossev.commonLib, line 1924
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1925
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1926
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1927
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1928
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1929
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1930

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1932
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1933
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 1934
        Long offset = 0 // library marker kkossev.commonLib, line 1935
        try { // library marker kkossev.commonLib, line 1936
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1937
        } // library marker kkossev.commonLib, line 1938
        catch (e) { // library marker kkossev.commonLib, line 1939
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 1940
        } // library marker kkossev.commonLib, line 1941
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1942
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 1943
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 1944
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 1945
    } // library marker kkossev.commonLib, line 1946
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1947
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1948
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1949
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1950
        if (status != '00') { // library marker kkossev.commonLib, line 1951
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1952
        } // library marker kkossev.commonLib, line 1953
    } // library marker kkossev.commonLib, line 1954
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1955
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1956
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1957
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1958
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1959
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1960
            return // library marker kkossev.commonLib, line 1961
        } // library marker kkossev.commonLib, line 1962
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1963
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1964
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1965
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1966
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1967
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1968
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1969
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1970
        } // library marker kkossev.commonLib, line 1971
    } // library marker kkossev.commonLib, line 1972
    else { // library marker kkossev.commonLib, line 1973
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 1974
    } // library marker kkossev.commonLib, line 1975
} // library marker kkossev.commonLib, line 1976

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1978
    log.trace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1979
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1980
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1981
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1982
            return // library marker kkossev.commonLib, line 1983
        } // library marker kkossev.commonLib, line 1984
    } // library marker kkossev.commonLib, line 1985
    // check if the method  method exists // library marker kkossev.commonLib, line 1986
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1987
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1988
            return // library marker kkossev.commonLib, line 1989
        } // library marker kkossev.commonLib, line 1990
    } // library marker kkossev.commonLib, line 1991
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1992
} // library marker kkossev.commonLib, line 1993

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1995
    int retValue = 0 // library marker kkossev.commonLib, line 1996
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1997
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1998
        int power = 1 // library marker kkossev.commonLib, line 1999
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2000
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 2001
            power = power * 256 // library marker kkossev.commonLib, line 2002
        } // library marker kkossev.commonLib, line 2003
    } // library marker kkossev.commonLib, line 2004
    return retValue // library marker kkossev.commonLib, line 2005
} // library marker kkossev.commonLib, line 2006

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 2008
    List<String> cmds = [] // library marker kkossev.commonLib, line 2009
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2010
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2011
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2012
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 2013
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2014
    return cmds // library marker kkossev.commonLib, line 2015
} // library marker kkossev.commonLib, line 2016

private getPACKET_ID() { // library marker kkossev.commonLib, line 2018
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 2019
} // library marker kkossev.commonLib, line 2020

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2022
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 2023
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2024
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2025
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 2026
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2027
} // library marker kkossev.commonLib, line 2028

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2030
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2031

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 2033
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2034
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2035
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 2036
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 2037
} // library marker kkossev.commonLib, line 2038

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2040
    List<String> cmds = [] // library marker kkossev.commonLib, line 2041
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2042
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 2043
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2044
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2045
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2046
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2047
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 2048
        } // library marker kkossev.commonLib, line 2049
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2050
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 2051
    } // library marker kkossev.commonLib, line 2052
    else { // library marker kkossev.commonLib, line 2053
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 2054
    } // library marker kkossev.commonLib, line 2055
} // library marker kkossev.commonLib, line 2056

/** // library marker kkossev.commonLib, line 2058
 * initializes the device // library marker kkossev.commonLib, line 2059
 * Invoked from configure() // library marker kkossev.commonLib, line 2060
 * @return zigbee commands // library marker kkossev.commonLib, line 2061
 */ // library marker kkossev.commonLib, line 2062
List<String> initializeDevice() { // library marker kkossev.commonLib, line 2063
    List<String> cmds = [] // library marker kkossev.commonLib, line 2064
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2065

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2067
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 2068
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 2069
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 2070
    } // library marker kkossev.commonLib, line 2071
    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2072
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2073
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2074
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2075
    } // library marker kkossev.commonLib, line 2076
    // // library marker kkossev.commonLib, line 2077
    return cmds // library marker kkossev.commonLib, line 2078
} // library marker kkossev.commonLib, line 2079

/** // library marker kkossev.commonLib, line 2081
 * configures the device // library marker kkossev.commonLib, line 2082
 * Invoked from configure() // library marker kkossev.commonLib, line 2083
 * @return zigbee commands // library marker kkossev.commonLib, line 2084
 */ // library marker kkossev.commonLib, line 2085
List<String> configureDevice() { // library marker kkossev.commonLib, line 2086
    List<String> cmds = [] // library marker kkossev.commonLib, line 2087
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2088

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 2090
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 2091
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 2092
    } // library marker kkossev.commonLib, line 2093
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2094
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 2095
    return cmds // library marker kkossev.commonLib, line 2096
} // library marker kkossev.commonLib, line 2097

/* // library marker kkossev.commonLib, line 2099
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2100
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2101
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2102
*/ // library marker kkossev.commonLib, line 2103

void refresh() { // library marker kkossev.commonLib, line 2105
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2106
    checkDriverVersion() // library marker kkossev.commonLib, line 2107
    List<String> cmds = [] // library marker kkossev.commonLib, line 2108
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2109

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2111
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 2112
        cmds += customRefresh() // library marker kkossev.commonLib, line 2113
    } // library marker kkossev.commonLib, line 2114
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2115
    else { // library marker kkossev.commonLib, line 2116
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 2117
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 2118
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 2119
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 2120
        } // library marker kkossev.commonLib, line 2121
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2122
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2123
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2124
        } // library marker kkossev.commonLib, line 2125
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2126
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2127
        } // library marker kkossev.commonLib, line 2128
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2129
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2130
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2131
        } // library marker kkossev.commonLib, line 2132
    } // library marker kkossev.commonLib, line 2133

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2135
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2136
    } // library marker kkossev.commonLib, line 2137
    else { // library marker kkossev.commonLib, line 2138
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2139
    } // library marker kkossev.commonLib, line 2140
} // library marker kkossev.commonLib, line 2141

/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2143
void setRefreshRequest()   { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 2144
/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2145
void clearRefreshRequest() { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 2146

void clearInfoEvent() { // library marker kkossev.commonLib, line 2148
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2149
} // library marker kkossev.commonLib, line 2150

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2152
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 2153
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 2154
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 2155
    } // library marker kkossev.commonLib, line 2156
    else { // library marker kkossev.commonLib, line 2157
        logInfo "${info}" // library marker kkossev.commonLib, line 2158
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 2159
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2160
    } // library marker kkossev.commonLib, line 2161
} // library marker kkossev.commonLib, line 2162

void ping() { // library marker kkossev.commonLib, line 2164
    if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2165
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2166
        logInfo 'ping() command is not available for this sleepy device.' // library marker kkossev.commonLib, line 2167
        sendRttEvent('n/a') // library marker kkossev.commonLib, line 2168
    } // library marker kkossev.commonLib, line 2169
    else { // library marker kkossev.commonLib, line 2170
        if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2171
        state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 2172
        //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 2173
        state.states['isPing'] = true // library marker kkossev.commonLib, line 2174
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2175
        if (isVirtual()) { // library marker kkossev.commonLib, line 2176
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2177
        } // library marker kkossev.commonLib, line 2178
        else { // library marker kkossev.commonLib, line 2179
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2180
        } // library marker kkossev.commonLib, line 2181
        logDebug 'ping...' // library marker kkossev.commonLib, line 2182
    } // library marker kkossev.commonLib, line 2183
} // library marker kkossev.commonLib, line 2184

def virtualPong() { // library marker kkossev.commonLib, line 2186
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2187
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2188
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 2189
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 2190
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 2191
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 2192
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 2193
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 2194
        sendRttEvent() // library marker kkossev.commonLib, line 2195
    } // library marker kkossev.commonLib, line 2196
    else { // library marker kkossev.commonLib, line 2197
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 2198
    } // library marker kkossev.commonLib, line 2199
    state.states['isPing'] = false // library marker kkossev.commonLib, line 2200
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 2201
} // library marker kkossev.commonLib, line 2202

/** // library marker kkossev.commonLib, line 2204
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2205
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2206
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2207
 * @return none // library marker kkossev.commonLib, line 2208
 */ // library marker kkossev.commonLib, line 2209
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2210
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2211
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2212
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 2213
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 2214
    if (value == null) { // library marker kkossev.commonLib, line 2215
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2216
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 2217
    } // library marker kkossev.commonLib, line 2218
    else { // library marker kkossev.commonLib, line 2219
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2220
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2221
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 2222
    } // library marker kkossev.commonLib, line 2223
} // library marker kkossev.commonLib, line 2224

/** // library marker kkossev.commonLib, line 2226
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2227
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2228
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2229
 */ // library marker kkossev.commonLib, line 2230
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2231
    if (cluster != null) { // library marker kkossev.commonLib, line 2232
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2233
    } // library marker kkossev.commonLib, line 2234
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 2235
    return 'NULL' // library marker kkossev.commonLib, line 2236
} // library marker kkossev.commonLib, line 2237

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2239
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2240
} // library marker kkossev.commonLib, line 2241

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2243
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2244
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 2245
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2246
} // library marker kkossev.commonLib, line 2247

/** // library marker kkossev.commonLib, line 2249
 * Schedule a device health check // library marker kkossev.commonLib, line 2250
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2251
 */ // library marker kkossev.commonLib, line 2252
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2253
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2254
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 2255
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2256
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2257
    } // library marker kkossev.commonLib, line 2258
    else { // library marker kkossev.commonLib, line 2259
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 2260
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2261
    } // library marker kkossev.commonLib, line 2262
} // library marker kkossev.commonLib, line 2263

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2265
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2266
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2267
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 2268
} // library marker kkossev.commonLib, line 2269

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2271
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2272
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2273
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2274
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 2275
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2276
        logInfo 'is now online!' // library marker kkossev.commonLib, line 2277
    } // library marker kkossev.commonLib, line 2278
} // library marker kkossev.commonLib, line 2279

void deviceHealthCheck() { // library marker kkossev.commonLib, line 2281
    checkDriverVersion() // library marker kkossev.commonLib, line 2282
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2283
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2284
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2285
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2286
            logWarn 'not present!' // library marker kkossev.commonLib, line 2287
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2288
        } // library marker kkossev.commonLib, line 2289
    } // library marker kkossev.commonLib, line 2290
    else { // library marker kkossev.commonLib, line 2291
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2292
    } // library marker kkossev.commonLib, line 2293
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2294
} // library marker kkossev.commonLib, line 2295

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 2297
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2298
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2299
    if (value == 'online') { // library marker kkossev.commonLib, line 2300
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2301
    } // library marker kkossev.commonLib, line 2302
    else { // library marker kkossev.commonLib, line 2303
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2304
    } // library marker kkossev.commonLib, line 2305
} // library marker kkossev.commonLib, line 2306

/** // library marker kkossev.commonLib, line 2308
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2309
 */ // library marker kkossev.commonLib, line 2310
void autoPoll() { // library marker kkossev.commonLib, line 2311
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 2312
    checkDriverVersion() // library marker kkossev.commonLib, line 2313
    List<String> cmds = [] // library marker kkossev.commonLib, line 2314
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 2315
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2316
    } // library marker kkossev.commonLib, line 2317

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2319
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2320
    } // library marker kkossev.commonLib, line 2321
} // library marker kkossev.commonLib, line 2322

/** // library marker kkossev.commonLib, line 2324
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2325
 */ // library marker kkossev.commonLib, line 2326
void updated() { // library marker kkossev.commonLib, line 2327
    logInfo 'updated()...' // library marker kkossev.commonLib, line 2328
    checkDriverVersion() // library marker kkossev.commonLib, line 2329
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2330
    unschedule() // library marker kkossev.commonLib, line 2331

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2333
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2334
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2335
    } // library marker kkossev.commonLib, line 2336
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2337
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2338
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2339
    } // library marker kkossev.commonLib, line 2340

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2342
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2343
        // schedule the periodic timer // library marker kkossev.commonLib, line 2344
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2345
        if (interval > 0) { // library marker kkossev.commonLib, line 2346
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2347
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2348
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2349
        } // library marker kkossev.commonLib, line 2350
    } // library marker kkossev.commonLib, line 2351
    else { // library marker kkossev.commonLib, line 2352
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2353
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 2354
    } // library marker kkossev.commonLib, line 2355
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 2356
        customUpdated() // library marker kkossev.commonLib, line 2357
    } // library marker kkossev.commonLib, line 2358

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 2360
} // library marker kkossev.commonLib, line 2361

/** // library marker kkossev.commonLib, line 2363
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2364
 */ // library marker kkossev.commonLib, line 2365
void logsOff() { // library marker kkossev.commonLib, line 2366
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 2367
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2368
} // library marker kkossev.commonLib, line 2369
void traceOff() { // library marker kkossev.commonLib, line 2370
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 2371
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2372
} // library marker kkossev.commonLib, line 2373

void configure(String command) { // library marker kkossev.commonLib, line 2375
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2376
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2377
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2378
        return // library marker kkossev.commonLib, line 2379
    } // library marker kkossev.commonLib, line 2380
    // // library marker kkossev.commonLib, line 2381
    String func // library marker kkossev.commonLib, line 2382
    try { // library marker kkossev.commonLib, line 2383
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2384
        "$func"() // library marker kkossev.commonLib, line 2385
    } // library marker kkossev.commonLib, line 2386
    catch (e) { // library marker kkossev.commonLib, line 2387
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2388
        return // library marker kkossev.commonLib, line 2389
    } // library marker kkossev.commonLib, line 2390
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2391
} // library marker kkossev.commonLib, line 2392

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 2394
void configureHelp(final String val) { // library marker kkossev.commonLib, line 2395
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2396
} // library marker kkossev.commonLib, line 2397

void loadAllDefaults() { // library marker kkossev.commonLib, line 2399
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 2400
    deleteAllSettings() // library marker kkossev.commonLib, line 2401
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2402
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2403
    deleteAllStates() // library marker kkossev.commonLib, line 2404
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2405
    initialize() // library marker kkossev.commonLib, line 2406
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 2407
    updated() // library marker kkossev.commonLib, line 2408
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 2409
} // library marker kkossev.commonLib, line 2410

void configureNow() { // library marker kkossev.commonLib, line 2412
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 2413
} // library marker kkossev.commonLib, line 2414

/** // library marker kkossev.commonLib, line 2416
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2417
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 2418
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2419
 */ // library marker kkossev.commonLib, line 2420
List<String> configure() { // library marker kkossev.commonLib, line 2421
    List<String> cmds = [] // library marker kkossev.commonLib, line 2422
    logInfo 'configure...' // library marker kkossev.commonLib, line 2423
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 2424
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2425
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2426
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 2427
    } // library marker kkossev.commonLib, line 2428
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2429
    cmds += configureDevice() // library marker kkossev.commonLib, line 2430
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2431
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 2432
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 2433
    //return cmds // library marker kkossev.commonLib, line 2434
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2435
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2436
    } // library marker kkossev.commonLib, line 2437
    else { // library marker kkossev.commonLib, line 2438
        logDebug "no configure() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2439
    } // library marker kkossev.commonLib, line 2440
} // library marker kkossev.commonLib, line 2441

/** // library marker kkossev.commonLib, line 2443
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2444
 */ // library marker kkossev.commonLib, line 2445
void installed() { // library marker kkossev.commonLib, line 2446
    logInfo 'installed...' // library marker kkossev.commonLib, line 2447
    // populate some default values for attributes // library marker kkossev.commonLib, line 2448
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2449
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2450
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 2451
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2452
} // library marker kkossev.commonLib, line 2453

/** // library marker kkossev.commonLib, line 2455
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 2456
 */ // library marker kkossev.commonLib, line 2457
void initialize() { // library marker kkossev.commonLib, line 2458
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2459
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2460
    updateTuyaVersion() // library marker kkossev.commonLib, line 2461
    updateAqaraVersion() // library marker kkossev.commonLib, line 2462
} // library marker kkossev.commonLib, line 2463

/* // library marker kkossev.commonLib, line 2465
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2466
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2467
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2468
*/ // library marker kkossev.commonLib, line 2469

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2471
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2472
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2473
} // library marker kkossev.commonLib, line 2474

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 2476
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2477
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2478
} // library marker kkossev.commonLib, line 2479

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2481
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 2482
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 2483
} // library marker kkossev.commonLib, line 2484

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 2486
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 2487
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 2488
        return // library marker kkossev.commonLib, line 2489
    } // library marker kkossev.commonLib, line 2490
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2491
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2492
    cmd.each { // library marker kkossev.commonLib, line 2493
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2494
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 2495
    } // library marker kkossev.commonLib, line 2496
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2497
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2498
} // library marker kkossev.commonLib, line 2499

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 2501

String getDeviceInfo() { // library marker kkossev.commonLib, line 2503
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2504
} // library marker kkossev.commonLib, line 2505

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2507
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 2508
} // library marker kkossev.commonLib, line 2509

void checkDriverVersion() { // library marker kkossev.commonLib, line 2511
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2512
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2513
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2514
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2515
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2516
        updateTuyaVersion() // library marker kkossev.commonLib, line 2517
        updateAqaraVersion() // library marker kkossev.commonLib, line 2518
    } // library marker kkossev.commonLib, line 2519
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2520
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2521
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2522
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 2523
} // library marker kkossev.commonLib, line 2524

// credits @thebearmay // library marker kkossev.commonLib, line 2526
String getModel() { // library marker kkossev.commonLib, line 2527
    try { // library marker kkossev.commonLib, line 2528
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2529
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2530
    } catch (ignore) { // library marker kkossev.commonLib, line 2531
        try { // library marker kkossev.commonLib, line 2532
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2533
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2534
                return model // library marker kkossev.commonLib, line 2535
            } // library marker kkossev.commonLib, line 2536
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2537
            return '' // library marker kkossev.commonLib, line 2538
        } // library marker kkossev.commonLib, line 2539
    } // library marker kkossev.commonLib, line 2540
} // library marker kkossev.commonLib, line 2541

// credits @thebearmay // library marker kkossev.commonLib, line 2543
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2544
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2545
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2546
    String revision = tokens.last() // library marker kkossev.commonLib, line 2547
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2548
} // library marker kkossev.commonLib, line 2549

/** // library marker kkossev.commonLib, line 2551
 * called from TODO // library marker kkossev.commonLib, line 2552
 */ // library marker kkossev.commonLib, line 2553

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2555
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2556
    unschedule() // library marker kkossev.commonLib, line 2557
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2558
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2559

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2561
} // library marker kkossev.commonLib, line 2562

void resetStatistics() { // library marker kkossev.commonLib, line 2564
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2565
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2566
} // library marker kkossev.commonLib, line 2567

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2569
void resetStats() { // library marker kkossev.commonLib, line 2570
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2571
    state.stats = [:] // library marker kkossev.commonLib, line 2572
    state.states = [:] // library marker kkossev.commonLib, line 2573
    state.lastRx = [:] // library marker kkossev.commonLib, line 2574
    state.lastTx = [:] // library marker kkossev.commonLib, line 2575
    state.health = [:] // library marker kkossev.commonLib, line 2576
    state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2577
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2578
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2579
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2580
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2581
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2582
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2583
} // library marker kkossev.commonLib, line 2584

/** // library marker kkossev.commonLib, line 2586
 * called from TODO // library marker kkossev.commonLib, line 2587
 */ // library marker kkossev.commonLib, line 2588
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2589
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2590
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2591
        state.clear() // library marker kkossev.commonLib, line 2592
        unschedule() // library marker kkossev.commonLib, line 2593
        resetStats() // library marker kkossev.commonLib, line 2594
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2595
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2596
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2597
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2598
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2599
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2600
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2601
    } // library marker kkossev.commonLib, line 2602

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2604
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2605
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2606
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2607
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2608
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2609

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2611
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2612
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2613
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2614
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2615
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2616
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2617
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2618
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2619
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 2620
    /* // library marker kkossev.commonLib, line 2621
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2622
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2623
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2624
    } // library marker kkossev.commonLib, line 2625
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2626
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.commonLib, line 2627
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.commonLib, line 2628
    } // library marker kkossev.commonLib, line 2629
    */ // library marker kkossev.commonLib, line 2630
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2631
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2632
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2633
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2634

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2636
    if ( mm != null) { // library marker kkossev.commonLib, line 2637
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2638
    } // library marker kkossev.commonLib, line 2639
    else { // library marker kkossev.commonLib, line 2640
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2641
    } // library marker kkossev.commonLib, line 2642
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2643
    if ( ep  != null) { // library marker kkossev.commonLib, line 2644
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2645
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2646
    } // library marker kkossev.commonLib, line 2647
    else { // library marker kkossev.commonLib, line 2648
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2649
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2650
    } // library marker kkossev.commonLib, line 2651
} // library marker kkossev.commonLib, line 2652

/** // library marker kkossev.commonLib, line 2654
 * called from TODO // library marker kkossev.commonLib, line 2655
 */ // library marker kkossev.commonLib, line 2656
void setDestinationEP() { // library marker kkossev.commonLib, line 2657
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2658
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2659
        state.destinationEP = ep // library marker kkossev.commonLib, line 2660
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2661
    } // library marker kkossev.commonLib, line 2662
    else { // library marker kkossev.commonLib, line 2663
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2664
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2665
    } // library marker kkossev.commonLib, line 2666
} // library marker kkossev.commonLib, line 2667

void  logDebug(final String msg) { // library marker kkossev.commonLib, line 2669
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2670
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2671
    } // library marker kkossev.commonLib, line 2672
} // library marker kkossev.commonLib, line 2673

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2675
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2676
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2677
    } // library marker kkossev.commonLib, line 2678
} // library marker kkossev.commonLib, line 2679

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2681
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2682
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2683
    } // library marker kkossev.commonLib, line 2684
} // library marker kkossev.commonLib, line 2685

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2687
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2688
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2689
    } // library marker kkossev.commonLib, line 2690
} // library marker kkossev.commonLib, line 2691

// _DEBUG mode only // library marker kkossev.commonLib, line 2693
void getAllProperties() { // library marker kkossev.commonLib, line 2694
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2695
    device.properties.each { it -> // library marker kkossev.commonLib, line 2696
        log.debug it // library marker kkossev.commonLib, line 2697
    } // library marker kkossev.commonLib, line 2698
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2699
    settings.each { it -> // library marker kkossev.commonLib, line 2700
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2701
    } // library marker kkossev.commonLib, line 2702
    log.trace 'Done' // library marker kkossev.commonLib, line 2703
} // library marker kkossev.commonLib, line 2704

// delete all Preferences // library marker kkossev.commonLib, line 2706
void deleteAllSettings() { // library marker kkossev.commonLib, line 2707
    settings.each { it -> // library marker kkossev.commonLib, line 2708
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2709
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2710
    } // library marker kkossev.commonLib, line 2711
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2712
} // library marker kkossev.commonLib, line 2713

// delete all attributes // library marker kkossev.commonLib, line 2715
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2716
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2717
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2718
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2719
    } // library marker kkossev.commonLib, line 2720
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2721
} // library marker kkossev.commonLib, line 2722

// delete all State Variables // library marker kkossev.commonLib, line 2724
void deleteAllStates() { // library marker kkossev.commonLib, line 2725
    state.each { it -> // library marker kkossev.commonLib, line 2726
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2727
    } // library marker kkossev.commonLib, line 2728
    state.clear() // library marker kkossev.commonLib, line 2729
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2730
} // library marker kkossev.commonLib, line 2731

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2733
    unschedule() // library marker kkossev.commonLib, line 2734
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2735
} // library marker kkossev.commonLib, line 2736

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2738
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2739
} // library marker kkossev.commonLib, line 2740

void parseTest(String par) { // library marker kkossev.commonLib, line 2742
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2743
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2744
    parse(par) // library marker kkossev.commonLib, line 2745
} // library marker kkossev.commonLib, line 2746

def testJob() { // library marker kkossev.commonLib, line 2748
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2749
} // library marker kkossev.commonLib, line 2750

/** // library marker kkossev.commonLib, line 2752
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2753
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2754
 */ // library marker kkossev.commonLib, line 2755
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2756
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2757
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2758
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2759
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2760
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2761
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2762
    String cron // library marker kkossev.commonLib, line 2763
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2764
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2765
    } // library marker kkossev.commonLib, line 2766
    else { // library marker kkossev.commonLib, line 2767
        if (minutes < 60) { // library marker kkossev.commonLib, line 2768
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2769
        } // library marker kkossev.commonLib, line 2770
        else { // library marker kkossev.commonLib, line 2771
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2772
        } // library marker kkossev.commonLib, line 2773
    } // library marker kkossev.commonLib, line 2774
    return cron // library marker kkossev.commonLib, line 2775
} // library marker kkossev.commonLib, line 2776

// credits @thebearmay // library marker kkossev.commonLib, line 2778
String formatUptime() { // library marker kkossev.commonLib, line 2779
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2780
} // library marker kkossev.commonLib, line 2781

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2783
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2784
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2785
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2786
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2787
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2788
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2789
} // library marker kkossev.commonLib, line 2790

boolean isTuya() { // library marker kkossev.commonLib, line 2792
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2793
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2794
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2795
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2796
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2797
} // library marker kkossev.commonLib, line 2798

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2800
    if (!isTuya()) { // library marker kkossev.commonLib, line 2801
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2802
        return // library marker kkossev.commonLib, line 2803
    } // library marker kkossev.commonLib, line 2804
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2805
    if (application != null) { // library marker kkossev.commonLib, line 2806
        Integer ver // library marker kkossev.commonLib, line 2807
        try { // library marker kkossev.commonLib, line 2808
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2809
        } // library marker kkossev.commonLib, line 2810
        catch (e) { // library marker kkossev.commonLib, line 2811
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2812
            return // library marker kkossev.commonLib, line 2813
        } // library marker kkossev.commonLib, line 2814
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2815
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2816
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2817
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2818
        } // library marker kkossev.commonLib, line 2819
    } // library marker kkossev.commonLib, line 2820
} // library marker kkossev.commonLib, line 2821

boolean isAqara() { // library marker kkossev.commonLib, line 2823
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2824
} // library marker kkossev.commonLib, line 2825

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2827
    if (!isAqara()) { // library marker kkossev.commonLib, line 2828
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2829
        return // library marker kkossev.commonLib, line 2830
    } // library marker kkossev.commonLib, line 2831
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2832
    if (application != null) { // library marker kkossev.commonLib, line 2833
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2834
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2835
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2836
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2837
        } // library marker kkossev.commonLib, line 2838
    } // library marker kkossev.commonLib, line 2839
} // library marker kkossev.commonLib, line 2840

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2842
    try { // library marker kkossev.commonLib, line 2843
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2844
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2845
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2846
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2847
    } catch (e) { // library marker kkossev.commonLib, line 2848
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2849
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2850
    } // library marker kkossev.commonLib, line 2851
} // library marker kkossev.commonLib, line 2852

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2854
    try { // library marker kkossev.commonLib, line 2855
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2856
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2857
        return date.getTime() // library marker kkossev.commonLib, line 2858
    } catch (e) { // library marker kkossev.commonLib, line 2859
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2860
        return now() // library marker kkossev.commonLib, line 2861
    } // library marker kkossev.commonLib, line 2862
} // library marker kkossev.commonLib, line 2863

void test(String par) { // library marker kkossev.commonLib, line 2865
    List<String> cmds = [] // library marker kkossev.commonLib, line 2866
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2867

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2869
    //parse(par) // library marker kkossev.commonLib, line 2870

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2872
} // library marker kkossev.commonLib, line 2873

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

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

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter */ // library marker kkossev.xiaomiLib, line 1
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
