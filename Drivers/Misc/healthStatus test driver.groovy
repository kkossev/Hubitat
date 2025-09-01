/**
 *  healthStatus test driver for Hubitat
 *
 *  https://community.hubitat.com/t/devicepresent-capability/89774/18?u=kkossev
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *    in compliance with the License. You may obtain a copy of the License at:
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *    on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *    for the specific language governing permissions and limitations under the License.
 *
 * ver. 1.0.0 2023-01-27 kkossev  - inital version
 * ver. 1.0.1 2023-02-02 kkossev  - a dummy capability 'Health Check' already exists in Hubitat! (unfortunately, the attribute 'healthStatus' still has to be declared)
 * ver. 1.0.2 2023-02-04 kkossev  - healthStatus enum extended w/ 'unknown'; powerSource attribute must be explicitely defined in order to be used in an app!; added dummy ping()
 * ver. 1.0.3 2023-11-21 kkossev  - (dev.branch) adding on and off commands; 
 *
*/

metadata {
    definition (name: "healthStatus test driver", namespace: "kkossev", author: "Krassimir Kossev", importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/main/Drivers/Misc/healthStatus%20test%20driver.groovy") {
        capability "Actuator"
        capability "Sensor"
        capability "Health Check"
        
        attribute "healthStatus", "enum", ["offline", "online", "unknown"]
        attribute "powerSource", "enum", ["battery", "dc", "mains", "unknown"]
        attribute "rtt", "number"
        
        command "setOffline", [[name: "Set healthStatus offline"]]
        command "setOnline",  [[name: "Set healthStatus online" ]]
        command "setRTT",     [[name: "setRTT", type: "STRING", description: "RTT", defaultValue : "unknown"]]
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
    }
}

def parse(String description) {
    if (logEnable) log.debug "${device.displayName} parse: description is $description"
}

def on() {
    if (logEnable) log.info "${device.displayName} switching on..."
    zigbee.on()
}

def off() {
    if (logEnable) log.info "${device.displayName} switching off..."
    zigbee.off()
}

def ping() {
    state.pingTime = new Date().getTime()
    scheduleCommandTimeoutCheck()
    sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) )
    if (logEnable) log.debug "ping..."
}

def setOffline() {
    sendEvent(name: "healthStatus", value: "offline", type: "digital", isStateChange: true )
    sendEvent(name: "powerSource",  value: "unknown", type: "digital", isStateChange: true )
    if (logEnable) log.info "${device.displayName} is offline..."
}

def setOnline() {
    sendEvent(name: "healthStatus", value: "online", type: "digital", isStateChange: true )
    sendEvent(name: "powerSource",  value: "dc", type: "digital", isStateChange: true )
    if (logEnable) log.info "${device.displayName} is online..."
}

