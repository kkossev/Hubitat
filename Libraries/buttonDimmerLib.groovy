library (
    base: 'driver',
    author: 'Krassimir Kossev',
    category: 'zigbee',
    description: 'Zigbee Button Dimmer Library',
    name: 'buttonDimmerLib',
    namespace: 'kkossev',
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/main/libraries/buttonDimmerLib.groovy',
    version: '1.0.1',
    documentationLink: ''
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
 * ver. 1.0.1  2023-09-02 kkossev  - (dev.branch) added TRADFRI on/off switch E1743; added "IKEA remote control E1810"; added IKEA TRADFRI SHORTCUT Button E1812; debounce disabled by default;
 * ver. 1.0.2  2023-09-06 kkossev  - (dev.branch) bugfix - Styrbar ignore button 1; IKEA RODRET E2201  key #4 changed to key #2; added "IKEA SYMFONISK Sound Controller E1744"; level control; RODRET Hold/Release button #2; added IKEA TRADFRI open/close remote E1766
 *
 *                                   TODO: turn switch on/off when changing from 0%
 *                                   TODO: Battery setting to be DEFAULT (no binding) for IKEA remotes!
 *                                   TODO: write ZCL Version to device data
 *                                   TODO: verify Ikea reporting configuration (WireShark) !
 *                                   TODO: battery options  (pairing)
 *                                   TODO: increase level on Up/Down key presses  (left-right rotation)  (simulation)
 *                                   TODO: STYRBAR - battery not repored (bind power cluster)?
 *                                   TODO: add KNYCKLAN Open/Close remote    // https://zigbee.blakadder.com/Ikea_E1841.html
 *                                   TODO: add TRADFRI wireless dimmer       // https://zigbee.blakadder.com/Ikea_ICTC-G-1.html
*/

def buttonDimmerVersion()   { '1.0.2' }
def buttonDimmerLibStamp() { '2023/09/06 8:10 AM' }

metadata {
    capability 'Switch'    // IKEA remote control E1810 - central button
    attribute 'switchMode', 'enum', SwitchModeOpts.options.values() as List<String> // ["dimmer", "scene"]
    command 'switchMode', [[name: 'mode*', type: 'ENUM', constraints: ['--- select ---'] + SwitchModeOpts.options.values() as List<String>, description: 'Select dimmer or switch mode']]
    if (_DEBUG) { command 'testBD', [[name: 'testBD', type: 'STRING', description: 'testBD', defaultValue : '']]  }

    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0004,0006,1000', outClusters:'0019,000A,0003,0004,0005,0006,0008,1000', model:'TS004F', manufacturer:'_TZ3000_xxxxxxxx', deviceJoinName: 'Tuya Scene Switch TS004F'
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0004,0006,1000,0000', outClusters:'0003,0004,0005,0006,0008,1000,0019,000A', model:'TS004F', manufacturer:'_TZ3000_xxxxxxxx', deviceJoinName: 'Tuya Smart Knob TS004F' //KK
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0004,0006,1000,E001', outClusters:'0019,000A,0003,0004,0006,0008,1000', model: 'TS004F', manufacturer: '_TZ3000_xxxxxxxx', deviceJoinName: 'MOES Smart Button (ZT-SY-SR-MS)' // MOES ZigBee IP55 Waterproof Smart Button Scene Switch & Wireless Remote Dimmer (ZT-SY-SR-MS)
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0006', outClusters:'0019,000A', model:'TS0044', manufacturer:'_TZ3000_xxxxxxxx', deviceJoinName: 'Zemismart Wireless Scene Switch'
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,000A,0001,0006', outClusters: '0019', model: 'TS0044', manufacturer: '_TZ3000_xxxxxxxx', deviceJoinName: 'Zemismart 4 Button Remote (ESW-0ZAA-EU)'                      // needs debouncing
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0006,E000,0000', outClusters: '0019,000A', model: 'TS0044', manufacturer: '_TZ3000_xxxxxxxx', deviceJoinName: 'Moes 4 button controller'                                                            // https://community.hubitat.com/t/release-tuya-scene-switch-ts004f-driver/92823/75?u=kkossev

    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0009,0020,1000,FC7C', outClusters:'0003,0004,0006,0008,0019,0102,1000', model:'TRADFRI on/off switch',      manufacturer:'IKEA of Sweden', deviceJoinName: 'IKEA on/off switch E1743'
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,1000,FC57,FC7C', outClusters:'0003,0004,0005,0006,0008,0019,1000', model:'TRADFRI remote control',     manufacturer:'IKEA of Sweden', deviceJoinName: 'IKEA remote control E1810'
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0009,0020,1000',      outClusters:'0003,0004,0006,0008,0019,0102,1000', model:'TRADFRI SHORTCUT Button',    manufacturer:'IKEA of Sweden', deviceJoinName: 'IKEA TRADFRI SHORTCUT Button E1812'
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,1000,FC57',      outClusters:'0003,0006,0008,0019,1000',           model:'Remote Control N2',          manufacturer:'IKEA of Sweden', deviceJoinName: 'IKEA STYRBAR remote control E2001'         // (stainless)
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,1000,FC57,FC7C', outClusters:'0003,0005,0006,0008,0019,1000',      model:'Remote Control N2',          manufacturer:'IKEA of Sweden', deviceJoinName: 'IKEA STYRBAR remote control E2002'         // (white)    // https://community.hubitat.com/t/beta-release-ikea-styrbar/82563/15?u=kkossev
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,1000,FC7C',      outClusters:'0003,0004,0006,0008,0019,1000',      model:'RODRET Dimmer',              manufacturer:'IKEA of Sweden', deviceJoinName: 'IKEA RODRET Wireless Dimmer E2201'
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0009,0020,1000,FC7C', outClusters:'0003,0004,0006,0008,0019,0102,1000', model:'TRADFRI open/close remote',  manufacturer:'IKEA of Sweden', deviceJoinName: 'IKEA TRADFRI open/close remote E1766'      // https://community.hubitat.com/t/compability-for-ikea/123672/22?u=kkossev
    fingerprint profileId:'0104', endpointId:'01', inClusters:'0000,0001,0003,0020,1000,FC7C',      outClusters:'0003,0004,0005,0006,0008,0019,1000', model:'SYMFONISK Sound Controller', manufacturer:'IKEA of Sweden', deviceJoinName: 'IKEA SYMFONISK Sound Controller E1744'

    preferences {
        input name: 'reverseButton', type: 'bool', title: '<b>Reverse button order</b>', defaultValue: true, description: '<i>Switches button order </i>'
        input name: 'debounce', type: 'enum', title: '<b>Debouncing</b>', options: DebounceOpts.options, defaultValue: DebounceOpts.defaultValue, required: true, description: '<i>Debouncing options.</i>'
        input name: 'dimmerStep', type: 'enum', title: '<b>Dimmer step</b>', options: DimmerStepOpts.options, defaultValue: DimmerStepOpts.defaultValue, required: true, description: '<i>Level change in percent</i>'
    }
}

@Field static final Map SwitchModeOpts = [
    defaultValue: 1,
    options     : [0: 'dimmer', 1: 'scene']
]
@Field static final Map DebounceOpts = [
    defaultValue: 0,
    options     : [0: 'disabled', 500: '0.5 seconds', 800: '0.8 seconds', 1000: '1.0 seconds', 1200: '1.2 seconds', 1500: '1.5 seconds', 2000: '2.0 seconds',]
]

@Field static final Map DimmerStepOpts = [
    defaultValue: 10,
    options     : [1: '1 %', 2: '2 %', 5: '5 %', 10: '10 %', 15: '15%', 20: '20%', 25: '25%']
]

@Field static final Integer STYRBAR_IGNORE_TIMER = 1500
@Field static final Integer SOUND_RELEASE_TIMER = 850

def needsDebouncing()           { (settings.debounce  ?: 0) as int != 0 }
def isIkeaOnOffSwitch()         { device.getDataValue('model') == 'TRADFRI on/off switch' }
def isIkeaRemoteControl()       { device.getDataValue('model') == 'TRADFRI remote control' }
def isIkeaShortcutButtonE1812() { device.getDataValue('model') == 'TRADFRI SHORTCUT Button' }
def isIkeaStyrbar()             { device.getDataValue('model') == 'Remote Control N2' }
def isIkeaRODRET()              { device.getDataValue('model') == 'RODRET Dimmer' }
def isIkeaOpenCloseRemote()     { device.getDataValue('model') == 'TRADFRI open/close remote' }
def isIkeaSoundController()     { device.getDataValue('model') == 'SYMFONISK Sound Controller' }

/*
 * -----------------------------------------------------------------------------
 *  Button/Dimmer  Scenes cluster 0x0005
 * -----------------------------------------------------------------------------
*/
void parseScenesClusterButtonDimmer(final Map descMap) {
    if (isIkeaStyrbar() || isIkeaRODRET() || isIkeaRemoteControl()) {
        processIkeaCommand(descMap)
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
    if (descMap.command in ['FC', 'FD']) {
        processTS004Fcommand(descMap)
    }
    else if (descMap.attrId == '8004') {
        processTS004Fmode(descMap)
    }
    else if (isIkeaStyrbar() || isIkeaRODRET() || isIkeaOnOffSwitch() || isIkeaRemoteControl() || isIkeaShortcutButtonE1812() || isIkeaSoundController()) {
        processIkeaCommand(descMap)
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
    if (descMap.attrId == '0000' && descMap.command == 'FD') {
        processTS004Fcommand(descMap)
    }
    else if (isIkeaStyrbar() || isIkeaRODRET() || isIkeaOnOffSwitch() || isIkeaRemoteControl() || isIkeaShortcutButtonE1812() || isIkeaSoundController()) {
        processIkeaCommand(descMap)
    }
    else {
        logWarn "parseLevelControlClusterButtonDimmer: unprocessed LevelControl cluster attribute ${descMap.attrId}"
    }
}

/*
 * -----------------------------------------------------------------------------
 *  Button/Dimmer  Window Covering cluster 0x0102
 * -----------------------------------------------------------------------------
*/
void parseWindowCoveringClusterButtonDimmer(final Map descMap) {
    /* groovylint-disable-next-line ConstantIfExpression */
    if (true/*isIkeaOpenCloseRemote()*/ ) {
        processIkeaWindowCoveringCluster(descMap)
    }
    else {
        logWarn "parseWindowCoveringClusterButtonDimmer: unprocessed Window Covering cluster command ${descMap.command}"
    }
}

/*
 * -----------------------------------------------------------------------------
 * IKEA buttons and remotes handler - c Window Covering cluster 0x0102
 * -----------------------------------------------------------------------------
*/
void processIkeaWindowCoveringCluster(final Map descMap) {
    logDebug "processIkeaWindowCoveringCluster: descMap: $descMap"
    def buttonNumber = 0
    def buttonState = 'unknown'

    if (descMap.clusterInt == 0x0102 && descMap.command == '00') {
        buttonNumber = 1
        buttonState = 'pushed'
    }
    else if (descMap.clusterInt == 0x0102 && descMap.command == '01') {
        buttonNumber = 2
        buttonState = 'pushed'
    }
    else if (descMap.clusterInt == 0x0102 && descMap.command == '02') {
        buttonNumber = state.states['lastButtonNumber'] ?: 5
        buttonState = 'released'
    }
    else {
        logWarn "processIkeaWindowCoveringCluster: unprocessed event from cluster ${descMap.clusterInt} command ${descMap.command } sourceEndpoint ${descMap.sourceEndpoint} data = ${descMap?.data}"
        return
    }

    if (buttonNumber != 0 ) {
        if (needsDebouncing()) {
            if (((state.states['lastButtonNumber'] ?: 0) == buttonNumber) && (state.states['debouncingActive'] == true)) {    // debouncing timer still active!
                logWarn "ignored event for button ${state.states['lastButtonNumber']} - still in the debouncing time period!"
                startButtonDebounce()
                logDebug "restarted debouncing timer ${settings.debounce ?: DebounceOpts.defaultValue}ms for button ${buttonNumber} (lastButtonNumber=${state.states['lastButtonNumber']})"
                return
            }
        }
        state.states['lastButtonNumber'] = buttonNumber
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  lastButtonNumber=${state.states['lastButtonNumber']}"
    }
    if (buttonState != 'unknown' && buttonNumber != 0) {
        def descriptionText = "button $buttonNumber was $buttonState"
        def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
        logInfo "${descriptionText}"
        sendEvent(event)
        if (needsDebouncing()) {
            startButtonDebounce()
        }
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  buttonState=${buttonState}"
    }
}

/*
 * -----------------------------------------------------------------------------
 * IKEA buttons and remotes handler - clusters 5, 6 and 8
 * -----------------------------------------------------------------------------
*/
void processIkeaCommand(final Map descMap) {
    logDebug "processIkeaCommand: descMap: $descMap"
    def buttonNumber = 0
    def buttonState = 'unknown'

    if (descMap.clusterInt == 0x0006 && descMap.command == '01') {
        if (state.states['ignoreButton1'] == true) {
            logWarn 'ignoring button 1 ...'
            return
        }
        else {
            //def ii = state.states["ignoreButton1"]
            buttonNumber = 1
            buttonState = 'pushed'
        }
    }
    else if (descMap.clusterInt == 0x0006 && descMap.command == '00') {
        buttonNumber = (isIkeaOnOffSwitch() || isIkeaRODRET() ) ? 2 : 4
        buttonState = 'pushed'
    }
    else if (descMap.clusterInt == 0x0006 && descMap.command == '02') {
        // IKEA remote control E1810 - central button
        toggle()
        buttonNumber = isIkeaSoundController() ? 1 : 5
        buttonState = 'pushed'
    }
    // cluster 5
    else if (descMap.clusterInt == 0x0005 && descMap.command == '07' && ((descMap.data as String) == '[01, 01, 0D, 00]')) {
        buttonNumber = 2
        buttonState = 'pushed'
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == '07' && ((descMap.data as String) == '[00, 01, 0D, 00]')) {
        buttonNumber = 3
        buttonState = 'pushed'
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == '09' && ((descMap.data as String) == '[00, 00]')) {
        // TODO !!
        logWarn 'button 2 or button 3 was held!'
        state.states['ignoreButton1'] = true
        runInMillis(STYRBAR_IGNORE_TIMER, ignoreButton1, [overwrite: true])
        return
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == '08' && ((descMap.data as String) == '[01, 0D, 00]')) {
        buttonNumber = 2
        buttonState = 'held'
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == '08' && ((descMap.data as String) == '[00, 0D, 00]')) {
        buttonNumber = 3
        buttonState = 'held'
    }
    else if (descMap.clusterInt == 0x0005 && descMap.command == '09') {
        buttonNumber = state.states['lastButtonNumber'] ?: 5
        buttonState = 'released'
    }
    // cluster 8
    else if (descMap.clusterInt == 0x0008 && descMap.command == '01') {
        if (isIkeaSoundController()) {
            def data2 = descMap.data[0] + descMap.data[1]
            if (data2 == '00C3') {
                buttonNumber = 2
                levelUp()
            }
            else if (data2 == '01C3') {
                buttonNumber = 3
                levelDn()
            }
            else {
                logWarn 'unprocessed!'
                return
            }
            buttonState = 'pushed'
            restartSoundReleaseTimer()
        }
        else {
            buttonNumber = (isIkeaOnOffSwitch() || isIkeaRODRET()) ? 2 : 4
            buttonState = 'held'
        }
    }
    else if (descMap.clusterInt == 0x0008 && descMap.command == '02') {
        if (isIkeaSoundController()) {
            buttonNumber = 1
            buttonState = 'doubleTapped'
        }
        else {
            buttonNumber = 4  // remote
            buttonState = 'pushed'
        }
    }
    else if (descMap.clusterInt == 0x0008 && descMap.command == '05') {
        buttonNumber = 1
        buttonState = 'held'
    }
    else if (descMap.clusterInt == 0x0008 && descMap.command == '06') {
        buttonNumber = 1   // remote
        buttonState = 'pushed'
    }
    else if (descMap.clusterInt == 0x0008 && descMap.command in ['07', '03']) {
        if (isIkeaSoundController()) {
            /*
            def data2 = descMap.data[0] + descMap.data[1]
            buttonNumber = state.states["lastButtonNumber"] ?: 5
            buttonState = "released"
            */
            logDebug 'ignored IkeaSoundController release event'
            restartSoundReleaseTimer()
            return
        }
        else {
            buttonNumber = state.states['lastButtonNumber'] ?: 5
            buttonState = 'released'
        }
    }
    else {
        logWarn "processIkeaCommand: unprocessed event from cluster ${descMap.clusterInt} command ${descMap.command } sourceEndpoint ${descMap.sourceEndpoint} data = ${descMap?.data}"
        return
    }

    if (buttonNumber != 0 ) {
        if (needsDebouncing()) {
            if (((state.states['lastButtonNumber'] ?: 0) == buttonNumber) && (state.states['debouncingActive'] == true)) {    // debouncing timer still active!
                logWarn "ignored event for button ${state.states['lastButtonNumber']} - still in the debouncing time period!"
                startButtonDebounce()
                logDebug "restarted debouncing timer ${settings.debounce ?: DebounceOpts.defaultValue}ms for button ${buttonNumber} (lastButtonNumber=${state.states['lastButtonNumber']})"
                return
            }
        }
        state.states['lastButtonNumber'] = buttonNumber
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  lastButtonNumber=${state.states['lastButtonNumber']}"
    }
    if (buttonState != 'unknown' && buttonNumber != 0) {
        def descriptionText = "button $buttonNumber was $buttonState"
        def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
        logInfo "${descriptionText}"
        sendEvent(event)
        if (needsDebouncing()) {
            startButtonDebounce()
        }
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  buttonState=${buttonState}"
    }
}

def startButtonDebounce() {
    logDebug "starting timer (${settings.debounce}) for button ${state.states['lastButtonNumber']}"
    runInMillis((settings.debounce ?: DebounceOpts.defaultValue) as int, clearButtonDebounce, [overwrite: true])    // restart the debouncing timer again
    state.states['debouncingActive'] = true
}

def clearButtonDebounce() {
    logDebug "debouncing timer (${settings.debounce}) for button ${state.states['lastButtonNumber']} expired."
    //state.states["lastButtonNumber"] = 0
    state.states['debouncingActive'] = false
}

def ignoreButton1() {
    logDebug "ignoreButton1 for button ${state.states['lastButtonNumber']} expired."
    state.states['ignoreButton1'] = false
}

def restartSoundReleaseTimer() {
    runInMillis(SOUND_RELEASE_TIMER, soundReleaseEvent, [overwrite: true])
    state.states['debouncingActive'] = true
}

def soundReleaseEvent() {
    unschedule(soundReleaseEvent)
    def buttonNumber = state.states['lastButtonNumber'] ?: 5
    def buttonState = 'released'
    def descriptionText = "button $buttonNumber was $buttonState"
    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
    logInfo "${descriptionText}"
    sendEvent(event)
    state.states['debouncingActive'] = false
}

def setLevelButtonDimmer(level, transitionTime) {
    sendDigitalLevelEvent(level)
}

void levelUp() {
    Integer level = (device.currentValue('level') ?: 0 ) as int
    level = level + (settings.dimmerStep as int)
    if (level > 100) level = 100
    sendDigitalLevelEvent(level)
}

void levelDn() {
    Integer level = (device.currentValue('level') ?: 0 ) as int
    level = level - (settings.dimmerStep as int)
    if (level < 0) level = 0
    sendDigitalLevelEvent(level)
}

void sendDigitalLevelEvent(level) {
    Boolean oldIsDigital = state.states['isDigital'] ?: true
    state.states['isDigital'] = true
    sendLevelControlEvent(level)
    state.states['isDigital'] = oldIsDigital
}

void processTS004Fcommand(final Map descMap) {
    logDebug "processTS004Fcommand: descMap: $descMap"
    def buttonNumber = 0
    def buttonState = 'unknown'
    Boolean reverseButton = settings.reverseButton ?: false
    // when TS004F initialized in Scene switch mode!
    if (descMap.clusterInt == 0x0006 && descMap.command == 'FD') {
        if (descMap.sourceEndpoint == '03') {
            buttonNumber = reverseButton == true ? 3 : 1
        }
        else if (descMap.sourceEndpoint == '04') {
            buttonNumber = reverseButton == true  ? 4 : 2
        }
        else if (descMap.sourceEndpoint == '02') {
            buttonNumber = reverseButton == true  ? 2 : 3
        }
        else if (descMap.sourceEndpoint == '01') {
            buttonNumber = reverseButton == true  ? 1 : 4
        }
        else if (descMap.sourceEndpoint == '05') {    // LoraTap TS0046
            buttonNumber = reverseButton == true  ? 5 : 5
        }
        else if (descMap.sourceEndpoint == '06') {
            buttonNumber = reverseButton == true  ? 6 : 6
        }
        if (descMap.data[0] == '00') {
            buttonState = 'pushed'
        }
        else if (descMap.data[0] == '01') {
            buttonState = 'doubleTapped'
        }
        else if (descMap.data[0] == '02') {
            buttonState = 'held'
        }
        else {
            logWarn "unknown data in event from cluster ${descMap.clusterInt} sourceEndpoint ${descMap.sourceEndpoint} data[0] = ${descMap.data[0]}"
            return
        }
    } // if command == "FD"}
    else if (descMap.clusterInt == 0x0006 && descMap.command == 'FC') {
        // Smart knob
        if (descMap.data[0] == '00') {            // Rotate one click right
            buttonNumber = 2
        }
        else if (descMap.data[0] == '01') {       // Rotate one click left
            buttonNumber = 3
        }
        buttonState = 'pushed'
    }
    else {
        logWarn 'processTS004Fcommand: unprocessed command'
        return
    }
    if (buttonNumber != 0 ) {
        if (needsDebouncing()) {
            if ((state.states['lastButtonNumber'] ?: 0) == buttonNumber ) {    // debouncing timer still active!
                logWarn "ignored event for button ${state.states['lastButtonNumber']} - still in the debouncing time period!"
                startButtonDebounce()
                logDebug "restarted debouncing timer ${settings.debounce ?: DebounceOpts.defaultValue}ms for button ${buttonNumber} (lastButtonNumber=${state.states['lastButtonNumber']})"
                return
            }
        }
        state.states['lastButtonNumber'] = buttonNumber
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  lastButtonNumber=${state.states['lastButtonNumber']}"
    }
    if (buttonState != 'unknown' && buttonNumber != 0) {
        def descriptionText = "button $buttonNumber was $buttonState"
        def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
        logInfo "${descriptionText}"
        sendEvent(event)
        if (needsDebouncing()) {
            startButtonDebounce()
        }
    }
    else {
        logWarn "UNHANDLED event for button ${buttonNumber},  buttonState=${buttonState}"
    }
}

void processTS004Fmode(final Map descMap) {
    if (descMap.value == '00') {
        sendEvent(name: 'switchMode', value: 'dimmer', isStateChange: true)
        logInfo 'mode is <b>dimmer</b>'
    }
    else if (descMap.value == '01') {
        sendEvent(name: 'switchMode', value: 'scene', isStateChange: true)
        logInfo 'mode is <b>scene</b>'
    }
    else {
        logWarn "TS004F unknown attrId ${descMap.attrId} value ${descMap.value}"
    }
}

def switchToSceneMode() {
    logInfo 'switching TS004F into Scene mode'
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x01))
}

def switchToDimmerMode() {
    logInfo 'switching TS004F into Dimmer mode'
    sendZigbeeCommands(zigbee.writeAttribute(0x0006, 0x8004, 0x30, 0x00))
}

def switchMode( mode ) {
    if (mode == 'dimmer') {
        switchToDimmerMode()
    }
    else if (mode == 'scene') {
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
    if (cmds == []) { cmds = ['delay 299'] }
    return cmds
}

def configureDeviceButtonDimmer() {
    List<String> cmds = []
    // TODO !!
    logDebug "configureDeviceButtonDimmer() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299'] }    // no ,
    return cmds
}

def initializeDeviceButtonDimmer() {
    List<String> cmds = []
    int intMinTime = 300
    int intMaxTime = 14400    // 4 hours reporting period for the battery

    cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8 /*0x20*/ /* data type*/, intMinTime, intMaxTime, 0x01, [:], delay = 141)    // OK
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 142', ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}", 'delay 144', ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0005 {${device.zigbeeId}} {}", 'delay 145', ]

    logDebug "initializeDeviceButtonDimmer() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299',] }
    return cmds
}

void initVarsButtonDimmer(boolean fullInit=false) {
    logDebug "initVarsButtonDimmer(${fullInit})"
    def debounceDefault = ((device.getDataValue('model') ?: 'n/a') == 'TS004F' || ((device.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3000_abci1hiu', '_TZ3000_vp6clf9d'])) ?  '1000' : '0'
    if (fullInit || settings?.debounce == null) device.updateSetting('debounce', [value: debounceDefault, type: 'enum'])
    if (fullInit || settings?.reverseButton == null) device.updateSetting('reverseButton', true)
    if (fullInit || settings?.dimmerStep == null) device.updateSetting('dimmerStep', [value: DimmerStepOpts.defaultValue.toString(), type: 'enum'])
    if (state.states == null) { state.states = [:] }
    state.states['ignoreButton1'] = false
    state.states['debouncingActive'] = false
}

void initEventsButtonDimmer(boolean fullInit=false) {
    def numberOfButtons = 0
    def supportedValues = []
    if (isIkeaShortcutButtonE1812()) {
        numberOfButtons = 1
        supportedValues = ['pushed', 'held', 'released']
    }
    else if (isIkeaRODRET() || isIkeaOnOffSwitch()) {
        numberOfButtons = 2
        supportedValues = ['pushed', 'held', 'released']
    }
    else if (isIkeaSoundController()) {
        numberOfButtons = 3
        supportedValues = ['pushed', 'held', 'released']
    }
    else if (isIkeaStyrbar()) {
        numberOfButtons = 4
        supportedValues = ['pushed', 'held', 'released']
    }
    else if (isIkeaRemoteControl()) {
        numberOfButtons = 5
        supportedValues = ['pushed', 'held', 'released']
    }
    if (numberOfButtons != 0) {
        sendNumberOfButtonsEvent(numberOfButtons)
        sendSupportedButtonValuesEvent(supportedValues)
    }
}

def testBD(par) {
    levelDn()
}
