/* groovylint-disable CompileStatic, DuplicateMapLiteral, DuplicateNumberLiteral, DuplicateStringLiteral, ImplicitReturnStatement, LineLength, NglParseError, ParameterName, UnusedImport */
/**
 *  Tuya Zigbee Fingerbot - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *     in compliance with the License. You may obtain a copy of the License at:
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *     on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *     for the specific language governing permissions and limitations under the License.
 *
 * This driver is inspired by @w35l3y work on Tuya device driver (Edge project).
 * For a big portions of code all credits go to Jonathan Bradshaw.
 *
 * ver. 2.0.3  2023-06-10 kkossev  - Tuya Zigbee Fingerbot
 * ver. 2.1.0  2023-07-15 kkossev  - Fingerbot driver
 * ver. 2.1.2  2023-07-23 kkossev  - Fingerbot library;
 * ver. 2.1.3  2023-08-28 kkossev  - Added Momentary capability for Fingerbot in the main code; direction preference initialization bug fix; voltageToPercent (battery %) is enabled by default; fingerbot button enable/disable;
 * ver. 2.1.4  2023-08-28 kkossev  - Added capability PushableButton for Fingerbot; sendTuyCommand independent from the particular Fingerboot fingerprint;
 *             2023-09-13 kkossev  - Added _TZ3210_j4pdtz9v Moes Zigbee Fingerbot
 * ver. 3.0.4  2024-03-29 kkossev  - Groovy Lint; new driver format and allignment w/commonLib ver 3.0.4; fingerBot mode setting bug fix; added touchButton attribute;
 *                                   push() toggles on/off;
 * ver. 3.0.6  2024-04-06 kkossev  - (dev. branch) commonLib 3.0.6
 *
 *                                   TODO:
 */

static String version() { '3.0.6' }
static String timeStamp() { '2024/04/06 11:55 PM' }

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput

#include kkossev.commonLib
#include kkossev.batteryLib

deviceType = 'Fingerbot'
@Field static final String DEVICE_TYPE = 'Fingerbot'

metadata {
    definition(
        name: 'Tuya Zigbee Fingerbot',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Zigbee%20Fingerbot/Tuya_Zigbee_Fingerbot_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true)
    {
        capability 'Actuator'
        capability 'Battery'
        capability 'Switch'
        capability "PushableButton"
        capability 'Momentary'

        attribute 'batteryVoltage', 'number'
        if (_THREE_STATE == true) {
            attribute 'switch', 'enum', SwitchThreeStateOpts.options.values() as List<String>
        }

        attribute 'fingerbotMode', 'enum', FingerbotModeOpts.options.values() as List<String>
        attribute 'direction', 'enum', FingerbotDirectionOpts.options.values() as List<String>
        attribute 'touchButton', 'enum', FingerbotButtonOpts.options.values() as List<String>
        attribute 'pushTime', 'number'
        attribute 'dnPosition', 'number'
        attribute 'upPosition', 'number'

        fingerprint profileId:'0104', endpointId:'01', inClusters:'0006,EF00,0000', outClusters:'0019,000A', model:'TS0001', manufacturer:'_TZ3210_dse8ogfy', deviceJoinName: 'Tuya Zigbee Fingerbot'
        fingerprint profileId:'0104', endpointId:'01', inClusters:'0006,EF00,0000', outClusters:'0019,000A', model:'TS0001', manufacturer:'_TZ3210_j4pdtz9v', deviceJoinName: 'Moes Zigbee Fingerbot'        // https://community.hubitat.com/t/release-tuya-zigbee-fingerbot/118719/38?u=kkossev

        preferences {
            input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>'
            input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>'
            input name: 'fingerbotMode', type: 'enum', title: '<b>Fingerbot Mode</b>', options: FingerbotModeOpts.options, defaultValue: FingerbotModeOpts.defaultValue, required: true, description: '<i>Push or Switch.</i>'
            input name: 'direction', type: 'enum', title: '<b>Fingerbot Direction</b>', options: FingerbotDirectionOpts.options, defaultValue: FingerbotDirectionOpts.defaultValue, required: true, description: '<i>Finger movement direction.</i>'
            input name: 'pushTime', type: 'number', title: '<b>Push Time</b>', description: '<i>The time that the finger will stay in down position in Push mode, seconds</i>', required: true, range: '0..255', defaultValue: 1
            input name: 'upPosition', type: 'number', title: '<b>Up Postition</b>', description: '<i>Finger up position, (0..50), percent</i>', required: true, range: '0..50', defaultValue: 0
            input name: 'dnPosition', type: 'number', title: '<b>Down Postition</b>', description: '<i>Finger down position (51..100), percent</i>', required: true, range: '51..100', defaultValue: 100
            input name: 'fingerbotButton', type: 'enum', title: '<b>Fingerbot Button</b>', options: FingerbotButtonOpts.options, defaultValue: FingerbotButtonOpts.defaultValue, required: true, description: '<i>Disable or enable the Fingerbot touch button</i>'
        }
    }
}

boolean isFingerbotFingerot()  { (device?.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3210_dse8ogfy', '_TZ3210_j4pdtz9v'] }  // added 03/29/2024

@Field static final Map FingerbotModeOpts = [
    defaultValue: 0,
    options     : [0: 'push', 1: 'switch']
]
@Field static final Map FingerbotDirectionOpts = [
    defaultValue: 0,
    options     : [0: 'normal', 1: 'reverse']
]
@Field static final Map FingerbotButtonOpts = [
    defaultValue: 1,
    options     : [0: 'disabled', 1: 'enabled']
]

void customPush() {
    String currentState = device.currentState('switch')?.value ?: 'n/a'
    logDebug "customPush() currentState=${currentState} fingerbotMode = ${FingerbotModeOpts.options[settings?.fingerbotMode as int]} (${settings?.fingerbotMode as int})"
    if ((settings?.fingerbotMode as int) == 0) { // push
        customOn()
    }
    else { // switch
        if (currentState == 'on') {
            customOff()
        } else {
            customOn()
        }
    }
}

/* groovylint-disable-next-line MethodParameterTypeRequired, NoDef, UnusedMethodParameter */
void customPush(buttonNumber) {    //pushableButton capability
    customPush()
}

void customSwitchEventPostProcesing(final Map event) {
    if (event.name == 'switch' && event.value == 'on' && (settings?.fingerbotMode as int) == 0) {   // push mode
        int duration = settings?.pushTime ?: 1
        logDebug "customSwitchEventPostProcesing() auto switching off after ${duration} seconds"
        runIn(duration, 'autoOff', [overwrite: true])
    }
    else {
        logDebug "customSwitchEventPostProcesing() - skipped event=${event}"
    }
}

void autoOff() {
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on()
    String currentState = device.currentState('switch')?.value ?: 'n/a'
    logDebug "autoOff() currentState=${currentState}"
    String descriptionText = "switch is auto off (push mode)"
    sendEvent(name: 'switch', value: 'off', descriptionText: descriptionText, type: 'digital', isStateChange: true)
    logInfo "${descriptionText}"
}

void customOff() {
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.off()  : zigbee.on()
    String currentState = device.currentState('switch')?.value ?: 'n/a'
    logDebug "customOff() currentState=${currentState}"
    if (_THREE_STATE == true && settings?.threeStateEnable == true) {
        if (currentState == 'off') {
            runIn(1, 'refresh',  [overwrite: true])
        }
        String value = SwitchThreeStateOpts.options[2]    // 'switching_on'
        String descriptionText = "${value}"
        if (logEnable) { descriptionText += ' (2)' }
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true)
        logInfo "${descriptionText}"
    }
    state.states['isDigital'] = true
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    sendZigbeeCommands(cmds)
}

void customOn() {
    List cmds = (settings?.inverceSwitch == null || settings?.inverceSwitch == false) ?  zigbee.on()  : zigbee.off()
    String currentState = device.currentState('switch')?.value ?: 'n/a'
    logDebug "customOn() currentState=${currentState}"
    if (_THREE_STATE == true && settings?.threeStateEnable == true) {
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on') {
            runIn(1, 'refresh',  [overwrite: true])
        }
        String value = SwitchThreeStateOpts.options[3]    // 'switching_on'
        String descriptionText = "${value}"
        if (logEnable) { descriptionText += ' (2)' }
        sendEvent(name: 'switch', value: value, descriptionText: descriptionText, type: 'digital', isStateChange: true)
        logInfo "${descriptionText}"
    }
    state.states['isDigital'] = true
    runInMillis(DIGITAL_TIMER, clearIsDigital, [overwrite: true])
    sendZigbeeCommands(cmds)
}

List<String> customConfigureDevice() {
    List<String> cmds = []

    final int mode = settings.fingerbotMode != null ? (settings.fingerbotMode as int) : FingerbotModeOpts.defaultValue
    logDebug "setting fingerbotMode to ${FingerbotModeOpts.options[mode as int]} (${mode})"
    cmds = sendTuyaCommand('65', DP_TYPE_BOOL, zigbee.convertToHexString(mode as int, 2) )

    final int duration = settings.pushTime != null ? (settings.pushTime as int) : 1
    logDebug "setting pushTime to ${duration} seconds)"
    cmds += sendTuyaCommand('67', DP_TYPE_VALUE, zigbee.convertToHexString(duration as int, 8) )

    final int dnPos = settings.dnPosition != null ? (settings.dnPosition as int) : 100
    logDebug "setting dnPosition to ${dnPos} %"
    cmds += sendTuyaCommand('66', DP_TYPE_VALUE, zigbee.convertToHexString(dnPos as int, 8) )

    final int upPos = settings.upPosition != null ? (settings.upPosition as int) : 0
    logDebug "setting upPosition to ${upPos} %"
    cmds += sendTuyaCommand('6A', DP_TYPE_VALUE, zigbee.convertToHexString(upPos as int, 8) )

    final int dir = settings.direction != null ? (settings.direction as int) : FingerbotDirectionOpts.defaultValue
    logDebug "setting fingerbot direction to ${FingerbotDirectionOpts.options[dir]} (${dir})"
    cmds += sendTuyaCommand('68', DP_TYPE_BOOL, zigbee.convertToHexString(dir as int, 2) )

    int button = settings.fingerbotButton != null ? (settings.fingerbotButton as int) : FingerbotButtonOpts.defaultValue
    logDebug "setting fingerbotButton to ${FingerbotButtonOpts.options[button as int]} (${button})"
    cmds += sendTuyaCommand('6B', DP_TYPE_BOOL, zigbee.convertToHexString(button as int, 2) )

    logDebug "configureDeviceFingerbot() : ${cmds}"
    return cmds
}

void customUpdated() {
    logDebug "customUpdated()"
    List<String> cmds = customConfigureDevice()
    sendZigbeeCommands(cmds)
}

void customInitializeVars(boolean fullInit=false) {
    logDebug "customInitializeVars(${fullInit})"
    if (fullInit || settings?.fingerbotMode == null) { device.updateSetting('fingerbotMode', [value: FingerbotModeOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.pushTime == null) { device.updateSetting('pushTime', [value:1, type:'number']) }
    if (fullInit || settings?.upPosition == null) { device.updateSetting('upPosition', [value:0, type:'number']) }
    if (fullInit || settings?.dnPosition == null) { device.updateSetting('dnPosition', [value:100, type:'number']) }
    if (fullInit || settings?.direction == null) { device.updateSetting('direction', [value: FingerbotDirectionOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.fingerbotButton == null) { device.updateSetting('fingerbotButton', [value: FingerbotButtonOpts.defaultValue.toString(), type: 'enum']) }
    if (fullInit || settings?.voltageToPercent == null) { device.updateSetting('voltageToPercent', true) }
}

/* groovylint-disable-next-line EmptyMethod, UnusedMethodParameter */
void customInitEvents(boolean fullInit=false) {
/*  not needed?
    sendNumberOfButtonsEvent(1)
    sendSupportedButtonValuesEvent("pushed")
*/
}

/*
Switch1             1
Mode                101
Degree of declining    code: 102
Duration             103
Switch Reverse        104
Battery Power        105
Increase            106
Tact Switch         107
Click                 108
Custom Program        109
Producion Test        110
Sports Statistics    111
Custom Timing        112
*/

/* groovylint-disable-next-line UnusedMethodParameter */
boolean customProcessTuyaDp(final Map descMap, int dp, int dp_id, int fncmd, final int dp_len=0) {
    // added 02/29/2024 - filter command 0x06 !
    if (descMap.command == '06') {
        logDebug "customProcessTuyaDp: filtered command ${descMap.command}!"
        return true // ignore, no further processing
    }
    switch (dp) {
        case 0x01 : // on/off
            logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            sendSwitchEvent(fncmd)
            break
        case 0x04 : // battery
            sendBatteryPercentageEvent(fncmd)
            break

        case 0x65 : // (101)
            String value = FingerbotModeOpts.options[fncmd as int]
            String descriptionText = "Fingerbot mode is ${value} (${fncmd})"
            sendEvent(name: 'fingerbotMode', value: value, descriptionText: descriptionText, type: 'physical')
            logInfo "${descriptionText}"
            break
        case 0x66 : // (102)
            int value = fncmd as int
            String descriptionText = "Fingerbot Down Position is ${value} %"
            sendEvent(name: 'dnPosition', value: value, descriptionText: descriptionText, type: 'physical')
            logInfo "${descriptionText}"
            break
        case 0x67 : // (103)
            int value = fncmd as int
            String descriptionText = "Fingerbot push time (duration) is ${value} seconds"
            sendEvent(name: 'pushTime', value: value, descriptionText: descriptionText, type: 'physical')
            logInfo "${descriptionText}"
            break
        case 0x68 : // (104)
            String value = FingerbotDirectionOpts.options[fncmd as int]
            String descriptionText = "Fingerbot switch direction is ${value} (${fncmd})"
            sendEvent(name: 'direction', value: value, descriptionText: descriptionText, type: 'physical')
            logInfo "${descriptionText}"
            break
        case 0x69 : // (105)
            logDebug "Fingerbot Battery Power is ${fncmd}"
            sendBatteryPercentageEvent(fncmd)
            break
        case 0x6A : // (106)
            int value = fncmd as int
            String descriptionText = "Fingerbot Up Position is ${value} %"
            sendEvent(name: 'upPosition', value: value, descriptionText: descriptionText, type: 'physical')
            logInfo "${descriptionText}"
            break
        case 0x6B : // (107)
            String value = FingerbotButtonOpts.options[fncmd as int]
            String descriptionText = "Fingerbot Touch Button is ${value} (${fncmd})"
            sendEvent(name: 'touchButton', value: value, descriptionText: descriptionText, type: 'physical')
            logInfo "${descriptionText}"
            break
        case 0x6C : // (108)
            logInfo "Fingerbot Click is ${fncmd}"
            break
        case 0x6D : // (109)
            logInfo "Fingerbot Custom Program is ${fncmd}"
            break
        case 0x6E : // (110)
            logInfo "Fingerbot Producion Test is ${fncmd}"
            break
        case 0x6F : // (111)
            logInfo "Fingerbot Sports Statistics is ${fncmd}"
            break
        case 0x70 : // (112)
            logInfo "Fingerbot Custom Timing is ${fncmd}"
            break
        default :
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"
            return false
    }
    return true
}

List<String> customRefresh() {
    List<String> cmds = []
    logDebug "customRefresh() (n/a) : ${cmds} "
    return cmds
}

// /////////////////////////////////////////////////////////////////// Libraries //////////////////////////////////////////////////////////////////////
