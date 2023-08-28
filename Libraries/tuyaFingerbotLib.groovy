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
 * ver. 1.0.0  2023-07-13 kkossev  - Libraries introduction for the Tuya Zigbee Fingerbot driver;
 * ver. 1.0.1  2023-07-23 kkossev  - added _TZ3210_dse8ogfy fingerprint
 * ver. 1.0.2  2023-08-28 kkossev  - (dev. branch) processTuyaDpFingerbot; added Momentary capability for Fingerbot in the main code; direction preference initialization bug fix; voltageToPercent (battery %) is enabled by default; fingerbot button enable/disable; 
 *
 *                                   TODO: Update preferences values w/ the received parameters when the battery is re-inserted.
*/


def tuyaFingerbotLibVersion()   {"1.0.2"}
def tuyaFingerbotLibStamp() {"2023/08/28 11:16 PM"}

metadata {
    attribute "fingerbotMode", "enum", FingerbotModeOpts.options.values() as List<String>
    attribute "direction", "enum", FingerbotDirectionOpts.options.values() as List<String>
    attribute "pushTime", "number"
    attribute "dnPosition", "number"
    attribute "upPosition", "number"
        
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0006,EF00,0000", outClusters:"0019,000A", model:"TS0001", manufacturer:"_TZ3210_dse8ogfy", deviceJoinName: "Tuya Zigbee Fingerbot"
    
    preferences {
        input name: 'fingerbotMode', type: 'enum', title: '<b>Fingerbot Mode</b>', options: FingerbotModeOpts.options, defaultValue: FingerbotModeOpts.defaultValue, required: true, description: '<i>Push or Switch.</i>'
        input name: 'direction', type: 'enum', title: '<b>Fingerbot Direction</b>', options: FingerbotDirectionOpts.options, defaultValue: FingerbotDirectionOpts.defaultValue, required: true, description: '<i>Finger movement direction.</i>'
        input name: 'pushTime', type: 'number', title: '<b>Push Time</b>', description: '<i>The time that the finger will stay in down position in Push mode, seconds</i>', required: true, range: "0..255", defaultValue: 0  
        input name: 'upPosition', type: 'number', title: '<b>Up Postition</b>', description: '<i>Finger up position, (0..50), percent</i>', required: true, range: "0..50", defaultValue: 0  
        input name: 'dnPosition', type: 'number', title: '<b>Down Postition</b>', description: '<i>Finger down position (51..100), percent</i>', required: true, range: "51..100", defaultValue: 100  
        input name: 'fingerbotButton', type: 'enum', title: '<b>Fingerbot Button</b>', options: FingerbotButtonOpts.options, defaultValue: FingerbotButtonOpts.defaultValue, required: true, description: '<i>Disable or enable the Fingerbot touch button</i>'
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
@Field static final Map FingerbotButtonOpts = [
    defaultValue: 1,
    options     : [0: 'disabled', 1: 'enabled']
]

def pushFingerbot() {
    logDebug "pushFingerbot: on"
    on()
}

def pushFingerbot(buttonNumber) {    //pushableButton capability
    pushFingerbot()
}


def configureDeviceFingerbot() {
    List<String> cmds = []
    
    def mode = settings.fingerbotMode != null ? settings.fingerbotMode : FingerbotModeOpts.defaultValue
    //logWarn "mode=${mode}  settings=${(settings.fingerbotMode)}"
    logDebug "setting fingerbotMode to ${FingerbotModeOpts.options[mode as int]} (${mode})"
    cmds = sendTuyaCommand("65", DP_TYPE_BOOL, zigbee.convertToHexString(mode as int, 2) )

    final int duration = (settings.pushTime as Integer) ?: 0
    logDebug "setting pushTime to ${duration} seconds)"
    cmds += sendTuyaCommand("67", DP_TYPE_VALUE, zigbee.convertToHexString(duration as int, 8) )

    final int dnPos = (settings.dnPosition as Integer) ?: 0
    logDebug "setting dnPosition to ${dnPos} %"
    cmds += sendTuyaCommand("66", DP_TYPE_VALUE, zigbee.convertToHexString(dnPos as int, 8) )
    final int upPos = (settings.upPosition as Integer) ?: 0
    logDebug "setting upPosition to ${upPos} %"
    cmds += sendTuyaCommand("6A", DP_TYPE_VALUE, zigbee.convertToHexString(upPos as int, 8) )
            
    final int dir = (settings.direction as Integer) ?: FingerbotDirectionOpts.defaultValue
    logDebug "setting fingerbot direction to ${FingerbotDirectionOpts.options[dir]} (${dir})"
    cmds += sendTuyaCommand("68", DP_TYPE_BOOL, zigbee.convertToHexString(dir as int, 2) )
    /*
    */
    def button = settings.fingerbotButton != null ? settings.fingerbotButton : FingerbotButtonOpts.defaultValue
    //logWarn "button=${button}  settings=${(settings.fingerbotButton)}"
    logDebug "setting fingerbotButton to ${FingerbotButtonOpts.options[button as int]} (${button})"
    cmds += sendTuyaCommand("6B", DP_TYPE_BOOL, zigbee.convertToHexString(button as int, 2) )

    logDebug "configureDeviceFingerbot() : ${cmds}"
    return cmds    
}

def initVarsFingerbot(boolean fullInit=false) {
    logDebug "initVarsFingerbot(${fullInit})"
    if (fullInit || settings?.fingerbotMode == null) device.updateSetting('fingerbotMode', [value: FingerbotModeOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.pushTime == null) device.updateSetting("pushTime", [value:0, type:"number"])
    if (fullInit || settings?.upPosition == null) device.updateSetting("upPosition", [value:0, type:"number"])
    if (fullInit || settings?.dnPosition == null) device.updateSetting("dnPosition", [value:100, type:"number"])
    if (fullInit || settings?.direction == null) device.updateSetting('direction', [value: FingerbotDirectionOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.fingerbotButton == null) device.updateSetting('fingerbotButton', [value: FingerbotButtonOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.voltageToPercent == null) device.updateSetting("voltageToPercent", true)

}

void initEventsFingerbot(boolean fullInit=false) {
/*  not needed?
    sendNumberOfButtonsEvent(1)
    sendSupportedButtonValuesEvent("pushed")
*/
}

/*
Switch1 		    1
Mode			    101
Degree of declining	code: 102
Duration 		    103
Switch Reverse		104
Battery Power		105
Increase		    106
Tact Switch 		107
Click 			    108
Custom Program		109
Producion Test		110
Sports Statistics	111
Custom Timing		112
*/

void processTuyaDpFingerbot(descMap, dp, dp_id, fncmd) {

    switch (dp) {
        case 0x01 : // on/off
            sendSwitchEvent(fncmd)
            break
        case 0x02 :
            logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            break
        case 0x04 : // battery
            sendBatteryPercentageEvent(fncmd)
            break
        
        case 0x65 : // (101)
            def value = FingerbotModeOpts.options[fncmd as int]
            def descriptionText = "Fingerbot mode is ${value} (${fncmd})"
            sendEvent(name: "fingerbotMode", value: value, descriptionText: descriptionText)
            logInfo "${descriptionText}"
            break
        case 0x66 : // (102)
            def value = fncmd as int
            def descriptionText = "Fingerbot Down Position is ${value} %"
            sendEvent(name: "dnPosition", value: value, descriptionText: descriptionText)
            logInfo "${descriptionText}"
            break
        case 0x67 : // (103)
            def value = fncmd as int
            def descriptionText = "Fingerbot push time (duration) is ${value} seconds"
            sendEvent(name: "pushTime", value: value, descriptionText: descriptionText)
            logInfo "${descriptionText}"
            break
        case 0x68 : // (104)
            def value = FingerbotDirectionOpts.options[fncmd as int]
            def descriptionText = "Fingerbot switch direction is ${value} (${fncmd})"
            sendEvent(name: "direction", value: value, descriptionText: descriptionText)
            logInfo "${descriptionText}"
            break
        case 0x69 : // (105)
            //logInfo "Fingerbot Battery Power is ${fncmd}"
            sendBatteryPercentageEvent(fncmd) 
            break
        case 0x6A : // (106)
            def value = fncmd as int
            def descriptionText = "Fingerbot Up Position is ${value} %"
            sendEvent(name: "upPosition", value: value, descriptionText: descriptionText)
            logInfo "${descriptionText}"
            break
        case 0x6B : // (107)
            logInfo "Fingerbot Tact Switch is ${fncmd}"
            break
        case 0x6C : // (108)
            logInfo "Fingerbot Click is ${fncmd}"
            break
        case 0x6D : // (109)
            logInfo "Fingerbot Custom Program is ${fncmd}"
            break
        case 0x6E : // (110)
            logInfo "Fingerbot Producion Test is ${fncmd}"
            break
        case 0x6F : // (111)
            logInfo "Fingerbot Sports Statistics is ${fncmd}"
            break
        case 0x70 : // (112)
            logInfo "Fingerbot Custom Timing is ${fncmd}"
            break
        default :
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            break            
    }
}


def refreshFingerbot() {
    List<String> cmds = []
    logDebug "refreshFingerbot() (n/a) : ${cmds} "
    return cmds
}


