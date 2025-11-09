/**
 *  Aqara Wall Switch Child
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  v. 2.0.0 10/19/2025 - kkosev - ported to Hubitat Elevation
 */

metadata {
    definition (name: "Aqara Wall Switch Child", namespace: "kkossev", author: "aonghus-mor & kkossev",
                importUrl: "https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20Wall%20Switch/aqara-wall-switch-child.groovy") {
        capability "Switch"
        capability "Momentary"
        capability "PushableButton"
        capability "Refresh"
    }
    
    preferences {
        input name: "unwired", type: "bool", title: "Is this switch unwired?", defaultValue: false, required: false
        input name: "decoupled", type: "bool", title: "Decoupled Mode?", defaultValue: false, required: false
        input name: "debugLogging", type: "bool", title: "Display debug log messages?", defaultValue: false, required: false
    }
}

def on() {
    logDebug("Child switch on")
    return parent.childOn(device.deviceNetworkId)
}

def off() {
    logDebug("Child switch off") 
    return parent.childOff(device.deviceNetworkId)
}

def push() {
    logDebug("Child switch push")
    return parent.childToggle(device.deviceNetworkId)
}

def refresh() {
    logDebug("Child refresh")
    return parent.childRefresh(device.deviceNetworkId, [unwired: unwired, decoupled: decoupled])
}

def installed() {
    logDebug("Child installed")
    sendEvent(name: 'numberOfButtons', value: 1)
    sendEvent(name: 'supportedButtonValues', value: ['pushed', 'held', 'double'], isStateChange: true)
}

def updated() {
    logDebug("Child updated")
}

private def logDebug(message) {
    if (debugLogging)
        log.debug "${device.displayName} ${message}"
}