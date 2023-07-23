library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "Tuya Zigbee Fingerbot Library",
    name: "tuyaFingerbotLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/main/libraries/tuyaFingerbotLib.groovy",
    version: "1.0.0",
    documentationLink: ""
)
/*
 *  Tuya Zigbee Fingerbot - library
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ver. 1.0.0  2023-07-13 kkossev  - (dev. branch) - Libraries introduction for the Tuya Zigbee Fingerbot driver;
 *
 *                                   TODO: 
*/


def tuyaFingerbotLibVersion()   {"1.0.0"}
def tuyaFingerbotLibStamp() {"2023/07/13 1:03 PM"}

metadata {
    attribute "fingerbotMode", "enum", FingerbotModeOpts.options.values() as List<String>
    attribute "direction", "enum", FingerbotDirectionOpts.options.values() as List<String>
    attribute "pushTime", "number"
    attribute "dnPosition", "number"
    attribute "upPosition", "number"
        
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0019", model:"lumi.remote.cagl02", manufacturer:"LUMI", deviceJoinName: "Aqara Cube T1 Pro"
    preferences {
        input name: 'fingerbotMode', type: 'enum', title: '<b>Fingerbot Mode</b>', options: FingerbotModeOpts.options, defaultValue: FingerbotModeOpts.defaultValue, required: true, description: '<i>Push or Switch.</i>'
        input name: 'direction', type: 'enum', title: '<b>Fingerbot Direction</b>', options: FingerbotDirectionOpts.options, defaultValue: FingerbotDirectionOpts.defaultValue, required: true, description: '<i>Finger movement direction.</i>'
        input name: 'pushTime', type: 'number', title: '<b>Push Time</b>', description: '<i>The time that the finger will stay in down position in Push mode, seconds</i>', required: true, range: "0..255", defaultValue: 0  
        input name: 'upPosition', type: 'number', title: '<b>Up Postition</b>', description: '<i>Finger up position, (0..50), percent</i>', required: true, range: "0..50", defaultValue: 0  
        input name: 'dnPosition', type: 'number', title: '<b>Down Postition</b>', description: '<i>Finger down position (51..100), percent</i>', required: true, range: "51..100", defaultValue: 100  
    }
}

@Field static final Map FingerbotModeOpts = [
    defaultValue: 0,
    options     : [0: 'push', 1: 'switch']
]
@Field static final Map FingerbotDirectionOpts = [
    defaultValue: 0,
    options     : [0: 'normal', 1: 'reverse']
]         

def configureDeviceFingerbot() {
    List<String> cmds = []
    
    final int mode = (settings.fingerbotMode as Integer) ?: FingerbotModeOpts.defaultValue
    logDebug "setting fingerbotMode to ${FingerbotModeOpts.options[mode]} (${mode})"
    cmds = sendTuyaCommand("65", DP_TYPE_BOOL, zigbee.convertToHexString(mode as int, 2) )
    final int duration = (settings.pushTime as Integer) ?: 0
    logDebug "setting pushTime to ${duration} seconds)"
    cmds += sendTuyaCommand("67", DP_TYPE_VALUE, zigbee.convertToHexString(duration as int, 8) )
    final int dnPos = (settings.dnPosition as Integer) ?: 0
    logDebug "setting dnPosition to ${dnPos} %)"
    cmds += sendTuyaCommand("66", DP_TYPE_VALUE, zigbee.convertToHexString(dnPos as int, 8) )
    final int upPos = (settings.upPosition as Integer) ?: 0
    logDebug "setting upPosition to ${upPos} %)"
    cmds += sendTuyaCommand("6A", DP_TYPE_VALUE, zigbee.convertToHexString(upPos as int, 8) )
    final int dir = (settings.direction as Integer) ?: FingerbotDirectionOpts.defaultValue
    logDebug "setting fingerbot direction to ${FingerbotDirectionOpts.options[dir]} (${dir})"
    cmds += sendTuyaCommand("68", DP_TYPE_BOOL, zigbee.convertToHexString(dir as int, 2) )
    
    logDebug "configureDeviceFingerbot() : ${cmds}"
    return cmds    
}

def initVarsFingerbot(boolean fullInit=false) {
    logDebug "initVarsFingerbot(${fullInit})"
    if (fullInit || settings?.fingerbotMode == null) device.updateSetting('fingerbotMode', [value: FingerbotModeOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.pushTime == null) device.updateSetting("pushTime", [value:0, type:"number"])
    if (fullInit || settings?.upPosition == null) device.updateSetting("upPosition", [value:0, type:"number"])
    if (fullInit || settings?.dnPosition == null) device.updateSetting("dnPosition", [value:100, type:"number"])
}

def refreshFingerbot() {
    List<String> cmds = []
    // TODO
    logDebug "refreshFingerbot() : ${cmds}"
    return cmds
}


