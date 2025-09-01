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
 * ver. 3.0.6  2024-04-06 kkossev  - commonLib 3.0.6
 * ver. 3.2.0  2024-05-28 kkossev  - commonLib 3.2.0
 * ver. 3.2.1  2024-05-28 kkossev  - (dev.branch)
 *
 *                                   TODO:
 */

static String version() { "3.2.1" }
static String timeStamp() {"2024/07/12 2:23 PM"}

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
        //input(name: "deviceNetworkId", type: "enum", title: "Router Device", description: "<small>Select a mains-powered device that you want to put in pairing mode.</small>", options: [ "0000":"?? Hubitat Hub" ] + getDevices(), required: true)
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


// all credits @dandanache  importUrl:"https://raw.githubusercontent.com/dan-danache/hubitat/master/zigbee-pairing-helper-driver/zigbee-pairing-helper.groovy"
private Map<String, String> getDevices() {
    try {
        httpGet([ uri:"http://127.0.0.1:8080/hub/zigbee/getChildAndRouteInfoJson" ]) { response ->
            if (response?.status != 200) {
                return ["ZZZZ": "Invalid response: ${response}"]
            }
            return response.data.devices
                .sort { it.name }
                .collectEntries { ["${it.zigbeeId}", "${it.name}"] }
        }
    } catch (Exception ex) {
        return ["ZZZZ": "Exception: ${ex}"]
    }
}

void zigbeePairingHelper() {
    logDebug "zigbeePairingHelper()..."
    if (settings?.deviceNetworkId == null || settings?.deviceNetworkId == "ZZZZ") {
        log.error("Invalid Device Network ID: ${settings?.deviceNetworkId}")
        return
    }

    log.info "Stopping Zigbee pairing on all devices. Please wait 5 seconds ..."
    sendHubCommand new hubitat.device.HubMultiAction(["he raw 0xFFFC 0x00 0x00 0x0036 {42 0001} {0x0000}"], hubitat.device.Protocol.ZIGBEE)
    runIn(5, "startDeviceZigbeePairing")
}

private startDeviceZigbeePairing() {
    log.info "Starting Zigbee pairing on device ${settings?.deviceNetworkId} for 90 seconds..."
    sendHubCommand new hubitat.device.HubMultiAction(["he raw 0x${settings?.deviceNetworkId} 0x00 0x00 0x0036 {43 5A01} {0x0000}"], hubitat.device.Protocol.ZIGBEE)
    log.warn "<b>Now is the right moment to put the device you want to join in pairing mode!</b>"
}



// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
