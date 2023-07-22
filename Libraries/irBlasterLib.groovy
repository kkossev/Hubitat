library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "IR Blaster Library",
    name: "irBlasterLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/main/libraries/airQualityLib.groovy",
    version: "1.0.0",
    documentationLink: ""
)
/*
 * irBlasterLib - IR Blaster Library
 *
 * ver. 1.0.0  2023-07-22 kkossev  - Libraries introduction for the IR Blaster driver;
 *
 *                                   TODO: 
*/

def irBlasterLibVersion()   {"1.0.0"}
def irBlasterTimeStamp()    {"2023/07/22 8:54 PM"}

metadata {
    attribute "learnedCode", "string"

    command "learnIR", [[name: "learnIR", type: "NUMBER", description: "learn IR code", defaultValue : ""]]
    command "sendIR",  [[name: "sendIR*", type: "STRING", description: "send IR code", defaultValue : ""]]
    
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,0003,0001,ED00,E004,0006", outClusters:"0019,000A", model:"TS1201", manufacturer:"_TZ3290_ot6ewjvmejq5ekhl", deviceJoinName: "Moes Zigbee IR Remote Controller UFO-R11" 
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,0003,0001,ED00,E004,0006", outClusters:"0019,000A", model:"TS1201", manufacturer:"_TZ3290_j37rooaxrcdcqo5n", deviceJoinName: "Tuya Zigbee IR Remote Controller ZS06" 
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0004,0005,0003,0001,ED00,E004,0006", outClusters:"0019,000A", model:"TS1201", manufacturer:"_TZ3290_acv1iuslxi3shaaj", deviceJoinName: "Tuya Zigbee IR Remote Controller" 
     
    preferences {

    }
}

//    https://github.com/Koenkk/zigbee-herdsman-converters/blob/9d5e7b902479582581615cbfac3148d66d4c675c/devices/tuya.js#L3762
//    https://github.com/Koenkk/zigbee-herdsman-converters/blob/9d5e7b902479582581615cbfac3148d66d4c675c/lib/zosung.js#L41
//    https://github.com/Koenkk/zigbee-herdsman/blob/61b701e8826d0120833a5485ebd595d4a2a5cfe9/src/zcl/definition/cluster.ts#L5115
//    https://github.com/Koenkk/zigbee-herdsman-converters/blob/49e8b9544d2598adb324d9591ac7910b450672b8/src/lib/zosung.ts 
//    https://github.com/ferehcarb/zha-device-handlers/blob/105fa7608ab61219be853fd3745a7c39d07d1c3a/zhaquirks/tuya/ts1201.py   
//    https://github.com/dresden-elektronik/deconz-rest-plugin/issues/6814 

@Field static final Map IRBlasterModeOpts = [        // used by airQualityIndexCheckInterval
    defaultValue: 0,
    options     : [0: 'send', 1: 'learn']
]

/*
 * -----------------------------------------------------------------------------
 * irBlaster
 * -----------------------------------------------------------------------------
*/


def configureIrBlaster() {
    ArrayList<String> cmds = []
    logDebug 'configureIrBlaster() (nothig to configure)'
    return cmds
}


def initializeDeviceIrBlaster() {
    ArrayList<String> cmds = []
    logDebug 'initializeDeviceIrBlaster() (nothig to initialize)'
    return cmds
}

void updatedIrBlaster() {
    logDebug "updatedIrBlaster: (nothig to update)"
}

def refreshIrBlaster() {
    List<String> cmds = []
    logDebug "refreshIrBlaster() : ${cmds} (nothig to refresh)"
    return cmds
}

def initVarsIrBlaster(boolean fullInit=false) {
    logDebug "initVarsIrBlaster(${fullInit}) (no vars to init)"
}

void initEventsIrBlaster(boolean fullInit=false) {
    logDebug "initVarsIrBlaster(${fullInit}) (nothing)"
}
