/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnusedImport, UnusedPrivateMethod, VariableName */
/**
 *  Aqara Cube T1 Pro - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/alpha-aqara-cube-t1-pro-mfczq12lm-c-7/121604
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
 * ver. 2.1.0  2023-07-15 kkossev  - Libraries first introduction for the Aqara Cube T1 Pro driver; Fingerbot driver; Aqara devices: store NWK in states; aqaraVersion bug fix;
 * ver. 2.1.1  2023-07-16 kkossev  - Aqara Cube T1 Pro fixes and improvements; implemented configure() and loadAllDefaults commands;
 * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) commonLib 3.0.6
 * ver. 3.2.0  2024-05-21 kkossev  - (dev. branch) commonLib 3.2.0
 *
 *                                   TODO: 
 */

static String version() { "3.2.0" }
static String timeStamp() {"2024/05/21 4:10 PM"}

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

deviceType = "AqaraCube"
@Field static final String DEVICE_TYPE = "AqaraCube"

#include kkossev.commonLib
#include kkossev.xiaomiLib
#include kkossev.buttonLib
#include kkossev.batteryLib

metadata {
    definition (
        name: 'Aqara Cube T1 Pro',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Aqara%20Cube%20T1%20Pro/Aqara_Cube_T1_Pro_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        // deviceType specific capabilities, commands and attributes         
        capability "Sensor"
        capability "PushableButton"
        capability "DoubleTapableButton"
        capability "HoldableButton"
        capability "ReleasableButton"
        capability 'Battery'

        // defined in aqaraCubeT1ProLib //attribute "mode", "enum", AqaraCubeModeOpts.options.values() as List<String>
        attribute 'batteryVoltage', 'number'
        attribute "operationMode", "enum", AqaraCubeModeOpts.options.values() as List<String>
        attribute "action", "enum", (AqaraCubeSceneModeOpts.options.values() + AqaraCubeActionModeOpts.options.values()) as List<String>
        attribute "cubeSide", "enum", AqaraCubeSideOpts.options.values() as List<String>
        attribute "angle", "number"
        attribute "sideUp", "number"

        command "push", [[name: "sent when the cube side is flipped", type: "NUMBER", description: "simulates a button press", defaultValue : ""]]
        command "doubleTap", [[name: "sent when the cube side is shaken", type: "NUMBER", description: "simulates a button press", defaultValue : ""]]
        command "release", [[name: "sent when the cube is rotated right", type: "NUMBER", description: "simulates a button press", defaultValue : ""]]
        command "hold", [[name: "sent when the cube is rotated left", type: "NUMBER", description: "simulates a button press", defaultValue : ""]]
    }

    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0012,0006", outClusters:"0000,0003,0019", model:"lumi.remote.cagl02", manufacturer:"LUMI", deviceJoinName: "Aqara Cube T1 Pro"
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0006", outClusters:"0000,0003", model:"lumi.remote.cagl02", manufacturer:"LUMI", deviceJoinName: "Aqara Cube T1 Pro"                        // https://community.hubitat.com/t/alpha-aqara-cube-t1-pro-c-7/121604/11?u=kkossev

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        input name: 'cubeOperationMode', type: 'enum', title: '<b>Cube Operation Mode</b>', options: AqaraCubeModeOpts.options, defaultValue: AqaraCubeModeOpts.defaultValue, required: true, description: '<i>Operation Mode.<br>Press LINK button 5 times to toggle between action mode and scene mode</i>'
        input name: 'sendButtonEvent', type: 'enum', title: '<b>Send Button Event</b>', options: SendButtonEventOpts.options, defaultValue: SendButtonEventOpts.defaultValue, required: true, description: '<i>Send button events on cube actions</i>'
    }
}


// https://github.com/Koenkk/zigbee2mqtt/issues/15652 
// https://homekitnews.com/2022/02/17/aqara-cube-t1-pro-review/

@Field static final Map AqaraCubeModeOpts = [
    defaultValue: 1,
    options     : [0: 'action', 1: 'scene']
]

/////////////////////// scene mode /////////////////////
@Field static final Map AqaraCubeSceneModeOpts = [
    defaultValue: 0,
    options     : [
        1: 'shake',           // activated when the cube is shaken
        2: 'hold',            // activated if user picks up the cube and holds it
        3: 'sideUp',          // activated when the cube is resting on a surface
        4: 'inactivity',      // (not used!)
        5: 'flipToSide',      // (not used!) activated when the cube is flipped on a surface
        6: 'rotateLeft',      // activated when the cube is rotated left on a surface
        7: 'rotateRight',     // activated when the cube is rotated right on a surface
        8: 'throw'            // activated after a throw motion
    ]
]

//------------------- action mode -----------------
@Field static final Map AqaraCubeActionModeOpts = [
    defaultValue: 0,
    options     : [
        0: 'slide',
        1: 'rotate',
        2: 'tapTwice',
        3: 'flip90',
        4: 'flip180',
        5: 'shake',
        6: 'inactivity'
    ]
]
          
@Field static final Map AqaraCubeSideOpts = [
    defaultValue: 0,
    options     : [
        0: 'actionFromSide',
        1: 'actionSide',
        2: 'actionToSide',
        3: 'side',                 // Destination side of action
        4: 'sideUp'                // Upfacing side of current scene
    ]
]          

@Field static final Map SendButtonEventOpts = [
    defaultValue: 0,
    options     : [0: 'disabled', 1: 'enabled']
]


def customRefresh() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)                 // battery voltage
    cmds += zigbee.readAttribute(0xFCC0, 0x0009, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x0148, [mfgCode: 0x115F], delay=200)   // operation_mode
    cmds += zigbee.readAttribute(0xFCC0, 0x0149, [mfgCode: 0x115F], delay=200)   // side_up attribute report
    logDebug "customRefresh() : ${cmds}"
    return cmds
}

def customInitializeVars(boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (fullInit || settings?.cubeOperationMode == null) device.updateSetting('cubeOperationMode', [value: AqaraCubeModeOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.sendButtonEvent == null) device.updateSetting('sendButtonEvent', [value: SendButtonEventOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.voltageToPercent == null) device.updateSetting("voltageToPercent", true)        // overwrite the defailt false setting
}

void customInitEvents(boolean fullInit=false) {
    sendNumberOfButtonsEvent(6)
    def supportedValues = ["pushed", "double", "held", "released", "tested"]
    sendSupportedButtonValuesEvent(supportedValues)
}

/*
    configure: async (device, coordinatorEndpoint, logger) => {
        const endpoint = device.getEndpoint(1);
        await endpoint.write('aqaraOpple', {'mode': 1}, {manufacturerCode: 0x115f});
        await reporting.bind(endpoint, coordinatorEndpoint, ['genBasic','genOnOff','genPowerCfg','genMultistateInput']);
        await endpoint.read('genPowerCfg', ['batteryVoltage']);
        await endpoint.read('aqaraOpple', [0x0148], {manufacturerCode: 0x115f});
        await endpoint.read('aqaraOpple', [0x0149], {manufacturerCode: 0x115f});
    },

*/

def customConfigureDevice() {
    List<String> cmds = []
    cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 50",]                                                 // Aqara - Hubitat C-7 voodoo

    // await endpoint.write('aqaraOpple', {'mode': 1}, {manufacturerCode: 0x115f});
    def mode = settings?.cubeOperationMode != null ? settings.cubeOperationMode : AqaraCubeModeOpts.defaultValue
    logDebug "cubeOperationMode will be set to ${(AqaraCubeModeOpts.options[mode as int])} (${mode})"
    cmds += zigbee.writeAttribute(0xFCC0, 0x0009, 0x20, mode as int, [mfgCode: 0x115F], delay=200)

    // https://github.com/Koenkk/zigbee-herdsman-converters/pull/5367
    cmds += ["he raw 0x${device.deviceNetworkId} 1 ${device.endpointId} 0xFCC0 {14 5F 11 01 02 FF 00 41 10 45 65 21 20 75 38 17 69 78 53 89 51 13 16 49 58}  {0x0104}", "delay 50",]      // Aqara Cube T1 Pro voodoo

    // TODO - check if explicit binding is needed at all?
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0000 {${device.zigbeeId}} {}", "delay 251", ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}", "delay 251", ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}", "delay 251", ]
    
    cmds += zigbee.readAttribute(0xFCC0, 0x0009, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x0148, [mfgCode: 0x115F], delay=200)   
    cmds += zigbee.readAttribute(0xFCC0, 0x0149, [mfgCode: 0x115F], delay=200)   
    
    logDebug "customConfigureDevice() : ${cmds}"
    return cmds    
}


/*
 # Clusters (Scene Mode): 
  ## Endpoint 2: 

  | Cluster            | Data                      | Description                   |
  | ------------------ | ------------------------- | ----------------------------- |
  | genMultistateInput | {presentValue: 0}         | action: shake                 |
  | genMultistateInput | {presentValue: 4}         | action: hold                  |
  | genMultistateInput | {presentValue: 2}         | action: wakeup                |
  | genMultistateInput | {presentValue: 1024-1029} | action: fall with ith side up |
*/
void customParseMultistateInputCluster(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "customParseMultistateInputCluster: (0x012)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
    String action = null
    Integer side = 0
    switch (value as Integer) {
        case 0: 
            action = 'shake'
            break
        case 1: 
            action = 'throw'
            break
        case 2:
            action = 'wakeup'
            break
        case 4:
            action = 'hold'
            break
        case 1024..1029 :
            action = 'flipToSide'
            side = value - 1024 + 1
            break
        default :
            logWarn "customParseMultistateInputCluster: unknown value: xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
            return
    }
    if (action != null) {
        def eventMap = [:]
        eventMap.value = action
        eventMap.name = "action"
        eventMap.unit = ""
        eventMap.type = "physical"
        eventMap.isStateChange = true    // always send these events as a change!
        String sideStr = ""
        if (action == "flipToSide") {
            sideStr = side.toString()
            eventMap.data = [side: side]
            // first send a sideUp event, so that the side number is available in the automation rule
            sendAqaraCubeSideUpEvent((side-1) as int)
        }
        eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${sideStr} ${eventMap.unit}"
        sendEvent(eventMap)
        logInfo "${eventMap.descriptionText}"   
        if (action == "shake") {
            if (settings?.sendButtonEvent){
                side = device.currentValue('sideUp', true) as Integer
                sendButtonEvent(side, "doubleTapped", isDigital=true)
            }
        }
    }
    else {
        logWarn "customParseMultistateInputCluster: unknown action: ${action} xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

// called from xiaomiLib - refactor !
void parseXiaomiClusterAqaraCube(final Map descMap) {
    logDebug "parseXiaomiClusterAqaraCube: cluster 0xFCC0 attribute 0x${descMap.attrId} ${descMap}"
    switch (descMap.attrInt as Integer) {
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode
            final Integer value = hexStrToUnsignedInt(descMap.value)
            log.info "cubeMode is '${AqaraCubeModeOpts.options[value]}' (0x${descMap.value})"
            device.updateSetting('cubeMode', [value: value.toString(), type: 'enum'])
            break
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5)
            processSideFacingUp(descMap)
            break
        default:
            logWarn "parseXiaomiClusterAqaraCube: unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

/*
 # Clusters (Scene Mode): 
  ## Endpoint 2: 

  | Cluster            | Data                      | Description                   |
  | ------------------ | ------------------------- | ----------------------------- |
  | aqaraopple         | {329: 0-5}                | i side facing up              |
*/
void processSideFacingUp(final Map descMap) {
    logDebug "processSideFacingUp: ${descMap}"
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    Integer value = hexStrToUnsignedInt(descMap.value)    
    sendAqaraCubeSideUpEvent(value)
}

def sendAqaraCubeSideUpEvent(final Integer value) {
    if ((device.currentValue('sideUp', true) as Integer) == (value+1)) {
        logDebug "no change in sideUp (${(value+1)}), skipping..."
        return
    }
    if (value>=0 && value<=5) {
        def eventMap = [:]
        eventMap.value = value + 1
        eventMap.name = "sideUp"
        eventMap.unit = ""
        eventMap.type = "physical"
        eventMap.isStateChange = true
        eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}"
        sendEvent(eventMap)
        logInfo "${eventMap.descriptionText}"
        if (settings?.sendButtonEvent){
            sendButtonEvent((value + 1) as Integer, "pushed", isDigital=true)
        }
    }
    else {
        logWarn "invalid Aqara Cube side facing up value=${value}"
    }    
}

// called from xiaomiLib - refactor !
def sendAqaraCubeOperationModeEvent(final Integer mode)
{
    logDebug "sendAqaraCubeModeEvent: ${mode}"
    if (mode in [0,1]) {
        def eventMap = [:]
        eventMap.value = AqaraCubeModeOpts.options.values()[mode as int]
        eventMap.name = "operationMode"
        eventMap.unit = ""
        eventMap.type = "physical"
        eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} (${mode})"
        sendEvent(eventMap)
        logInfo "${eventMap.descriptionText}"        
    }
    else {
        logWarn "invalid Aqara Cube mode ${mode}"
    }    
}

// 0x000C - Analog Input Cluster
void customParseAnalogInputCluster(final Map descMap) {
    logDebug "customParseAnalogInputCluster: (0x000C) attribute 0x${descMap.attrId} (value ${descMap.value})"
    if (descMap.value == null || descMap.value == 'FFFF') { logWarn "invalid or unknown value"; return } // invalid or unknown value
    if (descMap.attrId == "0055") {
        def value = hexStrToUnsignedInt(descMap.value)
        Float floatValue = Float.intBitsToFloat(value.intValue())   
        logDebug "value=${value} floatValue=${floatValue}" 
        sendAqaraCubeRotateEvent(floatValue as Integer)
    }
    else {
        logDebug "skipped attribute 0x${descMap.attrId}"
        return
    }
}

void sendAqaraCubeRotateEvent(final Integer degrees) {
    String leftRight = degrees < 0 ? 'rotateLeft' : 'rotateRight'
    
    def eventMap = [:]
    eventMap.name = "action"
    eventMap.value = leftRight
    eventMap.unit = "degrees"
    eventMap.type = "physical"
    eventMap.isStateChange = true    // always send these events as a change!
    eventMap.data = [degrees: degrees]
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${degrees} ${eventMap.unit}"
    sendEvent(eventMap)
    logInfo "${eventMap.descriptionText}"
    if (settings?.sendButtonEvent){
        def side = device.currentValue('sideUp', true) as Integer
        sendButtonEvent(side, leftRight == "rotateLeft" ? "held" : "released", isDigital=true)
    }
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
