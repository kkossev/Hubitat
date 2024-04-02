/* groovylint-disable NglParseError, ImplicitReturnStatement, InsecureRandom, MethodReturnTypeRequired, MethodSize, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnusedImport, VariableName *//**
 *  Tuya Zigbee Button Dimmer - driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
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
 * ver. 2.0.5  2023-07-02 kkossev  - Tuya Zigbee Button Dimmer: added Debounce option; added VoltageToPercent option for battery; added reverseButton option; healthStatus bug fix; added  Zigbee Groups' command; added switch moode (dimmer/scene) for TS004F
 * ver. 2.1.4  2023-09-06 kkossev  - buttonDimmerLib library; added IKEA Styrbar E2001/E2002, IKEA on/off switch E1743, IKEA remote control E1810; added Identify cluster; Ranamed 'Zigbee Button Dimmer'; bugfix - Styrbar ignore button 1; IKEA RODRET E2201  key #4 changed to key #2; added IKEA TRADFRI open/close remote E1766
 * ver. 3.0.4  2024-04-01 kkossev  - (dev. branch) commonLib 3.0.4; added 'Schneider Electric WDE002924'
 *
 *                                   TODO:
 */

static String version() { "3.0.4" }
static String timeStamp() {"2024/04/01 11:13 PM"}

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput



deviceType = "ButtonDimmer"
@Field static final String DEVICE_TYPE = "ButtonDimmer"

metadata {
    definition (
        //name: 'Tuya Zigbee Device',
        name: 'Zigbee Button Dimmer',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20Button%20Dimmer/Zigbee_Button_Dimmer_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {

        capability 'Configuration'
        capability 'Refresh'
        capability 'Health Check'
        capability 'Switch'    // IKEA remote control E1810 - central button
        capability 'Battery'
        capability 'SwitchLevel'
        capability 'PushableButton'
        capability 'DoubleTapableButton'
        capability 'HoldableButton'
        capability 'ReleasableButton'

        attribute 'switchMode', 'enum', SwitchModeOpts.options.values() as List<String> // ["dimmer", "scene"]
        attribute 'batteryVoltage', 'number'

        command 'switchMode', [[name: 'mode*', type: 'ENUM', constraints: ['--- select ---'] + SwitchModeOpts.options.values() as List<String>, description: 'Select dimmer or switch mode']]
        command 'zigbeeGroups', [
            [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>],
            [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']]
        ]


        if (_DEBUG) {
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]] 
            command 'parseTest', [[name: "parseTest", type: "STRING", description: "parseTest", defaultValue : ""]]
            command "tuyaTest", [
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]],
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]],
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"]
            ]
        }
        
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
        // OSRAM Lightify - use HE inbuilt driver to pair first !
        //fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0020,1000,FD00", outClusters:"0003,0004,0005,0006,0008,0019,0300,1000", model:"Lightify Switch Mini", manufacturer:"OSRAM", deviceJoinName: "Lightify Switch Mini"
        fingerprint profileId:'0104', endpointId:'15', inClusters:'0000,0001,0003,0020,FF17', outClusters:'0003,0004,0005,0006,0008,0019,0102', model:'FLS/SYSTEM-M/4', manufacturer:'Schneider Electric', deviceJoinName: 'Schneider Electric WDE002924'

    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'

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
boolean isOsram()               { device.getDataValue('manufacturer') == 'OSRAM' }
def isIkeaOnOffSwitch()         { device.getDataValue('model') == 'TRADFRI on/off switch' }
def isIkeaRemoteControl()       { device.getDataValue('model') == 'TRADFRI remote control' }
def isIkeaShortcutButtonE1812() { device.getDataValue('model') == 'TRADFRI SHORTCUT Button' }
def isIkeaStyrbar()             { device.getDataValue('model') == 'Remote Control N2' }
def isIkeaRODRET()              { device.getDataValue('model') == 'RODRET Dimmer' }
def isIkeaOpenCloseRemote()     { device.getDataValue('model') == 'TRADFRI open/close remote' }
def isIkeaSoundController()     { device.getDataValue('model') == 'SYMFONISK Sound Controller' }
def isSchneider()               { device.getDataValue('manufacturer') == 'Schneider Electric' }

/*
 * -----------------------------------------------------------------------------
 *  Button/Dimmer  Scenes cluster 0x0005
 * -----------------------------------------------------------------------------
*/
void customParseScenesCluster(final Map descMap) {
    if (isIkeaStyrbar() || isIkeaRODRET() || isIkeaRemoteControl()) {
        processIkeaCommand(descMap)
    }
    else {
        logWarn "customParseScenesCluster: unprocessed Scenes cluster attribute ${descMap.attrId}"
    }
}

/*
 * ----------------------------------------------------------------------------
 *  Button/Dimmer  On/Off  cluster 0x0006
 * -----------------------------------------------------------------------------
*/
void customParseOnOffCluster(final Map descMap) {
    if (descMap.command in ['FC', 'FD']) {
        processTS004Fcommand(descMap)
    }
    else if (descMap.attrId == '8004') {
        processTS004Fmode(descMap)
    }
    else if (isIkeaStyrbar() || isIkeaRODRET() || isIkeaOnOffSwitch() || isIkeaRemoteControl() || isIkeaShortcutButtonE1812() || isIkeaSoundController()) {
        processIkeaCommand(descMap)
    }
    else if (isOsram()) {
        processOsramCommand(descMap)
    }
    else if (isSchneider()) {
        processSchneiderWiser(descMap)
    }
    else {
        logWarn "customParseOnOffCluster: unprocessed OnOff Cluster 0x${descMap.clusterId}, sourceEndpoint ${descMap.sourceEndpoint}, command ${descMap.command} data ${descMap.data}"
    }
}

/*
 * -----------------------------------------------------------------------------
 *  Button/Dimmer  LevelControl cluster 0x0008
 * -----------------------------------------------------------------------------
*/
void customParseLevelControlCluster(final Map descMap) {
    if (descMap.attrId == '0000' && descMap.command == 'FD') {
        processTS004Fcommand(descMap)
    }
    else if (isIkeaStyrbar() || isIkeaRODRET() || isIkeaOnOffSwitch() || isIkeaRemoteControl() || isIkeaShortcutButtonE1812() || isIkeaSoundController()) {
        processIkeaCommand(descMap)
    }
    else if (isOsram()) {
        processOsramCommand(descMap)
    }
    else if (isSchneider()) {
        processSchneiderWiser(descMap)
    }
    else {
        logWarn "customParseOnOffCluster: unprocessed LevelControl Cluster 0x${descMap.clusterId}, sourceEndpoint ${descMap.sourceEndpoint}, command ${descMap.command} data ${descMap.data}"
    }
}

/*
 * -----------------------------------------------------------------------------
 *  Button/Dimmer  Window Covering cluster 0x0102
 * -----------------------------------------------------------------------------
*/
void customParseWindowCoveringCluster(final Map descMap) {
    processIkeaWindowCoveringCluster(descMap)
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

void processOsramCommand(final Map descMap)
{
    logDebug "processIkeaCommand: descMap: $descMap"
    def buttonNumber = 0
    def buttonState = 'unknown'

    if (isOsram() && ((descMap.clusterInt == 0x0006 || descMap.clusterInt == 0x0008) && (descMap.command in ['01', '03', '05', '04', '00' ]))) {
        // OSRAM Lightify Mini
        buttonNumber = safeToInt(descMap.sourceEndpoint)
        if (descMap.command == '01') { buttonState = 'pushed' }
        else if (descMap.command == '04') { buttonState = 'pushed' }
        else if (descMap.command == '00') { buttonState = 'pushed' }
        else if (descMap.command == '05') { buttonState = 'held' }
        else if (descMap.command == '03') { buttonState = 'released' }
        else { buttonState = 'unknown' }
    }
    else {
        logWarn "processOsramCommand: unprocessed event from cluster ${descMap.clusterInt} command ${descMap.command } sourceEndpoint ${descMap.sourceEndpoint} data = ${descMap?.data}"    
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


void processSchneiderWiser(final Map descMap) {
    logDebug "processSchneiderWiser: Cluster 0x${descMap.clusterId}, sourceEndpoint ${descMap.sourceEndpoint}, command ${descMap.command} data ${descMap.data}"
    int buttonNumber = 0
    String buttonState = 'unknown'

    if (descMap.clusterId == '0006') {
        buttonState = 'pushed'
        if (descMap.command == '01') {
            buttonNumber = 1
        }
        else if (descMap.command == '00') {
            buttonNumber = 2
        }
        else {
            logWarn "processSchneiderWiser: unprocessed event from clusterId ${descMap.clusterId} command ${descMap.command } sourceEndpoint ${descMap.sourceEndpoint} data = ${descMap?.data}"
            return
        }
        if (descMap.sourceEndpoint == '16') {
            buttonNumber += 2
        }
    }
    else if (descMap.clusterId == '0008') {
        if (descMap.command == '05') {
            buttonState = 'held'
            buttonNumber = 1
        }
        else if (descMap.command == '01') {
            buttonState = 'held'
            buttonNumber = 2
        }
        else if (descMap.command == '03') {
            buttonState = 'released'
            buttonNumber = state.states['lastButtonNumber'] ?: 5
        }
        else {
            logWarn "processSchneiderWiser: unprocessed event from clusterId ${descMap.clusterId} command ${descMap.command } sourceEndpoint ${descMap.sourceEndpoint} data = ${descMap?.data}"
            return
        }
        if (descMap.sourceEndpoint == '16' && buttonState != 'released') {
            buttonNumber += 2
        }        
    }
    else {
        logWarn "processSchneiderWiser: unprocessed clusterId ${descMap.clusterId} command ${descMap.command } sourceEndpoint ${descMap.sourceEndpoint} data = ${descMap?.data}"
        return
    }
    
    if (buttonNumber != 0) {
        state.states['lastButtonNumber'] = buttonNumber
    }
    else {
        logWarn "processSchneiderWiser: UNHANDLED event for button ${buttonNumber},  lastButtonNumber=${state.states['lastButtonNumber']}"
    }
    if (buttonState != 'unknown' && buttonNumber != 0) {
        def descriptionText = "button $buttonNumber was $buttonState"
        def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: 'physical']
        logInfo "${descriptionText}"
        sendEvent(event)
    }
    else {
        logWarn "processSchneiderWiser: UNHANDLED event for button ${buttonNumber},  buttonState=${buttonState}"
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

void customSetLevel(level, transitionTime) {
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

// TODO !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
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

/*
def customRefresh() {
    List<String> cmds = []
    logDebug "refreshButtonDimmer() (n/a) : ${cmds} "
    // TODO !!
    if (cmds == []) { cmds = ['delay 299'] }
    return cmds
}
*/

def customConfigureDevice() {
    List<String> cmds = []
    // TODO !!
    logDebug "customConfigureDevice() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299'] }    // no ,
    return cmds
}

List<String> customInitializeDevice() {
    List<String> cmds = []
    int intMinTime = 300
    int intMaxTime = 14400    // 4 hours reporting period for the battery
    int ep = zigbee.convertHexToInt(device.endpointId)
    cmds += zigbee.configureReporting(0x0001, 0x0021, DataType.UINT8 /*0x20*/ /* data type*/, intMinTime, intMaxTime, 0x01, [:], delay = 141)    // OK
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${intToHexStr(ep, 1)} 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 142', ]
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${intToHexStr(ep, 1)} 0x01 0x0008 {${device.zigbeeId}} {}", 'delay 144', ]
    // Schneider Electric WDE002924
    if (isSchneider()) {
        logDebug 'Schneider Electric WDE002924'
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${intToHexStr(ep + 1, 1)} 0x01 0x0006 {${device.zigbeeId}} {}", 'delay 145', ]
        cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${intToHexStr(ep + 1, 1)} 0x01 0x0008 {${device.zigbeeId}} {}", 'delay 146', ]
    }   

    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x${intToHexStr(ep, 1)} 0x01 0x0005 {${device.zigbeeId}} {}", 'delay 147', ]

    logDebug "customInitializeDevice() : ${cmds}"
    if (cmds == []) { cmds = ['delay 299',] }
    return cmds
}

void customInitVars(boolean fullInit=false) {
    logDebug "customInitVars(${fullInit})"
    def debounceDefault = ((device.getDataValue('model') ?: 'n/a') == 'TS004F' || ((device.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3000_abci1hiu', '_TZ3000_vp6clf9d'])) ?  '1000' : '0'
    if (fullInit || settings?.debounce == null) device.updateSetting('debounce', [value: debounceDefault, type: 'enum'])
    if (fullInit || settings?.reverseButton == null) device.updateSetting('reverseButton', true)
    if (fullInit || settings?.dimmerStep == null) device.updateSetting('dimmerStep', [value: DimmerStepOpts.defaultValue.toString(), type: 'enum'])
    if (state.states == null) { state.states = [:] }
    state.states['ignoreButton1'] = false
    state.states['debouncingActive'] = false
}

void customInitEvents(boolean fullInit=false) {
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
    else if (isIkeaStyrbar() || isSchneider()) {
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




// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////


// ~~~~~ start include (144) kkossev.commonLib ~~~~~
/* groovylint-disable CompileStatic, DuplicateListLiteral, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, InsecureRandom, LineLength, MethodCount, MethodReturnTypeRequired, MethodSize, NglParseError, NoDef, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryGetter, UnnecessaryGroovyImport, UnnecessaryObjectReferences, UnnecessaryPackageReference, UnusedImport, UnusedPrivateMethod, VariableName */ // library marker kkossev.commonLib, line 1
library( // library marker kkossev.commonLib, line 2
    base: 'driver', // library marker kkossev.commonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.commonLib, line 4
    category: 'zigbee', // library marker kkossev.commonLib, line 5
    description: 'Common ZCL Library', // library marker kkossev.commonLib, line 6
    name: 'commonLib', // library marker kkossev.commonLib, line 7
    namespace: 'kkossev', // library marker kkossev.commonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy', // library marker kkossev.commonLib, line 9
    version: '3.0.4', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.3  2024-03-17 kkossev  - (dev.branch) more groovy lint; support for deviceType Plug; ignore repeated temperature readings; cleaned thermostat specifics; cleaned AirQuality specifics; removed IRBlaster type; removed 'radar' type; threeStateEnable initlilization // library marker kkossev.commonLib, line 35
  * ver. 3.0.4  2024-03-29 kkossev  - (dev.branch) removed Button, buttonDimmer and Fingerbot specifics; batteryVoltage bug fix; inverceSwitch bug fix; parseE002Cluster // library marker kkossev.commonLib, line 36
  * // library marker kkossev.commonLib, line 37
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 38
  *                                   TODO: add custom* handlers for the new drivers! // library marker kkossev.commonLib, line 39
  *                                   TODO: remove the automatic capabilities selectionm for the new drivers! // library marker kkossev.commonLib, line 40
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib ! // library marker kkossev.commonLib, line 41
  *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.commonLib, line 42
  *                                   TODO: add GetInof (endpoints list) command // library marker kkossev.commonLib, line 43
  *                                   TODO: handle Virtual Switch sendZigbeeCommands(cmd=[he cmd 0xbb14c77a-5810-4e65-b16d-22bc665767ed 0xnull 6 1 {}, delay 2000]) // library marker kkossev.commonLib, line 44
  *                                   TODO: move zigbeeGroups : {} to dedicated lib // library marker kkossev.commonLib, line 45
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 46
  *                                   TODO: ping() for a virtual device (runIn 1 milissecond a callback nethod) // library marker kkossev.commonLib, line 47
 * // library marker kkossev.commonLib, line 48
*/ // library marker kkossev.commonLib, line 49

String commonLibVersion() { '3.0.4' } // library marker kkossev.commonLib, line 51
String commonLibStamp() { '2024/03/31 11:38 PM' } // library marker kkossev.commonLib, line 52

import groovy.transform.Field // library marker kkossev.commonLib, line 54
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 55
import hubitat.device.Protocol // library marker kkossev.commonLib, line 56
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 57
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 58
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 59
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 60
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 61
import java.math.BigDecimal // library marker kkossev.commonLib, line 62

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 64

metadata { // library marker kkossev.commonLib, line 66
        if (_DEBUG) { // library marker kkossev.commonLib, line 67
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 68
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 69
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 70
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 71
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 72
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 73
            ] // library marker kkossev.commonLib, line 74
        } // library marker kkossev.commonLib, line 75

        // common capabilities for all device types // library marker kkossev.commonLib, line 77
        capability 'Configuration' // library marker kkossev.commonLib, line 78
        capability 'Refresh' // library marker kkossev.commonLib, line 79
        capability 'Health Check' // library marker kkossev.commonLib, line 80

        // common attributes for all device types // library marker kkossev.commonLib, line 82
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 83
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 84
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 85

        // common commands for all device types // library marker kkossev.commonLib, line 87
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 88
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 89

        // deviceType specific capabilities, commands and attributes // library marker kkossev.commonLib, line 91
        if (deviceType in ['Device']) { // library marker kkossev.commonLib, line 92
            if (_DEBUG) { // library marker kkossev.commonLib, line 93
                command 'getAllProperties',       [[name: 'Get All Properties']] // library marker kkossev.commonLib, line 94
            } // library marker kkossev.commonLib, line 95
        } // library marker kkossev.commonLib, line 96
        if (_DEBUG || (deviceType in ['Dimmer', 'Switch', 'Valve'])) { // library marker kkossev.commonLib, line 97
            command 'zigbeeGroups', [ // library marker kkossev.commonLib, line 98
                [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 99
                [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.commonLib, line 100
            ] // library marker kkossev.commonLib, line 101
        } // library marker kkossev.commonLib, line 102
        if (deviceType in  ['Device', 'THSensor', 'MotionSensor', 'LightSensor', 'AqaraCube']) { // library marker kkossev.commonLib, line 103
            capability 'Sensor' // library marker kkossev.commonLib, line 104
        } // library marker kkossev.commonLib, line 105
        if (deviceType in  ['Device', 'MotionSensor']) { // library marker kkossev.commonLib, line 106
            capability 'MotionSensor' // library marker kkossev.commonLib, line 107
        } // library marker kkossev.commonLib, line 108
        if (deviceType in  ['Device', 'Switch', 'Relay', 'Outlet', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 109
            capability 'Actuator' // library marker kkossev.commonLib, line 110
        } // library marker kkossev.commonLib, line 111
        if (deviceType in  ['Device', 'THSensor', 'LightSensor', 'MotionSensor', 'Thermostat', 'AqaraCube']) { // library marker kkossev.commonLib, line 112
            capability 'Battery' // library marker kkossev.commonLib, line 113
            attribute 'batteryVoltage', 'number' // library marker kkossev.commonLib, line 114
        } // library marker kkossev.commonLib, line 115
        if (deviceType in  ['Device', 'Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 116
            capability 'Switch' // library marker kkossev.commonLib, line 117
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 118
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 119
            } // library marker kkossev.commonLib, line 120
        } // library marker kkossev.commonLib, line 121
        if (deviceType in ['Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 122
            capability 'SwitchLevel' // library marker kkossev.commonLib, line 123
        } // library marker kkossev.commonLib, line 124
        if (deviceType in  ['AqaraCube']) { // library marker kkossev.commonLib, line 125
            capability 'PushableButton' // library marker kkossev.commonLib, line 126
            capability 'DoubleTapableButton' // library marker kkossev.commonLib, line 127
            capability 'HoldableButton' // library marker kkossev.commonLib, line 128
            capability 'ReleasableButton' // library marker kkossev.commonLib, line 129
        } // library marker kkossev.commonLib, line 130
        if (deviceType in  ['Device']) { // library marker kkossev.commonLib, line 131
            capability 'Momentary' // library marker kkossev.commonLib, line 132
        } // library marker kkossev.commonLib, line 133
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 134
            capability 'TemperatureMeasurement' // library marker kkossev.commonLib, line 135
        } // library marker kkossev.commonLib, line 136
        if (deviceType in  ['Device', 'THSensor']) { // library marker kkossev.commonLib, line 137
            capability 'RelativeHumidityMeasurement' // library marker kkossev.commonLib, line 138
        } // library marker kkossev.commonLib, line 139
        if (deviceType in  ['Device', 'LightSensor']) { // library marker kkossev.commonLib, line 140
            capability 'IlluminanceMeasurement' // library marker kkossev.commonLib, line 141
        } // library marker kkossev.commonLib, line 142

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 144
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 145

    preferences { // library marker kkossev.commonLib, line 147
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 148
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 149
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 150

        if (device) { // library marker kkossev.commonLib, line 152
            if ((device.hasCapability('TemperatureMeasurement') || device.hasCapability('RelativeHumidityMeasurement') || device.hasCapability('IlluminanceMeasurement')) && !isZigUSB()) { // library marker kkossev.commonLib, line 153
                input name: 'minReportingTime', type: 'number', title: '<b>Minimum time between reports</b>', description: '<i>Minimum reporting interval, seconds (1..300)</i>', range: '1..300', defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 154
                if (deviceType != 'mmWaveSensor') { // library marker kkossev.commonLib, line 155
                    input name: 'maxReportingTime', type: 'number', title: '<b>Maximum time between reports</b>', description: '<i>Maximum reporting interval, seconds (120..10000)</i>', range: '120..10000', defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 156
                } // library marker kkossev.commonLib, line 157
            } // library marker kkossev.commonLib, line 158
            if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 159
                input name: 'illuminanceThreshold', type: 'number', title: '<b>Illuminance Reporting Threshold</b>', description: '<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>', range: '1..255', defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 160
                input name: 'illuminanceCoeff', type: 'decimal', title: '<b>Illuminance Correction Coefficient</b>', description: '<i>Illuminance correction coefficient, range (0.10..10.00)</i>', range: '0.10..10.00', defaultValue: 1.00 // library marker kkossev.commonLib, line 161
            } // library marker kkossev.commonLib, line 162

            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 164
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 165
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 166
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 167
                if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 168
                    input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 169
                } // library marker kkossev.commonLib, line 170
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 171
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 172
                } // library marker kkossev.commonLib, line 173
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 174
            } // library marker kkossev.commonLib, line 175
        } // library marker kkossev.commonLib, line 176
    } // library marker kkossev.commonLib, line 177
} // library marker kkossev.commonLib, line 178

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 180
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 181
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 182
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 183
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 184
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 185
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 186
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 187
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 188
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 189
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 190
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 191

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 193
    defaultValue: 1, // library marker kkossev.commonLib, line 194
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 195
] // library marker kkossev.commonLib, line 196
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 197
    defaultValue: 240, // library marker kkossev.commonLib, line 198
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 199
] // library marker kkossev.commonLib, line 200
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 201
    defaultValue: 0, // library marker kkossev.commonLib, line 202
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 203
] // library marker kkossev.commonLib, line 204

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 206
    defaultValue: 0, // library marker kkossev.commonLib, line 207
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 208
] // library marker kkossev.commonLib, line 209
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 210
    defaultValue: 0, // library marker kkossev.commonLib, line 211
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 212
] // library marker kkossev.commonLib, line 213

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 215
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 216
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 217
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 218
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 219
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 220
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 221
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 222
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 223
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 224
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 225
] // library marker kkossev.commonLib, line 226

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 228
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 229
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 230
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 231
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 232
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 233
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 234
boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 235
boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 236

/** // library marker kkossev.commonLib, line 238
 * Parse Zigbee message // library marker kkossev.commonLib, line 239
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 240
 */ // library marker kkossev.commonLib, line 241
void parse(final String description) { // library marker kkossev.commonLib, line 242
    checkDriverVersion() // library marker kkossev.commonLib, line 243
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 244
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 245
    setHealthStatusOnline() // library marker kkossev.commonLib, line 246

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 248
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 249
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 250
            parseIasMessage(description) // library marker kkossev.commonLib, line 251
        } // library marker kkossev.commonLib, line 252
        else { // library marker kkossev.commonLib, line 253
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 254
        } // library marker kkossev.commonLib, line 255
        return // library marker kkossev.commonLib, line 256
    } // library marker kkossev.commonLib, line 257
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 258
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 259
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 260
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 261
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 262
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 263
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 264
        return // library marker kkossev.commonLib, line 265
    } // library marker kkossev.commonLib, line 266
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 267
        return // library marker kkossev.commonLib, line 268
    } // library marker kkossev.commonLib, line 269
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 270

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 272
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 273
        return // library marker kkossev.commonLib, line 274
    } // library marker kkossev.commonLib, line 275
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 276
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 277
        return // library marker kkossev.commonLib, line 278
    } // library marker kkossev.commonLib, line 279
    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 280
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 281
    // // library marker kkossev.commonLib, line 282
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 283
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 284
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 285

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 287
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 288
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 289
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 290
            break // library marker kkossev.commonLib, line 291
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 292
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 293
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 294
            break // library marker kkossev.commonLib, line 295
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 296
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 297
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 298
            break // library marker kkossev.commonLib, line 299
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 300
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 301
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 302
            break // library marker kkossev.commonLib, line 303
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 304
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 305
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 306
            break // library marker kkossev.commonLib, line 307
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 308
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 309
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 310
            break // library marker kkossev.commonLib, line 311
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 312
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 313
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 314
            break // library marker kkossev.commonLib, line 315
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 316
            if (isZigUSB()) { // library marker kkossev.commonLib, line 317
                parseZigUSBAnlogInputCluster(description) // library marker kkossev.commonLib, line 318
            } // library marker kkossev.commonLib, line 319
            else { // library marker kkossev.commonLib, line 320
                parseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 321
                descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) } // library marker kkossev.commonLib, line 322
            } // library marker kkossev.commonLib, line 323
            break // library marker kkossev.commonLib, line 324
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 325
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 328
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 329
            break // library marker kkossev.commonLib, line 330
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 331
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 332
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 333
            break // library marker kkossev.commonLib, line 334
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 335
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 336
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 337
            break // library marker kkossev.commonLib, line 338
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 339
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 340
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 341
            break // library marker kkossev.commonLib, line 342
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 343
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 344
            break // library marker kkossev.commonLib, line 345
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 346
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 347
            break // library marker kkossev.commonLib, line 348
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 349
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 350
            break // library marker kkossev.commonLib, line 351
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 352
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 353
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 354
            break // library marker kkossev.commonLib, line 355
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 356
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 357
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 358
            break // library marker kkossev.commonLib, line 359
        case 0xE002 : // library marker kkossev.commonLib, line 360
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 361
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 362
            break // library marker kkossev.commonLib, line 363
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 364
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 365
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 366
            break // library marker kkossev.commonLib, line 367
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 368
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 369
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 370
            break // library marker kkossev.commonLib, line 371
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 372
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 373
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 374
            break // library marker kkossev.commonLib, line 375
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 376
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 377
            break // library marker kkossev.commonLib, line 378
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 379
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 380
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 381
            break // library marker kkossev.commonLib, line 382
        default: // library marker kkossev.commonLib, line 383
            if (settings.logEnable) { // library marker kkossev.commonLib, line 384
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 385
            } // library marker kkossev.commonLib, line 386
            break // library marker kkossev.commonLib, line 387
    } // library marker kkossev.commonLib, line 388
} // library marker kkossev.commonLib, line 389

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 391
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 392
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 393
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 394
    } // library marker kkossev.commonLib, line 395
    return false // library marker kkossev.commonLib, line 396
} // library marker kkossev.commonLib, line 397

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 399
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 400
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 401
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 402
    } // library marker kkossev.commonLib, line 403
    return false // library marker kkossev.commonLib, line 404
} // library marker kkossev.commonLib, line 405

/** // library marker kkossev.commonLib, line 407
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 408
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 409
 */ // library marker kkossev.commonLib, line 410
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 411
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 412
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 413
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 414
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 415
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 416
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 417
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 418
    } // library marker kkossev.commonLib, line 419
    else { // library marker kkossev.commonLib, line 420
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 421
    } // library marker kkossev.commonLib, line 422
} // library marker kkossev.commonLib, line 423

/** // library marker kkossev.commonLib, line 425
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 426
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 427
 */ // library marker kkossev.commonLib, line 428
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 429
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 430
    switch (commandId) { // library marker kkossev.commonLib, line 431
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 432
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 433
            break // library marker kkossev.commonLib, line 434
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 435
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 436
            break // library marker kkossev.commonLib, line 437
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 438
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 439
            break // library marker kkossev.commonLib, line 440
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 441
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 442
            break // library marker kkossev.commonLib, line 443
        case 0x0B: // default command response // library marker kkossev.commonLib, line 444
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 445
            break // library marker kkossev.commonLib, line 446
        default: // library marker kkossev.commonLib, line 447
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 448
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 449
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 450
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 451
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 452
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 453
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 454
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 455
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 456
            } // library marker kkossev.commonLib, line 457
            break // library marker kkossev.commonLib, line 458
    } // library marker kkossev.commonLib, line 459
} // library marker kkossev.commonLib, line 460

/** // library marker kkossev.commonLib, line 462
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 463
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 464
 */ // library marker kkossev.commonLib, line 465
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 466
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 467
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 468
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 469
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 470
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 471
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 472
    } // library marker kkossev.commonLib, line 473
    else { // library marker kkossev.commonLib, line 474
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 475
    } // library marker kkossev.commonLib, line 476
} // library marker kkossev.commonLib, line 477

/** // library marker kkossev.commonLib, line 479
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 480
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 481
 */ // library marker kkossev.commonLib, line 482
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 483
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 484
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 485
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 486
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 487
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 488
    } // library marker kkossev.commonLib, line 489
    else { // library marker kkossev.commonLib, line 490
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 491
    } // library marker kkossev.commonLib, line 492
} // library marker kkossev.commonLib, line 493

/** // library marker kkossev.commonLib, line 495
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 496
 */ // library marker kkossev.commonLib, line 497
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 498
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 499
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 500
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 501
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 502
        state.reportingEnabled = true // library marker kkossev.commonLib, line 503
    } // library marker kkossev.commonLib, line 504
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 505
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 506
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 507
    } else { // library marker kkossev.commonLib, line 508
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 509
    } // library marker kkossev.commonLib, line 510
} // library marker kkossev.commonLib, line 511

/** // library marker kkossev.commonLib, line 513
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 514
 */ // library marker kkossev.commonLib, line 515
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 516
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 517
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 518
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 519
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 520
    if (status == 0) { // library marker kkossev.commonLib, line 521
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 522
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 523
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 524
        int delta = 0 // library marker kkossev.commonLib, line 525
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 526
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 527
        } // library marker kkossev.commonLib, line 528
        else { // library marker kkossev.commonLib, line 529
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 530
        } // library marker kkossev.commonLib, line 531
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 532
    } // library marker kkossev.commonLib, line 533
    else { // library marker kkossev.commonLib, line 534
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 535
    } // library marker kkossev.commonLib, line 536
} // library marker kkossev.commonLib, line 537

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 539
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 540
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 541
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 542
        return false // library marker kkossev.commonLib, line 543
    } // library marker kkossev.commonLib, line 544
    // execute the customHandler function // library marker kkossev.commonLib, line 545
    boolean result = false // library marker kkossev.commonLib, line 546
    try { // library marker kkossev.commonLib, line 547
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 548
    } // library marker kkossev.commonLib, line 549
    catch (e) { // library marker kkossev.commonLib, line 550
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 551
        return false // library marker kkossev.commonLib, line 552
    } // library marker kkossev.commonLib, line 553
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 554
    return result // library marker kkossev.commonLib, line 555
} // library marker kkossev.commonLib, line 556

/** // library marker kkossev.commonLib, line 558
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 559
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 560
 */ // library marker kkossev.commonLib, line 561
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 562
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 563
    final String commandId = data[0] // library marker kkossev.commonLib, line 564
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 565
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 566
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 567
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 568
    } else { // library marker kkossev.commonLib, line 569
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 570
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 571
        if (isZigUSB()) { // library marker kkossev.commonLib, line 572
            executeCustomHandler('customParseDefaultCommandResponse', descMap) // library marker kkossev.commonLib, line 573
        } // library marker kkossev.commonLib, line 574
    } // library marker kkossev.commonLib, line 575
} // library marker kkossev.commonLib, line 576

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 578
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 579
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 580
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 581
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 582
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 583
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 584
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 585
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 586
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 587
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 588
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 589
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 590
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 591
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 592
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 593

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 595
    0x00: 'Success', // library marker kkossev.commonLib, line 596
    0x01: 'Failure', // library marker kkossev.commonLib, line 597
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 598
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 599
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 600
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 601
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 602
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 603
    0x88: 'Read Only', // library marker kkossev.commonLib, line 604
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 605
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 606
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 607
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 608
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 609
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 610
    0x94: 'Time out', // library marker kkossev.commonLib, line 611
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 612
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 613
] // library marker kkossev.commonLib, line 614

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 616
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 617
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 618
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 619
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 620
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 621
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 622
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 623
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 624
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 625
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 626
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 627
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 628
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 629
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 630
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 631
] // library marker kkossev.commonLib, line 632

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 634
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 635
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 636
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 637
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 638
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 639
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 640
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 641
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 642
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 643
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 644
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 645
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 646
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 647
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 648
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 649
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 650
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 651
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 652
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 653
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 654
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 655
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 656
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 657
] // library marker kkossev.commonLib, line 658

/* // library marker kkossev.commonLib, line 660
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 661
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 662
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 663
 */ // library marker kkossev.commonLib, line 664
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 665
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 666
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 667
    } // library marker kkossev.commonLib, line 668
    else { // library marker kkossev.commonLib, line 669
        logWarn 'Xiaomi cluster 0xFCC0' // library marker kkossev.commonLib, line 670
    } // library marker kkossev.commonLib, line 671
} // library marker kkossev.commonLib, line 672

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 674
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 675
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 676
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 677
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 678
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 679
    return avg // library marker kkossev.commonLib, line 680
} // library marker kkossev.commonLib, line 681

/* // library marker kkossev.commonLib, line 683
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 684
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 685
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 686
*/ // library marker kkossev.commonLib, line 687
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 688

/** // library marker kkossev.commonLib, line 690
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 691
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 692
 */ // library marker kkossev.commonLib, line 693
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 694
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 695
    /* // library marker kkossev.commonLib, line 696
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 697
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 698
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 699
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 700
    */ // library marker kkossev.commonLib, line 701
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 702
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 703
        case 0x0000: // library marker kkossev.commonLib, line 704
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 705
            break // library marker kkossev.commonLib, line 706
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 707
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 708
            if (isPing) { // library marker kkossev.commonLib, line 709
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 710
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 711
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 712
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 713
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 714
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 715
                    sendRttEvent() // library marker kkossev.commonLib, line 716
                } // library marker kkossev.commonLib, line 717
                else { // library marker kkossev.commonLib, line 718
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 719
                } // library marker kkossev.commonLib, line 720
                state.states['isPing'] = false // library marker kkossev.commonLib, line 721
            } // library marker kkossev.commonLib, line 722
            else { // library marker kkossev.commonLib, line 723
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 724
            } // library marker kkossev.commonLib, line 725
            break // library marker kkossev.commonLib, line 726
        case 0x0004: // library marker kkossev.commonLib, line 727
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 728
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 729
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 730
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 731
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 732
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 733
            } // library marker kkossev.commonLib, line 734
            break // library marker kkossev.commonLib, line 735
        case 0x0005: // library marker kkossev.commonLib, line 736
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 737
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 738
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 739
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 740
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 741
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 742
            } // library marker kkossev.commonLib, line 743
            break // library marker kkossev.commonLib, line 744
        case 0x0007: // library marker kkossev.commonLib, line 745
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 746
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 747
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 748
            break // library marker kkossev.commonLib, line 749
        case 0xFFDF: // library marker kkossev.commonLib, line 750
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 751
            break // library marker kkossev.commonLib, line 752
        case 0xFFE2: // library marker kkossev.commonLib, line 753
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 754
            break // library marker kkossev.commonLib, line 755
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 756
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 757
            break // library marker kkossev.commonLib, line 758
        case 0xFFFE: // library marker kkossev.commonLib, line 759
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 760
            break // library marker kkossev.commonLib, line 761
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 762
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 763
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 764
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 765
            break // library marker kkossev.commonLib, line 766
        default: // library marker kkossev.commonLib, line 767
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 768
            break // library marker kkossev.commonLib, line 769
    } // library marker kkossev.commonLib, line 770
} // library marker kkossev.commonLib, line 771

/* // library marker kkossev.commonLib, line 773
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 774
 * power cluster            0x0001 // library marker kkossev.commonLib, line 775
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 776
*/ // library marker kkossev.commonLib, line 777
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 778
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 779
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 780
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 781
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 782
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 783
    } // library marker kkossev.commonLib, line 784

    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 786
    if (descMap.attrId == '0020') { // library marker kkossev.commonLib, line 787
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 788
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 789
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.commonLib, line 790
        } // library marker kkossev.commonLib, line 791
    } // library marker kkossev.commonLib, line 792
    else if (descMap.attrId == '0021') { // library marker kkossev.commonLib, line 793
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.commonLib, line 794
    } // library marker kkossev.commonLib, line 795
    else { // library marker kkossev.commonLib, line 796
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 797
    } // library marker kkossev.commonLib, line 798
} // library marker kkossev.commonLib, line 799

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.commonLib, line 801
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 802
    Map result = [:] // library marker kkossev.commonLib, line 803
    BigDecimal volts = BigDecimal(rawValue) / 10G // library marker kkossev.commonLib, line 804
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.commonLib, line 805
        BigDecimal minVolts = 2.2 // library marker kkossev.commonLib, line 806
        BigDecimal maxVolts = 3.2 // library marker kkossev.commonLib, line 807
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 808
        int roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 809
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.commonLib, line 810
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.commonLib, line 811
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 812
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 813
            result.name = 'battery' // library marker kkossev.commonLib, line 814
            result.unit  = '%' // library marker kkossev.commonLib, line 815
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 816
        } // library marker kkossev.commonLib, line 817
        else { // library marker kkossev.commonLib, line 818
            result.value = volts // library marker kkossev.commonLib, line 819
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 820
            result.unit  = 'V' // library marker kkossev.commonLib, line 821
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 822
        } // library marker kkossev.commonLib, line 823
        result.type = 'physical' // library marker kkossev.commonLib, line 824
        result.isStateChange = true // library marker kkossev.commonLib, line 825
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 826
        sendEvent(result) // library marker kkossev.commonLib, line 827
    } // library marker kkossev.commonLib, line 828
    else { // library marker kkossev.commonLib, line 829
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 830
    } // library marker kkossev.commonLib, line 831
} // library marker kkossev.commonLib, line 832

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.commonLib, line 834
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 835
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 836
        return // library marker kkossev.commonLib, line 837
    } // library marker kkossev.commonLib, line 838
    Map map = [:] // library marker kkossev.commonLib, line 839
    map.name = 'battery' // library marker kkossev.commonLib, line 840
    map.timeStamp = now() // library marker kkossev.commonLib, line 841
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 842
    map.unit  = '%' // library marker kkossev.commonLib, line 843
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 844
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 845
    map.isStateChange = true // library marker kkossev.commonLib, line 846
    // // library marker kkossev.commonLib, line 847
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.commonLib, line 848
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 849
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 850
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 851
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 852
        // send it now! // library marker kkossev.commonLib, line 853
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 854
    } // library marker kkossev.commonLib, line 855
    else { // library marker kkossev.commonLib, line 856
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 857
        map.delayed = delayedTime // library marker kkossev.commonLib, line 858
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 859
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 860
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 861
    } // library marker kkossev.commonLib, line 862
} // library marker kkossev.commonLib, line 863

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 865
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 866
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 867
    sendEvent(map) // library marker kkossev.commonLib, line 868
} // library marker kkossev.commonLib, line 869

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 871
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 872
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 873
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 874
    sendEvent(map) // library marker kkossev.commonLib, line 875
} // library marker kkossev.commonLib, line 876

/* // library marker kkossev.commonLib, line 878
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 879
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 880
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 881
*/ // library marker kkossev.commonLib, line 882
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 883
void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 884
    logDebug 'unprocessed parseIdentityCluster' // library marker kkossev.commonLib, line 885
} // library marker kkossev.commonLib, line 886

/* // library marker kkossev.commonLib, line 888
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 889
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 890
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 891
*/ // library marker kkossev.commonLib, line 892
void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 893
    if (this.respondsTo('customParseScenesCluster')) { // library marker kkossev.commonLib, line 894
        customParseScenesCluster(descMap) // library marker kkossev.commonLib, line 895
    } // library marker kkossev.commonLib, line 896
    else { // library marker kkossev.commonLib, line 897
        logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 898
    } // library marker kkossev.commonLib, line 899
} // library marker kkossev.commonLib, line 900

/* // library marker kkossev.commonLib, line 902
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 903
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 904
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 905
*/ // library marker kkossev.commonLib, line 906
void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 907
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 908
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 909
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 910
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 911
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 912
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 913
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 914
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 915
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 916
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 917
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 918
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 919
            } // library marker kkossev.commonLib, line 920
            else { // library marker kkossev.commonLib, line 921
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 922
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 923
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 924
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 925
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 926
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 927
                        return // library marker kkossev.commonLib, line 928
                    } // library marker kkossev.commonLib, line 929
                } // library marker kkossev.commonLib, line 930
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 931
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.commonLib, line 932
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 933
            } // library marker kkossev.commonLib, line 934
            break // library marker kkossev.commonLib, line 935
        case 0x01: // View group // library marker kkossev.commonLib, line 936
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 937
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 938
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 939
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 940
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 941
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 942
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 943
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 944
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 945
            } // library marker kkossev.commonLib, line 946
            else { // library marker kkossev.commonLib, line 947
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 948
            } // library marker kkossev.commonLib, line 949
            break // library marker kkossev.commonLib, line 950
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 951
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 952
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 953
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 954
            final Set<String> groups = [] // library marker kkossev.commonLib, line 955
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 956
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 957
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 958
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 959
            } // library marker kkossev.commonLib, line 960
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 961
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 962
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 963
            break // library marker kkossev.commonLib, line 964
        case 0x03: // Remove group // library marker kkossev.commonLib, line 965
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 966
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 967
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 968
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 969
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 970
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 971
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 972
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 973
            } // library marker kkossev.commonLib, line 974
            else { // library marker kkossev.commonLib, line 975
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 976
            } // library marker kkossev.commonLib, line 977
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 978
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 979
            if (index >= 0) { // library marker kkossev.commonLib, line 980
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 981
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 982
            } // library marker kkossev.commonLib, line 983
            break // library marker kkossev.commonLib, line 984
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 985
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 986
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 987
            break // library marker kkossev.commonLib, line 988
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 989
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.commonLib, line 990
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 991
            logWarn 'not implemented!' // library marker kkossev.commonLib, line 992
            break // library marker kkossev.commonLib, line 993
        default: // library marker kkossev.commonLib, line 994
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 995
            break // library marker kkossev.commonLib, line 996
    } // library marker kkossev.commonLib, line 997
} // library marker kkossev.commonLib, line 998

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1000
List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1001
    List<String> cmds = [] // library marker kkossev.commonLib, line 1002
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1003
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1004
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1005
        return [] // library marker kkossev.commonLib, line 1006
    } // library marker kkossev.commonLib, line 1007
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1008
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1009
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1010
    return cmds // library marker kkossev.commonLib, line 1011
} // library marker kkossev.commonLib, line 1012

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1014
List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1015
    List<String> cmds = [] // library marker kkossev.commonLib, line 1016
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1017
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1018
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1019
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1020
    return cmds // library marker kkossev.commonLib, line 1021
} // library marker kkossev.commonLib, line 1022

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1024
List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 1025
    List<String> cmds = [] // library marker kkossev.commonLib, line 1026
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.commonLib, line 1027
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1028
    return cmds // library marker kkossev.commonLib, line 1029
} // library marker kkossev.commonLib, line 1030

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1032
List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 1033
    List<String> cmds = [] // library marker kkossev.commonLib, line 1034
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1035
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 1036
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 1037
        return [] // library marker kkossev.commonLib, line 1038
    } // library marker kkossev.commonLib, line 1039
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1040
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1041
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1042
    return cmds // library marker kkossev.commonLib, line 1043
} // library marker kkossev.commonLib, line 1044

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1046
List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1047
    List<String> cmds = [] // library marker kkossev.commonLib, line 1048
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1049
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1050
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1051
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1052
    return cmds // library marker kkossev.commonLib, line 1053
} // library marker kkossev.commonLib, line 1054

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1056
List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1057
    List<String> cmds = [] // library marker kkossev.commonLib, line 1058
    //final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1059
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1060
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1061
    return cmds // library marker kkossev.commonLib, line 1062
} // library marker kkossev.commonLib, line 1063

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1065
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.commonLib, line 1066
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1067
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1068
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1069
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1070
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1071
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1072
] // library marker kkossev.commonLib, line 1073

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1075
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.commonLib, line 1076
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1077
    List<String> cmds = [] // library marker kkossev.commonLib, line 1078
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 1079
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.commonLib, line 1080
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1081
    def value // library marker kkossev.commonLib, line 1082
    Boolean validated = false // library marker kkossev.commonLib, line 1083
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1084
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1085
        return // library marker kkossev.commonLib, line 1086
    } // library marker kkossev.commonLib, line 1087
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1088
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.commonLib, line 1089
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1090
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1091
        return // library marker kkossev.commonLib, line 1092
    } // library marker kkossev.commonLib, line 1093
    // // library marker kkossev.commonLib, line 1094
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1095
    def func // library marker kkossev.commonLib, line 1096
    try { // library marker kkossev.commonLib, line 1097
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1098
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1099
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1100
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1101
    } // library marker kkossev.commonLib, line 1102
    catch (e) { // library marker kkossev.commonLib, line 1103
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1104
        return // library marker kkossev.commonLib, line 1105
    } // library marker kkossev.commonLib, line 1106

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1108
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1109
} // library marker kkossev.commonLib, line 1110

/* groovylint-disable-next-line MethodParameterTypeRequired, UnusedMethodParameter */ // library marker kkossev.commonLib, line 1112
void groupCommandsHelp(val) { // library marker kkossev.commonLib, line 1113
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.commonLib, line 1114
} // library marker kkossev.commonLib, line 1115

/* // library marker kkossev.commonLib, line 1117
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1118
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1119
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1120
*/ // library marker kkossev.commonLib, line 1121

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1123
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 1124
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 1125
    } // library marker kkossev.commonLib, line 1126
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1127
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1128
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1129
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1130
    } // library marker kkossev.commonLib, line 1131
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 1132
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1133
    } // library marker kkossev.commonLib, line 1134
    else { // library marker kkossev.commonLib, line 1135
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1136
    } // library marker kkossev.commonLib, line 1137
} // library marker kkossev.commonLib, line 1138

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 1140
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 1141
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1142

void toggle() { // library marker kkossev.commonLib, line 1144
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 1145
    String state = '' // library marker kkossev.commonLib, line 1146
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 1147
        state = 'on' // library marker kkossev.commonLib, line 1148
    } // library marker kkossev.commonLib, line 1149
    else { // library marker kkossev.commonLib, line 1150
        state = 'off' // library marker kkossev.commonLib, line 1151
    } // library marker kkossev.commonLib, line 1152
    descriptionText += state // library marker kkossev.commonLib, line 1153
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 1154
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1155
} // library marker kkossev.commonLib, line 1156

void off() { // library marker kkossev.commonLib, line 1158
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 1159
        customOff() // library marker kkossev.commonLib, line 1160
        return // library marker kkossev.commonLib, line 1161
    } // library marker kkossev.commonLib, line 1162
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1163
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1164
        return // library marker kkossev.commonLib, line 1165
    } // library marker kkossev.commonLib, line 1166
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 1167
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1168
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 1169
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1170
        if (currentState == 'off') { // library marker kkossev.commonLib, line 1171
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1172
        } // library marker kkossev.commonLib, line 1173
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1174
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1175
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1176
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1177
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1178
    } // library marker kkossev.commonLib, line 1179
    /* // library marker kkossev.commonLib, line 1180
    else { // library marker kkossev.commonLib, line 1181
        if (currentState != 'off') { // library marker kkossev.commonLib, line 1182
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1183
        } // library marker kkossev.commonLib, line 1184
        else { // library marker kkossev.commonLib, line 1185
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 1186
            return // library marker kkossev.commonLib, line 1187
        } // library marker kkossev.commonLib, line 1188
    } // library marker kkossev.commonLib, line 1189
    */ // library marker kkossev.commonLib, line 1190

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1192
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1193
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1194
} // library marker kkossev.commonLib, line 1195

void on() { // library marker kkossev.commonLib, line 1197
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 1198
        customOn() // library marker kkossev.commonLib, line 1199
        return // library marker kkossev.commonLib, line 1200
    } // library marker kkossev.commonLib, line 1201
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 1202
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 1203
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 1204
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1205
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 1206
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1207
        } // library marker kkossev.commonLib, line 1208
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1209
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 1210
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 1211
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 1212
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1213
    } // library marker kkossev.commonLib, line 1214
    /* // library marker kkossev.commonLib, line 1215
    else { // library marker kkossev.commonLib, line 1216
        if (currentState != 'on') { // library marker kkossev.commonLib, line 1217
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1218
        } // library marker kkossev.commonLib, line 1219
        else { // library marker kkossev.commonLib, line 1220
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 1221
            return // library marker kkossev.commonLib, line 1222
        } // library marker kkossev.commonLib, line 1223
    } // library marker kkossev.commonLib, line 1224
    */ // library marker kkossev.commonLib, line 1225
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 1226
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1227
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1228
} // library marker kkossev.commonLib, line 1229

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 1231
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 1232
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 1233
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 1234
    } // library marker kkossev.commonLib, line 1235
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1236
    Map map = [:] // library marker kkossev.commonLib, line 1237
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 1238
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 1239
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 1240
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1241
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1242
        return // library marker kkossev.commonLib, line 1243
    } // library marker kkossev.commonLib, line 1244
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1245
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 1246
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1247
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 1248
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1249
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1250
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 1251
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1252
    } else { // library marker kkossev.commonLib, line 1253
        state.states['debounce'] = true // library marker kkossev.commonLib, line 1254
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1255
    } // library marker kkossev.commonLib, line 1256
    map.name = 'switch' // library marker kkossev.commonLib, line 1257
    map.value = value // library marker kkossev.commonLib, line 1258
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1259
    if (isRefresh) { // library marker kkossev.commonLib, line 1260
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1261
        map.isStateChange = true // library marker kkossev.commonLib, line 1262
    } else { // library marker kkossev.commonLib, line 1263
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1264
    } // library marker kkossev.commonLib, line 1265
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1266
    sendEvent(map) // library marker kkossev.commonLib, line 1267
    clearIsDigital() // library marker kkossev.commonLib, line 1268
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 1269
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 1270
    } // library marker kkossev.commonLib, line 1271
} // library marker kkossev.commonLib, line 1272

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 1274
    '0': 'switch off', // library marker kkossev.commonLib, line 1275
    '1': 'switch on', // library marker kkossev.commonLib, line 1276
    '2': 'switch last state' // library marker kkossev.commonLib, line 1277
] // library marker kkossev.commonLib, line 1278

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 1280
    '0': 'toggle', // library marker kkossev.commonLib, line 1281
    '1': 'state', // library marker kkossev.commonLib, line 1282
    '2': 'momentary' // library marker kkossev.commonLib, line 1283
] // library marker kkossev.commonLib, line 1284

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 1286
    Map descMap = [:] // library marker kkossev.commonLib, line 1287
    try { // library marker kkossev.commonLib, line 1288
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1289
    } // library marker kkossev.commonLib, line 1290
    catch (e1) { // library marker kkossev.commonLib, line 1291
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1292
        // try alternative custom parsing // library marker kkossev.commonLib, line 1293
        descMap = [:] // library marker kkossev.commonLib, line 1294
        try { // library marker kkossev.commonLib, line 1295
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1296
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1297
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1298
            } // library marker kkossev.commonLib, line 1299
        } // library marker kkossev.commonLib, line 1300
        catch (e2) { // library marker kkossev.commonLib, line 1301
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1302
            return [:] // library marker kkossev.commonLib, line 1303
        } // library marker kkossev.commonLib, line 1304
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1305
    } // library marker kkossev.commonLib, line 1306
    return descMap // library marker kkossev.commonLib, line 1307
} // library marker kkossev.commonLib, line 1308

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 1310
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1311
        return false // library marker kkossev.commonLib, line 1312
    } // library marker kkossev.commonLib, line 1313
    // try to parse ... // library marker kkossev.commonLib, line 1314
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1315
    Map descMap = [:] // library marker kkossev.commonLib, line 1316
    try { // library marker kkossev.commonLib, line 1317
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1318
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1319
    } // library marker kkossev.commonLib, line 1320
    catch (e) { // library marker kkossev.commonLib, line 1321
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1322
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1323
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1324
        return true // library marker kkossev.commonLib, line 1325
    } // library marker kkossev.commonLib, line 1326

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 1328
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1329
    } // library marker kkossev.commonLib, line 1330
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 1331
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1332
    } // library marker kkossev.commonLib, line 1333
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 1334
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1335
    } // library marker kkossev.commonLib, line 1336
    else { // library marker kkossev.commonLib, line 1337
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1338
        return false // library marker kkossev.commonLib, line 1339
    } // library marker kkossev.commonLib, line 1340
    return true    // processed // library marker kkossev.commonLib, line 1341
} // library marker kkossev.commonLib, line 1342

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1344
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 1345
  /* // library marker kkossev.commonLib, line 1346
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1347
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 1348
        return true // library marker kkossev.commonLib, line 1349
    } // library marker kkossev.commonLib, line 1350
*/ // library marker kkossev.commonLib, line 1351
    Map descMap = [:] // library marker kkossev.commonLib, line 1352
    try { // library marker kkossev.commonLib, line 1353
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1354
    } // library marker kkossev.commonLib, line 1355
    catch (e1) { // library marker kkossev.commonLib, line 1356
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1357
        // try alternative custom parsing // library marker kkossev.commonLib, line 1358
        descMap = [:] // library marker kkossev.commonLib, line 1359
        try { // library marker kkossev.commonLib, line 1360
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1361
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 1362
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1363
            } // library marker kkossev.commonLib, line 1364
        } // library marker kkossev.commonLib, line 1365
        catch (e2) { // library marker kkossev.commonLib, line 1366
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1367
            return true // library marker kkossev.commonLib, line 1368
        } // library marker kkossev.commonLib, line 1369
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1370
    } // library marker kkossev.commonLib, line 1371
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 1372
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 1373
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1374
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1375
        return false // library marker kkossev.commonLib, line 1376
    } // library marker kkossev.commonLib, line 1377
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1378
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1379
    // attribute report received // library marker kkossev.commonLib, line 1380
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1381
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1382
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1383
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1384
    } // library marker kkossev.commonLib, line 1385
    attrData.each { // library marker kkossev.commonLib, line 1386
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1387
        //def map = [:] // library marker kkossev.commonLib, line 1388
        if (it.status == '86') { // library marker kkossev.commonLib, line 1389
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1390
        // TODO - skip parsing? // library marker kkossev.commonLib, line 1391
        } // library marker kkossev.commonLib, line 1392
        switch (it.cluster) { // library marker kkossev.commonLib, line 1393
            case '0000' : // library marker kkossev.commonLib, line 1394
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 1395
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1396
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1397
                } // library marker kkossev.commonLib, line 1398
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 1399
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1400
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1401
                } // library marker kkossev.commonLib, line 1402
                else { // library marker kkossev.commonLib, line 1403
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1404
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1405
                } // library marker kkossev.commonLib, line 1406
                break // library marker kkossev.commonLib, line 1407
            default : // library marker kkossev.commonLib, line 1408
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1409
                break // library marker kkossev.commonLib, line 1410
        } // switch // library marker kkossev.commonLib, line 1411
    } // for each attribute // library marker kkossev.commonLib, line 1412
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1413
} // library marker kkossev.commonLib, line 1414

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 1416

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 1418
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1419
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 1420
    def mode // library marker kkossev.commonLib, line 1421
    String attrName // library marker kkossev.commonLib, line 1422
    if (it.value == null) { // library marker kkossev.commonLib, line 1423
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1424
        return // library marker kkossev.commonLib, line 1425
    } // library marker kkossev.commonLib, line 1426
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1427
    switch (it.attrId) { // library marker kkossev.commonLib, line 1428
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1429
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1430
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1431
            break // library marker kkossev.commonLib, line 1432
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1433
            attrName = 'On Time' // library marker kkossev.commonLib, line 1434
            mode = value // library marker kkossev.commonLib, line 1435
            break // library marker kkossev.commonLib, line 1436
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1437
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1438
            mode = value // library marker kkossev.commonLib, line 1439
            break // library marker kkossev.commonLib, line 1440
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1441
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1442
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1443
            break // library marker kkossev.commonLib, line 1444
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1445
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1446
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1447
            break // library marker kkossev.commonLib, line 1448
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1449
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1450
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1451
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1452
            } // library marker kkossev.commonLib, line 1453
            else { // library marker kkossev.commonLib, line 1454
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1455
            } // library marker kkossev.commonLib, line 1456
            break // library marker kkossev.commonLib, line 1457
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1458
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1459
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1460
            break // library marker kkossev.commonLib, line 1461
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1462
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1463
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1464
            break // library marker kkossev.commonLib, line 1465
        default : // library marker kkossev.commonLib, line 1466
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1467
            return // library marker kkossev.commonLib, line 1468
    } // library marker kkossev.commonLib, line 1469
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1470
} // library marker kkossev.commonLib, line 1471

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.commonLib, line 1473
    Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1474
    if (txtEnable) { log.info "${device.displayName } $event.descriptionText" } // library marker kkossev.commonLib, line 1475
    sendEvent(event) // library marker kkossev.commonLib, line 1476
} // library marker kkossev.commonLib, line 1477

void push() {                // Momentary capability // library marker kkossev.commonLib, line 1479
    logDebug 'push momentary' // library marker kkossev.commonLib, line 1480
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.commonLib, line 1481
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1482
} // library marker kkossev.commonLib, line 1483

void push(int buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1485
    logDebug "push button $buttonNumber" // library marker kkossev.commonLib, line 1486
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.commonLib, line 1487
    sendButtonEvent(buttonNumber, 'pushed', isDigital = true) // library marker kkossev.commonLib, line 1488
} // library marker kkossev.commonLib, line 1489

void doubleTap(int buttonNumber) { // library marker kkossev.commonLib, line 1491
    sendButtonEvent(buttonNumber, 'doubleTapped', isDigital = true) // library marker kkossev.commonLib, line 1492
} // library marker kkossev.commonLib, line 1493

void hold(int buttonNumber) { // library marker kkossev.commonLib, line 1495
    sendButtonEvent(buttonNumber, 'held', isDigital = true) // library marker kkossev.commonLib, line 1496
} // library marker kkossev.commonLib, line 1497

void release(int buttonNumber) { // library marker kkossev.commonLib, line 1499
    sendButtonEvent(buttonNumber, 'released', isDigital = true) // library marker kkossev.commonLib, line 1500
} // library marker kkossev.commonLib, line 1501

void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.commonLib, line 1503
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1504
} // library marker kkossev.commonLib, line 1505

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1507
void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1508
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

/* // library marker kkossev.commonLib, line 1512
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1513
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1514
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1515
*/ // library marker kkossev.commonLib, line 1516
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1517
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1518
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1519
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1520
    } // library marker kkossev.commonLib, line 1521
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1522
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1523
    } // library marker kkossev.commonLib, line 1524
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1525
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1526
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1527
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1528
    } // library marker kkossev.commonLib, line 1529
    else { // library marker kkossev.commonLib, line 1530
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1531
    } // library marker kkossev.commonLib, line 1532
} // library marker kkossev.commonLib, line 1533

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.commonLib, line 1535
    int value = rawValue as int // library marker kkossev.commonLib, line 1536
    if (value < 0) { value = 0 } // library marker kkossev.commonLib, line 1537
    if (value > 100) { value = 100 } // library marker kkossev.commonLib, line 1538
    Map map = [:] // library marker kkossev.commonLib, line 1539

    boolean isDigital = state.states['isDigital'] // library marker kkossev.commonLib, line 1541
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1542

    map.name = 'level' // library marker kkossev.commonLib, line 1544
    map.value = value // library marker kkossev.commonLib, line 1545
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 1546
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1547
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1548
        map.isStateChange = true // library marker kkossev.commonLib, line 1549
    } // library marker kkossev.commonLib, line 1550
    else { // library marker kkossev.commonLib, line 1551
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1552
    } // library marker kkossev.commonLib, line 1553
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1554
    sendEvent(map) // library marker kkossev.commonLib, line 1555
    clearIsDigital() // library marker kkossev.commonLib, line 1556
} // library marker kkossev.commonLib, line 1557

/** // library marker kkossev.commonLib, line 1559
 * Get the level transition rate // library marker kkossev.commonLib, line 1560
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1561
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1562
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1563
 */ // library marker kkossev.commonLib, line 1564
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1565
    int rate = 0 // library marker kkossev.commonLib, line 1566
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1567
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1568
    if (!isOn) { // library marker kkossev.commonLib, line 1569
        currentLevel = 0 // library marker kkossev.commonLib, line 1570
    } // library marker kkossev.commonLib, line 1571
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1572
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1573
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1574
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1575
    } else { // library marker kkossev.commonLib, line 1576
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1577
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1578
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1579
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1580
        } // library marker kkossev.commonLib, line 1581
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1582
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1583
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1584
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1585
        } // library marker kkossev.commonLib, line 1586
    } // library marker kkossev.commonLib, line 1587
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1588
    return rate // library marker kkossev.commonLib, line 1589
} // library marker kkossev.commonLib, line 1590

// Command option that enable changes when off // library marker kkossev.commonLib, line 1592
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1593

/** // library marker kkossev.commonLib, line 1595
 * Constrain a value to a range // library marker kkossev.commonLib, line 1596
 * @param value value to constrain // library marker kkossev.commonLib, line 1597
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1598
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1599
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1600
 */ // library marker kkossev.commonLib, line 1601
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1602
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1603
        return value // library marker kkossev.commonLib, line 1604
    } // library marker kkossev.commonLib, line 1605
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1606
} // library marker kkossev.commonLib, line 1607

/** // library marker kkossev.commonLib, line 1609
 * Constrain a value to a range // library marker kkossev.commonLib, line 1610
 * @param value value to constrain // library marker kkossev.commonLib, line 1611
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1612
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1613
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1614
 */ // library marker kkossev.commonLib, line 1615
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1616
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1617
        return value as Integer // library marker kkossev.commonLib, line 1618
    } // library marker kkossev.commonLib, line 1619
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1620
} // library marker kkossev.commonLib, line 1621

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1623
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1624

/** // library marker kkossev.commonLib, line 1626
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1627
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1628
 * @param commands commands to execute // library marker kkossev.commonLib, line 1629
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1630
 */ // library marker kkossev.commonLib, line 1631
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1632
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1633
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1634
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1635
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1636
    } // library marker kkossev.commonLib, line 1637
    return [] // library marker kkossev.commonLib, line 1638
} // library marker kkossev.commonLib, line 1639

def intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1641
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1642
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1643
} // library marker kkossev.commonLib, line 1644

def intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1646
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1647
} // library marker kkossev.commonLib, line 1648

/** // library marker kkossev.commonLib, line 1650
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1651
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1652
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1653
 */ // library marker kkossev.commonLib, line 1654
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.commonLib, line 1655
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1656
    List<String> cmds = [] // library marker kkossev.commonLib, line 1657
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1658
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1659
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1660
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1661
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1662
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1663
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1664
    } // library marker kkossev.commonLib, line 1665
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1666
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1667
    /* // library marker kkossev.commonLib, line 1668
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1669
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1670
    */ // library marker kkossev.commonLib, line 1671
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1672
    String endpointId = '01'     // TODO !!! // library marker kkossev.commonLib, line 1673
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1674

    return cmds // library marker kkossev.commonLib, line 1676
} // library marker kkossev.commonLib, line 1677

/** // library marker kkossev.commonLib, line 1679
 * Set Level Command // library marker kkossev.commonLib, line 1680
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1681
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1682
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1683
 */ // library marker kkossev.commonLib, line 1684
void setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1685
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1686
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.commonLib, line 1687
        customSetLevel(value, transitionTime) // library marker kkossev.commonLib, line 1688
        return // library marker kkossev.commonLib, line 1689
    } // library marker kkossev.commonLib, line 1690
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1691
    final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1692
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1693
    sendZigbeeCommands(setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1694
} // library marker kkossev.commonLib, line 1695

/* // library marker kkossev.commonLib, line 1697
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1698
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1699
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1700
*/ // library marker kkossev.commonLib, line 1701
void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1702
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1703
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1704
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1705
    } // library marker kkossev.commonLib, line 1706
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1707
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1708
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1709
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1710
    } // library marker kkossev.commonLib, line 1711
    else { // library marker kkossev.commonLib, line 1712
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1713
    } // library marker kkossev.commonLib, line 1714
} // library marker kkossev.commonLib, line 1715

/* // library marker kkossev.commonLib, line 1717
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1718
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1719
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1720
*/ // library marker kkossev.commonLib, line 1721
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1722
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1723
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1724
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1725
    int lux = value > 0 ? Math.round(Math.pow(10, (value / 10000))) : 0 // library marker kkossev.commonLib, line 1726
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1727
} // library marker kkossev.commonLib, line 1728

void handleIlluminanceEvent(int illuminance, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1730
    Map eventMap = [:] // library marker kkossev.commonLib, line 1731
    if (state.stats != null) { state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1732
    eventMap.name = 'illuminance' // library marker kkossev.commonLib, line 1733
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1734
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1735
    eventMap.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1736
    eventMap.unit = 'lx' // library marker kkossev.commonLib, line 1737
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1738
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1739
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1740
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1741
    Integer lastIllum = device.currentValue('illuminance') ?: 0 // library marker kkossev.commonLib, line 1742
    Integer delta = Math.abs(lastIllum - illumCorrected) // library marker kkossev.commonLib, line 1743
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1744
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1745
        return // library marker kkossev.commonLib, line 1746
    } // library marker kkossev.commonLib, line 1747
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1748
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1749
        unschedule('sendDelayedIllumEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1750
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1751
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1752
    } // library marker kkossev.commonLib, line 1753
    else {         // queue the event // library marker kkossev.commonLib, line 1754
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1755
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1756
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1757
    } // library marker kkossev.commonLib, line 1758
} // library marker kkossev.commonLib, line 1759

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1761
private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1762
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1763
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1764
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1765
} // library marker kkossev.commonLib, line 1766

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1768

/* // library marker kkossev.commonLib, line 1770
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1771
 * temperature // library marker kkossev.commonLib, line 1772
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1773
*/ // library marker kkossev.commonLib, line 1774
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1775
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1776
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1777
    int value = hexStrToSignedInt(descMap.value) // library marker kkossev.commonLib, line 1778
    handleTemperatureEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1779
} // library marker kkossev.commonLib, line 1780

void handleTemperatureEvent(BigDecimal temperaturePar, boolean isDigital=false) { // library marker kkossev.commonLib, line 1782
    Map eventMap = [:] // library marker kkossev.commonLib, line 1783
    BigDecimal temperature = safeToBigDecimal(temperaturePar) // library marker kkossev.commonLib, line 1784
    if (state.stats != null) { state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1785
    eventMap.name = 'temperature' // library marker kkossev.commonLib, line 1786
    if (location.temperatureScale == 'F') { // library marker kkossev.commonLib, line 1787
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1788
        eventMap.unit = '\u00B0F' // library marker kkossev.commonLib, line 1789
    } // library marker kkossev.commonLib, line 1790
    else { // library marker kkossev.commonLib, line 1791
        eventMap.unit = '\u00B0C' // library marker kkossev.commonLib, line 1792
    } // library marker kkossev.commonLib, line 1793
    BigDecimal tempCorrected = (temperature + safeToBigDecimal(settings?.temperatureOffset ?: 0)) // library marker kkossev.commonLib, line 1794
    eventMap.value = tempCorrected.setScale(1, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1795
    BigDecimal lastTemp = device.currentValue('temperature') ?: 0 // library marker kkossev.commonLib, line 1796
    logTrace "lastTemp=${lastTemp} tempCorrected=${tempCorrected} delta=${Math.abs(lastTemp - tempCorrected)}" // library marker kkossev.commonLib, line 1797
    if (Math.abs(lastTemp - tempCorrected) < 0.1) { // library marker kkossev.commonLib, line 1798
        logDebug "skipped temperature ${tempCorrected}, less than delta 0.1 (lastTemp=${lastTemp})" // library marker kkossev.commonLib, line 1799
        return // library marker kkossev.commonLib, line 1800
    } // library marker kkossev.commonLib, line 1801
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1802
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1803
    if (state.states['isRefresh'] == true) { // library marker kkossev.commonLib, line 1804
        eventMap.descriptionText += ' [refresh]' // library marker kkossev.commonLib, line 1805
        eventMap.isStateChange = true // library marker kkossev.commonLib, line 1806
    } // library marker kkossev.commonLib, line 1807
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1808
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1809
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1810
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1811
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1812
        unschedule('sendDelayedTempEvent')        //get rid of stale queued reports // library marker kkossev.commonLib, line 1813
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1814
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1815
    } // library marker kkossev.commonLib, line 1816
    else {         // queue the event // library marker kkossev.commonLib, line 1817
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1818
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1819
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1820
    } // library marker kkossev.commonLib, line 1821
} // library marker kkossev.commonLib, line 1822

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1824
private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1825
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1826
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1827
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1828
} // library marker kkossev.commonLib, line 1829

/* // library marker kkossev.commonLib, line 1831
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1832
 * humidity // library marker kkossev.commonLib, line 1833
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1834
*/ // library marker kkossev.commonLib, line 1835
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1836
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1837
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1838
    final int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1839
    handleHumidityEvent(value / 100.0F as BigDecimal) // library marker kkossev.commonLib, line 1840
} // library marker kkossev.commonLib, line 1841

void handleHumidityEvent(BigDecimal humidityPar, Boolean isDigital=false) { // library marker kkossev.commonLib, line 1843
    Map eventMap = [:] // library marker kkossev.commonLib, line 1844
    BigDecimal humidity = safeToBigDecimal(humidityPar) // library marker kkossev.commonLib, line 1845
    if (state.stats != null) { state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1846
    humidity +=  safeToBigDecimal(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1847
    if (humidity <= 0.0 || humidity > 100.0) { // library marker kkossev.commonLib, line 1848
        logWarn "ignored invalid humidity ${humidity} (${humidityPar})" // library marker kkossev.commonLib, line 1849
        return // library marker kkossev.commonLib, line 1850
    } // library marker kkossev.commonLib, line 1851
    eventMap.value = humidity.setScale(0, BigDecimal.ROUND_HALF_UP) // library marker kkossev.commonLib, line 1852
    eventMap.name = 'humidity' // library marker kkossev.commonLib, line 1853
    eventMap.unit = '% RH' // library marker kkossev.commonLib, line 1854
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1855
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1856
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1857
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now())) / 1000) // library marker kkossev.commonLib, line 1858
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1859
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1860
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1861
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1862
        unschedule('sendDelayedHumidityEvent') // library marker kkossev.commonLib, line 1863
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1864
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1865
    } // library marker kkossev.commonLib, line 1866
    else { // library marker kkossev.commonLib, line 1867
        eventMap.type = 'delayed' // library marker kkossev.commonLib, line 1868
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1869
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1870
    } // library marker kkossev.commonLib, line 1871
} // library marker kkossev.commonLib, line 1872

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.commonLib, line 1874
private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1875
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1876
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1877
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1878
} // library marker kkossev.commonLib, line 1879

/* // library marker kkossev.commonLib, line 1881
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1882
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1883
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1884
*/ // library marker kkossev.commonLib, line 1885

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1887
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { // library marker kkossev.commonLib, line 1888
        logWarn 'parseElectricalMeasureCluster is NOT implemented1' // library marker kkossev.commonLib, line 1889
    } // library marker kkossev.commonLib, line 1890
} // library marker kkossev.commonLib, line 1891

/* // library marker kkossev.commonLib, line 1893
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1894
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1895
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1896
*/ // library marker kkossev.commonLib, line 1897

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1899
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { // library marker kkossev.commonLib, line 1900
        logWarn 'parseMeteringCluster is NOT implemented1' // library marker kkossev.commonLib, line 1901
    } // library marker kkossev.commonLib, line 1902
} // library marker kkossev.commonLib, line 1903

/* // library marker kkossev.commonLib, line 1905
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1906
 * pm2.5 // library marker kkossev.commonLib, line 1907
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1908
*/ // library marker kkossev.commonLib, line 1909
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1910
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1911
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1912
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1913
    BigInteger bigIntegerValue = intBitsToFloat(value.intValue()).toBigInteger() // library marker kkossev.commonLib, line 1914
    handlePm25Event(bigIntegerValue as Integer) // library marker kkossev.commonLib, line 1915
} // library marker kkossev.commonLib, line 1916
// TODO - check if handlePm25Event handler exists !! // library marker kkossev.commonLib, line 1917

/* // library marker kkossev.commonLib, line 1919
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1920
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1921
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1922
*/ // library marker kkossev.commonLib, line 1923
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1924
    if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1925
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1926
    } // library marker kkossev.commonLib, line 1927
    else if (DEVICE_TYPE in ['AqaraCube']) { // library marker kkossev.commonLib, line 1928
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1929
    } // library marker kkossev.commonLib, line 1930
    else if (isZigUSB()) { // library marker kkossev.commonLib, line 1931
        parseZigUSBAnlogInputCluster(descMap) // library marker kkossev.commonLib, line 1932
    } // library marker kkossev.commonLib, line 1933
    else { // library marker kkossev.commonLib, line 1934
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1935
    } // library marker kkossev.commonLib, line 1936
} // library marker kkossev.commonLib, line 1937

/* // library marker kkossev.commonLib, line 1939
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1940
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1941
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1942
*/ // library marker kkossev.commonLib, line 1943

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1945
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1946
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1947
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1948
    //Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1949
    if (DEVICE_TYPE in  ['AqaraCube']) { // library marker kkossev.commonLib, line 1950
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 1951
    } // library marker kkossev.commonLib, line 1952
    else { // library marker kkossev.commonLib, line 1953
        handleMultistateInputEvent(value as int) // library marker kkossev.commonLib, line 1954
    } // library marker kkossev.commonLib, line 1955
} // library marker kkossev.commonLib, line 1956

void handleMultistateInputEvent(int value, boolean isDigital=false) { // library marker kkossev.commonLib, line 1958
    Map eventMap = [:] // library marker kkossev.commonLib, line 1959
    eventMap.value = value // library marker kkossev.commonLib, line 1960
    eventMap.name = 'multistateInput' // library marker kkossev.commonLib, line 1961
    eventMap.unit = '' // library marker kkossev.commonLib, line 1962
    eventMap.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.commonLib, line 1963
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1964
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1965
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1966
} // library marker kkossev.commonLib, line 1967

/* // library marker kkossev.commonLib, line 1969
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1970
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1971
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1972
*/ // library marker kkossev.commonLib, line 1973

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1975
    if (this.respondsTo('customParseWindowCoveringCluster')) { // library marker kkossev.commonLib, line 1976
        customParseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 1977
    } // library marker kkossev.commonLib, line 1978
    else { // library marker kkossev.commonLib, line 1979
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1980
    } // library marker kkossev.commonLib, line 1981
} // library marker kkossev.commonLib, line 1982

/* // library marker kkossev.commonLib, line 1984
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1985
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1986
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1987
*/ // library marker kkossev.commonLib, line 1988
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1989
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1990
    if (this.respondsTo('customParseThermostatCluster')) { // library marker kkossev.commonLib, line 1991
        customParseThermostatCluster(descMap) // library marker kkossev.commonLib, line 1992
    } // library marker kkossev.commonLib, line 1993
    else { // library marker kkossev.commonLib, line 1994
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1995
    } // library marker kkossev.commonLib, line 1996
} // library marker kkossev.commonLib, line 1997

// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1999

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 2001
    if (this.respondsTo('customParseFC11Cluster')) { // library marker kkossev.commonLib, line 2002
        customParseFC11Cluster(descMap) // library marker kkossev.commonLib, line 2003
    } // library marker kkossev.commonLib, line 2004
    else { // library marker kkossev.commonLib, line 2005
        logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2006
    } // library marker kkossev.commonLib, line 2007
} // library marker kkossev.commonLib, line 2008

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 2010
    if (this.respondsTo('customParseE002Cluster')) { // library marker kkossev.commonLib, line 2011
        customParseE002Cluster(descMap) // library marker kkossev.commonLib, line 2012
    } // library marker kkossev.commonLib, line 2013
    else { // library marker kkossev.commonLib, line 2014
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 2015
    } // library marker kkossev.commonLib, line 2016
} // library marker kkossev.commonLib, line 2017

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 2019
    if (this.respondsTo('customParseEC03Cluster')) { // library marker kkossev.commonLib, line 2020
        customParseEC03Cluster(descMap) // library marker kkossev.commonLib, line 2021
    } // library marker kkossev.commonLib, line 2022
    else { // library marker kkossev.commonLib, line 2023
        logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})"    // radars // library marker kkossev.commonLib, line 2024
    } // library marker kkossev.commonLib, line 2025
} // library marker kkossev.commonLib, line 2026

/* // library marker kkossev.commonLib, line 2028
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2029
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 2030
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2031
*/ // library marker kkossev.commonLib, line 2032
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 2033
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 2034
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 2035

// Tuya Commands // library marker kkossev.commonLib, line 2037
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 2038
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2039
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2040
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2041
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2042

// tuya DP type // library marker kkossev.commonLib, line 2044
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 2045
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 2046
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2047
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 2048
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 2049
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2050

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2052
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 2053
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2054
        Long offset = 0 // library marker kkossev.commonLib, line 2055
        try { // library marker kkossev.commonLib, line 2056
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2057
        } // library marker kkossev.commonLib, line 2058
        catch (e) { // library marker kkossev.commonLib, line 2059
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 2060
        } // library marker kkossev.commonLib, line 2061
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 2062
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2063
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2064
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2065
    } // library marker kkossev.commonLib, line 2066
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2067
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2068
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 2069
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2070
        if (status != '00') { // library marker kkossev.commonLib, line 2071
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 2072
        } // library marker kkossev.commonLib, line 2073
    } // library marker kkossev.commonLib, line 2074
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 2075
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2076
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2077
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2078
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2079
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2080
            return // library marker kkossev.commonLib, line 2081
        } // library marker kkossev.commonLib, line 2082
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 2083
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2084
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2085
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 2086
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2087
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2088
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2089
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 2090
        } // library marker kkossev.commonLib, line 2091
    } // library marker kkossev.commonLib, line 2092
    else { // library marker kkossev.commonLib, line 2093
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2094
    } // library marker kkossev.commonLib, line 2095
} // library marker kkossev.commonLib, line 2096

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 2098
    log.trace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 2099
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 2100
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 2101
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 2102
            return // library marker kkossev.commonLib, line 2103
        } // library marker kkossev.commonLib, line 2104
    } // library marker kkossev.commonLib, line 2105
    // check if the method  method exists // library marker kkossev.commonLib, line 2106
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 2107
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 2108
            return // library marker kkossev.commonLib, line 2109
        } // library marker kkossev.commonLib, line 2110
    } // library marker kkossev.commonLib, line 2111
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 2112
} // library marker kkossev.commonLib, line 2113

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 2115
    int retValue = 0 // library marker kkossev.commonLib, line 2116
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2117
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 2118
        int power = 1 // library marker kkossev.commonLib, line 2119
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2120
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 2121
            power = power * 256 // library marker kkossev.commonLib, line 2122
        } // library marker kkossev.commonLib, line 2123
    } // library marker kkossev.commonLib, line 2124
    return retValue // library marker kkossev.commonLib, line 2125
} // library marker kkossev.commonLib, line 2126

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 2128
    List<String> cmds = [] // library marker kkossev.commonLib, line 2129
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2130
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2131
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2132
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 2133
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2134
    return cmds // library marker kkossev.commonLib, line 2135
} // library marker kkossev.commonLib, line 2136

private getPACKET_ID() { // library marker kkossev.commonLib, line 2138
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 2139
} // library marker kkossev.commonLib, line 2140

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2142
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 2143
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2144
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2145
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 2146
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2147
} // library marker kkossev.commonLib, line 2148

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2150
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2151

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 2153
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2154
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 2155
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 2156
} // library marker kkossev.commonLib, line 2157

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2159
    List<String> cmds = [] // library marker kkossev.commonLib, line 2160
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2161
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 2162
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2163
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2164
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2165
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2166
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 2167
        } // library marker kkossev.commonLib, line 2168
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2169
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 2170
    } // library marker kkossev.commonLib, line 2171
    else { // library marker kkossev.commonLib, line 2172
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 2173
    } // library marker kkossev.commonLib, line 2174
} // library marker kkossev.commonLib, line 2175

/** // library marker kkossev.commonLib, line 2177
 * initializes the device // library marker kkossev.commonLib, line 2178
 * Invoked from configure() // library marker kkossev.commonLib, line 2179
 * @return zigbee commands // library marker kkossev.commonLib, line 2180
 */ // library marker kkossev.commonLib, line 2181
List<String> initializeDevice() { // library marker kkossev.commonLib, line 2182
    List<String> cmds = [] // library marker kkossev.commonLib, line 2183
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2184

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2186
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 2187
        return customInitializeDevice() // library marker kkossev.commonLib, line 2188
    } // library marker kkossev.commonLib, line 2189
    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2190
    if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2191
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2192
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2193
    } // library marker kkossev.commonLib, line 2194
    // // library marker kkossev.commonLib, line 2195
    if (cmds == []) { // library marker kkossev.commonLib, line 2196
        cmds = ['delay 299'] // library marker kkossev.commonLib, line 2197
    } // library marker kkossev.commonLib, line 2198
    return cmds // library marker kkossev.commonLib, line 2199
} // library marker kkossev.commonLib, line 2200

/** // library marker kkossev.commonLib, line 2202
 * configures the device // library marker kkossev.commonLib, line 2203
 * Invoked from configure() // library marker kkossev.commonLib, line 2204
 * @return zigbee commands // library marker kkossev.commonLib, line 2205
 */ // library marker kkossev.commonLib, line 2206
List<String> configureDevice() { // library marker kkossev.commonLib, line 2207
    List<String> cmds = [] // library marker kkossev.commonLib, line 2208
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2209

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 2211
        cmds += customConfigureDevice() // library marker kkossev.commonLib, line 2212
    } // library marker kkossev.commonLib, line 2213
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2214
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2215
    if ( cmds == null || cmds == []) { // library marker kkossev.commonLib, line 2216
        cmds = ['delay 277',] // library marker kkossev.commonLib, line 2217
    } // library marker kkossev.commonLib, line 2218
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 2219
    return cmds // library marker kkossev.commonLib, line 2220
} // library marker kkossev.commonLib, line 2221

/* // library marker kkossev.commonLib, line 2223
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2224
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2225
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2226
*/ // library marker kkossev.commonLib, line 2227

void refresh() { // library marker kkossev.commonLib, line 2229
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2230
    checkDriverVersion() // library marker kkossev.commonLib, line 2231
    List<String> cmds = [] // library marker kkossev.commonLib, line 2232
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 2233

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2235
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 2236
        cmds += customRefresh() // library marker kkossev.commonLib, line 2237
    } // library marker kkossev.commonLib, line 2238
    else if (DEVICE_TYPE in  ['AqaraCube'])  { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2239
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2240
    else { // library marker kkossev.commonLib, line 2241
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 2242
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 2243
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 2244
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 2245
        } // library marker kkossev.commonLib, line 2246
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2247
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2248
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2249
        } // library marker kkossev.commonLib, line 2250
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 2251
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2252
        } // library marker kkossev.commonLib, line 2253
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 2254
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2255
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 2256
        } // library marker kkossev.commonLib, line 2257
    } // library marker kkossev.commonLib, line 2258

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2260
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2261
    } // library marker kkossev.commonLib, line 2262
    else { // library marker kkossev.commonLib, line 2263
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2264
    } // library marker kkossev.commonLib, line 2265
} // library marker kkossev.commonLib, line 2266

/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2268
void setRefreshRequest()   { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 2269
/* groovylint-disable-next-line SpaceAfterClosingBrace */ // library marker kkossev.commonLib, line 2270
void clearRefreshRequest() { if (state.states == null) { state.states = [:] }; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 2271

void clearInfoEvent() { // library marker kkossev.commonLib, line 2273
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2274
} // library marker kkossev.commonLib, line 2275

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2277
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 2278
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 2279
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 2280
    } // library marker kkossev.commonLib, line 2281
    else { // library marker kkossev.commonLib, line 2282
        logInfo "${info}" // library marker kkossev.commonLib, line 2283
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 2284
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2285
    } // library marker kkossev.commonLib, line 2286
} // library marker kkossev.commonLib, line 2287

void ping() { // library marker kkossev.commonLib, line 2289
    if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 2290
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2291
        logInfo 'ping() command is not available for this sleepy device.' // library marker kkossev.commonLib, line 2292
        sendRttEvent('n/a') // library marker kkossev.commonLib, line 2293
    } // library marker kkossev.commonLib, line 2294
    else { // library marker kkossev.commonLib, line 2295
        if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2296
        state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 2297
        //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 2298
        state.states['isPing'] = true // library marker kkossev.commonLib, line 2299
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2300
        if (isVirtual()) { // library marker kkossev.commonLib, line 2301
            runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 2302
        } // library marker kkossev.commonLib, line 2303
        else { // library marker kkossev.commonLib, line 2304
            sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2305
        } // library marker kkossev.commonLib, line 2306
        logDebug 'ping...' // library marker kkossev.commonLib, line 2307
    } // library marker kkossev.commonLib, line 2308
} // library marker kkossev.commonLib, line 2309

def virtualPong() { // library marker kkossev.commonLib, line 2311
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 2312
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2313
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 2314
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 2315
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 2316
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 2317
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 2318
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 2319
        sendRttEvent() // library marker kkossev.commonLib, line 2320
    } // library marker kkossev.commonLib, line 2321
    else { // library marker kkossev.commonLib, line 2322
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 2323
    } // library marker kkossev.commonLib, line 2324
    state.states['isPing'] = false // library marker kkossev.commonLib, line 2325
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 2326
} // library marker kkossev.commonLib, line 2327

/** // library marker kkossev.commonLib, line 2329
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2330
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2331
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2332
 * @return none // library marker kkossev.commonLib, line 2333
 */ // library marker kkossev.commonLib, line 2334
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2335
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 2336
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2337
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 2338
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 2339
    if (value == null) { // library marker kkossev.commonLib, line 2340
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2341
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 2342
    } // library marker kkossev.commonLib, line 2343
    else { // library marker kkossev.commonLib, line 2344
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2345
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2346
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 2347
    } // library marker kkossev.commonLib, line 2348
} // library marker kkossev.commonLib, line 2349

/** // library marker kkossev.commonLib, line 2351
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2352
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2353
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2354
 */ // library marker kkossev.commonLib, line 2355
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2356
    if (cluster != null) { // library marker kkossev.commonLib, line 2357
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2358
    } // library marker kkossev.commonLib, line 2359
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 2360
    return 'NULL' // library marker kkossev.commonLib, line 2361
} // library marker kkossev.commonLib, line 2362

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2364
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2365
} // library marker kkossev.commonLib, line 2366

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2368
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2369
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 2370
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2371
} // library marker kkossev.commonLib, line 2372

/** // library marker kkossev.commonLib, line 2374
 * Schedule a device health check // library marker kkossev.commonLib, line 2375
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2376
 */ // library marker kkossev.commonLib, line 2377
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2378
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2379
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 2380
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2381
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2382
    } // library marker kkossev.commonLib, line 2383
    else { // library marker kkossev.commonLib, line 2384
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 2385
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2386
    } // library marker kkossev.commonLib, line 2387
} // library marker kkossev.commonLib, line 2388

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2390
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2391
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2392
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 2393
} // library marker kkossev.commonLib, line 2394

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2396
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2397
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2398
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2399
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 2400
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2401
        logInfo 'is now online!' // library marker kkossev.commonLib, line 2402
    } // library marker kkossev.commonLib, line 2403
} // library marker kkossev.commonLib, line 2404

void deviceHealthCheck() { // library marker kkossev.commonLib, line 2406
    checkDriverVersion() // library marker kkossev.commonLib, line 2407
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2408
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2409
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2410
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2411
            logWarn 'not present!' // library marker kkossev.commonLib, line 2412
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2413
        } // library marker kkossev.commonLib, line 2414
    } // library marker kkossev.commonLib, line 2415
    else { // library marker kkossev.commonLib, line 2416
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2417
    } // library marker kkossev.commonLib, line 2418
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2419
} // library marker kkossev.commonLib, line 2420

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 2422
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2423
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2424
    if (value == 'online') { // library marker kkossev.commonLib, line 2425
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2426
    } // library marker kkossev.commonLib, line 2427
    else { // library marker kkossev.commonLib, line 2428
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2429
    } // library marker kkossev.commonLib, line 2430
} // library marker kkossev.commonLib, line 2431

/** // library marker kkossev.commonLib, line 2433
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2434
 */ // library marker kkossev.commonLib, line 2435
void autoPoll() { // library marker kkossev.commonLib, line 2436
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 2437
    checkDriverVersion() // library marker kkossev.commonLib, line 2438
    List<String> cmds = [] // library marker kkossev.commonLib, line 2439
    //if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2440
    //state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2441
    // TODO !!!!!!!! // library marker kkossev.commonLib, line 2442
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 2443
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2444
    } // library marker kkossev.commonLib, line 2445

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2447
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2448
    } // library marker kkossev.commonLib, line 2449
} // library marker kkossev.commonLib, line 2450

/** // library marker kkossev.commonLib, line 2452
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2453
 */ // library marker kkossev.commonLib, line 2454
void updated() { // library marker kkossev.commonLib, line 2455
    logInfo 'updated()...' // library marker kkossev.commonLib, line 2456
    checkDriverVersion() // library marker kkossev.commonLib, line 2457
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2458
    unschedule() // library marker kkossev.commonLib, line 2459

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2461
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2462
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2463
    } // library marker kkossev.commonLib, line 2464
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2465
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 2466
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2467
    } // library marker kkossev.commonLib, line 2468

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2470
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2471
        // schedule the periodic timer // library marker kkossev.commonLib, line 2472
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2473
        if (interval > 0) { // library marker kkossev.commonLib, line 2474
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2475
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2476
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2477
        } // library marker kkossev.commonLib, line 2478
    } // library marker kkossev.commonLib, line 2479
    else { // library marker kkossev.commonLib, line 2480
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2481
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 2482
    } // library marker kkossev.commonLib, line 2483
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 2484
        customUpdated() // library marker kkossev.commonLib, line 2485
    } // library marker kkossev.commonLib, line 2486

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 2488
} // library marker kkossev.commonLib, line 2489

/** // library marker kkossev.commonLib, line 2491
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2492
 */ // library marker kkossev.commonLib, line 2493
void logsOff() { // library marker kkossev.commonLib, line 2494
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 2495
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2496
} // library marker kkossev.commonLib, line 2497
void traceOff() { // library marker kkossev.commonLib, line 2498
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 2499
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2500
} // library marker kkossev.commonLib, line 2501

void configure(String command) { // library marker kkossev.commonLib, line 2503
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2504
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2505
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2506
        return // library marker kkossev.commonLib, line 2507
    } // library marker kkossev.commonLib, line 2508
    // // library marker kkossev.commonLib, line 2509
    String func // library marker kkossev.commonLib, line 2510
    try { // library marker kkossev.commonLib, line 2511
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2512
        "$func"() // library marker kkossev.commonLib, line 2513
    } // library marker kkossev.commonLib, line 2514
    catch (e) { // library marker kkossev.commonLib, line 2515
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2516
        return // library marker kkossev.commonLib, line 2517
    } // library marker kkossev.commonLib, line 2518
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2519
} // library marker kkossev.commonLib, line 2520

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 2522
void configureHelp(final String val) { // library marker kkossev.commonLib, line 2523
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2524
} // library marker kkossev.commonLib, line 2525

void loadAllDefaults() { // library marker kkossev.commonLib, line 2527
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 2528
    deleteAllSettings() // library marker kkossev.commonLib, line 2529
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2530
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2531
    deleteAllStates() // library marker kkossev.commonLib, line 2532
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2533
    initialize() // library marker kkossev.commonLib, line 2534
    configure()     // calls  also   configureDevice() // library marker kkossev.commonLib, line 2535
    updated() // library marker kkossev.commonLib, line 2536
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 2537
} // library marker kkossev.commonLib, line 2538

void configureNow() { // library marker kkossev.commonLib, line 2540
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 2541
} // library marker kkossev.commonLib, line 2542

/** // library marker kkossev.commonLib, line 2544
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2545
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 2546
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2547
 */ // library marker kkossev.commonLib, line 2548
List<String> configure() { // library marker kkossev.commonLib, line 2549
    List<String> cmds = [] // library marker kkossev.commonLib, line 2550
    logInfo 'configure...' // library marker kkossev.commonLib, line 2551
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 2552
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2553
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 2554
        aqaraBlackMagic() // library marker kkossev.commonLib, line 2555
    } // library marker kkossev.commonLib, line 2556
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2557
    cmds += configureDevice() // library marker kkossev.commonLib, line 2558
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2559
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 2560
    return cmds // library marker kkossev.commonLib, line 2561
} // library marker kkossev.commonLib, line 2562

/** // library marker kkossev.commonLib, line 2564
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2565
 */ // library marker kkossev.commonLib, line 2566
void installed() { // library marker kkossev.commonLib, line 2567
    logInfo 'installed...' // library marker kkossev.commonLib, line 2568
    // populate some default values for attributes // library marker kkossev.commonLib, line 2569
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2570
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2571
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 2572
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2573
} // library marker kkossev.commonLib, line 2574

/** // library marker kkossev.commonLib, line 2576
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2577
 */ // library marker kkossev.commonLib, line 2578
void initialize() { // library marker kkossev.commonLib, line 2579
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2580
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2581
    updateTuyaVersion() // library marker kkossev.commonLib, line 2582
    updateAqaraVersion() // library marker kkossev.commonLib, line 2583
} // library marker kkossev.commonLib, line 2584

/* // library marker kkossev.commonLib, line 2586
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2587
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2588
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2589
*/ // library marker kkossev.commonLib, line 2590

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2592
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2593
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2594
} // library marker kkossev.commonLib, line 2595

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 2597
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2598
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2599
} // library marker kkossev.commonLib, line 2600

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 2602
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 2603
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 2604
} // library marker kkossev.commonLib, line 2605

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 2607
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2608
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2609
    cmd.each { // library marker kkossev.commonLib, line 2610
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2611
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 2612
    } // library marker kkossev.commonLib, line 2613
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2614
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2615
} // library marker kkossev.commonLib, line 2616

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 2618

String getDeviceInfo() { // library marker kkossev.commonLib, line 2620
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2621
} // library marker kkossev.commonLib, line 2622

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2624
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 2625
} // library marker kkossev.commonLib, line 2626

void checkDriverVersion() { // library marker kkossev.commonLib, line 2628
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2629
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2630
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2631
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2632
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2633
        updateTuyaVersion() // library marker kkossev.commonLib, line 2634
        updateAqaraVersion() // library marker kkossev.commonLib, line 2635
    } // library marker kkossev.commonLib, line 2636
    // no driver version change // library marker kkossev.commonLib, line 2637
} // library marker kkossev.commonLib, line 2638

// credits @thebearmay // library marker kkossev.commonLib, line 2640
String getModel() { // library marker kkossev.commonLib, line 2641
    try { // library marker kkossev.commonLib, line 2642
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 2643
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2644
    } catch (ignore) { // library marker kkossev.commonLib, line 2645
        try { // library marker kkossev.commonLib, line 2646
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2647
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2648
                return model // library marker kkossev.commonLib, line 2649
            } // library marker kkossev.commonLib, line 2650
        } catch (ignore_again) { // library marker kkossev.commonLib, line 2651
            return '' // library marker kkossev.commonLib, line 2652
        } // library marker kkossev.commonLib, line 2653
    } // library marker kkossev.commonLib, line 2654
} // library marker kkossev.commonLib, line 2655

// credits @thebearmay // library marker kkossev.commonLib, line 2657
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2658
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2659
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2660
    String revision = tokens.last() // library marker kkossev.commonLib, line 2661
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2662
} // library marker kkossev.commonLib, line 2663

/** // library marker kkossev.commonLib, line 2665
 * called from TODO // library marker kkossev.commonLib, line 2666
 */ // library marker kkossev.commonLib, line 2667

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2669
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2670
    unschedule() // library marker kkossev.commonLib, line 2671
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2672
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2673

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2675
} // library marker kkossev.commonLib, line 2676

void resetStatistics() { // library marker kkossev.commonLib, line 2678
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 2679
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 2680
} // library marker kkossev.commonLib, line 2681

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 2683
void resetStats() { // library marker kkossev.commonLib, line 2684
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 2685
    state.stats = [:] // library marker kkossev.commonLib, line 2686
    state.states = [:] // library marker kkossev.commonLib, line 2687
    state.lastRx = [:] // library marker kkossev.commonLib, line 2688
    state.lastTx = [:] // library marker kkossev.commonLib, line 2689
    state.health = [:] // library marker kkossev.commonLib, line 2690
    state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 2691
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 2692
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 2693
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 2694
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 2695
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 2696
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 2697
} // library marker kkossev.commonLib, line 2698

/** // library marker kkossev.commonLib, line 2700
 * called from TODO // library marker kkossev.commonLib, line 2701
 */ // library marker kkossev.commonLib, line 2702
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2703
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2704
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2705
        state.clear() // library marker kkossev.commonLib, line 2706
        unschedule() // library marker kkossev.commonLib, line 2707
        resetStats() // library marker kkossev.commonLib, line 2708
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2709
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2710
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 2711
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2712
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2713
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2714
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 2715
    } // library marker kkossev.commonLib, line 2716

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2718
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2719
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2720
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2721
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2722
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2723

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 2725
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 2726
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 2727
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 2728
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 2729
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2730
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 2731
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 2732
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 2733
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 2734

    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2736
        if (fullInit || settings?.minReportingTime == null) { device.updateSetting('minReportingTime', [value:DEFAULT_MIN_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2737
        if (fullInit || settings?.maxReportingTime == null) { device.updateSetting('maxReportingTime', [value:DEFAULT_MAX_REPORTING_TIME, type:'number']) } // library marker kkossev.commonLib, line 2738
    } // library marker kkossev.commonLib, line 2739
    if (device.hasCapability('IlluminanceMeasurement')) { // library marker kkossev.commonLib, line 2740
        if (fullInit || settings?.illuminanceThreshold == null) { device.updateSetting('illuminanceThreshold', [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:'number']) } // library marker kkossev.commonLib, line 2741
        if (fullInit || settings?.illuminanceCoeff == null) { device.updateSetting('illuminanceCoeff', [value:1.00, type:'decimal']) } // library marker kkossev.commonLib, line 2742
    } // library marker kkossev.commonLib, line 2743
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2744
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 2745
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 2746
    if (DEVICE_TYPE in ['AqaraCube'])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2747
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2748

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 2750
    if ( mm != null) { // library marker kkossev.commonLib, line 2751
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 2752
    } // library marker kkossev.commonLib, line 2753
    else { // library marker kkossev.commonLib, line 2754
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 2755
    } // library marker kkossev.commonLib, line 2756
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2757
    if ( ep  != null) { // library marker kkossev.commonLib, line 2758
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2759
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2760
    } // library marker kkossev.commonLib, line 2761
    else { // library marker kkossev.commonLib, line 2762
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 2763
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2764
    } // library marker kkossev.commonLib, line 2765
} // library marker kkossev.commonLib, line 2766

/** // library marker kkossev.commonLib, line 2768
 * called from TODO // library marker kkossev.commonLib, line 2769
 */ // library marker kkossev.commonLib, line 2770
void setDestinationEP() { // library marker kkossev.commonLib, line 2771
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 2772
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2773
        state.destinationEP = ep // library marker kkossev.commonLib, line 2774
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2775
    } // library marker kkossev.commonLib, line 2776
    else { // library marker kkossev.commonLib, line 2777
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2778
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 2779
    } // library marker kkossev.commonLib, line 2780
} // library marker kkossev.commonLib, line 2781

void  logDebug(final String msg) { // library marker kkossev.commonLib, line 2783
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2784
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2785
    } // library marker kkossev.commonLib, line 2786
} // library marker kkossev.commonLib, line 2787

void logInfo(final String msg) { // library marker kkossev.commonLib, line 2789
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 2790
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2791
    } // library marker kkossev.commonLib, line 2792
} // library marker kkossev.commonLib, line 2793

void logWarn(final String msg) { // library marker kkossev.commonLib, line 2795
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 2796
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2797
    } // library marker kkossev.commonLib, line 2798
} // library marker kkossev.commonLib, line 2799

void logTrace(final String msg) { // library marker kkossev.commonLib, line 2801
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 2802
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2803
    } // library marker kkossev.commonLib, line 2804
} // library marker kkossev.commonLib, line 2805

// _DEBUG mode only // library marker kkossev.commonLib, line 2807
void getAllProperties() { // library marker kkossev.commonLib, line 2808
    log.trace 'Properties:' // library marker kkossev.commonLib, line 2809
    device.properties.each { it -> // library marker kkossev.commonLib, line 2810
        log.debug it // library marker kkossev.commonLib, line 2811
    } // library marker kkossev.commonLib, line 2812
    log.trace 'Settings:' // library marker kkossev.commonLib, line 2813
    settings.each { it -> // library marker kkossev.commonLib, line 2814
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2815
    } // library marker kkossev.commonLib, line 2816
    log.trace 'Done' // library marker kkossev.commonLib, line 2817
} // library marker kkossev.commonLib, line 2818

// delete all Preferences // library marker kkossev.commonLib, line 2820
void deleteAllSettings() { // library marker kkossev.commonLib, line 2821
    settings.each { it -> // library marker kkossev.commonLib, line 2822
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2823
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2824
    } // library marker kkossev.commonLib, line 2825
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 2826
} // library marker kkossev.commonLib, line 2827

// delete all attributes // library marker kkossev.commonLib, line 2829
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2830
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 2831
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2832
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2833
    } // library marker kkossev.commonLib, line 2834
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 2835
} // library marker kkossev.commonLib, line 2836

// delete all State Variables // library marker kkossev.commonLib, line 2838
void deleteAllStates() { // library marker kkossev.commonLib, line 2839
    state.each { it -> // library marker kkossev.commonLib, line 2840
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2841
    } // library marker kkossev.commonLib, line 2842
    state.clear() // library marker kkossev.commonLib, line 2843
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 2844
} // library marker kkossev.commonLib, line 2845

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2847
    unschedule() // library marker kkossev.commonLib, line 2848
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 2849
} // library marker kkossev.commonLib, line 2850

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2852
    logDebug 'deleteAllChildDevices : not implemented!' // library marker kkossev.commonLib, line 2853
} // library marker kkossev.commonLib, line 2854

void parseTest(String par) { // library marker kkossev.commonLib, line 2856
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2857
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2858
    parse(par) // library marker kkossev.commonLib, line 2859
} // library marker kkossev.commonLib, line 2860

def testJob() { // library marker kkossev.commonLib, line 2862
    log.warn 'test job executed' // library marker kkossev.commonLib, line 2863
} // library marker kkossev.commonLib, line 2864

/** // library marker kkossev.commonLib, line 2866
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2867
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2868
 */ // library marker kkossev.commonLib, line 2869
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2870
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2871
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2872
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2873
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2874
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2875
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2876
    String cron // library marker kkossev.commonLib, line 2877
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2878
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2879
    } // library marker kkossev.commonLib, line 2880
    else { // library marker kkossev.commonLib, line 2881
        if (minutes < 60) { // library marker kkossev.commonLib, line 2882
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2883
        } // library marker kkossev.commonLib, line 2884
        else { // library marker kkossev.commonLib, line 2885
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2886
        } // library marker kkossev.commonLib, line 2887
    } // library marker kkossev.commonLib, line 2888
    return cron // library marker kkossev.commonLib, line 2889
} // library marker kkossev.commonLib, line 2890

// credits @thebearmay // library marker kkossev.commonLib, line 2892
String formatUptime() { // library marker kkossev.commonLib, line 2893
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2894
} // library marker kkossev.commonLib, line 2895

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2897
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2898
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2899
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2900
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2901
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2902
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2903
} // library marker kkossev.commonLib, line 2904

boolean isTuya() { // library marker kkossev.commonLib, line 2906
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2907
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2908
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2909
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2910
} // library marker kkossev.commonLib, line 2911

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2913
    if (!isTuya()) { // library marker kkossev.commonLib, line 2914
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2915
        return // library marker kkossev.commonLib, line 2916
    } // library marker kkossev.commonLib, line 2917
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2918
    if (application != null) { // library marker kkossev.commonLib, line 2919
        Integer ver // library marker kkossev.commonLib, line 2920
        try { // library marker kkossev.commonLib, line 2921
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2922
        } // library marker kkossev.commonLib, line 2923
        catch (e) { // library marker kkossev.commonLib, line 2924
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2925
            return // library marker kkossev.commonLib, line 2926
        } // library marker kkossev.commonLib, line 2927
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2928
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2929
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2930
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2931
        } // library marker kkossev.commonLib, line 2932
    } // library marker kkossev.commonLib, line 2933
} // library marker kkossev.commonLib, line 2934

boolean isAqara() { // library marker kkossev.commonLib, line 2936
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2937
} // library marker kkossev.commonLib, line 2938

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2940
    if (!isAqara()) { // library marker kkossev.commonLib, line 2941
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2942
        return // library marker kkossev.commonLib, line 2943
    } // library marker kkossev.commonLib, line 2944
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2945
    if (application != null) { // library marker kkossev.commonLib, line 2946
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2947
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2948
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2949
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2950
        } // library marker kkossev.commonLib, line 2951
    } // library marker kkossev.commonLib, line 2952
} // library marker kkossev.commonLib, line 2953

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2955
    try { // library marker kkossev.commonLib, line 2956
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2957
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2958
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2959
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2960
    } catch (e) { // library marker kkossev.commonLib, line 2961
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2962
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2963
    } // library marker kkossev.commonLib, line 2964
} // library marker kkossev.commonLib, line 2965

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2967
    try { // library marker kkossev.commonLib, line 2968
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2969
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2970
        return date.getTime() // library marker kkossev.commonLib, line 2971
    } catch (e) { // library marker kkossev.commonLib, line 2972
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2973
        return now() // library marker kkossev.commonLib, line 2974
    } // library marker kkossev.commonLib, line 2975
} // library marker kkossev.commonLib, line 2976

void test(String par) { // library marker kkossev.commonLib, line 2978
    List<String> cmds = [] // library marker kkossev.commonLib, line 2979
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2980

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2982
    //parse(par) // library marker kkossev.commonLib, line 2983

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2985
} // library marker kkossev.commonLib, line 2986

// ~~~~~ end include (144) kkossev.commonLib ~~~~~
