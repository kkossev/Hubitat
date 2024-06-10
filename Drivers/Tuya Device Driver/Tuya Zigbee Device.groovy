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

@Field static final Boolean _DEBUG = true

#include kkossev.commonLib
#include kkossev.buttonLib
#include kkossev.ctLib
#include kkossev.energyLib
#include kkossev.groupsLib
#include kkossev.humidityLib
#include kkossev.iasLib
#include kkossev.illuminanceLib
#include kkossev.levelLib
#include kkossev.onOffLib
#include kkossev.reportingLib
#include kkossev.rgbLib
#include kkossev.temperatureLib
#include kkossev.deviceProfileLib 
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
            command 'test',                    [[name: 'Test']]
        }
    }
    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
    }
}


import groovy.json.JsonSlurper
import groovy.util.XmlSlurper

void test() {
    String shortZigbeeId = device.id.toString().substring(0, 4)
    //String lastMessage = getLastMessage('0x0000')
    getLastMessage('')
}

String getLastMessage(String shortZigbeeId) {
    params = [
        uri    : "http://127.0.0.1:8080",
        path   : "/hub/zigbeeDetails/json",
        //headers: ["Cookie": cookie]
    ]
    if (debugEnabled) log.debug params
    asynchttpGet("getCpuTemperature", params) 
/*
    def xml = new XmlSlurper().parse('http://127.0.0.1/hub/zigbeeDetails/json')
    def json = new JsonSlurper().parseText(xml.text())
    def lastMessage = null

    json.devices.each { device ->
        if (device.shortZigbeeId == shortZigbeeId) {
            lastMessage = device.lastMessage
        }
    }

    return lastMessage
    */
}


void getCpuTemperature(resp, data) {
  //  try {
        if(resp.getStatus() == 200 || resp.getStatus() == 207) {
            Double tempWork = new Double(resp.data.toString())
            if(tempWork > 0) {
                log.debug tempWork
                /*
                if (location.temperatureScale == "F")
                    updateAttr("temperature",String.format("%.1f", celsiusToFahrenheit(tempWork)),"°F")
                else
                    updateAttr("temperature",String.format("%.1f",tempWork),"°C")

                updateAttr("temperatureF",String.format("%.1f",celsiusToFahrenheit(tempWork))+ " °F")
                updateAttr("temperatureC",String.format("%.1f",tempWork)+ " °C")
                */
            }
        }
 //   } catch(ignored) {
 //       def respStatus = resp.getStatus()
 //       if (!warnSuppress) log.warn "getTemp httpResp = $respStatus but returned invalid data, will retry next cycle"
 //   } 
}


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
