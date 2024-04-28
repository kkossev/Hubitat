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
 * ver. 1.1.0  2024-04-28 kkossev  - (dev.branch) relays child devices are created automatically; if a child device exist, send a switch event, otherwise send a button event;
 *
 *                                   TODO:  
 *                                   TODO:  configure the number of the physical switches (relays) in the Preferences
 *                                   TODO:  enable/disable the virtual switches 1,2,3 in the Preferences (also create child devices)
 *                                   TODO:  create the child devices automatically
 */

static String version() { "1.1.0" }
static String timeStamp() {"2024/04/28 6:0 PM"}

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

// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnnecessaryPublicModifier, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.1.0', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.6  2024-04-08 kkossev  - removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 38
  * ver. 3.0.7  2024-04-23 kkossev  - tuyaMagic() for Tuya devices only; added stats cfgCtr, instCtr rejoinCtr, matchDescCtr, activeEpRqCtr; trace ZDO commands; added 0x0406 OccupancyCluster; reduced debug for chatty devices; // library marker kkossev.commonLib, line 39
  * ver. 3.1.0  2024-04-28 kkossev  - (dev. branch) unnecesery unschedule() speed optimization; added syncTuyaDateTime(); tuyaBlackMagic() initialization bug fix. // library marker kkossev.commonLib, line 40
  * // library marker kkossev.commonLib, line 41
  *                                   TODO: MOVE ZDO counters to health state; // library marker kkossev.commonLib, line 42
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 43
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 44
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 45
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 46
  * // library marker kkossev.commonLib, line 47
*/ // library marker kkossev.commonLib, line 48

String commonLibVersion() { '3.1.0' } // library marker kkossev.commonLib, line 50
String commonLibStamp() { '2024/04/28 5:18 PM' } // library marker kkossev.commonLib, line 51

import groovy.transform.Field // library marker kkossev.commonLib, line 53
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 54
import hubitat.device.Protocol // library marker kkossev.commonLib, line 55
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 56
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 57
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 58
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 59
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 60
import java.math.BigDecimal // library marker kkossev.commonLib, line 61

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 63

metadata { // library marker kkossev.commonLib, line 65
        if (_DEBUG) { // library marker kkossev.commonLib, line 66
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 67
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 68
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 69
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 70
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 71
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 72
            ] // library marker kkossev.commonLib, line 73
        } // library marker kkossev.commonLib, line 74

        // common capabilities for all device types // library marker kkossev.commonLib, line 76
        capability 'Configuration' // library marker kkossev.commonLib, line 77
        capability 'Refresh' // library marker kkossev.commonLib, line 78
        capability 'Health Check' // library marker kkossev.commonLib, line 79

        // common attributes for all device types // library marker kkossev.commonLib, line 81
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 82
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 83
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 84

        // common commands for all device types // library marker kkossev.commonLib, line 86
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 87

        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 89
            capability 'Switch' // library marker kkossev.commonLib, line 90
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 91
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 92
            } // library marker kkossev.commonLib, line 93
        } // library marker kkossev.commonLib, line 94

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 96
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 97

    preferences { // library marker kkossev.commonLib, line 99
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 100
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 101
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 102

        if (device) { // library marker kkossev.commonLib, line 104
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 105
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 106
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 107
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 108
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 109
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 110
                } // library marker kkossev.commonLib, line 111
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 112
            } // library marker kkossev.commonLib, line 113
        } // library marker kkossev.commonLib, line 114
    } // library marker kkossev.commonLib, line 115
} // library marker kkossev.commonLib, line 116

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 118
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 119
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 120
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 121
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 122
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 123
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 124
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 125
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 126
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 127
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 128

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 130
    defaultValue: 1, // library marker kkossev.commonLib, line 131
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 132
] // library marker kkossev.commonLib, line 133
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 134
    defaultValue: 240, // library marker kkossev.commonLib, line 135
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 136
] // library marker kkossev.commonLib, line 137
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 138
    defaultValue: 0, // library marker kkossev.commonLib, line 139
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 140
] // library marker kkossev.commonLib, line 141

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 143
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 144
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 145
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 146
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 147
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 148
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 149
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 150
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 151
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 152
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 153
] // library marker kkossev.commonLib, line 154

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 156
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 157
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 158
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 159
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 160
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 161
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 162
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 163
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 164

/** // library marker kkossev.commonLib, line 166
 * Parse Zigbee message // library marker kkossev.commonLib, line 167
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 168
 */ // library marker kkossev.commonLib, line 169
void parse(final String description) { // library marker kkossev.commonLib, line 170
    checkDriverVersion(state)    // +1 ms // library marker kkossev.commonLib, line 171
    updateRxStats(state)         // +1 ms // library marker kkossev.commonLib, line 172
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 173
    setHealthStatusOnline(state) // +2 ms // library marker kkossev.commonLib, line 174

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 176
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 177
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 178
            parseIasMessage(description) // library marker kkossev.commonLib, line 179
        } // library marker kkossev.commonLib, line 180
        else { // library marker kkossev.commonLib, line 181
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 182
        } // library marker kkossev.commonLib, line 183
        return // library marker kkossev.commonLib, line 184
    } // library marker kkossev.commonLib, line 185
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 186
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 187
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 188
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 189
        List<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 190
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 191
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194

    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) {     // +15 ms // library marker kkossev.commonLib, line 196
        return // library marker kkossev.commonLib, line 197
    } // library marker kkossev.commonLib, line 198
    final Map descMap = myParseDescriptionAsMap(description)    // +5 ms // library marker kkossev.commonLib, line 199

    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" }   // library marker kkossev.commonLib, line 201
    if (isSpammyDeviceReport(descMap)) { return }  // +20 mS (both) // library marker kkossev.commonLib, line 202

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 204
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 205
        return // library marker kkossev.commonLib, line 206
    } // library marker kkossev.commonLib, line 207
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 208
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 209
        return // library marker kkossev.commonLib, line 210
    } // library marker kkossev.commonLib, line 211
    // // library marker kkossev.commonLib, line 212
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 213
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 214
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 215

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 217
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 218
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 219
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 220
            break // library marker kkossev.commonLib, line 221
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 222
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 223
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 224
            break // library marker kkossev.commonLib, line 225
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 226
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 227
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 228
            break // library marker kkossev.commonLib, line 229
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 230
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 231
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 232
            break // library marker kkossev.commonLib, line 233
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 234
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 235
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 236
            break // library marker kkossev.commonLib, line 237
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 238
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 239
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 240
            break // library marker kkossev.commonLib, line 241
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 242
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 243
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 244
            break // library marker kkossev.commonLib, line 245
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 246
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 247
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 248
            break // library marker kkossev.commonLib, line 249
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 250
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 251
            break // library marker kkossev.commonLib, line 252
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 253
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 254
            break // library marker kkossev.commonLib, line 255
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 256
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 257
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 258
            break // library marker kkossev.commonLib, line 259
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 260
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 261
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 262
            break // library marker kkossev.commonLib, line 263
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 264
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 265
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 266
            break // library marker kkossev.commonLib, line 267
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 268
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 269
            break // library marker kkossev.commonLib, line 270
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 271
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 272
            break // library marker kkossev.commonLib, line 273
        case 0x0406 : //OCCUPANCY_CLUSTER                   // Sonoff SNZB-06 // library marker kkossev.commonLib, line 274
            parseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 275
            break // library marker kkossev.commonLib, line 276
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 277
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 278
            break // library marker kkossev.commonLib, line 279
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 280
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 281
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 282
            break // library marker kkossev.commonLib, line 283
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 284
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 285
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 286
            break // library marker kkossev.commonLib, line 287
        case 0xE002 : // library marker kkossev.commonLib, line 288
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 289
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 290
            break // library marker kkossev.commonLib, line 291
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 292
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 293
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 294
            break // library marker kkossev.commonLib, line 295
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 296
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 297
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case 0xFC11 :                                       // Sonoff // library marker kkossev.commonLib, line 300
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 301
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 302
            break // library marker kkossev.commonLib, line 303
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 304
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 305
            break // library marker kkossev.commonLib, line 306
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 307
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 308
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 309
            break // library marker kkossev.commonLib, line 310
        default: // library marker kkossev.commonLib, line 311
            if (settings.logEnable) { // library marker kkossev.commonLib, line 312
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 313
            } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
    } // library marker kkossev.commonLib, line 316
} // library marker kkossev.commonLib, line 317

static void updateRxStats(final Map state) { // library marker kkossev.commonLib, line 319
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] }  // +5ms // library marker kkossev.commonLib, line 320
} // library marker kkossev.commonLib, line 321

boolean isChattyDeviceReport(final Map descMap)  {  // when @CompileStatis is slower? // library marker kkossev.commonLib, line 323
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 324
    if (this.respondsTo('isSpammyDPsToNotTrace')) {  // defined in deviceProfileLib // library marker kkossev.commonLib, line 325
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 326
    } // library marker kkossev.commonLib, line 327
    return false // library marker kkossev.commonLib, line 328
} // library marker kkossev.commonLib, line 329

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 331
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 332
    if (this.respondsTo('isSpammyDPsToIgnore')) {   // defined in deviceProfileLib // library marker kkossev.commonLib, line 333
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 334
    } // library marker kkossev.commonLib, line 335
    return false // library marker kkossev.commonLib, line 336
} // library marker kkossev.commonLib, line 337

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 339
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 340
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 341
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 342
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 343
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 344
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 345
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 346
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 347
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 348
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 349
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 350
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 351
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 352
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 353
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 354
] // library marker kkossev.commonLib, line 355

/** // library marker kkossev.commonLib, line 357
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 358
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 359
 */ // library marker kkossev.commonLib, line 360
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 361
    if (state.stats == null) { state.stats = [:] }  // library marker kkossev.commonLib, line 362
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 363
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 364
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 365
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 366
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 367
    final String clusterInfo = "${device.displayName} Received ZDO ${clusterName} (0x${descMap.clusterId}) status ${statusName}" // library marker kkossev.commonLib, line 368
    switch (clusterId) { // library marker kkossev.commonLib, line 369
        case 0x0005 : // library marker kkossev.commonLib, line 370
            state.stats['activeEpRqCtr'] = (state.stats['activeEpRqCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 371
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, data:${descMap.data})" } // library marker kkossev.commonLib, line 372
            break // library marker kkossev.commonLib, line 373
        case 0x0006 : // library marker kkossev.commonLib, line 374
            state.stats['matchDescCtr'] = (state.stats['matchDescCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 375
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Input cluster count:${descMap.data[5]} Input cluster: 0x${descMap.data[7] + descMap.data[6]})" } // library marker kkossev.commonLib, line 376
            break // library marker kkossev.commonLib, line 377
        case 0x0013 : // device announcement // library marker kkossev.commonLib, line 378
            state.stats['rejoinCtr'] = (state.stats['rejoinCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 379
            if (settings?.logEnable) { log.info "${clusterInfo}, rejoinCtr= ${state.stats['rejoinCtr']}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Device network ID: ${descMap.data[2] + descMap.data[1]}, Capability Information: ${descMap.data[11]})" } // library marker kkossev.commonLib, line 380
            break // library marker kkossev.commonLib, line 381
        case 0x8004 : // simple descriptor response // library marker kkossev.commonLib, line 382
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, status:${descMap.data[1]}, lenght:${hubitat.helper.HexUtils.hexStringToInt(descMap.data[4])}" } // library marker kkossev.commonLib, line 383
            //parseSimpleDescriptorResponse( descMap ) // library marker kkossev.commonLib, line 384
            break // library marker kkossev.commonLib, line 385
        case 0x8005 : // endpoint response // library marker kkossev.commonLib, line 386
            String endpointCount = descMap.data[4] // library marker kkossev.commonLib, line 387
            String endpointList = descMap.data[5] // library marker kkossev.commonLib, line 388
            if (settings?.logEnable) { log.info "${clusterInfo}, (endpoint response) endpointCount = ${endpointCount}  endpointList = ${endpointList}" } // library marker kkossev.commonLib, line 389
            break // library marker kkossev.commonLib, line 390
        case 0x8021 : // bind response // library marker kkossev.commonLib, line 391
            if (settings?.logEnable) { log.info "${clusterInfo}, data=${descMap.data} (Sequence Number:${descMap.data[0]}, Status: ${descMap.data[1] == '00' ? 'Success' : '<b>Failure</b>'})" } // library marker kkossev.commonLib, line 392
            break // library marker kkossev.commonLib, line 393
        case 0x8022 : //unbind request // library marker kkossev.commonLib, line 394
        case 0x8034 : //leave response // library marker kkossev.commonLib, line 395
            if (settings?.logEnable) { log.info "${clusterInfo}" } // library marker kkossev.commonLib, line 396
            break // library marker kkossev.commonLib, line 397
        default : // library marker kkossev.commonLib, line 398
            if (settings?.logEnable) { log.warn "${device.displayName} Unprocessed ZDO command: cluster=${descMap.clusterId} command=${descMap.command} attrId=${descMap.attrId} value=${descMap.value} data=${descMap.data}" } // library marker kkossev.commonLib, line 399
            break // library marker kkossev.commonLib, line 400
    } // library marker kkossev.commonLib, line 401
    if (this.respondsTo('customParseZdoClusters')) { customParseZdoClusters(descMap) } // library marker kkossev.commonLib, line 402
} // library marker kkossev.commonLib, line 403

/** // library marker kkossev.commonLib, line 405
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 406
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 407
 */ // library marker kkossev.commonLib, line 408
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 409
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 410
    switch (commandId) { // library marker kkossev.commonLib, line 411
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 412
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 413
            break // library marker kkossev.commonLib, line 414
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 415
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 416
            break // library marker kkossev.commonLib, line 417
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 418
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 419
            break // library marker kkossev.commonLib, line 420
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 421
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 422
            break // library marker kkossev.commonLib, line 423
        case 0x0B: // default command response // library marker kkossev.commonLib, line 424
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 425
            break // library marker kkossev.commonLib, line 426
        default: // library marker kkossev.commonLib, line 427
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 428
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 429
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 430
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 431
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 432
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 433
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 434
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 435
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 436
            } // library marker kkossev.commonLib, line 437
            break // library marker kkossev.commonLib, line 438
    } // library marker kkossev.commonLib, line 439
} // library marker kkossev.commonLib, line 440

/** // library marker kkossev.commonLib, line 442
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 443
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 444
 */ // library marker kkossev.commonLib, line 445
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 446
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 447
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 448
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 449
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 450
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 451
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 452
    } // library marker kkossev.commonLib, line 453
    else { // library marker kkossev.commonLib, line 454
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 455
    } // library marker kkossev.commonLib, line 456
} // library marker kkossev.commonLib, line 457

/** // library marker kkossev.commonLib, line 459
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 460
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 461
 */ // library marker kkossev.commonLib, line 462
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 463
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 464
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 465
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 466
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 467
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 468
    } // library marker kkossev.commonLib, line 469
    else { // library marker kkossev.commonLib, line 470
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 471
    } // library marker kkossev.commonLib, line 472
} // library marker kkossev.commonLib, line 473

/** // library marker kkossev.commonLib, line 475
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 476
 */ // library marker kkossev.commonLib, line 477
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 478
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 479
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 480
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 481
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 482
        state.reportingEnabled = true // library marker kkossev.commonLib, line 483
    } // library marker kkossev.commonLib, line 484
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 485
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 486
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 487
    } else { // library marker kkossev.commonLib, line 488
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 489
    } // library marker kkossev.commonLib, line 490
} // library marker kkossev.commonLib, line 491

/** // library marker kkossev.commonLib, line 493
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 494
 */ // library marker kkossev.commonLib, line 495
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 496
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 497
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 498
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 499
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 500
    if (status == 0) { // library marker kkossev.commonLib, line 501
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 502
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 503
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 504
        int delta = 0 // library marker kkossev.commonLib, line 505
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 506
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 507
        } // library marker kkossev.commonLib, line 508
        else { // library marker kkossev.commonLib, line 509
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 510
        } // library marker kkossev.commonLib, line 511
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 512
    } // library marker kkossev.commonLib, line 513
    else { // library marker kkossev.commonLib, line 514
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 515
    } // library marker kkossev.commonLib, line 516
} // library marker kkossev.commonLib, line 517

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 519
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 520
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 521
        logTrace "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 522
        return false // library marker kkossev.commonLib, line 523
    } // library marker kkossev.commonLib, line 524
    // execute the customHandler function // library marker kkossev.commonLib, line 525
    boolean result = false // library marker kkossev.commonLib, line 526
    try { // library marker kkossev.commonLib, line 527
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 528
    } // library marker kkossev.commonLib, line 529
    catch (e) { // library marker kkossev.commonLib, line 530
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 531
        return false // library marker kkossev.commonLib, line 532
    } // library marker kkossev.commonLib, line 533
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 534
    return result // library marker kkossev.commonLib, line 535
} // library marker kkossev.commonLib, line 536

/** // library marker kkossev.commonLib, line 538
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 539
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 540
 */ // library marker kkossev.commonLib, line 541
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 542
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 543
    final String commandId = data[0] // library marker kkossev.commonLib, line 544
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 545
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 546
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 547
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 548
    } else { // library marker kkossev.commonLib, line 549
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 550
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 551
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 552
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 553
        } // library marker kkossev.commonLib, line 554
    } // library marker kkossev.commonLib, line 555
} // library marker kkossev.commonLib, line 556

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 558
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 559
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 560
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 561

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 563
    0x00: 'Success', // library marker kkossev.commonLib, line 564
    0x01: 'Failure', // library marker kkossev.commonLib, line 565
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 566
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 567
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 568
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 569
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 570
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 571
    0x88: 'Read Only', // library marker kkossev.commonLib, line 572
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 573
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 574
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 575
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 576
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 577
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 578
    0x94: 'Time out', // library marker kkossev.commonLib, line 579
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 580
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 581
] // library marker kkossev.commonLib, line 582

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 584
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 585
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 586
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 587
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 588
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 589
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 590
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 591
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 592
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 593
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 594
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 595
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 596
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 597
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 598
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 599
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 600
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 601
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 602
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 603
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 604
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 605
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 606
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 607
] // library marker kkossev.commonLib, line 608

void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 610
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' } // library marker kkossev.commonLib, line 611
} // library marker kkossev.commonLib, line 612

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 614
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 615
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 616
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 617
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 618
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 619
    return avg // library marker kkossev.commonLib, line 620
} // library marker kkossev.commonLib, line 621

/* // library marker kkossev.commonLib, line 623
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 624
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 625
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 626
*/ // library marker kkossev.commonLib, line 627
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 628

/** // library marker kkossev.commonLib, line 630
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 631
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 632
 */ // library marker kkossev.commonLib, line 633
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 634
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 635
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 636
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 637
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 638
        case 0x0000: // library marker kkossev.commonLib, line 639
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 640
            break // library marker kkossev.commonLib, line 641
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 642
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 643
            if (isPing) { // library marker kkossev.commonLib, line 644
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 645
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 646
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 647
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 648
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 649
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 650
                    sendRttEvent() // library marker kkossev.commonLib, line 651
                } // library marker kkossev.commonLib, line 652
                else { // library marker kkossev.commonLib, line 653
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 654
                } // library marker kkossev.commonLib, line 655
                state.states['isPing'] = false // library marker kkossev.commonLib, line 656
            } // library marker kkossev.commonLib, line 657
            else { // library marker kkossev.commonLib, line 658
                logTrace "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 659
            } // library marker kkossev.commonLib, line 660
            break // library marker kkossev.commonLib, line 661
        case 0x0004: // library marker kkossev.commonLib, line 662
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 663
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 664
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 665
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 666
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 667
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 668
            } // library marker kkossev.commonLib, line 669
            break // library marker kkossev.commonLib, line 670
        case 0x0005: // library marker kkossev.commonLib, line 671
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 672
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 673
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 674
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 675
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 676
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 677
            } // library marker kkossev.commonLib, line 678
            break // library marker kkossev.commonLib, line 679
        case 0x0007: // library marker kkossev.commonLib, line 680
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 681
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 682
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 683
            break // library marker kkossev.commonLib, line 684
        case 0xFFDF: // library marker kkossev.commonLib, line 685
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 686
            break // library marker kkossev.commonLib, line 687
        case 0xFFE2: // library marker kkossev.commonLib, line 688
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 689
            break // library marker kkossev.commonLib, line 690
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 691
            logTrace "Tuya attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 692
            break // library marker kkossev.commonLib, line 693
        case 0xFFFE: // library marker kkossev.commonLib, line 694
            logTrace "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 695
            break // library marker kkossev.commonLib, line 696
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 697
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 698
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 699
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 700
            break // library marker kkossev.commonLib, line 701
        default: // library marker kkossev.commonLib, line 702
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 703
            break // library marker kkossev.commonLib, line 704
    } // library marker kkossev.commonLib, line 705
} // library marker kkossev.commonLib, line 706

// power cluster            0x0001 // library marker kkossev.commonLib, line 708
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 709
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 710
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 711
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 712
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 713
    } // library marker kkossev.commonLib, line 714
    if (this.respondsTo('customParsePowerCluster')) { // library marker kkossev.commonLib, line 715
        customParsePowerCluster(descMap) // library marker kkossev.commonLib, line 716
    } // library marker kkossev.commonLib, line 717
    else { // library marker kkossev.commonLib, line 718
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 719
    } // library marker kkossev.commonLib, line 720
} // library marker kkossev.commonLib, line 721

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 723
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' } // library marker kkossev.commonLib, line 724

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 726
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 727
} // library marker kkossev.commonLib, line 728

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 730
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 731
} // library marker kkossev.commonLib, line 732

/* // library marker kkossev.commonLib, line 734
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 735
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 736
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 737
*/ // library marker kkossev.commonLib, line 738

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 740
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 741
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 742
    } // library marker kkossev.commonLib, line 743
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 744
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 745
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 746
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 747
    } // library marker kkossev.commonLib, line 748
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 749
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 750
    } // library marker kkossev.commonLib, line 751
    else { // library marker kkossev.commonLib, line 752
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 753
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 754
    } // library marker kkossev.commonLib, line 755
} // library marker kkossev.commonLib, line 756

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 758
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 759
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 760

void toggle() { // library marker kkossev.commonLib, line 762
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 763
    String state = '' // library marker kkossev.commonLib, line 764
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 765
        state = 'on' // library marker kkossev.commonLib, line 766
    } // library marker kkossev.commonLib, line 767
    else { // library marker kkossev.commonLib, line 768
        state = 'off' // library marker kkossev.commonLib, line 769
    } // library marker kkossev.commonLib, line 770
    descriptionText += state // library marker kkossev.commonLib, line 771
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 772
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 773
} // library marker kkossev.commonLib, line 774

void off() { // library marker kkossev.commonLib, line 776
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 777
        customOff() // library marker kkossev.commonLib, line 778
        return // library marker kkossev.commonLib, line 779
    } // library marker kkossev.commonLib, line 780
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 781
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 782
        return // library marker kkossev.commonLib, line 783
    } // library marker kkossev.commonLib, line 784
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 785
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 786
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 787
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 788
        if (currentState == 'off') { // library marker kkossev.commonLib, line 789
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 790
        } // library marker kkossev.commonLib, line 791
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 792
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 793
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 794
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 795
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 796
    } // library marker kkossev.commonLib, line 797
    /* // library marker kkossev.commonLib, line 798
    else { // library marker kkossev.commonLib, line 799
        if (currentState != 'off') { // library marker kkossev.commonLib, line 800
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 801
        } // library marker kkossev.commonLib, line 802
        else { // library marker kkossev.commonLib, line 803
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 804
            return // library marker kkossev.commonLib, line 805
        } // library marker kkossev.commonLib, line 806
    } // library marker kkossev.commonLib, line 807
    */ // library marker kkossev.commonLib, line 808

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 810
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 811
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 812
} // library marker kkossev.commonLib, line 813

void on() { // library marker kkossev.commonLib, line 815
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 816
        customOn() // library marker kkossev.commonLib, line 817
        return // library marker kkossev.commonLib, line 818
    } // library marker kkossev.commonLib, line 819
    List<String> cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 820
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 821
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 822
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 823
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 824
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 825
        } // library marker kkossev.commonLib, line 826
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 827
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 828
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 829
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 830
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 831
    } // library marker kkossev.commonLib, line 832
    /* // library marker kkossev.commonLib, line 833
    else { // library marker kkossev.commonLib, line 834
        if (currentState != 'on') { // library marker kkossev.commonLib, line 835
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 836
        } // library marker kkossev.commonLib, line 837
        else { // library marker kkossev.commonLib, line 838
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 839
            return // library marker kkossev.commonLib, line 840
        } // library marker kkossev.commonLib, line 841
    } // library marker kkossev.commonLib, line 842
    */ // library marker kkossev.commonLib, line 843
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 844
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 845
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 846
} // library marker kkossev.commonLib, line 847

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 849
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 850
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 851
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 852
    } // library marker kkossev.commonLib, line 853
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 854
    Map map = [:] // library marker kkossev.commonLib, line 855
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 856
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 857
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 858
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 859
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 860
        return // library marker kkossev.commonLib, line 861
    } // library marker kkossev.commonLib, line 862
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 863
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 864
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 865
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 866
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 867
        state.states['debounce'] = true // library marker kkossev.commonLib, line 868
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 869
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 870
    } else { // library marker kkossev.commonLib, line 871
        state.states['debounce'] = true // library marker kkossev.commonLib, line 872
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 873
    } // library marker kkossev.commonLib, line 874
    map.name = 'switch' // library marker kkossev.commonLib, line 875
    map.value = value // library marker kkossev.commonLib, line 876
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 877
    if (isRefresh) { // library marker kkossev.commonLib, line 878
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 879
        map.isStateChange = true // library marker kkossev.commonLib, line 880
    } else { // library marker kkossev.commonLib, line 881
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 882
    } // library marker kkossev.commonLib, line 883
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 884
    sendEvent(map) // library marker kkossev.commonLib, line 885
    clearIsDigital() // library marker kkossev.commonLib, line 886
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 887
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 888
    } // library marker kkossev.commonLib, line 889
} // library marker kkossev.commonLib, line 890

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 892
    '0': 'switch off', // library marker kkossev.commonLib, line 893
    '1': 'switch on', // library marker kkossev.commonLib, line 894
    '2': 'switch last state' // library marker kkossev.commonLib, line 895
] // library marker kkossev.commonLib, line 896

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 898
    '0': 'toggle', // library marker kkossev.commonLib, line 899
    '1': 'state', // library marker kkossev.commonLib, line 900
    '2': 'momentary' // library marker kkossev.commonLib, line 901
] // library marker kkossev.commonLib, line 902

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 904
    Map descMap = [:] // library marker kkossev.commonLib, line 905
    try { // library marker kkossev.commonLib, line 906
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 907
    } // library marker kkossev.commonLib, line 908
    catch (e1) { // library marker kkossev.commonLib, line 909
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 910
        // try alternative custom parsing // library marker kkossev.commonLib, line 911
        descMap = [:] // library marker kkossev.commonLib, line 912
        try { // library marker kkossev.commonLib, line 913
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 914
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 915
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 916
            } // library marker kkossev.commonLib, line 917
        } // library marker kkossev.commonLib, line 918
        catch (e2) { // library marker kkossev.commonLib, line 919
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 920
            return [:] // library marker kkossev.commonLib, line 921
        } // library marker kkossev.commonLib, line 922
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 923
    } // library marker kkossev.commonLib, line 924
    return descMap // library marker kkossev.commonLib, line 925
} // library marker kkossev.commonLib, line 926

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 928
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 929
        return false // library marker kkossev.commonLib, line 930
    } // library marker kkossev.commonLib, line 931
    // try to parse ... // library marker kkossev.commonLib, line 932
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 933
    Map descMap = [:] // library marker kkossev.commonLib, line 934
    try { // library marker kkossev.commonLib, line 935
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 936
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 937
    } // library marker kkossev.commonLib, line 938
    catch (e) { // library marker kkossev.commonLib, line 939
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 940
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 941
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 942
        return true // library marker kkossev.commonLib, line 943
    } // library marker kkossev.commonLib, line 944

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 946
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 947
    } // library marker kkossev.commonLib, line 948
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 949
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 950
    } // library marker kkossev.commonLib, line 951
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 952
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 953
    } // library marker kkossev.commonLib, line 954
    else { // library marker kkossev.commonLib, line 955
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 956
        return false // library marker kkossev.commonLib, line 957
    } // library marker kkossev.commonLib, line 958
    return true    // processed // library marker kkossev.commonLib, line 959
} // library marker kkossev.commonLib, line 960

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 962
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 963
  /* // library marker kkossev.commonLib, line 964
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 965
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 966
        return true // library marker kkossev.commonLib, line 967
    } // library marker kkossev.commonLib, line 968
*/ // library marker kkossev.commonLib, line 969
    Map descMap = [:] // library marker kkossev.commonLib, line 970
    try { // library marker kkossev.commonLib, line 971
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 972
    } // library marker kkossev.commonLib, line 973
    catch (e1) { // library marker kkossev.commonLib, line 974
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 975
        // try alternative custom parsing // library marker kkossev.commonLib, line 976
        descMap = [:] // library marker kkossev.commonLib, line 977
        try { // library marker kkossev.commonLib, line 978
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 979
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 980
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 981
            } // library marker kkossev.commonLib, line 982
        } // library marker kkossev.commonLib, line 983
        catch (e2) { // library marker kkossev.commonLib, line 984
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 985
            return true // library marker kkossev.commonLib, line 986
        } // library marker kkossev.commonLib, line 987
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 988
    } // library marker kkossev.commonLib, line 989
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 990
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 991
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 992
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 993
        return false // library marker kkossev.commonLib, line 994
    } // library marker kkossev.commonLib, line 995
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 996
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 997
    // attribute report received // library marker kkossev.commonLib, line 998
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 999
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1000
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1001
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1002
    } // library marker kkossev.commonLib, line 1003
    attrData.each { // library marker kkossev.commonLib, line 1004
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1005
        //def map = [:] // library marker kkossev.commonLib, line 1006
        if (it.status == '86') { // library marker kkossev.commonLib, line 1007
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1008
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1009
        } // library marker kkossev.commonLib, line 1010
        switch (it.cluster) { // library marker kkossev.commonLib, line 1011
            case '0000' : // library marker kkossev.commonLib, line 1012
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1013
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1014
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1015
                } // library marker kkossev.commonLib, line 1016
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1017
                    logTrace "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1018
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1019
                } // library marker kkossev.commonLib, line 1020
                else { // library marker kkossev.commonLib, line 1021
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1022
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1023
                } // library marker kkossev.commonLib, line 1024
                break // library marker kkossev.commonLib, line 1025
            default : // library marker kkossev.commonLib, line 1026
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1027
                break // library marker kkossev.commonLib, line 1028
        } // switch // library marker kkossev.commonLib, line 1029
    } // for each attribute // library marker kkossev.commonLib, line 1030
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1031
} // library marker kkossev.commonLib, line 1032

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1034

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1036
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1037
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1038
    def mode // library marker kkossev.commonLib, line 1039
    String attrName // library marker kkossev.commonLib, line 1040
    if (it.value == null) { // library marker kkossev.commonLib, line 1041
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1042
        return // library marker kkossev.commonLib, line 1043
    } // library marker kkossev.commonLib, line 1044
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1045
    switch (it.attrId) { // library marker kkossev.commonLib, line 1046
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1047
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1048
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1049
            break // library marker kkossev.commonLib, line 1050
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1051
            attrName = 'On Time' // library marker kkossev.commonLib, line 1052
            mode = value // library marker kkossev.commonLib, line 1053
            break // library marker kkossev.commonLib, line 1054
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1055
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1056
            mode = value // library marker kkossev.commonLib, line 1057
            break // library marker kkossev.commonLib, line 1058
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1059
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1060
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1061
            break // library marker kkossev.commonLib, line 1062
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1063
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1064
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1065
            break // library marker kkossev.commonLib, line 1066
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1067
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1068
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1069
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1070
            } // library marker kkossev.commonLib, line 1071
            else { // library marker kkossev.commonLib, line 1072
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1073
            } // library marker kkossev.commonLib, line 1074
            break // library marker kkossev.commonLib, line 1075
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1076
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1077
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1078
            break // library marker kkossev.commonLib, line 1079
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1080
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1081
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1082
            break // library marker kkossev.commonLib, line 1083
        default : // library marker kkossev.commonLib, line 1084
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1085
            return // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1088
} // library marker kkossev.commonLib, line 1089

void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1091
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1092
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1093
    } // library marker kkossev.commonLib, line 1094
    else if (this.respondsTo('levelLibParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1095
        levelLibParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1096
    } // library marker kkossev.commonLib, line 1097
    else { // library marker kkossev.commonLib, line 1098
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1099
    } // library marker kkossev.commonLib, line 1100
} // library marker kkossev.commonLib, line 1101

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1103
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1104
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1105
} // library marker kkossev.commonLib, line 1106

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1108
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1109
} // library marker kkossev.commonLib, line 1110

void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1112
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1113
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1114
    } // library marker kkossev.commonLib, line 1115
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1116
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1117
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1118
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1119
    } // library marker kkossev.commonLib, line 1120
    else { // library marker kkossev.commonLib, line 1121
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1122
    } // library marker kkossev.commonLib, line 1123
} // library marker kkossev.commonLib, line 1124

void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1126
    if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 1127
} // library marker kkossev.commonLib, line 1128

// Temperature Measurement Cluster 0x0402 // library marker kkossev.commonLib, line 1130
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1131
    if (this.respondsTo('customParseTemperatureCluster')) { // library marker kkossev.commonLib, line 1132
        customParseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 1133
    } // library marker kkossev.commonLib, line 1134
    else { // library marker kkossev.commonLib, line 1135
        logWarn "unprocessed Temperature attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1136
    } // library marker kkossev.commonLib, line 1137
} // library marker kkossev.commonLib, line 1138

// Humidity Measurement Cluster 0x0405 // library marker kkossev.commonLib, line 1140
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1141
    if (this.respondsTo('customParseHumidityCluster')) { // library marker kkossev.commonLib, line 1142
        customParseHumidityCluster(descMap) // library marker kkossev.commonLib, line 1143
    } // library marker kkossev.commonLib, line 1144
    else { // library marker kkossev.commonLib, line 1145
        logWarn "unprocessed Humidity attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1146
    } // library marker kkossev.commonLib, line 1147
} // library marker kkossev.commonLib, line 1148

// Occupancy Sensing Cluster 0x0406 // library marker kkossev.commonLib, line 1150
void parseOccupancyCluster(final Map descMap) { // library marker kkossev.commonLib, line 1151
    if (this.respondsTo('customParseOccupancyCluster')) { // library marker kkossev.commonLib, line 1152
        customParseOccupancyCluster(descMap) // library marker kkossev.commonLib, line 1153
    } // library marker kkossev.commonLib, line 1154
    else { // library marker kkossev.commonLib, line 1155
        logWarn "unprocessed Occupancy attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1156
    } // library marker kkossev.commonLib, line 1157
} // library marker kkossev.commonLib, line 1158

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1160
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1161
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1162
} // library marker kkossev.commonLib, line 1163

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1165
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1166
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1167
} // library marker kkossev.commonLib, line 1168

// pm2.5 // library marker kkossev.commonLib, line 1170
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1171
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1172
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1173
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1174
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1175
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1176
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1177
    } // library marker kkossev.commonLib, line 1178
    else { // library marker kkossev.commonLib, line 1179
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1180
    } // library marker kkossev.commonLib, line 1181
} // library marker kkossev.commonLib, line 1182

// Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1184
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1185
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1186
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1187
    } // library marker kkossev.commonLib, line 1188
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1189
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1190
    } // library marker kkossev.commonLib, line 1191
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1192
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1193
    } // library marker kkossev.commonLib, line 1194
    else { // library marker kkossev.commonLib, line 1195
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1196
    } // library marker kkossev.commonLib, line 1197
} // library marker kkossev.commonLib, line 1198

// Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1200
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1201
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1202
} // library marker kkossev.commonLib, line 1203

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1205
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1206
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1207
} // library marker kkossev.commonLib, line 1208

// thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1210
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1211
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1212
} // library marker kkossev.commonLib, line 1213

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1215
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1216
} // library marker kkossev.commonLib, line 1217

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1219
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars // library marker kkossev.commonLib, line 1220
} // library marker kkossev.commonLib, line 1221

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1223
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars // library marker kkossev.commonLib, line 1224
} // library marker kkossev.commonLib, line 1225

/* // library marker kkossev.commonLib, line 1227
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1228
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1229
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1230
*/ // library marker kkossev.commonLib, line 1231
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1232
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1233
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1234

// Tuya Commands // library marker kkossev.commonLib, line 1236
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1237
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1238
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1239
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1240
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1241

// tuya DP type // library marker kkossev.commonLib, line 1243
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1244
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1245
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1246
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1247
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1248
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1249

void syncTuyaDateTime() { // library marker kkossev.commonLib, line 1251
    // The data format for time synchronization, including standard timestamps and local timestamps. Standard timestamp (4 bytes)    local timestamp (4 bytes) Time synchronization data format: The standard timestamp is the total number of seconds from 00:00:00 on January 01, 1970 GMT to the present. // library marker kkossev.commonLib, line 1252
    // For example, local timestamp = standard timestamp + number of seconds between standard time and local time (including time zone and daylight saving time).  // Y2K = 946684800 // library marker kkossev.commonLib, line 1253
    long offset = 0 // library marker kkossev.commonLib, line 1254
    int offsetHours = 0 // library marker kkossev.commonLib, line 1255
    Calendar cal = Calendar.getInstance();    //it return same time as new Date() // library marker kkossev.commonLib, line 1256
    def hour = cal.get(Calendar.HOUR_OF_DAY) // library marker kkossev.commonLib, line 1257
    try { // library marker kkossev.commonLib, line 1258
        offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1259
        offsetHours = (offset / 3600000) as int // library marker kkossev.commonLib, line 1260
        logDebug "timezone offset of current location is ${offset} (${offsetHours} hours), current hour is ${hour} h" // library marker kkossev.commonLib, line 1261
    } catch(e) { // library marker kkossev.commonLib, line 1262
        log.error "${device.displayName} cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 1263
    } // library marker kkossev.commonLib, line 1264
    // // library marker kkossev.commonLib, line 1265
    List<String> cmds // library marker kkossev.commonLib, line 1266
    cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000),8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1267
    String dateTimeNow = unix2formattedDate(now()) // library marker kkossev.commonLib, line 1268
    logDebug "sending time data : ${dateTimeNow} (${cmds})" // library marker kkossev.commonLib, line 1269
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1270
    logInfo "Tuya device time synchronized to ${dateTimeNow}" // library marker kkossev.commonLib, line 1271
} // library marker kkossev.commonLib, line 1272


void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1275
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1276
        syncTuyaDateTime() // library marker kkossev.commonLib, line 1277
    } // library marker kkossev.commonLib, line 1278
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1279
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1280
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1281
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1282
        if (status != '00') { // library marker kkossev.commonLib, line 1283
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1284
        } // library marker kkossev.commonLib, line 1285
    } // library marker kkossev.commonLib, line 1286
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1287
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1288
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1289
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1290
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1291
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1292
            return // library marker kkossev.commonLib, line 1293
        } // library marker kkossev.commonLib, line 1294
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1295
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1296
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1297
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1298
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1299
            if (!isChattyDeviceReport(descMap)) { // library marker kkossev.commonLib, line 1300
                logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1301
            } // library marker kkossev.commonLib, line 1302
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1303
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1304
        } // library marker kkossev.commonLib, line 1305
    } // library marker kkossev.commonLib, line 1306
    else { // library marker kkossev.commonLib, line 1307
        if (this.respondsTo('customParseTuyaCluster')) { // library marker kkossev.commonLib, line 1308
            customParseTuyaCluster(descMap) // library marker kkossev.commonLib, line 1309
        } // library marker kkossev.commonLib, line 1310
        else { // library marker kkossev.commonLib, line 1311
            logWarn "unprocessed Tuya cluster command ${descMap?.command} data=${descMap?.data}" // library marker kkossev.commonLib, line 1312
        } // library marker kkossev.commonLib, line 1313
    } // library marker kkossev.commonLib, line 1314
} // library marker kkossev.commonLib, line 1315

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1317
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1318
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1319
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1320
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1321
            return // library marker kkossev.commonLib, line 1322
        } // library marker kkossev.commonLib, line 1323
    } // library marker kkossev.commonLib, line 1324
    // check if the method  method exists // library marker kkossev.commonLib, line 1325
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1326
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1327
            return // library marker kkossev.commonLib, line 1328
        } // library marker kkossev.commonLib, line 1329
    } // library marker kkossev.commonLib, line 1330
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1331
} // library marker kkossev.commonLib, line 1332

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1334
    int retValue = 0 // library marker kkossev.commonLib, line 1335
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1336
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1337
        int power = 1 // library marker kkossev.commonLib, line 1338
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 1339
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 1340
            power = power * 256 // library marker kkossev.commonLib, line 1341
        } // library marker kkossev.commonLib, line 1342
    } // library marker kkossev.commonLib, line 1343
    return retValue // library marker kkossev.commonLib, line 1344
} // library marker kkossev.commonLib, line 1345

private List<String> getTuyaCommand(String dp, String dp_type, String fncmd) { return sendTuyaCommand(dp, dp_type, fncmd) } // library marker kkossev.commonLib, line 1347

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 1349
    List<String> cmds = [] // library marker kkossev.commonLib, line 1350
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 1351
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1352
    int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 1353
    //tuyaCmd = 0x04  // !!!!!!!!!!!!!!!!!!!!!!! // library marker kkossev.commonLib, line 1354
    cmds = zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], delay = 201, PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 1355
    logDebug "${device.displayName} getTuyaCommand (dp=$dp fncmd=$fncmd dp_type=$dp_type) = ${cmds}" // library marker kkossev.commonLib, line 1356
    return cmds // library marker kkossev.commonLib, line 1357
} // library marker kkossev.commonLib, line 1358

private getPACKET_ID() { // library marker kkossev.commonLib, line 1360
    /* // library marker kkossev.commonLib, line 1361
    int packetId = state.packetId ?: 0 // library marker kkossev.commonLib, line 1362
    state.packetId = packetId + 1 // library marker kkossev.commonLib, line 1363
    return zigbee.convertToHexString(packetId, 4) // library marker kkossev.commonLib, line 1364
    */ // library marker kkossev.commonLib, line 1365
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 1366
} // library marker kkossev.commonLib, line 1367

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1369
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 1370
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 1371
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 1372
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 1373
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 1374
} // library marker kkossev.commonLib, line 1375

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 1377
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 1378

List<String> tuyaBlackMagic() { // library marker kkossev.commonLib, line 1380
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 1381
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1382
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 1383
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 1384
} // library marker kkossev.commonLib, line 1385

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 1387
    List<String> cmds = [] // library marker kkossev.commonLib, line 1388
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1389
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 1390
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1391
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1392
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 1393
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 1394
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 1395
        } // library marker kkossev.commonLib, line 1396
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1397
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 1398
    } // library marker kkossev.commonLib, line 1399
    else { // library marker kkossev.commonLib, line 1400
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 1401
    } // library marker kkossev.commonLib, line 1402
} // library marker kkossev.commonLib, line 1403

/** // library marker kkossev.commonLib, line 1405
 * initializes the device // library marker kkossev.commonLib, line 1406
 * Invoked from configure() // library marker kkossev.commonLib, line 1407
 * @return zigbee commands // library marker kkossev.commonLib, line 1408
 */ // library marker kkossev.commonLib, line 1409
List<String> initializeDevice() { // library marker kkossev.commonLib, line 1410
    List<String> cmds = [] // library marker kkossev.commonLib, line 1411
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 1412
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 1413
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 1414
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1415
    } // library marker kkossev.commonLib, line 1416
    logDebug "initializeDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 1417
    return cmds // library marker kkossev.commonLib, line 1418
} // library marker kkossev.commonLib, line 1419

/** // library marker kkossev.commonLib, line 1421
 * configures the device // library marker kkossev.commonLib, line 1422
 * Invoked from configure() // library marker kkossev.commonLib, line 1423
 * @return zigbee commands // library marker kkossev.commonLib, line 1424
 */ // library marker kkossev.commonLib, line 1425
List<String> configureDevice() { // library marker kkossev.commonLib, line 1426
    List<String> cmds = [] // library marker kkossev.commonLib, line 1427
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 1428
    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 1429
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 1430
        if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1431
    } // library marker kkossev.commonLib, line 1432
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 1433
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 1434
    logDebug "configureDevice(): cmds=${cmds}" // library marker kkossev.commonLib, line 1435
    return cmds // library marker kkossev.commonLib, line 1436
} // library marker kkossev.commonLib, line 1437

/* // library marker kkossev.commonLib, line 1439
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1440
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 1441
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1442
*/ // library marker kkossev.commonLib, line 1443

List<String> customHandlers(final List customHandlersList) { // library marker kkossev.commonLib, line 1445
    List<String> cmds = [] // library marker kkossev.commonLib, line 1446
    if (customHandlersList != null && !customHandlersList.isEmpty()) { // library marker kkossev.commonLib, line 1447
        customHandlersList.each { handler -> // library marker kkossev.commonLib, line 1448
            if (this.respondsTo(handler)) { // library marker kkossev.commonLib, line 1449
                List<String> customCmds = this."${handler}"() // library marker kkossev.commonLib, line 1450
                if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1451
            } // library marker kkossev.commonLib, line 1452
        } // library marker kkossev.commonLib, line 1453
    } // library marker kkossev.commonLib, line 1454
    return cmds // library marker kkossev.commonLib, line 1455
} // library marker kkossev.commonLib, line 1456

void refresh() { // library marker kkossev.commonLib, line 1458
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE} model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')}" // library marker kkossev.commonLib, line 1459
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1460
    List<String> cmds = [] // library marker kkossev.commonLib, line 1461
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1462

    List<String> customCmds = customHandlers(['batteryRefresh', 'groupsRefresh', 'customRefresh']) // library marker kkossev.commonLib, line 1464
    if (customCmds != null && !customCmds.isEmpty()) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1465

    if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 1467
    else { // library marker kkossev.commonLib, line 1468
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 1469
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1470
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1471
        } // library marker kkossev.commonLib, line 1472
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1473
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1474
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1475
        } // library marker kkossev.commonLib, line 1476
    } // library marker kkossev.commonLib, line 1477

    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1479
        logDebug "refresh() cmds=${cmds}" // library marker kkossev.commonLib, line 1480
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1481
    } // library marker kkossev.commonLib, line 1482
    else { // library marker kkossev.commonLib, line 1483
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1484
    } // library marker kkossev.commonLib, line 1485
} // library marker kkossev.commonLib, line 1486

public void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 1488
public void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1489

public void clearInfoEvent() { // library marker kkossev.commonLib, line 1491
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 1492
} // library marker kkossev.commonLib, line 1493

public void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1495
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1496
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1497
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 1498
    } // library marker kkossev.commonLib, line 1499
    else { // library marker kkossev.commonLib, line 1500
        logInfo "${info}" // library marker kkossev.commonLib, line 1501
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 1502
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1503
    } // library marker kkossev.commonLib, line 1504
} // library marker kkossev.commonLib, line 1505

public void ping() { // library marker kkossev.commonLib, line 1507
    if (state.lastTx == null ) { state.lastTx = [:] } ; state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1508
    if (state.states == null ) { state.states = [:] } ;     state.states['isPing'] = true // library marker kkossev.commonLib, line 1509
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1510
    if (isVirtual()) { runInMillis(10, virtualPong) } // library marker kkossev.commonLib, line 1511
    else { sendZigbeeCommands(zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) } // library marker kkossev.commonLib, line 1512
    logDebug 'ping...' // library marker kkossev.commonLib, line 1513
} // library marker kkossev.commonLib, line 1514

def virtualPong() { // library marker kkossev.commonLib, line 1516
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1517
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1518
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1519
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1520
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1521
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1522
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1523
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1524
        sendRttEvent() // library marker kkossev.commonLib, line 1525
    } // library marker kkossev.commonLib, line 1526
    else { // library marker kkossev.commonLib, line 1527
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1528
    } // library marker kkossev.commonLib, line 1529
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1530
    //unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1531
    unscheduleCommandTimeoutCheck(state) // library marker kkossev.commonLib, line 1532
} // library marker kkossev.commonLib, line 1533

/** // library marker kkossev.commonLib, line 1535
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 1536
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 1537
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 1538
 * @return none // library marker kkossev.commonLib, line 1539
 */ // library marker kkossev.commonLib, line 1540
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1541
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1542
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1543
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1544
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1545
    if (value == null) { // library marker kkossev.commonLib, line 1546
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1547
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1548
    } // library marker kkossev.commonLib, line 1549
    else { // library marker kkossev.commonLib, line 1550
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1551
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1552
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1553
    } // library marker kkossev.commonLib, line 1554
} // library marker kkossev.commonLib, line 1555

/** // library marker kkossev.commonLib, line 1557
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 1558
 * @param cluster cluster ID // library marker kkossev.commonLib, line 1559
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 1560
 */ // library marker kkossev.commonLib, line 1561
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1562
    if (cluster != null) { // library marker kkossev.commonLib, line 1563
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1564
    } // library marker kkossev.commonLib, line 1565
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1566
    return 'NULL' // library marker kkossev.commonLib, line 1567
} // library marker kkossev.commonLib, line 1568

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1570
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1571
    state.states['isTimeoutCheck'] = true // library marker kkossev.commonLib, line 1572
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1573
} // library marker kkossev.commonLib, line 1574

// unschedule() is a very time consuming operation : ~ 5 milliseconds per call ! // library marker kkossev.commonLib, line 1576
void unscheduleCommandTimeoutCheck(final Map state) {   // can not be static :(  // library marker kkossev.commonLib, line 1577
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1578
    if (state.states['isTimeoutCheck'] == true) { // library marker kkossev.commonLib, line 1579
        state.states['isTimeoutCheck'] = false // library marker kkossev.commonLib, line 1580
        unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1581
    } // library marker kkossev.commonLib, line 1582
} // library marker kkossev.commonLib, line 1583

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1585
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1586
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1587
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1588
} // library marker kkossev.commonLib, line 1589

/** // library marker kkossev.commonLib, line 1591
 * Schedule a device health check // library marker kkossev.commonLib, line 1592
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 1593
 */ // library marker kkossev.commonLib, line 1594
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1595
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1596
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1597
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1598
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1599
    } // library marker kkossev.commonLib, line 1600
    else { // library marker kkossev.commonLib, line 1601
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1602
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1603
    } // library marker kkossev.commonLib, line 1604
} // library marker kkossev.commonLib, line 1605

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1607
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1608
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1609
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1610
} // library marker kkossev.commonLib, line 1611

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1613

void setHealthStatusOnline(Map state) { // library marker kkossev.commonLib, line 1615
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1616
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1617
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1618
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1619
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1620
    } // library marker kkossev.commonLib, line 1621
} // library marker kkossev.commonLib, line 1622

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1624
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1625
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1626
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1627
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1628
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1629
            logWarn 'not present!' // library marker kkossev.commonLib, line 1630
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1631
        } // library marker kkossev.commonLib, line 1632
    } // library marker kkossev.commonLib, line 1633
    else { // library marker kkossev.commonLib, line 1634
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1635
    } // library marker kkossev.commonLib, line 1636
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1637
} // library marker kkossev.commonLib, line 1638

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1640
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1641
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1642
    if (value == 'online') { // library marker kkossev.commonLib, line 1643
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1644
    } // library marker kkossev.commonLib, line 1645
    else { // library marker kkossev.commonLib, line 1646
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1647
    } // library marker kkossev.commonLib, line 1648
} // library marker kkossev.commonLib, line 1649

/** // library marker kkossev.commonLib, line 1651
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 1652
 */ // library marker kkossev.commonLib, line 1653
void autoPoll() { // library marker kkossev.commonLib, line 1654
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 1655
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1656
    List<String> cmds = [] // library marker kkossev.commonLib, line 1657
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 1658
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 1659
    } // library marker kkossev.commonLib, line 1660

    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1662
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1663
    } // library marker kkossev.commonLib, line 1664
} // library marker kkossev.commonLib, line 1665

/** // library marker kkossev.commonLib, line 1667
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1668
 */ // library marker kkossev.commonLib, line 1669
void updated() { // library marker kkossev.commonLib, line 1670
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1671
    checkDriverVersion(state) // library marker kkossev.commonLib, line 1672
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1673
    unschedule() // library marker kkossev.commonLib, line 1674

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1676
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1677
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1678
    } // library marker kkossev.commonLib, line 1679
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1680
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1681
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1682
    } // library marker kkossev.commonLib, line 1683

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1685
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1686
        // schedule the periodic timer // library marker kkossev.commonLib, line 1687
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1688
        if (interval > 0) { // library marker kkossev.commonLib, line 1689
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1690
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1691
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1692
        } // library marker kkossev.commonLib, line 1693
    } // library marker kkossev.commonLib, line 1694
    else { // library marker kkossev.commonLib, line 1695
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1696
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1697
    } // library marker kkossev.commonLib, line 1698
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1699
        customUpdated() // library marker kkossev.commonLib, line 1700
    } // library marker kkossev.commonLib, line 1701

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1703
} // library marker kkossev.commonLib, line 1704

/** // library marker kkossev.commonLib, line 1706
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 1707
 */ // library marker kkossev.commonLib, line 1708
void logsOff() { // library marker kkossev.commonLib, line 1709
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1710
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1711
} // library marker kkossev.commonLib, line 1712
void traceOff() { // library marker kkossev.commonLib, line 1713
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1714
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1715
} // library marker kkossev.commonLib, line 1716

void configure(String command) { // library marker kkossev.commonLib, line 1718
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1719
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1720
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1721
        return // library marker kkossev.commonLib, line 1722
    } // library marker kkossev.commonLib, line 1723
    // // library marker kkossev.commonLib, line 1724
    String func // library marker kkossev.commonLib, line 1725
    try { // library marker kkossev.commonLib, line 1726
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1727
        "$func"() // library marker kkossev.commonLib, line 1728
    } // library marker kkossev.commonLib, line 1729
    catch (e) { // library marker kkossev.commonLib, line 1730
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1731
        return // library marker kkossev.commonLib, line 1732
    } // library marker kkossev.commonLib, line 1733
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1734
} // library marker kkossev.commonLib, line 1735

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1737
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1738
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1739
} // library marker kkossev.commonLib, line 1740

void loadAllDefaults() { // library marker kkossev.commonLib, line 1742
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1743
    deleteAllSettings() // library marker kkossev.commonLib, line 1744
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1745
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1746
    deleteAllStates() // library marker kkossev.commonLib, line 1747
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1748
    initialize() // library marker kkossev.commonLib, line 1749
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1750
    updated() // library marker kkossev.commonLib, line 1751
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1752
} // library marker kkossev.commonLib, line 1753

void configureNow() { // library marker kkossev.commonLib, line 1755
    configure() // library marker kkossev.commonLib, line 1756
} // library marker kkossev.commonLib, line 1757

/** // library marker kkossev.commonLib, line 1759
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1760
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1761
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1762
 */ // library marker kkossev.commonLib, line 1763
void configure() { // library marker kkossev.commonLib, line 1764
    List<String> cmds = [] // library marker kkossev.commonLib, line 1765
    if (state.stats == null) { state.stats = [:] } ; state.stats.cfgCtr = (state.stats.cfgCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1766
    logInfo "configure()... cfgCtr=${state.stats.cfgCtr}" // library marker kkossev.commonLib, line 1767
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1768
    if (isTuya()) { // library marker kkossev.commonLib, line 1769
        cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1770
    } // library marker kkossev.commonLib, line 1771
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1772
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1773
    } // library marker kkossev.commonLib, line 1774
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1775
    if (initCmds != null && !initCmds.isEmpty()) { cmds += initCmds } // library marker kkossev.commonLib, line 1776
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1777
    if (cfgCmds != null && !cfgCmds.isEmpty()) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1778
    if (cmds != null && !cmds.isEmpty()) { // library marker kkossev.commonLib, line 1779
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1780
        logDebug "configure(): sent cmds = ${cmds}" // library marker kkossev.commonLib, line 1781
        sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1782
    } // library marker kkossev.commonLib, line 1783
    else { // library marker kkossev.commonLib, line 1784
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1785
    } // library marker kkossev.commonLib, line 1786
} // library marker kkossev.commonLib, line 1787

/** // library marker kkossev.commonLib, line 1789
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 1790
 */ // library marker kkossev.commonLib, line 1791
void installed() { // library marker kkossev.commonLib, line 1792
    if (state.stats == null) { state.stats = [:] } ; state.stats.instCtr = (state.stats.instCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1793
    logInfo "installed()... instCtr=${state.stats.instCtr}" // library marker kkossev.commonLib, line 1794
    // populate some default values for attributes // library marker kkossev.commonLib, line 1795
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1796
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1797
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1798
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1799
} // library marker kkossev.commonLib, line 1800

/** // library marker kkossev.commonLib, line 1802
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1803
 */ // library marker kkossev.commonLib, line 1804
void initialize() { // library marker kkossev.commonLib, line 1805
    if (state.stats == null) { state.stats = [:] } ; state.stats.initCtr = (state.stats.initCtr ?: 0) + 1 // library marker kkossev.commonLib, line 1806
    logInfo "initialize()... initCtr=${state.stats.initCtr}" // library marker kkossev.commonLib, line 1807
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1808
    updateTuyaVersion() // library marker kkossev.commonLib, line 1809
    updateAqaraVersion() // library marker kkossev.commonLib, line 1810
} // library marker kkossev.commonLib, line 1811

/* // library marker kkossev.commonLib, line 1813
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1814
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1815
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1816
*/ // library marker kkossev.commonLib, line 1817

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1819
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1820
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1821
} // library marker kkossev.commonLib, line 1822

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1824
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1825
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1826
} // library marker kkossev.commonLib, line 1827

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1829
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1830
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1831
} // library marker kkossev.commonLib, line 1832

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1834
    if (cmd == null || cmd.isEmpty()) { // library marker kkossev.commonLib, line 1835
        logWarn "sendZigbeeCommands: list is empty! cmd=${cmd}" // library marker kkossev.commonLib, line 1836
        return // library marker kkossev.commonLib, line 1837
    } // library marker kkossev.commonLib, line 1838
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1839
    cmd.each { // library marker kkossev.commonLib, line 1840
        if (it == null || it.isEmpty() || it == 'null') { // library marker kkossev.commonLib, line 1841
            logWarn "sendZigbeeCommands it: no commands to send! it=${it} (cmd=${cmd})" // library marker kkossev.commonLib, line 1842
            return // library marker kkossev.commonLib, line 1843
        } // library marker kkossev.commonLib, line 1844
        allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1845
        if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1846
    } // library marker kkossev.commonLib, line 1847
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1848
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1849
    logDebug "sendZigbeeCommands: sent cmd=${cmd}" // library marker kkossev.commonLib, line 1850
} // library marker kkossev.commonLib, line 1851

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1853

String getDeviceInfo() { // library marker kkossev.commonLib, line 1855
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1856
} // library marker kkossev.commonLib, line 1857

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1859
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1860
} // library marker kkossev.commonLib, line 1861

@CompileStatic // library marker kkossev.commonLib, line 1863
void checkDriverVersion(final Map state) { // library marker kkossev.commonLib, line 1864
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1865
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1866
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1867
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1868
        initializeVars(false) // library marker kkossev.commonLib, line 1869
        updateTuyaVersion() // library marker kkossev.commonLib, line 1870
        updateAqaraVersion() // library marker kkossev.commonLib, line 1871
    } // library marker kkossev.commonLib, line 1872
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1873
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1874
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1875
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1876
} // library marker kkossev.commonLib, line 1877


// credits @thebearmay // library marker kkossev.commonLib, line 1880
String getModel() { // library marker kkossev.commonLib, line 1881
    try { // library marker kkossev.commonLib, line 1882
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1883
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1884
    } catch (ignore) { // library marker kkossev.commonLib, line 1885
        try { // library marker kkossev.commonLib, line 1886
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1887
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1888
                return model // library marker kkossev.commonLib, line 1889
            } // library marker kkossev.commonLib, line 1890
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1891
            return '' // library marker kkossev.commonLib, line 1892
        } // library marker kkossev.commonLib, line 1893
    } // library marker kkossev.commonLib, line 1894
} // library marker kkossev.commonLib, line 1895

// credits @thebearmay // library marker kkossev.commonLib, line 1897
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1898
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1899
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1900
    String revision = tokens.last() // library marker kkossev.commonLib, line 1901
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1902
} // library marker kkossev.commonLib, line 1903

/** // library marker kkossev.commonLib, line 1905
 * called from TODO // library marker kkossev.commonLib, line 1906
 */ // library marker kkossev.commonLib, line 1907

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1909
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1910
    unschedule() // library marker kkossev.commonLib, line 1911
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1912
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1913

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1915
} // library marker kkossev.commonLib, line 1916

void resetStatistics() { // library marker kkossev.commonLib, line 1918
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1919
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1920
} // library marker kkossev.commonLib, line 1921

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1923
void resetStats() { // library marker kkossev.commonLib, line 1924
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1925
    state.stats = [:] // library marker kkossev.commonLib, line 1926
    state.states = [:] // library marker kkossev.commonLib, line 1927
    state.lastRx = [:] // library marker kkossev.commonLib, line 1928
    state.lastTx = [:] // library marker kkossev.commonLib, line 1929
    state.health = [:] // library marker kkossev.commonLib, line 1930
    if (this.respondsTo('groupsLibVersion')) { // library marker kkossev.commonLib, line 1931
        state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1932
    } // library marker kkossev.commonLib, line 1933
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 1934
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1935
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 1936
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 1937
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 1938
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1939
} // library marker kkossev.commonLib, line 1940

/** // library marker kkossev.commonLib, line 1942
 * called from TODO // library marker kkossev.commonLib, line 1943
 */ // library marker kkossev.commonLib, line 1944
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1945
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1946
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1947
        state.clear() // library marker kkossev.commonLib, line 1948
        unschedule() // library marker kkossev.commonLib, line 1949
        resetStats() // library marker kkossev.commonLib, line 1950
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1951
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1952
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1953
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1954
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1955
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1956
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1957
    } // library marker kkossev.commonLib, line 1958

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1960
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1961
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1962
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1963
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1964

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1966
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', DEFAULT_DEBUG_LOGGING ?: false) } // library marker kkossev.commonLib, line 1967
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1968
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 1969
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1970
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1971
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1972
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1973
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1974
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 1975

    // common libraries initialization - TODO !!!!!!!!!!!!! // library marker kkossev.commonLib, line 1977
    executeCustomHandler('groupsInitializeVars', fullInit) // library marker kkossev.commonLib, line 1978
    executeCustomHandler('deviceProfileInitializeVars', fullInit) // library marker kkossev.commonLib, line 1979
    executeCustomHandler('illuminanceInitializeVars', fullInit) // library marker kkossev.commonLib, line 1980

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1982
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1983
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1984
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1985
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 1986

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1988
    if ( mm != null) { // library marker kkossev.commonLib, line 1989
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 1990
    } // library marker kkossev.commonLib, line 1991
    else { // library marker kkossev.commonLib, line 1992
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 1993
    } // library marker kkossev.commonLib, line 1994
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1995
    if ( ep  != null) { // library marker kkossev.commonLib, line 1996
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1997
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1998
    } // library marker kkossev.commonLib, line 1999
    else { // library marker kkossev.commonLib, line 2000
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2001
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2002
    } // library marker kkossev.commonLib, line 2003
} // library marker kkossev.commonLib, line 2004

/** // library marker kkossev.commonLib, line 2006
 * called from TODO // library marker kkossev.commonLib, line 2007
 */ // library marker kkossev.commonLib, line 2008
void setDestinationEP() { // library marker kkossev.commonLib, line 2009
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2010
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2011
        state.destinationEP = ep // library marker kkossev.commonLib, line 2012
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2013
    } // library marker kkossev.commonLib, line 2014
    else { // library marker kkossev.commonLib, line 2015
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2016
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2017
    } // library marker kkossev.commonLib, line 2018
} // library marker kkossev.commonLib, line 2019

void logDebug(final String msg) { // library marker kkossev.commonLib, line 2021
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2022
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2023
    } // library marker kkossev.commonLib, line 2024
} // library marker kkossev.commonLib, line 2025

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2027
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2028
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2029
    } // library marker kkossev.commonLib, line 2030
} // library marker kkossev.commonLib, line 2031

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2033
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2034
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2035
    } // library marker kkossev.commonLib, line 2036
} // library marker kkossev.commonLib, line 2037

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2039
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2040
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2041
    } // library marker kkossev.commonLib, line 2042
} // library marker kkossev.commonLib, line 2043

// _DEBUG mode only // library marker kkossev.commonLib, line 2045
void getAllProperties() { // library marker kkossev.commonLib, line 2046
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2047
    device.properties.each { it -> // library marker kkossev.commonLib, line 2048
        log.debug it // library marker kkossev.commonLib, line 2049
    } // library marker kkossev.commonLib, line 2050
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2051
    settings.each { it -> // library marker kkossev.commonLib, line 2052
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2053
    } // library marker kkossev.commonLib, line 2054
    log.trace 'Done' // library marker kkossev.commonLib, line 2055
} // library marker kkossev.commonLib, line 2056

// delete all Preferences // library marker kkossev.commonLib, line 2058
void deleteAllSettings() { // library marker kkossev.commonLib, line 2059
    String preferencesDeleted = '' // library marker kkossev.commonLib, line 2060
    settings.each { it -> // library marker kkossev.commonLib, line 2061
        preferencesDeleted += "${it.key} (${it.value}), " // library marker kkossev.commonLib, line 2062
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2063
    } // library marker kkossev.commonLib, line 2064
    logDebug "Deleted settings: ${preferencesDeleted}" // library marker kkossev.commonLib, line 2065
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2066
} // library marker kkossev.commonLib, line 2067

// delete all attributes // library marker kkossev.commonLib, line 2069
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2070
    String attributesDeleted = '' // library marker kkossev.commonLib, line 2071
    device.properties.supportedAttributes.each { it -> attributesDeleted += "${it}, " ; device.deleteCurrentState("$it") } // library marker kkossev.commonLib, line 2072
    logDebug "Deleted attributes: ${attributesDeleted}" ; logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2073
} // library marker kkossev.commonLib, line 2074

// delete all State Variables // library marker kkossev.commonLib, line 2076
void deleteAllStates() { // library marker kkossev.commonLib, line 2077
    String stateDeleted = '' // library marker kkossev.commonLib, line 2078
    state.each { it -> stateDeleted += "${it.key}, " } // library marker kkossev.commonLib, line 2079
    state.clear() // library marker kkossev.commonLib, line 2080
    logDebug "Deleted states: ${stateDeleted}" ; logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2081
} // library marker kkossev.commonLib, line 2082

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2084
    unschedule() ; logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2085
} // library marker kkossev.commonLib, line 2086

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2088
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 2089
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 2090
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 2091
    } // library marker kkossev.commonLib, line 2092
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 2093
} // library marker kkossev.commonLib, line 2094

void parseTest(String par) { // library marker kkossev.commonLib, line 2096
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2097
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2098
    parse(par) // library marker kkossev.commonLib, line 2099
} // library marker kkossev.commonLib, line 2100

def testJob() { // library marker kkossev.commonLib, line 2102
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2103
} // library marker kkossev.commonLib, line 2104

/** // library marker kkossev.commonLib, line 2106
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2107
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2108
 */ // library marker kkossev.commonLib, line 2109
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2110
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2111
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2112
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2113
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2114
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2115
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2116
    String cron // library marker kkossev.commonLib, line 2117
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2118
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2119
    } // library marker kkossev.commonLib, line 2120
    else { // library marker kkossev.commonLib, line 2121
        if (minutes < 60) { // library marker kkossev.commonLib, line 2122
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2123
        } // library marker kkossev.commonLib, line 2124
        else { // library marker kkossev.commonLib, line 2125
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2126
        } // library marker kkossev.commonLib, line 2127
    } // library marker kkossev.commonLib, line 2128
    return cron // library marker kkossev.commonLib, line 2129
} // library marker kkossev.commonLib, line 2130

// credits @thebearmay // library marker kkossev.commonLib, line 2132
String formatUptime() { // library marker kkossev.commonLib, line 2133
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2134
} // library marker kkossev.commonLib, line 2135

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2137
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2138
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2139
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2140
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2141
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2142
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2143
} // library marker kkossev.commonLib, line 2144

boolean isTuya() { // library marker kkossev.commonLib, line 2146
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2147
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2148
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2149
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2150
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2151
} // library marker kkossev.commonLib, line 2152

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2154
    if (!isTuya()) { logTrace 'not Tuya' ; return } // library marker kkossev.commonLib, line 2155
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2156
    if (application != null) { // library marker kkossev.commonLib, line 2157
        Integer ver // library marker kkossev.commonLib, line 2158
        try { // library marker kkossev.commonLib, line 2159
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2160
        } // library marker kkossev.commonLib, line 2161
        catch (e) { // library marker kkossev.commonLib, line 2162
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2163
            return // library marker kkossev.commonLib, line 2164
        } // library marker kkossev.commonLib, line 2165
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2166
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2167
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2168
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2169
        } // library marker kkossev.commonLib, line 2170
    } // library marker kkossev.commonLib, line 2171
} // library marker kkossev.commonLib, line 2172

boolean isAqara() { // library marker kkossev.commonLib, line 2174
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2175
} // library marker kkossev.commonLib, line 2176

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2178
    if (!isAqara()) { logTrace 'not Aqara' ; return } // library marker kkossev.commonLib, line 2179
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2180
    if (application != null) { // library marker kkossev.commonLib, line 2181
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2182
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2183
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2184
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2185
        } // library marker kkossev.commonLib, line 2186
    } // library marker kkossev.commonLib, line 2187
} // library marker kkossev.commonLib, line 2188

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2190
    try { // library marker kkossev.commonLib, line 2191
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2192
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2193
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2194
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2195
    } catch (e) { // library marker kkossev.commonLib, line 2196
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2197
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2198
    } // library marker kkossev.commonLib, line 2199
} // library marker kkossev.commonLib, line 2200

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2202
    try { // library marker kkossev.commonLib, line 2203
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2204
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2205
        return date.getTime() // library marker kkossev.commonLib, line 2206
    } catch (e) { // library marker kkossev.commonLib, line 2207
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2208
        return now() // library marker kkossev.commonLib, line 2209
    } // library marker kkossev.commonLib, line 2210
} // library marker kkossev.commonLib, line 2211
/* // library marker kkossev.commonLib, line 2212
void test(String par) { // library marker kkossev.commonLib, line 2213
    List<String> cmds = [] // library marker kkossev.commonLib, line 2214
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2215

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2217
    //parse(par) // library marker kkossev.commonLib, line 2218

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2220
} // library marker kkossev.commonLib, line 2221
*/ // library marker kkossev.commonLib, line 2222

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
