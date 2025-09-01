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
 *
 *                                   TODO:
 */

static String version() { '3.2.0' }
static String timeStamp() { '2024/08/03 8:30 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field

@Field static final String DEVICE_TYPE = 'LightSensor'
deviceType = 'LightSensor'

#include kkossev.commonLib
#include kkossev.illuminanceLib
#include kkossev.xiaomiLib

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

