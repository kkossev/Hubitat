/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, ParameterName, PublicMethodsBeforeNonPublicMethods */
/**
 *  Tuya Zigbee Temperature Humidity Sensor - Driver for Hubitat Elevation
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
 * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) commonLib 3.06
 *
 *                                   TODO:  */

static String version() { '3.0.6' }
static String timeStamp() { '2024/04/06 11:56 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field

deviceType = 'THSensor'
@Field static final String DEVICE_TYPE = 'THSensor'

/* groovylint-disable-next-line NglParseError */
#include kkossev.commonLib
#include kkossev.temperatureLib
#include kkossev.humidityLib
#include kkossev.batteryLib

metadata {
    definition(
        name: 'Tuya Zigbee Temperature Humidity Sensor',
        importUrl: 'https://github.com/kkossev/Hubitat/blob/development/Drivers/Tuya%20Zigbee%20Temperature%20Humidityt%20Sensor/Tuya_Zigbee_Temperature_Humidity_Sensor_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
            capability 'Sensor'
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        if (device) {
        }
    }
}

List<String> customInitializeDevice() {
    List<String> cmds = []
    if (this.respondsTo('temperatureLibInitializeDevice')) {
        List<String> tempCmds = temperatureLibInitializeDevice()
        if (tempCmds != null && tempCmds != []) { cmds += tempCmds }
    }
    if (this.respondsTo('humidityLibInitializeDevice')) {
        List<String> humiCmds = humidityLibInitializeDevice()
        if (humiCmds != null && humiCmds != []) { cmds += humiCmds }
    }
    return cmds
}

