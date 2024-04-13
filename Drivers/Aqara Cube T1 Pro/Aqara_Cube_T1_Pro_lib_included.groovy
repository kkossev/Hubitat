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
 * ver. 3.0.6  2023-07-16 kkossev  - (dev. branch) library 3.0.6 all
 *
 *                                   TODO: 
 */

static String version() { "3.0.6" }
static String timeStamp() {"2024/04/06 1:13 PM"}

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

/*
  ## Endpoint 3: 

  | Cluster   | Data                                  | Desc                                       |
  | --------- | ------------------------------------- | ------------------------------------------ |
  | genAnalog | {267: 500, 329: 3, presentValue: -51} | 267: NA, 329: side up, presentValue: angle |


*/

/*
        zigbeeModel: ['lumi.sensor_cube', 'lumi.sensor_cube.aqgl01', 'lumi.remote.cagl02'],
        model: 'MFKZQ01LM',
        vendor: 'Xiaomi',
        description: 'Mi/Aqara smart home cube',
        meta: {battery: {voltageToPercentage: '3V_2850_3000'}},
        fromZigbee: [fz.xiaomi_basic, fz.MFKZQ01LM_action_multistate, fz.MFKZQ01LM_action_analog],
        exposes: [e.battery(), e.battery_voltage(), e.angle('action_angle'), e.device_temperature(), e.power_outage_count(false),
            e.cube_side('action_from_side'), e.cube_side('action_side'), e.cube_side('action_to_side'), e.cube_side('side'),
            e.action(['shake', 'wakeup', 'fall', 'tap', 'slide', 'flip180', 'flip90', 'rotate_left', 'rotate_right'])],
        toZigbee: [],
*/

/*
const definition = {
    zigbeeModel: ['lumi.remote.cagl02'],
    model: 'CTP-R01',
    vendor: 'Lumi',
    description: 'Aqara cube T1 Pro',
    meta: { battery: { voltageToPercentage: '3V_2850_3000' } },
    configure: async (device, coordinatorEndpoint, logger) => {
        const endpoint = device.getEndpoint(1);
        await endpoint.write('aqaraOpple', {'mode': 1}, {manufacturerCode: 0x115f});
        await reporting.bind(endpoint, coordinatorEndpoint, ['genOnOff','genPowerCfg','genMultistateInput']);
    },
    fromZigbee: [aqara_opple, action_multistate, fz.MFKZQ01LM_action_analog],


 convert: (model, msg, publish, options, meta) => {
   const value = msg.data['presentValue'];
   let result;
   if (value === 0) result = { action: 'shake' };
   else if (value === 2) result = { action: 'wakeup' };
   else if (value === 4) result = { action: 'hold' };
   else if (value >= 512) result = { action: 'tap', side: value - 511 };
   else if (value >= 256) result = { action: 'slide', side: value - 255 };
   else if (value >= 128) result = { action: 'flip180', side: value - 127 };
   else if (value >= 64) result = { action: 'flip90', action_from_side: Math.floor((value - 64) / 8) + 1, action_to_side: (value % 8) + 1, action_side: (value % 8) + 1, from_side: Math.floor((value - 64) / 8) + 1, to_side: (value % 8) + 1, side: (value % 8) + 1 };
   else if (value >= 1024) result = { action: 'side_up', side_up: value - 1023 };
   if (result && !utils.isLegacyEnabled(options)) { delete result.to_side, delete result.from_side };
   return result ? result : null;
 },
*/


/*
Manufacturer:    LUMI
Endpoint 01 application:    19
Endpoint 01 endpointId:    01
Endpoint 01 idAsInt:    1
Endpoint 01 inClusters:    0000,0003,0001,0012,0006
Endpoint 01 initialized:    true
Endpoint 01 manufacturer:    LUMI
Endpoint 01 model:    lumi.remote.cagl02
Endpoint 01 outClusters:    0000,0003,0019
Endpoint 01 profileId:    0104
Endpoint 01 stage:    4

Endpoint 02 application:    unknown
Endpoint 02 endpointId:    02
Endpoint 02 idAsInt:    2
Endpoint 02 inClusters:    0012
Endpoint 02 initialized:    true
Endpoint 02 manufacturer:    unknown
Endpoint 02 model:    unknown
Endpoint 02 outClusters:    0012
Endpoint 02 profileId:    0104
Endpoint 02 stage:    4

Endpoint 03 application:    unknown
Endpoint 03 endpointId:    03
Endpoint 03 idAsInt:    3
Endpoint 03 inClusters:    000C
Endpoint 03 initialized:    true
Endpoint 03 manufacturer:    unknown
Endpoint 03 model:    unknown
Endpoint 03 outClusters:    000C
Endpoint 03 profileId:    0104
Endpoint 03 stage:    4

*/


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////


// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.0.6', // library marker kkossev.commonLib, line 10
    documentationLink: '' // library marker kkossev.commonLib, line 11
) // library marker kkossev.commonLib, line 12
/* // library marker kkossev.commonLib, line 13
  *  Common ZCL Library // library marker kkossev.commonLib, line 14
  * // library marker kkossev.commonLib, line 15
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 16
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 19
  * // library marker kkossev.commonLib, line 20
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 21
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 22
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 23
  * // library marker kkossev.commonLib, line 24
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 25
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 26
  * // library marker kkossev.commonLib, line 27
  * // library marker kkossev.commonLib, line 28
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 29
  * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 30
  * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 31
  * ver. 3.0.0  2023-11-16 kkossev  - first version 3.x.x // library marker kkossev.commonLib, line 32
  * ver. 3.0.1  2023-12-06 kkossev  - nfo event renamed to Status; txtEnable and logEnable moved to the custom driver settings; 0xFC11 cluster; logEnable is false by default; checkDriverVersion is called on updated() and on healthCheck(); // library marker kkossev.commonLib, line 33
  * ver. 3.0.2  2023-12-17 kkossev  - configure() changes; Groovy Lint, Format and Fix v3.0.0 // library marker kkossev.commonLib, line 34
  * ver. 3.0.3  2024-03-17 kkossev  - more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 35
  * ver. 3.0.4  2024-04-02 kkossev  - removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster; // library marker kkossev.commonLib, line 36
  * ver. 3.0.5  2024-04-05 kkossev  - button methods bug fix; configure() bug fix; handlePm25Event bug fix; // library marker kkossev.commonLib, line 37
  * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; // library marker kkossev.commonLib, line 38
  * // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 40
  *                                   TODO: add custom* handlers for the new drivers! // library marker kkossev.commonLib, line 41
  *                                   TODO: remove the automatic capabilities selectionm for the new drivers! // library marker kkossev.commonLib, line 42
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 43
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 44
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 45
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 46
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 47
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 48
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 49
 * // library marker kkossev.commonLib, line 50
*/ // library marker kkossev.commonLib, line 51

String commonLibVersion() { '3.0.6' } // library marker kkossev.commonLib, line 53
String commonLibStamp() { '2024/04/06 9:51 AM' } // library marker kkossev.commonLib, line 54

import groovy.transform.Field // library marker kkossev.commonLib, line 56
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 57
import hubitat.device.Protocol // library marker kkossev.commonLib, line 58
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 59
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 60
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 61
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 62
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 63
import java.math.BigDecimal // library marker kkossev.commonLib, line 64

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 66

metadata { // library marker kkossev.commonLib, line 68
        if (_DEBUG) { // library marker kkossev.commonLib, line 69
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 70
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 71
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 72
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 73
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 74
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 75
            ] // library marker kkossev.commonLib, line 76
        } // library marker kkossev.commonLib, line 77

        // common capabilities for all device types // library marker kkossev.commonLib, line 79
        capability 'Configuration' // library marker kkossev.commonLib, line 80
        capability 'Refresh' // library marker kkossev.commonLib, line 81
        capability 'Health Check' // library marker kkossev.commonLib, line 82

        // common attributes for all device types // library marker kkossev.commonLib, line 84
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 85
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 86
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 87

        // common commands for all device types // library marker kkossev.commonLib, line 89
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 90
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 91

        // deviceType specific capabilities, commands and attributes // library marker kkossev.commonLib, line 93
        if (deviceType in ['Device']) { // library marker kkossev.commonLib, line 94
            if (_DEBUG) { // library marker kkossev.commonLib, line 95
                command 'getAllProperties',       [[name: 'Get All Properties']] // library marker kkossev.commonLib, line 96
            } // library marker kkossev.commonLib, line 97
        } // library marker kkossev.commonLib, line 98
        if (_DEBUG || (deviceType in ['Dimmer', 'Switch', 'Valve'])) { // library marker kkossev.commonLib, line 99
            command 'zigbeeGroups', [ // library marker kkossev.commonLib, line 100
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 101
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.commonLib, line 102
            ] // library marker kkossev.commonLib, line 103
        } // library marker kkossev.commonLib, line 104
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor', 'LightSensor']) { // library marker kkossev.commonLib, line 105
            capability 'Sensor' // library marker kkossev.commonLib, line 106
        } // library marker kkossev.commonLib, line 107
        if (deviceType in  ['Device', 'MotionSensor']) { // library marker kkossev.commonLib, line 108
            capability 'MotionSensor' // library marker kkossev.commonLib, line 109
        } // library marker kkossev.commonLib, line 110
        if (deviceType in  ['Device', 'Switch', 'Relay', 'Outlet', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 111
            capability 'Actuator' // library marker kkossev.commonLib, line 112
        } // library marker kkossev.commonLib, line 113
        if (deviceType in  ['Device', 'THSensor', 'LightSensor', 'MotionSensor', 'Thermostat']) { // library marker kkossev.commonLib, line 114
            capability 'Battery' // library marker kkossev.commonLib, line 115
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 116
        } // library marker kkossev.commonLib, line 117
        if (deviceType in  ['Device', 'Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 118
            capability 'Switch' // library marker kkossev.commonLib, line 119
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 120
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 121
            } // library marker kkossev.commonLib, line 122
        } // library marker kkossev.commonLib, line 123
        if (deviceType in ['Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 124
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 125
        } // library marker kkossev.commonLib, line 126
        if (deviceType in  ['Device']) { // library marker kkossev.commonLib, line 127
            capability 'Momentary' // library marker kkossev.commonLib, line 128
        } // library marker kkossev.commonLib, line 129
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 130
            capability 'TemperatureMeasurement' // library marker kkossev.commonLib, line 131
        } // library marker kkossev.commonLib, line 132
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 133
            capability 'RelativeHumidityMeasurement' // library marker kkossev.commonLib, line 134
        } // library marker kkossev.commonLib, line 135
        if (deviceType in  ['Device', 'LightSensor']) { // library marker kkossev.commonLib, line 136
            capability 'IlluminanceMeasurement' // library marker kkossev.commonLib, line 137
        } // library marker kkossev.commonLib, line 138

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 140
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 141

    preferences { // library marker kkossev.commonLib, line 143
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 144
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 145
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 146

        if (device) { // library marker kkossev.commonLib, line 148
            if ((device.hasCapability('TemperatureMeasurement') || device.hasCapability('RelativeHumidityMeasurement') || device.hasCapability('IlluminanceMeasurement'))) { // library marker kkossev.commonLib, line 149
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 150
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.commonLib, line 151
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 152
                } // library marker kkossev.commonLib, line 153
            } // library marker kkossev.commonLib, line 154
            if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 155
                input name: 'illuminanceThreshold', type: 'number', title: '<b>Illuminance Reporting Threshold</b>', description: '<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 156
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00 // library marker kkossev.commonLib, line 157
            } // library marker kkossev.commonLib, line 158

            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 160
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 161
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 162
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 163
                if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 164
                    input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 165
                } // library marker kkossev.commonLib, line 166
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 167
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 168
                } // library marker kkossev.commonLib, line 169
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 170
            } // library marker kkossev.commonLib, line 171
        } // library marker kkossev.commonLib, line 172
    } // library marker kkossev.commonLib, line 173
} // library marker kkossev.commonLib, line 174

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 176
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 177
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 178
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 179
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 180
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 181
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 182
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 183
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 184
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 185
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 186
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 187

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 189
    defaultValue: 1, // library marker kkossev.commonLib, line 190
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 191
] // library marker kkossev.commonLib, line 192
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 193
    defaultValue: 240, // library marker kkossev.commonLib, line 194
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 195
] // library marker kkossev.commonLib, line 196
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 197
    defaultValue: 0, // library marker kkossev.commonLib, line 198
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 199
] // library marker kkossev.commonLib, line 200

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 202
    defaultValue: 0, // library marker kkossev.commonLib, line 203
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 204
] // library marker kkossev.commonLib, line 205
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 206
    defaultValue: 0, // library marker kkossev.commonLib, line 207
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 208
] // library marker kkossev.commonLib, line 209

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 211
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 212
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 213
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 214
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 215
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 216
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 217
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 218
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 219
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 220
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 221
] // library marker kkossev.commonLib, line 222

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 224
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 225
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 226
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 227
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 228
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 229
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 230
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 231
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 232

/** // library marker kkossev.commonLib, line 234
 * Parse Zigbee message // library marker kkossev.commonLib, line 235
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 236
 */ // library marker kkossev.commonLib, line 237
void parse(final String description) { // library marker kkossev.commonLib, line 238
    checkDriverVersion() // library marker kkossev.commonLib, line 239
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 240
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 241
    setHealthStatusOnline() // library marker kkossev.commonLib, line 242

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 244
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 245
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 246
            parseIasMessage(description) // library marker kkossev.commonLib, line 247
        } // library marker kkossev.commonLib, line 248
        else { // library marker kkossev.commonLib, line 249
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 250
        } // library marker kkossev.commonLib, line 251
        return // library marker kkossev.commonLib, line 252
    } // library marker kkossev.commonLib, line 253
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 254
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 255
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 256
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 257
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 258
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 259
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 260
        return // library marker kkossev.commonLib, line 261
    } // library marker kkossev.commonLib, line 262
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 263
        return // library marker kkossev.commonLib, line 264
    } // library marker kkossev.commonLib, line 265
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 266

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 268
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 269
        return // library marker kkossev.commonLib, line 270
    } // library marker kkossev.commonLib, line 271
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 272
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 273
        return // library marker kkossev.commonLib, line 274
    } // library marker kkossev.commonLib, line 275
    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 276
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 277
    // // library marker kkossev.commonLib, line 278
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 279
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 280
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 281

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 283
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 284
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 285
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 286
            break // library marker kkossev.commonLib, line 287
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 288
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 289
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 290
            break // library marker kkossev.commonLib, line 291
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 292
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 293
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 294
            break // library marker kkossev.commonLib, line 295
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 296
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 297
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 300
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 301
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 302
            break // library marker kkossev.commonLib, line 303
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 304
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 305
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 306
            break // library marker kkossev.commonLib, line 307
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 308
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 309
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 310
            break // library marker kkossev.commonLib, line 311
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 312
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 313
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 316
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 319
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 320
            break // library marker kkossev.commonLib, line 321
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 322
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 323
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 324
            break // library marker kkossev.commonLib, line 325
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 326
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 327
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 328
            break // library marker kkossev.commonLib, line 329
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 330
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 331
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 332
            break // library marker kkossev.commonLib, line 333
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 334
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 335
            break // library marker kkossev.commonLib, line 336
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 337
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 338
            break // library marker kkossev.commonLib, line 339
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 340
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 341
            break // library marker kkossev.commonLib, line 342
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 343
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 344
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 345
            break // library marker kkossev.commonLib, line 346
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 347
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 348
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 349
            break // library marker kkossev.commonLib, line 350
        case 0xE002 : // library marker kkossev.commonLib, line 351
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 352
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 353
            break // library marker kkossev.commonLib, line 354
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 355
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 356
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 357
            break // library marker kkossev.commonLib, line 358
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 359
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 360
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 361
            break // library marker kkossev.commonLib, line 362
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 363
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 364
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 365
            break // library marker kkossev.commonLib, line 366
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 367
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 368
            break // library marker kkossev.commonLib, line 369
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 370
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 371
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 372
            break // library marker kkossev.commonLib, line 373
        default: // library marker kkossev.commonLib, line 374
            if (settings.logEnable) { // library marker kkossev.commonLib, line 375
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 376
            } // library marker kkossev.commonLib, line 377
            break // library marker kkossev.commonLib, line 378
    } // library marker kkossev.commonLib, line 379
} // library marker kkossev.commonLib, line 380

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 382
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 383
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 384
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 385
    } // library marker kkossev.commonLib, line 386
    return false // library marker kkossev.commonLib, line 387
} // library marker kkossev.commonLib, line 388

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 390
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 391
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 392
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 393
    } // library marker kkossev.commonLib, line 394
    return false // library marker kkossev.commonLib, line 395
} // library marker kkossev.commonLib, line 396

/** // library marker kkossev.commonLib, line 398
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 399
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 400
 */ // library marker kkossev.commonLib, line 401
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 402
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 403
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 404
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 405
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 406
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 407
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 408
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 409
    } // library marker kkossev.commonLib, line 410
    else { // library marker kkossev.commonLib, line 411
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 412
    } // library marker kkossev.commonLib, line 413
} // library marker kkossev.commonLib, line 414

/** // library marker kkossev.commonLib, line 416
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 417
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 418
 */ // library marker kkossev.commonLib, line 419
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 420
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 421
    switch (commandId) { // library marker kkossev.commonLib, line 422
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 423
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 424
            break // library marker kkossev.commonLib, line 425
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 426
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 427
            break // library marker kkossev.commonLib, line 428
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 429
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 430
            break // library marker kkossev.commonLib, line 431
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 432
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 433
            break // library marker kkossev.commonLib, line 434
        case 0x0B: // default command response // library marker kkossev.commonLib, line 435
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 436
            break // library marker kkossev.commonLib, line 437
        default: // library marker kkossev.commonLib, line 438
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 439
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 440
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 441
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 442
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 443
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 444
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 445
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 446
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 447
            } // library marker kkossev.commonLib, line 448
            break // library marker kkossev.commonLib, line 449
    } // library marker kkossev.commonLib, line 450
} // library marker kkossev.commonLib, line 451

/** // library marker kkossev.commonLib, line 453
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 454
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 455
 */ // library marker kkossev.commonLib, line 456
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 457
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 458
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 459
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 460
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 461
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 462
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 463
    } // library marker kkossev.commonLib, line 464
    else { // library marker kkossev.commonLib, line 465
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 466
    } // library marker kkossev.commonLib, line 467
} // library marker kkossev.commonLib, line 468

/** // library marker kkossev.commonLib, line 470
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 471
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 472
 */ // library marker kkossev.commonLib, line 473
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 474
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 475
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 476
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 477
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 478
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 479
    } // library marker kkossev.commonLib, line 480
    else { // library marker kkossev.commonLib, line 481
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 482
    } // library marker kkossev.commonLib, line 483
} // library marker kkossev.commonLib, line 484

/** // library marker kkossev.commonLib, line 486
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 487
 */ // library marker kkossev.commonLib, line 488
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 489
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 490
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 491
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 492
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 493
        state.reportingEnabled = true // library marker kkossev.commonLib, line 494
    } // library marker kkossev.commonLib, line 495
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 496
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 497
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 498
    } else { // library marker kkossev.commonLib, line 499
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 500
    } // library marker kkossev.commonLib, line 501
} // library marker kkossev.commonLib, line 502

/** // library marker kkossev.commonLib, line 504
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 505
 */ // library marker kkossev.commonLib, line 506
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 507
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 508
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 509
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 510
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 511
    if (status == 0) { // library marker kkossev.commonLib, line 512
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 513
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 514
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 515
        int delta = 0 // library marker kkossev.commonLib, line 516
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 517
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 518
        } // library marker kkossev.commonLib, line 519
        else { // library marker kkossev.commonLib, line 520
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 521
        } // library marker kkossev.commonLib, line 522
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 523
    } // library marker kkossev.commonLib, line 524
    else { // library marker kkossev.commonLib, line 525
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 526
    } // library marker kkossev.commonLib, line 527
} // library marker kkossev.commonLib, line 528

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 530
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 531
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 532
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 533
        return false // library marker kkossev.commonLib, line 534
    } // library marker kkossev.commonLib, line 535
    // execute the customHandler function // library marker kkossev.commonLib, line 536
    boolean result = false // library marker kkossev.commonLib, line 537
    try { // library marker kkossev.commonLib, line 538
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 539
    } // library marker kkossev.commonLib, line 540
    catch (e) { // library marker kkossev.commonLib, line 541
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 542
        return false // library marker kkossev.commonLib, line 543
    } // library marker kkossev.commonLib, line 544
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 545
    return result // library marker kkossev.commonLib, line 546
} // library marker kkossev.commonLib, line 547

/** // library marker kkossev.commonLib, line 549
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 550
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 551
 */ // library marker kkossev.commonLib, line 552
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 553
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 554
    final String commandId = data[0] // library marker kkossev.commonLib, line 555
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 556
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 557
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 558
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 559
    } else { // library marker kkossev.commonLib, line 560
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 561
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 562
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 563
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 564
        } // library marker kkossev.commonLib, line 565
    } // library marker kkossev.commonLib, line 566
} // library marker kkossev.commonLib, line 567

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 569
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 570
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 571
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 572
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 573
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 574
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 575
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 576
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 577
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 578
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 579
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 580
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 581
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 582
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 583
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 584

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 586
    0x00: 'Success', // library marker kkossev.commonLib, line 587
    0x01: 'Failure', // library marker kkossev.commonLib, line 588
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 589
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 590
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 591
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 592
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 593
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 594
    0x88: 'Read Only', // library marker kkossev.commonLib, line 595
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 596
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 597
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 598
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 599
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 600
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 601
    0x94: 'Time out', // library marker kkossev.commonLib, line 602
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 603
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 604
] // library marker kkossev.commonLib, line 605

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 607
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 608
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 609
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 610
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 611
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 612
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 613
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 614
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 615
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 616
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 617
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 618
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 619
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 620
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 621
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 622
] // library marker kkossev.commonLib, line 623

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 625
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 626
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 627
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 628
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 629
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 630
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 631
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 632
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 633
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 634
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 635
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 636
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 637
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 638
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 639
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 640
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 641
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 642
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 643
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 644
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 645
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 646
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 647
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 648
] // library marker kkossev.commonLib, line 649

/* // library marker kkossev.commonLib, line 651
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 652
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 653
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 654
 */ // library marker kkossev.commonLib, line 655
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 656
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 657
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 658
    } // library marker kkossev.commonLib, line 659
    else { // library marker kkossev.commonLib, line 660
        logWarn 'Xiaomi cluster 0xFCC0' // library marker kkossev.commonLib, line 661
    } // library marker kkossev.commonLib, line 662
} // library marker kkossev.commonLib, line 663

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 665
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 666
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 667
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 668
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 669
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 670
    return avg // library marker kkossev.commonLib, line 671
} // library marker kkossev.commonLib, line 672

/* // library marker kkossev.commonLib, line 674
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 675
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 676
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 677
*/ // library marker kkossev.commonLib, line 678
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 679

/** // library marker kkossev.commonLib, line 681
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 682
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 683
 */ // library marker kkossev.commonLib, line 684
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 685
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 686
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 687
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 688
        case 0x0000: // library marker kkossev.commonLib, line 689
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 690
            break // library marker kkossev.commonLib, line 691
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 692
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 693
            if (isPing) { // library marker kkossev.commonLib, line 694
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 695
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 696
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 697
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 698
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 699
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 700
                    sendRttEvent() // library marker kkossev.commonLib, line 701
                } // library marker kkossev.commonLib, line 702
                else { // library marker kkossev.commonLib, line 703
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 704
                } // library marker kkossev.commonLib, line 705
                state.states['isPing'] = false // library marker kkossev.commonLib, line 706
            } // library marker kkossev.commonLib, line 707
            else { // library marker kkossev.commonLib, line 708
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 709
            } // library marker kkossev.commonLib, line 710
            break // library marker kkossev.commonLib, line 711
        case 0x0004: // library marker kkossev.commonLib, line 712
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 713
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 714
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 715
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 716
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 717
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 718
            } // library marker kkossev.commonLib, line 719
            break // library marker kkossev.commonLib, line 720
        case 0x0005: // library marker kkossev.commonLib, line 721
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 722
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 723
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 724
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 725
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 726
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 727
            } // library marker kkossev.commonLib, line 728
            break // library marker kkossev.commonLib, line 729
        case 0x0007: // library marker kkossev.commonLib, line 730
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 731
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 732
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 733
            break // library marker kkossev.commonLib, line 734
        case 0xFFDF: // library marker kkossev.commonLib, line 735
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 736
            break // library marker kkossev.commonLib, line 737
        case 0xFFE2: // library marker kkossev.commonLib, line 738
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 739
            break // library marker kkossev.commonLib, line 740
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 741
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 742
            break // library marker kkossev.commonLib, line 743
        case 0xFFFE: // library marker kkossev.commonLib, line 744
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 745
            break // library marker kkossev.commonLib, line 746
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 747
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 748
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 749
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 750
            break // library marker kkossev.commonLib, line 751
        default: // library marker kkossev.commonLib, line 752
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 753
            break // library marker kkossev.commonLib, line 754
    } // library marker kkossev.commonLib, line 755
} // library marker kkossev.commonLib, line 756

/* // library marker kkossev.commonLib, line 758
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 759
 * power cluster            0x0001 // library marker kkossev.commonLib, line 760
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 761
*/ // library marker kkossev.commonLib, line 762
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 763
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 764
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 765
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 766
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 767
    } // library marker kkossev.commonLib, line 768

    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 770
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 771
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 772
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 773
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 774
        } // library marker kkossev.commonLib, line 775
    } // library marker kkossev.commonLib, line 776
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 777
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 778
    } // library marker kkossev.commonLib, line 779
    else { // library marker kkossev.commonLib, line 780
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 781
    } // library marker kkossev.commonLib, line 782
} // library marker kkossev.commonLib, line 783

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.commonLib, line 785
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 786
    Map result = [:] // library marker kkossev.commonLib, line 787
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.commonLib, line 788
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.commonLib, line 789
        BigDecimal minVolts = 2.2 // library marker kkossev.commonLib, line 790
        BigDecimal maxVolts = 3.2 // library marker kkossev.commonLib, line 791
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 792
        int roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 793
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 794
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 795
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 796
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 797
            result.name = 'battery' // library marker kkossev.commonLib, line 798
            result.unit  = '%' // library marker kkossev.commonLib, line 799
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 800
        } // library marker kkossev.commonLib, line 801
        else { // library marker kkossev.commonLib, line 802
            result.value = volts // library marker kkossev.commonLib, line 803
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 804
            result.unit  = 'V' // library marker kkossev.commonLib, line 805
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 806
        } // library marker kkossev.commonLib, line 807
        result.type = 'physical' // library marker kkossev.commonLib, line 808
        result.isStateChange = true // library marker kkossev.commonLib, line 809
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 810
        sendEvent(result) // library marker kkossev.commonLib, line 811
    } // library marker kkossev.commonLib, line 812
    else { // library marker kkossev.commonLib, line 813
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 814
    } // library marker kkossev.commonLib, line 815
} // library marker kkossev.commonLib, line 816

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.commonLib, line 818
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 819
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 820
        return // library marker kkossev.commonLib, line 821
    } // library marker kkossev.commonLib, line 822
    Map map = [:] // library marker kkossev.commonLib, line 823
    map.name = 'battery' // library marker kkossev.commonLib, line 824
    map.timeStamp = now() // library marker kkossev.commonLib, line 825
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 826
    map.unit  = '%' // library marker kkossev.commonLib, line 827
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 828
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 829
    map.isStateChange = true // library marker kkossev.commonLib, line 830
    // // library marker kkossev.commonLib, line 831
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.commonLib, line 832
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 833
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 834
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 835
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 836
        // send it now! // library marker kkossev.commonLib, line 837
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 838
    } // library marker kkossev.commonLib, line 839
    else { // library marker kkossev.commonLib, line 840
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 841
        map.delayed = delayedTime // library marker kkossev.commonLib, line 842
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 843
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 844
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 845
    } // library marker kkossev.commonLib, line 846
} // library marker kkossev.commonLib, line 847

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 849
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 850
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 851
    sendEvent(map) // library marker kkossev.commonLib, line 852
} // library marker kkossev.commonLib, line 853

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 855
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 856
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 857
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 858
    sendEvent(map) // library marker kkossev.commonLib, line 859
} // library marker kkossev.commonLib, line 860

/* // library marker kkossev.commonLib, line 862
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 863
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 864
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 865
*/ // library marker kkossev.commonLib, line 866
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 867
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 868
    logDebug 'unprocessed parseIdentityCluster' // library marker kkossev.commonLib, line 869
} // library marker kkossev.commonLib, line 870

/* // library marker kkossev.commonLib, line 872
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 873
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 874
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 875
*/ // library marker kkossev.commonLib, line 876
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 877
    if (this.respondsTo('customParseScenesCluster')) { // library marker kkossev.commonLib, line 878
        customParseScenesCluster(descMap) // library marker kkossev.commonLib, line 879
    } // library marker kkossev.commonLib, line 880
    else { // library marker kkossev.commonLib, line 881
        logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 882
    } // library marker kkossev.commonLib, line 883
} // library marker kkossev.commonLib, line 884

/* // library marker kkossev.commonLib, line 886
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 887
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 888
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 889
*/ // library marker kkossev.commonLib, line 890
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 891
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 892
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 893
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 894
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 895
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 896
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 897
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 898
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 899
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 900
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 901
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 902
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 903
            } // library marker kkossev.commonLib, line 904
            else { // library marker kkossev.commonLib, line 905
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 906
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 907
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 908
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 909
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 910
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 911
                        return // library marker kkossev.commonLib, line 912
                    } // library marker kkossev.commonLib, line 913
                } // library marker kkossev.commonLib, line 914
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 915
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.commonLib, line 916
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 917
            } // library marker kkossev.commonLib, line 918
            break // library marker kkossev.commonLib, line 919
        case 0x01: // View group // library marker kkossev.commonLib, line 920
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 921
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 922
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 923
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 924
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 925
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 926
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 927
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 928
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 929
            } // library marker kkossev.commonLib, line 930
            else { // library marker kkossev.commonLib, line 931
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 932
            } // library marker kkossev.commonLib, line 933
            break // library marker kkossev.commonLib, line 934
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 935
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 936
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 937
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 938
            final Set<String> groups = [] // library marker kkossev.commonLib, line 939
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 940
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 941
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 942
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 943
            } // library marker kkossev.commonLib, line 944
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 945
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 946
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 947
            break // library marker kkossev.commonLib, line 948
        case 0x03: // Remove group // library marker kkossev.commonLib, line 949
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 950
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 951
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 952
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 953
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 954
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 955
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 956
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 957
            } // library marker kkossev.commonLib, line 958
            else { // library marker kkossev.commonLib, line 959
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 960
            } // library marker kkossev.commonLib, line 961
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 962
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 963
            if (index >= 0) { // library marker kkossev.commonLib, line 964
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 965
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 966
            } // library marker kkossev.commonLib, line 967
            break // library marker kkossev.commonLib, line 968
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 969
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 970
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 971
            break // library marker kkossev.commonLib, line 972
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 973
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.commonLib, line 974
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 975
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 976
            break // library marker kkossev.commonLib, line 977
        default: // library marker kkossev.commonLib, line 978
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 979
            break // library marker kkossev.commonLib, line 980
    } // library marker kkossev.commonLib, line 981
} // library marker kkossev.commonLib, line 982

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 984
List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 985
    List<String> cmds = [] // library marker kkossev.commonLib, line 986
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 987
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 988
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 989
        return [] // library marker kkossev.commonLib, line 990
    } // library marker kkossev.commonLib, line 991
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 992
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 993
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 994
    return cmds // library marker kkossev.commonLib, line 995
} // library marker kkossev.commonLib, line 996

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 998
List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 999
    List<String> cmds = [] // library marker kkossev.commonLib, line 1000
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1001
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1002
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1003
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1004
    return cmds // library marker kkossev.commonLib, line 1005
} // library marker kkossev.commonLib, line 1006

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1008
List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1009
    List<String> cmds = [] // library marker kkossev.commonLib, line 1010
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.commonLib, line 1011
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1012
    return cmds // library marker kkossev.commonLib, line 1013
} // library marker kkossev.commonLib, line 1014

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1016
List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1017
    List<String> cmds = [] // library marker kkossev.commonLib, line 1018
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1019
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1020
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1021
        return [] // library marker kkossev.commonLib, line 1022
    } // library marker kkossev.commonLib, line 1023
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1024
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1025
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1026
    return cmds // library marker kkossev.commonLib, line 1027
} // library marker kkossev.commonLib, line 1028

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1030
List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1031
    List<String> cmds = [] // library marker kkossev.commonLib, line 1032
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1033
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1034
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1035
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1036
    return cmds // library marker kkossev.commonLib, line 1037
} // library marker kkossev.commonLib, line 1038

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1040
List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1041
    List<String> cmds = [] // library marker kkossev.commonLib, line 1042
    //final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1043
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1044
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1045
    return cmds // library marker kkossev.commonLib, line 1046
} // library marker kkossev.commonLib, line 1047

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1049
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.commonLib, line 1050
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1051
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1052
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1053
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1054
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1055
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1056
] // library marker kkossev.commonLib, line 1057

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1059
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.commonLib, line 1060
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1061
    List<String> cmds = [] // library marker kkossev.commonLib, line 1062
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1063
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.commonLib, line 1064
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1065
    def value // library marker kkossev.commonLib, line 1066
    Boolean validated = false // library marker kkossev.commonLib, line 1067
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1068
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1069
        return // library marker kkossev.commonLib, line 1070
    } // library marker kkossev.commonLib, line 1071
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1072
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.commonLib, line 1073
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1074
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1075
        return // library marker kkossev.commonLib, line 1076
    } // library marker kkossev.commonLib, line 1077
    // // library marker kkossev.commonLib, line 1078
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1079
    def func // library marker kkossev.commonLib, line 1080
    try { // library marker kkossev.commonLib, line 1081
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1082
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1083
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1084
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1085
    } // library marker kkossev.commonLib, line 1086
    catch (e) { // library marker kkossev.commonLib, line 1087
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1088
        return // library marker kkossev.commonLib, line 1089
    } // library marker kkossev.commonLib, line 1090

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1092
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1093
} // library marker kkossev.commonLib, line 1094

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1096
void groupCommandsHelp(val) { // library marker kkossev.commonLib, line 1097
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.commonLib, line 1098
} // library marker kkossev.commonLib, line 1099

/* // library marker kkossev.commonLib, line 1101
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1102
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1103
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1104
*/ // library marker kkossev.commonLib, line 1105

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1107
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 1108
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 1109
    } // library marker kkossev.commonLib, line 1110
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1111
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1112
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1113
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1114
    } // library marker kkossev.commonLib, line 1115
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 1116
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1117
    } // library marker kkossev.commonLib, line 1118
    else { // library marker kkossev.commonLib, line 1119
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 1120
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 1121
    } // library marker kkossev.commonLib, line 1122
} // library marker kkossev.commonLib, line 1123

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 1125
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 1126
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1127

void toggle() { // library marker kkossev.commonLib, line 1129
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 1130
    String state = '' // library marker kkossev.commonLib, line 1131
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 1132
        state = 'on' // library marker kkossev.commonLib, line 1133
    } // library marker kkossev.commonLib, line 1134
    else { // library marker kkossev.commonLib, line 1135
        state = 'off' // library marker kkossev.commonLib, line 1136
    } // library marker kkossev.commonLib, line 1137
    descriptionText += state // library marker kkossev.commonLib, line 1138
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 1139
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1140
} // library marker kkossev.commonLib, line 1141

void off() { // library marker kkossev.commonLib, line 1143
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 1144
        customOff() // library marker kkossev.commonLib, line 1145
        return // library marker kkossev.commonLib, line 1146
    } // library marker kkossev.commonLib, line 1147
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1148
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1149
        return // library marker kkossev.commonLib, line 1150
    } // library marker kkossev.commonLib, line 1151
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 1152
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1153
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 1154
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1155
        if (currentState == 'off') { // library marker kkossev.commonLib, line 1156
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1157
        } // library marker kkossev.commonLib, line 1158
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1159
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1160
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1161
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1162
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1163
    } // library marker kkossev.commonLib, line 1164
    /* // library marker kkossev.commonLib, line 1165
    else { // library marker kkossev.commonLib, line 1166
        if (currentState != 'off') { // library marker kkossev.commonLib, line 1167
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1168
        } // library marker kkossev.commonLib, line 1169
        else { // library marker kkossev.commonLib, line 1170
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 1171
            return // library marker kkossev.commonLib, line 1172
        } // library marker kkossev.commonLib, line 1173
    } // library marker kkossev.commonLib, line 1174
    */ // library marker kkossev.commonLib, line 1175

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1177
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1178
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1179
} // library marker kkossev.commonLib, line 1180

void on() { // library marker kkossev.commonLib, line 1182
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 1183
        customOn() // library marker kkossev.commonLib, line 1184
        return // library marker kkossev.commonLib, line 1185
    } // library marker kkossev.commonLib, line 1186
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 1187
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1188
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 1189
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1190
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 1191
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1192
        } // library marker kkossev.commonLib, line 1193
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1194
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1195
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1196
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1197
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1198
    } // library marker kkossev.commonLib, line 1199
    /* // library marker kkossev.commonLib, line 1200
    else { // library marker kkossev.commonLib, line 1201
        if (currentState != 'on') { // library marker kkossev.commonLib, line 1202
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1203
        } // library marker kkossev.commonLib, line 1204
        else { // library marker kkossev.commonLib, line 1205
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 1206
            return // library marker kkossev.commonLib, line 1207
        } // library marker kkossev.commonLib, line 1208
    } // library marker kkossev.commonLib, line 1209
    */ // library marker kkossev.commonLib, line 1210
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1211
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1212
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1213
} // library marker kkossev.commonLib, line 1214

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 1216
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 1217
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 1218
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 1219
    } // library marker kkossev.commonLib, line 1220
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1221
    Map map = [:] // library marker kkossev.commonLib, line 1222
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 1223
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 1224
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 1225
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1226
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1227
        return // library marker kkossev.commonLib, line 1228
    } // library marker kkossev.commonLib, line 1229
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1230
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 1231
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1232
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 1233
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1234
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1235
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 1236
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1237
    } else { // library marker kkossev.commonLib, line 1238
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1239
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1240
    } // library marker kkossev.commonLib, line 1241
    map.name = 'switch' // library marker kkossev.commonLib, line 1242
    map.value = value // library marker kkossev.commonLib, line 1243
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1244
    if (isRefresh) { // library marker kkossev.commonLib, line 1245
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1246
        map.isStateChange = true // library marker kkossev.commonLib, line 1247
    } else { // library marker kkossev.commonLib, line 1248
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1249
    } // library marker kkossev.commonLib, line 1250
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1251
    sendEvent(map) // library marker kkossev.commonLib, line 1252
    clearIsDigital() // library marker kkossev.commonLib, line 1253
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 1254
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 1255
    } // library marker kkossev.commonLib, line 1256
} // library marker kkossev.commonLib, line 1257

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 1259
    '0': 'switch off', // library marker kkossev.commonLib, line 1260
    '1': 'switch on', // library marker kkossev.commonLib, line 1261
    '2': 'switch last state' // library marker kkossev.commonLib, line 1262
] // library marker kkossev.commonLib, line 1263

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 1265
    '0': 'toggle', // library marker kkossev.commonLib, line 1266
    '1': 'state', // library marker kkossev.commonLib, line 1267
    '2': 'momentary' // library marker kkossev.commonLib, line 1268
] // library marker kkossev.commonLib, line 1269

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 1271
    Map descMap = [:] // library marker kkossev.commonLib, line 1272
    try { // library marker kkossev.commonLib, line 1273
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1274
    } // library marker kkossev.commonLib, line 1275
    catch (e1) { // library marker kkossev.commonLib, line 1276
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1277
        // try alternative custom parsing // library marker kkossev.commonLib, line 1278
        descMap = [:] // library marker kkossev.commonLib, line 1279
        try { // library marker kkossev.commonLib, line 1280
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1281
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1282
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1283
            } // library marker kkossev.commonLib, line 1284
        } // library marker kkossev.commonLib, line 1285
        catch (e2) { // library marker kkossev.commonLib, line 1286
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1287
            return [:] // library marker kkossev.commonLib, line 1288
        } // library marker kkossev.commonLib, line 1289
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1290
    } // library marker kkossev.commonLib, line 1291
    return descMap // library marker kkossev.commonLib, line 1292
} // library marker kkossev.commonLib, line 1293

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1295
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1296
        return false // library marker kkossev.commonLib, line 1297
    } // library marker kkossev.commonLib, line 1298
    // try to parse ... // library marker kkossev.commonLib, line 1299
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1300
    Map descMap = [:] // library marker kkossev.commonLib, line 1301
    try { // library marker kkossev.commonLib, line 1302
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1303
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1304
    } // library marker kkossev.commonLib, line 1305
    catch (e) { // library marker kkossev.commonLib, line 1306
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1307
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1308
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1309
        return true // library marker kkossev.commonLib, line 1310
    } // library marker kkossev.commonLib, line 1311

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1313
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1314
    } // library marker kkossev.commonLib, line 1315
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1316
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1317
    } // library marker kkossev.commonLib, line 1318
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1319
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1320
    } // library marker kkossev.commonLib, line 1321
    else { // library marker kkossev.commonLib, line 1322
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1323
        return false // library marker kkossev.commonLib, line 1324
    } // library marker kkossev.commonLib, line 1325
    return true    // processed // library marker kkossev.commonLib, line 1326
} // library marker kkossev.commonLib, line 1327

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1329
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 1330
  /* // library marker kkossev.commonLib, line 1331
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1332
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1333
        return true // library marker kkossev.commonLib, line 1334
    } // library marker kkossev.commonLib, line 1335
*/ // library marker kkossev.commonLib, line 1336
    Map descMap = [:] // library marker kkossev.commonLib, line 1337
    try { // library marker kkossev.commonLib, line 1338
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1339
    } // library marker kkossev.commonLib, line 1340
    catch (e1) { // library marker kkossev.commonLib, line 1341
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1342
        // try alternative custom parsing // library marker kkossev.commonLib, line 1343
        descMap = [:] // library marker kkossev.commonLib, line 1344
        try { // library marker kkossev.commonLib, line 1345
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1346
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1347
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1348
            } // library marker kkossev.commonLib, line 1349
        } // library marker kkossev.commonLib, line 1350
        catch (e2) { // library marker kkossev.commonLib, line 1351
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1352
            return true // library marker kkossev.commonLib, line 1353
        } // library marker kkossev.commonLib, line 1354
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1355
    } // library marker kkossev.commonLib, line 1356
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1357
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1358
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1359
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1360
        return false // library marker kkossev.commonLib, line 1361
    } // library marker kkossev.commonLib, line 1362
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1363
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1364
    // attribute report received // library marker kkossev.commonLib, line 1365
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1366
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1367
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1368
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1369
    } // library marker kkossev.commonLib, line 1370
    attrData.each { // library marker kkossev.commonLib, line 1371
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1372
        //def map = [:] // library marker kkossev.commonLib, line 1373
        if (it.status == '86') { // library marker kkossev.commonLib, line 1374
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1375
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1376
        } // library marker kkossev.commonLib, line 1377
        switch (it.cluster) { // library marker kkossev.commonLib, line 1378
            case '0000' : // library marker kkossev.commonLib, line 1379
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1380
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1381
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1382
                } // library marker kkossev.commonLib, line 1383
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1384
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1385
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1386
                } // library marker kkossev.commonLib, line 1387
                else { // library marker kkossev.commonLib, line 1388
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1389
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1390
                } // library marker kkossev.commonLib, line 1391
                break // library marker kkossev.commonLib, line 1392
            default : // library marker kkossev.commonLib, line 1393
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1394
                break // library marker kkossev.commonLib, line 1395
        } // switch // library marker kkossev.commonLib, line 1396
    } // for each attribute // library marker kkossev.commonLib, line 1397
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1398
} // library marker kkossev.commonLib, line 1399

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1401

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1403
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1404
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1405
    def mode // library marker kkossev.commonLib, line 1406
    String attrName // library marker kkossev.commonLib, line 1407
    if (it.value == null) { // library marker kkossev.commonLib, line 1408
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1409
        return // library marker kkossev.commonLib, line 1410
    } // library marker kkossev.commonLib, line 1411
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1412
    switch (it.attrId) { // library marker kkossev.commonLib, line 1413
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1414
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1415
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1416
            break // library marker kkossev.commonLib, line 1417
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1418
            attrName = 'On Time' // library marker kkossev.commonLib, line 1419
            mode = value // library marker kkossev.commonLib, line 1420
            break // library marker kkossev.commonLib, line 1421
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1422
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1423
            mode = value // library marker kkossev.commonLib, line 1424
            break // library marker kkossev.commonLib, line 1425
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1426
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1427
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1428
            break // library marker kkossev.commonLib, line 1429
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1430
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1431
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1432
            break // library marker kkossev.commonLib, line 1433
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1434
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1435
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1436
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1437
            } // library marker kkossev.commonLib, line 1438
            else { // library marker kkossev.commonLib, line 1439
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1440
            } // library marker kkossev.commonLib, line 1441
            break // library marker kkossev.commonLib, line 1442
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1443
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1444
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1445
            break // library marker kkossev.commonLib, line 1446
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1447
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1448
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1449
            break // library marker kkossev.commonLib, line 1450
        default : // library marker kkossev.commonLib, line 1451
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1452
            return // library marker kkossev.commonLib, line 1453
    } // library marker kkossev.commonLib, line 1454
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1455
} // library marker kkossev.commonLib, line 1456

/* // library marker kkossev.commonLib, line 1458
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1459
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1460
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1461
*/ // library marker kkossev.commonLib, line 1462
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1463
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1464
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1465
    } // library marker kkossev.commonLib, line 1466
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1467
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1468
    } // library marker kkossev.commonLib, line 1469
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1470
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1471
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1472
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1473
    } // library marker kkossev.commonLib, line 1474
    else { // library marker kkossev.commonLib, line 1475
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1476
    } // library marker kkossev.commonLib, line 1477
} // library marker kkossev.commonLib, line 1478

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.commonLib, line 1480
    int value = rawValue as int // library marker kkossev.commonLib, line 1481
    if (value < 0) { value = 0 } // library marker kkossev.commonLib, line 1482
    if (value > 100) { value = 100 } // library marker kkossev.commonLib, line 1483
    Map map = [:] // library marker kkossev.commonLib, line 1484

    boolean isDigital = state.states['isDigital'] // library marker kkossev.commonLib, line 1486
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1487

    map.name = 'level' // library marker kkossev.commonLib, line 1489
    map.value = value // library marker kkossev.commonLib, line 1490
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1491
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1492
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1493
        map.isStateChange = true // library marker kkossev.commonLib, line 1494
    } // library marker kkossev.commonLib, line 1495
    else { // library marker kkossev.commonLib, line 1496
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1497
    } // library marker kkossev.commonLib, line 1498
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1499
    sendEvent(map) // library marker kkossev.commonLib, line 1500
    clearIsDigital() // library marker kkossev.commonLib, line 1501
} // library marker kkossev.commonLib, line 1502

/** // library marker kkossev.commonLib, line 1504
 * Get the level transition rate // library marker kkossev.commonLib, line 1505
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1506
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1507
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1508
 */ // library marker kkossev.commonLib, line 1509
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1510
    int rate = 0 // library marker kkossev.commonLib, line 1511
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1512
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1513
    if (!isOn) { // library marker kkossev.commonLib, line 1514
        currentLevel = 0 // library marker kkossev.commonLib, line 1515
    } // library marker kkossev.commonLib, line 1516
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1517
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1518
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1519
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1520
    } else { // library marker kkossev.commonLib, line 1521
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1522
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1523
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1524
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1525
        } // library marker kkossev.commonLib, line 1526
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1527
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1528
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1529
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1530
        } // library marker kkossev.commonLib, line 1531
    } // library marker kkossev.commonLib, line 1532
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1533
    return rate // library marker kkossev.commonLib, line 1534
} // library marker kkossev.commonLib, line 1535

// Command option that enable changes when off // library marker kkossev.commonLib, line 1537
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1538

/** // library marker kkossev.commonLib, line 1540
 * Constrain a value to a range // library marker kkossev.commonLib, line 1541
 * @param value value to constrain // library marker kkossev.commonLib, line 1542
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1543
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1544
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1545
 */ // library marker kkossev.commonLib, line 1546
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1547
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1548
        return value // library marker kkossev.commonLib, line 1549
    } // library marker kkossev.commonLib, line 1550
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1551
} // library marker kkossev.commonLib, line 1552

/** // library marker kkossev.commonLib, line 1554
 * Constrain a value to a range // library marker kkossev.commonLib, line 1555
 * @param value value to constrain // library marker kkossev.commonLib, line 1556
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1557
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1558
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1559
 */ // library marker kkossev.commonLib, line 1560
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1561
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1562
        return value as Integer // library marker kkossev.commonLib, line 1563
    } // library marker kkossev.commonLib, line 1564
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1565
} // library marker kkossev.commonLib, line 1566

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1568
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1569

/** // library marker kkossev.commonLib, line 1571
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1572
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1573
 * @param commands commands to execute // library marker kkossev.commonLib, line 1574
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1575
 */ // library marker kkossev.commonLib, line 1576
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1577
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1578
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1579
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1580
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1581
    } // library marker kkossev.commonLib, line 1582
    return [] // library marker kkossev.commonLib, line 1583
} // library marker kkossev.commonLib, line 1584

def intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1586
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1587
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1588
} // library marker kkossev.commonLib, line 1589

def intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1591
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1592
} // library marker kkossev.commonLib, line 1593

/** // library marker kkossev.commonLib, line 1595
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1596
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1597
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1598
 */ // library marker kkossev.commonLib, line 1599
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.commonLib, line 1600
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1601
    List<String> cmds = [] // library marker kkossev.commonLib, line 1602
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1603
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1604
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1605
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1606
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1607
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1608
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1609
    } // library marker kkossev.commonLib, line 1610
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1611
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1612
    /* // library marker kkossev.commonLib, line 1613
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1614
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1615
    */ // library marker kkossev.commonLib, line 1616
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1617
    String endpointId = '01'     // TODO !!! // library marker kkossev.commonLib, line 1618
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1619

    return cmds // library marker kkossev.commonLib, line 1621
} // library marker kkossev.commonLib, line 1622

/** // library marker kkossev.commonLib, line 1624
 * Set Level Command // library marker kkossev.commonLib, line 1625
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1626
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1627
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1628
 */ // library marker kkossev.commonLib, line 1629
void setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1630
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1631
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.commonLib, line 1632
        customSetLevel(value, transitionTime) // library marker kkossev.commonLib, line 1633
        return // library marker kkossev.commonLib, line 1634
    } // library marker kkossev.commonLib, line 1635
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1636
    final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1637
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1638
    sendZigbeeCommands(setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1639
} // library marker kkossev.commonLib, line 1640

/* // library marker kkossev.commonLib, line 1642
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1643
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1644
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1645
*/ // library marker kkossev.commonLib, line 1646
void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1647
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1648
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1649
    } // library marker kkossev.commonLib, line 1650
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1651
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1652
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1653
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1654
    } // library marker kkossev.commonLib, line 1655
    else { // library marker kkossev.commonLib, line 1656
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1657
    } // library marker kkossev.commonLib, line 1658
} // library marker kkossev.commonLib, line 1659

/* // library marker kkossev.commonLib, line 1661
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1662
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1663
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1664
*/ // library marker kkossev.commonLib, line 1665
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1666
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1667
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1668
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.commonLib, line 1669
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1670
} // library marker kkossev.commonLib, line 1671

void handleIlluminanceEvent(int illuminance, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1673
    Map eventMap = [:] // library marker kkossev.commonLib, line 1674
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1675
    eventMap.name = 'illuminance' // library marker kkossev.commonLib, line 1676
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1677
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1678
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1679
    eventMap.unit = 'lx' // library marker kkossev.commonLib, line 1680
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1681
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1682
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1683
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1684
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.commonLib, line 1685
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.commonLib, line 1686
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1687
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1688
        return // library marker kkossev.commonLib, line 1689
    } // library marker kkossev.commonLib, line 1690
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1691
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1692
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1693
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1694
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1695
    } // library marker kkossev.commonLib, line 1696
    else {         // queue the event // library marker kkossev.commonLib, line 1697
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1698
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1699
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1700
    } // library marker kkossev.commonLib, line 1701
} // library marker kkossev.commonLib, line 1702

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1704
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1705
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1706
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1707
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1708
} // library marker kkossev.commonLib, line 1709

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1711

/* // library marker kkossev.commonLib, line 1713
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1714
 * temperature // library marker kkossev.commonLib, line 1715
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1716
*/ // library marker kkossev.commonLib, line 1717
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1718
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1719
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1720
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1721
} // library marker kkossev.commonLib, line 1722

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1724
    Map eventMap = [:] // library marker kkossev.commonLib, line 1725
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1726
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1727
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1728
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1729
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1730
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1731
    } // library marker kkossev.commonLib, line 1732
    else { // library marker kkossev.commonLib, line 1733
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1734
    } // library marker kkossev.commonLib, line 1735
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1736
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1737
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1738
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.commonLib, line 1739
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.commonLib, line 1740
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1741
        return // library marker kkossev.commonLib, line 1742
    } // library marker kkossev.commonLib, line 1743
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1744
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1745
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1746
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1747
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1748
    } // library marker kkossev.commonLib, line 1749
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1750
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1751
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1752
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1753
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1754
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1755
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1756
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1757
    } // library marker kkossev.commonLib, line 1758
    else {         // queue the event // library marker kkossev.commonLib, line 1759
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1760
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1761
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1762
    } // library marker kkossev.commonLib, line 1763
} // library marker kkossev.commonLib, line 1764

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1766
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1767
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1768
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1769
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1770
} // library marker kkossev.commonLib, line 1771

/* // library marker kkossev.commonLib, line 1773
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1774
 * humidity // library marker kkossev.commonLib, line 1775
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1776
*/ // library marker kkossev.commonLib, line 1777
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1778
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1779
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1780
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1781
} // library marker kkossev.commonLib, line 1782

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1784
    Map eventMap = [:] // library marker kkossev.commonLib, line 1785
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.commonLib, line 1786
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1787
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1788
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.commonLib, line 1789
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.commonLib, line 1790
        return // library marker kkossev.commonLib, line 1791
    } // library marker kkossev.commonLib, line 1792
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1793
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1794
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1795
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1796
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1797
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1798
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1799
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1800
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1801
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1802
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1803
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1804
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1805
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1806
    } // library marker kkossev.commonLib, line 1807
    else { // library marker kkossev.commonLib, line 1808
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1809
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1810
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1811
    } // library marker kkossev.commonLib, line 1812
} // library marker kkossev.commonLib, line 1813

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1815
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1816
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1817
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1818
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1819
} // library marker kkossev.commonLib, line 1820

/* // library marker kkossev.commonLib, line 1822
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1823
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1824
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1825
*/ // library marker kkossev.commonLib, line 1826

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1828
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { // library marker kkossev.commonLib, line 1829
        logWarn 'parseElectricalMeasureCluster is NOT implemented1' // library marker kkossev.commonLib, line 1830
    } // library marker kkossev.commonLib, line 1831
} // library marker kkossev.commonLib, line 1832

/* // library marker kkossev.commonLib, line 1834
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1835
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1836
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1837
*/ // library marker kkossev.commonLib, line 1838
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1839
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { // library marker kkossev.commonLib, line 1840
        logWarn 'parseMeteringCluster is NOT implemented1' // library marker kkossev.commonLib, line 1841
    } // library marker kkossev.commonLib, line 1842
} // library marker kkossev.commonLib, line 1843

/* // library marker kkossev.commonLib, line 1845
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1846
 * pm2.5 // library marker kkossev.commonLib, line 1847
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1848
*/ // library marker kkossev.commonLib, line 1849
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1850
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1851
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1852
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1853
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1854
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1855
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1856
    } // library marker kkossev.commonLib, line 1857
    else { // library marker kkossev.commonLib, line 1858
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1859
    } // library marker kkossev.commonLib, line 1860
} // library marker kkossev.commonLib, line 1861

/* // library marker kkossev.commonLib, line 1863
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1864
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1865
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1866
*/ // library marker kkossev.commonLib, line 1867
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1868
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1869
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1870
    } // library marker kkossev.commonLib, line 1871
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1872
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1873
    } // library marker kkossev.commonLib, line 1874
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1875
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1876
    } // library marker kkossev.commonLib, line 1877
    else { // library marker kkossev.commonLib, line 1878
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1879
    } // library marker kkossev.commonLib, line 1880
} // library marker kkossev.commonLib, line 1881

/* // library marker kkossev.commonLib, line 1883
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1884
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1885
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1886
*/ // library marker kkossev.commonLib, line 1887
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1888
    if (this.respondsTo('customParseMultistateInputCluster')) { // library marker kkossev.commonLib, line 1889
        customParseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 1890
    } // library marker kkossev.commonLib, line 1891
    else { // library marker kkossev.commonLib, line 1892
        logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1893
    } // library marker kkossev.commonLib, line 1894
} // library marker kkossev.commonLib, line 1895

/* // library marker kkossev.commonLib, line 1897
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1898
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1899
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1900
*/ // library marker kkossev.commonLib, line 1901
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1902
    if (this.respondsTo('customParseWindowCoveringCluster')) { // library marker kkossev.commonLib, line 1903
        customParseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 1904
    } // library marker kkossev.commonLib, line 1905
    else { // library marker kkossev.commonLib, line 1906
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1907
    } // library marker kkossev.commonLib, line 1908
} // library marker kkossev.commonLib, line 1909

/* // library marker kkossev.commonLib, line 1911
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1912
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1913
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1914
*/ // library marker kkossev.commonLib, line 1915
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1916
    if (this.respondsTo('customParseThermostatCluster')) { // library marker kkossev.commonLib, line 1917
        customParseThermostatCluster(descMap) // library marker kkossev.commonLib, line 1918
    } // library marker kkossev.commonLib, line 1919
    else { // library marker kkossev.commonLib, line 1920
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1921
    } // library marker kkossev.commonLib, line 1922
} // library marker kkossev.commonLib, line 1923

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1925

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1927
    if (this.respondsTo('customParseFC11Cluster')) { // library marker kkossev.commonLib, line 1928
        customParseFC11Cluster(descMap) // library marker kkossev.commonLib, line 1929
    } // library marker kkossev.commonLib, line 1930
    else { // library marker kkossev.commonLib, line 1931
        logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1932
    } // library marker kkossev.commonLib, line 1933
} // library marker kkossev.commonLib, line 1934

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1936
    if (this.respondsTo('customParseE002Cluster')) { // library marker kkossev.commonLib, line 1937
        customParseE002Cluster(descMap) // library marker kkossev.commonLib, line 1938
    } // library marker kkossev.commonLib, line 1939
    else { // library marker kkossev.commonLib, line 1940
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 1941
    } // library marker kkossev.commonLib, line 1942
} // library marker kkossev.commonLib, line 1943

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1945
    if (this.respondsTo('customParseEC03Cluster')) { // library marker kkossev.commonLib, line 1946
        customParseEC03Cluster(descMap) // library marker kkossev.commonLib, line 1947
    } // library marker kkossev.commonLib, line 1948
    else { // library marker kkossev.commonLib, line 1949
        logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 1950
    } // library marker kkossev.commonLib, line 1951
} // library marker kkossev.commonLib, line 1952

/* // library marker kkossev.commonLib, line 1954
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1955
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1956
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1957
*/ // library marker kkossev.commonLib, line 1958
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1959
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1960
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1961

// Tuya Commands // library marker kkossev.commonLib, line 1963
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1964
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1965
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1966
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1967
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1968

// tuya DP type // library marker kkossev.commonLib, line 1970
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1971
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1972
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1973
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1974
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1975
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1976

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1978
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1979
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 1980
        Long offset = 0 // library marker kkossev.commonLib, line 1981
        try { // library marker kkossev.commonLib, line 1982
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1983
        } // library marker kkossev.commonLib, line 1984
        catch (e) { // library marker kkossev.commonLib, line 1985
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 1986
        } // library marker kkossev.commonLib, line 1987
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1988
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 1989
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 1990
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 1991
    } // library marker kkossev.commonLib, line 1992
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1993
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1994
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1995
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1996
        if (status != '00') { // library marker kkossev.commonLib, line 1997
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1998
        } // library marker kkossev.commonLib, line 1999
    } // library marker kkossev.commonLib, line 2000
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 2001
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2002
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2003
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2004
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2005
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2006
            return // library marker kkossev.commonLib, line 2007
        } // library marker kkossev.commonLib, line 2008
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 2009
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2010
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2011
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 2012
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2013
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2014
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2015
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 2016
        } // library marker kkossev.commonLib, line 2017
    } // library marker kkossev.commonLib, line 2018
    else { // library marker kkossev.commonLib, line 2019
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2020
    } // library marker kkossev.commonLib, line 2021
} // library marker kkossev.commonLib, line 2022

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 2024
    log.trace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 2025
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 2026
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 2027
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 2028
            return // library marker kkossev.commonLib, line 2029
        } // library marker kkossev.commonLib, line 2030
    } // library marker kkossev.commonLib, line 2031
    // check if the method  method exists // library marker kkossev.commonLib, line 2032
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2033
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 2034
            return // library marker kkossev.commonLib, line 2035
        } // library marker kkossev.commonLib, line 2036
    } // library marker kkossev.commonLib, line 2037
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2038
} // library marker kkossev.commonLib, line 2039

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 2041
    int retValue = 0 // library marker kkossev.commonLib, line 2042
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2043
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 2044
        int power = 1 // library marker kkossev.commonLib, line 2045
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2046
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 2047
            power = power * 256 // library marker kkossev.commonLib, line 2048
        } // library marker kkossev.commonLib, line 2049
    } // library marker kkossev.commonLib, line 2050
    return retValue // library marker kkossev.commonLib, line 2051
} // library marker kkossev.commonLib, line 2052

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 2054
    List<String> cmds = [] // library marker kkossev.commonLib, line 2055
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2056
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2057
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2058
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 2059
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2060
    return cmds // library marker kkossev.commonLib, line 2061
} // library marker kkossev.commonLib, line 2062

private getPACKET_ID() { // library marker kkossev.commonLib, line 2064
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 2065
} // library marker kkossev.commonLib, line 2066

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2068
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 2069
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2070
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2071
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 2072
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2073
} // library marker kkossev.commonLib, line 2074

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2076
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2077

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 2079
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2080
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2081
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 2082
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 2083
} // library marker kkossev.commonLib, line 2084

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2086
    List<String> cmds = [] // library marker kkossev.commonLib, line 2087
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2088
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 2089
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2090
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2091
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2092
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2093
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 2094
        } // library marker kkossev.commonLib, line 2095
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2096
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 2097
    } // library marker kkossev.commonLib, line 2098
    else { // library marker kkossev.commonLib, line 2099
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 2100
    } // library marker kkossev.commonLib, line 2101
} // library marker kkossev.commonLib, line 2102

/** // library marker kkossev.commonLib, line 2104
 * initializes the device // library marker kkossev.commonLib, line 2105
 * Invoked from configure() // library marker kkossev.commonLib, line 2106
 * @return zigbee commands // library marker kkossev.commonLib, line 2107
 */ // library marker kkossev.commonLib, line 2108
List<String> initializeDevice() { // library marker kkossev.commonLib, line 2109
    List<String> cmds = [] // library marker kkossev.commonLib, line 2110
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2111

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2113
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 2114
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 2115
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 2116
    } // library marker kkossev.commonLib, line 2117
    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2118
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2119
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2120
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2121
    } // library marker kkossev.commonLib, line 2122
    // // library marker kkossev.commonLib, line 2123
    return cmds // library marker kkossev.commonLib, line 2124
} // library marker kkossev.commonLib, line 2125

/** // library marker kkossev.commonLib, line 2127
 * configures the device // library marker kkossev.commonLib, line 2128
 * Invoked from configure() // library marker kkossev.commonLib, line 2129
 * @return zigbee commands // library marker kkossev.commonLib, line 2130
 */ // library marker kkossev.commonLib, line 2131
List<String> configureDevice() { // library marker kkossev.commonLib, line 2132
    List<String> cmds = [] // library marker kkossev.commonLib, line 2133
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2134

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 2136
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 2137
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 2138
    } // library marker kkossev.commonLib, line 2139
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2140
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 2141
    return cmds // library marker kkossev.commonLib, line 2142
} // library marker kkossev.commonLib, line 2143

/* // library marker kkossev.commonLib, line 2145
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2146
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2147
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2148
*/ // library marker kkossev.commonLib, line 2149

void refresh() { // library marker kkossev.commonLib, line 2151
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2152
    checkDriverVersion() // library marker kkossev.commonLib, line 2153
    List<String> cmds = [] // library marker kkossev.commonLib, line 2154
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2155

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2157
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 2158
        cmds += customRefresh() // library marker kkossev.commonLib, line 2159
    } // library marker kkossev.commonLib, line 2160
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2161
    else { // library marker kkossev.commonLib, line 2162
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 2163
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 2164
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 2165
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 2166
        } // library marker kkossev.commonLib, line 2167
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2168
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2169
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2170
        } // library marker kkossev.commonLib, line 2171
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2172
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2173
        } // library marker kkossev.commonLib, line 2174
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2175
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2176
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2177
        } // library marker kkossev.commonLib, line 2178
    } // library marker kkossev.commonLib, line 2179

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2181
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2182
    } // library marker kkossev.commonLib, line 2183
    else { // library marker kkossev.commonLib, line 2184
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2185
    } // library marker kkossev.commonLib, line 2186
} // library marker kkossev.commonLib, line 2187

/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2189
void setRefreshRequest()   { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 2190
/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2191
void clearRefreshRequest() { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 2192

void clearInfoEvent() { // library marker kkossev.commonLib, line 2194
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2195
} // library marker kkossev.commonLib, line 2196

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2198
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 2199
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 2200
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 2201
    } // library marker kkossev.commonLib, line 2202
    else { // library marker kkossev.commonLib, line 2203
        logInfo "${info}" // library marker kkossev.commonLib, line 2204
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 2205
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2206
    } // library marker kkossev.commonLib, line 2207
} // library marker kkossev.commonLib, line 2208

void ping() { // library marker kkossev.commonLib, line 2210
    if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2211
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2212
        logInfo 'ping() command is not available for this sleepy device.' // library marker kkossev.commonLib, line 2213
        sendRttEvent('n/a') // library marker kkossev.commonLib, line 2214
    } // library marker kkossev.commonLib, line 2215
    else { // library marker kkossev.commonLib, line 2216
        if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2217
        state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 2218
        //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 2219
        state.states['isPing'] = true // library marker kkossev.commonLib, line 2220
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2221
        if (isVirtual()) { // library marker kkossev.commonLib, line 2222
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2223
        } // library marker kkossev.commonLib, line 2224
        else { // library marker kkossev.commonLib, line 2225
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2226
        } // library marker kkossev.commonLib, line 2227
        logDebug 'ping...' // library marker kkossev.commonLib, line 2228
    } // library marker kkossev.commonLib, line 2229
} // library marker kkossev.commonLib, line 2230

def virtualPong() { // library marker kkossev.commonLib, line 2232
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2233
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2234
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 2235
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 2236
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 2237
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 2238
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 2239
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 2240
        sendRttEvent() // library marker kkossev.commonLib, line 2241
    } // library marker kkossev.commonLib, line 2242
    else { // library marker kkossev.commonLib, line 2243
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 2244
    } // library marker kkossev.commonLib, line 2245
    state.states['isPing'] = false // library marker kkossev.commonLib, line 2246
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 2247
} // library marker kkossev.commonLib, line 2248

/** // library marker kkossev.commonLib, line 2250
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2251
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2252
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2253
 * @return none // library marker kkossev.commonLib, line 2254
 */ // library marker kkossev.commonLib, line 2255
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2256
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2257
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2258
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 2259
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 2260
    if (value == null) { // library marker kkossev.commonLib, line 2261
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2262
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 2263
    } // library marker kkossev.commonLib, line 2264
    else { // library marker kkossev.commonLib, line 2265
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2266
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2267
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 2268
    } // library marker kkossev.commonLib, line 2269
} // library marker kkossev.commonLib, line 2270

/** // library marker kkossev.commonLib, line 2272
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2273
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2274
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2275
 */ // library marker kkossev.commonLib, line 2276
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2277
    if (cluster != null) { // library marker kkossev.commonLib, line 2278
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2279
    } // library marker kkossev.commonLib, line 2280
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 2281
    return 'NULL' // library marker kkossev.commonLib, line 2282
} // library marker kkossev.commonLib, line 2283

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2285
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2286
} // library marker kkossev.commonLib, line 2287

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2289
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2290
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 2291
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2292
} // library marker kkossev.commonLib, line 2293

/** // library marker kkossev.commonLib, line 2295
 * Schedule a device health check // library marker kkossev.commonLib, line 2296
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2297
 */ // library marker kkossev.commonLib, line 2298
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2299
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2300
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 2301
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2302
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2303
    } // library marker kkossev.commonLib, line 2304
    else { // library marker kkossev.commonLib, line 2305
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 2306
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2307
    } // library marker kkossev.commonLib, line 2308
} // library marker kkossev.commonLib, line 2309

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2311
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2312
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2313
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 2314
} // library marker kkossev.commonLib, line 2315

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2317
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2318
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2319
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2320
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 2321
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2322
        logInfo 'is now online!' // library marker kkossev.commonLib, line 2323
    } // library marker kkossev.commonLib, line 2324
} // library marker kkossev.commonLib, line 2325

void deviceHealthCheck() { // library marker kkossev.commonLib, line 2327
    checkDriverVersion() // library marker kkossev.commonLib, line 2328
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2329
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2330
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2331
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2332
            logWarn 'not present!' // library marker kkossev.commonLib, line 2333
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2334
        } // library marker kkossev.commonLib, line 2335
    } // library marker kkossev.commonLib, line 2336
    else { // library marker kkossev.commonLib, line 2337
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2338
    } // library marker kkossev.commonLib, line 2339
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2340
} // library marker kkossev.commonLib, line 2341

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 2343
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2344
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2345
    if (value == 'online') { // library marker kkossev.commonLib, line 2346
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2347
    } // library marker kkossev.commonLib, line 2348
    else { // library marker kkossev.commonLib, line 2349
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2350
    } // library marker kkossev.commonLib, line 2351
} // library marker kkossev.commonLib, line 2352

/** // library marker kkossev.commonLib, line 2354
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2355
 */ // library marker kkossev.commonLib, line 2356
void autoPoll() { // library marker kkossev.commonLib, line 2357
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 2358
    checkDriverVersion() // library marker kkossev.commonLib, line 2359
    List<String> cmds = [] // library marker kkossev.commonLib, line 2360
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 2361
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2362
    } // library marker kkossev.commonLib, line 2363

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2365
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2366
    } // library marker kkossev.commonLib, line 2367
} // library marker kkossev.commonLib, line 2368

/** // library marker kkossev.commonLib, line 2370
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2371
 */ // library marker kkossev.commonLib, line 2372
void updated() { // library marker kkossev.commonLib, line 2373
    logInfo 'updated()...' // library marker kkossev.commonLib, line 2374
    checkDriverVersion() // library marker kkossev.commonLib, line 2375
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2376
    unschedule() // library marker kkossev.commonLib, line 2377

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2379
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2380
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2381
    } // library marker kkossev.commonLib, line 2382
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2383
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2384
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2385
    } // library marker kkossev.commonLib, line 2386

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2388
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2389
        // schedule the periodic timer // library marker kkossev.commonLib, line 2390
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2391
        if (interval > 0) { // library marker kkossev.commonLib, line 2392
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2393
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2394
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2395
        } // library marker kkossev.commonLib, line 2396
    } // library marker kkossev.commonLib, line 2397
    else { // library marker kkossev.commonLib, line 2398
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2399
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 2400
    } // library marker kkossev.commonLib, line 2401
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 2402
        customUpdated() // library marker kkossev.commonLib, line 2403
    } // library marker kkossev.commonLib, line 2404

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 2406
} // library marker kkossev.commonLib, line 2407

/** // library marker kkossev.commonLib, line 2409
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2410
 */ // library marker kkossev.commonLib, line 2411
void logsOff() { // library marker kkossev.commonLib, line 2412
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 2413
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2414
} // library marker kkossev.commonLib, line 2415
void traceOff() { // library marker kkossev.commonLib, line 2416
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 2417
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2418
} // library marker kkossev.commonLib, line 2419

void configure(String command) { // library marker kkossev.commonLib, line 2421
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2422
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2423
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2424
        return // library marker kkossev.commonLib, line 2425
    } // library marker kkossev.commonLib, line 2426
    // // library marker kkossev.commonLib, line 2427
    String func // library marker kkossev.commonLib, line 2428
    try { // library marker kkossev.commonLib, line 2429
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2430
        "$func"() // library marker kkossev.commonLib, line 2431
    } // library marker kkossev.commonLib, line 2432
    catch (e) { // library marker kkossev.commonLib, line 2433
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2434
        return // library marker kkossev.commonLib, line 2435
    } // library marker kkossev.commonLib, line 2436
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2437
} // library marker kkossev.commonLib, line 2438

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 2440
void configureHelp(final String val) { // library marker kkossev.commonLib, line 2441
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2442
} // library marker kkossev.commonLib, line 2443

void loadAllDefaults() { // library marker kkossev.commonLib, line 2445
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 2446
    deleteAllSettings() // library marker kkossev.commonLib, line 2447
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2448
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2449
    deleteAllStates() // library marker kkossev.commonLib, line 2450
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2451
    initialize() // library marker kkossev.commonLib, line 2452
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 2453
    updated() // library marker kkossev.commonLib, line 2454
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 2455
} // library marker kkossev.commonLib, line 2456

void configureNow() { // library marker kkossev.commonLib, line 2458
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 2459
} // library marker kkossev.commonLib, line 2460

/** // library marker kkossev.commonLib, line 2462
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2463
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 2464
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2465
 */ // library marker kkossev.commonLib, line 2466
List<String> configure() { // library marker kkossev.commonLib, line 2467
    List<String> cmds = [] // library marker kkossev.commonLib, line 2468
    logInfo 'configure...' // library marker kkossev.commonLib, line 2469
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 2470
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2471
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2472
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 2473
    } // library marker kkossev.commonLib, line 2474
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2475
    cmds += configureDevice() // library marker kkossev.commonLib, line 2476
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2477
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 2478
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 2479
    //return cmds // library marker kkossev.commonLib, line 2480
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2481
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2482
    } // library marker kkossev.commonLib, line 2483
    else { // library marker kkossev.commonLib, line 2484
        logDebug "no configure() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2485
    } // library marker kkossev.commonLib, line 2486
} // library marker kkossev.commonLib, line 2487

/** // library marker kkossev.commonLib, line 2489
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2490
 */ // library marker kkossev.commonLib, line 2491
void installed() { // library marker kkossev.commonLib, line 2492
    logInfo 'installed...' // library marker kkossev.commonLib, line 2493
    // populate some default values for attributes // library marker kkossev.commonLib, line 2494
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2495
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2496
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 2497
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2498
} // library marker kkossev.commonLib, line 2499

/** // library marker kkossev.commonLib, line 2501
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 2502
 */ // library marker kkossev.commonLib, line 2503
void initialize() { // library marker kkossev.commonLib, line 2504
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2505
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2506
    updateTuyaVersion() // library marker kkossev.commonLib, line 2507
    updateAqaraVersion() // library marker kkossev.commonLib, line 2508
} // library marker kkossev.commonLib, line 2509

/* // library marker kkossev.commonLib, line 2511
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2512
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2513
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2514
*/ // library marker kkossev.commonLib, line 2515

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2517
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2518
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2519
} // library marker kkossev.commonLib, line 2520

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 2522
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2523
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2524
} // library marker kkossev.commonLib, line 2525

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2527
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 2528
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 2529
} // library marker kkossev.commonLib, line 2530

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 2532
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 2533
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 2534
        return // library marker kkossev.commonLib, line 2535
    } // library marker kkossev.commonLib, line 2536
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2537
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2538
    cmd.each { // library marker kkossev.commonLib, line 2539
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2540
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 2541
    } // library marker kkossev.commonLib, line 2542
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2543
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2544
} // library marker kkossev.commonLib, line 2545

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 2547

String getDeviceInfo() { // library marker kkossev.commonLib, line 2549
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2550
} // library marker kkossev.commonLib, line 2551

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2553
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 2554
} // library marker kkossev.commonLib, line 2555

void checkDriverVersion() { // library marker kkossev.commonLib, line 2557
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2558
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2559
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2560
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2561
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2562
        updateTuyaVersion() // library marker kkossev.commonLib, line 2563
        updateAqaraVersion() // library marker kkossev.commonLib, line 2564
    } // library marker kkossev.commonLib, line 2565
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2566
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2567
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2568
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 2569
} // library marker kkossev.commonLib, line 2570

// credits @thebearmay // library marker kkossev.commonLib, line 2572
String getModel() { // library marker kkossev.commonLib, line 2573
    try { // library marker kkossev.commonLib, line 2574
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2575
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2576
    } catch (ignore) { // library marker kkossev.commonLib, line 2577
        try { // library marker kkossev.commonLib, line 2578
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2579
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2580
                return model // library marker kkossev.commonLib, line 2581
            } // library marker kkossev.commonLib, line 2582
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2583
            return '' // library marker kkossev.commonLib, line 2584
        } // library marker kkossev.commonLib, line 2585
    } // library marker kkossev.commonLib, line 2586
} // library marker kkossev.commonLib, line 2587

// credits @thebearmay // library marker kkossev.commonLib, line 2589
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2590
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2591
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2592
    String revision = tokens.last() // library marker kkossev.commonLib, line 2593
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2594
} // library marker kkossev.commonLib, line 2595

/** // library marker kkossev.commonLib, line 2597
 * called from TODO // library marker kkossev.commonLib, line 2598
 */ // library marker kkossev.commonLib, line 2599

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2601
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2602
    unschedule() // library marker kkossev.commonLib, line 2603
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2604
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2605

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2607
} // library marker kkossev.commonLib, line 2608

void resetStatistics() { // library marker kkossev.commonLib, line 2610
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2611
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2612
} // library marker kkossev.commonLib, line 2613

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2615
void resetStats() { // library marker kkossev.commonLib, line 2616
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2617
    state.stats = [:] // library marker kkossev.commonLib, line 2618
    state.states = [:] // library marker kkossev.commonLib, line 2619
    state.lastRx = [:] // library marker kkossev.commonLib, line 2620
    state.lastTx = [:] // library marker kkossev.commonLib, line 2621
    state.health = [:] // library marker kkossev.commonLib, line 2622
    state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2623
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2624
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2625
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2626
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2627
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2628
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2629
} // library marker kkossev.commonLib, line 2630

/** // library marker kkossev.commonLib, line 2632
 * called from TODO // library marker kkossev.commonLib, line 2633
 */ // library marker kkossev.commonLib, line 2634
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2635
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2636
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2637
        state.clear() // library marker kkossev.commonLib, line 2638
        unschedule() // library marker kkossev.commonLib, line 2639
        resetStats() // library marker kkossev.commonLib, line 2640
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2641
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2642
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2643
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2644
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2645
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2646
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2647
    } // library marker kkossev.commonLib, line 2648

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2650
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2651
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2652
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2653
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2654
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2655

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2657
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2658
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2659
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2660
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2661
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2662
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2663
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2664
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2665
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 2666

    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2668
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2669
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2670
    } // library marker kkossev.commonLib, line 2671
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2672
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.commonLib, line 2673
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.commonLib, line 2674
    } // library marker kkossev.commonLib, line 2675
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2676
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2677
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2678
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2679

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2681
    if ( mm != null) { // library marker kkossev.commonLib, line 2682
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2683
    } // library marker kkossev.commonLib, line 2684
    else { // library marker kkossev.commonLib, line 2685
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2686
    } // library marker kkossev.commonLib, line 2687
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2688
    if ( ep  != null) { // library marker kkossev.commonLib, line 2689
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2690
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2691
    } // library marker kkossev.commonLib, line 2692
    else { // library marker kkossev.commonLib, line 2693
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2694
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2695
    } // library marker kkossev.commonLib, line 2696
} // library marker kkossev.commonLib, line 2697

/** // library marker kkossev.commonLib, line 2699
 * called from TODO // library marker kkossev.commonLib, line 2700
 */ // library marker kkossev.commonLib, line 2701
void setDestinationEP() { // library marker kkossev.commonLib, line 2702
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2703
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2704
        state.destinationEP = ep // library marker kkossev.commonLib, line 2705
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2706
    } // library marker kkossev.commonLib, line 2707
    else { // library marker kkossev.commonLib, line 2708
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2709
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2710
    } // library marker kkossev.commonLib, line 2711
} // library marker kkossev.commonLib, line 2712

void  logDebug(final String msg) { // library marker kkossev.commonLib, line 2714
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2715
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2716
    } // library marker kkossev.commonLib, line 2717
} // library marker kkossev.commonLib, line 2718

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2720
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2721
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2722
    } // library marker kkossev.commonLib, line 2723
} // library marker kkossev.commonLib, line 2724

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2726
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2727
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2728
    } // library marker kkossev.commonLib, line 2729
} // library marker kkossev.commonLib, line 2730

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2732
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2733
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2734
    } // library marker kkossev.commonLib, line 2735
} // library marker kkossev.commonLib, line 2736

// _DEBUG mode only // library marker kkossev.commonLib, line 2738
void getAllProperties() { // library marker kkossev.commonLib, line 2739
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2740
    device.properties.each { it -> // library marker kkossev.commonLib, line 2741
        log.debug it // library marker kkossev.commonLib, line 2742
    } // library marker kkossev.commonLib, line 2743
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2744
    settings.each { it -> // library marker kkossev.commonLib, line 2745
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2746
    } // library marker kkossev.commonLib, line 2747
    log.trace 'Done' // library marker kkossev.commonLib, line 2748
} // library marker kkossev.commonLib, line 2749

// delete all Preferences // library marker kkossev.commonLib, line 2751
void deleteAllSettings() { // library marker kkossev.commonLib, line 2752
    settings.each { it -> // library marker kkossev.commonLib, line 2753
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2754
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2755
    } // library marker kkossev.commonLib, line 2756
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2757
} // library marker kkossev.commonLib, line 2758

// delete all attributes // library marker kkossev.commonLib, line 2760
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2761
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2762
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2763
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2764
    } // library marker kkossev.commonLib, line 2765
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2766
} // library marker kkossev.commonLib, line 2767

// delete all State Variables // library marker kkossev.commonLib, line 2769
void deleteAllStates() { // library marker kkossev.commonLib, line 2770
    state.each { it -> // library marker kkossev.commonLib, line 2771
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2772
    } // library marker kkossev.commonLib, line 2773
    state.clear() // library marker kkossev.commonLib, line 2774
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2775
} // library marker kkossev.commonLib, line 2776

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2778
    unschedule() // library marker kkossev.commonLib, line 2779
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2780
} // library marker kkossev.commonLib, line 2781

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2783
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2784
} // library marker kkossev.commonLib, line 2785

void parseTest(String par) { // library marker kkossev.commonLib, line 2787
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2788
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2789
    parse(par) // library marker kkossev.commonLib, line 2790
} // library marker kkossev.commonLib, line 2791

def testJob() { // library marker kkossev.commonLib, line 2793
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2794
} // library marker kkossev.commonLib, line 2795

/** // library marker kkossev.commonLib, line 2797
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2798
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2799
 */ // library marker kkossev.commonLib, line 2800
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2801
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2802
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2803
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2804
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2805
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2806
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2807
    String cron // library marker kkossev.commonLib, line 2808
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2809
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2810
    } // library marker kkossev.commonLib, line 2811
    else { // library marker kkossev.commonLib, line 2812
        if (minutes < 60) { // library marker kkossev.commonLib, line 2813
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2814
        } // library marker kkossev.commonLib, line 2815
        else { // library marker kkossev.commonLib, line 2816
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2817
        } // library marker kkossev.commonLib, line 2818
    } // library marker kkossev.commonLib, line 2819
    return cron // library marker kkossev.commonLib, line 2820
} // library marker kkossev.commonLib, line 2821

// credits @thebearmay // library marker kkossev.commonLib, line 2823
String formatUptime() { // library marker kkossev.commonLib, line 2824
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2825
} // library marker kkossev.commonLib, line 2826

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2828
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2829
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2830
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2831
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2832
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2833
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2834
} // library marker kkossev.commonLib, line 2835

boolean isTuya() { // library marker kkossev.commonLib, line 2837
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2838
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2839
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2840
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2841
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2842
} // library marker kkossev.commonLib, line 2843

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2845
    if (!isTuya()) { // library marker kkossev.commonLib, line 2846
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2847
        return // library marker kkossev.commonLib, line 2848
    } // library marker kkossev.commonLib, line 2849
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2850
    if (application != null) { // library marker kkossev.commonLib, line 2851
        Integer ver // library marker kkossev.commonLib, line 2852
        try { // library marker kkossev.commonLib, line 2853
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2854
        } // library marker kkossev.commonLib, line 2855
        catch (e) { // library marker kkossev.commonLib, line 2856
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2857
            return // library marker kkossev.commonLib, line 2858
        } // library marker kkossev.commonLib, line 2859
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2860
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2861
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2862
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2863
        } // library marker kkossev.commonLib, line 2864
    } // library marker kkossev.commonLib, line 2865
} // library marker kkossev.commonLib, line 2866

boolean isAqara() { // library marker kkossev.commonLib, line 2868
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2869
} // library marker kkossev.commonLib, line 2870

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2872
    if (!isAqara()) { // library marker kkossev.commonLib, line 2873
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2874
        return // library marker kkossev.commonLib, line 2875
    } // library marker kkossev.commonLib, line 2876
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2877
    if (application != null) { // library marker kkossev.commonLib, line 2878
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2879
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2880
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2881
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2882
        } // library marker kkossev.commonLib, line 2883
    } // library marker kkossev.commonLib, line 2884
} // library marker kkossev.commonLib, line 2885

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2887
    try { // library marker kkossev.commonLib, line 2888
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2889
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2890
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2891
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2892
    } catch (e) { // library marker kkossev.commonLib, line 2893
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2894
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2895
    } // library marker kkossev.commonLib, line 2896
} // library marker kkossev.commonLib, line 2897

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2899
    try { // library marker kkossev.commonLib, line 2900
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2901
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2902
        return date.getTime() // library marker kkossev.commonLib, line 2903
    } catch (e) { // library marker kkossev.commonLib, line 2904
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2905
        return now() // library marker kkossev.commonLib, line 2906
    } // library marker kkossev.commonLib, line 2907
} // library marker kkossev.commonLib, line 2908

void test(String par) { // library marker kkossev.commonLib, line 2910
    List<String> cmds = [] // library marker kkossev.commonLib, line 2911
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2912

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2914
    //parse(par) // library marker kkossev.commonLib, line 2915

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2917
} // library marker kkossev.commonLib, line 2918

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter */ // library marker kkossev.xiaomiLib, line 1
library( // library marker kkossev.xiaomiLib, line 2
    base: 'driver', // library marker kkossev.xiaomiLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.xiaomiLib, line 4
    category: 'zigbee', // library marker kkossev.xiaomiLib, line 5
    description: 'Xiaomi Library', // library marker kkossev.xiaomiLib, line 6
    name: 'xiaomiLib', // library marker kkossev.xiaomiLib, line 7
    namespace: 'kkossev', // library marker kkossev.xiaomiLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', // library marker kkossev.xiaomiLib, line 9
    version: '1.0.2', // library marker kkossev.xiaomiLib, line 10
    documentationLink: '' // library marker kkossev.xiaomiLib, line 11
) // library marker kkossev.xiaomiLib, line 12
/* // library marker kkossev.xiaomiLib, line 13
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 14
 * // library marker kkossev.xiaomiLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 17
 * // library marker kkossev.xiaomiLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 19
 * // library marker kkossev.xiaomiLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 23
 * // library marker kkossev.xiaomiLib, line 24
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 25
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 26
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) Groovy linting; aqaraCube specific code; // library marker kkossev.xiaomiLib, line 27
 * // library marker kkossev.xiaomiLib, line 28
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 29
*/ // library marker kkossev.xiaomiLib, line 30

/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 32
static String xiaomiLibVersion()   { '1.0.2' } // library marker kkossev.xiaomiLib, line 33
/* groovylint-disable-next-line ImplicitReturnStatement */ // library marker kkossev.xiaomiLib, line 34
static String xiaomiLibStamp() { '2024/04/06 12:14 PM' } // library marker kkossev.xiaomiLib, line 35

boolean isAqaraTVOC()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 37
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.xiaomiLib, line 38

// no metadata for this library! // library marker kkossev.xiaomiLib, line 40

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 42

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 44
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 45
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 46
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 47
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 48
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 49
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 50
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 52
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 53
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 54
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 55
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 56
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 57
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 58
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 59

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 61
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 62
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 63
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 64
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 65
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 66
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 67

// called from parseXiaomiCluster() in the main code ... // library marker kkossev.xiaomiLib, line 69
// // library marker kkossev.xiaomiLib, line 70
void parseXiaomiClusterLib(final Map descMap) { // library marker kkossev.xiaomiLib, line 71
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 72
        logTrace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 73
    } // library marker kkossev.xiaomiLib, line 74
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 75
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 76
        return // library marker kkossev.xiaomiLib, line 77
    } // library marker kkossev.xiaomiLib, line 78
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 79
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 80
        return // library marker kkossev.xiaomiLib, line 81
    } // library marker kkossev.xiaomiLib, line 82
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 83
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 84
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 85
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 86
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 87
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 90
            log.info 'unknown attribute - resetting?' // library marker kkossev.xiaomiLib, line 91
            break // library marker kkossev.xiaomiLib, line 92
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 93
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 94
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 95
            break // library marker kkossev.xiaomiLib, line 96
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 97
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 98
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 99
            break // library marker kkossev.xiaomiLib, line 100
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 101
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 102
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 103
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 104
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 105
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 106
            } // library marker kkossev.xiaomiLib, line 107
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 108
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 109
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 110
            } // library marker kkossev.xiaomiLib, line 111
            break // library marker kkossev.xiaomiLib, line 112
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 113
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 114
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 115
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 116
            break // library marker kkossev.xiaomiLib, line 117
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 118
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 119
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 120
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 121
            break // library marker kkossev.xiaomiLib, line 122
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 123
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 124
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 125
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 126
            break // library marker kkossev.xiaomiLib, line 127
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 128
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 129
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 130
            break // library marker kkossev.xiaomiLib, line 131
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 132
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 133
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 134
            break // library marker kkossev.xiaomiLib, line 135
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 136
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 137
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 138
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 139
                sendZigbeeCommands(customRefresh()) // library marker kkossev.xiaomiLib, line 140
            } // library marker kkossev.xiaomiLib, line 141
            break // library marker kkossev.xiaomiLib, line 142
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1 // library marker kkossev.xiaomiLib, line 143
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 144
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 145
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 146
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 147
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 148
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 149
                } // library marker kkossev.xiaomiLib, line 150
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 151
            } // library marker kkossev.xiaomiLib, line 152
            break // library marker kkossev.xiaomiLib, line 153
        default: // library marker kkossev.xiaomiLib, line 154
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 155
            break // library marker kkossev.xiaomiLib, line 156
    } // library marker kkossev.xiaomiLib, line 157
} // library marker kkossev.xiaomiLib, line 158

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 160
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 161
        switch (tag) { // library marker kkossev.xiaomiLib, line 162
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 163
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 164
                break // library marker kkossev.xiaomiLib, line 165
            case 0x03: // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 167
                break // library marker kkossev.xiaomiLib, line 168
            case 0x05: // library marker kkossev.xiaomiLib, line 169
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 170
                break // library marker kkossev.xiaomiLib, line 171
            case 0x06: // library marker kkossev.xiaomiLib, line 172
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 173
                break // library marker kkossev.xiaomiLib, line 174
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 175
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 176
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 177
                device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 178
                break // library marker kkossev.xiaomiLib, line 179
            case 0x0a: // library marker kkossev.xiaomiLib, line 180
                String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 181
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 182
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 183
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 184
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 185
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 186
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 187
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 188
                } // library marker kkossev.xiaomiLib, line 189
                break // library marker kkossev.xiaomiLib, line 190
            case 0x0b: // library marker kkossev.xiaomiLib, line 191
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 192
                break // library marker kkossev.xiaomiLib, line 193
            case 0x64: // library marker kkossev.xiaomiLib, line 194
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 195
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 196
                break // library marker kkossev.xiaomiLib, line 197
            case 0x65: // library marker kkossev.xiaomiLib, line 198
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 199
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 200
                break // library marker kkossev.xiaomiLib, line 201
            case 0x66: // library marker kkossev.xiaomiLib, line 202
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 203
                else if (isAqaraTVOC()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 204
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 205
                break // library marker kkossev.xiaomiLib, line 206
            case 0x67: // library marker kkossev.xiaomiLib, line 207
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 208
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 209
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 210
                break // library marker kkossev.xiaomiLib, line 211
            case 0x69: // library marker kkossev.xiaomiLib, line 212
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 213
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 214
                break // library marker kkossev.xiaomiLib, line 215
            case 0x6a: // library marker kkossev.xiaomiLib, line 216
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 217
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 218
                break // library marker kkossev.xiaomiLib, line 219
            case 0x6b: // library marker kkossev.xiaomiLib, line 220
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 221
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 222
                break // library marker kkossev.xiaomiLib, line 223
            case 0x95: // library marker kkossev.xiaomiLib, line 224
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 225
                break // library marker kkossev.xiaomiLib, line 226
            case 0x96: // library marker kkossev.xiaomiLib, line 227
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 228
                break // library marker kkossev.xiaomiLib, line 229
            case 0x97: // library marker kkossev.xiaomiLib, line 230
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 231
                break // library marker kkossev.xiaomiLib, line 232
            case 0x98: // library marker kkossev.xiaomiLib, line 233
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 234
                break // library marker kkossev.xiaomiLib, line 235
            case 0x9b: // library marker kkossev.xiaomiLib, line 236
                if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 237
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 238
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 239
                } // library marker kkossev.xiaomiLib, line 240
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 241
                break // library marker kkossev.xiaomiLib, line 242
            default: // library marker kkossev.xiaomiLib, line 243
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 244
        } // library marker kkossev.xiaomiLib, line 245
    } // library marker kkossev.xiaomiLib, line 246
} // library marker kkossev.xiaomiLib, line 247

/** // library marker kkossev.xiaomiLib, line 249
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 250
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 251
 */ // library marker kkossev.xiaomiLib, line 252
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 253
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 254
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 255
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 256
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 257
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 258
    } // library marker kkossev.xiaomiLib, line 259
    return bigInt // library marker kkossev.xiaomiLib, line 260
} // library marker kkossev.xiaomiLib, line 261

/** // library marker kkossev.xiaomiLib, line 263
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 264
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 265
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 266
 */ // library marker kkossev.xiaomiLib, line 267
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 268
    final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 269
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 270
    new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 271
        while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 272
            int tag = stream.read() // library marker kkossev.xiaomiLib, line 273
            int dataType = stream.read() // library marker kkossev.xiaomiLib, line 274
            Object value // library marker kkossev.xiaomiLib, line 275
            if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 276
                int length = stream.read() // library marker kkossev.xiaomiLib, line 277
                byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 278
                stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 279
                value = new String(byteArr) // library marker kkossev.xiaomiLib, line 280
            } else { // library marker kkossev.xiaomiLib, line 281
                int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 282
                value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 283
            } // library marker kkossev.xiaomiLib, line 284
            results[tag] = value // library marker kkossev.xiaomiLib, line 285
        } // library marker kkossev.xiaomiLib, line 286
    } // library marker kkossev.xiaomiLib, line 287
    return results // library marker kkossev.xiaomiLib, line 288
} // library marker kkossev.xiaomiLib, line 289

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 291
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 292
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 293
    return cmds // library marker kkossev.xiaomiLib, line 294
} // library marker kkossev.xiaomiLib, line 295

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 297
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 298
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 299
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 300
    return cmds // library marker kkossev.xiaomiLib, line 301
} // library marker kkossev.xiaomiLib, line 302

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 304
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 305
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 306
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 307
    return cmds // library marker kkossev.xiaomiLib, line 308
} // library marker kkossev.xiaomiLib, line 309

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 311
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 312
} // library marker kkossev.xiaomiLib, line 313

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 315
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 316
} // library marker kkossev.xiaomiLib, line 317

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (167) kkossev.buttonLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.buttonLib, line 1
library( // library marker kkossev.buttonLib, line 2
    base: 'driver', // library marker kkossev.buttonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.buttonLib, line 4
    category: 'zigbee', // library marker kkossev.buttonLib, line 5
    description: 'Zigbee Button Library', // library marker kkossev.buttonLib, line 6
    name: 'buttonLib', // library marker kkossev.buttonLib, line 7
    namespace: 'kkossev', // library marker kkossev.buttonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/buttonLib.groovy', // library marker kkossev.buttonLib, line 9
    version: '3.0.0', // library marker kkossev.buttonLib, line 10
    documentationLink: '' // library marker kkossev.buttonLib, line 11
) // library marker kkossev.buttonLib, line 12
/* // library marker kkossev.buttonLib, line 13
 *  Zigbee Button Library // library marker kkossev.buttonLib, line 14
 * // library marker kkossev.buttonLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.buttonLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.buttonLib, line 17
 * // library marker kkossev.buttonLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.buttonLib, line 19
 * // library marker kkossev.buttonLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.buttonLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.buttonLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.buttonLib, line 23
 * // library marker kkossev.buttonLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.buttonLib, line 25
 * // library marker kkossev.buttonLib, line 26
 *                                   TODO: // library marker kkossev.buttonLib, line 27
*/ // library marker kkossev.buttonLib, line 28

static String buttonLibVersion()   { '3.0.0' } // library marker kkossev.buttonLib, line 30
static String buttonLibStamp() { '2024/04/06 1:02 PM' } // library marker kkossev.buttonLib, line 31

//import groovy.json.* // library marker kkossev.buttonLib, line 33
//import groovy.transform.Field // library marker kkossev.buttonLib, line 34
//import hubitat.zigbee.clusters.iaszone.ZoneStatus // library marker kkossev.buttonLib, line 35
//import hubitat.zigbee.zcl.DataType // library marker kkossev.buttonLib, line 36
//import java.util.concurrent.ConcurrentHashMap // library marker kkossev.buttonLib, line 37

//import groovy.transform.CompileStatic // library marker kkossev.buttonLib, line 39

metadata { // library marker kkossev.buttonLib, line 41
    // no capabilities // library marker kkossev.buttonLib, line 42
    // no attributes // library marker kkossev.buttonLib, line 43
    // no commands // library marker kkossev.buttonLib, line 44
    preferences { // library marker kkossev.buttonLib, line 45
        // no prefrences // library marker kkossev.buttonLib, line 46
    } // library marker kkossev.buttonLib, line 47
} // library marker kkossev.buttonLib, line 48

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.buttonLib, line 50
    if (buttonState != 'unknown' && buttonNumber != 0) { // library marker kkossev.buttonLib, line 51
        String descriptionText = "button $buttonNumber was $buttonState" // library marker kkossev.buttonLib, line 52
        if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.buttonLib, line 53
        Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.buttonLib, line 54
        logInfo "$descriptionText" // library marker kkossev.buttonLib, line 55
        sendEvent(event) // library marker kkossev.buttonLib, line 56
    } // library marker kkossev.buttonLib, line 57
    else { // library marker kkossev.buttonLib, line 58
        logWarn "sendButtonEvent: UNHANDLED event for button ${buttonNumber}, buttonState=${buttonState}" // library marker kkossev.buttonLib, line 59
    } // library marker kkossev.buttonLib, line 60
} // library marker kkossev.buttonLib, line 61

void push() {                // Momentary capability // library marker kkossev.buttonLib, line 63
    logDebug 'push momentary' // library marker kkossev.buttonLib, line 64
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.buttonLib, line 65
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.buttonLib, line 66
} // library marker kkossev.buttonLib, line 67

void push(BigDecimal buttonNumber) {    //pushableButton capability // library marker kkossev.buttonLib, line 69
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 70
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 71
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 72
} // library marker kkossev.buttonLib, line 73

void doubleTap(BigDecimal buttonNumber) { // library marker kkossev.buttonLib, line 75
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.buttonLib, line 76
} // library marker kkossev.buttonLib, line 77

void hold(BigDecimal buttonNumber) { // library marker kkossev.buttonLib, line 79
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.buttonLib, line 80
} // library marker kkossev.buttonLib, line 81

void release(BigDecimal buttonNumber) { // library marker kkossev.buttonLib, line 83
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true) // library marker kkossev.buttonLib, line 84
} // library marker kkossev.buttonLib, line 85

void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.buttonLib, line 87
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 88
} // library marker kkossev.buttonLib, line 89

void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.buttonLib, line 91
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 92
} // library marker kkossev.buttonLib, line 93


// ~~~~~ end include (167) kkossev.buttonLib ~~~~~
