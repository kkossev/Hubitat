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

#include kkossev.commonLib
#include kkossev.onOffLib
#include kkossev.groupsLib
#include kkossev.buttonLib
#include kkossev.energyLib
#include kkossev.illuminanceLib
#include kkossev.levelLib
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
