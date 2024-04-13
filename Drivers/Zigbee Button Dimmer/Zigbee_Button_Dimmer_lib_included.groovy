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
 * ver. 3.0.4  2024-04-01 kkossev  - commonLib 3.0.4; added 'Schneider Electric WDE002924'
 * ver. 3.0.5  2024-04-05 kkossev  - fixed digital button events exception; reverseButton option enabled for Tuya devices only; added 'FLSSYSTEM-M4' alternative model name, when modified by the Zigbee - Generic Switch driver
 * ver. 3.0.6  2024-04-07 kkossev  - zigbee groups library; setLevel exception bug fix;
 *
 *                                   TODO: initialize the TS004F dimmers in scene mode during pairing; stack overflow bug fix !!!!
 */

static String version() { "3.0.6" }
static String timeStamp() {"2024/04/07 8:47 AM"}

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
        name: 'Zigbee Button Dimmer',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee%20Button%20Dimmer/Zigbee_Button_Dimmer_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true )
    {

        capability 'Configuration'
        capability 'Refresh'
        capability 'Health Check'
        capability 'Actuator'
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
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0001,0003,0004,0006,1000,0000', outClusters:'0003,0004,0005,0006,0008,1000,0019,000A', model:'TS004F', manufacturer:'_TZ3000_abrsvsou', deviceJoinName: 'Tuya Smart Knob TS004F' //KK (white)
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
        fingerprint profileId:'0104', endpointId:'15', inClusters:'0000,0001,0003,0020,FF17', outClusters:'0003,0004,0005,0006,0008,0019,0102', model:'FLSSYSTEM-M4', manufacturer:'Schneider Electric',   deviceJoinName: 'Schneider Electric WDE002924'       // alternative model name, when modified by the Zigbee - Generic Switch driver

    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
        if (isTuya()) {
            input name: 'reverseButton', type: 'bool', title: '<b>Reverse button order</b>', defaultValue: true, description: '<i>Switches button order </i>'
        }
        input name: 'debounce', type: 'enum', title: '<b>Debouncing</b>', options: DebounceOpts.options, defaultValue: DebounceOpts.defaultValue, required: true, description: '<i>Debouncing options for Tuya devices.</i>'
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
    sendButtonEvent(buttonNumber, buttonState)
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

void customSetLevel(int level, int transitionTime) {
    logDebug "customSetLevel: level = $level, transitionTime = $transitionTime"
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

void sendDigitalLevelEvent(int level) {
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
    return cmds
}
*/

def customConfigureDevice() {
    List<String> cmds = []
    // TODO !!
    logDebug "customConfigureDevice() : ${cmds}"
    return cmds
}

List<String> customInitializeDevice() {
    List<String> cmds = []
    if (!isTuya()) {
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
    }

    logDebug "customInitializeDevice() : ${cmds}"
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
    version: '3.0.6', // library marker kkossev.commonLib, line 10
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
  * ver. 3.0.6  2024-04-08 kkossev  - (dev. branch) removed isZigUSB() dependency; removed aqaraCube() dependency; removed button code; removed lightSensor code; moved zigbeeGroups and level and battery methods to dedicated libs + setLevel bug fix; // library marker kkossev.commonLib, line 38
  * // library marker kkossev.commonLib, line 39
  *                                   TODO: refresh() to bypass the duplicated events and minimim delta time between events checks // library marker kkossev.commonLib, line 40
  *                                   TODO: remove the isAqaraTRV_OLD() dependency from the lib // library marker kkossev.commonLib, line 41
  *                                   TODO: add GetInfo (endpoints list) command // library marker kkossev.commonLib, line 42
  *                                   TODO: disableDefaultResponse for Tuya commands // library marker kkossev.commonLib, line 43
  * // library marker kkossev.commonLib, line 44
*/ // library marker kkossev.commonLib, line 45

String commonLibVersion() { '3.0.6' } // library marker kkossev.commonLib, line 47
String commonLibStamp() { '2024/04/08 10:51 PM' } // library marker kkossev.commonLib, line 48

import groovy.transform.Field // library marker kkossev.commonLib, line 50
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 51
import hubitat.device.Protocol // library marker kkossev.commonLib, line 52
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 53
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 54
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 55
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 56
import groovy.transform.CompileStatic // library marker kkossev.commonLib, line 57
import java.math.BigDecimal // library marker kkossev.commonLib, line 58

@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 60

metadata { // library marker kkossev.commonLib, line 62
        if (_DEBUG) { // library marker kkossev.commonLib, line 63
            command 'test', [[name: 'test', type: 'STRING', description: 'test', defaultValue : '']] // library marker kkossev.commonLib, line 64
            command 'parseTest', [[name: 'parseTest', type: 'STRING', description: 'parseTest', defaultValue : '']] // library marker kkossev.commonLib, line 65
            command 'tuyaTest', [ // library marker kkossev.commonLib, line 66
                [name:'dpCommand', type: 'STRING', description: 'Tuya DP Command', constraints: ['STRING']], // library marker kkossev.commonLib, line 67
                [name:'dpValue',   type: 'STRING', description: 'Tuya DP value', constraints: ['STRING']], // library marker kkossev.commonLib, line 68
                [name:'dpType',    type: 'ENUM',   constraints: ['DP_TYPE_VALUE', 'DP_TYPE_BOOL', 'DP_TYPE_ENUM'], description: 'DP data type'] // library marker kkossev.commonLib, line 69
            ] // library marker kkossev.commonLib, line 70
        } // library marker kkossev.commonLib, line 71

        // common capabilities for all device types // library marker kkossev.commonLib, line 73
        capability 'Configuration' // library marker kkossev.commonLib, line 74
        capability 'Refresh' // library marker kkossev.commonLib, line 75
        capability 'Health Check' // library marker kkossev.commonLib, line 76

        // common attributes for all device types // library marker kkossev.commonLib, line 78
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 79
        attribute 'rtt', 'number' // library marker kkossev.commonLib, line 80
        attribute 'Status', 'string' // library marker kkossev.commonLib, line 81

        // common commands for all device types // library marker kkossev.commonLib, line 83
        command 'configure', [[name:'normally it is not needed to configure anything', type: 'ENUM',   constraints: /*['--- select ---'] +*/ ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 84

        if (deviceType in  ['Switch', 'Dimmer', 'Bulb']) { // library marker kkossev.commonLib, line 86
            capability 'Switch' // library marker kkossev.commonLib, line 87
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 88
                attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 89
            } // library marker kkossev.commonLib, line 90
        } // library marker kkossev.commonLib, line 91

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 93
        fingerprint profileId:'0104', endpointId:'F2', inClusters:'', outClusters:'', model:'unknown', manufacturer:'unknown', deviceJoinName: 'Zigbee device affected by Hubitat F2 bug' // library marker kkossev.commonLib, line 94

    preferences { // library marker kkossev.commonLib, line 96
        // txtEnable and logEnable moved to the custom driver settings - coopy& paste there ... // library marker kkossev.commonLib, line 97
        //input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 98
        //input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 99

        if (device) { // library marker kkossev.commonLib, line 101
            input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: '<i>These advanced options should be already automatically set in an optimal way for your device...</i>', defaultValue: false // library marker kkossev.commonLib, line 102
            if (advancedOptions == true) { // library marker kkossev.commonLib, line 103
                input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 104
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 105
                if ((deviceType in  ['Switch', 'Plug', 'Dimmer', 'Fingerbot']) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 106
                    input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>Experimental multi-state switch events</i>', defaultValue: false // library marker kkossev.commonLib, line 107
                } // library marker kkossev.commonLib, line 108
                input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 109
            } // library marker kkossev.commonLib, line 110
        } // library marker kkossev.commonLib, line 111
    } // library marker kkossev.commonLib, line 112
} // library marker kkossev.commonLib, line 113

@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 115
@Field static final Integer REFRESH_TIMER = 6000             // refresh time in miliseconds // library marker kkossev.commonLib, line 116
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events // library marker kkossev.commonLib, line 117
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 118
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 119
@Field static final String  UNKNOWN = 'UNKNOWN' // library marker kkossev.commonLib, line 120
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 121
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 122
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 123
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 124
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 60      // automatically clear the Info attribute after 60 seconds // library marker kkossev.commonLib, line 125

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 127
    defaultValue: 1, // library marker kkossev.commonLib, line 128
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 129
] // library marker kkossev.commonLib, line 130
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 131
    defaultValue: 240, // library marker kkossev.commonLib, line 132
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 133
] // library marker kkossev.commonLib, line 134
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 135
    defaultValue: 0, // library marker kkossev.commonLib, line 136
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 137
] // library marker kkossev.commonLib, line 138

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 140
    'Configure the device'       : [key:2, function: 'configureNow'], // library marker kkossev.commonLib, line 141
    'Reset Statistics'           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 142
    '           --            '  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 143
    'Delete All Preferences'     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 144
    'Delete All Current States'  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 145
    'Delete All Scheduled Jobs'  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 146
    'Delete All State Variables' : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 147
    'Delete All Child Devices'   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 148
    '           -             '  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 149
    '*** LOAD ALL DEFAULTS ***'  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 150
] // library marker kkossev.commonLib, line 151

boolean isVirtual() { device.controllerType == null || device.controllerType == '' } // library marker kkossev.commonLib, line 153
/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 154
//def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 155
boolean isAqaraTVOC_OLD()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 156
boolean isAqaraTRV_OLD()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 157
boolean isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 158
boolean isFingerbot()  { DEVICE_TYPE == 'Fingerbot' ? isFingerbotFingerot() : false } // library marker kkossev.commonLib, line 159
//boolean isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 160
//boolean isZigUSB()     { (device?.getDataValue('model') ?: 'n/a') in ['ZigUSB'] } // library marker kkossev.commonLib, line 161

/** // library marker kkossev.commonLib, line 163
 * Parse Zigbee message // library marker kkossev.commonLib, line 164
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 165
 */ // library marker kkossev.commonLib, line 166
void parse(final String description) { // library marker kkossev.commonLib, line 167
    checkDriverVersion() // library marker kkossev.commonLib, line 168
    if (state.stats != null) { state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 169
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 170
    setHealthStatusOnline() // library marker kkossev.commonLib, line 171

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) { // library marker kkossev.commonLib, line 173
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 174
        if (this.respondsTo('parseIasMessage')) { // library marker kkossev.commonLib, line 175
            parseIasMessage(description) // library marker kkossev.commonLib, line 176
        } // library marker kkossev.commonLib, line 177
        else { // library marker kkossev.commonLib, line 178
            logDebug 'ignored IAS zone status' // library marker kkossev.commonLib, line 179
        } // library marker kkossev.commonLib, line 180
        return // library marker kkossev.commonLib, line 181
    } // library marker kkossev.commonLib, line 182
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 183
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 184
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 185
        if (settings?.logEnable) { logInfo 'Sending IAS enroll response...' } // library marker kkossev.commonLib, line 186
        String cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 187
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 188
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 189
        return // library marker kkossev.commonLib, line 190
    } // library marker kkossev.commonLib, line 191
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 192
        return // library marker kkossev.commonLib, line 193
    } // library marker kkossev.commonLib, line 194
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 195

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 197
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 198
        return // library marker kkossev.commonLib, line 199
    } // library marker kkossev.commonLib, line 200
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 201
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 202
        return // library marker kkossev.commonLib, line 203
    } // library marker kkossev.commonLib, line 204
    if (!isChattyDeviceReport(descMap)) { logDebug "parse: descMap = ${descMap} description=${description }" } // library marker kkossev.commonLib, line 205
    if (isSpammyDeviceReport(descMap)) { return } // library marker kkossev.commonLib, line 206
    // // library marker kkossev.commonLib, line 207
    //final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 208
    //final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 209
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 210

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 212
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 213
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 214
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 215
            break // library marker kkossev.commonLib, line 216
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 217
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 218
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 219
            break // library marker kkossev.commonLib, line 220
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 221
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 222
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 223
            break // library marker kkossev.commonLib, line 224
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 225
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 226
            descMap.remove('additionalAttrs')?.each { final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 227
            break // library marker kkossev.commonLib, line 228
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 229
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 230
            descMap.remove('additionalAttrs')?.each { final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 231
            break // library marker kkossev.commonLib, line 232
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 233
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 234
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 235
            break // library marker kkossev.commonLib, line 236
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 237
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 238
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 239
            break // library marker kkossev.commonLib, line 240
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro; // library marker kkossev.commonLib, line 241
            parseAnalogInputCluster(descMap, description) // library marker kkossev.commonLib, line 242
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map, description) } // library marker kkossev.commonLib, line 243
            break // library marker kkossev.commonLib, line 244
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 245
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 246
            break // library marker kkossev.commonLib, line 247
         case 0x0102 :                                      // window covering // library marker kkossev.commonLib, line 248
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 249
            break // library marker kkossev.commonLib, line 250
        case 0x0201 :                                       // Aqara E1 TRV // library marker kkossev.commonLib, line 251
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 252
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 253
            break // library marker kkossev.commonLib, line 254
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 255
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 256
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 257
            break // library marker kkossev.commonLib, line 258
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 259
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 260
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 261
            break // library marker kkossev.commonLib, line 262
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 263
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 264
            break // library marker kkossev.commonLib, line 265
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 266
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 267
            break // library marker kkossev.commonLib, line 268
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 269
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 270
            break // library marker kkossev.commonLib, line 271
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 272
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 273
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 274
            break // library marker kkossev.commonLib, line 275
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 276
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 277
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 278
            break // library marker kkossev.commonLib, line 279
        case 0xE002 : // library marker kkossev.commonLib, line 280
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 281
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 282
            break // library marker kkossev.commonLib, line 283
        case 0xEC03 :   // Linptech unknown cluster // library marker kkossev.commonLib, line 284
            parseEC03Cluster(descMap) // library marker kkossev.commonLib, line 285
            descMap.remove('additionalAttrs')?.each { final Map map -> parseEC03Cluster(descMap + map) } // library marker kkossev.commonLib, line 286
            break // library marker kkossev.commonLib, line 287
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 288
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 289
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 290
            break // library marker kkossev.commonLib, line 291
        case 0xFC11 :                                    // Sonoff // library marker kkossev.commonLib, line 292
            parseFC11Cluster(descMap) // library marker kkossev.commonLib, line 293
            descMap.remove('additionalAttrs')?.each { final Map map -> parseFC11Cluster(descMap + map) } // library marker kkossev.commonLib, line 294
            break // library marker kkossev.commonLib, line 295
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 296
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 297
            break // library marker kkossev.commonLib, line 298
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 299
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 300
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 301
            break // library marker kkossev.commonLib, line 302
        default: // library marker kkossev.commonLib, line 303
            if (settings.logEnable) { // library marker kkossev.commonLib, line 304
                logWarn "zigbee received <b>unknown cluster:${descMap.cluster} (${descMap.clusterInt})</b> message (${descMap})" // library marker kkossev.commonLib, line 305
            } // library marker kkossev.commonLib, line 306
            break // library marker kkossev.commonLib, line 307
    } // library marker kkossev.commonLib, line 308
} // library marker kkossev.commonLib, line 309

boolean isChattyDeviceReport(final Map descMap)  { // library marker kkossev.commonLib, line 311
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 312
    if (this.respondsTo('isSpammyDPsToNotTrace')) { // library marker kkossev.commonLib, line 313
        return isSpammyDPsToNotTrace(descMap) // library marker kkossev.commonLib, line 314
    } // library marker kkossev.commonLib, line 315
    return false // library marker kkossev.commonLib, line 316
} // library marker kkossev.commonLib, line 317

boolean isSpammyDeviceReport(final Map descMap) { // library marker kkossev.commonLib, line 319
    if (_TRACE_ALL == true) { return false } // library marker kkossev.commonLib, line 320
    if (this.respondsTo('isSpammyDPsToIgnore')) { // library marker kkossev.commonLib, line 321
        return isSpammyDPsToIgnore(descMap) // library marker kkossev.commonLib, line 322
    } // library marker kkossev.commonLib, line 323
    return false // library marker kkossev.commonLib, line 324
} // library marker kkossev.commonLib, line 325

/** // library marker kkossev.commonLib, line 327
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 328
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 329
 */ // library marker kkossev.commonLib, line 330
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 331
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 332
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 333
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 334
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 335
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 336
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 337
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 338
    } // library marker kkossev.commonLib, line 339
    else { // library marker kkossev.commonLib, line 340
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 341
    } // library marker kkossev.commonLib, line 342
} // library marker kkossev.commonLib, line 343

/** // library marker kkossev.commonLib, line 345
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 346
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 347
 */ // library marker kkossev.commonLib, line 348
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 349
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 350
    switch (commandId) { // library marker kkossev.commonLib, line 351
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 352
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 353
            break // library marker kkossev.commonLib, line 354
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 355
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 356
            break // library marker kkossev.commonLib, line 357
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 358
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 361
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 362
            break // library marker kkossev.commonLib, line 363
        case 0x0B: // default command response // library marker kkossev.commonLib, line 364
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 365
            break // library marker kkossev.commonLib, line 366
        default: // library marker kkossev.commonLib, line 367
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 368
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 369
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 370
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 371
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 372
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 373
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 374
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 375
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 376
            } // library marker kkossev.commonLib, line 377
            break // library marker kkossev.commonLib, line 378
    } // library marker kkossev.commonLib, line 379
} // library marker kkossev.commonLib, line 380

/** // library marker kkossev.commonLib, line 382
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 383
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 384
 */ // library marker kkossev.commonLib, line 385
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 386
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 387
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 388
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 389
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 390
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 391
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 392
    } // library marker kkossev.commonLib, line 393
    else { // library marker kkossev.commonLib, line 394
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 395
    } // library marker kkossev.commonLib, line 396
} // library marker kkossev.commonLib, line 397

/** // library marker kkossev.commonLib, line 399
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 400
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 401
 */ // library marker kkossev.commonLib, line 402
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 403
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 404
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 405
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 406
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 407
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 408
    } // library marker kkossev.commonLib, line 409
    else { // library marker kkossev.commonLib, line 410
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 411
    } // library marker kkossev.commonLib, line 412
} // library marker kkossev.commonLib, line 413

/** // library marker kkossev.commonLib, line 415
 * Zigbee Configure Reporting Response Parsing  - command 0x07 // library marker kkossev.commonLib, line 416
 */ // library marker kkossev.commonLib, line 417
void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 418
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 419
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 420
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 421
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 422
        state.reportingEnabled = true // library marker kkossev.commonLib, line 423
    } // library marker kkossev.commonLib, line 424
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 425
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 426
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 427
    } else { // library marker kkossev.commonLib, line 428
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 429
    } // library marker kkossev.commonLib, line 430
} // library marker kkossev.commonLib, line 431

/** // library marker kkossev.commonLib, line 433
 * Parses the response of reading reporting configuration - command 0x09 // library marker kkossev.commonLib, line 434
 */ // library marker kkossev.commonLib, line 435
void parseReadReportingConfigResponse(final Map descMap) { // library marker kkossev.commonLib, line 436
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0006 , data=[00, 00, 00, 00, 10, 00, 00, 58, 02] (Status: Success) min=0 max=600 // library marker kkossev.commonLib, line 437
    // TS0121 Received Read Reporting Configuration Response (0x09) for cluster:0702 , data=[00, 00, 00, 00, 25, 3C, 00, 10, 0E, 00, 00, 00, 00, 00, 00] (Status: Success) min=60 max=3600 // library marker kkossev.commonLib, line 438
    int status = zigbee.convertHexToInt(descMap.data[0])    // Status: Success (0x00) // library marker kkossev.commonLib, line 439
    //def attr = zigbee.convertHexToInt(descMap.data[3])*256 + zigbee.convertHexToInt(descMap.data[2])    // Attribute: OnOff (0x0000) // library marker kkossev.commonLib, line 440
    if (status == 0) { // library marker kkossev.commonLib, line 441
        //def dataType = zigbee.convertHexToInt(descMap.data[4])    // Data Type: Boolean (0x10) // library marker kkossev.commonLib, line 442
        int min = zigbee.convertHexToInt(descMap.data[6]) * 256 + zigbee.convertHexToInt(descMap.data[5]) // library marker kkossev.commonLib, line 443
        int max = zigbee.convertHexToInt(descMap.data[8] + descMap.data[7]) // library marker kkossev.commonLib, line 444
        int delta = 0 // library marker kkossev.commonLib, line 445
        if (descMap.data.size() >= 10) { // library marker kkossev.commonLib, line 446
            delta = zigbee.convertHexToInt(descMap.data[10] + descMap.data[9]) // library marker kkossev.commonLib, line 447
        } // library marker kkossev.commonLib, line 448
        else { // library marker kkossev.commonLib, line 449
            logTrace "descMap.data.size = ${descMap.data.size()}" // library marker kkossev.commonLib, line 450
        } // library marker kkossev.commonLib, line 451
        logDebug "Received Read Reporting Configuration Response (0x09) for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'}) min=${min} max=${max} delta=${delta}" // library marker kkossev.commonLib, line 452
    } // library marker kkossev.commonLib, line 453
    else { // library marker kkossev.commonLib, line 454
        logWarn "<b>Not Found (0x8b)</b> Read Reporting Configuration Response for cluster:${descMap.clusterId} attribute:${descMap.data[3] + descMap.data[2]}, data=${descMap.data} (Status: ${descMap.data[0] == '00' ? 'Success' : '<b>Failure</b>'})" // library marker kkossev.commonLib, line 455
    } // library marker kkossev.commonLib, line 456
} // library marker kkossev.commonLib, line 457

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 459
def executeCustomHandler(String handlerName, handlerArgs) { // library marker kkossev.commonLib, line 460
    if (!this.respondsTo(handlerName)) { // library marker kkossev.commonLib, line 461
        logDebug "executeCustomHandler: function <b>${handlerName}</b> not found" // library marker kkossev.commonLib, line 462
        return false // library marker kkossev.commonLib, line 463
    } // library marker kkossev.commonLib, line 464
    // execute the customHandler function // library marker kkossev.commonLib, line 465
    boolean result = false // library marker kkossev.commonLib, line 466
    try { // library marker kkossev.commonLib, line 467
        result = "$handlerName"(handlerArgs) // library marker kkossev.commonLib, line 468
    } // library marker kkossev.commonLib, line 469
    catch (e) { // library marker kkossev.commonLib, line 470
        logWarn "executeCustomHandler: Exception '${e}'caught while processing <b>$handlerName</b>(<b>$handlerArgs</b>) (val=${fncmd}))" // library marker kkossev.commonLib, line 471
        return false // library marker kkossev.commonLib, line 472
    } // library marker kkossev.commonLib, line 473
    //logDebug "customSetFunction result is ${fncmd}" // library marker kkossev.commonLib, line 474
    return result // library marker kkossev.commonLib, line 475
} // library marker kkossev.commonLib, line 476

/** // library marker kkossev.commonLib, line 478
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 479
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 480
 */ // library marker kkossev.commonLib, line 481
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 482
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 483
    final String commandId = data[0] // library marker kkossev.commonLib, line 484
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 485
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 486
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 487
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 488
    } else { // library marker kkossev.commonLib, line 489
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 490
        // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 491
        if (this.respondsTo('customParseDefaultCommandResponse')) { // library marker kkossev.commonLib, line 492
            customParseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 493
        } // library marker kkossev.commonLib, line 494
    } // library marker kkossev.commonLib, line 495
} // library marker kkossev.commonLib, line 496

// Zigbee Attribute IDs // library marker kkossev.commonLib, line 498
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 499
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 500
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 501

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 503
    0x00: 'Success', // library marker kkossev.commonLib, line 504
    0x01: 'Failure', // library marker kkossev.commonLib, line 505
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 506
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 507
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 508
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 509
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 510
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 511
    0x88: 'Read Only', // library marker kkossev.commonLib, line 512
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 513
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 514
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 515
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 516
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 517
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 518
    0x94: 'Time out', // library marker kkossev.commonLib, line 519
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 520
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 521
] // library marker kkossev.commonLib, line 522

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 524
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 525
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 526
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 527
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 528
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 529
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 530
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 531
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 532
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 533
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 534
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 535
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 536
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 537
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 538
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 539
] // library marker kkossev.commonLib, line 540

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 542
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 543
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 544
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 545
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 546
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 547
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 548
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 549
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 550
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 551
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 552
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 553
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 554
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 555
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 556
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 557
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 558
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 559
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 560
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 561
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 562
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 563
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 564
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 565
] // library marker kkossev.commonLib, line 566

void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 568
    if (xiaomiLibVersion() != null) { parseXiaomiClusterLib(descMap) } else { logWarn 'Xiaomi cluster 0xFCC0' } // library marker kkossev.commonLib, line 569
} // library marker kkossev.commonLib, line 570

@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 572
BigDecimal approxRollingAverage(BigDecimal avgPar, BigDecimal newSample) { // library marker kkossev.commonLib, line 573
    BigDecimal avg = avgPar // library marker kkossev.commonLib, line 574
    if (avg == null || avg == 0) { avg = newSample } // library marker kkossev.commonLib, line 575
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 576
    avg += newSample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 577
    return avg // library marker kkossev.commonLib, line 578
} // library marker kkossev.commonLib, line 579

/* // library marker kkossev.commonLib, line 581
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 582
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 583
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 584
*/ // library marker kkossev.commonLib, line 585
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 586

/** // library marker kkossev.commonLib, line 588
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 589
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 590
 */ // library marker kkossev.commonLib, line 591
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 592
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 593
    state.lastRx['checkInTime'] = now // library marker kkossev.commonLib, line 594
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 595
        case 0x0000: // library marker kkossev.commonLib, line 596
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 597
            break // library marker kkossev.commonLib, line 598
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 599
            boolean isPing = state.states['isPing'] ?: false // library marker kkossev.commonLib, line 600
            if (isPing) { // library marker kkossev.commonLib, line 601
                int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 602
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 603
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 604
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 605
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 606
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 607
                    sendRttEvent() // library marker kkossev.commonLib, line 608
                } // library marker kkossev.commonLib, line 609
                else { // library marker kkossev.commonLib, line 610
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 611
                } // library marker kkossev.commonLib, line 612
                state.states['isPing'] = false // library marker kkossev.commonLib, line 613
            } // library marker kkossev.commonLib, line 614
            else { // library marker kkossev.commonLib, line 615
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 616
            } // library marker kkossev.commonLib, line 617
            break // library marker kkossev.commonLib, line 618
        case 0x0004: // library marker kkossev.commonLib, line 619
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 620
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 621
            String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 622
            if ((manufacturer == null || manufacturer == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 623
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 624
                device.updateDataValue('manufacturer', descMap?.value) // library marker kkossev.commonLib, line 625
            } // library marker kkossev.commonLib, line 626
            break // library marker kkossev.commonLib, line 627
        case 0x0005: // library marker kkossev.commonLib, line 628
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 629
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 630
            String model = device.getDataValue('model') // library marker kkossev.commonLib, line 631
            if ((model == null || model == 'unknown') && (descMap?.value != null)) { // library marker kkossev.commonLib, line 632
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 633
                device.updateDataValue('model', descMap?.value) // library marker kkossev.commonLib, line 634
            } // library marker kkossev.commonLib, line 635
            break // library marker kkossev.commonLib, line 636
        case 0x0007: // library marker kkossev.commonLib, line 637
            String powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 638
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 639
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 640
            break // library marker kkossev.commonLib, line 641
        case 0xFFDF: // library marker kkossev.commonLib, line 642
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 643
            break // library marker kkossev.commonLib, line 644
        case 0xFFE2: // library marker kkossev.commonLib, line 645
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 646
            break // library marker kkossev.commonLib, line 647
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 648
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 649
            break // library marker kkossev.commonLib, line 650
        case 0xFFFE: // library marker kkossev.commonLib, line 651
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 652
            break // library marker kkossev.commonLib, line 653
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 654
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 655
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 656
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 657
            break // library marker kkossev.commonLib, line 658
        default: // library marker kkossev.commonLib, line 659
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 660
            break // library marker kkossev.commonLib, line 661
    } // library marker kkossev.commonLib, line 662
} // library marker kkossev.commonLib, line 663

// power cluster            0x0001 // library marker kkossev.commonLib, line 665
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 666
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 667
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.commonLib, line 668
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.commonLib, line 669
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.commonLib, line 670
    } // library marker kkossev.commonLib, line 671
    if (this.respondsTo('customParsePowerCluster')) { // library marker kkossev.commonLib, line 672
        customParsePowerCluster(descMap) // library marker kkossev.commonLib, line 673
    } // library marker kkossev.commonLib, line 674
    else { // library marker kkossev.commonLib, line 675
        logDebug "zigbee received Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 676
    } // library marker kkossev.commonLib, line 677
} // library marker kkossev.commonLib, line 678

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 680
void parseIdentityCluster(final Map descMap) { logDebug 'unprocessed parseIdentityCluster' } // library marker kkossev.commonLib, line 681

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 683
    if (this.respondsTo('customParseScenesCluster')) { customParseScenesCluster(descMap) } else { logWarn "unprocessed ScenesCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 684
} // library marker kkossev.commonLib, line 685

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 687
    if (this.respondsTo('customParseGroupsCluster')) { customParseGroupsCluster(descMap) } else { logWarn "unprocessed GroupsCluster attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 688
} // library marker kkossev.commonLib, line 689

/* // library marker kkossev.commonLib, line 691
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 692
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 693
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 694
*/ // library marker kkossev.commonLib, line 695

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 697
    if (this.respondsTo('customParseOnOffCluster')) { // library marker kkossev.commonLib, line 698
        customParseOnOffCluster(descMap) // library marker kkossev.commonLib, line 699
    } // library marker kkossev.commonLib, line 700
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 701
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 702
        int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 703
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 704
    } // library marker kkossev.commonLib, line 705
    else if (descMap.attrId in ['4000', '4001', '4002', '4004', '8000', '8001', '8002', '8003']) { // library marker kkossev.commonLib, line 706
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 707
    } // library marker kkossev.commonLib, line 708
    else { // library marker kkossev.commonLib, line 709
        if (descMap.attrId != null) { logWarn "parseOnOffCluster: unprocessed attrId ${descMap.attrId}"  } // library marker kkossev.commonLib, line 710
        else { logDebug "parseOnOffCluster: skipped processing OnOIff cluster (attrId is ${descMap.attrId})" } // ZigUSB has its own interpretation of the Zigbee standards ... :( // library marker kkossev.commonLib, line 711
    } // library marker kkossev.commonLib, line 712
} // library marker kkossev.commonLib, line 713

void clearIsDigital()        { state.states['isDigital'] = false } // library marker kkossev.commonLib, line 715
void switchDebouncingClear() { state.states['debounce']  = false } // library marker kkossev.commonLib, line 716
void isRefreshRequestClear() { state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 717

void toggle() { // library marker kkossev.commonLib, line 719
    String descriptionText = 'central button switch is ' // library marker kkossev.commonLib, line 720
    String state = '' // library marker kkossev.commonLib, line 721
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off') { // library marker kkossev.commonLib, line 722
        state = 'on' // library marker kkossev.commonLib, line 723
    } // library marker kkossev.commonLib, line 724
    else { // library marker kkossev.commonLib, line 725
        state = 'off' // library marker kkossev.commonLib, line 726
    } // library marker kkossev.commonLib, line 727
    descriptionText += state // library marker kkossev.commonLib, line 728
    sendEvent(name: 'switch', value: state, descriptionText: descriptionText, type: 'physical', isStateChange: true) // library marker kkossev.commonLib, line 729
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 730
} // library marker kkossev.commonLib, line 731

void off() { // library marker kkossev.commonLib, line 733
    if (this.respondsTo('customOff')) { // library marker kkossev.commonLib, line 734
        customOff() // library marker kkossev.commonLib, line 735
        return // library marker kkossev.commonLib, line 736
    } // library marker kkossev.commonLib, line 737
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 738
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 739
        return // library marker kkossev.commonLib, line 740
    } // library marker kkossev.commonLib, line 741
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on() // library marker kkossev.commonLib, line 742
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 743
    logDebug "off() currentState=${currentState}" // library marker kkossev.commonLib, line 744
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 745
        if (currentState == 'off') { // library marker kkossev.commonLib, line 746
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 747
        } // library marker kkossev.commonLib, line 748
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 749
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 750
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 751
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 752
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 753
    } // library marker kkossev.commonLib, line 754
    /* // library marker kkossev.commonLib, line 755
    else { // library marker kkossev.commonLib, line 756
        if (currentState != 'off') { // library marker kkossev.commonLib, line 757
            logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 758
        } // library marker kkossev.commonLib, line 759
        else { // library marker kkossev.commonLib, line 760
            logDebug "ignoring off command for ${device.displayName} - already off" // library marker kkossev.commonLib, line 761
            return // library marker kkossev.commonLib, line 762
        } // library marker kkossev.commonLib, line 763
    } // library marker kkossev.commonLib, line 764
    */ // library marker kkossev.commonLib, line 765

    state.states['isDigital'] = true // library marker kkossev.commonLib, line 767
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 768
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 769
} // library marker kkossev.commonLib, line 770

void on() { // library marker kkossev.commonLib, line 772
    if (this.respondsTo('customOn')) { // library marker kkossev.commonLib, line 773
        customOn() // library marker kkossev.commonLib, line 774
        return // library marker kkossev.commonLib, line 775
    } // library marker kkossev.commonLib, line 776
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off() // library marker kkossev.commonLib, line 777
    String currentState = device.currentState('switch')?.value ?: 'n/a' // library marker kkossev.commonLib, line 778
    logDebug "on() currentState=${currentState}" // library marker kkossev.commonLib, line 779
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 780
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') { // library marker kkossev.commonLib, line 781
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 782
        } // library marker kkossev.commonLib, line 783
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 784
        String descriptionText = "${value}" // library marker kkossev.commonLib, line 785
        if (logEnable) { descriptionText += ' (2)' } // library marker kkossev.commonLib, line 786
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true) // library marker kkossev.commonLib, line 787
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 788
    } // library marker kkossev.commonLib, line 789
    /* // library marker kkossev.commonLib, line 790
    else { // library marker kkossev.commonLib, line 791
        if (currentState != 'on') { // library marker kkossev.commonLib, line 792
            logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 793
        } // library marker kkossev.commonLib, line 794
        else { // library marker kkossev.commonLib, line 795
            logDebug "ignoring on command for ${device.displayName} - already on" // library marker kkossev.commonLib, line 796
            return // library marker kkossev.commonLib, line 797
        } // library marker kkossev.commonLib, line 798
    } // library marker kkossev.commonLib, line 799
    */ // library marker kkossev.commonLib, line 800
    state.states['isDigital'] = true // library marker kkossev.commonLib, line 801
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 802
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 803
} // library marker kkossev.commonLib, line 804

void sendSwitchEvent(int switchValuePar) { // library marker kkossev.commonLib, line 806
    int switchValue = safeToInt(switchValuePar) // library marker kkossev.commonLib, line 807
    if (settings?.inverceSwitch != null && settings?.inverceSwitch == true) { // library marker kkossev.commonLib, line 808
        switchValue = (switchValue == 0x00) ? 0x01 : 0x00 // library marker kkossev.commonLib, line 809
    } // library marker kkossev.commonLib, line 810
    String value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 811
    Map map = [:] // library marker kkossev.commonLib, line 812
    boolean debounce = state.states['debounce'] ?: false // library marker kkossev.commonLib, line 813
    String lastSwitch = state.states['lastSwitch'] ?: 'unknown' // library marker kkossev.commonLib, line 814
    if (value == lastSwitch && (debounce || (settings.ignoreDuplicated ?: false))) { // library marker kkossev.commonLib, line 815
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 816
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 817
        return // library marker kkossev.commonLib, line 818
    } // library marker kkossev.commonLib, line 819
    logTrace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 820
    boolean isDigital = state.states['isDigital'] ?: false // library marker kkossev.commonLib, line 821
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.commonLib, line 822
    if (lastSwitch != value) { // library marker kkossev.commonLib, line 823
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 824
        state.states['debounce'] = true // library marker kkossev.commonLib, line 825
        state.states['lastSwitch'] = value // library marker kkossev.commonLib, line 826
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 827
    } else { // library marker kkossev.commonLib, line 828
        state.states['debounce'] = true // library marker kkossev.commonLib, line 829
        runInMillis(DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 830
    } // library marker kkossev.commonLib, line 831
    map.name = 'switch' // library marker kkossev.commonLib, line 832
    map.value = value // library marker kkossev.commonLib, line 833
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.commonLib, line 834
    if (isRefresh) { // library marker kkossev.commonLib, line 835
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 836
        map.isStateChange = true // library marker kkossev.commonLib, line 837
    } else { // library marker kkossev.commonLib, line 838
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 839
    } // library marker kkossev.commonLib, line 840
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 841
    sendEvent(map) // library marker kkossev.commonLib, line 842
    clearIsDigital() // library marker kkossev.commonLib, line 843
    if (this.respondsTo('customSwitchEventPostProcesing')) { // library marker kkossev.commonLib, line 844
        customSwitchEventPostProcesing(map) // library marker kkossev.commonLib, line 845
    } // library marker kkossev.commonLib, line 846
} // library marker kkossev.commonLib, line 847

@Field static final Map powerOnBehaviourOptions = [ // library marker kkossev.commonLib, line 849
    '0': 'switch off', // library marker kkossev.commonLib, line 850
    '1': 'switch on', // library marker kkossev.commonLib, line 851
    '2': 'switch last state' // library marker kkossev.commonLib, line 852
] // library marker kkossev.commonLib, line 853

@Field static final Map switchTypeOptions = [ // library marker kkossev.commonLib, line 855
    '0': 'toggle', // library marker kkossev.commonLib, line 856
    '1': 'state', // library marker kkossev.commonLib, line 857
    '2': 'momentary' // library marker kkossev.commonLib, line 858
] // library marker kkossev.commonLib, line 859

Map myParseDescriptionAsMap(String description) { // library marker kkossev.commonLib, line 861
    Map descMap = [:] // library marker kkossev.commonLib, line 862
    try { // library marker kkossev.commonLib, line 863
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 864
    } // library marker kkossev.commonLib, line 865
    catch (e1) { // library marker kkossev.commonLib, line 866
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 867
        // try alternative custom parsing // library marker kkossev.commonLib, line 868
        descMap = [:] // library marker kkossev.commonLib, line 869
        try { // library marker kkossev.commonLib, line 870
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 871
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 872
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 873
            } // library marker kkossev.commonLib, line 874
        } // library marker kkossev.commonLib, line 875
        catch (e2) { // library marker kkossev.commonLib, line 876
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 877
            return [:] // library marker kkossev.commonLib, line 878
        } // library marker kkossev.commonLib, line 879
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 880
    } // library marker kkossev.commonLib, line 881
    return descMap // library marker kkossev.commonLib, line 882
} // library marker kkossev.commonLib, line 883

boolean isTuyaE00xCluster(String description) { // library marker kkossev.commonLib, line 885
    if (description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 886
        return false // library marker kkossev.commonLib, line 887
    } // library marker kkossev.commonLib, line 888
    // try to parse ... // library marker kkossev.commonLib, line 889
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 890
    Map descMap = [:] // library marker kkossev.commonLib, line 891
    try { // library marker kkossev.commonLib, line 892
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 893
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 894
    } // library marker kkossev.commonLib, line 895
    catch (e) { // library marker kkossev.commonLib, line 896
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 897
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 898
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 899
        return true // library marker kkossev.commonLib, line 900
    } // library marker kkossev.commonLib, line 901

    if (descMap.cluster == 'E000' && descMap.attrId in ['D001', 'D002', 'D003']) { // library marker kkossev.commonLib, line 903
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 904
    } // library marker kkossev.commonLib, line 905
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D010') { // library marker kkossev.commonLib, line 906
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 907
    } // library marker kkossev.commonLib, line 908
    else if (descMap.cluster == 'E001' && descMap.attrId == 'D030') { // library marker kkossev.commonLib, line 909
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 910
    } // library marker kkossev.commonLib, line 911
    else { // library marker kkossev.commonLib, line 912
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 913
        return false // library marker kkossev.commonLib, line 914
    } // library marker kkossev.commonLib, line 915
    return true    // processed // library marker kkossev.commonLib, line 916
} // library marker kkossev.commonLib, line 917

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 919
boolean otherTuyaOddities(final String description) { // library marker kkossev.commonLib, line 920
  /* // library marker kkossev.commonLib, line 921
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 922
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4 // library marker kkossev.commonLib, line 923
        return true // library marker kkossev.commonLib, line 924
    } // library marker kkossev.commonLib, line 925
*/ // library marker kkossev.commonLib, line 926
    Map descMap = [:] // library marker kkossev.commonLib, line 927
    try { // library marker kkossev.commonLib, line 928
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 929
    } // library marker kkossev.commonLib, line 930
    catch (e1) { // library marker kkossev.commonLib, line 931
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 932
        // try alternative custom parsing // library marker kkossev.commonLib, line 933
        descMap = [:] // library marker kkossev.commonLib, line 934
        try { // library marker kkossev.commonLib, line 935
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 936
                List<String> pair = entry.split(':') // library marker kkossev.commonLib, line 937
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 938
            } // library marker kkossev.commonLib, line 939
        } // library marker kkossev.commonLib, line 940
        catch (e2) { // library marker kkossev.commonLib, line 941
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 942
            return true // library marker kkossev.commonLib, line 943
        } // library marker kkossev.commonLib, line 944
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 945
    } // library marker kkossev.commonLib, line 946
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"} // library marker kkossev.commonLib, line 947
    if (descMap.attrId == null) { // library marker kkossev.commonLib, line 948
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 949
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 950
        return false // library marker kkossev.commonLib, line 951
    } // library marker kkossev.commonLib, line 952
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 953
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 954
    // attribute report received // library marker kkossev.commonLib, line 955
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 956
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 957
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 958
    //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 959
    } // library marker kkossev.commonLib, line 960
    attrData.each { // library marker kkossev.commonLib, line 961
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 962
        //def map = [:] // library marker kkossev.commonLib, line 963
        if (it.status == '86') { // library marker kkossev.commonLib, line 964
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 965
        // TODO - skip parsing? // library marker kkossev.commonLib, line 966
        } // library marker kkossev.commonLib, line 967
        switch (it.cluster) { // library marker kkossev.commonLib, line 968
            case '0000' : // library marker kkossev.commonLib, line 969
                if (it.attrId in ['FFE0', 'FFE1', 'FFE2', 'FFE4']) { // library marker kkossev.commonLib, line 970
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 971
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 972
                } // library marker kkossev.commonLib, line 973
                else if (it.attrId in ['FFFE', 'FFDF']) { // library marker kkossev.commonLib, line 974
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 975
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 976
                } // library marker kkossev.commonLib, line 977
                else { // library marker kkossev.commonLib, line 978
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 979
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 980
                } // library marker kkossev.commonLib, line 981
                break // library marker kkossev.commonLib, line 982
            default : // library marker kkossev.commonLib, line 983
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 984
                break // library marker kkossev.commonLib, line 985
        } // switch // library marker kkossev.commonLib, line 986
    } // for each attribute // library marker kkossev.commonLib, line 987
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 988
} // library marker kkossev.commonLib, line 989

private boolean isCircuitBreaker()      { device.getDataValue('manufacturer') in ['_TZ3000_ky0fq4ho'] } // library marker kkossev.commonLib, line 991

void parseOnOffAttributes(final Map it) { // library marker kkossev.commonLib, line 993
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 994
    /* groovylint-disable-next-line VariableTypeRequired */ // library marker kkossev.commonLib, line 995
    def mode // library marker kkossev.commonLib, line 996
    String attrName // library marker kkossev.commonLib, line 997
    if (it.value == null) { // library marker kkossev.commonLib, line 998
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 999
        return // library marker kkossev.commonLib, line 1000
    } // library marker kkossev.commonLib, line 1001
    int value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1002
    switch (it.attrId) { // library marker kkossev.commonLib, line 1003
        case '4000' :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1004
            attrName = 'Global Scene Control' // library marker kkossev.commonLib, line 1005
            mode = value == 0 ? 'off' : value == 1 ? 'on' : null // library marker kkossev.commonLib, line 1006
            break // library marker kkossev.commonLib, line 1007
        case '4001' :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1008
            attrName = 'On Time' // library marker kkossev.commonLib, line 1009
            mode = value // library marker kkossev.commonLib, line 1010
            break // library marker kkossev.commonLib, line 1011
        case '4002' :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1012
            attrName = 'Off Wait Time' // library marker kkossev.commonLib, line 1013
            mode = value // library marker kkossev.commonLib, line 1014
            break // library marker kkossev.commonLib, line 1015
        case '4003' :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1 // library marker kkossev.commonLib, line 1016
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1017
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : 'UNKNOWN' // library marker kkossev.commonLib, line 1018
            break // library marker kkossev.commonLib, line 1019
        case '8000' :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1020
            attrName = 'Child Lock' // library marker kkossev.commonLib, line 1021
            mode = value == 0 ? 'off' : 'on' // library marker kkossev.commonLib, line 1022
            break // library marker kkossev.commonLib, line 1023
        case '8001' :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1024
            attrName = 'LED mode' // library marker kkossev.commonLib, line 1025
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1026
                mode = value == 0 ? 'Always Green' : value == 1 ? 'Red when On; Green when Off' : value == 2 ? 'Green when On; Red when Off' : value == 3 ? 'Always Red' : null // library marker kkossev.commonLib, line 1027
            } // library marker kkossev.commonLib, line 1028
            else { // library marker kkossev.commonLib, line 1029
                mode = value == 0 ? 'Disabled' : value == 1 ? 'Lit when On' : value == 2 ? 'Lit when Off' : value == 3 ? 'Freeze' : null // library marker kkossev.commonLib, line 1030
            } // library marker kkossev.commonLib, line 1031
            break // library marker kkossev.commonLib, line 1032
        case '8002' :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1033
            attrName = 'Power On State' // library marker kkossev.commonLib, line 1034
            mode = value == 0 ? 'off' : value == 1 ? 'on' : value == 2 ?  'Last state' : null // library marker kkossev.commonLib, line 1035
            break // library marker kkossev.commonLib, line 1036
        case '8003' : //  Over current alarm // library marker kkossev.commonLib, line 1037
            attrName = 'Over current alarm' // library marker kkossev.commonLib, line 1038
            mode = value == 0 ? 'Over Current OK' : value == 1 ? 'Over Current Alarm' : null // library marker kkossev.commonLib, line 1039
            break // library marker kkossev.commonLib, line 1040
        default : // library marker kkossev.commonLib, line 1041
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1042
            return // library marker kkossev.commonLib, line 1043
    } // library marker kkossev.commonLib, line 1044
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1045
} // library marker kkossev.commonLib, line 1046

void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1048
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1049
        customParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1050
    } // library marker kkossev.commonLib, line 1051
    else if (this.respondsTo('levelLibParseLevelControlCluster')) { // library marker kkossev.commonLib, line 1052
        levelLibParseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 1053
    } // library marker kkossev.commonLib, line 1054
    else { // library marker kkossev.commonLib, line 1055
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1056
    } // library marker kkossev.commonLib, line 1057
} // library marker kkossev.commonLib, line 1058

String intTo16bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1060
    String hexStr = zigbee.convertToHexString(value.toInteger(), 4) // library marker kkossev.commonLib, line 1061
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1062
} // library marker kkossev.commonLib, line 1063

String intTo8bitUnsignedHex(int value) { // library marker kkossev.commonLib, line 1065
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1066
} // library marker kkossev.commonLib, line 1067

void parseColorControlCluster(final Map descMap, String description) { // library marker kkossev.commonLib, line 1069
    if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.commonLib, line 1070
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1071
    } // library marker kkossev.commonLib, line 1072
    else if (descMap.attrId == '0000') { // library marker kkossev.commonLib, line 1073
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1074
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1075
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1076
    } // library marker kkossev.commonLib, line 1077
    else { // library marker kkossev.commonLib, line 1078
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1079
    } // library marker kkossev.commonLib, line 1080
} // library marker kkossev.commonLib, line 1081

void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1083
    if (this.respondsTo('customParseIlluminanceCluster')) { customParseIlluminanceCluster(descMap) } else { logWarn "unprocessed Illuminance attribute ${descMap.attrId}" } // library marker kkossev.commonLib, line 1084
} // library marker kkossev.commonLib, line 1085

// Temperature Measurement Cluster 0x0402 // library marker kkossev.commonLib, line 1087
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1088
    if (this.respondsTo('customParseTemperatureCluster')) { // library marker kkossev.commonLib, line 1089
        customParseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 1090
    } // library marker kkossev.commonLib, line 1091
    else { // library marker kkossev.commonLib, line 1092
        logWarn "unprocessed Temperature attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1093
    } // library marker kkossev.commonLib, line 1094
} // library marker kkossev.commonLib, line 1095

// Humidity Measurement Cluster 0x0405 // library marker kkossev.commonLib, line 1097
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1098
    if (this.respondsTo('customParseHumidityCluster')) { // library marker kkossev.commonLib, line 1099
        customParseHumidityCluster(descMap) // library marker kkossev.commonLib, line 1100
    } // library marker kkossev.commonLib, line 1101
    else { // library marker kkossev.commonLib, line 1102
        logWarn "unprocessed Humidity attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1103
    } // library marker kkossev.commonLib, line 1104
} // library marker kkossev.commonLib, line 1105

// Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1107
void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1108
    if (!executeCustomHandler('customParseElectricalMeasureCluster', descMap)) { logWarn 'parseElectricalMeasureCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1109
} // library marker kkossev.commonLib, line 1110

// Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1112
void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1113
    if (!executeCustomHandler('customParseMeteringCluster', descMap)) { logWarn 'parseMeteringCluster is NOT implemented1' } // library marker kkossev.commonLib, line 1114
} // library marker kkossev.commonLib, line 1115

// pm2.5 // library marker kkossev.commonLib, line 1117
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1118
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1119
    int value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1120
    /* groovylint-disable-next-line NoFloat */ // library marker kkossev.commonLib, line 1121
    float floatValue  = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1122
    if (this.respondsTo('handlePm25Event')) { // library marker kkossev.commonLib, line 1123
        handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1124
    } // library marker kkossev.commonLib, line 1125
    else { // library marker kkossev.commonLib, line 1126
        logWarn "handlePm25Event: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1127
    } // library marker kkossev.commonLib, line 1128
} // library marker kkossev.commonLib, line 1129

// Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1131
void parseAnalogInputCluster(final Map descMap, String description=null) { // library marker kkossev.commonLib, line 1132
    if (this.respondsTo('customParseAnalogInputCluster')) { // library marker kkossev.commonLib, line 1133
        customParseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1134
    } // library marker kkossev.commonLib, line 1135
    else if (this.respondsTo('customParseAnalogInputClusterDescription')) { // library marker kkossev.commonLib, line 1136
        customParseAnalogInputClusterDescription(description)                   // ZigUSB // library marker kkossev.commonLib, line 1137
    } // library marker kkossev.commonLib, line 1138
    else if (DEVICE_TYPE in ['AirQuality']) { // library marker kkossev.commonLib, line 1139
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1140
    } // library marker kkossev.commonLib, line 1141
    else { // library marker kkossev.commonLib, line 1142
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1143
    } // library marker kkossev.commonLib, line 1144
} // library marker kkossev.commonLib, line 1145

// Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1147
void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1148
    if (this.respondsTo('customParseMultistateInputCluster')) { customParseMultistateInputCluster(descMap) } else { logWarn "parseMultistateInputCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1149
} // library marker kkossev.commonLib, line 1150

// Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1152
void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1153
    if (this.respondsTo('customParseWindowCoveringCluster')) { customParseWindowCoveringCluster(descMap) } else { logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1154
} // library marker kkossev.commonLib, line 1155

// thermostat cluster 0x0201 // library marker kkossev.commonLib, line 1157
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 1158
    if (this.respondsTo('customParseThermostatCluster')) { customParseThermostatCluster(descMap) } else { logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1159
} // library marker kkossev.commonLib, line 1160

void parseFC11Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1162
    if (this.respondsTo('customParseFC11Cluster')) { customParseFC11Cluster(descMap) } else { logWarn "parseFC11Cluster: don't know how to handle descMap=${descMap}" } // library marker kkossev.commonLib, line 1163
} // library marker kkossev.commonLib, line 1164

void parseE002Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1166
    if (this.respondsTo('customParseE002Cluster')) { customParseE002Cluster(descMap) } else { logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }    // radars // library marker kkossev.commonLib, line 1167
} // library marker kkossev.commonLib, line 1168

void parseEC03Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1170
    if (this.respondsTo('customParseEC03Cluster')) { customParseEC03Cluster(descMap) } else { logWarn "Unprocessed cluster 0xEC03C command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" }   // radars // library marker kkossev.commonLib, line 1171
} // library marker kkossev.commonLib, line 1172

/* // library marker kkossev.commonLib, line 1174
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1175
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 1176
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1177
*/ // library marker kkossev.commonLib, line 1178
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 1179
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 1180
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 1181

// Tuya Commands // library marker kkossev.commonLib, line 1183
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 1184
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 1185
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 1186
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 1187
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 1188

// tuya DP type // library marker kkossev.commonLib, line 1190
private static getDP_TYPE_RAW()        { '01' }    // [ bytes ] // library marker kkossev.commonLib, line 1191
private static getDP_TYPE_BOOL()       { '01' }    // [ 0/1 ] // library marker kkossev.commonLib, line 1192
private static getDP_TYPE_VALUE()      { '02' }    // [ 4 byte value ] // library marker kkossev.commonLib, line 1193
private static getDP_TYPE_STRING()     { '03' }    // [ N byte string ] // library marker kkossev.commonLib, line 1194
private static getDP_TYPE_ENUM()       { '04' }    // [ 0-255 ] // library marker kkossev.commonLib, line 1195
private static getDP_TYPE_BITMAP()     { '05' }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 1196

void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 1198
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '24') {        //getSETTIME // library marker kkossev.commonLib, line 1199
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 1200
        Long offset = 0 // library marker kkossev.commonLib, line 1201
        try { // library marker kkossev.commonLib, line 1202
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 1203
        } // library marker kkossev.commonLib, line 1204
        catch (e) { // library marker kkossev.commonLib, line 1205
            logWarn 'cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero' // library marker kkossev.commonLib, line 1206
        } // library marker kkossev.commonLib, line 1207
        String cmds = zigbee.command(CLUSTER_TUYA, SETTIME, '0008' + zigbee.convertToHexString((int)(now() / 1000), 8) + zigbee.convertToHexString((int)((now() + offset) / 1000), 8)) // library marker kkossev.commonLib, line 1208
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 1209
        cmds.each { sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 1210
    //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 1211
    } // library marker kkossev.commonLib, line 1212
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == '0B') {    // ZCL Command Default Response // library marker kkossev.commonLib, line 1213
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 1214
        String status = descMap?.data[1] // library marker kkossev.commonLib, line 1215
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 1216
        if (status != '00') { // library marker kkossev.commonLib, line 1217
            logWarn "ATTENTION! manufacturer = ${device.getDataValue('manufacturer')} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!" // library marker kkossev.commonLib, line 1218
        } // library marker kkossev.commonLib, line 1219
    } // library marker kkossev.commonLib, line 1220
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == '01' || descMap?.command == '02' || descMap?.command == '05' || descMap?.command == '06')) { // library marker kkossev.commonLib, line 1221
        int dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 1222
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 1223
        //def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 1224
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 1225
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 1226
            return // library marker kkossev.commonLib, line 1227
        } // library marker kkossev.commonLib, line 1228
        for (int i = 0; i < (dataLen - 4); ) { // library marker kkossev.commonLib, line 1229
            int dp = zigbee.convertHexToInt(descMap?.data[2 + i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 1230
            int dp_id = zigbee.convertHexToInt(descMap?.data[3 + i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 1231
            int fncmd_len = zigbee.convertHexToInt(descMap?.data[5 + i]) // library marker kkossev.commonLib, line 1232
            int fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 1233
            logDebug "parseTuyaCluster: command=${descMap?.command} dp_id=${dp_id} dp=${dp} (0x${descMap?.data[2 + i]}) fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 1234
            processTuyaDP(descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 1235
            i = i + fncmd_len + 4 // library marker kkossev.commonLib, line 1236
        } // library marker kkossev.commonLib, line 1237
    } // library marker kkossev.commonLib, line 1238
    else { // library marker kkossev.commonLib, line 1239
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 1240
    } // library marker kkossev.commonLib, line 1241
} // library marker kkossev.commonLib, line 1242

void processTuyaDP(final Map descMap, final int dp, final int dp_id, final int fncmd, final int dp_len=0) { // library marker kkossev.commonLib, line 1244
    logTrace "processTuyaDP: <b> checking customProcessTuyaDp</b> dp=${dp} dp_id=${dp_id} fncmd=${fncmd} dp_len=${dp_len}" // library marker kkossev.commonLib, line 1245
    if (this.respondsTo(customProcessTuyaDp)) { // library marker kkossev.commonLib, line 1246
        logTrace 'customProcessTuyaDp exists, calling it...' // library marker kkossev.commonLib, line 1247
        if (customProcessTuyaDp(descMap, dp, dp_id, fncmd, dp_len) == true) { // library marker kkossev.commonLib, line 1248
            return // library marker kkossev.commonLib, line 1249
        } // library marker kkossev.commonLib, line 1250
    } // library marker kkossev.commonLib, line 1251
    // check if the method  method exists // library marker kkossev.commonLib, line 1252
    if (this.respondsTo(processTuyaDPfromDeviceProfile)) { // library marker kkossev.commonLib, line 1253
        if (processTuyaDPfromDeviceProfile(descMap, dp, dp_id, fncmd, dp_len) == true) {    // sucessfuly processed the new way - we are done.  version 3.0 // library marker kkossev.commonLib, line 1254
            return // library marker kkossev.commonLib, line 1255
        } // library marker kkossev.commonLib, line 1256
    } // library marker kkossev.commonLib, line 1257
    logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" // library marker kkossev.commonLib, line 1258
} // library marker kkossev.commonLib, line 1259

private int getTuyaAttributeValue(final List<String> _data, final int index) { // library marker kkossev.commonLib, line 1261
    int retValue = 0 // library marker kkossev.commonLib, line 1262
    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 1263
        int dataLength = zigbee.convertHexToInt(_data[5 + index]) // library marker kkossev.commonLib, line 1264
        int power = 1 // library marker kkossev.commonLib, line 1265
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 1266
            retValue = retValue + power * zigbee.convertHexToInt(_data[index + i + 5]) // library marker kkossev.commonLib, line 1267
            power = power * 256 // library marker kkossev.commonLib, line 1268
        } // library marker kkossev.commonLib, line 1269
    } // library marker kkossev.commonLib, line 1270
    return retValue // library marker kkossev.commonLib, line 1271
} // library marker kkossev.commonLib, line 1272

private List<String> sendTuyaCommand(String dp, String dp_type, String fncmd) { // library marker kkossev.commonLib, line 1274
    List<String> cmds = [] // library marker kkossev.commonLib, line 1275
    int ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 1276
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1277
    final int tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 1278
    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length() / 2), 4) + fncmd ) // library marker kkossev.commonLib, line 1279
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 1280
    return cmds // library marker kkossev.commonLib, line 1281
} // library marker kkossev.commonLib, line 1282

private getPACKET_ID() { // library marker kkossev.commonLib, line 1284
    return zigbee.convertToHexString(new Random().nextInt(65536), 4) // library marker kkossev.commonLib, line 1285
} // library marker kkossev.commonLib, line 1286

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1288
void tuyaTest(String dpCommand, String dpValue, String dpTypeString ) { // library marker kkossev.commonLib, line 1289
    String dpType   = dpTypeString == 'DP_TYPE_VALUE' ? DP_TYPE_VALUE : dpTypeString == 'DP_TYPE_BOOL' ? DP_TYPE_BOOL : dpTypeString == 'DP_TYPE_ENUM' ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 1290
    String dpValHex = dpTypeString == 'DP_TYPE_VALUE' ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 1291
    if (settings?.logEnable) { log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" } // library marker kkossev.commonLib, line 1292
    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 1293
} // library marker kkossev.commonLib, line 1294

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 1296
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 1297

String tuyaBlackMagic() { // library marker kkossev.commonLib, line 1299
    int ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 1300
    if (ep == null || ep == 0) { ep = 1 } // library marker kkossev.commonLib, line 1301
    logInfo 'tuyaBlackMagic()...' // library marker kkossev.commonLib, line 1302
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay = 200) // library marker kkossev.commonLib, line 1303
} // library marker kkossev.commonLib, line 1304

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 1306
    List<String> cmds = [] // library marker kkossev.commonLib, line 1307
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1308
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", 'delay 200',] // library marker kkossev.commonLib, line 1309
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1310
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 1311
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 1312
        if (isAqaraTVOC_OLD()) { // library marker kkossev.commonLib, line 1313
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay = 200)    // TVOC only // library marker kkossev.commonLib, line 1314
        } // library marker kkossev.commonLib, line 1315
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1316
        logDebug 'sent aqaraBlackMagic()' // library marker kkossev.commonLib, line 1317
    } // library marker kkossev.commonLib, line 1318
    else { // library marker kkossev.commonLib, line 1319
        logDebug 'aqaraBlackMagic() was SKIPPED' // library marker kkossev.commonLib, line 1320
    } // library marker kkossev.commonLib, line 1321
} // library marker kkossev.commonLib, line 1322

/** // library marker kkossev.commonLib, line 1324
 * initializes the device // library marker kkossev.commonLib, line 1325
 * Invoked from configure() // library marker kkossev.commonLib, line 1326
 * @return zigbee commands // library marker kkossev.commonLib, line 1327
 */ // library marker kkossev.commonLib, line 1328
List<String> initializeDevice() { // library marker kkossev.commonLib, line 1329
    List<String> cmds = [] // library marker kkossev.commonLib, line 1330
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 1331
    if (this.respondsTo('customInitializeDevice')) { // library marker kkossev.commonLib, line 1332
        List<String> customCmds = customInitializeDevice() // library marker kkossev.commonLib, line 1333
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1334
    } // library marker kkossev.commonLib, line 1335
    return cmds // library marker kkossev.commonLib, line 1336
} // library marker kkossev.commonLib, line 1337

/** // library marker kkossev.commonLib, line 1339
 * configures the device // library marker kkossev.commonLib, line 1340
 * Invoked from configure() // library marker kkossev.commonLib, line 1341
 * @return zigbee commands // library marker kkossev.commonLib, line 1342
 */ // library marker kkossev.commonLib, line 1343
List<String> configureDevice() { // library marker kkossev.commonLib, line 1344
    List<String> cmds = [] // library marker kkossev.commonLib, line 1345
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 1346

    if (this.respondsTo('customConfigureDevice')) { // library marker kkossev.commonLib, line 1348
        List<String> customCmds = customConfigureDevice() // library marker kkossev.commonLib, line 1349
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1350
    } // library marker kkossev.commonLib, line 1351
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 1352
    // sendZigbeeCommands(cmds) changed 03/04/2024 // library marker kkossev.commonLib, line 1353
    return cmds // library marker kkossev.commonLib, line 1354
} // library marker kkossev.commonLib, line 1355

/* // library marker kkossev.commonLib, line 1357
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1358
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 1359
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1360
*/ // library marker kkossev.commonLib, line 1361

void refresh() { // library marker kkossev.commonLib, line 1363
    logDebug "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1364
    checkDriverVersion() // library marker kkossev.commonLib, line 1365
    List<String> cmds = [] // library marker kkossev.commonLib, line 1366
    setRefreshRequest()    // 3 seconds // library marker kkossev.commonLib, line 1367

    // device type specific refresh handlers // library marker kkossev.commonLib, line 1369
    if (this.respondsTo('customRefresh')) { // library marker kkossev.commonLib, line 1370
        List<String> customCmds = customRefresh() // library marker kkossev.commonLib, line 1371
        if (customCmds != null && customCmds != []) { cmds +=  customCmds } // library marker kkossev.commonLib, line 1372
    } // library marker kkossev.commonLib, line 1373
    else if (DEVICE_TYPE in  ['Bulb'])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 1374
    else { // library marker kkossev.commonLib, line 1375
        // generic refresh handling, based on teh device capabilities // library marker kkossev.commonLib, line 1376
        if (device.hasCapability('Battery')) { // library marker kkossev.commonLib, line 1377
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay = 200)         // battery voltage // library marker kkossev.commonLib, line 1378
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay = 200)         // battery percentage // library marker kkossev.commonLib, line 1379
        } // library marker kkossev.commonLib, line 1380
        if (DEVICE_TYPE in  ['Dimmer']) { // library marker kkossev.commonLib, line 1381
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1382
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1383
        } // library marker kkossev.commonLib, line 1384
        if (DEVICE_TYPE in  ['THSensor']) { // library marker kkossev.commonLib, line 1385
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1386
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay = 200) // library marker kkossev.commonLib, line 1387
        } // library marker kkossev.commonLib, line 1388
    } // library marker kkossev.commonLib, line 1389

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1391
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1392
    } // library marker kkossev.commonLib, line 1393
    else { // library marker kkossev.commonLib, line 1394
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1395
    } // library marker kkossev.commonLib, line 1396
} // library marker kkossev.commonLib, line 1397

void setRefreshRequest()   { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = true; runInMillis(REFRESH_TIMER, clearRefreshRequest, [overwrite: true]) } // library marker kkossev.commonLib, line 1399
void clearRefreshRequest() { if (state.states == null) { state.states = [:] } ; state.states['isRefresh'] = false } // library marker kkossev.commonLib, line 1400

void clearInfoEvent() { // library marker kkossev.commonLib, line 1402
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 1403
} // library marker kkossev.commonLib, line 1404

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 1406
    if (info == null || info == 'clear') { // library marker kkossev.commonLib, line 1407
        logDebug 'clearing the Status event' // library marker kkossev.commonLib, line 1408
        sendEvent(name: 'Status', value: 'clear', isDigital: true) // library marker kkossev.commonLib, line 1409
    } // library marker kkossev.commonLib, line 1410
    else { // library marker kkossev.commonLib, line 1411
        logInfo "${info}" // library marker kkossev.commonLib, line 1412
        sendEvent(name: 'Status', value: info, isDigital: true) // library marker kkossev.commonLib, line 1413
        runIn(INFO_AUTO_CLEAR_PERIOD, 'clearInfoEvent')            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 1414
    } // library marker kkossev.commonLib, line 1415
} // library marker kkossev.commonLib, line 1416

void ping() { // library marker kkossev.commonLib, line 1418
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1419
    state.lastTx['pingTime'] = new Date().getTime() // library marker kkossev.commonLib, line 1420
    //if (state.states == null ) { state.states = [:] } // library marker kkossev.commonLib, line 1421
    state.states['isPing'] = true // library marker kkossev.commonLib, line 1422
    scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1423
    if (isVirtual()) { // library marker kkossev.commonLib, line 1424
        runInMillis(10, virtualPong) // library marker kkossev.commonLib, line 1425
    } // library marker kkossev.commonLib, line 1426
    else { // library marker kkossev.commonLib, line 1427
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 1428
    } // library marker kkossev.commonLib, line 1429
    logDebug 'ping...' // library marker kkossev.commonLib, line 1430
} // library marker kkossev.commonLib, line 1431

def virtualPong() { // library marker kkossev.commonLib, line 1433
    logDebug 'virtualPing: pong!' // library marker kkossev.commonLib, line 1434
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1435
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: '0').toInteger() // library marker kkossev.commonLib, line 1436
    if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 1437
        state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 1438
        if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 1439
        if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 1440
        state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']), safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 1441
        sendRttEvent() // library marker kkossev.commonLib, line 1442
    } // library marker kkossev.commonLib, line 1443
    else { // library marker kkossev.commonLib, line 1444
        logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 1445
    } // library marker kkossev.commonLib, line 1446
    state.states['isPing'] = false // library marker kkossev.commonLib, line 1447
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 1448
} // library marker kkossev.commonLib, line 1449

/** // library marker kkossev.commonLib, line 1451
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 1452
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 1453
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 1454
 * @return none // library marker kkossev.commonLib, line 1455
 */ // library marker kkossev.commonLib, line 1456
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 1457
    Long now = new Date().getTime() // library marker kkossev.commonLib, line 1458
    if (state.lastTx == null ) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1459
    int timeRunning = now.toInteger() - (state.lastTx['pingTime'] ?: now).toInteger() // library marker kkossev.commonLib, line 1460
    String descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats['pingsMin']} max=${state.stats['pingsMax']} average=${state.stats['pingsAvg']})" // library marker kkossev.commonLib, line 1461
    if (value == null) { // library marker kkossev.commonLib, line 1462
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1463
        sendEvent(name: 'rtt', value: timeRunning, descriptionText: descriptionText, unit: 'ms', isDigital: true) // library marker kkossev.commonLib, line 1464
    } // library marker kkossev.commonLib, line 1465
    else { // library marker kkossev.commonLib, line 1466
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 1467
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1468
        sendEvent(name: 'rtt', value: value, descriptionText: descriptionText, isDigital: true) // library marker kkossev.commonLib, line 1469
    } // library marker kkossev.commonLib, line 1470
} // library marker kkossev.commonLib, line 1471

/** // library marker kkossev.commonLib, line 1473
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 1474
 * @param cluster cluster ID // library marker kkossev.commonLib, line 1475
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 1476
 */ // library marker kkossev.commonLib, line 1477
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 1478
    if (cluster != null) { // library marker kkossev.commonLib, line 1479
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 1480
    } // library marker kkossev.commonLib, line 1481
    logWarn 'cluster is NULL!' // library marker kkossev.commonLib, line 1482
    return 'NULL' // library marker kkossev.commonLib, line 1483
} // library marker kkossev.commonLib, line 1484

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 1486
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 1487
} // library marker kkossev.commonLib, line 1488

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 1490
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 1491
    sendRttEvent('timeout') // library marker kkossev.commonLib, line 1492
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 1493
} // library marker kkossev.commonLib, line 1494

/** // library marker kkossev.commonLib, line 1496
 * Schedule a device health check // library marker kkossev.commonLib, line 1497
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 1498
 */ // library marker kkossev.commonLib, line 1499
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 1500
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 1501
        String cron = getCron( intervalMins * 60 ) // library marker kkossev.commonLib, line 1502
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 1503
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 1504
    } // library marker kkossev.commonLib, line 1505
    else { // library marker kkossev.commonLib, line 1506
        logWarn 'deviceHealthCheck is not scheduled!' // library marker kkossev.commonLib, line 1507
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1508
    } // library marker kkossev.commonLib, line 1509
} // library marker kkossev.commonLib, line 1510

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 1512
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 1513
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 1514
    logWarn 'device health check is disabled!' // library marker kkossev.commonLib, line 1515
} // library marker kkossev.commonLib, line 1516

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 1518
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 1519
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1520
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 1521
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) { // library marker kkossev.commonLib, line 1522
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 1523
        logInfo 'is now online!' // library marker kkossev.commonLib, line 1524
    } // library marker kkossev.commonLib, line 1525
} // library marker kkossev.commonLib, line 1526

void deviceHealthCheck() { // library marker kkossev.commonLib, line 1528
    checkDriverVersion() // library marker kkossev.commonLib, line 1529
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1530
    int ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 1531
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 1532
        if ((device.currentValue('healthStatus') ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 1533
            logWarn 'not present!' // library marker kkossev.commonLib, line 1534
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 1535
        } // library marker kkossev.commonLib, line 1536
    } // library marker kkossev.commonLib, line 1537
    else { // library marker kkossev.commonLib, line 1538
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 1539
    } // library marker kkossev.commonLib, line 1540
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 1541
} // library marker kkossev.commonLib, line 1542

void sendHealthStatusEvent(final String value) { // library marker kkossev.commonLib, line 1544
    String descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 1545
    sendEvent(name: 'healthStatus', value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 1546
    if (value == 'online') { // library marker kkossev.commonLib, line 1547
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1548
    } // library marker kkossev.commonLib, line 1549
    else { // library marker kkossev.commonLib, line 1550
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 1551
    } // library marker kkossev.commonLib, line 1552
} // library marker kkossev.commonLib, line 1553

/** // library marker kkossev.commonLib, line 1555
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 1556
 */ // library marker kkossev.commonLib, line 1557
void autoPoll() { // library marker kkossev.commonLib, line 1558
    logDebug 'autoPoll()...' // library marker kkossev.commonLib, line 1559
    checkDriverVersion() // library marker kkossev.commonLib, line 1560
    List<String> cmds = [] // library marker kkossev.commonLib, line 1561
    if (DEVICE_TYPE in  ['AirQuality']) { // library marker kkossev.commonLib, line 1562
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay = 200)      // tVOC   !! mfcode = "0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 1563
    } // library marker kkossev.commonLib, line 1564

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1566
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1567
    } // library marker kkossev.commonLib, line 1568
} // library marker kkossev.commonLib, line 1569

/** // library marker kkossev.commonLib, line 1571
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 1572
 */ // library marker kkossev.commonLib, line 1573
void updated() { // library marker kkossev.commonLib, line 1574
    logInfo 'updated()...' // library marker kkossev.commonLib, line 1575
    checkDriverVersion() // library marker kkossev.commonLib, line 1576
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1577
    unschedule() // library marker kkossev.commonLib, line 1578

    if (settings.logEnable) { // library marker kkossev.commonLib, line 1580
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1581
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 1582
    } // library marker kkossev.commonLib, line 1583
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 1584
        logTrace(settings.toString()) // library marker kkossev.commonLib, line 1585
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 1586
    } // library marker kkossev.commonLib, line 1587

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 1589
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 1590
        // schedule the periodic timer // library marker kkossev.commonLib, line 1591
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 1592
        if (interval > 0) { // library marker kkossev.commonLib, line 1593
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 1594
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 1595
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 1596
        } // library marker kkossev.commonLib, line 1597
    } // library marker kkossev.commonLib, line 1598
    else { // library marker kkossev.commonLib, line 1599
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 1600
        log.info 'Health Check is disabled!' // library marker kkossev.commonLib, line 1601
    } // library marker kkossev.commonLib, line 1602
    if (this.respondsTo('customUpdated')) { // library marker kkossev.commonLib, line 1603
        customUpdated() // library marker kkossev.commonLib, line 1604
    } // library marker kkossev.commonLib, line 1605

    sendInfoEvent('updated') // library marker kkossev.commonLib, line 1607
} // library marker kkossev.commonLib, line 1608

/** // library marker kkossev.commonLib, line 1610
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 1611
 */ // library marker kkossev.commonLib, line 1612
void logsOff() { // library marker kkossev.commonLib, line 1613
    logInfo 'debug logging disabled...' // library marker kkossev.commonLib, line 1614
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1615
} // library marker kkossev.commonLib, line 1616
void traceOff() { // library marker kkossev.commonLib, line 1617
    logInfo 'trace logging disabled...' // library marker kkossev.commonLib, line 1618
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 1619
} // library marker kkossev.commonLib, line 1620

void configure(String command) { // library marker kkossev.commonLib, line 1622
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 1623
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 1624
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 1625
        return // library marker kkossev.commonLib, line 1626
    } // library marker kkossev.commonLib, line 1627
    // // library marker kkossev.commonLib, line 1628
    String func // library marker kkossev.commonLib, line 1629
    try { // library marker kkossev.commonLib, line 1630
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 1631
        "$func"() // library marker kkossev.commonLib, line 1632
    } // library marker kkossev.commonLib, line 1633
    catch (e) { // library marker kkossev.commonLib, line 1634
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1635
        return // library marker kkossev.commonLib, line 1636
    } // library marker kkossev.commonLib, line 1637
    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 1638
} // library marker kkossev.commonLib, line 1639

/* groovylint-disable-next-line UnusedMethodParameter */ // library marker kkossev.commonLib, line 1641
void configureHelp(final String val) { // library marker kkossev.commonLib, line 1642
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 1643
} // library marker kkossev.commonLib, line 1644

void loadAllDefaults() { // library marker kkossev.commonLib, line 1646
    logWarn 'loadAllDefaults() !!!' // library marker kkossev.commonLib, line 1647
    deleteAllSettings() // library marker kkossev.commonLib, line 1648
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 1649
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 1650
    deleteAllStates() // library marker kkossev.commonLib, line 1651
    deleteAllChildDevices() // library marker kkossev.commonLib, line 1652
    initialize() // library marker kkossev.commonLib, line 1653
    configureNow()     // calls  also   configureDevice()   // bug fixed 04/03/2024 // library marker kkossev.commonLib, line 1654
    updated() // library marker kkossev.commonLib, line 1655
    sendInfoEvent('All Defaults Loaded! F5 to refresh') // library marker kkossev.commonLib, line 1656
} // library marker kkossev.commonLib, line 1657

void configureNow() { // library marker kkossev.commonLib, line 1659
    sendZigbeeCommands( configure() ) // library marker kkossev.commonLib, line 1660
} // library marker kkossev.commonLib, line 1661

/** // library marker kkossev.commonLib, line 1663
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 1664
 * Invoked when device is first installed and when the user updates the configuration  TODO // library marker kkossev.commonLib, line 1665
 * @return sends zigbee commands // library marker kkossev.commonLib, line 1666
 */ // library marker kkossev.commonLib, line 1667
List<String> configure() { // library marker kkossev.commonLib, line 1668
    List<String> cmds = [] // library marker kkossev.commonLib, line 1669
    logInfo 'configure...' // library marker kkossev.commonLib, line 1670
    logDebug "configure(): settings: $settings" // library marker kkossev.commonLib, line 1671
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 1672
    if (isAqaraTVOC_OLD() || isAqaraTRV_OLD()) { // library marker kkossev.commonLib, line 1673
        aqaraBlackMagic()   // zigbee commands are sent here! // library marker kkossev.commonLib, line 1674
    } // library marker kkossev.commonLib, line 1675
    List<String> initCmds = initializeDevice() // library marker kkossev.commonLib, line 1676
    if (initCmds != null && initCmds != [] ) { cmds += initCmds } // library marker kkossev.commonLib, line 1677
    List<String> cfgCmds = configureDevice() // library marker kkossev.commonLib, line 1678
    if (cfgCmds != null && cfgCmds != [] ) { cmds += cfgCmds } // library marker kkossev.commonLib, line 1679
    // commented out 12/15/2923 sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1680
    sendInfoEvent('sent device configuration') // library marker kkossev.commonLib, line 1681
    logDebug "configure(): returning cmds = ${cmds}" // library marker kkossev.commonLib, line 1682
    //return cmds // library marker kkossev.commonLib, line 1683
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 1684
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1685
    } // library marker kkossev.commonLib, line 1686
    else { // library marker kkossev.commonLib, line 1687
        logDebug "configure(): no commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1688
    } // library marker kkossev.commonLib, line 1689
} // library marker kkossev.commonLib, line 1690

/** // library marker kkossev.commonLib, line 1692
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 1693
 */ // library marker kkossev.commonLib, line 1694
void installed() { // library marker kkossev.commonLib, line 1695
    logInfo 'installed...' // library marker kkossev.commonLib, line 1696
    // populate some default values for attributes // library marker kkossev.commonLib, line 1697
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 1698
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 1699
    sendInfoEvent('installed') // library marker kkossev.commonLib, line 1700
    runIn(3, 'updated') // library marker kkossev.commonLib, line 1701
} // library marker kkossev.commonLib, line 1702

/** // library marker kkossev.commonLib, line 1704
 * Invoked when the initialize button is clicked // library marker kkossev.commonLib, line 1705
 */ // library marker kkossev.commonLib, line 1706
void initialize() { // library marker kkossev.commonLib, line 1707
    logInfo 'initialize...' // library marker kkossev.commonLib, line 1708
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 1709
    updateTuyaVersion() // library marker kkossev.commonLib, line 1710
    updateAqaraVersion() // library marker kkossev.commonLib, line 1711
} // library marker kkossev.commonLib, line 1712

/* // library marker kkossev.commonLib, line 1714
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1715
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 1716
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1717
*/ // library marker kkossev.commonLib, line 1718

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1720
static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 1721
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 1722
} // library marker kkossev.commonLib, line 1723

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDouble */ // library marker kkossev.commonLib, line 1725
static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 1726
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 1727
} // library marker kkossev.commonLib, line 1728

/* groovylint-disable-next-line MethodParameterTypeRequired */ // library marker kkossev.commonLib, line 1730
static BigDecimal safeToBigDecimal(val, BigDecimal defaultVal=0.0) { // library marker kkossev.commonLib, line 1731
    return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal // library marker kkossev.commonLib, line 1732
} // library marker kkossev.commonLib, line 1733

void sendZigbeeCommands(List<String> cmd) { // library marker kkossev.commonLib, line 1735
    if (cmd == null || cmd == [] || cmd == 'null') { // library marker kkossev.commonLib, line 1736
        logWarn 'sendZigbeeCommands: no commands to send!' // library marker kkossev.commonLib, line 1737
        return // library marker kkossev.commonLib, line 1738
    } // library marker kkossev.commonLib, line 1739
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 1740
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 1741
    cmd.each { // library marker kkossev.commonLib, line 1742
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 1743
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats = [:] } // library marker kkossev.commonLib, line 1744
    } // library marker kkossev.commonLib, line 1745
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 1746
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 1747
} // library marker kkossev.commonLib, line 1748

String driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? ' (debug version!) ' : ' ') + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString})" } // library marker kkossev.commonLib, line 1750

String getDeviceInfo() { // library marker kkossev.commonLib, line 1752
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 1753
} // library marker kkossev.commonLib, line 1754

String getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 1756
    return state.destinationEP ?: device.endpointId ?: '01' // library marker kkossev.commonLib, line 1757
} // library marker kkossev.commonLib, line 1758

void checkDriverVersion() { // library marker kkossev.commonLib, line 1760
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 1761
        logDebug "checkDriverVersion: updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 1762
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 1763
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1764
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 1765
        updateTuyaVersion() // library marker kkossev.commonLib, line 1766
        updateAqaraVersion() // library marker kkossev.commonLib, line 1767
    } // library marker kkossev.commonLib, line 1768
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1769
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1770
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1771
    if (state.stats  == null) { state.stats =  [:] } // library marker kkossev.commonLib, line 1772
} // library marker kkossev.commonLib, line 1773

// credits @thebearmay // library marker kkossev.commonLib, line 1775
String getModel() { // library marker kkossev.commonLib, line 1776
    try { // library marker kkossev.commonLib, line 1777
        /* groovylint-disable-next-line UnnecessaryGetter, UnusedVariable */ // library marker kkossev.commonLib, line 1778
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 1779
    } catch (ignore) { // library marker kkossev.commonLib, line 1780
        try { // library marker kkossev.commonLib, line 1781
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 1782
                model = res.data.device.modelName // library marker kkossev.commonLib, line 1783
                return model // library marker kkossev.commonLib, line 1784
            } // library marker kkossev.commonLib, line 1785
        } catch (ignore_again) { // library marker kkossev.commonLib, line 1786
            return '' // library marker kkossev.commonLib, line 1787
        } // library marker kkossev.commonLib, line 1788
    } // library marker kkossev.commonLib, line 1789
} // library marker kkossev.commonLib, line 1790

// credits @thebearmay // library marker kkossev.commonLib, line 1792
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 1793
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 1794
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 1795
    String revision = tokens.last() // library marker kkossev.commonLib, line 1796
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 1797
} // library marker kkossev.commonLib, line 1798

/** // library marker kkossev.commonLib, line 1800
 * called from TODO // library marker kkossev.commonLib, line 1801
 */ // library marker kkossev.commonLib, line 1802

void deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 1804
    state.clear()    // clear all states // library marker kkossev.commonLib, line 1805
    unschedule() // library marker kkossev.commonLib, line 1806
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 1807
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 1808

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 1810
} // library marker kkossev.commonLib, line 1811

void resetStatistics() { // library marker kkossev.commonLib, line 1813
    runIn(1, 'resetStats') // library marker kkossev.commonLib, line 1814
    sendInfoEvent('Statistics are reset. Refresh the web page') // library marker kkossev.commonLib, line 1815
} // library marker kkossev.commonLib, line 1816

// called from initializeVars(true) and resetStatistics() // library marker kkossev.commonLib, line 1818
void resetStats() { // library marker kkossev.commonLib, line 1819
    logDebug 'resetStats...' // library marker kkossev.commonLib, line 1820
    state.stats = [:] // library marker kkossev.commonLib, line 1821
    state.states = [:] // library marker kkossev.commonLib, line 1822
    state.lastRx = [:] // library marker kkossev.commonLib, line 1823
    state.lastTx = [:] // library marker kkossev.commonLib, line 1824
    state.health = [:] // library marker kkossev.commonLib, line 1825
    if (this.respondsTo('groupsLibVersion')) { // library marker kkossev.commonLib, line 1826
        state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1827
    } // library marker kkossev.commonLib, line 1828
    state.stats['rxCtr'] = 0 // library marker kkossev.commonLib, line 1829
    state.stats['txCtr'] = 0 // library marker kkossev.commonLib, line 1830
    state.states['isDigital'] = false // library marker kkossev.commonLib, line 1831
    state.states['isRefresh'] = false // library marker kkossev.commonLib, line 1832
    state.health['offlineCtr'] = 0 // library marker kkossev.commonLib, line 1833
    state.health['checkCtr3'] = 0 // library marker kkossev.commonLib, line 1834
} // library marker kkossev.commonLib, line 1835

/** // library marker kkossev.commonLib, line 1837
 * called from TODO // library marker kkossev.commonLib, line 1838
 */ // library marker kkossev.commonLib, line 1839
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 1840
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 1841
    if (fullInit == true ) { // library marker kkossev.commonLib, line 1842
        state.clear() // library marker kkossev.commonLib, line 1843
        unschedule() // library marker kkossev.commonLib, line 1844
        resetStats() // library marker kkossev.commonLib, line 1845
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 1846
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 1847
        logInfo 'all states and scheduled jobs cleared!' // library marker kkossev.commonLib, line 1848
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 1849
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 1850
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 1851
        sendInfoEvent('Initialized') // library marker kkossev.commonLib, line 1852
    } // library marker kkossev.commonLib, line 1853

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 1855
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1856
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1857
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 1858
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 1859

    if (fullInit || settings?.txtEnable == null) { device.updateSetting('txtEnable', true) } // library marker kkossev.commonLib, line 1861
    if (fullInit || settings?.logEnable == null) { device.updateSetting('logEnable', false) } // library marker kkossev.commonLib, line 1862
    if (fullInit || settings?.traceEnable == null) { device.updateSetting('traceEnable', false) } // library marker kkossev.commonLib, line 1863
    if (fullInit || settings?.alwaysOn == null) { device.updateSetting('alwaysOn', false) } // library marker kkossev.commonLib, line 1864
    if (fullInit || settings?.advancedOptions == null) { device.updateSetting('advancedOptions', [value:false, type:'bool']) } // library marker kkossev.commonLib, line 1865
    if (fullInit || settings?.healthCheckMethod == null) { device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1866
    if (fullInit || settings?.healthCheckInterval == null) { device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) } // library marker kkossev.commonLib, line 1867
    if (device.currentValue('healthStatus') == null) { sendHealthStatusEvent('unknown') } // library marker kkossev.commonLib, line 1868
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', false) } // library marker kkossev.commonLib, line 1869
    if ((fullInit || settings?.threeStateEnable == null) && _THREE_STATE == true) { device.updateSetting('threeStateEnable', false) } // library marker kkossev.commonLib, line 1870

    // device specific initialization should be at the end // library marker kkossev.commonLib, line 1872
    executeCustomHandler('customInitializeVars', fullInit) // library marker kkossev.commonLib, line 1873
    executeCustomHandler('customCreateChildDevices', fullInit) // library marker kkossev.commonLib, line 1874
    executeCustomHandler('customInitEvents', fullInit) // library marker kkossev.commonLib, line 1875
    if (DEVICE_TYPE in ['Bulb'])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 1876

    final String mm = device.getDataValue('model') // library marker kkossev.commonLib, line 1878
    if ( mm != null) { // library marker kkossev.commonLib, line 1879
        logTrace " model = ${mm}" // library marker kkossev.commonLib, line 1880
    } // library marker kkossev.commonLib, line 1881
    else { // library marker kkossev.commonLib, line 1882
        logWarn ' Model not found, please re-pair the device!' // library marker kkossev.commonLib, line 1883
    } // library marker kkossev.commonLib, line 1884
    final String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1885
    if ( ep  != null) { // library marker kkossev.commonLib, line 1886
        //state.destinationEP = ep // library marker kkossev.commonLib, line 1887
        logTrace " destinationEP = ${ep}" // library marker kkossev.commonLib, line 1888
    } // library marker kkossev.commonLib, line 1889
    else { // library marker kkossev.commonLib, line 1890
        logWarn ' Destination End Point not found, please re-pair the device!' // library marker kkossev.commonLib, line 1891
    //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 1892
    } // library marker kkossev.commonLib, line 1893
} // library marker kkossev.commonLib, line 1894

/** // library marker kkossev.commonLib, line 1896
 * called from TODO // library marker kkossev.commonLib, line 1897
 */ // library marker kkossev.commonLib, line 1898
void setDestinationEP() { // library marker kkossev.commonLib, line 1899
    String ep = device.getEndpointId() // library marker kkossev.commonLib, line 1900
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 1901
        state.destinationEP = ep // library marker kkossev.commonLib, line 1902
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 1903
    } // library marker kkossev.commonLib, line 1904
    else { // library marker kkossev.commonLib, line 1905
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 1906
        state.destinationEP = '01'    // fallback EP // library marker kkossev.commonLib, line 1907
    } // library marker kkossev.commonLib, line 1908
} // library marker kkossev.commonLib, line 1909

void logDebug(final String msg) { // library marker kkossev.commonLib, line 1911
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1912
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 1913
    } // library marker kkossev.commonLib, line 1914
} // library marker kkossev.commonLib, line 1915

void logInfo(final String msg) { // library marker kkossev.commonLib, line 1917
    if (settings?.txtEnable) { // library marker kkossev.commonLib, line 1918
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 1919
    } // library marker kkossev.commonLib, line 1920
} // library marker kkossev.commonLib, line 1921

void logWarn(final String msg) { // library marker kkossev.commonLib, line 1923
    if (settings?.logEnable) { // library marker kkossev.commonLib, line 1924
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 1925
    } // library marker kkossev.commonLib, line 1926
} // library marker kkossev.commonLib, line 1927

void logTrace(final String msg) { // library marker kkossev.commonLib, line 1929
    if (settings?.traceEnable) { // library marker kkossev.commonLib, line 1930
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 1931
    } // library marker kkossev.commonLib, line 1932
} // library marker kkossev.commonLib, line 1933

// _DEBUG mode only // library marker kkossev.commonLib, line 1935
void getAllProperties() { // library marker kkossev.commonLib, line 1936
    log.trace 'Properties:' // library marker kkossev.commonLib, line 1937
    device.properties.each { it -> // library marker kkossev.commonLib, line 1938
        log.debug it // library marker kkossev.commonLib, line 1939
    } // library marker kkossev.commonLib, line 1940
    log.trace 'Settings:' // library marker kkossev.commonLib, line 1941
    settings.each { it -> // library marker kkossev.commonLib, line 1942
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 1943
    } // library marker kkossev.commonLib, line 1944
    log.trace 'Done' // library marker kkossev.commonLib, line 1945
} // library marker kkossev.commonLib, line 1946

// delete all Preferences // library marker kkossev.commonLib, line 1948
void deleteAllSettings() { // library marker kkossev.commonLib, line 1949
    settings.each { it -> // library marker kkossev.commonLib, line 1950
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 1951
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 1952
    } // library marker kkossev.commonLib, line 1953
    logInfo  'All settings (preferences) DELETED' // library marker kkossev.commonLib, line 1954
} // library marker kkossev.commonLib, line 1955

// delete all attributes // library marker kkossev.commonLib, line 1957
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 1958
    device.properties.supportedAttributes.each { it -> // library marker kkossev.commonLib, line 1959
        logDebug "deleting $it" // library marker kkossev.commonLib, line 1960
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 1961
    } // library marker kkossev.commonLib, line 1962
    logInfo 'All current states (attributes) DELETED' // library marker kkossev.commonLib, line 1963
} // library marker kkossev.commonLib, line 1964

// delete all State Variables // library marker kkossev.commonLib, line 1966
void deleteAllStates() { // library marker kkossev.commonLib, line 1967
    state.each { it -> // library marker kkossev.commonLib, line 1968
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 1969
    } // library marker kkossev.commonLib, line 1970
    state.clear() // library marker kkossev.commonLib, line 1971
    logInfo 'All States DELETED' // library marker kkossev.commonLib, line 1972
} // library marker kkossev.commonLib, line 1973

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 1975
    unschedule() // library marker kkossev.commonLib, line 1976
    logInfo 'All scheduled jobs DELETED' // library marker kkossev.commonLib, line 1977
} // library marker kkossev.commonLib, line 1978

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 1980
    getChildDevices().each { child -> // library marker kkossev.commonLib, line 1981
        log.info "${device.displayName} Deleting ${child.deviceNetworkId}" // library marker kkossev.commonLib, line 1982
        deleteChildDevice(child.deviceNetworkId) // library marker kkossev.commonLib, line 1983
    } // library marker kkossev.commonLib, line 1984
    sendInfoEvent 'All child devices DELETED' // library marker kkossev.commonLib, line 1985
} // library marker kkossev.commonLib, line 1986

void parseTest(String par) { // library marker kkossev.commonLib, line 1988
    //read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 1989
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 1990
    parse(par) // library marker kkossev.commonLib, line 1991
} // library marker kkossev.commonLib, line 1992

def testJob() { // library marker kkossev.commonLib, line 1994
    log.warn 'test job executed' // library marker kkossev.commonLib, line 1995
} // library marker kkossev.commonLib, line 1996

/** // library marker kkossev.commonLib, line 1998
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 1999
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2000
 */ // library marker kkossev.commonLib, line 2001
String getCron(int timeInSeconds) { // library marker kkossev.commonLib, line 2002
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2003
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2004
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2005
    int minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2006
    int  hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2007
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2008
    String cron // library marker kkossev.commonLib, line 2009
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2010
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2011
    } // library marker kkossev.commonLib, line 2012
    else { // library marker kkossev.commonLib, line 2013
        if (minutes < 60) { // library marker kkossev.commonLib, line 2014
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *" // library marker kkossev.commonLib, line 2015
        } // library marker kkossev.commonLib, line 2016
        else { // library marker kkossev.commonLib, line 2017
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *" // library marker kkossev.commonLib, line 2018
        } // library marker kkossev.commonLib, line 2019
    } // library marker kkossev.commonLib, line 2020
    return cron // library marker kkossev.commonLib, line 2021
} // library marker kkossev.commonLib, line 2022

// credits @thebearmay // library marker kkossev.commonLib, line 2024
String formatUptime() { // library marker kkossev.commonLib, line 2025
    return formatTime(location.hub.uptime) // library marker kkossev.commonLib, line 2026
} // library marker kkossev.commonLib, line 2027

String formatTime(int timeInSeconds) { // library marker kkossev.commonLib, line 2029
    if (timeInSeconds == null) { return UNKNOWN } // library marker kkossev.commonLib, line 2030
    int days = (timeInSeconds / 86400).toInteger() // library marker kkossev.commonLib, line 2031
    int hours = ((timeInSeconds % 86400) / 3600).toInteger() // library marker kkossev.commonLib, line 2032
    int minutes = ((timeInSeconds % 3600) / 60).toInteger() // library marker kkossev.commonLib, line 2033
    int seconds = (timeInSeconds % 60).toInteger() // library marker kkossev.commonLib, line 2034
    return "${days}d ${hours}h ${minutes}m ${seconds}s" // library marker kkossev.commonLib, line 2035
} // library marker kkossev.commonLib, line 2036

boolean isTuya() { // library marker kkossev.commonLib, line 2038
    if (!device) { return true }    // fallback - added 04/03/2024 // library marker kkossev.commonLib, line 2039
    String model = device.getDataValue('model') // library marker kkossev.commonLib, line 2040
    String manufacturer = device.getDataValue('manufacturer') // library marker kkossev.commonLib, line 2041
    /* groovylint-disable-next-line UnnecessaryTernaryExpression */ // library marker kkossev.commonLib, line 2042
    return (model?.startsWith('TS') && manufacturer?.startsWith('_TZ')) ? true : false // library marker kkossev.commonLib, line 2043
} // library marker kkossev.commonLib, line 2044

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2046
    if (!isTuya()) { // library marker kkossev.commonLib, line 2047
        logTrace 'not Tuya' // library marker kkossev.commonLib, line 2048
        return // library marker kkossev.commonLib, line 2049
    } // library marker kkossev.commonLib, line 2050
    final String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2051
    if (application != null) { // library marker kkossev.commonLib, line 2052
        Integer ver // library marker kkossev.commonLib, line 2053
        try { // library marker kkossev.commonLib, line 2054
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2055
        } // library marker kkossev.commonLib, line 2056
        catch (e) { // library marker kkossev.commonLib, line 2057
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2058
            return // library marker kkossev.commonLib, line 2059
        } // library marker kkossev.commonLib, line 2060
        final String str = ((ver & 0xC0) >> 6).toString() + '.' + ((ver & 0x30) >> 4).toString() + '.' + (ver & 0x0F).toString() // library marker kkossev.commonLib, line 2061
        if (device.getDataValue('tuyaVersion') != str) { // library marker kkossev.commonLib, line 2062
            device.updateDataValue('tuyaVersion', str) // library marker kkossev.commonLib, line 2063
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2064
        } // library marker kkossev.commonLib, line 2065
    } // library marker kkossev.commonLib, line 2066
} // library marker kkossev.commonLib, line 2067

boolean isAqara() { // library marker kkossev.commonLib, line 2069
    return device.getDataValue('model')?.startsWith('lumi') ?: false // library marker kkossev.commonLib, line 2070
} // library marker kkossev.commonLib, line 2071

void updateAqaraVersion() { // library marker kkossev.commonLib, line 2073
    if (!isAqara()) { // library marker kkossev.commonLib, line 2074
        logTrace 'not Aqara' // library marker kkossev.commonLib, line 2075
        return // library marker kkossev.commonLib, line 2076
    } // library marker kkossev.commonLib, line 2077
    String application = device.getDataValue('application') // library marker kkossev.commonLib, line 2078
    if (application != null) { // library marker kkossev.commonLib, line 2079
        String str = '0.0.0_' + String.format('%04d', zigbee.convertHexToInt(application.take(2))) // library marker kkossev.commonLib, line 2080
        if (device.getDataValue('aqaraVersion') != str) { // library marker kkossev.commonLib, line 2081
            device.updateDataValue('aqaraVersion', str) // library marker kkossev.commonLib, line 2082
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2083
        } // library marker kkossev.commonLib, line 2084
    } // library marker kkossev.commonLib, line 2085
} // library marker kkossev.commonLib, line 2086

String unix2formattedDate(Long unixTime) { // library marker kkossev.commonLib, line 2088
    try { // library marker kkossev.commonLib, line 2089
        if (unixTime == null) { return null } // library marker kkossev.commonLib, line 2090
        /* groovylint-disable-next-line NoJavaUtilDate */ // library marker kkossev.commonLib, line 2091
        Date date = new Date(unixTime.toLong()) // library marker kkossev.commonLib, line 2092
        return date.format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2093
    } catch (e) { // library marker kkossev.commonLib, line 2094
        logDebug "Error formatting date: ${e.message}. Returning current time instead." // library marker kkossev.commonLib, line 2095
        return new Date().format('yyyy-MM-dd HH:mm:ss.SSS', location.timeZone) // library marker kkossev.commonLib, line 2096
    } // library marker kkossev.commonLib, line 2097
} // library marker kkossev.commonLib, line 2098

long formattedDate2unix(String formattedDate) { // library marker kkossev.commonLib, line 2100
    try { // library marker kkossev.commonLib, line 2101
        if (formattedDate == null) { return null } // library marker kkossev.commonLib, line 2102
        Date date = Date.parse('yyyy-MM-dd HH:mm:ss.SSS', formattedDate) // library marker kkossev.commonLib, line 2103
        return date.getTime() // library marker kkossev.commonLib, line 2104
    } catch (e) { // library marker kkossev.commonLib, line 2105
        logDebug "Error parsing formatted date: ${formattedDate}. Returning current time instead." // library marker kkossev.commonLib, line 2106
        return now() // library marker kkossev.commonLib, line 2107
    } // library marker kkossev.commonLib, line 2108
} // library marker kkossev.commonLib, line 2109
/* // library marker kkossev.commonLib, line 2110
void test(String par) { // library marker kkossev.commonLib, line 2111
    List<String> cmds = [] // library marker kkossev.commonLib, line 2112
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2113

    cmds = ["zdo unbind 0x${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0020 {${device.zigbeeId}} {}",] // library marker kkossev.commonLib, line 2115
    //parse(par) // library marker kkossev.commonLib, line 2116

    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2118
} // library marker kkossev.commonLib, line 2119
*/ // library marker kkossev.commonLib, line 2120

// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (167) kkossev.buttonLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.buttonLib, line 1
library( // library marker kkossev.buttonLib, line 2
    base: 'driver', // library marker kkossev.buttonLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.buttonLib, line 4
    category: 'zigbee', // library marker kkossev.buttonLib, line 5
    description: 'Zigbee Button Library', // library marker kkossev.buttonLib, line 6
    name: 'buttonLib', // library marker kkossev.buttonLib, line 7
    namespace: 'kkossev', // library marker kkossev.buttonLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/buttonLib.groovy', // library marker kkossev.buttonLib, line 9
    version: '3.0.0', // library marker kkossev.buttonLib, line 10
    documentationLink: '' // library marker kkossev.buttonLib, line 11
) // library marker kkossev.buttonLib, line 12
/* // library marker kkossev.buttonLib, line 13
 *  Zigbee Button Library // library marker kkossev.buttonLib, line 14
 * // library marker kkossev.buttonLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.buttonLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.buttonLib, line 17
 * // library marker kkossev.buttonLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.buttonLib, line 19
 * // library marker kkossev.buttonLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.buttonLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.buttonLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.buttonLib, line 23
 * // library marker kkossev.buttonLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added energyLib.groovy // library marker kkossev.buttonLib, line 25
 * // library marker kkossev.buttonLib, line 26
 *                                   TODO: // library marker kkossev.buttonLib, line 27
*/ // library marker kkossev.buttonLib, line 28

static String buttonLibVersion()   { '3.0.0' } // library marker kkossev.buttonLib, line 30
static String buttonLibStamp() { '2024/04/06 1:02 PM' } // library marker kkossev.buttonLib, line 31

/* // library marker kkossev.buttonLib, line 33
metadata { // library marker kkossev.buttonLib, line 34
    // no capabilities // library marker kkossev.buttonLib, line 35
    // no attributes // library marker kkossev.buttonLib, line 36
    // no commands // library marker kkossev.buttonLib, line 37
    preferences { // library marker kkossev.buttonLib, line 38
        // no prefrences // library marker kkossev.buttonLib, line 39
    } // library marker kkossev.buttonLib, line 40
} // library marker kkossev.buttonLib, line 41
*/ // library marker kkossev.buttonLib, line 42

void sendButtonEvent(int buttonNumber, String buttonState, boolean isDigital=false) { // library marker kkossev.buttonLib, line 44
    if (buttonState != 'unknown' && buttonNumber != 0) { // library marker kkossev.buttonLib, line 45
        String descriptionText = "button $buttonNumber was $buttonState" // library marker kkossev.buttonLib, line 46
        if (isDigital) { descriptionText += ' [digital]' } // library marker kkossev.buttonLib, line 47
        Map event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: descriptionText, isStateChange: true, type: isDigital == true ? 'digital' : 'physical'] // library marker kkossev.buttonLib, line 48
        logInfo "$descriptionText" // library marker kkossev.buttonLib, line 49
        sendEvent(event) // library marker kkossev.buttonLib, line 50
    } // library marker kkossev.buttonLib, line 51
    else { // library marker kkossev.buttonLib, line 52
        logWarn "sendButtonEvent: UNHANDLED event for button ${buttonNumber}, buttonState=${buttonState}" // library marker kkossev.buttonLib, line 53
    } // library marker kkossev.buttonLib, line 54
} // library marker kkossev.buttonLib, line 55

void push() {                // Momentary capability // library marker kkossev.buttonLib, line 57
    logDebug 'push momentary' // library marker kkossev.buttonLib, line 58
    if (this.respondsTo('customPush')) { customPush(); return } // library marker kkossev.buttonLib, line 59
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.buttonLib, line 60
} // library marker kkossev.buttonLib, line 61

void push(BigDecimal buttonNumber) {    //pushableButton capability // library marker kkossev.buttonLib, line 63
    logDebug "push button $buttonNumber" // library marker kkossev.buttonLib, line 64
    if (this.respondsTo('customPush')) { customPush(buttonNumber); return } // library marker kkossev.buttonLib, line 65
    sendButtonEvent(buttonNumber as int, 'pushed', isDigital = true) // library marker kkossev.buttonLib, line 66
} // library marker kkossev.buttonLib, line 67

void doubleTap(BigDecimal buttonNumber) { // library marker kkossev.buttonLib, line 69
    sendButtonEvent(buttonNumber as int, 'doubleTapped', isDigital = true) // library marker kkossev.buttonLib, line 70
} // library marker kkossev.buttonLib, line 71

void hold(BigDecimal buttonNumber) { // library marker kkossev.buttonLib, line 73
    sendButtonEvent(buttonNumber as int, 'held', isDigital = true) // library marker kkossev.buttonLib, line 74
} // library marker kkossev.buttonLib, line 75

void release(BigDecimal buttonNumber) { // library marker kkossev.buttonLib, line 77
    sendButtonEvent(buttonNumber as int, 'released', isDigital = true) // library marker kkossev.buttonLib, line 78
} // library marker kkossev.buttonLib, line 79

void sendNumberOfButtonsEvent(int numberOfButtons) { // library marker kkossev.buttonLib, line 81
    sendEvent(name: 'numberOfButtons', value: numberOfButtons, isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 82
} // library marker kkossev.buttonLib, line 83

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.buttonLib, line 85
void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.buttonLib, line 86
    sendEvent(name: 'supportedButtonValues', value: JsonOutput.toJson(supportedValues), isStateChange: true, type: 'digital') // library marker kkossev.buttonLib, line 87
} // library marker kkossev.buttonLib, line 88


// ~~~~~ end include (167) kkossev.buttonLib ~~~~~

// ~~~~~ start include (169) kkossev.groupsLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.groupsLib, line 1
library( // library marker kkossev.groupsLib, line 2
    base: 'driver', // library marker kkossev.groupsLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.groupsLib, line 4
    category: 'zigbee', // library marker kkossev.groupsLib, line 5
    description: 'Zigbee Groups Library', // library marker kkossev.groupsLib, line 6
    name: 'groupsLib', // library marker kkossev.groupsLib, line 7
    namespace: 'kkossev', // library marker kkossev.groupsLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/groupsLib.groovy', // library marker kkossev.groupsLib, line 9
    version: '3.0.0', // library marker kkossev.groupsLib, line 10
    documentationLink: '' // library marker kkossev.groupsLib, line 11
) // library marker kkossev.groupsLib, line 12
/* // library marker kkossev.groupsLib, line 13
 *  Zigbee Groups Library // library marker kkossev.groupsLib, line 14
 * // library marker kkossev.groupsLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.groupsLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.groupsLib, line 17
 * // library marker kkossev.groupsLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.groupsLib, line 19
 * // library marker kkossev.groupsLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.groupsLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.groupsLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.groupsLib, line 23
 * // library marker kkossev.groupsLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added groupsLib.groovy // library marker kkossev.groupsLib, line 25
 * // library marker kkossev.groupsLib, line 26
 *                                   TODO: // library marker kkossev.groupsLib, line 27
*/ // library marker kkossev.groupsLib, line 28

static String groupsLibVersion()   { '3.0.0' } // library marker kkossev.groupsLib, line 30
static String groupsLibStamp() { '2024/04/06 3:56 PM' } // library marker kkossev.groupsLib, line 31

metadata { // library marker kkossev.groupsLib, line 33
    // no capabilities // library marker kkossev.groupsLib, line 34
    // no attributes // library marker kkossev.groupsLib, line 35
    command 'zigbeeGroups', [ // library marker kkossev.groupsLib, line 36
        [name:'command', type: 'ENUM',   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.groupsLib, line 37
        [name:'value',   type: 'STRING', description: 'Group number', constraints: ['STRING']] // library marker kkossev.groupsLib, line 38
    ] // library marker kkossev.groupsLib, line 39

    preferences { // library marker kkossev.groupsLib, line 41
        // no prefrences // library marker kkossev.groupsLib, line 42
    } // library marker kkossev.groupsLib, line 43
} // library marker kkossev.groupsLib, line 44

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.groupsLib, line 46
    defaultValue: 0, // library marker kkossev.groupsLib, line 47
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.groupsLib, line 48
] // library marker kkossev.groupsLib, line 49
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.groupsLib, line 50
    defaultValue: 0, // library marker kkossev.groupsLib, line 51
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.groupsLib, line 52
] // library marker kkossev.groupsLib, line 53

/* // library marker kkossev.groupsLib, line 55
 * ----------------------------------------------------------------------------- // library marker kkossev.groupsLib, line 56
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.groupsLib, line 57
 * ----------------------------------------------------------------------------- // library marker kkossev.groupsLib, line 58
*/ // library marker kkossev.groupsLib, line 59
void customParseGroupsCluster(final Map descMap) { // library marker kkossev.groupsLib, line 60
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.groupsLib, line 61
    logDebug "customParseGroupsCluster: customParseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.groupsLib, line 62
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 63
    switch (descMap.command as Integer) { // library marker kkossev.groupsLib, line 64
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.groupsLib, line 65
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 66
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 67
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 68
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 69
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 70
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 71
                logWarn "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.groupsLib, line 72
            } // library marker kkossev.groupsLib, line 73
            else { // library marker kkossev.groupsLib, line 74
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.groupsLib, line 75
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.groupsLib, line 76
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.groupsLib, line 77
                for (int i = 0; i < groupCount; i++) { // library marker kkossev.groupsLib, line 78
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.groupsLib, line 79
                        logDebug "customParseGroupsCluster: Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.groupsLib, line 80
                        return // library marker kkossev.groupsLib, line 81
                    } // library marker kkossev.groupsLib, line 82
                } // library marker kkossev.groupsLib, line 83
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.groupsLib, line 84
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt, 4)})" // library marker kkossev.groupsLib, line 85
                state.zigbeeGroups['groups'].sort() // library marker kkossev.groupsLib, line 86
            } // library marker kkossev.groupsLib, line 87
            break // library marker kkossev.groupsLib, line 88
        case 0x01: // View group // library marker kkossev.groupsLib, line 89
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.groupsLib, line 90
            logDebug "customParseGroupsCluster: received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 91
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 92
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 93
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 94
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 95
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 96
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 97
                logWarn "customParseGroupsCluster: zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.groupsLib, line 98
            } // library marker kkossev.groupsLib, line 99
            else { // library marker kkossev.groupsLib, line 100
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.groupsLib, line 101
            } // library marker kkossev.groupsLib, line 102
            break // library marker kkossev.groupsLib, line 103
        case 0x02: // Get group membership // library marker kkossev.groupsLib, line 104
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 105
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 106
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.groupsLib, line 107
            final Set<String> groups = [] // library marker kkossev.groupsLib, line 108
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.groupsLib, line 109
                int pos = (i * 2) + 2 // library marker kkossev.groupsLib, line 110
                String group = data[pos + 1] + data[pos] // library marker kkossev.groupsLib, line 111
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.groupsLib, line 112
            } // library marker kkossev.groupsLib, line 113
            state.zigbeeGroups['groups'] = groups // library marker kkossev.groupsLib, line 114
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.groupsLib, line 115
            logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.groupsLib, line 116
            break // library marker kkossev.groupsLib, line 117
        case 0x03: // Remove group // library marker kkossev.groupsLib, line 118
            logInfo "customParseGroupsCluster: received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 119
            final List<String> data = descMap.data as List<String> // library marker kkossev.groupsLib, line 120
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.groupsLib, line 121
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.groupsLib, line 122
            final String groupId = data[2] + data[1] // library marker kkossev.groupsLib, line 123
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.groupsLib, line 124
            if (statusCode > 0x00) { // library marker kkossev.groupsLib, line 125
                logWarn "customParseGroupsCluster: zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.groupsLib, line 126
            } // library marker kkossev.groupsLib, line 127
            else { // library marker kkossev.groupsLib, line 128
                logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.groupsLib, line 129
            } // library marker kkossev.groupsLib, line 130
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.groupsLib, line 131
            int index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.groupsLib, line 132
            if (index >= 0) { // library marker kkossev.groupsLib, line 133
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.groupsLib, line 134
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.groupsLib, line 135
            } // library marker kkossev.groupsLib, line 136
            break // library marker kkossev.groupsLib, line 137
        case 0x04: //Remove all groups // library marker kkossev.groupsLib, line 138
            logDebug "customParseGroupsCluster: received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.groupsLib, line 139
            logWarn 'customParseGroupsCluster: not implemented!' // library marker kkossev.groupsLib, line 140
            break // library marker kkossev.groupsLib, line 141
        case 0x05: // Add group if identifying // library marker kkossev.groupsLib, line 142
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5). // library marker kkossev.groupsLib, line 143
            logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.groupsLib, line 144
            logWarn 'customParseGroupsCluster: not implemented!' // library marker kkossev.groupsLib, line 145
            break // library marker kkossev.groupsLib, line 146
        default: // library marker kkossev.groupsLib, line 147
            logWarn "customParseGroupsCluster: received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.groupsLib, line 148
            break // library marker kkossev.groupsLib, line 149
    } // library marker kkossev.groupsLib, line 150
} // library marker kkossev.groupsLib, line 151

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 153
List<String> addGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 154
    List<String> cmds = [] // library marker kkossev.groupsLib, line 155
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 156
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.groupsLib, line 157
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.groupsLib, line 158
        return [] // library marker kkossev.groupsLib, line 159
    } // library marker kkossev.groupsLib, line 160
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 161
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 162
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 163
    return cmds // library marker kkossev.groupsLib, line 164
} // library marker kkossev.groupsLib, line 165

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 167
List<String> viewGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 168
    List<String> cmds = [] // library marker kkossev.groupsLib, line 169
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 170
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 171
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 172
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 173
    return cmds // library marker kkossev.groupsLib, line 174
} // library marker kkossev.groupsLib, line 175

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 177
List<String> getGroupMembership(dummy) { // library marker kkossev.groupsLib, line 178
    List<String> cmds = [] // library marker kkossev.groupsLib, line 179
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00') // library marker kkossev.groupsLib, line 180
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 181
    return cmds // library marker kkossev.groupsLib, line 182
} // library marker kkossev.groupsLib, line 183

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 185
List<String> removeGroupMembership(groupNr) { // library marker kkossev.groupsLib, line 186
    List<String> cmds = [] // library marker kkossev.groupsLib, line 187
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 188
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.groupsLib, line 189
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.groupsLib, line 190
        return [] // library marker kkossev.groupsLib, line 191
    } // library marker kkossev.groupsLib, line 192
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 193
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 194
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 195
    return cmds // library marker kkossev.groupsLib, line 196
} // library marker kkossev.groupsLib, line 197

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 199
List<String> removeAllGroups(groupNr) { // library marker kkossev.groupsLib, line 200
    List<String> cmds = [] // library marker kkossev.groupsLib, line 201
    final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 202
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 203
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.groupsLib, line 204
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 205
    return cmds // library marker kkossev.groupsLib, line 206
} // library marker kkossev.groupsLib, line 207

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 209
List<String> notImplementedGroups(groupNr) { // library marker kkossev.groupsLib, line 210
    List<String> cmds = [] // library marker kkossev.groupsLib, line 211
    //final Integer group = safeToInt(groupNr) // library marker kkossev.groupsLib, line 212
    //final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.groupsLib, line 213
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.groupsLib, line 214
    return cmds // library marker kkossev.groupsLib, line 215
} // library marker kkossev.groupsLib, line 216

@Field static final Map GroupCommandsMap = [ // library marker kkossev.groupsLib, line 218
    '--- select ---'           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'groupCommandsHelp'], // library marker kkossev.groupsLib, line 219
    'Add group'                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.groupsLib, line 220
    'View group'               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.groupsLib, line 221
    'Get group membership'     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.groupsLib, line 222
    'Remove group'             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.groupsLib, line 223
    'Remove all groups'        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.groupsLib, line 224
    'Add group if identifying' : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.groupsLib, line 225
] // library marker kkossev.groupsLib, line 226

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef */ // library marker kkossev.groupsLib, line 228
void zigbeeGroups(final String command=null, par=null) { // library marker kkossev.groupsLib, line 229
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.groupsLib, line 230
    List<String> cmds = [] // library marker kkossev.groupsLib, line 231
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 232
    if (state.zigbeeGroups['groups'] == null) { state.zigbeeGroups['groups'] = [] } // library marker kkossev.groupsLib, line 233
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.groupsLib, line 234
    def value // library marker kkossev.groupsLib, line 235
    Boolean validated = false // library marker kkossev.groupsLib, line 236
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.groupsLib, line 237
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.groupsLib, line 238
        return // library marker kkossev.groupsLib, line 239
    } // library marker kkossev.groupsLib, line 240
    value = GroupCommandsMap[command]?.type == 'number' ? safeToInt(par, -1) : 0 // library marker kkossev.groupsLib, line 241
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) { validated = true } // library marker kkossev.groupsLib, line 242
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.groupsLib, line 243
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.groupsLib, line 244
        return // library marker kkossev.groupsLib, line 245
    } // library marker kkossev.groupsLib, line 246
    // // library marker kkossev.groupsLib, line 247
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */ // library marker kkossev.groupsLib, line 248
    def func // library marker kkossev.groupsLib, line 249
    try { // library marker kkossev.groupsLib, line 250
        func = GroupCommandsMap[command]?.function // library marker kkossev.groupsLib, line 251
        //def type = GroupCommandsMap[command]?.type // library marker kkossev.groupsLib, line 252
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.groupsLib, line 253
        cmds = "$func"(value) // library marker kkossev.groupsLib, line 254
    } // library marker kkossev.groupsLib, line 255
    catch (e) { // library marker kkossev.groupsLib, line 256
        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.groupsLib, line 257
        return // library marker kkossev.groupsLib, line 258
    } // library marker kkossev.groupsLib, line 259

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.groupsLib, line 261
    sendZigbeeCommands(cmds) // library marker kkossev.groupsLib, line 262
} // library marker kkossev.groupsLib, line 263

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */ // library marker kkossev.groupsLib, line 265
void groupCommandsHelp(val) { // library marker kkossev.groupsLib, line 266
    logWarn 'GroupCommands: select one of the commands in this list!' // library marker kkossev.groupsLib, line 267
} // library marker kkossev.groupsLib, line 268

List<String> customRefresh() { // library marker kkossev.groupsLib, line 270
    logDebug 'customRefresh()' // library marker kkossev.groupsLib, line 271
    return getGroupMembership(null) // library marker kkossev.groupsLib, line 272
} // library marker kkossev.groupsLib, line 273

void customInitializeVars( boolean fullInit = false ) { // library marker kkossev.groupsLib, line 275
    logDebug "customInitializeVars()... fullInit = ${fullInit}" // library marker kkossev.groupsLib, line 276
    if (fullInit || state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.groupsLib, line 277
} // library marker kkossev.groupsLib, line 278

// ~~~~~ end include (169) kkossev.groupsLib ~~~~~

// ~~~~~ start include (170) kkossev.levelLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, PublicMethodsBeforeNonPublicMethods, StaticMethodsBeforeInstanceMethods, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.levelLib, line 1
library( // library marker kkossev.levelLib, line 2
    base: 'driver', // library marker kkossev.levelLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.levelLib, line 4
    category: 'zigbee', // library marker kkossev.levelLib, line 5
    description: 'Zigbee Level Library', // library marker kkossev.levelLib, line 6
    name: 'levelLib', // library marker kkossev.levelLib, line 7
    namespace: 'kkossev', // library marker kkossev.levelLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/levelLib.groovy', // library marker kkossev.levelLib, line 9
    version: '3.0.0', // library marker kkossev.levelLib, line 10
    documentationLink: '' // library marker kkossev.levelLib, line 11
) // library marker kkossev.levelLib, line 12
/* // library marker kkossev.levelLib, line 13
 *  Zigbee Level Library // library marker kkossev.levelLib, line 14
 * // library marker kkossev.levelLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.levelLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.levelLib, line 17
 * // library marker kkossev.levelLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.levelLib, line 19
 * // library marker kkossev.levelLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.levelLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.levelLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.levelLib, line 23
 * // library marker kkossev.levelLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added levelLib.groovy // library marker kkossev.levelLib, line 25
 * // library marker kkossev.levelLib, line 26
 *                                   TODO: // library marker kkossev.levelLib, line 27
*/ // library marker kkossev.levelLib, line 28

static String levelLibVersion()   { '3.0.0' } // library marker kkossev.levelLib, line 30
static String levelLibStamp() { '2024/04/06 9:07 PM' } // library marker kkossev.levelLib, line 31

metadata { // library marker kkossev.levelLib, line 33
    // no capabilities // library marker kkossev.levelLib, line 34
    // no attributes // library marker kkossev.levelLib, line 35
    // no commands // library marker kkossev.levelLib, line 36
    preferences { // library marker kkossev.levelLib, line 37
        // no prefrences // library marker kkossev.levelLib, line 38
    } // library marker kkossev.levelLib, line 39
} // library marker kkossev.levelLib, line 40

import groovy.transform.Field // library marker kkossev.levelLib, line 42

void levelLibParseLevelControlCluster(final Map descMap) { // library marker kkossev.levelLib, line 44
    if (this.respondsTo('customParseLevelControlCluster')) { // library marker kkossev.levelLib, line 45
        customParseLevelControlCluster(descMap) // library marker kkossev.levelLib, line 46
    } // library marker kkossev.levelLib, line 47
    else if (DEVICE_TYPE in ['Bulb']) { // library marker kkossev.levelLib, line 48
        parseLevelControlClusterBulb(descMap) // library marker kkossev.levelLib, line 49
    } // library marker kkossev.levelLib, line 50
    else if (descMap.attrId == '0000') { // library marker kkossev.levelLib, line 51
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.levelLib, line 52
        final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.levelLib, line 53
        sendLevelControlEvent(rawValue) // library marker kkossev.levelLib, line 54
    } // library marker kkossev.levelLib, line 55
    else { // library marker kkossev.levelLib, line 56
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.levelLib, line 57
    } // library marker kkossev.levelLib, line 58
} // library marker kkossev.levelLib, line 59

void sendLevelControlEvent(final int rawValue) { // library marker kkossev.levelLib, line 61
    int value = rawValue as int // library marker kkossev.levelLib, line 62
    if (value < 0) { value = 0 } // library marker kkossev.levelLib, line 63
    if (value > 100) { value = 100 } // library marker kkossev.levelLib, line 64
    Map map = [:] // library marker kkossev.levelLib, line 65

    boolean isDigital = state.states['isDigital'] // library marker kkossev.levelLib, line 67
    map.type = isDigital == true ? 'digital' : 'physical' // library marker kkossev.levelLib, line 68

    map.name = 'level' // library marker kkossev.levelLib, line 70
    map.value = value // library marker kkossev.levelLib, line 71
    boolean isRefresh = state.states['isRefresh'] ?: false // library marker kkossev.levelLib, line 72
    if (isRefresh == true) { // library marker kkossev.levelLib, line 73
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.levelLib, line 74
        map.isStateChange = true // library marker kkossev.levelLib, line 75
    } // library marker kkossev.levelLib, line 76
    else { // library marker kkossev.levelLib, line 77
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.levelLib, line 78
    } // library marker kkossev.levelLib, line 79
    logInfo "${map.descriptionText}" // library marker kkossev.levelLib, line 80
    sendEvent(map) // library marker kkossev.levelLib, line 81
    clearIsDigital() // library marker kkossev.levelLib, line 82
} // library marker kkossev.levelLib, line 83

/** // library marker kkossev.levelLib, line 85
 * Send 'switchLevel' attribute event // library marker kkossev.levelLib, line 86
 * @param isOn true if light is on, false otherwise // library marker kkossev.levelLib, line 87
 * @param level brightness level (0-254) // library marker kkossev.levelLib, line 88
 */ // library marker kkossev.levelLib, line 89
/* groovylint-disable-next-line UnusedPrivateMethodParameter */ // library marker kkossev.levelLib, line 90
private List<String> setLevelPrivate(final BigDecimal value, final int rate = 0, final int delay = 0, final Boolean levelPreset = false) { // library marker kkossev.levelLib, line 91
    List<String> cmds = [] // library marker kkossev.levelLib, line 92
    final Integer level = constrain(value) // library marker kkossev.levelLib, line 93
    //final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.levelLib, line 94
    //final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.levelLib, line 95
    //final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.levelLib, line 96
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.levelLib, line 97
        // If light is off, first go to level 0 then to desired level // library marker kkossev.levelLib, line 98
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.levelLib, line 99
    } // library marker kkossev.levelLib, line 100
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.levelLib, line 101
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.levelLib, line 102
    /* // library marker kkossev.levelLib, line 103
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.levelLib, line 104
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.levelLib, line 105
    */ // library marker kkossev.levelLib, line 106
    int duration = 10            // TODO !!! // library marker kkossev.levelLib, line 107
    String endpointId = '01'     // TODO !!! // library marker kkossev.levelLib, line 108
    cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.levelLib, line 109

    return cmds // library marker kkossev.levelLib, line 111
} // library marker kkossev.levelLib, line 112

/** // library marker kkossev.levelLib, line 114
 * Set Level Command // library marker kkossev.levelLib, line 115
 * @param value level percent (0-100) // library marker kkossev.levelLib, line 116
 * @param transitionTime transition time in seconds // library marker kkossev.levelLib, line 117
 * @return List of zigbee commands // library marker kkossev.levelLib, line 118
 */ // library marker kkossev.levelLib, line 119
void setLevel(final BigDecimal value, final BigDecimal transitionTime = null) { // library marker kkossev.levelLib, line 120
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.levelLib, line 121
    if (this.respondsTo('customSetLevel')) { // library marker kkossev.levelLib, line 122
        customSetLevel(value.intValue(), transitionTime.intValue()) // library marker kkossev.levelLib, line 123
        return // library marker kkossev.levelLib, line 124
    } // library marker kkossev.levelLib, line 125
    if (DEVICE_TYPE in  ['Bulb']) { setLevelBulb(value.intValue(), transitionTime.intValue()); return } // library marker kkossev.levelLib, line 126
    final Integer rate = getLevelTransitionRate(value.intValue(), transitionTime.intValue()) // library marker kkossev.levelLib, line 127
    scheduleCommandTimeoutCheck() // library marker kkossev.levelLib, line 128
    sendZigbeeCommands(setLevelPrivate(value.intValue(), rate as int)) // library marker kkossev.levelLib, line 129
} // library marker kkossev.levelLib, line 130

/** // library marker kkossev.levelLib, line 132
 * Get the level transition rate // library marker kkossev.levelLib, line 133
 * @param level desired target level (0-100) // library marker kkossev.levelLib, line 134
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.levelLib, line 135
 * @return transition rate in 1/10ths of a second // library marker kkossev.levelLib, line 136
 */ // library marker kkossev.levelLib, line 137
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.levelLib, line 138
    int rate = 0 // library marker kkossev.levelLib, line 139
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.levelLib, line 140
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.levelLib, line 141
    if (!isOn) { // library marker kkossev.levelLib, line 142
        currentLevel = 0 // library marker kkossev.levelLib, line 143
    } // library marker kkossev.levelLib, line 144
    // Check if 'transitionTime' has a value // library marker kkossev.levelLib, line 145
    if (transitionTime > 0) { // library marker kkossev.levelLib, line 146
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.levelLib, line 147
        rate = transitionTime * 10 // library marker kkossev.levelLib, line 148
    } else { // library marker kkossev.levelLib, line 149
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.levelLib, line 150
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.levelLib, line 151
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.levelLib, line 152
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.levelLib, line 153
        } // library marker kkossev.levelLib, line 154
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.levelLib, line 155
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.levelLib, line 156
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.levelLib, line 157
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.levelLib, line 158
        } // library marker kkossev.levelLib, line 159
    } // library marker kkossev.levelLib, line 160
    logDebug "using level transition rate ${rate}" // library marker kkossev.levelLib, line 161
    return rate // library marker kkossev.levelLib, line 162
} // library marker kkossev.levelLib, line 163

// Delay before reading attribute (when using polling) // library marker kkossev.levelLib, line 165
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.levelLib, line 166
// Command option that enable changes when off // library marker kkossev.levelLib, line 167
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.levelLib, line 168

/** // library marker kkossev.levelLib, line 170
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.levelLib, line 171
 * @param delayMs delay in milliseconds // library marker kkossev.levelLib, line 172
 * @param commands commands to execute // library marker kkossev.levelLib, line 173
 * @return list of commands to be sent to the device // library marker kkossev.levelLib, line 174
 */ // library marker kkossev.levelLib, line 175
/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.levelLib, line 176
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.levelLib, line 177
    if (state.reportingEnabled == false) { // library marker kkossev.levelLib, line 178
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.levelLib, line 179
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.levelLib, line 180
    } // library marker kkossev.levelLib, line 181
    return [] // library marker kkossev.levelLib, line 182
} // library marker kkossev.levelLib, line 183

/** // library marker kkossev.levelLib, line 185
 * Constrain a value to a range // library marker kkossev.levelLib, line 186
 * @param value value to constrain // library marker kkossev.levelLib, line 187
 * @param min minimum value (default 0) // library marker kkossev.levelLib, line 188
 * @param max maximum value (default 100) // library marker kkossev.levelLib, line 189
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.levelLib, line 190
 */ // library marker kkossev.levelLib, line 191
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.levelLib, line 192
    if (min == null || max == null) { // library marker kkossev.levelLib, line 193
        return value // library marker kkossev.levelLib, line 194
    } // library marker kkossev.levelLib, line 195
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.levelLib, line 196
} // library marker kkossev.levelLib, line 197

/** // library marker kkossev.levelLib, line 199
 * Constrain a value to a range // library marker kkossev.levelLib, line 200
 * @param value value to constrain // library marker kkossev.levelLib, line 201
 * @param min minimum value (default 0) // library marker kkossev.levelLib, line 202
 * @param max maximum value (default 100) // library marker kkossev.levelLib, line 203
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.levelLib, line 204
 */ // library marker kkossev.levelLib, line 205
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.levelLib, line 206
    if (min == null || max == null) { // library marker kkossev.levelLib, line 207
        return value as Integer // library marker kkossev.levelLib, line 208
    } // library marker kkossev.levelLib, line 209
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.levelLib, line 210
} // library marker kkossev.levelLib, line 211

// ~~~~~ end include (170) kkossev.levelLib ~~~~~

// ~~~~~ start include (171) kkossev.batteryLib ~~~~~
/* groovylint-disable CompileStatic, CouldBeSwitchStatement, DuplicateListLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitClosureParameter, ImplicitReturnStatement, Instanceof, LineLength, MethodCount, MethodSize, NoDouble, NoFloat, NoWildcardImports, ParameterCount, ParameterName, UnnecessaryElseStatement, UnnecessaryGetter, UnnecessaryObjectReferences, UnnecessaryPublicModifier, UnnecessarySetter, UnusedImport */ // library marker kkossev.batteryLib, line 1
library( // library marker kkossev.batteryLib, line 2
    base: 'driver', // library marker kkossev.batteryLib, line 3
    author: 'Krassimir Kossev', // library marker kkossev.batteryLib, line 4
    category: 'zigbee', // library marker kkossev.batteryLib, line 5
    description: 'Zigbee Battery Library', // library marker kkossev.batteryLib, line 6
    name: 'batteryLib', // library marker kkossev.batteryLib, line 7
    namespace: 'kkossev', // library marker kkossev.batteryLib, line 8
    importUrl: 'https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/batteryLib.groovy', // library marker kkossev.batteryLib, line 9
    version: '3.0.1', // library marker kkossev.batteryLib, line 10
    documentationLink: '' // library marker kkossev.batteryLib, line 11
) // library marker kkossev.batteryLib, line 12
/* // library marker kkossev.batteryLib, line 13
 *  Zigbee Level Library // library marker kkossev.batteryLib, line 14
 * // library marker kkossev.batteryLib, line 15
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.batteryLib, line 16
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.batteryLib, line 17
 * // library marker kkossev.batteryLib, line 18
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.batteryLib, line 19
 * // library marker kkossev.batteryLib, line 20
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.batteryLib, line 21
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.batteryLib, line 22
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.batteryLib, line 23
 * // library marker kkossev.batteryLib, line 24
 * ver. 3.0.0  2024-04-06 kkossev  - added batteryLib.groovy // library marker kkossev.batteryLib, line 25
 * ver. 3.0.1  2024-04-06 kkossev  - customParsePowerCluster bug fix // library marker kkossev.batteryLib, line 26
 * // library marker kkossev.batteryLib, line 27
 *                                   TODO: battery voltage low/high limits configuration // library marker kkossev.batteryLib, line 28
*/ // library marker kkossev.batteryLib, line 29


static String batteryLibVersion()   { '3.0.1' } // library marker kkossev.batteryLib, line 32
static String batteryLibStamp() { '2024/04/06 9:33 AM' } // library marker kkossev.batteryLib, line 33

metadata { // library marker kkossev.batteryLib, line 35
    capability 'Battery' // library marker kkossev.batteryLib, line 36
    attribute 'batteryVoltage', 'number' // library marker kkossev.batteryLib, line 37
    // no commands // library marker kkossev.batteryLib, line 38
    preferences { // library marker kkossev.batteryLib, line 39
        if (device) { // library marker kkossev.batteryLib, line 40
            input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.batteryLib, line 41
        } // library marker kkossev.batteryLib, line 42
    } // library marker kkossev.batteryLib, line 43
} // library marker kkossev.batteryLib, line 44

void customParsePowerCluster(final Map descMap) { // library marker kkossev.batteryLib, line 46
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.batteryLib, line 47
    if (descMap.attrId in ['0020', '0021']) { // library marker kkossev.batteryLib, line 48
        state.lastRx['batteryTime'] = new Date().getTime() // library marker kkossev.batteryLib, line 49
        state.stats['battCtr'] = (state.stats['battCtr'] ?: 0) + 1 // library marker kkossev.batteryLib, line 50
    } // library marker kkossev.batteryLib, line 51
    final int rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.batteryLib, line 52
    if (descMap.attrId == '0020') { // library marker kkossev.batteryLib, line 53
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.batteryLib, line 54
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.batteryLib, line 55
            sendBatteryVoltageEvent(rawValue, convertToPercent = true) // library marker kkossev.batteryLib, line 56
        } // library marker kkossev.batteryLib, line 57
    } // library marker kkossev.batteryLib, line 58
    else if (descMap.attrId == '0021') { // library marker kkossev.batteryLib, line 59
        sendBatteryPercentageEvent(rawValue * 2) // library marker kkossev.batteryLib, line 60
    } // library marker kkossev.batteryLib, line 61
    else { // library marker kkossev.batteryLib, line 62
        logWarn "customParsePowerCluster: zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.batteryLib, line 63
    } // library marker kkossev.batteryLib, line 64
} // library marker kkossev.batteryLib, line 65

void sendBatteryVoltageEvent(final int rawValue, boolean convertToPercent=false) { // library marker kkossev.batteryLib, line 67
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.batteryLib, line 68
    Map result = [:] // library marker kkossev.batteryLib, line 69
    BigDecimal volts = safeToBigDecimal(rawValue) / 10G // library marker kkossev.batteryLib, line 70
    if (rawValue != 0 && rawValue != 255) { // library marker kkossev.batteryLib, line 71
        BigDecimal minVolts = 2.2 // library marker kkossev.batteryLib, line 72
        BigDecimal maxVolts = 3.2 // library marker kkossev.batteryLib, line 73
        BigDecimal pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.batteryLib, line 74
        int roundedPct = Math.round(pct * 100) // library marker kkossev.batteryLib, line 75
        if (roundedPct <= 0) { roundedPct = 1 } // library marker kkossev.batteryLib, line 76
        if (roundedPct > 100) { roundedPct = 100 } // library marker kkossev.batteryLib, line 77
        if (convertToPercent == true) { // library marker kkossev.batteryLib, line 78
            result.value = Math.min(100, roundedPct) // library marker kkossev.batteryLib, line 79
            result.name = 'battery' // library marker kkossev.batteryLib, line 80
            result.unit  = '%' // library marker kkossev.batteryLib, line 81
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.batteryLib, line 82
        } // library marker kkossev.batteryLib, line 83
        else { // library marker kkossev.batteryLib, line 84
            result.value = volts // library marker kkossev.batteryLib, line 85
            result.name = 'batteryVoltage' // library marker kkossev.batteryLib, line 86
            result.unit  = 'V' // library marker kkossev.batteryLib, line 87
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.batteryLib, line 88
        } // library marker kkossev.batteryLib, line 89
        result.type = 'physical' // library marker kkossev.batteryLib, line 90
        result.isStateChange = true // library marker kkossev.batteryLib, line 91
        logInfo "${result.descriptionText}" // library marker kkossev.batteryLib, line 92
        sendEvent(result) // library marker kkossev.batteryLib, line 93
    } // library marker kkossev.batteryLib, line 94
    else { // library marker kkossev.batteryLib, line 95
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.batteryLib, line 96
    } // library marker kkossev.batteryLib, line 97
} // library marker kkossev.batteryLib, line 98

void sendBatteryPercentageEvent(final int batteryPercent, boolean isDigital=false) { // library marker kkossev.batteryLib, line 100
    if ((batteryPercent as int) == 255) { // library marker kkossev.batteryLib, line 101
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.batteryLib, line 102
        return // library marker kkossev.batteryLib, line 103
    } // library marker kkossev.batteryLib, line 104
    Map map = [:] // library marker kkossev.batteryLib, line 105
    map.name = 'battery' // library marker kkossev.batteryLib, line 106
    map.timeStamp = now() // library marker kkossev.batteryLib, line 107
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.batteryLib, line 108
    map.unit  = '%' // library marker kkossev.batteryLib, line 109
    map.type = isDigital ? 'digital' : 'physical' // library marker kkossev.batteryLib, line 110
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.batteryLib, line 111
    map.isStateChange = true // library marker kkossev.batteryLib, line 112
    // // library marker kkossev.batteryLib, line 113
    Object latestBatteryEvent = device.currentState('battery') // library marker kkossev.batteryLib, line 114
    Long latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.batteryLib, line 115
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.batteryLib, line 116
    int timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.batteryLib, line 117
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.batteryLib, line 118
        // send it now! // library marker kkossev.batteryLib, line 119
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.batteryLib, line 120
    } // library marker kkossev.batteryLib, line 121
    else { // library marker kkossev.batteryLib, line 122
        int delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.batteryLib, line 123
        map.delayed = delayedTime // library marker kkossev.batteryLib, line 124
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.batteryLib, line 125
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.batteryLib, line 126
        runIn(delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.batteryLib, line 127
    } // library marker kkossev.batteryLib, line 128
} // library marker kkossev.batteryLib, line 129

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.batteryLib, line 131
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 132
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 133
    sendEvent(map) // library marker kkossev.batteryLib, line 134
} // library marker kkossev.batteryLib, line 135

/* groovylint-disable-next-line UnusedPrivateMethod */ // library marker kkossev.batteryLib, line 137
private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.batteryLib, line 138
    logInfo "${map.descriptionText}" // library marker kkossev.batteryLib, line 139
    //map.each {log.trace "$it"} // library marker kkossev.batteryLib, line 140
    sendEvent(map) // library marker kkossev.batteryLib, line 141
} // library marker kkossev.batteryLib, line 142

// ~~~~~ end include (171) kkossev.batteryLib ~~~~~
