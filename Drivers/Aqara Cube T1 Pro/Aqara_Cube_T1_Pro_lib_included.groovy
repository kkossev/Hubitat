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
 * ver. 3.3.0  2026-08-27 kkossev  - (dev. branch) commonLib 4.1.1
 *
 *                                   TODO: 
 */

static String version() { "3.3.0" }
static String timeStamp() {"2026/08/27 11:24 PM"}

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
                side = (device.currentValue('sideUp', true) ?: 0) as Integer
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
            device.updateSetting('cubeOperationMode', [value: value.toString(), type: 'enum'])
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
        def side = (device.currentValue('sideUp', true) ?: 0) as Integer
        sendButtonEvent(side, leftRight == "rotateLeft" ? "held" : "released", isDigital=true)
    }
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDouble, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Common ZCL Library', name: 'commonLib', namespace: 'kkossev', // library marker kkossev.commonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/commonLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-commonLib', // library marker kkossev.commonLib, line 4
    version: '4.1.1' // library marker kkossev.commonLib, line 5
) // library marker kkossev.commonLib, line 6
/* // library marker kkossev.commonLib, line 7
  *  Common ZCL Library // library marker kkossev.commonLib, line 8
  * // library marker kkossev.commonLib, line 9
  *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 10
  *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 11
  * // library marker kkossev.commonLib, line 12
  *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 13
  * // library marker kkossev.commonLib, line 14
  *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 15
  *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 16
  *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 17
  * // library marker kkossev.commonLib, line 18
  * This library is inspired by @w35l3y work on Tuya device driver (Edge project). // library marker kkossev.commonLib, line 19
  * For a big portions of code all credits go to Jonathan Bradshaw. // library marker kkossev.commonLib, line 20
  * // library marker kkossev.commonLib, line 21
  * // library marker kkossev.commonLib, line 22
  * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 23
  * .............................. // library marker kkossev.commonLib, line 24
  * ver. 3.5.2  2025-08-13 kkossev  - Status attribute renamed to _status_ // library marker kkossev.commonLib, line 25
  * ver. 4.0.0  2025-09-17 kkossev  - deviceProfileV4; HOBEIAN as Tuya device; customInitialize() hook; // library marker kkossev.commonLib, line 26
  * ver. 4.0.1  2025-10-14 kkossev  - added clusters 0xFC80 and 0xFC81 // library marker kkossev.commonLib, line 27
  * ver. 4.0.2  2025-10-18 kkossev  - added tuyaDelay in sendTuyaCommand() // library marker kkossev.commonLib, line 28
  * ver. 4.0.3  2025-10-18 kkossev  - added ignoreDuplicatedZigbeeMessages setting; DIGITAL_TIMER increased to 5000 ms // library marker kkossev.commonLib, line 29
  * ver. 4.0.4  2026-06-04 kkossev  - added ED00 cluster; // library marker kkossev.commonLib, line 30
  * ver. 4.0.5  2026-08-03 kkossev  - bug fixes // library marker kkossev.commonLib, line 31
  * ver. 4.1.0  2026-08-05 kkossev  - the administrative commands drop-down moved from configure(par) to the new deviceUtilities(par) command, so that configure() is again a plain Configuration capability button; removed the two separator entries from ConfigureOpts; configureHelp() is callable again and shows the command list and a '_status_' event when nothing was selected; do not use 'defaultValue' in a command parameter - it does not preselect the drop-down, but it IS submitted when Run is pressed without a selection!; configure() now shows a 'sleepy devices can not be configured' warning text; ping() icon changed to the antenna bars; added a one-click 'loadAllDefaults' command button // library marker kkossev.commonLib, line 32
  * ver. 4.1.1  2026-08-23 kkossev  - (dev. branch) bug fix: quoted the respondsTo('processTuyaDPfromDeviceProfile') argument in standardProcessTuyaDP(); the bare identifier threw a NullPointerException in drivers without deviceProfileLib; cosmetic: parse() and standardAndCustomParseCluster() log the cluster id from clusterId/clusterInt when descMap.cluster is null (catchall messages), instead of 'cluster:0xnull'; removed a stray '}' from the healthStatus warning text // library marker kkossev.commonLib, line 33
  * // library marker kkossev.commonLib, line 34
  *                                   TODO: change the offline threshold to 2  // library marker kkossev.commonLib, line 35
  *                                   TODO: add GetInfo (endpoints list) command (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 36
  *                                   TODO: make the configure() without parameter smart - analyze the State variables and call delete states.... call ActiveAndpoints() or/amd initialize() or/and configure() // library marker kkossev.commonLib, line 37
  *                                   TODO: check - offlineCtr is not increasing? (ZBMicro); // library marker kkossev.commonLib, line 38
  *                                   TODO: check deviceCommandTimeout() // library marker kkossev.commonLib, line 39
  *                                   TODO: when device rejoins the network, read the battery percentage again (probably in custom handler, not for all devices) // library marker kkossev.commonLib, line 40
  *                                   TODO: refresh() to include updating the softwareBuild data version // library marker kkossev.commonLib, line 41
  *                                   TODO: map the ZCL powerSource options to Hubitat powerSource options // library marker kkossev.commonLib, line 42
  *                                   TODO: MOVE ZDO counters to health state? // library marker kkossev.commonLib, line 43
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 44
  *                                   TODO: Versions of the main module + included libraries (in the 'Tuya Device' driver?) // library marker kkossev.commonLib, line 45
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 46
  * // library marker kkossev.commonLib, line 47
*/ // library marker kkossev.commonLib, line 48

String commonLibVersion() { '4.1.1' } // library marker kkossev.commonLib, line 50
String commonLibStamp() { '2026/08/23 4:28 PM' } // library marker kkossev.commonLib, line 51

import groovy.transform.Field // library marker kkossev.commonLib, line 53
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 54
import hubitat.device.Protocol // library marker kkossev.commonLib, line 55
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 56
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 57
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 58
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 59
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 60
import java.math.BigDecimal // library marker kkossev.commonLib, line 61

metadata { // library marker kkossev.commonLib, line 63
        if (_DEBUG) { // library marker kkossev.commonLib, line 64
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 65
            command 'testParse', [[name: 'testParse', type: 'STRING', description: 'testParse', defaultValue : '']] // library marker kkossev.commonLib, line 66
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 67
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 68
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 69
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 70
            ] // library marker kkossev.commonLib, line 71
        } // library marker kkossev.commonLib, line 72

        // common capabilities for all device types // library marker kkossev.commonLib, line 74
        capability 'Configuration' // library marker kkossev.commonLib, line 75
        capability 'Refresh' // library marker kkossev.commonLib, line 76
        capability 'HealthCheck' // library marker kkossev.commonLib, line 77
        capability 'PowerSource'       // powerSource - ENUM ["battery", "dc", "mains", "unknown"] // library marker kkossev.commonLib, line 78

        // common attributes for all device types // library marker kkossev.commonLib, line 80
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 81
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 82
        attribute '_status_', 'string' // library marker kkossev.commonLib, line 83

        // common commands for all device types // library marker kkossev.commonLib, line 85
        // 'configure' below carries a description-only parameter map (NO 'type' key!), exactly like ping and refresh - it just renders the help text under the button and submits nothing. // library marker kkossev.commonLib, line 86
        // NEVER give it a typed parameter: an ENUM here used to shadow the no-argument configure() of capability 'Configuration', making the dispatch depend on whether the platform happened to supply a value. // library marker kkossev.commonLib, line 87
        command 'configure', [[name:"✋ This button can not configure battery-powered 'sleepy' devices. Pair the device again to your hub, without deleting it!"]] // library marker kkossev.commonLib, line 88
        command 'deviceUtilities', [[name:'⚙️ Advanced administrative and diagnostic commands • Use only when troubleshooting or reconfiguring the device', type: 'ENUM', constraints: ConfigureOpts.keySet() as List<String>]]    // do NOT add a 'defaultValue' here! The drop-down still displays '- No selection -', but the platform submits the defaultValue when Run is pressed - i.e. an un-selected Run silently executed 'LOAD ALL DEFAULTS' (tested on C-8 Pro 2.5.1.143) // library marker kkossev.commonLib, line 89
        // one-click shortcut for the most used deviceUtilities entry. Description-only parameter map again - NEVER give loadAllDefaults a typed parameter: deviceUtilities dispatches it as "$func"() with no arguments, so an un-selected Run would hit the no-argument overload and wipe the device immediately. // library marker kkossev.commonLib, line 90
        command 'loadAllDefaults', [[name:'⚠️ Erases all preferences, states, scheduled jobs and child devices, then reloads the driver defaults • Use after switching drivers, or when the device was not recognised by an older version']] // library marker kkossev.commonLib, line 91
        command 'ping', [[name:'📶 Test device connectivity and measure response time • Updates the RTT attribute with round-trip time in milliseconds']] // library marker kkossev.commonLib, line 92
        command 'refresh', [[name:"🔄 Query the device for current state and update the attributes. • ⚠️ Battery-powered 'sleepy' devices may not respond!"]] // library marker kkossev.commonLib, line 93

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 95
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 96

    preferences { // library marker kkossev.commonLib, line 98
        // txtEnable and logEnable moved to the custom driver settings - copy& paste there ... // library marker kkossev.commonLib, line 99
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.' // library marker kkossev.commonLib, line 100
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: 'Turns on debug logging for 24 hours.' // library marker kkossev.commonLib, line 101

        if (device) { // library marker kkossev.commonLib, line 103
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: 'The advanced options should be already automatically set in an optimal way for your device...Click on the "Save and Close" button when toggling this option!', defaultValue: false // library marker kkossev.commonLib, line 104
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 105
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: 'Method to check device online/offline status.' // library marker kkossev.commonLib, line 106
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: 'How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"' // library marker kkossev.commonLib, line 107
                input name: 'ignoreDuplicatedZigbeeMessages', type: 'bool', title: '<b>Ignore Duplicated Zigbee Messages</b>', defaultValue: false, description: 'Ignore identical Zigbee attribute reports received within short time periods to reduce log spam and redundant processing' // library marker kkossev.commonLib, line 108
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: 'Turns on detailed extra trace logging for 30 minutes.' // library marker kkossev.commonLib, line 109
            } // library marker kkossev.commonLib, line 110
        } // library marker kkossev.commonLib, line 111
    } // library marker kkossev.commonLib, line 112
} // library marker kkossev.commonLib, line 113

@Field static final Integer IGNORE_DUPLICATED_ZIGBEE_MESSAGES_TIMER = 1000  // 1 second // library marker kkossev.commonLib, line 115
@Field static final Integer DIGITAL_TIMER = 5000             // command was sent by this driver // library marker kkossev.commonLib, line 116
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 117
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 118
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 119
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 120
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 121
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 122
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 123
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 124
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 125
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 126

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 128
    defaultValue: 1, options: [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 129
] // library marker kkossev.commonLib, line 130
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 131
    defaultValue: 240, options: [2: 'Every 2 Mins', 10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 135
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'], // library marker kkossev.commonLib, line 136
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 137
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 138
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 139
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 140
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 141
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 142
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'] // library marker kkossev.commonLib, line 143
] // library marker kkossev.commonLib, line 144

public boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 146

/** // library marker kkossev.commonLib, line 148
 * Parse Zigbee message // library marker kkossev.commonLib, line 149
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 150
 */ // library marker kkossev.commonLib, line 151
public void parse(final String description) { // library marker kkossev.commonLib, line 152

    Map stateCopy = state            // .clone() throws java.lang.CloneNotSupportedException in HE platform version 2.4.1.155 ! // library marker kkossev.commonLib, line 154
    checkDriverVersion(stateCopy)    // +1 ms // library marker kkossev.commonLib, line 155
    if (state.stats != null) { state.stats?.rxCtr= (state.stats?.rxCtr ?: 0) + 1 } else { state.stats = [:] }  // updateRxStats(state) // +1 ms // library marker kkossev.commonLib, line 156
    if (state.lastRx != null) { state.lastRx?.timeStamp = unix2formattedDate(now()) } else { state.lastRx = [:] } // library marker kkossev.commonLib, line 157
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 158
    setHealthStatusOnline(state)    // +2 ms // library marker kkossev.commonLib, line 159

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 161
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 162
        if (this.respondsTo('customParseIasMessage')) { customParseIasMessage(description) } // library marker kkossev.commonLib, line 163
        else if (this.respondsTo('standardParseIasMessage')) { standardParseIasMessage(description) } // library marker kkossev.commonLib, line 164
        else if (this.respondsTo('parseIasMessage')) { parseIasMessage(description) } // library marker kkossev.commonLib, line 165
        else { logDebug "ignored IAS zone status (no IAS parser) description: $description" } // library marker kkossev.commonLib, line 166
        return // library marker kkossev.commonLib, line 167
    } // library marker kkossev.commonLib, line 168
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 169
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 170
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 171
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 172
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 173
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 174
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 175
        return // library marker kkossev.commonLib, line 176
    } // library marker kkossev.commonLib, line 177

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 179
        return // library marker kkossev.commonLib, line 180
    } // library marker kkossev.commonLib, line 181
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 182

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 184
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 185

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 187
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 188
        return // library marker kkossev.commonLib, line 189
    } // library marker kkossev.commonLib, line 190
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 191
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194
    // // library marker kkossev.commonLib, line 195
    if (standardAndCustomParseCluster(descMap, description)) { return } // library marker kkossev.commonLib, line 196
    // // library marker kkossev.commonLib, line 197
    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 198
        case 0x000C :  // special case : ZigUSB                                     // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 199
            if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 200
                customParseAnalogInputClusterDescription(descMap, description)                 // ZigUSB // library marker kkossev.commonLib, line 201
                descMap.remove('additionalAttrs')?.each { final Map map -> customParseAnalogInputClusterDescription(descMap + map, description) } // library marker kkossev.commonLib, line 202
            } // library marker kkossev.commonLib, line 203
            break // library marker kkossev.commonLib, line 204
        case 0x0300 :  // Patch - need refactoring of the standardParseColorControlCluster ! // library marker kkossev.commonLib, line 205
            if (this.respondsTo('standardParseColorControlCluster')) { // library marker kkossev.commonLib, line 206
                standardParseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 207
                descMap.remove('additionalAttrs')?.each { final Map map -> standardParseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 208
            } // library marker kkossev.commonLib, line 209
            break // library marker kkossev.commonLib, line 210
        default: // library marker kkossev.commonLib, line 211
            if (settings.logEnable) { // library marker kkossev.commonLib, line 212
                // descMap.cluster is null for catchall messages - fall back to clusterId, or format clusterInt // library marker kkossev.commonLib, line 213
                String clusterHex = descMap.cluster ?: descMap.clusterId ?: zigbee.convertToHexString(descMap.clusterInt as Integer, 4) // library marker kkossev.commonLib, line 214
                logWarn "parse: zigbee received <b>unknown cluster:0x${clusterHex} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 215
            } // library marker kkossev.commonLib, line 216
            break // library marker kkossev.commonLib, line 217
    } // library marker kkossev.commonLib, line 218
} // library marker kkossev.commonLib, line 219

@Field static final Map<Integer, String> ClustersMap = [ // library marker kkossev.commonLib, line 221
    0x0000: 'Basic',             0x0001: 'Power',            0x0003: 'Identify',         0x0004: 'Groups',           0x0005: 'Scenes',       0x0006: 'OnOff',           0x0007:'onOffConfiguration',      0x0008: 'LevelControl',  // library marker kkossev.commonLib, line 222
    0x000C: 'AnalogInput',       0x0012: 'MultistateInput',  0x0020: 'PollControl',      0x0102: 'WindowCovering',   0x0201: 'Thermostat',  0x0204: 'ThermostatConfig',/*0x0300: 'ColorControl',*/ // library marker kkossev.commonLib, line 223
    0x0400: 'Illuminance',       0x0402: 'Temperature',      0x0405: 'Humidity',         0x0406: 'Occupancy',        0x042A: 'Pm25',         0x0500: 'IAS',             0x0702: 'Metering', // library marker kkossev.commonLib, line 224
    0x0B04: 'ElectricalMeasure', 0xE001: 'E0001',            0xE002: 'E002',             0xEC03: 'EC03',             0xEF00: 'Tuya',         0xFC03: 'FC03',            0xFC11: 'FC11',            0xFC7E: 'AirQualityIndex', // Sensirion VOC index // library marker kkossev.commonLib, line 225
    0xFC80: 'FC80',              0xFC81: 'FC81',             0xFCC0: 'XiaomiFCC0',       0xED00: 'ED00' // library marker kkossev.commonLib, line 226
] // library marker kkossev.commonLib, line 227

// first try calling the custom parser, if not found, call the standard parser // library marker kkossev.commonLib, line 229
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 230
boolean standardAndCustomParseCluster(Map descMap, final String description) { // library marker kkossev.commonLib, line 231
    Integer clusterInt = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 232
    String  clusterName = ClustersMap[clusterInt] ?: UNKNOWN // library marker kkossev.commonLib, line 233
    // descMap.cluster is null for catchall messages - fall back to clusterId, or format clusterInt, so that the logs never show 'cluster:0xnull' // library marker kkossev.commonLib, line 234
    String  clusterHex = descMap.cluster ?: descMap.clusterId ?: zigbee.convertToHexString(clusterInt, 4) // library marker kkossev.commonLib, line 235
    if (clusterName == null || clusterName == UNKNOWN) { // library marker kkossev.commonLib, line 236
        logWarn "standardAndCustomParseCluster: zigbee received <b>unknown cluster:0x${clusterHex} (${clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 237
        return false // library marker kkossev.commonLib, line 238
    } // library marker kkossev.commonLib, line 239
    String customParser = "customParse${clusterName}Cluster" // library marker kkossev.commonLib, line 240
    // check if a custom parser is defined in the custom driver. If found there, the standard parser should  be called within that custom parser, if needed // library marker kkossev.commonLib, line 241
    if (this.respondsTo(customParser)) { // library marker kkossev.commonLib, line 242
        this."${customParser}"(descMap) // library marker kkossev.commonLib, line 243
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${customParser}"(descMap + map) } // library marker kkossev.commonLib, line 244
        return true // library marker kkossev.commonLib, line 245
    } // library marker kkossev.commonLib, line 246
    String standardParser = "standardParse${clusterName}Cluster" // library marker kkossev.commonLib, line 247
    // if no custom parser is defined, try the standard parser (if exists), eventually defined in the included library file // library marker kkossev.commonLib, line 248
    if (this.respondsTo(standardParser)) { // library marker kkossev.commonLib, line 249
        this."${standardParser}"(descMap) // library marker kkossev.commonLib, line 250
        descMap.remove('additionalAttrs')?.each { final Map map -> this."${standardParser}"(descMap + map) } // library marker kkossev.commonLib, line 251
        return true // library marker kkossev.commonLib, line 252
    } // library marker kkossev.commonLib, line 253
    if (device?.getDataValue('model') != 'ZigUSB' && descMap.cluster != '0300') {    // patch! // library marker kkossev.commonLib, line 254
        logWarn "standardAndCustomParseCluster: <b>Missing</b> ${standardParser} or ${customParser} handler for <b>cluster:0x${clusterHex} (${clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 255
    } // library marker kkossev.commonLib, line 256
    return false // library marker kkossev.commonLib, line 257
} // library marker kkossev.commonLib, line 258

// not used - throws exception :  error groovy.lang.MissingPropertyException: No such property: rxCtr for class: java.lang.String on line 1568 (method parse) // library marker kkossev.commonLib, line 260
private static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 261
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 262
} // library marker kkossev.commonLib, line 263

public boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 265
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 266
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 267
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 268
    } // library marker kkossev.commonLib, line 269
    return false // library marker kkossev.commonLib, line 270
} // library marker kkossev.commonLib, line 271

public boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 273
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 274
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 275
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 276
    } // library marker kkossev.commonLib, line 277
    return false // library marker kkossev.commonLib, line 278
} // library marker kkossev.commonLib, line 279

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 281
    0x0002: 'Node Descriptor Request',  0x0005: 'Active Endpoints Request',   0x0006: 'Match Descriptor Request',  0x0022: 'Unbind Request',  0x0013: 'Device announce', 0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 282
    0x8002: 'Node Descriptor Response', 0x8004: 'Simple Descriptor Response', 0x8005: 'Active Endpoints Response', 0x801D: 'Extended Simple Descriptor Response', 0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 283
    0x8021: 'Bind Response',            0x8022: 'Unbind Response',            0x8023: 'Bind Register Response',    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 284
] // library marker kkossev.commonLib, line 285

// ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 287
private void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 288
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 289
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 290
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 291
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 292
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 293
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 294
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 295
    List<String> cmds = [] // library marker kkossev.commonLib, line 296
    switch (clusterId) { // library marker kkossev.commonLib, line 297
        case 0x0005 : // library marker kkossev.commonLib, line 298
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 299
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 300
            // send the active endpoint response // library marker kkossev.commonLib, line 301
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8005 {00 00 00 00 01 01} {0x0000}"] // library marker kkossev.commonLib, line 302
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 303
            break // library marker kkossev.commonLib, line 304
        case 0x0006 : // library marker kkossev.commonLib, line 305
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 306
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 307
            cmds += ["he raw ${device.deviceNetworkId} 0 0 0x8006 {00 00 00 00 00} {0x0000}"] // library marker kkossev.commonLib, line 308
            sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 311
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 312
            if (settings?.logEnable) { log.debug "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 313
            break // library marker kkossev.commonLib, line 314
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 315
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 316
            if (this.respondsTo('parseSimpleDescriptorResponse')) { parseSimpleDescriptorResponse(descMap) } // library marker kkossev.commonLib, line 317
            break // library marker kkossev.commonLib, line 318
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 319
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 320
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 321
            if (settings?.logEnable) { log.debug "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 322
            break // library marker kkossev.commonLib, line 323
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 324
            if (settings?.logEnable) { log.debug "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 325
            break // library marker kkossev.commonLib, line 326
        case 0x0002 : // Node Descriptor Request // library marker kkossev.commonLib, line 327
        case 0x0036 : // Permit Joining Request // library marker kkossev.commonLib, line 328
        case 0x8022 : // unbind request // library marker kkossev.commonLib, line 329
        case 0x8034 : // leave response // library marker kkossev.commonLib, line 330
            if (settings?.logEnable) { log.debug "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 331
            break // library marker kkossev.commonLib, line 332
        default : // library marker kkossev.commonLib, line 333
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 334
            break // library marker kkossev.commonLib, line 335
    } // library marker kkossev.commonLib, line 336
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 337
} // library marker kkossev.commonLib, line 338

// Zigbee General Command Parsing // library marker kkossev.commonLib, line 340
private void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 341
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 342
    switch (commandId) { // library marker kkossev.commonLib, line 343
        case 0x01: parseReadAttributeResponse(descMap); break // library marker kkossev.commonLib, line 344
        case 0x04: parseWriteAttributeResponse(descMap); break // library marker kkossev.commonLib, line 345
        case 0x07: parseConfigureResponse(descMap); break // library marker kkossev.commonLib, line 346
        case 0x09: parseReadReportingConfigResponse(descMap); break // library marker kkossev.commonLib, line 347
        case 0x0B: parseDefaultCommandResponse(descMap); break // library marker kkossev.commonLib, line 348
        default: // library marker kkossev.commonLib, line 349
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 350
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 351
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 352
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 353
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 354
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 355
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 356
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 357
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 358
            } // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
    } // library marker kkossev.commonLib, line 361
} // library marker kkossev.commonLib, line 362

// Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 364
private void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 365
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 366
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 367
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 368
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 369
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 370
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 371
    } // library marker kkossev.commonLib, line 372
    else { // library marker kkossev.commonLib, line 373
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 374
    } // library marker kkossev.commonLib, line 375
} // library marker kkossev.commonLib, line 376

// Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 378
private void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 379
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 380
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 381
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 382
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 383
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 384
    } // library marker kkossev.commonLib, line 385
    else { // library marker kkossev.commonLib, line 386
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 387
    } // library marker kkossev.commonLib, line 388
} // library marker kkossev.commonLib, line 389

// Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 391
private void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 392
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 393
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 394
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 395
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 396
        state.reportingEnabled = true // library marker kkossev.commonLib, line 397
    } // library marker kkossev.commonLib, line 398
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 399
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 400
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 401
    } else { // library marker kkossev.commonLib, line 402
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 403
    } // library marker kkossev.commonLib, line 404
} // library marker kkossev.commonLib, line 405

// Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 407
private void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 408
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 409
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 410
    if (status == 0) { // library marker kkossev.commonLib, line 411
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 412
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 413
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 414
        int delta = 0 // library marker kkossev.commonLib, line 415
        if (descMap.data.size() >= 11) { // library marker kkossev.commonLib, line 416
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 417
        } // library marker kkossev.commonLib, line 418
        else if (descMap.data.size() == 10) { // library marker kkossev.commonLib, line 419
            delta = zigbee.convertHexToInt(descMap.data[9])      // 1-byte reportable change (uint8/int8) // library marker kkossev.commonLib, line 420
        } // library marker kkossev.commonLib, line 421
        else { // library marker kkossev.commonLib, line 422
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 423
        } // library marker kkossev.commonLib, line 424
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 425
    } // library marker kkossev.commonLib, line 426
    else { // library marker kkossev.commonLib, line 427
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 428
    } // library marker kkossev.commonLib, line 429
} // library marker kkossev.commonLib, line 430

private Boolean executeCustomHandler(String handlerName, Object handlerArgs) { // library marker kkossev.commonLib, line 432
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 433
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 434
        return false // library marker kkossev.commonLib, line 435
    } // library marker kkossev.commonLib, line 436
    // execute the customHandler function // library marker kkossev.commonLib, line 437
    Boolean result = false // library marker kkossev.commonLib, line 438
    try { // library marker kkossev.commonLib, line 439
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 440
    } // library marker kkossev.commonLib, line 441
    catch (e) { // library marker kkossev.commonLib, line 442
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 443
        return false // library marker kkossev.commonLib, line 444
    } // library marker kkossev.commonLib, line 445
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 446
    return result // library marker kkossev.commonLib, line 447
} // library marker kkossev.commonLib, line 448

// Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 450
private void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 451
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 452
    final String commandId = data[0] // library marker kkossev.commonLib, line 453
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 454
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 455
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 456
        // Tuya EF00 devices answer every DP write (command 0x00) with a Default Response of 0x01 'Failure' // library marker kkossev.commonLib, line 457
        // regardless of the outcome - hub-verified 2026-08-24 on _TZE200_2aaelwxk (ZG-204ZM): a write that // library marker kkossev.commonLib, line 458
        // genuinely changed dp 102 from 30 to 60 was acknowledged with the same 'Failure'. Not worth a warning. // library marker kkossev.commonLib, line 459
        if (descMap.clusterInt == CLUSTER_TUYA && commandId == '00') { // library marker kkossev.commonLib, line 460
            logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status} (Tuya EF00 write - status byte is not meaningful)" // library marker kkossev.commonLib, line 461
        } // library marker kkossev.commonLib, line 462
        else { // library marker kkossev.commonLib, line 463
            logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 464
        } // library marker kkossev.commonLib, line 465
    } else { // library marker kkossev.commonLib, line 466
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 467
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 468
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 469
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 470
        } // library marker kkossev.commonLib, line 471
    } // library marker kkossev.commonLib, line 472
} // library marker kkossev.commonLib, line 473

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 475
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 476
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 477
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 478

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 480
    0x00: 'Success', 0x01: 'Failure', 0x02: 'Not Authorized', 0x80: 'Malformed Command', 0x81: 'Unsupported COMMAND', 0x85: 'Invalid Field', 0x86: 'Unsupported Attribute', 0x87: 'Invalid Value', 0x88: 'Read Only', // library marker kkossev.commonLib, line 481
    0x89: 'Insufficient Space', 0x8A: 'Duplicate Exists', 0x8B: 'Not Found', 0x8C: 'Unreportable Attribute', 0x8D: 'Invalid Data Type', 0x8E: 'Invalid Selector', 0x94: 'Time out', 0x9A: 'Notification Pending', 0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 482
] // library marker kkossev.commonLib, line 483

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 485
    0x00: 'Read Attributes', 0x01: 'Read Attributes Response', 0x02: 'Write Attributes', 0x03: 'Write Attributes Undivided', 0x04: 'Write Attributes Response', 0x05: 'Write Attributes No Response', 0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 486
    0x07: 'Configure Reporting Response', 0x08: 'Read Reporting Configuration', 0x09: 'Read Reporting Configuration Response', 0x0A: 'Report Attributes', 0x0B: 'Default Response', 0x0C: 'Discover Attributes', 0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 487
    0x0E: 'Read Attributes Structured', 0x0F: 'Write Attributes Structured', 0x10: 'Write Attributes Structured Response', 0x11: 'Discover Commands Received', 0x12: 'Discover Commands Received Response', 0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 488
    0x14: 'Discover Commands Generated Response', 0x15: 'Discover Attributes Extended', 0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 489
] // library marker kkossev.commonLib, line 490

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 492
private BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 493
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 494
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 495
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 496
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 497
    return avg // library marker kkossev.commonLib, line 498
} // library marker kkossev.commonLib, line 499

private void handlePingResponse() { // library marker kkossev.commonLib, line 501
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 502
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 503
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 504

    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 506
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 507
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 508
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 509
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 510
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 511
        sendRttEvent() // library marker kkossev.commonLib, line 512
    } // library marker kkossev.commonLib, line 513
    else { // library marker kkossev.commonLib, line 514
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 515
    } // library marker kkossev.commonLib, line 516
    state.states['isPing'] = false // library marker kkossev.commonLib, line 517
} // library marker kkossev.commonLib, line 518

/* // library marker kkossev.commonLib, line 520
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 521
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 522
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 523
*/ // library marker kkossev.commonLib, line 524
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 525

// Zigbee Basic Cluster Parsing  0x0000 - called from the main parse method // library marker kkossev.commonLib, line 527
private void standardParseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 528
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 529
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 530
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 531
    boolean isPing = state.states?.isPing ?: false // library marker kkossev.commonLib, line 532
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 533
        case 0x0000: // library marker kkossev.commonLib, line 534
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 535
            break // library marker kkossev.commonLib, line 536
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 537
            if (isPing) { // library marker kkossev.commonLib, line 538
                handlePingResponse() // library marker kkossev.commonLib, line 539
            } // library marker kkossev.commonLib, line 540
            else { // library marker kkossev.commonLib, line 541
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 542
            } // library marker kkossev.commonLib, line 543
            break // library marker kkossev.commonLib, line 544
        case 0x0004: // library marker kkossev.commonLib, line 545
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 546
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 547
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 548
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 549
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 550
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 551
            } // library marker kkossev.commonLib, line 552
            break // library marker kkossev.commonLib, line 553
        case 0x0005: // library marker kkossev.commonLib, line 554
            if (isPing) { // library marker kkossev.commonLib, line 555
                handlePingResponse() // library marker kkossev.commonLib, line 556
            } // library marker kkossev.commonLib, line 557
            else { // library marker kkossev.commonLib, line 558
                logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 559
                // received device model Remote Control N2 // library marker kkossev.commonLib, line 560
                String model = device.getDataValue('model') // library marker kkossev.commonLib, line 561
                if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 562
                    logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 563
                    device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 564
                } // library marker kkossev.commonLib, line 565
            } // library marker kkossev.commonLib, line 566
            break // library marker kkossev.commonLib, line 567
        case 0x0007: // library marker kkossev.commonLib, line 568
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 569
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 570
            String currentPowerSource = device.getDataValue('powerSource') // library marker kkossev.commonLib, line 571
            if (currentPowerSource == null || currentPowerSource == 'unknown') { // library marker kkossev.commonLib, line 572
                logInfo "updating device powerSource from ${currentPowerSource} to ${powerSourceReported}" // library marker kkossev.commonLib, line 573
                sendEvent(name: 'powerSource', value: powerSourceReported, type: 'physical') // library marker kkossev.commonLib, line 574
            } // library marker kkossev.commonLib, line 575
            break // library marker kkossev.commonLib, line 576
        case 0xFFDF: // library marker kkossev.commonLib, line 577
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 578
            break // library marker kkossev.commonLib, line 579
        case 0xFFE2: // library marker kkossev.commonLib, line 580
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 581
            break // library marker kkossev.commonLib, line 582
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 583
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 584
            break // library marker kkossev.commonLib, line 585
        case 0xFFFE: // library marker kkossev.commonLib, line 586
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 587
            break // library marker kkossev.commonLib, line 588
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 589
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 590
            logInfo "device firmware version is ${version}" // library marker kkossev.commonLib, line 591
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 592
            break // library marker kkossev.commonLib, line 593
        default: // library marker kkossev.commonLib, line 594
            logDebug "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 595
            break // library marker kkossev.commonLib, line 596
    } // library marker kkossev.commonLib, line 597
} // library marker kkossev.commonLib, line 598

private void standardParsePollControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 600
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 601
        case 0x0000: logDebug "PollControl cluster: CheckInInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 602
        case 0x0001: logDebug "PollControl cluster: LongPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 603
        case 0x0002: logDebug "PollControl cluster: ShortPollInterval = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 604
        case 0x0003: logDebug "PollControl cluster: FastPollTimeout = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 605
        case 0x0004: logDebug "PollControl cluster: CheckInIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 606
        case 0x0005: logDebug "PollControl cluster: LongPollIntervalMin = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 607
        case 0x0006: logDebug "PollControl cluster: FastPollTimeoutMax = ${descMap?.value}" ; break // library marker kkossev.commonLib, line 608
        default: logDebug "zigbee received unknown PollControl cluster attribute 0x${descMap.attrId} (value ${descMap.value})" ; break // library marker kkossev.commonLib, line 609
    } // library marker kkossev.commonLib, line 610
} // library marker kkossev.commonLib, line 611

public void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 613
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 614
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 615

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 617
    Map descMap = [:] // library marker kkossev.commonLib, line 618
    try { // library marker kkossev.commonLib, line 619
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 620
    } // library marker kkossev.commonLib, line 621
    catch (e1) { // library marker kkossev.commonLib, line 622
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 623
        // try alternative custom parsing // library marker kkossev.commonLib, line 624
        descMap = [:] // library marker kkossev.commonLib, line 625
        try { // library marker kkossev.commonLib, line 626
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 627
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 628
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 629
            } // library marker kkossev.commonLib, line 630
        } // library marker kkossev.commonLib, line 631
        catch (e2) { // library marker kkossev.commonLib, line 632
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 633
            return [:] // library marker kkossev.commonLib, line 634
        } // library marker kkossev.commonLib, line 635
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 636
    } // library marker kkossev.commonLib, line 637
    return descMap // library marker kkossev.commonLib, line 638
} // library marker kkossev.commonLib, line 639

// return true if the messages is processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 641
// return false if the cluster is not a Tuya cluster // library marker kkossev.commonLib, line 642
private boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 643
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 644
        return false // library marker kkossev.commonLib, line 645
    } // library marker kkossev.commonLib, line 646
    // try to parse ... // library marker kkossev.commonLib, line 647
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 648
    Map descMap = [:] // library marker kkossev.commonLib, line 649
    try { // library marker kkossev.commonLib, line 650
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 651
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 652
    } // library marker kkossev.commonLib, line 653
    catch (e) { // library marker kkossev.commonLib, line 654
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 655
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 656
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 657
        return true // library marker kkossev.commonLib, line 658
    } // library marker kkossev.commonLib, line 659

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 661
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 662
    } // library marker kkossev.commonLib, line 663
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 664
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 665
    } // library marker kkossev.commonLib, line 666
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 667
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 668
    } // library marker kkossev.commonLib, line 669
    else { // library marker kkossev.commonLib, line 670
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 671
        return false // library marker kkossev.commonLib, line 672
    } // library marker kkossev.commonLib, line 673
    return true    // processed // library marker kkossev.commonLib, line 674
} // library marker kkossev.commonLib, line 675

// return true if processed here, and further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 677
private boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 678
  /* // library marker kkossev.commonLib, line 679
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 680
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 681
        return true // library marker kkossev.commonLib, line 682
    } // library marker kkossev.commonLib, line 683
*/ // library marker kkossev.commonLib, line 684
    Map descMap = [:] // library marker kkossev.commonLib, line 685
    try { // library marker kkossev.commonLib, line 686
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 687
    } // library marker kkossev.commonLib, line 688
    catch (e1) { // library marker kkossev.commonLib, line 689
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 690
        // try alternative custom parsing // library marker kkossev.commonLib, line 691
        descMap = [:] // library marker kkossev.commonLib, line 692
        try { // library marker kkossev.commonLib, line 693
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 694
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 695
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 696
            } // library marker kkossev.commonLib, line 697
        } // library marker kkossev.commonLib, line 698
        catch (e2) { // library marker kkossev.commonLib, line 699
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 700
            return true // library marker kkossev.commonLib, line 701
        } // library marker kkossev.commonLib, line 702
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 703
    } // library marker kkossev.commonLib, line 704
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 705
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 706
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 707
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 708
        return false // library marker kkossev.commonLib, line 709
    } // library marker kkossev.commonLib, line 710
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 711
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 712
    // attribute report received // library marker kkossev.commonLib, line 713
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 714
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 715
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 716
    } // library marker kkossev.commonLib, line 717
    attrData.each { // library marker kkossev.commonLib, line 718
        if (it.status == '86') { // library marker kkossev.commonLib, line 719
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 720
        // TODO - skip parsing? // library marker kkossev.commonLib, line 721
        } // library marker kkossev.commonLib, line 722
        switch (it.cluster) { // library marker kkossev.commonLib, line 723
            case '0000' : // library marker kkossev.commonLib, line 724
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 725
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 726
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 727
                } // library marker kkossev.commonLib, line 728
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 729
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 730
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 731
                } // library marker kkossev.commonLib, line 732
                else { // library marker kkossev.commonLib, line 733
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 734
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 735
                } // library marker kkossev.commonLib, line 736
                break // library marker kkossev.commonLib, line 737
            default : // library marker kkossev.commonLib, line 738
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 739
                break // library marker kkossev.commonLib, line 740
        } // switch // library marker kkossev.commonLib, line 741
    } // for each attribute // library marker kkossev.commonLib, line 742
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 743
} // library marker kkossev.commonLib, line 744

public String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 746
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 747
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 748
} // library marker kkossev.commonLib, line 749

public String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 751
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 752
} // library marker kkossev.commonLib, line 753

/* // library marker kkossev.commonLib, line 755
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 756
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 757
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 758
*/ // library marker kkossev.commonLib, line 759
private static int getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 760
private static int getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 761
private static int getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 762

// Tuya Commands // library marker kkossev.commonLib, line 764
private static int getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 765
private static int getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 766
private static int getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 767
private static int getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 768
private static int getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 769

// tuya DP type // library marker kkossev.commonLib, line 771
private static String getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 772
private static String getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 773
private static String getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 774
private static String getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 775
private static String getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 776
private static String getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 777

private void syncTuyaDateTime() { // library marker kkossev.commonLib, line 779
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 780
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 781
    long offset = 0 // library marker kkossev.commonLib, line 782
    int offsetHours = 0 // library marker kkossev.commonLib, line 783
    Calendar cal = Calendar.getInstance()    //it return same time as new Date() // library marker kkossev.commonLib, line 784
    int hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 785
    try { // library marker kkossev.commonLib, line 786
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 787
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 788
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 789
    } catch (e) { // library marker kkossev.commonLib, line 790
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 791
    } // library marker kkossev.commonLib, line 792
    // // library marker kkossev.commonLib, line 793
    List<String> cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 794
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 795
    logDebug "Tuya device time synchronized to ${unix2formattedDate(now())} (${cmds})" // library marker kkossev.commonLib, line 796
} // library marker kkossev.commonLib, line 797

// called from the main parse method when the cluster is 0xEF00 and no custom handler is defined // library marker kkossev.commonLib, line 799
public void standardParseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 800
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 801
        syncTuyaDateTime() // library marker kkossev.commonLib, line 802
    } // library marker kkossev.commonLib, line 803
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 804
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 805
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 806
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 807
        if (status != '00') { // library marker kkossev.commonLib, line 808
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 809
        } // library marker kkossev.commonLib, line 810
    } // library marker kkossev.commonLib, line 811
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 812
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 813
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 814
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 815
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 816
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} data=${descMap?.data})" // library marker kkossev.commonLib, line 817
            return // library marker kkossev.commonLib, line 818
        } // library marker kkossev.commonLib, line 819
        boolean isSpammyDeviceProfileDefined = this.respondsTo('isSpammyDeviceProfile') // check if the method exists 05/21/2024 // library marker kkossev.commonLib, line 820
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 821
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 822
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 823
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 824
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 825
            if (!isChattyDeviceReport(descMap) && isSpammyDeviceProfileDefined && !isSpammyDeviceProfile()) { // library marker kkossev.commonLib, line 826
                logDebug "standardParseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 827
            } // library marker kkossev.commonLib, line 828
            standardProcessTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 829
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 830
        } // library marker kkossev.commonLib, line 831
    } // library marker kkossev.commonLib, line 832
    else { // library marker kkossev.commonLib, line 833
        logWarn "standardParseTuyaCluster: unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 834
    } // library marker kkossev.commonLib, line 835
} // library marker kkossev.commonLib, line 836

// called from the standardParseTuyaCluster method for each DP chunk in the messages (usually one, but could be multiple DPs in one message) // library marker kkossev.commonLib, line 838
void standardProcessTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 839
    logTrace "standardProcessTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 840
    if (this.respondsTo('customProcessTuyaDp')) { // library marker kkossev.commonLib, line 841
        //logTrace 'standardProcessTuyaDP: customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 842
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 843
            return       // EF00 DP has been processed in the custom handler - we are done! // library marker kkossev.commonLib, line 844
        } // library marker kkossev.commonLib, line 845
    } // library marker kkossev.commonLib, line 846
    // check if DeviceProfile processing method exists (deviceProfieLib should be included in the main driver) // library marker kkossev.commonLib, line 847
    if (this.respondsTo('processTuyaDPfromDeviceProfile')) { // library marker kkossev.commonLib, line 848
        //logTrace 'standardProcessTuyaDP: processTuyaDPfromDeviceProfile exists, calling it...' // library marker kkossev.commonLib, line 849
        if (this.respondsTo('isInCooldown') && isInCooldown()) { // library marker kkossev.commonLib, line 850
            logDebug "standardProcessTuyaDP: device is in cooldown, skipping processing of dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 851
            return // library marker kkossev.commonLib, line 852
        } // library marker kkossev.commonLib, line 853
        if (this.respondsTo('ensureCurrentProfileLoaded')) { // library marker kkossev.commonLib, line 854
            ensureCurrentProfileLoaded() // library marker kkossev.commonLib, line 855
        } // library marker kkossev.commonLib, line 856
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 857
            return      // sucessfuly processed the new way - we are done.  (version 3.0) // library marker kkossev.commonLib, line 858
        } // library marker kkossev.commonLib, line 859
    } // library marker kkossev.commonLib, line 860
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 861
} // library marker kkossev.commonLib, line 862

public int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 864
    int retValue = 0 // library marker kkossev.commonLib, line 865
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 866
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 867
        if (dataLength == 0) { return 0 } // library marker kkossev.commonLib, line 868
        int power = 1 // library marker kkossev.commonLib, line 869
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 870
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 871
            power = power * 256 // library marker kkossev.commonLib, line 872
        } // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
    return retValue // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876

public List<String> getTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { return sendTuyaCommand(dp, dp_type, fncmd, tuyaCmdDefault) } // library marker kkossev.commonLib, line 878

public List<String> sendTuyaCommand(String dp, String dp_type, String fncmd, int tuyaCmdDefault = SETDATA) { // library marker kkossev.commonLib, line 880
    List<String> cmds = [] // library marker kkossev.commonLib, line 881
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 882
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 883
    int tuyaCmd // library marker kkossev.commonLib, line 884
    // added 07/01/2024 - deviceProfilesV3 device key tuyaCmd:04 : owerwrite all sendTuyaCommand calls for a specfic device profile, if specified! // library marker kkossev.commonLib, line 885
    if (this.respondsTo('getDEVICE') && getDEVICE()?.device?.tuyaCmd != null) { // library marker kkossev.commonLib, line 886
        tuyaCmd = getDEVICE().device.tuyaCmd // library marker kkossev.commonLib, line 887
    } // library marker kkossev.commonLib, line 888
    else { // library marker kkossev.commonLib, line 889
        tuyaCmd = tuyaCmdDefault // 0x00 is the default command for most of the Tuya devices, except some .. // library marker kkossev.commonLib, line 890
    } // library marker kkossev.commonLib, line 891
    // Get delay from device profile or use default - guarded the same way as tuyaCmd above, because a driver that // library marker kkossev.commonLib, line 892
    // includes commonLib but NOT deviceProfileLib has no DEVICE at all (BUGS.md A3). // library marker kkossev.commonLib, line 893
    int tuyaDelay = (this.respondsTo('getDEVICE') ? (getDEVICE()?.device?.tuyaDelay as Integer) : null) ?: 201 // library marker kkossev.commonLib, line 894
    String tuyaPayload = PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd // library marker kkossev.commonLib, line 895
    // deviceProfile device key disableDefaultResponse:true - suppress the ZCL Default Response that the device is // library marker kkossev.commonLib, line 896
    // otherwise obliged to send for every EF00 command (Tuya answers 0x01 'Failure' regardless of the outcome). // library marker kkossev.commonLib, line 897
    // zigbee.command() always leaves the frame control 'disable default response' bit clear, so the frame has to be // library marker kkossev.commonLib, line 898
    // hand-built as 'he raw' with frame control 0x11 (bit0-1 = cluster specific, bit4 = disable default response). // library marker kkossev.commonLib, line 899
    boolean disableDefaultRsp = this.respondsTo('getDEVICE') ? (getDEVICE()?.device?.disableDefaultResponse == true) : false // library marker kkossev.commonLib, line 900
    if (disableDefaultRsp) { // library marker kkossev.commonLib, line 901
        String epHex  = zigbee.convertToHexString(ep, 2) // library marker kkossev.commonLib, line 902
        String cmdHex = zigbee.convertToHexString(tuyaCmd, 2) // library marker kkossev.commonLib, line 903
        cmds = ["he raw 0x${device.deviceNetworkId} 0x01 0x${epHex} 0x${zigbee.convertToHexString(CLUSTER_TUYA, 4)} {11 ${getZclSeqNo()} ${cmdHex} ${tuyaPayload}} {0x0104}", "delay ${tuyaDelay}"] // library marker kkossev.commonLib, line 904
    } // library marker kkossev.commonLib, line 905
    else { // library marker kkossev.commonLib, line 906
        cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = tuyaDelay, tuyaPayload) // library marker kkossev.commonLib, line 907
    } // library marker kkossev.commonLib, line 908
    logDebug "getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type disableDefaultRsp=${disableDefaultRsp}) = ${cmds}" // library marker kkossev.commonLib, line 909
    return cmds // library marker kkossev.commonLib, line 910
} // library marker kkossev.commonLib, line 911

// ZCL sequence number for hand-built 'he raw' frames - zigbee.command() manages its own, 'he raw' does not. // library marker kkossev.commonLib, line 913
// Must increment, otherwise a burst of writes goes out with a duplicate sequence number. // library marker kkossev.commonLib, line 914
private String getZclSeqNo() { // library marker kkossev.commonLib, line 915
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 916
    int seq = safeToInt(state.lastTx['zclSeq']) + 1 // library marker kkossev.commonLib, line 917
    if (seq > 0xFF) { seq = 1 } // library marker kkossev.commonLib, line 918
    state.lastTx['zclSeq'] = seq // library marker kkossev.commonLib, line 919
    return zigbee.convertToHexString(seq, 2) // library marker kkossev.commonLib, line 920
} // library marker kkossev.commonLib, line 921

private String getPACKET_ID() { return zigbee.convertToHexString(new Random().nextInt(65536), 4) } // library marker kkossev.commonLib, line 923

public void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 925
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 926
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 927
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 928
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 929
} // library marker kkossev.commonLib, line 930


public List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 933
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 934
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 935
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 936
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 937
} // library marker kkossev.commonLib, line 938

public List<String> queryAllTuyaDP() { // library marker kkossev.commonLib, line 940
    logTrace 'queryAllTuyaDP()' // library marker kkossev.commonLib, line 941
    List<String> cmds = zigbee.command(0xEF00, 0x03) // library marker kkossev.commonLib, line 942
    return cmds // library marker kkossev.commonLib, line 943
} // library marker kkossev.commonLib, line 944

public void aqaraBlackMagic() { // library marker kkossev.commonLib, line 946
    List<String> cmds = [] // library marker kkossev.commonLib, line 947
    if (this.respondsTo('customAqaraBlackMagic')) { // library marker kkossev.commonLib, line 948
        cmds = customAqaraBlackMagic() // library marker kkossev.commonLib, line 949
    } // library marker kkossev.commonLib, line 950
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 951
        logDebug 'sending aqaraBlackMagic()' // library marker kkossev.commonLib, line 952
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 953
        return // library marker kkossev.commonLib, line 954
    } // library marker kkossev.commonLib, line 955
    logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 956
} // library marker kkossev.commonLib, line 957

// Invoked from configure() // library marker kkossev.commonLib, line 959
public List<String> initializeDevice() { // library marker kkossev.commonLib, line 960
    List<String> cmds = [] // library marker kkossev.commonLib, line 961
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 962
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 963
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 964
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 965
    } // library marker kkossev.commonLib, line 966
    else { logDebug 'no customInitializeDevice method defined' } // library marker kkossev.commonLib, line 967
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 968
    return cmds // library marker kkossev.commonLib, line 969
} // library marker kkossev.commonLib, line 970

// Invoked from configure() // library marker kkossev.commonLib, line 972
public List<String> configureDevice() { // library marker kkossev.commonLib, line 973
    List<String> cmds = [] // library marker kkossev.commonLib, line 974
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 975
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 976
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 977
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 978
    } // library marker kkossev.commonLib, line 979
    else { logDebug 'no customConfigureDevice method defined' } // library marker kkossev.commonLib, line 980
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 981
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 982
    return cmds // library marker kkossev.commonLib, line 983
} // library marker kkossev.commonLib, line 984

/* // library marker kkossev.commonLib, line 986
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 987
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 988
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 989
*/ // library marker kkossev.commonLib, line 990

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 992
    List<String> cmds = [] // library marker kkossev.commonLib, line 993
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 994
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 995
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 996
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 997
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 998
            } // library marker kkossev.commonLib, line 999
        } // library marker kkossev.commonLib, line 1000
    } // library marker kkossev.commonLib, line 1001
    return cmds // library marker kkossev.commonLib, line 1002
} // library marker kkossev.commonLib, line 1003

public void refresh() { // library marker kkossev.commonLib, line 1005
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 1006
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1007
    List<String> cmds = [], customCmds = [] // library marker kkossev.commonLib, line 1008
    if (this.respondsTo('customRefresh')) {     // if there is a customRefresh() method defined in the main driver, call it // library marker kkossev.commonLib, line 1009
        customCmds = customRefresh() // library marker kkossev.commonLib, line 1010
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no customRefresh method defined' } // library marker kkossev.commonLib, line 1011
    } // library marker kkossev.commonLib, line 1012
    else {  // call all known libraryRefresh methods // library marker kkossev.commonLib, line 1013
        customCmds = customHandlers(['onOffRefresh', 'groupsRefresh', 'batteryRefresh', 'levelRefresh', 'temperatureRefresh', 'humidityRefresh', 'illuminanceRefresh']) // library marker kkossev.commonLib, line 1014
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } else { logDebug 'no libraries refresh() defined' } // library marker kkossev.commonLib, line 1015
    } // library marker kkossev.commonLib, line 1016
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1017
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 1018
        setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1019
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1020
    } // library marker kkossev.commonLib, line 1021
    else { // library marker kkossev.commonLib, line 1022
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1023
    } // library marker kkossev.commonLib, line 1024
} // library marker kkossev.commonLib, line 1025

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, 'clearRefreshRequest', [overwrite: true]) } // library marker kkossev.commonLib, line 1027
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1028
public void clearInfoEvent()      { sendInfoEvent('clear') } // library marker kkossev.commonLib, line 1029

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1031
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1032
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1033
        sendEvent(name: '_status_', value: 'clear', type: 'digital') // library marker kkossev.commonLib, line 1034
    } // library marker kkossev.commonLib, line 1035
    else { // library marker kkossev.commonLib, line 1036
        logInfo "${info}" // library marker kkossev.commonLib, line 1037
        sendEvent(name: '_status_', value: info, type: 'digital') // library marker kkossev.commonLib, line 1038
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1039
    } // library marker kkossev.commonLib, line 1040
} // library marker kkossev.commonLib, line 1041

public void ping() { // library marker kkossev.commonLib, line 1043
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1044
    if (state.states == null ) { state.states = [:] } ; state.states['isPing'] = true // library marker kkossev.commonLib, line 1045
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1046
    int  pingAttr = (device.getDataValue('manufacturer') == 'SONOFF') ? 0x05 : PING_ATTR_ID // library marker kkossev.commonLib, line 1047
    if (isVirtual()) { runInMillis(10, 'virtualPong') } // library marker kkossev.commonLib, line 1048
    else if (device.getDataValue('manufacturer') == 'Aqara') { // library marker kkossev.commonLib, line 1049
        logDebug 'Aqara device ping...' // library marker kkossev.commonLib, line 1050
        sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [destEndpoint: 0x01], 0) ) // library marker kkossev.commonLib, line 1051
    } // library marker kkossev.commonLib, line 1052
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, pingAttr, [:], 0) ) } // library marker kkossev.commonLib, line 1053
    logDebug 'ping...' // library marker kkossev.commonLib, line 1054
} // library marker kkossev.commonLib, line 1055

private void virtualPong() { // library marker kkossev.commonLib, line 1057
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1058
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1059
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1060
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1061
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1062
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '9999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1063
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1064
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1065
        sendRttEvent() // library marker kkossev.commonLib, line 1066
    } // library marker kkossev.commonLib, line 1067
    else { // library marker kkossev.commonLib, line 1068
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1069
    } // library marker kkossev.commonLib, line 1070
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1071
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1072
} // library marker kkossev.commonLib, line 1073

public void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1075
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1076
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1077
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1078
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1079
    if (value == null) { // library marker kkossev.commonLib, line 1080
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1081
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', type: 'physical') // library marker kkossev.commonLib, line 1082
    } // library marker kkossev.commonLib, line 1083
    else { // library marker kkossev.commonLib, line 1084
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1085
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1086
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, type: 'physical') // library marker kkossev.commonLib, line 1087
    } // library marker kkossev.commonLib, line 1088
} // library marker kkossev.commonLib, line 1089

private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1091
    if (cluster != null) { // library marker kkossev.commonLib, line 1092
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1093
    } // library marker kkossev.commonLib, line 1094
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1095
    return 'NULL' // library marker kkossev.commonLib, line 1096
} // library marker kkossev.commonLib, line 1097

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1099
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1100
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1101
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1102
} // library marker kkossev.commonLib, line 1103

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1105
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :( // library marker kkossev.commonLib, line 1106
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1107
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1108
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1109
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1110
    } // library marker kkossev.commonLib, line 1111
} // library marker kkossev.commonLib, line 1112

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1114
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1115
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1116
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1117
    if (state.health?.isHealthCheck == true) { // library marker kkossev.commonLib, line 1118
        logWarn 'device health check failed!' // library marker kkossev.commonLib, line 1119
        state.health?.checkCtr3 = (state.health?.checkCtr3 ?: 0 ) + 1 // library marker kkossev.commonLib, line 1120
        if (state.health?.checkCtr3 >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1121
            if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1122
                sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1123
            } // library marker kkossev.commonLib, line 1124
        } // library marker kkossev.commonLib, line 1125
        state.health['isHealthCheck'] = false // library marker kkossev.commonLib, line 1126
    } // library marker kkossev.commonLib, line 1127
} // library marker kkossev.commonLib, line 1128

private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1130
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1131
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1132
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1133
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135
    else { // library marker kkossev.commonLib, line 1136
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1137
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1138
    } // library marker kkossev.commonLib, line 1139
} // library marker kkossev.commonLib, line 1140

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1142
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1143
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1144
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1145
} // library marker kkossev.commonLib, line 1146

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1148
private void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1149
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1150
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1151
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1152
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1153
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1154
    } // library marker kkossev.commonLib, line 1155
} // library marker kkossev.commonLib, line 1156

private void deviceHealthCheck() { // library marker kkossev.commonLib, line 1158
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1159
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1160
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1161
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1162
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1163
            logWarn 'not present!' // library marker kkossev.commonLib, line 1164
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1165
        } // library marker kkossev.commonLib, line 1166
    } // library marker kkossev.commonLib, line 1167
    else { // library marker kkossev.commonLib, line 1168
        logDebug "deviceHealthCheck - online (notPresentCounter=${(ctr + 1)})" // library marker kkossev.commonLib, line 1169
    } // library marker kkossev.commonLib, line 1170
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1171
    // added 03/06/2025 // library marker kkossev.commonLib, line 1172
    if (settings?.healthCheckMethod as int == 2) { // library marker kkossev.commonLib, line 1173
        state.health['isHealthCheck'] = true // library marker kkossev.commonLib, line 1174
        ping()  // proactively ping the device... // library marker kkossev.commonLib, line 1175
    } // library marker kkossev.commonLib, line 1176
} // library marker kkossev.commonLib, line 1177

private void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1179
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1180
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1181
    if (value == 'online') { // library marker kkossev.commonLib, line 1182
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1183
    } // library marker kkossev.commonLib, line 1184
    else { // library marker kkossev.commonLib, line 1185
        if (settings?.txtEnable) { log.warn "${device.displayName} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1186
    } // library marker kkossev.commonLib, line 1187
} // library marker kkossev.commonLib, line 1188

 // Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1190
void updated() { // library marker kkossev.commonLib, line 1191
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1192
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1193
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1194
    unschedule() // library marker kkossev.commonLib, line 1195

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1197
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1198
        runIn(86400, 'logsOff') // library marker kkossev.commonLib, line 1199
    } // library marker kkossev.commonLib, line 1200
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1201
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1202
        runIn(1800, 'traceOff') // library marker kkossev.commonLib, line 1203
    } // library marker kkossev.commonLib, line 1204

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1206
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1207
        // schedule the periodic timer // library marker kkossev.commonLib, line 1208
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1209
        if (interval > 0) { // library marker kkossev.commonLib, line 1210
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1211
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthMethod]} method" // library marker kkossev.commonLib, line 1212
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1213
        } // library marker kkossev.commonLib, line 1214
    } // library marker kkossev.commonLib, line 1215
    else { // library marker kkossev.commonLib, line 1216
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1217
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1218
    } // library marker kkossev.commonLib, line 1219
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1220
        customUpdated() // library marker kkossev.commonLib, line 1221
    } // library marker kkossev.commonLib, line 1222

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1224
} // library marker kkossev.commonLib, line 1225

private void logsOff() { // library marker kkossev.commonLib, line 1227
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1228
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1229
} // library marker kkossev.commonLib, line 1230
private void traceOff() { // library marker kkossev.commonLib, line 1231
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1232
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1233
} // library marker kkossev.commonLib, line 1234

// the administrative / diagnostic commands drop-down list. Deliberately NOT named 'configure' - overloading the Configuration capability command made the dispatch depend on whether the platform happens to supply an argument // library marker kkossev.commonLib, line 1236
public void deviceUtilities(String command = null) { // library marker kkossev.commonLib, line 1237
    logInfo "deviceUtilities(${command})..." // library marker kkossev.commonLib, line 1238
    if (command == null || !(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1239
        configureHelp(command)      // nothing was selected, or the value is not one of ours - show the help and do nothing else // library marker kkossev.commonLib, line 1240
        return // library marker kkossev.commonLib, line 1241
    } // library marker kkossev.commonLib, line 1242
    // // library marker kkossev.commonLib, line 1243
    String func // library marker kkossev.commonLib, line 1244
    try { // library marker kkossev.commonLib, line 1245
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1246
        "$func"() // library marker kkossev.commonLib, line 1247
    } // library marker kkossev.commonLib, line 1248
    catch (e) { // library marker kkossev.commonLib, line 1249
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1250
        return // library marker kkossev.commonLib, line 1251
    } // library marker kkossev.commonLib, line 1252
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1253
} // library marker kkossev.commonLib, line 1254

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1256
void configureHelp(final String val = null) { // library marker kkossev.commonLib, line 1257
    logInfo "select one of the commands from the list: ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1258
    sendInfoEvent('Please select a command from the drop-down list')      // short _status_ event, auto-cleared after INFO_AUTO_CLEAR_PERIOD // library marker kkossev.commonLib, line 1259
} // library marker kkossev.commonLib, line 1260

public void loadAllDefaults() { // library marker kkossev.commonLib, line 1262
    logDebug 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1263
    deleteAllSettings() // library marker kkossev.commonLib, line 1264
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1265
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1266
    deleteAllStates() // library marker kkossev.commonLib, line 1267
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1268

    initialize() // library marker kkossev.commonLib, line 1270
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1271
    updated() // library marker kkossev.commonLib, line 1272
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1273
} // library marker kkossev.commonLib, line 1274

private void configureNow() { // library marker kkossev.commonLib, line 1276
    configure() // library marker kkossev.commonLib, line 1277
} // library marker kkossev.commonLib, line 1278

/** // library marker kkossev.commonLib, line 1280
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1281
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1282
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1283
 */ // library marker kkossev.commonLib, line 1284
void configure() { // library marker kkossev.commonLib, line 1285
    List<String> cmds = [] // library marker kkossev.commonLib, line 1286
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1287
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1288
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1289
    if (isTuya()) { // library marker kkossev.commonLib, line 1290
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1291
    } // library marker kkossev.commonLib, line 1292
    aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1293
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1294
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1295
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1296
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1297
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1298
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1299
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1300
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1301
    } // library marker kkossev.commonLib, line 1302
    else { // library marker kkossev.commonLib, line 1303
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1304
    } // library marker kkossev.commonLib, line 1305
} // library marker kkossev.commonLib, line 1306

 // Invoked when the device is installed with this driver automatically selected. // library marker kkossev.commonLib, line 1308
void installed() { // library marker kkossev.commonLib, line 1309
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1310
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1311
    // populate some default values for attributes // library marker kkossev.commonLib, line 1312
    sendEvent(name: 'healthStatus', value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1313
    sendEvent(name: 'powerSource',  value: 'unknown', descriptionText: 'device was installed', type: 'digital') // library marker kkossev.commonLib, line 1314
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1315
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1316
    runIn(5, 'queryPowerSource') // library marker kkossev.commonLib, line 1317
} // library marker kkossev.commonLib, line 1318

private void queryPowerSource() { // library marker kkossev.commonLib, line 1320
    sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x0007, [:], 0)) // library marker kkossev.commonLib, line 1321
} // library marker kkossev.commonLib, line 1322

 // Invoked from 'LoadAllDefaults' // library marker kkossev.commonLib, line 1324
private void initialize() { // library marker kkossev.commonLib, line 1325
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1326
    logDebug "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1327
    if (device.getDataValue('powerSource') == null) { // library marker kkossev.commonLib, line 1328
        logDebug "initializing device powerSource 'unknown'" // library marker kkossev.commonLib, line 1329
        sendEvent(name: 'powerSource', value: 'unknown', type: 'digital') // library marker kkossev.commonLib, line 1330
    } // library marker kkossev.commonLib, line 1331
    if (this.respondsTo('customInitialize')) { customInitialize() }  // library marker kkossev.commonLib, line 1332
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1333
    updateTuyaVersion() // library marker kkossev.commonLib, line 1334
    updateAqaraVersion() // library marker kkossev.commonLib, line 1335
} // library marker kkossev.commonLib, line 1336

/* // library marker kkossev.commonLib, line 1338
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1339
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1340
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1341
*/ // library marker kkossev.commonLib, line 1342

static Integer safeToInt(Object val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1344
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1345
} // library marker kkossev.commonLib, line 1346

static Double safeToDouble(Object val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1348
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1349
} // library marker kkossev.commonLib, line 1350

static BigDecimal safeToBigDecimal(Object val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1352
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1353
} // library marker kkossev.commonLib, line 1354

public void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1356
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1357
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1358
        return // library marker kkossev.commonLib, line 1359
    } // library marker kkossev.commonLib, line 1360
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1361
    cmd.each { // library marker kkossev.commonLib, line 1362
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1363
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1364
            return // library marker kkossev.commonLib, line 1365
        } // library marker kkossev.commonLib, line 1366
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1367
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1368
    } // library marker kkossev.commonLib, line 1369
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1370
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1371
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1372
} // library marker kkossev.commonLib, line 1373

private String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1375

private String getDeviceInfo() { // library marker kkossev.commonLib, line 1377
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1378
} // library marker kkossev.commonLib, line 1379

public String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1381
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1382
} // library marker kkossev.commonLib, line 1383

//@CompileStatic // library marker kkossev.commonLib, line 1385
public void checkDriverVersion(final Map stateCopy) { // library marker kkossev.commonLib, line 1386
    if (stateCopy.driverVersion == null || driverVersionAndTimeStamp() != stateCopy.driverVersion) { // library marker kkossev.commonLib, line 1387
        logDebug "checkDriverVersion: updating the settings from the current driver version ${stateCopy.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1388
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()} from version ${stateCopy.driverVersion ?: 'unknown'}") // library marker kkossev.commonLib, line 1389
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1390
        initializeVars(false) // library marker kkossev.commonLib, line 1391
        updateTuyaVersion() // library marker kkossev.commonLib, line 1392
        updateAqaraVersion() // library marker kkossev.commonLib, line 1393
        if (this.respondsTo('customcheckDriverVersion')) { customcheckDriverVersion(stateCopy) } // library marker kkossev.commonLib, line 1394
    } // library marker kkossev.commonLib, line 1395
    if (state.states == null) { state.states = [:] } ; if (state.lastRx == null) { state.lastRx = [:] } ; if (state.lastTx == null) { state.lastTx = [:] } ; if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1396
} // library marker kkossev.commonLib, line 1397

// credits @thebearmay // library marker kkossev.commonLib, line 1399
String getModel() { // library marker kkossev.commonLib, line 1400
    try { // library marker kkossev.commonLib, line 1401
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1402
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1403
    } catch (ignore) { // library marker kkossev.commonLib, line 1404
        try { // library marker kkossev.commonLib, line 1405
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1406
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1407
                return model // library marker kkossev.commonLib, line 1408
            } // library marker kkossev.commonLib, line 1409
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1410
            return '' // library marker kkossev.commonLib, line 1411
        } // library marker kkossev.commonLib, line 1412
    } // library marker kkossev.commonLib, line 1413
} // library marker kkossev.commonLib, line 1414

// credits @thebearmay // library marker kkossev.commonLib, line 1416
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1417
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1418
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1419
    String revision = tokens.last() // library marker kkossev.commonLib, line 1420
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1421
} // library marker kkossev.commonLib, line 1422

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1424
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1425
    unschedule() // library marker kkossev.commonLib, line 1426
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1427
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1428

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1430
} // library marker kkossev.commonLib, line 1431

void resetStatistics() { // library marker kkossev.commonLib, line 1433
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1434
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1435
} // library marker kkossev.commonLib, line 1436

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1438
void resetStats() { // library marker kkossev.commonLib, line 1439
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1440
    state.stats = [:] ; state.states = [:] ; state.lastRx = [:] ; state.lastTx = [:] ; state.health = [:] // library marker kkossev.commonLib, line 1441
    if (this.respondsTo('groupsLibVersion')) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1442
    state.stats.rxCtr = 0 ; state.stats.txCtr = 0 // library marker kkossev.commonLib, line 1443
    state.states['isDigital'] = false ; state.states['isRefresh'] = false ; state.states['isPing'] = false // library marker kkossev.commonLib, line 1444
    state.health['offlineCtr'] = 0 ; state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1445
    if (this.respondsTo('customResetStats')) { customResetStats() } // library marker kkossev.commonLib, line 1446
    logInfo 'statistics reset!' // library marker kkossev.commonLib, line 1447
} // library marker kkossev.commonLib, line 1448

void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1450
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1451
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1452
        state.clear() // library marker kkossev.commonLib, line 1453
        unschedule() // library marker kkossev.commonLib, line 1454
        resetStats() // library marker kkossev.commonLib, line 1455
        if (this.respondsTo('setDeviceNameAndProfile')) { setDeviceNameAndProfile() } // library marker kkossev.commonLib, line 1456
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1457
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1458
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1459
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1460
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1461
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1462
    } // library marker kkossev.commonLib, line 1463

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1465
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1466
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1467
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1468
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1469

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1471
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1472
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1473
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1474
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1475
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1476
    if (fullInit || settings?.ignoreDuplicatedZigbeeMessages == null) { device.updateSetting('ignoreDuplicatedZigbeeMessages', false) } // library marker kkossev.commonLib, line 1477
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1478

    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1480

    // common libraries initialization // library marker kkossev.commonLib, line 1482
    executeCustomHandler('batteryInitializeVars', fullInit)     // added 07/06/2024 // library marker kkossev.commonLib, line 1483
    executeCustomHandler('motionInitializeVars', fullInit)      // added 07/06/2024 // library marker kkossev.commonLib, line 1484
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1485
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1486
    executeCustomHandler('onOfInitializeVars', fullInit) // library marker kkossev.commonLib, line 1487
    executeCustomHandler('energyInitializeVars', fullInit) // library marker kkossev.commonLib, line 1488
    // // library marker kkossev.commonLib, line 1489
    executeCustomHandler('deviceProfileInitializeVars', fullInit)   // must be before the other deviceProfile initialization handlers! // library marker kkossev.commonLib, line 1490
    executeCustomHandler('initEventsDeviceProfile', fullInit)   // added 07/06/2024 // library marker kkossev.commonLib, line 1491
    // // library marker kkossev.commonLib, line 1492
    // custom device driver specific initialization should be at the end // library marker kkossev.commonLib, line 1493
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1494
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1495
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1496

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1498
    if (mm != null) { logTrace " model = ${mm}" } // library marker kkossev.commonLib, line 1499
    else { logWarn ' Model not found, please re-pair the device!' } // library marker kkossev.commonLib, line 1500
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1501
    if ( ep  != null) { // library marker kkossev.commonLib, line 1502
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1503
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1504
    } // library marker kkossev.commonLib, line 1505
    else { // library marker kkossev.commonLib, line 1506
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1507
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1508
    } // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

// not used!? // library marker kkossev.commonLib, line 1512
void setDestinationEP() { // library marker kkossev.commonLib, line 1513
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1514
    if (ep != null && ep != 'F2') { state.destinationEP = ep ; logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" } // library marker kkossev.commonLib, line 1515
    else { logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" ; state.destinationEP = '01' }   // fallback EP // library marker kkossev.commonLib, line 1516
} // library marker kkossev.commonLib, line 1517

void logDebug(final String msg) { if (settings?.logEnable)   { log.debug "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1519
void logInfo(final String msg)  { if (settings?.txtEnable)   { log.info  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1520
void logWarn(final String msg)  { if (settings?.logEnable)   { log.warn  "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1521
void logTrace(final String msg) { if (settings?.traceEnable) { log.trace "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1522
void logError(final String msg) { if (settings?.txtEnable)   { log.error "${device.displayName} " + msg } } // library marker kkossev.commonLib, line 1523

// _DEBUG mode only // library marker kkossev.commonLib, line 1525
void getAllProperties() { // library marker kkossev.commonLib, line 1526
    log.trace 'Properties:' ; device.properties.each { it -> log.debug it } // library marker kkossev.commonLib, line 1527
    log.trace 'Settings:' ;  settings.each { it -> log.debug "${it.key} =  ${it.value}" }    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1528
} // library marker kkossev.commonLib, line 1529

// delete all Preferences // library marker kkossev.commonLib, line 1531
void deleteAllSettings() { // library marker kkossev.commonLib, line 1532
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 1533
    settings.each { it -> preferencesDeleted += "${it.key} (${it.value}), " ; device.removeSetting("${it.key}") } // library marker kkossev.commonLib, line 1534
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 1535
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1536
} // library marker kkossev.commonLib, line 1537

// delete all attributes // library marker kkossev.commonLib, line 1539
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1540
    String attributesDeleted = '' // library marker kkossev.commonLib, line 1541
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 1542
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1543
} // library marker kkossev.commonLib, line 1544

// delete all State Variables // library marker kkossev.commonLib, line 1546
void deleteAllStates() { // library marker kkossev.commonLib, line 1547
    String stateDeleted = '' // library marker kkossev.commonLib, line 1548
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 1549
    state.clear() // library marker kkossev.commonLib, line 1550
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1551
} // library marker kkossev.commonLib, line 1552

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1554
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1555
} // library marker kkossev.commonLib, line 1556

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1558
    getChildDevices().each { child -> log.info "${device.displayName} Deleting ${child.deviceNetworkId}" ; deleteChildDevice(child.deviceNetworkId) } // library marker kkossev.commonLib, line 1559
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1560
} // library marker kkossev.commonLib, line 1561

void testParse(String par) { // library marker kkossev.commonLib, line 1563
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1564
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1565
    log.warn "testParse - <b>START</b> (${par})" // library marker kkossev.commonLib, line 1566
    parse(par) // library marker kkossev.commonLib, line 1567
    log.warn "testParse -   <b>END</b> (${par})" // library marker kkossev.commonLib, line 1568
    log.trace '------------------------------------------------------' // library marker kkossev.commonLib, line 1569
} // library marker kkossev.commonLib, line 1570

Object testJob() { // library marker kkossev.commonLib, line 1572
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1573
} // library marker kkossev.commonLib, line 1574

/** // library marker kkossev.commonLib, line 1576
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1577
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 1578
 */ // library marker kkossev.commonLib, line 1579
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 1580
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 1581
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 1582
    final Random rnd = new Random() // library marker kkossev.commonLib, line 1583
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 1584
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 1585
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 1586
    String cron // library marker kkossev.commonLib, line 1587
    if (timeInSeconds < 60) { cron = "*/$timeInSeconds * * * * ? *" } // library marker kkossev.commonLib, line 1588
    else { // library marker kkossev.commonLib, line 1589
        if (minutes < 60) {   cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" } // library marker kkossev.commonLib, line 1590
        else {                cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"  } // library marker kkossev.commonLib, line 1591
    } // library marker kkossev.commonLib, line 1592
    return cron // library marker kkossev.commonLib, line 1593
} // library marker kkossev.commonLib, line 1594

// credits @thebearmay // library marker kkossev.commonLib, line 1596
String formatUptime() { // library marker kkossev.commonLib, line 1597
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 1598
} // library marker kkossev.commonLib, line 1599

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 1601
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 1602
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 1603
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 1604
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 1605
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 1606
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1607
} // library marker kkossev.commonLib, line 1608

boolean isTuya() { // library marker kkossev.commonLib, line 1610
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 1611
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 1612
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 1613
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 1614
    return ((model?.startsWith('TS') && manufacturer?.startsWith('_T')) || model == 'HOBEIAN') ? true : false // library marker kkossev.commonLib, line 1615
} // library marker kkossev.commonLib, line 1616

void updateTuyaVersion() { // library marker kkossev.commonLib, line 1618
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 1619
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1620
    if (application != null) { // library marker kkossev.commonLib, line 1621
        Integer ver // library marker kkossev.commonLib, line 1622
        try { ver = zigbee.convertHexToInt(application) } // library marker kkossev.commonLib, line 1623
        catch (e) { logWarn "exception caught while converting application version ${application} to tuyaVersion"; return } // library marker kkossev.commonLib, line 1624
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 1625
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 1626
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 1627
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 1628
        } // library marker kkossev.commonLib, line 1629
    } // library marker kkossev.commonLib, line 1630
} // library marker kkossev.commonLib, line 1631

boolean isAqara() { return device.getDataValue('model')?.startsWith('lumi') ?: false } // library marker kkossev.commonLib, line 1633

void updateAqaraVersion() { // library marker kkossev.commonLib, line 1635
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 1636
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 1637
    if (application != null) { // library marker kkossev.commonLib, line 1638
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 1639
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 1640
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 1641
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 1642
        } // library marker kkossev.commonLib, line 1643
    } // library marker kkossev.commonLib, line 1644
} // library marker kkossev.commonLib, line 1645

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 1647
    try { // library marker kkossev.commonLib, line 1648
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 1649
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 1650
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 1651
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1652
    } catch (e) { // library marker kkossev.commonLib, line 1653
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 1654
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 1655
    } // library marker kkossev.commonLib, line 1656
} // library marker kkossev.commonLib, line 1657

Long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 1659
    try { // library marker kkossev.commonLib, line 1660
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 1661
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 1662
        return date.getTime() // library marker kkossev.commonLib, line 1663
    } catch (e) { // library marker kkossev.commonLib, line 1664
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 1665
        return now() // library marker kkossev.commonLib, line 1666
    } // library marker kkossev.commonLib, line 1667
} // library marker kkossev.commonLib, line 1668

static String timeToHMS(final int time) { // library marker kkossev.commonLib, line 1670
    int hours = (time / 3600) as int // library marker kkossev.commonLib, line 1671
    int minutes = ((time % 3600) / 60) as int // library marker kkossev.commonLib, line 1672
    int seconds = time % 60 // library marker kkossev.commonLib, line 1673
    return "${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 1674
} // library marker kkossev.commonLib, line 1675

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (176) kkossev.onOffLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.onOffLib, line 1
library( // library marker kkossev.onOffLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee OnOff Cluster Library', name: 'onOffLib', namespace: 'kkossev', // library marker kkossev.onOffLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/onOffLib.groovy', documentationLink: '', // library marker kkossev.onOffLib, line 4
    version: '3.2.4' // library marker kkossev.onOffLib, line 5
) // library marker kkossev.onOffLib, line 6
/* // library marker kkossev.onOffLib, line 7
 *  Zigbee OnOff Cluster Library // library marker kkossev.onOffLib, line 8
 * // library marker kkossev.onOffLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.onOffLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.onOffLib, line 11
 * // library marker kkossev.onOffLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.onOffLib, line 13
 * // library marker kkossev.onOffLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.onOffLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.onOffLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.onOffLib, line 17
 * // library marker kkossev.onOffLib, line 18
 * ver. 3.2.0  2024-06-04 kkossev  - commonLib 3.2.1 allignment; if isRefresh then sendEvent with isStateChange = true // library marker kkossev.onOffLib, line 19
 * ver. 3.2.1  2024-06-07 kkossev  - the advanced options are excpluded for DEVICE_TYPE Thermostat // library marker kkossev.onOffLib, line 20
 * ver. 3.2.2  2024-06-29 kkossev  - added on/off control for Tuya device profiles with 'switch' dp; // library marker kkossev.onOffLib, line 21
 * ver. 3.2.3  2025-12-06 kkossev  - fixed a bug in off() and on() methods where clearIsDigital() was called too early // library marker kkossev.onOffLib, line 22
 * ver. 3.2.4  2026-08-23 kkossev  - bug fix: quoted the respondsTo('getDEVICE') argument in on() and off(); the bare identifier threw a NullPointerException in drivers without deviceProfileLib // library marker kkossev.onOffLib, line 23
 * // library marker kkossev.onOffLib, line 24
 *                                   TODO: // library marker kkossev.onOffLib, line 25
*/ // library marker kkossev.onOffLib, line 26

static String onOffLibVersion()   { '3.2.4' } // library marker kkossev.onOffLib, line 28
static String onOffLibStamp() { '2026/08/23 4:27 PM' } // library marker kkossev.onOffLib, line 29

@Field static final Boolean _THREE_STATE = true // library marker kkossev.onOffLib, line 31

metadata { // library marker kkossev.onOffLib, line 33
    capability 'Actuator' // library marker kkossev.onOffLib, line 34
    capability 'Switch' // library marker kkossev.onOffLib, line 35
    if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 36
        attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.onOffLib, line 37
    } // library marker kkossev.onOffLib, line 38
    // no commands // library marker kkossev.onOffLib, line 39
    preferences { // library marker kkossev.onOffLib, line 40
        if (settings?.advancedOptions == true && device != null && !(DEVICE_TYPE in ['Device', 'Thermostat'])) { // library marker kkossev.onOffLib, line 41
            input(name: 'ignoreDuplicated', type: 'bool', title: '<b>Ignore Duplicated Switch Events</b>', description: 'Some switches and plugs send periodically the switch status as a heart-beet ', defaultValue: true) // library marker kkossev.onOffLib, line 42
            input(name: 'alwaysOn', type: 'bool', title: '<b>Always On</b>', description: 'Disable switching off plugs and switches that must stay always On', defaultValue: false) // library marker kkossev.onOffLib, line 43
            if (_THREE_STATE == true) { // library marker kkossev.onOffLib, line 44
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: 'Experimental multi-state switch events', defaultValue: false // library marker kkossev.onOffLib, line 45
            } // library marker kkossev.onOffLib, line 46
        } // library marker kkossev.onOffLib, line 47
    } // library marker kkossev.onOffLib, line 48
} // library marker kkossev.onOffLib, line 49

@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.onOffLib, line 51
    defaultValue: 0, options: [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.onOffLib, line 52
] // library marker kkossev.onOffLib, line 53

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.onOffLib, line 55
    '0': 'switch off', '1': 'switch on', '2': 'switch last state' // library marker kkossev.onOffLib, line 56
] // library marker kkossev.onOffLib, line 57

@Field static final Map switchTypeOptions = [ // library marker kkossev.onOffLib, line 59
    '0': 'toggle', '1': 'state', '2': 'momentary' // library marker kkossev.onOffLib, line 60
] // library marker kkossev.onOffLib, line 61

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.onOffLib, line 63

/* // library marker kkossev.onOffLib, line 65
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 66
 * on/off cluster            0x0006     TODO - move to a library !!!!!!!!!!!!!!! // library marker kkossev.onOffLib, line 67
 * ----------------------------------------------------------------------------- // library marker kkossev.onOffLib, line 68
*/ // library marker kkossev.onOffLib, line 69
void standardParseOnOffCluster(final Map descMap) { // library marker kkossev.onOffLib, line 70
    /* // library marker kkossev.onOffLib, line 71
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.onOffLib, line 72
        customParseOnOffCluster(descMap) // library marker kkossev.onOffLib, line 73
    } // library marker kkossev.onOffLib, line 74
    else */ // library marker kkossev.onOffLib, line 75
    if (descMap.attrId == '0000') { // library marker kkossev.onOffLib, line 76
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.onOffLib, line 77
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.onOffLib, line 78
        sendSwitchEvent(rawValue) // library marker kkossev.onOffLib, line 79
    } // library marker kkossev.onOffLib, line 80
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.onOffLib, line 81
        parseOnOffAttributes(descMap) // library marker kkossev.onOffLib, line 82
    } // library marker kkossev.onOffLib, line 83
    else { // library marker kkossev.onOffLib, line 84
        if (descMap.attrId != null) { logWarn "standardParseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.onOffLib, line 85
        else { logDebug "standardParseOnOffCluster: skipped processing OnOff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.onOffLib, line 86
    } // library marker kkossev.onOffLib, line 87
} // library marker kkossev.onOffLib, line 88

void toggleX() { // library marker kkossev.onOffLib, line 90
    String descriptionText = 'central button switch is ' // library marker kkossev.onOffLib, line 91
    String state = '' // library marker kkossev.onOffLib, line 92
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.onOffLib, line 93
        state = 'on' // library marker kkossev.onOffLib, line 94
    } // library marker kkossev.onOffLib, line 95
    else { // library marker kkossev.onOffLib, line 96
        state = 'off' // library marker kkossev.onOffLib, line 97
    } // library marker kkossev.onOffLib, line 98
    descriptionText += state // library marker kkossev.onOffLib, line 99
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.onOffLib, line 100
    logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 101
} // library marker kkossev.onOffLib, line 102

void off() { // library marker kkossev.onOffLib, line 104
    if (this.respondsTo('customOff')) { customOff() ; return  } // library marker kkossev.onOffLib, line 105
    if ((settings?.alwaysOn ?: false) == true) { logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" ; return } // library marker kkossev.onOffLib, line 106
    List<String> cmds = [] // library marker kkossev.onOffLib, line 107
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 108
    if (this.respondsTo('getDEVICE')) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 109
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 110
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  0  : 1 // library marker kkossev.onOffLib, line 111
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 112
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 113
            logTrace "off() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 114
        } // library marker kkossev.onOffLib, line 115
    } // library marker kkossev.onOffLib, line 116
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 117
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.onOffLib, line 118
    } // library marker kkossev.onOffLib, line 119

    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 121
    logDebug "off() currentState=${currentState}" // library marker kkossev.onOffLib, line 122
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 123
        if (currentState == 'off') { // library marker kkossev.onOffLib, line 124
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 125
        } // library marker kkossev.onOffLib, line 126
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.onOffLib, line 127
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 128
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 129
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 130
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 131
    } // library marker kkossev.onOffLib, line 132
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 133
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 134
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 135
} // library marker kkossev.onOffLib, line 136

void on() { // library marker kkossev.onOffLib, line 138
    if (this.respondsTo('customOn')) { customOn() ; return } // library marker kkossev.onOffLib, line 139
    List<String> cmds = [] // library marker kkossev.onOffLib, line 140
    // added 06/29/2024 - control Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 141
    if (this.respondsTo('getDEVICE')) {   // defined in deviceProfileLib // library marker kkossev.onOffLib, line 142
        Map switchMap = getAttributesMap('switch') // library marker kkossev.onOffLib, line 143
        int onOffValue = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  1  : 0 // library marker kkossev.onOffLib, line 144
        if (switchMap != null && switchMap != [:]) { // library marker kkossev.onOffLib, line 145
            cmds = sendTuyaParameter(switchMap, 'switch', onOffValue) // library marker kkossev.onOffLib, line 146
            logTrace "on() Tuya cmds=${cmds}" // library marker kkossev.onOffLib, line 147
        } // library marker kkossev.onOffLib, line 148
    } // library marker kkossev.onOffLib, line 149
    if (cmds.size() == 0) { // if not Tuya 0xEF00 switch // library marker kkossev.onOffLib, line 150
        cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.onOffLib, line 151
    } // library marker kkossev.onOffLib, line 152
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.onOffLib, line 153
    logDebug "on() currentState=${currentState}" // library marker kkossev.onOffLib, line 154
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.onOffLib, line 155
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.onOffLib, line 156
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.onOffLib, line 157
        } // library marker kkossev.onOffLib, line 158
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.onOffLib, line 159
        String descriptionText = "${value}" // library marker kkossev.onOffLib, line 160
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.onOffLib, line 161
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.onOffLib, line 162
        logInfo "${descriptionText}" // library marker kkossev.onOffLib, line 163
    } // library marker kkossev.onOffLib, line 164
    state.states['isDigital'] = true // library marker kkossev.onOffLib, line 165
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.onOffLib, line 166
    sendZigbeeCommands(cmds) // library marker kkossev.onOffLib, line 167
} // library marker kkossev.onOffLib, line 168

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.onOffLib, line 170
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.onOffLib, line 171
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.onOffLib, line 172
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.onOffLib, line 173
    } // library marker kkossev.onOffLib, line 174
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.onOffLib, line 175
    Map map = [:] // library marker kkossev.onOffLib, line 176
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.onOffLib, line 177
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.onOffLib, line 178
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.onOffLib, line 179
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false)) && !isRefresh) { // library marker kkossev.onOffLib, line 180
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.onOffLib, line 181
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 182
        return // library marker kkossev.onOffLib, line 183
    } // library marker kkossev.onOffLib, line 184
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.onOffLib, line 185
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.onOffLib, line 186
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.onOffLib, line 187
    if (lastSwitch != value) { // library marker kkossev.onOffLib, line 188
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.onOffLib, line 189
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 190
        state.states['lastSwitch'] = value // library marker kkossev.onOffLib, line 191
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 192
    } else { // library marker kkossev.onOffLib, line 193
        state.states['debounce'] = true // library marker kkossev.onOffLib, line 194
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.onOffLib, line 195
    } // library marker kkossev.onOffLib, line 196
    map.name = 'switch' // library marker kkossev.onOffLib, line 197
    map.value = value // library marker kkossev.onOffLib, line 198
    if (isRefresh) { // library marker kkossev.onOffLib, line 199
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.onOffLib, line 200
        map.isStateChange = true // library marker kkossev.onOffLib, line 201
    } else { // library marker kkossev.onOffLib, line 202
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.onOffLib, line 203
    } // library marker kkossev.onOffLib, line 204
    logInfo "${map.descriptionText}" // library marker kkossev.onOffLib, line 205
    sendEvent(map) // library marker kkossev.onOffLib, line 206
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.onOffLib, line 207
        customSwitchEventPostProcesing(map) // library marker kkossev.onOffLib, line 208
    } // library marker kkossev.onOffLib, line 209
} // library marker kkossev.onOffLib, line 210

void parseOnOffAttributes(final Map it) { // library marker kkossev.onOffLib, line 212
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 213
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.onOffLib, line 214
    String mode // library marker kkossev.onOffLib, line 215
    String attrName // library marker kkossev.onOffLib, line 216
    if (it.value == null) { // library marker kkossev.onOffLib, line 217
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.onOffLib, line 218
        return // library marker kkossev.onOffLib, line 219
    } // library marker kkossev.onOffLib, line 220
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.onOffLib, line 221
    switch (it.attrId) { // library marker kkossev.onOffLib, line 222
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.onOffLib, line 223
            attrName = 'Global Scene Control' // library marker kkossev.onOffLib, line 224
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.onOffLib, line 225
            break // library marker kkossev.onOffLib, line 226
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.onOffLib, line 227
            attrName = 'On Time' // library marker kkossev.onOffLib, line 228
            mode = value // library marker kkossev.onOffLib, line 229
            break // library marker kkossev.onOffLib, line 230
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.onOffLib, line 231
            attrName = 'Off Wait Time' // library marker kkossev.onOffLib, line 232
            mode = value // library marker kkossev.onOffLib, line 233
            break // library marker kkossev.onOffLib, line 234
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.onOffLib, line 235
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 236
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.onOffLib, line 237
            break // library marker kkossev.onOffLib, line 238
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.onOffLib, line 239
            attrName = 'Child Lock' // library marker kkossev.onOffLib, line 240
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.onOffLib, line 241
            break // library marker kkossev.onOffLib, line 242
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.onOffLib, line 243
            attrName = 'LED mode' // library marker kkossev.onOffLib, line 244
            if (isCircuitBreaker()) { // library marker kkossev.onOffLib, line 245
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.onOffLib, line 246
            } // library marker kkossev.onOffLib, line 247
            else { // library marker kkossev.onOffLib, line 248
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.onOffLib, line 249
            } // library marker kkossev.onOffLib, line 250
            break // library marker kkossev.onOffLib, line 251
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.onOffLib, line 252
            attrName = 'Power On State' // library marker kkossev.onOffLib, line 253
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.onOffLib, line 254
            break // library marker kkossev.onOffLib, line 255
        case '8003' : //  Over current alarm // library marker kkossev.onOffLib, line 256
            attrName = 'Over current alarm' // library marker kkossev.onOffLib, line 257
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.onOffLib, line 258
            break // library marker kkossev.onOffLib, line 259
        default : // library marker kkossev.onOffLib, line 260
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.onOffLib, line 261
            return // library marker kkossev.onOffLib, line 262
    } // library marker kkossev.onOffLib, line 263
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.onOffLib, line 264
} // library marker kkossev.onOffLib, line 265

List<String> onOffRefresh() { // library marker kkossev.onOffLib, line 267
    logDebug 'onOffRefresh()' // library marker kkossev.onOffLib, line 268
    List<String> cmds = zigbee.readAttribute(0x0006, 0x0000, [:], delay = 100) // library marker kkossev.onOffLib, line 269
    return cmds // library marker kkossev.onOffLib, line 270
} // library marker kkossev.onOffLib, line 271

void onOfInitializeVars( boolean fullInit = false ) { // library marker kkossev.onOffLib, line 273
    logDebug "onOfInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.onOffLib, line 274
    if (fullInit || settings?.ignoreDuplicated == null) { device.updateSetting('ignoreDuplicated', true) } // library marker kkossev.onOffLib, line 275
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.onOffLib, line 276
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.onOffLib, line 277
} // library marker kkossev.onOffLib, line 278

// ~~~~~ end include (176) kkossev.onOffLib ~~~~~

// ~~~~~ start include (165) kkossev.xiaomiLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, PublicMethodsBeforeNonPublicMethods, UnnecessaryGetter, UnnecessaryPublicModifier */ // library marker kkossev.xiaomiLib, line 1
library( // library marker kkossev.xiaomiLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Xiaomi Library', name: 'xiaomiLib', namespace: 'kkossev', importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy', documentationLink: '', // library marker kkossev.xiaomiLib, line 3
    version: '3.3.0' // library marker kkossev.xiaomiLib, line 4
) // library marker kkossev.xiaomiLib, line 5
/* // library marker kkossev.xiaomiLib, line 6
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 7
 * // library marker kkossev.xiaomiLib, line 8
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 9
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 10
 * // library marker kkossev.xiaomiLib, line 11
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 12
 * // library marker kkossev.xiaomiLib, line 13
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 14
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 15
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 16
 * // library marker kkossev.xiaomiLib, line 17
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 18
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 19
 * ver. 1.0.2  2024-04-06 kkossev  - (dev. branch) Groovy linting; aqaraCube specific code; // library marker kkossev.xiaomiLib, line 20
 * ver. 1.1.0  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.0 alignmment // library marker kkossev.xiaomiLib, line 21
 * ver. 3.2.2  2024-06-01 kkossev  - (dev. branch) comonLib 3.2.2 alignmment // library marker kkossev.xiaomiLib, line 22
 * ver. 3.3.0  2024-06-23 kkossev  - comonLib 3.3.0 alignmment; added parseXiaomiClusterSingeTag() method // library marker kkossev.xiaomiLib, line 23
 * // library marker kkossev.xiaomiLib, line 24
 *                                   TODO: remove the DEVICE_TYPE dependencies for Bulb, Thermostat, AqaraCube, FP1, TRV_OLD // library marker kkossev.xiaomiLib, line 25
 *                                   TODO: remove the isAqaraXXX  dependencies !! // library marker kkossev.xiaomiLib, line 26
*/ // library marker kkossev.xiaomiLib, line 27

static String xiaomiLibVersion()   { '3.3.0' } // library marker kkossev.xiaomiLib, line 29
static String xiaomiLibStamp() { '2024/06/23 9:36 AM' } // library marker kkossev.xiaomiLib, line 30

boolean isAqaraTVOC_Lib()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 32
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.xiaomiLib, line 33
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.xiaomiLib, line 34
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.xiaomiLib, line 35
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.xiaomiLib, line 36

// no metadata for this library! // library marker kkossev.xiaomiLib, line 38

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 40

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 42
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 43
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 44
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 45
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 46
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 47
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 48
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 49
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 50
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 51
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 52
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 53
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 54
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 55
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 56
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 57

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 59
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 60
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 61
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 62
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 63
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 64
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 65

// called from parseXiaomiCluster() in the main code, if no customParse is defined // library marker kkossev.xiaomiLib, line 67
// TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 68
// TODO - refactor for Thermostat and Bulb specific code // library marker kkossev.xiaomiLib, line 69
void standardParseXiaomiFCC0Cluster(final Map descMap) { // library marker kkossev.xiaomiLib, line 70
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 71
        logTrace "standardParseXiaomiFCC0Cluster: zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 72
    } // library marker kkossev.xiaomiLib, line 73
    if (DEVICE_TYPE in  ['Thermostat']) { // library marker kkossev.xiaomiLib, line 74
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 75
        return // library marker kkossev.xiaomiLib, line 76
    } // library marker kkossev.xiaomiLib, line 77
    if (DEVICE_TYPE in  ['Bulb']) { // library marker kkossev.xiaomiLib, line 78
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 79
        return // library marker kkossev.xiaomiLib, line 80
    } // library marker kkossev.xiaomiLib, line 81
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 82
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 83
    final String funcName = 'standardParseXiaomiFCC0Cluster' // library marker kkossev.xiaomiLib, line 84
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 85
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 86
            if (DEVICE_TYPE in  ['AqaraCube']) { logDebug "standardParseXiaomiFCC0Cluster: AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 87
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 88
            break // library marker kkossev.xiaomiLib, line 89
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 90
            logWarn "${funcName}: unknown attribute - resetting?" // library marker kkossev.xiaomiLib, line 91
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
                log.debug "${funcName}: xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 106
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
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 130
            break // library marker kkossev.xiaomiLib, line 131
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 132
            if (DEVICE_TYPE in  ['AqaraCube']) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 133
            else { logDebug "${funcName}: unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 134
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
            log.warn "${funcName}: zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 155
            break // library marker kkossev.xiaomiLib, line 156
    } // library marker kkossev.xiaomiLib, line 157
} // library marker kkossev.xiaomiLib, line 158

// cluster 0xFCC0 attribute  0x00F7 is sent as a keep-alive beakon every 55 minutes // library marker kkossev.xiaomiLib, line 160
public void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 161
    final String funcName = 'parseXiaomiClusterTags' // library marker kkossev.xiaomiLib, line 162
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 163
        parseXiaomiClusterSingeTag(tag, value) // library marker kkossev.xiaomiLib, line 164
    } // library marker kkossev.xiaomiLib, line 165
} // library marker kkossev.xiaomiLib, line 166

public void parseXiaomiClusterSingeTag(final Integer tag, final Object value) { // library marker kkossev.xiaomiLib, line 168
    final String funcName = 'parseXiaomiClusterSingeTag' // library marker kkossev.xiaomiLib, line 169
    switch (tag) { // library marker kkossev.xiaomiLib, line 170
        case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 171
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} battery voltage is ${value / 1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 172
            break // library marker kkossev.xiaomiLib, line 173
        case 0x03: // library marker kkossev.xiaomiLib, line 174
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 175
            break // library marker kkossev.xiaomiLib, line 176
        case 0x05: // library marker kkossev.xiaomiLib, line 177
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 178
            break // library marker kkossev.xiaomiLib, line 179
        case 0x06: // library marker kkossev.xiaomiLib, line 180
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 181
            break // library marker kkossev.xiaomiLib, line 182
        case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 183
            final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 184
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 185
            device.updateDataValue('aqaraVersion', swBuild) // library marker kkossev.xiaomiLib, line 186
            break // library marker kkossev.xiaomiLib, line 187
        case 0x0a: // library marker kkossev.xiaomiLib, line 188
            String nwk = intToHexStr(value as Integer, 2) // library marker kkossev.xiaomiLib, line 189
            if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 190
            String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 191
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 192
            if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 193
                logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 194
                state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 195
                state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 196
            } // library marker kkossev.xiaomiLib, line 197
            break // library marker kkossev.xiaomiLib, line 198
        case 0x0b: // library marker kkossev.xiaomiLib, line 199
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 200
            break // library marker kkossev.xiaomiLib, line 201
        case 0x64: // library marker kkossev.xiaomiLib, line 202
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} temperature is ${value / 100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 203
            // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 204
            break // library marker kkossev.xiaomiLib, line 205
        case 0x65: // library marker kkossev.xiaomiLib, line 206
            if (isAqaraFP1()) { logDebug "${funcName} PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 207
            else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value / 100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 208
            break // library marker kkossev.xiaomiLib, line 209
        case 0x66: // library marker kkossev.xiaomiLib, line 210
            if (isAqaraFP1()) { logDebug "${funcName} SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
            else if (isAqaraTVOC_Lib()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 212
            else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" } // library marker kkossev.xiaomiLib, line 213
            break // library marker kkossev.xiaomiLib, line 214
        case 0x67: // library marker kkossev.xiaomiLib, line 215
            if (isAqaraFP1()) { logDebug "${funcName} DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 216
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC: // library marker kkossev.xiaomiLib, line 217
            // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 218
            break // library marker kkossev.xiaomiLib, line 219
        case 0x69: // library marker kkossev.xiaomiLib, line 220
            if (isAqaraFP1()) { logDebug "${funcName} TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 221
            else              { logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 222
            break // library marker kkossev.xiaomiLib, line 223
        case 0x6a: // library marker kkossev.xiaomiLib, line 224
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 225
            else              { logDebug "${funcName} MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 226
            break // library marker kkossev.xiaomiLib, line 227
        case 0x6b: // library marker kkossev.xiaomiLib, line 228
            if (isAqaraFP1()) { logDebug "${funcName} FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 229
            else              { logDebug "${funcName} MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 230
            break // library marker kkossev.xiaomiLib, line 231
        case 0x95: // library marker kkossev.xiaomiLib, line 232
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 233
            break // library marker kkossev.xiaomiLib, line 234
        case 0x96: // library marker kkossev.xiaomiLib, line 235
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 236
            break // library marker kkossev.xiaomiLib, line 237
        case 0x97: // library marker kkossev.xiaomiLib, line 238
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 239
            break // library marker kkossev.xiaomiLib, line 240
        case 0x98: // library marker kkossev.xiaomiLib, line 241
            logDebug "${funcName}: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 242
            break // library marker kkossev.xiaomiLib, line 243
        case 0x9b: // library marker kkossev.xiaomiLib, line 244
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 245
                logDebug "${funcName} Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})" // library marker kkossev.xiaomiLib, line 246
                sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 247
            } // library marker kkossev.xiaomiLib, line 248
            else { logDebug "${funcName} CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 249
            break // library marker kkossev.xiaomiLib, line 250
        default: // library marker kkossev.xiaomiLib, line 251
            logDebug "${funcName} unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 252
    } // library marker kkossev.xiaomiLib, line 253
} // library marker kkossev.xiaomiLib, line 254

/** // library marker kkossev.xiaomiLib, line 256
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 257
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 258
 */ // library marker kkossev.xiaomiLib, line 259
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 260
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 261
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 262
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 263
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 264
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 265
    } // library marker kkossev.xiaomiLib, line 266
    return bigInt // library marker kkossev.xiaomiLib, line 267
} // library marker kkossev.xiaomiLib, line 268

/** // library marker kkossev.xiaomiLib, line 270
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 271
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 272
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 273
 */ // library marker kkossev.xiaomiLib, line 274
private Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 275
    try { // library marker kkossev.xiaomiLib, line 276
        final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 277
        final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 278
        new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 279
            while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 280
                int tag = stream.read() // library marker kkossev.xiaomiLib, line 281
                int dataType = stream.read() // library marker kkossev.xiaomiLib, line 282
                Object value // library marker kkossev.xiaomiLib, line 283
                if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 284
                    int length = stream.read() // library marker kkossev.xiaomiLib, line 285
                    byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 286
                    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 287
                    value = new String(byteArr) // library marker kkossev.xiaomiLib, line 288
                } else { // library marker kkossev.xiaomiLib, line 289
                    int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 290
                    value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 291
                } // library marker kkossev.xiaomiLib, line 292
                results[tag] = value // library marker kkossev.xiaomiLib, line 293
            } // library marker kkossev.xiaomiLib, line 294
        } // library marker kkossev.xiaomiLib, line 295
        return results // library marker kkossev.xiaomiLib, line 296
    } // library marker kkossev.xiaomiLib, line 297
    catch (e) { // library marker kkossev.xiaomiLib, line 298
        if (settings.logEnable) { "${device.displayName} decodeXiaomiTags: ${e}" } // library marker kkossev.xiaomiLib, line 299
        return [:] // library marker kkossev.xiaomiLib, line 300
    } // library marker kkossev.xiaomiLib, line 301
} // library marker kkossev.xiaomiLib, line 302

List<String> refreshXiaomi() { // library marker kkossev.xiaomiLib, line 304
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 305
    if (cmds == []) { cmds = ['delay 299'] } // library marker kkossev.xiaomiLib, line 306
    return cmds // library marker kkossev.xiaomiLib, line 307
} // library marker kkossev.xiaomiLib, line 308

List<String> configureXiaomi() { // library marker kkossev.xiaomiLib, line 310
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 311
    logDebug "configureXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 312
    if (cmds == []) { cmds = ['delay 299'] }    // no , // library marker kkossev.xiaomiLib, line 313
    return cmds // library marker kkossev.xiaomiLib, line 314
} // library marker kkossev.xiaomiLib, line 315

List<String> initializeXiaomi() { // library marker kkossev.xiaomiLib, line 317
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 318
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 319
    if (cmds == []) { cmds = ['delay 299',] } // library marker kkossev.xiaomiLib, line 320
    return cmds // library marker kkossev.xiaomiLib, line 321
} // library marker kkossev.xiaomiLib, line 322

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 324
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 325
} // library marker kkossev.xiaomiLib, line 326

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 328
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 329
} // library marker kkossev.xiaomiLib, line 330

List<String> standardAqaraBlackMagic() { // library marker kkossev.xiaomiLib, line 332
    return [] // library marker kkossev.xiaomiLib, line 333
    ///////////////////////////////////////// // library marker kkossev.xiaomiLib, line 334
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 335
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.xiaomiLib, line 336
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.xiaomiLib, line 337
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 338
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.xiaomiLib, line 339
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.xiaomiLib, line 340
        if (isAqaraTVOC_OLD()) { // library marker kkossev.xiaomiLib, line 341
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.xiaomiLib, line 342
        } // library marker kkossev.xiaomiLib, line 343
        logDebug 'standardAqaraBlackMagic()' // library marker kkossev.xiaomiLib, line 344
    } // library marker kkossev.xiaomiLib, line 345
    return cmds // library marker kkossev.xiaomiLib, line 346
} // library marker kkossev.xiaomiLib, line 347

// ~~~~~ end include (165) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (167) kkossev.buttonLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.buttonLib, line 1
library( // library marker kkossev.buttonLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Button Library', name: 'buttonLib', namespace: 'kkossev', // library marker kkossev.buttonLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/buttonLib.groovy', documentationLink: '', // library marker kkossev.buttonLib, line 4
    version: '3.2.0' // library marker kkossev.buttonLib, line 5
) // library marker kkossev.buttonLib, line 6
/* // library marker kkossev.buttonLib, line 7
 *  Zigbee Button Library // library marker kkossev.buttonLib, line 8
 * // library marker kkossev.buttonLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.buttonLib, line 10
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.buttonLib, line 11
 * // library marker kkossev.buttonLib, line 12
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.buttonLib, line 13
 * // library marker kkossev.buttonLib, line 14
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.buttonLib, line 15
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.buttonLib, line 16
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.buttonLib, line 17
 * // library marker kkossev.buttonLib, line 18
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.buttonLib, line 19
 * ver. 3.2.0  2024-05-24 kkossev  - commonLib 3.2.0 allignment; added capability 'PushableButton' and 'Momentary' // library marker kkossev.buttonLib, line 20
 * // library marker kkossev.buttonLib, line 21
 *                                   TODO: // library marker kkossev.buttonLib, line 22
*/ // library marker kkossev.buttonLib, line 23

static String buttonLibVersion()   { '3.2.0' } // library marker kkossev.buttonLib, line 25
static String buttonLibStamp() { '2024/05/24 12:48 PM' } // library marker kkossev.buttonLib, line 26

metadata { // library marker kkossev.buttonLib, line 28
    capability 'PushableButton' // library marker kkossev.buttonLib, line 29
    capability 'Momentary' // library marker kkossev.buttonLib, line 30
    // the other capabilities must be declared in the custom driver, if applicable for the particular device! // library marker kkossev.buttonLib, line 31
    // the custom driver must allso call sendNumberOfButtonsEvent() and sendSupportedButtonValuesEvent()! // library marker kkossev.buttonLib, line 32
    // capability 'DoubleTapableButton' // library marker kkossev.buttonLib, line 33
    // capability 'HoldableButton' // library marker kkossev.buttonLib, line 34
    // capability 'ReleasableButton' // library marker kkossev.buttonLib, line 35

    // no attributes // library marker kkossev.buttonLib, line 37
    // no commands // library marker kkossev.buttonLib, line 38
    preferences { // library marker kkossev.buttonLib, line 39
        // no prefrences // library marker kkossev.buttonLib, line 40
    } // library marker kkossev.buttonLib, line 41
} // library marker kkossev.buttonLib, line 42

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.buttonLib, line 44
    if (buttonState != 'unknown' && buttonNumber != 0) { // library marker kkossev.buttonLib, line 45
        String descriptionText = "button $buttonNumber was $buttonState" // library marker kkossev.buttonLib, line 46
        if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.buttonLib, line 47
        Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.buttonLib, line 48
        logInfo "$descriptionText" // library marker kkossev.buttonLib, line 49
        sendEvent(event) // library marker kkossev.buttonLib, line 50
    } // library marker kkossev.buttonLib, line 51
    else { // library marker kkossev.buttonLib, line 52
        logWarn "sendButtonEvent: UNHANDLED event for button ${buttonNumber}, buttonState=${buttonState}" // library marker kkossev.buttonLib, line 53
    } // library marker kkossev.buttonLib, line 54
} // library marker kkossev.buttonLib, line 55

void push() {                // Momentary capability // library marker kkossev.buttonLib, line 57
    logDebug 'push momentary' // library marker kkossev.buttonLib, line 58
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.buttonLib, line 59
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.buttonLib, line 60
} // library marker kkossev.buttonLib, line 61

/* // library marker kkossev.buttonLib, line 63
void push(BigDecimal buttonNumber) {    //pushableButton capability // library marker kkossev.buttonLib, line 64
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 65
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 66
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 67
} // library marker kkossev.buttonLib, line 68
*/ // library marker kkossev.buttonLib, line 69

void push(Object bn) {    //pushableButton capability // library marker kkossev.buttonLib, line 71
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 72
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 73
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 74
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 75
} // library marker kkossev.buttonLib, line 76

void doubleTap(Object bn) { // library marker kkossev.buttonLib, line 78
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 79
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.buttonLib, line 80
} // library marker kkossev.buttonLib, line 81

void hold(Object bn) { // library marker kkossev.buttonLib, line 83
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 84
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.buttonLib, line 85
} // library marker kkossev.buttonLib, line 86

void release(Object bn) { // library marker kkossev.buttonLib, line 88
    Integer buttonNumber = bn.toInteger() // library marker kkossev.buttonLib, line 89
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true) // library marker kkossev.buttonLib, line 90
} // library marker kkossev.buttonLib, line 91

// must be called from the custom driver! // library marker kkossev.buttonLib, line 93
void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.buttonLib, line 94
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 95
} // library marker kkossev.buttonLib, line 96
// must be called from the custom driver! // library marker kkossev.buttonLib, line 97
void sendSupportedButtonValuesEvent(List<String> supportedValues) { // library marker kkossev.buttonLib, line 98
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 99
} // library marker kkossev.buttonLib, line 100


// ~~~~~ end include (167) kkossev.buttonLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoJavaUtilDate, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', author: 'Krassimir Kossev', category: 'zigbee', description: 'Zigbee Battery Library', name: 'batteryLib', namespace: 'kkossev', // library marker kkossev.batteryLib, line 3
    importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/refs/heads/development/Libraries/batteryLib.groovy', documentationLink: 'https://github.com/kkossev/Hubitat/wiki/libraries-batteryLib', // library marker kkossev.batteryLib, line 4
    version: '3.2.4' // library marker kkossev.batteryLib, line 5
) // library marker kkossev.batteryLib, line 6
/* // library marker kkossev.batteryLib, line 7
 *  Zigbee Battery Library // library marker kkossev.batteryLib, line 8
 * // library marker kkossev.batteryLib, line 9
 *  Licensed Virtual the Apache License, Version 2.0 // library marker kkossev.batteryLib, line 10
 * // library marker kkossev.batteryLib, line 11
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 12
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 13
 * ver. 3.0.2  2024-04-14 kkossev  - batteryPercentage bug fix (was x2); added bVoltCtr; added battertRefresh // library marker kkossev.batteryLib, line 14
 * ver. 3.2.0  2024-05-21 kkossev  - commonLib 3.2.0 allignment; added lastBattery; added handleTuyaBatteryLevel // library marker kkossev.batteryLib, line 15
 * ver. 3.2.1  2024-07-06 kkossev  - added tuyaToBatteryLevel and handleTuyaBatteryLevel; added batteryInitializeVars // library marker kkossev.batteryLib, line 16
 * ver. 3.2.2  2024-07-18 kkossev  - added BatteryVoltage and BatteryDelay device capability checks // library marker kkossev.batteryLib, line 17
 * ver. 3.2.3  2025-07-13 kkossev  - bug fix: corrected runIn method name from 'sendDelayedBatteryEvent' to 'sendDelayedBatteryPercentageEvent' // library marker kkossev.batteryLib, line 18
 * ver. 3.2.4  2026-08-23 kkossev  - bug fix: non-Tuya battery percentage is now rounded instead of truncated (raw 1 was reported as 0%) // library marker kkossev.batteryLib, line 19
 * // library marker kkossev.batteryLib, line 20
 *                                   TODO: add an Advanced Option resetBatteryToZeroWhenOffline // library marker kkossev.batteryLib, line 21
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 22
*/ // library marker kkossev.batteryLib, line 23

static String batteryLibVersion()   { '3.2.4' } // library marker kkossev.batteryLib, line 25
static String batteryLibStamp() { '2026/08/23 3:43 PM' } // library marker kkossev.batteryLib, line 26

metadata { // library marker kkossev.batteryLib, line 28
    capability 'Battery' // library marker kkossev.batteryLib, line 29
    attribute  'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 30
    attribute  'lastBattery', 'date'         // last battery event time - added in 3.2.0 05/21/2024 // library marker kkossev.batteryLib, line 31
    // no commands // library marker kkossev.batteryLib, line 32
    preferences { // library marker kkossev.batteryLib, line 33
        if (device && advancedOptions == true) { // library marker kkossev.batteryLib, line 34
            if ('BatteryVoltage' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 35
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: 'Convert battery voltage to battery Percentage remaining.' // library marker kkossev.batteryLib, line 36
            } // library marker kkossev.batteryLib, line 37
            if ('BatteryDelay' in DEVICE?.capabilities) { // library marker kkossev.batteryLib, line 38
                input(name: 'batteryDelay', type: 'enum', title: '<b>Battery Events Delay</b>', description:'Select the Battery Events Delay<br>(default is <b>no delay</b>)', options: DelayBatteryOpts.options, defaultValue: DelayBatteryOpts.defaultValue) // library marker kkossev.batteryLib, line 39
            } // library marker kkossev.batteryLib, line 40
        } // library marker kkossev.batteryLib, line 41
    } // library marker kkossev.batteryLib, line 42
} // library marker kkossev.batteryLib, line 43

@Field static final Map DelayBatteryOpts = [ defaultValue: 0, options: [0: 'No delay', 30: '30 seconds', 3600: '1 hour', 14400: '4 hours', 28800: '8 hours', 43200: '12 hours']] // library marker kkossev.batteryLib, line 45

public void standardParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 47
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 48
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 49
    if (descMap.attrId == '0020') { // battery voltage // library marker kkossev.batteryLib, line 50
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 51
        state.stats['bVoltCtr'] = (state.stats['bVoltCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 52
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 53
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 54
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 55
        } // library marker kkossev.batteryLib, line 56
    } // library marker kkossev.batteryLib, line 57
    else if (descMap.attrId == '0021') { // battery percentage // library marker kkossev.batteryLib, line 58
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 59
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 60
        if (isTuya()) { // library marker kkossev.batteryLib, line 61
            sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 62
        } // library marker kkossev.batteryLib, line 63
        else { // library marker kkossev.batteryLib, line 64
            sendBatteryPercentageEvent(Math.round(rawValue / 2.0) as int) // library marker kkossev.batteryLib, line 65
        } // library marker kkossev.batteryLib, line 66
    } // library marker kkossev.batteryLib, line 67
    else { // library marker kkossev.batteryLib, line 68
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 69
    } // library marker kkossev.batteryLib, line 70
} // library marker kkossev.batteryLib, line 71

public void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 73
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 74
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 75
    Map result = [:] // library marker kkossev.batteryLib, line 76
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 77
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 78
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 79
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 80
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 81
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 82
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 83
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 84
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 85
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 86
            result.name = 'battery' // library marker kkossev.batteryLib, line 87
            result.unit  = '%' // library marker kkossev.batteryLib, line 88
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 89
        } // library marker kkossev.batteryLib, line 90
        else { // library marker kkossev.batteryLib, line 91
            result.value = volts // library marker kkossev.batteryLib, line 92
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 93
            result.unit  = 'V' // library marker kkossev.batteryLib, line 94
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 95
        } // library marker kkossev.batteryLib, line 96
        result.type = 'physical' // library marker kkossev.batteryLib, line 97
        result.isStateChange = true // library marker kkossev.batteryLib, line 98
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 99
        sendEvent(result) // library marker kkossev.batteryLib, line 100
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 101
    } // library marker kkossev.batteryLib, line 102
    else { // library marker kkossev.batteryLib, line 103
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 104
    } // library marker kkossev.batteryLib, line 105
} // library marker kkossev.batteryLib, line 106

public void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 108
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 109
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 110
        return // library marker kkossev.batteryLib, line 111
    } // library marker kkossev.batteryLib, line 112
    final Date lastBattery = new Date() // library marker kkossev.batteryLib, line 113
    Map map = [:] // library marker kkossev.batteryLib, line 114
    map.name = 'battery' // library marker kkossev.batteryLib, line 115
    map.timeStamp = now() // library marker kkossev.batteryLib, line 116
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 117
    map.unit  = '%' // library marker kkossev.batteryLib, line 118
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 119
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 120
    map.isStateChange = true // library marker kkossev.batteryLib, line 121
    // // library marker kkossev.batteryLib, line 122
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 123
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 124
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 125
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 126
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 127
        // send it now! // library marker kkossev.batteryLib, line 128
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 129
        sendEvent(name: 'lastBattery', value: lastBattery) // library marker kkossev.batteryLib, line 130
    } // library marker kkossev.batteryLib, line 131
    else { // library marker kkossev.batteryLib, line 132
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 133
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 134
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 135
        map.lastBattery = lastBattery // library marker kkossev.batteryLib, line 136
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 137
        runIn(delayedTime, 'sendDelayedBatteryPercentageEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 138
    } // library marker kkossev.batteryLib, line 139
} // library marker kkossev.batteryLib, line 140

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 142
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 143
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 144
    sendEvent(map) // library marker kkossev.batteryLib, line 145
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 146
} // library marker kkossev.batteryLib, line 147

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 149
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 150
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 151
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 152
    sendEvent(map) // library marker kkossev.batteryLib, line 153
    sendEvent(name: 'lastBattery', value: map.lastBattery) // library marker kkossev.batteryLib, line 154
} // library marker kkossev.batteryLib, line 155

public int tuyaToBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 157
    int rawValue = fncmd // library marker kkossev.batteryLib, line 158
    switch (fncmd) { // library marker kkossev.batteryLib, line 159
        case 0: rawValue = 100; break // Battery Full // library marker kkossev.batteryLib, line 160
        case 1: rawValue = 75;  break // Battery High // library marker kkossev.batteryLib, line 161
        case 2: rawValue = 50;  break // Battery Medium // library marker kkossev.batteryLib, line 162
        case 3: rawValue = 25;  break // Battery Low // library marker kkossev.batteryLib, line 163
        case 4: rawValue = 100; break // Tuya 3 in 1 -> USB powered // library marker kkossev.batteryLib, line 164
        // for all other values >4 we will use the raw value, expected to be the real battery level 4..100% // library marker kkossev.batteryLib, line 165
    } // library marker kkossev.batteryLib, line 166
    return rawValue // library marker kkossev.batteryLib, line 167
} // library marker kkossev.batteryLib, line 168

public void handleTuyaBatteryLevel(int fncmd) { // library marker kkossev.batteryLib, line 170
    int rawValue = tuyaToBatteryLevel(fncmd) // library marker kkossev.batteryLib, line 171
    sendBatteryPercentageEvent(rawValue) // library marker kkossev.batteryLib, line 172
} // library marker kkossev.batteryLib, line 173

public void batteryInitializeVars( boolean fullInit = false ) { // library marker kkossev.batteryLib, line 175
    logDebug "batteryInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.batteryLib, line 176
    if (device.hasCapability('Battery')) { // library marker kkossev.batteryLib, line 177
        if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.batteryLib, line 178
        if (fullInit || settings?.batteryDelay == null) { device.updateSetting('batteryDelay', [value: DelayBatteryOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.batteryLib, line 179
    } // library marker kkossev.batteryLib, line 180
} // library marker kkossev.batteryLib, line 181

public List<String> batteryRefresh() { // library marker kkossev.batteryLib, line 183
    List<String> cmds = [] // library marker kkossev.batteryLib, line 184
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 100)         // battery voltage // library marker kkossev.batteryLib, line 185
    cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 100)         // battery percentage // library marker kkossev.batteryLib, line 186
    return cmds // library marker kkossev.batteryLib, line 187
} // library marker kkossev.batteryLib, line 188

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~
