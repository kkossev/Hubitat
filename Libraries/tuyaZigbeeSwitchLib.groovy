library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "Tuya Zigbee Switch Library",
    name: "tuyaZigbeeSwitchLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/main/libraries/tuyaZigbeeSwitchLib.groovy",
    version: "1.0.0",
    documentationLink: ""
)
/*
 * tuyaZigbeeSwitchLib - Tuya Zigbee Switch Library
 *
 * ver. 1.0.0  2023-07-21 kkossev  - Libraries introduction for the Tuya Zigbee Switch driver;
 *
 *                                   TODO:
*/

def tuyaZigbeeSwitchLibVersion()   {"1.0.0"}
def tuyaZigbeeSwitchLibTimeStamp() {"2023/07/21 10:20 PM"}

metadata {
    //attribute "operationMode", "enum", AqaraCubeModeOpts.options.values() as List<String>
    
    //command "push", [[name: "sent when the cube side is flipped", type: "NUMBER", description: "simulates a button press", defaultValue : ""]]

    fingerprint profileId:"0104", endpointId:"01", inClusters:"0003,0004,0005,0006,E000,E001,0000", outClusters:"0019,000A", model:"TS0001", manufacturer:"_TZ3000_ajv2vfow", deviceJoinName: "EARU 3-Phase Circuit Breaker"
    preferences {
        //input name: 'cubeOperationMode', type: 'enum', title: '<b>Cube Operation Mode</b>', options: AqaraCubeModeOpts.options, defaultValue: AqaraCubeModeOpts.defaultValue, required: true, description: '<i>Operation Mode.<br>Press LINK button 5 times to toggle between action mode and scene mode</i>'
    }
}

/*
@Field static final Map AqaraCubeModeOpts = [
    defaultValue: 1,
    options     : [0: 'action', 1: 'scene']
]
*/


def refreshSwitch() {
    List<String> cmds = []
    cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay=200)
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership
    logDebug "refreshSwitch() : ${cmds}"
    return cmds
}

def initVarsSwitch(boolean fullInit=false) {
    logDebug "initVarsSwitch(${fullInit})"
/*    
    if (fullInit || settings?.cubeOperationMode == null) device.updateSetting('cubeOperationMode', [value: AqaraCubeModeOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.sendButtonEvent == null) device.updateSetting('sendButtonEvent', [value: SendButtonEventOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.voltageToPercent == null) device.updateSetting("voltageToPercent", true)        // overwrite the defailt false setting
*/
}

void initEventsSwitch(boolean fullInit=false) {
/*    
    sendNumberOfButtonsEvent(6)
    def supportedValues = ["pushed", "double", "held", "released", "tested"]
    sendSupportedButtonValuesEvent(supportedValues)
*/
}


def configureDeviceSwitch() {
    List<String> cmds = []
/*
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0000 {${device.zigbeeId}} {}", "delay 251", ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}", "delay 251", ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}", "delay 251", ]
    
    cmds += zigbee.readAttribute(0xFCC0, 0x0009, [mfgCode: 0x115F], delay=200)
    cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)
    cmds += zigbee.readAttribute(0xFCC0, 0x0148, [mfgCode: 0x115F], delay=200)   
    cmds += zigbee.readAttribute(0xFCC0, 0x0149, [mfgCode: 0x115F], delay=200)   
*/    
    logDebug "configureDeviceSwitch() : ${cmds}"
    return cmds    
}


void parseOnOffClusterSwitch(final Map descMap) {
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value
    def value = hexStrToUnsignedInt(descMap.value)
    logDebug "parseOnOffClusterSwitch: (0x0006)  attribute 0x${descMap.attrId} descMap.value=${descMap.value} value=${value}"
}

