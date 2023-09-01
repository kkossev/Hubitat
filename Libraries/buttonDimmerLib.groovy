library (
    base: "driver",
    author: "Krassimir Kossev",
    category: "zigbee",
    description: "Zigbee Button Dimmer Library",
    name: "buttonDimmerLib",
    namespace: "kkossev",
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/main/libraries/buttonDimmerLib.groovy",
    version: "1.0.1",
    documentationLink: ""
)
/*
 *  Zigbee Button Dimmer -Library
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
 * ver. 1.0.0  2023-08-30 kkossev  - Libraries introduction for the Tuya Zigbee Button Dimmer driver; added IKEA Styrbar E2001/E2002;
 * ver. 1.0.1  2023-09-01 kkossev  - (dev.branch) added TRADFRI on/off switch E1743; 
 *
 *                                   TODO: add IKEA RODRET E2201  keys processing !
 *                                   TODO: verify Ikea reporting configuration (WireShark) 1
 *                                   TODO: battery options  (pairing)
 *                                   TODO: add option 'Level Step'
 *                                   TODO: increase level on Up/Down key presses  (left-right rotation)  (simulation)
 *                                   TODO: STYRBAR - battery not repored (bind power cluster)?
 *                                   TODO: add IKEA Tradfri Shortcut Button E1812
 *                                   TODO: debouncing option not initialized?
*/


def buttonDimmerVersion()   {"1.0.1"}
def buttonDimmerLibStamp() {"2023/09/01 9:36 PM"}

metadata {
    attribute "switchMode", "enum", SwitchModeOpts.options.values() as List<String> // ["dimmer", "scene"] 
    command "switchMode", [[name: "mode*", type: "ENUM", constraints: ["--- select ---"] + SwitchModeOpts.options.values() as List<String>, description: "Select dimmer or switch mode"]]
        
  	fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0004,0006,1000", outClusters:"0019,000A,0003,0004,0005,0006,0008,1000", model:"TS004F", manufacturer:"_TZ3000_xxxxxxxx", deviceJoinName: "Tuya Scene Switch TS004F"
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0003,0004,0006,1000,0000", outClusters:"0003,0004,0005,0006,0008,1000,0019,000A", model:"TS004F", manufacturer:"_TZ3000_xxxxxxxx", deviceJoinName: "Tuya Smart Knob TS004F" //KK        
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0004,0006,1000,E001", outClusters:"0019,000A,0003,0004,0006,0008,1000", model: "TS004F", manufacturer: "_TZ3000_xxxxxxxx", deviceJoinName: "MOES Smart Button (ZT-SY-SR-MS)" // MOES ZigBee IP55 Waterproof Smart Button Scene Switch & Wireless Remote Dimmer (ZT-SY-SR-MS)
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0006", outClusters:"0019,000A", model:"TS0044", manufacturer:"_TZ3000_xxxxxxxx", deviceJoinName: "Zemismart Wireless Scene Switch"          
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,000A,0001,0006", outClusters: "0019", model: "TS0044", manufacturer: "_TZ3000_xxxxxxxx", deviceJoinName: "Zemismart 4 Button Remote (ESW-0ZAA-EU)"                      // needs debouncing
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0001,0006,E000,0000", outClusters: "0019,000A", model: "TS0044", manufacturer: "_TZ3000_xxxxxxxx", deviceJoinName: "Moes 4 button controller"                                                            // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver/92823/75?u=kkossev
    
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0009,0020,1000,FC7C", outClusters:"0003,0004,0006,0008,0019,0102,1000", model:"TRADFRI on/off switch",   manufacturer:"IKEA of Sweden", deviceJoinName: "IKEA on/off switch E1743"  
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0009,0020,1000",      outClusters:"0003,0004,0006,0008,0019,0102,1000", model:"TRADFRI SHORTCUT Button", manufacturer:"IKEA of Sweden", deviceJoinName: "IKEA Tradfri Shortcut Button E1812"
	fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,1000,FC57",      outClusters: "0003,0006,0008,0019,1000",          model:"Remote Control N2",       manufacturer:"IKEA of Sweden", deviceJoinName: "IKEA STYRBAR remote control E2001"                   // (stainless)
	fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,1000,FC57,FC7C", outClusters: "0003,0005,0006,0008,0019,1000",     model:"Remote Control N2",       manufacturer:"IKEA of Sweden", deviceJoinName: "IKEA STYRBAR remote control E2002"         // (white)    // https://community.hubitat.com/t/beta-release-ikea-styrbar/82563/15?u=kkossev
    fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,0020,1000,FC7C",      outClusters:"0003,0004,0006,0008,0019,1000",      model:"RODRET Dimmer",           manufacturer:"IKEA of Sweden", deviceJoinName: "IKEA RODRET Wireless Dimmer E2201"

    
    preferences {
        //    if (deviceType in  ["Button", "ButtonDimmer"]) {
        input name: "reverseButton", type: "bool", title: "<b>Reverse button order</b>", defaultValue: true, description: '<i>Switches button order </i>'
        input name: 'debounce', type: 'enum', title: '<b>Debouncing</b>', options: DebounceOpts.options, defaultValue: DebounceOpts.defaultValue, required: true, description: '<i>Debouncing options.</i>'
    }
}

@Field static final Map SwitchModeOpts = [
    defaultValue: 1,
    options     : [0: 'dimmer', 1: 'scene']
]
@Field static final Map DebounceOpts = [
    defaultValue: 1000,
    options     : [0: 'disabled', 800: '0.8 seconds', 1000: '1.0 seconds', 1200: '1.2 seconds', 1500: '1.5 seconds', 2000: '2.0 seconds',]
]

def needsDebouncing() { (((settings.debounce  ?: 0) as int) != 0) && (device.getDataValue("model") == "TS004F" || (device.getDataValue("manufacturer") in ["_TZ3000_abci1hiu", "_TZ3000_vp6clf9d"]))}
def isIkeaShortcutButtonE1812() { device.getDataValue("model") == "TRADFRI SHORTCUT Button" }
def isIkeaStyrbar()             { device.getDataValue("model") == "Remote Control N2" }
def isIkeaRODRET()              { device.getDataValue("model") == "RODRET Dimmer" }

/*
 * -----------------------------------------------------------------------------
 *  Button/Dimmer  Scenes cluster 0x0005
 * -----------------------------------------------------------------------------
*/
void parseScenesClusterButtonDimmer(final Map descMap) {
    if (isIkeaStyrbar() || isIkeaRODRET()) {
        processStyrbarCommand(descMap)
    }
    else {
        logWarn "parseScenesClusterButtonDimmer: unprocessed Scenes cluster attribute ${descMap.attrId}"
    }
}


/*
 * ----------------------------------------------------------------------------
 *  Button/Dimmer  On/Off  cluster 0x0006
 * -----------------------------------------------------------------------------
*/
void parseOnOffClusterButtonDimmer(final Map descMap) {
    if (descMap.command in ["FC", "FD"]) {
        processTS004Fcommand(descMap)
    }
    else if (descMap.attrId == "8004") {
        processTS004Fmode(descMap)
    }
    else if (isIkeaStyrbar() || isIkeaRODRET()) {
        processStyrbarCommand(descMap)
    }
    else if (isIkeaShortcutButtonE1812() && ((descMap.clusterInt == 0x0006 || descMap.clusterInt == 0x0008) && (descMap.command in ["01","05","07" ]))) {
            // TODO !!!
            logInfo "IkeaShortcutButtonE1812 - not ready yet!"
            // IKEA Tradfri Shortcut Button E1812
            /*
            buttonNumber = 1
            if (descMap.clusterInt == 0x0006 && descMap.command == "01") buttonState = "pushed"    
            else if (descMap.clusterInt == 0x0008 && descMap.command == "05") buttonState = "held"  
            else if (descMap.clusterInt == 0x0008 && descMap.command == "07") buttonState = "released"
            else buttonState = "unknown"
            */
    }    
    else {
        logWarn "parseOnOffClusterButtonDimmer: unprocessed OnOff Cluster attribute ${descMap.attrId}"
    }    
}

/*
 * -----------------------------------------------------------------------------
 *  Button/Dimmer  LevelControl cluster 0x0008
 * -----------------------------------------------------------------------------
*/
void parseLevelControlClusterButtonDimmer(final Map descMap) {
    if (descMap.attrId == "0000" && descMap.command == "FD") {
        processTS004Fcommand(descMap)
    }
    else if (isIkeaStyrbar() || isIkeaRODRET()) {
        processStyrbarCommand(descMap)
    }
    else {
        logWarn "parseLevelControlClusterButtonDimmer: unprocessed LevelControl cluster attribute ${descMap.attrId}"
    }
}


/*
 * -----------------------------------------------------------------------------
 * Styrbar
 * -----------------------------------------------------------------------------
*/
void processStyrbarCommand(final Map descMap) {
    logDebug "processStyrbarCommand: descMap: $descMap"
    def buttonNumber = 0
    def buttonState = "unknown"
    
    if (descMap.clusterInt == 0x0006 && descMap.command == "01") {
        if (state.states["ignoreButton1"] == true) {
            logWarn "ignoring button 1 ..."
            return
        }
        else {
            def ii = state.states["ignoreButton1"]
            buttonNumber = 1
            buttonState = "pushed"
        }
    }
    else if (descMap.clusterInt == 0x0006 && descMap.command == "00") {
        buttonNumber = 4
        buttonState = "pushed"
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == "07" && ((descMap.data as String) == "[01, 01, 0D, 00]")) {
        buttonNumber = 2
        buttonState = "pushed"
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == "07" && ((descMap.data as String) == "[00, 01, 0D, 00]")) {
        buttonNumber = 3
        buttonState = "pushed"
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == "09" && ((descMap.data as String) == "[00, 00]")) {
        // TODO !!
        logWarn "button 2 or button 3 was held!"
        state.states["ignoreButton1"] = true
        runInMillis(DebounceOpts.defaultValue as int, ignoreButton1, [overwrite: true])
        return
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == "08" && ((descMap.data as String) == "[01, 0D, 00]")) {
        buttonNumber = 2
        buttonState = "held"
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == "08" && ((descMap.data as String) == "[00, 0D, 00]")) {
        buttonNumber = 3
        buttonState = "held"
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == "09") {
        buttonNumber = state.states["lastButtonNumber"] ?: 5
        buttonState = "released"
    }
    else if (descMap.clusterInt == 0x0008 && descMap.command == "05") {
        buttonNumber = 1
        buttonState = "held"
    }
    else if (descMap.clusterInt == 0x0008 && descMap.command == "01") {
        buttonNumber = 4
        buttonState = "held"
    }
    else if (descMap.clusterInt == 0x0008 && descMap.command == "07") {
        buttonNumber = state.states["lastButtonNumber"] ?: 5
        buttonState = "released"
    }
    
    else {
        logWarn "processStyrbarCommand: unprocessed event from cluster ${descMap.clusterInt} command ${descMap.command } sourceEndpoint ${descMap.sourceEndpoint} data = ${descMap?.data}"
        return
    } 
   
    
    if (buttonNumber != 0 ) {
        if (needsDebouncing()) {
            if ((state.states["lastButtonNumber"] ?: 0) == buttonNumber ) {    // debouncing timer still active!
                logWarn "ignored event for button ${state.states['lastButtonNumber']} - still in the debouncing time period!"
                runInMillis((settings.debounce ?: DebounceOpts.defaultValue) as int, buttonDebounce, [overwrite: true])    // restart the debouncing timer again
                logDebug "restarted debouncing timer ${settings.debounce ?: DebounceOpts.defaultValue}ms for button ${buttonNumber} (lastButtonNumber=${state.states['lastButtonNumber']})"
                return
            }
        }
        state.states["lastButtonNumber"] = buttonNumber
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  lastButtonNumber=${state.states['lastButtonNumber']}"
    }
    if (buttonState != "unknown" && buttonNumber != 0) {
        def descriptionText = "button $buttonNumber was $buttonState"
	    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
        logInfo "${descriptionText}"
		sendEvent(event)
        if (needsDebouncing()) {
            runInMillis((settings.debounce ?: DebounceOpts.defaultValue) as int, buttonDebounce, [overwrite: true])
        }
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  buttonState=${buttonState}"
    }



}

def ignoreButton1() {
    logDebug "ignoreButton1 for button ${state.states['lastButtonNumber']} expired."
    state.states["ignoreButton1"] = false
}




void processTS004Fcommand(final Map descMap) {
    logDebug "processTS004Fcommand: descMap: $descMap"
    def buttonNumber = 0
    def buttonState = "unknown"
    Boolean reverseButton = settings.reverseButton ?: false
    // when TS004F initialized in Scene switch mode!
    if (descMap.clusterInt == 0x0006 && descMap.command == "FD") {
        if (descMap.sourceEndpoint == "03") {
     	    buttonNumber = reverseButton==true ? 3 : 1
        }
        else if (descMap.sourceEndpoint == "04") {
      	    buttonNumber = reverseButton==true  ? 4 : 2
        }
        else if (descMap.sourceEndpoint == "02") {
            buttonNumber = reverseButton==true  ? 2 : 3
        }
        else if (descMap.sourceEndpoint == "01") {
       	    buttonNumber = reverseButton==true  ? 1 : 4
        }
	    else if (descMap.sourceEndpoint == "05") {    // LoraTap TS0046
   	        buttonNumber = reverseButton==true  ? 5 : 5
        }
        else if (descMap.sourceEndpoint == "06") {
       	    buttonNumber = reverseButton==true  ? 6 : 6
        }            
        if (descMap.data[0] == "00") {
            buttonState = "pushed"
        }
        else if (descMap.data[0] == "01") {
            buttonState = "doubleTapped"
        }
        else if (descMap.data[0] == "02") {
            buttonState = "held"
        }
        else {
            logWarn "unknown data in event from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint} data[0] = ${descMap.data[0]}"
            return
        } 
    } // if command == "FD"}
    else if (descMap.clusterInt == 0x0006 && descMap.command == "FC") {
        // Smart knob
        if (descMap.data[0] == "00") {            // Rotate one click right
            buttonNumber = 2
        }
        else if (descMap.data[0] == "01") {       // Rotate one click left
            buttonNumber = 3
        }
        buttonState = "pushed"
    }
    else {
        logWarn "processTS004Fcommand: unprocessed command"
        return
    }
    if (buttonNumber != 0 ) {
        if (needsDebouncing()) {
            if ((state.states["lastButtonNumber"] ?: 0) == buttonNumber ) {    // debouncing timer still active!
                logWarn "ignored event for button ${state.states['lastButtonNumber']} - still in the debouncing time period!"
                runInMillis((settings.debounce ?: DebounceOpts.defaultValue) as int, buttonDebounce, [overwrite: true])    // restart the debouncing timer again
                logDebug "restarted debouncing timer ${settings.debounce ?: DebounceOpts.defaultValue}ms for button ${buttonNumber} (lastButtonNumber=${state.states['lastButtonNumber']})"
                return
            }
        }
        state.states["lastButtonNumber"] = buttonNumber
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  lastButtonNumber=${state.states['lastButtonNumber']}"
    }
    if (buttonState != "unknown" && buttonNumber != 0) {
        def descriptionText = "button $buttonNumber was $buttonState"
	    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
        logInfo "${descriptionText}"
		sendEvent(event)
        if (needsDebouncing()) {
            runInMillis((settings.debounce ?: DebounceOpts.defaultValue) as int, buttonDebounce, [overwrite: true])
        }
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  buttonState=${buttonState}"
    }
}

void processTS004Fmode(final Map descMap) {
    if (descMap.value == "00") {
        sendEvent(name: "switchMode", value: "dimmer", isStateChange: true) 
        logInfo "mode is <b>dimmer</b>"
    }
    else if (descMap.value == "01") {
        sendEvent(name: "switchMode", value: "scene", isStateChange: true)
        logInfo "mode is <b>scene</b>"
    }
    else {
        logWarn "TS004F unknown attrId ${descMap.attrId} value ${descMap.value}"
    }
}


def buttonDebounce(/*button*/) {
    logDebug "debouncing timer (${settings.debounce}) for button ${state.states['lastButtonNumber']} expired."
    state.states["lastButtonNumber"] = 0
}

def switchToSceneMode()
{
    logInfo "switching TS004F into Scene mode"
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01))
}

def switchToDimmerMode()
{
    logInfo "switching TS004F into Dimmer mode"
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x00))
}

def switchMode( mode ) {
    if (mode == "dimmer") {
        switchToDimmerMode()
    }
    else if (mode == "scene") {
        switchToSceneMode()
    }
}



void processTuyaDpButtonDimmer(descMap, dp, dp_id, fncmd) {

    switch (dp) {
        case 0x01 : // on/off
            sendSwitchEvent(fncmd)
            break
        case 0x02 :
            logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            break
        case 0x04 : // battery
            sendBatteryPercentageEvent(fncmd)
            break
        default :
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            break            
    }
}


def refreshButtonDimmer() {
    List<String> cmds = []
    logDebug "refreshButtonDimmer() (n/a) : ${cmds} "
    // TODO !! 
    if (cmds == []) { cmds = ["delay 299"] }
    return cmds
}

def configureDeviceButtonDimmer() {
    List<String> cmds = []
/*    
    def mode = settings.fingerbotMode != null ? settings.fingerbotMode : FingerbotModeOpts.defaultValue
    //logWarn "mode=${mode}  settings=${(settings.fingerbotMode)}"
    logDebug "setting fingerbotMode to ${FingerbotModeOpts.options[mode as int]} (${mode})"
    cmds = sendTuyaCommand("65", DP_TYPE_BOOL, zigbee.convertToHexString(mode as int, 2) )
*/
    
    logDebug "configureDeviceButtonDimmer() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299"] }    // no , 
    return cmds    
}

def initializeDeviceButtonDimmer()
{
    List<String> cmds = []
    int intMinTime = 300
    int intMaxTime = 3600

    cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8 /*0x20*/ /* data type*/, intMinTime, intMaxTime, 0x01, [:], delay=141)    // OK
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}", "delay 142", ]            // binding is OK            //  Configuring 0x0006 : -> configure reporting error: Unsupported Attribute [86, 00, 00, 00]
    //error: //cmds += zigbee.configureReporting(0x0020, 0x0000, DataType.INT16 /*0x20*/ /* data type*/, intMinTime, intMaxTime, 0x01, [:], delay=143)    // zigbee configure reporting error: Invalid Data Type [8D, 00, 00, 00]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}", "delay 144", ]            // binding is OK - reporting configuration is not supported
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0005 {${device.zigbeeId}} {}", "delay 145", ]
    

    logDebug "initializeDeviceButtonDimmer() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299",] }
    return cmds        
}


void initVarsButtonDimmer(boolean fullInit=false) {
    logDebug "initVarsButtonDimmer(${fullInit})"
    if (fullInit || settings?.debounce == null) device.updateSetting('debounce', [value: DebounceOpts.defaultValue.toString(), type: 'enum'])
    if (fullInit || settings?.reverseButton == null) device.updateSetting("reverseButton", true)
    if (state.states == null) { state.states = [:] } 
    state.states["ignoreButton1"] = false
}

void initEventsButtonDimmer(boolean fullInit=false) {
    def numberOfButtons = 0
    def supportedValues = []
    if (isIkeaShortcutButtonE1812()) {
        numberOfButtons = 1
        supportedValues = ["pushed", "held", "released"]
    } 
    else if (isIkeaRODRET()) {
        numberOfButtons = 2
        supportedValues = ["pushed", "held", "released"]
    }
    else if (isIkeaStyrbar()) {
        numberOfButtons = 4
        supportedValues = ["pushed", "held", "released"]
    } 
    if (numberOfButtons != 0) {
        sendNumberOfButtonsEvent(numberOfButtons)
        sendSupportedButtonValuesEvent(supportedValues)
    }
    
}



