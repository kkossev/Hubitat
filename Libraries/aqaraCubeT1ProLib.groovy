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
 * ver. 1.0.0  2023-07-13 kkossev  - (dev. branch) - Libraries introduction for the AqaraCubeT1Pro driver;
 *
 *                                   TODO: 
*/


def aqaraCubeT1ProLibVersion()   {"1.0.0"}
def aqaraCubeT1ProLibTimeStamp() {"2023/07/13 1:03 PM"}

metadata {
    attribute "mode", "enum", AqaraCubeModeOpts.options.values() as List<String>
    attribute "action", "enum", (AqaraCubeSceneModeOpts.options.values() + AqaraCubeActionModeOpts.options.values()) as List<String>
    attribute "cubeSide", "enum", AqaraCubeSideOpts.options.values() as List<String>
    attribute "angle", "number"     
        
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0003,0500,0001", outClusters:"0019", model:"lumi.remote.cagl02", manufacturer:"LUMI", deviceJoinName: "Aqara Cube T1 Pro"
    preferences {
        input name: 'cubeMode', type: 'enum', title: '<b>Mode</b>', options: AqaraCubeModeOpts.options, defaultValue: AqaraCubeModeOpts.defaultValue, required: true, description: '<i>Operation Mode.<br>Press LINK button 5 times to toggle between action mode and scene mode</i>'
    }
}

// https://github.com/Koenkk/zigbee2mqtt/issues/15652 

@Field static final Map AqaraCubeModeOpts = [
    defaultValue: 0,
    options     : [0: 'action', 1: 'scene']
]

@Field static final Map AqaraCubeSceneModeOpts = [
    defaultValue: 0,
    options     : [
        0: 'rotate',
        1: 'shake',
        2: 'hold',        // activated if user picks up the cube and holds it
        3: 'sideUp',      // activated when the cube is resting on a surface
        4: 'inactivity'
    ]
]

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
        0: 'action_from_side',
        1: 'action_side',
        2: 'action_to_side',
        3: 'side',                  // Destination side of action
        4: 'side_up'                // Upfacing side of current scene
    ]
]          
          

def refreshAqaraCube() {
    List<String> cmds = []
    // TODO
    logDebug "refreshAqaraCube() : ${cmds}"
    return cmds
}

def initVarsAqaraCube(boolean fullInit=false) {
    logDebug "initVarsAqaraCube(${fullInit})"
    if (fullInit || settings?.cubeMode == null) device.updateSetting('cubeMode', [value: AqaraCubeModeOpts.defaultValue.toString(), type: 'enum'])
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
    
    // await endpoint.write('aqaraOpple', {'mode': 1}, {manufacturerCode: 0x115f});
    // TODO !!!!
    def mode = settings?.cubeMode ?: 0
    cmds += zigbee.writeAttribute(0xFCC0, 0x0148, 0x20, mode, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x0148, [mfgCode: 0x115F], delay=200)
    
    // TODO !!!
    //    await reporting.bind(endpoint, coordinatorEndpoint, ['genBasic','genOnOff','genPowerCfg','genMultistateInput']);

    // TODO !!!
    //    await endpoint.read('genPowerCfg', ['batteryVoltage']);

    // TODO !!!
    //    await endpoint.read('aqaraOpple', [0x0148], {manufacturerCode: 0x115f});

    // TODO !!!
    //    await endpoint.read('aqaraOpple', [0x0149], {manufacturerCode: 0x115f});
    
    logDebug "configureDeviceAqaraCube() : ${cmds}"
    return cmds    
}

/*
 # Clusters (Scene Mode): 
  ## Endpoint 2: 

  | Cluster            | Data                      | Description                   |
  | ------------------ | ------------------------- | ----------------------------- |
  | aqaraopple         | {329: 0-5}                | i side facing up              |
  | genMultistateInput | {presentValue: 0}         | action: shake                 |
  | genMultistateInput | {presentValue: 4}         | action: hold                  |
  | genMultistateInput | {presentValue: 2}         | action: wakeup                |
  | genMultistateInput | {presentValue: 1024-1029} | action: fall with ith side up |

  ## Endpoint 3: 

  | Cluster   | Data                                  | Desc                                       |
  | --------- | ------------------------------------- | ------------------------------------------ |
  | genAnalog | {267: 500, 329: 3, presentValue: -51} | 267: NA, 329: side up, presentValue: angle |


*/

/*
{
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
    },



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
