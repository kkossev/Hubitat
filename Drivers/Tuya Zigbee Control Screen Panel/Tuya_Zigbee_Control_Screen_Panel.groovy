/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName */
/*
 *  Tuya Zigbee Button Dimmer - driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *  https://community.hubitat.com/t/a-new-interesting-tuya-zigbee-control-screen-panel-w-relays/136208/1
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
 * ver. 0.1.0  2024-04-07 kkossev  - first version
 * ver. 0.1.1  2024-04-10 kkossev  - removed Switch capability; added Smart Blind buttons 110,111,112; Projector buttons 113,114
 * ver. 0.1.2  2024-04-11 kkossev  - added syncTuyaDateTime test button; added info links; removed duplicated switch switch in the child device names
 * ver. 1.0.0  2024-04-13 kkossev  - first release version
 * ver. 1.0.1  2024-04-27 kkossev  - commonLib 3.1.0 update; sync the time automatically on device power up; 
 *
 *                                   TODO:  configure the number of switches in the preferences
 *                                   TODO:  create the child devices automatically
 */

static String version() { "1.0.1" }
static String timeStamp() {"2024/04/27 8:33 PM"}

@Field static final Boolean _DEBUG = true
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy ones
@Field static final Boolean _SIMULATION = false      // _TZE200_vm1gyrso

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput
import com.hubitat.app.ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper

deviceType = "multiEpSwitch"
@Field static final String DEVICE_TYPE = "multiEpSwitch"

@Field static final String DRIVER_NAME = 'Tuya Zigbee Control Screen Panel'
@Field static final String WIKI   = 'Wiki page:'
@Field static final String COMM_LINK =   'https://community.hubitat.com/t/a-new-interesting-tuya-zigbee-control-screen-panel-w-relays-and-scenes-t3e-2023-new-model/136208/1'
@Field static final String GITHUB_LINK = 'https://github.com/kkossev/Hubitat/wiki/Tuya-Zigbee-Control-Screen-Panel'

#include kkossev.commonLib

metadata {
    definition (
        name: 'Tuya Zigbee Control Screen Panel',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Control%20Screen%20Panel/Tuya_Zigbee_Control_Screen_Panel_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {
        capability 'Actuator'
        //capability 'Switch' 
        capability 'PushableButton'

        //attribute 'hubitatMode', 'enum', HubitatModeOpts.options.values() as List<String>
        //attribute 'scene', 'string'   // not used, TOBEDEL
        //command 'syncTuyaDateTime'
    }
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_mua6ucdj"   // this device
    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
	    input name: "helpInfo", type: "hidden", title: fmtHelpInfo("Community Link")
        if (device) {
            //input name: 'hubitatMode', type: 'enum', title: '<b>Hubitat Integration Mode</b>', options: HubitatModeOpts.options, defaultValue: HubitatModeOpts.defaultValue, required: true, description: '<i>Method to integrate with HE.</i>'
        }
    }
}

@Field static final Map HubitatModeOpts = [
    defaultValue: 0,
    options     : [0: 'Scene Mode', 1: 'Virtual Buttons']
]

@Field static final Map<Integer,String> TUYA_DP = [
    1: 'Reserve Scene1',    // can be written ENUM data type and will report back the value
    2: 'Reserve Scene2',    // no UI on the panel ?
    3: 'Reserve Scene3',
    4: 'Reserve Scene4',

    5: 'Full-on mode',    // same as the scenes 1..4
    6: 'Full-off mode',
    7: 'Viewing mode',
    8: 'Meeting mode',
    9: 'Sleeping mode',
    10: 'Coffee break mode',
    
    17: 'Scene ID & Group ID',

    18: 'Mode 1',
    19: 'Mode 2',
    20: 'Mode 3',
    21: 'Mode 4',
    24: 'Switch 1',
    25: 'Switch 2',
    26: 'Switch 3',
    27: 'Switch 4',
    30: 'Countdown 1',
    31: 'Countdown 2',
    32: 'Countdown 3',
    33: 'Countdown 4',
    38: 'Relay status all',
    39: 'Relay status 1',
    40: 'Relay status 2',
    41: 'Relay status 3',
    42: 'Relay status 4',
    101: 'Temp value',
    102: 'Bright value 1',
    103: 'Temp switch 1',
    104: 'Curtain open',
    105: 'Curtain stop',
    106: 'Curtain close',
    107: 'Roller shutter open',
    108: 'Roller shutter stop',
    109: 'Roller shutter close',
    110: 'Blinds open',
    111: 'Blinds stop',
    112: 'Blinds close',
    113: 'Projector open',
    114: 'Projector close',
    115: 'Screen open',
    116: 'Screen stop',
    117: 'Screen close',
    118: 'Gauze curtain open',
    119: 'Gauze curtain stop',
    120: 'Gauze curtain close',
    121: 'Window open',
    122: 'Window stop',
    123: 'Window close',
    124: 'Air Conditioner on',
    125: 'Air Conditioner off',
    126: 'Air Conditioner cooling',
    127: 'Air Conditioner heating'
]

@Field static final Map<Integer,String> TuyaPanelSwitches = [
    130: 'Switch 1-1',
    131: 'Switch 1-2',
    132: 'Switch 1-3',
    133: 'Switch 1-4',
    134: 'Switch 2-1',
    135: 'Switch 2-2',
    136: 'Switch 2-3',
    137: 'Switch 2-4',
    138: 'Switch 3-1',
    139: 'Switch 3-2',
    140: 'Switch 3-3',
    141: 'Switch 3-4'
]

boolean customProcessTuyaDp(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) {
    logDebug "customProcessTuyaDp(${descMap}, ${dp}, ${dp_id}, ${fncmd}, ${dp_len})"

    switch (dp) {
        /*
        case 0x01 : // on/off
            sendChildSwitchEvent(dp, fncmd)
            break
            */
        case 0x05 : // Full-on mode
            logDebug "Full-on mode: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Mode FullOn')
            break
        case 0x06 : // Full-off mode
            logDebug "Full-off mode: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Mode FullOff')
            break
        case 0x07 : //  Viewing mode
            if (_SIMULATION) {
                logDebug "<b>test!</b>Switch 2: ${fncmd}"
                sendChildSwitchEvent(25, fncmd)
            }
            else {
                logDebug "Viewing mode: ${fncmd}"
                sendSceneButtonEvent(dp, fncmd, 'Mode Viewing')
            }
            break
        case 0x08 : // Meeting mode
            logDebug "Meeting mode: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Mode Meeting')
            break
        case 0x09 : // Sleeping mode
            logDebug "Sleeping mode: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Mode Sleeping')
            break
        case 0x0A : // (10) Coffee break mode
            logDebug "Coffee break mode: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Mode CoffeeBreak')
            break
        case 0x12 : // (18) Unknown 1
            logDebug "Unknown 1: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Mode Unknown1')
            break
        case 0x13 : // (19) Unknown 2
            logDebug "Unknown 2: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Mode Unknown2')
            break
        case 0x14 : // (20) Unknown 3
            logDebug "Unknown 3: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Mode Unknown3')
            break
        case 0x15 : // (21) Unknown 4
            logDebug "Unknown 4: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Mode Unknown4')
            break
        case 0x18 : // (24) Switch 1
            logDebug "Switch 1: ${fncmd}"
            sendChildSwitchEvent(dp, fncmd)
            break
        case 0x19 : // (25) Switch 2
            logDebug "Switch 2: ${fncmd}"
            sendChildSwitchEvent(dp, fncmd)
            break
        case 0x1A : // (26) Switch 3
            logDebug "Switch 3: ${fncmd}"
            sendChildSwitchEvent(dp, fncmd)
            break
        case 0x1B : // (27) Switch 4
            logDebug "Switch 4: ${fncmd}"
            sendChildSwitchEvent(dp, fncmd)
            break
        case 0x68: // (104) Smart Curtain button 1
            logDebug "Smart Curtain button 1: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Curtain button1')
            break
        case 0x69: // (105) Smart Curtain button 2
            logDebug "Smart Curtain button 2: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Curtain button2')
            break
        case 0x6A: // (106) Smart Curtain button 3
            logDebug "Smart Curtain button 3: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Curtain button3')
            break
        case 0x6E : // (110) Smart Blind button 1
            logDebug "Smart Blind button 1: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Blind button1')
            break
        case 0x6F : // (111) Smart Blind button 2   
            logDebug "Smart Blind button 2: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Blind button2')
            break
        case 0x70 : // (112) Smart Blind button 3
            logDebug "Smart Blind button 3: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Blind button3')
            break
        case 0x71 : // (113) Projector button 1
            logDebug "Projector button 1: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Projector button1')
            break
        case 0x72 : // (114) Projector button 2
            logDebug "Projector button 2: ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'Projector button2')
            break
        case 0x7C: // (124) Air Conditioner button 4 (ON)
            logDebug "Air Conditioner button 4 (ON): ${fncmd}"  
            sendSceneButtonEvent(dp, fncmd, 'AC button4')
            break
        case 0x7D: // (125) Air Conditioner button 1 (OFF)
            logDebug "Air Conditioner button 1 (OFF): ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'AC button1')
            break
        case 0x7E: // (126) Air Conditioner button 2 (Cooling)
            logDebug "Air Conditioner button 2 (Cooling): ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'AC button2')
            break
        case 0x7F: // (127) Air Conditioner button 3 (Heating)
            logDebug "Air Conditioner button 3 (Heating): ${fncmd}"
            sendSceneButtonEvent(dp, fncmd, 'AC button3')
            break
        
        default :
            logWarn "<b>UNKNOWN</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            sendSceneButtonEvent(dp, fncmd, 'unknown')
            break
    }
    return true
}

void sendChildSwitchEvent(final int dp, final int fncmd) {
    logTrace "sendChildSwitchEvent(dp=${dp}, fncmd=${fncmd})"
    if (dp <= 0 || dp > 99) { return }
    // get the dni from the Tuya endpoint
    String numberString = dp.toString().padLeft(2, '0')   
    String descriptionText = "${device.displayName} switch #${dp} was turned ${fncmd == 1 ? 'on' : 'off'}"
    Map eventMap = [name: 'switch', value: fncmd == 1 ? 'on' : 'off', descriptionText: descriptionText, isStateChange: true]
    logInfo "${descriptionText} (child device #${dp})"
    String dni = getChildIdString(dp)
    logTrace "dni=${dni}"
    ChildDeviceWrapper dw = getChildDevice(dni) // null if dni is null for the parent device
    logDebug "dw=${dw} eventMap=${eventMap}"
    if (dw != null) {
        dw.parse([eventMap])
    }
    else {
        logWarn "ChildDeviceWrapper for dni ${dni} is null"
    }
}

void sendSceneButtonEvent(final int dp, final int fncmd, final String sceneName) {
    logTrace "sendSceneButtonEvent(dp=${dp}, fncmd=${fncmd}, sceneName=${sceneName})"
    String descriptionText = "button ${dp} was pushed (${sceneName ?: 'unknown'})"	
    Map event = [name: 'pushed', value: dp.toString(), data: [buttonNumber: dp], descriptionText: descriptionText, isStateChange: true, type: isDigital == true ? 'digital' : 'physical']
    logInfo "$descriptionText"
    sendEvent(event)
}

void customUpdated() {
    logDebug "customUpdated()"
}

void customInitializeVars(final boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (fullInit || settings?.hubitatMode == null) { device.updateSetting('hubitatMode', [value: HubitatModeOpts.defaultValue.toString(), type: 'enum']) }
}

void customInitEvents(final boolean fullInit=false) {
    logDebug "customInitEvents(${fullInit})"
    sendEvent(name: 'numberOfButtons', value: 127, isStateChange: true, type: 'digital')
}

void componentOn(DeviceWrapper childDevice) {
    int childIdNumber = getChildIdNumber(childDevice)
    if (_SIMULATION) { if (childIdNumber == 25) { childIdNumber = 7 } }   // test
    logDebug "sending componentOff ${childDevice.deviceNetworkId} (${childIdNumber})"
    List<String> cmds = sendTuyaCommand(HexUtils.integerToHexString(childIdNumber,1), DP_TYPE_BOOL, '01')
    sendZigbeeCommands(cmds)
}

void componentOff(DeviceWrapper childDevice) {
    int childIdNumber = getChildIdNumber(childDevice)
    if (_SIMULATION) { log.trace "childIdNumber=${childIdNumber}"; if (childIdNumber == 25) { childIdNumber = 7 } }   // test
    logDebug "sending componentOff ${childDevice.deviceNetworkId} (${childIdNumber})"
    List<String> cmds = sendTuyaCommand(HexUtils.integerToHexString(childIdNumber,1), DP_TYPE_BOOL, '00')
    sendZigbeeCommands(cmds)
}

void componentRefresh(DeviceWrapper childDevice) {
    logDebug "componentRefresh ${childDevice.deviceNetworkId} ${childDevice} (${getChildIdNumber(childDevice)}) - n/a"
}

int getChildIdNumber(DeviceWrapper childDevice) {
    String childId = childDevice.deviceNetworkId.split('-').last()
    logTrace "getChildIdNumber ${childDevice.deviceNetworkId} -> ${childId}"
    return childId.toInteger()
}

String getChildIdString(int childId) {
    String numberString = childId.toString().padLeft(2, '0')
    logTrace "getChildIdString ${childId} -> ${device.id}-${numberString}"
    return "${device.id}-${numberString}"
}


void createSwitchChildDevice(int index, String switchLabel) {
    logTrace "createChildDevice index=${index}"
    if (index == 0) { return }
    String childId = getChildIdString(index)
    ChildDeviceWrapper existingChild = getChildDevice(childId)
    if (existingChild) {
        log.info "${device.displayName} Child device ${existingChild} already exists (${childId})"
    } 
    else {
        String childDeviceName = "device ${switchLabel} (${childId})"
        logDebug "${device.displayName} Creating ${childDeviceName}"
        addChildDevice(
            'hubitat', 
            'Generic Component Switch', 
            childId, 
            [
                isComponent: false, 
                name: "${device.displayName} Switch EP${index.toString().padLeft(2, '0')}", 
                label: " ${switchLabel}"
            ]
        )
        sendInfoEvent "Created ${childDeviceName}"
    }
}

@Field static final int FirstSwitchIndex = 24
@Field static final int NumberOfSwitches = 4

void createAllSwitchChildDevices() {
    logDebug "createAllSwitchChildDevices}"
    for (index in FirstSwitchIndex..(FirstSwitchIndex + NumberOfSwitches - 1)) {
        String switchLabel = "Switch #${(index - FirstSwitchIndex + 1)}"
        createSwitchChildDevice(index, switchLabel)
    }
}

void customCreateChildDevices(boolean fullInit=false) {
    logDebug "customCreateChildDevices(${fullInit})"
    if (fullInit) {
        createAllSwitchChildDevices()
    }
}


void customParseTuyaCluster(final Map descMap) {
    logDebug "customParseTuyaCluster(${descMap})"
    if (descMap.cluster == CLUSTER_TUYA && descMap.command == '11') {
        syncTuyaDateTime()
    }
}

// credits @jtp10181
String fmtHelpInfo(String str) {
	String info = "${DRIVER_NAME} v${version()}"
	String prefLink = "<a href='${GITHUB_LINK}' target='_blank'>${WIKI}<br><div style='font-size: 70%;'>${info}</div></a>"
    String topStyle = "style='font-size: 18px; padding: 1px 12px; border: 2px solid green; border-radius: 6px; color: green;'"
    String topLink = "<a ${topStyle} href='${COMM_LINK}' target='_blank'>${str}<br><div style='font-size: 14px;'>${info}</div></a>"

	return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>" +
		"<div style='text-align: center; position: absolute; top: 46px; right: 60px; padding: 0px;'><ul class='nav'><li>${topLink}</ul></li></div>"
}


void test(String par) {
    /*
    List<String> cmds = []
    log.warn "test... ${par}"

    cmds = ["zdo unbind 0x${device.id} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",]
    //parse(par)


    sendZigbeeCommands(cmds)
    */
    createAllSwitchChildDevices()
}


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
