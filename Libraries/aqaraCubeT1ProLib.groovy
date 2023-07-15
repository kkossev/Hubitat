library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "Aqara Cube T1 Pro Library",
    name: "aqaraCubeT1ProLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/main/libraries/aqaraCubeT1ProLib.groovy",
    version: "1.0.0",
    documentationLink: ""
)
/*
 *  zigbeeScenes - ZCL Scenes Cluster methods - library
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
 * ver. 1.0.0  2023-07-15 kkossev  - (dev. branch) - Libraries introduction for the AqaraCubeT1Pro driver; operationMode 'scene': action:wakup, hold, shake, flipToSide,  rotateLeft, rotateRight; sideUp: 1..6;
 *
 *                                   TODO: 
*/


def aqaraCubeT1ProLibVersion()   {"1.0.0"}
def aqaraCubeT1ProLibTimeStamp() {"2023/07/15 1:14 PM"}

metadata {
    attribute "operationMode", "enum", AqaraCubeModeOpts.options.values() as List<String>
    attribute "action", "enum", (AqaraCubeSceneModeOpts.options.values() + AqaraCubeActionModeOpts.options.values()) as List<String>
    attribute "cubeSide", "enum", AqaraCubeSideOpts.options.values() as List<String>
    attribute "angle", "number"
    attribute "sideUp", "number"
    
	 
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0001,0012,0006", outClusters:"0000,0003,0019", model:"lumi.remote.cagl02", manufacturer:"LUMI", deviceJoinName: "Aqara Cube T1 Pro"
    preferences {
        input name: 'cubeOperationMode', type: 'enum', title: '<b>Cube Operation Mode</b>', options: AqaraCubeModeOpts.options, defaultValue: AqaraCubeModeOpts.defaultValue, required: true, description: '<i>Operation Mode.<br>Press LINK button 5 times to toggle between action mode and scene mode</i>'
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
        0: 'rotate',
        1: 'shake',
        2: 'hold',            // activated if user picks up the cube and holds it
        3: 'sideUp',          // activated when the cube is resting on a surface
        4: 'inactivity',
        5: 'flipToSide',      // was fall
        6: 'rotateLeft',
        7: 'rotateRight'   
    ]
]

/////////////////////// action mode ////////////////////
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
          

def refreshAqaraCube() {
    List<String> cmds = []

    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)                 // battery voltage
    cmds += zigbee.readAttribute(0xFCC0, 0x0009, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x0148, [mfgCode: 0x115F], delay=200)   // operation_mode
    cmds += zigbee.readAttribute(0xFCC0, 0x0149, [mfgCode: 0x115F], delay=200)   // side_up attribute report
    
    logDebug "refreshAqaraCube() : ${cmds}"
    return cmds
}

def initVarsAqaraCube(boolean fullInit=false) {
    logDebug "initVarsAqaraCube(${fullInit})"
    if (fullInit || settings?.cubeOperationMode == null) device.updateSetting('cubeOperationMode', [value: AqaraCubeModeOpts.defaultValue.toString(), type: 'enum'])
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

def configureDeviceAqaraCube() {
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
    
    logDebug "configureDeviceAqaraCube() : ${cmds}"
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
void parseMultistateInputClusterAqaraCube(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "parseMultistateInputClusterAqaraCube: (0X012)  descMap.value=${descMap.value} value=${value}"
    String action = null
    Integer side = 0
    switch (value as Integer) {
        case 0: 
            action = 'shake'
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
            logWarn "parseMultistateInputClusterAqaraCube: unknown value: xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
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
        }
        eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${sideStr} ${eventMap.unit}"
        sendEvent(eventMap)
        logInfo "${eventMap.descriptionText}"     
    }
    else {
        logWarn "parseMultistateInputClusterAqaraCube: unknown action: ${action} xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
    }
}

void parseXiaomiClusterAqaraCube(final Map descMap) {
    logDebug "parseMultistateInputClusterAqaraCube: cluster 0xFCC0 attribute 0x${descMap.attrId} ${descMap}"
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
    def value = hexStrToUnsignedInt(descMap.value)    
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
    }
    else {
        logWarn "invalid Aqara Cube side facing up value=${value}"
    }
}

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

void parseAqaraCubeAnalogInputCluster(final Map descMap) {
    logDebug "parseAqaraCubeAnalogInputCluster: (0x000C) attribute 0x${descMap.attrId} (value ${descMap.value}) !TODO!"
    if (descMap.value == null || descMap.value == 'FFFF') { logWarn "invalid or unknown value"; return } // invalid or unknown value
    if (descMap.attrId == "0055") {
        def value = hexStrToUnsignedInt(descMap.value)
	    Float floatValue = Float.intBitsToFloat(value.intValue())   
        logWarn "value=${value} floatValue=${floatValue}" 
        sendAqaraCubeRotateEvent(floatValue as Integer)
    }
    else {
        logDebug "skipped attribute 0x${descMap.attrId}"
        return
    }
    // value: 05 0B01214E 02550039 91C24042
    // value: 05 0B0121F4 01550039 15AE1EC2
    //        05 0B0121F4 01550039 1E853FC2
    
    //        01 0B0121F4 01550039 0A579B42
    //        01 0B0121C2 01550039 EB518CC1
    //        02 0B012168 01550039 C3F5F8C1
    //        04 0B0121F4 01550039 FFFF6CC2
    //        00 0B0121F4 01550039 02001742
    //        05 0B0121F4 01550039 EB519FC2
    
    // left :  000B0121F401550039 A4F0B3C2  ( side 1)
    // left :  000B0121F401550039 AF47C1C2
    // left :  000B01214001550039 53B8CCC1
    
    // rihjt:  000B0121F401550039 EBD1EA42
    // right:  000B0121F401550039 A4700F43
    // right:  000B0121F401550039 703D4B42
    
    // left :  010B0121F401550039 15AEAFC2  (side 2)
    // left:   010B0121F401550039 8E4291C2
    
    // right:  010B0121F401550039 5338D042
    // right:  010B01210C03550039 C4F5C342
    
    // left:   010B0121F401550039 656689C2
    // left:   010B01216801550039 295CAFC1 attribute 0x0055 (value C1AF5C29) !TODO!
    // left:   010B0121F401550039 90C258C2 attribute 0x0055 (value C258C290) !TODO!
    // left:   010B0121F401550039 9799CBC2 attribute 0x0055 (value C2CB9997)

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
Manufacturer:	LUMI
Endpoint 01 application:	19
Endpoint 01 endpointId:	01
Endpoint 01 idAsInt:	1
Endpoint 01 inClusters:	0000,0003,0001,0012,0006
Endpoint 01 initialized:	true
Endpoint 01 manufacturer:	LUMI
Endpoint 01 model:	lumi.remote.cagl02
Endpoint 01 outClusters:	0000,0003,0019
Endpoint 01 profileId:	0104
Endpoint 01 stage:	4

Endpoint 02 application:	unknown
Endpoint 02 endpointId:	02
Endpoint 02 idAsInt:	2
Endpoint 02 inClusters:	0012
Endpoint 02 initialized:	true
Endpoint 02 manufacturer:	unknown
Endpoint 02 model:	unknown
Endpoint 02 outClusters:	0012
Endpoint 02 profileId:	0104
Endpoint 02 stage:	4

Endpoint 03 application:	unknown
Endpoint 03 endpointId:	03
Endpoint 03 idAsInt:	3
Endpoint 03 inClusters:	000C
Endpoint 03 initialized:	true
Endpoint 03 manufacturer:	unknown
Endpoint 03 model:	unknown
Endpoint 03 outClusters:	000C
Endpoint 03 profileId:	0104
Endpoint 03 stage:	4

*/
