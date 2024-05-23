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
 * ver. 1.1.0  2024-04-28 kkossev  - relays child devices are created automatically; if a child device exist, send a switch event, otherwise send a button event;
 * ver. 1.2.0  2024-05-21 kkossev  - (dev.branch) commonLib 3.2.0 allignment;
 *
 *                                   TODO:  configure the number of the physical switches (relays) in the Preferences
 *                                   TODO:  enable/disable the virtual switches 1,2,3 in the Preferences (also create child devices)
 */

static String version() { "1.2.0" }
static String timeStamp() {"2024/05/21 9:58 AM"}

@Field static final Boolean _DEBUG = true
@Field static final Boolean _TRACE_ALL = false      // trace all messages, including the spammy ones
@Field static final Boolean DEFAULT_DEBUG_LOGGING = true

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
@Field static final int    NUMBER_OF_BUTTONS = 141

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

@Field static final Map<Integer,String> TuyaPanelOthers = [
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
]

@Field static final Map<Integer,String> TuyaPanelScenes = [
    1: 'Reserve Scene1',    // can be written ENUM data type and will report back the value
    2: 'Reserve Scene2',    // no UI on the panel ?
    3: 'Reserve Scene3',    // processed as a button
    4: 'Reserve Scene4',

    5: 'Full-on mode',      // same as the scenes 1..4
    6: 'Full-off mode',     // processed as a button
    7: 'Viewing mode',
    8: 'Meeting mode',
    9: 'Sleeping mode',
    10: 'Coffee break mode',

    17: 'Scene ID & Group ID',  // ??? // processed as a button

    18: 'Mode 1',           // processed as a button
    19: 'Mode 2',
    20: 'Mode 3',
    21: 'Mode 4',

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
    24: 'Switch 1',        // 0x18 can be written BOOL data type and will report back the value
    25: 'Switch 2',
    26: 'Switch 3',
    27: 'Switch 4',

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
    //logDebug "customProcessTuyaDp(${descMap}, <b>dp=${dp}, fncmd=${fncmd}</b>, len=${dp_len})"
    String dpName = ''
    // check if TuyaPanelSwitches has a key equal to fncmd
    if (TuyaPanelSwitches.containsKey(dp)) {
        dpName = TuyaPanelSwitches[dp]
        logDebug "customProcessTuyaDp: TuyaPanelSwitches: dp=${dp} fncmd=${fncmd} dpName=${dpName}"
        sendChildSwitchEvent(dp, fncmd, dpName)
    }
    else if (TuyaPanelScenes.containsKey(dp)) {
        dpName = TuyaPanelScenes[dp]
        logDebug "customProcessTuyaDp: TuyaPanelScenes: dp=${dp} fncmd=${fncmd} dpName=${dpName}"
        sendSceneButtonEvent(dp, fncmd, dpName)
    }
    else if (TuyaPanelOthers.containsKey(dp)) {
        dpName = TuyaPanelOthers[dp]
        logDebug "customProcessTuyaDp: TuyaPanelOthers: dp=${dp} fncmd=${fncmd} dpName=${dpName}"
        sendSceneButtonEvent(dp, fncmd, dpName)
    }
    else {
        logWarn "customProcessTuyaDp: <b>UNKNOWN</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
        sendSceneButtonEvent(dp, fncmd, 'unknown')
    }
    return true
}

void sendChildSwitchEvent(final int dp, final int fncmd, final String switchLabel) {
    logTrace "sendChildSwitchEvent(dp=${dp}, fncmd=${fncmd})"
    if (dp <= 0 || dp > 255) { return }
    // get the dni from the Tuya endpoint
    String numberString = dp.toString().padLeft(3, '0')   
    String descriptionText = "${device.displayName} switch #${dp} (${switchLabel}) was turned ${fncmd == 1 ? 'on' : 'off'}"
    Map eventMap = [name: 'switch', value: fncmd == 1 ? 'on' : 'off', descriptionText: descriptionText, isStateChange: true]
    String dni = getChildIdString(dp)
    logTrace "dni=${dni}"
    descriptionText += dni ? " (child device #${dp})" : " (no child device)"
    logInfo "${descriptionText}"
    ChildDeviceWrapper dw = getChildDevice(dni) // null if dni is null for the parent device
    logDebug "dw=${dw} eventMap=${eventMap}"
    if (dw != null) {
        dw.parse([eventMap])
    }
    else {
        logWarn "ChildDeviceWrapper for dni ${dni} (${switchLabel}) is null. Sending a button event instead..."
        sendSceneButtonEvent(dp, fncmd, switchLabel)
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
    sendEvent(name: 'numberOfButtons', value: NUMBER_OF_BUTTONS, isStateChange: true, type: 'digital')
}

void componentOn(DeviceWrapper childDevice) {
    int childIdNumber = getChildIdNumber(childDevice)
    logDebug "componentOn: ${childDevice.deviceNetworkId} (${childIdNumber})"
    List<String> cmds = sendTuyaCommand(HexUtils.integerToHexString(childIdNumber,1), DP_TYPE_BOOL, '01')
    sendZigbeeCommands(cmds)
}

void componentOff(DeviceWrapper childDevice) {
    int childIdNumber = getChildIdNumber(childDevice)
    logDebug "componentOff: ${childDevice.deviceNetworkId} (${childIdNumber})"
    List<String> cmds = sendTuyaCommand(HexUtils.integerToHexString(childIdNumber,1), DP_TYPE_BOOL, '00')
    sendZigbeeCommands(cmds)
}

void componentRefresh(DeviceWrapper childDevice) {
    logDebug "componentRefresh: ${childDevice.deviceNetworkId} ${childDevice} (${getChildIdNumber(childDevice)}) - n/a"
}

int getChildIdNumber(DeviceWrapper childDevice) {
    String childId = childDevice.deviceNetworkId.split('-').last()
    logTrace "getChildIdNumber: ${childDevice.deviceNetworkId} -> ${childId}"
    return childId.toInteger()
}

String getChildIdString(int childId) {
    String numberString = childId.toString().padLeft(2, '0')
    logTrace "getChildIdString: ${childId} -> ${device.id}-${numberString}"
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

//@Field static final int FirstSwitchIndex = 24
@Field static final int NumberOfSwitches = 4

void createGroupOflSwitchChildDevices(int firstSwitchIndex=24, int numberOfSwitches=4) {
    logDebug "createGroupOflSwitchChildDevices}"
    for (index in firstSwitchIndex..(firstSwitchIndex + numberOfSwitches - 1)) {
        String switchLabel = "Switch #${(index - firstSwitchIndex + 1)}"
        createSwitchChildDevice(index, switchLabel)
    }
}

// called from initializeVars() in the commonLib
void customCreateChildDevices(boolean fullInit=false) {
    logDebug "customCreateChildDevices(${fullInit})"
    //if (fullInit) {
        createGroupOflSwitchChildDevices(24, 4)
    //}
}

void customParseTuyaCluster(final Map descMap) {
    logDebug "customParseTuyaCluster(${descMap})"
    if (descMap.cluster == CLUSTER_TUYA && descMap.command == '11') {
        syncTuyaDateTime()
    }
    else {
        standardParseTuyaCluster(descMap)       // from commonLib
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
    createGroupOflSwitchChildDevices()
}


// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
